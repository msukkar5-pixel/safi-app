package com.mohamed.safi.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import com.mohamed.safi.ui.Text
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.session.MediaController
import com.mohamed.safi.audio.Player
import com.mohamed.safi.faith.*
import com.mohamed.safi.ui.*
import kotlinx.coroutines.delay

// ===================================================================== helpers

private fun shareText(ctx: Context, text: String) {
    runCatching {
        ctx.startActivity(
            Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "شارك")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

/** Follows a link from guide content: "tool:tawaf", "azkar:morning", or a plain app route. */
fun openDeenLink(route: String, open: (String) -> Unit) {
    when {
        route.startsWith("tool:") -> open("tool/" + route.substringAfter(':'))
        route.startsWith("azkar:") -> {
            val k = route.substringAfter(':')
            UiBus.pendingAzkar.value = when (k) { "morning" -> "الصباح"; "evening" -> "المساء"; "sleep" -> "النوم"; else -> k }
            open("azkar")
        }
        route.isNotBlank() -> open(route)
    }
}

private fun openAyah(s: Int, a: Int, open: (String) -> Unit) {
    if (s !in 1..114) return
    UiBus.pendingQuran.value = s to a.coerceAtLeast(1)
    open("quran")
}

private val toolNames = mapOf(
    "tawaf" to ("عداد الطواف" to Icons.Default.Loop),
    "sai" to ("عداد السعي" to Icons.Default.SwapVert),
    "rami" to ("رمي الجمرات" to Icons.Default.Grain),
    "hajjplan" to ("خطة أيام الحج" to Icons.Default.EventNote),
    "checklist" to ("شنطة السفر" to Icons.Default.Luggage),
)

private fun guideTools(id: String) = when (id) {
    "umrah" -> listOf("tawaf", "sai", "checklist")
    "hajj" -> listOf("hajjplan", "tawaf", "sai", "rami", "checklist")
    else -> emptyList()
}

// ===================================================================== Hajj & Umrah hub

@Composable
fun ManasikScreen(onBack: () -> Unit, open: (String) -> Unit) {
    ScreenScaffold("الحج والعمرة", onBack = onBack) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Text(
                    "«العمرة إلى العمرة كفارة لما بينهما، والحج المبرور ليس له جزاء إلا الجنة» — متفق عليه",
                    fontFamily = Amiri, fontSize = 17.sp, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.primary, modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf("umrah" to "🕋", "hajj" to "⛰️").forEach { (id, icon) ->
                        val g = remember(id) { runCatching { Deen.guide(id) }.getOrNull() }
                        GoldCard(onClick = { open(id) }, modifier = Modifier.weight(1f)) {
                            Text(icon, fontSize = 30.sp)
                            Spacer(Modifier.height(6.dp))
                            Text(g?.title ?: "", fontFamily = Amiri, fontWeight = FontWeight.Bold, fontSize = 19.sp)
                            Text(g?.sub ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, maxLines = 3)
                        }
                    }
                }
            }
            item { SectionTitle("أدوات وأنت هناك") }
            item { ToolGrid(listOf("tawaf", "sai", "rami", "hajjplan", "checklist"), open) }
            item { SectionTitle("بالرسم") }
            item { DeenDiagram("umrah_flow") }
            item { DeenDiagram("hajj_days") }
            item { SectionTitle("مرتبط") }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(onClick = { open("hisn") }, label = { Text("حصن المسلم") }, leadingIcon = { Icon(Icons.Default.Shield, null) })
                    AssistChip(onClick = { open("ruqyah") }, label = { Text("الرقية الشرعية") }, leadingIcon = { Icon(Icons.Default.Healing, null) })
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun ToolGrid(tools: List<String>, open: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        tools.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { t ->
                    val (name, icon) = toolNames[t] ?: return@forEach
                    AppCard(onClick = { open("tool/$t") }, modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text(name, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

// ===================================================================== guide (umrah / hajj / ruqyah)

@Composable
fun GuideScreen(id: String, onBack: () -> Unit, open: (String) -> Unit) {
    val ctx = LocalContext.current
    val guide = remember(id) { runCatching { Deen.guide(id, ctx) }.getOrNull() }
    val media = remember(id) { runCatching { Deen.media(id, ctx) }.getOrNull() }
    var secId by rememberSaveable { mutableStateOf<String?>(null) }
    if (guide == null) {
        ScreenScaffold("", onBack = onBack) { pad -> EmptyState(Icons.Default.ErrorOutline, "المحتوى ده مش موجود في النسخة دي", Modifier.padding(pad)) }
        return
    }
    val sec = guide.sections.firstOrNull { it.id == secId }
    if (sec != null) {
        GuideSectionView(guide, sec, open, onSection = { secId = it }, onBack = { secId = null })
        return
    }
    var q by rememberSaveable { mutableStateOf("") }
    val hits = remember(q) {
        val p = Quran.plain(q.trim())
        if (p.length < 2) emptyList()
        else guide.sections.flatMap { s -> s.blocks.filter { p in it.plain }.map { s to it } }.take(60)
    }
    val last = remember { Deen.lastSection(id) }

    ScreenScaffold(guide.title, onBack = onBack) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                GoldCard {
                    Text(guide.title, fontFamily = Amiri, fontWeight = FontWeight.Bold, fontSize = 24.sp, color = MaterialTheme.colorScheme.primary)
                    Text(guide.sub, style = MaterialTheme.typography.bodyMedium)
                    Text("كل حديث هنا متراجع على كتب السنة، وكل آية من المصحف.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(top = 6.dp))
                    guide.sections.firstOrNull { it.id == last }?.let { s ->
                        Spacer(Modifier.height(8.dp))
                        FilledTonalButton(onClick = { secId = s.id }) { Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text("كمّل: ${s.title}", maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                }
            }
            item {
                OutlinedTextField(
                    q, { q = it }, Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("دوّر في ${guide.title}…") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = { if (q.isNotEmpty()) IconButton(onClick = { q = "" }) { Icon(Icons.Default.Close, "مسح") } },
                    shape = RoundedCornerShape(14.dp),
                )
            }
            if (q.trim().length >= 2) {
                if (hits.isEmpty()) item { EmptyState(Icons.Default.SearchOff, "مفيش نتائج") }
                items(hits) { (s, b) ->
                    AppCard(onClick = { secId = s.id }) {
                        Text("${s.icon} ${s.title}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Text((listOf(b.q, b.title, b.x, b.items.firstOrNull() ?: "").firstOrNull { it.isNotBlank() } ?: b.rows.flatten().joinToString(" • ")).take(220), maxLines = 4, overflow = TextOverflow.Ellipsis)
                    }
                }
                return@LazyColumn
            }
            val tools = guideTools(id)
            if (tools.isNotEmpty()) {
                item { SectionTitle("أدوات") }
                item { ToolGrid(tools, open) }
            }
            item { SectionTitle("الدليل") }
            itemsIndexed(guide.sections, key = { _, s -> s.id }) { i, s ->
                AppCard(onClick = { secId = s.id }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(42.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                            Text(s.icon.ifBlank { "${i + 1}" }, fontSize = 20.sp)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(s.title, fontWeight = FontWeight.Bold)
                            if (s.sub.isNotBlank()) Text(s.sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, maxLines = 2)
                        }
                        Icon(Icons.Default.ChevronLeft, null, tint = MaterialTheme.colorScheme.outline)
                    }
                }
            }
            if (media != null) {
                if (media.surahs.isNotEmpty()) {
                    item { SectionTitle("سور الرقية — اقرأها أو اسمعها") }
                    item {
                        var names by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
                        LaunchedEffect(Unit) { names = runCatching { Quran.surahs(ctx).associate { it.number to it.name.removePrefix("سُورَةُ ").removePrefix("سورة ") } }.getOrDefault(emptyMap()) }
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            media.surahs.forEach { n ->
                                AssistChip(onClick = { openAyah(n, 1, open) }, label = { Text(names[n]?.let { "سورة $it" } ?: "سورة $n") }, leadingIcon = { Icon(Icons.Default.MenuBook, null) })
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        OutlinedButton(onClick = { UiBus.pendingQuranAudio.value = media.surahs.first(); open("quranaudio") }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Headphones, null); Spacer(Modifier.width(6.dp)); Text("اسمعها بصوت قارئ")
                        }
                    }
                }
                val books = media.books.mapNotNull { Books.meta(it) }
                if (books.isNotEmpty()) {
                    item { SectionTitle("كتب للتوسع") }
                    items(books) { b ->
                        AppCard(onClick = { open("book/${b.id}") }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Book, null, tint = Gold)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(b.title, fontFamily = Amiri, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                                    Text(b.author, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                }
                            }
                        }
                    }
                }
                if (media.videos.isNotEmpty()) {
                    item { SectionTitle("فيديوهات") }
                    items(media.videos) { v -> VideoRow(v.title, v.who, v.url) }
                }
            }
            if (id == "ruqyah") item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(onClick = { open("hisn") }, label = { Text("حصن المسلم") }, leadingIcon = { Icon(Icons.Default.Shield, null) })
                    AssistChip(onClick = { open("azkar") }, label = { Text("الأذكار") }, leadingIcon = { Icon(Icons.Default.Favorite, null) })
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun VideoRow(title: String, who: String, url: String) {
    val ctx = LocalContext.current
    AppCard(onClick = { Shaarawy.openUrl(ctx, url) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.PlayCircle, null, tint = Color(0xFFC62828))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                if (who.isNotBlank()) Text(who, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
            Icon(Icons.Default.OpenInNew, null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun GuideSectionView(guide: Guide, s: GSection, open: (String) -> Unit, onSection: (String) -> Unit, onBack: () -> Unit) {
    val idx = guide.sections.indexOf(s)
    LaunchedEffect(s.id) { Deen.setLastSection(guide.id, s.id) }
    ReadingTheme {
        ScreenScaffold(s.title, onBack = onBack, actions = { ReadingSettingsButton() }) { pad ->
            val state = androidx.compose.foundation.lazy.rememberLazyListState()
            LaunchedEffect(s.id) { state.scrollToItem(0) }
            LazyColumn(Modifier.fillMaxSize().padding(pad), state = state, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (s.sub.isNotBlank()) item { Text(s.sub, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold) }
                itemsIndexed(s.blocks) { _, b -> GuideBlockView(b, open) }
                item {
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        guide.sections.getOrNull(idx - 1)?.let { p -> OutlinedButton(onClick = { onSection(p.id) }) { Text("‹ ${p.title}", maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 130.dp)) } } ?: Spacer(Modifier)
                        guide.sections.getOrNull(idx + 1)?.let { n -> Button(onClick = { onSection(n.id) }) { Text("${n.title} ›", maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 150.dp)) } }
                    }
                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }
}

// ===================================================================== block renderer

@Composable
fun GuideBlockView(b: GBlock, open: (String) -> Unit) {
    val ctx = LocalContext.current
    val rs = rememberReadStyle()
    val cs = MaterialTheme.colorScheme
    when (b.t) {
        "p" -> Text(b.x, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Justify)
        "h" -> Text(b.x, fontFamily = Amiri, fontWeight = FontWeight.Bold, fontSize = rs.size(20f), color = cs.primary, modifier = Modifier.padding(top = 6.dp))
        "steps" -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            b.items.forEachIndexed { i, s ->
                Row {
                    Box(Modifier.size(28.dp).clip(CircleShape).background(cs.primary), contentAlignment = Alignment.Center) {
                        Text("${i + 1}", color = cs.onPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(s, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                }
            }
        }
        "list" -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            b.items.forEach { s ->
                Row {
                    Text("•", color = Gold, fontWeight = FontWeight.Bold, fontSize = rs.size(18f))
                    Spacer(Modifier.width(8.dp))
                    Text(s, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                }
            }
        }
        "ayah" -> Surface(
            shape = RoundedCornerShape(14.dp), color = cs.primary.copy(alpha = 0.06f),
            modifier = Modifier.fillMaxWidth().border(1.dp, Gold.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                .clickable(enabled = b.s in 1..114) { openAyah(b.s, b.a, open) },
        ) {
            Column(Modifier.padding(14.dp)) {
                if (b.title.isNotBlank()) Text(b.title, fontWeight = FontWeight.Bold, color = cs.primary, modifier = Modifier.padding(bottom = 4.dp))
                androidx.compose.material3.Text(
                    "﴿ ${b.x} ﴾", fontFamily = Amiri, fontSize = rs.size(21f), lineHeight = rs.lineH(21f), textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                    Text(b.src, style = MaterialTheme.typography.labelMedium, color = Gold, modifier = Modifier.weight(1f))
                    if (b.n > 1) Pill("${b.n} مرات")
                    if (b.s in 1..114) Icon(Icons.Default.MenuBook, "افتح في المصحف", tint = cs.outline, modifier = Modifier.size(18.dp))
                }
            }
        }
        "hadith" -> Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).clip(RoundedCornerShape(12.dp)).background(cs.surfaceContainerLow)) {
            Box(Modifier.width(4.dp).fillMaxHeight().background(cs.primary))
            Column(Modifier.padding(12.dp).weight(1f)) {
                if (b.who.isNotBlank()) Text("عن ${b.who}", style = MaterialTheme.typography.labelMedium, color = cs.outline)
                androidx.compose.material3.Text("«${b.x}»", fontFamily = rs.family, fontSize = rs.size(17f), lineHeight = rs.lineH(17f), textAlign = TextAlign.Justify)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    Text(b.src, style = MaterialTheme.typography.labelMedium, color = cs.primary, modifier = Modifier.weight(1f))
                    IconButton(onClick = { shareText(ctx, "«${b.x}»\n[${b.src}]") }, modifier = Modifier.size(30.dp)) { Icon(Icons.Default.Share, "شارك", Modifier.size(16.dp), tint = cs.outline) }
                }
            }
        }
        "dua" -> DuaCard(b.title, b.x, b.src, b.n)
        "note", "tip", "warn" -> {
            val (color, icon) = when (b.t) {
                "warn" -> Danger to Icons.Default.WarningAmber
                "tip" -> Positive to Icons.Default.Lightbulb
                else -> Gold to Icons.Default.Info
            }
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.10f)).border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(12.dp)).padding(12.dp)) {
                Icon(icon, null, tint = color)
                Spacer(Modifier.width(10.dp))
                Text(b.x, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            }
        }
        "diagram" -> Column {
            if (b.k in deenDiagrams) DeenDiagram(b.k)
            if (b.x.isNotBlank()) Text(b.x, style = MaterialTheme.typography.bodySmall, color = cs.outline, modifier = Modifier.padding(top = 4.dp))
        }
        "qa" -> {
            var openQ by remember { mutableStateOf(false) }
            Surface(shape = RoundedCornerShape(12.dp), color = cs.surfaceContainerLow, modifier = Modifier.fillMaxWidth().clickable { openQ = !openQ }) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("س:", fontWeight = FontWeight.Bold, color = cs.primary)
                        Spacer(Modifier.width(6.dp))
                        Text(b.q, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Icon(if (openQ) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
                    }
                    if (openQ) Text(b.x, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
        "table" -> GuideTable(b.head, b.rows)
        "video" -> VideoRow(b.x, b.who, b.url)
        "link" -> AppCard(onClick = { openDeenLink(b.route, open) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (b.route.startsWith("tool:")) Icons.Default.TouchApp else Icons.Default.ArrowCircleLeft, null, tint = cs.primary)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(b.x, fontWeight = FontWeight.Bold, color = cs.primary)
                    if (b.sub.isNotBlank()) Text(b.sub, style = MaterialTheme.typography.bodySmall, color = cs.outline)
                }
            }
        }
        "longtext" -> {
            var all by remember { mutableStateOf(false) }
            Surface(shape = RoundedCornerShape(12.dp), color = cs.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    androidx.compose.material3.Text(
                        if (all || b.x.length <= 900) b.x else b.x.take(900) + "…",
                        fontFamily = rs.family, fontSize = rs.size(17f), lineHeight = rs.lineH(17f), textAlign = TextAlign.Justify,
                    )
                    if (b.src.isNotBlank()) Text(b.src, style = MaterialTheme.typography.labelMedium, color = cs.primary, modifier = Modifier.padding(top = 6.dp))
                    if (b.x.length > 900) TextButton(onClick = { all = !all }) { Text(if (all) "اختصر" else "اقرأ النص كامل") }
                }
            }
        }
        else -> if (b.x.isNotBlank()) Text(b.x, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun DuaCard(title: String, text: String, src: String, n: Int) {
    val ctx = LocalContext.current
    val rs = rememberReadStyle()
    val haptic = LocalHapticFeedback.current
    var left by remember(text) { mutableIntStateOf(n.coerceAtLeast(1)) }
    val cs = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(14.dp), color = if (left == 0) Positive.copy(alpha = 0.10f) else cs.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().border(1.dp, Gold.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
            .clickable(enabled = n > 1) { if (left > 0) { left--; haptic.performHapticFeedback(HapticFeedbackType.LongPress) } },
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🤲", fontSize = 18.sp)
                Spacer(Modifier.width(6.dp))
                Text(title.ifBlank { "دعاء" }, fontWeight = FontWeight.Bold, color = cs.primary, modifier = Modifier.weight(1f))
                IconButton(onClick = { shareText(ctx, "$text\n[$src]") }, modifier = Modifier.size(30.dp)) { Icon(Icons.Default.Share, "شارك", Modifier.size(16.dp), tint = cs.outline) }
            }
            androidx.compose.material3.Text(text, fontFamily = Amiri, fontSize = rs.size(20f), lineHeight = rs.lineH(20f), textAlign = TextAlign.Justify, modifier = Modifier.padding(vertical = 6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(src, style = MaterialTheme.typography.labelMedium, color = Gold, modifier = Modifier.weight(1f))
                if (n > 1) {
                    Surface(shape = CircleShape, color = if (left == 0) Positive else cs.primary, contentColor = Color.White) {
                        Text(if (left == 0) "✓" else "$left / $n", fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                    }
                    if (left < n) IconButton(onClick = { left = n }, modifier = Modifier.size(30.dp)) { Icon(Icons.Default.Refresh, "من الأول", Modifier.size(16.dp)) }
                }
            }
        }
    }
}

@Composable
private fun GuideTable(head: List<String>, rows: List<List<String>>) {
    val cs = MaterialTheme.colorScheme
    val cols = maxOf(head.size, rows.maxOfOrNull { it.size } ?: 0)
    if (cols == 0) return
    val wide = cols > 3
    val content: @Composable () -> Unit = {
        Column(Modifier.clip(RoundedCornerShape(12.dp)).border(1.dp, cs.outlineVariant, RoundedCornerShape(12.dp))) {
            if (head.any { it.isNotBlank() }) Row(Modifier.background(cs.primary.copy(alpha = 0.12f)).height(IntrinsicSize.Min)) {
                (0 until cols).forEach { i ->
                    Text(head.getOrElse(i) { "" }, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = cs.primary,
                        modifier = (if (wide) Modifier.width(120.dp) else Modifier.weight(1f)).padding(8.dp))
                }
            }
            rows.forEachIndexed { r, row ->
                Row(Modifier.background(if (r % 2 == 1) cs.surfaceContainerLow else Color.Transparent).height(IntrinsicSize.Min)) {
                    (0 until cols).forEach { i ->
                        Text(row.getOrElse(i) { "" }, fontSize = 13.sp, fontWeight = if (i == 0) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = (if (wide) Modifier.width(120.dp) else Modifier.weight(1f)).padding(8.dp))
                    }
                }
                if (r < rows.lastIndex) HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.5f))
            }
        }
    }
    if (wide) Box(Modifier.horizontalScroll(rememberScrollState())) { content() } else content()
}

// ===================================================================== Hisn al-Muslim

@Composable
fun HisnScreen(onBack: () -> Unit, open: (String) -> Unit, start: Int? = null) {
    val ctx = LocalContext.current
    val hisn = remember { runCatching { Deen.hisn(ctx) }.getOrNull() }
    var chapter by rememberSaveable { mutableStateOf(start) }
    var group by rememberSaveable { mutableStateOf<Int?>(null) } // -1 = favourites
    var q by rememberSaveable { mutableStateOf("") }
    var fav by remember { mutableStateOf(Deen.hisnFav) }
    if (hisn == null) {
        ScreenScaffold("حصن المسلم", onBack = onBack) { pad -> EmptyState(Icons.Default.ErrorOutline, "المحتوى ده مش موجود في النسخة دي", Modifier.padding(pad)) }
        return
    }
    val ch = hisn.chapters.firstOrNull { it.i == chapter }
    if (ch != null) {
        HisnChapterView(hisn, ch, fav, onFav = { k -> fav = if (k in fav) fav - k else fav + k; Deen.hisnFav = fav }, onChapter = { chapter = it }, onBack = { if (start != null) onBack() else chapter = null })
        return
    }
    val list = remember(q, group, fav) {
        val p = Quran.plain(q.trim())
        hisn.chapters.filter { c ->
            (p.length < 2 || p in c.plain) && when (group) { null -> true; -1 -> "${c.i}" in fav; else -> c.group == group }
        }
    }
    ScreenScaffold(hisn.title, onBack = onBack) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                GoldCard {
                    Text(hisn.title, fontFamily = Amiri, fontWeight = FontWeight.Bold, fontSize = 24.sp, color = MaterialTheme.colorScheme.primary)
                    Text(hisn.author, style = MaterialTheme.typography.bodyMedium)
                    Text(hisn.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(top = 4.dp))
                    hisn.chapters.firstOrNull { it.i == Deen.hisnLast }?.let { c ->
                        Spacer(Modifier.height(8.dp))
                        FilledTonalButton(onClick = { chapter = c.i }) { Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text("كمّل: ${c.title}", maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                }
            }
            item {
                OutlinedTextField(
                    q, { q = it }, Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("دوّر في الأذكار…") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = { if (q.isNotEmpty()) IconButton(onClick = { q = "" }) { Icon(Icons.Default.Close, "مسح") } },
                    shape = RoundedCornerShape(14.dp),
                )
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item { FilterChip(group == null, { group = null }, label = { Text("الكل") }) }
                    item { FilterChip(group == -1, { group = if (group == -1) null else -1 }, label = { Text("⭐ المحفوظة") }) }
                    itemsIndexed(hisn.groups) { i, g -> FilterChip(group == i, { group = if (group == i) null else i }, label = { Text("${g.icon} ${g.title}") }) }
                }
            }
            if (list.isEmpty()) item { EmptyState(if (group == -1) Icons.Default.StarBorder else Icons.Default.SearchOff, if (group == -1) "دوس على النجمة في أي باب علشان يتحفظ هنا" else "مفيش نتائج") }
            items(list, key = { it.i }) { c ->
                AppCard(onClick = { chapter = c.i }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(34.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                            Text("${c.i}", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            androidx.compose.material3.Text(c.title, fontFamily = Amiri, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                            Text("${c.items.size} ذكر" + (hisn.groups.getOrNull(c.group)?.let { " • ${it.title}" } ?: ""), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }
                        if ("${c.i}" in fav) Icon(Icons.Default.Star, null, tint = Gold, modifier = Modifier.size(18.dp))
                        if (c.audio.isNotBlank()) Icon(Icons.Default.Headphones, null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(18.dp))
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun HisnChapterView(hisn: Hisn, ch: HisnChapter, fav: Set<String>, onFav: (String) -> Unit, onChapter: (Int) -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(ch.i) { Deen.hisnLast = ch.i }
    var ctrl by remember { mutableStateOf<MediaController?>(null) }
    DisposableEffect(Unit) {
        val f = Player.connect(ctx) { ctrl = it }
        onDispose { ctrl = null; MediaController.releaseFuture(f) }
    }
    val mediaId = "deen#hisn#${ch.i}"
    var playing by remember { mutableStateOf(false) }
    var mine by remember { mutableStateOf(false) }
    LaunchedEffect(ctrl, ch.i) {
        val c = ctrl ?: return@LaunchedEffect
        while (true) {
            mine = c.currentMediaItem?.mediaId == mediaId
            playing = mine && c.isPlaying
            delay(600)
        }
    }
    val counts = remember(ch.i) { mutableStateListOf(*ch.items.map { it.n }.toTypedArray()) }
    val idx = hisn.chapters.indexOf(ch)
    val rs = rememberReadStyle()
    ReadingTheme {
        ScreenScaffold(
            ch.title, onBack = onBack,
            actions = {
                IconButton(onClick = { onFav("${ch.i}") }) { Icon(if ("${ch.i}" in fav) Icons.Default.Star else Icons.Default.StarBorder, "حفظ", tint = if ("${ch.i}" in fav) Gold else LocalContentColor.current) }
                ReadingSettingsButton()
            },
        ) { pad ->
            LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (ch.audio.isNotBlank()) item {
                    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            FilledIconButton(onClick = {
                                val c = ctrl ?: return@FilledIconButton
                                when {
                                    playing -> c.pause()
                                    mine -> c.play()
                                    else -> Player.loadSingle(c, mediaId, ch.audio, ch.title, hisn.title)
                                }
                            }) { Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, "استمع") }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(if (playing) "شغّال دلوقتي" else "استمع للباب ده", fontWeight = FontWeight.SemiBold)
                                Text("تسجيل صوتي من طريق الإسلام — محتاج إنترنت", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                            }
                            if (mine) IconButton(onClick = { ctrl?.stop(); ctrl?.clearMediaItems() }) { Icon(Icons.Default.Stop, "إيقاف") }
                        }
                    }
                }
                itemsIndexed(ch.items) { i, z ->
                    val left = counts.getOrElse(i) { z.n }
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (left == 0) Positive.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth().clickable {
                            if (left > 0) { counts[i] = left - 1; haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
                        },
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            androidx.compose.material3.Text(z.x, fontFamily = Amiri, fontSize = rs.size(20f), lineHeight = rs.lineH(20f), textAlign = TextAlign.Justify)
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                                Text(z.ref, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, modifier = Modifier.weight(1f))
                                IconButton(onClick = { shareText(ctx, "${z.x}\n[${z.ref}]") }, modifier = Modifier.size(30.dp)) { Icon(Icons.Default.Share, "شارك", Modifier.size(16.dp)) }
                                Surface(shape = CircleShape, color = if (left == 0) Positive else MaterialTheme.colorScheme.primary, contentColor = Color.White) {
                                    Text(if (left == 0) "✓" else if (z.n > 1) "$left" else "١", fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                                }
                            }
                        }
                    }
                }
                item {
                    if (counts.any { it == 0 }) TextButton(onClick = { ch.items.forEachIndexed { i, z -> counts[i] = z.n } }) { Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(6.dp)); Text("صفّر العدادات") }
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        hisn.chapters.getOrNull(idx - 1)?.let { p -> OutlinedButton(onClick = { onChapter(p.i) }) { Text("‹ الباب اللي قبله") } } ?: Spacer(Modifier)
                        hisn.chapters.getOrNull(idx + 1)?.let { n -> Button(onClick = { onChapter(n.i) }) { Text("الباب اللي بعده ›") } }
                    }
                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }
}

// ===================================================================== tools

@Composable
fun DeenToolScreen(tool: String, onBack: () -> Unit) {
    val title = toolNames[tool]?.first ?: ""
    when (tool) {
        "tawaf" -> RoundCounter("tawaf", title, onBack, tawafTips)
        "sai" -> RoundCounter("sai", title, onBack, saiTips)
        "rami" -> RamiTool(onBack)
        "hajjplan", "checklist" -> ChecklistTool(tool, title, onBack)
        else -> ScreenScaffold(title, onBack = onBack) { pad -> EmptyState(Icons.Default.ErrorOutline, "الأداة دي مش موجودة", Modifier.padding(pad)) }
    }
}

/** What to remember at each round (index 0 = before starting, 1..7 = during that round, 8 = after finishing). */
private val tawafTips = listOf(
    "قبل ما تبدأ: الرجل يضطبع (يكشف كتفه اليمين) في طواف القدوم والعمرة. ابدأ من محاذاة الحجر الأسود: استلمه وقبّله إن تيسّر، أو أشر إليه بيدك وقل «الله أكبر».",
    "الشوط ١: الكعبة على يسارك. الرجل يرمُل (يسرع مع تقارب الخطى) في الأشواط الثلاثة الأولى من طواف القدوم والعمرة. ادعُ واذكر الله بما تيسّر.",
    "الشوط ٢: الرمل للرجل لسه مستمر. عند الركن اليماني استلمه بيدك إن تيسّر بدون تقبيل ولا إشارة.",
    "الشوط ٣: آخر شوط فيه رمل. بين الركن اليماني والحجر الأسود: «ربنا آتنا في الدنيا حسنة وفي الآخرة حسنة وقنا عذاب النار».",
    "الشوط ٤: امشِ عادي من هنا لآخر الطواف. كبّر كل ما تحاذي الحجر الأسود.",
    "الشوط ٥: أكثر من الدعاء بما تحب، مفيش دعاء مخصوص لكل شوط.",
    "الشوط ٦: لو شكيت في العدد ابنِ على الأقل وكمّل.",
    "الشوط ٧: الأخير! وبعده تغطي كتفك.",
    "خلصت الطواف 🤍 صلِّ ركعتين خلف مقام إبراهيم إن تيسّر (أو في أي مكان في الحرم): بعد الفاتحة «قل يا أيها الكافرون» في الأولى و«قل هو الله أحد» في الثانية، ثم اشرب من زمزم، ثم اتجه للسعي.",
)

private val saiTips = listOf(
    "قبل ما تبدأ: لما تقرب من الصفا اقرأ «إن الصفا والمروة من شعائر الله» وقل «أبدأ بما بدأ الله به». اطلع الصفا واستقبل الكعبة، ووحّد الله وكبّره: «لا إله إلا الله وحده لا شريك له، له الملك وله الحمد وهو على كل شيء قدير، لا إله إلا الله وحده، أنجز وعده، ونصر عبده، وهزم الأحزاب وحده» ٣ مرات وادعُ بينها.",
    "الشوط ١: من الصفا ← المروة. بين العلمين الأخضرين الرجل يسرع. فوق المروة استقبل القبلة وقل الذكر نفسه وادعُ.",
    "الشوط ٢: من المروة ← الصفا. أسرع بين العلمين الأخضرين.",
    "الشوط ٣: من الصفا ← المروة.",
    "الشوط ٤: من المروة ← الصفا.",
    "الشوط ٥: من الصفا ← المروة.",
    "الشوط ٦: من المروة ← الصفا.",
    "الشوط ٧: من الصفا ← المروة، وهو الأخير.",
    "خلصت السعي عند المروة 🤍 في العمرة: احلق أو قصّر وبكده تحللت. ومفيش ركعتين بعد السعي.",
)

@Composable
private fun RoundCounter(key: String, title: String, onBack: () -> Unit, tips: List<String>) {
    val haptic = LocalHapticFeedback.current
    var n by remember { mutableIntStateOf(Deen.count(key).coerceIn(0, 7)) }
    fun set(v: Int) { n = v.coerceIn(0, 7); Deen.setCount(key, n) }
    var confirmReset by remember { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme
    ScreenScaffold(title, onBack = onBack, actions = { IconButton(onClick = { confirmReset = true }) { Icon(Icons.Default.RestartAlt, "من الأول") } }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            // progress dots
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (1..7).forEach { i ->
                    Box(
                        Modifier.size(30.dp).clip(CircleShape).background(if (i <= n) cs.primary else cs.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) { Text("$i", color = if (i <= n) cs.onPrimary else cs.onSurfaceVariant, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                }
            }
            Spacer(Modifier.height(16.dp))
            val current = if (n >= 7) 8 else n + 1
            if (key == "sai" && n < 7) Text(if (current % 2 == 1) "رايح: الصفا ← المروة" else "راجع: المروة ← الصفا", fontWeight = FontWeight.Bold, color = Gold, fontSize = 18.sp)
            // the big button
            Box(
                Modifier.padding(vertical = 14.dp).size(210.dp).clip(CircleShape)
                    .background(if (n >= 7) Positive else cs.primary)
                    .clickable(enabled = n < 7) { set(n + 1); haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (n >= 7) {
                        Text("تمّ", color = Color.White, fontFamily = Amiri, fontWeight = FontWeight.Bold, fontSize = 40.sp)
                        Text("٧ أشواط", color = Color.White)
                    } else {
                        Text("الشوط", color = Color.White.copy(alpha = 0.85f))
                        Text("$current", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 64.sp)
                        Text("دوس لما تخلّصه", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
                    }
                }
            }
            if (n in 1..6) TextButton(onClick = { set(n - 1) }) { Icon(Icons.Default.Undo, null); Spacer(Modifier.width(6.dp)); Text("رجّع شوط") }
            GoldCard {
                Text(if (n == 0) "قبل ما تبدأ" else if (n >= 7) "بعد ما خلصت" else "في الشوط ${current}", fontWeight = FontWeight.Bold, color = cs.primary)
                Spacer(Modifier.height(4.dp))
                Text(tips.getOrElse(if (n == 0) 0 else current) { "" }, style = MaterialTheme.typography.bodyLarge)
            }
            Spacer(Modifier.height(12.dp))
            DeenDiagram(key)
            Spacer(Modifier.height(24.dp))
        }
    }
    if (confirmReset) ConfirmDialog("تبدأ من الأول؟", "العداد هيرجع صفر.", "ابدأ من الأول", { confirmReset = false }) { set(0); confirmReset = false }
}

@Composable
private fun RamiTool(onBack: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val cs = MaterialTheme.colorScheme
    val days = listOf("10" to "يوم ١٠", "11" to "يوم ١١", "12" to "يوم ١٢", "13" to "يوم ١٣")
    var day by rememberSaveable { mutableStateOf("10") }
    val jamarat = if (day == "10") listOf(2) else listOf(0, 1, 2)
    val names = listOf("الجمرة الصغرى", "الجمرة الوسطى", "جمرة العقبة")
    val after = listOf(
        "بعد ما تخلّص: تقدّم شوية لمكان سهل بعيد عن الزحام، واستقبل القبلة، وارفع يديك وادعُ طويلاً.",
        "بعد ما تخلّص: خذ ذات الشمال، واستقبل القبلة، وارفع يديك وادعُ طويلاً.",
        "بعدها ما تقفش للدعاء. ",
    )
    val counts = remember(day) { mutableStateListOf(*jamarat.map { Deen.count("rami_${day}_$it") }.toTypedArray()) }
    ScreenScaffold("رمي الجمرات", onBack = onBack) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    days.forEach { (k, t) -> FilterChip(day == k, { day = k }, label = { Text(t) }) }
                }
            }
            item {
                Text(
                    if (day == "10") "يوم النحر: ترمي جمرة العقبة بس بـ ٧ حصيات، والسنة بعد طلوع الشمس، وتقطع التلبية مع أول حصاة."
                    else "أيام التشريق: الرمي بعد الزوال (الظهر)، الجمرات الثلاث بالترتيب، ٧ حصيات لكل واحدة، تكبّر مع كل حصاة." +
                        if (day == "12") " ومن تعجّل يخرج من منى قبل غروب الشمس." else if (day == "13") " ده لمن تأخّر." else "",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            itemsIndexed(jamarat) { pos, j ->
                val c = counts.getOrElse(pos) { 0 }
                val done = c >= 7
                val prevDone = pos == 0 || counts.getOrElse(pos - 1) { 0 } >= 7
                GoldCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${pos + 1}. ${names[j]}", fontFamily = Amiri, fontWeight = FontWeight.Bold, fontSize = 19.sp, color = if (done) Positive else cs.primary, modifier = Modifier.weight(1f))
                        if (c > 0) IconButton(onClick = { counts[pos] = 0; Deen.setCount("rami_${day}_$j", 0) }) { Icon(Icons.Default.RestartAlt, "صفّر") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                        (1..7).forEach { i -> Box(Modifier.size(22.dp).clip(CircleShape).background(if (i <= c) Color(0xFF8A7E6A) else cs.surfaceVariant)) }
                    }
                    if (!done) Button(
                        onClick = {
                            counts[pos] = c + 1; Deen.setCount("rami_${day}_$j", c + 1)
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        enabled = prevDone, modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (prevDone) "رميت حصاة — الله أكبر (${c + 1} من ٧)" else "كمّل اللي قبلها الأول") }
                    else Text(after[j], color = if (j < 2) cs.primary else cs.outline, fontWeight = if (j < 2) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
            item { DeenDiagram("jamarat") }
            item {
                Text(
                    "الحصاة صغيرة فوق حبة الحمص شوية، ولازم تقع في الحوض. لو شكيت إنها وقعت كمّل بغيرها. والعاجز يوكّل غيره يرمي عنه بعد ما يرمي الوكيل عن نفسه.",
                    style = MaterialTheme.typography.bodySmall, color = cs.outline,
                )
            }
        }
    }
}

@Composable
private fun ChecklistTool(key: String, title: String, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val groups = remember(key) { runCatching { Deen.checklist(key, ctx) }.getOrDefault(emptyList()) }
    var checked by remember { mutableStateOf(Deen.checked(key)) }
    fun toggle(k: String) { checked = if (k in checked) checked - k else checked + k; Deen.setChecked(key, checked) }
    val total = groups.sumOf { it.items.size }
    var confirmReset by remember { mutableStateOf(false) }
    ScreenScaffold(title, onBack = onBack, actions = { IconButton(onClick = { confirmReset = true }) { Icon(Icons.Default.RestartAlt, "من الأول") } }) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                val done = checked.size.coerceAtMost(total)
                GoldCard {
                    Text("$done من $total", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(progress = { if (total == 0) 0f else done / total.toFloat() }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape))
                }
            }
            if (key == "hajjplan") item { DeenDiagram("hajj_days") }
            groups.forEachIndexed { gi, g ->
                item { SectionTitle(g.title) }
                item {
                    AppCard {
                        g.items.forEachIndexed { ii, s ->
                            val k = "$gi:$ii"
                            Row(Modifier.fillMaxWidth().clickable { toggle(k) }, verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(k in checked, { toggle(k) })
                                Text(s, color = if (k in checked) MaterialTheme.colorScheme.outline else Color.Unspecified, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
    if (confirmReset) ConfirmDialog("تبدأ من الأول؟", "كل العلامات هتتشال.", "امسح العلامات", { confirmReset = false }) { checked = emptySet(); Deen.setChecked(key, checked); confirmReset = false }
}
