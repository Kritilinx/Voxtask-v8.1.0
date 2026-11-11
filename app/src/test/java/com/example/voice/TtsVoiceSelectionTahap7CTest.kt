package com.example.voice

import com.example.ai.AiCommandIntent
import com.example.ai.AiResponseValidator
import com.example.ai.GeminiConfig
import com.example.data.backup.BackupManifest
import com.example.data.local.entity.ReminderEntity
import com.example.data.local.entity.TaskEntity
import com.example.data.model.Priority
import com.example.ui.navigation.VoxDestination
import com.example.ui.screens.ReminderFilter
import com.example.voice.parser.CommandIntent
import com.example.voice.parser.LocalCommandParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * Unit tests for Stage 7C (AI Voice Selection using Android Native TextToSpeech).
 * Covers all 10 mandatory verification items purely on JVM without Robolectric.
 */
class TtsVoiceSelectionTahap7CTest {

  private class InMemoryTtsVoiceStore(
    initialVoiceId: String? = null
  ) : TtsVoiceStore {
    var storedVoiceId: String? = initialVoiceId
    var writeCount: Int = 0

    override fun getSelectedVoiceId(): String? = storedVoiceId?.takeIf { it.isNotBlank() }

    override fun setSelectedVoiceId(voiceId: String?) {
      writeCount++
      storedVoiceId = voiceId?.takeIf { it.isNotBlank() }
    }
  }

  private data class SpokenRecord(
    val text: String,
    val queueFlush: Boolean,
    val utteranceId: String,
    val voiceUsedAtSpeakTime: String?
  )

  private class FakeTtsEnginePort(
    private val engineVoices: List<TtsEngineVoice>,
    private val defaultVoiceId: String? = "default-system-voice",
    private val initSuccess: Boolean = true,
    private val autoCompleteInit: Boolean = true
  ) : TtsEnginePort {

    var initializeCount: Int = 0
    var appliedVoiceId: String? = defaultVoiceId
    var fallbackLanguageAppliedCount: Int = 0
    var stopCount: Int = 0
    var shutdownCount: Int = 0
    val setVoiceCalls = mutableListOf<String>()
    val spokenRecords = mutableListOf<SpokenRecord>()

    var failSetVoiceForId: String? = null
    var throwOnSetVoiceForId: String? = null
    var failSpeakOnceWhileVoiceId: String? = null
    var listenerRef: TtsEngineListener? = null
    private var pendingInitCallback: ((Boolean) -> Unit)? = null

    override fun initialize(
      listener: TtsEngineListener,
      onInitResult: (success: Boolean) -> Unit
    ) {
      initializeCount++
      listenerRef = listener
      if (autoCompleteInit) {
        onInitResult(initSuccess)
      } else {
        pendingInitCallback = onInitResult
      }
    }

    fun triggerInitResult(success: Boolean = initSuccess) {
      pendingInitCallback?.invoke(success)
      pendingInitCallback = null
    }

    override fun getVoices(): List<TtsEngineVoice> = engineVoices

    override fun getCurrentOrDefaultVoiceId(): String? = appliedVoiceId ?: defaultVoiceId

    override fun setVoiceById(voiceId: String): Boolean {
      setVoiceCalls.add(voiceId)
      if (throwOnSetVoiceForId == voiceId) {
        throw IllegalStateException("Engine rejected voice $voiceId")
      }
      if (failSetVoiceForId == voiceId) {
        return false
      }
      val exists = engineVoices.any { !it.isNotInstalled && it.id == voiceId }
      if (!exists) return false
      appliedVoiceId = voiceId
      return true
    }

    override fun applyDefaultLanguageFallback(): Boolean {
      fallbackLanguageAppliedCount++
      appliedVoiceId = defaultVoiceId
      return true
    }

    override fun speak(text: String, queueFlush: Boolean, utteranceId: String): Boolean {
      if (failSpeakOnceWhileVoiceId != null && appliedVoiceId == failSpeakOnceWhileVoiceId) {
        failSpeakOnceWhileVoiceId = null
        return false
      }
      spokenRecords.add(
        SpokenRecord(
          text = text,
          queueFlush = queueFlush,
          utteranceId = utteranceId,
          voiceUsedAtSpeakTime = appliedVoiceId
        )
      )
      return true
    }

    override fun stop() {
      stopCount++
    }

    override fun shutdown() {
      shutdownCount++
    }
  }

