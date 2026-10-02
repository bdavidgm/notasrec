package com.bdavidgm.notasrec.ui.detail

import android.Manifest
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bdavidgm.notasrec.R
import com.bdavidgm.notasrec.data.local.NoteAudioEntity
import com.bdavidgm.notasrec.ui.theme.Celeste
import com.bdavidgm.notasrec.ui.theme.CelesteOscuro
import com.bdavidgm.notasrec.ui.theme.NegroTexto
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun AudioNoteSection(
    viewModel: DetailViewModel,
    isEditing: Boolean,
) {
    if (isEditing) {
        DraftTitleField(viewModel)
        Spacer(Modifier.height(8.dp))
    } else {
        val draftTitle by viewModel.draftTitle.collectAsStateWithLifecycle()
        Text(
            text = draftTitle.ifBlank { stringResource(R.string.untitled_note) },
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.height(8.dp))
    }
    AudioTrackList(
        viewModel = viewModel,
        isEditing = isEditing,
    )
}

@Composable
private fun DraftTitleField(viewModel: DetailViewModel) {
    val draftTitle by viewModel.draftTitle.collectAsStateWithLifecycle()
    OutlinedTextField(
        value = draftTitle,
        onValueChange = viewModel::updateDraftTitle,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.field_title)) },
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Celeste,
            unfocusedBorderColor = Celeste,
            cursorColor = NegroTexto,
        ),
    )
}

@Composable
private fun AudioTrackList(
    viewModel: DetailViewModel,
    isEditing: Boolean,
) {
    val tracks by viewModel.audioTracks.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val playback = remember { AudioPlayback() }
    val recording = remember { RecordingSession() }
    var isRecording by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val recordingLabel = stringResource(R.string.audio_recording)
    val recordError = stringResource(R.string.snackbar_audio_record_error)
    val importError = stringResource(R.string.snackbar_audio_import_error)
    val permissionError = stringResource(R.string.snackbar_audio_permission)

    DisposableEffect(playback, recording) {
        onDispose {
            playback.release()
            recording.stop()?.delete()
        }
    }

    fun stopRecording(save: Boolean) {
        val file = recording.stop() ?: return
        isRecording = false
        if (!save) {
            file.delete()
            return
        }
        scope.launch {
            viewModel.registerAudioRecording(file)
        }
    }

    val pickAudio = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = viewModel.addAudioFromUri(uri)
            if (!ok) message = importError
        }
    }

    fun beginRecording() {
        message = null
        scope.launch {
            val file = viewModel.newAudioRecordingFile()
            val created = runCatching {
                val mediaRecorder = if (Build.VERSION.SDK_INT >= 31) {
                    MediaRecorder(context)
                } else {
                    @Suppress("DEPRECATION")
                    MediaRecorder()
                }
                mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)
                mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                mediaRecorder.setOutputFile(file.absolutePath)
                mediaRecorder.prepare()
                mediaRecorder.start()
                mediaRecorder
            }
            val active = created.getOrElse {
                file.delete()
                message = recordError
                return@launch
            }
            recording.recorder = active
            recording.file = file
            isRecording = true
        }
    }

    val requestMic = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) beginRecording() else message = permissionError
    }

    if (tracks.isEmpty() && !isRecording) {
        Text(
            text = stringResource(R.string.audio_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = NegroTexto.copy(alpha = 0.7f),
        )
        Spacer(Modifier.height(8.dp))
    }

    tracks.forEach { track ->
        AudioTrackRow(
            track = track,
            playing = playback.playingId == track.id,
            isEditing = isEditing,
            onToggle = { playback.toggle(track) },
            onDelete = {
                if (playback.playingId == track.id) playback.release()
                viewModel.deleteAudioTrack(track.id)
            },
        )
    }

    if (isRecording) {
        Text(
            text = recordingLabel,
            style = MaterialTheme.typography.bodyLarge,
            color = CelesteOscuro,
        )
        TextButton(onClick = { stopRecording(save = true) }) {
            Text(stringResource(R.string.audio_stop), color = CelesteOscuro)
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { requestMic.launch(Manifest.permission.RECORD_AUDIO) }) {
                Icon(Icons.Filled.Mic, contentDescription = null, tint = CelesteOscuro)
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.audio_record), color = CelesteOscuro)
            }
            TextButton(onClick = { pickAudio.launch(arrayOf("audio/*")) }) {
                Text(stringResource(R.string.audio_pick), color = CelesteOscuro)
            }
        }
    }

    message?.let { text ->
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun AudioTrackRow(
    track: NoteAudioEntity,
    playing: Boolean,
    isEditing: Boolean,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onToggle) {
            Icon(
                imageVector = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = stringResource(
                    if (playing) R.string.cd_pause_audio else R.string.cd_play_audio,
                ),
                tint = CelesteOscuro,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.displayName,
                style = MaterialTheme.typography.bodyLarge,
                color = NegroTexto,
            )
            val duration = formatAudioDuration(track.durationMillis)
            if (duration.isNotEmpty()) {
                Text(
                    text = duration,
                    style = MaterialTheme.typography.bodySmall,
                    color = NegroTexto.copy(alpha = 0.65f),
                )
            }
        }
        if (isEditing) {
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.cd_remove_audio),
                    tint = NegroTexto.copy(alpha = 0.55f),
                )
            }
        }
    }
}

private fun formatAudioDuration(millis: Long): String {
    if (millis <= 0L) return ""
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

private class RecordingSession {
    var recorder: MediaRecorder? = null
    var file: File? = null

    fun stop(): File? {
        val active = recorder
        val recorded = file
        recorder = null
        file = null
        if (active != null) {
            runCatching { active.stop() }
            runCatching { active.release() }
        }
        return recorded
    }
}

private class AudioPlayback {
    var playingId by mutableStateOf<Long?>(null)
        private set

    private var player: MediaPlayer? = null

    fun toggle(track: NoteAudioEntity) {
        if (playingId == track.id) {
            release()
            return
        }
        release()
        val mediaPlayer = MediaPlayer()
        try {
            mediaPlayer.setDataSource(track.storedPath)
            mediaPlayer.setOnCompletionListener { release() }
            mediaPlayer.prepare()
            mediaPlayer.start()
            player = mediaPlayer
            playingId = track.id
        } catch (_: Exception) {
            mediaPlayer.release()
        }
    }

    fun release() {
        player?.runCatching { stop() }
        player?.release()
        player = null
        playingId = null
    }
}
