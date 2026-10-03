package com.bdavidgm.notasrec.ui.detail

import android.content.ActivityNotFoundException
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewResponder
import androidx.compose.foundation.relocation.bringIntoViewResponder
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bdavidgm.notasrec.R
import com.bdavidgm.notasrec.data.NoteKind
import com.bdavidgm.notasrec.data.local.NoteEntity
import com.bdavidgm.notasrec.data.local.TagEntity
import com.bdavidgm.notasrec.ui.components.CelesteElevatedButton
import com.bdavidgm.notasrec.ui.components.NotasScaffold
import com.bdavidgm.notasrec.ui.components.TopBarTextButton
import com.bdavidgm.notasrec.ui.theme.Celeste
import com.bdavidgm.notasrec.ui.theme.CelesteOscuro
import com.bdavidgm.notasrec.ui.theme.NegroTexto
import com.bdavidgm.notasrec.ui.util.NoteCopyOptions
import com.bdavidgm.notasrec.ui.util.NoteExportFormat
import com.bdavidgm.notasrec.ui.util.buildNoteCopyText
import com.bdavidgm.notasrec.ui.util.formatNoteInstant
import com.bdavidgm.notasrec.ui.util.noteTimestampLabel
import com.bdavidgm.notasrec.ui.util.suggestedNoteExportFileName
import com.bdavidgm.notasrec.ui.util.suggestedNoteExportZipFileName
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import java.util.Locale

private fun String.toExportFormat(): NoteExportFormat? =
    runCatching { NoteExportFormat.valueOf(this) }.getOrNull()