  private fun sampleDeviceVoices(): List<TtsEngineVoice> = listOf(
    TtsEngineVoice(
      id = "en-us-x-sfg-local",
      locale = Locale.US,
      isNetworkConnectionRequired = false,
      isNotInstalled = false,
      quality = 400
    ),
    TtsEngineVoice(
      id = "id-id-x-dfz-network",
      locale = Locale.forLanguageTag("id-ID"),
      isNetworkConnectionRequired = true,
      isNotInstalled = false,
      quality = 500
    ),
    TtsEngineVoice(
      id = "id-id-x-idd-local",
      locale = Locale.forLanguageTag("id-ID"),
      isNetworkConnectionRequired = false,
      isNotInstalled = false,
      quality = 400
    ),
    TtsEngineVoice(
      id = "id-id-x-f02-local",
      locale = Locale.Builder().setLanguage("id").setRegion("ID").build(),
      isNetworkConnectionRequired = false,
      isNotInstalled = false,
      quality = 500
    ),
    TtsEngineVoice(
      id = "id-generic-local",
      locale = Locale.forLanguageTag("id"),
      isNetworkConnectionRequired = false,
      isNotInstalled = false,
      quality = 300
    ),
    TtsEngineVoice(
      id = "id-id-x-not-downloaded",
      locale = Locale.forLanguageTag("id-ID"),
      isNetworkConnectionRequired = false,
      isNotInstalled = true,
      quality = 500
    ),
    TtsEngineVoice(
      id = "ja-jp-x-jac-local",
      locale = Locale.JAPAN,
      isNetworkConnectionRequired = false,
      isNotInstalled = false,
      quality = 400
    )
  )

  @Test
  fun test1_voiceListComesDynamicallyFromTtsEngine_neverHardcoded() {
    val store = InMemoryTtsVoiceStore()
    val emptyEngine = FakeTtsEnginePort(engineVoices = emptyList())
    val coordinatorWithEmptyEngine = TtsVoiceCoordinator(store, emptyEngine)

    assertTrue(coordinatorWithEmptyEngine.uiState.value.availableVoices.isEmpty())
    coordinatorWithEmptyEngine.prepareTts()
    assertEquals(TtsInitStatus.READY, coordinatorWithEmptyEngine.uiState.value.initStatus)
    assertTrue(coordinatorWithEmptyEngine.uiState.value.availableVoices.isEmpty())

    val customEngineVoices = listOf(
      TtsEngineVoice(id = "fr-fr-engine-v1", locale = Locale.FRANCE, isNotInstalled = false),
      TtsEngineVoice(id = "de-de-not-installed", locale = Locale.GERMANY, isNotInstalled = true)
    )
    val customEngine = FakeTtsEnginePort(engineVoices = customEngineVoices)
    val coordinatorWithCustomEngine = TtsVoiceCoordinator(store, customEngine)
    coordinatorWithCustomEngine.prepareTts()

    val reportedIds = coordinatorWithCustomEngine.uiState.value.availableVoices.map { it.id }
    assertEquals(listOf("fr-fr-engine-v1"), reportedIds)
    assertFalse(coordinatorWithCustomEngine.uiState.value.hasIndonesianVoice)
  }

  @Test
  fun test2_indonesianIdIdVoicesArePrioritizedAheadOfOtherLanguages() {
    val store = InMemoryTtsVoiceStore()
    val engine = FakeTtsEnginePort(engineVoices = sampleDeviceVoices())
    val coordinator = TtsVoiceCoordinator(store, engine)

    coordinator.prepareTts()
    val voices = coordinator.uiState.value.availableVoices

    // Installed voices only (notInstalled voice must be filtered out)
    assertFalse(voices.any { it.id == "id-id-x-not-downloaded" })
    assertEquals(6, voices.size)
    assertTrue(coordinator.uiState.value.hasIndonesianVoice)

    // Exact id-ID local voices come first, followed by id-ID network, general id, then other locales
    assertEquals("id-id-x-f02-local", voices[0].id)
    assertEquals("id-id-x-idd-local", voices[1].id)
    assertEquals("id-id-x-dfz-network", voices[2].id)
    assertEquals("id-generic-local", voices[3].id)
    assertTrue(voices[0].isExactIndonesian)
    assertEquals("id-ID", voices[0].localeTag)

    // Prioritized id-ID voice is used by default without prematurely persisting manual choice
    assertEquals("id-id-x-f02-local", engine.appliedVoiceId)
    assertNull(store.getSelectedVoiceId())
  }

