package com.mohamed.safi.ui

import android.content.Context
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import com.mohamed.safi.SafiApp
import org.json.JSONObject

/**
 * App language. The UI is written in Arabic; other languages are applied at display time from
 * assets/i18n/<lang>.json — exact strings plus template patterns ("صرفت {0} في {1}").
 */
object I18n {
    val languages = linkedMapOf("ar" to "العربية", "en" to "English", "ur" to "اردو")
    private fun sp() = SafiApp.instance.getSharedPreferences("safi_i18n", Context.MODE_PRIVATE)

    val lang = mutableStateOf("ar")
    private var exact: Map<String, String> = emptyMap()
    private class Pat(val re: Regex, val anchor: String, val out: String)
    private var patterns: List<Pat> = emptyList()
    private val cache = object : LinkedHashMap<String, String>(512, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?) = size > 3000
    }

    fun init(ctx: Context) { set(ctx, sp().getString("lang", "ar") ?: "ar") }

    fun set(ctx: Context, code: String) {
        synchronized(cache) { cache.clear() }
        if (code == "ar") { exact = emptyMap(); patterns = emptyList() }
        else runCatching {
            val j = JSONObject(ctx.assets.open("i18n/$code.json").bufferedReader().use { it.readText() })
            val ex = HashMap<String, String>()
            val pats = ArrayList<Pat>()
            j.keys().forEach { k ->
                val v = j.optString(k)
                if (v.isBlank()) return@forEach
                if (Regex("\\{\\d+\\}").containsMatchIn(k)) {
                    val pieces = k.split(Regex("\\{\\d+\\}"))
                    val nums = Regex("\\{(\\d+)\\}").findAll(k).map { it.groupValues[1] }.toList()
                    if (pieces.all { it.isBlank() }) return@forEach
                    val arabicAnchor = pieces.any { pc -> pc.any { it in '\u0600'..'\u06FF' } }
                    // pieces like " " or "، " alone would split ordinary sentences: skip those
                    if (!arabicAnchor && pieces.none { pc -> pc.any { !it.isWhitespace() && it != '،' && it != ',' && it != '.' } }) return@forEach
                    val sb = StringBuilder("^")
                    pieces.forEachIndexed { i, p ->
                        sb.append(Regex.escape(p))
                        if (i < nums.size) sb.append("(?<g").append(nums[i]).append(">.+?)")
                    }
                    sb.append("$")
                    runCatching { pats += Pat(Regex(sb.toString(), RegexOption.DOT_MATCHES_ALL), pieces.maxBy { it.length }, v) }
                } else ex[k] = v
            }
            exact = ex
            // longer, more specific patterns first
            patterns = pats.sortedByDescending { it.anchor.length }
        }
        lang.value = code
        sp().edit().putString("lang", code).apply()
    }

    val isRtl get() = lang.value != "en"
    val direction get() = if (isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr

    fun tr(s: String, depth: Int = 0): String {
        if (lang.value == "ar" || s.isEmpty()) return s
        if (s.none { it in '؀'..'ۿ' }) return s
        synchronized(cache) { cache[s]?.let { return it } }
        val r = translate(s, depth)
        synchronized(cache) { cache[s] = r }
        return r
    }

    private fun translate(s: String, depth: Int): String {
        exact[s]?.let { return it }
        val t = s.trim()
        if (t != s) exact[t]?.let { return s.replace(t, it) }
        // long text is content (Quran, hadith, books): never pattern-translate it
        if (s.length > 160) return s
        if (depth < 3) for (p in patterns) {
            if (p.anchor.isNotEmpty() && !s.contains(p.anchor)) continue
            val m = p.re.matchEntire(s) ?: continue
            var out = p.out
            Regex("\\{(\\d+)\\}").findAll(p.out).map { it.groupValues[1] }.toSet().forEach { n ->
                val g = runCatching { m.groups["g$n"]?.value }.getOrNull() ?: ""
                out = out.replace("{$n}", tr(g, depth + 1))
            }
            return out
        }
        // multi-line text: translate line by line
        if (s.contains('\n') && depth < 3) return s.split('\n').joinToString("\n") { tr(it, depth + 1) }
        return s
    }
}

/** Translate for display (no-op in Arabic). */
fun tr(s: String): String = I18n.tr(s)

/** Same as Material's Text(String) but shows the string in the chosen app language. */
@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    style: TextStyle = LocalTextStyle.current,
) {
    @Suppress("UNUSED_VARIABLE") val l = I18n.lang.value // recompose on language change
    androidx.compose.material3.Text(
        text = I18n.tr(text), modifier = modifier, color = color, fontSize = fontSize, fontStyle = fontStyle,
        fontWeight = fontWeight, fontFamily = fontFamily, letterSpacing = letterSpacing, textDecoration = textDecoration,
        textAlign = textAlign, lineHeight = lineHeight, overflow = overflow, softWrap = softWrap, maxLines = maxLines,
        minLines = minLines, onTextLayout = onTextLayout, style = style,
    )
}
