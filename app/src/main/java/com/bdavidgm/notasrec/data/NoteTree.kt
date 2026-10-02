package com.bdavidgm.notasrec.data

data class NoteTreeEntry<T>(
    val item: T,
    val depth: Int,
    val hasChildren: Boolean,
    val expanded: Boolean,
)

/**
 * Ordena notas en árbol: cada hija queda debajo de su padre.
 * [expandedIds] nulo muestra todos los niveles; si no, solo los padres abiertos.
 */
fun <T> flattenNoteTree(
    items: List<T>,
    idOf: (T) -> Long,
    parentIdOf: (T) -> Long?,
    sortKey: (T) -> Long,
    expandedIds: Set<Long>?,
): List<NoteTreeEntry<T>> {
    if (items.isEmpty()) return emptyList()
    val ids = items.map(idOf).toHashSet()
    val childrenOf = items.groupBy { item ->
        val parent = parentIdOf(item)
        if (parent != null && parent != idOf(item) && parent in ids) parent else null
    }
    val visiting = HashSet<Long>()
    val out = ArrayList<NoteTreeEntry<T>>(items.size)

    fun walk(item: T, depth: Int) {
        val id = idOf(item)
        if (!visiting.add(id)) return
        val children = childrenOf[id].orEmpty().sortedByDescending(sortKey)
        val hasChildren = children.isNotEmpty()
        val expanded = hasChildren && (expandedIds == null || id in expandedIds)
        out += NoteTreeEntry(item, depth, hasChildren, expanded)
        if (expanded) {
            for (child in children) walk(child, depth + 1)
        }
        visiting.remove(id)
    }

    val roots = childrenOf[null].orEmpty().sortedByDescending(sortKey)
    for (root in roots) walk(root, 0)
    return out
}
