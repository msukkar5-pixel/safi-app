package com.mohamed.safi.faith

/**
 * Situation-aware spiritual support. It runs only after the user writes a request or describes a situation;
 * it never listens in the background. Matching is ranked by context and explicit intent, not by one substring.
 * All quoted material comes from the app's Quran, Hisn/adhkar and Bukhari/Muslim datasets.
 */
object SituationSupport {
    data class Guidance(
        val situation: String,
        val confidence: Int,
        val ayah: Quran.Hit?,
        val hadith: Hadith?,
        val dua: Zikr?,
    )

    private data class Cue(
        val label: String,
        val triggers: List<Pair<String, Int>>,
        val quranQueries: List<String>,
        val hadithQueries: List<String>,
        val zikrTerms: List<String>,
    )

    private val cues = listOf(
        Cue(
            "الهلع والذعر",
            listOf("نوبة هلع" to 7, "نوبه هلع" to 7, "نوبة ذعر" to 7, "نوبه ذعر" to 7, "مش قادر أتنفس" to 6, "مش قادر اتنفس" to 6, "ضيق نفس" to 6, "نفسي مقطوع" to 6, "حاسس هموت" to 6, "هفقد السيطرة" to 6, "قلبي سريع" to 5, "رجفة" to 5, "رعشة" to 5, "دوخة" to 4, "محبوس" to 3),
            listOf("تطمئن", "ضيق", "حسبنا الله", "وسعها"), listOf("الكرب", "الهم والحزن", "الفرج"), listOf("الكرب", "الهم", "الخوف"),
        ),
        Cue(
            "قلق التوقع وكثرة التفكير",
            listOf("تفكير زائد" to 5, "كثرة التفكير" to 5, "مش عارف أبطل تفكير" to 5, "مش عارف ابطل تفكير" to 5, "مستني النتيجة" to 4, "قلقان من بكرة" to 5, "خايف يحصل" to 4, "قبل الامتحان" to 3, "قبل المقابلة" to 3, "مش عارف أنام من التفكير" to 5),
            listOf("العسر", "تطمئن", "يتوكل"), listOf("الهم والحزن", "التوكل", "الكرب"), listOf("الهم", "التوكل", "النوم"),
        ),
        Cue(
            "الضيق والقلق",
            listOf("مضايق" to 4, "مخنوق" to 4, "قلقان" to 4, "قلق" to 3, "متوتر" to 3, "توتر" to 3, "ضغط" to 2, "هم" to 2, "مش قادر" to 2, "مش عارف اتصرف" to 2),
            listOf("تطمئن", "العسر", "وسعها"), listOf("الهم والحزن", "الكرب"), listOf("الهم", "الكرب", "الضيق"),
        ),
        Cue(
            "الحزن والفقد",
            listOf("حزين" to 4, "حزن" to 3, "فقد" to 4, "فقدت" to 4, "وحشني" to 3, "فراق" to 3, "موجوع" to 2, "بعيط" to 2),
            listOf("الصابرين", "تحزنوا", "وبشر الصابرين"), listOf("الصبر", "المصيبة", "الحزن"), listOf("المصيبة", "الحزن", "الصبر"),
        ),
        Cue(
            "الغضب والانفعال",
            listOf("غضبان" to 5, "غضب" to 4, "عصبي" to 4, "متعصب" to 4, "اتخانقت" to 3, "خناقة" to 3, "منفعل" to 3),
            listOf("الكاظمين الغيظ", "ادفع بالتي هي أحسن", "والكاظمين"), listOf("الغضب", "القوة"), listOf("الغضب", "الغيظ"),
        ),
        Cue(
            "الخوف",
            listOf("خايف" to 5, "خوف" to 4, "مرعوب" to 5, "فزع" to 4, "قلبي بيخبط" to 3, "قلقان من" to 2),
            listOf("تخافا", "حسبنا الله", "يتوكل"), listOf("الخوف", "التوكل"), listOf("الخوف", "الحفظ"),
        ),
        Cue(
            "الذنب والندم",
            listOf("ذنب" to 5, "ذنبي" to 5, "ندمان" to 4, "ندم" to 4, "توبة" to 5, "توبه" to 5, "غلطت" to 3),
            listOf("لا تقنطوا", "أسرفوا", "يغفر الذنوب", "التوابين"), listOf("التوبة", "الاستغفار"), listOf("التوبة", "الاستغفار"),
        ),
        Cue(
            "الشكر والامتنان",
            listOf("شكر" to 4, "نعمة" to 4, "نعمه" to 4, "الحمد لله" to 4, "ممتن" to 4, "فضل" to 2),
            listOf("شكرتم", "بنعمة ربك", "فاذكروني"), listOf("الشكر", "الحمد"), listOf("الشكر", "الحمد"),
        ),
        Cue(
            "النوم والسكينة",
            listOf("نوم" to 5, "نايم" to 4, "انام" to 4, "قبل ما انام" to 5, "مش عارف أنام" to 5, "أرق" to 4),
            listOf("منامكم", "سكنا"), listOf("النوم", "الفراش"), listOf("النوم", "الفراش"),
        ),
    )