@Composable
fun DetailScreen(
    viewModel: DetailViewModel,
    onNavigateBack: () -> Unit,
    onOpenNote: (Long) -> Unit,
) {
    val nwt by viewModel.noteWithTags.collectAsStateWithLifecycle()
    val isEditing by viewModel.isEditing.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var overflowOpen by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showCopyDialog by remember { mutableStateOf(false) }
    var showManageTags by remember { mutableStateOf(false) }
    var showReminderDialog by remember { mutableStateOf(false) }
    var copyOptions by remember { mutableStateOf(NoteCopyOptions()) }
    // rememberSaveable: al abrir el selector el proceso puede morir y, sin esto,
    // al volver pendingExportFormat sería null y la exportación no arrancaría.
    var pendingExportFormat by rememberSaveable { mutableStateOf<String?>(null) }
    var packagingFormat by rememberSaveable { mutableStateOf<String?>(null) }

    fun launchCreateDocument(
        launcher: androidx.activity.result.ActivityResultLauncher<String>,
        fileName: String,
    ) {
        try {
            launcher.launch(fileName)
        } catch (_: ActivityNotFoundException) {
            pendingExportFormat = null
            scope.launch {
                snackbarHostState.showSnackbar(
                    context.getString(R.string.snackbar_no_document_picker),
                )
            }
        }
    }

    fun launchOpenTree(
        launcher: androidx.activity.result.ActivityResultLauncher<android.net.Uri?>,
    ) {
        try {
            launcher.launch(null)
        } catch (_: ActivityNotFoundException) {
            pendingExportFormat = null
            scope.launch {
                snackbarHostState.showSnackbar(
                    context.getString(R.string.snackbar_no_document_picker),
                )
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.photoFeedback.collect { feedback ->
            val message = when (feedback) {
                NotePhotoFeedback.Error -> context.getString(R.string.snackbar_photo_error)
                NotePhotoFeedback.NoCamera -> context.getString(R.string.snackbar_no_camera)
            }
            snackbarHostState.showSnackbar(message)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.exportFeedback.collect { feedback ->
            val message = when (feedback) {
                NoteExportFeedback.Ok -> context.getString(R.string.snackbar_note_export_ok)
                NoteExportFeedback.Fail -> context.getString(R.string.snackbar_note_export_error)
            }
            snackbarHostState.showSnackbar(message)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.linkFeedback.collect {
            snackbarHostState.showSnackbar(
                context.getString(R.string.snackbar_note_link_missing),
            )
        }
    }

    LaunchedEffect(Unit) {
        viewModel.navigateToNote.collect { targetId ->
            onOpenNote(targetId)
        }
    }

    val exportTxtLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(NoteExportFormat.TXT.mimeType),
    ) { uri ->
        val format = pendingExportFormat?.toExportFormat()
        pendingExportFormat = null
        if (uri != null && format == NoteExportFormat.TXT) {
            viewModel.exportNote(uri, NoteExportFormat.TXT)
        }
    }

    val exportMdLauncher = rememberLauncherForActivityResult(
        // text/markdown falla en varios selectores; el nombre .md basta.
        contract = ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri ->
        val format = pendingExportFormat?.toExportFormat()
        pendingExportFormat = null
        if (uri != null && format == NoteExportFormat.MD) {
            viewModel.exportNote(uri, NoteExportFormat.MD)
        }
    }

    val exportZipLauncher = rememberLauncherForActivityResult(
        // application/zip tumba el selector en algunos Xiaomi; */* + nombre .zip
        // es lo que SAF acepta de forma fiable.
        contract = ActivityResultContracts.CreateDocument("*/*"),
    ) { uri ->
        val format = pendingExportFormat?.toExportFormat()
        pendingExportFormat = null
        if (uri != null && format != null) {
            viewModel.exportNoteAsZip(uri, format)
        }
    }

    val exportFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { treeUri ->
        val format = pendingExportFormat?.toExportFormat()
        pendingExportFormat = null
        if (treeUri != null && format != null) {
            viewModel.exportNoteToFolder(treeUri, format)
        }
    }

    LaunchedEffect(nwt?.note?.updatedAtMillis, isEditing) {
        val note = nwt ?: return@LaunchedEffect
        if (!isEditing) {
            viewModel.onLoadedNote(note)
        }
    }

    BackHandler(enabled = showManageTags) {
        showManageTags = false
    }

    BackHandler(enabled = isEditing && !showManageTags) {
        scope.launch {
            viewModel.saveDraftIfEditing()
            onNavigateBack()
        }
    }

    val titleBar = nwt?.note?.title?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.detail_default_title)

    if (showManageTags) {
        ManageTagsScreen(
            viewModel = viewModel,
            noteTags = nwt?.tags.orEmpty(),
            onDismiss = { showManageTags = false },
        )
        return
    }

    if (showExportDialog) {
        val dialogActionColors = ButtonDefaults.textButtonColors(contentColor = CelesteOscuro)
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text(stringResource(R.string.dialog_export_note_title)) },
            text = {
                Column {
                    TextButton(
                        onClick = {
                            showExportDialog = false
                            scope.launch {
                                if (viewModel.exportIncludesPhotos()) {
                                    packagingFormat = NoteExportFormat.TXT.name
                                } else {
                                    pendingExportFormat = NoteExportFormat.TXT.name
                                    launchCreateDocument(
                                        exportTxtLauncher,
                                        suggestedNoteExportFileName(
                                            viewModel.draftTitle.value,
                                            NoteExportFormat.TXT,
                                        ),
                                    )
                                }
                            }
                        },
                        colors = dialogActionColors,
                    ) {
                        Text(stringResource(R.string.action_export_txt))
                    }
                    TextButton(
                        onClick = {
                            showExportDialog = false
                            scope.launch {
                                if (viewModel.exportIncludesPhotos()) {
                                    packagingFormat = NoteExportFormat.MD.name
                                } else {
                                    pendingExportFormat = NoteExportFormat.MD.name
                                    launchCreateDocument(
                                        exportMdLauncher,
                                        suggestedNoteExportFileName(
                                            viewModel.draftTitle.value,
                                            NoteExportFormat.MD,
                                        ),
                                    )
                                }
                            }
                        },
                        colors = dialogActionColors,
                    ) {
                        Text(stringResource(R.string.action_export_md))
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(
                    onClick = { showExportDialog = false },
                    colors = dialogActionColors,
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    packagingFormat?.toExportFormat()?.let { format ->
        ExportPackagingDialog(
            onZip = {
                packagingFormat = null
                pendingExportFormat = format.name
                // Tras cerrar el diálogo, el siguiente frame evita choques con el
                // selector de documentos en algunos fabricantes (p. ej. Xiaomi).
                scope.launch {
                    yield()
                    launchCreateDocument(
                        exportZipLauncher,
                        suggestedNoteExportZipFileName(viewModel.draftTitle.value),
                    )
                }
            },
            onFiles = {
                packagingFormat = null
                pendingExportFormat = format.name
                scope.launch {
                    yield()
                    launchOpenTree(exportFolderLauncher)
                }
            },
            onDismiss = { packagingFormat = null },
        )
    }

    if (showCopyDialog) {
        AlertDialog(
            onDismissRequest = { showCopyDialog = false },
            title = { Text(stringResource(R.string.dialog_copy_note_title)) },
            text = {
                Column {
                    CopyOptionRow(
                        label = stringResource(R.string.copy_option_title),
                        checked = copyOptions.includeTitle,
                        onCheckedChange = { copyOptions = copyOptions.copy(includeTitle = it) },
                    )
                    CopyOptionRow(
                        label = stringResource(R.string.copy_option_body),
                        checked = copyOptions.includeBody,
                        onCheckedChange = { copyOptions = copyOptions.copy(includeBody = it) },
                    )
                    CopyOptionRow(
                        label = stringResource(R.string.copy_option_created),
                        checked = copyOptions.includeCreatedAt,
                        onCheckedChange = { copyOptions = copyOptions.copy(includeCreatedAt = it) },
                    )
                    CopyOptionRow(
                        label = stringResource(R.string.copy_option_updated),
                        checked = copyOptions.includeUpdatedAt,
                        onCheckedChange = { copyOptions = copyOptions.copy(includeUpdatedAt = it) },
                    )
                    CopyOptionRow(
                        label = stringResource(R.string.copy_option_tags),
                        checked = copyOptions.includeTags,
                        onCheckedChange = { copyOptions = copyOptions.copy(includeTags = it) },
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (!copyOptions.hasAny) {
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    context.getString(R.string.snackbar_note_copy_empty),
                                )
                            }
                            return@TextButton
                        }
                        val note = nwt?.note
                        val text = buildNoteCopyText(
                            title = viewModel.draftTitle.value,
                            createdAtMillis = note?.createdAtMillis ?: System.currentTimeMillis(),
                            updatedAtMillis = note?.updatedAtMillis ?: System.currentTimeMillis(),
                            content = viewModel.currentSharingBody(),
                            tagNames = nwt?.tags.orEmpty().map { it.name },
                            options = copyOptions,
                        )
                        clipboardManager.setText(AnnotatedString(text))
                        showCopyDialog = false
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                context.getString(R.string.snackbar_note_copy_ok),
                            )
                        }
                    },
                ) {
                    Text(stringResource(R.string.action_copy))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCopyDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (showReminderDialog) {
        val note = nwt?.note
        ReminderDialog(
            viewModel = viewModel,
            initialAtMillis = note?.reminderAtMillis,
            initialWithSound = note?.reminderWithSound == true,
            initialSoundUri = note?.reminderSoundUri,
            initialSoundName = note?.reminderSoundName,
            onDismiss = { showReminderDialog = false },
        )
    }

    NotasScaffold(
        title = titleBar,
        snackbarHostState = snackbarHostState,
        navigationIcon = {
            IconButton(
                onClick = {
                    if (isEditing) {
                        scope.launch {
                            viewModel.saveDraftIfEditing()
                            onNavigateBack()
                        }
                    } else {
                        onNavigateBack()
                    }
                },
            ) {
                Icon(
                    imageVector = Icons.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.cd_back),
                    tint = NegroTexto,
                )
            }
        },
        actions = {
            if (isEditing) {
                TopBarTextButton(
                    label = stringResource(R.string.action_done),
                    onClick = { viewModel.setEditing(false) },
                )
            } else {
                TopBarTextButton(
                    label = stringResource(R.string.action_edit),
                    onClick = { viewModel.setEditing(true) },
                )
            }
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
                        text = { Text(stringResource(R.string.action_add_reminder)) },
                        onClick = {
                            overflowOpen = false
                            showReminderDialog = true
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_manage_tags_menu)) },
                        onClick = {
                            overflowOpen = false
                            showManageTags = true
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_copy_note)) },
                        onClick = {
                            overflowOpen = false
                            copyOptions = NoteCopyOptions()
                            showCopyDialog = true
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_export_note)) },
                        onClick = {
                            overflowOpen = false
                            showExportDialog = true
                        },
                    )
                }
            }
        },
    ) { padding ->
        val note = nwt?.note
        if (note == null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.loading))
            }
            return@NotasScaffold
        }

        val layoutDirection = LocalLayoutDirection.current
        DetailNoteBody(
            viewModel = viewModel,
            note = note,
            tags = nwt?.tags.orEmpty(),
            isEditing = isEditing,
            onEditReminder = { showReminderDialog = true },
            modifier = Modifier
                .fillMaxSize()
                // El inferior lo aplica el cuerpo con ime ∪ navigationBars. Si aquí
                // también se deja la barra, al abrir el teclado queda una franja
                // vacía encima del teclado.
                .padding(
                    PaddingValues(
                        start = padding.calculateStartPadding(layoutDirection),
                        top = padding.calculateTopPadding(),
                        end = padding.calculateEndPadding(layoutDirection),
                    ),
                ),
        )
    }
}

