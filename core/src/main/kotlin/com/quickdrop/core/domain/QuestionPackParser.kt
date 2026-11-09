package com.quickdrop.core.domain

import com.quickdrop.core.model.Difficulty
import com.quickdrop.core.model.LearningItemKind
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class ImportedDrop(
    val concept: String,
    val explanation: String = "",
    val difficulty: Difficulty = Difficulty.MEDIUM,
    val citationQuote: String = ""
)

data class ImportedQuestion(
    val question: String,
    val options: List<String>,
    val correctIndex: Int,
    val explanation: String = "",
    val difficulty: Difficulty = Difficulty.MEDIUM
)

data class ImportedQuestionPack(
    val courseCode: String? = null,
    val courseName: String? = null,
    val lectureTitle: String? = null,
    val topic: String? = null,
    val drops: List<ImportedDrop> = emptyList(),
    val questions: List<ImportedQuestion> = emptyList()
)

object QuestionPackParser {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    /**
     * Standard sample prompt and schema for external chatbots.
     */
    fun getSampleChatbotPrompt(courseName: String = "", lectureTitle: String = ""): String {
        val courseHint = if (courseName.isNotBlank()) " for $courseName" else ""
        val lectureHint = if (lectureTitle.isNotBlank()) " covering $lectureTitle" else ""

        return """
You are an expert academic tutor. Please analyze the attached lecture material$courseHint$lectureHint and generate an active recall question pack in JSON format.

Follow this exact JSON structure:
{
  "courseCode": "${courseName.take(10).ifBlank { "COURSE 101" }}",
  "courseName": "${courseName.ifBlank { "Course Name" }}",
  "lectureTitle": "${lectureTitle.ifBlank { "Lecture Title" }}",
  "topic": "${lectureTitle.ifBlank { "Key Concept" }}",
  "drops": [
    {
      "concept": "Clear, concise core principle or definition for quick flashcard active recall",
      "explanation": "Detailed theoretical context and explanation from the lecture",
      "difficulty": "EASY"
    },
    {
      "concept": "Another high-yield concept statement from the material",
      "explanation": "Explanation and application context",
      "difficulty": "MEDIUM"
    }
  ],
  "questions": [
    {
      "question": "Challenging multiple-choice question testing deep conceptual understanding",
      "options": [
        "Correct answer text",
        "Plausible distractor 1",
        "Plausible distractor 2",
        "Plausible distractor 3"
      ],
      "correctIndex": 0,
      "explanation": "Detailed explanation of why the correct option is right and others are incorrect",
      "difficulty": "MEDIUM"
    }
  ]
}

Guidelines:
- Generate 5 to 10 Flashcard Drops and 5 to 10 Multiple-Choice Questions (MCQs).
- Difficulty must be "EASY", "MEDIUM", or "HARD".
- "correctIndex" is the zero-based index (0, 1, 2, or 3) of the correct answer in the "options" list.
- Return ONLY valid JSON (no markdown formatting, no extra explanation text).
""".trim()
    }

    /**
     * Standard sample JSON template string.
     */
    val SAMPLE_JSON_TEMPLATE: String = """
{
  "courseCode": "CYB 203",
  "courseName": "Cybercrime, Law & Countermeasures",
  "lectureTitle": "Week 1: Foundations of Cybercrime",
  "topic": "Cybercrime Taxonomy",
  "drops": [
    {
      "concept": "Cyber-dependent crimes are offenses that can only be committed using Information and Communications Technology (ICT).",
      "explanation": "Examples include malware creation, ransomware attacks, and Distributed Denial of Service (DDoS) operations.",
      "difficulty": "EASY"
    },
    {
      "concept": "Cyber-enabled crimes are traditional offenses scaled or facilitated by computers and internet connectivity.",
      "explanation": "Examples include online fraud, cyber stalking, and digital identity theft where computers amplify existing crimes.",
      "difficulty": "MEDIUM"
    }
  ],
  "questions": [
    {
      "question": "Which of the following scenarios best exemplifies a cyber-dependent crime rather than a cyber-enabled crime?",
      "options": [
        "Launching a Distributed Denial of Service (DDoS) botnet against a critical web server",
        "Sending a phishing email to deceive a student into transferring bank funds",
        "Harassing an individual across multiple social media platforms",
        "Selling counterfeit software licenses via an online classified forum"
      ],
      "correctIndex": 0,
      "explanation": "A DDoS attack requires computers and networks as both the tool and the target and cannot exist without ICT infrastructure.",
      "difficulty": "MEDIUM"
    },
    {
      "question": "What is the primary role of cryptographic hash verification (such as SHA-256) in digital evidence forensics?",
      "options": [
        "To establish and prove that seized electronic evidence was not tampered with or altered",
        "To decrypt encrypted user hard drives without needing passphrases",
        "To trace the geographical GPS location of the suspect's device",
        "To permanently compress disk forensic images for cloud storage"
      ],
      "correctIndex": 0,
      "explanation": "Cryptographic hashes verify bitstream image integrity to preserve an unbroken chain of custody admissible in court.",
      "difficulty": "HARD"
    }
  ]
}
""".trim()

