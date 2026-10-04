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
import java.time.temporal.TemporalAdjusters

/**
 * Car-pool schedule. Two modes:
 *  - manual: an explicit table (date → driver) that the group agreed on; can repeat as a cycle.
 *  - auto:   simple rotation on working days, starting from a given date and person.
 * Explicit table entries always win, then one-day swaps (overrides), holidays (skips) mean nobody drives.
 */
data class CarpoolConfig(
    val members: List<String> = emptyList(),
    val me: Int = 0,
    val mode: String = "auto",                          // manual | auto
    val manual: Map<LocalDate, String> = emptyMap(),
    val repeatManual: Boolean = true,
    val days: Set<Int> = setOf(1, 2, 3, 4, 5),          // auto mode working days, ISO 1 = Monday
    val anchorDate: LocalDate = LocalDate.now(zone),
    val anchorIndex: Int = 0,
    val hour: Int = 20,
    val minute: Int = 0,
    val alwaysNotify: Boolean = false,
    val overrides: Map<LocalDate, String> = emptyMap(),
    val skips: Set<LocalDate> = emptySet(),
    val enabled: Boolean = false,
) {
    val myName: String get() = members.getOrNull(me) ?: ""

    private val manualStart: LocalDate? get() = manual.keys.minOrNull()?.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    private val manualEnd: LocalDate? get() = manual.keys.maxOrNull()?.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
    val manualLast: LocalDate? get() = manual.keys.maxOrNull()

    private fun fromTable(d: LocalDate): String? {
        manual[d]?.let { return it }
        if (!repeatManual) return null
        val s = manualStart ?: return null
        val e = manualEnd ?: return null
        if (!d.isAfter(e)) return null
        val cycle = ChronoUnit.DAYS.between(s, e) + 1
        val back = ((ChronoUnit.DAYS.between(s, d)) / cycle) * cycle
        return manual[d.minusDays(back)]
    }

    private fun isAutoWorkday(d: LocalDate) = d.dayOfWeek.value in days && d !in skips

    private fun rotation(d: LocalDate): String? {
        if (members.isEmpty() || !isAutoWorkday(d)) return null
        var n = 0L
        if (!d.isBefore(anchorDate)) {
            var x = anchorDate
            while (x.isBefore(d)) { if (isAutoWorkday(x)) n++; x = x.plusDays(1) }
        } else {
            var x = d
            while (x.isBefore(anchorDate)) { if (isAutoWorkday(x)) n--; x = x.plusDays(1) }
        }
        val size = members.size
        return members[(((anchorIndex + n) % size + size) % size).toInt()]
    }

    /** Who drives on [d], or null when nobody drives. */
    fun driverFor(d: LocalDate): String? {
        if (d in skips) return null
        overrides[d]?.let { return it }
        if (mode == "manual") return fromTable(d)
        // auto mode: table entries still count for the dates they cover (before the switch)
        manual[d]?.let { if (!d.isBefore(anchorDate)) null else return it }
        return rotation(d)
    }

    fun nextDriveDay(from: LocalDate): LocalDate? {
        var d = from
        repeat(30) {
            if (driverFor(d) != null) return d
            d = d.plusDays(1)
        }
        return null
    }

    fun myDays(from: LocalDate, daysAhead: Int = 30): List<LocalDate> =
        (0 until daysAhead).map { from.plusDays(it.toLong()) }.filter { myName.isNotEmpty() && driverFor(it) == myName }

    fun toJson(): String = JSONObject()
        .put("members", JSONArray(members))
        .put("me", me)
        .put("mode", mode)
        .put("manual", JSONObject().apply { manual.forEach { (k, v) -> put(k.toString(), v) } })
        .put("repeatManual", repeatManual)
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
        private fun dateMap(o: JSONObject?): Map<LocalDate, String> =
            if (o == null) emptyMap() else o.keys().asSequence().mapNotNull { k ->
                runCatching { LocalDate.parse(k) to o.getString(k) }.getOrNull()
            }.toMap()

        fun fromJson(s: String): CarpoolConfig = runCatching {
            val j = JSONObject(s)
            val m = j.optJSONArray("members") ?: JSONArray()
            val d = j.optJSONArray("days") ?: JSONArray()
            val sk = j.optJSONArray("skips") ?: JSONArray()
            val old = LocalDate.now(zone).minusDays(60)
            CarpoolConfig(
                members = (0 until m.length()).map { m.getString(it) },
                me = j.optInt("me", 0),
                mode = j.optString("mode", "auto"),
                manual = dateMap(j.optJSONObject("manual")),
                repeatManual = j.optBoolean("repeatManual", true),
                days = (0 until d.length()).map { d.getInt(it) }.toSet(),
                anchorDate = LocalDate.parse(j.optString("anchorDate", LocalDate.now(zone).toString())),
                anchorIndex = j.optInt("anchorIndex", 0),
                hour = j.optInt("hour", 20),
                minute = j.optInt("minute", 0),
                alwaysNotify = j.optBoolean("alwaysNotify", false),
                overrides = dateMap(j.optJSONObject("overrides")).filterKeys { !it.isBefore(old) },
                skips = (0 until sk.length()).mapNotNull { runCatching { LocalDate.parse(sk.getString(it)) }.getOrNull() }
                    .filter { !it.isBefore(old) }.toSet(),
                enabled = j.optBoolean("enabled", false),
            )
        }.getOrDefault(CarpoolConfig())

        /** October 2026 table the group agreed on (Friday off). */
        fun seed(): CarpoolConfig {
            val y = 2026
            val t = listOf(
                5 to "صبحي", 6 to "اسلام", 7 to "احمد", 8 to "سكر",
                12 to "احمد", 13 to "سكر", 14 to "صبحي", 15 to "اسلام",
                19 to "سكر", 20 to "احمد", 21 to "اسلام", 22 to "صبحي",
                26 to "اسلام", 27 to "صبحي", 28 to "سكر", 29 to "احمد",
            ).associate { (d, n) -> LocalDate.of(y, 10, d) to n }
            return CarpoolConfig(
                members = listOf("صبحي", "اسلام", "احمد", "سكر"), me = 3, mode = "manual", manual = t,
                repeatManual = true, days = setOf(1, 2, 3, 4, 5), anchorDate = LocalDate.of(y, 11, 2), anchorIndex = 0,
                hour = 20, minute = 0, enabled = true,
            )
        }

        private val arDays = mapOf(
            "الاثنين" to 1, "الإثنين" to 1, "الاتنين" to 1, "اتنين" to 1, "الثلاثاء" to 2, "التلات" to 2, "الثلاث" to 2,
            "الأربعاء" to 3, "الاربعاء" to 3, "الأربع" to 3, "الاربع" to 3, "الخميس" to 4, "الجمعة" to 5, "الجمعه" to 5,
            "السبت" to 6, "الأحد" to 7, "الاحد" to 7,
        )

        /**
         * Parses a pasted schedule like:
         *   "الاتنين 5/10: صبحي" or "الخميس 8 سكر" (month taken from the previous line).
         * Returns date → name.
         */
        fun parseTable(text: String, members: List<String>): Map<LocalDate, String> {
            val out = linkedMapOf<LocalDate, String>()
            val today = LocalDate.now(zone)
            var lastMonth: Int? = null
            var lastYear = today.year
            val re = Regex("(\\d{1,2})(?:\\s*[/\\-.]\\s*(\\d{1,2}))?(?:\\s*[/\\-.]\\s*(\\d{2,4}))?\\s*[:：\\-–]?\\s*([\\p{L}]+(?:\\s+[\\p{L}]+)?)")
            for (raw in text.lines()) {
                val line = toWestern(raw).replace("•", " ").replace("\t", " ").trim()
                if (line.isEmpty()) continue
                val m = re.find(line) ?: continue
                val day = m.groupValues[1].toIntOrNull() ?: continue
                val month = m.groupValues[2].toIntOrNull() ?: lastMonth ?: continue
                var year = m.groupValues[3].toIntOrNull()?.let { if (it < 100) 2000 + it else it } ?: lastYear
                if (m.groupValues[3].isEmpty() && lastMonth == null && month < today.monthValue - 2) year = today.year + 1
                var name = m.groupValues[4].trim()
                // keep only the member's name if the line has extra words
                members.firstOrNull { name.startsWith(it) || it.startsWith(name.split(" ").first()) }?.let { name = it }
                    ?: run { name = name.split(" ").first() }
                if (arDays.containsKey(name)) continue
                val date = runCatching { LocalDate.of(year, month, day) }.getOrNull() ?: continue
                out[date] = name
                lastMonth = month
                lastYear = year
            }
            return out
        }

        private fun toWestern(s: String) = s.map { c ->
            when (c) {
                in '٠'..'٩' -> '0' + (c - '٠')
                in '۰'..'۹' -> '0' + (c - '۰')
                else -> c
            }
        }.joinToString("")
    }
}

