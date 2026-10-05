package com.mohamed.safi.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import com.mohamed.safi.ui.Text
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.content.edit
import com.mohamed.safi.SafiApp
import com.mohamed.safi.data.*
import com.mohamed.safi.extra.*
import com.mohamed.safi.ui.*
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import java.time.temporal.ChronoUnit

// ======================================================================= Documents

private val docPresets = listOf("الهوية الإماراتية", "الإقامة", "الجواز", "رخصة السواقة", "ملكية العربية", "تأمين العربية", "التأمين الصحي", "عقد الإيجار (إيجاري)", "بطاقة العمل")

@Composable
fun SavingsScreen(onBack: () -> Unit, embedded: Boolean = false) {
    val goals by ExtraDb.dao.goals().collectAsState(emptyList())
    var editing by remember { mutableStateOf<SavingGoal?>(null) }
    var adding by remember { mutableStateOf(false) }
    var deposit by remember { mutableStateOf<SavingGoal?>(null) }
    val savedAed = goals.sumOf { Fx.toAed(it.saved, it.currency) }
    val targetAed = goals.sumOf { Fx.toAed(it.target, it.currency) }
    ScreenScaffold(
        "أهداف الادخار", onBack = if (embedded) null else onBack, showTopBar = !embedded,
        fab = { if (!embedded) ExtendedFloatingActionButton(onClick = { adding = true }, icon = { Icon(Icons.Default.Add, null) }, text = { Text("هدف") }) },
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 100.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (goals.isNotEmpty()) item {
                AppCard(color = MaterialTheme.colorScheme.primaryContainer) {
                    Text("محوّش في كل الأهداف", color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(money(savedAed), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "من ${money(targetAed)} • ${goals.size} هدف" + if (targetAed > 0) " • ${(savedAed / targetAed * 100).toInt().coerceIn(0, 100)}%" else "",
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    if (embedded) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = { adding = true }) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("هدف جديد") }
                    }
                }
            }
            if (goals.isEmpty()) item {
                EmptyState(Icons.Default.Savings, "مثلاً: عايز أحوّش 20,000 درهم لحد ديسمبر")
                if (embedded) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    Button(onClick = { adding = true }) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("ضيف أول هدف") }
                }
            }
            items(goals, key = { it.id }) { g ->
                val left = (g.target - g.saved).coerceAtLeast(0.0)
                val months = g.deadline?.let { ChronoUnit.MONTHS.between(LocalDate.now(zone).withDayOfMonth(1), it.toLocalDate().withDayOfMonth(1)).coerceAtLeast(1) }
                val frac = if (g.target > 0) (g.saved / g.target).toFloat().coerceIn(0f, 1f) else 0f
                AppCard(onClick = { editing = g }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CatBadge(g.name, 40, Icons.Default.Savings, Positive)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(g.name, fontWeight = FontWeight.Bold)
                            Text("${money(g.saved, g.currency)} من ${money(g.target, g.currency)}", style = MaterialTheme.typography.bodySmall)
                        }
                        FilledTonalButton(onClick = { deposit = g }) { Text("حوّشت") }
                    }
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(progress = { frac }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)), color = Positive, trackColor = Positive.copy(alpha = 0.14f))
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            when {
                                left <= 0 -> "وصلت للهدف ✓"
                                months != null -> "محتاج تحوّش ${money(left / months, g.currency)} كل شهر لحد ${shortDate(g.deadline!!)}"
                                else -> "فاضل ${money(left, g.currency)}"
                            },
                            style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f),
                        )
                        Text("${(frac * 100).toInt()}%", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = Positive)
                    }
                }
            }
        }
    }
    if (adding) GoalDialog(null) { adding = false }
    editing?.let { g -> GoalDialog(g) { editing = null } }
    deposit?.let { g -> GoalDepositDialog(g) { deposit = null } }
}

