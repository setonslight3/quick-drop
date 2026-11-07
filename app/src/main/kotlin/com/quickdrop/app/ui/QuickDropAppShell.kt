package com.quickdrop.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.quickdrop.app.QuickDropApplication
import com.quickdrop.core.domain.ClassOccurrence
import com.quickdrop.core.domain.SpacedRepetitionScheduler
import com.quickdrop.core.model.ApprovalStatus
import com.quickdrop.core.model.ClassSlot
import com.quickdrop.core.model.Difficulty
import com.quickdrop.core.model.DropVisibilityState
import com.quickdrop.core.model.LearningItemKind
import com.quickdrop.core.model.QuizScopeSnapshot
import com.quickdrop.core.model.ResponseOutcome
import com.quickdrop.core.model.ScheduleException
import com.quickdrop.core.model.ScheduleExceptionAction
import com.quickdrop.core.model.ThemePreference
import com.quickdrop.core.model.TopicMasteryStatus
import com.quickdrop.core.model.UserPreferences
import kotlinx.coroutines.launch

enum class BottomNavTab(val label: String) {
    HOME("Home"),
    TIMETABLE("Timetable"),
    STUDY("Study"),
    PROGRESS("Progress")
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
            title = { Text("Pause or Discard Quiz?") },
            text = {
                Text("Your answered questions are saved locally. You can resume later from Home or Study, or discard this session without affecting your topic mastery.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showQuitQuizConfirmDialog = false
                        activeScreen = ActiveScreen.MainTabs
                    }
                ) {
                    Text("Save & Resume Later")
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
                    Text("Discard Session")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Quick Drop")
                        Text(
                            text = "Local-first study companion • Offline ready",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { activeScreen = ActiveScreen.Settings },
                        modifier = Modifier.semantics { contentDescription = "Open Settings" }
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        bottomBar = {
            if (activeScreen == ActiveScreen.MainTabs) {
                NavigationBar {
                    NavigationBarItem(
                        selected = selectedTab == BottomNavTab.HOME,
                        onClick = { selectedTab = BottomNavTab.HOME },
                        icon = { Icon(Icons.Default.Home, contentDescription = null) },
                        label = { Text("Home") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == BottomNavTab.TIMETABLE,
                        onClick = { selectedTab = BottomNavTab.TIMETABLE },
                        icon = { Icon(Icons.Default.CalendarMonth, contentDescription = null) },
                        label = { Text("Timetable") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == BottomNavTab.STUDY,
                        onClick = { selectedTab = BottomNavTab.STUDY },
                        icon = { Icon(Icons.Default.Quiz, contentDescription = null) },
                        label = { Text("Study") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == BottomNavTab.PROGRESS,
                        onClick = { selectedTab = BottomNavTab.PROGRESS },
                        icon = { Icon(Icons.Default.Insights, contentDescription = null) },
                        label = { Text("Progress") }
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            when (val screen = activeScreen) {
                ActiveScreen.MainTabs -> {
                    when (selectedTab) {
                        BottomNavTab.HOME -> HomeScreen(
                            app = app,
                            onOpenSubject = { activeScreen = ActiveScreen.SubjectDetail(it) },
                            onOpenQuizSetup = { activeScreen = ActiveScreen.QuizSetup(it) },
                            onResumeSession = { activeScreen = ActiveScreen.QuizRunner(it) },
                            onAddLecture = { activeScreen = ActiveScreen.LectureEditor(it, null) }
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

@Composable
private fun HomeScreen(
    app: QuickDropApplication,
    onOpenSubject: (String) -> Unit,
    onOpenQuizSetup: (String?) -> Unit,
    onResumeSession: (String) -> Unit,
    onAddLecture: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val subjects by app.localStore.observeActiveSubjects().collectAsState(initial = emptyList())
    val activeSession by app.localStore.observeActiveSession().collectAsState(initial = null)
    val reviewStates by app.localStore.observeTopicReviewStates().collectAsState(initial = emptyMap())
    val drops by app.localStore.observeApprovedDrops().collectAsState(initial = emptyList())
    var upcomingOccurrences by remember { mutableStateOf<List<ClassOccurrence>>(emptyList()) }
    var newSubjectName by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(subjects) {
        upcomingOccurrences = app.localStore.getUpcomingOccurrences(daysAhead = 7)
    }

    val dueCount = reviewStates.values.count { state ->
        val due = state.dueAtEpochMs
        due != null && due <= app.clock.now().toEpochMilli()
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text(
                text = "Today: ${app.clock.localDate()}",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "Due topics for review: $dueCount",
                style = MaterialTheme.typography.bodyMedium
            )
        }

        if (activeSession != null) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Quiz in progress", style = MaterialTheme.typography.titleSmall)
                        Text("Question ${activeSession!!.currentQuestionIndex + 1} of ${activeSession!!.itemIdsInOrder.size}")
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { onResumeSession(activeSession!!.id) },
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text("Continue Quiz")
                        }
                    }
                }
            }
        }

        item {
            Text("Upcoming Classes", style = MaterialTheme.typography.titleMedium)
        }

        if (upcomingOccurrences.isEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("No classes scheduled yet.")
                        Text("Add a subject below and configure recurring slots in the Timetable tab.")
                    }
                }
            }
        } else {
            items(upcomingOccurrences.take(5), key = { it.occurrenceId }) { occ ->
                val subject = subjects.find { it.id == occ.subjectId }
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = subject?.name ?: "Course",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "${occ.effectiveLocalDate} • ${occ.startZoned.toLocalTime()} - ${occ.endZoned.toLocalTime()} (${occ.location.ifBlank { "No room" }})"
                        )
                        if (occ.isMoved) {
                            Text("Moved occurrence (one-off exception)", color = MaterialTheme.colorScheme.tertiary)
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { onOpenQuizSetup(occ.subjectId) },
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Text("Prepare")
                            }
                            OutlinedButton(
                                onClick = { onAddLecture(occ.subjectId) },
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Text("Add Material")
                            }
                        }
                    }
                }
            }
        }

        item {
            Text("Subjects", style = MaterialTheme.typography.titleMedium)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = newSubjectName,
                    onValueChange = { newSubjectName = it },
                    label = { Text("New Subject Name") },
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = {
                        if (newSubjectName.isNotBlank()) {
                            val nameToCreate = newSubjectName
                            newSubjectName = ""
                            scope.launch {
                                app.localStore.createOrUpdateSubject(
                                    name = nameToCreate,
                                    colorKey = "green",
                                    iconKey = "book"
                                )
                            }
                        }
                    },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Add")
                }
            }
        }

