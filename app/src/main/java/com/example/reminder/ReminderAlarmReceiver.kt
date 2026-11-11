package com.example.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.local.AppDatabase
import com.example.data.model.Priority
import com.example.data.model.RepeatMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

class ReminderAlarmReceiver : BroadcastReceiver() {

  override fun onReceive(context: Context, intent: Intent) {
    val reminderId = intent.getLongExtra(EXTRA_REMINDER_ID, -1L)
    if (reminderId == -1L) return

    val title = intent.getStringExtra(EXTRA_REMINDER_TITLE) ?: "Pengingat VoxTask"
    val description = intent.getStringExtra(EXTRA_REMINDER_DESC) ?: ""
    val category = intent.getStringExtra(EXTRA_REMINDER_CATEGORY) ?: "Umum"
    val priorityStr = intent.getStringExtra(EXTRA_REMINDER_PRIORITY) ?: Priority.MEDIUM.name
    val priority = try { Priority.valueOf(priorityStr) } catch (_: Exception) { Priority.MEDIUM }

    // 1. Show notification immediately
    NotificationHelper.showReminderNotification(
      context = context,
      reminderId = reminderId,
      title = title,
      description = description,
      priority = priority,
      category = category
    )

    // 2. Check recurrence and reschedule next occurrence if needed
    val pendingResult = goAsync()
    CoroutineScope(Dispatchers.IO).launch {
      try {
        val database = AppDatabase.getDatabase(context)
        val reminder = database.reminderDao().getReminderById(reminderId)

        if (reminder != null && !reminder.isCompleted && reminder.repeatMode != RepeatMode.ONCE) {
          val nextDue = calculateNextDueTime(reminder.dueDateTime, reminder.repeatMode)
          val updatedReminder = reminder.copy(dueDateTime = nextDue)
          database.reminderDao().update(updatedReminder)

          // Schedule next repeating alarm
          AlarmScheduler(context).schedule(updatedReminder)
        }
      } catch (_: Exception) {
      } finally {
        pendingResult.finish()
      }
    }
  }

  companion object {
    const val ACTION_REMINDER_ALARM = "com.example.voxtask.ACTION_REMINDER_ALARM"
    const val EXTRA_REMINDER_ID = "extra_reminder_id"
    const val EXTRA_REMINDER_TITLE = "extra_reminder_title"
    const val EXTRA_REMINDER_DESC = "extra_reminder_desc"
    const val EXTRA_REMINDER_CATEGORY = "extra_reminder_category"
    const val EXTRA_REMINDER_PRIORITY = "extra_reminder_priority"

    fun calculateNextDueTime(
      currentDue: Long,
      repeatMode: RepeatMode,
      timeZone: java.util.TimeZone = java.util.TimeZone.getDefault(),
      baseNowMillis: Long = System.currentTimeMillis()
    ): Long {
      val originalCal = Calendar.getInstance(timeZone).apply { timeInMillis = currentDue }
      val targetDayOfMonth = originalCal.get(Calendar.DAY_OF_MONTH)
      val targetHour = originalCal.get(Calendar.HOUR_OF_DAY)
      val targetMinute = originalCal.get(Calendar.MINUTE)
      val targetSecond = originalCal.get(Calendar.SECOND)
      val targetMillis = originalCal.get(Calendar.MILLISECOND)

      val calendar = Calendar.getInstance(timeZone).apply { timeInMillis = currentDue }

      fun advanceOneStep() {
        when (repeatMode) {
          RepeatMode.DAILY -> calendar.add(Calendar.DAY_OF_YEAR, 1)
          RepeatMode.WEEKLY -> calendar.add(Calendar.WEEK_OF_YEAR, 1)
          RepeatMode.MONTHLY -> {
            calendar.set(Calendar.DAY_OF_MONTH, 1) // Avoid month overflow when adding
            calendar.add(Calendar.MONTH, 1)
            val maxDayInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
            calendar.set(Calendar.DAY_OF_MONTH, minOf(targetDayOfMonth, maxDayInMonth))
            calendar.set(Calendar.HOUR_OF_DAY, targetHour)
            calendar.set(Calendar.MINUTE, targetMinute)
            calendar.set(Calendar.SECOND, targetSecond)
            calendar.set(Calendar.MILLISECOND, targetMillis)
          }
          RepeatMode.ONCE -> { /* No-op */ }
        }
      }

      advanceOneStep()
      while (calendar.timeInMillis <= baseNowMillis && repeatMode != RepeatMode.ONCE) {
        advanceOneStep()
      }
      return calendar.timeInMillis
    }
  }
}
