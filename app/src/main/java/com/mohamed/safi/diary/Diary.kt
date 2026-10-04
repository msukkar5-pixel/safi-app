package com.mohamed.safi.diary

import android.content.Context
import androidx.core.content.edit
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
import com.mohamed.safi.data.isoLocal
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray

@Entity(tableName = "entries", indices = [Index("time")])
data class DiaryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val time: Long = System.currentTimeMillis(),
    val text: String,
    val mood: String = "",          // emoji the user picked or the analysis suggested
    val analysis: String = "",      // AI analysis of this day
    val updatedAt: Long = System.currentTimeMillis(),
)

@Dao
interface DiaryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(e: DiaryEntry): Long
    @Delete suspend fun delete(e: DiaryEntry)
    @Query("SELECT * FROM entries ORDER BY time DESC") fun all(): Flow<List<DiaryEntry>>
    @Query("SELECT * FROM entries ORDER BY time DESC") suspend fun allNow(): List<DiaryEntry>
    @Query("SELECT * FROM entries WHERE time BETWEEN :from AND :to ORDER BY time") suspend fun between(from: Long, to: Long): List<DiaryEntry>
    @Query("SELECT * FROM entries WHERE id = :id") suspend fun get(id: Long): DiaryEntry?
}

@Database(entities = [DiaryEntry::class], version = 1, exportSchema = false)
abstract class DiaryDb : RoomDatabase() {
    abstract fun dao(): DiaryDao

    companion object {
        @Volatile private var inst: DiaryDb? = null
        fun get(ctx: Context = SafiApp.instance): DiaryDb = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(ctx.applicationContext, DiaryDb::class.java, "safi_diary.db").build().also { inst = it }
        }
        fun closeAll() { inst?.close(); inst = null }
        val dao: DiaryDao get() = get().dao()
    }
}

/** AI reading of the journal: one day, events over a period, and personality from everything written. */
object DiaryAI {
    private const val CARE = """You are a warm, honest and insightful journaling companion. Write in Egyptian Arabic, Western digits, short headings and bullets.
You are not a therapist and must not diagnose any condition. If the writing shows real distress (hopelessness, thoughts of self-harm),
gently encourage talking to someone trusted or a professional, and keep that part kind and brief."""

    private fun sp() = SafiApp.instance.getSharedPreferences("safi_diary", Context.MODE_PRIVATE)
    var lastEvents: String get() = sp().getString("events", "") ?: ""; set(v) = sp().edit { putString("events", v) }
    var lastEventsAt: Long get() = sp().getLong("eventsAt", 0); set(v) = sp().edit { putLong("eventsAt", v) }
    var lastPersonality: String get() = sp().getString("personality", "") ?: ""; set(v) = sp().edit { putString("personality", v) }
    var lastPersonalityAt: Long get() = sp().getLong("personalityAt", 0); set(v) = sp().edit { putLong("personalityAt", v) }

    private fun block(list: List<DiaryEntry>, maxChars: Int): String {
        val sb = StringBuilder()
        for (e in list.sortedBy { it.time }) {
            val line = "[${isoLocal(e.time)}${if (e.mood.isNotBlank()) " ${e.mood}" else ""}]\n${e.text.trim()}\n\n"
            if (sb.length + line.length > maxChars) break
            sb.append(line)
        }
        return sb.toString()
    }

    /** Fixes punctuation / splits sentences of dictated text without changing meaning. */
    suspend fun tidy(text: String): String = Claude.call(
        "You clean up dictated Arabic text: add punctuation and paragraphs, fix obvious speech-to-text mistakes. " +
            "Keep the same words, dialect and meaning. Do not add or remove content. Reply with the text only.",
        JSONArray().put(Claude.userText(text)), SafiApp.prefs.fastModel, 3000,
    ).trim()

    suspend fun analyzeDay(e: DiaryEntry): String {
        val recent = DiaryDb.dao.between(e.time - 14L * 86_400_000L, e.time - 1).takeLast(7)
        val prompt = """
            ${if (recent.isNotEmpty()) "Context — the previous days (short):\n" + block(recent, 6000) else ""}
            Today's entry:
            ${e.text}

            Write "تحليل اليوم":
            - المود العام (one emoji + one line)
            - أهم اللي حصل (bullets)
            - اللي كان كويس
            - اللي ضايقك أو شغل بالك
            - ملاحظة لاحظتها (connect to previous days if relevant)
            - خطوة صغيرة لبكرة
            End with one line: MOOD=<single emoji>
        """.trimIndent()
        val out = Claude.call(CARE, JSONArray().put(Claude.userText(prompt)), SafiApp.prefs.model, 1500)
        val mood = Regex("MOOD\\s*=\\s*(\\S+)").find(out)?.groupValues?.get(1) ?: e.mood
        val clean = out.replace(Regex("MOOD\\s*=\\s*\\S+"), "").trim()
        DiaryDb.dao.upsert(e.copy(analysis = clean, mood = e.mood.ifBlank { mood }, updatedAt = System.currentTimeMillis()))
        return clean
    }

    suspend fun analyzeEvents(days: Int): String {
        val from = System.currentTimeMillis() - days * 86_400_000L
        val list = DiaryDb.dao.between(from, System.currentTimeMillis())
        if (list.isEmpty()) throw ClaudeException("مفيش مذكرات في الفترة دي")
        val prompt = """
            Journal entries from the last $days days:
            ${block(list, 60_000)}

            Write "تحليل الأحداث" for this period:
            - أهم الأحداث بالترتيب
            - الناس اللي اتكررت في كلامك وإحساسك ناحيتهم
            - المواضيع اللي بتتكرر (شغل، فلوس، أسرة، صحة…)
            - إيه اللي بيرفع مودك وإيه اللي بينزله (patterns with examples)
            - أشياء مفتوحة لسه محتاجة قرار أو متابعة
            - 3 اقتراحات عملية
        """.trimIndent()
        val out = Claude.call(CARE, JSONArray().put(Claude.userText(prompt)), SafiApp.prefs.model, 2500)
        lastEvents = out.trim(); lastEventsAt = System.currentTimeMillis()
        return lastEvents
    }

    suspend fun analyzePersonality(): String {
        val list = DiaryDb.dao.allNow()
        if (list.size < 5) throw ClaudeException("محتاج 5 مذكرات على الأقل علشان التحليل يبقى له معنى (عندك ${list.size})")
        // most recent first, capped
        val text = block(list.take(120), 90_000)
        val prompt = """
            These are personal journal entries written over time (${list.size} entries, showing up to the latest 120):
            $text

            Write "قراءة في الشخصية" based ONLY on what is written, citing short examples from the entries:
            - طريقة تفكيرك وأخذك للقرارات
            - القيم اللي واضح إنها مهمة لك
            - نقاط قوتك
            - نقاط محتاجة انتباه (blind spots) بلطف وصراحة
            - إزاي بتتعامل مع الضغط والمشاعر
            - علاقاتك وأدوارك (أب، ابن، زوج، مدير…) كما تظهر في الكتابة
            - إيه اللي اتغير عبر الوقت
            - 5 توصيات للنمو الشخصي
            Make clear this is a reflective reading of the journal, not a psychological assessment.
        """.trimIndent()
        val out = Claude.call(CARE, JSONArray().put(Claude.userText(prompt)), SafiApp.prefs.model, 3500)
        lastPersonality = out.trim(); lastPersonalityAt = System.currentTimeMillis()
        return lastPersonality
    }
}
