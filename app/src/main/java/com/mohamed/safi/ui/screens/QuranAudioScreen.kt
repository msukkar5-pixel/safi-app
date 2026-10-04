package com.mohamed.safi.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.session.MediaController
import com.mohamed.safi.audio.Player
import com.mohamed.safi.faith.Moshaf
import com.mohamed.safi.faith.Quran
import com.mohamed.safi.faith.QuranAudio
import com.mohamed.safi.faith.Reciter
import com.mohamed.safi.ui.*
import kotlinx.coroutines.delay

/** القرآن المسموع: كل القرّاء وكل الروايات. */
@Composable
fun QuranAudioScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var reciters by remember { mutableStateOf<List<Reciter>?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    var q by remember { mutableStateOf("") }
    var riwaya by remember { mutableStateOf("") }
    var openR by remember { mutableStateOf<Reciter?>(null) }
    var openM by remember { mutableStateOf<Moshaf?>(null) }
    var favs by remember { mutableStateOf(QuranAudio.favorites) }
    val pendingSurah = remember { UiBus.pendingQuranAudio.value.also { UiBus.pendingQuranAudio.value = null } }
    var names by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
    var ctrl by remember { mutableStateOf<MediaController?>(null) }

    LaunchedEffect(Unit) {
        names = runCatching { Quran.surahs(ctx).associate { it.number to it.name.removePrefix("سُورَةُ ").removePrefix("سورة ") } }.getOrDefault(emptyMap())
        runCatching { QuranAudio.reciters(ctx) }.onSuccess { list ->
            reciters = list
            // coming from the Quran reader: open the last reciter on that surah
            if (pendingSurah != null) {
                val r = list.firstOrNull { it.id == QuranAudio.lastReciter } ?: list.firstOrNull { "العفاسي" in it.name } ?: list.first()
                openR = r
                openM = r.moshaf.firstOrNull { it.id == QuranAudio.lastMoshaf } ?: r.moshaf.first()
            }
        }.onFailure { err = it.message }
    }
    DisposableEffect(Unit) {
        var c: MediaController? = null
        Player.connect(ctx) { c = it; ctrl = it }
        onDispose { c?.release() }
    }

    val m = openM
    val r = openR
    if (r != null && m != null) {
        BackHandler { if (r.moshaf.size > 1) openM = null else { openM = null; openR = null } }
        SurahPlayer(r, m, names, ctrl, pendingSurah) { if (r.moshaf.size > 1) openM = null else { openM = null; openR = null } }
        return
    }
    if (r != null) {
        BackHandler { openR = null }
        ScreenScaffold(r.name, onBack = { openR = null }) { pad ->
            LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { Text("اختار الرواية", fontWeight = FontWeight.Bold) }
                items(r.moshaf) { mm ->
                    AppCard(onClick = { openM = mm }) {
                        Text(mm.riwaya, fontWeight = FontWeight.Bold)
                        Text((mm.style.ifBlank { "" }) + " • ${mm.surahs.size} سورة", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
        }
        return
    }

    val all = reciters
    val riwayat = remember(all) { all?.flatMap { it.moshaf.map { m -> m.riwaya } }?.groupingBy { it }?.eachCount()?.entries?.sortedByDescending { it.value }?.map { it.key } ?: emptyList() }
    val filtered = remember(all, q, riwaya, favs) {
        (all ?: emptyList())
            .filter { q.isBlank() || Quran.plain(it.name).contains(Quran.plain(q)) }
            .filter { riwaya.isBlank() || it.moshaf.any { m -> m.riwaya == riwaya } }
            .sortedBy { if (it.id.toString() in favs) 0 else 1 }
    }
    ScreenScaffold("القرآن المسموع", onBack = onBack) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                OutlinedTextField(q, { q = it }, placeholder = { Text("اسم القارئ") }, singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, null) }, modifier = Modifier.fillMaxWidth())
            }
            if (riwayat.isNotEmpty()) item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item { FilterChip(riwaya.isBlank(), { riwaya = "" }, label = { Text("كل الروايات") }) }
                    items(riwayat) { rw -> FilterChip(riwaya == rw, { riwaya = if (riwaya == rw) "" else rw }, label = { Text(rw) }) }
                }
            }
            err?.let { e -> item { Text(e, color = MaterialTheme.colorScheme.error) } }
            if (all == null && err == null) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if (all != null) item { Text("${filtered.size} قارئ", fontWeight = FontWeight.SemiBold) }
            items(filtered, key = { it.id }) { rc ->
                AppCard(onClick = {
                    openR = rc
                    val ms = if (riwaya.isBlank()) rc.moshaf else rc.moshaf.filter { it.riwaya == riwaya }
                    if (ms.size == 1) openM = ms.first()
                }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(40.dp)) {
                            Box(contentAlignment = Alignment.Center) { Text(rc.name.take(1), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(rc.name, fontWeight = FontWeight.Bold)
                            Text(rc.moshaf.joinToString(" • ") { it.riwaya }.let { if (it.length > 70) it.take(70) + "…" else it },
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, maxLines = 2)
                        }
                        val fav = rc.id.toString() in favs
                        IconButton(onClick = { favs = if (fav) favs - rc.id.toString() else favs + rc.id.toString(); QuranAudio.favorites = favs }) {
                            Icon(if (fav) Icons.Default.Star else Icons.Default.StarBorder, "مفضل", tint = if (fav) Gold else MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }
            item {
                Text("التسجيلات من موقع mp3quran.net وبتشتغل أونلاين.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

@Composable
private fun SurahPlayer(r: Reciter, m: Moshaf, names: Map<Int, String>, ctrl: MediaController?, startSurah: Int?, onBack: () -> Unit) {
    var cur by remember { mutableIntStateOf(-1) }
    var playing by remember { mutableStateOf(false) }
    var pos by remember { mutableLongStateOf(0L) }
    var dur by remember { mutableLongStateOf(0L) }
    var repeatOne by remember { mutableStateOf(false) }
    var autoStarted by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { QuranAudio.lastReciter = r.id; QuranAudio.lastMoshaf = m.id }
    LaunchedEffect(ctrl) {
        val c = ctrl ?: return@LaunchedEffect
        if (startSurah != null && !autoStarted && startSurah in m.surahs) { autoStarted = true; Player.loadQuran(c, r.name, m, names, startSurah) }
        while (true) {
            val id = c.currentMediaItem?.mediaId ?: ""
            if (id.startsWith("quran#${m.id}#")) {
                cur = id.substringAfterLast('#').toIntOrNull() ?: -1
                playing = c.isPlaying; pos = c.currentPosition; dur = c.duration.coerceAtLeast(0)
            } else { cur = -1; playing = false }
            delay(700)
        }
    }
    fun play(n: Int) {
        val c = ctrl ?: return
        if (cur > 0 && c.currentMediaItem?.mediaId?.startsWith("quran#${m.id}#") == true) {
            val idx = m.surahs.sorted().indexOf(n)
            if (idx >= 0) { c.seekTo(idx, 0); c.play() }
        } else Player.loadQuran(c, r.name, m, names, n)
    }
    ScreenScaffold(r.name, onBack = onBack) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            Surface(color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(m.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    Text(if (cur > 0) "سورة ${names[cur] ?: cur}" else "اختار سورة", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    if (cur > 0) {
                        Slider(
                            value = if (dur > 0) pos.toFloat() / dur else 0f,
                            onValueChange = { f -> pos = (f * dur).toLong() },
                            onValueChangeFinished = { ctrl?.seekTo(pos) },
                        )
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { ctrl?.seekToNextMediaItem() }, enabled = cur > 0) { Icon(Icons.Default.SkipNext, "التالية") }
                        FilledIconButton(onClick = {
                            val c = ctrl ?: return@FilledIconButton
                            when { cur > 0 && playing -> c.pause(); cur > 0 -> c.play(); else -> play(m.surahs.minOrNull() ?: 1) }
                        }, enabled = ctrl != null, modifier = Modifier.size(60.dp)) {
                            Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, "تشغيل", Modifier.size(34.dp))
                        }
                        IconButton(onClick = { ctrl?.seekToPreviousMediaItem() }, enabled = cur > 0) { Icon(Icons.Default.SkipPrevious, "السابقة") }
                        IconButton(onClick = {
                            repeatOne = !repeatOne
                            ctrl?.repeatMode = if (repeatOne) androidx.media3.common.Player.REPEAT_MODE_ONE else androidx.media3.common.Player.REPEAT_MODE_OFF
                        }) { Icon(if (repeatOne) Icons.Default.RepeatOne else Icons.Default.Repeat, "تكرار", tint = if (repeatOne) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline) }
                    }
                }
            }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(m.surahs.sorted(), key = { it }) { n ->
                    val active = n == cur
                    AppCard(onClick = { play(n) }, color = if (active) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("$n", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, modifier = Modifier.width(36.dp))
                            Text("سورة ${names[n] ?: n}", Modifier.weight(1f), fontWeight = if (active) FontWeight.Bold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (active && playing) Icon(Icons.Default.GraphicEq, null, tint = MaterialTheme.colorScheme.primary)
                            else Icon(Icons.Default.PlayCircleOutline, null, tint = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }
        }
    }
}
