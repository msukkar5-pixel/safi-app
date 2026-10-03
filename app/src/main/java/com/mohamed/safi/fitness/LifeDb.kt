package com.mohamed.safi.fitness

import android.content.Context
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
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "weights", indices = [Index("time")])
data class WeightEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val time: Long = System.currentTimeMillis(),
    val kg: Double,
    val bodyFat: Double? = null,
    val waistCm: Double? = null,
    val source: String = "manual",       // manual | watch
)

@Entity(tableName = "foods", indices = [Index("time")])
data class FoodEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val time: Long = System.currentTimeMillis(),
    val meal: String = "",                // فطار | غدا | عشا | سناك | قبل التمرين | بعد التمرين
    val text: String,
    val kcal: Double = 0.0,
    val protein: Double = 0.0,
    val carbs: Double = 0.0,
    val fat: Double = 0.0,
)

@Entity(tableName = "supplements")
data class Supplement(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val dose: String = "",
    val times: String = "",              // "08:00,21:00"
    val note: String = "",
    val active: Boolean = true,
)

@Entity(tableName = "sessions", indices = [Index("start")])
data class WorkoutSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val start: Long = System.currentTimeMillis(),
    val end: Long? = null,
    val dayName: String = "",
    val note: String = "",
)

@Entity(tableName = "sets", indices = [Index("sessionId"), Index("exerciseId")])
data class WorkoutSet(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val exerciseId: String,
    val exerciseName: String,
    val reps: Int,
    val kg: Double,
    val time: Long = System.currentTimeMillis(),
)

@Entity(tableName = "water")
data class WaterDay(
    @PrimaryKey val day: String,          // yyyy-MM-dd
    val cups: Int,
)

@Dao
interface LifeDao {
    @Insert suspend fun insertWeight(w: WeightEntry): Long
    @Delete suspend fun deleteWeight(w: WeightEntry)
    @Query("SELECT * FROM weights ORDER BY time DESC") fun weights(): Flow<List<WeightEntry>>
    @Query("SELECT * FROM weights ORDER BY time DESC") suspend fun weightsNow(): List<WeightEntry>
    @Query("SELECT COUNT(*) FROM weights WHERE source = 'watch' AND time = :t") suspend fun watchWeightExists(t: Long): Int

    @Insert suspend fun insertFood(f: FoodEntry): Long
    @Delete suspend fun deleteFood(f: FoodEntry)
    @Query("SELECT * FROM foods WHERE time BETWEEN :from AND :to ORDER BY time") fun foodsBetween(from: Long, to: Long): Flow<List<FoodEntry>>
    @Query("SELECT * FROM foods WHERE time BETWEEN :from AND :to ORDER BY time") suspend fun foodsBetweenNow(from: Long, to: Long): List<FoodEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertSupplement(s: Supplement): Long
    @Delete suspend fun deleteSupplement(s: Supplement)
    @Query("SELECT * FROM supplements ORDER BY name") fun supplements(): Flow<List<Supplement>>
    @Query("SELECT * FROM supplements WHERE active = 1") suspend fun supplementsNow(): List<Supplement>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertSession(s: WorkoutSession): Long
    @Delete suspend fun deleteSession(s: WorkoutSession)
    @Query("SELECT * FROM sessions ORDER BY start DESC LIMIT :n") fun sessions(n: Int): Flow<List<WorkoutSession>>
    @Query("SELECT * FROM sessions WHERE start BETWEEN :from AND :to ORDER BY start") suspend fun sessionsBetweenNow(from: Long, to: Long): List<WorkoutSession>
    @Query("SELECT * FROM sessions WHERE `end` IS NULL ORDER BY start DESC LIMIT 1") fun openSession(): Flow<WorkoutSession?>

    @Insert suspend fun insertSet(s: WorkoutSet): Long
    @Delete suspend fun deleteSet(s: WorkoutSet)
    @Query("SELECT * FROM sets WHERE sessionId = :sid ORDER BY time") fun setsFor(sid: Long): Flow<List<WorkoutSet>>
    @Query("SELECT * FROM sets WHERE sessionId = :sid ORDER BY time") suspend fun setsForNow(sid: Long): List<WorkoutSet>
    @Query("SELECT * FROM sets WHERE exerciseId = :ex ORDER BY time DESC LIMIT 60") suspend fun historyFor(ex: String): List<WorkoutSet>
    @Query("SELECT COUNT(*) FROM sets WHERE sessionId = :sid") suspend fun setCount(sid: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun setWater(w: WaterDay)
    @Query("SELECT * FROM water WHERE day = :day") fun water(day: String): Flow<WaterDay?>
    @Query("SELECT * FROM water WHERE day = :day") suspend fun waterNow(day: String): WaterDay?
}

@Database(
    entities = [WeightEntry::class, FoodEntry::class, Supplement::class, WorkoutSession::class, WorkoutSet::class, WaterDay::class],
    version = 1,
    exportSchema = false,
)
abstract class LifeDb : RoomDatabase() {
    abstract fun dao(): LifeDao

    companion object {
        @Volatile private var inst: LifeDb? = null
        fun get(ctx: Context): LifeDb = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(ctx.applicationContext, LifeDb::class.java, "safi_life.db").build().also { inst = it }
        }
    }
}
