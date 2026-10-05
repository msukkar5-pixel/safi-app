package com.mohamed.safi.faith

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import com.mohamed.safi.R
import com.mohamed.safi.SafiApp
import com.mohamed.safi.data.zone
import com.mohamed.safi.notify.Notifier
import com.mohamed.safi.notify.ReminderScheduler
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Worship alerts the user can tune: adhkar (fixed time or relative to a prayer), the daily wird
 * (skipped when already done), a pre-adhan heads-up, and after-prayer adhkar.
 */
object FaithAlerts {
    val PRAYERS = listOf("الفجر", "الظهر", "العصر", "المغرب", "العشاء")

    /** id, title shown in settings, the prayer a relative time is anchored to. */
    data class AzkarSlot(val id: String, val label: String, val anchor: String, val defTime: LocalTime, val defAfter: Int, val key: String)
    val azkarSlots = listOf(
        AzkarSlot("morning", "أذكار الصباح", "الفجر", LocalTime.of(6, 30), 20, "الصباح"),
        AzkarSlot("evening", "أذكار المساء", "العصر", LocalTime.of(16, 30), 20, "المساء"),
        AzkarSlot("sleep", "أذكار النوم", "العشاء", LocalTime.of(22, 30), 90, "النوم"),
    )

    private fun sp() = SafiApp.instance.getSharedPreferences("safi_alerts", Context.MODE_PRIVATE)

    // ---- adhkar
    fun azkarOn(id: String) = sp().getBoolean("az_on_$id", false)
    fun setAzkarOn(id: String, v: Boolean) = sp().edit { putBoolean("az_on_$id", v) }
    /** "fixed" or "after" (minutes after the anchor prayer) */
    fun azkarMode(id: String) = sp().getString("az_mode_$id", "after") ?: "after"
    fun setAzkarMode(id: String, v: String) = sp().edit { putString("az_mode_$id", v) }
    fun azkarTime(s: AzkarSlot): LocalTime = sp().getString("az_time_${s.id}", null)?.let { runCatching { LocalTime.parse(it) }.getOrNull() } ?: s.defTime
    fun setAzkarTime(id: String, t: LocalTime) = sp().edit { putString("az_time_$id", t.toString()) }
    fun azkarAfter(s: AzkarSlot) = sp().getInt("az_after_${s.id}", s.defAfter)
    fun setAzkarAfter(id: String, m: Int) = sp().edit { putInt("az_after_$id", m) }

    var afterPrayerOn: Boolean get() = sp().getBoolean("ap_on", false); set(v) = sp().edit { putBoolean("ap_on", v) }
    var afterPrayerMin: Int get() = sp().getInt("ap_min", 10); set(v) = sp().edit { putInt("ap_min", v) }

    // ---- wird
    var wirdOn: Boolean get() = sp().getBoolean("wird_on", false); set(v) = sp().edit { putBoolean("wird_on", v) }
    var wirdTime: LocalTime
        get() = sp().getString("wird_time", null)?.let { runCatching { LocalTime.parse(it) }.getOrNull() } ?: LocalTime.of(20, 0)
        set(v) = sp().edit { putString("wird_time", v.toString()) }
    var wirdLastOn: Boolean get() = sp().getBoolean("wird_last_on", true); set(v) = sp().edit { putBoolean("wird_last_on", v) }
    var wirdLastTime: LocalTime
        get() = sp().getString("wird_last", null)?.let { runCatching { LocalTime.parse(it) }.getOrNull() } ?: LocalTime.of(22, 0)
        set(v) = sp().edit { putString("wird_last", v.toString()) }

    // ---- pre-adhan
    var preOn: Boolean get() = sp().getBoolean("pre_on", false); set(v) = sp().edit { putBoolean("pre_on", v) }
    var preMin: Int get() = sp().getInt("pre_min", 15); set(v) = sp().edit { putInt("pre_min", v) }
    var prePrayers: Set<String> get() = sp().getStringSet("pre_set", PRAYERS.toSet()) ?: PRAYERS.toSet(); set(v) = sp().edit { putStringSet("pre_set", v) }

    // ---------------------------------------------------------------- scheduling
    private val ids = listOf("morning", "evening", "sleep", "after", "wird", "wird_last", "pre")
    private fun code(id: String) = 8_900_000 + ids.indexOf(id)

    private fun prayerTimes(d: LocalDate): Map<String, LocalDateTime> = Prayer.compute(d).times.toMap()

