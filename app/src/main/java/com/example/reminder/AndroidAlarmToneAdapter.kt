package com.example.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.res.AssetFileDescriptor
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.util.Log

class AndroidAlarmToneAdapter(context: Context) : AlarmTonePlatformPort {

  private val appContext: Context = context.applicationContext
  private val notificationManager: NotificationManager? =
    appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
  private val mainHandler = Handler(Looper.getMainLooper())

  private val playerLock = Any()
  private var previewPlayer: MediaPlayer? = null
  private var previewTimeoutRunnable: Runnable? = null

  override fun getDefaultSystemAlarmUriString(): String {
    val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
      ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
      ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
      ?: android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI
    return uri.toString()
  }

  override fun validateSelectedAudioUri(
    uriString: String,
    deepCodecProbe: Boolean
  ): AudioValidationResult {
    if (AlarmToneCoordinator.isNetworkOrUnsupportedScheme(uriString)) {
      return AudioValidationResult.Invalid("Sumber audio harus berasal dari berkas lokal perangkat.")
    }

    val uri = try {
      Uri.parse(uriString)
    } catch (_: Exception) {
      return AudioValidationResult.Invalid("URI berkas audio tidak valid.")
    }

    var persistableGranted = true
    if ("content".equals(uri.scheme, ignoreCase = true)) {
      try {
        appContext.contentResolver.takePersistableUriPermission(
          uri,
          Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
      } catch (_: SecurityException) {
        // Provider might only grant one-shot access; verify whether direct read still succeeds
        persistableGranted = false
      } catch (_: Exception) {}
    }

    val mimeType = try {
      appContext.contentResolver.getType(uri)
    } catch (_: Exception) {
      null
    }

    if (mimeType != null && isExplicitlyNonAudioMime(mimeType)) {
      return AudioValidationResult.Invalid("Format berkas ($mimeType) bukan file audio yang didukung.")
    }

    val readableCheck = verifyUriReadable(uri)
    if (readableCheck != null) {
      return readableCheck
    }

    if (deepCodecProbe) {
      val probeError = probePlayableWithMediaPlayer(uri)
      if (probeError != null) {
        return AudioValidationResult.Invalid(probeError)
      }
    }

    val displayName = resolveDisplayName(uri)
    return AudioValidationResult.Valid(
      uriString = uri.toString(),
      displayName = displayName,
      persistableGranted = persistableGranted
    )
  }

  private fun isExplicitlyNonAudioMime(mimeType: String): Boolean {
    val lower = mimeType.lowercase()
    if (lower.startsWith("audio/")) return false
    if (lower == "application/ogg" || lower == "application/x-ogg") return false
    if (lower == "application/octet-stream") return false
    return true
  }

  private fun verifyUriReadable(uri: Uri): AudioValidationResult.Invalid? {
    var afd: AssetFileDescriptor? = null
    return try {
      afd = appContext.contentResolver.openAssetFileDescriptor(uri, "r")
      if (afd == null) {
        AudioValidationResult.Invalid("Berkas audio tidak dapat dibuka atau telah dipindahkan.")
      } else if (afd.length == 0L) {
        AudioValidationResult.Invalid("Berkas audio kosong atau rusak.")
      } else {
        null
      }
    } catch (_: SecurityException) {
      AudioValidationResult.Invalid(
        reason = "Izin akses berkas audio sudah tidak berlaku.",
        isPermissionIssue = true
      )
    } catch (_: java.io.FileNotFoundException) {
      AudioValidationResult.Invalid("Berkas audio tidak ditemukan atau telah dihapus dari perangkat.")
    } catch (e: Exception) {
      AudioValidationResult.Invalid("Berkas audio tidak dapat dibaca (${e.message ?: "akses gagal"}).")
    } finally {
      try {
        afd?.close()
      } catch (_: Exception) {}
    }
  }

  private fun probePlayableWithMediaPlayer(uri: Uri): String? {
    var probe: MediaPlayer? = null
    return try {
      probe = MediaPlayer().apply {
        setAudioAttributes(buildAlarmAudioAttributes())
        setDataSource(appContext, uri)
        prepare()
      }
      null
    } catch (_: SecurityException) {
      "Izin membaca berkas audio tidak tersedia."
    } catch (e: Exception) {
      Log.w(TAG, "Audio probe failed for $uri: ${e.message}")
      "Format audio tidak didukung atau berkas tidak dapat diputar oleh perangkat."
    } finally {
      try {
        probe?.reset()
      } catch (_: Exception) {}
      try {
        probe?.release()
      } catch (_: Exception) {}
    }
  }

  private fun resolveDisplayName(uri: Uri): String {
    if ("content".equals(uri.scheme, ignoreCase = true)) {
      try {
        appContext.contentResolver.query(
          uri,
          arrayOf(OpenableColumns.DISPLAY_NAME),
          null,
          null,
          null
        )?.use { cursor ->
          if (cursor.moveToFirst()) {
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0) {
              val name = cursor.getString(idx)
              if (!name.isNullOrBlank()) return name
            }
          }
        }
      } catch (_: Exception) {}
    }
    val segment = uri.lastPathSegment?.substringAfterLast('/')?.trim()
    return segment?.takeIf { it.isNotBlank() } ?: "Nada Audio Lokal"
  }

