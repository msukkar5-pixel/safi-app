package com.mohamed.safi.kids

import android.content.Context
import androidx.compose.runtime.mutableIntStateOf
import androidx.core.content.edit
import com.mohamed.safi.SafiApp
import com.mohamed.safi.data.zone
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth

/** A child's profile. No real photo or full name is needed: a nickname and an emoji avatar are enough. */
data class Kid(val id: String, val name: String, val avatar: String, val age: Int, val fasting: Boolean = false)

/** What a done task adds to the Tree of Good and which passport stamp it gives. */
enum class Growth(val label: String, val icon: String) {
    LEAF("ورقة", "🍃"), FLOWER("زهرة", "🌸"), FRUIT("ثمرة", "🍎"), LIGHT("نور", "✨"), BRANCH("فرع", "🌿")
}

data class KidTask(val id: String, val title: String, val icon: String, val stars: Int, val growth: Growth, val minAge: Int = 3)

/**
 * The Kids section ("مدينة الخير"): daily tasks, stars, the Tree of Good and the passport of stamps, all year round.
 * Everything stays on this phone (shared prefs, included in the backup). A parent PIN guards the settings.
 */
object Kids {
    /** Task library; the parent picks which ones each child sees. Wording encourages, never scolds. */
    val library = listOf(
        KidTask("pray", "صلّيت الصلاة في وقتها", "🕌", 3, Growth.LIGHT, 7),
        KidTask("pray_try", "جرّبت أصلّي مع بابا أو ماما", "🤲", 2, Growth.LIGHT, 3),
        KidTask("quran", "قرأت أو سمعت صفحة قرآن", "📖", 2, Growth.LEAF, 3),
        KidTask("azkar_m", "قلت أذكار الصباح", "☀️", 1, Growth.LEAF, 5),
        KidTask("azkar_e", "قلت أذكار المساء", "🌙", 1, Growth.LEAF, 5),
        KidTask("sleep_dua", "قلت دعاء النوم", "🛏️", 1, Growth.LEAF, 3),
        KidTask("help", "ساعدت ماما أو بابا", "🤝", 3, Growth.FLOWER, 3),
        KidTask("table", "ساعدت في السفرة", "🍽️", 2, Growth.FLOWER, 4),
        KidTask("room", "رتبت أوضتي أو ألعابي", "🧸", 1, Growth.FLOWER, 3),
        KidTask("teeth", "غسلت سناني", "🪥", 1, Growth.FLOWER, 3),
        KidTask("kind", "قلت كلمة حلوة لحد", "💬", 1, Growth.FRUIT, 3),
        KidTask("share", "شاركت أكلي أو لعبي", "🍪", 3, Growth.FRUIT, 3),
        KidTask("sadaqa", "اتصدقت أو ساعدت محتاج", "💝", 3, Growth.FRUIT, 5),
        KidTask("relative", "كلمت قريب (جدو، تيتة، خالو…)", "📞", 2, Growth.BRANCH, 4),
        KidTask("homework", "خلصت واجبي", "✏️", 2, Growth.BRANCH, 6),
        KidTask("read", "قرأت قصة أو كتاب", "📚", 2, Growth.BRANCH, 4),
        KidTask("water", "مارميتش أكل ولا ميه (من غير إسراف)", "💧", 1, Growth.FRUIT, 4),
        KidTask("sorry", "اعتذرت لما غلطت", "🌈", 2, Growth.FRUIT, 4),
    )
    val avatars = listOf("👦", "👧", "🧒", "🌙", "⭐", "🦁", "🐱", "🐼", "🦋", "🌸", "🚀", "⚽")

