package com.mohamed.safi

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat

/** The name the user chose for the app / assistant (default "أثر"). */
object AppName {
    val v: String get() = SafiApp.prefs.appName

    /** Adds a home-screen icon carrying the chosen name (Android can't rename the installed icon). */
    fun pinShortcut(ctx: Context) {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(ctx)) {
            Toast.makeText(ctx, "الشاشة الرئيسية بتاعتك مش بتدعم ده", Toast.LENGTH_SHORT).show()
            return
        }
        val info = ShortcutInfoCompat.Builder(ctx, "named_${System.currentTimeMillis()}")
            .setShortLabel(v)
            .setLongLabel(v)
            .setIcon(IconCompat.createWithResource(ctx, R.mipmap.ic_launcher))
            .setIntent(Intent(ctx, MainActivity::class.java).setAction(Intent.ACTION_MAIN))
            .build()
        ShortcutManagerCompat.requestPinShortcut(ctx, info, null)
    }
}
