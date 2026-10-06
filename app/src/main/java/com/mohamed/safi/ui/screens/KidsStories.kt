package com.mohamed.safi.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import com.mohamed.safi.ui.Text
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mohamed.safi.kids.Kid
import com.mohamed.safi.kids.Kids
import com.mohamed.safi.ui.*
import org.json.JSONObject
import androidx.compose.material3.Text as RawText

private val SBg = Color(0xFFFFF7E8)
private val SInk = Color(0xFF3B3125)
private val SGreen = Color(0xFF2E9D5B)

/** Short value stories (with a question at the end) and choose-your-path stories, from assets/kids/stories.json in ar/en/ur. */
/** (id, icon, title) of every kids story in the calendar order: value and choice stories mixed. */
fun kidStoryIds(ctx: android.content.Context): List<Triple<String, String, String>> {
    val all = KidStories.load(ctx)
    fun list(k: String) = all.optJSONArray(k)?.let { a -> (0 until a.length()).map { a.getJSONObject(it) } }.orEmpty()
        .map { Triple(it.optString("id"), it.optString("icon"), KidStories.t(it.optJSONObject("title"))) }
    val v = ArrayDeque(list("stories")); val c = ArrayDeque(list("choice"))
    val out = ArrayList<Triple<String, String, String>>()
    while (v.isNotEmpty() || c.isNotEmpty()) { repeat(3) { v.removeFirstOrNull()?.let { out += it } }; c.removeFirstOrNull()?.let { out += it } }
    return out
}

private object KidStories {
    private var data: JSONObject? = null
    fun load(ctx: android.content.Context): JSONObject =
        data ?: runCatching { JSONObject(ctx.assets.open("kids/stories.json").bufferedReader().use { it.readText() }) }.getOrDefault(JSONObject()).also { data = it }
    /** The text in the app language: Arabic, English or Urdu; other languages read the English. */
    fun t(o: JSONObject?): String {
        if (o == null) return ""
        val l = I18n.lang.value
        return o.optString(if (l == "ar" || l == "ur") l else "en").ifBlank { o.optString("ar") }
    }
}

