package com.example.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class VoiceAssistantManager internal constructor(
  private val context: Context,
  voiceStore: TtsVoiceStore,
  ttsEngine: TtsEnginePort,
  private val runOnMainThread: (() -> Unit) -> Unit
) {

  constructor(context: Context) : this(
    context = context,
    voiceStore = SharedPreferencesTtsVoiceStore(context),
    ttsEngine = AndroidTextToSpeechAdapter(context),
    runOnMainThread = { action -> Handler(Looper.getMainLooper()).post(action) }
  )

  private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

  private val _assistantState = MutableStateFlow<VoiceAssistantState>(VoiceAssistantState.Idle)
  val assistantState: StateFlow<VoiceAssistantState> = _assistantState.asStateFlow()

  private val _lastRecognizedText = MutableStateFlow("")
  val lastRecognizedText: StateFlow<String> = _lastRecognizedText.asStateFlow()

  private var speechRecognizer: SpeechRecognizer? = null

  private val ttsCoordinator = TtsVoiceCoordinator(
    store = voiceStore,
    engine = ttsEngine,
    onSpeechStateChanged = { newState ->
      when (newState) {
        null -> {
          if (_assistantState.value is VoiceAssistantState.Speaking) {
            _assistantState.value = VoiceAssistantState.Idle
          }
        }
        is VoiceAssistantState.Idle -> {
          if (_assistantState.value is VoiceAssistantState.Speaking) {
            _assistantState.value = VoiceAssistantState.Idle
          }
        }
        else -> {
          _assistantState.value = newState
        }
      }
    },
    runOnMainThread = runOnMainThread,
    logError = { msg, err -> Log.e("VoiceAssistant", msg, err) }
  )

  val ttsVoiceState: StateFlow<TtsVoiceUiState> = ttsCoordinator.uiState

  fun isSpeechRecognitionAvailable(): Boolean {
    return SpeechRecognizer.isRecognitionAvailable(context)
  }

  fun prepareTts(onReady: () -> Unit = {}) {
    ttsCoordinator.prepareTts(onReady)
  }

  fun refreshTtsVoices() {
    ttsCoordinator.refreshVoices()
  }

  fun selectAiVoice(voiceId: String) {
    ttsCoordinator.selectVoice(voiceId)
  }

  fun clearSelectedAiVoice() {
    ttsCoordinator.clearSelectedVoice()
  }

  fun previewAiVoice(): TtsPreviewOutcome {
    runOnMainThread {
      try {
        speechRecognizer?.cancel()
      } catch (_: Exception) {}
    }
    return ttsCoordinator.previewSelectedVoice()
  }

  fun startListening(
    onResult: (String) -> Unit = {},
    onError: (String) -> Unit = {}
  ) {
    if (ContextCompat.checkSelfPermission(context, "android.permission.RECORD_AUDIO") != 0) {
      _assistantState.value = VoiceAssistantState.PermissionRequired
      onError("Microphone permission required")
      return
    }

    if (!isSpeechRecognitionAvailable()) {
      _assistantState.value = VoiceAssistantState.Error("Speech recognition not available on device")
      onError("Speech recognition not available")
      return
    }

    try {
      ttsCoordinator.stopSpeaking()
    } catch (_: Exception) {}

    mainHandler.post {
      try {
        if (speechRecognizer == null) {
          speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context.applicationContext).apply {
            setRecognitionListener(object : RecognitionListener {
              override fun onReadyForSpeech(params: Bundle?) {
                _assistantState.value = VoiceAssistantState.Listening
              }

              override fun onBeginningOfSpeech() {}
              override fun onRmsChanged(rmsdB: Float) {}
              override fun onBufferReceived(buffer: ByteArray?) {}

              override fun onEndOfSpeech() {
                _assistantState.value = VoiceAssistantState.Processing("")
              }

              override fun onError(error: Int) {
                val errorMsg = mapErrorCodeToMessage(error)
                _assistantState.value = VoiceAssistantState.Error(errorMsg)
                onError(errorMsg)
              }

              override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val recognized = matches?.firstOrNull() ?: ""
                _lastRecognizedText.value = recognized
                _assistantState.value = VoiceAssistantState.Processing(recognized)
                onResult(recognized)
              }

              override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val partial = matches?.firstOrNull() ?: ""
                if (partial.isNotBlank()) {
                  _lastRecognizedText.value = partial
                }
              }

              override fun onEvent(eventType: Int, params: Bundle?) {}
            })
          }
        }

        speechRecognizer?.cancel()

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
          putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
          putExtra(RecognizerIntent.EXTRA_LANGUAGE, "id-ID")
          putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "id-ID")
          putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf("id-ID", "in-ID"))
          putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
          putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }

        _assistantState.value = VoiceAssistantState.Listening
        speechRecognizer?.startListening(intent)
      } catch (e: Exception) {
        val errorMsg = e.message ?: "Gagal memulai pengenalan suara"
        _assistantState.value = VoiceAssistantState.Error(errorMsg)
        onError(errorMsg)
      }
    }
  }

  fun stopListening() {
    mainHandler.post {
      try {
        speechRecognizer?.stopListening()
        if (_assistantState.value is VoiceAssistantState.Listening) {
          _assistantState.value = VoiceAssistantState.Idle
        }
      } catch (_: Exception) {
        _assistantState.value = VoiceAssistantState.Idle
      }
    }
  }

  fun speak(
    text: String,
    utteranceId: String = "voxtask_tts_${System.currentTimeMillis()}",
    onDone: (() -> Unit)? = null
  ) {
    runOnMainThread {
      try {
        speechRecognizer?.cancel()
      } catch (_: Exception) {}
    }
    ttsCoordinator.speak(text = text, utteranceId = utteranceId, onDone = onDone)
  }

  fun stopSpeaking() {
    ttsCoordinator.stopSpeaking()
  }

  fun setState(state: VoiceAssistantState) {
    _assistantState.value = state
  }

  fun cancelAll() {
    ttsCoordinator.stopSpeaking()
    mainHandler.post {
      try {
        speechRecognizer?.cancel()
      } catch (_: Exception) {}
      _assistantState.value = VoiceAssistantState.Idle
    }
  }

  fun resetState() {
    _assistantState.value = VoiceAssistantState.Idle
  }

  fun release() {
    mainHandler.post {
      try {
        speechRecognizer?.destroy()
      } catch (_: Exception) {}
      speechRecognizer = null
    }
    ttsCoordinator.release()
  }

  private fun mapErrorCodeToMessage(error: Int): String {
    return when (error) {
      SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Waktu koneksi habis"
      SpeechRecognizer.ERROR_NETWORK -> "Koneksi pengenalan suara terputus"
      SpeechRecognizer.ERROR_AUDIO -> "Gangguan perekaman audio"
      SpeechRecognizer.ERROR_SERVER -> "Layanan pengenalan suara tidak merespons"
      SpeechRecognizer.ERROR_CLIENT -> "Kesalahan sistem aplikasi"
      SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Tidak ada ucapan terdengar"
      SpeechRecognizer.ERROR_NO_MATCH -> "Tidak ada suara yang dikenali"
      SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Layanan suara sedang sibuk"
      SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Izin mikrofon diperlukan"
      else -> "Gagal mengenali suara ($error)"
    }
  }
}
