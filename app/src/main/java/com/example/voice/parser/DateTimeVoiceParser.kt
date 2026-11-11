package com.example.voice.parser

import com.example.data.model.RepeatMode
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.regex.Pattern

enum class MissingField {
  NONE,
  TIME,
  DATE,
  TITLE
}

data class ParsedDateTimeResult(
  val timestamp: Long,
  val repeatMode: RepeatMode,
  val cleanedTitle: String,
  val hasExplicitDateTime: Boolean = false,
  val hasExplicitTitle: Boolean = true,
  val targetDayOfMonth: Int = -1,
  val hasExplicitDate: Boolean = false,
  val hasExplicitTime: Boolean = false,
  val targetDateMillis: Long? = null,
  val missingField: MissingField = MissingField.NONE,
  val relativeDurationMillis: Long? = null,
  val relativeDayOffset: Int? = null,
  val targetHour: Int? = null,
  val targetMinute: Int? = null,
  val extractedWeekday: String? = null,
  val extractedMeridiem: String? = null,
  val relativeDateUnit: RelativeDateUnit? = null,
  val relativeDateAmount: Int? = null,
  val relativeDateExpression: String? = null
)

object DateTimeVoiceParser {

  val INDONESIAN_NUMBERS_MAP = listOf(
    "tiga puluh satu" to 31,
    "tiga puluh" to 30,
    "dua puluh sembilan" to 29,
    "dua puluh delapan" to 28,
    "dua puluh tujuh" to 27,
    "dua puluh enam" to 26,
    "dua puluh lima" to 25,
    "dua puluh empat" to 24,
    "dua puluh tiga" to 23,
    "dua puluh dua" to 22,
    "dua puluh satu" to 21,
    "dua puluh" to 20,
    "sembilan belas" to 19,
    "delapan belas" to 18,
    "tujuh belas" to 17,
    "enam belas" to 16,
    "lima belas" to 15,
    "empat belas" to 14,
    "tiga belas" to 13,
    "dua belas" to 12,
    "sebelas" to 11,
    "sepuluh" to 10,
    "sembilan" to 9,
    "delapan" to 8,
    "tujuh" to 7,
    "enam" to 6,
    "lima" to 5,
    "empat" to 4,
    "tiga" to 3,
    "dua" to 2,
    "satu" to 1
  )

  fun normalizeTemporalWords(input: String): String {
    var text = input
    for ((words, num) in INDONESIAN_NUMBERS_MAP) {
      // 1. jam / pukul <words>
      text = text.replace(Regex("(?i)\\b(jam|pukul)\\s+$words\\b"), "$1 $num")
      // 2. <words> (menit|mnt|jam|jm|hari|minggu|bulan|tahun) lagi
      text = text.replace(Regex("(?i)\\b$words\\s+(menit|mnt|jam|jm|hari|minggu|bulan|tahun)\\s+lagi\\b"), "$num $1 lagi")
      // 3. <words> (menit|mnt|jam|jm|hari|minggu|bulan|tahun) dari sekarang
      text = text.replace(Regex("(?i)\\b$words\\s+(menit|mnt|jam|jm|hari|minggu|bulan|tahun)\\s+dari\\s+sekarang\\b"), "$num $1 dari sekarang")
      // 4. <words> (menit|mnt|jam|jm|hari|minggu|bulan|tahun)
      text = text.replace(Regex("(?i)\\b$words\\s+(menit|mnt|jam|jm|hari|minggu|bulan|tahun)\\b"), "$num $1")
      // 5. tanggal / tgl <words>
      text = text.replace(Regex("(?i)\\b(tanggal|tgl)\\s+$words\\b"), "$1 $num")
      // 6. setengah <words>
      text = text.replace(Regex("(?i)\\bsetengah\\s+$words\\b"), "setengah $num")
    }
    return text
  }

