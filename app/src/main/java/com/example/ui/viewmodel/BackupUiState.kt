package com.example.ui.viewmodel

import android.net.Uri
import com.example.data.backup.BackupManifest

sealed interface BackupUiState {
  data object Idle : BackupUiState

  data class Preparing(
    val message: String = "Mempersiapkan data..."
  ) : BackupUiState

  data class InProgress(
    val message: String = "Sedang mencadangkan data...",
    val progress: Float? = null
  ) : BackupUiState

  data class Validating(
    val message: String = "Memeriksa integritas berkas cadangan..."
  ) : BackupUiState

  data class PreviewReady(
    val uri: Uri,
    val manifest: BackupManifest
  ) : BackupUiState

  data class Restoring(
    val message: String = "Sedang memulihkan data dan berkas audio..."
  ) : BackupUiState

  data class Success(
    val title: String,
    val message: String,
    val manifest: BackupManifest? = null,
    val details: String = ""
  ) : BackupUiState

  data class Error(
    val title: String,
    val message: String,
    val canRetry: Boolean = true
  ) : BackupUiState
}