    /**
     * Parses raw JSON string into an [ImportedQuestionPack].
     * Handles markdown code fence cleanup, field aliases, and structure variations.
     */
    fun parse(rawText: String): Result<ImportedQuestionPack> {
        return try {
            val clean = stripMarkdownFences(rawText.trim())
            val rootElement = json.parseToJsonElement(clean)

            when (rootElement) {
                is JsonObject -> Result.success(parseJsonObject(rootElement))
                is JsonArray -> Result.success(parseJsonArrayItems(rootElement))
                else -> Result.failure(IllegalArgumentException("JSON must be an object or array"))
            }
        } catch (t: Throwable) {
            Result.failure(IllegalArgumentException("Invalid JSON format: ${t.message}"))
        }
    }

    private fun stripMarkdownFences(text: String): String {
        var result = text
        if (result.startsWith("```json", ignoreCase = true)) {
            result = result.substringAfter("\n")
        } else if (result.startsWith("```")) {
            result = result.substringAfter("\n")
        }
        if (result.endsWith("```")) {
            result = result.substringBeforeLast("```")
        }
        return result.trim()
    }

    private fun parseJsonObject(obj: JsonObject): ImportedQuestionPack {
        val courseCode = obj["courseCode"]?.jsonPrimitive?.contentOrNull
            ?: obj["code"]?.jsonPrimitive?.contentOrNull
        val courseName = obj["courseName"]?.jsonPrimitive?.contentOrNull
            ?: obj["course"]?.jsonPrimitive?.contentOrNull
            ?: obj["subject"]?.jsonPrimitive?.contentOrNull
        val lectureTitle = obj["lectureTitle"]?.jsonPrimitive?.contentOrNull
            ?: obj["lecture"]?.jsonPrimitive?.contentOrNull
            ?: obj["title"]?.jsonPrimitive?.contentOrNull
        val topic = obj["topic"]?.jsonPrimitive?.contentOrNull
            ?: obj["topicName"]?.jsonPrimitive?.contentOrNull
            ?: lectureTitle

        val drops = mutableListOf<ImportedDrop>()
        val dropsArray = obj["drops"]?.jsonArray
            ?: obj["flashcards"]?.jsonArray
            ?: obj["cards"]?.jsonArray
            ?: obj["notes"]?.jsonArray

        dropsArray?.forEach { el ->
            if (el is JsonObject) {
                parseDropObject(el)?.let { drops.add(it) }
            }
        }

        val questions = mutableListOf<ImportedQuestion>()
        val questionsArray = obj["questions"]?.jsonArray
            ?: obj["mcqs"]?.jsonArray
            ?: obj["quiz"]?.jsonArray
            ?: obj["items"]?.jsonArray

        questionsArray?.forEach { el ->
            if (el is JsonObject) {
                // If it's an MCQ
                val mcq = parseQuestionObject(el)
                if (mcq != null) {
                    questions.add(mcq)
                } else {
                    // Might be a drop inside items list
                    val drop = parseDropObject(el)
                    if (drop != null) drops.add(drop)
                }
            }
        }

        return ImportedQuestionPack(
            courseCode = courseCode,
            courseName = courseName,
            lectureTitle = lectureTitle,
            topic = topic,
            drops = drops,
            questions = questions
        )
    }

    private fun parseJsonArrayItems(array: JsonArray): ImportedQuestionPack {
        val drops = mutableListOf<ImportedDrop>()
        val questions = mutableListOf<ImportedQuestion>()

        for (el in array) {
            if (el is JsonObject) {
                val kindStr = el["kind"]?.jsonPrimitive?.contentOrNull?.uppercase()
                if (kindStr == "DROP" || kindStr == "FLASHCARD" || el.containsKey("concept")) {
                    parseDropObject(el)?.let { drops.add(it) }
                } else {
                    val mcq = parseQuestionObject(el)
                    if (mcq != null) questions.add(mcq)
                    else parseDropObject(el)?.let { drops.add(it) }
                }
            }
        }

        return ImportedQuestionPack(
            drops = drops,
            questions = questions
        )
    }

