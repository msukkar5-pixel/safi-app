package com.mohamed.safi.fitness

import android.content.Context
import com.mohamed.safi.SafiApp
import com.mohamed.safi.data.Reminder
import com.mohamed.safi.data.millis
import com.mohamed.safi.data.zone
import com.mohamed.safi.notify.ReminderScheduler
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

object Supps {
    fun parseTimes(s: String): List<LocalTime> =
        s.split(",", "،", " ").mapNotNull { t ->
            val x = t.trim()
            runCatching { LocalTime.parse(if (x.length == 4) "0$x" else x) }.getOrNull()
        }.distinct().sorted()

    /** Saves the supplement and replaces its daily reminders. */
    suspend fun save(ctx: Context, s: Supplement): Long {
        val id = Fit.dao.upsertSupplement(s).let { if (s.id != 0L) s.id else it }
        val dao = SafiApp.db.dao()
        dao.remindersFor("supp", id).forEach {
            ReminderScheduler.cancel(ctx, it.id)
            dao.deleteReminder(it)
        }
        if (s.active) {
            val now = LocalDateTime.now(zone)
            for (t in parseTimes(s.times)) {
                var at = LocalDate.now(zone).atTime(t)
                if (!at.isAfter(now)) at = at.plusDays(1)
                val r = Reminder(
                    title = "💊 ${s.name}" + if (s.dose.isNotBlank()) " — ${s.dose}" else "",
                    note = s.note, time = at.millis(), repeat = "daily", kind = "reminder",
                    refType = "supp", refId = id,
                )
                val rid = dao.upsertReminder(r)
                ReminderScheduler.schedule(ctx, r.copy(id = rid))
            }
        }
        return id
    }

    suspend fun delete(ctx: Context, s: Supplement) {
        val dao = SafiApp.db.dao()
        dao.remindersFor("supp", s.id).forEach {
            ReminderScheduler.cancel(ctx, it.id)
            dao.deleteReminder(it)
        }
        Fit.dao.deleteSupplement(s)
    }
}
