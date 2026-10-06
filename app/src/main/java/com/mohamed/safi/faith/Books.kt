package com.mohamed.safi.faith

import android.content.Context
import androidx.core.content.edit
import com.mohamed.safi.SafiApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

data class BSection(val vol: Int, val idx: Int, val title: String, val level: Int, val page: Int, val length: Int)
data class BVolume(val vol: Int, val first: String, val last: String, val sections: List<BSection>)

data class BookMeta(
    val id: String, val cat: String, val title: String, val author: String, val desc: String,
    val volumes: Int = 1, val chars: Long = 0, val size: Long = 0, val source: String = "",
)

/** One readable book: index.json + vNN.json, from assets or from downloaded files. */
class BookData(val id: String, private val open: (String) -> InputStream) {
    private val lock = Mutex()
    private var index: List<BVolume>? = null
    private val texts = LinkedHashMap<Int, List<String>>()

    suspend fun volumes(): List<BVolume> = lock.withLock {
        index?.let { return@withLock it }
        withContext(Dispatchers.IO) {
            val a = JSONArray(open("index.json").bufferedReader().use { it.readText() })
            val list = (0 until a.length()).map { i ->
                val o = a.getJSONObject(i)
                val v = o.getInt("v")
                val s = o.getJSONArray("s")
                BVolume(
                    v, o.optString("first"), o.optString("last"),
                    (0 until s.length()).map { k ->
                        val e = s.getJSONArray(k)
                        BSection(v, k, e.getString(0), e.optInt(1, 1), e.optInt(2, 0), e.optInt(3, 0))
                    },
                )
            }
            index = list
            list
        }
    }

    suspend fun text(vol: Int): List<String> = lock.withLock {
        texts[vol]?.let { return@withLock it }
        withContext(Dispatchers.IO) {
            val a = JSONArray(open(String.format(java.util.Locale.US, "v%02d.json", vol)).bufferedReader().use { it.readText() })
            val list = (0 until a.length()).map { a.getString(it) }
            if (texts.size >= 3) texts.remove(texts.keys.first())
            texts[vol] = list
            list
        }
    }

    data class Hit(val section: BSection, val snippet: String)

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

    // reading position & settings, per book
    private fun sp() = SafiApp.instance.getSharedPreferences(if (id == "bidaya") "safi_bidaya" else "safi_book_$id", Context.MODE_PRIVATE)
    var lastVol: Int get() = sp().getInt("vol", 0); set(v) = sp().edit { putInt("vol", v) }
    var lastIdx: Int get() = sp().getInt("idx", 0); set(v) = sp().edit { putInt("idx", v) }
    var marks: Set<String> get() = sp().getStringSet("marks", emptySet()) ?: emptySet(); set(v) = sp().edit { putStringSet("marks", v) }
}

object Books {
    const val BASE = "https://github.com/msukkar5-pixel/safi-app/releases/download/books/"

    val categories = linkedMapOf(
        "prophets" to "قصص الأنبياء",
        "seerah" to "السيرة النبوية",
        "sahaba" to "الصحابة والخلفاء",
        "history" to "التاريخ الإسلامي",
        "hajj" to "الحج والعمرة",
        "adhkar" to "الأذكار والرقية والطب النبوي",
        "egypt" to "تاريخ مصر",
        "uae" to "تاريخ الإمارات",
        "aqeedah" to "العقيدة",
        "tafsir" to "التفسير",
        "hadith" to "الحديث وشروحه",
        "fiqh" to "الفقه والسياسة الشرعية",
        "tazkiya" to "الرقائق وتزكية النفس",
        "family" to "الأسرة والتربية",
        "thought" to "فكر وخواطر إيمانية معاصرة",
        "adab" to "الأدب واللغة والحكمة",
        "kids" to "كتب للأطفال",
    )

    /** Categories shown inside their own sections (Stories, History, Hajj guide, Ruqyah guide); the library shows the rest. */
    val sectionCats = setOf("prophets", "seerah", "sahaba", "egypt", "uae", "hajj", "adhkar", "kids")

    val bidaya = BookMeta(
        "bidaya", "history", "البداية والنهاية", "الحافظ ابن كثير (ت ٧٧٤هـ)",
        "التاريخ من بدء الخلق إلى سنة ٧٦٨هـ، ثم الفتن وأشراط الساعة — ٢٠ مجلدًا كاملة",
        20, 11_500_000, 0, "طبعة دار هجر بتحقيق د. عبد الله التركي، من مشروع OpenITI (الشاملة ٤٤٤٥)، بدون حواشي المحقق",
    )

