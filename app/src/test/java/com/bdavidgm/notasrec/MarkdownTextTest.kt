package com.bdavidgm.notasrec

import com.bdavidgm.notasrec.ui.util.ensurePureMarkdown
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class MarkdownTextTest {

    @Test
    fun lineBreakTagsOnTheirOwnLineBecomeBlankMarkdownLines() {
        val input = "Primero\n\n<br>\n\nSegundo"
        val result = ensurePureMarkdown(input)

        assertEquals("Primero\n\n\n\nSegundo", result)
        assertFalse(result.contains("<br>"))
    }

    @Test
    fun inlineLineBreakBecomesMarkdownHardBreak() {
        assertEquals(
            "Primero  \nSegundo",
            ensurePureMarkdown("Primero<br />Segundo"),
        )
    }

    @Test
    fun matchingIsCaseInsensitive() {
        assertEquals("\n", ensurePureMarkdown("<BR/>\n"))
    }
}
