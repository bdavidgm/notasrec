package com.bdavidgm.notasrec.ui.detail

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.bdavidgm.notasrec.R
import com.bdavidgm.notasrec.ui.theme.NegroTexto
import com.bdavidgm.notasrec.ui.util.NOTE_LINK_BOUNDARY
import com.bdavidgm.notasrec.ui.util.ensureRichLinkBoundaries
import com.bdavidgm.notasrec.ui.util.ensurePureMarkdown
import com.bdavidgm.notasrec.ui.util.parseNoteLinkUid
import com.mohamedrejeb.richeditor.model.RichTextState
import com.mohamedrejeb.richeditor.ui.BasicRichTextEditor
import com.mohamedrejeb.richeditor.ui.material3.RichText
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.launch
import java.io.File

/**
 * `BasicTextField` no admite contenido en línea, así que ningún editor rich-text
 * de Compose (tampoco compose-rich-editor 1.2.0) puede pintar una imagen dentro
 * del campo: la librería solo las dibuja en su vista de lectura. Por eso el
 * cuerpo se parte en bloques —texto editable (multilínea) y fotos como
 * composables reales— solo en los límites de imagen, y se vuelve a unir en
 * Markdown para guardar. Así se puede seleccionar varias líneas y las listas
 * numeradas continúan al pulsar Enter.
 */
private sealed interface BodyBlock {
    val id: Long

    data class Text(override val id: Long, val markdown: String) : BodyBlock

    data class Photo(
        override val id: Long,
        val alt: String,
        val url: String,
    ) : BodyBlock {
        fun toMarkdown(): String = "![$alt]($url)"

        /** Coil resuelve mejor el fichero que la URI `file://`. */
        fun imageModel(): Any =
            if (url.startsWith(FILE_SCHEME)) File(url.removePrefix(FILE_SCHEME)) else url
    }
}

private const val FILE_SCHEME = "file://"

private val IMAGE_MARKDOWN = Regex("""!\[([^\]]*)]\(([^)\n]+)\)""")

/**
 * Marcador temporal para partir un bloque justo donde está el cursor: se inserta
 * en el editor, se serializa a Markdown y el punto de corte cae en el mismo sitio
 * que en el texto visible, sin tener que mapear offsets a mano.
 */
private const val SPLIT_MARK = "@@NOTAS_SPLIT@@"

/** La librería emite `<br>` para líneas vacías; nunca lo dejamos salir del editor. */
private fun RichTextState.toPureMarkdown(): String = ensurePureMarkdown(toMarkdown())

/**
 * Parte el Markdown en bloques. Cada foto queda entre dos bloques de texto (aunque
 * estén vacíos), así que siempre hay sitio donde escribir antes y después.
 */
private fun parseBodyBlocks(markdown: String, newId: () -> Long): List<BodyBlock> {
    val blocks = mutableListOf<BodyBlock>()
    var cursor = 0
    IMAGE_MARKDOWN.findAll(markdown).forEach { match ->
        blocks += BodyBlock.Text(newId(), markdown.substring(cursor, match.range.first).trim())
        blocks += BodyBlock.Photo(
            id = newId(),
            alt = match.groupValues[1],
            url = match.groupValues[2],
        )
        cursor = match.range.last + 1
    }
    blocks += BodyBlock.Text(newId(), markdown.substring(cursor).trim())
    return blocks
}

/** Acceso al contenido vivo de un bloque de texto (aún sin volcar al borrador). */
private class BodyTextHandle(
    val snapshot: () -> String,
    val splitAtCursor: () -> Pair<String, String>,
    val replace: (markdown: String, cursor: Int) -> Unit,
)

/** A dónde llevar el cursor tras insertar una foto o unir bloques. */
private data class LineFocus(val id: Long, val offset: Int)

private class BodyBlocksState(initialMarkdown: String) {
    val blocks: SnapshotStateList<BodyBlock> = mutableStateListOf()

