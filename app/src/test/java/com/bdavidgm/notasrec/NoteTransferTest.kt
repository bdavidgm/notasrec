package com.bdavidgm.notasrec

import com.bdavidgm.notasrec.data.NoteKind
import com.bdavidgm.notasrec.data.resolveImportedParents
import com.bdavidgm.notasrec.ui.util.FamilyExportNote
import com.bdavidgm.notasrec.ui.util.NoteExportFormat
import com.bdavidgm.notasrec.ui.util.buildFamilyExportDocument
import com.bdavidgm.notasrec.ui.util.encodeChecklist
import com.bdavidgm.notasrec.ui.util.parseChecklist
import com.bdavidgm.notasrec.ui.util.parseImportedNoteDocument
import com.bdavidgm.notasrec.ui.util.ChecklistLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteTransferTest {

    @Test
    fun checklistDocumentRoundTripsChecksAndKind() {
        val stored = encodeChecklist(
            listOf(
                ChecklistLine("huevos", checked = true),
                ChecklistLine("café", checked = false),
            ),
        )
        val document = buildFamilyExportDocument(
            notes = listOf(
                familyNote(title = "Compra", content = stored, kind = NoteKind.CHECKLIST, depth = 0),
            ),
            format = NoteExportFormat.TXT,
        )
        val parsed = parseImportedNoteDocument(document, fallbackTitle = null)
        assertEquals(NoteKind.CHECKLIST, parsed.kind)
        assertEquals("Compra", parsed.title)
        val lines = parseChecklist(parsed.content)
        assertEquals("huevos", lines[0].text)
        assertTrue(lines[0].checked)
        assertEquals("café", lines[1].text)
        assertEquals(false, lines[1].checked)
    }

    @Test
    fun childNotesKeepKindAndNesting() {
        val document = buildFamilyExportDocument(
            notes = listOf(
                familyNote("Padre", "texto del padre", NoteKind.TEXT, depth = 0),
                familyNote("Hija", encodeChecklist(listOf(ChecklistLine("una", true))), NoteKind.CHECKLIST, depth = 1),
                familyNote("Nieta", "detalle", NoteKind.TEXT, depth = 2),
                familyNote("Otra hija", "más", NoteKind.TEXT, depth = 1),
            ),
            format = NoteExportFormat.MD,
        )
        val parsed = parseImportedNoteDocument(document, fallbackTitle = null)
        assertEquals("Padre", parsed.title)
        assertEquals(NoteKind.TEXT, parsed.kind)
        assertEquals(listOf("Hija", "Otra hija"), parsed.children.map { it.title })
        assertEquals(NoteKind.CHECKLIST, parsed.children[0].kind)
        assertEquals("una", parseChecklist(parsed.children[0].content).single().text)
        assertEquals(listOf("Nieta"), parsed.children[0].children.map { it.title })
        assertEquals("detalle", parsed.children[0].children.single().content)
    }

    @Test
    fun documentWithoutKindLineStaysText() {
        val parsed = parseImportedNoteDocument(
            """
            Nota vieja

            Creada: 02/10/2026 10:00

            Un párrafo normal.
            """.trimIndent(),
            fallbackTitle = null,
        )
        assertEquals(NoteKind.TEXT, parsed.kind)
        assertEquals("Un párrafo normal.", parsed.content)
    }

    @Test
    fun importedBackupRelinksChildrenWhenUidChanges() {
        val links = resolveImportedParents(
            notes = listOf(
                "parent-export" to "",
                "child-export" to "parent-export",
            ),
            uidMap = mapOf(
                "parent-export" to "parent-new",
                "child-export" to "child-new",
            ),
            idByUid = mapOf(
                "parent-new" to 10L,
                "child-new" to 11L,
            ),
        )
        assertEquals(listOf(11L to 10L), links)
    }

    private fun familyNote(
        title: String,
        content: String,
        kind: NoteKind,
        depth: Int,
    ) = FamilyExportNote(
        title = title,
        createdAtMillis = 1_700_000_000_000L,
        updatedAtMillis = 1_700_000_000_000L,
        content = content,
        tagNames = emptyList(),
        kind = kind,
        depth = depth,
    )
}
