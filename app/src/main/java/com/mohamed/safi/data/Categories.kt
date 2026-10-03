package com.mohamed.safi.data

import com.mohamed.safi.SafiApp

object Cats {
    const val FOOD = "مطاعم وأكل"
    const val GROCERY = "سوبرماركت"
    const val FUEL = "بنزين"
    const val CAR = "صيانة السيارة"
    const val TRANSPORT = "سالك ومواصلات"
    const val RENT = "إيجار"
    const val UTILITIES = "كهرباء ومياه"
    const val TELECOM = "اتصالات وإنترنت"
    const val HEALTH = "صيدلية وصحة"
    const val CLOTHES = "ملابس وتسوق"
    const val ONLINE = "تسوق أونلاين واشتراكات"
    const val FUN = "ترفيه"
    const val EDU = "تعليم"
    const val CASH = "سحب نقدي"
    const val FEES = "رسوم بنكية"
    const val TRANSFER = "تحويلات"
    const val INCOME = "دخل"
    const val OTHER = "أخرى"

    val expense = listOf(
        FOOD, GROCERY, FUEL, CAR, TRANSPORT, RENT, UTILITIES, TELECOM, HEALTH,
        CLOTHES, ONLINE, FUN, EDU, CASH, FEES, TRANSFER, OTHER,
    )
    val all = expense + INCOME
    val carCats = listOf(FUEL, CAR, TRANSPORT)
}

object Categorizer {
    private val keywords: List<Pair<String, List<String>>> = listOf(
        Cats.TRANSFER to listOf(
            "al ansari", "alansari", "ansari exchange", "lulu exchange", "lulu money", "uae exchange",
            "al fardan", "fardan", "western union", "wise", "remitly", "joyalukkas exchange",
            "orient exchange", "sharaf exchange", "botim", "instapay", "exchange house", "remittance",
        ),
        Cats.FUEL to listOf("enoc", "adnoc", "eppco", "emarat", "petrol", "fuel", "service station", "بترول", "بنزين"),
        Cats.TRANSPORT to listOf(
            "salik", "rta", "nol", "careem", "uber", "taxi", "parking", "mawaqif", "darb", "metro",
            "hafilat", "valtrans", "مواقف", "سالك",
        ),
        Cats.GROCERY to listOf(
            "carrefour", "lulu", "union coop", "coop", "spinneys", "waitrose", "choithrams", "nesto",
            "grandiose", "viva", "al maya", "west zone", "westzone", "supermarket", "hypermarket",
            "grocery", "baqala", "kibsons", "instashop", "barakat", "geant", "safeer", "madina",
            "كارفور", "لولو", "بقالة",
        ),
        Cats.FOOD to listOf(
            "talabat", "deliveroo", "noon food", "zomato", "careem food", "mcdonald", "kfc", "starbucks",
            "cafe", "coffee", "restaurant", "burger", "pizza", "shawarma", "tim hortons", "costa",
            "dunkin", "hardee", "subway", "popeyes", "bakery", "kitchen", "grill", "mandi", "مطعم", "كافيه",
        ),
        Cats.HEALTH to listOf(
            "pharmacy", "pharma", "aster", "boots", "bin sina", "binsina", "clinic", "hospital",
            "medical", "dental", "medcare", "nmc", "mediclinic", "optical", "صيدلية", "مستشفى",
        ),
        Cats.TELECOM to listOf("etisalat", "e&", "du", "virgin mobile", "emirates integrated", "اتصالات"),
        Cats.UTILITIES to listOf("dewa", "sewa", "addc", "aadc", "fewa", "etihad water", "empower", "tabreed", "كهرباء"),
        Cats.ONLINE to listOf(
            "amazon", "noon", "namshi", "shein", "aliexpress", "temu", "apple.com", "itunes", "google",
            "netflix", "spotify", "anghami", "shahid", "osn", "youtube", "playstation", "microsoft",
            "anthropic", "openai", "claude",
        ),
        Cats.CLOTHES to listOf(
            "centrepoint", "max fashion", "splash", "h&m", "zara", "ikea", "home centre", "ace hardware",
            "dragon mart", "landmark", "lc waikiki", "decathlon", "sharaf dg", "jumbo", "emax", "virgin megastore",
        ),
        Cats.FUN to listOf("vox", "reel cinema", "cinema", "novo", "global village", "dubai parks", "magic planet"),
        Cats.CAR to listOf(
            "autopro", "service center", "tyre", "tire", "garage", "car wash", "al futtaim motors",
            "arabian automobiles", "auto", "motors", "تغيير زيت",
        ),
        Cats.EDU to listOf("school", "academy", "institute", "university", "course", "udemy", "coursera", "مدرسة"),
        Cats.FEES to listOf("annual fee", "late fee", "charges", "fee "),
    )

    fun merchantKey(m: String): String =
        m.lowercase()
            .replace(Regex("[^a-z؀-ۿ& ]"), " ")
            .trim()
            .split(Regex("\\s+"))
            .filter { it.length > 1 }
            .take(3)
            .joinToString(" ")

    private fun matches(text: String, kw: String): Boolean {
        if (kw.length <= 3) {
            val tokens = text.split(Regex("[^a-z0-9&؀-ۿ]+"))
            return tokens.any { it == kw }
        }
        return text.contains(kw)
    }

    fun byKeywords(text: String): String? {
        val t = text.lowercase()
        for ((cat, kws) in keywords) if (kws.any { matches(t, it) }) return cat
        return null
    }

    suspend fun categorize(merchant: String, fallbackText: String = ""): String {
        if (merchant.isNotBlank()) {
            val key = merchantKey(merchant)
            if (key.isNotBlank()) SafiApp.db.dao().ruleFor(key)?.let { return it.category }
            byKeywords(merchant)?.let { return it }
        }
        if (fallbackText.isNotBlank()) byKeywords(fallbackText)?.let { return it }
        return Cats.OTHER
    }

    /** Remember the category Mohamed chose for this merchant. */
    suspend fun learn(merchant: String, category: String) {
        val key = merchantKey(merchant)
        if (key.isNotBlank()) SafiApp.db.dao().upsertRule(MerchantRule(key, category))
    }
}
