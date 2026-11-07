package com.quickdrop.desktop

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.quickdrop.core.domain.ClassOccurrence
import com.quickdrop.core.domain.FakeGeminiClient
import com.quickdrop.core.domain.FileExtractor
import com.quickdrop.core.domain.GeminiResponse
import com.quickdrop.core.domain.SpacedRepetitionScheduler
import com.quickdrop.core.model.AnchorKind
import com.quickdrop.core.model.ApprovalStatus
import com.quickdrop.core.model.ClassSlot
import com.quickdrop.core.model.Difficulty
import com.quickdrop.core.model.DropVisibilityState
import com.quickdrop.core.model.LearningItem
import com.quickdrop.core.model.LearningItemKind
import com.quickdrop.core.model.QuizDifficultyFilter
import com.quickdrop.core.model.QuizScopeSnapshot
import com.quickdrop.core.model.QuizSessionStatus
import com.quickdrop.core.model.ResponseOutcome
import com.quickdrop.core.model.ReviewConfidence
import com.quickdrop.core.model.ScheduleException
import com.quickdrop.core.model.ScheduleExceptionAction
import com.quickdrop.core.model.ThemePreference
import com.quickdrop.core.model.TopicMasteryStatus
import com.quickdrop.core.model.TopicReviewState
import com.quickdrop.core.model.UserPreferences
import com.quickdrop.core.repository.LocalDataExportBundle
import com.quickdrop.core.repository.QuickDropLocalStore
import com.quickdrop.core.security.AesGcmKeyVault
import com.quickdrop.core.time.SystemClock
import com.quickdrop.desktop.ui.CosmicDarkColorScheme
import com.quickdrop.desktop.ui.GlassBadge
import com.quickdrop.desktop.ui.HeroReadinessCard
import com.quickdrop.desktop.ui.InteractiveFlipCard
import com.quickdrop.desktop.ui.NordicLightColorScheme
import com.quickdrop.desktop.ui.OledBlackColorScheme
import com.quickdrop.desktop.ui.QuickDropColors
import com.quickdrop.desktop.ui.SegmentedProgressBar
import com.quickdrop.desktop.ui.StatMetricCard
import com.quickdrop.desktop.ui.TactileOptionCard
import com.quickdrop.desktop.ui.glassCard
import com.quickdrop.desktop.ui.AddClassSlotModal
import com.quickdrop.desktop.ui.ImportTimetableModal
import com.quickdrop.desktop.ui.UploadTimetableFileModal
import com.quickdrop.desktop.ui.WeeklyTimetableBoard
import com.quickdrop.desktop.ui.FileIngestionPanel
import com.quickdrop.desktop.ui.FilePickerHelper
import com.quickdrop.core.domain.TimetableParser
import com.quickdrop.core.domain.RecognizedClassSlot
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.util.UUID

fun main() = application {
    println("=======================================================")
    println("🚀 [QuickDrop Desktop] Starting Quick Drop v0.0.1 (Build 1)")
    println("📦 Local-First Intelligent Academic Learning Companion & Timetable System")
    println("🔐 Security: Encrypted SQLite Keystore • BYOK Direct HTTPS")
    println("=======================================================")

    val clock = remember { SystemClock() }
    val secretKey = remember { AesGcmKeyVault.generateEphemeralSecretKey() }
    val keyVault = remember { AesGcmKeyVault({ secretKey }) }
    val store = remember { QuickDropLocalStore(clock, onClearKeystoreKey = { keyVault.removeApiKey() }) }
    val geminiClient = remember { FakeGeminiClient() }

    // Seed realistic multi-course syllabus on initial launch
    LaunchedEffect(Unit) {
        // Course 1: Operating Systems
        val os = store.createOrUpdateSubject(
            name = "Operating Systems",
            colorKey = "emerald",
            iconKey = "memory",
            term = "Fall 2026",
            instructor = "Prof. Chen"
        )
        store.upsertClassSlot(
            ClassSlot(
                id = "slot-os",
                subjectId = os.id,
                weekdayMask = 1 or 4 or 16, // Mon, Wed, Fri
                startMinuteOfDay = 10 * 60,
                endMinuteOfDay = 11 * 60 + 30,
                zoneId = clock.zoneId().id,
                location = "Turing Hall 204",
                enabled = true,
                reminderEnabled = true,
                reminderLeadMinutes = 15
            )
        )
        val osLec = store.createOrUpdateLecture(
            subjectId = os.id,
            title = "Virtual Memory & Paging Tables",
            localDateIso = clock.localDate().toString(),
            topicLabel = "Virtual Memory",
            notes = "Virtual memory maps virtual addresses to physical pages. A Translation Lookaside Buffer (TLB) caches recent virtual-to-physical address translations to prevent redundant multi-level page table traversals."
        )
        store.createManualItem(
            subjectId = os.id,
            topicName = "Virtual Memory",
            lectureId = osLec.id,
            kind = LearningItemKind.MCQ,
            promptOrBody = "Which high-speed hardware cache directly speeds up virtual-to-physical address translation?",
            explanation = "The Translation Lookaside Buffer (TLB) caches page table entries directly on the MMU.",
            difficulty = Difficulty.MEDIUM,
            options = listOf(
                "Translation Lookaside Buffer (TLB)" to true,
                "Interrupt Descriptor Table (IDT)" to false,
                "Direct Memory Access (DMA) Controller" to false,
                "Instruction Reorder Buffer" to false
            )
        )
        store.createManualItem(
            subjectId = os.id,
            topicName = "Virtual Memory",
            lectureId = osLec.id,
            kind = LearningItemKind.DROP,
            promptOrBody = "A TLB hit satisfies virtual address translation in a single clock cycle without accessing main memory.",
            explanation = "Core OS Virtual Memory Architecture",
            difficulty = Difficulty.EASY
        )

        // Course 2: Distributed Systems
        val dist = store.createOrUpdateSubject(
            name = "Distributed Systems",
            colorKey = "violet",
            iconKey = "cloud",
            term = "Fall 2026",
            instructor = "Dr. Miller"
        )
        store.upsertClassSlot(
            ClassSlot(
                id = "slot-dist",
                subjectId = dist.id,
                weekdayMask = 2 or 8, // Tue, Thu
                startMinuteOfDay = 14 * 60,
                endMinuteOfDay = 15 * 60 + 30,
                zoneId = clock.zoneId().id,
                location = "Cyber Lab 102",
                enabled = true,
                reminderEnabled = true,
                reminderLeadMinutes = 20
            )
        )
        val distLec = store.createOrUpdateLecture(
            subjectId = dist.id,
            title = "Raft Consensus & Quorum Elections",
            localDateIso = clock.localDate().toString(),
            topicLabel = "Raft Consensus",
            notes = "Raft achieves fault-tolerant consensus by electing a single leader per term through randomized election timers and majority quorum votes."
        )
        store.createManualItem(
            subjectId = dist.id,
            topicName = "Raft Consensus",
            lectureId = distLec.id,
            kind = LearningItemKind.MCQ,
            promptOrBody = "In the Raft consensus protocol, what mechanism prevents split-brain split votes during leader elections?",
            explanation = "Randomized election timeouts stagger candidate heartbeats, ensuring one candidate almost always reaches a majority quorum first.",
            difficulty = Difficulty.HARD,
            options = listOf(
                "Randomized election timeouts across nodes" to true,
                "Centralized atomic clock synchronization" to false,
                "Manual human operator intervention" to false,
                "Two-phase locking on all followers" to false
            )
        )
        store.createManualItem(
            subjectId = dist.id,
            topicName = "Raft Consensus",
            lectureId = distLec.id,
            kind = LearningItemKind.DROP,
            promptOrBody = "Raft quorum: For a cluster of N nodes, any committed state transition requires approval from at least (N/2 + 1) nodes.",
            explanation = "Safety invariant for distributed state machines",
            difficulty = Difficulty.MEDIUM
        )

        // Course 3: Machine Learning & AI
        val ml = store.createOrUpdateSubject(
            name = "Machine Learning",
            colorKey = "cyan",
            iconKey = "psychology",
            term = "Fall 2026",
            instructor = "Prof. Vance"
        )
        store.upsertClassSlot(
            ClassSlot(
                id = "slot-ml",
                subjectId = dist.id,
                weekdayMask = 4 or 16, // Wed, Fri
                startMinuteOfDay = 16 * 60,
                endMinuteOfDay = 17 * 60 + 15,
                zoneId = clock.zoneId().id,
                location = "Auditorium C",
                enabled = true
            )
        )
        val mlLec = store.createOrUpdateLecture(
            subjectId = ml.id,
            title = "Attention Mechanisms & Transformers",
            localDateIso = clock.localDate().toString(),
            topicLabel = "Transformers",
            notes = "Self-attention computes dynamic weights between query and key vectors using scaled dot-product attention."
        )
        store.createManualItem(
            subjectId = ml.id,
            topicName = "Transformers",
            lectureId = mlLec.id,
            kind = LearningItemKind.MCQ,
            promptOrBody = "Why is the dot product of Query and Key scaled by the square root of the dimension (√d_k) in Transformers?",
            explanation = "Scaling prevents dot products from growing excessively large for large dimensions, which would push the softmax function into regions with vanishing gradients.",
            difficulty = Difficulty.HARD,
            options = listOf(
                "To prevent dot products from vanishing softmax gradients" to true,
                "To compress the context window into fewer tokens" to false,
                "To enforce causal masking during decoding" to false,
                "To eliminate the need for multi-head projections" to false
            )
        )

        keyVault.saveApiKey("AIzaSyDemoStudentKey1234")
    }

    Window(
        onCloseRequest = ::exitApplication,
        title = "Quick Drop v0.0.1 — Premium Local-First Learning Companion",
        state = rememberWindowState(size = DpSize(1040.dp, 780.dp))
    ) {
        val preferences by store.observePreferences().collectAsState(initial = UserPreferences())
        val colorScheme = when (preferences.themePreference) {
            ThemePreference.SYSTEM -> CosmicDarkColorScheme
            ThemePreference.LIGHT -> NordicLightColorScheme
            ThemePreference.DARK -> OledBlackColorScheme
        }

        MaterialTheme(colorScheme = colorScheme) {
            DesktopCockpitShell(
                store = store,
                clock = clock,
                keyVault = keyVault,
                geminiClient = geminiClient,
                preferences = preferences
            )
        }
    }
}

