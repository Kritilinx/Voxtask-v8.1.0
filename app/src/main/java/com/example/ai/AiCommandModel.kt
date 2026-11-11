package com.example.ai

import com.example.data.model.Priority
import com.example.data.model.RepeatMode

/**
 * Strict intents supported by Gemini AI Command Fallback.
 */
enum class AiCommandIntent {
  CREATE_REMINDER,
  CREATE_TASK,
  QUERY_AGENDA,
  SUGGEST_PRIORITY,
  GENERAL_RESPONSE,
  NEEDS_CLARIFICATION,
  UNSUPPORTED
}

/**
 * Raw JSON container returned by Gemini schema extraction.
 */
data class RawAiResponse(
  val intent: String,
  val title: String,
  val description: String,
  val date: String,
  val time: String,
  val priority: String,
  val repeatMode: String,
  val responseText: String,
  val requiresConfirmation: Boolean
)

/**
 * Validated and sanitized AI command ready for execution by ViewModel and Repository.
 */
data class ValidatedAiCommand(
  val rawInput: String,
  val intent: AiCommandIntent,
  val title: String,
  val description: String,
  val targetEpochMillis: Long? = null,
  val priority: Priority = Priority.MEDIUM,
  val repeatMode: RepeatMode = RepeatMode.ONCE,
  val responseText: String,
  val requiresConfirmation: Boolean = false,
  val isSanitized: Boolean = true,
  val actualTokenCount: Long? = null,
  val isFromCache: Boolean = false,
  val isNetworkSuccess: Boolean = true
)
