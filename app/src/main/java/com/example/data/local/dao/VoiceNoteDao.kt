package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.VoiceNoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VoiceNoteDao {
  @Query("SELECT * FROM voice_notes ORDER BY createdAt DESC")
  fun getAllVoiceNotes(): Flow<List<VoiceNoteEntity>>

  @Query("SELECT * FROM voice_notes WHERE id = :id")
  suspend fun getVoiceNoteById(id: Long): VoiceNoteEntity?

  @Query("SELECT * FROM voice_notes WHERE title LIKE '%' || :query || '%' OR transcription LIKE '%' || :query || '%' ORDER BY createdAt DESC")
  fun searchVoiceNotes(query: String): Flow<List<VoiceNoteEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insert(voiceNote: VoiceNoteEntity): Long

  @Update
  suspend fun update(voiceNote: VoiceNoteEntity)

  @Delete
  suspend fun delete(voiceNote: VoiceNoteEntity)

  @Query("DELETE FROM voice_notes WHERE id = :id")
  suspend fun deleteById(id: Long)

  @Query("SELECT COUNT(*) FROM voice_notes")
  fun getVoiceNotesCount(): Flow<Int>
}