  @Test
  fun test3_selectingVoicePersistsStableEngineIdentifier() {
    val store = InMemoryTtsVoiceStore()
    val engine = FakeTtsEnginePort(engineVoices = sampleDeviceVoices())
    val coordinator = TtsVoiceCoordinator(store, engine)
    coordinator.prepareTts()

    coordinator.selectVoice("id-id-x-idd-local")

    assertEquals("id-id-x-idd-local", store.getSelectedVoiceId())
    assertEquals("id-id-x-idd-local", coordinator.uiState.value.selectedVoiceId)
    assertEquals("id-id-x-idd-local", coordinator.uiState.value.activeVoiceId)
    assertEquals("id-id-x-idd-local", engine.appliedVoiceId)
    assertFalse(coordinator.uiState.value.isUsingFallbackVoice)
  }

  @Test
  fun test4_selectedVoiceIsRestoredAfterAppReopen() {
    val persistentStore = InMemoryTtsVoiceStore()

    // First session: user selects voice and closes app
    val engineSession1 = FakeTtsEnginePort(engineVoices = sampleDeviceVoices())
    val coordinatorSession1 = TtsVoiceCoordinator(persistentStore, engineSession1)
    coordinatorSession1.prepareTts()
    coordinatorSession1.selectVoice("en-us-x-sfg-local")
    coordinatorSession1.release()

    // Second session (fresh coordinator & engine instance, same preference store)
    val engineSession2 = FakeTtsEnginePort(engineVoices = sampleDeviceVoices())
    val coordinatorSession2 = TtsVoiceCoordinator(persistentStore, engineSession2)
    assertEquals("en-us-x-sfg-local", coordinatorSession2.uiState.value.selectedVoiceId)

    // Even if speak() is requested during cold startup before init finishes, restored voice applies first
    coordinatorSession2.speak("Pengingat tersimpan", utteranceId = "cold_start_msg")
    assertEquals("en-us-x-sfg-local", coordinatorSession2.uiState.value.activeVoiceId)
    assertEquals("en-us-x-sfg-local", engineSession2.appliedVoiceId)
    assertEquals(1, engineSession2.spokenRecords.size)
    assertEquals("en-us-x-sfg-local", engineSession2.spokenRecords[0].voiceUsedAtSpeakTime)
  }

  @Test
  fun test5_missingOrRemovedSavedVoiceIdentifierTriggersFallbackSafely() {
    val store = InMemoryTtsVoiceStore(initialVoiceId = "removed-voice-from-device")
    val engine = FakeTtsEnginePort(
      engineVoices = sampleDeviceVoices(),
      defaultVoiceId = "android-default-voice"
    )
    val coordinator = TtsVoiceCoordinator(store, engine)

    coordinator.prepareTts()

    assertTrue(coordinator.uiState.value.isUsingFallbackVoice)
    assertEquals("android-default-voice", coordinator.uiState.value.activeVoiceId)
    assertEquals(1, engine.fallbackLanguageAppliedCount)

    coordinator.speak("Tes fallback setelah suara dihapus")
    assertEquals(1, engine.spokenRecords.size)
    assertEquals("android-default-voice", engine.spokenRecords[0].voiceUsedAtSpeakTime)
  }

