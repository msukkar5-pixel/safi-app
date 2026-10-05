package com.mohamed.safi.fitness

import android.content.Context
import com.mohamed.safi.SafiApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.io.File
import java.util.concurrent.TimeUnit

data class Exercise(
    val id: String,
    val name: String,
    val force: String,
    val level: String,
    val mechanic: String,
    val equipment: String,
    val primary: List<String>,
    val secondary: List<String>,
    val instructions: List<String>,
    val category: String,
    val images: List<String>,
) {
    val imageUrls: List<String> get() = images.map { ExerciseDb.IMG_BASE + it }
    val arName: String? get() = Fit.arName(id)
}

/**
 * Open exercise encyclopedia (free-exercise-db, public domain, 800+ exercises with photos).
 * Downloaded once on first use and cached on the phone.
 */
object ExerciseDb {
    const val URL = "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/dist/exercises.json"
    const val IMG_BASE = "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/"

    private val lock = Mutex()
    @Volatile private var cache: List<Exercise>? = null

    val muscles = linkedMapOf(
        "chest" to "صدر",
        "shoulders" to "أكتاف",
        "triceps" to "تراي",
        "biceps" to "باي",
        "forearms" to "سواعد",
        "lats" to "ظهر عريض (لاتس)",
        "middle back" to "منتصف الظهر",
        "lower back" to "أسفل الظهر",
        "traps" to "ترابيس",
        "neck" to "رقبة",
        "abdominals" to "بطن",
        "quadriceps" to "أمامية الفخذ",
        "hamstrings" to "خلفية الفخذ",
        "glutes" to "مؤخرة (جلوتس)",
        "calves" to "سمانة",
        "adductors" to "ضامة الفخذ",
        "abductors" to "خارج الفخذ",
    )

    val categories = linkedMapOf(
        "strength" to "حديد",
        "cardio" to "كارديو",
        "stretching" to "إطالة",
        "plyometrics" to "بلايومترك",
        "powerlifting" to "باورليفتنج",
        "olympic weightlifting" to "رفع أولمبي",
        "strongman" to "سترونج مان",
    )

    val equipment = linkedMapOf(
        "barbell" to "بار",
        "dumbbell" to "دمبل",
        "machine" to "جهاز",
        "cable" to "كابل",
        "body only" to "وزن الجسم",
        "kettlebells" to "كيتل بل",
        "bands" to "أستك",
        "e-z curl bar" to "بار زجزاج",
        "exercise ball" to "كورة",
        "medicine ball" to "كورة طبية",
        "foam roll" to "فوم رولر",
        "other" to "أخرى",
    )

    val levels = mapOf("beginner" to "مبتدئ", "intermediate" to "متوسط", "expert" to "متقدم")

    fun ar(map: Map<String, String>, k: String) = map[k] ?: k

    private fun file(ctx: Context) = File(ctx.filesDir, "exercises.json")

    fun isDownloaded(ctx: Context = SafiApp.instance) = file(ctx).length() > 10_000

    private fun parse(text: String): List<Exercise> {
        val arr = JSONArray(text)
        fun list(a: JSONArray?) = if (a == null) emptyList() else (0 until a.length()).map { a.optString(it) }
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Exercise(
                id = o.optString("id"),
                name = o.optString("name"),
                force = o.optString("force").let { if (it == "null") "" else it },
                level = o.optString("level"),
                mechanic = o.optString("mechanic").let { if (it == "null") "" else it },
                equipment = o.optString("equipment").let { if (it == "null" || it.isBlank()) "other" else it },
                primary = list(o.optJSONArray("primaryMuscles")),
                secondary = list(o.optJSONArray("secondaryMuscles")),
                instructions = list(o.optJSONArray("instructions")),
                category = o.optString("category"),
                images = list(o.optJSONArray("images")),
            )
        }
    }

    /** Loads from cache, downloading the first time. Throws when offline on first use. */
    suspend fun all(ctx: Context = SafiApp.instance): List<Exercise> = lock.withLock {
        cache?.let { return@withLock it }
        withContext(Dispatchers.IO) {
            val f = file(ctx)
            if (f.length() < 10_000) {
                val http = OkHttpClient.Builder().readTimeout(60, TimeUnit.SECONDS).build()
                http.newCall(Request.Builder().url(URL).build()).execute().use { r ->
                    if (!r.isSuccessful) throw IllegalStateException("تحميل الموسوعة فشل (${r.code})")
                    val body = r.body ?: throw IllegalStateException("تحميل الموسوعة فشل")
                    // Write to a temp file and rename on success, so a cut-off download never looks complete.
                    val tmp = File(f.parentFile, f.name + ".tmp")
                    try {
                        tmp.outputStream().use { out -> body.byteStream().use { it.copyTo(out) } }
                        if (tmp.length() < 10_000) throw IllegalStateException("تحميل الموسوعة فشل")
                        f.delete()
                        if (!tmp.renameTo(f)) {
                            tmp.copyTo(f, overwrite = true)
                            tmp.delete()
                        }
                    } catch (e: Exception) {
                        tmp.delete()
                        throw e
                    }
                }
            }
            val list = try {
                parse(f.readText()).sortedBy { it.name }
            } catch (e: Exception) {
                // Corrupt cache: drop it so the next call downloads again.
                f.delete()
                throw IllegalStateException("ملف الموسوعة بايظ، جرّب تاني", e)
            }
            cache = list
            list
        }
    }

    suspend fun byId(id: String): Exercise? = runCatching { all().firstOrNull { it.id == id } }.getOrNull()

    private fun norm(s: String) = s.lowercase().replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()

    /** Best match for a free-text English exercise name (used when Claude writes a plan). */
    suspend fun match(name: String): Exercise? {
        val list = runCatching { all() }.getOrNull() ?: return null
        val n = norm(name)
        list.firstOrNull { norm(it.name) == n }?.let { return it }
        list.firstOrNull { norm(it.id.replace('_', ' ')) == n }?.let { return it }
        val words = n.split(" ").filter { it.length > 2 }.toSet()
        if (words.isEmpty()) return null
        return list.maxByOrNull { e ->
            val ew = norm(e.name).split(" ").toSet()
            val common = words.count { it in ew }
            common * 10 - (ew.size - common)
        }?.takeIf { e -> words.count { it in norm(e.name).split(" ") } >= maxOf(1, words.size / 2) }
    }

    fun search(list: List<Exercise>, q: String, muscle: String?, category: String?, equip: String?): List<Exercise> {
        val qq = q.trim().lowercase()
        return list.filter { e ->
            (muscle == null || muscle in e.primary) &&
                (category == null || e.category == category) &&
                (equip == null || e.equipment == equip) &&
                (qq.isEmpty() || e.name.lowercase().contains(qq) || (e.arName?.contains(qq) == true) ||
                    e.primary.any { ar(muscles, it).contains(qq) })
        }
    }

    /** Short list for Claude when building a plan: popular gym exercises. */
    suspend fun catalogForPlan(): String {
        val list = runCatching { all() }.getOrNull() ?: return ""
        return list.filter {
            it.category == "strength" && it.level != "expert" &&
                it.equipment in setOf("barbell", "dumbbell", "machine", "cable", "body only", "e-z curl bar")
        }.groupBy { it.primary.firstOrNull() ?: "other" }
            .entries.joinToString("\n") { (m, es) -> "$m: " + es.take(28).joinToString(" | ") { it.name } }
    }
}
