package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import com.example.ui.components.FirstTimePermissionDialog
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.Priority
import com.example.reminder.AlarmSoundPlayer
import com.example.reminder.NotificationHelper
import com.example.ui.theme.VoxPrimary
import com.example.ui.theme.VoxSecondary
import com.example.ui.viewmodel.BackupUiState
import com.example.ui.viewmodel.BackupViewModel
import com.example.ui.viewmodel.VoxTaskViewModel
import com.example.voice.TtsInitStatus
import com.example.voice.TtsVoiceInfo
import com.example.voice.TtsVoiceSelector
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(
  viewModel: VoxTaskViewModel,
  backupViewModel: BackupViewModel = viewModel(),
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val stats by viewModel.stats.collectAsStateWithLifecycle()
  val aiStats by viewModel.aiUsageStats.collectAsStateWithLifecycle()
  val ttsVoiceState by viewModel.ttsVoiceState.collectAsStateWithLifecycle()
  val alarmToneState by viewModel.alarmToneState.collectAsStateWithLifecycle()
  val backupUiState by backupViewModel.uiState.collectAsStateWithLifecycle()
  val alarmRingingState by AlarmSoundPlayer.ringingState.collectAsStateWithLifecycle()
  val isSpeechAvailable = viewModel.assistantManager.isSpeechRecognitionAvailable()
  val parserDebugEnabled by viewModel.parserDebugEnabled.collectAsStateWithLifecycle()
  val parserDebugHistory by viewModel.parserDebugHistory.collectAsStateWithLifecycle()

  LaunchedEffect(Unit) {
    viewModel.refreshTtsVoices()
    viewModel.refreshAlarmToneStatus()
  }

  DisposableEffect(Unit) {
    onDispose {
      viewModel.stopAlarmTonePreview()
    }
  }

  val pickAlarmToneLauncher = rememberLauncherForActivityResult(
    contract = OpenAudioDocumentContract()
  ) { uri ->
    viewModel.selectAlarmTone(uri)
  }

  val createDocumentLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.CreateDocument("application/zip")
  ) { uri ->
    backupViewModel.exportBackup(uri)
  }

  val openDocumentLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocument()
  ) { uri ->
    backupViewModel.validateBackupFile(uri)
  }

  var hasNotificationPermission by remember {
    mutableStateOf(
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
      } else {
        true
      }
    )
  }

  val notificationPermissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission()
  ) { granted ->
    hasNotificationPermission = granted
  }

  val canScheduleExact = viewModel.alarmScheduler.canScheduleExactAlarms()
  var showPermissionModal by remember { mutableStateOf(false) }

  LazyColumn(
    modifier = modifier
      .fillMaxSize()
      .padding(horizontal = 20.dp)
      .testTag("settings_screen"),
    contentPadding = PaddingValues(top = 16.dp, bottom = 120.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    item {
      Text(
        text = "Settings",
        style = MaterialTheme.typography.displayMedium.copy(
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onBackground
        )
      )
      Text(
        text = "Konfigurasi VoxTask, Alarm Lokal & Status Notifikasi",
        style = MaterialTheme.typography.bodyMedium.copy(
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      )
    }

    // 1. Smart Reminder & Alarm Manager Status Card
    item {
      Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(18.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Box(
              modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(VoxPrimary.copy(alpha = 0.15f)),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = Icons.Default.Alarm,
                contentDescription = null,
                tint = VoxPrimary,
                modifier = Modifier.size(20.dp)
              )
            }
            Column {
              Text(
                text = "Smart Reminder & Alarm",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
              )
              Text(
                text = "Penjadwalan alarm lokal via AlarmManager",
                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
              )
            }
          }

          HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

          // Izin Notifikasi (POST_NOTIFICATIONS)
          SettingStatusRow(
            title = "Izin Notifikasi",
            subtitle = if (hasNotificationPermission) "Dapat menampilkan pengingat di status bar" else "Perlu izin agar pengingat muncul",
            statusText = if (hasNotificationPermission) "Diizinkan" else "Belum Aktif",
            statusColor = if (hasNotificationPermission) Color(0xFF10B981) else Color(0xFFEF4444)
          )

          if (!hasNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Button(
              onClick = {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
              },
              colors = ButtonDefaults.buttonColors(containerColor = VoxPrimary),
              modifier = Modifier.fillMaxWidth()
            ) {
              Icon(imageVector = Icons.Default.Notifications, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(8.dp))
              Text("Izinkan Notifikasi")
            }
          }

          // Izin Alarm Tepat (SCHEDULE_EXACT_ALARM)
          SettingStatusRow(
            title = "Alarm Tepat Waktu (Exact)",
            subtitle = if (canScheduleExact) "Alarm berdering tepat pada menit yang ditentukan" else "Menggunakan mode cadangan (Inexact Alarm)",
            statusText = if (canScheduleExact) "Exact Alarm" else "Inexact (Cadangan)",
            statusColor = if (canScheduleExact) Color(0xFF10B981) else Color(0xFFF59E0B)
          )

          if (!canScheduleExact && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            OutlinedButton(
              onClick = {
                try {
                  val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.parse("package:${context.packageName}")
                  }
                  context.startActivity(intent)
                } catch (_: Exception) {
                  try {
                    val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                      data = Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(fallback)
                  } catch (_: Exception) {}
                }
              },
              modifier = Modifier.fillMaxWidth()
            ) {
              Text("Buka Pengaturan Alarm Tepat")
            }
          }

          // Uji Coba Alarm & Notifikasi
          if (alarmRingingState.isRinging) {
            Button(
              onClick = {
                AlarmSoundPlayer.stopAlarm(context)
                NotificationHelper.cancelNotification(context, 99999)
              },
              colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError
              ),
              modifier = Modifier
                .fillMaxWidth()
                .testTag("stop_alarm_button")
            ) {
              Icon(imageVector = Icons.Default.NotificationsOff, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(8.dp))
              Text("Matikan Suara Alarm yang Berdering")
            }
          } else {
            Button(
              onClick = {
                NotificationHelper.showReminderNotification(
                  context = context,
                  reminderId = 99999L,
                  title = "Uji Coba Alarm VoxTask",
                  description = "Alarm Smart Reminder berbunyi nyaring & notifikasi aktif!",
                  priority = Priority.HIGH,
                  category = "Uji Sistem",
                  playAudibleAlarm = true
                )
              },
              colors = ButtonDefaults.buttonColors(
                containerColor = VoxSecondary.copy(alpha = 0.15f),
                contentColor = VoxSecondary
              ),
              modifier = Modifier
                .fillMaxWidth()
                .testTag("test_alarm_button")
            ) {
              Icon(imageVector = Icons.Default.NotificationsActive, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(8.dp))
              Text("Uji Coba Suara Alarm & Notifikasi")
            }
          }

          // Periksa & Kelola Seluruh Izin Aplikasi
          OutlinedButton(
            onClick = { showPermissionModal = true },
            modifier = Modifier
              .fillMaxWidth()
              .testTag("manage_all_permissions_button")
          ) {
            Icon(imageVector = Icons.Default.Security, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Kelola Izin Akses (Mic, Alarm, Notifikasi)")
          }
        }
      }
    }

    // 1a. Nada Alarm Reminder (Custom Local Audio SAF - Stage 7D)
    item {
      Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("alarm_tone_settings_card")
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(18.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp),
              modifier = Modifier.weight(1f)
            ) {
              Box(
                modifier = Modifier
                  .size(36.dp)
                  .clip(RoundedCornerShape(10.dp))
                  .background(VoxPrimary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = Icons.Default.MusicNote,
                  contentDescription = null,
                  tint = VoxPrimary,
                  modifier = Modifier.size(20.dp)
                )
              }
              Column {
                Text(
                  text = "Nada Alarm Reminder",
                  style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                  text = "Pilih file audio lokal HP untuk nada notifikasi pengingat",
                  style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                )
              }
            }

            val toneBadgeText: String
            val toneBadgeColor: Color
            when {
              alarmToneState.isCustomTone -> {
                toneBadgeText = "Kustom"
                toneBadgeColor = Color(0xFF10B981)
              }
              alarmToneState.isUsingFallbackDefault -> {
                toneBadgeText = "Fallback"
                toneBadgeColor = Color(0xFFF59E0B)
              }
              else -> {
                toneBadgeText = "Default"
                toneBadgeColor = VoxPrimary
              }
            }
            Surface(
              shape = RoundedCornerShape(8.dp),
              color = toneBadgeColor.copy(alpha = 0.12f)
            ) {
              Text(
                text = toneBadgeText,
                style = MaterialTheme.typography.labelSmall.copy(
                  color = toneBadgeColor,
                  fontWeight = FontWeight.Bold
                ),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
              )
            }
          }

          HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

          Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("alarm_tone_active_summary")
          ) {
            Column(
              modifier = Modifier.padding(12.dp),
              verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
              Text(
                text = if (alarmToneState.isCustomTone) {
                  alarmToneState.selectedDisplayName ?: "Audio Kustom Terpilih"
                } else {
                  "Nada Default Sistem"
                },
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
              )
              Text(
                text = if (alarmToneState.isCustomTone) {
                  "Sumber: File audio lokal perangkat (offline)"
                } else {
                  "Sumber: Nada alarm bawaan perangkat Android"
                },
                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
              )
            }
          }

          if (alarmToneState.isProcessing) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              CircularProgressIndicator(
                color = VoxPrimary,
                strokeWidth = 2.dp,
                modifier = Modifier.size(18.dp)
              )
              Text(
                text = "Memeriksa kompatibilitas file audio...",
                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
              )
            }
          }

          if (!alarmToneState.errorMessage.isNullOrBlank()) {
            Surface(
              shape = RoundedCornerShape(12.dp),
              color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f),
              modifier = Modifier
                .fillMaxWidth()
                .testTag("alarm_tone_error_notice")
            ) {
              Text(
                text = alarmToneState.errorMessage ?: "",
                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.error),
                modifier = Modifier.padding(12.dp)
              )
            }
          } else if (!alarmToneState.statusMessage.isNullOrBlank()) {
            val isFallback = alarmToneState.isUsingFallbackDefault
            Surface(
              shape = RoundedCornerShape(12.dp),
              color = if (isFallback) Color(0xFFF59E0B).copy(alpha = 0.12f) else Color(0xFF10B981).copy(alpha = 0.12f),
              modifier = Modifier
                .fillMaxWidth()
                .testTag("alarm_tone_status_notice")
            ) {
              Text(
                text = alarmToneState.statusMessage ?: "",
                style = MaterialTheme.typography.bodySmall.copy(
                  color = if (isFallback) Color(0xFFB45309) else Color(0xFF047857)
                ),
                modifier = Modifier.padding(12.dp)
              )
            }
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            OutlinedButton(
              onClick = {
                pickAlarmToneLauncher.launch(arrayOf("audio/*", "application/ogg"))
              },
              enabled = !alarmToneState.isProcessing,
              modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .testTag("pick_alarm_tone_button")
            ) {
              Icon(
                imageVector = Icons.Default.FolderOpen,
                contentDescription = "Pilih Audio Lokal",
                modifier = Modifier.size(18.dp)
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text("Pilih Audio")
            }

            Button(
              onClick = { viewModel.previewAlarmTone() },
              enabled = !alarmToneState.isProcessing,
              colors = ButtonDefaults.buttonColors(
                containerColor = VoxPrimary,
                contentColor = Color.White
              ),
              modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .testTag("preview_alarm_tone_button")
            ) {
              Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Coba Nada",
                modifier = Modifier.size(18.dp)
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text("Coba Nada")
            }
          }

          if (alarmToneState.isPreviewPlaying) {
            OutlinedButton(
              onClick = { viewModel.stopAlarmTonePreview() },
              modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag("stop_alarm_tone_preview_button")
            ) {
              Icon(
                imageVector = Icons.Default.Stop,
                contentDescription = "Hentikan Pratinjau",
                modifier = Modifier.size(18.dp)
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text("Hentikan Pratinjau")
            }
          }

          if (alarmToneState.isCustomTone || alarmToneState.isUsingFallbackDefault) {
            OutlinedButton(
              onClick = { viewModel.resetAlarmToneToDefault() },
              enabled = !alarmToneState.isProcessing,
              modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag("reset_default_alarm_tone_button")
            ) {
              Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "Gunakan Nada Default",
                modifier = Modifier.size(18.dp)
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text("Gunakan Nada Default")
            }
          }

          Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(
              modifier = Modifier.padding(10.dp),
              verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
              Text(
                text = "Info Notification Channel Android",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
              )
              Text(
                text = "Pada Android 8+, suara Notification Channel tidak dapat diubah langsung pada channel lama. Mengganti nada memakai channel khusus (${alarmToneState.activeChannelId}) dengan pengaturan prioritas & getar yang dipertahankan. Pengaturan yang Anda ubah di Android Settings tetap dihormati.",
                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
              )
              if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                TextButton(
                  onClick = {
                    try {
                      val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        putExtra(Settings.EXTRA_CHANNEL_ID, alarmToneState.activeChannelId)
                      }
                      context.startActivity(intent)
                    } catch (_: Exception) {
                      try {
                        val fallback = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                          putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        }
                        context.startActivity(fallback)
                      } catch (_: Exception) {}
                    }
                  },
                  modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("open_channel_settings_button")
                ) {
                  Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                  )
                  Spacer(modifier = Modifier.width(6.dp))
                  Text("Buka Pengaturan Channel Android")
                }
              }
            }
          }
        }
      }
    }

    // 1b. Parser Debug (Stage 7E.2)
    item {
      Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth().testTag("parser_debug_settings_card")
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(18.dp),
          verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Box(
              modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(VoxPrimary.copy(alpha = 0.15f)),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = Icons.Default.BugReport,
                contentDescription = "Parser Debug",
                tint = VoxPrimary,
                modifier = Modifier.size(20.dp)
              )
            }
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "Parser Debug",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
              )
              Text(
                text = "Diagnostik NLU & validasi sebelum pembuatan pengingat",
                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
              )
            }
            Switch(
              checked = parserDebugEnabled,
              onCheckedChange = { viewModel.setParserDebugEnabled(it) },
              modifier = Modifier.testTag("parser_debug_switch")
            )
          }

          Text(
            text = "Saat diaktifkan, hasil parsing terstruktur (STT, normalisasi, slot durasi, tanggal relatif, jam, dan keputusan akhir) akan ditampilkan di layar utama dan dicatat secara lokal untuk membantu verifikasi.",
            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
          )

          if (parserDebugEnabled) {
            Surface(
              shape = RoundedCornerShape(12.dp),
              color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
              modifier = Modifier.fillMaxWidth()
            ) {
              Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Text(
                    text = "Riwayat Diagnostik (${parserDebugHistory.size})",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                  )
                  if (parserDebugHistory.isNotEmpty()) {
                    TextButton(
                      onClick = { viewModel.clearParserDebugHistory() },
                      modifier = Modifier.testTag("clear_parser_debug_history_button")
                    ) {
                      Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Hapus Riwayat",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.error
                      )
                      Spacer(modifier = Modifier.width(4.dp))
                      Text("Hapus Riwayat", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                    }
                  }
                }

                if (parserDebugHistory.isEmpty()) {
                  Text(
                    text = "Belum ada riwayat perintah suara. Ucapkan perintah pengingat untuk melihat trace diagnostik.",
                    style = MaterialTheme.typography.bodySmall.copy(
                      fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                      color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                  )
                } else {
                  Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                  ) {
                    parserDebugHistory.take(5).forEach { traceItem ->
                      com.example.ui.components.ParserDebugTraceCard(trace = traceItem)
                    }
                  }
                }
              }
            }
          }
        }
      }
    }

    // 2. Arsitektur Local-First Card
    item {
      Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(18.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Box(
              modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF10B981).copy(alpha = 0.15f)),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = Icons.Default.Security,
                contentDescription = null,
                tint = Color(0xFF10B981),
                modifier = Modifier.size(20.dp)
              )
            }
            Column {
              Text(
                text = "Arsitektur Local-First",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
              )
              Text(
                text = "Data disimpan aman di perangkat Anda",
                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
              )
            }
          }

          HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

          SettingStatusRow(
            title = "Penyimpanan Database",
            subtitle = "Android Room SQLite (Offline-First v2)",
            statusText = "Aktif (v2)",
            statusColor = Color(0xFF10B981)
          )

          SettingStatusRow(
            title = "Autentikasi & Akun",
            subtitle = "Bebas login, langsung dapat digunakan",
            statusText = "Mandiri",
            statusColor = VoxPrimary
          )

          SettingStatusRow(
            title = "Privasi Data",
            subtitle = "Tidak ada pelacakan atau transfer ke server",
            statusText = "100% Privat",
            statusColor = Color(0xFF10B981)
          )
        }
      }
    }

    // 3. Integrasi Suara (STT & TTS)
    item {
      Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(18.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Box(
              modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(VoxPrimary.copy(alpha = 0.15f)),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = null,
                tint = VoxPrimary,
                modifier = Modifier.size(20.dp)
              )
            }
            Column {
              Text(
                text = "Mesin Suara Native (STT & TTS)",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
              )
              Text(
                text = "Fondasi integrasi suara terstruktur",
                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
              )
            }
          }

          HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

          SettingStatusRow(
            title = "Speech-to-Text (STT)",
            subtitle = "Android SpeechRecognizer Framework",
            statusText = if (isSpeechAvailable) "Tersedia" else "Menunggu Izin",
            statusColor = if (isSpeechAvailable) Color(0xFF10B981) else Color(0xFFF59E0B)
          )

          SettingStatusRow(
            title = "Text-to-Speech (TTS)",
            subtitle = "Android Native TextToSpeech Engine",
            statusText = "Siap Digunakan",
            statusColor = Color(0xFF10B981)
          )

          Button(
            onClick = {
              viewModel.assistantManager.speak("Halo, ini adalah pengujian suara Text-to-Speech dari VoxTask.")
            },
            colors = ButtonDefaults.buttonColors(containerColor = VoxPrimary.copy(alpha = 0.12f), contentColor = VoxPrimary),
            modifier = Modifier.fillMaxWidth()
          ) {
            Icon(imageVector = Icons.Default.VolumeUp, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Uji Coba Suara (TTS)")
          }
        }
      }
    }

    // 3a. Pemilihan Suara AI (Android Native TTS - Stage 7C)
    item {
      var showAllVoices by remember { mutableStateOf(false) }

      Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("ai_voice_settings_card")
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(18.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp),
              modifier = Modifier.weight(1f)
            ) {
              Box(
                modifier = Modifier
                  .size(36.dp)
                  .clip(RoundedCornerShape(10.dp))
                  .background(VoxSecondary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = Icons.Default.RecordVoiceOver,
                  contentDescription = null,
                  tint = VoxSecondary,
                  modifier = Modifier.size(20.dp)
                )
              }
              Column {
                Text(
                  text = "Suara AI",
                  style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                  text = "Suara Text-to-Speech perangkat untuk respons VoxTask",
                  style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                )
              }
            }

            val badgeText: String
            val badgeColor: Color
            when (ttsVoiceState.initStatus) {
              TtsInitStatus.READY -> {
                badgeText = "${ttsVoiceState.availableVoices.size} Suara"
                badgeColor = Color(0xFF10B981)
              }
              TtsInitStatus.ERROR -> {
                badgeText = "Gagal"
                badgeColor = MaterialTheme.colorScheme.error
              }
              TtsInitStatus.INITIALIZING, TtsInitStatus.IDLE -> {
                badgeText = "Memuat"
                badgeColor = Color(0xFFF59E0B)
              }
            }

            Surface(
              shape = RoundedCornerShape(8.dp),
              color = badgeColor.copy(alpha = 0.12f)
            ) {
              Text(
                text = badgeText,
                style = MaterialTheme.typography.labelSmall.copy(
                  color = badgeColor,
                  fontWeight = FontWeight.Bold
                ),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
              )
            }
          }

          HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

          when (ttsVoiceState.initStatus) {
            TtsInitStatus.IDLE, TtsInitStatus.INITIALIZING -> {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(vertical = 4.dp)
              ) {
                CircularProgressIndicator(
                  color = VoxPrimary,
                  strokeWidth = 2.dp,
                  modifier = Modifier.size(18.dp)
                )
                Text(
                  text = "Mengambil daftar suara dari mesin TTS perangkat...",
                  style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                )
              }
            }

            TtsInitStatus.ERROR -> {
              Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f),
                modifier = Modifier
                  .fillMaxWidth()
                  .testTag("tts_init_error_notice")
              ) {
                Column(
                  modifier = Modifier.padding(12.dp),
                  verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                  Text(
                    text = ttsVoiceState.feedbackMessage
                      ?: "Mesin Text-to-Speech belum siap atau gagal diinisialisasi pada perangkat ini.",
                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.error)
                  )
                  OutlinedButton(
                    onClick = { viewModel.refreshTtsVoices() },
                    modifier = Modifier
                      .fillMaxWidth()
                      .heightIn(min = 48.dp)
                      .testTag("retry_tts_init_button")
                  ) {
                    Icon(
                      imageVector = Icons.Default.Refresh,
                      contentDescription = null,
                      modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Muat Ulang TTS")
                  }
                }
              }
            }

            TtsInitStatus.READY -> {
              if (!ttsVoiceState.hasIndonesianVoice) {
                Surface(
                  shape = RoundedCornerShape(12.dp),
                  color = Color(0xFFF59E0B).copy(alpha = 0.12f),
                  modifier = Modifier
                    .fillMaxWidth()
                    .testTag("indonesian_voice_unavailable_notice")
                ) {
                  Text(
                    text = "Suara Bahasa Indonesia (id-ID) belum tersedia di perangkat ini.",
                    style = MaterialTheme.typography.bodySmall.copy(
                      color = Color(0xFFB45309),
                      fontWeight = FontWeight.Medium
                    ),
                    modifier = Modifier.padding(12.dp)
                  )
                }
              }

              if (ttsVoiceState.isUsingFallbackVoice || !ttsVoiceState.feedbackMessage.isNullOrBlank()) {
                Surface(
                  shape = RoundedCornerShape(12.dp),
                  color = Color(0xFFF59E0B).copy(alpha = 0.12f),
                  modifier = Modifier
                    .fillMaxWidth()
                    .testTag("tts_feedback_notice")
                ) {
                  Text(
                    text = ttsVoiceState.feedbackMessage
                      ?: "Suara pilihan tidak lagi tersedia di perangkat; menggunakan suara default Android.",
                    style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFB45309)),
                    modifier = Modifier.padding(12.dp)
                  )
                }
              }

              val allVoices = ttsVoiceState.availableVoices
              if (allVoices.isEmpty()) {
                Text(
                  text = "Tidak ada suara yang terpasang pada mesin Text-to-Speech perangkat. VoxTask tetap menggunakan suara default sistem bila tersedia.",
                  style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                  modifier = Modifier.testTag("no_tts_voices_notice")
                )
              } else {
                val displayedVoices = remember(
                  allVoices,
                  showAllVoices,
                  ttsVoiceState.selectedVoiceId,
                  ttsVoiceState.activeVoiceId
                ) {
                  if (showAllVoices || allVoices.size <= 5) {
                    allVoices
                  } else {
                    val prioritized = allVoices.take(5).toMutableList()
                    val selectedId = ttsVoiceState.selectedVoiceId
                    val activeId = ttsVoiceState.activeVoiceId
                    listOfNotNull(selectedId, activeId).forEach { id ->
                      val match = allVoices.firstOrNull { it.id == id }
                      if (match != null && prioritized.none { it.id == match.id }) {
                        prioritized.add(match)
                      }
                    }
                    prioritized
                  }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                  displayedVoices.forEach { voice ->
                    val isExplicitlySelected = voice.id == ttsVoiceState.selectedVoiceId
                    val isDefaultActive = ttsVoiceState.selectedVoiceId == null && voice.id == ttsVoiceState.activeVoiceId
                    AiVoiceOptionRow(
                      voice = voice,
                      isSelected = isExplicitlySelected,
                      isDefaultActive = isDefaultActive,
                      onSelect = { viewModel.selectAiVoice(voice.id) }
                    )
                  }
                }

                if (allVoices.size > displayedVoices.size) {
                  TextButton(
                    onClick = { showAllVoices = true },
                    modifier = Modifier
                      .fillMaxWidth()
                      .heightIn(min = 48.dp)
                      .testTag("toggle_all_voices_button")
                  ) {
                    Text("Tampilkan semua suara perangkat (${allVoices.size})")
                  }
                } else if (showAllVoices && allVoices.size > 5) {
                  TextButton(
                    onClick = { showAllVoices = false },
                    modifier = Modifier
                      .fillMaxWidth()
                      .heightIn(min = 48.dp)
                      .testTag("toggle_all_voices_button")
                  ) {
                    Text("Tampilkan lebih sedikit")
                  }
                }
              }
            }
          }

          Button(
            onClick = { viewModel.previewAiVoice() },
            colors = ButtonDefaults.buttonColors(
              containerColor = VoxPrimary,
              contentColor = Color.White
            ),
            modifier = Modifier
              .fillMaxWidth()
              .heightIn(min = 48.dp)
              .testTag("preview_ai_voice_button")
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.VolumeUp,
              contentDescription = "Coba Suara",
              modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Coba Suara")
          }

          Text(
            text = "Pratinjau membacakan: \"${TtsVoiceSelector.PREVIEW_SAMPLE_TEXT}\"",
            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
          )
        }
      }
    }

    // 3b. Gemini AI Fallback Configuration (Tahap 5)
    item {
      Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth().testTag("gemini_settings_card")
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(18.dp),
          verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp),
              modifier = Modifier.weight(1f)
            ) {
              Box(
                modifier = Modifier
                  .size(36.dp)
                  .clip(RoundedCornerShape(10.dp))
                  .background(VoxSecondary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = Icons.Default.AutoAwesome,
                  contentDescription = null,
                  tint = VoxSecondary,
                  modifier = Modifier.size(20.dp)
                )
              }
              Column {
                Text(
                  text = "Gemini AI Fallback",
                  style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                  text = "Hanya dipanggil jika parser lokal tidak mengenali perintah",
                  style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                )
              }
            }
            Switch(
              checked = aiStats.isEnabled,
              onCheckedChange = { viewModel.setAiEnabled(it) },
              modifier = Modifier.testTag("gemini_enabled_switch")
            )
          }

          HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

          SettingStatusRow(
            title = "Model AI",
            subtitle = "Firebase AI Logic & App Check",
            statusText = "gemini-3.8-flash",
            statusColor = VoxSecondary
          )

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column {
              Text(
                text = "Penggunaan Hari Ini",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
              )
              Text(
                text = "Sisa kuota: ${aiStats.remainingRequests} permintaan",
                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
              )
            }
            Text(
              text = "${aiStats.dailyCount} / ${aiStats.dailyLimit}",
              style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                color = if (aiStats.remainingRequests > 0) Color(0xFF10B981) else Color(0xFFEF4444)
              )
            )
          }

          Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
              text = "Atur Batas Kuota Harian:",
              style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              listOf(10, 20, 50).forEach { limit ->
                FilterChip(
                  selected = aiStats.dailyLimit == limit,
                  onClick = { viewModel.setAiDailyLimit(limit) },
                  label = { Text("$limit / hari") }
                )
              }
            }
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            val tokenText = if (aiStats.actualTotalTokens > 0) {
              "Total: ${aiStats.totalRequests} panggilan (${aiStats.actualTotalTokens} token aktual, ~${aiStats.estimatedTotalTokens} est)"
            } else {
              "Total: ${aiStats.totalRequests} panggilan (~${aiStats.estimatedTotalTokens} token est)"
            }
            Text(
              text = tokenText,
              style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
            )
            OutlinedButton(
              onClick = { viewModel.resetAiDailyCount() },
              contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
              Text("Reset Hari Ini", fontSize = 12.sp)
            }
          }
        }
      }
    }

    // 4. Database Statistics Card
    item {
      Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(18.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Box(
              modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(VoxSecondary.copy(alpha = 0.15f)),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = Icons.Default.Storage,
                contentDescription = null,
                tint = VoxSecondary,
                modifier = Modifier.size(20.dp)
              )
            }
            Text(
              text = "Statistik Data Lokal Room",
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
          }

          HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Text(text = "Total Pengingat (Reminders):", style = MaterialTheme.typography.bodyMedium)
            Text(text = "${stats.remindersCount}", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
          }
        }
      }
    }

    // 5. Data Reliability: Pencadangan & Pemulihan Data (Tahap 6C)
    item {
      Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth().testTag("backup_settings_card")
      ) {
        Column(
          modifier = Modifier.fillMaxWidth().padding(18.dp),
          verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Surface(
              shape = CircleShape,
              color = VoxPrimary.copy(alpha = 0.12f),
              modifier = Modifier.size(36.dp)
            ) {
              Box(contentAlignment = Alignment.Center) {
                Icon(
                  imageVector = Icons.Default.Security,
                  contentDescription = null,
                  tint = VoxPrimary,
                  modifier = Modifier.size(20.dp)
                )
              }
            }
            Column {
              Text(
                text = "Pencadangan & Pemulihan Data",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
              )
              Text(
                text = "Arsip ZIP lokal • Storage Access Framework",
                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
              )
            }
          }

          Text(
            text = "Cadangkan seluruh tugas, pengingat, dan rekaman catatan suara Anda ke berkas ZIP lokal di perangkat Anda, atau pulihkan data dari cadangan yang telah ada sebelumnya.",
            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
          )

          HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Button(
              onClick = {
                val defaultName = backupViewModel.generateDefaultBackupFileName()
                createDocumentLauncher.launch(defaultName)
              },
              colors = ButtonDefaults.buttonColors(containerColor = VoxPrimary),
              modifier = Modifier.weight(1f).testTag("backup_data_button")
            ) {
              Icon(
                imageVector = Icons.Default.Storage,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text("Cadangkan", style = MaterialTheme.typography.labelMedium)
            }

            OutlinedButton(
              onClick = {
                openDocumentLauncher.launch(
                  arrayOf(
                    "application/zip",
                    "application/octet-stream",
                    "application/x-zip-compressed"
                  )
                )
              },
              modifier = Modifier.weight(1f).testTag("restore_data_button")
            ) {
              Icon(
                imageVector = Icons.Default.Security,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text("Pulihkan", style = MaterialTheme.typography.labelMedium)
            }
          }
        }
      }
    }

    // 6. About VoxTask
    item {
      Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          Text(
            text = "VoxTask v1.1.0 (Smart Reminder)",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
          )
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = "Personal AI Voice Assistant • Local-First Architecture",
            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
          )
        }
      }
    }
  }

  // --- DIALOG PENANGANAN WORKFLOW BACKUP & RESTORE ---
  when (val state = backupUiState) {
    is BackupUiState.Preparing,
    is BackupUiState.InProgress,
    is BackupUiState.Validating,
    is BackupUiState.Restoring -> {
      val message = when (state) {
        is BackupUiState.Preparing -> state.message
        is BackupUiState.InProgress -> state.message
        is BackupUiState.Validating -> state.message
        is BackupUiState.Restoring -> state.message
        else -> "Memproses..."
      }
      AlertDialog(
        onDismissRequest = {},
        title = {
          Text("Memproses Data", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
        },
        text = {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(vertical = 8.dp)
          ) {
            CircularProgressIndicator(color = VoxPrimary, modifier = Modifier.size(36.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium)
          }
        },
        confirmButton = {}
      )
    }

    is BackupUiState.PreviewReady -> {
      val manifest = state.manifest
      val dateStr = SimpleDateFormat("EEEE, dd MMM yyyy HH:mm", Locale("id", "ID")).format(Date(manifest.createdAt))
      AlertDialog(
        onDismissRequest = { backupViewModel.cancelRestore() },
        title = {
          Text("Konfirmasi Pemulihan Data", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
        },
        text = {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
              text = "Berkas cadangan valid dan siap dipulihkan.",
              style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
            )
            Surface(
              shape = RoundedCornerShape(8.dp),
              color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
              modifier = Modifier.fillMaxWidth()
            ) {
              Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("• Tanggal Cadangan: $dateStr", style = MaterialTheme.typography.bodySmall)
                Text("• Perangkat Asal: ${manifest.deviceModel.ifBlank { "Tidak Diketahui" }}", style = MaterialTheme.typography.bodySmall)
                Text("• Jumlah Pengingat: ${manifest.counts.reminders}", style = MaterialTheme.typography.bodySmall)
              }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Surface(
              shape = RoundedCornerShape(8.dp),
              color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
              modifier = Modifier.fillMaxWidth()
            ) {
              Text(
                text = "⚠️ PERINGATAN: Seluruh data yang ada saat ini akan digantikan (Replace All). Alarm pengingat akan disinkronkan ulang.",
                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold),
                modifier = Modifier.padding(10.dp)
              )
            }
          }
        },
        confirmButton = {
          Button(
            onClick = { backupViewModel.confirmRestore() },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.testTag("confirm_restore_button")
          ) {
            Text("Gantikan & Pulihkan")
          }
        },
        dismissButton = {
          TextButton(
            onClick = { backupViewModel.cancelRestore() },
            modifier = Modifier.testTag("cancel_restore_button")
          ) {
            Text("Batal")
          }
        }
      )
    }

    is BackupUiState.Success -> {
      AlertDialog(
        onDismissRequest = { backupViewModel.dismissState() },
        title = {
          Text(state.title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = VoxSecondary))
        },
        text = {
          Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(state.message, style = MaterialTheme.typography.bodyMedium)
            if (state.details.isNotBlank()) {
              Text(state.details, style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
            }
          }
        },
        confirmButton = {
          Button(
            onClick = { backupViewModel.dismissState() },
            colors = ButtonDefaults.buttonColors(containerColor = VoxPrimary),
            modifier = Modifier.testTag("dismiss_backup_success_button")
          ) {
            Text("Selesai")
          }
        }
      )
    }

    is BackupUiState.Error -> {
      AlertDialog(
        onDismissRequest = { backupViewModel.dismissState() },
        title = {
          Text(state.title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error))
        },
        text = {
          Text(state.message, style = MaterialTheme.typography.bodyMedium)
        },
        confirmButton = {
          Button(
            onClick = { backupViewModel.dismissState() },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.testTag("dismiss_backup_error_button")
          ) {
            Text("Tutup")
          }
        }
      )
    }

    is BackupUiState.Idle -> {}
  }

  if (showPermissionModal) {
    FirstTimePermissionDialog(
      onDismiss = { showPermissionModal = false }
    )
  }
}