/** Put money into (or take it out of) a saving goal. */
@Composable
fun GoalDepositDialog(g: SavingGoal, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var amt by remember(g.id) { mutableStateOf("") }
    var withdraw by remember(g.id) { mutableStateOf(false) }
    var busy by remember(g.id) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(g.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("محوّش ${money(g.saved, g.currency)} من ${money(g.target, g.currency)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(!withdraw, { withdraw = false }, label = { Text("حوّشت") })
                    FilterChip(withdraw, { withdraw = true }, label = { Text("سحبت منه") })
                }
                NumberField("المبلغ", amt, suffix = curLabel(g.currency)) { amt = it }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = {
                val a = amt.toDoubleOrNull() ?: 0.0
                if (busy) return@TextButton
                if (a <= 0) { toast(ctx, "اكتب المبلغ"); return@TextButton }
                busy = true
                scope.launch {
                    try {
                        ExtraDb.dao.upsertGoal(g.copy(saved = (g.saved + if (withdraw) -a else a).coerceAtLeast(0.0)))
                        onDismiss()
                    } finally {
                        busy = false
                    }
                }
            }) { Text("حفظ", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}

/** Add / edit a saving goal. */
@Composable
fun GoalDialog(existing: SavingGoal?, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var target by remember { mutableStateOf(existing?.target?.let { fmt(it).replace(",", "") } ?: "") }
    var saved by remember { mutableStateOf(existing?.saved?.let { fmt(it).replace(",", "") } ?: "") }
    var currency by remember { mutableStateOf(existing?.currency ?: "AED") }
    var deadline by remember { mutableStateOf(existing?.deadline) }
    var confirmDel by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "هدف جديد" else existing.name) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("الهدف (عربية، أجازة، طوارئ…)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField("المبلغ المطلوب", target, Modifier.weight(1.4f)) { target = it }
                    ChoiceField("العملة", currency, CURRENCIES, Modifier.weight(1f)) { currency = it }
                }
                NumberField("محوّش منه لحد دلوقتي", saved) { saved = it }
                DateField("لحد إمتى (اختياري)", deadline, withTime = false) { deadline = it }
                if (existing != null) TextButton(onClick = { confirmDel = true }, colors = ButtonDefaults.textButtonColors(contentColor = Danger)) { Text("امسح") }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val t = target.toDoubleOrNull() ?: 0.0
                if (name.isBlank() || t <= 0) toast(ctx, "اكتب الهدف والمبلغ") else scope.launch {
                    ExtraDb.dao.upsertGoal(
                        SavingGoal(
                            id = existing?.id ?: 0, name = name.trim(), target = t, currency = currency, deadline = deadline,
                            saved = saved.toDoubleOrNull() ?: 0.0, createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                        ),
                    )
                    onDismiss()
                }
            }) { Text("حفظ", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
    if (confirmDel && existing != null) ConfirmDialog("مسح الهدف؟", existing.name, "امسح", { confirmDel = false }) {
        scope.launch { ExtraDb.dao.deleteGoal(existing); onDismiss() }
    }
}

// ======================================================================= Kids' lessons (Egypt)

@Composable
fun LessonsScreen(onBack: () -> Unit, embedded: Boolean = false) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val lessons by ExtraDb.dao.lessons().collectAsState(emptyList())
    var editing by remember { mutableStateOf<Lesson?>(null) }
    var adding by remember { mutableStateOf(false) }
    var payAll by remember { mutableStateOf(false) }
    val active = lessons.filter { it.active }
    val total = active.sumOf { it.monthlyFeeEgp }
    val rate = SafiApp.prefs.egpPerAed
    val cat = SafiApp.prefs.transferCats.firstOrNull { it.contains("دروس") } ?: "دروس الأولاد"

    ScreenScaffold(
        "دروس الأولاد", onBack = if (embedded) null else onBack, showTopBar = !embedded,
        fab = { if (!embedded) ExtendedFloatingActionButton(onClick = { adding = true }, icon = { Icon(Icons.Default.Add, null) }, text = { Text("درس") }) },
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 100.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                AppCard(color = MaterialTheme.colorScheme.primaryContainer) {
                    Text("الدروس في الشهر", color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(money(total, "EGP"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("≈ ${money(total / rate)} • ${active.size} درس شغّال", color = MaterialTheme.colorScheme.onPrimaryContainer)
                    if (total > 0) {
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { payAll = true }) { Text("سجّل تحويل الدروس للشهر ده") }
                    }
                }
            }
            if (lessons.isEmpty()) item { EmptyState(Icons.Default.School, "ضيف دروس كل واحد من الأولاد بالمدرس والمواعيد والسعر") }
            lessons.groupBy { it.child }.forEach { (child, ls) ->
                item(key = "child_$child") {
                    Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(child, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        Text(money(ls.filter { it.active }.sumOf { it.monthlyFeeEgp }, "EGP"), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.outline)
                    }
                }
                items(ls, key = { it.id }) { l ->
                    AppCard(onClick = { editing = l }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CatBadge(l.subject, 38, Icons.Default.School, catColor(l.subject))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(l.subject + if (l.teacher.isNotBlank()) " — ${l.teacher}" else "", fontWeight = FontWeight.SemiBold)
                                if (l.schedule.isNotBlank()) Text(l.schedule, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(money(l.monthlyFeeEgp, "EGP"), fontWeight = FontWeight.Bold)
                                if (l.monthlyFeeEgp > 0) Text("≈ ${money(l.monthlyFeeEgp / rate)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                if (l.sessionsPerMonth > 0) Text("${l.sessionsPerMonth} حصة", style = MaterialTheme.typography.bodySmall)
                                if (!l.active) Pill("متوقف", MaterialTheme.colorScheme.outline)
                            }
                        }
                    }
                }
            }
        }
    }
    if (adding) LessonDialog(null, lessons.map { it.child }.distinct()) { adding = false }
    editing?.let { l -> LessonDialog(l, lessons.map { it.child }.distinct()) { editing = null } }
    if (payAll) ConfirmDialog("تسجيل التحويل", "${money(total, "EGP")} (≈ ${money(total / rate)}) في بند $cat", "سجّل", { payAll = false }) {
        scope.launch {
            SafiApp.db.dao().upsertTransfer(Transfer(amountEgp = total, rate = rate, amountAed = total / rate, category = cat, note = "دروس " + monthName(java.time.YearMonth.now(zone))))
            toast(ctx, "اتسجل في تحويلات مصر")
        }
    }
}

