package com.example.voice.parser

import com.example.data.model.Priority
import com.example.data.model.RepeatMode

enum class CommandIntent {
  CREATE_REMINDER,
  EDIT_REMINDER,
  DELETE_REMINDER,
  COMPLETE_REMINDER,
  VIEW_TODAY_AGENDA,
  VIEW_NEXT_REMINDER,
  CONFIRM_YES,
  CONFIRM_NO,
  CLARIFICATION,
  UNKNOWN
}

data class ParsedCommand(
  val rawText: String,
  val intent: CommandIntent,
  val targetQuery: String = "",
  val newTitle: String = "",
  val dueDateTime: Long? = null,
  val repeatMode: RepeatMode = RepeatMode.ONCE,
  val priority: Priority = Priority.MEDIUM,
  val category: String = "Voice",
  val clarificationQuestion: String? = null,
  val hasExplicitDate: Boolean = false,
  val hasExplicitTime: Boolean = false,
  val targetDateMillis: Long? = null,
  val missingField: MissingField = MissingField.NONE,
  val debugTrace: ParserDebugTrace? = null
)

object LocalCommandParser {

  /**
   * Parses natural Indonesian voice input into structured command intents and parameters.
   */
  fun parse(
    rawInput: String,
    baseTimeMillis: Long = System.currentTimeMillis(),
    timeZone: java.util.TimeZone = java.util.TimeZone.getDefault()
  ): ParsedCommand {
    val trimmed = rawInput.trim()
    if (trimmed.isBlank()) {
      return ParsedCommand(
        rawText = rawInput,
        intent = CommandIntent.UNKNOWN,
        clarificationQuestion = "Suara tidak terdengar. Silakan ulangi perintah Anda."
      )
    }

    val lower = trimmed.lowercase()

    // 1. Confirmations (Yes / No / Cancel)
    if (isYes(lower)) {
      return ParsedCommand(rawText = trimmed, intent = CommandIntent.CONFIRM_YES)
    }
    if (isNo(lower) || isCancellation(lower)) {
      return ParsedCommand(rawText = trimmed, intent = CommandIntent.CONFIRM_NO)
    }

    // 2. Query: Agenda Hari Ini (Today's Agenda)
    if (matchesTodayAgenda(lower)) {
      return ParsedCommand(rawText = trimmed, intent = CommandIntent.VIEW_TODAY_AGENDA)
    }

    // 3. Query: Pengingat Berikutnya (Next Upcoming Reminder)
    if (matchesNextReminder(lower)) {
      return ParsedCommand(rawText = trimmed, intent = CommandIntent.VIEW_NEXT_REMINDER)
    }

    // 4. Complete Reminder or Task
    if (matchesComplete(lower)) {
      return parseCompleteCommand(trimmed, lower)
    }

    // 5. Delete Reminder or Task
    if (matchesDelete(lower)) {
      return parseDeleteCommand(trimmed, lower)
    }

    // 6. Edit Reminder
    if (matchesEdit(lower)) {
      return parseEditCommand(trimmed, lower, baseTimeMillis, timeZone)
    }

    // 7. Create Reminder
    if (matchesCreateReminder(lower)) {
      return parseCreateReminderCommand(trimmed, baseTimeMillis, timeZone)
    }

    // 8. If text has clear time expressions (e.g. "rapat jam 2 siang", "beli susu besok") assume Reminder
    if (hasTemporalMarkers(lower)) {
      return parseCreateReminderCommand(trimmed, baseTimeMillis, timeZone)
    }

    // 9. Clarification if too short
    if (trimmed.split("\\s+".toRegex()).size < 2) {
      val trace = ParserDebugTrace(
        originalText = trimmed,
        normalizedText = lower,
        intent = CommandIntent.CLARIFICATION,
        activityTitle = "",
        finalDecision = ParserFinalDecision.NEEDS_CLARIFICATION,
        isPendingClarification = true,
        rejectionReason = "Perintah terlalu singkat, membutuhkan klarifikasi"
      )
      return ParsedCommand(
        rawText = trimmed,
        intent = CommandIntent.CLARIFICATION,
        clarificationQuestion = "Perintah '$trimmed' belum lengkap. Anda ingin membuat tugas, pengingat, atau melihat agenda?",
        debugTrace = trace
      )
    }

    // 12. Fallback: Unrecognized or complex command -> UNKNOWN for Gemini AI Fallback
    val unknownTrace = ParserDebugTrace(
      originalText = trimmed,
      normalizedText = lower,
      intent = CommandIntent.UNKNOWN,
      activityTitle = "",
      finalDecision = ParserFinalDecision.REJECTED,
      isPendingClarification = false,
      rejectionReason = "Perintah lokal tidak dikenali, dialihkan ke asisten AI"
    )
    return ParsedCommand(
      rawText = trimmed,
      intent = CommandIntent.UNKNOWN,
      clarificationQuestion = "Perintah tidak dikenali. Mencoba memproses dengan asisten AI...",
      debugTrace = unknownTrace
    )
  }

