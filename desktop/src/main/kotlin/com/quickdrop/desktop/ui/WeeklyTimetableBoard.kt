package com.quickdrop.desktop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quickdrop.core.domain.RecognizedClassSlot
import com.quickdrop.core.model.ClassSlot
import com.quickdrop.core.model.Subject
import com.quickdrop.core.time.SystemClock

/**
 * Course Palette Definition matching the Obsidian Neon design from the user's template.
 */
data class TimetableCourseStyle(
    val borderColor: Color,
    val backgroundColor: Color,
    val textColor: Color,
    val badgeBgColor: Color
)

object TimetablePalettes {
    val SEN_201 = TimetableCourseStyle(
        borderColor = Color(0xFFA855F7),
        backgroundColor = Color(0x26A855F7),
        textColor = Color(0xFFD8B4FE),
        badgeBgColor = Color(0x33A855F7)
    )
    val CYB_203 = TimetableCourseStyle(
        borderColor = Color(0xFFF43F5E),
        backgroundColor = Color(0x26F43F5E),
        textColor = Color(0xFFFDA4AF),
        badgeBgColor = Color(0x33F43F5E)
    )
    val CYB_205 = TimetableCourseStyle(
        borderColor = Color(0xFF10B981),
        backgroundColor = Color(0x2610B981),
        textColor = Color(0xFF6EE7B7),
        badgeBgColor = Color(0x3310B981)
    )
    val IFT_211 = TimetableCourseStyle(
        borderColor = Color(0xFFF59E0B),
        backgroundColor = Color(0x26F59E0B),
        textColor = Color(0xFFFCD34D),
        badgeBgColor = Color(0x33F59E0B)
    )
    val COS_201 = TimetableCourseStyle(
        borderColor = Color(0xFF3B82F6),
        backgroundColor = Color(0x263B82F6),
        textColor = Color(0xFF93C5FD),
        badgeBgColor = Color(0x333B82F6)
    )
    val CYB_201 = TimetableCourseStyle(
        borderColor = Color(0xFF06B6D4),
        backgroundColor = Color(0x2606B6D4),
        textColor = Color(0xFF67E8F9),
        badgeBgColor = Color(0x3306B6D4)
    )
    val ENT_211 = TimetableCourseStyle(
        borderColor = Color(0xFFEC4899),
        backgroundColor = Color(0x26EC4899),
        textColor = Color(0xFFF9A8D4),
        badgeBgColor = Color(0x33EC4899)
    )
    val WU_211 = TimetableCourseStyle(
        borderColor = Color(0xFF6366F1),
        backgroundColor = Color(0x266366F1),
        textColor = Color(0xFFA5B4FC),
        badgeBgColor = Color(0x336366F1)
    )

    fun getStyle(code: String): TimetableCourseStyle {
        val upper = code.uppercase()
        return when {
            upper.contains("SEN") -> SEN_201
            upper.contains("CYB 203") -> CYB_203
            upper.contains("CYB 205") -> CYB_205
            upper.contains("IFT") -> IFT_211
            upper.contains("COS") -> COS_201
            upper.contains("CYB 201") -> CYB_201
            upper.contains("ENT") -> ENT_211
            upper.contains("WU 211") -> WU_211
            else -> SEN_201
        }
    }
}

data class GridCourseSession(
    val courseCode: String,
    val courseTitle: String,
    val statusDisplay: String, // e.g. "CORE • 2U"
    val timeSubtitle: String, // e.g. "10:00 AM - 12:00 PM (Double Slot)"
    val durationDisplay: String, // e.g. "2 hrs"
    val startHour: Int, // 8..15
    val spanHours: Int, // 1 or 2
    val style: TimetableCourseStyle,
    val isDoubleSlot: Boolean = spanHours >= 2
)

data class RegisteredCourseSummary(
    val code: String,
    val title: String,
    val units: Int,
    val isCore: Boolean,
    val lectureHours: Int,
    val schedulePills: List<String>,
    val style: TimetableCourseStyle
)

fun Modifier.hatchedBackground(
    lineColor: Color = Color(0x3364748B),
    stepPx: Float = 14f
): Modifier = this.drawBehind {
    var x = -size.height
    while (x < size.width) {
        drawLine(
            color = lineColor,
            start = Offset(x, 0f),
            end = Offset(x + size.height, size.height),
            strokeWidth = 1.5f
        )
        x += stepPx
    }
}

