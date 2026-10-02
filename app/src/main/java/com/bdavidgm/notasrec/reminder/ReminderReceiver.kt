package com.bdavidgm.notasrec.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.bdavidgm.notasrec.data.local.AppDatabase
import kotlinx.coroutines.runBlocking
import kotlin.concurrent.thread

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ReminderScheduler.ACTION_REMINDER) return
        val noteId = intent.getLongExtra(ReminderScheduler.EXTRA_NOTE_ID, -1L)
        if (noteId < 0L) return
        val pending = goAsync()
        val appContext = context.applicationContext
        thread {
            try {
                val note = runBlocking {
                    AppDatabase.getInstance(appContext).notasDao().getNote(noteId)
                }
                if (note?.reminderAtMillis != null) {
                    ReminderNotifications.show(appContext, note)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
