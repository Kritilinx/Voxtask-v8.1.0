package com.example.ai

/**
 * Centralized configuration for Gemini AI Fallback integration.
 * Manages model selection, timeouts, input boundaries, and usage defaults.
 */
object GeminiConfig {
  /**
   * Cost-effective frontier-class Gemini Flash model for structured voice command extraction.
   * Centralized here so model updates can be made in a single place.
   */
  const val MODEL_NAME: String = "gemini-3.8-flash"

  /**
   * Realistic timeout for model inference coroutines (30 seconds).
   */
  const val TIMEOUT_MS: Long = 30_000L

  /**
   * Maximum characters sent from voice transcription to avoid unnecessary token usage.
   */
  const val MAX_INPUT_CHARS: Int = 300

  /**
   * Default daily AI request quota limit.
   */
  const val DEFAULT_DAILY_LIMIT: Int = 20

  /**
   * Cache expiration duration (5 minutes) to avoid repeated Gemini calls for identical commands.
   */
  const val CACHE_TTL_MS: Long = 5 * 60 * 1000L

  /**
   * Friendly failure message shown in UI and spoken by TTS when AI fallback fails or cannot process.
   * "Perintah tidak dikenal, apakah Anda bisa mengulang kembali?"
   */
  const val DEFAULT_FRIENDLY_FAILURE_MESSAGE: String =
    "Perintah tidak dikenal, apakah Anda bisa mengulang kembali?"
}
