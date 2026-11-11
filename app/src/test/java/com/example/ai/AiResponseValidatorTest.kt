package com.example.ai

import com.example.data.model.Priority
import com.example.data.model.RepeatMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiResponseValidatorTest {

  @Test
  fun testValidCreateReminderJson() {
    val rawJson = """
      {
        "intent": "CREATE_REMINDER",
        "title": "Beli Bahan Makanan",
        "description": "Beli telur dan susu",
        "date": "2026-05-20",
        "time": "14:30",
        "priority": "HIGH",
        "repeatMode": "DAILY",
        "responseText": "Saya buatkan pengingat beli bahan makanan."
      }
    """.trimIndent()

    val validated = AiResponseValidator.validateJson(rawJson, "ingatkan saya beli bahan makanan")
    assertEquals(AiCommandIntent.CREATE_REMINDER, validated.intent)
    assertEquals("Beli Bahan Makanan", validated.title)
    assertEquals("Beli telur dan susu", validated.description)
    assertEquals(Priority.HIGH, validated.priority)
    assertEquals(RepeatMode.DAILY, validated.repeatMode)
    assertNotNull(validated.targetEpochMillis)
    assertEquals("Saya buatkan pengingat beli bahan makanan.", validated.responseText)
  }

  @Test
  fun testBlockedInjectionPattern() {
    val rawJson = """
      {
        "intent": "CREATE_TASK",
        "title": "SELECT * FROM users",
        "description": "SQL test",
        "responseText": "Done"
      }
    """.trimIndent()

    val validated = AiResponseValidator.validateJson(rawJson, "test injection")
    assertEquals(AiCommandIntent.UNSUPPORTED, validated.intent)
    assertFalse(validated.isSanitized)
  }

  @Test
  fun testMissingTitleClarification() {
    val rawJson = """
      {
        "intent": "CREATE_TASK",
        "title": "",
        "description": "No title provided",
        "responseText": ""
      }
    """.trimIndent()

    val validated = AiResponseValidator.validateJson(rawJson, "tambah tugas")
    assertEquals(AiCommandIntent.NEEDS_CLARIFICATION, validated.intent)
  }
}
