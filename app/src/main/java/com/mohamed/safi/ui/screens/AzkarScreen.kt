package com.mohamed.safi.ui.screens

import androidx.activity.compose.BackHandler
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import com.mohamed.safi.ui.Text
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mohamed.safi.faith.Azkar
import com.mohamed.safi.faith.Quran
import com.mohamed.safi.faith.ZikrCategory
import com.mohamed.safi.ui.*
import kotlinx.coroutines.launch
import java.time.LocalTime

private fun vibrate(ctx: android.content.Context, ms: Long) {
    runCatching {
        val v = ctx.getSystemService(Vibrator::class.java) ?: return
        if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
    }
}

@Composable
private fun rememberArabicFont(): FontFamily {
    val ctx = LocalContext.current
    return remember {
        if (Quran.hasFont(ctx)) runCatching { FontFamily(Font("fonts/quran.ttf", ctx.assets)) }.getOrNull() ?: FontFamily.Default else FontFamily.Default
    }
}

@Composable
fun AzkarScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val cats = remember { Azkar.all(ctx) }
    var open by remember { mutableStateOf<ZikrCategory?>(UiBus.pendingAzkar.value?.let { k -> cats.firstOrNull { k in it.name } }) }
    LaunchedEffect(Unit) { UiBus.pendingAzkar.value = null }
    var tasbeeh by remember { mutableStateOf(false) }
    val o = open
    if (o != null) {
        BackHandler { open = null }
        ZikrReader(o) { open = null }
        return
    }
    if (tasbeeh) {
        BackHandler { tasbeeh = false }
        TasbeehView { tasbeeh = false }
        return
    }

    val scope = rememberCoroutineScope()
    var morning by remember { mutableStateOf<LocalTime?>(null) }
    var evening by remember { mutableStateOf<LocalTime?>(null) }
    LaunchedEffect(Unit) { morning = Azkar.reminderTime("morning"); evening = Azkar.reminderTime("evening") }

    ScreenScaffold("الأذكار والأدعية", onBack = onBack) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                val suggested = Azkar.current(ctx)
                suggested?.let { c ->
                    AppCard(onClick = { open = c }, color = MaterialTheme.colorScheme.primaryContainer) {
                        Text("${Azkar.icon(c.name)} وقت ${c.name}", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("${c.items.size} ذكر • دوس علشان تبدأ", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            items(cats, key = { it.name }) { c ->
                AppCard(onClick = { open = c }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(Azkar.icon(c.name), fontSize = 26.sp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(c.name, fontWeight = FontWeight.SemiBold)
                            Text("${c.items.size}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }
            item {
                AppCard(onClick = { tasbeeh = true }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("📿", fontSize = 26.sp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("السبحة", fontWeight = FontWeight.SemiBold)
                            Text("المجموع: ${Azkar.tasbeehTotal}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }
            item { SectionTitle("التذكير") }
            item {
                AppCard {
                    ReminderToggle("ذكّرني بأذكار الصباح", morning, LocalTime.of(6, 30)) { t ->
                        morning = t; scope.launch { Azkar.setReminder(ctx, "morning", t) }
                    }
                    ReminderToggle("ذكّرني بأذكار المساء", evening, LocalTime.of(16, 30)) { t ->
                        evening = t; scope.launch { Azkar.setReminder(ctx, "evening", t) }
                    }
                }
            }
            item {
                Text(
                    "المصدر: مختارات من حصن المسلم (islambook.com). الأعداد حسب الوارد في الأحاديث.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

@Composable
private fun ReminderToggle(label: String, time: LocalTime?, default: LocalTime, onChange: (LocalTime?) -> Unit) {
    val ctx = LocalContext.current
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label)
            if (time != null) Text(
                "الساعة ${time.hour % 12 + if (time.hour % 12 == 0) 12 else 0}:${"%02d".format(time.minute)} ${if (time.hour < 12) "ص" else "م"} • غيّر",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable {
                    pickTime(ctx, java.time.LocalDate.now().atTime(time).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()) { h, m -> onChange(LocalTime.of(h, m)) }
                },
            )
        }
        Switch(time != null, { on -> onChange(if (on) default else null) })
    }
}

@Composable
private fun ZikrReader(c: ZikrCategory, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val family = rememberArabicFont()
    val left = remember(c.name) { mutableStateListOf(*c.items.map { it.count }.toTypedArray()) }
    val done = left.count { it == 0 }
    var size by remember { mutableIntStateOf(22) }

    ScreenScaffold(
        c.name, onBack = onBack,
        actions = {
            IconButton(onClick = { size = (size - 2).coerceAtLeast(14) }) { Icon(Icons.Default.ZoomOut, "أصغر") }
            IconButton(onClick = { size = (size + 2).coerceAtMost(40) }) { Icon(Icons.Default.ZoomIn, "أكبر") }
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            LinearProgressIndicator(progress = { done / c.items.size.toFloat() }, modifier = Modifier.fillMaxWidth())
            Text("$done من ${c.items.size}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                itemsIndexed(c.items) { i, z ->
                    val remaining = left[i]
                    val finished = remaining == 0
                    Card(
                        onClick = {
                            if (!finished) {
                                left[i] = remaining - 1
                                vibrate(ctx, if (remaining - 1 == 0) 120 else 25)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (finished) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceContainerLow,
                        ),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(z.text, fontFamily = family, fontSize = size.sp, lineHeight = (size * 1.8).sp, textAlign = TextAlign.Justify)
                            if (z.desc.isNotBlank()) {
                                Spacer(Modifier.height(8.dp))
                                Text(z.desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                            }
                            if (z.ref.isNotBlank()) Text(z.ref, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(10.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(shape = CircleShape, color = if (finished) Positive else MaterialTheme.colorScheme.primary) {
                                    Text(
                                        if (finished) "✓" else "$remaining",
                                        color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                Text(if (z.count > 1) "التكرار: ${z.count} • دوس على الكارت تعدّ" else "مرة واحدة", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                IconButton(onClick = {
                                    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, z.text), "شارك").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                }) { Icon(Icons.Default.Share, "شارك") }
                            }
                        }
                    }
                }
                item {
                    if (done == c.items.size) Text("تقبّل الله ✓", fontWeight = FontWeight.Bold, color = Positive, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                    TextButton(onClick = { c.items.forEachIndexed { i, z -> left[i] = z.count } }) { Text("ابدأ من الأول") }
                }
            }
        }
    }
}

private val tasbeehPhrases = listOf("سبحان الله", "الحمد لله", "الله أكبر", "لا إله إلا الله", "أستغفر الله", "سبحان الله وبحمده", "لا حول ولا قوة إلا بالله", "اللهم صلِّ على محمد")

@Composable
private fun TasbeehView(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var count by remember { mutableIntStateOf(Azkar.tasbeehCount) }
    var phrase by remember { mutableStateOf(tasbeehPhrases.first()) }
    var target by remember { mutableIntStateOf(33) }
    ScreenScaffold("السبحة", onBack = onBack) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            ChipsRow(tasbeehPhrases, phrase, { it }) { phrase = it; count = 0; Azkar.tasbeehCount = 0 }
            ChipsRow(listOf(33, 100, 1000), target, { "$it" }) { target = it }
            Spacer(Modifier.height(16.dp))
            Text(phrase, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            Surface(
                onClick = {
                    count += 1; Azkar.tasbeehCount = count; Azkar.tasbeehTotal = Azkar.tasbeehTotal + 1
                    vibrate(ctx, if (count % target == 0) 200 else 20)
                },
                shape = CircleShape, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(240.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$count", color = MaterialTheme.colorScheme.onPrimary, fontSize = 64.sp, fontWeight = FontWeight.Bold)
                        Text("دورة ${count / target} • ${count % target}/$target", color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f))
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = { count = 0; Azkar.tasbeehCount = 0 }) { Text("صفّر") }
            Text("المجموع الكلي: ${Azkar.tasbeehTotal}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        }
    }
}
