package com.bdavidgm.notasrec.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Enlace saliente desde [sourceNoteId] hacia el [targetUid] de otra nota.
 * Se reconstruye al guardar el cuerpo; no es la fuente de verdad (eso es el Markdown).
 */
@Entity(
    tableName = "note_links",
    primaryKeys = ["sourceNoteId", "targetUid"],
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["sourceNoteId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("sourceNoteId"),
        Index("targetUid"),
    ],
)
data class NoteLinkCrossRef(
    val sourceNoteId: Long,
    val targetUid: String,
)
