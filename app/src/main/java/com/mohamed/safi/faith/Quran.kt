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
import java.util.concurrent.TimeUnit

data class Ayah(val n: Int, val text: String, val juz: Int, val page: Int, val sajda: Boolean) {
    /** Diacritic-free text for search, computed once at load. */
    val plain: String = Quran.plain(text)
}
data class Surah(val number: Int, val name: String, val english: String, val revelation: String, val ayahs: List<Ayah>) {
    val revelationAr get() = if (revelation.equals("Meccan", true)) "مكية" else "مدنية"
}

/** Full Quran text (Uthmani script, Tanzil text via alquran.cloud). Bundled in the APK, downloaded if missing. */
object Quran {
    private const val URL = "https://api.alquran.cloud/v1/quran/quran-uthmani"
    private val lock = Mutex()
    @Volatile private var cache: List<Surah>? = null

    const val BASMALA = "بِسْمِ ٱللَّهِ ٱلرَّحْمَٰنِ ٱلرَّحِيمِ"

    private fun file(ctx: Context) = File(ctx.filesDir, "quran.json")

    private fun readBundled(ctx: Context): String? =
        runCatching { ctx.assets.open("quran.json").bufferedReader().use { it.readText() } }.getOrNull()?.takeIf { it.length > 100_000 }

    suspend fun surahs(ctx: Context = SafiApp.instance): List<Surah> = lock.withLock {
        cache?.let { return@withLock it }
        withContext(Dispatchers.IO) {
            val text = readBundled(ctx)
                ?: file(ctx).takeIf { it.length() > 100_000 }?.readText()
                ?: run {
                    val http = OkHttpClient.Builder().readTimeout(90, TimeUnit.SECONDS).build()
                    http.newCall(Request.Builder().url(URL).build()).execute().use { r ->
                        if (!r.isSuccessful) throw IllegalStateException("تحميل المصحف فشل (${r.code})")
                        val body = r.body?.string() ?: throw IllegalStateException("تحميل المصحف فشل")
                        file(ctx).writeText(body)
                        body
                    }
                }
            val arr = JSONObject(text).getJSONObject("data").getJSONArray("surahs")
            val list = (0 until arr.length()).map { i ->
                val s = arr.getJSONObject(i)
                val num = s.getInt("number")
                val ay = s.getJSONArray("ayahs")
                Surah(
                    number = num,
                    name = s.optString("name"),
                    english = s.optString("englishName"),
                    revelation = s.optString("revelationType"),
                    ayahs = (0 until ay.length()).map { k ->
                        val a = ay.getJSONObject(k)
                        var t = a.getString("text").replace("\uFEFF", "")
                        // The first verse of each surah (except Al-Fatiha and At-Tawba) starts with the basmala in this edition
                        if (k == 0 && num != 1 && num != 9) t = stripBasmala(t)
                        Ayah(a.getInt("numberInSurah"), t.trim(), a.optInt("juz"), a.optInt("page"), a.opt("sajda").let { it is JSONObject || it == true })
                    },
                )
            }
            cache = list
            list
        }
    }

    private fun stripBasmala(t: String): String {
        val words = t.trim().split(spaces)
        return if (words.size > 4 && plain(words.take(4).joinToString(" ")).startsWith("بسم الله الرحمن الرحيم")) words.drop(4).joinToString(" ") else t
    }

    private val marks = Regex("[\\u0610-\\u061A\\u064B-\\u065F\\u0670\\u06D6-\\u06ED\\u08D3-\\u08FF]")
    private val spaces = Regex("\\s+")

    /** Removes diacritics/Quranic marks for searching. */
    fun plain(s: String): String = s
        .replace(marks, "")
        .replace('ٱ', 'ا').replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا')
        .replace('ى', 'ي').replace('ة', 'ه').replace("ـ", "")

    data class Hit(val surah: Surah, val ayah: Ayah)

    fun search(list: List<Surah>, q: String, limit: Int = 100): List<Hit> {
        val qq = plain(q.trim())
        if (qq.length < 2) return emptyList()
        val out = mutableListOf<Hit>()
        for (s in list) for (a in s.ayahs) {
            if (a.plain.contains(qq)) {
                out += Hit(s, a)
                if (out.size >= limit) return out
            }
        }
        return out
    }

    fun toArabicDigits(n: Int): String = n.toString().map { '٠' + (it - '0') }.joinToString("")

    // ---------- bookmark / settings ----------
    private fun sp() = SafiApp.instance.getSharedPreferences("safi_quran", Context.MODE_PRIVATE)
    var lastSurah: Int get() = sp().getInt("lastSurah", 0); set(v) = sp().edit { putInt("lastSurah", v) }
    var lastAyah: Int get() = sp().getInt("lastAyah", 1); set(v) = sp().edit { putInt("lastAyah", v) }
    var fontSize: Int get() = sp().getInt("fontSize", 26); set(v) = sp().edit { putInt("fontSize", v) }
    var bookmarks: Set<String> get() = sp().getStringSet("bookmarks", emptySet()) ?: emptySet(); set(v) = sp().edit { putStringSet("bookmarks", v) }

    fun hasFont(ctx: Context) = runCatching { ctx.assets.list("fonts")?.contains("quran.ttf") == true }.getOrDefault(false)
}
