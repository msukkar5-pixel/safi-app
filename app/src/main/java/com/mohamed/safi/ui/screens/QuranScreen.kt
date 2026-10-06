package com.mohamed.safi.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
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
        UiBus.pendingQuran.value?.let { open = it; UiBus.pendingQuran.value = null }
    }

    val surahs = list
    val o = open
    if (surahs != null && o != null) {
        BackHandler { open = null }
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
                val pq = Quran.plain(q)
                val filtered = if (searching && q.isNotBlank()) surahs.filter { Quran.plain(it.name).contains(pq) || it.number.toString() == q } else surahs
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

private val quranModes = listOf("mushaf" to "مصحف", "flow" to "متصل", "ayat" to "آية آية", "tafsir" to "مع التفسير")

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun QuranReader(surahs: List<Surah>, surahNo: Int, startAyah: Int, onBack: () -> Unit) {
    var current by remember { mutableIntStateOf(surahNo) }
    var start by remember { mutableIntStateOf(startAyah) }
    var mode by remember { mutableStateOf(Quran.viewMode) }
    var size by remember { mutableIntStateOf(Quran.fontSize) }
    var marks by remember { mutableStateOf(Quran.bookmarks) }
    var fontId by remember { mutableStateOf(Quran.quranFont) }
    var bars by remember { mutableStateOf(true) }
    var auto by remember { mutableStateOf(false) }
    var speed by remember { mutableIntStateOf(Quran.autoSpeed) }
    var sheet by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val family = if (fontId == "system") FontFamily.Default else Amiri
    val hasPages = remember(surahs) { surahs.all { s -> s.ayahs.all { it.page in 1..604 } } }
    if (mode == "mushaf" && !hasPages) mode = "ayat"
    val s = surahs[current - 1]
    ImmersiveEffect(!bars)

    ReadingTheme {
        val st = rememberReadStyle()
        ScreenScaffold(
            if (mode == "mushaf") "المصحف" else s.name, onBack = onBack, showTopBar = bars,
            actions = {
                IconButton(onClick = { UiBus.pendingQuranAudio.value = current; UiBus.pendingRoute.value = "quranaudio" }) { Icon(Icons.Default.Headphones, "استمع") }
                IconButton(onClick = { size = (size + 2).coerceAtMost(48); Quran.fontSize = size }) { Icon(Icons.Default.TextIncrease, "أكبر") }
                IconButton(onClick = { size = (size - 2).coerceAtLeast(16); Quran.fontSize = size }) { Icon(Icons.Default.TextDecrease, "أصغر") }
                ReadingSettingsButton {
                    Text("طريقة عرض المصحف", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        quranModes.forEach { (id, t) -> if (id != "mushaf" || hasPages) FilterChip(mode == id, { mode = id; Quran.viewMode = id }, label = { Text(t) }) }
                    }
                    Text("خط المصحف", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(fontId == "amiri", { fontId = "amiri"; Quran.quranFont = "amiri" }, label = { Text("نسخ (أميري)", fontFamily = Amiri) })
                        FilterChip(fontId == "system", { fontId = "system"; Quran.quranFont = "system" }, label = { Text("خط الجهاز") })
                    }
                    Text("حجم خط المصحف: ${Quran.toArabicDigits(size)}", style = MaterialTheme.typography.labelLarge)
                    Slider(size.toFloat(), { size = it.toInt(); Quran.fontSize = size }, valueRange = 16f..48f)
                }
            },
        ) { pad ->
            Column(Modifier.fillMaxSize().padding(pad)) {
                if (bars) Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    quranModes.forEach { (id, t) ->
                        if (id != "mushaf" || hasPages) FilterChip(mode == id, { mode = id; Quran.viewMode = id; auto = false }, label = { Text(t, fontSize = 12.sp) })
                    }
                }
                Box(Modifier.weight(1f)) {
                    val tap: (Int, Int) -> Unit = { sn, an -> sheet = sn to an }
                    when (mode) {
                        "mushaf" -> MushafPager(surahs, current, start, size, family, st, marks, onTap = tap, onToggleBars = { bars = !bars }) { sn, an ->
                            current = sn; start = an; Quran.lastSurah = sn; Quran.lastAyah = an
                        }
                        else -> SurahList(
                            surahs, s, start, mode, size, family, st, marks, auto, speed,
                            onTap = tap, onToggleBars = { bars = !bars },
                            onSurah = { current = it; start = 1 },
                        )
                    }
                }
                if (mode != "mushaf" && bars) Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalIconButton(onClick = { auto = !auto }) { Icon(if (auto) Icons.Default.Pause else Icons.Default.PlayArrow, "تمرير تلقائي") }
                    Spacer(Modifier.width(8.dp))
                    Text(if (auto) "تمرير تلقائي" else "تمرير تلقائي للقراءة الطويلة", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                    if (auto) {
                        IconButton(onClick = { speed = (speed - 1).coerceAtLeast(1); Quran.autoSpeed = speed }) { Icon(Icons.Default.Remove, "أبطأ") }
                        Text(Quran.toArabicDigits(speed))
                        IconButton(onClick = { speed = (speed + 1).coerceAtMost(8); Quran.autoSpeed = speed }) { Icon(Icons.Default.Add, "أسرع") }
                    }
                }
            }
        }
    }

    sheet?.let { (sn, an) ->
        val ss = surahs[sn - 1]
        val a = ss.ayahs.first { it.n == an }
        val k = "$sn:$an"
        AyahSheet(ss, a, family, size, key = k, marked = k in marks,
            onToggleMark = { marks = if (k in marks) marks - k else marks + k; Quran.bookmarks = marks },
            onDismiss = { sheet = null })
    }
}

