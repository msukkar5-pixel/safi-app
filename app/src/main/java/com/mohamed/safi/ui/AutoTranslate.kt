package com.mohamed.safi.ui

import android.content.Context
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Builds a UI translation for a language the app doesn't ship, on the phone: Google ML Kit's free offline
 * translator turns the English strings (assets/i18n/en.json) into the target language once, and the result is saved
 * as files/i18n/<code>.json with the same Arabic keys. Quran, hadith and book text are never translated.
 */
object AutoTranslate {
    /** 0..1 while translating, null when idle. */
    val progress = MutableStateFlow<Float?>(null)
    val error = MutableStateFlow<String?>(null)

    fun file(ctx: Context, code: String) = File(File(ctx.filesDir, "i18n").apply { mkdirs() }, "$code.json")

    private suspend fun <T> com.google.android.gms.tasks.Task<T>.await(): T = suspendCancellableCoroutine { c ->
        addOnSuccessListener { c.resume(it) }
        addOnFailureListener { c.resumeWithException(it) }
        addOnCanceledListener { c.cancel() }
    }

    /** Placeholders like {0} must survive translation; protect them as numbers in brackets the translator keeps. */
    private fun protect(s: String) = s.replace(Regex("\\{(\\d+)\\}")) { "[[${it.groupValues[1]}]]" }
    private fun restore(s: String) = s.replace(Regex("\\[\\s*\\[\\s*(\\d+)\\s*]\\s*]")) { "{${it.groupValues[1]}}" }

    suspend fun build(ctx: Context, code: String): Boolean = withContext(Dispatchers.IO) {
        val target = TranslateLanguage.fromLanguageTag(code) ?: run { error.value = "اللغة دي مش مدعومة"; return@withContext false }
        val translator = Translation.getClient(TranslatorOptions.Builder().setSourceLanguage(TranslateLanguage.ENGLISH).setTargetLanguage(target).build())
        try {
            error.value = null
            progress.value = 0f
            translator.downloadModelIfNeeded(DownloadConditions.Builder().build()).await()
            val en = JSONObject(ctx.assets.open("i18n/en.json").bufferedReader().use { it.readText() })
            val out = JSONObject()
            val keys = en.keys().asSequence().toList()
            keys.forEachIndexed { i, k ->
                val v = en.optString(k)
                if (v.isNotBlank()) {
                    val t = runCatching { restore(translator.translate(protect(v)).await()) }.getOrNull()
                    // keep the English text if a placeholder got lost
                    val ok = t != null && Regex("\\{\\d+}").findAll(v).all { m -> t.contains(m.value) }
                    out.put(k, if (ok) t else v)
                }
                if (i % 40 == 0) progress.value = i / keys.size.toFloat()
            }
            file(ctx, code).writeText(out.toString())
            true
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            error.value = "مقدرتش أجهّز اللغة (محتاج إنترنت أول مرة): ${e.message ?: ""}"
            false
        } finally {
            progress.value = null
            translator.close()
        }
    }
}
