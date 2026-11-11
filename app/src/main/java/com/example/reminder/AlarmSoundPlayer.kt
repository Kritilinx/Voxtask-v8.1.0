package com.example.reminder

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AlarmRingingState(
  val isRinging: Boolean = false,
  val reminderId: Long? = null,
  val title: String = ""
)

object AlarmSoundPlayer {
  private const val TAG = "AlarmSoundPlayer"
  private const val MAX_RING_DURATION_MS = 60_000L // 60 seconds auto-timeout

  private val mainHandler = Handler(Looper.getMainLooper())
  private var mediaPlayer: MediaPlayer? = null
  private var ringtone: Ringtone? = null

  private val _ringingState = MutableStateFlow(AlarmRingingState())
  val ringingState: StateFlow<AlarmRingingState> = _ringingState.asStateFlow()

  private val autoStopRunnable = Runnable {
    Log.d(TAG, "Alarm auto-stopped after timeout")
    stopAlarm()
  }

  fun getDefaultSystemAlarmUri(context: Context): Uri {
    return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
      ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
      ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
      ?: android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI
  }

  fun getAlarmUri(context: Context): Uri {
    return try {
      AlarmToneRuntime.resolveEffectiveAlarmUri(context)
    } catch (_: Exception) {
      getDefaultSystemAlarmUri(context)
    }
  }

  @Synchronized
  fun startAlarm(context: Context, reminderId: Long, title: String) {
    stopAlarm(context) // Stop previous if any
    try {
      AlarmToneRuntime.getCoordinator(context).stopPreview()
    } catch (_: Exception) {}

    _ringingState.value = AlarmRingingState(
      isRinging = true,
      reminderId = reminderId,
      title = title
    )

    val defaultUri = getDefaultSystemAlarmUri(context)
    val alarmUri = getAlarmUri(context)

    val audioAttributes = AudioAttributes.Builder()
      .setUsage(AudioAttributes.USAGE_ALARM)
      .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
      .build()

    // 1. Start MediaPlayer on ALARM stream (with safe fallback to system default URI if custom URI fails)
    var playerStarted = tryStartMediaPlayer(context, alarmUri, audioAttributes)
    var fallbackUriUsed = alarmUri

    if (!playerStarted && alarmUri != defaultUri) {
      AlarmToneRuntime.notifyPlaybackFailure(
        context = context,
        failedUriString = alarmUri.toString(),
        detail = "File audio tidak dapat diputar oleh MediaPlayer."
      )
      fallbackUriUsed = defaultUri
      playerStarted = tryStartMediaPlayer(context, defaultUri, audioAttributes)
    }

    // 2. Fallback to RingtoneManager if MediaPlayer fails
    if (!playerStarted) {
      try {
        ringtone = RingtoneManager.getRingtone(context.applicationContext, fallbackUriUsed)?.apply {
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            this.audioAttributes = audioAttributes
          }
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            isLooping = true
          }
          play()
        }
      } catch (e: Exception) {
        Log.e(TAG, "Ringtone fallback failed: ${e.message}", e)
      }
    }

    // 3. Start Vibration
    startVibration(context)

    // 4. Schedule automatic stop after timeout
    mainHandler.removeCallbacks(autoStopRunnable)
    mainHandler.postDelayed(autoStopRunnable, MAX_RING_DURATION_MS)
  }

  private fun tryStartMediaPlayer(
    context: Context,
    uri: Uri,
    audioAttributes: AudioAttributes
  ): Boolean {
    return try {
      mediaPlayer = MediaPlayer().apply {
        setAudioAttributes(audioAttributes)
        setDataSource(context.applicationContext, uri)
        @Suppress("DEPRECATION")
        setAudioStreamType(AudioManager.STREAM_ALARM)
        isLooping = true
        prepare()
        start()
      }
      true
    } catch (e: Exception) {
      Log.w(TAG, "MediaPlayer failed for $uri: ${e.message}")
      try {
        mediaPlayer?.reset()
      } catch (_: Exception) {}
      try {
        mediaPlayer?.release()
      } catch (_: Exception) {}
      mediaPlayer = null
      false
    }
  }

  private fun startVibration(context: Context) {
    try {
      val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vm?.defaultVibrator ?: (context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)
      } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
      }

      val pattern = longArrayOf(0, 800, 400, 800, 400, 800)
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
      } else {
        @Suppress("DEPRECATION")
        vibrator?.vibrate(pattern, 0)
      }
    } catch (e: Exception) {
      Log.w(TAG, "Vibration start error: ${e.message}")
    }
  }

  @Synchronized
  fun stopAlarm(context: Context? = null) {
    mainHandler.removeCallbacks(autoStopRunnable)

    try {
      if (mediaPlayer?.isPlaying == true) {
        mediaPlayer?.stop()
      }
      mediaPlayer?.release()
    } catch (e: Exception) {
      Log.w(TAG, "Error stopping media player: ${e.message}")
    } finally {
      mediaPlayer = null
    }

    try {
      if (ringtone?.isPlaying == true) {
        ringtone?.stop()
      }
    } catch (e: Exception) {
      Log.w(TAG, "Error stopping ringtone: ${e.message}")
    } finally {
      ringtone = null
    }

    // Stop vibration
    context?.let { ctx ->
      try {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
          val vm = ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
          vm?.defaultVibrator ?: (ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)
        } else {
          @Suppress("DEPRECATION")
          ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        vibrator?.cancel()
      } catch (_: Exception) {}
    }

    _ringingState.value = AlarmRingingState(
      isRinging = false,
      reminderId = null,
      title = ""
    )
  }
}
