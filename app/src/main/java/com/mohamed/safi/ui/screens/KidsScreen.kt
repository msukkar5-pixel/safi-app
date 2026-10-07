package com.mohamed.safi.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import com.mohamed.safi.ui.Text
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.drawText
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mohamed.safi.data.zone
import com.mohamed.safi.kids.*
import com.mohamed.safi.quiz.Quiz
import com.mohamed.safi.ui.*
import kotlinx.coroutines.delay
import java.time.LocalDate

private val KidBg = Color(0xFFFFF7E8)
private val KidSky = Color(0xFF4FA3D9)
private val KidGreen = Color(0xFF2E9D5B)

/** True during Ramadan (Umm al-Qura calendar on the phone). */
fun isRamadan(d: LocalDate = LocalDate.now(zone)): Boolean = runCatching {
    java.time.chrono.HijrahDate.from(d).get(java.time.temporal.ChronoField.MONTH_OF_YEAR) == 9
}.getOrDefault(false)

@Composable
fun KidsScreen(onBack: () -> Unit, open: (String) -> Unit) {
    @Suppress("UNUSED_VARIABLE") val v = Kids.version.intValue
    val kids = Kids.kids()
    var current by rememberSaveable { mutableStateOf(kids.firstOrNull()?.id) }
    var parent by remember { mutableStateOf(false) }
    var askPin by remember { mutableStateOf(false) }
    var game by remember { mutableStateOf<String?>(null) }
    val kid = kids.firstOrNull { it.id == current } ?: kids.firstOrNull()

    if (parent) { ParentArea(onClose = { parent = false }); return }
    val g = game
    if (g != null && kid != null) {
        androidx.activity.compose.BackHandler { game = null }
        when (g) {
            "memory" -> MemoryGame(kid) { game = null }
            "wudu" -> WuduGame(kid) { game = null }
            "quiz" -> KidsQuiz(kid) { game = null }
            "stories" -> KidsStoriesScreen(kid) { game = null }
            "youth" -> YouthClubScreen(kid, onDone = { game = null }, onRoute = open, onStories = { game = "stories" })
            "kidbooks" -> ScreenScaffold("مكتبة الأطفال", onBack = { game = null }) { pad ->
                androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item { Text("كتب دينية أصيلة مناسبة للأطفال: الأربعون النووية، التجويد، أصول الدين، الصلاة، والشمائل والسيرة.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline) }
                    item { BookList(listOf("kids")) { open("book/$it") } }
                }
            }
            else -> if (g.startsWith("story:")) KidsStoriesScreen(kid, g.removePrefix("story:")) { game = null } else KidsGame(g, kid) { game = null }
        }
        return
    }

    ScreenScaffold("مدينة الخير", onBack = onBack, actions = {
        IconButton(onClick = { if (Kids.hasPin) askPin = true else parent = true }) { Icon(Icons.Default.Lock, "ركن الوالدين") }
    }) { pad ->
        LazyColumn(Modifier.fillMaxSize().background(KidBg).padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (kids.isEmpty()) {
                item {
                    GoldCard {
                        Text("أهلاً بيك في مدينة الخير 🌳", fontFamily = Amiri, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                        Text("مكان للأطفال طول السنة: مهام يومية بسيطة، نجوم، شجرة خير بتكبر، جواز سفر بالأختام، ألعاب وقصص. كله على الموبايل ده بس ومن غير إعلانات.", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { parent = true }) { Icon(Icons.Default.PersonAdd, null); Spacer(Modifier.width(6.dp)); Text("ضيف طفل (للوالدين)") }
                    }
                }
                return@LazyColumn
            }
            if (kids.size > 1) item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(kids, key = { it.id }) { k ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable { current = k.id }.padding(6.dp)) {
                            Box(Modifier.size(56.dp).clip(CircleShape).background(if (k.id == kid?.id) KidSky else Color.White).border(2.dp, KidSky, CircleShape), contentAlignment = Alignment.Center) {
                                Text(k.avatar, fontSize = 28.sp)
                            }
                            Text(k.name, fontWeight = if (k.id == kid?.id) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
            }
            if (kid != null) {
                item { KidHeader(kid) }
                item { SeasonJourneyCard(kid) }
                if (isRamadan() && kid.fasting) item { FastCard(kid) }
                item { Text("مهام النهارده", fontFamily = Amiri, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = KidGreen) }
                item { TaskGrid(kid) }
                item { TreeCard(kid) }
                item { PassportCard(kid) }
                item { RewardsCard(kid) }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        KidTile("📝", "دروسي وواجباتي", Modifier.weight(1f)) { open("study") }
                        KidTile("📺", "قنوات الأطفال", Modifier.weight(1f)) { open("kidstv") }
                        KidTile("📚", "مكتبة الأطفال", Modifier.weight(1f)) { game = "kidbooks" }
                    }
                }
                if (kid.age >= 10) item { YouthClubCard(kid) { game = "youth" } }
                item { DailyKidsCard(kid) { game = it } }
                item { Text("ألعاب وقصص", fontFamily = Amiri, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = KidGreen) }
                item {
                    Surface(onClick = { game = "stories" }, shape = RoundedCornerShape(22.dp), color = Color(0xFFFFE9B8), modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("📚", fontSize = 40.sp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("قصص مدينة الخير", fontFamily = Amiri, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color(0xFF3B3125))
                                Text("قصص بعِبرة، وقصص بتختار فيها النهاية", style = MaterialTheme.typography.bodySmall, color = Color(0xFF3B3125))
                            }
                        }
                    }
                }
                item { Text("كل الألعاب", fontWeight = FontWeight.Bold, color = Color(0xFF3B3125)) }
                KidData.allGames(com.mohamed.safi.SafiApp.instance, kid.age).chunked(3).forEach { row ->
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { e -> KidTile(e.icon, e.title, Modifier.weight(1f)) { game = e.id } }
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
                item { KidTile("📖", "قصص الأنبياء", Modifier.fillMaxWidth()) { open("stories") } }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
    if (askPin) PinDialog(onDismiss = { askPin = false }) { ok -> askPin = false; if (ok) parent = true }
}

@Composable
private fun SeasonJourneyCard(kid: Kid) {
    @Suppress("UNUSED_VARIABLE") val live = Kids.version.intValue
    val available = SeasonJourney.available(kid.age)
    val done = SeasonJourney.done(kid.id)
    val points = SeasonJourney.points(kid.id)
    val rank = SeasonJourney.rank(kid.id)
    val next = SeasonJourney.ranks.firstOrNull { it.minStars > points }
    Surface(shape = RoundedCornerShape(24.dp), color = Color(0xFF263D6B), contentColor = Color.White, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(rank.icon, fontSize = 34.sp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("رحلة أثر الشهرية", fontFamily = Amiri, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                    Text("${rank.title} • $points نجمة موسمية", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = .82f))
                }
                Text("${done.size}/${available.size}", fontWeight = FontWeight.Bold, color = Gold)
            }
            if (next != null) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(progress = { (points.toFloat() / next.minStars).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(7.dp).clip(CircleShape), color = Gold, trackColor = Color.White.copy(alpha = .25f), drawStopIndicator = {})
                Text("فاضل ${(next.minStars - points).coerceAtLeast(0)} نجمة على ${next.icon} ${next.title}", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = .82f), modifier = Modifier.padding(top = 4.dp))
            }
            Spacer(Modifier.height(10.dp))
            available.take(4).forEach { mission ->
                val complete = mission.id in done
                Surface(onClick = { SeasonJourney.toggle(kid.id, mission) }, shape = RoundedCornerShape(14.dp), color = if (complete) Positive.copy(alpha = .78f) else Color.White.copy(alpha = .12f), modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (complete) "✅" else mission.icon)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(mission.title, fontWeight = FontWeight.SemiBold)
                            Text(mission.detail, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = .82f), maxLines = 1)
                        }
                        Text("+${mission.stars}⭐", style = MaterialTheme.typography.labelSmall, color = Gold)
                    }
                }
            }
            Text("مهمات الموسم اختيارية وتتجدد كل شهر. المشاركة في تحدي العيلة تظل بإذن ولي الأمر فقط.", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = .74f), modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
