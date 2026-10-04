package com.mohamed.safi.audio

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionToken
import com.mohamed.safi.SafiApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class AudioBook(val id: String, val title: String, val author: String, val downloads: Int = 0) {
    val cover get() = "https://archive.org/services/img/$id"
}

data class Track(val title: String, val url: String, val seconds: Double)

/**
 * Free & legal audiobooks: LibriVox (public domain, 20k+ books) and Internet Archive items
 * that carry a public-domain or Creative Commons licence.
 */
object Library {
    private val http = OkHttpClient.Builder().readTimeout(40, TimeUnit.SECONDS).build()

    val languages = linkedMapOf(
        "" to "كل اللغات", "ara" to "عربي", "eng" to "English", "fra" to "Français",
        "deu" to "Deutsch", "spa" to "Español", "ita" to "Italiano", "urd" to "اردو",
    )
    private val langQ = mapOf(
        "ara" to "(Arabic OR ara)", "eng" to "(English OR eng)", "fra" to "(French OR fre OR fra)",
        "deu" to "(German OR ger OR deu)", "spa" to "(Spanish OR spa)", "ita" to "(Italian OR ita)", "urd" to "(Urdu OR urd)",
    )

    val arabicSuggestions = listOf("كتاب مسموع", "رواية", "قصص", "السيرة النبوية", "رياض الصالحين", "تفسير", "شعر", "تاريخ", "كليلة ودمنة", "الأدب")

    private fun esc(s: String) = s.replace(Regex("[\\\\\"():^~*?+\\-!{}\\[\\]/]"), " ").trim()

