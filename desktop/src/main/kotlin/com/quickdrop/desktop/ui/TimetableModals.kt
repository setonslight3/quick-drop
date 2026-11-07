package com.quickdrop.desktop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quickdrop.core.domain.RecognizedClassSlot
import com.quickdrop.core.domain.TimetableParseResult
import com.quickdrop.core.domain.TimetableParser
import com.quickdrop.core.model.ClassSlot
import com.quickdrop.core.model.Subject
import com.quickdrop.core.repository.QuickDropLocalStore
import com.quickdrop.core.time.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Upload Timetable as a File Modal.
 * Supports HTML (.html/.htm), iCalendar (.ics), CSV, TSV, PDF, and text.
 * Automatically recognizes all courses, days, and times, and imports them all at once!
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UploadTimetableFileModal(
    store: QuickDropLocalStore,
    clock: SystemClock,
    subjects: List<Subject>,
    keyVault: com.quickdrop.core.security.AesGcmKeyVault? = null,
    onDismiss: () -> Unit,
    onImportComplete: (Int) -> Unit
) {
    val scope = rememberCoroutineScope()

    var selectedFile by remember { mutableStateOf<File?>(null) }
    var isAnalyzing by remember { mutableStateOf(false) }
    var parseResult by remember { mutableStateOf<TimetableParseResult?>(null) }
    var isImporting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var useAiRequested by remember { mutableStateOf(false) }

    var showDirectPaste by remember { mutableStateOf(false) }
    var pastedScheduleText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(QuickDropColors.Emerald.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileUpload,
                            contentDescription = null,
                            tint = QuickDropColors.EmeraldLight,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Upload Timetable File",
                            color = QuickDropColors.TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                        Text(
                            text = "Auto-recognizes your schedule and fills in your timetable all at once",
                            color = QuickDropColors.TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }

                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = QuickDropColors.TextMuted)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Dropzone Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(QuickDropColors.CosmicSurfaceElevated)
                        .border(1.dp, QuickDropColors.CosmicBorder, RoundedCornerShape(16.dp))
                        .padding(18.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = {
                                scope.launch {
                                    val file = withContext(Dispatchers.IO) {
                                        FilePickerHelper.pickTimetableFile()
                                    }
                                    if (file != null && file.exists()) {
                                        selectedFile = file
                                        isAnalyzing = true
                                        errorMessage = null
                                        delay(250)

                                        val bytes = withContext(Dispatchers.IO) { file.readBytes() }
                                        val apiKey = keyVault?.getDecryptedApiKey()
                                        val result = TimetableParser.parseSmart(bytes, file.name, apiKey = apiKey)
                                        parseResult = result
                                        isAnalyzing = false

                                        if (result.slots.isEmpty()) {
                                            errorMessage = "No class slots recognized in '${file.name}'. You can paste timetable text or try another file format (HTML, ICS, CSV, PDF)."
                                        }
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = QuickDropColors.Emerald),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.height(46.dp)
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("📂 Select Timetable File (HTML, ICS, CSV, PDF, Text)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("HTML Web Portal", color = QuickDropColors.TextMuted, fontSize = 11.sp)
                            Text("•", color = QuickDropColors.TextMuted, fontSize = 11.sp)
                            Text("iCalendar (.ics)", color = QuickDropColors.TextMuted, fontSize = 11.sp)
                            Text("•", color = QuickDropColors.TextMuted, fontSize = 11.sp)
                            Text("CSV / TSV", color = QuickDropColors.TextMuted, fontSize = 11.sp)
                            Text("•", color = QuickDropColors.TextMuted, fontSize = 11.sp)
                            Text("PDF Schedules", color = QuickDropColors.TextMuted, fontSize = 11.sp)
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = { showDirectPaste = !showDirectPaste }) {
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp), tint = QuickDropColors.CyanLight)
                                Spacer(Modifier.width(4.dp))
                                Text(if (showDirectPaste) "Hide Text Input" else "Or Paste Schedule Text / HTML Directly", color = QuickDropColors.CyanLight, fontSize = 11.sp)
                            }

                            if (selectedFile != null && parseResult != null) {
                                TextButton(
                                    onClick = {
                                        scope.launch {
                                            isAnalyzing = true
                                            val bytes = withContext(Dispatchers.IO) { selectedFile!!.readBytes() }
                                            val apiKey = keyVault?.getDecryptedApiKey()
                                            val result = TimetableParser.parseSmart(bytes, selectedFile!!.name, apiKey = apiKey)
                                            parseResult = result
                                            isAnalyzing = false
                                        }
                                    }
                                ) {
                                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color(0xFFF59E0B))
                                    Spacer(Modifier.width(4.dp))
                                    Text("🤖 Re-analyze with Gemini AI", color = Color(0xFFF59E0B), fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }

                // Direct Paste Accordion
                if (showDirectPaste) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = pastedScheduleText,
                            onValueChange = { pastedScheduleText = it },
                            placeholder = { Text("Paste raw HTML, CSV, or schedule text like:\nMon, Wed 09:00 - 10:30 CS 401 Hall 201\nTue, Thu 11:00 - 12:30 Algorithms Room 304") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 4,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = QuickDropColors.Cyan,
                                unfocusedBorderColor = QuickDropColors.CosmicBorder
                            )
                        )
                        Button(
                            onClick = {
                                if (pastedScheduleText.isNotBlank()) {
                                    scope.launch {
                                        isAnalyzing = true
                                        val bytes = pastedScheduleText.toByteArray(Charsets.UTF_8)
                                        val apiKey = keyVault?.getDecryptedApiKey()
                                        val result = TimetableParser.parseSmart(bytes, "pasted_schedule.html", apiKey = apiKey)
                                        parseResult = result
                                        isAnalyzing = false
                                        if (result.slots.isEmpty()) {
                                            errorMessage = "Could not detect classes. Ensure lines contain course name, days (e.g. Mon, Wed), and time (e.g. 09:00-10:30)."
                                        } else {
                                            errorMessage = null
                                        }
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = QuickDropColors.Cyan),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Process Pasted Text", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }

                // Loading spinner
                if (isAnalyzing) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = QuickDropColors.Emerald, strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("Analyzing file and parsing weekly slots...", color = QuickDropColors.TextSecondary, fontSize = 12.sp)
                    }
                }

                // Error / warning banner
                if (errorMessage != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0x33EF4444))
                            .border(1.dp, QuickDropColors.Rose.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                            .padding(12.dp)
                    ) {
                        Text(errorMessage.orEmpty(), color = QuickDropColors.RoseLight, fontSize = 12.sp)
                    }
                }

                // Recognized Classes Review Section
                if (parseResult != null && parseResult!!.slots.isNotEmpty()) {
                    val result = parseResult!!
                    val slots = result.slots

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF0C1F19))
                            .border(1.dp, QuickDropColors.Emerald.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                            .padding(16.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = QuickDropColors.Emerald, modifier = Modifier.size(20.dp))
                                    Text(
                                        text = "Recognized ${slots.size} Classes All at Once!",
                                        color = QuickDropColors.TextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }

                                GlassBadge(
                                    text = if (result.isAiDetected) "🤖 Gemini AI Verified" else result.detectedFormat,
                                    backgroundColor = if (result.isAiDetected) Color(0x3306B6D4) else Color(0x3310B981),
                                    textColor = if (result.isAiDetected) QuickDropColors.CyanLight else QuickDropColors.EmeraldLight
                                )
                            }

                            Text(
                                text = "Confirm the recognized weekly schedule below. Quick Drop will create the subjects and populate your agenda in one click.",
                                color = QuickDropColors.TextSecondary,
                                fontSize = 11.sp
                            )

                            // List of recognized classes
                            slots.forEachIndexed { index, slot ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(QuickDropColors.CosmicSurfaceElevated)
                                        .padding(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(8.dp)
                                                        .clip(CircleShape)
                                                        .background(QuickDropColors.Emerald)
                                                )
                                                val titleWithCode = if (slot.courseCode.isNotBlank()) "${slot.courseCode} — ${slot.courseName}" else slot.courseName
                                                Text(
                                                    text = titleWithCode,
                                                    color = QuickDropColors.TextPrimary,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp
                                                )
                                            }
                                            Text(
                                                text = "📍 ${slot.location}${if (slot.isDoubleSlot) " • Double Slot (2 hrs)" else ""}${if (slot.isElective) " • Elective" else " • Core"} (${slot.credits}U)",
                                                color = QuickDropColors.TextMuted,
                                                fontSize = 11.sp
                                            )
                                        }

                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            GlassBadge(
                                                text = slot.dayNamesDisplay,
                                                backgroundColor = Color(0x268B5CF6),
                                                textColor = QuickDropColors.VioletLight
                                            )
                                            GlassBadge(
                                                text = slot.timeDisplay,
                                                backgroundColor = Color(0x2606B6D4),
                                                textColor = QuickDropColors.CyanLight
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            val hasSlots = parseResult != null && parseResult!!.slots.isNotEmpty()
            Button(
                enabled = hasSlots && !isImporting,
                onClick = {
                    val slotsToImport = parseResult?.slots.orEmpty()
                    if (slotsToImport.isNotEmpty()) {
                        scope.launch {
                            isImporting = true
                            val colorPalette = listOf("cyan", "rose", "emerald", "amber", "blue", "violet", "fuchsia", "indigo")
                            var colorIdx = 0

                            for (rSlot in slotsToImport) {
                                // Match or create subject
                                val displayName = if (rSlot.courseCode.isNotBlank()) "${rSlot.courseCode} - ${rSlot.courseName}" else rSlot.courseName
                                val existingSubj = subjects.find {
                                    (rSlot.courseCode.isNotBlank() && it.name.contains(rSlot.courseCode, ignoreCase = true)) ||
                                        it.name.contains(rSlot.courseName, ignoreCase = true) ||
                                        rSlot.courseName.contains(it.name, ignoreCase = true)
                                }

                                val subjectId = if (existingSubj != null) {
                                    existingSubj.id
                                } else {
                                    val assignedColor = colorPalette[colorIdx % colorPalette.size]
                                    colorIdx++
                                    val newSubj = store.createOrUpdateSubject(
                                        name = displayName,
                                        colorKey = assignedColor,
                                        iconKey = "school",
                                        term = "First Semester"
                                    )
                                    newSubj.id
                                }

                                val slot = ClassSlot(
                                    id = UUID.randomUUID().toString(),
                                    subjectId = subjectId,
                                    weekdayMask = rSlot.weekdayMask,
                                    startMinuteOfDay = rSlot.startMinuteOfDay,
                                    endMinuteOfDay = rSlot.endMinuteOfDay,
                                    zoneId = clock.zoneId().id,
                                    location = rSlot.location,
                                    enabled = true,
                                    reminderEnabled = true,
                                    reminderLeadMinutes = 15
                                )
                                store.upsertClassSlot(slot)
                            }

                            isImporting = false
                            onImportComplete(slotsToImport.size)
                            onDismiss()
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = QuickDropColors.Emerald),
                shape = RoundedCornerShape(10.dp)
            ) {
                if (isImporting) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Importing...", fontWeight = FontWeight.Bold)
                } else {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    val count = parseResult?.slots?.size ?: 0
                    Text(if (count > 0) "✨ Confirm & Fill Timetable ($count Classes)" else "Select a File First", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = QuickDropColors.TextMuted)
            }
        }
    )
}

