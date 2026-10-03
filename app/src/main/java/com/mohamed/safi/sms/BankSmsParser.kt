package com.mohamed.safi.sms

import com.mohamed.safi.SafiApp

/**
 * Reads card / account alerts from Emirates NBD, ADCB and ADIB (and any extra sender
 * Mohamed adds in Settings). Formats change over time, so the parser is keyword based
 * and anything it can't read is handed to Claude by SmsProcessor.
 */
object BankSmsParser {

    data class Parsed(
        val bank: String,
        val amount: Double,
        val currency: String,
        val merchant: String,
        val isCredit: Boolean,
        val isCash: Boolean,
        val card: String?,
    )

    fun bankFor(sender: String, body: String): String? {
        val s = sender.lowercase().replace(Regex("[^a-z0-9]"), "")
        val b = body.lowercase()
        if ("enbd" in s || "emiratesnbd" in s || "emirates nbd" in b || "emiratesnbd" in b) return "Emirates NBD"
        if ("adcb" in s || "adcb" in b) return "ADCB"
        if ("adib" in s || "adib" in b) return "ADIB"
        val extra = SafiApp.prefs.extraSenders.split(",").map { it.trim().lowercase().replace(Regex("[^a-z0-9]"), "") }
            .filter { it.isNotEmpty() }
        extra.firstOrNull { it in s }?.let { return sender }
        return null
    }

    private val otpWords = listOf(
        "otp", "one time password", "one-time", "verification code", "passcode", "do not share",
        "رمز التحقق", "كلمة المرور", "لا تشارك", "activation code",
    )
    private val debitWords = listOf(
        "purchase", "spent", "debited", "debit", "withdrawn", "withdrawal", "paid", "payment of", "pos",
        "deducted", "used for", "was used", "has been used", "transaction of", "trx", "txn", "charged",
        "شراء", "خصم", "سحب", "دفع", "استخدام",
    )
    private val creditWords = listOf(
        "credited", "credit of", "received", "deposited", "deposit", "refund", "reversal", "salary",
        "returned", "إيداع", "ايداع", "إضافة", "استرداد", "راتب",
    )
    private val cashWords = listOf("atm", "cash withdrawal", "withdrawn", "withdrawal", "سحب نقدي", "صراف")
    private val skipWords = listOf(
        "declined", "insufficient", "not successful", "unsuccessful", "failed", "statement",
        "minimum payment", "due date", "payment due", "rejected", "مرفوض", "غير ناجحة",
    )

    private val curPattern = "(AED|EGP|USD|SAR|EUR|GBP|QAR|KWD|OMR|BHD|درهم|د\\.إ)"
    private val numPattern = "([0-9]{1,3}(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?|[0-9]+(?:\\.[0-9]{1,2})?)"
    private val amountBefore = Regex("$curPattern\\s*$numPattern", RegexOption.IGNORE_CASE)
    private val amountAfter = Regex("$numPattern\\s*$curPattern", RegexOption.IGNORE_CASE)
    private val balanceCut = Regex(
        "(avl\\.?|available|avail\\.?|current|outstanding|remaining)\\s*(bal|balance|limit|credit limit)|الرصيد|رصيدك|الحد المتاح",
        RegexOption.IGNORE_CASE,
    )
    private val merchantPatterns = listOf(
        Regex("\\bat\\s+(.+?)(?=\\s+on\\s|\\s+dated|\\s+for\\s+aed|\\.\\s|,|;|\\s+-\\s|\\s+avl|\\s+available|\\s+using|\\s+with\\s+card|$)", RegexOption.IGNORE_CASE),
        Regex("\\bto\\s+(.+?)(?=\\s+on\\s|\\.\\s|,|;|\\s+avl|\\s+available|\\s+ref|$)", RegexOption.IGNORE_CASE),
        Regex("merchant\\s*[:\\-]?\\s*(.+?)(?=\\.\\s|,|;|$)", RegexOption.IGNORE_CASE),
        Regex("(?:لدى|في|عند)\\s+(.+?)(?=\\s+بتاريخ|\\s+في\\s+\\d|\\.|،|,|$)"),
    )
    private val cardPattern = Regex("(?:card|بطاقة|account|a/c|acct|حساب)[^0-9]{0,25}(\\d{4})", RegexOption.IGNORE_CASE)

    fun isOtp(body: String): Boolean {
        val b = body.lowercase()
        return otpWords.any { it in b }
    }

    fun parse(bank: String, body: String): Parsed? {
        val lower = body.lowercase()
        if (isOtp(body)) return null
        if (skipWords.any { it in lower }) return null

        // Ignore everything from "available balance" onwards so we don't read the balance as the amount
        val cutAt = balanceCut.find(body)?.range?.first ?: body.length
        val main = body.substring(0, cutAt)

        val m1 = amountBefore.find(main)
        val m2 = amountAfter.find(main)
        val (cur, num) = when {
            m1 != null && (m2 == null || m1.range.first <= m2.range.first) -> m1.groupValues[1] to m1.groupValues[2]
            m2 != null -> m2.groupValues[2] to m2.groupValues[1]
            else -> return null
        }
        val amount = num.replace(",", "").toDoubleOrNull() ?: return null
        if (amount <= 0) return null
        val currency = when (cur.uppercase()) {
            "درهم", "د.إ" -> "AED"
            else -> cur.uppercase()
        }

        val hasDebit = debitWords.any { it in lower }
        val hasCredit = creditWords.any { it in lower }
        if (!hasDebit && !hasCredit) return null
        val isCredit = hasCredit && !hasDebit || (hasCredit && lower.indexOfFirst(creditWords) < lower.indexOfFirst(debitWords))
        val isCash = !isCredit && cashWords.any { it in lower }

        var merchant = ""
        for (p in merchantPatterns) {
            val m = p.find(main) ?: continue
            val candidate = m.groupValues[1].trim().trim('.', ',', '-', ':')
            if (candidate.isNotEmpty() && !candidate.first().isDigit() && candidate.length <= 60) {
                merchant = candidate
                break
            }
        }
        merchant = cleanMerchant(merchant)

        val card = cardPattern.find(body)?.groupValues?.get(1)
        return Parsed(bank, amount, currency, merchant, isCredit, isCash, card)
    }

    private fun String.indexOfFirst(words: List<String>): Int =
        words.map { indexOf(it) }.filter { it >= 0 }.minOrNull() ?: Int.MAX_VALUE

    fun cleanMerchant(m: String): String =
        m.replace(Regex("\\s{2,}"), " ")
            .replace(Regex("(?i)\\b(dubai|abu dhabi|sharjah|ajman|uae|ae|are)\\s*$"), "")
            .trim()
            .take(40)

    /** Looks like a money message even if we couldn't parse it (used to decide whether to ask Claude). */
    fun looksTransactional(body: String): Boolean {
        val lower = body.lowercase()
        if (isOtp(body)) return false
        return (amountBefore.containsMatchIn(body) || amountAfter.containsMatchIn(body)) &&
            (debitWords.any { it in lower } || creditWords.any { it in lower })
    }
}
