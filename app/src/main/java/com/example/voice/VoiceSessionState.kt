package com.example.voice

import com.example.data.model.Priority
import com.example.data.model.RepeatMode

enum class DisambiguationAction {
  DELETE,
  COMPLETE,
  EDIT
}

sealed interface MultiTurnContext {
  // 1. Missing time when creating a reminder
  data class CreatingReminderMissingTime(
    val title: String,
    val priority: Priority = Priority.MEDIUM,
    val category: String = "Voice",
    val repeatMode: RepeatMode = RepeatMode.ONCE,
    val targetDateMillis: Long? = null
  ) : MultiTurnContext

  // 2. Missing title when creating a reminder
  data class CreatingReminderMissingTitle(
    val dueDateTime: Long,
    val priority: Priority = Priority.MEDIUM,
    val category: String = "Voice",
    val repeatMode: RepeatMode = RepeatMode.ONCE
  ) : MultiTurnContext

  // 4. Missing new time when editing a reminder
  data class EditingReminderMissingTime(
    val reminderId: Long,
    val title: String
  ) : MultiTurnContext

  // 5. Disambiguation between multiple matching reminders
  data class DisambiguatingReminder(
    val candidateIds: List<Long>,
    val candidateTitles: List<String>,
    val actionType: DisambiguationAction
  ) : MultiTurnContext
}

sealed interface PendingVoiceAction {
  data class DeleteReminder(val reminderId: Long, val title: String) : PendingVoiceAction
  data class EditReminderTime(
    val reminderId: Long,
    val title: String,
    val newTimestamp: Long,
    val newRepeatMode: RepeatMode
  ) : PendingVoiceAction
  data class CreateAiReminder(
    val title: String,
    val dueDateTime: Long,
    val priority: Priority = Priority.MEDIUM,
    val category: String = "AI",
    val repeatMode: RepeatMode = RepeatMode.ONCE
  ) : PendingVoiceAction
}

data class VoiceSession(
  val transcription: String = "",
  val assistantResponse: String = "",
  val pendingAction: PendingVoiceAction? = null,
  val multiTurnContext: MultiTurnContext? = null,
  val candidateOptions: List<String> = emptyList(),
  val isClarification: Boolean = false,
  val isAiResponse: Boolean = false,
  val parserDebugTrace: com.example.voice.parser.ParserDebugTrace? = null,
  val timestamp: Long = System.currentTimeMillis()
)

