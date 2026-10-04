package com.mohamed.safi.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.mohamed.safi.SafiApp
import com.mohamed.safi.ai.Claude
import com.mohamed.safi.data.*
import com.mohamed.safi.health.*
import com.mohamed.safi.notify.ReminderScheduler
import com.mohamed.safi.ui.*
import kotlinx.coroutines.launch
import java.io.File

private const val DISCLAIMER = "المعلومات دي للمتابعة بس ومش بديل عن الدكتور. أي تغيير في الدوا لازم يكون بإذن الدكتور."

@Composable
fun HealthRecordsScreen(onBack: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    ScreenScaffold("حالتي الصحية", onBack = onBack) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            TabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
                Tab(tab == 0, { tab = 0 }, text = { Text("الأدوية") })
                Tab(tab == 1, { tab = 1 }, text = { Text("التحاليل") })
                Tab(tab == 2, { tab = 2 }, text = { Text("الدكاترة") })
                Tab(tab == 3, { tab = 3 }, text = { Text("ملخص") })
            }
            when (tab) {
                0 -> MedsTab()
                1 -> LabsTab()
                2 -> VisitsTab()
                else -> HealthSummaryTab()
            }
        }
    }
}

// ------------------------------------------------------------------ medications

@Composable
private fun MedsTab() {
    val meds by HealthDb.dao.meds().collectAsState(emptyList())
    var editing by remember { mutableStateOf<Medication?>(null) }
    var adding by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Button(onClick = { adding = true }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Add, null); Text("دوا جديد") }
        }
        if (meds.isEmpty()) item { EmptyState(Icons.Default.Medication, "ضيف أدويتك بمواعيدها وهفكرك بكل جرعة") }
        items(meds, key = { it.id }) { m ->
            val ended = m.endDate != null && m.endDate < System.currentTimeMillis()
            AppCard(onClick = { editing = m }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CatBadge(m.name, 40, Icons.Default.Medication, if (m.active && !ended) Color2 else MaterialTheme.colorScheme.outline)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(m.name + if (m.dose.isNotBlank()) " — ${m.dose}" else "", fontWeight = FontWeight.SemiBold)
                        Text(
                            listOf(
                                if (m.times.isNotBlank()) "⏰ ${m.times}" else "",
                                m.withFood, m.reason,
                            ).filter { it.isNotBlank() }.joinToString(" • "),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline,
                        )
                        m.endDate?.let { Text(if (ended) "الكورس خلص" else "لحد ${shortDate(it)}", style = MaterialTheme.typography.bodySmall) }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        m.pillsLeft?.let { Text("باقي $it", fontWeight = FontWeight.Bold, color = if (it <= Meds.times(m.times).size * m.perDose * 5) Warn else MaterialTheme.colorScheme.onSurface) }
                        if (!m.active || ended) Pill("متوقف", MaterialTheme.colorScheme.outline)
                    }
                }
            }
        }
        item { Text("لما التنبيه يوصلك دوس \"تم\" والعدد بيقل لوحده، وهفكرك قبل ما الدوا يخلص.\n$DISCLAIMER", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline) }
    }
    if (adding) MedDialog(null) { adding = false }
    editing?.let { MedDialog(it) { editing = null } }
}

