package com.example.reminder

import android.app.NotificationManager
import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest

data class AlarmChannelConfig(
  val importance: Int = NotificationManager.IMPORTANCE_HIGH,
  val enableVibration: Boolean = true,
  val vibrationPattern: LongArray = DEFAULT_VIBRATION_PATTERN,
  val enableLights: Boolean = true,
  val lockscreenVisibility: Int = 1, // NotificationCompat.VISIBILITY_PUBLIC
  val bypassDnd: Boolean = true
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is AlarmChannelConfig) return false
    return importance == other.importance &&
      enableVibration == other.enableVibration &&
      vibrationPattern.contentEquals(other.vibrationPattern) &&
      enableLights == other.enableLights &&
      lockscreenVisibility == other.lockscreenVisibility &&
      bypassDnd == other.bypassDnd
  }

  override fun hashCode(): Int {
    var result = importance
    result = 31 * result + enableVibration.hashCode()
    result = 31 * result + vibrationPattern.contentHashCode()
    result = 31 * result + enableLights.hashCode()
    result = 31 * result + lockscreenVisibility
    result = 31 * result + bypassDnd.hashCode()
    return result
  }

  companion object {
    val DEFAULT_VIBRATION_PATTERN = longArrayOf(0L, 800L, 400L, 800L, 400L, 800L)
  }
}

sealed class AudioValidationResult {
  data class Valid(
    val uriString: String,
    val displayName: String,
    val persistableGranted: Boolean = true
  ) : AudioValidationResult()

  data class Invalid(
    val reason: String,
    val isPermissionIssue: Boolean = false
  ) : AudioValidationResult()
}

sealed class AlarmToneSelectionResult {
  data class Selected(
    val uriString: String,
    val displayName: String,
    val channelId: String
  ) : AlarmToneSelectionResult()

  data class Rejected(
    val reason: String
  ) : AlarmToneSelectionResult()
}

sealed class AlarmTonePreviewResult {
  data object Playing : AlarmTonePreviewResult()
  data class Failed(
    val reason: String,
    val triggeredFallback: Boolean
  ) : AlarmTonePreviewResult()
}

data class AlarmToneNotificationResolution(
  val channelId: String,
  val effectiveUriString: String,
  val isCustomTone: Boolean,
  val isUsingFallbackDefault: Boolean,
  val fallbackNotice: String? = null
)

data class AlarmToneUiState(
  val selectedUriString: String? = null,
  val selectedDisplayName: String? = null,
  val isCustomTone: Boolean = false,
  val isUsingFallbackDefault: Boolean = false,
  val activeChannelId: String = AlarmToneCoordinator.LEGACY_DEFAULT_CHANNEL_ID,
  val isPreviewPlaying: Boolean = false,
  val statusMessage: String? = null,
  val errorMessage: String? = null,
  val isProcessing: Boolean = false
)

interface AlarmToneStore {
  fun getSelectedToneUri(): String?
  fun setSelectedToneUri(uriString: String?)
  fun getSelectedToneName(): String?
  fun setSelectedToneName(name: String?)
  fun getActiveCustomChannelId(): String?
  fun setActiveCustomChannelId(channelId: String?)
  fun getRetainedCustomChannelIds(): List<String>
  fun setRetainedCustomChannelIds(channelIds: List<String>)
  fun getFallbackNotice(): String?
  fun setFallbackNotice(notice: String?)
}

class SharedPreferencesAlarmToneStore(context: Context) : AlarmToneStore {
  private val prefs: SharedPreferences =
    context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  override fun getSelectedToneUri(): String? =
    prefs.getString(KEY_SELECTED_ALARM_TONE_URI, null)?.takeIf { it.isNotBlank() }

  override fun setSelectedToneUri(uriString: String?) {
    prefs.edit().apply {
      if (uriString.isNullOrBlank()) remove(KEY_SELECTED_ALARM_TONE_URI)
      else putString(KEY_SELECTED_ALARM_TONE_URI, uriString)
    }.apply()
  }

  override fun getSelectedToneName(): String? =
    prefs.getString(KEY_SELECTED_ALARM_TONE_NAME, null)?.takeIf { it.isNotBlank() }

