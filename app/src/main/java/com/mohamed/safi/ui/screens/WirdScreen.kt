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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mohamed.safi.data.*
import com.mohamed.safi.faith.Quran
import com.mohamed.safi.faith.Surah
import com.mohamed.safi.faith.Wird
import com.mohamed.safi.ui.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

@Composable
fun WirdScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var surahs by remember { mutableStateOf<List<Surah>>(emptyList()) }
    var tick by remember { mutableIntStateOf(0) }
    var reading by remember { mutableStateOf(false) }
    var reminder by remember { mutableStateOf<LocalTime?>(null) }
    var settings by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        surahs = runCatching { Quran.surahs(ctx) }.getOrDefault(emptyList())
        reminder = Wird.reminderTime()
    }
    if (reading && surahs.isNotEmpty()) {
        PageRangeReader(surahs, Wird.todayRange(), onDone = { Wird.markDone(); tick++; reading = false }, onBack = { reading = false })
        return
    }
    val refresh = tick
    val range = remember(refresh) { Wird.todayRange() }
    val covered = surahs.filter { s -> s.ayahs.any { it.page in range } }.map { it.name }

    ScreenScaffold(
        "الورد اليومي", onBack = onBack,
        actions = { IconButton(onClick = { settings = true }) { Icon(Icons.Default.Tune, "الإعدادات") } },
    ) { pad -> key(refresh) {
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                AppCard(color = if (Wird.doneToday) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.tertiaryContainer) {
                    Text(if (Wird.doneToday) "✓ خلصت ورد النهارده" else "ورد النهارده", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("الصفحات ${range.first} – ${range.last} (${range.count()} صفحات)", style = MaterialTheme.typography.bodyLarge)
                    if (covered.isNotEmpty()) Text(covered.joinToString(" • "), style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { reading = true }, enabled = surahs.isNotEmpty()) { Icon(Icons.Default.MenuBook, null); Spacer(Modifier.width(6.dp)); Text("اقرأ الورد") }
                        if (!Wird.doneToday) OutlinedButton(onClick = { Wird.markDone(); tick++ }) { Text("قريته") }
                        else TextButton(onClick = { Wird.undo(); tick++ }) { Text("تراجع") }
                    }
                }
            }
            item {
                AppCard {
                    val read = Wird.nextPage - 1
                    Text("الختمة", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(progress = { read / Wird.TOTAL_PAGES.toFloat() }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)))
                    Spacer(Modifier.height(6.dp))
                    Row {
                        StatBlock("قريت", "$read صفحة", Modifier.weight(1f))
                        StatBlock("هتختم بعد", "${Wird.daysToFinish()} يوم", Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(6.dp))
                    Row {
                        StatBlock("أيام متتالية", "${Wird.streak} 🔥", Modifier.weight(1f))
                        StatBlock("ختمات", "${Wird.khatmas}", Modifier.weight(1f))
                    }
                }
            }
            item { SectionTitle("أذكار يومية مع الورد") }
            items(Wird.extras) { x ->
                var n by remember(x.text, tick) { mutableIntStateOf(Wird.extraCount(x.text)) }
                Card(
                    onClick = { if (n < x.target) { n++; Wird.setExtraCount(x.text, n) } },
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = if (n >= x.target) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow),
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(x.text, fontWeight = FontWeight.SemiBold)
                            LinearProgressIndicator(progress = { n / x.target.toFloat() }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(if (n >= x.target) "✓" else "$n/${x.target}", fontWeight = FontWeight.Bold)
                    }
                }
            }
            item { Text("دوس على الكارت عشان تعدّ. العداد بيبدأ من الصفر كل يوم.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline) }
            item { SectionTitle("التذكير") }
            item {
                AppCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("ذكّرني بالورد كل يوم")
                            reminder?.let { t ->
                                TextButton(onClick = {
                                    pickTime(ctx, LocalDate.now().atTime(t).millis()) { h, m ->
                                        val nt = LocalTime.of(h, m); reminder = nt; scope.launch { Wird.setReminder(ctx, nt) }
                                    }
                                }, contentPadding = PaddingValues(0.dp)) { Text("الساعة ${timeStr(LocalDate.now().atTime(t).millis())} • غيّر") }
                            }
                        }
                        Switch(reminder != null, { on ->
                            val t = if (on) LocalTime.of(21, 0) else null
                            reminder = t; scope.launch { Wird.setReminder(ctx, t) }
                        })
                    }
                }
            }
        }
    } }

    if (settings) WirdSettings(onDismiss = { settings = false; tick++ })
}