private fun KidHeader(kid: Kid) {
    val stars = Kids.stars(kid.id)
    val title = Kids.rewards.lastOrNull { stars >= it.stars }
    val next = Kids.rewards.firstOrNull { stars < it.stars }
    Surface(shape = RoundedCornerShape(24.dp), color = KidSky, contentColor = Color.White, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(64.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) { Text(kid.avatar, fontSize = 36.sp) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("أهلاً يا ${kid.name}!", fontFamily = Amiri, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                if (title != null) Text("${title.icon} ${title.title}", fontWeight = FontWeight.SemiBold)
                if (next != null) {
                    LinearProgressIndicator(progress = { stars / next.stars.toFloat() }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(8.dp).clip(CircleShape), color = Gold, trackColor = Color.White.copy(alpha = 0.3f), drawStopIndicator = {})
                    Text("فاضل ${next.stars - stars} نجمة على ${next.icon}", fontSize = 12.sp)
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("⭐", fontSize = 26.sp)
                Text("$stars", fontWeight = FontWeight.Bold, fontSize = 22.sp)
            }
        }
    }
}

@Composable
private fun YouthClubCard(kid: Kid, onClick: () -> Unit) {
    val done = Kids.youthDoneOn(kid.id).size
    Surface(onClick = onClick, shape = RoundedCornerShape(24.dp), color = Color(0xFF182B50), contentColor = Color.White, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("🚀", fontSize = 38.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("نادي التحدي", fontFamily = Amiri, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                Text("مهمات ومشاريع وقصص قرارات ومسارات أذكى لسن ${kid.age}", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = .82f))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$done/${Kids.youthMissions.size}", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Gold)
                Text("اليوم", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = .72f))
            }
        }
    }
}

