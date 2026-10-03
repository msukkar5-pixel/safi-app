package com.mohamed.safi.data

import com.mohamed.safi.SafiApp
import java.time.LocalDate
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

    fun step(t: Long, freq: String): Long? {
        val d = t.toLdt()
        return when (freq) {
            "weekly" -> d.plusWeeks(1).millis()
            "monthly" -> d.plusMonths(1).millis()
            "quarterly" -> d.plusMonths(3).millis()
            "yearly" -> d.plusYears(1).millis()
            else -> null
        }
    }

    suspend fun forMonth(ym: YearMonth = YearMonth.now(zone)): List<Obligation> {
        val dao = SafiApp.db.dao()
        val (from, to) = monthRange(ym)
        val now = System.currentTimeMillis()
        val isCurrent = ym == YearMonth.now(zone)
        val out = mutableListOf<Obligation>()

        for (b in dao.billsNow()) {
            var d = b.nextDue
            if (d < from) {
                if (isCurrent) {
                    out += Obligation(b.name, b.kind, b.amount, b.currency, Fx.toAed(b.amount, b.currency), d, true, b.id)
                }
                // move forward into this month to also count this month's occurrence
                var guard = 0
                while (d < from && guard < 500) {
                    d = step(d, b.frequency) ?: break
                    guard++
                }
                if (d < from) continue
            }
            var guard = 0
            while (d in from..to && guard < 60) {
                out += Obligation(b.name, b.kind, b.amount, b.currency, Fx.toAed(b.amount, b.currency), d, d < now && isCurrent && d.toLocalDate() < LocalDate.now(zone), b.id)
                d = step(d, b.frequency) ?: break
                guard++
            }
        }

        for (debt in dao.openDebtsNow().filter { it.direction == "i_owe" }) {
            val rem = debt.remaining
            if (rem <= 0) continue
            val inst = debt.monthlyInstallment
            if (inst != null && inst > 0) {
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