@Composable
private fun WirdSettings(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var ppd by remember { mutableIntStateOf(Wird.pagesPerDay) }
    var start by remember { mutableStateOf(Wird.nextPage.toString()) }
    var extras by remember { mutableStateOf(Wird.extras) }
    var newText by remember { mutableStateOf("") }
    var newCount by remember { mutableStateOf("100") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إعدادات الورد") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("كام صفحة في اليوم؟", style = MaterialTheme.typography.labelLarge)
                ChipsRow(listOf(1, 2, 4, 5, 10, 20, 40), ppd, { if (it == 20) "جزء" else if (it == 40) "جزئين" else "$it" }) { ppd = it }
                Text("هتختم كل ${(Wird.TOTAL_PAGES + ppd - 1) / ppd} يوم", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                OutlinedButton(onClick = {
                    pickDate(ctx, System.currentTimeMillis() + 30L * 86_400_000L) { ppd = Wird.pagesForDate(it.toLocalDate()) }
                }) { Text("عايز أختم في تاريخ معيّن") }
                NumberField("هبدأ من صفحة", start) { start = it.filter { c -> c.isDigit() } }
                HorizontalDivider()
                Text("أذكار يومية", style = MaterialTheme.typography.labelLarge)
                extras.forEach { x ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${x.text} × ${x.target}", Modifier.weight(1f))
                        IconButton(onClick = { extras = extras - x }) { Icon(Icons.Default.Close, "شيل") }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(newText, { newText = it }, label = { Text("ذكر") }, singleLine = true, modifier = Modifier.weight(2f))
                    NumberField("العدد", newCount, Modifier.weight(1f)) { newCount = it.filter { c -> c.isDigit() } }
                    IconButton(onClick = {
                        if (newText.isNotBlank()) { extras = extras + Wird.Extra(newText.trim(), newCount.toIntOrNull()?.coerceAtLeast(1) ?: 100); newText = "" }
                    }) { Icon(Icons.Default.Add, "ضيف") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                Wird.pagesPerDay = ppd
                start.toIntOrNull()?.let { Wird.nextPage = it }
                Wird.extras = extras
                onDismiss()
            }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}

@Composable
private fun PageRangeReader(surahs: List<Surah>, range: IntRange, onDone: () -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val family = remember {
        if (Quran.hasFont(ctx)) runCatching { FontFamily(Font("fonts/quran.ttf", ctx.assets)) }.getOrNull() ?: FontFamily.Default else FontFamily.Default
    }
    val size = Quran.fontSize
    val accent = MaterialTheme.colorScheme.primary
    val parts = remember(range) {
        surahs.mapNotNull { s -> s.ayahs.filter { it.page in range }.takeIf { it.isNotEmpty() }?.let { s to it } }
    }
    ScreenScaffold("الورد: ص ${range.first}–${range.last}", onBack = onBack) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp)) {
            parts.forEach { (s, ayahs) ->
                item {
                    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Text(s.name, fontFamily = family, fontSize = (size + 2).sp, color = accent, textAlign = TextAlign.Center, modifier = Modifier.padding(8.dp))
                    }
                    if (ayahs.first().n == 1 && s.number != 1 && s.number != 9) {
                        Text(Quran.BASMALA, fontFamily = family, fontSize = size.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))
                    }
                }
                items(ayahs, key = { "${s.number}:${it.n}" }) { a ->
                    Text(
                        buildAnnotatedString {
                            append(a.text); append(" ")
                            withStyle(SpanStyle(color = accent)) { append("﴿${Quran.toArabicDigits(a.n)}﴾") }
                        },
                        fontFamily = family, fontSize = size.sp, lineHeight = (size * 1.9).sp, textAlign = TextAlign.Justify,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    )
                }
            }
            item {
                Button(onClick = onDone, modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
                    Icon(Icons.Default.Check, null); Spacer(Modifier.width(6.dp)); Text("خلصت ورد النهارده")
                }
            }
        }
    }
}
