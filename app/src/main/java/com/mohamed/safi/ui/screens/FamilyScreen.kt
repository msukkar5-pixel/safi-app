package com.mohamed.safi.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import com.mohamed.safi.ui.Text
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mohamed.safi.family.Family
import com.mohamed.safi.data.dateStr
import com.mohamed.safi.ui.*
import kotlinx.coroutines.launch

private val roles = listOf("أب", "أم", "ابن", "بنت", "جد", "جدة", "أخ", "أخت")

private fun qrBitmap(text: String, size: Int = 640): Bitmap {
    val m = com.google.zxing.qrcode.QRCodeWriter().encode(text, com.google.zxing.BarcodeFormat.QR_CODE, size, size)
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
    for (x in 0 until size) for (y in 0 until size) bmp.setPixel(x, y, if (m[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
    return bmp
}

/** Link family phones: QR pairing, encrypted cards, home Wi-Fi sync or any messenger. No server. */
@Composable
fun FamilyScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    @Suppress("UNUSED_VARIABLE") val v = Family.version.intValue
    @Suppress("UNUSED_VARIABLE") val sync = Family.lastSync.intValue
    var showQr by remember { mutableStateOf(false) }
    var setup by remember { mutableStateOf<String?>(null) } // "create" | "join"
    var scanned by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Family.joined) {
        if (Family.joined) Family.startLan(ctx)
        onDispose { Family.stopLan() }
    }

    fun scan() {
        runCatching {
            com.google.mlkit.vision.codescanner.GmsBarcodeScanning.getClient(ctx).startScan()
                .addOnSuccessListener { b -> scanned = b.rawValue; setup = "join" }
                .addOnFailureListener { toast(ctx, "مقدرتش أفتح الماسح") }
        }.onFailure { toast(ctx, "الماسح مش متاح على الموبايل ده") }
    }

    ScreenScaffold("ربط العيلة", onBack = onBack) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                AppCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lock, null, tint = Positive)
                        Spacer(Modifier.width(8.dp))
                        Text("خصوصية كاملة ومن غير سيرفر", fontWeight = FontWeight.Bold)
                    }
                    Text("الربط بيتم بمسح QR من موبايل لموبايل. كل اللي بيتشارك متشفّر بمفتاح العيلة، ومحدش غير أفراد العيلة يقدر يقراه. كل واحد بيختار هو يشارك إيه، ومن الأول مفيش حاجة بتتشارك غير اسمه.", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (!Family.joined) {
                item {
                    Button(onClick = { setup = "create" }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Icon(Icons.Default.GroupAdd, null); Spacer(Modifier.width(6.dp)); Text("أنشئ عيلة جديدة") }
                }
                item {
                    OutlinedButton(onClick = { scan() }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Icon(Icons.Default.QrCodeScanner, null); Spacer(Modifier.width(6.dp)); Text("انضم بمسح QR من موبايل العيلة") }
                }
                item {
                    Text("واحد بس ينشئ العيلة (غالباً الأب أو الأم)، وباقي العيلة يمسحوا الـ QR من موبايله.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
                return@LazyColumn
            }
            item {
                GoldCard {
                    Text("أنا: ${Family.myName} (${Family.myRole})", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { showQr = true }) { Icon(Icons.Default.QrCode, null); Spacer(Modifier.width(4.dp)); Text("ضيف فرد") }
                        FilledTonalButton(onClick = {
                            scope.launch {
                                val card = Family.myCard()
                                ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, card), tr("ابعت تحديثك")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            }
                        }) { Icon(Icons.Default.Send, null); Spacer(Modifier.width(4.dp)); Text("ابعت تحديثي") }
                    }
                    Text("على نفس الواي فاي: التحديث بيتبادل لوحده وانت فاتح الشاشة دي. برا البيت: ابعت تحديثك بواتساب، واللي يستلمه يعمل «مشاركة» للرسالة مع صافي.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
            }
            item { SectionTitle("العيلة") }
            val members = Family.members()
            if (members.isEmpty()) item { EmptyState(Icons.Default.Groups, "لسه مفيش تحديثات من حد. افتحوا الشاشة دي على نفس الواي فاي أو ابعتوا التحديث.") }
            items(members, key = { it.id }) { m ->
                AppCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(42.dp).clip(RoundedCornerShape(21.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                            Text(when (m.role) { "أب", "جد" -> "👨"; "أم", "جدة" -> "👩"; "ابن", "أخ" -> "👦"; "بنت", "أخت" -> "👧"; else -> "🙂" }, fontSize = 22.sp)
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            androidx.compose.material3.Text(m.name, fontWeight = FontWeight.Bold)
                            Text("${m.role} • ${dateStr(m.at)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                    m.status?.let { androidx.compose.material3.Text("💬 $it", modifier = Modifier.padding(top = 4.dp)) }
                    m.prayers?.let { Text("🕌 صلّى النهارده: $it من ٥") }
                    m.wird?.let { Text("📖 الورد: $it") }
                    m.kids?.let { androidx.compose.material3.Text("👧 $it") }
                    m.city?.let { androidx.compose.material3.Text("📍 $it") }
                }
            }
            item { SectionTitle("أنا بشارك إيه؟") }
            item {
                AppCard {
                    ShareSwitch("صلواتي النهارده (العدد بس)", Family.sharePrayers) { Family.sharePrayers = it }
                    ShareSwitch("وردي من القرآن", Family.shareWird) { Family.shareWird = it }
                    ShareSwitch("نجوم الأطفال", Family.shareKids) { Family.shareKids = it }
                    ShareSwitch("مدينتي (من غير موقع دقيق)", Family.shareCity) { Family.shareCity = it }
                    var st by remember { mutableStateOf(Family.status) }
                    OutlinedTextField(st, { st = it.take(80); Family.status = st }, label = { Text("رسالة حالة (مثلاً: وصلت البيت)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            }
            item {
                var confirm by remember { mutableStateOf(false) }
                TextButton(onClick = { confirm = true }) { Text("اخرج من العيلة وامسح البيانات", color = Danger) }
                if (confirm) ConfirmDialog("تخرج من العيلة؟", "هيتمسح مفتاح العيلة وكل التحديثات من الموبايل ده.", "اخرج", { confirm = false }) { Family.stopLan(); Family.leave(); confirm = false }
            }
        }
    }

    if (showQr) {
        val bmp = remember { qrBitmap(Family.inviteText()) }
        AlertDialog(
            onDismissRequest = { showQr = false },
            title = { Text("امسح ده من موبايل الفرد الجديد") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(bmp.asImageBitmap(), null, Modifier.size(260.dp).background(Color.White))
                    Text("الكود ده هو مفتاح العيلة. ماتبعتهوش ولا تصوّره لحد برّا العيلة.", style = MaterialTheme.typography.bodySmall, color = Danger)
                }
            },
            confirmButton = { TextButton(onClick = { showQr = false }) { Text("تمام") } },
        )
    }

    setup?.let { mode ->
        var name by remember { mutableStateOf("") }
        var role by remember { mutableStateOf(roles.first()) }
        AlertDialog(
            onDismissRequest = { setup = null },
            title = { Text(if (mode == "create") "عيلة جديدة" else "الانضمام للعيلة") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it.take(20) }, label = { Text("اسمك اللي العيلة هتشوفه") }, singleLine = true)
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { roles.forEach { r -> FilterChip(role == r, { role = r }, label = { Text(r) }) } }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val n = name.ifBlank { tr(role) }
                    if (mode == "create") { Family.create(n, role); setup = null }
                    else if (Family.join(scanned.orEmpty(), n, role)) { toast(ctx, "انضميت للعيلة"); setup = null }
                    else { toast(ctx, "الكود ده مش كود عيلة صافي"); setup = null }
                }) { Text("تمام") }
            },
            dismissButton = { TextButton(onClick = { setup = null }) { Text("إلغاء") } },
        )
    }
}

@Composable
private fun ShareSwitch(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f))
        Switch(value, onChange)
    }
}