  @Test
  fun test6_ttsContinuesWorkingWhenSelectedVoiceFailsAtEngineOrSpeakTime() {
    // Case A: setVoiceById returns false for an available voice
    val storeA = InMemoryTtsVoiceStore(initialVoiceId = "id-id-x-idd-local")
    val engineA = FakeTtsEnginePort(
      engineVoices = sampleDeviceVoices(),
      defaultVoiceId = "android-safe-fallback"
    ).apply {
      failSetVoiceForId = "id-id-x-idd-local"
    }
    val coordinatorA = TtsVoiceCoordinator(storeA, engineA)
    coordinatorA.prepareTts()

    assertTrue(coordinatorA.uiState.value.isUsingFallbackVoice)
    assertEquals(1, engineA.fallbackLanguageAppliedCount)
    coordinatorA.speak("Tetap bersuara meski setVoice gagal")
    assertEquals(1, engineA.spokenRecords.size)
    assertEquals("android-safe-fallback", engineA.spokenRecords[0].voiceUsedAtSpeakTime)

    // Case B: setVoiceById succeeds, then engine.speak fails once with custom voice -> automatic fallback & retry
    val storeB = InMemoryTtsVoiceStore(initialVoiceId = "id-id-x-dfz-network")
    val engineB = FakeTtsEnginePort(
      engineVoices = sampleDeviceVoices(),
      defaultVoiceId = "android-retry-default"
    ).apply {
      failSpeakOnceWhileVoiceId = "id-id-x-dfz-network"
    }
    val coordinatorB = TtsVoiceCoordinator(storeB, engineB)
    coordinatorB.prepareTts()
    coordinatorB.speak("Uji retry fallback")

    assertTrue(coordinatorB.uiState.value.isUsingFallbackVoice)
    assertEquals(1, engineB.spokenRecords.size)
    assertEquals("Uji retry fallback", engineB.spokenRecords[0].text)
    assertEquals("android-retry-default", engineB.spokenRecords[0].voiceUsedAtSpeakTime)

    // Case C: TTS engine initialization completely fails -> no crash on preview or speak
    val storeC = InMemoryTtsVoiceStore()
    val failedEngine = FakeTtsEnginePort(engineVoices = emptyList(), initSuccess = false)
    val coordinatorC = TtsVoiceCoordinator(storeC, failedEngine)
    coordinatorC.prepareTts()
    assertEquals(TtsInitStatus.ERROR, coordinatorC.uiState.value.initStatus)
    assertEquals(TtsPreviewOutcome.ENGINE_UNAVAILABLE, coordinatorC.previewSelectedVoice())
    coordinatorC.speak("Pesan saat TTS gagal")
  }

  @Test
  fun test7_previewButtonUsesSelectedVoiceNeverPersistsAndPreventsOverlap() {
    val store = InMemoryTtsVoiceStore()
    val engine = FakeTtsEnginePort(engineVoices = sampleDeviceVoices())
    val coordinator = TtsVoiceCoordinator(store, engine)
    coordinator.prepareTts()

    // Preview while no voice was manually saved yet -> uses prioritized active voice and does NOT write to store
    val writeCountBefore = store.writeCount
    val outcome1 = coordinator.previewSelectedVoice()
    assertEquals(TtsPreviewOutcome.SPOKEN, outcome1)
    assertEquals(writeCountBefore, store.writeCount)
    assertNull(store.getSelectedVoiceId())
    assertEquals(1, engine.spokenRecords.size)
    assertEquals(
      "Halo, saya VoxTask. Ada yang bisa saya bantu?",
      engine.spokenRecords[0].text
    )
    assertTrue(engine.spokenRecords[0].queueFlush)
    assertEquals("id-id-x-f02-local", engine.spokenRecords[0].voiceUsedAtSpeakTime)

    // Select another voice, simulate ongoing speech with a done-callback, then trigger preview
    coordinator.selectVoice("id-id-x-idd-local")
    val writesAfterSelect = store.writeCount
    var staleCallbackTriggered = false
    coordinator.speak("Ucapan sebelumnya", utteranceId = "prev_id") {
      staleCallbackTriggered = true
    }

    val outcome2 = coordinator.previewSelectedVoice()
    assertEquals(TtsPreviewOutcome.SPOKEN, outcome2)
    // Preview must NEVER persist a new store write
    assertEquals(writesAfterSelect, store.writeCount)
    assertEquals("id-id-x-idd-local", store.getSelectedVoiceId())
    // Stop was invoked to prevent overlap and clear prior callbacks
    assertTrue(engine.stopCount >= 1)
    engine.listenerRef?.onUtteranceDone("prev_id")
    assertFalse(staleCallbackTriggered)

    val previewRecord = engine.spokenRecords.last()
    assertEquals("Halo, saya VoxTask. Ada yang bisa saya bantu?", previewRecord.text)
    assertTrue(previewRecord.queueFlush)
    assertEquals("id-id-x-idd-local", previewRecord.voiceUsedAtSpeakTime)
  }

  @Test
  fun test7b_repeatedPreviewClicksBeforeInitCompletes_doNotStackInQueue() {
    val store = InMemoryTtsVoiceStore()
    val asyncEngine = FakeTtsEnginePort(
      engineVoices = sampleDeviceVoices(),
      autoCompleteInit = false
    )
    val coordinator = TtsVoiceCoordinator(store, asyncEngine)

    val outcome1 = coordinator.previewSelectedVoice()
    val outcome2 = coordinator.previewSelectedVoice()
    assertEquals(TtsPreviewOutcome.QUEUED_FOR_INIT, outcome1)
    assertEquals(TtsPreviewOutcome.QUEUED_FOR_INIT, outcome2)
    assertNull(store.getSelectedVoiceId())

    asyncEngine.triggerInitResult(true)
    assertEquals(1, asyncEngine.spokenRecords.size)
    assertEquals("Halo, saya VoxTask. Ada yang bisa saya bantu?", asyncEngine.spokenRecords[0].text)
  }

