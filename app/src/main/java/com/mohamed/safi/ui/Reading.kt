package com.mohamed.safi.ui

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.TextFields
import com.mohamed.safi.ui.Text
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import com.mohamed.safi.SafiApp

/** A reading theme: background, text, accent. "app" follows the app's own light/dark theme. */
data class ReadTheme(val id: String, val name: String, val bg: Color, val fg: Color, val accent: Color, val card: Color, val dark: Boolean)

/** Eye-comfort settings shared by every reading screen (Quran, books, hadith, adhkar, guides). */
object ReadPrefs {
    val themes = listOf(
        ReadTheme("app", "التطبيق", Color.Unspecified, Color.Unspecified, Color.Unspecified, Color.Unspecified, false),
        ReadTheme("paper", "ورقي", Color(0xFFFBF7EC), Color(0xFF2A2620), Color(0xFF0E5A4A), Color(0xFFF3ECDA), false),
        ReadTheme("sepia", "سيبيا", Color(0xFFF1E5C8), Color(0xFF4A3A26), Color(0xFF8A5A1E), Color(0xFFE8D9B4), false),
        ReadTheme("green", "أخضر هادئ", Color(0xFFE6EFE4), Color(0xFF1E2C22), Color(0xFF2F6B4F), Color(0xFFD8E6D5), false),
        ReadTheme("night", "ليلي", Color(0xFF1C1B19), Color(0xFFD9D2C3), Color(0xFFC9A24B), Color(0xFF272522), true),
        ReadTheme("black", "أسود", Color(0xFF000000), Color(0xFFB7AF9F), Color(0xFFB08F45), Color(0xFF121110), true),
    )
    private fun sp() = SafiApp.instance.getSharedPreferences("safi_read", Context.MODE_PRIVATE)

    // A version counter so every reading screen recomposes when a setting changes.
    val version = mutableIntStateOf(0)
    private fun bump() { version.intValue++ }

    var theme: String get() = sp().getString("theme", "app")!!; set(v) { sp().edit { putString("theme", v) }; bump() }
    /** Text size multiplier 0.85–1.8 */
    var scale: Float get() = sp().getFloat("scale", 1f); set(v) { sp().edit { putFloat("scale", v) }; bump() }
    /** Line height multiplier 1.3–2.4 */
    var line: Float get() = sp().getFloat("line", 1.75f); set(v) { sp().edit { putFloat("line", v) }; bump() }
    /** Warm (blue-light) filter 0–0.4 */
    var warm: Float get() = sp().getFloat("warm", 0f); set(v) { sp().edit { putFloat("warm", v) }; bump() }
    /** Extra dimming 0–0.6 */
    var dim: Float get() = sp().getFloat("dim", 0f); set(v) { sp().edit { putFloat("dim", v) }; bump() }
    var keepOn: Boolean get() = sp().getBoolean("keepOn", true); set(v) { sp().edit { putBoolean("keepOn", v) }; bump() }
    /** "amiri" or "sans" */
    var font: String get() = sp().getString("font", "amiri")!!; set(v) { sp().edit { putString("font", v) }; bump() }
    /** Auto night: switch to the night theme after Isha-ish hours (20:00–05:00) */
    var autoNight: Boolean get() = sp().getBoolean("autoNight", false); set(v) { sp().edit { putBoolean("autoNight", v) }; bump() }

    fun current(): ReadTheme {
        val h = java.time.LocalTime.now().hour
        val id = if (autoNight && (h >= 20 || h < 5)) "night" else theme
        return themes.firstOrNull { it.id == id } ?: themes[0]
    }
    fun reset() { sp().edit { clear() }; bump() }
}

/** Resolved style for the current reading settings. */
data class ReadStyle(val bg: Color, val fg: Color, val accent: Color, val card: Color, val dark: Boolean, val scale: Float, val line: Float, val family: FontFamily) {
    fun size(base: Float) = (base * scale).sp
    fun lineH(base: Float) = (base * scale * line).sp
}

@Composable
fun rememberReadStyle(): ReadStyle {
    ReadPrefs.version.intValue // subscribe
    val cs = MaterialTheme.colorScheme
    val t = ReadPrefs.current()
    val app = t.id == "app"
    return ReadStyle(
        bg = if (app) cs.background else t.bg,
        fg = if (app) cs.onBackground else t.fg,
        accent = if (app) cs.primary else t.accent,
        card = if (app) cs.surfaceContainerLow else t.card,
        dark = if (app) isSystemInDarkTheme() else t.dark,
        scale = ReadPrefs.scale, line = ReadPrefs.line,
        family = if (ReadPrefs.font == "sans") FontFamily.Default else Amiri,
    )
}

