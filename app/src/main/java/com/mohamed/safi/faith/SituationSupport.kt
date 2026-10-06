package com.mohamed.safi.faith

/**
 * Situation-aware spiritual support. It runs only after the user writes a request or describes a situation;
 * it never listens in the background. All quoted material comes from the app's Quran, Hisn/adhkar and
 * Bukhari/Muslim datasets, and the prompt tells the model not to invent an attribution.
 */
object SituationSupport {
    data class Guidance(
        val situation: String,
        val ayah: Quran.Hit?,
        val hadith: Hadith?,
        val dua: Zikr?,
    )

    private data class Cue(val label: String, val quran: String, val hadith: String, val zikr: String)

    private val cues = listOf(
        Cue("الضيق والقلق", "تطمئن", "الهم والحزن", "الهم والحزن"),
        Cue("الحزن والفقد", "تحزنوا", "الصبر", "المصيبة"),
        Cue("الغضب", "الكاظمين الغيظ", "الغضب", "الغضب"),
        Cue("الخوف", "تخافا", "الخوف", "الخوف"),
        Cue("الذنب والندم", "أسرفوا", "التوبة", "التوبة"),
        Cue("الشكر والامتنان", "شكرتم", "الشكر", "الشكر"),
        Cue("النوم", "أنفسكم", "النوم", "النوم"),
    )

    private fun cueFor(text: String): Cue? {
        val t = Quran.plain(text)
        val keys = listOf(
            cues[6] to listOf("نوم", "نايم", "انام", "قبل ما انام"),
            cues[2] to listOf("غضبان", "عصبي", "غضب", "متعصب"),
            cues[3] to listOf("خايف", "خوف", "مرعوب", "قلقان من"),
            cues[4] to listOf("ذنب", "ذنبي", "ندمان", "توبه", "توبة"),
            cues[5] to listOf("شكر", "نعمه", "نعمة", "الحمد لله"),
            cues[1] to listOf("حزين", "حزن", "فقد", "وحشني", "موجوع"),
            cues[0] to listOf("مضايق", "ضيق", "قلق", "متوتر", "مخنوق", "هم", "زعلان", "مش قادر"),
        )
        return keys.firstOrNull { (_, words) -> words.any { it in t } }?.first
    }

    suspend fun forMessage(text: String): Guidance? {
        val cue = cueFor(text) ?: return null
        val ayah = runCatching { Quran.search(Quran.surahs(), cue.quran, 1).firstOrNull() }.getOrNull()
        val hadith = runCatching {
            val book = Hadiths.load(if (cue == cues[2] || cue == cues[3]) "muslim" else "bukhari")
            Hadiths.search(book, cue.hadith, 12).firstOrNull()
        }.getOrNull()
        val dua = runCatching {
            Azkar.all().asSequence()
                .filter { cue.zikr in it.name || it.name in cue.zikr }
                .flatMap { it.items.asSequence() }
                .firstOrNull()
                ?: Azkar.all().asSequence().flatMap { it.items.asSequence() }.firstOrNull { cue.zikr in it.desc }
        }.getOrNull()
        return Guidance(cue.label, ayah, hadith, dua).takeIf { it.ayah != null || it.hadith != null || it.dua != null }
    }

    fun prompt(g: Guidance): String = buildString {
        appendLine("SITUATION SUPPORT (only use when relevant to the user's explicit message): ${g.situation}")
        g.ayah?.let { appendLine("QURAN AYAH: ${it.ayah.text} — سورة ${it.surah.name}, آية ${it.ayah.n}") }
        g.hadith?.let { appendLine("AUTHENTIC HADITH: ${it.text} — ${Hadiths.bookTitle(it.book)}, رقم ${it.number}") }
        g.dua?.let { appendLine("DUA/AZKAR: ${it.text}${if (it.ref.isBlank()) "" else " — ${it.ref}"}") }
        appendLine("Use gentle wording, offer one short practical step, and never claim to diagnose or to have heard anything in the background. Do not invent or alter religious attributions.")
    }
}
