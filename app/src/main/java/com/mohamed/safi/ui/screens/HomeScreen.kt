package com.mohamed.safi.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mohamed.safi.SafiApp
import com.mohamed.safi.data.*
import com.mohamed.safi.notify.Brief
import com.mohamed.safi.ui.*
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

@Composable
fun HomeScreen(open: (String) -> Unit) {
    val dao = SafiApp.db.dao()
    val prefs = SafiApp.prefs
    val ym = YearMonth.now(zone)
    val (from, to) = remember(ym) { monthRange(ym) }
    val (pFrom, _) = remember(ym) { monthRange(ym.minusMonths(1)) }
    val month by dao.expensesBetween(from, to).collectAsState(emptyList())
    val prevSameDay = remember(ym) {
        val d = LocalDate.now(zone)
        pFrom to ym.minusMonths(1).atDay(minOf(d.dayOfMonth, ym.minusMonths(1).lengthOfMonth())).atTime(23, 59).millis()
    }
    val prev by dao.expensesBetween(prevSameDay.first, prevSameDay.second).collectAsState(emptyList())
    val transfers by dao.transfersBetween(from, to).collectAsState(emptyList())
    val recent by dao.recentExpenses(5).collectAsState(emptyList())
    val bills by dao.bills().collectAsState(emptyList())
    val debts by dao.debts().collectAsState(emptyList())
    val reminders by dao.reminders().collectAsState(emptyList())
    var obligations by remember { mutableStateOf<List<Obligation>>(emptyList()) }
    var soon by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(bills, debts) {
        obligations = Obligations.forMonth(ym)
        soon = Brief.todayLines()
    }
    val perms by rememberPermState()

    var editing by remember { mutableStateOf<Expense?>(null) }
    var adding by remember { mutableStateOf(false) }
    val receipt = rememberReceiptController()
    val voice = rememberVoiceInput { text ->
        UiBus.pendingVoice.value = text
        open("assistant")
    }

    val spent = month.filter { !it.isIncome }.sumOf { it.amountAed }
    val income = month.filter { it.isIncome }.sumOf { it.amountAed }
    val prevSpent = prev.filter { !it.isIncome }.sumOf { it.amountAed }
    val sentEgp = transfers.sumOf { it.amountEgp }
    val sentAed = transfers.sumOf { it.amountAed + it.feesAed }
    val hour = LocalTime.now().hour
    val greet = when (hour) {
        in 4..11 -> "صباح الخير"
        in 12..16 -> "نهارك سعيد"
        else -> "مساء الخير"
    }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("$greet يا ${prefs.userName}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(dateStr(System.currentTimeMillis()), color = MaterialTheme.colorScheme.outline)
                }
                AssistChip(
                    onClick = { open("settings") },
                    label = { Text("1 د.إ = ${prefs.egpPerAed} ج") },
                    leadingIcon = { Icon(Icons.Default.SwapHoriz, null, Modifier.size(18.dp)) },
                )
            }
        }

        if (!perms.essentialsOk) {
            item {
                AppCard(onClick = { open("settings") }, color = MaterialTheme.colorScheme.tertiaryContainer) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, null, tint = Warn)
                        Spacer(Modifier.width(10.dp))
                        Text("فيه صلاحيات ناقصة، التنبيهات أو رسايل البنك ممكن ماتشتغلش. دوس هنا.", Modifier.weight(1f))
                    }
                }
            }
        }

        // Month summary
        item {
            AppCard(onClick = { open("reports") }, color = MaterialTheme.colorScheme.primary) {
                val onP = MaterialTheme.colorScheme.onPrimary
                Text("صرفت في ${monthName(ym)}", color = onP.copy(alpha = 0.85f))
                Text(money(spent), color = onP, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                if (prevSpent > 0) {
                    val diff = spent - prevSpent
                    Text(
                        (if (diff >= 0) "▲ أكتر " else "▼ أقل ") + "بـ ${money(kotlin.math.abs(diff))} من نفس الوقت الشهر اللي فات",
                        color = onP.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row {
                    Column(Modifier.weight(1f)) {
                        Text("تحويلات مصر", color = onP.copy(alpha = 0.8f), style = MaterialTheme.typography.labelMedium)
                        Text(money(sentEgp, "EGP"), color = onP, fontWeight = FontWeight.SemiBold)
                        Text("≈ ${money(sentAed)}", color = onP.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall)
                    }
                    Column(Modifier.weight(1f)) {
                        Text("دخل الشهر", color = onP.copy(alpha = 0.8f), style = MaterialTheme.typography.labelMedium)
                        Text(money(income), color = onP, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        item { CarpoolCard(open) }
        item { PrayerCard(open) }

        // Quick actions
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickAction(Icons.Default.Mic, "سجّل بالصوت", Brand, Modifier.weight(1f)) { voice() }
                QuickAction(Icons.Default.CameraAlt, "صوّر فاتورة", Color2, Modifier.weight(1f)) { receipt.open() }
                QuickAction(Icons.Default.Payments, "مصروف كاش", Positive, Modifier.weight(1f)) { adding = true }
                QuickAction(Icons.Default.SwapHoriz, "تحويل مصر", Warn, Modifier.weight(1f)) { open("transfers") }
            }
        }

        // Obligations this month
        item {
            val total = obligations.sumOf { it.amountAed }
            val overdue = obligations.filter { it.overdue }
            AppCard(onClick = { open("bills") }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("مطلوب منك الشهر ده", fontWeight = FontWeight.Bold)
                        Text(
                            if (obligations.isEmpty()) "ضيف فواتيرك والتزاماتك علشان أفكرك بيها" else "${obligations.size} التزام",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline,
                        )
                    }
                    Text(money(total), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                if (overdue.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Pill("${overdue.size} متأخر", Danger)
                }
                obligations.filter { it.due >= System.currentTimeMillis() - 86_400_000L || it.overdue }.take(5).forEach { o ->
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(o.title, Modifier.weight(1f), maxLines = 1)
                        Text(dueText(o.due), style = MaterialTheme.typography.bodySmall, color = if (o.overdue) Danger else MaterialTheme.colorScheme.outline)
                        Spacer(Modifier.width(10.dp))
                        Text(money(o.amount, o.currency), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        if (soon.isNotEmpty()) {
            item {
                AppCard(color = MaterialTheme.colorScheme.tertiaryContainer) {
                    Text("محتاج انتباهك", fontWeight = FontWeight.Bold)
                    soon.forEach { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp)) }
                }
            }
        }

        val upcoming = reminders.filter { !it.done && it.time >= System.currentTimeMillis() }.take(3)
        if (upcoming.isNotEmpty()) {
            item { SectionTitle("الجاي") { TextButton(onClick = { open("schedule") }) { Text("الكل") } } }
            items(upcoming, key = { "r" + it.id }) { r ->
                AppCard(onClick = { open("schedule") }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CatBadge(r.title, 36, if (r.kind == "appointment") Icons.Default.Event else Icons.Default.Alarm, if (r.kind == "appointment") Color2 else Brand)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(r.title, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            Text(dateTimeStr(r.time) + if (r.location.isNotBlank()) " • ${r.location}" else "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }
        }

        item { SectionTitle("آخر المصاريف") { TextButton(onClick = { open("expenses") }) { Text("الكل") } } }
        if (recent.isEmpty()) {
            item { EmptyState(Icons.Default.Receipt, "لسه مفيش مصاريف. شارك رسالة البنك لصافي من تطبيق الرسايل.") }
        }
        items(recent, key = { "e" + it.id }) { e -> ExpenseRow(e) { editing = e } }
        item { Spacer(Modifier.height(24.dp)) }
    }

    ReceiptHost(receipt)
    if (adding) ExpenseEditor(existing = null) { adding = false }
    editing?.let { e -> ExpenseEditor(existing = e) { editing = null } }
}

@Composable
private fun QuickAction(icon: ImageVector, label: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Card(
        onClick = onClick, modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(shape = CircleShape, color = color.copy(alpha = 0.14f), modifier = Modifier.size(44.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = color) }
            }
            Spacer(Modifier.height(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 2)
        }
    }
}
