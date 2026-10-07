package com.mohamed.safi.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mohamed.safi.data.AppShortcuts
import com.mohamed.safi.ui.AppCard
import com.mohamed.safi.ui.EmptyState
import com.mohamed.safi.ui.ScreenScaffold
import com.mohamed.safi.ui.Text

private data class AppDestination(val route: String, val icon: String, val title: String, val description: String, val keywords: String)

private val destinations = listOf(
    AppDestination("assistant", "🧠", "رفيق", "مساعدك المحلي أو المتصل، الذاكرة والتنبيهات", "مساعد ذكاء ذاكرة تفضيلات محادثة"),
    AppDestination("quiz", "🏆", "مسابقات وتحديات", "دوري أسبوعي، ماراثون، عيلة وأصحاب", "مسابقة دوري تحدي ماراثون اصحاب عيلة"),
    AppDestination("kids", "🌳", "مدينة الخير", "مهام وألعاب وقصص ونادي ناشئين", "اطفال طفل ناشئين نادي العاب قصص"),
    AppDestination("family", "👨‍👩‍👧", "العيلة", "ربط مشفّر، مزامنة، أهداف وختمة", "عيلة اقتران واي فاي مزامنة QR اهل"),
    AppDestination("study", "📚", "الدراسة والواجبات", "جلسات وتركيز ودروس وامتحانات", "مذاكرة واجبات دراسة امتحان"),
    AppDestination("wird", "📖", "الورد اليومي", "خطة قراءة القرآن وسلسلة الاستمرار", "ورد قرآن قراءة مصحف"),
    AppDestination("hifz", "🕌", "الحفظ", "خطة وتسجيل المحفوظ من القرآن", "حفظ تسميع قرآن"),
    AppDestination("quran", "📗", "القرآن", "قراءة وبحث في السور والآيات", "قران آية سورة بحث"),
    AppDestination("azkar", "🤲", "الأذكار", "أذكار الصباح والمساء والمناسبات", "اذكار دعاء صباح مساء"),
    AppDestination("hadith", "🕋", "الحديث", "بحث وقراءة من كتب الحديث", "حديث سنة"),
    AppDestination("radio", "📻", "إذاعات القرآن", "محطات حية تم التحقق من تشغيلها", "راديو اذاعة قرآن بث"),
    AppDestination("kidstv", "📺", "قنوات الأطفال", "مصادر أطفال تعمل وقت آخر فحص", "تلفزيون قنوات اطفال"),
    AppDestination("schedule", "🗓️", "الجدول والتنبيهات", "مواعيد وتذكيرات ومنبهات", "مواعيد تنبيه جدول منبه"),
    AppDestination("finance", "💳", "المال والمصاريف", "مصروفات وتحويلات وفواتير وتقارير", "مصروف فلوس فواتير تحويل ديون"),
    AppDestination("fitness", "🏃", "الصحة والحركة", "أكل ومياه ووزن وتمرين", "صحة رياضة أكل وزن ماء"),
    AppDestination("diary", "✍️", "مذكراتي", "مساحة خاصة للكتابة والمشاعر", "مذكرات يوميات مشاعر"),
    AppDestination("documents", "📁", "المستندات", "وثائق وصور وتنبيهات انتهاء", "مستندات جواز اوراق"),
    AppDestination("settings", "⚙️", "الإعدادات", "الخصوصية، الوصول، النسخ الاحتياطي والذكاء", "اعدادات خصوصية نسخة خط حجم تباين"),
    AppDestination("app_guide", "🧭", "دليل التطبيق", "شرح الأقسام وطريقة الاستخدام", "دليل شرح مساعدة"),
)

@Composable
fun AppSearchScreen(onBack: () -> Unit, open: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    @Suppress("UNUSED_VARIABLE") val shortcutsVersion = AppShortcuts.version.intValue
    val needle = query.trim().lowercase()
    val results = remember(needle) {
        if (needle.length < 2) destinations else destinations.filter {
            (it.title + " " + it.description + " " + it.keywords).lowercase().contains(needle)
        }
    }
    ScreenScaffold("بحث في أثر", onBack = onBack) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                OutlinedTextField(
                    query, { query = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text("اكتب اسم القسم أو اللي محتاجه") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                )
            }
            item { Text(if (needle.length < 2) "اختصارات لكل أقسام أثر" else "${results.size} نتيجة", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline) }
            if (results.isEmpty()) item { EmptyState(Icons.Default.Search, "ملقيناش قسم بالاسم ده. جرّب كلمة أبسط.") }
            items(results, key = { it.route }) { item ->
                AppCard(onClick = { open(item.route) }) {
                    Row {
                        Text(item.icon, style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.title, fontWeight = FontWeight.Bold)
                            Text(item.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }
                        TextButton(onClick = { AppShortcuts.toggleFavorite(item.route) }, contentPadding = PaddingValues(horizontal = 6.dp)) {
                            Text(if (AppShortcuts.isFavorite(item.route)) "★" else "☆", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}
