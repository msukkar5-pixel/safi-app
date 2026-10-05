package com.mohamed.safi.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import com.mohamed.safi.ui.Text
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.HealthConnectClient
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.mohamed.safi.ai.Claude
import com.mohamed.safi.data.*
import com.mohamed.safi.fitness.*
import com.mohamed.safi.ui.*
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun FitnessScreen(onBack: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var profile by remember { mutableStateOf(Fit.prefs.heightCm <= 0) }
    // Bumped when the profile dialog closes so tabs that read Fit.prefs recompute.
    var profileRev by remember { mutableIntStateOf(0) }
    val tabs = listOf("اليوم", "القلب والضغط", "التمرين", "الموسوعة", "الأكل", "الوزن")
    ScreenScaffold(
        "الجيم والصحة", onBack = onBack,
        actions = { IconButton(onClick = { profile = true }) { Icon(Icons.Default.Person, "ملفي") } },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            ScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp, containerColor = MaterialTheme.colorScheme.background) {
                tabs.forEachIndexed { i, t -> Tab(tab == i, { tab = i }, text = { Text(t) }) }
            }
            when (tab) {
                0 -> TodayTab(profileRev) { tab = if (it >= 1) it + 1 else it }
                1 -> VitalsSummary()
                2 -> WorkoutTab()
                3 -> EncyclopediaTab()
                4 -> FoodTab()
                else -> WeightTab(profileRev)
            }
        }
    }
    if (profile) ProfileDialog { profile = false; profileRev++ }
}

// ---------------------------------------------------------------- Today