  fun isYes(input: String): Boolean {
    val lower = input.lowercase().trim()
    val tokens = listOf("ya", "iya", "benar", "setuju", "lanjutkan", "oke", "ok", "yes", "konfirmasi", "lakukan", "siap", "betul", "boleh")
    return lower in tokens || lower.startsWith("ya ") || lower.startsWith("iya ") || lower.startsWith("oke ")
  }

  fun isNo(input: String): Boolean {
    val lower = input.lowercase().trim()
    val tokens = listOf("tidak", "batal", "jangan", "batalkan", "bukan", "cancel", "no", "nggak", "enggak", "stop", "hentikan", "gak usah", "ga usah", "kembali")
    return lower in tokens || lower.startsWith("tidak ") || lower.startsWith("jangan ") || lower.startsWith("batal ")
  }

  fun isCancellation(input: String): Boolean {
    val lower = input.lowercase().trim()
    return lower in listOf("batal", "batalkan", "cancel", "stop", "hentikan", "keluar", "tutup sesi") ||
        lower.startsWith("batal ") || lower.startsWith("hentikan ")
  }

  fun parseCandidateIndex(input: String, maxCount: Int): Int? {
    val lower = input.lowercase().trim()
    // 1. Direct digit matching
    val digitMatcher = java.util.regex.Pattern.compile("(?:nomor|pilih|ke|yang)?\\s*(\\d+)").matcher(lower)
    if (digitMatcher.find()) {
      val num = digitMatcher.group(1)?.toIntOrNull()
      if (num != null && num in 1..maxCount) {
        return num - 1
      }
    }

    // 2. Word numbers in Indonesian
    val wordMap = mapOf(
      "pertama" to 0, "satu" to 0,
      "kedua" to 1, "dua" to 1,
      "ketiga" to 2, "tiga" to 2,
      "keempat" to 3, "empat" to 3,
      "kelima" to 4, "lima" to 4
    )
    for ((word, index) in wordMap) {
      if (index < maxCount && (lower == word || lower.contains("nomor $word") || lower.contains("yang $word") || lower.contains("pilih $word"))) {
        return index
      }
    }
    return null
  }

  private fun matchesTodayAgenda(lower: String): Boolean {
    return lower.contains("agenda hari ini") ||
        lower.contains("apa agenda") ||
        lower.contains("lihat agenda") ||
        lower.contains("ada agenda apa") ||
        lower.contains("jadwal hari ini") ||
        lower.contains("apa jadwalku") ||
        lower.contains("tugas hari ini") ||
        lower.contains("rencana hari ini") ||
        lower == "agenda" ||
        lower == "jadwal"
  }

  private fun matchesNextReminder(lower: String): Boolean {
    return lower.contains("reminder berikutnya") ||
        lower.contains("pengingat berikutnya") ||
        lower.contains("reminder selanjutnya") ||
        lower.contains("pengingat selanjutnya") ||
        lower.contains("reminder terdekat") ||
        lower.contains("pengingat terdekat") ||
        lower.contains("jadwal terdekat") ||
        lower.contains("kapan reminder") ||
        lower.contains("apa reminder berikutnya")
  }

  private fun matchesComplete(lower: String): Boolean {
    return lower.startsWith("selesaikan") ||
        lower.startsWith("tandai selesai") ||
        lower.startsWith("centang") ||
        lower.contains(" selesai") ||
        lower.startsWith("tutup tugas") ||
        lower.startsWith("tutup reminder")
  }