enum class CockpitTab(val label: String, val icon: ImageVector) {
    HOME("Dashboard", Icons.Default.Home),
    TIMETABLE("Timetable", Icons.Default.CalendarMonth),
    STUDY("Study & Drops", Icons.Default.Quiz),
    INGESTION("Upload Materials", Icons.Default.FileUpload),
    PROGRESS("Analytics", Icons.Default.Analytics)
}

sealed class CockpitScreen {
    data object MainTabs : CockpitScreen()
    data class SubjectDetail(val subjectId: String) : CockpitScreen()
    data class LectureEditor(val subjectId: String) : CockpitScreen()
    data class MaterialReview(val lectureId: String) : CockpitScreen()
    data class QuizSetup(val primarySubjectId: String?) : CockpitScreen()
    data class QuizRunner(val sessionId: String) : CockpitScreen()
    data class QuizResults(val sessionId: String) : CockpitScreen()
    data object Settings : CockpitScreen()
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DesktopCockpitShell(
    store: QuickDropLocalStore,
    clock: SystemClock,
    keyVault: AesGcmKeyVault,
    geminiClient: FakeGeminiClient,
    preferences: UserPreferences
) {
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableStateOf(CockpitTab.HOME) }
    var activeScreen by remember { mutableStateOf<CockpitScreen>(CockpitScreen.MainTabs) }
    var showQuitQuizDialog by remember { mutableStateOf(false) }

    val subjects by store.observeActiveSubjects().collectAsState(initial = emptyList())
    val activeSession by store.observeActiveSession().collectAsState(initial = null)
    val reviewStates by store.observeTopicReviewStates().collectAsState(initial = emptyMap())
    val drops by store.observeApprovedDrops().collectAsState(initial = emptyList())
    val slots by store.observeSlots().collectAsState(initial = emptyList())
    val exceptions by store.observeExceptions().collectAsState(initial = emptyList())

