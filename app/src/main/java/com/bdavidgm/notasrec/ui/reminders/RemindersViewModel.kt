package com.bdavidgm.notasrec.ui.reminders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.bdavidgm.notasrec.data.NotasRepository
import com.bdavidgm.notasrec.data.NoteKind
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class RemindersViewModel(
    repository: NotasRepository,
) : ViewModel() {

    data class ReminderItemUi(
        val noteId: Long,
        val title: String,
        val kind: NoteKind,
        val reminderAtMillis: Long,
        val withSound: Boolean,
        val soundName: String?,
    )

    val items: StateFlow<List<ReminderItemUi>> =
        repository.observeNotesWithReminders()
            .map { notes ->
                notes.mapNotNull { note ->
                    val at = note.reminderAtMillis ?: return@mapNotNull null
                    ReminderItemUi(
                        noteId = note.id,
                        title = note.title,
                        kind = NoteKind.fromStored(note.kind),
                        reminderAtMillis = at,
                        withSound = note.reminderWithSound,
                        soundName = note.reminderSoundName,
                    )
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    companion object {
        fun factory(repository: NotasRepository) = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass.isAssignableFrom(RemindersViewModel::class.java))
                @Suppress("UNCHECKED_CAST")
                return RemindersViewModel(repository) as T
            }
        }
    }
}
