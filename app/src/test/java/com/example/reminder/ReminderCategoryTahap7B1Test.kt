package com.example.reminder

import com.example.ai.AiCommandIntent
import com.example.ai.AiResponseValidator
import com.example.data.local.entity.ReminderEntity
import com.example.data.model.Priority
import com.example.data.model.RepeatMode
import com.example.ui.navigation.VoxDestination
import com.example.ui.screens.ReminderFilter
import com.example.voice.parser.CommandIntent
import com.example.voice.parser.LocalCommandParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * Unit tests for Addendum Tahap 7B.1 (Remove Reminder Category UX).
 * Pure JVM tests verifying internal category compatibility for new reminders,
 * preservation of legacy category data on edits/completion toggles, voice/AI parsing
 * without requesting category, and absence of category filters or Task reactivation.
 */
class ReminderCategoryTahap7B1Test {

  @Test
  fun createNewReminderWithoutCategory_usesCompatibleInternalDefault() {
    val due = 1_800_000_000_000L
    val newReminder = ReminderEntity(
      title = "Kirim dokumen pajak",
      description = "Sebelum jam kerja berakhir",
      dueDateTime = due
    )

    assertEquals("Kirim dokumen pajak", newReminder.title)
    assertEquals("General", newReminder.category)
    assertEquals(Priority.MEDIUM, newReminder.priority)
    assertEquals(RepeatMode.ONCE, newReminder.repeatMode)
    assertFalse(newReminder.isCompleted)
  }

  @Test
  fun editLegacyRemindersWithExistingCategories_preservesLegacyCategoriesWithoutLoss() {
    val baseDue = 1_760_000_000_000L
    val newDue = 1_760_090_000_000L

    val legacyCategories = listOf(
      "Pekerjaan",
      "Kesehatan",
      "Pribadi",
      "Umum",
      "AI"
    )

    legacyCategories.forEachIndexed { index, oldCategory ->
      val original = ReminderEntity(
        id = (index + 1).toLong(),
        title = "Pengingat $oldCategory",
        description = "Deskripsi awal",
        dueDateTime = baseDue,
        priority = Priority.HIGH,
        category = oldCategory,
        repeatMode = RepeatMode.ONCE,
        createdAt = 1_750_000_000_000L + index
      )

      // Matches EditReminderDialog.copy(...) behavior where category and priority are untouched
      val updated = original.copy(
        title = "Pengingat $oldCategory Diperbarui",
        description = "Deskripsi diperbarui",
        dueDateTime = newDue,
        repeatMode = RepeatMode.WEEKLY
      )

      assertEquals(oldCategory, updated.category)
      assertEquals(Priority.HIGH, updated.priority)
      assertEquals("Pengingat $oldCategory Diperbarui", updated.title)
      assertEquals("Deskripsi diperbarui", updated.description)
      assertEquals(newDue, updated.dueDateTime)
      assertEquals(RepeatMode.WEEKLY, updated.repeatMode)
      assertEquals(original.id, updated.id)
      assertEquals(original.createdAt, updated.createdAt)
    }
  }

  @Test
  fun toggleCompletionOnLegacyReminder_preservesCategoryAndOtherFields() {
    val legacy = ReminderEntity(
      id = 99L,
      title = "Minum vitamin pagi",
      description = "Setelah sarapan",
      dueDateTime = 1_765_000_000_000L,
      isCompleted = false,
      priority = Priority.LOW,
      category = "Kesehatan",
      repeatMode = RepeatMode.DAILY
    )

    val completed = legacy.copy(isCompleted = true)
    assertTrue(completed.isCompleted)
    assertEquals("Kesehatan", completed.category)
    assertEquals(Priority.LOW, completed.priority)
    assertEquals(legacy.dueDateTime, completed.dueDateTime)

    val reopened = completed.copy(isCompleted = false)
    assertFalse(reopened.isCompleted)
    assertEquals("Kesehatan", reopened.category)
  }

  @Test
  fun localCommandParser_commandsWithoutAndWithHints_neverRequestCategory() {
    val tz = TimeZone.getTimeZone("Asia/Jakarta")
    val baseCal = Calendar.getInstance(tz).apply {
      set(2026, Calendar.NOVEMBER, 9, 10, 0, 0)
      set(Calendar.MILLISECOND, 0)
    }

    val noCategoryCmd = LocalCommandParser.parse(
      "Ingatkan saya besok jam 7 pagi bayar listrik",
      baseCal.timeInMillis
    )
    assertEquals(CommandIntent.CREATE_REMINDER, noCategoryCmd.intent)
    assertEquals("Bayar listrik", noCategoryCmd.newTitle)
    assertNotNull(noCategoryCmd.dueDateTime)
    assertNull(noCategoryCmd.clarificationQuestion)

    val withHintCmd = LocalCommandParser.parse(
      "Ingatkan saya minum obat besok jam 9 malam",
      baseCal.timeInMillis
    )
    assertEquals(CommandIntent.CREATE_REMINDER, withHintCmd.intent)
    assertEquals("Minum obat", withHintCmd.newTitle)
    assertNotNull(withHintCmd.dueDateTime)
    assertNull(withHintCmd.clarificationQuestion)

    val missingTimeOnly = LocalCommandParser.parse(
      "Ingatkan saya kirim berkas",
      baseCal.timeInMillis
    )
    assertEquals(CommandIntent.CREATE_REMINDER, missingTimeOnly.intent)
    assertNull(missingTimeOnly.dueDateTime)
    assertNull(missingTimeOnly.clarificationQuestion)
  }

  @Test
  fun aiResponseValidator_createReminderWithoutCategoryField_validatesCleanly() {
    val aiJson = """
      {
        "intent": "CREATE_REMINDER",
        "title": "Jadwal kontrol dokter",
        "description": "Bawa hasil laboratorium",
        "date": "2026-11-12",
        "time": "09:30",
        "priority": "MEDIUM",
        "repeatMode": "ONCE",
        "responseText": "Siap, pengingat Jadwal kontrol dokter telah dijadwalkan.",
        "requiresConfirmation": false
      }
    """.trimIndent()

    val validated = AiResponseValidator.validateJson(
      rawJson = aiJson,
      originalInput = "Ingatkan jadwal kontrol dokter tanggal 12 jam 09:30"
    )

    assertEquals(AiCommandIntent.CREATE_REMINDER, validated.intent)
    assertEquals("Jadwal kontrol dokter", validated.title)
    assertNotNull(validated.targetEpochMillis)
    assertTrue(validated.isSanitized)
  }

  @Test
  fun reminderFiltersAndNavigation_doNotContainCategoryFilterOrTaskScreen() {
    assertEquals(
      listOf(ReminderFilter.ALL, ReminderFilter.UPCOMING, ReminderFilter.COMPLETED),
      ReminderFilter.entries
    )
    assertEquals(
      listOf(VoxDestination.HOME, VoxDestination.REMINDERS, VoxDestination.SETTINGS),
      VoxDestination.entries
    )
  }
}
