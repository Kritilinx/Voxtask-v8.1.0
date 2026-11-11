package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "voice_notes")
data class VoiceNoteEntity(
  @PrimaryKey(autoGenerate = true)
  val id: Long = 0,
  val title: String,
  val audioPath: String? = null,
  val transcription: String? = null,
  val durationMs: Long = 0L,
  val createdAt: Long = System.currentTimeMillis(),
  val tags: String = ""
)
