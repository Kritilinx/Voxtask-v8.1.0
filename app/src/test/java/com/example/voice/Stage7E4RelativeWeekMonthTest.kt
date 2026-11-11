package com.example.voice

import com.example.voice.parser.CommandIntent
import com.example.voice.parser.DateTimeVoiceParser
import com.example.voice.parser.LocalCommandParser
import com.example.voice.parser.ParserFinalDecision
import com.example.voice.parser.RelativeDateUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * STAGE 7E.4 — Relative Week & Month Calculation Tests.
 * Fixed testing clock: 10 November 2026 pukul 21.42 WIB.
 */
class Stage7E4RelativeWeekMonthTest {

  private val wibTimeZone: TimeZone = TimeZone.getTimeZone("Asia/Jakarta")

  /** Fixed clock: 10 November 2026 21:42:00 WIB */
  private val fixedBaseTimeMillis: Long = Calendar.getInstance(wibTimeZone).apply {
    set(2026, Calendar.NOVEMBER, 10, 21, 42, 0)
    set(Calendar.MILLISECOND, 0)
  }.timeInMillis

  private fun printTrace(testName: String, input: String, cmd: com.example.voice.parser.ParsedCommand) {
    val trace = cmd.debugTrace
    println("==========================================================================")
    println("TRACE [$testName] Input: \"$input\"")
    if (trace != null) {
      println("  Intent: ${trace.intent}")
      println("  Activity: ${trace.activityTitle}")
      println("  Relative Date Unit: ${trace.relativeDateUnit}")
      println("  Relative Date Amount: ${trace.relativeDateAmount}")
      println("  Relative Date Expression: ${trace.relativeDateExpression}")
      println("  Target Date: ${trace.targetDateFormatted}")
      println("  Clock Time: ${trace.clockTimeString}")
      println("  Missing Fields: ${if (trace.missingFields.isEmpty()) "None" else trace.missingFields.joinToString()}")
      println("  Final Decision: ${trace.finalDecision}")
    }
    println("==========================================================================")
  }

  // =========================================================================
  // 1. PENGUJIAN MINGGU
  // =========================================================================

  // Test 1: "1 minggu lagi ada rapat" -> 17 November, meminta jam
  @Test
  fun test01_satuMingguLagiAdaRapat_numeric() {
    val input = "1 minggu lagi ada rapat"
    val cmd = LocalCommandParser.parse(input, fixedBaseTimeMillis, wibTimeZone)
    printTrace("Test 1: 1 minggu lagi ada rapat", input, cmd)

    assertEquals(CommandIntent.CREATE_REMINDER, cmd.intent)
    assertTrue("Title should contain rapat", cmd.newTitle.contains("Rapat", ignoreCase = true))
    assertNull("Clock time is missing, dueDateTime must be null", cmd.dueDateTime)

    val trace = cmd.debugTrace
    assertNotNull(trace)
    assertEquals(RelativeDateUnit.WEEK, trace!!.relativeDateUnit)
    assertEquals(1, trace.relativeDateAmount)
    assertEquals("1 minggu lagi", trace.relativeDateExpression)
    assertEquals("17 November 2026", trace.targetDateFormatted)
    assertEquals("Not provided", trace.clockTimeString)
    assertTrue(trace.missingFields.contains("Clock Time"))
    assertEquals(ParserFinalDecision.NEEDS_CLARIFICATION, trace.finalDecision)
    assertTrue(trace.isPendingClarification)
  }

