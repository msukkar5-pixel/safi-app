package com.mohamed.safi.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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

private val roles = listOf("زوج", "زوجة", "أب", "أم", "ابن", "ابنة", "أخ", "أخت", "جد", "جدة")

private fun roleEmoji(r: String) = when (r) {
    "أب", "زوج" -> "👨"; "أم", "زوجة" -> "👩"; "جد" -> "👴"; "جدة" -> "👵"
    "ابن", "أخ" -> "👦"; "ابنة", "بنت", "أخت" -> "👧"; else -> "🙂"
}

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
    var showReply by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<com.mohamed.safi.family.MemberCard?>(null) }
    var syncOn by remember { mutableStateOf(com.mohamed.safi.SafiApp.prefs.familySyncOn) }
    val newcomer = Family.joinedName.value
    LaunchedEffect(newcomer) {
        if (newcomer != null) { toast(ctx, "اتضاف للعيلة: $newcomer"); Family.joinedName.value = null; showQr = false }
    }

    DisposableEffect(Family.joined, syncOn) {
        if (Family.joined && !com.mohamed.safi.SafiApp.prefs.familySyncOn) Family.startLan(ctx)
        onDispose { if (!com.mohamed.safi.SafiApp.prefs.familySyncOn) Family.stopLan() }
    }

    fun handle(text: String) {
        when {
            Family.isInvite(text) -> if (Family.joined) toast(ctx, "انت منضم لعيلة بالفعل") else { scanned = text; setup = "join" }
            Family.isCard(text) -> Family.importCard(text)?.let { toast(ctx, "اتضاف للعيلة: $it"); showQr = false } ?: toast(ctx, "الكود ده مش من عيلتك")
            else -> toast(ctx, "الكود ده مش كود عيلة ${com.mohamed.safi.AppName.v}")
        }
    }

    fun scan() {
        runCatching {
            com.google.mlkit.vision.codescanner.GmsBarcodeScanning.getClient(ctx).startScan()
                .addOnSuccessListener { b -> b.rawValue?.let { handle(it) } }
                .addOnFailureListener { toast(ctx, "مقدرتش أفتح الماسح") }
        }.onFailure { toast(ctx, "الماسح مش متاح على الموبايل ده") }
    }

    ScreenScaffold("ربط العيلة", onBack = onBack) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            @Suppress("UNUSED_VARIABLE") val live = Family.version.intValue // re-run the list when the data changes
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
                    Text("أنا: ${Family.myName} (${tr(Family.myRole)})", fontWeight = FontWeight.Bold)
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
                    Text("على نفس الواي فاي: التحديث بيتبادل لوحده حتى لو قفلت الشاشة طالما المزامنة المستمرة مفعلة. برا البيت: ابعت تحديثك بواتساب، واللي يستلمه يعمل «مشاركة» للرسالة مع ${com.mohamed.safi.AppName.v}.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("المزامنة المستمرة", fontWeight = FontWeight.SemiBold)
                            Text("إشعار واضح • بطاقات مشفّرة عبر الواي فاي فقط", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }
                        Switch(syncOn, {
                            syncOn = it
                            com.mohamed.safi.SafiApp.prefs.familySyncOn = it
                            if (it) { Family.stopLan(); com.mohamed.safi.family.FamilySyncService.start(ctx) }
                            else com.mohamed.safi.family.FamilySyncService.stop(ctx)
                        })
                    }
                }
            }
            item { SectionTitle("العيلة") }
            val members = Family.members()
            if (members.isEmpty()) item { EmptyState(Icons.Default.Groups, "لسه محدش اتضاف. اضغط «ضيف فرد» وخلّيه يمسح الكود، وهيظهر هنا بصفته.") }
            items(members, key = { it.id }) { m ->
                val role = Family.label(m.id) ?: m.role
                AppCard(onClick = { editing = m }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(42.dp).clip(RoundedCornerShape(21.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                            Text(roleEmoji(role), fontSize = 22.sp)
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            androidx.compose.material3.Text(m.name, fontWeight = FontWeight.Bold)
                            Text("${tr(role)} • ${dateStr(m.at)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }
                        Icon(Icons.Default.Edit, null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(18.dp))
                    }
                    m.status?.let { androidx.compose.material3.Text("💬 $it", modifier = Modifier.padding(top = 4.dp)) }
                    m.prayers?.let { Text("🕌 صلّى النهارده: $it من ٥") }
                    m.wird?.let { Text("📖 الورد: $it") }
                    m.kids?.let { androidx.compose.material3.Text("👧 $it") }
                    m.city?.let { androidx.compose.material3.Text("📍 $it") }
                }
            }
            item {
                AppCard(onClick = { UiBus.pendingRoute.value = "familylists" }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Checklist, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("لستة العيلة", fontWeight = FontWeight.Bold)
                            Text("المشتريات وأعياد الميلاد والمواعيد، متشاركة بينكم", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }
                        Icon(Icons.Default.ChevronLeft, null)
                    }
                }
            }
            item { SectionTitle("الختمة العائلية") }
            item { FamilyKhatma() }
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
                    Spacer(Modifier.height(6.dp))
                    Text("لو انتو على نفس الواي فاي هيتضاف عندك لوحده. لو لأ: بعد ما ينضم هيظهرله كود، امسحه من هنا 👇", style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = { scan() }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.QrCodeScanner, null); Spacer(Modifier.width(4.dp)); Text("امسح كود الفرد الجديد") }
                }
            },
            confirmButton = { TextButton(onClick = { showQr = false }) { Text("تمام") } },
        )
    }

    if (showReply) {
        val bmp = remember { qrBitmap(Family.introCard()) }
        AlertDialog(
            onDismissRequest = { showReply = false },
            title = { Text("انضميت للعيلة 🎉") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("خلّي اللي ضافك يمسح الكود ده من «امسح كود الفرد الجديد» عشان تتضاف عنده. (على نفس الواي فاي بيحصل لوحده.)", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(6.dp))
                    Image(bmp.asImageBitmap(), null, Modifier.size(240.dp).background(Color.White))
                }
            },
            confirmButton = { TextButton(onClick = { showReply = false }) { Text("تمام") } },
        )
    }

    editing?.let { m ->
        var role by remember(m.id) { mutableStateOf(Family.label(m.id) ?: m.role) }
        var confirmRemove by remember(m.id) { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { androidx.compose.material3.Text(m.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("صفته عندي", fontWeight = FontWeight.Bold)
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { roles.forEach { r -> FilterChip(role == r, { role = r }, label = { Text(r) }) } }
                    TextButton(onClick = { confirmRemove = true }) { Text("شيله من عندي", color = Danger) }
                }
            },
            confirmButton = { Button(onClick = { Family.setLabel(m.id, role); editing = null }) { Text("حفظ") } },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("إلغاء") } },
        )
        if (confirmRemove) ConfirmDialog("تشيله؟", "هيتمسح من الموبايل ده بس، ولو بعت تحديث تاني هيرجع.", "شيل", { confirmRemove = false }) { Family.removeMember(m.id); confirmRemove = false; editing = null }
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
                    if (mode == "create") { Family.create(n, role); com.mohamed.safi.family.FamilySyncService.start(ctx); setup = null }
                    else if (Family.join(scanned.orEmpty(), n, role)) { com.mohamed.safi.family.FamilySyncService.start(ctx); setup = null; showReply = true }
                else { toast(ctx, "الكود ده مش كود عيلة ${com.mohamed.safi.AppName.v}"); setup = null }
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

