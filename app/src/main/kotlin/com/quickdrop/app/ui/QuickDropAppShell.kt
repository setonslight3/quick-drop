package com.quickdrop.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quickdrop.app.QuickDropApplication
import com.quickdrop.app.ui.theme.QuickDropColors
import com.quickdrop.core.domain.ClassOccurrence
import com.quickdrop.core.domain.SpacedRepetitionScheduler
import com.quickdrop.core.model.ApprovalStatus
import com.quickdrop.core.model.ClassSlot
import com.quickdrop.core.model.Difficulty
import com.quickdrop.core.model.DropVisibilityState
import com.quickdrop.core.model.LearningItem
import com.quickdrop.core.model.LearningItemKind
import com.quickdrop.core.model.QuizScopeSnapshot
import com.quickdrop.core.model.ResponseOutcome
import com.quickdrop.core.model.ScheduleException
import com.quickdrop.core.model.ScheduleExceptionAction
import com.quickdrop.core.model.Subject
import com.quickdrop.core.model.ThemePreference
import com.quickdrop.core.model.TopicMasteryStatus
import com.quickdrop.core.model.UserPreferences
import kotlinx.coroutines.launch
import java.util.UUID

enum class BottomNavTab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Default.Home),
    TIMETABLE("Timetable", Icons.Default.CalendarMonth),
    STUDY("Study", Icons.Default.Quiz),
    INGESTION("Upload", Icons.Default.FileUpload),
    PROGRESS("Analytics", Icons.Default.Insights)
}

sealed class ActiveScreen {
    data object MainTabs : ActiveScreen()
    data class SubjectDetail(val subjectId: String) : ActiveScreen()
    data class LectureEditor(val subjectId: String, val lectureId: String? = null) : ActiveScreen()
    data class MaterialReview(val lectureId: String) : ActiveScreen()
    data class QuizSetup(val primarySubjectId: String?) : ActiveScreen()
    data class QuizRunner(val sessionId: String) : ActiveScreen()
    data class QuizResults(val sessionId: String) : ActiveScreen()
    data object Settings : ActiveScreen()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickDropAppShell(
    app: QuickDropApplication,
    initialDeepLinkUri: String?,
    onConsumeDeepLink: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var selectedTab by rememberSaveable { mutableStateOf(BottomNavTab.HOME) }
    var activeScreen by remember { mutableStateOf<ActiveScreen>(ActiveScreen.MainTabs) }
    var showQuitQuizConfirmDialog by remember { mutableStateOf(false) }

    val subjects by app.localStore.observeActiveSubjects().collectAsState(initial = emptyList())
    val activeQuizSession by app.localStore.observeActiveSession().collectAsState(initial = null)
    val preferences by app.localStore.observePreferences().collectAsState(initial = UserPreferences())

    // Handle deep links from notifications (`quickdrop://open/class?subjectId=...`)
    LaunchedEffect(initialDeepLinkUri) {
        if (!initialDeepLinkUri.isNullOrBlank()) {
            if (initialDeepLinkUri.contains("subjectId=")) {
                val subjId = initialDeepLinkUri.substringAfter("subjectId=").substringBefore("&")
                if (subjId.isNotBlank()) {
                    activeScreen = ActiveScreen.QuizSetup(primarySubjectId = subjId)
                }
            } else if (initialDeepLinkUri.contains("drop")) {
                selectedTab = BottomNavTab.STUDY
                activeScreen = ActiveScreen.MainTabs
            }
            onConsumeDeepLink()
        }
    }

    // Prevent silent loss of quiz answers on system Back press
    BackHandler(enabled = activeScreen != ActiveScreen.MainTabs) {
        when (activeScreen) {
            is ActiveScreen.QuizRunner -> showQuitQuizConfirmDialog = true
            else -> activeScreen = ActiveScreen.MainTabs
        }
    }

    if (showQuitQuizConfirmDialog && activeQuizSession != null) {
        AlertDialog(
            onDismissRequest = { showQuitQuizConfirmDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Schedule, contentDescription = null, tint = QuickDropColors.Amber)
                    Text("Pause or Discard Quiz?", color = QuickDropColors.TextPrimary, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Text(
                    "Your answered questions are saved locally. You can resume later from Home or Study, or discard this session without affecting your topic mastery.",
                    color = QuickDropColors.TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showQuitQuizConfirmDialog = false
                        activeScreen = ActiveScreen.MainTabs
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = QuickDropColors.Emerald)
                ) {
                    Text("Save & Resume Later", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        val sessionId = activeQuizSession?.id
                        showQuitQuizConfirmDialog = false
                        if (sessionId != null) {
                            scope.launch {
                                app.localStore.discardQuizSession(sessionId)
                                activeScreen = ActiveScreen.MainTabs
                            }
                        }
                    }
                ) {
                    Text("Discard Session", color = QuickDropColors.RoseLight)
                }
            }
        )
    }