    /** Next fire time for [id] after now, or null when off. Also returns a label (e.g. which prayer). */
    fun next(id: String, now: LocalDateTime = LocalDateTime.now(zone)): Pair<LocalDateTime, String>? {
        fun daily(t: LocalTime): LocalDateTime { val x = now.toLocalDate().atTime(t); return if (x.isAfter(now)) x else x.plusDays(1) }
        fun relative(prayers: Collection<String>, minutes: Long): Pair<LocalDateTime, String>? =
            (0..2).flatMap { off ->
                val m = prayerTimes(now.toLocalDate().plusDays(off.toLong()))
                prayers.mapNotNull { p -> m[p]?.plusMinutes(minutes)?.let { it to p } }
            }.filter { it.first.isAfter(now) }.minByOrNull { it.first }
        return when (id) {
            "morning", "evening", "sleep" -> {
                val s = azkarSlots.first { it.id == id }
                if (!azkarOn(id)) null
                else if (azkarMode(id) == "fixed") daily(azkarTime(s)) to s.anchor
                else relative(listOf(s.anchor), azkarAfter(s).toLong())
            }
            "after" -> if (afterPrayerOn) relative(PRAYERS, afterPrayerMin.toLong()) else null
            "wird" -> if (wirdOn) daily(wirdTime) to "" else null
            "wird_last" -> if (wirdOn && wirdLastOn) daily(wirdLastTime) to "" else null
            "pre" -> if (preOn && prePrayers.isNotEmpty()) relative(prePrayers, -preMin.toLong()) else null
            else -> null
        }
    }

    private fun pi(ctx: Context, id: String, label: String = ""): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, code(id), Intent(ctx, FaithAlertReceiver::class.java).setAction("fire").putExtra("id", id).putExtra("label", label),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    fun schedule(ctx: Context, id: String) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        am.cancel(pi(ctx, id))
        val (at, label) = runCatching { next(id) }.getOrNull() ?: return
        val ms = at.atZone(zone).toInstant().toEpochMilli()
        val p = pi(ctx, id, label)
        try {
            if (ReminderScheduler.canExact(ctx)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, p)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, p)
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, p)
        }
    }

    fun scheduleAll(ctx: Context) = ids.forEach { runCatching { schedule(ctx, it) } }

    /** Posts the notification for [id]. [test] skips the "already done" checks. */
    fun notify(ctx: Context, id: String, label: String, test: Boolean = false) {
        when (id) {
            "morning", "evening", "sleep" -> {
                val s = azkarSlots.first { it.id == id }
                Notifier.show(ctx, code(id), Notifier.CH_REMIND, "🤲 ${s.label}", "وقت ${s.label}. دقايق بسيطة تحفظك يومك.", route = "azkar:${s.key}")
            }
            "after" -> Notifier.show(ctx, code(id), Notifier.CH_REMIND, "📿 أذكار بعد الصلاة", if (label.isNotBlank()) "بعد صلاة $label: أستغفر الله ٣، وآية الكرسي، والتسبيح ٣٣" else "أستغفر الله ٣، وآية الكرسي، والتسبيح ٣٣", route = "azkar:بعد السلام")
            "wird", "wird_last" -> {
                if (!test && Wird.doneToday) return
                val r = Wird.todayRange()
                val done = PendingIntent.getBroadcast(
                    ctx, code(id) + 100, Intent(ctx, FaithAlertReceiver::class.java).setAction("wird_done").putExtra("id", id),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                Notifier.show(
                    ctx, 8_900_050, Notifier.CH_REMIND,
                    if (id == "wird_last") "📖 لسه ما قريتش وردك النهارده" else "📖 وردك اليومي",
                    "من صفحة ${r.first} إلى ${r.last}",
                    route = "wird",
                    actions = listOf(NotificationCompat.Action(R.drawable.ic_notify, com.mohamed.safi.ui.tr("قريته ✓"), done)),
                )
            }
            "pre" -> Notifier.show(ctx, code(id), Notifier.CH_PRAYER, "🕌 صلاة ${label.ifBlank { "الجاية" }} بعد $preMin دقيقة", "استعد للصلاة واتوضّى", route = "prayer")
        }
    }
}

class FaithAlertReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra("id") ?: return
        when (intent.action) {
            "wird_done" -> {
                if (!Wird.doneToday) Wird.markDone()
                Notifier.cancel(context, 8_900_050)
            }
            else -> {
                runCatching { FaithAlerts.notify(context, id, intent.getStringExtra("label") ?: "") }
                FaithAlerts.schedule(context, id)
            }
        }
    }
}
