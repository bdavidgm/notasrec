package com.bdavidgm.notasrec.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.util.UUID

@Database(
    entities = [
        NoteEntity::class,
        TagEntity::class,
        NoteTagCrossRef::class,
        NoteImageEntity::class,
        NoteLinkCrossRef::class,
        NoteAudioEntity::class,
    ],
    version = 6,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun notasDao(): NotasDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Añade [NoteEntity.uid] (estable para enlaces internos) y la tabla
         * [note_links]. Las notas existentes reciben un UUID en el backfill.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE notes ADD COLUMN uid TEXT NOT NULL DEFAULT ''",
                )
                db.query("SELECT id FROM notes WHERE uid = ''").use { cursor ->
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(0)
                        val uid = UUID.randomUUID().toString()
                        db.execSQL(
                            "UPDATE notes SET uid = ? WHERE id = ?",
                            arrayOf(uid, id),
                        )
                    }
                }
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_notes_uid ON notes(uid)",
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS note_links (
                        sourceNoteId INTEGER NOT NULL,
                        targetUid TEXT NOT NULL,
                        PRIMARY KEY(sourceNoteId, targetUid),
                        FOREIGN KEY(sourceNoteId) REFERENCES notes(id) ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_note_links_sourceNoteId ON note_links(sourceNoteId)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_note_links_targetUid ON note_links(targetUid)",
                )
            }
        }

        /** Distingue notas de texto y checklist. Las existentes siguen siendo de texto. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE notes ADD COLUMN kind TEXT NOT NULL DEFAULT 'TEXT'",
                )
            }
        }

        /** Relación nota padre → notas hijas. Las notas que ya existían quedan en la raíz. */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN parentId INTEGER")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_notes_parentId ON notes(parentId)",
                )
            }
        }

        /** Pistas de las notas de audio, copiadas al almacenamiento interno de la app. */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `note_audios` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `noteId` INTEGER NOT NULL,
                        `storedPath` TEXT NOT NULL,
                        `displayName` TEXT NOT NULL,
                        `durationMillis` INTEGER NOT NULL,
                        `sortOrder` INTEGER NOT NULL,
                        FOREIGN KEY(`noteId`) REFERENCES `notes`(`id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_note_audios_noteId` ON `note_audios` (`noteId`)",
                )
            }
        }

        /** Día y hora del aviso, y la melodía opcional. */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN reminderAtMillis INTEGER")
                db.execSQL(
                    "ALTER TABLE notes ADD COLUMN reminderWithSound INTEGER NOT NULL DEFAULT 0",
                )
                db.execSQL("ALTER TABLE notes ADD COLUMN reminderSoundUri TEXT")
                db.execSQL("ALTER TABLE notes ADD COLUMN reminderSoundName TEXT")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "notas.db",
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
