package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.entity.ReminderEntity
import com.example.ui.components.AddReminderDialog
import com.example.ui.components.EditReminderDialog
import com.example.ui.components.ReminderCard
import com.example.ui.theme.VoxPrimary
import com.example.ui.viewmodel.VoxTaskViewModel

enum class ReminderFilter {
  ALL,
  UPCOMING,
  COMPLETED
}

@Composable
fun RemindersScreen(
  viewModel: VoxTaskViewModel,
  modifier: Modifier = Modifier
) {
  val allReminders by viewModel.allReminders.collectAsStateWithLifecycle()
  var currentFilter by remember { mutableStateOf(ReminderFilter.ALL) }
  var showAddDialog by remember { mutableStateOf(false) }
  var editingReminder by remember { mutableStateOf<ReminderEntity?>(null) }

  val filteredReminders = remember(allReminders, currentFilter) {
    val now = System.currentTimeMillis()
    when (currentFilter) {
      ReminderFilter.ALL -> allReminders
      ReminderFilter.UPCOMING -> allReminders.filter { !it.isCompleted && it.dueDateTime >= now }
      ReminderFilter.COMPLETED -> allReminders.filter { it.isCompleted }
    }
  }

  Scaffold(
    modifier = modifier.fillMaxSize(),
    floatingActionButton = {
      FloatingActionButton(
        onClick = { showAddDialog = true },
        containerColor = VoxPrimary,
        contentColor = androidx.compose.ui.graphics.Color.White,
        modifier = Modifier.testTag("add_reminder_fab")
      ) {
        Icon(imageVector = Icons.Default.Add, contentDescription = "Tambah Pengingat")
      }
    }
  ) { innerPadding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .padding(horizontal = 20.dp)
        .testTag("reminders_screen")
    ) {
      Spacer(modifier = Modifier.height(16.dp))

      Text(
        text = "My Reminders",
        style = MaterialTheme.typography.displayMedium.copy(
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onBackground
        )
      )
      Text(
        text = "Pengingat offline tersimpan & terjadwal di perangkat",
        style = MaterialTheme.typography.bodyMedium.copy(
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      )

      Spacer(modifier = Modifier.height(14.dp))

      // Filter chips
      Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        FilterChip(
          selected = currentFilter == ReminderFilter.ALL,
          onClick = { currentFilter = ReminderFilter.ALL },
          label = { Text("Semua (${allReminders.size})") }
        )
        FilterChip(
          selected = currentFilter == ReminderFilter.UPCOMING,
          onClick = { currentFilter = ReminderFilter.UPCOMING },
          label = { Text("Mendatang") }
        )
        FilterChip(
          selected = currentFilter == ReminderFilter.COMPLETED,
          onClick = { currentFilter = ReminderFilter.COMPLETED },
          label = { Text("Selesai") }
        )
      }

      Spacer(modifier = Modifier.height(12.dp))

      if (filteredReminders.isEmpty()) {
        EmptyCardPlaceholder(
          message = when (currentFilter) {
            ReminderFilter.ALL -> "Belum ada pengingat. Buat pengingat baru sekarang!"
            ReminderFilter.UPCOMING -> "Tidak ada pengingat mendatang."
            ReminderFilter.COMPLETED -> "Belum ada pengingat yang diselesaikan."
          },
          actionText = if (currentFilter == ReminderFilter.ALL) "+ Buat Pengingat" else null,
          onAction = { showAddDialog = true }
        )
      } else {
        LazyColumn(
          verticalArrangement = Arrangement.spacedBy(10.dp),
          contentPadding = PaddingValues(bottom = 120.dp)
        ) {
          items(filteredReminders, key = { it.id }) { reminder ->
            ReminderCard(
              reminder = reminder,
              onToggle = { viewModel.toggleReminder(reminder) },
              onDelete = { viewModel.deleteReminder(reminder.id) },
              onEdit = { editingReminder = reminder }
            )
          }
        }
      }
    }
  }

  if (showAddDialog) {
    AddReminderDialog(
      onDismiss = { showAddDialog = false },
      onConfirm = { title, desc, due, prio, cat, repeat ->
        viewModel.addReminder(title, desc, due, prio, cat, repeat)
        showAddDialog = false
      }
    )
  }

  editingReminder?.let { reminderToEdit ->
    EditReminderDialog(
      reminder = reminderToEdit,
      onDismiss = { editingReminder = null },
      onConfirm = { updated ->
        viewModel.updateReminder(updated)
        editingReminder = null
      }
    )
  }
}
