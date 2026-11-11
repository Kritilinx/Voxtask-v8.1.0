package com.example.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import java.util.Locale

class AndroidTextToSpeechAdapter(private val context: Context) : TtsEnginePort {

  private var textToSpeech: TextToSpeech? = null

  override fun initialize(
    listener: TtsEngineListener,
    onInitResult: (success: Boolean) -> Unit
  ) {
    try {
      textToSpeech?.stop()
      textToSpeech?.shutdown()
    } catch (_: Exception) {}

    textToSpeech = TextToSpeech(context.applicationContext) { status ->
      if (status == TextToSpeech.SUCCESS) {
        textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
          override fun onStart(utteranceId: String?) {
            listener.onUtteranceStart(utteranceId)
          }

          override fun onDone(utteranceId: String?) {
            listener.onUtteranceDone(utteranceId)
          }

          @Deprecated("Deprecated in Java")
          override fun onError(utteranceId: String?) {
            listener.onUtteranceError(utteranceId)
          }
        })
        onInitResult(true)
      } else {
        onInitResult(false)
      }
    }
  }

  override fun getVoices(): List<TtsEngineVoice> {
    val tts = textToSpeech ?: return emptyList()
    val rawVoices = try {
      tts.voices ?: emptySet()
    } catch (_: Exception) {
      emptySet()
    }
    return rawVoices.mapNotNull { voice ->
      val name = voice?.name ?: return@mapNotNull null
      val locale = voice.locale ?: return@mapNotNull null
      TtsEngineVoice(
        id = name,
        locale = locale,
        isNetworkConnectionRequired = voice.isNetworkConnectionRequired,
        isNotInstalled = isVoiceNotInstalled(voice),
        quality = voice.quality
      )
    }
  }

  override fun getCurrentOrDefaultVoiceId(): String? {
    val tts = textToSpeech ?: return null
    return try {
      tts.voice?.name ?: tts.defaultVoice?.name
    } catch (_: Exception) {
      null
    }
  }

  override fun setVoiceById(voiceId: String): Boolean {
    val tts = textToSpeech ?: return false
    val targetVoice = try {
      tts.voices?.firstOrNull { voice ->
        voice != null && voice.name == voiceId && !isVoiceNotInstalled(voice)
      }
    } catch (_: Exception) {
      null
    } ?: return false

    return try {
      tts.setVoice(targetVoice) == TextToSpeech.SUCCESS
    } catch (_: Exception) {
      false
    }
  }

  override fun applyDefaultLanguageFallback(): Boolean {
    val tts = textToSpeech ?: return false
    return try {
      val localeId = Locale.forLanguageTag("id-ID")
      val idResult = tts.setLanguage(localeId)
      if (idResult != TextToSpeech.LANG_MISSING_DATA && idResult != TextToSpeech.LANG_NOT_SUPPORTED) {
        return true
      }
      val defaultResult = tts.setLanguage(Locale.getDefault())
      if (defaultResult != TextToSpeech.LANG_MISSING_DATA && defaultResult != TextToSpeech.LANG_NOT_SUPPORTED) {
        return true
      }
      val defaultVoice = tts.defaultVoice
      defaultVoice != null && tts.setVoice(defaultVoice) == TextToSpeech.SUCCESS
    } catch (_: Exception) {
      false
    }
  }

  override fun speak(text: String, queueFlush: Boolean, utteranceId: String): Boolean {
    val tts = textToSpeech ?: return false
    val queueMode = if (queueFlush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
    return try {
      tts.speak(text, queueMode, null, utteranceId) == TextToSpeech.SUCCESS
    } catch (_: Exception) {
      false
    }
  }

  override fun stop() {
    try {
      textToSpeech?.stop()
    } catch (_: Exception) {}
  }

  override fun shutdown() {
    try {
      textToSpeech?.stop()
      textToSpeech?.shutdown()
    } catch (_: Exception) {}
    textToSpeech = null
  }

  private fun isVoiceNotInstalled(voice: Voice): Boolean {
    val features = voice.features ?: return false
    return features.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)
  }
}
