package com.mohamed.safi.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mohamed.safi.quiz.Challenge
import com.mohamed.safi.quiz.Question
import com.mohamed.safi.quiz.Quiz
import com.mohamed.safi.family.Family
import com.mohamed.safi.ui.*
import kotlinx.coroutines.delay

/**
 * [lives] > 0: lose one per mistake (3 lives). [timeAttack]: one 60-second clock for the whole game.
 * [players]: pass-and-play, players take turns. [ladder]: 15 questions, harder each step, safe points at 5 and 10.
 */
private data class Game(
    val mode: String, val title: String, val questions: List<Question>, val suddenDeath: Boolean = false, val stage: Int = 0,
    val lives: Int = 0, val timeAttack: Boolean = false, val players: List<String> = emptyList(), val ladder: Boolean = false,
    val ch: Challenge.Code? = null,
)
private data class Answer(val q: Question, val picked: Int?, val points: Int, val player: Int = 0)

private val ladderLevels = listOf(1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3)

/** Short beeps for right/wrong, if the user didn't mute them. */
private object QuizSound {
    private val tone by lazy { runCatching { android.media.ToneGenerator(android.media.AudioManager.STREAM_MUSIC, 60) }.getOrNull() }
    fun ok() { if (Quiz.sound) runCatching { tone?.startTone(android.media.ToneGenerator.TONE_PROP_ACK, 120) } }
    fun bad() { if (Quiz.sound) runCatching { tone?.startTone(android.media.ToneGenerator.TONE_PROP_NACK, 160) } }
}

