package com.quickdrop.core.domain

import com.quickdrop.core.model.AnchorKind
import com.quickdrop.core.model.SourceChunk
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

enum class FileIntakeStatus {
    VALID,
    UNSUPPORTED_MIME_OR_SIGNATURE,
    OVERSIZED_BYTES,
    EXCEEDS_PAGE_OR_SLIDE_LIMIT,
    ENCRYPTED_FILE,
    CORRUPT_FILE,
    SCANNED_OR_EMPTY_TEXT
}

data class ExtractionOutcome(
    val status: FileIntakeStatus,
    val detectedMimeType: String,
    val sha256: String,
    val pageOrSlideCount: Int,
    val chunks: List<SourceChunk>,
    val partialExtractionNotice: String? = null,
    val userActionableMessage: String? = null
)

/**
 * Validates file signatures (not extensions alone), enforces the 50 MB / 200 pages-or-slides limits,
 * hashes bytes with SHA-256 for deduplication, and extracts page/slide-anchored [SourceChunk]s
 * from PDF, PPTX, and image files.
 */
object FileExtractor {

    const val MAX_FILE_BYTES: Long = 50L * 1024L * 1024L // 50 MB
    const val MAX_PAGES_OR_SLIDES: Int = 200
    const val MAX_CHUNK_TOKENS: Int = 600

    val isAwtAvailable: Boolean by lazy {
        try {
            Class.forName("java.awt.image.BufferedImage") != null
        } catch (_: Throwable) {
            false
        }
    }

    fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun estimateTokens(text: String): Int {
        val words = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.size
        return (words * 1.3).toInt().coerceAtLeast(1)
    }

    /**
     * Inspects magic bytes to verify PDF, ZIP/PPTX, JPEG, PNG, or WebP regardless of file extension.
     */
    fun detectMimeBySignature(bytes: ByteArray, fileNameHint: String = ""): String? {
        if (bytes.size < 4) return null

        // PDF: %PDF- (0x25 0x50 0x44 0x46 0x2D)
        if (bytes.size >= 5 &&
            bytes[0] == 0x25.toByte() &&
            bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x44.toByte() &&
            bytes[3] == 0x46.toByte() &&
            bytes[4] == 0x2D.toByte()
        ) {
            return "application/pdf"
        }

        // PNG: 89 50 4E 47 0D 0A 1A 0A
        if (bytes.size >= 8 &&
            bytes[0] == 0x89.toByte() &&
            bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() &&
            bytes[3] == 0x47.toByte()
        ) {
            return "image/png"
        }

        // JPEG: FF D8 FF
        if (bytes[0] == 0xFF.toByte() &&
            bytes[1] == 0xD8.toByte() &&
            bytes[2] == 0xFF.toByte()
        ) {
            return "image/jpeg"
        }

        // WebP: RIFF....WEBP
        if (bytes.size >= 12 &&
            bytes[0] == 'R'.code.toByte() &&
            bytes[1] == 'I'.code.toByte() &&
            bytes[2] == 'F'.code.toByte() &&
            bytes[3] == 'F'.code.toByte() &&
            bytes[8] == 'W'.code.toByte() &&
            bytes[9] == 'E'.code.toByte() &&
            bytes[10] == 'B'.code.toByte() &&
            bytes[11] == 'P'.code.toByte()
        ) {
            return "image/webp"
        }

        // ZIP container (PK\x03\x04) - check if PPTX by inspecting zip entries
        if (bytes[0] == 0x50.toByte() &&
            bytes[1] == 0x4B.toByte() &&
            bytes[2] == 0x03.toByte() &&
            bytes[3] == 0x04.toByte()
        ) {
            if (isPptxArchive(bytes)) {
                return "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            }
        }

        // HTML / Web documents: check file name hint or opening tags
        val nameLower = fileNameHint.lowercase()
        if (nameLower.endsWith(".html") || nameLower.endsWith(".htm") || nameLower.endsWith(".xhtml")) {
            return "text/html"
        }
        if (nameLower.endsWith(".txt") || nameLower.endsWith(".md") || nameLower.endsWith(".csv") || nameLower.endsWith(".ics")) {
            return "text/plain"
        }

        val preview = try {
            String(bytes.take(512).toByteArray(), Charsets.UTF_8).lowercase().trimStart()
        } catch (_: Exception) {
            ""
        }
        if (preview.startsWith("<!doctype html") || preview.startsWith("<html") ||
            preview.contains("<head") || preview.contains("<body") ||
            preview.contains("<table") || preview.contains("<div") || preview.contains("<section")
        ) {
            return "text/html"
        }

        return null
    }

    private fun isPptxArchive(bytes: ByteArray): Boolean {
        return try {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
                var entry = zis.nextEntry
                var hasPresentationXml = false
                while (entry != null) {
                    if (entry.name.startsWith("ppt/") || entry.name == "[Content_Types].xml") {
                        if (entry.name.startsWith("ppt/slides/slide") || entry.name == "ppt/presentation.xml") {
                            hasPresentationXml = true
                            break
                        }
                    }
                    entry = zis.nextEntry
                }
                hasPresentationXml
            }
        } catch (_: Exception) {
            false
        }
    }