@Composable
private fun YouthClubScreen(kid: Kid, onDone: () -> Unit, onRoute: (String) -> Unit, onStories: () -> Unit) {
    @Suppress("UNUSED_VARIABLE") val live = Kids.version.intValue
    val done = Kids.youthDoneOn(kid.id)
    ScreenScaffold("نادي التحدي", onBack = onDone) { pad ->
        LazyColumn(Modifier.fillMaxSize().background(Color(0xFFF6F8FC)).padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Surface(shape = RoundedCornerShape(24.dp), color = Color(0xFF182B50), contentColor = Color.White) {
                    Column(Modifier.padding(18.dp)) {
                        Text("مساحتك الكبيرة يا ${kid.name}", fontFamily = Amiri, fontWeight = FontWeight.Bold, fontSize = 24.sp)
                        Text("مش سباق درجات ولا واجبات زيادة: اختار من مهماتك اليومية، ابنِ عادة، وجرب تحديات فيها تفكير وإبداع.", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = .86f))
                    }
                }
            }
            item { SectionTitle("مهمات اليوم") }
            items(Kids.youthMissions, key = { it.id }) { mission ->
                val isDone = mission.id in done
                AppCard(onClick = { Kids.toggleYouthMission(kid.id, mission) }, color = if (isDone) Positive.copy(alpha = .12f) else MaterialTheme.colorScheme.surfaceContainerLow) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (isDone) "✅" else mission.icon, fontSize = 28.sp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(mission.title, fontWeight = FontWeight.Bold)
                            Text(mission.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }
                        Text("+${mission.stars} ⭐", color = Gold, fontWeight = FontWeight.Bold)
                    }
                }
            }
            item { SectionTitle("اختار مغامرتك") }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    ModeTile("🏆", "مسابقة أثر", "أسئلة صعبة وأوضاع تنافس", Modifier.weight(1f)) { onRoute("quiz") }
                    ModeTile("📖", "قصص قرارات", "مواقف أكبر سنًا", Modifier.weight(1f)) { onStories() }
                }
            }
            item {
                ModeTile("🎯", "جلسة دراسة ذكية", "خطط وراجع تقدمك من غير ضغط", Modifier.fillMaxWidth()) { onRoute("study") }
            }
            item {
                Text("كل التقدم محفوظ على هذا الجهاز. مشاركة إنجاز بسيط مع العائلة تظل اختيارية من إعدادات العائلة.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun ModeTile(icon: String, title: String, sub: String, modifier: Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(20.dp), color = Color.White, border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD9E1EF))) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(icon, fontSize = 28.sp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
            Icon(Icons.Default.ChevronLeft, null, tint = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun TaskGrid(kid: Kid) {
    val haptic = LocalHapticFeedback.current
    val done = Kids.doneOn(kid.id)
    val tasks = Kids.tasks(kid.id)
    var cheer by remember { mutableStateOf<String?>(null) }
    if (tasks.isEmpty()) { Text("الوالدين يختاروا المهام من ركن الوالدين 🔒", color = MaterialTheme.colorScheme.outline); return }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        tasks.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { t ->
                    val on = t.id in done
                    val sc by animateFloatAsState(if (on) 1.03f else 1f, spring(dampingRatio = 0.4f), label = "s")
                    Surface(
                        onClick = {
                            val d = Kids.toggle(kid.id, t)
                            if (d > 0) { haptic.performHapticFeedback(HapticFeedbackType.LongPress); cheer = listOf("برافو! 🎉", "شاطر جداً! 🌟", "ربنا يبارك فيك 🤍", "جميل! كمّل 💪").random() }
                        },
                        shape = RoundedCornerShape(20.dp),
                        color = if (on) KidGreen.copy(alpha = 0.18f) else Color.White,
                        border = androidx.compose.foundation.BorderStroke(2.dp, if (on) KidGreen else Color(0xFFE8DCC4)),
                        modifier = Modifier.weight(1f).heightIn(min = 104.dp).scale(sc),
                    ) {
                        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(if (on) "✅" else t.icon, fontSize = 30.sp)
                            Text(t.title, textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color(0xFF3B3125))
                            Text("${"⭐".repeat(t.stars)} ${t.growth.icon}", fontSize = 12.sp)
                        }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        cheer?.let { c ->
            LaunchedEffect(c) { delay(1600); cheer = null }
            Text(c, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = KidGreen, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        }
        Text("لو نسيت تعلّم مهمة، دوس عليها تاني تشيلها. مفيش مشكلة 🤍", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
    }
}

/** The Tree of Good: grows with this month's tasks (leaves, flowers, fruits, lights, branches). */
@Composable
private fun TreeCard(kid: Kid) {
    val g = Kids.growth(kid.id)
    val total = g.values.sum()
    GoldCard {
        Text("شجرة الخير 🌳", fontFamily = Amiri, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Text("كل مهمة بتضيف حاجة لشجرتك الشهر ده", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
        val tm = androidx.compose.ui.text.rememberTextMeasurer()
        Canvas(Modifier.fillMaxWidth().height(230.dp)) {
            val cx = size.width / 2
            val ground = size.height - 14.dp.toPx()
            drawOval(Color(0xFF9CCC65).copy(alpha = 0.4f), Offset(cx - size.width * 0.35f, ground - 8.dp.toPx()), Size(size.width * 0.7f, 18.dp.toPx()))
            // trunk grows a little with the number of branches
            val h = 70.dp.toPx() + (g[Growth.BRANCH] ?: 0).coerceAtMost(10) * 4.dp.toPx()
            val trunk = Path().apply {
                moveTo(cx - 12.dp.toPx(), ground); lineTo(cx - 7.dp.toPx(), ground - h); lineTo(cx + 7.dp.toPx(), ground - h); lineTo(cx + 12.dp.toPx(), ground); close()
            }
            drawPath(trunk, Color(0xFF8D6E63))
            val crownR = 50.dp.toPx() + total.coerceAtMost(40) * 1.6f.dp.toPx()
            val cy = ground - h - crownR * 0.55f
            drawCircle(Color(0xFF66BB6A).copy(alpha = if (total == 0) 0.25f else 0.85f), crownR, Offset(cx, cy))
            drawCircle(Color(0xFF81C784).copy(alpha = 0.6f), crownR * 0.7f, Offset(cx - crownR * 0.35f, cy - crownR * 0.2f))
            // place the items around the crown, deterministic positions
            var i = 0
            Growth.values().forEach { gr ->
                repeat((g[gr] ?: 0).coerceAtMost(14)) {
                    val ang = (i * 137.5) * Math.PI / 180
                    val rr = crownR * (0.25f + ((i * 37) % 70) / 100f)
                    val p = Offset(cx + (rr * kotlin.math.cos(ang)).toFloat(), cy + (rr * kotlin.math.sin(ang)).toFloat())
                    val res = tm.measure(gr.icon, androidx.compose.ui.text.TextStyle(fontSize = 15.sp))
                    drawText(res, topLeft = Offset(p.x - res.size.width / 2f, p.y - res.size.height / 2f))
                    i++
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Growth.values().forEach { gr -> Text("${gr.icon} ${g[gr] ?: 0}", fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun PassportCard(kid: Kid) {
    val g = Kids.growth(kid.id)
    val days = Kids.activeDays(kid.id)
    GoldCard {
        Text("جواز سفر الخير 🛂", fontFamily = Amiri, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Text("$days يوم فيهم خير الشهر ده", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Growth.values().forEach { gr ->
                val n = g[gr] ?: 0
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(52.dp).clip(CircleShape).background(if (n > 0) Gold.copy(alpha = 0.25f) else Color(0xFFEDE7DA))
                            .border(2.dp, if (n > 0) Gold else Color(0xFFD7CCB5), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { Text(if (n > 0) gr.icon else "·", fontSize = 24.sp) }
                    Text(stampName(gr), fontSize = 11.sp)
                }
            }
        }
    }
}

private fun stampName(g: Growth) = when (g) {
    Growth.LIGHT -> "ختم الصلاة"
    Growth.LEAF -> "ختم القرآن والذكر"
    Growth.FLOWER -> "ختم المساعدة"
    Growth.FRUIT -> "ختم الرحمة"
    Growth.BRANCH -> "ختم الصلة والعلم"
}

@Composable
private fun RewardsCard(kid: Kid) {
    val stars = Kids.stars(kid.id)
    GoldCard {
        Text("صندوق المفاجآت 🎁", fontFamily = Amiri, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Spacer(Modifier.height(6.dp))
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Kids.rewards.forEach { r ->
                val on = stars >= r.stars
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(70.dp)) {
                    Text(if (on) r.icon else "🔒", fontSize = 28.sp)
                    Text(if (on) r.title else "${r.stars} ⭐", fontSize = 10.sp, textAlign = TextAlign.Center, maxLines = 2)
                }
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        val ctx = androidx.compose.ui.platform.LocalContext.current
        Text("اطلب مكافأة من بابا وماما (معاك ${Kids.available(kid.id)} ⭐)", fontWeight = FontWeight.Bold)
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Kids.homeRewards.forEachIndexed { i, t ->
                val cost = Kids.homeRewardCost.getOrElse(i) { 30 }
                AssistChip(onClick = { toast(ctx, if (Kids.request(kid, t, cost)) "الطلب وصل لبابا وماما 🎉" else "محتاج نجوم أكتر ⭐") }, label = { Text("$t • $cost ⭐") })
            }
        }
        Kids.requests().filter { it.kid == kid.id }.forEach { r -> Text("⏳ مستني موافقة: ${r.title}", style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun FastCard(kid: Kid) {
    val v = Kids.fast(kid.id)
    GoldCard {
        Text("صيامي النهارده 🌙", fontFamily = Amiri, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Text("على قد قدرتك، وصحتك أهم. اسأل بابا وماما 🤍", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(1 to "اتسحّرت", 2 to "صمت لحد الظهر", 3 to "صمت اليوم كله").forEach { (k, t) ->
                FilterChip(v == k, { Kids.setFast(kid.id, if (v == k) 0 else k) }, label = { Text(t) })
            }
        }
        if (v > 0) Text(if (v == 3) "محاولة رائعة! ربنا يتقبل منك 🌟" else "محاولة جميلة! 🌙", color = KidGreen, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun KidTile(icon: String, label: String, modifier: Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(20.dp), color = Color.White, border = androidx.compose.foundation.BorderStroke(2.dp, KidSky.copy(alpha = 0.5f)), modifier = modifier.height(96.dp)) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(icon, fontSize = 32.sp)
            Text(label, fontWeight = FontWeight.Bold, color = Color(0xFF3B3125), textAlign = TextAlign.Center, fontSize = 14.sp, lineHeight = 16.sp)
        }
    }
}

// ================================================================= games

@Composable
private fun MemoryGame(kid: Kid, onDone: () -> Unit) {
    val faces = remember { (listOf("🕌", "🌙", "⭐", "📖", "🤲", "🏮").flatMap { listOf(it, it) }).shuffled() }
    val open = remember { mutableStateListOf<Int>() }
    val matched = remember { mutableStateListOf<Int>() }
    var moves by remember { mutableIntStateOf(0) }
    var won by remember { mutableStateOf(false) }
    LaunchedEffect(open.size) {
        if (open.size == 2) {
            moves++
            delay(650)
            if (faces[open[0]] == faces[open[1]]) matched.addAll(open)
            open.clear()
            if (matched.size == faces.size && !won) { won = true; Kids.addStars(kid.id, 3) }
        }
    }
    ScreenScaffold("لعبة الذاكرة", onBack = onDone) { pad ->
        Column(Modifier.fillMaxSize().background(KidBg).padding(pad).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (won) "برافو! خلصتها في $moves محاولة ⭐⭐⭐" else "اقلب كارتين متشابهين • $moves محاولة", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(Modifier.height(12.dp))
            LazyVerticalGrid(GridCells.Fixed(3), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                itemsIndexed(faces) { i, f ->
                    val shown = i in open || i in matched
                    Surface(
                        onClick = { if (!shown && open.size < 2) open.add(i) },
                        shape = RoundedCornerShape(16.dp), color = if (i in matched) KidGreen.copy(alpha = 0.2f) else if (shown) Color.White else KidSky,
                        modifier = Modifier.aspectRatio(1f),
                    ) { Box(contentAlignment = Alignment.Center) { Text(if (shown) f else "?", fontSize = 34.sp, color = if (shown) Color.Unspecified else Color.White) } }
                }
            }
            if (won) { Spacer(Modifier.height(12.dp)); Button(onClick = onDone) { Text("رجوع") } }
        }
    }
}

/** Put the steps of wudu in order (the common order taught to children). */
@Composable
private fun WuduGame(kid: Kid, onDone: () -> Unit) {
    val steps = listOf("النية وأقول: بسم الله", "أغسل كفّيّ", "المضمضة والاستنشاق", "أغسل وجهي", "أغسل يديّ للمرفقين", "أمسح رأسي وأذنيّ", "أغسل رجليّ للكعبين")
    val shuffled = remember { steps.indices.shuffled() }
    var nextStep by remember { mutableIntStateOf(0) }
    var hint by remember { mutableStateOf("") }
    val haptic = LocalHapticFeedback.current
    ScreenScaffold("رتّب خطوات الوضوء", onBack = onDone) { pad ->
        Column(Modifier.fillMaxSize().background(KidBg).padding(pad).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (nextStep == steps.size) "ممتاز! رتبت الوضوء صح 💧⭐⭐⭐" else "دوس على الخطوة رقم ${nextStep + 1}", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            if (hint.isNotBlank()) Text(hint, color = Warn)
            shuffled.forEach { i ->
                val done = i < nextStep
                Surface(
                    onClick = {
                        if (done || nextStep == steps.size) return@Surface
                        if (i == nextStep) {
                            nextStep++; hint = ""
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            if (nextStep == steps.size) Kids.addStars(kid.id, 3)
                        } else hint = "فكّر تاني، إيه اللي بنعمله قبلها؟ 🤔"
                    },
                    shape = RoundedCornerShape(16.dp), color = if (done) KidGreen.copy(alpha = 0.18f) else Color.White,
                    border = androidx.compose.foundation.BorderStroke(2.dp, if (done) KidGreen else KidSky.copy(alpha = 0.4f)), modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (done) "${i + 1}" else "💧", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Spacer(Modifier.width(10.dp))
                        Text(steps[i], fontSize = 17.sp, color = Color(0xFF3B3125))
                    }
                }
            }
            if (nextStep == steps.size) Button(onClick = onDone) { Text("رجوع") }
        }
    }
}

/** Five easy questions; a wrong answer only says "think again". */
@Composable
private fun KidsQuiz(kid: Kid, onDone: () -> Unit) {
    val qs = remember { Quiz.pick(5, Quiz.religionCats.keys, lvl = 1) }
    var i by remember { mutableIntStateOf(0) }
    var wrong by remember { mutableStateOf(setOf<Int>()) }
    var right by remember { mutableIntStateOf(0) }
    ScreenScaffold("أسئلة سهلة", onBack = onDone) { pad ->
        Column(Modifier.fillMaxSize().background(KidBg).padding(pad).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val q = qs.getOrNull(i)
            if (q == null) {
                Text("خلصت! جاوبت $right من ${qs.size} من أول مرة 🌟", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Button(onClick = onDone) { Text("رجوع") }
                return@Column
            }
            Text("سؤال ${i + 1} من ${qs.size}", color = MaterialTheme.colorScheme.outline)
            Text(q.q, fontFamily = Amiri, fontWeight = FontWeight.Bold, fontSize = 22.sp)
            q.answers.forEachIndexed { k, a ->
                Surface(
                    onClick = {
                        if (k == q.correct) {
                            if (wrong.isEmpty()) { right++; Kids.addStars(kid.id, 1) }
                            i++; wrong = emptySet()
                        } else wrong = wrong + k
                    },
                    shape = RoundedCornerShape(16.dp), color = if (k in wrong) Color(0xFFFFE0E0) else Color.White,
                    border = androidx.compose.foundation.BorderStroke(2.dp, KidSky.copy(alpha = 0.4f)), modifier = Modifier.fillMaxWidth(),
                ) { Text(a, Modifier.padding(14.dp), fontSize = 18.sp) }
            }
            if (wrong.isNotEmpty()) Text("فكّر تاني، إنت قريب! 🤔", color = Warn)
        }
    }
}

// ================================================================= parent area

@Composable
private fun PinDialog(onDismiss: () -> Unit, onResult: (Boolean) -> Unit) {
    var pin by remember { mutableStateOf("") }
    var err by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ركن الوالدين") },
        text = {
            Column {
                OutlinedTextField(pin, { pin = it.filter { c -> c.isDigit() }.take(6); err = false }, label = { Text("الرقم السري") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
                if (err) Text("الرقم غلط", color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = { Button(onClick = { if (Kids.checkPin(pin)) onResult(true) else err = true }) { Text("دخول") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}

@Composable
private fun ParentArea(onClose: () -> Unit) {
    @Suppress("UNUSED_VARIABLE") val v = Kids.version.intValue
    var editing by remember { mutableStateOf<Kid?>(null) }
    var adding by remember { mutableStateOf(false) }
    var tasksFor by remember { mutableStateOf<Kid?>(null) }
    var pinDialog by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<Kid?>(null) }
    androidx.activity.compose.BackHandler { onClose() }
    tasksFor?.let { k -> TaskPicker(k) { tasksFor = null }; return }
    ScreenScaffold("ركن الوالدين", onBack = onClose) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val reqs = Kids.requests()
            if (reqs.isNotEmpty()) item {
                GoldCard {
                    Text("طلبات المكافآت", fontWeight = FontWeight.Bold)
                    reqs.forEach { r ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.material3.Text("${r.kidName}: ${tr(r.title)} • ⭐${r.cost}", Modifier.weight(1f))
                            TextButton(onClick = { Kids.decide(r.id, true) }) { Text("موافق") }
                            TextButton(onClick = { Kids.decide(r.id, false) }) { Text("لأ", color = Danger) }
                        }
                    }
                }
            }
            item {
                AppCard {
                    Text("الخصوصية", fontWeight = FontWeight.Bold)
                    Text("كل بيانات الأطفال على الموبايل ده بس. مفيش صور ولا أسماء حقيقية مطلوبة، ومفيش إعلانات ولا روابط خارجية في قسم الأطفال. المكافآت جوه التطبيق بس.", style = MaterialTheme.typography.bodySmall)
                }
            }
            items(Kids.kids(), key = { it.id }) { k ->
                AppCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(k.avatar, fontSize = 30.sp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(k.name, fontWeight = FontWeight.Bold)
                            Text("${k.age} سنين • ⭐ ${Kids.stars(k.id)}" + if (k.fasting) " • متابعة الصيام مفعّلة" else "", style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(onClick = { tasksFor = k }) { Icon(Icons.Default.Checklist, "المهام") }
                        IconButton(onClick = { editing = k }) { Icon(Icons.Default.Edit, "تعديل") }
                        IconButton(onClick = { confirmDelete = k }) { Icon(Icons.Default.Delete, "حذف") }
                    }
                }
            }
            item { Button(onClick = { adding = true }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.PersonAdd, null); Spacer(Modifier.width(6.dp)); Text("ضيف طفل") } }
            item { OutlinedButton(onClick = { pinDialog = true }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Lock, null); Spacer(Modifier.width(6.dp)); Text(if (Kids.hasPin) "غيّر الرقم السري" else "حط رقم سري لركن الوالدين") } }
            item {
                AppCard {
                    Text("أفكار مكافآت في البيت", fontWeight = FontWeight.Bold)
                    Kids.homeRewards.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
                    Text("المكافأة على المحاولة مش على المقارنة بين الإخوات.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
            }
        }
    }
    if (adding || editing != null) KidEditor(editing) { adding = false; editing = null }
    if (pinDialog) {
        var p by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { pinDialog = false },
            title = { Text("الرقم السري") },
            text = { OutlinedTextField(p, { p = it.filter { c -> c.isDigit() }.take(6) }, label = { Text("٤ أرقام أو أكتر (فاضي = من غير رقم)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)) },
            confirmButton = { Button(onClick = { Kids.setPin(p); pinDialog = false }, enabled = p.isEmpty() || p.length >= 4) { Text("حفظ") } },
            dismissButton = { TextButton(onClick = { pinDialog = false }) { Text("إلغاء") } },
        )
    }
    confirmDelete?.let { k -> ConfirmDialog("تمسح ${k.name}؟", "هتتمسح نجومه ومهامه وشجرته.", "امسح", { confirmDelete = null }) { Kids.deleteKid(k.id); confirmDelete = null } }
}

@Composable
private fun KidEditor(existing: Kid?, onDone: () -> Unit) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var avatar by remember { mutableStateOf(existing?.avatar ?: Kids.avatars.first()) }
    var age by remember { mutableIntStateOf(existing?.age ?: 6) }
    var fasting by remember { mutableStateOf(existing?.fasting ?: false) }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(if (existing == null) "طفل جديد" else "تعديل") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it.take(20) }, label = { Text("اسم الدلع") }, singleLine = true)
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Kids.avatars.forEach { a ->
                        Box(Modifier.size(40.dp).clip(CircleShape).background(if (a == avatar) KidSky.copy(alpha = 0.3f) else Color.Transparent).clickable { avatar = a }, contentAlignment = Alignment.Center) { Text(a, fontSize = 22.sp) }
                    }
                }
                Text("السن: $age", fontWeight = FontWeight.SemiBold)
                Slider(age.toFloat(), { age = it.toInt() }, valueRange = 3f..14f, steps = 10)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("متابعة الصيام في رمضان", Modifier.weight(1f))
                    Switch(fasting, { fasting = it })
                }
                Text("متابعة الصيام اختيارية، والطفل بيشوف تشجيع بس.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
        },
        confirmButton = {
            Button(onClick = {
                Kids.saveKid(Kid(existing?.id ?: java.util.UUID.randomUUID().toString().take(8), name.ifBlank { tr("بطلنا") }, avatar, age, fasting))
                onDone()
            }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDone) { Text("إلغاء") } },
    )
}

@Composable
private fun TaskPicker(kid: Kid, onDone: () -> Unit) {
    val chosen = remember { mutableStateListOf(*Kids.tasks(kid.id).map { it.id }.toTypedArray()) }
    androidx.activity.compose.BackHandler { Kids.setTasks(kid.id, chosen.toList()); onDone() }
    ScreenScaffold("مهام ${kid.name}", onBack = { Kids.setTasks(kid.id, chosen.toList()); onDone() }) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            item { Text("اختار المهام اللي تناسب سنه (يفضل من ٤ لـ ٨ مهام في اليوم)", style = MaterialTheme.typography.bodySmall) }
            items(Kids.library, key = { it.id }) { t ->
                val on = t.id in chosen
                AppCard(onClick = { if (on) chosen.remove(t.id) else chosen.add(t.id) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(t.icon, fontSize = 24.sp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(t.title, fontWeight = FontWeight.SemiBold)
                            Text("${"⭐".repeat(t.stars)} • ${t.growth.icon} ${t.growth.label}" + if (kid.age < t.minAge) " • لسن ${t.minAge}+" else "", style = MaterialTheme.typography.bodySmall)
                        }
                        Checkbox(on, { if (on) chosen.remove(t.id) else chosen.add(t.id) })
                    }
                }
            }
        }
    }
}

/** "Today's game and story": in Ramadan, game n and story n on day n (30 of each); the rest of the year it rotates daily. */
@Composable
private fun DailyKidsCard(kid: Kid, onOpen: (String) -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val today = LocalDate.now(zone)
    val inR = isRamadan(today)
    val n = if (inR) com.mohamed.safi.faith.Ramadan.day(today) else (today.toEpochDay() % 30).toInt() + 1
    val games = remember(kid.age) { KidData.allGames(ctx, kid.age) }
    val stories = remember(kid.age) { kidStoryIds(ctx, kid.age) }
    val g = games.getOrNull((n - 1) % games.size.coerceAtLeast(1)) ?: return
    val st = stories.getOrNull((n - 1) % stories.size.coerceAtLeast(1))
    Surface(shape = RoundedCornerShape(22.dp), color = Color(0xFF1A2A4F), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(if (inR) "🌙 تقويم رمضان: اليوم $n" else "✨ مفاجأة النهارده", fontFamily = Amiri, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Gold)
            Text(if (inR) "كل يوم في رمضان لعبة جديدة وقصة جديدة" else "لعبة وقصة مختارين ليك النهارده", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.8f))
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(onClick = { onOpen(g.id) }, shape = RoundedCornerShape(16.dp), color = Color.White, modifier = Modifier.weight(1f)) {
                    Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(g.icon, fontSize = 30.sp); Text("لعبة اليوم", style = MaterialTheme.typography.labelSmall, color = Color(0xFF3B3125))
                        Text(g.title, fontWeight = FontWeight.Bold, color = Color(0xFF3B3125), textAlign = TextAlign.Center, maxLines = 2)
                    }
                }
                if (st != null) Surface(onClick = { onOpen("story:" + st.first) }, shape = RoundedCornerShape(16.dp), color = Color(0xFFFFE9B8), modifier = Modifier.weight(1f)) {
                    Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(st.second, fontSize = 30.sp); Text("قصة اليوم", style = MaterialTheme.typography.labelSmall, color = Color(0xFF3B3125))
                        Text(st.third, fontWeight = FontWeight.Bold, color = Color(0xFF3B3125), textAlign = TextAlign.Center, maxLines = 2)
                    }
                }
            }
        }
    }
}
