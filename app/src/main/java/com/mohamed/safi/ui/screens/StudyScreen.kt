package com.mohamed.safi.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import com.mohamed.safi.ui.Text
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mohamed.safi.data.zone
import com.mohamed.safi.family.Family
import com.mohamed.safi.study.Study
import com.mohamed.safi.ui.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import androidx.compose.material3.Text as RawText

/**
 * Lessons and homework for the children: timetable (school and private lessons), homework with notes and a done
 * check, a study timer, exams and grades. Parent and child both see and edit the same data through the family link.
 */
@Composable
fun StudyScreen(onBack: () -> Unit, open: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    @Suppress("UNUSED_VARIABLE") val v = Study.version.intValue
    @Suppress("UNUSED_VARIABLE") val fv = Family.version.intValue
    val kidPhone = com.mohamed.safi.kids.KidMode.on
    val students = Study.students().let { s -> if (kidPhone && Study.me != null) s.filter { it.id == Study.me } else s }
    var sid by remember { mutableStateOf(Study.me ?: students.firstOrNull()?.id) }
    if (sid != null && students.none { it.id == sid }) sid = students.firstOrNull()?.id
    var tab by remember { mutableIntStateOf(0) }
    var addStudent by remember { mutableStateOf(false) }
    var editLesson by remember { mutableStateOf<Study.Lesson?>(null) }
    var editHw by remember { mutableStateOf<Study.Homework?>(null) }
    var editExam by remember { mutableStateOf<Study.Exam?>(null) }
    var manual by remember { mutableStateOf(false) }

    // sync with family phones on the same Wi-Fi while this screen is open
    DisposableEffect(Family.joined) {
        if (Family.joined) Family.startLan(ctx)
        onDispose { Family.stopLan() }
    }

    ScreenScaffold("دروسي وواجباتي", onBack = onBack) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    students.forEach { s -> FilterChip(sid == s.id, { sid = s.id }, label = { RawText(s.name) }) }
                    if (!kidPhone) AssistChip(onClick = { addStudent = true }, label = { Text("ضيف ابن / بنت") }, leadingIcon = { Icon(Icons.Default.PersonAdd, null) })
                }
            }
            val id = sid
            if (id == null) {
                item { EmptyState(Icons.Default.School, "ضيف ابنك أو بنتك الأول، وبعدها حط الدروس والواجبات") }
                item { SyncCard(open, kidPhone) }
                return@LazyColumn
            }
            item { TodayCard(id) }
            item {
                TabRow(tab) {
                    listOf("الجدول", "الواجبات", "المذاكرة", "الامتحانات").forEachIndexed { i, t -> Tab(tab == i, { tab = i }, text = { Text(t, fontSize = 13.sp) }) }
                }
            }
            when (tab) {
                0 -> {
                    item { Button(onClick = { editLesson = Study.Lesson(Study.newId(), id, "", "", "", Study.kinds[1], emptySet(), "16:00", 60, "") }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Add, null); Text("ضيف درس أو حصة") } }
                    val all = Study.lessons(id)
                    if (all.isEmpty()) item { EmptyState(Icons.Default.CalendarMonth, "لسه مفيش دروس") }
                    Study.dayNames.forEach { (d, name) ->
                        val ls = all.filter { d in it.days }
                        if (ls.isNotEmpty()) {
                            item { Text(name, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) }
                            items(ls, key = { "l$d" + it.id }) { l -> LessonRow(l) { editLesson = l } }
                        }
                    }
                }
                1 -> {
                    item { Button(onClick = { editHw = Study.Homework(Study.newId(), id, "", "", LocalDate.now(zone).plusDays(1).toString(), false, 0, "مدرسة") }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Add, null); Text("ضيف واجب") } }
                    val hw = Study.homework(id)
                    if (hw.isEmpty()) item { EmptyState(Icons.Default.EditNote, "مفيش واجبات") }
                    items(hw.take(60), key = { it.id }) { h -> HomeworkRow(h, onEdit = { editHw = h }) { Study.setDone(h, it) } }
                }
                2 -> {
                    item { TimerCard(id) }
                    item { TextButton(onClick = { manual = true }) { Text("سجّل مذاكرة بإيدك") } }
                    item { StudyStats(id) }
                }
                else -> {
                    item { Button(onClick = { editExam = Study.Exam(Study.newId(), id, "", LocalDate.now(zone).plusDays(7).toString(), "", "") }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Add, null); Text("ضيف امتحان") } }
                    val ex = Study.exams(id)
                    if (ex.isEmpty()) item { EmptyState(Icons.Default.Grading, "مفيش امتحانات") }
                    items(ex, key = { it.id }) { e ->
                        val days = runCatching { ChronoUnit.DAYS.between(LocalDate.now(zone), LocalDate.parse(e.date)) }.getOrDefault(0L)
                        AppCard(onClick = { editExam = e }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    RawText(e.subject, fontWeight = FontWeight.Bold)
                                    RawText(e.date + if (e.note.isNotBlank()) " • ${e.note}" else "", style = MaterialTheme.typography.bodySmall)
                                }
                                if (e.grade.isNotBlank()) Pill("🎯 ${e.grade}")
                                else Pill(when { days > 1 -> tr("فاضل $days يوم"); days == 1L -> tr("بكرة"); days == 0L -> tr("النهارده"); else -> tr("فات") })
                            }
                        }
                    }
                }
            }
            item { SyncCard(open, kidPhone) }
            item { AlertSettings() }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (addStudent) {
        var n by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { addStudent = false }, title = { Text("ضيف ابن / بنت") },
            text = { OutlinedTextField(n, { n = it.take(20) }, label = { Text("الاسم") }, singleLine = true) },
            confirmButton = { Button(onClick = { if (n.isNotBlank()) { sid = Study.addStudent(n); addStudent = false } }) { Text("حفظ") } },
            dismissButton = { TextButton(onClick = { addStudent = false }) { Text("إلغاء") } })
    }
    editLesson?.let { l -> LessonDialog(l, { editLesson = null }) }
    editHw?.let { h -> HomeworkDialog(h, { editHw = null }) }
    editExam?.let { e -> ExamDialog(e, { editExam = null }) }
    if (manual && sid != null) {
        var subj by remember { mutableStateOf("") }; var min by remember { mutableStateOf("30") }
        AlertDialog(onDismissRequest = { manual = false }, title = { Text("سجّل مذاكرة") },
            text = { Column { SubjectField(sid!!, subj) { subj = it }; NumberField("دقايق", min, suffix = tr("دقيقة")) { min = it } } },
            confirmButton = { Button(onClick = { Study.addSession(sid!!, subj.ifBlank { tr("عام") }, System.currentTimeMillis(), min.toIntOrNull() ?: 0); manual = false }) { Text("حفظ") } },
            dismissButton = { TextButton(onClick = { manual = false }) { Text("إلغاء") } })
    }
}

