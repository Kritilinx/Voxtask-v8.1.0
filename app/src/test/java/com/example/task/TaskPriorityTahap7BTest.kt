package com.example.task

import com.example.ai.AiCommandIntent
import com.example.ai.AiResponseValidator
import com.example.data.local.Converters
import com.example.data.local.entity.TaskEntity
import com.example.data.model.Priority
import com.example.voice.parser.CommandIntent
import com.example.voice.parser.LocalCommandParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tahap 7B verification unit tests (pure JVM, no Android runtime / Robolectric dependency).
 * Verifies legacy TaskEntity priority preservation, default priority on new tasks,
 * Room Converters compatibility, and AI/local parser non-regression.
 */
class TaskPriorityTahap7BTest {

  private val converters = Converters()

  @Test
  fun createNewTaskWithoutSelectingPriority_usesCompatibleDefault() {
    val newTask = TaskEntity(
      title = "Susun laporan mingguan",
      description = "Ringkasan progres proyek"
    )

    assertEquals("Susun laporan mingguan", newTask.title)
    assertEquals(Priority.MEDIUM, newTask.priority)
    assertFalse(newTask.isCompleted)
  }

  @Test
  fun legacyTasksWithAllPriorityLevels_canBeReadAndEditedWithoutCorruptingPriority() {
    val legacyLow = TaskEntity(id = 1L, title = "Task Low", priority = Priority.LOW)
    val legacyMedium = TaskEntity(id = 2L, title = "Task Medium", priority = Priority.MEDIUM)
    val legacyHigh = TaskEntity(id = 3L, title = "Task High", priority = Priority.HIGH)

    // Edit title & description without touching priority
    val editedLow = legacyLow.copy(title = "Task Low Edited", description = "Updated")
    val editedMedium = legacyMedium.copy(title = "Task Medium Edited")
    val editedHigh = legacyHigh.copy(isCompleted = true)

    assertEquals(Priority.LOW, editedLow.priority)
    assertEquals(Priority.MEDIUM, editedMedium.priority)
    assertEquals(Priority.HIGH, editedHigh.priority)
    assertTrue(editedHigh.isCompleted)
  }

  @Test
  fun roomConverters_preserveAllPriorityValuesAndFallbackSafely() {
    assertEquals("LOW", converters.fromPriority(Priority.LOW))
    assertEquals("MEDIUM", converters.fromPriority(Priority.MEDIUM))
    assertEquals("HIGH", converters.fromPriority(Priority.HIGH))
    assertEquals("MEDIUM", converters.fromPriority(null))

    assertEquals(Priority.LOW, converters.toPriority("LOW"))
    assertEquals(Priority.MEDIUM, converters.toPriority("MEDIUM"))
    assertEquals(Priority.HIGH, converters.toPriority("HIGH"))
    assertEquals(Priority.MEDIUM, converters.toPriority(null))
    assertEquals(Priority.MEDIUM, converters.toPriority("UNKNOWN_VALUE"))
  }

  @Test
  fun aiResponseValidator_createTaskWithoutPriority_doesNotFailValidation() {
    val jsonWithoutPriority = """
      {
        "intent": "CREATE_TASK",
        "title": "Kirim invoice klien",
        "description": "Tagihan bulan ini",
        "date": "",
        "time": "",
        "repeatMode": "ONCE",
        "responseText": "Siap, saya tambahkan tugas Kirim invoice klien.",
        "requiresConfirmation": false
      }
    """.trimIndent()

    val validated = AiResponseValidator.validateJson(jsonWithoutPriority, "buat tugas kirim invoice")
    assertEquals(AiCommandIntent.CREATE_TASK, validated.intent)
    assertEquals("Kirim invoice klien", validated.title)
    assertEquals(Priority.MEDIUM, validated.priority)
    assertTrue(validated.isSanitized)
  }

  @Test
  fun aiResponseValidator_createTaskWithInvalidPriority_usesSafeDefault() {
    val jsonInvalidPriority = """
      {
        "intent": "CREATE_TASK",
        "title": "Rapikan arsip",
        "description": "",
        "date": "",
        "time": "",
        "priority": "CRITICAL",
        "repeatMode": "ONCE",
        "responseText": "Siap, tugas ditambahkan.",
        "requiresConfirmation": false
      }
    """.trimIndent()

    val validated = AiResponseValidator.validateJson(jsonInvalidPriority, "buat tugas rapikan arsip")
    assertEquals(AiCommandIntent.CREATE_TASK, validated.intent)
    assertEquals("Rapikan arsip", validated.title)
    assertEquals(Priority.MEDIUM, validated.priority)
  }

  @Test
  fun localCommandParser_reminderAndAgendaCommandsRemainIntact() {
    val reminder = LocalCommandParser.parse("ingatkan saya rapat besok jam 10")
    assertEquals(CommandIntent.CREATE_REMINDER, reminder.intent)

    val agenda = LocalCommandParser.parse("apa agenda saya hari ini")
    assertEquals(CommandIntent.VIEW_TODAY_AGENDA, agenda.intent)
  }
}