  private fun parseCompleteCommand(trimmed: String, lower: String): ParsedCommand {
    var query = trimmed
      .replace(Regex("(?i)^(selesaikan|tandai selesai|centang|tutup reminder|tutup tugas|tutup)\\s*"), "")
      .replace(Regex("(?i)\\b(reminder|pengingat|tugas|task|jadwal)\\b"), "")
      .replace(Regex("(?i)\\s*selesai$"), "")
      .trim()

    query = query.replace(Regex("^[-:,\\s]+|[-:,\\s]+$"), "").trim()

    if (query.isBlank()) {
      return ParsedCommand(
        rawText = trimmed,
        intent = CommandIntent.CLARIFICATION,
        clarificationQuestion = "Pengingat apa yang ingin Anda tandai selesai?"
      )
    }

    return ParsedCommand(
      rawText = trimmed,
      intent = CommandIntent.COMPLETE_REMINDER,
      targetQuery = query
    )
  }

  private fun matchesDelete(lower: String): Boolean {
    return lower.startsWith("hapus") ||
        lower.startsWith("buang") ||
        lower.startsWith("delete") ||
        lower.startsWith("hilangkan") ||
        lower.startsWith("batalkan pengingat") ||
        lower.startsWith("batalkan reminder")
  }

  private fun parseDeleteCommand(trimmed: String, lower: String): ParsedCommand {
    var query = trimmed
      .replace(Regex("(?i)^(hapus|buang|delete|hilangkan|batalkan)\\s*"), "")
      .replace(Regex("(?i)\\b(pengingat|reminder|tugas|task|jadwal)\\b"), "")
      .trim()

    query = query.replace(Regex("^[-:,\\s]+|[-:,\\s]+$"), "").trim()

    if (query.isBlank()) {
      return ParsedCommand(
        rawText = trimmed,
        intent = CommandIntent.CLARIFICATION,
        clarificationQuestion = "Pengingat atau tugas mana yang ingin Anda hapus?"
      )
    }

    return ParsedCommand(
      rawText = trimmed,
      intent = CommandIntent.DELETE_REMINDER,
      targetQuery = query
    )
  }

  private fun matchesEdit(lower: String): Boolean {
    return lower.startsWith("ubah") ||
        lower.startsWith("edit") ||
        lower.startsWith("ganti") ||
        lower.startsWith("undur") ||
        lower.startsWith("majukan")
  }

  private fun parseEditCommand(trimmed: String, lower: String, baseTime: Long, timeZone: java.util.TimeZone): ParsedCommand {
    // E.g.: "ubah reminder rapat menjadi besok jam 10" or "edit reminder minum obat jam 8 malam"
    val parts = trimmed.split(Regex("(?i)\\b(menjadi|ke|pada|jam|tanggal)\\b", RegexOption.IGNORE_CASE), limit = 2)

    val targetPart = if (parts.isNotEmpty()) {
      parts[0]
        .replace(Regex("(?i)^(ubah|edit|ganti|undur|majukan)\\s*"), "")
        .replace(Regex("(?i)\\b(pengingat|reminder|tugas|task|jadwal)\\b"), "")
        .trim()
    } else ""

    val detailPart = if (parts.size > 1) parts[1] else trimmed

    val parsedTime = DateTimeVoiceParser.parse(detailPart, baseTime, timeZone)

    if (targetPart.isBlank()) {
      return ParsedCommand(
        rawText = trimmed,
        intent = CommandIntent.CLARIFICATION,
        clarificationQuestion = "Pengingat mana yang ingin Anda ubah?"
      )
    }

    return ParsedCommand(
      rawText = trimmed,
      intent = CommandIntent.EDIT_REMINDER,
      targetQuery = targetPart,
      dueDateTime = parsedTime.timestamp,
      repeatMode = parsedTime.repeatMode
    )
  }

  private fun matchesCreateReminder(lower: String): Boolean {
    return lower.startsWith("ingatkan") ||
        lower.startsWith("buat pengingat") ||
        lower.startsWith("tambah pengingat") ||
        lower.startsWith("buat reminder") ||
        lower.startsWith("tambah reminder") ||
        lower.startsWith("jadwalkan") ||
        lower.startsWith("remind me")
  }

