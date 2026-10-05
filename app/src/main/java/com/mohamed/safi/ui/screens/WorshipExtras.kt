package com.mohamed.safi.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import com.mohamed.safi.ui.Text
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mohamed.safi.data.zone
import com.mohamed.safi.faith.*
import com.mohamed.safi.ui.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

private fun statusColor(s: Int) = when (s) {
    PrayerStatus.JAMAAH -> Color(0xFF1B7A4E)
    PrayerStatus.ON_TIME -> Positive
    PrayerStatus.LATE -> Warn
    PrayerStatus.QADA -> Color(0xFF6D7FA8)
    PrayerStatus.MISSED -> Danger
    else -> Color.Gray.copy(alpha = 0.35f)
}

// ===================================================================== prayer tracker

@Composable
fun PrayerTrackerScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var day by remember { mutableStateOf(LocalDate.now(zone)) }
    val log by remember(day) { PrayerLog.dao().day(PrayerLog.key(day)) }.collectAsState(null)
    val ym = YearMonth.from(day)
    val month by remember(ym) { PrayerLog.dao().range(PrayerLog.key(ym.atDay(1)), PrayerLog.key(ym.atEndOfMonth())) }.collectAsState(emptyList())
    val recent by remember { PrayerLog.dao().recent(400) }.collectAsState(emptyList())
    val qada by remember { PrayerLog.dao().qada() }.collectAsState(emptyList())
    var choosing by remember { mutableStateOf<Int?>(null) }
    val stats = PrayerLog.stats(month)
    val streak = PrayerLog.streak(recent)

    ScreenScaffold("متابعة الصلوات", onBack = onBack) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                GoldCard {
                    Row {
                        StatBlock("أيام متتالية كاملة", "$streak", Modifier.weight(1f))
                        StatBlock("في وقتها (الشهر)", "${stats.onTimePct}%", Modifier.weight(1f))
                        StatBlock("جماعة (الشهر)", "${stats.jamaahPct}%", Modifier.weight(1f))
                    }
                }
            }
            item {
                // week strip
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    (-6..0).forEach { off ->
                        val d = LocalDate.now(zone).plusDays(off.toLong())
                        val l = recent.firstOrNull { it.date == PrayerLog.key(d) }
                        val sel = d == day
                        Column(
                            Modifier.clip(RoundedCornerShape(12.dp)).background(if (sel) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                .clickable { day = d }.padding(6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(dayShort(d), style = MaterialTheme.typography.labelSmall)
                            Text("${d.dayOfMonth}", fontWeight = FontWeight.Bold)
                            Row { (0..4).forEach { i -> Box(Modifier.padding(0.5.dp).size(5.dp).clip(CircleShape).background(statusColor(l?.status(i) ?: 0))) } }
                        }
                    }
                }
            }
            item { Text(if (day == LocalDate.now(zone)) "النهارده" else dateLong(day), fontFamily = Amiri, fontWeight = FontWeight.Bold, fontSize = 20.sp) }
            items(5) { i ->
                val s = log?.status(i) ?: 0
                AppCard(onClick = { choosing = i }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(14.dp).clip(CircleShape).background(statusColor(s)))
                        Spacer(Modifier.width(12.dp))
                        Text(PrayerLog.fard[i], fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
                        Text(PrayerStatus.label(s), color = if (s == 0) MaterialTheme.colorScheme.outline else statusColor(s), fontWeight = FontWeight.SemiBold)
                        if (s == 0) {
                            Spacer(Modifier.width(8.dp))
                            FilledTonalButton(onClick = { scope.launch { PrayerLog.setStatus(day, i, PrayerStatus.ON_TIME) } }, contentPadding = PaddingValues(horizontal = 10.dp)) { Text("صلّيت ✓") }
                        }
                    }
                }
            }
            item { SectionTitle("السنن الرواتب والوتر") }
            item {
                AppCard {
                    PrayerLog.sunnahItems.forEachIndexed { bit, name ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { scope.launch { PrayerLog.toggleSunnah(day, bit) } }) {
                            Checkbox(log?.hasSunnah(bit) == true, { scope.launch { PrayerLog.toggleSunnah(day, bit) } })
                            Text(name)
                        }
                    }
                }
            }
            item { SectionTitle("الشهر") }
            item {
                AppCard {
                    val first = ym.atDay(1)
                    Row { listOf("سبت", "أحد", "اثنين", "ثلاثاء", "أربعاء", "خميس", "جمعة").forEach { Text(it, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center) } }
                    val lead = (first.dayOfWeek.value + 1) % 7 // Saturday first
                    val cells = List(lead) { null } + (1..ym.lengthOfMonth()).map { ym.atDay(it) }
                    cells.chunked(7).forEach { row ->
                        Row {
                            row.forEach { d ->
                                Box(Modifier.weight(1f).padding(2.dp).height(34.dp), contentAlignment = Alignment.Center) {
                                    if (d != null) {
                                        val l = month.firstOrNull { it.date == PrayerLog.key(d) }
                                        val n = l?.onTimeCount ?: 0
                                        Box(
                                            Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp))
                                                .background(if (l == null) Color.Transparent else Positive.copy(alpha = 0.12f + n * 0.17f))
                                                .clickable { day = d },
                                            contentAlignment = Alignment.Center,
                                        ) { Text("${d.dayOfMonth}", style = MaterialTheme.typography.labelMedium, fontWeight = if (d == day) FontWeight.Bold else FontWeight.Normal) }
                                    }
                                }
                            }
                            repeat(7 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
            item { SectionTitle("القضاء") }
            item {
                AppCard {
                    Text("الصلوات اللي فاتتك وعليك تقضيها", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    PrayerLog.fard.forEachIndexed { i, name ->
                        val owed = qada.firstOrNull { it.prayer == i }?.owed ?: 0
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(name, Modifier.weight(1f))
                            IconButton(onClick = { scope.launch { PrayerLog.adjustQada(i, 1) } }) { Icon(Icons.Default.Add, "زوّد") }
                            Text("$owed", fontWeight = FontWeight.Bold, modifier = Modifier.width(32.dp), textAlign = TextAlign.Center)
                            TextButton(onClick = { scope.launch { PrayerLog.adjustQada(i, -1) } }, enabled = owed > 0) { Text("قضيت واحدة") }
                        }
                    }
                }
            }
        }
    }
    choosing?.let { i ->
        AlertDialog(
            onDismissRequest = { choosing = null },
            title = { Text("صلاة ${PrayerLog.fard[i]}") },
            text = {
                Column {
                    (PrayerStatus.choices + PrayerStatus.NONE).forEach { s ->
                        Row(Modifier.fillMaxWidth().clickable { scope.launch { PrayerLog.setStatus(day, i, s) }; choosing = null }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(14.dp).clip(CircleShape).background(statusColor(s)))
                            Spacer(Modifier.width(10.dp))
                            Text(PrayerStatus.label(s))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { choosing = null }) { Text("إلغاء") } },
        )
    }
}

private val arDays = mapOf(1 to "اثن", 2 to "ثلا", 3 to "أرب", 4 to "خمي", 5 to "جمع", 6 to "سبت", 7 to "أحد")
private fun dayShort(d: LocalDate) = arDays[d.dayOfWeek.value] ?: ""
private fun dateLong(d: LocalDate) = "${d.dayOfMonth}/${d.monthValue} • ${IslamicCalendar.label(d)}"

// ===================================================================== Islamic calendar

@Composable
fun IslamicCalendarScreen(onBack: () -> Unit) {
    val today = LocalDate.now(zone)
    var ym by remember { mutableStateOf(YearMonth.from(today)) }
    val occ = remember { IslamicCalendar.nextOccasions(8) }
    ScreenScaffold("التقويم الهجري", onBack = onBack) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                GoldCard {
                    Text(IslamicCalendar.label(today), fontFamily = Amiri, fontWeight = FontWeight.Bold, fontSize = 24.sp, color = MaterialTheme.colorScheme.primary)
                    Text("${today.dayOfMonth}/${today.monthValue}/${today.year}", color = MaterialTheme.colorScheme.outline)
                    IslamicCalendar.fastingToday(today)?.let { Text("النهارده: $it", color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.SemiBold) }
                }
            }
            item { SectionTitle("المناسبات الجاية") }
            items(occ) { (name, date) ->
                val days = ChronoUnit.DAYS.between(today, date)
                AppCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(name, fontWeight = FontWeight.Bold)
                            Text("${IslamicCalendar.label(date)} • ${date.dayOfMonth}/${date.monthValue}/${date.year}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }
                        Text(if (days == 0L) "النهارده" else "بعد $days يوم", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            item { MonthSwitcher(ym) { ym = it } }
            item {
                AppCard {
                    Row { listOf("سبت", "أحد", "اثنين", "ثلاثاء", "أربعاء", "خميس", "جمعة").forEach { Text(it, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center) } }
                    val first = ym.atDay(1)
                    val lead = (first.dayOfWeek.value + 1) % 7
                    val cells = List(lead) { null } + (1..ym.lengthOfMonth()).map { ym.atDay(it) }
                    cells.chunked(7).forEach { row ->
                        Row {
                            row.forEach { d ->
                                Box(Modifier.weight(1f).padding(1.dp).height(48.dp), contentAlignment = Alignment.Center) {
                                    if (d != null) {
                                        val h = IslamicCalendar.hijri(d)
                                        val fast = IslamicCalendar.fastingToday(d) != null
                                        Column(
                                            Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp))
                                                .background(if (d == today) MaterialTheme.colorScheme.primaryContainer else if (fast) Gold.copy(alpha = 0.12f) else Color.Transparent),
                                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                                        ) {
                                            Text("${h?.d ?: ""}", fontWeight = FontWeight.Bold, fontFamily = Amiri)
                                            Text("${d.dayOfMonth}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                        }
                                    }
                                }
                            }
                            repeat(7 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                    Text("الرقم الكبير هجري والصغير ميلادي. الأيام الملوّنة أيام صيام مستحب.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }
            }
            item {
                Text("التواريخ حسب تقويم أم القرى، وممكن تختلف يوم عن رؤية الهلال.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

// ===================================================================== Asma ul-Husna

@Composable
fun AsmaHusnaScreen(onBack: () -> Unit) {
    var favs by remember { mutableStateOf(AsmaHusna.favorites) }
    var open by remember { mutableStateOf<Int?>(null) }
    val (todayIdx, todayName) = remember { AsmaHusna.ofDay() }
    ScreenScaffold("أسماء الله الحسنى", onBack = onBack) { pad ->
        LazyVerticalGrid(GridCells.Fixed(3), Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                GoldCard(onClick = { open = todayIdx }) {
                    Text("اسم اليوم", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
                    Text(todayName.first, fontFamily = Amiri, fontWeight = FontWeight.Bold, fontSize = 30.sp, color = MaterialTheme.colorScheme.primary)
                    Text(todayName.second)
                }
            }
            itemsIndexed(AsmaHusna.names) { i, (name, _) ->
                val fav = name in favs
                Card(onClick = { open = i }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Column(Modifier.fillMaxWidth().padding(vertical = 14.dp, horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${i + 1}", style = MaterialTheme.typography.labelSmall, color = if (fav) Gold else MaterialTheme.colorScheme.outline)
                        Text(name, fontFamily = Amiri, fontWeight = FontWeight.Bold, fontSize = 18.sp, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                Text("قال ﷺ: «إن لله تسعة وتسعين اسماً، مائة إلا واحداً، من أحصاها دخل الجنة» (متفق عليه). والسرد المشهور للأسماء من رواية الترمذي.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
        }
    }
    open?.let { i ->
        val (name, meaning) = AsmaHusna.names[i]
        val fav = name in favs
        AlertDialog(
            onDismissRequest = { open = null },
            title = { Text(name, fontFamily = Amiri, fontSize = 30.sp, color = MaterialTheme.colorScheme.primary) },
            text = { Text(meaning, style = MaterialTheme.typography.bodyLarge) },
            confirmButton = { TextButton(onClick = { open = null }) { Text("تمام") } },
            dismissButton = {
                TextButton(onClick = { favs = if (fav) favs - name else favs + name; AsmaHusna.favorites = favs }) {
                    Icon(if (fav) Icons.Default.Star else Icons.Default.StarBorder, null, tint = Gold); Text(if (fav) " في المفضلة" else " للمفضلة")
                }
            },
        )
    }
}

// ===================================================================== home cards

@Composable
fun PrayerTrackerHomeCard(open: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val today = LocalDate.now(zone)
    val log by remember { PrayerLog.dao().day(PrayerLog.key(today)) }.collectAsState(null)
    GoldCard(onClick = { open("prayertracker") }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("صلواتك النهارده", fontFamily = Amiri, fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.weight(1f))
            Text("${log?.onTimeCount ?: 0}/5", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            PrayerLog.fard.forEachIndexed { i, name ->
                val s = log?.status(i) ?: 0
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable {
                    scope.launch { PrayerLog.setStatus(today, i, if (s == 0) PrayerStatus.ON_TIME else PrayerStatus.NONE) }
                }) {
                    Box(Modifier.size(40.dp).clip(CircleShape).background(if (s == 0) MaterialTheme.colorScheme.surfaceVariant else statusColor(s)), contentAlignment = Alignment.Center) {
                        if (s != 0) Icon(Icons.Default.Check, null, tint = Color.White)
                    }
                    Text(name, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

@Composable
fun OccasionHomeCard(open: (String) -> Unit) {
    val today = LocalDate.now(zone)
    val next = remember { IslamicCalendar.nextOccasions(1).firstOrNull() }
    val fast = remember { IslamicCalendar.fastingToday(today) }
    val (_, name) = remember { AsmaHusna.ofDay() }
    GoldCard(onClick = { open("islamiccalendar") }) {
        if (next != null) {
            val days = ChronoUnit.DAYS.between(today, next.second)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Event, null, tint = Gold)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(next.first, fontWeight = FontWeight.Bold)
                    Text(IslamicCalendar.label(next.second), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
                Text(if (days == 0L) "النهارده" else "بعد $days يوم", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
        }
        if (fast != null) Text("🌙 النهارده: $fast", color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
        HorizontalDivider(Modifier.padding(vertical = 8.dp), color = Gold.copy(alpha = 0.3f))
        Row(Modifier.fillMaxWidth().clickable { open("asmahusna") }, verticalAlignment = Alignment.CenterVertically) {
            Text("اسم اليوم: ", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
            Text(name.first, fontFamily = Amiri, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
            Text(" — ${name.second}", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        }
    }
}