@Composable
fun QuizScreen(onBack: () -> Unit) {
    var screen by remember { mutableStateOf("home") }
    var game by remember { mutableStateOf<Game?>(null) }
    var result by remember { mutableStateOf<List<Answer>?>(null) }
    var rev by remember { mutableIntStateOf(0) }

    fun start(g: Game) { if (g.questions.isEmpty()) return; game = g; result = null; screen = "play" }

    when (screen) {
        "play" -> game?.let { g ->
            QuizPlay(g, onQuit = { screen = "home"; rev++ }) { answers -> result = answers; screen = "result" }
            return
        }
        "result" -> { val g = game; val r = result; if (g != null && r != null) { QuizResult(g, r, onAgain = { start(rebuild(g)) }) { screen = "home"; rev++ }; return } }
        "cats" -> { QuizCategories(onBack = { screen = "home" }) { cats, lvl, title -> start(Game("cat", title, Quiz.pick(10, cats, lvl))) }; return }
        "stages" -> { QuizStages(onBack = { screen = "home" }) { n -> start(Game("stage", "المرحلة $n", Quiz.stageQuestions(n), stage = n)) }; return }
        "stats" -> { QuizStats { screen = "home" }; return }
        "remote" -> { ChallengeHub(onBack = { screen = "home"; rev++ }) { c -> start(Game("challenge", challengeTitle(c), Challenge.questions(c), ch = c)) }; return }
    }

    // a friend's challenge that was shared into the app
    val incoming = Challenge.incoming.value
    if (incoming != null) {
        val ctx = androidx.compose.ui.platform.LocalContext.current
        AlertDialog(
            onDismissRequest = { Challenge.incoming.value = null },
            title = { Text("⚔️ ${incoming.from.ifBlank { tr("صاحبك") }} بيتحداك!") },
            text = { Text("جاب ${incoming.correct} من ${incoming.qs.size}. هتلعب نفس الأسئلة بالظبط، وبعدها ابعتله نتيجتك.") },
            confirmButton = {
                Button(onClick = {
                    Challenge.incoming.value = null
                    if (Challenge.played(incoming)) toast(ctx, "لعبت التحدي ده قبل كده") else start(Game("challenge", challengeTitle(incoming), Challenge.questions(incoming), ch = incoming))
                }) { Text("يلا ألعب") }
            },
            dismissButton = { TextButton(onClick = { Challenge.incoming.value = null }) { Text("بعدين") } },
        )
    }

    val bank = remember { Quiz.load() }
    var friends by remember { mutableStateOf(false) }
    if (friends) FriendsDialog({ friends = false }) { names ->
        friends = false
        start(Game("friends", "تحدّي ${names.joinToString(" × ")}", Quiz.pick(names.size * 8), players = names))
    }
    key(rev) {
        ScreenScaffold("مسابقة صافي", onBack = onBack) { pad ->
            LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Brush.linearGradient(listOf(BrandDeep, Brand)))) {
                        IslamicPattern(Modifier.matchParentSize(), GoldSoft.copy(alpha = 0.10f))
                        Column(Modifier.padding(20.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(56.dp).clip(CircleShape).background(Gold), contentAlignment = Alignment.Center) {
                                    Text("${Quiz.level()}", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 24.sp)
                                }
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("المستوى ${Quiz.level()}", color = Color.White, fontFamily = Amiri, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                                    LinearProgressIndicator(progress = { Quiz.levelProgress() }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                                        color = Gold, trackColor = Color.White.copy(alpha = 0.2f), drawStopIndicator = {})
                                    Text("${Quiz.xp} نقطة خبرة", color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            Row {
                                HeroStat("🔥 ${Quiz.streak}", "أيام لعب", Modifier.weight(1f))
                                HeroStat("${Quiz.best("quick")}", "أعلى سكور", Modifier.weight(1f))
                                HeroStat("${Quiz.stage - 1}", "مراحل خلصت", Modifier.weight(1f))
                            }
                        }
                    }
                }
                if (bank.isEmpty()) item { EmptyState(Icons.Default.Quiz, "الأسئلة بتتجهز") }
                item {
                    GoldCard(onClick = if (Quiz.dailyDoneToday) null else ({ start(Game("daily", "التحدي اليومي", Quiz.daily())) })) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.EmojiEvents, null, tint = Gold, modifier = Modifier.size(36.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("التحدي اليومي", fontFamily = Amiri, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                                Text(if (Quiz.dailyDoneToday) "خلّصته النهارده: ${Quiz.dailyScore} نقطة. استنى بكرة!" else "١٠ أسئلة جديدة كل يوم، مرة واحدة بس",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                            }
                            if (!Quiz.dailyDoneToday) Icon(Icons.Default.PlayArrow, null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ModeCard("تحدي سريع", "١٠ أسئلة منوّعة", Icons.Default.Bolt, Color(0xFFE09F3E), Modifier.weight(1f)) { start(Game("quick", "تحدي سريع", Quiz.pick(10))) }
                        ModeCard("المراحل", "اطلع مرحلة مرحلة", Icons.Default.Stairs, Brand, Modifier.weight(1f)) { screen = "stages" }
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ModeCard("حسب القسم", "ديني أو عام وتختار الصعوبة", Icons.Default.Category, Color(0xFF5C6BC0), Modifier.weight(1f)) { screen = "cats" }
                        ModeCard("الموت المفاجئ", "أول غلطة تخسر", Icons.Default.Whatshot, Danger, Modifier.weight(1f)) {
                            start(Game("sudden", "الموت المفاجئ", Quiz.pick(60, levels = List(60) { i -> if (i < 10) 1 else if (i < 25) 2 else 3 }), suddenDeath = true))
                        }
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ModeCard("أسئلة دينية", "قرآن، سيرة، صحابة، فقه", Icons.Default.MenuBook, Color(0xFF1B5E20), Modifier.weight(1f)) {
                            start(Game("religion", "أسئلة دينية", Quiz.pick(10, Quiz.religionCats.keys)))
                        }
                        ModeCard("إحصائياتي", "دقتك في كل قسم", Icons.Default.BarChart, Color(0xFF6C7A89), Modifier.weight(1f)) { screen = "stats" }
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ModeCard("٣ أرواح", "كل غلطة بروح، لحد إمتى هتصمد؟", Icons.Default.Favorite, Color(0xFFD81B60), Modifier.weight(1f)) {
                            start(Game("lives", "٣ أرواح", Quiz.pick(80, levels = List(80) { i -> if (i < 8) 1 else if (i < 25) 2 else 3 }), lives = 3))
                        }
                        ModeCard("سباق الدقيقة", "أكبر عدد صح في ٦٠ ثانية", Icons.Default.Timer, Color(0xFF00897B), Modifier.weight(1f)) {
                            start(Game("time", "سباق الدقيقة", Quiz.pick(60), timeAttack = true))
                        }
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ModeCard("سلّم الأبطال", "١٥ سؤال من السهل للصعب", Icons.Default.Stairs, Color(0xFF8E24AA), Modifier.weight(1f)) {
                            start(Game("ladder", "سلّم الأبطال", Quiz.pick(15, levels = ladderLevels), ladder = true))
                        }
                        ModeCard("تحدّي صاحبك", "اتنين على نفس الموبايل بالدور", Icons.Default.Groups, Color(0xFF3949AB), Modifier.weight(1f)) { friends = true }
                    }
                }
                item {
                    GoldCard(onClick = { screen = "remote" }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("⚔️", fontSize = 32.sp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("تحدّي أصحابك من أي مكان", fontFamily = Amiri, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                                Text("العب ١٠ أسئلة وابعت التحدي بواتساب. صاحبك يلعب نفس الأسئلة ويبعتلك نتيجته.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                            }
                            Icon(Icons.Default.ChevronLeft, null)
                        }
                    }
                }
                item { Badges() }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("أصوات المسابقة", Modifier.weight(1f))
                        var snd by remember { mutableStateOf(Quiz.sound) }
                        Switch(snd, { snd = it; Quiz.sound = it })
                    }
                }
                item { Text("${bank.size} سؤال • الأسئلة الدينية من مصادر أهل السنة (القرآن والصحيحين وكتب السيرة المعتمدة)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline) }
            }
        }
    }
}

private fun rebuild(g: Game): Game = when (g.mode) {
    "lives" -> g.copy(questions = Quiz.pick(80, levels = List(80) { i -> if (i < 8) 1 else if (i < 25) 2 else 3 }))
    "time" -> g.copy(questions = Quiz.pick(60))
    "ladder" -> g.copy(questions = Quiz.pick(15, levels = ladderLevels))
    "friends" -> g.copy(questions = Quiz.pick(g.players.size * 8))
    "stage" -> g.copy(questions = Quiz.stageQuestions(g.stage))
    "sudden" -> g.copy(questions = Quiz.pick(60, levels = List(60) { i -> if (i < 10) 1 else if (i < 25) 2 else 3 }))
    "religion" -> g.copy(questions = Quiz.pick(10, Quiz.religionCats.keys))
    "daily", "challenge" -> g.copy(mode = "quick", title = "تحدي سريع", questions = Quiz.pick(10), ch = null)
    else -> g.copy(questions = Quiz.pick(10, g.questions.map { it.cat }.toSet().takeIf { g.mode == "cat" }))
}

@Composable
private fun HeroStat(value: String, label: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Text(label, color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun ModeCard(title: String, sub: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = modifier.height(120.dp), shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f)), border = BorderStroke(1.dp, color.copy(alpha = 0.35f))) {
        Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Icon(icon, null, tint = color, modifier = Modifier.size(30.dp))
            Column {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, maxLines = 2)
            }
        }
    }
}

