package com.quickdrop.core.domain

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets

data class RecognizedClassSlot(
    val courseName: String,
    val courseCode: String = "",
    val weekdayMask: Int, // Bitmask: (1 shl (isoDay - 1))
    val dayNamesDisplay: String, // e.g. "Mon, Wed"
    val startMinuteOfDay: Int, // 0..1439
    val endMinuteOfDay: Int, // 1..1440
    val timeDisplay: String, // e.g. "09:00 - 10:30"
    val location: String = "",
    val instructor: String = "",
    val credits: Int = 2,
    val isElective: Boolean = false,
    val isDoubleSlot: Boolean = false,
    val colorCode: String = ""
)

data class TimetableParseResult(
    val fileName: String,
    val detectedFormat: String,
    val slots: List<RecognizedClassSlot>,
    val rawTextSnippet: String = "",
    val warnings: List<String> = emptyList(),
    val isAiDetected: Boolean = false
)

object TimetableParser {

    /**
     * Parses any uploaded timetable file (HTML, ICS, CSV, TSV, TXT, PDF)
     * and automatically extracts structured recurring class slots.
     */
    fun parse(bytes: ByteArray, fileName: String): TimetableParseResult {
        val lowerName = fileName.lowercase()

        return when {
            lowerName.endsWith(".ics") || isIcsContent(bytes) -> parseIcs(bytes, fileName)
            lowerName.endsWith(".html") || lowerName.endsWith(".htm") || isHtmlContent(bytes) -> parseHtml(bytes, fileName)
            lowerName.endsWith(".pdf") || isPdfContent(bytes) -> parsePdf(bytes, fileName)
            else -> parseTextOrCsv(bytes, fileName)
        }
    }

    /**
     * Smart parse that runs Gemini AI first if an API key is available,
     * and seamlessly falls back to local high-precision DOM / text parsing.
     */
    suspend fun parseSmart(
        bytes: ByteArray,
        fileName: String,
        apiKey: String? = null,
        modelId: String = "gemini-2.0-flash"
    ): TimetableParseResult {
        if (!apiKey.isNullOrBlank()) {
            val content = try {
                String(bytes, StandardCharsets.UTF_8)
            } catch (_: Exception) { "" }

            if (content.isNotBlank()) {
                val aiSlots = TimetableAiParser.parseWithGemini(content, apiKey, modelId)
                if (aiSlots != null && aiSlots.isNotEmpty()) {
                    return TimetableParseResult(
                        fileName = fileName,
                        detectedFormat = "Gemini AI Schedule Intelligence",
                        slots = aiSlots.distinctBy { "${it.courseName}_${it.weekdayMask}_${it.startMinuteOfDay}" },
                        rawTextSnippet = stripHtml(content).take(400),
                        isAiDetected = true
                    )
                }
            }
        }

        return parse(bytes, fileName)
    }

    private fun isIcsContent(bytes: ByteArray): Boolean {
        val text = safePreview(bytes, 256)
        return text.contains("BEGIN:VCALENDAR", ignoreCase = true)
    }

    private fun isHtmlContent(bytes: ByteArray): Boolean {
        val text = safePreview(bytes, 256)
        return text.contains("<!doctype html", ignoreCase = true) ||
            text.contains("<html", ignoreCase = true) ||
            text.contains("<table", ignoreCase = true) ||
            text.contains("<div", ignoreCase = true)
    }

    private fun isPdfContent(bytes: ByteArray): Boolean {
        return bytes.size >= 5 &&
            bytes[0] == 0x25.toByte() &&
            bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x44.toByte() &&
            bytes[3] == 0x46.toByte() &&
            bytes[4] == 0x2D.toByte()
    }

    private fun safePreview(bytes: ByteArray, maxLen: Int): String {
        return try {
            String(bytes.take(maxLen).toByteArray(), StandardCharsets.UTF_8).lowercase().trimStart()
        } catch (_: Exception) {
            ""
        }
    }