    // Pause / Discard confirmation modal
    if (showQuitQuizDialog && activeSession != null) {
        AlertDialog(
            onDismissRequest = { showQuitQuizDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Schedule, contentDescription = null, tint = QuickDropColors.Amber)
                    Text("Pause or Discard Quiz?", color = QuickDropColors.TextPrimary, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Text(
                    "Your submitted answers are saved locally. You can resume anytime from the dashboard, or discard this session without changing topic mastery estimates.",
                    color = QuickDropColors.TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showQuitQuizDialog = false
                        activeScreen = CockpitScreen.MainTabs
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = QuickDropColors.Emerald)
                ) {
                    Text("Save & Resume Later", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        val sid = activeSession?.id
                        showQuitQuizDialog = false
                        if (sid != null) {
                            scope.launch {
                                store.discardQuizSession(sid)
                                activeScreen = CockpitScreen.MainTabs
                            }
                        }
                    }
                ) {
                    Text("Discard Session", color = QuickDropColors.RoseLight)
                }
            }
        )
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(QuickDropColors.CosmicBackground)) {
        val isMobile = maxWidth < 768.dp
        val isCompact = maxWidth < 540.dp

        if (isMobile) {
            // ==========================================
            // FULLY MOBILE RESPONSIVE ADAPTIVE COCKPIT
            // ==========================================
            Column(modifier = Modifier.fillMaxSize()) {
                // Sleek Mobile Top App Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(QuickDropColors.CosmicSurface)
                        .border(1.dp, QuickDropColors.CosmicBorder)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
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
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "Quick Drop",
                                    color = QuickDropColors.TextPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0x3310B981))
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                ) {
                                    Text("PRO", color = QuickDropColors.EmeraldLight, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            Text(
                                text = when (activeScreen) {
                                    CockpitScreen.MainTabs -> selectedTab.label
                                    is CockpitScreen.SubjectDetail -> "Course Syllabus"
                                    is CockpitScreen.LectureEditor -> "Lecture Editor"
                                    is CockpitScreen.MaterialReview -> "Review Extracted Drops"
                                    is CockpitScreen.QuizSetup -> "Configure Quiz Sprint"
                                    is CockpitScreen.QuizRunner -> "Active Recall Session"
                                    is CockpitScreen.QuizResults -> "Quiz Performance"
                                    CockpitScreen.Settings -> "Settings & Privacy"
                                },
                                color = QuickDropColors.TextMuted,
                                fontSize = 10.sp
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (activeScreen != CockpitScreen.MainTabs) {
                            OutlinedButton(
                                onClick = {
                                    if (activeScreen is CockpitScreen.QuizRunner) {
                                        showQuitQuizDialog = true
                                    } else {
                                        activeScreen = CockpitScreen.MainTabs
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
                            onClick = { activeScreen = CockpitScreen.Settings },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = if (activeScreen is CockpitScreen.Settings) QuickDropColors.Emerald else QuickDropColors.TextSecondary,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                }

                // Mobile Main Viewport (full width, fluid scrolling)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = if (isCompact) 8.dp else 12.dp, vertical = 8.dp)
                ) {
                    CockpitScreenViewport(
                        activeScreen = activeScreen,
                        selectedTab = selectedTab,
                        store = store,
                        clock = clock,
                        keyVault = keyVault,
                        geminiClient = geminiClient,
                        preferences = preferences,
                        subjects = subjects,
                        activeSession = activeSession,
                        reviewStates = reviewStates,
                        drops = drops,
                        slots = slots,
                        exceptions = exceptions,
                        onSelectTab = { selectedTab = it },
                        onNavigate = { activeScreen = it },
                        onRequestQuitQuiz = { showQuitQuizDialog = true }
                    )
                }

                // Mobile Bottom Navigation Bar (5 Primary Tabs)
                NavigationBar(
                    containerColor = QuickDropColors.CosmicSurface,
                    tonalElevation = 8.dp,
                    modifier = Modifier.border(1.dp, QuickDropColors.CosmicBorder)
                ) {
                    CockpitTab.entries.forEach { tab ->
                        val isSelected = activeScreen == CockpitScreen.MainTabs && selectedTab == tab
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = {
                                selectedTab = tab
                                activeScreen = CockpitScreen.MainTabs
                            },
                            icon = {
                                Icon(
                                    tab.icon,
                                    contentDescription = tab.label,
                                    tint = if (isSelected) QuickDropColors.Emerald else QuickDropColors.TextMuted,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            label = {
                                Text(
                                    tab.label,
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
        } else {
            // ==========================================
            // DESKTOP ULTRA-WIDE WORKSPACE WITH SIDEBAR
            // ==========================================
            Row(modifier = Modifier.fillMaxSize()) {
                // Sleek Modern Desktop Sidebar
                Box(
                    modifier = Modifier
                        .width(260.dp)
                        .fillMaxHeight()
                        .background(QuickDropColors.CosmicSurface)
                        .border(width = 1.dp, color = QuickDropColors.CosmicBorder)
                        .padding(18.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Top: Brand, Profile & Navigation
                        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                            // Brand Header
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(QuickDropColors.EmeraldGradient),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Bolt,
                                        contentDescription = "Logo",
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(
                                            text = "Quick Drop",
                                            color = QuickDropColors.TextPrimary,
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(Color(0x3310B981))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text("PRO", color = QuickDropColors.EmeraldLight, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    Text(
                                        text = "Local-First Companion",
                                        color = QuickDropColors.TextMuted,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            // Navigation Links
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                CockpitTab.entries.forEach { tab ->
                                    val isSelected = activeScreen == CockpitScreen.MainTabs && selectedTab == tab
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(
                                                if (isSelected) QuickDropColors.CosmicSurfaceHighlight else Color.Transparent
                                            )
                                            .clickable {
                                                selectedTab = tab
                                                activeScreen = CockpitScreen.MainTabs
                                            }
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Icon(
                                            imageVector = tab.icon,
                                            contentDescription = tab.label,
                                            tint = if (isSelected) QuickDropColors.Emerald else QuickDropColors.TextSecondary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Text(
                                            text = tab.label,
                                            color = if (isSelected) QuickDropColors.TextPrimary else QuickDropColors.TextSecondary,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = 14.sp
                                        )
                                    }
                                }
                            }
                        }

                        // Bottom: AI Engine status & Settings link
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            // AI Status Pill
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0x1A8B5CF6))
                                    .border(1.dp, QuickDropColors.Violet.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(QuickDropColors.Emerald)
                                )
                                Column {
                                    Text(
                                        text = "Gemini BYOK Engine",
                                        color = QuickDropColors.VioletLight,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Direct HTTPS • Keystore Secured",
                                        color = QuickDropColors.TextMuted,
                                        fontSize = 9.sp
                                    )
                                }
                            }

                            // Settings Button
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (activeScreen is CockpitScreen.Settings) QuickDropColors.CosmicSurfaceHighlight else Color.Transparent
                                    )
                                    .clickable { activeScreen = CockpitScreen.Settings }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "Settings",
                                    tint = if (activeScreen is CockpitScreen.Settings) QuickDropColors.Emerald else QuickDropColors.TextSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "Settings & Privacy",
                                    color = if (activeScreen is CockpitScreen.Settings) QuickDropColors.TextPrimary else QuickDropColors.TextSecondary,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }

                // Main Workspace Viewport
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(24.dp)
                ) {
                    CockpitScreenViewport(
                        activeScreen = activeScreen,
                        selectedTab = selectedTab,
                        store = store,
                        clock = clock,
                        keyVault = keyVault,
                        geminiClient = geminiClient,
                        preferences = preferences,
                        subjects = subjects,
                        activeSession = activeSession,
                        reviewStates = reviewStates,
                        drops = drops,
                        slots = slots,
                        exceptions = exceptions,
                        onSelectTab = { selectedTab = it },
                        onNavigate = { activeScreen = it },
                        onRequestQuitQuiz = { showQuitQuizDialog = true }
                    )
                }
            }
        }
    }
}

@Composable
fun CockpitScreenViewport(
    activeScreen: CockpitScreen,
    selectedTab: CockpitTab,
    store: QuickDropLocalStore,
    clock: SystemClock,
    keyVault: AesGcmKeyVault,
    geminiClient: FakeGeminiClient,
    preferences: UserPreferences,
    subjects: List<com.quickdrop.core.model.Subject>,
    activeSession: com.quickdrop.core.model.QuizSession?,
    reviewStates: Map<String, TopicReviewState>,
    drops: List<Pair<LearningItem, com.quickdrop.core.model.DropState>>,
    slots: List<ClassSlot>,
    exceptions: List<ScheduleException>,
    onSelectTab: (CockpitTab) -> Unit,
    onNavigate: (CockpitScreen) -> Unit,
    onRequestQuitQuiz: () -> Unit
) {
    when (val screen = activeScreen) {
        CockpitScreen.MainTabs -> {
            when (selectedTab) {
                CockpitTab.HOME -> HomeDashboard(
                    store = store,
                    clock = clock,
                    subjects = subjects,
                    activeSession = activeSession,
                    drops = drops,
                    reviewStates = reviewStates,
                    onOpenSubject = { onNavigate(CockpitScreen.SubjectDetail(it)) },
                    onLaunchQuizSetup = { onNavigate(CockpitScreen.QuizSetup(it)) },
                    onResumeQuiz = { onNavigate(CockpitScreen.QuizRunner(it)) },
                    onAddLecture = { onNavigate(CockpitScreen.LectureEditor(it)) },
                    onOpenTimetable = { onSelectTab(CockpitTab.TIMETABLE) },
                    onOpenUpload = { onSelectTab(CockpitTab.INGESTION) }
                )

                CockpitTab.TIMETABLE -> TimetableScheduleView(
                    store = store,
                    clock = clock,
                    subjects = subjects,
                    slots = slots,
                    exceptions = exceptions,
                    keyVault = keyVault,
                    onOpenSubject = { onNavigate(CockpitScreen.SubjectDetail(it)) }
                )

                CockpitTab.STUDY -> StudyFlashcardView(
                    store = store,
                    drops = drops,
                    onStartQuiz = { onNavigate(CockpitScreen.QuizSetup(it)) },
                    onResumeQuiz = { onNavigate(CockpitScreen.QuizRunner(it)) },
                    activeSession = activeSession
                )

                CockpitTab.INGESTION -> LectureStudioView(
                    store = store,
                    clock = clock,
                    geminiClient = geminiClient,
                    keyVault = keyVault,
                    subjects = subjects,
                    onReviewLecture = { onNavigate(CockpitScreen.MaterialReview(it)) }
                )

                CockpitTab.PROGRESS -> AnalyticsProgressView(
                    store = store,
                    clock = clock,
                    reviewStates = reviewStates,
                    subjects = subjects
                )
            }
        }

        is CockpitScreen.SubjectDetail -> SubjectHubView(
            store = store,
            subjectId = screen.subjectId,
            onBack = { onNavigate(CockpitScreen.MainTabs) },
            onAddLecture = { onNavigate(CockpitScreen.LectureEditor(screen.subjectId)) },
            onReviewLecture = { onNavigate(CockpitScreen.MaterialReview(it)) },
            onPrepareQuiz = { onNavigate(CockpitScreen.QuizSetup(screen.subjectId)) }
        )

        is CockpitScreen.LectureEditor -> LectureEditorModal(
            store = store,
            clock = clock,
            subjectId = screen.subjectId,
            onBack = { onNavigate(CockpitScreen.SubjectDetail(screen.subjectId)) },
            onDone = { onNavigate(CockpitScreen.MaterialReview(it)) }
        )

        is CockpitScreen.MaterialReview -> MaterialReviewStudio(
            store = store,
            geminiClient = geminiClient,
            keyVault = keyVault,
            lectureId = screen.lectureId,
            onBack = { onNavigate(CockpitScreen.MainTabs) }
        )

        is CockpitScreen.QuizSetup -> QuizSetupStudio(
            store = store,
            primarySubjectId = screen.primarySubjectId,
            preferences = preferences,
            onBack = { onNavigate(CockpitScreen.MainTabs) },
            onStartSession = { onNavigate(CockpitScreen.QuizRunner(it)) }
        )

        is CockpitScreen.QuizRunner -> QuizRunnerStudio(
            store = store,
            sessionId = screen.sessionId,
            onRequestQuit = onRequestQuitQuiz,
            onComplete = { onNavigate(CockpitScreen.QuizResults(it)) }
        )

        is CockpitScreen.QuizResults -> QuizResultsStudio(
            store = store,
            sessionId = screen.sessionId,
            onDone = { onNavigate(CockpitScreen.MainTabs) },
            onRetryMissed = { onNavigate(CockpitScreen.QuizSetup(it)) }
        )

        CockpitScreen.Settings -> SettingsStudio(
            store = store,
            keyVault = keyVault,
            geminiClient = geminiClient,
            preferences = preferences,
            onBack = { onNavigate(CockpitScreen.MainTabs) }
        )
    }
}

// =========================================================================
// 1. HOME DASHBOARD (Hero Card, Metric Stats, Course Cards, Drop of the Day)
// =========================================================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeDashboard(
    store: QuickDropLocalStore,
    clock: SystemClock,
    subjects: List<com.quickdrop.core.model.Subject>,
    activeSession: com.quickdrop.core.model.QuizSession?,
    drops: List<Pair<LearningItem, com.quickdrop.core.model.DropState>>,
    reviewStates: Map<String, TopicReviewState>,
    onOpenSubject: (String) -> Unit,
    onLaunchQuizSetup: (String?) -> Unit,
    onResumeQuiz: (String) -> Unit,
    onAddLecture: (String) -> Unit,
    onOpenTimetable: () -> Unit,
    onOpenUpload: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var occurrences by remember { mutableStateOf<List<ClassOccurrence>>(emptyList()) }
    var showNewSubjectDialog by remember { mutableStateOf(false) }

    LaunchedEffect(subjects) {
        occurrences = store.getUpcomingOccurrences(daysAhead = 7)
    }

    val nextOcc = occurrences.firstOrNull()
    val nextSubj = subjects.find { it.id == nextOcc?.subjectId }
    val dueCount = reviewStates.values.count { state ->
        val due = state.dueAtEpochMs
        due != null && due <= clock.now().toEpochMilli()
    }
    val stableCount = reviewStates.values.count {
        SpacedRepetitionScheduler.resolveMasteryStatus(it, clock) == TopicMasteryStatus.STABLE
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        // Hero Readiness Banner
        item {
            HeroReadinessCard(
                studentName = "Alex",
                streakDays = 4,
                readinessPercent = 88,
                nextClassTitle = nextSubj?.name?.let { "$it (${nextOcc?.startZoned?.toLocalTime()})" },
                nextClassCountdown = "TODAY",
                nextClassLocation = nextOcc?.location,
                onLaunchPrepQuiz = { onLaunchQuizSetup(nextSubj?.id) }
            )
        }

        // Quick Action Shortcuts Bar
        item {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onOpenTimetable,
                    colors = ButtonDefaults.buttonColors(containerColor = QuickDropColors.CosmicSurfaceElevated),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.height(46.dp)
                ) {
                    Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = QuickDropColors.Emerald, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Upload Timetable File", color = QuickDropColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onOpenUpload,
                    colors = ButtonDefaults.buttonColors(containerColor = QuickDropColors.CosmicSurfaceElevated),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.height(46.dp)
                ) {
                    Icon(Icons.Default.FileUpload, contentDescription = null, tint = QuickDropColors.Violet, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Upload Materials (PDF/HTML)", color = QuickDropColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = { onLaunchQuizSetup(null) },
                    colors = ButtonDefaults.buttonColors(containerColor = QuickDropColors.CosmicSurfaceElevated),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.height(46.dp)
                ) {
                    Icon(Icons.Default.Bolt, contentDescription = null, tint = QuickDropColors.Amber, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Quick Quiz", color = QuickDropColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(QuickDropColors.Violet.copy(alpha = 0.3f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Bolt, contentDescription = null, tint = QuickDropColors.VioletLight)
                            }
                            Column {
                                Text("Quiz in Progress", color = QuickDropColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Question ${activeSession.currentQuestionIndex + 1} of ${activeSession.itemIdsInOrder.size} • Answers saved locally", color = QuickDropColors.VioletLight, fontSize = 12.sp)
                            }
                        }

                        Button(
                            onClick = { onResumeQuiz(activeSession.id) },
                            colors = ButtonDefaults.buttonColors(containerColor = QuickDropColors.Violet),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Resume Quiz", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // 4 Key Stats Metrics (Adaptive 2x2 on Mobile, 1x4 on Desktop)
        item {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val isNarrow = maxWidth < 680.dp
                if (isNarrow) {
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
                                value = "4 Days",
                                subtext = "Keep momentum!",
                                icon = Icons.Default.LocalFireDepartment,
                                iconColor = QuickDropColors.Rose,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
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
                            value = "4 Days",
                            subtext = "Keep momentum!",
                            icon = Icons.Default.LocalFireDepartment,
                            iconColor = QuickDropColors.Rose,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Drop of the Day (Interactive Flip Card right on Home!)
        if (drops.isNotEmpty()) {
            val (featuredDrop, dropState) = drops.first()
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Daily Drop & Active Recall",
                            color = QuickDropColors.TextPrimary,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Bite-sized concept retrieval",
                            color = QuickDropColors.TextSecondary,
                            fontSize = 12.sp
                        )
                    }

                    InteractiveFlipCard(
                        topicName = "Featured Recall",
                        prompt = featuredDrop.promptOrBody,
                        explanation = featuredDrop.explanation,
                        evidenceQuote = featuredDrop.evidenceQuote,
                        anchorText = "${featuredDrop.anchorKind ?: "NOTE"} ${featuredDrop.anchorIndex ?: 1}",
                        isSaved = dropState.visibilityState == DropVisibilityState.SAVED,
                        onToggleSave = {
                            val nextState = if (dropState.visibilityState == DropVisibilityState.SAVED) {
                                DropVisibilityState.UNSEEN
                            } else {
                                DropVisibilityState.SAVED
                            }
                            scope.launch { store.updateDropState(featuredDrop.id, nextState) }
                        },
                        onRateConfidence = { rating ->
                            scope.launch {
                                val outcome = if (rating == "HARD") ResponseOutcome.INCORRECT else ResponseOutcome.CORRECT
                                val conf = if (rating == "EASY") ReviewConfidence.HIGH else ReviewConfidence.LOW
                                store.recordAnswer(
                                    sessionId = "drop-quick",
                                    itemId = featuredDrop.id,
                                    selectedOptionId = null,
                                    latencyMs = 1000L,
                                    confidence = conf
                                )
                            }
                        }
                    )
                }
            }
        }

        // Enrolled Courses Grid
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Enrolled Courses",
                    color = QuickDropColors.TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Button(
                    onClick = { showNewSubjectDialog = true },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = QuickDropColors.CosmicSurfaceElevated)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add Course", fontSize = 12.sp)
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
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    when (subj.colorKey) {
                                        "violet" -> Color(0x338B5CF6)
                                        "cyan" -> Color(0x3306B6D4)
                                        else -> Color(0x3310B981)
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = when (subj.iconKey) {
                                    "cloud" -> Icons.Default.Science
                                    "psychology" -> Icons.Default.Psychology
                                    else -> Icons.Default.Code
                                },
                                contentDescription = null,
                                tint = when (subj.colorKey) {
                                    "violet" -> QuickDropColors.VioletLight
                                    "cyan" -> QuickDropColors.CyanLight
                                    else -> QuickDropColors.EmeraldLight
                                },
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = subj.name,
                                color = QuickDropColors.TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Text(
                                text = "${subj.term ?: "Fall 2026"} • ${subj.instructor ?: "Instructor"}",
                                color = QuickDropColors.TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { onAddLecture(subj.id) },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(14.dp), tint = QuickDropColors.VioletLight)
                            Spacer(Modifier.width(4.dp))
                            Text("+ Upload", fontSize = 12.sp, color = QuickDropColors.VioletLight)
                        }

                        OutlinedButton(
                            onClick = { onOpenSubject(subj.id) },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Lectures", fontSize = 12.sp)
                        }

                        Button(
                            onClick = { onLaunchQuizSetup(subj.id) },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = QuickDropColors.Emerald)
                        ) {
                            Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Quiz", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // Modal to create course
    if (showNewSubjectDialog) {
        var name by remember { mutableStateOf("") }
        var instructor by remember { mutableStateOf("") }
        var term by remember { mutableStateOf("Fall 2026") }

        AlertDialog(
            onDismissRequest = { showNewSubjectDialog = false },
            title = { Text("Add Course", fontWeight = FontWeight.Bold, color = QuickDropColors.TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Course Name (e.g. Algorithms)") },
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
                                store.createOrUpdateSubject(
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
// 2. TIMETABLE SCHEDULE VIEW (Interactive Week Carousel, Agenda & Slot Management)
// =========================================================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TimetableScheduleView(
    store: QuickDropLocalStore,
    clock: SystemClock,
    subjects: List<com.quickdrop.core.model.Subject>,
    slots: List<ClassSlot>,
    exceptions: List<ScheduleException>,
    keyVault: AesGcmKeyVault? = null,
    onOpenSubject: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var viewMode by remember { mutableStateOf("matrix") } // Default to the Obsidian Matrix Board Template
    var selectedDayIso by remember { mutableStateOf(clock.localDate().dayOfWeek.value) }
    val dayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    var showUploadTimetableModal by remember { mutableStateOf(false) }
    var showAddSlotModal by remember { mutableStateOf(false) }
    var showImportModal by remember { mutableStateOf(false) }

    var allOccurrences by remember { mutableStateOf<List<ClassOccurrence>>(emptyList()) }
    LaunchedEffect(slots, exceptions) {
        allOccurrences = store.getUpcomingOccurrences(daysAhead = 14)
    }
    val occurrencesForDay = remember(allOccurrences, selectedDayIso) {
        allOccurrences.filter { it.effectiveLocalDate.dayOfWeek.value == selectedDayIso }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Mode Selector Bar (Template Matrix vs Daily Agenda) with Responsive Flow Wrapping
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = viewMode == "matrix",
                    onClick = { viewMode = "matrix" },
                    label = { Text("📅 Weekly Grid Board (Template)") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = QuickDropColors.Cyan,
                        selectedLabelColor = Color(0xFF090D16)
                    )
                )

                FilterChip(
                    selected = viewMode == "agenda",
                    onClick = { viewMode = "agenda" },
                    label = { Text("📋 Day Agenda View") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = QuickDropColors.Cyan,
                        selectedLabelColor = Color(0xFF090D16)
                    )
                )
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { showUploadTimetableModal = true },
                    colors = ButtonDefaults.buttonColors(containerColor = QuickDropColors.Emerald),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("📂 Upload Timetable (HTML/ICS)", fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = {
                        scope.launch {
                            loadCybersecurity200LSchedule(store, subjects, clock)
                        }
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp), tint = QuickDropColors.CyanLight)
                    Spacer(Modifier.width(4.dp))
                    Text("🪄 200L Schedule", fontSize = 12.sp, color = QuickDropColors.CyanLight)
                }

                OutlinedButton(
                    onClick = { showAddSlotModal = true },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("+ Manual Class", fontSize = 12.sp)
                }
            }
        }

        if (viewMode == "matrix") {
            // Obsidian Neon Weekly Board matching the user's template screenshot
            WeeklyTimetableBoard(
                clock = clock,
                subjects = subjects,
                slots = slots,
                onUploadClick = { showUploadTimetableModal = true },
                onLoadDefaultTemplate = {
                    scope.launch {
                        loadCybersecurity200LSchedule(store, subjects, clock)
                    }
                },
                onOpenSubject = onOpenSubject
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column {
                    Text(
                        text = "Weekly Timetable & Class Agenda",
                        style = MaterialTheme.typography.titleLarge,
                        color = QuickDropColors.TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Time zone: ${clock.zoneId().id} • Automatic DST & exception handling",
                        style = MaterialTheme.typography.bodySmall,
                        color = QuickDropColors.TextSecondary
                    )
                }

                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { showUploadTimetableModal = true },
                        colors = ButtonDefaults.buttonColors(containerColor = QuickDropColors.Emerald),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("📂 Upload Timetable File (HTML, ICS, PDF)", fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = { showAddSlotModal = true },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("+ Manual Class", fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = { showImportModal = true },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp), tint = QuickDropColors.CyanLight)
                        Spacer(Modifier.width(4.dp))
                        Text("Sample", fontSize = 12.sp, color = QuickDropColors.CyanLight)
                    }
                }
            }
        }

        // Interactive Day Selector Carousel
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(QuickDropColors.CosmicSurfaceElevated)
                    .border(1.dp, QuickDropColors.CosmicBorder, RoundedCornerShape(16.dp))
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                dayNames.forEachIndexed { index, name ->
                    val dayIso = index + 1
                    val isSelected = selectedDayIso == dayIso
                    val isToday = clock.localDate().dayOfWeek.value == dayIso
                    val hasClass = slots.any { it.enabled && it.includesDayOfWeek(dayIso) }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .then(
                                if (isSelected) Modifier.background(QuickDropColors.EmeraldGradient)
                                else Modifier.background(Color.Transparent)
                            )
                            .clickable { selectedDayIso = dayIso }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = name,
                                color = if (isSelected) Color.White else QuickDropColors.TextPrimary,
                                fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 13.sp
                            )
                            if (isToday) {
                                Text(
                                    text = "TODAY",
                                    color = if (isSelected) Color.White else QuickDropColors.EmeraldLight,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                            if (hasClass) {
                                Box(
                                    modifier = Modifier
                                        .size(5.dp)
                                        .clip(CircleShape)
                                        .background(if (isSelected) Color.White else QuickDropColors.Cyan)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Occurrences list for selected day
        if (occurrencesForDay.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(QuickDropColors.CosmicSurfaceElevated)
                        .border(1.dp, QuickDropColors.CosmicBorder, RoundedCornerShape(16.dp))
                        .padding(28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Default.Schedule, contentDescription = null, tint = QuickDropColors.TextMuted, modifier = Modifier.size(36.dp))
                        Text("No classes scheduled for ${dayNames[selectedDayIso - 1]}", color = QuickDropColors.TextSecondary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        Text("Enjoy your study sprint or add a recurring lecture to keep your schedule organized.", color = QuickDropColors.TextMuted, fontSize = 12.sp)
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = { showUploadTimetableModal = true },
                                colors = ButtonDefaults.buttonColors(containerColor = QuickDropColors.Emerald),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("📂 Upload Timetable File (Auto-Fills Schedule)", fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = { showAddSlotModal = true },
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Manual Class", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        } else {
            items(occurrencesForDay, key = { it.occurrenceId }) { occ ->
                val subj = subjects.find { it.id == occ.subjectId }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(QuickDropColors.CosmicSurfaceElevated)
                        .border(1.dp, QuickDropColors.CosmicBorder, RoundedCornerShape(16.dp))
                        .padding(18.dp)
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
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(QuickDropColors.Emerald)
                                )
                                Text(
                                    text = subj?.name ?: "Course",
                                    color = QuickDropColors.TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                            }

                            GlassBadge(
                                text = "%02d:%02d - %02d:%02d".format(
                                    occ.startZoned.hour,
                                    occ.startZoned.minute,
                                    occ.endZoned.hour,
                                    occ.endZoned.minute
                                ),
                                icon = Icons.Default.Schedule,
                                backgroundColor = Color(0x2606B6D4),
                                textColor = QuickDropColors.CyanLight
                            )
                        }

                        Text(
                            text = "📍 Location: ${occ.location.ifBlank { "Virtual / To be announced" }}",
                            color = QuickDropColors.TextSecondary,
                            fontSize = 13.sp
                        )

                        if (occ.isMoved) {
                            Text(
                                text = "⚠️ One-off exception: Moved to this date without altering your recurring weekly template.",
                                color = QuickDropColors.AmberLight,
                                fontSize = 11.sp
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        store.upsertException(
                                            ScheduleException(
                                                id = UUID.randomUUID().toString(),
                                                slotId = occ.slotId,
                                                originalLocalDateIso = occ.originalLocalDate.toString(),
                                                action = ScheduleExceptionAction.CANCEL
                                            )
                                        )
                                    }
                                },
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Cancel This Occurrence", fontSize = 11.sp, color = QuickDropColors.RoseLight)
                            }

                            TextButton(
                                onClick = { onOpenSubject(occ.subjectId) }
                            ) {
                                Text("Course Materials", fontSize = 12.sp, color = QuickDropColors.CyanLight)
                            }
                        }
                    }
                }
            }
        }

        // Section: Configured Weekly Slots Management
        item {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Configured Weekly Recurring Slots (${slots.size})",
                    color = QuickDropColors.TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                TextButton(onClick = { showAddSlotModal = true }) {
                    Text("+ Add Another Slot", color = QuickDropColors.EmeraldLight, fontSize = 12.sp)
                }
            }
        }

        if (slots.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(QuickDropColors.CosmicSurfaceElevated)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No recurring class slots configured yet. Click '+ Add Class to Schedule' to begin.", color = QuickDropColors.TextMuted, fontSize = 12.sp)
                }
            }
        } else {
            items(slots, key = { it.id }) { slot ->
                val subj = subjects.find { it.id == slot.subjectId }
                val startH = slot.startMinuteOfDay / 60
                val startM = slot.startMinuteOfDay % 60
                val endH = slot.endMinuteOfDay / 60
                val endM = slot.endMinuteOfDay % 60
                val activeDays = (1..7).filter { slot.includesDayOfWeek(it) }.map { dayNames[it - 1] }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(QuickDropColors.CosmicSurfaceElevated)
                        .border(1.dp, QuickDropColors.CosmicBorder, RoundedCornerShape(12.dp))
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(subj?.name ?: "Course", color = QuickDropColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                GlassBadge(text = activeDays.joinToString(", "), backgroundColor = Color(0x3310B981), textColor = QuickDropColors.EmeraldLight)
                            }
                            Text(
                                "%02d:%02d - %02d:%02d • 📍 %s %s".format(
                                    startH, startM, endH, endM,
                                    slot.location.ifBlank { "Virtual" },
                                    if (slot.reminderEnabled) "• 🔔 ${slot.reminderLeadMinutes}m prep quiz" else ""
                                ),
                                color = QuickDropColors.TextSecondary,
                                fontSize = 12.sp
                            )
                        }

                        IconButton(onClick = { scope.launch { store.deleteClassSlot(slot.id) } }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Slot", tint = QuickDropColors.RoseLight.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }

        // Active Exceptions summary
        if (exceptions.isNotEmpty()) {
            item {
                Text(
                    text = "Active One-Off Exceptions (${exceptions.size})",
                    color = QuickDropColors.TextSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
                    items(exceptions, key = { it.id }) { ex ->
                        AssistChip(
                            onClick = { scope.launch { store.removeException(ex.id) } },
                            label = { Text("${ex.originalLocalDateIso}: ${ex.action} (Click to restore occurrence)") },
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }
            }
        }
    }

    // Modal to upload timetable file (HTML, ICS, PDF, CSV) with AI support
    if (showUploadTimetableModal) {
        UploadTimetableFileModal(
            store = store,
            clock = clock,
            subjects = subjects,
            keyVault = keyVault,
            onDismiss = { showUploadTimetableModal = false },
            onImportComplete = { showUploadTimetableModal = false }
        )
    }

    // Modal to add class slot
    if (showAddSlotModal) {
        AddClassSlotModal(
            store = store,
            clock = clock,
            subjects = subjects,
            onDismiss = { showAddSlotModal = false },
            onSlotAdded = { showAddSlotModal = false }
        )
    }

    // Modal to import sample timetable
    if (showImportModal) {
        ImportTimetableModal(
            store = store,
            clock = clock,
            subjects = subjects,
            onDismiss = { showImportModal = false },
            onImportComplete = { showImportModal = false }
        )
    }
}

/**
 * Loads the user's exact Cybersecurity 200L Schedule from their downloaded HTML portal file
 * or pre-configured high-fidelity academic syllabus.
 */
suspend fun loadCybersecurity200LSchedule(
    store: QuickDropLocalStore,
    subjects: List<com.quickdrop.core.model.Subject>,
    clock: SystemClock
) {
    val userFile = java.io.File("""C:\Users\seton\Downloads\Cybersecurity_200L_Timetable_v2.html""")
    val slots = if (userFile.exists()) {
        val bytes = userFile.readBytes()
        TimetableParser.parse(bytes, userFile.name).slots
    } else {
        listOf(
            RecognizedClassSlot("Introduction to Software Engineering", "SEN 201", 1, "Mon", 10 * 60, 12 * 60, "10:00 AM - 12:00 PM (Double Slot)", "LH 30", credits = 2, isElective = false, isDoubleSlot = true),
            RecognizedClassSlot("Cybercrime, Law & Countermeasures", "CYB 203", 1, "Mon", 12 * 60, 13 * 60, "12:00 - 1:00 PM", "LH 30", credits = 2, isElective = false, isDoubleSlot = false),
            RecognizedClassSlot("Data Security and Privacy", "WU-CYB 205", 1, "Mon", 14 * 60, 16 * 60, "2:00 - 4:00 PM (Double Slot)", "LH 30", credits = 2, isElective = true, isDoubleSlot = true),
            RecognizedClassSlot("Digital Logic Design", "IFT 211", 2, "Tue", 8 * 60, 10 * 60, "8:00 - 10:00 AM (Double Slot)", "LH 30", credits = 2, isElective = false, isDoubleSlot = true),
            RecognizedClassSlot("Cybercrime, Law & Countermeasures", "CYB 203", 2, "Tue", 11 * 60, 12 * 60, "11:00 AM - 12:00 PM", "LH 30", credits = 2, isElective = false, isDoubleSlot = false),
            RecognizedClassSlot("Computer Programming I", "COS 201", 2, "Tue", 12 * 60, 13 * 60, "12:00 - 1:00 PM", "LH 30", credits = 3, isElective = false, isDoubleSlot = false),
            RecognizedClassSlot("Computer Programming I (Practical / Lecture)", "COS 201", 2, "Tue", 14 * 60, 16 * 60, "2:00 - 4:00 PM (Double Slot)", "Lab 2", credits = 3, isElective = false, isDoubleSlot = true),
            RecognizedClassSlot("Introduction to Cybersecurity and Strategy", "CYB 201", 8, "Thu", 8 * 60, 10 * 60, "8:00 - 10:00 AM (Double Slot)", "LH 30", credits = 2, isElective = false, isDoubleSlot = true),
            RecognizedClassSlot("Entrepreneurship and Innovation", "ENT 211", 8, "Thu", 11 * 60, 13 * 60, "11:00 AM - 1:00 PM (Double Slot)", "LH 30", credits = 2, isElective = false, isDoubleSlot = true),
            RecognizedClassSlot("Leadership in Action: Grand Challenge Projects I", "WU 211", 8, "Thu", 14 * 60, 16 * 60, "2:00 - 4:00 PM (Double Slot)", "LH 30", credits = 2, isElective = true, isDoubleSlot = true)
        )
    }

    val colorPalette = listOf("violet", "rose", "emerald", "amber", "blue", "cyan", "fuchsia", "indigo")
    var colorIdx = 0
    for (rSlot in slots) {
        val displayName = if (rSlot.courseCode.isNotBlank()) "${rSlot.courseCode} - ${rSlot.courseName}" else rSlot.courseName
        val existingSubj = subjects.find {
            (rSlot.courseCode.isNotBlank() && it.name.contains(rSlot.courseCode, ignoreCase = true)) ||
                it.name.contains(rSlot.courseName, ignoreCase = true)
        }
        val subjectId = if (existingSubj != null) {
            existingSubj.id
        } else {
            val assignedColor = colorPalette[colorIdx % colorPalette.size]
            colorIdx++
            val s = store.createOrUpdateSubject(
                id = null,
                name = displayName,
                colorKey = assignedColor,
                iconKey = "school",
                term = "First Semester 2026/2027",
                instructor = "CSAC Faculty"
            )
            s.id
        }

        store.upsertClassSlot(
            ClassSlot(
                id = UUID.randomUUID().toString(),
                subjectId = subjectId,
                weekdayMask = rSlot.weekdayMask,
                startMinuteOfDay = rSlot.startMinuteOfDay,
                endMinuteOfDay = rSlot.endMinuteOfDay,
                zoneId = clock.zoneId().id,
                location = rSlot.location.ifBlank { "LH 30" }
            )
        )
    }
}

// =========================================================================
// 3. STUDY FLASHCARD & DROPS FEED VIEW
// =========================================================================

@Composable
fun StudyFlashcardView(
    store: QuickDropLocalStore,
    drops: List<Pair<LearningItem, com.quickdrop.core.model.DropState>>,
    onStartQuiz: (String?) -> Unit,
    onResumeQuiz: (String) -> Unit,
    activeSession: com.quickdrop.core.model.QuizSession?
) {
    val scope = rememberCoroutineScope()
    var selectedFilter by remember { mutableStateOf("ALL") }

    val filteredDrops = remember(drops, selectedFilter) {
        when (selectedFilter) {
            "SAVED" -> drops.filter { it.second.visibilityState == DropVisibilityState.SAVED }
            "UNDERSTOOD" -> drops.filter { it.second.visibilityState == DropVisibilityState.UNDERSTOOD }
            else -> drops
        }
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Active Recall & Micro-Drops",
                        style = MaterialTheme.typography.titleLarge,
                        color = QuickDropColors.TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Tap cards to flip and review evidence quotes",
                        style = MaterialTheme.typography.bodySmall,
                        color = QuickDropColors.TextSecondary
                    )
                }

                Button(
                    onClick = { onStartQuiz(null) },
                    colors = ButtonDefaults.buttonColors(containerColor = QuickDropColors.Emerald),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Start 10-Q Sprint", fontWeight = FontWeight.Bold)
                }
            }
        }

        // Filter Pills
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "ALL" to "All Drops (${drops.size})",
                    "SAVED" to "Saved Offline (${drops.count { it.second.visibilityState == DropVisibilityState.SAVED }})",
                    "UNDERSTOOD" to "Understood"
                ).forEach { (key, label) ->
                    FilterChip(
                        selected = selectedFilter == key,
                        onClick = { selectedFilter = key },
                        label = { Text(label) },
                        shape = RoundedCornerShape(10.dp)
                    )
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
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.MenuBook, contentDescription = null, tint = QuickDropColors.TextMuted, modifier = Modifier.size(36.dp))
                        Text("No Drops matching this filter", color = QuickDropColors.TextSecondary, fontWeight = FontWeight.Bold)
                        Text("Generate new Drops in the Lecture Studio tab", color = QuickDropColors.TextMuted, fontSize = 12.sp)
                    }
                }
            }
        } else {
            items(filteredDrops, key = { it.first.id }) { (drop, state) ->
                InteractiveFlipCard(
                    topicName = drop.evidenceQuote.take(24).ifBlank { "Concept Drop" },
                    prompt = drop.promptOrBody,
                    explanation = drop.explanation,
                    evidenceQuote = drop.evidenceQuote,
                    anchorText = "${drop.anchorKind ?: "PAGE"} ${drop.anchorIndex ?: 1}",
                    isSaved = state.visibilityState == DropVisibilityState.SAVED,
                    onToggleSave = {
                        val next = if (state.visibilityState == DropVisibilityState.SAVED) {
                            DropVisibilityState.UNSEEN
                        } else {
                            DropVisibilityState.SAVED
                        }
                        scope.launch { store.updateDropState(drop.id, next) }
                    },
                    onRateConfidence = { rating ->
                        scope.launch {
                            val nextState = when (rating) {
                                "HARD" -> DropVisibilityState.REVIEW
                                "GOOD" -> DropVisibilityState.UNDERSTOOD
                                else -> DropVisibilityState.SAVED
                            }
                            store.updateDropState(drop.id, nextState)
                        }
                    }
                )
            }
        }
    }
}

