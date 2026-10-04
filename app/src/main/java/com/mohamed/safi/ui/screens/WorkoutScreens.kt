package com.mohamed.safi.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.mohamed.safi.ai.Claude
import com.mohamed.safi.data.*
import com.mohamed.safi.fitness.*
import com.mohamed.safi.ui.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ---------------------------------------------------------------- Workout plan + logging

@Composable
fun WorkoutTab() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var planJson by remember { mutableStateOf(Fit.prefs.workoutPlan) }
    val plan = remember(planJson) { Fit.parsePlan(planJson) }
    val open by Fit.dao.openSession().collectAsState(null)
    val recent by Fit.dao.sessions(10).collectAsState(emptyList())
    var busy by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<String?>(null) }

    val session = open
    if (session != null) {
        SessionView(session, plan?.days?.firstOrNull { it.name == session.dayName }) { detail = it }
        detail?.let { ExerciseDetailById(it) { detail = null } }
        return
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            AppCard {
                Text("خطة التمرين", fontWeight = FontWeight.Bold)
                Text(
                    if (plan == null) "الذكاء الاصطناعي هيعملك خطة أسبوعية على حسب ملفك وهدفك ووقتك، من تمارين الموسوعة."
                    else "اتعملت ${shortDate(Fit.prefs.workoutPlanDate)} • ${plan.days.size} أيام",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline,
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = {
                    if (!Claude.hasKey) toast(ctx, "اربط ذكاء اصطناعي من الإعدادات")
                    else {
                        busy = true
                        scope.launch {
                            try {
                                Coach.workoutPlan()
                                planJson = Fit.prefs.workoutPlan
                            } catch (e: Exception) {
                                toast(ctx, e.message ?: "فيه مشكلة")
                            }
                            busy = false
                        }
                    }
                }, enabled = !busy) {
                    if (busy) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)); Text("بيجهز الخطة… (دقيقة تقريبًا)") }
                    else Text(if (plan == null) "اعملي خطة" else "اعمل خطة جديدة")
                }
            }
        }
        plan?.let { p ->
            items(p.days) { d ->
                AppCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(d.name, fontWeight = FontWeight.Bold)
                            if (d.focus.isNotBlank()) Text(d.focus, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }
                        Button(onClick = {
                            scope.launch { Fit.dao.upsertSession(WorkoutSession(dayName = d.name)) }
                        }) { Icon(Icons.Default.PlayArrow, null); Text("ابدأ") }
                    }
                    Spacer(Modifier.height(6.dp))
                    d.exercises.forEach { e ->
                        Row(
                            Modifier.fillMaxWidth().clickable(enabled = e.id.isNotBlank()) { detail = e.id }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("• " + (Fit.arName(e.id) ?: e.name), Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${e.sets}×${e.reps}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
            if (p.cardio.isNotBlank()) item { AppCard { Text("الكارديو", fontWeight = FontWeight.Bold); Text(p.cardio) } }
            if (p.notes.isNotBlank()) item { AppCard { Text("ملاحظات", fontWeight = FontWeight.Bold); Text(p.notes) } }
        }
        item {
            OutlinedButton(onClick = { scope.launch { Fit.dao.upsertSession(WorkoutSession(dayName = "تمرين حر")) } }, modifier = Modifier.fillMaxWidth()) {
                Text("ابدأ تمرين من غير خطة")
            }
        }
        if (recent.isNotEmpty()) {
            item { SectionTitle("آخر التمارين") }
            items(recent.filter { it.end != null }, key = { "s" + it.id }) { s ->
                var count by remember(s.id) { mutableIntStateOf(0) }
                LaunchedEffect(s.id) { count = Fit.dao.setCount(s.id) }
                AppCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(s.dayName, fontWeight = FontWeight.SemiBold)
                            Text(
                                dateTimeStr(s.start) + (s.end?.let { " • ${(it - s.start) / 60_000} دقيقة" } ?: ""),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline,
                            )
                        }
                        Text("$count مجموعة")
                    }
                }
            }
        }
    }
    detail?.let { ExerciseDetailById(it) { detail = null } }
}