    private val handles = mutableMapOf<Long, BodyTextHandle>()
    private val markdownById = mutableMapOf<Long, String>()
    private var lastId = 0L

    init {
        load(initialMarkdown)
    }

    fun load(markdown: String) {
        val parsed = parseBodyBlocks(markdown, ::newId)
        blocks.clear()
        markdownById.clear()
        handles.clear()
        parsed.forEach { block ->
            when (block) {
                is BodyBlock.Text -> addText(block.markdown)
                is BodyBlock.Photo -> blocks.add(block)
            }
        }
    }

    fun registerHandle(id: Long, handle: BodyTextHandle?) {
        if (handle == null) handles.remove(id) else handles[id] = handle
    }

    /** Volcado en vivo (salir de la nota, cambiar de modo). */
    fun currentMarkdown(): String = join(live = true)

    /** Lo ya serializado, sin volver a convertir cada bloque a Markdown. */
    fun cachedMarkdown(): String = join(live = false)

    fun updateText(id: Long, markdown: String) {
        markdownById[id] = markdown
    }

    /**
     * Retroceso al principio de un tramo de texto: une con el tramo anterior
     * (p. ej. tras borrar una foto y quedar dos editores contiguos).
     */
    fun mergeWithPrevious(id: Long): LineFocus? {
        val index = blocks.indexOfFirst { it.id == id }
        if (index <= 0) return null
        val current = blocks[index] as? BodyBlock.Text ?: return null
        val previous = blocks[index - 1] as? BodyBlock.Text ?: return null
        val previousText = textOf(previous, live = true)
        val currentText = textOf(current, live = true)
        val merged = when {
            previousText.isEmpty() -> currentText
            currentText.isEmpty() -> previousText
            else -> previousText + "\n" + currentText
        }
        val cursor = previousText.length
        markdownById[previous.id] = merged
        handles[previous.id]?.replace?.invoke(merged, cursor)
        blocks.removeAt(index)
        markdownById.remove(current.id)
        handles.remove(current.id)
        return LineFocus(previous.id, cursor)
    }

    fun remove(id: Long) {
        val index = blocks.indexOfFirst { it.id == id }
        blocks.removeAll { it.id == id }
        handles.remove(id)
        markdownById.remove(id)
        if (index >= 0) coalesceAdjacentTextAt(index.coerceAtMost(blocks.lastIndex.coerceAtLeast(0)))
    }

    /**
     * Inserta la foto en el cursor del bloque [focusedTextId] partiéndolo en dos.
     * Devuelve el id del bloque de texto que queda debajo, para darle el foco.
     */
    fun insertPhoto(focusedTextId: Long?, url: String): Long {
        val photo = BodyBlock.Photo(id = newId(), alt = "", url = url)
        val index = blocks.indexOfFirst { it.id == focusedTextId }
        val split = focusedTextId?.let { handles[it] }?.splitAtCursor()

        val removedId = focusedTextId
        if (index < 0 || split == null || removedId == null) {
            blocks += photo
            return addText("").id
        }

        // Ids nuevos: el bloque partido se recompone desde su Markdown.
        addText(split.first.trimEnd(), index)
        blocks.removeAt(index + 1)
        markdownById.remove(removedId)
        handles.remove(removedId)
        blocks.add(index + 1, photo)
        return addText(split.second.trimStart(), index + 2).id
    }

