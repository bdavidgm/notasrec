package com.bdavidgm.notasrec.ui.detail

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.bdavidgm.notasrec.R
import com.bdavidgm.notasrec.ui.theme.Celeste
import com.bdavidgm.notasrec.ui.theme.CelesteClaro
import com.bdavidgm.notasrec.ui.theme.CelesteOscuro
import com.bdavidgm.notasrec.ui.theme.NegroTexto
import com.bdavidgm.notasrec.ui.util.NOTE_LINK_BOUNDARY
import com.bdavidgm.notasrec.ui.util.ensurePureMarkdown
import com.bdavidgm.notasrec.ui.util.ensureRichLinkBoundaries
import com.bdavidgm.notasrec.ui.util.parseNoteLinkUid
import com.mohamedrejeb.richeditor.model.RichTextState
import com.mohamedrejeb.richeditor.ui.BasicRichTextEditor
import com.mohamedrejeb.richeditor.ui.material3.RichText
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
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

    data class Quote(override val id: Long, val markdown: String) : BodyBlock

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

private sealed interface MarkdownPiece {
    data class Prose(val markdown: String) : MarkdownPiece
    data class Quote(val markdown: String) : MarkdownPiece
    data class Code(val markdown: String) : MarkdownPiece
}

/** Separa citas y bloques de código para pintarlos; el editor no los entiende. */
private fun splitMarkdownPieces(markdown: String): List<MarkdownPiece> {
    if (markdown.isEmpty()) return listOf(MarkdownPiece.Prose(""))
    val pieces = mutableListOf<MarkdownPiece>()
    val buffer = mutableListOf<String>()
    val codeBuffer = mutableListOf<String>()
    var quoting = false
    var inCode = false
    fun flush() {
        if (buffer.isEmpty()) return
        val text = buffer.joinToString("\n")
        buffer.clear()
        if (quoting) pieces += MarkdownPiece.Quote(text)
        else if (text.isNotBlank()) pieces += MarkdownPiece.Prose(text)
    }
    fun flushCode() {
        pieces += MarkdownPiece.Code(codeBuffer.joinToString("\n"))
        codeBuffer.clear()
    }
    for (line in markdown.lines()) {
        if (isCodeFenceLine(line)) {
            flush()
            if (inCode) flushCode()
            inCode = !inCode
            quoting = false
            continue
        }
        if (inCode) {
            codeBuffer += line
            continue
        }
        val content = quoteLineContent(line)
        val isQuote = content != null
        if (isQuote != quoting) flush()
        quoting = isQuote
        buffer += content ?: line
    }
    flush()
    if (inCode) flushCode()
    return pieces.ifEmpty { listOf(MarkdownPiece.Prose(markdown)) }
}

private fun quoteLineContent(line: String): String? {
    if (!line.startsWith(">")) return null
    return line.removePrefix(">").removePrefix(" ")
}

private fun quoteMarkdown(body: String): String =
    body.lines().joinToString("\n") { line -> if (line.isEmpty()) ">" else "> $line" }

private const val QUOTE_MARKER = "\u200b▎"
private const val QUOTE_PREFIX = "$QUOTE_MARKER "
private const val CODE_MARKER = "\u200b\u2063"
private const val CODE_PREFIX = CODE_MARKER
private const val INLINE_CODE_MARKER = "\u200b\u2064"
private const val INLINE_CODE_PREFIX = INLINE_CODE_MARKER
private const val NORMAL_PARAGRAPH_MARKER = "\u2060"
private val quoteParagraphStyle = ParagraphStyle(
    textIndent = TextIndent(firstLine = 10.sp, restLine = 10.sp),
)
private val quoteMarkerStyle = SpanStyle(color = CelesteOscuro)
private val codeSpanStyle = SpanStyle(
    fontFamily = FontFamily.Monospace,
    background = Color(0xFFF3F4F6),
)
private val inlineCodeSpanStyle = SpanStyle(
    fontFamily = FontFamily.Monospace,
    background = CelesteClaro,
)
private val inlineCodeLine = Regex("^`([^`]*)`$")

