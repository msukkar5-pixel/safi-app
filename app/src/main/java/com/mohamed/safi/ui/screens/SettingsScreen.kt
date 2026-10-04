package com.mohamed.safi.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.mohamed.safi.SafiApp
import com.mohamed.safi.ai.Claude
import com.mohamed.safi.data.Fx
import com.mohamed.safi.data.dateTimeStr
import com.mohamed.safi.location.LocationService
import com.mohamed.safi.notify.DailyWorker
import com.mohamed.safi.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val prefs = SafiApp.prefs
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf(prefs.userName) }
    var key by remember { mutableStateOf(prefs.apiKey) }
    var showKey by remember { mutableStateOf(false) }
    var model by remember { mutableStateOf(prefs.model) }
    var fast by remember { mutableStateOf(prefs.fastModel) }
    var testing by remember { mutableStateOf(false) }
    var rate by remember { mutableStateOf(prefs.egpPerAed.toString()) }
    var rateAuto by remember { mutableStateOf(prefs.rateAuto) }
    var rateUpdated by remember { mutableLongStateOf(prefs.rateUpdated) }
    var cats by remember { mutableStateOf(prefs.transferCats.joinToString("، ")) }
    var lockOn by remember { mutableStateOf(prefs.lockOn) }
    var briefHour by remember { mutableIntStateOf(prefs.briefHour) }
    var interval by remember { mutableIntStateOf(prefs.locationIntervalMin) }

    ScreenScaffold("الإعدادات", onBack = onBack) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SectionTitle("الصلاحيات")
            PermissionsList()

            SectionTitle("المساعد الذكي (Claude)")
            AppCard {
                OutlinedTextField(
                    key, { key = it.trim() }, label = { Text("مفتاح Claude API") }, singleLine = true,
                    placeholder = { Text("sk-ant-…") },
                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showKey = !showKey }) { Icon(if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility, null) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "من console.anthropic.com ← API Keys. المفتاح بيتحفظ على تليفونك بس.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(model, { model = it }, label = { Text("الموديل الأساسي") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(fast, { fast = it }, label = { Text("الموديل السريع (رسايل البنك)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        prefs.apiKey = key; prefs.model = model.ifBlank { "claude-sonnet-5-5" }; prefs.fastModel = fast.ifBlank { "claude-haiku-4-5-20251001" }
                        toast(ctx, "اتحفظ")
                    }) { Text("حفظ") }
                    OutlinedButton(onClick = {
                        prefs.apiKey = key; prefs.model = model; prefs.fastModel = fast
                        testing = true
                        scope.launch {
                            val msg = try {
                                Claude.call("Reply with one short Egyptian Arabic sentence.", JSONArray().put(Claude.userText("قول أهلاً")), prefs.model, 50)
                                "✓ شغال: " + prefs.model
                            } catch (e: Exception) {
                                e.message ?: "فيه مشكلة"
                            }
                            testing = false
                            toast(ctx, msg)
                        }
                    }, enabled = !testing && key.isNotBlank()) {
                        if (testing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("جرّب")
                    }
                }
            }

            SectionTitle("العملة وتحويلات مصر")
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("تحديث سعر الجنيه أوتوماتيك")
                        Text(
                            if (rateUpdated > 0) "آخر تحديث: ${dateTimeStr(rateUpdated)}" else "لسه ماتحدثش",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline,
                        )
                    }
                    Switch(rateAuto, { rateAuto = it; prefs.rateAuto = it })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NumberField("1 درهم = كام جنيه", rate, Modifier.weight(1f)) { rate = it }
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = {
                        scope.launch {
                            val ok = withContext(Dispatchers.IO) { Fx.refresh() }
                            rate = prefs.egpPerAed.toString(); rateUpdated = prefs.rateUpdated
                            toast(ctx, if (ok) "السعر اتحدث" else "مفيش نت")
                        }
                    }) { Icon(Icons.Default.Refresh, "تحديث") }
                }
                Text("لو بتحول بسعر الصرافة، اقفل التحديث الأوتوماتيك واكتب سعرك.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(cats, { cats = it }, label = { Text("بنود التحويلات (افصل بفاصلة)") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Button(onClick = {
                    rate.toDoubleOrNull()?.takeIf { it > 0 }?.let { prefs.egpPerAed = it }
                    prefs.transferCats = cats.split(",", "،").map { it.trim() }.filter { it.isNotEmpty() }.ifEmpty { listOf("أخرى") }
                    toast(ctx, "اتحفظ")
                }) { Text("حفظ") }
            }

            SectionTitle("رسايل البنك")
            AppCard {
                Text("تليفونك بيمنع أي تطبيق برا المتجر يقرا الرسايل، فالتسجيل بيتم بطريقتين:", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text("• رسالة واحدة: في تطبيق الرسايل دوس مطوّل على رسالة البنك ← مشاركة ← صافي.", style = MaterialTheme.typography.bodyMedium)
                Text("• كذا رسالة مرة واحدة: انسخهم، وفي شاشة المصاريف دوس زرار اللصق فوق.", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(6.dp))
                Text("بيتعرف على Emirates NBD وADCB وADIB وأي بنك تاني. نفس الرسالة مش بتتسجل مرتين. لو صيغتها غريبة وفيه مفتاح Claude، Claude بيقراها.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }

            SectionTitle("النسخة الاحتياطية")
            BackupCard()

            SectionTitle("التطبيقات المربوطة")
            AppCard {
                var maps by remember { mutableStateOf(com.mohamed.safi.apps.Apps.mapsApp(ctx)) }
                var music by remember { mutableStateOf(com.mohamed.safi.apps.Apps.musicApp(ctx)) }
                val A = com.mohamed.safi.apps.Apps
                ChoiceField("الخرائط", maps, listOf(A.WAZE, A.GMAPS), display = { A.appLabel(it) + if (!A.installed(ctx, it)) " (مش متثبت)" else "" }) {
                    maps = it; A.setMapsApp(ctx, it)
                }
                Spacer(Modifier.height(8.dp))
                ChoiceField("المزيكا", music, listOf(A.ANGHAMI, A.SPOTIFY, A.YTMUSIC), display = { A.appLabel(it) + if (!A.installed(ctx, it)) " (مش متثبت)" else "" }) {
                    music = it; A.setMusicApp(ctx, it)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "قول لصافي: \"وديني دبي مول\"، \"شغّل فيروز\"، \"افتح كريم\"، \"ابعت واتساب لـ 050…\". أي تطبيق متثبت على تليفونك يقدر يفتحه بالاسم.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline,
                )
            }

            SectionTitle("عام")
            AppCard {
                OutlinedTextField(name, { name = it }, label = { Text("اسمك") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                ChoiceField("ملخص الصبح الساعة", briefHour.toString(), (5..11).map { it.toString() }, display = { "$it الصبح" }) {
                    briefHour = it.toInt(); prefs.briefHour = briefHour; DailyWorker.schedule(ctx, replace = true)
                }
                Spacer(Modifier.height(8.dp))
                ChoiceField("تسجيل الموقع كل", interval.toString(), listOf("2", "5", "10", "15", "30"), display = { "$it دقايق" }) {
                    interval = it.toInt(); prefs.locationIntervalMin = interval
                    if (prefs.locationOn) { LocationService.stop(ctx); LocationService.start(ctx) }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("قفل بالبصمة")
                        Text("يطلب البصمة كل ما تفتح التطبيق", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    }
                    Switch(lockOn, { lockOn = it; prefs.lockOn = it })
                }
                Spacer(Modifier.height(8.dp))
                Button(onClick = { prefs.userName = name.trim().ifBlank { "محمد" }; toast(ctx, "اتحفظ") }) { Text("حفظ") }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "صافي ${runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrDefault("")} • كل بياناتك على تليفونك انت بس",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp),
            )
        }
    }
}


@Composable
private fun BackupCard() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var confirmUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val create = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        if (uri != null) {
            busy = true
            scope.launch {
                val msg = try { com.mohamed.safi.extra.Backup.export(ctx, uri); "النسخة اتحفظت ✓" } catch (e: Exception) { e.message ?: "فشل" }
                busy = false
                toast(ctx, msg)
            }
        }
    }
    val open = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) confirmUri = uri }

    AppCard {
        Text("احفظ نسخة من كل بياناتك في ملف واحد. وانت بتحفظه اختار Google Drive علشان لو التليفون اتغير ترجّعها.", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { create.launch("safi-backup-${java.time.LocalDate.now()}.zip") }, enabled = !busy) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("احفظ نسخة")
            }
            OutlinedButton(onClick = { open.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }, enabled = !busy) { Text("رجّع نسخة") }
        }
        Text(
            "وكمان كل جمعة بتتعمل نسخة أوتوماتيك في Downloads/Safi على التليفون. مفتاح Claude مش بيتحفظ في النسخة.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline,
        )
    }
    confirmUri?.let { u ->
        ConfirmDialog("ترجيع النسخة؟", "كل البيانات الحالية هتتبدل باللي في الملف، والتطبيق هيقفل ويفتح تاني.", "رجّع", { confirmUri = null }) {
            busy = true
            scope.launch {
                try { com.mohamed.safi.extra.Backup.restore(ctx, u) } catch (e: Exception) { busy = false; toast(ctx, e.message ?: "فشل") }
            }
        }
    }
}