/**
 * Wraps a reading screen: recolors Material components to the reading theme, scales text,
 * keeps the screen on, and draws the warm / dim overlays on top (they don't block touches).
 */
@Composable
fun ReadingTheme(content: @Composable () -> Unit) {
    val st = rememberReadStyle()
    val base = MaterialTheme.colorScheme
    val t = ReadPrefs.current()
    val cs = if (t.id == "app") base else {
        val mix = { a: Color, b: Color, f: Float -> Color(a.red + (b.red - a.red) * f, a.green + (b.green - a.green) * f, a.blue + (b.blue - a.blue) * f, 1f) }
        val c = if (t.dark) darkColorScheme() else lightColorScheme()
        c.copy(
            primary = t.accent, onPrimary = if (t.dark) Color.Black else Color.White,
            primaryContainer = mix(t.bg, t.accent, 0.18f), onPrimaryContainer = t.fg,
            secondaryContainer = mix(t.bg, t.accent, 0.12f), onSecondaryContainer = t.fg,
            tertiary = t.accent, tertiaryContainer = mix(t.bg, t.accent, 0.22f), onTertiaryContainer = t.fg,
            background = t.bg, onBackground = t.fg, surface = t.bg, onSurface = t.fg,
            surfaceVariant = t.card, onSurfaceVariant = mix(t.fg, t.bg, 0.25f),
            surfaceContainerLowest = t.bg, surfaceContainerLow = t.card, surfaceContainer = t.card,
            surfaceContainerHigh = mix(t.card, t.fg, 0.06f), surfaceContainerHighest = mix(t.card, t.fg, 0.1f),
            outline = mix(t.fg, t.bg, 0.45f), outlineVariant = mix(t.fg, t.bg, 0.8f),
        )
    }
    val ty = MaterialTheme.typography
    fun TextStyle.sc(): TextStyle = copy(fontSize = fontSize * st.scale, lineHeight = (fontSize.value * st.scale * st.line).sp)
    val type = ty.copy(
        bodyLarge = ty.bodyLarge.sc().copy(fontFamily = st.family), bodyMedium = ty.bodyMedium.sc().copy(fontFamily = st.family), bodySmall = ty.bodySmall.sc(),
        titleMedium = ty.titleMedium.sc(), titleSmall = ty.titleSmall.sc(), labelLarge = ty.labelLarge.sc(),
    )
    // keep the screen on while reading
    val view = LocalView.current
    val keep = ReadPrefs.keepOn
    DisposableEffect(keep) { view.keepScreenOn = keep; onDispose { view.keepScreenOn = false } }
    MaterialTheme(colorScheme = cs, typography = type, shapes = MaterialTheme.shapes) {
        Box(Modifier.fillMaxSize()) {
            content()
            val warm = ReadPrefs.warm
            val dim = ReadPrefs.dim
            if (warm > 0f) Box(Modifier.matchParentSize().background(Color(0xFFFF8F00).copy(alpha = warm)))
            if (dim > 0f) Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = dim)))
        }
    }
}