  fun parse(
    text: String,
    baseTimeMillis: Long = System.currentTimeMillis(),
    timeZone: TimeZone = TimeZone.getDefault()
  ): ParsedDateTimeResult {
    val lower = text.lowercase(Locale.ROOT).trim()
    val normalized = normalizeTemporalWords(lower)

    val calendar = Calendar.getInstance(timeZone).apply {
      timeInMillis = baseTimeMillis
    }

    var repeatMode = RepeatMode.ONCE
    var dayOffset = 0
    var hasSpecificDay = false
    var targetHour = -1
    var targetMinute = 0
    var isRelativeOffset = false
    var relativeOffsetMillis = 0L
    var explicitDayOfMonth = -1

    // Repeat modes
    if (normalized.contains("setiap hari") || normalized.contains("tiap hari") || normalized.contains("harian")) {
      repeatMode = RepeatMode.DAILY
    } else if (normalized.contains("setiap minggu") || normalized.contains("tiap minggu") || normalized.contains("mingguan") ||
      normalized.contains("setiap senin") || normalized.contains("setiap selasa") || normalized.contains("setiap rabu") ||
      normalized.contains("setiap kamis") || normalized.contains("setiap jumat") || normalized.contains("setiap sabtu") ||
      normalized.contains("setiap minggu")
    ) {
      repeatMode = RepeatMode.WEEKLY
    } else if (normalized.contains("setiap bulan") || normalized.contains("tiap bulan") || normalized.contains("bulanan") ||
      normalized.contains("setiap tanggal") || normalized.contains("tiap tanggal")
    ) {
      repeatMode = RepeatMode.MONTHLY
    }

    var extractedWeekdayName: String? = null
    var extractedMeridiemName: String? = null

    // 1. Relative duration (Minutes)
    if (normalized.contains("setengah jam lagi")) {
      relativeOffsetMillis += 30 * 60 * 1000L
      isRelativeOffset = true
    } else {
      val minutePattern = Pattern.compile("(\\d+)\\s*(menit|mnt)\\s*lagi")
      val minuteMatcher = minutePattern.matcher(normalized)
      if (minuteMatcher.find()) {
        val mins = minuteMatcher.group(1)?.toIntOrNull() ?: 0
        relativeOffsetMillis += mins * 60 * 1000L
        isRelativeOffset = true
      }
    }

    // 2. Relative duration (Hours)
    val hourPattern = Pattern.compile("(\\d+)\\s*(jam|jm)\\s*lagi")
    val hourMatcher = hourPattern.matcher(normalized)
    if (hourMatcher.find()) {
      val hours = hourMatcher.group(1)?.toIntOrNull() ?: 0
      relativeOffsetMillis += hours * 3600 * 1000L
      isRelativeOffset = true
    }

    // 3. Relative Date & Weekday & Absolute Date (Only if not a pure relative duration)
    var targetLocalDate: java.time.LocalDate? = null
    var calculatedDayOffset: Int? = null
    var detectedRelativeUnit: RelativeDateUnit? = null
    var detectedRelativeAmount: Int? = null
    var detectedRelativeExpr: String? = null

    if (!isRelativeOffset) {
      val zoneId: java.time.ZoneId = try {
        timeZone.toZoneId()
      } catch (_: Exception) {
        java.time.ZoneId.of("Asia/Jakarta")
      }
      val baseLocalDate = java.time.Instant.ofEpochMilli(baseTimeMillis).atZone(zoneId).toLocalDate()

      // 3a. Relative Weeks: "1 minggu lagi", "2 minggu dari sekarang", "seminggu lagi"
      val weekPattern = Pattern.compile("(\\d+)\\s*minggu\\s*(lagi|dari sekarang)")
      val weekMatcher = weekPattern.matcher(normalized)
      if (weekMatcher.find()) {
        val weeks = weekMatcher.group(1)?.toIntOrNull() ?: 1
        detectedRelativeUnit = RelativeDateUnit.WEEK
        detectedRelativeAmount = weeks
        detectedRelativeExpr = weekMatcher.group(0)
        targetLocalDate = baseLocalDate.plusWeeks(weeks.toLong())
        hasSpecificDay = true
      } else if (normalized.contains("seminggu lagi") || normalized.contains("seminggu dari sekarang")) {
        val expr = if (normalized.contains("seminggu dari sekarang")) "seminggu dari sekarang" else "seminggu lagi"
        detectedRelativeUnit = RelativeDateUnit.WEEK
        detectedRelativeAmount = 1
        detectedRelativeExpr = expr
        targetLocalDate = baseLocalDate.plusWeeks(1)
        hasSpecificDay = true
      }

      // 3b. Relative Months: "1 bulan lagi", "2 bulan dari sekarang", "sebulan lagi"
      if (targetLocalDate == null) {
        val monthPattern = Pattern.compile("(\\d+)\\s*bulan\\s*(lagi|dari sekarang)")
        val monthMatcher = monthPattern.matcher(normalized)
        if (monthMatcher.find()) {
          val months = monthMatcher.group(1)?.toIntOrNull() ?: 1
          detectedRelativeUnit = RelativeDateUnit.MONTH
          detectedRelativeAmount = months
          detectedRelativeExpr = monthMatcher.group(0)
          targetLocalDate = baseLocalDate.plusMonths(months.toLong())
          hasSpecificDay = true
        } else if (normalized.contains("sebulan lagi") || normalized.contains("sebulan dari sekarang")) {
          val expr = if (normalized.contains("sebulan dari sekarang")) "sebulan dari sekarang" else "sebulan lagi"
          detectedRelativeUnit = RelativeDateUnit.MONTH
          detectedRelativeAmount = 1
          detectedRelativeExpr = expr
          targetLocalDate = baseLocalDate.plusMonths(1)
          hasSpecificDay = true
        }
      }

      // 3c. Relative Years: "setahun lagi", "1 tahun lagi", "setahun dari sekarang"
      if (targetLocalDate == null) {
        val yearPattern = Pattern.compile("(\\d+)\\s*tahun\\s*(lagi|dari sekarang)")
        val yearMatcher = yearPattern.matcher(normalized)
        if (yearMatcher.find()) {
          val years = yearMatcher.group(1)?.toIntOrNull() ?: 1
          detectedRelativeUnit = RelativeDateUnit.MONTH
          detectedRelativeAmount = years * 12
          detectedRelativeExpr = yearMatcher.group(0)
          targetLocalDate = baseLocalDate.plusYears(years.toLong())
          hasSpecificDay = true
        } else if (normalized.contains("setahun lagi") || normalized.contains("setahun dari sekarang")) {
          val expr = if (normalized.contains("setahun dari sekarang")) "setahun dari sekarang" else "setahun lagi"
          detectedRelativeUnit = RelativeDateUnit.MONTH
          detectedRelativeAmount = 12
          detectedRelativeExpr = expr
          targetLocalDate = baseLocalDate.plusYears(1)
          hasSpecificDay = true
        }
      }

      // 3d. Relative days: "3 hari lagi", "2 hari lagi", "besok", "lusa", etc.
      if (targetLocalDate == null) {
        val dayPattern = Pattern.compile("(\\d+)\\s*hari\\s*(?:lagi|dari sekarang|dari hari ini)")
        val dayMatcher = dayPattern.matcher(normalized)
        if (dayMatcher.find()) {
          dayOffset = dayMatcher.group(1)?.toIntOrNull() ?: 0
          hasSpecificDay = true
        } else if (normalized.contains("lusa")) {
          dayOffset = 2
          hasSpecificDay = true
        } else if (normalized.contains("besok")) {
          dayOffset = 1
          hasSpecificDay = true
        } else if (normalized.contains("minggu depan")) {
          // Handled in weekday section below or fallback 7
          dayOffset = 7
          hasSpecificDay = true
        } else if (normalized.contains("hari ini") || normalized.contains("nanti")) {
          dayOffset = 0
          hasSpecificDay = true
        }
      }

      if (targetLocalDate != null) {
        calendar.set(Calendar.YEAR, targetLocalDate.year)
        calendar.set(Calendar.MONTH, targetLocalDate.monthValue - 1)
        calendar.set(Calendar.DAY_OF_MONTH, targetLocalDate.dayOfMonth)
        calculatedDayOffset = java.time.temporal.ChronoUnit.DAYS.between(baseLocalDate, targetLocalDate).toInt()
      } else if (hasSpecificDay) {
        calculatedDayOffset = dayOffset
      }

      // Weekday handling (ISO: Senin=1, Selasa=2, Rabu=3, Kamis=4, Jumat=5, Sabtu=6, Minggu=7)
      // Only execute if not already resolved as a relative week/month/day
      if (targetLocalDate == null) {
        val isoWeekdays = mapOf(
          "senin" to 1,
          "selasa" to 2,
          "rabu" to 3,
          "kamis" to 4,
          "jumat" to 5,
          "jum'at" to 5,
          "sabtu" to 6,
          "minggu" to 7
        )

        val calDow = calendar.get(Calendar.DAY_OF_WEEK)
        val currentIso = if (calDow == Calendar.SUNDAY) 7 else calDow - 1

        for ((dayName, targetIso) in isoWeekdays) {
          if (normalized.contains(dayName)) {
            extractedWeekdayName = dayName.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
            if (normalized.contains("setiap $dayName") || normalized.contains("tiap $dayName")) {
              repeatMode = RepeatMode.WEEKLY
            }

            // Distinguish: "<day> ini" (within current week) vs "<day> depan" (next calendar week)
            if (normalized.contains("$dayName ini") || normalized.contains("hari $dayName ini")) {
              dayOffset = targetIso - currentIso
              hasSpecificDay = true
              calculatedDayOffset = dayOffset
              break
            } else if (normalized.contains("$dayName depan")) {
              dayOffset = (targetIso - currentIso) + 7
              hasSpecificDay = true
              calculatedDayOffset = dayOffset
              break
            } else if (!hasSpecificDay) {
              // General mention like "hari Minggu" or "hari Jumat"
              val diff = targetIso - currentIso
              dayOffset = if (diff >= 0) diff else diff + 7
              hasSpecificDay = true
              calculatedDayOffset = dayOffset
              break
            }
          }
        }
      }

      // Absolute date: "tanggal 15", "tgl 5"
      val datePattern = Pattern.compile("(?:tanggal|tgl)\\s*(\\d{1,2})")
      val dateMatcher = datePattern.matcher(normalized)
      if (dateMatcher.find()) {
        val targetDay = dateMatcher.group(1)?.toIntOrNull()
        if (targetDay != null && targetDay in 1..31) {
          explicitDayOfMonth = targetDay
          calendar.set(Calendar.DAY_OF_MONTH, targetDay)
          if (calendar.timeInMillis < baseTimeMillis && repeatMode != RepeatMode.MONTHLY) {
            calendar.add(Calendar.MONTH, 1)
          }
          hasSpecificDay = true
        }
      }

      // Clock Time: "setengah 8 pagi" = 07.30
      val setengahPattern = Pattern.compile("setengah\\s*(\\d{1,2})(?:\\s*(pagi|siang|sore|malam))?")
      val setengahMatcher = setengahPattern.matcher(normalized)
      if (setengahMatcher.find()) {
        val num = setengahMatcher.group(1)?.toIntOrNull() ?: 8
        val period = setengahMatcher.group(2)
        extractedMeridiemName = period
        var h = num - 1
        if (period != null) {
          h = adjustIndonesianHour(h, period)
        }
        targetHour = h
        targetMinute = 30
      }

      // Clock Time: colon/dot format "jam 13.00", "pukul 13:00", "jam 1.30 siang"
      if (targetHour == -1) {
        val colonTimePattern = Pattern.compile("(?:jam|pukul)\\s*(\\d{1,2})[:.](\\d{2})(?:\\s*(pagi|siang|sore|malam))?")
        val colonMatcher = colonTimePattern.matcher(normalized)
        if (colonMatcher.find()) {
          val rawHour = colonMatcher.group(1)?.toIntOrNull() ?: 9
          targetMinute = colonMatcher.group(2)?.toIntOrNull() ?: 0
          val period = colonMatcher.group(3)
          extractedMeridiemName = period
          targetHour = adjustIndonesianHour(rawHour, period)
        }
      }

      // Clock Time: general format "jam 9 pagi", "jam 1 siang", "jam 8 malam", "pukul 16"
      if (targetHour == -1) {
        val generalTimePattern = Pattern.compile("(?:jam|pukul)\\s*(\\d{1,2})(?:\\s*(pagi|siang|sore|malam))?")
        val generalMatcher = generalTimePattern.matcher(normalized)
        if (generalMatcher.find()) {
          val rawHour = generalMatcher.group(1)?.toIntOrNull() ?: 9
          val period = generalMatcher.group(2)
          extractedMeridiemName = period
          targetHour = adjustIndonesianHour(rawHour, period)
          targetMinute = 0
        }
      }

      // Fallback: standalone period keyword without explicit hour number
      if (targetHour == -1) {
        if (normalized.contains("pagi")) {
          extractedMeridiemName = "pagi"
          targetHour = 8
          targetMinute = 0
        } else if (normalized.contains("siang")) {
          extractedMeridiemName = "siang"
          targetHour = 13
          targetMinute = 0
        } else if (normalized.contains("sore")) {
          extractedMeridiemName = "sore"
          targetHour = 16
          targetMinute = 0
        } else if (normalized.contains("malam")) {
          extractedMeridiemName = "malam"
          targetHour = 20
          targetMinute = 0
        }
      }
    }

    val hasExplicitTime = isRelativeOffset || (targetHour != -1)
    val hasExplicitDate = isRelativeOffset || hasSpecificDay

    var targetDateMillis: Long? = null
    val finalTimestamp: Long

    if (isRelativeOffset) {
      finalTimestamp = baseTimeMillis + relativeOffsetMillis
      targetDateMillis = finalTimestamp
    } else {
      if (targetLocalDate == null && dayOffset != 0) {
        calendar.add(Calendar.DAY_OF_YEAR, dayOffset)
      }

      if (hasExplicitDate) {
        val dateCal = Calendar.getInstance(timeZone).apply {
          timeInMillis = calendar.timeInMillis
          set(Calendar.HOUR_OF_DAY, 0)
          set(Calendar.MINUTE, 0)
          set(Calendar.SECOND, 0)
          set(Calendar.MILLISECOND, 0)
        }
        targetDateMillis = dateCal.timeInMillis
      }

      if (targetHour != -1) {
        calendar.set(Calendar.HOUR_OF_DAY, targetHour)
        calendar.set(Calendar.MINUTE, targetMinute)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)

        // Advance to tomorrow if only clock time was provided and it already passed today
        if (!hasSpecificDay && calendar.timeInMillis <= baseTimeMillis) {
          calendar.add(Calendar.DAY_OF_YEAR, 1)
        }
      } else if (!hasSpecificDay || (dayOffset <= 0 && targetLocalDate == null)) {
        calendar.add(Calendar.HOUR_OF_DAY, 1)
      } else {
        // Placeholder 09:00, but hasExplicitTime remains false
        calendar.set(Calendar.HOUR_OF_DAY, 9)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
      }

      finalTimestamp = calendar.timeInMillis
    }