@Composable
fun KidsStoriesScreen(kid: Kid, startId: String? = null, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val all = remember { KidStories.load(ctx) }
    fun find(k: String) = all.optJSONArray(k)?.let { a -> (0 until a.length()).map { a.getJSONObject(it) } }.orEmpty().firstOrNull { it.optString("id") == startId }
    var open by remember { mutableStateOf(startId?.let { find("stories") }) }
    var choice by remember { mutableStateOf(startId?.let { find("choice") }) }
    // opened straight from "today's story": closing the story goes back to the kids screen
    val direct = startId != null
    open?.let { s -> BackHandler { if (direct) onDone() else open = null }; ValueStory(kid, s) { if (direct) onDone() else open = null }; return }
    choice?.let { s -> BackHandler { if (direct) onDone() else choice = null }; ChoiceStory(kid, s) { if (direct) onDone() else choice = null }; return }
    BackHandler { onDone() }
    val stories = all.optJSONArray("stories")?.let { a -> (0 until a.length()).map { a.getJSONObject(it) } }.orEmpty()
    val choices = all.optJSONArray("choice")?.let { a -> (0 until a.length()).map { a.getJSONObject(it) } }.orEmpty()
    ScreenScaffold("قصص مدينة الخير", onBack = onDone) { pad ->
        LazyColumn(Modifier.fillMaxSize().background(SBg).padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Text("اختار إنت النهاية", fontFamily = Amiri, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = SGreen) }
            items(choices) { s -> StoryRow(s, true) { choice = s } }
            item { Text("قصص وعِبر", fontFamily = Amiri, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = SGreen) }
            items(stories) { s -> StoryRow(s, false) { open = s } }
            item { Text("قصص تربوية قصيرة من تأليف صافي، مش أحاديث ولا قصص حقيقية.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline) }
        }
    }
}

@Composable
private fun StoryRow(s: JSONObject, interactive: Boolean, onClick: () -> Unit) {
    val read = Kids.storyRead(s.optString("id"))
    Surface(onClick = onClick, shape = RoundedCornerShape(18.dp), color = Color.White, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            RawText(s.optString("icon"), fontSize = 34.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                RawText(KidStories.t(s.optJSONObject("title")), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = SInk)
                Text(if (interactive) "قصة بتختار فيها إنت" else "قصة وسؤال في الآخر", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
            if (read) RawText("⭐", fontSize = 22.sp)
        }
    }
}

@Composable
private fun ValueStory(kid: Kid, s: JSONObject, onDone: () -> Unit) {
    val pages = s.optJSONArray("pages")?.let { a -> (0 until a.length()).map { KidStories.t(a.getJSONObject(it)) } }.orEmpty()
    val answers = s.optJSONArray("a")?.let { a -> (0 until a.length()).map { KidStories.t(a.getJSONObject(it)) } }.orEmpty()
    val correct = s.optInt("c")
    var page by remember { mutableIntStateOf(0) }
    var wrong by remember { mutableStateOf(setOf<Int>()) }
    var solved by remember { mutableStateOf(false) }
    ScreenScaffold(KidStories.t(s.optJSONObject("title")), onBack = onDone) { pad ->
        Column(Modifier.fillMaxSize().background(SBg).padding(pad).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            val scenes = s.optJSONArray("scenes")
            val scene = scenes?.optString(page.coerceAtMost(scenes.length() - 1))?.ifBlank { null }
            if (scene != null) StoryScene(scene) else RawText(s.optString("icon"), fontSize = 72.sp)
            Spacer(Modifier.height(12.dp))
            if (page < pages.size) {
                LinearProgressIndicator(progress = { (page + 1f) / (pages.size + 1) }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(20.dp))
                RawText(pages[page], fontSize = 22.sp, lineHeight = 36.sp, color = SInk, modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (page > 0) OutlinedButton(onClick = { page-- }, modifier = Modifier.weight(1f).height(52.dp)) { Text("رجوع") }
                    Button(onClick = { page++ }, modifier = Modifier.weight(1f).height(52.dp)) { Text("بعدين؟") }
                }
            } else {
                RawText(KidStories.t(s.optJSONObject("q")), fontFamily = Amiri, fontWeight = FontWeight.Bold, fontSize = 24.sp, color = SInk)
                Spacer(Modifier.height(12.dp))
                answers.forEachIndexed { i, a ->
                    Surface(
                        onClick = {
                            if (solved) return@Surface
                            if (i == correct) { solved = true; if (Kids.markStory(s.optString("id"))) Kids.addStars(kid.id, if (wrong.isEmpty()) 2 else 1) } else wrong = wrong + i
                        },
                        shape = RoundedCornerShape(16.dp),
                        color = when { solved && i == correct -> SGreen.copy(alpha = 0.2f); i in wrong -> Color(0xFFFFE0E0); else -> Color.White },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    ) { RawText(a, Modifier.padding(14.dp), fontSize = 18.sp, color = SInk) }
                }
                if (wrong.isNotEmpty() && !solved) Text("فكّر تاني، إنت قريب! 🤔", color = Warn)
                if (solved) {
                    Spacer(Modifier.height(12.dp))
                    GoldCard { RawText("🌟 " + KidStories.t(s.optJSONObject("lesson")), fontWeight = FontWeight.Bold, fontSize = 18.sp) }
                    Spacer(Modifier.weight(1f))
                    Button(onClick = onDone, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("قصة تانية") }
                }
            }
        }
    }
}

@Composable
private fun ChoiceStory(kid: Kid, s: JSONObject, onDone: () -> Unit) {
    val nodes = s.optJSONObject("nodes") ?: JSONObject()
    var at by remember { mutableStateOf(s.optString("start", "a")) }
    val n = nodes.optJSONObject(at) ?: JSONObject()
    val end = n.optString("end")
    LaunchedEffect(at) { if (end == "good" && Kids.markStory(s.optString("id"))) Kids.addStars(kid.id, 2) }
    ScreenScaffold(KidStories.t(s.optJSONObject("title")), onBack = onDone) { pad ->
        Column(Modifier.fillMaxSize().background(SBg).padding(pad).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            n.optString("scene").ifBlank { null }?.let { StoryScene(it) }
                ?: RawText(if (end == "good") "🌟" else if (end == "retry") "🤔" else s.optString("icon"), fontSize = 72.sp)
            Spacer(Modifier.height(16.dp))
            RawText(KidStories.t(n.optJSONObject("t")), fontSize = 22.sp, lineHeight = 36.sp, color = SInk)
            Spacer(Modifier.height(20.dp))
            val ch = n.optJSONArray("ch")
            if (ch != null) for (i in 0 until ch.length()) {
                val c = ch.getJSONObject(i)
                Button(onClick = { at = c.optString("to") }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4FA3D9))) {
                    RawText(KidStories.t(c.optJSONObject("l")), fontSize = 18.sp)
                }
            }
            Spacer(Modifier.weight(1f))
            when (end) {
                "retry" -> Button(onClick = { at = s.optString("start", "a") }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("جرّب تاني") }
                "good" -> Button(onClick = onDone, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("برافو! قصة تانية") }
            }
        }
    }
}