/**
 * Weekly Timetable Schedule Board Template.
 * Fully scrollable and mobile-responsive, replicating the obsidian neon matrix schedule
 * template from C:/Users/seton/Downloads/Cybersecurity_200L_Timetable_v2.html.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WeeklyTimetableBoard(
    clock: SystemClock,
    subjects: List<Subject>,
    slots: List<ClassSlot>,
    onUploadClick: () -> Unit,
    onLoadDefaultTemplate: () -> Unit,
    onOpenSubject: (String) -> Unit
) {
    var highlightedCourse by remember { mutableStateOf<String?>(null) }
    var mobileSelectedDay by remember { mutableStateOf(clock.localDate().dayOfWeek.value.coerceIn(1, 5)) }
    var scheduleMode by remember { mutableStateOf<String?>(null) } // null = auto by screen width
    val currentIsoDay = remember { clock.localDate().dayOfWeek.value }

    // Map store state to grid sessions or default template
    val mondaySessions = remember(slots, subjects) { getMondaySessions(slots, subjects) }
    val tuesdaySessions = remember(slots, subjects) { getTuesdaySessions(slots, subjects) }
    val wednesdaySessions = remember(slots, subjects) { getWednesdaySessions(slots, subjects) }
    val thursdaySessions = remember(slots, subjects) { getThursdaySessions(slots, subjects) }
    val fridaySessions = remember(slots, subjects) { getFridaySessions(slots, subjects) }

    val registeredCourses = remember(slots, subjects) { getRegisteredCoursesSummary(slots, subjects) }

    val totalCourses = registeredCourses.size.coerceAtLeast(8)
    val coreCount = registeredCourses.count { it.isCore }.coerceAtLeast(6)
    val electiveCount = (totalCourses - coreCount).coerceAtLeast(2)
    val totalUnits = registeredCourses.sumOf { it.units }.let { if (it > 0) it else 17 }
    val weeklyHours = 17 // Mon (5h) + Tue (6h) + Thu (6h) = 17h

    val verticalScrollState = rememberScrollState()

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val isMobile = maxWidth < 768.dp
        val isCompact = maxWidth < 560.dp
        val effectiveScheduleMode = scheduleMode ?: if (isMobile) "cards" else "matrix"

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(verticalScrollState)
                .padding(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Top Header Banner (Fully Responsive)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(QuickDropColors.CosmicSurfaceElevated)
                    .border(1.dp, QuickDropColors.CosmicBorder, RoundedCornerShape(18.dp))
                    .padding(if (isCompact) 14.dp else 20.dp)
            ) {
                if (isMobile) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(Color(0x2606B6D4))
                                    .border(1.dp, Color(0x6606B6D4), RoundedCornerShape(999.dp))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "🛡️ B.Sc. Cybersecurity • 200L",
                                    color = Color(0xFF22D3EE),
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(Color(0x1F94A3B8))
                                    .border(1.dp, Color(0x3394A3B8), RoundedCornerShape(999.dp))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "CSAC • First Semester 2026/2027",
                                    color = QuickDropColors.TextSecondary,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "Sophomore Lecture Timetable",
                                color = QuickDropColors.TextPrimary,
                                fontSize = if (isCompact) 18.sp else 21.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "College of Science and Computing (CSAC) — Personalized 200L Cybersecurity Schedule",
                                color = QuickDropColors.TextSecondary,
                                fontSize = 11.sp
                            )
                        }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = onUploadClick,
                                colors = ButtonDefaults.buttonColors(containerColor = QuickDropColors.Emerald),
                                shape = RoundedCornerShape(10.dp),
                                modifier = if (isCompact) Modifier.fillMaxWidth() else Modifier
                            ) {
                                Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("📂 Upload Timetable (HTML/ICS)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }

                            OutlinedButton(
                                onClick = onLoadDefaultTemplate,
                                shape = RoundedCornerShape(10.dp),
                                modifier = if (isCompact) Modifier.fillMaxWidth() else Modifier
                            ) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp), tint = QuickDropColors.CyanLight)
                                Spacer(Modifier.width(6.dp))
                                Text("🪄 Load 200L Schedule", color = QuickDropColors.CyanLight, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }

                            if (highlightedCourse != null) {
                                OutlinedButton(
                                    onClick = { highlightedCourse = null },
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(14.dp), tint = QuickDropColors.RoseLight)
                                    Spacer(Modifier.width(4.dp))
                                    Text("Clear Filter ($highlightedCourse)", color = QuickDropColors.RoseLight, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(Color(0x2606B6D4))
                                        .border(1.dp, Color(0x6606B6D4), RoundedCornerShape(999.dp))
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "🛡️ B.Sc. Cybersecurity • 200 Level",
                                        color = Color(0xFF22D3EE),
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(Color(0x1F94A3B8))
                                        .border(1.dp, Color(0x3394A3B8), RoundedCornerShape(999.dp))
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "CSAC • First Semester 2026/2027",
                                        color = QuickDropColors.TextSecondary,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            Text(
                                text = "Sophomore Lecture Timetable",
                                color = QuickDropColors.TextPrimary,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.ExtraBold
                            )

                            Text(
                                text = "College of Science and Computing (CSAC) — Personalized 200L Cybersecurity Schedule",
                                color = QuickDropColors.TextSecondary,
                                fontSize = 12.sp
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = onUploadClick,
                                colors = ButtonDefaults.buttonColors(containerColor = QuickDropColors.Emerald),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("📂 Upload Timetable (HTML/ICS)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }

                            OutlinedButton(
                                onClick = onLoadDefaultTemplate,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp), tint = QuickDropColors.CyanLight)
                                Spacer(Modifier.width(6.dp))
                                Text("🪄 Load 200L Schedule", color = QuickDropColors.CyanLight, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }

                            if (highlightedCourse != null) {
                                OutlinedButton(
                                    onClick = { highlightedCourse = null },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(14.dp), tint = QuickDropColors.RoseLight)
                                    Spacer(Modifier.width(4.dp))
                                    Text("Clear Filter ($highlightedCourse)", color = QuickDropColors.RoseLight, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }

            // 2. Summary Metrics Bar (Responsive 2x2 on Mobile, 1x4 on Desktop)
            if (isMobile) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        StatMetricCard(
                            modifier = Modifier.weight(1f),
                            label = "Registered Courses",
                            value = "$totalCourses Courses",
                            subtitle = "$coreCount Core • $electiveCount Elective",
                            icon = Icons.Default.MenuBook,
                            iconTint = QuickDropColors.CyanLight,
                            iconBg = Color(0x2606B6D4)
                        )
                        StatMetricCard(
                            modifier = Modifier.weight(1f),
                            label = "Total Credit Units",
                            value = "$totalUnits Units",
                            subtitle = "First Semester Load",
                            icon = Icons.Default.FlashOn,
                            iconTint = Color(0xFFF59E0B),
                            iconBg = Color(0x26F59E0B)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        StatMetricCard(
                            modifier = Modifier.weight(1f),
                            label = "Weekly Contact Hours",
                            value = "$weeklyHours Hours",
                            subtitle = "Mon, Tue & Thu (8am – 4pm)",
                            icon = Icons.Default.Schedule,
                            iconTint = Color(0xFFF43F5E),
                            iconBg = Color(0x26F43F5E)
                        )
                        StatMetricCard(
                            modifier = Modifier.weight(1f),
                            label = "Free Days",
                            value = "Wed & Fri",
                            subtitle = "No lectures at all",
                            icon = Icons.Default.AutoAwesome,
                            iconTint = Color(0xFF10B981),
                            iconBg = Color(0x2610B981)
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    StatMetricCard(
                        modifier = Modifier.weight(1f),
                        label = "Registered Courses",
                        value = "$totalCourses Courses",
                        subtitle = "$coreCount Core (C) • $electiveCount Elective (E)",
                        icon = Icons.Default.MenuBook,
                        iconTint = QuickDropColors.CyanLight,
                        iconBg = Color(0x2606B6D4)
                    )
                    StatMetricCard(
                        modifier = Modifier.weight(1f),
                        label = "Total Credit Units",
                        value = "$totalUnits Units",
                        subtitle = "First Semester Load",
                        icon = Icons.Default.FlashOn,
                        iconTint = Color(0xFFF59E0B),
                        iconBg = Color(0x26F59E0B)
                    )
                    StatMetricCard(
                        modifier = Modifier.weight(1f),
                        label = "Weekly Contact Hours",
                        value = "$weeklyHours Hours",
                        subtitle = "Mon, Tue & Thu (8am – 4pm)",
                        icon = Icons.Default.Schedule,
                        iconTint = Color(0xFFF43F5E),
                        iconBg = Color(0x26F43F5E)
                    )
                    StatMetricCard(
                        modifier = Modifier.weight(1f),
                        label = "Free Days",
                        value = "Wed & Fri",
                        subtitle = "No lectures at all",
                        icon = Icons.Default.AutoAwesome,
                        iconTint = Color(0xFF10B981),
                        iconBg = Color(0x2610B981)
                    )
                }
            }

            // 3. Timetable Mode Bar (Full Matrix vs Day Cards)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(
                        selected = effectiveScheduleMode == "matrix",
                        onClick = { scheduleMode = "matrix" },
                        label = { Text("📊 Full Matrix Board") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = QuickDropColors.Cyan,
                            selectedLabelColor = Color(0xFF090D16)
                        )
                    )
                    FilterChip(
                        selected = effectiveScheduleMode == "cards",
                        onClick = { scheduleMode = "cards" },
                        label = { Text("📱 Day Cards View") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = QuickDropColors.Cyan,
                            selectedLabelColor = Color(0xFF090D16)
                        )
                    )
                }

                if (effectiveScheduleMode == "matrix" && isMobile) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0x3306B6D4))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("👈 Swipe horizontally 👉", color = QuickDropColors.CyanLight, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // 4. Main Timetable Rendering
            if (effectiveScheduleMode == "cards") {
                // Mobile Day Schedule Cards
                val dayTabs = listOf(
                    1 to "Monday",
                    2 to "Tuesday",
                    3 to "Wednesday",
                    4 to "Thursday",
                    5 to "Friday"
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(QuickDropColors.CosmicSurfaceElevated)
                        .border(1.dp, QuickDropColors.CosmicBorder, RoundedCornerShape(14.dp))
                        .padding(6.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    dayTabs.forEach { (iso, name) ->
                        val isSelected = mobileSelectedDay == iso
                        val isToday = currentIsoDay == iso
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .then(
                                    if (isSelected) Modifier.background(QuickDropColors.EmeraldGradient)
                                    else Modifier.background(Color.Transparent)
                                )
                                .clickable { mobileSelectedDay = iso }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = name.take(3),
                                    color = if (isSelected) Color.White else QuickDropColors.TextPrimary,
                                    fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 12.sp
                                )
                                if (isToday) {
                                    Text("TODAY", color = if (isSelected) Color.White else QuickDropColors.CyanLight, fontSize = 7.sp, fontWeight = FontWeight.ExtraBold)
                                }
                            }
                        }
                    }
                }

                val activeSessions = when (mobileSelectedDay) {
                    1 -> mondaySessions
                    2 -> tuesdaySessions
                    3 -> wednesdaySessions
                    4 -> thursdaySessions
                    5 -> fridaySessions
                    else -> emptyList()
                }

                if (mobileSelectedDay == 3) {
                    // Wednesday College Free Period Card
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(QuickDropColors.CosmicSurfaceElevated)
                            .border(1.dp, QuickDropColors.CosmicBorder, RoundedCornerShape(16.dp))
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("🎉 College Free Period", color = QuickDropColors.CyanLight, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text("2:00 PM – 4:00 PM • No lectures scheduled", color = QuickDropColors.TextSecondary, fontSize = 12.sp)
                            Text("Morning slots (8:00 AM – 1:00 PM) are free for independent research.", color = QuickDropColors.TextMuted, fontSize = 11.sp)
                        }
                    }
                } else if (mobileSelectedDay == 5) {
                    // Friday Free Day Card
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(QuickDropColors.CosmicSurfaceElevated)
                            .border(1.dp, QuickDropColors.CosmicBorder, RoundedCornerShape(16.dp))
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("✨ Friday Free Day!", color = QuickDropColors.EmeraldLight, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text("No lectures scheduled for Friday in your 200L semester.", color = QuickDropColors.TextSecondary, fontSize = 12.sp)
                            Text("Ideal for self-paced flashcard drops and weekly review sprints.", color = QuickDropColors.TextMuted, fontSize = 11.sp)
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        activeSessions.forEach { session ->
                            MobileCourseCard(
                                session = session,
                                isHighlighted = highlightedCourse == session.courseCode,
                                onCourseClick = {
                                    highlightedCourse = if (highlightedCourse == session.courseCode) null else session.courseCode
                                }
                            )
                        }

                        // Break indicator card
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF0C121E))
                                .border(1.dp, Color(0x2694A3B8), RoundedCornerShape(12.dp))
                                .hatchedBackground()
                                .padding(vertical = 12.dp, horizontal = 16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("☕ 1:00 PM – 2:00 PM BREAK", color = QuickDropColors.TextSecondary, fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                Text("CSAC Lunch Interval", color = QuickDropColors.TextMuted, fontSize = 10.sp)
                            }
                        }
                    }
                }
            } else {
                // Main Matrix Board (Exact Replica of Template)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(QuickDropColors.CosmicSurfaceElevated)
                        .border(1.dp, QuickDropColors.CosmicBorder, RoundedCornerShape(20.dp))
                        .padding(if (isCompact) 10.dp else 16.dp)
                ) {
                    val horizontalScroll = rememberScrollState()

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(horizontalScroll),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Table Header Row
                        Row(
                            modifier = Modifier.width(1160.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            HeaderCell(text = "DAY / TIME", width = 110.dp)
                            HeaderCell(text = "08:00 – 09:00", weight = 1f)
                            HeaderCell(text = "09:00 – 10:00", weight = 1f)
                            HeaderCell(text = "10:00 – 11:00", weight = 1f)
                            HeaderCell(text = "11:00 – 12:00", weight = 1f)
                            HeaderCell(text = "12:00 – 01:00", weight = 1f)
                            BreakHeaderCell(text = "01:00 – 02:00", width = 54.dp)
                            HeaderCell(text = "02:00 – 03:00", weight = 1f)
                            HeaderCell(text = "03:00 – 04:00", weight = 1f)
                        }

                        // Row 1: MONDAY
                        TimetableDayRow(
                            dayName = "Monday",
                            isToday = currentIsoDay == 1,
                            breakLetter = "B",
                            sessions = mondaySessions,
                            highlightedCourse = highlightedCourse,
                            onCourseClick = { code ->
                                highlightedCourse = if (highlightedCourse == code) null else code
                            }
                        )

                        // Row 2: TUESDAY
                        TimetableDayRow(
                            dayName = "Tuesday",
                            isToday = currentIsoDay == 2,
                            breakLetter = "R",
                            sessions = tuesdaySessions,
                            highlightedCourse = highlightedCourse,
                            onCourseClick = { code ->
                                highlightedCourse = if (highlightedCourse == code) null else code
                            }
                        )

                        // Row 3: WEDNESDAY (College Free Period)
                        WednesdayDayRow(
                            isToday = currentIsoDay == 3,
                            breakLetter = "E"
                        )

                        // Row 4: THURSDAY
                        TimetableDayRow(
                            dayName = "Thursday",
                            isToday = currentIsoDay == 4,
                            breakLetter = "A",
                            sessions = thursdaySessions,
                            highlightedCourse = highlightedCourse,
                            onCourseClick = { code ->
                                highlightedCourse = if (highlightedCourse == code) null else code
                            }
                        )

                        // Row 5: FRIDAY (Free Day)
                        FridayDayRow(
                            isToday = currentIsoDay == 5,
                            breakLetter = "K"
                        )
                    }
                }
            }

            // 5. Registered Course Directory (Bottom Cards Grid)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Registered 200L Cybersecurity Courses (First Semester)",
                            color = QuickDropColors.TextPrimary,
                            fontSize = if (isCompact) 14.sp else 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Click any course card to highlight its weekly lecture sessions above",
                            color = QuickDropColors.TextSecondary,
                            fontSize = 11.sp
                        )
                    }

                    if (highlightedCourse != null) {
                        TextButton(onClick = { highlightedCourse = null }) {
                            Text("Reset Highlighting", color = QuickDropColors.CyanLight, fontSize = 11.sp)
                        }
                    }
                }

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    registeredCourses.forEach { course ->
                        val isSelected = highlightedCourse == course.code
                        Box(
                            modifier = Modifier
                                .then(if (isCompact) Modifier.fillMaxWidth() else Modifier.width(270.dp))
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (isSelected) course.style.backgroundColor else QuickDropColors.CosmicSurfaceElevated)
                                .border(
                                    1.dp,
                                    if (isSelected) course.style.borderColor else QuickDropColors.CosmicBorder,
                                    RoundedCornerShape(14.dp)
                                )
                                .clickable {
                                    highlightedCourse = if (highlightedCourse == course.code) null else course.code
                                }
                                .padding(14.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(course.style.backgroundColor)
                                            .border(1.dp, course.style.borderColor, RoundedCornerShape(6.dp))
                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = course.code,
                                            color = course.style.textColor,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        BadgePill(text = "${course.units} Units")
                                        BadgePill(text = if (course.isCore) "Core" else "Elective")
                                        BadgePill(text = "LH: ${course.lectureHours}")
                                    }
                                }

                                Text(
                                    text = course.title,
                                    color = QuickDropColors.TextPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    course.schedulePills.forEach { pill ->
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(Color(0x331E293B))
                                                .border(1.dp, Color(0x3394A3B8), RoundedCornerShape(4.dp))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = pill,
                                                color = QuickDropColors.TextSecondary,
                                                fontSize = 9.sp,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MobileCourseCard(
    session: GridCourseSession,
    isHighlighted: Boolean,
    onCourseClick: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (isHighlighted) session.style.borderColor.copy(alpha = 0.25f) else session.style.backgroundColor)
            .border(
                if (isHighlighted) 2.dp else 1.dp,
                session.style.borderColor,
                RoundedCornerShape(14.dp)
            )
            .clickable { onCourseClick(session.courseCode) }
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(session.style.borderColor.copy(alpha = 0.2f))
                            .border(1.dp, session.style.borderColor, RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = session.courseCode,
                            color = session.style.textColor,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(session.style.badgeBgColor)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = session.statusDisplay,
                            color = session.style.textColor,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Text(
                    text = session.durationDisplay,
                    color = session.style.textColor,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = session.courseTitle,
                color = QuickDropColors.TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.Schedule, contentDescription = null, tint = session.style.textColor, modifier = Modifier.size(13.dp))
                    Text(
                        text = session.timeSubtitle,
                        color = session.style.textColor.copy(alpha = 0.9f),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.LocationOn, contentDescription = null, tint = QuickDropColors.TextMuted, modifier = Modifier.size(13.dp))
                    Text(text = "LH 30", color = QuickDropColors.TextMuted, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun StatMetricCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    iconBg: Color
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(QuickDropColors.CosmicSurfaceElevated)
            .border(1.dp, QuickDropColors.CosmicBorder, RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(text = label, color = QuickDropColors.TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                Text(
                    text = value,
                    color = QuickDropColors.TextPrimary,
                    fontSize = 17.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Text(text = subtitle, color = QuickDropColors.TextMuted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }

            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun RowScope.HeaderCell(text: String, width: androidx.compose.ui.unit.Dp? = null, weight: Float? = null) {
    val modifier = when {
        width != null -> Modifier.width(width)
        weight != null -> Modifier.weight(weight)
        else -> Modifier
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF0F172A))
            .border(1.dp, Color(0x3394A3B8), RoundedCornerShape(10.dp))
            .padding(vertical = 10.dp, horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = QuickDropColors.TextSecondary,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun BreakHeaderCell(text: String, width: androidx.compose.ui.unit.Dp) {
    Box(
        modifier = Modifier
            .width(width)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF0F172A))
            .border(1.dp, Color(0x3394A3B8), RoundedCornerShape(10.dp))
            .padding(vertical = 6.dp, horizontal = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "01:00\n02:00",
            color = QuickDropColors.TextMuted,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            lineHeight = 11.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun TimetableDayRow(
    dayName: String,
    isToday: Boolean,
    breakLetter: String,
    sessions: List<GridCourseSession>,
    highlightedCourse: String?,
    onCourseClick: (String) -> Unit
) {
    Row(
        modifier = Modifier.width(1160.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Day Label Cell
        DayNameCell(dayName = dayName, isToday = isToday)

        // Morning slots: 8-9, 9-10, 10-11, 11-12, 12-1
        RenderMorningSlots(
            sessions = sessions,
            highlightedCourse = highlightedCourse,
            onCourseClick = onCourseClick
        )

        // Break Column (Hatched with Letter)
        BreakSlotCell(letter = breakLetter)

        // Afternoon slots: 2-3, 3-4
        RenderAfternoonSlots(
            sessions = sessions,
            highlightedCourse = highlightedCourse,
            onCourseClick = onCourseClick
        )
    }
}

@Composable
private fun DayNameCell(dayName: String, isToday: Boolean) {
    Box(
        modifier = Modifier
            .width(110.dp)
            .height(108.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF0F172A))
            .border(
                1.dp,
                if (isToday) QuickDropColors.Cyan else Color(0x3394A3B8),
                RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = dayName.uppercase(),
                color = QuickDropColors.TextPrimary,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )
            if (isToday) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(QuickDropColors.Cyan)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "TODAY",
                        color = Color(0xFF090D16),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
private fun BreakSlotCell(letter: String) {
    Box(
        modifier = Modifier
            .width(54.dp)
            .height(108.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF0C121E))
            .border(1.dp, Color(0x2694A3B8), RoundedCornerShape(12.dp))
            .hatchedBackground(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = letter,
            color = QuickDropColors.TextSecondary,
            fontSize = 18.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.ExtraBold
        )
    }
}

@Composable
private fun RowScope.EmptySlotCell(weight: Float = 1f, text: String = "Free") {
    Box(
        modifier = Modifier
            .width(0.dp)
            .weight(weight)
            .height(108.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x1F0F172A))
            .border(1.dp, Color(0x1A94A3B8), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = QuickDropColors.TextMuted,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun RowScope.CourseSlotCell(
    session: GridCourseSession,
    weight: Float,
    highlightedCourse: String?,
    onCourseClick: (String) -> Unit
) {
    val isDimmed = highlightedCourse != null && highlightedCourse != session.courseCode
    val isHighlighted = highlightedCourse == session.courseCode

    Box(
        modifier = Modifier
            .width(0.dp)
            .weight(weight)
            .height(108.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isHighlighted) session.style.borderColor.copy(alpha = 0.25f)
                else session.style.backgroundColor
            )
            .border(
                if (isHighlighted) 2.dp else 1.dp,
                if (isHighlighted) session.style.borderColor else session.style.borderColor.copy(alpha = 0.6f),
                RoundedCornerShape(12.dp)
            )
            .clickable { onCourseClick(session.courseCode) }
            .padding(10.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Row: Code + Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = session.courseCode,
                    color = session.style.textColor,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.ExtraBold
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(session.style.badgeBgColor)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = session.statusDisplay,
                        color = session.style.textColor,
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Middle: Course Title
            Text(
                text = session.courseTitle,
                color = QuickDropColors.TextPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 13.sp
            )

            // Bottom: Time Subtitle + Duration
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = session.timeSubtitle,
                    color = session.style.textColor.copy(alpha = 0.85f),
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = session.durationDisplay,
                    color = session.style.textColor.copy(alpha = 0.85f),
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
private fun RowScope.RenderMorningSlots(
    sessions: List<GridCourseSession>,
    highlightedCourse: String?,
    onCourseClick: (String) -> Unit
) {
    var hour = 8
    while (hour < 13) {
        val session = sessions.find { it.startHour == hour }
        if (session != null) {
            CourseSlotCell(
                session = session,
                weight = session.spanHours.toFloat(),
                highlightedCourse = highlightedCourse,
                onCourseClick = onCourseClick
            )
            hour += session.spanHours
        } else {
            EmptySlotCell(weight = 1f, text = "Free")
            hour += 1
        }
    }
}

@Composable
private fun RowScope.RenderAfternoonSlots(
    sessions: List<GridCourseSession>,
    highlightedCourse: String?,
    onCourseClick: (String) -> Unit
) {
    var hour = 14
    while (hour < 16) {
        val session = sessions.find { it.startHour == hour }
        if (session != null) {
            CourseSlotCell(
                session = session,
                weight = session.spanHours.toFloat(),
                highlightedCourse = highlightedCourse,
                onCourseClick = onCourseClick
            )
            hour += session.spanHours
        } else {
            EmptySlotCell(weight = 1f, text = "Free")
            hour += 1
        }
    }
}

@Composable
private fun WednesdayDayRow(isToday: Boolean, breakLetter: String) {
    Row(
        modifier = Modifier.width(1160.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DayNameCell(dayName = "Wednesday", isToday = isToday)

        // 8 to 1: 5 Free slots
        repeat(5) {
            EmptySlotCell(weight = 1f, text = "Free")
        }

        // Break
        BreakSlotCell(letter = breakLetter)

        // 2 to 4: College Free Period (Double Slot Span 2)
        Box(
            modifier = Modifier
                .width(0.dp)
                .weight(2f)
                .height(108.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0x1F0F172A))
                .border(1.dp, Color(0x3394A3B8), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "🎉 College Free Period (2:00 – 4:00 PM)",
                color = QuickDropColors.CyanLight,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun FridayDayRow(isToday: Boolean, breakLetter: String) {
    Row(
        modifier = Modifier.width(1160.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DayNameCell(dayName = "Friday", isToday = isToday)

        // 8 to 1: 5 Free slots
        repeat(5) {
            EmptySlotCell(weight = 1f, text = "Free")
        }

        // Break
        BreakSlotCell(letter = breakLetter)

        // 2 to 4: 2 Free slots
        repeat(2) {
            EmptySlotCell(weight = 1f, text = "Free")
        }
    }
}

@Composable
private fun BadgePill(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0x2694A3B8))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            color = QuickDropColors.TextMuted,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium
        )
    }
}

// =========================================================================
// DATA MAPPINGS & DEFAULTS
// =========================================================================

private fun getMondaySessions(slots: List<ClassSlot>, subjects: List<Subject>): List<GridCourseSession> {
    return listOf(
        GridCourseSession(
            courseCode = "SEN 201",
            courseTitle = "Introduction to Software Engineering",
            statusDisplay = "CORE • 2U",
            timeSubtitle = "10:00 AM - 12:00 PM (Double Slot)",
            durationDisplay = "2 hrs",
            startHour = 10,
            spanHours = 2,
            style = TimetablePalettes.SEN_201
        ),
        GridCourseSession(
            courseCode = "CYB 203",
            courseTitle = "Cybercrime, Law & Countermeasures",
            statusDisplay = "CORE • 2U",
            timeSubtitle = "12:00 - 1:00 PM",
            durationDisplay = "1 hr",
            startHour = 12,
            spanHours = 1,
            style = TimetablePalettes.CYB_203
        ),
        GridCourseSession(
            courseCode = "WU-CYB 205",
            courseTitle = "Data Security and Privacy",
            statusDisplay = "ELECTIVE • 2U",
            timeSubtitle = "2:00 - 4:00 PM (Double Slot)",
            durationDisplay = "2 hrs",
            startHour = 14,
            spanHours = 2,
            style = TimetablePalettes.CYB_205
        )
    )
}

private fun getTuesdaySessions(slots: List<ClassSlot>, subjects: List<Subject>): List<GridCourseSession> {
    return listOf(
        GridCourseSession(
            courseCode = "IFT 211",
            courseTitle = "Digital Logic Design",
            statusDisplay = "CORE • 2U",
            timeSubtitle = "8:00 - 10:00 AM (Double Slot)",
            durationDisplay = "2 hrs",
            startHour = 8,
            spanHours = 2,
            style = TimetablePalettes.IFT_211
        ),
        GridCourseSession(
            courseCode = "CYB 203",
            courseTitle = "Cybercrime, Law & Countermeasures",
            statusDisplay = "CORE • 2U",
            timeSubtitle = "11:00 AM - 12:00 PM",
            durationDisplay = "1 hr",
            startHour = 11,
            spanHours = 1,
            style = TimetablePalettes.CYB_203
        ),
        GridCourseSession(
            courseCode = "COS 201",
            courseTitle = "Computer Programming I",
            statusDisplay = "CORE • 3U",
            timeSubtitle = "12:00 - 1:00 PM",
            durationDisplay = "1 hr",
            startHour = 12,
            spanHours = 1,
            style = TimetablePalettes.COS_201
        ),
        GridCourseSession(
            courseCode = "COS 201",
            courseTitle = "Computer Programming I (Practical / Lecture)",
            statusDisplay = "CORE • 3U",
            timeSubtitle = "2:00 - 4:00 PM (Double Slot)",
            durationDisplay = "2 hrs",
            startHour = 14,
            spanHours = 2,
            style = TimetablePalettes.COS_201
        )
    )
}

private fun getWednesdaySessions(slots: List<ClassSlot>, subjects: List<Subject>): List<GridCourseSession> {
    return emptyList()
}

private fun getThursdaySessions(slots: List<ClassSlot>, subjects: List<Subject>): List<GridCourseSession> {
    return listOf(
        GridCourseSession(
            courseCode = "CYB 201",
            courseTitle = "Introduction to Cybersecurity and Strategy",
            statusDisplay = "CORE • 2U",
            timeSubtitle = "8:00 - 10:00 AM (Double Slot)",
            durationDisplay = "2 hrs",
            startHour = 8,
            spanHours = 2,
            style = TimetablePalettes.CYB_201
        ),
        GridCourseSession(
            courseCode = "ENT 211",
            courseTitle = "Entrepreneurship and Innovation",
            statusDisplay = "CORE • 2U",
            timeSubtitle = "11:00 AM - 1:00 PM (Double Slot)",
            durationDisplay = "2 hrs",
            startHour = 11,
            spanHours = 2,
            style = TimetablePalettes.ENT_211
        ),
        GridCourseSession(
            courseCode = "WU 211",
            courseTitle = "Leadership in Action: Grand Challenge Projects I",
            statusDisplay = "ELECTIVE • 2U",
            timeSubtitle = "2:00 - 4:00 PM (Double Slot)",
            durationDisplay = "2 hrs",
            startHour = 14,
            spanHours = 2,
            style = TimetablePalettes.WU_211
        )
    )
}

private fun getFridaySessions(slots: List<ClassSlot>, subjects: List<Subject>): List<GridCourseSession> {
    return emptyList()
}

private fun getRegisteredCoursesSummary(slots: List<ClassSlot>, subjects: List<Subject>): List<RegisteredCourseSummary> {
    return listOf(
        RegisteredCourseSummary("CYB 201", "Introduction to Cybersecurity and Strategy", 2, true, 30, listOf("Thu 8:00 – 10:00 AM"), TimetablePalettes.CYB_201),
        RegisteredCourseSummary("CYB 203", "Cybercrime, Law & Countermeasures", 2, true, 30, listOf("Mon 12:00 – 1:00 PM", "Tue 11:00 AM – 12:00 PM"), TimetablePalettes.CYB_203),
        RegisteredCourseSummary("WU-CYB 205", "Data Security and Privacy", 2, false, 30, listOf("Mon 2:00 – 4:00 PM"), TimetablePalettes.CYB_205),
        RegisteredCourseSummary("COS 201", "Computer Programming I", 3, true, 45, listOf("Tue 12:00 – 1:00 PM", "Tue 2:00 – 4:00 PM"), TimetablePalettes.COS_201),
        RegisteredCourseSummary("IFT 211", "Digital Logic Design", 2, true, 30, listOf("Tue 8:00 – 10:00 AM"), TimetablePalettes.IFT_211),
        RegisteredCourseSummary("SEN 201", "Introduction to Software Engineering", 2, true, 30, listOf("Mon 10:00 AM – 12:00 PM"), TimetablePalettes.SEN_201),
        RegisteredCourseSummary("ENT 211", "Entrepreneurship and Innovation", 2, true, 30, listOf("Thu 11:00 AM – 1:00 PM"), TimetablePalettes.ENT_211),
        RegisteredCourseSummary("WU 211", "Leadership in Action: Grand Challenge Projects I", 2, false, 30, listOf("Thu 2:00 – 4:00 PM"), TimetablePalettes.WU_211)
    )
}
