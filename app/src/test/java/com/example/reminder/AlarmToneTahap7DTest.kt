package com.example.reminder

import android.app.NotificationManager
import com.example.ai.GeminiConfig
import com.example.data.backup.BackupManifest
import com.example.data.local.entity.ReminderEntity
import com.example.data.local.entity.TaskEntity
import com.example.data.model.Priority
import com.example.data.model.RepeatMode
import com.example.voice.SharedPreferencesTtsVoiceStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * Pure JVM unit tests for Stage 7D (Custom Local Alarm Tone).
 * Covers all 10 mandatory test requirements without requiring Robolectric or network downloads.
 */
class AlarmToneTahap7DTest {

  private class InMemoryAlarmToneStore(
    private var selectedToneUri: String? = null,
    private var selectedToneName: String? = null,
    private var activeCustomChannelId: String? = null,
    private var retainedCustomChannelIds: List<String> = emptyList(),
    private var fallbackNotice: String? = null
  ) : AlarmToneStore {

    override fun getSelectedToneUri(): String? = selectedToneUri?.takeIf { it.isNotBlank() }
    override fun setSelectedToneUri(uriString: String?) {
      selectedToneUri = uriString?.takeIf { it.isNotBlank() }
    }

    override fun getSelectedToneName(): String? = selectedToneName?.takeIf { it.isNotBlank() }
    override fun setSelectedToneName(name: String?) {
      selectedToneName = name?.takeIf { it.isNotBlank() }
    }

    override fun getActiveCustomChannelId(): String? = activeCustomChannelId?.takeIf { it.isNotBlank() }
    override fun setActiveCustomChannelId(channelId: String?) {
      activeCustomChannelId = channelId?.takeIf { it.isNotBlank() }
    }

    override fun getRetainedCustomChannelIds(): List<String> = retainedCustomChannelIds
    override fun setRetainedCustomChannelIds(channelIds: List<String>) {
      retainedCustomChannelIds = channelIds
    }

    override fun getFallbackNotice(): String? = fallbackNotice?.takeIf { it.isNotBlank() }
    override fun setFallbackNotice(notice: String?) {
      fallbackNotice = notice?.takeIf { it.isNotBlank() }
    }
  }

  private data class FakeChannelRecord(
    val channelId: String,
    val name: String,
    val description: String,
    val soundUriString: String,
    val config: AlarmChannelConfig
  )

  private class FakeAlarmTonePlatformPort(
    private val defaultSystemUri: String = "content://settings/system/alarm_alert"
  ) : AlarmTonePlatformPort {

    val validUris = mutableMapOf<String, String>() // uri -> displayName
    val unsupportedUris = mutableSetOf<String>()
    val permissionDeniedUris = mutableSetOf<String>()

    val channels = linkedMapOf<String, FakeChannelRecord>()
    val deletedChannelIds = mutableListOf<String>()
    var createChannelCallsCount = 0

    var previewStartFailUris = mutableSetOf<String>()
    val startedPreviewUris = mutableListOf<String>()
    var stopAndReleaseCount = 0
    var stopActiveAlarmCount = 0
    var activePreviewOnCompleted: (() -> Unit)? = null
    var activePreviewOnError: ((String) -> Unit)? = null

    fun registerValidAudio(uri: String, displayName: String) {
      validUris[uri] = displayName
      unsupportedUris.remove(uri)
      permissionDeniedUris.remove(uri)
    }

    override fun getDefaultSystemAlarmUriString(): String = defaultSystemUri

    override fun validateSelectedAudioUri(uriString: String, deepCodecProbe: Boolean): AudioValidationResult {
      if (uriString in permissionDeniedUris) {
        return AudioValidationResult.Invalid(
          reason = "Izin akses berkas audio sudah tidak berlaku.",
          isPermissionIssue = true
        )
      }
      if (uriString in unsupportedUris) {
        return AudioValidationResult.Invalid(
          reason = "Format audio tidak didukung atau berkas tidak dapat diputar oleh perangkat."
        )
      }
      val displayName = validUris[uriString]
        ?: return AudioValidationResult.Invalid("Berkas audio tidak ditemukan atau telah dihapus dari perangkat.")
      return AudioValidationResult.Valid(
        uriString = uriString,
        displayName = displayName,
        persistableGranted = true
      )
    }

    override fun channelExists(channelId: String): Boolean = channels.containsKey(channelId)

    override fun readChannelConfig(channelId: String): AlarmChannelConfig? = channels[channelId]?.config

    override fun createChannelIfMissing(
      channelId: String,
      channelName: String,
      channelDescription: String,
      soundUriString: String,
      config: AlarmChannelConfig
    ) {
      if (channels.containsKey(channelId)) return
      createChannelCallsCount++
      channels[channelId] = FakeChannelRecord(
        channelId = channelId,
        name = channelName,
        description = channelDescription,
        soundUriString = soundUriString,
        config = config
      )
    }

    override fun deleteObsoleteCustomChannel(channelId: String) {
      deletedChannelIds.add(channelId)
      channels.remove(channelId)
    }

    override fun startPreviewPlayback(
      uriString: String,
      onCompleted: () -> Unit,
      onPlaybackError: (String) -> Unit
    ): Boolean {
      if (uriString in previewStartFailUris) {
        return false
      }
      startedPreviewUris.add(uriString)
      activePreviewOnCompleted = onCompleted
      activePreviewOnError = onPlaybackError
      return true
    }

    override fun stopAndReleasePreviewPlayback() {
      stopAndReleaseCount++
      activePreviewOnCompleted = null
      activePreviewOnError = null
    }

    override fun stopActiveAlarmIfRinging() {
      stopActiveAlarmCount++
    }
  }

