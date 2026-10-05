package com.mohamed.safi.car

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import com.mohamed.safi.SafiApp
import kotlinx.coroutines.flow.Flow

/** One periodic maintenance item (oil, filters, tyres…). Due by km, by time, or whichever comes first. */
@Entity(tableName = "maint")
data class MaintItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val intervalKm: Int? = null,
    val intervalDays: Int? = null,
    val lastKm: Int? = null,
    val lastDate: Long? = null,
    val note: String = "",
    val remind: Boolean = true,
    val sort: Int = 0,
    /** id of the matching row in the old safi.db car_items table, kept in sync so the morning brief stays right. */
    val legacyId: Long? = null,
    /** km-alert bookkeeping: the lastKm cycle we already alerted for, and how far (1 = close, 2 = due). */
    val kmAlertCycle: Int? = null,
    val kmAlertStage: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
)

/** Something Mohamed wants to do to the car: a modification, repair, accessory… */
@Entity(tableName = "plans")
data class CarPlan(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val category: String = "تعديل",      // تعديل | إصلاح | إكسسوار | تجميل | أداء
    val estCost: Double? = null,
    val priority: String = "مهم",         // عاجل | مهم | لاحقاً
    val status: String = "فكرة",          // فكرة | مخطط | اتعمل
    val note: String = "",
    val doneDate: Long? = null,
    val actualCost: Double? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

/** Odometer readings over time — used for km/day and fuel cost per 100 km. */
@Entity(tableName = "odo")
data class OdoLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val time: Long = System.currentTimeMillis(),
    val km: Int,
)

@Dao
interface CarDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertMaint(m: MaintItem): Long
    @Delete suspend fun deleteMaint(m: MaintItem)
    @Query("SELECT * FROM maint ORDER BY sort, id") fun maint(): Flow<List<MaintItem>>
    @Query("SELECT * FROM maint ORDER BY sort, id") suspend fun maintNow(): List<MaintItem>
    @Query("SELECT * FROM maint WHERE id = :id") suspend fun maintItem(id: Long): MaintItem?
    @Query("SELECT COUNT(*) FROM maint") suspend fun maintCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertPlan(p: CarPlan): Long
    @Delete suspend fun deletePlan(p: CarPlan)
    @Query("SELECT * FROM plans ORDER BY createdAt DESC") fun plans(): Flow<List<CarPlan>>

    @Insert suspend fun insertOdo(o: OdoLog): Long
    @Query("SELECT * FROM odo ORDER BY time") suspend fun odoLogs(): List<OdoLog>
    @Query("DELETE FROM odo WHERE km > :km") suspend fun deleteOdoAbove(km: Int)
}

@Database(entities = [MaintItem::class, CarPlan::class, OdoLog::class], version = 1, exportSchema = false)
abstract class CarDb : RoomDatabase() {
    abstract fun dao(): CarDao

    companion object {
        @Volatile private var inst: CarDb? = null
        fun get(ctx: Context = SafiApp.instance): CarDb = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(ctx.applicationContext, CarDb::class.java, "safi_car.db").build().also { inst = it }
        }
        fun closeAll() { inst?.close(); inst = null }
        val dao: CarDao get() = get().dao()
    }
}
