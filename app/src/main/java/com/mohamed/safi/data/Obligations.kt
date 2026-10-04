package com.mohamed.safi.data

import android.content.Context
import com.mohamed.safi.SafiApp
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

data class Obligation(
    val title: String,
    val type: String,          // bill | transfer | subscription | installment | debt
    val amount: Double,
    val currency: String,
    val amountAed: Double,
    val due: Long,
    val overdue: Boolean,
    val refId: Long,
)

object Obligations {

    private fun sp() = SafiApp.instance.getSharedPreferences("safi_obligations", Context.MODE_PRIVATE)

    /**
     * [anchor]: the bill's real day of month (e.g. 31). Without it plusMonths clamps 31 → 30 → 28
     * and the date never comes back.
     */
    fun step(t: Long, freq: String, anchor: Int? = null): Long? {
        val d = t.toLdt()
        fun anchored(x: LocalDateTime): LocalDateTime =
            if (anchor == null || anchor < 1) x else x.withDayOfMonth(minOf(anchor, x.toLocalDate().lengthOfMonth()))
        return when (freq) {
            "weekly" -> d.plusWeeks(1).millis()
            "monthly" -> anchored(d.plusMonths(1)).millis()
            "quarterly" -> anchored(d.plusMonths(3)).millis()
            "yearly" -> d.plusYears(1).millis()
            else -> null
        }
    }

    /** Next due date for [b] after its current one, keeping its anchor day. */
    fun stepBill(b: Bill): Long? = step(b.nextDue, b.frequency, anchorFor(b))

    /**
     * The bill's day of month. Saved the first time a bill is seen (or edited). A later nextDue that can only be
     * explained by month-end clamping (28–30, below the anchor) keeps the anchor; anything else means the date was
     * changed on purpose, so the anchor follows it.
     */
    fun anchorFor(b: Bill): Int {
        val day = b.nextDue.toLocalDate().dayOfMonth
        if (b.id <= 0) return day
        val key = "bill_anchor_${b.id}"
        val stored = sp().getInt(key, 0)
        val clamped = stored > day && day >= 28
        if (stored == 0 || (stored != day && !clamped)) {
            sp().edit().putInt(key, day).apply()
            return day
        }
        return stored
    }

    /** Call when a bill is created/edited with an explicit due date. */
    fun setAnchor(billId: Long, due: Long) {
        if (billId > 0) sp().edit().putInt("bill_anchor_$billId", due.toLocalDate().dayOfMonth).apply()
    }

    /** nextDue moved back onto the anchor day (undoes earlier clamping), or the same value if nothing drifted. */
    fun anchoredDue(b: Bill): Long {
        if (b.frequency != "monthly" && b.frequency != "quarterly") return b.nextDue
        val d = b.nextDue.toLdt()
        val day = minOf(anchorFor(b), d.toLocalDate().lengthOfMonth())
        return if (day == d.dayOfMonth) b.nextDue else d.withDayOfMonth(day).millis()
    }

    /** Remember that this month's instalment of a debt was paid. */
    fun markInstalmentPaid(debtId: Long, ym: YearMonth = YearMonth.now(zone)) {
        sp().edit().putString("debt_paid_$debtId", ym.toString()).apply()
    }

    private fun instalmentPaidIn(debtId: Long, ym: YearMonth): Boolean =
        sp().getString("debt_paid_$debtId", null) == ym.toString()

    suspend fun forMonth(ym: YearMonth = YearMonth.now(zone)): List<Obligation> {
        val dao = SafiApp.db.dao()
        val (from, to) = monthRange(ym)
        val now = System.currentTimeMillis()
        val isCurrent = ym == YearMonth.now(zone)
        val out = mutableListOf<Obligation>()

        for (raw in dao.billsNow()) {
            // repair dates that drifted to the 28th/30th (e.g. bills advanced by older code or the assistant)
            val fixedDue = anchoredDue(raw)
            val b = if (fixedDue != raw.nextDue) raw.copy(nextDue = fixedDue).also { runCatching { dao.upsertBill(it) } } else raw
            val anchor = anchorFor(b)
            var d = b.nextDue
            if (d < from) {
                if (isCurrent) {
                    out += Obligation(b.name, b.kind, b.amount, b.currency, Fx.toAed(b.amount, b.currency), d, true, b.id)
                }
                // move forward into this month to also count this month's occurrence
                var guard = 0
                while (d < from && guard < 500) {
                    d = step(d, b.frequency, anchor) ?: break
                    guard++
                }
                if (d < from) continue
            }
            var guard = 0
            while (d in from..to && guard < 60) {
                out += Obligation(b.name, b.kind, b.amount, b.currency, Fx.toAed(b.amount, b.currency), d, d < now && isCurrent && d.toLocalDate() < LocalDate.now(zone), b.id)
                d = step(d, b.frequency, anchor) ?: break
                guard++
            }
        }

        for (debt in dao.openDebtsNow().filter { it.direction == "i_owe" }) {
            val rem = debt.remaining
            if (rem <= 0) continue
            val inst = debt.monthlyInstallment
            if (inst != null && inst > 0) {
                if (instalmentPaidIn(debt.id, ym)) continue
                val day = debt.dueDate?.toLocalDate()?.dayOfMonth ?: ym.lengthOfMonth()
                val due = ym.atDay(minOf(day, ym.lengthOfMonth())).millisAt(10)
                val amt = minOf(inst, rem)
                out += Obligation("قسط ${debt.person}", "debt", amt, debt.currency, Fx.toAed(amt, debt.currency), due, false, debt.id)
            } else {
                val due = debt.dueDate ?: continue
                if (due in from..to || (isCurrent && due < from)) {
                    out += Obligation("سداد ${debt.person}", "debt", rem, debt.currency, Fx.toAed(rem, debt.currency), due, due < now, debt.id)
                }
            }
        }
        return out.sortedBy { it.due }
    }

    /** Payments still owed to Mohamed this month. */
    suspend fun owedToMe(): List<Debt> =
        SafiApp.db.dao().openDebtsNow().filter { it.direction == "owed_to_me" }
}
