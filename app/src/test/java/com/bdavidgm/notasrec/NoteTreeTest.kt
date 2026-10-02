package com.bdavidgm.notasrec

import com.bdavidgm.notasrec.data.flattenNoteTree
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteTreeTest {

    private data class Node(val id: Long, val parentId: Long?, val updatedAt: Long)

    private fun tree(nodes: List<Node>, expanded: Set<Long>?) =
        flattenNoteTree(
            items = nodes,
            idOf = { it.id },
            parentIdOf = { it.parentId },
            sortKey = { it.updatedAt },
            expandedIds = expanded,
        )

    @Test
    fun collapsedParentHidesChildren() {
        val entries = tree(
            listOf(
                Node(1, null, 20),
                Node(2, 1, 10),
                Node(3, null, 5),
            ),
            expanded = emptySet(),
        )
        assertEquals(listOf(1L, 3L), entries.map { it.item.id })
        assertTrue(entries.first().hasChildren)
        assertFalse(entries.first().expanded)
    }

    @Test
    fun expandedParentShowsChildIndented() {
        val entries = tree(
            listOf(
                Node(1, null, 20),
                Node(2, 1, 10),
            ),
            expanded = setOf(1),
        )
        assertEquals(listOf(1L, 2L), entries.map { it.item.id })
        assertEquals(0, entries[0].depth)
        assertEquals(1, entries[1].depth)
    }

    @Test
    fun childWithoutParentInTheListStaysVisible() {
        val entries = tree(
            listOf(Node(2, 1, 10)),
            expanded = null,
        )
        assertEquals(1, entries.size)
        assertEquals(0, entries.single().depth)
    }
}
