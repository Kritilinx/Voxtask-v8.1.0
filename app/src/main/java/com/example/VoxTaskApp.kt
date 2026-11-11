package com.example

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.reminder.AlarmSoundPlayer
import com.example.reminder.NotificationHelper
import com.example.ui.components.FirstTimePermissionDialog
import com.example.ui.navigation.VoxDestination
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.RemindersScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.VoxPrimary
import com.example.ui.viewmodel.BackupViewModel
import com.example.ui.viewmodel.VoxTaskViewModel
import kotlinx.coroutines.launch

@Composable
fun VoxTaskApp(
  viewModel: VoxTaskViewModel = viewModel(),
  backupViewModel: BackupViewModel = viewModel()
) {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()
  val snackbarHostState = remember { SnackbarHostState() }
  var currentDestination by remember { mutableStateOf(VoxDestination.HOME) }
  val alarmRingingState by AlarmSoundPlayer.ringingState.collectAsStateWithLifecycle()

  // Cek apakah dialog konfirmasi izin pertama kali sudah ditampilkan
  val prefs = remember { context.getSharedPreferences("voxtask_prefs", Context.MODE_PRIVATE) }
  var showPermissionDialog by remember {
    mutableStateOf(!prefs.getBoolean("has_completed_permission_onboarding", false))
  }

  // Microphone permission launcher for native Speech-to-Text
  val recordAudioLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission()
  ) { isGranted ->
    if (isGranted) {
      viewModel.triggerMicInteraction()
    } else {
      coroutineScope.launch {
        snackbarHostState.showSnackbar("Izin mikrofon diperlukan untuk perintah suara.")
      }
    }
  }

  val onMicClick: () -> Unit = {
    val hasPermission = ContextCompat.checkSelfPermission(
      context,
      Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED

    if (hasPermission) {
      viewModel.triggerMicInteraction()
    } else {
      recordAudioLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }
  }

  // Handle system back button to always return to HOME first if on another screen
  BackHandler(enabled = currentDestination != VoxDestination.HOME) {
    currentDestination = VoxDestination.HOME
  }

  Scaffold(
    contentWindowInsets = WindowInsets.safeDrawing,
    snackbarHost = { SnackbarHost(snackbarHostState) },
    bottomBar = {
      NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        modifier = Modifier
          .testTag("bottom_navigation_bar")
          .windowInsetsPadding(WindowInsets.navigationBars)
      ) {
        VoxDestination.entries.forEach { destination ->
          val isSelected = currentDestination == destination
          NavigationBarItem(
            selected = isSelected,
            onClick = { currentDestination = destination },
            icon = {
              Icon(
                imageVector = if (isSelected) destination.selectedIcon else destination.unselectedIcon,
                contentDescription = destination.label
              )
            },
            label = {
              Text(
                text = destination.label,
                style = MaterialTheme.typography.labelSmall
              )
            },
            colors = NavigationBarItemDefaults.colors(
              selectedIconColor = VoxPrimary,
              selectedTextColor = VoxPrimary,
              indicatorColor = VoxPrimary.copy(alpha = 0.15f)
            ),
            modifier = Modifier.testTag("nav_item_${destination.route}")
          )
        }
      }
    }
  ) { innerPadding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
    ) {
      // Active alarm ringing banner with instant Stop button
      AnimatedVisibility(
        visible = alarmRingingState.isRinging,
        enter = slideInVertically() + fadeIn(),
        exit = slideOutVertically() + fadeOut()
      ) {
        Surface(
          color = MaterialTheme.colorScheme.errorContainer,
          contentColor = MaterialTheme.colorScheme.onErrorContainer,
          shadowElevation = 6.dp,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("active_alarm_ringing_banner")
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Row(
              modifier = Modifier.weight(1f),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(
                imageVector = Icons.Default.NotificationsActive,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(26.dp)
              )
              Spacer(modifier = Modifier.width(10.dp))
              Column {
                Text(
                  text = "Alarm Berdering!",
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.error
                )
                Text(
                  text = alarmRingingState.title.ifBlank { "Waktu pengingat telah tiba" },
                  style = MaterialTheme.typography.bodySmall,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
              }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
              onClick = {
                val remId = alarmRingingState.reminderId
                AlarmSoundPlayer.stopAlarm(context)
                if (remId != null) {
                  NotificationHelper.cancelNotification(context, remId.toInt())
                }
              },
              colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError
              ),
              contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
              modifier = Modifier.testTag("stop_ringing_alarm_button")
            ) {
              Text("Matikan", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
          }
        }
      }

      Box(
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth()
      ) {
        AnimatedContent(
          targetState = currentDestination,
          transitionSpec = { fadeIn() togetherWith fadeOut() },
          label = "screen_transition"
        ) { destination ->
          when (destination) {
            VoxDestination.HOME -> HomeScreen(
              viewModel = viewModel,
              onNavigateTo = { dest -> currentDestination = dest },
              onMicClick = onMicClick
            )
            VoxDestination.REMINDERS -> RemindersScreen(viewModel = viewModel)
            VoxDestination.SETTINGS -> SettingsScreen(
              viewModel = viewModel,
              backupViewModel = backupViewModel
            )
          }
        }
      }
    }
  }

  // Dialog konfirmasi perizinan saat pertama kali aplikasi dibuka
  if (showPermissionDialog) {
    FirstTimePermissionDialog(
      onDismiss = {
        showPermissionDialog = false
        prefs.edit().putBoolean("has_completed_permission_onboarding", true).apply()
      }
    )
  }
}
