package com.bdavidgm.notasrec.ui.util

/**
 * Enlaces internos entre notas. Se guardan como Markdown normal con el esquema
 * `notas://nota/<uid>`, así el rich editor los trata como cualquier otro enlace
 * y el ida-vuelta MD → estado → MD no los toca.
 */

const val NOTE_LINK_SCHEME = "notas"
const val NOTE_LINK_HOST = "nota"

/**
 * Límite invisible de estilo normal después de un enlace interno.
 *
 * compose-rich-editor 1.0.0-rc10 tiene un error en el borde derecho de los
 * enlaces: atribuye la escritura al párrafo siguiente. Este carácter crea un
 * span normal donde colocar el cursor sin añadir espacio visible.
 */
const val NOTE_LINK_BOUNDARY = '\u200B'

private val NOTE_LINK_URI = Regex("""^notas://nota/([^/\s?#]+)$""", RegexOption.IGNORE_CASE)

/** Markdown `[texto](notas://nota/<uid>)` — solo el destino interno. */
private val NOTE_LINK_MARKDOWN = Regex(
    """\[([^\]]*)]\(notas://nota/([^)\s]+)\)""",
    RegexOption.IGNORE_CASE,
)

/** Cualquier enlace Markdown salvo imágenes (`![…](…)`). */
private val RICH_LINK_MARKDOWN = Regex("""(?<!!)\[([^\]]*)]\(([^)\n]+)\)""")

fun noteLinkUrl(uid: String): String = "$NOTE_LINK_SCHEME://$NOTE_LINK_HOST/$uid"

/** Si [url] es un enlace interno, devuelve el uid; si no, null. */
fun parseNoteLinkUid(url: String): String? =
    NOTE_LINK_URI.matchEntire(url.trim())?.groupValues?.get(1)?.takeIf { it.isNotEmpty() }

fun isNoteLinkUrl(url: String): Boolean = parseNoteLinkUid(url) != null

/** Uids referenciados en el cuerpo (sin duplicados, en orden de aparición). */
fun extractNoteLinkUids(content: String): List<String> {
    val seen = linkedSetOf<String>()
    NOTE_LINK_MARKDOWN.findAll(content).forEach { match ->
        val uid = match.groupValues[2]
        if (uid.isNotEmpty()) seen += uid
    }
    return seen.toList()
}

/**
 * En una exportación suelta el esquema `notas://` no sirve fuera de la app:
 * deja solo el texto visible del enlace.
 */
fun stripInternalNoteLinks(content: String): String =
    NOTE_LINK_MARKDOWN.replace(content) { match ->
        match.groupValues[1].ifBlank { match.value }
    }.let(::removeRichLinkBoundaries)

/** Quita únicamente el carácter técnico invisible, conservando los enlaces. */
fun removeRichLinkBoundaries(content: String): String =
    content.replace(NOTE_LINK_BOUNDARY.toString(), "")

/**
 * Garantiza que cada enlace tenga detrás un carácter de estilo normal.
 * Es idempotente y sirve también para notas creadas antes del workaround.
 */
fun ensureRichLinkBoundaries(content: String): String =
    RICH_LINK_MARKDOWN.replace(content) { match ->
        val end = match.range.last + 1
        if (content.getOrNull(end) == NOTE_LINK_BOUNDARY) {
            match.value
        } else {
            match.value + NOTE_LINK_BOUNDARY
        }
    }

/**
 * Reescribe `notas://nota/<viejo>` → `notas://nota/<nuevo>` según [uidMap].
 * Sirve al importar una copia de seguridad cuando algún uid chocaba y se regeneró.
 */
fun remapNoteLinkUids(content: String, uidMap: Map<String, String>): String {
    if (uidMap.isEmpty()) return content
    return NOTE_LINK_MARKDOWN.replace(content) { match ->
        val text = match.groupValues[1]
        val oldUid = match.groupValues[2]
        val newUid = uidMap[oldUid] ?: return@replace match.value
        "[$text](${noteLinkUrl(newUid)})"
    }
}
