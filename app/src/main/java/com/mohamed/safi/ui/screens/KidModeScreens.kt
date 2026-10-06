package com.mohamed.safi.ui.screens

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import com.mohamed.safi.ui.Text
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mohamed.safi.faith.Prayer
import com.mohamed.safi.kids.KidMode
import com.mohamed.safi.ui.*

private val KBg = Color(0xFFFFF7E8)
private val KInk = Color(0xFF3B3125)
private val tileColors = listOf(0xFF4FA3D9, 0xFF2E9D5B, 0xFFE09F3E, 0xFF8E5BB8, 0xFFD9534F, 0xFF00897B).map { Color(it) }

/** The child's home in kid mode: only the sections the parent allowed, big and simple. */
@Composable
fun KidHomeScreen(open: (String) -> Unit) {
    @Suppress("UNUSED_VARIABLE") val v = KidMode.version.intValue
    var askPin by remember { mutableStateOf(false) }
    val allowed = KidMode.allowed
    val list = KidMode.sections.filter { it.route in allowed }
    Column(Modifier.fillMaxSize().background(KBg).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("أهلاً يا ${KidMode.name.ifBlank { tr("بطل") }} 👋", fontFamily = Amiri, fontWeight = FontWeight.Bold, fontSize = 26.sp, color = KInk)
                val (p, t) = remember { runCatching { Prayer.nextPrayer() }.getOrNull() } ?: (null to null)
                if (p != null && t != null) Text("الصلاة الجاية: $p ${t12(t)}", color = KInk)
            }
            IconButton(onClick = { askPin = true }) { Icon(Icons.Default.Lock, "ولي الأمر", tint = KInk.copy(alpha = 0.5f)) }
        }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            list.chunked(2).forEachIndexed { r, row ->
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEachIndexed { c, s ->
                            Surface(onClick = { open(s.route) }, shape = RoundedCornerShape(24.dp), color = tileColors[(r * 2 + c) % tileColors.size], modifier = Modifier.weight(1f).height(130.dp)) {
                                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                    androidx.compose.material3.Text(s.icon, fontSize = 44.sp)
                                    Text(s.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp, textAlign = TextAlign.Center)
                                }
                            }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
            if (list.isEmpty()) item { Text("ولي الأمر لسه ما اختارش أقسام", color = KInk) }
        }
    }
    if (askPin) ParentPinDialog({ askPin = false }) { askPin = false; open("kidsetup") }
}

