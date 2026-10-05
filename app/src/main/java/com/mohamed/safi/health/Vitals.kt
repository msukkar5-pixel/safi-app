package com.mohamed.safi.health

import android.content.Context
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.room.*
import com.mohamed.safi.SafiApp
import com.mohamed.safi.fitness.Health
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/** A manual or watch reading. kind: bp | hr | rhr | spo2. For bp: v1 = systolic, v2 = diastolic, v3 = pulse. */
@Entity(tableName = "vitals", indices = [Index("time")])
data class Vital(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val time: Long = System.currentTimeMillis(),
    val kind: String,
    val v1: Double,
    val v2: Double? = null,
    val v3: Double? = null,
    val note: String = "",
    val source: String = "manual", // manual | watch
)

@Dao
interface VitalsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(v: Vital): Long
    @Delete suspend fun delete(v: Vital)
    @Query("SELECT * FROM vitals ORDER BY time DESC") fun all(): Flow<List<Vital>>
    @Query("SELECT * FROM vitals WHERE time >= :from ORDER BY time DESC") suspend fun since(from: Long): List<Vital>
}

@Database(entities = [Vital::class], version = 1, exportSchema = false)
abstract class VitalsDb : RoomDatabase() {
    abstract fun dao(): VitalsDao
    companion object {
        @Volatile private var inst: VitalsDb? = null
        fun get(ctx: Context = SafiApp.instance): VitalsDb = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(ctx.applicationContext, VitalsDb::class.java, "safi_vitals.db").build().also { inst = it }
        }
        val dao: VitalsDao get() = get().dao()
    }
}

data class Advice(val level: Int, val title: String, val text: String) // level: 0 info, 1 watch, 2 see doctor, 3 urgent

object Vitals {
    /** Watch readings from Health Connect for the last [days] (not stored; merged at display time). */
    suspend fun fromWatch(ctx: Context, days: Long = 30): List<Vital> {
        if (!Health.available(ctx)) return emptyList()
        val g = Health.granted(ctx)
        val c = Health.client(ctx)
        val f = TimeRangeFilter.after(Instant.now().minusSeconds(days * 86_400))
        val out = mutableListOf<Vital>()
        if (HealthPermission.getReadPermission(BloodPressureRecord::class) in g) runCatching {
            c.readRecords(ReadRecordsRequest(BloodPressureRecord::class, f)).records.forEach {
                out += Vital(time = it.time.toEpochMilli(), kind = "bp", v1 = it.systolic.inMillimetersOfMercury, v2 = it.diastolic.inMillimetersOfMercury, source = "watch")
            }
        }
        if (HealthPermission.getReadPermission(RestingHeartRateRecord::class) in g) runCatching {
            c.readRecords(ReadRecordsRequest(RestingHeartRateRecord::class, f)).records.forEach {
                out += Vital(time = it.time.toEpochMilli(), kind = "rhr", v1 = it.beatsPerMinute.toDouble(), source = "watch")
            }
        }
        if (HealthPermission.getReadPermission(OxygenSaturationRecord::class) in g) runCatching {
            c.readRecords(ReadRecordsRequest(OxygenSaturationRecord::class, f)).records.forEach {
                out += Vital(time = it.time.toEpochMilli(), kind = "spo2", v1 = it.percentage.value, source = "watch")
            }
        }
        if (HealthPermission.getReadPermission(HeartRateRecord::class) in g) runCatching {
            // one point per record (avg of its samples) keeps the list small
            c.readRecords(ReadRecordsRequest(HeartRateRecord::class, TimeRangeFilter.after(Instant.now().minusSeconds(minOf(days, 7) * 86_400)), pageSize = 500)).records.forEach { r ->
                if (r.samples.isNotEmpty()) out += Vital(time = r.endTime.toEpochMilli(), kind = "hr", v1 = r.samples.map { it.beatsPerMinute }.average(),
                    v2 = r.samples.minOf { it.beatsPerMinute }.toDouble(), v3 = r.samples.maxOf { it.beatsPerMinute }.toDouble(), source = "watch")
            }
        }
        return out
    }

    /** ACC/AHA categories. */
    fun bpCategory(sys: Double, dia: Double): Pair<String, Int> = when {
        sys >= 180 || dia >= 120 -> "أزمة ضغط" to 3
        sys >= 140 || dia >= 90 -> "ضغط مرتفع (درجة ٢)" to 2
        sys >= 130 || dia >= 80 -> "ضغط مرتفع (درجة ١)" to 1
        sys >= 120 -> "مرتفع قليلاً" to 1
        sys < 90 || dia < 60 -> "ضغط واطي" to 1
        else -> "طبيعي" to 0
    }

    private val bpMedWords = listOf(
        "amlodipine", "أملوديبين", "norvasc", "نورفاسك", "losartan", "لوسارتان", "cozaar", "valsartan", "فالسارتان", "diovan", "ديوفان",
        "lisinopril", "ليزينوبريل", "enalapril", "captopril", "كابتوبريل", "concor", "كونكور", "bisoprolol", "بيسوبرولول", "atenolol", "أتينولول",
        "metoprolol", "ميتوبرولول", "hydrochlorothiazide", "هيدروكلوروثيازيد", "co-diovan", "exforge", "إكسفورج", "telmisartan", "micardis", "ميكارديس",
        "olmesartan", "candesartan", "atacand", "ضغط",
    )

