package com.mohamed.safi.health

import android.content.Context
import android.net.Uri
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import com.mohamed.safi.SafiApp
import com.mohamed.safi.ai.Claude
import com.mohamed.safi.ai.ClaudeException
import com.mohamed.safi.ai.ReceiptReader
import com.mohamed.safi.data.Reminder
import com.mohamed.safi.data.isoLocal
import com.mohamed.safi.data.millis
import com.mohamed.safi.data.parseIso
import com.mohamed.safi.data.zone
import com.mohamed.safi.notify.ReminderScheduler
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** One measured value from a lab test (e.g. HbA1c 5.4 %, range 4–5.6). */
@Entity(tableName = "labs", indices = [Index("test"), Index("date")])
data class LabResult(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: Long,
    val test: String,
    val value: Double?,
    val valueText: String = "",
    val unit: String = "",
    val low: Double? = null,
    val high: Double? = null,
    val lab: String = "",
    val note: String = "",
    val photoPath: String? = null,
)

@Entity(tableName = "meds")
data class Medication(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val dose: String = "",
    val times: String = "",              // "08:00, 20:00"
    val withFood: String = "",           // قبل الأكل / بعد الأكل / مع الأكل
    val reason: String = "",
    val startDate: Long = System.currentTimeMillis(),
    val endDate: Long? = null,
    val pillsLeft: Int? = null,
    val perDose: Int = 1,
    val active: Boolean = true,
)

@Entity(tableName = "visits", indices = [Index("date")])
data class Visit(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: Long,
    val doctor: String,
    val specialty: String = "",
    val place: String = "",
    val reason: String = "",
    val notes: String = "",
    val nextVisit: Long? = null,
)

@Dao
interface HealthDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertLab(l: LabResult): Long
    @Delete suspend fun deleteLab(l: LabResult)
    @Query("SELECT * FROM labs ORDER BY date DESC, test") fun labs(): Flow<List<LabResult>>
    @Query("SELECT * FROM labs ORDER BY date DESC") suspend fun labsNow(): List<LabResult>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertMed(m: Medication): Long
    @Delete suspend fun deleteMed(m: Medication)
    @Query("SELECT * FROM meds ORDER BY active DESC, name") fun meds(): Flow<List<Medication>>
    @Query("SELECT * FROM meds WHERE active = 1") suspend fun medsNow(): List<Medication>
    @Query("SELECT * FROM meds WHERE id = :id") suspend fun med(id: Long): Medication?

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertVisit(v: Visit): Long
    @Delete suspend fun deleteVisit(v: Visit)
    @Query("SELECT * FROM visits ORDER BY date DESC") fun visits(): Flow<List<Visit>>
    @Query("SELECT * FROM visits ORDER BY date DESC") suspend fun visitsNow(): List<Visit>
}

@Database(entities = [LabResult::class, Medication::class, Visit::class], version = 1, exportSchema = false)
abstract class HealthDb : RoomDatabase() {
    abstract fun dao(): HealthDao

    companion object {
        @Volatile private var inst: HealthDb? = null
        fun get(ctx: Context = SafiApp.instance): HealthDb = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(ctx.applicationContext, HealthDb::class.java, "safi_health.db").build().also { inst = it }
        }
        fun closeAll() { inst?.close(); inst = null }
        val dao: HealthDao get() = get().dao()
    }
}

object Meds {
    fun times(s: String): List<LocalTime> = com.mohamed.safi.fitness.Supps.parseTimes(s)

    /** Saves the medication and (re)creates its daily reminders until the end date. */
    suspend fun save(ctx: Context, m: Medication): Long {
        val id = HealthDb.dao.upsertMed(m).let { if (m.id != 0L) m.id else it }
        val dao = SafiApp.db.dao()
        dao.remindersFor("med", id).forEach { ReminderScheduler.cancel(ctx, it.id); ReminderScheduler.forget(ctx, it.id); dao.deleteReminder(it) }
        val ended = m.endDate != null && m.endDate < System.currentTimeMillis()
        if (m.active && !ended) {
            val now = LocalDateTime.now(zone)
            for (t in times(m.times)) {
                var at = LocalDate.now(zone).atTime(t)
                if (!at.isAfter(now)) at = at.plusDays(1)
                val r = Reminder(
                    title = "💊 ${m.name}" + if (m.dose.isNotBlank()) " — ${m.dose}" else "",
                    note = listOf(m.withFood, m.reason).filter { it.isNotBlank() }.joinToString(" • "),
                    time = at.millis(), repeat = "daily", kind = "reminder", alarm = false,
                    refType = "med", refId = id,
                )
                val rid = dao.upsertReminder(r)
                ReminderScheduler.schedule(ctx, r.copy(id = rid))
            }
        }
        return id
    }

    suspend fun delete(ctx: Context, m: Medication) {
        val dao = SafiApp.db.dao()
        dao.remindersFor("med", m.id).forEach { ReminderScheduler.cancel(ctx, it.id); ReminderScheduler.forget(ctx, it.id); dao.deleteReminder(it) }
        HealthDb.dao.deleteMed(m)
    }

