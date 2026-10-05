package com.mohamed.safi.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

/** Keys used by the guides in assets/deen. Unknown keys draw nothing. */
val deenDiagrams = setOf(
    "umrah_flow", "miqat", "ihram", "tawaf", "sai", "hajj_days", "nusuk_types", "arafah", "day10", "jamarat", "ruqyah_how", "tahseen_day",
)

@Composable
fun DeenDiagram(key: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth().border(1.dp, Gold.copy(alpha = 0.45f), RoundedCornerShape(16.dp)),
    ) {
        Box(Modifier.padding(12.dp)) {
            when (key) {
                "umrah_flow" -> FlowSteps(
                    listOf(
                        Step("🧭", "الإحرام من الميقات", "تنوي العمرة وتقول «لبيك اللهم عمرة» وتلبّي"),
                        Step("🕋", "الطواف ٧ أشواط", "تبدأ من الحجر الأسود والكعبة على يسارك"),
                        Step("🤲", "ركعتان خلف المقام", "إن تيسّر، ثم تشرب من زمزم"),
                        Step("⛰️", "السعي ٧ أشواط", "تبدأ بالصفا وتنتهي بالمروة"),
                        Step("✂️", "الحلق أو التقصير", "وبكده عمرتك تمّت وتحللت من الإحرام"),
                    ),
                )
                "day10" -> Column {
                    FlowSteps(
                        listOf(
                            Step("🪨", "رمي جمرة العقبة", "٧ حصيات، تكبّر مع كل حصاة، وتقطع التلبية"),
                            Step("🐑", "ذبح الهدي", "على المتمتع والقارن"),
                            Step("✂️", "الحلق أو التقصير", "والحلق أفضل للرجال"),
                            Step("🕋", "طواف الإفاضة", "ثم السعي للمتمتع، ولمن لم يسعَ قبل كده"),
                        ),
                    )
                    DiagramNote("الترتيب ده هو السنة، ولو قدّمت حاجة على حاجة فلا حرج: «افعل ولا حرج». وبعد فعل اثنين من الثلاثة (الرمي، الحلق، الطواف) تتحلل التحلل الأول ويحل لك كل شيء إلا النساء.")
                }
                "ruqyah_how" -> FlowSteps(
                    listOf(
                        Step("🤍", "النية واليقين", "إن الشفاء من الله وحده، والرقية سبب"),
                        Step("📖", "الفاتحة", "أعظم الرقى، وتُقرأ بتدبّر"),
                        Step("✨", "آية الكرسي وخواتيم البقرة", "وما تيسّر من آيات الرقية"),
                        Step("👐", "الإخلاص والمعوذتان", "مع النفث في الكفّين والمسح على الجسد"),
                        Step("✋", "يدك على موضع الألم", "«بسم الله» ٣ ثم «أعوذ بالله وقدرته من شر ما أجد وأحاذر» ٧"),
                        Step("🤲", "أدعية الرقية من السنة", "تلاقيها بنصّها وتخريجها في قسم «أدعية الرقية»"),
                    ),
                )
                "hajj_days" -> Timeline(
                    listOf(
                        Step("٨", "يوم التروية — منى", "الإحرام بالحج للمتمتع، والصلوات الخمس في منى قصراً بلا جمع، والمبيت بها"),
                        Step("٩", "يوم عرفة — عرفة", "بعد الشروق لعرفة، الظهر والعصر جمعاً وقصراً، والدعاء حتى الغروب. ده الركن الأعظم"),
                        Step("ليلة ١٠", "مزدلفة", "المغرب والعشاء جمعاً، المبيت، الفجر، ثم الدعاء حتى الإسفار"),
                        Step("١٠", "يوم النحر — منى ومكة", "رمي العقبة، الذبح، الحلق، طواف الإفاضة"),
                        Step("١١ و١٢", "أيام التشريق — منى", "رمي الجمرات الثلاث بعد الزوال والمبيت. ومن تعجّل يخرج قبل غروب يوم ١٢"),
                        Step("١٣", "لمن تأخّر — منى", "الرمي بعد الزوال ثم الخروج"),
                        Step("🧳", "قبل السفر — مكة", "طواف الوداع آخر عهدك بالبيت"),
                    ),
                )
                "tahseen_day" -> Timeline(
                    listOf(
                        Step("🌅", "الاستيقاظ", "أذكار الاستيقاظ (أول باب في حصن المسلم)"),
                        Step("☀️", "الصباح بعد الفجر", "أذكار الصباح: آية الكرسي، المعوذات، «بسم الله الذي لا يضر مع اسمه شيء…» ٣"),
                        Step("🚪", "الخروج من البيت", "ذكر الخروج من المنزل (في حصن المسلم)"),
                        Step("🏠", "دخول البيت", "تذكر الله عند الدخول وتسلّم"),
                        Step("🌙", "المساء", "أذكار المساء، و«أعوذ بكلمات الله التامات من شر ما خلق»"),
                        Step("🛏️", "النوم", "آية الكرسي، والإخلاص والمعوذتان مع النفث، وخواتيم البقرة"),
                    ),
                )
                "nusuk_types" -> NusukTypes()
                else -> CanvasDiagram(key)
            }
        }
    }
}

