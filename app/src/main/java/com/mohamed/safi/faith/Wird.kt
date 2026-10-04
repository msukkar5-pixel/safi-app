package com.mohamed.safi.faith

import android.content.Context
import androidx.core.content.edit
import com.mohamed.safi.SafiApp
import com.mohamed.safi.data.Reminder
import com.mohamed.safi.data.millis
import com.mohamed.safi.data.zone
import com.mohamed.safi.notify.ReminderScheduler
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/**
 * الورد اليومي: a fixed daily portion of Quran (by Madani mushaf pages, 604 total)
 * plus optional daily dhikr counts (استغفار، صلاة على النبي…).
 */
object Wird {
    const val TOTAL_PAGES = 604

    data class Extra(val text: String, val target: Int)

    private fun sp() = SafiApp.instance.getSharedPreferences("safi_wird", Context.MODE_PRIVATE)

    var pagesPerDay: Int get() = sp().getInt("ppd", 4); set(v) = sp().edit { putInt("ppd", v) }
    /** Next page to read (1..604). */
    var nextPage: Int get() = sp().getInt("next", 1); set(v) = sp().edit { putInt("next", v.coerceIn(1, TOTAL_PAGES)) }
    var khatmas: Int get() = sp().getInt("khatmas", 0); set(v) = sp().edit { putInt("khatmas", v) }
    var lastDoneDay: String get() = sp().getString("lastDone", "") ?: ""; set(v) = sp().edit { putString("lastDone", v) }
    var streak: Int get() = sp().getInt("streak", 0); set(v) = sp().edit { putInt("streak", v) }
    var targetDate: Long get() = sp().getLong("target", 0); set(v) = sp().edit { putLong("target", v) }

    var extras: List<Extra>
        get() = runCatching {
            val a = JSONArray(sp().getString("extras", null) ?: """[{"t":"أستغفر الله","n":100},{"t":"اللهم صلِّ وسلم على نبينا محمد","n":100}]""")
            (0 until a.length()).map { a.getJSONObject(it).let { o -> Extra(o.getString("t"), o.getInt("n")) } }
        }.getOrDefault(emptyList())
        set(v) = sp().edit { putString("extras", JSONArray(v.map { JSONObject().put("t", it.text).put("n", it.target) }).toString()) }

    /** Progress of extras for today: text -> count. */
    fun extraCount(text: String): Int = if (sp().getString("extrasDay", "") == today()) sp().getInt("x_$text", 0) else 0
    fun setExtraCount(text: String, n: Int) {
        if (sp().getString("extrasDay", "") != today()) {
            sp().edit { extras.forEach { remove("x_${it.text}") }; putString("extrasDay", today()) }
        }
        sp().edit { putInt("x_$text", n) }
    }

    private fun today() = LocalDate.now(zone).toString()
    val doneToday: Boolean get() = lastDoneDay == today()

    fun todayRange(): IntRange {
        val start = nextPage
        return start..(start + pagesPerDay - 1).coerceAtMost(TOTAL_PAGES)
    }

    fun markDone() {
        if (doneToday) return
        val y = LocalDate.now(zone).minusDays(1).toString()
        streak = if (lastDoneDay == y) streak + 1 else 1
        lastDoneDay = today()
        val end = todayRange().last
        if (end >= TOTAL_PAGES) { khatmas += 1; nextPage = 1 } else nextPage = end + 1
    }

    fun undo() {
        if (!doneToday) return
        val back = (nextPage - pagesPerDay).let { if (it < 1) TOTAL_PAGES - pagesPerDay + 1 else it }
        nextPage = back
        lastDoneDay = ""
        streak = (streak - 1).coerceAtLeast(0)
    }

    fun daysToFinish(): Int {
        val left = TOTAL_PAGES - nextPage + 1
        return (left + pagesPerDay - 1) / pagesPerDay
    }

    /** Pages per day needed to finish by a date. */
    fun pagesForDate(date: LocalDate): Int {
        val days = ChronoUnit.DAYS.between(LocalDate.now(zone), date).coerceAtLeast(1)
        val left = TOTAL_PAGES - nextPage + 1
        return ((left + days - 1) / days).toInt().coerceAtLeast(1)
    }

    // ---------- reminder ----------
    suspend fun reminderTime(): LocalTime? {
        val r = SafiApp.db.dao().remindersFor("wird", 1).firstOrNull() ?: return null
        return java.time.Instant.ofEpochMilli(r.time).atZone(zone).toLocalTime()
    }

    suspend fun setReminder(ctx: Context, time: LocalTime?) {
        val dao = SafiApp.db.dao()
        dao.remindersFor("wird", 1).forEach { ReminderScheduler.cancel(ctx, it.id); dao.deleteReminder(it) }
        if (time == null) return
        var at = LocalDate.now(zone).atTime(time)
        if (!at.isAfter(LocalDateTime.now(zone))) at = at.plusDays(1)
        val r = Reminder(title = "📖 وردك اليومي", note = "$pagesPerDay صفحات من المصحف", time = at.millis(), repeat = "daily", refType = "wird", refId = 1)
        val id = dao.upsertReminder(r)
        ReminderScheduler.schedule(ctx, r.copy(id = id))
    }
}