    private fun normalized(s: String): String = Quran.plain(s)
        .replace(Regex("[،؛؟!,.ـ]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun cueFor(text: String): Pair<Cue, Int>? {
        val t = normalized(text)
        val asksForReligious = listOf("اية", "حديث", "دعاء", "ذكر", "اذكار").any { normalized(it) in t }
        val ranked = cues.map { cue ->
            val matched = cue.triggers.filter { (phrase, _) -> normalized(phrase) in t }
            val score = matched.sumOf { it.second } + if (asksForReligious && matched.isNotEmpty()) 2 else 0
            cue to score
        }.filter { it.second >= 3 }.sortedByDescending { it.second }
        return ranked.firstOrNull()
    }

    private suspend fun ayahFor(cue: Cue): Quran.Hit? {
        val surahs = runCatching { Quran.surahs() }.getOrNull() ?: return null
        return cue.quranQueries.asSequence().mapNotNull { query -> Quran.search(surahs, query, 3).firstOrNull() }.firstOrNull()
    }

    private suspend fun hadithFor(cue: Cue): Hadith? {
        val bookId = if (cue.label == "الغضب والانفعال" || cue.label == "الخوف") "muslim" else "bukhari"
        val book = runCatching { Hadiths.load(bookId) }.getOrNull() ?: return null
        return cue.hadithQueries.asSequence().mapNotNull { query -> Hadiths.search(book, query, 8).firstOrNull() }.firstOrNull()
    }

    private fun duaFor(cue: Cue): Zikr? {
        val all = runCatching { Azkar.all() }.getOrNull() ?: return null
        val byCategory = all.asSequence()
            .filter { category -> cue.zikrTerms.any { term -> term in category.name || category.name in term } }
            .flatMap { it.items.asSequence() }
            .toList()
        return byCategory.firstOrNull()
            ?: all.asSequence().flatMap { it.items.asSequence() }.firstOrNull { z -> cue.zikrTerms.any { it in z.desc || it in z.text } }
    }

    suspend fun forMessage(text: String): Guidance? {
        val (cue, score) = cueFor(text) ?: return null
        val ayah = ayahFor(cue)
        val hadith = hadithFor(cue)
        val dua = duaFor(cue)
        return Guidance(cue.label, score, ayah, hadith, dua).takeIf { it.ayah != null || it.hadith != null || it.dua != null }
    }

    fun prompt(g: Guidance): String = buildString {
        appendLine("SITUATION SUPPORT (explicit user message; confidence ${g.confidence}): ${g.situation}")
        g.ayah?.let { appendLine("QURAN AYAH: ${it.ayah.text} — سورة ${it.surah.name}, آية ${it.ayah.n}") }
        g.hadith?.let { appendLine("AUTHENTIC HADITH: ${it.text} — ${Hadiths.bookTitle(it.book)}, رقم ${it.number}") }
        g.dua?.let { appendLine("DUA/AZKAR: ${it.text}${if (it.ref.isBlank()) "" else " — ${it.ref}"}") }
        appendLine("Use gentle wording, offer one short practical step, and never claim to diagnose or to have heard anything in the background. Do not invent or alter religious attributions. If confidence is low or the quote is not relevant, do not force it.")
    }

    /** Deterministic fallback so the feature is truly automatic even if the model ignores the support block. */
    fun automaticAddition(reply: String, g: Guidance): String? {
        if (g.confidence < 4) return null
        val r = normalized(reply)
        val alreadyQuoted = listOfNotNull(
            g.ayah?.ayah?.text,
            g.hadith?.text,
            g.dua?.text,
        ).any { quote -> normalized(quote).take(28).let { it.length >= 12 && it in r } }
        if (alreadyQuoted || r.contains("سوره") || r.contains("حديث") || r.contains("دعاء")) return null
        val line = when {
            g.ayah != null -> "📖 ${g.ayah.ayah.text} — سورة ${g.ayah.surah.name}، آية ${g.ayah.ayah.n}"
            g.hadith != null -> "📜 ${g.hadith.text} — ${Hadiths.bookTitle(g.hadith.book)}، رقم ${g.hadith.number}"
            g.dua != null -> "🤲 ${g.dua.text}${if (g.dua.ref.isBlank()) "" else " — ${g.dua.ref}"}"
            else -> return null
        }
        return "$reply\n\n${when (g.situation) { "الهلع والذعر" -> "خد نفسًا هادئًا، وخلي الخطوة الجاية بسيطة."; "قلق التوقع وكثرة التفكير" -> "خلّي تركيزك في الخطوة اللي قدامك بس."; "الغضب والانفعال" -> "خد لحظة قبل ما ترد."; "الخوف" -> "ربنا يطمّن قلبك."; "الحزن والفقد" -> "ربنا يربط على قلبك."; else -> "ربنا يخفف عنك." }}\n$line"
    }

    fun spokenGuidance(g: Guidance): String {
        val opening = when (g.situation) {
            "الهلع والذعر" -> "خد نفسًا هادئًا، وخلي الخطوة الجاية بسيطة."
            "قلق التوقع وكثرة التفكير" -> "خلّي تركيزك في الخطوة اللي قدامك بس."
            "الغضب والانفعال" -> "خد لحظة قبل ما ترد."
            "الخوف" -> "ربنا يطمّن قلبك."
            "الحزن والفقد" -> "ربنا يربط على قلبك."
            else -> "ربنا يخفف عنك."
        }
        val content = g.ayah?.let { it.ayah.text }
            ?: g.hadith?.text
            ?: g.dua?.text
            ?: return opening
        return "$opening $content"
    }
}
