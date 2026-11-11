package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.ReminderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {
  @Query("SELECT * FROM reminders ORDER BY dueDateTime ASC")
  fun getAllReminders(): Flow<List<ReminderEntity>>

  @Query("SELECT * FROM reminders WHERE isCompleted = 0 AND dueDateTime >= :currentTime ORDER BY dueDateTime ASC")
  fun getUpcomingReminders(currentTime: Long): Flow<List<ReminderEntity>>

  @Query("SELECT * FROM reminders WHERE dueDateTime BETWEEN :startOfDay AND :endOfDay ORDER BY dueDateTime ASC")
  fun getTodayReminders(startOfDay: Long, endOfDay: Long): Flow<List<ReminderEntity>>

  @Query("SELECT * FROM reminders WHERE id = :id")
  suspend fun getReminderById(id: Long): ReminderEntity?

  @Query("SELECT * FROM reminders WHERE isCompleted = 0 AND dueDateTime >= :currentTime")
  suspend fun getActiveRemindersSync(currentTime: Long): List<ReminderEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insert(reminder: ReminderEntity): Long

  @Update
  suspend fun update(reminder: ReminderEntity)

  @Delete
  suspend fun delete(reminder: ReminderEntity)

  @Query("DELETE FROM reminders WHERE id = :id")
  suspend fun deleteById(id: Long)

  @Query("UPDATE reminders SET isCompleted = :completed WHERE id = :id")
  suspend fun setCompleted(id: Long, completed: Boolean)

  @Query("SELECT COUNT(*) FROM reminders")
  fun getRemindersCount(): Flow<Int>
}
