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
import com.mohamed.safi.quiz.Question
import com.mohamed.safi.quiz.Quiz
import com.mohamed.safi.ui.*
import kotlinx.coroutines.delay

private data class Game(val mode: String, val title: String, val questions: List<Question>, val suddenDeath: Boolean = false, val stage: Int = 0)
private data class Answer(val q: Question, val picked: Int?, val points: Int)

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
    }

    val bank = remember { Quiz.load() }
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
                item { Text("${bank.size} سؤال • الأسئلة الدينية من مصادر أهل السنة (القرآن والصحيحين وكتب السيرة المعتمدة)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline) }
            }
        }
    }
}

private fun rebuild(g: Game): Game = when (g.mode) {
    "stage" -> g.copy(questions = Quiz.stageQuestions(g.stage))
    "sudden" -> g.copy(questions = Quiz.pick(60, levels = List(60) { i -> if (i < 10) 1 else if (i < 25) 2 else 3 }))
    "religion" -> g.copy(questions = Quiz.pick(10, Quiz.religionCats.keys))
    "daily" -> g.copy(mode = "quick", title = "تحدي سريع", questions = Quiz.pick(10))
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

    BackHandler { confirmQuit = true }

    fun finish() = onFinish(answers.toList())
    fun next() {
        if (g.suddenDeath && answers.lastOrNull()?.let { it.picked != it.q.correct } == true) { finish(); return }
        if (idx + 1 >= g.questions.size) finish() else { idx++; picked = null; removed = emptySet() }
    }
    fun answer(i: Int?) {
        val qq = q ?: return
        if (picked != null) return
        picked = i ?: -1
        val ok = i == qq.correct
        haptic.performHapticFeedback(if (ok) HapticFeedbackType.TextHandleMove else HapticFeedbackType.LongPress)
        combo = if (ok) combo + 1 else 0
        val pts = if (ok) Quiz.points(qq.lvl, left, combo) else 0
        lastPoints = pts
        score += pts
        answers += Answer(qq, i, pts)
        Quiz.record(qq, ok)
    }
    LaunchedEffect(idx, picked) {
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
            if (!g.suddenDeath) LinearProgressIndicator(progress = { (idx + 1f) / total }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).height(5.dp).clip(CircleShape), drawStopIndicator = {})
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TimerRing(left, limit, picked == null) }
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
            if (picked == null) {
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
            } else {
                val ok = picked == q.correct
                AppCard(color = if (ok) Positive.copy(alpha = 0.12f) else Danger.copy(alpha = 0.10f)) {
                    Text(if (ok) "صح! +$lastPoints" else if (picked == -1) "الوقت خلص" else "غلط", fontWeight = FontWeight.Bold, color = if (ok) Positive else Danger, fontSize = 18.sp)
                    if (!ok) Text("الإجابة: ${q.answers[q.correct]}", fontWeight = FontWeight.SemiBold)
                    if (q.exp.isNotBlank()) Text(q.exp, style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(8.dp))
                Button(onClick = { next() }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Text(if (g.suddenDeath && !ok) "النتيجة" else if (idx + 1 >= g.questions.size) "النتيجة" else "التالي", fontSize = 18.sp)
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
    LaunchedEffect(Unit) {
        val bestKey = if (g.mode == "cat") "cat" else g.mode
        if (score > Quiz.best(bestKey)) { Quiz.setBest(bestKey, score); newBest = score > 0 }
        xpGain = score / 2 + correct * 5
        Quiz.addXp(xpGain)
        if (g.mode == "daily") Quiz.markDaily(score)
        if (g.mode == "stage") { Quiz.setStageStars(g.stage, stars); if (correct >= 7 && g.stage >= Quiz.stage) Quiz.stage = g.stage + 1 }
    }
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
