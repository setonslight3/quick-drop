package com.quickdrop.app.integration.gemini

import com.quickdrop.core.domain.GeminiClient
import com.quickdrop.core.domain.GeminiPipeline
import com.quickdrop.core.domain.GeminiResponse
import com.quickdrop.core.domain.GenerationEnvelope
import com.quickdrop.core.model.AiErrorCode
import com.quickdrop.core.model.SourceChunk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import kotlin.random.Random

/**
 * Direct device-to-Google HTTPS client for the Gemini GenerateContent API (`v1beta/models/{modelId}:generateContent`).
 * - Never logs the API key, prompts, source chunks, or response bodies.
 * - Distinguishes 400/401/403 (INVALID_KEY), 404 (UNSUPPORTED_MODEL), 429 (QUOTA_EXCEEDED with Retry-After),
 *   500..599 (PROVIDER_OUTAGE), and network/timeout errors (OFFLINE_OR_TIMEOUT).
 * - Applies bounded exponential backoff with jitter only for transient 5xx/timeout errors; immediately halts on 429.
 */
class DirectHttpsGeminiClient : GeminiClient {

    @Serializable
    private data class Part(val text: String)

    @Serializable
    private data class Content(val parts: List<Part>)

    @Serializable
    private data class GenerationConfig(
        val responseMimeType: String = "application/json",
        val temperature: Double = 0.2
    )

    @Serializable
    private data class GenerateContentRequest(
        val contents: List<Content>,
        val generationConfig: GenerationConfig = GenerationConfig()
    )

    @Serializable
    private data class CandidateContent(val parts: List<Part> = emptyList())

    @Serializable
    private data class Candidate(val content: CandidateContent? = null)

    @Serializable
    private data class GenerateContentResponse(val candidates: List<Candidate> = emptyList())

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override suspend fun testConnection(apiKey: String, modelId: String): GeminiResponse {
        if (apiKey.isBlank()) {
            return GeminiResponse.Failure(
                errorCode = AiErrorCode.NO_KEY,
                sanitizedMessage = "Please enter a Gemini API key first.",
                retryable = false
            )
        }
        val testPayload = GenerateContentRequest(
            contents = listOf(
                Content(parts = listOf(Part("Respond with JSON: {\"abstained\":true,\"items\":[]}")))
            )
        )
        return executeWithBoundedRetry(apiKey.trim(), modelId.trim(), testPayload, maxAttempts = 1)
    }

    override suspend fun generateStudyDrafts(
        apiKey: String,
        modelId: String,
        subjectName: String,
        lectureTitle: String,
        chunks: List<SourceChunk>,
        validationFeedbackFromPreviousAttempt: List<String>
    ): GeminiResponse {
        val prompt = GeminiPipeline.buildSystemAndUserPrompt(
            subjectName = subjectName,
            lectureTitle = lectureTitle,
            chunks = chunks,
            validationFeedback = validationFeedbackFromPreviousAttempt
        )
        val payload = GenerateContentRequest(
            contents = listOf(Content(parts = listOf(Part(prompt))))
        )
        return executeWithBoundedRetry(apiKey.trim(), modelId.trim(), payload, maxAttempts = 2)
    }

    private suspend fun executeWithBoundedRetry(
        apiKey: String,
        modelId: String,
        requestPayload: GenerateContentRequest,
        maxAttempts: Int
    ): GeminiResponse = withContext(Dispatchers.IO) {
        var attempt = 0
        var lastFailure: GeminiResponse.Failure? = null

        while (attempt < maxAttempts) {
            attempt++
            val result = performSingleHttpCall(apiKey, modelId, requestPayload)
            when (result) {
                is GeminiResponse.Success -> return@withContext result
                is GeminiResponse.Failure -> {
                    lastFailure = result
                    // Stop immediately on 429 quota, invalid key, or unsupported model
                    if (!result.retryable || result.errorCode == AiErrorCode.QUOTA_EXCEEDED) {
                        return@withContext result
                    }
                    if (attempt < maxAttempts) {
                        val backoffMs = (500L * (1 shl (attempt - 1))) + Random.nextLong(50L, 250L)
                        delay(backoffMs)
                    }
                }
            }
        }
        lastFailure ?: GeminiResponse.Failure(
            errorCode = AiErrorCode.PROVIDER_OUTAGE,
            sanitizedMessage = "Request failed after retry.",
            retryable = true
        )
    }