    fun extract(
        fileId: String,
        bytes: ByteArray,
        fileName: String,
        ocrFallbackText: String? = null
    ): ExtractionOutcome {
        val hash = sha256Hex(bytes)

        if (bytes.size.toLong() > MAX_FILE_BYTES) {
            return ExtractionOutcome(
                status = FileIntakeStatus.OVERSIZED_BYTES,
                detectedMimeType = "application/octet-stream",
                sha256 = hash,
                pageOrSlideCount = 0,
                chunks = emptyList(),
                userActionableMessage = "File exceeds the 50 MB size limit. Please split or compress the file and try again."
            )
        }

        val mime = detectMimeBySignature(bytes, fileName)
            ?: return ExtractionOutcome(
                status = FileIntakeStatus.UNSUPPORTED_MIME_OR_SIGNATURE,
                detectedMimeType = "application/octet-stream",
                sha256 = hash,
                pageOrSlideCount = 0,
                chunks = emptyList(),
                userActionableMessage = "Unsupported or unrecognized file format. Accepted formats: PDF, PPTX, HTML, JPEG, PNG, WebP, Text."
            )

        return try {
            when (mime) {
                "application/pdf" -> extractPdf(fileId, bytes, hash, ocrFallbackText)
                "application/vnd.openxmlformats-officedocument.presentationml.presentation" -> extractPptx(fileId, bytes, hash)
                "image/png", "image/jpeg", "image/webp" -> extractImage(fileId, mime, hash, ocrFallbackText)
                "text/html" -> extractHtml(fileId, bytes, hash)
                "text/plain" -> extractPlainText(fileId, bytes, hash)
                else -> ExtractionOutcome(
                    status = FileIntakeStatus.UNSUPPORTED_MIME_OR_SIGNATURE,
                    detectedMimeType = mime,
                    sha256 = hash,
                    pageOrSlideCount = 0,
                    chunks = emptyList(),
                    userActionableMessage = "Unsupported file format."
                )
            }
        } catch (t: Throwable) {
            ExtractionOutcome(
                status = FileIntakeStatus.CORRUPT_FILE,
                detectedMimeType = mime,
                sha256 = hash,
                pageOrSlideCount = 0,
                chunks = emptyList(),
                userActionableMessage = "Error reading $fileName: ${t.message ?: "Unreadable file structure"}"
            )
        }
    }

    private fun extractPdf(
        fileId: String,
        bytes: ByteArray,
        sha256: String,
        ocrFallbackText: String?
    ): ExtractionOutcome {
        // If AWT is present (desktop environment), try PDFBox first, catching any LinkageError or Throwable
        if (isAwtAvailable) {
            try {
                val boxOutcome = extractPdfWithPdfBox(fileId, bytes, sha256, ocrFallbackText)
                if (boxOutcome.status != FileIntakeStatus.SCANNED_OR_EMPTY_TEXT) {
                    return boxOutcome
                }
            } catch (_: Throwable) {
                // PDFBox failed or linkage error - fallback to pure stream parser
            }
        }

        // Pure Android & JVM stream extractor - zero AWT dependency, 100% crash-proof
        val streamResult = PdfStreamExtractor.extract(
            fileId = fileId,
            bytes = bytes,
            maxPages = MAX_PAGES_OR_SLIDES,
            maxChunkTokens = MAX_CHUNK_TOKENS,
            ocrFallbackText = ocrFallbackText
        )

        return ExtractionOutcome(
            status = streamResult.status,
            detectedMimeType = "application/pdf",
            sha256 = sha256,
            pageOrSlideCount = streamResult.pageCount,
            chunks = streamResult.chunks,
            userActionableMessage = streamResult.errorMessage
        )
    }

