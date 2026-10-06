package com.mohamed.safi.quiz

import android.content.Context
import androidx.core.content.edit
import com.mohamed.safi.SafiApp
import com.mohamed.safi.data.zone
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import kotlin.random.Random

data class Question(val q: String, val answers: List<String>, val correct: Int, val cat: String, val lvl: Int, val exp: String) {
    val id: Int get() = q.hashCode()
    fun shuffled(r: Random): Question {
        val order = answers.indices.shuffled(r)
        return copy(answers = order.map { answers[it] }, correct = order.indexOf(correct))
    }
}

object Quiz {
    val religionCats = linkedMapOf(
        "quran" to "القرآن الكريم", "prophets" to "الأنبياء", "seerah" to "السيرة النبوية", "sahaba" to "الصحابة",
        "hadith" to "الحديث", "fiqh" to "الفقه", "aqeedah" to "العقيدة", "islamic_history" to "التاريخ الإسلامي",
    )
    val generalCats = linkedMapOf(
        "general" to "معلومات عامة", "geography" to "جغرافيا", "science" to "علوم", "history" to "تاريخ",
        "sports" to "رياضة", "egypt" to "مصر", "uae" to "الإمارات", "tech" to "تكنولوجيا", "language" to "لغة عربية", "math" to "ألغاز وحساب",
    )
    val allCats get() = religionCats + generalCats

    private var bank: List<Question>? = null

    fun load(ctx: Context = SafiApp.instance): List<Question> {
        bank?.let { return it }
        val out = mutableListOf<Question>()
        for (f in listOf("quiz/religion.json", "quiz/general.json")) {
            val txt = runCatching { ctx.assets.open(f).bufferedReader().use { it.readText() } }.getOrNull() ?: continue
            val a = runCatching { JSONArray(txt) }.getOrNull() ?: continue
            for (i in 0 until a.length()) runCatching {
                val o = a.getJSONObject(i)
                val ans = o.getJSONArray("a")
                val list = (0 until ans.length()).map { ans.getString(it) }
                val c = o.getInt("c")
                if (list.size == 4 && c in 0..3 && list.toSet().size == 4) out += Question(o.getString("q"), list, c, o.optString("cat", "general"), o.optInt("lvl", 1).coerceIn(1, 3), o.optString("exp"))
            }
        }
        bank = out.distinctBy { it.q }
        return bank!!
    }

    // ------------------------------------------------------------------ persistence
    private fun sp() = SafiApp.instance.getSharedPreferences("safi_quiz", Context.MODE_PRIVATE)
    var xp: Int get() = sp().getInt("xp", 0); set(v) = sp().edit { putInt("xp", v) }
    fun level(x: Int = xp): Int { var l = 1; var need = 100; var left = x; while (left >= need) { left -= need; l++; need += 60 }; return l }
    /** progress within the current level, 0..1 */
    fun levelProgress(x: Int = xp): Float { var need = 100; var left = x; while (left >= need) { left -= need; need += 60 }; return left.toFloat() / need }
    fun best(mode: String) = sp().getInt("best_$mode", 0)
    fun setBest(mode: String, v: Int) = sp().edit { putInt("best_$mode", v) }
    var stage: Int get() = sp().getInt("stage", 1); set(v) = sp().edit { putInt("stage", v) }
    fun stageStars(n: Int) = sp().getInt("stars_$n", 0)
    fun setStageStars(n: Int, s: Int) = sp().edit { putInt("stars_$n", maxOf(s, stageStars(n))) }
    val dailyDoneToday get() = sp().getString("daily_day", "") == LocalDate.now(zone).toString()
    val dailyScore get() = sp().getInt("daily_score", 0)
    fun markDaily(score: Int) = sp().edit { putString("daily_day", LocalDate.now(zone).toString()); putInt("daily_score", score) }
    private fun leagueWeek(): String {
        val d = LocalDate.now(zone)
        val w = d.get(java.time.temporal.WeekFields.ISO.weekOfWeekBasedYear())
        return "${d.year}-$w"
    }
    private fun leagueKey() = "league_${leagueWeek()}"
    /** Local weekly league: all scores stay on the device and reset automatically with a new ISO week. */
    val leaguePoints: Int get() = sp().getInt(leagueKey(), 0)
    val leagueRounds: Int get() = sp().getInt("${leagueKey()}_rounds", 0)
    val leagueGoals = listOf(400, 900, 1_500)
    fun leagueTier(points: Int = leaguePoints): Int = leagueGoals.count { points >= it }
    fun addLeague(score: Int) = sp().edit {
        putInt(leagueKey(), leaguePoints + score.coerceAtLeast(0))
        putInt("${leagueKey()}_rounds", leagueRounds + 1)
    }
    val streak: Int get() {
        val last = sp().getString("play_day", "") ?: ""
        val today = LocalDate.now(zone)
        return if (last == today.toString() || last == today.minusDays(1).toString()) sp().getInt("streak", 0) else 0
    }
    private fun touchDay() {
        val today = LocalDate.now(zone)
        val last = sp().getString("play_day", "")
        if (last == today.toString()) return
        val s = if (last == today.minusDays(1).toString()) sp().getInt("streak", 0) + 1 else 1
        sp().edit { putString("play_day", today.toString()); putInt("streak", s) }
    }
    fun catStats(cat: String): Pair<Int, Int> = sp().getInt("c_ok_$cat", 0) to sp().getInt("c_all_$cat", 0)
    private val recentIds: MutableList<Int> by lazy { (sp().getString("recent", "") ?: "").split(',').mapNotNull { it.toIntOrNull() }.toMutableList() }