    /** Rewards that open with stars (inside the app only — no purchases). */
    data class Reward(val stars: Int, val icon: String, val title: String)
    val rewards = listOf(
        Reward(10, "🏮", "فانوس ذهبي"), Reward(25, "🌟", "لقب: نجم البيت"), Reward(50, "🦁", "لقب: بطل التعاون"),
        Reward(80, "🌙", "هلال فضي"), Reward(120, "🏆", "كأس الخير"), Reward(180, "👑", "لقب: سفير الرحمة"),
        Reward(250, "🌳", "شجرة الجنة الكبيرة"), Reward(350, "🕌", "مسجد مدينة الخير"),
    )
    /** Ideas the parent can tie to stars at home (non-material). */
    val homeRewards = listOf("يختار قصة النوم", "يختار لعبة عائلية", "يختار أكلة العشا", "خروجة صغيرة", "وقت خاص مع بابا أو ماما")

    private fun sp() = SafiApp.instance.getSharedPreferences("safi_kids", Context.MODE_PRIVATE)
    /** Bumped on every change so screens refresh. */
    val version = mutableIntStateOf(0)
    private fun bump() { version.intValue++ }

    // ---------------------------------------------------------------- profiles
    fun kids(): List<Kid> = runCatching {
        val a = JSONArray(sp().getString("kids", "[]"))
        (0 until a.length()).map { a.getJSONObject(it).let { o -> Kid(o.getString("id"), o.getString("name"), o.optString("avatar", "🧒"), o.optInt("age", 6), o.optBoolean("fasting")) } }
    }.getOrDefault(emptyList())

    fun saveKid(k: Kid) {
        val list = kids().filter { it.id != k.id } + k
        sp().edit { putString("kids", JSONArray(list.map { JSONObject().put("id", it.id).put("name", it.name).put("avatar", it.avatar).put("age", it.age).put("fasting", it.fasting) }).toString()) }
        if (sp().getString("tasks_${k.id}", null) == null) setTasks(k.id, library.filter { k.age >= it.minAge }.take(7).map { it.id })
        bump()
    }

    fun deleteKid(id: String) {
        sp().edit {
            putString("kids", JSONArray(kids().filter { it.id != id }.map { JSONObject().put("id", it.id).put("name", it.name).put("avatar", it.avatar).put("age", it.age).put("fasting", it.fasting) }).toString())
            sp().all.keys.filter { it.endsWith("_$id") || it.contains("_${id}_") }.forEach { remove(it) }
        }
        bump()
    }

    fun tasks(kid: String): List<KidTask> {
        val ids = sp().getString("tasks_$kid", "")!!.split(",").filter { it.isNotBlank() }
        return ids.mapNotNull { i -> library.firstOrNull { it.id == i } }
    }
    fun setTasks(kid: String, ids: List<String>) { sp().edit { putString("tasks_$kid", ids.joinToString(",")) }; bump() }

    // ---------------------------------------------------------------- progress
    private fun dayKey(kid: String, d: LocalDate) = "done_${kid}_$d"
    fun doneOn(kid: String, d: LocalDate = LocalDate.now(zone)): Set<String> = sp().getStringSet(dayKey(kid, d), emptySet()) ?: emptySet()

    /** Marks a task done today (or undoes it). Returns the stars change. */
    fun toggle(kid: String, task: KidTask, d: LocalDate = LocalDate.now(zone)): Int {
        val done = doneOn(kid, d)
        val on = task.id !in done
        val delta = if (on) task.stars else -task.stars
        sp().edit {
            putStringSet(dayKey(kid, d), if (on) done + task.id else done - task.id)
            putInt("stars_$kid", (sp().getInt("stars_$kid", 0) + delta).coerceAtLeast(0))
            val gk = "growth_${kid}_${YearMonth.from(d)}_${task.growth.name}"
            putInt(gk, (sp().getInt(gk, 0) + if (on) 1 else -1).coerceAtLeast(0))
        }
        bump()
        return delta
    }

    fun stars(kid: String) = sp().getInt("stars_$kid", 0)
    /** This month's tree: how many leaves, flowers, fruits, lights and branches. */
    fun growth(kid: String, ym: YearMonth = YearMonth.now(zone)): Map<Growth, Int> = Growth.values().associateWith { sp().getInt("growth_${kid}_${ym}_${it.name}", 0) }

