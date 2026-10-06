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
fun KidsStoriesScreen(kid: Kid, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val all = remember { KidStories.load(ctx) }
    var open by remember { mutableStateOf<JSONObject?>(null) }
    var choice by remember { mutableStateOf<JSONObject?>(null) }
    open?.let { s -> BackHandler { open = null }; ValueStory(kid, s) { open = null }; return }
    choice?.let { s -> BackHandler { choice = null }; ChoiceStory(kid, s) { choice = null }; return }
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
            RawText(s.optString("icon"), fontSize = 72.sp)
            Spacer(Modifier.height(12.dp))
            if (page < pages.size) {
                LinearProgressIndicator(progress = { (page + 1f) / (pages.size + 1) }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(20.dp))
                RawText(pages[page], fontSize = 22.sp, lineHeight = 36.sp, color = SInk, modifier = Modifier.weight(1f))
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
            RawText(if (end == "good") "🌟" else if (end == "retry") "🤔" else s.optString("icon"), fontSize = 72.sp)
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