@Composable
private fun DetailNoteBody(
    viewModel: DetailViewModel,
    note: NoteEntity,
    tags: List<TagEntity>,
    isEditing: Boolean,
    onEditReminder: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val removeTag = remember(viewModel) { viewModel::removeTag }
    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    // El rect de bring-into-view llega en el espacio del contenido (ya desplazado
    // por el scroll). Guardamos las coordenadas de la zona visible y las del
    // contenido para compararlos en coordenadas de ventana, que es lo único que
    // dice de verdad si el cursor está fuera de pantalla.
    var viewportCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var contentCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    // El editor pide traer a la vista el campo entero (alto enorme) al recibir el
    // foco; solo atendemos peticiones del tamaño del cursor.
    val maxCursorBringPx = with(density) { 64.dp.toPx() }
    val scrollMarginPx = with(density) { 12.dp.toPx() }

    Box(
        modifier = modifier
            // El máximo de teclado y barra de navegación: sumarlos deja un hueco
            // del alto de la barra justo encima del teclado.
            .windowInsetsPadding(
                WindowInsets.ime.union(WindowInsets.navigationBars).only(WindowInsetsSides.Bottom),
            )
            .padding(16.dp)
            // Fuera del scroll: es exactamente el hueco visible del editor.
            .onPlaced { viewportCoords = it },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                // Dentro del scroll y pegados: comparten coordenadas con el
                // contenido, el mismo espacio en que llega el rect del cursor.
                .onPlaced { contentCoords = it }
                .cursorFollowBringIntoView(
                    scrollState = scrollState,
                    viewport = { viewportCoords },
                    content = { contentCoords },
                    maxRequestHeightPx = maxCursorBringPx,
                    marginPx = scrollMarginPx,
                ),
        ) {
            Text(
                text = noteTimestampLabel(note.createdAtMillis, note.updatedAtMillis),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
            note.reminderAtMillis?.let { at ->
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onEditReminder)
                        .padding(vertical = 4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Notifications,
                        contentDescription = stringResource(R.string.cd_reminder),
                        tint = CelesteOscuro,
                    )
                    Text(
                        text = buildString {
                            append(stringResource(R.string.reminder_scheduled, formatNoteInstant(at)))
                            if (note.reminderWithSound) {
                                val sound = note.reminderSoundName?.takeIf { it.isNotBlank() }
                                    ?: stringResource(R.string.reminder_sound_default)
                                append(" · ")
                                append(sound)
                            }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = NegroTexto,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            val kind = NoteKind.fromStored(note.kind)
            when (kind) {
                NoteKind.CHECKLIST -> ChecklistNoteSection(viewModel = viewModel, isEditing = isEditing)
                NoteKind.AUDIO -> AudioNoteSection(viewModel = viewModel, isEditing = isEditing)
                NoteKind.TEXT -> {
                    DetailContentModeRow(viewModel)
                    Spacer(Modifier.height(12.dp))
                    if (isEditing) {
                        DraftTitleField(viewModel)
                        Spacer(Modifier.height(8.dp))
                        DraftContentEditor(viewModel = viewModel)
                    } else {
                        ReadOnlyNoteBody(viewModel)
                    }
                }
            }
            if (isEditing) {
                // Hueco extra para poder subir la última línea por encima del teclado.
                Spacer(Modifier.height(48.dp))
            }

            if (tags.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                NoteTagsRow(
                    tags = tags,
                    isEditing = isEditing,
                    onRemoveTag = removeTag,
                )
            }
        }
    }
}

/**
 * Sigue al cursor y nada más: atiende solo peticiones del tamaño del cursor y
 * desplaza lo mínimo para descubrirlo. Las del campo entero se ignoran, que son
 * las que mandaban la vista al final —o al principio— del texto al tocarlo.
 */
@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.cursorFollowBringIntoView(
    scrollState: ScrollState,
    viewport: () -> LayoutCoordinates?,
    content: () -> LayoutCoordinates?,
    maxRequestHeightPx: Float,
    marginPx: Float,
): Modifier {
    fun visibleBounds(): Rect? = viewport()?.takeIf { it.isAttached }?.boundsInWindow()
    fun attachedContent(): LayoutCoordinates? = content()?.takeIf { it.isAttached }

    return bringIntoViewResponder(
        object : BringIntoViewResponder {
            /**
             * El padre de este responder es el propio `verticalScroll`: si le
             * pasáramos el rect tal cual volvería a desplazarse por su cuenta. Le
             * decimos que lo pedido ya está en el centro del hueco visible para que
             * no mueva nada; de lo que haga falta ya se encarga [bringChildIntoView].
             */
            override fun calculateRectForParent(localRect: Rect): Rect {
                val visible = visibleBounds() ?: return localRect
                val contentCoords = attachedContent() ?: return localRect
                return Rect(contentCoords.windowToLocal(visible.center), 0f)
            }

            override suspend fun bringChildIntoView(localRect: () -> Rect?) {
                val rect = localRect() ?: return
                if (rect.height > maxRequestHeightPx) return
                val visible = visibleBounds()?.takeIf { it.height > 0f } ?: return
                val contentCoords = attachedContent() ?: return

                val top = contentCoords.localToWindow(rect.topLeft).y
                val bottom = contentCoords.localToWindow(rect.bottomLeft).y
                val delta = when {
                    bottom > visible.bottom - marginPx -> bottom - visible.bottom + marginPx
                    top < visible.top + marginPx -> top - visible.top - marginPx
                    else -> return
                }
                scrollState.scrollBy(delta.coerceIn(-visible.height, visible.height))
            }
        },
    )
}

@Composable
private fun DetailContentModeRow(viewModel: DetailViewModel) {
    val contentMode by viewModel.contentDisplayMode.collectAsStateWithLifecycle()
    ContentModeSelector(
        mode = contentMode,
        onModeChange = viewModel::setContentDisplayMode,
    )
}

@Composable
private fun ChecklistNoteSection(
    viewModel: DetailViewModel,
    isEditing: Boolean,
) {
    if (isEditing) {
        DraftTitleField(viewModel)
        Spacer(Modifier.height(8.dp))
    } else {
        val draftTitle by viewModel.draftTitle.collectAsStateWithLifecycle()
        Text(
            text = draftTitle.ifBlank { stringResource(R.string.untitled_note) },
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.height(8.dp))
    }
    ChecklistEditor(
        viewModel = viewModel,
        isEditing = isEditing,
    )
}

@Composable
private fun DraftTitleField(viewModel: DetailViewModel) {
    val draftTitle by viewModel.draftTitle.collectAsStateWithLifecycle()
    OutlinedTextField(
        value = draftTitle,
        onValueChange = viewModel::updateDraftTitle,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.field_title)) },
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Celeste,
            unfocusedBorderColor = Celeste,
            cursorColor = NegroTexto,
        ),
    )
}

