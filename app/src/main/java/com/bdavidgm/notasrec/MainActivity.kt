package com.bdavidgm.notasrec

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.bdavidgm.notasrec.navigation.NotasNavHost
import com.bdavidgm.notasrec.ui.theme.NotasTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MainActivity : ComponentActivity() {

        private val _pendingOpenDocumentUri = MutableStateFlow<Uri?>(null)
        val pendingOpenDocumentUri: StateFlow<Uri?> = _pendingOpenDocumentUri.asStateFlow()

        private val _pendingOpenNoteId = MutableStateFlow<Long?>(null)
        val pendingOpenNoteId: StateFlow<Long?> = _pendingOpenNoteId.asStateFlow()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // auto() deja enforceNavigationBarContrast activo: al abrir el teclado el
        // sistema pinta una franja clara justo encima y tapa el texto de la nota.
        enableEdgeToEdge(
            navigationBarStyle = SystemBarStyle.light(
                Color.TRANSPARENT,
                Color.TRANSPARENT,
            ),
        )
        // Solo en el arranque fresco / Abrir con…; no reimportar tras rotación.
        if (savedInstanceState == null) {
            _pendingOpenDocumentUri.value = extractOpenDocumentUri(intent)
            _pendingOpenNoteId.value = extractOpenNoteId(intent)
        }
        setContent {
            NotasTheme(dynamicColor = false) {
                val pendingUri by pendingOpenDocumentUri.collectAsState()
                val pendingNoteId by pendingOpenNoteId.collectAsState()
                NotasNavHost(
                    pendingOpenDocumentUri = pendingUri,
                    onOpenDocumentConsumed = { consumed ->
                        if (_pendingOpenDocumentUri.value == consumed) {
                            _pendingOpenDocumentUri.value = null
                        }
                    },
                    pendingOpenNoteId = pendingNoteId,
                    onOpenNoteConsumed = { consumeOpenNote(it) },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        _pendingOpenDocumentUri.value = extractOpenDocumentUri(intent)
        _pendingOpenNoteId.value = extractOpenNoteId(intent)
    }

    fun consumeOpenNote(noteId: Long) {
        if (_pendingOpenNoteId.value == noteId) {
            _pendingOpenNoteId.value = null
        }
    }

    companion object {
        const val EXTRA_OPEN_NOTE_ID = "open_note_id"

        fun extractOpenNoteId(intent: Intent?): Long? {
            val id = intent?.getLongExtra(EXTRA_OPEN_NOTE_ID, -1L) ?: return null
            return id.takeIf { it >= 0L }
        }

        fun extractOpenDocumentUri(intent: Intent?): Uri? {
            if (intent == null) return null
            return when (intent.action) {
                Intent.ACTION_VIEW -> intent.data
                Intent.ACTION_SEND -> {
                    val streamUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
                    }
                    streamUri ?: intent.clipData?.getItemAt(0)?.uri
                }
                else -> null
            }
        }
    }
}
