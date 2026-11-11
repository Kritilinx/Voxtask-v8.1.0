package com.example.voice

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

enum class TtsInitStatus {
  IDLE,
  INITIALIZING,
  READY,
  ERROR
}

enum class TtsPreviewOutcome {
  SPOKEN,
  QUEUED_FOR_INIT,
  ENGINE_UNAVAILABLE,
  FAILED
}

data class TtsEngineVoice(
  val id: String,
  val locale: Locale,
  val isNetworkConnectionRequired: Boolean = false,
  val isNotInstalled: Boolean = false,
  val quality: Int = 300
)

data class TtsVoiceInfo(
  val id: String,
  val displayName: String,
  val localeTag: String,
  val languageDisplayName: String,
  val isExactIndonesian: Boolean,
  val isIndonesian: Boolean,
  val requiresNetwork: Boolean,
  val quality: Int
)

data class TtsVoiceUiState(
  val initStatus: TtsInitStatus = TtsInitStatus.IDLE,
  val availableVoices: List<TtsVoiceInfo> = emptyList(),
  val selectedVoiceId: String? = null,
  val activeVoiceId: String? = null,
  val hasIndonesianVoice: Boolean = false,
  val isUsingFallbackVoice: Boolean = false,
  val feedbackMessage: String? = null
)

interface TtsVoiceStore {
  fun getSelectedVoiceId(): String?
  fun setSelectedVoiceId(voiceId: String?)
}

class SharedPreferencesTtsVoiceStore(context: Context) : TtsVoiceStore {
  private val prefs: SharedPreferences =
    context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  override fun getSelectedVoiceId(): String? {
    val stored = prefs.getString(KEY_SELECTED_TTS_VOICE_ID, null)
    return stored?.takeIf { it.isNotBlank() }
  }

  override fun setSelectedVoiceId(voiceId: String?) {
    if (voiceId.isNullOrBlank()) {
      prefs.edit().remove(KEY_SELECTED_TTS_VOICE_ID).apply()
    } else {
      prefs.edit().putString(KEY_SELECTED_TTS_VOICE_ID, voiceId).apply()
    }
  }

  companion object {
    const val PREFS_NAME = "voxtask_prefs"
    const val KEY_SELECTED_TTS_VOICE_ID = "selected_tts_voice_id"
  }
}

interface TtsEngineListener {
  fun onUtteranceStart(utteranceId: String?)
  fun onUtteranceDone(utteranceId: String?)
  fun onUtteranceError(utteranceId: String?)
}

interface TtsEnginePort {
  fun initialize(listener: TtsEngineListener, onInitResult: (success: Boolean) -> Unit)
  fun getVoices(): List<TtsEngineVoice>
  fun getCurrentOrDefaultVoiceId(): String?
  fun setVoiceById(voiceId: String): Boolean
  fun applyDefaultLanguageFallback(): Boolean
  fun speak(text: String, queueFlush: Boolean, utteranceId: String): Boolean
  fun stop()
  fun shutdown()
}

object TtsVoiceSelector {
  const val PREVIEW_SAMPLE_TEXT = "Halo, saya VoxTask. Ada yang bisa saya bantu?"
  private val DISPLAY_LOCALE = Locale.forLanguageTag("id-ID")

  fun extractAvailableVoices(rawVoices: Collection<TtsEngineVoice>): List<TtsVoiceInfo> {
    return rawVoices
      .asSequence()
      .filter { !it.isNotInstalled && it.id.isNotBlank() }
      .distinctBy { it.id }
      .map { voice ->
        val lang = voice.locale.language.lowercase(Locale.ROOT)
        val country = voice.locale.country.uppercase(Locale.ROOT)
        val rawTag = voice.locale.toLanguageTag()
        val normalizedTag = when {
          rawTag.equals("in-ID", ignoreCase = true) -> "id-ID"
          rawTag.equals("in", ignoreCase = true) -> "id"
          rawTag.isNotBlank() && rawTag != "und" -> rawTag
          country.isNotBlank() -> "$lang-$country"
          else -> lang.ifBlank { "default" }
        }
        val isIndonesianLang = (lang == "id" || lang == "in")
        val isExactId = isIndonesianLang && (country == "ID" || normalizedTag.equals("id-ID", ignoreCase = true))
        val langDisplay = voice.locale.getDisplayName(DISPLAY_LOCALE).ifBlank { normalizedTag }

        TtsVoiceInfo(
          id = voice.id,
          displayName = voice.id,
          localeTag = normalizedTag,
          languageDisplayName = langDisplay,
          isExactIndonesian = isExactId,
          isIndonesian = isIndonesianLang,
          requiresNetwork = voice.isNetworkConnectionRequired,
          quality = voice.quality
        )
      }
      .sortedWith(
        compareByDescending<TtsVoiceInfo> { it.isExactIndonesian }
          .thenByDescending { it.isIndonesian }
          .thenBy { it.requiresNetwork }
          .thenByDescending { it.quality }
          .thenBy { it.localeTag.lowercase(Locale.ROOT) }
          .thenBy { it.id.lowercase(Locale.ROOT) }
      )
      .toList()
  }