    Scaffold(
        containerColor = QuickDropColors.CosmicBackground,
        topBar = {
            // Sleek Modern Mobile Top App Bar with safe system status bar padding
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(QuickDropColors.CosmicSurface)
                    .statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(QuickDropColors.EmeraldGradient),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = "Logo",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "Quick Drop",
                                    color = QuickDropColors.TextPrimary,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0x3310B981))
                                        .padding(horizontal = 5.dp, vertical = 1.dp)
                                ) {
                                    Text("PRO", color = QuickDropColors.EmeraldLight, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            Text(
                                text = when (activeScreen) {
                                    ActiveScreen.MainTabs -> selectedTab.label
                                    is ActiveScreen.SubjectDetail -> "Course Syllabus"
                                    is ActiveScreen.LectureEditor -> "Lecture Editor"
                                    is ActiveScreen.MaterialReview -> "Review Extracted Drops"
                                    is ActiveScreen.QuizSetup -> "Configure Quiz Sprint"
                                    is ActiveScreen.QuizRunner -> "Active Recall Session"
                                    is ActiveScreen.QuizResults -> "Quiz Results"
                                    ActiveScreen.Settings -> "Settings & Privacy"
                                },
                                color = QuickDropColors.TextMuted,
                                fontSize = 11.sp,
                                maxLines = 1
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (activeScreen != ActiveScreen.MainTabs) {
                            OutlinedButton(
                                onClick = {
                                    if (activeScreen is ActiveScreen.QuizRunner) {
                                        showQuitQuizConfirmDialog = true
                                    } else {
                                        activeScreen = ActiveScreen.MainTabs
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(32.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                            ) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Back", modifier = Modifier.size(13.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Back", fontSize = 11.sp)
                            }
                        }

                        IconButton(
                            onClick = { activeScreen = ActiveScreen.Settings },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = if (activeScreen is ActiveScreen.Settings) QuickDropColors.Emerald else QuickDropColors.TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(QuickDropColors.CosmicBorder)
                )
            }
        },
        bottomBar = {
            if (activeScreen == ActiveScreen.MainTabs) {
                NavigationBar(
                    containerColor = QuickDropColors.CosmicSurface,
                    tonalElevation = 8.dp,
                    modifier = Modifier.border(1.dp, QuickDropColors.CosmicBorder)
                ) {
                    BottomNavTab.entries.forEach { tab ->
                        val isSelected = selectedTab == tab
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = { selectedTab = tab },
                            icon = {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = tab.label,
                                    tint = if (isSelected) QuickDropColors.Emerald else QuickDropColors.TextMuted,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            label = {
                                Text(
                                    text = tab.label,
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) QuickDropColors.EmeraldLight else QuickDropColors.TextMuted,
                                    maxLines = 1
                                )
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            when (val screen = activeScreen) {
                ActiveScreen.MainTabs -> {
                    when (selectedTab) {
                        BottomNavTab.HOME -> HomeScreen(
                            app = app,
                            onOpenSubject = { activeScreen = ActiveScreen.SubjectDetail(it) },
                            onOpenQuizSetup = { activeScreen = ActiveScreen.QuizSetup(it) },
                            onResumeSession = { activeScreen = ActiveScreen.QuizRunner(it) },
                            onAddLecture = { activeScreen = ActiveScreen.LectureEditor(it, null) },
                            onOpenTimetable = { selectedTab = BottomNavTab.TIMETABLE },
                            onOpenUpload = { selectedTab = BottomNavTab.INGESTION }
                        )

                        BottomNavTab.TIMETABLE -> TimetableScreen(
                            app = app,
                            onOpenSubject = { activeScreen = ActiveScreen.SubjectDetail(it) }
                        )

                        BottomNavTab.STUDY -> StudyScreen(
                            app = app,
                            onStartQuizSetup = { activeScreen = ActiveScreen.QuizSetup(it) },
                            onResumeQuiz = { activeScreen = ActiveScreen.QuizRunner(it) }
                        )

                        BottomNavTab.INGESTION -> AndroidFileIngestionStudio(
                            store = app.localStore,
                            clock = app.clock,
                            geminiClient = app.activeGeminiClient(),
                            subjects = subjects,
                            onReviewLecture = { activeScreen = ActiveScreen.MaterialReview(it) }
                        )

                        BottomNavTab.PROGRESS -> ProgressScreen(app = app)
                    }
                }

                is ActiveScreen.SubjectDetail -> SubjectDetailScreen(
                    app = app,
                    subjectId = screen.subjectId,
                    onBack = { activeScreen = ActiveScreen.MainTabs },
                    onAddLecture = { activeScreen = ActiveScreen.LectureEditor(screen.subjectId, null) },
                    onReviewMaterial = { activeScreen = ActiveScreen.MaterialReview(it) },
                    onPrepareQuiz = { activeScreen = ActiveScreen.QuizSetup(screen.subjectId) }
                )

                is ActiveScreen.LectureEditor -> LectureEditorScreen(
                    app = app,
                    subjectId = screen.subjectId,
                    lectureId = screen.lectureId,
                    onBack = { activeScreen = ActiveScreen.SubjectDetail(screen.subjectId) },
                    onOpenMaterialReview = { activeScreen = ActiveScreen.MaterialReview(it) }
                )

                is ActiveScreen.MaterialReview -> MaterialReviewScreen(
                    app = app,
                    lectureId = screen.lectureId,
                    onBack = { activeScreen = ActiveScreen.MainTabs }
                )

                is ActiveScreen.QuizSetup -> QuizSetupScreen(
                    app = app,
                    primarySubjectId = screen.primarySubjectId,
                    preferences = preferences,
                    onBack = { activeScreen = ActiveScreen.MainTabs },
                    onSessionStarted = { activeScreen = ActiveScreen.QuizRunner(it) }
                )

                is ActiveScreen.QuizRunner -> QuizRunnerScreen(
                    app = app,
                    sessionId = screen.sessionId,
                    onRequestQuit = { showQuitQuizConfirmDialog = true },
                    onQuizCompleted = { activeScreen = ActiveScreen.QuizResults(it) }
                )

                is ActiveScreen.QuizResults -> QuizResultsScreen(
                    app = app,
                    sessionId = screen.sessionId,
                    onDone = { activeScreen = ActiveScreen.MainTabs },
                    onRetryDueOrMissed = { subjId -> activeScreen = ActiveScreen.QuizSetup(subjId) }
                )

                ActiveScreen.Settings -> SettingsScreen(
                    app = app,
                    preferences = preferences,
                    onBack = { activeScreen = ActiveScreen.MainTabs }
                )
            }
        }
    }
}

// =========================================================================
// 1. MOBILE HOME DASHBOARD (Hero Card, Quick Actions, 2x2 Stats, Flip Card)
// =========================================================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HomeScreen(
    app: QuickDropApplication,
    onOpenSubject: (String) -> Unit,
    onOpenQuizSetup: (String?) -> Unit,
    onResumeSession: (String) -> Unit,
    onAddLecture: (String) -> Unit,
    onOpenTimetable: () -> Unit,
    onOpenUpload: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val preferences by app.localStore.observePreferences().collectAsState(initial = com.quickdrop.core.model.UserPreferences())
    val completedSessions by app.localStore.observeCompletedSessions().collectAsState(initial = emptyList())
    val subjects by app.localStore.observeActiveSubjects().collectAsState(initial = emptyList())
    val activeSession by app.localStore.observeActiveSession().collectAsState(initial = null)
    val reviewStates by app.localStore.observeTopicReviewStates().collectAsState(initial = emptyMap())
    val drops by app.localStore.observeApprovedDrops().collectAsState(initial = emptyList())
    var upcomingOccurrences by remember { mutableStateOf<List<ClassOccurrence>>(emptyList()) }
    var showNewSubjectDialog by remember { mutableStateOf(false) }

    LaunchedEffect(subjects) {
        upcomingOccurrences = app.localStore.getUpcomingOccurrences(daysAhead = 7)
    }

    val nextOcc = upcomingOccurrences.firstOrNull()
    val nextSubj = subjects.find { it.id == nextOcc?.subjectId }

    val dueCount = reviewStates.values.count { state ->
        val due = state.dueAtEpochMs
        due != null && due <= app.clock.now().toEpochMilli()
    }
    val stableCount = reviewStates.values.count {
        SpacedRepetitionScheduler.resolveMasteryStatus(it, app.clock) == TopicMasteryStatus.STABLE
    }

    val realStreakDays = remember(completedSessions) {
        if (completedSessions.isEmpty()) 0
        else {
            val sessionDates = completedSessions
                .mapNotNull { session ->
                    session.endedAtEpochMs?.let { ms ->
                        java.time.Instant.ofEpochMilli(ms).atZone(app.clock.zoneId()).toLocalDate().toString()
                    }
                }
                .distinct()
                .sortedDescending()
                .toSet()

            val today = app.clock.localDate()
            var currentDay = today
            var streak = 0

            // If not completed today, check if active yesterday
            if (!sessionDates.contains(currentDay.toString())) {
                currentDay = currentDay.minusDays(1)
            }

            while (sessionDates.contains(currentDay.toString())) {
                streak++
                currentDay = currentDay.minusDays(1)
            }
            streak
        }
    }

    val realReadinessPercent = remember(reviewStates, completedSessions, stableCount) {
        val totalTopics = reviewStates.size
        val totalAnswered = completedSessions.sumOf { it.answeredCount }
        val totalCorrect = completedSessions.sumOf { it.correctCount }

        when {
            totalTopics > 0 && totalAnswered > 0 -> {
                val masteryRatio = (stableCount * 100) / totalTopics
                val accuracyRatio = (totalCorrect * 100) / totalAnswered
                ((masteryRatio + accuracyRatio) / 2).coerceIn(0, 100)
            }
            totalTopics > 0 -> ((stableCount * 100) / totalTopics).coerceIn(0, 100)
            totalAnswered > 0 -> ((totalCorrect * 100) / totalAnswered).coerceIn(0, 100)
            else -> 0
        }
    }

    val studentDisplayName = preferences.displayName.trim().ifBlank { "Scholar" }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Hero Readiness Card
        item {
            HeroReadinessCard(
                studentName = studentDisplayName,
                streakDays = realStreakDays,
                readinessPercent = realReadinessPercent,
                nextClassTitle = nextSubj?.name?.let { "$it (${nextOcc?.startZoned?.toLocalTime()})" },
                nextClassCountdown = if (nextOcc != null) "TODAY" else null,
                nextClassLocation = nextOcc?.location,
                onLaunchPrepQuiz = { onOpenQuizSetup(nextSubj?.id) }
            )
        }

        // Quick Action Shortcuts Bar
        item {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onOpenTimetable,
                    colors = ButtonDefaults.buttonColors(containerColor = QuickDropColors.CosmicSurfaceElevated),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.height(40.dp)
                ) {
                    Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = QuickDropColors.Emerald, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Upload Timetable", color = QuickDropColors.TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }

                Button(
                    onClick = onOpenUpload,
                    colors = ButtonDefaults.buttonColors(containerColor = QuickDropColors.CosmicSurfaceElevated),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.height(40.dp)
                ) {
                    Icon(Icons.Default.FileUpload, contentDescription = null, tint = QuickDropColors.Violet, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Upload Slides", color = QuickDropColors.TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }

                Button(
                    onClick = { onOpenQuizSetup(null) },
                    colors = ButtonDefaults.buttonColors(containerColor = QuickDropColors.CosmicSurfaceElevated),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.height(40.dp)
                ) {
                    Icon(Icons.Default.Bolt, contentDescription = null, tint = QuickDropColors.Amber, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("10-Q Sprint", color = QuickDropColors.TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }

                OutlinedButton(
                    onClick = { showNewSubjectDialog = true },
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.height(40.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("+ Course", fontSize = 11.sp, maxLines = 1)
                }
            }
        }

        // Active Quiz Resume Alert (if quiz is currently paused)
        if (activeSession != null) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            Brush.linearGradient(listOf(Color(0xFF2E1065), Color(0xFF1E1B4B)))
                        )
                        .border(1.dp, QuickDropColors.Violet.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(QuickDropColors.Violet.copy(alpha = 0.3f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Bolt, contentDescription = null, tint = QuickDropColors.VioletLight)
                            }
                            Column {
                                Text("Quiz in Progress", color = QuickDropColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("Question ${activeSession!!.currentQuestionIndex + 1} of ${activeSession!!.itemIdsInOrder.size} • Answers saved locally", color = QuickDropColors.VioletLight, fontSize = 11.sp)
                            }
                        }

                        Button(
                            onClick = { onResumeSession(activeSession!!.id) },
                            colors = ButtonDefaults.buttonColors(containerColor = QuickDropColors.Violet),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Resume", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // 4 Key Metric Stats (2x2 Grid)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatMetricCard(
                        label = "Enrolled Courses",
                        value = "${subjects.size}",
                        subtext = "Active Term",
                        icon = Icons.Default.School,
                        iconColor = QuickDropColors.Cyan,
                        modifier = Modifier.weight(1f)
                    )
                    StatMetricCard(
                        label = "Due for Review",
                        value = "$dueCount",
                        subtext = "Needs attention",
                        icon = Icons.Default.Schedule,
                        iconColor = QuickDropColors.Amber,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatMetricCard(
                        label = "Mastered Topics",
                        value = "$stableCount",
                        subtext = "Stable recall",
                        icon = Icons.Default.CheckCircle,
                        iconColor = QuickDropColors.Emerald,
                        modifier = Modifier.weight(1f)
                    )
                    StatMetricCard(
                        label = "Study Streak",
                        value = "$realStreakDays ${if (realStreakDays == 1) "Day" else "Days"}",
                        subtext = if (realStreakDays > 0) "Keep momentum!" else "Complete a quiz",
                        icon = Icons.Default.LocalFireDepartment,
                        iconColor = QuickDropColors.Rose,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Drop of the Day (Interactive 3D Flip Card directly on Home!)
        if (drops.isNotEmpty()) {
            val (featuredDrop, dropState) = drops.first()
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = QuickDropColors.AmberLight, modifier = Modifier.size(16.dp))
                            Text(
                                text = "Drop of the Day • Daily Active Recall",
                                color = QuickDropColors.TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                        Text(
                            text = "Tap to flip",
                            color = QuickDropColors.TextMuted,
                            fontSize = 11.sp
                        )
                    }

                    InteractiveFlipCard(
                        topicName = featuredDrop.evidenceQuote.take(22).ifBlank { "Core Concept" },
                        prompt = featuredDrop.promptOrBody,
                        explanation = featuredDrop.explanation,
                        evidenceQuote = featuredDrop.evidenceQuote,
                        anchorText = "${featuredDrop.anchorKind ?: "PAGE"} ${featuredDrop.anchorIndex ?: 1}",
                        isSaved = dropState.visibilityState == DropVisibilityState.SAVED,
                        onToggleSave = {
                            val next = if (dropState.visibilityState == DropVisibilityState.SAVED) DropVisibilityState.UNSEEN else DropVisibilityState.SAVED
                            scope.launch { app.localStore.updateDropState(featuredDrop.id, next) }
                        },
                        onRateConfidence = { rating ->
                            scope.launch {
                                val nextState = when (rating) {
                                    "HARD" -> DropVisibilityState.REVIEW
                                    "GOOD" -> DropVisibilityState.UNDERSTOOD
                                    else -> DropVisibilityState.SAVED
                                }
                                app.localStore.updateDropState(featuredDrop.id, nextState)
                            }
                        }
                    )
                }
            }
        }

        // Active Course Syllabus Cards
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Enrolled Courses (${subjects.size})",
                    color = QuickDropColors.TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                TextButton(onClick = { showNewSubjectDialog = true }) {
                    Text("+ Add Course", color = QuickDropColors.EmeraldLight, fontSize = 12.sp)
                }
            }
        }

        items(subjects, key = { it.id }) { subj ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(QuickDropColors.CosmicSurfaceElevated)
                    .border(1.dp, QuickDropColors.CosmicBorder, RoundedCornerShape(16.dp))
                    .padding(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        when (subj.colorKey) {
                                            "violet" -> Color(0x338B5CF6)
                                            "cyan" -> Color(0x3306B6D4)
                                            "rose" -> Color(0x33F43F5E)
                                            else -> Color(0x3310B981)
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = when (subj.iconKey) {
                                        "cloud" -> Icons.Default.Science
                                        "security" -> Icons.Default.Psychology
                                        else -> Icons.Default.Code
                                    },
                                    contentDescription = null,
                                    tint = when (subj.colorKey) {
                                        "violet" -> QuickDropColors.VioletLight
                                        "cyan" -> QuickDropColors.CyanLight
                                        "rose" -> QuickDropColors.RoseLight
                                        else -> QuickDropColors.EmeraldLight
                                    },
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = subj.name,
                                    color = QuickDropColors.TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "${subj.term ?: "First Semester 2026/2027"} • ${subj.instructor ?: "Faculty"}",
                                    color = QuickDropColors.TextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { onAddLecture(subj.id) },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                            modifier = Modifier.weight(1f).height(38.dp)
                        ) {
                            Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(13.dp), tint = QuickDropColors.VioletLight)
                            Spacer(Modifier.width(4.dp))
                            Text("+ Upload", fontSize = 11.sp, color = QuickDropColors.VioletLight, maxLines = 1)
                        }

                        OutlinedButton(
                            onClick = { onOpenSubject(subj.id) },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                            modifier = Modifier.weight(1f).height(38.dp)
                        ) {
                            Text("Lectures", fontSize = 11.sp, maxLines = 1)
                        }

                        Button(
                            onClick = { onOpenQuizSetup(subj.id) },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = QuickDropColors.Emerald),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                            modifier = Modifier.weight(1f).height(38.dp)
                        ) {
                            Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(13.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Quiz", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }
                }
            }
        }
    }

    if (showNewSubjectDialog) {
        var name by remember { mutableStateOf("") }
        var instructor by remember { mutableStateOf("") }
        var term by remember { mutableStateOf("First Semester 2026/2027") }

        AlertDialog(
            onDismissRequest = { showNewSubjectDialog = false },
            title = { Text("Add Course", fontWeight = FontWeight.Bold, color = QuickDropColors.TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Course Name (e.g. SEN 201 - Software Eng)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = instructor,
                        onValueChange = { instructor = it },
                        label = { Text("Instructor (Optional)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = term,
                        onValueChange = { term = it },
                        label = { Text("Academic Term") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (name.isNotBlank()) {
                            scope.launch {
                                app.localStore.createOrUpdateSubject(
                                    name = name,
                                    colorKey = "cyan",
                                    iconKey = "code",
                                    term = term,
                                    instructor = instructor
                                )
                                showNewSubjectDialog = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = QuickDropColors.Emerald)
                ) {
                    Text("Create Course")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewSubjectDialog = false }) { Text("Cancel") }
            }
        )
    }
}

// =========================================================================
// 2. MOBILE TIMETABLE SCREEN (Obsidian Matrix + Day Cards View + Modals)
// =========================================================================

@Composable
private fun TimetableScreen(
    app: QuickDropApplication,
    onOpenSubject: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val subjects by app.localStore.observeActiveSubjects().collectAsState(initial = emptyList())
    val slots by app.localStore.observeSlots().collectAsState(initial = emptyList())

    var showUploadModal by remember { mutableStateOf(false) }
    var showAddSlotModal by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        WeeklyTimetableBoard(
            clock = app.clock,
            subjects = subjects,
            slots = slots,
            onUploadClick = { showUploadModal = true },
            onLoadDefaultTemplate = {
                scope.launch {
                    importRecognizedSlotsIntoStore(getCybersecurity200LSlots(), app.localStore, subjects, app.clock)
                }
            },
            onOpenSubject = onOpenSubject
        )
    }

    if (showUploadModal) {
        AndroidUploadTimetableFileModal(
            store = app.localStore,
            clock = app.clock,
            subjects = subjects,
            onDismiss = { showUploadModal = false },
            onImportComplete = { showUploadModal = false }
        )
    }

    if (showAddSlotModal) {
        AndroidAddClassSlotModal(
            store = app.localStore,
            clock = app.clock,
            subjects = subjects,
            onDismiss = { showAddSlotModal = false },
            onSlotAdded = { showAddSlotModal = false }
        )
    }
}

// =========================================================================
// 3. MOBILE STUDY & DROPS FEED SCREEN (Interactive 3D Flip Cards)
// =========================================================================

@Composable
private fun StudyScreen(
    app: QuickDropApplication,
    onStartQuizSetup: (String?) -> Unit,
    onResumeQuiz: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val activeSession by app.localStore.observeActiveSession().collectAsState(initial = null)
    val drops by app.localStore.observeApprovedDrops().collectAsState(initial = emptyList())
    var selectedFilter by rememberSaveable { mutableStateOf("ALL") }

    val filteredDrops = remember(drops, selectedFilter) {
        when (selectedFilter) {
            "SAVED" -> drops.filter { it.second.visibilityState == DropVisibilityState.SAVED }
            "UNDERSTOOD" -> drops.filter { it.second.visibilityState == DropVisibilityState.UNDERSTOOD }
            "REVIEW" -> drops.filter { it.second.visibilityState == DropVisibilityState.REVIEW }
            else -> drops
        }
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Active Recall & Micro-Drops",
                        style = MaterialTheme.typography.titleLarge,
                        color = QuickDropColors.TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Tap cards to flip & inspect verified quotes",
                        style = MaterialTheme.typography.bodySmall,
                        color = QuickDropColors.TextSecondary
                    )
                }

                Button(
                    onClick = { onStartQuizSetup(null) },
                    colors = ButtonDefaults.buttonColors(containerColor = QuickDropColors.Emerald),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                ) {
                    Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Start 10-Question Recall Sprint", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }

        // Filter Pills (Responsive & Horizontally Scrollable)
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "ALL" to "All Drops (${drops.size})",
                    "SAVED" to "Saved (${drops.count { it.second.visibilityState == DropVisibilityState.SAVED }})",
                    "UNDERSTOOD" to "Understood (${drops.count { it.second.visibilityState == DropVisibilityState.UNDERSTOOD }})",
                    "REVIEW" to "Review Due (${drops.count { it.second.visibilityState == DropVisibilityState.REVIEW }})"
                ).forEach { (key, label) ->
                    val isSelected = selectedFilter == key
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) QuickDropColors.CosmicSurfaceHighlight else QuickDropColors.CosmicSurfaceElevated)
                            .border(1.dp, if (isSelected) QuickDropColors.Emerald else QuickDropColors.CosmicBorder, RoundedCornerShape(8.dp))
                            .clickable { selectedFilter = key }
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) QuickDropColors.EmeraldLight else QuickDropColors.TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        if (filteredDrops.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(QuickDropColors.CosmicSurfaceElevated)
                        .padding(28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.MenuBook, contentDescription = null, tint = QuickDropColors.TextMuted, modifier = Modifier.size(36.dp))
                        Text("No Drops matching this filter", color = QuickDropColors.TextSecondary, fontWeight = FontWeight.Bold)
                        Text("Ingest new lectures in the Upload tab to generate drops", color = QuickDropColors.TextMuted, fontSize = 12.sp)
                    }
                }
            }
        } else {
            items(filteredDrops, key = { it.first.id }) { (drop, state) ->
                InteractiveFlipCard(
                    topicName = drop.evidenceQuote.take(24).ifBlank { "Core Concept" },
                    prompt = drop.promptOrBody,
                    explanation = drop.explanation,
                    evidenceQuote = drop.evidenceQuote,
                    anchorText = "${drop.anchorKind ?: "PAGE"} ${drop.anchorIndex ?: 1}",
                    isSaved = state.visibilityState == DropVisibilityState.SAVED,
                    onToggleSave = {
                        val next = if (state.visibilityState == DropVisibilityState.SAVED) DropVisibilityState.UNSEEN else DropVisibilityState.SAVED
                        scope.launch { app.localStore.updateDropState(drop.id, next) }
                    },
                    onRateConfidence = { rating ->
                        scope.launch {
                            val nextState = when (rating) {
                                "HARD" -> DropVisibilityState.REVIEW
                                "GOOD" -> DropVisibilityState.UNDERSTOOD
                                else -> DropVisibilityState.SAVED
                            }
                            app.localStore.updateDropState(drop.id, nextState)
                        }
                    }
                )
            }
        }
    }
}