private fun isCodeFenceLine(line: String): Boolean {
    val trimmed = line.trim()
    if (!trimmed.startsWith("```")) return false
    val info = trimmed.removePrefix("```")
    return info.isEmpty() || info.all { it.isLetterOrDigit() || it == '-' || it == '_' || it == '+' }
}

/** Sustituye el marcador Markdown por una barra visual que el editor no interpreta. */
private fun protectBlockQuotes(markdown: String): String =
    markdown.lines().joinToString("\n") { line ->
        if (line.startsWith(">")) {
            QUOTE_MARKER + line.removePrefix(">")
        } else {
            line
        }
    }

/** El editor no pinta ```; cada línea del bloque lleva un marcador invisible. */
private fun protectCodeFences(markdown: String): String {
    val out = mutableListOf<String>()
    var inCode = false
    for (line in markdown.lines()) {
        if (isCodeFenceLine(line)) {
            inCode = !inCode
            continue
        }
        out += if (inCode && !line.startsWith(CODE_MARKER)) CODE_MARKER + line else line
    }
    return out.joinToString("\n")
}

private fun prepareEditorMarkdown(markdown: String): String =
    protectBlockQuotes(protectInlineCodeLines(protectCodeFences(markdown)))

/** Una línea entera `` `texto` `` entra al editor como código en línea de varias líneas. */
private fun protectInlineCodeLines(markdown: String): String =
    markdown.lines().joinToString("\n") { line ->
        if (line.startsWith(CODE_MARKER) || line.startsWith(INLINE_CODE_MARKER)) {
            line
        } else {
            val inner = inlineCodeLine.matchEntire(line)?.groupValues?.get(1) ?: return@joinToString line
            INLINE_CODE_MARKER + inner.removePrefix("\u200b")
        }
    }

private fun normalizeQuoteMarkers(markdown: String): String =
    markdown
        .replace(QUOTE_MARKER, ">")
        .replace(NORMAL_PARAGRAPH_MARKER, "")

private fun normalizeCodeMarkers(markdown: String): String {
    val out = mutableListOf<String>()
    var inCode = false
    fun close() {
        if (inCode) {
            out += "```"
            inCode = false
        }
    }
    for (line in markdown.lines()) {
        if (line.startsWith(CODE_MARKER)) {
            if (!inCode) {
                out += "```"
                inCode = true
            }
            out += line.removePrefix(CODE_MARKER)
        } else {
            close()
            out += line
        }
    }
    close()
    return out.joinToString("\n")
}

private fun normalizeInlineCodeMarkers(markdown: String): String =
    markdown.lines().joinToString("\n") { line ->
        if (!line.startsWith(INLINE_CODE_MARKER)) {
            line
        } else {
            val content = line.removePrefix(INLINE_CODE_MARKER)
            if (content.isEmpty()) "`\u200b`" else "`$content`"
        }
    }

private fun exportEditorMarkdown(markdown: String): String =
    normalizeInlineCodeMarkers(normalizeCodeMarkers(normalizeQuoteMarkers(markdown)))

/** Aplica el aspecto de cita sin separar el párrafo en otro editor. */
private fun renderBlockQuotes(state: RichTextState) {
    val previousSelection = state.selection
    val quoteStarts = state.annotatedString.paragraphStyles
        .map { it.start }
        .filter { start -> state.annotatedString.text.startsWith(QUOTE_MARKER, start) }
        .ifEmpty {
            if (state.annotatedString.text.startsWith(QUOTE_MARKER)) listOf(0) else emptyList()
        }
    quoteStarts.forEach { start ->
        state.selection = TextRange(start)
        state.addParagraphStyle(quoteParagraphStyle)
        state.addSpanStyle(
            quoteMarkerStyle,
            TextRange(start + 1, (start + QUOTE_MARKER.length).coerceAtMost(state.annotatedString.length)),
        )
    }
    state.selection = previousSelection
}