    /** Tras quitar una foto, funde los dos tramos de texto que quedan juntos. */
    private fun coalesceAdjacentTextAt(index: Int) {
        if (blocks.isEmpty()) return
        val at = index.coerceIn(0, blocks.lastIndex)
        val leftIndex = when {
            blocks[at] is BodyBlock.Text && at > 0 && blocks[at - 1] is BodyBlock.Text -> at - 1
            at < blocks.lastIndex &&
                blocks[at] is BodyBlock.Text &&
                blocks[at + 1] is BodyBlock.Text -> at
            else -> return
        }
        val left = blocks[leftIndex] as BodyBlock.Text
        val right = blocks[leftIndex + 1] as BodyBlock.Text
        val leftText = textOf(left, live = true)
        val rightText = textOf(right, live = true)
        val merged = when {
            leftText.isEmpty() -> rightText
            rightText.isEmpty() -> leftText
            else -> leftText.trimEnd() + "\n\n" + rightText.trimStart()
        }
        markdownById[left.id] = merged
        handles[left.id]?.replace?.invoke(merged, merged.length.coerceAtMost(leftText.length))
        blocks.removeAt(leftIndex + 1)
        markdownById.remove(right.id)
        handles.remove(right.id)
    }

    private fun addText(markdown: String, at: Int = blocks.size): BodyBlock.Text {
        val block = BodyBlock.Text(newId(), markdown)
        markdownById[block.id] = markdown
        if (at >= blocks.size) blocks.add(block) else blocks.add(at, block)
        return block
    }

    private fun join(live: Boolean): String {
        val parts = mutableListOf<String>()
        for (block in blocks) {
            when (block) {
                is BodyBlock.Text -> {
                    val text = textOf(block, live)
                    if (text.isNotBlank()) parts += text
                }
                is BodyBlock.Photo -> parts += block.toMarkdown()
            }
        }
        return parts.joinToString("\n\n")
    }

    private fun textOf(block: BodyBlock.Text, live: Boolean): String {
        val raw = if (live) {
            handles[block.id]?.snapshot?.invoke() ?: markdownById[block.id] ?: block.markdown
        } else {
            markdownById[block.id] ?: block.markdown
        }
        return raw.replace(SPLIT_MARK, "")
    }

    private fun newId(): Long = ++lastId
}