  // Test 2: "satu minggu lagi ada rapat" -> 17 November, meminta jam
  @Test
  fun test02_satuMingguLagiAdaRapat_words() {
    val input = "satu minggu lagi ada rapat"
    val cmd = LocalCommandParser.parse(input, fixedBaseTimeMillis, wibTimeZone)
    printTrace("Test 2: satu minggu lagi ada rapat", input, cmd)

    assertEquals(CommandIntent.CREATE_REMINDER, cmd.intent)
    assertTrue(cmd.newTitle.contains("Rapat", ignoreCase = true))
    assertNull(cmd.dueDateTime)

    val trace = cmd.debugTrace
    assertNotNull(trace)
    assertEquals(RelativeDateUnit.WEEK, trace!!.relativeDateUnit)
    assertEquals(1, trace.relativeDateAmount)
    assertEquals("1 minggu lagi", trace.relativeDateExpression)
    assertEquals("17 November 2026", trace.targetDateFormatted)
    assertEquals("Not provided", trace.clockTimeString)
    assertTrue(trace.missingFields.contains("Clock Time"))
    assertEquals(ParserFinalDecision.NEEDS_CLARIFICATION, trace.finalDecision)
  }

  // Test 3: "seminggu lagi ada rapat" -> 17 November, meminta jam
  @Test
  fun test03_semingguLagiAdaRapat() {
    val input = "seminggu lagi ada rapat"
    val cmd = LocalCommandParser.parse(input, fixedBaseTimeMillis, wibTimeZone)
    printTrace("Test 3: seminggu lagi ada rapat", input, cmd)

    assertEquals(CommandIntent.CREATE_REMINDER, cmd.intent)
    assertTrue(cmd.newTitle.contains("Rapat", ignoreCase = true))
    assertNull(cmd.dueDateTime)

    val trace = cmd.debugTrace
    assertNotNull(trace)
    assertEquals(RelativeDateUnit.WEEK, trace!!.relativeDateUnit)
    assertEquals(1, trace.relativeDateAmount)
    assertEquals("seminggu lagi", trace.relativeDateExpression)
    assertEquals("17 November 2026", trace.targetDateFormatted)
    assertEquals(ParserFinalDecision.NEEDS_CLARIFICATION, trace.finalDecision)
  }

  // Test 4: "2 minggu lagi jam 9 pagi ada rapat" -> 24 November, 09.00
  @Test
  fun test04_duaMingguLagiJam9PagiAdaRapat() {
    val input = "2 minggu lagi jam 9 pagi ada rapat"
    val cmd = LocalCommandParser.parse(input, fixedBaseTimeMillis, wibTimeZone)
    printTrace("Test 4: 2 minggu lagi jam 9 pagi ada rapat", input, cmd)

    assertEquals(CommandIntent.CREATE_REMINDER, cmd.intent)
    assertTrue(cmd.newTitle.contains("Rapat", ignoreCase = true))
    assertNotNull(cmd.dueDateTime)

    val cal = Calendar.getInstance(wibTimeZone).apply { timeInMillis = cmd.dueDateTime!! }
    assertEquals(2026, cal.get(Calendar.YEAR))
    assertEquals(Calendar.NOVEMBER, cal.get(Calendar.MONTH))
    assertEquals(24, cal.get(Calendar.DAY_OF_MONTH))
    assertEquals(9, cal.get(Calendar.HOUR_OF_DAY))
    assertEquals(0, cal.get(Calendar.MINUTE))

    val trace = cmd.debugTrace
    assertNotNull(trace)
    assertEquals(RelativeDateUnit.WEEK, trace!!.relativeDateUnit)
    assertEquals(2, trace.relativeDateAmount)
    assertEquals("2 minggu lagi", trace.relativeDateExpression)
    assertEquals("24 November 2026", trace.targetDateFormatted)
    assertEquals("09:00", trace.clockTimeString)
    assertTrue(trace.missingFields.isEmpty())
    assertEquals(ParserFinalDecision.READY_TO_CREATE, trace.finalDecision)
  }

