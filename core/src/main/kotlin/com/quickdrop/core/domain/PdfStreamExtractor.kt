package com.quickdrop.core.domain

import com.quickdrop.core.model.AnchorKind
import com.quickdrop.core.model.SourceChunk
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream

/**
 * Pure Kotlin/Java PDF Stream Text Extractor.
 *
 * Runs 100% reliably on Android (Dalvik/ART) and desktop JVM without any
 * dependency on java.awt.* or desktop graphics classes.
 *
 * Decompresses /FlateDecode streams, resolves /Type /Page references,
 * parses PDF text operators (BT..ET, Tj, TJ, ', "), unescapes PDF strings,
 * and extracts anchored [SourceChunk]s per page.
 */
object PdfStreamExtractor {

    data class PdfExtractResult(
        val status: FileIntakeStatus,
        val pageCount: Int,
        val chunks: List<SourceChunk>,
        val errorMessage: String? = null
    )

    fun extract(
        fileId: String,
        bytes: ByteArray,
        maxPages: Int = 200,
        maxChunkTokens: Int = 600,
        ocrFallbackText: String? = null
    ): PdfExtractResult {
        return try {
            doExtract(fileId, bytes, maxPages, maxChunkTokens, ocrFallbackText)
        } catch (t: Throwable) {
            PdfExtractResult(
                status = FileIntakeStatus.CORRUPT_FILE,
                pageCount = 0,
                chunks = emptyList(),
                errorMessage = "Corrupt or unreadable PDF: ${t.message ?: "Parsing error"}"
            )
        }
    }

