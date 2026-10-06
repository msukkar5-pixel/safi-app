package com.mohamed.safi.audio

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
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
import com.google.common.util.concurrent.ListenableFuture
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
        "ara" to "عربي", "eng" to "English", "urd" to "اردو", "fra" to "Français", "ind" to "Indonesia",
        "tur" to "Türkçe", "msa" to "Melayu", "ben" to "বাংলা", "deu" to "Deutsch", "spa" to "Español", "" to "كل اللغات",
    )
    private val langQ = mapOf(
        "ara" to "(Arabic OR ara)", "eng" to "(English OR eng)", "fra" to "(French OR fre OR fra)",
        "deu" to "(German OR ger OR deu)", "spa" to "(Spanish OR spa)", "urd" to "(Urdu OR urd)",
        "ind" to "(Indonesian OR ind)", "tur" to "(Turkish OR tur)", "msa" to "(Malay OR msa OR may)", "ben" to "(Bengali OR ben)",
    )

    val suggestions = mapOf(
        "ara" to listOf("السيرة النبوية", "قصص الأنبياء", "الصحابة", "تفسير", "رياض الصالحين", "الأربعين النووية", "العقيدة", "الفقه", "الرقائق", "التاريخ الإسلامي"),
        "" to listOf("Seerah", "Prophets", "Sahaba", "Tafsir", "Hadith", "Riyad as-Salihin", "Aqeedah", "Fiqh", "Islamic history"),
    )

    // Islamic subjects only (in several languages)
    private const val ISLAMIC = "(islam OR islamic OR muslim OR muslims OR إسلام OR الإسلام OR إسلامي OR إسلامية OR الاسلام OR اسلامي OR سيرة OR السيرة OR النبوية OR الرسول OR الأنبياء OR الانبياء OR الصحابة OR حديث OR الحديث OR فقه OR الفقه OR تفسير OR التفسير OR عقيدة OR العقيدة OR seerah OR sirah OR hadith OR tafsir OR fiqh OR aqeedah OR sunnah OR muhammad OR prophets OR islami OR islamique OR islamisch)"

    /** Topics in every field of life. Each is a search clause; the blocklist below applies to all of them. */
    val topics = linkedMapOf(
        "islamic" to "إسلامية",
        "history" to "تاريخ وسير",
        "self" to "تطوير الذات",
        "family" to "الأسرة والتربية",
        "health" to "الصحة",
        "science" to "علوم ومعرفة",
        "kids" to "قصص أطفال",
        "language" to "لغة وأدب",
    )
    private val topicQ = mapOf(
        "history" to "(history OR تاريخ OR التاريخ OR biography OR سيرة OR سير OR حضارة OR civilization)",
        "self" to "(\"self help\" OR \"self-help\" OR \"personal development\" OR productivity OR success OR habits OR \"تطوير الذات\" OR \"تنمية بشرية\" OR النجاح OR العادات OR الإدارة OR management OR leadership OR قيادة)",
        "family" to "(parenting OR family OR marriage OR تربية OR الأسرة OR الأبناء OR الزواج OR الطفل)",
        "health" to "(health OR nutrition OR medicine OR الصحة OR التغذية OR الطب OR طب OR صحة)",
        "science" to "(science OR astronomy OR physics OR chemistry OR geography OR علوم OR العلوم OR الفلك OR الجغرافيا OR الفيزياء OR الكيمياء)",
        "kids" to "(children OR \"children's stories\" OR \"قصص أطفال\" OR \"قصص الأطفال\" OR \"قصص للأطفال\" OR kids)",
        "language" to "(grammar OR \"arabic language\" OR النحو OR اللغة OR البلاغة OR الأدب OR شعر OR poetry OR خطابة)",
    )

    /**
     * Never shown, whatever the topic or search: unbelief and attacks on Islam, other religions' scripture and missionary
     * material, sects outside Ahl al-Sunnah, magic and the occult, romance/erotica and music.
     */
    private const val BLOCK = "(atheism OR atheist OR atheists OR agnostic OR secularism OR إلحاد OR الإلحاد OR ملحد OR الملحدين OR علمانية OR \"god delusion\" OR nietzsche OR darwin OR evolution OR التطور OR bible OR christian OR christianity OR gospel OR jesus OR church OR catholic OR missionary OR تبشير OR الإنجيل OR المسيحية OR torah OR judaism OR hindu OR buddhism OR buddhist OR shia OR shiite OR شيعة OR الشيعة OR شيعي OR اثنى OR ahmadiyya OR قاديانية OR bahai OR بهائية OR magic OR witchcraft OR occult OR astrology OR tarot OR سحر OR شعوذة OR أبراج OR romance OR erotic OR erotica OR sex OR love OR غرام OR رومانسية OR جنس OR music OR موسيقى OR song OR songs OR horror OR رعب OR vampire)"

    private fun esc(s: String) = s.replace(Regex("[\\\\\"():^~*?+\\-!{}\\[\\]/]"), " ").trim()

    /** Islamic audiobooks and lectures-as-books from the Internet Archive (incl. LibriVox). */
    suspend fun search(source: String, text: String, lang: String, page: Int, topic: String = "islamic"): Pair<List<AudioBook>, Int> = withContext(Dispatchers.IO) {
        val parts = mutableListOf<String>()
        parts += "mediatype:audio"
        parts += "NOT collection:(etree OR georgeblood OR 78rpm OR audio_music OR opensource_audio_music OR podcasts)"
        val tq = topicQ[topic]
        parts += if (tq == null) "(subject:$ISLAMIC OR title:$ISLAMIC)" else "(subject:$tq OR title:$tq)"
        parts += "NOT subject:$BLOCK"
        parts += "NOT title:$BLOCK"
        // books, not recitations / nasheed / music
        if (topic != "kids") parts += "NOT subject:(fiction OR novel OR novels OR رواية OR روايات)"
        parts += "NOT title:(bible OR gospel OR gibran OR famine)"
        parts += "NOT subject:(تلاوة OR تلاوات OR مرتل OR مجود OR recitation OR qiraat OR nasheed OR نشيد OR اناشيد OR أناشيد OR music OR موسيقى OR song OR songs)"
        langQ[lang]?.let { parts += "language:$it" }
        val t = esc(text)
        if (t.isNotBlank()) parts += "(title:($t) OR creator:($t) OR subject:($t))"
        parts += "(subject:(audiobook OR audiobooks OR book OR books OR كتاب OR كتب OR \"كتاب مسموع\" OR \"كتب مسموعة\" OR \"كتاب صوتي\" OR librivox OR lecture OR lectures OR دروس OR شرح OR سلسلة) OR title:(كتاب OR كتب OR شرح OR سلسلة OR book OR audiobook) OR collection:librivoxaudio)"
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
            val all = (0 until files.length()).map { files.getJSONObject(it) }.filter { f -> listOf(".mp3", ".ogg", ".m4a", ".opus", ".flac", ".wav", ".aac").any { f.optString("name").endsWith(it, true) } }
            // prefer one quality per chapter: 64kb > VBR > 128kb > anything
            val preferred = listOf("64Kbps MP3", "VBR MP3", "128Kbps MP3", "MP3", "Ogg Vorbis")
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

    /** Wall-clock millis at which the sleep timer pauses playback; 0 = off. Enforced by [PlaybackService]. */
    var sleepAt: Long get() = sp().getLong("sleep_at", 0L); set(v) = sp().edit { putLong("sleep_at", v) }
}