@Composable
internal fun RichMarkdownDraftEditor(viewModel: DetailViewModel) {
    val body = remember(viewModel) { BodyBlocksState(viewModel.draftContent.value) }
    var lastPushed by remember(viewModel) { mutableStateOf(viewModel.draftContent.value) }
    // userEdited se activa también al recibir el foco un bloque: desde ese momento
    // el cursor es del usuario y ninguna recarga rehace los bloques bajo sus pies.
    var userEdited by remember(viewModel) { mutableStateOf(false) }
    var focusedTextId by remember { mutableStateOf<Long?>(null) }
    // Al abrir la edición el cursor se coloca en el primer carácter del cuerpo:
    // el primer bloque siempre es de texto (parseBodyBlocks lo garantiza).
    var pendingFocus by remember {
        val empty = body.blocks.all { it is BodyBlock.Text && it.markdown.isBlank() }
        val firstId = body.blocks.firstOrNull()?.id
        mutableStateOf(if (empty || firstId == null) null else LineFocus(firstId, 0))
    }
    var activeState by remember { mutableStateOf<RichTextState?>(null) }

    val push = {
        userEdited = true
        // Caché: cada tecla no vuelve a serializar el resto de las líneas.
        val markdown = body.cachedMarkdown()
        lastPushed = markdown
        viewModel.updateDraftContent(markdown)
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showPhotoDialog by remember { mutableStateOf(false) }
    var noteLinkState by remember { mutableStateOf<RichTextState?>(null) }
    var cameraTarget by remember { mutableStateOf<File?>(null) }

    val insertPhoto: (String) -> Unit = { path ->
        pendingFocus = body.insertPhoto(focusedTextId, "$FILE_SCHEME$path").let { LineFocus(it, 0) }
        push()
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val path = viewModel.importBodyPhoto(uri)
                if (path != null) insertPhoto(path) else viewModel.reportPhotoError()
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { saved ->
        val file = cameraTarget
        cameraTarget = null
        if (file != null) {
            if (saved) {
                insertPhoto(file.absolutePath)
                scope.launch { viewModel.registerBodyPhoto(file) }
            } else {
                scope.launch { viewModel.discardBodyPhoto(file) }
            }
        }
    }

    if (showPhotoDialog) {
        InsertPhotoDialog(
            onGallery = {
                showPhotoDialog = false
                galleryLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            },
            onCamera = {
                showPhotoDialog = false
                scope.launch {
                    val file = viewModel.newBodyPhotoFile()
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file,
                    )
                    cameraTarget = file
                    // Sin app de cámara el launcher lanza ActivityNotFoundException.
                    runCatching { cameraLauncher.launch(uri) }.onFailure {
                        cameraTarget = null
                        viewModel.discardBodyPhoto(file)
                        viewModel.reportCameraMissing()
                    }
                }
            },
            onDismiss = { showPhotoDialog = false },
        )
    }

    LaunchedEffect(viewModel) {
        viewModel.draftContent.collect { remote ->
            if (userEdited || remote == lastPushed) return@collect
            body.load(remote)
            lastPushed = remote
        }
    }

    DisposableEffect(viewModel) {
        viewModel.setBodySnapshotProvider { body.currentMarkdown() }
        onDispose {
            viewModel.updateDraftContent(body.currentMarkdown())
            viewModel.setBodySnapshotProvider(null)
        }
    }

    RichMarkdownFormatToolbar(
        state = activeState,
        onInsertPhoto = { showPhotoDialog = true },
        onInsertNoteLink = { activeState?.let { noteLinkState = it } },
    )

    noteLinkState?.let { state ->
        LinkNoteDialog(
            viewModel = viewModel,
            state = state,
            onDismiss = { noteLinkState = null },
        )
    }

    Spacer(Modifier.height(8.dp))
    Text(
        text = stringResource(R.string.field_body),
        style = MaterialTheme.typography.labelMedium,
        color = NegroTexto.copy(alpha = 0.75f),
    )
    Spacer(Modifier.height(4.dp))
    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        val onlyTextBlock = body.blocks.size == 1
        body.blocks.forEachIndexed { index, block ->
            key(block.id) {
                when (block) {
                    is BodyBlock.Text -> BodyTextBlock(
                        block = block,
                        isLast = index == body.blocks.lastIndex,
                        placeholder = if (onlyTextBlock) {
                            stringResource(R.string.markdown_edit_hint)
                        } else {
                            null
                        },
                        focus = pendingFocus?.takeIf { it.id == block.id },
                        onFocusHandled = { pendingFocus = null },
                        onFocused = { state ->
                            focusedTextId = block.id
                            activeState = state
                            userEdited = true
                        },
                        onRegisterHandle = { handle -> body.registerHandle(block.id, handle) },
                        onFlush = { markdown -> body.updateText(block.id, markdown) },
                        onEdited = { markdown ->
                            body.updateText(block.id, markdown)
                            push()
                        },
                        onBackspaceAtStart = {
                            pendingFocus = body.mergeWithPrevious(block.id)
                            push()
                        },
                    )

                    is BodyBlock.Photo -> BodyPhotoBlock(
                        photo = block,
                        onRemove = {
                            body.remove(block.id)
                            push()
                            viewModel.removeBodyPhoto(block.url)
                        },
                    )
                }
            }
        }
    }
}

