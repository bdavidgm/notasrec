package com.bdavidgm.notasrec.data

import com.bdavidgm.notasrec.data.local.NoteEntity
import com.bdavidgm.notasrec.data.local.NoteSummaryJoinRow
import com.bdavidgm.notasrec.data.local.NoteTagJoinRow
import com.bdavidgm.notasrec.data.local.TagEntity

data class NoteWithTags(
    val note: NoteEntity,
    val tags: List<TagEntity>,
)

data class NoteFamilyMember(
    val note: NoteWithTags,
    val depth: Int,
)

/** Nota de la lista principal, sin el cuerpo. */
data class NoteSummary(
    val id: Long,
    val title: String,
    val kind: String,
    val parentId: Long?,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val tags: List<TagEntity>,
    val hasReminder: Boolean,
)

/** Resumen para el diálogo de enlazar nota (sin cargar el cuerpo entero). */
data class NoteLinkCandidate(
    val id: Long,
    val uid: String,
    val title: String,
    val depth: Int = 0,
)

internal fun List<NoteSummaryJoinRow>.toNoteSummaryList(): List<NoteSummary> {
    if (isEmpty()) return emptyList()
    return groupBy { it.noteId }
        .entries
        .sortedByDescending { it.value.first().updatedAtMillis }
        .map { (_, rows) ->
            val r0 = rows.first()
            val tags = rows.mapNotNull { r ->
                if (r.tagId < 0L) return@mapNotNull null
                TagEntity(id = r.tagId, name = r.tagName)
            }.distinctBy { it.id }.sortedBy { it.name.lowercase() }
            NoteSummary(
                id = r0.noteId,
                title = r0.title,
                kind = r0.kind,
                parentId = r0.parentId,
                createdAtMillis = r0.createdAtMillis,
                updatedAtMillis = r0.updatedAtMillis,
                tags = tags,
                hasReminder = r0.reminderAtMillis != null,
            )
        }
}

internal fun List<NoteTagJoinRow>.toNoteWithTagsList(): List<NoteWithTags> {
    if (isEmpty()) return emptyList()
    return groupBy { it.noteId }
        .entries
        .sortedByDescending { it.value.first().updatedAtMillis }
        .map { (_, rows) ->
            val r0 = rows.first()
            val note = NoteEntity(
                id = r0.noteId,
                uid = r0.uid,
                title = r0.title,
                content = r0.content,
                kind = r0.kind,
                createdAtMillis = r0.createdAtMillis,
                updatedAtMillis = r0.updatedAtMillis,
                parentId = r0.parentId,
                reminderAtMillis = r0.reminderAtMillis,
                reminderWithSound = r0.reminderWithSound,
                reminderSoundUri = r0.reminderSoundUri,
                reminderSoundName = r0.reminderSoundName,
            )
            val tags = rows.mapNotNull { r ->
                if (r.tagId < 0L) return@mapNotNull null
                TagEntity(id = r.tagId, name = r.tagName)
            }.distinctBy { it.id }.sortedBy { it.name.lowercase() }
            NoteWithTags(note = note, tags = tags)
        }
}

internal fun List<NoteTagJoinRow>.toSingleNoteWithTags(): NoteWithTags? {
    if (isEmpty()) return null
    return toNoteWithTagsList().firstOrNull()
}
