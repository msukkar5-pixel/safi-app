package com.mohamed.safi.fitness

import android.content.Context
import android.net.Uri
import com.mohamed.safi.SafiApp
import com.mohamed.safi.ai.Claude
import com.mohamed.safi.ai.ClaudeException
import com.mohamed.safi.ai.ReceiptReader
import com.mohamed.safi.data.dayRange
import com.mohamed.safi.data.fmt
import com.mohamed.safi.data.isoLocal
import com.mohamed.safi.data.zone
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** Claude as nutrition + training coach. Not medical advice. */
object Coach {

    private const val DISCLAIMER = "Not a doctor: for medical conditions, medications or blood-work questions, tell him to check with his doctor."

    suspend fun profileText(): String {
        val p = Fit.prefs
        val w = Fit.latestWeight()
        val t = Fit.targets(w)
        val supps = Fit.dao.supplementsNow()
        val weights = Fit.dao.weightsNow().take(10).reversed()
        return buildString {
            appendLine("Sex: ${if (p.male) "male" else "female"}, age ${Fit.age()}, height ${p.heightCm} cm, weight ${w?.let { fmt(it) } ?: "?"} kg, target ${if (p.targetKg > 0) fmt(p.targetKg) else "?"} kg")
            appendLine("Goal: ${Fit.goalLabel(p.goal)}. Notes: ${p.goalNote}")
            appendLine("Training: ${p.trainingDays} days/week, ${p.sessionMin} min/session. Equipment: ${p.equipment}")
            appendLine("Food preferences: ${p.foodPrefs}")
            if (t != null) appendLine("Daily targets: ${t.kcal} kcal, protein ${t.protein} g, carbs ${t.carbs} g, fat ${t.fat} g (BMR ${t.bmr}, TDEE ${t.tdee})")
            if (supps.isNotEmpty()) appendLine("Supplements: " + supps.joinToString("; ") { "${it.name} ${it.dose} at ${it.times}" })
            if (weights.isNotEmpty()) appendLine("Recent weights: " + weights.joinToString(", ") { "${isoLocal(it.time).take(10)}=${fmt(it.kg)}" })
            appendLine("Lives in UAE, Egyptian. Foods available: Egyptian/Gulf supermarket items.")
        }
    }

    suspend fun dietPlan(): String {
        val system = "You are a sports nutrition coach. Write in Egyptian Arabic, clear and practical, Western digits. $DISCLAIMER"
        val prompt = """
            ${profileText()}

            Write a full daily meal plan for him for training days and a lighter version for rest days.
            Format exactly:
            - A 2-line summary of the targets.
            - "يوم التمرين": each meal (الفطار، سناك، قبل التمرين، بعد التمرين، الغدا، العشا) with foods in grams and the kcal/protein of each meal.
            - "يوم الراحة": what changes.
            - "بدائل": 6 swaps (e.g. instead of X eat Y).
            - "المكملات": when to take each of his supplements relative to meals/training, and anything worth reconsidering. Keep it cautious.
            - "المية": target.
            - "نصايح التنشيف": 5 short bullet points.
            Use plain text with headings and bullets, no markdown tables.
        """.trimIndent()
        val out = Claude.call(system, JSONArray().put(Claude.userText(prompt)), SafiApp.prefs.model, 3000)
        Fit.prefs.dietPlan = out.trim()
        Fit.prefs.dietPlanDate = System.currentTimeMillis()
        return out
    }