  @Test
  fun test1_selectedAudioUriAndNameAreSavedCorrectly() {
    val store = InMemoryAlarmToneStore()
    val platform = FakeAlarmTonePlatformPort().apply {
      registerValidAudio("content://media/external/audio/media/42", "Kicau_Pagi.mp3")
    }
    val coordinator = AlarmToneCoordinator(store, platform)

    // Network/internet URLs must be rejected immediately (offline-only requirement)
    val networkResult = coordinator.selectCustomAudioUri("https://example.com/alarm.mp3")
    assertTrue(networkResult is AlarmToneSelectionResult.Rejected)
    assertNull(store.getSelectedToneUri())

    // Selecting local SAF audio URI saves URI, displayName, and resolves custom channel
    val result = coordinator.selectCustomAudioUri("content://media/external/audio/media/42")
    assertTrue(result is AlarmToneSelectionResult.Selected)
    val selected = result as AlarmToneSelectionResult.Selected

    assertEquals("content://media/external/audio/media/42", store.getSelectedToneUri())
    assertEquals("Kicau_Pagi.mp3", store.getSelectedToneName())
    assertEquals(selected.channelId, store.getActiveCustomChannelId())
    assertTrue(coordinator.uiState.value.isCustomTone)
    assertFalse(coordinator.uiState.value.isUsingFallbackDefault)
    assertEquals("Kicau_Pagi.mp3", coordinator.uiState.value.selectedDisplayName)
  }

  @Test
  fun test2_selectedAudioUriIsRestoredAfterAppRestart() {
    val persistentStore = InMemoryAlarmToneStore()
    val customUri = "content://com.android.externalstorage.documents/document/primary%3AMusic%2Fbel.wav"

    // Session 1: User chooses custom tone and closes app
    val platformSession1 = FakeAlarmTonePlatformPort().apply {
      registerValidAudio(customUri, "bel.wav")
    }
    val coordinatorSession1 = AlarmToneCoordinator(persistentStore, platformSession1)
    val selected = coordinatorSession1.selectCustomAudioUri(customUri) as AlarmToneSelectionResult.Selected
    coordinatorSession1.release()

    // Session 2: Fresh coordinator & platform instance simulating app reopen (retaining OS channels)
    val platformSession2 = FakeAlarmTonePlatformPort().apply {
      registerValidAudio(customUri, "bel.wav")
      channels.putAll(platformSession1.channels)
    }
    val coordinatorSession2 = AlarmToneCoordinator(persistentStore, platformSession2)

    assertEquals(customUri, coordinatorSession2.uiState.value.selectedUriString)
    assertEquals("bel.wav", coordinatorSession2.uiState.value.selectedDisplayName)
    assertTrue(coordinatorSession2.uiState.value.isCustomTone)
    assertEquals(selected.channelId, coordinatorSession2.uiState.value.activeChannelId)

    val resolution = coordinatorSession2.resolveForNotification()
    assertEquals(selected.channelId, resolution.channelId)
    assertEquals(customUri, resolution.effectiveUriString)
    assertEquals(0, platformSession2.createChannelCallsCount)
  }

