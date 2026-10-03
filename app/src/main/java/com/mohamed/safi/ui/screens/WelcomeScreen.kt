package com.mohamed.safi.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.mohamed.safi.SafiApp
import com.mohamed.safi.location.LocationService
import com.mohamed.safi.notify.ReminderScheduler
import com.mohamed.safi.sms.SmsProcessor
import com.mohamed.safi.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

fun granted(ctx: Context, p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED

fun ignoringBattery(ctx: Context): Boolean =
    ctx.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(ctx.packageName) == true

data class PermState(
    val notifications: Boolean,
    val sms: Boolean,
    val location: Boolean,
    val bgLocation: Boolean,
    val exact: Boolean,
    val battery: Boolean,
) {
    val essentialsOk get() = notifications && sms && exact
}

fun permState(ctx: Context) = PermState(
    notifications = Build.VERSION.SDK_INT < 33 || granted(ctx, Manifest.permission.POST_NOTIFICATIONS),
    sms = granted(ctx, Manifest.permission.RECEIVE_SMS) && granted(ctx, Manifest.permission.READ_SMS),
    location = granted(ctx, Manifest.permission.ACCESS_FINE_LOCATION),
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

@SuppressLint("BatteryLife")
@Composable
fun PermissionsList(onChanged: () -> Unit = {}) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val st by rememberPermState()
    var refresh by remember { mutableIntStateOf(0) }
    val state = remember(st, refresh) { permState(ctx) }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++; onChanged() }
    val smsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        refresh++
        onChanged()
        if (res.values.all { it }) {
            scope.launch(Dispatchers.IO) { SmsProcessor.importInbox(ctx, 90) }
            toast(ctx, "بقرا رسايل البنك آخر 3 شهور…")
        }
    }
    val locLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refresh++; onChanged() }
    val bgLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++; onChanged() }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PermRow(Icons.Default.Notifications, "الإشعارات", "للتذكيرات والفواتير وملخص الصبح", state.notifications) {
            if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        PermRow(Icons.Default.Sms, "رسايل البنك", "يسجل مصاريف البطاقة لوحده من رسايل Emirates NBD وADCB وADIB", state.sms) {
            smsLauncher.launch(arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS))
        }
        PermRow(Icons.Default.Alarm, "المنبهات في ميعادها بالظبط", "علشان التذكير ميتأخرش", state.exact) {
            if (Build.VERSION.SDK_INT >= 31) {
                runCatching {
                    ctx.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + ctx.packageName)))
                }
            }
        }
        PermRow(Icons.Default.MyLocation, "الموقع", "يربط كل مصروف بمكانه ويسجل الأماكن اللي بتروحها", state.location) {
            locLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
        if (state.location && Build.VERSION.SDK_INT >= 29) {
            PermRow(Icons.Default.Place, "الموقع طول الوقت", "اختار \"السماح طوال الوقت\" علشان يسجل وهو مقفول", state.bgLocation) {
                bgLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
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
fun WelcomeScreen(onDone: () -> Unit) {
    val prefs = SafiApp.prefs
    val ctx = LocalContext.current
    var name by remember { mutableStateOf(prefs.userName) }
    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp),
    ) {
        Text("أهلاً بيك في صافي 👋", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(
            "مساعدك الشخصي: مصاريفك من رسايل البنك، تحويلات مصر، الفواتير، السلف، المواعيد، المنبهات، العربية والأماكن. كله على تليفونك انت بس.",
            color = MaterialTheme.colorScheme.outline,
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(name, { name = it }, label = { Text("اسمك") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        SectionTitle("الصلاحيات")
        PermissionsList()
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = {
                prefs.userName = name.trim().ifBlank { "محمد" }
                prefs.onboarded = true
                if (granted(ctx, Manifest.permission.ACCESS_FINE_LOCATION)) {
                    prefs.locationOn = true
                    runCatching { LocationService.start(ctx) }
                }
                onDone()
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text("يلا نبدأ", fontWeight = FontWeight.Bold) }
        Spacer(Modifier.height(8.dp))
        Text(
            "تقدر تغير أي حاجة بعدين من الإعدادات. علشان المساعد الذكي وقراءة الفواتير ضيف مفتاح Claude API من الإعدادات.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline,
        )
    }
}
