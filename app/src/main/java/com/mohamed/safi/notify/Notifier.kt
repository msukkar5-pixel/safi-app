package com.mohamed.safi.notify

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.mohamed.safi.MainActivity
import com.mohamed.safi.R

object Notifier {
    const val CH_REMIND = "reminders"
    const val CH_ALARM = "alarm_reminders"
    const val CH_MONEY = "money"
    const val CH_DAILY = "daily"
    const val CH_LOC = "location"
    const val CH_PRAYER = "prayer"

    fun createChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CH_REMIND, "التذكيرات والمواعيد", NotificationManager.IMPORTANCE_HIGH).apply {
                enableVibration(true)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_ALARM, "تذكيرات بصوت المنبه", NotificationManager.IMPORTANCE_HIGH).apply {
                enableVibration(true)
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_MONEY, "المصاريف من رسايل البنك", NotificationManager.IMPORTANCE_DEFAULT),
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_DAILY, "ملخص الصبح والفواتير", NotificationManager.IMPORTANCE_HIGH),
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_PRAYER, "مواعيد الصلاة", NotificationManager.IMPORTANCE_HIGH).apply { enableVibration(true) },
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_LOC, "تسجيل الأماكن", NotificationManager.IMPORTANCE_MIN),
        )
    }

    fun canNotify(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun openAppIntent(ctx: Context, route: String? = null, requestCode: Int = 0): PendingIntent {
        val i = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (route != null) putExtra("route", route)
        }
        return PendingIntent.getActivity(ctx, requestCode, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    @SuppressLint("MissingPermission")
    fun show(
        ctx: Context,
        id: Int,
        channel: String,
        title: String,
        text: String,
        route: String? = null,
        actions: List<NotificationCompat.Action> = emptyList(),
        fullScreen: Boolean = false,
        ongoing: Boolean = false,
    ) {
        if (!canNotify(ctx)) return
        val content = openAppIntent(ctx, route, id)
        val b = NotificationCompat.Builder(ctx, channel)
            .setSmallIcon(R.drawable.ic_notify)
            .setColor(0xFF0F6E5C.toInt())
            .setContentTitle(com.mohamed.safi.ui.tr(title))
            .setContentText(com.mohamed.safi.ui.tr(text))
            .setStyle(NotificationCompat.BigTextStyle().bigText(com.mohamed.safi.ui.tr(text)))
            .setContentIntent(content)
            .setAutoCancel(!ongoing)
            .setOngoing(ongoing)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
        if (fullScreen) {
            b.setCategory(NotificationCompat.CATEGORY_ALARM)
            b.setFullScreenIntent(content, true)
        }
        actions.forEach { b.addAction(it) }
        NotificationManagerCompat.from(ctx).notify(id, b.build())
    }

    fun cancel(ctx: Context, id: Int) = NotificationManagerCompat.from(ctx).cancel(id)
}
