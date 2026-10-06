package com.mohamed.safi.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import com.mohamed.safi.ui.Text
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.session.MediaController
import coil.compose.AsyncImage
import com.mohamed.safi.audio.AudioBook
import com.mohamed.safi.audio.Library
import com.mohamed.safi.audio.Player
import com.mohamed.safi.audio.Track
import com.mohamed.safi.ui.*
import kotlinx.coroutines.delay

@Composable
fun AudiobooksScreen(onBack: () -> Unit) {
    var book by remember { mutableStateOf<AudioBook?>(null) }
    val b = book
    if (b != null) {
        BackHandler { book = null }
        BookPlayer(b) { book = null }
        return
    }

    var tab by remember { mutableIntStateOf(if (Library.shelf.isEmpty()) 1 else 0) }
    var q by remember { mutableStateOf("") }
    var lang by remember { mutableStateOf("ara") }
    var topic by remember { mutableStateOf("islamic") }
    var page by remember { mutableIntStateOf(1) }
    var results by remember { mutableStateOf<List<AudioBook>>(emptyList()) }
    var total by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    var shelf by remember { mutableStateOf(Library.shelf) }

    LaunchedEffect(tab, q, lang, page, topic) {
        if (tab == 0) return@LaunchedEffect
        delay(400); loading = true; err = null
        runCatching { Library.search("archive", q, lang, page, topic) }
            .onSuccess { (l, n) -> results = if (page == 1) l else results + l; total = n }
            .onFailure { err = it.message ?: "مفيش نت؟" }
        loading = false
    }

    ScreenScaffold("الكتب المسموعة", onBack = onBack) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            TabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
                Tab(tab == 0, { tab = 0; shelf = Library.shelf }, text = { Text("مكتبتي") })
                Tab(tab == 1, { tab = 1; page = 1 }, text = { Text("تصفّح الكتب") })
            }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (tab == 0) {
                    if (shelf.isEmpty()) item { EmptyState(Icons.Default.Headphones, "لسه مفيش كتب في مكتبتك. افتح «تصفّح الكتب» واختار كتاب.") }
                    items(shelf, key = { "s" + it.id }) { bk ->
                        val p = Library.progress(bk.id)
                        BookRow(bk, if (p != null) "وقفت عند الفصل ${p.first + 1}" else null) { book = bk }
                    }
                    item {
                        Text("كتب إسلامية وكتب نافعة في التاريخ وتطوير الذات والأسرة والصحة والعلوم وقصص الأطفال، بلغات كتير من أرشيف الإنترنت. أي كتاب فيه إلحاد أو طعن في الدين أو كتب أديان وفرق أخرى أو سحر أو روايات غرامية بيتشال تلقائياً. القرآن المسموع ليه قسم لوحده.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    }
                } else {
                    item {
                        OutlinedTextField(q, { q = it; page = 1 }, placeholder = { Text("اسم كتاب أو مؤلف أو موضوع") }, singleLine = true,
                            leadingIcon = { Icon(Icons.Default.Search, null) }, modifier = Modifier.fillMaxWidth())
                    }
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(Library.topics.entries.toList()) { (k, name) ->
                                FilterChip(topic == k, { topic = k; page = 1 }, label = { Text(name) })
                            }
                        }
                    }
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(Library.languages.entries.toList()) { (code, name) ->
                                FilterChip(lang == code, { lang = code; page = 1 }, label = { Text(name) })
                            }
                        }
                    }
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(if (topic != "islamic") emptyList() else Library.suggestions[if (lang == "ara") "ara" else ""] ?: emptyList()) { s ->
                                AssistChip(onClick = { q = if (q == s) "" else s; page = 1 }, label = { Text(s) },
                                    colors = if (q == s) AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.secondaryContainer) else AssistChipDefaults.assistChipColors())
                            }
                        }
                    }
                    if (loading && page == 1) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                    err?.let { e -> item { Text(e, color = MaterialTheme.colorScheme.error) } }
                    if (!loading && results.isEmpty() && err == null) item { EmptyState(Icons.Default.SearchOff, "مفيش نتايج") }
                    if (results.isNotEmpty()) item { Text("$total كتاب", fontWeight = FontWeight.SemiBold) }
                    items(results) { bk -> BookRow(bk, null) { book = bk } }
                    if (results.size < total) item {
                        OutlinedButton(onClick = { page++ }, enabled = !loading, modifier = Modifier.fillMaxWidth()) {
                            Text(if (loading) "بيحمّل…" else "كمان")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BookRow(b: AudioBook, sub: String?, onClick: () -> Unit) {
    AppCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(b.cover, null, contentScale = ContentScale.Crop, modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(b.title, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (b.author.isNotBlank()) Text(b.author, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (sub != null) Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

private fun fmt(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    val h = s / 3600; val m = (s % 3600) / 60; val ss = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, ss) else "%d:%02d".format(m, ss)
}

@Composable
private fun BookPlayer(b: AudioBook, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var tracks by remember { mutableStateOf<List<Track>?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    var ctrl by remember { mutableStateOf<MediaController?>(null) }
    var onShelf by remember { mutableStateOf(Library.shelf.any { it.id == b.id }) }
    var cur by remember { mutableIntStateOf(-1) }
    var pos by remember { mutableLongStateOf(0L) }
    var dur by remember { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf(false) }
    var speed by remember { mutableFloatStateOf(Library.speed) }
    var sleepAt by remember { mutableLongStateOf(Library.sleepAt.let { if (it > System.currentTimeMillis()) it else 0L }) }
    var mine by remember { mutableStateOf(false) } // controller currently holds this book

    LaunchedEffect(b.id) {
        runCatching { Library.tracks(b.id) }.onSuccess { tracks = it; if (it.isEmpty()) err = "الكتاب ده مفيهوش ملفات صوت" }
            .onFailure { err = it.message }
    }
    DisposableEffect(Unit) {
        val f = Player.connect(ctx) { ctrl = it }
        onDispose {
            ctrl?.let { c ->
                val id = c.currentMediaItem?.mediaId ?: ""
                if (id.startsWith(b.id + "#")) Library.saveProgress(b.id, id.substringAfterLast('#').toIntOrNull() ?: 0, c.currentPosition.coerceAtLeast(0L))
            }
            ctrl = null
            MediaController.releaseFuture(f)
        }
    }
    LaunchedEffect(ctrl) {
        val c = ctrl ?: return@LaunchedEffect
        while (true) {
            val id = c.currentMediaItem?.mediaId ?: ""
            mine = id.startsWith(b.id + "#")
            if (mine) {
                cur = id.substringAfter('#').toIntOrNull() ?: 0
                pos = c.currentPosition; dur = c.duration.coerceAtLeast(0)
                playing = c.isPlaying
            } else { playing = false }
            // progress saving and the sleep timer run in PlaybackService; just mirror the timer here
            sleepAt = Library.sleepAt.let { if (it > System.currentTimeMillis()) it else 0L }
            delay(1000)
        }
    }
    fun start(i: Int, ms: Long) {
        val c = ctrl ?: return
        val t = tracks ?: return
        if (!onShelf) { Library.addToShelf(b); onShelf = true }
        if (mine) { c.seekTo(i, ms); c.play() } else Player.load(c, b, t, i, ms)
    }

    ScreenScaffold(b.title, onBack = onBack, actions = {
        IconButton(onClick = {
            if (onShelf) Library.removeFromShelf(b.id) else Library.addToShelf(b)
            onShelf = !onShelf
        }) { Icon(if (onShelf) Icons.Default.Bookmark else Icons.Default.BookmarkBorder, "مكتبتي") }
    }) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(b.cover, null, contentScale = ContentScale.Crop, modifier = Modifier.size(96.dp).clip(RoundedCornerShape(12.dp)))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(b.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        if (b.author.isNotBlank()) Text(b.author, color = MaterialTheme.colorScheme.primary)
                        tracks?.let { t -> Text("${t.size} فصل • ${fmt((t.sumOf { it.seconds } * 1000).toLong())}", style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
            item {
                AppCard(color = MaterialTheme.colorScheme.secondaryContainer) {
                    val t = tracks
                    if (mine && cur >= 0 && t != null) {
                        Text(t.getOrNull(cur)?.title ?: "", fontWeight = FontWeight.SemiBold, maxLines = 2)
                        Slider(
                            value = if (dur > 0) pos.toFloat() / dur else 0f,
                            onValueChange = { f -> pos = (f * dur).toLong() },
                            onValueChangeFinished = { ctrl?.seekTo(pos) },
                        )
                        Row { Text(fmt(pos), style = MaterialTheme.typography.labelSmall); Spacer(Modifier.weight(1f)); Text(fmt(dur), style = MaterialTheme.typography.labelSmall) }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { ctrl?.let { if (mine) it.seekToPreviousMediaItem() } }) { Icon(Icons.Default.SkipNext, "السابق") }
                        IconButton(onClick = { ctrl?.let { if (mine) it.seekTo((it.currentPosition - 10000).coerceAtLeast(0)) } }) { Icon(Icons.Default.Replay10, "رجوع ١٠ ثواني") }
                        FilledIconButton(onClick = {
                            val c = ctrl ?: return@FilledIconButton
                            when {
                                mine && playing -> c.pause()
                                mine -> c.play()
                                else -> { val p = Library.progress(b.id); start(p?.first ?: 0, p?.second ?: 0L) }
                            }
                        }, enabled = ctrl != null && tracks?.isNotEmpty() == true, modifier = Modifier.size(64.dp)) {
                            Icon(if (mine && playing) Icons.Default.Pause else Icons.Default.PlayArrow, "تشغيل", Modifier.size(36.dp))
                        }
                        IconButton(onClick = { ctrl?.let { if (mine) it.seekTo(it.currentPosition + 30000) } }) { Icon(Icons.Default.Forward30, "قدام ٣٠ ثانية") }
                        IconButton(onClick = { ctrl?.let { if (mine) it.seekToNextMediaItem() } }) { Icon(Icons.Default.SkipPrevious, "التالي") }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        TextButton(onClick = {
                            val opts = listOf(0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
                            speed = opts[(opts.indexOf(speed) + 1).mod(opts.size)].takeIf { opts.contains(speed) } ?: 1f
                            Library.speed = speed; ctrl?.setPlaybackSpeed(speed)
                        }) { Icon(Icons.Default.Speed, null); Spacer(Modifier.width(4.dp)); Text("×$speed") }
                        TextButton(onClick = {
                            val now = System.currentTimeMillis()
                            val left = if (sleepAt > now) (sleepAt - now) / 60000 + 1 else 0
                            val next = when { left <= 0 -> 15L; left <= 15 -> 30L; left <= 30 -> 60L; else -> 0L }
                            sleepAt = if (next == 0L) 0 else now + next * 60000
                            Library.sleepAt = sleepAt
                            toast(ctx, if (next == 0L) "مؤقت النوم اتلغى" else "هيقف بعد $next دقيقة")
                        }) {
                            Icon(Icons.Default.Bedtime, null); Spacer(Modifier.width(4.dp))
                            Text(if (sleepAt > 0) "مؤقت النوم ✓" else "مؤقت النوم")
                        }
                    }
                    if (!mine) Library.progress(b.id)?.let { p ->
                        Text("هيكمّل من الفصل ${p.first + 1} عند ${fmt(p.second)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            err?.let { e -> item { Text(e, color = MaterialTheme.colorScheme.error) } }
            val t = tracks
            if (t == null && err == null) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if (t != null) {
                item { SectionTitle("الفصول") }
                itemsIndexed(t) { i, tr ->
                    val active = mine && i == cur
                    AppCard(onClick = { start(i, 0) }, color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${i + 1}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(36.dp))
                            Text(tr.title, Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                            if (tr.seconds > 0) Text(fmt((tr.seconds * 1000).toLong()), style = MaterialTheme.typography.labelSmall)
                            if (active && playing) Icon(Icons.Default.GraphicEq, null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}
