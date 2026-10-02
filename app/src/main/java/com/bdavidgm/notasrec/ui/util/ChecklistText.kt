package com.bdavidgm.notasrec.ui.util

data class ChecklistLine(
    val text: String,
    val checked: Boolean,
)

private val CHECKLIST_LINE = Regex("""^\[( |x)] (.*)$""")

/** Una nota checklist vacía se muestra con una línea en blanco lista para escribir. */
fun parseChecklist(raw: String): List<ChecklistLine> {
    if (raw.isBlank()) return listOf(ChecklistLine(text = "", checked = false))
    val items = mutableListOf<ChecklistLine>()
    for (line in raw.lineSequence()) {
        val match = CHECKLIST_LINE.matchEntire(line)
        when {
            match != null -> items += ChecklistLine(
                text = match.groupValues[2],
                checked = match.groupValues[1] == "x",
            )
            items.isNotEmpty() -> {
                val previous = items.last()
                val extra = line.removePrefix("  ")
                items[items.lastIndex] = previous.copy(text = previous.text + "\n" + extra)
            }
            else -> items += ChecklistLine(text = line, checked = false)
        }
    }
    return items.ifEmpty { listOf(ChecklistLine(text = "", checked = false)) }
}

fun encodeChecklist(items: List<ChecklistLine>): String {
    if (items.all { it.text.isBlank() && !it.checked }) return ""
    return items.joinToString("\n") { line ->
        val mark = if (line.checked) "x" else " "
        val parts = line.text.split('\n')
        buildString {
            append("[$mark] ${parts.first()}")
            for (extra in parts.drop(1)) {
                append('\n')
                append("  ")
                append(extra)
            }
        }
    }
}

/** Texto para copiar o exportar: casillas markdown y tachado en las hechas. */
fun checklistAsExportText(raw: String): String {
    val items = parseChecklist(raw).filter { it.text.isNotBlank() || it.checked }
    if (items.isEmpty()) return ""
    return items.joinToString("\n") { line ->
        val mark = if (line.checked) "x" else " "
        val parts = line.text.split('\n')
        buildString {
            append("- [$mark] ")
            append(if (line.checked) "~~${parts.first()}~~" else parts.first())
            for (extra in parts.drop(1)) {
                append("\n  ")
                append(if (line.checked) "~~$extra~~" else extra)
            }
        }
    }
}

private val EXPORTED_TASK = Regex("""^- \[( |x)] (.*)$""")
private val STRIKE_WRAP = Regex("""^~~(.*)~~$""")

/** ¿El cuerpo es solo la lista que escribe [checklistAsExportText]? */
fun bodyLooksLikeExportedChecklist(body: String): Boolean {
    val lines = body.lines().filter { it.isNotBlank() }
    if (lines.isEmpty() || !EXPORTED_TASK.matches(lines.first().trim())) return false
    return lines.all { line ->
        EXPORTED_TASK.matches(line.trim()) || line.startsWith("  ")
    }
}

/**
 * Vuelve al formato guardado en la base: tanto las líneas internas `[x] …`
 * como las exportadas `- [x] ~~…~~`.
 */
fun checklistFromExportText(raw: String): String {
    if (raw.isBlank()) return ""
    val items = mutableListOf<ChecklistLine>()
    val normalized = raw.replace("\r\n", "\n").replace('\r', '\n')
    for (line in normalized.lineSequence()) {
        val markdown = EXPORTED_TASK.matchEntire(line.trim())
        val internal = CHECKLIST_LINE.matchEntire(line)
        when {
            markdown != null -> items += ChecklistLine(
                text = unstrike(markdown.groupValues[2]),
                checked = markdown.groupValues[1] == "x",
            )
            internal != null -> items += ChecklistLine(
                text = internal.groupValues[2],
                checked = internal.groupValues[1] == "x",
            )
            items.isNotEmpty() -> {
                val previous = items.last()
                val extra = unstrike(line.removePrefix("  "))
                items[items.lastIndex] = previous.copy(text = previous.text + "\n" + extra)
            }
            else -> items += ChecklistLine(text = line, checked = false)
        }
    }
    return encodeChecklist(items)
}

private fun unstrike(text: String): String =
    STRIKE_WRAP.matchEntire(text)?.groupValues?.get(1) ?: text
