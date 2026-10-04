package com.mohamed.safi.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import com.mohamed.safi.ui.Text
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.mohamed.safi.SafiApp
import com.mohamed.safi.ai.Claude
import com.mohamed.safi.ai.Providers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

object VoicePrefs {
    private fun sp() = SafiApp.instance.getSharedPreferences("safi_voice", Context.MODE_PRIVATE)
    var lang: String get() = sp().getString("lang", "ar-EG") ?: "ar-EG"; set(v) = sp().edit { putString("lang", v) }
    /** google = phone's speech engine (free) | ai = record and let the AI provider transcribe (more accurate) */
    var engine: String get() = sp().getString("engine", "google") ?: "google"; set(v) = sp().edit { putString("engine", v) }

    val languages = linkedMapOf(
        "ar-EG" to "عربي مصري", "ar-AE" to "عربي إماراتي", "ar-SA" to "عربي سعودي", "ar-JO" to "عربي شامي",
        "ar" to "عربي فصحى", "en-US" to "English (US)", "en-GB" to "English (UK)", "fr-FR" to "Français",
        "hi-IN" to "हिन्दी", "ur-PK" to "اردو", "tl-PH" to "Filipino", "tr-TR" to "Türkçe",
    )

    val sttProviders = linkedMapOf("gemini" to "Gemini (فيه باقة مجانية)", "groq" to "Groq (فيه باقة مجانية)", "openai" to "OpenAI")

    /** Which service turns recorded speech into text (independent of the chat AI). */
    var sttProvider: String
        get() = sp().getString("stt", null)
            ?: Providers.current.id.takeIf { it in sttProviders && SafiApp.prefs.keyOf(it).isNotBlank() }
            ?: sttProviders.keys.firstOrNull { SafiApp.prefs.keyOf(it).isNotBlank() }
            ?: "gemini"
        set(v) = sp().edit { putString("stt", v) }

    fun aiCanTranscribe() = SafiApp.prefs.keyOf(sttProvider).isNotBlank()
}

/** Phone speech engine that keeps listening through pauses until the user taps "done". */
private class ContinuousRecognizer(
    private val ctx: Context,
    private val lang: String,
    private val onPartial: (String) -> Unit,
    private val onChunk: (String) -> Unit,
    private val onFinished: () -> Unit,
    private val onFatal: (String) -> Unit,
) {
    private val sr = SpeechRecognizer.createSpeechRecognizer(ctx)
    private var active = false
    private var stopping = false

    private fun intent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, lang)
        .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
        .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
        .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, ctx.packageName)

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
        override fun onPartialResults(partialResults: Bundle?) {
            partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let(onPartial)
        }
        override fun onResults(results: Bundle?) {
            results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.takeIf { it.isNotBlank() }?.let(onChunk)
            if (active && !stopping) restart() else onFinished()
        }
        override fun onError(error: Int) {
            when {
                stopping || !active -> onFinished()
                error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> restart()
                error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY || error == SpeechRecognizer.ERROR_CLIENT -> {
                    sr.cancel(); restart()
                }
                error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> onFatal("اسمح للتطبيق باستخدام الميكروفون")
                error == SpeechRecognizer.ERROR_NETWORK || error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> onFatal("التعرف على الصوت محتاج إنترنت")
                error == 12 || error == 13 -> onFatal("اللغة دي مش متاحة على تليفونك. غيّرها من الإعدادات أو نزّل حزمة اللغة")
                else -> restart()
            }
        }
    }

    private fun restart() {
        if (!active) return
        runCatching { sr.startListening(intent()) }.onFailure { onFatal("الميكروفون مش متاح") }
    }

    fun start() {
        active = true; stopping = false
        sr.setRecognitionListener(listener)
        restart()
    }

    fun finish() { stopping = true; runCatching { sr.stopListening() } }
    fun cancel() { active = false; runCatching { sr.cancel() } }
    fun destroy() { active = false; runCatching { sr.destroy() } }
}

/**
 * Opens a listening sheet and returns the full sentence/paragraph when the user taps "done".
 * Works with the phone's speech engine (free, live text) or the AI provider's transcription (more accurate).
 */
@Composable
fun rememberVoiceInput(onText: (String) -> Unit): () -> Unit {
    var open by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) open = true else toast(ctx, "لازم تسمح بالميكروفون علشان تتكلم")
    }
    if (open) VoiceSheet(onDone = { t -> open = false; if (t.isNotBlank()) onText(t) }, onCancel = { open = false })
    return {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) open = true
        else perm.launch(Manifest.permission.RECORD_AUDIO)
    }
}

