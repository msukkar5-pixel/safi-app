package com.mohamed.safi.social

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.util.Base64
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import androidx.core.content.edit
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mohamed.safi.R
import com.mohamed.safi.SafiApp
import com.mohamed.safi.data.zone
import com.mohamed.safi.faith.Deen
import com.mohamed.safi.faith.Hadiths
import com.mohamed.safi.faith.Quran
import com.mohamed.safi.faith.Wird
import com.mohamed.safi.notify.Notifier
import com.mohamed.safi.ui.ShareCard
import com.mohamed.safi.ui.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.security.SecureRandom
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * The daily post: a verse, a hadith from Sahih al-Bukhari / Muslim, or a supplication from Hisn al-Muslim — all taken
 * from the app's bundled, verified texts (nothing written here) — as an image card plus a caption.
 *
 * - Fully automatic, through each platform's official API, with the user's own accounts:
 *   a Telegram channel (bot token), a Facebook Page (page token), X (the user's developer keys).
 * - Everything else (Instagram, WhatsApp status, personal Facebook profiles, TikTok…) has no official way to post for
 *   a person from an app, so a notification brings the ready post and opens it in that app with one tap.
 * Secrets are stored under "key_*" names, which the backup strips. Each attempt is written to a small log.
 */
object Social {
    private fun sp() = SafiApp.instance.getSharedPreferences("safi_social", Context.MODE_PRIVATE)

    var on: Boolean get() = sp().getBoolean("on", false); set(v) = sp().edit { putBoolean("on", v) }
    var hour: Int get() = sp().getInt("hour", 7); set(v) = sp().edit { putInt("hour", v) }
    var minute: Int get() = sp().getInt("minute", 0); set(v) = sp().edit { putInt("minute", v) }
    /** Hours between posts: 1, 2, 3, 6 or 24 (once a day at [hour]). */
    var every: Int get() = sp().getInt("every", 1); set(v) = sp().edit { putInt("every", v) }
    val everyOptions = listOf(1 to "كل ساعة", 2 to "كل ساعتين", 3 to "كل ٣ ساعات", 6 to "كل ٦ ساعات", 24 to "مرة في اليوم")
    /** Skip posting between midnight and 6 am. */
    var quietNight: Boolean get() = sp().getBoolean("quiet", false); set(v) = sp().edit { putBoolean("quiet", v) }

    /** Which post of the series this moment is: changes every [every] hours, so each post is different. */
    fun slot(at: LocalDateTime = LocalDateTime.now(zone)): Long =
        if (every >= 24) at.toLocalDate().toEpochDay() else at.atZone(zone).toEpochSecond() / 3600 / every
    val allTypes = listOf("ayah" to "آية", "hadith" to "حديث", "dua" to "دعاء", "wird" to "تذكير بالورد")
    var types: Set<String> get() = sp().getStringSet("types", setOf("ayah", "hadith", "dua")) ?: setOf("ayah"); set(v) = sp().edit { putStringSet("types", v) }
    var signature: String get() = sp().getString("sig", "#أثر").orEmpty(); set(v) = sp().edit { putString("sig", v.take(120)) }

    // accounts (secrets are key_* so backups never carry them)
    var tgToken: String get() = sp().getString("key_tgtoken", "").orEmpty(); set(v) = sp().edit { putString("key_tgtoken", v.trim()) }
    var tgChat: String get() = sp().getString("tg_chat", "").orEmpty(); set(v) = sp().edit { putString("tg_chat", v.trim()) }
    var fbPage: String get() = sp().getString("fb_page", "").orEmpty(); set(v) = sp().edit { putString("fb_page", v.trim()) }
    var fbToken: String get() = sp().getString("key_fbtoken", "").orEmpty(); set(v) = sp().edit { putString("key_fbtoken", v.trim()) }
    var xKeys: List<String>
        get() = listOf("key_xck", "key_xcs", "key_xat", "key_xas").map { sp().getString(it, "").orEmpty() }
        set(v) = sp().edit { listOf("key_xck", "key_xcs", "key_xat", "key_xas").forEachIndexed { i, k -> putString(k, v.getOrElse(i) { "" }.trim()) } }
    /** Optional Meta (Facebook) App ID: lets Instagram/Facebook open their story editor directly. Not a secret. */
    var metaAppId: String get() = sp().getString("meta_app", "").orEmpty(); set(v) = sp().edit { putString("meta_app", v.trim()) }
    val tgReady get() = tgToken.isNotBlank() && tgChat.isNotBlank()
    val fbReady get() = fbToken.isNotBlank() && fbPage.isNotBlank()
    val xReady get() = xKeys.all { it.isNotBlank() }

    // ------------------------------------------------------------------ today's post
    data class Post(val type: String, val title: String, val text: String, val source: String) {
        fun caption(sig: String) = buildString {
            append(title).append("\n\n").append(text)
            if (source.isNotBlank()) append("\n\n— ").append(source)
            if (sig.isNotBlank()) append("\n\n").append(sig)
        }
    }

    suspend fun today(ctx: Context = SafiApp.instance, seed: Long = slot()): Post? = withContext(Dispatchers.IO) {
        val enabled = allTypes.map { it.first }.filter { it in types }.ifEmpty { listOf("ayah") }
        val order = enabled.indices.map { enabled[((seed + it) % enabled.size).toInt()] }
        for (t in order) runCatching { build(ctx, t, seed) }.getOrNull()?.let { return@withContext it }
        null
    }

    private suspend fun build(ctx: Context, type: String, seed: Long): Post? = when (type) {
        "ayah" -> {
            val pool = Quran.surahs(ctx).flatMap { s -> s.ayahs.filter { it.text.length in 60..220 }.map { s to it } }
            val (s, a) = pool[(seed * 31 % pool.size).toInt()]
            Post(type, "📖 آية اليوم", "﴿ ${a.text} ﴾", "${s.name} • ${a.n}")
        }
        "hadith" -> {
            val book = if ((seed / 3) % 2 == 0L) "bukhari" else "muslim"
            val b = Hadiths.load(book, ctx)
            // only sayings of the Prophet ﷺ (not a Companion's words or a chain-only report)
            val pool = b.hadiths.filter { it.text.length in 80..420 && ("قال رسول الله صلى الله عليه وسلم" in it.plain || "قال النبي صلى الله عليه وسلم" in it.plain) }
                .ifEmpty { b.hadiths.filter { it.text.length in 80..420 && "صلى الله عليه وسلم" in it.plain } }
            val h = pool[(seed * 17 % pool.size).toInt()]
            Post(type, "🌿 حديث اليوم", h.text, "${Hadiths.bookTitle(book)} (${h.number})")
        }
        "dua" -> {
            val hisn = Deen.hisn(ctx)
            val pool = hisn.chapters.flatMap { c -> c.items.filter { it.x.length in 30..300 }.map { c to it } }
            val (c, i) = pool[(seed * 13 % pool.size).toInt()]
            Post(type, "🤲 من حصن المسلم", i.x, listOf(c.title, i.ref).filter { it.isNotBlank() }.joinToString(" • "))
        }
        "wird" -> Post(type, "📖 تذكير بالورد", "${tr("وردي النهارده")}: ${Wird.pagesPerDay} ${tr("صفحات من المصحف")}. ${tr("شاركني وخلّينا نختم سوا")} 🤍", "")
        else -> null
    }

    fun card(ctx: Context, p: Post): File? = ShareCard.render(ctx, p.title, p.text, p.source, signature, name = "safi_post")

    // ------------------------------------------------------------------ posting
    private val http by lazy { OkHttpClient.Builder().callTimeout(60, TimeUnit.SECONDS).build() }

    /** Posts to every linked account. Returns one line per platform. */
    suspend fun postAll(ctx: Context, p: Post): List<String> = withContext(Dispatchers.IO) {
        val img = card(ctx, p)
        val cap = p.caption(signature)
        val out = mutableListOf<String>()
        if (tgReady) out += result("Telegram") { telegram(cap, img) }
        if (fbReady) out += result("Facebook") { facebook(cap, img) }
        if (xReady) out += result("X") { xPost(cap) }
        out
    }

    private inline fun result(name: String, f: () -> Unit): String {
        val r = runCatching { f() }
        val line = if (r.isSuccess) "✓ $name" else "✗ $name: ${r.exceptionOrNull()?.message?.take(140)}"
        log(line)
        return line
    }

    private fun ok(resp: okhttp3.Response) = resp.use { if (!it.isSuccessful) error("HTTP ${it.code} ${it.body?.string()?.take(160).orEmpty()}") }

    private fun telegram(caption: String, img: File?) {
        val base = "https://api.telegram.org/bot$tgToken"
        if (img != null && caption.length <= 1024) {
            val body = MultipartBody.Builder().setType(MultipartBody.FORM).addFormDataPart("chat_id", tgChat).addFormDataPart("caption", caption)
                .addFormDataPart("photo", img.name, img.asRequestBody("image/png".toMediaType())).build()
            ok(http.newCall(Request.Builder().url("$base/sendPhoto").post(body).build()).execute())
        } else {
            if (img != null) {
                val body = MultipartBody.Builder().setType(MultipartBody.FORM).addFormDataPart("chat_id", tgChat)
                    .addFormDataPart("photo", img.name, img.asRequestBody("image/png".toMediaType())).build()
                ok(http.newCall(Request.Builder().url("$base/sendPhoto").post(body).build()).execute())
            }
            val j = JSONObject().put("chat_id", tgChat).put("text", caption.take(4096))
            ok(http.newCall(Request.Builder().url("$base/sendMessage").post(j.toString().toRequestBody("application/json".toMediaType())).build()).execute())
        }
    }

    /** A Facebook Page (Graph API). Personal profiles can't be posted to by apps. */
    private fun facebook(caption: String, img: File?) {
        val b = MultipartBody.Builder().setType(MultipartBody.FORM).addFormDataPart("access_token", fbToken)
        val url = if (img != null) {
            b.addFormDataPart("caption", caption).addFormDataPart("source", img.name, img.asRequestBody("image/png".toMediaType()))
            "https://graph.facebook.com/$fbPage/photos"
        } else { b.addFormDataPart("message", caption); "https://graph.facebook.com/$fbPage/feed" }
        ok(http.newCall(Request.Builder().url(url).post(b.build()).build()).execute())
    }

    /** X (API v2, OAuth 1.0a user keys). Text only; posts longer than 280 characters are skipped, never cut. */
    private fun xPost(caption: String) {
        val text = if (caption.length <= 280) caption else caption.substringBeforeLast("\n\n").takeIf { it.length <= 280 } ?: error(tr("المنشور أطول من ٢٨٠ حرف"))
        val url = "https://api.twitter.com/2/tweets"
        val (ck, cs, at, ats) = xKeys
        val req = Request.Builder().url(url).header("Authorization", oauth1("POST", url, ck, cs, at, ats))
            .post(JSONObject().put("text", text).toString().toRequestBody("application/json".toMediaType())).build()
        ok(http.newCall(req).execute())
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20").replace("*", "%2A").replace("%7E", "~")

    private fun oauth1(method: String, url: String, ck: String, cs: String, token: String, ts: String): String {
        val nonce = ByteArray(16).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(java.util.Locale.US, it) }
        val p = sortedMapOf(
            "oauth_consumer_key" to ck, "oauth_nonce" to nonce, "oauth_signature_method" to "HMAC-SHA1",
            "oauth_timestamp" to (System.currentTimeMillis() / 1000).toString(), "oauth_token" to token, "oauth_version" to "1.0",
        )
        val base = method + "&" + enc(url) + "&" + enc(p.entries.joinToString("&") { "${enc(it.key)}=${enc(it.value)}" })
        val mac = Mac.getInstance("HmacSHA1").apply { init(SecretKeySpec("${enc(cs)}&${enc(ts)}".toByteArray(), "HmacSHA1")) }
        p["oauth_signature"] = Base64.encodeToString(mac.doFinal(base.toByteArray()), Base64.NO_WRAP)
        return "OAuth " + p.entries.joinToString(", ") { "${enc(it.key)}=\"${enc(it.value)}\"" }
    }

    // ------------------------------------------------------------------ one-tap share to any app
    /** The share intent for [pkg] (null = let the user choose). The caption is also put on the clipboard, since some apps ignore it. */
    fun shareIntent(ctx: Context, p: Post, pkg: String?): Intent? {
        val img = card(ctx, p) ?: return null
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", img)
        val send = Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri).putExtra(Intent.EXTRA_TEXT, p.caption(signature))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).apply { clipData = ClipData.newRawUri("", uri); if (pkg != null) setPackage(pkg) }
        return if (pkg == null) Intent.createChooser(send, tr("انشر المنشور")) else send
    }

    fun copyCaption(ctx: Context, p: Post) {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager ?: return
        cm.setPrimaryClip(ClipData.newPlainText("safi", p.caption(signature)))
    }

    // ------------------------------------------------------------------ log
    fun log(line: String) {
        val a = runCatching { JSONArray(sp().getString("log", "[]")) }.getOrDefault(JSONArray())
        val l = (0 until a.length()).map { a.getJSONObject(it) }.toMutableList()
        l.add(0, JSONObject().put("at", System.currentTimeMillis()).put("x", line))
        sp().edit { putString("log", JSONArray(l.take(40)).toString()) }
    }
    fun logLines(): List<Pair<Long, String>> = runCatching {
        val a = JSONArray(sp().getString("log", "[]"))
        (0 until a.length()).map { a.getJSONObject(it).let { o -> o.optLong("at") to o.optString("x") } }
    }.getOrDefault(emptyList())

    // ------------------------------------------------------------------ daily schedule
    private fun pi(ctx: Context) = PendingIntent.getBroadcast(ctx, 8_950_001, Intent(ctx, SocialReceiver::class.java).setAction("post"),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun schedule(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        am.cancel(pi(ctx))
        if (!on) return
        val now = LocalDateTime.now(zone)
        val at = if (every >= 24) {
            now.toLocalDate().atTime(hour, minute).let { if (it.isAfter(now)) it else it.plusDays(1) }
        } else {
            // the next full hour that fits the interval (e.g. every 3 hours: 0, 3, 6 …)
            var t = now.withMinute(0).withSecond(0).withNano(0).plusHours(1)
            while (t.hour % every != 0) t = t.plusHours(1)
            t
        }
        // inexact is fine for a daily post and needs no special permission
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.atZone(zone).toInstant().toEpochMilli(), pi(ctx))
    }

    /** Runs the daily job now (in the background, when there is internet). */
    fun runNow(ctx: Context) {
        WorkManager.getInstance(ctx).enqueue(
            OneTimeWorkRequestBuilder<SocialWorker>().setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build(),
        )
    }

    const val NOTIF_ID = 8_950_002
}

class SocialReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Social.runNow(context)
        Social.schedule(context)
    }
}

class SocialWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        if (Social.quietNight && java.time.LocalTime.now(zone).hour < 6) return Result.success()
        val p = Social.today(ctx) ?: return Result.retry()
        val results = Social.postAll(ctx, p)
        // with automatic accounts and frequent posts, only notify when something failed
        val linked = Social.tgReady || Social.fbReady || Social.xReady
        if (linked && Social.every < 24 && results.none { it.startsWith("✗") }) return Result.success()
        // a notification with the ready post: one tap to publish where there is no automatic posting
        val share = Social.shareIntent(ctx, p, null)
        val actions = buildList {
            if (share != null) add(NotificationCompat.Action(R.drawable.ic_notify, tr("انشر"),
                PendingIntent.getActivity(ctx, Social.NOTIF_ID, share, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)))
        }
        val sub = if (results.isEmpty()) tr("جاهز تنشره بضغطة") else results.joinToString("  ")
        Notifier.show(ctx, Social.NOTIF_ID, Notifier.CH_REMIND, "📣 ${tr("منشور النهارده")}: ${p.title}", sub, route = "social", actions = actions)
        return Result.success()
    }
}
