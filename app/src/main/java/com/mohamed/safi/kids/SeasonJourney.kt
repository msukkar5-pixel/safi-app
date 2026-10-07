package com.mohamed.safi.kids

import androidx.core.content.edit
import com.mohamed.safi.SafiApp
import com.mohamed.safi.data.zone
import java.time.YearMonth

/** A local, repeatable monthly journey. Completion is explicit; no passive tracking is used. */
object SeasonJourney {
    data class Mission(val id: String, val icon: String, val title: String, val detail: String, val stars: Int, val minAge: Int)
    data class Rank(val minStars: Int, val icon: String, val title: String)

    val missions = listOf(
        Mission("kindness", "🤝", "أثر طيب", "اعمل مساعدة أو كلمة طيبة حقيقية.", 3, 3),
        Mission("reader", "📚", "قارئ الشهر", "اقرأ قصة أو فصلًا واكتب أو احكِ فكرة واحدة.", 4, 6),
        Mission("curious", "🔎", "سؤال ذكي", "ابحث عن إجابة سؤال مفيد وشاركها مع البيت.", 4, 8),
        Mission("focus", "🎯", "جلسة تركيز", "25 دقيقة مذاكرة أو مشروع بلا مشتتات.", 4, 9),
        Mission("maker", "🛠️", "صانع أثر", "اصنع شيئًا بسيطًا أو أصلح أو صمّم فكرة.", 5, 10),
        Mission("digital", "📱", "مسؤول رقميًا", "طبّق قاعدة أمان أو احترام على الإنترنت.", 4, 10),
        Mission("team", "🏟️", "لاعب فريق", "شارك في هدف عائلي من غير مقارنة أو إحراج.", 4, 7),
        Mission("learning", "🧠", "معرفة جديدة", "اتعلم معلومة في علم أو تاريخ أو جغرافيا.", 4, 8),
    )
    val ranks = listOf(
        Rank(0, "🌱", "بداية الأثر"), Rank(12, "🍃", "مستكشف"), Rank(28, "🌟", "صاحب معرفة"),
        Rank(48, "🏅", "صانع أثر"), Rank(72, "👑", "سفير الرحمة"),
    )

    private fun sp() = SafiApp.instance.getSharedPreferences("safi_kids", android.content.Context.MODE_PRIVATE)
    private fun key(kid: String, month: YearMonth) = "season_${month}_$kid"
    fun month() = YearMonth.now(zone)
    fun done(kid: String, month: YearMonth = month()): Set<String> = sp().getStringSet(key(kid, month), emptySet()) ?: emptySet()
    fun points(kid: String, month: YearMonth = month()) = done(kid, month).sumOf { id -> missions.firstOrNull { it.id == id }?.stars ?: 0 }
    fun rank(kid: String, month: YearMonth = month()) = ranks.lastOrNull { points(kid, month) >= it.minStars } ?: ranks.first()
    fun available(age: Int) = missions.filter { age >= it.minAge }

    fun toggle(kid: String, mission: Mission, month: YearMonth = month()): Int {
        val old = done(kid, month)
        val add = mission.id !in old
        sp().edit { putStringSet(key(kid, month), if (add) old + mission.id else old - mission.id) }
        Kids.addStars(kid, if (add) mission.stars else -mission.stars)
        return if (add) mission.stars else -mission.stars
    }
}
