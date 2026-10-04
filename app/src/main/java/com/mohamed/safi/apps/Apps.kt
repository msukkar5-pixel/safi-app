package com.mohamed.safi.apps

import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import androidx.core.content.edit

data class InstalledApp(val label: String, val pkg: String)

/** Lets Safi open and drive other apps on the phone. */
object Apps {
    const val WAZE = "com.waze"
    const val GMAPS = "com.google.android.apps.maps"
    const val ANGHAMI = "com.anghami"
    const val SPOTIFY = "com.spotify.music"
    const val YTMUSIC = "com.google.android.apps.youtube.music"
    const val YOUTUBE = "com.google.android.youtube"
    const val WHATSAPP = "com.whatsapp"
    const val WHATSAPP_B = "com.whatsapp.w4b"

    private fun sp(ctx: Context) = ctx.getSharedPreferences("safi_apps", Context.MODE_PRIVATE)
    fun mapsApp(ctx: Context) = sp(ctx).getString("maps", WAZE) ?: WAZE
    fun setMapsApp(ctx: Context, pkg: String) = sp(ctx).edit { putString("maps", pkg) }
    fun musicApp(ctx: Context) = sp(ctx).getString("music", ANGHAMI) ?: ANGHAMI
    fun setMusicApp(ctx: Context, pkg: String) = sp(ctx).edit { putString("music", pkg) }
    fun ttsOn(ctx: Context) = sp(ctx).getBoolean("tts", false)
    fun setTts(ctx: Context, on: Boolean) = sp(ctx).edit { putBoolean("tts", on) }

    fun installed(ctx: Context, pkg: String): Boolean =
        runCatching { ctx.packageManager.getPackageInfo(pkg, 0); true }.getOrDefault(false)

