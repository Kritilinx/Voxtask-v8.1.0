package com.example.data.backup

import com.example.data.local.entity.ReminderEntity
import com.example.data.local.entity.TaskEntity
import com.example.data.local.entity.VoiceNoteEntity
import java.io.File

/**
 * Result hierarchy for staging directory pre-restore validation.
 */
sealed interface ValidationResult

data class ValidationSuccess(
  val manifest: BackupManifest,
  val tasks: List<TaskEntity>,
  val reminders: List<ReminderEntity>,
  val voiceNotes: List<VoiceNoteEntity>,
  val availableAudioFiles: List<File>
) : ValidationResult

data class ValidationFailure(
  val result: RestoreResult
) : ValidationResult

/**
 * Validates the contents, schema, versions, and checksums of an extracted backup archive
 * in the staging directory BEFORE any database modification is permitted.
 */
class BackupValidator(
  private val serializer: BackupSerializer,
  private val storage: BackupStorage
) {

  fun validateStaging(stagingDir: File): ValidationResult {
    // 1. Verify manifest.json exists
    val manifestFile = File(stagingDir, BackupManifest.MANIFEST_FILE_NAME)
    if (!manifestFile.exists() || !manifestFile.isFile) {
      return ValidationFailure(
        RestoreResult.ValidationError("Berkas '${BackupManifest.MANIFEST_FILE_NAME}' tidak ditemukan dalam arsip backup.")
      )
    }

    // 2. Parse and validate manifest content
    val manifest: BackupManifest = try {
      val jsonContent = manifestFile.readText(Charsets.UTF_8)
      serializer.deserializeManifest(jsonContent)
    } catch (e: Exception) {
      return ValidationFailure(
        RestoreResult.ValidationError("Gagal membaca manifest backup: ${e.message}", e.toString())
      )
    }

    // 3. Check backupVersion compatibility
    if (manifest.backupVersion > BackupManifest.CURRENT_BACKUP_VERSION) {
      return ValidationFailure(
        RestoreResult.IncompatibleVersion(
          backupDbVersion = manifest.databaseVersion,
          currentDbVersion = BackupManifest.CURRENT_DATABASE_VERSION,
          message = "Versi backup (${manifest.backupVersion}) lebih tinggi dari versi yang didukung aplikasi (${BackupManifest.CURRENT_BACKUP_VERSION}). Silakan perbarui aplikasi."
        )
      )
    }

    // 4. Check databaseVersion compatibility
    if (manifest.databaseVersion > BackupManifest.CURRENT_DATABASE_VERSION) {
      return ValidationFailure(
        RestoreResult.IncompatibleVersion(
          backupDbVersion = manifest.databaseVersion,
          currentDbVersion = BackupManifest.CURRENT_DATABASE_VERSION,
          message = "Versi skema database backup (${manifest.databaseVersion}) lebih baru dari database aplikasi saat ini (${BackupManifest.CURRENT_DATABASE_VERSION})."
        )
      )
    }

    // 5. Verify required payload files exist
    val requiredFiles = listOf(
      BackupManifest.TASKS_FILE_NAME,
      BackupManifest.REMINDERS_FILE_NAME,
      BackupManifest.VOICE_NOTES_FILE_NAME
    )
    for (fileName in requiredFiles) {
      val file = File(stagingDir, fileName)
      if (!file.exists() || !file.isFile) {
        return ValidationFailure(
          RestoreResult.ValidationError("Berkas payload wajib '$fileName' tidak ditemukan dalam arsip backup.")
        )
      }
    }

    // 6. Verify checksums of all registered files
    for ((relativePath, expectedHash) in manifest.checksums) {
      val payloadFile = File(stagingDir, relativePath)
      if (!payloadFile.exists() || !payloadFile.isFile) {
        return ValidationFailure(
          RestoreResult.ValidationError("Berkas '$relativePath' terdaftar dalam checksum manifest namun tidak ditemukan dalam arsip.")
        )
      }

      val actualHash = try {
        storage.calculateSha256(payloadFile)
      } catch (e: Exception) {
        return ValidationFailure(
          RestoreResult.ValidationError("Gagal menghitung checksum berkas '$relativePath': ${e.message}")
        )
      }

      if (!actualHash.equals(expectedHash, ignoreCase = true)) {
        return ValidationFailure(
          RestoreResult.ValidationError(
            "Verifikasi integritas gagal: Checksum SHA-256 untuk '$relativePath' tidak cocok (diharapkan: $expectedHash, aktual: $actualHash)."
          )
        )
      }
    }

    // 7. Parse and validate JSON content
    val tasks: List<TaskEntity> = try {
      val content = File(stagingDir, BackupManifest.TASKS_FILE_NAME).readText(Charsets.UTF_8)
      serializer.deserializeTasks(content)
    } catch (e: Exception) {
      return ValidationFailure(
        RestoreResult.ValidationError("Format data tugas (tasks.json) rusak atau tidak valid: ${e.message}")
      )
    }

    val reminders: List<ReminderEntity> = try {
      val content = File(stagingDir, BackupManifest.REMINDERS_FILE_NAME).readText(Charsets.UTF_8)
      serializer.deserializeReminders(content)
    } catch (e: Exception) {
      return ValidationFailure(
        RestoreResult.ValidationError("Format data pengingat (reminders.json) rusak atau tidak valid: ${e.message}")
      )
    }

    val voiceNotes: List<VoiceNoteEntity> = try {
      val content = File(stagingDir, BackupManifest.VOICE_NOTES_FILE_NAME).readText(Charsets.UTF_8)
      serializer.deserializeVoiceNotes(content)
    } catch (e: Exception) {
      return ValidationFailure(
        RestoreResult.ValidationError("Format data catatan suara (voice_notes.json) rusak atau tidak valid: ${e.message}")
      )
    }

    // 8. Verify data item counts match manifest declarations
    if (tasks.size != manifest.counts.tasks) {
      return ValidationFailure(
        RestoreResult.ValidationError("Jumlah tugas aktual (${tasks.size}) tidak sesuai dengan manifest (${manifest.counts.tasks}).")
      )
    }
    if (reminders.size != manifest.counts.reminders) {
      return ValidationFailure(
        RestoreResult.ValidationError("Jumlah pengingat aktual (${reminders.size}) tidak sesuai dengan manifest (${manifest.counts.reminders}).")
      )
    }
    if (voiceNotes.size != manifest.counts.voiceNotes) {
      return ValidationFailure(
        RestoreResult.ValidationError("Jumlah catatan suara aktual (${voiceNotes.size}) tidak sesuai dengan manifest (${manifest.counts.voiceNotes}).")
      )
    }

    // 9. Collect available audio files in staging
    val audioDir = File(stagingDir, BackupManifest.AUDIO_DIR_NAME)
    val availableAudio = mutableListOf<File>()
    if (audioDir.exists() && audioDir.isDirectory) {
      audioDir.listFiles()?.filter { it.isFile }?.let { availableAudio.addAll(it) }
    }

    return ValidationSuccess(
      manifest = manifest,
      tasks = tasks,
      reminders = reminders,
      voiceNotes = voiceNotes,
      availableAudioFiles = availableAudio
    )
  }
}