@Composable
private fun VoiceSheet(onDone: (String) -> Unit, onCancel: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val useAi = VoicePrefs.engine == "ai" && VoicePrefs.aiCanTranscribe()
    var committed by remember { mutableStateOf("") }
    var partial by remember { mutableStateOf("") }
    var status by remember { mutableStateOf(if (useAi) "بسجّل… اتكلم براحتك ودوس خلصت" else "اتكلم… مش هقفل لحد ما تدوس خلصت") }
    var busy by remember { mutableStateOf(false) }
    var finishing by remember { mutableStateOf(false) }
    var recognizer by remember { mutableStateOf<ContinuousRecognizer?>(null) }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var audioFile by remember { mutableStateOf<File?>(null) }
    var seconds by remember { mutableIntStateOf(0) }
    var delivered by remember { mutableStateOf(false) }
    val deliver: (String) -> Unit = { t -> if (!delivered) { delivered = true; onDone(t) } }

    val full = (committed + " " + partial).trim()

    LaunchedEffect(Unit) {
        if (useAi) {
            val gem = VoicePrefs.sttProvider == "gemini"
            val f = File(ctx.cacheDir, "voice_${System.currentTimeMillis()}.${if (gem) "aac" else "m4a"}")
            val r = (if (Build.VERSION.SDK_INT >= 31) MediaRecorder(ctx) else @Suppress("DEPRECATION") MediaRecorder()).apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(if (gem) MediaRecorder.OutputFormat.AAC_ADTS else MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(16000)
                setAudioEncodingBitRate(48000)
                setAudioChannels(1)
                setOutputFile(f.absolutePath)
            }
            try {
                r.prepare(); r.start(); recorder = r; audioFile = f
            } catch (e: Exception) {
                status = "مقدرتش أشغّل التسجيل"
            }
            while (recorder != null && !finishing) { delay(1000); seconds++ }
        } else {
            if (!SpeechRecognizer.isRecognitionAvailable(ctx)) {
                status = "التعرف على الصوت مش متاح. نزّل تطبيق Google أو اختار \"تحويل بالذكاء الاصطناعي\" من الإعدادات"
                return@LaunchedEffect
            }
            val rec = ContinuousRecognizer(
                ctx, VoicePrefs.lang,
                onPartial = { partial = it },
                onChunk = { committed = (committed + " " + it).trim(); partial = "" },
                onFinished = { if (finishing) deliver((committed + " " + partial).trim()) },
                onFatal = { status = it },
            )
            recognizer = rec
            rec.start()
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            recognizer?.destroy()
            runCatching { recorder?.stop() }; runCatching { recorder?.release() }
        }
    }

    fun done() {
        if (finishing) return
        finishing = true
        if (useAi) {
            val r = recorder
            recorder = null
            runCatching { r?.stop() }; runCatching { r?.release() }
            val f = audioFile
            if (f == null || f.length() < 1000) { deliver(""); return }
            busy = true; status = "بحوّل الكلام لكتابة…"
            scope.launch {
                try {
                    deliver(Claude.transcribe(f, VoicePrefs.lang))
                } catch (e: Exception) {
                    busy = false; finishing = false
                    status = e.message ?: "التحويل فشل"
                } finally { f.delete() }
            }
        } else {
            status = "بخلّص…"
            recognizer?.finish()
            // safety: deliver what we have if the engine doesn't answer
            scope.launch { delay(2500); if (finishing) deliver((committed + " " + partial).trim()) }
        }
    }

    val pulse by rememberInfiniteTransition(label = "p").animateFloat(
        1f, 1.15f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "s",
    )

    AlertDialog(
        onDismissRequest = {},
        title = { Text(if (useAi) "تسجيل" else "بسمعك") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Surface(
                    shape = CircleShape, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(84.dp).scale(if (busy || finishing) 1f else pulse),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (busy) CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary)
                        else Icon(Icons.Default.Mic, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(42.dp))
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(status + if (useAi && !busy) "  ${seconds / 60}:${"%02d".format(seconds % 60)}" else "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                Text(VoicePrefs.languages[VoicePrefs.lang] ?: VoicePrefs.lang, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                if (!useAi) {
                    Spacer(Modifier.height(10.dp))
                    Column(Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState())) {
                        Text(if (full.isBlank()) "…" else full, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { done() }, enabled = !busy) { Icon(Icons.Default.Check, null); Spacer(Modifier.width(4.dp)); Text("خلصت") }
        },
        dismissButton = {
            TextButton(onClick = { recognizer?.cancel(); onCancel() }, enabled = !busy) { Text("إلغاء") }
        },
    )
}