@OptIn(FlowPreview::class)
@Composable
private fun BodyTextBlock(
    block: BodyBlock.Text,
    isLast: Boolean,
    placeholder: String?,
    focus: LineFocus?,
    onFocusHandled: () -> Unit,
    onFocused: (RichTextState) -> Unit,
    onRegisterHandle: (BodyTextHandle?) -> Unit,
    onFlush: (String) -> Unit,
    onEdited: (String) -> Unit,
    onBackspaceAtStart: () -> Unit,
) {
    // El estado se inicializa aquí (y no en un LaunchedEffect) para que el primer
    // annotatedString ya sea el definitivo: así `drop(1)` descarta la carga y no
    // se confunde con una edición del usuario.
    val state = remember {
        RichTextState().apply {
            // rc10 atribuye la escritura en el borde derecho de un enlace al
            // párrafo siguiente (#709). Un límite invisible de estilo normal
            // elimina la ambigüedad también en enlaces guardados anteriormente.
            setMarkdown(ensureRichLinkBoundaries(block.markdown))
            selection = TextRange.Zero
        }
    }
    // El round-trip del Markdown normaliza (listas, énfasis…), así que la
    // referencia es lo que el editor devuelve, no lo que se le dio.
    var lastKnown by remember { mutableStateOf(state.toPureMarkdown()) }
    val interactionSource = remember { IgnorePressInteractionSource() }
    val focusRequester = remember { FocusRequester() }
    val currentOnEdited by rememberUpdatedState(onEdited)
    val currentOnFlush by rememberUpdatedState(onFlush)
    val currentOnBackspace by rememberUpdatedState(onBackspaceAtStart)

    DisposableEffect(state) {
        onRegisterHandle(
            BodyTextHandle(
                snapshot = { state.toPureMarkdown() },
                splitAtCursor = {
                    state.addTextAfterSelection(SPLIT_MARK)
                    val parts = state.toPureMarkdown().split(SPLIT_MARK, limit = 2)
                    parts[0] to parts.getOrElse(1) { "" }
                },
                replace = { markdown, cursor ->
                    state.setMarkdown(ensureRichLinkBoundaries(markdown))
                    val bounded = cursor.coerceIn(0, state.annotatedString.length)
                    state.selection = TextRange(bounded)
                },
            ),
        )
        onDispose {
            currentOnFlush(state.toPureMarkdown())
            onRegisterHandle(null)
        }
    }

    // Clave de rendimiento: observar el annotatedString (señal barata) y
    // serializar a Markdown una sola vez pasado el debounce.
    LaunchedEffect(state) {
        snapshotFlow { state.annotatedString }
            .drop(1)
            .debounce(400)
            .collect {
                val markdown = state.toPureMarkdown()
                if (markdown == lastKnown) return@collect
                lastKnown = markdown
                currentOnEdited(markdown)
            }
    }

    // Al tocar visualmente el final del enlace, Android puede colocar el cursor
    // justo antes del límite invisible. Lo adelantamos sobre ese carácter para
    // que Enter y la escritura pertenezcan al span normal y no al enlace.
    LaunchedEffect(state) {
        snapshotFlow { state.selection }
            .collect { selection ->
                if (!selection.collapsed) return@collect
                val offset = selection.min
                if (state.annotatedString.text.getOrNull(offset) != NOTE_LINK_BOUNDARY) {
                    return@collect
                }
                if (state.selectedLinkUrl == null) return@collect
                state.selection = TextRange(offset + 1)
            }
    }

    LaunchedEffect(focus) {
        val request = focus ?: return@LaunchedEffect
        val length = state.annotatedString.length
        state.selection = TextRange(request.offset.coerceIn(0, length))
        // En la primera composición el nodo puede no estar colocado todavía.
        if (runCatching { focusRequester.requestFocus() }.isFailure) {
            withFrameNanos { }
            runCatching { focusRequester.requestFocus() }
        }
        onFocusHandled()
    }

    BasicRichTextEditor(
        state = state,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = if (isLast) 120.dp else 24.dp)
            .focusRequester(focusRequester)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown || event.key != Key.Backspace) {
                    return@onPreviewKeyEvent false
                }
                val selection = state.selection
                if (!selection.collapsed || selection.start != 0) return@onPreviewKeyEvent false
                currentOnBackspace()
                true
            }
            .onFocusChanged { if (it.isFocused) onFocused(state) },
        singleParagraph = false,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = NegroTexto),
        interactionSource = interactionSource,
        cursorBrush = SolidColor(NegroTexto),
        decorationBox = { innerTextField ->
            Box {
                if (placeholder != null && state.annotatedString.text.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyLarge,
                        color = NegroTexto.copy(alpha = 0.45f),
                    )
                }
                innerTextField()
            }
        },
        contentPadding = PaddingValues(0.dp),
    )
}

