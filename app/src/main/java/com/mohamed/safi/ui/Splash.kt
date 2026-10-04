package com.mohamed.safi.ui

import android.graphics.BitmapFactory
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.json.JSONObject

private val SplashGold = Color(0xFFD4AF37)
private val SplashGoldSoft = Color(0xFFE9D8A6)

/** Opening dedication: صدقة جارية على روح المرحوم عبدالمحسن رمضان سكر. */
@Composable
fun DedicationSplash(onDone: () -> Unit) {
    val ctx = LocalContext.current
    val img = remember { runCatching { ctx.assets.open("splash/kaaba.jpg").use { BitmapFactory.decodeStream(it) }?.asImageBitmap() }.getOrNull() }
    val credit = remember {
        runCatching { JSONObject(ctx.assets.open("splash/credit.json").bufferedReader().use { it.readText() }) }.getOrNull()
            ?.let { "صورة الكعبة: ${it.optString("artist")} — ${it.optString("license")} — Wikimedia Commons" }
    }
    val quran = remember { runCatching { FontFamily(Font("fonts/quran.ttf", ctx.assets)) }.getOrNull() ?: FontFamily.Default }
    var done by remember { mutableStateOf(false) }
    fun finish() { if (!done) { done = true; onDone() } }

    val fade = remember { Animatable(0f) }
    val zoom = remember { Animatable(1.12f) }
    LaunchedEffect(Unit) {
        fade.animateTo(1f, tween(900))
    }
    LaunchedEffect(Unit) { zoom.animateTo(1f, tween(4200, easing = LinearOutSlowInEasing)) }
    LaunchedEffect(Unit) { delay(4000); finish() }

    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { finish() },
    ) {
        if (img != null) {
            Image(
                img, null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().fillMaxHeight(0.58f).align(Alignment.TopCenter).scale(zoom.value),
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.6f),
                    0.2f to Color.Black.copy(alpha = 0.15f),
                    0.4f to Color.Black.copy(alpha = 0.1f),
                    0.58f to Color.Black,
                    1f to Color.Black,
                ),
            ),
        )
        Column(
            Modifier.fillMaxSize().alpha(fade.value).systemBarsPadding().padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(12.dp))
            Text(
                "﴿رَّبِّ ٱرۡحَمۡهُمَا كَمَا رَبَّيَانِي صَغِيرٗا﴾",
                fontFamily = quran, fontSize = 26.sp, color = SplashGold, textAlign = TextAlign.Center, lineHeight = 44.sp,
            )
            Spacer(Modifier.weight(1f))
            Ornament()
            Spacer(Modifier.height(14.dp))
            Text("إلى روح أبي الطاهرة", color = SplashGoldSoft, fontSize = 16.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text(
                "المرحوم عبدالمحسن رمضان سكر",
                color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "هذا التطبيق صدقةٌ جاريةٌ ووقفٌ لله على روحه،\nفلا تبخل عليه بدعوة:",
                color = Color.White.copy(alpha = 0.9f), fontSize = 15.sp, textAlign = TextAlign.Center, lineHeight = 26.sp,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "اللهم ارحمه رحمةً واسعة، واغفر له، ونوّر قبره، واجعل كل خيرٍ يُعمل به هنا في ميزان حسناته، واجعله من أهل الفردوس الأعلى",
                color = SplashGold, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, lineHeight = 30.sp,
            )
            Spacer(Modifier.height(14.dp))
            Ornament()
            Spacer(Modifier.height(20.dp))
            Text("المس الشاشة للمتابعة", color = Color.White.copy(alpha = 0.45f), fontSize = 12.sp)
            if (credit != null) {
                Spacer(Modifier.height(6.dp))
                Text(credit, color = Color.White.copy(alpha = 0.3f), fontSize = 9.sp, textAlign = TextAlign.Center, maxLines = 2)
            }
        }
    }
}

@Composable
private fun Ornament() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(60.dp).height(1.dp).background(Brush.horizontalGradient(listOf(Color.Transparent, SplashGold))))
        Text("  ۞  ", color = SplashGold, fontSize = 18.sp)
        Box(Modifier.width(60.dp).height(1.dp).background(Brush.horizontalGradient(listOf(SplashGold, Color.Transparent))))
    }
}

