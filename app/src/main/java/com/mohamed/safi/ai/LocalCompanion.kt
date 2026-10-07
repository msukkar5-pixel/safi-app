package com.mohamed.safi.ai

import com.mohamed.safi.SafiApp
import com.mohamed.safi.data.zone
import com.mohamed.safi.faith.SituationSupport
import com.mohamed.safi.notify.Brief
import java.time.LocalDate

/**
 * Helpful offline fallback. It never pretends to be a large language model,
 * never uploads data, and only uses information already stored on the device.
 */
object LocalCompanion {
    const val LABEL = "🔒 رد محلي"

    suspend fun reply(message: String): String {
        val text = message.trim()
        val normalized = text.lowercase()
        val support = runCatching { SituationSupport.forMessage(text) }.getOrNull()
        val base = AppGuide.localReply(text) ?: when {
            text.isBlank() -> "اكتب لي اللي محتاجه، وأنا أساعدك باللي متاح على الموبايل حتى من غير إنترنت."
            has(normalized, "افتكر", "خلي بالك", "تذكر") -> remember(text)
            has(normalized, "نسيت", "امسح", "متفتكرش") -> "لو تقصد ذاكرة رفيق، افتح «رفيق وذاكرته» من الإعدادات واحذف المعلومة اللي مش عايزها."
            has(normalized, "ورد", "قرآن", "مصحف") -> wird()
            has(normalized, "مطلوب", "ميعاد", "تنبيه", "واجب") -> today()
            has(normalized, "تحدي", "مسابقة", "دوري") -> "عندك دوري أسبوعي وماراثون معرفة وتحديات عيلة وأصحاب. افتح المسابقة واختار اللي يناسبك؛ تقدم العيلة لا يظهر إلا للأعضاء المعتمدين."
            has(normalized, "مذاكرة", "دراسة", "امتحان") -> "خلّيها بداية صغيرة: اختار 25 دقيقة تركيز، اقفل المشتتات، وبعدها اكتب سطرين عن اللي خلصته. تقدر تفتح «دروسي وواجباتي» لتسجل الجلسة."
            has(normalized, "وحدة", "لوحدي", "زعلان", "خايف", "قلقان", "متوتر", "مضغوط") -> "أنا موجود معاك هنا. مش لازم تحل كل شيء دلوقتي؛ اختار خطوة بسيطة وآمنة: اشرب مية، اكتب اللي مضايقك، أو كلم شخص تثق فيه."
            has(normalized, "صباح", "خطة اليوم", "يومي") -> dayPlan()
            else -> "أنا في الوضع المحلي من غير إنترنت: أقدر أراجع وردك وموجز يومك، أقترح خطوة بسيطة، وأحفظ تفضيلًا لو قلت «افتكر». للشرح المفتوح أو تنفيذ الأوامر المعقدة، اربط مزود ذكاء من الإعدادات."
        }
        return support?.let { SituationSupport.automaticAddition(base, it) } ?: base
    }

    private fun remember(text: String): String {
        val value = text.replace(Regex("(?i)^(افتكر|خلي بالك|تذكريني?|تذكر)\\s*(إني|انني|ان|إن)?\\s*"), "").trim()
        if (value.length < 3) return "قول لي المعلومة أو التفضيل اللي تحب أحفظه، مثل: «افتكر إني بحب الرد المختصر»."
        val normalized = value.lowercase()
        val category = when {
            listOf("قلق", "متوتر", "زعلان", "وحيد", "خايف", "مضغوط", "مزاج", "بحس").any { it in normalized } -> "feeling"
            listOf("يهديني", "يريحني", "يساعدني", "بفضل", "طمني").any { it in normalized } -> "comfort"
            listOf("ماما", "بابا", "ابني", "بنتي", "مراتي", "زوج", "صاحبي", "صديقتي").any { it in normalized } -> "relationship"
            else -> "general"
        }
        val m = CompanionProfile.rememberMemory(category, value)
        return if (m == null) "محتاج معلومة أوضح شوية علشان أحفظها صح." else "✓ حفظت المعلومة محليًا في ملفك: $value"
    }

    private fun wird(): String {
        val w = com.mohamed.safi.faith.Wird
        return if (w.doneToday) "✓ وردك اليوم خلص، تقبّل الله. بكرة تبدأ من صفحة ${w.nextPage}."
        else "وردك اليوم من صفحة ${w.todayRange().first} إلى ${w.todayRange().last}. حتى صفحة واحدة بداية جميلة."
    }

    private suspend fun today(): String {
        val lines = Brief.todayLines().take(4)
        return if (lines.isEmpty()) "مفيش التزامات أو مواعيد قريبة مسجلة. تقدر تضيف تذكير من جدولك، أو اكتب لي التفاصيل بعد ما تربط الذكاء المتصل."
        else "أهم اللي قريب منك اليوم:\n" + lines.joinToString("\n") { "• $it" }
    }

    private suspend fun dayPlan(): String {
        val date = LocalDate.now(zone)
        val lines = Brief.todayLines().take(3)
        return buildString {
            append("خطة ${date.dayOfMonth}/${date.monthValue}: اختار مهمة ضرورية، مهمة لنفسك، وراحة قصيرة. ")
            if (lines.isEmpty()) append("مفيش مواعيد مسجلة قريبة؛ ابدأ بحاجه صغيرة تقدر تخلصها في 20 دقيقة.")
            else append("أقرب مواعيدك: ").append(lines.joinToString(" • "))
        }
    }

    private fun has(text: String, vararg words: String) = words.any { it in text }
}
