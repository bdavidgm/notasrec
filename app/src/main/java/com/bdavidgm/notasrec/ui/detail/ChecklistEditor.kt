package com.bdavidgm.notasrec.ui.detail

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.bdavidgm.notasrec.R
import com.bdavidgm.notasrec.ui.theme.CelesteOscuro
import com.bdavidgm.notasrec.ui.theme.NegroTexto
import com.bdavidgm.notasrec.ui.util.ChecklistLine
import com.bdavidgm.notasrec.ui.util.encodeChecklist
import com.bdavidgm.notasrec.ui.util.parseChecklist
import kotlinx.coroutines.delay

@Composable
fun ChecklistEditor(
    viewModel: DetailViewModel,
    isEditing: Boolean,
) {
    val items = remember {
        mutableStateListOf<ChecklistLine>().apply {
            addAll(parseChecklist(viewModel.draftContent.value))
        }
    }
    val userEdited = remember { mutableStateOf(false) }
    val focusRequesters = remember(items.size) {
        List(items.size) { FocusRequester() }
    }
    var pendingFocus by remember { mutableStateOf<Int?>(null) }
    var textGeneration by remember { mutableIntStateOf(0) }

    fun encoded(): String = encodeChecklist(items.toList())

    DisposableEffect(viewModel) {
        viewModel.setBodySnapshotProvider(::encoded)
        onDispose {
            if (userEdited.value) {
                viewModel.updateDraftContent(encoded())
            }
            viewModel.setBodySnapshotProvider(null)
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.draftContent.collect { remote ->
            if (userEdited.value) return@collect
            val parsed = parseChecklist(remote)
            if (parsed == items.toList()) return@collect
            items.clear()
            items.addAll(parsed)
        }
    }

    LaunchedEffect(textGeneration) {
        if (textGeneration == 0) return@LaunchedEffect
        delay(350)
        viewModel.commitDraftContent(encoded())
    }

    LaunchedEffect(pendingFocus, items.size) {
        val index = pendingFocus ?: return@LaunchedEffect
        pendingFocus = null
        if (index in focusRequesters.indices) {
            runCatching { focusRequesters[index].requestFocus() }
        }
    }

    fun publishText() {
        textGeneration++
    }

    fun publishNow() {
        viewModel.commitDraftContent(encoded())
    }

    items.forEachIndexed { index, line ->
        ChecklistRow(
            line = line,
            isEditing = isEditing,
            focusRequester = focusRequesters[index],
            onCheckedChange = { checked ->
                userEdited.value = true
                items[index] = line.copy(checked = checked)
                publishNow()
            },
            onTextChange = { text ->
                userEdited.value = true
                items[index] = line.copy(text = text)
                publishText()
            },
            onRemove = {
                userEdited.value = true
                if (items.size == 1) {
                    items[0] = ChecklistLine(text = "", checked = false)
                } else {
                    items.removeAt(index)
                }
                publishNow()
            },
        )
    }

    if (isEditing) {
        Spacer(Modifier.height(4.dp))
        TextButton(
            onClick = {
                userEdited.value = true
                items.add(ChecklistLine(text = "", checked = false))
                pendingFocus = items.lastIndex
                publishNow()
            },
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                tint = CelesteOscuro,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = stringResource(R.string.checklist_add_line),
                color = CelesteOscuro,
            )
        }
    }
}

@Composable
private fun ChecklistRow(
    line: ChecklistLine,
    isEditing: Boolean,
    focusRequester: FocusRequester,
    onCheckedChange: (Boolean) -> Unit,
    onTextChange: (String) -> Unit,
    onRemove: () -> Unit,
) {
    val textStyle: TextStyle = MaterialTheme.typography.bodyLarge.copy(
        color = NegroTexto.copy(alpha = if (line.checked) 0.55f else 1f),
        textDecoration = if (line.checked) TextDecoration.LineThrough else TextDecoration.None,
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Checkbox(
            checked = line.checked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(
                checkedColor = CelesteOscuro,
                uncheckedColor = CelesteOscuro,
                checkmarkColor = MaterialTheme.colorScheme.surface,
            ),
        )
        BasicTextField(
            value = line.text,
            onValueChange = onTextChange,
            modifier = Modifier
                .weight(1f)
                .padding(top = 12.dp, bottom = 8.dp)
                .focusRequester(focusRequester),
            readOnly = !isEditing,
            enabled = isEditing,
            textStyle = textStyle,
            cursorBrush = SolidColor(NegroTexto),
            decorationBox = { inner ->
                if (isEditing && line.text.isEmpty()) {
                    Text(
                        text = stringResource(R.string.checklist_item_hint),
                        style = MaterialTheme.typography.bodyLarge,
                        color = NegroTexto.copy(alpha = 0.35f),
                    )
                }
                inner()
            },
        )
        if (isEditing) {
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.cd_remove_checklist_item),
                    tint = NegroTexto.copy(alpha = 0.55f),
                )
            }
        }
    }
}
