package io.github.nithv.braindump.data

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * The on-phone database. Every change to the tables bumps [VERSION] and ships a tested migration
 * (design doc §4). Migrations are forward-only: Android can't install an older app over a newer one.
 */
@Database(
    entities = [CaptureEntity::class, ItemEntity::class, CheckpointEntity::class],
    version = BrainDumpDatabase.VERSION,
    exportSchema = true,
)
abstract class BrainDumpDatabase : RoomDatabase() {
    abstract fun captures(): CaptureDao

    abstract fun items(): ItemDao

    abstract fun checkpoints(): CheckpointDao

    companion object {
        const val NAME = "brain-dump.db"
        const val VERSION = 2

        fun open(context: Context): BrainDumpDatabase {
            // Before Room touches an older database, keep a copy of it (design doc §9, "rollback").
            runCatching { MigrationBackup(context.getDatabasePath(NAME)).beforeOpen(VERSION) }
                .onFailure { Log.w("BrainDump", "Pre-migration copy failed; opening anyway", it) }
            return Room.databaseBuilder(context, BrainDumpDatabase::class.java, NAME)
                .addMigrations(*MIGRATIONS)
                // Deliberately NO fallbackToDestructiveMigration(): a missing migration should crash
                // loudly in testing, never quietly wipe someone's dumps.
                .build()
        }
    }
}

/**
 * v1 → v2 (N5): adds the checkpoints table for Pause / Resume. Nothing existing changes, so every
 * dump and pile item is carried over untouched. The SQL is exactly what Room expects for
 * [CheckpointEntity] (see schemas/.../2.json); MigrationTest checks it, column by column.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `checkpoints` (`id` TEXT NOT NULL, `label` TEXT, `project_tag` TEXT,
               `link` TEXT, `audio_file` TEXT, `transcript` TEXT NOT NULL, `next_step` TEXT,
               `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, `resumed_at` INTEGER,
               `dismissed_at` INTEGER, PRIMARY KEY(`id`))""",
        )
        db.execSQL(
            """CREATE INDEX IF NOT EXISTS `index_checkpoints_resumed_at_dismissed_at_created_at`
               ON `checkpoints` (`resumed_at`, `dismissed_at`, `created_at`)""",
        )
    }
}

/** Every migration, oldest first. A phone on any old version walks the chain to the newest. */
val MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2)
