package com.mohamed.safi.ui.screens

import android.net.Uri
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.mohamed.safi.audio.NowPlaying
import com.mohamed.safi.faith.Shaarawy
import com.mohamed.safi.ui.*
import org.json.JSONArray

/** [urls] are tried in order (CI keeps only the live ones, best first). */
private data class Channel(val id: String, val name: String, val urls: List<String>, val yt: String)

/** Free Sunni Quran & Sunnah TV channels (assets/media/tv.json, streams checked in CI by tools/check_streams.py). */
@Composable
fun TvScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val channels = remember {
        runCatching {
            val a = JSONArray(ctx.assets.open("media/tv.json").bufferedReader().use { it.readText() })
            (0 until a.length()).mapNotNull { i ->
                val o = a.getJSONObject(i)
                val list = o.optJSONArray("urls")?.let { u -> (0 until u.length()).map { u.getString(it) } } ?: listOfNotNull(o.optString("url").ifBlank { null })
                if (list.isEmpty()) null else Channel(o.getString("id"), o.getString("name"), list, o.optString("yt"))
            }
        }.getOrDefault(emptyList())
    }
    val player = remember { ExoPlayer.Builder(ctx).build() }
    var current by remember { mutableStateOf<Channel?>(null) }
    var urlIndex by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf(false) }
    var buffering by remember { mutableStateOf(false) }
    var full by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        val l = object : androidx.media3.common.Player.Listener {
            override fun onPlayerError(e: PlaybackException) {
                // try the channel's backup link before giving up
                val c = current
                if (c != null && urlIndex + 1 < c.urls.size) {
                    urlIndex++
                    player.setMediaItem(MediaItem.fromUri(c.urls[urlIndex])); player.prepare(); player.play()
                } else { error = true; buffering = false }
            }
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == androidx.media3.common.Player.STATE_BUFFERING
                if (state == androidx.media3.common.Player.STATE_READY) error = false
            }
        }
        player.addListener(l)
        onDispose { player.removeListener(l); player.release() }
    }
    fun watch(c: Channel) {
        NowPlaying.controller?.pause() // don't play two sounds at once
        current = c; error = false; urlIndex = 0
        player.setMediaItem(MediaItem.fromUri(c.urls.first()))
        player.prepare()
        player.play()
    }
    ImmersiveEffect(full)

    if (full && current != null) {
        androidx.activity.compose.BackHandler { full = false }
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView({ PlayerView(it).apply { this.player = player } }, Modifier.fillMaxSize(), update = { it.player = player })
            IconButton(onClick = { full = false }, modifier = Modifier.align(Alignment.TopStart).padding(8.dp)) {
                Icon(Icons.Default.FullscreenExit, "خروج من ملء الشاشة", tint = Color.White)
            }
        }
        return
    }

    ScreenScaffold("قنوات القرآن والسنة", onBack = onBack) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            current?.let { c ->
                item {
                    Column {
                        Box(Modifier.fillMaxWidth().aspectRatio(16 / 9f).background(Color.Black)) {
                            AndroidView({ PlayerView(it).apply { this.player = player } }, Modifier.fillMaxSize(), update = { it.player = player })
                            if (buffering) CircularProgressIndicator(Modifier.align(Alignment.Center))
                            if (error) Column(Modifier.align(Alignment.Center).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("البث مش متاح دلوقتي", color = Color.White, fontWeight = FontWeight.Bold)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(onClick = { watch(c) }) { Text("جرّب تاني", color = Color.White) }
                                    if (c.yt.isNotBlank()) TextButton(onClick = { Shaarawy.openUrl(ctx, "https://www.youtube.com/results?search_query=" + Uri.encode(c.yt)) }) { Text("على يوتيوب", color = Color.White) }
                                }
                            }
                            IconButton(onClick = { full = true }, modifier = Modifier.align(Alignment.TopStart)) { Icon(Icons.Default.Fullscreen, "ملء الشاشة", tint = Color.White) }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                            androidx.compose.material3.Text(c.name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            IconButton(onClick = { player.stop(); current = null }) { Icon(Icons.Default.Stop, "إيقاف") }
                        }
                    }
                }
            }
            item { SectionTitle("بث مباشر داخل التطبيق") }
            if (channels.isEmpty()) item { EmptyState(Icons.Default.LiveTv, "مفيش قنوات متاحة في النسخة دي") }
            items(channels, key = { it.id }) { c ->
                AppCard(onClick = { watch(c) }, color = if (current?.id == c.id) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LiveTv, null, tint = Gold)
                        Spacer(Modifier.width(12.dp))
                        androidx.compose.material3.Text(c.name, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Icon(Icons.Default.PlayCircle, "شاهد", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            item { SectionTitle("قنوات أخرى على يوتيوب") }
            items(youtubeChannels) { (name, query) ->
                AppCard(onClick = { Shaarawy.openUrl(ctx, "https://www.youtube.com/results?search_query=" + Uri.encode(query)) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PlayCircle, null, tint = Color(0xFFC62828))
                        Spacer(Modifier.width(12.dp))
                        Text(name, modifier = Modifier.weight(1f))
                        Icon(Icons.Default.OpenInNew, null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(18.dp))
                    }
                }
            }
            item {
                Text(
                    "روابط البث بتتراجع تلقائياً مع كل نسخة من التطبيق، واللي مش شغال بيتشال. البث محتاج إنترنت ويستهلك باقة.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline,
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/** Live channels that broadcast on YouTube; opened as a search so the link never goes stale. */
private val youtubeChannels = listOf(
    "بث مباشر من المسجد الحرام" to "بث مباشر المسجد الحرام مكة الآن",
    "بث مباشر من المسجد النبوي" to "بث مباشر المسجد النبوي الآن",
    "قناة مصر قرآن كريم" to "قناة مصر قرآن كريم بث مباشر",
    "إذاعة القرآن الكريم من القاهرة" to "إذاعة القرآن الكريم من القاهرة بث مباشر",
)
