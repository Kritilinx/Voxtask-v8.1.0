package com.example.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.local.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

  override fun onReceive(context: Context, intent: Intent) {
    if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
      intent.action == "android.intent.action.QUICKBOOT_POWERON" ||
      intent.action == "com.htc.intent.action.QUICKBOOT_POWERON"
    ) {
      val pendingResult = goAsync()
      CoroutineScope(Dispatchers.IO).launch {
        try {
          val database = AppDatabase.getDatabase(context)
          val scheduler = AlarmScheduler(context)
          val now = System.currentTimeMillis()

          val activeReminders = database.reminderDao().getActiveRemindersSync(now)
          for (reminder in activeReminders) {
            scheduler.schedule(reminder)
          }
        } catch (_: Exception) {
        } finally {
          pendingResult.finish()
        }
      }
    }
  }
}
