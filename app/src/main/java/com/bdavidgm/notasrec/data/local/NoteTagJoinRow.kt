package com.bdavidgm.notasrec.data.local

import androidx.room.ColumnInfo

/**
 * Fila plana de JOIN nota–etiquetas (sin @Relation) para compatibilidad con KSP + AGP 9.
 */
data class NoteTagJoinRow(
    @ColumnInfo(name = "noteId")
    val noteId: Long,
    val uid: String,
    val title: String,
    val content: String,
    val kind: String,
    val parentId: Long?,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val reminderAtMillis: Long?,
    val reminderWithSound: Boolean,
    val reminderSoundUri: String?,
    val reminderSoundName: String?,
    @ColumnInfo(name = "tagId")
    val tagId: Long,
    @ColumnInfo(name = "tagName")
    val tagName: String,
)
