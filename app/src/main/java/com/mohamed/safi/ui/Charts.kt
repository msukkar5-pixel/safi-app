package com.mohamed.safi.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import com.mohamed.safi.ui.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/** Minimal line chart: points are (x, y); optional dashed target line. */
@Composable
fun LineChart(
    points: List<Pair<Double, Double>>,
    modifier: Modifier = Modifier.fillMaxWidth().height(160.dp),
    color: Color = Brand,
    target: Double? = null,
) {
    val grid = MaterialTheme.colorScheme.outlineVariant
    val targetColor = MaterialTheme.colorScheme.tertiary
    Canvas(modifier) {
        if (points.size < 2) return@Canvas
        val xs = points.map { it.first }
        val ys = points.map { it.second } + listOfNotNull(target)
        val minX = xs.min(); val maxX = xs.max()
        var minY = ys.min(); var maxY = ys.max()
        val pad = ((maxY - minY) * 0.15).coerceAtLeast(0.5)
        minY -= pad; maxY += pad
        val w = size.width; val h = size.height
        // RTL screens still read time left → right for charts
        fun px(x: Double) = if (maxX == minX) w / 2 else ((x - minX) / (maxX - minX) * w).toFloat()
        fun py(y: Double) = (h - (y - minY) / (maxY - minY) * h).toFloat()
        for (i in 0..3) {
            val y = h * i / 3f
            drawLine(grid, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
        }
        if (target != null) {
            drawLine(
                targetColor, Offset(0f, py(target)), Offset(w, py(target)), strokeWidth = 3f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f)),
            )
        }
        val path = Path()
        points.forEachIndexed { i, (x, y) ->
            if (i == 0) path.moveTo(px(x), py(y)) else path.lineTo(px(x), py(y))
        }
        drawPath(path, color, style = Stroke(width = 5f))
        points.forEach { (x, y) -> drawCircle(color, 6f, Offset(px(x), py(y))) }
    }
}

/** Ring progress (0..1+) used for calories / protein / water / steps. */
@Composable
fun Ring(fraction: Float, color: Color, modifier: Modifier) {
    val track = color.copy(alpha = 0.15f)
    Canvas(modifier) {
        val stroke = size.minDimension * 0.12f
        val inset = stroke / 2
        val arcSize = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke)
        drawArc(track, -90f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
        drawArc(
            if (fraction > 1.05f) Danger else color, -90f, 360f * fraction.coerceIn(0f, 1f), false,
            Offset(inset, inset), arcSize, style = Stroke(stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round),
        )
    }
}
