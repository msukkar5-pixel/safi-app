package com.mohamed.safi.docs

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
import androidx.room.Transaction
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mohamed.safi.SafiApp
import kotlinx.coroutines.flow.Flow

/** User-editable document type (الهوية، الإقامة…). [icon] is a key from [DocIcons]. */
@Entity(tableName = "doc_types")
data class DocType(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val icon: String = "folder",
    val sort: Int = 0,
)

/**
 * Extra pages of a document (page 2, 3…). Page 1 stays in `Doc.photoPath` (safi_extra.db),
 * so older documents keep working untouched.
 */
@Entity(tableName = "doc_pages", indices = [Index("docId")])
data class DocPage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val docId: Long,
    val idx: Int,
    val path: String,
)

/** Which type a document belongs to. Documents without a row are matched by title. */
@Entity(tableName = "doc_meta")
data class DocMeta(
    @PrimaryKey val docId: Long,
    val typeId: Long? = null,
)

@Dao
abstract class DocsDao {
    @Query("SELECT * FROM doc_types ORDER BY sort, id") abstract fun types(): Flow<List<DocType>>
    @Query("SELECT * FROM doc_types ORDER BY sort, id") abstract suspend fun typesNow(): List<DocType>
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun upsertType(t: DocType): Long
    @Delete abstract suspend fun deleteTypeRow(t: DocType)
    @Query("SELECT COALESCE(MAX(sort), 0) FROM doc_types") abstract suspend fun maxSort(): Int

    @Query("SELECT * FROM doc_pages ORDER BY docId, idx") abstract fun allPages(): Flow<List<DocPage>>
    @Query("SELECT * FROM doc_pages WHERE docId = :docId ORDER BY idx") abstract suspend fun pagesOf(docId: Long): List<DocPage>
    @Query("DELETE FROM doc_pages WHERE docId = :docId") abstract suspend fun clearPages(docId: Long)
    @Insert abstract suspend fun insertPages(p: List<DocPage>)

    @Query("SELECT * FROM doc_meta") abstract fun metas(): Flow<List<DocMeta>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun upsertMeta(m: DocMeta)
    @Query("DELETE FROM doc_meta WHERE docId = :docId") abstract suspend fun deleteMeta(docId: Long)
    @Query("UPDATE doc_meta SET typeId = NULL WHERE typeId = :typeId") abstract suspend fun detachType(typeId: Long)

    /** Pages after the first one, in order. */
    @Transaction
    open suspend fun replaceExtraPages(docId: Long, paths: List<String>) {
        clearPages(docId)
        if (paths.isNotEmpty()) insertPages(paths.mapIndexed { i, p -> DocPage(docId = docId, idx = i + 2, path = p) })
    }

    @Transaction
    open suspend fun deleteType(t: DocType) {
        detachType(t.id)
        deleteTypeRow(t)
    }

    @Transaction
    open suspend fun forgetDoc(docId: Long) {
        clearPages(docId)
        deleteMeta(docId)
    }
}

@Database(entities = [DocType::class, DocPage::class, DocMeta::class], version = 1, exportSchema = false)
abstract class DocsDb : RoomDatabase() {
    abstract fun dao(): DocsDao

    companion object {
        /** Default types, seeded once when the database is created. name to icon key. */
        val defaults = listOf(
            "الهوية الإماراتية" to "badge",
            "الإقامة" to "home",
            "الجواز" to "flight",
            "رخصة السواقة" to "card",
            "ملكية العربية" to "car",
            "تأمين العربية" to "shield",
            "التأمين الصحي" to "health",
            "عقد الإيجار (إيجاري)" to "key",
            "بطاقة العمل" to "work",
            "شهادات" to "school",
            "أخرى" to "folder",
        )

        @Volatile private var inst: DocsDb? = null
        fun get(ctx: Context = SafiApp.instance): DocsDb = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(ctx.applicationContext, DocsDb::class.java, "safi_docs.db")
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        defaults.forEachIndexed { i, (name, icon) ->
                            db.execSQL("INSERT INTO doc_types(name, icon, sort) VALUES(?, ?, ?)", arrayOf<Any>(name, icon, i))
                        }
                    }
                })
                .build().also { inst = it }
        }
        fun closeAll() { inst?.close(); inst = null }
        val dao: DocsDao get() = get().dao()
    }
}
