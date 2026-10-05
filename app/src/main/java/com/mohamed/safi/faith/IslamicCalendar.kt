package com.mohamed.safi.faith

import com.mohamed.safi.data.zone
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoField

/** Umm al-Qura calendar helpers: occasions and recommended fasting days. */
object IslamicCalendar {
    val months = listOf("محرم", "صفر", "ربيع الأول", "ربيع الآخر", "جمادى الأولى", "جمادى الآخرة", "رجب", "شعبان", "رمضان", "شوال", "ذو القعدة", "ذو الحجة")

    data class H(val y: Int, val m: Int, val d: Int)

    fun hijri(d: LocalDate): H? = runCatching {
        val h = HijrahDate.from(d)
        H(h.get(ChronoField.YEAR), h.get(ChronoField.MONTH_OF_YEAR), h.get(ChronoField.DAY_OF_MONTH))
    }.getOrNull()

    fun gregorian(y: Int, m: Int, d: Int): LocalDate? = runCatching { LocalDate.from(HijrahDate.of(y, m, d)) }.getOrNull()

    fun label(d: LocalDate): String = hijri(d)?.let { "${it.d} ${months[it.m - 1]} ${it.y} هـ" } ?: ""

    private val occasions = listOf(
        Triple("رأس السنة الهجرية", 1, 1),
        Triple("تاسوعاء (صيامه سنة)", 1, 9),
        Triple("عاشوراء (صيامه يكفّر سنة)", 1, 10),
        Triple("أول رمضان", 9, 1),
        Triple("العشر الأواخر من رمضان", 9, 21),
        Triple("عيد الفطر", 10, 1),
        Triple("أول ذي الحجة (العشر المباركة)", 12, 1),
        Triple("يوم عرفة (صيامه يكفّر سنتين)", 12, 9),
        Triple("عيد الأضحى", 12, 10),
    )

    /** The next [n] occasions from today (today included). */
    fun nextOccasions(n: Int, from: LocalDate = LocalDate.now(zone)): List<Pair<String, LocalDate>> {
        val h = hijri(from) ?: return emptyList()
        return (h.y..h.y + 1).flatMap { y -> occasions.mapNotNull { (name, m, d) -> gregorian(y, m, d)?.let { name to it } } }
            .filter { !it.second.isBefore(from) }.sortedBy { it.second }.take(n)
    }

    /** Recommended (or obligatory) fasting for [d], or null. */
    fun fastingToday(d: LocalDate = LocalDate.now(zone)): String? {
        val h = hijri(d) ?: return null
        if (h.m == 9) return "صيام رمضان"
        if ((h.m == 10 && h.d == 1) || (h.m == 12 && h.d in 10..13)) return null // Eid and the days of tashreeq: no fasting
        return when {
            h.m == 12 && h.d == 9 -> "صيام يوم عرفة"
            h.m == 1 && h.d == 10 -> "صيام عاشوراء"
            h.m == 1 && h.d == 9 -> "صيام تاسوعاء"
            h.m == 10 && h.d >= 2 -> "ست من شوال"
            h.d in 13..15 -> "الأيام البيض (${h.d})"
            d.dayOfWeek == DayOfWeek.MONDAY -> "صيام الاثنين سنة"
            d.dayOfWeek == DayOfWeek.THURSDAY -> "صيام الخميس سنة"
            h.m == 12 && h.d in 1..8 -> "العشر من ذي الحجة"
            else -> null
        }
    }
}
