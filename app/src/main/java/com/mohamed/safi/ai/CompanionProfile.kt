package com.mohamed.safi.ai

import android.content.Context
import androidx.core.content.edit
import com.mohamed.safi.SafiApp
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Explicit, local-only companion profile.
 * Nothing is learned from raw chat, audio, contacts, family cards, or background activity.
 * A memory exists only after the user explicitly asks the companion to remember it.
 */
object CompanionProfile {
    private const val PREFS = "athar_companion_profile"
    private const val KEY = "explicit_preferences" // legacy, kept for backward compatibility
    private const val MEMORIES = "profile_memories_v1"
    private const val ALERT_POLICY = "alert_policy_v1"

    data class Memory(val id: String, val category: String, val value: String, val at: Long)
    data class AlertPolicy(
        val supportOn: Boolean = true,
        val voiceOn: Boolean = true,
        val quietFrom: Int = -1,
        val quietUntil: Int = -1,
        val cooldownMinutes: Int = 10,
    )

    private fun prefs() = SafiApp.instance.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun clean(s: String, max: Int) = s.trim().replace(Regex("\\s+"), " ").take(max)

    fun alertPolicy(): AlertPolicy {
        val o = runCatching { JSONObject(prefs().getString(ALERT_POLICY, "{}") ?: "{}") }.getOrElse { JSONObject() }
        return AlertPolicy(
            o.optBoolean("supportOn", true), o.optBoolean("voiceOn", true),
            o.optInt("quietFrom", -1), o.optInt("quietUntil", -1), o.optInt("cooldown", 10).coerceIn(1, 120),
        )
    }

    @Synchronized
    fun setAlertPolicy(supportOn: Boolean? = null, voiceOn: Boolean? = null, quietFrom: Int? = null, quietUntil: Int? = null, cooldownMinutes: Int? = null) {
        val old = alertPolicy()
        val next = AlertPolicy(
            supportOn ?: old.supportOn, voiceOn ?: old.voiceOn,
            quietFrom ?: old.quietFrom, quietUntil ?: old.quietUntil,
            (cooldownMinutes ?: old.cooldownMinutes).coerceIn(1, 120),
        )
        prefs().edit {
            putString(ALERT_POLICY, JSONObject().put("supportOn", next.supportOn).put("voiceOn", next.voiceOn)
                .put("quietFrom", next.quietFrom).put("quietUntil", next.quietUntil).put("cooldown", next.cooldownMinutes).toString())
        }
    }

    private fun quietNow(hour: Int, p: AlertPolicy): Boolean = when {
        p.quietFrom !in 0..23 || p.quietUntil !in 0..23 || p.quietFrom == p.quietUntil -> false
        p.quietFrom < p.quietUntil -> hour in p.quietFrom until p.quietUntil
        else -> hour >= p.quietFrom || hour < p.quietUntil
    }

    @Synchronized
    fun autoSupportAllowed(kind: String, now: Long = System.currentTimeMillis()): Boolean {
        val p = alertPolicy()
        if (!p.supportOn || (kind == "voice" && !p.voiceOn)) return false
        if (quietNow(java.time.LocalTime.now(com.mohamed.safi.data.zone).hour, p)) return false
        val last = prefs().getLong("last_support_$kind", 0)
        return now - last >= p.cooldownMinutes * 60_000L
    }

    @Synchronized
    fun recordAutoSupport(kind: String, now: Long = System.currentTimeMillis()) {
        prefs().edit { putLong("last_support_$kind", now) }
    }

    fun prefersConciseAlerts(): Boolean = memories().any {
        it.category in setOf("tone", "notifications", "تنبيهات") &&
            listOf("مختصر", "قليل", "من غير تفاصيل", "short").any { word -> word in it.value.lowercase() }
    }

    @Synchronized
    fun remember(topic: String, preference: String) {
        val cleanTopic = clean(topic, 60)
        val cleanPreference = clean(preference, 240)
        if (cleanTopic.isBlank() || cleanPreference.isBlank()) return
        val current = runCatching { JSONObject(prefs().getString(KEY, "{}") ?: "{}") }.getOrElse { JSONObject() }
        current.put(cleanTopic, cleanPreference)
        prefs().edit { putString(KEY, current.toString()) }
    }