private data class Step(val badge: String, val title: String, val sub: String)

@Composable
private fun FlowSteps(steps: List<Step>) {
    val accent = MaterialTheme.colorScheme.primary
    Column {
        steps.forEachIndexed { i, s ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(accent.copy(alpha = 0.14f)).border(1.5.dp, Gold, CircleShape), contentAlignment = Alignment.Center) {
                    Text(s.badge, fontSize = 18.sp)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("${i + 1}. ${s.title}", fontWeight = FontWeight.Bold, color = accent)
                    Text(s.sub, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (i < steps.lastIndex) Box(Modifier.padding(start = 19.dp).width(2.dp).height(14.dp).background(Gold.copy(alpha = 0.7f)))
        }
    }
}

@Composable
private fun Timeline(steps: List<Step>) {
    val accent = MaterialTheme.colorScheme.primary
    Column {
        steps.forEachIndexed { i, s ->
            Row {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(64.dp)) {
                    Surface(shape = RoundedCornerShape(10.dp), color = accent, contentColor = MaterialTheme.colorScheme.onPrimary) {
                        Text(s.badge, fontWeight = FontWeight.Bold, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp))
                    }
                    if (i < steps.lastIndex) Box(Modifier.width(2.dp).height(34.dp).background(Gold.copy(alpha = 0.7f)))
                }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f).padding(bottom = 8.dp)) {
                    Text(s.title, fontWeight = FontWeight.Bold, color = accent)
                    Text(s.sub, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun DiagramNote(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp))
}

@Composable
private fun NusukTypes() {
    val types = listOf(
        Triple("التمتّع", "عمرة في أشهر الحج ← تتحلل ← تحرم بالحج يوم ٨", "الهدي: واجب"),
        Triple("القِران", "تحرم بالعمرة والحج معاً «لبيك عمرةً وحجاً» وتفضل محرم ليوم النحر", "الهدي: واجب"),
        Triple("الإفراد", "تحرم بالحج وحده «لبيك حجاً»", "الهدي: لا يجب"),
    )
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            types.forEach { (name, how, hady) ->
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                        .border(1.dp, Gold.copy(alpha = 0.6f), RoundedCornerShape(12.dp)).padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(name, fontFamily = Amiri, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(4.dp))
                    Text(how, fontSize = 12.sp, textAlign = TextAlign.Center, lineHeight = 17.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(hady, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Gold)
                }
            }
        }
        DiagramNote("التمتع أفضلها عند كثير من العلماء لمن لم يسق الهدي، وهو اللي أمر به النبي ﷺ أصحابه في حجة الوداع.")
    }
}

// ============================================================ drawn diagrams

private class Pen(val tm: TextMeasurer, val fg: Color, val accent: Color, val muted: Color, val bg: Color)

private fun DrawScope.label(
    p: Pen, text: String, center: Offset, color: Color = p.fg, size: TextUnit = 11.sp, bold: Boolean = false, maxW: Float = 150.dp.toPx(),
) {
    val r = p.tm.measure(
        tr(text),
        TextStyle(color = color, fontSize = size, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, textAlign = TextAlign.Center),
        constraints = Constraints(maxWidth = maxW.toInt().coerceAtLeast(1)),
    )
    drawText(r, topLeft = Offset(center.x - r.size.width / 2f, center.y - r.size.height / 2f))
}