@Composable
private fun MedDialog(existing: Medication?, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var dose by remember { mutableStateOf(existing?.dose ?: "") }
    var times by remember { mutableStateOf(existing?.times ?: "") }
    var food by remember { mutableStateOf(existing?.withFood ?: "") }
    var reason by remember { mutableStateOf(existing?.reason ?: "") }
    var end by remember { mutableStateOf(existing?.endDate) }
    var pills by remember { mutableStateOf(existing?.pillsLeft?.toString() ?: "") }
    var perDose by remember { mutableStateOf((existing?.perDose ?: 1).toString()) }
    var active by remember { mutableStateOf(existing?.active ?: true) }
    var confirmDel by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "دوا جديد" else existing.name) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("اسم الدوا") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(dose, { dose = it }, label = { Text("الجرعة (قرص، 500 مجم، 10 وحدات…)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(times, { times = it }, label = { Text("المواعيد (08:00, 20:00)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row {
                    TextButton(onClick = {
                        pickTime(ctx, System.currentTimeMillis()) { h, m ->
                            times = (Meds.times(times).map { it.toString() } + String.format(java.util.Locale.US, "%02d:%02d", h, m)).distinct().sorted().joinToString(", ")
                        }
                    }) { Icon(Icons.Default.Schedule, null); Text("ضيف ميعاد") }
                    if (times.isNotBlank()) TextButton(onClick = { times = "" }) { Text("امسح") }
                }
                ChipsRow(listOf("قبل الأكل", "بعد الأكل", "مع الأكل", "على الريق", "قبل النوم"), food.ifBlank { null }, { it }) { food = if (food == it) "" else it }
                OutlinedTextField(reason, { reason = it }, label = { Text("لإيه (اختياري)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                DateField("آخر يوم في الكورس (اختياري)", end, withTime = false) { end = it }
                if (end != null) TextButton(onClick = { end = null }) { Text("مستمر من غير نهاية") }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField("العدد اللي معاك", pills, Modifier.weight(1f)) { pills = it.filter { c -> c.isDigit() } }
                    NumberField("كام في الجرعة", perDose, Modifier.weight(1f)) { perDose = it.filter { c -> c.isDigit() } }
                }
                Row(verticalAlignment = Alignment.CenterVertically) { Text("شغّال", Modifier.weight(1f)); Switch(active, { active = it }) }
                if (existing != null) TextButton(onClick = { confirmDel = true }, colors = ButtonDefaults.textButtonColors(contentColor = Danger)) { Text("امسح") }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isBlank()) toast(ctx, "اكتب اسم الدوا") else scope.launch {
                    Meds.save(
                        ctx,
                        Medication(
                            id = existing?.id ?: 0, name = name.trim(), dose = dose.trim(), times = times.trim(), withFood = food,
                            reason = reason.trim(), startDate = existing?.startDate ?: System.currentTimeMillis(),
                            endDate = end?.toLocalDate()?.millisAt(23, 59), pillsLeft = pills.toIntOrNull(),
                            perDose = perDose.toIntOrNull()?.coerceAtLeast(1) ?: 1, active = active,
                        ),
                    )
                    onDismiss()
                }
            }) { Text("حفظ", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
    if (confirmDel && existing != null) ConfirmDialog("مسح ${existing.name}؟", "وتنبيهاته", "امسح", { confirmDel = false }) {
        scope.launch { Meds.delete(ctx, existing); onDismiss() }
    }
}

// ------------------------------------------------------------------ lab results

private fun status(r: LabResult): Pair<String, Color> {
    val v = r.value ?: return "" to Color.Unspecified
    return when {
        r.low != null && v < r.low -> "منخفض" to Warn
        r.high != null && v > r.high -> "مرتفع" to Danger
        r.low != null || r.high != null -> "طبيعي" to Positive
        else -> "" to Color.Unspecified
    }
}

@Composable
private fun LabsTab() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val labs by HealthDb.dao.labs().collectAsState(emptyList())
    var adding by remember { mutableStateOf(false) }
    var reading by remember { mutableStateOf(false) }
    var extracted by remember { mutableStateOf<HealthAI.Extracted?>(null) }
    var photoPath by remember { mutableStateOf<String?>(null) }
    var detail by remember { mutableStateOf<String?>(null) }
    var explain by remember { mutableStateOf<String?>(null) }
    var explaining by remember { mutableStateOf(false) }
    var camFile by remember { mutableStateOf<File?>(null) }

    fun read(uri: Uri) {
        if (!Claude.hasKey) { toast(ctx, "قراءة التحليل محتاجة ذكاء اصطناعي مربوط من الإعدادات"); return }
        reading = true
        scope.launch {
            try {
                val dir = File(ctx.filesDir, "labs").apply { mkdirs() }
                val f = File(dir, "lab_${System.currentTimeMillis()}.jpg")
                ctx.contentResolver.openInputStream(uri)?.use { i -> f.outputStream().use { i.copyTo(it) } }
                photoPath = f.absolutePath
                extracted = HealthAI.readReport(ctx, uri)
            } catch (e: Exception) { toast(ctx, e.message ?: "فشل") }
            reading = false
        }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val f = camFile
        if (ok && f != null) read(Uri.fromFile(f))
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { it?.let(::read) }

    val byTest = labs.groupBy { it.test.lowercase() }.values.map { it.sortedByDescending { r -> r.date } }.sortedByDescending { it.first().date }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    val f = File(ctx.cacheDir, "labcam_${System.currentTimeMillis()}.jpg")
                    camFile = f
                    runCatching { camera.launch(FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)) }
                }, enabled = !reading, modifier = Modifier.weight(1f)) { Icon(Icons.Default.CameraAlt, null); Text(" صوّر التحليل") }
                OutlinedButton(onClick = { gallery.launch("image/*") }, enabled = !reading) { Icon(Icons.Default.PhotoLibrary, null) }
                OutlinedButton(onClick = { adding = true }) { Icon(Icons.Default.Add, null) }
            }
            if (reading) { LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 6.dp)); Text("بقرا التحليل…", style = MaterialTheme.typography.bodySmall) }
        }
        if (labs.isNotEmpty()) item {
            OutlinedButton(onClick = {
                if (!Claude.hasKey) toast(ctx, "اربط ذكاء اصطناعي من الإعدادات") else {
                    explaining = true
                    scope.launch {
                        val latestDate = labs.maxOf { it.date }.toLocalDate()
                        explain = try { HealthAI.explain(labs.filter { it.date.toLocalDate() == latestDate }) } catch (e: Exception) { "⚠️ " + (e.message ?: "") }
                        explaining = false
                    }
                }
            }, enabled = !explaining, modifier = Modifier.fillMaxWidth()) {
                if (explaining) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Text("اشرحلي آخر تحاليل")
            }
        }
        explain?.let { e -> item { AppCard(color = MaterialTheme.colorScheme.primaryContainer) { Text(e, lineHeight = 26.sp); Text(DISCLAIMER, style = MaterialTheme.typography.bodySmall) } } }
        if (labs.isEmpty()) item { EmptyState(Icons.Default.Science, "صوّر ورقة التحليل وأنا أسجل كل النتايج") }
        items(byTest, key = { it.first().test.lowercase() }) { list ->
            val r = list.first()
            val (st, color) = status(r)
            AppCard(onClick = { detail = r.test }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(r.test, fontWeight = FontWeight.SemiBold)
                        Text(
                            shortDate(r.date) + (if (r.low != null || r.high != null) " • الطبيعي ${r.low?.let { fmt(it) } ?: "?"}–${r.high?.let { fmt(it) } ?: "?"}" else "") +
                                if (list.size > 1) " • ${list.size} مرات" else "",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text((r.value?.let { fmt(it) } ?: r.valueText) + " " + r.unit, fontWeight = FontWeight.Bold)
                        if (st.isNotBlank()) Pill(st, color)
                    }
                }
            }
        }
        item { Text(DISCLAIMER, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline) }
    }

    if (adding) LabDialog { adding = false }
    extracted?.let { ex ->
        AlertDialog(
            onDismissRequest = { extracted = null },
            title = { Text("لقيت ${ex.items.size} نتيجة") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text((ex.date?.let { "بتاريخ ${shortDate(it)}" } ?: "من غير تاريخ (هتتسجل النهارده)") + if (ex.lab.isNotBlank()) " • ${ex.lab}" else "", style = MaterialTheme.typography.bodySmall)
                    ex.items.forEach { r ->
                        val (st, c) = status(r)
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(r.test, Modifier.weight(1f))
                            Text((r.value?.let { fmt(it) } ?: r.valueText) + " " + r.unit, fontWeight = FontWeight.SemiBold)
                            if (st.isNotBlank()) { Spacer(Modifier.width(4.dp)); Pill(st, c) }
                        }
                    }
                    Text("راجع الأرقام مع الورقة قبل الحفظ.", style = MaterialTheme.typography.bodySmall, color = Warn)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        ex.items.forEach { HealthDb.dao.upsertLab(it.copy(photoPath = photoPath)) }
                        toast(ctx, "اتسجل ${ex.items.size} نتيجة")
                        extracted = null
                    }
                }) { Text("احفظ الكل", fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { extracted = null }) { Text("إلغاء") } },
        )
    }
    detail?.let { test ->
        val list = labs.filter { it.test.equals(test, true) }.sortedBy { it.date }
        AlertDialog(
            onDismissRequest = { detail = null },
            title = { Text(test) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    val pts = list.filter { it.value != null }.map { (it.date / 86_400_000.0) to it.value!! }
                    if (pts.size >= 2) LineChart(pts, target = list.last().high)
                    list.reversed().forEach { r ->
                        val (st, c) = status(r)
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(shortDate(r.date), Modifier.weight(1f))
                            Text((r.value?.let { fmt(it) } ?: r.valueText) + " " + r.unit, fontWeight = FontWeight.SemiBold)
                            if (st.isNotBlank()) { Spacer(Modifier.width(4.dp)); Pill(st, c) }
                            r.photoPath?.let { p -> IconButton(onClick = { openFile(ctx, p) }) { Icon(Icons.Default.Image, "الصورة") } }
                            IconButton(onClick = { scope.launch { HealthDb.dao.deleteLab(r) } }) { Icon(Icons.Default.Delete, "امسح") }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { detail = null }) { Text("إغلاق") } },
        )
    }
}

