package com.example.ui.components

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.ui.theme.VoxPrimary
import com.example.ui.theme.VoxSecondary

/**
 * Dialog konfirmasi perizinan akses untuk pertama kali membuka aplikasi VoxTask,
 * mencakup Microphone (perintah suara), Notifikasi (pengingat), dan Alarm Tepat Waktu.
 */
@Composable
fun FirstTimePermissionDialog(
  onDismiss: () -> Unit
) {
  val context = LocalContext.current
  val scrollState = rememberScrollState()

  // State status perizinan saat ini
  var hasMicPermission by remember {
    mutableStateOf(
      ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    )
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

  val alarmManager = remember { context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager }
  var hasExactAlarmPermission by remember {
    mutableStateOf(
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        alarmManager?.canScheduleExactAlarms() == true
      } else {
        true
      }
    )
  }

  // Launcher perizinan ganda untuk Mic dan Notifikasi
  val multiplePermissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestMultiplePermissions()
  ) { permissions ->
    hasMicPermission = permissions[Manifest.permission.RECORD_AUDIO] ?: hasMicPermission
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      hasNotificationPermission = permissions[Manifest.permission.POST_NOTIFICATIONS] ?: hasNotificationPermission
    }
    // Jika semua sudah direspons, tutup dialog secara mulus
    onDismiss()
  }

  val micPermissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission()
  ) { granted ->
    hasMicPermission = granted
  }

  val notificationPermissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission()
  ) { granted ->
    hasNotificationPermission = granted
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    modifier = Modifier
      .fillMaxWidth()
      .testTag("first_time_permission_dialog"),
    title = {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        Box(
          modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(VoxPrimary.copy(alpha = 0.15f)),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = Icons.Default.Security,
            contentDescription = null,
            tint = VoxPrimary,
            modifier = Modifier.size(22.dp)
          )
        }
        Column {
          Text(
            text = "Izin Akses Aplikasi",
            style = MaterialTheme.typography.titleLarge.copy(
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onSurface
            )
          )
          Text(
            text = "Dibutuhkan untuk fitur VoxTask",
            style = MaterialTheme.typography.bodySmall.copy(
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          )
        }
      }
    },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        Text(
          text = "Agar asisten suara dan pengingat bekerja optimal di latar belakang, mohon izinkan akses berikut:",
          style = MaterialTheme.typography.bodyMedium.copy(
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        )

        // 1. Microphone Permission Item
        PermissionItemCard(
          icon = Icons.Default.Mic,
          title = "Mikrofon (Voice Assistant)",
          description = "Digunakan untuk pengenalan suara saat Anda membuat pengingat hands-free.",
          isGranted = hasMicPermission,
          onRequestClick = {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
          }
        )

        // 2. Notification Permission Item
        PermissionItemCard(
          icon = Icons.Default.Notifications,
          title = "Notifikasi (Pemberitahuan)",
          description = "Menampilkan tanda pengingat saat alarm berdering di layar atau status bar.",
          isGranted = hasNotificationPermission,
          onRequestClick = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
              notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
          }
        )

        // 3. Exact Alarm Permission Item
        PermissionItemCard(
          icon = Icons.Default.Alarm,
          title = "Alarm Tepat Waktu (Exact)",
          description = "Menjamin pengingat berbunyi tepat waktu meski perangkat dalam mode hemat daya.",
          isGranted = hasExactAlarmPermission,
          onRequestClick = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
              try {
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                  data = Uri.parse("package:${context.packageName}")
                }
                context.startActivity(intent)
              } catch (_: Exception) {}
            }
          }
        )

        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = "🔒 Privasi Anda terjaga: Data suara diolah langsung di perangkat (offline-first) dan tidak dikirim ke server.",
          style = MaterialTheme.typography.bodySmall.copy(
            color = VoxSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
          )
        )
      }
    },
    confirmButton = {
      Button(
        onClick = {
          val needed = mutableListOf<String>()
          if (!hasMicPermission) needed.add(Manifest.permission.RECORD_AUDIO)
          if (!hasNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS)
          }

          if (needed.isNotEmpty()) {
            multiplePermissionLauncher.launch(needed.toTypedArray())
          } else {
            onDismiss()
          }
        },
        colors = ButtonDefaults.buttonColors(containerColor = VoxPrimary),
        modifier = Modifier.testTag("confirm_all_permissions_button")
      ) {
        val allGranted = hasMicPermission && hasNotificationPermission && hasExactAlarmPermission
        Text(if (allGranted) "Selesai & Lanjutkan" else "Izinkan Semua & Lanjut")
      }
    },
    dismissButton = {
      TextButton(
        onClick = onDismiss,
        modifier = Modifier.testTag("skip_permissions_button")
      ) {
        Text("Lewati")
      }
    }
  )
}

@Composable
private fun PermissionItemCard(
  icon: ImageVector,
  title: String,
  description: String,
  isGranted: Boolean,
  onRequestClick: () -> Unit
) {
  Card(
    shape = RoundedCornerShape(14.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ),
    modifier = Modifier.fillMaxWidth()
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      Box(
        modifier = Modifier
          .size(36.dp)
          .clip(CircleShape)
          .background(
            if (isGranted) Color(0xFF10B981).copy(alpha = 0.15f)
            else VoxPrimary.copy(alpha = 0.15f)
          ),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = if (isGranted) Color(0xFF10B981) else VoxPrimary,
          modifier = Modifier.size(20.dp)
        )
      }

      Column(modifier = Modifier.weight(1f)) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          Text(
            text = title,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
          )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
          text = description,
          style = MaterialTheme.typography.bodySmall.copy(
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp
          )
        )
      }

      if (isGranted) {
        Surface(
          shape = RoundedCornerShape(8.dp),
          color = Color(0xFF10B981).copy(alpha = 0.15f)
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            Icon(
              imageVector = Icons.Default.Check,
              contentDescription = null,
              tint = Color(0xFF10B981),
              modifier = Modifier.size(14.dp)
            )
            Text(
              text = "Aktif",
              style = MaterialTheme.typography.labelSmall.copy(
                color = Color(0xFF10B981),
                fontWeight = FontWeight.Bold
              )
            )
          }
        }
      } else {
        Button(
          onClick = onRequestClick,
          contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
          shape = RoundedCornerShape(8.dp),
          colors = ButtonDefaults.buttonColors(containerColor = VoxPrimary)
        ) {
          Text("Izinkan", style = MaterialTheme.typography.labelSmall)
        }
      }
    }
  }
}
