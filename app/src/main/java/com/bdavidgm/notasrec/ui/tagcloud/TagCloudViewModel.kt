package com.bdavidgm.notasrec.ui.tagcloud

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.bdavidgm.notasrec.data.NotasRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class TagCloudViewModel(
    repository: NotasRepository,
) : ViewModel() {

    data class TagCloudItem(
        val tagId: Long,
        val name: String,
        val noteCount: Int,
        val selected: Boolean,
    )

    private val _selectedTagIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedTagIds: StateFlow<Set<Long>> = _selectedTagIds.asStateFlow()

    private val allNotes = repository.observeNoteSummaries("%")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val tags = combine(allNotes, _selectedTagIds) { notes, selected ->
        val perTag = linkedMapOf<Long, Pair<String, MutableSet<Long>>>()
        for (n in notes) {
            for (t in n.tags) {
                val entry = perTag.getOrPut(t.id) { t.name to mutableSetOf() }
                entry.second.add(n.id)
            }
        }
        perTag.map { (id, pair) ->
            TagCloudItem(
                tagId = id,
                name = pair.first,
                noteCount = pair.second.size,
                selected = id in selected,
            )
        }.sortedByDescending { it.noteCount }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Notas que contienen todas las etiquetas seleccionadas (AND). */
    val matchingNotesCount = combine(allNotes, _selectedTagIds) { notes, selected ->
        if (selected.isEmpty()) 0
        else notes.count { n -> selected.all { tid -> n.tags.any { it.id == tid } } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun toggleTag(tagId: Long) {
        val cur = _selectedTagIds.value.toMutableSet()
        if (tagId in cur) cur.remove(tagId) else cur.add(tagId)
        _selectedTagIds.value = cur
    }

    fun clearSelection() {
        _selectedTagIds.value = emptySet()
    }

    companion object {
        fun factory(repository: NotasRepository) = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass.isAssignableFrom(TagCloudViewModel::class.java))
                @Suppress("UNCHECKED_CAST")
                return TagCloudViewModel(repository) as T
            }
        }
    }
}
