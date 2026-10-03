package com.mohamed.safi.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Base64
import android.media.ExifInterface
import com.mohamed.safi.SafiApp
import com.mohamed.safi.data.Cats
import com.mohamed.safi.data.parseIso
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.ByteArrayOutputStream
import java.io.File

data class ReceiptResult(
    val merchant: String,
    val total: Double,
    val currency: String,
    val category: String,
    val time: Long?,
    val items: String,
    val imagePath: String,
)

object ReceiptReader {

    private fun loadScaled(ctx: Context, uri: Uri, maxSide: Int = 1568): Bitmap? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        if (opts.outWidth <= 0) return null
        var sample = 1
        while (opts.outWidth / sample > maxSide * 2 || opts.outHeight / sample > maxSide * 2) sample *= 2
        val bmp = ctx.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null
        val rotation = runCatching {
            ctx.contentResolver.openInputStream(uri)?.use { s ->
                when (ExifInterface(s).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
        }.getOrDefault(0f)
        val scale = minOf(1f, maxSide.toFloat() / maxOf(bmp.width, bmp.height))
        val m = Matrix().apply {
            postScale(scale, scale)
            if (rotation != 0f) postRotate(rotation)
        }
        return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
    }

    suspend fun read(ctx: Context, uri: Uri): ReceiptResult = withContext(Dispatchers.IO) {
        val bmp = loadScaled(ctx, uri) ?: throw ClaudeException("مش قادر أفتح الصورة")
        val dir = File(ctx.filesDir, "receipts").apply { mkdirs() }
        val file = File(dir, "r_${System.currentTimeMillis()}.jpg")
        val bytes = ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }.toByteArray()
        file.writeBytes(bytes)
        val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)

        val prompt = """
            This is a receipt/invoice photographed in the UAE (could be Arabic or English).
            Return JSON only:
            {"merchant": "store name", "total": number (final amount paid incl. VAT), "currency": "AED",
             "datetime": "YYYY-MM-DDTHH:MM or empty", "category": one of [${Cats.expense.joinToString(", ")}],
             "items": "up to 6 main items with prices, one line, Arabic or English"}
        """.trimIndent()
        val raw = Claude.call(
            "You read receipts precisely. JSON only.",
            JSONArray().put(Claude.userImage(b64, prompt)),
            SafiApp.prefs.model, 800,
        )
        val j = Claude.extractJson(raw) ?: throw ClaudeException("مقدرتش أقرا الفاتورة، جرب صورة أوضح")
        val cat = j.optString("category").takeIf { it in Cats.expense } ?: Cats.OTHER
        ReceiptResult(
            merchant = j.optString("merchant"),
            total = j.optDouble("total", 0.0).let { if (it.isNaN()) 0.0 else it },
            currency = j.optString("currency", "AED").ifBlank { "AED" }.uppercase(),
            category = cat,
            time = parseIso(j.optString("datetime")),
            items = j.optString("items"),
            imagePath = file.absolutePath,
        )
    }
}
