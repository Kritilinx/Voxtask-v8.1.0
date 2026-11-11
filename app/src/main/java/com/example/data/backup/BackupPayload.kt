package com.example.data.backup

import com.example.data.local.entity.ReminderEntity
import com.example.data.local.entity.TaskEntity
import com.example.data.local.entity.VoiceNoteEntity
import java.io.File

/**
 * References an audio file to be packaged into the backup archive.
 */
data class BackupAudioFile(
  val relativePath: String, // e.g. "audio/voicenote_123.m4a"
  val sourceFile: File,
  val voiceNoteId: Long
)

/**
 * In-memory representation of extracted or staged backup data.
 */
data class BackupPayload(
  val tasks: List<TaskEntity> = emptyList(),
  val reminders: List<ReminderEntity> = emptyList(),
  val voiceNotes: List<VoiceNoteEntity> = emptyList(),
  val audioFiles: List<BackupAudioFile> = emptyList(),
  val missingAudioFiles: List<String> = emptyList()
)