    fun launchable(ctx: Context): List<InstalledApp> {
        val pm = ctx.packageManager
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        return pm.queryIntentActivities(i, 0)
            .map { InstalledApp(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
            .filter { it.pkg != ctx.packageName }
            .distinctBy { it.pkg }
            .sortedBy { it.label.lowercase() }
    }

    private fun start(ctx: Context, i: Intent): Boolean = try {
        ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }

    /** Opens an app by (Arabic or English) name. */
    fun open(ctx: Context, name: String): String? {
        val aliases = mapOf(
            "واتس" to WHATSAPP, "واتساب" to WHATSAPP, "whatsapp" to WHATSAPP,
            "ويز" to WAZE, "waze" to WAZE, "خرائط" to GMAPS, "maps" to GMAPS,
            "انغامي" to ANGHAMI, "أنغامي" to ANGHAMI, "anghami" to ANGHAMI,
            "سبوتيفاي" to SPOTIFY, "spotify" to SPOTIFY, "يوتيوب" to YOUTUBE, "youtube" to YOUTUBE,
            "سامسونج هيلث" to "com.sec.android.app.shealth", "samsung health" to "com.sec.android.app.shealth",
            "كاميرا" to "", "camera" to "",
        )
        val n = name.trim().lowercase()
        if (n in setOf("كاميرا", "camera", "الكاميرا")) {
            return if (start(ctx, Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))) "الكاميرا" else null
        }
        val pkg = aliases.entries.firstOrNull { n.contains(it.key) && it.value.isNotEmpty() }?.value
        val pm = ctx.packageManager
        if (pkg != null) pm.getLaunchIntentForPackage(pkg)?.let { if (start(ctx, it)) return name }
        val apps = launchable(ctx)
        val hit = apps.firstOrNull { it.label.lowercase() == n }
            ?: apps.firstOrNull { app ->
                val label = app.label.lowercase()
                label.contains(n) || (label.length >= 3 && n.contains(label))
            }
            ?: apps.firstOrNull { it.pkg.lowercase().contains(n.replace(" ", "")) }
            ?: return null
        val li = pm.getLaunchIntentForPackage(hit.pkg) ?: return null
        return if (start(ctx, li)) hit.label else null
    }

    /** Navigation with the chosen maps app (Waze by default). */
    fun navigate(ctx: Context, destination: String, appPref: String? = null): String {
        val q = Uri.encode(destination)
        val pkg = when (appPref?.lowercase()) {
            "waze", "ويز" -> WAZE
            "google", "maps", "gmaps", "جوجل" -> GMAPS
            else -> mapsApp(ctx)
        }
        if (pkg == WAZE && installed(ctx, WAZE)) {
            if (start(ctx, Intent(Intent.ACTION_VIEW, Uri.parse("https://waze.com/ul?q=$q&navigate=yes")).setPackage(WAZE))) return "Waze"
        }
        if (installed(ctx, GMAPS)) {
            if (start(ctx, Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=$q")).setPackage(GMAPS))) return "Google Maps"
        }
        start(ctx, Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$q")))
        return "الخرائط"
    }

    /** Plays a song/artist/playlist in the chosen music app (Anghami by default). Returns the app label, or null if nothing could be opened. */
    fun playMusic(ctx: Context, query: String, appPref: String? = null): String? {
        val pkg = when (appPref?.lowercase()) {
            "anghami", "انغامي", "أنغامي" -> ANGHAMI
            "spotify", "سبوتيفاي" -> SPOTIFY
            "youtube", "youtube music", "يوتيوب" -> YTMUSIC
            else -> musicApp(ctx)
        }
        // 1) Standard "play from search" (what Google Assistant uses)
        val play = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH)
            .putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
            .putExtra(SearchManager.QUERY, query)
            .setPackage(pkg)
        val canPlay = ctx.packageManager.queryIntentActivities(play, PackageManager.MATCH_DEFAULT_ONLY).isNotEmpty()
        if (canPlay && start(ctx, play)) return appLabel(pkg)
        // 2) Open the app's search page
        val q = Uri.encode(query)
        val url = when (pkg) {
            ANGHAMI -> "https://play.anghami.com/search/$q"
            SPOTIFY -> "spotify:search:$q"
            YTMUSIC -> "https://music.youtube.com/search?q=$q"
            else -> "https://www.youtube.com/results?search_query=$q"
        }
        if (start(ctx, Intent(Intent.ACTION_VIEW, Uri.parse(url)).setPackage(pkg))) return appLabel(pkg)
        return if (start(ctx, Intent(Intent.ACTION_VIEW, Uri.parse(url)))) appLabel(pkg) else null
    }

    fun appLabel(pkg: String) = when (pkg) {
        ANGHAMI -> "أنغامي"
        SPOTIFY -> "Spotify"
        YTMUSIC -> "YouTube Music"
        YOUTUBE -> "YouTube"
        WAZE -> "Waze"
        GMAPS -> "Google Maps"
        else -> pkg
    }

    fun dial(ctx: Context, number: String): Boolean =
        start(ctx, Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(number.filter { it.isDigit() || it == '+' }))))

    /** UAE numbers: 05xxxxxxxx → 9715xxxxxxxx; Egypt 01xxxxxxxxx → 201xxxxxxxxx. */
    fun intlNumber(n: String): String {
        val d = n.filter { it.isDigit() }
        return when {
            d.startsWith("00") -> d.drop(2)
            d.startsWith("05") && d.length == 10 -> "971" + d.drop(1)
            d.startsWith("01") && d.length == 11 -> "20" + d.drop(1)
            else -> d
        }
    }

    fun whatsapp(ctx: Context, number: String, text: String): Boolean {
        val url = if (number.isBlank()) "https://wa.me/?text=${Uri.encode(text)}"
        else "https://wa.me/${intlNumber(number)}?text=${Uri.encode(text)}"
        val i = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        if (installed(ctx, WHATSAPP) && start(ctx, Intent(i).setPackage(WHATSAPP))) return true
        if (installed(ctx, WHATSAPP_B) && start(ctx, Intent(i).setPackage(WHATSAPP_B))) return true
        return start(ctx, i)
    }

    fun webSearch(ctx: Context, q: String): Boolean =
        start(ctx, Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, q)) ||
            start(ctx, Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(q))))
}
