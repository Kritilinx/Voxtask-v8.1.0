package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.model.Priority
import com.example.data.model.RepeatMode

@Entity(tableName = "reminders")
data class ReminderEntity(
  @PrimaryKey(autoGenerate = true)
  val id: Long = 0,
  val title: String,
  val description: String = "",
  val dueDateTime: Long,
  val isCompleted: Boolean = false,
  val priority: Priority = Priority.MEDIUM,
  val category: String = "General",
  val repeatMode: RepeatMode = RepeatMode.ONCE,
  val createdAt: Long = System.currentTimeMillis()
)
