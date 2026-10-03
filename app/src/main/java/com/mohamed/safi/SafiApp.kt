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
        Notifier.createChannels(this)
        DailyWorker.schedule(this, replace = false)
    }

    companion object {
        lateinit var instance: SafiApp
            private set
        val db: AppDatabase by lazy { AppDatabase.build(instance) }
        val prefs: Prefs by lazy { Prefs(instance) }
    }
}
