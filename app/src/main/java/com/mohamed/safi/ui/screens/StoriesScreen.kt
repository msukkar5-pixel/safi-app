package com.mohamed.safi.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import com.mohamed.safi.ui.Text
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mohamed.safi.faith.Quran
import com.mohamed.safi.faith.Shaarawy
import com.mohamed.safi.faith.Stories
import com.mohamed.safi.faith.StoryItem
import com.mohamed.safi.ui.*

/** Prophets' stories, Seerah and Companions — Sunni sources. */
@Composable
fun StoriesScreen(onBack: () -> Unit, open: (String) -> Unit) {
    val ctx = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }
    var item by remember { mutableStateOf<StoryItem?>(null) }
    val (list, sources, videoKey) = when (tab) {
        0 -> Triple(Stories.prophets, Stories.SOURCES_PROPHETS, "prophets")
        1 -> Triple(Stories.seerah, Stories.SOURCES_SEERAH, "seerah")
        else -> Triple(Stories.sahaba, Stories.SOURCES_SAHABA, "sahaba")
    }
    val it0 = item
    if (it0 != null) { StoryDetail(it0, sources, tab, open) { item = null }; return }

    ScreenScaffold("القصص والسيرة", onBack = onBack) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            TabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
                Tab(tab == 0, { tab = 0 }, text = { Text("قصص الأنبياء") })
                Tab(tab == 1, { tab = 1 }, text = { Text("السيرة النبوية") })
                Tab(tab == 2, { tab = 2 }, text = { Text("الصحابة") })
            }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { SectionTitle("الكتب كاملة") }
                item {
                    BookList(
                        when (tab) { 0 -> listOf("prophets"); 1 -> listOf("seerah"); else -> listOf("sahaba") },
                    ) { open("book/$it") }
                }
                item { SectionTitle(if (tab == 0) "مختصر قصص الأنبياء" else if (tab == 1) "مختصر السيرة" else "مختصر سير الصحابة") }
                items(list) { s ->
                    AppCard(onClick = { item = s }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(s.title, fontWeight = FontWeight.Bold)
                                Text(s.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                            }
                            Icon(Icons.Default.ChevronLeft, null)
                        }
                    }
                }
                item {
                    OutlinedButton(onClick = { Shaarawy.open(ctx, Stories.videoQueries[videoKey] ?: "") }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.PlayCircle, null); Spacer(Modifier.width(6.dp))
                        Text(if (tab == 0) "محاضرات قصص الأنبياء (يوتيوب)" else if (tab == 1) "محاضرات السيرة النبوية (يوتيوب)" else "محاضرات سير الصحابة (يوتيوب)")
                    }
                }
            }
        }
    }
}

@Composable
private fun StoryDetail(s: StoryItem, sources: String, tab: Int, open: (String) -> Unit, onBack: () -> Unit) {
    androidx.activity.compose.BackHandler { onBack() }
    val ctx = LocalContext.current
    var surahNames by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
    LaunchedEffect(Unit) { surahNames = runCatching { Quran.surahs(ctx).associate { it.number to it.name } }.getOrDefault(emptyMap()) }
    ReadingTheme { ScreenScaffold(s.title, onBack = onBack, actions = { ReadingSettingsButton() }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(s.subtitle, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            Text(s.body, style = MaterialTheme.typography.bodyLarge, lineHeight = 30.sp)
            if (s.verses.isNotEmpty()) {
                SectionTitle("في القرآن الكريم")
                s.verses.forEach { v ->
                    OutlinedButton(onClick = {
                        UiBus.pendingQuran.value = v.surah to v.from
                        open("quran")
                    }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.MenuBook, null); Spacer(Modifier.width(6.dp))
                        Text("${surahNames[v.surah] ?: "سورة ${v.surah}"} • الآيات ${v.from}" + if (v.to > v.from) "–${v.to}" else "")
                    }
                }
            }
            if (s.hadithQuery.isNotBlank()) {
                OutlinedButton(onClick = { UiBus.pendingHadith.value = s.hadithQuery; open("hadith") }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.LibraryBooks, null); Spacer(Modifier.width(6.dp)); Text("أحاديث عن ${s.title.substringBefore(" عليه").substringBefore(" ﷺ")} في صحيح البخاري")
                }
            }
            val name = s.title.substringBefore(" عليه").substringBefore(" ﷺ").substringBefore(" رضي").substringBefore("(").trim()
            val bookId = when (tab) { 0 -> "qisas"; 1 -> "sira_hisham"; else -> "usd_ghaba" }
            val bookName = when (tab) { 0 -> "قصص الأنبياء لابن كثير"; 1 -> "السيرة النبوية لابن هشام"; else -> "أسد الغابة لابن الأثير" }
            Button(onClick = {
                UiBus.pendingBook.value = bookId to (if (tab == 1) "" else name)
                open("book/$bookId")
            }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.AutoStories, null); Spacer(Modifier.width(6.dp)); Text("اقرأها كاملة في $bookName")
            }
            Text(sources, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        }
    }
} }
