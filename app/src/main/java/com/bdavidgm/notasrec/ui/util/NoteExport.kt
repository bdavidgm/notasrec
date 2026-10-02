package com.bdavidgm.notasrec.ui.util

import com.bdavidgm.notasrec.data.NoteKind
import java.io.File

enum class NoteExportFormat(val extension: String, val mimeType: String) {
    TXT(extension = "txt", mimeType = "text/plain"),
    MD(extension = "md", mimeType = "text/markdown"),
}

/** Foto del cuerpo que acompaña al documento exportado. */
data class NoteExportPhoto(
    val fileName: String,
    val source: File,
)

data class NoteExportPackage(
    val document: String,
    val photos: List<NoteExportPhoto>,
)

/** Cuerpo con los enlaces de foto reapuntados y los ficheros a los que apuntan. */
data class RelocatedNoteBody(
    val content: String,
    val photos: List<NoteExportPhoto>,
)

private val BODY_IMAGE_MARKDOWN = Regex("""!\[([^\]]*)]\(([^)\n]+)\)""")

data class FamilyExportNote(
    val title: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val content: String,
    val tagNames: List<String>,
    val kind: NoteKind,
    val depth: Int,
)

/** ¿Hay fotos del almacenamiento interno que haya que llevarse con la nota? */
fun hasExportablePhotos(content: String): Boolean =
    BODY_IMAGE_MARKDOWN.findAll(content).any { localPhotoFile(it.groupValues[2]) != null }

/**
 * Documento de la nota con los enlaces de foto apuntando a [photosFolderName],
 * más la lista de ficheros que hay que copiar ahí. Las rutas originales van al
 * almacenamiento privado de la app, así que fuera de ella no valen para nada.
 */
fun buildNoteExportPackage(
    title: String,
    createdAtMillis: Long,
    updatedAtMillis: Long,
    content: String,
    tagNames: List<String>,
    format: NoteExportFormat,
    photosFolderName: String,
): NoteExportPackage {
    // fotos → rutas relativas; enlaces internos → solo el texto (no sirven fuera).
    val relocated = relocateBodyPhotos(stripInternalNoteLinks(content), photosFolderName)
    return NoteExportPackage(
        document = buildNoteExportDocument(
            title = title,
            createdAtMillis = createdAtMillis,
            updatedAtMillis = updatedAtMillis,
            content = relocated.content,
            tagNames = tagNames,
            format = format,
        ),
        photos = relocated.photos,
    )
}

/**
 * Sustituye cada enlace de foto local del cuerpo por `[folder]/foto-N.ext`. Las
 * fotos del cuerpo no están en la tabla de imágenes: solo existen como ficheros
 * enlazados desde el texto, así que hay que sacarlas de aquí.
 */
fun relocateBodyPhotos(
    content: String,
    folder: String,
    namePrefix: String = "foto",
): RelocatedNoteBody {
    val photos = mutableListOf<NoteExportPhoto>()
    val rewritten = BODY_IMAGE_MARKDOWN.replace(content) { match ->
        val file = localPhotoFile(match.groupValues[2]) ?: return@replace match.value
        val fileName = "$namePrefix-${photos.size + 1}.${file.extension.ifBlank { "jpg" }}"
        photos += NoteExportPhoto(fileName = fileName, source = file)
        "![${match.groupValues[1]}]($folder/$fileName)"
    }
    return RelocatedNoteBody(content = rewritten, photos = photos)
}

private fun localPhotoFile(url: String): File? {
    val path = when {
        url.startsWith("file://") -> url.removePrefix("file://")
        url.startsWith("/") -> url
        else -> return null
    }
    return File(path).takeIf { it.isFile }
}

fun buildNoteExportDocument(
    title: String,
    createdAtMillis: Long,
    updatedAtMillis: Long,
    content: String,
    tagNames: List<String>,
    format: NoteExportFormat,
    kind: NoteKind = NoteKind.TEXT,
): String {
    val displayTitle = title.ifBlank { "Sin título" }
    val titleLine = when (format) {
        NoteExportFormat.MD -> "# $displayTitle"
        NoteExportFormat.TXT -> displayTitle
    }
    val dateLine = noteTimestampLabel(createdAtMillis, updatedAtMillis)
    val kindLine = when (kind) {
        NoteKind.CHECKLIST -> "Tipo: checklist"
        NoteKind.AUDIO -> "Tipo: audio"
        NoteKind.TEXT -> "Tipo: texto"
    }
    val tagsLine = tagNames
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .joinToString(" ") { tag ->
            if (tag.startsWith("#")) tag else "#$tag"
        }

    return buildString {
        appendLine(titleLine)
        appendLine()
        appendLine(dateLine)
        appendLine()
        appendLine(kindLine)
        appendLine()
        append(ensurePureMarkdown(content).trimEnd())
        if (tagsLine.isNotEmpty()) {
            appendLine()
            appendLine()
            append(tagsLine)
        }
        appendLine()
    }
}

