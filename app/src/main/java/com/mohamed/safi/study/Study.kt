package com.mohamed.safi.study

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.mutableIntStateOf
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import com.mohamed.safi.R
import com.mohamed.safi.SafiApp
import com.mohamed.safi.data.zone
import com.mohamed.safi.notify.Notifier
import com.mohamed.safi.ui.tr
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Lessons, homework, study time and exams for each child.
 *
 * Every record carries the student id, an update time and a deleted flag, so the parent's phone and the child's phone
 * can both add and edit, and the newest change wins when they meet. The data travels inside the encrypted family card
 * (home Wi-Fi or any messenger), so it works the same in both directions with no server.
 */
object Study {
    data class Student(val id: String, val name: String)
    data class Lesson(val id: String, val sid: String, val subject: String, val teacher: String, val place: String, val kind: String,
                      val days: Set<Int>, val time: String, val minutes: Int, val note: String)
    data class Homework(val id: String, val sid: String, val subject: String, val text: String, val due: String, val done: Boolean,
                        val doneAt: Long, val kind: String)
    data class Session(val id: String, val sid: String, val subject: String, val start: Long, val minutes: Int)
    data class Exam(val id: String, val sid: String, val subject: String, val date: String, val note: String, val grade: String)

    val kinds = listOf("مدرسة", "درس خصوصي", "سنتر", "أونلاين")
    val dayNames = listOf(6 to "السبت", 7 to "الأحد", 1 to "الاثنين", 2 to "الثلاثاء", 3 to "الأربعاء", 4 to "الخميس", 5 to "الجمعة")

    private fun sp() = SafiApp.instance.getSharedPreferences("safi_study", Context.MODE_PRIVATE)
    val version = mutableIntStateOf(0)
    private fun bump() { version.intValue++ }
    fun newId() = java.util.UUID.randomUUID().toString().take(10)

    // ------------------------------------------------------------------ storage: one JSON array per kind, keyed by id
    private val types = listOf("students", "lessons", "homework", "sessions", "exams")
    private fun raw(type: String): MutableMap<String, JSONObject> {
        val a = runCatching { JSONArray(sp().getString(type, "[]")) }.getOrDefault(JSONArray())
        return (0 until a.length()).map { a.getJSONObject(it) }.associateBy { it.optString("id") }.toMutableMap()
    }
    private fun save(type: String, m: Map<String, JSONObject>) {
        // forget deleted records after 90 days
        val cut = System.currentTimeMillis() - 90L * 86_400_000L
        sp().edit { putString(type, JSONArray(m.values.filter { !(it.optBoolean("del") && it.optLong("upd") < cut) }).toString()) }
    }
    private fun put(type: String, o: JSONObject) {
        val m = raw(type); o.put("upd", System.currentTimeMillis()); m[o.getString("id")] = o; save(type, m); bump()
        StudyAlerts.schedule(SafiApp.instance)
    }
    fun delete(type: String, id: String) {
        val m = raw(type); m[id]?.let { it.put("del", true).put("upd", System.currentTimeMillis()); save(type, m); bump() }
    }
    private fun live(type: String) = raw(type).values.filter { !it.optBoolean("del") }

    // ------------------------------------------------------------------ students
    fun students(): List<Student> = live("students").map { Student(it.getString("id"), it.optString("name")) }.sortedBy { it.name }
    fun addStudent(name: String, id: String = newId()): String { put("students", JSONObject().put("id", id).put("name", name.trim())); return id }
    fun student(id: String?) = students().firstOrNull { it.id == id }

    // ------------------------------------------------------------------ lessons
    fun lessons(sid: String) = live("lessons").filter { it.optString("sid") == sid }.map {
        Lesson(it.getString("id"), sid, it.optString("subject"), it.optString("teacher"), it.optString("place"), it.optString("kind"),
            it.optString("days").split(",").mapNotNull { d -> d.toIntOrNull() }.toSet(), it.optString("time", "16:00"), it.optInt("min", 60), it.optString("note"))
    }.sortedBy { it.time }
    fun saveLesson(l: Lesson) = put("lessons", JSONObject().put("id", l.id).put("sid", l.sid).put("subject", l.subject).put("teacher", l.teacher)
        .put("place", l.place).put("kind", l.kind).put("days", l.days.joinToString(",")).put("time", l.time).put("min", l.minutes).put("note", l.note))
    fun lessonsOn(sid: String, d: LocalDate) = lessons(sid).filter { d.dayOfWeek.value in it.days }

    // ------------------------------------------------------------------ homework
    fun homework(sid: String) = live("homework").filter { it.optString("sid") == sid }.map {
        Homework(it.getString("id"), sid, it.optString("subject"), it.optString("text"), it.optString("due"), it.optBoolean("done"), it.optLong("doneAt"), it.optString("kind"))
    }.sortedWith(compareBy<Homework> { it.done }.thenBy { it.due })
    fun saveHomework(h: Homework) = put("homework", JSONObject().put("id", h.id).put("sid", h.sid).put("subject", h.subject).put("text", h.text)
        .put("due", h.due).put("done", h.done).put("doneAt", h.doneAt).put("kind", h.kind))
    fun setDone(h: Homework, v: Boolean) = saveHomework(h.copy(done = v, doneAt = if (v) System.currentTimeMillis() else 0))
    fun openHomework(sid: String) = homework(sid).filter { !it.done }

