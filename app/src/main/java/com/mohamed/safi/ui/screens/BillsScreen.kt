package com.mohamed.safi.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import com.mohamed.safi.ai.Assistant
import com.mohamed.safi.ai.Bills
import com.mohamed.safi.data.*
import com.mohamed.safi.ui.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

private data class Preset(val name: String, val kind: String, val category: String, val currency: String, val frequency: String = "monthly")

private val presets = listOf(
    Preset("الإيجار", "bill", Cats.RENT, "AED"),
    Preset("كهرباء ومياه", "bill", Cats.UTILITIES, "AED"),
    Preset("الموبايل", "bill", Cats.TELECOM, "AED"),
    Preset("الإنترنت", "bill", Cats.TELECOM, "AED"),
    Preset("تأمين العربية", "bill", Cats.CAR, "AED", "yearly"),
    Preset("ترخيص العربية", "bill", Cats.CAR, "AED", "yearly"),
    Preset("قسط العربية", "installment", Cats.CAR, "AED"),
    Preset("بطاقة الائتمان", "bill", Cats.FEES, "AED"),
    Preset("مصروف ماما", "transfer", "ماما", "EGP"),
    Preset("مصاريف البيت", "transfer", "البيت", "EGP"),
    Preset("دروس الأولاد", "transfer", "دروس الأولاد", "EGP"),
    Preset("الصيدلية", "transfer", "الصيدلية", "EGP"),
    Preset("اشتراك", "subscription", Cats.ONLINE, "AED"),
)

private fun kindLabel(k: String) = when (k) {
    "transfer" -> "تحويل مصر"
    "subscription" -> "اشتراك"
    "installment" -> "قسط"
    else -> "فاتورة"
}