  // Test 5: "dua minggu dari sekarang jam 10 pagi rapat" -> 24 November, 10.00
  @Test
  fun test05_duaMingguDariSekarangJam10PagiRapat() {
    val input = "dua minggu dari sekarang jam 10 pagi rapat"
    val cmd = LocalCommandParser.parse(input, fixedBaseTimeMillis, wibTimeZone)
    printTrace("Test 5: dua minggu dari sekarang jam 10 pagi rapat", input, cmd)

    assertEquals(CommandIntent.CREATE_REMINDER, cmd.intent)
    assertEquals("Rapat", cmd.newTitle)
    assertNotNull(cmd.dueDateTime)

    val cal = Calendar.getInstance(wibTimeZone).apply { timeInMillis = cmd.dueDateTime!! }
    assertEquals(2026, cal.get(Calendar.YEAR))
    assertEquals(Calendar.NOVEMBER, cal.get(Calendar.MONTH))
    assertEquals(24, cal.get(Calendar.DAY_OF_MONTH))
    assertEquals(10, cal.get(Calendar.HOUR_OF_DAY))
    assertEquals(0, cal.get(Calendar.MINUTE))

    val trace = cmd.debugTrace
    assertNotNull(trace)
    assertEquals(RelativeDateUnit.WEEK, trace!!.relativeDateUnit)
    assertEquals(2, trace.relativeDateAmount)
    assertEquals("2 minggu dari sekarang", trace.relativeDateExpression)
    assertEquals("24 November 2026", trace.targetDateFormatted)
    assertEquals("10:00", trace.clockTimeString)
    assertEquals(ParserFinalDecision.READY_TO_CREATE, trace.finalDecision)
  }

  // Test 6: "3 minggu lagi harus minum" -> 1 Desember, meminta jam
  @Test
  fun test06_tigaMingguLagiHarusMinum() {
    val input = "3 minggu lagi harus minum"
    val cmd = LocalCommandParser.parse(input, fixedBaseTimeMillis, wibTimeZone)
    printTrace("Test 6: 3 minggu lagi harus minum", input, cmd)

    assertEquals(CommandIntent.CREATE_REMINDER, cmd.intent)
    assertEquals("Minum", cmd.newTitle)
    assertNull(cmd.dueDateTime)

    val trace = cmd.debugTrace
    assertNotNull(trace)
    assertEquals(RelativeDateUnit.WEEK, trace!!.relativeDateUnit)
    assertEquals(3, trace.relativeDateAmount)
    assertEquals("3 minggu lagi", trace.relativeDateExpression)
    assertEquals("1 Desember 2026", trace.targetDateFormatted)
    assertEquals("Not provided", trace.clockTimeString)
    assertTrue(trace.missingFields.contains("Clock Time"))
    assertEquals(ParserFinalDecision.NEEDS_CLARIFICATION, trace.finalDecision)
  }

  // =========================================================================
  // 2. PENGUJIAN BULAN
  // =========================================================================

  // Test 7: "1 bulan lagi ada pembayaran" -> 10 Desember, meminta jam
  @Test
  fun test07_satuBulanLagiAdaPembayaran() {
    val input = "1 bulan lagi ada pembayaran"
    val cmd = LocalCommandParser.parse(input, fixedBaseTimeMillis, wibTimeZone)
    printTrace("Test 7: 1 bulan lagi ada pembayaran", input, cmd)

    assertEquals(CommandIntent.CREATE_REMINDER, cmd.intent)
    assertTrue(cmd.newTitle.contains("Pembayaran", ignoreCase = true))
    assertNull(cmd.dueDateTime)

    val trace = cmd.debugTrace
    assertNotNull(trace)
    assertEquals(RelativeDateUnit.MONTH, trace!!.relativeDateUnit)
    assertEquals(1, trace.relativeDateAmount)
    assertEquals("1 bulan lagi", trace.relativeDateExpression)
    assertEquals("10 Desember 2026", trace.targetDateFormatted)
    assertEquals(ParserFinalDecision.NEEDS_CLARIFICATION, trace.finalDecision)
  }