    // ------------------------------------------------------------------ study sessions
    fun sessions(sid: String) = live("sessions").filter { it.optString("sid") == sid }.map {
        Session(it.getString("id"), sid, it.optString("subject"), it.optLong("start"), it.optInt("min"))
    }.sortedByDescending { it.start }
    fun addSession(sid: String, subject: String, start: Long, minutes: Int) {
        if (minutes <= 0) return
        put("sessions", JSONObject().put("id", newId()).put("sid", sid).put("subject", subject).put("start", start).put("min", minutes))
    }
    private fun dayOf(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
    fun minutesOn(sid: String, d: LocalDate) = sessions(sid).filter { dayOf(it.start) == d }.sumOf { it.minutes }
    fun minutesBySubject(sid: String, from: LocalDate) = sessions(sid).filter { !dayOf(it.start).isBefore(from) }.groupBy { it.subject }.mapValues { e -> e.value.sumOf { it.minutes } }

    /** A running study timer (survives leaving the screen). */
    fun timer(): Triple<String, String, Long>? = sp().getString("timer", null)?.split("|")?.takeIf { it.size == 3 }?.let { Triple(it[0], it[1], it[2].toLongOrNull() ?: 0L) }
    fun startTimer(sid: String, subject: String) { sp().edit { putString("timer", "$sid|$subject|${System.currentTimeMillis()}") }; bump() }
    fun stopTimer(): Int {
        val t = timer() ?: return 0
        sp().edit { remove("timer") }
        val min = ((System.currentTimeMillis() - t.third) / 60_000L).toInt().coerceAtMost(600)
        addSession(t.first, t.second, t.third, min); bump()
        return min
    }

    // ------------------------------------------------------------------ exams
    fun exams(sid: String) = live("exams").filter { it.optString("sid") == sid }.map {
        Exam(it.getString("id"), sid, it.optString("subject"), it.optString("date"), it.optString("note"), it.optString("grade"))
    }.sortedBy { it.date }
    fun saveExam(e: Exam) = put("exams", JSONObject().put("id", e.id).put("sid", e.sid).put("subject", e.subject).put("date", e.date).put("note", e.note).put("grade", e.grade))

    /** Subjects already used for this student, for quick picking. */
    fun subjects(sid: String) = (lessons(sid).map { it.subject } + homework(sid).map { it.subject } + exams(sid).map { it.subject }).filter { it.isNotBlank() }.distinct()

    // ------------------------------------------------------------------ sync through the family card
    /** Everything recent, for the family card. */
    fun export(): JSONObject? {
        val cut = System.currentTimeMillis() - 60L * 86_400_000L
        val o = JSONObject()
        types.forEach { t ->
            val list = raw(t).values.filter { t != "sessions" || it.optLong("start") >= cut }
            if (list.isNotEmpty()) o.put(t, JSONArray(list))
        }
        return if (o.length() == 0) null else o
    }

    /** Merges another phone's records: the newer version of each record wins. */
    fun merge(o: JSONObject) {
        var changed = false
        types.forEach { t ->
            val a = o.optJSONArray(t) ?: return@forEach
            val m = raw(t)
            for (i in 0 until a.length()) {
                val r = a.optJSONObject(i) ?: continue
                val id = r.optString("id").ifBlank { null } ?: continue
                if (r.optLong("upd") > (m[id]?.optLong("upd") ?: -1L)) { m[id] = r; changed = true }
            }
            save(t, m)
        }
        if (changed) { bump(); StudyAlerts.schedule(SafiApp.instance) }
    }

    // ------------------------------------------------------------------ settings
    var alertsOn: Boolean get() = sp().getBoolean("alerts", true); set(v) { sp().edit { putBoolean("alerts", v) }; StudyAlerts.schedule(SafiApp.instance) }
    var lead: Int get() = sp().getInt("lead", 30); set(v) { sp().edit { putInt("lead", v) }; StudyAlerts.schedule(SafiApp.instance) }
    var checkHour: Int get() = sp().getInt("check", 20); set(v) { sp().edit { putInt("check", v) }; StudyAlerts.schedule(SafiApp.instance) }
    /** The student this phone belongs to (a child's phone), if any. */
    var me: String? get() = sp().getString("me", null); set(v) { sp().edit { putString("me", v) }; bump() }
}

/** Lesson reminders and the evening "did you do your homework?" check. */
object StudyAlerts {
    private const val ID_LESSON = 8_960_001
    private const val ID_HW = 8_960_002
    private const val ID_WEEK = 8_960_003