    private val http = OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS).readTimeout(120, TimeUnit.SECONDS).build()
    private val cache = mutableMapOf<String, BookData>()
    private var catalogCache: List<BookMeta>? = null

    var fontSize: Int
        get() = SafiApp.instance.getSharedPreferences("safi_bidaya", Context.MODE_PRIVATE).getInt("font", 19)
        set(v) = SafiApp.instance.getSharedPreferences("safi_bidaya", Context.MODE_PRIVATE).edit { putInt("font", v) }

    private fun root(ctx: Context = SafiApp.instance) = File(ctx.filesDir, "books")
    private fun dir(id: String) = File(root(), id)

    fun bidayaAvailable(ctx: Context = SafiApp.instance) =
        runCatching { ctx.assets.list("bidaya")?.contains("index.json") == true }.getOrDefault(false)

    fun isReady(id: String): Boolean = if (id == "bidaya") bidayaAvailable() else File(dir(id), "index.json").exists()

    fun data(id: String): BookData = synchronized(cache) {
        cache.getOrPut(id) {
            if (id == "bidaya") BookData(id) { SafiApp.instance.assets.open("bidaya/$it") }
            else BookData(id) { File(dir(id), it).inputStream() }
        }
    }

    private fun parse(a: JSONArray) = (0 until a.length()).map {
        val o = a.getJSONObject(it)
        BookMeta(
            o.getString("id"), o.optString("cat"), o.optString("title"), o.optString("author"), o.optString("desc"),
            o.optInt("volumes", 1), o.optLong("chars"), o.optLong("size"), o.optString("source"),
        )
    }

    /** Bundled catalog, refreshed from the books release when online. */
    fun catalog(ctx: Context = SafiApp.instance): List<BookMeta> {
        catalogCache?.let { return it }
        val f = File(root(ctx), "catalog.json")
        val list = runCatching { parse(JSONArray(f.readText())) }.getOrNull()
            ?: runCatching { parse(JSONArray(ctx.assets.open("books_catalog.json").bufferedReader().use { it.readText() })) }.getOrDefault(emptyList())
        val all = listOf(bidaya) + list.filter { it.id != "bidaya" }
        catalogCache = all
        return all
    }

    private val refreshLock = Mutex()
    @Volatile private var refreshed = false

    /** One catalog refresh per app run, shared by every book list on screen (they used to race and overwrite the file). */
    suspend fun refreshOnce(ctx: Context = SafiApp.instance): Boolean = refreshLock.withLock {
        if (refreshed) return@withLock false
        refreshCatalog(ctx).also { refreshed = true }
    }

    suspend fun refreshCatalog(ctx: Context = SafiApp.instance): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            http.newCall(Request.Builder().url(BASE + "catalog.json").build()).execute().use { r ->
                if (!r.isSuccessful) return@runCatching false
                val txt = r.body?.string() ?: return@runCatching false
                val parsed = parse(JSONArray(txt))
                if (parsed.isEmpty()) return@runCatching false
                root(ctx).mkdirs()
                // write then rename, so a reader never sees half a file
                val tmp = File(root(ctx), "catalog.json.tmp")
                tmp.writeText(txt)
                tmp.renameTo(File(root(ctx), "catalog.json"))
                catalogCache = null
                true
            }
        }.getOrDefault(false)
    }

    fun byCat(cat: String) = catalog().filter { it.cat == cat }
    fun meta(id: String) = catalog().firstOrNull { it.id == id }

    private val downloadLocks = mutableMapOf<String, Mutex>()

    /** Downloads and unpacks a book. onProgress gets 0..1. One download per book id at a time. */
    suspend fun download(id: String, onProgress: (Float) -> Unit) {
        val lock = synchronized(downloadLocks) { downloadLocks.getOrPut(id) { Mutex() } }
        lock.withLock { downloadLocked(id, onProgress) }
    }

    private suspend fun downloadLocked(id: String, onProgress: (Float) -> Unit): Unit = withContext(Dispatchers.IO) {
        val tmp = File(root(), "$id.part")
        root().mkdirs()
        try {
            http.newCall(Request.Builder().url("$BASE$id.zip").build()).execute().use { r ->
                if (!r.isSuccessful) throw IllegalStateException("التحميل فشل (${r.code})")
                val body = r.body ?: throw IllegalStateException("التحميل فشل")
                val total = body.contentLength().takeIf { it > 0 } ?: (meta(id)?.size ?: 0L)
                body.byteStream().use { input ->
                    tmp.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        var done = 0L
                        while (true) {
                            coroutineContext.ensureActive()
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            done += n
                            if (total > 0) onProgress((done.toFloat() / total).coerceAtMost(1f))
                        }
                    }
                }
            }
        } catch (t: Throwable) { tmp.delete(); throw t }
        val target = dir(id)
        val staging = File(root(), "$id.new")
        staging.deleteRecursively(); staging.mkdirs()
        ZipInputStream(tmp.inputStream().buffered()).use { z ->
            while (true) {
                coroutineContext.ensureActive()
                val e = z.nextEntry ?: break
                val name = File(e.name).name
                if (e.isDirectory || name.isBlank()) continue
                File(staging, name).outputStream().use { z.copyTo(it) }
            }
        }
        tmp.delete()
        if (!File(staging, "index.json").exists()) { staging.deleteRecursively(); throw IllegalStateException("الملف ناقص") }
        target.deleteRecursively()
        if (!staging.renameTo(target)) {
            val copied = runCatching { staging.copyRecursively(target, overwrite = true) }.getOrDefault(false)
            staging.deleteRecursively()
            if (!copied) { target.deleteRecursively(); throw IllegalStateException("مقدرتش أحفظ الكتاب") }
        }
        synchronized(cache) { cache.remove(id) }
    }

    fun delete(id: String) {
        if (id == "bidaya") return
        dir(id).deleteRecursively()
        synchronized(cache) { cache.remove(id) }
    }

    fun sizeText(bytes: Long): String = when {
        bytes <= 0 -> ""
        bytes < 1024 * 1024 -> "${bytes / 1024} ك.ب"
        else -> "%.1f م.ب".format(bytes / 1024f / 1024f)
    }
}
