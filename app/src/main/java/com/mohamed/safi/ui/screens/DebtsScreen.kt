package com.mohamed.safi.ui.screens

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
import com.mohamed.safi.SafiApp
import com.mohamed.safi.ai.Debts
import com.mohamed.safi.data.*
import com.mohamed.safi.ui.*
import kotlinx.coroutines.launch

@Composable
fun DebtsScreen(onBack: () -> Unit, embedded: Boolean = false) {
    val dao = SafiApp.db.dao()
    val all by dao.debts().collectAsState(emptyList())
    var tab by remember { mutableIntStateOf(0) }
    var showClosed by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Debt?>(null) }
    var adding by remember { mutableStateOf(false) }
    var paying by remember { mutableStateOf<Debt?>(null) }

    val dir = if (tab == 0) "i_owe" else "owed_to_me"
    val list = all.filter { it.direction == dir && (showClosed || !it.closed) }
    val totalOwe = all.filter { it.direction == "i_owe" && !it.closed }.sumOf { Fx.toAed(it.remaining, it.currency) }
    val totalOwed = all.filter { it.direction == "owed_to_me" && !it.closed }.sumOf { Fx.toAed(it.remaining, it.currency) }

    ScreenScaffold(
        "السلف والديون", onBack = if (embedded) null else onBack, showTopBar = !embedded,
        actions = {
            IconButton(onClick = { showClosed = !showClosed }) {
                Icon(if (showClosed) Icons.Default.VisibilityOff else Icons.Default.Visibility, "اللي اتقفل")
            }
        },
        fab = { if (!embedded) ExtendedFloatingActionButton(onClick = { adding = true }, icon = { Icon(Icons.Default.Add, null) }, text = { Text("سلفة") }) },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AppCard(Modifier.weight(1f), onClick = { tab = 0 }, color = if (tab == 0) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerLow) {
                        Text("عليّا", style = MaterialTheme.typography.labelLarge)
                        Text(money(totalOwe), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Danger)
                    }
                    AppCard(Modifier.weight(1f), onClick = { tab = 1 }, color = if (tab == 1) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow) {
                        Text("ليّا", style = MaterialTheme.typography.labelLarge)
                        Text(money(totalOwed), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Positive)
                    }
                }
            }
            if (embedded) item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    FilterChip(
                        selected = showClosed, onClick = { showClosed = !showClosed },
                        label = { Text("اعرض اللي اتقفل") },
                        leadingIcon = { Icon(if (showClosed) Icons.Default.VisibilityOff else Icons.Default.Visibility, null, Modifier.size(18.dp)) },
                    )
                }
            }
            if (list.isEmpty()) item {
                EmptyState(Icons.Default.People, if (tab == 0) "مفيش فلوس عليك 👌" else "محدش مستلف منك")
            }
            items(list, key = { it.id }) { d ->
                val frac = if (d.amount > 0) (d.paid / d.amount).toFloat() else 0f
                val pct = (frac.coerceIn(0f, 1f) * 100).toInt()
                AppCard(onClick = { editing = d }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CatBadge(d.person, 42, Icons.Default.Person, if (d.direction == "i_owe") Danger else Positive)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(d.person, fontWeight = FontWeight.SemiBold)
                            val dueLine = buildString {
                                d.dueDate?.let { append("الميعاد: ${shortDate(it)} • ${dueText(it)}") }
                                d.monthlyInstallment?.let { if (isNotEmpty()) append(" • "); append("قسط ${money(it, d.currency)}/شهر") }
                                if (d.closed) { if (isNotEmpty()) append(" • "); append("اتقفلت ✓") }
                            }
                            if (dueLine.isNotEmpty()) Text(
                                dueLine, style = MaterialTheme.typography.bodySmall,
                                color = if (!d.closed && d.dueDate != null && daysUntil(d.dueDate) < 0) Danger else MaterialTheme.colorScheme.outline,
                            )
                            if (d.note.isNotBlank()) Text(d.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, maxLines = 1)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(money(d.remaining, d.currency), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Text(if (d.closed) "خلصت" else "باقي", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                            if (d.currency != "AED") Text("≈ ${money(Fx.toAed(d.remaining, d.currency))}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    val barColor = if (d.direction == "i_owe") Danger else Positive
                    LinearProgressIndicator(
                        progress = { frac.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                        color = barColor,
                        trackColor = barColor.copy(alpha = 0.14f),
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (d.direction == "i_owe") "سددت ${money(d.paid, d.currency)} من ${money(d.amount, d.currency)}"
                            else "رجعلك ${money(d.paid, d.currency)} من ${money(d.amount, d.currency)}",
                            style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f),
                        )
                        Text("$pct%", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = barColor)
                    }
                    if (!d.closed) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { paying = d }) { Text(if (d.direction == "i_owe") "سددت جزء / كله" else "رجّعلي جزء / كله") }
                        }
                    }
                }
            }
        }
    }

    if (adding) DebtEditor(null, if (tab == 0) "i_owe" else "owed_to_me") { adding = false }
    editing?.let { d -> DebtEditor(d, d.direction) { editing = null } }
    paying?.let { d -> PayDebtDialog(d) { paying = null } }
}