@Composable
private fun DraftContentEditor(viewModel: DetailViewModel) {
    // Ojo: aquí NO se colecta draftContent. Si se leyera en composición, cada
    // pulsación recompondría el editor completo (y su toolbar).
    val contentMode by viewModel.contentDisplayMode.collectAsStateWithLifecycle()

    when (contentMode) {
        ContentDisplayMode.TXT -> PlainTextDraftEditor(viewModel)
        ContentDisplayMode.MD -> RichMarkdownDraftEditor(viewModel)
    }
}

@Composable
private fun PlainTextDraftEditor(viewModel: DetailViewModel) {
    // userEdited se activa también al recibir el foco: desde ese momento la
    // selección es del usuario y ninguna recarga vuelve a llevarla al inicio.
    var userEdited by remember { mutableStateOf(false) }
    var localField by remember {
        mutableStateOf(TextFieldValue(viewModel.draftContent.value, TextRange.Zero))
    }
    val focusRequester = remember { FocusRequester() }

    // Al abrir la edición el cursor se coloca en el primer carácter del cuerpo.
    LaunchedEffect(Unit) {
        if (localField.text.isBlank()) return@LaunchedEffect
        // En la primera composición el nodo puede no estar colocado todavía.
        if (runCatching { focusRequester.requestFocus() }.isFailure) {
            withFrameNanos { }
            runCatching { focusRequester.requestFocus() }
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.draftContent.collect { remote ->
            if (userEdited || remote == localField.text) return@collect
            localField = TextFieldValue(remote, TextRange.Zero)
        }
    }

    // El campo pinta en local. El borrador del ViewModel se actualiza al dejar
    // de escribir, para no despertar el autoguardado en cada tecla.
    LaunchedEffect(localField.text, userEdited) {
        if (!userEdited) return@LaunchedEffect
        delay(400)
        viewModel.updateDraftContent(localField.text)
    }

    DisposableEffect(viewModel) {
        viewModel.setBodySnapshotProvider { localField.text }
        onDispose {
            viewModel.updateDraftContent(localField.text)
            viewModel.setBodySnapshotProvider(null)
        }
    }

    Text(
        text = stringResource(R.string.field_body),
        style = MaterialTheme.typography.labelMedium,
        color = NegroTexto.copy(alpha = 0.75f),
    )
    Spacer(Modifier.height(4.dp))
    BasicTextField(
        value = localField,
        onValueChange = { updated ->
            userEdited = true
            localField = updated
        },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 160.dp)
            .focusRequester(focusRequester)
            .onFocusChanged { if (it.isFocused) userEdited = true },
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = NegroTexto),
        cursorBrush = SolidColor(NegroTexto),
    )
}

