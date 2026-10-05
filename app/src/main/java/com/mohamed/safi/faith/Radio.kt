package com.mohamed.safi.faith

import android.content.Context
import androidx.core.content.edit
import com.mohamed.safi.SafiApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

data class Station(val id: String, val name: String, val url: String, val group: String)

/**
 * Quran radios: official stations bundled in assets/media/radio.json (checked in CI by
 * tools/check_streams.py) plus every live radio from mp3quran.net (reciters, tafsir, adhkar, ruqyah).
 */
object Radio {
    private val http = OkHttpClient.Builder().readTimeout(30, TimeUnit.SECONDS).build()
    private fun cacheFile(ctx: Context) = File(ctx.filesDir, "radios.json")

    fun bundled(ctx: Context = SafiApp.instance): List<Station> = runCatching {
        val a = JSONArray(ctx.assets.open("media/radio.json").bufferedReader().use { it.readText() })
        (0 until a.length()).map { a.getJSONObject(it).let { o -> Station(o.getString("id"), o.getString("name"), o.getString("url"), o.optString("group", "رسمية")) } }
    }.getOrDefault(emptyList())

    private fun parse(txt: String): List<Station> {
        val a = JSONObject(txt).getJSONArray("radios")
        return (0 until a.length()).mapNotNull { i ->
            val o = a.getJSONObject(i)
            val url = o.optString("url").replace("http://", "https://")
            if (url.isBlank()) null else Station("mp3q_" + o.optInt("id"), o.optString("name").replace("*", "").trim(), url, groupOf(o.optString("name")))
        }
    }

    /** Sort mp3quran radios into readable groups by name. */
    private fun groupOf(name: String) = when {
        "تفسير" in name || "تدبر" in name -> "التفسير"
        "رقية" in name || "الرقية" in name -> "الرقية"
        "أذكار" in name || "اذكار" in name || "الصباح" in name || "المساء" in name -> "الأذكار"
        "ترجمة" in name || "مترجم" in name -> "ترجمات"
        "سيرة" in name || "السيرة" in name || "فتاوى" in name || "حديث" in name -> "سيرة وفتاوى"
        else -> "القرّاء"
    }

    /** mp3quran radios that CI found playing when this version was built (tools/radios.py). */
    private fun verified(ctx: Context): List<Station> = runCatching {
        val a = JSONArray(ctx.assets.open("media/radio_mp3quran.json").bufferedReader().use { it.readText() })
        (0 until a.length()).map { a.getJSONObject(it).let { o -> Station(o.getString("id"), o.getString("name"), o.getString("url"), o.optString("group", "القرّاء")) } }
    }.getOrDefault(emptyList())

    /** Stations that failed to play on this phone recently go to the end of the list. */
    fun markFailed(id: String) = sp().edit { putLong("fail_$id", System.currentTimeMillis()) }
    fun markOk(id: String) = sp().edit { remove("fail_$id") }
    fun failedRecently(id: String) = System.currentTimeMillis() - sp().getLong("fail_$id", 0L) < 24L * 3600 * 1000

    /** Official stations + the checked mp3quran list; falls back to the live API only when the build has no checked list. */
    suspend fun all(ctx: Context = SafiApp.instance, force: Boolean = false): List<Station> = withContext(Dispatchers.IO) {
        val checked = verified(ctx)
        if (checked.isNotEmpty()) return@withContext (bundled(ctx) + checked).sortedBy { if (failedRecently(it.id)) 1 else 0 }
        allLive(ctx, force)
    }

    private suspend fun allLive(ctx: Context, force: Boolean): List<Station> = withContext(Dispatchers.IO) {
        val f = cacheFile(ctx)
        val fresh = f.exists() && System.currentTimeMillis() - f.lastModified() < 24L * 3600 * 1000
        val net = if (fresh && !force) null else runCatching {
            http.newCall(Request.Builder().url("https://mp3quran.net/api/v3/radios?language=ar").build()).execute().use { r ->
                val t = r.body?.string() ?: error("empty")
                parse(t).also { if (it.isNotEmpty()) f.writeText(t) }
            }
        }.getOrNull()
        val remote = net ?: runCatching { parse(f.readText()) }.getOrDefault(emptyList())
        bundled(ctx) + remote.sortedBy { it.name }
    }

    private fun sp() = SafiApp.instance.getSharedPreferences("safi_radio", Context.MODE_PRIVATE)
    var favorites: Set<String>
        get() = sp().getStringSet("fav", emptySet()) ?: emptySet()
        set(v) = sp().edit { putStringSet("fav", v) }
    var last: String
        get() = sp().getString("last", "") ?: ""
        set(v) = sp().edit { putString("last", v) }
}