/**
 * Documento de una nota y sus hijas. Cada hija va precedida de
 * `--- nota-hija N ---`, donde N es la profundidad respecto a la nota exportada.
 * El cuerpo de un checklist se escribe como lista markdown.
 */
fun buildFamilyExportDocument(
    notes: List<FamilyExportNote>,
    format: NoteExportFormat,
): String {
    val readable = notes.map { note -> note.copy(content = readableExportContent(note)) }
    return writeFamilyDocument(readable, format)
}

/** Igual que [buildFamilyExportDocument], con las fotos locales reapuntadas a [photosFolderName]. */
fun buildFamilyExportPackage(
    notes: List<FamilyExportNote>,
    format: NoteExportFormat,
    photosFolderName: String,
): NoteExportPackage {
    val photos = mutableListOf<NoteExportPhoto>()
    val readable = notes.mapIndexed { index, note ->
        val moved = relocateBodyPhotos(
            content = readableExportContent(note),
            folder = photosFolderName,
            namePrefix = "n${index + 1}-foto",
        )
        photos += moved.photos
        note.copy(content = moved.content)
    }
    return NoteExportPackage(
        document = writeFamilyDocument(readable, format),
        photos = photos,
    )
}

private fun writeFamilyDocument(
    notes: List<FamilyExportNote>,
    format: NoteExportFormat,
): String {
    if (notes.isEmpty()) return ""
    return notes.joinToString(separator = "\n") { note ->
        val marker = if (note.depth > 0) "--- nota-hija ${note.depth} ---\n\n" else ""
        marker + buildNoteExportDocument(
            title = note.title,
            createdAtMillis = note.createdAtMillis,
            updatedAtMillis = note.updatedAtMillis,
            content = note.content,
            tagNames = note.tagNames,
            format = format,
            kind = note.kind,
        ).trimEnd()
    }.trimEnd() + "\n"
}

private fun readableExportContent(note: FamilyExportNote): String {
    val readable = if (note.kind == NoteKind.CHECKLIST) {
        checklistAsExportText(note.content)
    } else {
        note.content
    }
    return stripInternalNoteLinks(readable)
}

fun suggestedNoteExportFileName(title: String, format: NoteExportFormat): String =
    "${exportBaseName(title)}.${format.extension}"

/** Nombre del ZIP que empaqueta la nota y sus fotos. */
fun suggestedNoteExportZipFileName(title: String): String = "${exportBaseName(title)}.zip"

/** Carpeta hermana del documento donde van las fotos al exportar en archivos sueltos. */
fun suggestedNotePhotosFolderName(title: String): String = "${exportBaseName(title)}_imagenes"

private fun exportBaseName(title: String): String =
    title
        .ifBlank { "nota" }
        .replace(Regex("[\\\\/:*?\"<>|\\n\\r\\t]"), "_")
        .trim()
        .take(60)
        .ifBlank { "nota" }

data class NoteCopyOptions(
    val includeTitle: Boolean = true,
    val includeBody: Boolean = true,
    val includeCreatedAt: Boolean = true,
    val includeUpdatedAt: Boolean = true,
    val includeTags: Boolean = true,
) {
    val hasAny: Boolean
        get() = includeTitle || includeBody || includeCreatedAt || includeUpdatedAt || includeTags
}

fun buildNoteCopyText(
    title: String,
    createdAtMillis: Long,
    updatedAtMillis: Long,
    content: String,
    tagNames: List<String>,
    options: NoteCopyOptions,
): String {
    if (!options.hasAny) return ""

    val parts = mutableListOf<String>()
    if (options.includeTitle) {
        parts += title.ifBlank { "Sin título" }
    }
    if (options.includeCreatedAt) {
        parts += "Creada: ${formatNoteInstant(createdAtMillis)}"
    }
    if (options.includeUpdatedAt) {
        parts += "Última edición: ${formatNoteInstant(updatedAtMillis)}"
    }
    if (options.includeBody) {
        parts += ensurePureMarkdown(removeRichLinkBoundaries(content)).trimEnd()
    }
    if (options.includeTags) {
        val tagsLine = tagNames
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(" ") { tag ->
                if (tag.startsWith("#")) tag else "#$tag"
            }
        if (tagsLine.isNotEmpty()) {
            parts += tagsLine
        }
    }
    return parts.joinToString("\n\n").trim() + "\n"
}