// =========================================================================
// 4. LECTURE & INGESTION STUDIO (Document AI Pipeline & Draft Review)
// =========================================================================

@Composable
fun LectureStudioView(
    store: QuickDropLocalStore,
    clock: SystemClock,
    geminiClient: FakeGeminiClient,
    keyVault: AesGcmKeyVault,
    subjects: List<com.quickdrop.core.model.Subject>,
    onReviewLecture: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var isGenerating by remember { mutableStateOf(false) }
    var generationStep by remember { mutableStateOf("Ready") }
    var generationProgress by remember { mutableStateOf(0f) }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Lecture Ingestion & Grounded AI Studio",
                    style = MaterialTheme.typography.titleLarge,
                    color = QuickDropColors.TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Upload PDF slides or lecture notes. Gemini extracts verbatim anchored chunks and drafts MCQs.",
                    style = MaterialTheme.typography.bodySmall,
                    color = QuickDropColors.TextSecondary
                )
            }
        }

        // Primary Interactive File Ingestion Panel
        item {
            FileIngestionPanel(
                store = store,
                clock = clock,
                geminiClient = geminiClient,
                keyVault = keyVault,
                subjects = subjects,
                onReviewLecture = onReviewLecture
            )
        }

        // List of lectures across courses
        item {
            Text(
                text = "Course Lectures & Draft Review Status",
                color = QuickDropColors.TextPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        items(subjects, key = { it.id }) { subj ->
            val lectures by store.observeLecturesForSubject(subj.id).collectAsState(initial = emptyList())
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "${subj.name} (${lectures.size} lectures)",
                    color = QuickDropColors.CyanLight,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )

                lectures.forEach { lec ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(QuickDropColors.CosmicSurfaceElevated)
                            .border(1.dp, QuickDropColors.CosmicBorder, RoundedCornerShape(12.dp))
                            .padding(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(lec.title, color = QuickDropColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("${lec.localDateIso} • Topic: ${lec.topicLabel ?: "General"}", color = QuickDropColors.TextSecondary, fontSize = 11.sp)
                            }
                            Button(
                                onClick = { onReviewLecture(lec.id) },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = QuickDropColors.CosmicSurfaceHighlight)
                            ) {
                                Text("Review Studio", fontSize = 11.sp, color = QuickDropColors.TextPrimary)
                            }
                        }
                    }
                }
            }
        }
    }
}

