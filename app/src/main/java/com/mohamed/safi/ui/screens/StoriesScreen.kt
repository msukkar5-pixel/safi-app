package com.mohamed.safi.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
    if (it0 != null) { StoryDetail(it0, sources, open) { item = null }; return }

    ScreenScaffold("القصص والسيرة", onBack = onBack) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            TabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
                Tab(tab == 0, { tab = 0 }, text = { Text("قصص الأنبياء") })
                Tab(tab == 1, { tab = 1 }, text = { Text("السيرة النبوية") })
                Tab(tab == 2, { tab = 2 }, text = { Text("الصحابة") })
            }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Text(sources, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
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
private fun StoryDetail(s: StoryItem, sources: String, open: (String) -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var surahNames by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
    LaunchedEffect(Unit) { surahNames = runCatching { Quran.surahs(ctx).associate { it.number to it.name } }.getOrDefault(emptyMap()) }
    ScreenScaffold(s.title, onBack = onBack) { pad ->
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
            if (s.webQuery.isNotBlank()) {
                OutlinedButton(onClick = {
                    Shaarawy.openUrl(ctx, "https://www.google.com/search?q=" + android.net.Uri.encode(s.webQuery + " site:islamweb.net OR site:dorar.net OR site:shamela.ws"))
                }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Public, null); Spacer(Modifier.width(6.dp)); Text("اقرأ بالتفصيل (إسلام ويب، الدرر السنية، الشاملة)")
                }
            }
            Text(sources, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        }
    }
}
