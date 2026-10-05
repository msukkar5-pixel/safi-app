package com.mohamed.safi

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import com.mohamed.safi.ui.Text
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.mohamed.safi.data.Fx
import com.mohamed.safi.ui.*
import com.mohamed.safi.ui.screens.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : FragmentActivity() {

    private var unlocked = mutableStateOf(false)
    private val splash = mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        unlocked.value = !SafiApp.prefs.lockOn || (savedInstanceState?.getBoolean("unlocked") == true)
        splash.value = savedInstanceState == null
        handleIntent(intent)
        runCatching { com.mohamed.safi.faith.FaithAlerts.scheduleAll(this) }
        SafiApp.scope.launch { runCatching { com.mohamed.safi.faith.FaithAlerts.migrateOld(this@MainActivity) } }
        setContent {
            SafiTheme {
                CompositionLocalProvider(LocalLayoutDirection provides I18n.direction) {
                    // the app stays composed underneath, so returning from the camera / a picker never loses state
                    Box(Modifier.fillMaxSize()) {
                        AppRoot()
                        if (!unlocked.value && !splash.value) LockScreen { authenticate() }
                        if (splash.value) DedicationSplash {
                            splash.value = false
                            if (!unlocked.value) authenticate()
                        }
                    }
                }
            }
        }
        if (!unlocked.value && !splash.value) authenticate()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("unlocked", unlocked.value)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        intent.getStringExtra("route")?.let {
            if (it == "voice") {
                UiBus.listenNow.value = true
                UiBus.pendingRoute.value = "assistant"
            } else if (it.startsWith("azkar:")) {
                UiBus.pendingAzkar.value = it.substringAfter(':')
                UiBus.pendingRoute.value = "azkar"
            } else UiBus.pendingRoute.value = it
        }
        if (intent.action == Intent.ACTION_SEND) {
            intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() }?.let { UiBus.pendingShare.value = it }
        }
    }

    override fun onStop() {
        super.onStop()
        runCatching { com.mohamed.safi.widget.SafiWidget.updateAll(this) }
        if (!isChangingConfigurations) stoppedAt = System.currentTimeMillis()
    }

    private var stoppedAt = 0L

    override fun onStart() {
        super.onStart()
        val away = if (stoppedAt > 0) System.currentTimeMillis() - stoppedAt else 0L
        if (away > 5 * 60_000) splash.value = true
        // re-lock only after a real absence (not a quick trip to the camera or a file picker)
        if (SafiApp.prefs.lockOn && away > 2 * 60_000) unlocked.value = false
        if (!unlocked.value && SafiApp.prefs.lockOn && !splash.value) authenticate()
    }

    private var prompting = false

    private fun authenticate() {
        if (prompting) return
        val authenticators = if (Build.VERSION.SDK_INT >= 30) {
            BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        } else {
            BiometricManager.Authenticators.BIOMETRIC_WEAK
        }
        if (BiometricManager.from(this).canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            unlocked.value = true
            return
        }
        prompting = true
        val prompt = BiometricPrompt(
            this, ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    prompting = false
                    unlocked.value = true
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    prompting = false
                }
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("${com.mohamed.safi.AppName.v}")
            .setSubtitle("افتح بالبصمة")
            .setAllowedAuthenticators(authenticators)
            .apply { if (Build.VERSION.SDK_INT < 30) setNegativeButtonText("إلغاء") }
            .build()
        prompt.authenticate(info)
    }
}

@Composable
private fun LockScreen(onUnlock: () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(Icons.Default.Lock, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(16.dp))
            Text("${com.mohamed.safi.AppName.v} مقفول", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onUnlock) {
                Icon(Icons.Default.Fingerprint, null)
                Spacer(Modifier.width(8.dp))
                Text("افتح")
            }
        }
    }
}