/** Las fotos de la nota no caben en un solo .txt/.md: o van en un ZIP o al lado. */
@Composable
private fun ExportPackagingDialog(
    onZip: () -> Unit,
    onFiles: () -> Unit,
    onDismiss: () -> Unit,
) {
    val dialogActionColors = ButtonDefaults.textButtonColors(contentColor = CelesteOscuro)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_export_photos_title)) },
        text = {
            Column {
                Text(stringResource(R.string.dialog_export_photos_message))
                Spacer(Modifier.height(12.dp))
                TextButton(
                    onClick = onZip,
                    modifier = Modifier.fillMaxWidth(),
                    colors = dialogActionColors,
                ) {
                    Text(stringResource(R.string.action_export_zip))
                }
                TextButton(
                    onClick = onFiles,
                    modifier = Modifier.fillMaxWidth(),
                    colors = dialogActionColors,
                ) {
                    Text(stringResource(R.string.action_export_files))
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = dialogActionColors,
            ) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun ReadOnlyNoteBody(viewModel: DetailViewModel) {
    val draftTitle by viewModel.draftTitle.collectAsStateWithLifecycle()
    val draftContent by viewModel.draftContent.collectAsStateWithLifecycle()
    val contentMode by viewModel.contentDisplayMode.collectAsStateWithLifecycle()
    val followLink = remember(viewModel) { viewModel::followNoteLink }

    Text(
        text = draftTitle.ifBlank { stringResource(R.string.untitled_note) },
        style = MaterialTheme.typography.titleLarge,
    )
    Spacer(Modifier.height(8.dp))
    when (contentMode) {
        ContentDisplayMode.TXT -> {
            Text(
                text = draftContent.ifBlank { stringResource(R.string.empty_body_hint) },
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        ContentDisplayMode.MD -> {
            ReadOnlyRichMarkdownBody(
                markdown = draftContent,
                onNoteLink = followLink,
            )
        }
    }
}

@Composable
private fun NoteTagsRow(
    tags: List<TagEntity>,
    isEditing: Boolean,
    onRemoveTag: (Long) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        tags.forEach { tag ->
            NoteTagChip(
                tag = tag,
                isEditing = isEditing,
                onRemoveTag = onRemoveTag,
            )
        }
    }
}

@Composable
private fun NoteTagChip(
    tag: TagEntity,
    isEditing: Boolean,
    onRemoveTag: (Long) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            color = Celeste,
            shape = RoundedCornerShape(20.dp),
        ) {
            Text(
                text = tag.name,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelLarge,
                color = NegroTexto,
            )
        }
        if (isEditing) {
            IconButton(onClick = { onRemoveTag(tag.id) }) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.cd_remove_tag),
                    tint = NegroTexto,
                )
            }
        }
    }
}

