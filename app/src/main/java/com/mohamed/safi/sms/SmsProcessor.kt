package com.mohamed.safi.sms

import android.content.Context
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
import java.security.MessageDigest

object SmsProcessor {

    fun hash(sender: String, body: String): String {
        val md = MessageDigest.getInstance("SHA-1")
        return md.digest((sender.lowercase().trim() + "|" + body.trim()).toByteArray())
            .joinToString("") { String.format(java.util.Locale.US, "%02x", it) }
    }

    private const val CONSUMED_KEY = "consumed_sms_hashes"
    private fun sp() = SafiApp.instance.getSharedPreferences("safi_sms", Context.MODE_PRIVATE)

    /** Hashes of bank SMS whose expense was turned into something else (e.g. an Egypt transfer) and must not be re-imported. */
    fun markConsumed(hash: String) {
        val set = HashSet(sp().getStringSet(CONSUMED_KEY, emptySet()) ?: emptySet())
        if (set.add(hash)) sp().edit().putStringSet(CONSUMED_KEY, set).apply()
    }

    fun isConsumed(hash: String): Boolean =
        sp().getStringSet(CONSUMED_KEY, emptySet())?.contains(hash) == true

    /** Already imported (still in the DB) or consumed by a conversion. */
    suspend fun isDuplicate(hash: String): Boolean =
        SafiApp.db.dao().countHash(hash) > 0 || isConsumed(hash)

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
    suspend fun process(ctx: Context, sender: String, body: String, time: Long, notify: Boolean, useClaude: Boolean = true, trusted: Boolean = false): Expense? {
        val dao = SafiApp.db.dao()
        val h = hash(sender, body)
        if (dao.countHash(h) > 0 || isConsumed(h)) return null
        val bank = BankSmsParser.bankFor(sender, body) ?: (if (trusted) "" else return null)
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
            bank = (p.bank + (p.card?.let { " •$it" } ?: "")).trim(),
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

    private val months = mapOf(
        "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4, "may" to 5, "jun" to 6,
        "jul" to 7, "aug" to 8, "sep" to 9, "oct" to 10, "nov" to 11, "dec" to 12,
    )
    private val dNum = Regex("\\b(\\d{1,2})[/\\-.](\\d{1,2})[/\\-.](\\d{2,4})(?:[ ,T]+(\\d{1,2}):(\\d{2}))?")
    private val dMon = Regex("\\b(\\d{1,2})[ \\-]?([A-Za-z]{3})[A-Za-z]*[ \\-,]?(\\d{2,4})(?:[ ,T]+(\\d{1,2}):(\\d{2}))?")
    private val dIso = Regex("\\b(\\d{4})-(\\d{2})-(\\d{2})(?:[ T](\\d{1,2}):(\\d{2}))?")

    /** Date written inside the bank message, if any (UAE banks use day/month order). */
    fun dateIn(body: String): Long? {
        fun build(y: Int, m: Int, d: Int, h: String?, mi: String?): Long? = runCatching {
            val year = if (y < 100) 2000 + y else y
            java.time.LocalDateTime.of(year, m, d, h?.toIntOrNull() ?: 12, mi?.toIntOrNull() ?: 0)
                .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        }.getOrNull()
        val now = System.currentTimeMillis()
        val t = dIso.find(body)?.let { m -> build(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt(), m.groupValues[4].ifEmpty { null }, m.groupValues[5].ifEmpty { null }) }
            ?: dNum.find(body)?.let { m -> build(m.groupValues[3].toInt(), m.groupValues[2].toInt(), m.groupValues[1].toInt(), m.groupValues[4].ifEmpty { null }, m.groupValues[5].ifEmpty { null }) }
            ?: dMon.find(body)?.let { m ->
                val mon = months[m.groupValues[2].lowercase()] ?: return@let null
                build(m.groupValues[3].toInt(), mon, m.groupValues[1].toInt(), m.groupValues[4].ifEmpty { null }, m.groupValues[5].ifEmpty { null })
            }
        return t?.takeIf { it <= now + 86_400_000L && it > now - 400L * 86_400_000L }
    }

    /** Split pasted text into separate bank messages. */
    fun split(text: String): List<String> {
        val blocks = text.split(Regex("\\n\\s*\\n")).map { it.trim() }.filter { it.isNotEmpty() }
        if (blocks.size > 1) return blocks
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val txLines = lines.filter { BankSmsParser.looksTransactional(it) }
        return if (txLines.size > 1) txLines else listOf(text.trim())
    }

    /** [skipped]: messages that weren't transactions; [duplicates]: already recorded before. */
    data class ImportResult(val added: List<Expense>, val skipped: Int, val duplicates: Int = 0)

    /** Messages Mohamed shared or pasted into the app. */
    suspend fun processText(ctx: Context, text: String, useClaude: Boolean = Claude.hasKey): ImportResult {
        val added = mutableListOf<Expense>()
        var skipped = 0
        var duplicates = 0
        for (msg in split(com.mohamed.safi.ui.normalizeDigits(text))) {
            if (runCatching { isDuplicate(hash("shared", msg)) }.getOrDefault(false)) {
                duplicates++
                continue
            }
            val time = dateIn(msg) ?: System.currentTimeMillis()
            val e = runCatching { process(ctx, "shared", msg, time, notify = false, useClaude = useClaude, trusted = true) }.getOrNull()
            if (e != null) added += e else skipped++
        }
        return ImportResult(added, skipped, duplicates)
    }
}
