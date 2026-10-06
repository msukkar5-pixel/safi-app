package com.mohamed.safi.quiz

import android.content.Context
import android.util.Base64
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.edit
import com.mohamed.safi.SafiApp
import com.mohamed.safi.data.zone
import com.mohamed.safi.family.Family
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.time.LocalDate
import kotlin.random.Random

/**
 * Friends challenge that works from anywhere, with no server: the challenger plays 10 questions, then sends a
 * short code by WhatsApp (or any app). The friend opens it in Safi, gets the very same questions, and sends a reply
 * code back. Scores are kept on each phone. One code can go to a whole group; every reply counts on its own.
 */
object Challenge {
    const val PREFIX = "SAFI-CH1:"
    const val CAPSULE_PREFIX = "SAFI-CH2:"
    const val FAMILY_CAPSULE_PREFIX = "SAFI-CH3:"
    private val re = Regex("SAFI-CH1:[A-Za-z0-9_=-]+")
    private val capsuleRe = Regex("SAFI-CH2:[A-Za-z0-9_=-]+")
    private val familyCapsuleRe = Regex("SAFI-CH3:[A-Za-z0-9_-]+:[A-Za-z0-9+/=]+")

    /** [reply] false = a challenge to play; true = a friend's result for a challenge I sent. */
    data class Code(
        val id: String, val fromId: String, val from: String, val seed: Long, val qs: List<Int>, val cats: String,
        val correct: Int, val score: Int, val reply: Boolean = false,
    )

    private fun sp() = SafiApp.instance.getSharedPreferences("safi_challenge", Context.MODE_PRIVATE)
    private const val EVENTS = "events_v1"
    val version = mutableIntStateOf(0)
    /** A challenge that arrived (shared into the app), waiting for the quiz screen to show it. */
    val incoming = mutableStateOf<Code?>(null)

    val myId: String get() = sp().getString("me", null) ?: java.util.UUID.randomUUID().toString().take(8).also { sp().edit { putString("me", it) } }
    var myName: String get() = sp().getString("name", "").orEmpty(); set(v) = sp().edit { putString("name", v.take(20)) }

    // ------------------------------------------------------------------ codes
    fun encode(c: Code): String {
        val o = JSONObject().put("i", c.id).put("f", c.fromId).put("n", c.from).put("s", c.seed).put("q", JSONArray(c.qs))
            .put("k", c.cats).put("c", c.correct).put("p", c.score).put("r", if (c.reply) 1 else 0)
        return PREFIX + Base64.encodeToString(o.toString().toByteArray(Charsets.UTF_8), Base64.NO_WRAP or Base64.URL_SAFE)
    }

    fun decode(text: String): Code? {
        val m = re.find(text) ?: return null
        return runCatching {
            val o = JSONObject(String(Base64.decode(m.value.removePrefix(PREFIX), Base64.NO_WRAP or Base64.URL_SAFE), Charsets.UTF_8))
            val q = o.optJSONArray("q") ?: JSONArray()
            Code(
                o.getString("i"), o.getString("f"), o.optString("n").take(20), o.optLong("s"), (0 until q.length()).map { q.getInt(it) }.take(15),
                o.optString("k"), o.optInt("c").coerceIn(0, 15), o.optInt("p").coerceIn(0, 100_000), o.optInt("r") == 1,
            )
        }.getOrNull()
    }

    fun contains(text: String) = re.containsMatchIn(text)
    fun containsCapsule(text: String) = capsuleRe.containsMatchIn(text)
    fun containsFamilyCapsule(text: String) = familyCapsuleRe.containsMatchIn(text)

    /** Append-only local journal; later capsules can merge events instead of replacing history. */
    data class Update(val eventId: String, val challengeId: String, val participantId: String, val participant: String,
                      val kind: String, val correct: Int, val score: Int, val at: Long, val previousHash: String, val hash: String)

    private fun digest(text: String): String = MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    private fun eventValid(o: JSONObject): Boolean {
        val eventId = o.optString("e")
        val challengeId = o.optString("c")
        val participantId = o.optString("p")
        val kind = o.optString("k")
        val correct = o.optInt("x")
        val score = o.optInt("s")
        val at = o.optLong("t")
        val previous = o.optString("prev")
        val hash = o.optString("h")
        if (eventId.isBlank() || challengeId.isBlank() || participantId.isBlank() || kind.isBlank() || previous.isBlank() || hash.isBlank()) return false
        if (correct !in 0..15 || score !in 0..100_000 || at <= 0) return false
        return digest("$eventId|$challengeId|$participantId|$kind|$correct|$score|$at|$previous") == hash
    }

