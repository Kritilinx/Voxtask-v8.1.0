package com.example.voice

sealed interface VoiceAssistantState {
  data object Idle : VoiceAssistantState
  data object Listening : VoiceAssistantState
  data class Processing(val recognizedSpeech: String = "") : VoiceAssistantState
  data class Speaking(val spokenText: String = "") : VoiceAssistantState
  data class WaitingForAnswer(val prompt: String = "") : VoiceAssistantState
  data class AwaitingConfirmation(val actionDescription: String = "") : VoiceAssistantState
  data class Error(val errorMessage: String) : VoiceAssistantState
  data object PermissionRequired : VoiceAssistantState
}
