package com.bdavidgm.notasrec.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "notes",
    indices = [
        Index(value = ["uid"], unique = true),
        Index(value = ["parentId"]),
    ],
)
data class NoteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    /** Identificador estable para enlaces internos y copias de seguridad. */
    val uid: String,
    val title: String,
    val content: String,
    /** [com.bdavidgm.notasrec.data.NoteKind.name]. Las notas antiguas son de texto. */
    val kind: String = com.bdavidgm.notasrec.data.NoteKind.TEXT.name,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    /** Nota de la que cuelga esta. Nulo si está en la raíz de la lista. */
    val parentId: Long? = null,
    /** Instante en que debe sonar el aviso. Nulo si la nota no tiene recordatorio. */
    val reminderAtMillis: Long? = null,
    val reminderWithSound: Boolean = false,
    /** `content://` de una melodía del sistema, o ruta interna con prefijo `app-file:`. */
    val reminderSoundUri: String? = null,
    val reminderSoundName: String? = null,
)
