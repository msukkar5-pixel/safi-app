package com.mohamed.safi.faith

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.*

/** Mosques near a point, from OpenStreetMap (Overpass API, free, no key). */
object Mosques {
    data class Mosque(val name: String, val lat: Double, val lng: Double, val meters: Int)
    private val http by lazy { OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS).build() }

    suspend fun near(lat: Double, lng: Double, radius: Int): List<Mosque> = withContext(Dispatchers.IO) {
        val q = String.format(java.util.Locale.US,
            "[out:json][timeout:25];(node[\"amenity\"=\"place_of_worship\"][\"religion\"=\"muslim\"](around:%d,%.6f,%.6f);" +
                "way[\"amenity\"=\"place_of_worship\"][\"religion\"=\"muslim\"](around:%d,%.6f,%.6f););out center 80;",
            radius, lat, lng, radius, lat, lng)
        val body = http.newCall(Request.Builder().url("https://overpass-api.de/api/interpreter").post(FormBody.Builder().add("data", q).build())
            .header("User-Agent", "Safi-app").build()).execute().use { r -> if (!r.isSuccessful) error("HTTP ${r.code}"); r.body!!.string() }
        val els = JSONObject(body).optJSONArray("elements") ?: return@withContext emptyList()
        (0 until els.length()).mapNotNull { i ->
            val e = els.getJSONObject(i)
            val la = if (e.has("lat")) e.getDouble("lat") else e.optJSONObject("center")?.optDouble("lat") ?: return@mapNotNull null
            val lo = if (e.has("lon")) e.getDouble("lon") else e.optJSONObject("center")?.optDouble("lon") ?: return@mapNotNull null
            val tags = e.optJSONObject("tags")
            val name = tags?.optString("name:ar")?.ifBlank { null } ?: tags?.optString("name")?.ifBlank { null } ?: "مسجد"
            Mosque(name, la, lo, distance(lat, lng, la, lo))
        }.distinctBy { "${it.name}|${(it.lat * 1e4).roundToInt()}|${(it.lng * 1e4).roundToInt()}" }.sortedBy { it.meters }
    }

    fun distance(a1: Double, o1: Double, a2: Double, o2: Double): Int {
        val r = 6_371_000.0
        val dLat = Math.toRadians(a2 - a1); val dLng = Math.toRadians(o2 - o1)
        val h = sin(dLat / 2).pow(2) + cos(Math.toRadians(a1)) * cos(Math.toRadians(a2)) * sin(dLng / 2).pow(2)
        return (2 * r * asin(sqrt(h))).roundToInt()
    }
}
