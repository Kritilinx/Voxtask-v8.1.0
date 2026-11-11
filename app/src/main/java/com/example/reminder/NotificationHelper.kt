@file:Suppress("DEPRECATION")
package com.example.reminder

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.model.Priority

object NotificationHelper {
  const val CHANNEL_ID = AlarmToneCoordinator.LEGACY_DEFAULT_CHANNEL_ID

  fun createNotificationChannel(context: Context) {
    AlarmToneRuntime.ensureActiveChannel(context)
  }

  fun showReminderNotification(
    context: Context,
    reminderId: Long,
    title: String,
    description: String,
    priority: Priority = Priority.MEDIUM,
    category: String = "General",
    playAudibleAlarm: Boolean = true
  ) {
    val toneResolution = AlarmToneRuntime.resolveForNotification(context)

    // 1. Play loud looping alarm sound and vibration immediately
    if (playAudibleAlarm) {
      AlarmSoundPlayer.startAlarm(context, reminderId, title)
    }

    val alarmUri = Uri.parse(toneResolution.effectiveUriString)

    // Intent when clicking the notification body (opens MainActivity & stops ringing)
    val openAppIntent = Intent(context, MainActivity::class.java).apply {
      flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
      putExtra("open_reminder_id", reminderId)
    }
    val contentPendingIntent = PendingIntent.getActivity(
      context,
      reminderId.toInt(),
      openAppIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    // Intent when notification is dismissed / swiped away (stops alarm sound)
    val deleteIntent = Intent(context, AlarmActionReceiver::class.java).apply {
      action = AlarmActionReceiver.ACTION_STOP_ALARM
      putExtra(AlarmActionReceiver.EXTRA_REMINDER_ID, reminderId)
    }
    val deletePendingIntent = PendingIntent.getBroadcast(
      context,
      (reminderId + 100000).toInt(),
      deleteIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    // Action 1: Matikan Alarm
    val stopIntent = Intent(context, AlarmActionReceiver::class.java).apply {
      action = AlarmActionReceiver.ACTION_STOP_ALARM
      putExtra(AlarmActionReceiver.EXTRA_REMINDER_ID, reminderId)
    }
    val stopPendingIntent = PendingIntent.getBroadcast(
      context,
      (reminderId + 200000).toInt(),
      stopIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    // Action 2: Tunda 5 Menit
    val snoozeIntent = Intent(context, AlarmActionReceiver::class.java).apply {
      action = AlarmActionReceiver.ACTION_SNOOZE_ALARM
      putExtra(AlarmActionReceiver.EXTRA_REMINDER_ID, reminderId)
    }
    val snoozePendingIntent = PendingIntent.getBroadcast(
      context,
      (reminderId + 300000).toInt(),
      snoozeIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val notificationPriority = when (priority) {
      Priority.HIGH -> NotificationCompat.PRIORITY_MAX
      Priority.MEDIUM -> NotificationCompat.PRIORITY_HIGH
      Priority.LOW -> NotificationCompat.PRIORITY_DEFAULT
    }

    val displayContent = if (description.isNotBlank()) description else "Waktu pengingat telah tiba! (Kategori: $category)"

    val builder = NotificationCompat.Builder(context, toneResolution.channelId)
      .setSmallIcon(R.drawable.ic_launcher_foreground)
      .setContentTitle("🔔 $title")
      .setContentText(displayContent)
      .setStyle(
        NotificationCompat.BigTextStyle().bigText(
          "$displayContent\nPrioritas: ${priority.name} • Kategori: $category"
        )
      )
      .setPriority(notificationPriority)
      .setCategory(NotificationCompat.CATEGORY_ALARM)
      .setSound(alarmUri, AudioManager.STREAM_ALARM)
      .setVibrate(longArrayOf(0, 800, 400, 800, 400, 800))
      .setOngoing(true)
      .setAutoCancel(true)
      .setContentIntent(contentPendingIntent)
      .setDeleteIntent(deletePendingIntent)
      .setFullScreenIntent(contentPendingIntent, true)
      .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
      .addAction(
        android.R.drawable.ic_menu_close_clear_cancel,
        "Matikan Alarm",
        stopPendingIntent
      )
      .addAction(
        android.R.drawable.ic_popup_reminder,
        "Tunda 5 Menit",
        snoozePendingIntent
      )

    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    notificationManager.notify(reminderId.toInt(), builder.build())
  }

  fun cancelNotification(context: Context, notificationId: Int) {
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    notificationManager.cancel(notificationId)
  }
}
