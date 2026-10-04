package com.mohamed.safi.ui.screens

import android.content.Intent
import android.provider.AlarmClock
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.mohamed.safi.SafiApp
import com.mohamed.safi.data.*
import com.mohamed.safi.notify.ReminderScheduler
import com.mohamed.safi.ui.*
import kotlinx.coroutines.launch
import java.time.LocalDate

private fun repeatLabel(r: String) = when (r) {
    "daily" -> "كل يوم"
    "weekly" -> "كل أسبوع"
    "monthly" -> "كل شهر"
    "yearly" -> "كل سنة"
    else -> "مرة واحدة"
}

@Composable
fun ScheduleScreen() {
    val dao = SafiApp.db.dao()
    val all by dao.reminders().collectAsState(emptyList())
    var tab by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<Reminder?>(null) }
    var adding by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current

    ScreenScaffold(
        "المواعيد والتذكيرات",
        fab = {
            if (tab < 2) ExtendedFloatingActionButton(
                onClick = { adding = true }, icon = { Icon(Icons.Default.Add, null) },
                text = { Text(if (tab == 0) "تذكير" else "ميعاد") },
            )
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            TabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
                Tab(tab == 0, { tab = 0 }, text = { Text("تذكيرات") }, icon = { Icon(Icons.Default.Notifications, null) })
                Tab(tab == 1, { tab = 1 }, text = { Text("مواعيد") }, icon = { Icon(Icons.Default.Event, null) })
                Tab(tab == 2, { tab = 2 }, text = { Text("منبه ومؤقت") }, icon = { Icon(Icons.Default.Alarm, null) })
            }
            if (tab == 2) {
                AlarmsTab()
            } else {
                val kind = if (tab == 0) "reminder" else "appointment"
                val list = all.filter { it.kind == kind }
                val active = list.filter { !it.done }
                val done = list.filter { it.done }.sortedByDescending { it.time }.take(20)
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 100.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (active.isEmpty()) item {
                        EmptyState(
                            if (tab == 0) Icons.Default.Notifications else Icons.Default.Event,
                            if (tab == 0) "قول لـ${com.mohamed.safi.AppName.v}: فكرني بكرة الساعة 9 أكلم البنك" else "ضيف مواعيدك وهفكرك قبلها",
                        )
                    }
                    val groups = active.groupBy { it.time.toLocalDate() }
                    groups.forEach { (day, rs) ->
                        item(key = "h$day") {
                            Text(
                                if (day == LocalDate.now(zone)) "النهارده" else dateStr(rs.first().time),
                                fontWeight = FontWeight.Bold,
                                color = if (day.isBefore(LocalDate.now(zone))) Danger else MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                        items(rs, key = { it.id }) { r ->
                            ReminderRow(r, onClick = { editing = r }) {
                                scope.launch {
                                    if (r.repeat == "none") {
                                        dao.upsertReminder(r.copy(done = true))
                                        ReminderScheduler.cancel(ctx, r.id)
                                    } else {
                                        ReminderScheduler.nextTime(r.time, r.repeat)?.let {
                                            val u = r.copy(time = it)
                                            dao.upsertReminder(u)
                                            ReminderScheduler.schedule(ctx, u)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (done.isNotEmpty()) {
                        item { SectionTitle("خلصت") }
                        items(done, key = { "d" + it.id }) { r -> ReminderRow(r, onClick = { editing = r }) {} }
                    }
                }
            }
        }
    }

    if (adding) ReminderEditor(null, if (tab == 1) "appointment" else "reminder") { adding = false }
    editing?.let { r -> ReminderEditor(r, r.kind) { editing = null } }
}

@Composable
private fun ReminderRow(r: Reminder, onClick: () -> Unit, onDone: () -> Unit) {
    AppCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = r.done, onCheckedChange = { if (!r.done) onDone() })
            Spacer(Modifier.width(4.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    r.title, fontWeight = FontWeight.SemiBold,
                    textDecoration = if (r.done) TextDecoration.LineThrough else null,
                )
                val sub = buildString {
                    append(timeStr(r.time))
                    if (r.repeat != "none") append(" • ${repeatLabel(r.repeat)}")
                    if (r.location.isNotBlank()) append(" • 📍 ${r.location}")
                    if (r.remindBeforeMin > 0) append(" • قبلها ${beforeLabel(r.remindBeforeMin)}")
                }
                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                if (r.note.isNotBlank()) Text(r.note, style = MaterialTheme.typography.bodySmall, maxLines = 2)
            }
            if (r.alarm) Icon(Icons.Default.Alarm, "منبه", tint = Warn)
        }
    }
}

private fun beforeLabel(min: Int) = when {
    min >= 1440 -> "${min / 1440} يوم"
    min >= 60 -> "${min / 60} ساعة"
    else -> "$min دقيقة"
}

@Composable
private fun ReminderEditor(existing: Reminder?, kind: String, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf(existing?.title ?: "") }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    var time by remember { mutableStateOf(existing?.time ?: (System.currentTimeMillis() + 3_600_000L).let { it - it % 60_000 }) }
    var repeat by remember { mutableStateOf(existing?.repeat ?: "none") }
    var location by remember { mutableStateOf(existing?.location ?: "") }
    var before by remember { mutableIntStateOf(existing?.remindBeforeMin ?: if (kind == "appointment") 60 else 0) }
    var alarm by remember { mutableStateOf(existing?.alarm ?: false) }
    var confirmDelete by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) (if (kind == "appointment") "ميعاد جديد" else "تذكير جديد") else "تعديل") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("إيه؟") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                DateField("إمتى", time) { time = it }
                ChoiceField("التكرار", repeat, listOf("none", "daily", "weekly", "monthly", "yearly"), display = ::repeatLabel) { repeat = it }
                if (kind == "appointment") {
                    OutlinedTextField(location, { location = it }, label = { Text("المكان") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    ChoiceField("فكرني قبلها", before.toString(), listOf("0", "15", "30", "60", "120", "1440"),
                        display = { v -> if (v == "0") "في الميعاد" else beforeLabel(v.toInt()) }) { before = it.toInt() }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("بصوت المنبه")
                        Text("يرن بصوت عالي ويظهر على الشاشة حتى لو مقفولة", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    }
                    Switch(alarm, { alarm = it })
                }
                OutlinedTextField(note, { note = it }, label = { Text("ملاحظة") }, modifier = Modifier.fillMaxWidth())
                if (existing != null) {
                    TextButton(onClick = { confirmDelete = true }, colors = ButtonDefaults.textButtonColors(contentColor = Danger)) {
                        Icon(Icons.Default.Delete, null); Spacer(Modifier.width(6.dp)); Text("امسح")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (title.isBlank()) toast(ctx, "اكتب التذكير") else scope.launch {
                    val r = Reminder(
                        id = existing?.id ?: 0, title = title.trim(), note = note.trim(), time = time, repeat = repeat,
                        kind = kind, location = location.trim(), remindBeforeMin = before, alarm = alarm,
                        done = false, refType = existing?.refType, refId = existing?.refId,
                    )
                    val id = SafiApp.db.dao().upsertReminder(r)
                    ReminderScheduler.schedule(ctx, r.copy(id = existing?.id ?: id))
                    if (!ReminderScheduler.canExact(ctx)) toast(ctx, "اسمح بالمنبهات الدقيقة من الإعدادات علشان التذكير ميتأخرش")
                    onDismiss()
                }
            }) { Text("حفظ", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
    if (confirmDelete && existing != null) {
        ConfirmDialog("مسح؟", existing.title, "امسح", { confirmDelete = false }) {
            scope.launch {
                ReminderScheduler.cancel(ctx, existing.id)
                SafiApp.db.dao().deleteReminder(existing)
                onDismiss()
            }
        }
    }
}

@Composable
private fun AlarmsTab() {
    val ctx = LocalContext.current
    var time by remember { mutableStateOf(LocalDate.now(zone).plusDays(1).millisAt(6, 0)) }
    var label by remember { mutableStateOf("") }
    val dayNames = listOf("ح" to 1, "ن" to 2, "ث" to 3, "ر" to 4, "خ" to 5, "ج" to 6, "س" to 7)
    var days by remember { mutableStateOf(setOf<Int>()) }

    fun startSafe(i: Intent) {
        try { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) { toast(ctx, "تطبيق الساعة مش متاح") }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AppCard {
            Text("منبه جديد", fontWeight = FontWeight.Bold)
            Text("بيتضاف في تطبيق الساعة بتاع تليفونك، فبيرن حتى لو ${com.mohamed.safi.AppName.v} مقفول.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = { pickTime(ctx, time) { h, m -> time = time.toLdt().withHour(h).withMinute(m).millis() } }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Schedule, null); Spacer(Modifier.width(8.dp)); Text(timeStr(time), style = MaterialTheme.typography.titleLarge)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                dayNames.forEach { (n, d) ->
                    FilterChip(selected = d in days, onClick = { days = if (d in days) days - d else days + d }, label = { Text(n) }, modifier = Modifier.weight(1f))
                }
            }
            Text(if (days.isEmpty()) "مرة واحدة" else "بيتكرر في الأيام المختارة", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            OutlinedTextField(label, { label = it }, label = { Text("اسم المنبه") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Button(onClick = {
                val t = time.toLdt()
                val i = Intent(AlarmClock.ACTION_SET_ALARM)
                    .putExtra(AlarmClock.EXTRA_HOUR, t.hour)
                    .putExtra(AlarmClock.EXTRA_MINUTES, t.minute)
                    .putExtra(AlarmClock.EXTRA_MESSAGE, label.ifBlank { "${com.mohamed.safi.AppName.v}" })
                    .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                if (days.isNotEmpty()) i.putIntegerArrayListExtra(AlarmClock.EXTRA_DAYS, ArrayList(days.sorted()))
                startSafe(i)
                toast(ctx, "المنبه اتضاف ${timeStr(time)}")
            }, modifier = Modifier.fillMaxWidth()) { Text("ضيف المنبه") }
        }
        AppCard {
            Text("مؤقت سريع", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            val mins = listOf(5, 10, 15, 30, 45, 60)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                mins.forEach { m ->
                    OutlinedButton(
                        onClick = {
                            startSafe(
                                Intent(AlarmClock.ACTION_SET_TIMER)
                                    .putExtra(AlarmClock.EXTRA_LENGTH, m * 60)
                                    .putExtra(AlarmClock.EXTRA_MESSAGE, "${com.mohamed.safi.AppName.v}")
                                    .putExtra(AlarmClock.EXTRA_SKIP_UI, true),
                            )
                            toast(ctx, "مؤقت $m دقيقة")
                        },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(0.dp),
                    ) { Text("$m") }
                }
            }
            Text("بالدقايق", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        }
        OutlinedButton(onClick = { startSafe(Intent(AlarmClock.ACTION_SHOW_ALARMS)) }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Alarm, null); Spacer(Modifier.width(8.dp)); Text("افتح كل المنبهات")
        }
        Text(
            "تقدر كمان تقول لـ${com.mohamed.safi.AppName.v} بالصوت: \"صحيني كل يوم الساعة 6 ونص الصبح ما عدا الجمعة\"",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline,
        )
    }
}
