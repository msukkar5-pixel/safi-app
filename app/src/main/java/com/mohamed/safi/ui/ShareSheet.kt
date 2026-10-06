package com.mohamed.safi.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import com.mohamed.safi.ui.Text
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.flow.MutableStateFlow

/** Something to share: a verse, hadith or dua with its source. [extra] is only added to plain-text shares (e.g. tafsir). */
data class ShareItem(val title: String, val body: String, val source: String, val extra: String = "")

/** Opens the share menu from anywhere: as a story, an image post or text, to WhatsApp, Facebook, Instagram and the rest. */
object ShareBus {
    val item = MutableStateFlow<ShareItem?>(null)
    fun open(title: String, body: String, source: String, extra: String = "") { item.value = ShareItem(title, body, source, extra) }
}

private val apps = listOf(
    "com.whatsapp" to "واتساب", "com.facebook.katana" to "فيسبوك", "com.instagram.android" to "إنستجرام",
    "com.snapchat.android" to "سناب شات", "org.telegram.messenger" to "تيليجرام", "com.zhiliaoapp.musically" to "تيك توك",
)

@Composable
fun ShareSheetHost() {
    val it by ShareBus.item.collectAsState()
    it?.let { s -> ShareSheet(s) { ShareBus.item.value = null } }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ShareSheet(s: ShareItem, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("شارك", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Text("ستوري / حالة", fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                apps.forEach { (pkg, n) -> AssistChip(onClick = { story(ctx, s, pkg); onDismiss() }, label = { Text(n) }) }
                AssistChip(onClick = { image(ctx, s, null, true); onDismiss() }, label = { Text("تطبيق تاني") }, leadingIcon = { Icon(Icons.Default.Share, null) })
            }
            Text("الستوري بتتعمل صورة طولية. في واتساب اختار «حالتي»، وفي فيسبوك وإنستجرام اختار «Story».", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            HorizontalDivider()
            Text("صورة منشور", fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                apps.forEach { (pkg, n) -> AssistChip(onClick = { image(ctx, s, pkg, false); onDismiss() }, label = { Text(n) }) }
                AssistChip(onClick = { image(ctx, s, null, false); onDismiss() }, label = { Text("تطبيق تاني") }, leadingIcon = { Icon(Icons.Default.Share, null) })
            }
            HorizontalDivider()
            OutlinedButton(onClick = { text(ctx, s); onDismiss() }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.TextFields, null); Spacer(Modifier.width(6.dp)); Text("شارك كنص") }
        }
    }
}

private fun caption(s: ShareItem) = s.body + if (s.source.isNotBlank()) "\n[${s.source}]" else ""

private fun file(ctx: Context, s: ShareItem, story: Boolean): Uri? {
    val footer = com.mohamed.safi.social.Social.signature
    val f = ShareCard.render(ctx, s.title, s.body, s.source, footer, name = if (story) "safi_story" else "safi_card", story = story) ?: return null
    return FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
}

private fun copy(ctx: Context, s: ShareItem) {
    (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager)?.setPrimaryClip(ClipData.newPlainText("safi", caption(s)))
}

private fun launch(ctx: Context, i: Intent): Boolean = runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true }.getOrDefault(false)

/** An image post (or, with [story], a tall story image) sent to [pkg], or to any app when null. */
private fun image(ctx: Context, s: ShareItem, pkg: String?, story: Boolean) {
    val uri = file(ctx, s, story) ?: return toast(ctx, "مقدرتش أجهّز الصورة")
    copy(ctx, s)
    val send = Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri).putExtra(Intent.EXTRA_TEXT, caption(s))
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).apply { clipData = ClipData.newRawUri("", uri) }
    if (pkg == null) { launch(ctx, Intent.createChooser(send, tr("شارك"))); return }
    if (!launch(ctx, send.setPackage(pkg))) toast(ctx, "التطبيق ده مش متسطّب")
}

/**
 * Story: Instagram and Facebook open their story editor directly when a Meta App ID is set (their rule);
 * otherwise, and for WhatsApp, Snapchat, Telegram and TikTok, the tall image goes to the app, which offers Story / Status.
 */
private fun story(ctx: Context, s: ShareItem, pkg: String) {
    val uri = file(ctx, s, true) ?: return toast(ctx, "مقدرتش أجهّز الصورة")
    copy(ctx, s)
    val appId = com.mohamed.safi.social.Social.metaAppId
    if (appId.isNotBlank() && (pkg == "com.instagram.android" || pkg == "com.facebook.katana")) {
        ctx.grantUriPermission(pkg, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val i = if (pkg == "com.instagram.android")
            Intent("com.instagram.share.ADD_TO_STORY").putExtra("source_application", appId)
        else Intent("com.facebook.stories.ADD_TO_STORY").putExtra("com.facebook.platform.extra.APPLICATION_ID", appId)
        i.setDataAndType(uri, "image/png").setPackage(pkg).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (launch(ctx, i)) return
    }
    val send = Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).apply { clipData = ClipData.newRawUri("", uri) }.setPackage(pkg)
    if (launch(ctx, send)) toast(ctx, if (pkg == "com.whatsapp") "اختار «حالتي» من فوق" else "اختار «Story» من التطبيق")
    else toast(ctx, "التطبيق ده مش متسطّب")
}

private fun text(ctx: Context, s: ShareItem) {
    val t = caption(s) + if (s.extra.isNotBlank()) "\n\n${s.extra}" else ""
    launch(ctx, Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, t), tr("شارك")))
}