/**
 * Interactive Modal for creating recurring class timetable slots manually.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddClassSlotModal(
    store: QuickDropLocalStore,
    clock: SystemClock,
    subjects: List<Subject>,
    initialSubjectId: String? = null,
    onDismiss: () -> Unit,
    onSlotAdded: (ClassSlot) -> Unit
) {
    val scope = rememberCoroutineScope()

    var selectedSubjectId by remember {
        mutableStateOf(initialSubjectId ?: subjects.firstOrNull()?.id ?: "")
    }
    var isCreatingNewCourse by remember { mutableStateOf(subjects.isEmpty()) }
    var newCourseName by remember { mutableStateOf("") }
    var newCourseInstructor by remember { mutableStateOf("") }

    // Multi-day selection: ISO days 1..7 (Mon=1, Sun=7)
    var selectedDays by remember { mutableStateOf(setOf(1)) } // Default Monday

    // Time state
    val timePresets = listOf(
        "09:00 - 10:30" to Pair(9 * 60, 90),
        "10:45 - 12:15" to Pair(10 * 60 + 45, 90),
        "13:30 - 15:00" to Pair(13 * 60 + 30, 90),
        "15:15 - 16:45" to Pair(15 * 60 + 15, 90),
        "17:00 - 18:30" to Pair(17 * 60, 90)
    )
    var selectedPresetIndex by remember { mutableStateOf<Int?>(0) }
    var startHourText by remember { mutableStateOf("09") }
    var startMinuteText by remember { mutableStateOf("00") }
    var durationMinutesText by remember { mutableStateOf("90") }

    var locationText by remember { mutableStateOf("Lecture Hall 101") }
    var reminderEnabled by remember { mutableStateOf(true) }
    var reminderLeadMinutes by remember { mutableStateOf(15) }

    var errorMessage by remember { mutableStateOf<String?>(null) }
    var warningMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(QuickDropColors.Emerald.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarMonth,
                            contentDescription = null,
                            tint = QuickDropColors.EmeraldLight,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Add Class to Schedule",
                            color = QuickDropColors.TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                        Text(
                            text = "Recurring weekly class with automatic conflict detection",
                            color = QuickDropColors.TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }

                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = QuickDropColors.TextMuted)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1. Course Selection
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Course / Subject", color = QuickDropColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        TextButton(onClick = { isCreatingNewCourse = !isCreatingNewCourse }) {
                            Text(
                                if (isCreatingNewCourse) "Choose Existing" else "+ New Course",
                                color = QuickDropColors.EmeraldLight,
                                fontSize = 11.sp
                            )
                        }
                    }

                    if (isCreatingNewCourse || subjects.isEmpty()) {
                        OutlinedTextField(
                            value = newCourseName,
                            onValueChange = { newCourseName = it },
                            placeholder = { Text("e.g. CS 401: Distributed Systems") },
                            leadingIcon = { Icon(Icons.Default.School, contentDescription = null, tint = QuickDropColors.Emerald) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = QuickDropColors.Emerald,
                                unfocusedBorderColor = QuickDropColors.CosmicBorder
                            )
                        )
                        OutlinedTextField(
                            value = newCourseInstructor,
                            onValueChange = { newCourseInstructor = it },
                            placeholder = { Text("Instructor (Optional)") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = QuickDropColors.Emerald,
                                unfocusedBorderColor = QuickDropColors.CosmicBorder
                            )
                        )
                    } else {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            subjects.forEach { subj ->
                                val isSelected = subj.id == selectedSubjectId
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { selectedSubjectId = subj.id },
                                    label = { Text(subj.name) },
                                    leadingIcon = if (isSelected) {
                                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                    } else null,
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = QuickDropColors.Emerald.copy(alpha = 0.25f),
                                        selectedLabelColor = QuickDropColors.EmeraldLight
                                    ),
                                    shape = RoundedCornerShape(10.dp)
                                )
                            }
                        }
                    }
                }

                // 2. Day of Week Multi-Selector
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Days of the Week", color = QuickDropColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    val days = listOf(
                        1 to "Mon", 2 to "Tue", 3 to "Wed", 4 to "Thu",
                        5 to "Fri", 6 to "Sat", 7 to "Sun"
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(QuickDropColors.CosmicSurfaceElevated)
                            .border(1.dp, QuickDropColors.CosmicBorder, RoundedCornerShape(12.dp))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        days.forEach { (isoDay, name) ->
                            val isSelected = selectedDays.contains(isoDay)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isSelected) QuickDropColors.Emerald else Color.Transparent
                                    )
                                    .clickable {
                                        selectedDays = if (isSelected) {
                                            if (selectedDays.size > 1) selectedDays - isoDay else selectedDays
                                        } else {
                                            selectedDays + isoDay
                                        }
                                    }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = name,
                                    color = if (isSelected) Color.White else QuickDropColors.TextSecondary,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }

                // 3. Time Presets
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Class Time Slot", color = QuickDropColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        timePresets.forEachIndexed { index, (label, timePair) ->
                            val isSelected = selectedPresetIndex == index
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    selectedPresetIndex = index
                                    val startMins = timePair.first
                                    val dur = timePair.second
                                    startHourText = "%02d".format(startMins / 60)
                                    startMinuteText = "%02d".format(startMins % 60)
                                    durationMinutesText = dur.toString()
                                },
                                label = { Text(label, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = QuickDropColors.Cyan.copy(alpha = 0.25f),
                                    selectedLabelColor = QuickDropColors.CyanLight
                                ),
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                    }

                    // Custom input fields
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = startHourText,
                            onValueChange = {
                                if (it.length <= 2 && it.all { c -> c.isDigit() }) {
                                    startHourText = it
                                    selectedPresetIndex = null
                                }
                            },
                            label = { Text("Hour (00-23)", fontSize = 10.sp) },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        Text(":", color = QuickDropColors.TextSecondary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        OutlinedTextField(
                            value = startMinuteText,
                            onValueChange = {
                                if (it.length <= 2 && it.all { c -> c.isDigit() }) {
                                    startMinuteText = it
                                    selectedPresetIndex = null
                                }
                            },
                            label = { Text("Min (00-59)", fontSize = 10.sp) },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = durationMinutesText,
                            onValueChange = {
                                if (it.length <= 3 && it.all { c -> c.isDigit() }) {
                                    durationMinutesText = it
                                    selectedPresetIndex = null
                                }
                            },
                            label = { Text("Duration (mins)", fontSize = 10.sp) },
                            modifier = Modifier.weight(1.2f),
                            singleLine = true
                        )
                    }
                }

                // 4. Classroom / Location
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Location / Room", color = QuickDropColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    OutlinedTextField(
                        value = locationText,
                        onValueChange = { locationText = it },
                        placeholder = { Text("e.g. Room 302, Science Hall, or Zoom URL") },
                        leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null, tint = QuickDropColors.Cyan) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // 5. Pre-Class Spaced-Recall Reminder
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(QuickDropColors.CosmicSurfaceElevated)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = null,
                            tint = if (reminderEnabled) QuickDropColors.Amber else QuickDropColors.TextMuted,
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text("Pre-Class Recall Quiz", color = QuickDropColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("Preps you 15 min before class with high-yield items", color = QuickDropColors.TextMuted, fontSize = 10.sp)
                        }
                    }

                    Switch(
                        checked = reminderEnabled,
                        onCheckedChange = { reminderEnabled = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = QuickDropColors.Amber)
                    )
                }

                // Feedback warnings / errors
                if (errorMessage != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0x33EF4444))
                            .padding(10.dp)
                    ) {
                        Text(errorMessage.orEmpty(), color = QuickDropColors.RoseLight, fontSize = 12.sp)
                    }
                }
                if (warningMessage != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0x33F59E0B))
                            .padding(10.dp)
                    ) {
                        Text("⚠️ $warningMessage", color = QuickDropColors.AmberLight, fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    errorMessage = null
                    warningMessage = null

                    val hour = startHourText.toIntOrNull()
                    val minute = startMinuteText.toIntOrNull()
                    val duration = durationMinutesText.toIntOrNull()

                    if (hour == null || hour !in 0..23) {
                        errorMessage = "Please enter a valid start hour (0-23)."
                        return@Button
                    }
                    if (minute == null || minute !in 0..59) {
                        errorMessage = "Please enter a valid start minute (0-59)."
                        return@Button
                    }
                    if (duration == null || duration <= 0 || duration > 480) {
                        errorMessage = "Duration must be between 1 and 480 minutes."
                        return@Button
                    }
                    if (selectedDays.isEmpty()) {
                        errorMessage = "Please select at least one day of the week."
                        return@Button
                    }

                    val startMinuteOfDay = hour * 60 + minute
                    val endMinuteOfDay = (startMinuteOfDay + duration).coerceAtMost(1440)
                    if (endMinuteOfDay <= startMinuteOfDay) {
                        errorMessage = "End time must be after start time."
                        return@Button
                    }

                    val weekdayMask = selectedDays.fold(0) { acc, day -> acc or (1 shl (day - 1)) }

                    scope.launch {
                        val finalSubjectId = if (isCreatingNewCourse || subjects.isEmpty()) {
                            if (newCourseName.isBlank()) {
                                errorMessage = "Please provide a name for the new course."
                                return@launch
                            }
                            val created = store.createOrUpdateSubject(
                                name = newCourseName.trim(),
                                colorKey = "emerald",
                                iconKey = "school",
                                instructor = newCourseInstructor.trim().ifBlank { null }
                            )
                            created.id
                        } else {
                            selectedSubjectId
                        }

                        val slot = ClassSlot(
                            id = UUID.randomUUID().toString(),
                            subjectId = finalSubjectId,
                            weekdayMask = weekdayMask,
                            startMinuteOfDay = startMinuteOfDay,
                            endMinuteOfDay = endMinuteOfDay,
                            zoneId = clock.zoneId().id,
                            location = locationText.trim(),
                            enabled = true,
                            reminderEnabled = reminderEnabled,
                            reminderLeadMinutes = reminderLeadMinutes
                        )

                        val (savedSlot, warnings) = store.upsertClassSlot(slot)
                        if (warnings.isNotEmpty()) {
                            warningMessage = "Saved with conflict: Overlaps with an existing class at that time."
                        }
                        onSlotAdded(savedSlot)
                        onDismiss()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = QuickDropColors.Emerald),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Save Class to Timetable", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = QuickDropColors.TextMuted)
            }
        }
    )
}

/**
 * Quick modal to auto-populate sample weekly university schedule.
 */
