package com.bdavidgm.notasrec.ui.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.AbsoluteSizeSpan
import android.text.style.AlignmentSpan
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.LeadingMarginSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.text.style.UnderlineSpan
import android.util.DisplayMetrics
import android.util.TypedValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import com.bdavidgm.notasrec.R
import com.mohamedrejeb.richeditor.model.RichSpanStyle
import com.mohamedrejeb.richeditor.model.RichTextState
import java.io.File
import java.io.OutputStream
import kotlin.math.min
import kotlin.math.roundToInt

private const val PAGE_WIDTH = 595
private const val PAGE_HEIGHT = 842
private const val PAGE_MARGIN = 40
private const val BODY_SP = 16f
private const val QUOTE_COLOR = 0xFF3D95C6.toInt()
private const val CODE_CAPSULE_RADIUS = 8f
private const val CODE_CAPSULE_PAD = 2f

private val imageMarkdown = Regex("""!\[([^\]]*)]\(([^)\n]+)\)""")

/**
 * PDF de las notas con el mismo cuerpo que la vista Markdown: estilos del editor,
 * citas con barra y fotos entre los párrafos.
 */
fun writeMarkdownPdf(
    context: Context,
    notes: List<FamilyExportNote>,
    output: OutputStream,
) {
    val metrics = DisplayMetrics().apply {
        density = 1f
        scaledDensity = 1f
        densityDpi = 72
    }
    val document = PdfDocument()
    val writer = PdfPageWriter(document, metrics)
    try {
        if (notes.isEmpty()) {
            writer.drawLayout(layoutOf(context.getString(R.string.untitled_note), titlePaint(), metrics, writer.contentWidth))
        }
        notes.forEachIndexed { index, note ->
            if (index > 0) writer.gap(dp(metrics, 28f))
            writer.drawLayout(
                layoutOf(
                    note.title.ifBlank { context.getString(R.string.untitled_note) },
                    titlePaint(),
                    metrics,
                    writer.contentWidth,
                ),
            )
            val body = removeRichLinkBoundaries(note.content).trim()
            if (body.isNotEmpty()) {
                writer.gap(dp(metrics, 12f))
                drawMarkdown(writer, body, metrics)
            }
        }
        writer.finish()
        document.writeTo(output)
    } finally {
        writer.finish()
        document.close()
    }
}

private sealed interface MarkdownPiece {
    data class Prose(val markdown: String) : MarkdownPiece
    data class Quote(val markdown: String) : MarkdownPiece
    data class Photo(val alt: String, val url: String) : MarkdownPiece
    data class Code(val markdown: String) : MarkdownPiece
}

private fun drawMarkdown(writer: PdfPageWriter, markdown: String, metrics: DisplayMetrics) {
    splitMarkdown(markdown).forEachIndexed { index, piece ->
        if (index > 0) writer.gap(dp(metrics, 10f))
        when (piece) {
            is MarkdownPiece.Prose -> writer.drawLayout(
                richLayout(piece.markdown, writer.contentWidth, metrics),
            )
            is MarkdownPiece.Quote -> writer.drawLayout(
                richLayout(piece.markdown, writer.contentWidth - dp(metrics, 14f), metrics),
                quoted = true,
            )
            is MarkdownPiece.Photo -> writer.drawPhoto(piece.url, piece.alt, metrics)
            is MarkdownPiece.Code -> writer.drawCode(piece.markdown, metrics)
        }
    }
}

/**
 * El parser de Markdown no emite el texto de los bloques ```, así que hay que
 * separarlos antes. Dentro del bloque no se interpretan citas ni fotos.
 */
private fun splitMarkdown(markdown: String): List<MarkdownPiece> {
    if (markdown.isEmpty()) return emptyList()
    val pieces = mutableListOf<MarkdownPiece>()
    val text = mutableListOf<String>()
    val code = mutableListOf<String>()
    var inCode = false
    fun flushText() {
        if (text.isEmpty()) return
        pieces += splitImagesAndQuotes(text.joinToString("\n"))
        text.clear()
    }
    fun flushCode() {
        pieces += MarkdownPiece.Code(code.joinToString("\n"))
        code.clear()
    }
    for (line in markdown.lines()) {
        if (isCodeFenceLine(line)) {
            if (inCode) flushCode() else flushText()
            inCode = !inCode
            continue
        }
        if (inCode) code += line else text += line
    }
    if (inCode) flushCode() else flushText()
    return pieces
}