    // =========================================================================
    // 1. iCalendar (ICS) PARSER
    // =========================================================================
    private fun parseIcs(bytes: ByteArray, fileName: String): TimetableParseResult {
        val text = String(bytes, StandardCharsets.UTF_8)
        val events = text.split(Regex("BEGIN:VEVENT", RegexOption.IGNORE_CASE))
        val slots = mutableListOf<RecognizedClassSlot>()

        for (eventBlock in events.drop(1)) {
            val endIdx = eventBlock.indexOf("END:VEVENT", ignoreCase = true)
            val block = if (endIdx != -1) eventBlock.substring(0, endIdx) else eventBlock

            var summary = ""
            var location = ""
            var dtStart = ""
            var dtEnd = ""
            var rrule = ""

            // Unfold lines as per RFC 5545
            val unfolded = block.replace("\r\n ", "").replace("\n ", "")
            for (line in unfolded.lines()) {
                val trimmed = line.trim()
                val upper = trimmed.uppercase()
                when {
                    upper.startsWith("SUMMARY:") -> summary = trimmed.substringAfter(":")
                    upper.startsWith("LOCATION:") -> location = trimmed.substringAfter(":")
                    upper.startsWith("DTSTART") -> dtStart = trimmed.substringAfter(":")
                    upper.startsWith("DTEND") -> dtEnd = trimmed.substringAfter(":")
                    upper.startsWith("RRULE:") -> rrule = trimmed.substringAfter(":")
                }
            }

            if (summary.isBlank() && dtStart.isBlank()) continue

            val startTimeMin = extractTimeMinutesFromIcs(dtStart) ?: (9 * 60)
            val endTimeMin = extractTimeMinutesFromIcs(dtEnd) ?: (startTimeMin + 90)

            val days = parseRruleDays(rrule).ifEmpty {
                parseDayFromIcsDate(dtStart)
            }

            if (days.isNotEmpty()) {
                val mask = days.fold(0) { acc, d -> acc or (1 shl (d - 1)) }
                val dayStr = days.sorted().joinToString(", ") { isoDayToAbbrev(it) }
                val timeStr = "%02d:%02d - %02d:%02d".format(
                    startTimeMin / 60, startTimeMin % 60,
                    endTimeMin / 60, endTimeMin % 60
                )

                slots.add(
                    RecognizedClassSlot(
                        courseName = cleanCourseName(summary),
                        weekdayMask = mask,
                        dayNamesDisplay = dayStr,
                        startMinuteOfDay = startTimeMin,
                        endMinuteOfDay = endTimeMin,
                        timeDisplay = timeStr,
                        location = cleanLocation(location)
                    )
                )
            }
        }

        return TimetableParseResult(
            fileName = fileName,
            detectedFormat = "iCalendar (.ics)",
            slots = slots.distinctBy { "${it.courseName}_${it.weekdayMask}_${it.startMinuteOfDay}" },
            rawTextSnippet = text.take(500)
        )
    }

    private fun extractTimeMinutesFromIcs(dtStr: String): Int? {
        val tIndex = dtStr.indexOf('T')
        if (tIndex == -1 || tIndex + 5 > dtStr.length) return null
        val timePart = dtStr.substring(tIndex + 1)
        val hour = timePart.take(2).toIntOrNull() ?: return null
        val min = timePart.drop(2).take(2).toIntOrNull() ?: 0
        return hour * 60 + min
    }

    private fun parseRruleDays(rrule: String): Set<Int> {
        val days = mutableSetOf<Int>()
        val byDayMatch = Regex("BYDAY=([^;]+)", RegexOption.IGNORE_CASE).find(rrule)
        if (byDayMatch != null) {
            val tokens = byDayMatch.groupValues[1].split(",")
            for (token in tokens) {
                val upper = token.trim().uppercase()
                when {
                    upper.endsWith("MO") -> days.add(1)
                    upper.endsWith("TU") -> days.add(2)
                    upper.endsWith("WE") -> days.add(3)
                    upper.endsWith("TH") -> days.add(4)
                    upper.endsWith("FR") -> days.add(5)
                    upper.endsWith("SA") -> days.add(6)
                    upper.endsWith("SU") -> days.add(7)
                }
            }
        }
        return days
    }

