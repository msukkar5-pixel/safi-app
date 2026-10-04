package com.mohamed.safi.data

import com.mohamed.safi.SafiApp
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

val zone: ZoneId get() = ZoneId.systemDefault()

fun fmt(d: Double): String =
    if (d == Math.floor(d) && Math.abs(d) < 1e12) String.format(Locale.US, "%,.0f", d)
    else String.format(Locale.US, "%,.2f", d)

fun money(d: Double, currency: String = "AED"): String = "${fmt(d)} ${curLabel(currency)}"

fun curLabel(c: String): String = when (c.uppercase()) {
    "AED" -> "د.إ"
    "EGP" -> "ج.م"
    "USD" -> "$"
    "SAR" -> "ر.س"
    "EUR" -> "€"
    else -> c
}

val CURRENCIES = listOf("AED", "EGP", "USD", "SAR", "EUR")

private val arMonths = listOf(
    "يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو",
    "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر",
)
private val arDays = mapOf(
    1 to "الاثنين", 2 to "الثلاثاء", 3 to "الأربعاء", 4 to "الخميس",
    5 to "الجمعة", 6 to "السبت", 7 to "الأحد",
)

fun monthName(ym: YearMonth) = "${arMonths[ym.monthValue - 1]} ${ym.year}"

fun monthRange(ym: YearMonth): Pair<Long, Long> {
    val start = ym.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val end = ym.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
    return start to end
}

fun dayRange(d: LocalDate): Pair<Long, Long> {
    val start = d.atStartOfDay(zone).toInstant().toEpochMilli()
    return start to (start + 86_400_000L - 1)
}

fun Long.toLdt(): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(this), zone)
fun Long.toLocalDate(): LocalDate = toLdt().toLocalDate()
fun LocalDateTime.millis(): Long = atZone(zone).toInstant().toEpochMilli()
fun LocalDate.millisAt(hour: Int = 9, minute: Int = 0): Long = atTime(hour, minute).millis()

fun timeStr(t: Long): String = t.toLdt().format(DateTimeFormatter.ofPattern("hh:mm a", Locale.US))
    .replace("AM", "ص").replace("PM", "م")

fun dateStr(t: Long): String {
    val d = t.toLocalDate()
    return "${arDays[d.dayOfWeek.value]} ${d.dayOfMonth} ${arMonths[d.monthValue - 1]}"
}

fun shortDate(t: Long): String {
    val d = t.toLocalDate()
    val today = LocalDate.now(zone)
    return when (d) {
        today -> "النهارده"
        today.minusDays(1) -> "امبارح"
        today.plusDays(1) -> "بكرة"
        else -> "${d.dayOfMonth} ${arMonths[d.monthValue - 1]}" + if (d.year != today.year) " ${d.year}" else ""
    }
}

fun dateTimeStr(t: Long) = "${shortDate(t)} ${timeStr(t)}"

fun isoLocal(t: Long): String = t.toLdt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", java.util.Locale.US))

/** "بعد 3 أيام" / "متأخرة يومين" */
fun dueText(due: Long): String {
    val days = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(zone), due.toLocalDate())
    return when {
        days < -1 -> "متأخرة ${-days} يوم"
        days == -1L -> "متأخرة من امبارح"
        days == 0L -> "النهارده"
        days == 1L -> "بكرة"
        else -> "بعد $days يوم"
    }
}

fun daysUntil(t: Long): Long =
    java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(zone), t.toLocalDate())

/** Parse "2026-10-05T09:00", "2026-10-05 09:00" or "2026-10-05". */
fun parseIso(s: String?): Long? {
    if (s.isNullOrBlank()) return null
    val t = s.trim().replace(" ", "T")
    return runCatching { LocalDateTime.parse(t.take(16)).millis() }.getOrNull()
        ?: runCatching { LocalDate.parse(t.take(10)).millisAt(9) }.getOrNull()
}

object Fx {
    /** Converts any supported currency to AED using the latest saved rates. */
    fun toAed(amount: Double, currency: String): Double {
        val c = currency.uppercase()
        if (c == "AED") return amount
        val rate = rateFor(c)
        return if (rate > 0) amount / rate else amount
    }

    /** units of [c] per 1 AED */
    fun rateFor(c: String): Double {
        val prefs = SafiApp.prefs
        if (c == "EGP") return prefs.egpPerAed
        val json = prefs.ratesJson
        if (json.isNotBlank()) {
            runCatching { org.json.JSONObject(json).optDouble(c, 0.0) }.getOrNull()?.let { if (it > 0) return it }
        }
        return when (c) {
            "USD" -> 0.2723
            "SAR" -> 1.0211
            "EUR" -> 0.25
            else -> 1.0
        }
    }

    /** Refresh from open.er-api.com (free, no key). */
    fun refresh(): Boolean {
        val prefs = SafiApp.prefs
        return runCatching {
            val client = okhttp3.OkHttpClient()
            val req = okhttp3.Request.Builder().url("https://open.er-api.com/v6/latest/AED").build()
            client.newCall(req).execute().use { r ->
                val body = r.body?.string() ?: return false
                val rates = org.json.JSONObject(body).getJSONObject("rates")
                prefs.ratesJson = rates.toString()
                if (prefs.rateAuto) {
                    val egp = rates.optDouble("EGP", 0.0)
                    if (egp > 0) prefs.egpPerAed = Math.round(egp * 100.0) / 100.0
                }
                prefs.rateUpdated = System.currentTimeMillis()
                true
            }
        }.getOrDefault(false)
    }
}