@Composable
private fun ManageTagsScreen(
    viewModel: DetailViewModel,
    noteTags: List<TagEntity>,
    onDismiss: () -> Unit,
) {
    val allTags by viewModel.allTags.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var pendingTagNames by remember { mutableStateOf<List<String>>(emptyList()) }
    // null = sin diálogo; id null = solo pendiente de teclado (aún no en BD).
    var tagPendingDelete by remember { mutableStateOf<Pair<Long?, String>?>(null) }

    val onRemoveNoteTag = remember(viewModel) { viewModel::removeTag }
    val onDeleteTag = remember(viewModel) { viewModel::deleteTag }

    fun applyPending() {
        viewModel.addTags(pendingTagNames)
        pendingTagNames = emptyList()
        query = ""
        onDismiss()
    }

    tagPendingDelete?.let { (tagId, tagName) ->
        AlertDialog(
            onDismissRequest = { tagPendingDelete = null },
            title = { Text(stringResource(R.string.dialog_delete_tag_title)) },
            text = {
                Text(stringResource(R.string.dialog_delete_tag_message, tagName))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (tagId != null) {
                            onDeleteTag(tagId)
                        }
                        val key = tagName.lowercase(Locale.getDefault())
                        pendingTagNames = pendingTagNames.filterNot {
                            it.lowercase(Locale.getDefault()) == key
                        }
                        tagPendingDelete = null
                    },
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { tagPendingDelete = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    NotasScaffold(
        title = stringResource(R.string.manage_tags_title),
        navigationIcon = {
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.cd_back),
                    tint = NegroTexto,
                )
            }
        },
        actions = {
            TopBarTextButton(
                label = stringResource(R.string.action_apply_tags),
                enabled = pendingTagNames.isNotEmpty(),
                onClick = { applyPending() },
            )
        },
    ) { padding ->
        ManageTagsList(
            noteTags = noteTags,
            allTags = allTags,
            query = query,
            pendingTagNames = pendingTagNames,
            onQueryChange = { query = it },
            onPendingChange = { pendingTagNames = it },
            onRemoveNoteTag = onRemoveNoteTag,
            onRequestDeleteTag = { id, name -> tagPendingDelete = id to name },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        )
    }
}