private fun isInCodeParagraph(state: RichTextState): Boolean =
    isInCodeParagraph(state.annotatedString, state.selection.min)

private fun isInCodeParagraph(text: AnnotatedString, cursor: Int): Boolean {
    if (text.isEmpty()) return false
    return text.text.startsWith(CODE_MARKER, paragraphStart(text, cursor))
}

private fun codeContentStart(paragraphStart: Int): Int = paragraphStart + CODE_MARKER.length

/** Aplica el aspecto de código sin separar el párrafo en otro editor. */
private fun renderCodeBlocks(state: RichTextState) {
    val previousSelection = state.selection
    val ranges = state.annotatedString.paragraphStyles
        .filter { state.annotatedString.text.startsWith(CODE_MARKER, it.start) }
        .ifEmpty {
            if (state.annotatedString.text.startsWith(CODE_MARKER)) {
                listOf(AnnotatedString.Range(ParagraphStyle(), 0, state.annotatedString.length))
            } else {
                emptyList()
            }
        }
    ranges.forEach { range ->
        if (range.end > range.start) {
            state.addSpanStyle(codeSpanStyle, TextRange(range.start, range.end))
        }
    }
    state.selection = previousSelection
    if (isInCodeParagraph(state)) state.addSpanStyle(codeSpanStyle)
}

private fun isInInlineCodeParagraph(state: RichTextState): Boolean =
    isInInlineCodeParagraph(state.annotatedString, state.selection.min)

private fun isInInlineCodeParagraph(text: AnnotatedString, cursor: Int): Boolean {
    if (text.isEmpty()) return false
    return text.text.startsWith(INLINE_CODE_MARKER, paragraphStart(text, cursor))
}

private fun inlineCodeContentStart(paragraphStart: Int): Int =
    paragraphStart + INLINE_CODE_MARKER.length

private fun renderInlineCode(state: RichTextState) {
    val previousSelection = state.selection
    val ranges = state.annotatedString.paragraphStyles
        .filter { state.annotatedString.text.startsWith(INLINE_CODE_MARKER, it.start) }
        .ifEmpty {
            if (state.annotatedString.text.startsWith(INLINE_CODE_MARKER)) {
                listOf(AnnotatedString.Range(ParagraphStyle(), 0, state.annotatedString.length))
            } else {
                emptyList()
            }
        }
    ranges.forEach { range ->
        if (range.end > range.start) {
            state.addSpanStyle(inlineCodeSpanStyle, TextRange(range.start, range.end))
        }
    }
    state.selection = previousSelection
    if (isInInlineCodeParagraph(state)) state.addSpanStyle(inlineCodeSpanStyle)
}

private fun continueInlineCode(state: RichTextState) {
    state.addTextAfterSelection("\n$INLINE_CODE_PREFIX")
    renderInlineCode(state)
}

/** Backspace al comienzo de la última línea quita solo el código en línea de esa línea. */
private fun exitInlineCodeWithBackspace(state: RichTextState): Boolean {
    val selection = state.selection
    if (!selection.collapsed) return false
    val start = paragraphStart(state, selection.min)
    if (!state.annotatedString.text.startsWith(INLINE_CODE_MARKER, start)) return false
    if (selection.min != inlineCodeContentStart(start)) return false

    val nextParagraphStart = state.annotatedString.paragraphStyles
        .map { it.start }
        .firstOrNull { it > start }
    if (
        nextParagraphStart != null &&
        state.annotatedString.text.startsWith(INLINE_CODE_MARKER, nextParagraphStart)
    ) {
        return false
    }

    val end = paragraphEnd(state, selection.min).coerceAtMost(state.annotatedString.length)
    state.selection = TextRange(start)
    state.removeSpanStyle(inlineCodeSpanStyle, TextRange(start, end))
    state.removeTextRange(TextRange(start, inlineCodeContentStart(start)))
    return true
}