    @Synchronized
    private fun appendUpdate(c: Code, correct: Int, score: Int, kind: String) {
        val existing = runCatching { JSONArray(sp().getString(EVENTS, "[]")) }.getOrElse { JSONArray() }
        val previous = if (existing.length() == 0) "GENESIS" else existing.optJSONObject(existing.length() - 1)?.optString("h", "GENESIS") ?: "GENESIS"
        val at = System.currentTimeMillis()
        val eventId = java.util.UUID.randomUUID().toString()
        val body = "$eventId|${c.id}|$myId|$kind|$correct|$score|$at|$previous"
        existing.put(JSONObject().put("e", eventId).put("c", c.id).put("p", myId).put("n", myName)
            .put("k", kind).put("x", correct).put("s", score).put("t", at).put("prev", previous).put("h", digest(body)))
        sp().edit { putString(EVENTS, existing.toString()) }
        version.intValue++
    }

    /** One local completion per kind/day, shared only when the user enabled family activity sharing. */
    fun recordFamilyActivity(kind: String, points: Int) {
        if (!Family.joined || !Family.shareActivities) return
        val day = LocalDate.now(zone).toString()
        val marker = "family_activity_${kind}_$day"
        if (sp().getBoolean(marker, false)) return
        val code = Code("family_${kind}_$day", myId, myName, 0L, emptyList(), "family", 0, 0)
        appendUpdate(code, correct = 1, score = points.coerceIn(1, 100), kind = "activity_$kind")
        sp().edit { putBoolean(marker, true) }
    }

    /** Returns only local challenge events; no audio, chat, or family data is included. */
    fun localUpdates(): List<Update> {
        val a = runCatching { JSONArray(sp().getString(EVENTS, "[]")) }.getOrElse { JSONArray() }
        val imported = runCatching { JSONArray(sp().getString("imported_$EVENTS", "[]")) }.getOrElse { JSONArray() }
        for (i in 0 until imported.length()) imported.optJSONObject(i)?.let { a.put(it) }
        return (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            Update(o.optString("e"), o.optString("c"), o.optString("p"), o.optString("n"), o.optString("k"),
                o.optInt("x"), o.optInt("s"), o.optLong("t"), o.optString("prev"), o.optString("h"))
        }
    }

    /** Validates the local hash chain before a future capsule export or merge. */
    fun journalIsValid(): Boolean {
        val raw = runCatching { JSONArray(sp().getString(EVENTS, "[]")) }.getOrElse { JSONArray() }
        val updates = (0 until raw.length()).mapNotNull { i ->
            val o = raw.optJSONObject(i) ?: return@mapNotNull null
            Update(o.optString("e"), o.optString("c"), o.optString("p"), o.optString("n"), o.optString("k"),
                o.optInt("x"), o.optInt("s"), o.optLong("t"), o.optString("prev"), o.optString("h"))
        }
        var previous = "GENESIS"
        return updates.all { u ->
            val body = "${u.eventId}|${u.challengeId}|${u.participantId}|${u.kind}|${u.correct}|${u.score}|${u.at}|$previous"
            val valid = u.previousHash == previous && u.hash == digest(body)
            previous = u.hash
            valid
        }
    }

    /** Export only challenge events, not chat, family, money, or personal memory. */
    fun exportCapsule(challengeId: String? = null, familyOnly: Boolean = false, limit: Int = Int.MAX_VALUE): String {
        val a = JSONArray()
        val familyIds = (Family.members().map { it.id } + Family.myId).toSet()
        localUpdates().filter { challengeId.isNullOrBlank() || it.challengeId == challengeId }
            .filter { !Family.joined || if (familyOnly) it.participantId in familyIds else it.participantId !in familyIds }
            .sortedByDescending { it.at }.take(limit.coerceAtLeast(0)).forEach { u ->
            a.put(JSONObject().put("e", u.eventId).put("c", u.challengeId).put("p", u.participantId).put("n", u.participant)
                .put("k", u.kind).put("x", u.correct).put("s", u.score).put("t", u.at).put("prev", u.previousHash).put("h", u.hash))
        }
        val body = JSONObject().put("v", 2).put("events", a)
        if (familyOnly) Family.encryptFamilyPayload(body.toString())?.let { encrypted ->
            return FAMILY_CAPSULE_PREFIX + Family.familyId + ":" + encrypted
        }
        return CAPSULE_PREFIX + Base64.encodeToString(body.toString().toByteArray(Charsets.UTF_8), Base64.NO_WRAP or Base64.URL_SAFE)
    }

    /** Merge a received capsule idempotently; imported events never replace the local journal. */
    fun importCapsule(text: String): Int {
        val familyMatch = familyCapsuleRe.find(text)
        val match = capsuleRe.find(text)
        val body = when {
            familyMatch != null -> {
                val parts = familyMatch.value.removePrefix(FAMILY_CAPSULE_PREFIX).split(":", limit = 2)
                if (parts.size != 2 || parts[0] != Family.familyId) return 0
                Family.decryptFamilyPayload(parts[1])?.let { runCatching { JSONObject(it) }.getOrNull() }
            }
            match != null -> runCatching {
                JSONObject(String(Base64.decode(match.value.removePrefix(CAPSULE_PREFIX), Base64.NO_WRAP or Base64.URL_SAFE), Charsets.UTF_8))
            }.getOrNull()
            else -> null
        } ?: return 0
        if (body.optInt("v", 0) != 2) return 0
        val incoming = body.optJSONArray("events") ?: return 0
        val imported = runCatching { JSONArray(sp().getString("imported_$EVENTS", "[]")) }.getOrElse { JSONArray() }
        val known = (0 until imported.length()).mapNotNull { imported.optJSONObject(it)?.optString("e") }.toMutableSet()
        known += localUpdates().map { it.eventId }
        var added = 0
        for (i in 0 until incoming.length()) {
            val event = incoming.optJSONObject(i) ?: continue
            if (!eventValid(event)) continue
            val id = event.optString("e")
            if (id.isBlank() || !known.add(id)) continue
            imported.put(event)
            added++
        }
        if (added > 0) {
            sp().edit { putString("imported_$EVENTS", imported.toString()) }
            version.intValue++
        }
        return added
    }

