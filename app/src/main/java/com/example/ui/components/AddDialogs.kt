package com.example.ui.components

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.entity.ReminderEntity
import com.example.data.model.Priority
import com.example.data.model.RepeatMode
import com.example.ui.theme.VoxPrimary
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale


@Composable
fun AddReminderDialog(
  onDismiss: () -> Unit,
  onConfirm: (title: String, description: String, dueDateTime: Long, priority: Priority, category: String, repeatMode: RepeatMode) -> Unit
) {
  val context = LocalContext.current
  var title by remember { mutableStateOf("") }
  var description by remember { mutableStateOf("") }
  var selectedRepeatMode by remember { mutableStateOf(RepeatMode.ONCE) }

  val defaultDue = remember {
    val cal = Calendar.getInstance()
    cal.add(Calendar.HOUR_OF_DAY, 1)
    cal.timeInMillis
  }
  var dueTimestamp by remember { mutableStateOf(defaultDue) }

  val dateFormatter = SimpleDateFormat("dd MMMM yyyy", Locale("id", "ID"))
  val timeFormatter = SimpleDateFormat("HH:mm", Locale("id", "ID"))

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(
        text = "Tambah Pengingat Baru",
        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
      )
    },
    text = {
      Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.verticalScroll(rememberScrollState())
      ) {
        OutlinedTextField(
          value = title,
          onValueChange = { title = it },
          label = { Text("Nama Pengingat") },
          singleLine = true,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("reminder_title_input")
        )

        OutlinedTextField(
          value = description,
          onValueChange = { description = it },
          label = { Text("Catatan Tambahan (opsional)") },
          maxLines = 2,
          modifier = Modifier.fillMaxWidth()
        )

        Text(
          text = "Jadwal Tanggal & Jam",
          style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
        )

        // Date and Time selector buttons
        Row(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          OutlinedButton(
            onClick = {
              val cal = Calendar.getInstance().apply { timeInMillis = dueTimestamp }
              DatePickerDialog(
                context,
                { _, year, month, dayOfMonth ->
                  val newCal = Calendar.getInstance().apply {
                    timeInMillis = dueTimestamp
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, dayOfMonth)
                  }
                  dueTimestamp = newCal.timeInMillis
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
              ).show()
            },
            modifier = Modifier.weight(1f)
          ) {
            Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
            Text(dateFormatter.format(dueTimestamp), style = MaterialTheme.typography.labelSmall)
          }

          OutlinedButton(
            onClick = {
              val cal = Calendar.getInstance().apply { timeInMillis = dueTimestamp }
              TimePickerDialog(
                context,
                { _, hourOfDay, minute ->
                  val newCal = Calendar.getInstance().apply {
                    timeInMillis = dueTimestamp
                    set(Calendar.HOUR_OF_DAY, hourOfDay)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                  }
                  dueTimestamp = newCal.timeInMillis
                },
                cal.get(Calendar.HOUR_OF_DAY),
                cal.get(Calendar.MINUTE),
                true
              ).show()
            },
            modifier = Modifier.weight(0.8f)
          ) {
            Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
            Text(timeFormatter.format(dueTimestamp), style = MaterialTheme.typography.labelSmall)
          }
        }

        // Preset Time Quick Chips
        Row(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          PresetTimeChip("+30 Min") {
            val c = Calendar.getInstance()
            c.add(Calendar.MINUTE, 30)
            dueTimestamp = c.timeInMillis
          }
          PresetTimeChip("+1 Jam") {
            val c = Calendar.getInstance()
            c.add(Calendar.HOUR_OF_DAY, 1)
            dueTimestamp = c.timeInMillis
          }
          PresetTimeChip("Besok") {
            val c = Calendar.getInstance()
            c.add(Calendar.DAY_OF_YEAR, 1)
            c.set(Calendar.HOUR_OF_DAY, 9)
            c.set(Calendar.MINUTE, 0)
            dueTimestamp = c.timeInMillis
          }
        }

        Text(
          text = "Pengulangan (Recurrence)",
          style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
        )

        Row(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          RepeatMode.entries.forEach { mode ->
            FilterChip(
              selected = selectedRepeatMode == mode,
              onClick = { selectedRepeatMode = mode },
              label = { Text(mode.displayName, style = MaterialTheme.typography.labelSmall) }
            )
          }
        }

      }
    },
    confirmButton = {
      Button(
        onClick = {
          if (title.isNotBlank()) {
            onConfirm(title, description, dueTimestamp, Priority.MEDIUM, "General", selectedRepeatMode)
          }
        },
        enabled = title.isNotBlank(),
        colors = ButtonDefaults.buttonColors(containerColor = VoxPrimary),
        modifier = Modifier.testTag("save_reminder_button")
      ) {
        Text("Simpan Pengingat")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Batal")
      }
    }
  )
}

