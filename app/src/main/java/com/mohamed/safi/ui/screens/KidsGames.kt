package com.mohamed.safi.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import com.mohamed.safi.ui.Text
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mohamed.safi.kids.Kid
import com.mohamed.safi.kids.Kids
import com.mohamed.safi.ui.*
import kotlinx.coroutines.delay
import kotlin.random.Random

private val GBg = Color(0xFFFFF7E8)
private val GSky = Color(0xFF4FA3D9)
private val GGreen = Color(0xFF2E9D5B)
private val GInk = Color(0xFF3B3125)

/** The kids' games other than memory, wudu and the quiz (those live in KidsScreen). */
@Composable
fun KidsGame(id: String, kid: Kid, onDone: () -> Unit) {
    BackHandler { onDone() }
    when (id) {
        "catch" -> CatchGame(kid, onDone)
        "maze" -> MazeGame(kid, onDone)
        "color" -> ColorGame(kid, onDone)
        "letters" -> LettersGame(kid, onDone)
        "prayers" -> PrayerOrderGame(kid, onDone)
        "count" -> CountGame(kid, onDone)
        "simon" -> SimonGame(kid, onDone)
        "slide" -> SlideGame(kid, onDone)
        "xo" -> XoGame(kid, onDone)
        "balloons" -> BalloonGame(kid, onDone)
        else -> {
            val ctx = androidx.compose.ui.platform.LocalContext.current
            val data = remember { KidData.games(ctx) }
            data.pick.firstOrNull { it.optString("id") == id }?.let { PickGame(kid, it, onDone); return }
            data.order.firstOrNull { it.optString("id") == id }?.let { OrderGame(kid, it, onDone); return }
            data.memory.firstOrNull { it.optString("id") == id }?.let { m ->
                val faces = m.optJSONArray("faces")!!.let { a -> (0 until a.length()).map { a.getString(it) } }
                ThemedMemory(kid, KidData.t(m.optJSONObject("title")), faces, onDone); return
            }
            LaunchedEffect(Unit) { onDone() }
        }
    }
}

/** Kids' content in ar/en/ur from assets/kids/*.json. */
object KidData {
    class Games(val pick: List<org.json.JSONObject>, val order: List<org.json.JSONObject>, val memory: List<org.json.JSONObject>)
    private var games: Games? = null
    private fun arr(o: org.json.JSONObject, k: String) = o.optJSONArray(k)?.let { a -> (0 until a.length()).map { a.getJSONObject(it) } }.orEmpty()
    fun games(ctx: android.content.Context): Games = games ?: runCatching {
        val o = org.json.JSONObject(ctx.assets.open("kids/games.json").bufferedReader().use { it.readText() })
        Games(arr(o, "pick"), arr(o, "order"), arr(o, "memory"))
    }.getOrDefault(Games(emptyList(), emptyList(), emptyList())).also { games = it }

    /** Text in the app language: Arabic, English or Urdu; other languages read the English. */
    fun t(o: org.json.JSONObject?): String {
        if (o == null) return ""
        val l = I18n.lang.value
        return o.optString(if (l == "ar" || l == "ur") l else "en").ifBlank { o.optString("ar") }
    }

    data class Entry(val id: String, val icon: String, val title: String)
    /** Every kids game, in the order of the Ramadan calendar (game n on Ramadan day n). */
    fun allGames(ctx: android.content.Context): List<Entry> {
        val g = games(ctx)
        val builtIn = listOf(
            Entry("memory", "🧠", tr("لعبة الذاكرة")), Entry("catch", "🏮", tr("اصطاد الفوانيس")), Entry("wudu", "💧", tr("رتّب الوضوء")),
            Entry("maze", "🧭", tr("المتاهة")), Entry("letters", "🔤", tr("الحروف")), Entry("color", "🎨", tr("لوّن بالأرقام")),
            Entry("prayers", "🕌", tr("الصلوات الخمس")), Entry("count", "🔢", tr("عدّ معايا")), Entry("quiz", "❓", tr("أسئلة سهلة")),
            Entry("simon", "🚦", tr("تتابع الألوان")), Entry("slide", "🧩", tr("اللغز المنزلق")), Entry("xo", "⭕", tr("إكس أو")), Entry("balloons", "🎈", tr("فرقع البالونات")),
        )
        val fromJson = (g.pick + g.order + g.memory).map { Entry(it.optString("id"), it.optString("icon"), t(it.optJSONObject("title"))) }
        // interleave so the calendar mixes kinds of games
        val out = ArrayList<Entry>()
        val a = ArrayDeque(builtIn); val b = ArrayDeque(fromJson)
        while (a.isNotEmpty() || b.isNotEmpty()) { a.removeFirstOrNull()?.let { out += it }; b.removeFirstOrNull()?.let { out += it }; b.removeFirstOrNull()?.let { out += it } }
        return out
    }
}

@Composable
private fun GameFrame(title: String, onDone: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ScreenScaffold(title, onBack = onDone) { pad ->
        Column(Modifier.fillMaxSize().background(GBg).padding(pad).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, content = content)
    }
}

