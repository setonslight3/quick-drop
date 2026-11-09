package com.quickdrop.core.domain

import com.quickdrop.core.model.Difficulty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuestionPackParserTest {

    @Test
    fun `parses standard sample JSON template correctly`() {
        val result = QuestionPackParser.parse(QuestionPackParser.SAMPLE_JSON_TEMPLATE)
        assertTrue(result.isSuccess)
        val pack = result.getOrThrow()

        assertEquals("CYB 203", pack.courseCode)
        assertEquals("Cybercrime, Law & Countermeasures", pack.courseName)
        assertEquals(2, pack.drops.size)
        assertEquals(2, pack.questions.size)

        val firstDrop = pack.drops[0]
        assertTrue(firstDrop.concept.contains("Cyber-dependent"))
        assertEquals(Difficulty.EASY, firstDrop.difficulty)

        val firstQ = pack.questions[0]
        assertTrue(firstQ.question.contains("cyber-dependent"))
        assertTrue(firstQ.options[0].contains("DDoS"))
        assertEquals(4, firstQ.options.size)
        assertEquals(0, firstQ.correctIndex)
    }

    @Test
    fun `parses markdown-fenced json correctly`() {
        val fenced = "```json\n" + QuestionPackParser.SAMPLE_JSON_TEMPLATE + "\n```"
        val result = QuestionPackParser.parse(fenced)
        assertTrue(result.isSuccess)
        val pack = result.getOrThrow()
        assertEquals(2, pack.questions.size)
    }

    @Test
    fun `parses options with text and isCorrect objects`() {
        val raw = """
        {
          "courseName": "Operating Systems",
          "lectureTitle": "Virtual Memory",
          "questions": [
            {
              "question": "What is the TLB?",
              "options": [
                { "text": "Translation Lookaside Buffer", "isCorrect": true },
                { "text": "Thread Local Block", "isCorrect": false }
              ],
              "explanation": "Caches address mappings."
            }
          ]
        }
        """.trimIndent()

        val result = QuestionPackParser.parse(raw)
        assertTrue(result.isSuccess)
        val pack = result.getOrThrow()
        assertEquals(1, pack.questions.size)
        assertEquals(0, pack.questions[0].correctIndex)
        assertEquals("Translation Lookaside Buffer", pack.questions[0].options[0])
    }
}