private data class Tab(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val tabs = listOf(
    Tab("home", "الرئيسية", Icons.Default.Home),
    Tab("finance", "الحسابات", Icons.Default.AccountBalanceWallet),
    Tab("assistant", "${com.mohamed.safi.AppName.v}", Icons.Default.Mic),
    Tab("schedule", "المواعيد", Icons.Default.Event),
    Tab("more", "المزيد", Icons.Default.GridView),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppRoot() {
    CrashReportDialog()
    val nav = rememberNavController()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val entry by nav.currentBackStackEntryAsState()
    val current = entry?.destination?.route
    val pendingRoute by UiBus.pendingRoute.collectAsState()

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            if (System.currentTimeMillis() - SafiApp.prefs.rateUpdated > 6 * 3_600_000L) Fx.refresh()
        }
        if (SafiApp.prefs.locationOn) runCatching { com.mohamed.safi.location.LocationService.start(ctx) }
        runCatching { com.mohamed.safi.data.Carpool.schedule(ctx) }
        runCatching { com.mohamed.safi.faith.Prayer.schedule(ctx) }
        runCatching { com.mohamed.safi.widget.SafiWidget.updateAll(ctx) }
    }
    val pendingShare by UiBus.pendingShare.collectAsState()
    LaunchedEffect(pendingShare) {
        val text = pendingShare ?: return@LaunchedEffect
        UiBus.pendingShare.value = null
        val res = withContext(Dispatchers.IO) { com.mohamed.safi.sms.SmsProcessor.processText(ctx, text) }
        if (res.added.isNotEmpty()) {
            val e = res.added.first()
            toast(
                ctx,
                if (res.added.size == 1) "اتسجل: ${com.mohamed.safi.data.money(e.amount, e.currency)} — ${e.category}"
                else "اتسجل ${res.added.size} عملية" + if (res.skipped > 0) " (${res.skipped} مش عمليات)" else "",
            )
            runCatching { go(nav, "finance") }
        } else if (res.duplicates > 0) {
            toast(ctx, "العمليات دي متسجلة قبل كده")
        } else if (com.mohamed.safi.ai.Claude.hasKey) {
            UiBus.pendingVoice.value = text
            runCatching { go(nav, "assistant") }
        } else {
            toast(ctx, "مقدرتش ألاقي مبلغ في الرسالة دي")
        }
    }
    LaunchedEffect(pendingRoute) {
        pendingRoute?.let { r ->
            UiBus.pendingRoute.value = null
            runCatching { go(nav, r) }
        }
    }

    Scaffold(
        bottomBar = {
            if (current in tabs.map { it.route }) {
                NavigationBar {
                    tabs.forEach { t ->
                        NavigationBarItem(
                            selected = current == t.route,
                            onClick = { go(nav, t.route) },
                            icon = { Icon(t.icon, t.label) },
                            label = { Text(t.label) },
                        )
                    }
                }
            }
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { pad ->
        val back: () -> Unit = { nav.popBackStack() }
        val open: (String) -> Unit = { nav.navigate(it) }
        NavHost(nav, startDestination = if (SafiApp.prefs.onboarded) "home" else "welcome", modifier = Modifier.padding(pad).consumeWindowInsets(pad)) {
            composable("welcome") { WelcomeScreen { nav.navigate("home") { popUpTo("welcome") { inclusive = true } } } }
            composable("home") { HomeScreen(open) }
            composable("expenses") { ExpensesScreen() }
            composable("finance") { FinanceScreen(null, open) }
            composable("vehicle") { VehicleScreen(back, open) }
            composable("alerts") { AlertsScreen(back) }
            composable("vitals") { VitalsScreen(back) }
            composable("prayertracker") { PrayerTrackerScreen(back) }
            composable("islamiccalendar") { IslamicCalendarScreen(back) }
            composable("asmahusna") { AsmaHusnaScreen(back) }
            composable("assistant") { AssistantScreen() }
            composable("schedule") { ScheduleScreen() }
            composable("more") { MoreScreen(open) }
            composable("transfers") { TransfersScreen(back) }
            composable("bills") { BillsScreen(back) }
            composable("debts") { DebtsScreen(back) }
            composable("car") { CarScreen(back) }
            composable("places") { PlacesScreen(back) }
            composable("reports") { ReportsScreen(back) }
            composable("settings") { SettingsScreen(back) }
            composable("carpool") { CarpoolScreen(back) }
            composable("fitness") { FitnessScreen(back) }
            composable("quran") { QuranScreen(back) }
            composable("prayer") { PrayerScreen(back) }
            composable("documents") { DocumentsScreen(back) }
            composable("savings") { SavingsScreen(back) }
            composable("lessons") { LessonsScreen(back) }
            composable("zakat") { ZakatScreen(back) }
            composable("azkar") { AzkarScreen(back) }
            composable("hadith") { HadithScreen(back) }
            composable("diary") { DiaryScreen(back) }
            composable("tafsir") { QuranScreen(back) }
            composable("shaarawy") { ShaarawyScreen(back) }
            composable("healthrecords") { HealthRecordsScreen(back) }
            composable("wird") { WirdScreen(back) }
            composable("stories") { StoriesScreen(back, open) }
            composable("bidaya") { BidayaScreen(back) }
            composable("quranaudio") { QuranAudioScreen(back) }
            composable("library") { LibraryScreen(back, { nav.navigate("book/$it") }, open) }
            composable("book/{id}") { e ->
                val id = e.arguments?.getString("id") ?: "bidaya"
                val q = remember { UiBus.pendingBook.value?.takeIf { it.first == id }?.second ?: "" }
                LaunchedEffect(Unit) { UiBus.pendingBook.value = null }
                BookScreen(id, back, q)
            }
            composable("history") { HistoryScreen(back, open) }
            composable("audiobooks") { AudiobooksScreen(back) }
        }
    }
}

private fun go(nav: NavHostController, route: String) {
    if (route in tabs.map { it.route }) {
        nav.navigate(route) {
            popUpTo("home") { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    } else {
        nav.navigate(route) { launchSingleTop = true }
    }
}

@Composable
private fun CrashReportDialog() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var report by remember { mutableStateOf(CrashLog.pending(ctx)) }
    val r = report ?: return
    AlertDialog(
        onDismissRequest = { CrashLog.clear(ctx); report = null },
        title = { Text("التطبيق قفل المرة اللي فاتت") },
        text = {
            Column {
                Text("ابعتلي التقرير ده (واتساب أو انسخه والصقه في المحادثة مع Claude) علشان أصلّح السبب بالظبط.")
                Spacer(Modifier.height(8.dp))
                Text(r.take(600), style = MaterialTheme.typography.bodySmall, maxLines = 10)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, r)
                ctx.startActivity(Intent.createChooser(i, "ابعت تقرير القفلة").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                CrashLog.clear(ctx); report = null
            }) { Text("ابعت التقرير") }
        },
        dismissButton = {
            TextButton(onClick = {
                (ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager)
                    .setPrimaryClip(android.content.ClipData.newPlainText("crash", r))
                toast(ctx, "اتنسخ")
                CrashLog.clear(ctx); report = null
            }) { Text("انسخ") }
        },
    )
}