object Carpool {
    private fun sp(ctx: Context) = ctx.getSharedPreferences("safi_carpool", Context.MODE_PRIVATE)

    fun load(ctx: Context = SafiApp.instance): CarpoolConfig {
        val s = sp(ctx).getString("cfg", null)
        val c = s?.let { CarpoolConfig.fromJson(it) }
        if (c == null || (c.members.isEmpty() && c.manual.isEmpty() && !sp(ctx).getBoolean("seeded", false))) {
            val seeded = CarpoolConfig.seed()
            sp(ctx).edit { putString("cfg", seeded.toJson()); putBoolean("seeded", true) }
            return seeded
        }
        return c
    }

    fun save(ctx: Context, c: CarpoolConfig) {
        sp(ctx).edit { putString("cfg", c.toJson()); putBoolean("seeded", true) }
        schedule(ctx, c)
        runCatching { com.mohamed.safi.widget.SafiWidget.updateAll(ctx) }
    }

    private fun pending(ctx: Context): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, 7_700_001, Intent(ctx, CarpoolReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    fun schedule(ctx: Context, c: CarpoolConfig = load(ctx)) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        am.cancel(pending(ctx))
        if (!c.enabled || (c.members.isEmpty() && c.manual.isEmpty())) return
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
        if (driver == c.myName) {
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
        runCatching { com.mohamed.safi.widget.SafiWidget.updateAll(context) }
    }
}
