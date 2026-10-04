package com.mohamed.safi.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mohamed.safi.faith.*
import com.mohamed.safi.ui.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun BidayaScreen(onBack: () -> Unit) = BookScreen("bidaya", onBack)

/** Generic in-app book: volumes → table of contents → reader, with search and bookmarks. */
@Composable
fun BookScreen(bookId: String, onBack: () -> Unit, initialQuery: String = "") {
    val meta = remember(bookId) { Books.meta(bookId) }
    if (!Books.isReady(bookId)) { BookDownload(bookId, meta, onBack, initialQuery); return }
    val book = remember(bookId) { Books.data(bookId) }
    var vols by remember { mutableStateOf<List<BVolume>?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    var openVol by rememberSaveable { mutableIntStateOf(0) }
    var reading by remember { mutableStateOf<BSection?>(null) }
    var q by rememberSaveable { mutableStateOf(initialQuery) }
    var hits by remember { mutableStateOf<List<BookData.Hit>?>(null) }
    var searching by remember { mutableStateOf(false) }
    LaunchedEffect(bookId) {
        runCatching { book.volumes() }.onSuccess { vols = it }.onFailure { err = it.message ?: "الكتاب مش راضي يفتح" }
    }
    LaunchedEffect(q, vols) {
        if (vols == null || q.trim().length < 2) { hits = null; return@LaunchedEffect }
        delay(500); searching = true
        hits = runCatching { book.search(q) }.getOrDefault(emptyList())
        searching = false
    }

    val all = vols
    val r = reading
    if (r != null && all != null) { BookReader(book, all, r) { reading = null }; return }
    val ov = all?.firstOrNull { it.vol == openVol }
    if (ov != null) {
        BackHandler { openVol = 0 }
        BookToc(ov, all.size > 1, onOpen = { reading = it }) { openVol = 0 }
        return
    }
    // single-volume books open straight on their table of contents
    if (all != null && all.size == 1 && hits == null && q.isBlank()) {
        BookToc(all[0], false, onOpen = { reading = it }, title = meta?.title ?: "", header = {
            BookHeader(meta, book, all) { reading = it }
            OutlinedTextField(q, { q = it }, placeholder = { Text("دوّر في الكتاب: اسم، حدث، كلمة…") }, singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, null) }, modifier = Modifier.fillMaxWidth())
        }, onBack = onBack)
        return
    }

    ScreenScaffold(meta?.title ?: "كتاب", onBack = onBack) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { if (all != null) BookHeader(meta, book, all) { reading = it } }
            err?.let { e -> item { Text(e, color = MaterialTheme.colorScheme.error) } }
            if (all == null && err == null) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if (all != null) {
                item {
                    OutlinedTextField(q, { q = it }, placeholder = { Text("دوّر في الكتاب كله: اسم، حدث، سنة…") }, singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        trailingIcon = { if (q.isNotEmpty()) IconButton(onClick = { q = "" }) { Icon(Icons.Default.Close, "مسح") } },
                        modifier = Modifier.fillMaxWidth())
                }
                if (searching) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                val h = hits
                if (h != null) {
                    item { Text(if (h.isEmpty() && !searching) "مفيش نتايج" else "${h.size}${if (h.size >= 80) "+" else ""} نتيجة", fontWeight = FontWeight.SemiBold) }
                    items(h) { hit ->
                        AppCard(onClick = { reading = hit.section }) {
                            Text((if (all.size > 1) "المجلد ${hit.section.vol} • " else "") + hit.section.title, fontWeight = FontWeight.SemiBold, maxLines = 2)
                            Text(hit.snippet, style = MaterialTheme.typography.bodySmall, maxLines = 3)
                        }
                    }
                } else {
                    items(all, key = { it.vol }) { v ->
                        AppCard(onClick = { openVol = v.vol }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("${v.vol}", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(40.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("المجلد ${v.vol}", fontWeight = FontWeight.SemiBold)
                                    Text(if (v.first == v.last) v.first else "${v.first} ← ${v.last}", style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BookHeader(meta: BookMeta?, book: BookData, all: List<BVolume>, onContinue: (BSection) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (meta != null) {
            Text(meta.author, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            if (meta.desc.isNotBlank()) Text(meta.desc, style = MaterialTheme.typography.bodyMedium)
            if (meta.source.isNotBlank()) Text("المصدر: ${meta.source}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        }
        if (book.lastVol > 0) {
            val s = all.firstOrNull { it.vol == book.lastVol }?.sections?.getOrNull(book.lastIdx)
            if (s != null) AppCard(onClick = { onContinue(s) }, color = MaterialTheme.colorScheme.primaryContainer) {
                Text("كمّل القراءة", fontWeight = FontWeight.Bold)
                Text((if (all.size > 1) "المجلد ${s.vol} • " else "") + s.title, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        val marks = book.marks
        if (marks.isNotEmpty()) {
            Text("العلامات", fontWeight = FontWeight.SemiBold)
            marks.mapNotNull { k ->
                val (v, i) = k.split(":").let { (it.getOrNull(0)?.toIntOrNull() ?: 0) to (it.getOrNull(1)?.toIntOrNull() ?: -1) }
                all.firstOrNull { it.vol == v }?.sections?.getOrNull(i)
            }.take(10).forEach { s ->
                Row(Modifier.fillMaxWidth().clickable { onContinue(s) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Bookmark, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(s.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun BookToc(
    v: BVolume, multi: Boolean, onOpen: (BSection) -> Unit, title: String = "", header: (@Composable () -> Unit)? = null, onBack: () -> Unit,
) {
    ScreenScaffold(if (multi) "المجلد ${v.vol}" else title.ifBlank { "الفهرس" }, onBack = onBack) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp)) {
            if (header != null) item { header(); Spacer(Modifier.height(12.dp)); Text("الفهرس", fontWeight = FontWeight.Bold) }
            items(v.sections, key = { it.idx }) { s ->
                Row(
                    Modifier.fillMaxWidth().clickable { onOpen(s) }.padding(vertical = 10.dp).padding(start = ((s.level - 1).coerceIn(0, 4) * 14).dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        s.title, Modifier.weight(1f),
                        fontWeight = if (s.level == 1) FontWeight.Bold else FontWeight.Normal,
                        color = if (s.level == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                    if (s.page > 0) Text("ص ${s.page}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            }
        }
    }
}

@Composable
private fun BookReader(book: BookData, all: List<BVolume>, start: BSection, onBack: () -> Unit) {
    BackHandler { onBack() }
    var sec by remember { mutableStateOf(start) }
    var body by remember { mutableStateOf<List<String>?>(null) }
    var size by remember { mutableIntStateOf(Books.fontSize) }
    var marks by remember { mutableStateOf(book.marks) }
    val state = rememberLazyListState()
    LaunchedEffect(sec) {
        body = null
        val t = runCatching { book.text(sec.vol) }.getOrDefault(emptyList())
        body = (t.getOrNull(sec.idx) ?: "").split('\n').filter { it.isNotBlank() }
        book.lastVol = sec.vol; book.lastIdx = sec.idx
        state.scrollToItem(0)
    }
    fun neighbour(d: Int): BSection? {
        val v = all.firstOrNull { it.vol == sec.vol } ?: return null
        val i = sec.idx + d
        return when {
            i in v.sections.indices -> v.sections[i]
            d > 0 -> all.firstOrNull { it.vol > sec.vol }?.sections?.firstOrNull()
            else -> all.lastOrNull { it.vol < sec.vol }?.sections?.lastOrNull()
        }
    }
    val key = "${sec.vol}:${sec.idx}"

    ScreenScaffold(
        (if (all.size > 1) "المجلد ${sec.vol}" else "") + (if (sec.page > 0) (if (all.size > 1) " • " else "") + "ص ${sec.page}" else ""),
        onBack = onBack,
        actions = {
            IconButton(onClick = { marks = if (key in marks) marks - key else marks + key; book.marks = marks }) {
                Icon(if (key in marks) Icons.Default.Bookmark else Icons.Default.BookmarkBorder, "علامة")
            }
            IconButton(onClick = { size = (size - 2).coerceAtLeast(14); Books.fontSize = size }) { Icon(Icons.Default.ZoomOut, "أصغر") }
            IconButton(onClick = { size = (size + 2).coerceAtMost(34); Books.fontSize = size }) { Icon(Icons.Default.ZoomIn, "أكبر") }
        },
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), state = state, contentPadding = PaddingValues(16.dp)) {
            item {
                Text(sec.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(12.dp))
            }
            val b = body
            if (b == null) item { CircularProgressIndicator() }
            else if (b.isEmpty()) item { Text("(عنوان بدون نص، النص في الفصل اللي بعده)", color = MaterialTheme.colorScheme.outline) }
            else items(b.size) { i ->
                val p = b[i]
                val obit = p.startsWith("◆")
                Text(
                    p, fontSize = size.sp, lineHeight = (size * 1.75).sp, textAlign = TextAlign.Justify,
                    fontWeight = if (obit) FontWeight.Bold else FontWeight.Normal,
                    color = if (obit) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                )
            }
            item {
                Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    val prev = neighbour(-1)
                    val next = neighbour(1)
                    if (prev != null) OutlinedButton(onClick = { sec = prev }) { Text("السابق") } else Spacer(Modifier.width(1.dp))
                    if (next != null) Button(onClick = { sec = next }) { Text("التالي") }
                }
            }
        }
    }
}

@Composable
private fun BookDownload(bookId: String, meta: BookMeta?, onBack: () -> Unit, initialQuery: String = "") {
    val scope = rememberCoroutineScope()
    var progress by remember { mutableStateOf<Float?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    var ready by remember { mutableStateOf(false) }
    if (ready) { BookScreen(bookId, onBack, initialQuery); return }
    fun start() {
        err = null; progress = 0f
        scope.launch {
            runCatching { Books.download(bookId) { progress = it } }
                .onSuccess { ready = true }
                .onFailure { err = it.message ?: "التحميل فشل، اتأكد من النت"; progress = null }
        }
    }
    LaunchedEffect(Unit) { start() }
    ScreenScaffold(meta?.title ?: "كتاب", onBack = onBack) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.MenuBook, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
            Text(meta?.title ?: "", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            meta?.author?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            Text("الكتاب بيتحمّل مرة واحدة ويفضل على تليفونك تقراه من غير نت" + (meta?.size?.takeIf { it > 0 }?.let { " (${Books.sizeText(it)})" } ?: ""),
                textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
            progress?.let { p -> LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth()); Text("${(p * 100).toInt()}%") }
            err?.let { e ->
                Text(e, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                Button(onClick = { start() }) { Text("حاول تاني") }
            }
        }
    }
}

/** List of books for one or more categories, with download state. */
@Composable
fun BookList(cats: List<String>, openBook: (String) -> Unit) {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { if (Books.refreshCatalog(ctx)) tick++ }
    val books = remember(tick) { Books.catalog(ctx).filter { it.cat in cats } }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        books.forEach { b -> BookCard(b) { openBook(b.id) } }
        if (books.isEmpty()) Text("الكتب لسه بتتجهز، افتح الصفحة دي تاني بعد شوية.", color = MaterialTheme.colorScheme.outline)
    }
}

@Composable
fun BookCard(b: BookMeta, onClick: () -> Unit) {
    val ready = remember(b.id) { Books.isReady(b.id) }
    AppCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.MenuBook, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(b.title, fontWeight = FontWeight.Bold)
                Text(b.author, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                if (b.desc.isNotBlank()) Text(b.desc, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    (if (b.volumes > 1) "${b.volumes} مجلد • " else "") + if (ready) "متحمّل ✓" else "اقرأ (تحميل ${Books.sizeText(b.size).ifBlank { "صغير" }})",
                    style = MaterialTheme.typography.labelSmall, color = if (ready) Positive else MaterialTheme.colorScheme.outline,
                )
            }
            Icon(Icons.Default.ChevronLeft, null)
        }
    }
}

/** The whole library, by category. */
@Composable
fun LibraryScreen(onBack: () -> Unit, openBook: (String) -> Unit) {
    ScreenScaffold("المكتبة", onBack = onBack) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Books.categories.forEach { (k, name) ->
                item { SectionTitle(name) }
                item { BookList(listOf(k), openBook) }
            }
        }
    }
}