@Composable
private fun ManageTagsList(
    noteTags: List<TagEntity>,
    allTags: List<TagEntity>,
    query: String,
    pendingTagNames: List<String>,
    onQueryChange: (String) -> Unit,
    onPendingChange: (List<String>) -> Unit,
    onRemoveNoteTag: (Long) -> Unit,
    onRequestDeleteTag: (id: Long?, name: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val noteTagIds = remember(noteTags) { noteTags.map { it.id }.toSet() }
    val noteTagNames = remember(noteTags) {
        noteTags.map { it.name.lowercase(Locale.getDefault()) }.toSet()
    }
    val pendingLower = remember(pendingTagNames) {
        pendingTagNames.map { it.lowercase(Locale.getDefault()) }.toSet()
    }
    val trimmedQuery = query.trim()
    // Las pendientes se muestran arriba, en "En esta nota", así que salen de aquí.
    val availableTags = remember(allTags, noteTagIds, pendingLower) {
        allTags.filter { tag ->
            tag.id !in noteTagIds &&
                tag.name.lowercase(Locale.getDefault()) !in pendingLower
        }
    }
    val suggestions = remember(availableTags, trimmedQuery) {
        availableTags.filter { tag ->
            trimmedQuery.isEmpty() ||
                tag.name.lowercase(Locale.getDefault())
                    .contains(trimmedQuery.lowercase(Locale.getDefault()))
        }
    }
    val canSelectTyped = trimmedQuery.isNotEmpty() &&
        trimmedQuery.lowercase(Locale.getDefault()) !in noteTagNames &&
        trimmedQuery.lowercase(Locale.getDefault()) !in pendingLower

    val togglePendingName = remember(pendingTagNames, onPendingChange) {
        { name: String ->
            val key = name.lowercase(Locale.getDefault())
            onPendingChange(
                if (pendingTagNames.any { it.lowercase(Locale.getDefault()) == key }) {
                    pendingTagNames.filterNot { it.lowercase(Locale.getDefault()) == key }
                } else {
                    pendingTagNames + name
                },
            )
        }
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(vertical = 12.dp),
    ) {
        item(key = "input") {
            ManageTagsQueryField(
                query = query,
                onQueryChange = onQueryChange,
                canSelectTyped = canSelectTyped,
                onSelectTyped = {
                    onPendingChange(pendingTagNames + trimmedQuery)
                    onQueryChange("")
                },
            )
        }

        item(key = "on_note_header") {
            Text(
                text = stringResource(R.string.manage_tags_on_note),
                style = MaterialTheme.typography.titleSmall,
                color = NegroTexto,
            )
            Spacer(Modifier.height(8.dp))
            if (noteTags.isEmpty() && pendingTagNames.isEmpty()) {
                Text(
                    text = stringResource(R.string.manage_tags_on_note_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                )
                Spacer(Modifier.height(12.dp))
            }
        }

        items(noteTags, key = { "note-${it.id}" }) { tag ->
            ManageNoteTagRow(
                tag = tag,
                onRemoveNoteTag = onRemoveNoteTag,
                onRequestDeleteTag = onRequestDeleteTag,
            )
        }

        // Pendientes de aplicar: ya se ven aquí, pero no se guardan hasta "Aplicar".
        items(
            pendingTagNames,
            key = { "pending-${it.lowercase(Locale.getDefault())}" },
        ) { name ->
            val existingId = allTags.firstOrNull {
                it.name.lowercase(Locale.getDefault()) == name.lowercase(Locale.getDefault())
            }?.id
            ManagePendingTagRow(
                name = name,
                tagId = existingId,
                onRemovePending = togglePendingName,
                onRequestDeleteTag = onRequestDeleteTag,
            )
        }

        item(key = "existing_header") {
            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.manage_tags_existing),
                style = MaterialTheme.typography.titleSmall,
                color = NegroTexto,
            )
            Spacer(Modifier.height(8.dp))
            when {
                availableTags.isEmpty() && trimmedQuery.isEmpty() -> {
                    Text(
                        text = stringResource(R.string.manage_tags_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                    )
                }
                suggestions.isEmpty() -> {
                    Text(
                        text = stringResource(R.string.manage_tags_no_matches),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                    )
                }
            }
        }

        if (suggestions.isNotEmpty()) {
            items(suggestions, key = { "avail-${it.id}" }) { tag ->
                ManageAvailableTagRow(
                    tag = tag,
                    onToggle = togglePendingName,
                    onRequestDeleteTag = onRequestDeleteTag,
                )
            }
        }
    }
}

@Composable
private fun ManageTagsQueryField(
    query: String,
    onQueryChange: (String) -> Unit,
    canSelectTyped: Boolean,
    onSelectTyped: () -> Unit,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text(stringResource(R.string.field_new_tag)) },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(
            onDone = { if (canSelectTyped) onSelectTyped() },
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Celeste,
            unfocusedBorderColor = Celeste,
            cursorColor = NegroTexto,
        ),
    )
    Spacer(Modifier.height(8.dp))
    CelesteElevatedButton(
        onClick = onSelectTyped,
        enabled = canSelectTyped,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(R.string.action_select_tag))
    }
    Spacer(Modifier.height(20.dp))
}