    private fun doExtract(
        fileId: String,
        bytes: ByteArray,
        maxPages: Int,
        maxChunkTokens: Int,
        ocrFallbackText: String?
    ): PdfExtractResult {
        val rawLatin = String(bytes, StandardCharsets.ISO_8859_1)

        // 0. Verify basic PDF structure
        val hasPdfStructure = rawLatin.contains("obj") || rawLatin.contains("xref") || rawLatin.contains("trailer")
        if (!hasPdfStructure) {
            return PdfExtractResult(
                status = FileIntakeStatus.CORRUPT_FILE,
                pageCount = 0,
                chunks = emptyList(),
                errorMessage = "Corrupt or invalid PDF file structure."
            )
        }

        // 1. Check encryption
        val isEncrypted = Regex("""/Encrypt\s*(?:<<|\d+\s+\d+\s+R)""").containsMatchIn(rawLatin)
        if (isEncrypted) {
            return PdfExtractResult(
                status = FileIntakeStatus.ENCRYPTED_FILE,
                pageCount = 0,
                chunks = emptyList(),
                errorMessage = "Password-protected PDF cannot be extracted. Remove password protection and retry."
            )
        }

        // 2. Locate Page Count
        val pageObjectsRegex = Regex("""/Type\s*/Page\b(?!s)""")
        val pageCountFromPages = pageObjectsRegex.findAll(rawLatin).count()
        val countRegex = Regex("""/Type\s*/Pages\b[^>]*?/Count\s+(\d+)""")
        val countMatch = countRegex.find(rawLatin)
        val declaredCount = countMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0

        val estimatedPages = when {
            declaredCount > 0 -> declaredCount
            pageCountFromPages > 0 -> pageCountFromPages
            else -> 1
        }

        if (estimatedPages > maxPages) {
            return PdfExtractResult(
                status = FileIntakeStatus.EXCEEDS_PAGE_OR_SLIDE_LIMIT,
                pageCount = estimatedPages,
                chunks = emptyList(),
                errorMessage = "PDF has $estimatedPages pages, exceeding the $maxPages-page limit. Upload smaller chapter sections."
            )
        }

        // 3. Extract text page-by-page by resolving /Contents
        val pageTexts = extractPageTexts(bytes, rawLatin)

        val chunks = mutableListOf<SourceChunk>()
        var ordinal = 1

        if (pageTexts.isNotEmpty()) {
            for ((pageIdx, text) in pageTexts) {
                val clean = text.trim()
                if (clean.isNotEmpty()) {
                    val subChunks = FileExtractor.splitByTokenBudget(clean, maxChunkTokens)
                    for (sub in subChunks) {
                        chunks.add(
                            SourceChunk(
                                id = UUID.randomUUID().toString(),
                                fileId = fileId,
                                anchorKind = AnchorKind.PAGE,
                                anchorIndex = pageIdx,
                                ordinal = ordinal++,
                                extractedText = sub,
                                tokenEstimate = FileExtractor.estimateTokens(sub),
                                contentHash = FileExtractor.sha256Hex(sub.toByteArray(Charsets.UTF_8))
                            )
                        )
                    }
                }
            }
        }

        // 4. Fallback if page-object resolution found nothing: decompress all streams
        if (chunks.isEmpty()) {
            val allDecompressedStreams = extractAllStreams(bytes)
            val combinedText = StringBuilder()
            for (st in allDecompressedStreams) {
                val text = parseContentStreamText(st)
                if (text.isNotBlank()) {
                    if (combinedText.isNotEmpty()) combinedText.append("\n\n")
                    combinedText.append(text)
                }
            }

            val finalCombined = combinedText.toString().trim()
            if (finalCombined.isNotEmpty()) {
                val subChunks = FileExtractor.splitByTokenBudget(finalCombined, maxChunkTokens)
                for (sub in subChunks) {
                    chunks.add(
                        SourceChunk(
                            id = UUID.randomUUID().toString(),
                            fileId = fileId,
                            anchorKind = AnchorKind.PAGE,
                            anchorIndex = 1,
                            ordinal = ordinal++,
                            extractedText = sub,
                            tokenEstimate = FileExtractor.estimateTokens(sub),
                            contentHash = FileExtractor.sha256Hex(sub.toByteArray(Charsets.UTF_8))
                        )
                    )
                }
            }
        }

        // 5. Fallback for scanned/image-only PDFs
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
                        tokenEstimate = FileExtractor.estimateTokens(clean),
                        contentHash = FileExtractor.sha256Hex(clean.toByteArray(Charsets.UTF_8))
                    )
                )
            } else {
                return PdfExtractResult(
                    status = FileIntakeStatus.SCANNED_OR_EMPTY_TEXT,
                    pageCount = estimatedPages,
                    chunks = emptyList(),
                    errorMessage = "This PDF appears to be scanned or image-only with no extractable text layer. Enable OCR or add manual lecture notes."
                )
            }
        }

        return PdfExtractResult(
            status = FileIntakeStatus.VALID,
            pageCount = estimatedPages.coerceAtLeast(chunks.maxOfOrNull { it.anchorIndex } ?: 1),
            chunks = chunks
        )
    }

    /**
     * Extracts text for each page by mapping /Type /Page objects to their /Contents stream objects.
     */
    private fun extractPageTexts(bytes: ByteArray, rawLatin: String): List<Pair<Int, String>> {
        val result = mutableListOf<Pair<Int, String>>()

        // Parse indirect objects: map of "$objNum $genNum" -> object body string and byte range
        val objRegex = Regex("""(\d+)\s+(\d+)\s+obj\b([\s\S]*?)endobj""")
        val objMatches = objRegex.findAll(rawLatin).toList()
        if (objMatches.isEmpty()) return result

        val objectBodies = mutableMapOf<Int, String>()
        val pageObjects = mutableListOf<String>()

        for (m in objMatches) {
            val num = m.groupValues[1].toIntOrNull() ?: continue
            val body = m.groupValues[3]
            objectBodies[num] = body

            if (Regex("""/Type\s*/Page\b(?!s)""").containsMatchIn(body)) {
                pageObjects.add(body)
            }
        }

        var pageIndex = 1
        for (pageBody in pageObjects) {
            val contentsRegexSingle = Regex("""/Contents\s+(\d+)\s+\d+\s+R""")
            val contentsRegexArray = Regex("""/Contents\s*\[\s*([^\]]+)\]""")

            val contentIds = mutableListOf<Int>()
            val singleMatch = contentsRegexSingle.find(pageBody)
            if (singleMatch != null) {
                singleMatch.groupValues[1].toIntOrNull()?.let { contentIds.add(it) }
            } else {
                val arrayMatch = contentsRegexArray.find(pageBody)
                if (arrayMatch != null) {
                    val tokens = arrayMatch.groupValues[1].split(Regex("""\s+"""))
                    var i = 0
                    while (i < tokens.size) {
                        val num = tokens[i].toIntOrNull()
                        if (num != null && i + 2 < tokens.size && tokens[i + 2] == "R") {
                            contentIds.add(num)
                            i += 3
                        } else {
                            i++
                        }
                    }
                }
            }

            val pageSb = StringBuilder()
            for (cid in contentIds) {
                val streamBytes = extractObjectStreamBytes(bytes, rawLatin, cid)
                if (streamBytes != null) {
                    val text = parseContentStreamText(streamBytes)
                    if (text.isNotBlank()) {
                        if (pageSb.isNotEmpty()) pageSb.append("\n")
                        pageSb.append(text)
                    }
                }
            }

            val text = pageSb.toString().trim()
            if (text.isNotEmpty()) {
                result.add(pageIndex to text)
            }
            pageIndex++
        }

        return result
    }

    private fun extractObjectStreamBytes(bytes: ByteArray, rawLatin: String, objId: Int): ByteArray? {
        val target = "$objId 0 obj"
        val idx = rawLatin.indexOf(target)
        if (idx < 0) return null

        val endObjIdx = rawLatin.indexOf("endobj", idx)
        if (endObjIdx < 0) return null

        val headerSub = rawLatin.substring(idx, (idx + 1024).coerceAtMost(rawLatin.length))
        val isFlate = headerSub.contains("/FlateDecode")

        val streamKeyword = "stream"
        var streamStart = rawLatin.indexOf(streamKeyword, idx)
        if (streamStart < 0 || streamStart > endObjIdx) return null
        streamStart += streamKeyword.length

        // Skip newline after 'stream' (\r\n or \n)
        if (streamStart < bytes.size && bytes[streamStart] == '\r'.code.toByte()) streamStart++
        if (streamStart < bytes.size && bytes[streamStart] == '\n'.code.toByte()) streamStart++

        val endStreamKeyword = "endstream"
        val streamEnd = rawLatin.indexOf(endStreamKeyword, streamStart)
        if (streamEnd < 0 || streamEnd > endObjIdx) return null

        // Trim trailing newline before 'endstream'
        var actualEnd = streamEnd
        if (actualEnd > streamStart && bytes[actualEnd - 1] == '\n'.code.toByte()) actualEnd--
        if (actualEnd > streamStart && bytes[actualEnd - 1] == '\r'.code.toByte()) actualEnd--

        if (actualEnd <= streamStart) return null

        val rawStreamBytes = bytes.copyOfRange(streamStart, actualEnd)
        return if (isFlate) {
            decompressFlate(rawStreamBytes) ?: rawStreamBytes
        } else {
            rawStreamBytes
        }
    }

    private fun extractAllStreams(bytes: ByteArray): List<ByteArray> {
        val list = mutableListOf<ByteArray>()
        val streamMarker = "stream".toByteArray(StandardCharsets.ISO_8859_1)
        val endStreamMarker = "endstream".toByteArray(StandardCharsets.ISO_8859_1)

        var pos = 0
        while (pos < bytes.size - 10) {
            val sIdx = indexOfBytes(bytes, streamMarker, pos)
            if (sIdx < 0) break

            var start = sIdx + streamMarker.size
            if (start < bytes.size && bytes[start] == '\r'.code.toByte()) start++
            if (start < bytes.size && bytes[start] == '\n'.code.toByte()) start++

            val eIdx = indexOfBytes(bytes, endStreamMarker, start)
            if (eIdx < 0) break

            var end = eIdx
            if (end > start && bytes[end - 1] == '\n'.code.toByte()) end--
            if (end > start && bytes[end - 1] == '\r'.code.toByte()) end--

            if (end > start) {
                val streamData = bytes.copyOfRange(start, end)
                val decomp = decompressFlate(streamData)
                list.add(decomp ?: streamData)
            }
            pos = eIdx + endStreamMarker.size
        }
        return list
    }

    private fun indexOfBytes(source: ByteArray, target: ByteArray, fromIndex: Int): Int {
        if (fromIndex >= source.size || target.isEmpty()) return -1
        outer@ for (i in fromIndex..(source.size - target.size)) {
            for (j in target.indices) {
                if (source[i + j] != target[j]) continue@outer
            }
            return i
        }
        return -1
    }

    private fun decompressFlate(data: ByteArray): ByteArray? {
        // Try standard zlib first
        try {
            InflaterInputStream(ByteArrayInputStream(data)).use { iis ->
                return iis.readBytes()
            }
        } catch (_: Throwable) {
            // Fallback: raw deflate (nowrap = true)
            try {
                val inflater = Inflater(true)
                inflater.setInput(data)
                val buffer = ByteArray(4096)
                val baos = ByteArrayOutputStream()
                while (!inflater.finished()) {
                    val count = inflater.inflate(buffer)
                    if (count == 0) break
                    baos.write(buffer, 0, count)
                }
                inflater.end()
                val res = baos.toByteArray()
                if (res.isNotEmpty()) return res
            } catch (_: Throwable) {
            }
        }
        return null
    }

    /**
     * Parses PDF operators inside a decompressed content stream:
     * - (text) Tj
     * - [(t1) 20 (t2)] TJ
     * - (text) '
     * - <hex> Tj
     */
    fun parseContentStreamText(contentBytes: ByteArray): String {
        val streamText = String(contentBytes, StandardCharsets.ISO_8859_1)
        val sb = StringBuilder()

        // 1. Array strings: [(str) 20 (str2)] TJ
        val tjRegex = Regex("""\[(.*?)\]\s*TJ""")
        // 2. Single string: (str) Tj or (str) '
        val singleTjRegex = Regex("""\(((?:[^\\)]|\\.)*)\)\s*(?:Tj|['"])""")
        // 3. Hex string: <hex> Tj
        val hexTjRegex = Regex("""<([0-9a-fA-F]+)>\s*(?:Tj|['"])""")

        var lastEnd = 0
        val matchTj = tjRegex.findAll(streamText).toList()

        for (m in matchTj) {
            val inside = m.groupValues[1]
            val parsedArrayText = parseTjArray(inside)
            if (parsedArrayText.isNotBlank()) {
                if (sb.isNotEmpty()) sb.append(" ")
                sb.append(parsedArrayText)
            }
        }

        // If no TJ array matches, check single Tj and hex
        if (sb.isEmpty()) {
            for (m in singleTjRegex.findAll(streamText)) {
                val literal = unescapePdfString(m.groupValues[1])
                if (literal.isNotBlank()) {
                    if (sb.isNotEmpty()) sb.append(" ")
                    sb.append(literal)
                }
            }

            for (m in hexTjRegex.findAll(streamText)) {
                val hexStr = decodePdfHexString(m.groupValues[1])
                if (hexStr.isNotBlank()) {
                    if (sb.isNotEmpty()) sb.append(" ")
                    sb.append(hexStr)
                }
            }
        }

        // Clean up common PDF formatting artifacts
        return sb.toString()
            .replace(Regex("""[ \t]+"""), " ")
            .replace(Regex("""\n{3,}"""), "\n\n")
            .trim()
    }

    private fun parseTjArray(arrayContent: String): String {
        val sb = StringBuilder()
        val itemRegex = Regex("""\(((?:[^\\)]|\\.)*)\)|<([0-9a-fA-F]+)>|(-?\d+(?:\.\d+)?)""")

        for (m in itemRegex.findAll(arrayContent)) {
            val literal = m.groups[1]?.value
            val hex = m.groups[2]?.value
            val offset = m.groups[3]?.value

            if (literal != null) {
                sb.append(unescapePdfString(literal))
            } else if (hex != null) {
                sb.append(decodePdfHexString(hex))
            } else if (offset != null) {
                val num = offset.toDoubleOrNull() ?: 0.0
                // Large negative spacing in PDF fonts indicates a word boundary space
                if (num < -120.0 && sb.isNotEmpty() && !sb.endsWith(" ")) {
                    sb.append(" ")
                }
            }
        }
        return sb.toString().trim()
    }

    private fun unescapePdfString(pdfStr: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < pdfStr.length) {
            val c = pdfStr[i]
            if (c == '\\' && i + 1 < pdfStr.length) {
                when (val next = pdfStr[i + 1]) {
                    'n' -> { sb.append('\n'); i += 2 }
                    'r' -> { sb.append('\r'); i += 2 }
                    't' -> { sb.append('\t'); i += 2 }
                    'b' -> { sb.append('\b'); i += 2 }
                    'f' -> { sb.append('\u000C'); i += 2 }
                    '\\', '(', ')' -> { sb.append(next); i += 2 }
                    in '0'..'7' -> {
                        // Octal sequence \ddd (up to 3 digits)
                        var octal = "$next"
                        var j = i + 2
                        while (j < pdfStr.length && j < i + 4 && pdfStr[j] in '0'..'7') {
                            octal += pdfStr[j]
                            j++
                        }
                        val code = octal.toIntOrNull(8) ?: 32
                        sb.append(code.toChar())
                        i = j
                    }
                    else -> {
                        sb.append(next)
                        i += 2
                    }
                }
            } else {
                sb.append(c)
                i++
            }
        }
        return sb.toString()
    }

    private fun decodePdfHexString(hex: String): String {
        val clean = hex.trim().replace(Regex("""\s+"""), "")
        val bytes = ByteArray(clean.length / 2)
        for (i in bytes.indices) {
            val byteStr = clean.substring(i * 2, i * 2 + 2)
            bytes[i] = byteStr.toIntOrNull(16)?.toByte() ?: 0
        }
        // Check for UTF-16BE BOM: 0xFE 0xFF
        return if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            String(bytes.copyOfRange(2, bytes.size), StandardCharsets.UTF_16BE)
        } else {
            String(bytes, StandardCharsets.ISO_8859_1)
        }
    }
}