    private fun extractPdfWithPdfBox(
        fileId: String,
        bytes: ByteArray,
        sha256: String,
        ocrFallbackText: String?
    ): ExtractionOutcome {
        return try {
            PDDocument.load(bytes).use { doc ->
                if (doc.isEncrypted) {
                    return ExtractionOutcome(
                        status = FileIntakeStatus.ENCRYPTED_FILE,
                        detectedMimeType = "application/pdf",
                        sha256 = sha256,
                        pageOrSlideCount = 0,
                        chunks = emptyList(),
                        userActionableMessage = "Password-protected PDF cannot be extracted. Remove password protection and retry."
                    )
                }

                val pageCount = doc.numberOfPages
                if (pageCount > MAX_PAGES_OR_SLIDES) {
                    return ExtractionOutcome(
                        status = FileIntakeStatus.EXCEEDS_PAGE_OR_SLIDE_LIMIT,
                        detectedMimeType = "application/pdf",
                        sha256 = sha256,
                        pageOrSlideCount = pageCount,
                        chunks = emptyList(),
                        userActionableMessage = "PDF has $pageCount pages, exceeding the 200-page limit. Upload smaller chapter sections."
                    )
                }

                val stripper = PDFTextStripper()
                val chunks = mutableListOf<SourceChunk>()
                var ordinal = 1

                for (pageIndex in 1..pageCount) {
                    stripper.startPage = pageIndex
                    stripper.endPage = pageIndex
                    val rawPageText = stripper.getText(doc).trim()
                    if (rawPageText.isNotEmpty()) {
                        val subChunks = splitByTokenBudget(rawPageText, MAX_CHUNK_TOKENS)
                        for (sub in subChunks) {
                            chunks.add(
                                SourceChunk(
                                    id = UUID.randomUUID().toString(),
                                    fileId = fileId,
                                    anchorKind = AnchorKind.PAGE,
                                    anchorIndex = pageIndex,
                                    ordinal = ordinal++,
                                    extractedText = sub,
                                    tokenEstimate = estimateTokens(sub),
                                    contentHash = sha256Hex(sub.toByteArray(Charsets.UTF_8))
                                )
                            )
                        }
                    }
                }

                if (chunks.isEmpty()) {
                    if (!ocrFallbackText.isNullOrBlank()) {
                        val clean = ocrFallbackText.trim()
                        chunks.add(
                            SourceChunk(
                                id = UUID.randomUUID().toString(),
                                fileId = fileId,
                                anchorKind = AnchorKind.PAGE,
                                anchorIndex = 1,
                                ordinal = 1,
                                extractedText = clean,
                                tokenEstimate = estimateTokens(clean),
                                contentHash = sha256Hex(clean.toByteArray(Charsets.UTF_8))
                            )
                        )
                    } else {
                        return ExtractionOutcome(
                            status = FileIntakeStatus.SCANNED_OR_EMPTY_TEXT,
                            detectedMimeType = "application/pdf",
                            sha256 = sha256,
                            pageOrSlideCount = pageCount,
                            chunks = emptyList(),
                            userActionableMessage = "This PDF appears to be scanned or image-only with no extractable text layer. Enable OCR or add manual lecture notes."
                        )
                    }
                }

                ExtractionOutcome(
                    status = FileIntakeStatus.VALID,
                    detectedMimeType = "application/pdf",
                    sha256 = sha256,
                    pageOrSlideCount = pageCount,
                    chunks = chunks
                )
            }
        } catch (t: Throwable) {
            if (t.message?.contains("decrypt", ignoreCase = true) == true ||
                t.message?.contains("password", ignoreCase = true) == true
            ) {
                ExtractionOutcome(
                    status = FileIntakeStatus.ENCRYPTED_FILE,
                    detectedMimeType = "application/pdf",
                    sha256 = sha256,
                    pageOrSlideCount = 0,
                    chunks = emptyList(),
                    userActionableMessage = "Encrypted PDF cannot be read. Please upload an unencrypted copy."
                )
            } else {
                ExtractionOutcome(
                    status = FileIntakeStatus.CORRUPT_FILE,
                    detectedMimeType = "application/pdf",
                    sha256 = sha256,
                    pageOrSlideCount = 0,
                    chunks = emptyList(),
                    userActionableMessage = "Corrupt or unreadable PDF file. Please verify the file and try again."
                )
            }
        }
    }

