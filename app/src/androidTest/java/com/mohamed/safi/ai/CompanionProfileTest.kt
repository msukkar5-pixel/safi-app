package com.mohamed.safi.ai

import com.mohamed.safi.SafiApp
import com.mohamed.safi.faith.SituationSupport
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Runs only in the Android test target and restores the profile after each test. */
@RunWith(AndroidJUnit4::class)
class CompanionProfileTest {
    private lateinit var original: Map<String, *>

    @Before
    fun cleanBefore() {
        original = SafiApp.instance.getSharedPreferences("athar_companion_profile", 0).all
        CompanionProfile.clear()
    }

    @After
    fun cleanAfter() {
        val p = SafiApp.instance.getSharedPreferences("athar_companion_profile", 0)
        p.edit().clear().apply()
        val e = p.edit()
        original.forEach { (k, v) ->
            when (v) {
                is String -> e.putString(k, v)
                is Boolean -> e.putBoolean(k, v)
                is Int -> e.putInt(k, v)
                is Long -> e.putLong(k, v)
                is Float -> e.putFloat(k, v)
                is Set<*> -> e.putStringSet(k, v.filterIsInstance<String>().toSet())
            }
        }
        e.apply()
    }

    @Test
    fun explicitMemoriesRoundTripAndSelectiveDelete() {
        assertTrue(CompanionProfile.rememberMemory("tone", "أحب الرد المختصر") != null)
        assertTrue(CompanionProfile.rememberMemory("routine", "أبدأ يومي بعد صلاة الفجر") != null)
        assertTrue(CompanionProfile.rememberMemory("goal", "أوفر لشراء سيارة") != null)

        assertEquals(3, CompanionProfile.memories().size)
        assertTrue(CompanionProfile.snapshot().contains("أحب الرد المختصر"))
        assertTrue(CompanionProfile.snapshot().contains("أوفر لشراء سيارة"))
        assertTrue(CompanionProfile.prefersConciseAlerts())
        assertEquals(1, CompanionProfile.forgetMemory("routine"))
        assertEquals(2, CompanionProfile.memories().size)
        assertEquals(1, CompanionProfile.forgetMemory("tone"))
    }

    @Test
    fun panicSignalsAreSeparatedFromAnticipatoryAnxiety() = runBlocking {
        val panic = SituationSupport.forMessage("حاسس هموت ومش قادر أتنفس وقلبي سريع")
        assertEquals("الهلع والذعر", panic?.situation)

        val anticipatory = SituationSupport.forMessage("قلقان من نتيجة الامتحان ومش عارف أبطل تفكير")
        assertEquals("قلق التوقع وكثرة التفكير", anticipatory?.situation)
    }

    @Test
    fun alertPolicyHonorsQuietHoursVoiceAndCooldown() {
        val hour = java.time.LocalTime.now(com.mohamed.safi.data.zone).hour
        CompanionProfile.setAlertPolicy(supportOn = true, voiceOn = false, quietFrom = hour, quietUntil = (hour + 1) % 24, cooldownMinutes = 30)
        assertTrue(!CompanionProfile.alertPolicy().voiceOn)
        assertEquals(30, CompanionProfile.alertPolicy().cooldownMinutes)
        assertTrue(!CompanionProfile.autoSupportAllowed("voice"))

        CompanionProfile.setAlertPolicy(voiceOn = true, quietFrom = -1, quietUntil = -1)
        assertTrue(CompanionProfile.autoSupportAllowed("voice"))
        CompanionProfile.recordAutoSupport("voice")
        assertTrue(!CompanionProfile.autoSupportAllowed("voice"))
    }
}
