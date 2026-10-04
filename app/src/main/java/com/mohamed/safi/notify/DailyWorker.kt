package com.mohamed.safi.notify

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mohamed.safi.SafiApp
import com.mohamed.safi.data.Budget
import com.mohamed.safi.data.Fx
import com.mohamed.safi.data.Obligations
import com.mohamed.safi.data.daysUntil
import com.mohamed.safi.data.dueText
import com.mohamed.safi.data.fmt
import com.mohamed.safi.data.money
import com.mohamed.safi.data.monthName
import com.mohamed.safi.data.monthRange
import com.mohamed.safi.data.remaining
import com.mohamed.safi.data.shortDate
import com.mohamed.safi.data.zone
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.util.concurrent.TimeUnit

class DailyWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        runCatching { Fx.refresh() }
        runCatching { Brief.morning(applicationContext) }
        runCatching { com.mohamed.safi.faith.Prayer.refreshLocation(applicationContext); com.mohamed.safi.faith.Prayer.schedule(applicationContext) }
        runCatching { com.mohamed.safi.data.Carpool.schedule(applicationContext) }
        runCatching { com.mohamed.safi.widget.SafiWidget.updateAll(applicationContext) }
        runCatching {
            if (java.time.LocalDate.now(zone).dayOfWeek == java.time.DayOfWeek.FRIDAY) com.mohamed.safi.extra.Backup.autoToDownloads(applicationContext)
        }
        runCatching {
            // keep 6 months of location history
            SafiApp.db.dao().deleteLocationsBefore(System.currentTimeMillis() - 183L * 86_400_000L)
        }
        return Result.success()
    }

    companion object {
        fun schedule(ctx: Context, replace: Boolean) {
            val hour = SafiApp.prefs.briefHour
            val now = LocalDateTime.now(zone)
            var next = now.toLocalDate().atTime(hour, 0)
            if (!next.isAfter(now)) next = next.plusDays(1)
            val delay = Duration.between(now, next).toMillis()
            val req = PeriodicWorkRequestBuilder<DailyWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
                "daily_brief",
                if (replace) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP,
                req,
            )
        }
    }
}

object Brief {

    /** Lines for today: bills, debts, car. Also used by the Home screen. */
    suspend fun todayLines(): List<String> {
        val dao = SafiApp.db.dao()
        val prefs = SafiApp.prefs
        val lines = mutableListOf<String>()

        for (b in dao.billsNow()) {
            val d = daysUntil(b.nextDue)
            if (d <= b.remindDaysBefore) {
                lines += "• ${b.name}: ${money(b.amount, b.currency)} — ${dueText(b.nextDue)}"
            }
        }
        for (debt in dao.openDebtsNow()) {
            val due = debt.dueDate ?: continue
            val d = daysUntil(due)
            if (debt.direction == "i_owe" && d <= 3) {
                lines += "• سداد لـ ${debt.person}: ${money(debt.remaining, debt.currency)} — ${dueText(due)}"
            } else if (debt.direction == "owed_to_me" && d <= 0) {
                lines += "• ${debt.person} المفروض يرجعلك ${money(debt.remaining, debt.currency)} (${dueText(due)})"
            }
        }
        val odo = prefs.odometer
        for (c in dao.carItemsNow()) {
            val dueByDate = if (c.intervalMonths > 0) {
                java.time.Instant.ofEpochMilli(c.lastDate).atZone(zone).toLocalDate().plusMonths(c.intervalMonths.toLong())
            } else null
            val kmLeft = if (c.intervalKm > 0 && odo > 0) c.lastKm + c.intervalKm - odo else null
            val dateClose = dueByDate != null && !dueByDate.isAfter(LocalDate.now(zone).plusDays(7))
            val kmClose = kmLeft != null && kmLeft <= 500
            if (dateClose || kmClose) {
                lines += "• العربية: ${c.name}" + (if (kmLeft != null) " (فاضل ${fmt(kmLeft.toDouble())} كم)" else "") +
                    (if (dueByDate != null) " — ميعاده ${dueByDate.dayOfMonth}/${dueByDate.monthValue}" else "")
            }
        }
        runCatching {
            for (d in com.mohamed.safi.extra.ExtraDb.dao.docsNow()) {
                val e = d.expiry ?: continue
                val left = daysUntil(e)
                if (left <= d.remindDays) {
                    lines += "• ${d.title}${if (d.owner.isNotBlank()) " (${d.owner})" else ""}: " +
                        (if (left < 0) "منتهي من ${-left} يوم" else "ينتهي ${shortDate(e)} (${dueText(e)})")
                }
            }
        }
        listOf(
            "تجديد ترخيص العربية" to prefs.regExpiry,
            "تجديد تأمين العربية" to prefs.insExpiry,
            "تجديد رخصة السواقة" to prefs.licenseExpiry,
        ).forEach { (name, t) ->
            if (t > 0 && daysUntil(t) <= 30) lines += "• $name — ${dueText(t)} (${shortDate(t)})"
        }
        return lines
    }

    suspend fun morning(ctx: Context) {
        val prefs = SafiApp.prefs
        val today = LocalDate.now(zone).toString()
        if (prefs.lastBriefDay == today) return
        prefs.lastBriefDay = today

        val lines = todayLines()
        if (lines.isNotEmpty()) {
            Notifier.show(
                ctx, 501, Notifier.CH_DAILY,
                "صباح الخير" + (prefs.userName.takeIf { it.isNotBlank() }?.let { " يا $it" } ?: "") + " ☀️",
                "المطلوب قريب:\n" + lines.joinToString("\n"),
                route = "bills",
            )
        }

        val ym = YearMonth.now(zone)
        if (LocalDate.now(zone).dayOfMonth <= 3 && prefs.lastMonthlySummary != ym.toString()) {
            prefs.lastMonthlySummary = ym.toString()
            monthly(ctx, ym)
        }
    }

    suspend fun monthly(ctx: Context, ym: YearMonth) {
        val obs = Obligations.forMonth(ym)
        if (obs.isEmpty()) return
        val total = obs.sumOf { it.amountAed }
        val body = obs.take(12).joinToString("\n") {
            "• ${it.title}: ${money(it.amount, it.currency)} — ${shortDate(it.due)}"
        } + (if (obs.size > 12) "\n… و${obs.size - 12} كمان" else "")
        Notifier.show(
            ctx, 502, Notifier.CH_DAILY,
            "مطلوب منك في ${monthName(ym)}: ${money(total)}",
            body, route = "bills",
        )
    }

    /** Alert when a category crosses 80% or 100% of its monthly budget. */
    suspend fun checkBudget(ctx: Context, category: String, justAdded: Double) {
        val dao = SafiApp.db.dao()
        val budget: Budget = dao.budgetFor(category) ?: return
        val (from, to) = monthRange(YearMonth.now(zone))
        val spent = dao.expensesBetweenNow(from, to).filter { it.category == category && !it.isIncome }.sumOf { it.amountAed }
        val before = spent - justAdded
        val limit = budget.monthlyLimit
        if (limit <= 0) return
        val text = when {
            before < limit && spent >= limit -> "عديت ميزانية $category: ${money(spent)} من ${money(limit)}"
            before < limit * 0.8 && spent >= limit * 0.8 -> "قربت تخلص ميزانية $category: ${money(spent)} من ${money(limit)}"
            else -> return
        }
        Notifier.show(ctx, 600 + category.hashCode() % 100, Notifier.CH_MONEY, "تنبيه الميزانية", text, route = "reports")
    }
}
