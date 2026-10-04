package com.mohamed.safi.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mohamed.safi.faith.BSection
import com.mohamed.safi.faith.BVolume
import com.mohamed.safi.faith.Bidaya
import com.mohamed.safi.ui.*
import kotlinx.coroutines.delay

@Composable
fun BidayaScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var vols by remember { mutableStateOf<List<BVolume>?>(null) }
    var openVol by remember { mutableStateOf<BVolume?>(null) }
    var reading by remember { mutableStateOf<BSection?>(null) }
    var q by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<Bidaya.Hit>?>(null) }
    var searching by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { if (Bidaya.available(ctx)) vols = runCatching { Bidaya.volumes(ctx) }.getOrNull() }
    LaunchedEffect(q) {
        if (q.trim().length < 3) { hits = null; return@LaunchedEffect }
        delay(500); searching = true
        hits = runCatching { Bidaya.search(q) }.getOrDefault(emptyList())
        searching = false
    }

    val r = reading
    val all = vols
    if (r != null && all != null) { BidayaReader(all, r) { reading = null }; return }
    val ov = openVol
    if (ov != null) { BidayaToc(ov, onOpen = { reading = it }) { openVol = null }; return }

    ScreenScaffold("البداية والنهاية", onBack = onBack) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Text("للحافظ ابن كثير الدمشقي (ت ٧٧٤هـ)", fontWeight = FontWeight.SemiBold)
                Text(Bidaya.CREDIT, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
            if (all == null) {
                item { EmptyState(Icons.Default.MenuBook, if (Bidaya.available(ctx)) "بيفتح…" else "الكتاب مش موجود في النسخة دي من التطبيق") }
            } else {
                if (Bidaya.lastVol > 0) item {
                    val v = all.firstOrNull { it.vol == Bidaya.lastVol }
                    val s = v?.sections?.getOrNull(Bidaya.lastIdx)
                    if (s != null) AppCard(onClick = { reading = s }, color = MaterialTheme.colorScheme.primaryContainer) {
                        Text("كمّل القراءة", fontWeight = FontWeight.Bold)
                        Text("المجلد ${s.vol} • ${s.title}", style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                item {
                    OutlinedTextField(q, { q = it }, placeholder = { Text("دوّر في الكتاب كله: اسم، حدث، سنة…") }, singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Search, null) }, modifier = Modifier.fillMaxWidth())
                }
                if (searching) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                val h = hits
                if (h != null) {
                    item { Text("${h.size}${if (h.size >= 80) "+" else ""} نتيجة", fontWeight = FontWeight.SemiBold) }
                    items(h) { hit ->
                        AppCard(onClick = { reading = hit.section }) {
                            Text("المجلد ${hit.section.vol} • ${hit.section.title}", fontWeight = FontWeight.SemiBold, maxLines = 2)
                            Text(hit.snippet, style = MaterialTheme.typography.bodySmall, maxLines = 3)
                        }
                    }
                } else {
                    items(all, key = { it.vol }) { v ->
                        AppCard(onClick = { openVol = v }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("${v.vol}", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(40.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("المجلد ${v.vol}", fontWeight = FontWeight.SemiBold)
                                    Text("${v.first} ← ${v.last}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, maxLines = 2, overflow = TextOverflow.Ellipsis)
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
private fun BidayaToc(v: BVolume, onOpen: (BSection) -> Unit, onBack: () -> Unit) {
    ScreenScaffold("المجلد ${v.vol}", onBack = onBack) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp)) {
            items(v.sections, key = { it.idx }) { s ->
                Row(
                    Modifier.fillMaxWidth().clickable { onOpen(s) }.padding(vertical = 8.dp).padding(start = ((s.level - 1).coerceAtMost(4) * 14).dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        s.title, Modifier.weight(1f),
                        fontWeight = if (s.level == 1) FontWeight.Bold else FontWeight.Normal,
                        color = if (s.level == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                    Text("ص ${s.page}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            }
        }
    }
}

@Composable
private fun BidayaReader(all: List<BVolume>, start: BSection, onBack: () -> Unit) {
    var sec by remember { mutableStateOf(start) }
    var body by remember { mutableStateOf<List<String>?>(null) }
    var size by remember { mutableIntStateOf(Bidaya.fontSize) }
    var marks by remember { mutableStateOf(Bidaya.marks) }
    val state = rememberLazyListState()
    LaunchedEffect(sec) {
        body = null
        val t = Bidaya.text(sec.vol)
        body = (t.getOrNull(sec.idx) ?: "").split('\n').filter { it.isNotBlank() }
        Bidaya.lastVol = sec.vol; Bidaya.lastIdx = sec.idx
        state.scrollToItem(0)
    }
    fun neighbour(d: Int): BSection? {
        val v = all.first { it.vol == sec.vol }
        val i = sec.idx + d
        return when {
            i in v.sections.indices -> v.sections[i]
            d > 0 -> all.firstOrNull { it.vol == sec.vol + 1 }?.sections?.firstOrNull()
            else -> all.firstOrNull { it.vol == sec.vol - 1 }?.sections?.lastOrNull()
        }
    }
    val key = "${sec.vol}:${sec.idx}"

    ScreenScaffold(
        "المجلد ${sec.vol} • ص ${sec.page}", onBack = onBack,
        actions = {
            IconButton(onClick = { marks = if (key in marks) marks - key else marks + key; Bidaya.marks = marks }) {
                Icon(if (key in marks) Icons.Default.Bookmark else Icons.Default.BookmarkBorder, "علامة")
            }
            IconButton(onClick = { size = (size - 2).coerceAtLeast(14); Bidaya.fontSize = size }) { Icon(Icons.Default.ZoomOut, "أصغر") }
            IconButton(onClick = { size = (size + 2).coerceAtMost(32); Bidaya.fontSize = size }) { Icon(Icons.Default.ZoomIn, "أكبر") }
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
            else items(b) { p ->
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
                    neighbour(-1)?.let { p -> OutlinedButton(onClick = { sec = p }) { Text("السابق") } } ?: Spacer(Modifier)
                    neighbour(1)?.let { n -> Button(onClick = { sec = n }) { Text("التالي") } }
                }
            }
        }
    }
}