    fun record(q: Question, ok: Boolean) {
        sp().edit {
            putInt("c_all_${q.cat}", sp().getInt("c_all_${q.cat}", 0) + 1)
            if (ok) putInt("c_ok_${q.cat}", sp().getInt("c_ok_${q.cat}", 0) + 1)
        }
        recentIds.remove(q.id); recentIds.add(q.id)
        while (recentIds.size > 300) recentIds.removeAt(0)
        sp().edit { putString("recent", recentIds.joinToString(",")) }
        touchDay()
    }

    // ------------------------------------------------------------------ building games
    /** [cats] null = all; [lvl] null = any. Prefers questions not seen recently. */
    fun pick(n: Int, cats: Set<String>? = null, lvl: Int? = null, seed: Long? = null, levels: List<Int>? = null): List<Question> {
        val r = if (seed != null) Random(seed) else Random.Default
        var pool = load().filter { (cats == null || it.cat in cats) && (lvl == null || it.lvl == lvl) }
        if (pool.isEmpty()) pool = load().filter { cats == null || it.cat in cats }
        val fresh = if (seed == null) pool.filter { it.id !in recentIds } else pool
        val base = if (fresh.size >= n) fresh else pool
        val chosen = if (levels != null) {
            val queues = (1..3).associateWith { l -> ArrayDeque(base.filter { it.lvl == l }.shuffled(r)) }
            val rest = ArrayDeque(base.shuffled(r))
            val used = HashSet<String>()
            levels.mapNotNull { l ->
                val q = generateSequence { queues[l]?.removeFirstOrNull() }.firstOrNull { it.q !in used }
                    ?: generateSequence { rest.removeFirstOrNull() }.firstOrNull { it.q !in used }
                q?.also { used += it.q }
            }
        } else balanced(base, n, r)
        return chosen.map { it.shuffled(r) }
    }

    /** Mixes categories evenly, so the 1000+ Quran questions don't crowd out the rest. */
    private fun balanced(pool: List<Question>, n: Int, r: Random): List<Question> {
        val groups = pool.groupBy { it.cat }.values.map { ArrayDeque(it.shuffled(r)) }.shuffled(r)
        val out = ArrayList<Question>(n)
        while (out.size < n && groups.any { it.isNotEmpty() }) {
            for (g in groups) { if (out.size >= n) break; g.removeFirstOrNull()?.let { out += it } }
        }
        return out.shuffled(r)
    }

    // ------------------------------------------------------------------ fun extras
    var sound: Boolean get() = sp().getBoolean("sound", true); set(v) = sp().edit { putBoolean("sound", v) }

    data class Badge(val id: String, val icon: String, val title: String, val how: String)
    val badges = listOf(
        Badge("first", "🌱", "البداية", "أول لعبة تخلّصها"),
        Badge("perfect", "💯", "العلامة الكاملة", "كل الإجابات صح في لعبة ١٠ أسئلة"),
        Badge("combo5", "🔥", "على نار", "٥ إجابات صح ورا بعض"),
        Badge("combo10", "☄️", "مايتوقفش", "١٠ إجابات صح ورا بعض"),
        Badge("survivor", "❤️", "الناجي", "٢٠ سؤال صح في ٣ أرواح"),
        Badge("speed15", "⚡", "البرق", "١٥ إجابة صح في سباق الدقيقة"),
        Badge("ladder", "🏆", "قمة السلّم", "توصل للسؤال ١٥ في سلّم الأبطال"),
        Badge("friend", "🤝", "التحدي", "تلعب تحدي مع صاحبك"),
        Badge("quran50", "📖", "صاحب القرآن", "٥٠ إجابة صح في أسئلة القرآن"),
        Badge("level5", "⭐", "المستوى ٥", "توصل للمستوى الخامس"),
        Badge("level10", "🌟", "المستوى ١٠", "توصل للمستوى العاشر"),
        Badge("streak7", "📅", "أسبوع كامل", "تلعب ٧ أيام ورا بعض"),
        Badge("league1", "🥉", "بداية الدوري", "توصل للمرحلة الأولى من دوري الأسبوع"),
        Badge("league3", "👑", "بطل الأسبوع", "تخلص المراحل الثلاثة من دوري الأسبوع"),
    )
    fun hasBadge(id: String) = sp().getBoolean("badge_$id", false)
    /** Unlocks the badges these conditions earned; returns only the new ones. */
    fun unlock(ids: Collection<String>): List<Badge> {
        val fresh = ids.filter { !hasBadge(it) }
        if (fresh.isNotEmpty()) sp().edit { fresh.forEach { putBoolean("badge_$it", true) } }
        return badges.filter { it.id in fresh }
    }

    fun daily(): List<Question> {
        val seed = LocalDate.now(zone).toEpochDay() * 7919L
        return pick(10, null, null, seed, listOf(1, 1, 1, 2, 2, 2, 2, 3, 3, 3))
    }

    /** Stage n: 10 questions, harder as n grows. */
    fun stageQuestions(n: Int): List<Question> {
        val levels = when {
            n <= 3 -> listOf(1, 1, 1, 1, 1, 1, 2, 2, 2, 2)
            n <= 7 -> listOf(1, 1, 1, 2, 2, 2, 2, 2, 3, 3)
            n <= 12 -> listOf(1, 2, 2, 2, 2, 2, 3, 3, 3, 3)
            else -> listOf(2, 2, 2, 3, 3, 3, 3, 3, 3, 3)
        }
        return pick(10, null, null, n * 104729L, levels)
    }

    fun points(lvl: Int, secondsLeft: Int, combo: Int): Int {
        val base = when (lvl) { 1 -> 10; 2 -> 20; else -> 30 }
        val mult = when { combo >= 5 -> 3; combo >= 3 -> 2; else -> 1 }
        return (base + secondsLeft / 2) * mult
    }

    fun addXp(v: Int) { xp += v }
}