    suspend fun workoutPlan(): Fit.Plan {
        val catalog = ExerciseDb.catalogForPlan()
        val system = "You are an expert strength coach. Reply with JSON only."
        val prompt = """
            ${profileText()}

            Build a weekly gym plan with exactly ${Fit.prefs.trainingDays} training days that fits ${Fit.prefs.sessionMin} minutes per session.
            Use ONLY exercise names copied exactly from this catalog (grouped by main muscle):
            $catalog

            JSON:
            {"days":[{"name":"اليوم 1 - صدر وتراي","focus":"short Arabic focus","exercises":[{"name":"<exact catalog name>","sets":4,"reps":"8-10","rest":90,"note":"short Arabic tip"}]}],
             "cardio":"Arabic: cardio prescription for his goal (type, minutes, when)",
             "notes":"Arabic: progression rules, deload, warm-up, 4-6 short lines"}
            5-8 exercises per day. Day names in Arabic.
        """.trimIndent()
        val raw = Claude.call(system, JSONArray().put(Claude.userText(prompt)), SafiApp.prefs.model, 4000)
        val j = Claude.extractJson(raw) ?: throw ClaudeException("Claude رجّع خطة مش مفهومة، جرب تاني")
        // attach exercise ids from the encyclopedia
        val days = j.optJSONArray("days") ?: throw ClaudeException("الخطة فاضية، جرب تاني")
        for (i in 0 until days.length()) {
            val ex = days.getJSONObject(i).optJSONArray("exercises") ?: continue
            for (k in 0 until ex.length()) {
                val e = ex.getJSONObject(k)
                val m = ExerciseDb.match(e.optString("name"))
                e.put("id", m?.id ?: "")
                if (m != null) e.put("name", m.name)
            }
        }
        val s = j.toString()
        val plan = Fit.parsePlan(s) ?: throw ClaudeException("الخطة مش مفهومة، جرب تاني")
        Fit.prefs.workoutPlan = s
        Fit.prefs.workoutPlanDate = System.currentTimeMillis()
        return plan
    }

    data class FoodEstimate(val text: String, val kcal: Double, val protein: Double, val carbs: Double, val fat: Double)

    private fun parseFood(j: JSONObject?): FoodEstimate {
        if (j == null) throw ClaudeException("مقدرتش أحسب الأكل ده")
        return FoodEstimate(
            j.optString("summary"), j.optDouble("kcal", 0.0), j.optDouble("protein", 0.0),
            j.optDouble("carbs", 0.0), j.optDouble("fat", 0.0),
        )
    }

    private val FOOD_JSON = """{"summary":"short Arabic description with grams","kcal":0,"protein":0,"carbs":0,"fat":0}"""

    suspend fun estimateFood(text: String): FoodEstimate {
        val raw = Claude.call(
            "You are a nutrition calculator for Egyptian/Gulf food. JSON only.",
            JSONArray().put(Claude.userText("Estimate the totals for: $text\nAssume typical portions if not given. Return $FOOD_JSON")),
            SafiApp.prefs.fastModel, 400,
        )
        return parseFood(Claude.extractJson(raw))
    }

    suspend fun estimateFoodPhoto(ctx: Context, uri: Uri, note: String): FoodEstimate {
        val b64 = ReceiptReader.base64(ctx, uri)
        val raw = Claude.call(
            "You estimate food portions from photos precisely. JSON only.",
            JSONArray().put(Claude.userImage(b64, "Estimate this meal. ${if (note.isNotBlank()) "User says: $note." else ""} Return $FOOD_JSON")),
            SafiApp.prefs.model, 500,
        )
        return parseFood(Claude.extractJson(raw))
    }

    suspend fun explainExercise(e: Exercise): Pair<String, String> {
        val raw = Claude.call(
            "You are a gym coach. Egyptian Arabic. JSON only.",
            JSONArray().put(
                Claude.userText(
                    """
                    Exercise: ${e.name} (${e.category}, ${e.equipment}, muscles: ${(e.primary + e.secondary).joinToString()})
                    Steps: ${e.instructions.joinToString(" ")}
                    Return {"name":"common Arabic gym name","text":"شرح الأداء خطوة بخطوة، الأخطاء الشائعة، نصايح، عدد المجموعات والعدات المناسب لهدف التنشيف. نقط قصيرة."}
                    """.trimIndent(),
                ),
            ),
            SafiApp.prefs.fastModel, 1200,
        )
        val j = Claude.extractJson(raw) ?: throw ClaudeException("مقدرتش أشرح التمرين")
        val name = j.optString("name")
        val text = j.optString("text")
        Fit.saveAr(e.id, name, text)
        return name to text
    }