    val hasExplicitDateTime = isRelativeOffset ||
        (hasExplicitDate && hasExplicitTime) ||
        (!hasExplicitDate && hasExplicitTime) ||
        (repeatMode != RepeatMode.ONCE)

    val rawCleaned = cleanRawReminderTitle(text)
    val hasExplicitTitle = rawCleaned.isNotBlank()
    val finalTitle = if (hasExplicitTitle) {
      if (rawCleaned.isNotEmpty()) {
        val firstChar = rawCleaned[0]
        val head = if (firstChar.isLowerCase()) firstChar.titlecase(Locale.getDefault()) else firstChar.toString()
        head + rawCleaned.substring(1)
      } else {
        rawCleaned
      }
    } else {
      "Pengingat Baru"
    }

    val missingField = when {
      !hasExplicitTitle -> MissingField.TITLE
      !hasExplicitTime && hasExplicitDate -> MissingField.TIME
      !hasExplicitDate && !isRelativeOffset -> MissingField.DATE
      else -> MissingField.NONE
    }

    return ParsedDateTimeResult(
      timestamp = finalTimestamp,
      repeatMode = repeatMode,
      cleanedTitle = finalTitle,
      hasExplicitDateTime = hasExplicitDateTime,
      hasExplicitTitle = hasExplicitTitle,
      targetDayOfMonth = if (explicitDayOfMonth != -1) explicitDayOfMonth else calendar.get(Calendar.DAY_OF_MONTH),
      hasExplicitDate = hasExplicitDate,
      hasExplicitTime = hasExplicitTime,
      targetDateMillis = targetDateMillis,
      missingField = missingField,
      relativeDurationMillis = if (isRelativeOffset) relativeOffsetMillis else null,
      relativeDayOffset = calculatedDayOffset,
      targetHour = if (targetHour != -1) targetHour else null,
      targetMinute = if (targetHour != -1) targetMinute else null,
      extractedWeekday = extractedWeekdayName,
      extractedMeridiem = extractedMeridiemName,
      relativeDateUnit = detectedRelativeUnit,
      relativeDateAmount = detectedRelativeAmount,
      relativeDateExpression = detectedRelativeExpr
    )
  }

  fun adjustIndonesianHour(rawHour: Int, period: String?): Int {
    var h = rawHour
    if (period != null) {
      when (period.lowercase(Locale.ROOT)) {
        "pagi" -> {
          // Pagi: jam 1–11 tetap, jam 12 menjadi 00
          if (h == 12) h = 0
        }
        "siang" -> {
          // Siang: jam 11 dan 12 tetap; jam 1–10 ditambah 12
          if (h in 1..10) {
            h += 12
          } else if (h == 11 || h == 12) {
            // tetap
          }
        }
        "sore" -> {
          // Sore: jam 3–6 ditambah 12; jam 1–2 tetap atau jika diucapkan sore disesuaikan
          if (h in 3..6) {
            h += 12
          } else if (h in 1..2) {
            h += 12
          }
        }
        "malam" -> {
          // Malam: jam 7–11 ditambah 12; jam 12 menjadi 00; jam 1–2 tetap
          if (h in 7..11) {
            h += 12
          } else if (h == 12) {
            h = 0
          } else if (h in 1..2) {
            // 1 malam = 01.00, 2 malam = 02.00 tetap
          }
        }
      }
    }
    return h
  }

  fun cleanRawReminderTitle(raw: String): String {
    // First normalize temporal words so words like "dua puluh lima menit lagi" become "25 menit lagi"
    var cleaned = normalizeTemporalWords(raw.lowercase(Locale.ROOT))
      .replace(Regex("(?i)^(ingatkan saya|ingatkan|buatkan pengingat|buat pengingat|tambah pengingat|jadwalkan pengingat|jadwalkan|remind me to|remind me)\\s*"), "")
      .replace(Regex("(?i)\\b(harus|perlu|tolong|wajib|mohon)\\b"), "")
      .replace(Regex("(?i)\\b(akan ada|akan)\\b"), "")
      .replace(Regex("(?i)\\b(untuk|buat|tentang)\\b"), "")
      .replace(Regex("(?i)\\b(setiap hari|tiap hari|harian|setiap minggu|tiap minggu|mingguan|setiap bulan|tiap bulan|bulanan)\\b"), "")
      .replace(Regex("(?i)\\b(\\d+|satu|dua|tiga|empat|lima|enam|tujuh|delapan|sembilan|sepuluh|sebelas|dua belas|dua puluh|tiga puluh)\\s*hari\\s*(lagi|dari sekarang|dari hari ini)?\\b"), "")
      .replace(Regex("(?i)\\b(\\d+|satu|dua|tiga|empat|lima|enam|tujuh|delapan|sembilan|sepuluh|sebelas|dua belas)\\s*minggu\\s*(lagi|dari sekarang)?\\b"), "")
      .replace(Regex("(?i)\\bseminggu\\s*(lagi|dari sekarang)?\\b"), "")
      .replace(Regex("(?i)\\b(\\d+|satu|dua|tiga|empat|lima|enam|tujuh|delapan|sembilan|sepuluh|sebelas|dua belas)\\s*bulan\\s*(lagi|dari sekarang)?\\b"), "")
      .replace(Regex("(?i)\\bsebulan\\s*(lagi|dari sekarang)?\\b"), "")
      .replace(Regex("(?i)\\b(\\d+|satu|dua|tiga|empat|lima|enam|tujuh|delapan|sembilan|sepuluh|sebelas|dua belas)\\s*tahun\\s*(lagi|dari sekarang)?\\b"), "")
      .replace(Regex("(?i)\\bsetahun\\s*(lagi|dari sekarang)?\\b"), "")
      .replace(Regex("(?i)\\bdari\\s+(hari\\s+ini|sekarang)\\b"), "")
      .replace(Regex("(?i)\\b(besok|lusa|minggu depan|hari ini|nanti)\\b"), "")
      .replace(Regex("(?i)\\bhari\\s*(senin|selasa|rabu|kamis|jumat|jum'at|sabtu|minggu)\\s*(ini|depan)?\\b"), "")
      .replace(Regex("(?i)\\b(senin|selasa|rabu|kamis|jumat|jum'at|sabtu|minggu)\\s*(ini|depan)\\b"), "")
      .replace(Regex("(?i)\\b(tanggal|tgl)\\s*\\d{1,2}\\b"), "")
      .replace(Regex("(?i)\\bsetengah\\s*(\\d{1,2}|satu|dua|tiga|empat|lima|enam|tujuh|delapan|sembilan|sepuluh|sebelas|dua belas)\\b"), "")
      .replace(Regex("(?i)\\b(jam|pukul)\\s*(\\d{1,2}|satu|dua|tiga|empat|lima|enam|tujuh|delapan|sembilan|sepuluh|sebelas|dua belas)([:.]\\d{2})?\\s*(wib|wita|wit)?\\s*(pagi|siang|sore|malam)?\\b"), "")
      .replace(Regex("(?i)\\b(\\d+)\\s*(menit|mnt)\\s*lagi\\b"), "")
      .replace(Regex("(?i)\\b(\\d+)\\s*(jam|jm)\\s*lagi\\b"), "")
      .replace(Regex("(?i)\\bsetengah\\s*jam\\s*lagi\\b"), "")
      .replace(Regex("(?i)\\b(pagi|siang|sore|malam)\\b"), "")
      .trim()

    // If title begins with filler "ada pertandingan" or "ada acara", clean filler "ada "
    cleaned = cleaned.replace(Regex("(?i)^ada\\s+(pertandingan|acara|konser|lomba|turnamen|kegiatan|meeting)\\b"), "$1")

    cleaned = cleaned
      .replace(Regex("\\s+"), " ")
      .replace(Regex("^[-:,\\s]+|[-:,\\s]+$"), "")
      .trim()

    // Capitalize first character of the cleaned title
    if (cleaned.isNotEmpty()) {
      val firstChar = cleaned[0]
      val head = if (firstChar.isLowerCase()) firstChar.titlecase(Locale.getDefault()) else firstChar.toString()
      cleaned = head + cleaned.substring(1)
    }

    return cleaned
  }
}
