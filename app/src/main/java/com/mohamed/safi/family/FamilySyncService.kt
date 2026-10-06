package com.mohamed.safi.family

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.mohamed.safi.R
import com.mohamed.safi.SafiApp
import com.mohamed.safi.notify.Notifier

/**
 * Visible, user-controlled LAN sync. It exchanges encrypted family cards only; it never records audio
 * or inspects conversations. Android keeps it alive as a foreground service while the user leaves it on.
 */
class FamilySyncService : Service() {
    override fun onCreate() {
        super.onCreate()
        Notifier.createChannels(this)
        val notification = NotificationCompat.Builder(this, Notifier.CH_FAMILY_SYNC)
            .setSmallIcon(R.drawable.ic_notify)
            .setColor(0xFF0F6E5C.toInt())
            .setContentTitle("مزامنة العيلة شغالة")
            .setContentText("تبادل مشفّر عبر الواي فاي فقط")
            .setStyle(NotificationCompat.BigTextStyle().bigText("تبادل مشفّر لبطاقات العيلة والتحديات المشتركة فقط. لا يوجد تسجيل صوت أو قراءة محادثات."))
            .setContentIntent(Notifier.openAppIntent(this, "family", 9301))
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
        startForeground(ID, notification)
        if (Family.joined && SafiApp.prefs.familySyncOn) Family.startLan(this)
        else stopSelf()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Family.joined || !SafiApp.prefs.familySyncOn) stopSelf()
        return START_STICKY
    }

    override fun onDestroy() {
        Family.stopLan()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val ID = 9301
        fun start(ctx: Context) {
            if (!Family.joined || !SafiApp.prefs.familySyncOn) return
            val i = Intent(ctx, FamilySyncService::class.java)
            runCatching { androidx.core.content.ContextCompat.startForegroundService(ctx, i) }
        }
        fun stop(ctx: Context) {
            runCatching { ctx.stopService(Intent(ctx, FamilySyncService::class.java)) }
        }
    }
}
