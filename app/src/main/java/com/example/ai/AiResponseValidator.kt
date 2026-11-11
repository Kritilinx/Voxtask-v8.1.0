package com.example.ai

import android.util.Log
import com.example.data.model.Priority
import com.example.data.model.RepeatMode
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Strict validator and sanitizer for AI responses.
 * Enforces zero-trust validation: blocks SQL patterns, validates date/time ranges,
 * and ensures safe mapping into internal models.
 */
object AiResponseValidator {

  private val SQL_CODE_PATTERNS = listOf(
    "SELECT ", "INSERT ", "UPDATE ", "DELETE ", "DROP ", "ALTER ", "TRUNCATE ",
    "CREATE TABLE", "FROM ", "WHERE ", "--", "/*", "*/", ";", "EXEC(", "<SCRIPT",
    "JAVASCRIPT:", "EVAL(", "SYSTEM(", "ROOM_MASTER_TABLE"
  )

  /**
   * Parses and validates raw JSON string from Gemini into ValidatedAiCommand.
   */
  fun validateJson(
    rawJson: String,
    originalInput: String,
    actualTokenCount: Long? = null
  ): ValidatedAiCommand {
    val trimmedJson = rawJson.trim()
    if (!trimmedJson.startsWith("{") || !trimmedJson.endsWith("}")) {
      Log.w("AiResponseValidator", "Invalid JSON format received from Gemini: $rawJson")
      return ValidatedAiCommand(
        rawInput = originalInput,
        intent = AiCommandIntent.UNSUPPORTED,
        title = "",
        description = "",
        responseText = GeminiConfig.DEFAULT_FRIENDLY_FAILURE_MESSAGE,
        requiresConfirmation = false,
        isSanitized = false,
        actualTokenCount = actualTokenCount
      )
    }

    return try {
      val fields = parseJsonFields(trimmedJson)

      val rawIntent = (fields["intent"] ?: "UNSUPPORTED").trim().uppercase(Locale.US)
      val rawTitle = sanitizeText(fields["title"] ?: "")
      val rawDesc = sanitizeText(fields["description"] ?: "")
      val rawDate = (fields["date"] ?: "").trim()
      val rawTime = (fields["time"] ?: "").trim()
      val rawPriority = (fields["priority"] ?: "MEDIUM").trim().uppercase(Locale.US)
      val rawRepeat = (fields["repeatMode"] ?: "ONCE").trim().uppercase(Locale.US)
      val rawResponse = sanitizeText(fields["responseText"] ?: "")
      val rawConfirmation = fields["requiresConfirmation"]?.toBoolean() ?: false

      // Guard against code/SQL injection in any text fields
      if (containsInjectionPatterns(rawTitle) || containsInjectionPatterns(rawDesc) || containsInjectionPatterns(rawResponse)) {
        Log.w("AiResponseValidator", "Blocked potential injection pattern in AI response: title=$rawTitle, desc=$rawDesc, resp=$rawResponse")
        return ValidatedAiCommand(
          rawInput = originalInput,
          intent = AiCommandIntent.UNSUPPORTED,
          title = "",
          description = "",
          responseText = GeminiConfig.DEFAULT_FRIENDLY_FAILURE_MESSAGE,
          requiresConfirmation = false,
          isSanitized = false
        )
      }

      val intent = try {
        AiCommandIntent.valueOf(rawIntent)
      } catch (e: IllegalArgumentException) {
        AiCommandIntent.UNSUPPORTED
      }

      val priority = when (rawPriority) {
        "HIGH" -> Priority.HIGH
        "LOW" -> Priority.LOW
        else -> Priority.MEDIUM
      }

      val repeatMode = when (rawRepeat) {
        "DAILY" -> RepeatMode.DAILY
        "WEEKLY" -> RepeatMode.WEEKLY
        "MONTHLY" -> RepeatMode.MONTHLY
        else -> RepeatMode.ONCE
      }

      val targetEpochMillis = parseDateTime(rawDate, rawTime)

      // Safety check for empty titles when creating tasks or reminders
      if ((intent == AiCommandIntent.CREATE_REMINDER || intent == AiCommandIntent.CREATE_TASK) && rawTitle.isBlank()) {
        return ValidatedAiCommand(
          rawInput = originalInput,
          intent = AiCommandIntent.NEEDS_CLARIFICATION,
          title = "",
          description = rawDesc,
          responseText = "Judul untuk ${if (intent == AiCommandIntent.CREATE_REMINDER) "pengingat" else "tugas"} belum jelas. Apa judul yang Anda inginkan?",
          requiresConfirmation = false
        )
      }

      val priorityLabel = when (priority) {
        Priority.HIGH -> "Tinggi"
        Priority.LOW -> "Rendah"
        Priority.MEDIUM -> "Sedang"
      }

      val friendlyResponse = if (rawResponse.isNotBlank()) {
        rawResponse
      } else {
        when (intent) {
          AiCommandIntent.CREATE_REMINDER -> "Siap, saya akan jadwalkan pengingat '$rawTitle'."
          AiCommandIntent.CREATE_TASK -> "Siap, saya tambahkan tugas '$rawTitle'."
          AiCommandIntent.QUERY_AGENDA -> "Berikut informasi agenda Anda."
          AiCommandIntent.SUGGEST_PRIORITY -> "Saran prioritas untuk tugas '$rawTitle' adalah $priorityLabel."
          AiCommandIntent.GENERAL_RESPONSE -> "Ada yang bisa saya bantu terkait tugas dan pengingat Anda?"
          AiCommandIntent.NEEDS_CLARIFICATION -> "Mohon lengkapi detail perintah Anda."
          AiCommandIntent.UNSUPPORTED -> GeminiConfig.DEFAULT_FRIENDLY_FAILURE_MESSAGE
        }
      }

      ValidatedAiCommand(
        rawInput = originalInput,
        intent = intent,
        title = rawTitle,
        description = rawDesc,
        targetEpochMillis = targetEpochMillis,
        priority = priority,
        repeatMode = repeatMode,
        responseText = friendlyResponse,
        requiresConfirmation = rawConfirmation || (intent == AiCommandIntent.CREATE_REMINDER && targetEpochMillis == null),
        actualTokenCount = actualTokenCount
      )
    } catch (e: Exception) {
      Log.e("AiResponseValidator", "Failed to parse/validate Gemini response: ${e.message}", e)
      ValidatedAiCommand(
        rawInput = originalInput,
        intent = AiCommandIntent.UNSUPPORTED,
        title = "",
        description = "",
        responseText = GeminiConfig.DEFAULT_FRIENDLY_FAILURE_MESSAGE,
        requiresConfirmation = false,
        isSanitized = false
      )
    }
  }

