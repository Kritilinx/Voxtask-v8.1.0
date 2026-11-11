package com.example.data.local

import androidx.room.TypeConverter
import com.example.data.model.Priority
import com.example.data.model.RepeatMode

class Converters {
  @TypeConverter
  fun fromPriority(priority: Priority?): String {
    return priority?.name ?: Priority.MEDIUM.name
  }

  @TypeConverter
  fun toPriority(value: String?): Priority {
    return try {
      if (value != null) Priority.valueOf(value) else Priority.MEDIUM
    } catch (_: Exception) {
      Priority.MEDIUM
    }
  }

  @TypeConverter
  fun fromRepeatMode(repeatMode: RepeatMode?): String {
    return repeatMode?.name ?: RepeatMode.ONCE.name
  }

  @TypeConverter
  fun toRepeatMode(value: String?): RepeatMode {
    return try {
      if (value != null) RepeatMode.valueOf(value) else RepeatMode.ONCE
    } catch (_: Exception) {
      RepeatMode.ONCE
    }
  }
}
