package com.quickdrop.app.ui

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
import androidx.compose.foundation.layout.PaddingValues
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
import com.quickdrop.app.ui.theme.QuickDropColors
import com.quickdrop.core.domain.RecognizedClassSlot
import com.quickdrop.core.model.ClassSlot
import com.quickdrop.core.model.Subject
import com.quickdrop.core.time.AppClock

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
    val location: String = "LH 30",
    val style: TimetableCourseStyle = TimetablePalettes.getStyle(courseCode)
)

data class RegisteredCourseInfo(
    val code: String,
    val title: String,
    val units: Int,
    val isCore: Boolean,
    val instructor: String,
    val scheduleSummary: String,
    val location: String,
    val style: TimetableCourseStyle
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WeeklyTimetableBoard(
    clock: AppClock,
    subjects: List<Subject>,
    slots: List<ClassSlot>,
    onUploadClick: () -> Unit,
    onLoadDefaultTemplate: () -> Unit,
    onOpenSubject: (String) -> Unit = {}
) {
    var scheduleMode by remember { mutableStateOf<String?>(null) } // null = auto
    var mobileSelectedDay by remember { mutableStateOf(1) } // 1=Mon, 2=Tue, 3=Wed, 4=Thu, 5=Fri

    val currentIsoDay = remember { clock.localDate().dayOfWeek.value }

    // Map store state to grid sessions or default template
    val mondaySessions = remember(slots, subjects) { getMondaySessions(slots, subjects) }
    val tuesdaySessions = remember(slots, subjects) { getTuesdaySessions(slots, subjects) }
    val wednesdaySessions = remember(slots, subjects) { getWednesdaySessions(slots, subjects) }
    val thursdaySessions = remember(slots, subjects) { getThursdaySessions(slots, subjects) }
    val fridaySessions = remember(slots, subjects) { getFridaySessions(slots, subjects) }

    val registeredCourses = remember(slots, subjects) { getRegisteredCoursesSummary(slots, subjects) }

    val totalCourses = registeredCourses.size
    val coreCount = registeredCourses.count { it.isCore }
    val electiveCount = registeredCourses.count { !it.isCore }
    val totalUnits = registeredCourses.sumOf { it.units }
    val weeklyMinutes = slots.filter { it.enabled }.sumOf { (it.endMinuteOfDay - it.startMinuteOfDay).coerceAtLeast(0) }
    val weeklyHours = (weeklyMinutes + 30) / 60

    val hasMon = mondaySessions.isNotEmpty()
    val hasTue = tuesdaySessions.isNotEmpty()
    val hasWed = wednesdaySessions.isNotEmpty()
    val hasThu = thursdaySessions.isNotEmpty()
    val hasFri = fridaySessions.isNotEmpty()

    val freeDaysList = buildList {
        if (!hasMon) add("Mon")
        if (!hasTue) add("Tue")
        if (!hasWed) add("Wed")
        if (!hasThu) add("Thu")
        if (!hasFri) add("Fri")
    }

    val freeDaysText = when {
        freeDaysList.isEmpty() -> "None"
        freeDaysList.size == 5 -> "All Free (No classes)"
        freeDaysList.size == 2 -> "${freeDaysList[0]} & ${freeDaysList[1]}"
        else -> freeDaysList.joinToString(", ")
    }

    val activeDaysList = buildList {
        if (hasMon) add("Mon")
        if (hasTue) add("Tue")
        if (hasWed) add("Wed")
        if (hasThu) add("Thu")
        if (hasFri) add("Fri")
    }

    val activeDaysText = if (activeDaysList.isEmpty()) "No classes scheduled" else activeDaysList.joinToString(", ")

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
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = if (isCompact) Modifier.fillMaxWidth() else Modifier
                            ) {
                                Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("📂 Upload Timetable (HTML/ICS)", fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
                            }

                            OutlinedButton(
                                onClick = onLoadDefaultTemplate,
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = if (isCompact) Modifier.fillMaxWidth() else Modifier
                            ) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp), tint = QuickDropColors.CyanLight)
                                Spacer(Modifier.width(6.dp))
                                Text("🪄 200L Schedule Template", fontSize = 12.sp, color = QuickDropColors.CyanLight, maxLines = 1)
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
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
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
                                fontSize = 13.sp
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = onUploadClick,
                                colors = ButtonDefaults.buttonColors(containerColor = QuickDropColors.Emerald),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("📂 Upload Timetable (HTML/ICS)", fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = onLoadDefaultTemplate,
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp), tint = QuickDropColors.CyanLight)
                                Spacer(Modifier.width(4.dp))
                                Text("🪄 200L Schedule", fontSize = 12.sp, color = QuickDropColors.CyanLight)
                            }
                        }
                    }
                }
            }

            // 2. 4 Top Metric Stats Cards (Adaptive 2x2 on Mobile, 1x4 on Desktop)
            if (isMobile) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatsCard(
                            label = "Registered Courses",
                            mainText = "$totalCourses Courses",
                            subText = "$coreCount Core • $electiveCount Elective",
                            badgeIcon = "📚",
                            badgeBg = Color(0x263B82F6),
                            modifier = Modifier.weight(1f)
                        )
                        StatsCard(
                            label = "Total Credit Units",
                            mainText = "$totalUnits Units",
                            subText = if (totalUnits > 0) "Semester Load" else "No units registered",
                            badgeIcon = "⚡",
                            badgeBg = Color(0x26F59E0B),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatsCard(
                            label = "Weekly Contact Hours",
                            mainText = "$weeklyHours Hours",
                            subText = activeDaysText,
                            badgeIcon = "⏰",
                            badgeBg = Color(0x26A855F7),
                            modifier = Modifier.weight(1f)
                        )
                        StatsCard(
                            label = "Free Days",
                            mainText = freeDaysText,
                            subText = if (freeDaysList.isEmpty()) "Classes every weekday" else "No lectures scheduled",
                            badgeIcon = "✨",
                            badgeBg = Color(0x2610B981),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatsCard(
                        label = "Registered Courses",
                        mainText = "$totalCourses Courses",
                        subText = "$coreCount Core • $electiveCount Elective",
                        badgeIcon = "📚",
                        badgeBg = Color(0x263B82F6),
                        modifier = Modifier.weight(1f)
                    )
                    StatsCard(
                        label = "Total Credit Units",
                        mainText = "$totalUnits Units",
                        subText = if (totalUnits > 0) "Semester Load" else "No units registered",
                        badgeIcon = "⚡",
                        badgeBg = Color(0x26F59E0B),
                        modifier = Modifier.weight(1f)
                    )
                    StatsCard(
                        label = "Weekly Contact Hours",
                        mainText = "$weeklyHours Hours",
                        subText = activeDaysText,
                        badgeIcon = "⏰",
                        badgeBg = Color(0x26A855F7),
                        modifier = Modifier.weight(1f)
                    )
                    StatsCard(
                        label = "Free Days",
                        mainText = freeDaysText,
                        subText = if (freeDaysList.isEmpty()) "Classes every weekday" else "No lectures scheduled",
                        badgeIcon = "✨",
                        badgeBg = Color(0x2610B981),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // 3. View Switcher Mode Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(QuickDropColors.CosmicSurfaceElevated)
                    .border(1.dp, QuickDropColors.CosmicBorder, RoundedCornerShape(12.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .then(
                            if (effectiveScheduleMode == "cards") Modifier.background(QuickDropColors.EmeraldGradient)
                            else Modifier.background(Color.Transparent)
                        )
                        .clickable { scheduleMode = "cards" }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("📱", fontSize = 14.sp)
                        Text(
                            text = "Day Cards View",
                            color = if (effectiveScheduleMode == "cards") Color.White else QuickDropColors.TextSecondary,
                            fontWeight = if (effectiveScheduleMode == "cards") FontWeight.Bold else FontWeight.Medium,
                            fontSize = 13.sp
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .then(
                            if (effectiveScheduleMode == "matrix") Modifier.background(QuickDropColors.EmeraldGradient)
                            else Modifier.background(Color.Transparent)
                        )
                        .clickable { scheduleMode = "matrix" }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("📊", fontSize = 14.sp)
                        Text(
                            text = "Full Matrix Board",
                            color = if (effectiveScheduleMode == "matrix") Color.White else QuickDropColors.TextSecondary,
                            fontWeight = if (effectiveScheduleMode == "matrix") FontWeight.Bold else FontWeight.Medium,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            // 4. Primary Schedule Component based on selected mode
            if (effectiveScheduleMode == "cards") {
                // MOBILE / COMPACT TOUCH-FRIENDLY DAY CARDS VIEW
                DayCardsMobileView(
                    currentIsoDay = currentIsoDay,
                    selectedDayIso = mobileSelectedDay,
                    onSelectDay = { mobileSelectedDay = it },
                    mondaySessions = mondaySessions,
                    tuesdaySessions = tuesdaySessions,
                    wednesdaySessions = wednesdaySessions,
                    thursdaySessions = thursdaySessions,
                    fridaySessions = fridaySessions,
                    onOpenSubject = onOpenSubject
                )
            } else {
                // HIGH-FIDELITY MATRIX GRID (Horizontal scrollable on mobile)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (isMobile) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0x1A06B6D4))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("↔", fontSize = 13.sp, color = QuickDropColors.CyanLight)
                            Text(
                                "Swipe horizontally to view full 8am–4pm matrix",
                                color = QuickDropColors.CyanLight,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(QuickDropColors.CosmicSurfaceElevated)
                            .border(1.dp, QuickDropColors.CosmicBorder, RoundedCornerShape(16.dp))
                            .padding(horizontal = 4.dp, vertical = 8.dp)
                            .horizontalScroll(rememberScrollState())
                    ) {
                        MatrixBoardView(
                            mondaySessions = mondaySessions,
                            tuesdaySessions = tuesdaySessions,
                            wednesdaySessions = wednesdaySessions,
                            thursdaySessions = thursdaySessions,
                            fridaySessions = fridaySessions,
                            onOpenSubject = onOpenSubject
                        )
                    }
                }
            }

            // 5. Course Directory Cards (Grid)
            CourseDirectorySection(
                courses = registeredCourses,
                isMobile = isMobile,
                onOpenSubject = onOpenSubject
            )
        }
    }
}

@Composable
fun DayCardsMobileView(
    currentIsoDay: Int,
    selectedDayIso: Int,
    onSelectDay: (Int) -> Unit,
    mondaySessions: List<GridCourseSession>,
    tuesdaySessions: List<GridCourseSession>,
    wednesdaySessions: List<GridCourseSession>,
    thursdaySessions: List<GridCourseSession>,
    fridaySessions: List<GridCourseSession>,
    onOpenSubject: (String) -> Unit
) {
    val dayTabs = listOf(
        1 to "Monday",
        2 to "Tuesday",
        3 to "Wednesday",
        4 to "Thursday",
        5 to "Friday"
    )

    val currentSessions = when (selectedDayIso) {
        1 -> mondaySessions
        2 -> tuesdaySessions
        3 -> wednesdaySessions
        4 -> thursdaySessions
        5 -> fridaySessions
        else -> emptyList()
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Day selector tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(QuickDropColors.CosmicSurfaceElevated)
                .border(1.dp, QuickDropColors.CosmicBorder, RoundedCornerShape(14.dp))
                .padding(6.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            dayTabs.forEach { (iso, name) ->
                val isSelected = selectedDayIso == iso
                val isToday = currentIsoDay == iso
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .then(
                            if (isSelected) Modifier.background(QuickDropColors.EmeraldGradient)
                            else Modifier.background(Color.Transparent)
                        )
                        .clickable { onSelectDay(iso) }
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
                            Text(
                                text = "TODAY",
                                color = if (isSelected) Color.White else QuickDropColors.EmeraldLight,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }
            }
        }

        // Sessions for selected day
        if (currentSessions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(QuickDropColors.CosmicSurfaceElevated)
                    .border(1.dp, QuickDropColors.CosmicBorder, RoundedCornerShape(16.dp))
                    .padding(28.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (selectedDayIso == 3) "✨ College Free Period" else "🌴 No Lectures Scheduled",
                        color = QuickDropColors.TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = if (selectedDayIso == 3) "12:00 PM – 4:00 PM reserved for student academic projects & research" else "Free day! Perfect for active recall reviews & assignments.",
                        color = QuickDropColors.TextSecondary,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                currentSessions.forEach { session ->
                    MobileCourseCard(session = session, onOpen = { onOpenSubject(session.courseCode) })
                }
            }
        }
    }
}

@Composable
fun MobileCourseCard(session: GridCourseSession, onOpen: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(session.style.backgroundColor)
            .border(1.5.dp, session.style.borderColor, RoundedCornerShape(14.dp))
            .clickable(onClick = onOpen)
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = session.courseCode,
                    color = session.style.textColor,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp,
                    fontFamily = FontFamily.Monospace
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(session.style.badgeBgColor)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = session.statusDisplay,
                        color = session.style.textColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    )
                }
            }

            Text(
                text = session.courseTitle,
                color = QuickDropColors.TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
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
                        color = QuickDropColors.TextSecondary,
                        fontSize = 11.sp
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.LocationOn, contentDescription = null, tint = session.style.textColor, modifier = Modifier.size(13.dp))
                    Text(
                        text = session.location,
                        color = session.style.textColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

@Composable
fun StatsCard(
    label: String,
    mainText: String,
    subText: String,
    badgeIcon: String,
    badgeBg: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(QuickDropColors.CosmicSurfaceElevated)
            .border(1.dp, QuickDropColors.CosmicBorder, RoundedCornerShape(14.dp))
            .padding(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    color = QuickDropColors.TextSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(badgeIcon, fontSize = 13.sp)
            }

            Text(
                text = mainText,
                color = QuickDropColors.TextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = subText,
                color = QuickDropColors.TextMuted,
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun MatrixBoardView(
    mondaySessions: List<GridCourseSession>,
    tuesdaySessions: List<GridCourseSession>,
    wednesdaySessions: List<GridCourseSession>,
    thursdaySessions: List<GridCourseSession>,
    fridaySessions: List<GridCourseSession>,
    onOpenSubject: (String) -> Unit
) {
    val timeHeaders = listOf(
        "DAY / TIME" to 110.dp,
        "08:00 - 09:00" to 95.dp,
        "09:00 - 10:00" to 95.dp,
        "10:00 - 11:00" to 95.dp,
        "11:00 - 12:00" to 95.dp,
        "12:00 - 01:00" to 95.dp,
        "01:00 - 02:00" to 50.dp, // BREAK
        "02:00 - 03:00" to 95.dp,
        "03:00 - 04:00" to 95.dp
    )

    Column(
        modifier = Modifier.width(timeHeaders.sumOf { it.second.value.toDouble() }.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        // Table Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(QuickDropColors.CosmicSurface)
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            timeHeaders.forEach { (text, width) ->
                Box(modifier = Modifier.width(width), contentAlignment = Alignment.Center) {
                    Text(
                        text = text,
                        color = QuickDropColors.TextSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // Days
        MatrixDayRow("MONDAY", mondaySessions, "B", onOpenSubject)
        MatrixDayRow("TUESDAY", tuesdaySessions, "R", onOpenSubject)
        MatrixWednesdayRow("WEDNESDAY", "E")
        MatrixDayRow("THURSDAY", thursdaySessions, "A", onOpenSubject)
        MatrixFridayRow("FRIDAY", "K")
    }
}

@Composable
fun MatrixDayRow(
    dayName: String,
    sessions: List<GridCourseSession>,
    breakLetter: String,
    onOpenSubject: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Day label
        Box(
            modifier = Modifier
                .width(110.dp)
                .fillMaxSize()
                .background(QuickDropColors.CosmicSurface)
                .border(0.5.dp, QuickDropColors.CosmicBorder)
                .padding(8.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = dayName,
                color = QuickDropColors.TextPrimary,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        // Hour 8 & 9 (8am - 10am)
        RenderTimeSlot(sessions, 8, 95.dp, onOpenSubject)
        RenderTimeSlot(sessions, 9, 95.dp, onOpenSubject)

        // Hour 10 & 11 (10am - 12pm)
        RenderTimeSlot(sessions, 10, 95.dp, onOpenSubject)
        RenderTimeSlot(sessions, 11, 95.dp, onOpenSubject)

        // Hour 12 (12pm - 1pm)
        RenderTimeSlot(sessions, 12, 95.dp, onOpenSubject)

        // Break Column (1pm - 2pm)
        Box(
            modifier = Modifier
                .width(50.dp)
                .fillMaxSize()
                .background(Color(0x1A64748B))
                .border(0.5.dp, QuickDropColors.CosmicBorder),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = breakLetter,
                color = QuickDropColors.TextMuted,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        // Hour 14 & 15 (2pm - 4pm)
        RenderTimeSlot(sessions, 14, 95.dp, onOpenSubject)
        RenderTimeSlot(sessions, 15, 95.dp, onOpenSubject)
    }
}

@Composable
fun RenderTimeSlot(
    sessions: List<GridCourseSession>,
    hour: Int,
    defaultWidth: androidx.compose.ui.unit.Dp,
    onOpenSubject: (String) -> Unit
) {
    // Check if this hour is covered by a spanning double slot that started earlier
    val startingHere = sessions.find { it.startHour == hour }
    val coveredByEarlier = sessions.find { it.startHour < hour && (it.startHour + it.spanHours) > hour }

    if (coveredByEarlier != null) {
        // Already rendered by earlier hour
        return
    }

    if (startingHere != null) {
        val cellWidth = defaultWidth * startingHere.spanHours
        Box(
            modifier = Modifier
                .width(cellWidth)
                .fillMaxSize()
                .background(startingHere.style.backgroundColor)
                .border(1.dp, startingHere.style.borderColor)
                .clickable { onOpenSubject(startingHere.courseCode) }
                .padding(6.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = startingHere.courseCode,
                        color = startingHere.style.textColor,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = startingHere.statusDisplay,
                        color = startingHere.style.textColor,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = startingHere.courseTitle,
                    color = QuickDropColors.TextPrimary,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = startingHere.timeSubtitle.take(14),
                        color = QuickDropColors.TextMuted,
                        fontSize = 8.sp
                    )
                    Text(
                        text = startingHere.durationDisplay,
                        color = startingHere.style.textColor,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    } else {
        // Free Slot
        Box(
            modifier = Modifier
                .width(defaultWidth)
                .fillMaxSize()
                .background(Color(0x08FFFFFF))
                .border(0.5.dp, QuickDropColors.CosmicBorder),
            contentAlignment = Alignment.Center
        ) {
            Text("Free", color = QuickDropColors.TextMuted, fontSize = 9.sp)
        }
    }
}

@Composable
fun MatrixWednesdayRow(dayName: String, breakLetter: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(110.dp)
                .fillMaxSize()
                .background(QuickDropColors.CosmicSurface)
                .border(0.5.dp, QuickDropColors.CosmicBorder)
                .padding(8.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = dayName,
                color = QuickDropColors.TextPrimary,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        repeat(5) {
            Box(
                modifier = Modifier
                    .width(95.dp)
                    .fillMaxSize()
                    .background(Color(0x08FFFFFF))
                    .border(0.5.dp, QuickDropColors.CosmicBorder),
                contentAlignment = Alignment.Center
            ) {
                Text("Free", color = QuickDropColors.TextMuted, fontSize = 9.sp)
            }
        }

        Box(
            modifier = Modifier
                .width(50.dp)
                .fillMaxSize()
                .background(Color(0x1A64748B))
                .border(0.5.dp, QuickDropColors.CosmicBorder),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = breakLetter,
                color = QuickDropColors.TextMuted,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        Box(
            modifier = Modifier
                .width(190.dp)
                .fillMaxSize()
                .background(Color(0x1406B6D4))
                .border(1.dp, Color(0x3306B6D4))
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "✨ College Free Period (12:00 - 4:00 PM)",
                color = Color(0xFF67E8F9),
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp
            )
        }
    }
}

@Composable
fun MatrixFridayRow(dayName: String, breakLetter: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(110.dp)
                .fillMaxSize()
                .background(QuickDropColors.CosmicSurface)
                .border(0.5.dp, QuickDropColors.CosmicBorder)
                .padding(8.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = dayName,
                color = QuickDropColors.TextPrimary,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        repeat(5) {
            Box(
                modifier = Modifier
                    .width(95.dp)
                    .fillMaxSize()
                    .background(Color(0x08FFFFFF))
                    .border(0.5.dp, QuickDropColors.CosmicBorder),
                contentAlignment = Alignment.Center
            ) {
                Text("Free", color = QuickDropColors.TextMuted, fontSize = 9.sp)
            }
        }

        Box(
            modifier = Modifier
                .width(50.dp)
                .fillMaxSize()
                .background(Color(0x1A64748B))
                .border(0.5.dp, QuickDropColors.CosmicBorder),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = breakLetter,
                color = QuickDropColors.TextMuted,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        repeat(2) {
            Box(
                modifier = Modifier
                    .width(95.dp)
                    .fillMaxSize()
                    .background(Color(0x08FFFFFF))
                    .border(0.5.dp, QuickDropColors.CosmicBorder),
                contentAlignment = Alignment.Center
            ) {
                Text("Free", color = QuickDropColors.TextMuted, fontSize = 9.sp)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CourseDirectorySection(
    courses: List<RegisteredCourseInfo>,
    isMobile: Boolean,
    onOpenSubject: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Registered Courses Directory (${courses.size})",
                color = QuickDropColors.TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            Text(
                text = "First Semester 2026/2027",
                color = QuickDropColors.TextMuted,
                fontSize = 11.sp
            )
        }

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            courses.forEach { course ->
                CourseDirectoryCard(
                    course = course,
                    modifier = if (isMobile) Modifier.fillMaxWidth() else Modifier.width(280.dp),
                    onOpen = { onOpenSubject(course.code) }
                )
            }
        }
    }
}

@Composable
fun CourseDirectoryCard(
    course: RegisteredCourseInfo,
    modifier: Modifier = Modifier,
    onOpen: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(QuickDropColors.CosmicSurfaceElevated)
            .border(1.dp, course.style.borderColor.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
            .clickable(onClick = onOpen)
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = course.code,
                    color = course.style.textColor,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (course.isCore) Color(0x3310B981) else Color(0x338B5CF6))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (course.isCore) "CORE • ${course.units}U" else "ELECTIVE • ${course.units}U",
                        color = if (course.isCore) QuickDropColors.EmeraldLight else QuickDropColors.VioletLight,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    )
                }
            }

            Text(
                text = course.title,
                color = QuickDropColors.TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = "📍 ${course.location} • ⏰ ${course.scheduleSummary}",
                color = QuickDropColors.TextSecondary,
                fontSize = 11.sp
            )
        }
    }
}

// -------------------------------------------------------------
// Sample data helpers for template fallback & slot parsing
// -------------------------------------------------------------
fun getMondaySessions(slots: List<ClassSlot>, subjects: List<Subject>): List<GridCourseSession> {
    val result = mutableListOf<GridCourseSession>()
    for (slot in slots.filter { it.enabled && it.includesDayOfWeek(1) }) {
        val subj = subjects.find { it.id == slot.subjectId }
        val name = subj?.name ?: "Course"
        val code = name.takeWhile { it != '-' && it != ':' }.trim()
        val title = name.substringAfter('-', name).substringAfter(':', name).trim()
        val startH = slot.startMinuteOfDay / 60
        val endH = slot.endMinuteOfDay / 60
        val span = (endH - startH).coerceAtLeast(1)
        val isDouble = span >= 2
        result.add(
            GridCourseSession(
                courseCode = if (code.length <= 10) code else "COURSE",
                courseTitle = title,
                statusDisplay = "CORE • 2U",
                timeSubtitle = "%02d:00 - %02d:00 %s".format(startH, endH, if (isDouble) "(Double Slot)" else ""),
                durationDisplay = "$span hrs",
                startHour = startH,
                spanHours = span,
                location = slot.location.ifBlank { "LH 30" }
            )
        )
    }
    if (result.isEmpty()) {
        return listOf(
            GridCourseSession("SEN 201", "Introduction to Software Engineering", "CORE • 2U", "10:00 AM - 12:00 PM (Double Slot)", "2 hrs", 10, 2),
            GridCourseSession("CYB 203", "Cybercrime, Law & Countermeasures", "CORE • 2U", "12:00 - 1:00 PM", "1 hr", 12, 1),
            GridCourseSession("WU-CYB 205", "Data Security and Privacy", "ELECTIVE • 2U", "2:00 - 4:00 PM (Double Slot)", "2 hrs", 14, 2)
        )
    }
    return result
}

fun getTuesdaySessions(slots: List<ClassSlot>, subjects: List<Subject>): List<GridCourseSession> {
    val result = mutableListOf<GridCourseSession>()
    for (slot in slots.filter { it.enabled && it.includesDayOfWeek(2) }) {
        val subj = subjects.find { it.id == slot.subjectId }
        val name = subj?.name ?: "Course"
        val code = name.takeWhile { it != '-' && it != ':' }.trim()
        val title = name.substringAfter('-', name).substringAfter(':', name).trim()
        val startH = slot.startMinuteOfDay / 60
        val endH = slot.endMinuteOfDay / 60
        val span = (endH - startH).coerceAtLeast(1)
        val isDouble = span >= 2
        result.add(
            GridCourseSession(
                courseCode = if (code.length <= 10) code else "COURSE",
                courseTitle = title,
                statusDisplay = "CORE • 2U",
                timeSubtitle = "%02d:00 - %02d:00 %s".format(startH, endH, if (isDouble) "(Double Slot)" else ""),
                durationDisplay = "$span hrs",
                startHour = startH,
                spanHours = span,
                location = slot.location.ifBlank { "LH 30" }
            )
        )
    }
    if (result.isEmpty()) {
        return listOf(
            GridCourseSession("IFT 211", "Digital Logic Design", "CORE • 2U", "8:00 - 10:00 AM (Double Slot)", "2 hrs", 8, 2),
            GridCourseSession("CYB 203", "Cybercrime, Law & Countermeasures", "CORE • 2U", "11:00 AM - 12:00 PM", "1 hr", 11, 1),
            GridCourseSession("COS 201", "Computer Programming I", "CORE • 3U", "12:00 - 1:00 PM", "1 hr", 12, 1),
            GridCourseSession("COS 201", "Computer Programming I (Practical / Lecture)", "CORE • 3U", "2:00 - 4:00 PM (Double Slot)", "2 hrs", 14, 2, "Lab 2")
        )
    }
    return result
}

fun getWednesdaySessions(slots: List<ClassSlot>, subjects: List<Subject>): List<GridCourseSession> = emptyList()

fun getThursdaySessions(slots: List<ClassSlot>, subjects: List<Subject>): List<GridCourseSession> {
    val result = mutableListOf<GridCourseSession>()
    for (slot in slots.filter { it.enabled && it.includesDayOfWeek(4) }) {
        val subj = subjects.find { it.id == slot.subjectId }
        val name = subj?.name ?: "Course"
        val code = name.takeWhile { it != '-' && it != ':' }.trim()
        val title = name.substringAfter('-', name).substringAfter(':', name).trim()
        val startH = slot.startMinuteOfDay / 60
        val endH = slot.endMinuteOfDay / 60
        val span = (endH - startH).coerceAtLeast(1)
        val isDouble = span >= 2
        result.add(
            GridCourseSession(
                courseCode = if (code.length <= 10) code else "COURSE",
                courseTitle = title,
                statusDisplay = "CORE • 2U",
                timeSubtitle = "%02d:00 - %02d:00 %s".format(startH, endH, if (isDouble) "(Double Slot)" else ""),
                durationDisplay = "$span hrs",
                startHour = startH,
                spanHours = span,
                location = slot.location.ifBlank { "LH 30" }
            )
        )
    }
    if (result.isEmpty()) {
        return listOf(
            GridCourseSession("CYB 201", "Introduction to Cybersecurity and Strategy", "CORE • 2U", "8:00 - 10:00 AM (Double Slot)", "2 hrs", 8, 2),
            GridCourseSession("ENT 211", "Entrepreneurship and Innovation", "CORE • 2U", "11:00 AM - 1:00 PM (Double Slot)", "2 hrs", 11, 2),
            GridCourseSession("WU 211", "Leadership in Action: Grand Challenge Projects I", "ELECTIVE • 2U", "2:00 - 4:00 PM (Double Slot)", "2 hrs", 14, 2)
        )
    }
    return result
}

fun getFridaySessions(slots: List<ClassSlot>, subjects: List<Subject>): List<GridCourseSession> = emptyList()

fun getRegisteredCoursesSummary(slots: List<ClassSlot>, subjects: List<Subject>): List<RegisteredCourseInfo> {
    return listOf(
        RegisteredCourseInfo("SEN 201", "Introduction to Software Engineering", 2, true, "CSAC Faculty", "Mon 10:00 - 12:00", "LH 30", TimetablePalettes.SEN_201),
        RegisteredCourseInfo("CYB 203", "Cybercrime, Law & Countermeasures", 2, true, "CSAC Faculty", "Mon 12:00 - 1:00, Tue 11:00 - 12:00", "LH 30", TimetablePalettes.CYB_203),
        RegisteredCourseInfo("WU-CYB 205", "Data Security and Privacy", 2, false, "CSAC Faculty", "Mon 14:00 - 16:00", "LH 30", TimetablePalettes.CYB_205),
        RegisteredCourseInfo("IFT 211", "Digital Logic Design", 2, true, "CSAC Faculty", "Tue 08:00 - 10:00", "LH 30", TimetablePalettes.IFT_211),
        RegisteredCourseInfo("COS 201", "Computer Programming I", 3, true, "CSAC Faculty", "Tue 12:00 - 1:00, Tue 14:00 - 16:00", "LH 30 / Lab 2", TimetablePalettes.COS_201),
        RegisteredCourseInfo("CYB 201", "Introduction to Cybersecurity and Strategy", 2, true, "CSAC Faculty", "Thu 08:00 - 10:00", "LH 30", TimetablePalettes.CYB_201),
        RegisteredCourseInfo("ENT 211", "Entrepreneurship and Innovation", 2, true, "CSAC Faculty", "Thu 11:00 - 13:00", "LH 30", TimetablePalettes.ENT_211),
        RegisteredCourseInfo("WU 211", "Leadership in Action: Grand Challenge Projects I", 2, false, "CSAC Faculty", "Thu 14:00 - 16:00", "LH 30", TimetablePalettes.WU_211)
    )
}
