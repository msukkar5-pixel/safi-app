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
private data class Channel(val id: String, val name: String, val urls: List<String>, val yt: String, val desc: String = "")

/** Free Sunni Quran & Sunnah TV channels (assets/media/tv.json, streams checked in CI by tools/check_streams.py). */
/** [kids]: the kids' channels (assets/media/kidstv.json), without YouTube links, and the parent can hide any channel. */
@Composable
fun TvScreen(onBack: () -> Unit, kids: Boolean = false) {
    val ctx = LocalContext.current
    @Suppress("UNUSED_VARIABLE") val kv = com.mohamed.safi.kids.Kids.version.intValue
    var parent by remember { mutableStateOf(false) }
    var askPin by remember { mutableStateOf(false) }
    val all = remember {
        runCatching {
            val a = JSONArray(ctx.assets.open(if (kids) "media/kidstv.json" else "media/tv.json").bufferedReader().use { it.readText() })
            (0 until a.length()).mapNotNull { i ->
                val o = a.getJSONObject(i)
                val list = o.optJSONArray("urls")?.let { u -> (0 until u.length()).map { u.getString(it) } } ?: listOfNotNull(o.optString("url").ifBlank { null })
                if (list.isEmpty()) null else Channel(o.getString("id"), o.getString("name"), list, if (kids) "" else o.optString("yt"), o.optString("desc"))
            }
        }.getOrDefault(emptyList())
    }
    val channels = if (kids) all.filter { it.id !in com.mohamed.safi.kids.Kids.hiddenChannels } else all
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

    ScreenScaffold(if (kids) "قنوات الأطفال" else "قنوات القرآن والسنة", onBack = onBack, actions = {
        if (kids) IconButton(onClick = { askPin = true }) { Icon(Icons.Default.Lock, "ركن الوالدين") }
    }) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            @Suppress("UNUSED_VARIABLE") val live = com.mohamed.safi.kids.Kids.version.intValue
            if (kids) item {
                AppCard {
                    Text("قنوات عربية للأطفال من جهات معروفة بمحتوى عائلي. مفيش قنوات أجنبية مترجمة ولا قنوات مشهورة بأفكار مخالفة.", style = MaterialTheme.typography.bodySmall)
                    Text("البث المباشر بتتحكم فيه القناة نفسها، فمش هنقدر نضمن كل حلقة. لو مش مرتاح لقناة، اخفيها من القفل فوق.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
            }
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
                        Column(Modifier.weight(1f)) {
                            androidx.compose.material3.Text(c.name, fontWeight = FontWeight.SemiBold)
                            if (c.desc.isNotBlank()) Text(c.desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }
                        Icon(Icons.Default.PlayCircle, "شاهد", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            if (!kids) item { SectionTitle("قنوات أخرى على يوتيوب") }
            if (!kids) items(youtubeChannels) { (name, query) ->
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
    if (askPin) KidsParentPin({ askPin = false }) { askPin = false; parent = true }
    if (parent) AlertDialog(
        onDismissRequest = { parent = false },
        title = { Text("القنوات اللي تظهر للأطفال") },
        text = {
            Column {
                all.forEach { c ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(c.id !in com.mohamed.safi.kids.Kids.hiddenChannels, { show ->
                            val h = com.mohamed.safi.kids.Kids.hiddenChannels
                            com.mohamed.safi.kids.Kids.hiddenChannels = if (show) h - c.id else h + c.id
                            if (!show && current?.id == c.id) { player.stop(); current = null }
                        })
                        androidx.compose.material3.Text(c.name)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { parent = false }) { Text("تمام") } },
    )
}

/** Live channels that broadcast on YouTube; opened as a search so the link never goes stale. */
private val youtubeChannels = listOf(
    "بث مباشر من المسجد الحرام" to "بث مباشر المسجد الحرام مكة الآن",
    "بث مباشر من المسجد النبوي" to "بث مباشر المسجد النبوي الآن",
    "قناة مصر قرآن كريم" to "قناة مصر قرآن كريم بث مباشر",
    "إذاعة القرآن الكريم من القاهرة" to "إذاعة القرآن الكريم من القاهرة بث مباشر",
)

/** The parent's PIN: kid mode's PIN on a child's phone, otherwise the kids' city PIN (none set = open). */
@Composable
private fun KidsParentPin(onDismiss: () -> Unit, onOk: () -> Unit) {
    val km = com.mohamed.safi.kids.KidMode
    val k = com.mohamed.safi.kids.Kids
    if (!km.on && !k.hasPin) { LaunchedEffect(Unit) { onOk() }; return }
    var pin by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("لولي الأمر بس") },
        text = {
            Column {
                OutlinedTextField(pin, { pin = it.filter(Char::isDigit).take(8); wrong = false }, label = { Text("الرقم السري") }, singleLine = true,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword))
                if (wrong) Text("الرقم غلط", color = Danger)
            }
        },
        confirmButton = { Button(onClick = { if (if (km.on) km.checkPin(pin) else k.checkPin(pin)) onOk() else wrong = true }) { Text("دخول") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}