/**
 * Activa el código en línea en el párrafo actual. Si ya lo es, el texto se queda
 * igual y el cursor pasa al párrafo siguiente.
 */
private fun toggleInlineCodeParagraph(state: RichTextState) {
    val text = state.annotatedString.text
    val cursor = state.selection.min.coerceIn(0, text.length)
    val start = paragraphStart(state, cursor)
    if (isInInlineCodeParagraph(state)) {
        val end = paragraphEnd(state, cursor).coerceAtMost(text.length)
        state.selection = TextRange(end)
        state.addTextAtIndex(end, "\n$NORMAL_PARAGRAPH_MARKER")
    } else {
        state.selection = TextRange(start)
        state.addTextAtIndex(start, INLINE_CODE_PREFIX)
        renderInlineCode(state)
    }
}

private fun continueCodeBlock(state: RichTextState) {
    state.addTextAfterSelection("\n$CODE_PREFIX")
    renderCodeBlocks(state)
}

/** Backspace al comienzo de la última línea de código quita solo el formato de esa línea. */
private fun exitCodeBlockWithBackspace(state: RichTextState): Boolean {
    val selection = state.selection
    if (!selection.collapsed) return false
    val start = paragraphStart(state, selection.min)
    if (!state.annotatedString.text.startsWith(CODE_MARKER, start)) return false
    if (selection.min != codeContentStart(start)) return false

    val nextParagraphStart = state.annotatedString.paragraphStyles
        .map { it.start }
        .firstOrNull { it > start }
    if (
        nextParagraphStart != null &&
        state.annotatedString.text.startsWith(CODE_MARKER, nextParagraphStart)
    ) {
        return false
    }

    val end = paragraphEnd(state, selection.min).coerceAtMost(state.annotatedString.length)
    state.selection = TextRange(start)
    state.removeSpanStyle(codeSpanStyle, TextRange(start, end))
    state.removeTextRange(TextRange(start, codeContentStart(start)))
    return true
}

/**
 * Activa el bloque de código en el párrafo actual. Si ya lo es, el código se
 * queda igual y el cursor pasa al párrafo siguiente.
 */
private fun toggleCodeParagraph(state: RichTextState) {
    val text = state.annotatedString.text
    val cursor = state.selection.min.coerceIn(0, text.length)
    val start = paragraphStart(state, cursor)
    if (isInCodeParagraph(state)) {
        val end = paragraphEnd(state, cursor).coerceAtMost(text.length)
        state.selection = TextRange(end)
        state.addTextAtIndex(end, "\n$NORMAL_PARAGRAPH_MARKER")
    } else {
        state.selection = TextRange(start)
        state.addTextAtIndex(start, CODE_PREFIX)
        renderCodeBlocks(state)
    }
}

private fun paragraphStart(text: AnnotatedString, cursor: Int): Int {
    val boundedCursor = cursor.coerceIn(0, text.length)
    val range = text.paragraphStyles.lastOrNull {
        boundedCursor >= it.start && boundedCursor < it.end
    } ?: text.paragraphStyles.lastOrNull { boundedCursor == it.end }
    return range?.start ?: 0
}

private fun isInQuoteParagraph(state: RichTextState): Boolean {
    return isInQuoteParagraph(state.annotatedString, state.selection.min)
}

private fun isInQuoteParagraph(text: AnnotatedString, cursor: Int): Boolean {
    if (text.isEmpty()) return false
    return text.text.startsWith(QUOTE_MARKER, paragraphStart(text, cursor))
}

private fun quoteContentStart(state: RichTextState, paragraphStart: Int): Int {
    val afterMarker = paragraphStart + QUOTE_MARKER.length
    return afterMarker + if (state.annotatedString.text.getOrNull(afterMarker) == ' ') 1 else 0
}