private fun DrawScope.arrowHead(at: Offset, dir: Offset, color: Color, len: Float = 9.dp.toPx()) {
    val m = kotlin.math.sqrt(dir.x * dir.x + dir.y * dir.y).takeIf { it > 0f } ?: return
    val ux = dir.x / m
    val uy = dir.y / m
    val back = Offset(at.x - ux * len, at.y - uy * len)
    val nx = -uy * len * 0.55f
    val ny = ux * len * 0.55f
    val path = Path().apply {
        moveTo(at.x, at.y); lineTo(back.x + nx, back.y + ny); lineTo(back.x - nx, back.y - ny); close()
    }
    drawPath(path, color)
}

private fun DrawScope.arrow(from: Offset, to: Offset, color: Color, width: Float = 2.dp.toPx(), dashed: Boolean = false) {
    drawLine(color, from, to, width, StrokeCap.Round, if (dashed) PathEffect.dashPathEffect(floatArrayOf(10f, 8f)) else null)
    arrowHead(to, to - from, color)
}

@Composable
private fun CanvasDiagram(key: String) {
    val cs = MaterialTheme.colorScheme
    val tm = rememberTextMeasurer()
    val pen = Pen(tm, cs.onSurface, cs.primary, cs.onSurfaceVariant, cs.surfaceContainerLow)
    val h = when (key) {
        "tawaf" -> 330
        "sai" -> 360
        "miqat" -> 330
        "arafah" -> 280
        "jamarat" -> 230
        "ihram" -> 250
        else -> 0
    }
    if (h == 0) return
    Canvas(Modifier.fillMaxWidth().height(h.dp)) {
        when (key) {
            "tawaf" -> drawTawaf(pen)
            "sai" -> drawSai(pen)
            "miqat" -> drawMiqat(pen)
            "arafah" -> drawArafah(pen)
            "jamarat" -> drawJamarat(pen)
            "ihram" -> drawIhram(pen)
        }
    }
}

/**
 * Top view, schematic. Corners: الحجر الأسود bottom-right, العراقي top-right, الشامي top-left, اليماني bottom-left;
 * the door and the Maqam on the right wall, Hijr Ismail on the top wall. Walking counter-clockwise keeps the Kaaba on the left.
 */
