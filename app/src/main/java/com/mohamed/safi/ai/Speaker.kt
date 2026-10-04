package com.mohamed.safi.ai

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/** Reads Safi's replies out loud (Arabic). */
object Speaker {
    private var tts: TextToSpeech? = null
    private var ready = false
    private var pending: String? = null

    fun init(ctx: Context) {
        if (tts != null) return
        tts = TextToSpeech(ctx.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val t = tts ?: return@TextToSpeech
                val eg = Locale("ar", "EG")
                val r = t.setLanguage(eg)
                if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) t.setLanguage(Locale("ar"))
                ready = true
                pending?.let { say(it) }
                pending = null
            }
        }
    }

    fun say(text: String) {
        val clean = text.replace(Regex("[*#_`•✓✗⚠️]"), " ").replace(Regex("\\s+"), " ").trim().take(800)
        if (clean.isEmpty()) return
        if (!ready) { pending = clean; return }
        val arabic = clean.count { it in '\u0600'..'\u06FF' } > clean.length / 4
        runCatching { tts?.setLanguage(if (arabic) Locale("ar", "EG") else Locale.forLanguageTag(com.mohamed.safi.ui.VoicePrefs.lang.takeIf { !it.startsWith("ar") } ?: "en-US")) }
        tts?.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "safi")
    }

    fun stop() { tts?.stop() }
}
