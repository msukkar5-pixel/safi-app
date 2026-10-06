package com.mohamed.safi.family

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Base64
import androidx.compose.runtime.mutableIntStateOf
import androidx.core.content.edit
import com.mohamed.safi.SafiApp
import com.mohamed.safi.data.zone
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom
import java.time.LocalDate
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** The latest update one family member chose to share. */
data class MemberCard(
    val id: String, val name: String, val role: String, val at: Long,
    val prayers: Int? = null, val wird: String? = null, val kids: String? = null, val city: String? = null, val status: String? = null,
    val khRound: Int = 0, val khMine: Set<Int> = emptySet(), val khDone: Set<Int> = emptySet(),
)

/**
 * Family linking without any server.
 *
 * - Pairing: one phone creates the family (random 256-bit key) and shows it as a QR code; others scan it to join.
 *   Only phones that scanned the QR hold the key.
 * - Sharing: each member sends a small "card" with only what they turned on (nothing but their name by default).
 *   Every card is encrypted with AES-256-GCM using the family key, so whatever carries it can't read it.
 * - Transport: automatically over the home Wi-Fi (Android network service discovery + a socket) while the Family
 *   screen is open, or by sending the encrypted card through any app (WhatsApp…) when away.
 * - Only the latest card per member is kept. Leaving the family deletes the key and every card.
 * The key is stored as "key_family", which the app's backup strips out.
 */
object Family {
    const val PREFIX = "SAFI-FAM1:"
    private const val QR_PREFIX = "SAFI-JOIN1:"
    private const val SERVICE = "_safifamily._tcp."

    private fun sp() = SafiApp.instance.getSharedPreferences("safi_family", Context.MODE_PRIVATE)
    val version = mutableIntStateOf(0)
    private fun bump() { version.intValue++ }

    val joined get() = sp().getString("key_family", "").orEmpty().isNotBlank()
    val familyId get() = sp().getString("fid", "").orEmpty()
    val myId: String get() = sp().getString("me", null) ?: java.util.UUID.randomUUID().toString().take(10).also { sp().edit { putString("me", it) } }
    var myName: String get() = sp().getString("name", "").orEmpty(); set(v) { sp().edit { putString("name", v) }; bump() }
    var myRole: String get() = sp().getString("role", "").orEmpty(); set(v) { sp().edit { putString("role", v) }; bump() }

    // what I share — all off by default
    var sharePrayers: Boolean get() = sp().getBoolean("s_pr", false); set(v) { sp().edit { putBoolean("s_pr", v) }; bump() }
    var shareWird: Boolean get() = sp().getBoolean("s_wird", false); set(v) { sp().edit { putBoolean("s_wird", v) }; bump() }
    var shareKids: Boolean get() = sp().getBoolean("s_kids", false); set(v) { sp().edit { putBoolean("s_kids", v) }; bump() }
    var shareCity: Boolean get() = sp().getBoolean("s_city", false); set(v) { sp().edit { putBoolean("s_city", v) }; bump() }
    var status: String get() = sp().getString("status", "").orEmpty(); set(v) { sp().edit { putString("status", v) }; bump() }

    private fun key(): ByteArray? = sp().getString("key_family", null)?.let { Base64.decode(it, Base64.NO_WRAP) }