    /** Days this month with at least one task, for the passport. */
    fun activeDays(kid: String, ym: YearMonth = YearMonth.now(zone)): Int = (1..ym.lengthOfMonth()).count { doneOn(kid, ym.atDay(it)).isNotEmpty() }

    // ---------------------------------------------------------------- games and Ramadan fasting (parent-enabled)
    /** Stories give stars once a day each, so re-reading is welcome but can't farm stars. */
    fun storyRead(id: String) = sp().contains("story_$id")
    fun markStory(id: String): Boolean {
        val today = LocalDate.now(zone).toString()
        if (sp().getString("story_$id", "") == today) return false
        sp().edit { putString("story_$id", today) }; bump(); return true
    }

    // ------------------------------------------------------------------ home rewards the child asks for with stars
    /** Star cost of each home reward (same order as [homeRewards]). */
    val homeRewardCost = listOf(15, 20, 25, 60, 40)
    fun spent(kid: String) = sp().getInt("spent_$kid", 0)
    /** Stars the child can still spend (all earned stars keep growing the tree). */
    fun available(kid: String) = (stars(kid) - spent(kid)).coerceAtLeast(0)
    data class Request(val id: String, val kid: String, val kidName: String, val title: String, val cost: Int)
    fun requests(): List<Request> = runCatching {
        val a = JSONArray(sp().getString("requests", "[]"))
        (0 until a.length()).map { a.getJSONObject(it).let { o -> Request(o.getString("id"), o.getString("kid"), o.optString("name"), o.getString("t"), o.getInt("c")) } }
    }.getOrDefault(emptyList())
    private fun saveRequests(l: List<Request>) = sp().edit {
        putString("requests", JSONArray(l.map { JSONObject().put("id", it.id).put("kid", it.kid).put("name", it.kidName).put("t", it.title).put("c", it.cost) }).toString())
    }
    /** The child asks for a reward; returns false if there aren't enough stars. */
    fun request(k: Kid, title: String, cost: Int): Boolean {
        val pending = requests().filter { it.kid == k.id }.sumOf { it.cost }
        if (available(k.id) - pending < cost) return false
        saveRequests(requests() + Request(java.util.UUID.randomUUID().toString().take(8), k.id, k.name, title, cost)); bump(); return true
    }
    /** The parent says yes (stars are spent) or no. */
    fun decide(id: String, yes: Boolean) {
        val r = requests().firstOrNull { it.id == id } ?: return
        if (yes) sp().edit { putInt("spent_${r.kid}", spent(r.kid) + r.cost) }
        saveRequests(requests().filter { it.id != id }); bump()
    }

    /** Kids' TV channels the parent chose to hide. */
    var hiddenChannels: Set<String> get() = sp().getStringSet("tv_hidden", emptySet()) ?: emptySet(); set(v) { sp().edit { putStringSet("tv_hidden", v) }; bump() }

    fun addStars(kid: String, n: Int) { sp().edit { putInt("stars_$kid", stars(kid) + n) }; bump() }

    /** 0 = none, 1 = suhoor, 2 = until noon, 3 = full day. Only shown when the parent enabled fasting for this child. */
    fun fast(kid: String, d: LocalDate = LocalDate.now(zone)) = sp().getInt("fast_${kid}_$d", 0)
    fun setFast(kid: String, v: Int, d: LocalDate = LocalDate.now(zone)) { sp().edit { putInt("fast_${kid}_$d", v) }; bump() }

    // ---------------------------------------------------------------- parent PIN
    val hasPin get() = sp().getString("pin", "").orEmpty().isNotBlank()
    fun checkPin(p: String) = !hasPin || sp().getString("pin", "") == p
    fun setPin(p: String) { sp().edit { putString("pin", p) } }
}