@Composable
private fun WinBox(text: String, onAgain: () -> Unit, onDone: () -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = Color.White, border = androidx.compose.foundation.BorderStroke(2.dp, GGreen)) {
        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = GInk)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                Button(onClick = onAgain) { Text("العب تاني") }
                OutlinedButton(onClick = onDone) { Text("رجوع") }
            }
        }
    }
}

// ================================================================= catch the lanterns

private data class Fall(val id: Int, val x: Float, val y: Float, val speed: Float, val icon: String, val good: Boolean)

/** 30 seconds: tap the falling lanterns, moons and stars; clouds take a point. */
@Composable
private fun CatchGame(kid: Kid, onDone: () -> Unit) {
    var round by remember { mutableIntStateOf(0) }
    val items = remember(round) { mutableStateListOf<Fall>() }
    var score by remember(round) { mutableIntStateOf(0) }
    var left by remember(round) { mutableIntStateOf(30) }
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(round) {
        var n = 0; var t = 0L
        while (left > 0) {
            delay(32); t += 32
            if (t % 1000 < 32) left--
            val speedUp = 1f + (30 - left) / 30f
            val moved = items.map { it.copy(y = it.y + it.speed * speedUp * 0.032f) }.filter { it.y < 1.05f }
            items.clear(); items.addAll(moved)
            if (Random.nextFloat() < 0.06f * speedUp) {
                val good = Random.nextFloat() < 0.8f
                items.add(Fall(n++, Random.nextFloat() * 0.85f, -0.1f, 0.18f + Random.nextFloat() * 0.15f,
                    if (good) listOf("🏮", "🌙", "⭐", "🏮").random() else "☁️", good))
            }
        }
        items.clear()
        Kids.addStars(kid.id, (score / 6).coerceIn(1, 5))
    }
    GameFrame("اصطاد الفوانيس", onDone) {
        Row(Modifier.fillMaxWidth()) {
            Text("⭐ $score", fontWeight = FontWeight.Bold, fontSize = 22.sp, modifier = Modifier.weight(1f))
            Text("⏱️ $left", fontWeight = FontWeight.Bold, fontSize = 22.sp)
        }
        Text("دوس على الفانوس والقمر والنجمة، وابعد عن السحابة ☁️", style = MaterialTheme.typography.bodySmall, color = GInk)
        Spacer(Modifier.height(8.dp))
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(20.dp)).background(Color(0xFF1A2A4F))) {
            val w = maxWidth; val h = maxHeight
            items.forEach { f ->
                key(f.id) {
                    Box(
                        Modifier.offset(x = w * f.x, y = h * f.y).size(58.dp).clip(CircleShape).clickable {
                            items.removeAll { it.id == f.id }
                            if (f.good) { score++; haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) } else score = (score - 1).coerceAtLeast(0)
                        },
                        contentAlignment = Alignment.Center,
                    ) { androidx.compose.material3.Text(f.icon, fontSize = 34.sp) }
                }
            }
            if (left == 0) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                WinBox("برافو! اصطدت $score 🏮", onAgain = { round++ }, onDone = onDone)
            }
        }
    }
}

// ================================================================= maze

private class Maze(val cols: Int, val rows: Int, seed: Long) {
    // open[c][r] bit: 1 up, 2 right, 4 down, 8 left
    val open = Array(cols) { IntArray(rows) }
    init {
        val r = Random(seed)
        val seen = Array(cols) { BooleanArray(rows) }
        val stack = ArrayDeque<Pair<Int, Int>>()
        stack.add(0 to 0); seen[0][0] = true
        while (stack.isNotEmpty()) {
            val (c, w) = stack.last()
            val nbrs = listOf(Triple(0, -1, 1), Triple(1, 0, 2), Triple(0, 1, 4), Triple(-1, 0, 8))
                .filter { (dc, dr, _) -> c + dc in 0 until cols && w + dr in 0 until rows && !seen[c + dc][w + dr] }
            if (nbrs.isEmpty()) { stack.removeLast(); continue }
            val (dc, dr, bit) = nbrs[r.nextInt(nbrs.size)]
            open[c][w] = open[c][w] or bit
            val back = when (bit) { 1 -> 4; 2 -> 8; 4 -> 1; else -> 2 }
            open[c + dc][w + dr] = open[c + dc][w + dr] or back
            seen[c + dc][w + dr] = true
            stack.add((c + dc) to (w + dr))
        }
    }
}