// =========================================================================
// 4. MOBILE PROGRESS & ANALYTICS SCREEN
// =========================================================================

@Composable
private fun ProgressScreen(app: QuickDropApplication) {
    val completedSessions by app.localStore.observeCompletedSessions().collectAsState(initial = emptyList())
    val reviewStates by app.localStore.observeTopicReviewStates().collectAsState(initial = emptyMap())

    val totalCorrect = completedSessions.sumOf { it.correctCount }
    val totalAnswered = completedSessions.sumOf { it.answeredCount }
    val overallAccuracy = if (totalAnswered > 0) (totalCorrect * 100) / totalAnswered else 0

    val newCount = reviewStates.values.count { SpacedRepetitionScheduler.resolveMasteryStatus(it, app.clock) == TopicMasteryStatus.NEW }
    val learningCount = reviewStates.values.count { SpacedRepetitionScheduler.resolveMasteryStatus(it, app.clock) == TopicMasteryStatus.LEARNING }
    val reviewCount = reviewStates.values.count { SpacedRepetitionScheduler.resolveMasteryStatus(it, app.clock) == TopicMasteryStatus.DUE }
    val stableCount = reviewStates.values.count { SpacedRepetitionScheduler.resolveMasteryStatus(it, app.clock) == TopicMasteryStatus.STABLE }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Spaced Repetition & Recall Analytics", style = MaterialTheme.typography.titleLarge, color = QuickDropColors.TextPrimary, fontWeight = FontWeight.Bold)
                Text("SuperMemo SM-2 mastery estimates based on your practice latency and confidence.", style = MaterialTheme.typography.bodySmall, color = QuickDropColors.TextSecondary)
            }
        }

        // Hero Accuracy Card
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(QuickDropColors.CosmicSurfaceElevated)
                    .border(1.dp, QuickDropColors.CosmicBorder, RoundedCornerShape(18.dp))
                    .padding(18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Cumulative Accuracy", color = QuickDropColors.TextSecondary, fontSize = 12.sp)
                        Text("$overallAccuracy%", color = QuickDropColors.TextPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp)
                        Text("$totalCorrect correct of $totalAnswered answered • ${completedSessions.size} sessions", color = QuickDropColors.TextMuted, fontSize = 11.sp)
                    }

                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(64.dp)) {
                        CircularProgressIndicator(
                            progress = { (overallAccuracy / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier.size(64.dp),
                            color = QuickDropColors.Emerald,
                            trackColor = Color(0x33FFFFFF),
                            strokeWidth = 6.dp
                        )
                        Icon(Icons.Default.Insights, contentDescription = null, tint = QuickDropColors.EmeraldLight, modifier = Modifier.size(24.dp))
                    }
                }
            }
        }

        // SM-2 Mastery Distribution
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(QuickDropColors.CosmicSurfaceElevated)
                    .border(1.dp, QuickDropColors.CosmicBorder, RoundedCornerShape(18.dp))
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Topic Mastery Distribution (SM-2)", color = QuickDropColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MasteryBar("Stable", stableCount, QuickDropColors.Emerald, Modifier.weight(1f))
                        MasteryBar("Learning", learningCount, QuickDropColors.Cyan, Modifier.weight(1f))
                        MasteryBar("Review", reviewCount, QuickDropColors.Amber, Modifier.weight(1f))
                        MasteryBar("New", newCount, QuickDropColors.Violet, Modifier.weight(1f))
                    }
                }
            }
        }

        item {
            Text("Active Topic Retention Health (${reviewStates.size})", color = QuickDropColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }

        if (reviewStates.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(QuickDropColors.CosmicSurfaceElevated)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Complete your first quiz sprint to generate topic mastery metrics.", color = QuickDropColors.TextMuted, fontSize = 12.sp)
                }
            }
        } else {
            items(reviewStates.values.toList(), key = { it.topicId }) { state ->
                val status = SpacedRepetitionScheduler.resolveMasteryStatus(state, app.clock)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(QuickDropColors.CosmicSurfaceElevated)
                        .border(1.dp, QuickDropColors.CosmicBorder, RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Topic: ${state.topicId.take(14)}", color = QuickDropColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("Stability: ${"%.1f".format(state.stabilityDays)}d • Correct: ${state.correctCount}/${state.attemptCount} • Lapses: ${state.lapses}", color = QuickDropColors.TextSecondary, fontSize = 11.sp)
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    when (status) {
                                        TopicMasteryStatus.STABLE -> Color(0x3310B981)
                                        TopicMasteryStatus.LEARNING -> Color(0x3306B6D4)
                                        TopicMasteryStatus.DUE -> Color(0x33F59E0B)
                                        else -> Color(0x338B5CF6)
                                    }
                                )
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = status.name,
                                color = when (status) {
                                    TopicMasteryStatus.STABLE -> QuickDropColors.EmeraldLight
                                    TopicMasteryStatus.LEARNING -> QuickDropColors.CyanLight
                                    TopicMasteryStatus.DUE -> QuickDropColors.AmberLight
                                    else -> QuickDropColors.VioletLight
                                },
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MasteryBar(label: String, count: Int, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.15f))
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
            .padding(10.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("$count", color = color, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
            Text(label, color = QuickDropColors.TextSecondary, fontSize = 10.sp)
        }
    }
}