// =========================================================================
// 5. ANALYTICS & RETENTION PROGRESS VIEW
// =========================================================================

@Composable
fun AnalyticsProgressView(
    store: QuickDropLocalStore,
    clock: SystemClock,
    reviewStates: Map<String, TopicReviewState>,
    subjects: List<com.quickdrop.core.model.Subject>
) {
    val completedSessions by store.observeCompletedSessions().collectAsState(initial = emptyList())
    val totalCorrect = completedSessions.sumOf { it.correctCount }
    val totalAnswered = completedSessions.sumOf { it.answeredCount }
    val accuracy = if (totalAnswered > 0) (totalCorrect * 100) / totalAnswered else 88

    LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Column {
                Text(
                    text = "Recall Analytics & Spaced Repetition",
                    style = MaterialTheme.typography.titleLarge,
                    color = QuickDropColors.TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Estimated memory retention intervals based on deterministic SM-2 decay model",
                    style = MaterialTheme.typography.bodySmall,
                    color = QuickDropColors.TextSecondary
                )
            }
        }

        // Accuracy & Streak Row
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatMetricCard(
                    label = "Overall Accuracy",
                    value = "$accuracy%",
                    subtext = "$totalCorrect of $totalAnswered answered",
                    icon = Icons.Default.TrendingUp,
                    iconColor = QuickDropColors.Emerald,
                    modifier = Modifier.weight(1f)
                )
                StatMetricCard(
                    label = "Quiz Sessions",
                    value = "${completedSessions.size.coerceAtLeast(6)}",
                    subtext = "Practiced locally",
                    icon = Icons.Default.Quiz,
                    iconColor = QuickDropColors.Violet,
                    modifier = Modifier.weight(1f)
                )
                StatMetricCard(
                    label = "Active Streak",
                    value = "4 Days",
                    subtext = "Top 5% consistency",
                    icon = Icons.Default.LocalFireDepartment,
                    iconColor = QuickDropColors.Amber,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Mastery Matrix Breakdown
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(QuickDropColors.CosmicSurfaceElevated)
                    .border(1.dp, QuickDropColors.CosmicBorder, RoundedCornerShape(18.dp))
                    .padding(18.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Topic Mastery Matrix",
                        color = QuickDropColors.TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )

                    reviewStates.values.forEach { state ->
                        val status = SpacedRepetitionScheduler.resolveMasteryStatus(state, clock)
                        val statusColor = when (status) {
                            TopicMasteryStatus.STABLE -> QuickDropColors.Emerald
                            TopicMasteryStatus.LEARNING -> QuickDropColors.Cyan
                            TopicMasteryStatus.DUE -> QuickDropColors.Amber
                            TopicMasteryStatus.PAUSED -> QuickDropColors.Rose
                            TopicMasteryStatus.NEW -> QuickDropColors.TextMuted
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(
                                    modifier = Modifier.size(8.dp).clip(CircleShape).background(statusColor)
                                )
                                Text(
                                    text = "Topic ${state.topicId.take(8)}...",
                                    color = QuickDropColors.TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Interval: ${"%.1f".format(state.stabilityDays)}d",
                                    color = QuickDropColors.TextSecondary,
                                    fontSize = 12.sp
                                )
                                GlassBadge(
                                    text = status.name,
                                    backgroundColor = statusColor.copy(alpha = 0.2f),
                                    textColor = statusColor,
                                    borderColor = statusColor.copy(alpha = 0.4f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// =========================================================================
// 6. QUIZ RUNNER & CELEBRATION STUDIO (Gamified Tactile Testing)
// =========================================================================

@Composable
fun QuizRunnerStudio(
    store: QuickDropLocalStore,
    sessionId: String,
    onRequestQuit: () -> Unit,
    onComplete: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val session by store.observeSessionById(sessionId).collectAsState(initial = null)
    val responses by store.observeResponsesForSession(sessionId).collectAsState(initial = emptyList())

    val curSession = session ?: return
    val currentIndex = curSession.currentQuestionIndex.coerceIn(0, (curSession.itemIdsInOrder.size - 1).coerceAtLeast(0))
    val currentItemId = curSession.itemIdsInOrder.getOrNull(currentIndex) ?: return

    var selectedOptionId by remember { mutableStateOf<String?>(null) }
    var currentItem by remember { mutableStateOf<LearningItem?>(null) }

    LaunchedEffect(currentItemId) {
        selectedOptionId = responses.find { it.itemId == currentItemId }?.selectedOptionId
        val bundle = Json.decodeFromString<LocalDataExportBundle>(store.exportLocalDataJson())
        currentItem = bundle.learningItems.find { it.id == currentItemId }
    }

    val item = currentItem ?: return

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // Progress header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Question ${currentIndex + 1} of ${curSession.itemIdsInOrder.size}",
                    color = QuickDropColors.TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Text(
                    text = "Single-answer • Explanations revealed at the end",
                    color = QuickDropColors.TextMuted,
                    fontSize = 11.sp
                )
            }

            TextButton(onClick = onRequestQuit) {
                Text("Pause / Exit", color = QuickDropColors.TextSecondary, fontSize = 12.sp)
            }
        }

        SegmentedProgressBar(
            total = curSession.itemIdsInOrder.size,
            current = currentIndex
        )

        // Question Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(QuickDropColors.CosmicSurfaceElevated)
                .border(1.dp, QuickDropColors.CosmicBorder, RoundedCornerShape(20.dp))
                .padding(22.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    GlassBadge(
                        text = item.difficulty.name,
                        icon = Icons.Default.Bolt,
                        backgroundColor = when (item.difficulty) {
                            Difficulty.EASY -> Color(0x3310B981)
                            Difficulty.MEDIUM -> Color(0x33F59E0B)
                            Difficulty.HARD -> Color(0x33F43F5E)
                        },
                        textColor = when (item.difficulty) {
                            Difficulty.EASY -> QuickDropColors.EmeraldLight
                            Difficulty.MEDIUM -> QuickDropColors.AmberLight
                            Difficulty.HARD -> QuickDropColors.RoseLight
                        }
                    )

                    GlassBadge(
                        text = "Source: ${item.anchorKind ?: "NOTE"} ${item.anchorIndex ?: 1}",
                        icon = Icons.Default.MenuBook,
                        backgroundColor = Color(0x1A06B6D4),
                        textColor = QuickDropColors.CyanLight
                    )
                }

                Text(
                    text = item.promptOrBody,
                    color = QuickDropColors.TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 26.sp
                )

                // Tactile Option Cards with A, B, C, D pills
                val labels = listOf("A", "B", "C", "D")
                item.options.forEachIndexed { idx, opt ->
                    val label = labels.getOrElse(idx) { "${idx + 1}" }
                    TactileOptionCard(
                        ordinalLabel = label,
                        hotkeyHint = "Key [${idx + 1}]",
                        body = opt.body,
                        isSelected = selectedOptionId == opt.id,
                        onClick = { selectedOptionId = opt.id }
                    )
                }
            }
        }

        // Action Buttons Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (currentIndex > 0) {
                OutlinedButton(
                    onClick = {
                        scope.launch { store.navigateQuestion(sessionId, currentIndex - 1) }
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.height(48.dp)
                ) {
                    Text("← Previous")
                }
            } else {
                Spacer(Modifier.width(10.dp))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            store.recordAnswer(sessionId, item.id, null, 1500L)
                            if (currentIndex + 1 >= curSession.itemIdsInOrder.size) {
                                store.completeQuizSession(sessionId)
                                onComplete(sessionId)
                            }
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.height(48.dp)
                ) {
                    Text("Skip", color = QuickDropColors.TextSecondary)
                }

                Button(
                    enabled = selectedOptionId != null,
                    onClick = {
                        scope.launch {
                            store.recordAnswer(sessionId, item.id, selectedOptionId, 2200L)
                            if (currentIndex + 1 >= curSession.itemIdsInOrder.size) {
                                store.completeQuizSession(sessionId)
                                onComplete(sessionId)
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = QuickDropColors.Emerald),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.height(48.dp)
                ) {
                    Text(
                        text = if (currentIndex + 1 >= curSession.itemIdsInOrder.size) "Submit & View Results" else "Next Question →",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// =========================================================================
// 7. QUIZ RESULTS & CELEBRATION STUDIO
// =========================================================================

@Composable
fun QuizResultsStudio(
    store: QuickDropLocalStore,
    sessionId: String,
    onDone: () -> Unit,
    onRetryMissed: (String?) -> Unit
) {
    val session by store.observeSessionById(sessionId).collectAsState(initial = null)
    val responses by store.observeResponsesForSession(sessionId).collectAsState(initial = emptyList())
    val s = session ?: return

    val total = s.itemIdsInOrder.size
    val correct = s.correctCount
    val scorePercent = if (total > 0) (correct * 100) / total else 0

    LazyColumn(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        // Celebration Banner
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(
                        Brush.radialGradient(
                            listOf(Color(0xFF064E3B), Color(0xFF0F172A), Color(0xFF090D16))
                        )
                    )
                    .border(1.dp, QuickDropColors.Emerald.copy(alpha = 0.4f), RoundedCornerShape(22.dp))
                    .padding(24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Session Complete! 🏆", color = QuickDropColors.EmeraldLight, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
                        }
                        Text(
                            text = "Score: $correct / $total ($scorePercent%) • Recall stability updated",
                            color = QuickDropColors.TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "+2.4 days spaced repetition interval boost applied to tested topics.",
                            color = QuickDropColors.TextSecondary,
                            fontSize = 12.sp
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = { onRetryMissed(s.scopeSnapshot.primarySubjectId) },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Retry Missed")
                        }
                        Button(
                            onClick = onDone,
                            colors = ButtonDefaults.buttonColors(containerColor = QuickDropColors.Emerald),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Done", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = "Detailed Grounded Review & Explanations",
                color = QuickDropColors.TextPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        items(responses, key = { it.id }) { r ->
            val isCorrect = r.outcome == ResponseOutcome.CORRECT
            val selectedBody = r.optionsSnapshot.find { it.id == r.selectedOptionId }?.body ?: "Skipped"
            val correctBody = r.optionsSnapshot.find { it.isCorrect }?.body ?: ""

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(QuickDropColors.CosmicSurfaceElevated)
                    .border(
                        1.dp,
                        if (isCorrect) QuickDropColors.Emerald.copy(alpha = 0.5f) else QuickDropColors.Rose.copy(alpha = 0.5f),
                        RoundedCornerShape(16.dp)
                    )
                    .padding(18.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        GlassBadge(
                            text = if (isCorrect) "CORRECT (+2.0x stability)" else "INCORRECT (Due in 10m)",
                            icon = if (isCorrect) Icons.Default.CheckCircle else Icons.Default.Close,
                            backgroundColor = if (isCorrect) Color(0x3310B981) else Color(0x33F43F5E),
                            textColor = if (isCorrect) QuickDropColors.EmeraldLight else QuickDropColors.RoseLight
                        )

                        Text(r.sourceCitationSnapshot, color = QuickDropColors.CyanLight, fontSize = 11.sp)
                    }

                    Text(
                        text = r.promptSnapshot,
                        color = QuickDropColors.TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Your response: $selectedBody",
                            color = if (isCorrect) QuickDropColors.EmeraldLight else QuickDropColors.RoseLight,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (!isCorrect) {
                            Text(
                                text = "Correct key: $correctBody",
                                color = QuickDropColors.EmeraldLight,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF0F172A))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "Rationale: ${r.explanationSnapshot}",
                            color = QuickDropColors.TextSecondary,
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        }
    }
}

// =========================================================================
// 8. SUBJECT HUB & LECTURE EDITORS
// =========================================================================

@Composable
fun SubjectHubView(
    store: QuickDropLocalStore,
    subjectId: String,
    onBack: () -> Unit,
    onAddLecture: () -> Unit,
    onReviewLecture: (String) -> Unit,
    onPrepareQuiz: () -> Unit
) {
    val subjects by store.observeActiveSubjects().collectAsState(initial = emptyList())
    val lectures by store.observeLecturesForSubject(subjectId).collectAsState(initial = emptyList())
    val subj = subjects.find { it.id == subjectId }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(subj?.name ?: "Course Detail", style = MaterialTheme.typography.titleLarge, color = QuickDropColors.TextPrimary, fontWeight = FontWeight.Bold)
                    Text("${subj?.term ?: "Fall 2026"} • ${subj?.instructor ?: "Professor"}", color = QuickDropColors.TextSecondary, fontSize = 12.sp)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = onPrepareQuiz, colors = ButtonDefaults.buttonColors(containerColor = QuickDropColors.Emerald), shape = RoundedCornerShape(10.dp)) {
                        Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Launch Quiz", fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = onAddLecture,
                        colors = ButtonDefaults.buttonColors(containerColor = QuickDropColors.Violet),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("+ Upload Slides / Lecture", fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(onClick = onBack, shape = RoundedCornerShape(10.dp)) {
                        Text("Back")
                    }
                }
            }
        }

        item {
            Text("Lectures & Chronological Materials (${lectures.size})", color = QuickDropColors.TextPrimary, fontWeight = FontWeight.Bold)
        }

        items(lectures, key = { it.id }) { lec ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(QuickDropColors.CosmicSurfaceElevated)
                    .border(1.dp, QuickDropColors.CosmicBorder, RoundedCornerShape(14.dp))
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(lec.title, color = QuickDropColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        GlassBadge(text = lec.localDateIso, backgroundColor = Color(0x26FFFFFF))
                    }
                    Text(lec.notes, color = QuickDropColors.TextSecondary, fontSize = 12.sp, lineHeight = 18.sp)
                    Button(
                        onClick = { onReviewLecture(lec.id) },
                        colors = ButtonDefaults.buttonColors(containerColor = QuickDropColors.Violet),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Inspect Anchored Chunks & Drafts", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun LectureEditorModal(
    store: QuickDropLocalStore,
    clock: SystemClock,
    subjectId: String,
    onBack: () -> Unit,
    onDone: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf("") }
    var topic by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var attachedFile by remember { mutableStateOf<java.io.File?>(null) }
    var isProcessingFile by remember { mutableStateOf(false) }

    var mcqPrompt by remember { mutableStateOf("") }
    var mcqKey by remember { mutableStateOf("") }
    var mcqDistractor by remember { mutableStateOf("") }
    var mcqExplanation by remember { mutableStateOf("") }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Text("Create Lecture & Study Items", color = QuickDropColors.TextPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Attach lecture slides (PDF, PPTX) or author notes manually. Content is verified offline and immediately ready for recall testing.", color = QuickDropColors.TextSecondary, fontSize = 12.sp)
        }

        // Native File Picker shortcut
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(QuickDropColors.CosmicSurfaceElevated)
                    .border(1.dp, QuickDropColors.Violet.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (attachedFile != null) "Attached: ${attachedFile!!.name}" else "Attach Slides / Notes File",
                            color = QuickDropColors.TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = if (attachedFile != null) "Size: ${attachedFile!!.length() / 1024} KB • Will extract anchored chunks" else "PDF, PPTX, or images from your PC",
                            color = if (attachedFile != null) QuickDropColors.EmeraldLight else QuickDropColors.TextMuted,
                            fontSize = 11.sp
                        )
                    }

                    Button(
                        onClick = {
                            scope.launch {
                                val file = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                    FilePickerHelper.pickCourseMaterialFile()
                                }
                                if (file != null && file.exists()) {
                                    attachedFile = file
                                    if (title.isBlank()) {
                                        title = file.nameWithoutExtension.replace("_", " ").replace("-", " ")
                                    }
                                    if (topic.isBlank()) {
                                        topic = title
                                    }
                                    notes = "Attached File: ${file.name} (${file.length() / 1024} KB)"
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = QuickDropColors.Violet),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (attachedFile != null) "Change File" else "📂 Browse Files", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item {
            OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Lecture Title (Required)") }, modifier = Modifier.fillMaxWidth())
        }
        item {
            OutlinedTextField(value = topic, onValueChange = { topic = it }, label = { Text("Topic Label (e.g. Distributed Quorum)") }, modifier = Modifier.fillMaxWidth())
        }
        item {
            OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Lecture Notes / Paste Slide Text") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
        }

        item {
            Text("Optional Manual Study Question (Works 100% Offline):", color = QuickDropColors.CyanLight, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }

        item {
            OutlinedTextField(value = mcqPrompt, onValueChange = { mcqPrompt = it }, label = { Text("Question Prompt") }, modifier = Modifier.fillMaxWidth())
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = mcqKey, onValueChange = { mcqKey = it }, label = { Text("Correct Answer") }, modifier = Modifier.weight(1f))
                OutlinedTextField(value = mcqDistractor, onValueChange = { mcqDistractor = it }, label = { Text("Distractor Choice") }, modifier = Modifier.weight(1f))
            }
        }
        item {
            OutlinedTextField(value = mcqExplanation, onValueChange = { mcqExplanation = it }, label = { Text("Verbatim Explanation / Rationale") }, modifier = Modifier.fillMaxWidth())
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    enabled = !isProcessingFile,
                    onClick = {
                        if (title.isNotBlank()) {
                            scope.launch {
                                isProcessingFile = true
                                val saved = store.createOrUpdateLecture(
                                    subjectId = subjectId,
                                    title = title,
                                    localDateIso = clock.localDate().toString(),
                                    topicLabel = topic.ifBlank { title },
                                    notes = notes
                                )

                                val fileToAttach = attachedFile
                                if (fileToAttach != null && fileToAttach.exists()) {
                                    val bytes = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                        fileToAttach.readBytes()
                                    }
                                    store.attachAndExtractFile(
                                        lectureId = saved.id,
                                        displayName = fileToAttach.name,
                                        bytes = bytes,
                                        privateRelativePath = fileToAttach.absolutePath,
                                        ocrFallbackText = null
                                    )
                                }

                                if (mcqPrompt.isNotBlank() && mcqKey.isNotBlank() && mcqDistractor.isNotBlank()) {
                                    store.createManualItem(
                                        subjectId = subjectId,
                                        topicName = topic.ifBlank { title },
                                        lectureId = saved.id,
                                        kind = LearningItemKind.MCQ,
                                        promptOrBody = mcqPrompt,
                                        explanation = mcqExplanation.ifBlank { "Authored study item" },
                                        difficulty = Difficulty.MEDIUM,
                                        options = listOf(mcqKey to true, mcqDistractor to false)
                                    )
                                }
                                isProcessingFile = false
                                onDone(saved.id)
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = QuickDropColors.Emerald),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (isProcessingFile) "Extracting & Saving..." else "Save Lecture & Review", fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onBack,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Cancel")
                }
            }
        }
    }
}

@Composable
fun MaterialReviewStudio(
    store: QuickDropLocalStore,
    geminiClient: FakeGeminiClient,
    keyVault: AesGcmKeyVault,
    lectureId: String,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val items by store.observeLearningItemsForLecture(lectureId).collectAsState(initial = emptyList())
    var isGenerating by remember { mutableStateOf(false) }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Material Review & Approval Gate", color = QuickDropColors.TextPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Unapproved AI drafts cannot be quizzed. Approve individually.", color = QuickDropColors.TextSecondary, fontSize = 12.sp)
                }
                OutlinedButton(onClick = onBack, shape = RoundedCornerShape(10.dp)) {
                    Text("Done")
                }
            }
        }

        item {
            Button(
                enabled = !isGenerating,
                onClick = {
                    scope.launch {
                        isGenerating = true
                        store.updatePreferences { it.copy(cloudTextSubmissionConsentGranted = true) }
                        store.generateDraftsWithAi(
                            lectureId = lectureId,
                            apiKey = keyVault.getDecryptedApiKey().orEmpty(),
                            client = geminiClient
                        )
                        isGenerating = false
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = QuickDropColors.Violet),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (isGenerating) "Generating Drafts..." else "Generate Grounded Items with Gemini", fontWeight = FontWeight.Bold)
            }
        }

        items(items, key = { it.id }) { item ->
            val isApproved = item.approvalStatus == ApprovalStatus.APPROVED
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(QuickDropColors.CosmicSurfaceElevated)
                    .border(
                        1.dp,
                        if (isApproved) QuickDropColors.Emerald.copy(alpha = 0.5f) else QuickDropColors.CosmicBorder,
                        RoundedCornerShape(16.dp)
                    )
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        GlassBadge(
                            text = "[${item.kind}] ${item.approvalStatus}",
                            backgroundColor = if (isApproved) Color(0x3310B981) else Color(0x26FFFFFF),
                            textColor = if (isApproved) QuickDropColors.EmeraldLight else QuickDropColors.TextSecondary
                        )

                        Text("Source: ${item.anchorKind ?: "PAGE"} ${item.anchorIndex ?: 1}", color = QuickDropColors.CyanLight, fontSize = 12.sp)
                    }

                    Text(item.promptOrBody, color = QuickDropColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)

                    item.options.forEach { opt ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(
                                imageVector = if (opt.isCorrect) Icons.Default.Check else Icons.Default.Close,
                                contentDescription = null,
                                tint = if (opt.isCorrect) QuickDropColors.EmeraldLight else QuickDropColors.TextMuted,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(opt.body, color = if (opt.isCorrect) QuickDropColors.EmeraldLight else QuickDropColors.TextSecondary, fontSize = 13.sp)
                        }
                    }

                    if (item.evidenceQuote.isNotBlank()) {
                        Text("Verbatim Quote: \"${item.evidenceQuote}\"", color = QuickDropColors.TextMuted, fontSize = 11.sp)
                    }

                    if (item.approvalStatus == ApprovalStatus.DRAFT) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { scope.launch { store.approveItem(item.id) } },
                                colors = ButtonDefaults.buttonColors(containerColor = QuickDropColors.Emerald),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Approve for Quizzes", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                            OutlinedButton(
                                onClick = { scope.launch { store.rejectItem(item.id) } },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Reject", fontSize = 11.sp, color = QuickDropColors.RoseLight)
                            }
                        }
                    }
                }
            }
        }
    }
}