// ============================================================================ play

@Composable
private fun QuizPlay(g: Game, onQuit: () -> Unit, onFinish: (List<Answer>) -> Unit) {
    val haptic = LocalHapticFeedback.current
    var idx by remember { mutableIntStateOf(0) }
    var picked by remember { mutableStateOf<Int?>(null) }
    var removed by remember { mutableStateOf(setOf<Int>()) }
    var used5050 by remember { mutableStateOf(false) }
    var usedSkip by remember { mutableStateOf(false) }
    var usedTime by remember { mutableStateOf(false) }
    var combo by remember { mutableIntStateOf(0) }
    var score by remember { mutableIntStateOf(0) }
    val answers = remember { mutableStateListOf<Answer>() }
    var confirmQuit by remember { mutableStateOf(false) }
    val q = g.questions.getOrNull(idx)
    val total = if (g.suddenDeath) g.questions.size else g.questions.size
    val limit = if ((q?.lvl ?: 1) >= 3) 30 else 20
    var left by remember(idx) { mutableIntStateOf(limit) }
    var lastPoints by remember { mutableIntStateOf(0) }
    var lives by remember { mutableIntStateOf(g.lives) }
    var clock by remember { mutableIntStateOf(60) }
    var finished by remember { mutableStateOf(false) }
    val player = if (g.players.isEmpty()) 0 else idx % g.players.size

    BackHandler { confirmQuit = true }

    fun finish() { if (!finished) { finished = true; onFinish(answers.toList()) } }
    fun next() {
        val lastWrong = answers.lastOrNull()?.let { it.picked != it.q.correct } == true
        if ((g.suddenDeath || g.ladder) && lastWrong) { finish(); return }
        if (g.lives > 0 && lives <= 0) { finish(); return }
        if (idx + 1 >= g.questions.size) finish() else { idx++; picked = null; removed = emptySet() }
    }
    // one clock for the whole game in the 60-second race
    if (g.timeAttack) LaunchedEffect(Unit) {
        while (clock > 0) { delay(1000); clock-- }
        finish()
    }
    fun answer(i: Int?) {
        val qq = q ?: return
        if (picked != null) return
        picked = i ?: -1
        val ok = i == qq.correct
        haptic.performHapticFeedback(if (ok) HapticFeedbackType.TextHandleMove else HapticFeedbackType.LongPress)
        if (ok) QuizSound.ok() else QuizSound.bad()
        combo = if (ok) combo + 1 else 0
        val pts = if (ok) Quiz.points(qq.lvl, if (g.timeAttack) 10 else left, combo) else 0
        lastPoints = pts
        score += pts
        answers += Answer(qq, i, pts, player)
        Quiz.record(qq, ok)
        if (!ok && g.lives > 0) lives--
    }
    // the race moves on by itself
    LaunchedEffect(idx, picked) {
        if (g.timeAttack && picked != null) { delay(450); next() }
    }
    LaunchedEffect(idx, picked) {
        if (g.timeAttack) return@LaunchedEffect
        if (picked != null) return@LaunchedEffect
        while (left > 0 && picked == null) { delay(1000); if (picked == null) left-- }
        if (picked == null && left <= 0) answer(null)
    }
    if (q == null) { LaunchedEffect(Unit) { finish() }; return }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { confirmQuit = true }) { Icon(Icons.Default.Close, "خروج") }
                Column(Modifier.weight(1f)) {
                    Text(g.title, fontWeight = FontWeight.Bold)
                    Text(if (g.suddenDeath) "سؤال ${idx + 1}" else "سؤال ${idx + 1} من $total", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("$score", fontWeight = FontWeight.Bold, fontSize = 22.sp, color = MaterialTheme.colorScheme.primary)
                    if (combo >= 3) Text("x${if (combo >= 5) 3 else 2} 🔥", color = Warn, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                }
            }
            if (!g.suddenDeath && g.lives == 0 && !g.timeAttack) LinearProgressIndicator(progress = { (idx + 1f) / total }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).height(5.dp).clip(CircleShape), drawStopIndicator = {})
            if (g.players.isNotEmpty()) {
                val scores = g.players.indices.map { p -> answers.filter { it.player == p }.sumOf { it.points } }
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    g.players.forEachIndexed { p, n ->
                        Surface(shape = RoundedCornerShape(12.dp), color = if (p == player) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (p == player) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f)) {
                            Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                androidx.compose.material3.Text(n, fontWeight = FontWeight.Bold, maxLines = 1)
                                Text("${scores[p]}", fontSize = 18.sp)
                            }
                        }
                    }
                }
                Text("الدور على: ${g.players[player]}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.align(Alignment.CenterHorizontally))
            }
            if (g.lives > 0) Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.Center) {
                repeat(g.lives) { i -> Text(if (i < lives) "❤️" else "🤍", fontSize = 24.sp) }
                Spacer(Modifier.width(10.dp))
                Text("صح: ${answers.count { it.picked == it.q.correct }}", fontWeight = FontWeight.Bold)
            }
            if (g.ladder) Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                repeat(15) { i ->
                    val safe = i == 4 || i == 9
                    Box(Modifier.weight(1f).height(if (safe) 12.dp else 8.dp).clip(CircleShape).background(
                        when { i < idx -> Positive; i == idx -> Gold; safe -> Gold.copy(alpha = 0.35f); else -> MaterialTheme.colorScheme.surfaceVariant },
                    ))
                }
            }
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (g.timeAttack) TimerRing(clock, 60, true) else TimerRing(left, limit, picked == null)
            }
            Spacer(Modifier.height(12.dp))
            GoldCard {
                Row {
                    Pill(Quiz.allCats[q.cat] ?: q.cat, MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(6.dp))
                    Pill(when (q.lvl) { 1 -> "سهل"; 2 -> "متوسط"; else -> "صعب" }, when (q.lvl) { 1 -> Positive; 2 -> Warn; else -> Danger })
                }
                Spacer(Modifier.height(10.dp))
                Text(q.q, fontFamily = Amiri, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 36.sp)
            }
            Spacer(Modifier.height(12.dp))
            q.answers.forEachIndexed { i, a ->
                if (i in removed) { Spacer(Modifier.height(64.dp)); return@forEachIndexed }
                val state = when {
                    picked == null -> 0
                    i == q.correct -> 1
                    i == picked -> 2
                    else -> 3
                }
                val bg by animateColorAsState(when (state) { 1 -> Positive.copy(alpha = 0.22f); 2 -> Danger.copy(alpha = 0.22f); else -> MaterialTheme.colorScheme.surfaceContainerLow }, label = "bg")
                val border = when (state) { 1 -> Positive; 2 -> Danger; else -> MaterialTheme.colorScheme.outlineVariant }
                Card(
                    onClick = { answer(i) }, enabled = picked == null,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).heightIn(min = 56.dp),
                    shape = RoundedCornerShape(16.dp), border = BorderStroke(1.5.dp, border),
                    colors = CardDefaults.cardColors(containerColor = bg, disabledContainerColor = bg, disabledContentColor = MaterialTheme.colorScheme.onSurface),
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(28.dp).clip(CircleShape).background(border.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                            Text("${"أبجد"[i]}", fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(a, Modifier.weight(1f), fontSize = 17.sp)
                        if (state == 1) Icon(Icons.Default.CheckCircle, null, tint = Positive)
                        if (state == 2) Icon(Icons.Default.Cancel, null, tint = Danger)
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            if (picked == null && !g.timeAttack) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Lifeline("50:50", Icons.Default.ContentCut, !used5050, Modifier.weight(1f)) {
                        used5050 = true
                        removed = q.answers.indices.filter { it != q.correct }.shuffled().take(2).toSet()
                    }
                    Lifeline("تخطي", Icons.Default.SkipNext, !usedSkip && !g.suddenDeath, Modifier.weight(1f)) {
                        usedSkip = true; idx++; removed = emptySet()
                        if (idx >= g.questions.size) finish()
                    }
                    Lifeline("+١٥ ثانية", Icons.Default.MoreTime, !usedTime, Modifier.weight(1f)) { usedTime = true; left += 15 }
                }
            } else if (picked != null && !g.timeAttack) {
                val ok = picked == q.correct
                AppCard(color = if (ok) Positive.copy(alpha = 0.12f) else Danger.copy(alpha = 0.10f)) {
                    Text(if (ok) "صح! +$lastPoints" else if (picked == -1) "الوقت خلص" else "غلط", fontWeight = FontWeight.Bold, color = if (ok) Positive else Danger, fontSize = 18.sp)
                    if (!ok) Text("الإجابة: ${q.answers[q.correct]}", fontWeight = FontWeight.SemiBold)
                    if (q.exp.isNotBlank()) Text(q.exp, style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(8.dp))
                Button(onClick = { next() }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Text(if ((g.suddenDeath || g.ladder) && !ok) "النتيجة" else if (g.lives > 0 && lives <= 0) "النتيجة" else if (idx + 1 >= g.questions.size) "النتيجة" else "التالي", fontSize = 18.sp)
                }
            }
        }
    }
    if (confirmQuit) ConfirmDialog("تخرج من اللعبة؟", "النقط اللي جمعتها مش هتتحسب.", "اخرج", { confirmQuit = false }) { onQuit() }
}