    /**
     * source = "librivox": public-domain LibriVox recordings.
     * source = "archive": any Internet Archive audio with a public-domain / CC licence (good for Arabic).
     */
    suspend fun search(source: String, text: String, lang: String, page: Int): Pair<List<AudioBook>, Int> = withContext(Dispatchers.IO) {
        val parts = mutableListOf<String>()
        if (source == "librivox") parts += "collection:librivoxaudio"
        else {
            parts += "mediatype:audio"
            parts += "(licenseurl:*publicdomain* OR licenseurl:*creativecommons* OR collection:librivoxaudio)"
            parts += "NOT collection:(etree OR georgeblood OR 78rpm OR audio_music OR opensource_audio_music)"
        }
        langQ[lang]?.let { parts += "language:$it" }
        val t = esc(text)
        if (t.isNotBlank()) parts += "(title:($t) OR creator:($t) OR subject:($t))"
        if (source != "librivox") {
            // real audiobooks only: no Quran recitations, songs or random clips
            parts += "(subject:(audiobook OR audiobooks OR \"كتاب مسموع\" OR \"كتب مسموعة\" OR \"كتاب صوتي\" OR \"كتب صوتية\" OR librivox) OR title:(\"كتاب مسموع\" OR \"كتاب صوتي\" OR audiobook) OR collection:librivoxaudio)"
            parts += "NOT subject:(quran OR قرآن OR تلاوة OR نشيد OR اناشيد OR music OR موسيقى)"
        }
        val url = "https://archive.org/advancedsearch.php".toHttpUrl().newBuilder()
            .addQueryParameter("q", parts.joinToString(" AND "))
            .addQueryParameter("fl[]", "identifier").addQueryParameter("fl[]", "title")
            .addQueryParameter("fl[]", "creator").addQueryParameter("fl[]", "downloads")
            .addQueryParameter("sort[]", "downloads desc")
            .addQueryParameter("rows", "40").addQueryParameter("page", page.toString())
            .addQueryParameter("output", "json").build()
        http.newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (!r.isSuccessful) throw IllegalStateException("البحث فشل (${r.code})")
            val res = JSONObject(r.body?.string() ?: "{}").getJSONObject("response")
            val docs = res.getJSONArray("docs")
            fun str(o: JSONObject, k: String): String = when (val v = o.opt(k)) {
                is JSONArray -> (0 until v.length()).joinToString("، ") { v.optString(it) }
                null -> ""
                else -> v.toString()
            }
            val list = (0 until docs.length()).map { i ->
                val d = docs.getJSONObject(i)
                AudioBook(d.getString("identifier"), str(d, "title").ifBlank { d.getString("identifier") }, str(d, "creator"), d.optInt("downloads"))
            }
            list to res.optInt("numFound")
        }
    }

    /** MP3 chapters of a book, in order. */
    suspend fun tracks(id: String): List<Track> = withContext(Dispatchers.IO) {
        http.newCall(Request.Builder().url("https://archive.org/metadata/$id").build()).execute().use { r ->
            if (!r.isSuccessful) throw IllegalStateException("مقدرتش أفتح الكتاب (${r.code})")
            val j = JSONObject(r.body?.string() ?: "{}")
            val server = "https://archive.org/download/$id/"
            val files = j.optJSONArray("files") ?: JSONArray()
            val all = (0 until files.length()).map { files.getJSONObject(it) }.filter { it.optString("name").endsWith(".mp3", true) }
            // prefer one quality per chapter: 64kb > VBR > 128kb > anything
            val preferred = listOf("64Kbps MP3", "VBR MP3", "128Kbps MP3")
            val chosen = preferred.firstNotNullOfOrNull { fmt -> all.filter { it.optString("format") == fmt }.takeIf { it.isNotEmpty() } } ?: all
            chosen.sortedWith(compareBy({ it.optString("track").substringBefore('/').toIntOrNull() ?: Int.MAX_VALUE }, { it.optString("name") }))
                .map {
                    val name = it.optString("name")
                    Track(
                        it.optString("title").ifBlank { name.substringBeforeLast('.').replace('_', ' ') },
                        server + Uri.encode(name, "/"),
                        it.optString("length").let { l -> l.toDoubleOrNull() ?: l.split(":").fold(0.0) { acc, p -> acc * 60 + (p.toDoubleOrNull() ?: 0.0) } },
                    )
                }
        }
    }

    // ---------- my shelf: favourites + progress ----------
    private fun sp() = SafiApp.instance.getSharedPreferences("safi_books", Context.MODE_PRIVATE)

    var shelf: List<AudioBook>
        get() = runCatching {
            val a = JSONArray(sp().getString("shelf", "[]"))
            (0 until a.length()).map { a.getJSONObject(it).let { o -> AudioBook(o.getString("id"), o.getString("t"), o.optString("a")) } }
        }.getOrDefault(emptyList())
        set(v) = sp().edit { putString("shelf", JSONArray(v.map { JSONObject().put("id", it.id).put("t", it.title).put("a", it.author) }).toString()) }

    fun addToShelf(b: AudioBook) { if (shelf.none { it.id == b.id }) shelf = listOf(b) + shelf }
    fun removeFromShelf(id: String) { shelf = shelf.filter { it.id != id } }

    fun saveProgress(id: String, track: Int, posMs: Long) = sp().edit { putString("p_$id", "$track:$posMs"); putString("last", id) }
    fun progress(id: String): Pair<Int, Long>? = sp().getString("p_$id", null)?.split(":")?.let { (it[0].toIntOrNull() ?: 0) to (it.getOrNull(1)?.toLongOrNull() ?: 0L) }
    var speed: Float get() = sp().getFloat("speed", 1f); set(v) = sp().edit { putFloat("speed", v) }
}

/** Plays in the background with lock-screen / notification controls. */
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).build(), true)
            .setHandleAudioBecomingNoisy(true)
            .build()
        session = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: android.content.Intent?) {
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        session?.run { player.release(); release() }
        session = null
        super.onDestroy()
    }
}

object Player {
    fun connect(ctx: Context, onReady: (MediaController) -> Unit) {
        val token = SessionToken(ctx, ComponentName(ctx, PlaybackService::class.java))
        val f = MediaController.Builder(ctx, token).buildAsync()
        f.addListener({ runCatching { onReady(f.get()) } }, ContextCompat.getMainExecutor(ctx))
    }

    fun load(c: MediaController, book: AudioBook, tracks: List<Track>, startIndex: Int, startMs: Long) {
        val items = tracks.mapIndexed { i, t ->
            MediaItem.Builder().setUri(t.url).setMediaId("${book.id}#$i")
                .setMediaMetadata(
                    MediaMetadata.Builder().setTitle(t.title).setArtist(book.author).setAlbumTitle(book.title)
                        .setArtworkUri(Uri.parse(book.cover)).build(),
                ).build()
        }
        c.setMediaItems(items, startIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0)), startMs)
        c.setPlaybackSpeed(Library.speed)
        c.prepare()
        c.play()
    }
}