  @Test
  fun test3_userCanResetBackToDefaultAlarmTone() {
    val store = InMemoryAlarmToneStore()
    val customUri = "content://media/external/audio/media/88"
    val platform = FakeAlarmTonePlatformPort().apply {
      registerValidAudio(customUri, "nada_kerja.ogg")
    }
    val coordinator = AlarmToneCoordinator(store, platform)
    coordinator.selectCustomAudioUri(customUri)
    assertTrue(coordinator.uiState.value.isCustomTone)

    coordinator.resetToDefaultTone()

    assertNull(store.getSelectedToneUri())
    assertNull(store.getSelectedToneName())
    assertNull(store.getActiveCustomChannelId())
    assertFalse(coordinator.uiState.value.isCustomTone)
    assertFalse(coordinator.uiState.value.isUsingFallbackDefault)
    assertEquals(AlarmToneCoordinator.LEGACY_DEFAULT_CHANNEL_ID, coordinator.uiState.value.activeChannelId)

    val resolution = coordinator.resolveForNotification()
    assertEquals(AlarmToneCoordinator.LEGACY_DEFAULT_CHANNEL_ID, resolution.channelId)
    assertEquals(platform.getDefaultSystemAlarmUriString(), resolution.effectiveUriString)
    assertFalse(platform.deletedChannelIds.contains(AlarmToneCoordinator.LEGACY_DEFAULT_CHANNEL_ID))
    assertTrue(platform.channels.containsKey(AlarmToneCoordinator.LEGACY_DEFAULT_CHANNEL_ID))
  }

  @Test
  fun test4_invalidDeletedOrRevokedUriTriggersSafeFallbackToDefault() {
    val validUri = "content://media/external/audio/media/10"
    val deletedUri = "content://media/external/audio/media/999"
    val store = InMemoryAlarmToneStore()
    val platform = FakeAlarmTonePlatformPort().apply {
      registerValidAudio(validUri, "nada_sah.mp3")
      permissionDeniedUris.add("content://revoked/audio.mp3")
    }
    val coordinator = AlarmToneCoordinator(store, platform)
    coordinator.selectCustomAudioUri(validUri)

    // Case A: Selecting a permission-denied or non-existent URI is rejected without breaking existing valid tone
    val rejected = coordinator.selectCustomAudioUri("content://revoked/audio.mp3")
    assertTrue(rejected is AlarmToneSelectionResult.Rejected)
    assertEquals(validUri, store.getSelectedToneUri())
    assertEquals(validUri, coordinator.uiState.value.selectedUriString)

    // Case B: Existing saved URI is later deleted from device -> automatic fallback on resolveForNotification()
    platform.validUris.remove(validUri)
    val resolution = coordinator.resolveForNotification()

    assertEquals(AlarmToneCoordinator.LEGACY_DEFAULT_CHANNEL_ID, resolution.channelId)
    assertEquals(platform.getDefaultSystemAlarmUriString(), resolution.effectiveUriString)
    assertFalse(resolution.isCustomTone)
    assertTrue(resolution.isUsingFallbackDefault)
    assertTrue(coordinator.uiState.value.isUsingFallbackDefault)
    assertNull(store.getSelectedToneUri())

    // Case C: Cold startup with missing URI in store -> restores safely to default fallback with notice
    val coldStore = InMemoryAlarmToneStore(
      selectedToneUri = deletedUri,
      selectedToneName = "file_terhapus.mp3"
    )
    val coldCoordinator = AlarmToneCoordinator(coldStore, platform)
    val coldResolution = coldCoordinator.refreshAndEnsureChannel()
    assertEquals(AlarmToneCoordinator.LEGACY_DEFAULT_CHANNEL_ID, coldResolution.channelId)
    assertEquals(platform.getDefaultSystemAlarmUriString(), coldResolution.effectiveUriString)
    assertTrue(coldResolution.isUsingFallbackDefault)
    assertFalse(coldResolution.fallbackNotice.isNullOrBlank())
  }

