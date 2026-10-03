package com.mohamed.safi.ai

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import com.mohamed.safi.SafiApp
import com.mohamed.safi.data.Bill
import com.mohamed.safi.data.Categorizer
import com.mohamed.safi.data.Cats
import com.mohamed.safi.data.ChatMsg
import com.mohamed.safi.data.Debt
import com.mohamed.safi.data.Expense
import com.mohamed.safi.data.Fx
import com.mohamed.safi.data.Obligations
import com.mohamed.safi.data.Reminder
import com.mohamed.safi.data.Transfer
import com.mohamed.safi.data.fmt
import com.mohamed.safi.data.isoLocal
import com.mohamed.safi.data.money
import com.mohamed.safi.data.monthRange
import com.mohamed.safi.data.parseIso
import com.mohamed.safi.data.remaining
import com.mohamed.safi.data.zone
import com.mohamed.safi.location.LocationLogger
import com.mohamed.safi.location.LocationService
import com.mohamed.safi.notify.Brief
import com.mohamed.safi.notify.ReminderScheduler
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/**
 * Mohamed's personal assistant. Understands Egyptian Arabic (typed or spoken),
 * answers questions from his own data and turns requests into actions.
 */
object Assistant {

    data class Result(val reply: String, val done: List<String>)

    private suspend fun context(): String {
        val dao = SafiApp.db.dao()
        val prefs = SafiApp.prefs
        val now = LocalDateTime.now(zone)
        val ym = YearMonth.now(zone)
        val (mFrom, mTo) = monthRange(ym)
        val (pFrom, pTo) = monthRange(ym.minusMonths(1))
        val month = dao.expensesBetweenNow(mFrom, mTo)
        val prev = dao.expensesBetweenNow(pFrom, pTo)
        val recent = dao.expensesBetweenNow(System.currentTimeMillis() - 60L * 86_400_000L, System.currentTimeMillis() + 86_400_000L)
        val transfers = dao.transfersBetweenNow(pFrom, mTo)
        val bills = dao.billsNow()
        val debts = dao.openDebtsNow()
        val reminders = dao.activeRemindersNow().filter { it.time < System.currentTimeMillis() + 30L * 86_400_000L }
        val obligations = Obligations.forMonth(ym)

        fun byCat(list: List<Expense>) = list.filter { !it.isIncome }.groupBy { it.category }
            .mapValues { e -> e.value.sumOf { it.amountAed } }.entries.sortedByDescending { it.value }
            .joinToString("; ") { "${it.key}: ${fmt(it.value)}" }

        return buildString {
            appendLine("NOW: ${now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm EEEE"))} (Asia/Dubai)")
            appendLine("USER: ${prefs.userName}, lives in UAE, family in Egypt. Default currency AED.")
            appendLine("RATE: 1 AED = ${prefs.egpPerAed} EGP")
            appendLine("EXPENSE CATEGORIES: ${Cats.expense.joinToString(", ")}")
            appendLine("EGYPT TRANSFER CATEGORIES: ${prefs.transferCats.joinToString(", ")}")
            appendLine()
            appendLine("THIS MONTH spent ${fmt(month.filter { !it.isIncome }.sumOf { it.amountAed })} AED, income ${fmt(month.filter { it.isIncome }.sumOf { it.amountAed })} AED. By category: ${byCat(month)}")
            appendLine("LAST MONTH spent ${fmt(prev.filter { !it.isIncome }.sumOf { it.amountAed })} AED. By category: ${byCat(prev)}")
            appendLine()
            appendLine("EXPENSES last 60 days (id | date | AED | category | merchant | method | place | note):")
            recent.take(200).forEach {
                appendLine("#${it.id} | ${isoLocal(it.time)} | ${if (it.isIncome) "+" else ""}${fmt(it.amountAed)}${if (it.currency != "AED") " (${fmt(it.amount)} ${it.currency})" else ""} | ${it.category} | ${it.merchant} | ${it.method} | ${it.placeName} | ${it.note}")
            }
            appendLine()
            appendLine("EGYPT TRANSFERS (this+last month):")
            transfers.forEach { appendLine("#${it.id} | ${isoLocal(it.time)} | ${fmt(it.amountEgp)} EGP = ${fmt(it.amountAed)} AED | ${it.category} | ${it.recipient} | ${it.note}") }
            appendLine()
            appendLine("RECURRING BILLS:")
            bills.forEach { appendLine("#${it.id} | ${it.name} | ${it.kind} | ${fmt(it.amount)} ${it.currency} | ${it.frequency} | next due ${isoLocal(it.nextDue)}") }
            appendLine()
            appendLine("OBLIGATIONS THIS MONTH (total ${fmt(obligations.sumOf { it.amountAed })} AED):")
            obligations.forEach { appendLine("- ${it.title}: ${fmt(it.amount)} ${it.currency} due ${isoLocal(it.due)}${if (it.overdue) " OVERDUE" else ""}") }
            appendLine()
            appendLine("OPEN DEBTS:")
            debts.forEach {
                appendLine("#${it.id} | ${if (it.direction == "i_owe") "I OWE" else "OWED TO ME"} | ${it.person} | remaining ${fmt(it.remaining)} ${it.currency} of ${fmt(it.amount)} | due ${it.dueDate?.let { d -> isoLocal(d) } ?: "-"}${it.monthlyInstallment?.let { m -> " | monthly $m" } ?: ""} | ${it.note}")
            }
            appendLine()
            appendLine("UPCOMING REMINDERS/APPOINTMENTS (30 days):")
            reminders.forEach { appendLine("#${it.id} | ${isoLocal(it.time)} | ${it.kind} | ${it.title} | repeat ${it.repeat} | ${it.location}") }
            appendLine()
            appendLine("CAR: odometer ${prefs.odometer} km")
            val today = Brief.todayLines()
            if (today.isNotEmpty()) appendLine("DUE SOON:\n" + today.joinToString("\n"))
        }
    }

    private val SYSTEM = """
You are "صافي", the personal assistant inside Mohamed's own Android app. Mohamed is Egyptian, lives and works in the UAE, spends mostly by card in AED, and sends money to his family in Egypt in EGP.
Speak Egyptian Arabic, short and direct, warm but no fluff. Use Western digits for numbers.

You can read his data (given below) and take actions. ALWAYS answer with ONE JSON object only, no text outside it:
{"reply": "what you say to Mohamed", "actions": [ ... ]}

Available actions (use exact keys; omit optional keys you don't know):
- {"type":"add_expense","amount":50,"currency":"AED","category":"<one of EXPENSE CATEGORIES>","merchant":"","method":"cash|card","note":"","datetime":"YYYY-MM-DDTHH:MM"}
- {"type":"add_income","amount":0,"currency":"AED","merchant":"","note":"","datetime":"..."}
- {"type":"edit_expense","id":123, plus any fields to change: amount,currency,category,merchant,method,note,datetime}
- {"type":"delete_expense","id":123}
- {"type":"add_transfer","amount_egp":5000,"category":"<one of EGYPT TRANSFER CATEGORIES>","recipient":"","fees_aed":0,"note":"","datetime":"..."}  (if he gives AED instead, set "amount_aed")
- {"type":"add_reminder","title":"","datetime":"YYYY-MM-DDTHH:MM","repeat":"none|daily|weekly|monthly|yearly","kind":"reminder|appointment","before_min":0,"location":"","note":"","alarm":false}
- {"type":"done_reminder","id":1}
- {"type":"set_alarm","hour":6,"minute":30,"label":"","days":[1,2,3,4,5]}   (days: 1=Sunday … 7=Saturday; omit days for one-time)
- {"type":"set_timer","seconds":600,"label":""}
- {"type":"add_debt","person":"","amount":0,"currency":"AED","direction":"i_owe|owed_to_me","due":"YYYY-MM-DD","monthly_installment":0,"note":""}
- {"type":"pay_debt","id":1,"amount":0}   (repayment; use the debt id from OPEN DEBTS)
- {"type":"add_bill","name":"","kind":"bill|transfer|subscription|installment","category":"","amount":0,"currency":"AED","frequency":"monthly|quarterly|yearly|weekly|once","next_due":"YYYY-MM-DD","remind_days":2}
  (for monthly money to family in Egypt use kind "transfer", currency "EGP" and an EGYPT TRANSFER CATEGORY)
- {"type":"pay_bill","id":1,"amount":0}   (amount optional, defaults to bill amount)
- {"type":"set_odometer","km":0}

Rules:
- "استلفت من X" = i_owe. "سلفت X" / "X مستلف مني" = owed_to_me. Create a reminder automatically comes with add_debt when there is a due date (the app does it).
- Relative dates ("بكرة", "الخميس الجاي", "آخر الشهر", "كمان ساعتين") must be converted using NOW. If no time given for a reminder, use 09:00.
- If he says he paid something in cash, method "cash". Guess the best category yourself.
- For questions (كام صرفت، مطلوب مني إيه، فين صرفت) compute from the data and answer with numbers; actions = [].
- "مطلوب مني إيه الشهر ده" → list OBLIGATIONS THIS MONTH with total in AED and EGP items with their AED value.
- If something essential is missing (e.g. amount), ask briefly and don't add the action.
- Confirm what you did in reply in one short line. Never invent data you don't have.
""".trimIndent()

    suspend fun ask(ctx: Context, userText: String): Result {
        val dao = SafiApp.db.dao()
        dao.insertChat(ChatMsg(role = "user", text = userText))

        // Build alternating history ending with this user message
        val history = dao.chatRecent(14)
        val msgs = JSONArray()
        var lastRole = ""
        val sb = StringBuilder()
        fun flush() {
            if (lastRole.isNotEmpty() && sb.isNotEmpty()) {
                msgs.put(JSONObject().put("role", lastRole).put("content", sb.toString()))
            }
            sb.clear()
        }
        for (m in history) {
            if (msgs.length() == 0 && lastRole.isEmpty() && m.role != "user") continue
            if (m.role != lastRole) {
                flush()
                lastRole = m.role
            } else sb.append("\n")
            sb.append(m.text)
        }
        flush()

        val system = SYSTEM + "\n\n=== MOHAMED'S DATA ===\n" + context()
        return try {
            val raw = Claude.call(system, msgs, SafiApp.prefs.model, 2048)
            val json = Claude.extractJson(raw)
            val reply = json?.optString("reply")?.takeIf { it.isNotBlank() } ?: raw.trim()
            val actions = json?.optJSONArray("actions") ?: JSONArray()
            val done = execute(ctx, actions)
            dao.insertChat(ChatMsg(role = "assistant", text = reply, actions = done.joinToString("\n")))
            Result(reply, done)
        } catch (e: Exception) {
            val msg = e.message ?: "حصلت مشكلة"
            dao.insertChat(ChatMsg(role = "assistant", text = "⚠️ $msg"))
            Result("⚠️ $msg", emptyList())
        }
    }

    private fun JSONObject.str(k: String) = optString(k, "").let { if (it == "null") "" else it.trim() }
    private fun JSONObject.dbl(k: String): Double? = if (has(k) && !isNull(k)) optDouble(k).takeIf { !it.isNaN() } else null

    suspend fun execute(ctx: Context, actions: JSONArray): List<String> {
        val dao = SafiApp.db.dao()
        val prefs = SafiApp.prefs
        val done = mutableListOf<String>()
        for (i in 0 until actions.length()) {
            val a = actions.optJSONObject(i) ?: continue
            runCatching {
                when (a.str("type")) {
                    "add_expense", "add_income" -> {
                        val income = a.str("type") == "add_income"
                        val amount = a.dbl("amount") ?: return@runCatching
                        val cur = a.str("currency").ifBlank { "AED" }.uppercase()
                        val merchant = a.str("merchant")
                        var cat = if (income) Cats.INCOME else a.str("category")
                        if (cat !in Cats.all) cat = Categorizer.categorize(merchant, a.str("note"))
                        val time = parseIso(a.str("datetime")) ?: System.currentTimeMillis()
                        val here = if (time > System.currentTimeMillis() - 3_600_000L) LocationService.currentLocation(ctx) else null
                        val place = if (here == null) LocationLogger.placeAt(time) else null
                        val e = Expense(
                            amount = amount, currency = cur, amountAed = Fx.toAed(amount, cur), category = cat,
                            merchant = merchant, note = a.str("note"),
                            method = a.str("method").ifBlank { if (income) "card" else "cash" },
                            time = time, lat = here?.latitude ?: place?.lat, lng = here?.longitude ?: place?.lng,
                            placeName = place?.placeName ?: "", source = "voice", isIncome = income,
                        )
                        dao.insertExpense(e)
                        if (!income) Brief.checkBudget(ctx, cat, e.amountAed)
                        done += (if (income) "✓ دخل " else "✓ مصروف ") + money(amount, cur) + " — $cat"
                    }
                    "edit_expense" -> {
                        val old = dao.expense(a.optLong("id")) ?: return@runCatching
                        val amount = a.dbl("amount") ?: old.amount
                        val cur = a.str("currency").ifBlank { old.currency }.uppercase()
                        val cat = a.str("category").takeIf { it in Cats.all } ?: old.category
                        val updated = old.copy(
                            amount = amount, currency = cur, amountAed = Fx.toAed(amount, cur), category = cat,
                            merchant = a.str("merchant").ifBlank { old.merchant },
                            method = a.str("method").ifBlank { old.method },
                            note = a.str("note").ifBlank { old.note },
                            time = parseIso(a.str("datetime")) ?: old.time,
                        )
                        dao.updateExpense(updated)
                        if (cat != old.category && updated.merchant.isNotBlank()) Categorizer.learn(updated.merchant, cat)
                        done += "✓ اتعدل: ${money(amount, cur)} — $cat"
                    }
                    "delete_expense" -> {
                        val old = dao.expense(a.optLong("id")) ?: return@runCatching
                        dao.deleteExpense(old)
                        done += "✓ اتمسح مصروف ${money(old.amount, old.currency)}"
                    }
                    "add_transfer" -> {
                        val rate = prefs.egpPerAed
                        val egp = a.dbl("amount_egp") ?: a.dbl("amount_aed")?.let { it * rate } ?: return@runCatching
                        val cats = prefs.transferCats
                        val cat = a.str("category").takeIf { it.isNotBlank() } ?: cats.last()
                        if (cat !in cats) prefs.transferCats = cats.dropLast(1) + cat + cats.last()
                        dao.upsertTransfer(
                            Transfer(
                                amountEgp = egp, rate = rate, amountAed = egp / rate, feesAed = a.dbl("fees_aed") ?: 0.0,
                                category = cat, recipient = a.str("recipient"), note = a.str("note"),
                                time = parseIso(a.str("datetime")) ?: System.currentTimeMillis(),
                            ),
                        )
                        done += "✓ تحويل ${money(egp, "EGP")} (${money(egp / rate)}) — $cat"
                    }
                    "add_reminder" -> {
                        val time = parseIso(a.str("datetime")) ?: return@runCatching
                        val r = Reminder(
                            title = a.str("title").ifBlank { "تذكير" }, note = a.str("note"), time = time,
                            repeat = a.str("repeat").ifBlank { "none" }, kind = a.str("kind").ifBlank { "reminder" },
                            location = a.str("location"), remindBeforeMin = a.optInt("before_min", 0),
                            alarm = a.optBoolean("alarm", false),
                        )
                        val id = dao.upsertReminder(r)
                        ReminderScheduler.schedule(ctx, r.copy(id = id))
                        done += "✓ ${if (r.kind == "appointment") "ميعاد" else "تذكير"}: ${r.title} — ${com.mohamed.safi.data.dateTimeStr(time)}"
                    }
                    "done_reminder" -> {
                        val r = dao.reminder(a.optLong("id")) ?: return@runCatching
                        dao.upsertReminder(r.copy(done = true))
                        ReminderScheduler.cancel(ctx, r.id)
                        done += "✓ خلصت: ${r.title}"
                    }
                    "set_alarm" -> {
                        val days = a.optJSONArray("days")
                        val i2 = Intent(AlarmClock.ACTION_SET_ALARM)
                            .putExtra(AlarmClock.EXTRA_HOUR, a.optInt("hour"))
                            .putExtra(AlarmClock.EXTRA_MINUTES, a.optInt("minute"))
                            .putExtra(AlarmClock.EXTRA_MESSAGE, a.str("label").ifBlank { "صافي" })
                            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        if (days != null && days.length() > 0) {
                            val list = ArrayList<Int>()
                            for (d in 0 until days.length()) list += days.optInt(d)
                            i2.putIntegerArrayListExtra(AlarmClock.EXTRA_DAYS, list)
                        }
                        ctx.startActivity(i2)
                        done += "✓ منبه ${"%02d:%02d".format(a.optInt("hour"), a.optInt("minute"))}"
                    }
                    "set_timer" -> {
                        val i2 = Intent(AlarmClock.ACTION_SET_TIMER)
                            .putExtra(AlarmClock.EXTRA_LENGTH, a.optInt("seconds", 60))
                            .putExtra(AlarmClock.EXTRA_MESSAGE, a.str("label").ifBlank { "صافي" })
                            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        ctx.startActivity(i2)
                        done += "✓ مؤقت ${a.optInt("seconds", 60) / 60} دقيقة"
                    }
                    "add_debt" -> {
                        val amount = a.dbl("amount") ?: return@runCatching
                        val due = parseIso(a.str("due"))
                        val d = Debt(
                            person = a.str("person").ifBlank { "؟" }, amount = amount,
                            currency = a.str("currency").ifBlank { "AED" }.uppercase(),
                            direction = if (a.str("direction") == "owed_to_me") "owed_to_me" else "i_owe",
                            dueDate = due, monthlyInstallment = a.dbl("monthly_installment")?.takeIf { it > 0 },
                            note = a.str("note"),
                        )
                        val id = dao.upsertDebt(d)
                        Debts.scheduleReminder(ctx, d.copy(id = id))
                        done += "✓ ${if (d.direction == "i_owe") "سلفة عليك لـ" else "سلفة ليك عند"} ${d.person}: ${money(amount, d.currency)}"
                    }
                    "pay_debt" -> {
                        val d = dao.debt(a.optLong("id")) ?: return@runCatching
                        val amt = a.dbl("amount") ?: d.remaining
                        val updated = Debts.pay(ctx, d, amt)
                        done += "✓ سداد ${money(amt, d.currency)} — ${d.person} (باقي ${money(updated.remaining, d.currency)})"
                    }
                    "add_bill" -> {
                        val amount = a.dbl("amount") ?: return@runCatching
                        val next = parseIso(a.str("next_due")) ?: LocalDate.now(zone).plusMonths(1).withDayOfMonth(1).atTime(9, 0)
                            .atZone(zone).toInstant().toEpochMilli()
                        val kind = a.str("kind").ifBlank { "bill" }
                        val b = Bill(
                            name = a.str("name").ifBlank { "فاتورة" }, kind = kind,
                            category = a.str("category").ifBlank { if (kind == "transfer") prefs.transferCats.first() else Cats.OTHER },
                            amount = amount, currency = a.str("currency").ifBlank { if (kind == "transfer") "EGP" else "AED" }.uppercase(),
                            frequency = a.str("frequency").ifBlank { "monthly" }, nextDue = next,
                            remindDaysBefore = a.optInt("remind_days", 2),
                        )
                        dao.upsertBill(b)
                        done += "✓ التزام: ${b.name} ${money(amount, b.currency)} (${freqLabel(b.frequency)})"
                    }
                    "pay_bill" -> {
                        val b = dao.billsNow().firstOrNull { it.id == a.optLong("id") } ?: return@runCatching
                        val amt = a.dbl("amount") ?: b.amount
                        Bills.markPaid(ctx, b, amt)
                        done += "✓ اتدفع: ${b.name} ${money(amt, b.currency)}"
                    }
                    "set_odometer" -> {
                        prefs.odometer = a.optInt("km", prefs.odometer)
                        done += "✓ عداد العربية: ${prefs.odometer} كم"
                    }
                    else -> {}
                }
                Unit
            }
        }
        return done
    }

    fun freqLabel(f: String) = when (f) {
        "weekly" -> "أسبوعي"
        "monthly" -> "شهري"
        "quarterly" -> "كل 3 شهور"
        "yearly" -> "سنوي"
        "once" -> "مرة واحدة"
        else -> f
    }
}

