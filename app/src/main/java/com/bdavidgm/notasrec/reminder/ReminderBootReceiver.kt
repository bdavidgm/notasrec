package com.bdavidgm.notasrec.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.bdavidgm.notasrec.data.local.AppDatabase
import kotlinx.coroutines.runBlocking
import kotlin.concurrent.thread

class ReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        val appContext = context.applicationContext
        thread {
            try {
                val notes = runBlocking {
                    AppDatabase.getInstance(appContext).notasDao()
                        .getNotesWithFutureReminders(System.currentTimeMillis())
                }
                for (note in notes) {
                    val at = note.reminderAtMillis ?: continue
                    ReminderScheduler.schedule(appContext, note.id, at)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
