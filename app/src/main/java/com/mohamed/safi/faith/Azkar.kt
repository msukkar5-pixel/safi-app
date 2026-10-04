package com.mohamed.safi.faith

import android.content.Context
import com.mohamed.safi.SafiApp
import com.mohamed.safi.data.Reminder
import com.mohamed.safi.data.millis
import com.mohamed.safi.data.zone
import com.mohamed.safi.notify.ReminderScheduler
import org.json.JSONArray
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

data class Zikr(val text: String, val count: Int, val desc: String, val ref: String)
data class ZikrCategory(val name: String, val items: List<Zikr>)

/** Morning/evening adhkar, after-prayer, sleep, waking, Quranic and Prophetic duas (Hisn al-Muslim selection). */
object Azkar {
    @Volatile private var cache: List<ZikrCategory>? = null

    fun all(ctx: Context = SafiApp.instance): List<ZikrCategory> {
        cache?.let { return it }
        val arr = JSONArray(ctx.assets.open("azkar.json").bufferedReader().use { it.readText() })
        val list = (0 until arr.length()).map { i ->
            val c = arr.getJSONObject(i)
            val items = c.getJSONArray("items")
            ZikrCategory(
                c.getString("name"),
                (0 until items.length()).map { k ->
                    val z = items.getJSONObject(k)
                    Zikr(z.getString("text"), z.optInt("count", 1).coerceAtLeast(1), z.optString("desc"), z.optString("ref"))
                },
            )
        }
        cache = list
        return list
    }

    fun icon(name: String) = when {
        "الصباح" in name -> "☀️"
        "المساء" in name -> "🌙"
        "الصلاة" in name -> "🕌"
        "تسابيح" in name -> "📿"
        "النوم" in name -> "🛏"
        "الاستيقاظ" in name -> "🌅"
        "قرآنية" in name -> "📖"
        "الأنبياء" in name -> "🤲"
        else -> "🤲"
    }

    // ---------- tasbeeh ----------
    private fun sp() = SafiApp.instance.getSharedPreferences("safi_azkar", Context.MODE_PRIVATE)
    var tasbeehCount: Int get() = sp().getInt("tasbeeh", 0); set(v) = sp().edit().putInt("tasbeeh", v).apply()
    var tasbeehTotal: Long get() = sp().getLong("tasbeehTotal", 0); set(v) = sp().edit().putLong("tasbeehTotal", v).apply()

    // ---------- reminders (daily) ----------
    suspend fun reminderTime(kind: String): LocalTime? {
        val r = SafiApp.db.dao().remindersFor("azkar", if (kind == "morning") 1 else 2).firstOrNull() ?: return null
        return java.time.Instant.ofEpochMilli(r.time).atZone(zone).toLocalTime()
    }

    suspend fun setReminder(ctx: Context, kind: String, time: LocalTime?) {
        val dao = SafiApp.db.dao()
        val ref = if (kind == "morning") 1L else 2L
        dao.remindersFor("azkar", ref).forEach { ReminderScheduler.cancel(ctx, it.id); dao.deleteReminder(it) }
        if (time == null) return
        var at = LocalDate.now(zone).atTime(time)
        if (!at.isAfter(LocalDateTime.now(zone))) at = at.plusDays(1)
        val r = Reminder(
            title = if (kind == "morning") "☀️ أذكار الصباح" else "🌙 أذكار المساء",
            time = at.millis(), repeat = "daily", refType = "azkar", refId = ref,
        )
        val id = dao.upsertReminder(r)
        ReminderScheduler.schedule(ctx, r.copy(id = id))
    }
}
