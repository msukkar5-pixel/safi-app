package com.mohamed.safi.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import com.mohamed.safi.ui.Text
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
            GoldCard(onClick = { UiBus.pendingRoute.value = "app_guide" }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.MenuBook, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("دليل التطبيق", fontWeight = FontWeight.Bold)
                        Text("شرح كل قسم وإزاي تستخدمه", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    }
                    Icon(Icons.Default.ChevronLeft, null)
                }
            }
            AppCard(onClick = { UiBus.pendingRoute.value = "social" }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Campaign, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("المنشور اليومي على السوشيال ميديا", fontWeight = FontWeight.Bold)
                        Text("آية أو حديث أو دعاء كل يوم على حساباتك", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    }
                    Icon(Icons.Default.ChevronLeft, null)
                }
            }
            SectionTitle("لغة التطبيق")
            AppCard {
                val scope = rememberCoroutineScope()
                val prog by AutoTranslate.progress.collectAsState()
                val err by AutoTranslate.error.collectAsState()
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    I18n.languages.forEach { (code, label) ->
                        FilterChip(I18n.lang.value == code, {
                            I18n.set(ctx, code)
                            if (!I18n.ready(ctx, code) && prog == null) scope.launch {
                                if (AutoTranslate.build(ctx, code)) I18n.set(ctx, code)
                            }
                        }, label = { androidx.compose.material3.Text(label) })
                    }
                }
                prog?.let { p ->
                    Text("بجهّز اللغة على تليفونك… ${(p * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                    LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
                }
                err?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                Text("العربي والإنجليزي والأوردو جاهزين. باقي اللغات بتتترجم على تليفونك مرة واحدة (محتاج نت أول مرة بس) وبعدها تشتغل من غير نت. القرآن والأذكار والأحاديث والكتب بتفضل بلغتها الأصلية.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }

            SectionTitle("الصلاحيات")
            PermissionsList()

            SectionTitle("الذكاء الاصطناعي")
            AiSettingsCard()

            SectionTitle("الصوت")
            VoiceSettingsCard()

            SectionTitle("اسم التطبيق")
            AppNameCard()

            SectionTitle("رفيق وذاكرته")
            CompanionMemoryCard()

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
                Text("• رسالة واحدة: في تطبيق الرسايل دوس مطوّل على رسالة البنك ← مشاركة ← ${com.mohamed.safi.AppName.v}.", style = MaterialTheme.typography.bodyMedium)
                Text("• كذا رسالة مرة واحدة: انسخهم، وفي شاشة المصاريف دوس زرار اللصق فوق.", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(6.dp))
                Text("بيتعرف على Emirates NBD وADCB وADIB وأي بنك تاني. نفس الرسالة مش بتتسجل مرتين. لو صيغتها غريبة والذكاء الاصطناعي مربوط، هو اللي بيقراها.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
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
                    "قول لـ${com.mohamed.safi.AppName.v}: \"وديني دبي مول\"، \"شغّل فيروز\"، \"افتح كريم\"، \"ابعت واتساب لـ 050…\". أي تطبيق متثبت على تليفونك يقدر يفتحه بالاسم.",
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
                Button(onClick = { prefs.userName = name.trim(); toast(ctx, "اتحفظ") }) { Text("حفظ") }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "${com.mohamed.safi.AppName.v} ${runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrDefault("")} • كل بياناتك على تليفونك انت بس",
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
            "وكمان كل جمعة بتتعمل نسخة أوتوماتيك في Downloads/Safi على التليفون. مفاتيح الذكاء الاصطناعي مش بتتحفظ في النسخة.",
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


@Composable
private fun AiSettingsCard() {
    val prefs = SafiApp.prefs
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var provider by remember { mutableStateOf(prefs.aiProvider) }
    var key by remember(provider) { mutableStateOf(prefs.apiKey) }
    var model by remember(provider) { mutableStateOf(prefs.model) }
    var fast by remember(provider) { mutableStateOf(prefs.fastModel) }
    var baseUrl by remember(provider) { mutableStateOf(prefs.aiBaseUrl) }
    var showKey by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var models by remember(provider) { mutableStateOf<List<String>>(emptyList()) }
    var loadingModels by remember { mutableStateOf(false) }
    val p = com.mohamed.safi.ai.Providers.get(provider)

    fun persist() {
        prefs.aiProvider = provider
        prefs.apiKey = key
        prefs.aiBaseUrl = baseUrl
        prefs.model = model
        prefs.fastModel = fast
    }

    AppCard {
        Text("اختار أي ذكاء اصطناعي عندك حساب فيه. كل مزود ليه مفتاحه، والمفاتيح بتتحفظ على تليفونك بس.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(8.dp))
        ChoiceField("المزوّد", provider, com.mohamed.safi.ai.Providers.all.map { it.id }, display = { com.mohamed.safi.ai.Providers.get(it).label }) {
            persist(); provider = it; prefs.aiProvider = it
        }
        Spacer(Modifier.height(8.dp))
        if (provider == "custom") {
            OutlinedTextField(baseUrl, { baseUrl = it.trim() }, label = { Text("الرابط (https://…/v1)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
        }
        OutlinedTextField(
            key, { key = it.trim() }, label = { Text("مفتاح API") }, singleLine = true,
            visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = { IconButton(onClick = { showKey = !showKey }) { Icon(if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility, null) } },
            modifier = Modifier.fillMaxWidth(),
        )
        if (p.keyUrl.isNotBlank()) Text("المفتاح من: ${p.keyUrl}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        if (!p.vision) Text("${p.label} مش بيقرا صور، فقراءة الفواتير والأكل بالصورة مش هتشتغل معاه.", style = MaterialTheme.typography.bodySmall, color = Warn)
        Spacer(Modifier.height(8.dp))
        if (models.isNotEmpty()) {
            ChoiceField("الموديل الأساسي", model, models) { model = it }
            Spacer(Modifier.height(6.dp))
            ChoiceField("الموديل السريع", fast, models) { fast = it }
        } else {
            OutlinedTextField(model, { model = it }, label = { Text("الموديل الأساسي") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(fast, { fast = it }, label = { Text("الموديل السريع (رسايل البنك)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        TextButton(onClick = {
            persist(); loadingModels = true
            scope.launch {
                try { models = com.mohamed.safi.ai.Claude.listModels(); if (models.isEmpty()) toast(ctx, "مفيش موديلات راجعة") }
                catch (e: Exception) { toast(ctx, e.message ?: "فشل") }
                loadingModels = false
            }
        }, enabled = key.isNotBlank() && !loadingModels) {
            if (loadingModels) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Text("هات الموديلات المتاحة")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { persist(); toast(ctx, "اتحفظ") }) { Text("حفظ") }
            OutlinedButton(onClick = {
                persist()
                testing = true
                scope.launch {
                    val msg = try {
                        val r = com.mohamed.safi.ai.Claude.call("Reply with one short Egyptian Arabic sentence.", JSONArray().put(com.mohamed.safi.ai.Claude.userText("قول أهلاً")), prefs.model, 60)
                        "✓ شغال (${p.label}): " + r.take(60)
                    } catch (e: Exception) { e.message ?: "فيه مشكلة" }
                    testing = false
                    toast(ctx, msg)
                }
            }, enabled = !testing && key.isNotBlank()) {
                if (testing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("جرّب")
            }
        }
    }
}

@Composable
private fun AppNameCard() {
    val prefs = SafiApp.prefs
    val ctx = LocalContext.current
    var name by remember { mutableStateOf(prefs.appName) }
    AppCard {
        Text("سمّي التطبيق والمساعد بالاسم اللي يعجبك.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        OutlinedTextField(name, { name = it }, label = { Text("الاسم") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                prefs.appName = name.trim().ifBlank { "أثر" }
                toast(ctx, "اتغير الاسم. اقفل التطبيق وافتحه علشان يظهر في كل مكان.")
            }) { Text("حفظ") }
            OutlinedButton(onClick = {
                prefs.appName = name.trim().ifBlank { "أثر" }
                com.mohamed.safi.AppName.pinShortcut(ctx)
            }) { Text("أيقونة بالاسم ده") }
        }
        Text(
            "أندرويد مش بيسمح بتغيير اسم الأيقونة الأصلية، فزرار \"أيقونة بالاسم ده\" بيحط أيقونة جديدة على الشاشة الرئيسية بالاسم اللي اخترته.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline,
        )
    }
}


@Composable
private fun VoiceSettingsCard() {
    var lang by remember { mutableStateOf(com.mohamed.safi.ui.VoicePrefs.lang) }
    var engine by remember { mutableStateOf(com.mohamed.safi.ui.VoicePrefs.engine) }
    val canAi = com.mohamed.safi.ui.VoicePrefs.aiCanTranscribe()
    AppCard {
        ChoiceField("لغة الكلام", lang, com.mohamed.safi.ui.VoicePrefs.languages.keys.toList(), display = { com.mohamed.safi.ui.VoicePrefs.languages[it] ?: it }) {
            lang = it; com.mohamed.safi.ui.VoicePrefs.lang = it
        }
        Spacer(Modifier.height(8.dp))
        Text("طريقة تحويل الكلام", fontWeight = FontWeight.SemiBold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(engine == "google", { engine = "google"; com.mohamed.safi.ui.VoicePrefs.engine = "google" })
            Column(Modifier.weight(1f)) {
                Text("صوت التليفون (مجاني)")
                Text("بتشوف الكلام وانت بتتكلم، وبيفضل يسمع لحد ما تدوس خلصت", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(engine == "ai", { engine = "ai"; com.mohamed.safi.ui.VoicePrefs.engine = "ai" })
            Column(Modifier.weight(1f)) {
                Text("بالذكاء الاصطناعي (أدق)")
                Text("بيسجّل كلامك كله وبعدين يحوّله لكتابة. أدق في اللهجات والجمل الطويلة. بيشتغل بمفتاح منفصل حتى لو مساعدك Claude.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
        }
        if (engine == "ai") SttKeyBox()
        Text("المساعد بيرد بنفس اللغة اللي بتكلمه بيها.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun SttKeyBox() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val vp = com.mohamed.safi.ui.VoicePrefs
    var prov by remember { mutableStateOf(vp.sttProvider) }
    var key by remember(prov) { mutableStateOf(SafiApp.prefs.keyOf(prov)) }
    var show by remember { mutableStateOf(false) }
    val links = mapOf("gemini" to "https://aistudio.google.com/apikey", "groq" to "https://console.groq.com/keys", "openai" to "https://platform.openai.com/api-keys")
    Column(Modifier.fillMaxWidth().padding(start = 12.dp, top = 4.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ChoiceField("خدمة تحويل الصوت", prov, vp.sttProviders.keys.toList(), display = { vp.sttProviders[it] ?: it }) {
            prov = it; vp.sttProvider = it
        }
        OutlinedTextField(
            key, { key = it }, label = { Text("مفتاح ${vp.sttProviders[prov]?.substringBefore(" (")}") }, singleLine = true,
            visualTransformation = if (show) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
            trailingIcon = { IconButton(onClick = { show = !show }) { Icon(if (show) Icons.Default.VisibilityOff else Icons.Default.Visibility, null) } },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { SafiApp.prefs.setKeyOf(prov, key); vp.sttProvider = prov; toast(ctx, "اتحفظ") }) { Text("حفظ") }
            OutlinedButton(onClick = { com.mohamed.safi.faith.Shaarawy.openUrl(ctx, links[prov] ?: "") }) { Text("هات مفتاح") }
        }
        Text(
            if (prov == "gemini") "مفتاح Gemini بيتعمل من حساب جوجل في دقيقة، وفيه استخدام مجاني بحدود يومية." else if (prov == "groq") "Groq فيه استخدام مجاني بحدود يومية." else "OpenAI بالدفع حسب الاستخدام.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline,
        )
    }
}

@Composable
private fun CompanionMemoryCard() {
    val ctx = LocalContext.current
    var snapshot by remember { mutableStateOf(com.mohamed.safi.ai.CompanionProfile.snapshot()) }
    AppCard {
        Text("رفيق يتعلم تفضيلاتك أنت فقط، وليس محادثاتك أو أصواتك.", fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text("المحفوظ حاليًا:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        Text(snapshot, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = {
            com.mohamed.safi.ai.CompanionProfile.clear()
            snapshot = com.mohamed.safi.ai.CompanionProfile.snapshot()
            toast(ctx, "اتمسحت تفضيلات رفيق فقط")
        }) { Text("امسح ذاكرة رفيق") }
        Text(
            "تقدر تقول لرفيق: افتكر إني بحب الرد المختصر، أو انسَ تفضيل الرد المختصر. بيانات المصاريف والمحادثات والملفات لا تُمسح من هذا الزر.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline,
        )
    }
}
