package com.mohamed.safi.sms

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Telephony
import androidx.core.content.ContextCompat
import com.mohamed.safi.SafiApp
import com.mohamed.safi.ai.Claude
import com.mohamed.safi.data.Categorizer
import com.mohamed.safi.data.Cats
import com.mohamed.safi.data.Expense
import com.mohamed.safi.data.Fx
import com.mohamed.safi.data.money
import com.mohamed.safi.location.LocationLogger
import com.mohamed.safi.notify.Brief
import com.mohamed.safi.notify.Notifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.security.MessageDigest

object SmsProcessor {

    fun hash(sender: String, body: String): String {
        val md = MessageDigest.getInstance("SHA-1")
        return md.digest((sender.lowercase().trim() + "|" + body.trim()).toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    /** Ask Claude to read a bank SMS the rule parser couldn't handle. */
    private suspend fun claudeParse(bank: String, body: String): BankSmsParser.Parsed? {
        if (!Claude.hasKey) return null
        val system = "You extract card/account transactions from UAE bank SMS. Reply with JSON only."
        val prompt = """
            SMS from $bank:
            $body

            Return JSON: {"is_transaction": bool, "is_credit": bool, "amount": number, "currency": "AED",
            "merchant": "short merchant name or empty", "is_cash_withdrawal": bool}
            is_transaction=false for OTPs, declined, balance or promotional messages.
        """.trimIndent()
        return runCatching {
            val res = Claude.call(system, org.json.JSONArray().put(Claude.userText(prompt)), SafiApp.prefs.fastModel, 300)
            val j = Claude.extractJson(res) ?: return null
            if (!j.optBoolean("is_transaction", false)) return null
            val amt = j.optDouble("amount", 0.0)
            if (amt <= 0) return null
            BankSmsParser.Parsed(
                bank, amt, j.optString("currency", "AED").ifBlank { "AED" }.uppercase(),
                BankSmsParser.cleanMerchant(j.optString("merchant", "")),
                j.optBoolean("is_credit", false), j.optBoolean("is_cash_withdrawal", false), null,
            )
        }.getOrNull()
    }

    /**
     * Turns a bank SMS into an Expense (or income). Returns the saved expense or null.
     * Safe to call twice with the same message: duplicates are ignored.
     */
    suspend fun process(ctx: Context, sender: String, body: String, time: Long, notify: Boolean, useClaude: Boolean = true): Expense? {
        val dao = SafiApp.db.dao()
        val h = hash(sender, body)
        if (dao.countHash(h) > 0) return null
        val bank = BankSmsParser.bankFor(sender, body) ?: return null
        val ruleParsed: BankSmsParser.Parsed? = BankSmsParser.parse(bank, body)
        val first: BankSmsParser.Parsed = ruleParsed
            ?: (if (useClaude && BankSmsParser.looksTransactional(body)) claudeParse(bank, body) else null)
            ?: return null
        var p: BankSmsParser.Parsed = first
        if (ruleParsed != null && p.merchant.isBlank() && useClaude && Claude.hasKey && !p.isCredit && !p.isCash) {
            val better = claudeParse(bank, body)
            if (better != null && better.merchant.isNotBlank()) p = p.copy(merchant = better.merchant)
        }

        val category = when {
            p.isCredit -> Cats.INCOME
            p.isCash -> Cats.CASH
            else -> Categorizer.categorize(p.merchant, body)
        }
        val place = LocationLogger.placeAt(time)
        val e = Expense(
            amount = p.amount,
            currency = p.currency,
            amountAed = Fx.toAed(p.amount, p.currency),
            category = category,
            merchant = p.merchant,
            method = if (p.isCash) "cash" else "card",
            bank = p.bank + (p.card?.let { " •$it" } ?: ""),
            time = time,
            lat = place?.lat,
            lng = place?.lng,
            placeName = place?.placeName ?: "",
            source = "sms",
            smsHash = h,
            isIncome = p.isCredit,
        )
        val id = dao.insertExpense(e)
        if (id <= 0) return null
        val saved = e.copy(id = id)
        if (notify) {
            val title = if (p.isCredit) "💰 فلوس دخلت" else "💳 اتسجل مصروف"
            val text = buildString {
                append(money(p.amount, p.currency))
                if (p.merchant.isNotBlank()) append(" — ${p.merchant}")
                append("\nالتصنيف: $category")
                if (saved.placeName.isNotBlank()) append(" • ${saved.placeName}")
                if (category == Cats.TRANSFER) append("\nلو ده تحويل لمصر، صنّفه من شاشة التحويلات")
            }
            Notifier.show(ctx, (id % 100_000).toInt() + 200_000, Notifier.CH_MONEY, title, text, route = "expenses")
            if (!p.isCredit) Brief.checkBudget(ctx, category, saved.amountAed)
        }
        return saved
    }

    fun hasReadPermission(ctx: Context) =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

    /** Import bank messages from the inbox (first run or manual re-import). */
    suspend fun importInbox(ctx: Context, days: Int = 90, useClaude: Boolean = false): Int {
        if (!hasReadPermission(ctx)) return 0
        val since = System.currentTimeMillis() - days * 86_400_000L
        var count = 0
        val cursor = ctx.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
            "${Telephony.Sms.DATE} > ?",
            arrayOf(since.toString()),
            "${Telephony.Sms.DATE} ASC",
        ) ?: return 0
        val rows = mutableListOf<Triple<String, String, Long>>()
        cursor.use { c ->
            while (c.moveToNext()) {
                val addr = c.getString(0) ?: continue
                val body = c.getString(1) ?: continue
                rows += Triple(addr, body, c.getLong(2))
            }
        }
        for ((addr, body, date) in rows) {
            if (process(ctx, addr, body, date, notify = false, useClaude = useClaude) != null) count++
        }
        SafiApp.prefs.smsImported = true
        return count
    }
}

private val smsScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        if (!SafiApp.prefs.smsOn) return
        val msgs = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (msgs.isEmpty()) return
        // Multi-part messages arrive as several parts from the same sender
        val grouped = msgs.groupBy { it.originatingAddress ?: "" }
        val pr = goAsync()
        smsScope.launch {
            try {
                for ((sender, parts) in grouped) {
                    val body = parts.joinToString("") { it.messageBody ?: "" }
                    val time = System.currentTimeMillis()
                    runCatching { SmsProcessor.process(context, sender, body, time, notify = true) }
                }
            } finally {
                pr.finish()
            }
        }
    }
}