/** 30 juz split between family members: tap a free juz to take it, tap yours to mark it read. */
@Composable
private fun FamilyKhatma() {
    @Suppress("UNUSED_VARIABLE") val v = Family.version.intValue
    var confirm by remember { mutableStateOf(false) }
    AppCard {
        if (Family.khRound == 0) {
            Text("قسّموا الـ ٣٠ جزء على العيلة، وكل واحد يعلّم على جزئه لما يخلّصه. التقدم بيوصل للكل مع التحديث.", style = MaterialTheme.typography.bodySmall)
            Button(onClick = { Family.startKhatma() }, modifier = Modifier.fillMaxWidth()) { Text("ابدأ ختمة عائلية") }
            return@AppCard
        }
        val juz = Family.khatma()
        val done = juz.count { it.done }
        Text("الختمة رقم ${Family.khRound}: اتقرا $done من ٣٠ جزء", fontWeight = FontWeight.Bold)
        LinearProgressIndicator(progress = { done / 30f }, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
        juz.chunked(5).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 3.dp)) {
                row.forEach { j ->
                    val bg = when { j.done -> Positive.copy(alpha = 0.25f); j.mine -> Gold.copy(alpha = 0.35f); j.by.isNotEmpty() -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f); else -> MaterialTheme.colorScheme.surfaceVariant }
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(bg)
                            .clickable { when { j.mine && !j.done -> Family.setDone(j.n, true); j.mine -> Family.unclaim(j.n); j.by.isEmpty() -> Family.claim(j.n) } }
                            .padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(if (j.done) "✓" else "${j.n}", fontWeight = FontWeight.Bold)
                        androidx.compose.material3.Text(if (j.mine) tr("أنا") else j.by.firstOrNull().orEmpty(), fontSize = 9.sp, maxLines = 1)
                    }
                }
            }
        }
        Text("اضغط جزء فاضي تاخده، واضغط جزءك لما تقراه ✓، ومرة كمان تسيبه. ابعت تحديثك عشان العيلة تشوف.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        if (done == 30) Text("ما شاء الله، الختمة خلصت 🎉", color = Positive, fontWeight = FontWeight.Bold)
        TextButton(onClick = { confirm = true }) { Text("ابدأ ختمة جديدة") }
    }
    if (confirm) ConfirmDialog("ختمة جديدة؟", "هتبدأ ختمة جديدة للعيلة كلها لما يوصلهم تحديثك.", "ابدأ", { confirm = false }) { Family.startKhatma(); confirm = false }
}