@Composable
fun ImportTimetableModal(
    store: QuickDropLocalStore,
    clock: SystemClock,
    subjects: List<Subject>,
    onDismiss: () -> Unit,
    onImportComplete: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var isImporting by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Sample Timetable Generator", color = QuickDropColors.TextPrimary, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Instantly seed a realistic weekly student timetable with Monday-Friday recurring lecture slots, room locations, and prep reminders.",
                    color = QuickDropColors.TextSecondary,
                    fontSize = 13.sp
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(QuickDropColors.CosmicSurfaceElevated)
                        .padding(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Generated Schedule Preview:", color = QuickDropColors.EmeraldLight, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text("• Mon & Wed 09:00 - 10:30 (Hall 201) — Operating Systems", color = QuickDropColors.TextPrimary, fontSize = 11.sp)
                        Text("• Tue & Thu 11:00 - 12:30 (Room 304) — Algorithms & Complexity", color = QuickDropColors.TextPrimary, fontSize = 11.sp)
                        Text("• Fri 14:00 - 15:30 (Lab 5B) — Computer Networks", color = QuickDropColors.TextPrimary, fontSize = 11.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !isImporting,
                onClick = {
                    scope.launch {
                        isImporting = true
                        val osSubj = subjects.find { it.name.contains("Operating", ignoreCase = true) }
                            ?: store.createOrUpdateSubject(name = "Operating Systems", colorKey = "emerald", iconKey = "memory")
                        val algoSubj = subjects.find { it.name.contains("Algo", ignoreCase = true) }
                            ?: store.createOrUpdateSubject(name = "Algorithms & Data Structures", colorKey = "cyan", iconKey = "code")
                        val netSubj = subjects.find { it.name.contains("Network", ignoreCase = true) }
                            ?: store.createOrUpdateSubject(name = "Computer Networks", colorKey = "violet", iconKey = "cloud")

                        store.upsertClassSlot(
                            ClassSlot(
                                id = UUID.randomUUID().toString(),
                                subjectId = osSubj.id,
                                weekdayMask = (1 shl 0) or (1 shl 2),
                                startMinuteOfDay = 9 * 60,
                                endMinuteOfDay = 10 * 60 + 30,
                                zoneId = clock.zoneId().id,
                                location = "Lecture Hall 201",
                                reminderEnabled = true
                            )
                        )

                        store.upsertClassSlot(
                            ClassSlot(
                                id = UUID.randomUUID().toString(),
                                subjectId = algoSubj.id,
                                weekdayMask = (1 shl 1) or (1 shl 3),
                                startMinuteOfDay = 11 * 60,
                                endMinuteOfDay = 12 * 60 + 30,
                                zoneId = clock.zoneId().id,
                                location = "Room 304 (Turing Hall)",
                                reminderEnabled = true
                            )
                        )

                        store.upsertClassSlot(
                            ClassSlot(
                                id = UUID.randomUUID().toString(),
                                subjectId = netSubj.id,
                                weekdayMask = (1 shl 4),
                                startMinuteOfDay = 14 * 60,
                                endMinuteOfDay = 15 * 60 + 30,
                                zoneId = clock.zoneId().id,
                                location = "Lab 5B (Networks Lab)",
                                reminderEnabled = true
                            )
                        )

                        isImporting = false
                        onImportComplete()
                        onDismiss()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = QuickDropColors.Emerald)
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (isImporting) "Generating..." else "Fill Sample Schedule", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
