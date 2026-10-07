package com.mohamed.safi.docs

import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** On-device OCR index for document pages. Images and recognized text never leave the phone. */
object DocOcr {
    private const val PREFS = "athar_document_ocr"
    private const val PREFIX = "doc_"
    private const val MAX_TEXT = 30_000

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun text(ctx: Context, docId: Long): String = prefs(ctx).getString(PREFIX + docId, "") ?: ""

    fun clear(ctx: Context, docId: Long) = prefs(ctx).edit { remove(PREFIX + docId) }

    suspend fun index(ctx: Context, docId: Long, pages: List<String>): String {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return try {
            val text = pages.mapNotNull { path ->
                val file = File(path)
                if (!file.isFile) null else try {
                    recognizer.process(InputImage.fromFilePath(ctx, Uri.fromFile(file))).processAwait().text.trim()
                } catch (_: Exception) { null }
            }.filter { it.isNotBlank() }.joinToString("\n\n").take(MAX_TEXT)
            prefs(ctx).edit { putString(PREFIX + docId, text) }
            text
        } finally {
            recognizer.close()
        }
    }

    private suspend fun <T> Task<T>.processAwait(): T = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { if (cont.isActive) cont.resume(it) }
        addOnFailureListener { if (cont.isActive) cont.resumeWithException(it) }
        addOnCanceledListener { cont.cancel() }
    }
}
