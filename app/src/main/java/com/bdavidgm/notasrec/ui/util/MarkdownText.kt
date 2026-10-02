package com.bdavidgm.notasrec.ui.util

/**
 * compose-rich-editor 1.0.0-rc10 serializa párrafos vacíos consecutivos como
 * líneas `<br>`. Aunque HTML sea válido dentro de Markdown, las notas de esta
 * aplicación deben guardar Markdown puro.
 */
private val HTML_BR_LINE = Regex(
    pattern = """(?im)^[\t ]*<br[\t ]*/?>[\t ]*$""",
)
private val HTML_BR_INLINE = Regex(
    pattern = """(?i)<br[\t ]*/?>""",
)

/**
 * Elimina los `<br>` que ocupan una línea completa conservando los saltos de
 * línea circundantes. Un `<br>` incrustado se convierte en el salto duro
 * estándar de Markdown: dos espacios y nueva línea.
 */
fun ensurePureMarkdown(markdown: String): String =
    markdown
        .replace(HTML_BR_LINE, "")
        .replace(HTML_BR_INLINE, "  \n")