// =========================================================================
// 5. SUBJECT DETAIL SCREEN
// =========================================================================

@Composable
private fun SubjectDetailScreen(
    app: QuickDropApplication,
    subjectId: String,
    onBack: () -> Unit,
    onAddLecture: () -> Unit,
    onReviewMaterial: (String) -> Unit,
    onPrepareQuiz: () -> Unit
) {
    val subjects by app.localStore.observeActiveSubjects().collectAsState(initial = emptyList())
    val lectures by app.localStore.observeLecturesForSubject(subjectId).collectAsState(initial = emptyList())
    val topics by app.localStore.observeTopicsForSubject(subjectId).collectAsState(initial = emptyList())
    val subject = subjects.find { it.id == subjectId }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(subject?.name ?: "Course Syllabus", style = MaterialTheme.typography.titleLarge, color = QuickDropColors.TextPrimary, fontWeight = FontWeight.Bold)
                    Text("${subject?.term ?: "First Semester"} • ${subject?.instructor ?: "Faculty"}", style = MaterialTheme.typography.bodySmall, color = QuickDropColors.TextSecondary)
                }

                Spacer(Modifier.width(8.dp))

                OutlinedButton(
                    onClick = onBack,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Text("Back", fontSize = 12.sp, maxLines = 1)
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onPrepareQuiz,
                    colors = ButtonDefaults.buttonColors(containerColor = QuickDropColors.Emerald),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.weight(1f).height(42.dp)
                ) {
                    Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Prepare Quiz", fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
                }

                Button(
                    onClick = onAddLecture,
                    colors = ButtonDefaults.buttonColors(containerColor = QuickDropColors.Violet),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.weight(1.2f).height(42.dp)
                ) {
                    Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("+ Add Material", fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
                }
            }
        }

        item {
            Text("Lectures & Materials (${lectures.size})", color = QuickDropColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }

        if (lectures.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(QuickDropColors.CosmicSurfaceElevated)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("No lecture materials uploaded yet", color = QuickDropColors.TextSecondary, fontWeight = FontWeight.Bold)
                        Text("Tap '+ Add Material' above or upload lecture slides", color = QuickDropColors.TextMuted, fontSize = 12.sp)
                    }
                }
            }
        } else {
            items(lectures, key = { it.id }) { lecture ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(QuickDropColors.CosmicSurfaceElevated)
                        .border(1.dp, QuickDropColors.CosmicBorder, RoundedCornerShape(14.dp))
                        .padding(14.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(lecture.title, color = QuickDropColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(lecture.localDateIso, color = QuickDropColors.TextMuted, fontSize = 11.sp)
                        }

                        if (!lecture.topicLabel.isNullOrBlank()) {
                            Text("Topic: ${lecture.topicLabel}", color = QuickDropColors.CyanLight, fontSize = 11.sp)
                        }

                        if (lecture.notes.isNotBlank()) {
                            Text(lecture.notes.take(120), color = QuickDropColors.TextSecondary, fontSize = 12.sp)
                        }

                        Button(
                            onClick = { onReviewMaterial(lecture.id) },
                            colors = ButtonDefaults.buttonColors(containerColor = QuickDropColors.CosmicSurfaceHighlight),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().height(36.dp)
                        ) {
                            Text("Review Extracted Items & Drops →", fontSize = 11.sp, color = QuickDropColors.TextPrimary)
                        }
                    }
                }
            }
        }
    }
}