// ================================================================= picture-book scenes

/** A drawn scene for a story page: "background|emojis" (day, night, home, school, garden, street, rain, shop). */
@Composable
fun StoryScene(spec: String, modifier: Modifier = Modifier) {
    val parts = spec.split("|", limit = 2)
    val bg = parts[0]
    val figures = remember(spec) { graphemes(parts.getOrElse(1) { "" }) }
    val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "scene")
    val phase: State<Float> = t.animateFloat(
        initialValue = 0f, targetValue = 6.2832f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(2400, easing = androidx.compose.animation.core.LinearEasing)),
        label = "bob",
    )
    Box(modifier.fillMaxWidth().height(230.dp).clip(RoundedCornerShape(24.dp))) {
        androidx.compose.foundation.Canvas(Modifier.matchParentSize()) { drawScene(bg) }
        Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 22.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
            figures.forEachIndexed { i, e ->
                val angle: Float = phase.value + i.toFloat() * 1.3f
                val dy = (kotlin.math.sin(angle) * 4f).dp
                RawText(e, fontSize = if (figures.size <= 2) 72.sp else if (figures.size <= 3) 60.sp else 50.sp, modifier = Modifier.offset(y = dy))
            }
        }
    }
}

private fun graphemes(s: String): List<String> {
    val it = android.icu.text.BreakIterator.getCharacterInstance()
    it.setText(s)
    val out = ArrayList<String>()
    var start = it.first(); var end = it.next()
    while (end != android.icu.text.BreakIterator.DONE) { s.substring(start, end).takeIf { x -> x.isNotBlank() }?.let(out::add); start = end; end = it.next() }
    return out
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawScene(bg: String) {
    val w = size.width; val h = size.height
    fun sky(top: Color, bottom: Color) = drawRect(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(top, bottom)))
    fun ground(c: Color, frac: Float = 0.26f) = drawRect(c, androidx.compose.ui.geometry.Offset(0f, h * (1 - frac)), androidx.compose.ui.geometry.Size(w, h * frac))
    fun cloud(x: Float, y: Float, r: Float, c: Color = Color.White) { drawCircle(c, r, androidx.compose.ui.geometry.Offset(x, y)); drawCircle(c, r * 0.8f, androidx.compose.ui.geometry.Offset(x + r, y + r * 0.2f)); drawCircle(c, r * 0.7f, androidx.compose.ui.geometry.Offset(x - r * 0.9f, y + r * 0.3f)) }
    fun wall(c: Color, floor: Color) { drawRect(c); ground(floor, 0.22f) }
    when (bg) {
        "night" -> {
            sky(Color(0xFF0E1A3A), Color(0xFF2B3A6B))
            listOf(0.1f to 0.15f, 0.3f to 0.08f, 0.55f to 0.2f, 0.75f to 0.1f, 0.2f to 0.35f, 0.65f to 0.32f).forEach { (x, y) -> drawCircle(Color(0xFFFFF3B0), 3.5f, androidx.compose.ui.geometry.Offset(w * x, h * y)) }
            drawCircle(Color(0xFFF2C14E), h * 0.12f, androidx.compose.ui.geometry.Offset(w * 0.85f, h * 0.2f))
            drawCircle(Color(0xFF14214A), h * 0.11f, androidx.compose.ui.geometry.Offset(w * 0.85f + h * 0.05f, h * 0.17f))
            ground(Color(0xFF1F3B2A))
        }
        "home" -> {
            wall(Color(0xFFFFE8C7), Color(0xFFB98552))
            drawRect(Color(0xFF9ED3F5), androidx.compose.ui.geometry.Offset(w * 0.08f, h * 0.12f), androidx.compose.ui.geometry.Size(w * 0.22f, h * 0.3f))
            drawRect(Color.White, androidx.compose.ui.geometry.Offset(w * 0.185f, h * 0.12f), androidx.compose.ui.geometry.Size(6f, h * 0.3f))
            drawRect(Color(0xFFE09F3E), androidx.compose.ui.geometry.Offset(w * 0.72f, h * 0.14f), androidx.compose.ui.geometry.Size(w * 0.16f, h * 0.18f))
        }
        "school" -> {
            wall(Color(0xFFFFF4C9), Color(0xFFC8A47A))
            drawRect(Color(0xFF2E5E4E), androidx.compose.ui.geometry.Offset(w * 0.2f, h * 0.1f), androidx.compose.ui.geometry.Size(w * 0.6f, h * 0.32f))
            drawRect(Color(0xFF8D6E63), androidx.compose.ui.geometry.Offset(w * 0.2f, h * 0.42f), androidx.compose.ui.geometry.Size(w * 0.6f, 6f))
        }
        "garden" -> {
            sky(Color(0xFF8FD3FF), Color(0xFFDFF3FF))
            drawCircle(Color(0xFFFFD54F), h * 0.1f, androidx.compose.ui.geometry.Offset(w * 0.86f, h * 0.18f))
            cloud(w * 0.25f, h * 0.18f, h * 0.07f)
            drawRect(Color(0xFF8D6E63), androidx.compose.ui.geometry.Offset(w * 0.07f, h * 0.38f), androidx.compose.ui.geometry.Size(w * 0.04f, h * 0.4f))
            drawCircle(Color(0xFF43A047), h * 0.14f, androidx.compose.ui.geometry.Offset(w * 0.09f, h * 0.36f))
            ground(Color(0xFF7CC576), 0.3f)
            listOf(0.3f, 0.48f, 0.66f, 0.9f).forEach { x -> drawCircle(Color(0xFFFF8A80), 6f, androidx.compose.ui.geometry.Offset(w * x, h * 0.8f)) }
        }
        "street" -> {
            sky(Color(0xFFA7DBFF), Color(0xFFE8F6FF))
            listOf(0.05f to 0.32f, 0.22f to 0.22f, 0.7f to 0.28f, 0.86f to 0.18f).forEach { (x, top) ->
                drawRect(Color(0xFFCFD8DC), androidx.compose.ui.geometry.Offset(w * x, h * top), androidx.compose.ui.geometry.Size(w * 0.12f, h * (0.74f - top)))
            }
            ground(Color(0xFF78909C), 0.26f)
            for (i in 0 until 6) drawRect(Color.White, androidx.compose.ui.geometry.Offset(w * (0.04f + i * 0.17f), h * 0.86f), androidx.compose.ui.geometry.Size(w * 0.08f, 4f))
        }
        "rain" -> {
            sky(Color(0xFF78909C), Color(0xFFB0BEC5))
            cloud(w * 0.3f, h * 0.15f, h * 0.09f, Color(0xFFECEFF1)); cloud(w * 0.75f, h * 0.12f, h * 0.08f, Color(0xFFECEFF1))
            for (i in 0 until 24) { val x = (i * 47 % 100) / 100f * w; val y = (i * 31 % 60) / 100f * h + h * 0.1f
                drawLine(Color(0xFFBBDEFB), androidx.compose.ui.geometry.Offset(x, y), androidx.compose.ui.geometry.Offset(x - 6f, y + 18f), 3f) }
            ground(Color(0xFF5D7F5A))
        }
        "shop" -> {
            wall(Color(0xFFFFF8E1), Color(0xFFBCAAA4))
            listOf(0.18f, 0.34f).forEach { y ->
                drawRect(Color(0xFF8D6E63), androidx.compose.ui.geometry.Offset(w * 0.05f, h * y), androidx.compose.ui.geometry.Size(w * 0.9f, 5f))
                for (i in 0 until 9) drawRect(listOf(Color(0xFFE57373), Color(0xFF64B5F6), Color(0xFFFFD54F), Color(0xFF81C784))[i % 4],
                    androidx.compose.ui.geometry.Offset(w * (0.07f + i * 0.1f), h * y - 22f), androidx.compose.ui.geometry.Size(w * 0.06f, 22f))
            }
        }
        else -> { // day
            sky(Color(0xFF8FD3FF), Color(0xFFE3F5FF))
            drawCircle(Color(0xFFFFD54F), h * 0.11f, androidx.compose.ui.geometry.Offset(w * 0.85f, h * 0.2f))
            cloud(w * 0.2f, h * 0.2f, h * 0.07f); cloud(w * 0.55f, h * 0.12f, h * 0.05f)
            ground(Color(0xFF8BC34A))
        }
    }
}