private fun DrawScope.drawTawaf(p: Pen) {
    val c = Offset(size.width / 2f, size.height / 2f + 6.dp.toPx())
    val k = 70.dp.toPx()
    val tl = Offset(c.x - k / 2, c.y - k / 2)
    // the circuit
    val rx = size.width * 0.40f
    val ry = size.height * 0.40f
    drawOval(p.accent.copy(alpha = 0.08f), Offset(c.x - rx, c.y - ry), Size(rx * 2, ry * 2))
    drawOval(p.accent.copy(alpha = 0.55f), Offset(c.x - rx, c.y - ry), Size(rx * 2, ry * 2), style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))))
    // arrowheads, counter-clockwise on screen (angle decreasing)
    listOf(-20f, -110f, -200f, -290f).forEach { deg ->
        val t = Math.toRadians(deg.toDouble())
        val pt = Offset(c.x + rx * cos(t).toFloat(), c.y + ry * sin(t).toFloat())
        arrowHead(pt, Offset(rx * sin(t).toFloat(), -ry * cos(t).toFloat()), p.accent, 12.dp.toPx())
    }
    // Hijr Ismail (semicircle on the top wall)
    drawArc(Gold.copy(alpha = 0.35f), 180f, 180f, true, Offset(tl.x, tl.y - k * 0.45f), Size(k, k * 0.9f))
    drawArc(Gold, 180f, 180f, false, Offset(tl.x, tl.y - k * 0.45f), Size(k, k * 0.9f), style = Stroke(1.5.dp.toPx()))
    // the Kaaba
    drawRoundRect(Color(0xFF151515), tl, Size(k, k), CornerRadius(4.dp.toPx()))
    drawRect(Gold, Offset(tl.x, tl.y + k * 0.22f), Size(k, k * 0.08f))
    // door (right wall, near the Black Stone corner)
    drawRect(Gold, Offset(tl.x + k - 3.dp.toPx(), tl.y + k * 0.45f), Size(3.dp.toPx(), k * 0.3f))
    // Black Stone corner + start line
    val bs = Offset(tl.x + k, tl.y + k)
    drawCircle(Color(0xFF7A5C2E), 6.dp.toPx(), bs)
    drawLine(Color(0xFF2E9D5B), bs, Offset(c.x + rx + 4.dp.toPx(), bs.y + (ry - k / 2) * 0.55f), 3.dp.toPx(), StrokeCap.Round)
    // Yemeni corner
    val ym = Offset(tl.x, tl.y + k)
    drawCircle(p.accent, 5.dp.toPx(), ym)
    // Maqam Ibrahim
    val mq = Offset(tl.x + k + 26.dp.toPx(), c.y - k * 0.05f)
    drawRoundRect(Gold, Offset(mq.x - 6.dp.toPx(), mq.y - 8.dp.toPx()), Size(12.dp.toPx(), 16.dp.toPx()), CornerRadius(3.dp.toPx()))

    label(p, "الكعبة", Offset(c.x, c.y + k * 0.08f), Gold, 12.sp, true)
    label(p, "حِجر إسماعيل\n(من الكعبة — طُف من ورائه)", Offset(c.x, tl.y - k * 0.62f), p.fg, 10.sp)
    label(p, "الحجر الأسود\nابدأ وانتهِ هنا", Offset(bs.x + 8.dp.toPx(), bs.y + 44.dp.toPx()), Color(0xFF2E9D5B), 11.sp, true)
    label(p, "الركن اليماني\nاستلمه بيدك إن تيسّر", Offset(ym.x - 30.dp.toPx(), ym.y + 24.dp.toPx()), p.accent, 10.sp, true)
    label(p, "مقام إبراهيم", Offset(mq.x + 30.dp.toPx(), mq.y - 2.dp.toPx()), p.fg, 10.sp)
    label(p, "بين الركنين: ربنا آتنا في الدنيا حسنة…", Offset(c.x, c.y + ry + 2.dp.toPx()), p.muted, 10.sp, maxW = size.width * 0.8f)
    label(p, "الكعبة على يسارك دايماً — ٧ أشواط", Offset(c.x, 10.dp.toPx()), p.accent, 11.sp, true, size.width)
}

private fun DrawScope.drawSai(p: Pen) {
    val cx = size.width / 2f
    val top = 50.dp.toPx()
    val bottom = size.height - 50.dp.toPx()
    val w = 90.dp.toPx()
    // corridor
    drawRoundRect(p.accent.copy(alpha = 0.07f), Offset(cx - w / 2, top), Size(w, bottom - top), CornerRadius(8.dp.toPx()))
    drawRoundRect(p.accent.copy(alpha = 0.4f), Offset(cx - w / 2, top), Size(w, bottom - top), CornerRadius(8.dp.toPx()), style = Stroke(1.dp.toPx()))
    // green markers zone
    val g1 = top + (bottom - top) * 0.40f
    val g2 = top + (bottom - top) * 0.58f
    drawRect(Color(0xFF2E9D5B).copy(alpha = 0.18f), Offset(cx - w / 2, g1), Size(w, g2 - g1))
    drawLine(Color(0xFF2E9D5B), Offset(cx - w / 2 - 8.dp.toPx(), g1), Offset(cx + w / 2 + 8.dp.toPx(), g1), 4.dp.toPx(), StrokeCap.Round)
    drawLine(Color(0xFF2E9D5B), Offset(cx - w / 2 - 8.dp.toPx(), g2), Offset(cx + w / 2 + 8.dp.toPx(), g2), 4.dp.toPx(), StrokeCap.Round)
    // hills
    fun hill(y: Float, up: Boolean) {
        val hh = 26.dp.toPx()
        val path = Path().apply {
            moveTo(cx - w * 0.75f, y); quadraticBezierTo(cx, if (up) y - hh * 2 else y + hh * 2, cx + w * 0.75f, y); close()
        }
        drawPath(path, Color(0xFF8A6A3E).copy(alpha = 0.55f))
    }
    hill(top, true)
    hill(bottom, false)
    label(p, "المروة", Offset(cx, top - 26.dp.toPx()), p.fg, 14.sp, true)
    label(p, "الصفا", Offset(cx, bottom + 26.dp.toPx()), p.fg, 14.sp, true)
    // lap arrows
    val right = cx + w / 2 + 34.dp.toPx()
    val left = cx - w / 2 - 34.dp.toPx()
    arrow(Offset(right, bottom - 10.dp.toPx()), Offset(right, top + 10.dp.toPx()), p.accent, 3.dp.toPx())
    arrow(Offset(left, top + 10.dp.toPx()), Offset(left, bottom - 10.dp.toPx()), Gold, 3.dp.toPx())
    label(p, "١ ٣ ٥ ٧", Offset(right + 22.dp.toPx(), (top + bottom) / 2), p.accent, 12.sp, true, 40.dp.toPx())
    label(p, "٢ ٤ ٦", Offset(left - 22.dp.toPx(), (top + bottom) / 2), Gold, 12.sp, true, 40.dp.toPx())
    label(p, "الميلان الأخضران:\nالرجل يُسرع بينهما", Offset(cx, (g1 + g2) / 2), Color(0xFF2E9D5B), 10.sp, true, w + 30.dp.toPx())
    label(p, "ابدأ هنا", Offset(cx - w / 2 - 6.dp.toPx(), bottom + 8.dp.toPx()), p.accent, 10.sp, true, 60.dp.toPx())
    label(p, "النهاية هنا", Offset(cx + w / 2 + 14.dp.toPx(), top - 8.dp.toPx()), p.accent, 10.sp, true, 70.dp.toPx())
}