/** Enter continúa la cita en el párrafo siguiente, dentro del mismo editor. */
private fun continueBlockQuote(state: RichTextState) {
    state.addTextAfterSelection("\n$QUOTE_PREFIX")
    renderBlockQuotes(state)
}

/**
 * Backspace al comienzo del último párrafo citado quita solo su marcador. Los
 * párrafos anteriores siguen siendo parte de la cita.
 */
private fun exitBlockQuoteWithBackspace(state: RichTextState): Boolean {
    val selection = state.selection
    if (!selection.collapsed) return false
    val start = paragraphStart(state, selection.min)
    if (!state.annotatedString.text.startsWith(QUOTE_MARKER, start)) return false
    if (selection.min != quoteContentStart(state, start)) return false

    val nextParagraphStart = state.annotatedString.paragraphStyles
        .map { it.start }
        .firstOrNull { it > start }
    if (
        nextParagraphStart != null &&
        state.annotatedString.text.startsWith(QUOTE_MARKER, nextParagraphStart)
    ) {
        return false
    }

    state.selection = TextRange(start)
    state.removeParagraphStyle(quoteParagraphStyle)
    state.removeTextRange(TextRange(start, quoteContentStart(state, start)))
    return true
}

/**
 * Activa la cita en el párrafo actual sin partir el editor. Si ya es una cita,
 * el texto citado se queda igual y el cursor pasa al párrafo siguiente.
 */
private fun toggleQuoteParagraph(state: RichTextState) {
    val text = state.annotatedString.text
    val cursor = state.selection.min.coerceIn(0, text.length)
    val start = paragraphStart(state, cursor)
    if (isInQuoteParagraph(state)) {
        val end = paragraphEnd(state, cursor).coerceAtMost(text.length)
        state.selection = TextRange(end)
        // El marcador invisible distingue esta salida explícita de un Enter.
        state.addTextAtIndex(end, "\n$NORMAL_PARAGRAPH_MARKER")
    } else {
        state.selection = TextRange(start)
        state.addTextAtIndex(start, QUOTE_PREFIX)
        renderBlockQuotes(state)
    }
}

private fun paragraphStart(state: RichTextState, cursor: Int): Int {
    val styles = state.annotatedString.paragraphStyles
    if (styles.isEmpty()) return 0
    val range = styles.lastOrNull { cursor >= it.start && cursor < it.end }
        ?: styles.lastOrNull { cursor == it.end }
    return range?.start ?: 0
}

private fun paragraphEnd(state: RichTextState, cursor: Int): Int {
    val textLength = state.annotatedString.text.length
    val styles = state.annotatedString.paragraphStyles
    if (styles.isEmpty()) return textLength
    val range = styles.lastOrNull { cursor >= it.start && cursor < it.end }
        ?: styles.lastOrNull { cursor == it.end }
    return range?.end ?: textLength
}

