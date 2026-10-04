package com.mohamed.safi.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.mohamed.safi.faith.Quran
import com.mohamed.safi.faith.Shaarawy
import com.mohamed.safi.faith.Surah
import com.mohamed.safi.ui.*

@Composable
fun ShaarawyScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }
    var surahs by remember { mutableStateOf<List<Surah>>(emptyList()) }
    var saved by remember { mutableStateOf(Shaarawy.saved) }
    var adding by remember { mutableStateOf(false) }
    var q by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { surahs = runCatching { Quran.surahs(ctx) }.getOrDefault(emptyList()) }

    ScreenScaffold(
        "الشيخ الشعراوي", onBack = onBack,
        fab = { if (tab == 2) ExtendedFloatingActionButton(onClick = { adding = true }, icon = { Icon(Icons.Default.Add, null) }, text = { Text("لينك") }) },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            TabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
                Tab(tab == 0, { tab = 0 }, text = { Text("خواطر بالسورة") })
                Tab(tab == 1, { tab = 1 }, text = { Text("مواضيع") })
                Tab(tab == 2, { tab = 2 }, text = { Text("المحفوظة") })
            }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Text(
                        "كل زرار بيفتح يوتيوب على فيديوهات الشيخ للسورة أو الموضوع. ولو لقيت حلقة عجبتك، احفظ لينكها في \"المحفوظة\".",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline,
                    )
                }
                when (tab) {
                    0 -> {
                        item {
                            OutlinedTextField(q, { q = it }, placeholder = { Text("اسم السورة") }, singleLine = true, leadingIcon = { Icon(Icons.Default.Search, null) }, modifier = Modifier.fillMaxWidth())
                        }
                        val list = if (q.isBlank()) surahs else surahs.filter { Quran.plain(it.name).contains(Quran.plain(q)) }
                        items(list, key = { it.number }) { s ->
                            AppCard(onClick = { Shaarawy.open(ctx, Shaarawy.surahSearch(s.name)) }) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("${s.number}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(36.dp))
                                    Text(s.name, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                                    Icon(Icons.Default.PlayCircle, null, tint = Danger)
                                }
                            }
                        }
                    }
                    1 -> items(Shaarawy.topics) { t ->
                        AppCard(onClick = { Shaarawy.open(ctx, Shaarawy.topicSearch(t)) }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(t, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                                Icon(Icons.Default.PlayCircle, null, tint = Danger)
                            }
                        }
                    }
                    else -> {
                        if (saved.isEmpty()) item { EmptyState(Icons.Default.VideoLibrary, "من يوتيوب: مشاركة ← نسخ الرابط، وبعدين ضيفه هنا") }
                        items(saved) { l ->
                            AppCard(onClick = { Shaarawy.openUrl(ctx, l.url) }) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.PlayCircle, null, tint = Danger)
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(l.title, fontWeight = FontWeight.SemiBold)
                                        Text(l.url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, maxLines = 1)
                                    }
                                    IconButton(onClick = { saved = saved - l; Shaarawy.saved = saved }) { Icon(Icons.Default.Delete, "امسح") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (adding) {
        var title by remember { mutableStateOf("") }
        var url by remember {
            mutableStateOf(
                (ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).primaryClip
                    ?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(ctx)?.toString()?.takeIf { it.startsWith("http") } ?: "",
            )
        }
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text("احفظ حلقة") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(title, { title = it }, label = { Text("العنوان") }, singleLine = true)
                    OutlinedTextField(url, { url = it.trim() }, label = { Text("اللينك") }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (!url.startsWith("http")) toast(ctx, "الصق لينك صحيح") else {
                        saved = saved + Shaarawy.Link(title.ifBlank { "حلقة للشيخ الشعراوي" }, url)
                        Shaarawy.saved = saved
                        adding = false
                    }
                }) { Text("حفظ") }
            },
            dismissButton = { TextButton(onClick = { adding = false }) { Text("إلغاء") } },
        )
    }
}