  override fun setSelectedToneName(name: String?) {
    prefs.edit().apply {
      if (name.isNullOrBlank()) remove(KEY_SELECTED_ALARM_TONE_NAME)
      else putString(KEY_SELECTED_ALARM_TONE_NAME, name)
    }.apply()
  }

  override fun getActiveCustomChannelId(): String? =
    prefs.getString(KEY_ACTIVE_CUSTOM_CHANNEL_ID, null)?.takeIf { it.isNotBlank() }

  override fun setActiveCustomChannelId(channelId: String?) {
    prefs.edit().apply {
      if (channelId.isNullOrBlank()) remove(KEY_ACTIVE_CUSTOM_CHANNEL_ID)
      else putString(KEY_ACTIVE_CUSTOM_CHANNEL_ID, channelId)
    }.apply()
  }

  override fun getRetainedCustomChannelIds(): List<String> {
    val raw = prefs.getString(KEY_RETAINED_CUSTOM_CHANNEL_IDS, null).orEmpty()
    if (raw.isBlank()) return emptyList()
    return raw.split("|").map { it.trim() }.filter { it.isNotBlank() }
  }

  override fun setRetainedCustomChannelIds(channelIds: List<String>) {
    val clean = channelIds.map { it.trim() }.filter { it.isNotBlank() }.distinct()
    prefs.edit().apply {
      if (clean.isEmpty()) remove(KEY_RETAINED_CUSTOM_CHANNEL_IDS)
      else putString(KEY_RETAINED_CUSTOM_CHANNEL_IDS, clean.joinToString("|"))
    }.apply()
  }

  override fun getFallbackNotice(): String? =
    prefs.getString(KEY_FALLBACK_NOTICE, null)?.takeIf { it.isNotBlank() }

  override fun setFallbackNotice(notice: String?) {
    prefs.edit().apply {
      if (notice.isNullOrBlank()) remove(KEY_FALLBACK_NOTICE)
      else putString(KEY_FALLBACK_NOTICE, notice)
    }.apply()
  }

  companion object {
    const val PREFS_NAME = "voxtask_prefs"
    const val KEY_SELECTED_ALARM_TONE_URI = "selected_alarm_tone_uri"
    const val KEY_SELECTED_ALARM_TONE_NAME = "selected_alarm_tone_name"
    const val KEY_ACTIVE_CUSTOM_CHANNEL_ID = "selected_alarm_tone_channel_id"
    const val KEY_RETAINED_CUSTOM_CHANNEL_IDS = "retained_custom_alarm_channel_ids"
    const val KEY_FALLBACK_NOTICE = "alarm_tone_fallback_notice"
  }
}

interface AlarmTonePlatformPort {
  fun getDefaultSystemAlarmUriString(): String
  fun validateSelectedAudioUri(uriString: String, deepCodecProbe: Boolean): AudioValidationResult
  fun channelExists(channelId: String): Boolean
  fun readChannelConfig(channelId: String): AlarmChannelConfig?
  fun createChannelIfMissing(
    channelId: String,
    channelName: String,
    channelDescription: String,
    soundUriString: String,
    config: AlarmChannelConfig
  )
  fun deleteObsoleteCustomChannel(channelId: String)
  fun startPreviewPlayback(
    uriString: String,
    onCompleted: () -> Unit,
    onPlaybackError: (String) -> Unit
  ): Boolean
  fun stopAndReleasePreviewPlayback()
  fun stopActiveAlarmIfRinging()
}