        items(subjects, key = { it.id }) { subject ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(subject.name, style = MaterialTheme.typography.titleSmall)
                        val termValue = subject.term
                        if (!termValue.isNullOrBlank()) {
                            Text(termValue, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { onOpenSubject(subject.id) },
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text("Open")
                        }
                        Button(
                            onClick = { onOpenQuizSetup(subject.id) },
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text("Quiz")
                        }
                    }
                }
            }
        }

        if (drops.isNotEmpty()) {
            item {
                val (latestDrop, _) = drops.first()
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Recent Drop", style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(4.dp))
                        Text(latestDrop.promptOrBody)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TimetableScreen(
    app: QuickDropApplication,
    onOpenSubject: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val subjects by app.localStore.observeActiveSubjects().collectAsState(initial = emptyList())
    val slots by app.localStore.observeSlots().collectAsState(initial = emptyList())
    val exceptions by app.localStore.observeExceptions().collectAsState(initial = emptyList())

    var selectedSubjectId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedWeekdayIso by rememberSaveable { mutableStateOf(1) }
    var startHour by rememberSaveable { mutableStateOf("09") }
    var endHour by rememberSaveable { mutableStateOf("10") }
    var location by rememberSaveable { mutableStateOf("Room 101") }
    var overlapWarningMessage by remember { mutableStateOf<String?>(null) }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Weekly Timetable & Agenda", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Time zone: ${app.clock.zoneId().id} • One-off exceptions cancel or move a single date without altering your recurring template.",
                style = MaterialTheme.typography.bodySmall
            )
        }

        if (subjects.isNotEmpty()) {
            item {
                val activeSubjId = selectedSubjectId ?: subjects.first().id
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Add Recurring Class Slot", style = MaterialTheme.typography.titleSmall)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            subjects.forEach { subj ->
                                FilterChip(
                                    selected = subj.id == activeSubjId,
                                    onClick = { selectedSubjectId = subj.id },
                                    label = { Text(subj.name) }
                                )
                            }
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            val dayLabels = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
                            dayLabels.forEachIndexed { idx, label ->
                                val iso = idx + 1
                                FilterChip(
                                    selected = selectedWeekdayIso == iso,
                                    onClick = { selectedWeekdayIso = iso },
                                    label = { Text(label) }
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = startHour,
                                onValueChange = { startHour = it },
                                label = { Text("Start Hour (0-22)") },
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = endHour,
                                onValueChange = { endHour = it },
                                label = { Text("End Hour (1-23)") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        OutlinedTextField(
                            value = location,
                            onValueChange = { location = it },
                            label = { Text("Location") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (overlapWarningMessage != null) {
                            Text(
                                text = overlapWarningMessage!!,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Button(
                            onClick = {
                                val sH = (startHour.toIntOrNull() ?: 9).coerceIn(0, 22)
                                val eH = (endHour.toIntOrNull() ?: (sH + 1)).coerceIn(sH + 1, 23)
                                scope.launch {
                                    val (_, warnings) = app.localStore.upsertClassSlot(
                                        ClassSlot(
                                            id = java.util.UUID.randomUUID().toString(),
                                            subjectId = activeSubjId,
                                            weekdayMask = 1 shl (selectedWeekdayIso - 1),
                                            startMinuteOfDay = sH * 60,
                                            endMinuteOfDay = eH * 60,
                                            zoneId = app.clock.zoneId().id,
                                            location = location,
                                            enabled = true,
                                            reminderEnabled = true,
                                            reminderLeadMinutes = 15
                                        )
                                    )
                                    overlapWarningMessage = if (warnings.isNotEmpty()) {
                                        "Note: This slot overlaps with ${warnings.size} existing slot(s). Saved as requested."
                                    } else {
                                        null
                                    }
                                    app.rescheduleRemindersAsync()
                                }
                            },
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text("Save Class Slot")
                        }
                    }
                }
            }
        }

        items(slots, key = { it.id }) { slot ->
            val subject = subjects.find { it.id == slot.subjectId }
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "${subject?.name ?: "Course"} • ${slot.location}",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = "Time: %02d:%02d - %02d:%02d (%s)".format(
                            slot.startMinuteOfDay / 60,
                            slot.startMinuteOfDay % 60,
                            slot.endMinuteOfDay / 60,
                            slot.endMinuteOfDay % 60,
                            slot.zoneId
                        )
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    app.localStore.upsertException(
                                        ScheduleException(
                                            id = java.util.UUID.randomUUID().toString(),
                                            slotId = slot.id,
                                            originalLocalDateIso = app.clock.localDate().toString(),
                                            action = ScheduleExceptionAction.CANCEL
                                        )
                                    )
                                    app.rescheduleRemindersAsync()
                                }
                            },
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text("Cancel Today Only")
                        }
                        TextButton(
                            onClick = { onOpenSubject(slot.subjectId) },
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text("Subject Detail")
                        }
                    }
                }
            }
        }

        if (exceptions.isNotEmpty()) {
            item {
                Text("Active Date Exceptions (${exceptions.size})", style = MaterialTheme.typography.titleSmall)
            }
            items(exceptions, key = { it.id }) { ex ->
                AssistChip(
                    onClick = {
                        scope.launch { app.localStore.removeException(ex.id) }
                    },
                    label = { Text("${ex.originalLocalDateIso}: ${ex.action} (Tap to restore)") }
                )
            }
        }
    }
}

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

    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(subject?.name ?: "Subject Detail", style = MaterialTheme.typography.headlineSmall)
                OutlinedButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Back")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onPrepareQuiz, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Prepare Quiz")
                }
                OutlinedButton(onClick = onAddLecture, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Add Lecture / Material")
                }
            }
        }

        item {
            Text("Topics (${topics.size})", style = MaterialTheme.typography.titleMedium)
            if (topics.isEmpty()) {
                Text("No topics created yet. Add a lecture or manual study item.")
            }
        }

        item {
            Text("Lectures (Newest First)", style = MaterialTheme.typography.titleMedium)
        }

        items(lectures, key = { it.id }) { lecture ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("${lecture.localDateIso} — ${lecture.title}", style = MaterialTheme.typography.titleSmall)
                    if (!lecture.topicLabel.isNullOrBlank()) {
                        Text("Topic: ${lecture.topicLabel}", style = MaterialTheme.typography.bodySmall)
                    }
                    if (lecture.notes.isNotBlank()) {
                        Text(lecture.notes.take(140), style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { onReviewMaterial(lecture.id) },
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text("Review Items & Drafts")
                    }
                }
            }
        }
    }
}

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
    var dateIso by rememberSaveable { mutableStateOf(app.clock.localDate().toString()) }
    var topicLabel by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var manualQuestionPrompt by rememberSaveable { mutableStateOf("") }
    var manualCorrectAnswer by rememberSaveable { mutableStateOf("") }
    var manualDistractor by rememberSaveable { mutableStateOf("") }
    var manualExplanation by rememberSaveable { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Add Lecture & Study Content", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Lecture Title (Required)") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = dateIso,
                onValueChange = { dateIso = it },
                label = { Text("Lecture Date (YYYY-MM-DD)") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = topicLabel,
                onValueChange = { topicLabel = it },
                label = { Text("Topic Label (Optional)") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Lecture Notes / Paste Excerpt") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Manual MCQ Creation (Works Offline Without AI Key)", style = MaterialTheme.typography.titleSmall)
                    OutlinedTextField(
                        value = manualQuestionPrompt,
                        onValueChange = { manualQuestionPrompt = it },
                        label = { Text("Question Prompt") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = manualCorrectAnswer,
                        onValueChange = { manualCorrectAnswer = it },
                        label = { Text("Correct Answer Option") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = manualDistractor,
                        onValueChange = { manualDistractor = it },
                        label = { Text("Distractor Option") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = manualExplanation,
                        onValueChange = { manualExplanation = it },
                        label = { Text("Explanation") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        if (statusMessage != null) {
            item {
                Text(statusMessage!!, color = MaterialTheme.colorScheme.primary)
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        if (title.isBlank()) {
                            statusMessage = "Lecture title is required."
                            return@Button
                        }
                        scope.launch {
                            val savedLecture = app.localStore.createOrUpdateLecture(
                                id = lectureId,
                                subjectId = subjectId,
                                title = title,
                                localDateIso = dateIso,
                                topicLabel = topicLabel.ifBlank { title },
                                notes = notes
                            )
                            if (manualQuestionPrompt.isNotBlank() && manualCorrectAnswer.isNotBlank() && manualDistractor.isNotBlank()) {
                                app.localStore.createManualItem(
                                    subjectId = subjectId,
                                    topicName = topicLabel.ifBlank { title },
                                    lectureId = savedLecture.id,
                                    kind = LearningItemKind.MCQ,
                                    promptOrBody = manualQuestionPrompt,
                                    explanation = manualExplanation.ifBlank { "Manual study item" },
                                    difficulty = Difficulty.MEDIUM,
                                    options = listOf(
                                        manualCorrectAnswer to true,
                                        manualDistractor to false
                                    )
                                )
                            }
                            onOpenMaterialReview(savedLecture.id)
                        }
                    },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Save Lecture")
                }
                OutlinedButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Cancel")
                }
            }
        }
    }
}

@Composable
private fun MaterialReviewScreen(
    app: QuickDropApplication,
    lectureId: String,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val items by app.localStore.observeLearningItemsForLecture(lectureId).collectAsState(initial = emptyList())
    val aiJobs by app.localStore.observeAiJobs().collectAsState(initial = emptyList())
    val preferences by app.localStore.observePreferences().collectAsState(initial = UserPreferences())
    var showConsentDialog by remember { mutableStateOf(false) }

    val latestJobForLecture = aiJobs.lastOrNull { it.lectureId == lectureId }

    if (showConsentDialog) {
        AlertDialog(
            onDismissRequest = { showConsentDialog = false },
            title = { Text("Send Selected Text to Google Gemini?") },
            text = {
                Text("To generate draft questions and Drops, Quick Drop sends the extracted text from this lecture directly to Google using your Gemini API key. Generated items remain unapproved drafts until you review and approve each one.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConsentDialog = false
                        scope.launch {
                            app.localStore.updatePreferences { it.copy(cloudTextSubmissionConsentGranted = true) }
                            val key = app.apiKeyVault.getDecryptedApiKey().orEmpty()
                            app.localStore.generateDraftsWithAi(
                                lectureId = lectureId,
                                apiKey = key,
                                client = app.activeGeminiClient()
                            )
                        }
                    }
                ) {
                    Text("Consent & Generate Drafts")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConsentDialog = false }) {
                    Text("Stay in Manual Mode")
                }
            }
        )
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Material & Draft Review", style = MaterialTheme.typography.titleLarge)
                OutlinedButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Done")
                }
            }
            Text(
                text = "Only individually approved items enter quizzes or Drops. Bulk approval is disabled by design.",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    if (!preferences.cloudTextSubmissionConsentGranted) {
                        showConsentDialog = true
                    } else {
                        scope.launch {
                            val key = app.apiKeyVault.getDecryptedApiKey().orEmpty()
                            app.localStore.generateDraftsWithAi(
                                lectureId = lectureId,
                                apiKey = key,
                                client = app.activeGeminiClient()
                            )
                        }
                    }
                },
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text("Generate Drafts with Gemini (BYOK)")
            }
            if (latestJobForLecture != null) {
                Text(
                    text = "AI Job State: ${latestJobForLecture.state} • ${latestJobForLecture.sanitizedStatusMessage}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        items(items, key = { it.id }) { item ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "[${item.kind}] Status: ${item.approvalStatus} (v${item.version}) • ${item.difficulty}",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Text(item.promptOrBody, style = MaterialTheme.typography.titleSmall)
                    if (item.anchorKind != null && item.anchorIndex != null) {
                        Text(
                            text = "Source Anchor: ${item.anchorKind} ${item.anchorIndex} | Evidence: \"${item.evidenceQuote}\"",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    item.options.forEach { opt ->
                        val marker = if (opt.isCorrect) "[Correct Key]" else "[Distractor]"
                        Text("• $marker ${opt.body}", style = MaterialTheme.typography.bodySmall)
                    }
                    if (item.explanation.isNotBlank()) {
                        Text("Rationale: ${item.explanation}", style = MaterialTheme.typography.bodySmall)
                    }
                    if (item.approvalStatus == ApprovalStatus.DRAFT) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { scope.launch { app.localStore.approveItem(item.id) } },
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Text("Approve Item")
                            }
                            OutlinedButton(
                                onClick = { scope.launch { app.localStore.rejectItem(item.id) } },
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Text("Reject")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuizSetupScreen(
    app: QuickDropApplication,
    primarySubjectId: String?,
    preferences: UserPreferences,
    onBack: () -> Unit,
    onSessionStarted: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var requestedCount by rememberSaveable { mutableStateOf(preferences.defaultQuizCount) }
    var includeOlderTopics by rememberSaveable { mutableStateOf(preferences.includeOlderTopicsByDefault) }
    var shortPoolNotice by remember { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Quiz Setup", style = MaterialTheme.typography.headlineSmall)
        Text("Defaults loaded from Settings: $requestedCount questions, ${preferences.defaultDifficultyFilter}, untimed, explanations revealed only at the end.")

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(5, 10, 15, 20).forEach { count ->
                FilterChip(
                    selected = requestedCount == count,
                    onClick = { requestedCount = count },
                    label = { Text("$count Qs") }
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("Include due & older core topics")
            Switch(checked = includeOlderTopics, onCheckedChange = { includeOlderTopics = it })
        }

        if (shortPoolNotice != null) {
            Text(shortPoolNotice!!, color = MaterialTheme.colorScheme.error)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    scope.launch {
                        val (session, result) = app.localStore.startNewQuizSession(
                            scope = QuizScopeSnapshot(
                                primarySubjectId = primarySubjectId,
                                includeCrossSubjectReview = preferences.includeCrossSubjectByDefault,
                                includeOlderTopics = includeOlderTopics,
                                requestedCount = requestedCount,
                                difficultyFilter = preferences.defaultDifficultyFilter
                            )
                        )
                        if (session == null) {
                            shortPoolNotice = "No approved MCQ items available for this scope yet. Add or approve study items first."
                        } else {
                            onSessionStarted(session.id)
                        }
                    }
                },
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text("Start Quiz")
            }
            OutlinedButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Back")
            }
        }
    }
}

@Composable
private fun QuizRunnerScreen(
    app: QuickDropApplication,
    sessionId: String,
    onRequestQuit: () -> Unit,
    onQuizCompleted: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val session by app.localStore.observeSessionById(sessionId).collectAsState(initial = null)
    val responses by app.localStore.observeResponsesForSession(sessionId).collectAsState(initial = emptyList())
    var selectedOptionId by rememberSaveable { mutableStateOf<String?>(null) }

    val currentSession = session ?: return
    val currentIndex = currentSession.currentQuestionIndex.coerceIn(0, (currentSession.itemIdsInOrder.size - 1).coerceAtLeast(0))
    val currentItemId = currentSession.itemIdsInOrder.getOrNull(currentIndex) ?: return

    // Look up existing response if student navigated back
    val existingResponse = responses.find { it.itemId == currentItemId }
    LaunchedEffect(currentItemId, existingResponse) {
        selectedOptionId = existingResponse?.selectedOptionId
    }

    // Observe the item
    val draftItems by app.localStore.observeDraftItems().collectAsState(initial = emptyList())
    var currentItem by remember { mutableStateOf<com.quickdrop.core.model.LearningItem?>(null) }
    LaunchedEffect(currentItemId) {
        // Export bundle or direct lookup
        val exported = kotlinx.serialization.json.Json.decodeFromString<com.quickdrop.core.repository.LocalDataExportBundle>(
            app.localStore.exportLocalDataJson()
        )
        currentItem = exported.learningItems.find { it.id == currentItemId }
    }

    val item = currentItem ?: return

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LinearProgressIndicator(
            progress = { (currentIndex + 1).toFloat() / currentSession.itemIdsInOrder.size.toFloat() },
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Question ${currentIndex + 1} of ${currentSession.itemIdsInOrder.size}",
                style = MaterialTheme.typography.titleMedium
            )
            TextButton(onClick = onRequestQuit, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Quit / Pause")
            }
        }

        if (currentSession.scopeSnapshot.actualEligibleCount < currentSession.scopeSnapshot.requestedCount) {
            Text(
                text = "Note: Pool had ${currentSession.scopeSnapshot.actualEligibleCount} approved items (requested ${currentSession.scopeSnapshot.requestedCount}).",
                style = MaterialTheme.typography.labelSmall
            )
        }

        // Per specification: NEVER reveal correctness or explanation before final results!
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(item.promptOrBody, style = MaterialTheme.typography.titleMedium)
                item.options.forEach { option ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                    ) {
                        RadioButton(
                            selected = selectedOptionId == option.id,
                            onClick = { selectedOptionId = option.id }
                        )
                        Text(option.body, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (currentIndex > 0) {
                OutlinedButton(
                    onClick = {
                        scope.launch { app.localStore.navigateQuestion(sessionId, currentIndex - 1) }
                    },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Back")
                }
            }

            OutlinedButton(
                onClick = {
                    scope.launch {
                        app.localStore.recordAnswer(
                            sessionId = sessionId,
                            itemId = item.id,
                            selectedOptionId = null, // Skip
                            latencyMs = 1500L
                        )
                        if (currentIndex + 1 >= currentSession.itemIdsInOrder.size) {
                            app.localStore.completeQuizSession(sessionId)
                            onQuizCompleted(sessionId)
                        }
                    }
                },
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text("Skip")
            }

            Button(
                enabled = selectedOptionId != null,
                onClick = {
                    scope.launch {
                        app.localStore.recordAnswer(
                            sessionId = sessionId,
                            itemId = item.id,
                            selectedOptionId = selectedOptionId,
                            latencyMs = 2500L
                        )
                        if (currentIndex + 1 >= currentSession.itemIdsInOrder.size) {
                            app.localStore.completeQuizSession(sessionId)
                            onQuizCompleted(sessionId)
                        }
                    }
                },
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(if (currentIndex + 1 >= currentSession.itemIdsInOrder.size) "Submit Quiz" else "Next")
            }
        }
    }
}

@Composable
private fun QuizResultsScreen(
    app: QuickDropApplication,
    sessionId: String,
    onDone: () -> Unit,
    onRetryDueOrMissed: (String?) -> Unit
) {
    val session by app.localStore.observeSessionById(sessionId).collectAsState(initial = null)
    val responses by app.localStore.observeResponsesForSession(sessionId).collectAsState(initial = emptyList())

    val completedSession = session ?: return

    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Quiz Results", style = MaterialTheme.typography.headlineSmall)
            Text(
                text = "Score: ${completedSession.correctCount} / ${completedSession.answeredCount} answered (${completedSession.skippedCount} skipped)",
                style = MaterialTheme.typography.titleMedium
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onRetryDueOrMissed(completedSession.scopeSnapshot.primarySubjectId) },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Review Due / Missed")
                }
                OutlinedButton(onClick = onDone, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Back to Home")
                }
            }
        }

        items(responses, key = { it.id }) { resp ->
            val selectedText = resp.optionsSnapshot.find { it.id == resp.selectedOptionId }?.body ?: "Skipped"
            val correctText = resp.optionsSnapshot.find { it.isCorrect }?.body ?: ""

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Q${resp.sequence + 1}: ${resp.outcome}",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Text(resp.promptSnapshot, style = MaterialTheme.typography.titleSmall)
                    Text("Your answer: $selectedText")
                    Text("Correct key: $correctText")
                    Text("Explanation: ${resp.explanationSnapshot}", style = MaterialTheme.typography.bodySmall)
                    Text("Source: ${resp.sourceCitationSnapshot}", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun StudyScreen(
    app: QuickDropApplication,
    onStartQuizSetup: (String?) -> Unit,
    onResumeQuiz: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val activeSession by app.localStore.observeActiveSession().collectAsState(initial = null)
    val drops by app.localStore.observeApprovedDrops().collectAsState(initial = emptyList())
    var showSavedOnly by rememberSaveable { mutableStateOf(false) }

    val filteredDrops = if (showSavedOnly) {
        drops.filter { it.second.visibilityState == DropVisibilityState.SAVED }
    } else {
        drops
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Study & Drops Feed", style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onStartQuizSetup(null) },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Start Cumulative Quiz")
                }
                if (activeSession != null) {
                    OutlinedButton(
                        onClick = { onResumeQuiz(activeSession!!.id) },
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text("Resume Quiz")
                    }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !showSavedOnly,
                    onClick = { showSavedOnly = false },
                    label = { Text("All Approved Drops (${drops.size})") }
                )
                FilterChip(
                    selected = showSavedOnly,
                    onClick = { showSavedOnly = true },
                    label = { Text("Saved Drops Offline") }
                )
            }
        }

        items(filteredDrops, key = { it.first.id }) { (dropItem, dropState) ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(dropItem.promptOrBody, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = "State: ${dropState.visibilityState} • Anchor: ${dropItem.anchorKind ?: "NOTE"} ${dropItem.anchorIndex ?: 1}",
                        style = MaterialTheme.typography.labelSmall
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                scope.launch { app.localStore.updateDropState(dropItem.id, DropVisibilityState.SAVED) }
                            },
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text("Save")
                        }
                        OutlinedButton(
                            onClick = {
                                scope.launch { app.localStore.updateDropState(dropItem.id, DropVisibilityState.UNDERSTOOD) }
                            },
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text("Understood")
                        }
                        TextButton(
                            onClick = {
                                scope.launch { app.localStore.updateDropState(dropItem.id, DropVisibilityState.REVIEW) }
                            },
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text("Review Again")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgressScreen(app: QuickDropApplication) {
    val completedSessions by app.localStore.observeCompletedSessions().collectAsState(initial = emptyList())
    val reviewStates by app.localStore.observeTopicReviewStates().collectAsState(initial = emptyMap())

    val totalCorrect = completedSessions.sumOf { it.correctCount }
    val totalAnswered = completedSessions.sumOf { it.answeredCount }
    val overallAccuracy = if (totalAnswered > 0) (totalCorrect * 100) / totalAnswered else 0

    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Recall & Routine Outcomes", style = MaterialTheme.typography.titleLarge)
            Text(
                text = "Note: Topic mastery is a local practice estimate based on spaced repetition intervals, not an academic grade.",
                style = MaterialTheme.typography.bodySmall
            )
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Completed Quiz Sessions: ${completedSessions.size}", style = MaterialTheme.typography.titleSmall)
                    Text("Overall Accuracy: $overallAccuracy% ($totalCorrect / $totalAnswered answered)")
                }
            }
        }

        item {
            Text("Topic Mastery States", style = MaterialTheme.typography.titleMedium)
        }

        items(reviewStates.values.toList(), key = { it.topicId }) { state ->
            val status = SpacedRepetitionScheduler.resolveMasteryStatus(state, app.clock)
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Topic ID: ${state.topicId.take(8)} • Status: $status", style = MaterialTheme.typography.titleSmall)
                    Text("Stability: ${"%.1f".format(state.stabilityDays)} days • Correct: ${state.correctCount}/${state.attemptCount} • Lapses: ${state.lapses}")
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    app: QuickDropApplication,
    preferences: UserPreferences,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var keyInput by rememberSaveable { mutableStateOf("") }
    var maskedKeyPreview by remember { mutableStateOf(app.apiKeyVault.getMaskedKeyPreview()) }
    var keyStatusMessage by remember { mutableStateOf<String?>(null) }
    var exportedJsonPreview by remember { mutableStateOf<String?>(null) }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Settings & Privacy", style = MaterialTheme.typography.headlineSmall)
                OutlinedButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Close")
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Appearance Theme", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemePreference.entries.forEach { theme ->
                            FilterChip(
                                selected = preferences.themePreference == theme,
                                onClick = {
                                    scope.launch {
                                        app.localStore.updatePreferences { it.copy(themePreference = theme) }
                                    }
                                },
                                label = { Text(theme.name) }
                            )
                        }
                    }
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Gemini API Key (BYOK • Android Keystore AES-GCM)", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = if (maskedKeyPreview != null) {
                            "Stored Key (Masked): $maskedKeyPreview"
                        } else {
                            "No API key configured (Manual study mode active)"
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = keyInput,
                        onValueChange = { keyInput = it },
                        label = { Text("Add or Replace Gemini API Key") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                if (keyInput.isNotBlank()) {
                                    app.apiKeyVault.saveApiKey(keyInput)
                                    keyInput = ""
                                    maskedKeyPreview = app.apiKeyVault.getMaskedKeyPreview()
                                    keyStatusMessage = "Key encrypted in Android Keystore."
                                }
                            },
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text("Save Key")
                        }
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    val stored = app.apiKeyVault.getDecryptedApiKey().orEmpty()
                                    val res = app.activeGeminiClient().testConnection(stored, preferences.geminiModelId)
                                    keyStatusMessage = when (res) {
                                        is com.quickdrop.core.domain.GeminiResponse.Success -> "Connection verified (${res.modelId})."
                                        is com.quickdrop.core.domain.GeminiResponse.Failure -> res.sanitizedMessage
                                    }
                                }
                            },
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text("Test")
                        }
                        TextButton(
                            onClick = {
                                app.apiKeyVault.removeApiKey()
                                maskedKeyPreview = null
                                keyStatusMessage = "Key and Keystore alias deleted."
                            },
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text("Remove Key")
                        }
                    }
                    if (keyStatusMessage != null) {
                        Text(keyStatusMessage!!, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Reminders & Quiet Hours", style = MaterialTheme.typography.titleSmall)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Class Reminders (Inexact • Best Effort)")
                        Switch(
                            checked = preferences.classRemindersEnabled,
                            onCheckedChange = { enabled ->
                                scope.launch {
                                    app.localStore.updatePreferences { it.copy(classRemindersEnabled = enabled) }
                                    app.rescheduleRemindersAsync()
                                }
                            }
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Overnight Quiet Hours (22:00 – 07:00)")
                        Switch(
                            checked = preferences.quietHoursEnabled,
                            onCheckedChange = { enabled ->
                                scope.launch {
                                    app.localStore.updatePreferences { it.copy(quietHoursEnabled = enabled) }
                                    app.rescheduleRemindersAsync()
                                }
                            }
                        )
                    }
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Local Data Export & Permanent Delete", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = "Export includes your subjects, timetable, lectures, approved items, and quiz history in readable JSON. Your Gemini API key is never included in exports.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    exportedJsonPreview = app.localStore.exportLocalDataJson().take(600)
                                }
                            },
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text("Export JSON")
                        }
                        Button(
                            onClick = {
                                scope.launch {
                                    app.localStore.clearAllLocalData()
                                    maskedKeyPreview = null
                                    exportedJsonPreview = "All local database records, files, and Keystore aliases cleared."
                                }
                            },
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text("Clear All Local Data")
                        }
                    }
                    if (exportedJsonPreview != null) {
                        Text(exportedJsonPreview!!, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
