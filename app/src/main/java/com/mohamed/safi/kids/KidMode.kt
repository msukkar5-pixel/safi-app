package com.mohamed.safi.kids

import android.content.Context
import android.util.Base64
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.edit
import com.mohamed.safi.SafiApp
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Kid mode for a child's phone: the app shows only the sections the parent picked, on a simple kids' home screen.
 * Everything else (money, documents, places, diary, settings…) can't be opened. Leaving kid mode or changing the
 * sections needs the parent's PIN (stored only as a salted hash).
 * The parent can set it up on the child's phone, or on their own phone and pass it over as a QR / text code.
 */
object KidMode {
    const val PREFIX = "SAFI-KID1:"

    data class Section(val route: String, val title: String, val icon: String, val byDefault: Boolean)
    val sections = listOf(
        Section("kids", "مدينة الخير", "🌳", true), Section("study", "دروسي وواجباتي", "📝", true), Section("kidstv", "قنوات الأطفال", "📺", true), Section("quran", "القرآن الكريم", "📖", true),
        Section("quranaudio", "القرآن المسموع", "🎧", true), Section("azkar", "الأذكار", "🤲", true),
        Section("prayer", "مواعيد الصلاة", "🕌", true), Section("stories", "قصص الأنبياء والسيرة", "📚", true),
        Section("quiz", "المسابقة", "🏆", true), Section("asmahusna", "أسماء الله الحسنى", "✨", true),
        Section("sleep", "قبل النوم", "🌙", true), Section("ramadan", "رمضان", "🏮", true),
        Section("hisn", "حصن المسلم", "🛡️", false), Section("prayertracker", "صلواتي", "✅", false),
        Section("wird", "الورد اليومي", "📗", false), Section("radio", "إذاعات القرآن", "📻", false),
        Section("tv", "قنوات القرآن", "📺", false), Section("islamiccalendar", "التقويم الهجري", "📅", false),
        Section("audiobooks", "الكتب المسموعة", "🎙️", false), Section("library", "المكتبة", "🏛️", false),
        Section("family", "العيلة", "👨‍👩‍👧", false), Section("assistant", "المساعد الذكي", "🤖", false),
    )
    val defaults get() = sections.filter { it.byDefault }.map { it.route }.toSet()

    private fun sp() = SafiApp.instance.getSharedPreferences("safi_kidmode", Context.MODE_PRIVATE)
    val version = mutableIntStateOf(0)
    /** A setup code that arrived by share, waiting for the setup screen to confirm it. */
    val pendingCode = mutableStateOf<String?>(null)

    val on: Boolean get() = sp().getBoolean("on", false)
    val name: String get() = sp().getString("name", "").orEmpty()
    val allowed: Set<String> get() = sp().getStringSet("allowed", defaults) ?: defaults

