package com.bdavidgm.notasrec.ui.reminders

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bdavidgm.notasrec.R
import com.bdavidgm.notasrec.data.NoteKind
import com.bdavidgm.notasrec.ui.components.NotasScaffold
import com.bdavidgm.notasrec.ui.theme.Celeste
import com.bdavidgm.notasrec.ui.theme.CelesteOscuro
import com.bdavidgm.notasrec.ui.util.formatNoteInstant

@Composable
fun RemindersScreen(
    viewModel: RemindersViewModel,
    navigationIcon: @Composable () -> Unit,
    onOpenNote: (Long) -> Unit,
) {
    val items by viewModel.items.collectAsStateWithLifecycle()

    NotasScaffold(
        title = stringResource(R.string.reminders_title),
        navigationIcon = navigationIcon,
    ) { padding ->
        if (items.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.reminders_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                    modifier = Modifier.padding(24.dp),
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(12.dp),
            ) {
                items(items, key = { it.noteId }) { item ->
                    ReminderListItem(
                        item = item,
                        onOpenNote = onOpenNote,
                    )
                }
            }
        }
    }
}

@Composable
private fun ReminderListItem(
    item: RemindersViewModel.ReminderItemUi,
    onOpenNote: (Long) -> Unit,
) {
    val titleText = item.title.ifBlank { stringResource(R.string.untitled_note) }
    val (kindIcon, kindCd) = kindIconAndDescription(item.kind)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, Celeste.copy(alpha = 0.55f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = { onOpenNote(item.noteId) })
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = kindIcon,
                    contentDescription = stringResource(kindCd),
                    tint = CelesteOscuro,
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .size(20.dp),
                )
                Text(
                    text = titleText,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            if (item.withSound) {
                val soundLabel = item.soundName?.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.reminder_sound_default)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.VolumeUp,
                        contentDescription = null,
                        tint = CelesteOscuro,
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .size(16.dp),
                    )
                    Text(
                        text = stringResource(R.string.reminders_sound, soundLabel),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 6.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Notifications,
                    contentDescription = stringResource(R.string.cd_reminder),
                    tint = CelesteOscuro,
                    modifier = Modifier
                        .padding(end = 6.dp)
                        .size(16.dp),
                )
                Text(
                    text = formatNoteInstant(item.reminderAtMillis),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                )
            }
        }
    }
}

private fun kindIconAndDescription(kind: NoteKind): Pair<ImageVector, Int> =
    when (kind) {
        NoteKind.CHECKLIST -> Icons.Filled.CheckBox to R.string.cd_checklist_note
        NoteKind.AUDIO -> Icons.Filled.Mic to R.string.cd_audio_note
        NoteKind.TEXT -> Icons.AutoMirrored.Filled.Notes to R.string.cd_text_note
    }
