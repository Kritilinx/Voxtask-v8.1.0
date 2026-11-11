package com.example.voice

import com.example.data.model.Priority
import com.example.data.model.RepeatMode
import com.example.voice.parser.CommandIntent
import com.example.voice.parser.LocalCommandParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class LocalCommandParserTest {

  @Test
  fun testConfirmations() {
    val yesCommand = LocalCommandParser.parse("ya")
    assertEquals(CommandIntent.CONFIRM_YES, yesCommand.intent)

    val okCommand = LocalCommandParser.parse("oke")
    assertEquals(CommandIntent.CONFIRM_YES, okCommand.intent)

    val noCommand = LocalCommandParser.parse("tidak")
    assertEquals(CommandIntent.CONFIRM_NO, noCommand.intent)

    val cancelCommand = LocalCommandParser.parse("batal")
    assertEquals(CommandIntent.CONFIRM_NO, cancelCommand.intent)
  }

  @Test
  fun testViewAgendaAndNextReminder() {
    val todayCommand = LocalCommandParser.parse("lihat agenda hari ini")
    assertEquals(CommandIntent.VIEW_TODAY_AGENDA, todayCommand.intent)

    val nextCommand = LocalCommandParser.parse("apa pengingat berikutnya")
    assertEquals(CommandIntent.VIEW_NEXT_REMINDER, nextCommand.intent)
  }

  @Test
  fun testCreateReminderCommand() {
    val cal = Calendar.getInstance().apply {
      set(2026, Calendar.MARCH, 15, 10, 0, 0)
    }
    val baseMillis = cal.timeInMillis

    val cmd = LocalCommandParser.parse("ingatkan saya rapat tim besok jam 2 siang", baseMillis)
    assertEquals(CommandIntent.CREATE_REMINDER, cmd.intent)
    assertTrue(cmd.newTitle.contains("rapat tim", ignoreCase = true))
    assertNotNull(cmd.dueDateTime)
  }

  @Test
  fun testCompleteAndDeleteCommands() {
    val completeCmd = LocalCommandParser.parse("selesaikan tugas beli susu")
    assertEquals(CommandIntent.COMPLETE_REMINDER, completeCmd.intent)
    assertEquals("beli susu", completeCmd.targetQuery)

    val deleteCmd = LocalCommandParser.parse("hapus pengingat rapat tim")
    assertEquals(CommandIntent.DELETE_REMINDER, deleteCmd.intent)
    assertEquals("rapat tim", deleteCmd.targetQuery)
  }

  @Test
  fun testEmptyOrClarificationCommand() {
    val emptyCmd = LocalCommandParser.parse("   ")
    assertEquals(CommandIntent.UNKNOWN, emptyCmd.intent)

    val singleWordCmd = LocalCommandParser.parse("halo")
    assertEquals(CommandIntent.CLARIFICATION, singleWordCmd.intent)
  }
}
