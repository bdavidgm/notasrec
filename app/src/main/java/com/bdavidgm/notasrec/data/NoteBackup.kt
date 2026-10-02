package com.bdavidgm.notasrec.data

/**
 * Tras insertar las notas de una copia, enlaza cada hija con su padre.
 * [uidMap] traduce el id de exportación al uid guardado (puede cambiar si chocaba).
 * [idByUid] es el id local de cada uid recién insertado.
 */
fun resolveImportedParents(
    notes: List<Pair<String, String>>,
    uidMap: Map<String, String>,
    idByUid: Map<String, Long>,
): List<Pair<Long, Long>> {
    val links = mutableListOf<Pair<Long, Long>>()
    for ((preferredUid, parentExportId) in notes) {
        if (parentExportId.isBlank()) continue
        val parentUid = uidMap[parentExportId] ?: continue
        val childUid = uidMap[preferredUid] ?: continue
        val parentId = idByUid[parentUid] ?: continue
        val childId = idByUid[childUid] ?: continue
        if (parentId == childId) continue
        links += childId to parentId
    }
    return links
}