/** Walk from the start to the mosque. The maze grows a bit each time you finish one. */
@Composable
private fun MazeGame(kid: Kid, onDone: () -> Unit) {
    var level by remember { mutableIntStateOf(0) }
    val cols = 5 + level.coerceAtMost(3); val rows = 7 + level.coerceAtMost(4)
    val maze = remember(level) { Maze(cols, rows, System.nanoTime()) }
    var pos by remember(level) { mutableStateOf(0 to 0) }
    var steps by remember(level) { mutableIntStateOf(0) }
    val won = pos == (cols - 1) to (rows - 1)
    LaunchedEffect(won) { if (won) Kids.addStars(kid.id, 2) }
    fun move(bit: Int, dc: Int, dr: Int) {
        if (won) return
        val (c, r) = pos
        if (maze.open[c][r] and bit != 0) { pos = (c + dc) to (r + dr); steps++ }
    }
    GameFrame("المتاهة", onDone) {
        Text(if (won) "وصلت المسجد! 🕌" else "وصّل 🧒 للمسجد 🕌", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = GInk)
        Spacer(Modifier.height(8.dp))
        // the maze is a picture: keep it left-to-right whatever the app language
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                val cell = minOf(maxWidth / cols, maxHeight / rows)
                Box(Modifier.size(cell * cols, cell * rows).background(Color.White, RoundedCornerShape(8.dp))) {
                    Canvas(Modifier.matchParentSize()) {
                        val s = size.width / cols
                        val stroke = 4.dp.toPx()
                        for (c in 0 until cols) for (r in 0 until rows) {
                            val x = c * s; val y = r * s; val o = maze.open[c][r]
                            if (o and 1 == 0) drawLine(GSky, Offset(x, y), Offset(x + s, y), stroke, StrokeCap.Round)
                            if (o and 2 == 0) drawLine(GSky, Offset(x + s, y), Offset(x + s, y + s), stroke, StrokeCap.Round)
                            if (o and 4 == 0) drawLine(GSky, Offset(x, y + s), Offset(x + s, y + s), stroke, StrokeCap.Round)
                            if (o and 8 == 0) drawLine(GSky, Offset(x, y), Offset(x, y + s), stroke, StrokeCap.Round)
                        }
                    }
                    Box(Modifier.offset(cell * (cols - 1), cell * (rows - 1)).size(cell), contentAlignment = Alignment.Center) { androidx.compose.material3.Text("🕌", fontSize = (cell.value * 0.6f).sp) }
                    Box(Modifier.offset(cell * pos.first, cell * pos.second).size(cell), contentAlignment = Alignment.Center) { androidx.compose.material3.Text("🧒", fontSize = (cell.value * 0.6f).sp) }
                }
            }
            Spacer(Modifier.height(8.dp))
            if (won) WinBox("خلصتها في $steps خطوة ⭐⭐", onAgain = { level++ }, onDone = onDone)
            else Column(horizontalAlignment = Alignment.CenterHorizontally) {
                MazeBtn(Icons.Default.KeyboardArrowUp) { move(1, 0, -1) }
                Row {
                    MazeBtn(Icons.Default.KeyboardArrowLeft) { move(8, -1, 0) }
                    Spacer(Modifier.width(64.dp))
                    MazeBtn(Icons.Default.KeyboardArrowRight) { move(2, 1, 0) }
                }
                MazeBtn(Icons.Default.KeyboardArrowDown) { move(4, 0, 1) }
            }
        }
    }
}

@Composable
private fun MazeBtn(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    FilledIconButton(onClick = onClick, modifier = Modifier.size(60.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = GSky)) {
        Icon(icon, null, Modifier.size(36.dp), tint = Color.White)
    }
}

// ================================================================= colour by number

private val palette = listOf(Color(0xFFF2C14E), Color(0xFF43A047), Color(0xFF42A5F5), Color(0xFF8D6E63), Color(0xFFE53935))
private val pictures = listOf(
    "🕌" to listOf(
        "000001100000", "000011110000", "300111111003", "301111111103", "301111111103", "322222222223",
        "322222222223", "322244422223", "322244422223", "322244422223", "322244422223", "555555555555",
    ),
    "🏮" to listOf(
        "0000440000", "0001111000", "0011111100", "0555555550", "0511111150", "0511331150",
        "0511331150", "0511111150", "0555555550", "0011111100", "0001111000", "0000440000",
    ),
    "🌳" to listOf(
        "0002222000", "0022552200", "0222222220", "2252222522", "2222222222", "0222252220",
        "0022222200", "0000440000", "0000440000", "0000440000", "0044444400",
    ),
    "🌙" to listOf(
        "3333333333", "3331133333", "3311333313", "3113333111", "3113333313",
        "3113333333", "3311333333", "3331133333", "3333333333",
    ),
)