private fun DrawScope.drawMiqat(p: Pen) {
    val c = Offset(size.width / 2f, size.height / 2f + 10.dp.toPx())
    val r = minOf(size.width, size.height) * 0.36f
    // north arrow
    arrow(Offset(18.dp.toPx(), 40.dp.toPx()), Offset(18.dp.toPx(), 14.dp.toPx()), p.muted, 1.5.dp.toPx())
    label(p, "ش", Offset(18.dp.toPx(), 50.dp.toPx()), p.muted, 10.sp)
    data class M(val name: String, val now: String, val forWho: String, val deg: Double, val dist: Float)
    val list = listOf(
        M("ذو الحليفة", "أبيار علي", "أهل المدينة", -100.0, 1.0f),
        M("الجحفة", "رابغ", "الشام ومصر والمغرب", -150.0, 0.85f),
        M("ذات عِرق", "الضريبة", "أهل العراق", -40.0, 0.62f),
        M("قرن المنازل", "السيل الكبير", "نجد والخليج", 5.0, 0.6f),
        M("يلملم", "السعدية", "أهل اليمن", 110.0, 0.66f),
    )
    drawCircle(p.accent.copy(alpha = 0.06f), r * 1.05f, c)
    list.forEach { m ->
        val t = Math.toRadians(m.deg)
        val pt = Offset(c.x + r * m.dist * cos(t).toFloat(), c.y + r * m.dist * sin(t).toFloat())
        drawLine(p.accent.copy(alpha = 0.6f), c, pt, 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
        drawCircle(p.accent, 6.dp.toPx(), pt)
        val ly = if (pt.y < c.y) pt.y - 22.dp.toPx() else pt.y + 22.dp.toPx()
        val lx = pt.x.coerceIn(50.dp.toPx(), size.width - 50.dp.toPx())
        label(p, "${m.name} (${m.now})\n${m.forWho}", Offset(lx, ly), p.fg, 10.sp, false, 110.dp.toPx())
    }
    drawCircle(Color(0xFF151515), 13.dp.toPx(), c)
    drawCircle(Gold, 13.dp.toPx(), c, style = Stroke(2.dp.toPx()))
    label(p, "مكة", Offset(c.x, c.y + 24.dp.toPx()), Gold, 13.sp, true)
    label(p, "ومن كان أقرب من المواقيت يُحرم من مكانه", Offset(size.width / 2, size.height - 8.dp.toPx()), p.muted, 10.sp, false, size.width)
}

private fun DrawScope.drawArafah(p: Pen) {
    val l = 70.dp.toPx()
    val t = 30.dp.toPx()
    val r = size.width - 16.dp.toPx()
    val b = size.height - 40.dp.toPx()
    val area = Path().apply {
        moveTo(l, t + 20.dp.toPx()); lineTo(l + (r - l) * 0.45f, t); lineTo(r, t + 30.dp.toPx())
        lineTo(r - 10.dp.toPx(), b); lineTo(l + 20.dp.toPx(), b - 10.dp.toPx()); close()
    }
    drawPath(area, Color(0xFF2E9D5B).copy(alpha = 0.12f))
    drawPath(area, Color(0xFF2E9D5B), style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))))
    label(p, "عرفة — كلها موقف", Offset((l + r) / 2, (t + b) / 2 - 10.dp.toPx()), Color(0xFF2E9D5B), 14.sp, true, 200.dp.toPx())
    // Jabal al-Rahmah
    val jx = r - 70.dp.toPx()
    val jy = t + 70.dp.toPx()
    drawPath(Path().apply { moveTo(jx - 26.dp.toPx(), jy + 16.dp.toPx()); lineTo(jx, jy - 18.dp.toPx()); lineTo(jx + 26.dp.toPx(), jy + 16.dp.toPx()); close() }, Color(0xFF8A6A3E).copy(alpha = 0.7f))
    label(p, "جبل الرحمة\n(مش لازم تطلعه)", Offset(jx, jy + 36.dp.toPx()), p.fg, 10.sp)
    // Namirah mosque straddling the western boundary
    val mx = l + 18.dp.toPx()
    val my = (t + b) / 2 + 30.dp.toPx()
    drawRoundRect(Gold, Offset(mx - 22.dp.toPx(), my - 14.dp.toPx()), Size(44.dp.toPx(), 28.dp.toPx()), CornerRadius(4.dp.toPx()))
    label(p, "مسجد نمرة", Offset(mx, my), Color.Black, 9.sp, true)
    label(p, "مقدمته خارج عرفة", Offset(mx + 10.dp.toPx(), my + 26.dp.toPx()), Warn, 9.sp, true)
    // Wadi Uranah outside
    label(p, "بطن عُرَنة\nليس من عرفة", Offset(32.dp.toPx(), t + 30.dp.toPx()), Warn, 10.sp, true, 64.dp.toPx())
    label(p, "الوقوف: من الزوال لغروب الشمس، والدعاء مستقبل القبلة", Offset(size.width / 2, size.height - 14.dp.toPx()), p.muted, 10.sp, false, size.width)
}

