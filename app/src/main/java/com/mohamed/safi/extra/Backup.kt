package com.mohamed.safi.extra

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.mohamed.safi.MainActivity
import com.mohamed.safi.SafiApp
import com.mohamed.safi.fitness.LifeDb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Full backup of Safi's data (databases, settings, receipt/document photos) into one zip.
 * The Claude API key is never written to the backup.
 */
object Backup {
    private val dbNames = listOf("safi.db", "safi_life.db", "safi_extra.db", "safi_diary.db", "safi_health.db")
    private val folders = listOf("receipts", "docs", "labs")

    private fun checkpoint() {
        runCatching { SafiApp.db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { it.moveToFirst() } }
        runCatching { LifeDb.get(SafiApp.instance).openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { it.moveToFirst() } }
        runCatching { ExtraDb.get().openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { it.moveToFirst() } }
        runCatching { com.mohamed.safi.diary.DiaryDb.get().openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { it.moveToFirst() } }
        runCatching { com.mohamed.safi.health.HealthDb.get().openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { it.moveToFirst() } }
    }

    private fun stripKey(xml: String) = xml.replace(Regex("<string name=\"(apiKey|key_[a-z]+)\">.*?</string>", RegexOption.DOT_MATCHES_ALL), "")

    fun write(ctx: Context, out: OutputStream) {
        checkpoint()
        ZipOutputStream(out.buffered()).use { zip ->
            fun put(name: String, f: File) {
                if (!f.exists() || f.isDirectory) return
                zip.putNextEntry(ZipEntry(name))
                if (name.startsWith("shared_prefs/")) zip.write(stripKey(f.readText()).toByteArray())
                else f.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
            for (db in dbNames) {
                val f = ctx.getDatabasePath(db)
                put("databases/$db", f)
                put("databases/$db-wal", File(f.path + "-wal"))
            }
            File(ctx.applicationInfo.dataDir, "shared_prefs").listFiles()?.forEach { put("shared_prefs/${it.name}", it) }
            for (folder in folders) File(ctx.filesDir, folder).listFiles()?.forEach { put("files/$folder/${it.name}", it) }
            zip.putNextEntry(ZipEntry("safi-backup.txt"))
            zip.write("Safi backup ${java.time.LocalDateTime.now()}".toByteArray())
            zip.closeEntry()
        }
    }

    suspend fun export(ctx: Context, uri: Uri) = withContext(Dispatchers.IO) {
        ctx.contentResolver.openOutputStream(uri, "wt")?.use { write(ctx, it) } ?: throw IllegalStateException("مقدرتش أكتب الملف")
    }

    /** Weekly copy in Downloads/Safi (Android 10+). */
    suspend fun autoToDownloads(ctx: Context): Boolean = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < 29) return@withContext false
        runCatching {
            val name = "safi-auto-backup.zip"
            val cr = ctx.contentResolver
            val coll = MediaStore.Downloads.EXTERNAL_CONTENT_URI
            var uri: Uri? = null
            cr.query(coll, arrayOf(MediaStore.Downloads._ID), "${MediaStore.Downloads.DISPLAY_NAME}=?", arrayOf(name), null)?.use { c ->
                if (c.moveToFirst()) uri = android.content.ContentUris.withAppendedId(coll, c.getLong(0))
            }
            if (uri == null) {
                uri = cr.insert(coll, ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, name)
                    put(MediaStore.Downloads.MIME_TYPE, "application/zip")
                    put(MediaStore.Downloads.RELATIVE_PATH, "Download/Safi")
                })
            }
            cr.openOutputStream(uri!!, "wt")!!.use { write(ctx, it) }
            true
        }.getOrDefault(false)
    }

    /** Restores and restarts the app. Keeps the current API key. */
    suspend fun restore(ctx: Context, uri: Uri) {
        val keys = SafiApp.prefs.allKeys()
        withContext(Dispatchers.IO) {
            val tmp = File(ctx.cacheDir, "restore.zip")
            ctx.contentResolver.openInputStream(uri)?.use { i -> tmp.outputStream().use { i.copyTo(it) } }
                ?: throw IllegalStateException("مقدرتش أفتح الملف")
            val names = ZipInputStream(tmp.inputStream()).use { z -> generateSequence { z.nextEntry }.map { it.name }.toList() }
            if ("databases/safi.db" !in names) throw IllegalStateException("الملف ده مش نسخة احتياطية من ${com.mohamed.safi.AppName.v}")

            runCatching { SafiApp.db.close() }
            runCatching { LifeDb.closeAll() }
            runCatching { ExtraDb.closeAll() }
            runCatching { com.mohamed.safi.diary.DiaryDb.closeAll() }
            runCatching { com.mohamed.safi.health.HealthDb.closeAll() }
            for (db in dbNames) {
                val f = ctx.getDatabasePath(db)
                File(f.path + "-wal").delete(); File(f.path + "-shm").delete()
            }
            ZipInputStream(tmp.inputStream()).use { z ->
                var e = z.nextEntry
                while (e != null) {
                    val target: File? = when {
                        e.name.startsWith("databases/") -> ctx.getDatabasePath(e.name.removePrefix("databases/"))
                        e.name.startsWith("shared_prefs/") -> File(ctx.applicationInfo.dataDir, e.name)
                        e.name.startsWith("files/") -> File(ctx.filesDir, e.name.removePrefix("files/"))
                        else -> null
                    }
                    if (target != null && !e.isDirectory && !e.name.contains("..")) {
                        target.parentFile?.mkdirs()
                        if (e.name == "shared_prefs/safi.xml" && keys.isNotEmpty()) {
                            val inject = keys.entries.joinToString("\n") { (k, v) -> "<string name=\"$k\">${v.replace("&", "&amp;").replace("<", "&lt;")}</string>" }
                            val xml = String(z.readBytes()).replace("</map>", "$inject\n</map>")
                            target.writeText(xml)
                        } else {
                            target.outputStream().use { copy(z, it) }
                        }
                    }
                    e = z.nextEntry
                }
            }
            tmp.delete()
        }
        val i = Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        ctx.startActivity(i)
        Runtime.getRuntime().exit(0)
    }

    private fun copy(i: InputStream, o: OutputStream) {
        val buf = ByteArray(16 * 1024)
        while (true) {
            val n = i.read(buf)
            if (n <= 0) break
            o.write(buf, 0, n)
        }
    }
}
