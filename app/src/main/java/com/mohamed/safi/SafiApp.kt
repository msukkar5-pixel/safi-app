package com.mohamed.safi

import android.app.Application
import com.mohamed.safi.data.AppDatabase
import com.mohamed.safi.data.Prefs
import com.mohamed.safi.notify.DailyWorker
import com.mohamed.safi.notify.Notifier

class SafiApp : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
        CrashLog.install(this)
        com.mohamed.safi.ui.I18n.init(this)
        Notifier.createChannels(this)
        DailyWorker.schedule(this, replace = false)
    }

    companion object {
        lateinit var instance: SafiApp
            private set
        val db: AppDatabase by lazy { AppDatabase.build(instance) }
        val prefs: Prefs by lazy { Prefs(instance) }
        /** App-lifetime scope for work that must outlive a screen (e.g. an assistant request). */
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main)
    }
}

/** Saves the last crash so the app can show it (and the user can send it) on next launch. */
object CrashLog {
    private fun file(ctx: android.content.Context) = java.io.File(ctx.filesDir, "last_crash.txt")

    fun install(ctx: android.content.Context) {
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching {
                val sw = java.io.StringWriter()
                e.printStackTrace(java.io.PrintWriter(sw))
                val info = "Safi ${runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull()} • " +
                    "Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT}) • ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}\n" +
                    "thread: ${t.name} • ${java.util.Date()}\n\n"
                file(ctx).writeText(info + sw.toString().take(12000))
            }
            prev?.uncaughtException(t, e)
        }
    }

    fun pending(ctx: android.content.Context): String? = file(ctx).takeIf { it.exists() }?.readText()
    fun clear(ctx: android.content.Context) { file(ctx).delete() }
}
