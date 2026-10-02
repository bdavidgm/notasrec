package com.bdavidgm.notasrec.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.bdavidgm.notasrec.data.local.NoteAudioEntity
import com.bdavidgm.notasrec.data.local.NoteEntity
import com.bdavidgm.notasrec.data.local.NoteImageEntity
import com.bdavidgm.notasrec.data.local.NoteLinkCrossRef
import com.bdavidgm.notasrec.data.local.NoteTagCrossRef
import com.bdavidgm.notasrec.data.local.NotasDao
import com.bdavidgm.notasrec.data.local.TagEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import com.bdavidgm.notasrec.ui.util.NoteExportPhoto
import com.bdavidgm.notasrec.ui.util.ParsedNoteDocument
import com.bdavidgm.notasrec.reminder.ReminderScheduler
import com.bdavidgm.notasrec.reminder.ReminderSounds
import com.bdavidgm.notasrec.ui.util.ensurePureMarkdown
import com.bdavidgm.notasrec.ui.util.extractNoteLinkUids
import com.bdavidgm.notasrec.ui.util.parsePlainNoteDocument
import com.bdavidgm.notasrec.ui.util.relocateBodyPhotos
import com.bdavidgm.notasrec.ui.util.remapNoteLinkUids

class NotasRepository(
    private val dao: NotasDao,
    private val appContext: Context,
) {

    fun observeNoteSummaries(searchPattern: String): Flow<List<NoteSummary>> =
        dao.observeNoteSummaryRowsBySearch(searchPattern).map { it.toNoteSummaryList() }

    fun observeNoteWithTags(noteId: Long): Flow<NoteWithTags?> =
        dao.observeNoteTagJoinRowsForNote(noteId).map { it.toSingleNoteWithTags() }

    fun observeAllTags(): Flow<List<TagEntity>> = dao.observeAllTags()

    /** Notas candidatas para el diálogo de enlace interno (excluye [excludeNoteId]). */
    fun observeNotesForLink(query: String, excludeNoteId: Long): Flow<List<NoteLinkCandidate>> =
        dao.observeNotesForLink(
            searchPattern = likePattern(query),
            excludeNoteId = excludeNoteId,
        ).map { rows ->
            flattenNoteTree(
                items = rows,
                idOf = { it.id },
                parentIdOf = { it.parentId },
                sortKey = { it.updatedAtMillis },
                expandedIds = null,
            ).map { entry ->
                NoteLinkCandidate(
                    id = entry.item.id,
                    uid = entry.item.uid,
                    title = entry.item.title,
                    depth = entry.depth,
                )
            }
        }

    suspend fun getNoteIdByUid(uid: String): Long? = dao.getNoteIdByUid(uid)

    suspend fun createBlankNote(kind: NoteKind = NoteKind.TEXT, parentId: Long? = null): Long {
        val now = System.currentTimeMillis()
        return dao.insertNote(
            NoteEntity(
                uid = newNoteUid(),
                title = "",
                content = "",
                kind = kind.name,
                createdAtMillis = now,
                updatedAtMillis = now,
                parentId = parentId,
            ),
        )
    }

    /**
     * Lee un .txt/.md desde [uri], crea una nota en la base de datos y devuelve su id.
     */
    suspend fun importNoteFromTextFile(uri: Uri): Long = withContext(Dispatchers.IO) {
        val text = appContext.contentResolver.openInputStream(uri)?.use { input ->
            input.bufferedReader(StandardCharsets.UTF_8).readText()
        } ?: throw IOException("No se pudo leer el archivo.")

        val displayName = queryDisplayName(uri)
            ?.substringBeforeLast('.')
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

        val parsed = parsePlainNoteDocument(text, displayName)
        insertParsedNote(parsed, parentId = null)
    }

    private suspend fun insertParsedNote(parsed: ParsedNoteDocument, parentId: Long?): Long {
        val now = System.currentTimeMillis()
        val created = parsed.createdAtMillis ?: now
        val updated = parsed.updatedAtMillis ?: now
        val noteId = dao.insertNote(
            NoteEntity(
                uid = newNoteUid(),
                title = parsed.title.trimEnd(),
                content = parsed.content,
                kind = parsed.kind.name,
                createdAtMillis = created,
                updatedAtMillis = updated,
                parentId = parentId,
            ),
        )
        for (tagName in parsed.tagNames) {
            addTagToNote(noteId, tagName)
        }
        syncNoteLinks(noteId, parsed.content)
        for (child in parsed.children) {
            insertParsedNote(child, noteId)
        }
        return noteId
    }

    private fun queryDisplayName(uri: Uri): String? {
        val projection = arrayOf(android.provider.OpenableColumns.DISPLAY_NAME)
        appContext.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index >= 0) return cursor.getString(index)
            }
        }
        return uri.lastPathSegment
    }

    suspend fun updateNote(note: NoteEntity) {
        dao.updateNote(note)
    }

    suspend fun getNote(id: Long): NoteEntity? = dao.getNote(id)

    suspend fun persistDraftIfChanged(noteId: Long, title: String, content: String) {
        val current = dao.getNote(noteId) ?: return
        val t = title.trimEnd()
        if (t == current.title && content == current.content) return
        dao.updateNote(
            current.copy(
                title = t,
                content = content,
                updatedAtMillis = System.currentTimeMillis(),
            ),
        )
        syncNoteLinks(noteId, content)
    }

    /** Reescribe la tabla note_links a partir de los `notas://` del cuerpo. */
    private suspend fun syncNoteLinks(noteId: Long, content: String) {
        dao.deleteNoteLinksForSource(noteId)
        for (uid in extractNoteLinkUids(content)) {
            dao.insertNoteLink(NoteLinkCrossRef(sourceNoteId = noteId, targetUid = uid))
        }
    }

    private fun newNoteUid(): String = UUID.randomUUID().toString()

    /** Nota abierta y sus descendientes, en preorden, para exportarlas juntas. */
    suspend fun noteFamilyForExport(rootId: Long): List<NoteFamilyMember> {
        val out = mutableListOf<NoteFamilyMember>()
        val visiting = HashSet<Long>()
        suspend fun walk(id: Long, depth: Int) {
            if (!visiting.add(id)) return
            val member = observeNoteWithTags(id).first() ?: return
            out += NoteFamilyMember(note = member, depth = depth)
            val children = dao.getChildNoteIds(id)
                .mapNotNull { dao.getNote(it) }
                .sortedByDescending { it.updatedAtMillis }
            for (child in children) walk(child.id, depth + 1)
        }
        walk(rootId, 0)
        return out
    }

    suspend fun deleteNote(noteId: Long) {
        ReminderScheduler.cancel(appContext, noteId)
        val existing = dao.getNote(noteId)
        for (childId in dao.getChildNoteIds(noteId)) {
            deleteNote(childId)
        }
        val images = dao.observeImagesForNote(noteId).first()
        val audios = dao.getAudiosForNoteExport(noteId)
        withContext(Dispatchers.IO) {
            images.forEach { File(it.storedPath).delete() }
            File(appContext.filesDir, "note_images/$noteId").deleteRecursively()
            audios.forEach { File(it.storedPath).delete() }
            File(appContext.filesDir, "note_audio/$noteId").deleteRecursively()
            existing?.reminderSoundUri?.let { uri ->
                if (ReminderSounds.isAppFile(uri)) ReminderSounds.fileOf(uri).delete()
            }
        }
        dao.deleteNoteById(noteId)
    }

    suspend fun addTagToNote(noteId: Long, rawName: String) {
        val name = rawName.trim()
        if (name.isEmpty()) return
        dao.insertTag(TagEntity(name = name))
        val tag = dao.getTagByName(name) ?: return
        dao.linkTagToNote(NoteTagCrossRef(noteId = noteId, tagId = tag.id))
    }

    suspend fun removeTagFromNote(noteId: Long, tagId: Long) {
        dao.unlinkTag(noteId, tagId)
    }

    /** Elimina la etiqueta de todas las notas y del catálogo. */
    suspend fun deleteTag(tagId: Long) {
        dao.deleteTagById(tagId)
    }

    /**
     * Copia una imagen de la galería al almacenamiento interno de la nota, la
     * registra en note_images y devuelve su ruta absoluta, que es lo que enlaza
     * el cuerpo.
     */
    suspend fun copyImageIntoNoteBody(noteId: Long, uri: Uri): String? {
        val dest = newNoteBodyImageFile(noteId)
        val copied = withContext(Dispatchers.IO) {
            appContext.contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
                true
            } ?: false
        }

        if (!copied) {
            withContext(Dispatchers.IO) { dest.delete() }
            return null
        }

        registerNoteBodyImage(noteId, dest)
        return dest.absolutePath
    }

    /** Fichero destino para una captura de cámara, dentro de la carpeta de la nota. */
    suspend fun newNoteBodyImageFile(noteId: Long): File =
        withContext(Dispatchers.IO) {
            val dir = File(appContext.filesDir, "note_images/$noteId").apply { mkdirs() }
            File(dir, "${UUID.randomUUID()}.jpg")
        }

    /**
     * Da de alta en note_images una foto ya escrita en disco (la cámara escribe
     * el fichero por su cuenta, así que se registra cuando la captura ha ido bien).
     */
    suspend fun registerNoteBodyImage(noteId: Long, file: File) {
        val existing = dao.getImagesForNoteExport(noteId)
        if (existing.any { it.storedPath == file.absolutePath }) return
        dao.insertNoteImage(
            NoteImageEntity(
                noteId = noteId,
                storedPath = file.absolutePath,
                sortOrder = existing.maxOfOrNull { it.sortOrder }?.plus(1) ?: 0,
            ),
        )
    }

    /** Quita del cuerpo una foto: fuera de la tabla y fuera del disco. */
    suspend fun removeNoteBodyImage(noteId: Long, storedPath: String) {
        dao.deleteImageByPath(noteId, storedPath)
        withContext(Dispatchers.IO) { File(storedPath).delete() }
    }

    suspend fun deleteBodyImageFile(file: File) {
        withContext(Dispatchers.IO) { file.delete() }
    }

    fun observeAudios(noteId: Long): Flow<List<NoteAudioEntity>> =
        dao.observeAudiosForNote(noteId)

    /** Copia un audio elegido por el usuario al almacenamiento interno de la nota. */
    suspend fun addAudioFromUri(noteId: Long, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val displayName = queryDisplayName(uri)?.trim().orEmpty().ifBlank { "Audio" }
        val extension = audioExtension(displayName, resolverMime(uri))
        val dest = newAudioFile(noteId, extension)
        val copied = appContext.contentResolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
            true
        } ?: false
        if (!copied || dest.length() == 0L) {
            dest.delete()
            return@withContext false
        }
        registerAudio(noteId, dest, displayName.substringBeforeLast('.').ifBlank { displayName })
        true
    }

    /** Fichero vacío donde MediaRecorder escribirá la grabación. */
    suspend fun newAudioRecordingFile(noteId: Long): File = withContext(Dispatchers.IO) {
        newAudioFile(noteId, "m4a")
    }

    /** La grabación terminó bien: pasa a la tabla de pistas. */
    suspend fun registerAudioRecording(noteId: Long, file: File) {
        if (!file.isFile || file.length() == 0L) {
            file.delete()
            return
        }
        val count = dao.getAudiosForNoteExport(noteId).size + 1
        registerAudio(noteId, file, "Pista $count")
    }

    suspend fun discardAudioFile(file: File) {
        withContext(Dispatchers.IO) { file.delete() }
    }

    suspend fun deleteAudioTrack(trackId: Long) {
        val track = dao.getAudioById(trackId) ?: return
        withContext(Dispatchers.IO) { File(track.storedPath).delete() }
        dao.deleteAudioById(trackId)
        touchNote(track.noteId)
    }

    /** Pistas de la nota y de sus hijas, con un nombre estable dentro del ZIP. */
    suspend fun exportAudioFiles(rootId: Long): List<NoteExportPhoto> {
        val out = mutableListOf<NoteExportPhoto>()
        for (member in noteFamilyForExport(rootId)) {
            val tracks = dao.getAudiosForNoteExport(member.note.note.id)
            for (track in tracks) {
                val file = File(track.storedPath)
                if (!file.isFile) continue
                val extension = file.extension.ifBlank { "m4a" }
                out += NoteExportPhoto(
                    fileName = "n${member.depth}-${track.sortOrder}.$extension",
                    source = file,
                )
            }
        }
        return out
    }

    private suspend fun registerAudio(noteId: Long, file: File, displayName: String) {
        val existing = dao.getAudiosForNoteExport(noteId)
        val duration = readDurationMillis(file)
        dao.insertAudio(
            NoteAudioEntity(
                noteId = noteId,
                storedPath = file.absolutePath,
                displayName = displayName.take(80),
                durationMillis = duration,
                sortOrder = existing.maxOfOrNull { it.sortOrder }?.plus(1) ?: 0,
            ),
        )
        touchNote(noteId)
    }

    private fun newAudioFile(noteId: Long, extension: String): File {
        val dir = File(appContext.filesDir, "note_audio/$noteId").apply { mkdirs() }
        return File(dir, "${UUID.randomUUID()}.$extension")
    }

    private fun resolverMime(uri: Uri): String? =
        appContext.contentResolver.getType(uri)

    private fun audioExtension(displayName: String, mime: String?): String {
        val fromName = displayName.substringAfterLast('.', "").lowercase()
        if (fromName in AUDIO_EXTENSIONS) return fromName
        return when (mime?.lowercase()) {
            "audio/mpeg", "audio/mp3" -> "mp3"
            "audio/mp4", "audio/m4a", "audio/x-m4a" -> "m4a"
            "audio/aac" -> "aac"
            "audio/ogg", "application/ogg" -> "ogg"
            "audio/wav", "audio/x-wav", "audio/wave" -> "wav"
            "audio/3gpp" -> "3gp"
            "audio/flac", "audio/x-flac" -> "flac"
            "audio/opus" -> "opus"
            else -> "m4a"
        }
    }

    private fun readDurationMillis(file: File): Long {
        val retriever = android.media.MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
        } catch (_: Exception) {
            0L
        } finally {
            retriever.release()
        }
    }

    private suspend fun touchNote(noteId: Long) {
        val note = dao.getNote(noteId) ?: return
        dao.updateNote(note.copy(updatedAtMillis = System.currentTimeMillis()))
    }

    suspend fun setReminder(
        noteId: Long,
        triggerAtMillis: Long,
        withSound: Boolean,
        soundUri: String?,
        soundName: String?,
    ) {
        val current = dao.getNote(noteId) ?: return
        val previous = current.reminderSoundUri
        if (ReminderSounds.isAppFile(previous) && previous != soundUri) {
            withContext(Dispatchers.IO) { ReminderSounds.fileOf(previous!!).delete() }
        }
        dao.updateNote(
            current.copy(
                reminderAtMillis = triggerAtMillis,
                reminderWithSound = withSound,
                reminderSoundUri = if (withSound) soundUri else null,
                reminderSoundName = if (withSound) soundName else null,
            ),
        )
        ReminderScheduler.schedule(appContext, noteId, triggerAtMillis)
    }

    suspend fun clearReminder(noteId: Long) {
        val current = dao.getNote(noteId) ?: return
        current.reminderSoundUri?.let { uri ->
            if (ReminderSounds.isAppFile(uri)) {
                withContext(Dispatchers.IO) { ReminderSounds.fileOf(uri).delete() }
            }
        }
        dao.updateNote(
            current.copy(
                reminderAtMillis = null,
                reminderWithSound = false,
                reminderSoundUri = null,
                reminderSoundName = null,
            ),
        )
        ReminderScheduler.cancel(appContext, noteId)
    }

    /** Copia un audio elegido por el usuario para usarlo como melodía del aviso. */
    suspend fun copyReminderSound(noteId: Long, uri: Uri): Pair<String, String>? =
        withContext(Dispatchers.IO) {
            val displayName = queryDisplayName(uri)?.trim().orEmpty().ifBlank { "Melodía" }
            val extension = audioExtension(displayName, resolverMime(uri))
            val dir = File(appContext.filesDir, "reminder_sounds").apply { mkdirs() }
            val dest = File(dir, "$noteId.$extension")
            val copied = appContext.contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
                true
            } ?: false
            if (!copied || dest.length() == 0L) {
                dest.delete()
                return@withContext null
            }
            ReminderSounds.appFileUri(dest) to displayName
        }

    /**
     * Escribe el texto de una nota exportada en el URI elegido por el usuario (.txt / .md).
     */
    suspend fun writeTextExport(destinationUri: Uri, text: String) {
        withContext(Dispatchers.IO) {
            val out = appContext.contentResolver.openOutputStream(destinationUri)
                ?: throw IOException("No se pudo escribir en el destino elegido.")
            out.bufferedWriter(StandardCharsets.UTF_8).use { writer ->
                writer.write(text)
            }
        }
    }

    /**
     * Empaqueta la nota y sus fotos en un ZIP: el documento en la raíz y las
     * fotos en [photosFolderName], que es lo que enlaza el documento.
     */
    suspend fun writeNoteExportZip(
        destinationUri: Uri,
        documentName: String,
        document: String,
        photosFolderName: String,
        photos: List<NoteExportPhoto>,
        audios: List<NoteExportPhoto> = emptyList(),
    ) {
        withContext(Dispatchers.IO) {
            // "wt" (API 26+) trunca si el destino ya existía; sin eso un ZIP a
            // medias puede quedar con basura de una escritura anterior.
            val out = if (android.os.Build.VERSION.SDK_INT >= 26) {
                appContext.contentResolver.openOutputStream(destinationUri, "wt")
            } else {
                appContext.contentResolver.openOutputStream(destinationUri)
            } ?: throw IOException("No se pudo escribir en el destino elegido.")
            ZipOutputStream(BufferedOutputStream(out)).use { zos ->
                zos.putNextEntry(ZipEntry(documentName))
                zos.write(document.toByteArray(StandardCharsets.UTF_8))
                zos.closeEntry()
                for (photo in photos) {
                    if (!photo.source.isFile) continue
                    zos.putNextEntry(ZipEntry("$photosFolderName/${photo.fileName}"))
                    photo.source.inputStream().use { input -> input.copyTo(zos) }
                    zos.closeEntry()
                }
                for (audio in audios) {
                    if (!audio.source.isFile) continue
                    zos.putNextEntry(ZipEntry("audios/${audio.fileName}"))
                    audio.source.inputStream().use { input -> input.copyTo(zos) }
                    zos.closeEntry()
                }
                zos.finish()
            }
        }
    }

    /**
     * Escribe la nota en la carpeta elegida junto a una subcarpeta con sus fotos.
     *
     * SAF renombra lo que ya existe, así que la subcarpeta se crea primero y el
     * documento se compone después con [buildDocument], ya con el nombre real;
     * si no, los enlaces relativos apuntarían a una carpeta que no es. La
     * subcarpeta recién creada está vacía, de modo que los nombres de las fotos
     * sí se respetan.
     */
    suspend fun writeNoteExportFiles(
        treeUri: Uri,
        documentName: String,
        mimeType: String,
        photosFolderName: String,
        photos: List<NoteExportPhoto>,
        buildDocument: (photosFolderName: String) -> String,
    ) {
        withContext(Dispatchers.IO) {
            val resolver = appContext.contentResolver
            val parentUri = DocumentsContract.buildDocumentUriUsingTree(
                treeUri,
                DocumentsContract.getTreeDocumentId(treeUri),
            )

            val existingPhotos = photos.filter { it.source.isFile }
            val photosFolder = if (existingPhotos.isEmpty()) {
                null
            } else {
                DocumentsContract.createDocument(
                    resolver,
                    parentUri,
                    DocumentsContract.Document.MIME_TYPE_DIR,
                    photosFolderName,
                ) ?: throw IOException("No se pudo crear la carpeta de fotos.")
            }
            val folderName = photosFolder?.let { displayName(it) } ?: photosFolderName

            val documentUri = DocumentsContract.createDocument(
                resolver,
                parentUri,
                mimeType,
                documentName,
            ) ?: throw IOException("No se pudo crear el archivo de la nota.")

            resolver.openOutputStream(documentUri)?.use { out ->
                out.bufferedWriter(StandardCharsets.UTF_8).use { writer ->
                    writer.write(buildDocument(folderName))
                }
            } ?: throw IOException("No se pudo escribir la nota.")

            if (photosFolder != null) {
                for (photo in existingPhotos) {
                    val photoUri = DocumentsContract.createDocument(
                        resolver,
                        photosFolder,
                        "image/jpeg",
                        photo.fileName,
                    ) ?: throw IOException("No se pudo crear ${photo.fileName}.")
                    resolver.openOutputStream(photoUri)?.use { out ->
                        photo.source.inputStream().use { input -> input.copyTo(out) }
                    } ?: throw IOException("No se pudo copiar ${photo.fileName}.")
                }
            }
        }
    }

    private fun displayName(uri: Uri): String? =
        appContext.contentResolver.query(
            uri,
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }

    /**
     * Exporta todas las notas (texto, etiquetas e imágenes) a un ZIP con [manifest.json].
     */
    suspend fun exportAllNotesToZip(destinationUri: Uri) {
        withContext(Dispatchers.IO) {
            val out = appContext.contentResolver.openOutputStream(destinationUri)
                ?: throw IOException("No se pudo escribir en el destino elegido.")
            BufferedOutputStream(out).use { buffered ->
                exportAllNotesToZipStream(buffered)
            }
        }
    }

    /**
     * Importa notas desde un ZIP generado por esta app. Devuelve el número de notas creadas.
     */
    suspend fun importNotesFromZip(sourceUri: Uri): Int =
        withContext(Dispatchers.IO) {
            val input = appContext.contentResolver.openInputStream(sourceUri)
                ?: throw IOException("No se pudo leer el archivo.")
            BufferedInputStream(input).use { buffered ->
                importNotesFromZipStream(buffered)
            }
        }

    private suspend fun exportAllNotesToZipStream(outputStream: OutputStream) {
        val rows = dao.getAllNoteTagRowsForExport()
        val notesWithTags = rows.toNoteWithTagsList()
        val notesJson = JSONArray()
        val imageWrites = mutableListOf<Pair<String, File>>()

        for (nwt in notesWithTags) {
            // El uid estable viaja como exportId: al restaurar los enlaces internos
            // siguen apuntando a la misma nota.
            val exportId = nwt.note.uid.ifBlank { UUID.randomUUID().toString() }
            // Las fotos incrustadas se localizan por el texto, que es lo único que
            // dice dónde va cada una; hay que reapuntar sus enlaces al ZIP.
            val body = relocateBodyPhotos(
                ensurePureMarkdown(nwt.note.content),
                "body/$exportId",
            )
            val bodyImagesJson = JSONArray()
            for (photo in body.photos) {
                val zipPath = "body/$exportId/${photo.fileName}"
                bodyImagesJson.put(zipPath)
                imageWrites.add(zipPath to photo.source)
            }

            val noteObj = JSONObject()
            noteObj.put("exportId", exportId)
            noteObj.put("title", nwt.note.title)
            noteObj.put("kind", nwt.note.kind)
            nwt.note.parentId?.let { parentId ->
                dao.getNoteUid(parentId)?.let { parentUid ->
                    noteObj.put("parentExportId", parentUid)
                }
            }
            noteObj.put("content", body.content)
            noteObj.put("bodyImages", bodyImagesJson)
            noteObj.put("createdAtMillis", nwt.note.createdAtMillis)
            noteObj.put("updatedAtMillis", nwt.note.updatedAtMillis)
            nwt.note.reminderAtMillis?.let { at ->
                noteObj.put("reminderAtMillis", at)
                noteObj.put("reminderWithSound", nwt.note.reminderWithSound)
                noteObj.put("reminderSoundName", nwt.note.reminderSoundName ?: "")
                val sound = nwt.note.reminderSoundUri
                if (ReminderSounds.isAppFile(sound)) {
                    val file = ReminderSounds.fileOf(sound!!)
                    if (file.isFile) {
                        val extension = file.extension.ifBlank { "m4a" }
                        val path = "reminders/$exportId.$extension"
                        noteObj.put("reminderSoundFile", path)
                        imageWrites.add(path to file)
                    }
                } else if (!sound.isNullOrBlank()) {
                    noteObj.put("reminderSoundUri", sound)
                }
            }
            val tagsArr = JSONArray()
            nwt.tags.forEach { tagsArr.put(it.name) }
            noteObj.put("tags", tagsArr)

            // Las fotos del cuerpo están en note_images y ya viajan en `body/`:
            // sin esto irían dos veces en el ZIP.
            val bodyPhotoPaths = body.photos.map { it.source.absolutePath }.toSet()
            val imagesJson = JSONArray()
            val images = dao.getImagesForNoteExport(nwt.note.id)
            var fileIndex = 0
            for (img in images) {
                if (img.storedPath in bodyPhotoPaths) continue
                val file = File(img.storedPath)
                if (!file.isFile) continue
                val path = "images/$exportId/$fileIndex.jpg"
                fileIndex++
                imagesJson.put(
                    JSONObject()
                        .put("path", path)
                        .put("sortOrder", img.sortOrder),
                )
                imageWrites.add(path to file)
            }
            noteObj.put("images", imagesJson)
            val audiosJson = JSONArray()
            val audios = dao.getAudiosForNoteExport(nwt.note.id)
            for (track in audios) {
                val file = File(track.storedPath)
                if (!file.isFile) continue
                val extension = file.extension.ifBlank { "m4a" }
                val path = "audios/$exportId/${track.sortOrder}.$extension"
                audiosJson.put(
                    JSONObject()
                        .put("path", path)
                        .put("displayName", track.displayName)
                        .put("durationMillis", track.durationMillis)
                        .put("sortOrder", track.sortOrder),
                )
                imageWrites.add(path to file)
            }
            noteObj.put("audios", audiosJson)
            notesJson.put(noteObj)
        }

        val manifest = JSONObject()
            .put("schemaVersion", 1)
            .put("exportedAtMillis", System.currentTimeMillis())
            .put("notes", notesJson)

        ZipOutputStream(outputStream).use { zos ->
            zos.putNextEntry(ZipEntry("manifest.json"))
            zos.write(manifest.toString(2).toByteArray(StandardCharsets.UTF_8))
            zos.closeEntry()
            for ((zipPath, file) in imageWrites) {
                zos.putNextEntry(ZipEntry(zipPath))
                file.inputStream().use { input -> input.copyTo(zos) }
                zos.closeEntry()
            }
        }
    }

    private suspend fun importNotesFromZipStream(inputStream: InputStream): Int {
        val stagingDir = File(appContext.cacheDir, "import_${UUID.randomUUID()}").apply { mkdirs() }
        return try {
            ZipInputStream(inputStream).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val outFile = resolveZipEntrySafe(stagingDir, entry.name)
                        outFile.parentFile?.mkdirs()
                        FileOutputStream(outFile).use { fos -> zis.copyTo(fos) }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            val manifestFile = File(stagingDir, "manifest.json")
            if (!manifestFile.isFile) {
                throw IOException("El archivo no contiene manifest.json válido.")
            }
            val root = JSONObject(manifestFile.readText(StandardCharsets.UTF_8))
            val version = root.optInt("schemaVersion", 0)
            if (version != 1) {
                throw IOException("Formato no compatible (versión $version).")
            }
            val notesArr = root.optJSONArray("notes")
                ?: throw IOException("El manifiesto no incluye la lista de notas.")

            // exportId del ZIP → uid real insertado (si chocaba, se regenera).
            val uidMap = mutableMapOf<String, String>()
            data class PendingNote(
                val preferredUid: String,
                val title: String,
                val content: String,
                val kind: String,
                val parentExportId: String,
                val created: Long,
                val updated: Long,
                val json: JSONObject,
            )

            suspend fun allocateUid(preferred: String): String {
                val taken = uidMap.values.toHashSet()
                if (preferred !in taken && dao.getNoteIdByUid(preferred) == null) {
                    return preferred
                }
                var candidate: String
                do {
                    candidate = UUID.randomUUID().toString()
                } while (candidate in taken || dao.getNoteIdByUid(candidate) != null)
                return candidate
            }

            val pending = mutableListOf<PendingNote>()
            for (i in 0 until notesArr.length()) {
                val n = notesArr.getJSONObject(i)
                val preferred = n.optString("exportId", "").ifBlank { UUID.randomUUID().toString() }
                val uid = allocateUid(preferred)
                uidMap[preferred] = uid
                pending += PendingNote(
                    preferredUid = preferred,
                    title = n.optString("title", ""),
                    content = n.optString("content", ""),
                    kind = NoteKind.fromStored(n.optString("kind", NoteKind.TEXT.name)).name,
                    parentExportId = n.optString("parentExportId", ""),
                    created = n.optLong("createdAtMillis", System.currentTimeMillis()),
                    updated = n.optLong("updatedAtMillis", System.currentTimeMillis()),
                    json = n,
                )
            }

            var imported = 0
            val idByUid = mutableMapOf<String, Long>()
            for (item in pending) {
                val n = item.json
                val uid = uidMap.getValue(item.preferredUid)
                var content = remapNoteLinkUids(item.content, uidMap)
                val noteId = dao.insertNote(
                    NoteEntity(
                        id = 0,
                        uid = uid,
                        title = item.title,
                        content = content,
                        kind = item.kind,
                        createdAtMillis = item.created,
                        updatedAtMillis = item.updated,
                    ),
                )
                idByUid[uid] = noteId
                val tags = n.optJSONArray("tags")
                if (tags != null) {
                    for (t in 0 until tags.length()) {
                        val name = tags.getString(t).trim()
                        if (name.isEmpty()) continue
                        dao.insertTag(TagEntity(name = name))
                        val tag = dao.getTagByName(name) ?: continue
                        dao.linkTagToNote(NoteTagCrossRef(noteId = noteId, tagId = tag.id))
                    }
                }
                // Las fotos del cuerpo vuelven al almacenamiento interno y sus
                // enlaces relativos pasan a apuntar a la copia recién hecha.
                val bodyImages = n.optJSONArray("bodyImages")
                if (bodyImages != null && bodyImages.length() > 0) {
                    val dir = File(appContext.filesDir, "note_images/$noteId").apply { mkdirs() }
                    for (j in 0 until bodyImages.length()) {
                        val path = bodyImages.getString(j)
                        val src = resolveZipEntrySafe(stagingDir, path)
                        if (!src.isFile) continue
                        val dest = File(dir, "${UUID.randomUUID()}.jpg")
                        src.inputStream().use { inp ->
                            dest.outputStream().use { out -> inp.copyTo(out) }
                        }
                        registerNoteBodyImage(noteId, dest)
                        content = content.replace(path, "file://${dest.absolutePath}")
                    }
                    dao.updateNote(
                        NoteEntity(
                            id = noteId,
                            uid = uid,
                            title = item.title,
                            content = content,
                            kind = item.kind,
                            createdAtMillis = item.created,
                            updatedAtMillis = item.updated,
                        ),
                    )
                }

                val images = n.optJSONArray("images")
                if (images != null) {
                    val pairs = mutableListOf<Pair<Int, String>>()
                    for (j in 0 until images.length()) {
                        val im = images.getJSONObject(j)
                        pairs.add(im.optInt("sortOrder", j) to im.getString("path"))
                    }
                    pairs.sortBy { it.first }
                    // Las fotos del cuerpo ya ocupan las primeras posiciones.
                    var order = dao.getImagesForNoteExport(noteId)
                        .maxOfOrNull { it.sortOrder }?.plus(1) ?: 0
                    for ((_, path) in pairs) {
                        val src = resolveZipEntrySafe(stagingDir, path)
                        if (!src.isFile) continue
                        val dir = File(appContext.filesDir, "note_images/$noteId").apply { mkdirs() }
                        val dest = File(dir, "${UUID.randomUUID()}.jpg")
                        src.inputStream().use { inp ->
                            dest.outputStream().use { out -> inp.copyTo(out) }
                        }
                        dao.insertNoteImage(
                            NoteImageEntity(
                                noteId = noteId,
                                storedPath = dest.absolutePath,
                                sortOrder = order++,
                            ),
                        )
                    }
                }
                val audios = n.optJSONArray("audios")
                if (audios != null) {
                    val dir = File(appContext.filesDir, "note_audio/$noteId").apply { mkdirs() }
                    for (j in 0 until audios.length()) {
                        val track = audios.getJSONObject(j)
                        val path = track.getString("path")
                        val src = resolveZipEntrySafe(stagingDir, path)
                        if (!src.isFile) continue
                        val extension = src.extension.ifBlank { "m4a" }
                        val dest = File(dir, "${UUID.randomUUID()}.$extension")
                        src.inputStream().use { inp ->
                            dest.outputStream().use { out -> inp.copyTo(out) }
                        }
                        dao.insertAudio(
                            NoteAudioEntity(
                                noteId = noteId,
                                storedPath = dest.absolutePath,
                                displayName = track.optString("displayName", "Pista"),
                                durationMillis = track.optLong("durationMillis", 0L),
                                sortOrder = track.optInt("sortOrder", j),
                            ),
                        )
                    }
                }
                restoreImportedReminder(stagingDir, noteId, n)
                syncNoteLinks(noteId, content)
                imported++
            }
            for ((childId, parentId) in resolveImportedParents(
                notes = pending.map { it.preferredUid to it.parentExportId },
                uidMap = uidMap,
                idByUid = idByUid,
            )) {
                dao.setNoteParent(childId, parentId)
            }
            imported
        } finally {
            stagingDir.deleteRecursively()
        }
    }

    private suspend fun restoreImportedReminder(stagingDir: File, noteId: Long, json: JSONObject) {
        if (!json.has("reminderAtMillis") || json.isNull("reminderAtMillis")) return
        val at = json.getLong("reminderAtMillis")
        val withSound = json.optBoolean("reminderWithSound", false)
        val name = json.optString("reminderSoundName", "").ifBlank { null }
        var soundUri = json.optString("reminderSoundUri", "").ifBlank { null }
        val soundFile = json.optString("reminderSoundFile", "")
        if (soundFile.isNotBlank()) {
            val src = resolveZipEntrySafe(stagingDir, soundFile)
            if (src.isFile) {
                val extension = src.extension.ifBlank { "m4a" }
                val dir = File(appContext.filesDir, "reminder_sounds").apply { mkdirs() }
                val dest = File(dir, "$noteId.$extension")
                src.inputStream().use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                }
                soundUri = ReminderSounds.appFileUri(dest)
            }
        }
        val current = dao.getNote(noteId) ?: return
        dao.updateNote(
            current.copy(
                reminderAtMillis = at,
                reminderWithSound = withSound,
                reminderSoundUri = soundUri,
                reminderSoundName = name,
            ),
        )
        if (at > System.currentTimeMillis()) {
            ReminderScheduler.schedule(appContext, noteId, at)
        }
    }

    /** Evita zip slip: la ruta descomprimida debe quedar bajo [baseDir]. */
    private fun resolveZipEntrySafe(baseDir: File, zipRelativePath: String): File {
        val child = File(baseDir, zipRelativePath)
        val baseCanon = baseDir.canonicalPath + File.separator
        val childCanon = child.canonicalPath
        if (!childCanon.startsWith(baseCanon)) {
            throw SecurityException("Entrada ZIP no permitida: $zipRelativePath")
        }
        return child
    }

    companion object {
        private val AUDIO_EXTENSIONS = setOf(
            "mp3", "m4a", "aac", "ogg", "wav", "3gp", "opus", "flac", "mp4", "amr",
        )

        fun likePattern(raw: String): String {
            if (raw.isBlank()) return "%"
            val safe = raw.replace('%', ' ').replace('_', ' ')
            return "%$safe%"
        }
    }
}
