package com.bdavidgm.notasrec.ui.detail

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.FormatStrikethrough
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.NoteAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bdavidgm.notasrec.R
import com.bdavidgm.notasrec.ui.theme.Celeste
import com.bdavidgm.notasrec.ui.theme.NegroTexto
import com.bdavidgm.notasrec.ui.util.NOTE_LINK_BOUNDARY
import com.mohamedrejeb.richeditor.model.RichSpanStyle
import com.mohamedrejeb.richeditor.model.RichTextState

@Composable
fun ContentModeSelector(
    mode: ContentDisplayMode,
    onModeChange: (ContentDisplayMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SegmentedButtonDefaults.colors(
        activeContainerColor = Celeste,
        activeContentColor = NegroTexto,
        activeBorderColor = Celeste,
        inactiveContainerColor = Celeste.copy(alpha = 0.18f),
        inactiveContentColor = NegroTexto,
        inactiveBorderColor = Celeste,
    )
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        SegmentedButton(
            selected = mode == ContentDisplayMode.TXT,
            onClick = { onModeChange(ContentDisplayMode.TXT) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            colors = colors,
            label = { Text(stringResource(R.string.mode_txt)) },
        )
        SegmentedButton(
            selected = mode == ContentDisplayMode.MD,
            onClick = { onModeChange(ContentDisplayMode.MD) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            colors = colors,
            label = { Text(stringResource(R.string.mode_md)) },
        )
    }
}

/**
 * [state] es el bloque de texto con el foco: hasta que el usuario toca uno, los
 * botones de formato no tienen sobre qué actuar (el de la foto sí; el de enlace
 * a nota también exige un bloque enfocado).
 */
@Composable
fun RichMarkdownFormatToolbar(
    state: RichTextState?,
    onInsertPhoto: () -> Unit,
    onInsertNoteLink: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showLinkDialog by remember { mutableStateOf(false) }
    val placeholderLink = stringResource(R.string.markdown_placeholder_link)

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Celeste.copy(alpha = 0.35f),
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.Start,
        ) {
            FormatIconButton(
                imageVector = Icons.Filled.FormatBold,
                contentDescription = stringResource(R.string.cd_format_bold),
                onClick = {
                    state?.toggleSpanStyle(SpanStyle(fontWeight = FontWeight.Bold))
                },
            )
            FormatIconButton(
                imageVector = Icons.Filled.FormatItalic,
                contentDescription = stringResource(R.string.cd_format_italic),
                onClick = {
                    state?.toggleSpanStyle(SpanStyle(fontStyle = FontStyle.Italic))
                },
            )
            FormatIconButton(
                imageVector = Icons.Filled.FormatStrikethrough,
                contentDescription = stringResource(R.string.cd_format_strikethrough),
                onClick = {
                    state?.toggleSpanStyle(SpanStyle(textDecoration = TextDecoration.LineThrough))
                },
            )
            FormatIconButton(
                imageVector = Icons.Filled.FormatSize,
                contentDescription = stringResource(R.string.cd_format_heading),
                onClick = {
                    state?.toggleSpanStyle(
                        SpanStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold),
                    )
                },
            )
            FormatIconButton(
                imageVector = Icons.AutoMirrored.Filled.FormatListBulleted,
                contentDescription = stringResource(R.string.cd_format_bullet),
                onClick = { state?.toggleUnorderedList() },
            )
            FormatIconButton(
                imageVector = Icons.Filled.FormatListNumbered,
                contentDescription = stringResource(R.string.cd_format_numbered),
                onClick = { state?.toggleOrderedList() },
            )
            FormatIconButton(
                imageVector = Icons.Filled.Code,
                contentDescription = stringResource(R.string.cd_format_code),
                onClick = { state?.toggleCodeSpan() },
            )
            FormatIconButton(
                imageVector = Icons.Filled.Link,
                contentDescription = stringResource(R.string.cd_format_link),
                onClick = { if (state != null) showLinkDialog = true },
            )
            FormatIconButton(
                imageVector = Icons.Filled.NoteAlt,
                contentDescription = stringResource(R.string.cd_format_note_link),
                onClick = { if (state != null) onInsertNoteLink() },
            )
            FormatIconButton(
                imageVector = Icons.Filled.AddAPhoto,
                contentDescription = stringResource(R.string.cd_insert_photo),
                onClick = onInsertPhoto,
            )
        }
    }

    if (showLinkDialog && state != null) {
        val selected = state.annotatedString.text
            .substring(state.selection.min, state.selection.max)
        LinkInsertDialog(
            initialText = selected.ifBlank { placeholderLink },
            onDismiss = { showLinkDialog = false },
            onConfirm = { linkText, url ->
                val href = url.ifBlank { "https://" }
                insertLinkWithBoundary(
                    state = state,
                    fallbackText = linkText.ifBlank { placeholderLink },
                    url = href,
                )
                showLinkDialog = false
            },
        )
    }
}

/**
 * Inserta un enlace seguido de un carácter invisible sin estilo de enlace.
 * Evita el bug #709 de compose-rich-editor al escribir o pulsar Enter justo
 * después del enlace.
 */
private fun insertLinkWithBoundary(
    state: RichTextState,
    fallbackText: String,
    url: String,
) {
    val selectedText = state.annotatedString.text
        .substring(state.selection.min, state.selection.max)
    val visibleText = selectedText.ifBlank { fallbackText }

    if (!state.selection.collapsed) state.removeSelectedText()
    state.addLink(text = visibleText + NOTE_LINK_BOUNDARY, url = url)
    val boundaryEnd = state.selection.min
    state.removeRichSpan(
        spanStyle = RichSpanStyle.Link(url),
        textRange = TextRange(boundaryEnd - 1, boundaryEnd),
    )
    state.selection = TextRange(boundaryEnd)
}

@Composable
private fun FormatIconButton(
    imageVector: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = NegroTexto,
        )
    }
}

@Composable
private fun LinkInsertDialog(
    initialText: String,
    onDismiss: () -> Unit,
    onConfirm: (text: String, url: String) -> Unit,
) {
    var linkText by remember(initialText) { mutableStateOf(initialText) }
    var url by remember { mutableStateOf("https://") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_link_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = linkText,
                    onValueChange = { linkText = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(R.string.field_link_text)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Celeste,
                        unfocusedBorderColor = Celeste,
                        cursorColor = NegroTexto,
                    ),
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(R.string.field_link_url)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Celeste,
                        unfocusedBorderColor = Celeste,
                        cursorColor = NegroTexto,
                    ),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(linkText, url) }) {
                Text(stringResource(R.string.action_insert))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}