/** Top-bar button that opens the reading settings. */
@Composable
fun ReadingSettingsButton(extra: (@Composable ColumnScope.() -> Unit)? = null) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) { Icon(Icons.Default.TextFields, "إعدادات القراءة") }
    if (open) ReadingSheet({ open = false }, extra)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadingSheet(onDismiss: () -> Unit, extra: (@Composable ColumnScope.() -> Unit)? = null) {
    ReadPrefs.version.intValue
    val st = rememberReadStyle()
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().heightIn(max = 640.dp).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 4.dp)) {
            Text("راحة العين والقراءة", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(Modifier.height(10.dp))
            // live preview
            Surface(shape = RoundedCornerShape(14.dp), color = st.bg, modifier = Modifier.fillMaxWidth().border(1.dp, st.accent.copy(alpha = 0.4f), RoundedCornerShape(14.dp))) {
                Box {
                    Text(
                        "الحمد لله الذي بنعمته تتم الصالحات — هكذا سيظهر النص.",
                        Modifier.padding(14.dp), color = st.fg, fontFamily = st.family, fontSize = st.size(17f), lineHeight = st.lineH(17f),
                    )
                    if (ReadPrefs.warm > 0f) Box(Modifier.matchParentSize().background(Color(0xFFFF8F00).copy(alpha = ReadPrefs.warm)))
                    if (ReadPrefs.dim > 0f) Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = ReadPrefs.dim)))
                }
            }
            Spacer(Modifier.height(14.dp))
            Text("لون الصفحة", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                ReadPrefs.themes.forEach { t ->
                    val sel = ReadPrefs.theme == t.id
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f).clickable { ReadPrefs.theme = t.id }) {
                        val bg = if (t.id == "app") MaterialTheme.colorScheme.surfaceVariant else t.bg
                        val fg = if (t.id == "app") MaterialTheme.colorScheme.onSurface else t.fg
                        Box(
                            Modifier.size(40.dp).clip(CircleShape).background(bg)
                                .border(if (sel) 3.dp else 1.dp, if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) { if (sel) Icon(Icons.Default.Check, null, tint = fg) else Text("أ", color = fg, fontFamily = Amiri) }
                        Text(t.name, fontSize = 11.sp, maxLines = 1)
                    }
                }
            }
            SettingSwitch("ليلي تلقائي من ٨ بالليل لـ ٥ الفجر", ReadPrefs.autoNight) { ReadPrefs.autoNight = it }
            Spacer(Modifier.height(8.dp))
            SliderRow("حجم الخط", ReadPrefs.scale, 0.85f..1.8f, "${(ReadPrefs.scale * 100).toInt()}%") { ReadPrefs.scale = it }
            SliderRow("المسافة بين السطور", ReadPrefs.line, 1.3f..2.4f, String.format(java.util.Locale.US, "%.1f", ReadPrefs.line)) { ReadPrefs.line = it }
            SliderRow("فلتر دافئ (يقلل الضوء الأزرق)", ReadPrefs.warm, 0f..0.4f, "${(ReadPrefs.warm / 0.4f * 100).toInt()}%") { ReadPrefs.warm = it }
            SliderRow("تعتيم إضافي (للقراءة بالليل)", ReadPrefs.dim, 0f..0.6f, "${(ReadPrefs.dim / 0.6f * 100).toInt()}%") { ReadPrefs.dim = it }
            Text("الخط", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(ReadPrefs.font == "amiri", { ReadPrefs.font = "amiri" }, label = { Text("أميري (نسخ كلاسيكي)", fontFamily = Amiri) })
                FilterChip(ReadPrefs.font == "sans", { ReadPrefs.font = "sans" }, label = { Text("عادي") })
            }
            SettingSwitch("الشاشة ما تطفيش وأنا بقرأ", ReadPrefs.keepOn) { ReadPrefs.keepOn = it }
            if (extra != null) { HorizontalDivider(Modifier.padding(vertical = 10.dp)); extra() }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    ReadPrefs.theme = "sepia"; ReadPrefs.warm = 0.12f; ReadPrefs.line = 2f; ReadPrefs.scale = 1.1f; ReadPrefs.keepOn = true
                }) { Text("وضع القراءة الطويلة") }
                TextButton(onClick = { ReadPrefs.reset() }) { Text("رجّع الافتراضي") }
            }
            Text(
                "نصيحة للقراءة الطويلة: كل ٢٠ دقيقة بص على حاجة بعيدة ٢٠ ثانية، وخلي الإضاءة حواليك قريبة من إضاءة الشاشة.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(vertical = 8.dp),
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SliderRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, shown: String, onChange: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Text(shown, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
        Slider(value, onChange, valueRange = range)
    }
}

@Composable
private fun SettingSwitch(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!value) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(value, onChange)
    }
}

/** Reading-screen immersive toggle: hides the system bars while reading. */
@Composable
fun ImmersiveEffect(on: Boolean) {
    val ctx = LocalContext.current
    DisposableEffect(on) {
        val w = (ctx as? Activity)?.window
        if (w != null) {
            val c = androidx.core.view.WindowCompat.getInsetsController(w, w.decorView)
            if (on) {
                c.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                c.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            } else c.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }
        onDispose { w?.let { androidx.core.view.WindowCompat.getInsetsController(it, it.decorView).show(androidx.core.view.WindowInsetsCompat.Type.systemBars()) } }
    }
}
