package io.github.nithv.braindump.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * The on-phone database. Every change to the tables bumps [version] and ships a tested migration
 * (design doc §4). Migrations are forward-only: Android can't install an older app over a newer one.
 */
@Database(
    entities = [CaptureEntity::class, ItemEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class BrainDumpDatabase : RoomDatabase() {
    abstract fun captures(): CaptureDao

    companion object {
        const val NAME = "brain-dump.db"

        fun open(context: Context): BrainDumpDatabase =
            Room.databaseBuilder(context, BrainDumpDatabase::class.java, NAME)
                // Deliberately NO fallbackToDestructiveMigration(): a missing migration should crash
                // loudly in testing, never quietly wipe someone's dumps.
                .build()
    }
}
