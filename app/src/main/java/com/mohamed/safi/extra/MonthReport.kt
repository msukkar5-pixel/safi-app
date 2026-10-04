package com.mohamed.safi.extra

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.mohamed.safi.SafiApp
import com.mohamed.safi.data.*
import com.mohamed.safi.fitness.Fit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.YearMonth

/** One-month PDF: spending, Egypt transfers, obligations, debts, savings, weight. */
object MonthReport {
    private const val W = 595
    private const val H = 842
    private const val M = 40f

    private class Writer(val doc: PdfDocument) {
        var page: PdfDocument.Page? = null
        var y = 0f
        var n = 0
        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 20f; typeface = Typeface.DEFAULT_BOLD; color = Color.rgb(15, 110, 92); textAlign = Paint.Align.RIGHT }
        val h2 = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 14f; typeface = Typeface.DEFAULT_BOLD; color = Color.rgb(15, 110, 92); textAlign = Paint.Align.RIGHT }
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 11f; color = Color.rgb(30, 30, 30); textAlign = Paint.Align.RIGHT }
        val num = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 11f; color = Color.rgb(30, 30, 30); textAlign = Paint.Align.LEFT; typeface = Typeface.DEFAULT_BOLD }
        val bar = Paint().apply { color = Color.rgb(207, 232, 225) }
        val line = Paint().apply { color = Color.rgb(220, 220, 220); strokeWidth = 1f }

        fun newPage() {
            page?.let { doc.finishPage(it) }
            n++
            page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, n).create())
            y = M
        }
        private fun need(h: Float) { if (page == null || y + h > H - M) newPage() }
        fun heading(t: String) { need(40f); y += 26f; page!!.canvas.drawText(t, W - M, y, title); y += 8f }
        fun section(t: String) { need(34f); y += 22f; page!!.canvas.drawText(t, W - M, y, h2); y += 4f; page!!.canvas.drawLine(M, y + 2, W - M, y + 2, line); y += 6f }
        fun text(t: String) { need(16f); y += 15f; page!!.canvas.drawText(t, W - M, y, body) }
        fun row(label: String, value: String, fraction: Float = -1f) {
            need(18f); y += 16f
            val c = page!!.canvas
            if (fraction >= 0f) c.drawRect(M, y - 10f, M + (W - 2 * M) * 0.45f * fraction.coerceIn(0f, 1f), y + 3f, bar)
            c.drawText(label, W - M, y, body)
            c.drawText(value, M + 4f, y, num)
        }
        fun finish() { page?.let { doc.finishPage(it) } }
    }

    suspend fun build(ctx: Context, ym: YearMonth): File = withContext(Dispatchers.IO) {
        val dao = SafiApp.db.dao()
        val (from, to) = monthRange(ym)
        val ex = dao.expensesBetweenNow(from, to)
        val out = ex.filter { !it.isIncome }
        val spent = out.sumOf { it.amountAed }
        val income = ex.filter { it.isIncome }.sumOf { it.amountAed }
        val transfers = dao.transfersBetweenNow(from, to)
        val sent = transfers.sumOf { it.amountAed + it.feesAed }

        val doc = PdfDocument()
        val w = Writer(doc)
        w.newPage()
        w.heading("تقرير ${monthName(ym)} — ${SafiApp.prefs.userName}")
        w.text("اتعمل من تطبيق صافي ${dateTimeStr(System.currentTimeMillis())}")

        w.section("الملخص")
        w.row("المصروف", money(spent))
        w.row("تحويلات مصر (شامل الرسوم)", money(sent))
        w.row("الدخل المسجل", money(income))
        w.row("الصافي", money(income - spent - sent))
        w.row("منه كاش", money(out.filter { it.method == "cash" }.sumOf { it.amountAed }))
        w.row("عدد العمليات", "${out.size}")

        w.section("المصروف حسب التصنيف")
        val byCat = out.groupBy { it.category }.mapValues { e -> e.value.sumOf { it.amountAed } }.entries.sortedByDescending { it.value }
        val maxC = byCat.maxOfOrNull { it.value } ?: 1.0
        byCat.forEach { (c, v) -> w.row("$c  (${if (spent > 0) (v / spent * 100).toInt() else 0}%)", money(v), (v / maxC).toFloat()) }

        w.section("أكتر أماكن صرفت فيها")
        out.filter { it.merchant.isNotBlank() }.groupBy { it.merchant.lowercase() }
            .map { (_, v) -> v.first().merchant to v.sumOf { it.amountAed } }.sortedByDescending { it.second }.take(10)
            .forEach { (m, v) -> w.row(m, money(v)) }

        if (transfers.isNotEmpty()) {
            w.section("تحويلات مصر حسب البند")
            transfers.groupBy { it.category }.forEach { (c, list) ->
                w.row(c, "${money(list.sumOf { it.amountEgp }, "EGP")}  =  ${money(list.sumOf { it.amountAed })}")
            }
        }

        val next = ym.plusMonths(1)
        val obs = Obligations.forMonth(next)
        if (obs.isNotEmpty()) {
            w.section("مطلوب منك في ${monthName(next)}: ${money(obs.sumOf { it.amountAed })}")
            obs.forEach { o -> w.row("${o.title} — ${shortDate(o.due)}", money(o.amount, o.currency)) }
        }

        val debts = dao.openDebtsNow()
        if (debts.isNotEmpty()) {
            w.section("السلف المفتوحة")
            debts.forEach { d -> w.row((if (d.direction == "i_owe") "عليك لـ " else "ليك عند ") + d.person + (d.dueDate?.let { " — ${shortDate(it)}" } ?: ""), money(d.remaining, d.currency)) }
        }

        val goals = runCatching { ExtraDb.dao.goalsNow() }.getOrDefault(emptyList())
        if (goals.isNotEmpty()) {
            w.section("الادخار")
            goals.forEach { g -> w.row(g.name, "${money(g.saved, g.currency)} / ${money(g.target, g.currency)}", (g.saved / g.target).toFloat()) }
        }

        val weights = runCatching { Fit.dao.weightsNow().filter { it.time in from..to } }.getOrDefault(emptyList())
        if (weights.size >= 2) {
            w.section("الوزن")
            w.row("أول الشهر", "${fmt(weights.last().kg)} كجم")
            w.row("آخر الشهر", "${fmt(weights.first().kg)} كجم")
        }

        w.finish()
        val dir = File(ctx.cacheDir, "reports").apply { mkdirs() }
        val f = File(dir, "safi-report-$ym.pdf")
        f.outputStream().use { doc.writeTo(it) }
        doc.close()
        f
    }

    fun share(ctx: Context, f: File) {
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
        val i = Intent(Intent.ACTION_SEND).setType("application/pdf").putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(i, "شارك التقرير").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