  fun pickPrioritizedIndonesianVoice(availableVoices: List<TtsVoiceInfo>): TtsVoiceInfo? {
    return availableVoices.firstOrNull { it.isExactIndonesian }
      ?: availableVoices.firstOrNull { it.isIndonesian }
  }
}

class TtsVoiceCoordinator(
  private val store: TtsVoiceStore,
  private val engine: TtsEnginePort,
  private val onSpeechStateChanged: (VoiceAssistantState?) -> Unit = {},
  private val runOnMainThread: (() -> Unit) -> Unit = { action -> action() },
  private val logError: (String, Throwable?) -> Unit = { _, _ -> }
) {

  private val _uiState = MutableStateFlow(
    TtsVoiceUiState(selectedVoiceId = store.getSelectedVoiceId())
  )
  val uiState: StateFlow<TtsVoiceUiState> = _uiState.asStateFlow()

  @Volatile
  private var isTtsInitialized = false

  @Volatile
  private var isTtsInitializing = false

  @Volatile
  private var voiceAppliedOnEngine = false

  private val pendingSpeakQueue = mutableListOf<PendingUtterance>()
  private val pendingReadyCallbacks = mutableListOf<() -> Unit>()
  private val onSpeechDoneCallbacks = ConcurrentHashMap<String, () -> Unit>()

  private data class PendingUtterance(
    val text: String,
    val utteranceId: String,
    val isPreview: Boolean
  )

  private val engineListener = object : TtsEngineListener {
    override fun onUtteranceStart(utteranceId: String?) {
      onSpeechStateChanged(VoiceAssistantState.Speaking(""))
    }

    override fun onUtteranceDone(utteranceId: String?) {
      val callback = if (utteranceId != null) onSpeechDoneCallbacks.remove(utteranceId) else null
      runOnMainThread {
        if (callback == null) {
          onSpeechStateChanged(VoiceAssistantState.Idle)
        } else {
          callback.invoke()
        }
      }
    }

    override fun onUtteranceError(utteranceId: String?) {
      if (utteranceId != null) {
        onSpeechDoneCallbacks.remove(utteranceId)
      }
      runOnMainThread {
        onSpeechStateChanged(VoiceAssistantState.Idle)
      }
    }
  }

  fun prepareTts(onReady: () -> Unit = {}) {
    if (isTtsInitialized) {
      refreshVoicesInternal()
      onReady()
      return
    }

    synchronized(pendingReadyCallbacks) {
      pendingReadyCallbacks.add(onReady)
      if (isTtsInitializing) return
      isTtsInitializing = true
    }

    _uiState.value = _uiState.value.copy(
      initStatus = TtsInitStatus.INITIALIZING,
      selectedVoiceId = store.getSelectedVoiceId(),
      feedbackMessage = null
    )

    try {
      engine.initialize(engineListener) { success ->
        isTtsInitializing = false
        if (success) {
          isTtsInitialized = true
          refreshVoicesInternal()
          applyConfiguredVoiceInternal()
          flushPendingSpeakQueue()
          val callbacks = synchronized(pendingReadyCallbacks) {
            pendingReadyCallbacks.toList().also { pendingReadyCallbacks.clear() }
          }
          callbacks.forEach { it.invoke() }
        } else {
          isTtsInitialized = false
          voiceAppliedOnEngine = false
          _uiState.value = _uiState.value.copy(
            initStatus = TtsInitStatus.ERROR,
            availableVoices = emptyList(),
            hasIndonesianVoice = false,
            activeVoiceId = null,
            feedbackMessage = "Mesin Text-to-Speech gagal diinisialisasi atau belum tersedia di perangkat."
          )
          synchronized(pendingSpeakQueue) {
            pendingSpeakQueue.clear()
          }
          onSpeechDoneCallbacks.clear()
          synchronized(pendingReadyCallbacks) {
            pendingReadyCallbacks.clear()
          }
        }
      }
    } catch (e: Exception) {
      logError("TTS initialize failed: ${e.message}", e)
      isTtsInitializing = false
      isTtsInitialized = false
      voiceAppliedOnEngine = false
      _uiState.value = _uiState.value.copy(
        initStatus = TtsInitStatus.ERROR,
        feedbackMessage = "Gagal menyiapkan mesin Text-to-Speech."
      )
      synchronized(pendingReadyCallbacks) {
        pendingReadyCallbacks.clear()
      }
    }
  }

  fun refreshVoices() {
    if (!isTtsInitialized) {
      prepareTts()
      return
    }
    refreshVoicesInternal()
    applyConfiguredVoiceInternal()
  }

  fun selectVoice(voiceId: String) {
    val cleanId = voiceId.trim()
    if (cleanId.isBlank()) {
      clearSelectedVoice()
      return
    }
    store.setSelectedVoiceId(cleanId)
    _uiState.value = _uiState.value.copy(
      selectedVoiceId = cleanId,
      feedbackMessage = null
    )
    if (isTtsInitialized) {
      applyConfiguredVoiceInternal()
    } else {
      prepareTts()
    }
  }

  fun clearSelectedVoice() {
    store.setSelectedVoiceId(null)
    _uiState.value = _uiState.value.copy(
      selectedVoiceId = null,
      isUsingFallbackVoice = false,
      feedbackMessage = null
    )
    if (isTtsInitialized) {
      applyConfiguredVoiceInternal()
    }
  }

  /**
   * Previews the currently selected (or resolved) voice using the standard Indonesian sample.
   * NEVER writes or changes saved preference in TtsVoiceStore.
   */
  fun previewSelectedVoice(): TtsPreviewOutcome {
    // Stop any previous speech / clear pending multi-turn callbacks so utterances never overlap
    stopSpeaking()

    if (_uiState.value.initStatus == TtsInitStatus.ERROR) {
      _uiState.value = _uiState.value.copy(
        feedbackMessage = "Mesin Text-to-Speech belum siap atau gagal diinisialisasi."
      )
      return TtsPreviewOutcome.ENGINE_UNAVAILABLE
    }

    val utteranceId = "voxtask_tts_preview_${System.currentTimeMillis()}"
    if (!isTtsInitialized) {
      synchronized(pendingSpeakQueue) {
        // Ensure repeated preview taps before init completes never stack in the queue
        pendingSpeakQueue.removeAll { it.isPreview }
        pendingSpeakQueue.add(
          PendingUtterance(
            text = TtsVoiceSelector.PREVIEW_SAMPLE_TEXT,
            utteranceId = utteranceId,
            isPreview = true
          )
        )
      }
      _uiState.value = _uiState.value.copy(
        feedbackMessage = "Menyiapkan suara Text-to-Speech..."
      )
      prepareTts()
      return TtsPreviewOutcome.QUEUED_FOR_INIT
    }

    onSpeechStateChanged(VoiceAssistantState.Speaking(TtsVoiceSelector.PREVIEW_SAMPLE_TEXT))
    return if (speakInternal(TtsVoiceSelector.PREVIEW_SAMPLE_TEXT, queueFlush = true, utteranceId = utteranceId)) {
      TtsPreviewOutcome.SPOKEN
    } else {
      onSpeechStateChanged(VoiceAssistantState.Idle)
      _uiState.value = _uiState.value.copy(
        feedbackMessage = "Tidak dapat memutar pratinjau suara pada perangkat ini."
      )
      TtsPreviewOutcome.FAILED
    }
  }

  fun speak(
    text: String,
    utteranceId: String = "voxtask_tts_${System.currentTimeMillis()}",
    onDone: (() -> Unit)? = null
  ) {
    if (onDone != null) {
      onSpeechDoneCallbacks[utteranceId] = onDone
    }

    if (!isTtsInitialized) {
      synchronized(pendingSpeakQueue) {
        pendingSpeakQueue.add(
          PendingUtterance(
            text = text,
            utteranceId = utteranceId,
            isPreview = false
          )
        )
      }
      prepareTts()
      return
    }

    onSpeechStateChanged(VoiceAssistantState.Speaking(text))
    val spoken = speakInternal(text, queueFlush = true, utteranceId = utteranceId)
    if (!spoken) {
      onSpeechDoneCallbacks.remove(utteranceId)
      onSpeechStateChanged(VoiceAssistantState.Idle)
    }
  }

  fun stopSpeaking() {
    onSpeechDoneCallbacks.clear()
    synchronized(pendingSpeakQueue) {
      pendingSpeakQueue.clear()
    }
    try {
      engine.stop()
    } catch (_: Exception) {}
    onSpeechStateChanged(null) // Signal to reset only if currently Speaking
  }

  fun release() {
    onSpeechDoneCallbacks.clear()
    synchronized(pendingSpeakQueue) {
      pendingSpeakQueue.clear()
    }
    synchronized(pendingReadyCallbacks) {
      pendingReadyCallbacks.clear()
    }
    try {
      engine.stop()
      engine.shutdown()
    } catch (_: Exception) {}
    isTtsInitialized = false
    isTtsInitializing = false
    voiceAppliedOnEngine = false
  }

  private fun refreshVoicesInternal() {
    val rawVoices = try {
      engine.getVoices()
    } catch (e: Exception) {
      logError("Failed to read TTS voices: ${e.message}", e)
      emptyList()
    }
    val sortedVoices = TtsVoiceSelector.extractAvailableVoices(rawVoices)
    val hasIndo = sortedVoices.any { it.isExactIndonesian || it.isIndonesian }
    _uiState.value = _uiState.value.copy(
      initStatus = TtsInitStatus.READY,
      availableVoices = sortedVoices,
      hasIndonesianVoice = hasIndo
    )
  }

  private fun applyConfiguredVoiceInternal() {
    val savedId = store.getSelectedVoiceId()
    val available = _uiState.value.availableVoices

    if (!savedId.isNullOrBlank()) {
      val existsInEngine = available.any { it.id == savedId }
      if (existsInEngine) {
        val applied = try {
          engine.setVoiceById(savedId)
        } catch (e: Exception) {
          logError("Failed to apply voice '$savedId': ${e.message}", e)
          false
        }
        if (applied) {
          voiceAppliedOnEngine = true
          _uiState.value = _uiState.value.copy(
            selectedVoiceId = savedId,
            activeVoiceId = savedId,
            isUsingFallbackVoice = false,
            feedbackMessage = null
          )
          return
        }
      }

      // Selected voice is missing, deleted, or failed on the engine -> safe Android default fallback
      applySystemDefaultWithNotice(
        savedId = savedId,
        notice = "Suara pilihan tidak tersedia di perangkat; menggunakan suara default Android."
      )
      return
    }

    // No explicit user choice saved yet: prioritize Indonesian (id-ID) if available without saving to store
    val prioritizedIndo = TtsVoiceSelector.pickPrioritizedIndonesianVoice(available)
    if (prioritizedIndo != null) {
      val appliedIndo = try {
        engine.setVoiceById(prioritizedIndo.id)
      } catch (_: Exception) {
        false
      }
      if (appliedIndo) {
        voiceAppliedOnEngine = true
        _uiState.value = _uiState.value.copy(
          selectedVoiceId = null,
          activeVoiceId = prioritizedIndo.id,
          isUsingFallbackVoice = false,
          feedbackMessage = null
        )
        return
      }
    }

    applySystemDefaultWithNotice(savedId = null, notice = null)
  }

  private fun applySystemDefaultWithNotice(savedId: String?, notice: String?) {
    try {
      engine.applyDefaultLanguageFallback()
    } catch (e: Exception) {
      logError("Default language fallback failed: ${e.message}", e)
    }
    voiceAppliedOnEngine = true
    val engineVoiceId = try {
      engine.getCurrentOrDefaultVoiceId()
    } catch (_: Exception) {
      null
    }
    _uiState.value = _uiState.value.copy(
      selectedVoiceId = savedId,
      activeVoiceId = engineVoiceId,
      isUsingFallbackVoice = !savedId.isNullOrBlank(),
      feedbackMessage = notice
    )
  }

  private fun flushPendingSpeakQueue() {
    val queued = synchronized(pendingSpeakQueue) {
      pendingSpeakQueue.toList().also { pendingSpeakQueue.clear() }
    }
    if (queued.isEmpty()) return
    var anySpoken = false
    queued.forEachIndexed { index, item ->
      val flush = index == 0
      val spoken = speakInternal(item.text, queueFlush = flush, utteranceId = item.utteranceId)
      if (spoken) {
        anySpoken = true
        if (index == queued.lastIndex) {
          onSpeechStateChanged(VoiceAssistantState.Speaking(item.text))
        }
      } else {
        onSpeechDoneCallbacks.remove(item.utteranceId)
      }
    }
    if (!anySpoken) {
      onSpeechStateChanged(VoiceAssistantState.Idle)
    }
  }

  private fun speakInternal(text: String, queueFlush: Boolean, utteranceId: String): Boolean {
    if (!voiceAppliedOnEngine) {
      applyConfiguredVoiceInternal()
    }
    val initialSuccess = try {
      engine.speak(text, queueFlush = queueFlush, utteranceId = utteranceId)
    } catch (e: Exception) {
      logError("TTS speak error: ${e.message}", e)
      false
    }
    if (initialSuccess) {
      return true
    }

    // If speaking with selected voice failed at engine level, fallback to Android default and retry once
    val savedId = store.getSelectedVoiceId()
    applySystemDefaultWithNotice(
      savedId = savedId,
      notice = if (!savedId.isNullOrBlank()) {
        "Suara pilihan gagal diputar; dialihkan ke suara default Android."
      } else {
        null
      }
    )
    return try {
      engine.speak(text, queueFlush = queueFlush, utteranceId = utteranceId)
    } catch (e: Exception) {
      logError("TTS fallback speak failed: ${e.message}", e)
      false
    }
  }
}