class AlarmToneCoordinator(
  private val store: AlarmToneStore,
  private val platform: AlarmTonePlatformPort
) {

  private val stateLock = Any()

  private val _uiState = MutableStateFlow(
    AlarmToneUiState(
      selectedUriString = store.getSelectedToneUri(),
      selectedDisplayName = store.getSelectedToneName(),
      isCustomTone = !store.getSelectedToneUri().isNullOrBlank(),
      activeChannelId = resolveInitialChannelIdFromStore(),
      isUsingFallbackDefault = !store.getFallbackNotice().isNullOrBlank(),
      statusMessage = store.getFallbackNotice()
    )
  )
  val uiState: StateFlow<AlarmToneUiState> = _uiState.asStateFlow()

  private fun resolveInitialChannelIdFromStore(): String {
    val savedUri = store.getSelectedToneUri() ?: return LEGACY_DEFAULT_CHANNEL_ID
    return store.getActiveCustomChannelId()?.takeIf {
      it.startsWith(CUSTOM_CHANNEL_PREFIX)
    } ?: computeCustomChannelId(savedUri)
  }

  fun refreshAndEnsureChannel(): AlarmToneNotificationResolution {
    synchronized(stateLock) {
      return resolveForNotificationInternal(deepCodecProbe = false)
    }
  }

  fun resolveForNotification(): AlarmToneNotificationResolution {
    synchronized(stateLock) {
      return resolveForNotificationInternal(deepCodecProbe = false)
    }
  }

  fun selectCustomAudioUri(uriString: String): AlarmToneSelectionResult {
    val cleanUri = uriString.trim()
    if (cleanUri.isBlank()) {
      return AlarmToneSelectionResult.Rejected("Tidak ada berkas audio yang dipilih.")
    }

    if (isNetworkOrUnsupportedScheme(cleanUri)) {
      val msg = "Hanya berkas audio lokal dari perangkat yang diizinkan (bukan URL jaringan)."
      _uiState.value = _uiState.value.copy(
        errorMessage = msg,
        isProcessing = false
      )
      return AlarmToneSelectionResult.Rejected(msg)
    }

    _uiState.value = _uiState.value.copy(isProcessing = true, errorMessage = null)

    val validation = try {
      platform.validateSelectedAudioUri(cleanUri, deepCodecProbe = true)
    } catch (e: Exception) {
      AudioValidationResult.Invalid("Gagal memvalidasi berkas audio: ${e.message ?: "kesalahan tidak dikenal"}")
    }

    return when (validation) {
      is AudioValidationResult.Invalid -> {
        // Keep any existing valid selection unchanged; never persist invalid URI
        _uiState.value = _uiState.value.copy(
          isProcessing = false,
          errorMessage = validation.reason
        )
        AlarmToneSelectionResult.Rejected(validation.reason)
      }

      is AudioValidationResult.Valid -> {
        synchronized(stateLock) {
          stopPreviewInternal()

          val targetChannelId = computeCustomChannelId(validation.uriString)
          ensureCustomChannelCreatedWithSafeMigration(
            targetChannelId = targetChannelId,
            soundUriString = validation.uriString,
            previousChannelId = _uiState.value.activeChannelId
          )

          store.setSelectedToneUri(validation.uriString)
          store.setSelectedToneName(validation.displayName)
          store.setActiveCustomChannelId(targetChannelId)
          store.setFallbackNotice(null)

          _uiState.value = AlarmToneUiState(
            selectedUriString = validation.uriString,
            selectedDisplayName = validation.displayName,
            isCustomTone = true,
            isUsingFallbackDefault = false,
            activeChannelId = targetChannelId,
            isPreviewPlaying = false,
            statusMessage = "Nada kustom '${validation.displayName}' disimpan. Notifikasi berikutnya memakai channel nada terbaru.",
            errorMessage = null,
            isProcessing = false
          )

          AlarmToneSelectionResult.Selected(
            uriString = validation.uriString,
            displayName = validation.displayName,
            channelId = targetChannelId
          )
        }
      }
    }
  }

  fun resetToDefaultTone() {
    synchronized(stateLock) {
      stopPreviewInternal()
      ensureDefaultLegacyChannelExists()

      store.setSelectedToneUri(null)
      store.setSelectedToneName(null)
      store.setActiveCustomChannelId(null)
      store.setFallbackNotice(null)

      _uiState.value = AlarmToneUiState(
        selectedUriString = null,
        selectedDisplayName = null,
        isCustomTone = false,
        isUsingFallbackDefault = false,
        activeChannelId = LEGACY_DEFAULT_CHANNEL_ID,
        isPreviewPlaying = false,
        statusMessage = "Nada alarm dikembalikan ke nada default perangkat.",
        errorMessage = null,
        isProcessing = false
      )
    }
  }

  fun previewSelectedTone(): AlarmTonePreviewResult {
    synchronized(stateLock) {
      // Stop any running preview or active alarm sound before starting new preview
      stopPreviewInternal()
      try {
        platform.stopActiveAlarmIfRinging()
      } catch (_: Exception) {}

      val savedUri = store.getSelectedToneUri()
      if (savedUri.isNullOrBlank()) {
        val defaultUri = platform.getDefaultSystemAlarmUriString()
        return startPreviewWithFallbackHandling(
          uriToPlay = defaultUri,
          isCustomAttempt = false,
          savedDisplayName = null
        )
      }

      val validation = try {
        platform.validateSelectedAudioUri(savedUri, deepCodecProbe = true)
      } catch (e: Exception) {
        AudioValidationResult.Invalid("Berkas audio tidak dapat dibaca (${e.message ?: "error"}). ")
      }

      return when (validation) {
        is AudioValidationResult.Valid -> {
          startPreviewWithFallbackHandling(
            uriToPlay = validation.uriString,
            isCustomAttempt = true,
            savedDisplayName = validation.displayName
          )
        }
        is AudioValidationResult.Invalid -> {
          val reason = triggerSafeFallbackInternal(
            failedDisplayName = store.getSelectedToneName(),
            reasonDetail = validation.reason
          )
          AlarmTonePreviewResult.Failed(reason = reason, triggeredFallback = true)
        }
      }
    }
  }

  fun stopPreview() {
    synchronized(stateLock) {
      stopPreviewInternal()
    }
  }

  fun handleRuntimePlaybackFailure(failedUriString: String?, detail: String? = null) {
    synchronized(stateLock) {
      val currentSaved = store.getSelectedToneUri()
      if (!failedUriString.isNullOrBlank() && currentSaved == failedUriString) {
        triggerSafeFallbackInternal(
          failedDisplayName = store.getSelectedToneName(),
          reasonDetail = detail ?: "Berkas audio gagal diputar saat alarm berbunyi."
        )
      }
    }
  }

  fun release() {
    synchronized(stateLock) {
      stopPreviewInternal()
    }
  }

  private fun startPreviewWithFallbackHandling(
    uriToPlay: String,
    isCustomAttempt: Boolean,
    savedDisplayName: String?
  ): AlarmTonePreviewResult {
    val started = try {
      platform.startPreviewPlayback(
        uriString = uriToPlay,
        onCompleted = {
          synchronized(stateLock) {
            platform.stopAndReleasePreviewPlayback()
            _uiState.value = _uiState.value.copy(isPreviewPlaying = false)
          }
        },
        onPlaybackError = { errorText ->
          synchronized(stateLock) {
            platform.stopAndReleasePreviewPlayback()
            if (isCustomAttempt) {
              triggerSafeFallbackInternal(
                failedDisplayName = savedDisplayName,
                reasonDetail = errorText
              )
            } else {
              _uiState.value = _uiState.value.copy(
                isPreviewPlaying = false,
                errorMessage = "Gagal memutar pratinjau nada: $errorText"
              )
            }
          }
        }
      )
    } catch (e: Exception) {
      platform.stopAndReleasePreviewPlayback()
      false
    }

    return if (started) {
      _uiState.value = _uiState.value.copy(
        isPreviewPlaying = true,
        errorMessage = null
      )
      AlarmTonePreviewResult.Playing
    } else if (isCustomAttempt) {
      val reason = triggerSafeFallbackInternal(
        failedDisplayName = savedDisplayName,
        reasonDetail = "Format atau berkas audio tidak dapat diputar oleh perangkat."
      )
      AlarmTonePreviewResult.Failed(reason = reason, triggeredFallback = true)
    } else {
      val msg = "Pratinjau nada default tidak tersedia pada perangkat ini."
      _uiState.value = _uiState.value.copy(
        isPreviewPlaying = false,
        errorMessage = msg
      )
      AlarmTonePreviewResult.Failed(reason = msg, triggeredFallback = false)
    }
  }

  private fun stopPreviewInternal() {
    try {
      platform.stopAndReleasePreviewPlayback()
    } catch (_: Exception) {}
    if (_uiState.value.isPreviewPlaying) {
      _uiState.value = _uiState.value.copy(isPreviewPlaying = false)
    }
  }

  private fun resolveForNotificationInternal(deepCodecProbe: Boolean): AlarmToneNotificationResolution {
    val savedUri = store.getSelectedToneUri()
    val defaultUri = platform.getDefaultSystemAlarmUriString()

    if (savedUri.isNullOrBlank()) {
      ensureDefaultLegacyChannelExists()
      val existingNotice = store.getFallbackNotice()
      _uiState.value = _uiState.value.copy(
        selectedUriString = null,
        selectedDisplayName = null,
        isCustomTone = false,
        isUsingFallbackDefault = !existingNotice.isNullOrBlank(),
        activeChannelId = LEGACY_DEFAULT_CHANNEL_ID,
        statusMessage = existingNotice
      )
      return AlarmToneNotificationResolution(
        channelId = LEGACY_DEFAULT_CHANNEL_ID,
        effectiveUriString = defaultUri,
        isCustomTone = false,
        isUsingFallbackDefault = !existingNotice.isNullOrBlank(),
        fallbackNotice = existingNotice
      )
    }

    if (isNetworkOrUnsupportedScheme(savedUri)) {
      val msg = triggerSafeFallbackInternal(
        failedDisplayName = store.getSelectedToneName(),
        reasonDetail = "Sumber URI bukan file lokal perangkat."
      )
      return AlarmToneNotificationResolution(
        channelId = LEGACY_DEFAULT_CHANNEL_ID,
        effectiveUriString = defaultUri,
        isCustomTone = false,
        isUsingFallbackDefault = true,
        fallbackNotice = msg
      )
    }

    val validation = try {
      platform.validateSelectedAudioUri(savedUri, deepCodecProbe = deepCodecProbe)
    } catch (e: Exception) {
      AudioValidationResult.Invalid("Berkas audio tidak dapat dibaca (${e.message ?: "error"}). ")
    }

    return when (validation) {
      is AudioValidationResult.Valid -> {
        val targetChannelId = computeCustomChannelId(validation.uriString)
        ensureCustomChannelCreatedWithSafeMigration(
          targetChannelId = targetChannelId,
          soundUriString = validation.uriString,
          previousChannelId = store.getActiveCustomChannelId() ?: LEGACY_DEFAULT_CHANNEL_ID
        )
        if (store.getActiveCustomChannelId() != targetChannelId) {
          store.setActiveCustomChannelId(targetChannelId)
        }
        if (validation.displayName != store.getSelectedToneName()) {
          store.setSelectedToneName(validation.displayName)
        }

        _uiState.value = _uiState.value.copy(
          selectedUriString = validation.uriString,
          selectedDisplayName = validation.displayName,
          isCustomTone = true,
          isUsingFallbackDefault = false,
          activeChannelId = targetChannelId,
          errorMessage = null
        )

        AlarmToneNotificationResolution(
          channelId = targetChannelId,
          effectiveUriString = validation.uriString,
          isCustomTone = true,
          isUsingFallbackDefault = false,
          fallbackNotice = null
        )
      }

      is AudioValidationResult.Invalid -> {
        val notice = triggerSafeFallbackInternal(
          failedDisplayName = store.getSelectedToneName(),
          reasonDetail = validation.reason
        )
        AlarmToneNotificationResolution(
          channelId = LEGACY_DEFAULT_CHANNEL_ID,
          effectiveUriString = defaultUri,
          isCustomTone = false,
          isUsingFallbackDefault = true,
          fallbackNotice = notice
        )
      }
    }
  }

  private fun triggerSafeFallbackInternal(
    failedDisplayName: String?,
    reasonDetail: String
  ): String {
    ensureDefaultLegacyChannelExists()
    val namePart = failedDisplayName?.takeIf { it.isNotBlank() }?.let { " ('$it')" } ?: ""
    val notice = "Nada pilihan$namePart tidak tersedia ($reasonDetail). Dialihkan ke nada default."

    store.setSelectedToneUri(null)
    store.setSelectedToneName(null)
    store.setActiveCustomChannelId(null)
    store.setFallbackNotice(notice)

    _uiState.value = _uiState.value.copy(
      selectedUriString = null,
      selectedDisplayName = null,
      isCustomTone = false,
      isUsingFallbackDefault = true,
      activeChannelId = LEGACY_DEFAULT_CHANNEL_ID,
      isPreviewPlaying = false,
      statusMessage = null,
      errorMessage = notice,
      isProcessing = false
    )
    return notice
  }

  private fun ensureDefaultLegacyChannelExists() {
    if (!platform.channelExists(LEGACY_DEFAULT_CHANNEL_ID)) {
      platform.createChannelIfMissing(
        channelId = LEGACY_DEFAULT_CHANNEL_ID,
        channelName = LEGACY_CHANNEL_NAME,
        channelDescription = LEGACY_CHANNEL_DESC,
        soundUriString = platform.getDefaultSystemAlarmUriString(),
        config = AlarmChannelConfig()
      )
    }
  }

  private fun ensureCustomChannelCreatedWithSafeMigration(
    targetChannelId: String,
    soundUriString: String,
    previousChannelId: String?
  ) {
    // Always keep legacy default channel alive and untouched as safe fallback
    ensureDefaultLegacyChannelExists()

    val retained = store.getRetainedCustomChannelIds().toMutableList()

    if (platform.channelExists(targetChannelId)) {
      // Channel already exists: respect user settings in Android Settings, do not recreate!
      retained.remove(targetChannelId)
      retained.add(targetChannelId)
      store.setRetainedCustomChannelIds(retained)
      return
    }

    // Safely migrate user-adjusted channel attributes from previous active channel or legacy channel
    val migratedConfig = previousChannelId
      ?.let { platform.readChannelConfig(it) }
      ?: platform.readChannelConfig(LEGACY_DEFAULT_CHANNEL_ID)
      ?: AlarmChannelConfig()

    platform.createChannelIfMissing(
      channelId = targetChannelId,
      channelName = CUSTOM_CHANNEL_NAME,
      channelDescription = CUSTOM_CHANNEL_DESC,
      soundUriString = soundUriString,
      config = migratedConfig
    )

    retained.remove(targetChannelId)
    retained.add(targetChannelId)

    // Evict oldest custom channels only beyond MAX_RETAINED_CUSTOM_CHANNELS; NEVER delete legacy channel
    while (retained.size > MAX_RETAINED_CUSTOM_CHANNELS) {
      val oldest = retained.removeAt(0)
      if (oldest != LEGACY_DEFAULT_CHANNEL_ID && oldest != targetChannelId) {
        try {
          platform.deleteObsoleteCustomChannel(oldest)
        } catch (_: Exception) {}
      }
    }
    store.setRetainedCustomChannelIds(retained)
  }

  companion object {
    const val LEGACY_DEFAULT_CHANNEL_ID = "voxtask_alarm_channel_sound_v2"
    const val CUSTOM_CHANNEL_PREFIX = "voxtask_alarm_channel_custom_"
    const val LEGACY_CHANNEL_NAME = "VoxTask Alarm & Pengingat"
    const val LEGACY_CHANNEL_DESC = "Alarm berbunyi nyaring dan notifikasi pengingat dari VoxTask"
    const val CUSTOM_CHANNEL_NAME = "VoxTask Alarm Kustom"
    const val CUSTOM_CHANNEL_DESC = "Notifikasi pengingat VoxTask dengan nada lokal pilihan Anda"
    const val MAX_RETAINED_CUSTOM_CHANNELS = 5

    fun isNetworkOrUnsupportedScheme(uriString: String): Boolean {
      val trimmed = uriString.trim().lowercase()
      val scheme = trimmed.substringBefore(":", missingDelimiterValue = "")
      if (scheme.isBlank()) return true
      return scheme !in setOf("content", "android.resource")
    }

    fun computeCustomChannelId(uriString: String): String {
      val digest = MessageDigest.getInstance("SHA-256")
        .digest(uriString.toByteArray(Charsets.UTF_8))
      val hexPrefix = digest.take(6).joinToString("") { byte ->
        "%02x".format(byte)
      }
      return "$CUSTOM_CHANNEL_PREFIX$hexPrefix"
    }
  }
}