  @Test
  fun test5_unsupportedAudioFormatAndPreviewFailuresHandledSafelyWithoutCrash() {
    val store = InMemoryAlarmToneStore()
    val badAudioUri = "content://downloads/corrupt_track.wma"
    val goodAudioUri = "content://media/external/audio/media/55"
    val platform = FakeAlarmTonePlatformPort().apply {
      registerValidAudio(goodAudioUri, "alarm_lembut.m4a")
      unsupportedUris.add(badAudioUri)
    }
    val coordinator = AlarmToneCoordinator(store, platform)

    // Case A: Selecting unsupported format fails safely and does not persist invalid selection
    val selectionResult = coordinator.selectCustomAudioUri(badAudioUri)
    assertTrue(selectionResult is AlarmToneSelectionResult.Rejected)
    assertNull(store.getSelectedToneUri())
    assertFalse(coordinator.uiState.value.errorMessage.isNullOrBlank())

    // Case B: Valid preview stops any prior preview, starts new preview, and releases on completion
    assertTrue(coordinator.selectCustomAudioUri(goodAudioUri) is AlarmToneSelectionResult.Selected)
    val preview1 = coordinator.previewSelectedTone()
    assertTrue(preview1 is AlarmTonePreviewResult.Playing)
    assertTrue(coordinator.uiState.value.isPreviewPlaying)
    assertTrue(platform.stopActiveAlarmCount >= 1)

    val releasesBeforeSecondTap = platform.stopAndReleaseCount
    val preview2 = coordinator.previewSelectedTone()
    assertTrue(preview2 is AlarmTonePreviewResult.Playing)
    assertTrue(platform.stopAndReleaseCount > releasesBeforeSecondTap)
    assertEquals(listOf(goodAudioUri, goodAudioUri), platform.startedPreviewUris)

    // Completion releases player and clears playing flag
    platform.activePreviewOnCompleted?.invoke()
    assertFalse(coordinator.uiState.value.isPreviewPlaying)

    // Case C: Runtime playback error during preview releases MediaPlayer and triggers default fallback
    coordinator.previewSelectedTone()
    platform.activePreviewOnError?.invoke("Codec error")
    assertFalse(coordinator.uiState.value.isPreviewPlaying)
    assertTrue(coordinator.uiState.value.isUsingFallbackDefault)
    assertEquals(AlarmToneCoordinator.LEGACY_DEFAULT_CHANNEL_ID, coordinator.uiState.value.activeChannelId)

    // Case D: Immediate start failure during preview handles safely without crash
    coordinator.selectCustomAudioUri(goodAudioUri)
    platform.previewStartFailUris.add(goodAudioUri)
    val failedPreview = coordinator.previewSelectedTone()
    assertTrue(failedPreview is AlarmTonePreviewResult.Failed)
    assertTrue((failedPreview as AlarmTonePreviewResult.Failed).triggeredFallback)
    assertFalse(coordinator.uiState.value.isPreviewPlaying)
  }

  @Test
  fun test6_reminderChannelUsesCorrectSoundAndMigratesUserSettingsSafely() {
    val store = InMemoryAlarmToneStore()
    val customUri = "content://media/external/audio/media/77"
    val platform = FakeAlarmTonePlatformPort().apply {
      registerValidAudio(customUri, "lonceng.mp3")
    }

    // Pre-existing legacy channel modified by user in Android Settings (e.g., vibration disabled, importance adjusted)
    val userModifiedLegacyConfig = AlarmChannelConfig(
      importance = NotificationManager.IMPORTANCE_DEFAULT,
      enableVibration = false,
      vibrationPattern = longArrayOf(0L, 200L),
      enableLights = true,
      lockscreenVisibility = 1,
      bypassDnd = false
    )
    platform.channels[AlarmToneCoordinator.LEGACY_DEFAULT_CHANNEL_ID] = FakeChannelRecord(
      channelId = AlarmToneCoordinator.LEGACY_DEFAULT_CHANNEL_ID,
      name = AlarmToneCoordinator.LEGACY_CHANNEL_NAME,
      description = AlarmToneCoordinator.LEGACY_CHANNEL_DESC,
      soundUriString = platform.getDefaultSystemAlarmUriString(),
      config = userModifiedLegacyConfig
    )

    val coordinator = AlarmToneCoordinator(store, platform)
    val selected = coordinator.selectCustomAudioUri(customUri) as AlarmToneSelectionResult.Selected

    assertNotEquals(AlarmToneCoordinator.LEGACY_DEFAULT_CHANNEL_ID, selected.channelId)
    val customChannel = platform.channels[selected.channelId]!!
    assertEquals(customUri, customChannel.soundUriString)
    // User's notification channel config from Android Settings was safely migrated forward
    assertEquals(NotificationManager.IMPORTANCE_DEFAULT, customChannel.config.importance)
    assertFalse(customChannel.config.enableVibration)
    assertFalse(customChannel.config.bypassDnd)

    // Legacy channel was never deleted or overwritten
    val legacyChannel = platform.channels[AlarmToneCoordinator.LEGACY_DEFAULT_CHANNEL_ID]!!
    assertEquals(platform.getDefaultSystemAlarmUriString(), legacyChannel.soundUriString)
    assertFalse(platform.deletedChannelIds.contains(AlarmToneCoordinator.LEGACY_DEFAULT_CHANNEL_ID))
  }

