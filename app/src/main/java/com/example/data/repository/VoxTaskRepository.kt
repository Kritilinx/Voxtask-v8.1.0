package com.example.data.repository

import com.example.data.local.dao.ReminderDao
import com.example.data.local.dao.TaskDao
import com.example.data.local.dao.VoiceNoteDao
import com.example.data.local.entity.ReminderEntity
import com.example.data.local.entity.TaskEntity
import com.example.data.local.entity.VoiceNoteEntity
import kotlinx.coroutines.flow.Flow
import java.util.Calendar

class VoxTaskRepository(
  private val reminderDao: ReminderDao,
  private val taskDao: TaskDao,
  private val voiceNoteDao: VoiceNoteDao
) {
  // Reminders
  val allReminders: Flow<List<ReminderEntity>> = reminderDao.getAllReminders()
  val remindersCount: Flow<Int> = reminderDao.getRemindersCount()

  fun getUpcomingReminders(currentTime: Long = System.currentTimeMillis()): Flow<List<ReminderEntity>> {
    return reminderDao.getUpcomingReminders(currentTime)
  }

  fun getTodayReminders(): Flow<List<ReminderEntity>> {
    val (start, end) = getDayRangeTimestamps()
    return reminderDao.getTodayReminders(start, end)
  }

  suspend fun getReminderById(id: Long): ReminderEntity? = reminderDao.getReminderById(id)
  suspend fun getActiveReminders(currentTime: Long = System.currentTimeMillis()): List<ReminderEntity> =
    reminderDao.getActiveRemindersSync(currentTime)
  suspend fun insertReminder(reminder: ReminderEntity): Long = reminderDao.insert(reminder)
  suspend fun updateReminder(reminder: ReminderEntity) = reminderDao.update(reminder)
  suspend fun deleteReminder(reminder: ReminderEntity) = reminderDao.delete(reminder)
  suspend fun deleteReminderById(id: Long) = reminderDao.deleteById(id)
  suspend fun setReminderCompleted(id: Long, completed: Boolean) = reminderDao.setCompleted(id, completed)

  // Tasks
  val allTasks: Flow<List<TaskEntity>> = taskDao.getAllTasks()
  val pendingTasks: Flow<List<TaskEntity>> = taskDao.getPendingTasks()
  val tasksCount: Flow<Int> = taskDao.getTasksCount()

  fun getTodayTasks(): Flow<List<TaskEntity>> {
    val (start, end) = getDayRangeTimestamps()
    return taskDao.getTodayTasks(start, end)
  }

  suspend fun insertTask(task: TaskEntity): Long = taskDao.insert(task)
  suspend fun updateTask(task: TaskEntity) = taskDao.update(task)
  suspend fun deleteTask(task: TaskEntity) = taskDao.delete(task)
  suspend fun deleteTaskById(id: Long) = taskDao.deleteById(id)
  suspend fun setTaskCompleted(id: Long, completed: Boolean) = taskDao.setCompleted(id, completed)

  // Voice Notes
  val allVoiceNotes: Flow<List<VoiceNoteEntity>> = voiceNoteDao.getAllVoiceNotes()
  val voiceNotesCount: Flow<Int> = voiceNoteDao.getVoiceNotesCount()

  fun searchVoiceNotes(query: String): Flow<List<VoiceNoteEntity>> {
    return if (query.isBlank()) {
      voiceNoteDao.getAllVoiceNotes()
    } else {
      voiceNoteDao.searchVoiceNotes(query)
    }
  }

  suspend fun insertVoiceNote(voiceNote: VoiceNoteEntity): Long = voiceNoteDao.insert(voiceNote)
  suspend fun updateVoiceNote(voiceNote: VoiceNoteEntity) = voiceNoteDao.update(voiceNote)
  suspend fun deleteVoiceNote(voiceNote: VoiceNoteEntity) = voiceNoteDao.delete(voiceNote)
  suspend fun deleteVoiceNoteById(id: Long) = voiceNoteDao.deleteById(id)

  private fun getDayRangeTimestamps(): Pair<Long, Long> {
    val calendar = Calendar.getInstance().apply {
      set(Calendar.HOUR_OF_DAY, 0)
      set(Calendar.MINUTE, 0)
      set(Calendar.SECOND, 0)
      set(Calendar.MILLISECOND, 0)
    }
    val start = calendar.timeInMillis
    calendar.apply {
      set(Calendar.HOUR_OF_DAY, 23)
      set(Calendar.MINUTE, 59)
      set(Calendar.SECOND, 59)
      set(Calendar.MILLISECOND, 999)
    }
    val end = calendar.timeInMillis
    return Pair(start, end)
  }
}