@Composable
private fun TodayTab(profileRev: Int, goTab: (Int) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val today = LocalDate.now(zone)
    val (from, to) = remember { dayRange(today) }
    val foods by Fit.dao.foodsBetween(from, to).collectAsState(emptyList())
    val water by Fit.dao.water(today.toString()).collectAsState(null)
    val weights by Fit.dao.weights().collectAsState(emptyList())
    var health by remember { mutableStateOf<DayHealth?>(null) }
    var hcGranted by remember { mutableStateOf(false) }
    var reportBusy by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf(Fit.prefs.lastReport) }
    var hcAvailable by remember { mutableStateOf(Health.available(ctx)) }

    suspend fun refreshHealth() {
        hcGranted = Health.hasAny(ctx)
        if (hcGranted) {
            health = Health.day(ctx, today)
            Health.syncWeights(ctx)
        }
    }

    val hcLauncher = rememberLauncherForActivityResult(Health.permissionContract()) { granted ->
        Fit.prefs.healthConnected = granted.isNotEmpty()
        if (granted.isEmpty()) {
            // Denied twice: Android stops showing the dialog, so send him to Health Connect's own screen.
            toast(ctx, "اسمح لـ${com.mohamed.safi.AppName.v} من Health Connect")
            val opened = runCatching { ctx.startActivity(Health.manageIntent(ctx)) }.isSuccess ||
                runCatching { ctx.startActivity(android.content.Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)) }.isSuccess
            if (!opened) toast(ctx, "افتح Health Connect واسمح بالقراية")
        }
        scope.launch { refreshHealth() }
    }
    // Re-check on every resume (e.g. coming back from installing Health Connect or its settings).
    LifecycleResumeEffect(Unit) {
        hcAvailable = Health.available(ctx)
        val job = scope.launch { refreshHealth() }
        onPauseOrDispose { job.cancel() }
    }

    val t = remember(weights, profileRev) { Fit.targets(weights.firstOrNull()?.kg) }
    val kcal = foods.sumOf { it.kcal }
    val protein = foods.sumOf { it.protein }
    val cups = water?.cups ?: 0

    // key(): lazy items read Fit.prefs directly, so rebuild them after the profile changes.
    key(profileRev) { LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (Fit.prefs.heightCm <= 0) item {
            AppCard(color = MaterialTheme.colorScheme.tertiaryContainer) {
                Text("كمّل ملفك (الطول والوزن) من زرار الشخص فوق علشان أحسبلك احتياجك.", fontWeight = FontWeight.SemiBold)
            }
        }
        item {
            AppCard {
                Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                    RingStat("سعرات", "${kcal.toInt()}", t?.let { "/ ${it.kcal}" }, t?.let { (kcal / it.kcal).toFloat() } ?: 0f, Warn)
                    RingStat("بروتين", "${protein.toInt()}g", t?.let { "/ ${it.protein}g" }, t?.let { (protein / it.protein).toFloat() } ?: 0f, Brand)
                    RingStat("مية", "$cups", "/ ${Fit.prefs.waterTarget}", cups / Fit.prefs.waterTarget.toFloat().coerceAtLeast(1f), Color2)
                    RingStat(
                        "خطوات", health?.steps?.let { if (it >= 1000) "${it / 1000}.${(it % 1000) / 100}k" else "$it" } ?: "-", "/ ${Fit.prefs.stepsTarget / 1000}k",
                        ((health?.steps ?: 0) / Fit.prefs.stepsTarget.toFloat().coerceAtLeast(1f)), Positive,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = {
                        scope.launch { Fit.dao.setWater(WaterDay(today.toString(), cups + 1)) }
                    }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.WaterDrop, null); Spacer(Modifier.width(4.dp)); Text("+ كوباية") }
                    OutlinedButton(onClick = { goTab(3) }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Restaurant, null); Spacer(Modifier.width(4.dp)); Text("سجّل أكل") }
                }
                if (cups > 0) TextButton(onClick = { scope.launch { Fit.dao.setWater(WaterDay(today.toString(), cups - 1)) } }) { Text("شيل كوباية") }
            }
        }

        // Watch
        item {
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CatBadge("watch", 38, Icons.Default.Watch, Color2)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("ساعة سامسونج", fontWeight = FontWeight.Bold)
                        Text(
                            when {
                                !Health.supported -> "الموبايل ده مش بيدعم Health Connect (محتاج أندرويد 9 أو أحدث)"
                                !hcAvailable -> "محتاج تطبيق Health Connect"
                                !hcGranted -> "مش متوصلة لسه"
                                else -> "من Samsung Health عن طريق Health Connect"
                            },
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline,
                        )
                    }
                    if (hcGranted) IconButton(onClick = { scope.launch { refreshHealth() } }) { Icon(Icons.Default.Refresh, "تحديث") }
                }
                if (!Health.supported) {
                    // Health Connect can't be installed below Android 9; nothing to offer.
                } else if (!hcAvailable) {
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { runCatching { ctx.startActivity(Health.installIntent()) } }) { Text("نزّل Health Connect") }
                } else if (!hcGranted) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "1) في Samsung Health: الإعدادات ← Health Connect ← فعّل المشاركة.\n2) دوس الزرار ده واسمح لـ${com.mohamed.safi.AppName.v} يقرا.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(6.dp))
                    Button(onClick = { runCatching { hcLauncher.launch(Health.permissions) } }) { Text("اربط الساعة") }
                } else health?.let { h ->
                    Spacer(Modifier.height(10.dp))
                    Row {
                        StatBlock("نوم امبارح", if (h.sleepMin > 0) "${h.sleepMin / 60}س ${h.sleepMin % 60}د" else "-", Modifier.weight(1f))
                        StatBlock("نبض الراحة", h.restingHr?.let { "$it" } ?: "-", Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(8.dp))
                    Row {
                        StatBlock("متوسط النبض", h.avgHr?.let { "$it" } ?: "-", Modifier.weight(1f), sub = h.maxHr?.let { "أعلى $it" })
                        StatBlock("حرق نشاط", "${h.activeKcal.toInt()}", Modifier.weight(1f), sub = "سعر")
                    }
                    if (h.workouts.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text("تمارين الساعة: " + h.workouts.joinToString("، "), style = MaterialTheme.typography.bodySmall)
                    }
                    Text(
                        "لو الأرقام مش محدثة افتح Samsung Health واسحب لتحت علشان يزامن.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
        }

        // Today's plan
        item {
            val plan = remember { Fit.parsePlan(Fit.prefs.workoutPlan) }
            AppCard(onClick = { goTab(1) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CatBadge("gym", 38, Icons.Default.FitnessCenter, Brand)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("التمرين", fontWeight = FontWeight.Bold)
                        Text(
                            plan?.let { "${it.days.size} أيام في الخطة • دوس علشان تبدأ" } ?: "اعمل خطتك من تاب التمرين",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline,
                        )
                    }
                }
            }
        }

        // Weekly report
        item {
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("تقرير الأسبوع", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Button(onClick = {
                        if (!Claude.hasKey) toast(ctx, "اربط ذكاء اصطناعي من الإعدادات") else {
                            reportBusy = true
                            scope.launch {
                                report = try { Coach.weeklyReport(ctx) } catch (e: Exception) { "⚠️ " + (e.message ?: "") }
                                reportBusy = false
                            }
                        }
                    }, enabled = !reportBusy) {
                        if (reportBusy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text(if (report.isBlank()) "اعمل التقرير" else "حدّث")
                    }
                }
                if (report.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(report, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        item {
            Text(
                "${com.mohamed.safi.AppName.v} مش دكتور. أي حاجة تخص أمراض أو أدوية أو تحاليل راجع فيها دكتورك.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline,
            )
        }
    } }
}

@Composable
private fun RingStat(label: String, value: String, sub: String?, fraction: Float, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(70.dp)) {
            Ring(fraction, color, Modifier.fillMaxSize())
            Text(value, fontWeight = FontWeight.Bold, fontSize = 13.sp, textAlign = TextAlign.Center)
        }
        Text(label, style = MaterialTheme.typography.labelMedium)
        if (sub != null) Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
    }
}