  // Test 8: "sebulan lagi ada pembayaran" -> 10 Desember, meminta jam
  @Test
  fun test08_sebulanLagiAdaPembayaran() {
    val input = "sebulan lagi ada pembayaran"
    val cmd = LocalCommandParser.parse(input, fixedBaseTimeMillis, wibTimeZone)
    printTrace("Test 8: sebulan lagi ada pembayaran", input, cmd)

    assertEquals(CommandIntent.CREATE_REMINDER, cmd.intent)
    assertTrue(cmd.newTitle.contains("Pembayaran", ignoreCase = true))
    assertNull(cmd.dueDateTime)

    val trace = cmd.debugTrace
    assertNotNull(trace)
    assertEquals(RelativeDateUnit.MONTH, trace!!.relativeDateUnit)
    assertEquals(1, trace.relativeDateAmount)
    assertEquals("sebulan lagi", trace.relativeDateExpression)
    assertEquals("10 Desember 2026", trace.targetDateFormatted)
    assertEquals(ParserFinalDecision.NEEDS_CLARIFICATION, trace.finalDecision)
  }

  // Test 9: "2 bulan lagi jam 10 pagi bayar listrik" -> 10 Januari 2027, 10.00
  @Test
  fun test09_duaBulanLagiJam10PagiBayarListrik() {
    val input = "2 bulan lagi jam 10 pagi bayar listrik"
    val cmd = LocalCommandParser.parse(input, fixedBaseTimeMillis, wibTimeZone)
    printTrace("Test 9: 2 bulan lagi jam 10 pagi bayar listrik", input, cmd)

    assertEquals(CommandIntent.CREATE_REMINDER, cmd.intent)
    assertEquals("Bayar listrik", cmd.newTitle)
    assertNotNull(cmd.dueDateTime)

    val cal = Calendar.getInstance(wibTimeZone).apply { timeInMillis = cmd.dueDateTime!! }
    assertEquals(2027, cal.get(Calendar.YEAR))
    assertEquals(Calendar.JANUARY, cal.get(Calendar.MONTH))
    assertEquals(10, cal.get(Calendar.DAY_OF_MONTH))
    assertEquals(10, cal.get(Calendar.HOUR_OF_DAY))
    assertEquals(0, cal.get(Calendar.MINUTE))

    val trace = cmd.debugTrace
    assertNotNull(trace)
    assertEquals(RelativeDateUnit.MONTH, trace!!.relativeDateUnit)
    assertEquals(2, trace.relativeDateAmount)
    assertEquals("2 bulan lagi", trace.relativeDateExpression)
    assertEquals("10 Januari 2027", trace.targetDateFormatted)
    assertEquals("10:00", trace.clockTimeString)
    assertEquals(ParserFinalDecision.READY_TO_CREATE, trace.finalDecision)
  }

  // Test 10: "dua bulan lagi harus minum" -> 10 Januari 2027, meminta jam
  @Test
  fun test10_duaBulanLagiHarusMinum() {
    val input = "dua bulan lagi harus minum"
    val cmd = LocalCommandParser.parse(input, fixedBaseTimeMillis, wibTimeZone)
    printTrace("Test 10: dua bulan lagi harus minum", input, cmd)

    assertEquals(CommandIntent.CREATE_REMINDER, cmd.intent)
    assertEquals("Minum", cmd.newTitle)
    assertNull(cmd.dueDateTime)

    val trace = cmd.debugTrace
    assertNotNull(trace)
    assertEquals(RelativeDateUnit.MONTH, trace!!.relativeDateUnit)
    assertEquals(2, trace.relativeDateAmount)
    assertEquals("2 bulan lagi", trace.relativeDateExpression)
    assertEquals("10 Januari 2027", trace.targetDateFormatted)
    assertEquals(ParserFinalDecision.NEEDS_CLARIFICATION, trace.finalDecision)
  }

