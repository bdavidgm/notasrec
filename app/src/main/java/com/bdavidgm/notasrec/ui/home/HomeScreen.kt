package com.bdavidgm.notasrec.ui.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bdavidgm.notasrec.R
import com.bdavidgm.notasrec.data.NoteKind
import com.bdavidgm.notasrec.ui.components.CelesteFab
import com.bdavidgm.notasrec.ui.components.NotasScaffold
import com.bdavidgm.notasrec.ui.theme.Celeste
import com.bdavidgm.notasrec.ui.theme.CelesteClaro
import com.bdavidgm.notasrec.ui.theme.CelesteOscuro
import com.bdavidgm.notasrec.ui.theme.NegroTexto
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenNote: (Long) -> Unit,
    onOpenDrawer: () -> Unit,
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        if (uri != null) viewModel.performExport(uri)
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) viewModel.performImport(uri)
    }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            viewModel.openDocumentAsNote(uri, onOpenNote)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.backupFeedback.collect { feedback ->
            val msg = when (feedback) {
                BackupFeedback.ExportOk -> context.getString(R.string.snackbar_export_ok)
                BackupFeedback.ExportFail -> context.getString(R.string.snackbar_export_error)
                is BackupFeedback.ImportOk -> context.getString(
                    R.string.snackbar_import_ok,
                    feedback.noteCount,
                )
                BackupFeedback.ImportFail -> context.getString(R.string.snackbar_import_error)
                is BackupFeedback.OpenDocumentOk ->
                    context.getString(R.string.snackbar_open_document_ok)
                BackupFeedback.OpenDocumentFail ->
                    context.getString(R.string.snackbar_open_document_error)
            }
            snackbarHostState.showSnackbar(msg)
        }
    }

    var overflowOpen by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<PendingNoteDelete?>(null) }
    var addMenuOpen by remember { mutableStateOf(false) }
    val notesListState = rememberLazyListState()
    val hideNewNoteFab by remember {
        derivedStateOf { notesListState.isScrolledToEnd() }
    }
    LaunchedEffect(hideNewNoteFab) {
        if (hideNewNoteFab) addMenuOpen = false
    }

    val onRequestDelete = remember {
        { noteId: Long, titleForDialog: String, hasChildren: Boolean ->
            pendingDelete = PendingNoteDelete(noteId, titleForDialog, hasChildren)
        }
    }
    val onOpenNoteStable = remember(onOpenNote) { onOpenNote }

    pendingDelete?.let { pending ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.dialog_delete_note_title)) },
            text = {
                Text(
                    stringResource(
                        if (pending.hasChildren) {
                            R.string.dialog_delete_note_message_with_children
                        } else {
                            R.string.dialog_delete_note_message
                        },
                        pending.title,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteNote(pending.noteId)
                        pendingDelete = null
                    },
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    NotasScaffold(
        title = stringResource(R.string.app_name),
        snackbarHostState = snackbarHostState,
        navigationIcon = {
            IconButton(onClick = onOpenDrawer) {
                Icon(
                    imageVector = Icons.Filled.Menu,
                    contentDescription = stringResource(R.string.cd_open_drawer),
                    tint = NegroTexto,
                )
            }
        },
        actions = {
            Box {
                IconButton(onClick = { overflowOpen = true }) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.cd_overflow_menu),
                        tint = NegroTexto,
                    )
                }
                DropdownMenu(
                    expanded = overflowOpen,
                    onDismissRequest = { overflowOpen = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_open_document)) },
                        onClick = {
                            overflowOpen = false
                            openDocumentLauncher.launch(
                                arrayOf(
                                    "text/plain",
                                    "text/markdown",
                                    "text/x-markdown",
                                ),
                            )
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_export)) },
                        onClick = {
                            overflowOpen = false
                            val fmt = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault())
                            exportLauncher.launch("notas_${fmt.format(Date())}.zip")
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_import)) },
                        onClick = {
                            overflowOpen = false
                            importLauncher.launch(
                                arrayOf(
                                    "application/zip",
                                    "application/x-zip-compressed",
                                ),
                            )
                        },
                    )
                }
            }
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = !hideNewNoteFab,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
            ) {
                NewNoteSpeedDial(
                    expanded = addMenuOpen,
                    onToggle = { addMenuOpen = !addMenuOpen },
                    onCreateTextNote = {
                        addMenuOpen = false
                        viewModel.createNote(NoteKind.TEXT, onOpenNoteStable)
                    },
                    onCreateChecklist = {
                        addMenuOpen = false
                        viewModel.createNote(NoteKind.CHECKLIST, onOpenNoteStable)
                    },
                    onCreateAudioNote = {
                        addMenuOpen = false
                        viewModel.createNote(NoteKind.AUDIO, onOpenNoteStable)
                    },
                )
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            HomeSearchField(viewModel)
            HomeTagChipsRow(viewModel)
            HomeNotesList(
                viewModel = viewModel,
                listState = notesListState,
                onOpenNote = onOpenNoteStable,
                onRequestDelete = onRequestDelete,
                onToggleExpanded = viewModel::toggleExpanded,
                onCreateChild = { parentId, kind ->
                    viewModel.createChildNote(parentId, kind, onOpenNoteStable)
                },
            )
        }
            if (addMenuOpen) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.08f))
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                            onClick = { addMenuOpen = false },
                        ),
                )
            }
        }
    }
}