object Debts {
    suspend fun scheduleReminder(ctx: Context, d: Debt) {
        val dao = SafiApp.db.dao()
        dao.remindersFor("debt", d.id).forEach {
            ReminderScheduler.cancel(ctx, it.id)
            dao.deleteReminder(it)
        }
        val due = d.dueDate ?: return
        if (d.closed) return
        val at = java.time.Instant.ofEpochMilli(due).atZone(zone).toLocalDate().atTime(10, 0).atZone(zone).toInstant().toEpochMilli()
        val title = if (d.direction == "i_owe") "ميعاد سداد ${money(d.remaining, d.currency)} لـ ${d.person}"
        else "${d.person} المفروض يرجعلك ${money(d.remaining, d.currency)}"
        val r = Reminder(
            title = title, time = at, kind = "reminder", remindBeforeMin = 0,
            repeat = if (d.monthlyInstallment != null) "monthly" else "none",
            refType = "debt", refId = d.id,
        )
        val id = dao.upsertReminder(r)
        ReminderScheduler.schedule(ctx, r.copy(id = id))
    }

    suspend fun pay(ctx: Context, d: Debt, amount: Double): Debt {
        val dao = SafiApp.db.dao()
        val paid = d.paid + amount
        val updated = d.copy(paid = paid, closed = paid >= d.amount - 0.009)
        dao.upsertDebt(updated)
        if (updated.closed) {
            dao.remindersFor("debt", d.id).forEach {
                ReminderScheduler.cancel(ctx, it.id)
                dao.upsertReminder(it.copy(done = true))
            }
        }
        return updated
    }
}

object Bills {
    /** Records the payment (as an expense, or as an Egypt transfer) and moves the bill to its next due date. */
    suspend fun markPaid(ctx: Context, b: Bill, amount: Double) {
        val dao = SafiApp.db.dao()
        val prefs = SafiApp.prefs
        val now = System.currentTimeMillis()
        if (b.kind == "transfer" || b.currency == "EGP" && b.category in prefs.transferCats) {
            val rate = prefs.egpPerAed
            val egp = if (b.currency == "EGP") amount else amount * rate
            dao.upsertTransfer(Transfer(amountEgp = egp, rate = rate, amountAed = egp / rate, category = b.category, note = b.name, time = now))
        } else {
            val cat = if (b.category in Cats.all) b.category else Categorizer.categorize(b.name)
            dao.insertExpense(
                Expense(
                    amount = amount, currency = b.currency, amountAed = Fx.toAed(amount, b.currency), category = cat,
                    merchant = b.name, method = "card", time = now, source = "bill", note = "دفع التزام",
                ),
            )
        }
        val next = Obligations.step(b.nextDue, b.frequency)
        if (next == null) dao.upsertBill(b.copy(active = false, lastPaid = now))
        else dao.upsertBill(b.copy(nextDue = next, lastPaid = now))
    }
}