/** Acceso al contenido vivo de un bloque de texto (aún sin volcar al borrador). */
private class BodyTextHandle(
    val snapshot: () -> String,
    val paragraphIndex: () -> Int,
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
                is BodyBlock.Quote -> addText(quoteMarkdown(block.markdown))
            }
        }
        if (blocks.isEmpty()) addText("")
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
                is BodyBlock.Quote -> parts += quoteMarkdown(quoteText(block, live))
                is BodyBlock.Photo -> parts += block.toMarkdown()
            }
        }
        return parts.joinToString("\n\n")
    }

    private fun textOf(block: BodyBlock.Text, live: Boolean): String = markdownOf(block.id, block.markdown, live)

    private fun quoteText(block: BodyBlock.Quote, live: Boolean): String = markdownOf(block.id, block.markdown, live)

    private fun markdownOf(id: Long, fallback: String, live: Boolean): String {
        val raw = if (live) {
            handles[id]?.snapshot?.invoke() ?: markdownById[id] ?: fallback
        } else {
            markdownById[id] ?: fallback
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
        quoteSelected = activeState?.let(::isInQuoteParagraph) == true,
        codeSelected = activeState?.let(::isInCodeParagraph) == true,
        inlineCodeSelected = activeState?.let(::isInInlineCodeParagraph) == true,
        onInsertPhoto = { showPhotoDialog = true },
        onInsertNoteLink = { activeState?.let { noteLinkState = it } },
        onToggleQuote = {
            val editor = activeState ?: return@RichMarkdownFormatToolbar
            toggleQuoteParagraph(editor)
        },
        onToggleCode = {
            val editor = activeState ?: return@RichMarkdownFormatToolbar
            toggleCodeParagraph(editor)
        },
        onToggleInlineCode = {
            val editor = activeState ?: return@RichMarkdownFormatToolbar
            toggleInlineCodeParagraph(editor)
        },
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
                        blockId = block.id,
                        initialMarkdown = block.markdown,
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

                    is BodyBlock.Quote -> QuoteTextBlock(
                        blockId = block.id,
                        initialMarkdown = block.markdown,
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
    blockId: Long,
    initialMarkdown: String,
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
            setMarkdown(ensureRichLinkBoundaries(prepareEditorMarkdown(initialMarkdown)))
            renderBlockQuotes(this)
            renderCodeBlocks(this)
            renderInlineCode(this)
            selection = TextRange.Zero
        }
    }
    // El round-trip del Markdown normaliza (listas, énfasis…), así que la
    // referencia es lo que el editor devuelve, no lo que se le dio.
    var lastKnown by remember { mutableStateOf(exportEditorMarkdown(state.toPureMarkdown())) }
    val interactionSource = remember { MutableInteractionSource() }
    val focusRequester = remember { FocusRequester() }
    val currentOnEdited by rememberUpdatedState(onEdited)
    val currentOnFlush by rememberUpdatedState(onFlush)
    val currentOnBackspace by rememberUpdatedState(onBackspaceAtStart)

    DisposableEffect(state) {
        onRegisterHandle(
            BodyTextHandle(
                snapshot = { exportEditorMarkdown(state.toPureMarkdown()) },
                paragraphIndex = {
                    val cursor = state.selection.min
                    val styles = state.annotatedString.paragraphStyles
                    if (styles.isEmpty()) 0
                    else styles.indexOfLast { cursor >= it.start && cursor <= it.end }.coerceAtLeast(0)
                },
                splitAtCursor = {
                    state.addTextAfterSelection(SPLIT_MARK)
                    val parts = exportEditorMarkdown(state.toPureMarkdown()).split(SPLIT_MARK, limit = 2)
                    parts[0] to parts.getOrElse(1) { "" }
                },
                replace = { markdown, cursor ->
                    state.setMarkdown(ensureRichLinkBoundaries(prepareEditorMarkdown(markdown)))
                    renderBlockQuotes(state)
                    renderCodeBlocks(state)
                    renderInlineCode(state)
                    val bounded = cursor.coerceIn(0, state.annotatedString.length)
                    state.selection = TextRange(bounded)
                },
            ),
        )
        onDispose {
            currentOnFlush(exportEditorMarkdown(state.toPureMarkdown()))
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
                val markdown = exportEditorMarkdown(state.toPureMarkdown())
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

    // Algunos teclados virtuales insertan el salto directamente y no generan
    // Key.Enter. Si el cursor venía de una cita y aparece un párrafo normal por
    // la inserción de un único salto, añadimos su marcador en el mismo editor.
    LaunchedEffect(state) {
        var previousText = state.annotatedString
        var previousSelection = state.selection
        snapshotFlow { state.annotatedString to state.selection }
            .collect { (text, selection) ->
                val enteredNewParagraph =
                    selection.collapsed &&
                        previousSelection.collapsed &&
                        text.length == previousText.length + 1 &&
                        selection.min == previousSelection.min + 1 &&
                        text.text.getOrNull(selection.min - 1) == ' '
                val continueQuote = enteredNewParagraph &&
                    isInQuoteParagraph(previousText, previousSelection.min) &&
                    !isInQuoteParagraph(text, selection.min)
                val continueCode = enteredNewParagraph &&
                    isInCodeParagraph(previousText, previousSelection.min) &&
                    !isInCodeParagraph(text, selection.min)

                val continueInlineCode = enteredNewParagraph &&
                    isInInlineCodeParagraph(previousText, previousSelection.min) &&
                    !isInInlineCodeParagraph(text, selection.min)

                previousText = text
                previousSelection = selection
                when {
                    continueInlineCode -> {
                        state.addTextAfterSelection(INLINE_CODE_PREFIX)
                        renderInlineCode(state)
                    }
                    continueCode -> {
                        state.addTextAfterSelection(CODE_PREFIX)
                        renderCodeBlocks(state)
                    }
                    continueQuote -> {
                        state.addTextAfterSelection(QUOTE_PREFIX)
                        renderBlockQuotes(state)
                    }
                }
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
                if (event.type != KeyEventType.KeyDown) {
                    return@onPreviewKeyEvent false
                }
                if (event.key == Key.Enter && state.selection.collapsed) {
                    when {
                        isInInlineCodeParagraph(state) -> {
                            continueInlineCode(state)
                            return@onPreviewKeyEvent true
                        }
                        isInCodeParagraph(state) -> {
                            continueCodeBlock(state)
                            return@onPreviewKeyEvent true
                        }
                        isInQuoteParagraph(state) -> {
                            continueBlockQuote(state)
                            return@onPreviewKeyEvent true
                        }
                    }
                }
                if (event.key != Key.Backspace) return@onPreviewKeyEvent false
                if (exitInlineCodeWithBackspace(state)) return@onPreviewKeyEvent true
                if (exitCodeBlockWithBackspace(state)) return@onPreviewKeyEvent true
                if (exitBlockQuoteWithBackspace(state)) return@onPreviewKeyEvent true
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

@Composable
private fun QuoteTextBlock(
    blockId: Long,
    initialMarkdown: String,
    focus: LineFocus?,
    onFocusHandled: () -> Unit,
    onFocused: (RichTextState) -> Unit,
    onRegisterHandle: (BodyTextHandle?) -> Unit,
    onFlush: (String) -> Unit,
    onEdited: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .padding(top = 2.dp, end = 10.dp, bottom = 2.dp)
                .width(4.dp)
                .fillMaxHeight()
                .background(CelesteOscuro, RoundedCornerShape(2.dp)),
        )
        Box(modifier = Modifier.weight(1f)) {
            BodyTextBlock(
                blockId = blockId,
                initialMarkdown = initialMarkdown,
                isLast = false,
                placeholder = null,
                focus = focus,
                onFocusHandled = onFocusHandled,
                onFocused = onFocused,
                onRegisterHandle = onRegisterHandle,
                onFlush = onFlush,
                onEdited = onEdited,
                onBackspaceAtStart = {},
            )
        }
    }
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

                        is BodyBlock.Quote -> ReadOnlyTextBlock(quoteMarkdown(block.markdown))

                        is BodyBlock.Photo -> BodyPhotoBlock(photo = block, onRemove = null)
                    }
                }
            }
        }
    }
}

