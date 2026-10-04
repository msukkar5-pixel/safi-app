package com.mohamed.safi.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import com.mohamed.safi.faith.Quran
import com.mohamed.safi.faith.Surah
import com.mohamed.safi.ui.*
import kotlinx.coroutines.delay

@Composable
fun QuranScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var list by remember { mutableStateOf<List<Surah>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var open by remember { mutableStateOf<Pair<Int, Int>?>(null) }   // surah, ayah
    var q by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }

    LaunchedEffect(attempt) {
        error = null
        try { list = Quran.surahs(ctx) } catch (e: Exception) { error = "المصحف محتاج إنترنت أول مرة بس علشان يتحمّل" }
    }

    val surahs = list
    val o = open
    if (surahs != null && o != null) {
        QuranReader(surahs, o.first, o.second, onBack = { open = null })
        return
    }

    ScreenScaffold(
        "القرآن الكريم", onBack = onBack,
        actions = { IconButton(onClick = { searching = !searching; q = "" }) { Icon(Icons.Default.Search, "بحث") } },
    ) { pad ->
        if (surahs == null) {
            Column(Modifier.fillMaxSize().padding(pad).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                if (error == null) { CircularProgressIndicator(); Spacer(Modifier.height(10.dp)); Text("بيفتح المصحف…") }
                else { Text(error!!); Spacer(Modifier.height(8.dp)); Button(onClick = { attempt++ }) { Text("حاول تاني") } }
            }
            return@ScreenScaffold
        }
        val hits = remember(q) { if (searching) Quran.search(surahs, q) else emptyList() }
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (searching) {
                item {
                    OutlinedTextField(q, { q = it }, placeholder = { Text("دوّر على كلمة أو آية أو اسم سورة") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                if (q.length >= 2) item { Text("${hits.size}${if (hits.size >= 100) "+" else ""} نتيجة", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline) }
                items(hits) { h ->
                    AppCard(onClick = { open = h.surah.number to h.ayah.n }) {
                        Text("${h.surah.name} • آية ${h.ayah.n}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Text(h.ayah.text, style = MaterialTheme.typography.bodyLarge, maxLines = 3)
                    }
                }
            }
            if (!searching || q.length < 2) {
                if (Quran.lastSurah > 0) item {
                    val s = surahs[Quran.lastSurah - 1]
                    AppCard(onClick = { open = s.number to Quran.lastAyah }, color = MaterialTheme.colorScheme.primaryContainer) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Bookmark, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text("كمّل القراءة", fontWeight = FontWeight.Bold)
                                Text("${s.name} • آية ${Quran.lastAyah}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                val marks = Quran.bookmarks.mapNotNull { b -> b.split(":").let { p -> p.getOrNull(0)?.toIntOrNull()?.let { it to (p.getOrNull(1)?.toIntOrNull() ?: 1) } } }
                if (marks.isNotEmpty()) item {
                    Text("العلامات", fontWeight = FontWeight.Bold)
                    marks.sortedWith(compareBy({ it.first }, { it.second })).forEach { (s, a) ->
                        Text(
                            "🔖 ${surahs[s - 1].name} • آية $a",
                            Modifier.fillMaxWidth().clickable { open = s to a }.padding(vertical = 6.dp),
                        )
                    }
                }
                val filtered = if (searching && q.isNotBlank()) surahs.filter { Quran.plain(it.name).contains(Quran.plain(q)) || it.number.toString() == q } else surahs
                items(filtered, key = { it.number }) { s ->
                    Card(
                        onClick = { open = s.number to 1 }, modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(38.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                                Text(Quran.toArabicDigits(s.number), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(s.name, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                Text("${s.revelationAr} • ${s.ayahs.size} آية • الجزء ${s.ayahs.first().juz}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                            }
                            Text(s.english, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuranReader(surahs: List<Surah>, surahNo: Int, startAyah: Int, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var current by remember { mutableIntStateOf(surahNo) }
    val s = surahs[current - 1]
    var size by remember { mutableIntStateOf(Quran.fontSize) }
    var marks by remember { mutableStateOf(Quran.bookmarks) }
    val family = remember {
        if (Quran.hasFont(ctx)) runCatching { FontFamily(Font("fonts/quran.ttf", ctx.assets)) }.getOrNull() ?: FontFamily.Default else FontFamily.Default
    }
    val state = rememberLazyListState()
    LaunchedEffect(current) {
        state.scrollToItem(if (current == surahNo && startAyah > 1) startAyah else 0)
    }
    // remember position
    LaunchedEffect(state.firstVisibleItemIndex, current) {
        delay(600)
        Quran.lastSurah = current
        Quran.lastAyah = (state.firstVisibleItemIndex).coerceIn(1, s.ayahs.size)
    }
    val accent = MaterialTheme.colorScheme.primary
    var sheetAyah by remember { mutableStateOf<Int?>(null) }

    ScreenScaffold(
        s.name, onBack = onBack,
        actions = {
            IconButton(onClick = { size = (size - 2).coerceAtLeast(16); Quran.fontSize = size }) { Icon(Icons.Default.TextDecrease, "أصغر") }
            IconButton(onClick = { size = (size + 2).coerceAtMost(48); Quran.fontSize = size }) { Icon(Icons.Default.TextIncrease, "أكبر") }
        },
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), state = state, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
            item {
                Column(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(s.name, fontFamily = family, fontSize = (size + 4).sp, color = accent)
                            Text("${s.revelationAr} • ${s.ayahs.size} آية", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (s.number != 1 && s.number != 9) {
                        Spacer(Modifier.height(12.dp))
                        Text(Quran.BASMALA, fontFamily = family, fontSize = size.sp, textAlign = TextAlign.Center)
                    }
                }
            }
            itemsIndexed(s.ayahs, key = { _, a -> a.n }) { _, a ->
                val key = "${s.number}:${a.n}"
                val marked = key in marks
                Column(
                    Modifier.fillMaxWidth()
                        .clickable { sheetAyah = a.n }
                        .background(if (marked) MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f) else androidx.compose.ui.graphics.Color.Transparent)
                        .padding(vertical = 8.dp),
                ) {
                    Text(
                        buildAnnotatedString {
                            append(a.text)
                            append(" ")
                            withStyle(SpanStyle(color = accent)) { append("﴿${Quran.toArabicDigits(a.n)}﴾") }
                        },
                        fontFamily = family, fontSize = size.sp, lineHeight = (size * 1.9).sp,
                        textAlign = TextAlign.Justify, modifier = Modifier.fillMaxWidth(),
                    )
                    if (a.sajda) Text("۩ سجدة", color = accent, style = MaterialTheme.typography.labelSmall)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            }
            item {
                Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    if (current > 1) OutlinedButton(onClick = { current -= 1 }) { Text("السورة اللي قبلها") } else Spacer(Modifier)
                    if (current < 114) Button(onClick = { current += 1 }) { Text("السورة اللي بعدها") }
                }
                Text("دوس على أي آية علشان تقرا تفسيرها أو تحط عليها علامة.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                OutlinedButton(onClick = { com.mohamed.safi.faith.Shaarawy.open(ctx, com.mohamed.safi.faith.Shaarawy.surahSearch(s.name)) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Icon(Icons.Default.PlayCircle, null); Spacer(Modifier.width(6.dp)); Text("خواطر الشعراوي عن ${s.name}")
                }
            }
        }
    }

    sheetAyah?.let { n ->
        val a = s.ayahs.first { it.n == n }
        AyahSheet(s, a, family, size, key = "${s.number}:${a.n}", marked = "${s.number}:${a.n}" in marks,
            onToggleMark = {
                val k = "${s.number}:${a.n}"
                marks = if (k in marks) marks - k else marks + k
                Quran.bookmarks = marks
            },
            onDismiss = { sheetAyah = null })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AyahSheet(
    s: Surah, a: com.mohamed.safi.faith.Ayah, family: FontFamily, size: Int, key: String, marked: Boolean,
    onToggleMark: () -> Unit, onDismiss: () -> Unit,
) {
    val ctx = LocalContext.current
    var edition by remember { mutableStateOf(com.mohamed.safi.faith.Tafsir.editions.first().first) }
    var text by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(edition) {
        text = null; failed = false
        text = com.mohamed.safi.faith.Tafsir.of(edition, s.number, a.n)
        if (text == null) failed = true
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().heightIn(max = 600.dp).verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text("${s.name} • آية ${a.n}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(6.dp))
            Text(a.text, fontFamily = family, fontSize = (size - 2).sp, lineHeight = ((size - 2) * 1.8).sp)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                com.mohamed.safi.faith.Tafsir.editions.forEach { (id, title) ->
                    FilterChip(edition == id, { edition = id }, label = { Text(title) })
                }
            }
            Spacer(Modifier.height(8.dp))
            when {
                text != null -> Text(text!!, style = MaterialTheme.typography.bodyLarge, lineHeight = 30.sp)
                failed -> Text("التفسير محتاج إنترنت أول مرة علشان يتحمّل", color = MaterialTheme.colorScheme.outline)
                else -> CircularProgressIndicator(Modifier.size(24.dp))
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onToggleMark) {
                    Icon(if (marked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder, null)
                    Spacer(Modifier.width(4.dp)); Text(if (marked) "شيل العلامة" else "علامة")
                }
                OutlinedButton(onClick = {
                    val share = "${a.text}\n[${s.name}: ${a.n}]" + (text?.let { "\n\n${com.mohamed.safi.faith.Tafsir.editions.first { it.first == edition }.second}: $it" } ?: "")
                    ctx.startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, share), "شارك").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                }) { Icon(Icons.Default.Share, null); Spacer(Modifier.width(4.dp)); Text("شارك") }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
