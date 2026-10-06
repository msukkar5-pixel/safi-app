package com.mohamed.safi.data

import android.content.Context
import androidx.core.content.edit

class Prefs(context: Context) {
    private val p = context.getSharedPreferences("safi", Context.MODE_PRIVATE)

    private fun s(k: String, d: String) = p.getString(k, d) ?: d
    private fun putS(k: String, v: String) = p.edit { putString(k, v) }
    private fun b(k: String, d: Boolean) = p.getBoolean(k, d)
    private fun putB(k: String, v: Boolean) = p.edit { putBoolean(k, v) }
    private fun i(k: String, d: Int) = p.getInt(k, d)
    private fun putI(k: String, v: Int) = p.edit { putInt(k, v) }
    private fun l(k: String, d: Long) = p.getLong(k, d)
    private fun putL(k: String, v: Long) = p.edit { putLong(k, v) }

    var userName: String get() = s("userName", ""); set(v) = putS("userName", v)

    /** Name the user gave the app / assistant. Existing custom names remain unchanged. */
    var appName: String get() = s("appName", "أثر").ifBlank { "أثر" }; set(v) = putS("appName", v.trim())

    // AI provider (any): anthropic | openai | gemini | deepseek | groq | openrouter | custom
    var aiProvider: String get() = s("aiProvider", "anthropic"); set(v) = putS("aiProvider", v)

    /** Key / models are stored per provider so switching keeps each one. */
    var apiKey: String
        get() = if (aiProvider == "anthropic") s("key_anthropic", s("apiKey", "")) else s("key_$aiProvider", "")
        set(v) = putS("key_$aiProvider", v.trim())
    var model: String
        get() = s("model_$aiProvider", if (aiProvider == "anthropic") s("model", "") else "").ifBlank { com.mohamed.safi.ai.Providers.get(aiProvider).model }
        set(v) = putS("model_$aiProvider", v.trim())
    var fastModel: String
        get() = s("fast_$aiProvider", if (aiProvider == "anthropic") s("fastModel", "") else "").ifBlank { com.mohamed.safi.ai.Providers.get(aiProvider).fastModel.ifBlank { model } }
        set(v) = putS("fast_$aiProvider", v.trim())
    var aiBaseUrl: String get() = s("baseUrl_$aiProvider", ""); set(v) = putS("baseUrl_$aiProvider", v.trim())

    fun keyOf(provider: String): String = if (provider == "anthropic") s("key_anthropic", s("apiKey", "")) else s("key_$provider", "")
    fun setKeyOf(provider: String, v: String) = putS("key_$provider", v.trim())

    fun allKeys(): Map<String, String> = p.all.filterKeys { it == "apiKey" || it.startsWith("key_") }.mapValues { it.value.toString() }

    // Exchange rates (base AED). egpPerAed = how many EGP for 1 AED
    var egpPerAed: Double
        get() = s("egpPerAed", "13.2").toDoubleOrNull() ?: 13.2
        set(v) = putS("egpPerAed", v.toString())
    var ratesJson: String get() = s("ratesJson", ""); set(v) = putS("ratesJson", v)
    var rateUpdated: Long get() = l("rateUpdated", 0); set(v) = putL("rateUpdated", v)
    var rateAuto: Boolean get() = b("rateAuto", true); set(v) = putB("rateAuto", v)

    // Egypt transfer categories
    var transferCats: List<String>
        get() = s("transferCats", "ماما,البيت,دروس الأولاد,الصيدلية,مدارس,أخرى")
            .split(",").map { it.trim() }.filter { it.isNotEmpty() }
        set(v) = putS("transferCats", v.joinToString(","))

    // SMS
    var smsOn: Boolean get() = b("smsOn", true); set(v) = putB("smsOn", v)
    var smsImported: Boolean get() = b("smsImported", false); set(v) = putB("smsImported", v)
    /** Extra SMS sender names to treat as bank messages (comma separated). */
    var extraSenders: String get() = s("extraSenders", ""); set(v) = putS("extraSenders", v)

    // Location
    var locationOn: Boolean get() = b("locationOn", false); set(v) = putB("locationOn", v)
    var locationIntervalMin: Int get() = i("locInterval", 5); set(v) = putI("locInterval", v)

    // Security / daily brief
    var lockOn: Boolean get() = b("lockOn", false); set(v) = putB("lockOn", v)
    var briefOn: Boolean get() = b("briefOn", true); set(v) = putB("briefOn", v)
    var briefHour: Int get() = i("briefHour", 8); set(v) = putI("briefHour", v)
    var familySyncOn: Boolean get() = b("familySyncOn", true); set(v) = putB("familySyncOn", v)
    var lastMonthlySummary: String get() = s("lastMonthly", ""); set(v) = putS("lastMonthly", v)
    var lastBriefDay: String get() = s("lastBriefDay", ""); set(v) = putS("lastBriefDay", v)

    // Car
    var odometer: Int get() = i("odometer", 0); set(v) = putI("odometer", v)
    var carName: String get() = s("carName", "عربيتي"); set(v) = putS("carName", v)
    var regExpiry: Long get() = l("regExpiry", 0); set(v) = putL("regExpiry", v)
    var insExpiry: Long get() = l("insExpiry", 0); set(v) = putL("insExpiry", v)
    var licenseExpiry: Long get() = l("licenseExpiry", 0); set(v) = putL("licenseExpiry", v)

    var onboarded: Boolean get() = b("onboarded", false); set(v) = putB("onboarded", v)
}