private fun DrawScope.drawJamarat(p: Pen) {
    val y = size.height * 0.42f
    val xs = listOf(size.width * 0.82f, size.width * 0.5f, size.width * 0.18f)
    val names = listOf("الصغرى", "الوسطى", "العقبة (الكبرى)")
    val after = listOf("تقدّم وادعُ\nمستقبل القبلة", "خذ ذات الشمال\nوادعُ طويلاً", "لا تقف بعدها")
    // order arrows (right to left, the walking order from Mina's Khayf side towards Makkah)
    for (i in 0 until 2) arrow(Offset(xs[i] - 26.dp.toPx(), y), Offset(xs[i + 1] + 26.dp.toPx(), y), p.accent.copy(alpha = 0.7f), 2.dp.toPx(), dashed = true)
    xs.forEachIndexed { i, x ->
        drawOval(p.accent.copy(alpha = 0.15f), Offset(x - 24.dp.toPx(), y - 12.dp.toPx()), Size(48.dp.toPx(), 24.dp.toPx()))
        drawRoundRect(Color(0xFF8A7E6A), Offset(x - 8.dp.toPx(), y - 40.dp.toPx()), Size(16.dp.toPx(), 40.dp.toPx()), CornerRadius(3.dp.toPx()))
        label(p, "${i + 1}. ${names[i]}", Offset(x, y - 54.dp.toPx()), p.fg, 12.sp, true, 110.dp.toPx())
        label(p, "٧ حصيات", Offset(x, y + 22.dp.toPx()), Gold, 11.sp, true)
        label(p, after[i], Offset(x, y + 50.dp.toPx()), if (i < 2) p.accent else p.muted, 10.sp, i < 2, 110.dp.toPx())
    }
    label(p, "أيام التشريق بعد الزوال: بالترتيب ده كل يوم • يوم ١٠: العقبة بس", Offset(size.width / 2, size.height - 10.dp.toPx()), p.muted, 10.sp, false, size.width)
    label(p, "جهة مسجد الخيف ←", Offset(size.width * 0.86f, 12.dp.toPx()), p.muted, 9.sp)
    label(p, "→ جهة مكة", Offset(size.width * 0.14f, 12.dp.toPx()), p.muted, 9.sp)
}

