package com.bdavidgm.notasrec.ui.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.FormatStrikethrough
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.NoteAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.bdavidgm.notasrec.R
import com.bdavidgm.notasrec.ui.theme.Celeste
import com.bdavidgm.notasrec.ui.theme.CelesteOscuro
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
    quoteSelected: Boolean,
    onInsertPhoto: () -> Unit,
    onInsertNoteLink: () -> Unit,
    onToggleQuote: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showLinkDialog by remember { mutableStateOf(false) }
    var headingLevel by remember { mutableIntStateOf(0) }
    val placeholderLink = stringResource(R.string.markdown_placeholder_link)
    val editor = state
    val cursor = editor?.selection
    LaunchedEffect(editor, cursor) {
        val richState = editor ?: return@LaunchedEffect
        val selection = cursor ?: return@LaunchedEffect
        if (!selection.collapsed) return@LaunchedEffect
        if (isAtLineStart(richState, selection.min)) return@LaunchedEffect
        headingLevel = headingLevelOf(richState.getSpanStyle(TextRange(selection.min)))
    }

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
            HeadingLevelButton(
                level = headingLevel,
                onLevelSelected = { level ->
                    headingLevel = level
                    state?.let { applyHeading(it, level) }
                },
            )
            FormatIconButton(
                imageVector = Icons.Filled.FormatQuote,
                contentDescription = stringResource(R.string.cd_format_quote),
                selected = quoteSelected,
                onClick = onToggleQuote,
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

/**
 * Tamaños que compose-rich-editor traduce a `#`…`######` al guardar Markdown:
 * negrita y tamaño en `em`, de mayor a menor.
 */
private val headingStyles: List<SpanStyle> = listOf(
    SpanStyle(fontSize = 2.em, fontWeight = FontWeight.Bold),
    SpanStyle(fontSize = 1.5.em, fontWeight = FontWeight.Bold),
    SpanStyle(fontSize = 1.17.em, fontWeight = FontWeight.Bold),
    SpanStyle(fontSize = 1.12.em, fontWeight = FontWeight.Bold),
    SpanStyle(fontSize = 0.83.em, fontWeight = FontWeight.Bold),
    SpanStyle(fontSize = 0.75.em, fontWeight = FontWeight.Bold),
)

/** Tamaños visibles en el menú, distintos entre sí para comparar de un vistazo. */
private val headingMenuSizes: List<TextUnit> = listOf(
    26.sp, 22.sp, 19.sp, 17.sp, 15.sp, 13.sp,
)

@Composable
private fun HeadingLevelButton(
    level: Int,
    onLevelSelected: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val label = if (level in 1..6) "H$level" else "H#"
    val description = stringResource(R.string.cd_format_heading)
    Box {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.semantics { contentDescription = description },
        ) {
            Text(
                text = label,
                color = NegroTexto,
                fontWeight = FontWeight.Bold,
                fontSize = if (level in 1..6) 15.sp else 16.sp,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = Celeste,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, Color.White),
        ) {
            headingMenuSizes.forEachIndexed { index, size ->
                val itemLevel = index + 1
                DropdownMenuItem(
                    text = {
                        Text(
                            text = "H$itemLevel",
                            color = NegroTexto,
                            fontWeight = FontWeight.Bold,
                            fontSize = size,
                        )
                    },
                    onClick = {
                        expanded = false
                        onLevelSelected(itemLevel)
                    },
                    colors = MenuDefaults.itemColors(textColor = NegroTexto),
                )
            }
        }
    }
}

private fun isAtLineStart(state: RichTextState, cursor: Int): Boolean {
    if (cursor <= 0) return true
    val text = state.annotatedString.text
    if (text.getOrNull(cursor - 1) == '\n') return true
    val styles = state.annotatedString.paragraphStyles
    if (styles.isEmpty()) return false
    val paragraph = styles.lastOrNull { cursor >= it.start && cursor < it.end }
        ?: styles.lastOrNull { cursor == it.end }
    return paragraph != null && cursor == paragraph.start
}

/** 0 si el carácter no es un título H1–H6. */
private fun headingLevelOf(style: SpanStyle): Int {
    if ((style.fontWeight?.weight ?: 400) <= 400) return 0
    val size = style.fontSize
    return when (size.type) {
        TextUnitType.Em -> when {
            size >= 2.em -> 1
            size >= 1.5.em -> 2
            size >= 1.17.em -> 3
            size >= 1.12.em -> 4
            size >= 0.83.em -> 5
            size >= 0.75.em -> 6
            else -> 0
        }
        TextUnitType.Sp -> when {
            size.value >= 32f -> 1
            size.value >= 24f -> 2
            size.value >= 18.72f -> 3
            size.value >= 17.92f -> 4
            size.value >= 13.28f -> 5
            size.value >= 12f -> 6
            else -> 0
        }
        else -> 0
    }
}

private fun applyHeading(state: RichTextState, level: Int) {
    val style = headingStyles.getOrNull(level - 1) ?: return
    val selection = state.selection
    if (selection.collapsed) {
        // Solo el estilo de lo que se va a escribir; el texto ya existente no cambia.
        headingStyles.forEach { state.removeSpanStyle(it) }
        state.addSpanStyle(style)
    } else {
        headingStyles.forEach { state.removeSpanStyle(it, selection) }
        state.addSpanStyle(style, selection)
    }
}

@Composable
private fun FormatIconButton(
    imageVector: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    selected: Boolean = false,
) {
    IconButton(
        onClick = onClick,
        modifier = if (selected) Modifier.background(CelesteOscuro, CircleShape) else Modifier,
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = if (selected) Color.White else NegroTexto,
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