private fun isCodeFenceLine(line: String): Boolean {
    val trimmed = line.trim()
    if (!trimmed.startsWith("```")) return false
    val info = trimmed.removePrefix("```")
    return info.isEmpty() || info.all { it.isLetterOrDigit() || it == '-' || it == '_' || it == '+' }
}

private fun splitImagesAndQuotes(markdown: String): List<MarkdownPiece> {
    if (markdown.isBlank()) return emptyList()
    val pieces = mutableListOf<MarkdownPiece>()
    var cursor = 0
    imageMarkdown.findAll(markdown).forEach { match ->
        if (match.range.first > cursor) {
            pieces += splitQuotes(markdown.substring(cursor, match.range.first))
        }
        pieces += MarkdownPiece.Photo(match.groupValues[1], match.groupValues[2])
        cursor = match.range.last + 1
    }
    if (cursor < markdown.length) pieces += splitQuotes(markdown.substring(cursor))
    return pieces
}

private fun splitQuotes(markdown: String): List<MarkdownPiece> {
    val pieces = mutableListOf<MarkdownPiece>()
    val buffer = mutableListOf<String>()
    var quoting = false
    fun flush() {
        if (buffer.isEmpty()) return
        val text = buffer.joinToString("\n").trim()
        buffer.clear()
        if (text.isEmpty()) return
        pieces += if (quoting) MarkdownPiece.Quote(text) else MarkdownPiece.Prose(text)
    }
    markdown.lines().forEach { line ->
        val content = if (line.startsWith(">")) line.removePrefix(">").removePrefix(" ") else null
        val isQuote = content != null
        if (isQuote != quoting) flush()
        quoting = isQuote
        buffer += content ?: line
    }
    flush()
    return pieces
}

private fun richLayout(markdown: String, width: Int, metrics: DisplayMetrics): StaticLayout {
    val state = RichTextState().apply { setMarkdown(ensureRichLinkBoundaries(markdown)) }
    return staticLayout(
        state.annotatedString.toExportSpannable(metrics, inlineCodeRanges(state)),
        width,
        bodyPaint(),
    )
}

/** Rangos del texto ya interpretado que la vista pinta como cápsula de `código`. */
private fun inlineCodeRanges(state: RichTextState): List<IntRange> {
    val text = state.annotatedString.text
    if (text.isEmpty()) return emptyList()
    val ranges = mutableListOf<IntRange>()
    var start = -1
    for (index in text.indices) {
        val isCode = state.getRichSpanStyle(TextRange(index + 1)) is RichSpanStyle.Code
        if (isCode && start < 0) start = index
        if (!isCode && start >= 0) {
            ranges += start until index
            start = -1
        }
    }
    if (start >= 0) ranges += start until text.length
    return ranges
}

/** Marca de la cápsula. No cambia el texto; el PDF la dibuja al pintar el layout. */
private class InlineCodeSpan

