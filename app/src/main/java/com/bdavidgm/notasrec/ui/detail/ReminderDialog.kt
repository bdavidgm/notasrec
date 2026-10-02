package com.bdavidgm.notasrec.ui.detail

import android.Manifest
import android.app.Activity
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bdavidgm.notasrec.R
import com.bdavidgm.notasrec.reminder.ReminderScheduler
import com.bdavidgm.notasrec.reminder.ReminderSounds
import com.bdavidgm.notasrec.ui.theme.CelesteOscuro
import com.bdavidgm.notasrec.ui.util.formatNoteInstant
import kotlinx.coroutines.launch
import java.util.Calendar

@Composable
fun ReminderDialog(
    viewModel: DetailViewModel,
    initialAtMillis: Long?,
    initialWithSound: Boolean,
    initialSoundUri: String?,
    initialSoundName: String?,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val initial = remember(initialAtMillis) {
        Calendar.getInstance().apply {
            timeInMillis = initialAtMillis ?: (System.currentTimeMillis() + 60 * 60 * 1000L)
        }
    }
    var dayMillis by remember { mutableLongStateOf(initial.timeInMillis) }
    var hour by remember { mutableIntStateOf(initial.get(Calendar.HOUR_OF_DAY)) }
    var minute by remember { mutableIntStateOf(initial.get(Calendar.MINUTE)) }
    var withSound by remember { mutableStateOf(initialWithSound) }
    var soundUri by remember { mutableStateOf(initialSoundUri) }
    var soundName by remember { mutableStateOf(initialSoundName) }
    var error by remember { mutableStateOf<String?>(null) }
    val pastError = stringResource(R.string.reminder_error_past)
    val soundError = stringResource(R.string.snackbar_audio_import_error)
    val defaultSoundName = stringResource(R.string.reminder_sound_default)

    fun combinedMillis(): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = dayMillis
        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, minute)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    val ringtonePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val picked = if (Build.VERSION.SDK_INT >= 33) {
            result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        }
        if (picked == null) {
            soundUri = null
            soundName = null
        } else {
            soundUri = picked.toString()
            soundName = RingtoneManager.getRingtone(context, picked)?.getTitle(context)
        }
    }

    val pickFile = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val copied = viewModel.copyReminderSound(uri)
            if (copied == null) {
                error = soundError
            } else {
                soundUri = copied.first
                soundName = copied.second
                error = null
            }
        }
    }

    val requestNotifications = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { _ ->
        viewModel.saveReminder(combinedMillis(), withSound, soundUri, soundName)
        onDismiss()
    }

    fun save() {
        val at = combinedMillis()
        if (at <= System.currentTimeMillis()) {
            error = pastError
            return
        }
        if (Build.VERSION.SDK_INT >= 33) {
            requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.saveReminder(at, withSound, soundUri, soundName)
            onDismiss()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.action_add_reminder)) },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(
                            R.string.reminder_day,
                            formatNoteInstant(dayMillis).substringBefore(' '),
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = {
                        val cal = Calendar.getInstance().apply { timeInMillis = dayMillis }
                        DatePickerDialog(
                            context,
                            { _, year, month, day ->
                                val next = Calendar.getInstance().apply { timeInMillis = dayMillis }
                                next.set(Calendar.YEAR, year)
                                next.set(Calendar.MONTH, month)
                                next.set(Calendar.DAY_OF_MONTH, day)
                                dayMillis = next.timeInMillis
                            },
                            cal.get(Calendar.YEAR),
                            cal.get(Calendar.MONTH),
                            cal.get(Calendar.DAY_OF_MONTH),
                        ).show()
                    }) {
                        Text(stringResource(R.string.reminder_pick_day), color = CelesteOscuro)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.reminder_time, "%02d:%02d".format(hour, minute)),
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = {
                        TimePickerDialog(
                            context,
                            { _, pickedHour, pickedMinute ->
                                hour = pickedHour
                                minute = pickedMinute
                            },
                            hour,
                            minute,
                            DateFormat.is24HourFormat(context),
                        ).show()
                    }) {
                        Text(stringResource(R.string.reminder_pick_time), color = CelesteOscuro)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.reminder_with_sound),
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = withSound, onCheckedChange = { withSound = it })
                }
                if (withSound) {
                    Text(
                        text = soundName?.takeIf { it.isNotBlank() }
                            ?: if (ReminderSounds.isAppFile(soundUri)) {
                                stringResource(R.string.reminder_sound_custom)
                            } else {
                                defaultSoundName
                            },
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(
                            onClick = {
                                val existing = soundUri?.takeUnless { ReminderSounds.isAppFile(it) }
                                    ?.let(Uri::parse)
                                val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                    putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALL)
                                    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                                    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                                    putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, existing)
                                }
                                runCatching { ringtonePicker.launch(intent) }
                            },
                        ) {
                            Text(stringResource(R.string.reminder_pick_ringtone), color = CelesteOscuro)
                        }
                        TextButton(onClick = { pickFile.launch(arrayOf("audio/*")) }) {
                            Text(stringResource(R.string.reminder_pick_file), color = CelesteOscuro)
                        }
                    }
                }
                if (!ReminderScheduler.canScheduleExact(context)) {
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.reminder_exact_alarm_hint))
                    TextButton(onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                            runCatching { context.startActivity(intent) }
                        }
                    }) {
                        Text(stringResource(R.string.reminder_allow_exact), color = CelesteOscuro)
                    }
                }
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it)
                }
                if (initialAtMillis != null) {
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = {
                        viewModel.clearReminder()
                        onDismiss()
                    }) {
                        Text(stringResource(R.string.reminder_remove), color = CelesteOscuro)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { save() }) {
                Text(stringResource(R.string.action_save_reminder))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}
