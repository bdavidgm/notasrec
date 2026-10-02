package com.bdavidgm.notasrec.ui.util

import com.bdavidgm.notasrec.data.NoteKind
import java.text.SimpleDateFormat
import java.util.Locale

data class ParsedNoteDocument(
    val title: String,
    val content: String,
    val tagNames: List<String>,
    val createdAtMillis: Long? = null,
    val updatedAtMillis: Long? = null,
    val kind: NoteKind = NoteKind.TEXT,
    val children: List<ParsedNoteDocument> = emptyList(),
)

private val signatureRegex =
    Regex("""^(Creada|Última edición):\s*(.+)$""", RegexOption.IGNORE_CASE)
private val kindLineRegex =
    Regex("""^Tipo:\s*(checklist|texto|audio)\s*$""", RegexOption.IGNORE_CASE)
private val childMarkerRegex = Regex("""(?m)^--- nota-hija (\d+) ---\s*$""")
private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

/**
 * Interpreta un .txt/.md: si coincide con el formato de exportación de la app
 * (título, firma, cuerpo, #etiquetas), lo descompone; si no, usa el nombre
 * del archivo como título y todo el texto como cuerpo.
 */
fun parseImportedNoteDocument(
    rawText: String,
    fallbackTitle: String? = null,
): ParsedNoteDocument {
    val text = rawText.replace("\r\n", "\n").replace('\r', '\n').trim()
    val sections = splitFamilySections(text)
    val root = ParsedNoteNode(parseNoteSection(sections.first().second, fallbackTitle))
    val stack = ArrayDeque<ParsedNoteNode>()
    stack.addLast(root)
    for ((depth, section) in sections.drop(1)) {
        val node = ParsedNoteNode(parseNoteSection(section, fallbackTitle = null))
        while (stack.size > depth) stack.removeLast()
        val parent = stack.lastOrNull() ?: root
        parent.children += node
        stack.addLast(node)
    }
    return root.toDocument()
}

private class ParsedNoteNode(
    section: ParsedNoteDocument,
    val children: MutableList<ParsedNoteNode> = mutableListOf(),
) {
    val title = section.title
    val content = section.content
    val tagNames = section.tagNames
    val createdAtMillis = section.createdAtMillis
    val updatedAtMillis = section.updatedAtMillis
    val kind = section.kind

    fun toDocument(): ParsedNoteDocument = ParsedNoteDocument(
        title = title,
        content = content,
        tagNames = tagNames,
        createdAtMillis = createdAtMillis,
        updatedAtMillis = updatedAtMillis,
        kind = kind,
        children = children.map { it.toDocument() },
    )
}

private fun splitFamilySections(text: String): List<Pair<Int, String>> {
    if (text.isEmpty()) return listOf(0 to "")
    val markers = childMarkerRegex.findAll(text).toList()
    if (markers.isEmpty()) return listOf(0 to text)
    val sections = mutableListOf(0 to text.substring(0, markers.first().range.first).trim())
    for (index in markers.indices) {
        val depth = markers[index].groupValues[1].toIntOrNull()?.coerceAtLeast(1) ?: 1
        val start = markers[index].range.last + 1
        val end = markers.getOrNull(index + 1)?.range?.first ?: text.length
        sections += depth to text.substring(start, end).trim()
    }
    return sections
}