    private fun performSingleHttpCall(
        apiKey: String,
        modelId: String,
        requestPayload: GenerateContentRequest
    ): GeminiResponse {
        val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$modelId:generateContent"
        var connection: HttpURLConnection? = null
        return try {
            val url = URL(endpoint)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 12_000
                readTimeout = 25_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("x-goog-api-key", apiKey)
            }

            val bodyBytes = json.encodeToString(requestPayload).toByteArray(Charsets.UTF_8)
            connection.outputStream.use { os -> os.write(bodyBytes) }

            val status = connection.responseCode
            when {
                status in 200..299 -> {
                    val responseBody = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    val parsed = json.decodeFromString(GenerateContentResponse.serializer(), responseBody)
                    val modelText = parsed.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text.orEmpty()
                    val envelope = GeminiPipeline.parseEnvelope(modelText).getOrElse {
                        return GeminiResponse.Failure(
                            errorCode = AiErrorCode.MALFORMED_RESPONSE,
                            sanitizedMessage = "Provider returned malformed JSON schema.",
                            retryable = true
                        )
                    }
                    GeminiResponse.Success(
                        envelope = envelope,
                        modelId = modelId,
                        promptVersion = GeminiPipeline.PROMPT_VERSION
                    )
                }

                status == 400 || status == 401 || status == 403 -> GeminiResponse.Failure(
                    errorCode = AiErrorCode.INVALID_KEY,
                    sanitizedMessage = "Authentication failed ($status). Check your Gemini API key in Settings.",
                    retryable = false
                )

                status == 404 -> GeminiResponse.Failure(
                    errorCode = AiErrorCode.UNSUPPORTED_MODEL,
                    sanitizedMessage = "Model '$modelId' is not supported or not found (404).",
                    retryable = false
                )

                status == 429 -> {
                    val retryHeader = connection.getHeaderField("Retry-After")?.toIntOrNull()
                    GeminiResponse.Failure(
                        errorCode = AiErrorCode.QUOTA_EXCEEDED,
                        sanitizedMessage = if (retryHeader != null) {
                            "Quota or rate limit reached (429). Retry after ${retryHeader}s."
                        } else {
                            "Quota or rate limit reached (429). Try again later or study manually."
                        },
                        retryable = false,
                        retryAfterSeconds = retryHeader
                    )
                }

                status in 500..599 -> GeminiResponse.Failure(
                    errorCode = AiErrorCode.PROVIDER_OUTAGE,
                    sanitizedMessage = "Gemini provider service error ($status). Existing approved content works offline.",
                    retryable = true
                )

                else -> GeminiResponse.Failure(
                    errorCode = AiErrorCode.PROVIDER_OUTAGE,
                    sanitizedMessage = "Unexpected HTTP status ($status) from provider.",
                    retryable = false
                )
            }
        } catch (_: UnknownHostException) {
            GeminiResponse.Failure(
                errorCode = AiErrorCode.OFFLINE_OR_TIMEOUT,
                sanitizedMessage = "Device is offline. Source is saved locally; retry when connected.",
                retryable = true
            )
        } catch (_: SocketTimeoutException) {
            GeminiResponse.Failure(
                errorCode = AiErrorCode.OFFLINE_OR_TIMEOUT,
                sanitizedMessage = "Request timed out. You can continue studying approved items offline.",
                retryable = true
            )
        } catch (_: Exception) {
            GeminiResponse.Failure(
                errorCode = AiErrorCode.OFFLINE_OR_TIMEOUT,
                sanitizedMessage = "Network error while contacting Gemini API.",
                retryable = true
            )
        } finally {
            connection?.disconnect()
        }
    }
}