    fun create(name: String, role: String) {
        val k = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val fid = ByteArray(6).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(java.util.Locale.US, it) }
        sp().edit { putString("key_family", Base64.encodeToString(k, Base64.NO_WRAP)); putString("fid", fid) }
        myName = name; myRole = role
    }

    fun isInvite(t: String) = t.trim().startsWith(QR_PREFIX)
    fun isCard(t: String) = t.trim().startsWith(PREFIX)

    /** Name, role and id only: enough for the other phone to add me right away. */
    private fun intro() = JSONObject().put("id", myId).put("name", myName).put("role", myRole).put("at", System.currentTimeMillis())
        .put("khr", khRound).put("khm", khMine.joinToString(",")).put("khd", khDone.joinToString(","))

    /** Text inside the invite QR code: the family key plus who invited, so the new member sees them at once. */
    fun inviteText(): String = QR_PREFIX + familyId + ":" + sp().getString("key_family", "") + ":" +
        Base64.encodeToString(intro().toString().toByteArray(Charsets.UTF_8), Base64.NO_WRAP or Base64.URL_SAFE)

    /** A small encrypted card for the "reply" QR the new member shows back to the inviter. */
    fun introCard(): String = PREFIX + familyId + ":" + encrypt(intro().toString())

    fun join(qr: String, name: String, role: String): Boolean {
        if (!isInvite(qr)) return false
        val parts = qr.trim().removePrefix(QR_PREFIX).split(":", limit = 3)
        if (parts.size < 2 || parts[1].isBlank()) return false
        val k = runCatching { Base64.decode(parts[1], Base64.NO_WRAP) }.getOrNull() ?: return false
        if (k.size != 32) return false
        sp().edit { putString("key_family", parts[1]); putString("fid", parts[0]) }
        myName = name; myRole = role
        // the inviter's introduction travels inside the QR
        parts.getOrNull(2)?.let { b ->
            runCatching { JSONObject(String(Base64.decode(b, Base64.NO_WRAP or Base64.URL_SAFE), Charsets.UTF_8)) }.getOrNull()?.let { store(it) }
        }
        return true
    }

    fun leave() {
        val me = myId
        sp().edit { clear(); putString("me", me) }
        bump()
    }

    // ------------------------------------------------------------------ cards
    private fun encrypt(plain: String): String {
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key(), "AES"), GCMParameterSpec(128, iv))
        val ct = c.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(iv + ct, Base64.NO_WRAP)
    }

    private fun decrypt(b64: String): String? = runCatching {
        val all = Base64.decode(b64, Base64.NO_WRAP)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key(), "AES"), GCMParameterSpec(128, all.copyOfRange(0, 12)))
        String(c.doFinal(all.copyOfRange(12, all.size)), Charsets.UTF_8)
    }.getOrNull()

    /** My card, with only what I chose to share. */
    suspend fun myCard(): String {
        val o = JSONObject().put("id", myId).put("name", myName).put("role", myRole).put("at", System.currentTimeMillis())
        if (sharePrayers) runCatching {
            val log = com.mohamed.safi.faith.PrayerLog.dao().dayNow(com.mohamed.safi.faith.PrayerLog.key(LocalDate.now(zone)))
            o.put("prayers", (0..4).count { i -> (log?.status(i) ?: 0).let { st -> st != 0 && st != com.mohamed.safi.faith.PrayerStatus.MISSED } })
        }
        if (shareWird) o.put("wird", (if (com.mohamed.safi.faith.Wird.doneToday) "✓ " else "") + "${com.mohamed.safi.faith.Wird.nextPage - 1}/${com.mohamed.safi.faith.Wird.TOTAL_PAGES}")
        if (shareKids) o.put("kids", com.mohamed.safi.kids.Kids.kids().joinToString("، ") { "${it.avatar} ${it.name} ⭐${com.mohamed.safi.kids.Kids.stars(it.id)}" })
        if (shareCity) o.put("city", com.mohamed.safi.faith.Prayer.city)
        if (status.isNotBlank()) o.put("status", status)
        o.put("khr", khRound).put("khm", khMine.joinToString(",")).put("khd", khDone.joinToString(","))
        // lessons, homework and study time travel with the card so parent and child stay in sync
        runCatching { com.mohamed.safi.study.Study.export() }.getOrNull()?.let { o.put("study", it) }
        return PREFIX + familyId + ":" + encrypt(o.toString())
    }

    /** Reads a card from another member. Returns their name, or null if it isn't for this family. */
    fun importCard(text: String): String? {
        val t = text.trim().substringAfter(PREFIX, "").takeIf { it.isNotBlank() } ?: return null
        val fid = t.substringBefore(":")
        if (fid != familyId) return null
        val json = decrypt(t.substringAfter(":")) ?: return null
        val o = runCatching { JSONObject(json) }.getOrNull() ?: return null
        return if (store(o)) o.optString("name") else null
    }

    /** Keeps the newest card per member; a newer family khatma round from anyone starts it here too. */
    private fun store(o: JSONObject): Boolean {
        val id = o.optString("id")
        if (id.isBlank() || id == myId) return false
        o.optJSONObject("study")?.let { runCatching { com.mohamed.safi.study.Study.merge(it) } }
        o.remove("study")
        val prev = sp().getLong("at_$id", 0)
        if (o.optLong("at") >= prev) sp().edit { putString("card_$id", o.toString()); putLong("at_$id", o.optLong("at")) }
        val r = o.optInt("khr", 0)
        if (r > khRound) sp().edit { putInt("kh_round", r); putStringSet("kh_mine", emptySet()); putStringSet("kh_done", emptySet()) }
        bump()
        return true
    }

    /** Whether this member was already known (to tell "added" from "updated"). */
    fun known(id: String) = sp().contains("card_$id")

    /** How I label a member on my phone (e.g. the one who joined as "ابن" is "ابني"); defaults to the role they chose. */
    fun label(id: String): String? = sp().getString("label_$id", null)
    fun setLabel(id: String, v: String) { sp().edit { putString("label_$id", v) }; bump() }

    // ------------------------------------------------------------------ family khatma: 30 juz split between members
    val khRound: Int get() = sp().getInt("kh_round", 0)
    val khMine: Set<Int> get() = sp().getStringSet("kh_mine", emptySet())!!.mapNotNull { it.toIntOrNull() }.toSet()
    val khDone: Set<Int> get() = sp().getStringSet("kh_done", emptySet())!!.mapNotNull { it.toIntOrNull() }.toSet()
    private fun putSet(k: String, v: Set<Int>) = sp().edit { putStringSet(k, v.map { it.toString() }.toSet()) }

    fun startKhatma() { sp().edit { putInt("kh_round", khRound + 1) }; putSet("kh_mine", emptySet()); putSet("kh_done", emptySet()); bump() }
    fun claim(j: Int) { putSet("kh_mine", khMine + j); bump() }
    fun unclaim(j: Int) { putSet("kh_mine", khMine - j); putSet("kh_done", khDone - j); bump() }
    fun setDone(j: Int, v: Boolean) { putSet("kh_done", if (v) khDone + j else khDone - j); bump() }

    /** For each juz: who took it (names) and whether it's read, merging my state with members of the same round. */
    data class Juz(val n: Int, val by: List<String>, val done: Boolean, val mine: Boolean)
    fun khatma(): List<Juz> {
        val others = members().filter { it.khRound == khRound }
        return (1..30).map { j ->
            val by = others.filter { j in it.khMine }.map { label(it.id) ?: it.name }
            Juz(j, by, j in khDone || others.any { j in it.khDone }, j in khMine)
        }
    }

    fun members(): List<MemberCard> = sp().all.keys.filter { it.startsWith("card_") }.mapNotNull { k ->
        val o = runCatching { JSONObject(sp().getString(k, "{}")!!) }.getOrNull() ?: return@mapNotNull null
        MemberCard(
            o.optString("id"), o.optString("name"), o.optString("role"), o.optLong("at"),
            if (o.has("prayers")) o.optInt("prayers") else null, o.optString("wird").ifBlank { null }, o.optString("kids").ifBlank { null },
            o.optString("city").ifBlank { null }, o.optString("status").ifBlank { null },
            o.optInt("khr", 0), ints(o.optString("khm")), ints(o.optString("khd")),
        )
    }.sortedByDescending { it.at }

    private fun ints(s: String) = s.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()

    fun removeMember(id: String) { sp().edit { remove("card_$id"); remove("at_$id"); remove("label_$id") }; bump() }

    // ------------------------------------------------------------------ home Wi-Fi sync
    private var scope: CoroutineScope? = null
    private var server: ServerSocket? = null
    private var nsd: NsdManager? = null
    private var reg: NsdManager.RegistrationListener? = null
    private var disc: NsdManager.DiscoveryListener? = null
    val lastSync = mutableIntStateOf(0)
    /** Name of a member who just appeared over Wi-Fi, for a "joined" message. */
    val joinedName = androidx.compose.runtime.mutableStateOf<String?>(null)

    /** Exchanges cards with family phones on the same Wi-Fi while running. */
    fun startLan(ctx: Context) {
        if (!joined || scope != null) return
        val sc = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope = sc
        val m = ctx.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return
        nsd = m
        val ss = runCatching { ServerSocket(0) }.getOrNull() ?: return
        server = ss
        sc.launch {
            while (!ss.isClosed) {
                val s = runCatching { ss.accept() }.getOrNull() ?: break
                launch { exchange(s) }
            }
        }
        val info = NsdServiceInfo().apply { serviceName = "safi-$familyId-$myId"; serviceType = SERVICE; port = ss.localPort }
        reg = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(i: NsdServiceInfo) {}
            override fun onRegistrationFailed(i: NsdServiceInfo, e: Int) {}
            override fun onServiceUnregistered(i: NsdServiceInfo) {}
            override fun onUnregistrationFailed(i: NsdServiceInfo, e: Int) {}
        }
        runCatching { m.registerService(info, NsdManager.PROTOCOL_DNS_SD, reg) }
        disc = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(t: String) {}
            override fun onDiscoveryStopped(t: String) {}
            override fun onStartDiscoveryFailed(t: String, e: Int) {}
            override fun onStopDiscoveryFailed(t: String, e: Int) {}
            override fun onServiceLost(i: NsdServiceInfo) {}
            override fun onServiceFound(i: NsdServiceInfo) {
                // only our family's phones, and not ourselves
                if (!i.serviceName.startsWith("safi-$familyId-") || i.serviceName.endsWith("-$myId")) return
                @Suppress("DEPRECATION")
                runCatching {
                    m.resolveService(i, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(si: NsdServiceInfo, e: Int) {}
                        override fun onServiceResolved(si: NsdServiceInfo) {
                            val host = si.host ?: return
                            sc.launch { runCatching { Socket().use { s -> s.connect(InetSocketAddress(host, si.port), 4000); exchange(s) } } }
                        }
                    })
                }
            }
        }
        runCatching { m.discoverServices(SERVICE, NsdManager.PROTOCOL_DNS_SD, disc) }
    }

    /** Both sides send their card and read the other's: one line each, encrypted. */
    private suspend fun exchange(s: Socket) {
        runCatching {
            s.soTimeout = 5000
            val out = s.getOutputStream().bufferedWriter()
            out.write(myCard()); out.newLine(); out.flush()
            val line = s.getInputStream().bufferedReader().readLine() ?: return
            val isNew = runCatching { line.substringAfter(PREFIX).substringAfter(":") }.getOrNull()?.let { decrypt(it) }
                ?.let { runCatching { JSONObject(it).optString("id") }.getOrNull() }?.let { !known(it) } ?: false
            importCard(line)?.let { n -> lastSync.intValue++; if (isNew) joinedName.value = n }
        }
        runCatching { s.close() }
    }

    fun stopLan() {
        runCatching { reg?.let { nsd?.unregisterService(it) } }
        runCatching { disc?.let { nsd?.stopServiceDiscovery(it) } }
        runCatching { server?.close() }
        scope?.cancel()
        scope = null; server = null; reg = null; disc = null
    }
}