// ---------------------------------------------------------------- Weight

@Composable
private fun WeightTab(profileRev: Int) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val weights by Fit.dao.weights().collectAsState(emptyList())
    var adding by remember { mutableStateOf(false) }
    var del by remember { mutableStateOf<WeightEntry?>(null) }
    val t = remember(weights, profileRev) { Fit.targets(weights.firstOrNull()?.kg) }

    key(profileRev) { LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("وزنك الحالي", color = MaterialTheme.colorScheme.outline)
                        Text(weights.firstOrNull()?.let { "${fmt(it.kg)} كجم" } ?: "-", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        if (weights.size >= 2) {
                            val old = weights.lastOrNull { it.time >= System.currentTimeMillis() - 30L * 86_400_000L } ?: weights.last()
                            val d = weights.first().kg - old.kg
                            Text(
                                (if (d <= 0) "▼ " else "▲ ") + "${fmt(kotlin.math.abs(d))} كجم من ${shortDate(old.time)}",
                                color = if ((d <= 0) == (Fit.prefs.goal == "cut")) Positive else Warn,
                            )
                        }
                        if (Fit.prefs.targetKg > 0 && weights.isNotEmpty()) {
                            Text("فاضل ${fmt(kotlin.math.abs(weights.first().kg - Fit.prefs.targetKg))} كجم على هدفك (${fmt(Fit.prefs.targetKg)})", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Button(onClick = { adding = true }) { Icon(Icons.Default.Add, null); Text("سجّل") }
                }
                val pts = weights.filter { it.time >= System.currentTimeMillis() - 120L * 86_400_000L }.reversed()
                    .map { (it.time / 86_400_000.0) to it.kg }
                if (pts.size >= 2) {
                    Spacer(Modifier.height(12.dp))
                    LineChart(pts, target = Fit.prefs.targetKg.takeIf { it > 0 })
                    Text("آخر 4 شهور" + if (Fit.prefs.targetKg > 0) " • الخط المتقطع = هدفك" else "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
            }
        }
        if (t != null) item {
            AppCard {
                Text("احتياجك اليومي", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Row {
                    StatBlock("سعرات", "${t.kcal}", Modifier.weight(1f), sub = Fit.goalLabel(Fit.prefs.goal))
                    StatBlock("بروتين", "${t.protein}g", Modifier.weight(1f))
                }
                Spacer(Modifier.height(6.dp))
                Row {
                    StatBlock("كارب", "${t.carbs}g", Modifier.weight(1f))
                    StatBlock("دهون", "${t.fat}g", Modifier.weight(1f))
                }
                Text("الحرق الأساسي ${t.bmr} • مع النشاط ${t.tdee}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
        }
        item {
            TextButton(onClick = {
                scope.launch {
                    val n = Health.syncWeights(ctx)
                    toast(ctx, if (n > 0) "اتضاف $n وزن من الساعة/الميزان" else "مفيش أوزان جديدة في Samsung Health")
                }
            }) { Icon(Icons.Default.Sync, null); Spacer(Modifier.width(6.dp)); Text("هات الأوزان من Samsung Health") }
        }
        items(weights, key = { it.id }) { w ->
            AppCard(onClick = { del = w }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(dateTimeStr(w.time), Modifier.weight(1f))
                    if (w.source == "watch") { Pill("الساعة", Color2); Spacer(Modifier.width(6.dp)) }
                    w.bodyFat?.let { Text("دهون ${fmt(it)}%  ", style = MaterialTheme.typography.bodySmall) }
                    Text("${fmt(w.kg)} كجم", fontWeight = FontWeight.Bold)
                }
            }
        }
    } }

    if (adding) {
        var kg by remember { mutableStateOf("") }
        var fat by remember { mutableStateOf("") }
        var waist by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text("سجّل الوزن") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField("الوزن", kg, suffix = "كجم") { kg = it }
                    NumberField("نسبة الدهون (اختياري)", fat, suffix = "%") { fat = it }
                    NumberField("الوسط (اختياري)", waist, suffix = "سم") { waist = it }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val k = kg.toDoubleOrNull()
                    if (k == null || k < 30) toast(ctx, "اكتب الوزن") else scope.launch {
                        Fit.dao.insertWeight(WeightEntry(kg = k, bodyFat = fat.toDoubleOrNull(), waistCm = waist.toDoubleOrNull()))
                        adding = false
                    }
                }) { Text("حفظ") }
            },
            dismissButton = { TextButton(onClick = { adding = false }) { Text("إلغاء") } },
        )
    }
    del?.let { w ->
        ConfirmDialog("مسح الوزن ده؟", "${fmt(w.kg)} كجم — ${dateTimeStr(w.time)}", "امسح", { del = null }) {
            scope.launch { Fit.dao.deleteWeight(w) }
        }
    }
}