/** Plays in the background with lock-screen / notification controls. */
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private val handler = Handler(Looper.getMainLooper())
    private var lastSave = 0L

    /** Audiobook items use mediaId "bookId#index"; Quran ("quran#...") and other items ("deen#...", "radio#...") are skipped. */
    private fun saveProgress(p: androidx.media3.common.Player) {
        val id = p.currentMediaItem?.mediaId ?: return
        if (id.startsWith("quran#") || id.startsWith("deen#") || id.startsWith("radio#")) return
        val cut = id.lastIndexOf('#')
        if (cut <= 0) return
        val index = id.substring(cut + 1).toIntOrNull() ?: return
        Library.saveProgress(id.substring(0, cut), index, p.currentPosition.coerceAtLeast(0L))
        lastSave = SystemClock.elapsedRealtime()
    }

    private val tick = object : Runnable {
        override fun run() {
            val p = session?.player ?: return
            val sleep = Library.sleepAt
            if (sleep > 0 && System.currentTimeMillis() >= sleep) {
                Library.sleepAt = 0L
                p.pause() // onIsPlayingChanged(false) saves progress and stops the ticker
                return
            }
            if (p.isPlaying) {
                if (SystemClock.elapsedRealtime() - lastSave >= 15_000L) saveProgress(p)
                handler.postDelayed(this, 5_000L)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).build(), true)
            .setHandleAudioBecomingNoisy(true)
            .build()
        player.addListener(object : androidx.media3.common.Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                handler.removeCallbacks(tick)
                if (isPlaying) {
                    // a timer that expired while paused is stale: don't stop the new session immediately
                    val sleep = Library.sleepAt
                    if (sleep > 0 && System.currentTimeMillis() >= sleep) Library.sleepAt = 0L
                    lastSave = SystemClock.elapsedRealtime()
                    handler.postDelayed(tick, 5_000L)
                } else saveProgress(player)
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) { saveProgress(player) }
        })
        session = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: android.content.Intent?) {
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        session?.let { saveProgress(it.player) }
        session?.run { player.release(); release() }
        session = null
        super.onDestroy()
    }
}

