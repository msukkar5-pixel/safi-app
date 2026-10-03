package com.mohamed.safi.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.edit
import com.mohamed.safi.SafiApp
import com.mohamed.safi.notify.Notifier
import com.mohamed.safi.notify.ReminderScheduler
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Car-pool rotation: N people take turns driving on working days.
 * Holidays (skips) don't consume a turn; overrides swap the driver for one day only.
 */
data class CarpoolConfig(
    val members: List<String> = emptyList(),
    val me: Int = 0,
    val days: Set<Int> = setOf(1, 2, 3, 4, 5),        // ISO: 1 = Monday … 7 = Sunday
    val anchorDate: LocalDate = LocalDate.now(zone),
    val anchorIndex: Int = 0,                           // who drives on anchorDate (or the first working day after it)
    val hour: Int = 20,
    val minute: Int = 0,
    val alwaysNotify: Boolean = false,                  // also tell me who drives when it's not me
    val overrides: Map<LocalDate, String> = emptyMap(),
    val skips: Set<LocalDate> = emptySet(),
    val enabled: Boolean = false,
) {
    val myName: String get() = members.getOrNull(me) ?: ""

    fun isWorkday(d: LocalDate) = d.dayOfWeek.value in days && d !in skips

    /** Who drives on [d], or null when nobody drives (weekend / holiday / not configured). */
    fun driverFor(d: LocalDate): String? {
        if (members.isEmpty() || !isWorkday(d)) return null
        overrides[d]?.let { return it }
        // The first working day on/after anchorDate belongs to anchorIndex; every working day after it moves one turn.
        var n = 0L
        if (!d.isBefore(anchorDate)) {
            var x = anchorDate
            while (x.isBefore(d)) {
                if (isWorkday(x)) n++
                x = x.plusDays(1)
            }
        } else {
            var x = d
            while (x.isBefore(anchorDate)) {
                if (isWorkday(x)) n--
                x = x.plusDays(1)
            }
        }
        val size = members.size
        val idx = (((anchorIndex + n) % size + size) % size).toInt()
        return members[idx]
    }

    fun nextWorkday(from: LocalDate): LocalDate? {
        var d = from
        repeat(30) {
            if (isWorkday(d)) return d
            d = d.plusDays(1)
        }
        return null
    }

    /** My next driving days in the coming [daysAhead] days. */
    fun myDays(from: LocalDate, daysAhead: Int = 30): List<LocalDate> =
        (0 until daysAhead).map { from.plusDays(it.toLong()) }.filter { driverFor(it) == myName && myName.isNotEmpty() }

    fun toJson(): String = JSONObject()
        .put("members", JSONArray(members))
        .put("me", me)
        .put("days", JSONArray(days.toList()))
        .put("anchorDate", anchorDate.toString())
        .put("anchorIndex", anchorIndex)
        .put("hour", hour).put("minute", minute)
        .put("alwaysNotify", alwaysNotify)
        .put("overrides", JSONObject().apply { overrides.forEach { (k, v) -> put(k.toString(), v) } })
        .put("skips", JSONArray(skips.map { it.toString() }))
        .put("enabled", enabled)
        .toString()

    companion object {
        fun fromJson(s: String): CarpoolConfig = runCatching {
            val j = JSONObject(s)
            val m = j.optJSONArray("members") ?: JSONArray()
            val d = j.optJSONArray("days") ?: JSONArray()
            val o = j.optJSONObject("overrides") ?: JSONObject()
            val sk = j.optJSONArray("skips") ?: JSONArray()
            CarpoolConfig(
                members = (0 until m.length()).map { m.getString(it) },
                me = j.optInt("me", 0),
                days = (0 until d.length()).map { d.getInt(it) }.toSet(),
                anchorDate = LocalDate.parse(j.optString("anchorDate", LocalDate.now(zone).toString())),
                anchorIndex = j.optInt("anchorIndex", 0),
                hour = j.optInt("hour", 20),
                minute = j.optInt("minute", 0),
                alwaysNotify = j.optBoolean("alwaysNotify", false),
                overrides = o.keys().asSequence().associate { LocalDate.parse(it) to o.getString(it) }
                    .filterKeys { !it.isBefore(LocalDate.now(zone).minusDays(60)) },
                skips = (0 until sk.length()).map { LocalDate.parse(sk.getString(it)) }
                    .filter { !it.isBefore(LocalDate.now(zone).minusDays(60)) }.toSet(),
                enabled = j.optBoolean("enabled", false),
            )
        }.getOrDefault(CarpoolConfig())
    }
}

object Carpool {
    private fun sp(ctx: Context) = ctx.getSharedPreferences("safi_carpool", Context.MODE_PRIVATE)

    fun load(ctx: Context = SafiApp.instance): CarpoolConfig =
        sp(ctx).getString("cfg", null)?.let { CarpoolConfig.fromJson(it) } ?: CarpoolConfig()

    fun save(ctx: Context, c: CarpoolConfig) {
        sp(ctx).edit { putString("cfg", c.toJson()) }
        schedule(ctx, c)
    }

    private fun pending(ctx: Context): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, 7_700_001, Intent(ctx, CarpoolReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    fun schedule(ctx: Context, c: CarpoolConfig = load(ctx)) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        am.cancel(pending(ctx))
        if (!c.enabled || c.members.isEmpty()) return
        val now = java.time.LocalDateTime.now(zone)
        var at = now.toLocalDate().atTime(c.hour, c.minute)
        if (!at.isAfter(now)) at = at.plusDays(1)
        val t = at.millis()
        try {
            if (ReminderScheduler.canExact(ctx)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pending(ctx))
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pending(ctx))
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pending(ctx))
        }
    }

    fun dayLabel(d: LocalDate): String {
        val today = LocalDate.now(zone)
        return when (ChronoUnit.DAYS.between(today, d)) {
            0L -> "النهارده"
            1L -> "بكرة"
            else -> dateStr(d.millisAt(9))
        }
    }

    fun notifyTomorrow(ctx: Context) {
        val c = load(ctx)
        if (!c.enabled) return
        val tomorrow = LocalDate.now(zone).plusDays(1)
        val driver = c.driverFor(tomorrow) ?: return
        val mine = driver == c.myName
        if (mine) {
            val after = c.myDays(tomorrow.plusDays(1), 21).firstOrNull()
            Notifier.show(
                ctx, 7701, Notifier.CH_ALARM,
                "🚗 بكرة انت اللي هتسوق",
                "جهّز العربية وبنزينها." + (after?.let { "\nدورك اللي بعده: ${dateStr(it.millisAt(9))}" } ?: ""),
                route = "carpool",
            )
        } else if (c.alwaysNotify) {
            Notifier.show(ctx, 7701, Notifier.CH_REMIND, "🚗 بكرة $driver اللي هيسوق", "مش دورك بكرة.", route = "carpool")
        }
    }
}

class CarpoolReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        runCatching { Carpool.notifyTomorrow(context) }
        Carpool.schedule(context)
    }
}

fun isoDow(d: DayOfWeek) = d.value