/** Etiqueta ya asociada: casilla marcada; desmarcarla la quita de la nota. */
@Composable
private fun ManageNoteTagRow(
    tag: TagEntity,
    onRemoveNoteTag: (Long) -> Unit,
    onRequestDeleteTag: (id: Long?, name: String) -> Unit,
) {
    ManageTagCheckRow(
        name = tag.name,
        checked = true,
        onClick = { onRemoveNoteTag(tag.id) },
        onDelete = { onRequestDeleteTag(tag.id, tag.name) },
        contentDescription = stringResource(R.string.cd_remove_tag),
    )
}

/** Etiqueta escrita o elegida que se guardará al pulsar "Aplicar". */
@Composable
private fun ManagePendingTagRow(
    name: String,
    tagId: Long?,
    onRemovePending: (String) -> Unit,
    onRequestDeleteTag: (id: Long?, name: String) -> Unit,
) {
    ManageTagCheckRow(
        name = name,
        checked = true,
        onClick = { onRemovePending(name) },
        onDelete = { onRequestDeleteTag(tagId, name) },
        contentDescription = stringResource(R.string.cd_remove_pending_tag),
    )
}

/** Etiqueta no asociada: marcarla la pasa a pendiente, arriba. */
@Composable
private fun ManageAvailableTagRow(
    tag: TagEntity,
    onToggle: (String) -> Unit,
    onRequestDeleteTag: (id: Long?, name: String) -> Unit,
) {
    ManageTagCheckRow(
        name = tag.name,
        checked = false,
        onClick = { onToggle(tag.name) },
        onDelete = { onRequestDeleteTag(tag.id, tag.name) },
    )
}

@Composable
private fun ManageTagCheckRow(
    name: String,
    checked: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    contentDescription: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clickable(onClickLabel = contentDescription, onClick = onClick)
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = checked,
                onCheckedChange = null,
            )
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                color = NegroTexto,
                modifier = Modifier.padding(start = 4.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.cd_delete_tag),
                tint = NegroTexto,
            )
        }
    }
    HorizontalDivider()
}

@Composable
private fun CopyOptionRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp),
        )
    }
}