    private fun pi(ctx: Context, action: String, extra: String = "") = PendingIntent.getBroadcast(
        ctx, when (action) { "hw" -> ID_HW; "weekly" -> ID_WEEK; else -> ID_LESSON }, Intent(ctx, StudyReceiver::class.java).setAction(action).putExtra("x", extra),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun schedule(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        am.cancel(pi(ctx, "lesson")); am.cancel(pi(ctx, "hw")); am.cancel(pi(ctx, "weekly"))
        if (!Study.alertsOn) return
        val now = LocalDateTime.now(zone)
        val students = Study.students().let { s -> Study.me?.let { me -> s.filter { it.id == me } } ?: s }
        // the next lesson start minus the lead time, over the coming week
        val next = (0..7).flatMap { off ->
            val d = now.toLocalDate().plusDays(off.toLong())
            students.flatMap { st -> Study.lessonsOn(st.id, d).mapNotNull { l ->
                runCatching { LocalTime.parse(l.time) }.getOrNull()?.let { t -> d.atTime(t).minusMinutes(Study.lead.toLong()) to "${st.id}|${l.id}" }
            } }
        }.filter { it.first.isAfter(now) }.minByOrNull { it.first }
        next?.let { (at, x) -> set(am, at, pi(ctx, "lesson", x)) }
        if (students.isNotEmpty()) {
            val c = now.toLocalDate().atTime(Study.checkHour, 0).let { if (it.isAfter(now)) it else it.plusDays(1) }
            set(am, c, pi(ctx, "hw"))
        }
        // the parent's weekly report, Friday 8 pm (not on a child's own phone)
        if (Study.me == null && Study.students().isNotEmpty()) {
            var w = now.toLocalDate().atTime(20, 0)
            while (w.dayOfWeek != java.time.DayOfWeek.FRIDAY || !w.isAfter(now)) w = w.plusDays(1)
            set(am, w, pi(ctx, "weekly"))
        }
    }

    private fun set(am: AlarmManager, at: LocalDateTime, p: PendingIntent) {
        val ms = at.atZone(zone).toInstant().toEpochMilli()
        runCatching { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, p) }
    }

    fun fire(ctx: Context, action: String, extra: String) {
        when (action) {
            "lesson" -> {
                val (sid, lid) = extra.split("|").let { (it.getOrNull(0) ?: "") to (it.getOrNull(1) ?: "") }
                val st = Study.student(sid) ?: return
                val l = Study.lessons(sid).firstOrNull { it.id == lid } ?: return
                val who = if (Study.me == sid) "" else "${st.name}: "
                Notifier.show(ctx, ID_LESSON, Notifier.CH_REMIND, "📚 $who${tr("درس")} ${l.subject} ${tr("الساعة")} ${l.time}",
                    listOf(l.teacher, l.place).filter { it.isNotBlank() }.joinToString(" • ").ifBlank { tr("جهّز حاجتك") }, route = "study")
            }
            "hw" -> {
                val today = LocalDate.now(zone).toString()
                val students = Study.students().let { s -> Study.me?.let { me -> s.filter { it.id == me } } ?: s }
                val open = students.flatMap { st -> Study.openHomework(st.id).filter { it.due.isBlank() || it.due <= LocalDate.now(zone).plusDays(1).toString() }.map { st to it } }
                if (open.isEmpty()) return
                val first = open.first()
                val done = PendingIntent.getBroadcast(ctx, ID_HW + 10, Intent(ctx, StudyReceiver::class.java).setAction("done").putExtra("x", first.second.id),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                val who = if (Study.me != null) "" else "${first.first.name}: "
                Notifier.show(ctx, ID_HW, Notifier.CH_REMIND, "✏️ $who${tr("عملت الواجب؟")}",
                    open.joinToString("، ") { it.second.subject } + if (open.any { it.second.due == today }) " — ${tr("مطلوب بكرة أو النهارده")}" else "",
                    route = "study", actions = listOf(NotificationCompat.Action(R.drawable.ic_notify, tr("عملته ✓"), done)))
            }
            "weekly" -> {
                val from = LocalDate.now(zone).minusDays(6)
                val lines = Study.students().map { st ->
                    val min = Study.minutesBySubject(st.id, from).values.sum()
                    val hw = Study.homework(st.id)
                    val done = hw.count { it.done && it.doneAt >= from.atStartOfDay(zone).toInstant().toEpochMilli() }
                    val open = hw.count { !it.done }
                    val exam = Study.exams(st.id).firstOrNull { it.date >= LocalDate.now(zone).toString() && it.grade.isBlank() }
                    "${st.name}: ${tr("ذاكر")} $min ${tr("د")} • ✓$done • ${tr("فاضل")} $open" + (exam?.let { " • 📝 ${it.subject} ${it.date}" } ?: "")
                }
                if (lines.isEmpty()) return
                Notifier.show(ctx, ID_WEEK, Notifier.CH_DAILY, "📊 ${tr("تقرير الأسبوع للأولاد")}", lines.joinToString("\n"), route = "study")
            }
            "done" -> {
                Study.students().forEach { st -> Study.homework(st.id).firstOrNull { it.id == extra }?.let { Study.setDone(it, true) } }
                Notifier.cancel(ctx, ID_HW)
            }
        }
    }
}

class StudyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        runCatching { StudyAlerts.fire(context, intent.action ?: "", intent.getStringExtra("x").orEmpty()) }
        if (intent.action != "done") StudyAlerts.schedule(context)
    }
}