/**
 * Vista de lectura. rc10 sí entiende `![](url)`, pero crea el hueco de la imagen
 * con tamaño 0×0 y solo lo ajusta en un `LaunchedEffect` que se dispara antes de
 * que Coil acabe de decodificar, así que la foto nunca se veía. Se pintan los
 * mismos bloques que en edición: texto con la librería y fotos con Coil.
 *
 * Los toques en enlaces pasan por [LocalUriHandler]: los `notas://` los captura
 * [onNoteLink]; el resto sigue al handler de plataforma.
 */
@Composable
internal fun ReadOnlyRichMarkdownBody(
    markdown: String,
    onNoteLink: (uid: String) -> Unit = {},
) {
    if (markdown.isBlank()) {
        Text(
            text = stringResource(R.string.empty_body_hint),
            style = MaterialTheme.typography.bodyLarge,
            color = NegroTexto.copy(alpha = 0.65f),
        )
        return
    }

    val blocks = remember(markdown) {
        var lastId = 0L
        parseBodyBlocks(markdown) { ++lastId }
    }

    val platformUriHandler = LocalUriHandler.current
    val uriHandler = remember(platformUriHandler, onNoteLink) {
        object : UriHandler {
            override fun openUri(uri: String) {
                val uid = parseNoteLinkUid(uri)
                if (uid != null) onNoteLink(uid) else platformUriHandler.openUri(uri)
            }
        }
    }

    CompositionLocalProvider(LocalUriHandler provides uriHandler) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            blocks.forEach { block ->
                key(block.id) {
                    when (block) {
                        is BodyBlock.Text ->
                            if (block.markdown.isNotBlank()) ReadOnlyTextBlock(block.markdown)

                        is BodyBlock.Photo -> BodyPhotoBlock(photo = block, onRemove = null)
                    }
                }
            }
        }
    }
}

@Composable
private fun ReadOnlyTextBlock(markdown: String) {
    val state = remember(markdown) { RichTextState().apply { setMarkdown(markdown) } }
    RichText(
        state = state,
        modifier = Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.bodyLarge,
    )
}

@Composable
private fun BodyPhotoBlock(
    photo: BodyBlock.Photo,
    onRemove: (() -> Unit)?,
) {
    val context = LocalContext.current
    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(photo.imageModel())
                .crossfade(true)
                .build(),
            contentDescription = photo.alt.ifBlank { null },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 360.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Fit,
        )
        if (onRemove != null) {
            IconButton(
                onClick = onRemove,
                modifier = Modifier.align(Alignment.TopEnd),
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.cd_remove_photo),
                    tint = NegroTexto,
                )
            }
        }
    }
}

@Composable
private fun InsertPhotoDialog(
    onGallery: () -> Unit,
    onCamera: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_insert_photo_title)) },
        text = {
            Column {
                TextButton(
                    onClick = onGallery,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.action_photo_gallery))
                }
                TextButton(
                    onClick = onCamera,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.action_photo_camera))
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

/**
 * compose-rich-editor (rc10) recoloca la selección en cada `PressInteraction.Press`
 * con una heurística de líneas propia: si el texto tiene líneas plegadas, descarta
 * el offset que calculó `BasicTextField` y el toque acaba en otro sitio o se ignora
 * (issue #304 de la librería). Como su `VisualTransformation` usa
 * `OffsetMapping.Identity`, el mapeo nativo ya es correcto, así que basta con no
 * entregarle el evento de pulsación. El foco y los colores siguen llegando.
 */
private class IgnorePressInteractionSource(
    private val delegate: MutableInteractionSource = MutableInteractionSource(),
) : MutableInteractionSource by delegate {
    override val interactions: Flow<Interaction> =
        delegate.interactions.filterNot { it is PressInteraction.Press }
}