  private fun sanitizeText(input: String): String {
    return input.trim()
      .replace(Regex("[\\p{Cntrl}&&[^\r\n\t]]"), "") // Remove control chars
      .take(200) // Prevent absurdly long strings
  }

  fun containsInjectionPatterns(text: String): Boolean {
    val upper = text.uppercase(Locale.US)
    return SQL_CODE_PATTERNS.any { pattern -> upper.contains(pattern) }
  }

  /**
   * Strictly parses date (YYYY-MM-DD) and time (HH:mm) into epoch millis with sanity checks.
   */
  fun parseDateTime(dateStr: String, timeStr: String): Long? {
    if (dateStr.isBlank() && timeStr.isBlank()) return null

    val cal = Calendar.getInstance()

    if (dateStr.isNotBlank()) {
      val dateParts = dateStr.split("-")
      if (dateParts.size == 3) {
        val y = dateParts[0].toIntOrNull() ?: return null
        val m = dateParts[1].toIntOrNull() ?: return null
        val d = dateParts[2].toIntOrNull() ?: return null

        if (y < 2024 || y > 2040 || m !in 1..12 || d !in 1..31) return null

        cal.set(Calendar.YEAR, y)
        cal.set(Calendar.MONTH, m - 1)
        cal.set(Calendar.DAY_OF_MONTH, d)
      } else {
        return null
      }
    }

    if (timeStr.isNotBlank()) {
      val timeParts = timeStr.split(":")
      if (timeParts.size == 2) {
        val h = timeParts[0].toIntOrNull() ?: return null
        val min = timeParts[1].toIntOrNull() ?: return null

        if (h !in 0..23 || min !in 0..59) return null

        cal.set(Calendar.HOUR_OF_DAY, h)
        cal.set(Calendar.MINUTE, min)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
      } else {
        return null
      }
    } else {
      // Default to 09:00 if only date specified
      cal.set(Calendar.HOUR_OF_DAY, 9)
      cal.set(Calendar.MINUTE, 0)
      cal.set(Calendar.SECOND, 0)
      cal.set(Calendar.MILLISECOND, 0)
    }

    // If only time was provided and it already passed today, schedule for tomorrow
    if (dateStr.isBlank() && timeStr.isNotBlank() && cal.timeInMillis <= System.currentTimeMillis()) {
      cal.add(Calendar.DAY_OF_YEAR, 1)
    }

    return cal.timeInMillis
  }

  fun parseJsonFields(jsonString: String): Map<String, String> {
    val map = mutableMapOf<String, String>()
    val regex = Regex("\"([a-zA-Z0-9_]+)\"\\s*:\\s*(?:\"((?:\\\\\"|[^\"])*)\"|([a-zA-Z0-9_.-]+))")
    for (match in regex.findAll(jsonString)) {
      val key = match.groupValues[1]
      val strVal = match.groupValues[2]
      val primVal = match.groupValues[3]
      val value = if (match.value.contains("\"\"")) "" else if (strVal.isNotEmpty()) strVal else primVal
      map[key] = value.replace("\\\"", "\"").replace("\\n", "\n")
    }
    return map
  }
}
