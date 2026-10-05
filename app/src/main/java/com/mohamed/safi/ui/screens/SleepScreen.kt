package com.mohamed.safi.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import com.mohamed.safi.ui.Text
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mohamed.safi.audio.Library
import com.mohamed.safi.audio.NowPlaying
import com.mohamed.safi.audio.Player
import com.mohamed.safi.faith.*
import com.mohamed.safi.ui.*
import kotlinx.coroutines.delay

/** Calm reciters looked up by name in the mp3quran list (the list itself comes from the network). */
private val calmReciters = listOf("المنشاوي", "الحصري", "العفاسي", "عبد الباسط", "عبدالباسط", "المعيقلي", "الغامدي", "الدوسري", "السديس", "الشريم", "القطامي")

/** Surahs offered for listening before sleep (any can be chosen). */
private val sleepSurahs = listOf(67, 32, 36, 55, 56, 17, 2, 112, 113, 114)

private fun pickMoshaf(r: Reciter, need: List<Int>): Moshaf? =
    r.moshaf.filter { m -> need.all { it in m.surahs } }.let { ok -> ok.firstOrNull { "مرتل" in it.name } ?: ok.firstOrNull() }

@Composable
fun SleepScreen(onBack: () -> Unit, open: (String) -> Unit) {
    val ctx = LocalContext.current
    val hisn = remember { runCatching { Deen.hisn(ctx) }.getOrNull() }
    val sleepChapters = remember(hisn) {
        hisn?.chapters.orEmpty().filter { c ->
            val t = Quran.plain(c.title)
            c.group == 2 && listOf("النوم", "تقلب", "الفزع", "الرويا").any { it in t }
        }
    }
    val tahseen = remember {
        runCatching { Deen.guide("ruqyah", ctx).sections.firstOrNull { it.id == "tahseen" }?.blocks.orEmpty() }.getOrDefault(emptyList())
            .filter { b -> b.t == "hadith" && listOf("فراش", "ليله", "نام").any { it in b.plain } }
    }
    val ruqyahSurahs = remember { runCatching { Deen.media("ruqyah", ctx).surahs }.getOrDefault(emptyList()) }
    var reciters by remember { mutableStateOf<List<Reciter>>(emptyList()) }
    var loadError by remember { mutableStateOf(false) }
    var names by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
    LaunchedEffect(Unit) {
        names = runCatching { Quran.surahs(ctx).associate { it.number to it.name.removePrefix("سُورَةُ ").removePrefix("سورة ") } }.getOrDefault(emptyMap())
        runCatching { QuranAudio.reciters(ctx) }
            .onSuccess { all -> reciters = calmReciters.mapNotNull { k -> all.firstOrNull { k in it.name } }.distinctBy { it.id } }
            .onFailure { loadError = true }
    }
    var reciterId by remember { mutableIntStateOf(Deen.sleepReciter) }
    val reciter = reciters.firstOrNull { it.id == reciterId } ?: reciters.firstOrNull()
    var chosen by remember { mutableStateOf(listOf(67, 32)) }
    val now by NowPlaying.state.collectAsState()

    // sleep timer: refresh the minutes left
    var sleepAt by remember { mutableLongStateOf(Library.sleepAt) }
    var tick by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { tick = System.currentTimeMillis(); sleepAt = Library.sleepAt; delay(15_000) } }
    val minutesLeft = if (sleepAt > tick) ((sleepAt - tick) / 60_000 + 1).toInt() else 0
    fun setTimer(min: Int) {
        val t = if (min == 0) 0L else System.currentTimeMillis() + min * 60_000L
        Library.sleepAt = t; sleepAt = t; tick = System.currentTimeMillis()
    }

    fun play(items: List<Triple<String, String, String>>, artist: String, album: String) {
        val c = NowPlaying.controller
        if (c == null) { toast(ctx, "المشغّل لسه بيجهز، جرّب تاني"); return }
        Player.loadList(c, items, artist, album)
    }
    fun playSurahs(list: List<Int>, tag: String) {
        val r = reciter ?: run { toast(ctx, if (loadError) "محتاج إنترنت علشان التلاوة" else "لسه بيحمّل القرّاء"); return }
        val m = pickMoshaf(r, list) ?: run { toast(ctx, "القارئ ده مش عنده كل السور دي، اختار قارئ تاني"); return }
        play(list.map { n -> Triple("deen#sleep#$tag#$n", m.url(n), "سورة " + (names[n] ?: "$n")) }, r.name, "قبل النوم")
    }

    ScreenScaffold("قبل النوم", onBack = onBack) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                GoldCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🌙", fontSize = 28.sp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("نام على ذكر", fontFamily = Amiri, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = MaterialTheme.colorScheme.primary)
                            Text("أذكار النوم، أدعية مسموعة، رقية، وتلاوة هادية بمؤقت يقفل لوحده.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(if (minutesLeft > 0) "الصوت هيقف بعد $minutesLeft دقيقة" else "مؤقت النوم", fontWeight = FontWeight.SemiBold)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(minutesLeft == 0, { setTimer(0) }, label = { Text("من غير") })
                        listOf(15, 30, 45, 60, 90).forEach { m -> FilterChip(false, { setTimer(m) }, label = { Text("$m د") }) }
                    }
                    now?.let { st ->
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.GraphicEq, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            androidx.compose.material3.Text(st.title, Modifier.weight(1f), maxLines = 1)
                            IconButton(onClick = { NowPlaying.toggle() }) { Icon(if (st.playing) Icons.Default.Pause else Icons.Default.PlayArrow, "تشغيل") }
                            IconButton(onClick = { NowPlaying.stop() }) { Icon(Icons.Default.Stop, "إيقاف") }
                        }
                    }
                }
            }

            item { SectionTitle("أذكار النوم من حصن المسلم") }
            items(sleepChapters, key = { it.i }) { c ->
                AppCard(onClick = { open("hisn/${c.i}") }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Bedtime, null, tint = Gold)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            androidx.compose.material3.Text(c.title, fontFamily = Amiri, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                            Text("${c.items.size} ذكر", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }
                        if (c.audio.isNotBlank()) IconButton(onClick = {
                            play(listOf(Triple("deen#hisn#${c.i}", c.audio, c.title)), hisn?.title ?: "", hisn?.title ?: "")
                        }) { Icon(Icons.Default.PlayCircle, "استمع", tint = MaterialTheme.colorScheme.primary) }
                    }
                }
            }
            if (sleepChapters.any { it.audio.isNotBlank() }) item {
                FilledTonalButton(
                    onClick = { play(sleepChapters.filter { it.audio.isNotBlank() }.map { Triple("deen#hisn#${it.i}", it.audio, it.title) }, hisn?.title ?: "", "أذكار النوم") },
                    modifier = Modifier.fillMaxWidth(),
                ) { Icon(Icons.Default.Headphones, null); Spacer(Modifier.width(6.dp)); Text("اسمع أذكار النوم كلها") }
            }
            if (tahseen.isNotEmpty()) {
                item { SectionTitle("من السنة قبل النوم") }
                items(tahseen) { b -> GuideBlockView(b, open) }
            }

            item { SectionTitle("الرقية قبل النوم") }
            item {
                AppCard {
                    Text("الفاتحة والمعوذات وآيات الرقية، بصوت القارئ اللي تختاره تحت.", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { playSurahs(ruqyahSurahs.ifEmpty { listOf(1, 112, 113, 114) }, "ruqyah") }, enabled = reciter != null) {
                            Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(4.dp)); Text("شغّل سور الرقية")
                        }
                        OutlinedButton(onClick = { open("ruqyah") }) { Text("دليل الرقية") }
                    }
                }
            }

            item { SectionTitle("تلاوة هادية") }
            item {
                AppCard {
                    when {
                        reciters.isEmpty() && !loadError -> Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)); Text("بيحمّل القرّاء…")
                        }
                        reciters.isEmpty() -> Text("محتاج إنترنت علشان قائمة القرّاء", color = MaterialTheme.colorScheme.outline)
                        else -> {
                            Text("القارئ", style = MaterialTheme.typography.labelLarge)
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                reciters.forEach { r ->
                                    FilterChip(reciter?.id == r.id, { reciterId = r.id; Deen.sleepReciter = r.id }, label = { androidx.compose.material3.Text(r.name) })
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("السور (اختار واحدة أو أكتر بالترتيب)", style = MaterialTheme.typography.labelLarge)
                    FlowRowChips(sleepSurahs, chosen, { n -> names[n]?.let { "سورة $it" } ?: "سورة $n" }) { n ->
                        chosen = if (n in chosen) chosen - n else chosen + n
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { playSurahs(chosen, "tilawa") }, enabled = reciter != null && chosen.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text("شغّل التلاوة")
                    }
                    if (minutesLeft == 0) TextButton(onClick = { setTimer(30) }) { Text("شغّل مؤقت ٣٠ دقيقة كمان") }
                }
            }
            item {
                Text(
                    "التلاوة من mp3quran.net وتسجيلات الأذكار من طريق الإسلام، وبتشتغل أونلاين.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRowChips(all: List<Int>, chosen: List<Int>, label: (Int) -> String, onToggle: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        all.forEach { n ->
            val idx = chosen.indexOf(n)
            FilterChip(
                idx >= 0, { onToggle(n) },
                label = { Text(if (idx >= 0) "${idx + 1}. ${label(n)}" else label(n)) },
            )
        }
    }
}