  @Test
  fun test7_changingTonesDoesNotCreateUnboundedDuplicateChannels() {
    val store = InMemoryAlarmToneStore()
    val platform = FakeAlarmTonePlatformPort()
    val uris = (1..12).map { index ->
      val uri = "content://media/external/audio/media/$index"
      platform.registerValidAudio(uri, "tone_$index.mp3")
      uri
    }

    val coordinator = AlarmToneCoordinator(store, platform)

    // 1. Opening/refreshing many times for same selection creates zero duplicate channels
    coordinator.selectCustomAudioUri(uris[0])
    val channelCountAfterFirstSelect = platform.channels.size
    repeat(20) {
      coordinator.refreshAndEnsureChannel()
      coordinator.resolveForNotification()
    }
    assertEquals(channelCountAfterFirstSelect, platform.channels.size)

    // 2. Switching between 12 distinct custom tones keeps retained custom channels bounded (<= MAX_RETAINED_CUSTOM_CHANNELS)
    uris.forEach { uri ->
      coordinator.selectCustomAudioUri(uri)
    }

    val customChannelsInSystem = platform.channels.keys.filter {
      it.startsWith(AlarmToneCoordinator.CUSTOM_CHANNEL_PREFIX)
    }
    assertTrue(customChannelsInSystem.size <= AlarmToneCoordinator.MAX_RETAINED_CUSTOM_CHANNELS)
    assertTrue(platform.channels.containsKey(AlarmToneCoordinator.LEGACY_DEFAULT_CHANNEL_ID))
    assertFalse(platform.deletedChannelIds.contains(AlarmToneCoordinator.LEGACY_DEFAULT_CHANNEL_ID))

    // 3. Re-selecting an already-retained tone reuses its existing channel with zero new allocations
    val latestUri = uris.last()
    val prevCreateCount = platform.createChannelCallsCount
    coordinator.selectCustomAudioUri(latestUri)
    assertEquals(prevCreateCount, platform.createChannelCallsCount)
  }

  @Test
  fun test8_remindersCanStillBeScheduledAfterToneChangeOrFallback() {
    val store = InMemoryAlarmToneStore()
    val toneA = "content://media/external/audio/media/201"
    val toneB = "content://media/external/audio/media/202"
    val platform = FakeAlarmTonePlatformPort().apply {
      registerValidAudio(toneA, "toneA.mp3")
      registerValidAudio(toneB, "toneB.wav")
    }
    val coordinator = AlarmToneCoordinator(store, platform)

    val reminder = ReminderEntity(
      id = 77L,
      title = "Minum Vitamin",
      dueDateTime = System.currentTimeMillis() + 3_600_000L
    )
    assertTrue(reminder.dueDateTime > 0L)
    assertFalse(reminder.isCompleted)

    // Tone A -> Tone B -> Deleted Tone B (fallback) -> Default
    val resA = coordinator.selectCustomAudioUri(toneA) as AlarmToneSelectionResult.Selected
    assertEquals(resA.channelId, coordinator.resolveForNotification().channelId)

    val resB = coordinator.selectCustomAudioUri(toneB) as AlarmToneSelectionResult.Selected
    assertEquals(resB.channelId, coordinator.resolveForNotification().channelId)

    platform.validUris.remove(toneB)
    val fallbackRes = coordinator.resolveForNotification()
    assertEquals(AlarmToneCoordinator.LEGACY_DEFAULT_CHANNEL_ID, fallbackRes.channelId)
    assertEquals(platform.getDefaultSystemAlarmUriString(), fallbackRes.effectiveUriString)

    coordinator.resetToDefaultTone()
    val defaultRes = coordinator.resolveForNotification()
    assertEquals(NotificationHelper.CHANNEL_ID, defaultRes.channelId)
  }