/** Pick a colour number, then tap the squares with that number. */
@Composable
private fun ColorGame(kid: Kid, onDone: () -> Unit) {
    var pic by remember { mutableIntStateOf(0) }
    val rows = pictures[pic].second
    val filled = remember(pic) { mutableStateListOf<Int>() }
    var color by remember(pic) { mutableIntStateOf(1) }
    val total = rows.sumOf { r -> r.count { it != '0' } }
    val won = filled.size == total
    LaunchedEffect(won) { if (won) Kids.addStars(kid.id, 3) }
    GameFrame("لوّن بالأرقام", onDone) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            pictures.forEachIndexed { i, p -> FilterChip(pic == i, { pic = i }, label = { androidx.compose.material3.Text(p.first, fontSize = 20.sp) }) }
        }
        Spacer(Modifier.height(8.dp))
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                val cols = rows.maxOf { it.length }
                val cell = minOf(maxWidth / cols, maxHeight / rows.size)
                Column {
                    rows.forEachIndexed { r, line ->
                        Row {
                            line.forEachIndexed { c, ch ->
                                val n = ch.digitToInt()
                                val idx = r * 100 + c
                                val done = n == 0 || idx in filled
                                Box(
                                    Modifier.size(cell).border(0.5.dp, Color(0x22000000))
                                        .background(if (n == 0) Color.White else if (done) palette[n - 1] else Color(0xFFF5F5F5))
                                        .clickable(enabled = !done) { if (n == color) filled.add(idx) },
                                    contentAlignment = Alignment.Center,
                                ) { if (!done) androidx.compose.material3.Text("$n", fontSize = (cell.value * 0.45f).sp, color = Color.Gray) }
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        if (won) WinBox("لوحة جميلة! 🎨", onAgain = { pic = (pic + 1) % pictures.size }, onDone = onDone)
        else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            palette.forEachIndexed { i, p ->
                Box(
                    Modifier.size(52.dp).clip(CircleShape).background(p).border(if (color == i + 1) 4.dp else 0.dp, GInk, CircleShape).clickable { color = i + 1 },
                    contentAlignment = Alignment.Center,
                ) { Text("${i + 1}", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 20.sp) }
            }
        }
    }
}

// ================================================================= Arabic letters

private val letters = listOf(
    "أ" to ("🐰" to "أرنب"), "ب" to ("🦆" to "بطة"), "ت" to ("🍎" to "تفاحة"), "ث" to ("🦊" to "ثعلب"), "ج" to ("🐫" to "جمل"),
    "ح" to ("🐴" to "حصان"), "خ" to ("🐑" to "خروف"), "د" to ("🐻" to "دب"), "ذ" to ("🌽" to "ذرة"), "ز" to ("🦒" to "زرافة"),
    "س" to ("🐟" to "سمكة"), "ش" to ("☀️" to "شمس"), "ص" to ("🚀" to "صاروخ"), "ض" to ("🐸" to "ضفدع"), "ط" to ("✈️" to "طيارة"),
    "ظ" to ("✉️" to "ظرف"), "ع" to ("🍇" to "عنب"), "غ" to ("☁️" to "غيمة"), "ف" to ("🐘" to "فيل"), "ق" to ("🌙" to "قمر"),
    "ك" to ("📖" to "كتاب"), "ل" to ("🍋" to "ليمون"), "م" to ("🍌" to "موز"), "ن" to ("🐝" to "نحلة"), "هـ" to ("🎁" to "هدية"),
    "و" to ("🌹" to "وردة"), "ي" to ("✋" to "يد"),
)

/** See the picture, pick the letter its name starts with. */
@Composable
private fun LettersGame(kid: Kid, onDone: () -> Unit) {
    var round by remember { mutableIntStateOf(0) }
    val qs = remember(round) { letters.shuffled().take(8) }
    var i by remember(round) { mutableIntStateOf(0) }
    var wrong by remember(round) { mutableStateOf(setOf<String>()) }
    var right by remember(round) { mutableIntStateOf(0) }
    var reveal by remember(round) { mutableStateOf(false) }
    val q = qs.getOrNull(i)
    val choices = remember(round, i) { q?.let { (listOf(it.first) + letters.map { l -> l.first }.filter { l -> l != it.first }.shuffled().take(2)).shuffled() } ?: emptyList() }
    LaunchedEffect(reveal) { if (reveal) { delay(1100); reveal = false; wrong = emptySet(); i++ } }
    LaunchedEffect(q == null) { if (q == null) Kids.addStars(kid.id, (right / 2).coerceAtLeast(1)) }
    GameFrame("الحروف", onDone) {
        if (q == null) { WinBox("عرفت $right من ${qs.size} من أول مرة 🌟", onAgain = { round++ }, onDone = onDone); return@GameFrame }
        Text("${i + 1} / ${qs.size}", color = GInk)
        androidx.compose.material3.Text(q.second.first, fontSize = 110.sp, modifier = Modifier.padding(16.dp))
        androidx.compose.material3.Text(if (reveal) q.second.second else "؟", fontFamily = Amiri, fontWeight = FontWeight.Bold, fontSize = 36.sp, color = GGreen)
        Text("بيبدأ بأنهي حرف؟", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = GInk)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            choices.forEach { l ->
                Surface(
                    onClick = { if (reveal) return@Surface; if (l == q.first) { if (wrong.isEmpty()) right++; reveal = true } else wrong = wrong + l },
                    shape = RoundedCornerShape(20.dp), color = when { reveal && l == q.first -> GGreen.copy(alpha = 0.25f); l in wrong -> Color(0xFFFFE0E0); else -> Color.White },
                    border = androidx.compose.foundation.BorderStroke(2.dp, GSky), modifier = Modifier.size(84.dp),
                ) { Box(contentAlignment = Alignment.Center) { androidx.compose.material3.Text(l, fontFamily = Amiri, fontSize = 40.sp, fontWeight = FontWeight.Bold) } }
            }
        }
    }
}

// ================================================================= prayers in order + rak'ahs

private val prayers = listOf("الفجر" to 2, "الظهر" to 4, "العصر" to 4, "المغرب" to 3, "العشاء" to 4)

