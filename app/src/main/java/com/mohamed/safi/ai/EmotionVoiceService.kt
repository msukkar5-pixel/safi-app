package com.mohamed.safi.ai

import android.Manifest
import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.IBinder
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationCompat
import com.mohamed.safi.SafiApp
import com.mohamed.safi.R
import com.mohamed.safi.faith.SituationSupport
import com.mohamed.safi.notify.Notifier
import kotlinx.coroutines.*
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Explicit opt-in, visible voice-signal helper. It keeps only short PCM frames in memory,
 * computes loudness, then discards them. It never records, transcribes, uploads, or stores audio.
 * Loudness is only a rough signal; it is not a diagnosis of emotion.
 */
class EmotionVoiceService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var recorder: AudioRecord? = null
    private var loudWindows = 0

    override fun onCreate() {
        super.onCreate()
        Notifier.createChannels(this)
        startForeground(NOTIF_ID, notification())
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            stopSelf()
            return
        }
        scope.launch { monitor() }
    }

    private fun notification(): Notification = NotificationCompat.Builder(this, Notifier.CH_EMOTION_VOICE)
        .setSmallIcon(R.drawable.ic_notify)
        .setContentTitle("مساعد النبرة الصوتية شغال")
        .setContentText("تحليل مستوى الصوت فقط — بدون حفظ أو رفع التسجيلات")
        .setOngoing(true)
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .build()

    private suspend fun monitor() = withContext(Dispatchers.IO) {
        val rate = 16_000
        val min = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (min <= 0) { stopSelf(); return@withContext }
        val size = maxOf(min, rate / 4)
        val r = runCatching {
            AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.MIC)
                .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_IN_MONO).build())
                .setBufferSizeInBytes(size * 2)
                .build()
        }.getOrNull() ?: run { stopSelf(); return@withContext }
        recorder = r
        val buffer = ShortArray(size)
        try {
            r.startRecording()
            while (isActive && SafiApp.prefs.emotionVoiceOn) {
                val n = r.read(buffer, 0, buffer.size)
                if (n > 0) {
                    val rms = sqrt(buffer.take(n).sumOf { it.toDouble() * it.toDouble() } / n) / 32768.0
                    val db = 20.0 * log10(rms.coerceAtLeast(0.00001))
                    // A sustained loud voice signal, not a single sound or word.
                    if (db > -18.0) loudWindows++ else loudWindows = maxOf(0, loudWindows - 1)
                    if (loudWindows >= 8) {
                        loudWindows = 0
                        triggerSupport()
                    }
                }
            }
        } finally {
            runCatching { r.stop() }
            r.release()
            recorder = null
        }
    }

    private suspend fun triggerSupport() {
        val now = System.currentTimeMillis()
        if (!CompanionProfile.autoSupportAllowed("voice", now)) return
        val guidance = runCatching { SituationSupport.forMessage("صوت مرتفع، غضب وانفعال") }.getOrNull() ?: return
        CompanionProfile.recordAutoSupport("voice", now)
        val text = SituationSupport.spokenGuidance(guidance)
        Speaker.init(this)
        Speaker.say(text)
        Notifier.show(this, NOTIF_ID + 1, Notifier.CH_EMOTION_VOICE, "أثر لاحظ ارتفاع النبرة", "ذكر ودعاء بدون تسجيل الصوت", route = "assistant")
    }

    override fun onDestroy() {
        scope.cancel()
        runCatching { recorder?.stop() }
        recorder?.release()
        recorder = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val NOTIF_ID = 48_200
        fun start(ctx: Context) {
            if (!SafiApp.prefs.emotionVoiceOn) return
            if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return
            runCatching { ContextCompat.startForegroundService(ctx, Intent(ctx, EmotionVoiceService::class.java)) }
        }
        fun stop(ctx: Context) { runCatching { ctx.stopService(Intent(ctx, EmotionVoiceService::class.java)) } }
    }
}
