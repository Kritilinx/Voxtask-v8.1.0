package com.example.reminder

import com.example.ai.AiCommandIntent
import com.example.ai.AiResponseValidator
import com.example.data.local.Converters
import com.example.data.local.entity.ReminderEntity
import com.example.data.model.Priority
import com.example.data.model.RepeatMode
import com.example.ui.navigation.VoxDestination
import com.example.voice.parser.CommandIntent
import com.example.voice.parser.DateTimeVoiceParser
import com.example.voice.parser.LocalCommandParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * Verification unit tests for Tahap 7B.1 (Remove Priority from Task & Reminder UI).
 * Pure JVM tests covering ReminderEntity defaults, preservation of legacy priority values
 * during edits/toggles, voice/text command parsing without priority, AI validation fallback,
 * and navigation stability.
 */
class ReminderPriorityTahap7B1Test {

  private val converters = Converters()

  @Test
  fun createNewReminderWithoutPriority_usesCompatibleInternalDefault() {
    val due = 1_800_000_000_000L
    val reminder = ReminderEntity(
      title = "Bayar tagihan internet",
      description = "Jatuh tempo bulan ini",
      dueDateTime = due,
      category = "Keuangan",
      repeatMode = RepeatMode.MONTHLY
    )

    assertEquals("Bayar tagihan internet", reminder.title)
    assertEquals(Priority.MEDIUM, reminder.priority)
    assertFalse(reminder.isCompleted)
    assertEquals(RepeatMode.MONTHLY, reminder.repeatMode)
  }

  @Test
  fun editLegacyRemindersAcrossAllPriorityLevels_preservesPriorityAndOtherFields() {
    val baseTime = 1_760_000_000_000L
    val updatedTime = 1_760_086_400_000L

    val legacyLow = ReminderEntity(
      id = 10L,
      title = "Reminder Low",
      description = "Catatan awal low",
      dueDateTime = baseTime,
      priority = Priority.LOW,
      category = "Pribadi",
      repeatMode = RepeatMode.ONCE,
      createdAt = 1_750_000_000_000L
    )
    val legacyMedium = ReminderEntity(
      id = 11L,
      title = "Reminder Medium",
      description = "Catatan awal medium",
      dueDateTime = baseTime,
      priority = Priority.MEDIUM,
      category = "Umum",
      repeatMode = RepeatMode.DAILY,
      createdAt = 1_750_000_001_000L
    )
    val legacyHigh = ReminderEntity(
      id = 12L,
      title = "Reminder High",
      description = "Catatan awal high",
      dueDateTime = baseTime,
      priority = Priority.HIGH,
      category = "Pekerjaan",
      repeatMode = RepeatMode.WEEKLY,
      createdAt = 1_750_000_002_000L
    )

    // Replicate EditReminderDialog.copy(...) behavior where priority and category are not mutated
    val editedLow = legacyLow.copy(
      title = "Reminder Low Diperbarui",
      description = "Catatan baru",
      dueDateTime = updatedTime,
      repeatMode = RepeatMode.DAILY
    )
    val editedMedium = legacyMedium.copy(
      title = "Reminder Medium Diperbarui"
    )
    val editedHigh = legacyHigh.copy(
      title = "Reminder High Diperbarui",
      isCompleted = true
    )

    assertEquals(Priority.LOW, editedLow.priority)
    assertEquals(10L, editedLow.id)
    assertEquals("Reminder Low Diperbarui", editedLow.title)
    assertEquals("Catatan baru", editedLow.description)
    assertEquals(updatedTime, editedLow.dueDateTime)
    assertEquals("Pribadi", editedLow.category)
    assertEquals(RepeatMode.DAILY, editedLow.repeatMode)
    assertEquals(1_750_000_000_000L, editedLow.createdAt)

    assertEquals(Priority.MEDIUM, editedMedium.priority)
    assertEquals(RepeatMode.DAILY, editedMedium.repeatMode)

    assertEquals(Priority.HIGH, editedHigh.priority)
    assertTrue(editedHigh.isCompleted)
    assertEquals(RepeatMode.WEEKLY, editedHigh.repeatMode)
  }

  @Test
  fun localCommandParser_createReminderCommandWithoutPriority_succeeds() {
    val tz = TimeZone.getTimeZone("Asia/Jakarta")
    val baseCal = Calendar.getInstance(tz).apply {
      set(2026, Calendar.NOVEMBER, 9, 10, 0, 0)
      set(Calendar.MILLISECOND, 0)
    }

    val command = "Ingatkan saya besok jam 7 pagi bayar listrik"
    val parsed = LocalCommandParser.parse(command, baseCal.timeInMillis)
    val parsedDateTime = DateTimeVoiceParser.parse(command, baseCal.timeInMillis, tz)

    assertEquals(CommandIntent.CREATE_REMINDER, parsed.intent)
    assertEquals("Bayar listrik", parsed.newTitle)
    assertNotNull(parsed.dueDateTime)
    assertEquals(Priority.MEDIUM, parsed.priority)
    assertTrue(parsedDateTime.hasExplicitDateTime)

    val resultCal = Calendar.getInstance(tz).apply { timeInMillis = parsedDateTime.timestamp }
    assertEquals(10, resultCal.get(Calendar.DAY_OF_MONTH))
    assertEquals(7, resultCal.get(Calendar.HOUR_OF_DAY))
    assertEquals(0, resultCal.get(Calendar.MINUTE))
  }

  @Test
  fun aiResponseValidator_createReminderWithoutPriority_usesDefaultMedium() {
    val jsonWithoutPriority = """
      {
        "intent": "CREATE_REMINDER",
        "title": "Bayar listrik",
        "description": "Tagihan bulanan",
        "date": "2026-11-10",
        "time": "07:00",
        "repeatMode": "ONCE",
        "responseText": "Siap, pengingat Bayar listrik dijadwalkan besok jam 7 pagi.",
        "requiresConfirmation": false
      }
    """.trimIndent()

    val validated = AiResponseValidator.validateJson(
      jsonWithoutPriority,
      "Ingatkan saya besok jam 7 pagi bayar listrik"
    )

    assertEquals(AiCommandIntent.CREATE_REMINDER, validated.intent)
    assertEquals("Bayar listrik", validated.title)
    assertEquals(Priority.MEDIUM, validated.priority)
    assertNotNull(validated.targetEpochMillis)
    assertTrue(validated.isSanitized)
  }

  @Test
  fun roomConverters_preserveReminderPrioritiesAndFallbackSafely() {
    assertEquals("LOW", converters.fromPriority(Priority.LOW))
    assertEquals("MEDIUM", converters.fromPriority(Priority.MEDIUM))
    assertEquals("HIGH", converters.fromPriority(Priority.HIGH))
    assertEquals(Priority.LOW, converters.toPriority("LOW"))
    assertEquals(Priority.MEDIUM, converters.toPriority("MEDIUM"))
    assertEquals(Priority.HIGH, converters.toPriority("HIGH"))
    assertEquals(Priority.MEDIUM, converters.toPriority(null))
  }

  @Test
  fun navigationDestinations_remainUnchangedWithoutTaskScreen() {
    assertEquals(
      listOf(VoxDestination.HOME, VoxDestination.REMINDERS, VoxDestination.SETTINGS),
      VoxDestination.entries
    )
  }
}