/** First put the five prayers in order, then say how many rak'ahs each fard prayer has. */
@Composable
private fun PrayerOrderGame(kid: Kid, onDone: () -> Unit) {
    var round by remember { mutableIntStateOf(0) }
    val order = remember(round) { prayers.indices.shuffled() }
    var next by remember(round) { mutableIntStateOf(0) }
    var rakIdx by remember(round) { mutableIntStateOf(0) }
    var hint by remember(round) { mutableStateOf("") }
    var mistakes by remember(round) { mutableIntStateOf(0) }
    val done = rakIdx == prayers.size
    LaunchedEffect(done) { if (done) Kids.addStars(kid.id, if (mistakes == 0) 3 else 2) }
    GameFrame("الصلوات الخمس", onDone) {
        if (done) { WinBox(if (mistakes == 0) "ممتاز! من غير ولا غلطة 🕌⭐⭐⭐" else "برافو! خلصت 🕌⭐⭐", onAgain = { round++ }, onDone = onDone); return@GameFrame }
        if (next < prayers.size) {
            Text("رتّب الصلوات: دوس على الصلاة رقم ${next + 1}", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = GInk)
            if (hint.isNotBlank()) Text(hint, color = Warn)
            Spacer(Modifier.height(8.dp))
            order.forEach { i ->
                val ok = i < next
                Surface(
                    onClick = { if (ok) return@Surface; if (i == next) { next++; hint = "" } else { mistakes++; hint = "فكّر تاني: إيه الصلاة اللي بعدها؟ 🤔" } },
                    shape = RoundedCornerShape(16.dp), color = if (ok) GGreen.copy(alpha = 0.18f) else Color.White,
                    border = androidx.compose.foundation.BorderStroke(2.dp, if (ok) GGreen else GSky.copy(alpha = 0.4f)), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (ok) "${i + 1}" else "🕌", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Spacer(Modifier.width(10.dp))
                        Text(prayers[i].first, fontSize = 18.sp, color = GInk)
                    }
                }
            }
        } else {
            val (name, n) = prayers[rakIdx]
            Text("صلاة $name الفرض كام ركعة؟", fontFamily = Amiri, fontWeight = FontWeight.Bold, fontSize = 24.sp, color = GInk)
            if (hint.isNotBlank()) Text(hint, color = Warn)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                listOf(2, 3, 4).forEach { k ->
                    Surface(
                        onClick = { if (k == n) { rakIdx++; hint = "" } else { mistakes++; hint = "قريب! جرّب تاني 🤔" } },
                        shape = CircleShape, color = Color.White, border = androidx.compose.foundation.BorderStroke(2.dp, GSky), modifier = Modifier.size(84.dp),
                    ) { Box(contentAlignment = Alignment.Center) { Text("$k", fontSize = 36.sp, fontWeight = FontWeight.Bold) } }
                }
            }
        }
    }
}

// ================================================================= counting

