package com.mohamed.safi.faith

import android.content.Context
import androidx.core.content.edit
import com.mohamed.safi.SafiApp
import org.json.JSONArray
import org.json.JSONObject

/**
 * One content block of a guide (assets/deen/<id>.json, built by tools/deen/build.py).
 * t = p | h | steps | list | ayah | hadith | dua | note | warn | tip | diagram | qa | table | video | link | longtext
 */
data class GBlock(
    val t: String,
    val x: String = "",
    val src: String = "",
    val who: String = "",
    val title: String = "",
    /** dua: times to say it; ayah: unused */
    val n: Int = 0,
    /** ayah: surah and ayah number (0 when unknown) */
    val s: Int = 0,
    val a: Int = 0,
    /** diagram key */
    val k: String = "",
    /** qa question */
    val q: String = "",
    /** link target ("tool:tawaf", "azkar:morning", or an app route) */
    val route: String = "",
    val sub: String = "",
    val url: String = "",
    val items: List<String> = emptyList(),
    val head: List<String> = emptyList(),
    val rows: List<List<String>> = emptyList(),
) {
    /** All the text in the block, for search. */
    val plain: String by lazy { Quran.plain(listOf(x, title, q, sub, src, items.joinToString(" "), rows.joinToString(" ") { it.joinToString(" ") }).joinToString(" ")) }
}

data class GSection(val id: String, val title: String, val icon: String, val sub: String, val blocks: List<GBlock>)
data class Guide(val id: String, val title: String, val sub: String, val sections: List<GSection>)

data class DeenVideo(val title: String, val who: String, val url: String)
data class GuideMedia(val videos: List<DeenVideo>, val books: List<String>, val surahs: List<Int>)
/** A titled group of checklist lines (packing list, Hajj day plan). */
data class CheckGroup(val title: String, val items: List<String>)

data class HisnItem(val x: String, val n: Int, val ref: String)
data class HisnChapter(val i: Int, val title: String, val group: Int, val audio: String, val items: List<HisnItem>) {
    val plain: String by lazy { Quran.plain(title + " " + items.joinToString(" ") { it.x }) }
}
data class HisnGroup(val title: String, val icon: String)
data class Hisn(val title: String, val author: String, val note: String, val groups: List<HisnGroup>, val chapters: List<HisnChapter>)

object Deen {
    val guides = listOf("umrah", "hajj", "ruqyah")
    private val cache = mutableMapOf<String, Any>()

    private fun read(ctx: Context, name: String) = ctx.assets.open("deen/$name.json").bufferedReader().use { it.readText() }
    private fun JSONArray?.strings(): List<String> = if (this == null) emptyList() else (0 until length()).map { optString(it) }

    private fun block(o: JSONObject) = GBlock(
        t = o.optString("t"), x = o.optString("x"), src = o.optString("src"), who = o.optString("who"), title = o.optString("title"),
        n = o.optInt("n"), s = o.optInt("s"), a = o.optInt("a"), k = o.optString("k"), q = o.optString("q"),
        route = o.optString("route"), sub = o.optString("sub"), url = o.optString("url"),
        items = o.optJSONArray("items").strings(), head = o.optJSONArray("head").strings(),
        rows = o.optJSONArray("rows")?.let { r -> (0 until r.length()).map { r.optJSONArray(it).strings() } } ?: emptyList(),
    )

    fun guide(id: String, ctx: Context = SafiApp.instance): Guide = synchronized(cache) {
        cache.getOrPut("g_$id") {
            val j = JSONObject(read(ctx, id))
            val secs = j.getJSONArray("sections")
            Guide(
                j.optString("id", id), j.optString("title"), j.optString("sub"),
                (0 until secs.length()).map { i ->
                    val s = secs.getJSONObject(i)
                    val b = s.optJSONArray("blocks") ?: JSONArray()
                    GSection(s.optString("id"), s.optString("title"), s.optString("icon"), s.optString("sub"), (0 until b.length()).map { block(b.getJSONObject(it)) })
                },
            )
        } as Guide
    }

    private fun mediaJson(ctx: Context): JSONObject = synchronized(cache) { cache.getOrPut("media") { JSONObject(read(ctx, "media")) } as JSONObject }

    fun media(id: String, ctx: Context = SafiApp.instance): GuideMedia {
        val o = mediaJson(ctx).optJSONObject(id) ?: return GuideMedia(emptyList(), emptyList(), emptyList())
        val v = o.optJSONArray("videos") ?: JSONArray()
        val su = o.optJSONArray("surahs")
        return GuideMedia(
            (0 until v.length()).map { v.getJSONObject(it).let { x -> DeenVideo(x.optString("x"), x.optString("who"), x.optString("url")) } },
            o.optJSONArray("books").strings(),
            if (su == null) emptyList() else (0 until su.length()).map { su.optInt(it) },
        )
    }

    /** "checklist" (what to pack) or "hajjplan" (the Hajj days). */
    fun checklist(key: String, ctx: Context = SafiApp.instance): List<CheckGroup> {
        val a = mediaJson(ctx).optJSONArray(key) ?: return emptyList()
        return (0 until a.length()).mapNotNull { i ->
            val g = a.optJSONArray(i) ?: return@mapNotNull null
            CheckGroup(g.optString(0), g.optJSONArray(1).strings())
        }
    }

    fun hisn(ctx: Context = SafiApp.instance): Hisn = synchronized(cache) {
        cache.getOrPut("hisn") {
            val j = JSONObject(read(ctx, "hisn"))
            val g = j.getJSONArray("groups")
            val c = j.getJSONArray("chapters")
            Hisn(
                j.optString("title"), j.optString("author"), j.optString("note"),
                (0 until g.length()).map { g.getJSONObject(it).let { o -> HisnGroup(o.optString("t"), o.optString("icon")) } },
                (0 until c.length()).map { i ->
                    val o = c.getJSONObject(i)
                    val arr = o.optJSONArray("items") ?: JSONArray()
                    HisnChapter(
                        o.optInt("i", i + 1), o.optString("t"), o.optInt("g"), o.optString("audio"),
                        (0 until arr.length()).map { k -> arr.getJSONObject(k).let { z -> HisnItem(z.optString("x"), z.optInt("n", 1).coerceAtLeast(1), z.optString("ref")) } },
                    )
                },
            )
        } as Hisn
    }

    // ------------------------------------------------------------------ saved state

    private fun sp() = SafiApp.instance.getSharedPreferences("safi_deen", Context.MODE_PRIVATE)

    fun checked(key: String): Set<String> = sp().getStringSet("chk_$key", emptySet()) ?: emptySet()
    fun setChecked(key: String, v: Set<String>) = sp().edit { putStringSet("chk_$key", v) }

    /** Counter tools (tawaf, sai, rami) keep their count if the app is closed mid-way. */
    fun count(tool: String): Int = sp().getInt("cnt_$tool", 0)
    fun setCount(tool: String, v: Int) = sp().edit { putInt("cnt_$tool", v) }

    var hisnFav: Set<String>
        get() = sp().getStringSet("hisn_fav", emptySet()) ?: emptySet()
        set(v) = sp().edit { putStringSet("hisn_fav", v) }
    var hisnLast: Int
        get() = sp().getInt("hisn_last", 0)
        set(v) = sp().edit { putInt("hisn_last", v) }

    /** Last section opened in each guide, to offer "continue". */
    fun lastSection(guide: String): String = sp().getString("last_$guide", "") ?: ""
    fun setLastSection(guide: String, section: String) = sp().edit { putString("last_$guide", section) }
}