@Composable
private fun LabDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var date by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var test by remember { mutableStateOf("") }
    var value by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("") }
    var low by remember { mutableStateOf("") }
    var high by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("نتيجة تحليل") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DateField("التاريخ", date, withTime = false) { date = it }
                OutlinedTextField(test, { test = it }, label = { Text("التحليل (HbA1c، Vitamin D، Cholesterol…)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField("القيمة", value, Modifier.weight(1f)) { value = it }
                    OutlinedTextField(unit, { unit = it }, label = { Text("الوحدة") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField("الطبيعي من", low, Modifier.weight(1f)) { low = it }
                    NumberField("لحد", high, Modifier.weight(1f)) { high = it }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (test.isBlank()) toast(ctx, "اكتب اسم التحليل") else scope.launch {
                    HealthDb.dao.upsertLab(LabResult(date = date, test = test.trim(), value = value.toDoubleOrNull(), unit = unit.trim(), low = low.toDoubleOrNull(), high = high.toDoubleOrNull()))
                    onDismiss()
                }
            }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}

// ------------------------------------------------------------------ doctor visits

@Composable
private fun VisitsTab() {
    val visits by HealthDb.dao.visits().collectAsState(emptyList())
    var editing by remember { mutableStateOf<Visit?>(null) }
    var adding by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Button(onClick = { adding = true }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Add, null); Text("زيارة دكتور") } }
        if (visits.isEmpty()) item { EmptyState(Icons.Default.LocalHospital, "سجّل زياراتك وكلام الدكتور والميعاد الجاي") }
        items(visits, key = { it.id }) { v ->
            AppCard(onClick = { editing = v }) {
                Text("${v.doctor}" + if (v.specialty.isNotBlank()) " — ${v.specialty}" else "", fontWeight = FontWeight.SemiBold)
                Text(shortDate(v.date) + if (v.place.isNotBlank()) " • ${v.place}" else "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                if (v.reason.isNotBlank()) Text(v.reason)
                if (v.notes.isNotBlank()) Text(v.notes, style = MaterialTheme.typography.bodySmall)
                v.nextVisit?.let { Text("الميعاد الجاي: ${dateTimeStr(it)}", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
    if (adding) VisitDialog(null) { adding = false }
    editing?.let { VisitDialog(it) { editing = null } }
}

@Composable
private fun VisitDialog(existing: Visit?, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var date by remember { mutableLongStateOf(existing?.date ?: System.currentTimeMillis()) }
    var doctor by remember { mutableStateOf(existing?.doctor ?: "") }
    var specialty by remember { mutableStateOf(existing?.specialty ?: "") }
    var place by remember { mutableStateOf(existing?.place ?: "") }
    var reason by remember { mutableStateOf(existing?.reason ?: "") }
    var notes by remember { mutableStateOf(existing?.notes ?: "") }
    var next by remember { mutableStateOf(existing?.nextVisit) }
    var confirmDel by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "زيارة دكتور" else existing.doctor) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DateField("التاريخ", date) { date = it }
                OutlinedTextField(doctor, { doctor = it }, label = { Text("الدكتور") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(specialty, { specialty = it }, label = { Text("التخصص") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(place, { place = it }, label = { Text("المستشفى / العيادة") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(reason, { reason = it }, label = { Text("سبب الزيارة") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(notes, { notes = it }, label = { Text("الدكتور قال إيه") }, modifier = Modifier.fillMaxWidth())
                DateField("الميعاد الجاي (اختياري)", next) { next = it }
                if (existing != null) TextButton(onClick = { confirmDel = true }, colors = ButtonDefaults.textButtonColors(contentColor = Danger)) { Text("امسح") }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (doctor.isBlank()) toast(ctx, "اكتب اسم الدكتور") else scope.launch {
                    val v = Visit(id = existing?.id ?: 0, date = date, doctor = doctor.trim(), specialty = specialty.trim(), place = place.trim(), reason = reason.trim(), notes = notes.trim(), nextVisit = next)
                    val id = HealthDb.dao.upsertVisit(v).let { if (existing != null) existing.id else it }
                    val dao = SafiApp.db.dao()
                    dao.remindersFor("visit", id).forEach { ReminderScheduler.cancel(ctx, it.id); dao.deleteReminder(it) }
                    next?.let { n ->
                        if (n > System.currentTimeMillis()) {
                            val r = Reminder(title = "🩺 ميعاد د. ${v.doctor}", time = n, kind = "appointment", location = v.place, remindBeforeMin = 120, refType = "visit", refId = id)
                            val rid = dao.upsertReminder(r)
                            ReminderScheduler.schedule(ctx, r.copy(id = rid))
                        }
                    }
                    onDismiss()
                }
            }) { Text("حفظ", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
    if (confirmDel && existing != null) ConfirmDialog("مسح الزيارة؟", existing.doctor, "امسح", { confirmDel = false }) {
        scope.launch { HealthDb.dao.deleteVisit(existing); onDismiss() }
    }
}

// ------------------------------------------------------------------ summary

@Composable
private fun HealthSummaryTab() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("ملخص لأدويتك وتحاليلك وزياراتك، وإيه اللي محتاج متابعة.", style = MaterialTheme.typography.bodyMedium)
        Button(onClick = {
            if (!Claude.hasKey) toast(ctx, "اربط ذكاء اصطناعي من الإعدادات") else {
                busy = true
                scope.launch {
                    text = try { HealthAI.summary() } catch (e: Exception) { "⚠️ " + (e.message ?: "") }
                    busy = false
                }
            }
        }, enabled = !busy) { if (busy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Text("اعمل الملخص") }
        text?.let { AppCard { Text(it, lineHeight = 26.sp) } }
        Text(DISCLAIMER, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
    }
}
