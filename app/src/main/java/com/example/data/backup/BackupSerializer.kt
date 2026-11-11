package com.example.data.backup

import com.example.data.local.entity.ReminderEntity
import com.example.data.local.entity.TaskEntity
import com.example.data.local.entity.VoiceNoteEntity
import com.example.data.model.Priority
import com.example.data.model.RepeatMode
import org.json.JSONArray
import org.json.JSONObject

/**
 * Handles JSON serialization and deserialization for VoxTask entities and backup manifest.
 * Uses org.json for deterministic, zero-dependency Android-native JSON processing.
 */
class BackupSerializer {

  // --- MANIFEST SERIALIZATION ---

  fun serializeManifest(manifest: BackupManifest): String {
    val root = JSONObject().apply {
      put("backupVersion", manifest.backupVersion)
      put("appVersion", manifest.appVersion)
      put("databaseVersion", manifest.databaseVersion)
      put("createdAt", manifest.createdAt)
      put("deviceModel", manifest.deviceModel)

      val countsJson = JSONObject().apply {
        put("tasks", manifest.counts.tasks)
        put("reminders", manifest.counts.reminders)
        put("voiceNotes", manifest.counts.voiceNotes)
        put("audioFiles", manifest.counts.audioFiles)
      }
      put("counts", countsJson)

      val checksumsJson = JSONObject()
      manifest.checksums.forEach { (path, hash) ->
        checksumsJson.put(path, hash)
      }
      put("checksums", checksumsJson)

      val missingAudioJson = JSONArray()
      manifest.missingAudioFiles.forEach { missingAudioJson.put(it) }
      put("missingAudioFiles", missingAudioJson)

      val metadataJson = JSONObject()
      manifest.metadata.forEach { (k, v) -> metadataJson.put(k, v) }
      put("metadata", metadataJson)
    }
    return root.toString(2)
  }

  fun deserializeManifest(jsonStr: String): BackupManifest {
    val root = JSONObject(jsonStr)

    val backupVersion = root.optInt("backupVersion", 1)
    val appVersion = root.optString("appVersion", "1.0")
    val databaseVersion = root.getInt("databaseVersion")
    val createdAt = root.optLong("createdAt", System.currentTimeMillis())
    val deviceModel = root.optString("deviceModel", "")

    val countsObj = root.optJSONObject("counts")
    val counts = BackupCounts(
      tasks = countsObj?.optInt("tasks", 0) ?: 0,
      reminders = countsObj?.optInt("reminders", 0) ?: 0,
      voiceNotes = countsObj?.optInt("voiceNotes", 0) ?: 0,
      audioFiles = countsObj?.optInt("audioFiles", 0) ?: 0
    )

    val checksumsMap = mutableMapOf<String, String>()
    val checksumsObj = root.optJSONObject("checksums")
    checksumsObj?.let { obj ->
      val keys = obj.keys()
      while (keys.hasNext()) {
        val key = keys.next()
        checksumsMap[key] = obj.getString(key)
      }
    }

    val missingAudioList = mutableListOf<String>()
    val missingArray = root.optJSONArray("missingAudioFiles")
    if (missingArray != null) {
      for (i in 0 until missingArray.length()) {
        missingAudioList.add(missingArray.getString(i))
      }
    }

    val metadataMap = mutableMapOf<String, String>()
    val metadataObj = root.optJSONObject("metadata")
    metadataObj?.let { obj ->
      val keys = obj.keys()
      while (keys.hasNext()) {
        val key = keys.next()
        metadataMap[key] = obj.getString(key)
      }
    }

    return BackupManifest(
      backupVersion = backupVersion,
      appVersion = appVersion,
      databaseVersion = databaseVersion,
      createdAt = createdAt,
      deviceModel = deviceModel,
      counts = counts,
      checksums = checksumsMap,
      missingAudioFiles = missingAudioList,
      metadata = metadataMap
    )
  }

  // --- TASKS SERIALIZATION ---

  fun serializeTasks(tasks: List<TaskEntity>): String {
    val array = JSONArray()
    tasks.forEach { task ->
      val obj = JSONObject().apply {
        put("id", task.id)
        put("title", task.title)
        put("description", task.description)
        put("isCompleted", task.isCompleted)
        put("priority", task.priority.name)
        if (task.dueDate != null) {
          put("dueDate", task.dueDate)
        } else {
          put("dueDate", JSONObject.NULL)
        }
        put("category", task.category)
        put("createdAt", task.createdAt)
      }
      array.put(obj)
    }
    return array.toString(2)
  }

