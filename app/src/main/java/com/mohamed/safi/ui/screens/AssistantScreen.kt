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
    val listState = rememberLazyListState()
    val pending by UiBus.pendingVoice.collectAsState()

    fun send(text: String) {
        val t = text.trim()
        if (t.isEmpty() || busy) return
        if (!Claude.hasKey) {
            toast(ctx, "ضيف مفتاح Claude API من الإعدادات الأول")
            return
        }
        input = ""
        busy = true
        scope.launch {
            Assistant.ask(ctx, t)
            busy = false
        }
    }

    val voice = rememberVoiceInput { send(it) }

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
        "صافي",
        actions = {
            if (messages.isNotEmpty()) IconButton(onClick = { confirmClear = true }) { Icon(Icons.Default.DeleteSweep, "مسح المحادثة") }
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).imePadding()) {
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
                                Text("⚠️ محتاج تضيف مفتاح Claude API من الإعدادات", color = Warn)
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
                        Text("صافي بيفكر…", color = MaterialTheme.colorScheme.outline)
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
