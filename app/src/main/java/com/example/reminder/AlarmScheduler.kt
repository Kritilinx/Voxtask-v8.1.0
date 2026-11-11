package com.example.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.data.local.entity.ReminderEntity

class AlarmScheduler(private val context: Context) {

  private val alarmManager: AlarmManager? = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
  /**
   * Check whether exact alarms can be scheduled on this device safely.
   */
  fun canScheduleExactAlarms(): Boolean {
    return try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        alarmManager?.canScheduleExactAlarms() ?: false
      } else {
        true
      }
    } catch (_: Exception) {
      false
    }
  }

  /**
   * Schedule an alarm for a given reminder.
   * Uses reminder.id as unique requestCode to prevent duplicate alarms.
   */
  fun schedule(reminder: ReminderEntity) {
    // Do not schedule if reminder is already marked completed or due in the past
    val now = System.currentTimeMillis()
    if (reminder.isCompleted || reminder.dueDateTime <= now) {
      cancel(reminder.id)
      return
    }

    val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
      action = ReminderAlarmReceiver.ACTION_REMINDER_ALARM
      putExtra(ReminderAlarmReceiver.EXTRA_REMINDER_ID, reminder.id)
      putExtra(ReminderAlarmReceiver.EXTRA_REMINDER_TITLE, reminder.title)
      putExtra(ReminderAlarmReceiver.EXTRA_REMINDER_DESC, reminder.description)
      putExtra(ReminderAlarmReceiver.EXTRA_REMINDER_CATEGORY, reminder.category)
      putExtra(ReminderAlarmReceiver.EXTRA_REMINDER_PRIORITY, reminder.priority.name)
    }

    val pendingIntent = PendingIntent.getBroadcast(
      context,
      reminder.id.toInt(),
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    try {
      if (canScheduleExactAlarms()) {
        alarmManager?.setExactAndAllowWhileIdle(
          AlarmManager.RTC_WAKEUP,
          reminder.dueDateTime,
          pendingIntent
        )
      } else {
        // Safe fallback to inexact alarm when exact alarm permission is not granted
        alarmManager?.setAndAllowWhileIdle(
          AlarmManager.RTC_WAKEUP,
          reminder.dueDateTime,
          pendingIntent
        )
      }
    } catch (_: SecurityException) {
      // In case exact alarm was revoked between check and call
      try {
        alarmManager?.setAndAllowWhileIdle(
          AlarmManager.RTC_WAKEUP,
          reminder.dueDateTime,
          pendingIntent
        )
      } catch (_: Exception) {}
    } catch (_: Exception) {}
  }

  /**
   * Cancel an alarm associated with a reminder ID.
   */
  fun cancel(reminderId: Long) {
    val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
      action = ReminderAlarmReceiver.ACTION_REMINDER_ALARM
    }

    val pendingIntent = PendingIntent.getBroadcast(
      context,
      reminderId.toInt(),
      intent,
      PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
    )

    if (pendingIntent != null) {
      alarmManager?.cancel(pendingIntent)
      pendingIntent.cancel()
    }
  }
}