    private fun extractPptx(
        fileId: String,
        bytes: ByteArray,
        sha256: String
    ): ExtractionOutcome {
        return try {
            val slideEntries = mutableMapOf<Int, ByteArray>()
            var hasEmbeddedImagesOrCharts = false

            ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val name = entry.name
                    val slideMatch = Regex("^ppt/slides/slide(\\d+)\\.xml$").find(name)
                    if (slideMatch != null) {
                        val slideNum = slideMatch.groupValues[1].toInt()
                        slideEntries[slideNum] = zis.readBytes()
                    } else if (name.startsWith("ppt/media/") || name.startsWith("ppt/charts/")) {
                        hasEmbeddedImagesOrCharts = true
                    }
                    entry = zis.nextEntry
                }
            }

            if (slideEntries.isEmpty()) {
                return ExtractionOutcome(
                    status = FileIntakeStatus.CORRUPT_FILE,
                    detectedMimeType = "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                    sha256 = sha256,
                    pageOrSlideCount = 0,
                    chunks = emptyList(),
                    userActionableMessage = "No slides found in PPTX presentation."
                )
            }

            val slideCount = slideEntries.keys.maxOrNull() ?: slideEntries.size
            if (slideCount > MAX_PAGES_OR_SLIDES) {
                return ExtractionOutcome(
                    status = FileIntakeStatus.EXCEEDS_PAGE_OR_SLIDE_LIMIT,
                    detectedMimeType = "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                    sha256 = sha256,
                    pageOrSlideCount = slideCount,
                    chunks = emptyList(),
                    userActionableMessage = "Presentation has $slideCount slides, exceeding the 200-slide limit."
                )
            }

