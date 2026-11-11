package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.TaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
  @Query("SELECT * FROM tasks ORDER BY isCompleted ASC, createdAt DESC")
  fun getAllTasks(): Flow<List<TaskEntity>>

  @Query("SELECT * FROM tasks WHERE isCompleted = 0 ORDER BY createdAt DESC")
  fun getPendingTasks(): Flow<List<TaskEntity>>

  @Query("SELECT * FROM tasks WHERE dueDate BETWEEN :startOfDay AND :endOfDay ORDER BY isCompleted ASC, priority DESC")
  fun getTodayTasks(startOfDay: Long, endOfDay: Long): Flow<List<TaskEntity>>

  @Query("SELECT * FROM tasks WHERE id = :id")
  suspend fun getTaskById(id: Long): TaskEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insert(task: TaskEntity): Long

  @Update
  suspend fun update(task: TaskEntity)

  @Delete
  suspend fun delete(task: TaskEntity)

  @Query("DELETE FROM tasks WHERE id = :id")
  suspend fun deleteById(id: Long)

  @Query("UPDATE tasks SET isCompleted = :completed WHERE id = :id")
  suspend fun setCompleted(id: Long, completed: Boolean)

  @Query("SELECT COUNT(*) FROM tasks")
  fun getTasksCount(): Flow<Int>
}