@Composable
private fun TodayCard(sid: String) {
    val today = LocalDate.now(zone)
    val lessons = Study.lessonsOn(sid, today)
    val open = Study.openHomework(sid)
    val doneToday = Study.homework(sid).count { it.done && it.doneAt > 0 && java.time.Instant.ofEpochMilli(it.doneAt).atZone(zone).toLocalDate() == today }
    GoldCard {
        Text("النهارده", fontFamily = Amiri, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        if (lessons.isEmpty()) Text("مفيش دروس النهارده", color = MaterialTheme.colorScheme.outline)
        lessons.forEach { l -> RawText("📚 ${l.time}  ${l.subject}" + if (l.teacher.isNotBlank()) " — ${l.teacher}" else "") }
        Spacer(Modifier.height(6.dp))
        Row {
            StatBlock("ذاكر", "${Study.minutesOn(sid, today)} " + tr("د"), Modifier.weight(1f))
            StatBlock("واجبات خلصت", "$doneToday", Modifier.weight(1f))
            StatBlock("واجبات فاضلة", "${open.size}", Modifier.weight(1f), valueColor = if (open.isEmpty()) Positive else Danger)
        }
    }
}

@Composable
private fun LessonRow(l: Study.Lesson, onClick: () -> Unit) {
    AppCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RawText(l.time, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                RawText(l.subject, fontWeight = FontWeight.Bold)
                RawText(listOf(tr(l.kind), l.teacher, l.place).filter { it.isNotBlank() }.joinToString(" • "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                if (l.note.isNotBlank()) RawText("📝 ${l.note}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun HomeworkRow(h: Study.Homework, onEdit: () -> Unit, onDone: (Boolean) -> Unit) {
    val late = !h.done && h.due.isNotBlank() && h.due < LocalDate.now(zone).toString()
    AppCard(onClick = onEdit) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(h.done, onDone)
            Column(Modifier.weight(1f)) {
                RawText(h.subject, fontWeight = FontWeight.Bold, textDecoration = if (h.done) TextDecoration.LineThrough else null)
                if (h.text.isNotBlank()) RawText(h.text, style = MaterialTheme.typography.bodySmall)
                Text(tr(h.kind) + if (h.due.isNotBlank()) " • ${tr("التسليم")} ${h.due}" else "", style = MaterialTheme.typography.labelSmall, color = if (late) Danger else MaterialTheme.colorScheme.outline)
            }
            if (late) Pill(tr("متأخر"), Danger)
        }
    }
}

@Composable
private fun TimerCard(sid: String) {
    val ctx = LocalContext.current
    var subj by remember { mutableStateOf("") }
    val t = Study.timer()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(t) { while (t != null) { delay(1000); now = System.currentTimeMillis() } }
    GoldCard {
        if (t == null) {
            Text("ابدأ مذاكرة", fontWeight = FontWeight.Bold)
            SubjectField(sid, subj) { subj = it }
            Button(onClick = { Study.startTimer(sid, subj.ifBlank { tr("عام") }) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.PlayArrow, null); Text("ابدأ") }
        } else {
            val sec = ((now - t.third) / 1000).coerceAtLeast(0)
            RawText("${t.second}", fontWeight = FontWeight.Bold)
            RawText("%02d:%02d:%02d".format(java.util.Locale.US, sec / 3600, sec / 60 % 60, sec % 60), fontSize = 40.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Button(onClick = { val m = Study.stopTimer(); toast(ctx, "اتسجل $m دقيقة مذاكرة 👏") }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Stop, null); Text("خلصت") }
        }
    }
}

@Composable
private fun StudyStats(sid: String) {
    val week = Study.minutesBySubject(sid, LocalDate.now(zone).minusDays(6))
    val max = (week.values.maxOrNull() ?: 1).coerceAtLeast(1)
    AppCard {
        Text("آخر ٧ أيام: ${week.values.sum()} دقيقة", fontWeight = FontWeight.Bold)
        week.entries.sortedByDescending { it.value }.forEach { (s, m) -> BarRow(s, "$m " + tr("د"), m / max.toFloat(), MaterialTheme.colorScheme.primary) }
        Spacer(Modifier.height(6.dp))
        Row {
            (6 downTo 0).forEach { i ->
                val d = LocalDate.now(zone).minusDays(i.toLong())
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    RawText("${Study.minutesOn(sid, d)}", fontWeight = FontWeight.Bold)
                    RawText(d.dayOfMonth.toString(), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun SubjectField(sid: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(value, { onChange(it.take(30)) }, label = { Text("المادة") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    val known = Study.subjects(sid)
    if (known.isNotEmpty()) {
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) { known.take(10).forEach { s -> SuggestionChip(onClick = { onChange(s) }, label = { RawText(s) }) } }
    }
}

@Composable
private fun LessonDialog(l0: Study.Lesson, onDone: () -> Unit) {
    val ctx = LocalContext.current
    var l by remember { mutableStateOf(l0) }
    var del by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDone, title = { Text("درس / حصة") }, text = {
        Column(Modifier.verticalScrollable(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SubjectField(l.sid, l.subject) { l = l.copy(subject = it) }
            OutlinedTextField(l.teacher, { l = l.copy(teacher = it.take(30)) }, label = { Text("اسم المدرس") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(l.place, { l = l.copy(place = it.take(40)) }, label = { Text("المكان") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) { Study.kinds.forEach { k -> FilterChip(l.kind == k, { l = l.copy(kind = k) }, label = { Text(k) }) } }
            Text("الأيام", fontWeight = FontWeight.Bold)
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Study.dayNames.forEach { (d, n) -> FilterChip(d in l.days, { l = l.copy(days = if (d in l.days) l.days - d else l.days + d) }, label = { Text(n) }) }
            }
            OutlinedButton(onClick = {
                val (h, m) = l.time.split(":").map { it.toIntOrNull() ?: 0 }.let { (it.getOrElse(0) { 16 }) to (it.getOrElse(1) { 0 }) }
                val init = LocalDate.now(zone).atTime(h, m).atZone(zone).toInstant().toEpochMilli()
                pickTime(ctx, init) { hh, mm -> l = l.copy(time = "%02d:%02d".format(java.util.Locale.US, hh, mm)) }
            }) { Icon(Icons.Default.Schedule, null); Text("الساعة ${l.time}") }
            NumberField("المدة", l.minutes.toString(), suffix = tr("دقيقة")) { l = l.copy(minutes = it.toIntOrNull() ?: 60) }
            OutlinedTextField(l.note, { l = l.copy(note = it.take(200)) }, label = { Text("ملاحظات") }, modifier = Modifier.fillMaxWidth())
            if (l0.subject.isNotBlank()) TextButton(onClick = { del = true }) { Text("امسح", color = Danger) }
        }
    }, confirmButton = {
        Button(onClick = {
            if (l.subject.isBlank() || l.days.isEmpty()) toast(ctx, "اكتب المادة واختار يوم على الأقل") else { Study.saveLesson(l); onDone() }
        }) { Text("حفظ") }
    }, dismissButton = { TextButton(onClick = onDone) { Text("إلغاء") } })
    if (del) ConfirmDialog("تمسح الدرس؟", "", "امسح", { del = false }) { Study.delete("lessons", l0.id); del = false; onDone() }
}

@Composable
private fun HomeworkDialog(h0: Study.Homework, onDone: () -> Unit) {
    val ctx = LocalContext.current
    var h by remember { mutableStateOf(h0) }
    var del by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDone, title = { Text("واجب") }, text = {
        Column(Modifier.verticalScrollable(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SubjectField(h.sid, h.subject) { h = h.copy(subject = it) }
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) { listOf("مدرسة", "درس خصوصي").forEach { k -> FilterChip(h.kind == k, { h = h.copy(kind = k) }, label = { Text(k) }) } }
            OutlinedTextField(h.text, { h = h.copy(text = it.take(500)) }, label = { Text("المطلوب / ملاحظات") }, minLines = 3, modifier = Modifier.fillMaxWidth())
            DateField("التسليم", runCatching { LocalDate.parse(h.due).atStartOfDay(zone).toInstant().toEpochMilli() }.getOrNull(), withTime = false) { ms ->
                h = h.copy(due = java.time.Instant.ofEpochMilli(ms).atZone(zone).toLocalDate().toString())
            }
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(h.done, { h = h.copy(done = it, doneAt = if (it) System.currentTimeMillis() else 0) }); Text("عملته") }
            if (h0.subject.isNotBlank()) TextButton(onClick = { del = true }) { Text("امسح", color = Danger) }
        }
    }, confirmButton = {
        Button(onClick = { if (h.subject.isBlank()) toast(ctx, "اكتب المادة") else { Study.saveHomework(h); onDone() } }) { Text("حفظ") }
    }, dismissButton = { TextButton(onClick = onDone) { Text("إلغاء") } })
    if (del) ConfirmDialog("تمسح الواجب؟", "", "امسح", { del = false }) { Study.delete("homework", h0.id); del = false; onDone() }
}

@Composable
private fun ExamDialog(e0: Study.Exam, onDone: () -> Unit) {
    val ctx = LocalContext.current
    var e by remember { mutableStateOf(e0) }
    var del by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDone, title = { Text("امتحان") }, text = {
        Column(Modifier.verticalScrollable(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SubjectField(e.sid, e.subject) { e = e.copy(subject = it) }
            DateField("الميعاد", runCatching { LocalDate.parse(e.date).atStartOfDay(zone).toInstant().toEpochMilli() }.getOrNull(), withTime = false) { ms ->
                e = e.copy(date = java.time.Instant.ofEpochMilli(ms).atZone(zone).toLocalDate().toString())
            }
            OutlinedTextField(e.note, { e = e.copy(note = it.take(100)) }, label = { Text("المنهج / ملاحظات") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(e.grade, { e = e.copy(grade = it.take(20)) }, label = { Text("الدرجة (بعد الامتحان)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            if (e0.subject.isNotBlank()) TextButton(onClick = { del = true }) { Text("امسح", color = Danger) }
        }
    }, confirmButton = {
        Button(onClick = { if (e.subject.isBlank()) toast(ctx, "اكتب المادة") else { Study.saveExam(e); onDone() } }) { Text("حفظ") }
    }, dismissButton = { TextButton(onClick = onDone) { Text("إلغاء") } })
    if (del) ConfirmDialog("تمسح الامتحان؟", "", "امسح", { del = false }) { Study.delete("exams", e0.id); del = false; onDone() }
}

@Composable
private fun Modifier.verticalScrollable(): Modifier = this.verticalScroll(androidx.compose.foundation.rememberScrollState())

/** How parent and child stay in sync: home Wi-Fi automatically, or by sending the update. */
@Composable
private fun SyncCard(open: (String) -> Unit, kidPhone: Boolean) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    AppCard {
        Text("المتابعة مع العيلة", fontWeight = FontWeight.Bold)
        if (!Family.joined) {
            Text("اربطوا موبايلاتكم من «ربط العيلة» عشان ولي الأمر يشوف الدروس والواجبات والمذاكرة، وأي حد فيكم يضيف.", style = MaterialTheme.typography.bodySmall)
            if (!kidPhone) TextButton(onClick = { open("family") }) { Text("ربط العيلة") }
        } else {
            Text("على نفس الواي فاي بيتحدّث لوحده وانت فاتح الشاشة دي. برّا البيت: ابعت التحديث بواتساب، واللي يستلمه يعمل «مشاركة» للرسالة مع صافي.", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = {
                scope.launch {
                    val card = Family.myCard()
                    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, card), tr("ابعت التحديث")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }) { Icon(Icons.Default.Send, null); Text("ابعت التحديث") }
        }
    }
}

@Composable
private fun AlertSettings() {
    var on by remember { mutableStateOf(Study.alertsOn) }
    var lead by remember { mutableIntStateOf(Study.lead) }
    var hour by remember { mutableIntStateOf(Study.checkHour) }
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("تنبيهات الدروس والواجب", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Switch(on, { on = it; Study.alertsOn = it })
        }
        if (on) {
            Text("قبل الدرس بـ", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf(15, 30, 60).forEach { m -> FilterChip(lead == m, { lead = m; Study.lead = m }, label = { Text("$m دقيقة") }) } }
            Text("سؤال «عملت الواجب؟» الساعة", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf(18, 19, 20, 21).forEach { h -> FilterChip(hour == h, { hour = h; Study.checkHour = h }, label = { RawText("${h - 12}") }) } }
        }
    }
}
