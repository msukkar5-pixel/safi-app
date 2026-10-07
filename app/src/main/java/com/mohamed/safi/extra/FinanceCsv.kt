package com.mohamed.safi.extra

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.mohamed.safi.SafiApp
import com.mohamed.safi.data.isoLocal
import com.mohamed.safi.data.monthRange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.YearMonth

/** Local, shareable month export. Values are quoted so the CSV opens safely in spreadsheet apps. */
object FinanceCsv {
    private fun csv(value: Any?): String = "\"" + (value?.toString() ?: "").replace("\"", "\"\"").replace("\n", " ") + "\""

    suspend fun build(ctx: Context, month: YearMonth): File = withContext(Dispatchers.IO) {
        val dao = SafiApp.db.dao()
        val (from, to) = monthRange(month)
        val expenses = dao.expensesBetweenNow(from, to)
        val transfers = dao.transfersBetweenNow(from, to)
        val dir = File(ctx.cacheDir, "reports").apply { mkdirs() }
        val out = File(dir, "athar-finance-$month.csv")
        out.bufferedWriter(Charsets.UTF_8).use { w ->
            // UTF-8 BOM keeps Arabic readable in Excel on common phone/desktop workflows.
            w.write('\uFEFF'.code)
            w.appendLine("type,date,amount,currency,amount_aed,category,merchant,method,recipient,fees_aed,note")
            expenses.forEach { e ->
                w.appendLine(listOf(
                    if (e.isIncome) "income" else "expense", isoLocal(e.time), e.amount, e.currency, e.amountAed,
                    e.category, e.merchant, e.method, "", "", e.note,
                ).joinToString(",") { value -> csv(value) })
            }
            transfers.forEach { t ->
                w.appendLine(listOf(
                    "egypt_transfer", isoLocal(t.time), t.amountEgp, "EGP", t.amountAed,
                    t.category, "", "", t.recipient, t.feesAed, t.note,
                ).joinToString(",") { value -> csv(value) })
            }
        }
        out
    }

    fun share(ctx: Context, file: File) {
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", file)
        val intent = Intent(Intent.ACTION_SEND).setType("text/csv")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(intent, "شارك ملف CSV").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
