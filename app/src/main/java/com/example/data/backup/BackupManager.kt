package com.example.data.backup

import android.content.Context
import android.os.Build
import androidx.room.withTransaction
import com.example.data.local.AppDatabase
import com.example.data.local.entity.VoiceNoteEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/**
 * Primary orchestrator for VoxTask backup and restore operations.
 * Coordinates snapshotting, serialization, compression, validation,
 * audio staging, and atomic Room database transactions.
 */
class BackupManager(
  private val context: Context,
  private val database: AppDatabase
) {
  private val serializer = BackupSerializer()
  private val storage = BackupStorage(context)
  private val validator = BackupValidator(serializer, storage)

  /**
   * Creates a full backup ZIP archive written to the specified OutputStream.
   *
   * @param outputStream Output stream to receive the ZIP archive (e.g., from SAF URI).
   * @return BackupResult indicating Success with metadata or Error.
   */
  suspend fun createBackup(outputStream: OutputStream): BackupResult = withContext(Dispatchers.IO) {
    try {
      // 1. Snapshot Room data consistently in a single read transaction
      val (tasks, reminders, voiceNotes) = database.withTransaction {
        Triple(
          database.taskDao().getAllTasks().first(),
          database.reminderDao().getAllReminders().first(),
          database.voiceNoteDao().getAllVoiceNotes().first()
        )
      }

      // 2. Scan audio files associated with voice notes
      val audioFilesToBackup = mutableListOf<BackupAudioFile>()
      val missingAudioFiles = mutableListOf<String>()
      val relativeAudioMap = mutableMapOf<Long, String>() // voiceNoteId -> relativePath

      voiceNotes.forEach { note ->
        val rawPath = note.audioPath
        if (!rawPath.isNullOrBlank()) {
          val audioFile = File(rawPath)
          if (audioFile.exists() && audioFile.canRead() && audioFile.isFile) {
            val safeExtension = audioFile.extension.ifBlank { "m4a" }
            val safeName = "voicenote_${note.id}.${safeExtension}"
            val relativePath = "${BackupManifest.AUDIO_DIR_NAME}/$safeName"
            audioFilesToBackup.add(BackupAudioFile(relativePath, audioFile, note.id))
            relativeAudioMap[note.id] = relativePath
          } else {
            missingAudioFiles.add("Catatan #${note.id}: File audio tidak ditemukan di '$rawPath'")
          }
        }
      }

      // 3. Serialize payload entities to bytes
      val tasksJson = serializer.serializeTasks(tasks).toByteArray(Charsets.UTF_8)
      val remindersJson = serializer.serializeReminders(reminders).toByteArray(Charsets.UTF_8)
      val voiceNotesJson = serializer.serializeVoiceNotes(voiceNotes) { note ->
        relativeAudioMap[note.id]
      }.toByteArray(Charsets.UTF_8)

      // 4. Calculate SHA-256 checksums for each payload file and audio file
      val checksums = mutableMapOf<String, String>()
      checksums[BackupManifest.TASKS_FILE_NAME] = storage.calculateSha256(tasksJson)
      checksums[BackupManifest.REMINDERS_FILE_NAME] = storage.calculateSha256(remindersJson)
      checksums[BackupManifest.VOICE_NOTES_FILE_NAME] = storage.calculateSha256(voiceNotesJson)

      audioFilesToBackup.forEach { audio ->
        checksums[audio.relativePath] = storage.calculateSha256(audio.sourceFile)
      }

      // 5. Build manifest
      val manifest = BackupManifest(
        backupVersion = BackupManifest.CURRENT_BACKUP_VERSION,
        appVersion = "1.0",
        databaseVersion = BackupManifest.CURRENT_DATABASE_VERSION,
        createdAt = System.currentTimeMillis(),
        deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
        counts = BackupCounts(
          tasks = tasks.size,
          reminders = reminders.size,
          voiceNotes = voiceNotes.size,
          audioFiles = audioFilesToBackup.size
        ),
        checksums = checksums,
        missingAudioFiles = missingAudioFiles,
        metadata = mapOf("generator" to "VoxTask Backup Engine v1.0")
      )

      val manifestJson = serializer.serializeManifest(manifest).toByteArray(Charsets.UTF_8)

      // 6. Write all entries to ZIP stream
      val byteEntries = mapOf(
        BackupManifest.MANIFEST_FILE_NAME to manifestJson,
        BackupManifest.TASKS_FILE_NAME to tasksJson,
        BackupManifest.REMINDERS_FILE_NAME to remindersJson,
        BackupManifest.VOICE_NOTES_FILE_NAME to voiceNotesJson
      )
      val fileEntries = audioFilesToBackup.map { Pair(it.relativePath, it.sourceFile) }

      val totalBytes = storage.writeZip(outputStream, byteEntries, fileEntries)
      val totalEntries = byteEntries.size + fileEntries.size

      BackupResult.Success(manifest, totalBytes, totalEntries)
    } catch (e: Exception) {
      BackupResult.Error("Gagal membuat berkas cadangan: ${e.message}", e)
    }
  }

  /**
   * Restores data from a backup ZIP archive provided via an InputStream.
   * Employs strict pre-validation, staged extraction, and an atomic Room transaction.
   *
   * @param inputStream Input stream reading the backup ZIP archive.
   * @return RestoreResult indicating Success or specific failure categories.
   */
  suspend fun restoreBackup(inputStream: InputStream): RestoreResult = withContext(Dispatchers.IO) {
    val stagingDir = storage.createStagingDirectory("restore_staging")
    try {
      // 1. Extract ZIP safely to staging directory (with ZipSlip, ZipBomb, duplicate checks)
      try {
        storage.extractZipToStaging(inputStream, stagingDir)
      } catch (e: Exception) {
        return@withContext RestoreResult.CorruptedArchive(
          reason = "Gagal mengekstrak arsip backup: ${e.message}",
          cause = e
        )
      }

      // 2. Validate manifest, schema compatibility, checksums, and JSON structures
      val validation = validator.validateStaging(stagingDir)
      if (validation is ValidationFailure) {
        return@withContext validation.result
      }

      val validData = validation as ValidationSuccess
      val manifest = validData.manifest
      val parsedTasks = validData.tasks
      val parsedReminders = validData.reminders
      val parsedVoiceNotes = validData.voiceNotes

      // 3. Stage and copy audio files to application internal storage
      val audioDir = File(context.filesDir, BackupManifest.AUDIO_DIR_NAME).apply { mkdirs() }
      val newlyCopiedAudioFiles = mutableListOf<File>()
      val missingAudios = mutableListOf<String>()

      val mappedVoiceNotes: List<VoiceNoteEntity> = parsedVoiceNotes.map { note ->
        val relPath = note.audioPath
        if (!relPath.isNullOrBlank()) {
          val stagingAudioFile = File(stagingDir, relPath)
          if (stagingAudioFile.exists() && stagingAudioFile.isFile) {
            val destFile = File(audioDir, stagingAudioFile.name)
            stagingAudioFile.copyTo(destFile, overwrite = true)
            newlyCopiedAudioFiles.add(destFile)
            note.copy(audioPath = destFile.absolutePath)
          } else {
            missingAudios.add("Catatan #${note.id}: File audio tidak ditemukan dalam arsip")
            note.copy(audioPath = null)
          }
        } else {
          note
        }
      }

      // 4. Atomic database update with Room transaction (REPLACE ALL mode)
      try {
        database.withTransaction {
          // A. Delete existing records using DAO methods
          database.taskDao().getAllTasks().first().forEach {
            database.taskDao().delete(it)
          }
          database.reminderDao().getAllReminders().first().forEach {
            database.reminderDao().delete(it)
          }
          database.voiceNoteDao().getAllVoiceNotes().first().forEach {
            database.voiceNoteDao().delete(it)
          }

          // B. Insert restored records
          parsedTasks.forEach { database.taskDao().insert(it) }
          parsedReminders.forEach { database.reminderDao().insert(it) }
          mappedVoiceNotes.forEach { database.voiceNoteDao().insert(it) }
        }
      } catch (e: Exception) {
        // Rollback happened automatically in SQLite.
        // Clean up newly copied audio files so no orphaned files remain on disk.
        newlyCopiedAudioFiles.forEach { it.delete() }
        return@withContext RestoreResult.ExecutionError(
          message = "Gagal memulihkan database. Transaksi telah dibatalkan (rollback): ${e.message}",
          cause = e,
          isRollbackSuccess = true
        )
      }

      RestoreResult.Success(
        manifest = manifest,
        tasksRestored = parsedTasks.size,
        remindersRestored = parsedReminders.size,
        voiceNotesRestored = mappedVoiceNotes.size,
        audioFilesRestored = newlyCopiedAudioFiles.size,
        missingAudioFiles = missingAudios + manifest.missingAudioFiles,
        isDatabaseCommitted = true,
        isAudioCopied = true
      )
    } finally {
      // 5. Always clean up staging directory in finally block
      storage.cleanDirectory(stagingDir)
    }
  }

  /**
   * Safely inspects and pre-validates a backup archive without touching the database.
   * Extracts into temporary staging, verifies manifest, checksums, and JSON schemas,
   * then returns the validated manifest or an error result.
   */
  suspend fun inspectBackup(inputStream: InputStream): InspectResult = withContext(Dispatchers.IO) {
    val stagingDir = storage.createStagingDirectory("inspect_staging")
    try {
      try {
        storage.extractZipToStaging(inputStream, stagingDir)
      } catch (e: Exception) {
        return@withContext InspectResult.Error("Arsip backup rusak atau tidak valid: ${e.message}", e)
      }

      when (val validation = validator.validateStaging(stagingDir)) {
        is ValidationFailure -> {
          when (val res = validation.result) {
            is RestoreResult.IncompatibleVersion ->
              InspectResult.IncompatibleVersion(res.backupDbVersion, res.currentDbVersion, res.message)
            is RestoreResult.ValidationError ->
              InspectResult.Error(res.reason)
            is RestoreResult.CorruptedArchive ->
              InspectResult.Error(res.reason, res.cause)
            else ->
              InspectResult.Error("Validasi berkas cadangan gagal.")
          }
        }
        is ValidationSuccess -> {
          InspectResult.Success(validation.manifest)
        }
      }
    } finally {
      storage.cleanDirectory(stagingDir)
    }
  }
}