    private fun parseDayFromIcsDate(dtStart: String): Set<Int> {
        val dateDigits = dtStart.takeWhile { it.isDigit() }
        if (dateDigits.length >= 8) {
            try {
                val year = dateDigits.take(4).toInt()
                val month = dateDigits.substring(4, 6).toInt()
                val day = dateDigits.substring(6, 8).toInt()
                val javaDate = java.time.LocalDate.of(year, month, day)
                return setOf(javaDate.dayOfWeek.value)
            } catch (_: Exception) {}
        }
        return setOf(1)
    }

    // =========================================================================
    // 2. HTML TIMETABLE PARSER (High-Precision Matrix + Directory Cards + Fallback)
    // =========================================================================
    private fun parseHtml(bytes: ByteArray, fileName: String): TimetableParseResult {
        val html = String(bytes, StandardCharsets.UTF_8)
        val slots = mutableListOf<RecognizedClassSlot>()

        // 1. Matrix Timetable (e.g. <tr> with data-day, day headers, course-cells, colspan)
        val matrixSlots = parseHtmlMatrixTable(html)
        slots.addAll(matrixSlots)

        // 2. Course Cards (<div class="course-card">)
        if (slots.isEmpty()) {
            val cardSlots = parseHtmlCourseCards(html)
            slots.addAll(cardSlots)
        }

        // 3. Fallback to generic table row extraction (List format: Course | Days | Time | Room)
        if (slots.isEmpty()) {
            val genericSlots = parseHtmlGenericTable(html)
            slots.addAll(genericSlots)
        }

        // 4. Fallback to plain text lines
        if (slots.isEmpty()) {
            val plainText = stripHtml(html)
            slots.addAll(parseTextLines(plainText))
        }

        val formatDesc = when {
            matrixSlots.isNotEmpty() -> "HTML Schedule Matrix"
            else -> "HTML Web Schedule"
        }

        return TimetableParseResult(
            fileName = fileName,
            detectedFormat = formatDesc,
            slots = slots.distinctBy { "${it.courseName}_${it.weekdayMask}_${it.startMinuteOfDay}" },
            rawTextSnippet = stripHtml(html).take(400)
        )
    }

