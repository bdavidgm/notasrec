package com.bdavidgm.notasrec.ui.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bdavidgm.notasrec.R
import com.bdavidgm.notasrec.data.NoteLinkCandidate
import com.bdavidgm.notasrec.ui.theme.Celeste
import com.bdavidgm.notasrec.ui.theme.NegroTexto
import com.bdavidgm.notasrec.ui.util.NOTE_LINK_BOUNDARY
import com.bdavidgm.notasrec.ui.util.noteLinkUrl
import com.mohamedrejeb.richeditor.model.RichSpanStyle
import com.mohamedrejeb.richeditor.model.RichTextState

/**
 * Busca una nota e inserta `[título](notas://nota/<uid>)` en el bloque con foco.
 */
@Composable
internal fun LinkNoteDialog(
    viewModel: DetailViewModel,
    state: RichTextState,
    onDismiss: () -> Unit,
) {
    val query by viewModel.linkSearch.collectAsStateWithLifecycle()
    val candidates by viewModel.linkCandidates.collectAsStateWithLifecycle()
    val untitled = stringResource(R.string.untitled_note)

    DisposableEffect(viewModel) {
        onDispose { viewModel.clearLinkSearch() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_link_note_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = viewModel::setLinkSearch,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(R.string.field_link_note_search)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Celeste,
                        unfocusedBorderColor = Celeste,
                        cursorColor = NegroTexto,
                    ),
                )
                if (candidates.isEmpty()) {
                    Text(
                        text = stringResource(R.string.link_note_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = NegroTexto.copy(alpha = 0.7f),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp),
                    ) {
                        items(candidates, key = { it.id }) { candidate ->
                            LinkNoteRow(
                                candidate = candidate,
                                fallbackTitle = untitled,
                                onClick = {
                                    insertNoteLink(state, candidate, untitled)
                                    onDismiss()
                                },
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun LinkNoteRow(
    candidate: NoteLinkCandidate,
    fallbackTitle: String,
    onClick: () -> Unit,
) {
    Text(
        text = candidate.title.ifBlank { fallbackTitle },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = (candidate.depth * 16).dp, top = 12.dp, bottom = 12.dp),
        style = MaterialTheme.typography.bodyLarge,
        color = NegroTexto,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

private fun insertNoteLink(
    state: RichTextState,
    candidate: NoteLinkCandidate,
    fallbackTitle: String,
) {
    val url = noteLinkUrl(candidate.uid)
    val selectedText = state.annotatedString.text
        .substring(state.selection.min, state.selection.max)
    val visibleText = selectedText.ifBlank { candidate.title.ifBlank { fallbackTitle } }

    // Se añade el límite dentro del enlace y después se le retira el estilo.
    // Así nunca insertamos texto directamente en el borde problemático del link.
    if (!state.selection.collapsed) state.removeSelectedText()
    state.addLink(text = visibleText + NOTE_LINK_BOUNDARY, url = url)
    val boundaryEnd = state.selection.min
    val boundaryRange = TextRange(boundaryEnd - 1, boundaryEnd)
    state.removeRichSpan(
        spanStyle = RichSpanStyle.Link(url),
        textRange = boundaryRange,
    )
    state.selection = TextRange(boundaryEnd)
}
