package com.mohamed.safi.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import com.mohamed.safi.ui.Text
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mohamed.safi.ui.*

private data class Entry(val route: String, val title: String, val desc: String, val icon: ImageVector, val color: Color)

private val sections = listOf(
    "العبادات" to listOf(
        Entry("quran", "القرآن الكريم", "المصحف والتفسير والعلامات", Icons.Default.MenuBook, Color(0xFF0F6E5C)),
        Entry("quranaudio", "القرآن المسموع", "كل القرّاء وكل الروايات", Icons.Default.Headphones, Color(0xFF00695C)),
        Entry("wird", "الورد اليومي", "صفحات كل يوم وختمة", Icons.Default.AutoStories, Color(0xFF1B5E20)),
        Entry("azkar", "الأذكار والأدعية", "الصباح والمساء والسبحة", Icons.Default.Favorite, Color(0xFF00897B)),
        Entry("hisn", "حصن المسلم", "كل الأبواب بالعداد والصوت", Icons.Default.Shield, Color(0xFF00796B)),
        Entry("manasik", "الحج والعمرة", "المناسك خطوة بخطوة وأدوات", Icons.Default.Landscape, Color(0xFF8F6E22)),
        Entry("ruqyah", "الرقية الشرعية", "الآيات والأدعية الصحيحة", Icons.Default.Healing, Color(0xFF2E7D5B)),
        Entry("prayer", "الصلاة والقبلة", "المواعيد والأذان والبوصلة", Icons.Default.Mosque, Color(0xFF2E7DBA)),
        Entry("prayertracker", "متابعة الصلوات", "في وقتها، جماعة، القضاء", Icons.Default.TaskAlt, Color(0xFF1B7A4E)),
        Entry("islamiccalendar", "التقويم الهجري", "المناسبات وأيام الصيام", Icons.Default.CalendarMonth, Color(0xFF8F6E22)),
        Entry("asmahusna", "أسماء الله الحسنى", "الأسماء ومعانيها", Icons.Default.AutoAwesome, Color(0xFF00695C)),
        Entry("shaarawy", "الشيخ الشعراوي", "خواطر بالسورة والموضوع", Icons.Default.VideoLibrary, Color(0xFFC62828)),
    ),
    "المعرفة" to listOf(
        Entry("library", "المكتبة", "الكتب والسير والأحاديث والمسموع", Icons.Default.LocalLibrary, Color(0xFF5D4037)),
        Entry("quiz", "مسابقة صافي", "أسئلة دينية وعامة ومراحل", Icons.Default.EmojiEvents, Color(0xFFB8860B)),
    ),
    "حياتي" to listOf(
        Entry("finance", "الحسابات", "مصاريف، تحويلات، التزامات، سلف، ادخار", Icons.Default.AccountBalanceWallet, Brand),
        Entry("vehicle", "السيارة", "دور السواقة، الصيانة، الخطط", Icons.Default.DirectionsCar, Color(0xFFD98A1C)),
        Entry("documents", "المستندات", "الهوية، الإقامة، الجواز…", Icons.Default.Badge, Color(0xFF5C6BC0)),
        Entry("diary", "مذكراتي", "بالصوت وتحليل يومك", Icons.Default.EditNote, Color(0xFFAD1457)),
        Entry("places", "أماكني", "كل مكان رحته بالوقت", Icons.Default.Place, Color(0xFF8E5BB8)),
    ),
    "الصحة" to listOf(
        Entry("fitness", "الجيم والصحة", "أكل، تمارين، وزن، الساعة", Icons.Default.FitnessCenter, Color(0xFF1B998B)),
        Entry("vitals", "القلب والضغط", "الضغط والنبض والأكسجين ونصايح", Icons.Default.Favorite, Color(0xFFC62828)),
        Entry("healthrecords", "حالتي الصحية", "أدوية، تحاليل، دكاترة", Icons.Default.MonitorHeart, Color(0xFFD32F2F)),
    ),
    "الإعدادات" to listOf(
        Entry("alerts", "التنبيهات", "الأذان، الأذكار، الورد", Icons.Default.NotificationsActive, Color(0xFF00796B)),
        Entry("settings", "الإعدادات", "اللغة، الذكاء الاصطناعي، الصوت", Icons.Default.Settings, Color(0xFF6C7A89)),
    ),
)

@Composable
fun MoreScreen(open: (String) -> Unit) {
    ScreenScaffold("المزيد") { pad ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            sections.forEach { (title, list) ->
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(title, fontFamily = Amiri, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 6.dp))
                }
                items(list) { e ->
                    AppCard(onClick = { open(e.route) }) {
                        CatBadge(e.title, 44, e.icon, e.color)
                        Spacer(Modifier.height(10.dp))
                        Text(e.title, fontWeight = FontWeight.Bold)
                        Text(e.desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
        }
    }
}