@Composable
private fun TimerRing(left: Int, limit: Int, running: Boolean) {
    val frac by animateFloatAsState(left.toFloat() / limit, tween(900, easing = LinearEasing), label = "t")
    val color = if (left <= 5) Danger else if (left <= 10) Warn else Positive
    val track = MaterialTheme.colorScheme.surfaceVariant
    Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val s = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round)
            drawArc(track, 0f, 360f, false, style = s, size = Size(size.width, size.height))
            drawArc(color, -90f, 360f * frac, false, style = s, size = Size(size.width, size.height))
        }
        Text("$left", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = if (running) color else MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun Lifeline(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 10.dp)) {
        Icon(icon, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

// ============================================================================ result

@Composable
private fun QuizResult(g: Game, answers: List<Answer>, onAgain: () -> Unit, onHome: () -> Unit) {
    val correct = answers.count { it.picked == it.q.correct }
    val score = answers.sumOf { it.points }
    val accuracy = if (answers.isEmpty()) 0 else correct * 100 / answers.size
    val stars = when { accuracy >= 90 -> 3; accuracy >= 70 -> 2; accuracy >= 40 -> 1; else -> 0 }
    var newBest by remember { mutableStateOf(false) }
    var review by remember { mutableStateOf(false) }
    var xpGain by remember { mutableIntStateOf(0) }
    var newBadges by remember { mutableStateOf<List<Quiz.Badge>>(emptyList()) }
    LaunchedEffect(Unit) {
        val bestKey = if (g.mode == "cat") "cat" else g.mode
        if (score > Quiz.best(bestKey)) { Quiz.setBest(bestKey, score); newBest = score > 0 }
        xpGain = score / 2 + correct * 5
        Quiz.addXp(xpGain)
        if (g.mode == "daily") Quiz.markDaily(score)
        if (g.mode == "stage") { Quiz.setStageStars(g.stage, stars); if (correct >= 7 && g.stage >= Quiz.stage) Quiz.stage = g.stage + 1 }
        var run = 0; var best = 0
        answers.forEach { a -> run = if (a.picked == a.q.correct) run + 1 else 0; best = maxOf(best, run) }
        val earned = buildList {
            add("first")
            if (answers.size == 10 && correct == 10) add("perfect")
            if (best >= 5) add("combo5")
            if (best >= 10) add("combo10")
            if (g.mode == "lives" && correct >= 20) add("survivor")
            if (g.mode == "time" && correct >= 15) add("speed15")
            if (g.mode == "ladder" && correct >= 15) add("ladder")
            if (g.mode == "friends" || g.mode == "challenge") add("friend")
            if (Quiz.catStats("quran").first >= 50) add("quran50")
            if (Quiz.level() >= 5) add("level5")
            if (Quiz.level() >= 10) add("level10")
            if (Quiz.streak >= 7) add("streak7")
        }
        newBadges = Quiz.unlock(earned)
    }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val sendCode = remember { g.ch?.let { Challenge.afterPlay(it, correct, score) } }
    BackHandler { onHome() }
    if (review) {
        BackHandler { review = false }
        ScreenScaffold("راجع إجاباتك", onBack = { review = false }) { pad ->
            LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(answers) { i, a ->
                    val ok = a.picked == a.q.correct
                    AppCard {
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(if (ok) Icons.Default.CheckCircle else Icons.Default.Cancel, null, tint = if (ok) Positive else Danger)
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text("${i + 1}. ${a.q.q}", fontWeight = FontWeight.SemiBold)
                                if (!ok) Text("إجابتك: ${a.picked?.takeIf { it >= 0 }?.let { a.q.answers[it] } ?: "ما جاوبتش"}", color = Danger, style = MaterialTheme.typography.bodySmall)
                                Text("الصح: ${a.q.answers[a.q.correct]}", color = Positive, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                if (a.q.exp.isNotBlank()) Text(a.q.exp, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                            }
                        }
                    }
                }
            }
        }
        return
    }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(24.dp))
        Text(g.title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        Row { repeat(3) { i -> Icon(if (i < stars) Icons.Default.Star else Icons.Default.StarBorder, null, tint = Gold, modifier = Modifier.size(52.dp)) } }
        Spacer(Modifier.height(8.dp))
        Text("$score", fontSize = 56.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text("نقطة", color = MaterialTheme.colorScheme.outline)
        if (newBest) Text("🏆 رقم قياسي جديد!", color = Gold, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(top = 6.dp))
        if (g.players.isNotEmpty()) {
            val scores = g.players.indices.map { p -> answers.filter { it.player == p }.sumOf { it.points } }
            val top = scores.maxOrNull() ?: 0
            val winners = g.players.filterIndexed { i, _ -> scores[i] == top }
            Text(if (winners.size > 1) "🤝 تعادل!" else "🏆 الفايز: ${winners.first()}", color = Gold, fontWeight = FontWeight.Bold, fontSize = 22.sp, modifier = Modifier.padding(top = 6.dp))
            g.players.forEachIndexed { i, n -> Text("$n: ${scores[i]}", fontWeight = FontWeight.SemiBold) }
        }
        g.ch?.let { c ->
            if (c.fromId != Challenge.myId) {
                val r = compareValuesBy(correct to score, c.correct to c.score, { it.first }, { it.second })
                Text(if (r > 0) "🏆 كسبت ${c.from}!" else if (r < 0) "${c.from} كسب المرة دي 💪" else "🤝 تعادل!", color = Gold, fontWeight = FontWeight.Bold, fontSize = 22.sp, modifier = Modifier.padding(top = 6.dp))
                Text("انت $correct • ${c.from} ${c.correct}", fontWeight = FontWeight.SemiBold)
            }
            Button(onClick = {
                val msg = if (c.fromId == Challenge.myId) tr("⚔️ بتحداك في مسابقة ${com.mohamed.safi.AppName.v}! جبت $correct من ${answers.size}. افتح الرسالة دي بـ${com.mohamed.safi.AppName.v} (مشاركة ← ${com.mohamed.safi.AppName.v}) والعب نفس الأسئلة:")
                    else tr("نتيجتي في تحديك: $correct من ${answers.size}. شاركها مع ${com.mohamed.safi.AppName.v} عشان تتسجل:")
                ctx.startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(android.content.Intent.EXTRA_TEXT, msg + "\n" + sendCode), tr("ابعت")).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
            }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp).height(52.dp), colors = ButtonDefaults.buttonColors(containerColor = Positive)) {
                Icon(Icons.Default.Send, null); Spacer(Modifier.width(6.dp))
                Text(if (c.fromId == Challenge.myId) "ابعت التحدي لأصحابك" else "ابعت نتيجتك لـ ${c.from}", fontSize = 17.sp)
            }
        }
        if (g.ladder) Text(
            when { correct >= 15 -> "وصلت لقمة السلّم! 🏆"; correct >= 10 -> "عديت محطة الأمان التانية"; correct >= 5 -> "عديت محطة الأمان الأولى"; else -> "حاول توصل لمحطة الأمان (السؤال ٥)" },
            fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp),
        )
        newBadges.forEach { b ->
            Surface(shape = RoundedCornerShape(14.dp), color = Gold.copy(alpha = 0.18f), modifier = Modifier.padding(top = 8.dp)) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(b.icon, fontSize = 24.sp); Spacer(Modifier.width(8.dp))
                    Column { Text("إنجاز جديد: ${b.title}", fontWeight = FontWeight.Bold); Text(b.how, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        GoldCard {
            Row {
                StatBlock("صح", "$correct/${answers.size}", Modifier.weight(1f))
                StatBlock("الدقة", "$accuracy%", Modifier.weight(1f))
                StatBlock("خبرة", "+$xpGain", Modifier.weight(1f))
            }
        }
        if (g.mode == "stage") Text(if (correct >= 7) "المرحلة ${g.stage + 1} اتفتحت" else "محتاج ٧ من ١٠ علشان تفتح المرحلة الجاية", modifier = Modifier.padding(top = 10.dp),
            color = if (correct >= 7) Positive else MaterialTheme.colorScheme.outline, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.weight(1f))
        OutlinedButton(onClick = { review = true }, modifier = Modifier.fillMaxWidth()) { Text("راجع إجاباتك") }
        Spacer(Modifier.height(8.dp))
        Button(onClick = onAgain, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text(if (g.mode == "daily") "العب تحدي سريع" else "العب تاني", fontSize = 18.sp) }
        TextButton(onClick = onHome) { Text("الرئيسية") }
    }
    if (accuracy >= 90 || newBadges.isNotEmpty()) Confetti()
}

// ============================================================================ categories, stages, stats

@Composable
private fun QuizCategories(onBack: () -> Unit, onStart: (Set<String>, Int?, String) -> Unit) {
    var lvl by remember { mutableStateOf<Int?>(null) }
    BackHandler { onBack() }
    ScreenScaffold("اختار القسم", onBack = onBack) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(null to "كل المستويات", 1 to "سهل", 2 to "متوسط", 3 to "صعب").forEach { (l, t) -> FilterChip(lvl == l, { lvl = l }, label = { Text(t) }) }
                }
            }
            item { SectionTitle("ديني") }
            item { CatGrid(Quiz.religionCats) { k, n -> onStart(setOf(k), lvl, n) } }
            item { SectionTitle("ثقافة عامة") }
            item { CatGrid(Quiz.generalCats) { k, n -> onStart(setOf(k), lvl, n) } }
        }
    }
}

