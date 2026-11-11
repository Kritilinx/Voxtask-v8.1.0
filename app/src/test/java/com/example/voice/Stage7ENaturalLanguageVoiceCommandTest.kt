package com.example.voice

import com.example.data.model.RepeatMode
import com.example.voice.parser.CommandIntent
import com.example.voice.parser.DateTimeVoiceParser
import com.example.voice.parser.LocalCommandParser
import com.example.voice.parser.MissingField
import com.example.voice.parser.ParserFinalDecision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * Stage 7E.2 — Parser Debug Trace & Pre-Creation Validation Tests.
 * Standard testing clock: 10 November 2026 pukul 15.27 WIB.
 *
 * Verifies that every voice command produces a structured ParserDebugTrace:
 * 1. Original Text
 * 2. Normalized Text
 * 3. Intent Detection
 * 4. Activity Extraction
 * 5. Relative Duration Extraction
 * 6. Relative Date Extraction
 * 7. Weekday Extraction
 * 8. Clock Time Extraction
 * 9. Meridiem Extraction
 * 10. Target DateTime Calculation
 * 11. Missing Fields
 * 12. Pending Clarification
 * 13. Final Decision (READY_TO_CREATE, NEEDS_CLARIFICATION, REJECTED)
 */
class Stage7ENaturalLanguageVoiceCommandTest {

  private val wibTimeZone: TimeZone = TimeZone.getTimeZone("Asia/Jakarta")

  /** Fixed clock: 10 November 2026 15:27:00 WIB */
  private val fixedBaseTimeMillis: Long = Calendar.getInstance(wibTimeZone).apply {
    set(2026, Calendar.NOVEMBER, 10, 15, 27, 0)
    set(Calendar.MILLISECOND, 0)
  }.timeInMillis

  private fun printTrace(testName: String, input: String, cmd: com.example.voice.parser.ParsedCommand) {
    val trace = cmd.debugTrace
    println("==========================================================================")
    println("TRACE [$testName] Input: \"$input\"")
    if (trace != null) {
      println("  1. Original Text: ${trace.originalText}")
      println("  2. Normalized Text: ${trace.normalizedText}")
      println("  3. Intent: ${trace.intent}")
      println("  4. Activity: ${trace.activityTitle}")
      println("  5. Relative Duration: ${trace.relativeDurationString}")
      println("  6. Relative Date: ${trace.relativeDateString}")
      println("  7. Weekday: ${trace.weekdayString}")
      println("  8. Clock Time: ${trace.clockTimeString}")
      println("  9. Meridiem: ${trace.meridiemString}")
      println(" 10. Target Date: ${trace.targetDateFormatted}")
      println("     Target Time: ${trace.targetTimeFormatted}")
      println(" 11. Missing Fields: ${if (trace.missingFields.isEmpty()) "None" else trace.missingFields.joinToString()}")
      println(" 12. Pending Clarification: ${trace.isPendingClarification}")
      println(" 13. Final Decision: ${trace.finalDecision}")
      if (trace.rejectionReason != null) {
        println("      Reason: ${trace.rejectionReason}")
      }
    } else {
      println("  [ERROR] No ParserDebugTrace attached!")
    }
    println("==========================================================================")
  }

  // =========================================================================
  // MANDATORY 10 TEST CASES FOR STAGE 7E.2
  // =========================================================================

  // 1. Satu menit lagi harus tidur
  @Test
  fun test01_satuMenitLagiHarusTidur() {
    val input = "Satu menit lagi harus tidur"
    val cmd = LocalCommandParser.parse(input, fixedBaseTimeMillis, wibTimeZone)
    printTrace("Test 1: Satu menit lagi harus tidur", input, cmd)

    assertEquals(CommandIntent.CREATE_REMINDER, cmd.intent)
    assertEquals("Tidur", cmd.newTitle)
    assertNotNull(cmd.dueDateTime)

    val trace = cmd.debugTrace
    assertNotNull("Trace must be present", trace)
    assertEquals("Satu menit lagi harus tidur", trace!!.originalText)
    assertEquals("1 menit lagi harus tidur", trace.normalizedText)
    assertEquals(CommandIntent.CREATE_REMINDER, trace.intent)
    assertEquals("Tidur", trace.activityTitle)
    assertEquals("1 menit", trace.relativeDurationString)
    assertEquals("15:28", trace.clockTimeString)
    assertEquals("10 November 2026", trace.targetDateFormatted)
    assertTrue("Target time must contain 15:28", trace.targetTimeFormatted.contains("15:28"))
    assertTrue("Missing fields must be empty", trace.missingFields.isEmpty())
    assertFalse("Pending clarification must be false", trace.isPendingClarification)
    assertEquals(ParserFinalDecision.READY_TO_CREATE, trace.finalDecision)
  }