  private fun parseCreateReminderCommand(trimmed: String, baseTime: Long, timeZone: java.util.TimeZone): ParsedCommand {
    val parsed = DateTimeVoiceParser.parse(trimmed, baseTime, timeZone)
    val priority = extractPriority(trimmed)
    val category = extractCategory(trimmed)

    val finalTitle = if (parsed.hasExplicitTitle) parsed.cleanedTitle else ""
    val finalDueDateTime = if (parsed.hasExplicitDateTime) parsed.timestamp else null

    val normalized = DateTimeVoiceParser.normalizeTemporalWords(trimmed.lowercase())

    // Format Relative Duration
    val relativeDurationStr = if (parsed.relativeDurationMillis != null) {
      val mins = parsed.relativeDurationMillis / (60 * 1000L)
      if (mins >= 60 && mins % 60 == 0L) {
        val hours = mins / 60
        "$hours jam"
      } else if (mins == 30L && normalized.contains("setengah jam lagi")) {
        "30 menit (setengah jam)"
      } else {
        "$mins menit"
      }
    } else {
      "None"
    }

    // Format Relative Date
    val relativeDateStr = when {
      parsed.relativeDateExpression != null -> parsed.relativeDateExpression
      parsed.relativeDayOffset != null -> when (parsed.relativeDayOffset) {
        0 -> "Hari ini"
        1 -> "Besok (+1 hari)"
        2 -> "Lusa (+2 hari)"
        7 -> "Seminggu lagi (+7 hari)"
        else -> "+${parsed.relativeDayOffset} hari"
      }
      normalized.contains("besok") -> "Besok (+1 hari)"
      normalized.contains("lusa") -> "Lusa (+2 hari)"
      else -> "None"
    }

    // Weekday String
    val weekdayStr = parsed.extractedWeekday ?: "None"

    // Clock Time String
    val clockTimeStr = if (parsed.targetHour != null) {
      String.format(java.util.Locale.ROOT, "%02d:%02d", parsed.targetHour, parsed.targetMinute ?: 0)
    } else if (parsed.relativeDurationMillis != null) {
      val cal = java.util.Calendar.getInstance(timeZone).apply { timeInMillis = parsed.timestamp }
      String.format(java.util.Locale.ROOT, "%02d:%02d", cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE))
    } else {
      "Not provided"
    }

    // Meridiem String
    val meridiemStr = parsed.extractedMeridiem ?: "None"

    // Target Date & Time Formatted
    val (targetDateFormatted, targetTimeFormatted) = if (finalDueDateTime != null) {
      val cal = java.util.Calendar.getInstance(timeZone).apply { timeInMillis = finalDueDateTime }
      val sdfDate = java.text.SimpleDateFormat("d MMMM yyyy", java.util.Locale("id", "ID")).apply { this.timeZone = timeZone }
      val sdfTime = java.text.SimpleDateFormat("HH:mm", java.util.Locale("id", "ID")).apply { this.timeZone = timeZone }
      val tzName = if (timeZone.id == "Asia/Jakarta" || timeZone.rawOffset == 7 * 3600 * 1000) "WIB" else timeZone.displayName
      Pair(sdfDate.format(cal.time), "${sdfTime.format(cal.time)} $tzName")
    } else if (parsed.targetDateMillis != null) {
      val cal = java.util.Calendar.getInstance(timeZone).apply { timeInMillis = parsed.targetDateMillis }
      val sdfDate = java.text.SimpleDateFormat("d MMMM yyyy", java.util.Locale("id", "ID")).apply { this.timeZone = timeZone }
      Pair(sdfDate.format(cal.time), "Not provided")
    } else {
      Pair("None", "None")
    }

    // Missing fields & Final decision
    val missingList = mutableListOf<String>()
    if (finalTitle.isBlank()) missingList.add("Title")
    if (!parsed.hasExplicitTime) missingList.add("Clock Time")
    if (!parsed.hasExplicitDate && parsed.relativeDurationMillis == null) missingList.add("Date")

    val finalDecision: ParserFinalDecision
    val isPendingClarification: Boolean
    val rejectionReason: String?

    when {
      finalTitle.isNotBlank() && finalDueDateTime != null -> {
        finalDecision = ParserFinalDecision.READY_TO_CREATE
        isPendingClarification = false
        rejectionReason = null
      }
      finalTitle.isNotBlank() && finalDueDateTime == null -> {
        finalDecision = ParserFinalDecision.NEEDS_CLARIFICATION
        isPendingClarification = true
        rejectionReason = null
      }
      finalTitle.isBlank() && finalDueDateTime != null -> {
        finalDecision = ParserFinalDecision.NEEDS_CLARIFICATION
        isPendingClarification = true
        rejectionReason = null
      }
      else -> {
        finalDecision = ParserFinalDecision.REJECTED
        isPendingClarification = false
        rejectionReason = "Informasi judul dan waktu tidak mencukupi"
      }
    }

