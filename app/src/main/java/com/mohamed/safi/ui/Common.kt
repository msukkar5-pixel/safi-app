package com.mohamed.safi.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import com.mohamed.safi.ui.Text
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mohamed.safi.data.Cats
import com.mohamed.safi.data.millis
import com.mohamed.safi.data.monthName
import com.mohamed.safi.data.toLdt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.YearMonth

fun CoroutineScope.io(block: suspend CoroutineScope.() -> Unit) = launch(Dispatchers.IO, block = block)

fun catIcon(cat: String): ImageVector = when (cat) {
    Cats.FOOD -> Icons.Default.Restaurant
    Cats.GROCERY -> Icons.Default.ShoppingCart
    Cats.FUEL -> Icons.Default.LocalGasStation
    Cats.CAR -> Icons.Default.Build
    Cats.TRANSPORT -> Icons.Default.DirectionsCar
    Cats.RENT -> Icons.Default.Home
    Cats.UTILITIES -> Icons.Default.Bolt
    Cats.TELECOM -> Icons.Default.Wifi
    Cats.HEALTH -> Icons.Default.LocalPharmacy
    Cats.CLOTHES -> Icons.Default.ShoppingBag
    Cats.ONLINE -> Icons.Default.CreditCard
    Cats.FUN -> Icons.Default.Movie
    Cats.EDU -> Icons.Default.School
    Cats.CASH -> Icons.Default.LocalAtm
    Cats.FEES -> Icons.Default.AccountBalance
    Cats.TRANSFER -> Icons.Default.SwapHoriz
    Cats.INCOME -> Icons.Default.Savings
    else -> Icons.Default.Category
}

private val palette = listOf(
    Color(0xFF0F6E5C), Color(0xFF2E7DBA), Color(0xFFD98A1C), Color(0xFF8E5BB8), Color(0xFFC6423A),
    Color(0xFF3D9970), Color(0xFF6C7A89), Color(0xFFB5651D), Color(0xFF1B998B), Color(0xFFE07A5F),
    Color(0xFF5C6BC0), Color(0xFF9C6644), Color(0xFF00897B), Color(0xFFAD1457), Color(0xFF546E7A),
    Color(0xFF7CB342), Color(0xFF8D6E63), Color(0xFF26A69A),
)

fun catColor(cat: String): Color {
    val i = Cats.all.indexOf(cat)
    return if (i >= 0) palette[i % palette.size] else palette[(cat.hashCode() and 0x7fffffff) % palette.size]
}

@Composable
fun CatBadge(cat: String, size: Int = 40, icon: ImageVector = catIcon(cat), color: Color = catColor(cat)) {
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(color.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size((size * 0.55).dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    fab: @Composable () -> Unit = {},
    showTopBar: Boolean = true,
    content: @Composable (PaddingValues) -> Unit,
) {
    if (onBack != null) androidx.activity.compose.BackHandler(onBack = onBack)
    Scaffold(
        topBar = {
            if (showTopBar) TopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    if (onBack != null) IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع")
                    }
                },
                actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = fab,
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        content = content,
    )
}

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    color: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    content: @Composable ColumnScope.() -> Unit,
) {
    val m = modifier.fillMaxWidth()
    if (onClick != null) {
        Card(
            onClick = onClick, modifier = m, shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = color),
        ) { Column(Modifier.padding(16.dp), content = content) }
    } else {
        Card(
            modifier = m, shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = color),
        ) { Column(Modifier.padding(16.dp), content = content) }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().padding(top = 18.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        action?.invoke()
    }
}