    /** Saves a user-approved fact in a category such as tone, routine, goals, likes, or avoid. */
    @Synchronized
    fun rememberMemory(category: String, value: String): Memory? {
        val c = clean(category, 40).ifBlank { "general" }
        val v = clean(value, 300)
        if (v.isBlank()) return null
        val m = Memory(UUID.randomUUID().toString().take(8), c, v, System.currentTimeMillis())
        val a = memoriesJson()
        a.put(JSONObject().put("id", m.id).put("category", m.category).put("value", m.value).put("at", m.at))
        prefs().edit { putString(MEMORIES, a.toString()) }
        return m
    }

    @Synchronized
    fun forget(topic: String) {
        val cleanTopic = clean(topic, 60)
        if (cleanTopic.isBlank()) return
        val current = runCatching { JSONObject(prefs().getString(KEY, "{}") ?: "{}") }.getOrElse { JSONObject() }
        current.remove(cleanTopic)
        prefs().edit { putString(KEY, current.toString()) }
    }

    @Synchronized
    fun forgetMemory(idOrCategory: String): Int {
        val target = clean(idOrCategory, 60)
        if (target.isBlank()) return 0
        val old = memoriesJson()
        val keep = JSONArray()
        var removed = 0
        for (i in 0 until old.length()) {
            val o = old.optJSONObject(i) ?: continue
            if (o.optString("id") == target || o.optString("category") == target) removed++ else keep.put(o)
        }
        prefs().edit { putString(MEMORIES, keep.toString()) }
        return removed
    }

    @Synchronized
    fun clearMemories() = prefs().edit { remove(KEY).remove(MEMORIES) }

    /** Full reset used only by an explicit profile reset or isolated test setup. */
    @Synchronized
    fun clear() = prefs().edit { remove(KEY).remove(MEMORIES).remove(ALERT_POLICY) }

    fun voiceMonitoringAllowed(): Boolean {
        val p = alertPolicy()
        return p.supportOn && p.voiceOn
    }

    fun memories(): List<Memory> {
        val a = memoriesJson()
        return (0 until a.length()).mapNotNull { i ->
            a.optJSONObject(i)?.let { o ->
                Memory(o.optString("id"), o.optString("category"), o.optString("value"), o.optLong("at"))
            }
        }.sortedWith(compareBy<Memory> { it.category }.thenBy { it.at })
    }

    private fun memoriesJson() = runCatching {
        JSONArray(prefs().getString(MEMORIES, "[]") ?: "[]")
    }.getOrElse { JSONArray() }

    fun snapshot(): String {
        val legacy = runCatching { JSONObject(prefs().getString(KEY, "{}") ?: "{}") }.getOrElse { JSONObject() }
        val advanced = memories()
        val policy = alertPolicy()
        if (legacy.length() == 0 && advanced.isEmpty()) {
            return "لا توجد تفضيلات أو ذكريات شخصية محفوظة. سياسة الدعم التلقائي: ${if (policy.supportOn) "مفعلة" else "موقوفة"}، الهدوء ${policy.quietFrom}:${policy.quietUntil}."
        }
        val out = StringBuilder()
        if (legacy.length() > 0) {
            out.append("تفضيلات أساسية:\n")
            legacy.keys().asSequence().toList().sorted().forEach { key ->
                out.append("- ").append(key).append(": ").append(legacy.optString(key)).append('\n')
            }
        }
        if (advanced.isNotEmpty()) {
            out.append("ملف شخصي صريح:\n")
            advanced.forEach { out.append("- [").append(it.category).append("] ").append(it.value).append(" {id=").append(it.id).append("}\n") }
        }
        out.append("سياسة التنبيهات: الدعم ").append(if (policy.supportOn) "مفعل" else "موقوف")
            .append("، النطق ").append(if (policy.voiceOn) "مفعل" else "موقوف")
            .append("، الهدوء ").append(policy.quietFrom).append(":").append(policy.quietUntil)
            .append("، الفاصل ").append(policy.cooldownMinutes).append(" دقيقة.")
        return out.toString().trim()
    }
}
