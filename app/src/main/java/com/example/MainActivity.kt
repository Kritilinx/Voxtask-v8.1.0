package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.ai.configureAppCheck
import com.example.reminder.AlarmSoundPlayer
import com.example.reminder.NotificationHelper
import com.example.ui.theme.VoxTaskTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    // Mandatory: App Check setup before UI or Firebase operations
    configureAppCheck(this, intent)
    enableEdgeToEdge()
    NotificationHelper.createNotificationChannel(this)

    // Stop ringing alarm if user opened app from reminder notification
    if (intent?.hasExtra("open_reminder_id") == true) {
      val reminderId = intent.getLongExtra("open_reminder_id", -1L)
      AlarmSoundPlayer.stopAlarm(this)
      if (reminderId != -1L) {
        NotificationHelper.cancelNotification(this, reminderId.toInt())
      }
    }

    setContent {
      VoxTaskTheme {
        VoxTaskApp()
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    if (intent.hasExtra("open_reminder_id")) {
      val reminderId = intent.getLongExtra("open_reminder_id", -1L)
      AlarmSoundPlayer.stopAlarm(this)
      if (reminderId != -1L) {
        NotificationHelper.cancelNotification(this, reminderId.toInt())
      }
    }
  }
}
