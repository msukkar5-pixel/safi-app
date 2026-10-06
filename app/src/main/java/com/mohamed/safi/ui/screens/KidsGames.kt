package com.mohamed.safi.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
        else -> onDone()
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