  // Test 11: "3 bulan lagi jam 8 malam ada acara" -> 10 Februari 2027, 20.00
  @Test
  fun test11_tigaBulanLagiJam8MalamAdaAcara() {
    val input = "3 bulan lagi jam 8 malam ada acara"
    val cmd = LocalCommandParser.parse(input, fixedBaseTimeMillis, wibTimeZone)
    printTrace("Test 11: 3 bulan lagi jam 8 malam ada acara", input, cmd)

    assertEquals(CommandIntent.CREATE_REMINDER, cmd.intent)
    assertEquals("Acara", cmd.newTitle)
    assertNotNull(cmd.dueDateTime)

    val cal = Calendar.getInstance(wibTimeZone).apply { timeInMillis = cmd.dueDateTime!! }
    assertEquals(2027, cal.get(Calendar.YEAR))
    assertEquals(Calendar.FEBRUARY, cal.get(Calendar.MONTH))
    assertEquals(10, cal.get(Calendar.DAY_OF_MONTH))
    assertEquals(20, cal.get(Calendar.HOUR_OF_DAY))
    assertEquals(0, cal.get(Calendar.MINUTE))

    val trace = cmd.debugTrace
    assertNotNull(trace)
    assertEquals(RelativeDateUnit.MONTH, trace!!.relativeDateUnit)
    assertEquals(3, trace.relativeDateAmount)
    assertEquals("3 bulan lagi", trace.relativeDateExpression)
    assertEquals("10 Februari 2027", trace.targetDateFormatted)
    assertEquals("20:00", trace.clockTimeString)
    assertEquals(ParserFinalDecision.READY_TO_CREATE, trace.finalDecision)
  }

  // Test 12: "setahun lagi jam 9 pagi ada acara" -> 10 November 2027, 09.00
  @Test
  fun test12_setahunLagiJam9PagiAdaAcara() {
    val input = "setahun lagi jam 9 pagi ada acara"
    val cmd = LocalCommandParser.parse(input, fixedBaseTimeMillis, wibTimeZone)
    printTrace("Test 12: setahun lagi jam 9 pagi ada acara", input, cmd)

    assertEquals(CommandIntent.CREATE_REMINDER, cmd.intent)
    assertEquals("Acara", cmd.newTitle)
    assertNotNull(cmd.dueDateTime)

    val cal = Calendar.getInstance(wibTimeZone).apply { timeInMillis = cmd.dueDateTime!! }
    assertEquals(2027, cal.get(Calendar.YEAR))
    assertEquals(Calendar.NOVEMBER, cal.get(Calendar.MONTH))
    assertEquals(10, cal.get(Calendar.DAY_OF_MONTH))
    assertEquals(9, cal.get(Calendar.HOUR_OF_DAY))
    assertEquals(0, cal.get(Calendar.MINUTE))

    val trace = cmd.debugTrace
    assertNotNull(trace)
    assertEquals(RelativeDateUnit.MONTH, trace!!.relativeDateUnit)
    assertEquals(12, trace.relativeDateAmount)
    assertEquals("setahun lagi", trace.relativeDateExpression)
    assertEquals("10 November 2027", trace.targetDateFormatted)
    assertEquals("09:00", trace.clockTimeString)
    assertEquals(ParserFinalDecision.READY_TO_CREATE, trace.finalDecision)
  }

  // =========================================================================
  // 3. PENGUJIAN AKHIR BULAN (MONTH-END EDGE CASES)
  // =========================================================================