            val chunks = mutableListOf<SourceChunk>()
            var ordinal = 1
            val dbf = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = false
                runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            }

            for ((slideNumber, xmlBytes) in slideEntries.toSortedMap()) {
                val doc = dbf.newDocumentBuilder().parse(ByteArrayInputStream(xmlBytes))
                val textNodes = doc.getElementsByTagName("a:t")
                val sb = StringBuilder()
                for (i in 0 until textNodes.length) {
                    val nodeText = textNodes.item(i)?.textContent?.trim()
                    if (!nodeText.isNullOrEmpty()) {
                        if (sb.isNotEmpty()) sb.append(" ")
                        sb.append(nodeText)
                    }
                }
                val slideText = sb.toString().trim()
                if (slideText.isNotEmpty()) {
                    val subChunks = splitByTokenBudget(slideText, MAX_CHUNK_TOKENS)
                    for (sub in subChunks) {
                        chunks.add(
                            SourceChunk(
                                id = UUID.randomUUID().toString(),
                                fileId = fileId,
                                anchorKind = AnchorKind.SLIDE,
                                anchorIndex = slideNumber,
                                ordinal = ordinal++,
                                extractedText = sub,
                                tokenEstimate = estimateTokens(sub),
                                contentHash = sha256Hex(sub.toByteArray(Charsets.UTF_8))
                            )
                        )
                    }
                }
            }

            val partialNotice = if (hasEmbeddedImagesOrCharts) {
                "Partial extraction: Slide text was extracted, but embedded images/charts were omitted in MVP."
            } else {
                null
            }

            if (chunks.isEmpty()) {
                return ExtractionOutcome(
                    status = FileIntakeStatus.SCANNED_OR_EMPTY_TEXT,
                    detectedMimeType = "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                    sha256 = sha256,
                    pageOrSlideCount = slideCount,
                    chunks = emptyList(),
                    partialExtractionNotice = partialNotice,
                    userActionableMessage = "Presentation slides contain images only and no extractable text boxes."
                )
            }

            ExtractionOutcome(
                status = FileIntakeStatus.VALID,
                detectedMimeType = "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                sha256 = sha256,
                pageOrSlideCount = slideCount,
                chunks = chunks,
                partialExtractionNotice = partialNotice
            )
        } catch (_: Exception) {
            ExtractionOutcome(
                status = FileIntakeStatus.CORRUPT_FILE,
                detectedMimeType = "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                sha256 = sha256,
                pageOrSlideCount = 0,
                chunks = emptyList(),
                userActionableMessage = "Corrupt or unreadable PPTX file."
            )
        }
    }

    private fun extractImage(
        fileId: String,
        mimeType: String,
        sha256: String,
        ocrFallbackText: String?
    ): ExtractionOutcome {
        if (ocrFallbackText.isNullOrBlank()) {
            return ExtractionOutcome(
                status = FileIntakeStatus.SCANNED_OR_EMPTY_TEXT,
                detectedMimeType = mimeType,
                sha256 = sha256,
                pageOrSlideCount = 1,
                chunks = emptyList(),
                userActionableMessage = "Image stored offline. Add on-device OCR text, grant explicit cloud image consent, or attach manual notes."
            )
        }
        val clean = ocrFallbackText.trim()
        val chunk = SourceChunk(
            id = UUID.randomUUID().toString(),
            fileId = fileId,
            anchorKind = AnchorKind.IMAGE,
            anchorIndex = 1,
            ordinal = 1,
            extractedText = clean,
            tokenEstimate = estimateTokens(clean),
            contentHash = sha256Hex(clean.toByteArray(Charsets.UTF_8))
        )
        return ExtractionOutcome(
            status = FileIntakeStatus.VALID,
            detectedMimeType = mimeType,
            sha256 = sha256,
            pageOrSlideCount = 1,
            chunks = listOf(chunk)
        )
    }

    fun splitByTokenBudget(text: String, maxTokens: Int): List<String> {
        if (estimateTokens(text) <= maxTokens) return listOf(text)
        val paragraphs = text.split(Regex("\\n\\s*\\n|(?<=\\.)\\s+"))
        val results = mutableListOf<String>()
        val current = StringBuilder()

        for (p in paragraphs) {
            val trimmed = p.trim()
            if (trimmed.isEmpty()) continue
            val candidate = if (current.isEmpty()) trimmed else "$current $trimmed"
            if (estimateTokens(candidate) <= maxTokens) {
                if (current.isNotEmpty()) current.append(" ")
                current.append(trimmed)
            } else {
                if (current.isNotEmpty()) {
                    results.add(current.toString())
                    current.clear()
                }
                current.append(trimmed)
            }
        }
        if (current.isNotEmpty()) {
            results.add(current.toString())
        }
        return results
    }

    private fun extractHtml(
        fileId: String,
        bytes: ByteArray,
        sha256: String
    ): ExtractionOutcome {
        val rawHtml = try {
            String(bytes, Charsets.UTF_8)
        } catch (_: Exception) {
            return ExtractionOutcome(
                status = FileIntakeStatus.CORRUPT_FILE,
                detectedMimeType = "text/html",
                sha256 = sha256,
                pageOrSlideCount = 0,
                chunks = emptyList(),
                userActionableMessage = "Could not decode HTML document as UTF-8."
            )
        }

        // Clean out script and style tags and comments
        val noScript = rawHtml.replace(Regex("<script[^>]*>.*?</script>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), " ")
        val noStyle = noScript.replace(Regex("<style[^>]*>.*?</style>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), " ")
        val noComments = noStyle.replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), " ")

        // Check if partitioned by slides/sections
        val sectionRegex = Regex("<(?:section|article|div[^>]*class=[\"'][^\"']*(?:slide|page|step)[^\"']*[\"'])[^>]*>(.*?)</(?:section|article|div)>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        val sectionMatches = sectionRegex.findAll(noComments).map { it.groupValues[1] }.toList()

        val rawParts = if (sectionMatches.size >= 2) {
            sectionMatches
        } else {
            // Split by h1, h2, hr or table or paragraphs
            val splitByHeaders = noComments.split(Regex("(?=<h[1-3][^>]*>|<hr[^>]*>)", RegexOption.IGNORE_CASE))
            if (splitByHeaders.size >= 2) {
                splitByHeaders
            } else {
                listOf(noComments)
            }
        }

        val chunks = mutableListOf<SourceChunk>()
        var ordinal = 1

        for (part in rawParts) {
            val stripped = stripHtmlTags(part).trim()
            if (stripped.length >= 20) {
                val subChunks = chunkText(stripped, maxWords = 350)
                for (sub in subChunks) {
                    chunks.add(
                        SourceChunk(
                            id = UUID.randomUUID().toString(),
                            fileId = fileId,
                            anchorKind = AnchorKind.PAGE,
                            anchorIndex = ordinal,
                            ordinal = ordinal,
                            extractedText = sub,
                            tokenEstimate = estimateTokens(sub),
                            contentHash = sha256Hex(sub.toByteArray(Charsets.UTF_8))
                        )
                    )
                    ordinal++
                }
            }
        }

        if (chunks.isEmpty()) {
            return ExtractionOutcome(
                status = FileIntakeStatus.SCANNED_OR_EMPTY_TEXT,
                detectedMimeType = "text/html",
                sha256 = sha256,
                pageOrSlideCount = 0,
                chunks = emptyList(),
                userActionableMessage = "HTML document contained no readable text."
            )
        }

        return ExtractionOutcome(
            status = FileIntakeStatus.VALID,
            detectedMimeType = "text/html",
            sha256 = sha256,
            pageOrSlideCount = chunks.size,
            chunks = chunks,
            partialExtractionNotice = null
        )
    }

    private fun extractPlainText(
        fileId: String,
        bytes: ByteArray,
        sha256: String
    ): ExtractionOutcome {
        val text = try {
            String(bytes, Charsets.UTF_8).trim()
        } catch (_: Exception) {
            return ExtractionOutcome(
                status = FileIntakeStatus.CORRUPT_FILE,
                detectedMimeType = "text/plain",
                sha256 = sha256,
                pageOrSlideCount = 0,
                chunks = emptyList(),
                userActionableMessage = "Could not decode text file as UTF-8."
            )
        }

        if (text.isEmpty()) {
            return ExtractionOutcome(
                status = FileIntakeStatus.SCANNED_OR_EMPTY_TEXT,
                detectedMimeType = "text/plain",
                sha256 = sha256,
                pageOrSlideCount = 0,
                chunks = emptyList(),
                userActionableMessage = "Text document is empty."
            )
        }

        val parts = text.split(Regex("\\n\\s*\\n+")).filter { it.trim().length >= 20 }
        val effectiveParts = if (parts.isEmpty()) listOf(text) else parts
        val chunks = mutableListOf<SourceChunk>()
        var ordinal = 1

        for (part in effectiveParts) {
            val subChunks = chunkText(part.trim(), maxWords = 350)
            for (sub in subChunks) {
                chunks.add(
                    SourceChunk(
                        id = UUID.randomUUID().toString(),
                        fileId = fileId,
                        anchorKind = AnchorKind.PAGE,
                        anchorIndex = ordinal,
                        ordinal = ordinal,
                        extractedText = sub,
                        tokenEstimate = estimateTokens(sub),
                        contentHash = sha256Hex(sub.toByteArray(Charsets.UTF_8))
                    )
                )
                ordinal++
            }
        }

        return ExtractionOutcome(
            status = FileIntakeStatus.VALID,
            detectedMimeType = "text/plain",
            sha256 = sha256,
            pageOrSlideCount = chunks.size,
            chunks = chunks,
            partialExtractionNotice = null
        )
    }

    private fun stripHtmlTags(html: String): String {
        return html
            .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("</(?:p|div|tr|h[1-6]|li)>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("<[^>]+>"), " ")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace(Regex("[ \\t]+"), " ")
            .replace(Regex("\\n\\s*\\n"), "\n\n")
            .trim()
    }

    private fun chunkText(text: String, maxWords: Int): List<String> {
        val words = text.split(Regex("\\s+"))
        if (words.size <= maxWords) return listOf(text)
        val result = mutableListOf<String>()
        var i = 0
        while (i < words.size) {
            val end = (i + maxWords).coerceAtMost(words.size)
            result.add(words.subList(i, end).joinToString(" "))
            i = end
        }
        return result
    }
}
