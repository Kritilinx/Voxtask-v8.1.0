package com.example.data.backup

/**
 * Result sealed hierarchy for backup archive creation.
 */
sealed interface BackupResult {
  data class Success(
    val manifest: BackupManifest,
    val totalBytes: Long,
    val zipEntriesCount: Int
  ) : BackupResult

  data class Error(
    val message: String,
    val cause: Throwable? = null
  ) : BackupResult
}

/**
 * Result sealed hierarchy for backup archive restoration.
 */
sealed interface RestoreResult {
  data class Success(
    val manifest: BackupManifest,
    val tasksRestored: Int,
    val remindersRestored: Int,
    val voiceNotesRestored: Int,
    val audioFilesRestored: Int,
    val missingAudioFiles: List<String> = emptyList(),
    val isDatabaseCommitted: Boolean = true,
    val isAudioCopied: Boolean = true
  ) : RestoreResult

  data class ValidationError(
    val reason: String,
    val details: String = ""
  ) : RestoreResult

  data class IncompatibleVersion(
    val backupDbVersion: Int,
    val currentDbVersion: Int,
    val message: String
  ) : RestoreResult

  data class CorruptedArchive(
    val reason: String,
    val cause: Throwable? = null
  ) : RestoreResult

  data class ExecutionError(
    val message: String,
    val cause: Throwable? = null,
    val isRollbackSuccess: Boolean = true
  ) : RestoreResult
}

/**
 * Result sealed hierarchy for pre-restore archive inspection and validation.
 */
sealed interface InspectResult {
  data class Success(val manifest: BackupManifest) : InspectResult
  data class Error(val message: String, val cause: Throwable? = null) : InspectResult
  data class IncompatibleVersion(
    val backupDbVersion: Int,
    val currentDbVersion: Int,
    val message: String
  ) : InspectResult
}
