package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.VolumeUp
import com.example.data.model.RepeatMode
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.ReminderEntity
import com.example.ui.theme.VoxPrimary
import com.example.ui.theme.VoxSecondary
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun ReminderCard(
  reminder: ReminderEntity,
  onToggle: () -> Unit,
  onDelete: () -> Unit,
  onEdit: (() -> Unit)? = null,
  modifier: Modifier = Modifier
) {
  val timeFormat = SimpleDateFormat("HH:mm • dd MMM", Locale("id", "ID"))
  val formattedTime = timeFormat.format(reminder.dueDateTime)

  Card(
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(
      containerColor = if (reminder.isCompleted) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
      } else {
        MaterialTheme.colorScheme.surface
      }
    ),
    elevation = CardDefaults.cardElevation(defaultElevation = if (reminder.isCompleted) 0.dp else 2.dp),
    modifier = modifier
      .fillMaxWidth()
      .testTag("reminder_card_${reminder.id}")
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(14.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Checkbox(
        checked = reminder.isCompleted,
        onCheckedChange = { onToggle() },
        colors = CheckboxDefaults.colors(
          checkedColor = VoxPrimary,
          uncheckedColor = MaterialTheme.colorScheme.outline
        ),
        modifier = Modifier.testTag("reminder_check_${reminder.id}")
      )

      Spacer(modifier = Modifier.width(8.dp))

      Column(modifier = Modifier.weight(1f)) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          Surface(
            shape = RoundedCornerShape(6.dp),
            color = VoxPrimary.copy(alpha = 0.12f)
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
              Icon(
                imageVector = Icons.Default.Alarm,
                contentDescription = null,
                tint = VoxPrimary,
                modifier = Modifier.size(12.dp)
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                text = formattedTime,
                style = MaterialTheme.typography.labelSmall.copy(
                  color = VoxPrimary,
                  fontWeight = FontWeight.Medium
                )
              )
            }
          }

          if (reminder.repeatMode != RepeatMode.ONCE) {
            Surface(
              shape = RoundedCornerShape(6.dp),
              color = VoxSecondary.copy(alpha = 0.15f)
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
              ) {
                Icon(
                  imageVector = Icons.Default.Repeat,
                  contentDescription = null,
                  tint = VoxSecondary,
                  modifier = Modifier.size(11.dp)
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                  text = reminder.repeatMode.displayName,
                  style = MaterialTheme.typography.labelSmall.copy(
                    color = VoxSecondary,
                    fontWeight = FontWeight.SemiBold
                  )
                )
              }
            }
          }

        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
          text = reminder.title,
          style = MaterialTheme.typography.titleMedium.copy(
            fontWeight = FontWeight.SemiBold,
            textDecoration = if (reminder.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
            color = if (reminder.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
          ),
          maxLines = 2,
          overflow = TextOverflow.Ellipsis
        )

        if (reminder.description.isNotBlank()) {
          Spacer(modifier = Modifier.height(2.dp))
          Text(
            text = reminder.description,
            style = MaterialTheme.typography.bodySmall.copy(
              color = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
          )
        }
      }

      Row(verticalAlignment = Alignment.CenterVertically) {
        if (onEdit != null) {
          IconButton(
            onClick = onEdit,
            modifier = Modifier
              .size(36.dp)
              .testTag("edit_reminder_${reminder.id}")
          ) {
            Icon(
              imageVector = Icons.Default.Edit,
              contentDescription = "Edit pengingat",
              tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
              modifier = Modifier.size(18.dp)
            )
          }
        }

        IconButton(
          onClick = onDelete,
          modifier = Modifier
            .size(36.dp)
            .testTag("delete_reminder_${reminder.id}")
        ) {
          Icon(
            imageVector = Icons.Default.Delete,
            contentDescription = "Hapus pengingat",
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(18.dp)
          )
        }
      }
    }
  }
}

