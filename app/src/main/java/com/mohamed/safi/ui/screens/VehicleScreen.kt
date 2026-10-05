package com.mohamed.safi.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import com.mohamed.safi.ui.Text
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mohamed.safi.SafiApp
import com.mohamed.safi.car.CarDb
import com.mohamed.safi.car.CarPlan
import com.mohamed.safi.data.*
import com.mohamed.safi.ui.*
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** السيارة: carpool rotation + the car (fuel, periodic maintenance, papers) + plans & modifications. */
@Composable
fun VehicleScreen(onBack: () -> Unit, open: (String) -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("دور السواقة" to Icons.Default.Groups, "العربية والصيانة" to Icons.Default.Build, "خطط وتعديلات" to Icons.Default.Lightbulb)
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        VehicleHeader(onBack)
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(tabs) { i, (t, ic) ->
                FilterChip(tab == i, { tab = i }, label = { Text(t) }, leadingIcon = { Icon(ic, null, Modifier.size(18.dp)) })
            }
        }
        Box(Modifier.weight(1f)) {
            when (tab) {
                0 -> CarpoolScreen({}, embedded = true)
                1 -> CarScreen({}, embedded = true)
                else -> CarPlansTab()
            }
        }
    }
}

@Composable
private fun VehicleHeader(onBack: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val prefs = SafiApp.prefs
    val items by remember { SafiApp.db.dao().carItems() }.collectAsState(emptyList())
    val odo = prefs.odometer
    val tomorrow = remember { runCatching { Carpool.load(ctx).driverFor(LocalDate.now(zone).plusDays(1)) }.getOrNull() }
    // the maintenance item that is due soonest (by km or by time)
    val next = items.mapNotNull { c ->
        val kmLeft = if (c.intervalKm > 0 && odo > 0) c.lastKm + c.intervalKm - odo else null
        val daysLeft = if (c.intervalMonths > 0) {
            val due = Instant.ofEpochMilli(c.lastDate).atZone(zone).toLocalDate().plusMonths(c.intervalMonths.toLong())
            ChronoUnit.DAYS.between(LocalDate.now(zone), due).toInt()
        } else null
        val score = minOf(kmLeft?.let { it / 50 } ?: Int.MAX_VALUE, daysLeft ?: Int.MAX_VALUE)
        if (score == Int.MAX_VALUE) null else Triple(c.name, kmLeft to daysLeft, score)
    }.minByOrNull { it.third }

    Surface(color = BrandDeep) {
        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع", tint = Color.White) }
                Text("السيارة", color = Color.White, fontFamily = Amiri, fontWeight = FontWeight.Bold, fontSize = 24.sp)
            }
            Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("العداد", color = Color.White.copy(alpha = 0.65f), style = MaterialTheme.typography.labelSmall)
                    Text(if (odo > 0) "${fmt(odo.toDouble())} كم" else "—", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                }
                Column(Modifier.weight(1f)) {
                    Text("بكرة بيسوق", color = Color.White.copy(alpha = 0.65f), style = MaterialTheme.typography.labelSmall)
                    Text(tomorrow ?: "مفيش سواقة", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                }
            }
            if (next != null) {
                val (kmLeft, daysLeft) = next.second
                val parts = listOfNotNull(
                    kmLeft?.let { if (it <= 0) "متأخر ${fmt((-it).toDouble())} كم" else "بعد ${fmt(it.toDouble())} كم" },
                    daysLeft?.let { if (it <= 0) "فات ميعاده" else "أو $it يوم" },
                )
                val late = (kmLeft ?: 1) <= 0 || (daysLeft ?: 1) <= 0
                Surface(color = Color.White.copy(alpha = 0.08f), shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.OilBarrel, null, tint = if (late) Color(0xFFFFB4A9) else GoldSoft)
                        Spacer(Modifier.width(8.dp))
                        Text("${next.first}: ${parts.joinToString(" ")}", color = Color.White, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

private val planCats = listOf("تعديل", "إصلاح", "إكسسوار", "تجميل", "أداء")
private val planPriorities = listOf("عاجل", "مهم", "لاحقاً")
private val planStatuses = listOf("فكرة", "مخطط", "اتعمل")

@Composable
private fun CarPlansTab() {
    val plans by remember { CarDb.dao.plans() }.collectAsState(emptyList())
    var editing by remember { mutableStateOf<CarPlan?>(null) }
    var adding by remember { mutableStateOf(false) }
    val openPlans = plans.filter { it.status != "اتعمل" }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = { ExtendedFloatingActionButton(onClick = { adding = true }, icon = { Icon(Icons.Default.Add, null) }, text = { Text("فكرة جديدة") }) },
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 100.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                AppCard(color = MaterialTheme.colorScheme.primaryContainer) {
                    Text("ميزانية الخطط المفتوحة", style = MaterialTheme.typography.labelMedium)
                    Text(money(openPlans.sumOf { it.estCost ?: 0.0 }), fontWeight = FontWeight.Bold, fontSize = 24.sp)
                    Text("${openPlans.count { it.priority == "عاجل" }} عاجل • ${openPlans.size} مفتوحة • ${plans.size - openPlans.size} اتعملت",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            if (plans.isEmpty()) item {
                EmptyState(Icons.Default.Lightbulb, "اكتب أي حاجة نفسك تعملها في العربية: تظليل، كاوتش جديد، شاشة، سيراميك، تصليح صوت… وحط تكلفتها التقريبية وأولويتها.")
            }
            planStatuses.forEach { st ->
                val list = plans.filter { it.status == st }.sortedBy { planPriorities.indexOf(it.priority) }
                if (list.isNotEmpty()) {
                    item { SectionTitle("$st (${list.size})") }
                    items(list, key = { it.id }) { p -> PlanCard(p) { editing = p } }
                }
            }
        }
    }
    if (adding) PlanDialog(null) { adding = false }
    editing?.let { p -> PlanDialog(p) { editing = null } }
}

@Composable
private fun PlanCard(p: CarPlan, onClick: () -> Unit) {
    val scope = rememberCoroutineScope()
    val prColor = when (p.priority) { "عاجل" -> Danger; "مهم" -> Warn; else -> MaterialTheme.colorScheme.outline }
    AppCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(p.title, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                    Pill(p.category, MaterialTheme.colorScheme.primary)
                    Pill(p.priority, prColor)
                }
                if (p.note.isNotBlank()) Text(p.note, style = MaterialTheme.typography.bodySmall, maxLines = 2, modifier = Modifier.padding(top = 4.dp))
            }
            Column(horizontalAlignment = Alignment.End) {
                val cost = if (p.status == "اتعمل") p.actualCost ?: p.estCost else p.estCost
                if (cost != null) Text(money(cost), fontWeight = FontWeight.SemiBold)
                if (p.status != "اتعمل") TextButton(onClick = {
                    val nextSt = planStatuses[(planStatuses.indexOf(p.status) + 1).coerceAtMost(2)]
                    scope.launch { CarDb.dao.upsertPlan(p.copy(status = nextSt, doneDate = if (nextSt == "اتعمل") System.currentTimeMillis() else null)) }
                }) { Text(if (p.status == "فكرة") "خطّط لها" else "اتعملت ✓") }
                else p.doneDate?.let { Text(dateStr(it), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline) }
            }
        }
    }
}