@Composable
private fun ParentPinDialog(onDismiss: () -> Unit, onOk: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("لولي الأمر بس") },
        text = {
            Column {
                OutlinedTextField(pin, { pin = it.filter(Char::isDigit).take(8); wrong = false }, label = { Text("الرقم السري") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
                if (wrong) Text("الرقم غلط", color = Danger)
            }
        },
        confirmButton = { Button(onClick = { if (KidMode.checkPin(pin)) onOk(pin) else wrong = true }) { Text("دخول") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}

/**
 * Parent's screen: turn kid mode on for this phone, make a setup code for a child's phone, or (in kid mode, after the
 * PIN) change the allowed sections or turn kid mode off.
 */
@Composable
fun KidSetupScreen(onBack: () -> Unit, onKidHome: () -> Unit) {
    val ctx = LocalContext.current
    @Suppress("UNUSED_VARIABLE") val v = KidMode.version.intValue
    var unlocked by remember { mutableStateOf(!KidMode.on) }
    var name by remember { mutableStateOf(KidMode.name) }
    var allowed by remember { mutableStateOf(KidMode.allowed) }
    var pin by remember { mutableStateOf("") }
    var pin2 by remember { mutableStateOf("") }
    var confirmOn by remember { mutableStateOf(false) }
    var qr by remember { mutableStateOf<String?>(null) }
    var incoming by remember { mutableStateOf<KidMode.Setup?>(KidMode.pendingCode.value?.let { KidMode.readCode(it) }) }
    LaunchedEffect(Unit) { KidMode.pendingCode.value = null }

    if (KidMode.on && !unlocked) { ParentPinDialog(onBack) { unlocked = true }; return }

    fun pinOk(): Boolean = when {
        pin.length < 4 -> { toast(ctx, "الرقم السري ٤ أرقام على الأقل"); false }
        pin != pin2 -> { toast(ctx, "الرقمين مش زي بعض"); false }
        allowed.isEmpty() -> { toast(ctx, "اختار قسم واحد على الأقل"); false }
        else -> true
    }

    ScreenScaffold("وضع الطفل", onBack = onBack) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                AppCard {
                    Text("لموبايل الطفل: التطبيق يظهر بأقسام إنت تختارها بس، في شاشة بسيطة للأطفال.", fontWeight = FontWeight.Bold)
                    Text("الحسابات والمستندات والأماكن والمذكرات والإعدادات بتتقفل. والخروج من وضع الطفل محتاج رقمك السري.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
            }
            if (!KidMode.on) item {
                OutlinedButton(onClick = {
                    runCatching {
                        com.google.mlkit.vision.codescanner.GmsBarcodeScanning.getClient(ctx).startScan()
                            .addOnSuccessListener { b -> KidMode.readCode(b.rawValue.orEmpty())?.let { incoming = it } ?: toast(ctx, "ده مش كود وضع الطفل") }
                            .addOnFailureListener { toast(ctx, "مقدرتش أفتح الماسح") }
                    }.onFailure { toast(ctx, "الماسح مش متاح على الموبايل ده") }
                }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Icon(Icons.Default.QrCodeScanner, null); Spacer(Modifier.width(6.dp)); Text("ده موبايل الطفل: امسح كود ولي الأمر") }
            }
            item {
                GoldCard {
                    if (!KidMode.on) OutlinedTextField(name, { name = it.take(20) }, label = { Text("اسم الطفل") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Text("الأقسام اللي تظهر للطفل", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        KidMode.sections.forEach { s ->
                            FilterChip(s.route in allowed, {
                                allowed = if (s.route in allowed) allowed - s.route else allowed + s.route
                                if (KidMode.on) KidMode.setAllowed(allowed)
                            }, label = { Text("${s.icon} ${tr(s.title)}") })
                        }
                    }
                    if (!KidMode.on) {
                        OutlinedTextField(pin, { pin = it.filter(Char::isDigit).take(8) }, label = { Text("رقم سري لولي الأمر") }, singleLine = true,
                            visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(pin2, { pin2 = it.filter(Char::isDigit).take(8) }, label = { Text("أكّد الرقم السري") }, singleLine = true,
                            visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), modifier = Modifier.fillMaxWidth())
                    }
                }
            }
            if (!KidMode.on) {
                item {
                    Button(onClick = { if (pinOk()) qr = KidMode.setupCode(name, allowed, pin) }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                        Icon(Icons.Default.QrCode, null); Spacer(Modifier.width(6.dp)); Text("اعمل كود لموبايل الطفل")
                    }
                    Text("من موبايلك: اعمل الكود، وعلى موبايل الطفل نزّل صافي واختار «ده موبايل طفل» وامسحه.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
                item {
                    OutlinedButton(onClick = { if (pinOk()) confirmOn = true }, modifier = Modifier.fillMaxWidth()) { Text("شغّل وضع الطفل على الموبايل ده") }
                }
            } else item {
                var off by remember { mutableStateOf(false) }
                Button(onClick = onKidHome, modifier = Modifier.fillMaxWidth()) { Text("رجوع لشاشة الطفل") }
                TextButton(onClick = { off = true }) { Text("اقفل وضع الطفل (التطبيق الكامل)", color = Danger) }
                if (off) ParentPinDialog({ off = false }) { p -> off = false; if (KidMode.disable(p)) UiBus.pendingRoute.value = "home" }
            }
        }
    }

    if (confirmOn) ConfirmDialog("تشغّل وضع الطفل هنا؟", "الموبايل ده هيفتح على شاشة الطفل بس، والخروج محتاج الرقم السري.", "شغّل", { confirmOn = false }) {
        KidMode.enable(name, allowed, pin); confirmOn = false; onKidHome()
    }
    incoming?.let { s ->
        ConfirmDialog("تحوّل الموبايل ده لموبايل طفل؟", "وضع الطفل لـ ${s.name.ifBlank { "?" }}، بالأقسام اللي ولي الأمر اختارها.", "تمام", { incoming = null }) {
            KidMode.applyCode(s); incoming = null; onKidHome()
        }
    }
    qr?.let { code ->
        val bmp = remember(code) { qrBitmapFor(code) }
        AlertDialog(
            onDismissRequest = { qr = null },
            title = { Text("امسح ده من موبايل الطفل") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(bmp.asImageBitmap(), null, Modifier.size(250.dp).background(Color.White))
                    Text("أو ابعته كرسالة وافتحها بصافي على موبايل الطفل.", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = { qr = null }) { Text("تمام") } },
            dismissButton = {
                TextButton(onClick = {
                    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, code), tr("ابعت")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }) { Text("ابعته كرسالة") }
            },
        )
    }
}

private fun qrBitmapFor(text: String, size: Int = 640): android.graphics.Bitmap {
    val m = com.google.zxing.qrcode.QRCodeWriter().encode(text, com.google.zxing.BarcodeFormat.QR_CODE, size, size)
    val bmp = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.RGB_565)
    for (x in 0 until size) for (y in 0 until size) bmp.setPixel(x, y, if (m[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
    return bmp
}
