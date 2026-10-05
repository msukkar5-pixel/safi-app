package com.mohamed.safi.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.mohamed.safi.sms.SmsProcessor
import com.mohamed.safi.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.YearMonth

/** [embedded]: shown inside the finance hub (no top bar / FAB; tools move into the list). */
@Composable
fun ExpensesScreen(embedded: Boolean = false) {
    val dao = SafiApp.db.dao()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var ym by remember { mutableStateOf(YearMonth.now(zone)) }
    val (from, to) = remember(ym) { monthRange(ym) }
    val all by remember(from, to) { dao.expensesBetween(from, to) }.collectAsState(emptyList())
    var filter by remember { mutableStateOf("الكل") }
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Expense?>(null) }
    var adding by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    val receipt = rememberReceiptController()

    val cats = listOf("الكل") + all.map { it.category }.distinct()
    val list = all.filter { (filter == "الكل" || it.category == filter) &&
        (query.isBlank() || it.merchant.contains(query, true) || it.note.contains(query, true) || it.category.contains(query) || it.placeName.contains(query, true)) }
    val spent = list.filter { !it.isIncome }.sumOf { it.amountAed }
    val income = list.filter { it.isIncome }.sumOf { it.amountAed }
    val cash = list.filter { !it.isIncome && it.method == "cash" }.sumOf { it.amountAed }

    fun pasteBankSms() {
        val clip = (ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager)
            .primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(ctx)?.toString()
        if (clip.isNullOrBlank()) {
            toast(ctx, "انسخ رسالة البنك (أو كذا رسالة) الأول، وبعدين دوس هنا")
        } else {
            importing = true
            scope.launch {
                val r = withContext(Dispatchers.IO) { SmsProcessor.processText(ctx, clip) }
                importing = false
                toast(
                    ctx,
                    when {
                        r.added.isNotEmpty() -> "اتسجل ${r.added.size} عملية" + if (r.duplicates > 0) " (${r.duplicates} متسجلة قبل كده)" else ""
                        r.duplicates > 0 -> "العمليات دي متسجلة قبل كده"
                        else -> "مفيش عمليات جديدة في اللي نسخته"
                    },
                )
            }
        }
    }

    ScreenScaffold(
        "المصاريف",
        showTopBar = !embedded,
        actions = {
            IconButton(onClick = { searching = !searching; if (!searching) query = "" }) { Icon(Icons.Default.Search, "بحث") }
            IconButton(onClick = { pasteBankSms() }) {
                if (importing) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp) else Icon(Icons.Default.ContentPaste, "الصق رسايل البنك")
            }
        },
        fab = {
            if (!embedded) Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SmallFloatingActionButton(onClick = { receipt.open() }) { Icon(Icons.Default.CameraAlt, "فاتورة") }
                ExtendedFloatingActionButton(onClick = { adding = true }, icon = { Icon(Icons.Default.Add, null) }, text = { Text("مصروف") })
            }
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item {
                if (embedded) {
                    // the hub has no top bar: month on one side, tools on the other
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) { MonthSwitcher(ym) { ym = it } }
                        IconButton(onClick = { searching = !searching; if (!searching) query = "" }) { Icon(Icons.Default.Search, "بحث") }
                        IconButton(onClick = { pasteBankSms() }) {
                            if (importing) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp) else Icon(Icons.Default.ContentPaste, "الصق رسايل البنك")
                        }
                        IconButton(onClick = { receipt.open() }) { Icon(Icons.Default.CameraAlt, "صوّر فاتورة") }
                    }
                } else MonthSwitcher(ym) { ym = it }
            }
            if (searching) {
                item {
                    OutlinedTextField(query, { query = it }, placeholder = { Text("دوّر على محل، ملاحظة، مكان…") }, singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Search, null) }, modifier = Modifier.fillMaxWidth())
                }
            }
            item {
                AppCard {
                    Row {
                        StatBlock("المصروف", money(spent), Modifier.weight(1f), sub = "${list.count { !it.isIncome }} عملية")
                        StatBlock("منه كاش", money(cash), Modifier.weight(1f))
                        if (income > 0) StatBlock("الدخل", money(income), Modifier.weight(1f), valueColor = Positive)
                    }
                }
            }
            item { ChipsRow(cats, filter, { it }) { filter = it } }
            if (list.isEmpty()) item {
                EmptyState(
                    Icons.Default.Receipt,
                    if (query.isNotBlank() || filter != "الكل") "مفيش نتايج — جرّب كلمة تانية أو التصنيف \"الكل\""
                    else "مفيش مصاريف الشهر ده — دوس + وسجّل أول مصروف، أو الصق رسالة البنك",
                )
            }
            val byDay = list.groupBy { it.time.toLocalDate() }
            byDay.forEach { (day, dayItems) ->
                item(key = "d$day") {
                    Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(shortDate(dayItems.first().time), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text(money(dayItems.filter { !it.isIncome }.sumOf { it.amountAed }), color = MaterialTheme.colorScheme.outline, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                    }
                }
                items(dayItems, key = { it.id }) { e -> ExpenseRow(e) { editing = e } }
            }
        }
    }

    ReceiptHost(receipt)
    if (adding) ExpenseEditor(existing = null) { adding = false }
    editing?.let { e -> ExpenseEditor(existing = e) { editing = null } }
}
