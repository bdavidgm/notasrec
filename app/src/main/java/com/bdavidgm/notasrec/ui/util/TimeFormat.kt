package com.bdavidgm.notasrec.ui.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

private val noteInstantFormat = object : ThreadLocal<SimpleDateFormat>() {
    override fun initialValue(): SimpleDateFormat =
        SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
}

fun formatNoteInstant(millis: Long): String =
    noteInstantFormat.get()!!.format(Date(millis))

fun noteTimestampLabel(createdAtMillis: Long, updatedAtMillis: Long): String {
    val edited = abs(updatedAtMillis - createdAtMillis) > 1_500L
    return if (edited) {
        "Última edición: ${formatNoteInstant(updatedAtMillis)}"
    } else {
        "Creada: ${formatNoteInstant(createdAtMillis)}"
    }
}
