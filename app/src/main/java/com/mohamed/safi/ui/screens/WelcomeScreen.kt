package com.mohamed.safi.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import com.mohamed.safi.ui.Text
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.mohamed.safi.SafiApp
import com.mohamed.safi.location.LocationService
import com.mohamed.safi.notify.ReminderScheduler
import com.mohamed.safi.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

fun granted(ctx: Context, p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED

fun ignoringBattery(ctx: Context): Boolean =
    ctx.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(ctx.packageName) == true

data class PermState(
    val notifications: Boolean,
    val location: Boolean,
    val bgLocation: Boolean,
    val exact: Boolean,
    val battery: Boolean,
) {
    val essentialsOk get() = notifications && exact
}

fun permState(ctx: Context) = PermState(
    notifications = Build.VERSION.SDK_INT < 33 || granted(ctx, Manifest.permission.POST_NOTIFICATIONS),
    // Approximate (coarse) location is enough for logging places.
    location = granted(ctx, Manifest.permission.ACCESS_FINE_LOCATION) || granted(ctx, Manifest.permission.ACCESS_COARSE_LOCATION),
    bgLocation = Build.VERSION.SDK_INT < 29 || granted(ctx, Manifest.permission.ACCESS_BACKGROUND_LOCATION),
    exact = ReminderScheduler.canExact(ctx),
    battery = ignoringBattery(ctx),
)

@Composable
fun rememberPermState(): State<PermState> {
    val ctx = LocalContext.current
    val state = remember { mutableStateOf(permState(ctx)) }
    LifecycleResumeEffect(Unit) {
        state.value = permState(ctx)
        onPauseOrDispose { }
    }
    return state
}

fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

fun openAppSettings(ctx: Context) {
    runCatching {
        ctx.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

private fun askedPrefs(ctx: Context) = ctx.applicationContext.getSharedPreferences("safi_perm_asked", Context.MODE_PRIVATE)

/** False once Android stopped showing the dialog (denied twice / "don't ask again"). */
fun canStillAsk(ctx: Context, perms: Array<String>): Boolean {
    val sp = askedPrefs(ctx)
    if (perms.none { sp.getBoolean(it, false) }) return true
    val act = ctx.findActivity() ?: return true
    return perms.any { ActivityCompat.shouldShowRequestPermissionRationale(act, it) }
}

/**
 * Returns a click handler that requests [perms], or opens the app's settings page
 * when the system won't show the dialog any more. [onResult] gets true if any was granted.
 */
@Composable
fun rememberPermRequester(perms: Array<String>, onResult: (Boolean) -> Unit = {}): () -> Unit {
    val ctx = LocalContext.current
    val latest by rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        latest(res.values.any { it } || perms.any { granted(ctx, it) })
    }
    return remember(perms.joinToString()) {
        {
            when {
                perms.any { granted(ctx, it) } -> latest(true)
                !canStillAsk(ctx, perms) -> openAppSettings(ctx)
                else -> {
                    askedPrefs(ctx).edit().apply { perms.forEach { putBoolean(it, true) } }.apply()
                    launcher.launch(perms)
                }
            }
        }
    }
}

val LOCATION_PERMS = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

@SuppressLint("BatteryLife")
@Composable
fun PermissionsList(onChanged: () -> Unit = {}) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val st by rememberPermState()
    var refresh by remember { mutableIntStateOf(0) }
    val state = remember(st, refresh) { permState(ctx) }

    val askNotif = if (Build.VERSION.SDK_INT >= 33) {
        rememberPermRequester(arrayOf(Manifest.permission.POST_NOTIFICATIONS)) { refresh++; onChanged() }
    } else null
    val askLoc = rememberPermRequester(LOCATION_PERMS) { refresh++; onChanged() }
    val askBg = if (Build.VERSION.SDK_INT >= 29) {
        rememberPermRequester(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) { refresh++; onChanged() }
    } else null

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PermRow(Icons.Default.Notifications, "الإشعارات", "للتذكيرات والفواتير وملخص الصبح", state.notifications) {
            askNotif?.invoke()
        }
        PermRow(Icons.Default.Alarm, "المنبهات في ميعادها بالظبط", "علشان التذكير ميتأخرش", state.exact) {
            if (Build.VERSION.SDK_INT >= 31) {
                runCatching {
                    ctx.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + ctx.packageName)))
                }
            }
        }
        PermRow(Icons.Default.MyLocation, "الموقع", "يربط كل مصروف بمكانه ويسجل الأماكن اللي بتروحها", state.location) {
            askLoc()
        }
        if (state.location && Build.VERSION.SDK_INT >= 29) {
            PermRow(Icons.Default.Place, "الموقع طول الوقت", "اختار \"السماح طوال الوقت\" علشان يسجل وهو مقفول", state.bgLocation) {
                askBg?.invoke()
            }
        }
        PermRow(Icons.Default.Bolt, "يشتغل في الخلفية", "علشان أندرويد ميقفلوش وتوصلك التنبيهات", state.battery) {
            runCatching {
                ctx.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + ctx.packageName)))
            }
        }
    }
}

@Composable
fun PermRow(icon: ImageVector, title: String, desc: String, ok: Boolean, onGrant: () -> Unit) {
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CatBadge(title, 38, icon, if (ok) Positive else Warn)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
            if (ok) Icon(Icons.Default.CheckCircle, null, tint = Positive)
            else FilledTonalButton(onClick = onGrant) { Text("اسمح") }
        }
    }
}

@Composable
fun WelcomeScreen(onKid: () -> Unit = {}, onDone: () -> Unit) {
    val prefs = SafiApp.prefs
    val ctx = LocalContext.current
    var name by remember { mutableStateOf(prefs.userName) }
    var appName by remember { mutableStateOf(prefs.appName) }
    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp),
    ) {
        Text("أهلاً بيك في ${com.mohamed.safi.AppName.v} 👋", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(
            "مساعدك الشخصي: مصاريفك، تحويلات مصر، الفواتير، السلف، المواعيد، المنبهات، العربية والأماكن. كله على تليفونك انت بس.\n\nرسايل البنك: من تطبيق الرسايل دوس مطوّل على الرسالة ← مشاركة ← ${com.mohamed.safi.AppName.v}، وهتتسجل لوحدها.",
            color = MaterialTheme.colorScheme.outline,
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(name, { name = it }, label = { Text("اسمك") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(appName, { appName = it }, label = { Text("سمّي مساعدك (اختياري)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        SectionTitle("الصلاحيات")
        PermissionsList()
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = {
                prefs.userName = name.trim()
                prefs.appName = appName.trim().ifBlank { "أثر" }
                prefs.onboarded = true
                if (LocationService.hasPermission(ctx)) {
                    prefs.locationOn = true
                    runCatching { LocationService.start(ctx) }
                }
                onDone()
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text("يلا نبدأ", fontWeight = FontWeight.Bold) }
        OutlinedButton(onClick = onKid, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("👧 ده موبايل طفل؟ جهّزه بأقسام تختارها") }
        Spacer(Modifier.height(8.dp))
        Text(
            "تقدر تغير أي حاجة بعدين من الإعدادات. علشان المساعد الذكي وقراءة الفواتير اربط أي ذكاء اصطناعي (Claude أو ChatGPT أو Gemini أو غيرهم) من الإعدادات.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline,
        )
    }
}