  @Test
  fun test8_aiAndLocalParserResponsesUseSameConfiguredVoiceAndSingleEngineInstance() {
    val store = InMemoryTtsVoiceStore()
    val engine = FakeTtsEnginePort(engineVoices = sampleDeviceVoices())
    val coordinator = TtsVoiceCoordinator(store, engine)
    coordinator.prepareTts()
    coordinator.selectVoice("id-id-x-idd-local")

    // 1. Local parser response
    val localParsed = LocalCommandParser.parse("agenda hari ini")
    assertEquals(CommandIntent.VIEW_TODAY_AGENDA, localParsed.intent)
    val localResponseText = "Agenda pengingat hari ini: 1 pengingat."
    coordinator.speak(localResponseText, utteranceId = "local_agenda")

    // 2. Gemini AI validated fallback response
    val aiValidated = AiResponseValidator.validateJson(
      rawJson = """
        {
          "intent": "CREATE_REMINDER",
          "title": "Rapat Tim",
          "description": "",
          "date": "2026-11-12",
          "time": "10:00",
          "priority": "MEDIUM",
          "repeatMode": "ONCE",
          "responseText": "Siap, pengingat Rapat Tim berhasil dijadwalkan.",
          "requiresConfirmation": false
        }
      """.trimIndent(),
      originalInput = "tolong jadwalkan rapat tim lusa jam 10"
    )
    assertEquals(AiCommandIntent.CREATE_REMINDER, aiValidated.intent)
    coordinator.speak(aiValidated.responseText, utteranceId = "gemini_resp")

    // Single TTS engine initialization and consistent voice for both local and AI responses
    assertEquals(1, engine.initializeCount)
    assertEquals(2, engine.spokenRecords.size)
    assertEquals("id-id-x-idd-local", engine.spokenRecords[0].voiceUsedAtSpeakTime)
    assertEquals("id-id-x-idd-local", engine.spokenRecords[1].voiceUsedAtSpeakTime)
    assertEquals(localResponseText, engine.spokenRecords[0].text)
    assertEquals(aiValidated.responseText, engine.spokenRecords[1].text)
  }

  @Test
  fun test9_stage7AFriendlyAiFailureMessageTextRemainsExactWhenSpoken() {
    val expectedStage7AMessage = "Perintah tidak dikenal, apakah Anda bisa mengulang kembali?"
    assertEquals(expectedStage7AMessage, GeminiConfig.DEFAULT_FRIENDLY_FAILURE_MESSAGE)

    val store = InMemoryTtsVoiceStore(initialVoiceId = "id-id-x-f02-local")
    val engine = FakeTtsEnginePort(engineVoices = sampleDeviceVoices())
    val coordinator = TtsVoiceCoordinator(store, engine)
    coordinator.prepareTts()

    coordinator.speak(GeminiConfig.DEFAULT_FRIENDLY_FAILURE_MESSAGE, utteranceId = "stage7a_fail")

    assertEquals(1, engine.spokenRecords.size)
    assertEquals(expectedStage7AMessage, engine.spokenRecords[0].text)
    assertEquals("id-id-x-f02-local", engine.spokenRecords[0].voiceUsedAtSpeakTime)
  }

  @Test
  fun test10_databaseBackupAiConfigAndNavigationRemainUnchanged() {
    assertEquals(2, BackupManifest.CURRENT_DATABASE_VERSION)
    assertEquals("gemini-3.8-flash", GeminiConfig.MODEL_NAME)

    val reminder = ReminderEntity(title = "Tes", dueDateTime = 1_760_000_000_000L)
    assertEquals(Priority.MEDIUM, reminder.priority)
    assertEquals("General", reminder.category)

    val task = TaskEntity(title = "Legacy Task")
    assertEquals(Priority.MEDIUM, task.priority)
    assertEquals("General", task.category)

    assertEquals(
      listOf(VoxDestination.HOME, VoxDestination.REMINDERS, VoxDestination.SETTINGS),
      VoxDestination.entries
    )
    assertEquals(
      listOf(ReminderFilter.ALL, ReminderFilter.UPCOMING, ReminderFilter.COMPLETED),
      ReminderFilter.entries
    )
  }
}
