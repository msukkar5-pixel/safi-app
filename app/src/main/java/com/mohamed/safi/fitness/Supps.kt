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
    /** Arabic-Indic / Persian digits to 0-9 and '٫' to '.', keeping the Arabic comma '،' as a separator. */
    fun normalizeTimeDigits(s: String): String = buildString(s.length) {
        for (c in s) append(
            when (c) {
                in '٠'..'٩' -> '0' + (c - '٠')
                in '۰'..'۹' -> '0' + (c - '۰')
                '٫' -> '.'
                else -> c
            },
        )
    }

    private val timeRe = Regex(
        "(?<![\\d:.])(\\d{1,2})(?:\\s*[:.]\\s*(\\d{2}))?\\s*(a\\.?m\\.?|p\\.?m\\.?|صباحاً|صباحا|صباح|مساءً|مساءا|مساء|ص|م)?(?![\\p{L}\\d])",
        RegexOption.IGNORE_CASE,
    )

    /** Accepts "08:00, 20:00", "٨:٠٠ ، ٨:٣٠", "8", "8:30", "8 pm", "8 م", "8 ص". */
    fun parseTimes(s: String): List<LocalTime> =
        timeRe.findAll(normalizeTimeDigits(s)).mapNotNull { m ->
            var h = m.groupValues[1].toIntOrNull() ?: return@mapNotNull null
            val min = m.groupValues[2].ifEmpty { "0" }.toIntOrNull() ?: return@mapNotNull null
            val suf = m.groupValues[3].lowercase().replace(".", "")
            if (min > 59) return@mapNotNull null
            when {
                suf.isEmpty() -> if (h > 23) return@mapNotNull null
                else -> {
                    if (h !in 1..12) return@mapNotNull null
                    val pm = suf == "pm" || suf.startsWith("م")
                    h = if (pm) (if (h == 12) 12 else h + 12) else (if (h == 12) 0 else h)
                }
            }
            LocalTime.of(h, min)
        }.distinct().sorted().toList()

    /** Saves the supplement and replaces its daily reminders. */
    suspend fun save(ctx: Context, s: Supplement): Long {
        val id = Fit.dao.upsertSupplement(s).let { if (s.id != 0L) s.id else it }
        val dao = SafiApp.db.dao()
        dao.remindersFor("supp", id).forEach {
            ReminderScheduler.cancel(ctx, it.id)
            ReminderScheduler.forget(ctx, it.id)
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
            ReminderScheduler.forget(ctx, it.id)
            dao.deleteReminder(it)
        }
        Fit.dao.deleteSupplement(s)
    }
}