@Composable
private fun CatGrid(cats: Map<String, String>, onPick: (String, String) -> Unit) {
    val counts = remember { Quiz.load().groupingBy { it.cat }.eachCount() }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        cats.entries.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (k, n) ->
                    val (ok, all) = Quiz.catStats(k)
                    AppCard(onClick = { onPick(k, n) }, modifier = Modifier.weight(1f)) {
                        Text(n, fontWeight = FontWeight.Bold)
                        Text("${counts[k] ?: 0} سؤال" + if (all > 0) " • دقتك ${ok * 100 / all}%" else "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun QuizStages(onBack: () -> Unit, onPlay: (Int) -> Unit) {
    BackHandler { onBack() }
    val unlocked = Quiz.stage
    ScreenScaffold("المراحل", onBack = onBack) { pad ->
        LazyVerticalGrid(GridCells.Fixed(4), Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items((1..30).toList()) { n ->
                val open = n <= unlocked
                val stars = Quiz.stageStars(n)
                Card(onClick = { if (open) onPlay(n) }, enabled = open, shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = if (n == unlocked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        if (open) Text("$n", fontWeight = FontWeight.Bold, fontSize = 22.sp) else Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.outline)
                        Row { repeat(3) { i -> Icon(Icons.Default.Star, null, tint = if (i < stars) Gold else MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.size(12.dp)) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuizStats(onBack: () -> Unit) {
    BackHandler { onBack() }
    ScreenScaffold("إحصائياتي", onBack = onBack) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                GoldCard {
                    Row {
                        StatBlock("المستوى", "${Quiz.level()}", Modifier.weight(1f))
                        StatBlock("الخبرة", "${Quiz.xp}", Modifier.weight(1f))
                        StatBlock("أطول موت مفاجئ", "${Quiz.best("sudden")}", Modifier.weight(1f))
                    }
                }
            }
            items(Quiz.allCats.entries.toList()) { (k, n) ->
                val (ok, all) = Quiz.catStats(k)
                val f = if (all == 0) 0f else ok.toFloat() / all
                BarRow(n, if (all == 0) "—" else "${(f * 100).toInt()}% ($ok/$all)", f, if (f >= 0.7f) Positive else if (f >= 0.4f) Warn else Danger)
            }
        }
    }
}


// ============================================================================ remote friends challenge

private fun challengeTitle(c: Challenge.Code) = if (c.fromId == Challenge.myId) tr("تحدّي الأصحاب") else tr("تحدّي ${c.from}")

@Composable
private fun ChallengeHub(onBack: () -> Unit, onPlay: (Challenge.Code) -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    @Suppress("UNUSED_VARIABLE") val v = Challenge.version.intValue
    var name by remember { mutableStateOf(Challenge.myName) }
    var cats by remember { mutableStateOf("all") }
    var code by remember { mutableStateOf("") }
    fun open(text: String) {
        if (Challenge.containsCapsule(text) || Challenge.containsFamilyCapsule(text)) {
            val added = Challenge.importCapsule(text)
            code = ""
            toast(ctx, if (added > 0) "اتضافت $added نتيجة جديدة للترتيب" else "الكبسولة غير صالحة أو موجودة عندك قبل كده")
            return
        }
        val c = Challenge.decode(text) ?: run { toast(ctx, "ده مش كود تحدي ${com.mohamed.safi.AppName.v}"); return }
        if (c.reply) {
            when (Challenge.importReply(c)) {
                null -> toast(ctx, "النتيجة دي متسجلة قبل كده أو مش لتحدي بعته")
                1 -> toast(ctx, "🏆 كسبت ${c.from}!")
                0 -> toast(ctx, "🤝 تعادل مع ${c.from}")
                else -> toast(ctx, "${c.from} كسب المرة دي")
            }
            code = ""
        } else if (c.fromId == Challenge.myId) toast(ctx, "ده التحدي بتاعك، ابعته لأصحابك")
        else if (Challenge.played(c)) toast(ctx, "لعبت التحدي ده قبل كده")
        else onPlay(c)
    }
    // a code copied from WhatsApp is picked up when the screen opens
    LaunchedEffect(Unit) {
        val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
        val clip = runCatching { cm?.primaryClip?.getItemAt(0)?.text?.toString() }.getOrNull().orEmpty()
        if (Challenge.contains(clip) || Challenge.containsCapsule(clip) || Challenge.containsFamilyCapsule(clip)) code = clip
    }
    BackHandler { onBack() }
    ScreenScaffold("تحدّي الأصحاب", onBack = onBack) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                AppCard {
                    Text("إزاي بيشتغل؟", fontWeight = FontWeight.Bold)
                    Text("١. تلعب ١٠ أسئلة.\n٢. تبعت كود التحدي لصاحبك أو لجروب بواتساب.\n٣. صاحبك يعمل «مشاركة» للرسالة مع ${com.mohamed.safi.AppName.v} ويلعب نفس الأسئلة.\n٤. يبعتلك نتيجته بنفس الطريقة، وتتسجل عندك في الترتيب.", style = MaterialTheme.typography.bodySmall)
                    Text("من غير سيرفر ومن غير حساب: كل حاجة بتتحفظ على موبايلك بس.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
            }
            item {
                GoldCard {
                    OutlinedTextField(name, { name = it.take(20) }, label = { Text("اسمك اللي أصحابك هيشوفوه") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("all" to "منوّع", "religion" to "ديني", "general" to "عام").forEach { (k, t) -> FilterChip(cats == k, { cats = k }, label = { Text(t) }) }
                    }
                    Button(onClick = {
                        if (name.isBlank()) { toast(ctx, "اكتب اسمك الأول"); return@Button }
                        Challenge.myName = name
                        onPlay(Challenge.create(cats))
                    }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text("ابدأ تحدي جديد") }
                }
            }
            item {
                AppCard {
                    Text("وصلك تحدي أو نتيجة؟", fontWeight = FontWeight.Bold)
                    OutlinedTextField(code, { code = it }, label = { Text("الصق الرسالة هنا") }, maxLines = 3, modifier = Modifier.fillMaxWidth())
                    Button(onClick = {
                        if (name.isNotBlank()) Challenge.myName = name
                        open(code)
                    }, enabled = Challenge.contains(code) || Challenge.containsCapsule(code) || Challenge.containsFamilyCapsule(code), modifier = Modifier.fillMaxWidth()) { Text("افتح") }
                    OutlinedButton(onClick = {
                        val share = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain")
                            .putExtra(android.content.Intent.EXTRA_TEXT, Challenge.exportCapsule())
                        ctx.startActivity(android.content.Intent.createChooser(share, "ابعت كبسولة التحديات"))
                    }, modifier = Modifier.fillMaxWidth()) { Text("شارك تحديثات التحديات") }
                }
            }
            val list = Challenge.friends()
            item { SectionTitle("الترتيب بيني وبين أصحابي") }
            if (list.isEmpty()) item { EmptyState(Icons.Default.EmojiEvents, "لسه مفيش نتايج. ابعت أول تحدي!") }
            itemsIndexed(list, key = { _, f -> f.id }) { i, f ->
                AppCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(when (i) { 0 -> "🥇"; 1 -> "🥈"; 2 -> "🥉"; else -> "${i + 1}" }, fontSize = 22.sp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            androidx.compose.material3.Text(f.name.ifBlank { "?" }, fontWeight = FontWeight.Bold)
                            Text("آخر مرة: ${f.last}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }
                        Text("✅ ${f.wins}  🤝 ${f.draws}  ❌ ${f.losses}", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            if (Family.joined) item { FamilyChallengeBoard() }
        }
    }
}

@Composable
private fun FamilyChallengeBoard() {
    @Suppress("UNUSED_VARIABLE") val familyVersion = Family.version.intValue
    @Suppress("UNUSED_VARIABLE") val challengeVersion = Challenge.version.intValue
    val familyIds = (Family.members().map { it.id } + Family.myId).toSet()
    val events = Challenge.localUpdates().filter { it.participantId in familyIds }.sortedByDescending { it.at }
    val byPlayer = events.groupBy { it.participantId to it.participant.ifBlank { "فرد من العيلة" } }
        .map { (key, rows) ->
            val latest = rows.maxByOrNull { it.at }
            val points = rows.sumOf { it.score }
            key.second to (rows.size to (points to (latest?.correct ?: 0)))
        }.sortedByDescending { it.second.second.first }
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Groups, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text("تقدم العيلة", fontWeight = FontWeight.Bold)
                Text("نتائج التحديات المشفرة بين أفراد العيلة فقط", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
        }
        Spacer(Modifier.height(8.dp))
        if (byPlayer.isEmpty()) {
            Text("لسه مفيش نتيجة عائلية متزامنة.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        } else {
            byPlayer.take(6).forEachIndexed { index, (name, stats) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${index + 1}", modifier = Modifier.width(24.dp), fontWeight = FontWeight.Bold)
                    Text(name, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    Text("${stats.first} جولة • ${stats.second.first} نقطة", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Text("تحديثات العيلة تنتقل تلقائيًا عبر الواي فاي أو بطاقة العيلة، ولا تظهر في ترتيب الأصدقاء.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
    }
}

// ============================================================================ extras

@Composable
private fun FriendsDialog(onDismiss: () -> Unit, onStart: (List<String>) -> Unit) {
    var a by remember { mutableStateOf("") }
    var b by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تحدّي صاحبك") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("كل واحد ياخد سؤال بالدور، ٨ أسئلة لكل واحد. اللي يجمع نقط أكتر يكسب.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(a, { a = it }, label = { Text("اللاعب الأول") }, singleLine = true)
                OutlinedTextField(b, { b = it }, label = { Text("اللاعب التاني") }, singleLine = true)
            }
        },
        confirmButton = { Button(onClick = { onStart(listOf(a.ifBlank { tr("اللاعب ١") }, b.ifBlank { tr("اللاعب ٢") })) }) { Text("يلا") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}

@Composable
private fun Badges() {
    val got = Quiz.badges.filter { Quiz.hasBadge(it.id) }
    GoldCard {
        Text("الإنجازات (${got.size}/${Quiz.badges.size})", fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Quiz.badges.forEach { b ->
                val on = Quiz.hasBadge(b.id)
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(72.dp)) {
                    Text(if (on) b.icon else "🔒", fontSize = 26.sp)
                    Text(b.title, fontSize = 10.sp, textAlign = TextAlign.Center, maxLines = 2,
                        color = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline)
                }
            }
        }
    }
}

/** A short burst of falling confetti for a great result. */
@Composable
private fun Confetti() {
    val anim = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) { anim.animateTo(1f, tween(2600, easing = LinearEasing)) }
    val pieces = remember {
        val r = kotlin.random.Random(System.currentTimeMillis())
        List(70) { Triple(r.nextFloat(), r.nextFloat() * 0.6f + 0.4f, listOf(Gold, Positive, Color(0xFFE53935), Color(0xFF1E88E5), Color(0xFF8E24AA))[r.nextInt(5)]) }
    }
    if (anim.value >= 1f) return
    Canvas(Modifier.fillMaxSize()) {
        val t = anim.value
        pieces.forEachIndexed { i, (x, speed, c) ->
            val y = (t * speed * 1.4f - (i % 7) * 0.05f) * size.height
            if (y < 0f || y > size.height) return@forEachIndexed
            val wob = kotlin.math.sin((t * 12 + i).toDouble()).toFloat() * 14f
            drawRect(c.copy(alpha = 1f - t * 0.6f), androidx.compose.ui.geometry.Offset(x * size.width + wob, y), Size(9f, 14f))
        }
    }
}
