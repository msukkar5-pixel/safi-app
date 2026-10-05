package com.mohamed.safi.docs

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Page image files live under filesDir/docs (covered by the "${applicationId}.files" FileProvider). */
object DocFiles {
    fun dir(ctx: Context): File = File(ctx.filesDir, "docs").apply { mkdirs() }

    private var seq = 0
    fun newFile(ctx: Context): File {
        seq = (seq + 1) % 1000
        return File(dir(ctx), "doc_${System.currentTimeMillis()}_$seq.jpg")
    }

    fun uriFor(ctx: Context, f: File): Uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)

    /** Copy an image from any content uri into docs/. Returns the new absolute path. */
    fun copyIn(ctx: Context, src: Uri): String? = runCatching {
        val f = newFile(ctx)
        ctx.contentResolver.openInputStream(src)?.use { i -> f.outputStream().use { i.copyTo(it) } } ?: return null
        if (f.length() == 0L) { f.delete(); null } else f.absolutePath
    }.getOrNull()

    fun delete(path: String?) { if (path != null) runCatching { File(path).delete() } }
}

object DocPdf {
    private const val A4_W = 595 // points (1/72")
    private const val A4_H = 842
    private const val MARGIN = 24f
    private const val MAX_PX = 1800

    fun fileName(title: String): String {
        val clean = title.replace(Regex("[\\\\/:*?\"<>|\\n\\r\\t]"), " ").trim().ifBlank { "document" }.take(60)
        val day = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US))
        return "$clean $day.pdf"
    }

    /** Builds an A4 PDF from [pages] (image paths) into cache/pdf/. Null if no page could be drawn. */
    fun build(ctx: Context, title: String, pages: List<String>): File? {
        val doc = PdfDocument()
        var n = 0
        try {
            for (p in pages) {
                val bmp = loadBitmap(p) ?: continue
                n++
                val page = doc.startPage(PdfDocument.PageInfo.Builder(A4_W, A4_H, n).create())
                val c = page.canvas
                c.drawColor(Color.WHITE)
                val aw = A4_W - 2 * MARGIN
                val ah = A4_H - 2 * MARGIN
                val s = minOf(aw / bmp.width, ah / bmp.height)
                val w = bmp.width * s
                val h = bmp.height * s
                val left = (A4_W - w) / 2f
                val top = (A4_H - h) / 2f
                c.drawBitmap(bmp, null, RectF(left, top, left + w, top + h), Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
                doc.finishPage(page)
                bmp.recycle()
            }
            if (n == 0) return null
            val dir = File(ctx.cacheDir, "pdf").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val out = File(dir, fileName(title))
            out.outputStream().use { doc.writeTo(it) }
            return out
        } finally {
            doc.close()
        }
    }

    /** Copies [pdf] to Downloads/Safi (API 29+) or the app's external Downloads dir (26–28). Returns a readable location. */
    fun saveToDownloads(ctx: Context, pdf: File): String? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val cr = ctx.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, pdf.name)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Safi")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = cr.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
            cr.openOutputStream(uri)?.use { o -> pdf.inputStream().use { it.copyTo(o) } } ?: run { cr.delete(uri, null, null); return null }
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            cr.update(uri, values, null, null)
            "Download/Safi/${pdf.name}"
        } else {
            val dir = ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: return null
            dir.mkdirs()
            val f = File(dir, pdf.name)
            pdf.copyTo(f, overwrite = true)
            f.absolutePath
        }
    }.getOrNull()

    fun shareIntent(ctx: Context, pdf: File): Intent {
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", pdf)
        val send = Intent(Intent.ACTION_SEND)
            .setType("application/pdf")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newRawUri(pdf.name, uri)
        return Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    /** Decodes a page downsampled to at most [MAX_PX] on the long side, upright per EXIF. */
    private fun loadBitmap(path: String): Bitmap? = runCatching {
        val f = File(path)
        if (!f.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_PX) sample *= 2
        val raw = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val rot = when (runCatching { ExifInterface(path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (rot == 0f) raw else Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, Matrix().apply { postRotate(rot) }, true).also { if (it !== raw) raw.recycle() }
    }.getOrNull()
}
