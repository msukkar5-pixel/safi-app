package com.mohamed.safi.faith

import android.content.Context
import androidx.core.content.edit
import com.mohamed.safi.SafiApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.util.concurrent.TimeUnit

data class Hadith(val book: String, val number: Int, val section: Int, val text: String)
data class HadithSection(val number: Int, val name: String, val first: Int, val last: Int)
data class HadithBook(val id: String, val title: String, val sections: List<HadithSection>, val hadiths: List<Hadith>)

/**
 * Sahih collections only: Sahih al-Bukhari and Sahih Muslim (every hadith in both is authentic by scholarly consensus).
 * Arabic text from the public-domain hadith-api dataset (fawazahmed0), bundled in the app.
 */
object Hadiths {
    val books = listOf("bukhari" to "صحيح البخاري", "muslim" to "صحيح مسلم")
    private val lock = Mutex()
    private val cache = mutableMapOf<String, HadithBook>()

    private val bukhariAr = listOf(
        "بدء الوحي", "الإيمان", "العلم", "الوضوء", "الغسل", "الحيض", "التيمم", "الصلاة", "مواقيت الصلاة", "الأذان",
        "الجمعة", "صلاة الخوف", "العيدين", "الوتر", "الاستسقاء", "الكسوف", "سجود القرآن", "تقصير الصلاة", "التهجد", "فضل الصلاة في مسجد مكة والمدينة",
        "العمل في الصلاة", "السهو", "الجنائز", "الزكاة", "الحج", "العمرة", "المحصر", "جزاء الصيد", "فضائل المدينة", "الصوم",
        "صلاة التراويح", "فضل ليلة القدر", "الاعتكاف", "البيوع", "السلم", "الشفعة", "الإجارة", "الحوالات", "الكفالة", "الوكالة",
        "المزارعة", "المساقاة", "الاستقراض وأداء الديون والحجر والتفليس", "الخصومات", "اللقطة", "المظالم", "الشركة", "الرهن", "العتق", "المكاتب",
        "الهبة", "الشهادات", "الصلح", "الشروط", "الوصايا", "الجهاد والسير", "فرض الخمس", "الجزية والموادعة", "بدء الخلق", "أحاديث الأنبياء",
        "المناقب", "فضائل أصحاب النبي ﷺ", "مناقب الأنصار", "المغازي", "التفسير", "فضائل القرآن", "النكاح", "الطلاق", "النفقات", "الأطعمة",
        "العقيقة", "الذبائح والصيد", "الأضاحي", "الأشربة", "المرضى", "الطب", "اللباس", "الأدب", "الاستئذان", "الدعوات",
        "الرقاق", "القدر", "الأيمان والنذور", "كفارات الأيمان", "الفرائض", "الحدود", "الديات", "استتابة المرتدين", "الإكراه", "الحيل",
        "التعبير", "الفتن", "الأحكام", "التمني", "أخبار الآحاد", "الاعتصام بالكتاب والسنة", "التوحيد",
    )
    private val muslimAr = listOf(
        "الإيمان", "الطهارة", "الحيض", "الصلاة", "المساجد ومواضع الصلاة", "صلاة المسافرين وقصرها", "الجمعة", "صلاة العيدين", "صلاة الاستسقاء", "الكسوف",
        "الجنائز", "الزكاة", "الصيام", "الاعتكاف", "الحج", "النكاح", "الرضاع", "الطلاق", "اللعان", "العتق",
        "البيوع", "المساقاة", "الفرائض", "الهبات", "الوصية", "النذر", "الأيمان", "القسامة والمحاربين والقصاص والديات", "الحدود", "الأقضية",
        "اللقطة", "الجهاد والسير", "الإمارة", "الصيد والذبائح", "الأضاحي", "الأشربة", "اللباس والزينة", "الآداب", "السلام", "الألفاظ من الأدب وغيرها",
        "الشعر", "الرؤيا", "الفضائل", "فضائل الصحابة", "البر والصلة والآداب", "القدر", "العلم", "الذكر والدعاء والتوبة والاستغفار", "الرقاق", "التوبة",
        "صفات المنافقين وأحكامهم", "صفة القيامة والجنة والنار", "الجنة وصفة نعيمها وأهلها", "الفتن وأشراط الساعة", "الزهد والرقائق", "التفسير",
    )