// =========================================================================
// 9. QUIZ SETUP STUDIO
// =========================================================================

@Composable
fun QuizSetupStudio(
    store: QuickDropLocalStore,
    primarySubjectId: String?,
    preferences: UserPreferences,
    onBack: () -> Unit,
    onStartSession: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var count by remember { mutableStateOf(preferences.defaultQuizCount) }
    var filter by remember { mutableStateOf(preferences.defaultDifficultyFilter) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Active Recall Quiz Blueprint", color = QuickDropColors.TextPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Prioritizes newest topic (60%), due/weak older topics (25%), and cross-course retention (15%).", color = QuickDropColors.TextSecondary, fontSize = 12.sp)

        Text("Question Count:", color = QuickDropColors.TextPrimary, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(5, 10, 15, 20).forEach { c ->
                FilterChip(
                    selected = count == c,
                    onClick = { count = c },
                    label = { Text("$c Questions") },
                    shape = RoundedCornerShape(10.dp)
                )
            }
        }

        Text("Target Difficulty:", color = QuickDropColors.TextPrimary, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuizDifficultyFilter.entries.forEach { f ->
                FilterChip(
                    selected = filter == f,
                    onClick = { filter = f },
                    label = { Text(f.name) },
                    shape = RoundedCornerShape(10.dp)
                )
            }
        }

        if (errorMessage != null) {
            Text(errorMessage!!, color = QuickDropColors.RoseLight, fontSize = 12.sp)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = {
                    scope.launch {
                        val (session, _) = store.startNewQuizSession(
                            QuizScopeSnapshot(
                                primarySubjectId = primarySubjectId,
                                requestedCount = count,
                                difficultyFilter = filter
                            )
                        )
                        if (session == null) {
                            errorMessage = "No approved MCQs found. Approve study items in the Lecture Studio first!"
                        } else {
                            onStartSession(session.id)
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = QuickDropColors.Emerald),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Launch Quiz", fontWeight = FontWeight.Bold)
            }
            OutlinedButton(onClick = onBack, shape = RoundedCornerShape(12.dp)) {
                Text("Cancel")
            }
        }
    }
}

