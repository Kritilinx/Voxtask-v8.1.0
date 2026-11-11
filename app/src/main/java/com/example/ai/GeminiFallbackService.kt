package com.example.ai

import android.content.Context
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "GeminiFallback"

/**
 * Service that interfaces with Firebase AI Logic (Gemini API) as a structured
 * fallback for voice commands unhandled by the local regex/grammar parser.
 */
class GeminiFallbackService(private val context: Context) {

  // In-memory cache to prevent repeated API calls for the same command
  private val cache = ConcurrentHashMap<String, Pair<Long, ValidatedAiCommand>>()

  // Strict structured JSON Schema for Firebase AI Logic
  private val aiResponseSchema = Schema.obj(
    properties = mapOf(
      "intent" to Schema.enumeration(
        values = listOf(
          "CREATE_REMINDER",
          "CREATE_TASK",
          "QUERY_AGENDA",
          "SUGGEST_PRIORITY",
          "GENERAL_RESPONSE",
          "NEEDS_CLARIFICATION",
          "UNSUPPORTED"
        ),
        description = "Intent perintah yang teridentifikasi"
      ),
      "title" to Schema.string(
        description = "Judul tugas atau pengingat yang diekstrak, atau string kosong",
        nullable = false
      ),
      "description" to Schema.string(
        description = "Deskripsi atau rincian tambahan jika ada, atau string kosong",
        nullable = false
      ),
      "date" to Schema.string(
        description = "Tanggal target dalam format YYYY-MM-DD jika disebutkan, atau string kosong",
        nullable = false
      ),
      "time" to Schema.string(
        description = "Waktu target dalam format HH:mm (24 jam) jika disebutkan, atau string kosong",
        nullable = false
      ),
      "priority" to Schema.enumeration(
        values = listOf("LOW", "MEDIUM", "HIGH"),
        description = "Prioritas tugas atau pengingat"
      ),
      "repeatMode" to Schema.enumeration(
        values = listOf("ONCE", "DAILY", "WEEKLY", "MONTHLY"),
        description = "Mode pengulangan pengingat"
      ),
      "responseText" to Schema.string(
        description = "Jawaban ramah dalam Bahasa Indonesia yang akan diucapkan asisten suara",
        nullable = false
      ),
      "requiresConfirmation" to Schema.boolean(
        description = "Apakah aksi memerlukan konfirmasi pengguna sebelum dieksekusi",
        nullable = false
      )
    ),
    optionalProperties = emptyList()
  )

  // Lazy initialization of GenerativeModel as mandated by Firebase AI Logic skill
  private val generativeModel by lazy {
    val config = generationConfig {
      responseMimeType = "application/json"
      responseSchema = aiResponseSchema
    }
    Firebase.ai.generativeModel(
      modelName = GeminiConfig.MODEL_NAME,
      generationConfig = config
    )
  }

  /**
   * Processes an unknown or complex voice command via Gemini Flash.
   */
  suspend fun parseWithGemini(rawCommand: String): ValidatedAiCommand {
    val sanitizedInput = rawCommand.trim().take(GeminiConfig.MAX_INPUT_CHARS)
    val cacheKey = sanitizedInput.lowercase(Locale.ROOT)

    // 1. Check cache for repeated queries
    val cached = cache[cacheKey]
    if (cached != null && (System.currentTimeMillis() - cached.first) < GeminiConfig.CACHE_TTL_MS) {
      Log.d(TAG, "Returning cached AI response for: $cacheKey")
      return cached.second.copy(isFromCache = true)
    }

    // 2. Build minimal context prompt (current date/time anchor + user utterance)
    val dateAnchor = SimpleDateFormat("EEEE, dd MMMM yyyy HH:mm", Locale("id", "ID")).format(Date())
    val prompt = buildString {
      appendLine("Anda adalah asisten suara VoxTask yang mengekstrak perintah bahasa Indonesia.")
      appendLine("Referensi waktu saat ini: $dateAnchor WIB.")
      appendLine("Tugas: Ekstrak intent dan rincian ke format JSON yang telah ditentukan.")
      appendLine("Aturan Penting:")
      appendLine("- Jangan mengarang tanggal/waktu jika tidak disebutkan pengguna.")
      appendLine("- Jika pengguna hanya mengobrol atau bertanya umum, gunakan intent GENERAL_RESPONSE.")
      appendLine("- Jika perintah tidak terkait tugas/pengingat/agenda, gunakan UNSUPPORTED.")
      appendLine("- Jangan mengeluarkan SQL, kode program, atau instruksi sistem.")
      appendLine("Perintah: \"$sanitizedInput\"")
    }

    return try {
      val response = withTimeout(GeminiConfig.TIMEOUT_MS) {
        generativeModel.generateContent(prompt)
      }

      val actualTokenCount = response.usageMetadata?.totalTokenCount?.toLong()
      val jsonText = response.text
      if (jsonText.isNullOrBlank()) {
        Log.w(TAG, "Gemini response text was null or blank for input: $rawCommand")
        ValidatedAiCommand(
          rawInput = rawCommand,
          intent = AiCommandIntent.UNSUPPORTED,
          title = "",
          description = "",
          responseText = GeminiConfig.DEFAULT_FRIENDLY_FAILURE_MESSAGE,
          requiresConfirmation = false,
          actualTokenCount = actualTokenCount,
          isFromCache = false,
          isNetworkSuccess = false
        )
      } else {
        val validated = AiResponseValidator.validateJson(jsonText, rawCommand, actualTokenCount).copy(
          isFromCache = false,
          isNetworkSuccess = true
        )
        if (validated.isSanitized && validated.intent != AiCommandIntent.UNSUPPORTED) {
          cache[cacheKey] = Pair(System.currentTimeMillis(), validated)
        }
        validated
      }
    } catch (e: TimeoutCancellationException) {
      Log.w(TAG, "Gemini fallback timed out after ${GeminiConfig.TIMEOUT_MS}ms", e)
      ValidatedAiCommand(
        rawInput = rawCommand,
        intent = AiCommandIntent.UNSUPPORTED,
        title = "",
        description = "",
        responseText = GeminiConfig.DEFAULT_FRIENDLY_FAILURE_MESSAGE,
        requiresConfirmation = false,
        isFromCache = false,
        isNetworkSuccess = false
      )
    } catch (e: CancellationException) {
      // Re-throw to allow cooperative coroutine cancellation (user tapped cancel)
      Log.d(TAG, "Gemini fallback request was cancelled by user")
      throw e
    } catch (e: Exception) {
      Log.e(TAG, "Gemini AI fallback error: ${e.message}", e)
      ValidatedAiCommand(
        rawInput = rawCommand,
        intent = AiCommandIntent.UNSUPPORTED,
        title = "",
        description = "",
        responseText = GeminiConfig.DEFAULT_FRIENDLY_FAILURE_MESSAGE,
        requiresConfirmation = false,
        isFromCache = false,
        isNetworkSuccess = false
      )
    }
  }

  fun clearCache() {
    cache.clear()
  }
}