  // Test 13: 31 Januari 2027 + 1 bulan = 28 Februari 2027
  @Test
  fun test13_endOfMonth_31JanPlus1Month() {
    val jan31Time = Calendar.getInstance(wibTimeZone).apply {
      set(2027, Calendar.JANUARY, 31, 10, 0, 0)
      set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    val cmd = LocalCommandParser.parse("1 bulan lagi bayar tagihan", jan31Time, wibTimeZone)
    val cal = Calendar.getInstance(wibTimeZone).apply { timeInMillis = cmd.targetDateMillis!! }
    assertEquals(2027, cal.get(Calendar.YEAR))
    assertEquals(Calendar.FEBRUARY, cal.get(Calendar.MONTH))
    assertEquals(28, cal.get(Calendar.DAY_OF_MONTH))
    assertEquals("28 Februari 2027", cmd.debugTrace?.targetDateFormatted)
  }

  // Test 14: 31 Januari 2027 + 2 bulan = 31 Maret 2027
  @Test
  fun test14_endOfMonth_31JanPlus2Months() {
    val jan31Time = Calendar.getInstance(wibTimeZone).apply {
      set(2027, Calendar.JANUARY, 31, 10, 0, 0)
      set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    val cmd = LocalCommandParser.parse("2 bulan lagi bayar tagihan", jan31Time, wibTimeZone)
    val cal = Calendar.getInstance(wibTimeZone).apply { timeInMillis = cmd.targetDateMillis!! }
    assertEquals(2027, cal.get(Calendar.YEAR))
    assertEquals(Calendar.MARCH, cal.get(Calendar.MONTH))
    assertEquals(31, cal.get(Calendar.DAY_OF_MONTH))
    assertEquals("31 Maret 2027", cmd.debugTrace?.targetDateFormatted)
  }

  // Test 15: 30 November 2026 + 3 bulan = 28 Februari 2027
  @Test
  fun test15_endOfMonth_30NovPlus3Months() {
    val nov30Time = Calendar.getInstance(wibTimeZone).apply {
      set(2026, Calendar.NOVEMBER, 30, 10, 0, 0)
      set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    val cmd = LocalCommandParser.parse("3 bulan lagi bayar tagihan", nov30Time, wibTimeZone)
    val cal = Calendar.getInstance(wibTimeZone).apply { timeInMillis = cmd.targetDateMillis!! }
    assertEquals(2027, cal.get(Calendar.YEAR))
    assertEquals(Calendar.FEBRUARY, cal.get(Calendar.MONTH))
    assertEquals(28, cal.get(Calendar.DAY_OF_MONTH))
    assertEquals("28 Februari 2027", cmd.debugTrace?.targetDateFormatted)
  }

  // Test 16: Tahun kabisat: 31 Januari 2028 + 1 bulan = 29 Februari 2028
  @Test
  fun test16_leapYear_31Jan2028Plus1Month() {
    val leapJan31 = Calendar.getInstance(wibTimeZone).apply {
      set(2028, Calendar.JANUARY, 31, 10, 0, 0)
      set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    val cmd = LocalCommandParser.parse("1 bulan lagi bayar tagihan", leapJan31, wibTimeZone)
    val cal = Calendar.getInstance(wibTimeZone).apply { timeInMillis = cmd.targetDateMillis!! }
    assertEquals(2028, cal.get(Calendar.YEAR))
    assertEquals(Calendar.FEBRUARY, cal.get(Calendar.MONTH))
    assertEquals(29, cal.get(Calendar.DAY_OF_MONTH))
    assertEquals("29 Februari 2028", cmd.debugTrace?.targetDateFormatted)
  }

  // =========================================================================
  // 4. REGRESSION TESTS
  // =========================================================================

  // Test 17: "1 menit lagi harus tidur" tetap benar
  @Test
  fun test17_regression_satuMenitLagiHarusTidur() {
    val input = "1 menit lagi harus tidur"
    val cmd = LocalCommandParser.parse(input, fixedBaseTimeMillis, wibTimeZone)
    assertEquals(CommandIntent.CREATE_REMINDER, cmd.intent)
    assertEquals("Tidur", cmd.newTitle)
    assertNotNull(cmd.dueDateTime)

    val cal = Calendar.getInstance(wibTimeZone).apply { timeInMillis = cmd.dueDateTime!! }
    assertEquals(21, cal.get(Calendar.HOUR_OF_DAY))
    assertEquals(43, cal.get(Calendar.MINUTE))
    assertEquals(ParserFinalDecision.READY_TO_CREATE, cmd.debugTrace?.finalDecision)
  }

  // Test 18: "5 jam lagi harus tidur" tetap benar
  @Test
  fun test18_regression_limaJamLagiHarusTidur() {
    val input = "5 jam lagi harus tidur"
    val cmd = LocalCommandParser.parse(input, fixedBaseTimeMillis, wibTimeZone)
    assertEquals(CommandIntent.CREATE_REMINDER, cmd.intent)
    assertEquals("Tidur", cmd.newTitle)
    assertNotNull(cmd.dueDateTime)

    val cal = Calendar.getInstance(wibTimeZone).apply { timeInMillis = cmd.dueDateTime!! }
    assertEquals(11, cal.get(Calendar.DAY_OF_MONTH))
    assertEquals(2, cal.get(Calendar.HOUR_OF_DAY))
    assertEquals(42, cal.get(Calendar.MINUTE))
    assertEquals(ParserFinalDecision.READY_TO_CREATE, cmd.debugTrace?.finalDecision)
  }

  // Test 19: "Besok jam 9 pagi ada rapat" tetap benar
  @Test
  fun test19_regression_besokJam9PagiAdaRapat() {
    val input = "Besok jam 9 pagi ada rapat"
    val cmd = LocalCommandParser.parse(input, fixedBaseTimeMillis, wibTimeZone)
    assertEquals(CommandIntent.CREATE_REMINDER, cmd.intent)
    assertTrue(cmd.newTitle.contains("Rapat", ignoreCase = true))
    assertNotNull(cmd.dueDateTime)

    val cal = Calendar.getInstance(wibTimeZone).apply { timeInMillis = cmd.dueDateTime!! }
    assertEquals(11, cal.get(Calendar.DAY_OF_MONTH))
    assertEquals(9, cal.get(Calendar.HOUR_OF_DAY))
    assertEquals(0, cal.get(Calendar.MINUTE))
    assertEquals(ParserFinalDecision.READY_TO_CREATE, cmd.debugTrace?.finalDecision)
  }

  // Test 20: "Tiga hari lagi jam 9 malam ada ulangan" tetap benar
  @Test
  fun test20_regression_tigaHariLagiJam9MalamAdaUlangan() {
    val input = "Tiga hari lagi jam 9 malam ada ulangan"
    val cmd = LocalCommandParser.parse(input, fixedBaseTimeMillis, wibTimeZone)
    assertEquals(CommandIntent.CREATE_REMINDER, cmd.intent)
    assertEquals("Ada ulangan", cmd.newTitle)
    assertNotNull(cmd.dueDateTime)

    val cal = Calendar.getInstance(wibTimeZone).apply { timeInMillis = cmd.dueDateTime!! }
    assertEquals(13, cal.get(Calendar.DAY_OF_MONTH))
    assertEquals(21, cal.get(Calendar.HOUR_OF_DAY))
    assertEquals(0, cal.get(Calendar.MINUTE))
    assertEquals(ParserFinalDecision.READY_TO_CREATE, cmd.debugTrace?.finalDecision)
  }

  // Test 21: "Hari Minggu ini ada pertandingan bola" tetap meminta jam
  @Test
  fun test21_regression_hariMingguIniAdaPertandinganBola() {
    val input = "Hari Minggu ini ada pertandingan bola"
    val cmd = LocalCommandParser.parse(input, fixedBaseTimeMillis, wibTimeZone)
    assertEquals(CommandIntent.CREATE_REMINDER, cmd.intent)
    assertEquals("Pertandingan bola", cmd.newTitle)
    assertNull(cmd.dueDateTime)

    assertEquals("15 November 2026", cmd.debugTrace?.targetDateFormatted)
    assertEquals(ParserFinalDecision.NEEDS_CLARIFICATION, cmd.debugTrace?.finalDecision)
  }

  // Test 22: Klarifikasi multi-turn tetap mempertahankan tanggal dan aktivitas
  @Test
  fun test22_regression_multiTurnClarification() {
    // Turn 1: "2 bulan lagi ada pembayaran"
    val turn1 = LocalCommandParser.parse("2 bulan lagi ada pembayaran", fixedBaseTimeMillis, wibTimeZone)
    assertEquals(ParserFinalDecision.NEEDS_CLARIFICATION, turn1.debugTrace?.finalDecision)
    val savedTargetDate = turn1.targetDateMillis
    val savedTitle = turn1.newTitle
    assertNotNull(savedTargetDate)

    // Turn 2: User answers "Jam 9 pagi"
    val turn2Answer = "Jam 9 pagi"
    val timeParsed = DateTimeVoiceParser.parse(turn2Answer, fixedBaseTimeMillis, wibTimeZone)
    assertTrue(timeParsed.hasExplicitTime)

    val finalCal = Calendar.getInstance(wibTimeZone).apply {
      timeInMillis = savedTargetDate!!
      val timeCal = Calendar.getInstance(wibTimeZone).apply { timeInMillis = timeParsed.timestamp }
      set(Calendar.HOUR_OF_DAY, timeCal.get(Calendar.HOUR_OF_DAY))
      set(Calendar.MINUTE, timeCal.get(Calendar.MINUTE))
      set(Calendar.SECOND, 0)
      set(Calendar.MILLISECOND, 0)
    }

    assertEquals(2027, finalCal.get(Calendar.YEAR))
    assertEquals(Calendar.JANUARY, finalCal.get(Calendar.MONTH))
    assertEquals(10, finalCal.get(Calendar.DAY_OF_MONTH))
    assertEquals(9, finalCal.get(Calendar.HOUR_OF_DAY))
    assertEquals(0, finalCal.get(Calendar.MINUTE))
    assertTrue("Activity must be preserved", savedTitle.contains("Pembayaran", ignoreCase = true))
  }

  // Test 23: Format jam 24 jam tetap benar
  @Test
  fun test23_regression_formatJam24Jam() {
    val input = "besok pukul 14.30 rapat evaluasi"
    val cmd = LocalCommandParser.parse(input, fixedBaseTimeMillis, wibTimeZone)
    assertNotNull(cmd.dueDateTime)

    val cal = Calendar.getInstance(wibTimeZone).apply { timeInMillis = cmd.dueDateTime!! }
    assertEquals(14, cal.get(Calendar.HOUR_OF_DAY))
    assertEquals(30, cal.get(Calendar.MINUTE))
    assertEquals("14:30", cmd.debugTrace?.clockTimeString)
    assertEquals(ParserFinalDecision.READY_TO_CREATE, cmd.debugTrace?.finalDecision)
  }

  // Test 24: Konversi pagi, siang, sore, malam tetap benar
  @Test
  fun test24_regression_meridiemConversion() {
    val testCases = listOf(
      Pair("jam 8 pagi", 8),
      Pair("jam 1 siang", 13),
      Pair("jam 4 sore", 16),
      Pair("jam 8 malam", 20)
    )

    for ((timePhrase, expectedH) in testCases) {
      val cmd = LocalCommandParser.parse("besok $timePhrase ada agenda", fixedBaseTimeMillis, wibTimeZone)
      assertNotNull("Parsed dueDateTime null for $timePhrase", cmd.dueDateTime)
      val cal = Calendar.getInstance(wibTimeZone).apply { timeInMillis = cmd.dueDateTime!! }
      assertEquals("Hour mismatch for $timePhrase", expectedH, cal.get(Calendar.HOUR_OF_DAY))
      assertEquals(0, cal.get(Calendar.MINUTE))
    }
  }
}