@Composable
private fun SessionView(session: WorkoutSession, day: Fit.PlanDay?, openDetail: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val sets by Fit.dao.setsFor(session.id).collectAsState(emptyList())
    var restLeft by remember { mutableIntStateOf(0) }
    var extra by remember { mutableStateOf<List<Fit.PlanExercise>>(emptyList()) }
    var addName by remember { mutableStateOf(false) }
    var confirmEnd by remember { mutableStateOf(false) }

    LaunchedEffect(restLeft) {
        if (restLeft > 0) { delay(1000); restLeft -= 1 }
    }

    val exercises = (day?.exercises ?: emptyList()) + extra + sets.map { it.exerciseId to it.exerciseName }
        .distinctBy { it.first }.filter { (id, _) -> (day?.exercises ?: emptyList()).none { it.id.ifBlank { it.name } == id } && extra.none { it.id.ifBlank { it.name } == id } }
        .map { (id, n) -> Fit.PlanExercise(id, n, 3, "10", 90, "") }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            AppCard(color = MaterialTheme.colorScheme.primaryContainer) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(session.dayName, fontWeight = FontWeight.Bold)
                        Text(
                            "بدأت ${timeStr(session.start)} • ${sets.size} مجموعة • حجم ${fmt(sets.sumOf { it.kg * it.reps })} كجم",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Button(onClick = { confirmEnd = true }) { Text("خلصت") }
                }
                if (restLeft > 0) {
                    Spacer(Modifier.height(8.dp))
                    Text("راحة: ${restLeft / 60}:${"%02d".format(restLeft % 60)}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Warn)
                    TextButton(onClick = { restLeft = 0 }) { Text("تخطي الراحة") }
                }
            }
        }
        items(exercises) { e ->
            ExerciseLogCard(session.id, e, sets.filter { it.exerciseId == e.id.ifBlank { e.name } }, onRest = { restLeft = it }, onInfo = { if (e.id.isNotBlank()) openDetail(e.id) })
        }
        item {
            OutlinedButton(onClick = { addName = true }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Add, null); Text("ضيف تمرين") }
        }
    }

    if (addName) {
        var name by remember { mutableStateOf("") }
        var found by remember { mutableStateOf<List<Exercise>>(emptyList()) }
        LaunchedEffect(name) {
            delay(250)
            found = if (name.length < 2) emptyList() else runCatching { ExerciseDb.search(ExerciseDb.all(), name, null, null, null).take(8) }.getOrDefault(emptyList())
        }
        AlertDialog(
            onDismissRequest = { addName = false },
            title = { Text("ضيف تمرين") },
            text = {
                Column {
                    OutlinedTextField(name, { name = it }, label = { Text("اسم التمرين (إنجليزي)") }, singleLine = true)
                    found.forEach { ex ->
                        Text(
                            ex.arName ?: ex.name,
                            Modifier.fillMaxWidth().clickable {
                                extra = extra + Fit.PlanExercise(ex.id, ex.name, 3, "10", 90, ""); addName = false
                            }.padding(vertical = 8.dp),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (name.isNotBlank()) extra = extra + Fit.PlanExercise("", name.trim(), 3, "10", 90, "")
                    addName = false
                }) { Text("ضيفه بالاسم ده") }
            },
            dismissButton = { TextButton(onClick = { addName = false }) { Text("إلغاء") } },
        )
    }
    if (confirmEnd) {
        ConfirmDialog("خلصت التمرين؟", "${sets.size} مجموعة • ${(System.currentTimeMillis() - session.start) / 60_000} دقيقة", "خلصت", { confirmEnd = false }) {
            scope.launch {
                if (sets.isEmpty()) Fit.dao.deleteSession(session)
                else Fit.dao.upsertSession(session.copy(end = System.currentTimeMillis()))
                toast(ctx, if (sets.isEmpty()) "اتلغى" else "عاش 💪")
            }
        }
    }
}