/** Add / edit a kid's lesson. [children]: names already used, offered as chips. */
@Composable
fun LessonDialog(existing: Lesson?, children: List<String>, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var child by remember { mutableStateOf(existing?.child ?: children.firstOrNull() ?: "") }
    var subject by remember { mutableStateOf(existing?.subject ?: "") }
    var teacher by remember { mutableStateOf(existing?.teacher ?: "") }
    var schedule by remember { mutableStateOf(existing?.schedule ?: "") }
    var fee by remember { mutableStateOf(existing?.monthlyFeeEgp?.takeIf { it > 0 }?.let { fmt(it).replace(",", "") } ?: "") }
    var sessions by remember { mutableStateOf(existing?.sessionsPerMonth?.takeIf { it > 0 }?.toString() ?: "") }
    var phone by remember { mutableStateOf(existing?.phone ?: "") }
    var active by remember { mutableStateOf(existing?.active ?: true) }
    var confirmDel by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "درس جديد" else existing.subject) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (children.isNotEmpty()) ChipsRow(children, child, { it }) { child = it }
                OutlinedTextField(child, { child = it }, label = { Text("اسم الابن/البنت") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(subject, { subject = it }, label = { Text("المادة") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(teacher, { teacher = it }, label = { Text("المدرس") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(schedule, { schedule = it }, label = { Text("المواعيد (السبت والتلات 5 م)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField("في الشهر", fee, Modifier.weight(1.3f), suffix = "ج.م") { fee = it }
                    NumberField("حصص/شهر", sessions, Modifier.weight(1f)) { sessions = it.filter { c -> c.isDigit() } }
                }
                OutlinedTextField(phone, { phone = it }, label = { Text("موبايل المدرس (اختياري)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) { Text("شغّال", Modifier.weight(1f)); Switch(active, { active = it }) }
                if (existing != null) TextButton(onClick = { confirmDel = true }, colors = ButtonDefaults.textButtonColors(contentColor = Danger)) { Text("امسح") }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (child.isBlank() || subject.isBlank()) toast(ctx, "اكتب الاسم والمادة") else scope.launch {
                    ExtraDb.dao.upsertLesson(
                        Lesson(
                            id = existing?.id ?: 0, child = child.trim(), subject = subject.trim(), teacher = teacher.trim(), schedule = schedule.trim(),
                            monthlyFeeEgp = fee.toDoubleOrNull() ?: 0.0, sessionsPerMonth = sessions.toIntOrNull() ?: 0, phone = phone.trim(), active = active,
                        ),
                    )
                    onDismiss()
                }
            }) { Text("حفظ", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
    if (confirmDel && existing != null) ConfirmDialog("مسح الدرس؟", existing.subject, "امسح", { confirmDel = false }) {
        scope.launch { ExtraDb.dao.deleteLesson(existing); onDismiss() }
    }
}

// ======================================================================= Zakat

@Composable
fun ZakatScreen(onBack: () -> Unit, embedded: Boolean = false) {
    val ctx = LocalContext.current
    val sp = remember { ctx.getSharedPreferences("safi_zakat", android.content.Context.MODE_PRIVATE) }
    fun g(k: String) = sp.getString(k, "") ?: ""
    var cash by remember { mutableStateOf(g("cash")) }
    var g24 by remember { mutableStateOf(g("g24")) }
    var g21 by remember { mutableStateOf(g("g21")) }
    var g18 by remember { mutableStateOf(g("g18")) }
    var price by remember { mutableStateOf(g("price")) }
    var invest by remember { mutableStateOf(g("invest")) }
    var owed by remember { mutableStateOf(g("owed")) }
    var debts by remember { mutableStateOf(g("debts")) }
    LaunchedEffect(Unit) {
        val dao = SafiApp.db.dao()
        val open = dao.openDebtsNow()
        if (owed.isBlank()) owed = fmt(open.filter { it.direction == "owed_to_me" }.sumOf { Fx.toAed(it.remaining, it.currency) }).replace(",", "").takeIf { it != "0" } ?: ""
        if (debts.isBlank()) debts = fmt(open.filter { it.direction == "i_owe" }.sumOf { Fx.toAed(it.remaining, it.currency) }).replace(",", "").takeIf { it != "0" } ?: ""
    }
    fun d(s: String) = s.toDoubleOrNull() ?: 0.0
    val goldGrams24 = d(g24) + d(g21) * 21 / 24 + d(g18) * 18 / 24
    val goldValue = goldGrams24 * d(price)
    val total = d(cash) + goldValue + d(invest) + d(owed) - d(debts)
    val nisab = 85 * d(price)
    val due = if (d(price) > 0 && total >= nisab) total * 0.025 else 0.0

    ScreenScaffold("حاسبة الزكاة", onBack = if (embedded) null else onBack, showTopBar = !embedded) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = if (embedded) 100.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AppCard(color = MaterialTheme.colorScheme.primaryContainer) {
                Text("الزكاة المستحقة", color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(money(due), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(
                    if (d(price) <= 0) "اكتب سعر جرام الذهب عيار 24 علشان أحسب النصاب"
                    else if (total < nisab) "المال الصافي ${money(total)} أقل من النصاب ${money(nisab)} — مفيش زكاة"
                    else "2.5% من ${money(total)} • النصاب ${money(nisab)} (85 جرام دهب)",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            NumberField("كاش وفلوس في البنوك", cash, suffix = "د.إ") { cash = it }
            NumberField("سعر جرام الدهب عيار 24 النهارده", price, suffix = "د.إ") { price = it }
            Text("السعر بتلاقيه في محلات الدهب أو موقع سوق دبي للذهب.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                NumberField("دهب 24", g24, Modifier.weight(1f), suffix = "جم") { g24 = it }
                NumberField("دهب 21", g21, Modifier.weight(1f), suffix = "جم") { g21 = it }
                NumberField("دهب 18", g18, Modifier.weight(1f), suffix = "جم") { g18 = it }
            }
            NumberField("أسهم واستثمارات وبضاعة تجارة", invest, suffix = "د.إ") { invest = it }
            NumberField("فلوس ليك عند الناس (متوقع ترجع)", owed, suffix = "د.إ") { owed = it }
            NumberField("ديون عليك مستحقة", debts, suffix = "د.إ") { debts = it }
            Button(onClick = {
                sp.edit { putString("cash", cash); putString("g24", g24); putString("g21", g21); putString("g18", g18); putString("price", price); putString("invest", invest); putString("owed", owed); putString("debts", debts) }
                toast(ctx, "اتحفظ")
            }) { Text("احفظ الأرقام") }
            Text(
                "شروط الزكاة: بلوغ النصاب ومرور سنة هجرية كاملة على المال. دهب الزينة المستعمل فيه خلاف بين العلماء. دي حسبة تقريبية، وللحالات الخاصة اسأل دار الإفتاء أو شيخ موثوق.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}