    private fun parseDropObject(el: JsonObject): ImportedDrop? {
        val concept = el["concept"]?.jsonPrimitive?.contentOrNull
            ?: el["prompt"]?.jsonPrimitive?.contentOrNull
            ?: el["promptOrBody"]?.jsonPrimitive?.contentOrNull
            ?: el["body"]?.jsonPrimitive?.contentOrNull
            ?: el["fact"]?.jsonPrimitive?.contentOrNull
            ?: return null

        if (concept.isBlank()) return null

        val explanation = el["explanation"]?.jsonPrimitive?.contentOrNull
            ?: el["notes"]?.jsonPrimitive?.contentOrNull
            ?: el["context"]?.jsonPrimitive?.contentOrNull
            ?: ""

        val diffStr = el["difficulty"]?.jsonPrimitive?.contentOrNull?.uppercase() ?: "MEDIUM"
        val difficulty = when (diffStr) {
            "EASY" -> Difficulty.EASY
            "HARD" -> Difficulty.HARD
            else -> Difficulty.MEDIUM
        }

        val quote = el["citationQuote"]?.jsonPrimitive?.contentOrNull
            ?: el["evidenceQuote"]?.jsonPrimitive?.contentOrNull
            ?: concept

        return ImportedDrop(
            concept = concept.trim(),
            explanation = explanation.trim(),
            difficulty = difficulty,
            citationQuote = quote.trim()
        )
    }

    private fun parseQuestionObject(el: JsonObject): ImportedQuestion? {
        val questionText = el["question"]?.jsonPrimitive?.contentOrNull
            ?: el["prompt"]?.jsonPrimitive?.contentOrNull
            ?: el["promptOrBody"]?.jsonPrimitive?.contentOrNull
            ?: el["stem"]?.jsonPrimitive?.contentOrNull
            ?: return null

        if (questionText.isBlank()) return null

        val rawOptions = el["options"]?.jsonArray
            ?: el["choices"]?.jsonArray
            ?: el["answers"]?.jsonArray
            ?: return null

        val options = mutableListOf<String>()
        var correctIdxFromObjects = -1

        for ((idx, optEl) in rawOptions.withIndex()) {
            if (optEl is JsonObject) {
                val text = optEl["text"]?.jsonPrimitive?.contentOrNull
                    ?: optEl["body"]?.jsonPrimitive?.contentOrNull
                    ?: optEl["option"]?.jsonPrimitive?.contentOrNull
                    ?: ""
                val isCorrect = optEl["isCorrect"]?.jsonPrimitive?.booleanOrNull == true ||
                    optEl["correct"]?.jsonPrimitive?.booleanOrNull == true
                if (isCorrect) correctIdxFromObjects = idx
                if (text.isNotBlank()) options.add(text.trim())
            } else {
                val text = optEl.jsonPrimitive.contentOrNull ?: ""
                if (text.isNotBlank()) options.add(text.trim())
            }
        }

        if (options.size < 2) return null

        var correctIdx = el["correctIndex"]?.jsonPrimitive?.intOrNull
            ?: el["answerIndex"]?.jsonPrimitive?.intOrNull
            ?: el["correctAnswerIndex"]?.jsonPrimitive?.intOrNull
            ?: correctIdxFromObjects

        // If not specified as index, check if specified by letter ("A", "B", "C", "D") or matching text
        if (correctIdx < 0 || correctIdx >= options.size) {
            val correctStr = el["correctOption"]?.jsonPrimitive?.contentOrNull
                ?: el["correctAnswer"]?.jsonPrimitive?.contentOrNull
                ?: el["answer"]?.jsonPrimitive?.contentOrNull

            if (!correctStr.isNullOrBlank()) {
                val letterMatch = when (correctStr.trim().uppercase()) {
                    "A", "1" -> 0
                    "B", "2" -> 1
                    "C", "3" -> 2
                    "D", "4" -> 3
                    else -> -1
                }
                if (letterMatch in options.indices) {
                    correctIdx = letterMatch
                } else {
                    val matchingTextIdx = options.indexOfFirst { it.equals(correctStr.trim(), ignoreCase = true) }
                    correctIdx = if (matchingTextIdx >= 0) matchingTextIdx else 0
                }
            } else {
                correctIdx = 0
            }
        }

        val explanation = el["explanation"]?.jsonPrimitive?.contentOrNull
            ?: el["rationale"]?.jsonPrimitive?.contentOrNull
            ?: ""

        val diffStr = el["difficulty"]?.jsonPrimitive?.contentOrNull?.uppercase() ?: "MEDIUM"
        val difficulty = when (diffStr) {
            "EASY" -> Difficulty.EASY
            "HARD" -> Difficulty.HARD
            else -> Difficulty.MEDIUM
        }

        return ImportedQuestion(
            question = questionText.trim(),
            options = options,
            correctIndex = correctIdx.coerceIn(0, options.size - 1),
            explanation = explanation.trim(),
            difficulty = difficulty
        )
    }

    private val JsonElement.contentOrNull: String?
        get() = try {
            jsonPrimitive.content
        } catch (_: Exception) {
            null
        }
}
