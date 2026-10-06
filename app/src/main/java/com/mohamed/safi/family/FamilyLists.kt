package com.mohamed.safi.family

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.mutableIntStateOf
import androidx.core.content.edit
import com.mohamed.safi.SafiApp
import com.mohamed.safi.data.zone
import com.mohamed.safi.notify.Notifier
import com.mohamed.safi.ui.tr
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * The family's shared shopping list and dates (birthdays, appointments, occasions). Anyone in the family adds or
 * ticks items; records merge by newest change inside the encrypted family card, like the kids' lessons.
 */
object FamilyLists {
    data class Item(val id: String, val text: String, val qty: String, val done: Boolean, val by: String)
    data class Event(val id: String, val title: String, val date: String, val kind: String, val note: String, val by: String) {
        /** Birthdays repeat every year. */
        fun nextDate(today: LocalDate = LocalDate.now(zone)): LocalDate? = runCatching {
            val d = LocalDate.parse(date)
            if (kind != "عيد ميلاد") d else d.withYear(today.year).let { if (it.isBefore(today)) it.plusYears(1) else it }
        }.getOrNull()
    }
    val kinds = listOf("عيد ميلاد", "موعد", "مناسبة", "اجتماع مدرسة")

    private fun sp() = SafiApp.instance.getSharedPreferences("safi_family_lists", Context.MODE_PRIVATE)
    val version = mutableIntStateOf(0)
    private val types = listOf("shop", "events")
    private fun raw(t: String): MutableMap<String, JSONObject> {
        val a = runCatching { JSONArray(sp().getString(t, "[]")) }.getOrDefault(JSONArray())
        return (0 until a.length()).map { a.getJSONObject(it) }.associateBy { it.optString("id") }.toMutableMap()
    }
    private fun save(t: String, m: Map<String, JSONObject>) {
        val cut = System.currentTimeMillis() - 60L * 86_400_000L
        sp().edit { putString(t, JSONArray(m.values.filter { !(it.optBoolean("del") && it.optLong("upd") < cut) }).toString()) }
    }
    private fun put(t: String, o: JSONObject) { val m = raw(t); o.put("upd", System.currentTimeMillis()); m[o.getString("id")] = o; save(t, m); version.intValue++ }
    fun delete(t: String, id: String) { val m = raw(t); m[id]?.let { it.put("del", true).put("upd", System.currentTimeMillis()); save(t, m); version.intValue++ } }
    private fun live(t: String) = raw(t).values.filter { !it.optBoolean("del") }
    private fun newId() = java.util.UUID.randomUUID().toString().take(10)
    private val me get() = Family.myName.ifBlank { tr("أنا") }

    fun items() = live("shop").map { Item(it.getString("id"), it.optString("text"), it.optString("qty"), it.optBoolean("done"), it.optString("by")) }
        .sortedWith(compareBy<Item> { it.done }.thenBy { it.text })
    fun addItem(text: String, qty: String) = put("shop", JSONObject().put("id", newId()).put("text", text.trim()).put("qty", qty.trim()).put("done", false).put("by", me))
    fun toggle(i: Item) = put("shop", JSONObject().put("id", i.id).put("text", i.text).put("qty", i.qty).put("done", !i.done).put("by", i.by))
    fun clearBought() = items().filter { it.done }.forEach { delete("shop", it.id) }

    fun events() = live("events").map { Event(it.getString("id"), it.optString("title"), it.optString("date"), it.optString("kind"), it.optString("note"), it.optString("by")) }
        .sortedBy { it.nextDate() ?: LocalDate.MAX }
    fun saveEvent(e: Event) { put("events", JSONObject().put("id", e.id.ifBlank { newId() }).put("title", e.title).put("date", e.date).put("kind", e.kind).put("note", e.note).put("by", e.by.ifBlank { me })); schedule(SafiApp.instance) }

    // ------------------------------------------------------------------ sync inside the family card
    fun export(): JSONObject? {
        val o = JSONObject()
        types.forEach { t -> raw(t).values.toList().takeIf { it.isNotEmpty() }?.let { o.put(t, JSONArray(it)) } }
        return if (o.length() == 0) null else o
    }
    fun merge(o: JSONObject) {
        var changed = false
        types.forEach { t ->
            val a = o.optJSONArray(t) ?: return@forEach
            val m = raw(t)
            for (i in 0 until a.length()) {
                val r = a.optJSONObject(i) ?: continue
                val id = r.optString("id").ifBlank { null } ?: continue
                if (r.optLong("upd") > (m[id]?.optLong("upd") ?: -1L)) { m[id] = r; changed = true }
            }
            save(t, m)
        }
        if (changed) { version.intValue++; schedule(SafiApp.instance) }
    }

    // ------------------------------------------------------------------ reminders: 9 am on the day and the evening before
    private fun pi(ctx: Context) = PendingIntent.getBroadcast(ctx, 8_970_001, Intent(ctx, FamilyListsReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun schedule(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        am.cancel(pi(ctx))
        if (events().isEmpty()) return
        val now = LocalDateTime.now(zone)
        val next = listOf(now.toLocalDate().atTime(9, 0), now.toLocalDate().atTime(20, 0), now.toLocalDate().plusDays(1).atTime(9, 0)).first { it.isAfter(now) }
        runCatching { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.atZone(zone).toInstant().toEpochMilli(), pi(ctx)) }
    }

    fun fire(ctx: Context) {
        val now = LocalDateTime.now(zone)
        val today = now.toLocalDate()
        val evening = now.hour >= 12
        val target = if (evening) today.plusDays(1) else today
        val list = events().filter { it.nextDate(today) == target }
        if (list.isNotEmpty()) Notifier.show(ctx, 8_970_002, Notifier.CH_REMIND,
            "👨‍👩‍👧 " + tr(if (evening) "بكرة في العيلة" else "النهارده في العيلة"),
            list.joinToString("، ") { (if (it.kind == "عيد ميلاد") "🎂 " else "📅 ") + it.title }, route = "familylists")
        schedule(ctx)
    }
}

class FamilyListsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) { runCatching { FamilyLists.fire(context) } }
}
