package com.bithead.shelter.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Evidence::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun evidenceDao(): EvidenceDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE evidence ADD COLUMN deletedFileHash TEXT")
                db.execSQL("ALTER TABLE evidence ADD COLUMN deletedAt INTEGER")
            }
        }
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE evidence ADD COLUMN incidentId TEXT")
                db.execSQL("ALTER TABLE evidence ADD COLUMN mediaType TEXT NOT NULL DEFAULT 'AUDIO'")
                db.execSQL("ALTER TABLE evidence ADD COLUMN mimeType TEXT NOT NULL DEFAULT 'audio/mp4'")
            }
        }

        fun get(context: Context): AppDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(context, AppDatabase::class.java, "shelter.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
                .also { INSTANCE = it }
        }
    }
}