// ---------------------------------------------------------------- Profile

@Composable
fun ProfileDialog(onDismiss: () -> Unit) {
    val p = Fit.prefs
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var height by remember { mutableStateOf(if (p.heightCm > 0) p.heightCm.toString() else "") }
    var weight by remember { mutableStateOf("") }
    var year by remember { mutableStateOf(p.birthYear.toString()) }
    var male by remember { mutableStateOf(p.male) }
    var goal by remember { mutableStateOf(p.goal) }
    var activity by remember { mutableStateOf(p.activity.toString()) }
    var target by remember { mutableStateOf(if (p.targetKg > 0) fmt(p.targetKg) else "") }
    var days by remember { mutableStateOf(p.trainingDays.toString()) }
    var minutes by remember { mutableStateOf(p.sessionMin.toString()) }
    var equipment by remember { mutableStateOf(p.equipment) }
    var foodPrefs by remember { mutableStateOf(p.foodPrefs) }
    var goalNote by remember { mutableStateOf(p.goalNote) }
    var waterT by remember { mutableStateOf(p.waterTarget.toString()) }
    var stepsT by remember { mutableStateOf(p.stepsTarget.toString()) }
    var kcalO by remember { mutableStateOf(if (p.kcalOverride > 0) p.kcalOverride.toString() else "") }
    LaunchedEffect(Unit) { Fit.latestWeight()?.let { weight = fmt(it) } }

    val activities = listOf("1.2", "1.375", "1.55", "1.725", "1.9")
    fun actLabel(a: String) = when (a) {
        "1.2" -> "قاعد أغلب الوقت"
        "1.375" -> "نشاط خفيف (1-3 تمرين)"
        "1.55" -> "متوسط (3-5 تمرين)"
        "1.725" -> "عالي (6-7 تمرين)"
        else -> "عالي جدًا (تمرينين في اليوم / شغل بدني)"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ملفي") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField("الطول", height, Modifier.weight(1f), suffix = "سم") { height = it }
                    NumberField("الوزن", weight, Modifier.weight(1f), suffix = "كجم") { weight = it }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    NumberField("سنة الميلاد", year, Modifier.weight(1f)) { year = it }
                    FilterChip(male, { male = true }, label = { Text("ذكر") })
                    FilterChip(!male, { male = false }, label = { Text("أنثى") })
                }
                ChoiceField("الهدف", goal, listOf("cut", "recomp", "maintain", "bulk"), display = Fit::goalLabel) { goal = it }
                NumberField("الوزن المستهدف", target, suffix = "كجم") { target = it }
                ChoiceField("مستوى النشاط", activity, activities, display = ::actLabel) { activity = it }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField("أيام تمرين/أسبوع", days, Modifier.weight(1f)) { days = it }
                    NumberField("دقايق التمرين", minutes, Modifier.weight(1f)) { minutes = it }
                }
                OutlinedTextField(equipment, { equipment = it }, label = { Text("المكان/الأدوات") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(foodPrefs, { foodPrefs = it }, label = { Text("أكلك المفضل / اللي مش بتاكله") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(goalNote, { goalNote = it }, label = { Text("ملاحظات عن هدفك") }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField("المية (كوبايات)", waterT, Modifier.weight(1f)) { waterT = it }
                    NumberField("الخطوات", stepsT, Modifier.weight(1f)) { stepsT = it }
                }
                NumberField("سعرات يدوي (سيبها فاضية = أوتوماتيك)", kcalO) { kcalO = it }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                height.toIntOrNull()?.let { p.heightCm = it }
                year.toIntOrNull()?.takeIf { it in 1930..2015 }?.let { p.birthYear = it }
                p.male = male; p.goal = goal
                activity.toDoubleOrNull()?.let { p.activity = it }
                p.targetKg = target.toDoubleOrNull() ?: 0.0
                days.toIntOrNull()?.coerceIn(1, 7)?.let { p.trainingDays = it }
                minutes.toIntOrNull()?.let { p.sessionMin = it }
                p.equipment = equipment.trim(); p.foodPrefs = foodPrefs.trim(); p.goalNote = goalNote.trim()
                waterT.toIntOrNull()?.let { p.waterTarget = it }
                stepsT.toIntOrNull()?.let { p.stepsTarget = it }
                p.kcalOverride = kcalO.toIntOrNull() ?: 0
                val w = weight.toDoubleOrNull()
                scope.launch {
                    if (w != null && w > 30 && w != Fit.latestWeight()) Fit.dao.insertWeight(WeightEntry(kg = w))
                    toast(ctx, "اتحفظ")
                    onDismiss()
                }
            }) { Text("حفظ", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}
