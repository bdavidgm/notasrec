package com.bdavidgm.notasrec.ui.home

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.bdavidgm.notasrec.data.NoteKind
import com.bdavidgm.notasrec.data.NoteSummary
import com.bdavidgm.notasrec.data.NoteTreeEntry
import com.bdavidgm.notasrec.data.NotasRepository
import com.bdavidgm.notasrec.data.flattenNoteTree
import com.bdavidgm.notasrec.ui.util.noteTimestampLabel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale

sealed interface BackupFeedback {
    data object ExportOk : BackupFeedback
    data object ExportFail : BackupFeedback
    data class ImportOk(val noteCount: Int) : BackupFeedback
    data object ImportFail : BackupFeedback
    data class OpenDocumentOk(val noteId: Long) : BackupFeedback
    data object OpenDocumentFail : BackupFeedback
}

class HomeViewModel(
    private val repository: NotasRepository,
) : ViewModel() {

    private val _backupFeedback = Channel<BackupFeedback>(Channel.BUFFERED)
    val backupFeedback = _backupFeedback.receiveAsFlow()

    private val _search = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _search.asStateFlow()

    private val _selectedTagIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedTagIds: StateFlow<Set<Long>> = _selectedTagIds.asStateFlow()

    private val notesMatchingSearch = _search
        .debounce { query -> if (query.isEmpty()) 0L else 300L }
        .map { NotasRepository.likePattern(it) }
        .distinctUntilChanged()
        .flatMapLatest { repository.observeNoteSummaries(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _expandedIds = MutableStateFlow<Set<Long>>(emptySet())

    val displayedNotes = combine(
        notesMatchingSearch,
        _selectedTagIds,
        _expandedIds,
    ) { notes, selected, expanded ->
        val filtered = if (selected.isEmpty()) {
            notes
        } else {
            notes.filter { n -> selected.all { tid -> n.tags.any { it.id == tid } } }
        }
        flattenNoteTree(
            items = filtered,
            idOf = { it.id },
            parentIdOf = { it.parentId },
            sortKey = { it.updatedAtMillis },
            expandedIds = expanded,
        ).map { entry -> entry.item.toCardUi(entry) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    data class NoteCardUi(
        val id: Long,
        val title: String,
        val checklist: Boolean,
        val audio: Boolean,
        val timeLabel: String,
        val tagNames: List<String>,
        val depth: Int,
        val hasChildren: Boolean,
        val expanded: Boolean,
        val hasReminder: Boolean,
    )

    data class TagChipUi(
        val tagId: Long,
        val name: String,
        val count: Int,
        val selected: Boolean,
    )

    val tagChips = combine(notesMatchingSearch, _selectedTagIds) { notes, selected ->
        val perTag = linkedMapOf<Long, Pair<String, MutableSet<Long>>>()
        for (n in notes) {
            for (t in n.tags) {
                val entry = perTag.getOrPut(t.id) { t.name to mutableSetOf() }
                entry.second.add(n.id)
            }
        }
        perTag.map { (id, pair) ->
            TagChipUi(
                tagId = id,
                name = pair.first,
                count = pair.second.size,
                selected = id in selected,
            )
        }.sortedBy { it.name.lowercase(Locale.getDefault()) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onSearchChange(value: String) {
        _search.value = value
    }

    fun toggleTagFilter(tagId: Long) {
        val cur = _selectedTagIds.value.toMutableSet()
        if (tagId in cur) cur.remove(tagId) else cur.add(tagId)
        _selectedTagIds.value = cur
    }

    fun setSelectedTagIds(tagIds: Set<Long>) {
        _selectedTagIds.value = tagIds
    }

    fun toggleExpanded(noteId: Long) {
        val cur = _expandedIds.value.toMutableSet()
        if (noteId in cur) cur.remove(noteId) else cur.add(noteId)
        _expandedIds.value = cur
    }

    fun createNote(kind: NoteKind, onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            val id = repository.createBlankNote(kind)
            onCreated(id)
        }
    }

    fun createChildNote(parentId: Long, kind: NoteKind, onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            val id = repository.createBlankNote(kind, parentId)
            _expandedIds.value = _expandedIds.value + parentId
            onCreated(id)
        }
    }

    fun deleteNote(noteId: Long) {
        viewModelScope.launch {
            repository.deleteNote(noteId)
        }
    }

    fun performExport(destinationUri: Uri) {
        viewModelScope.launch {
            try {
                repository.exportAllNotesToZip(destinationUri)
                _backupFeedback.send(BackupFeedback.ExportOk)
            } catch (_: Exception) {
                _backupFeedback.send(BackupFeedback.ExportFail)
            }
        }
    }

    fun performImport(sourceUri: Uri) {
        viewModelScope.launch {
            try {
                val count = repository.importNotesFromZip(sourceUri)
                _backupFeedback.send(BackupFeedback.ImportOk(count))
            } catch (_: Exception) {
                _backupFeedback.send(BackupFeedback.ImportFail)
            }
        }
    }

    fun openDocumentAsNote(sourceUri: Uri, onOpened: (Long) -> Unit = {}) {
        viewModelScope.launch {
            try {
                val noteId = repository.importNoteFromTextFile(sourceUri)
                _backupFeedback.send(BackupFeedback.OpenDocumentOk(noteId))
                onOpened(noteId)
            } catch (_: Exception) {
                _backupFeedback.send(BackupFeedback.OpenDocumentFail)
            }
        }
    }

    companion object {
        fun factory(repository: NotasRepository) = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass.isAssignableFrom(HomeViewModel::class.java))
                @Suppress("UNCHECKED_CAST")
                return HomeViewModel(repository) as T
            }
        }
    }
}

private fun NoteSummary.toCardUi(entry: NoteTreeEntry<NoteSummary>): HomeViewModel.NoteCardUi =
    HomeViewModel.NoteCardUi(
        id = id,
        title = title,
        checklist = NoteKind.fromStored(kind) == NoteKind.CHECKLIST,
        audio = NoteKind.fromStored(kind) == NoteKind.AUDIO,
        timeLabel = noteTimestampLabel(createdAtMillis, updatedAtMillis),
        tagNames = tags.map { it.name },
        depth = entry.depth,
        hasChildren = entry.hasChildren,
        expanded = entry.expanded,
        hasReminder = hasReminder,
    )