@Composable
private fun NewNoteSpeedDial(
    expanded: Boolean,
    onToggle: () -> Unit,
    onCreateTextNote: () -> Unit,
    onCreateChecklist: () -> Unit,
    onCreateAudioNote: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(expandFrom = Alignment.Bottom),
            exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom),
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                NoteTypeFab(
                    label = stringResource(R.string.fab_new_audio_note),
                    icon = Icons.Filled.Mic,
                    contentDescription = stringResource(R.string.cd_new_audio_note),
                    onClick = onCreateAudioNote,
                )
                NoteTypeFab(
                    label = stringResource(R.string.fab_new_checklist_note),
                    icon = Icons.Filled.CheckBox,
                    contentDescription = stringResource(R.string.cd_new_checklist_note),
                    onClick = onCreateChecklist,
                )
                NoteTypeFab(
                    label = stringResource(R.string.fab_new_text_note),
                    icon = Icons.AutoMirrored.Filled.Notes,
                    contentDescription = stringResource(R.string.cd_new_text_note),
                    onClick = onCreateTextNote,
                )
            }
        }
        val mainDescription = stringResource(
            if (expanded) R.string.cd_close_note_type else R.string.cd_choose_note_type,
        )
        CelesteFab(
            onClick = onToggle,
            modifier = Modifier.semantics { contentDescription = mainDescription },
        ) {
            Text(
                text = stringResource(R.string.fab_new_note),
                style = MaterialTheme.typography.titleLarge,
                color = NegroTexto,
            )
        }
    }
}

@Composable
private fun NoteTypeFab(
    label: String,
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 3.dp,
        ) {
            Text(
                text = label,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelLarge,
                color = NegroTexto,
            )
        }
        SmallFloatingActionButton(
            onClick = onClick,
            containerColor = Celeste,
            contentColor = NegroTexto,
            elevation = FloatingActionButtonDefaults.elevation(),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
            )
        }
    }
}

@Composable
private fun HomeSearchField(viewModel: HomeViewModel) {
    val search by viewModel.searchQuery.collectAsStateWithLifecycle()
    OutlinedTextField(
        value = search,
        onValueChange = viewModel::onSearchChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        singleLine = true,
        placeholder = { Text(stringResource(R.string.search_hint)) },
        shape = RoundedCornerShape(28.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Celeste,
            unfocusedBorderColor = Celeste,
            cursorColor = NegroTexto,
            focusedLabelColor = NegroTexto,
        ),
    )
}

@Composable
private fun HomeTagChipsRow(viewModel: HomeViewModel) {
    val tagChips by viewModel.tagChips.collectAsStateWithLifecycle()
    val onToggle = remember(viewModel) { viewModel::toggleTagFilter }

    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(tagChips, key = { it.tagId }) { chip ->
            HomeTagChip(
                chip = chip,
                onToggle = onToggle,
            )
        }
    }
}

