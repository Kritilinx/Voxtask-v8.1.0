package com.example.data.backup

/**
 * Data counts model included in the backup manifest.
 */
data class BackupCounts(
  val tasks: Int = 0,
  val reminders: Int = 0,
  val voiceNotes: Int = 0,
  val audioFiles: Int = 0
)

/**
 * Metadata manifest describing the content, versions, and integrity checksums
 * of a VoxTask backup archive.
 */
data class BackupManifest(
  val backupVersion: Int = CURRENT_BACKUP_VERSION,
  val appVersion: String = "1.0",
  val databaseVersion: Int = CURRENT_DATABASE_VERSION,
  val createdAt: Long = System.currentTimeMillis(),
  val deviceModel: String = "",
  val counts: BackupCounts = BackupCounts(),
  val checksums: Map<String, String> = emptyMap(), // relative path -> SHA-256 lowercase hex
  val missingAudioFiles: List<String> = emptyList(),
  val metadata: Map<String, String> = emptyMap()
) {
  companion object {
    const val CURRENT_BACKUP_VERSION: Int = 1
    const val CURRENT_DATABASE_VERSION: Int = 2

    const val MANIFEST_FILE_NAME: String = "manifest.json"
    const val TASKS_FILE_NAME: String = "tasks.json"
    const val REMINDERS_FILE_NAME: String = "reminders.json"
    const val VOICE_NOTES_FILE_NAME: String = "voice_notes.json"
    const val AUDIO_DIR_NAME: String = "audio"
  }
}