// =========================================================================
// 6. LECTURE EDITOR SCREEN
// =========================================================================

@Composable
private fun LectureEditorScreen(
    app: QuickDropApplication,
    subjectId: String,
    lectureId: String?,
    onBack: () -> Unit,
    onOpenMaterialReview: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var title by rememberSaveable { mutableStateOf("") }
    var topicLabel by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var prompt by rememberSaveable { mutableStateOf("") }
    var explanation by rememberSaveable { mutableStateOf("") }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Add Lecture Material", style = MaterialTheme.typography.titleLarge, color = QuickDropColors.TextPrimary, fontWeight = FontWeight.Bold)
                OutlinedButton(onClick = onBack, shape = RoundedCornerShape(10.dp)) { Text("Cancel") }
            }
        }

        item {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Lecture Title (e.g. Virtual Memory Architecture)") },
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            OutlinedTextField(
                value = topicLabel,
                onValueChange = { topicLabel = it },
                label = { Text("Topic Label (Optional)") },
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Lecture Summary / Slide Notes") },
                modifier = Modifier.fillMaxWidth().height(120.dp)
            )
        }

        item {
            Text("Create Quick Practice Flashcard:", color = QuickDropColors.TextSecondary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }

        item {
            OutlinedTextField(
                value = prompt,
                onValueChange = { prompt = it },
                label = { Text("Question or Active Recall Prompt") },
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            OutlinedTextField(
                value = explanation,
                onValueChange = { explanation = it },
                label = { Text("Answer / Rationale") },
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            Button(
                enabled = title.isNotBlank(),
                onClick = {
                    scope.launch {
                        val lec = app.localStore.createOrUpdateLecture(
                            id = lectureId,
                            subjectId = subjectId,
                            title = title,
                            localDateIso = app.clock.localDate().toString(),
                            topicLabel = topicLabel.ifBlank { title },
                            notes = notes
                        )
                        if (prompt.isNotBlank()) {
                            app.localStore.createManualItem(
                                subjectId = subjectId,
                                topicName = topicLabel.ifBlank { title },
                                lectureId = lec.id,
                                kind = LearningItemKind.DROP,
                                promptOrBody = prompt,
                                explanation = explanation.ifBlank { "Core concept from $title" },
                                difficulty = Difficulty.MEDIUM
                            )
                        }
                        onOpenMaterialReview(lec.id)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = QuickDropColors.Emerald),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(46.dp)
            ) {
                Text("Save Lecture & Review", fontWeight = FontWeight.Bold)
            }
        }
    }
}

// =========================================================================
// 7. MATERIAL REVIEW SCREEN
// =========================================================================

@Composable
private fun MaterialReviewScreen(
    app: QuickDropApplication,
    lectureId: String,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val lecture by app.localStore.observeLectureById(lectureId).collectAsState(initial = null)
    val drafts by app.localStore.observeDraftsForLecture(lectureId).collectAsState(initial = emptyList())
    val itemsList by app.localStore.observeLearningItemsForLecture(lectureId).collectAsState(initial = emptyList())

    LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(lecture?.title ?: "Lecture Review", style = MaterialTheme.typography.titleLarge, color = QuickDropColors.TextPrimary, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text("Approved Drops: ${itemsList.size} • Pending Drafts: ${drafts.size}", color = QuickDropColors.TextSecondary, fontSize = 11.sp, maxLines = 1)
                }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(
                    onClick = onBack,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Text("Done", fontSize = 12.sp, maxLines = 1)
                }
            }
        }

        if (drafts.isNotEmpty()) {
            item {
                Text("Pending AI Drafts (Tap to Approve):", color = QuickDropColors.AmberLight, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            items(drafts, key = { it.id }) { draft ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(QuickDropColors.CosmicSurfaceElevated)
                        .border(1.dp, QuickDropColors.Amber.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                        .padding(14.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(draft.promptOrBody, color = QuickDropColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("Explanation: ${draft.explanation}", color = QuickDropColors.TextSecondary, fontSize = 11.sp)
                        if (draft.evidenceQuote.isNotBlank()) {
                            Text("Evidence: \"${draft.evidenceQuote}\"", color = QuickDropColors.EmeraldLight, fontSize = 10.sp)
                        }
                        Button(
                            onClick = { scope.launch { app.localStore.approveDraftItem(draft.id) } },
                            colors = ButtonDefaults.buttonColors(containerColor = QuickDropColors.Emerald),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text("Approve into Drops", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        item {
            Text("Approved Active Recall Items (${itemsList.size}):", color = QuickDropColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }

        if (itemsList.isEmpty()) {
            item {
                Text("No items approved yet.", color = QuickDropColors.TextMuted, fontSize = 12.sp)
            }
        } else {
            items(itemsList, key = { it.id }) { item ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(QuickDropColors.CosmicSurfaceElevated)
                        .border(1.dp, QuickDropColors.CosmicBorder, RoundedCornerShape(14.dp))
                        .padding(14.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0x3310B981))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(item.kind.name, color = QuickDropColors.EmeraldLight, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                            Text(item.promptOrBody, color = QuickDropColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Text("Answer: ${item.explanation}", color = QuickDropColors.TextSecondary, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

// =========================================================================
// 8. QUIZ SETUP SCREEN
// =========================================================================

@Composable
private fun QuizSetupScreen(
    app: QuickDropApplication,
    primarySubjectId: String?,
    preferences: UserPreferences,
    onBack: () -> Unit,
    onSessionStarted: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val subjects by app.localStore.observeActiveSubjects().collectAsState(initial = emptyList())
    var selectedSubjectId by remember { mutableStateOf(primarySubjectId) }
    var questionCount by remember { mutableStateOf(10) }
    var isStarting by remember { mutableStateOf(false) }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Configure Quiz Sprint", style = MaterialTheme.typography.titleLarge, color = QuickDropColors.TextPrimary, fontWeight = FontWeight.Bold)
                OutlinedButton(onClick = onBack, shape = RoundedCornerShape(10.dp)) { Text("Cancel") }
            }
        }

        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(QuickDropColors.CosmicSurfaceElevated)
                    .border(1.dp, QuickDropColors.CosmicBorder, RoundedCornerShape(18.dp))
                    .padding(18.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Select Scope:", color = QuickDropColors.TextSecondary, fontWeight = FontWeight.Bold, fontSize = 13.sp)

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selectedSubjectId == null) Color(0x3310B981) else QuickDropColors.CosmicSurface)
                            .border(1.dp, if (selectedSubjectId == null) QuickDropColors.Emerald else QuickDropColors.CosmicBorder, RoundedCornerShape(10.dp))
                            .clickable { selectedSubjectId = null }
                            .padding(12.dp)
                    ) {
                        Text("🌐 Cumulative (All Courses & Topics)", color = if (selectedSubjectId == null) QuickDropColors.EmeraldLight else QuickDropColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    subjects.forEach { subj ->
                        val isSelected = subj.id == selectedSubjectId
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) Color(0x3310B981) else QuickDropColors.CosmicSurface)
                                .border(1.dp, if (isSelected) QuickDropColors.Emerald else QuickDropColors.CosmicBorder, RoundedCornerShape(10.dp))
                                .clickable { selectedSubjectId = subj.id }
                                .padding(12.dp)
                        ) {
                            Text("📚 ${subj.name}", color = if (isSelected) QuickDropColors.EmeraldLight else QuickDropColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }

                    Text("Sprint Size: $questionCount Questions", color = QuickDropColors.TextSecondary, fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(5, 10, 15).forEach { count ->
                            val isSel = questionCount == count
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSel) QuickDropColors.Emerald else QuickDropColors.CosmicSurface)
                                    .clickable { questionCount = count }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("$count Questions", color = if (isSel) Color.White else QuickDropColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }

                    Button(
                        enabled = !isStarting,
                        onClick = {
                            isStarting = true
                            scope.launch {
                                val session = app.localStore.createQuizSession(
                                    scope = QuizScopeSnapshot(
                                        primarySubjectId = selectedSubjectId,
                                        requestedCount = questionCount
                                    )
                                )
                                onSessionStarted(session.id)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = QuickDropColors.Emerald),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Start Active Recall Sprint", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

// =========================================================================
// 9. QUIZ RUNNER SCREEN (Tactile Option Cards & Immediate Flow)
// =========================================================================

@Composable
private fun QuizRunnerScreen(
    app: QuickDropApplication,
    sessionId: String,
    onRequestQuit: () -> Unit,
    onQuizCompleted: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val session by app.localStore.observeSessionById(sessionId).collectAsState(initial = null)
    val currentSession = session ?: return

    val currentIndex = currentSession.currentQuestionIndex
    val itemId = currentSession.itemIdsInOrder.getOrNull(currentIndex)
    val item by app.localStore.observeItemById(itemId ?: "").collectAsState(initial = null)

    var selectedOptionId by remember(currentIndex) { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Progress Bar & Header
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Question ${currentIndex + 1} of ${currentSession.itemIdsInOrder.size}",
                    color = QuickDropColors.TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                TextButton(onClick = onRequestQuit) {
                    Text("Pause / Quit", color = QuickDropColors.RoseLight, fontSize = 12.sp)
                }
            }

            SegmentedProgressBar(
                total = currentSession.itemIdsInOrder.size,
                current = currentIndex
            )
        }

        val curItem = item
        if (curItem != null) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(QuickDropColors.CosmicSurfaceElevated)
                    .border(1.dp, QuickDropColors.CosmicBorder, RoundedCornerShape(20.dp))
                    .padding(18.dp)
            ) {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        Text(
                            text = curItem.promptOrBody,
                            color = QuickDropColors.TextPrimary,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 24.sp
                        )
                    }

                    if (curItem.options.isNotEmpty()) {
                        items(curItem.options) { option ->
                            val letters = listOf("A", "B", "C", "D", "E")
                            val idx = curItem.options.indexOf(option)
                            val label = letters.getOrElse(idx) { "?" }

                            TactileOptionCard(
                                ordinalLabel = label,
                                hotkeyHint = "",
                                body = option.body,
                                isSelected = selectedOptionId == option.id,
                                onClick = { selectedOptionId = option.id }
                            )
                        }
                    } else {
                        item {
                            Text(
                                text = "💡 Active Recall Flashcard: Think of the answer in your mind, then tap Submit to proceed.",
                                color = QuickDropColors.TextMuted,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            // Bottom Navigation buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (currentIndex > 0) {
                    OutlinedButton(
                        onClick = {
                            scope.launch { app.localStore.navigateQuestion(sessionId, currentIndex - 1) }
                        },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.weight(1f).height(46.dp)
                    ) {
                        Text("Previous", fontSize = 12.sp, maxLines = 1)
                    }
                }

                Button(
                    enabled = selectedOptionId != null || curItem.options.isEmpty(),
                    onClick = {
                        scope.launch {
                            app.localStore.recordAnswer(
                                sessionId = sessionId,
                                itemId = curItem.id,
                                selectedOptionId = selectedOptionId,
                                latencyMs = 2000L
                            )
                            if (currentIndex + 1 >= currentSession.itemIdsInOrder.size) {
                                app.localStore.completeQuizSession(sessionId)
                                onQuizCompleted(sessionId)
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = QuickDropColors.Emerald),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.weight(1.5f).height(46.dp)
                ) {
                    Text(
                        text = if (currentIndex + 1 >= currentSession.itemIdsInOrder.size) "Submit Quiz" else "Next Question →",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

// =========================================================================
// 10. QUIZ RESULTS SCREEN
// =========================================================================

@Composable
private fun QuizResultsScreen(
    app: QuickDropApplication,
    sessionId: String,
    onDone: () -> Unit,
    onRetryDueOrMissed: (String?) -> Unit
) {
    val session by app.localStore.observeSessionById(sessionId).collectAsState(initial = null)
    val responses by app.localStore.observeResponsesForSession(sessionId).collectAsState(initial = emptyList())
    val completed = session ?: return

    val accuracy = if (completed.answeredCount > 0) (completed.correctCount * 100) / completed.answeredCount else 0

    LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF064E3B), Color(0xFF0F172A))
                        )
                    )
                    .border(1.dp, QuickDropColors.Emerald.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                    .padding(20.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Sprint Complete! 🎯", color = QuickDropColors.EmeraldLight, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("$accuracy%", color = QuickDropColors.TextPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 42.sp)
                    Text("${completed.correctCount} of ${completed.answeredCount} answered correctly", color = QuickDropColors.TextSecondary, fontSize = 13.sp)

                    Spacer(Modifier.height(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = onDone,
                            colors = ButtonDefaults.buttonColors(containerColor = QuickDropColors.Emerald),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Back to Dashboard", fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = { onRetryDueOrMissed(completed.scopeSnapshot.primarySubjectId) },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Retry Missed")
                        }
                    }
                }
            }
        }

        item {
            Text("Detailed Question Review:", color = QuickDropColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }

        items(responses) { resp ->
            val isCorrect = resp.outcome == ResponseOutcome.CORRECT
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(QuickDropColors.CosmicSurfaceElevated)
                    .border(1.dp, if (isCorrect) QuickDropColors.Emerald.copy(alpha = 0.4f) else QuickDropColors.Rose.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                    .padding(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Q${resp.sequence + 1}", color = QuickDropColors.TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = if (isCorrect) "✓ CORRECT" else "✗ INCORRECT",
                            color = if (isCorrect) QuickDropColors.EmeraldLight else QuickDropColors.RoseLight,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 11.sp
                        )
                    }

                    Text(resp.promptSnapshot, color = QuickDropColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("Explanation: ${resp.explanationSnapshot}", color = QuickDropColors.TextSecondary, fontSize = 12.sp)
                    if (resp.sourceCitationSnapshot.isNotBlank()) {
                        Text("Source: ${resp.sourceCitationSnapshot}", color = QuickDropColors.CyanLight, fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

// =========================================================================
// 11. SETTINGS SCREEN
// =========================================================================

@Composable
private fun SettingsScreen(
    app: QuickDropApplication,
    preferences: UserPreferences,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var apiKey by rememberSaveable { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Settings & Privacy", style = MaterialTheme.typography.titleLarge, color = QuickDropColors.TextPrimary, fontWeight = FontWeight.Bold)
                OutlinedButton(onClick = onBack, shape = RoundedCornerShape(10.dp)) { Text("Done") }
            }
        }

        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(QuickDropColors.CosmicSurfaceElevated)
                    .border(1.dp, QuickDropColors.CosmicBorder, RoundedCornerShape(18.dp))
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Google Gemini BYOK (Bring Your Own Key)", color = QuickDropColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("API keys are saved directly into the encrypted Android Keystore. All HTTPS calls go straight to Google APIs with zero proxy servers.", color = QuickDropColors.TextSecondary, fontSize = 11.sp)

                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text("Gemini API Key (AIzaSy...)") },
                        placeholder = { Text(app.apiKeyVault.getMaskedKeyPreview()?.ifBlank { "Enter API key" } ?: "Enter API key") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = {
                            if (apiKey.isNotBlank()) {
                                app.apiKeyVault.saveApiKey(apiKey)
                                statusMessage = "API Key saved securely in Android Keystore!"
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = QuickDropColors.Emerald),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Save API Key", fontWeight = FontWeight.Bold)
                    }

                    if (statusMessage != null) {
                        Text(statusMessage!!, color = QuickDropColors.EmeraldLight, fontSize = 12.sp)
                    }
                }
            }
        }

        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(QuickDropColors.CosmicSurfaceElevated)
                    .border(1.dp, QuickDropColors.CosmicBorder, RoundedCornerShape(18.dp))
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("App Version & Build", color = QuickDropColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("Quick Drop Mobile v0.0.1 (Build 1)", color = QuickDropColors.EmeraldLight, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text("Obsidian Cosmic Theme • Local SQLite • SuperMemo SM-2 Spaced Repetition", color = QuickDropColors.TextSecondary, fontSize = 11.sp)
                }
            }
        }
    }
}