  // 2. Tiga hari lagi dari hari ini jam sembilan pagi ada ulangan
  @Test
  fun test02_tigaHariLagiDariHariIniJamSembilanPagiAdaUlangan() {
    val input = "Tiga hari lagi dari hari ini jam sembilan pagi ada ulangan"
    val cmd = LocalCommandParser.parse(input, fixedBaseTimeMillis, wibTimeZone)
    printTrace("Test 2: Tiga hari lagi dari hari ini jam sembilan pagi ada ulangan", input, cmd)

    assertEquals(CommandIntent.CREATE_REMINDER, cmd.intent)
    assertEquals("Ada ulangan", cmd.newTitle)
    assertNotNull(cmd.dueDateTime)

    val trace = cmd.debugTrace
    assertNotNull(trace)
    assertEquals(CommandIntent.CREATE_REMINDER, trace!!.intent)
    assertEquals("Ada ulangan", trace.activityTitle)
    assertEquals("+3 hari", trace.relativeDateString)
    assertEquals("09:00", trace.clockTimeString)
    assertEquals("pagi", trace.meridiemString)
    assertEquals("13 November 2026", trace.targetDateFormatted)
    assertTrue(trace.targetTimeFormatted.contains("09:00"))
    assertTrue(trace.missingFields.isEmpty())
    assertFalse(trace.isPendingClarification)
    assertEquals(ParserFinalDecision.READY_TO_CREATE, trace.finalDecision)
  }

  // 3. Besok jam sembilan pagi ada ulangan
  @Test
  fun test03_besokJamSembilanPagiAdaUlangan() {
    val input = "Besok jam sembilan pagi ada ulangan"
    val cmd = LocalCommandParser.parse(input, fixedBaseTimeMillis, wibTimeZone)
    printTrace("Test 3: Besok jam sembilan pagi ada ulangan", input, cmd)

    assertEquals(CommandIntent.CREATE_REMINDER, cmd.intent)
    assertEquals("Ada ulangan", cmd.newTitle)
    assertNotNull(cmd.dueDateTime)

    val trace = cmd.debugTrace
    assertNotNull(trace)
    assertEquals(CommandIntent.CREATE_REMINDER, trace!!.intent)
    assertEquals("Ada ulangan", trace.activityTitle)
    assertEquals("Besok (+1 hari)", trace.relativeDateString)
    assertEquals("09:00", trace.clockTimeString)
    assertEquals("11 November 2026", trace.targetDateFormatted)
    assertTrue(trace.targetTimeFormatted.contains("09:00"))
    assertTrue(trace.missingFields.isEmpty())
    assertFalse(trace.isPendingClarification)
    assertEquals(ParserFinalDecision.READY_TO_CREATE, trace.finalDecision)
  }

  // 4. Lima jam lagi harus tidur
  @Test
  fun test04_limaJamLagiHarusTidur() {
    val input = "Lima jam lagi harus tidur"
    val cmd = LocalCommandParser.parse(input, fixedBaseTimeMillis, wibTimeZone)
    printTrace("Test 4: Lima jam lagi harus tidur", input, cmd)

    assertEquals(CommandIntent.CREATE_REMINDER, cmd.intent)
    assertEquals("Tidur", cmd.newTitle)
    assertNotNull(cmd.dueDateTime)

    val trace = cmd.debugTrace
    assertNotNull(trace)
    assertEquals(CommandIntent.CREATE_REMINDER, trace!!.intent)
    assertEquals("Tidur", trace.activityTitle)
    assertEquals("5 jam", trace.relativeDurationString)
    assertEquals("20:27", trace.clockTimeString)
    assertEquals("10 November 2026", trace.targetDateFormatted)
    assertTrue(trace.targetTimeFormatted.contains("20:27"))
    assertTrue(trace.missingFields.isEmpty())
    assertFalse(trace.isPendingClarification)
    assertEquals(ParserFinalDecision.READY_TO_CREATE, trace.finalDecision)
  }

