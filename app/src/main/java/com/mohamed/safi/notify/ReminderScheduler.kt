package com.mohamed.safi.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.mohamed.safi.R
import com.mohamed.safi.SafiApp
import com.mohamed.safi.data.Reminder
import com.mohamed.safi.data.millis
import com.mohamed.safi.data.timeStr
import com.mohamed.safi.data.toLdt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.YearMonth

object ReminderScheduler {
    private const val SNOOZE_OFFSET = 1_000_000

    fun canExact(ctx: Context): Boolean {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return false
        return Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
    }

    private fun pending(ctx: Context, id: Long, snooze: Boolean, occ: Long? = null): PendingIntent {
        val i = Intent(ctx, ReminderReceiver::class.java).putExtra("id", id).putExtra("snooze", snooze)
        if (occ != null) i.putExtra("occ", occ)
        val code = (id % 900_000).toInt() + if (snooze) SNOOZE_OFFSET else 0
        return PendingIntent.getBroadcast(ctx, code, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun setAt(ctx: Context, at: Long, pi: PendingIntent, loud: Boolean) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        try {
            if (canExact(ctx)) {
                if (loud) {
                    am.setAlarmClock(AlarmManager.AlarmClockInfo(at, Notifier.openAppIntent(ctx)), pi)
                } else {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
                }
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    fun triggerTime(r: Reminder): Long = r.time - r.remindBeforeMin * 60_000L

    private const val PREFS = "reminder_state"
    private fun sp(ctx: Context) = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Original day-of-month for monthly reminders, so short months don't make them drift to the 28th. */
    fun anchorDay(ctx: Context, r: Reminder): Int {
        val d = r.time.toLdt().toLocalDate()
        if (r.id <= 0) return d.dayOfMonth
        val key = "dom_${r.id}"
        val p = sp(ctx)
        val stored = p.getInt(key, 0)
        // Still valid if the stored day, clamped to this month, matches the reminder's date; otherwise it was edited.
        if (stored in 1..31 && minOf(stored, d.lengthOfMonth()) == d.dayOfMonth) return stored
        p.edit().putInt(key, d.dayOfMonth).apply()
        return d.dayOfMonth
    }

    fun forget(ctx: Context, id: Long) {
        sp(ctx).edit().remove("dom_$id").remove("fired_$id").apply()
    }

    /** One step forward from [time]; null for non-repeating. */
    private fun step(time: Long, repeat: String, anchorDay: Int?): Long? {
        val t = time.toLdt()
        return when (repeat) {
            "daily" -> t.plusDays(1).millis()
            "weekly" -> t.plusWeeks(1).millis()
            "monthly" -> {
                val ym = YearMonth.from(t).plusMonths(1)
                val day = minOf(anchorDay ?: t.dayOfMonth, ym.lengthOfMonth())
                ym.atDay(day).atTime(t.toLocalTime()).millis()
            }
            "yearly" -> t.plusYears(1).millis()
            else -> null
        }
    }

    /** Next occurrence strictly after now. */
    fun nextTime(time: Long, repeat: String, anchorDay: Int? = null): Long? {
        if (repeat == "none") return null
        val now = System.currentTimeMillis()
        var t = time
        var guard = 0
        do {
            t = step(t, repeat, anchorDay) ?: return null
            guard++
        } while (t <= now && guard < 5000)
        return t
    }

    /** Next occurrence strictly after now, keeping a monthly reminder on its original day. */
    fun nextTime(ctx: Context, r: Reminder): Long? =
        nextTime(r.time, r.repeat, if (r.repeat == "monthly") anchorDay(ctx, r) else null)

    /**
     * Works out what to store and when to ring.
     * `time` only moves forward once the event itself has passed; the alarm is set at
     * (occurrence - remindBefore), possibly for the occurrence after `time` if its early reminder already went.
     * Returns (reminder to store, occurrence the alarm is for or null).
     */
    private fun plan(ctx: Context, r: Reminder, grace: Long): Pair<Reminder, Long?> {
        val now = System.currentTimeMillis()
        val before = r.remindBeforeMin * 60_000L
        if (r.repeat == "none") {
            val occ = r.time.takeIf { it - before >= now - grace }
            return r to occ
        }
        val anchor = if (r.repeat == "monthly") anchorDay(ctx, r) else null
        var cur = r.time
        var guard = 0
        while (cur <= now && guard < 5000) { cur = step(cur, r.repeat, anchor) ?: return r to null; guard++ }
        var occ = cur
        guard = 0
        while (occ - before < now - grace && guard < 5000) { occ = step(occ, r.repeat, anchor) ?: return r to null; guard++ }
        return (if (cur != r.time) r.copy(time = cur) else r) to occ
    }

    private fun arm(ctx: Context, r: Reminder, occ: Long?) {
        cancel(ctx, r.id)
        if (r.done || occ == null) return
        setAt(ctx, occ - r.remindBeforeMin * 60_000L, pending(ctx, r.id, false, occ), r.alarm)
    }

    /** Schedules the reminder; a past repeating time is rolled forward and saved. */
    fun schedule(ctx: Context, r: Reminder) {
        if (r.done) { cancel(ctx, r.id); return }
        val (u, occ) = plan(ctx, r, 60_000)
        arm(ctx, u, occ)
        if (u.time != r.time && r.id > 0) receiverScope.launch { persistTime(r, u.time) }
    }

    /** Same as [schedule] but saves inline. A negative [grace] skips an occurrence whose alarm is due right now (it just fired). */
    suspend fun scheduleNow(ctx: Context, r: Reminder, grace: Long = 60_000) {
        if (r.done) { cancel(ctx, r.id); return }
        val (u, occ) = plan(ctx, r, grace)
        if (u.time != r.time && r.id > 0) persistTime(r, u.time)
        arm(ctx, u, occ)
    }

    private suspend fun persistTime(r: Reminder, time: Long) {
        val dao = SafiApp.db.dao()
        val cur = dao.reminder(r.id) ?: return
        if (!cur.done && cur.time == r.time) dao.upsertReminder(cur.copy(time = time))
    }

    fun snooze(ctx: Context, r: Reminder, minutes: Int = 10) {
        setAt(ctx, System.currentTimeMillis() + minutes * 60_000L, pending(ctx, r.id, true, r.time), r.alarm)
    }

    fun cancel(ctx: Context, id: Long) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        am.cancel(pending(ctx, id, false))
        am.cancel(pending(ctx, id, true))
    }

    /** Remember that a one-time reminder was shown, so a reboot/update doesn't show it again. */
    fun markFired(ctx: Context, r: Reminder) {
        sp(ctx).edit().putLong("fired_${r.id}", r.time).apply()
    }

    private fun wasFired(ctx: Context, r: Reminder): Boolean = sp(ctx).getLong("fired_${r.id}", Long.MIN_VALUE) == r.time

    fun show(ctx: Context, r: Reminder) {
        val id = r.id
        val nid = (id % 900_000).toInt() + 10_000
        val text = buildString {
            if (r.kind == "appointment") {
                append("الميعاد الساعة ${timeStr(r.time)}")
                if (r.location.isNotBlank()) append(" في ${r.location}")
            }
            if (r.note.isNotBlank()) {
                if (isNotEmpty()) append("\n")
                append(r.note)
            }
            if (isEmpty()) append(timeStr(r.time))
        }
        val actions = listOf(
            NotificationCompat.Action(R.drawable.ic_notify, "تم ✓", ActionReceiver.intent(ctx, ActionReceiver.DONE, id)),
            NotificationCompat.Action(R.drawable.ic_notify, "أجّل 10 دقايق", ActionReceiver.intent(ctx, ActionReceiver.SNOOZE, id)),
        )
        Notifier.show(
            ctx, nid,
            if (r.alarm) Notifier.CH_ALARM else Notifier.CH_REMIND,
            (if (r.kind == "appointment") "📅 " else "⏰ ") + r.title,
            text, route = when (r.refType) { "azkar" -> "azkar"; "wird" -> "wird"; "med" -> "healthrecords"; else -> "schedule" }, actions = actions, fullScreen = r.alarm,
        )
        if (r.repeat == "none") markFired(ctx, r)
    }

    suspend fun rescheduleAll(ctx: Context) {
        val dao = SafiApp.db.dao()
        val now = System.currentTimeMillis()
        dao.activeRemindersNow().forEach { r ->
            if (r.repeat == "none") {
                val at = triggerTime(r)
                // Missed while the phone was off: show it now if it fell due within the last 24h.
                if (at < now - 60_000 && at >= now - 86_400_000L && !wasFired(ctx, r)) {
                    runCatching { show(ctx, r) }
                }
            }
            scheduleNow(ctx, r)
        }
    }
}

private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra("id", -1)
        val snoozed = intent.getBooleanExtra("snooze", false)
        if (id < 0) return
        val pr = goAsync()
        receiverScope.launch {
            try {
                val dao = SafiApp.db.dao()
                var r = dao.reminder(id) ?: return@launch
                if (r.done) return@launch
                // The alarm may be for a later occurrence than the stored one (early reminder of the next one).
                val occ = intent.getLongExtra("occ", r.time)
                if (r.repeat != "none" && occ > r.time) {
                    r = r.copy(time = occ)
                    dao.upsertReminder(r)
                }
                ReminderScheduler.show(context, r)
                if (!snoozed && r.repeat != "none") {
                    // Moves `time` only if the event itself has passed; otherwise arms the next occurrence's early alarm.
                    ReminderScheduler.scheduleNow(context, r, grace = -5_000)
                }
            } finally {
                pr.finish()
            }
        }
    }
}

class ActionReceiver : BroadcastReceiver() {
    companion object {
        const val DONE = "com.mohamed.safi.DONE"
        const val SNOOZE = "com.mohamed.safi.SNOOZE"

        fun intent(ctx: Context, action: String, id: Long): PendingIntent {
            val i = Intent(ctx, ActionReceiver::class.java).setAction(action).putExtra("id", id)
            val code = (id % 900_000).toInt() + if (action == DONE) 2_000_000 else 3_000_000
            return PendingIntent.getBroadcast(ctx, code, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra("id", -1)
        if (id < 0) return
        Notifier.cancel(context, (id % 900_000).toInt() + 10_000)
        val pr = goAsync()
        receiverScope.launch {
            try {
                val dao = SafiApp.db.dao()
                val r = dao.reminder(id) ?: return@launch
                when (intent.action) {
                    DONE -> if (r.repeat == "none") {
                        dao.upsertReminder(r.copy(done = true))
                        ReminderScheduler.cancel(context, id)
                    } else {
                        if (r.refType == "med" && r.refId != null) com.mohamed.safi.health.Meds.taken(r.refId)
                    }
                    SNOOZE -> ReminderScheduler.snooze(context, r)
                    else -> Unit
                }
            } finally {
                pr.finish()
            }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pr = goAsync()
        receiverScope.launch {
            try {
                ReminderScheduler.rescheduleAll(context)
                DailyWorker.schedule(context, replace = false)
                runCatching { com.mohamed.safi.data.Carpool.schedule(context) }
                // also on time-zone change: follow the new place's prayer times
                runCatching { com.mohamed.safi.faith.Prayer.autoUpdate(context) }
                if (SafiApp.prefs.locationOn) {
                    runCatching { com.mohamed.safi.location.LocationService.start(context) }
                }
            } finally {
                pr.finish()
            }
        }
    }
}
