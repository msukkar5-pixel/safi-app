package com.mohamed.safi.faith

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import com.mohamed.safi.SafiApp
import com.mohamed.safi.data.zone
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * One row per day. Each fard prayer holds a status code (see [PrayerStatus]); 0 = not logged yet.
 * [sunnah] is a bitmask of the rawatib + witr (see [PrayerLog.sunnahItems]).
 * [date] is ISO yyyy-MM-dd (LocalDate.toString), so string ranges sort correctly.
 */
@Entity(tableName = "prayer_day")
data class PrayerDayLog(
    @PrimaryKey val date: String,
    val fajr: Int = 0,
    val dhuhr: Int = 0,
    val asr: Int = 0,
    val maghrib: Int = 0,
    val isha: Int = 0,
    val sunnah: Int = 0,
) {
    val statuses: List<Int> get() = listOf(fajr, dhuhr, asr, maghrib, isha)
    fun status(i: Int): Int = statuses[i]
    fun with(i: Int, s: Int): PrayerDayLog = when (i) {
        0 -> copy(fajr = s); 1 -> copy(dhuhr = s); 2 -> copy(asr = s); 3 -> copy(maghrib = s); else -> copy(isha = s)
    }
    /** All five prayed on time (alone or in jamaa'a). */
    val allOnTime: Boolean get() = statuses.all { PrayerStatus.onTime(it) }
    val onTimeCount: Int get() = statuses.count { PrayerStatus.onTime(it) }
    fun hasSunnah(bit: Int) = sunnah and (1 shl bit) != 0
}

/** Make-up prayers still owed, per prayer index 0..4. */
@Entity(tableName = "qada")
data class QadaCount(@PrimaryKey val prayer: Int, val owed: Int = 0)

object PrayerStatus {
    const val NONE = 0
    const val ON_TIME = 1
    const val JAMAAH = 2
    const val LATE = 3
    const val QADA = 4
    const val MISSED = 5

    val choices = listOf(ON_TIME, JAMAAH, LATE, QADA, MISSED)

    fun label(s: Int) = when (s) {
        ON_TIME -> "في وقتها"
        JAMAAH -> "جماعة"
        LATE -> "متأخرة"
        QADA -> "قضاء"
        MISSED -> "فاتت"
        else -> "لسه"
    }

    fun onTime(s: Int) = s == ON_TIME || s == JAMAAH
}

@Dao
interface PrayerLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(d: PrayerDayLog)
    @Query("SELECT * FROM prayer_day WHERE date = :date") suspend fun dayNow(date: String): PrayerDayLog?
    @Query("SELECT * FROM prayer_day WHERE date = :date") fun day(date: String): Flow<PrayerDayLog?>
    @Query("SELECT * FROM prayer_day WHERE date BETWEEN :from AND :to ORDER BY date") fun range(from: String, to: String): Flow<List<PrayerDayLog>>
    @Query("SELECT * FROM prayer_day ORDER BY date DESC LIMIT :limit") fun recent(limit: Int): Flow<List<PrayerDayLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertQada(q: QadaCount)
    @Query("SELECT * FROM qada WHERE prayer = :prayer") suspend fun qadaNow(prayer: Int): QadaCount?
    @Query("SELECT * FROM qada ORDER BY prayer") fun qada(): Flow<List<QadaCount>>
}

@Database(entities = [PrayerDayLog::class, QadaCount::class], version = 1, exportSchema = false)
abstract class FaithDb : RoomDatabase() {
    abstract fun dao(): PrayerLogDao

    companion object {
        @Volatile private var inst: FaithDb? = null
        fun get(ctx: Context = SafiApp.instance): FaithDb = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(ctx.applicationContext, FaithDb::class.java, "safi_faith.db").build().also { inst = it }
        }
    }
}

/** Prayer tracker (متابعة الصلوات): repository helpers. Call the suspend functions on Dispatchers.IO. */
object PrayerLog {
    /** The five fard prayers, same spelling as [Prayer.names]. */
    val fard = listOf("الفجر", "الظهر", "العصر", "المغرب", "العشاء")

    /** Sunnah rawatib (12 rak'at) + witr; index = bit in [PrayerDayLog.sunnah]. */
    val sunnahItems = listOf(
        "ركعتين قبل الفجر",
        "أربع ركعات قبل الظهر",
        "ركعتين بعد الظهر",
        "ركعتين بعد المغرب",
        "ركعتين بعد العشاء",
        "الوتر",
    )

    fun dao() = FaithDb.get().dao()
    fun key(d: LocalDate): String = d.toString()
    fun today(): LocalDate = LocalDate.now(zone)

    /** Sets a prayer's status and keeps the qada counter in step with "فاتت". */
    suspend fun setStatus(date: LocalDate, prayer: Int, status: Int) {
        val db = FaithDb.get()
        db.withTransaction {
            val dao = db.dao()
            val cur = dao.dayNow(key(date)) ?: PrayerDayLog(key(date))
            val old = cur.status(prayer)
            if (old == status) return@withTransaction
            dao.upsert(cur.with(prayer, status))
            val delta = (if (status == PrayerStatus.MISSED) 1 else 0) - (if (old == PrayerStatus.MISSED) 1 else 0)
            if (delta != 0) {
                val q = dao.qadaNow(prayer) ?: QadaCount(prayer)
                dao.upsertQada(q.copy(owed = (q.owed + delta).coerceAtLeast(0)))
            }
        }
    }

    suspend fun toggleSunnah(date: LocalDate, bit: Int) {
        val dao = dao()
        val cur = dao.dayNow(key(date)) ?: PrayerDayLog(key(date))
        dao.upsert(cur.copy(sunnah = cur.sunnah xor (1 shl bit)))
    }

    /** Adds (or with a negative [delta], removes) owed make-up prayers. */
    suspend fun adjustQada(prayer: Int, delta: Int) {
        val dao = dao()
        val q = dao.qadaNow(prayer) ?: QadaCount(prayer)
        dao.upsertQada(q.copy(owed = (q.owed + delta).coerceAtLeast(0)))
    }

    /** Consecutive days with all 5 on time, ending today (or yesterday if today isn't complete yet). */
    fun streak(logs: List<PrayerDayLog>, today: LocalDate = today()): Int {
        val good = logs.filter { it.allOnTime }.map { it.date }.toHashSet()
        var d = if (key(today) in good) today else today.minusDays(1)
        var n = 0
        while (key(d) in good) { n++; d = d.minusDays(1) }
        return n
    }

    data class Stats(val logged: Int, val onTimePct: Int, val jamaahPct: Int)

    fun stats(logs: List<PrayerDayLog>): Stats {
        val all = logs.flatMap { it.statuses }.filter { it != PrayerStatus.NONE }
        if (all.isEmpty()) return Stats(0, 0, 0)
        val onTime = all.count { PrayerStatus.onTime(it) }
        val jam = all.count { it == PrayerStatus.JAMAAH }
        return Stats(all.size, onTime * 100 / all.size, jam * 100 / all.size)
    }
}
