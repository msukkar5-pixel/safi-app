package com.mohamed.safi.audio

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** What the shared player is doing, for the mini player shown on every screen. */
data class NowState(val mediaId: String, val title: String, val artist: String, val playing: Boolean, val buffering: Boolean, val failed: Boolean = false) {
    /** Screen that owns this item, opened when the mini player is tapped. */
    val route: String
        get() = when {
            mediaId.startsWith("quran#") -> "quranaudio"
            mediaId.startsWith("radio#") -> "radio"
            mediaId.startsWith("deen#hisn#") -> "hisn"
            mediaId.startsWith("deen#sleep#") -> "sleep"
            mediaId.startsWith("deen#") -> "sleep"
            else -> "audiobooks"
        }
}

/**
 * One app-wide controller of [PlaybackService]. Screens keep their own controllers for their
 * detailed UI; this one only mirrors the state and offers play / pause / stop everywhere.
 */
object NowPlaying {
    private val _state = MutableStateFlow<NowState?>(null)
    val state: StateFlow<NowState?> = _state
    private var future: ListenableFuture<MediaController>? = null
    var controller: MediaController? = null
        private set

    private val listener = object : androidx.media3.common.Player.Listener {
        override fun onEvents(player: androidx.media3.common.Player, events: androidx.media3.common.Player.Events) { refresh() }
    }

    fun connect(ctx: Context) {
        if (future != null) return
        future = Player.connect(ctx.applicationContext) { c ->
            controller = c
            c.addListener(listener)
            refresh()
        }
    }

    fun release() {
        controller?.removeListener(listener)
        controller = null
        future?.let { MediaController.releaseFuture(it) }
        future = null
        _state.value = null
    }

    private fun refresh() {
        val c = controller ?: return
        val item: MediaItem? = c.currentMediaItem
        val failed = c.playerError != null
        val active = item != null && (failed || (c.playbackState != androidx.media3.common.Player.STATE_IDLE && c.playbackState != androidx.media3.common.Player.STATE_ENDED))
        _state.value = if (!active || item == null) null else {
            val md: MediaMetadata = item.mediaMetadata
            NowState(
                item.mediaId, md.title?.toString() ?: "", (md.artist ?: md.albumTitle)?.toString() ?: "",
                c.isPlaying, c.playbackState == androidx.media3.common.Player.STATE_BUFFERING, failed,
            )
        }
    }

    fun toggle() {
        val c = controller ?: return
        when {
            c.isPlaying -> c.pause()
            c.playerError != null || c.playbackState == androidx.media3.common.Player.STATE_IDLE -> { c.prepare(); c.play() }
            else -> c.play()
        }
    }

    fun stop() {
        val c = controller ?: return
        c.stop()
        c.clearMediaItems()
        Library.sleepAt = 0L
        refresh()
    }
}
