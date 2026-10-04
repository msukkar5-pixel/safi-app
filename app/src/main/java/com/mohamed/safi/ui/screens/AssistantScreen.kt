package com.mohamed.safi.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mohamed.safi.SafiApp
import com.mohamed.safi.ai.Assistant
import com.mohamed.safi.ai.Claude
import com.mohamed.safi.data.ChatMsg
import com.mohamed.safi.data.timeStr
import com.mohamed.safi.ui.*
import kotlinx.coroutines.launch

private val suggestions = listOf(
    "مطلوب مني إيه الشهر ده؟",
    "صرفت 45 درهم كاش على الغدا",
    "حولت لماما 5000 جنيه",
    "فكرني بكرة الساعة 9 أكلم البنك",
    "استلفت 1000 درهم من أحمد هرجعهم آخر الشهر",
    "صرفت كام على البنزين الشهر ده؟",
    "صحيني كل يوم الساعة 6 ونص ما عدا الجمعة",
    "الإيجار 4500 درهم كل شهر يوم 1",
    "وديني دبي مول",
    "شغّل عمرو دياب على أنغامي",
    "مين اللي هيسوق بكرة؟",
    "فطرت 3 بيضات وتوست",
    "وزني النهارده 92.5",
)

@Composable
fun AssistantScreen() {
    val dao = SafiApp.db.dao()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val messages by dao.chat().collectAsState(emptyList())
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var handoff by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val pending by UiBus.pendingVoice.collectAsState()
    var tts by remember { mutableStateOf(com.mohamed.safi.apps.Apps.ttsOn(ctx)) }
    LaunchedEffect(tts) { if (tts) com.mohamed.safi.ai.Speaker.init(ctx) }
    val listenNow by UiBus.listenNow.collectAsState()

    fun send(text: String) {
        val t = text.trim()
        if (t.isEmpty() || busy) return
        if (!Claude.hasKey) {
            toast(ctx, "اربط ذكاء اصطناعي من الإعدادات الأول")
            return
        }
        input = ""
        busy = true
        scope.launch {
            val r = Assistant.ask(ctx, t)
            if (tts) com.mohamed.safi.ai.Speaker.say(r.reply)
            busy = false
        }
    }

    val voice = rememberVoiceInput { send(it) }
    val claudeVoice = rememberVoiceInput { ClaudeApp.ask(ctx, it) }
    LaunchedEffect(listenNow) {
        if (listenNow) {
            UiBus.listenNow.value = false
            voice()
        }
    }

    LaunchedEffect(pending) {
        pending?.let {
            UiBus.pendingVoice.value = null
            send(it)
        }
    }
    LaunchedEffect(messages.size, busy) {
        val count = messages.size + (if (busy) 1 else 0)
        if (count > 0) listState.animateScrollToItem(count - 1)
    }

    ScreenScaffold(
        "${com.mohamed.safi.AppName.v}",
        actions = {
            IconButton(onClick = { handoff = true }) { Icon(Icons.Default.OpenInNew, "اسأل في تطبيق اشتراكك") }
            IconButton(onClick = {
                tts = !tts
                com.mohamed.safi.apps.Apps.setTts(ctx, tts)
                if (!tts) com.mohamed.safi.ai.Speaker.stop()
                toast(ctx, if (tts) "${com.mohamed.safi.AppName.v} هيرد بالصوت" else "الرد بالصوت اتقفل")
            }) { Icon(if (tts) Icons.Default.VolumeUp else Icons.Default.VolumeOff, "الرد بالصوت") }
            if (messages.isNotEmpty()) IconButton(onClick = { confirmClear = true }) { Icon(Icons.Default.DeleteSweep, "مسح المحادثة") }
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).imePadding()) {
            ClaudeAppBar(onVoice = { claudeVoice() }, onText = { ClaudeApp.ask(ctx, input); input = "" }, hasText = input.isNotBlank())
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                state = listState,
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (messages.isEmpty()) {
                    item {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(top = 24.dp)) {
                            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(72.dp)) {
                                Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Mic, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary) }
                            }
                            Spacer(Modifier.height(12.dp))
                            Text("قولّي أي حاجة", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text(
                                "سجّل مصروف، حوّل، فكّرني، صحّيني، استلفت، أو اسألني عن فلوسك.",
                                color = MaterialTheme.colorScheme.outline, style = MaterialTheme.typography.bodyMedium,
                            )
                            if (!Claude.hasKey) {
                                Spacer(Modifier.height(10.dp))
                                Text("⚠️ اربط أي ذكاء اصطناعي من الإعدادات (Claude، ChatGPT، Gemini…)", color = Warn)
                            }
                            Spacer(Modifier.height(16.dp))
                            suggestions.forEach { s ->
                                SuggestionChip(onClick = { send(s) }, label = { Text(s) }, modifier = Modifier.padding(vertical = 2.dp))
                            }
                        }
                    }
                }
                items(messages, key = { it.id }) { m -> Bubble(m) }
                if (busy) item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("${com.mohamed.safi.AppName.v} بيفكر…", color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
            Surface(tonalElevation = 2.dp) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilledIconButton(onClick = { voice() }, modifier = Modifier.size(48.dp), enabled = !busy) {
                        Icon(Icons.Default.Mic, "اتكلم")
                    }
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        input, { input = it },
                        placeholder = { Text("اكتب أو اتكلم…") },
                        modifier = Modifier.weight(1f),
                        maxLines = 4,
                        shape = RoundedCornerShape(24.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = { send(input) }, enabled = input.isNotBlank() && !busy) {
                        Icon(Icons.AutoMirrored.Filled.Send, "ابعت", tint = if (input.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
                    }
                }
            }
        }
    }

    if (handoff) {
        val apps = listOf(
            "com.anthropic.claude" to "Claude",
            "com.openai.chatgpt" to "ChatGPT",
            "com.google.android.apps.bard" to "Gemini",
            "com.microsoft.copilot" to "Copilot",
            "com.deepseek.chat" to "DeepSeek",
        ).filter { com.mohamed.safi.apps.Apps.installed(ctx, it.first) }
        val question = input.ifBlank { messages.lastOrNull { it.role == "user" }?.text ?: "" }
        AlertDialog(
            onDismissRequest = { handoff = false },
            title = { Text("اسأل في تطبيق اشتراكك") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "الاشتراك الشهري (Claude Pro أو ChatGPT Plus أو Gemini Advanced) بيشتغل جوه تطبيقهم بس، ومش بيدّي تطبيقات تانية صلاحية تستخدمه. هنا تقدر تبعت سؤالك لتطبيقهم وتاخد الرد هناك.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (question.isBlank()) Text("اكتب سؤالك في الخانة الأول.", color = Warn)
                    if (apps.isEmpty()) Text("مش لاقي تطبيق Claude أو ChatGPT أو Gemini على تليفونك.", color = MaterialTheme.colorScheme.outline)
                    apps.forEach { (pkg, label) ->
                        OutlinedButton(onClick = {
                            val i = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain")
                                .putExtra(android.content.Intent.EXTRA_TEXT, question).setPackage(pkg)
                                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            val ok = runCatching { ctx.startActivity(i) }.isSuccess
                            if (!ok) {
                                (ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager)
                                    .setPrimaryClip(android.content.ClipData.newPlainText("q", question))
                                ctx.packageManager.getLaunchIntentForPackage(pkg)?.let { ctx.startActivity(it) }
                                toast(ctx, "السؤال اتنسخ، الصقه في $label")
                            }
                            handoff = false
                        }, enabled = question.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("افتح في $label") }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { handoff = false }) { Text("إغلاق") } },
        )
    }

    if (confirmClear) {
        ConfirmDialog("مسح المحادثة؟", "البيانات اللي اتسجلت هتفضل زي ما هي، بس الكلام بس اللي هيتمسح.", "امسح", { confirmClear = false }) {
            scope.launch { dao.clearChat() }
        }
    }
}