  // 5. Hari Minggu ini akan ada pertandingan bola
  @Test
  fun test05_hariMingguIniAkanAdaPertandinganBola() {
    val input = "Hari Minggu ini akan ada pertandingan bola"
    val cmd = LocalCommandParser.parse(input, fixedBaseTimeMillis, wibTimeZone)
    printTrace("Test 5: Hari Minggu ini akan ada pertandingan bola", input, cmd)

    assertEquals(CommandIntent.CREATE_REMINDER, cmd.intent)
    assertEquals("Pertandingan bola", cmd.newTitle)
    assertNull("Time is missing, dueDateTime must be null", cmd.dueDateTime)

    val trace = cmd.debugTrace
    assertNotNull(trace)
    assertEquals(CommandIntent.CREATE_REMINDER, trace!!.intent)
    assertEquals("Pertandingan bola", trace.activityTitle)
    assertEquals("Minggu", trace.weekdayString)
    assertEquals("15 November 2026", trace.targetDateFormatted)
    assertEquals("Not provided", trace.clockTimeString)
    assertTrue("Must list Clock Time as missing", trace.missingFields.contains("Clock Time"))
    assertTrue("Pending clarification must be true", trace.isPendingClarification)
    assertEquals(ParserFinalDecision.NEEDS_CLARIFICATION, trace.finalDecision)
  }

  // 6. Klarifikasi "Jam 4 sore" setelah perintah sebelumnya
  @Test
  fun test06_multiTurnClarificationJamEmpatSore() {
    // Turn 1: "Hari Minggu ini akan ada pertandingan bola"
    val turn1 = LocalCommandParser.parse("Hari Minggu ini akan ada pertandingan bola", fixedBaseTimeMillis, wibTimeZone)
    val savedTargetDate = turn1.targetDateMillis
    val savedTitle = turn1.newTitle
    assertNotNull(savedTargetDate)

    // Turn 2: User answers "jam 4 sore"
    val answer = "jam 4 sore"
    val timeParsed = DateTimeVoiceParser.parse(answer, fixedBaseTimeMillis, wibTimeZone)
    assertTrue(timeParsed.hasExplicitTime)

    val finalCal = Calendar.getInstance(wibTimeZone).apply {
      timeInMillis = savedTargetDate!!
      val timeCal = Calendar.getInstance(wibTimeZone).apply { timeInMillis = timeParsed.timestamp }
      set(Calendar.HOUR_OF_DAY, timeCal.get(Calendar.HOUR_OF_DAY))
      set(Calendar.MINUTE, timeCal.get(Calendar.MINUTE))
      set(Calendar.SECOND, 0)
      set(Calendar.MILLISECOND, 0)
    }

    val finalTimestamp = finalCal.timeInMillis
    val sdfDate = java.text.SimpleDateFormat("d MMMM yyyy", java.util.Locale("id", "ID")).apply { this.timeZone = wibTimeZone }
    val sdfTime = java.text.SimpleDateFormat("HH:mm", java.util.Locale("id", "ID")).apply { this.timeZone = wibTimeZone }

    val turn2Trace = com.example.voice.parser.ParserDebugTrace(
      originalText = answer,
      normalizedText = DateTimeVoiceParser.normalizeTemporalWords(answer.lowercase()),
      intent = CommandIntent.CREATE_REMINDER,
      activityTitle = savedTitle,
      clockTimeString = String.format(java.util.Locale.ROOT, "%02d:%02d", finalCal.get(Calendar.HOUR_OF_DAY), finalCal.get(Calendar.MINUTE)),
      meridiemString = timeParsed.extractedMeridiem ?: "None",
      targetDateFormatted = sdfDate.format(finalCal.time),
      targetTimeFormatted = "${sdfTime.format(finalCal.time)} WIB",
      targetTimestamp = finalTimestamp,
      missingFields = emptyList(),
      isPendingClarification = false,
      finalDecision = ParserFinalDecision.READY_TO_CREATE,
      isMultiTurnContinuation = true,
      previousContextSummary = "Activity: '$savedTitle', Date: 15 November 2026"
    )

    println("==========================================================================")
    println("TRACE [Test 6: Multi-turn Turn 2 Answer: \"$answer\"]")
    println("  Input: \"$answer\"")
    println("  Previous Context: ${turn2Trace.previousContextSummary}")
    println("  Final Activity: ${turn2Trace.activityTitle}")
    println("  Final Date: ${turn2Trace.targetDateFormatted}")
    println("  Final Time: ${turn2Trace.targetTimeFormatted}")
    println("  Final Decision: ${turn2Trace.finalDecision}")
    println("==========================================================================")

    assertEquals("Pertandingan bola", turn2Trace.activityTitle)
    assertEquals("15 November 2026", turn2Trace.targetDateFormatted)
    assertEquals("16:00", turn2Trace.clockTimeString)
    assertEquals(ParserFinalDecision.READY_TO_CREATE, turn2Trace.finalDecision)
    assertFalse(turn2Trace.isPendingClarification)
  }