@Composable
fun EditReminderDialog(
  reminder: ReminderEntity,
  onDismiss: () -> Unit,
  onConfirm: (updated: ReminderEntity) -> Unit
) {
  val context = LocalContext.current
  var title by remember { mutableStateOf(reminder.title) }
  var description by remember { mutableStateOf(reminder.description) }
  var selectedRepeatMode by remember { mutableStateOf(reminder.repeatMode) }
  var dueTimestamp by remember { mutableStateOf(reminder.dueDateTime) }

  val dateFormatter = SimpleDateFormat("dd MMMM yyyy", Locale("id", "ID"))
  val timeFormatter = SimpleDateFormat("HH:mm", Locale("id", "ID"))

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(
        text = "Edit Pengingat",
        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
      )
    },
    text = {
      Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.verticalScroll(rememberScrollState())
      ) {
        OutlinedTextField(
          value = title,
          onValueChange = { title = it },
          label = { Text("Nama Pengingat") },
          singleLine = true,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("edit_reminder_title_input")
        )

        OutlinedTextField(
          value = description,
          onValueChange = { description = it },
          label = { Text("Catatan Tambahan (opsional)") },
          maxLines = 2,
          modifier = Modifier.fillMaxWidth()
        )

        Text(
          text = "Jadwal Tanggal & Jam",
          style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
        )

        Row(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          OutlinedButton(
            onClick = {
              val cal = Calendar.getInstance().apply { timeInMillis = dueTimestamp }
              DatePickerDialog(
                context,
                { _, year, month, dayOfMonth ->
                  val newCal = Calendar.getInstance().apply {
                    timeInMillis = dueTimestamp
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, dayOfMonth)
                  }
                  dueTimestamp = newCal.timeInMillis
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
              ).show()
            },
            modifier = Modifier.weight(1f)
          ) {
            Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
            Text(dateFormatter.format(dueTimestamp), style = MaterialTheme.typography.labelSmall)
          }

          OutlinedButton(
            onClick = {
              val cal = Calendar.getInstance().apply { timeInMillis = dueTimestamp }
              TimePickerDialog(
                context,
                { _, hourOfDay, minute ->
                  val newCal = Calendar.getInstance().apply {
                    timeInMillis = dueTimestamp
                    set(Calendar.HOUR_OF_DAY, hourOfDay)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                  }
                  dueTimestamp = newCal.timeInMillis
                },
                cal.get(Calendar.HOUR_OF_DAY),
                cal.get(Calendar.MINUTE),
                true
              ).show()
            },
            modifier = Modifier.weight(0.8f)
          ) {
            Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
            Text(timeFormatter.format(dueTimestamp), style = MaterialTheme.typography.labelSmall)
          }
        }

        Text(
          text = "Pengulangan (Recurrence)",
          style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
        )

        Row(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          RepeatMode.entries.forEach { mode ->
            FilterChip(
              selected = selectedRepeatMode == mode,
              onClick = { selectedRepeatMode = mode },
              label = { Text(mode.displayName, style = MaterialTheme.typography.labelSmall) }
            )
          }
        }

      }
    },
    confirmButton = {
      Button(
        onClick = {
          if (title.isNotBlank()) {
            onConfirm(
              reminder.copy(
                title = title.trim(),
                description = description.trim(),
                dueDateTime = dueTimestamp,
                repeatMode = selectedRepeatMode
              )
            )
          }
        },
        enabled = title.isNotBlank(),
        colors = ButtonDefaults.buttonColors(containerColor = VoxPrimary),
        modifier = Modifier.testTag("update_reminder_button")
      ) {
        Text("Perbarui")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Batal")
      }
    }
  )
}

@Composable
private fun PresetTimeChip(label: String, onClick: () -> Unit) {
  Surface(
    shape = RoundedCornerShape(8.dp),
    color = MaterialTheme.colorScheme.surfaceVariant,
    modifier = Modifier.clickable(onClick = onClick)
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
    )
  }
}

