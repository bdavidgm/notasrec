package com.bdavidgm.notasrec.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.io.File
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.bdavidgm.notasrec.MainActivity
import com.bdavidgm.notasrec.R
import com.bdavidgm.notasrec.data.local.NoteEntity

object ReminderNotifications {

    private const val SILENT_CHANNEL = "reminders_silent"

    fun show(context: Context, note: NoteEntity) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val channelId = ensureChannel(context, note)
        val title = note.title.ifBlank { context.getString(R.string.untitled_note) }
        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_NOTE_ID, note.id)
        }
        val content = PendingIntent.getActivity(
            context,
            note.id.toInt(),
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(context.getString(R.string.notification_reminder))
            .setAutoCancel(true)
            .setContentIntent(content)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        NotificationManagerCompat.from(context).notify(note.id.toInt(), notification)
        if (note.reminderWithSound && ReminderSounds.isAppFile(note.reminderSoundUri)) {
            playAppFile(ReminderSounds.fileOf(note.reminderSoundUri!!))
        }
    }

    private fun ensureChannel(context: Context, note: NoteEntity): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return SILENT_CHANNEL
        val manager = context.getSystemService(NotificationManager::class.java)
        val (id, sound) = channelFor(note)
        if (manager.getNotificationChannel(id) == null) {
            val channel = NotificationChannel(
                id,
                context.getString(R.string.notification_channel_reminders),
                NotificationManager.IMPORTANCE_HIGH,
            )
            if (sound != null) {
                val attributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                channel.setSound(sound, attributes)
            } else {
                channel.setSound(null, null)
            }
            manager.createNotificationChannel(channel)
        }
        return id
    }

    /**
     * Las melodías del sistema van en el canal. Un fichero propio lo reproduce
     * la app, porque el sistema no puede leer el almacenamiento interno.
     */
    private fun channelFor(note: NoteEntity): Pair<String, Uri?> {
        if (!note.reminderWithSound) return SILENT_CHANNEL to null
        val stored = note.reminderSoundUri
        if (stored.isNullOrBlank() || ReminderSounds.isAppFile(stored)) {
            val fallback = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            return if (ReminderSounds.isAppFile(stored)) {
                SILENT_CHANNEL to null
            } else {
                "reminders_default" to fallback
            }
        }
        val uri = Uri.parse(stored)
        return "reminders_" + stored.hashCode().toUInt().toString(16) to uri
    }

    private fun playAppFile(file: File) {
        if (!file.isFile) return
        Handler(Looper.getMainLooper()).post {
            val player = MediaPlayer()
            try {
                player.setDataSource(file.absolutePath)
                player.setOnCompletionListener { it.release() }
                player.prepare()
                player.start()
            } catch (_: Exception) {
                player.release()
            }
        }
    }
}