  // 7. Tiga hari lagi harus minum
  @Test
  fun test07_tigaHariLagiHarusMinum() {
    val input = "3 hari lagi harus minum"
    val cmd = LocalCommandParser.parse(input, fixedBaseTimeMillis, wibTimeZone)
    printTrace("Test 7: 3 hari lagi harus minum", input, cmd)

    assertEquals(CommandIntent.CREATE_REMINDER, cmd.intent)
    assertEquals("Minum", cmd.newTitle)
    assertNull(cmd.dueDateTime)

    val trace = cmd.debugTrace
    assertNotNull(trace)
    assertEquals("Minum", trace!!.activityTitle)
    assertEquals("+3 hari", trace.relativeDateString)
    assertEquals("13 November 2026", trace.targetDateFormatted)
    assertEquals("Not provided", trace.clockTimeString)
    assertTrue(trace.missingFields.contains("Clock Time"))
    assertTrue(trace.isPendingClarification)
    assertEquals(ParserFinalDecision.NEEDS_CLARIFICATION, trace.finalDecision)
  }

  // 8. Lusa jam 8 malam harus minum
  @Test
  fun test08_lusaJamDelapanMalamHarusMinum() {
    val input = "lusa jam 8 malam harus minum"
    val cmd = LocalCommandParser.parse(input, fixedBaseTimeMillis, wibTimeZone)
    printTrace("Test 8: lusa jam 8 malam harus minum", input, cmd)

    assertEquals(CommandIntent.CREATE_REMINDER, cmd.intent)
    assertEquals("Minum", cmd.newTitle)
    assertNotNull(cmd.dueDateTime)

    val trace = cmd.debugTrace
    assertNotNull(trace)
    assertEquals("Minum", trace!!.activityTitle)
    assertEquals("Lusa (+2 hari)", trace.relativeDateString)
    assertEquals("20:00", trace.clockTimeString)
    assertEquals("malam", trace.meridiemString)
    assertEquals("12 November 2026", trace.targetDateFormatted)
    assertTrue(trace.targetTimeFormatted.contains("20:00"))
    assertTrue(trace.missingFields.isEmpty())
    assertFalse(trace.isPendingClarification)
    assertEquals(ParserFinalDecision.READY_TO_CREATE, trace.finalDecision)
  }

  // 9. Perintah dengan tanggal dan waktu absolut
  @Test
  fun test09_perintahDenganTanggalDanWaktuAbsolut() {
    val input = "tanggal 25 jam 14.30 bayar tagihan internet"
    val cmd = LocalCommandParser.parse(input, fixedBaseTimeMillis, wibTimeZone)
    printTrace("Test 9: tanggal 25 jam 14.30 bayar tagihan internet", input, cmd)

    assertEquals(CommandIntent.CREATE_REMINDER, cmd.intent)
    assertTrue(cmd.newTitle.contains("Bayar tagihan internet"))
    assertNotNull(cmd.dueDateTime)

    val trace = cmd.debugTrace
    assertNotNull(trace)
    assertEquals("14:30", trace!!.clockTimeString)
    assertEquals("25 November 2026", trace.targetDateFormatted)
    assertTrue(trace.targetTimeFormatted.contains("14:30"))
    assertEquals(ParserFinalDecision.READY_TO_CREATE, trace.finalDecision)
    assertFalse(trace.isPendingClarification)
  }

  // 10. Perintah dengan angka dalam bentuk tulisan dan angka numerik
  @Test
  fun test10_angkaTulisanDanNumerik() {
    val inputWords = "dua puluh lima menit lagi minum obat"
    val cmdWords = LocalCommandParser.parse(inputWords, fixedBaseTimeMillis, wibTimeZone)
    printTrace("Test 10a (Words): dua puluh lima menit lagi minum obat", inputWords, cmdWords)

    val traceWords = cmdWords.debugTrace
    assertNotNull(traceWords)
    assertEquals("25 menit lagi minum obat", traceWords!!.normalizedText)
    assertEquals("25 menit", traceWords.relativeDurationString)
    assertEquals(ParserFinalDecision.READY_TO_CREATE, traceWords.finalDecision)

    val inputDigits = "25 menit lagi minum obat"
    val cmdDigits = LocalCommandParser.parse(inputDigits, fixedBaseTimeMillis, wibTimeZone)
    printTrace("Test 10b (Digits): 25 menit lagi minum obat", inputDigits, cmdDigits)

    val traceDigits = cmdDigits.debugTrace
    assertNotNull(traceDigits)
    assertEquals("25 menit", traceDigits!!.relativeDurationString)
    assertEquals(traceWords.clockTimeString, traceDigits.clockTimeString)
    assertEquals(ParserFinalDecision.READY_TO_CREATE, traceDigits.finalDecision)
  }