@Composable
private fun PlanDialog(existing: CarPlan?, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var title by remember { mutableStateOf(existing?.title ?: "") }
    var cat by remember { mutableStateOf(existing?.category ?: "تعديل") }
    var pr by remember { mutableStateOf(existing?.priority ?: "مهم") }
    var st by remember { mutableStateOf(existing?.status ?: "فكرة") }
    var est by remember { mutableStateOf(existing?.estCost?.let { fmt(it) } ?: "") }
    var actual by remember { mutableStateOf(existing?.actualCost?.let { fmt(it) } ?: "") }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    var addExpense by remember { mutableStateOf(true) }
    var confirmDel by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "خطة جديدة للعربية" else "تعديل الخطة") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("عايز تعمل إيه؟") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                ChipRow(planCats, cat) { cat = it }
                ChipRow(planPriorities, pr) { pr = it }
                ChipRow(planStatuses, st) { st = it }
                OutlinedTextField(est, { est = it }, label = { Text("التكلفة التقريبية (د.إ)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal))
                if (st == "اتعمل") {
                    OutlinedTextField(actual, { actual = it }, label = { Text("التكلفة الفعلية (د.إ)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal))
                    if (existing?.status != "اتعمل") Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(addExpense, { addExpense = it }); Text("سجّلها كمصروف عربية")
                    }
                }
                OutlinedTextField(note, { note = it }, label = { Text("ملاحظات (المحل، الموديل…)") }, modifier = Modifier.fillMaxWidth())
                if (existing != null) TextButton(onClick = { confirmDel = true }) { Text("امسح", color = Danger) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (title.isBlank()) { toast(ctx, "اكتب اسم الخطة"); return@TextButton }
                val a = actual.toDoubleOrNull()
                val p = (existing ?: CarPlan(title = title)).copy(
                    title = title.trim(), category = cat, priority = pr, status = st, note = note.trim(),
                    estCost = est.toDoubleOrNull(), actualCost = a,
                    doneDate = if (st == "اتعمل") existing?.doneDate ?: System.currentTimeMillis() else null,
                )
                scope.launch {
                    CarDb.dao.upsertPlan(p)
                    if (st == "اتعمل" && existing?.status != "اتعمل" && addExpense) {
                        val cost = a ?: p.estCost
                        if (cost != null && cost > 0) SafiApp.db.dao().insertExpense(
                            Expense(amount = cost, amountAed = cost, category = Cats.CAR, merchant = p.title, note = "خطة عربية: ${p.category}"),
                        )
                    }
                    onDismiss()
                }
            }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
    if (confirmDel && existing != null) ConfirmDialog("تمسح الخطة؟", existing.title, "امسح", { confirmDel = false }) {
        scope.launch { CarDb.dao.deletePlan(existing); onDismiss() }
    }
}

@Composable
private fun ChipRow(options: List<String>, value: String, onPick: (String) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(options) { o -> FilterChip(value == o, { onPick(o) }, label = { Text(o) }) }
    }
}