/** Count the pictures and pick the number (for the little ones). */
@Composable
private fun CountGame(kid: Kid, onDone: () -> Unit) {
    var round by remember { mutableIntStateOf(0) }
    val icons = listOf("⭐", "🌙", "🍎", "🐟", "🌸", "🏮", "🐑", "🎈")
    val qs = remember(round) { List(6) { icons.random() to Random.nextInt(1, 10) } }
    var i by remember(round) { mutableIntStateOf(0) }
    var wrong by remember(round) { mutableStateOf(setOf<Int>()) }
    var right by remember(round) { mutableIntStateOf(0) }
    val q = qs.getOrNull(i)
    val choices = remember(round, i) { q?.let { (setOf(it.second) + generateSequence { Random.nextInt(1, 10) }.filter { n -> n != it.second }.distinct().take(2)).shuffled() } ?: emptyList() }
    LaunchedEffect(q == null) { if (q == null) Kids.addStars(kid.id, (right / 2).coerceAtLeast(1)) }
    GameFrame("عدّ معايا", onDone) {
        if (q == null) { WinBox("عدّيت $right صح من أول مرة 🌟", onAgain = { round++ }, onDone = onDone); return@GameFrame }
        Text("كام ${q.first}؟", fontWeight = FontWeight.Bold, fontSize = 24.sp, color = GInk)
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(20.dp)).background(Color.White), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                (0 until q.second).chunked(3).forEach { row -> Row { row.forEach { _ -> androidx.compose.material3.Text(q.first, fontSize = 46.sp, modifier = Modifier.padding(6.dp)) } } }
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            choices.forEach { k ->
                Surface(
                    onClick = { if (k == q.second) { if (wrong.isEmpty()) right++; i++; wrong = emptySet() } else wrong = wrong + k },
                    shape = CircleShape, color = if (k in wrong) Color(0xFFFFE0E0) else Color.White, border = androidx.compose.foundation.BorderStroke(2.dp, GSky), modifier = Modifier.size(84.dp),
                ) { Box(contentAlignment = Alignment.Center) { Text("$k", fontSize = 36.sp, fontWeight = FontWeight.Bold) } }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

// ================================================================= quiz-like packs (assets/kids/games.json "pick")

@Composable
private fun PickGame(kid: Kid, pack: org.json.JSONObject, onDone: () -> Unit) {
    var round by remember { mutableIntStateOf(0) }
    val items = remember(round) { pack.optJSONArray("items")!!.let { a -> (0 until a.length()).map { a.getJSONObject(it) } }.shuffled() }
    var i by remember(round) { mutableIntStateOf(0) }
    var wrong by remember(round) { mutableStateOf(setOf<Int>()) }
    var right by remember(round) { mutableIntStateOf(0) }
    val haptic = LocalHapticFeedback.current
    val q = items.getOrNull(i)
    LaunchedEffect(q == null) { if (q == null) Kids.addStars(kid.id, (right / 3).coerceIn(1, 3)) }
    GameFrame(KidData.t(pack.optJSONObject("title")), onDone) {
        if (q == null) { WinBox("عرفت $right من ${items.size} من أول مرة 🌟", onAgain = { round++ }, onDone = onDone); return@GameFrame }
        Text("${i + 1} / ${items.size}", color = GInk)
        LinearProgressIndicator(progress = { i / items.size.toFloat() }, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), color = GGreen)
        Spacer(Modifier.height(12.dp))
        val prompt = KidData.t(q.optJSONObject("q")).ifBlank { KidData.t(pack.optJSONObject("prompt")) }
        val big = prompt.length <= 4
        androidx.compose.material3.Text(prompt, fontFamily = Amiri, fontWeight = FontWeight.Bold, fontSize = if (big) 72.sp else 24.sp, color = GInk,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (big) KidData.t(pack.optJSONObject("prompt")).takeIf { it.isNotBlank() }?.let { androidx.compose.material3.Text(it, fontSize = 18.sp, color = GInk) }
        Spacer(Modifier.height(20.dp))
        val opts = q.optJSONArray("o")!!.let { a -> (0 until a.length()).map { KidData.t(a.getJSONObject(it)) } }
        val c = q.optInt("c")
        val short = opts.all { it.length <= 3 }
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            opts.forEachIndexed { k, o ->
                Surface(
                    onClick = {
                        if (k == c) { if (wrong.isEmpty()) right++; haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); i++; wrong = emptySet() }
                        else wrong = wrong + k
                    },
                    shape = RoundedCornerShape(20.dp), color = if (k in wrong) Color(0xFFFFE0E0) else Color.White,
                    border = androidx.compose.foundation.BorderStroke(2.dp, GSky),
                    modifier = if (short) Modifier.size(88.dp) else Modifier.fillMaxWidth(),
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(if (short) 0.dp else 14.dp)) {
                        androidx.compose.material3.Text(o, fontSize = if (short) 40.sp else 20.sp, fontWeight = FontWeight.SemiBold, color = GInk)
                    }
                }
            }
        }
        if (wrong.isNotEmpty()) Text("فكّر تاني، إنت قريب! 🤔", color = Warn, modifier = Modifier.padding(top = 8.dp))
    }
}

// ================================================================= put in order (assets/kids/games.json "order")

@Composable
private fun OrderGame(kid: Kid, pack: org.json.JSONObject, onDone: () -> Unit) {
    val steps = remember { pack.optJSONArray("steps")!!.let { a -> (0 until a.length()).map { KidData.t(a.getJSONObject(it)) } } }
    var round by remember { mutableIntStateOf(0) }
    val shuffled = remember(round) { steps.indices.shuffled() }
    var next by remember(round) { mutableIntStateOf(0) }
    var hint by remember(round) { mutableStateOf(false) }
    val done = next == steps.size
    LaunchedEffect(done) { if (done) Kids.addStars(kid.id, 2) }
    GameFrame(KidData.t(pack.optJSONObject("title")), onDone) {
        androidx.compose.material3.Text(KidData.t(pack.optJSONObject("hint")), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = GInk)
        if (hint) Text("فكّر تاني، إيه اللي بعدها؟ 🤔", color = Warn)
        Spacer(Modifier.height(8.dp))
        Column(Modifier.weight(1f).verticalScroll(androidx.compose.foundation.rememberScrollState())) {
            shuffled.forEach { i ->
                val ok = i < next
                Surface(
                    onClick = { if (ok || done) return@Surface; if (i == next) { next++; hint = false } else hint = true },
                    shape = RoundedCornerShape(16.dp), color = if (ok) GGreen.copy(alpha = 0.18f) else Color.White,
                    border = androidx.compose.foundation.BorderStroke(2.dp, if (ok) GGreen else GSky.copy(alpha = 0.4f)), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (ok) "${i + 1}" else "•", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Spacer(Modifier.width(10.dp))
                        androidx.compose.material3.Text(steps[i], fontSize = 18.sp, color = GInk)
                    }
                }
            }
        }
        if (done) WinBox("ممتاز! رتّبتها صح ⭐⭐", onAgain = { round++ }, onDone = onDone)
    }
}

// ================================================================= themed memory

