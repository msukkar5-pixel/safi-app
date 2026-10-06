package com.mohamed.safi.ai

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import com.mohamed.safi.SafiApp
import com.mohamed.safi.apps.Apps
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
            appendLine("NOW: ${now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm EEEE", java.util.Locale.US))} (${zone.id})")
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
            val cp = com.mohamed.safi.data.Carpool.load()
            if (cp.members.isNotEmpty()) {
                val d0 = LocalDate.now(zone)
                appendLine("CARPOOL (me = ${cp.myName}, members ${cp.members.joinToString()}): " +
                    (0 until 14).map { d0.plusDays(it.toLong()) }.mapNotNull { d -> cp.driverFor(d)?.let { "$d ${d.dayOfWeek.toString().take(3)}=$it" } }.joinToString("; "))
            }
            runCatching { appendLine(com.mohamed.safi.fitness.Coach.assistantContext(com.mohamed.safi.SafiApp.instance)) }
            runCatching {
                val meds = com.mohamed.safi.health.HealthDb.dao.medsNow()
                if (meds.isNotEmpty()) appendLine("MEDICATIONS: " + meds.joinToString("; ") { "${it.name} ${it.dose} at ${it.times} ${it.withFood}" })
                val w = com.mohamed.safi.faith.Wird
                appendLine("QURAN WIRD: ${w.pagesPerDay} pages/day, today pages ${w.todayRange().first}-${w.todayRange().last}, done today: ${w.doneToday}, streak ${w.streak}")
            }
            runCatching {
                appendLine("PRAYER TIMES TODAY (${com.mohamed.safi.faith.Prayer.city}): " + com.mohamed.safi.faith.Prayer.today().times.joinToString(", ") { "${it.first} ${it.second.toLocalTime()}" })
                appendLine("QIBLA: ${com.mohamed.safi.faith.Prayer.qiblaBearing().toInt()}° from north")
            }
            runCatching {
                val x = com.mohamed.safi.extra.ExtraDb.dao
                val docs = x.docsNow()
                if (docs.isNotEmpty()) appendLine("DOCUMENTS: " + docs.joinToString("; ") { "${it.title} ${it.owner} expires ${it.expiry?.let { e -> isoLocal(e).take(10) } ?: "-"}" })
                val goals = x.goalsNow()
                if (goals.isNotEmpty()) appendLine("SAVING GOALS: " + goals.joinToString("; ") { "${it.name} ${fmt(it.saved)}/${fmt(it.target)} ${it.currency}" })
                val lessons = x.lessonsNow()
                if (lessons.isNotEmpty()) appendLine("KIDS LESSONS (EGP/month): " + lessons.joinToString("; ") { "${it.child} ${it.subject} ${it.teacher} ${fmt(it.monthlyFeeEgp)}" })
            }
            val today = Brief.todayLines()
            if (today.isNotEmpty()) appendLine("DUE SOON:\n" + today.joinToString("\n"))
        }
    }

    private val SYSTEM = """
You are "${com.mohamed.safi.AppName.v}", the personal assistant inside the user's own Android app. The user (name: ${com.mohamed.safi.SafiApp.prefs.userName.ifBlank { "unknown" }}) is Egyptian, lives and works in the UAE, spends mostly by card in AED, and sends money to his family in Egypt in EGP.
Reply in the same language/dialect the user used (Egyptian Arabic by default; English, Hindi, Urdu, French… if he writes in them). Short and direct, warm but no fluff. Use Western digits for numbers.

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
- {"type":"navigate","destination":"place name or address, keep Arabic/English as said, add city if obvious e.g. 'Dubai Mall, Dubai'","app":"waze|google (optional)"}
- {"type":"play_music","query":"song / artist / playlist","app":"anghami|spotify|youtube (optional)"}
- {"type":"open_app","name":"app name as installed, e.g. WhatsApp, Instagram, Careem"}
- {"type":"call","number":"phone number"}
- {"type":"whatsapp","number":"phone number or empty","text":"message text"}
- {"type":"web_search","query":""}
- {"type":"log_food","meal":"فطار|غدا|عشا|سناك|قبل التمرين|بعد التمرين","text":"what he ate with grams","kcal":0,"protein":0,"carbs":0,"fat":0}  (estimate the numbers yourself)
- {"type":"log_weight","kg":0}
- {"type":"log_water","cups":1}   (1 cup = 250 ml; a 500 ml bottle = 2)
- {"type":"add_supplement","name":"","dose":"","times":"08:00,21:00","note":""}
- {"type":"carpool_set","date":"YYYY-MM-DD","driver":"member name"}   (one-day swap)
- {"type":"carpool_off","date":"YYYY-MM-DD"}   (holiday, nobody drives)
- {"type":"open_screen","screen":"hifz|kidstv|study|kidsetup|app_guide|social|family|kids|ramadan|radio|tv|sleep|hisn|manasik|umrah|hajj|ruqyah|quiz|prayertracker|islamiccalendar|asmahusna|alerts|vitals|finance|vehicle|quran|quranaudio|wird|library|stories|bidaya|history|audiobooks|shaarawy|healthrecords|azkar|hadith|diary|prayer|fitness|carpool|documents|savings|lessons|zakat|bills|debts|transfers|reports|car|places|schedule|expenses"}
- {"type":"add_diary","text":"the diary text exactly as he said it, cleaned punctuation only","mood":"one emoji or empty"}   (when he says سجّل في مذكراتي / اكتب في المذكرات)
- {"type":"add_document","title":"","owner":"","expiry":"YYYY-MM-DD"}
- {"type":"add_medication","name":"","dose":"","times":"08:00, 20:00","with_food":"قبل الأكل|بعد الأكل|مع الأكل|","reason":"","end":"YYYY-MM-DD or empty"}
- {"type":"wird_done"}   (he finished today's Quran wird)
- {"type":"shaarawy","query":"surah or topic"}   (open Sheikh Shaarawy videos on YouTube)
- {"type":"add_saving","goal":"goal name","amount":0}   (money he put aside toward an existing goal)

Rules:
- For navigate / play_music / open_app / call / whatsapp: just do it, reply in a few words. You cannot pick a contact by name: if he says "كلم أحمد" without a number, ask for the number.
- Fitness questions: use FITNESS PROFILE and targets. You are not a doctor; for medical issues advise a doctor.
- Carpool: answer who drives from CARPOOL; "بدّلت مع أحمد يوم الخميس" = carpool_set for that date with the new driver (and the other date if he mentions it).
- "استلفت من X" = i_owe. "سلفت X" / "X مستلف مني" = owed_to_me. Create a reminder automatically comes with add_debt when there is a due date (the app does it).
- Relative dates ("بكرة", "الخميس الجاي", "آخر الشهر", "كمان ساعتين") must be converted using NOW. If no time given for a reminder, use 09:00.
- If he says he paid something in cash, method "cash". Guess the best category yourself.
- For questions (كام صرفت، مطلوب مني إيه، فين صرفت) compute from the data and answer with numbers; actions = [].
- "مطلوب مني إيه الشهر ده" → list OBLIGATIONS THIS MONTH with total in AED and EGP items with their AED value.
- If something essential is missing (e.g. amount), ask briefly and don't add the action.
- Confirm what you did in reply in one short line. Never invent data you don't have.
""".trimIndent()

    private val _busy = MutableStateFlow(false)
    /** True while a request is in flight; survives leaving and reopening the chat screen. */
    val busy: StateFlow<Boolean> = _busy

    /** Pulls "reply" out of a truncated or malformed JSON answer so raw JSON is never shown. */
    private fun replyFromBroken(raw: String): String? {
        val m = Regex("\"reply\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)").find(raw) ?: return null
        val body = m.groupValues[1]
        val text = runCatching { JSONObject("{\"r\":\"$body\"}").getString("r") }.getOrNull() ?: body
        return text.trim().takeIf { it.isNotBlank() }
    }

    suspend fun ask(ctx: Context, userText: String): Result {
        val dao = SafiApp.db.dao()
        _busy.value = true
        return try {
            dao.insertChat(ChatMsg(role = "user", text = userText))

            // Build alternating history ending with this user message.
            // Past assistant turns are replayed in the JSON shape so the model keeps answering in JSON.
            val history = dao.chatRecent(14)
            val msgs = JSONArray()
            var lastRole = ""
            val parts = mutableListOf<String>()
            fun flush() {
                if (lastRole.isNotEmpty() && parts.isNotEmpty()) {
                    val content = if (lastRole == "assistant")
                        JSONObject().put("reply", parts.joinToString("\n")).put("actions", JSONArray()).toString()
                    else parts.joinToString("\n")
                    msgs.put(JSONObject().put("role", lastRole).put("content", content))
                }
                parts.clear()
            }
            for (m in history) {
                if (msgs.length() == 0 && lastRole.isEmpty() && m.role != "user") continue
                if (m.role != lastRole) {
                    flush()
                    lastRole = m.role
                }
                parts += m.text
            }
            flush()

            val system = SYSTEM + "\n\n=== USER DATA ===\n" + context()
            val raw = Claude.call(system, msgs, SafiApp.prefs.model, 4096, json = true)
            val json = Claude.extractJson(raw)
            val reply = json?.optString("reply")?.trim()?.takeIf { it.isNotBlank() && it != "null" }
                ?: replyFromBroken(raw)
                ?: (if (json != null) "تمام" else raw.trim())
            val actions = json?.optJSONArray("actions") ?: JSONArray()
            val done = execute(ctx, actions)
            dao.insertChat(ChatMsg(role = "assistant", text = reply, actions = done.joinToString("\n")))
            Result(reply, done)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val msg = e.message ?: "حصلت مشكلة"
            runCatching { dao.insertChat(ChatMsg(role = "assistant", text = "⚠️ $msg")) }
            Result("⚠️ $msg", emptyList())
        } finally {
            _busy.value = false
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
            val type = a.str("type")
            val before = done.size
            val outcome = runCatching {
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
                            .putExtra(AlarmClock.EXTRA_MESSAGE, a.str("label").ifBlank { "${com.mohamed.safi.AppName.v}" })
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
                            .putExtra(AlarmClock.EXTRA_MESSAGE, a.str("label").ifBlank { "${com.mohamed.safi.AppName.v}" })
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
                        com.mohamed.safi.data.Obligations.markInstalmentPaid(d.id)
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
                        val billId = dao.upsertBill(b)
                        runCatching { com.mohamed.safi.data.Obligations.setAnchor(billId, next) }
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
                    "navigate" -> {
                        val dest = a.str("destination")
                        if (dest.isNotBlank()) done += "✓ ${Apps.navigate(ctx, dest, a.str("app").ifBlank { null })}: $dest"
                    }
                    "play_music" -> {
                        val q = a.str("query")
                        if (q.isNotBlank()) done += Apps.playMusic(ctx, q, a.str("app").ifBlank { null })?.let { "✓ $it: $q" } ?: "✗ مش قادر أشغل: $q"
                    }
                    "open_app" -> {
                        val name = a.str("name")
                        done += Apps.open(ctx, name)?.let { "✓ فتحت $it" } ?: "✗ مش لاقي تطبيق اسمه $name"
                    }
                    "call" -> if (Apps.dial(ctx, a.str("number"))) done += "✓ اتصال ${a.str("number")}"
                    "whatsapp" -> if (Apps.whatsapp(ctx, a.str("number"), a.str("text"))) done += "✓ واتساب جاهز، دوس إرسال"
                    "web_search" -> if (Apps.webSearch(ctx, a.str("query"))) done += "✓ بحث: ${a.str("query")}"
                    "log_food" -> {
                        val f = com.mohamed.safi.fitness.FoodEntry(
                            meal = a.str("meal"), text = a.str("text").ifBlank { "أكل" },
                            kcal = a.dbl("kcal") ?: 0.0, protein = a.dbl("protein") ?: 0.0,
                            carbs = a.dbl("carbs") ?: 0.0, fat = a.dbl("fat") ?: 0.0,
                        )
                        com.mohamed.safi.fitness.Fit.dao.insertFood(f)
                        done += "✓ أكل: ${f.text} — ${f.kcal.toInt()} سعر، ${f.protein.toInt()} بروتين"
                    }
                    "log_weight" -> {
                        val kg = a.dbl("kg") ?: return@runCatching
                        com.mohamed.safi.fitness.Fit.dao.insertWeight(com.mohamed.safi.fitness.WeightEntry(kg = kg))
                        done += "✓ الوزن: ${fmt(kg)} كجم"
                    }
                    "log_water" -> {
                        val day = LocalDate.now(zone).toString()
                        val cur = com.mohamed.safi.fitness.Fit.dao.waterNow(day)?.cups ?: 0
                        val n = (cur + a.optInt("cups", 1)).coerceAtLeast(0)
                        com.mohamed.safi.fitness.Fit.dao.setWater(com.mohamed.safi.fitness.WaterDay(day, n))
                        done += "✓ المية: $n/${com.mohamed.safi.fitness.Fit.prefs.waterTarget} كوباية"
                    }
                    "add_supplement" -> {
                        val name = a.str("name")
                        if (name.isNotBlank()) {
                            com.mohamed.safi.fitness.Supps.save(ctx, com.mohamed.safi.fitness.Supplement(name = name, dose = a.str("dose"), times = a.str("times"), note = a.str("note")))
                            done += "✓ مكمل: $name ${a.str("times")}"
                        }
                    }
                    "open_screen" -> {
                        val sc = a.str("screen")
                        if (sc.isNotBlank()) { com.mohamed.safi.ui.UiBus.pendingRoute.value = sc; done += "✓ فتحت" }
                    }
                    "add_medication" -> {
                        val n = a.str("name")
                        if (n.isNotBlank()) {
                            com.mohamed.safi.health.Meds.save(ctx, com.mohamed.safi.health.Medication(name = n, dose = a.str("dose"), times = a.str("times"), withFood = a.str("with_food"), reason = a.str("reason"), endDate = parseIso(a.str("end"))))
                            done += "✓ دوا: $n ${a.str("times")}"
                        }
                    }
                    "wird_done" -> { com.mohamed.safi.faith.Wird.markDone(); done += "✓ الورد اتسجل، ربنا يتقبل" }
                    "shaarawy" -> {
                        val q = a.str("query")
                        if (q.isNotBlank()) { com.mohamed.safi.faith.Shaarawy.open(ctx, com.mohamed.safi.faith.Shaarawy.topicSearch(q)); done += "✓ الشعراوي: $q" }
                    }
                    "add_diary" -> {
                        val t = a.str("text")
                        if (t.isNotBlank()) {
                            com.mohamed.safi.diary.DiaryDb.dao.upsert(com.mohamed.safi.diary.DiaryEntry(text = t, mood = a.str("mood")))
                            done += "✓ اتسجل في مذكراتك"
                        }
                    }
                    "add_document" -> {
                        val t = a.str("title")
                        if (t.isNotBlank()) {
                            com.mohamed.safi.extra.ExtraDb.dao.upsertDoc(com.mohamed.safi.extra.Doc(title = t, owner = a.str("owner"), expiry = parseIso(a.str("expiry"))))
                            done += "✓ مستند: $t"
                        }
                    }
                    "add_saving" -> {
                        val goals = com.mohamed.safi.extra.ExtraDb.dao.goalsNow()
                        val g = goals.firstOrNull { it.name == a.str("goal") } ?: goals.firstOrNull { it.name.contains(a.str("goal")) || a.str("goal").contains(it.name) } ?: goals.singleOrNull()
                        val amt = a.dbl("amount") ?: 0.0
                        if (g != null && amt != 0.0) {
                            com.mohamed.safi.extra.ExtraDb.dao.upsertGoal(g.copy(saved = (g.saved + amt).coerceAtLeast(0.0)))
                            done += "✓ ${g.name}: ${money(g.saved + amt, g.currency)} من ${money(g.target, g.currency)}"
                        }
                    }
                    "carpool_set", "carpool_off" -> {
                        val d = runCatching { LocalDate.parse(a.str("date").take(10)) }.getOrNull() ?: return@runCatching
                        val cp = com.mohamed.safi.data.Carpool.load(ctx)
                        val updated = if (a.str("type") == "carpool_off") cp.copy(skips = cp.skips + d, overrides = cp.overrides - d)
                        else {
                            val who = cp.members.firstOrNull { it == a.str("driver") } ?: cp.members.firstOrNull { it.contains(a.str("driver")) || a.str("driver").contains(it) } ?: a.str("driver")
                            cp.copy(overrides = cp.overrides + (d to who), skips = cp.skips - d)
                        }
                        com.mohamed.safi.data.Carpool.save(ctx, updated)
                        done += if (a.str("type") == "carpool_off") "✓ $d إجازة" else "✓ $d: ${updated.overrides[d]} هيسوق"
                    }
                    else -> {}
                }
                Unit
            }
            outcome.exceptionOrNull()?.let { if (it is CancellationException) throw it }
            if (outcome.isFailure || done.size == before) done += "✗ ${type.ifBlank { "?" }}"
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
