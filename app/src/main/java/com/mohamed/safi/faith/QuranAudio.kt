package com.mohamed.safi.faith

import android.content.Context
import androidx.core.content.edit
import com.mohamed.safi.SafiApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit

data class Moshaf(val id: Int, val name: String, val server: String, val surahs: List<Int>, val rewayaId: Int) {
    /** "حفص عن عاصم - مرتل" → riwaya "حفص عن عاصم", style "مرتل" */
    val riwaya get() = name.substringBefore(" - ").trim()
    val style get() = name.substringAfter(" - ", "").trim()
    fun url(surah: Int) = server.trimEnd('/') + "/" + String.format(Locale.US, "%03d", surah) + ".mp3"
}

data class Reciter(val id: Int, val name: String, val moshaf: List<Moshaf>)

/** Full Quran audio: every reciter and riwaya on mp3quran.net (free to stream). */
object QuranAudio {
    private val http = OkHttpClient.Builder().readTimeout(40, TimeUnit.SECONDS).build()
    private var cache: List<Reciter>? = null
    private fun file(ctx: Context) = File(ctx.filesDir, "quran_reciters.json")

    private fun parse(txt: String): List<Reciter> {
        val a = JSONObject(txt).getJSONArray("reciters")
        return (0 until a.length()).map { i ->
            val r = a.getJSONObject(i)
            val ms = r.optJSONArray("moshaf")
            Reciter(
                r.getInt("id"), r.getString("name").trim(),
                (0 until (ms?.length() ?: 0)).map { k ->
                    val m = ms!!.getJSONObject(k)
                    Moshaf(
                        m.getInt("id"), m.optString("name").trim(), m.optString("server"),
                        m.optString("surah_list").split(',').mapNotNull { it.trim().toIntOrNull() }, m.optInt("rewaya_id"),
                    )
                }.filter { it.server.startsWith("http") && it.surahs.isNotEmpty() },
            )
        }.filter { it.moshaf.isNotEmpty() }.sortedBy { it.name }
    }

    suspend fun reciters(ctx: Context = SafiApp.instance, force: Boolean = false): List<Reciter> = withContext(Dispatchers.IO) {
        cache?.takeIf { !force }?.let { return@withContext it }
        val f = file(ctx)
        val fresh = f.exists() && System.currentTimeMillis() - f.lastModified() < 7L * 24 * 3600 * 1000
        if (!force && fresh) runCatching { parse(f.readText()) }.getOrNull()?.let { cache = it; return@withContext it }
        val net = runCatching {
            http.newCall(Request.Builder().url("https://www.mp3quran.net/api/v3/reciters?language=ar").build()).execute().use { r ->
                if (!r.isSuccessful) error("(${r.code})")
                val t = r.body?.string() ?: error("empty")
                val list = parse(t)
                if (list.isNotEmpty()) f.writeText(t)
                list
            }
        }
        val list = net.getOrNull()
            ?: runCatching { parse(f.readText()) }.getOrNull()
            ?: throw IllegalStateException("مقدرتش أجيب القرّاء، اتأكد من النت")
        cache = list
        list
    }

    private fun sp() = SafiApp.instance.getSharedPreferences("safi_quran_audio", Context.MODE_PRIVATE)
    var lastReciter: Int get() = sp().getInt("reciter", 0); set(v) = sp().edit { putInt("reciter", v) }
    var lastMoshaf: Int get() = sp().getInt("moshaf", 0); set(v) = sp().edit { putInt("moshaf", v) }
    var favorites: Set<String> get() = sp().getStringSet("fav", emptySet()) ?: emptySet(); set(v) = sp().edit { putStringSet("fav", v) }
}
