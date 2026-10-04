package com.mohamed.safi.extra

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

/** Identity / car / family documents with expiry reminders. Stored only on the phone. */
@Entity(tableName = "documents")
data class Doc(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,                 // الهوية، الإقامة، الجواز، رخصة السواقة…
    val owner: String = "",            // انا، مراتي، الأولاد…
    val number: String = "",
    val expiry: Long? = null,
    val remindDays: Int = 30,
    val photoPath: String? = null,
    val note: String = "",
)

@Entity(tableName = "goals")
data class SavingGoal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val target: Double,
    val currency: String = "AED",
    val deadline: Long? = null,
    val saved: Double = 0.0,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "lessons")
data class Lesson(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val child: String,
    val subject: String,
    val teacher: String = "",
    val schedule: String = "",         // e.g. "السبت والتلات 5 م"
    val monthlyFeeEgp: Double = 0.0,
    val sessionsPerMonth: Int = 0,
    val phone: String = "",
    val active: Boolean = true,
)

@Dao
interface ExtraDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertDoc(d: Doc): Long
    @Delete suspend fun deleteDoc(d: Doc)
    @Query("SELECT * FROM documents ORDER BY CASE WHEN expiry IS NULL THEN 1 ELSE 0 END, expiry") fun docs(): Flow<List<Doc>>
    @Query("SELECT * FROM documents") suspend fun docsNow(): List<Doc>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertGoal(g: SavingGoal): Long
    @Delete suspend fun deleteGoal(g: SavingGoal)
    @Query("SELECT * FROM goals ORDER BY createdAt") fun goals(): Flow<List<SavingGoal>>
    @Query("SELECT * FROM goals") suspend fun goalsNow(): List<SavingGoal>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertLesson(l: Lesson): Long
    @Delete suspend fun deleteLesson(l: Lesson)
    @Query("SELECT * FROM lessons ORDER BY child, subject") fun lessons(): Flow<List<Lesson>>
    @Query("SELECT * FROM lessons WHERE active = 1") suspend fun lessonsNow(): List<Lesson>
}

@Database(entities = [Doc::class, SavingGoal::class, Lesson::class], version = 1, exportSchema = false)
abstract class ExtraDb : RoomDatabase() {
    abstract fun dao(): ExtraDao

    companion object {
        @Volatile private var inst: ExtraDb? = null
        fun get(ctx: Context = SafiApp.instance): ExtraDb = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(ctx.applicationContext, ExtraDb::class.java, "safi_extra.db").build().also { inst = it }
        }
        fun closeAll() { inst?.close(); inst = null }
        val dao: ExtraDao get() = get().dao()
    }
}