@Composable
private fun ReadOnlyTextBlock(markdown: String) {
    val pieces = remember(markdown) { splitMarkdownPieces(markdown) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        pieces.forEach { piece ->
            when (piece) {
                is MarkdownPiece.Prose ->
                    if (piece.markdown.isNotBlank()) RichMarkdownText(piece.markdown)
                is MarkdownPiece.Quote -> Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    verticalAlignment = Alignment.Top,
                ) {
                    Box(
                        modifier = Modifier
                            .padding(top = 2.dp, end = 10.dp, bottom = 2.dp)
                            .width(4.dp)
                            .fillMaxHeight()
                            .background(CelesteOscuro, RoundedCornerShape(2.dp)),
                    )
                    Box(modifier = Modifier.weight(1f)) {
                        if (piece.markdown.isBlank()) {
                            Text(
                                text = ">",
                                style = MaterialTheme.typography.bodyLarge,
                                color = CelesteOscuro,
                            )
                        } else {
                            RichMarkdownText(piece.markdown)
                        }
                    }
                }
                is MarkdownPiece.Code -> Text(
                    text = piece.markdown.ifBlank { " " },
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF3F4F6), RoundedCornerShape(6.dp))
                        .padding(10.dp),
                    style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                    color = NegroTexto,
                )
            }
        }
    }
}

