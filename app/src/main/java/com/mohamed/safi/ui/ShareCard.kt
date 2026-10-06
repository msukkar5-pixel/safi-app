package com.mohamed.safi.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import com.mohamed.safi.R
import java.io.File

/** Draws a simple branded image card (title, Quran text, footer) and opens the share sheet. Nothing leaves the phone until the user picks an app. */
object ShareCard {
    fun share(ctx: Context, title: String, body: String, source: String, footer: String) {
        val file = render(ctx, title, body, source, footer) ?: return
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", file)
        val send = Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(send, tr("شارك البطاقة")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Draws the card into cache/[name].png and returns the file. */
    fun render(ctx: Context, title: String, body: String, source: String, footer: String, name: String = "safi_card", story: Boolean = false): File? {
        val w = 1080; val h = if (story) 1920 else 1350
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), Paint().apply {
            shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), 0xFF1A2A4F.toInt(), 0xFF3B4C8C.toInt(), Shader.TileMode.CLAMP)
        })
        val gold = 0xFFC9A24B.toInt()
        c.drawRoundRect(40f, 40f, w - 40f, h - 40f, 48f, 48f, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 4f; color = gold })
        val amiri = runCatching { ResourcesCompat.getFont(ctx, R.font.amiri_regular) }.getOrNull()
        val amiriBold = runCatching { ResourcesCompat.getFont(ctx, R.font.amiri_bold) }.getOrNull()
        fun layout(text: String, size: Float, color: Int, bold: Boolean): StaticLayout {
            val p = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; this.color = color; typeface = if (bold) amiriBold else amiri }
            return StaticLayout.Builder.obtain(text, 0, text.length, p, w - 200).setAlignment(Layout.Alignment.ALIGN_CENTER).setLineSpacing(0f, 1.25f).build()
        }
        fun draw(l: StaticLayout, y: Float): Float { c.save(); c.translate(100f, y); l.draw(c); c.restore(); return y + l.height }
        val t = layout(title, 64f, gold, true)
        var bodySize = 58f
        var b = layout(body, bodySize, android.graphics.Color.WHITE, false)
        val maxBody = if (story) 1200 else 760
        while (b.height > maxBody && bodySize > 30f) { bodySize -= 4f; b = layout(body, bodySize, android.graphics.Color.WHITE, false) }
        val s = layout(source, 36f, gold, false)
        val f = layout(footer, 38f, 0xCCFFFFFF.toInt(), false)
        // stories: keep text clear of the apps' top and bottom bars
        val top = if (story) 300f else 130f; val bottom = if (story) 330f else 130f
        draw(t, top)
        val mid = (h - b.height - s.height - 30) / 2f + 40f
        draw(s, draw(b, mid) + 30f)
        draw(f, h - bottom - f.height)
        if ("صافي" !in footer) draw(layout("صافي", 34f, gold, true), h - bottom + 20f)

        val file = File(ctx.cacheDir, "$name.png")
        return runCatching { file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }; file }.getOrNull()
    }
}
