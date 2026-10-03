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

object ReminderScheduler {
    private const val SNOOZE_OFFSET = 1_000_000

    fun canExact(ctx: Context): Boolean {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return false
        return Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
    }

    private fun pending(ctx: Context, id: Long, snooze: Boolean): PendingIntent {
        val i = Intent(ctx, ReminderReceiver::class.java).putExtra("id", id).putExtra("snooze", snooze)
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

    fun schedule(ctx: Context, r: Reminder) {
        cancel(ctx, r.id)
        if (r.done) return
        val at = triggerTime(r)
        if (at < System.currentTimeMillis() - 60_000) return
        setAt(ctx, at, pending(ctx, r.id, false), r.alarm)
    }

    fun snooze(ctx: Context, r: Reminder, minutes: Int = 10) {
        setAt(ctx, System.currentTimeMillis() + minutes * 60_000L, pending(ctx, r.id, true), r.alarm)
    }

    fun cancel(ctx: Context, id: Long) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        am.cancel(pending(ctx, id, false))
        am.cancel(pending(ctx, id, true))
    }

    /** Next occurrence strictly after now. */
    fun nextTime(time: Long, repeat: String): Long? {
        if (repeat == "none") return null
        var t = time.toLdt()
        val now = System.currentTimeMillis()
        var guard = 0
        do {
            t = when (repeat) {
                "daily" -> t.plusDays(1)
                "weekly" -> t.plusWeeks(1)
                "monthly" -> t.plusMonths(1)
                "yearly" -> t.plusYears(1)
                else -> return null
            }
            guard++
        } while (t.millis() <= now && guard < 5000)
        return t.millis()
    }

    suspend fun rescheduleAll(ctx: Context) {
        val dao = SafiApp.db.dao()
        dao.activeRemindersNow().forEach { r ->
            var rr = r
            if (triggerTime(r) < System.currentTimeMillis() && r.repeat != "none") {
                nextTime(r.time, r.repeat)?.let { rr = r.copy(time = it); dao.upsertReminder(rr) }
            }
            schedule(ctx, rr)
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
                val r = dao.reminder(id) ?: return@launch
                if (r.done) return@launch
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
                    NotificationCompat.Action(R.drawable.ic_notify, "تم ✓", ActionReceiver.intent(context, ActionReceiver.DONE, id)),
                    NotificationCompat.Action(R.drawable.ic_notify, "أجّل 10 دقايق", ActionReceiver.intent(context, ActionReceiver.SNOOZE, id)),
                )
                Notifier.show(
                    context, nid,
                    if (r.alarm) Notifier.CH_ALARM else Notifier.CH_REMIND,
                    (if (r.kind == "appointment") "📅 " else "⏰ ") + r.title,
                    text, route = "schedule", actions = actions, fullScreen = r.alarm,
                )
                if (!snoozed && r.repeat != "none") {
                    ReminderScheduler.nextTime(r.time, r.repeat)?.let { next ->
                        val updated = r.copy(time = next)
                        dao.upsertReminder(updated)
                        ReminderScheduler.schedule(context, updated)
                    }
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
                    } else Unit
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
                if (SafiApp.prefs.locationOn) {
                    runCatching { com.mohamed.safi.location.LocationService.start(context) }
                }
            } finally {
                pr.finish()
            }
        }
    }
}