    private fun hash(salt: String, pin: String) =
        MessageDigest.getInstance("SHA-256").digest((salt + pin).toByteArray()).joinToString("") { "%02x".format(java.util.Locale.US, it) }
    private fun newSalt() = ByteArray(8).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(java.util.Locale.US, it) }

    fun checkPin(pin: String) = hash(sp().getString("salt", "").orEmpty(), pin) == sp().getString("pin", "")

    private fun apply(name: String, allowed: Set<String>, salt: String, pinHash: String, studentId: String? = null) {
        sp().edit { putBoolean("on", true); putString("name", name); putStringSet("allowed", allowed); putString("salt", salt); putString("pin", pinHash) }
        // this phone belongs to one student: the same id the parent's phone uses, so lessons and homework match up
        val st = com.mohamed.safi.study.Study
        val sid = studentId ?: st.students().firstOrNull { it.name == name }?.id ?: st.newId()
        if (st.student(sid) == null) st.addStudent(name.ifBlank { "بطل" }, sid)
        st.me = sid
        // the child also gets a profile in the kids' city
        if ("kids" in allowed && Kids.kids().isEmpty()) Kids.saveKid(Kid(java.util.UUID.randomUUID().toString().take(8), name.ifBlank { "بطل" }, "🧒", 7))
        com.mohamed.safi.SafiApp.prefs.onboarded = true
        version.intValue++
    }

    fun enable(name: String, allowed: Set<String>, pin: String) { val s = newSalt(); apply(name.trim(), allowed, s, hash(s, pin)) }

    fun setAllowed(v: Set<String>) { sp().edit { putStringSet("allowed", v) }; version.intValue++ }

    fun disable(pin: String): Boolean {
        if (!checkPin(pin)) return false
        sp().edit { putBoolean("on", false) }
        version.intValue++
        return true
    }

    /** Can this nav route be opened in kid mode? Sub-routes follow their section. */
    fun allows(route: String?): Boolean {
        if (!on || route == null) return true
        val base = route.substringBefore("/")
        if (base == "kidhome" || base == "kidsetup") return true
        if (timeUp) return keepQuran && base in alwaysOpen && base in allowed
        val a = allowed
        return when (base) {
            "tafsir" -> "quran" in a
            "book" -> a.any { it in setOf("library", "stories") }
            "bidaya", "history" -> "stories" in a || "library" in a
            "umrah", "hajj", "tool", "manasik" -> false
            else -> base in a
        }
    }

    // ------------------------------------------------------------------ setup code (QR or text), made on the parent's phone
    /** [studentId] links the child's lessons on both phones; [familyInvite] joins the child's phone to the family. */
    fun setupCode(name: String, allowed: Set<String>, pin: String, studentId: String? = null, familyInvite: String? = null, role: String = "ابن"): String {
        val s = newSalt()
        val o = JSONObject().put("n", name.trim()).put("a", JSONArray(allowed.toList())).put("s", s).put("h", hash(s, pin)).put("r", role)
        studentId?.let { o.put("st", it) }
        familyInvite?.let { o.put("f", it) }
        return PREFIX + Base64.encodeToString(o.toString().toByteArray(Charsets.UTF_8), Base64.NO_WRAP or Base64.URL_SAFE)
    }

    data class Setup(val name: String, val allowed: Set<String>, val salt: String, val hash: String, val student: String?, val invite: String?, val role: String)
    fun readCode(text: String): Setup? = runCatching {
        val b = Regex("SAFI-KID1:[A-Za-z0-9_=-]+").find(text)!!.value.removePrefix(PREFIX)
        val o = JSONObject(String(Base64.decode(b, Base64.NO_WRAP or Base64.URL_SAFE), Charsets.UTF_8))
        val a = o.getJSONArray("a")
        val known = sections.map { it.route }.toSet()
        Setup(o.optString("n").take(20), (0 until a.length()).map { a.getString(it) }.filter { it in known }.toSet(), o.getString("s"), o.getString("h"),
            o.optString("st").ifBlank { null }, o.optString("f").ifBlank { null }, o.optString("r").ifBlank { "ابن" })
    }.getOrNull()

    fun applyCode(s: Setup) {
        // join the parent's family first, so the first update can already carry the lessons
        s.invite?.let { inv -> if (!com.mohamed.safi.family.Family.joined) com.mohamed.safi.family.Family.join(inv, s.name.ifBlank { "بطل" }, s.role) }
        apply(s.name, s.allowed, s.salt, s.hash, s.student)
    }

    // ------------------------------------------------------------------ screen time (inside Safi only)
    /** Minutes a day the child can use the app (0 = no limit). */
    var dailyLimit: Int get() = sp().getInt("limit", 0); set(v) { sp().edit { putInt("limit", v) }; version.intValue++ }
    var bedtimeOn: Boolean get() = sp().getBoolean("bed_on", false); set(v) { sp().edit { putBoolean("bed_on", v) }; version.intValue++ }
    var bedFrom: Int get() = sp().getInt("bed_from", 21); set(v) { sp().edit { putInt("bed_from", v) }; version.intValue++ }
    var bedTo: Int get() = sp().getInt("bed_to", 6); set(v) { sp().edit { putInt("bed_to", v) }; version.intValue++ }
    /** Quran, adhkar and prayer times stay open when the time is up. */
    var keepQuran: Boolean get() = sp().getBoolean("keep_quran", true); set(v) { sp().edit { putBoolean("keep_quran", v) }; version.intValue++ }
    val alwaysOpen = setOf("quran", "quranaudio", "azkar", "prayer", "hisn")

    private fun today() = java.time.LocalDate.now(com.mohamed.safi.data.zone).toString()
    val usedToday: Int get() = if (sp().getString("used_day", "") == today()) sp().getInt("used", 0) else 0
    private val bonusToday: Int get() = if (sp().getString("bonus_day", "") == today()) sp().getInt("bonus", 0) else 0
    fun tick(minutes: Int = 1) { sp().edit { putString("used_day", today()); putInt("used", usedToday + minutes) }; version.intValue++ }
    /** The parent gives extra minutes for today. */
    fun addBonus(min: Int) { sp().edit { putString("bonus_day", today()); putInt("bonus", bonusToday + min) }; version.intValue++ }
    val minutesLeft: Int? get() = if (dailyLimit <= 0) null else (dailyLimit + bonusToday - usedToday).coerceAtLeast(0)
    val isBedtime: Boolean get() {
        if (!bedtimeOn || sp().getBoolean("bed_skip_" + today(), false)) return false
        val h = java.time.LocalTime.now(com.mohamed.safi.data.zone).hour
        return if (bedFrom > bedTo) h >= bedFrom || h < bedTo else h in bedFrom until bedTo
    }
    fun skipBedtimeTonight() { sp().edit { putBoolean("bed_skip_" + today(), true) }; addBonus(30) }
    /** Time is up (daily limit or bedtime): only kid home and the always-open sections. */
    val timeUp: Boolean get() = on && (minutesLeft == 0 || isBedtime)
}