    private fun arName(book: String, n: Int, en: String, maxKey: Int): String {
        val list = if (book == "bukhari") bukhariAr else muslimAr
        if (book == "muslim" && n == 0) return "المقدمة"
        return if (maxKey == list.size && n in 1..list.size) "كتاب " + list[n - 1] else "الكتاب $n" + if (en.isNotBlank()) " — $en" else ""
    }

    private fun file(ctx: Context, id: String) = File(ctx.filesDir, "hadith_$id.json")

    suspend fun load(id: String, ctx: Context = SafiApp.instance): HadithBook = lock.withLock {
        cache[id]?.let { return@withLock it }
        withContext(Dispatchers.IO) {
            val text = runCatching { ctx.assets.open("hadith/$id.json").bufferedReader().use { it.readText() } }.getOrNull()
                ?: file(ctx, id).takeIf { it.length() > 100_000 }?.readText()
                ?: run {
                    val http = OkHttpClient.Builder().readTimeout(120, TimeUnit.SECONDS).build()
                    val url = "https://cdn.jsdelivr.net/gh/fawazahmed0/hadith-api@1/editions/ara-$id.min.json"
                    http.newCall(Request.Builder().url(url).build()).execute().use { r ->
                        if (!r.isSuccessful) throw IllegalStateException("التحميل فشل (${r.code})")
                        val b = r.body?.string() ?: throw IllegalStateException("التحميل فشل")
                        file(ctx, id).writeText(b); b
                    }
                }
            val j = JSONObject(text)
            val meta = j.getJSONObject("metadata")
            val sec = meta.optJSONObject("sections") ?: meta.optJSONObject("section") ?: JSONObject()
            val det = meta.optJSONObject("section_details") ?: meta.optJSONObject("section_detail") ?: JSONObject()
            val keys = sec.keys().asSequence().mapNotNull { it.toIntOrNull() }.toList()
            val maxKey = keys.maxOrNull() ?: 0
            val sections = keys.sorted().mapNotNull { n ->
                val en = sec.optString(n.toString())
                val d = det.optJSONObject(n.toString())
                val first = d?.optInt("hadithnumber_first", 0) ?: 0
                val last = d?.optInt("hadithnumber_last", 0) ?: 0
                if (en.isBlank() && last == 0) null else HadithSection(n, arName(id, n, en, maxKey), first, last)
            }
            val arr = j.getJSONArray("hadiths")
            val hs = (0 until arr.length()).mapNotNull { i ->
                val h = arr.getJSONObject(i)
                val t = h.optString("text").trim()
                if (t.isEmpty()) null else Hadith(
                    id, h.optInt("hadithnumber"), h.optJSONObject("reference")?.optInt("book", 0) ?: 0, t,
                )
            }
            val book = HadithBook(id, books.first { it.first == id }.second, sections, hs)
            cache[id] = book
            book
        }
    }

    fun search(book: HadithBook, q: String, limit: Int = 150): List<Hadith> {
        val qq = Quran.plain(q.trim())
        if (qq.length < 2) return emptyList()
        val words = qq.split(Regex("\\s+")).filter { it.isNotBlank() }
        return book.hadiths.asSequence().filter { h -> val p = Quran.plain(h.text); words.all { it in p } }.take(limit).toList()
    }

    /** Same hadith all day, changes daily. */
    suspend fun ofTheDay(): Hadith? = runCatching {
        val b = load(if (LocalDate.now().dayOfYear % 2 == 0) "bukhari" else "muslim")
        val short = b.hadiths.filter { it.text.length in 120..600 }
        val seed = LocalDate.now().toEpochDay()
        short[(seed % short.size).toInt()]
    }.getOrNull()

    fun bookTitle(id: String) = books.firstOrNull { it.first == id }?.second ?: id

    // ---------- favourites ----------
    private fun sp() = SafiApp.instance.getSharedPreferences("safi_hadith", Context.MODE_PRIVATE)
    var favourites: Set<String> get() = sp().getStringSet("fav", emptySet()) ?: emptySet(); set(v) = sp().edit { putStringSet("fav", v) }
    fun key(h: Hadith) = "${h.book}:${h.number}"
}
