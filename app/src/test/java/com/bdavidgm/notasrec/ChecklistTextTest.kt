package com.bdavidgm.notasrec

import com.bdavidgm.notasrec.ui.util.ChecklistLine
import com.bdavidgm.notasrec.ui.util.checklistAsExportText
import com.bdavidgm.notasrec.ui.util.encodeChecklist
import com.bdavidgm.notasrec.ui.util.parseChecklist
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChecklistTextTest {

    @Test
    fun blankContentBecomesOneEmptyLine() {
        val lines = parseChecklist("")
        assertEquals(1, lines.size)
        assertEquals("", lines.single().text)
        assertFalse(lines.single().checked)
    }

    @Test
    fun roundTripKeepsOrderTextAndChecks() {
        val encoded = encodeChecklist(
            listOf(
                ChecklistLine("leche", checked = false),
                ChecklistLine("pan", checked = true),
            ),
        )
        val parsed = parseChecklist(encoded)
        assertEquals("leche", parsed[0].text)
        assertFalse(parsed[0].checked)
        assertEquals("pan", parsed[1].text)
        assertTrue(parsed[1].checked)
    }

    @Test
    fun untouchedEmptyLinesStayBlankInStorage() {
        assertEquals("", encodeChecklist(listOf(ChecklistLine("", checked = false))))
    }

    @Test
    fun multilineItemKeepsLineBreaks() {
        val encoded = encodeChecklist(
            listOf(ChecklistLine("leche\ndesnatada", checked = true)),
        )
        val parsed = parseChecklist(encoded)
        assertEquals("leche\ndesnatada", parsed.single().text)
        assertTrue(parsed.single().checked)
    }

    @Test
    fun exportStrikesCheckedLines() {
        val raw = encodeChecklist(
            listOf(
                ChecklistLine("huevos", checked = true),
                ChecklistLine("café", checked = false),
            ),
        )
        assertEquals("- [x] ~~huevos~~\n- [ ] café", checklistAsExportText(raw))
    }
}
