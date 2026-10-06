package com.mohamed.safi.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import com.mohamed.safi.ui.Text
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mohamed.safi.SafiApp
import com.mohamed.safi.data.*
import com.mohamed.safi.extra.ExtraDb
import com.mohamed.safi.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.YearMonth

private data class FinTab(val title: String, val icon: ImageVector)

private val finTabs = listOf(
    FinTab("المصاريف", Icons.Default.Receipt),
    FinTab("تحويلات مصر", Icons.Default.SwapHoriz),
    FinTab("الالتزامات", Icons.Default.Payments),
    FinTab("السلف والديون", Icons.Default.People),
    FinTab("الادخار", Icons.Default.Savings),
    FinTab("دروس الأولاد", Icons.Default.School),
    FinTab("التقارير", Icons.Default.BarChart),
    FinTab("الزكاة", Icons.Default.VolunteerActivism),
)

/** الحسابات: every money section in one place, with one entry point for adding. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinanceScreen(onBack: (() -> Unit)?, open: (String) -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var sheet by remember { mutableStateOf(false) }
    var add by remember { mutableStateOf<String?>(null) }
    val receipt = rememberReceiptController()
    val voice = rememberVoiceInput { text ->
        UiBus.pendingVoice.value = text
        open("assistant")
    }
    var showRate by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { sheet = true }, icon = { Icon(Icons.Default.Add, null) }, text = { Text("سجّل") })
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            FinanceHeader(onBack, onRate = { showRate = true })
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(finTabs) { i, t ->
                    FilterChip(
                        selected = tab == i, onClick = { tab = i },
                        label = { Text(t.title) },
                        leadingIcon = { Icon(t.icon, null, Modifier.size(18.dp)) },
                    )
                }
            }
            Box(Modifier.weight(1f)) {
                val back: () -> Unit = {}
                when (tab) {
                    0 -> ExpensesScreen(embedded = true)
                    1 -> TransfersScreen(back, embedded = true)
                    2 -> BillsScreen(back, embedded = true)
                    3 -> DebtsScreen(back, embedded = true)
                    4 -> SavingsScreen(back, embedded = true)
                    5 -> LessonsScreen(back, embedded = true)
                    6 -> ReportsScreen(back, embedded = true)
                    else -> ZakatScreen(back, embedded = true)
                }
            }
        }
    }

    if (sheet) {
        ModalBottomSheet(onDismissRequest = { sheet = false }) {
            Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("سجّل جديد", style = MaterialTheme.typography.titleLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { sheet = false; voice() }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Mic, null); Spacer(Modifier.width(6.dp)); Text("سجّل بالصوت")
                    }
                    FilledTonalButton(onClick = { sheet = false; receipt.open() }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.CameraAlt, null); Spacer(Modifier.width(6.dp)); Text("صوّر فاتورة")
                    }
                }
                val opts = listOf(
                    Triple("expense", "مصروف", Icons.Default.ShoppingCart),
                    Triple("income", "دخل", Icons.Default.AccountBalanceWallet),
                    Triple("transfer", "تحويل مصر", Icons.Default.SwapHoriz),
                    Triple("bill", "فاتورة أو التزام", Icons.Default.Payments),
                    Triple("debt_i", "سلفة عليّا", Icons.Default.CallReceived),
                    Triple("debt_o", "سلفة ليّا", Icons.Default.CallMade),
                    Triple("goal", "هدف ادخار", Icons.Default.Savings),
                    Triple("lesson", "درس للأولاد", Icons.Default.School),
                )
                opts.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { (k, label, icon) ->
                            OutlinedCard(onClick = { sheet = false; add = k }, modifier = Modifier.weight(1f)) {
                                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(10.dp))
                                    Text(label, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }

    val lessons by remember { ExtraDb.dao.lessons() }.collectAsState(emptyList())
    when (add) {
        "expense" -> ExpenseEditor(existing = null) { add = null }
        "income" -> ExpenseEditor(existing = null, startIncome = true) { add = null }
        "transfer" -> TransferEditor(null, null) { add = null }
        "bill" -> ObligationEditor(null) { add = null }
        "debt_i" -> DebtEditor(null, "i_owe") { add = null }
        "debt_o" -> DebtEditor(null, "owed_to_me") { add = null }
        "goal" -> GoalDialog(null) { add = null }
        "lesson" -> LessonDialog(null, lessons.map { it.child }.distinct()) { add = null }
    }
    if (showRate) RateDialog { showRate = false }
    ReceiptHost(receipt)
}

@Composable
private fun FinanceHeader(onBack: (() -> Unit)?, onRate: () -> Unit) {
    val dao = SafiApp.db.dao()
    val prefs = SafiApp.prefs
    val ym = remember { YearMonth.now(zone) }
    val (from, to) = remember(ym) { monthRange(ym) }
    val month by remember(from, to) { dao.expensesBetween(from, to) }.collectAsState(emptyList())
    val transfers by remember(from, to) { dao.transfersBetween(from, to) }.collectAsState(emptyList())
    val debts by remember { dao.debts() }.collectAsState(emptyList())
    val budgets by remember { dao.budgets() }.collectAsState(emptyList())
    var obligations by remember { mutableStateOf<List<Obligation>>(emptyList()) }
    LaunchedEffect(month.size, debts.size) { obligations = runCatching { Obligations.forMonth(ym) }.getOrDefault(emptyList()) }

    val spent = month.filter { !it.isIncome }.sumOf { it.amountAed }
    val income = month.filter { it.isIncome }.sumOf { it.amountAed }
    val sentEgp = transfers.sumOf { it.amountEgp }
    val sentAed = transfers.sumOf { it.amountAed + it.feesAed }
    val budget = budgets.sumOf { it.monthlyLimit }
    val open = debts.filter { !it.closed }
    val iOwe = open.filter { it.direction == "i_owe" }.sumOf { Fx.toAed(it.remaining, it.currency) }
    val owedMe = open.filter { it.direction != "i_owe" }.sumOf { Fx.toAed(it.remaining, it.currency) }
    val dueLeft = obligations.filter { it.due >= System.currentTimeMillis() - 86_400_000L || it.overdue }
    val overdue = obligations.count { it.overdue }

    Surface(color = BrandDeep, shape = MaterialTheme.shapes.large.copy(topStart = androidx.compose.foundation.shape.CornerSize(0.dp), topEnd = androidx.compose.foundation.shape.CornerSize(0.dp))) {
        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 18.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع", tint = Color.White) }
                Text("الحسابات", color = Color.White, fontFamily = Amiri, fontWeight = FontWeight.Bold, fontSize = 24.sp, modifier = Modifier.weight(1f))
                AssistChip(
                    onClick = onRate,
                    label = { Text("1 د.إ = ${fmt(prefs.egpPerAed)} ج.م", color = Color.White) },
                    leadingIcon = { Icon(Icons.Default.CurrencyExchange, null, Modifier.size(16.dp), tint = GoldSoft) },
                    border = AssistChipDefaults.assistChipBorder(true, borderColor = GoldSoft.copy(alpha = 0.5f)),
                )
            }
            Text("صرفت في ${monthName(ym)}", color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.bodySmall)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(money(spent), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 30.sp)
                if (budget > 0) Text("  من ${money(budget)}", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 6.dp))
            }
            if (budget > 0) {
                val frac = (spent / budget).toFloat().coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { frac }, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).height(6.dp),
                    color = if (spent > budget) Danger else Gold, trackColor = Color.White.copy(alpha = 0.15f), drawStopIndicator = {},
                )
            }
            Spacer(Modifier.height(8.dp))
            Row {
                HeaderStat("الدخل", money(income), Modifier.weight(1f))
                HeaderStat("الصافي", money(income - spent), Modifier.weight(1f), if (income - spent < 0) Color(0xFFFFB4A9) else Color.White)
                HeaderStat("تحويلات مصر", money(sentEgp, "EGP"), Modifier.weight(1f), sub = "≈ ${money(sentAed)}")
            }
            Spacer(Modifier.height(6.dp))
            Row {
                HeaderStat("مطلوب الشهر ده", money(dueLeft.sumOf { it.amountAed }), Modifier.weight(1f), sub = if (overdue > 0) "$overdue متأخر" else "${dueLeft.size} التزام")
                HeaderStat("عليّا", money(iOwe), Modifier.weight(1f))
                HeaderStat("ليّا", money(owedMe), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun HeaderStat(label: String, value: String, modifier: Modifier, color: Color = Color.White, sub: String? = null) {
    Column(modifier) {
        Text(label, color = Color.White.copy(alpha = 0.65f), style = MaterialTheme.typography.labelSmall)
        Text(value, color = color, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
        if (sub != null) Text(sub, color = GoldSoft.copy(alpha = 0.9f), style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

@Composable
private fun RateDialog(onDismiss: () -> Unit) {
    val prefs = SafiApp.prefs
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var rate by remember { mutableStateOf(prefs.egpPerAed.toString()) }
    var auto by remember { mutableStateOf(prefs.rateAuto) }
    var updated by remember { mutableLongStateOf(prefs.rateUpdated) }
    var busy by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("سعر الجنيه") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(rate, { rate = it }, label = { Text("كام جنيه في الدرهم") }, singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("تحديث تلقائي كل يوم")
                        Text(if (updated > 0) "آخر تحديث: ${dateTimeStr(updated)}" else "لسه ماتحدثش", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    }
                    Switch(auto, { auto = it; prefs.rateAuto = it })
                }
                OutlinedButton(onClick = {
                    busy = true
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) { runCatching { Fx.refresh() }.getOrDefault(false) }
                        rate = prefs.egpPerAed.toString(); updated = prefs.rateUpdated; busy = false
                        toast(ctx, if (ok) "اتحدث" else "مقدرتش أحدث، اتأكد من النت")
                    }
                }, enabled = !busy) { Text(if (busy) "بيحدث…" else "حدّث دلوقتي") }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                rate.toDoubleOrNull()?.takeIf { it > 0 }?.let { prefs.egpPerAed = it }
                onDismiss()
            }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}