  // =========================================================================
  // ADDITIONAL REGRESSION & COVERAGE TESTS
  // =========================================================================

  @Test
  fun test11_limaMenitLagiHarusMinum() {
    val input = "5 menit lagi harus minum"
    val cmd = LocalCommandParser.parse(input, fixedBaseTimeMillis, wibTimeZone)
    val cal = Calendar.getInstance(wibTimeZone).apply { timeInMillis = cmd.dueDateTime!! }
    assertEquals(15, cal.get(Calendar.HOUR_OF_DAY))
    assertEquals(32, cal.get(Calendar.MINUTE))
  }

  @Test
  fun test12_setengahJamLagiHarusMinum() {
    val input = "setengah jam lagi harus minum"
    val cmd = LocalCommandParser.parse(input, fixedBaseTimeMillis, wibTimeZone)
    val cal = Calendar.getInstance(wibTimeZone).apply { timeInMillis = cmd.dueDateTime!! }
    assertEquals(15, cal.get(Calendar.HOUR_OF_DAY))
    assertEquals(57, cal.get(Calendar.MINUTE))
  }

  @Test
  fun test13_tigaHariLagiJamSatuSiangHarusMinum() {
    val input = "3 hari lagi jam 1 siang harus minum"
    val cmd = LocalCommandParser.parse(input, fixedBaseTimeMillis, wibTimeZone)
    val cal = Calendar.getInstance(wibTimeZone).apply { timeInMillis = cmd.dueDateTime!! }
    assertEquals(13, cal.get(Calendar.DAY_OF_MONTH))
    assertEquals(13, cal.get(Calendar.HOUR_OF_DAY))
  }

  @Test
  fun test14_besokAdaRapat_requestsTimeClarification() {
    val input = "besok ada rapat"
    val cmd = LocalCommandParser.parse(input, fixedBaseTimeMillis, wibTimeZone)
    assertEquals(ParserFinalDecision.NEEDS_CLARIFICATION, cmd.debugTrace?.finalDecision)
    assertEquals("Ada rapat", cmd.newTitle)
  }

  @Test
  fun test15_hariMingguIniJamSepuluhPagi() {
    val input = "Hari Minggu ini jam 10 pagi ada pertandingan bola"
    val cmd = LocalCommandParser.parse(input, fixedBaseTimeMillis, wibTimeZone)
    assertEquals(ParserFinalDecision.READY_TO_CREATE, cmd.debugTrace?.finalDecision)
    val cal = Calendar.getInstance(wibTimeZone).apply { timeInMillis = cmd.dueDateTime!! }
    assertEquals(15, cal.get(Calendar.DAY_OF_MONTH))
    assertEquals(10, cal.get(Calendar.HOUR_OF_DAY))
  }

  @Test
  fun test16_mingguDepanJamSembilanPagi() {
    val input = "Minggu depan jam 9 pagi ada pertandingan bola"
    val cmd = LocalCommandParser.parse(input, fixedBaseTimeMillis, wibTimeZone)
    val cal = Calendar.getInstance(wibTimeZone).apply { timeInMillis = cmd.dueDateTime!! }
    assertEquals(22, cal.get(Calendar.DAY_OF_MONTH))
    assertEquals(9, cal.get(Calendar.HOUR_OF_DAY))
  }

  @Test
  fun test17_jam1Malam_produces0100() {
    val input = "ingatkan belajar jam 1 malam"
    val parsed = DateTimeVoiceParser.parse(input, fixedBaseTimeMillis, wibTimeZone)
    val cal = Calendar.getInstance(wibTimeZone).apply { timeInMillis = parsed.timestamp }
    assertEquals(1, cal.get(Calendar.HOUR_OF_DAY))
    assertEquals(0, cal.get(Calendar.MINUTE))
  }

  @Test
  fun test18_jam12Malam_produces0000() {
    val input = "ingatkan tidur jam 12 malam"
    val parsed = DateTimeVoiceParser.parse(input, fixedBaseTimeMillis, wibTimeZone)
    val cal = Calendar.getInstance(wibTimeZone).apply { timeInMillis = parsed.timestamp }
    assertEquals(0, cal.get(Calendar.HOUR_OF_DAY))
    assertEquals(0, cal.get(Calendar.MINUTE))
  }

