package com.quickdrop.core.domain

import com.quickdrop.core.model.AnchorKind
import com.quickdrop.core.model.ConfidenceLabel
import com.quickdrop.core.model.Difficulty
import com.quickdrop.core.model.LearningItemKind
import com.quickdrop.core.model.SourceChunk
import kotlinx.serialization.Serializable

@Serializable
data class DraftOptionPayload(
    val id: String,
    val text: String,
    val isCorrect: Boolean
)

@Serializable
data class DraftSourceAnchorPayload(
    val fileId: String,
    val anchorKind: AnchorKind,
    val anchorIndex: Int
)

@Serializable
data class GeneratedDraftPayload(
    val kind: LearningItemKind,
    val topicName: String,
    val difficulty: Difficulty,
    val promptOrBody: String,
    val options: List<DraftOptionPayload> = emptyList(),
    val explanation: String = "",
    val evidenceQuote: String,
    val source: DraftSourceAnchorPayload,
    val confidenceLabel: ConfidenceLabel = ConfidenceLabel.HIGH
)

@Serializable
data class GenerationEnvelope(
    val abstained: Boolean = false,
    val abstentionReason: String? = null,
    val items: List<GeneratedDraftPayload> = emptyList()
)

data class DraftValidationResult(
    val isValid: Boolean,
    val failureReasons: List<String>,
    val matchedChunk: SourceChunk? = null
)

/**
 * Validates AI-generated and student-edited study content before it can enter REVIEW_READY or APPROVED:
 * - MCQ: >= 2 options, exactly 1 correct option, non-blank explanation, prompt/explanation length bounds
 * - DROP: 0 options, concise body within bounds
 * - Existing source anchor in supplied [SourceChunk]s
 * - Evidence quote near-match in cited chunk (prevents hallucinated citations or prompt-injected off-source facts)
 * - No near-duplicate content hash against existing approved items
 */
object DraftValidator {

    private const val MAX_PROMPT_LENGTH = 600
    private const val MAX_EXPLANATION_LENGTH = 800
    private const val MAX_OPTION_LENGTH = 240

    fun canonicalTopicKey(rawName: String): String {
        return rawName.trim()
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .ifEmpty { "general" }
    }

    fun normalizeForMatch(text: String): String {
        return text.lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun computeContentHash(kind: LearningItemKind, promptOrBody: String, options: List<String>): String {
        val canonical = buildString {
            append(kind.name)
            append("|")
            append(normalizeForMatch(promptOrBody))
            for (opt in options.map { normalizeForMatch(it) }.sorted()) {
                append("|")
                append(opt)
            }
        }
        return FileExtractor.sha256Hex(canonical.toByteArray(Charsets.UTF_8))
    }

    fun validateDraft(
        draft: GeneratedDraftPayload,
        availableChunks: List<SourceChunk>,
        existingApprovedHashes: Set<String>
    ): DraftValidationResult {
        val reasons = mutableListOf<String>()

        if (draft.topicName.isBlank()) {
            reasons.add("Topic name must not be blank.")
        }

        val prompt = draft.promptOrBody.trim()
        if (prompt.length !in 8..MAX_PROMPT_LENGTH) {
            reasons.add("Prompt/body length (${prompt.length}) must be between 8 and $MAX_PROMPT_LENGTH characters.")
        }

        // Check source anchor existence
        val matchingChunks = availableChunks.filter {
            it.fileId == draft.source.fileId &&
                it.anchorKind == draft.source.anchorKind &&
                it.anchorIndex == draft.source.anchorIndex
        }
        if (matchingChunks.isEmpty()) {
            reasons.add("Cited source anchor (${draft.source.anchorKind} ${draft.source.anchorIndex}) does not exist in source chunks.")
        }

        // Check evidence quote near-match inside cited chunk
        val quoteNorm = normalizeForMatch(draft.evidenceQuote)
        var matchedChunk: SourceChunk? = null
        if (quoteNorm.length < 6) {
            reasons.add("Evidence quote is too short or missing.")
        } else if (matchingChunks.isNotEmpty()) {
            matchedChunk = matchingChunks.firstOrNull { chunk ->
                val chunkNorm = normalizeForMatch(chunk.extractedText)
                chunkNorm.contains(quoteNorm) || tokenOverlapRatio(quoteNorm, chunkNorm) >= 0.75
            }
            if (matchedChunk == null) {
                reasons.add("Evidence quote does not match text in cited ${draft.source.anchorKind} ${draft.source.anchorIndex}.")
            }
        }

        when (draft.kind) {
            LearningItemKind.MCQ -> {
                if (draft.options.size < 2) {
                    reasons.add("MCQ requires at least 2 answer options.")
                }
                val correctOptions = draft.options.filter { it.isCorrect }
                if (correctOptions.size != 1) {
                    reasons.add("MCQ requires活跃 exactly 1 correct option, found ${correctOptions.size}.")
                }
                if (draft.options.any { it.text.trim().isEmpty() || it.text.length > MAX_OPTION_LENGTH }) {
                    reasons.add("Each MCQ option must be non-empty and at most $MAX_OPTION_LENGTH characters.")
                }
                val distinctOptionTexts = draft.options.map { normalizeForMatch(it.text) }.distinct()
                if (distinctOptionTexts.size != draft.options.size) {
                    reasons.add("MCQ options contain duplicate choices.")
                }
                if (draft.explanation.trim().length !in 8..MAX_EXPLANATION_LENGTH) {
                    reasons.add("MCQ explanation must be between 8 and $MAX_EXPLANATION_LENGTH characters.")
                }
            }

            LearningItemKind.DROP -> {
                if (draft.options.isNotEmpty()) {
                    reasons.add("DROP item must not have answer options.")
                }
            }
        }

        val candidateHash = computeContentHash(
            kind = draft.kind,
            promptOrBody = draft.promptOrBody,
            options = draft.options.map { it.text }
        )
        if (candidateHash in existingApprovedHashes) {
            reasons.add("Near-duplicate of an already approved learning item.")
        }

        return DraftValidationResult(
            isValid = reasons.isEmpty(),
            failureReasons = reasons,
            matchedChunk = matchedChunk
        )
    }

    private fun tokenOverlapRatio(quoteNorm: String, chunkNorm: String): Double {
        val quoteTokens = quoteNorm.split(" ").filter { it.length > 2 }.toSet()
        if (quoteTokens.isEmpty()) return 0.0
        val chunkTokens = chunkNorm.split(" ").toSet()
        val matched = quoteTokens.count { it in chunkTokens }
        return matched.toDouble() / quoteTokens.size.toDouble()
    }
}
