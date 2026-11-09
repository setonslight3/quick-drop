package com.quickdrop.app.ui

import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quickdrop.app.ui.theme.QuickDropColors
import com.quickdrop.core.domain.FileExtractor
import com.quickdrop.core.domain.FileIntakeStatus
import com.quickdrop.core.domain.GeminiClient
import com.quickdrop.core.domain.ImportedQuestionPack
import com.quickdrop.core.domain.QuestionPackParser
import com.quickdrop.core.model.Difficulty
import com.quickdrop.core.model.LearningItemKind
import com.quickdrop.core.model.Subject
import com.quickdrop.core.repository.QuickDropLocalStore
import com.quickdrop.core.security.ApiKeyVault
import com.quickdrop.core.time.AppClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AndroidFileIngestionStudio(
    store: QuickDropLocalStore,
    clock: AppClock,
    geminiClient: GeminiClient,
    apiKeyVault: ApiKeyVault,
    subjects: List<Subject>,
    onReviewLecture: (String) -> Unit,
    onOpenSettings: () -> Unit = {}
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    // Mode: 0 = Chatbot JSON Pack (Free, 0 tokens), 1 = Direct File Upload
    var selectedTab by remember { mutableIntStateOf(0) }

    // Common fields
    var selectedSubjectId by remember { mutableStateOf(subjects.firstOrNull()?.id ?: "") }
    var lectureTitle by remember { mutableStateOf("") }
    var topicLabel by remember { mutableStateOf("") }

    // Mode 0: JSON Pack fields
    var jsonInputText by remember { mutableStateOf("") }
    var parsedPack by remember { mutableStateOf<ImportedQuestionPack?>(null) }
    var jsonFileName by remember { mutableStateOf<String?>(null) }
    var showJsonPasteBox by remember { mutableStateOf(false) }
    var showSampleJsonDialog by remember { mutableStateOf(false) }

    // Mode 1: Direct File fields
    var selectedFileName by remember { mutableStateOf<String?>(null) }
    var fileBytes by remember { mutableStateOf<ByteArray?>(null) }
    var manualNotes by remember { mutableStateOf("") }
    var showApiKeyMissingDialog by remember { mutableStateOf(false) }

    // Status state
    var isProcessing by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isErrorMessage by remember { mutableStateOf(false) }
    var createdLectureId by remember { mutableStateOf<String?>(null) }
    var generatedItemCount by remember { mutableStateOf(0) }

    val selectedSubject = subjects.find { it.id == selectedSubjectId }

    // File Picker for JSON Pack
    val jsonFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val cr = context.contentResolver
                var name = "questions.json"
                cr.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) name = cursor.getString(idx)
                    }
                }
                val bytes = cr.openInputStream(uri)?.use { it.readBytes() }
                if (bytes != null) {
                    val content = String(bytes, Charsets.UTF_8)
                    jsonFileName = name
                    jsonInputText = content
                    val result = QuestionPackParser.parse(content)
                    if (result.isSuccess) {
                        val pack = result.getOrThrow()
                        parsedPack = pack
                        if (pack.lectureTitle?.isNotBlank() == true) {
                            lectureTitle = pack.lectureTitle!!
                        }
                        if (pack.topic?.isNotBlank() == true) {
                            topicLabel = pack.topic!!
                        }
                        statusMessage = "Loaded $name: ${pack.drops.size} Drops and ${pack.questions.size} MCQs ready to import!"
                        isErrorMessage = false
                    } else {
                        statusMessage = "Could not parse JSON: ${result.exceptionOrNull()?.message}"
                        isErrorMessage = true
                    }
                }
            } catch (e: Exception) {
                statusMessage = "Error reading file: ${e.message}"
                isErrorMessage = true
            }
        }
    }

    // File Picker for Regular Documents (PDF, PPTX, HTML, TXT)
    val documentFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val cr = context.contentResolver
                var name = "document.pdf"
                cr.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) name = cursor.getString(idx)
                    }
                }
                selectedFileName = name
                fileBytes = cr.openInputStream(uri)?.use { it.readBytes() }
                if (lectureTitle.isBlank()) {
                    lectureTitle = name.substringBeforeLast(".")
                }
                statusMessage = "Loaded $name (${fileBytes?.size ?: 0} bytes). Ready to extract!"
                isErrorMessage = false
            } catch (e: Exception) {
                statusMessage = "Error opening file: ${e.message}"
                isErrorMessage = true
            }
        }
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Page Title & Header
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Active Recall Ingestion Studio",
                    color = QuickDropColors.TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "Generate deep, exam-level active recall Drops & MCQs for your courses.",
                    color = QuickDropColors.TextSecondary,
                    fontSize = 12.sp
                )
            }
        }

        // Mode Navigation Switcher Tabs
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(QuickDropColors.CosmicSurfaceElevated)
                    .border(1.dp, QuickDropColors.CosmicBorder, RoundedCornerShape(14.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Tab 0: Chatbot JSON (Free)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selectedTab == 0) QuickDropColors.Violet else Color.Transparent)
                        .clickable { selectedTab = 0; statusMessage = null }
                        .padding(vertical = 10.dp, horizontal = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(
                            Icons.Default.DataObject,
                            contentDescription = null,
                            tint = if (selectedTab == 0) Color.White else QuickDropColors.TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "AI Chatbot JSON (Free)",
                            color = if (selectedTab == 0) Color.White else QuickDropColors.TextSecondary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }

                // Tab 1: Direct File Upload
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selectedTab == 1) QuickDropColors.Emerald else Color.Transparent)
                        .clickable { selectedTab = 1; statusMessage = null }
                        .padding(vertical = 10.dp, horizontal = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(
                            Icons.Default.UploadFile,
                            contentDescription = null,
                            tint = if (selectedTab == 1) Color.White else QuickDropColors.TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Direct File Upload",
                            color = if (selectedTab == 1) Color.White else QuickDropColors.TextSecondary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        // =====================================================================
        // TAB 0: CHATBOT JSON PACK (FREE & 100% ACCURATE)
        // =====================================================================
        if (selectedTab == 0) {
            // Step 1: Copy AI Prompt & Schema
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(QuickDropColors.CosmicSurfaceElevated)
                        .border(1.dp, QuickDropColors.Violet.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
                        .padding(18.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(QuickDropColors.Violet.copy(alpha = 0.25f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("1", color = QuickDropColors.VioletLight, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                            Text(
                                text = "Step 1: Get AI Chatbot Prompt & Schema",
                                color = QuickDropColors.TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }

                        Text(
                            text = "Use ChatGPT, Claude, or Gemini Web for free. Attach your lecture PDF/slides to the chatbot, paste this prompt, and it will return a high-yield question pack.",
                            color = QuickDropColors.TextSecondary,
                            fontSize = 12.sp
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    val courseName = selectedSubject?.name ?: "Course"
                                    val prompt = QuestionPackParser.getSampleChatbotPrompt(courseName, lectureTitle)
                                    clipboardManager.setText(AnnotatedString(prompt))
                                    Toast.makeText(context, "Prompt copied! Paste it into ChatGPT or Claude with your lecture file.", Toast.LENGTH_LONG).show()
                                    statusMessage = "Copied AI prompt to clipboard! Paste into your chatbot."
                                    isErrorMessage = false
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = QuickDropColors.Violet),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1.3f)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Copy Chatbot Prompt", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }

                            OutlinedButton(
                                onClick = { showSampleJsonDialog = true },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("View Schema", fontSize = 12.sp, color = QuickDropColors.VioletLight)
                            }
                        }
                    }
                }
            }

            // Step 2: Course Target & JSON Importer
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
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(QuickDropColors.Emerald.copy(alpha = 0.25f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("2", color = QuickDropColors.EmeraldLight, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                            Text(
                                text = "Step 2: Import Generated JSON",
                                color = QuickDropColors.TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }

                        // Target Course Selector
                        Text("Target Course:", color = QuickDropColors.TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        LazyColumn(modifier = Modifier.heightIn(max = 120.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(subjects) { subj ->
                                val isSelected = subj.id == selectedSubjectId
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isSelected) Color(0x3310B981) else QuickDropColors.CosmicSurface)
                                        .border(1.dp, if (isSelected) QuickDropColors.Emerald else QuickDropColors.CosmicBorder, RoundedCornerShape(10.dp))
                                        .clickable { selectedSubjectId = subj.id }
                                        .padding(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(subj.name, color = if (isSelected) QuickDropColors.EmeraldLight else QuickDropColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        if (isSelected) {
                                            Icon(Icons.Default.Check, contentDescription = null, tint = QuickDropColors.Emerald, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }

                        // Two Ways to Import: Browse file or Paste text
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = { jsonFilePickerLauncher.launch("*/*") },
                                colors = ButtonDefaults.buttonColors(containerColor = QuickDropColors.CosmicSurfaceHighlight),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp), tint = QuickDropColors.EmeraldLight)
                                Spacer(Modifier.width(6.dp))
                                Text("Browse .JSON", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = QuickDropColors.TextPrimary)
                            }

                            Button(
                                onClick = { showJsonPasteBox = !showJsonPasteBox },
                                colors = ButtonDefaults.buttonColors(containerColor = QuickDropColors.CosmicSurfaceHighlight),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.DataObject, contentDescription = null, modifier = Modifier.size(16.dp), tint = QuickDropColors.CyanLight)
                                Spacer(Modifier.width(6.dp))
                                Text(if (showJsonPasteBox) "Hide Paste Box" else "Paste Text", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = QuickDropColors.TextPrimary)
                            }
                        }

                        if (showJsonPasteBox) {
                            OutlinedTextField(
                                value = jsonInputText,
                                onValueChange = {
                                    jsonInputText = it
                                    if (it.isNotBlank()) {
                                        val res = QuestionPackParser.parse(it)
                                        if (res.isSuccess) {
                                            parsedPack = res.getOrThrow()
                                            statusMessage = "Valid JSON: ${parsedPack!!.drops.size} Drops and ${parsedPack!!.questions.size} MCQs detected!"
                                            isErrorMessage = false
                                        } else {
                                            statusMessage = "JSON error: ${res.exceptionOrNull()?.message}"
                                            isErrorMessage = true
                                        }
                                    }
                                },
                                label = { Text("Paste JSON from ChatGPT / Claude here") },
                                modifier = Modifier.fillMaxWidth().height(140.dp)
                            )
                        }

                        // Preview of Parsed Question Pack
                        if (parsedPack != null) {
                            val pack = parsedPack!!
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF064E3B).copy(alpha = 0.35f))
                                    .border(1.dp, QuickDropColors.Emerald.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                    .padding(12.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = QuickDropColors.Emerald, modifier = Modifier.size(18.dp))
                                        Text("Valid Question Pack Ready to Ingest", color = QuickDropColors.EmeraldLight, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                    Text(
                                        text = "• ${pack.drops.size} Active Recall Drops (Flashcards)\n• ${pack.questions.size} Multiple-Choice Questions (MCQs)",
                                        color = QuickDropColors.TextPrimary,
                                        fontSize = 12.sp,
                                        lineHeight = 18.sp
                                    )
                                }
                            }
                        }

                        // Submit Button for JSON Ingestion
                        Button(
                            enabled = !isProcessing && selectedSubjectId.isNotBlank() && parsedPack != null && (parsedPack!!.drops.isNotEmpty() || parsedPack!!.questions.isNotEmpty()),
                            onClick = {
                                isProcessing = true
                                scope.launch {
                                    try {
                                        val pack = parsedPack!!
                                        val effectiveTitle = lectureTitle.ifBlank { pack.lectureTitle ?: "Lecture Study Pack" }
                                        val effectiveTopic = topicLabel.ifBlank { pack.topic ?: effectiveTitle }

                                        val (lecture, count) = withContext(Dispatchers.IO) {
                                            val createdLecture = store.createOrUpdateLecture(
                                                subjectId = selectedSubjectId,
                                                title = effectiveTitle,
                                                localDateIso = clock.localDate().toString(),
                                                topicLabel = effectiveTopic,
                                                notes = "Imported via AI Question Pack JSON"
                                            )

                                            var itemsCount = 0

                                            // Add Drops
                                            for (d in pack.drops) {
                                                store.createManualItem(
                                                    subjectId = selectedSubjectId,
                                                    topicName = effectiveTopic,
                                                    lectureId = createdLecture.id,
                                                    kind = LearningItemKind.DROP,
                                                    promptOrBody = d.concept,
                                                    explanation = d.explanation,
                                                    difficulty = d.difficulty
                                                )
                                                itemsCount++
                                            }

                                            // Add MCQs
                                            for (q in pack.questions) {
                                                val optionsList = q.options.mapIndexed { idx, optText ->
                                                    optText to (idx == q.correctIndex)
                                                }
                                                store.createManualItem(
                                                    subjectId = selectedSubjectId,
                                                    topicName = effectiveTopic,
                                                    lectureId = createdLecture.id,
                                                    kind = LearningItemKind.MCQ,
                                                    promptOrBody = q.question,
                                                    explanation = q.explanation,
                                                    difficulty = q.difficulty,
                                                    options = optionsList
                                                )
                                                itemsCount++
                                            }

                                            createdLecture to itemsCount
                                        }

                                        createdLectureId = lecture.id
                                        generatedItemCount = count
                                        statusMessage = "Successfully imported $count active recall questions & drops! (Zero API tokens consumed)"
                                        isErrorMessage = false
                                    } catch (t: Throwable) {
                                        statusMessage = "Import failed: ${t.message}"
                                        isErrorMessage = true
                                    } finally {
                                        isProcessing = false
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = QuickDropColors.Emerald),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().height(46.dp)
                        ) {
                            if (isProcessing) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White)
                                Spacer(Modifier.width(8.dp))
                                Text("Importing Questions...", fontWeight = FontWeight.Bold)
                            } else {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("⚡ Ingest JSON Pack & Save Drops (0 Tokens)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }

                        if (statusMessage != null) {
                            Text(
                                text = statusMessage!!,
                                color = if (isErrorMessage) QuickDropColors.RoseLight else QuickDropColors.EmeraldLight,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }

                        if (createdLectureId != null) {
                            OutlinedButton(
                                onClick = { onReviewLecture(createdLectureId!!) },
                                modifier = Modifier.fillMaxWidth().height(42.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Review Ingested Lecture & Drops →", color = QuickDropColors.CyanLight, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // =====================================================================
        // TAB 1: DIRECT FILE UPLOAD (PDF, PPTX, HTML - REQUIRES API KEY)
        // =====================================================================
        if (selectedTab == 1) {
            // Upload Dropzone
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(QuickDropColors.CosmicSurfaceElevated)
                        .border(1.dp, QuickDropColors.CosmicBorder, RoundedCornerShape(18.dp))
                        .padding(20.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(CircleShape)
                                .background(QuickDropColors.Emerald.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = "Upload",
                                tint = QuickDropColors.EmeraldLight,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = if (selectedFileName != null) "Selected: $selectedFileName" else "Tap to Choose Course File",
                                color = QuickDropColors.TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Text(
                                text = "PDF slides, PPTX, HTML, or plain text lecture notes",
                                color = QuickDropColors.TextMuted,
                                fontSize = 11.sp
                            )
                        }

                        Button(
                            onClick = { documentFilePickerLauncher.launch("*/*") },
                            colors = ButtonDefaults.buttonColors(containerColor = QuickDropColors.Emerald),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (selectedFileName == null) "Browse Files" else "Change File", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }

            // Target Course & Lecture Details Form
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
                        Text("Target Course:", color = QuickDropColors.TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)

                        LazyColumn(modifier = Modifier.heightIn(max = 120.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(subjects) { subj ->
                                val isSelected = subj.id == selectedSubjectId
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isSelected) Color(0x3310B981) else QuickDropColors.CosmicSurface)
                                        .border(1.dp, if (isSelected) QuickDropColors.Emerald else QuickDropColors.CosmicBorder, RoundedCornerShape(10.dp))
                                        .clickable { selectedSubjectId = subj.id }
                                        .padding(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(subj.name, color = if (isSelected) QuickDropColors.EmeraldLight else QuickDropColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        if (isSelected) {
                                            Icon(Icons.Default.Check, contentDescription = null, tint = QuickDropColors.Emerald, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }

                        OutlinedTextField(
                            value = lectureTitle,
                            onValueChange = { lectureTitle = it },
                            label = { Text("Lecture / Chapter Title") },
                            placeholder = { Text("e.g. Foundations of Cybercrime") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = topicLabel,
                            onValueChange = { topicLabel = it },
                            label = { Text("Topic Tag (Optional)") },
                            placeholder = { Text("e.g. Cybercrime Taxonomy") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = manualNotes,
                            onValueChange = { manualNotes = it },
                            label = { Text("Paste Slide Text or Notes (Optional)") },
                            placeholder = { Text("Optional summary or pasted outline...") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(90.dp)
                        )

                        if (isProcessing) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = QuickDropColors.Emerald)
                                Spacer(Modifier.width(10.dp))
                                Text("Grounded Gemini AI is generating active recall drops...", color = QuickDropColors.TextSecondary, fontSize = 12.sp)
                            }
                        }

                        if (statusMessage != null) {
                            Text(
                                text = statusMessage!!,
                                color = if (isErrorMessage) QuickDropColors.RoseLight else QuickDropColors.EmeraldLight,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }

                        Button(
                            enabled = !isProcessing && selectedSubjectId.isNotBlank() && (fileBytes != null || manualNotes.isNotBlank() || lectureTitle.isNotBlank()),
                            onClick = {
                                // 1. Strict Check for API Key
                                val apiKey = apiKeyVault.getDecryptedApiKey()
                                if (apiKey.isNullOrBlank()) {
                                    showApiKeyMissingDialog = true
                                    return@Button
                                }

                                isProcessing = true
                                scope.launch {
                                    try {
                                        val title = lectureTitle.ifBlank { selectedFileName?.substringBeforeLast(".") ?: "Lecture Notes" }
                                        val topic = topicLabel.ifBlank { title }

                                        val (lecture, count, statusText) = withContext(Dispatchers.IO) {
                                            // Extract file chunks
                                            var outcomeChunks: List<com.quickdrop.core.model.SourceChunk> = emptyList()
                                            var notesText = manualNotes.trim()

                                            if (fileBytes != null) {
                                                val outcome = FileExtractor.extract(
                                                    fileId = java.util.UUID.randomUUID().toString(),
                                                    bytes = fileBytes!!,
                                                    fileName = selectedFileName ?: "document.pdf"
                                                )
                                                outcomeChunks = outcome.chunks
                                                if (notesText.isBlank()) {
                                                    notesText = outcome.chunks.take(5).joinToString("\n\n") { it.extractedText }
                                                }
                                            }

                                            val createdLecture = store.createOrUpdateLecture(
                                                subjectId = selectedSubjectId,
                                                title = title,
                                                localDateIso = clock.localDate().toString(),
                                                topicLabel = topic,
                                                notes = notesText
                                            )

                                            if (fileBytes != null) {
                                                try {
                                                    store.attachAndExtractFile(
                                                        lectureId = createdLecture.id,
                                                        displayName = selectedFileName ?: "document",
                                                        bytes = fileBytes!!,
                                                        privateRelativePath = selectedFileName ?: "file",
                                                        ocrFallbackText = null
                                                    )
                                                } catch (_: Throwable) {
                                                }
                                            }

                                            // Trigger Gemini AI generation
                                            store.updatePreferences { it.copy(cloudTextSubmissionConsentGranted = true) }
                                            val aiJob = store.generateDraftsWithAi(
                                                lectureId = createdLecture.id,
                                                apiKey = apiKey,
                                                client = geminiClient
                                            )

                                            if (aiJob.state == com.quickdrop.core.model.FileJobState.NEEDS_USER_ACTION ||
                                                aiJob.state == com.quickdrop.core.model.FileJobState.FAILED_FINAL ||
                                                aiJob.state == com.quickdrop.core.model.FileJobState.FAILED_RETRYABLE) {
                                                throw IllegalStateException(aiJob.sanitizedStatusMessage ?: "AI generation failed.")
                                            }

                                            val generatedDrafts = store.observeDraftsForLecture(createdLecture.id).first()
                                            var approvedCount = 0
                                            for (d in generatedDrafts) {
                                                store.approveItem(d.id)
                                                approvedCount++
                                            }

                                            Triple(createdLecture, approvedCount, "Generated $approvedCount grounded recall items with Gemini AI!")
                                        }

                                        createdLectureId = lecture.id
                                        generatedItemCount = count
                                        statusMessage = statusText
                                        isErrorMessage = false
                                    } catch (t: Throwable) {
                                        statusMessage = "Extraction error: ${t.message ?: "Could not complete AI generation."}"
                                        isErrorMessage = true
                                    } finally {
                                        isProcessing = false
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = QuickDropColors.Emerald),
                            modifier = Modifier.fillMaxWidth().height(46.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Generate Questions with Gemini AI", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }

                        if (createdLectureId != null) {
                            OutlinedButton(
                                onClick = { onReviewLecture(createdLectureId!!) },
                                modifier = Modifier.fillMaxWidth().height(42.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Review Ingested Lecture & Drops →", color = QuickDropColors.CyanLight, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal: API Key Missing Warning
    if (showApiKeyMissingDialog) {
        AlertDialog(
            onDismissRequest = { showApiKeyMissingDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Key, contentDescription = null, tint = QuickDropColors.Amber)
                    Text("Gemini API Key Required", color = QuickDropColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Direct in-app AI question generation requires a Google Gemini API key to analyze your slides and generate accurate, exam-level recall items.",
                        color = QuickDropColors.TextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0x22F59E0B))
                            .border(1.dp, QuickDropColors.Amber.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = "💡 Don't have an API key? You can use our Free Chatbot JSON Pack option! Copy our prompt, paste into ChatGPT/Claude along with your file, and import the JSON for free with 0 API tokens.",
                            color = QuickDropColors.AmberLight,
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showApiKeyMissingDialog = false
                        selectedTab = 0 // Switch to free Chatbot JSON tab
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = QuickDropColors.Violet)
                ) {
                    Text("Use Free Chatbot JSON", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showApiKeyMissingDialog = false
                        onOpenSettings()
                    }
                ) {
                    Text("Add Key in Settings")
                }
            }
        )
    }

    // Modal: Sample JSON Schema Viewer
    if (showSampleJsonDialog) {
        AlertDialog(
            onDismissRequest = { showSampleJsonDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.DataObject, contentDescription = null, tint = QuickDropColors.VioletLight)
                    Text("Chatbot Question Pack Schema", color = QuickDropColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Any AI chatbot (ChatGPT, Claude, Gemini Web) can produce this format when given our prompt:",
                        color = QuickDropColors.TextSecondary,
                        fontSize = 12.sp
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(QuickDropColors.CosmicSurface)
                            .border(1.dp, QuickDropColors.CosmicBorder, RoundedCornerShape(10.dp))
                            .padding(10.dp)
                    ) {
                        LazyColumn {
                            item {
                                Text(
                                    text = QuestionPackParser.SAMPLE_JSON_TEMPLATE,
                                    color = QuickDropColors.CyanLight,
                                    fontSize = 11.sp,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(QuestionPackParser.SAMPLE_JSON_TEMPLATE))
                        Toast.makeText(context, "Sample JSON copied to clipboard!", Toast.LENGTH_SHORT).show()
                        showSampleJsonDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = QuickDropColors.Violet)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Copy Sample JSON", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSampleJsonDialog = false }) {
                    Text("Close", color = QuickDropColors.TextSecondary)
                }
            }
        )
    }
}