@Composable
fun PayDebtDialog(d: Debt, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var amount by remember { mutableStateOf(fmt(d.monthlyInstallment?.coerceAtMost(d.remaining) ?: d.remaining).replace(",", "")) }
    var busy by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (d.direction == "i_owe") "سداد لـ ${d.person}" else "${d.person} رجّع") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("الباقي ${money(d.remaining, d.currency)}")
                NumberField("المبلغ", amount, suffix = curLabel(d.currency)) { amount = it }
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
                            val u = Debts.pay(ctx, d, a)
                            // this month's instalment is done; don't list it as due any more
                            if (d.direction == "i_owe" && d.monthlyInstallment != null) Obligations.markInstalmentPaid(d.id)
                            toast(ctx, if (u.closed) "خلصت السلفة 🎉" else "باقي ${money(u.remaining, d.currency)}")
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

/** Add / edit a loan. [defaultDir]: i_owe | owed_to_me (the editor lets the user switch). */
@Composable
fun DebtEditor(existing: Debt?, defaultDir: String, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var dir by remember { mutableStateOf(existing?.direction ?: defaultDir) }
    var person by remember { mutableStateOf(existing?.person ?: "") }
    var amount by remember { mutableStateOf(existing?.amount?.let { fmt(it).replace(",", "") } ?: "") }
    var currency by remember { mutableStateOf(existing?.currency ?: "AED") }
    var due by remember { mutableStateOf(existing?.dueDate) }
    var installment by remember { mutableStateOf(existing?.monthlyInstallment?.let { fmt(it).replace(",", "") } ?: "") }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    var confirmDelete by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "سلفة جديدة" else "تعديل") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = dir == "i_owe", onClick = { dir = "i_owe" }, label = { Text("أنا استلفت") })
                    FilterChip(selected = dir == "owed_to_me", onClick = { dir = "owed_to_me" }, label = { Text("أنا سلّفت") })
                }
                OutlinedTextField(person, { person = it }, label = { Text(if (dir == "i_owe") "استلفت من مين" else "سلّفت مين") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField("المبلغ", amount, Modifier.weight(1.4f)) { amount = it }
                    ChoiceField("العملة", currency, CURRENCIES, Modifier.weight(1f)) { currency = it }
                }
                DateField("ميعاد السداد", due, withTime = false) { due = it }
                if (due != null) TextButton(onClick = { due = null }) { Text("من غير ميعاد") }
                NumberField("قسط شهري (اختياري)", installment) { installment = it }
                OutlinedTextField(note, { note = it }, label = { Text("ملاحظة") }, modifier = Modifier.fillMaxWidth())
                Text("هفكرك يوم الميعاد الساعة 10 الصبح، وكمان في ملخص الصبح قبلها بـ 3 أيام.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
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
                if (person.isBlank() || a <= 0) toast(ctx, "اكتب الاسم والمبلغ") else scope.launch {
                    val d = Debt(
                        id = existing?.id ?: 0, person = person.trim(), amount = a, currency = currency, direction = dir,
                        dueDate = due?.let { it.toLocalDate().millisAt(10) },
                        monthlyInstallment = installment.toDoubleOrNull()?.takeIf { it > 0 },
                        paid = existing?.paid ?: 0.0, note = note.trim(), createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                        closed = (existing?.paid ?: 0.0) >= a,
                    )
                    val id = SafiApp.db.dao().upsertDebt(d)
                    Debts.scheduleReminder(ctx, d.copy(id = if (existing != null) existing.id else id))
                    onDismiss()
                }
            }) { Text("حفظ", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
    if (confirmDelete && existing != null) {
        ConfirmDialog("مسح السلفة؟", "${existing.person} — ${money(existing.amount, existing.currency)}", "امسح", { confirmDelete = false }) {
            scope.launch {
                val dao = SafiApp.db.dao()
                dao.remindersFor("debt", existing.id).forEach {
                    com.mohamed.safi.notify.ReminderScheduler.cancel(ctx, it.id)
                    dao.deleteReminder(it)
                }
                dao.deleteDebt(existing)
                onDismiss()
            }
        }
    }
}