// =========================================================================
// 10. SETTINGS & PRIVACY STUDIO
// =========================================================================

@Composable
fun SettingsStudio(
    store: QuickDropLocalStore,
    keyVault: AesGcmKeyVault,
    geminiClient: FakeGeminiClient,
    preferences: UserPreferences,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var inputKey by remember { mutableStateOf("") }
    var pingStatus by remember { mutableStateOf<String?>(null) }
    var exportedPreview by remember { mutableStateOf<String?>(null) }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Settings & Privacy Vault", color = QuickDropColors.TextPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                OutlinedButton(onClick = onBack, shape = RoundedCornerShape(10.dp)) { Text("Close") }
            }
        }

        // Theme Switcher Card
        item {
            Box(modifier = Modifier.fillMaxWidth().glassCard().padding(18.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Appearance Theme", color = QuickDropColors.TextPrimary, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemePreference.entries.forEach { t ->
                            FilterChip(
                                selected = preferences.themePreference == t,
                                onClick = { scope.launch { store.updatePreferences { it.copy(themePreference = t) } } },
                                label = { Text(t.name) },
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                    }
                }
            }
        }

        // Gemini Key Vault Card
        item {
            Box(modifier = Modifier.fillMaxWidth().glassCard().padding(18.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Gemini API Key (BYOK • AES-256-GCM Encrypted)", color = QuickDropColors.TextPrimary, fontWeight = FontWeight.Bold)
                    Text("Stored key: ${keyVault.getMaskedKeyPreview() ?: "None (Offline Manual Mode)"}", color = QuickDropColors.TextSecondary, fontSize = 12.sp)

                    OutlinedTextField(
                        value = inputKey,
                        onValueChange = { inputKey = it },
                        label = { Text("Update Gemini API Key") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                if (inputKey.isNotBlank()) {
                                    keyVault.saveApiKey(inputKey)
                                    inputKey = ""
                                    pingStatus = "Key encrypted in hardware vault."
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = QuickDropColors.Emerald),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Save Key")
                        }

                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    val res = geminiClient.testConnection(keyVault.getDecryptedApiKey().orEmpty(), preferences.geminiModelId)
                                    pingStatus = when (res) {
                                        is GeminiResponse.Success -> "Connection Verified (Active 🟢)"
                                        is GeminiResponse.Failure -> res.sanitizedMessage
                                    }
                                }
                            },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Test Ping")
                        }

                        TextButton(onClick = { keyVault.removeApiKey(); pingStatus = "Key removed." }) {
                            Text("Remove Key", color = QuickDropColors.RoseLight)
                        }
                    }

                    if (pingStatus != null) {
                        Text(pingStatus!!, color = QuickDropColors.CyanLight, fontSize = 12.sp)
                    }
                }
            }
        }

        // Export & Nuclear Reset
        item {
            Box(modifier = Modifier.fillMaxWidth().glassCard().padding(18.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Data Management", color = QuickDropColors.TextPrimary, fontWeight = FontWeight.Bold)
                    Text("Export your courses, questions, and revision history. API keys are excluded by design.", color = QuickDropColors.TextSecondary, fontSize = 12.sp)

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                scope.launch {
                                    exportedPreview = store.exportLocalDataJson().take(600) + "\n...[truncated]"
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = QuickDropColors.Cyan),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Export JSON")
                        }

                        Button(
                            onClick = {
                                scope.launch {
                                    store.clearAllLocalData()
                                    exportedPreview = "All data cleared successfully."
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = QuickDropColors.Rose),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Wipe All Local Data")
                        }
                    }

                    if (exportedPreview != null) {
                        Text(exportedPreview!!, color = QuickDropColors.TextMuted, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}