    suspend fun reviewSupplements(): String {
        val system = "You are a careful sports-nutrition advisor. Egyptian Arabic, short bullets. $DISCLAIMER"
        val prompt = """
            ${profileText()}
            Review his supplements: timing, doses vs typical evidence-based ranges, overlaps, what is useful or a waste for his goal,
            and interactions to watch. If nothing listed, suggest evidence-based basics for his goal (e.g. creatine, whey, vitamin D) with cautions.
        """.trimIndent()
        return Claude.call(system, JSONArray().put(Claude.userText(prompt)), SafiApp.prefs.model, 1500)
    }

    suspend fun weeklyReport(ctx: Context): String {
        val today = LocalDate.now(zone)
        val days = (0 until 7).map { today.minusDays(it.toLong()) }.reversed()
        val t = Fit.targets(Fit.latestWeight())
        val lines = mutableListOf<String>()
        for (d in days) {
            val (f, to) = dayRange(d)
            val foods = Fit.dao.foodsBetweenNow(f, to)
            val sessions = Fit.dao.sessionsBetweenNow(f, to)
            val h = runCatching { Health.day(ctx, d) }.getOrNull()
            lines += "$d: food ${foods.sumOf { it.kcal }.toInt()} kcal / protein ${foods.sumOf { it.protein }.toInt()} g; " +
                "gym sessions ${sessions.size}; water ${Fit.dao.waterNow(d.toString())?.cups ?: 0} cups; " +
                (h?.let { "steps ${it.steps}, sleep ${it.sleepMin / 60}h${it.sleepMin % 60}m, resting HR ${it.restingHr ?: "-"}, watch workouts ${it.workouts.joinToString()}" } ?: "")
        }
        val system = "You are his coach. Egyptian Arabic, direct, no fluff, Western digits. $DISCLAIMER"
        val prompt = """
            ${profileText()}
            Last 7 days:
            ${lines.joinToString("\n")}
            ${t?.let { "Targets: ${it.kcal} kcal, ${it.protein} g protein, ${Fit.prefs.waterTarget} cups water, ${Fit.prefs.stepsTarget} steps" } ?: ""}

            Write a weekly report: score /10, what went well, what's off, weight trend, sleep and recovery, 3 concrete actions for next week.
            Note when food logging is missing instead of assuming he ate nothing.
        """.trimIndent()
        val out = Claude.call(system, JSONArray().put(Claude.userText(prompt)), SafiApp.prefs.model, 1500)
        Fit.prefs.lastReport = out
        return out
    }

    /** Short context block for the main assistant. */
    suspend fun assistantContext(ctx: Context): String {
        val today = LocalDate.now(zone)
        val (f, to) = dayRange(today)
        val foods = Fit.dao.foodsBetweenNow(f, to)
        val t = Fit.targets(Fit.latestWeight())
        val plan = Fit.parsePlan(Fit.prefs.workoutPlan)
        return buildString {
            appendLine("FITNESS PROFILE:")
            append(profileText())
            appendLine("TODAY FOOD: ${foods.sumOf { it.kcal }.toInt()} kcal, ${foods.sumOf { it.protein }.toInt()} g protein" + (t?.let { " (target ${it.kcal} kcal / ${it.protein} g)" } ?: "") + "; items: " + foods.joinToString("; ") { it.text })
            appendLine("WATER TODAY: ${Fit.dao.waterNow(today.toString())?.cups ?: 0}/${Fit.prefs.waterTarget} cups")
            if (plan != null) appendLine("WORKOUT PLAN DAYS: " + plan.days.joinToString(" | ") { it.name })
        }
    }
}