@Composable
private fun RichMarkdownText(markdown: String) {
    val state = remember(markdown) { RichTextState().apply { setMarkdown(markdown) } }
    RichText(
        state = state,
        modifier = Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.bodyLarge,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BodyPhotoBlock(
    photo: BodyBlock.Photo,
    onRemove: (() -> Unit)?,
) {
    val context = LocalContext.current
    var selected by remember { mutableStateOf(false) }
    var showFullscreen by remember { mutableStateOf(false) }
    val imageModel = remember(photo.url) { photo.imageModel() }

    if (selected) {
        BackHandler { selected = false }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .then(
                    if (selected) {
                        Modifier.border(BorderStroke(2.dp, CelesteOscuro), RoundedCornerShape(8.dp))
                    } else {
                        Modifier
                    },
                )
                .combinedClickable(
                    onClick = { if (selected) selected = false },
                    onLongClick = { selected = true },
                ),
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(imageModel)
                    .crossfade(true)
                    .build(),
                contentDescription = photo.alt.ifBlank { null },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp),
                contentScale = ContentScale.Fit,
            )
        }

        if (selected) {
            Spacer(Modifier.height(8.dp))
            PhotoActionMenu(
                canDelete = onRemove != null,
                onFullscreen = {
                    selected = false
                    showFullscreen = true
                },
                onDelete = {
                    selected = false
                    onRemove?.invoke()
                },
            )
        }
    }

    if (showFullscreen) {
        FullscreenPhotoDialog(
            imageModel = imageModel,
            contentDescription = photo.alt.ifBlank { null },
            onDismiss = { showFullscreen = false },
        )
    }
}

@Composable
private fun PhotoActionMenu(
    canDelete: Boolean,
    onFullscreen: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        color = Celeste,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.85f)),
        shadowElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PhotoMenuAction(
                icon = Icons.Filled.Fullscreen,
                label = stringResource(R.string.action_photo_fullscreen),
                contentDescription = stringResource(R.string.cd_photo_fullscreen),
                onClick = onFullscreen,
            )
            if (canDelete) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(width = 1.dp, height = 28.dp)
                        .background(Color.White.copy(alpha = 0.7f)),
                )
                PhotoMenuAction(
                    icon = Icons.Filled.Delete,
                    label = stringResource(R.string.action_photo_delete),
                    contentDescription = stringResource(R.string.cd_remove_photo),
                    onClick = onDelete,
                )
            }
        }
    }
}

@Composable
private fun PhotoMenuAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = NegroTexto,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = NegroTexto,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun FullscreenPhotoDialog(
    imageModel: Any,
    contentDescription: String?,
    onDismiss: () -> Unit,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(imageModel)
                    .crossfade(true)
                    .build(),
                contentDescription = contentDescription,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val newScale = (scale * zoom).coerceIn(1f, 6f)
                            scale = newScale
                            offset = if (newScale <= 1.01f) {
                                Offset.Zero
                            } else {
                                offset + pan
                            }
                        }
                    }
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
                contentScale = ContentScale.Fit,
            )
            Surface(
                color = Celeste,
                shape = RoundedCornerShape(50),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.85f)),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp),
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.cd_close_photo_fullscreen),
                        tint = NegroTexto,
                    )
                }
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

