package com.mohamed.safi.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import com.mohamed.safi.ui.Text
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mohamed.safi.ai.Claude
import com.mohamed.safi.data.dateTimeStr
import com.mohamed.safi.health.*
import com.mohamed.safi.ui.*
import kotlinx.coroutines.launch
import org.json.JSONArray

private fun levelColor(l: Int) = when (l) { 3 -> Danger; 2 -> Warn; 1 -> Color(0xFFB08A2E); else -> Positive }

/** Notes card shared with the health-records screen. */
@Composable
fun AdviceCards(advice: List<Advice>) {
    advice.forEach { a ->
        AppCard(color = if (a.level >= 3) Danger.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceContainerLow) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(if (a.level >= 2) Icons.Default.Warning else if (a.level == 1) Icons.Default.Info else Icons.Default.CheckCircle, null, tint = levelColor(a.level))
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(a.title, fontWeight = FontWeight.Bold, color = if (a.level >= 3) Danger else MaterialTheme.colorScheme.onSurface)
                    Text(a.text, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
    if (advice.isNotEmpty()) Text("دي معلومات عامة مش تشخيص. القرار دايماً لدكتورك.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
}

/** Loads manual + watch readings and the advice; reused by the health-records screen. */
@Composable
fun rememberVitals(): Pair<List<Vital>, List<Advice>> {
    val ctx = LocalContext.current
    val manual by remember { VitalsDb.dao.all() }.collectAsState(emptyList())
    val meds by remember { HealthDb.dao.meds() }.collectAsState(emptyList())
    val labs by remember { HealthDb.dao.labs() }.collectAsState(emptyList())
    var watch by remember { mutableStateOf<List<Vital>>(emptyList()) }
    LaunchedEffect(Unit) { watch = runCatching { Vitals.fromWatch(ctx) }.getOrDefault(emptyList()) }
    val all = remember(manual, watch) { (manual + watch).sortedByDescending { it.time } }
    val advice = remember(all, meds, labs) { Vitals.advise(all, meds.filter { it.active }, labs) }
    return all to advice
}

@Composable
fun VitalsScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val (all, advice) = rememberVitals()
    val meds by remember { HealthDb.dao.meds() }.collectAsState(emptyList())
    val labs by remember { HealthDb.dao.labs() }.collectAsState(emptyList())
    var adding by remember { mutableStateOf(false) }
    var range by remember { mutableIntStateOf(7) }
    var ai by remember { mutableStateOf<String?>(null) }
    var aiBusy by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Vital?>(null) }

    val now = System.currentTimeMillis()
    val inRange = all.filter { it.time >= now - range * 86_400_000L }
    val bp = all.firstOrNull { it.kind == "bp" && it.v2 != null }
    val rhr = all.firstOrNull { it.kind == "rhr" } ?: all.firstOrNull { it.kind == "hr" && it.source == "manual" }
    val today = all.filter { it.kind == "hr" && it.time >= java.time.LocalDate.now(com.mohamed.safi.data.zone).atStartOfDay(com.mohamed.safi.data.zone).toInstant().toEpochMilli() }
    val spo2 = all.firstOrNull { it.kind == "spo2" }

    ScreenScaffold(
        "القلب والضغط", onBack = onBack,
        fab = { ExtendedFloatingActionButton(onClick = { adding = true }, icon = { Icon(Icons.Default.Add, null) }, text = { Text("قياس") }) },
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 100.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    VitalTile("الضغط", bp?.let { "${it.v1.toInt()}/${it.v2!!.toInt()}" } ?: "—", bp?.let { Vitals.bpCategory(it.v1, it.v2!!).first } ?: "سجّل قراءة",
                        bp?.let { levelColor(Vitals.bpCategory(it.v1, it.v2!!).second) } ?: MaterialTheme.colorScheme.outline, Icons.Default.Bloodtype, Modifier.weight(1f))
                    VitalTile("نبض الراحة", rhr?.let { "${it.v1.toInt()}" } ?: "—", "نبضة/دقيقة", Brand, Icons.Default.MonitorHeart, Modifier.weight(1f))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    VitalTile("نبض النهارده", if (today.isNotEmpty()) "${today.mapNotNull { it.v2 }.minOrNull()?.toInt() ?: "—"}–${today.mapNotNull { it.v3 }.maxOrNull()?.toInt() ?: "—"}" else "—", "أقل–أعلى", Gold, Icons.Default.Favorite, Modifier.weight(1f))
                    VitalTile("الأكسجين", spo2?.let { "${it.v1.toInt()}%" } ?: "—", "SpO2", Color(0xFF2E7DBA), Icons.Default.Air, Modifier.weight(1f))
                }
            }
            if (advice.isNotEmpty()) {
                item { SectionTitle("ملاحظات على صحتك") }
                item { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { AdviceCards(advice) } }
            }
            item {
                OutlinedButton(onClick = {
                    if (!Claude.hasKey) { ai = "اربط ذكاء اصطناعي من الإعدادات الأول."; return@OutlinedButton }
                    aiBusy = true
                    scope.launch {
                        val summary = buildString {
                            appendLine("Readings (newest first):")
                            all.take(40).forEach { appendLine("${dateTimeStr(it.time)} ${it.kind} ${it.v1} ${it.v2 ?: ""} ${it.v3 ?: ""} (${it.source})") }
                            appendLine("Active medications: " + meds.filter { it.active }.joinToString { "${it.name} ${it.dose} (${it.reason})" })
                            appendLine("Recent labs: " + labs.take(25).joinToString { "${it.test}=${it.value ?: it.valueText} ${it.unit} [${it.low ?: ""}-${it.high ?: ""}]" })
                        }
                        ai = runCatching {
                            Claude.call(
                                "You are a careful health information assistant. Reply in Egyptian Arabic with Western digits, short bullet points. " +
                                    "You do not diagnose or change treatment. Explain trends in blood pressure, heart rate and SpO2, connect them to the medications and labs, " +
                                    "give practical lifestyle tips, say clearly when to see a doctor, and if anything is urgent say so first. End with: دي معلومات عامة مش تشخيص.",
                                JSONArray().put(Claude.userText(summary)), maxTokens = 1500,
                            )
                        }.getOrElse { it.message ?: "حصلت مشكلة" }
                        aiBusy = false
                    }
                }, enabled = !aiBusy, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.AutoAwesome, null); Text(if (aiBusy) "  بيحلل…" else "  حلّل قراءاتي بالذكاء الاصطناعي")
                }
            }
            ai?.let { t -> item { AppCard { Text(t, style = MaterialTheme.typography.bodyMedium) } } }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(7, 30, 90).forEach { d -> FilterChip(range == d, { range = d }, label = { Text("$d يوم") }) }
                }
            }
            val bpPts = inRange.filter { it.kind == "bp" && it.v2 != null }.sortedBy { it.time }
            if (bpPts.size >= 2) item {
                AppCard {
                    Text("الضغط الانقباضي (العالي)", fontWeight = FontWeight.SemiBold)
                    LineChart(bpPts.map { it.time.toDouble() to it.v1 }, color = Danger, target = 130.0)
                    Text("الانبساطي (الواطي)", fontWeight = FontWeight.SemiBold)
                    LineChart(bpPts.map { it.time.toDouble() to it.v2!! }, color = Brand, target = 80.0)
                }
            }
            val hrPts = inRange.filter { it.kind == "rhr" || it.kind == "hr" }.sortedBy { it.time }
            if (hrPts.size >= 2) item {
                AppCard {
                    Text("النبض", fontWeight = FontWeight.SemiBold)
                    LineChart(hrPts.map { it.time.toDouble() to it.v1 }, color = Gold)
                }
            }
            item { SectionTitle("القراءات") }
            if (all.isEmpty()) item { EmptyState(Icons.Default.MonitorHeart, "سجّل قياس الضغط بإيدك، أو اربط ساعة سامسونج من «الجيم والصحة» علشان النبض والأكسجين يتسحبوا لوحدهم.") }
            items(all.take(80), key = { "${it.source}${it.kind}${it.time}${it.id}" }) { v ->
                AppCard(onClick = if (v.source == "manual") ({ deleting = v }) else null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            when (v.kind) { "bp" -> "ضغط"; "rhr" -> "نبض راحة"; "spo2" -> "أكسجين"; else -> "نبض" },
                            fontWeight = FontWeight.SemiBold, modifier = Modifier.width(80.dp),
                        )
                        Text(
                            when (v.kind) {
                                "bp" -> "${v.v1.toInt()}/${v.v2?.toInt()}" + (v.v3?.let { " • نبض ${it.toInt()}" } ?: "")
                                "spo2" -> "${v.v1.toInt()}%"
                                "hr" -> "${v.v1.toInt()}" + if (v.v2 != null && v.v3 != null) " (${v.v2.toInt()}–${v.v3.toInt()})" else ""
                                else -> "${v.v1.toInt()}"
                            },
                            Modifier.weight(1f), fontWeight = FontWeight.Bold,
                        )
                        Column(horizontalAlignment = Alignment.End) {
                            Text(dateTimeStr(v.time), style = MaterialTheme.typography.labelSmall)
                            Text(if (v.source == "watch") "الساعة" else "يدوي", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                    if (v.note.isNotBlank()) Text(v.note, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
    if (adding) VitalDialog { adding = false }
    deleting?.let { v -> ConfirmDialog("تمسح القراءة دي؟", dateTimeStr(v.time), "امسح", { deleting = null }) { scope.launch { VitalsDb.dao.delete(v) } } }
}

@Composable
private fun VitalTile(label: String, value: String, sub: String, color: Color, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier) {
    AppCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = color, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
        Text(value, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text(sub, color = color, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun VitalDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var kind by remember { mutableStateOf("bp") }
    var a by remember { mutableStateOf("") }
    var b by remember { mutableStateOf("") }
    var c by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var time by remember { mutableLongStateOf(System.currentTimeMillis()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("قياس جديد") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(listOf("bp" to "ضغط", "hr" to "نبض", "spo2" to "أكسجين")) { (k, l) -> FilterChip(kind == k, { kind = k }, label = { Text(l) }) }
                }
                when (kind) {
                    "bp" -> {
                        NumberField("العالي (الانقباضي)", a) { a = it }
                        NumberField("الواطي (الانبساطي)", b) { b = it }
                        NumberField("النبض (اختياري)", c) { c = it }
                    }
                    "hr" -> NumberField("النبض (نبضة/دقيقة)", a) { a = it }
                    else -> NumberField("نسبة الأكسجين %", a) { a = it }
                }
                DateField("الوقت", time) { time = it }
                OutlinedTextField(note, { note = it }, label = { Text("ملاحظة (بعد مجهود، صداع…)") }, modifier = Modifier.fillMaxWidth())
                if (kind == "bp") Text("قيس وانت قاعد ومرتاح ٥ دقايق، ودراعك على مستوى القلب.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val v1 = a.toDoubleOrNull()
                val v2 = b.toDoubleOrNull()
                if (v1 == null || (kind == "bp" && v2 == null)) { toast(ctx, "اكتب الأرقام"); return@TextButton }
                scope.launch {
                    VitalsDb.dao.upsert(
                        Vital(time = time, kind = if (kind == "hr") "rhr" else kind, v1 = v1, v2 = if (kind == "bp") v2 else null,
                            v3 = if (kind == "bp") c.toDoubleOrNull() else null, note = note.trim()),
                    )
                    if (kind == "bp" && Vitals.bpCategory(v1, v2!!).second >= 3) toast(ctx, "القراءة عالية جداً — بص على الملاحظات")
                    onDismiss()
                }
            }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}

/** Compact vitals + advice, used as a tab in Health records and Gym & health. */
@Composable
fun VitalsSummary() {
    val (all, advice) = rememberVitals()
    val bp = all.firstOrNull { it.kind == "bp" && it.v2 != null }
    val rhr = all.firstOrNull { it.kind == "rhr" }
    val spo2 = all.firstOrNull { it.kind == "spo2" }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                VitalTile("الضغط", bp?.let { "${it.v1.toInt()}/${it.v2!!.toInt()}" } ?: "—", bp?.let { Vitals.bpCategory(it.v1, it.v2!!).first } ?: "مفيش قراءة",
                    bp?.let { levelColor(Vitals.bpCategory(it.v1, it.v2!!).second) } ?: MaterialTheme.colorScheme.outline, Icons.Default.Bloodtype, Modifier.weight(1f))
                VitalTile("نبض الراحة", rhr?.let { "${it.v1.toInt()}" } ?: "—", "نبضة/دقيقة", Brand, Icons.Default.MonitorHeart, Modifier.weight(1f))
                VitalTile("الأكسجين", spo2?.let { "${it.v1.toInt()}%" } ?: "—", "SpO2", Color(0xFF2E7DBA), Icons.Default.Air, Modifier.weight(1f))
            }
        }
        item {
            Button(onClick = { UiBus.pendingRoute.value = "vitals" }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.MonitorHeart, null); Text("  سجّل قياس وشوف التفاصيل والرسوم")
            }
        }
        if (advice.isNotEmpty()) {
            item { SectionTitle("ملاحظات على صحتك") }
            item { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { AdviceCards(advice) } }
        }
    }
}
