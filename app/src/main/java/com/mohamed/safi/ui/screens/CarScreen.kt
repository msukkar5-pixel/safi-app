package com.mohamed.safi.ui.screens

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mohamed.safi.SafiApp
import com.mohamed.safi.data.*
import com.mohamed.safi.ui.*
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

private val defaultItems = listOf(
    CarItem(name = "تغيير زيت", intervalKm = 10_000, intervalMonths = 6),
    CarItem(name = "فلتر هواء", intervalKm = 20_000, intervalMonths = 12),
    CarItem(name = "فحص الفرامل", intervalKm = 20_000, intervalMonths = 12),
    CarItem(name = "تغيير الكاوتش", intervalKm = 50_000, intervalMonths = 48),
    CarItem(name = "بطارية", intervalKm = 0, intervalMonths = 30),
    CarItem(name = "صيانة التكييف", intervalKm = 0, intervalMonths = 12),
)

@Composable
fun CarScreen(onBack: () -> Unit) {
    val dao = SafiApp.db.dao()
    val prefs = SafiApp.prefs
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val carItems by remember { dao.carItems() }.collectAsState(emptyList())
    val ym = remember { YearMonth.now(zone) }
    val sixFrom = remember(ym) { monthRange(ym.minusMonths(5)).first }
    val to = remember(ym) { monthRange(ym).second }
    val carExpenses by remember(sixFrom, to) { dao.expensesBetween(sixFrom, to) }.collectAsState(emptyList())
    var odometer by remember { mutableIntStateOf(prefs.odometer) }
    var editOdo by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<CarItem?>(null) }
    var adding by remember { mutableStateOf(false) }
    var docs by remember { mutableStateOf(Triple(prefs.regExpiry, prefs.insExpiry, prefs.licenseExpiry)) }

    val carOnly = carExpenses.filter { it.category in Cats.carCats && !it.isIncome }
    val (mFrom, _) = monthRange(ym)
    val thisMonth = carOnly.filter { it.time >= mFrom }
    val fuelMonth = thisMonth.filter { it.category == Cats.FUEL }.sumOf { it.amountAed }
    val byMonth = (0..5).map { i ->
        val m = ym.minusMonths((5 - i).toLong())
        val (f, t) = monthRange(m)
        m to carOnly.filter { it.time in f..t }.sumOf { it.amountAed }
    }

    ScreenScaffold(
        "العربية", onBack = onBack,
        fab = { ExtendedFloatingActionButton(onClick = { adding = true }, icon = { Icon(Icons.Default.Add, null) }, text = { Text("بند صيانة") }) },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                AppCard(onClick = { editOdo = true }, color = MaterialTheme.colorScheme.primaryContainer) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Speed, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("العداد", color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Text(if (odometer > 0) "${fmt(odometer.toDouble())} كم" else "دوس وسجل العداد", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        }
                        Icon(Icons.Default.Edit, null)
                    }
                }
            }
            item {
                AppCard {
                    Row {
                        StatBlock("بنزين الشهر ده", money(fuelMonth), Modifier.weight(1f))
                        StatBlock("كل مصاريف العربية", money(thisMonth.sumOf { it.amountAed }), Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(12.dp))
                    val max = byMonth.maxOf { it.second }.coerceAtLeast(1.0)
                    byMonth.forEach { (m, v) ->
                        BarRow(monthName(m), money(v), (v / max).toFloat(), Brand)
                    }
                    Text("بنزين + صيانة + سالك ومواصلات", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
            }
            item { SectionTitle("الأوراق") }
            item {
                AppCard {
                    DocRow("ترخيص العربية (الملكية)", docs.first) { prefs.regExpiry = it; docs = docs.copy(first = it) }
                    DocRow("تأمين العربية", docs.second) { prefs.insExpiry = it; docs = docs.copy(second = it) }
                    DocRow("رخصة السواقة", docs.third) { prefs.licenseExpiry = it; docs = docs.copy(third = it) }
                    Text("هفكرك قبل الانتهاء بشهر في ملخص الصبح.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
            }
            item {
                SectionTitle("الصيانة الدورية") {
                    if (carItems.isEmpty()) TextButton(onClick = {
                        scope.launch { defaultItems.forEach { dao.upsertCarItem(it.copy(lastKm = odometer)) } }
                    }) { Text("ضيف البنود الأساسية") }
                }
            }
            if (carItems.isEmpty()) item { EmptyState(Icons.Default.Build, "ضيف بنود الصيانة علشان أفكرك بمواعيدها") }
            items(carItems, key = { it.id }) { c ->
                val dueDate = if (c.intervalMonths > 0) Instant.ofEpochMilli(c.lastDate).atZone(zone).toLocalDate().plusMonths(c.intervalMonths.toLong()) else null
                val kmLeft = if (c.intervalKm > 0 && odometer > 0) c.lastKm + c.intervalKm - odometer else null
                val late = (dueDate != null && dueDate.isBefore(LocalDate.now(zone))) || (kmLeft != null && kmLeft < 0)
                val close = !late && ((dueDate != null && !dueDate.isAfter(LocalDate.now(zone).plusDays(30))) || (kmLeft != null && kmLeft <= 1000))
                AppCard(onClick = { editing = c }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CatBadge(c.name, 40, Icons.Default.Build, if (late) Danger else if (close) Warn else Positive)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(c.name, fontWeight = FontWeight.SemiBold)
                            val parts = mutableListOf<String>()
                            if (kmLeft != null) parts += if (kmLeft >= 0) "فاضل ${fmt(kmLeft.toDouble())} كم" else "عدّى بـ ${fmt((-kmLeft).toDouble())} كم"
                            if (dueDate != null) parts += "قبل ${dueDate.dayOfMonth}/${dueDate.monthValue}/${dueDate.year}"
                            Text(parts.joinToString(" • ").ifBlank { "سجّل آخر مرة" }, style = MaterialTheme.typography.bodySmall, color = if (late) Danger else MaterialTheme.colorScheme.outline)
                            Text("آخر مرة: ${shortDate(c.lastDate)}" + if (c.lastKm > 0) " عند ${fmt(c.lastKm.toDouble())} كم" else "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }
                        FilledTonalButton(onClick = {
                            scope.launch { dao.upsertCarItem(c.copy(lastDate = System.currentTimeMillis(), lastKm = odometer)) }
                            toast(ctx, "اتسجل ${c.name} النهارده")
                        }) { Text("عملته") }
                    }
                }
            }
        }
    }

    if (editOdo) {
        var v by remember { mutableStateOf(if (odometer > 0) odometer.toString() else "") }
        AlertDialog(
            onDismissRequest = { editOdo = false },
            title = { Text("قراءة العداد") },
            text = { NumberField("كم", v, suffix = "كم") { v = it.filter { c -> c.isDigit() } } },
            confirmButton = {
                TextButton(onClick = {
                    v.toIntOrNull()?.let { odometer = it; prefs.odometer = it }
                    editOdo = false
                }) { Text("حفظ") }
            },
            dismissButton = { TextButton(onClick = { editOdo = false }) { Text("إلغاء") } },
        )
    }
    if (adding) CarItemEditor(null, odometer) { adding = false }
    editing?.let { c -> CarItemEditor(c, odometer) { editing = null } }
}

@Composable
private fun DocRow(name: String, t: Long, onPick: (Long) -> Unit) {
    val ctx = LocalContext.current
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(name)
            if (t > 0) Text(
                "تنتهي ${shortDate(t)} • ${dueText(t)}", style = MaterialTheme.typography.bodySmall,
                color = if (daysUntil(t) <= 30) Danger else MaterialTheme.colorScheme.outline,
            )
        }
        TextButton(onClick = { pickDate(ctx, if (t > 0) t else System.currentTimeMillis(), onPick) }) { Text(if (t > 0) "غيّر" else "حدد التاريخ") }
    }
}