private fun DrawScope.drawIhram(p: Pen) {
    val white = Color(0xFFF7F5EE)
    val line = p.fg.copy(alpha = 0.6f)
    fun figureBase(cx: Float, top: Float) {
        drawCircle(Color(0xFFD9B48F), 14.dp.toPx(), Offset(cx, top + 14.dp.toPx()))
    }
    // man
    val mx = size.width * 0.27f
    val t = 18.dp.toPx()
    figureBase(mx, t)
    val body = Rect(mx - 26.dp.toPx(), t + 32.dp.toPx(), mx + 26.dp.toPx(), t + 100.dp.toPx())
    // rida (upper sheet) draped, right shoulder bare (idtiba' look)
    val rida = Path().apply {
        moveTo(body.left, body.top + 6.dp.toPx()); lineTo(mx + 6.dp.toPx(), body.top); lineTo(body.right, body.top + 20.dp.toPx())
        lineTo(body.right, body.bottom); lineTo(body.left, body.bottom); close()
    }
    drawRoundRect(Color(0xFFD9B48F), Offset(body.left, body.top), Size(body.width, 28.dp.toPx()), CornerRadius(10.dp.toPx()))
    drawPath(rida, white)
    drawPath(rida, line, style = Stroke(1.dp.toPx()))
    // izar (lower sheet)
    drawRect(white, Offset(body.left - 2.dp.toPx(), body.bottom), Size(body.width + 4.dp.toPx(), 70.dp.toPx()))
    drawRect(line, Offset(body.left - 2.dp.toPx(), body.bottom), Size(body.width + 4.dp.toPx(), 70.dp.toPx()), style = Stroke(1.dp.toPx()))
    drawRect(Color(0xFF8A6A3E), Offset(body.left - 2.dp.toPx(), body.bottom - 2.dp.toPx()), Size(body.width + 4.dp.toPx(), 5.dp.toPx()))
    label(p, "الرجل", Offset(mx, size.height - 48.dp.toPx()), p.accent, 13.sp, true)
    label(p, "إزار ورداء أبيضان ونعلان\nبدون مخيط ولا غطاء رأس", Offset(mx, size.height - 20.dp.toPx()), p.fg, 10.sp, false, 150.dp.toPx())
    // woman
    val wx = size.width * 0.73f
    figureBase(wx, t)
    val dress = Path().apply {
        moveTo(wx - 20.dp.toPx(), t + 2.dp.toPx()); quadraticBezierTo(wx, t - 8.dp.toPx(), wx + 20.dp.toPx(), t + 2.dp.toPx())
        lineTo(wx + 22.dp.toPx(), t + 34.dp.toPx()); lineTo(wx + 40.dp.toPx(), t + 170.dp.toPx())
        lineTo(wx - 40.dp.toPx(), t + 170.dp.toPx()); lineTo(wx - 22.dp.toPx(), t + 34.dp.toPx()); close()
    }
    drawPath(dress, p.accent.copy(alpha = 0.35f))
    drawPath(dress, line, style = Stroke(1.dp.toPx()))
    drawOval(Color(0xFFD9B48F), Offset(wx - 9.dp.toPx(), t + 8.dp.toPx()), Size(18.dp.toPx(), 20.dp.toPx()))
    label(p, "المرأة", Offset(wx, size.height - 48.dp.toPx()), p.accent, 13.sp, true)
    label(p, "ملابسها العادية الساترة\nبلا نقاب ولا قفازين", Offset(wx, size.height - 20.dp.toPx()), p.fg, 10.sp, false, 150.dp.toPx())
}
