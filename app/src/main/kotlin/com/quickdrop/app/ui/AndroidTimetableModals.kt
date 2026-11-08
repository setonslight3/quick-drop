package com.quickdrop.app.ui

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Schedule
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quickdrop.app.ui.theme.QuickDropColors
import com.quickdrop.core.domain.RecognizedClassSlot
import com.quickdrop.core.domain.TimetableParser
import com.quickdrop.core.model.ClassSlot
import com.quickdrop.core.model.Subject
import com.quickdrop.core.repository.QuickDropLocalStore
import com.quickdrop.core.time.AppClock
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun AndroidUploadTimetableFileModal(
    store: QuickDropLocalStore,
    clock: AppClock,
    subjects: List<Subject>,
    onDismiss: () -> Unit,
    onImportComplete: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var activeTab by remember { mutableStateOf("file") } // "file", "paste", "template"
    var selectedFileName by remember { mutableStateOf<String?>(null) }
    var rawHtmlInput by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }
    var recognizedSlots by remember { mutableStateOf<List<RecognizedClassSlot>>(emptyList()) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            isProcessing = true
            scope.launch {
                try {
                    val cr = context.contentResolver
                    var displayName = "timetable.html"
                    cr.query(uri, null, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (nameIdx >= 0) displayName = cursor.getString(nameIdx)
                        }
                    }
                    selectedFileName = displayName
                    val bytes = cr.openInputStream(uri)?.use { it.readBytes() }
                    if (bytes != null && bytes.isNotEmpty()) {
                        val parsed = TimetableParser.parse(bytes, displayName)
                        recognizedSlots = parsed.slots
                        val totalCredits = parsed.slots.sumOf { it.credits }
                        statusMessage = "Successfully recognized ${parsed.slots.size} courses ($totalCredits credit units) from $displayName!"
                    } else {
                        statusMessage = "Unable to read file contents from $displayName"
                    }
                } catch (e: Exception) {
                    statusMessage = "Error parsing file: ${e.message}"
                } finally {
                    isProcessing = false
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(QuickDropColors.EmeraldGradient),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.FileUpload, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                    Column {
                        Text("Upload Timetable File", color = QuickDropColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("HTML, ICS, PDF, CSV Supported", color = QuickDropColors.TextSecondary, fontSize = 11.sp)
                    }
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = QuickDropColors.TextMuted)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Tab selector
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(QuickDropColors.CosmicSurfaceElevated)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf("file" to "📁 Pick File", "paste" to "📋 Paste HTML", "template" to "🪄 200L Auto").forEach { (tabId, label) ->
                        val isSelected = activeTab == tabId
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .then(
                                    if (isSelected) Modifier.background(QuickDropColors.EmeraldGradient)
                                    else Modifier.background(Color.Transparent)
                                )
                                .clickable { activeTab = tabId }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) Color.White else QuickDropColors.TextSecondary,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                when (activeTab) {
                    "file" -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(QuickDropColors.CosmicSurfaceElevated)
                                .border(1.dp, QuickDropColors.CosmicBorder, RoundedCornerShape(12.dp))
                                .clickable {
                                    filePickerLauncher.launch("*/*")
                                }
                                .padding(20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.FileUpload, contentDescription = null, tint = QuickDropColors.Emerald, modifier = Modifier.size(36.dp))
                                Text("Tap to Choose Timetable File", color = QuickDropColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("HTML portal page, ICS calendar, or PDF", color = QuickDropColors.TextMuted, fontSize = 11.sp)
                                if (selectedFileName != null) {
                                    Text("Selected: $selectedFileName", color = QuickDropColors.CyanLight, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    "paste" -> {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = rawHtmlInput,
                                onValueChange = { rawHtmlInput = it },
                                label = { Text("Paste HTML or Timetable Text here") },
                                placeholder = { Text("<table>... <tr><td>SEN 201</td>...</tr></table>") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(130.dp)
                            )
                            Button(
                                onClick = {
                                    if (rawHtmlInput.isNotBlank()) {
                                        isProcessing = true
                                        val parsed = TimetableParser.parse(rawHtmlInput.toByteArray(), "pasted.html")
                                        recognizedSlots = parsed.slots
                                        statusMessage = "Recognized ${parsed.slots.size} course slots from pasted HTML!"
                                        isProcessing = false
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = QuickDropColors.Emerald),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Parse HTML with AI Engine", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }

                    "template" -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0x1F06B6D4))
                                .border(1.dp, Color(0x3306B6D4), RoundedCornerShape(12.dp))
                                .padding(16.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("🛡️ Cybersecurity 200L Syllabus", color = QuickDropColors.CyanLight, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(
                                    "SEN 201, CYB 203, WU-CYB 205, IFT 211, COS 201, CYB 201, ENT 211, WU 211.\nPre-configured with full 17 credit units, room venues, and double slots.",
                                    color = QuickDropColors.TextSecondary,
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp
                                )
                                Button(
                                    onClick = {
                                        recognizedSlots = getCybersecurity200LSlots()
                                        statusMessage = "Loaded all 10 slots from Cybersecurity 200L syllabus!"
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = QuickDropColors.Cyan),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Preview 200L Schedule", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF090D16))
                                }
                            }
                        }
                    }
                }

                if (isProcessing) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = QuickDropColors.Emerald)
                        Spacer(Modifier.width(8.dp))
                        Text("Extracting courses and schedules...", color = QuickDropColors.TextSecondary, fontSize = 12.sp)
                    }
                }

                if (statusMessage != null) {
                    Text(statusMessage!!, color = QuickDropColors.EmeraldLight, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                }

                // Recognized slots live preview
                if (recognizedSlots.isNotEmpty()) {
                    Text("Recognized Schedule (${recognizedSlots.size} slots):", color = QuickDropColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 180.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(recognizedSlots) { slot ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(QuickDropColors.CosmicSurfaceElevated)
                                    .border(1.dp, QuickDropColors.CosmicBorder, RoundedCornerShape(8.dp))
                                    .padding(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text("${slot.courseCode} - ${slot.courseName}", color = QuickDropColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        Text("${slot.dayNamesDisplay} • ${slot.timeDisplay} • 📍 ${slot.location}", color = QuickDropColors.TextSecondary, fontSize = 10.sp)
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(if (slot.isElective) Color(0x338B5CF6) else Color(0x3310B981))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            if (slot.isElective) "ELECTIVE • ${slot.credits}U" else "CORE • ${slot.credits}U",
                                            color = if (slot.isElective) QuickDropColors.VioletLight else QuickDropColors.EmeraldLight,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = recognizedSlots.isNotEmpty(),
                onClick = {
                    scope.launch {
                        importRecognizedSlotsIntoStore(recognizedSlots, store, subjects, clock)
                        onImportComplete()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = QuickDropColors.Emerald)
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Import ${recognizedSlots.size} Slots into Timetable", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = QuickDropColors.TextSecondary)
            }
        }
    )
}

@Composable
fun AndroidAddClassSlotModal(
    store: QuickDropLocalStore,
    clock: AppClock,
    subjects: List<Subject>,
    onDismiss: () -> Unit,
    onSlotAdded: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var selectedSubjectId by remember { mutableStateOf(subjects.firstOrNull()?.id ?: "") }
    var selectedDayIso by remember { mutableStateOf(1) }
    var startHour by remember { mutableStateOf(10) }
    var endHour by remember { mutableStateOf(12) }
    var location by remember { mutableStateOf("LH 30") }
    var reminderLeadMinutes by remember { mutableStateOf(15) }

    val dayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Class to Timetable", color = QuickDropColors.TextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Select Course:", color = QuickDropColors.TextSecondary, fontSize = 12.sp)
                LazyColumn(modifier = Modifier.heightIn(max = 120.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(subjects) { subj ->
                        val isSelected = subj.id == selectedSubjectId
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) Color(0x3310B981) else QuickDropColors.CosmicSurfaceElevated)
                                .border(1.dp, if (isSelected) QuickDropColors.Emerald else QuickDropColors.CosmicBorder, RoundedCornerShape(8.dp))
                                .clickable { selectedSubjectId = subj.id }
                                .padding(8.dp)
                        ) {
                            Text(subj.name, color = if (isSelected) QuickDropColors.EmeraldLight else QuickDropColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }

                Text("Day of the Week:", color = QuickDropColors.TextSecondary, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                    dayNames.forEachIndexed { idx, name ->
                        val iso = idx + 1
                        val isSelected = selectedDayIso == iso
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSelected) QuickDropColors.Emerald else QuickDropColors.CosmicSurfaceElevated)
                                .clickable { selectedDayIso = iso }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(name, color = if (isSelected) Color.White else QuickDropColors.TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = "$startHour:00",
                        onValueChange = {},
                        label = { Text("Start Time") },
                        modifier = Modifier.weight(1f),
                        readOnly = true
                    )
                    OutlinedTextField(
                        value = "$endHour:00",
                        onValueChange = {},
                        label = { Text("End Time") },
                        modifier = Modifier.weight(1f),
                        readOnly = true
                    )
                }

                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("Room / Location (e.g. LH 30)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                enabled = selectedSubjectId.isNotBlank(),
                onClick = {
                    scope.launch {
                        store.upsertClassSlot(
                            ClassSlot(
                                id = UUID.randomUUID().toString(),
                                subjectId = selectedSubjectId,
                                weekdayMask = 1 shl (selectedDayIso - 1),
                                startMinuteOfDay = startHour * 60,
                                endMinuteOfDay = endHour * 60,
                                zoneId = clock.zoneId().id,
                                location = location.ifBlank { "LH 30" },
                                enabled = true,
                                reminderEnabled = true,
                                reminderLeadMinutes = reminderLeadMinutes
                            )
                        )
                        onSlotAdded()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = QuickDropColors.Emerald)
            ) {
                Text("Add Class Slot", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

suspend fun importRecognizedSlotsIntoStore(
    recognizedSlots: List<RecognizedClassSlot>,
    store: QuickDropLocalStore,
    subjects: List<Subject>,
    clock: AppClock
) {
    val colorPalette = listOf("violet", "rose", "emerald", "amber", "blue", "cyan", "fuchsia", "indigo")
    var colorIdx = 0

    for (rSlot in recognizedSlots) {
        val displayName = if (rSlot.courseCode.isNotBlank()) "${rSlot.courseCode} - ${rSlot.courseName}" else rSlot.courseName
        val existing = subjects.find {
            (rSlot.courseCode.isNotBlank() && it.name.contains(rSlot.courseCode, ignoreCase = true)) ||
                it.name.contains(rSlot.courseName, ignoreCase = true)
        }
        val subjectId = if (existing != null) {
            existing.id
        } else {
            val s = store.createOrUpdateSubject(
                id = null,
                name = displayName,
                colorKey = colorPalette[colorIdx % colorPalette.size],
                iconKey = "school",
                term = "First Semester 2026/2027",
                instructor = "CSAC Faculty"
            )
            colorIdx++
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

fun getCybersecurity200LSlots(): List<RecognizedClassSlot> = listOf(
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
