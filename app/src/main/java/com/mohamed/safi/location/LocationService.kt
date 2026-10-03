package com.mohamed.safi.location

import android.Manifest
import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.mohamed.safi.R
import com.mohamed.safi.SafiApp
import com.mohamed.safi.data.LocationLog
import com.mohamed.safi.data.SavedPlace
import com.mohamed.safi.notify.Notifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Locale

object LocationLogger {
    private val lock = Mutex()
    private const val SAME_PLACE_M = 150f

    fun distance(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Float {
        val r = FloatArray(1)
        Location.distanceBetween(lat1, lng1, lat2, lng2, r)
        return r[0]
    }

    @Suppress("DEPRECATION")
    fun geocode(ctx: Context, lat: Double, lng: Double): String {
        if (!Geocoder.isPresent()) return ""
        return runCatching {
            val list = Geocoder(ctx, Locale("ar")).getFromLocation(lat, lng, 1)
            val a = list?.firstOrNull() ?: return ""
            listOfNotNull(
                a.featureName?.takeIf { f -> f.isNotBlank() && f.any { !it.isDigit() } && f != a.thoroughfare },
                a.thoroughfare,
                a.subLocality,
                a.locality ?: a.adminArea,
            ).distinct().take(3).joinToString("، ")
        }.getOrDefault("")
    }

    private fun savedName(places: List<SavedPlace>, lat: Double, lng: Double): String? =
        places.minByOrNull { distance(lat, lng, it.lat, it.lng) }
            ?.takeIf { distance(lat, lng, it.lat, it.lng) < SAME_PLACE_M }?.name

    suspend fun handle(ctx: Context, loc: Location): Unit = lock.withLock {
        if (loc.hasAccuracy() && loc.accuracy > 200f) return@withLock Unit
        val dao = SafiApp.db.dao()
        val now = System.currentTimeMillis()
        val last = dao.lastLocation()
        if (last != null && distance(last.lat, last.lng, loc.latitude, loc.longitude) < SAME_PLACE_M) {
            dao.updateLocation(last.copy(endTime = now))
        } else {
            val name = savedName(dao.placesNow(), loc.latitude, loc.longitude)
                ?: geocode(ctx, loc.latitude, loc.longitude)
            dao.insertLocation(
                LocationLog(
                    startTime = now, endTime = now, lat = loc.latitude, lng = loc.longitude,
                    accuracy = if (loc.hasAccuracy()) loc.accuracy else 0f, placeName = name,
                ),
            )
        }
        Unit
    }

    /** Where Mohamed was at time t (within 45 minutes), used to tag expenses. */
    suspend fun placeAt(t: Long): LocationLog? {
        val l = SafiApp.db.dao().locationAt(t + 60_000) ?: return null
        return if (t - l.endTime <= 45 * 60_000L) l else null
    }

    suspend fun nameHere(ctx: Context, name: String, lat: Double, lng: Double) {
        val dao = SafiApp.db.dao()
        dao.upsertPlace(SavedPlace(name = name, lat = lat, lng = lng))
        dao.renameLocationsNear(lat, lng, name)
    }
}

class LocationService : Service(), LocationListener {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var lm: LocationManager? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val n = NotificationCompat.Builder(this, Notifier.CH_LOC)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle("صافي بيسجل أماكنك")
            .setContentText("علشان يربط كل مصروف بمكانه")
            .setContentIntent(Notifier.openAppIntent(this, "places", 77))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
        try {
            ServiceCompat.startForeground(this, 4242, n, type)
        } catch (e: Exception) {
            stopSelf()
            return
        }
        startUpdates()
    }

    @SuppressLint("MissingPermission")
    private fun startUpdates() {
        if (!hasPermission(this)) {
            stopSelf()
            return
        }
        val m = getSystemService(LocationManager::class.java) ?: run { stopSelf(); return }
        lm = m
        val interval = SafiApp.prefs.locationIntervalMin.coerceIn(1, 60) * 60_000L
        val providers = m.getProviders(true)
        val chosen = when {
            Build.VERSION.SDK_INT >= 31 && LocationManager.FUSED_PROVIDER in providers -> listOf(LocationManager.FUSED_PROVIDER)
            else -> listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER).filter { it in providers }
        }
        for (p in chosen) {
            runCatching { m.requestLocationUpdates(p, interval, 50f, this, Looper.getMainLooper()) }
        }
        chosen.firstNotNullOfOrNull { runCatching { m.getLastKnownLocation(it) }.getOrNull() }?.let { onLocationChanged(it) }
    }

    override fun onLocationChanged(location: Location) {
        scope.launch { runCatching { LocationLogger.handle(applicationContext, location) } }
    }

    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        runCatching { lm?.removeUpdates(this) }
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        fun hasPermission(ctx: Context) =
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        fun start(ctx: Context) {
            if (!hasPermission(ctx)) return
            ContextCompat.startForegroundService(ctx, Intent(ctx, LocationService::class.java))
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, LocationService::class.java))
        }

        @SuppressLint("MissingPermission")
        fun currentLocation(ctx: Context): Location? {
            if (!hasPermission(ctx)) return null
            val m = ctx.getSystemService(LocationManager::class.java) ?: return null
            return m.getProviders(true).mapNotNull { runCatching { m.getLastKnownLocation(it) }.getOrNull() }
                .maxByOrNull { it.time }
        }
    }
}
