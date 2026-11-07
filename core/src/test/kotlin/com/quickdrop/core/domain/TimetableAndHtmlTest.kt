package com.quickdrop.core.domain

import com.quickdrop.core.model.AnchorKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets

class TimetableAndHtmlTest {

    @Test
    fun testParseHtmlTimetable() {
        val sampleHtml = """
            <!DOCTYPE html>
            <html>
            <head><title>Fall 2026 Student Schedule</title></head>
            <body>
                <h1>Student Class Schedule</h1>
                <table>
                    <thead>
                        <tr><th>Course</th><th>Days</th><th>Time</th><th>Room</th></tr>
                    </thead>
                    <tbody>
                        <tr>
                            <td>CS 401 Distributed Systems</td>
                            <td>Monday, Wednesday</td>
                            <td>09:00 - 10:30</td>
                            <td>Hall 201</td>
                        </tr>
                        <tr>
                            <td>Algorithms & Data Structures</td>
                            <td>Tuesday, Thursday</td>
                            <td>11:00 - 12:30</td>
                            <td>Room 304</td>
                        </tr>
                        <tr>
                            <td>Computer Networks Lab</td>
                            <td>Friday</td>
                            <td>14:00 - 15:30</td>
                            <td>Lab 5B</td>
                        </tr>
                    </tbody>
                </table>
            </body>
            </html>
        """.trimIndent()

        val result = TimetableParser.parse(sampleHtml.toByteArray(StandardCharsets.UTF_8), "schedule.html")
        assertEquals("HTML Web Schedule", result.detectedFormat)
        assertTrue("Should recognize slots from HTML table", result.slots.isNotEmpty())
        assertTrue("Should extract all 3 course slots, got ${result.slots.size}", result.slots.size >= 3)

        val cs401 = result.slots.find { it.courseName.contains("Distributed", ignoreCase = true) || it.courseName.contains("CS 401", ignoreCase = true) }
        assertNotNull(cs401)
        assertEquals(9 * 60, cs401!!.startMinuteOfDay)
        assertEquals(10 * 60 + 30, cs401.endMinuteOfDay)
        assertTrue("Should include Monday", (cs401.weekdayMask and 1) != 0)
        assertTrue("Should include Wednesday", (cs401.weekdayMask and 4) != 0)
    }

