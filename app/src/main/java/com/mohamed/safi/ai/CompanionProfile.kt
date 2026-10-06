package com.mohamed.safi.ai

import android.content.Context
import androidx.core.content.edit
import com.mohamed.safi.SafiApp
import org.json.JSONObject

/**
 * Explicit, local-only preferences for the user's companion.
 * No audio, chat transcript, contact, or family data is stored here.
 */
object CompanionProfile {
    private const val PREFS = "athar_companion_profile"
    private const val KEY = "explicit_preferences"

    private fun prefs() = SafiApp.instance.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized
    fun remember(topic: String, preference: String) {
        val cleanTopic = topic.trim().take(60)
        val cleanPreference = preference.trim().take(240)
        if (cleanTopic.isBlank() || cleanPreference.isBlank()) return
        val current = runCatching { JSONObject(prefs().getString(KEY, "{}") ?: "{}") }.getOrElse { JSONObject() }
        current.put(cleanTopic, cleanPreference)
        prefs().edit { putString(KEY, current.toString()) }
    }

    @Synchronized
    fun forget(topic: String) {
        val cleanTopic = topic.trim().take(60)
        if (cleanTopic.isBlank()) return
        val current = runCatching { JSONObject(prefs().getString(KEY, "{}") ?: "{}") }.getOrElse { JSONObject() }
        current.remove(cleanTopic)
        prefs().edit { putString(KEY, current.toString()) }
    }

    @Synchronized
    fun clear() = prefs().edit { remove(KEY) }

    fun snapshot(): String {
        val current = runCatching { JSONObject(prefs().getString(KEY, "{}") ?: "{}") }.getOrElse { JSONObject() }
        if (current.length() == 0) return "لا توجد تفضيلات شخصية محفوظة."
        val out = StringBuilder()
        current.keys().asSequence().toList().sorted().forEach { key ->
            out.append("- ").append(key).append(": ").append(current.optString(key)).append('\n')
        }
        return out.toString().trim()
    }
}
