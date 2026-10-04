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
                    Text(greet + (prefs.userName.takeIf { it.isNotBlank() }?.let { " يا $it" } ?: ""), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
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

        item { WirdHomeCard(open) }
        item { NextUpCard(reminders, open) }

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

        if (soon.isNotEmpty()) {
            item {
                AppCard(color = MaterialTheme.colorScheme.tertiaryContainer) {
                    Text("محتاج انتباهك", fontWeight = FontWeight.Bold)
                    soon.forEach { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp)) }
                }
            }
        }

        item { AzkarHomeCard(open) }
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

@Composable
private fun WirdHomeCard(open: (String) -> Unit) {
    var done by remember { mutableStateOf(com.mohamed.safi.faith.Wird.doneToday) }
    val w = com.mohamed.safi.faith.Wird
    val r = w.todayRange()
    AppCard(onClick = { open("wird") }, color = MaterialTheme.colorScheme.primary) {
        val onP = MaterialTheme.colorScheme.onPrimary
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.AutoStories, null, tint = onP)
            Spacer(Modifier.width(8.dp))
            Text("وردك اليومي", color = onP, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
            if (w.streak > 0) Text("🔥 ${w.streak} يوم", color = onP.copy(alpha = 0.9f), style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.height(8.dp))
        if (done) {
            Text("✓ خلّصت وردك النهارده، ربنا يتقبل", color = onP, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Text("بكرة من صفحة ${w.nextPage}", color = onP.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall)
        } else {
            Text("من صفحة ${r.first} لـ ${r.last}", color = onP, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text("${r.last - r.first + 1} صفحات من المصحف", color = onP.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(10.dp))
        LinearProgressIndicator(
            progress = { ((w.nextPage - 1).toFloat() / com.mohamed.safi.faith.Wird.TOTAL_PAGES).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(), color = onP, trackColor = onP.copy(alpha = 0.25f),
        )
        Text(
            "الختمة: ${((w.nextPage - 1) * 100 / com.mohamed.safi.faith.Wird.TOTAL_PAGES)}%" + if (w.khatmas > 0) " • ختمت ${w.khatmas} مرة" else "",
            color = onP.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall,
        )
        if (!done) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { open("wird") }, colors = ButtonDefaults.buttonColors(containerColor = onP, contentColor = MaterialTheme.colorScheme.primary)) { Text("اقرأ") }
                OutlinedButton(onClick = { w.markDone(); done = true }, colors = ButtonDefaults.outlinedButtonColors(contentColor = onP)) { Text("قريته ✓") }
            }
        }
    }
}

@Composable
private fun NextUpCard(reminders: List<Reminder>, open: (String) -> Unit) {
    val now = System.currentTimeMillis()
    val next = reminders.filter { !it.done && it.time >= now }.sortedBy { it.time }
    val first = next.firstOrNull()
    AppCard(onClick = { open("schedule") }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CatBadge(first?.title ?: "", 40, if (first?.kind == "appointment") Icons.Default.Event else Icons.Default.Alarm, if (first?.kind == "appointment") Color2 else Brand)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(if (first?.kind == "appointment") "موعدك الجاي" else "التنبيه اللي عليه الدور", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                if (first == null) Text("مفيش مواعيد أو تنبيهات جاية", fontWeight = FontWeight.SemiBold)
                else {
                    Text(first.title, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 2)
                    Text(dateTimeStr(first.time) + " • " + untilText(first.time - now) + if (first.location.isNotBlank()) " • ${first.location}" else "",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        next.drop(1).take(2).forEach { r ->
            Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text(r.title, Modifier.weight(1f), maxLines = 1, style = MaterialTheme.typography.bodyMedium)
                Text(dateTimeStr(r.time), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

private fun untilText(ms: Long): String {
    val m = ms / 60_000
    return when {
        m < 1 -> "دلوقتي"
        m < 60 -> "بعد $m دقيقة"
        m < 24 * 60 -> "بعد ${m / 60} ساعة" + if (m % 60 > 0) " و${m % 60} دقيقة" else ""
        else -> "بعد ${m / (24 * 60)} يوم"
    }
}

@Composable
private fun AzkarHomeCard(open: (String) -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val cat = remember { com.mohamed.safi.faith.Azkar.current(ctx) } ?: return
    val key = "home_" + java.time.LocalDate.now(zone) + "_" + cat.name
    val sp = remember { ctx.getSharedPreferences("safi_azkar", android.content.Context.MODE_PRIVATE) }
    var idx by remember { mutableIntStateOf(sp.getInt(key + "_i", 0)) }
    var cnt by remember { mutableIntStateOf(sp.getInt(key + "_c", 0)) }
    fun save() { sp.edit().putInt(key + "_i", idx).putInt(key + "_c", cnt).apply() }
    val finished = idx >= cat.items.size
    val z = cat.items.getOrNull(idx)
    AppCard(
        onClick = {
            if (z != null) {
                cnt++
                if (cnt >= z.count) { idx++; cnt = 0 }
                save()
            }
        },
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(com.mohamed.safi.faith.Azkar.icon(cat.name), fontSize = 22.sp)
            Spacer(Modifier.width(8.dp))
            Text(cat.name, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
            Text("${minOf(idx + 1, cat.items.size)}/${cat.items.size}", style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.height(8.dp))
        if (finished || z == null) {
            Text("✓ خلّصت ${cat.name}، تقبّل الله", fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
            TextButton(onClick = { idx = 0; cnt = 0; save() }) { Text("من الأول") }
        } else {
            Text(z.text, fontSize = 18.sp, lineHeight = 32.sp, maxLines = 10, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            if (z.desc.isNotBlank()) Text(z.desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(top = 4.dp))
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
                    Text("${z.count - cnt}", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold, fontSize = 20.sp,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp))
                }
                Spacer(Modifier.width(10.dp))
                Text("دوس على الكارت للعدّ", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                TextButton(onClick = { idx++; cnt = 0; save() }) { Text("التالي") }
            }
        }
        TextButton(onClick = { UiBus.pendingAzkar.value = cat.name; open("azkar") }) { Text("كل الأذكار") }
    }
}
