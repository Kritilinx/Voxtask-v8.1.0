package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.local.dao.ReminderDao
import com.example.data.local.dao.TaskDao
import com.example.data.local.dao.VoiceNoteDao
import com.example.data.local.entity.ReminderEntity
import com.example.data.local.entity.TaskEntity
import com.example.data.local.entity.VoiceNoteEntity

@Database(
  entities = [ReminderEntity::class, TaskEntity::class, VoiceNoteEntity::class],
  version = 2,
  exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
  abstract fun reminderDao(): ReminderDao
  abstract fun taskDao(): TaskDao
  abstract fun voiceNoteDao(): VoiceNoteDao

  companion object {
    @Volatile
    private var INSTANCE: AppDatabase? = null

    val MIGRATION_1_2 = object : Migration(1, 2) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE reminders ADD COLUMN repeatMode TEXT NOT NULL DEFAULT 'ONCE'")
      }
    }

    fun getDatabase(context: Context): AppDatabase {
      return INSTANCE ?: synchronized(this) {
        val instance = Room.databaseBuilder(
          context.applicationContext,
          AppDatabase::class.java,
          "voxtask_database"
        )
          .addMigrations(MIGRATION_1_2)
          .build()
        INSTANCE = instance
        instance
      }
    }
  }
}
