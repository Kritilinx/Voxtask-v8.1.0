package com.example.ai

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class AiUsageStats(
  val isEnabled: Boolean,
  val dailyCount: Int,
  val dailyLimit: Int,
  val remainingRequests: Int,
  val totalRequests: Int,
  val estimatedTotalTokens: Long,
  val actualTotalTokens: Long = 0L
)

/**
 * Tracks AI fallback call frequency, daily quota enforcement, and token estimation.
 * Persists locally via SharedPreferences with automatic day boundary reset.
 * Distinguishes between actual token usage (reported by API usageMetadata) and local estimation.
 */
class AiUsageTracker(context: Context) {

  private val prefs: SharedPreferences =
    context.getSharedPreferences("vox_ai_preferences", Context.MODE_PRIVATE)

  private val dateFormat = SimpleDateFormat("yyyyMMdd", Locale.US)

  private val todayKey: String
    get() = dateFormat.format(Date())

  fun isEnabled(): Boolean {
    return prefs.getBoolean(KEY_AI_ENABLED, true)
  }

  fun setEnabled(enabled: Boolean) {
    prefs.edit().putBoolean(KEY_AI_ENABLED, enabled).apply()
  }

  fun getDailyLimit(): Int {
    return prefs.getInt(KEY_DAILY_LIMIT, GeminiConfig.DEFAULT_DAILY_LIMIT)
  }

  fun setDailyLimit(limit: Int) {
    val sanitized = limit.coerceIn(1, 100)
    prefs.edit().putInt(KEY_DAILY_LIMIT, sanitized).apply()
  }

  /**
   * Returns current count of requests made today, resetting if calendar date shifted.
   */
  fun getDailyCount(): Int {
    val recordedDate = prefs.getString(KEY_LAST_DATE, "")
    val today = todayKey
    return if (recordedDate == today) {
      prefs.getInt(KEY_DAILY_COUNT, 0)
    } else {
      0
    }
  }

  /**
   * Check whether a new AI request is permitted under user settings and daily quota.
   */
  fun canMakeRequest(): Boolean {
    if (!isEnabled()) return false
    return getDailyCount() < getDailyLimit()
  }

  /**
   * Record a successful or attempted AI request.
   * Tracks both actual tokens (if reported by service metadata) and estimated tokens.
   */
  fun recordRequest(actualTokens: Long? = null, estimatedTokens: Long = 150L) {
    val today = todayKey
    val recordedDate = prefs.getString(KEY_LAST_DATE, "")

    val currentDailyCount = if (recordedDate == today) {
      prefs.getInt(KEY_DAILY_COUNT, 0)
    } else {
      0
    }

    val totalReq = prefs.getInt(KEY_TOTAL_REQUESTS, 0) + 1
    val totalEstTokens = prefs.getLong(KEY_TOTAL_ESTIMATED_TOKENS, 0L) + estimatedTokens
    val totalActTokens = prefs.getLong(KEY_TOTAL_ACTUAL_TOKENS, 0L) + (actualTokens ?: 0L)

    prefs.edit()
      .putString(KEY_LAST_DATE, today)
      .putInt(KEY_DAILY_COUNT, currentDailyCount + 1)
      .putInt(KEY_TOTAL_REQUESTS, totalReq)
      .putLong(KEY_TOTAL_ESTIMATED_TOKENS, totalEstTokens)
      .putLong(KEY_TOTAL_ACTUAL_TOKENS, totalActTokens)
      .apply()
  }

  /**
   * Reset daily counter manually (from Settings).
   */
  fun resetDailyCount() {
    val today = todayKey
    prefs.edit()
      .putString(KEY_LAST_DATE, today)
      .putInt(KEY_DAILY_COUNT, 0)
      .apply()
  }

  fun getStats(): AiUsageStats {
    val count = getDailyCount()
    val limit = getDailyLimit()
    val remaining = (limit - count).coerceAtLeast(0)
    return AiUsageStats(
      isEnabled = isEnabled(),
      dailyCount = count,
      dailyLimit = limit,
      remainingRequests = remaining,
      totalRequests = prefs.getInt(KEY_TOTAL_REQUESTS, 0),
      estimatedTotalTokens = prefs.getLong(KEY_TOTAL_ESTIMATED_TOKENS, 0L),
      actualTotalTokens = prefs.getLong(KEY_TOTAL_ACTUAL_TOKENS, 0L)
    )
  }

  companion object {
    private const val KEY_AI_ENABLED = "key_ai_enabled"
    private const val KEY_DAILY_LIMIT = "key_daily_limit"
    private const val KEY_LAST_DATE = "key_last_date"
    private const val KEY_DAILY_COUNT = "key_daily_count"
    private const val KEY_TOTAL_REQUESTS = "key_total_requests"
    private const val KEY_TOTAL_ESTIMATED_TOKENS = "key_total_tokens"
    private const val KEY_TOTAL_ACTUAL_TOKENS = "key_total_actual_tokens"
  }
}
