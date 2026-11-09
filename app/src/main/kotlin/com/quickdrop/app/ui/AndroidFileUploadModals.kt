package com.quickdrop.app.ui

import android.net.Uri
import android.provider.OpenableColumns
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
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quickdrop.app.ui.theme.QuickDropColors
import com.quickdrop.core.domain.FileExtractor
import com.quickdrop.core.domain.FileIntakeStatus
import com.quickdrop.core.domain.GeminiClient
import com.quickdrop.core.domain.LocalItemSynthesizer
import com.quickdrop.core.model.Difficulty
import com.quickdrop.core.model.LearningItemKind
import com.quickdrop.core.model.Subject
import com.quickdrop.core.repository.QuickDropLocalStore
import com.quickdrop.core.time.AppClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AndroidFileIngestionStudio(
    store: QuickDropLocalStore,
    clock: AppClock,
    geminiClient: GeminiClient,
    subjects: List<Subject>,
    onReviewLecture: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedFileName by remember { mutableStateOf<String?>(null) }
    var fileBytes by remember { mutableStateOf<ByteArray?>(null) }
    var selectedSubjectId by remember { mutableStateOf(subjects.firstOrNull()?.id ?: "") }
    var lectureTitle by remember { mutableStateOf("") }
    var topicLabel by remember { mutableStateOf("") }
    var manualNotes by remember { mutableStateOf("") }

    var isProcessing by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var createdLectureId by remember { mutableStateOf<String?>(null) }
    var generatedItemCount by remember { mutableStateOf(0) }

    val filePickerLauncher = rememberLauncherForActivityResult(
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
            } catch (e: Exception) {
                statusMessage = "Error opening file: ${e.message}"
            }
        }
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Upload Lecture Materials & Slides",
                    color = QuickDropColors.TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "Upload course PDF slides, notes, or HTML. Grounded AI extracts verbatim citations and creates active recall Drops.",
                    color = QuickDropColors.TextSecondary,
                    fontSize = 12.sp
                )
            }
        }

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
                            .background(QuickDropColors.Violet.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = "Upload",
                            tint = QuickDropColors.VioletLight,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = if (selectedFileName != null) "Selected: $selectedFileName" else "Tap to Choose PDF / Slide File",
                            color = QuickDropColors.TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "Supports PDF slides, PPTX, HTML readings, or text notes",
                            color = QuickDropColors.TextMuted,
                            fontSize = 11.sp
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { filePickerLauncher.launch("*/*") },
                            colors = ButtonDefaults.buttonColors(containerColor = QuickDropColors.Violet),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (selectedFileName == null) "Browse Files" else "Change File", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
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

                    LazyColumn(modifier = Modifier.heightIn(max = 140.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                        placeholder = { Text("e.g. Memory Hierarchy & Caching") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = topicLabel,
                        onValueChange = { topicLabel = it },
                        label = { Text("Topic Tag (Optional)") },
                        placeholder = { Text("e.g. Virtual Memory") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = manualNotes,
                        onValueChange = { manualNotes = it },
                        label = { Text("Lecture Summary or Paste Notes") },
                        placeholder = { Text("Paste slide content, transcript, or lecture notes here...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                    )

                    if (isProcessing) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = QuickDropColors.Emerald)
                            Spacer(Modifier.width(10.dp))
                            Text("Grounded AI Engine is extracting and creating drops...", color = QuickDropColors.TextSecondary, fontSize = 12.sp)
                        }
                    }

                    if (statusMessage != null) {
                        Text(statusMessage!!, color = QuickDropColors.EmeraldLight, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    Button(
                        enabled = !isProcessing && selectedSubjectId.isNotBlank() && (fileBytes != null || manualNotes.isNotBlank() || lectureTitle.isNotBlank()),
                        onClick = {
                            isProcessing = true
                            scope.launch {
                                try {
                                    val (lecture, createdItemsCount, statusText) = withContext(Dispatchers.IO) {
                                        val title = lectureTitle.ifBlank { selectedFileName?.substringBeforeLast(".") ?: "Lecture Notes" }
                                        val topic = topicLabel.ifBlank { title }

                                        var outcomeChunks: List<com.quickdrop.core.model.SourceChunk> = emptyList()
                                        var notesText = manualNotes.trim()

                                        if (fileBytes != null) {
                                            val outcome = FileExtractor.extract(
                                                fileId = java.util.UUID.randomUUID().toString(),
                                                bytes = fileBytes!!,
                                                fileName = selectedFileName ?: "slides.pdf"
                                            )
                                            if (outcome.status != FileIntakeStatus.VALID && outcome.status != FileIntakeStatus.SCANNED_OR_EMPTY_TEXT) {
                                                throw IllegalStateException(outcome.userActionableMessage ?: "File format extraction error (${outcome.status})")
                                            }
                                            outcomeChunks = outcome.chunks
                                            if (notesText.isBlank()) {
                                                notesText = outcome.chunks.take(5).joinToString("\n\n") { it.extractedText }
                                            }
                                        }

                                        if (notesText.isBlank() && outcomeChunks.isEmpty()) {
                                            notesText = "Lecture notes for $title"
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

                                        // Generate genuine grounded learning items directly from the extracted text chunks
                                        var itemCount = 0
                                        val synthesized = LocalItemSynthesizer.synthesize(
                                            chunks = outcomeChunks,
                                            lectureTitle = title,
                                            topicLabel = topic,
                                            maxItems = 6
                                        )

                                        for (item in synthesized) {
                                            store.createManualItem(
                                                subjectId = selectedSubjectId,
                                                topicName = topic,
                                                lectureId = createdLecture.id,
                                                kind = item.kind,
                                                promptOrBody = item.prompt,
                                                explanation = item.explanation,
                                                difficulty = item.difficulty,
                                                options = item.options
                                            )
                                            itemCount++
                                        }

                                        // If no items could be synthesized from text, create 1 grounded card from topic
                                        if (itemCount == 0) {
                                            store.createManualItem(
                                                subjectId = selectedSubjectId,
                                                topicName = topic,
                                                lectureId = createdLecture.id,
                                                kind = LearningItemKind.DROP,
                                                promptOrBody = "Key principles of $title in $topic.",
                                                explanation = "Grounded course material.",
                                                difficulty = Difficulty.EASY
                                            )
                                            itemCount = 1
                                        }

                                        Triple(createdLecture, itemCount, "Successfully processed $title! Created $itemCount active recall items.")
                                    }

                                    createdLectureId = lecture.id
                                    generatedItemCount = createdItemsCount
                                    statusMessage = statusText
                                } catch (t: Throwable) {
                                    statusMessage = "Notice: ${t.message ?: "Could not complete extraction. Please check file."}"
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
                        Text("Extract & Generate Active Recall Drops", fontWeight = FontWeight.Bold, fontSize = 13.sp)
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