@Composable
fun Pill(text: String, color: Color = MaterialTheme.colorScheme.primary) {
    Text(
        text,
        color = color,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@Composable
fun EmptyState(icon: ImageVector, text: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(10.dp))
        Text(text, color = MaterialTheme.colorScheme.outline, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun MonthSwitcher(ym: YearMonth, onChange: (YearMonth) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        // RTL: the "previous" chevron sits on the right
        IconButton(onClick = { onChange(ym.minusMonths(1)) }) { Icon(Icons.Default.ChevronRight, "الشهر اللي فات") }
        Text(monthName(ym), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        IconButton(onClick = { onChange(ym.plusMonths(1)) }, enabled = ym < YearMonth.now()) {
            Icon(Icons.Default.ChevronLeft, "الشهر الجاي")
        }
    }
}

@Composable
fun ChoiceField(
    label: String,
    value: String,
    options: List<String>,
    modifier: Modifier = Modifier,
    display: (String) -> String = { it },
    onChange: (String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedTextField(
            value = display(value),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Box(Modifier.matchParentSize().clickable { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { o ->
                DropdownMenuItem(text = { Text(display(o)) }, onClick = { onChange(o); open = false })
            }
        }
    }
}

@Composable
fun NumberField(label: String, value: String, modifier: Modifier = Modifier, suffix: String? = null, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onChange(v.replace('٫', '.').filter { it.isDigit() || it == '.' }.let(::toWesternDigits)) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        suffix = { if (suffix != null) Text(suffix) },
        modifier = modifier.fillMaxWidth(),
    )
}

fun toWesternDigits(s: String): String = s.map { c ->
    when (c) {
        in '٠'..'٩' -> '0' + (c - '٠')
        in '۰'..'۹' -> '0' + (c - '۰')
        else -> c
    }
}.joinToString("")

@Composable
fun DateField(label: String, millis: Long?, modifier: Modifier = Modifier, withTime: Boolean = true, onPick: (Long) -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val text = millis?.let { if (withTime) com.mohamed.safi.data.dateTimeStr(it) else com.mohamed.safi.data.shortDate(it) } ?: "اختار"
    Box(modifier) {
        OutlinedTextField(
            value = text, onValueChange = {}, readOnly = true, label = { Text(label) },
            trailingIcon = { Icon(Icons.Default.CalendarMonth, null) },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
        )
        Box(
            Modifier.matchParentSize().clickable {
                if (withTime) pickDateTime(ctx, millis ?: System.currentTimeMillis(), onPick)
                else pickDate(ctx, millis ?: System.currentTimeMillis(), onPick)
            },
        )
    }
}

fun pickDate(ctx: Context, initial: Long, onPicked: (Long) -> Unit) {
    val d = initial.toLdt()
    DatePickerDialog(ctx, { _, y, m, day ->
        onPicked(LocalDateTime.of(y, m + 1, day, d.hour, d.minute).millis())
    }, d.year, d.monthValue - 1, d.dayOfMonth).show()
}

fun pickTime(ctx: Context, initial: Long, onPicked: (Int, Int) -> Unit) {
    val d = initial.toLdt()
    TimePickerDialog(ctx, { _, h, min -> onPicked(h, min) }, d.hour, d.minute, false).show()
}

fun pickDateTime(ctx: Context, initial: Long, onPicked: (Long) -> Unit) {
    pickDate(ctx, initial) { dayMillis ->
        pickTime(ctx, initial) { h, m ->
            onPicked(dayMillis.toLdt().withHour(h).withMinute(m).withSecond(0).withNano(0).millis())
        }
    }
}

@Composable
fun <T> ChipsRow(options: List<T>, selected: T?, label: (T) -> String, onSelect: (T) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
        items(options) { o ->
            FilterChip(selected = o == selected, onClick = { onSelect(o) }, label = { Text(label(o)) })
        }
    }
}

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String = "تأكيد", onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}

@Composable
fun StatBlock(label: String, value: String, modifier: Modifier = Modifier, sub: String? = null, valueColor: Color = MaterialTheme.colorScheme.onSurface) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = valueColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
    }
}

/** Horizontal bar used in reports and budgets. */
@Composable
fun BarRow(label: String, value: String, fraction: Float, color: Color, icon: ImageVector? = null, sub: String? = null, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().let { if (onClick != null) it.clickable(onClick = onClick) else it }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            CatBadge(label, 34, icon, color)
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { fraction.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = color,
                trackColor = color.copy(alpha = 0.12f),
            )
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        }
    }
}

/** Calm card with a thin gold frame, for spiritual content. */
@Composable
fun GoldCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val border = androidx.compose.foundation.BorderStroke(1.dp, Gold.copy(alpha = 0.55f))
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    if (onClick != null) Card(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, colors = colors, border = border) {
        Column(Modifier.padding(18.dp), content = content)
    } else Card(modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, colors = colors, border = border) {
        Column(Modifier.padding(18.dp), content = content)
    }
}