@Composable
private fun Bubble(m: ChatMsg) {
    val mine = m.role == "user"
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (mine) Alignment.Start else Alignment.End) {
        Box(
            Modifier
                .widthIn(max = 320.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 18.dp, topEnd = 18.dp,
                        bottomStart = if (mine) 4.dp else 18.dp, bottomEnd = if (mine) 18.dp else 4.dp,
                    ),
                )
                .background(if (mine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Column {
                Text(m.text, color = if (mine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                if (m.actions.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    m.actions.split("\n").forEach {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = Positive, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        Text(timeStr(m.time), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
    }
}

/** The real Claude app (uses the user's own Pro/Max subscription). */
object ClaudeApp {
    const val PKG = "com.anthropic.claude"
    fun installed(ctx: android.content.Context) = com.mohamed.safi.apps.Apps.installed(ctx, PKG)

    fun open(ctx: android.content.Context) {
        val i = ctx.packageManager.getLaunchIntentForPackage(PKG)
        if (i != null) ctx.startActivity(i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        else runCatching {
            ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("market://details?id=$PKG")).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure {
            ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://play.google.com/store/apps/details?id=$PKG")).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    /** Sends the text into a new Claude chat; falls back to clipboard + open. */
    fun ask(ctx: android.content.Context, text: String) {
        val q = text.trim()
        if (q.isEmpty()) { open(ctx); return }
        if (!installed(ctx)) { toast(ctx, "نزّل تطبيق Claude الأول"); open(ctx); return }
        val i = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain")
            .putExtra(android.content.Intent.EXTRA_TEXT, q).setPackage(PKG)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { ctx.startActivity(i) }.isFailure) {
            (ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager)
                .setPrimaryClip(android.content.ClipData.newPlainText("q", q))
            open(ctx)
            toast(ctx, "الكلام اتنسخ، الصقه في Claude")
        }
    }
}

@Composable
private fun ClaudeAppBar(onVoice: () -> Unit, onText: () -> Unit, hasText: Boolean) {
    val ctx = LocalContext.current
    Surface(color = Color(0xFFD97757).copy(alpha = 0.14f), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = Color(0xFFD97757), modifier = Modifier.size(34.dp)) {
                    Box(contentAlignment = Alignment.Center) { Text("✳", color = Color.White, fontWeight = FontWeight.Bold) }
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Claude باشتراكك", fontWeight = FontWeight.Bold)
                    Text("بيفتح تطبيق Claude الحقيقي", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onVoice, modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97757), contentColor = Color.White),
                ) { Icon(Icons.Default.Mic, null); Spacer(Modifier.width(6.dp)); Text("كلّم Claude") }
                OutlinedButton(onClick = { if (hasText) onText() else ClaudeApp.open(ctx) }, modifier = Modifier.weight(1f)) {
                    Text(if (hasText) "ابعت المكتوب لـClaude" else "افتح Claude")
                }
            }
        }
    }
}