  @Test
  fun test9_repeatingRemindersContinueCalculatingNextOccurrencesCorrectly() {
    val tz = TimeZone.getTimeZone("Asia/Jakarta")
    val baseCal = Calendar.getInstance(tz).apply {
      set(2026, Calendar.JANUARY, 31, 8, 30, 0)
      set(Calendar.MILLISECOND, 0)
    }
    val currentDue = baseCal.timeInMillis
    val baseNow = currentDue + 1_000L

    val nextDaily = ReminderAlarmReceiver.calculateNextDueTime(
      currentDue = currentDue,
      repeatMode = RepeatMode.DAILY,
      timeZone = tz,
      baseNowMillis = baseNow
    )
    val dailyCal = Calendar.getInstance(tz).apply { timeInMillis = nextDaily }
    assertEquals(2026, dailyCal.get(Calendar.YEAR))
    assertEquals(Calendar.FEBRUARY, dailyCal.get(Calendar.MONTH))
    assertEquals(1, dailyCal.get(Calendar.DAY_OF_MONTH))
    assertEquals(8, dailyCal.get(Calendar.HOUR_OF_DAY))

    val nextWeekly = ReminderAlarmReceiver.calculateNextDueTime(
      currentDue = currentDue,
      repeatMode = RepeatMode.WEEKLY,
      timeZone = tz,
      baseNowMillis = baseNow
    )
    val weeklyCal = Calendar.getInstance(tz).apply { timeInMillis = nextWeekly }
    assertEquals(Calendar.FEBRUARY, weeklyCal.get(Calendar.MONTH))
    assertEquals(7, weeklyCal.get(Calendar.DAY_OF_MONTH))

    val nextMonthly = ReminderAlarmReceiver.calculateNextDueTime(
      currentDue = currentDue,
      repeatMode = RepeatMode.MONTHLY,
      timeZone = tz,
      baseNowMillis = baseNow
    )
    val monthlyCal = Calendar.getInstance(tz).apply { timeInMillis = nextMonthly }
    assertEquals(Calendar.FEBRUARY, monthlyCal.get(Calendar.MONTH))
    assertEquals(28, monthlyCal.get(Calendar.DAY_OF_MONTH))
    assertEquals(8, monthlyCal.get(Calendar.HOUR_OF_DAY))
    assertEquals(30, monthlyCal.get(Calendar.MINUTE))
  }

  @Test
  fun test10_roomSchemaBackupFormatAiConfigAndStage7CRemainUnchanged() {
    // Database version & Backup manifest constants unchanged
    assertEquals(2, BackupManifest.CURRENT_DATABASE_VERSION)
    assertEquals(1, BackupManifest.CURRENT_BACKUP_VERSION)

    // Stage 7A/Gemini config unchanged
    assertEquals("gemini-3.8-flash", GeminiConfig.MODEL_NAME)
    assertEquals(
      "Perintah tidak dikenal, apakah Anda bisa mengulang kembali?",
      GeminiConfig.DEFAULT_FRIENDLY_FAILURE_MESSAGE
    )

    // Room entities unchanged
    val reminder = ReminderEntity(title = "Tes Reminder", dueDateTime = 1_760_000_000_000L)
    assertEquals(Priority.MEDIUM, reminder.priority)
    assertEquals("General", reminder.category)

    val task = TaskEntity(title = "Legacy Task")
    assertEquals(Priority.MEDIUM, task.priority)

    // Alarm tone SharedPreferences keys stay isolated from Stage 7C TTS Voice preference key
    assertNotEquals(
      SharedPreferencesTtsVoiceStore.KEY_SELECTED_TTS_VOICE_ID,
      SharedPreferencesAlarmToneStore.KEY_SELECTED_ALARM_TONE_URI
    )
    assertEquals(
      NotificationHelper.CHANNEL_ID,
      AlarmToneCoordinator.LEGACY_DEFAULT_CHANNEL_ID
    )
  }
}
