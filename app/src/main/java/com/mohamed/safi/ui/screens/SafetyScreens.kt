package com.mohamed.safi.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import com.mohamed.safi.ui.Text
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mohamed.safi.faith.Mosques
import com.mohamed.safi.faith.Prayer
import com.mohamed.safi.safety.Sos
import com.mohamed.safi.ui.*
import kotlinx.coroutines.launch
import androidx.compose.material3.Text as RawText

/** Mosques around you (OpenStreetMap), nearest first, with walking directions. */
@Composable
fun MosquesScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var radius by remember { mutableIntStateOf(2000) }
    var list by remember { mutableStateOf<List<Mosques.Mosque>?>(null) }
    var error by remember { mutableStateOf(false) }
    var rev by remember { mutableIntStateOf(0) }
    LaunchedEffect(radius, rev) {
        list = null; error = false
        val here = Sos.lastLocation(ctx)
        val lat = here?.latitude ?: Prayer.lat; val lng = here?.longitude ?: Prayer.lng
        runCatching { Mosques.near(lat, lng, radius) }.onSuccess { list = it }.onFailure { error = true; list = emptyList() }
    }
    ScreenScaffold("مساجد قريبة", onBack = onBack) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(1000 to "١ كم", 2000 to "٢ كم", 5000 to "٥ كم", 10000 to "١٠ كم").forEach { (r, n) -> FilterChip(radius == r, { radius = r }, label = { Text(n) }) }
                }
            }
            val l = list
            when {
                l == null -> item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
                error -> item {
                    EmptyState(Icons.Default.CloudOff, "مقدرتش أجيب المساجد، اتأكد من الإنترنت")
                    TextButton(onClick = { rev++ }) { Text("جرّب تاني") }
                }
                l.isEmpty() -> item { EmptyState(Icons.Default.Mosque, "مفيش مساجد مسجّلة في المسافة دي، كبّر المسافة") }
                else -> items(l) { m ->
                    AppCard(onClick = {
                        val u = Uri.parse(String.format(java.util.Locale.US, "https://www.google.com/maps/dir/?api=1&destination=%.6f,%.6f&travelmode=walking", m.lat, m.lng))
                        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, u).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Mosque, null, tint = Gold)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                RawText(m.name, fontWeight = FontWeight.SemiBold)
                                Text(if (m.meters < 1000) "${m.meters} متر" else "${"%.1f".format(java.util.Locale.US, m.meters / 1000.0)} كم", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                            }
                            Icon(Icons.Default.Directions, "الاتجاهات", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
            item { Text("البيانات من OpenStreetMap (© OpenStreetMap contributors)، وممكن يكون فيه مساجد مش مسجّلة.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline) }
        }
    }
}

/** Emergency: hold the button to send your location to chosen family members; quick dial for an ambulance. */
@Composable
fun SosScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var contacts by remember { mutableStateOf(Sos.contacts) }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    val progress = remember { Animatable(0f) }
    var smsAllowed by remember { mutableStateOf(Sos.canSendDirect(ctx)) }
    val smsPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { smsAllowed = it }
    val locPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val uri = r.data?.data ?: return@rememberLauncherForActivityResult
        runCatching {
            ctx.contentResolver.query(uri, arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER), null, null, null)?.use { c ->
                if (c.moveToFirst()) { contacts = (contacts + Sos.Contact(c.getString(0).orEmpty(), c.getString(1).orEmpty())).take(5); Sos.contacts = contacts }
            }
        }
    }
    fun fire() {
        when (val n = Sos.send(ctx)) {
            -1 -> toast(ctx, "ضيف رقم واحد على الأقل تحت")
            0 -> toast(ctx, "اتفتحت الرسالة، دوس إرسال")
            else -> toast(ctx, "اتبعتت لـ $n")
        }
    }
    ScreenScaffold("الطوارئ", onBack = onBack) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            item {
                Box(
                    Modifier.size(200.dp).clip(CircleShape).background(Danger).pointerInput(Unit) {
                        detectTapGestures(onPress = {
                            // hold for 2 seconds so it can't go off by mistake
                            val job = scope.launch { progress.snapTo(0f); progress.animateTo(1f, tween(2000, easing = LinearEasing)); if (progress.value >= 1f) fire() }
                            tryAwaitRelease(); job.cancel(); scope.launch { progress.snapTo(0f) }
                        })
                    },
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(progress = { progress.value }, modifier = Modifier.fillMaxSize().padding(6.dp), color = Color.White, strokeWidth = 8.dp, trackColor = Color.Transparent)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        RawText("SOS", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 44.sp)
                        Text("دوس مطوّل ثانيتين", color = Color.White)
                    }
                }
            }
            item { Text("بيبعت «محتاج مساعدة» ومكانك على الخريطة للأرقام دي.", textAlign = TextAlign.Center) }
            item {
                OutlinedButton(onClick = { runCatching { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Sos.ambulance())).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.LocalHospital, null, tint = Danger); Spacer(Modifier.width(6.dp)); Text("اتصل بالإسعاف (${Sos.ambulance()})")
                }
            }
            item { SectionTitle("مين يوصله التنبيه؟", Modifier.fillMaxWidth()) }
            items(contacts) { c ->
                AppCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { RawText(c.name.ifBlank { c.phone }, fontWeight = FontWeight.SemiBold); RawText(c.phone, style = MaterialTheme.typography.bodySmall) }
                        IconButton(onClick = { contacts = contacts - c; Sos.contacts = contacts }) { Icon(Icons.Default.Close, null) }
                    }
                }
            }
            if (contacts.size < 5) item {
                AppCard {
                    OutlinedButton(onClick = { pick.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Contacts, null); Spacer(Modifier.width(6.dp)); Text("اختار من الأسماء")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(name, { name = it.take(20) }, label = { Text("الاسم") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(phone, { phone = it.filter { ch -> ch.isDigit() || ch == '+' }.take(16) }, label = { Text("الرقم") }, singleLine = true, modifier = Modifier.weight(1f),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone))
                    }
                    TextButton(onClick = { if (phone.length >= 6) { contacts = contacts + Sos.Contact(name, phone); Sos.contacts = contacts; name = ""; phone = "" } }) { Text("ضيف") }
                }
            }
            item {
                AppCard {
                    var note by remember { mutableStateOf(Sos.note) }
                    OutlinedTextField(note, { note = it; Sos.note = it }, label = { Text("معلومة تتبعت مع التنبيه (فصيلة الدم، مرض مزمن…)") }, modifier = Modifier.fillMaxWidth())
                    if (!smsAllowed) {
                        Text("عشان التنبيه يتبعت لوحده من غير ما تدوس إرسال، اسمح بإرسال الرسايل.", style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { smsPerm.launch(Manifest.permission.SEND_SMS) }) { Text("اسمح بإرسال الرسايل") }
                    } else Text("✓ التنبيه بيتبعت رسالة SMS لوحده (بتكلفة الرسالة العادية من خطك).", style = MaterialTheme.typography.bodySmall, color = Positive)
                    if (Sos.lastLocation(ctx) == null) TextButton(onClick = { locPerm.launch(Manifest.permission.ACCESS_FINE_LOCATION) }) { Text("اسمح بالموقع عشان مكانك يتبعت") }
                    TextButton(onClick = { toast(ctx, Sos.message(ctx)) }) { Text("شوف الرسالة اللي هتتبعت") }
                }
            }
        }
    }
}
