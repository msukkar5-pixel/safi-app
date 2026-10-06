package com.mohamed.safi.faith

import android.content.Context
import androidx.core.content.edit
import com.mohamed.safi.SafiApp
import com.mohamed.safi.data.zone
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoField

/**
 * Ramadan layer: shows by itself during Ramadan (Hijri month 9 on the phone's Umm al-Qura calendar, which can differ
 * by a day from the local moon sighting), with a countdown the rest of the year.
 */
object Ramadan {
    fun hijri(d: LocalDate = LocalDate.now(zone)): HijrahDate? = runCatching { HijrahDate.from(d) }.getOrNull()
    fun isRamadan(d: LocalDate = LocalDate.now(zone)) = hijri(d)?.get(ChronoField.MONTH_OF_YEAR) == 9
    fun day(d: LocalDate = LocalDate.now(zone)): Int = hijri(d)?.get(ChronoField.DAY_OF_MONTH) ?: 0
    fun lastTen(d: LocalDate = LocalDate.now(zone)) = isRamadan(d) && day(d) >= 21

    /** Days until the 1st of Ramadan (0 during Ramadan). */
    fun daysUntil(d: LocalDate = LocalDate.now(zone)): Int {
        if (isRamadan(d)) return 0
        var x = d
        repeat(400) { x = x.plusDays(1); if (isRamadan(x)) return (x.toEpochDay() - d.toEpochDay()).toInt() }
        return -1
    }

    /** Daily plans by available time. */
    data class Item(val id: String, val title: String, val icon: String)
    val plans = linkedMapOf(
        5 to listOf(Item("zikr", "ذكر قصير من أذكارك", "📿"), Item("ayah", "آية أو صفحة قرآن", "📖"), Item("good", "عمل خير صغير", "💝")),
        15 to listOf(Item("azkar", "أذكار الصباح أو المساء", "☀️"), Item("pages", "صفحتين أو أكتر من القرآن", "📖"), Item("dua", "دعاء قبل الفطار", "🤲"), Item("habit", "مهمة سلوكية (كلمة طيبة، ترك عادة)", "🌱")),
        30 to listOf(Item("wird", "ورد القرآن (جزء أو حسب خطتك)", "📖"), Item("azkar2", "أذكار الصباح والمساء", "☀️"), Item("lesson", "درس أو قصة من السيرة", "🎧"), Item("sadaqa", "صدقة أو عمل للعيلة", "💝"), Item("review", "مراجعة يومك", "📝")),
    )
    /** Fixed checklist for every Ramadan day. */
    val daily = listOf(
        Item("fard", "الصلوات الخمس", "🕌"), Item("taraweeh", "التراويح أو القيام", "🌙"), Item("witr", "الوتر", "✨"),
        Item("quran", "الورد القرآني", "📖"), Item("sadaqa", "صدقة", "💝"), Item("rahim", "صلة رحم", "📞"),
        Item("iftar_saim", "إفطار صائم أو المشاركة فيه", "🍲"), Item("no_waste", "من غير إسراف في الأكل", "🌿"),
    )

    private fun sp() = SafiApp.instance.getSharedPreferences("safi_ramadan", Context.MODE_PRIVATE)
    var planMinutes: Int get() = sp().getInt("plan", 15); set(v) = sp().edit { putInt("plan", v) }
    fun done(d: LocalDate = LocalDate.now(zone)): Set<String> = sp().getStringSet("done_$d", emptySet()) ?: emptySet()
    fun toggle(id: String, d: LocalDate = LocalDate.now(zone)) {
        val s = done(d); sp().edit { putStringSet("done_$d", if (id in s) s - id else s + id) }
    }
    /** Last-ten nights the user marked as prayed (qiyam). */
    fun nightDone(day: Int) = sp().getBoolean("night_${hijri()?.get(ChronoField.YEAR_OF_ERA)}_$day", false)
    fun setNight(day: Int, v: Boolean) = sp().edit { putBoolean("night_${hijri()?.get(ChronoField.YEAR_OF_ERA)}_$day", v) }

    /** A gentle, skippable suggestion for this moment of a Ramadan day. */
    fun whatNow(now: LocalDateTime = LocalDateTime.now(zone)): Pair<String, String> {
        val t = Prayer.today().times.toMap()
        val fajr = t["الفجر"] ?: now; val dhuhr = t["الظهر"] ?: now; val asr = t["العصر"] ?: now
        val maghrib = t["المغرب"] ?: now; val isha = t["العشاء"] ?: now
        return when {
            now.isBefore(fajr.minusMinutes(90)) && now.hour >= 2 -> "🌙 وقت القيام والسحور قرب" to "صلّ ركعتين، واستغفر، وجهّز سحورك"
            now.isBefore(fajr) -> "🍽️ وقت السحور" to "تسحّر ولو بتمرة وميه، وانوِ الصيام"
            now.isBefore(fajr.plusHours(2)) -> "☀️ بعد الفجر" to "أذكار الصباح، وصفحات من وردك"
            now.isBefore(dhuhr) -> "📖 قبل الظهر" to "مهمة صغيرة: صفحة قرآن أو ذكر وانت شغال"
            now.isBefore(asr) -> "🤍 بعد الظهر" to "عمل خير صغير: كلمة طيبة أو مساعدة"
            now.isBefore(maghrib.minusMinutes(40)) -> "🌿 العصر" to "أذكار المساء وكمّل وردك"
            now.isBefore(maghrib) -> "🤲 قبل المغرب" to "وقت دعاء، وساعد في تجهيز الفطار"
            now.isBefore(isha) -> "🍲 بعد الفطار" to "الحمد لله، صلّ المغرب، وكُل من غير إسراف"
            now.hour < 23 -> "🌙 بعد العشاء" to "التراويح أو القيام، والوتر"
            else -> "🛏️ قبل النوم" to "أذكار النوم، وراجع يومك، ونيّة صيام بكرة"
        }
    }
}
