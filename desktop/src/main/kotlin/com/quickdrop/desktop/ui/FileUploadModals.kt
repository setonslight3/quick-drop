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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import com.quickdrop.core.domain.ExtractionOutcome
import com.quickdrop.core.domain.FileIntakeStatus
import com.quickdrop.core.domain.FakeGeminiClient
import com.quickdrop.core.domain.FileExtractor
import com.quickdrop.core.model.Difficulty
import com.quickdrop.core.model.LearningItemKind
import com.quickdrop.core.model.Subject
import com.quickdrop.core.repository.QuickDropLocalStore
import com.quickdrop.core.security.AesGcmKeyVault
import com.quickdrop.core.time.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Full-featured File Ingestion Card with native Windows File Picker,
 * chunk extraction feedback, and instant AI draft trigger.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FileIngestionPanel(
    store: QuickDropLocalStore,
    clock: SystemClock,
    geminiClient: FakeGeminiClient,
    keyVault: AesGcmKeyVault,
    subjects: List<Subject>,
    initialSubjectId: String? = null,
    onReviewLecture: (String) -> Unit
) {
    val scope = rememberCoroutineScope()

    var selectedFile by remember { mutableStateOf<File?>(null) }
    var selectedSubjectId by remember {
        mutableStateOf(initialSubjectId ?: subjects.firstOrNull()?.id ?: "")
    }
    var lectureTitle by remember { mutableStateOf("") }
    var topicLabel by remember { mutableStateOf("") }

    var isExtracting by remember { mutableStateOf(false) }
    var extractionOutcome by remember { mutableStateOf<ExtractionOutcome?>(null) }
    var createdLectureId by remember { mutableStateOf<String?>(null) }

    var isGeneratingQuestions by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var showPasteNotesModal by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // --- 1. PRIMARY DROPZONE CARD ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(QuickDropColors.CosmicSurfaceElevated)
                .border(1.dp, QuickDropColors.CosmicBorder, RoundedCornerShape(20.dp))
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(QuickDropColors.Violet.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = "Upload",
                        tint = QuickDropColors.VioletLight,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Course Material & Slide Ingestion",
                        color = QuickDropColors.TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                    Text(
                        text = "Select lecture slides (PDF, PPTX, HTML) or photos of notes. Chunks are anchored verbatim for active recall.",
                        color = QuickDropColors.TextSecondary,
                        fontSize = 12.sp
                    )
                }

                // File Action Buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            scope.launch {
                                val file = withContext(Dispatchers.IO) {
                                    FilePickerHelper.pickCourseMaterialFile()
                                }
                                if (file != null && file.exists()) {
                                    selectedFile = file
                                    lectureTitle = file.nameWithoutExtension
                                        .replace("_", " ")
                                        .replace("-", " ")
                                        .capitalizeWords()
                                    topicLabel = lectureTitle
                                    extractionOutcome = null
                                    createdLectureId = null
                                    errorMessage = null
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = QuickDropColors.Violet),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(44.dp)
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("📂 Select File from Computer (PDF, PPTX, HTML, Images)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    OutlinedButton(
                        onClick = { showPasteNotesModal = true },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(44.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("✍️ Paste Text / Notes", fontSize = 12.sp)
                    }
                }

                // Subtitle specs
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("PDF (Vector & OCR)", color = QuickDropColors.TextMuted, fontSize = 10.sp)
                    Text("•", color = QuickDropColors.TextMuted, fontSize = 10.sp)
                    Text("PowerPoint (.pptx)", color = QuickDropColors.TextMuted, fontSize = 10.sp)
                    Text("•", color = QuickDropColors.TextMuted, fontSize = 10.sp)
                    Text("HTML (.html / .htm)", color = QuickDropColors.EmeraldLight, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text("•", color = QuickDropColors.TextMuted, fontSize = 10.sp)
                    Text("Images", color = QuickDropColors.TextMuted, fontSize = 10.sp)
                    Text("•", color = QuickDropColors.TextMuted, fontSize = 10.sp)
                    Text("Max 50 MB / 200 Slides", color = QuickDropColors.TextMuted, fontSize = 10.sp)
                }
            }
        }

        // --- 2. SELECTED FILE DETAILS & INGESTION CONFIRMATION ---
        if (selectedFile != null) {
            val file = selectedFile!!
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF131A2B))
                    .border(1.dp, QuickDropColors.Violet.copy(alpha = 0.4f), RoundedCornerShape(18.dp))
                    .padding(20.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(QuickDropColors.Violet.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Description, contentDescription = null, tint = QuickDropColors.VioletLight)
                            }
                            Column {
                                Text(file.name, color = QuickDropColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                val sizeKb = file.length() / 1024
                                val sizeStr = if (sizeKb > 1024) "%.1f MB".format(sizeKb / 1024.0) else "$sizeKb KB"
                                Text("$sizeStr • Ready for extraction", color = QuickDropColors.VioletLight, fontSize = 11.sp)
                            }
                        }

                        IconButton(onClick = { selectedFile = null; extractionOutcome = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", tint = QuickDropColors.TextMuted)
                        }
                    }

                    // Course selector
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Select Target Course:", color = QuickDropColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            subjects.forEach { subj ->
                                val isSelected = subj.id == selectedSubjectId
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { selectedSubjectId = subj.id },
                                    label = { Text(subj.name, fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = QuickDropColors.Emerald.copy(alpha = 0.25f),
                                        selectedLabelColor = QuickDropColors.EmeraldLight
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                )
                            }
                        }
                    }

                    // Lecture title & topic inputs
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = lectureTitle,
                            onValueChange = { lectureTitle = it },
                            label = { Text("Lecture Title") },
                            modifier = Modifier.weight(1.5f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = topicLabel,
                            onValueChange = { topicLabel = it },
                            label = { Text("Topic Label") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }

                    // Extract Button
                    Button(
                        enabled = !isExtracting,
                        onClick = {
                            if (lectureTitle.isBlank()) {
                                errorMessage = "Please enter a lecture title."
                                return@Button
                            }
                            if (selectedSubjectId.isBlank() && subjects.isNotEmpty()) {
                                selectedSubjectId = subjects.first().id
                            }
                            scope.launch {
                                isExtracting = true
                                statusText = "Reading bytes and verifying SHA-256 fingerprint..."
                                errorMessage = null
                                delay(300)

                                val bytes = withContext(Dispatchers.IO) {
                                    file.readBytes()
                                }

                                statusText = "Parsing document structure & anchored text chunks..."
                                val lecture = store.createOrUpdateLecture(
                                    subjectId = selectedSubjectId,
                                    title = lectureTitle.trim(),
                                    localDateIso = clock.localDate().toString(),
                                    topicLabel = topicLabel.trim().ifBlank { lectureTitle.trim() },
                                    notes = "Imported from file: ${file.name}"
                                )
                                createdLectureId = lecture.id

                                val (sourceFile, outcome) = store.attachAndExtractFile(
                                    lectureId = lecture.id,
                                    displayName = file.name,
                                    bytes = bytes,
                                    privateRelativePath = file.absolutePath,
                                    ocrFallbackText = null
                                )

                                extractionOutcome = outcome
                                isExtracting = false
                                statusText = null

                                if (outcome.status != FileIntakeStatus.VALID) {
                                    errorMessage = outcome.userActionableMessage ?: "Extraction error: ${outcome.status}"
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = QuickDropColors.Emerald),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().height(44.dp)
                    ) {
                        if (isExtracting) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(statusText ?: "Extracting...", fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("⚡ Ingest & Extract Anchored Slide Chunks", fontWeight = FontWeight.Bold)
                        }
                    }

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
                }
            }
        }

        // --- 3. EXTRACTION RESULTS & DRAFT GENERATION BANNER ---
        val outcome = extractionOutcome
        val lectureId = createdLectureId
        if (outcome != null && lectureId != null) {

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF0F231D))
                    .border(1.dp, QuickDropColors.Emerald.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
                    .padding(20.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = QuickDropColors.Emerald, modifier = Modifier.size(24.dp))
                            Text("Document Extracted Successfully!", color = QuickDropColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                        GlassBadge(text = "${outcome.pageOrSlideCount} Slides / Pages", backgroundColor = Color(0x3310B981), textColor = QuickDropColors.EmeraldLight)
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column {
                            Text("Extracted Chunks", color = QuickDropColors.TextSecondary, fontSize = 11.sp)
                            Text("${outcome.chunks.size} anchored segments", color = QuickDropColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Column {
                            Text("Integrity SHA-256", color = QuickDropColors.TextSecondary, fontSize = 11.sp)
                            Text(outcome.sha256.take(16) + "...", color = QuickDropColors.TextMuted, fontSize = 12.sp)
                        }
                        Column {
                            Text("MIME Format", color = QuickDropColors.TextSecondary, fontSize = 11.sp)
                            Text(outcome.detectedMimeType, color = QuickDropColors.CyanLight, fontSize = 12.sp)
                        }
                    }

                    // Preview of Slide 1 chunk text
                    if (outcome.chunks.isNotEmpty()) {
                        val firstChunk = outcome.chunks.first()
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(QuickDropColors.CosmicSurfaceElevated)
                                .padding(12.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Preview (${firstChunk.anchorKind} ${firstChunk.anchorIndex}):", color = QuickDropColors.EmeraldLight, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text(firstChunk.extractedText.take(240) + if (firstChunk.extractedText.length > 240) "..." else "", color = QuickDropColors.TextSecondary, fontSize = 12.sp)
                            }
                        }
                    }

                    // Draft MCQs / Open Review Studio Buttons
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            enabled = !isGeneratingQuestions,
                            onClick = {
                                scope.launch {
                                    isGeneratingQuestions = true
                                    store.updatePreferences { it.copy(cloudTextSubmissionConsentGranted = true) }
                                    store.generateDraftsWithAi(
                                        lectureId = lectureId,
                                        apiKey = keyVault.getDecryptedApiKey().orEmpty(),
                                        client = geminiClient
                                    )
                                    isGeneratingQuestions = false
                                    onReviewLecture(lectureId)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = QuickDropColors.Violet),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f).height(42.dp)
                        ) {
                            if (isGeneratingQuestions) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                                Spacer(Modifier.width(6.dp))
                                Text("Drafting MCQs...", fontWeight = FontWeight.Bold)
                            } else {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("✨ Draft MCQs with AI", fontWeight = FontWeight.Bold)
                            }
                        }

                        OutlinedButton(
                            onClick = { onReviewLecture(lectureId) },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f).height(42.dp)
                        ) {
                            Icon(Icons.Default.Quiz, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Open Review Studio", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // Modal to paste notes / text directly
    if (showPasteNotesModal) {
        var noteTitle by remember { mutableStateOf("") }
        var noteTopic by remember { mutableStateOf("") }
        var noteContent by remember { mutableStateOf("") }
        var noteSubjectId by remember { mutableStateOf(subjects.firstOrNull()?.id ?: "") }

        AlertDialog(
            onDismissRequest = { showPasteNotesModal = false },
            title = {
                Text("Paste Lecture Notes / Syllabus", color = QuickDropColors.TextPrimary, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 450.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Paste raw lecture notes, slide outlines, or syllabus text. We'll index and anchor it for recall testing.", color = QuickDropColors.TextSecondary, fontSize = 12.sp)

                    OutlinedTextField(
                        value = noteTitle,
                        onValueChange = { noteTitle = it },
                        label = { Text("Lecture Title (e.g. Memory Management)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = noteTopic,
                        onValueChange = { noteTopic = it },
                        label = { Text("Topic Tag") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = noteContent,
                        onValueChange = { noteContent = it },
                        label = { Text("Paste Notes Here") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 6
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (noteTitle.isNotBlank() && noteContent.isNotBlank() && noteSubjectId.isNotBlank()) {
                            scope.launch {
                                val saved = store.createOrUpdateLecture(
                                    subjectId = noteSubjectId,
                                    title = noteTitle.trim(),
                                    localDateIso = clock.localDate().toString(),
                                    topicLabel = noteTopic.trim().ifBlank { noteTitle.trim() },
                                    notes = noteContent.trim()
                                )
                                showPasteNotesModal = false
                                onReviewLecture(saved.id)
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = QuickDropColors.Emerald)
                ) {
                    Text("Save Notes & Review", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPasteNotesModal = false }) { Text("Cancel") }
            }
        )
    }
}

private fun String.capitalizeWords(): String =
    split(" ").joinToString(" ") { it.replaceFirstChar { c -> if (c.isLowerCase()) c.titlecase() else c.toString() } }