@Composable
private fun SettingStatusRow(
  title: String,
  subtitle: String,
  statusText: String,
  statusColor: Color
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = title,
        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
      )
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
      )
    }
    Surface(
      shape = RoundedCornerShape(8.dp),
      color = statusColor.copy(alpha = 0.12f)
    ) {
      Text(
        text = statusText,
        style = MaterialTheme.typography.labelSmall.copy(
          color = statusColor,
          fontWeight = FontWeight.Bold
        ),
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
      )
    }
  }
}

@Composable
private fun AiVoiceOptionRow(
  voice: TtsVoiceInfo,
  isSelected: Boolean,
  isDefaultActive: Boolean,
  onSelect: () -> Unit
) {
  val highlighted = isSelected || isDefaultActive
  Surface(
    shape = RoundedCornerShape(12.dp),
    color = if (highlighted) VoxPrimary.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
    border = if (isSelected) {
      BorderStroke(1.5.dp, VoxPrimary)
    } else if (isDefaultActive) {
      BorderStroke(1.dp, VoxSecondary.copy(alpha = 0.5f))
    } else {
      null
    },
    modifier = Modifier
      .fillMaxWidth()
      .heightIn(min = 56.dp)
      .clip(RoundedCornerShape(12.dp))
      .clickable(onClick = onSelect)
      .testTag("tts_voice_option_${voice.id}")
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      Icon(
        imageVector = if (highlighted) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
        contentDescription = if (highlighted) "Suara dipilih" else "Pilih suara",
        tint = if (isSelected) VoxPrimary else if (isDefaultActive) VoxSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(20.dp)
      )

      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = voice.displayName,
          style = MaterialTheme.typography.bodyMedium.copy(
            fontWeight = if (highlighted) FontWeight.Bold else FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
          ),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
        val networkLabel = if (voice.requiresNetwork) "Jaringan" else "Lokal"
        Text(
          text = "${voice.languageDisplayName} (${voice.localeTag}) • $networkLabel",
          style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }

      if (isSelected) {
        Surface(
          shape = RoundedCornerShape(6.dp),
          color = VoxPrimary.copy(alpha = 0.15f)
        ) {
          Text(
            text = "Dipilih",
            style = MaterialTheme.typography.labelSmall.copy(
              color = VoxPrimary,
              fontWeight = FontWeight.Bold
            ),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
          )
        }
      } else if (isDefaultActive) {
        Surface(
          shape = RoundedCornerShape(6.dp),
          color = VoxSecondary.copy(alpha = 0.15f)
        ) {
          Text(
            text = "Aktif",
            style = MaterialTheme.typography.labelSmall.copy(
              color = VoxSecondary,
              fontWeight = FontWeight.Bold
            ),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
          )
        }
      } else if (voice.isExactIndonesian) {
        Surface(
          shape = RoundedCornerShape(6.dp),
          color = Color(0xFF10B981).copy(alpha = 0.12f)
        ) {
          Text(
            text = "id-ID",
            style = MaterialTheme.typography.labelSmall.copy(
              color = Color(0xFF10B981),
              fontWeight = FontWeight.SemiBold
            ),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
          )
        }
      }
    }
  }
}

private class OpenAudioDocumentContract : ActivityResultContracts.OpenDocument() {
  override fun createIntent(context: Context, input: Array<String>): Intent {
    return super.createIntent(context, input).apply {
      addFlags(
        Intent.FLAG_GRANT_READ_URI_PERMISSION or
          Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
      )
    }
  }
}