@Composable
private fun ExerciseLogCard(sessionId: Long, e: Fit.PlanExercise, done: List<WorkoutSet>, onRest: (Int) -> Unit, onInfo: () -> Unit) {
    val scope = rememberCoroutineScope()
    val key = e.id.ifBlank { e.name }
    var last by remember(key) { mutableStateOf<List<WorkoutSet>>(emptyList()) }
    LaunchedEffect(key) { last = Fit.dao.historyFor(key).filter { it.sessionId != sessionId } }
    val prev = last.firstOrNull()
    var reps by remember(key) { mutableStateOf("") }
    var kg by remember(key) { mutableStateOf("") }
    LaunchedEffect(prev, done.size) {
        val src = done.lastOrNull() ?: prev
        if (src != null) {
            if (reps.isEmpty()) reps = src.reps.toString()
            if (kg.isEmpty()) kg = fmt(src.kg).replace(",", "")
        }
    }

    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(Fit.arName(e.id) ?: e.name, fontWeight = FontWeight.Bold, maxLines = 2)
                Text("الهدف: ${e.sets}×${e.reps} • راحة ${e.rest} ث", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                if (prev != null) {
                    val best = last.filter { it.sessionId == prev.sessionId }.maxByOrNull { it.kg }
                    Text("آخر مرة: ${fmt(best?.kg ?: prev.kg)} كجم × ${best?.reps ?: prev.reps}", style = MaterialTheme.typography.bodySmall, color = Color2)
                }
                if (e.note.isNotBlank()) Text(e.note, style = MaterialTheme.typography.bodySmall)
            }
            if (e.id.isNotBlank()) IconButton(onClick = onInfo) { Icon(Icons.Default.Info, "الشرح") }
        }
        done.forEachIndexed { i, s ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("مجموعة ${i + 1}", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                Text("${fmt(s.kg)} كجم × ${s.reps}", fontWeight = FontWeight.SemiBold)
                IconButton(onClick = { scope.launch { Fit.dao.deleteSet(s) } }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, "امسح", Modifier.size(16.dp))
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            NumberField("كجم", kg, Modifier.weight(1f)) { kg = it }
            NumberField("عدات", reps, Modifier.weight(1f)) { reps = it.filter { c -> c.isDigit() } }
            FilledIconButton(onClick = {
                val r = reps.toIntOrNull() ?: return@FilledIconButton
                val k = kg.toDoubleOrNull() ?: 0.0
                scope.launch {
                    Fit.dao.insertSet(WorkoutSet(sessionId = sessionId, exerciseId = key, exerciseName = e.name, reps = r, kg = k))
                    onRest(e.rest)
                }
            }) { Icon(Icons.Default.Check, "سجّل") }
        }
    }
}

// ---------------------------------------------------------------- Encyclopedia

@Composable
fun EncyclopediaTab() {
    var list by remember { mutableStateOf<List<Exercise>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var q by remember { mutableStateOf("") }
    var muscle by remember { mutableStateOf<String?>(null) }
    var cat by remember { mutableStateOf<String?>(null) }
    var equip by remember { mutableStateOf<String?>(null) }
    var detail by remember { mutableStateOf<Exercise?>(null) }

    LaunchedEffect(attempt) {
        error = null
        try { list = ExerciseDb.all() } catch (e: Exception) { error = "محتاج إنترنت أول مرة بس علشان أنزّل الموسوعة" }
    }

    val all = list
    if (all == null) {
        Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            if (error == null) {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text("بنزّل موسوعة التمارين (مرة واحدة)…")
            } else {
                Text(error!!)
                Spacer(Modifier.height(8.dp))
                Button(onClick = { attempt++ }) { Text("حاول تاني") }
            }
        }
        return
    }
    val shown = remember(all, q, muscle, cat, equip) { ExerciseDb.search(all, q, muscle, cat, equip) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            OutlinedTextField(
                q, { q = it }, placeholder = { Text("دوّر: bench, squat, صدر…") }, singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, null) }, modifier = Modifier.fillMaxWidth(),
            )
        }
        item { ChipsRow(listOf<String?>(null) + ExerciseDb.categories.keys, cat, { it?.let { k -> ExerciseDb.categories[k] } ?: "الكل" }) { cat = it } }
        item { ChipsRow(listOf<String?>(null) + ExerciseDb.muscles.keys, muscle, { it?.let { k -> ExerciseDb.muscles[k] } ?: "كل العضلات" }) { muscle = it } }
        item { ChipsRow(listOf<String?>(null) + ExerciseDb.equipment.keys, equip, { it?.let { k -> ExerciseDb.equipment[k] } ?: "كل الأدوات" }) { equip = it } }
        item { Text("${shown.size} تمرين", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline) }
        items(shown, key = { it.id }) { e ->
            Card(
                onClick = { detail = e }, modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        model = e.imageUrls.firstOrNull(), contentDescription = null, contentScale = ContentScale.Crop,
                        modifier = Modifier.size(64.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(e.arName ?: e.name, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (e.arName != null) Text(e.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, maxLines = 1)
                        Text(
                            e.primary.joinToString("، ") { ExerciseDb.ar(ExerciseDb.muscles, it) } + " • " + ExerciseDb.ar(ExerciseDb.equipment, e.equipment),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, maxLines = 1,
                        )
                    }
                }
            }
        }
    }
    detail?.let { ExerciseDetail(it) { detail = null } }
}