    /**
     * Parses complex weekly schedule matrix tables with header time columns and day rows.
     */
    fun parseHtmlMatrixTable(html: String): List<RecognizedClassSlot> {
        val slots = mutableListOf<RecognizedClassSlot>()

        // Extract column header time slots
        val thRegex = Regex("<th[^>]*>(.*?)</th>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        val theadMatch = Regex("<thead[^>]*>(.*?)</thead>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(html)
        val theadContent = theadMatch?.groupValues?.get(1) ?: html

        val headerCells = thRegex.findAll(theadContent).map { stripHtml(it.groupValues[1]).trim() }.toList()
        val colTimes = mutableMapOf<Int, Pair<Int, Int>>()
        headerCells.forEachIndexed { idx, cellText ->
            val range = extractTimeRange(cellText)
            if (range != null) {
                colTimes[idx] = range
            }
        }

        // Process table rows
        val trRegex = Regex("<tr([^>]*)>(.*?)</tr>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        for (match in trRegex.findAll(html)) {
            val trAttrs = match.groupValues[1]
            val trBody = match.groupValues[2]

            // 1. Determine day of week
            var dayOfWeek: Int? = null
            val dayAttrMatch = Regex("data-day\\s*=\\s*[\"']?(\\d+)[\"']?", RegexOption.IGNORE_CASE).find(trAttrs)
            if (dayAttrMatch != null) {
                dayOfWeek = dayAttrMatch.groupValues[1].toIntOrNull()
            }

            if (dayOfWeek == null) {
                val firstCellMatch = Regex("<(?:td|th)[^>]*>(.*?)</(?:td|th)>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(trBody)
                if (firstCellMatch != null) {
                    val firstCellText = stripHtml(firstCellMatch.groupValues[1]).trim()
                    val days = extractDays(firstCellText)
                    if (days.isNotEmpty()) {
                        dayOfWeek = days.first()
                    }
                }
            }

            if (dayOfWeek == null || dayOfWeek !in 1..7) continue

            // 2. Iterate through <td> elements in the row
            val tdRegex = Regex("<td([^>]*)>(.*?)</td>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            val tdMatches = tdRegex.findAll(trBody).toList()

            var currentColIndex = 0
            for ((idx, tdMatch) in tdMatches.withIndex()) {
                val tdAttrs = tdMatch.groupValues[1]
                val tdContent = tdMatch.groupValues[2]

                val colspanMatch = Regex("colspan\\s*=\\s*[\"']?(\\d+)[\"']?", RegexOption.IGNORE_CASE).find(tdAttrs)
                val colspan = colspanMatch?.groupValues?.get(1)?.toIntOrNull() ?: 1

                // Skip Day Name cell (usually first cell at column 0)
                if (idx == 0 && extractDays(stripHtml(tdContent)).isNotEmpty()) {
                    currentColIndex = 1
                    continue
                }

                // Check for break column or free period
                val isBreak = tdAttrs.contains("slot-break", ignoreCase = true) ||
                    stripHtml(tdContent).trim().uppercase() in setOf("B", "R", "E", "A", "K", "BREAK")
                val isFree = tdAttrs.contains("slot-empty", ignoreCase = true) &&
                    !tdContent.contains("course-cell", ignoreCase = true)

                if (isBreak || isFree) {
                    currentColIndex += colspan
                    continue
                }

                // Check if this cell contains a course
                if (tdContent.contains("course-cell", ignoreCase = true) ||
                    Regex("course-(?:code|title|top)", RegexOption.IGNORE_CASE).containsMatchIn(tdContent) ||
                    Regex("\\b[A-Z]{2,4}\\s*\\d{3}\\b").containsMatchIn(tdContent)
                ) {
                    val slot = extractSlotFromHtmlCell(tdAttrs, tdContent, dayOfWeek, currentColIndex, colspan, colTimes)
                    if (slot != null) {
                        slots.add(slot)
                    }
                }

                currentColIndex += colspan
            }
        }

        return slots
    }

    private fun extractSlotFromHtmlCell(
        tdAttrs: String,
        tdContent: String,
        dayOfWeek: Int,
        colIndex: Int,
        colspan: Int,
        colTimes: Map<Int, Pair<Int, Int>>
    ): RecognizedClassSlot? {
        // Course Code
        val codeAttrMatch = Regex("data-course\\s*=\\s*[\"']?([^\"'>]+)[\"']?", RegexOption.IGNORE_CASE).find(tdContent)
        val codeTagMatch = Regex("<span[^>]*class=[\"'][^\"']*course-code[^\"']*[\"'][^>]*>([^<]+)</span>", RegexOption.IGNORE_CASE).find(tdContent)
        val genericCodeMatch = Regex("\\b([A-Z]{2,4}\\s*-\\s*[A-Z]{2,4}\\s*\\d{3}|[A-Z]{2,4}\\s*\\d{3})\\b").find(tdContent)

        val courseCode = (codeAttrMatch?.groupValues?.get(1)
            ?: codeTagMatch?.groupValues?.get(1)
            ?: genericCodeMatch?.groupValues?.get(1)
            ?: "").trim()

        // Course Title
        val titleTagMatch = Regex("<div[^>]*class=[\"'][^\"']*course-title[^\"']*[\"'][^>]*>([^<]+)</div>", RegexOption.IGNORE_CASE).find(tdContent)
        var courseTitle = titleTagMatch?.groupValues?.get(1)?.trim().orEmpty()
        if (courseTitle.isBlank()) {
            val plain = stripHtml(tdContent)
            courseTitle = plain.replace(courseCode, "").lines().firstOrNull { it.isNotBlank() } ?: courseCode
        }

        // Status & Credits
        val statusTagMatch = Regex("<span[^>]*class=[\"'][^\"']*course-status[^\"']*[\"'][^>]*>([^<]+)</span>", RegexOption.IGNORE_CASE).find(tdContent)
        val statusText = statusTagMatch?.groupValues?.get(1)?.trim().orEmpty()
        val credits = Regex("(\\d+)\\s*[Uu]").find(statusText)?.groupValues?.get(1)?.toIntOrNull()
            ?: Regex("(\\d+)\\s*Units?", RegexOption.IGNORE_CASE).find(tdContent)?.groupValues?.get(1)?.toIntOrNull()
            ?: 2
        val isElective = statusText.contains("Elective", ignoreCase = true) ||
            tdContent.contains("Elective", ignoreCase = true)

        // Time Range
        val metaTagMatch = Regex("<div[^>]*class=[\"'][^\"']*course-meta[^\"']*[\"'][^>]*>(.*?)</div>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(tdContent)
        val metaText = metaTagMatch?.groupValues?.get(1)?.let { stripHtml(it) } ?: ""
        var timeRange = extractTimeRange(metaText)

        if (timeRange == null) {
            val headerStart = colTimes[colIndex]?.first
            val headerEnd = colTimes[colIndex + colspan - 1]?.second
            if (headerStart != null && headerEnd != null) {
                timeRange = Pair(headerStart, headerEnd)
            } else {
                timeRange = extractTimeRange(stripHtml(tdContent))
            }
        }

        if (timeRange == null) {
            // Default 9am - 10am
            timeRange = Pair(9 * 60, 10 * 60)
        }

        val (startMin, endMin) = timeRange
        val isDouble = colspan >= 2 || (endMin - startMin >= 110) ||
            tdContent.contains("Double Slot", ignoreCase = true)

        // CSS Color Code
        val colorMatch = Regex("class=[\"'][^\"']*(c-[a-z0-9_-]+)[^\"']*[\"']", RegexOption.IGNORE_CASE).find(tdContent)
        val colorCode = colorMatch?.groupValues?.get(1).orEmpty()

        val mask = 1 shl (dayOfWeek - 1)
        val dayStr = isoDayToAbbrev(dayOfWeek)
        val timeStr = "%02d:%02d - %02d:%02d".format(
            startMin / 60, startMin % 60,
            endMin / 60, endMin % 60
        )

        return RecognizedClassSlot(
            courseName = cleanCourseName(courseTitle.ifEmpty { courseCode }),
            courseCode = courseCode,
            weekdayMask = mask,
            dayNamesDisplay = dayStr,
            startMinuteOfDay = startMin,
            endMinuteOfDay = endMin,
            timeDisplay = timeStr,
            location = "Lecture Hall",
            credits = credits,
            isElective = isElective,
            isDoubleSlot = isDouble,
            colorCode = colorCode
        )
    }

    /**
     * Parses course cards (e.g. <div class="course-card">) containing codes, titles, and slot pills.
     */
    private fun parseHtmlCourseCards(html: String): List<RecognizedClassSlot> {
        val slots = mutableListOf<RecognizedClassSlot>()
        val cardRegex = Regex("<div[^>]*class=[\"'][^\"']*course-card[^\"']*[\"'][^>]*>(.*?)</div>\\s*</div>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

        for (match in cardRegex.findAll(html)) {
            val block = match.groupValues[1]

            val code = Regex("<span[^>]*class=[\"'][^\"']*cc-code[^\"']*[\"'][^>]*>([^<]+)</span>", RegexOption.IGNORE_CASE).find(block)?.groupValues?.get(1)?.trim().orEmpty()
            val title = Regex("<div[^>]*class=[\"'][^\"']*cc-title[^\"']*[\"'][^>]*>([^<]+)</div>", RegexOption.IGNORE_CASE).find(block)?.groupValues?.get(1)?.trim().orEmpty()

            val units = Regex("(\\d+)\\s*Units?", RegexOption.IGNORE_CASE).find(block)?.groupValues?.get(1)?.toIntOrNull() ?: 2
            val isElective = block.contains("Status: E", ignoreCase = true) || block.contains("Elective", ignoreCase = true)

            val pillRegex = Regex("<span[^>]*class=[\"'][^\"']*cc-slot-pill[^\"']*[\"'][^>]*>([^<]+)</span>", RegexOption.IGNORE_CASE)
            for (pillMatch in pillRegex.findAll(block)) {
                val pillText = pillMatch.groupValues[1].trim()
                val days = extractDays(pillText)
                val timeRange = extractTimeRange(pillText)

                if (days.isNotEmpty() && timeRange != null) {
                    for (d in days) {
                        val (startMin, endMin) = timeRange
                        val mask = 1 shl (d - 1)
                        val dayStr = isoDayToAbbrev(d)
                        val timeStr = "%02d:%02d - %02d:%02d".format(
                            startMin / 60, startMin % 60,
                            endMin / 60, endMin % 60
                        )

                        slots.add(
                            RecognizedClassSlot(
                                courseName = cleanCourseName(title.ifEmpty { code }),
                                courseCode = code,
                                weekdayMask = mask,
                                dayNamesDisplay = dayStr,
                                startMinuteOfDay = startMin,
                                endMinuteOfDay = endMin,
                                timeDisplay = timeStr,
                                location = "Lecture Hall",
                                credits = units,
                                isElective = isElective,
                                isDoubleSlot = (endMin - startMin >= 110)
                            )
                        )
                    }
                }
            }
        }

        return slots
    }

    /**
     * Standard table parser: <tr><td>Course</td><td>Days</td><td>Time</td>...</tr>
     */
    private fun parseHtmlGenericTable(html: String): List<RecognizedClassSlot> {
        val slots = mutableListOf<RecognizedClassSlot>()
        val trRegex = Regex("<tr[^>]*>(.*?)</tr>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        val rows = trRegex.findAll(html).map { it.groupValues[1] }.toList()

        for (row in rows) {
            val tdRegex = Regex("<(?:td|th)[^>]*>(.*?)</(?:td|th)>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            val cells = tdRegex.findAll(row).map { stripHtml(it.groupValues[1]).trim() }.filter { it.isNotEmpty() }.toList()

            if (cells.size >= 2) {
                val combinedRow = cells.joinToString(" | ")
                val slot = parseLineForClassSlot(combinedRow)
                if (slot != null) {
                    slots.add(slot)
                }
            }
        }
        return slots
    }

    // =========================================================================
    // 3. PDF TIMETABLE PARSER
    // =========================================================================
    private fun parsePdf(bytes: ByteArray, fileName: String): TimetableParseResult {
        val text = try {
            PDDocument.load(bytes).use { doc ->
                val stripper = PDFTextStripper()
                stripper.sortByPosition = true
                stripper.getText(doc)
            }
        } catch (_: Exception) {
            ""
        }

        val slots = parseTextLines(text)

        return TimetableParseResult(
            fileName = fileName,
            detectedFormat = "PDF Timetable Document",
            slots = slots.distinctBy { "${it.courseName}_${it.weekdayMask}_${it.startMinuteOfDay}" },
            rawTextSnippet = text.take(500)
        )
    }

    // =========================================================================
    // 4. CSV / TSV / TEXT TIMETABLE PARSER
    // =========================================================================
    private fun parseTextOrCsv(bytes: ByteArray, fileName: String): TimetableParseResult {
        val text = String(bytes, StandardCharsets.UTF_8)
        val slots = parseTextLines(text)

        return TimetableParseResult(
            fileName = fileName,
            detectedFormat = if (fileName.endsWith(".csv", ignoreCase = true)) "CSV Schedule" else "Text Schedule",
            slots = slots.distinctBy { "${it.courseName}_${it.weekdayMask}_${it.startMinuteOfDay}" },
            rawTextSnippet = text.take(400)
        )
    }

    // =========================================================================
    // HEURISTIC LINE PARSER (Days, Times, Courses & Locations)
    // =========================================================================
    private fun parseTextLines(text: String): List<RecognizedClassSlot> {
        val slots = mutableListOf<RecognizedClassSlot>()
        val lines = text.lines()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.length < 6) continue

            val slot = parseLineForClassSlot(trimmed)
            if (slot != null) {
                slots.add(slot)
            }
        }

        if (slots.isEmpty() && lines.size > 2) {
            for (i in 0 until lines.size - 2) {
                val window = "${lines[i]} | ${lines[i + 1]} | ${lines[i + 2]}"
                val slot = parseLineForClassSlot(window)
                if (slot != null) {
                    slots.add(slot)
                }
            }
        }

        return slots
    }

    /**
     * Parses a single text line (or table row) for Day, Time, Course Title, and Location.
     */
    fun parseLineForClassSlot(line: String): RecognizedClassSlot? {
        val days = extractDays(line)
        val timeRange = extractTimeRange(line)

        if (days.isEmpty() || timeRange == null) return null

        val (startMin, endMin) = timeRange
        val mask = days.fold(0) { acc, d -> acc or (1 shl (d - 1)) }
        val dayStr = days.sorted().joinToString(", ") { isoDayToAbbrev(it) }
        val timeStr = "%02d:%02d - %02d:%02d".format(
            startMin / 60, startMin % 60,
            endMin / 60, endMin % 60
        )

        val location = extractLocation(line)
        val courseName = extractCourseName(line, days, location)

        val codeMatch = Regex("\\b([A-Z]{2,4}\\s*-\\s*[A-Z]{2,4}\\s*\\d{3}|[A-Z]{2,4}\\s*\\d{3})\\b").find(line)
        val courseCode = codeMatch?.value.orEmpty()

        return RecognizedClassSlot(
            courseName = courseName,
            courseCode = courseCode,
            weekdayMask = mask,
            dayNamesDisplay = dayStr,
            startMinuteOfDay = startMin,
            endMinuteOfDay = endMin,
            timeDisplay = timeStr,
            location = location,
            credits = 2,
            isElective = line.contains("Elective", ignoreCase = true),
            isDoubleSlot = (endMin - startMin >= 110)
        )
    }

    private fun extractDays(text: String): Set<Int> {
        val days = mutableSetOf<Int>()
        val lower = text.lowercase()

        if (Regex("\\b(mon|monday)\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)) days.add(1)
        if (Regex("\\b(tue|tues|tuesday)\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)) days.add(2)
        if (Regex("\\b(wed|wednesday)\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)) days.add(3)
        if (Regex("\\b(thu|thur|thurs|thursday)\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)) days.add(4)
        if (Regex("\\b(fri|friday)\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)) days.add(5)
        if (Regex("\\b(sat|saturday)\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)) days.add(6)
        if (Regex("\\b(sun|sunday)\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)) days.add(7)

        if (days.isEmpty()) {
            if (Regex("\\bMWF\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)) {
                days.addAll(listOf(1, 3, 5))
            } else if (Regex("\\b(TTH|TR)\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)) {
                days.addAll(listOf(2, 4))
            } else if (Regex("\\bMW\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)) {
                days.addAll(listOf(1, 3))
            }
        }

        return days
    }

    private fun extractTimeRange(text: String): Pair<Int, Int>? {
        // Matches e.g. 09:00 - 10:30, 9:00 AM - 10:30 AM, 14:00 to 15:30, 9am-10:30am
        val timeRegex = Regex("(\\d{1,2})[:.]?(\\d{2})?\\s*(am|pm)?\\s*(?:-|–|—|to)\\s*(\\d{1,2})[:.]?(\\d{2})?\\s*(am|pm)?", RegexOption.IGNORE_CASE)
        val match = timeRegex.find(text) ?: return null

        val h1 = match.groupValues[1].toIntOrNull() ?: return null
        val m1 = match.groupValues[2].toIntOrNull() ?: 0
        val ap1 = match.groupValues[3].lowercase()

        val h2 = match.groupValues[4].toIntOrNull() ?: return null
        val m2 = match.groupValues[5].toIntOrNull() ?: 0
        val ap2 = match.groupValues[6].lowercase()

        var startHour = h1
        var endHour = h2

        // AM/PM adjustments
        if (ap1 == "pm" && startHour < 12) startHour += 12
        if (ap1 == "am" && startHour == 12) startHour = 0

        if (ap2 == "pm" && endHour < 12) endHour += 12
        if (ap2 == "am" && endHour == 12) endHour = 0

        // Heuristic: If 1:00 to 4:00 without PM, or start < 8 and end > start
        if (ap1.isEmpty() && ap2 == "pm" && startHour < 8) startHour += 12
        if (ap1.isEmpty() && ap2.isEmpty()) {
            if (startHour in 1..7) startHour += 12
            if (endHour in 1..7 && endHour <= startHour) endHour += 12
        }

        val startMin = (startHour * 60 + m1).coerceIn(0, 1439)
        val endMin = (endHour * 60 + m2).coerceIn(1, 1440)

        val finalEndMin = if (endMin > startMin) endMin else (startMin + 90).coerceAtMost(1440)
        return Pair(startMin, finalEndMin)
    }

    private fun extractLocation(text: String): String {
        val locationRegex = Regex("(?:Room|Rm|Hall|Building|Bldg|Lab|Auditorium|Aud|LH)\\s*[:#-]?\\s*([A-Za-z0-9-]+(?:\\s+[A-Za-z0-9-]+)?)", RegexOption.IGNORE_CASE)
        val match = locationRegex.find(text)
        if (match != null) {
            return match.value.trim()
        }
        if (text.contains("online", ignoreCase = true) || text.contains("zoom", ignoreCase = true)) {
            return "Online / Zoom"
        }
        return "Lecture Hall"
    }

    private fun extractCourseName(line: String, days: Set<Int>, location: String): String {
        var cleaned = line
            .replace(Regex("(\\d{1,2})[:.]?(\\d{2})?\\s*(am|pm)?\\s*(?:-|–|—|to)\\s*(\\d{1,2})[:.]?(\\d{2})?\\s*(am|pm)?", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\b(mon|monday|tue|tues|tuesday|wed|wednesday|thu|thur|thurs|thursday|fri|friday|sat|saturday|sun|sunday)\\b", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\b(MWF|TTH|TR|MW)\\b", RegexOption.IGNORE_CASE), "")
            .replace(Regex("(?:Room|Rm|Hall|Building|Bldg|Lab|Auditorium|Aud|LH)\\s*[:#-]?\\s*([A-Za-z0-9-]+(?:\\s+[A-Za-z0-9-]+)?)", RegexOption.IGNORE_CASE), "")
            .replace(Regex("[|;,]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        cleaned = cleaned.replace(Regex("^(?:Course|Subject|Class|Lecture|Module)\\s*[:#-]?\\s*", RegexOption.IGNORE_CASE), "")

        if (cleaned.length < 3) {
            return "Course ${days.joinToString("") { isoDayToAbbrev(it) }}"
        }

        return cleanCourseName(cleaned.take(45))
    }

    private fun cleanCourseName(name: String): String {
        return name
            .replace(Regex("[_\\-]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .split(" ")
            .joinToString(" ") { it.replaceFirstChar { c -> if (c.isLowerCase()) c.titlecase() else c.toString() } }
    }

    private fun cleanLocation(loc: String): String {
        return if (loc.isBlank()) "Lecture Hall" else loc.trim()
    }

    fun stripHtml(html: String): String {
        return html
            .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("</(?:p|div|tr|h[1-6]|li)>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("<[^>]+>"), " ")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace(Regex("[ \\t]+"), " ")
            .trim()
    }

    fun isoDayToAbbrev(isoDay: Int): String {
        return when (isoDay) {
            1 -> "Mon"
            2 -> "Tue"
            3 -> "Wed"
            4 -> "Thu"
            5 -> "Fri"
            6 -> "Sat"
            7 -> "Sun"
            else -> "Day"
        }
    }
}
