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
import java.time.YearMonth

@Composable
fun TransfersScreen(onBack: () -> Unit) {
    val dao = SafiApp.db.dao()
    val prefs = SafiApp.prefs
    val scope = rememberCoroutineScope()
    var ym by remember { mutableStateOf(YearMonth.now(zone)) }
    val (from, to) = remember(ym) { monthRange(ym) }
    val list by dao.transfersBetween(from, to).collectAsState(emptyList())
    val fromBank by dao.expensesInCategory(Cats.TRANSFER).collectAsState(emptyList())
    var editing by remember { mutableStateOf<Transfer?>(null) }
    var adding by remember { mutableStateOf(false) }
    var classify by remember { mutableStateOf<Expense?>(null) }

    val totalEgp = list.sumOf { it.amountEgp }
    val totalAed = list.sumOf { it.amountAed }
    val fees = list.sumOf { it.feesAed }
    val byCat = list.groupBy { it.category }.mapValues { e -> e.value.sumOf { it.amountEgp } to e.value.sumOf { it.amountAed } }
        .entries.sortedByDescending { it.value.first }

    ScreenScaffold(
        "تحويلات مصر", onBack = onBack,
        fab = { ExtendedFloatingActionButton(onClick = { adding = true }, icon = { Icon(Icons.Default.Add, null) }, text = { Text("تحويل") }) },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { MonthSwitcher(ym) { ym = it } }
            item {
                AppCard(color = MaterialTheme.colorScheme.primaryContainer) {
                    Text("اتحول لمصر في ${monthName(ym)}", color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(money(totalEgp, "EGP"), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("= ${money(totalAed)}" + if (fees > 0) " + رسوم ${money(fees)}" else "", color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text("السعر الحالي: 1 درهم = ${prefs.egpPerAed} جنيه", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
            }
            if (fromBank.isNotEmpty()) {
                item {
                    AppCard(color = MaterialTheme.colorScheme.tertiaryContainer) {
                        Text("تحويلات من رسايل البنك محتاجة تتصنف", fontWeight = FontWeight.Bold)
                        Text("قولّي كل تحويل راح لمين", style = MaterialTheme.typography.bodySmall)
                        fromBank.take(6).forEach { e ->
                            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(money(e.amount, e.currency) + " — " + e.merchant.ifBlank { "تحويل" }, maxLines = 1)
                                    Text(dateTimeStr(e.time), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                }
                                FilledTonalButton(onClick = { classify = e }) { Text("صنّف") }
                            }
                        }
                    }
                }
            }
            if (byCat.isNotEmpty()) {
                item { SectionTitle("حسب البند") }
                item {
                    AppCard {
                        val max = byCat.maxOf { it.value.first }.coerceAtLeast(1.0)
                        byCat.forEach { (cat, v) ->
                            BarRow(
                                cat, money(v.first, "EGP"), (v.first / max).toFloat(), catColor(cat),
                                Icons.Default.Person, sub = "≈ ${money(v.second)}",
                            )
                        }
                    }
                }
            }
            item { SectionTitle("كل التحويلات") }
            if (list.isEmpty()) item { EmptyState(Icons.Default.SwapHoriz, "مفيش تحويلات الشهر ده") }
            items(list, key = { it.id }) { t ->
                AppCard(onClick = { editing = t }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CatBadge(t.category, 40, Icons.Default.Person)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(t.category + if (t.recipient.isNotBlank()) " • ${t.recipient}" else "", fontWeight = FontWeight.SemiBold)
                            Text(dateTimeStr(t.time) + if (t.note.isNotBlank()) " • ${t.note}" else "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, maxLines = 1)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(money(t.amountEgp, "EGP"), fontWeight = FontWeight.Bold)
                            Text("${money(t.amountAed)} @${t.rate}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }
        }
    }

    if (adding) TransferEditor(null, null) { adding = false }
    editing?.let { t -> TransferEditor(t, null) { editing = null } }
    classify?.let { e ->
        TransferEditor(null, e) { classify = null }
    }
}

/** [fromExpense]: a bank-SMS expense being re-classified as an Egypt transfer (the expense is removed to avoid double counting). */
@Composable
fun TransferEditor(existing: Transfer?, fromExpense: Expense?, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = SafiApp.prefs
    var cats by remember { mutableStateOf(prefs.transferCats) }
    var category by remember { mutableStateOf(existing?.category ?: cats.first()) }
    var rate by remember { mutableStateOf((existing?.rate ?: prefs.egpPerAed).toString()) }
    val initialEgp = existing?.amountEgp ?: fromExpense?.let { it.amountAed * prefs.egpPerAed }
    var egp by remember { mutableStateOf(initialEgp?.let { fmt(it).replace(",", "") } ?: "") }
    var fees by remember { mutableStateOf(existing?.feesAed?.takeIf { it > 0 }?.toString() ?: "") }
    var recipient by remember { mutableStateOf(existing?.recipient ?: "") }
    var note by remember { mutableStateOf(existing?.note ?: fromExpense?.merchant ?: "") }
    var time by remember { mutableStateOf(existing?.time ?: fromExpense?.time ?: System.currentTimeMillis()) }
    var newCat by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "تحويل لمصر" else "تعديل التحويل") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("البند", style = MaterialTheme.typography.labelLarge)
                ChipsRow(cats, category, { it }) { category = it }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(newCat, { newCat = it }, label = { Text("بند جديد") }, singleLine = true, modifier = Modifier.weight(1f))
                    IconButton(onClick = {
                        val c = newCat.trim()
                        if (c.isNotEmpty() && c !in cats) {
                            cats = cats + c
                            prefs.transferCats = cats
                            category = c
                            newCat = ""
                        }
                    }) { Icon(Icons.Default.Add, "ضيف") }
                }
                NumberField("المبلغ بالجنيه", egp, suffix = "ج.م") { egp = it }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField("السعر (جنيه/درهم)", rate, Modifier.weight(1f)) { rate = it }
                    NumberField("رسوم بالدرهم", fees, Modifier.weight(1f)) { fees = it }
                }
                val e = egp.toDoubleOrNull()
                val r = rate.toDoubleOrNull()
                if (e != null && r != null && r > 0) {
                    Text("= ${money(e / r)}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
                OutlinedTextField(recipient, { recipient = it }, label = { Text("اسم المستلم (اختياري)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(note, { note = it }, label = { Text("ملاحظة") }, modifier = Modifier.fillMaxWidth())
                DateField("الوقت", time) { time = it }
                if (existing != null) {
                    TextButton(onClick = { confirmDelete = true }, colors = ButtonDefaults.textButtonColors(contentColor = Danger)) {
                        Icon(Icons.Default.Delete, null); Spacer(Modifier.width(6.dp)); Text("امسح")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val e = egp.toDoubleOrNull() ?: 0.0
                val r = rate.toDoubleOrNull() ?: 0.0
                if (e <= 0 || r <= 0) toast(ctx, "اكتب المبلغ والسعر") else scope.launch {
                    val dao = SafiApp.db.dao()
                    val t = Transfer(
                        id = existing?.id ?: 0, amountEgp = e, rate = r, amountAed = e / r,
                        feesAed = fees.toDoubleOrNull() ?: 0.0, category = category, recipient = recipient.trim(),
                        note = note.trim(), time = time,
                    )
                    dao.upsertTransfer(t)
                    if (fromExpense != null) dao.deleteExpense(fromExpense)
                    onDismiss()
                }
            }) { Text("حفظ", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
    if (confirmDelete && existing != null) {
        ConfirmDialog("مسح التحويل؟", money(existing.amountEgp, "EGP"), "امسح", { confirmDelete = false }) {
            scope.launch { SafiApp.db.dao().deleteTransfer(existing); onDismiss() }
        }
    }
}