/** Ayahs as one flowing, justified paragraph; tapping an ayah opens its sheet. */
@Composable
private fun AyahFlow(
    surahNo: Int, ayahs: List<com.mohamed.safi.faith.Ayah>, size: Int, family: FontFamily, st: ReadStyle, marks: Set<String>,
    onTap: (Int, Int) -> Unit, onToggleBars: () -> Unit, modifier: Modifier = Modifier,
) {
    val markBg = st.accent.copy(alpha = 0.16f)
    val ann = remember(ayahs, marks, st.accent) {
        buildAnnotatedString {
            ayahs.forEach { a ->
                pushStringAnnotation("a", a.n.toString())
                if ("$surahNo:${a.n}" in marks) withStyle(SpanStyle(background = markBg)) { append(a.text) } else append(a.text)
                append(" ")
                withStyle(SpanStyle(color = st.accent)) { append("﴿${Quran.toArabicDigits(a.n)}﴾") }
                if (a.sajda) withStyle(SpanStyle(color = st.accent)) { append(" ۩") }
                pop()
                append(" ")
            }
        }
    }
    var layout by remember { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }
    androidx.compose.material3.Text(
        ann,
        modifier = modifier.fillMaxWidth().pointerInput(ann) {
            detectTapGestures(
                onDoubleTap = { onToggleBars() },
                onTap = { pos ->
                    val off = layout?.getOffsetForPosition(pos) ?: return@detectTapGestures
                    ann.getStringAnnotations("a", off, off).firstOrNull()?.let { onTap(surahNo, it.item.toInt()) }
                },
            )
        },
        onTextLayout = { layout = it },
        color = st.fg, fontFamily = family, fontSize = size.sp, lineHeight = (size * st.line * 1.1f).sp, textAlign = TextAlign.Justify,
    )
}