    // ------------------------------------------------------------------ playing
    fun catsFor(key: String): Set<String>? = when (key) { "religion" -> Quiz.religionCats.keys; "general" -> Quiz.generalCats.keys; else -> null }

    /** A new challenge: fixed questions picked from a random seed. */
    fun create(cats: String): Code {
        val seed = Random.nextLong(1, Long.MAX_VALUE)
        val qs = Quiz.pick(10, catsFor(cats), null, seed, listOf(1, 1, 1, 2, 2, 2, 2, 3, 3, 3))
        return Code(java.util.UUID.randomUUID().toString().take(8), myId, myName, seed, qs.map { it.id }, cats, 0, 0)
    }

    /** The same questions with the same answer order on both phones. */
    fun questions(c: Code): List<Question> {
        val byId = Quiz.load().associateBy { it.id }
        val found = c.qs.mapNotNull { byId[it] }
        // a friend on another app version may miss a question or two; if many are missing, rebuild from the same seed
        val list = if (found.size >= minOf(8, c.qs.size)) found else Quiz.pick(10, catsFor(c.cats), null, c.seed)
        return list.mapIndexed { i, q -> q.shuffled(Random(c.seed + i)) }
    }

    // ------------------------------------------------------------------ results
    /** After I play: my own challenge (to send) or a reply to a friend's. Returns the code to send. */
    fun afterPlay(c: Code, correct: Int, score: Int): String {
        val mine = c.fromId == myId
        return if (mine) {
            sp().edit { putString("sent_${c.id}", JSONObject().put("c", correct).put("p", score).toString()) }
            appendUpdate(c, correct, score, "challenge_result")
            encode(c.copy(from = myName, correct = correct, score = score))
        } else {
            sp().edit { putBoolean("played_${c.id}", true) }
            appendUpdate(c, correct, score, "friend_result")
            record(c.fromId, c.from, correct, score, c.correct, c.score)
            encode(c.copy(fromId = myId, from = myName, correct = correct, score = score, reply = true))
        }
    }

    fun played(c: Code) = sp().getBoolean("played_${c.id}", false)

    /** A reply to a challenge I sent. Returns a result line, or null if it isn't mine. */
    fun importReply(c: Code): Int? {
        if (!c.reply || c.fromId == myId) return null
        val mine = sp().getString("sent_${c.id}", null)?.let { JSONObject(it) } ?: return null
        val key = "seen_${c.id}_${c.fromId}"
        if (sp().getBoolean(key, false)) return null
        sp().edit { putBoolean(key, true) }
        return record(c.fromId, c.from, mine.optInt("c"), mine.optInt("p"), c.correct, c.score)
    }

    /** Win = more correct answers; points break a tie. Returns 1 win, 0 draw, -1 loss (for me). */
    private fun record(friendId: String, name: String, myC: Int, myP: Int, theirC: Int, theirP: Int): Int {
        val r = compareValuesBy(myC to myP, theirC to theirP, { it.first }, { it.second }).coerceIn(-1, 1)
        val o = sp().getString("f_$friendId", null)?.let { runCatching { JSONObject(it) }.getOrNull() } ?: JSONObject()
        o.put("n", name.ifBlank { o.optString("n") })
        o.put(if (r > 0) "w" else if (r < 0) "l" else "d", o.optInt(if (r > 0) "w" else if (r < 0) "l" else "d") + 1)
        o.put("last", "$myC-$theirC").put("at", System.currentTimeMillis())
        sp().edit { putString("f_$friendId", o.toString()) }
        version.intValue++
        return r
    }

    data class Friend(val id: String, val name: String, val wins: Int, val losses: Int, val draws: Int, val last: String, val at: Long)
    fun friends(): List<Friend> = sp().all.keys.filter { it.startsWith("f_") }.mapNotNull { k ->
        val o = runCatching { JSONObject(sp().getString(k, "{}")!!) }.getOrNull() ?: return@mapNotNull null
        Friend(k.removePrefix("f_"), o.optString("n"), o.optInt("w"), o.optInt("l"), o.optInt("d"), o.optString("last"), o.optLong("at"))
    }.sortedWith(compareByDescending<Friend> { it.wins - it.losses }.thenByDescending { it.at })
}
