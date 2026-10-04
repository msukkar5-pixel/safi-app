package com.mohamed.safi.faith

import android.content.Context
import androidx.core.content.edit
import com.mohamed.safi.SafiApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray

data class BSection(val vol: Int, val idx: Int, val title: String, val level: Int, val page: Int, val length: Int)
data class BVolume(val vol: Int, val first: String, val last: String, val sections: List<BSection>)

/**
 * البداية والنهاية — الحافظ ابن كثير (ت ٧٧٤هـ), full text in 20 volumes.
 * Text: Dar Hajr edition (ed. ʿAbd Allāh al-Turkī, 1997) via the OpenITI corpus (Shamela 4445),
 * with the editor's footnotes and introductions removed, so what remains is Ibn Kathir's own text.
 */
object Bidaya {
    const val CREDIT = "النص الكامل لكتاب «البداية والنهاية» للحافظ ابن كثير (ت ٧٧٤هـ) — طبعة دار هجر بتحقيق د. عبد الله التركي (٢٠ مجلدًا)، من مشروع OpenITI (المكتبة الشاملة ٤٤٤٥)، بدون حواشي المحقق."

    private val lock = Mutex()
    private var index: List<BVolume>? = null
    private val texts = mutableMapOf<Int, List<String>>()

    fun available(ctx: Context = SafiApp.instance) = runCatching { ctx.assets.list("bidaya")?.contains("index.json") == true }.getOrDefault(false)

    suspend fun volumes(ctx: Context = SafiApp.instance): List<BVolume> = lock.withLock {
        index?.let { return@withLock it }
        withContext(Dispatchers.IO) {
            val a = JSONArray(ctx.assets.open("bidaya/index.json").bufferedReader().use { it.readText() })
            val list = (0 until a.length()).map { i ->
                val o = a.getJSONObject(i)
                val v = o.getInt("v")
                val s = o.getJSONArray("s")
                BVolume(
                    v, o.getString("first"), o.getString("last"),
                    (0 until s.length()).map { k ->
                        val e = s.getJSONArray(k)
                        BSection(v, k, e.getString(0), e.getInt(1), e.getInt(2), e.getInt(3))
                    },
                )
            }
            index = list
            list
        }
    }

    suspend fun text(vol: Int, ctx: Context = SafiApp.instance): List<String> = lock.withLock {
        texts[vol]?.let { return@withLock it }
        withContext(Dispatchers.IO) {
            val a = JSONArray(ctx.assets.open("bidaya/v%02d.json".format(vol)).bufferedReader().use { it.readText() })
            val list = (0 until a.length()).map { a.getString(it) }
            // keep at most 3 volumes in memory
            if (texts.size >= 3) texts.remove(texts.keys.first())
            texts[vol] = list
            list
        }
    }

    data class Hit(val section: BSection, val snippet: String)

    /** Searches titles everywhere and text in all volumes (slow-ish; runs in background). */
    suspend fun search(q: String, limit: Int = 80): List<Hit> = withContext(Dispatchers.Default) {
        val qq = Quran.plain(q.trim())
        if (qq.length < 2) return@withContext emptyList()
        val out = mutableListOf<Hit>()
        for (v in volumes()) {
            val t = text(v.vol)
            for (s in v.sections) {
                val body = t.getOrNull(s.idx) ?: continue
                val inTitle = Quran.plain(s.title).contains(qq)
                val p = Quran.plain(body)
                val at = p.indexOf(qq)
                if (inTitle || at >= 0) {
                    val snip = if (at >= 0) p.substring((at - 60).coerceAtLeast(0), (at + 90).coerceAtMost(p.length)) else body.take(120)
                    out += Hit(s, "…" + snip.replace('\n', ' ') + "…")
                    if (out.size >= limit) return@withContext out
                }
            }
        }
        out
    }

    private fun sp() = SafiApp.instance.getSharedPreferences("safi_bidaya", Context.MODE_PRIVATE)
    var lastVol: Int get() = sp().getInt("vol", 0); set(v) = sp().edit { putInt("vol", v) }
    var lastIdx: Int get() = sp().getInt("idx", 0); set(v) = sp().edit { putInt("idx", v) }
    var fontSize: Int get() = sp().getInt("font", 19); set(v) = sp().edit { putInt("font", v) }
    var marks: Set<String> get() = sp().getStringSet("marks", emptySet()) ?: emptySet(); set(v) = sp().edit { putStringSet("marks", v) }
}