    /** Called when the user marks a dose taken: decrements the pill count. */
    suspend fun taken(medId: Long) {
        val m = HealthDb.dao.med(medId) ?: return
        val left = m.pillsLeft ?: return
        HealthDb.dao.upsertMed(m.copy(pillsLeft = (left - m.perDose).coerceAtLeast(0)))
    }

    /** Daily check: stop finished courses, warn when pills are running out. */
    suspend fun dailyCheck(ctx: Context): List<String> {
        val out = mutableListOf<String>()
        for (m in HealthDb.dao.medsNow()) {
            if (m.endDate != null && m.endDate < System.currentTimeMillis()) {
                save(ctx, m.copy(active = false)); continue
            }
            val left = m.pillsLeft ?: continue
            val perDay = times(m.times).size.coerceAtLeast(1) * m.perDose
            val days = left / perDay
            if (days <= 5) out += "• ${m.name}: باقي $left (حوالي $days يوم) — جدّد الروشتة"
        }
        return out
    }
}

/** AI helpers: read a lab report photo and explain results. Never diagnoses. */
object HealthAI {
    private const val CARE = "You are a careful medical information assistant. Egyptian Arabic, Western digits. " +
        "You do not diagnose or change treatment. Explain what values mean in general, flag what's outside the reference range, " +
        "and always recommend reviewing results with the treating doctor. If anything looks urgent, say so clearly."

    data class Extracted(val date: Long?, val lab: String, val items: List<LabResult>)

    suspend fun readReport(ctx: Context, uri: Uri): Extracted {
        val b64 = ReceiptReader.base64(ctx, uri)
        val raw = Claude.call(
            "You extract lab test results from photos of medical reports precisely. JSON only.",
            JSONArray().put(
                Claude.userImage(
                    b64,
                    """Extract every test result. Return:
                    {"date":"YYYY-MM-DD or empty","lab":"lab name or empty","results":[{"test":"name as printed (English)","value":number or null,"value_text":"if not numeric","unit":"","low":number or null,"high":number or null}]}""",
                ),
            ),
            SafiApp.prefs.model, 3000,
        )
        val j = Claude.extractJson(raw) ?: throw ClaudeException("مقدرتش أقرا التحليل، جرب صورة أوضح")
        val date = parseIso(j.optString("date"))
        val arr = j.optJSONArray("results") ?: JSONArray()
        fun d(o: org.json.JSONObject, k: String): Double? = if (o.isNull(k) || !o.has(k)) null else o.optDouble(k).takeIf { !it.isNaN() }
        val items = (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            LabResult(
                date = date ?: System.currentTimeMillis(), test = o.optString("test").trim(), value = d(o, "value"),
                valueText = o.optString("value_text").let { if (it == "null") "" else it }, unit = o.optString("unit"),
                low = d(o, "low"), high = d(o, "high"), lab = j.optString("lab"),
            )
        }.filter { it.test.isNotBlank() }
        return Extracted(date, j.optString("lab"), items)
    }

    suspend fun explain(results: List<LabResult>): String {
        val meds = HealthDb.dao.medsNow()
        val history = HealthDb.dao.labsNow().filter { r -> results.any { it.test.equals(r.test, true) } && results.none { it.id == r.id } }
        val prompt = buildString {
            appendLine("Latest results:")
            results.forEach { appendLine("${it.test}: ${it.value ?: it.valueText} ${it.unit} (ref ${it.low ?: "?"}–${it.high ?: "?"}) on ${isoLocal(it.date).take(10)}") }
            if (history.isNotEmpty()) {
                appendLine("Previous values of the same tests:")
                history.take(40).forEach { appendLine("${it.test}: ${it.value ?: it.valueText} ${it.unit} on ${isoLocal(it.date).take(10)}") }
            }
            if (meds.isNotEmpty()) appendLine("Current medications: " + meds.joinToString { "${it.name} ${it.dose}" })
            appendLine("\nExplain: what each abnormal value usually means, the trend vs previous values, lifestyle/diet points, and questions to ask the doctor.")
        }
        return Claude.call(CARE, JSONArray().put(Claude.userText(prompt)), SafiApp.prefs.model, 2500)
    }

    suspend fun summary(): String {
        val labs = HealthDb.dao.labsNow().take(80)
        val meds = HealthDb.dao.medsNow()
        val visits = HealthDb.dao.visitsNow().take(10)
        val prompt = buildString {
            appendLine("Medications: " + meds.joinToString("; ") { "${it.name} ${it.dose} ${it.times} ${it.reason}" })
            appendLine("Lab results (newest first):")
            labs.forEach { appendLine("${isoLocal(it.date).take(10)} ${it.test}: ${it.value ?: it.valueText} ${it.unit} (ref ${it.low ?: "?"}–${it.high ?: "?"})") }
            appendLine("Doctor visits:")
            visits.forEach { appendLine("${isoLocal(it.date).take(10)} ${it.doctor} ${it.specialty}: ${it.reason} — ${it.notes}") }
            appendLine("\nWrite a short health overview: what's in range, what needs attention, trends, medication timing tips, and when the next checkups are due.")
        }
        return Claude.call(CARE, JSONArray().put(Claude.userText(prompt)), SafiApp.prefs.model, 2500)
    }
}