    val trace = ParserDebugTrace(
      originalText = trimmed,
      normalizedText = normalized,
      intent = CommandIntent.CREATE_REMINDER,
      activityTitle = finalTitle,
      relativeDurationString = relativeDurationStr,
      relativeDateString = relativeDateStr,
      weekdayString = weekdayStr,
      clockTimeString = clockTimeStr,
      meridiemString = meridiemStr,
      targetDateFormatted = targetDateFormatted,
      targetTimeFormatted = targetTimeFormatted,
      targetTimestamp = finalDueDateTime,
      missingFields = missingList,
      isPendingClarification = isPendingClarification,
      finalDecision = finalDecision,
      rejectionReason = rejectionReason,
      relativeDateUnit = parsed.relativeDateUnit,
      relativeDateAmount = parsed.relativeDateAmount,
      relativeDateExpression = parsed.relativeDateExpression
    )

    return ParsedCommand(
      rawText = trimmed,
      intent = CommandIntent.CREATE_REMINDER,
      newTitle = finalTitle,
      dueDateTime = finalDueDateTime,
      repeatMode = parsed.repeatMode,
      priority = priority,
      category = category,
      hasExplicitDate = parsed.hasExplicitDate,
      hasExplicitTime = parsed.hasExplicitTime,
      targetDateMillis = parsed.targetDateMillis,
      missingField = parsed.missingField,
      debugTrace = trace
    )
  }

  fun hasTemporalMarkers(lower: String): Boolean {
    return lower.contains("besok") ||
        lower.contains("lusa") ||
        lower.contains("nanti") ||
        lower.contains("hari ini") ||
        lower.contains("minggu depan") ||
        lower.contains("hari lagi") ||
        lower.contains("jam lagi") ||
        lower.contains("jm lagi") ||
        lower.contains("menit lagi") ||
        lower.contains("mnt lagi") ||
        lower.contains("minggu lagi") ||
        lower.contains("minggu dari sekarang") ||
        lower.contains("seminggu lagi") ||
        lower.contains("seminggu dari sekarang") ||
        lower.contains("bulan lagi") ||
        lower.contains("bulan dari sekarang") ||
        lower.contains("sebulan lagi") ||
        lower.contains("sebulan dari sekarang") ||
        lower.contains("tahun lagi") ||
        lower.contains("tahun dari sekarang") ||
        lower.contains("setahun lagi") ||
        lower.contains("setahun dari sekarang") ||
        lower.contains("setengah jam lagi") ||
        lower.contains("jam ") ||
        lower.contains("pukul ") ||
        lower.contains("setiap ") ||
        lower.contains("tiap ") ||
        lower.contains("tanggal ") ||
        lower.contains("tgl ") ||
        lower.contains("pagi") ||
        lower.contains("siang") ||
        lower.contains("sore") ||
        lower.contains("malam") ||
        lower.contains("senin") ||
        lower.contains("selasa") ||
        lower.contains("rabu") ||
        lower.contains("kamis") ||
        lower.contains("jumat") ||
        lower.contains("jum'at") ||
        lower.contains("sabtu") ||
        lower.contains("minggu")
  }

  private fun hasActionWords(lower: String): Boolean {
    return lower.split("\\s+".toRegex()).size >= 3
  }

  private fun extractPriority(text: String): Priority {
    val lower = text.lowercase()
    return when {
      lower.contains("prioritas tinggi") || lower.contains("penting") || lower.contains("urgent") -> Priority.HIGH
      lower.contains("prioritas rendah") || lower.contains("santai") -> Priority.LOW
      else -> Priority.MEDIUM
    }
  }

  private fun extractCategory(text: String): PriorityCategory {
    val lower = text.lowercase()
    return when {
      lower.contains("pekerjaan") || lower.contains("kerja") || lower.contains("kantor") -> "Pekerjaan"
      lower.contains("kesehatan") || lower.contains("obat") || lower.contains("olahraga") -> "Kesehatan"
      lower.contains("pribadi") || lower.contains("keluarga") || lower.contains("rumah") -> "Pribadi"
      lower.contains("belajar") || lower.contains("kuliah") || lower.contains("sekolah") -> "Belajar"
      else -> "Voice"
    }
  }
}

typealias PriorityCategory = String