  fun deserializeTasks(jsonStr: String): List<TaskEntity> {
    val array = JSONArray(jsonStr)
    val list = ArrayList<TaskEntity>(array.length())
    for (i in 0 until array.length()) {
      val obj = array.getJSONObject(i)
      val priorityStr = obj.optString("priority", Priority.MEDIUM.name)
      val priority = try {
        Priority.valueOf(priorityStr)
      } catch (_: Exception) {
        Priority.MEDIUM
      }

      val dueDate = if (!obj.isNull("dueDate")) obj.optLong("dueDate") else null

      list.add(
        TaskEntity(
          id = obj.optLong("id", 0L),
          title = obj.getString("title"),
          description = obj.optString("description", ""),
          isCompleted = obj.optBoolean("isCompleted", false),
          priority = priority,
          dueDate = dueDate,
          category = obj.optString("category", "General"),
          createdAt = obj.optLong("createdAt", System.currentTimeMillis())
        )
      )
    }
    return list
  }

  // --- REMINDERS SERIALIZATION ---

  fun serializeReminders(reminders: List<ReminderEntity>): String {
    val array = JSONArray()
    reminders.forEach { reminder ->
      val obj = JSONObject().apply {
        put("id", reminder.id)
        put("title", reminder.title)
        put("description", reminder.description)
        put("dueDateTime", reminder.dueDateTime)
        put("isCompleted", reminder.isCompleted)
        put("priority", reminder.priority.name)
        put("category", reminder.category)
        put("repeatMode", reminder.repeatMode.name)
        put("createdAt", reminder.createdAt)
      }
      array.put(obj)
    }
    return array.toString(2)
  }

  fun deserializeReminders(jsonStr: String): List<ReminderEntity> {
    val array = JSONArray(jsonStr)
    val list = ArrayList<ReminderEntity>(array.length())
    for (i in 0 until array.length()) {
      val obj = array.getJSONObject(i)
      val priorityStr = obj.optString("priority", Priority.MEDIUM.name)
      val priority = try {
        Priority.valueOf(priorityStr)
      } catch (_: Exception) {
        Priority.MEDIUM
      }

      val repeatModeStr = obj.optString("repeatMode", RepeatMode.ONCE.name)
      val repeatMode = try {
        RepeatMode.valueOf(repeatModeStr)
      } catch (_: Exception) {
        RepeatMode.ONCE
      }

      list.add(
        ReminderEntity(
          id = obj.optLong("id", 0L),
          title = obj.getString("title"),
          description = obj.optString("description", ""),
          dueDateTime = obj.getLong("dueDateTime"),
          isCompleted = obj.optBoolean("isCompleted", false),
          priority = priority,
          category = obj.optString("category", "General"),
          repeatMode = repeatMode,
          createdAt = obj.optLong("createdAt", System.currentTimeMillis())
        )
      )
    }
    return list
  }

  // --- VOICE NOTES SERIALIZATION ---

  fun serializeVoiceNotes(
    voiceNotes: List<VoiceNoteEntity>,
    relativeAudioPathMapper: (VoiceNoteEntity) -> String? = { it.audioPath }
  ): String {
    val array = JSONArray()
    voiceNotes.forEach { note ->
      val obj = JSONObject().apply {
        put("id", note.id)
        put("title", note.title)
        val relativePath = relativeAudioPathMapper(note)
        if (relativePath != null) {
          put("audioPath", relativePath)
        } else {
          put("audioPath", JSONObject.NULL)
        }
        if (note.transcription != null) {
          put("transcription", note.transcription)
        } else {
          put("transcription", JSONObject.NULL)
        }
        put("durationMs", note.durationMs)
        put("createdAt", note.createdAt)
        put("tags", note.tags)
      }
      array.put(obj)
    }
    return array.toString(2)
  }

  fun deserializeVoiceNotes(jsonStr: String): List<VoiceNoteEntity> {
    val array = JSONArray(jsonStr)
    val list = ArrayList<VoiceNoteEntity>(array.length())
    for (i in 0 until array.length()) {
      val obj = array.getJSONObject(i)
      val audioPath = if (!obj.isNull("audioPath")) obj.optString("audioPath") else null
      val transcription = if (!obj.isNull("transcription")) obj.optString("transcription") else null

      list.add(
        VoiceNoteEntity(
          id = obj.optLong("id", 0L),
          title = obj.getString("title"),
          audioPath = audioPath,
          transcription = transcription,
          durationMs = obj.optLong("durationMs", 0L),
          createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
          tags = obj.optString("tags", "")
        )
      )
    }
    return list
  }
}
