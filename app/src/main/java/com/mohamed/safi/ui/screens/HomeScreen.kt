package com.mohamed.safi.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import com.mohamed.safi.ui.Text
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
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { HomeHeader(greet + (prefs.userName.takeIf { it.isNotBlank() }?.let { " يا $it" } ?: ""), open) }

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
        item { PrayerTrackerHomeCard(open) }
        item { NextUpCard(reminders, open) }
        item { OccasionHomeCard(open) }

        item { CarpoolCard(open) }

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
    val pct = ((w.nextPage - 1).toFloat() / com.mohamed.safi.faith.Wird.TOTAL_PAGES).coerceIn(0f, 1f)
    GoldCard(onClick = { open("wird") }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.AutoStories, null, tint = Gold)
            Spacer(Modifier.width(8.dp))
            Text("وردك اليومي", fontFamily = Amiri, fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.weight(1f))
            if (w.streak > 0) Text("${w.streak} يوم متتالي", color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.height(6.dp))
        if (done) {
            Text("خلّصت وردك النهارده، تقبّل الله", fontFamily = Amiri, fontSize = 22.sp, color = MaterialTheme.colorScheme.primary)
            Text("بكرة من صفحة ${w.nextPage}", color = MaterialTheme.colorScheme.outline, style = MaterialTheme.typography.bodySmall)
        } else {
            Text("من صفحة ${r.first} إلى ${r.last}", fontFamily = Amiri, fontWeight = FontWeight.Bold, fontSize = 26.sp, color = MaterialTheme.colorScheme.primary)
            Text("${r.last - r.first + 1} صفحات من المصحف", color = MaterialTheme.colorScheme.outline, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(10.dp))
        LinearProgressIndicator(progress = { pct }, modifier = Modifier.fillMaxWidth().height(6.dp), color = Gold, trackColor = MaterialTheme.colorScheme.outlineVariant, drawStopIndicator = {})
        Text(
            "الختمة ${(pct * 100).toInt()}%" + if (w.khatmas > 0) " • ختمت ${w.khatmas} مرة" else "",
            color = MaterialTheme.colorScheme.outline, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp),
        )
        if (!done) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { open("wird") }) { Icon(Icons.Default.MenuBook, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("اقرأ") }
                OutlinedButton(onClick = { w.markDone(); done = true }) { Text("قريته") }
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
    GoldCard(
        onClick = {
            if (z != null) {
                cnt++
                if (cnt >= z.count) { idx++; cnt = 0 }
                save()
            }
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(com.mohamed.safi.faith.Azkar.icon(cat.name), fontSize = 22.sp)
            Spacer(Modifier.width(8.dp))
            Text(cat.name, fontFamily = Amiri, fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.weight(1f))
            Text("${minOf(idx + 1, cat.items.size)}/${cat.items.size}", style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.height(8.dp))
        if (finished || z == null) {
            Text("✓ خلّصت ${cat.name}، تقبّل الله", fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
            TextButton(onClick = { idx = 0; cnt = 0; save() }) { Text("من الأول") }
        } else {
            Text(z.text, fontFamily = Amiri, fontSize = 20.sp, lineHeight = 38.sp, maxLines = 10, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
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

/** Calm header: greeting, Hijri + Gregorian date, next prayer with countdown, over a subtle geometric pattern. */
@Composable
private fun HomeHeader(greeting: String, open: (String) -> Unit) {
    var now by remember { mutableStateOf(java.time.LocalDateTime.now(zone)) }
    var next by remember { mutableStateOf(com.mohamed.safi.faith.Prayer.nextPrayer()) }
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(20_000); now = java.time.LocalDateTime.now(zone); next = com.mohamed.safi.faith.Prayer.nextPrayer() } }
    val hijri = remember(now.toLocalDate()) { hijriText(now.toLocalDate()) }
    val left = java.time.Duration.between(now, next.second)
    Box(
        Modifier
            .layout { m, c ->
                val extra = 32.dp.roundToPx()
                val w = c.maxWidth + extra
                val p = m.measure(c.copy(minWidth = w, maxWidth = w))
                layout(c.maxWidth, p.height) { p.place(-extra / 2, 0) }
            }
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
            .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(BrandDeep, Brand)))
            .clickable { open("prayer") },
    ) {
        IslamicPattern(Modifier.matchParentSize(), GoldSoft.copy(alpha = 0.10f))
        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 18.dp)) {
            Text(greeting, color = Color.White, fontFamily = Amiri, fontWeight = FontWeight.Bold, fontSize = 26.sp)
            Text(hijri, color = GoldSoft, fontFamily = Amiri, fontSize = 17.sp)
            Text(dateStr(System.currentTimeMillis()), color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text("الصلاة الجاية", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelMedium)
                    Text(next.first, color = Color.White, fontFamily = Amiri, fontWeight = FontWeight.Bold, fontSize = 30.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(t12(next.second), color = Gold, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                    Text(leftText(left), color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

/** "باقي ساعتين و٥ دقايق" style countdown without "0 ساعة". */
fun leftText(d: java.time.Duration): String {
    val total = d.toMinutes().coerceAtLeast(0)
    val h = total / 60; val m = total % 60
    val hs = when (h) { 0L -> ""; 1L -> "ساعة"; 2L -> "ساعتين"; in 3..10 -> "$h ساعات"; else -> "$h ساعة" }
    val ms = when (m) { 0L -> ""; 1L -> "دقيقة"; 2L -> "دقيقتين"; in 3..10 -> "$m دقايق"; else -> "$m دقيقة" }
    return when {
        hs.isEmpty() && ms.isEmpty() -> "دلوقتي"
        hs.isEmpty() -> "باقي $ms"
        ms.isEmpty() -> "باقي $hs"
        else -> "باقي $hs و$ms"
    }
}

fun hijriText(d: java.time.LocalDate): String = runCatching {
    val h = java.time.chrono.HijrahDate.from(d)
    val months = listOf("محرم", "صفر", "ربيع الأول", "ربيع الآخر", "جمادى الأولى", "جمادى الآخرة", "رجب", "شعبان", "رمضان", "شوال", "ذو القعدة", "ذو الحجة")
    val day = h.get(java.time.temporal.ChronoField.DAY_OF_MONTH)
    val month = h.get(java.time.temporal.ChronoField.MONTH_OF_YEAR)
    val year = h.get(java.time.temporal.ChronoField.YEAR)
    "$day ${months[month - 1]} $year هـ"
}.getOrDefault("")

/** Subtle eight-pointed-star lattice, drawn with lines. */
@Composable
fun IslamicPattern(modifier: Modifier, color: Color, cell: androidx.compose.ui.unit.Dp = 44.dp) {
    androidx.compose.foundation.Canvas(modifier) {
        val c = cell.toPx()
        val stroke = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.2f)
        var y = -c / 2
        while (y < size.height + c) {
            var x = -c / 2
            while (x < size.width + c) {
                val r = c * 0.36f
                val p1 = androidx.compose.ui.graphics.Path().apply {
                    moveTo(x - r, y - r); lineTo(x + r, y - r); lineTo(x + r, y + r); lineTo(x - r, y + r); close()
                }
                val d = r * 1.414f
                val p2 = androidx.compose.ui.graphics.Path().apply {
                    moveTo(x, y - d); lineTo(x + d, y); lineTo(x, y + d); lineTo(x - d, y); close()
                }
                drawPath(p1, color, style = stroke)
                drawPath(p2, color, style = stroke)
                x += c
            }
            y += c
        }
    }
}
