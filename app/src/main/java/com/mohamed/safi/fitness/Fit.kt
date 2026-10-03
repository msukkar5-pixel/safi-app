package com.mohamed.safi.fitness

import android.content.Context
import androidx.core.content.edit
import com.mohamed.safi.SafiApp
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import kotlin.math.roundToInt

/** Body + goal profile and daily targets. */
class FitPrefs(ctx: Context) {
    private val p = ctx.getSharedPreferences("safi_fit", Context.MODE_PRIVATE)
    private fun s(k: String, d: String) = p.getString(k, d) ?: d
    private fun putS(k: String, v: String) = p.edit { putString(k, v) }

    var heightCm: Int get() = p.getInt("height", 0); set(v) = p.edit { putInt("height", v) }
    var birthYear: Int get() = p.getInt("birthYear", 1986); set(v) = p.edit { putInt("birthYear", v) }
    var male: Boolean get() = p.getBoolean("male", true); set(v) = p.edit { putBoolean("male", v) }
    var targetKg: Double get() = s("targetKg", "0").toDoubleOrNull() ?: 0.0; set(v) = putS("targetKg", v.toString())
    var goal: String get() = s("goal", "cut"); set(v) = putS("goal", v)        // cut | maintain | bulk | recomp
    var activity: Double get() = s("activity", "1.725").toDoubleOrNull() ?: 1.725; set(v) = putS("activity", v.toString())
    var trainingDays: Int get() = p.getInt("trainingDays", 6); set(v) = p.edit { putInt("trainingDays", v) }
    var sessionMin: Int get() = p.getInt("sessionMin", 90); set(v) = p.edit { putInt("sessionMin", v) }
    var foodPrefs: String
        get() = s("foodPrefs", "لحمة حمراء، صدور فراخ، بيض، بطاطس مسلوقة. الرز مرة واحدة في اليوم بالكتير.")
        set(v) = putS("foodPrefs", v)
    var goalNote: String
        get() = s("goalNote", "تنشيف دهون ومياه مع الحفاظ على العضل. تمرين حديد 90 دقيقة يوميًا.")
        set(v) = putS("goalNote", v)
    var equipment: String get() = s("equipment", "جيم كامل"); set(v) = putS("equipment", v)
    var waterTarget: Int get() = p.getInt("waterTarget", 12); set(v) = p.edit { putInt("waterTarget", v) }   // cups of 250 ml
    var stepsTarget: Int get() = p.getInt("stepsTarget", 8000); set(v) = p.edit { putInt("stepsTarget", v) }

    // Manual target override (0 = auto)
    var kcalOverride: Int get() = p.getInt("kcalOverride", 0); set(v) = p.edit { putInt("kcalOverride", v) }

    var dietPlan: String get() = s("dietPlan", ""); set(v) = putS("dietPlan", v)
    var dietPlanDate: Long get() = p.getLong("dietPlanDate", 0); set(v) = p.edit { putLong("dietPlanDate", v) }
    var workoutPlan: String get() = s("workoutPlan", ""); set(v) = putS("workoutPlan", v)          // JSON
    var workoutPlanDate: Long get() = p.getLong("workoutPlanDate", 0); set(v) = p.edit { putLong("workoutPlanDate", v) }
    var lastReport: String get() = s("lastReport", ""); set(v) = putS("lastReport", v)
    var arNames: String get() = s("arNames", "{}"); set(v) = putS("arNames", v)                    // exerciseId -> Arabic explanation
    var healthConnected: Boolean get() = p.getBoolean("hc", false); set(v) = p.edit { putBoolean("hc", v) }
}

data class Targets(val kcal: Int, val protein: Int, val carbs: Int, val fat: Int, val bmr: Int, val tdee: Int)

object Fit {
    val prefs: FitPrefs by lazy { FitPrefs(SafiApp.instance) }
    val dao: LifeDao get() = LifeDb.get(SafiApp.instance).dao()

    fun age(): Int = LocalDate.now().year - prefs.birthYear

    /** Mifflin–St Jeor BMR × activity, adjusted for goal. Protein 2.2 g/kg on a cut. */
    fun targets(weightKg: Double?): Targets? {
        val w = weightKg ?: return null
        val h = prefs.heightCm
        if (h <= 0 || w <= 0) return null
        val bmr = 10 * w + 6.25 * h - 5 * age() + if (prefs.male) 5 else -161
        val tdee = bmr * prefs.activity
        val goalKcal = when (prefs.goal) {
            "cut" -> tdee * 0.8
            "bulk" -> tdee * 1.1
            "recomp" -> tdee * 0.92
            else -> tdee
        }
        val kcal = if (prefs.kcalOverride > 0) prefs.kcalOverride.toDouble() else goalKcal
        val protein = w * when (prefs.goal) { "cut" -> 2.2; "recomp" -> 2.0; else -> 1.8 }
        val fat = w * 0.8
        val carbs = ((kcal - protein * 4 - fat * 9) / 4).coerceAtLeast(50.0)
        return Targets(kcal.roundToInt(), protein.roundToInt(), carbs.roundToInt(), fat.roundToInt(), bmr.roundToInt(), tdee.roundToInt())
    }

    suspend fun latestWeight(): Double? = dao.weightsNow().firstOrNull()?.kg

    fun goalLabel(g: String) = when (g) {
        "cut" -> "تنشيف (خسارة دهون)"
        "bulk" -> "تضخيم"
        "recomp" -> "إعادة تشكيل"
        else -> "ثبات"
    }

    // ---------- workout plan JSON ----------
    data class PlanExercise(val id: String, val name: String, val sets: Int, val reps: String, val rest: Int, val note: String)
    data class PlanDay(val name: String, val focus: String, val exercises: List<PlanExercise>)
    data class Plan(val days: List<PlanDay>, val cardio: String, val notes: String)

    fun parsePlan(s: String): Plan? = runCatching {
        val j = JSONObject(s)
        val days = j.getJSONArray("days")
        Plan(
            (0 until days.length()).map { i ->
                val d = days.getJSONObject(i)
                val ex = d.optJSONArray("exercises") ?: JSONArray()
                PlanDay(
                    d.optString("name"), d.optString("focus"),
                    (0 until ex.length()).map { k ->
                        val e = ex.getJSONObject(k)
                        PlanExercise(
                            e.optString("id"), e.optString("name"), e.optInt("sets", 3),
                            e.optString("reps", "10"), e.optInt("rest", 90), e.optString("note"),
                        )
                    },
                )
            },
            j.optString("cardio"), j.optString("notes"),
        )
    }.getOrNull()

    fun arName(id: String): String? = runCatching { JSONObject(prefs.arNames).optJSONObject(id)?.optString("name") }.getOrNull()?.takeIf { it.isNotBlank() }
    fun arExplain(id: String): String? = runCatching { JSONObject(prefs.arNames).optJSONObject(id)?.optString("text") }.getOrNull()?.takeIf { it.isNotBlank() }
    fun saveAr(id: String, name: String, text: String) {
        val j = runCatching { JSONObject(prefs.arNames) }.getOrDefault(JSONObject())
        j.put(id, JSONObject().put("name", name).put("text", text))
        prefs.arNames = j.toString()
    }
}