  override fun channelExists(channelId: String): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
    return notificationManager?.getNotificationChannel(channelId) != null
  }

  override fun readChannelConfig(channelId: String): AlarmChannelConfig? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
    val channel = notificationManager?.getNotificationChannel(channelId) ?: return null
    return AlarmChannelConfig(
      importance = channel.importance,
      enableVibration = channel.shouldVibrate(),
      vibrationPattern = channel.vibrationPattern ?: AlarmChannelConfig.DEFAULT_VIBRATION_PATTERN,
      enableLights = channel.shouldShowLights(),
      lockscreenVisibility = channel.lockscreenVisibility,
      bypassDnd = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) channel.canBypassDnd() else true
    )
  }

  override fun createChannelIfMissing(
    channelId: String,
    channelName: String,
    channelDescription: String,
    soundUriString: String,
    config: AlarmChannelConfig
  ) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val manager = notificationManager ?: return
    if (manager.getNotificationChannel(channelId) != null) return

    val soundUri = Uri.parse(soundUriString)
    val channel = NotificationChannel(channelId, channelName, config.importance).apply {
      description = channelDescription
      setSound(soundUri, buildAlarmAudioAttributes())
      enableVibration(config.enableVibration)
      if (config.enableVibration) {
        vibrationPattern = config.vibrationPattern
      }
      enableLights(config.enableLights)
      lockscreenVisibility = config.lockscreenVisibility
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        setBypassDnd(config.bypassDnd)
      }
    }
    manager.createNotificationChannel(channel)
  }

  override fun deleteObsoleteCustomChannel(channelId: String) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    if (channelId == AlarmToneCoordinator.LEGACY_DEFAULT_CHANNEL_ID) return
    if (!channelId.startsWith(AlarmToneCoordinator.CUSTOM_CHANNEL_PREFIX)) return
    notificationManager?.deleteNotificationChannel(channelId)
  }

  override fun startPreviewPlayback(
    uriString: String,
    onCompleted: () -> Unit,
    onPlaybackError: (String) -> Unit
  ): Boolean {
    stopAndReleasePreviewPlayback()
    val uri = try {
      Uri.parse(uriString)
    } catch (_: Exception) {
      return false
    }

    return synchronized(playerLock) {
      var player: MediaPlayer? = null
      try {
        player = MediaPlayer().apply {
          setAudioAttributes(buildAlarmAudioAttributes())
          @Suppress("DEPRECATION")
          setAudioStreamType(AudioManager.STREAM_ALARM)
          isLooping = false
          setOnCompletionListener {
            mainHandler.post {
              onCompleted()
            }
          }
          setOnErrorListener { _, what, extra ->
            mainHandler.post {
              onPlaybackError("Pemutar audio mengalami gangguan ($what/$extra).")
            }
            true
          }
          setDataSource(appContext, uri)
          prepare()
          start()
        }
        previewPlayer = player
        val timeout = Runnable {
          onCompleted()
        }
        previewTimeoutRunnable = timeout
        mainHandler.postDelayed(timeout, MAX_PREVIEW_DURATION_MS)
        true
      } catch (e: Exception) {
        Log.w(TAG, "Preview playback failed: ${e.message}", e)
        try {
          player?.reset()
        } catch (_: Exception) {}
        try {
          player?.release()
        } catch (_: Exception) {}
        previewPlayer = null
        false
      }
    }
  }

  override fun stopAndReleasePreviewPlayback() {
    synchronized(playerLock) {
      previewTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
      previewTimeoutRunnable = null
      val player = previewPlayer
      previewPlayer = null
      if (player != null) {
        try {
          if (player.isPlaying) {
            player.stop()
          }
        } catch (_: Exception) {}
        try {
          player.reset()
        } catch (_: Exception) {}
        try {
          player.release()
        } catch (_: Exception) {}
      }
    }
  }

  override fun stopActiveAlarmIfRinging() {
    AlarmSoundPlayer.stopAlarm(appContext)
  }

  private fun buildAlarmAudioAttributes(): AudioAttributes {
    return AudioAttributes.Builder()
      .setUsage(AudioAttributes.USAGE_ALARM)
      .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
      .build()
  }

  companion object {
    private const val TAG = "AndroidAlarmToneAdapter"
    private const val MAX_PREVIEW_DURATION_MS = 15_000L
  }
}

object AlarmToneRuntime {
  @Volatile
  private var coordinator: AlarmToneCoordinator? = null

  fun getCoordinator(context: Context): AlarmToneCoordinator {
    return coordinator ?: synchronized(this) {
      coordinator ?: AlarmToneCoordinator(
        store = SharedPreferencesAlarmToneStore(context.applicationContext),
        platform = AndroidAlarmToneAdapter(context.applicationContext)
      ).also { coordinator = it }
    }
  }

  fun ensureActiveChannel(context: Context): AlarmToneNotificationResolution {
    return getCoordinator(context).refreshAndEnsureChannel()
  }

  fun resolveForNotification(context: Context): AlarmToneNotificationResolution {
    return getCoordinator(context).resolveForNotification()
  }

  fun resolveEffectiveAlarmUri(context: Context): Uri {
    val resolved = getCoordinator(context).resolveForNotification()
    return Uri.parse(resolved.effectiveUriString)
  }

  fun notifyPlaybackFailure(context: Context, failedUriString: String, detail: String? = null) {
    getCoordinator(context).handleRuntimePlaybackFailure(failedUriString, detail)
  }
}