@Composable
private fun SurahBanner(s: Surah, size: Int, family: FontFamily, st: ReadStyle) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(st.accent.copy(alpha = 0.10f))
                .border(1.5.dp, Gold, RoundedCornerShape(10.dp)).padding(3.dp).border(0.8.dp, Gold.copy(alpha = 0.6f), RoundedCornerShape(8.dp)).padding(vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("۞", color = Gold, fontSize = 18.sp)
                Spacer(Modifier.width(10.dp))
                Text(s.name, fontFamily = Amiri, fontWeight = FontWeight.Bold, fontSize = (size * 0.85f).sp, color = st.accent)
                Spacer(Modifier.width(10.dp))
                Text("۞", color = Gold, fontSize = 18.sp)
            }
        }
        Text("${s.revelationAr} • ${Quran.toArabicDigits(s.ayahs.size)} آية", fontSize = 11.sp, color = st.fg.copy(alpha = 0.6f))
        if (s.number != 1 && s.number != 9) Text(Quran.BASMALA, fontFamily = family, fontSize = size.sp, color = st.fg, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun MushafPager(
    surahs: List<Surah>, surahNo: Int, startAyah: Int, size: Int, family: FontFamily, st: ReadStyle, marks: Set<String>,
    onTap: (Int, Int) -> Unit, onToggleBars: () -> Unit, onPage: (Int, Int) -> Unit,
) {
    val pages = remember(surahs) { surahs.flatMap { s -> s.ayahs.map { s to it } }.groupBy { it.second.page } }
    val startPage = remember { surahs[surahNo - 1].ayahs.firstOrNull { it.n >= startAyah }?.page ?: 1 }
    val pager = androidx.compose.foundation.pager.rememberPagerState(initialPage = startPage - 1) { 604 }
    LaunchedEffect(pager.currentPage) {
        delay(400)
        pages[pager.currentPage + 1]?.firstOrNull()?.let { (s, a) -> onPage(s.number, a.n) }
    }
    androidx.compose.foundation.pager.HorizontalPager(pager, Modifier.fillMaxSize(), beyondViewportPageCount = 1) { p ->
        val items = pages[p + 1].orEmpty()
        val juz = items.firstOrNull()?.second?.juz ?: 0
        Box(Modifier.fillMaxSize().padding(8.dp)) {
            Column(
                Modifier.fillMaxSize().clip(RoundedCornerShape(6.dp)).background(st.bg)
                    .border(2.dp, Gold.copy(alpha = 0.85f), RoundedCornerShape(6.dp)).padding(4.dp)
                    .border(0.8.dp, st.accent.copy(alpha = 0.5f), RoundedCornerShape(4.dp)).padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Row(Modifier.fillMaxWidth()) {
                    Text(items.firstOrNull()?.first?.name ?: "", fontFamily = Amiri, fontSize = 13.sp, color = st.accent, modifier = Modifier.weight(1f))
                    Text("الجزء ${Quran.toArabicDigits(juz)}", fontFamily = Amiri, fontSize = 13.sp, color = st.accent)
                }
                HorizontalDivider(color = Gold.copy(alpha = 0.5f))
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = 6.dp)) {
                    var i = 0
                    while (i < items.size) {
                        val s = items[i].first
                        var j = i
                        while (j < items.size && items[j].first.number == s.number) j++
                        val run = items.subList(i, j).map { it.second }
                        if (run.first().n == 1) SurahBanner(s, size, family, st)
                        AyahFlow(s.number, run, size, family, st, marks, onTap, onToggleBars)
                        i = j
                    }
                }
                HorizontalDivider(color = Gold.copy(alpha = 0.5f))
                Text("﴾ ${Quran.toArabicDigits(p + 1)} ﴿", fontFamily = Amiri, fontSize = 13.sp, color = st.accent, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun SurahList(
    surahs: List<Surah>, s: Surah, startAyah: Int, mode: String, size: Int, family: FontFamily, st: ReadStyle, marks: Set<String>,
    auto: Boolean, speed: Int, onTap: (Int, Int) -> Unit, onToggleBars: () -> Unit, onSurah: (Int) -> Unit,
) {
    val ctx = LocalContext.current
    val state = rememberLazyListState()
    val chunk = 8
    val chunks = remember(s, mode) { if (mode == "flow") s.ayahs.chunked(chunk) else s.ayahs.map { listOf(it) } }
    var tafsir by remember { mutableStateOf<Map<Int, List<String>>?>(null) }
    var tafsirFailed by remember { mutableStateOf(false) }
    if (mode == "tafsir") LaunchedEffect(Unit) {
        runCatching { tafsir = com.mohamed.safi.faith.Tafsir.load("muyassar") }.onFailure { tafsirFailed = true }
    }
    LaunchedEffect(s.number, mode) {
        val idx = chunks.indexOfFirst { c -> c.any { it.n == startAyah } }.coerceAtLeast(0)
        state.scrollToItem(if (startAyah > 1) idx + 1 else 0)
    }
    LaunchedEffect(state.firstVisibleItemIndex, s.number) {
        delay(600)
        Quran.lastSurah = s.number
        Quran.lastAyah = chunks.getOrNull((state.firstVisibleItemIndex - 1).coerceAtLeast(0))?.first()?.n ?: 1
    }
    LaunchedEffect(auto, speed) {
        while (auto) {
            state.scrollBy(speed * 0.6f)
            delay(16)
        }
    }
    LazyColumn(Modifier.fillMaxSize(), state = state, contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp)) {
        item { SurahBanner(s, size, family, st) }
        itemsIndexed(chunks, key = { _, c -> c.first().n }) { _, c ->
            when (mode) {
                "flow" -> AyahFlow(s.number, c, size, family, st, marks, onTap, onToggleBars, Modifier.padding(vertical = 2.dp))
                else -> {
                    val a = c.first()
                    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        AyahFlow(s.number, c, size, family, st, marks, onTap, onToggleBars)
                        if (mode == "tafsir") {
                            val t = tafsir?.get(s.number)?.getOrNull(a.n - 1)
                            Spacer(Modifier.height(4.dp))
                            Surface(color = st.card, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    t ?: if (tafsirFailed) "التفسير محتاج إنترنت أول مرة" else "…",
                                    Modifier.padding(10.dp), fontSize = st.size(14f), lineHeight = st.lineH(14f), color = st.fg.copy(alpha = 0.85f),
                                )
                            }
                        }
                    }
                    HorizontalDivider(color = st.fg.copy(alpha = 0.08f))
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                if (s.number > 1) OutlinedButton(onClick = { onSurah(s.number - 1) }) { Text("السورة اللي قبلها") } else Spacer(Modifier)
                if (s.number < 114) Button(onClick = { onSurah(s.number + 1) }) { Text("السورة اللي بعدها") }
            }
            Text("دوس على أي آية للتفسير أو العلامة أو المشاركة • دوس مرتين لوضع التركيز", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            OutlinedButton(onClick = { com.mohamed.safi.faith.Shaarawy.open(ctx, com.mohamed.safi.faith.Shaarawy.surahSearch(s.name)) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Icon(Icons.Default.PlayCircle, null); Spacer(Modifier.width(6.dp)); Text("خواطر الشعراوي عن ${s.name}")
            }
            Spacer(Modifier.height(40.dp))
        }
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
                    ShareBus.open("📖", "﴿ ${a.text} ﴾", "${s.name}: ${a.n}", text?.let { "${com.mohamed.safi.faith.Tafsir.editions.first { it.first == edition }.second}: $it" } ?: "")
                }) { Icon(Icons.Default.Share, null); Spacer(Modifier.width(4.dp)); Text("شارك") }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
