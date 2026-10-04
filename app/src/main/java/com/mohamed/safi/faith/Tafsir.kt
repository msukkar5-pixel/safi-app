package com.mohamed.safi.faith

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.edit
import com.mohamed.safi.SafiApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/** Full tafsir for every ayah: التفسير الميسر and تفسير الجلالين (bundled, downloaded if missing). */
object Tafsir {
    val editions = listOf("muyassar" to "التفسير الميسر", "jalalayn" to "تفسير الجلالين")
    private val lock = Mutex()
    private val cache = mutableMapOf<String, Map<Int, List<String>>>()   // surah -> ayah texts

    private fun file(ctx: Context, id: String) = File(ctx.filesDir, "tafsir_$id.json")

    suspend fun load(id: String, ctx: Context = SafiApp.instance): Map<Int, List<String>> = lock.withLock {
        cache[id]?.let { return@withLock it }
        withContext(Dispatchers.IO) {
            val text = runCatching { ctx.assets.open("tafsir/$id.json").bufferedReader().use { it.readText() } }.getOrNull()
                ?: file(ctx, id).takeIf { it.length() > 100_000 }?.readText()
                ?: OkHttpClient.Builder().readTimeout(120, TimeUnit.SECONDS).build()
                    .newCall(Request.Builder().url("https://api.alquran.cloud/v1/quran/ar.$id").build()).execute().use { r ->
                        if (!r.isSuccessful) throw IllegalStateException("تحميل التفسير فشل (${r.code})")
                        (r.body?.string() ?: throw IllegalStateException("تحميل التفسير فشل")).also { file(ctx, id).writeText(it) }
                    }
            val surahs = JSONObject(text).getJSONObject("data").getJSONArray("surahs")
            val map = (0 until surahs.length()).associate { i ->
                val s = surahs.getJSONObject(i)
                val ay = s.getJSONArray("ayahs")
                s.getInt("number") to (0 until ay.length()).map { ay.getJSONObject(it).getString("text").trim() }
            }
            cache[id] = map
            map
        }
    }

    suspend fun of(id: String, surah: Int, ayah: Int): String? =
        runCatching { load(id)[surah]?.getOrNull(ayah - 1) }.getOrNull()
}

/** Sheikh Muhammad Metwally Al-Shaarawy — links only (YouTube searches + links the user saves). */
object Shaarawy {
    val topics = listOf(
        "الصبر", "الرزق", "التوكل على الله", "بر الوالدين", "القضاء والقدر", "الدعاء", "التوبة", "الموت والآخرة",
        "الحسد", "الزواج والأسرة", "تربية الأولاد", "الأمانة", "الشكر", "الخوف والرجاء", "الابتلاء", "معجزات القرآن",
        "قصص الأنبياء", "سيدنا يوسف", "سيدنا موسى", "سيدنا إبراهيم", "الصلاة", "الصيام", "الزكاة", "الحج",
    )

    fun surahSearch(surahName: String) = "خواطر الشعراوي " + surahName.replace("سُورَةُ", "سورة").let { Quran.plain(it) }
    fun topicSearch(topic: String) = "الشيخ الشعراوي $topic"

    fun open(ctx: Context, query: String) {
        val url = "https://www.youtube.com/results?search_query=" + Uri.encode(query)
        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    fun openUrl(ctx: Context, url: String) {
        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    data class Link(val title: String, val url: String)

    private fun sp() = SafiApp.instance.getSharedPreferences("safi_shaarawy", Context.MODE_PRIVATE)
    var saved: List<Link>
        get() = runCatching {
            val a = JSONArray(sp().getString("links", "[]"))
            (0 until a.length()).map { a.getJSONObject(it).let { o -> Link(o.getString("t"), o.getString("u")) } }
        }.getOrDefault(emptyList())
        set(v) = sp().edit { putString("links", JSONArray(v.map { JSONObject().put("t", it.title).put("u", it.url) }).toString()) }
}
