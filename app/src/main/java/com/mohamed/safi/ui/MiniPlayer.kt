package com.mohamed.safi.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mohamed.safi.audio.NowPlaying

/** Slim bar shown above the bottom navigation whenever anything is playing (book, Quran, dua, radio). */
@Composable
fun MiniPlayer(currentRoute: String?, open: (String) -> Unit) {
    val s by NowPlaying.state.collectAsState()
    val st = s
    // the full player screen of that item already shows its own controls
    val hidden = st == null || currentRoute == st.route
    AnimatedVisibility(!hidden, enter = expandVertically(), exit = shrinkVertically()) {
        if (st == null) return@AnimatedVisibility
        Surface(color = MaterialTheme.colorScheme.secondaryContainer, tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
            Row(
                Modifier.clickable { open(st.route) }.padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (st.buffering) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else Icon(Icons.Default.GraphicEq, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    androidx.compose.material3.Text(st.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (st.artist.isNotBlank()) androidx.compose.material3.Text(
                        st.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = { NowPlaying.toggle() }) {
                    Icon(if (st.playing) Icons.Default.Pause else Icons.Default.PlayArrow, if (st.playing) "إيقاف مؤقت" else "تشغيل")
                }
                IconButton(onClick = { NowPlaying.stop() }) { Icon(Icons.Default.Close, "إيقاف") }
            }
        }
    }
}