private fun parseNoteSection(text: String, fallbackTitle: String?): ParsedNoteDocument {
    if (text.isEmpty()) {
        return ParsedNoteDocument(
            title = fallbackTitle.orEmpty(),
            content = "",
            tagNames = emptyList(),
        )
    }
    val blocks = text.split(Regex("\n{2,}"))
        .map { it.trim() }
        .filter { it.isNotEmpty() }
    if (blocks.isEmpty()) {
        return ParsedNoteDocument(
            title = fallbackTitle.orEmpty(),
            content = "",
            tagNames = emptyList(),
        )
    }

    var index = 0
    val title = blocks[index].lineSequence().first().trim().removePrefix("#").trim()
    index++

    var createdAt: Long? = null
    var updatedAt: Long? = null
    if (index < blocks.size) {
        val sigMatch = signatureRegex.matchEntire(blocks[index].lineSequence().first().trim())
        if (sigMatch != null) {
            val label = sigMatch.groupValues[1]
            val millis = parseExportDate(sigMatch.groupValues[2].trim())
            if (label.startsWith("Última", ignoreCase = true) ||
                label.startsWith("Ultima", ignoreCase = true)
            ) {
                updatedAt = millis
                createdAt = millis
            } else {
                createdAt = millis
                updatedAt = millis
            }
            index++
        }
    }

    var kind = NoteKind.TEXT
    var kindExplicit = false
    if (index < blocks.size) {
        val kindMatch = kindLineRegex.matchEntire(blocks[index].lineSequence().first().trim())
        if (kindMatch != null) {
            kind = when (kindMatch.groupValues[1].lowercase()) {
                "checklist" -> NoteKind.CHECKLIST
                "audio" -> NoteKind.AUDIO
                else -> NoteKind.TEXT
            }
            kindExplicit = true
            index++
        }
    }

    var tags = emptyList<String>()
    var bodyEnd = blocks.size
    if (index < blocks.size && looksLikeTagsLine(blocks.last())) {
        tags = extractHashtags(blocks.last())
        bodyEnd = blocks.size - 1
    }

    val body = if (index < bodyEnd) {
        blocks.subList(index, bodyEnd).joinToString("\n\n")
    } else {
        ""
    }
    if (!kindExplicit && bodyLooksLikeExportedChecklist(body)) {
        kind = NoteKind.CHECKLIST
    }
    val content = if (kind == NoteKind.CHECKLIST) checklistFromExportText(body) else body

    return ParsedNoteDocument(
        title = title.ifBlank { fallbackTitle.orEmpty() },
        content = content,
        tagNames = tags,
        createdAtMillis = createdAt,
        updatedAtMillis = updatedAt,
        kind = kind,
    )
}

/**
 * Para archivos planos sin formato de exportación: título = nombre de archivo,
 * contenido = texto completo.
 */
fun parsePlainNoteDocument(
    rawText: String,
    fallbackTitle: String?,
): ParsedNoteDocument {
    val structured = parseImportedNoteDocument(rawText, fallbackTitle)
    // Si el parseo estructurado dejó el cuerpo vacío y metió casi todo en el título
    // (archivo de una sola línea/párrafo sin firma), tratarlo como texto plano.
    val trimmed = rawText.replace("\r\n", "\n").replace('\r', '\n').trim()
    val blocks = trimmed.split(Regex("\n{2,}")).map { it.trim() }.filter { it.isNotEmpty() }
    val looksStructured = blocks.size >= 2 && (
        signatureRegex.containsMatchIn(blocks.getOrElse(1) { "" }) ||
            (blocks.size >= 3 && looksLikeTagsLine(blocks.last()))
        )
    return if (looksStructured) {
        structured
    } else {
        ParsedNoteDocument(
            title = fallbackTitle?.takeIf { it.isNotBlank() }.orEmpty(),
            content = trimmed,
            tagNames = emptyList(),
        )
    }
}

private fun parseExportDate(value: String): Long? =
    try {
        dateFormat.parse(value)?.time
    } catch (_: Exception) {
        null
    }

private fun looksLikeTagsLine(block: String): Boolean {
    val line = block.lineSequence().first().trim()
    if (line.isEmpty() || !line.contains('#')) return false
    val tokens = line.split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (tokens.isEmpty()) return false
    return tokens.all { token ->
        token.startsWith("#") && token.length > 1 && !token.contains('\n')
    }
}

private fun extractHashtags(block: String): List<String> =
    block.lineSequence().first().trim()
        .split(Regex("\\s+"))
        .map { it.trim().removePrefix("#").trim() }
        .filter { it.isNotEmpty() }
        .distinct()