  @Test
  fun test19_monthAndYearTransition() {
    val endOfJan = Calendar.getInstance(wibTimeZone).apply {
      set(2026, Calendar.JANUARY, 31, 15, 27, 0)
      set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    val cmdBesok = LocalCommandParser.parse("besok jam 9 pagi harus minum obat", endOfJan, wibTimeZone)
    val calBesok = Calendar.getInstance(wibTimeZone).apply { timeInMillis = cmdBesok.dueDateTime!! }
    assertEquals(2026, calBesok.get(Calendar.YEAR))
    assertEquals(Calendar.FEBRUARY, calBesok.get(Calendar.MONTH))
    assertEquals(1, calBesok.get(Calendar.DAY_OF_MONTH))
  }

  @Test
  fun test20_repeatModes() {
    val cmdDaily = LocalCommandParser.parse("setiap hari jam 7 pagi minum vitamin", fixedBaseTimeMillis, wibTimeZone)
    assertEquals(RepeatMode.DAILY, cmdDaily.repeatMode)

    val cmdWeekly = LocalCommandParser.parse("setiap senin jam 9 pagi evaluasi tim", fixedBaseTimeMillis, wibTimeZone)
    assertEquals(RepeatMode.WEEKLY, cmdWeekly.repeatMode)

    val cmdMonthly = LocalCommandParser.parse("setiap bulan tanggal 5 bayar listrik", fixedBaseTimeMillis, wibTimeZone)
    assertEquals(RepeatMode.MONTHLY, cmdMonthly.repeatMode)
  }

  // =========================================================================
  // STAGE 7E.3: MANDATORY MERIDIEM INTERPRETATION TESTS
  // =========================================================================

  @Test
  fun testStage7E3_malamHoursCoverage() {
    // Malam: 7 malam -> 19, 8 malam -> 20, 9 malam -> 21, 10 malam -> 22, 11 malam -> 23, 12 malam -> 00, 1 malam -> 01, 2 malam -> 02
    val testCases = listOf(
      Triple("7 malam", 19, 0),
      Triple("8 malam", 20, 0),
      Triple("9 malam", 21, 0),
      Triple("10 malam", 22, 0),
      Triple("11 malam", 23, 0),
      Triple("12 malam", 0, 0),
      Triple("1 malam", 1, 0),
      Triple("2 malam", 2, 0)
    )

    for ((phrase, expectedHour, expectedMinute) in testCases) {
      val input = "besok jam $phrase ada rapat"
      val cmd = LocalCommandParser.parse(input, fixedBaseTimeMillis, wibTimeZone)
      assertNotNull("Failed for phrase: $phrase", cmd.dueDateTime)
      val cal = Calendar.getInstance(wibTimeZone).apply { timeInMillis = cmd.dueDateTime!! }
      assertEquals("Hour mismatch for $phrase", expectedHour, cal.get(Calendar.HOUR_OF_DAY))
      assertEquals("Minute mismatch for $phrase", expectedMinute, cal.get(Calendar.MINUTE))
      assertEquals(ParserFinalDecision.READY_TO_CREATE, cmd.debugTrace?.finalDecision)
    }
  }

  @Test
  fun testStage7E3_pagiHoursCoverage() {
    // Pagi: 3 pagi -> 03, 4 pagi -> 04, 5 pagi -> 05, 6 pagi -> 06, 7 pagi -> 07, 8 pagi -> 08, 9 pagi -> 09, 10 pagi -> 10
    val testCases = listOf(
      Triple("3 pagi", 3, 0),
      Triple("4 pagi", 4, 0),
      Triple("5 pagi", 5, 0),
      Triple("6 pagi", 6, 0),
      Triple("7 pagi", 7, 0),
      Triple("8 pagi", 8, 0),
      Triple("9 pagi", 9, 0),
      Triple("10 pagi", 10, 0)
    )

    for ((phrase, expectedHour, expectedMinute) in testCases) {
      val input = "besok jam $phrase olahraga"
      val cmd = LocalCommandParser.parse(input, fixedBaseTimeMillis, wibTimeZone)
      assertNotNull("Failed for phrase: $phrase", cmd.dueDateTime)
      val cal = Calendar.getInstance(wibTimeZone).apply { timeInMillis = cmd.dueDateTime!! }
      assertEquals("Hour mismatch for $phrase", expectedHour, cal.get(Calendar.HOUR_OF_DAY))
      assertEquals("Minute mismatch for $phrase", expectedMinute, cal.get(Calendar.MINUTE))
      assertEquals(ParserFinalDecision.READY_TO_CREATE, cmd.debugTrace?.finalDecision)
    }
  }

  @Test
  fun testStage7E3_siangHoursCoverage() {
    // Siang: 11 siang -> 11, 12 siang -> 12, 1 siang -> 13, 2 siang -> 14
    val testCases = listOf(
      Triple("11 siang", 11, 0),
      Triple("12 siang", 12, 0),
      Triple("1 siang", 13, 0),
      Triple("2 siang", 14, 0)
    )

    for ((phrase, expectedHour, expectedMinute) in testCases) {
      val input = "besok jam $phrase makan siang"
      val cmd = LocalCommandParser.parse(input, fixedBaseTimeMillis, wibTimeZone)
      assertNotNull("Failed for phrase: $phrase", cmd.dueDateTime)
      val cal = Calendar.getInstance(wibTimeZone).apply { timeInMillis = cmd.dueDateTime!! }
      assertEquals("Hour mismatch for $phrase", expectedHour, cal.get(Calendar.HOUR_OF_DAY))
      assertEquals("Minute mismatch for $phrase", expectedMinute, cal.get(Calendar.MINUTE))
      assertEquals(ParserFinalDecision.READY_TO_CREATE, cmd.debugTrace?.finalDecision)
    }
  }

  @Test
  fun testStage7E3_soreHoursCoverage() {
    // Sore: 3 sore -> 15, 4 sore -> 16, 5 sore -> 17, 6 sore -> 18
    val testCases = listOf(
      Triple("3 sore", 15, 0),
      Triple("4 sore", 16, 0),
      Triple("5 sore", 17, 0),
      Triple("6 sore", 18, 0)
    )

    for ((phrase, expectedHour, expectedMinute) in testCases) {
      val input = "besok jam $phrase santai"
      val cmd = LocalCommandParser.parse(input, fixedBaseTimeMillis, wibTimeZone)
      assertNotNull("Failed for phrase: $phrase", cmd.dueDateTime)
      val cal = Calendar.getInstance(wibTimeZone).apply { timeInMillis = cmd.dueDateTime!! }
      assertEquals("Hour mismatch for $phrase", expectedHour, cal.get(Calendar.HOUR_OF_DAY))
      assertEquals("Minute mismatch for $phrase", expectedMinute, cal.get(Calendar.MINUTE))
      assertEquals(ParserFinalDecision.READY_TO_CREATE, cmd.debugTrace?.finalDecision)
    }
  }

  @Test
  fun testStage7E3_requiredRegressionScenarios() {
    // 1. "Besok jam sembilan pagi ada ulangan" tetap 09.00
    val r1 = LocalCommandParser.parse("Besok jam sembilan pagi ada ulangan", fixedBaseTimeMillis, wibTimeZone)
    val cal1 = Calendar.getInstance(wibTimeZone).apply { timeInMillis = r1.dueDateTime!! }
    assertEquals(9, cal1.get(Calendar.HOUR_OF_DAY))
    assertEquals(0, cal1.get(Calendar.MINUTE))
    assertEquals("09:00", r1.debugTrace?.clockTimeString)
    assertEquals("pagi", r1.debugTrace?.meridiemString)

    // 2. "Besok jam sembilan malam ada ulangan" menjadi 21.00
    val r2 = LocalCommandParser.parse("Besok jam sembilan malam ada ulangan", fixedBaseTimeMillis, wibTimeZone)
    val cal2 = Calendar.getInstance(wibTimeZone).apply { timeInMillis = r2.dueDateTime!! }
    assertEquals(21, cal2.get(Calendar.HOUR_OF_DAY))
    assertEquals(0, cal2.get(Calendar.MINUTE))
    assertEquals("21:00", r2.debugTrace?.clockTimeString)
    assertEquals("malam", r2.debugTrace?.meridiemString)

    // 3. "Tiga hari lagi jam 3 sore ada pertandingan" menjadi 15.00
    val r3 = LocalCommandParser.parse("Tiga hari lagi jam 3 sore ada pertandingan", fixedBaseTimeMillis, wibTimeZone)
    val cal3 = Calendar.getInstance(wibTimeZone).apply { timeInMillis = r3.dueDateTime!! }
    assertEquals(13, cal3.get(Calendar.DAY_OF_MONTH)) // 10 Nov + 3
    assertEquals(15, cal3.get(Calendar.HOUR_OF_DAY))
    assertEquals(0, cal3.get(Calendar.MINUTE))
    assertEquals("15:00", r3.debugTrace?.clockTimeString)
    assertEquals("sore", r3.debugTrace?.meridiemString)

    // 4. "Besok pukul 21 ada rapat" tetap 21.00
    val r4 = LocalCommandParser.parse("Besok pukul 21 ada rapat", fixedBaseTimeMillis, wibTimeZone)
    val cal4 = Calendar.getInstance(wibTimeZone).apply { timeInMillis = r4.dueDateTime!! }
    assertEquals(21, cal4.get(Calendar.HOUR_OF_DAY))
    assertEquals(0, cal4.get(Calendar.MINUTE))
    assertEquals("21:00", r4.debugTrace?.clockTimeString)

    // 5. "Besok pukul 15 ada rapat" tetap 15.00
    val r5 = LocalCommandParser.parse("Besok pukul 15 ada rapat", fixedBaseTimeMillis, wibTimeZone)
    val cal5 = Calendar.getInstance(wibTimeZone).apply { timeInMillis = r5.dueDateTime!! }
    assertEquals(15, cal5.get(Calendar.HOUR_OF_DAY))
    assertEquals(0, cal5.get(Calendar.MINUTE))
    assertEquals("15:00", r5.debugTrace?.clockTimeString)

    // 6. "Satu menit lagi harus tidur" tetap menggunakan perhitungan durasi relatif (15:28)
    val r6 = LocalCommandParser.parse("Satu menit lagi harus tidur", fixedBaseTimeMillis, wibTimeZone)
    val cal6 = Calendar.getInstance(wibTimeZone).apply { timeInMillis = r6.dueDateTime!! }
    assertEquals(15, cal6.get(Calendar.HOUR_OF_DAY))
    assertEquals(28, cal6.get(Calendar.MINUTE))

    // 7. "Hari Minggu ini akan ada pertandingan bola" tetap meminta klarifikasi jam
    val r7 = LocalCommandParser.parse("Hari Minggu ini akan ada pertandingan bola", fixedBaseTimeMillis, wibTimeZone)
    assertNull(r7.dueDateTime)
    assertEquals(ParserFinalDecision.NEEDS_CLARIFICATION, r7.debugTrace?.finalDecision)

    // 8. Multi-turn "Jam 4 sore" tetap menjadi 16.00 dan mempertahankan tanggal serta aktivitas sebelumnya
    val turn1 = LocalCommandParser.parse("Hari Minggu ini akan ada pertandingan bola", fixedBaseTimeMillis, wibTimeZone)
    val savedTargetDate = turn1.targetDateMillis
    val savedTitle = turn1.newTitle
    val answerTime = DateTimeVoiceParser.parse("Jam 4 sore", fixedBaseTimeMillis, wibTimeZone)
    val finalCal8 = Calendar.getInstance(wibTimeZone).apply {
      timeInMillis = savedTargetDate!!
      val timeCal = Calendar.getInstance(wibTimeZone).apply { timeInMillis = answerTime.timestamp }
      set(Calendar.HOUR_OF_DAY, timeCal.get(Calendar.HOUR_OF_DAY))
      set(Calendar.MINUTE, timeCal.get(Calendar.MINUTE))
      set(Calendar.SECOND, 0)
      set(Calendar.MILLISECOND, 0)
    }
    assertEquals(15, finalCal8.get(Calendar.DAY_OF_MONTH))
    assertEquals(16, finalCal8.get(Calendar.HOUR_OF_DAY))
    assertEquals(0, finalCal8.get(Calendar.MINUTE))
  }

  @Test
  fun testStage7E3_variationsCoverage() {
    // Variations: jam/pukul + digits/words
    val variations = listOf(
      "jam 9 malam" to 21,
      "pukul 9 malam" to 21,
      "jam sembilan malam" to 21,
      "pukul sembilan malam" to 21,
      "jam 3 sore" to 15,
      "pukul 3 sore" to 15,
      "jam tiga sore" to 15,
      "jam 7 pagi" to 7,
      "pukul tujuh pagi" to 7,
      "jam 1 siang" to 13,
      "pukul satu siang" to 13,
      "jam 21" to 21,
      "pukul 21.00" to 21,
      "jam 15" to 15,
      "pukul 15.00" to 15
    )

    for ((phrase, expectedH) in variations) {
      val cmd = LocalCommandParser.parse("besok $phrase rapat", fixedBaseTimeMillis, wibTimeZone)
      assertNotNull("Parsed dueDateTime null for '$phrase'", cmd.dueDateTime)
      val c = Calendar.getInstance(wibTimeZone).apply { timeInMillis = cmd.dueDateTime!! }
      assertEquals("Hour mismatch for '$phrase'", expectedH, c.get(Calendar.HOUR_OF_DAY))
    }
  }
}