@Composable
private fun HomeTagChip(
    chip: HomeViewModel.TagChipUi,
    onToggle: (Long) -> Unit,
) {
    ElevatedButton(
        onClick = { onToggle(chip.tagId) },
        colors = ButtonDefaults.elevatedButtonColors(
            containerColor = if (chip.selected) CelesteOscuro else CelesteClaro,
            contentColor = NegroTexto,
        ),
    ) {
        val label = stringResource(R.string.tag_chip_label, chip.name, chip.count)
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun HomeNotesList(
    viewModel: HomeViewModel,
    listState: LazyListState,
    onOpenNote: (Long) -> Unit,
    onRequestDelete: (noteId: Long, titleForDialog: String, hasChildren: Boolean) -> Unit,
    onToggleExpanded: (Long) -> Unit,
    onCreateChild: (parentId: Long, kind: NoteKind) -> Unit,
) {
    val notes by viewModel.displayedNotes.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(
            items = notes,
            key = { it.id },
            contentType = { "note" },
        ) { item ->
            NoteListItem(
                item = item,
                onOpenNote = onOpenNote,
                onRequestDelete = onRequestDelete,
                onToggleExpanded = onToggleExpanded,
                onCreateChild = onCreateChild,
            )
        }
    }
}

@Composable
private fun NoteListItem(
    item: HomeViewModel.NoteCardUi,
    onOpenNote: (Long) -> Unit,
    onRequestDelete: (noteId: Long, titleForDialog: String, hasChildren: Boolean) -> Unit,
    onToggleExpanded: (Long) -> Unit,
    onCreateChild: (parentId: Long, kind: NoteKind) -> Unit,
) {
    val titleText = item.title.ifBlank { stringResource(R.string.untitled_note) }
    val noteId = item.id
    var childMenuOpen by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (item.depth * 16).dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, Celeste.copy(alpha = 0.55f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (item.hasChildren) {
                IconButton(onClick = { onToggleExpanded(noteId) }) {
                    Icon(
                        imageVector = if (item.expanded) {
                            Icons.Filled.KeyboardArrowDown
                        } else {
                            Icons.AutoMirrored.Filled.KeyboardArrowRight
                        },
                        contentDescription = stringResource(
                            if (item.expanded) {
                                R.string.cd_collapse_children
                            } else {
                                R.string.cd_expand_children
                            },
                        ),
                        tint = NegroTexto,
                    )
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = { onOpenNote(noteId) })
                    .padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (item.checklist) {
                        Icon(
                            imageVector = Icons.Filled.CheckBox,
                            contentDescription = stringResource(R.string.cd_checklist_note),
                            tint = CelesteOscuro,
                            modifier = Modifier
                                .padding(end = 6.dp)
                                .size(18.dp),
                        )
                    }
                    if (item.audio) {
                        Icon(
                            imageVector = Icons.Filled.Mic,
                            contentDescription = stringResource(R.string.cd_audio_note),
                            tint = CelesteOscuro,
                            modifier = Modifier
                                .padding(end = 6.dp)
                                .size(18.dp),
                        )
                    }
                    if (item.hasReminder) {
                        Icon(
                            imageVector = Icons.Filled.Notifications,
                            contentDescription = stringResource(R.string.cd_reminder),
                            tint = CelesteOscuro,
                            modifier = Modifier
                                .padding(end = 6.dp)
                                .size(18.dp),
                        )
                    }
                    Text(
                        text = titleText,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                if (item.tagNames.isNotEmpty()) {
                    // Sin scroll anidado: una fila desplazable dentro de cada card
                    // pelea el gesto vertical y corta los frames.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                            .clipToBounds(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        item.tagNames.forEach { name ->
                            Surface(
                                color = Celeste,
                                shape = RoundedCornerShape(16.dp),
                            ) {
                                Text(
                                    text = name,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = NegroTexto,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
                Text(
                    text = item.timeLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Box {
                IconButton(onClick = { childMenuOpen = true }) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = stringResource(R.string.cd_add_child_note),
                        tint = NegroTexto,
                    )
                }
                DropdownMenu(
                    expanded = childMenuOpen,
                    onDismissRequest = { childMenuOpen = false },
                    containerColor = Color.White,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Celeste),
                ) {
                    val itemColors = MenuDefaults.itemColors(
                        textColor = NegroTexto,
                        leadingIconColor = CelesteOscuro,
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.fab_new_text_note)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Notes,
                                contentDescription = null,
                            )
                        },
                        colors = itemColors,
                        onClick = {
                            childMenuOpen = false
                            onCreateChild(noteId, NoteKind.TEXT)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.fab_new_checklist_note)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.CheckBox,
                                contentDescription = null,
                            )
                        },
                        colors = itemColors,
                        onClick = {
                            childMenuOpen = false
                            onCreateChild(noteId, NoteKind.CHECKLIST)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.fab_new_audio_note)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Mic,
                                contentDescription = null,
                            )
                        },
                        colors = itemColors,
                        onClick = {
                            childMenuOpen = false
                            onCreateChild(noteId, NoteKind.AUDIO)
                        },
                    )
                }
            }
            IconButton(
                onClick = { onRequestDelete(noteId, titleText, item.hasChildren) },
                modifier = Modifier.widthIn(min = 48.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.cd_delete_note),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

private fun LazyListState.isScrolledToEnd(): Boolean {
    val info = layoutInfo
    val last = info.visibleItemsInfo.lastOrNull() ?: return false
    val reachedLastItem = info.totalItemsCount > 0 &&
        last.index == info.totalItemsCount - 1 &&
        last.offset + last.size <= info.viewportEndOffset
    return reachedLastItem && canScrollBackward
}

private data class PendingNoteDelete(
    val noteId: Long,
    val title: String,
    val hasChildren: Boolean,
)