    /** Rule-based notes from the readings plus medications and recent labs. Not a diagnosis. */
    fun advise(readings: List<Vital>, meds: List<Medication>, labs: List<LabResult>): List<Advice> {
        val out = mutableListOf<Advice>()
        val now = System.currentTimeMillis()
        val week = readings.filter { it.time >= now - 7 * 86_400_000L }
        val bp = readings.filter { it.kind == "bp" && it.v2 != null }.sortedByDescending { it.time }
        val onBpMeds = meds.any { m -> bpMedWords.any { w -> m.name.contains(w, true) || m.reason.contains(w, true) } }
        bp.firstOrNull()?.let { b ->
            val (cat, lvl) = bpCategory(b.v1, b.v2!!)
            if (lvl == 3) out += Advice(3, "قراءة ضغط عالية جداً (${b.v1.toInt()}/${b.v2.toInt()})",
                "استريح ٥ دقايق وقيس تاني. لو لسه فوق ١٨٠/١٢٠ أو معاها صداع شديد أو ألم في الصدر أو ضيق نفس أو زغللة أو تنميل — روح طوارئ فوراً أو اتصل بـ ٩٩٨ (الإسعاف في الإمارات).")
            val recent = bp.filter { it.time >= now - 14 * 86_400_000L }
            val highCount = recent.count { bpCategory(it.v1, it.v2!!).second >= 2 }
            if (lvl < 3 && highCount >= 2) out += Advice(2, "الضغط عالي في أكتر من قراءة",
                (if (onBpMeds) "إنت على دوا ضغط والقراءات لسه عالية ($highCount مرات في أسبوعين). كلم دكتورك يراجع الجرعة، وماتغيرش الدوا من نفسك."
                else "$highCount قراءات فوق ١٤٠/٩٠ في أسبوعين. ده محتاج كشف عند دكتور باطنة أو قلب علشان يتأكد ويقرر العلاج."))
            else if (lvl == 1) out += Advice(1, "الضغط $cat",
                "قلّل الملح والأكل الجاهز، امشي ٣٠ دقيقة أغلب الأيام، ونام كويس. قيس في نفس الميعاد كل يوم وانت قاعد ومرتاح.")
            if (b.v1 < 90 || b.v2 < 60) out += Advice(1, "الضغط واطي (${b.v1.toInt()}/${b.v2.toInt()})",
                "اشرب مية كفاية وقوم من القعدة براحة. لو معاه دوخة أو إغماء كلم دكتور" + if (onBpMeds) "، خصوصاً إنك على دوا ضغط." else ".")
        }
        val rhr = readings.filter { it.kind == "rhr" }.sortedByDescending { it.time }.take(7)
        if (rhr.size >= 3) {
            val avg = rhr.map { it.v1 }.average()
            if (avg > 100) out += Advice(2, "نبض الراحة عالي (${avg.toInt()})", "متوسط نبضك وانت مرتاح فوق ١٠٠. ممكن يكون من قلة نوم أو كافيين أو جفاف أو حرارة، بس لو مستمر محتاج كشف.")
            else if (avg < 50) out += Advice(1, "نبض الراحة واطي (${avg.toInt()})", "ده طبيعي عند الرياضيين. لو معاه دوخة أو تعب أو إغماء اكشف.")
        }
        val maxHr = week.filter { it.kind == "hr" }.mapNotNull { it.v3 }.maxOrNull()
        if (maxHr != null && maxHr > 185) out += Advice(1, "نبض عالي وصل ${maxHr.toInt()}", "لو ده كان وقت تمرين شديد فطبيعي. لو كان وانت مرتاح أو معاه خفقان أو دوخة، اكشف.")
        val spo2 = readings.filter { it.kind == "spo2" }.sortedByDescending { it.time }.firstOrNull()
        if (spo2 != null) {
            if (spo2.v1 < 90) out += Advice(3, "الأكسجين واطي جداً (${spo2.v1.toInt()}%)", "لو معاه ضيق نفس أو تعب شديد روح طوارئ فوراً (٩٩٨). قراءات الساعة ممكن تغلط، فاتأكد بجهاز إصبع.")
            else if (spo2.v1 < 94) out += Advice(2, "الأكسجين أقل من الطبيعي (${spo2.v1.toInt()}%)", "الطبيعي ٩٥٪ وأكتر. قيس تاني وانت قاعد، ولو فضل واطي كلم دكتور.")
        }
        // labs that support the picture
        val latest = labs.groupBy { it.test.lowercase() }.mapValues { it.value.maxBy { l -> l.date } }.values
        latest.filter { l -> l.value != null && ((l.high != null && l.value > l.high) || (l.low != null && l.value < l.low)) }
            .filter { l -> listOf("ldl", "كوليسترول", "cholesterol", "triglyceride", "دهون", "hba1c", "سكر", "glucose", "creatinine", "كرياتينين", "potassium", "بوتاسيوم", "sodium").any { l.test.contains(it, true) } }
            .take(3).forEach { l ->
                out += Advice(1, "تحليل ${l.test} خارج الطبيعي", "آخر نتيجة ${l.value} ${l.unit}. مع قراءات الضغط والقلب، اعرضها على دكتورك في الزيارة الجاية.")
            }
        if (out.isEmpty() && readings.isNotEmpty()) out += Advice(0, "قراءاتك كويسة", "استمر على المشي والنوم الكويس والأكل المتوازن، وقيس الضغط مرة في الأسبوع على الأقل.")
        return out.sortedByDescending { it.level }
    }
}
