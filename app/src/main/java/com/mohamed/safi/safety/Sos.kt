package com.mohamed.safi.safety

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.mohamed.safi.SafiApp
import com.mohamed.safi.data.zone
import org.json.JSONArray
import org.json.JSONObject

/**
 * The emergency button: sends "I need help" with the phone's location to the contacts the user chose.
 * With the SMS permission it sends by itself; otherwise it opens the SMS app ready to send.
 */
object Sos {
    data class Contact(val name: String, val phone: String)
    private fun sp() = SafiApp.instance.getSharedPreferences("safi_sos", Context.MODE_PRIVATE)

    var contacts: List<Contact>
        get() = runCatching {
            val a = JSONArray(sp().getString("contacts", "[]"))
            (0 until a.length()).map { a.getJSONObject(it).let { o -> Contact(o.optString("n"), o.optString("p")) } }
        }.getOrDefault(emptyList())
        set(v) = sp().edit { putString("contacts", JSONArray(v.take(5).map { JSONObject().put("n", it.name).put("p", it.phone) }).toString()) }
    var note: String get() = sp().getString("note", "").orEmpty(); set(v) = sp().edit { putString("note", v.take(100)) }

    /** The freshest location the phone already knows (GPS, network or passive). */
    fun lastLocation(ctx: Context): Location? {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) return null
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        return runCatching {
            lm.getProviders(true).mapNotNull { @Suppress("MissingPermission") lm.getLastKnownLocation(it) }.maxByOrNull { it.time }
        }.getOrNull()
    }

    fun message(ctx: Context): String {
        val loc = lastLocation(ctx)
        val where = loc?.let { String.format(java.util.Locale.US, "https://maps.google.com/?q=%.6f,%.6f", it.latitude, it.longitude) }
            ?: com.mohamed.safi.faith.Prayer.city.ifBlank { "" }
        val time = java.time.LocalTime.now(zone).let { String.format(java.util.Locale.US, "%02d:%02d", it.hour, it.minute) }
        return "🆘 محتاج مساعدة ضروري. مكاني: $where ($time)" + if (note.isNotBlank()) "\n$note" else ""
    }

    fun canSendDirect(ctx: Context) = ContextCompat.checkSelfPermission(ctx, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED

    /** Sends to every contact. Returns how many were sent directly (0 = the SMS app was opened instead). */
    fun send(ctx: Context): Int {
        val list = contacts.filter { it.phone.isNotBlank() }
        if (list.isEmpty()) return -1
        val msg = message(ctx)
        if (canSendDirect(ctx)) {
            val sms = if (android.os.Build.VERSION.SDK_INT >= 31) ctx.getSystemService(SmsManager::class.java) else @Suppress("DEPRECATION") SmsManager.getDefault()
            var n = 0
            list.forEach { c -> runCatching { sms.sendMultipartTextMessage(c.phone, null, sms.divideMessage(msg), null, null); n++ } }
            if (n > 0) return n
        }
        val uri = Uri.parse("smsto:" + list.joinToString(";") { it.phone })
        runCatching { ctx.startActivity(Intent(Intent.ACTION_SENDTO, uri).putExtra("sms_body", msg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        return 0
    }

    /** Ambulance number by country (the dialer opens; nothing is called automatically). */
    fun ambulance(): String = when (com.mohamed.safi.faith.Prayer.country.uppercase()) {
        "EG" -> "123"; "AE" -> "998"; "SA" -> "997"; else -> "112"
    }
}