@Composable
fun BillsScreen(onBack: () -> Unit, embedded: Boolean = false) {
    val dao = SafiApp.db.dao()
    val bills by dao.bills().collectAsState(emptyList())
    val debts by dao.debts().collectAsState(emptyList())
    var obligations by remember { mutableStateOf<List<Obligation>>(emptyList()) }
    var ym by remember { mutableStateOf(YearMonth.now(zone)) }
    LaunchedEffect(bills, debts, ym) { obligations = Obligations.forMonth(ym) }
    var editing by remember { mutableStateOf<Bill?>(null) }
    var preset by remember { mutableStateOf<Preset?>(null) }
    var adding by remember { mutableStateOf(false) }
    var paying by remember { mutableStateOf<Bill?>(null) }

    ScreenScaffold(
        "الفواتير والالتزامات", onBack = if (embedded) null else onBack, showTopBar = !embedded,
        fab = { if (!embedded) ExtendedFloatingActionButton(onClick = { adding = true }, icon = { Icon(Icons.Default.Add, null) }, text = { Text("التزام") }) },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                MonthSwitcher(ym) { ym = it }
            }
            item {
                val total = obligations.sumOf { it.amountAed }
                val egp = obligations.filter { it.currency == "EGP" }.sumOf { it.amount }
                AppCard(color = MaterialTheme.colorScheme.primaryContainer) {
                    Text("مطلوب منك في ${monthName(ym)}", color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(money(total), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    if (egp > 0) Text("منها ${money(egp, "EGP")} تحويلات مصر", color = MaterialTheme.colorScheme.onPrimaryContainer)
                    val late = obligations.count { it.overdue }
                    if (obligations.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Pill("${obligations.size} التزام")
                            if (late > 0) Pill("$late متأخر", Danger)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    if (obligations.isNotEmpty()) HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f))
                    obligations.forEach { o ->
                        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(o.title, Modifier.weight(1f), maxLines = 1)
                            Text(
                                shortDate(o.due), style = MaterialTheme.typography.bodySmall,
                                color = if (o.overdue) Danger else MaterialTheme.colorScheme.outline,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(money(o.amount, o.currency), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
            item {
                Text("ضيف بسرعة", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(presets) { p -> AssistChip(onClick = { preset = p }, label = { Text(p.name) }) }
                }
            }
            item { SectionTitle("التزاماتك") }
            if (bills.isEmpty()) item { EmptyState(Icons.Default.Payments, "ضيف الإيجار والكهرباء والاتصالات وتحويلات مصر الشهرية — من \"ضيف بسرعة\" فوق") }
            items(bills, key = { it.id }) { b ->
                val d = daysUntil(b.nextDue)
                val statusColor = when {
                    d < 0 -> Danger
                    d <= b.remindDaysBefore -> Warn
                    else -> MaterialTheme.colorScheme.outline
                }
                AppCard(onClick = { editing = b }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CatBadge(b.category, 42, if (b.kind == "transfer") Icons.Default.SwapHoriz else catIcon(b.category))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(b.name, fontWeight = FontWeight.SemiBold)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Pill(kindLabel(b.kind))
                                Text(Assistant.freqLabel(b.frequency), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                            }
                            Text("${shortDate(b.nextDue)} • ${dueText(b.nextDue)}", style = MaterialTheme.typography.bodySmall, color = statusColor)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(money(b.amount, b.currency), fontWeight = FontWeight.Bold)
                            if (b.currency != "AED") Text("≈ ${money(Fx.toAed(b.amount, b.currency))}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                            Spacer(Modifier.height(4.dp))
                            FilledTonalButton(onClick = { paying = b }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) { Text("دفعت") }
                        }
                    }
                }
            }
        }
    }

    if (adding) BillEditor(null, null) { adding = false }
    preset?.let { p -> BillEditor(null, p) { preset = null } }
    editing?.let { b -> BillEditor(b, null) { editing = null } }
    paying?.let { b -> PayBillDialog(b) { paying = null } }
}

@Composable
fun PayBillDialog(b: Bill, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var amount by remember { mutableStateOf(fmt(b.amount).replace(",", "")) }
    var busy by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("دفعت ${b.name}؟") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("لو المبلغ اتغير المرة دي عدّله:", style = MaterialTheme.typography.bodyMedium)
                NumberField("المبلغ", amount, suffix = curLabel(b.currency)) { amount = it }
                Text(
                    if (b.kind == "transfer") "هيتسجل كتحويل مصر — ${b.category}" else "هيتسجل مصروف في ${b.category}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline,
                )
            }
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = {
                val a = amount.toDoubleOrNull() ?: 0.0
                if (busy) return@TextButton
                if (a <= 0) toast(ctx, "اكتب المبلغ") else {
                    busy = true
                    scope.launch {
                        try {
                            Bills.markPaid(ctx, b, a)
                            toast(ctx, "تمام، الميعاد الجاي اتحدد")
                            onDismiss()
                        } finally {
                            busy = false
                        }
                    }
                }
            }) { Text("تأكيد", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}

/** Add / edit a bill or obligation (public entry point for the finance hub). */
@Composable
fun ObligationEditor(existing: Bill?, onDismiss: () -> Unit) = BillEditor(existing, null, onDismiss)

@Composable
private fun BillEditor(existing: Bill?, preset: Preset?, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = SafiApp.prefs
    var name by remember { mutableStateOf(existing?.name ?: preset?.name ?: "") }
    var kind by remember { mutableStateOf(existing?.kind ?: preset?.kind ?: "bill") }
    var category by remember { mutableStateOf(existing?.category ?: preset?.category ?: Cats.UTILITIES) }
    var amount by remember { mutableStateOf(existing?.amount?.let { fmt(it).replace(",", "") } ?: "") }
    var currency by remember { mutableStateOf(existing?.currency ?: preset?.currency ?: "AED") }
    var frequency by remember { mutableStateOf(existing?.frequency ?: preset?.frequency ?: "monthly") }
    var nextDue by remember { mutableStateOf(existing?.nextDue ?: LocalDate.now(zone).plusMonths(1).withDayOfMonth(1).millisAt(9)) }
    var remind by remember { mutableStateOf((existing?.remindDaysBefore ?: 2).toString()) }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    var confirmDelete by remember { mutableStateOf(false) }
    val catOptions = if (kind == "transfer") prefs.transferCats else Cats.expense

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "التزام جديد" else "تعديل الالتزام") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("الاسم") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                ChoiceField("النوع", kind, listOf("bill", "transfer", "subscription", "installment"), display = ::kindLabel) {
                    kind = it
                    if (it == "transfer") { currency = "EGP"; category = prefs.transferCats.first() }
                    else if (category !in Cats.expense) category = Cats.OTHER
                }
                ChoiceField(if (kind == "transfer") "البند" else "التصنيف", category, catOptions) { category = it }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField("المبلغ", amount, Modifier.weight(1.4f)) { amount = it }
                    ChoiceField("العملة", currency, CURRENCIES, Modifier.weight(1f)) { currency = it }
                }
                ChoiceField("التكرار", frequency, listOf("monthly", "quarterly", "yearly", "weekly", "once"), display = Assistant::freqLabel) { frequency = it }
                DateField("الميعاد الجاي", nextDue, withTime = false) { nextDue = it }
                NumberField("فكرني قبلها بكام يوم", remind) { remind = it }
                OutlinedTextField(note, { note = it }, label = { Text("ملاحظة (رقم الحساب، طريقة الدفع…)") }, modifier = Modifier.fillMaxWidth())
                if (existing != null) {
                    TextButton(onClick = { confirmDelete = true }, colors = ButtonDefaults.textButtonColors(contentColor = Danger)) {
                        Icon(Icons.Default.Delete, null); Spacer(Modifier.width(6.dp)); Text("امسح")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val a = amount.toDoubleOrNull() ?: 0.0
                if (name.isBlank() || a <= 0) toast(ctx, "اكتب الاسم والمبلغ") else scope.launch {
                    // the chosen day becomes the bill's anchor day (so 31st stays 31st after short months)
                    if (existing != null) Obligations.setAnchor(existing.id, nextDue)
                    val newId = SafiApp.db.dao().upsertBill(
                        Bill(
                            id = existing?.id ?: 0, name = name.trim(), kind = kind, category = category, amount = a,
                            currency = currency, frequency = frequency, nextDue = nextDue,
                            remindDaysBefore = remind.toIntOrNull() ?: 2, note = note.trim(), lastPaid = existing?.lastPaid,
                        ),
                    )
                    if (existing == null) Obligations.setAnchor(newId, nextDue)
                    onDismiss()
                }
            }) { Text("حفظ", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
    if (confirmDelete && existing != null) {
        ConfirmDialog("مسح ${existing.name}؟", "مش هفكرك بيه تاني", "امسح", { confirmDelete = false }) {
            scope.launch { SafiApp.db.dao().deleteBill(existing); onDismiss() }
        }
    }
}