private fun layoutOf(text: String, paint: TextPaint, metrics: DisplayMetrics, width: Int): StaticLayout {
    val span = SpannableStringBuilder(text)
    if (span.isNotEmpty()) {
        span.setSpan(
            AbsoluteSizeSpan(paint.textSize.roundToInt().coerceAtLeast(1), false),
            0,
            span.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
    }
    return staticLayout(span, width, paint)
}

private fun staticLayout(text: CharSequence, width: Int, paint: TextPaint): StaticLayout {
    val safeWidth = width.coerceAtLeast(1)
    return StaticLayout.Builder.obtain(text, 0, text.length, paint, safeWidth)
        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
        .setIncludePad(false)
        .setLineSpacing(0f, 1.15f)
        .build()
}

private fun AnnotatedString.toExportSpannable(
    metrics: DisplayMetrics,
    codeRanges: List<IntRange>,
): CharSequence {
    val paragraphs = paragraphStyles
    if (paragraphs.isEmpty()) return slice(0, text.length, null, metrics).marked(codeRanges, 0, text.length)
    val out = SpannableStringBuilder()
    paragraphs.forEachIndexed { index, range ->
        var end = range.end.coerceAtMost(text.length)
        if (index != paragraphs.lastIndex && end > range.start && text[end - 1] == ' ') end--
        if (end > range.start) out.append(slice(range.start, end, range.item, metrics).marked(codeRanges, range.start, end))
        if (index != paragraphs.lastIndex) out.append('\n')
    }
    return out.ifEmpty { "" }
}

private fun CharSequence.marked(codeRanges: List<IntRange>, annotatedStart: Int, annotatedEnd: Int): CharSequence {
    if (codeRanges.isEmpty() || this !is SpannableStringBuilder || isEmpty()) return this
    for (range in codeRanges) {
        val from = maxOf(range.first, annotatedStart)
        val to = minOf(range.last + 1, annotatedEnd)
        val localStart = from - annotatedStart
        val localEnd = to - annotatedStart
        if (localStart < 0 || localEnd > length || localStart >= localEnd) continue
        setSpan(InlineCodeSpan(), localStart, localEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
    return this
}

private fun AnnotatedString.slice(
    start: Int,
    end: Int,
    paragraph: ParagraphStyle?,
    metrics: DisplayMetrics,
): CharSequence {
    val safeStart = start.coerceIn(0, text.length)
    val safeEnd = end.coerceIn(safeStart, text.length)
    val out = SpannableStringBuilder(text.substring(safeStart, safeEnd))
    spanStyles.forEach { range ->
        val from = maxOf(range.start, safeStart)
        val to = minOf(range.end, safeEnd)
        if (from < to) out.applySpan(range.item, from - safeStart, to - safeStart, metrics)
    }
    paragraph?.let { style ->
        if (out.isEmpty()) return@let
        val indent = style.textIndent
        val first = indent?.firstLine.toExportPx(metrics).coerceAtLeast(0)
        val rest = indent?.restLine.toExportPx(metrics).coerceAtLeast(0)
        if (first > 0 || rest > 0) {
            out.setSpan(
                LeadingMarginSpan.Standard(first, rest),
                0,
                out.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
        val alignment = when (style.textAlign) {
            TextAlign.Center -> Layout.Alignment.ALIGN_CENTER
            TextAlign.Right, TextAlign.End -> Layout.Alignment.ALIGN_OPPOSITE
            else -> null
        }
        if (alignment != null && out.isNotEmpty()) {
            out.setSpan(AlignmentSpan.Standard(alignment), 0, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }
    return out
}

private fun SpannableStringBuilder.applySpan(
    style: SpanStyle,
    start: Int,
    end: Int,
    metrics: DisplayMetrics,
) {
    val bold = (style.fontWeight?.weight ?: 0) >= FontWeight.Bold.weight
    val italic = style.fontStyle == FontStyle.Italic
    val typefaceStyle = when {
        bold && italic -> Typeface.BOLD_ITALIC
        bold -> Typeface.BOLD
        italic -> Typeface.ITALIC
        else -> 0
    }
    if (start < 0 || end > length || start >= end) return
    if (typefaceStyle != 0) {
        setSpan(StyleSpan(typefaceStyle), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
    when (style.fontFamily) {
        FontFamily.Monospace -> setSpan(TypefaceSpan("monospace"), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        FontFamily.Serif -> setSpan(TypefaceSpan("serif"), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        FontFamily.Cursive -> setSpan(TypefaceSpan("cursive"), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        else -> Unit
    }
    if (style.textDecoration?.contains(TextDecoration.Underline) == true) {
        setSpan(UnderlineSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
    if (style.textDecoration?.contains(TextDecoration.LineThrough) == true) {
        setSpan(StrikethroughSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
    if (style.color != androidx.compose.ui.graphics.Color.Unspecified) {
        setSpan(ForegroundColorSpan(style.color.toArgb()), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
    if (style.background != androidx.compose.ui.graphics.Color.Unspecified) {
        setSpan(BackgroundColorSpan(style.background.toArgb()), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
    val size = style.fontSize.toExportPx(metrics, BODY_SP)
    if (size > 0) setSpan(AbsoluteSizeSpan(size, false), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
}

private fun TextUnit?.toExportPx(metrics: DisplayMetrics, emBaseSp: Float = BODY_SP): Int {
    if (this == null) return 0
    if (this == TextUnit.Unspecified) return 0
    val sp = when (type) {
        TextUnitType.Sp -> value
        TextUnitType.Em -> value * emBaseSp
        else -> return 0
    }
    return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, metrics).roundToInt()
}

private fun bodyPaint(): TextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
    color = Color.BLACK
    textSize = BODY_SP
}

private fun codePaint(): TextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
    color = Color.BLACK
    textSize = BODY_SP
    typeface = Typeface.MONOSPACE
}

private fun titlePaint(): TextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
    color = Color.BLACK
    textSize = 22f
}

private fun dp(metrics: DisplayMetrics, value: Float): Int =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, metrics).roundToInt()

private class PdfPageWriter(
    private val document: PdfDocument,
    private val metrics: DisplayMetrics,
) {
    val contentWidth = PAGE_WIDTH - PAGE_MARGIN * 2
    private val contentBottom = PAGE_HEIGHT - PAGE_MARGIN
    private var page: PdfDocument.Page? = null
    private var pageNumber = 0
    private var y = PAGE_MARGIN
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = QUOTE_COLOR }
    private val codeBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF3F4F6.toInt() }
    private val codeCapsulePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.LTGRAY
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    fun gap(amount: Int) {
        if (page != null) y += amount
    }

    fun drawLayout(layout: StaticLayout, quoted: Boolean = false) {
        val barWidth = if (quoted) dp(metrics, 4f) else 0
        val textLeft = PAGE_MARGIN + if (quoted) dp(metrics, 14f) else 0
        var line = 0
        while (line < layout.lineCount) {
            ensurePage()
            val available = contentBottom - y
            val lineHeight = (layout.getLineBottom(line) - layout.getLineTop(line)).coerceAtLeast(1)
            if (lineHeight > available && y > PAGE_MARGIN) {
                finishPage()
                continue
            }
            val top = layout.getLineTop(line)
            var end = line
            while (
                end + 1 < layout.lineCount &&
                layout.getLineBottom(end + 1) - top <= contentBottom - y
            ) {
                end++
            }
            val sliceHeight = layout.getLineBottom(end) - top
            val canvas = canvas()
            if (quoted) {
                canvas.drawRoundRect(
                    PAGE_MARGIN.toFloat(),
                    y.toFloat(),
                    PAGE_MARGIN + barWidth.toFloat(),
                    y + sliceHeight.toFloat(),
                    dp(metrics, 2f).toFloat(),
                    dp(metrics, 2f).toFloat(),
                    barPaint,
                )
            }
            canvas.save()
            canvas.translate(textLeft.toFloat(), (y - top).toFloat())
            val sliceBottom = layout.getLineBottom(end)
            canvas.save()
            canvas.clipRect(
                -CODE_CAPSULE_PAD,
                top - CODE_CAPSULE_PAD,
                layout.width + CODE_CAPSULE_PAD,
                sliceBottom + CODE_CAPSULE_PAD,
            )
            drawInlineCodeCapsules(canvas, layout, line, end)
            canvas.restore()
            canvas.clipRect(0, top, layout.width, sliceBottom)
            layout.draw(canvas)
            canvas.restore()
            y += sliceHeight
            line = end + 1
            if (line < layout.lineCount) finishPage()
        }
    }

    private fun drawInlineCodeCapsules(canvas: Canvas, layout: StaticLayout, firstLine: Int, lastLine: Int) {
        val text = layout.text as? Spanned ?: return
        val spans = text.getSpans(0, text.length, InlineCodeSpan::class.java)
        val path = Path()
        for (span in spans) {
            val start = text.getSpanStart(span)
            val end = text.getSpanEnd(span)
            if (start < 0 || end <= start) continue
            val spanStartLine = layout.getLineForOffset(start)
            val spanEndLine = layout.getLineForOffset(end - 1)
            if (spanEndLine < firstLine || spanStartLine > lastLine) continue
            for (lineIndex in maxOf(spanStartLine, firstLine)..minOf(spanEndLine, lastLine)) {
                val lineStart = layout.getLineStart(lineIndex)
                val lineEnd = layout.getLineEnd(lineIndex)
                val from = maxOf(start, lineStart)
                val to = minOf(end, lineEnd)
                if (from >= to) continue
                val left = layout.getPrimaryHorizontal(from)
                val right = if (to >= lineEnd) layout.getLineRight(lineIndex) else layout.getPrimaryHorizontal(to)
                val boxLeft = minOf(left, right) - CODE_CAPSULE_PAD
                val boxRight = maxOf(left, right) + CODE_CAPSULE_PAD
                if (boxRight - boxLeft <= CODE_CAPSULE_PAD) continue
                val first = lineIndex == layout.getLineForOffset(start)
                val last = lineIndex == layout.getLineForOffset(end - 1)
                val radius = CODE_CAPSULE_RADIUS
                val radii = floatArrayOf(
                    if (first) radius else 0f, if (first) radius else 0f,
                    if (last) radius else 0f, if (last) radius else 0f,
                    if (last) radius else 0f, if (last) radius else 0f,
                    if (first) radius else 0f, if (first) radius else 0f,
                )
                path.reset()
                path.addRoundRect(
                    RectF(
                        boxLeft,
                        layout.getLineTop(lineIndex) - CODE_CAPSULE_PAD,
                        boxRight,
                        layout.getLineBottom(lineIndex) + CODE_CAPSULE_PAD,
                    ),
                    radii,
                    Path.Direction.CW,
                )
                canvas.drawPath(path, codeCapsulePaint)
            }
        }
    }

    fun drawCode(text: String, metrics: DisplayMetrics) {
        val pad = dp(metrics, 10f)
        val radius = dp(metrics, 6f).toFloat()
        val layout = layoutOf(
            text.ifBlank { " " },
            codePaint(),
            metrics,
            (contentWidth - pad * 2).coerceAtLeast(1),
        )
        var line = 0
        var topPad = pad
        while (line < layout.lineCount) {
            ensurePage()
            val lineHeight = (layout.getLineBottom(line) - layout.getLineTop(line)).coerceAtLeast(1)
            if (topPad + lineHeight > contentBottom - y && y > PAGE_MARGIN) {
                finishPage()
                continue
            }
            val top = layout.getLineTop(line)
            val room = contentBottom - y - topPad
            var end = line
            while (end + 1 < layout.lineCount && layout.getLineBottom(end + 1) - top <= room) {
                end++
            }
            val sliceHeight = (layout.getLineBottom(end) - top).coerceAtLeast(1)
            val bottomPad = if (end == layout.lineCount - 1) {
                pad.coerceAtMost((contentBottom - y - topPad - sliceHeight).coerceAtLeast(0))
            } else {
                0
            }
            val canvas = canvas()
            val bottom = y + topPad + sliceHeight + bottomPad
            canvas.drawRoundRect(
                RectF(
                    PAGE_MARGIN.toFloat(),
                    y.toFloat(),
                    (PAGE_WIDTH - PAGE_MARGIN).toFloat(),
                    bottom.toFloat(),
                ),
                radius,
                radius,
                codeBackgroundPaint,
            )
            canvas.save()
            canvas.translate((PAGE_MARGIN + pad).toFloat(), (y + topPad - top).toFloat())
            canvas.clipRect(0, top, layout.width, layout.getLineBottom(end).coerceAtLeast(top + 1))
            layout.draw(canvas)
            canvas.restore()
            y = bottom
            line = end + 1
            topPad = 0
            if (line < layout.lineCount) finishPage()
        }
    }

    fun drawPhoto(url: String, alt: String, metrics: DisplayMetrics) {
        val bitmap = decodePhoto(url, contentWidth, dp(metrics, 360f))
        if (bitmap == null) {
            if (alt.isNotBlank()) drawLayout(layoutOf(alt, bodyPaint(), metrics, contentWidth))
            return
        }
        if (bitmap.width <= 0 || bitmap.height <= 0) {
            bitmap.recycle()
            return
        }
        val maxHeight = dp(metrics, 360f).toFloat()
        val scale = min(contentWidth / bitmap.width.toFloat(), maxHeight / bitmap.height.toFloat())
        if (!scale.isFinite() || scale <= 0f) {
            bitmap.recycle()
            return
        }
        val drawWidth = bitmap.width * scale
        val drawHeight = bitmap.height * scale
        if (page != null && y > PAGE_MARGIN && y + drawHeight > contentBottom) finishPage()
        ensurePage()
        val left = PAGE_MARGIN + (contentWidth - drawWidth) / 2f
        val dest = RectF(left, y.toFloat(), left + drawWidth, y + drawHeight)
        // El lienzo del PDF no admite clipPath: en algunos equipos cierra el proceso.
        canvas().drawBitmap(bitmap, null, dest, bitmapPaint)
        bitmap.recycle()
        y += drawHeight.roundToInt().coerceAtLeast(1)
    }

    fun finish() {
        finishPage()
    }

    private fun ensurePage() {
        if (page == null) {
            pageNumber++
            page = document.startPage(
                PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create(),
            )
            y = PAGE_MARGIN
            canvas().drawColor(Color.WHITE)
        }
    }

    private fun finishPage() {
        page?.let { document.finishPage(it) }
        page = null
    }

    private fun canvas(): Canvas = page!!.canvas
}

private fun decodePhoto(url: String, maxWidth: Int, maxHeight: Int): Bitmap? {
    val path = when {
        url.startsWith("file://") -> url.removePrefix("file://")
        url.startsWith("/") -> url
        else -> return null
    }
    val file = File(path)
    if (!file.isFile) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (
        sample < 64 &&
        (bounds.outWidth / sample > maxWidth * 2 || bounds.outHeight / sample > maxHeight * 2)
    ) {
        sample *= 2
    }
    val options = BitmapFactory.Options().apply {
        inSampleSize = sample
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    val decoded = BitmapFactory.decodeFile(file.path, options) ?: return null
    if (Build.VERSION.SDK_INT < 26 || decoded.config != Bitmap.Config.HARDWARE) return decoded
    val software = decoded.copy(Bitmap.Config.ARGB_8888, false)
    decoded.recycle()
    return software
}
