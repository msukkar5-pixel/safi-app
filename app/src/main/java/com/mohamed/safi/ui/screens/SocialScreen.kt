package com.mohamed.safi.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import com.mohamed.safi.ui.Text
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.mohamed.safi.data.dateTimeStr
import com.mohamed.safi.social.Social
import com.mohamed.safi.ui.*
import kotlinx.coroutines.launch

/** Daily post to social media: preview, one-tap share, automatic posting with the user's own accounts, schedule, log. */
@Composable
fun SocialScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var post by remember { mutableStateOf<Social.Post?>(null) }
    var preview by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var busy by remember { mutableStateOf(false) }
    var rev by remember { mutableIntStateOf(0) }
    var types by remember { mutableStateOf(Social.types) }
    LaunchedEffect(types) {
        post = Social.today(ctx)
        preview = post?.let { p -> kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { Social.card(ctx, p)?.let { BitmapFactory.decodeFile(it.path) } } }
    }
    fun open(pkg: String?) {
        val p = post ?: return
        Social.copyCaption(ctx, p)
        val i = Social.shareIntent(ctx, p, pkg) ?: return
        runCatching { ctx.startActivity(i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { toast(ctx, "التطبيق ده مش متسطّب") }
        if (pkg != null) toast(ctx, "الكلام اتنسخ، الصقه لو مظهرش")
    }

    ScreenScaffold("المنشور اليومي", onBack = onBack) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                AppCard {
                    Text("كل يوم صافي بيجهّز منشور من نصوص موثّقة جوه التطبيق: آية من المصحف، أو حديث من صحيح البخاري ومسلم، أو دعاء من حصن المسلم.", style = MaterialTheme.typography.bodySmall)
                    Text("بيتنشر تلقائي على: قناة تيليجرام، صفحة فيسبوك، وX. وباقي التطبيقات (إنستجرام، واتساب، تيك توك، بروفايل فيسبوك الشخصي) بضغطة واحدة من الإشعار، لأنها مش بتسمح لأي تطبيق ينشر نيابة عنك.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
            }
            item { SectionTitle("منشور النهارده") }
            item {
                GoldCard {
                    val bmp = preview
                    if (bmp != null) Image(bmp.asImageBitmap(), null, Modifier.fillMaxWidth().aspectRatio(1080f / 1350f).clip(RoundedCornerShape(12.dp)))
                    else Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    Spacer(Modifier.height(10.dp))
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("com.facebook.katana" to "فيسبوك", "com.instagram.android" to "إنستجرام", "com.twitter.android" to "X", "com.whatsapp" to "واتساب", "org.telegram.messenger" to "تيليجرام")
                            .forEach { (pkg, n) -> AssistChip(onClick = { open(pkg) }, label = { Text(n) }) }
                        AssistChip(onClick = { open(null) }, label = { Text("تطبيق تاني") }, leadingIcon = { Icon(Icons.Default.Share, null) })
                    }
                    if (Social.tgReady || Social.fbReady || Social.xReady) Button(onClick = {
                        val p = post ?: return@Button
                        busy = true
                        scope.launch { val r = Social.postAll(ctx, p); busy = false; rev++; toast(ctx, r.joinToString("  ")) }
                    }, enabled = !busy && post != null, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                        if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Icon(Icons.Default.Send, null)
                        Spacer(Modifier.width(6.dp)); Text("انشر دلوقتي على الحسابات المربوطة")
                    }
                }
            }
            item { SectionTitle("النشر كل يوم") }
            item {
                AppCard {
                    var on by remember { mutableStateOf(Social.on) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("انشر كل يوم تلقائي", fontWeight = FontWeight.Bold)
                            Text("الساعة ${"%02d:%02d".format(java.util.Locale.US, Social.hour, Social.minute)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }
                        Switch(on, { on = it; Social.on = it; Social.schedule(ctx) })
                    }
                    var h by remember { mutableFloatStateOf(Social.hour.toFloat()) }
                    Text("الساعة: ${h.toInt()}", style = MaterialTheme.typography.bodySmall)
                    Slider(h, { h = it }, valueRange = 0f..23f, steps = 22, onValueChangeFinished = { Social.hour = h.toInt(); Social.minute = 0; Social.schedule(ctx); rev++ })
                    Text("نوع المحتوى", fontWeight = FontWeight.Bold)
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Social.allTypes.forEach { (k, n) ->
                            FilterChip(k in types, {
                                val nt = if (k in types) types - k else types + k
                                if (nt.isNotEmpty()) { types = nt; Social.types = nt }
                            }, label = { Text(n) })
                        }
                    }
                    var sig by remember { mutableStateOf(Social.signature) }
                    OutlinedTextField(sig, { sig = it; Social.signature = it }, label = { Text("توقيع أو هاشتاج في آخر المنشور") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Text("الفيديوهات مش متاحة: التطبيق مفيهوش فيديوهات موثّقة يقدر ينشرها.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
            }
            item { SectionTitle("الحسابات (للنشر التلقائي)") }
            item {
                AccountCard("تيليجرام (قناة)", Social.tgReady, "١. افتح @BotFather في تيليجرام واعمل بوت جديد وخد الـ token.\n٢. ضيف البوت أدمن في قناتك.\n٣. اكتب اسم القناة زي @mychannel.") {
                    var t by remember { mutableStateOf(Social.tgToken) }; var c by remember { mutableStateOf(Social.tgChat) }
                    SecretField("Bot token", t) { t = it; Social.tgToken = it }
                    OutlinedTextField(c, { c = it; Social.tgChat = it }, label = { Text("القناة (@اسم_القناة)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            }
            item {
                AccountCard("صفحة فيسبوك", Social.fbReady, "فيسبوك بيسمح بالنشر التلقائي على الصفحات بس، مش البروفايل الشخصي.\n١. من developers.facebook.com اعمل App.\n٢. من Graph API Explorer طلّع Page access token بصلاحية pages_manage_posts (وخليه طويل المدة).\n٣. اكتب رقم الصفحة (Page ID) والـ token هنا.") {
                    var id by remember { mutableStateOf(Social.fbPage) }; var t by remember { mutableStateOf(Social.fbToken) }
                    OutlinedTextField(id, { id = it; Social.fbPage = it }, label = { Text("Page ID") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    SecretField("Page access token", t) { t = it; Social.fbToken = it }
                }
            }
            item {
                AccountCard("X (تويتر)", Social.xReady, "١. من developer.x.com اعمل App بصلاحية Read and Write.\n٢. خد الـ API Key/Secret والـ Access Token/Secret.\n٣. النشر نص بس، والمنشور الأطول من ٢٨٠ حرف بيتخطّى. استخدام الـ API ممكن يبقى بحدود أو بفلوس حسب خطة X.") {
                    var k by remember { mutableStateOf(Social.xKeys) }
                    listOf("API Key", "API Secret", "Access Token", "Access Token Secret").forEachIndexed { i, n ->
                        SecretField(n, k[i]) { v -> k = k.toMutableList().also { it[i] = v }; Social.xKeys = k }
                    }
                }
            }
            item {
                Text("المفاتيح بتتحفظ على موبايلك بس، ومش بتدخل في النسخة الاحتياطية. إنستجرام بيحتاج حساب بيزنس وسيرفر يرفع الصورة، فبيتنشر من الإشعار بضغطة.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
            item { SectionTitle("السجل") }
            item {
                @Suppress("UNUSED_VARIABLE") val r = rev
                val lines = Social.logLines()
                AppCard {
                    if (lines.isEmpty()) Text("لسه مفيش محاولات نشر", color = MaterialTheme.colorScheme.outline)
                    lines.take(15).forEach { (at, x) -> androidx.compose.material3.Text("${dateTimeStr(at)}  $x", style = MaterialTheme.typography.bodySmall) }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun AccountCard(title: String, ready: Boolean, help: String, content: @Composable ColumnScope.() -> Unit) {
    var openHelp by remember { mutableStateOf(false) }
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(if (ready) "✓ مربوط" else "مش مربوط", color = if (ready) Positive else MaterialTheme.colorScheme.outline, style = MaterialTheme.typography.labelMedium)
        }
        TextButton(onClick = { openHelp = !openHelp }) { Text(if (openHelp) "اخفي الشرح" else "إزاي أربطه؟") }
        if (openHelp) Text(help, style = MaterialTheme.typography.bodySmall)
        content()
    }
}

@Composable
private fun SecretField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(value, onChange, label = { androidx.compose.material3.Text(label) }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
}