@Composable
private fun ThemedMemory(kid: Kid, title: String, faces: List<String>, onDone: () -> Unit) {
    var round by remember { mutableIntStateOf(0) }
    val cards = remember(round) { faces.take(if (faces.size > 6) 8 else 6).flatMap { listOf(it, it) }.shuffled() }
    val open = remember(round) { mutableStateListOf<Int>() }
    val matched = remember(round) { mutableStateListOf<Int>() }
    var moves by remember(round) { mutableIntStateOf(0) }
    LaunchedEffect(open.size, round) {
        if (open.size == 2) {
            moves++; delay(650)
            if (cards[open[0]] == cards[open[1]]) matched.addAll(open)
            open.clear()
            if (matched.size == cards.size) Kids.addStars(kid.id, 3)
        }
    }
    val won = matched.size == cards.size
    val cols = if (cards.size > 12) 4 else 3
    GameFrame(title, onDone) {
        Text(if (won) "برافو! خلصتها في $moves محاولة ⭐⭐⭐" else "اقلب كارتين متشابهين • $moves محاولة", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = GInk)
        Spacer(Modifier.height(10.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            cards.indices.chunked(cols).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { i ->
                        val shown = i in open || i in matched
                        Surface(
                            onClick = { if (!shown && open.size < 2) open.add(i) },
                            shape = RoundedCornerShape(14.dp), color = if (i in matched) GGreen.copy(alpha = 0.2f) else if (shown) Color.White else GSky,
                            modifier = Modifier.weight(1f).aspectRatio(1f),
                        ) { Box(contentAlignment = Alignment.Center) { androidx.compose.material3.Text(if (shown) cards[i] else "?", fontSize = 30.sp, color = if (shown) Color.Unspecified else Color.White) } }
                    }
                }
            }
        }
        if (won) WinBox("برافو! 🌟", onAgain = { round++ }, onDone = onDone)
    }
}

// ================================================================= Simon: repeat the colour sequence

@Composable
private fun SimonGame(kid: Kid, onDone: () -> Unit) {
    val colors = listOf(Color(0xFFE53935), Color(0xFF43A047), Color(0xFF1E88E5), Color(0xFFFDD835))
    var round by remember { mutableIntStateOf(0) }
    val seq = remember(round) { mutableStateListOf(Random.nextInt(4)) }
    var showing by remember(round) { mutableStateOf(true) }
    var lit by remember(round) { mutableIntStateOf(-1) }
    var pos by remember(round) { mutableIntStateOf(0) }
    var over by remember(round) { mutableStateOf(false) }
    LaunchedEffect(seq.size, round) {
        showing = true; delay(600)
        for (c in seq.toList()) { lit = c; delay(520); lit = -1; delay(220) }
        showing = false; pos = 0
    }
    LaunchedEffect(over) { if (over) Kids.addStars(kid.id, (seq.size / 2).coerceIn(1, 5)) }
    GameFrame("تتابع الألوان", onDone) {
        Text(if (over) "وصلت لـ ${seq.size - 1} 🌟" else if (showing) "ركّز… 👀" else "دورك! (${pos + 1}/${seq.size})", fontWeight = FontWeight.Bold, fontSize = 22.sp, color = GInk)
        Spacer(Modifier.height(16.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(0 to 1, 2 to 3).forEach { (a, b) ->
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf(a, b).forEach { c ->
                        Box(
                            Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(24.dp))
                                .background(if (lit == c) colors[c] else colors[c].copy(alpha = 0.35f))
                                .clickable(enabled = !showing && !over) {
                                    if (c == seq[pos]) { pos++; if (pos == seq.size) seq.add(Random.nextInt(4)) } else over = true
                                },
                        )
                    }
                }
            }
        }
        if (over) { Spacer(Modifier.height(8.dp)); WinBox("برافو! افتكرت ${seq.size - 1} 🚦", onAgain = { round++ }, onDone = onDone) }
    }
}

// ================================================================= sliding puzzle 3x3

