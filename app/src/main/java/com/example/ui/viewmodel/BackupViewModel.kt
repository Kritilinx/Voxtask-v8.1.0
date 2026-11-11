package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.backup.BackupManager
import com.example.data.backup.BackupResult
import com.example.data.backup.InspectResult
import com.example.data.backup.RestoreResult
import com.example.data.local.AppDatabase
import com.example.reminder.AlarmScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BackupViewModel @JvmOverloads constructor(
  application: Application,
  private val database: AppDatabase = AppDatabase.getDatabase(application),
  private val backupManager: BackupManager = BackupManager(application, database),
  private val alarmScheduler: AlarmScheduler = AlarmScheduler(application)
) : AndroidViewModel(application) {

  private val _uiState = MutableStateFlow<BackupUiState>(BackupUiState.Idle)
  val uiState: StateFlow<BackupUiState> = _uiState.asStateFlow()

  private var ongoingJob: Job? = null

  fun generateDefaultBackupFileName(): String {
    val timestamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
    return "voxtask_backup_$timestamp.zip"
  }

  fun dismissState() {
    _uiState.value = BackupUiState.Idle
  }

  fun cancelRestore() {
    ongoingJob?.cancel(CancellationException("Cancelled by user"))
    _uiState.value = BackupUiState.Idle
  }

  fun exportBackup(uri: Uri?) {
    if (uri == null) {
      _uiState.value = BackupUiState.Idle
      return
    }
    ongoingJob?.cancel(CancellationException("New backup started"))
    ongoingJob = viewModelScope.launch(Dispatchers.IO) {
      _uiState.value = BackupUiState.InProgress("Mempersiapkan snapshot data...")
      var outputStream: OutputStream? = null
      try {
        outputStream = getApplication<Application>().contentResolver.openOutputStream(uri)
      } catch (_: Exception) {}

      if (outputStream == null) {
        _uiState.value = BackupUiState.Error(
          title = "Akses Gagal",
          message = "Tidak dapat membuka berkas tujuan untuk menyimpan cadangan.",
          canRetry = false
        )
        return@launch
      }

      val result = backupManager.createBackup(outputStream)
      when (result) {
        is BackupResult.Success -> {
          val manifest = result.manifest
          val details = "Berhasil mencadangkan ${manifest.counts.tasks} tugas, ${manifest.counts.reminders} pengingat, dan ${manifest.counts.voiceNotes} catatan suara (${manifest.counts.audioFiles} rekaman audio)."
          _uiState.value = BackupUiState.Success(
            title = "Pencadangan Berhasil",
            message = "Berkas cadangan ZIP berhasil dibuat.",
            manifest = manifest,
            details = details
          )
        }
        is BackupResult.Error -> {
          _uiState.value = BackupUiState.Error(
            title = "Pencadangan Gagal",
            message = result.message,
            canRetry = false
          )
        }
      }
    }
  }

  fun validateBackupFile(uri: Uri?) {
    if (uri == null) {
      _uiState.value = BackupUiState.Idle
      return
    }
    ongoingJob?.cancel(CancellationException("New validation started"))
    ongoingJob = viewModelScope.launch(Dispatchers.IO) {
      _uiState.value = BackupUiState.Validating("Memeriksa integritas berkas cadangan...")
      var inputStream: InputStream? = null
      try {
        inputStream = getApplication<Application>().contentResolver.openInputStream(uri)
      } catch (_: Exception) {}

      if (inputStream == null) {
        _uiState.value = BackupUiState.Error(
          title = "Akses Gagal",
          message = "Tidak dapat membaca berkas cadangan yang dipilih.",
          canRetry = false
        )
        return@launch
      }

      val result = backupManager.inspectBackup(inputStream)
      when (result) {
        is InspectResult.Success -> {
          _uiState.value = BackupUiState.PreviewReady(uri, result.manifest)
        }
        is InspectResult.IncompatibleVersion -> {
          _uiState.value = BackupUiState.Error(
            title = "Versi Tidak Kompatibel",
            message = result.message,
            canRetry = false
          )
        }
        is InspectResult.Error -> {
          _uiState.value = BackupUiState.Error(
            title = "Berkas Cadangan Tidak Valid",
            message = result.message,
            canRetry = false
          )
        }
      }
    }
  }

  fun confirmRestore() {
    val currentState = _uiState.value as? BackupUiState.PreviewReady ?: return
    ongoingJob?.cancel(CancellationException("Restore started"))
    ongoingJob = viewModelScope.launch(Dispatchers.IO) {
      _uiState.value = BackupUiState.Restoring("Sedang memulihkan data dan file audio...")
      val now = System.currentTimeMillis()

      val oldActiveReminderIds = try {
        val active = database.reminderDao().getActiveRemindersSync(now)
        active.map { it.id }
      } catch (_: Exception) {
        emptyList()
      }

      var inputStream: InputStream? = null
      try {
        inputStream = getApplication<Application>().contentResolver.openInputStream(currentState.uri)
      } catch (_: Exception) {}

      if (inputStream == null) {
        _uiState.value = BackupUiState.Error(
          title = "Akses Gagal",
          message = "Tidak dapat membuka kembali berkas untuk memproses pemulihan.",
          canRetry = false
        )
        return@launch
      }

      val result = backupManager.restoreBackup(inputStream)
      if (result !is RestoreResult.Success) {
        when (result) {
          is RestoreResult.IncompatibleVersion -> {
            _uiState.value = BackupUiState.Error(
              title = "Versi Tidak Kompatibel",
              message = result.message,
              canRetry = false
            )
          }
          is RestoreResult.ValidationError -> {
            _uiState.value = BackupUiState.Error(
              title = "Validasi Gagal",
              message = "${result.reason} Data lama tetap aman dipertahankan.",
              canRetry = false
            )
          }
          is RestoreResult.CorruptedArchive -> {
            _uiState.value = BackupUiState.Error(
              title = "Arsip Rusak",
              message = "${result.reason} Data lama tetap aman dipertahankan.",
              canRetry = false
            )
          }
          is RestoreResult.ExecutionError -> {
            _uiState.value = BackupUiState.Error(
              title = "Pemulihan Gagal",
              message = "${result.message} Data lama tetap aman dipertahankan (transaksi telah di-rollback).",
              canRetry = false
            )
          }
          else -> {}
        }
        return@launch
      }

      var alarmsScheduledCount = 0
      try {
        for (id in oldActiveReminderIds) {
          alarmScheduler.cancel(id)
        }
        val newActive = database.reminderDao().getActiveRemindersSync(System.currentTimeMillis())
        for (reminder in newActive) {
          alarmScheduler.schedule(reminder)
          alarmsScheduledCount++
        }
      } catch (_: Exception) {}

      val details = "Dipulihkan: ${result.tasksRestored} tugas, ${result.remindersRestored} pengingat, dan ${result.voiceNotesRestored} catatan suara (${result.audioFilesRestored} audio). $alarmsScheduledCount alarm aktif berhasil dijadwalkan ulang."
      _uiState.value = BackupUiState.Success(
        title = "Pemulihan Berhasil",
        message = "Data berhasil dipulihkan secara penuh (Replace All).",
        manifest = result.manifest,
        details = details
      )
    }
  }
}
