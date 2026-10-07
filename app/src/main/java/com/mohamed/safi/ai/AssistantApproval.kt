package com.mohamed.safi.ai

import android.content.Context
import androidx.core.content.edit
import com.mohamed.safi.SafiApp
import org.json.JSONArray
import org.json.JSONObject

/**
 * Holds a short-lived destructive action proposed by the assistant until the user
 * confirms it in a separate message. It is local-only and expires automatically.
 */
object AssistantApproval {
    private const val PREFS = "athar_assistant_approval"
    private const val KEY = "pending"
    private const val TTL_MS = 10 * 60 * 1000L

    data class Pending(val actions: JSONArray, val summary: String, val createdAt: Long)

    private fun prefs() = SafiApp.instance.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized
    fun offer(actions: JSONArray, summary: String) {
        if (actions.length() == 0) return
        prefs().edit {
            putString(KEY, JSONObject()
                .put("actions", actions)
                .put("summary", summary.take(180))
                .put("createdAt", System.currentTimeMillis())
                .toString())
        }
    }

    @Synchronized
    fun pending(): Pending? {
        val o = runCatching { JSONObject(prefs().getString(KEY, "") ?: "") }.getOrNull() ?: return null
        val createdAt = o.optLong("createdAt")
        if (createdAt <= 0L || System.currentTimeMillis() - createdAt > TTL_MS) {
            clear()
            return null
        }
        val actions = o.optJSONArray("actions") ?: return null
        return Pending(actions, o.optString("summary"), createdAt)
    }

    @Synchronized
    fun clear() = prefs().edit { remove(KEY) }

    fun isConfirm(text: String): Boolean {
        val t = text.lowercase().trim()
        return listOf("تأكيد", "اكد", "أكّد", "متأكد", "تمام امسح", "ايوه امسح", "أيوه امسح", "yes delete", "confirm delete").any { it == t || t.contains(it) }
    }

    fun isCancel(text: String): Boolean {
        val t = text.lowercase().trim()
        return listOf("إلغاء", "الغ", "بلاش", "لا تمسح", "cancel", "no").any { it == t || t.contains(it) }
    }
}