@Composable
private fun SlideGame(kid: Kid, onDone: () -> Unit) {
    var round by remember { mutableIntStateOf(0) }
    val tiles = remember(round) {
        // shuffle by legal moves so it is always solvable
        val t = (1..8).toMutableList<Int>().apply { add(0) }
        var blank = 8
        repeat(60) {
            val r = blank / 3; val c = blank % 3
            val n = listOfNotNull(if (r > 0) blank - 3 else null, if (r < 2) blank + 3 else null, if (c > 0) blank - 1 else null, if (c < 2) blank + 1 else null).random()
            t[blank] = t[n]; t[n] = 0; blank = n
        }
        mutableStateListOf<Int>().apply { addAll(t) }
    }
    var moves by remember(round) { mutableIntStateOf(0) }
    val solved = tiles.toList() == (1..8).toList() + 0
    LaunchedEffect(solved) { if (solved && moves > 0) Kids.addStars(kid.id, 3) }
    GameFrame("اللغز المنزلق", onDone) {
        Text(if (solved) "حلّيتها في $moves حركة 🧩" else "رتّب الأرقام من ١ لـ ٨ • $moves حركة", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = GInk)
        Spacer(Modifier.height(16.dp))
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Column(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(16.dp)).background(GSky.copy(alpha = 0.25f)).padding(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                (0 until 3).forEach { r ->
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        (0 until 3).forEach { c ->
                            val i = r * 3 + c; val v = tiles[i]
                            Box(
                                Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(12.dp)).background(if (v == 0) Color.Transparent else Color.White)
                                    .clickable(enabled = v != 0 && !solved) {
                                        val b = tiles.indexOf(0)
                                        if ((b / 3 == r && kotlin.math.abs(b % 3 - c) == 1) || (b % 3 == c && kotlin.math.abs(b / 3 - r) == 1)) { tiles[b] = v; tiles[i] = 0; moves++ }
                                    },
                                contentAlignment = Alignment.Center,
                            ) { if (v != 0) Text("$v", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = GInk) }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        if (solved) WinBox("برافو! 🧩", onAgain = { round++ }, onDone = onDone)
    }
}

// ================================================================= tic-tac-toe against the phone

@Composable
private fun XoGame(kid: Kid, onDone: () -> Unit) {
    var round by remember { mutableIntStateOf(0) }
    val b = remember(round) { mutableStateListOf<Int>().apply { repeat(9) { add(0) } } } // 1 = child (X), 2 = phone (O)
    val lines = listOf(listOf(0, 1, 2), listOf(3, 4, 5), listOf(6, 7, 8), listOf(0, 3, 6), listOf(1, 4, 7), listOf(2, 5, 8), listOf(0, 4, 8), listOf(2, 4, 6))
    fun winner(): Int = lines.firstOrNull { l -> b[l[0]] != 0 && l.all { b[it] == b[l[0]] } }?.let { b[it[0]] } ?: if (b.none { it == 0 }) 3 else 0
    fun phoneMove() {
        val free = b.indices.filter { b[it] == 0 }
        if (free.isEmpty()) return
        // win if possible, else block, else centre, else random (beatable on purpose)
        fun finishing(p: Int) = free.firstOrNull { i -> lines.any { l -> i in l && l.count { b[it] == p } == 2 && l.count { b[it] == 0 } == 1 } }
        val m = finishing(2) ?: (if (Random.nextFloat() < 0.75f) finishing(1) else null) ?: (4.takeIf { it in free }) ?: free.random()
        b[m] = 2
    }
    val w = winner()
    LaunchedEffect(w) { if (w == 1) Kids.addStars(kid.id, 2) else if (w == 3) Kids.addStars(kid.id, 1) }
    GameFrame("إكس أو", onDone) {
        Text(when (w) { 1 -> "كسبت! 🎉"; 2 -> "الموبايل كسب المرة دي 😅"; 3 -> "تعادل 🤝"; else -> "إنت ❌ والموبايل ⭕" }, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = GInk)
        Spacer(Modifier.height(16.dp))
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Column(Modifier.fillMaxWidth().aspectRatio(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                (0 until 3).forEach { r ->
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        (0 until 3).forEach { c ->
                            val i = r * 3 + c
                            Box(
                                Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(16.dp)).background(Color.White)
                                    .clickable(enabled = b[i] == 0 && w == 0) { b[i] = 1; if (winner() == 0) phoneMove() },
                                contentAlignment = Alignment.Center,
                            ) { androidx.compose.material3.Text(when (b[i]) { 1 -> "❌"; 2 -> "⭕"; else -> "" }, fontSize = 48.sp) }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        if (w != 0) WinBox(if (w == 1) "برافو! ⭐⭐" else "جرّب تاني!", onAgain = { round++ }, onDone = onDone)
    }
}

// ================================================================= pop the balloons in order

@Composable
private fun BalloonGame(kid: Kid, onDone: () -> Unit) {
    var round by remember { mutableIntStateOf(0) }
    val count = 8 + (round * 2).coerceAtMost(8)
    val spots = remember(round) { List(count) { Random.nextFloat() * 0.8f to Random.nextFloat() * 0.85f } }
    val popped = remember(round) { mutableStateListOf<Int>() }
    var miss by remember(round) { mutableStateOf(false) }
    val done = popped.size == count
    LaunchedEffect(done) { if (done) Kids.addStars(kid.id, 2) }
    GameFrame("فرقع البالونات", onDone) {
        Text(if (done) "برافو! 🎈" else "فرقعهم بالترتيب: ${popped.size + 1}", fontWeight = FontWeight.Bold, fontSize = 22.sp, color = if (miss) Warn else GInk)
        Spacer(Modifier.height(8.dp))
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(20.dp)).background(Color(0xFFE3F2FD))) {
            val w = maxWidth; val h = maxHeight
            spots.forEachIndexed { i, (x, y) ->
                if (i !in popped) Box(
                    Modifier.offset(w * x, h * y).size(62.dp).clickable { if (i == popped.size) { popped.add(i); miss = false } else miss = true },
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.material3.Text("🎈", fontSize = 52.sp)
                    Text("${i + 1}", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 18.sp, modifier = Modifier.padding(bottom = 10.dp))
                }
            }
        }
        if (done) { Spacer(Modifier.height(8.dp)); WinBox("فرقعت $count بالونة 🎈", onAgain = { round++ }, onDone = onDone) }
    }
}
