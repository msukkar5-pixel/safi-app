package com.mohamed.safi.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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

private val entries = listOf(
    Entry("quran", "القرآن الكريم", "المصحف والتفسير والعلامات", Icons.Default.MenuBook, Color(0xFF0F6E5C)),
    Entry("wird", "الورد اليومي", "صفحات كل يوم وختمة وتذكير", Icons.Default.AutoStories, Color(0xFF1B5E20)),
    Entry("shaarawy", "الشيخ الشعراوي", "خواطر بالسورة والموضوع", Icons.Default.VideoLibrary, Color(0xFFC62828)),
    Entry("azkar", "الأذكار والأدعية", "الصباح والمساء والسبحة", Icons.Default.Favorite, Color(0xFF00897B)),
    Entry("hadith", "الأحاديث الصحيحة", "البخاري ومسلم", Icons.Default.LibraryBooks, Color(0xFF9C6644)),
    Entry("diary", "مذكراتي", "بالصوت وتحليل يومك وشخصيتك", Icons.Default.EditNote, Color(0xFFAD1457)),
    Entry("prayer", "الصلاة والقبلة", "المواعيد والأذان والبوصلة", Icons.Default.Mosque, Color(0xFF2E7DBA)),
    Entry("healthrecords", "حالتي الصحية", "أدوية، تحاليل، دكاترة", Icons.Default.MonitorHeart, Color(0xFFD32F2F)),
    Entry("fitness", "الجيم والصحة", "أكل، تمارين، وزن، الساعة", Icons.Default.FitnessCenter, Color(0xFF1B998B)),
    Entry("carpool", "دور السواقة", "مين هيسوق بكرة", Icons.Default.DirectionsCar, Color(0xFFD98A1C)),
    Entry("transfers", "تحويلات مصر", "ماما، البيت، الدروس…", Icons.Default.SwapHoriz, Warn),
    Entry("bills", "الفواتير والالتزامات", "إيجار، كهرباء، اتصالات", Icons.Default.Payments, Brand),
    Entry("debts", "السلف والديون", "ليك وعليك ومواعيدها", Icons.Default.People, Danger),
    Entry("car", "العربية", "بنزين، صيانة، أوراق", Icons.Default.DirectionsCar, Color2),
    Entry("places", "أماكني", "كل مكان رحته بالوقت", Icons.Default.Place, Color(0xFF8E5BB8)),
    Entry("reports", "التقارير والميزانية", "صرفت إيه وفين", Icons.Default.BarChart, Positive),
    Entry("documents", "المستندات", "الهوية، الإقامة، الجواز…", Icons.Default.Badge, Color(0xFF5C6BC0)),
    Entry("savings", "أهداف الادخار", "تحوّش كام كل شهر", Icons.Default.Savings, Color(0xFF3D9970)),
    Entry("lessons", "دروس الأولاد", "المواد والمدرسين والفلوس", Icons.Default.School, Color(0xFFB5651D)),
    Entry("zakat", "حاسبة الزكاة", "النصاب والمستحق", Icons.Default.VolunteerActivism, Color(0xFF8D6E63)),
    Entry("settings", "الإعدادات", "المفتاح، الصلاحيات، السعر", Icons.Default.Settings, Color(0xFF6C7A89)),
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
            items(entries) { e ->
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