@Composable
fun ExerciseDetailById(id: String, onDismiss: () -> Unit) {
    var e by remember(id) { mutableStateOf<Exercise?>(null) }
    LaunchedEffect(id) { e = ExerciseDb.byId(id) }
    e?.let { ExerciseDetail(it, onDismiss) }
}

@Composable
fun ExerciseDetail(e: Exercise, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var frame by remember { mutableIntStateOf(0) }
    var arName by remember { mutableStateOf(Fit.arName(e.id)) }
    var arText by remember { mutableStateOf(Fit.arExplain(e.id)) }
    var busy by remember { mutableStateOf(false) }
    var history by remember { mutableStateOf<List<WorkoutSet>>(emptyList()) }
    LaunchedEffect(e.id) { history = Fit.dao.historyFor(e.id) }
    LaunchedEffect(e.id) {
        while (e.images.size > 1) { delay(900); frame = (frame + 1) % e.images.size }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(arName ?: e.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "إغلاق") }
                }
                if (arName != null) Text(e.name, color = MaterialTheme.colorScheme.outline)
                Spacer(Modifier.height(10.dp))
                if (e.imageUrls.isNotEmpty()) {
                    AsyncImage(
                        model = e.imageUrls[frame.coerceIn(0, e.imageUrls.size - 1)], contentDescription = e.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().aspectRatio(1.3f).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill(ExerciseDb.ar(ExerciseDb.categories, e.category))
                    Pill(ExerciseDb.ar(ExerciseDb.equipment, e.equipment), Color2)
                    Pill(ExerciseDb.ar(ExerciseDb.levels, e.level), Warn)
                }
                Spacer(Modifier.height(8.dp))
                Text("العضلة الأساسية: " + e.primary.joinToString("، ") { ExerciseDb.ar(ExerciseDb.muscles, it) }, fontWeight = FontWeight.SemiBold)
                if (e.secondary.isNotEmpty()) Text("عضلات مساعدة: " + e.secondary.joinToString("، ") { ExerciseDb.ar(ExerciseDb.muscles, it) }, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        if (!Claude.hasKey) toast(ctx, "اربط ذكاء اصطناعي من الإعدادات") else {
                            busy = true
                            scope.launch {
                                try {
                                    val (n, t) = Coach.explainExercise(e)
                                    arName = n; arText = t
                                } catch (ex: Exception) { toast(ctx, ex.message ?: "") }
                                busy = false
                            }
                        }
                    }, enabled = !busy) {
                        if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text(if (arText == null) "اشرحه بالعربي" else "اشرح تاني")
                    }
                    OutlinedButton(onClick = {
                        runCatching {
                            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(e.name + " exercise form"))))
                        }
                    }) { Icon(Icons.Default.PlayCircle, null); Spacer(Modifier.width(4.dp)); Text("فيديو") }
                }
                arText?.let {
                    Spacer(Modifier.height(10.dp))
                    AppCard(color = MaterialTheme.colorScheme.primaryContainer) { Text(it) }
                }
                Spacer(Modifier.height(12.dp))
                Text("الخطوات (إنجليزي)", fontWeight = FontWeight.Bold)
                e.instructions.forEachIndexed { i, s -> Text("${i + 1}. $s", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 3.dp)) }
                if (history.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text("سجلّك", fontWeight = FontWeight.Bold)
                    val best = history.maxByOrNull { it.kg }
                    best?.let { Text("أعلى وزن: ${fmt(it.kg)} كجم × ${it.reps} (${shortDate(it.time)})", color = Positive) }
                    val pts = history.groupBy { it.sessionId }.values.map { s -> s.first().time / 86_400_000.0 to s.maxOf { it.kg } }.sortedBy { it.first }
                    if (pts.size >= 2) LineChart(pts)
                }
                Spacer(Modifier.height(30.dp))
            }
        }
    }
}
