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
import com.mohamed.safi.sms.SmsProcessor
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
    var senders by remember { mutableStateOf(prefs.extraSenders) }
    var smsOn by remember { mutableStateOf(prefs.smsOn) }
    var importing by remember { mutableStateOf(false) }
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("سجّل المصاريف من الرسايل")
                        Text("Emirates NBD • ADCB • ADIB", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    }
                    Switch(smsOn, { smsOn = it; prefs.smsOn = it })
                }
                OutlinedTextField(
                    senders, { senders = it }, label = { Text("أسماء مرسلين إضافية (اختياري)") },
                    placeholder = { Text("مثلاً: Mashreq, FAB") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { prefs.extraSenders = senders; toast(ctx, "اتحفظ") }) { Text("حفظ") }
                    OutlinedButton(onClick = {
                        prefs.extraSenders = senders
                        importing = true
                        scope.launch {
                            val n = withContext(Dispatchers.IO) { SmsProcessor.importInbox(ctx, 180, useClaude = Claude.hasKey) }
                            importing = false
                            toast(ctx, "اتضاف $n عملية")
                        }
                    }, enabled = !importing) {
                        if (importing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("اقرا آخر 6 شهور")
                    }
                }
                Text("الرسايل مش بتطلع برا تليفونك. لو رسالة صيغتها غريبة وفيه مفتاح Claude، بتتبعت لـ Claude بس علشان يقراها.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
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
