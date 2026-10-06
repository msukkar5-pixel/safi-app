package com.mohamed.safi.faith

import android.content.Context
import androidx.compose.runtime.mutableIntStateOf
import androidx.core.content.edit
import com.mohamed.safi.SafiApp
import com.mohamed.safi.data.zone
import org.json.JSONObject
import java.time.LocalDate
import java.util.Locale

/**
 * Quran memorization: listen and repeat verse by verse (everyayah.com recordings), test yourself with hidden words,
 * and review what you memorized on a spaced schedule (1, 3, 7, 14, 30 days).
 */
object Hifz {
    /** everyayah.com folders: verse-by-verse recordings. The teaching recitation (Husary mu'allim) suits memorizing. */
    val reciters = listOf(
        "Husary_Muallim_128kbps" to "الحصري (المعلّم)", "Husary_128kbps" to "الحصري", "Minshawy_Murattal_128kbps" to "المنشاوي",
        "Alafasy_128kbps" to "العفاسي", "Abdul_Basit_Murattal_192kbps" to "عبد الباسط",
    )
    fun audio(reciter: String, surah: Int, ayah: Int) =
        "https://everyayah.com/data/$reciter/" + String.format(Locale.US, "%03d%03d", surah, ayah) + ".mp3"

    private fun sp() = SafiApp.instance.getSharedPreferences("safi_hifz", Context.MODE_PRIVATE)
    val version = mutableIntStateOf(0)
    var reciter: String get() = sp().getString("reciter", reciters.first().first)!!; set(v) = sp().edit { putString("reciter", v) }
    var repeat: Int get() = sp().getInt("repeat", 3); set(v) = sp().edit { putInt("repeat", v) }

    /** Memorized verses: "surah:ayah" -> {level (0..5), next review date}. */
    private val gaps = listOf(1L, 3L, 7L, 14L, 30L, 60L)
    private fun key(s: Int, a: Int) = "$s:$a"
    private fun rec(s: Int, a: Int): JSONObject? = sp().getString("m_" + key(s, a), null)?.let { runCatching { JSONObject(it) }.getOrNull() }
    fun isMemorized(s: Int, a: Int) = rec(s, a) != null
    fun memorizedIn(s: Int): Int = sp().all.keys.count { it.startsWith("m_$s:") }
    val total: Int get() = sp().all.keys.count { it.startsWith("m_") }

    /** Marks verses as memorized (first review tomorrow). */
    fun markMemorized(s: Int, from: Int, to: Int) {
        val next = LocalDate.now(zone).plusDays(1).toString()
        sp().edit { for (a in from..to) putString("m_" + key(s, a), JSONObject().put("lv", 0).put("next", next).toString()) }
        version.intValue++
        runCatching { com.mohamed.safi.quiz.Challenge.recordFamilyActivity("hifz", (to - from + 1).coerceIn(1, 10)) }
    }
    fun forget(s: Int, a: Int) { sp().edit { remove("m_" + key(s, a)) }; version.intValue++ }

    /** After a review: remembered moves to a longer gap, forgot goes back to tomorrow. */
    fun reviewed(s: Int, a: Int, remembered: Boolean) {
        val r = rec(s, a) ?: return
        val lv = if (remembered) (r.optInt("lv") + 1).coerceAtMost(gaps.size - 1) else 0
        val next = LocalDate.now(zone).plusDays(gaps[lv]).toString()
        sp().edit { putString("m_" + key(s, a), r.put("lv", lv).put("next", next).toString()) }
        version.intValue++
    }

    /** Verses due for review today, grouped as (surah, ayah). */
    fun due(today: LocalDate = LocalDate.now(zone)): List<Pair<Int, Int>> = sp().all.entries.filter { it.key.startsWith("m_") }.mapNotNull { (k, v) ->
        val o = runCatching { JSONObject(v as String) }.getOrNull() ?: return@mapNotNull null
        if (o.optString("next") <= today.toString()) k.removePrefix("m_").split(":").let { it[0].toInt() to it[1].toInt() } else null
    }.sortedWith(compareBy({ it.first }, { it.second }))

    /** The last surah/range worked on, to continue from. */
    var last: Triple<Int, Int, Int>
        get() = sp().getString("last", "114:1:6")!!.split(":").map { it.toIntOrNull() ?: 1 }.let { p -> Triple(p[0], p[1], p.getOrElse(2) { p[1] }) }
        set(v) = sp().edit { putString("last", "${v.first}:${v.second}:${v.third}") }

    /** Hides words for the self-test: [level] 1 = every other word, 2 = all but the first word, 3 = all. */
    fun hide(text: String, level: Int): String {
        val words = text.split(" ")
        return words.mapIndexed { i, w ->
            val hidden = when (level) { 1 -> i % 2 == 1; 2 -> i > 0; else -> true }
            if (hidden && w.isNotBlank()) "▁".repeat(w.length.coerceIn(2, 6)) else w
        }.joinToString(" ")
    }
}
