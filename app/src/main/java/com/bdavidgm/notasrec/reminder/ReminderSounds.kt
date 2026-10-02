package com.bdavidgm.notasrec.reminder

import java.io.File

object ReminderSounds {
    const val APP_FILE_PREFIX = "app-file:"

    fun appFileUri(file: File): String = APP_FILE_PREFIX + file.absolutePath

    fun isAppFile(uri: String?): Boolean = uri?.startsWith(APP_FILE_PREFIX) == true

    fun fileOf(uri: String): File = File(uri.removePrefix(APP_FILE_PREFIX))
}