    @Test
    fun testParseIcsTimetable() {
        val sampleIcs = """
            BEGIN:VCALENDAR
            VERSION:2.0
            PRODID:-//University Schedule//EN
            BEGIN:VEVENT
            SUMMARY:Operating Systems Lecture
            LOCATION:Turing Hall 101
            DTSTART:20260901T100000Z
            DTEND:20260901T113000Z
            RRULE:FREQ=WEEKLY;BYDAY=MO,WE
            END:VEVENT
            BEGIN:VEVENT
            SUMMARY:Machine Learning Seminar
            LOCATION:Room 402
            DTSTART:20260902T140000Z
            DTEND:20260902T153000Z
            RRULE:FREQ=WEEKLY;BYDAY=TU,TH
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val result = TimetableParser.parse(sampleIcs.toByteArray(StandardCharsets.UTF_8), "my_classes.ics")
        assertEquals("iCalendar (.ics)", result.detectedFormat)
        assertEquals(2, result.slots.size)

        val os = result.slots.find { it.courseName.contains("Operating", ignoreCase = true) }
        assertNotNull(os)
        assertEquals(10 * 60, os!!.startMinuteOfDay)
        assertEquals(11 * 60 + 30, os.endMinuteOfDay)
        assertEquals("Turing Hall 101", os.location)
    }

    @Test
    fun testFileExtractorSupportsHtml() {
        val htmlSlides = """
            <!DOCTYPE html>
            <html>
            <head><title>Cache Coherence Slides</title></head>
            <body>
                <section class="slide">
                    <h1>Slide 1: Cache Coherence Overview</h1>
                    <p>In symmetric multiprocessing (SMP) architectures, multiple processor cores maintain private L1 and L2 caches connected via a shared bus to main memory.</p>
                </section>
                <section class="slide">
                    <h1>Slide 2: The MESI Protocol</h1>
                    <p>The MESI protocol is an invalidate-based cache coherence protocol with four distinct states: Modified, Exclusive, Shared, and Invalid.</p>
                </section>
            </body>
            </html>
        """.trimIndent()

        val bytes = htmlSlides.toByteArray(StandardCharsets.UTF_8)
        val outcome = FileExtractor.extract("file-html-1", bytes, "cache_coherence.html")

        assertEquals(FileIntakeStatus.VALID, outcome.status)
        assertEquals("text/html", outcome.detectedMimeType)
        assertTrue("Expected at least 2 slides extracted", outcome.pageOrSlideCount >= 2)
        assertEquals(outcome.pageOrSlideCount, outcome.chunks.size)
        assertTrue(outcome.chunks.first().extractedText.contains("Cache Coherence"))
        assertEquals(AnchorKind.PAGE, outcome.chunks.first().anchorKind)
    }

    @Test
    fun testParseCybersecurityMatrixHtml() {
        val userFile = java.io.File("""C:\Users\seton\Downloads\Cybersecurity_200L_Timetable_v2.html""")
        val htmlContent = if (userFile.exists()) {
            userFile.readText(StandardCharsets.UTF_8)
        } else {
            """
            <table class="timetable">
              <thead>
                <tr>
                  <th class="corner-col">DAY / TIME</th>
                  <th>08:00 – 09:00</th>
                  <th>09:00 – 10:00</th>
                  <th>10:00 – 11:00</th>
                  <th>11:00 – 12:00</th>
                  <th>12:00 – 01:00</th>
                  <th class="break-col">01:00 – 02:00</th>
                  <th>02:00 – 03:00</th>
                  <th>03:00 – 04:00</th>
                </tr>
              </thead>
              <tbody>
                <tr data-day="1">
                  <td class="day-cell">Monday</td>
                  <td class="slot-empty">Free</td>
                  <td class="slot-empty">Free</td>
                  <td colspan="2">
                    <div class="course-cell c-sen201" data-course="SEN 201">
                      <div class="course-top">
                        <span class="course-code">SEN 201</span>
                        <span class="course-status">Core • 2U</span>
                      </div>
                      <div class="course-title">Introduction to Software Engineering</div>
                      <div class="course-meta">
                        <span>10:00 AM - 12:00 PM (Double Slot)</span>
                        <span>2 hrs</span>
                      </div>
                    </div>
                  </td>
                  <td>
                    <div class="course-cell c-cyb203" data-course="CYB 203">
                      <div class="course-top"><span class="course-code">CYB 203</span></div>
                      <div class="course-title">Cybercrime, Law & Countermeasures</div>
                      <div class="course-meta"><span>12:00 - 1:00 PM</span><span>1 hr</span></div>
                    </div>
                  </td>
                  <td class="slot-break">B</td>
                  <td colspan="2">
                    <div class="course-cell c-cyb205" data-course="WU-CYB 205">
                      <div class="course-top"><span class="course-code">WU-CYB 205</span><span class="course-status">Elective • 2U</span></div>
                      <div class="course-title">Data Security and Privacy</div>
                      <div class="course-meta"><span>2:00 - 4:00 PM (Double Slot)</span><span>2 hrs</span></div>
                    </div>
                  </td>
                </tr>
              </tbody>
            </table>
            """.trimIndent()
        }

        val result = TimetableParser.parse(htmlContent.toByteArray(StandardCharsets.UTF_8), "Cybersecurity_200L_Timetable_v2.html")
        assertTrue("Should detect slots from matrix timetable", result.slots.isNotEmpty())
        if (userFile.exists()) {
            assertEquals("Should detect all 10 class slots from user file", 10, result.slots.size)
        } else {
            assertTrue("Should detect at least 3 Monday courses, found ${result.slots.size}", result.slots.size >= 3)
        }

        val sen201 = result.slots.find { it.courseCode == "SEN 201" || it.courseName.contains("Software Engineering") }
        assertNotNull("SEN 201 should be recognized", sen201)
        assertEquals(10 * 60, sen201!!.startMinuteOfDay)
        assertEquals(12 * 60, sen201.endMinuteOfDay)
        assertEquals(2, sen201.credits)
        assertTrue(sen201.isDoubleSlot)

        val cyb203 = result.slots.find { it.courseCode == "CYB 203" }
        assertNotNull("CYB 203 should be recognized", cyb203)
        assertEquals(12 * 60, cyb203!!.startMinuteOfDay)
        assertEquals(13 * 60, cyb203.endMinuteOfDay)

        val cyb205 = result.slots.find { it.courseCode == "WU-CYB 205" }
        assertNotNull("WU-CYB 205 should be recognized", cyb205)
        assertTrue("WU-CYB 205 should be elective", cyb205!!.isElective)
        assertEquals(14 * 60, cyb205.startMinuteOfDay)
        assertEquals(16 * 60, cyb205.endMinuteOfDay)
    }
}
