package com.bdavidgm.notasrec.data

enum class NoteKind {
    TEXT,
    CHECKLIST,
    AUDIO,
    ;

    companion object {
        fun fromStored(value: String?): NoteKind =
            entries.firstOrNull { it.name == value } ?: TEXT
    }
}