@Composable
private fun CarItemEditor(existing: CarItem?, odometer: Int, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var km by remember { mutableStateOf(existing?.intervalKm?.takeIf { it > 0 }?.toString() ?: "") }
    var months by remember { mutableStateOf(existing?.intervalMonths?.takeIf { it > 0 }?.toString() ?: "") }
    var lastKm by remember { mutableStateOf((existing?.lastKm ?: odometer).takeIf { it > 0 }?.toString() ?: "") }
    var lastDate by remember { mutableStateOf(existing?.lastDate ?: System.currentTimeMillis()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "بند صيانة" else existing.name) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("البند") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField("كل كام كم", km, Modifier.weight(1f)) { km = it.filter { c -> c.isDigit() } }
                    NumberField("أو كل كام شهر", months, Modifier.weight(1f)) { months = it.filter { c -> c.isDigit() } }
                }
                NumberField("العداد آخر مرة", lastKm, suffix = "كم") { lastKm = it.filter { c -> c.isDigit() } }
                DateField("آخر مرة", lastDate, withTime = false) { lastDate = it }
                if (existing != null) {
                    TextButton(onClick = { scope.launch { SafiApp.db.dao().deleteCarItem(existing); onDismiss() } }, colors = ButtonDefaults.textButtonColors(contentColor = Danger)) {
                        Icon(Icons.Default.Delete, null); Spacer(Modifier.width(6.dp)); Text("امسح")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isNotBlank()) scope.launch {
                    SafiApp.db.dao().upsertCarItem(
                        CarItem(
                            id = existing?.id ?: 0, name = name.trim(), intervalKm = km.toIntOrNull() ?: 0,
                            intervalMonths = months.toIntOrNull() ?: 0, lastKm = lastKm.toIntOrNull() ?: 0, lastDate = lastDate,
                        ),
                    )
                    onDismiss()
                }
            }) { Text("حفظ", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}
