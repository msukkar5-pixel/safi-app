package com.mohamed.safi.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import com.mohamed.safi.ui.Text
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mohamed.safi.audio.NowPlaying
import com.mohamed.safi.audio.Player
import com.mohamed.safi.faith.Quran
import com.mohamed.safi.faith.Radio
import com.mohamed.safi.faith.Station
import com.mohamed.safi.ui.*
import kotlinx.coroutines.launch

private const val FAV = "⭐ المفضلة"

/** إذاعات القرآن الكريم: الرسمية + كل إذاعات mp3quran (قرّاء، تفسير، رقية، أذكار). */
@Composable
fun RadioScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var stations by remember { mutableStateOf(Radio.bundled(ctx)) }
    var loading by remember { mutableStateOf(true) }
    var q by remember { mutableStateOf("") }
    var group by remember { mutableStateOf<String?>(null) }
    var favs by remember { mutableStateOf(Radio.favorites) }
    val now by NowPlaying.state.collectAsState()
    fun reload(force: Boolean) {
        loading = true
        scope.launch { stations = runCatching { Radio.all(ctx, force) }.getOrDefault(stations); loading = false }
    }
    LaunchedEffect(Unit) { reload(false) }
    val groups = remember(stations) { stations.map { it.group }.distinct() }
    val list = remember(stations, q, group, favs) {
        val p = Quran.plain(q.trim())
        stations.filter { s ->
            (p.isEmpty() || p in Quran.plain(s.name)) && when (group) { null -> true; FAV -> s.id in favs; else -> s.group == group }
        }
    }
    fun play(s: Station) {
        val c = NowPlaying.controller ?: run { toast(ctx, "المشغّل لسه بيجهز، جرّب تاني"); return }
        Radio.last = s.id
        Player.loadSingle(c, "radio#${s.id}", s.url, s.name, "إذاعات القرآن")
    }

    ScreenScaffold("إذاعات القرآن", onBack = onBack, actions = {
        IconButton(onClick = { reload(true) }) { Icon(Icons.Default.Refresh, "تحديث") }
    }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            // the station playing now
            now?.takeIf { it.mediaId.startsWith("radio#") }?.let { st ->
                Surface(color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        when {
                            st.buffering -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                            st.failed -> Icon(Icons.Default.ErrorOutline, null, tint = Danger)
                            else -> Icon(Icons.Default.Radio, null, tint = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            androidx.compose.material3.Text(st.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                when { st.failed -> "المحطة مش متاحة دلوقتي، جرّب تاني أو اختار غيرها"; st.buffering -> "بيتصل…"; st.playing -> "بث مباشر"; else -> "متوقف" },
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        FilledIconButton(onClick = { NowPlaying.toggle() }) { Icon(if (st.playing) Icons.Default.Pause else Icons.Default.PlayArrow, "تشغيل") }
                        IconButton(onClick = { NowPlaying.stop() }) { Icon(Icons.Default.Stop, "إيقاف") }
                    }
                }
            }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    OutlinedTextField(
                        q, { q = it }, Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("دوّر باسم القارئ أو الإذاعة…") },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        trailingIcon = { if (q.isNotEmpty()) IconButton(onClick = { q = "" }) { Icon(Icons.Default.Close, "مسح") } },
                        shape = RoundedCornerShape(14.dp),
                    )
                }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        item { FilterChip(group == null, { group = null }, label = { Text("الكل (${stations.size})") }) }
                        item { FilterChip(group == FAV, { group = if (group == FAV) null else FAV }, label = { Text(FAV) }) }
                        items(groups) { g -> FilterChip(group == g, { group = if (group == g) null else g }, label = { Text(g) }) }
                    }
                }
                if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                if (!loading && list.isEmpty()) item {
                    EmptyState(Icons.Default.Radio, if (group == FAV) "دوس على النجمة جنب أي إذاعة علشان تتحفظ هنا" else "مفيش إذاعات بالاسم ده")
                }
                items(list, key = { it.id }) { s ->
                    val on = now?.mediaId == "radio#${s.id}"
                    AppCard(onClick = { if (on) NowPlaying.toggle() else play(s) }, color = if (on) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (on && now?.playing == true) Icons.Default.GraphicEq else Icons.Default.Radio, null, tint = if (s.group == "رسمية") Gold else MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                androidx.compose.material3.Text(s.name, fontWeight = FontWeight.SemiBold)
                                Text(s.group, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                            }
                            IconButton(onClick = { favs = if (s.id in favs) favs - s.id else favs + s.id; Radio.favorites = favs }) {
                                Icon(if (s.id in favs) Icons.Default.Star else Icons.Default.StarBorder, "مفضلة", tint = if (s.id in favs) Gold else MaterialTheme.colorScheme.outline)
                            }
                        }
                    }
                }
                item {
                    Text(
                        "الإذاعات الرسمية بتتراجع روابطها تلقائياً مع كل نسخة، وباقي الإذاعات من mp3quran.net. البث محتاج إنترنت.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline,
                    )
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}
