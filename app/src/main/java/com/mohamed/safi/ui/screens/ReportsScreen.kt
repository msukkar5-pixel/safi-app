package com.mohamed.safi.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mohamed.safi.SafiApp
import com.mohamed.safi.data.*
import com.mohamed.safi.ui.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun ReportsScreen(onBack: () -> Unit) {
    val dao = SafiApp.db.dao()
    val scope = rememberCoroutineScope()
    var ym by remember { mutableStateOf(YearMonth.now(zone)) }
    val (from, to) = remember(ym) { monthRange(ym) }
    val (pFrom, pTo) = remember(ym) { monthRange(ym.minusMonths(1)) }
    val list by dao.expensesBetween(from, to).collectAsState(emptyList())
    val prev by dao.expensesBetween(pFrom, pTo).collectAsState(emptyList())
    val transfers by dao.transfersBetween(from, to).collectAsState(emptyList())
    val budgets by dao.budgets().collectAsState(emptyList())
    var budgetFor by remember { mutableStateOf<String?>(null) }

    val out = list.filter { !it.isIncome }
    val spent = out.sumOf { it.amountAed }
    val income = list.filter { it.isIncome }.sumOf { it.amountAed }
    val sent = transfers.sumOf { it.amountAed + it.feesAed }
    val prevByCat = prev.filter { !it.isIncome }.groupBy { it.category }.mapValues { e -> e.value.sumOf { it.amountAed } }
    val byCat = out.groupBy { it.category }.mapValues { e -> e.value.sumOf { it.amountAed } }
    val budgetMap = budgets.associate { it.category to it.monthlyLimit }
    val cats = (byCat.keys + budgetMap.keys).distinct().sortedByDescending { byCat[it] ?: 0.0 }
    val days = if (ym == YearMonth.now(zone)) LocalDate.now(zone).dayOfMonth else ym.lengthOfMonth()
    val topMerchants = out.filter { it.merchant.isNotBlank() }.groupBy { it.merchant.lowercase() }
        .map { (_, v) -> v.first().merchant to v.sumOf { it.amountAed } }.sortedByDescending { it.second }.take(6)
    val cashTotal = out.filter { it.method == "cash" }.sumOf { it.amountAed }

    ScreenScaffold("التقارير والميزانية", onBack = onBack) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { MonthSwitcher(ym) { ym = it } }
            item {
                val ctx = androidx.compose.ui.platform.LocalContext.current
                var pdfBusy by remember { mutableStateOf(false) }
                OutlinedButton(onClick = {
                    pdfBusy = true
                    scope.launch {
                        try { com.mohamed.safi.extra.MonthReport.share(ctx, com.mohamed.safi.extra.MonthReport.build(ctx, ym)) }
                        catch (e: Exception) { com.mohamed.safi.ui.toast(ctx, e.message ?: "فشل") }
                        pdfBusy = false
                    }
                }, enabled = !pdfBusy, modifier = Modifier.fillMaxWidth()) {
                    if (pdfBusy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else { Icon(Icons.Default.PictureAsPdf, null); Spacer(Modifier.width(6.dp)); Text("تقرير الشهر PDF") }
                }
            }
            item {
                AppCard {
                    Row {
                        StatBlock("المصروف", money(spent), Modifier.weight(1f))
                        StatBlock("تحويلات مصر", money(sent), Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(12.dp))
                    Row {
                        StatBlock("الدخل", money(income), Modifier.weight(1f), valueColor = Positive)
                        val left = income - spent - sent
                        StatBlock("الصافي", money(left), Modifier.weight(1f), valueColor = if (left >= 0) Positive else Danger)
                    }
                    Spacer(Modifier.height(12.dp))
                    Row {
                        StatBlock("متوسط اليوم", money(spent / days.coerceAtLeast(1)), Modifier.weight(1f))
                        StatBlock("كاش", money(cashTotal), Modifier.weight(1f))
                    }
                }
            }
            item {
                SectionTitle("حسب التصنيف")
                Text("دوس على أي تصنيف علشان تحددله ميزانية شهرية", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
            item {
                AppCard {
                    if (cats.isEmpty()) Text("مفيش مصاريف", color = MaterialTheme.colorScheme.outline)
                    val maxV = (byCat.values.maxOrNull() ?: 1.0).coerceAtLeast(1.0)
                    cats.forEach { cat ->
                        val v = byCat[cat] ?: 0.0
                        val limit = budgetMap[cat]
                        val p = prevByCat[cat] ?: 0.0
                        val sub = buildString {
                            if (spent > 0) append("${(v / spent * 100).toInt()}%")
                            if (p > 0) {
                                val d = (v - p) / p * 100
                                append(" • ${if (d >= 0) "▲" else "▼"} ${kotlin.math.abs(d).toInt()}% عن الشهر اللي فات")
                            }
                            if (limit != null) append(" • الميزانية ${money(limit)}")
                        }
                        val frac = if (limit != null && limit > 0) (v / limit).toFloat() else (v / maxV).toFloat()
                        val color = when {
                            limit == null -> catColor(cat)
                            v >= limit -> Danger
                            v >= limit * 0.8 -> Warn
                            else -> Positive
                        }
                        BarRow(cat, money(v), frac, color, catIcon(cat), sub) { budgetFor = cat }
                    }
                }
            }
            if (topMerchants.isNotEmpty()) {
                item { SectionTitle("أكتر أماكن صرفت فيها") }
                item {
                    AppCard {
                        val maxM = topMerchants.first().second.coerceAtLeast(1.0)
                        topMerchants.forEach { (m, v) -> BarRow(m, money(v), (v / maxM).toFloat(), Color2) }
                    }
                }
            }
            if (transfers.isNotEmpty()) {
                item { SectionTitle("تحويلات مصر حسب البند") }
                item {
                    AppCard {
                        val t = transfers.groupBy { it.category }.mapValues { e -> e.value.sumOf { it.amountEgp } }.entries.sortedByDescending { it.value }
                        val maxT = t.first().value.coerceAtLeast(1.0)
                        t.forEach { (c, v) -> BarRow(c, money(v, "EGP"), (v / maxT).toFloat(), catColor(c)) }
                    }
                }
            }
        }
    }

    budgetFor?.let { cat ->
        var v by remember(cat) { mutableStateOf(budgetMap[cat]?.let { fmt(it).replace(",", "") } ?: "") }
        AlertDialog(
            onDismissRequest = { budgetFor = null },
            title = { Text("ميزانية $cat") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField("الحد الشهري", v, suffix = "د.إ") { v = it }
                    Text("هنبهك لما توصل 80% ولما تعدي الحد.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val x = v.toDoubleOrNull()
                    scope.launch {
                        if (x == null || x <= 0) budgets.firstOrNull { it.category == cat }?.let { dao.deleteBudget(it) }
                        else dao.upsertBudget(Budget(cat, x))
                    }
                    budgetFor = null
                }) { Text("حفظ") }
            },
            dismissButton = { TextButton(onClick = { budgetFor = null }) { Text("إلغاء") } },
        )
    }
}