object Player {
    /** Release with [MediaController.releaseFuture] when done. */
    fun connect(ctx: Context, onReady: (MediaController) -> Unit): ListenableFuture<MediaController> {
        val token = SessionToken(ctx, ComponentName(ctx, PlaybackService::class.java))
        val f = MediaController.Builder(ctx, token).buildAsync()
        f.addListener({ if (!f.isCancelled) runCatching { onReady(f.get()) } }, ContextCompat.getMainExecutor(ctx))
        return f
    }

    fun loadQuran(c: MediaController, reciter: String, m: com.mohamed.safi.faith.Moshaf, names: Map<Int, String>, startSurah: Int) {
        val items = m.surahs.sorted().map { n ->
            MediaItem.Builder().setUri(m.url(n)).setMediaId("quran#${m.id}#$n")
                .setMediaMetadata(
                    MediaMetadata.Builder().setTitle("سورة " + (names[n] ?: n.toString())).setArtist(reciter).setAlbumTitle(m.name).build(),
                ).build()
        }
        val idx = m.surahs.sorted().indexOf(startSurah).coerceAtLeast(0)
        c.setMediaItems(items, idx, 0L)
        c.setPlaybackSpeed(1f)
        c.repeatMode = androidx.media3.common.Player.REPEAT_MODE_OFF
        c.prepare()
        c.play()
    }

    /** One recording (a Hisn al-Muslim chapter, a dua…); mediaId should start with "deen#". */
    fun loadSingle(c: MediaController, mediaId: String, url: String, title: String, album: String) {
        val item = MediaItem.Builder().setUri(url).setMediaId(mediaId)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(title).setArtist(album).setAlbumTitle(album).build())
            .build()
        c.setMediaItem(item)
        c.setPlaybackSpeed(1f)
        c.repeatMode = androidx.media3.common.Player.REPEAT_MODE_OFF
        c.prepare()
        c.play()
    }

    /** A short playlist of (mediaId, url, title) — sleep duas, ruqyah surahs, calm recitation. */
    fun loadList(c: MediaController, items: List<Triple<String, String, String>>, artist: String, album: String) {
        if (items.isEmpty()) return
        c.setMediaItems(items.map { (id, url, title) ->
            MediaItem.Builder().setUri(url).setMediaId(id)
                .setMediaMetadata(MediaMetadata.Builder().setTitle(title).setArtist(artist).setAlbumTitle(album).build())
                .build()
        }, 0, 0L)
        c.setPlaybackSpeed(1f)
        c.repeatMode = androidx.media3.common.Player.REPEAT_MODE_OFF
        c.prepare()
        c.play()
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
        c.repeatMode = androidx.media3.common.Player.REPEAT_MODE_OFF
        c.prepare()
        c.play()
    }
}
