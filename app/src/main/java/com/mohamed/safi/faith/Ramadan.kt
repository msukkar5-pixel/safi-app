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

    // ------------------------------------------------------------------ khatma plans
    /** Finish the Quran in [days] days: sets the daily wird size. */
    fun setKhatmaPlan(days: Int) { Wird.pagesPerDay = (Wird.TOTAL_PAGES + days - 1) / days }
    fun khatmaPlanDays(): Int = (Wird.TOTAL_PAGES + Wird.pagesPerDay - 1) / Wird.pagesPerDay

    // ------------------------------------------------------------------ private notebook
    private val prompts = listOf(
        "إيه أكتر لحظة حسيت فيها بالسكينة النهارده؟", "دعوة نفسك ربنا يستجيبها", "عادة نفسك تسيبها في رمضان ده",
        "حاجة اتعلمتها النهارده", "حد عايز تشكره، وليه؟", "إيه اللي ضايقك النهارده؟ وتقدر تتعامل معاه إزاي؟",
        "٣ نعم إنت ممتن ليها النهارده", "آية وقفت عندها في وردك", "حاجة عملتها النهارده وفخور بيها",
        "إزاي تخلّي بكرة أحسن من النهارده؟", "حد محتاج تصالحه أو تطمن عليه", "إيه اللي بيشغلك عن العبادة؟ وتقلله إزاي؟",
        "موقف حلو حصل مع العيلة", "حاجة عايز تحافظ عليها بعد رمضان", "رسالة لنفسك آخر رمضان",
    )
    fun prompt(d: LocalDate = LocalDate.now(zone)): String = prompts[((if (isRamadan(d)) day(d) - 1 else d.toEpochDay().toInt()).mod(prompts.size))]
    fun note(d: LocalDate = LocalDate.now(zone)): String = sp().getString("note_$d", "").orEmpty()
    fun setNote(d: LocalDate, text: String) = sp().edit { putString("note_$d", text.take(2000)) }
    /** Days that have a note, newest first. */
    fun noteDays(): List<LocalDate> = sp().all.keys.filter { it.startsWith("note_") && !sp().getString(it, "").isNullOrBlank() }
        .mapNotNull { runCatching { LocalDate.parse(it.removePrefix("note_")) }.getOrNull() }.sortedDescending()

    // ------------------------------------------------------------------ charity log
    data class Sadaqa(val at: Long, val amount: Double, val note: String)
    fun sadaqat(): List<Sadaqa> = runCatching {
        val a = org.json.JSONArray(sp().getString("sadaqa", "[]"))
        (0 until a.length()).map { a.getJSONObject(it).let { o -> Sadaqa(o.getLong("at"), o.optDouble("v", 0.0), o.optString("n")) } }
    }.getOrDefault(emptyList()).sortedByDescending { it.at }
    private fun saveSadaqat(l: List<Sadaqa>) = sp().edit {
        putString("sadaqa", org.json.JSONArray(l.map { org.json.JSONObject().put("at", it.at).put("v", it.amount).put("n", it.note) }).toString())
    }
    fun addSadaqa(amount: Double, note: String) = saveSadaqat(sadaqat() + Sadaqa(System.currentTimeMillis(), amount, note.take(80)))
    fun removeSadaqa(at: Long) = saveSadaqat(sadaqat().filter { it.at != at })
    /** Total since the start of this (or the last) Ramadan. */
    fun sadaqaThisRamadan(today: LocalDate = LocalDate.now(zone)): Double {
        var start = today
        if (!isRamadan(start)) { var n = 0; while (!isRamadan(start) && n++ < 400) start = start.minusDays(1) }
        if (!isRamadan(start)) return 0.0
        while (isRamadan(start.minusDays(1))) start = start.minusDays(1)
        val from = start.atStartOfDay(zone).toInstant().toEpochMilli()
        return sadaqat().filter { it.at >= from }.sumOf { it.amount }
    }

    // ------------------------------------------------------------------ good-deed challenge of the day
    private val deeds = listOf(
        "كلّم قريب ماكلمتوش من زمان", "اتصدّق ولو بمبلغ صغير", "ساعد في تجهيز الفطار", "فطّر صايم أو شارك في شنطة رمضان",
        "قول كلمة حلوة لكل واحد في البيت", "سامح حد زعّلك", "اقرا تفسير صفحة من وردك", "ادعي لـ ٣ أشخاص بأساميهم",
        "اتبرّع بهدوم مش محتاجها", "ابعت رسالة شكر لحد أثّر فيك", "اسقِ زرعة أو حط ميه للطيور", "ساعد جار كبير في السن",
        "اتعلم ذكر جديد من قسم الأذكار", "قلّل الموبايل ساعة وقضّيها مع العيلة", "روح الصلاة بدري",
        "زور مريض أو اتصل تطمن عليه", "شارك أكلك مع زميل", "ماتتكلمش عن حد في غيابه طول اليوم", "اكتب ٥ نعم إنت ممتن ليها",
        "علّم طفل حاجة مفيدة", "ساعد حد في شغله من غير ما يطلب", "شيل حاجة مؤذية من الطريق", "ادعي لوالديك بعد كل صلاة",
        "اتصل بصاحب قديم واطمن عليه", "احكي لأولادك قصة من السيرة", "اتصدّق صدقة سر محدش يعرف بيها",
        "اسأل عن حال البواب أو عامل النظافة", "جهّز زكاة الفطر بدري", "اكتب رسالة شكر لأهلك", "خطّط لعادة حلوة تكمّل بيها بعد رمضان",
    )
    fun deed(d: LocalDate = LocalDate.now(zone)): String = deeds[((if (isRamadan(d)) day(d) - 1 else d.toEpochDay().toInt()).mod(deeds.size))]
    fun deedDone(d: LocalDate = LocalDate.now(zone)) = sp().getBoolean("deed_$d", false)
    fun setDeedDone(d: LocalDate, v: Boolean) = sp().edit { putBoolean("deed_$d", v) }
    /** Deeds done during this Ramadan (or ever, outside it). */
    fun deedsCount(): Int = sp().all.count { (k, v) -> k.startsWith("deed_") && v == true }
}
