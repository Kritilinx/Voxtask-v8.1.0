package com.example.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.example.data.local.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmActionReceiver : BroadcastReceiver() {

  override fun onReceive(context: Context, intent: Intent) {
    val reminderId = intent.getLongExtra(EXTRA_REMINDER_ID, -1L)

    when (intent.action) {
      ACTION_STOP_ALARM -> {
        AlarmSoundPlayer.stopAlarm(context)
        if (reminderId != -1L) {
          NotificationHelper.cancelNotification(context, reminderId.toInt())
        }
      }

      ACTION_SNOOZE_ALARM -> {
        AlarmSoundPlayer.stopAlarm(context)
        if (reminderId != -1L) {
          NotificationHelper.cancelNotification(context, reminderId.toInt())

          val snoozeMillis = System.currentTimeMillis() + (5 * 60 * 1000L) // 5 minutes snooze
          val pendingResult = goAsync()
          CoroutineScope(Dispatchers.IO).launch {
            try {
              val database = AppDatabase.getDatabase(context)
              val reminder = database.reminderDao().getReminderById(reminderId)
              if (reminder != null) {
                val snoozedReminder = reminder.copy(
                  dueDateTime = snoozeMillis,
                  isCompleted = false
                )
                database.reminderDao().update(snoozedReminder)
                AlarmScheduler(context).schedule(snoozedReminder)
              }
            } catch (_: Exception) {
            } finally {
              pendingResult.finish()
            }
          }
          Toast.makeText(context, "Alarm ditunda 5 menit", Toast.LENGTH_SHORT).show()
        }
      }
    }
  }

  companion object {
    const val ACTION_STOP_ALARM = "com.example.voxtask.ACTION_STOP_ALARM"
    const val ACTION_SNOOZE_ALARM = "com.example.voxtask.ACTION_SNOOZE_ALARM"
    const val EXTRA_REMINDER_ID = "extra_reminder_id"
  }
}
