package com.bdavidgm.notasrec.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface NotasDao {

    @Query(
        """
        SELECT n.id AS noteId, n.title AS title, n.kind AS kind, n.parentId AS parentId,
               n.createdAtMillis AS createdAtMillis, n.updatedAtMillis AS updatedAtMillis,
               n.reminderAtMillis AS reminderAtMillis,
               IFNULL(t.id, -1) AS tagId, IFNULL(t.name, '') AS tagName
        FROM notes n
        LEFT JOIN note_tags nt ON n.id = nt.noteId
        LEFT JOIN tags t ON nt.tagId = t.id
        WHERE (n.title LIKE :searchPattern OR n.content LIKE :searchPattern)
           OR n.parentId IN (
                SELECT id FROM notes
                WHERE title LIKE :searchPattern OR content LIKE :searchPattern
           )
           OR n.id IN (
                SELECT parentId FROM notes
                WHERE parentId IS NOT NULL
                  AND (title LIKE :searchPattern OR content LIKE :searchPattern)
           )
        ORDER BY n.updatedAtMillis DESC, t.name COLLATE NOCASE ASC
        """,
    )
    fun observeNoteSummaryRowsBySearch(searchPattern: String): Flow<List<NoteSummaryJoinRow>>

    @Query(
        """
        SELECT n.id AS noteId, n.uid AS uid, n.title AS title, n.content AS content,
               n.kind AS kind, n.parentId AS parentId,
               n.createdAtMillis AS createdAtMillis, n.updatedAtMillis AS updatedAtMillis,
               n.reminderAtMillis AS reminderAtMillis, n.reminderWithSound AS reminderWithSound,
               n.reminderSoundUri AS reminderSoundUri, n.reminderSoundName AS reminderSoundName,
               IFNULL(t.id, -1) AS tagId, IFNULL(t.name, '') AS tagName
        FROM notes n
        LEFT JOIN note_tags nt ON n.id = nt.noteId
        LEFT JOIN tags t ON nt.tagId = t.id
        WHERE n.id = :id
        ORDER BY t.name COLLATE NOCASE ASC
        """,
    )
    fun observeNoteTagJoinRowsForNote(id: Long): Flow<List<NoteTagJoinRow>>

    @Query("SELECT * FROM notes WHERE id = :id LIMIT 1")
    suspend fun getNote(id: Long): NoteEntity?

    @Query("SELECT id FROM notes WHERE uid = :uid LIMIT 1")
    suspend fun getNoteIdByUid(uid: String): Long?

    @Query("SELECT uid FROM notes WHERE id = :id LIMIT 1")
    suspend fun getNoteUid(id: Long): String?

    @Query(
        """
        SELECT * FROM notes
        WHERE reminderAtMillis IS NOT NULL AND reminderAtMillis > :nowMillis
        """,
    )
    suspend fun getNotesWithFutureReminders(nowMillis: Long): List<NoteEntity>

    /**
     * Candidatas para enlazar: título/contenido que coincidan, excluyendo la nota
     * actual. El diálogo solo necesita id, uid y título.
     */
    @Query(
        """
        SELECT id, uid, title, parentId, updatedAtMillis FROM notes
        WHERE id != :excludeNoteId
          AND (
            title LIKE :searchPattern OR content LIKE :searchPattern
            OR parentId IN (
                SELECT id FROM notes
                WHERE id != :excludeNoteId
                  AND (title LIKE :searchPattern OR content LIKE :searchPattern)
            )
            OR id IN (
                SELECT parentId FROM notes
                WHERE parentId IS NOT NULL
                  AND parentId != :excludeNoteId
                  AND (title LIKE :searchPattern OR content LIKE :searchPattern)
            )
          )
        """,
    )
    fun observeNotesForLink(
        searchPattern: String,
        excludeNoteId: Long,
    ): Flow<List<NoteLinkCandidateRow>>

    @Query("SELECT id FROM notes WHERE parentId = :parentId")
    suspend fun getChildNoteIds(parentId: Long): List<Long>

    @Query("UPDATE notes SET parentId = :parentId WHERE id = :noteId")
    suspend fun setNoteParent(noteId: Long, parentId: Long)

    @Insert
    suspend fun insertNote(note: NoteEntity): Long

    @Update
    suspend fun updateNote(note: NoteEntity): Int

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteNoteById(id: Long): Int

    @Query("SELECT * FROM tags ORDER BY name COLLATE NOCASE ASC")
    fun observeAllTags(): Flow<List<TagEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTag(tag: TagEntity): Long

    @Query("SELECT * FROM tags WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun getTagByName(name: String): TagEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun linkTagToNote(ref: NoteTagCrossRef): Long

    @Query("DELETE FROM note_tags WHERE noteId = :noteId AND tagId = :tagId")
    suspend fun unlinkTag(noteId: Long, tagId: Long): Int

    /** Borra la etiqueta; las uniones en note_tags caen por CASCADE. */
    @Query("DELETE FROM tags WHERE id = :tagId")
    suspend fun deleteTagById(tagId: Long): Int

    @Insert
    suspend fun insertNoteImage(image: NoteImageEntity): Long

    @Query("DELETE FROM note_images WHERE noteId = :noteId AND storedPath = :storedPath")
    suspend fun deleteImageByPath(noteId: Long, storedPath: String): Int

    @Query("SELECT * FROM note_images WHERE noteId = :noteId ORDER BY sortOrder ASC, id ASC")
    fun observeImagesForNote(noteId: Long): Flow<List<NoteImageEntity>>

    @Query("SELECT * FROM note_images WHERE noteId = :noteId ORDER BY sortOrder ASC, id ASC")
    suspend fun getImagesForNoteExport(noteId: Long): List<NoteImageEntity>

    @Query(
        """
        SELECT n.id AS noteId, n.uid AS uid, n.title AS title, n.content AS content,
               n.kind AS kind, n.parentId AS parentId,
               n.createdAtMillis AS createdAtMillis, n.updatedAtMillis AS updatedAtMillis,
               n.reminderAtMillis AS reminderAtMillis, n.reminderWithSound AS reminderWithSound,
               n.reminderSoundUri AS reminderSoundUri, n.reminderSoundName AS reminderSoundName,
               IFNULL(t.id, -1) AS tagId, IFNULL(t.name, '') AS tagName
        FROM notes n
        LEFT JOIN note_tags nt ON n.id = nt.noteId
        LEFT JOIN tags t ON nt.tagId = t.id
        ORDER BY n.updatedAtMillis DESC, t.name COLLATE NOCASE ASC
        """,
    )
    suspend fun getAllNoteTagRowsForExport(): List<NoteTagJoinRow>

    @Query("DELETE FROM note_links WHERE sourceNoteId = :sourceNoteId")
    suspend fun deleteNoteLinksForSource(sourceNoteId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNoteLink(link: NoteLinkCrossRef): Long

    @Query("SELECT * FROM note_audios WHERE noteId = :noteId ORDER BY sortOrder ASC, id ASC")
    fun observeAudiosForNote(noteId: Long): Flow<List<NoteAudioEntity>>

    @Query("SELECT * FROM note_audios WHERE noteId = :noteId ORDER BY sortOrder ASC, id ASC")
    suspend fun getAudiosForNoteExport(noteId: Long): List<NoteAudioEntity>

    @Query("SELECT * FROM note_audios WHERE id = :id LIMIT 1")
    suspend fun getAudioById(id: Long): NoteAudioEntity?

    @Insert
    suspend fun insertAudio(audio: NoteAudioEntity): Long

    @Query("DELETE FROM note_audios WHERE id = :id")
    suspend fun deleteAudioById(id: Long): Int
}
