package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.model.Priority

@Entity(tableName = "tasks")
data class TaskEntity(
  @PrimaryKey(autoGenerate = true)
  val id: Long = 0,
  val title: String,
  val description: String = "",
  val isCompleted: Boolean = false,
  val priority: Priority = Priority.MEDIUM,
  val dueDate: Long? = null,
  val category: String = "General",
  val createdAt: Long = System.currentTimeMillis()
)
