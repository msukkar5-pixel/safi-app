package com.mohamed.safi.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.mohamed.safi.faith.Hifz
import com.mohamed.safi.faith.Quran
import com.mohamed.safi.faith.Surah
import com.mohamed.safi.ui.*
import androidx.compose.material3.Text as RawText

/** Memorize the Quran: listen and repeat, test yourself with hidden words, and review on a schedule. */
@Composable
fun HifzScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    @Suppress("UNUSED_VARIABLE") val v = Hifz.version.intValue
    var surahs by remember { mutableStateOf<List<Surah>>(emptyList()) }
    LaunchedEffect(Unit) { surahs = runCatching { Quran.surahs(ctx) }.getOrDefault(emptyList()) }
    val (ls, lf, lt) = Hifz.last
    var sNo by remember { mutableIntStateOf(ls) }
    var from by remember { mutableIntStateOf(lf) }
    var to by remember { mutableIntStateOf(lt) }
    var pickSurah by remember { mutableStateOf(false) }
    var hideLevel by remember { mutableIntStateOf(0) }
    var revealed by remember { mutableStateOf(setOf<Int>()) }
    var reviewing by remember { mutableStateOf(false) }
    val surah = surahs.firstOrNull { it.number == sNo }
    val count = surah?.ayahs?.size ?: 1
    if (from > count) from = 1
    if (to > count || to < from) to = (from + 4).coerceAtMost(count)

    // ---- player: each verse repeated N times
    val player = remember { ExoPlayer.Builder(ctx).build() }
    var playing by remember { mutableStateOf(false) }
    var nowAyah by remember { mutableIntStateOf(0) }
    DisposableEffect(Unit) {
        val l = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onMediaItemTransition(item: MediaItem?, reason: Int) { nowAyah = item?.mediaId?.substringAfter(":")?.toIntOrNull() ?: 0 }
        }
        player.addListener(l)
        onDispose { player.removeListener(l); player.release() }
    }
    fun play() {
        com.mohamed.safi.audio.NowPlaying.controller?.pause()
        val items = (from..to).flatMap { a -> List(Hifz.repeat) { MediaItem.Builder().setUri(Hifz.audio(Hifz.reciter, sNo, a)).setMediaId("$sNo:$a").build() } }
        player.setMediaItems(items); player.repeatMode = Player.REPEAT_MODE_ALL; player.prepare(); player.play()
        Hifz.last = Triple(sNo, from, to)
    }

    if (reviewing) { HifzReview(surahs) { reviewing = false }; return }

    ScreenScaffold("حفظ القرآن", onBack = onBack, actions = { ReadingSettingsButton() }) { pad ->
        ReadingTheme {
            LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                @Suppress("UNUSED_VARIABLE") val live = Hifz.version.intValue
                item {
                    GoldCard {
                        Row {
                            StatBlock("حفظت", "${Hifz.total} " + tr("آية"), Modifier.weight(1f))
                            StatBlock("مراجعة النهارده", "${Hifz.due().size}", Modifier.weight(1f))
                        }
                        if (Hifz.due().isNotEmpty()) Button(onClick = { player.pause(); reviewing = true }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Replay, null); Text("ابدأ المراجعة") }
                    }
                }
                item {
                    AppCard {
                        Text("احفظ جديد", fontWeight = FontWeight.Bold)
                        OutlinedButton(onClick = { pickSurah = true }, modifier = Modifier.fillMaxWidth()) {
                            RawText(surah?.name ?: "…", fontFamily = Amiri, fontSize = 18.sp)
                            Spacer(Modifier.weight(1f)); Icon(Icons.Default.ArrowDropDown, null)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("من آية", style = MaterialTheme.typography.bodySmall)
                            Stepper(from, 1, count) { from = it; if (to < it) to = it }
                            Text("لـ", style = MaterialTheme.typography.bodySmall)
                            Stepper(to, from, count) { to = it }
                        }
                        Text("القارئ", style = MaterialTheme.typography.bodySmall)
                        @OptIn(ExperimentalLayoutApi::class)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            var r by remember { mutableStateOf(Hifz.reciter) }
                            Hifz.reciters.forEach { (id, n) -> FilterChip(r == id, { r = id; Hifz.reciter = id }, label = { Text(n) }) }
                        }
                        Text("تكرار كل آية", style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            var rp by remember { mutableIntStateOf(Hifz.repeat) }
                            listOf(1, 3, 5, 10, 20).forEach { n -> FilterChip(rp == n, { rp = n; Hifz.repeat = n }, label = { RawText("$n") }) }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { if (playing) player.pause() else play() }, modifier = Modifier.weight(1f)) {
                                Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, null); Text(if (playing) "إيقاف مؤقت" else "اسمع وكرر")
                            }
                            OutlinedButton(onClick = { player.stop(); nowAyah = 0 }) { Icon(Icons.Default.Stop, null) }
                        }
                    }
                }
                item {
                    Text("اختبر نفسك", fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(0 to "اقرا", 1 to "نص الكلمات", 2 to "أول كلمة", 3 to "من الذاكرة").forEach { (l, n) ->
                            FilterChip(hideLevel == l, { hideLevel = l; revealed = emptySet() }, label = { Text(n, fontSize = 12.sp) })
                        }
                    }
                }
                surah?.let { s ->
                    items(s.ayahs.filter { it.n in from..to }, key = { "a${s.number}:${it.n}" }) { a ->
                        val show = hideLevel == 0 || a.n in revealed
                        val rs = rememberReadStyle()
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (nowAyah == a.n) Gold.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier.fillMaxWidth().clickable { revealed = if (a.n in revealed) revealed - a.n else revealed + a.n },
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                RawText(
                                    (if (show) a.text else Hifz.hide(a.text, hideLevel)) + " ﴿${a.n}﴾",
                                    fontFamily = rs.family, fontSize = rs.size(22f), lineHeight = rs.lineH(40f),
                                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                                )
                                if (Hifz.isMemorized(s.number, a.n)) Text("✓ محفوظة", color = Positive, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    item {
                        if (hideLevel > 0) Text("اضغط على الآية تشوفها وتخبّيها", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        Button(onClick = {
                            Hifz.markMemorized(s.number, from, to)
                            // a child in kid mode earns stars for memorizing
                            if (com.mohamed.safi.kids.KidMode.on) com.mohamed.safi.kids.Kids.kids().firstOrNull()?.let { com.mohamed.safi.kids.Kids.addStars(it.id, (to - from + 1).coerceIn(1, 10)) }
                            toast(ctx, "ما شاء الله! هتراجعهم بكرة 🌟")
                            // move on to the next verses of the same length
                            if (to < count) { val size = to - from; val nf = to + 1; from = nf; to = (nf + size).coerceAtMost(count) }
                        }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Positive)) { Icon(Icons.Default.Check, null); Text("حفظتهم ✓") }
                    }
                }
                item {
                    Text("التسجيلات آية بآية من everyayah.com وبتحتاج إنترنت.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }

    if (pickSurah) AlertDialog(
        onDismissRequest = { pickSurah = false },
        title = { Text("اختار السورة") },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(surahs, key = { it.number }) { s ->
                    Row(Modifier.fillMaxWidth().clickable { sNo = s.number; from = 1; to = minOf(5, s.ayahs.size); pickSurah = false }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        RawText("${s.number}", modifier = Modifier.width(36.dp), color = MaterialTheme.colorScheme.outline)
                        RawText(s.name, fontFamily = Amiri, fontSize = 18.sp, modifier = Modifier.weight(1f))
                        val m = Hifz.memorizedIn(s.number)
                        if (m > 0) RawText("✓ $m/${s.ayahs.size}", color = Positive, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { pickSurah = false }) { Text("إلغاء") } },
    )
}

@Composable
private fun Stepper(value: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
        IconButton(onClick = { if (value > min) onChange(value - 1) }, modifier = Modifier.size(36.dp)) { Icon(Icons.Default.Remove, null) }
        RawText("$value", fontWeight = FontWeight.Bold)
        IconButton(onClick = { if (value < max) onChange(value + 1) }, modifier = Modifier.size(36.dp)) { Icon(Icons.Default.Add, null) }
    }
}

/** Spaced review: recite from memory, then reveal and say whether you remembered. */
@Composable
private fun HifzReview(surahs: List<Surah>, onDone: () -> Unit) {
    val queue = remember { Hifz.due() }
    var i by remember { mutableIntStateOf(0) }
    var show by remember { mutableStateOf(false) }
    androidx.activity.compose.BackHandler { onDone() }
    ScreenScaffold("مراجعة المحفوظ", onBack = onDone) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            val cur = queue.getOrNull(i)
            if (cur == null) {
                Text("خلصت مراجعة النهارده 🌟", fontFamily = Amiri, fontWeight = FontWeight.Bold, fontSize = 24.sp)
                Spacer(Modifier.height(12.dp)); Button(onClick = onDone) { Text("رجوع") }
                return@Column
            }
            val s = surahs.firstOrNull { it.number == cur.first }
            val a = s?.ayahs?.firstOrNull { it.n == cur.second }
            Text("${i + 1} / ${queue.size}", color = MaterialTheme.colorScheme.outline)
            RawText("${s?.name.orEmpty()} • ${cur.second}", color = Gold, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            if (a != null) {
                // the first word as a cue, then recite the rest from memory
                RawText(if (show) a.text else Hifz.hide(a.text, 2), fontFamily = Amiri, fontSize = 24.sp, lineHeight = 44.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            }
            if (!show) Button(onClick = { show = true }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("اقراها من ذاكرتك وبعدين اضغط هنا") }
            else Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { Hifz.reviewed(cur.first, cur.second, true); show = false; i++ }, modifier = Modifier.weight(1f).height(52.dp), colors = ButtonDefaults.buttonColors(containerColor = Positive)) { Text("افتكرتها ✓") }
                OutlinedButton(onClick = { Hifz.reviewed(cur.first, cur.second, false); show = false; i++ }, modifier = Modifier.weight(1f).height(52.dp)) { Text("محتاج أراجعها") }
            }
        }
    }
}
