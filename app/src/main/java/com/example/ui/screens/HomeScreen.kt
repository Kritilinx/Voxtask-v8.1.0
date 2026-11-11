package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import com.example.voice.VoiceAssistantState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.entity.ReminderEntity
import com.example.ui.components.AddReminderDialog
import com.example.ui.components.EditReminderDialog
import com.example.ui.components.MicOrbButton
import com.example.ui.components.ReminderCard
import com.example.ui.navigation.VoxDestination
import com.example.ui.theme.VoxPrimary
import com.example.ui.theme.VoxSecondary
import com.example.ui.viewmodel.VoxTaskViewModel

@Composable
fun HomeScreen(
  viewModel: VoxTaskViewModel,
  onNavigateTo: (VoxDestination) -> Unit,
  onMicClick: () -> Unit = { viewModel.triggerMicInteraction() },
  modifier: Modifier = Modifier
) {
  val assistantState by viewModel.assistantState.collectAsStateWithLifecycle()
  val voiceSession by viewModel.voiceSession.collectAsStateWithLifecycle()
  val lastRecognizedText by viewModel.lastRecognizedText.collectAsStateWithLifecycle()
  val stats by viewModel.stats.collectAsStateWithLifecycle()
  val allReminders by viewModel.allReminders.collectAsStateWithLifecycle()
  val upcomingReminders by viewModel.upcomingReminders.collectAsStateWithLifecycle()
  val todayReminders by viewModel.todayReminders.collectAsStateWithLifecycle()
  val parserDebugEnabled by viewModel.parserDebugEnabled.collectAsStateWithLifecycle()

  var showAddReminderDialog by remember { mutableStateOf(false) }
  var editingReminder by remember { mutableStateOf<ReminderEntity?>(null) }

  LazyColumn(
    modifier = modifier
      .fillMaxSize()
      .testTag("home_screen"),
    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp),
    verticalArrangement = Arrangement.spacedBy(18.dp)
  ) {
    // 1. Header with contextual greeting and date
    item {
      Column(modifier = Modifier.fillMaxWidth()) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column {
            Text(
              text = viewModel.getGreeting(),
              style = MaterialTheme.typography.displayMedium.copy(
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
              ),
              modifier = Modifier.testTag("home_greeting_text")
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = viewModel.getTodayFormattedDate(),
              style = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            )
          }

          // Local-First badge
          Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
              Box(
                modifier = Modifier
                  .size(8.dp)
                  .clip(CircleShape)
                  .background(Color(0xFF10B981))
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = "Offline Mode",
                style = MaterialTheme.typography.labelSmall.copy(
                  fontWeight = FontWeight.SemiBold,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              )
            }
          }
        }
      }
    }

    // 2. Large Central Microphone Control Center
    item {
      Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
          containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp, horizontal = 16.dp),
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          MicOrbButton(
            assistantState = assistantState,
            onClick = onMicClick
          )

          // 2a. Live State Indicator Badge (Conversational State Management)
          val stateBadgeInfo: Pair<String, Color>? = when (assistantState) {
            is VoiceAssistantState.Listening -> Pair("Mendengarkan ucapan...", Color(0xFF10B981))
            is VoiceAssistantState.Processing -> {
              val speech = (assistantState as VoiceAssistantState.Processing).recognizedSpeech
              val label = if (speech.contains("Gemini", ignoreCase = true)) "Gemini AI menganalisis..." else "Memproses perintah..."
              Pair(label, VoxSecondary)
            }
            is VoiceAssistantState.Speaking -> Pair("VoxTask Berbicara...", VoxPrimary)
            is VoiceAssistantState.WaitingForAnswer -> Pair("Menunggu Jawaban Anda (30 dtk)...", Color(0xFFF59E0B))
            is VoiceAssistantState.AwaitingConfirmation -> Pair("Menunggu Konfirmasi...", Color(0xFFEA580C))
            is VoiceAssistantState.Error -> Pair((assistantState as VoiceAssistantState.Error).errorMessage, Color(0xFFEF4444))
            else -> null
          }

          if (stateBadgeInfo != null) {
            Spacer(modifier = Modifier.height(10.dp))
            Surface(
              shape = RoundedCornerShape(16.dp),
              color = stateBadgeInfo.second.copy(alpha = 0.12f),
              border = androidx.compose.foundation.BorderStroke(1.dp, stateBadgeInfo.second.copy(alpha = 0.35f))
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
              ) {
                if (assistantState is VoiceAssistantState.Processing) {
                  CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    strokeWidth = 2.dp,
                    color = stateBadgeInfo.second
                  )
                } else {
                  Box(
                    modifier = Modifier
                      .size(8.dp)
                      .clip(CircleShape)
                      .background(stateBadgeInfo.second)
                  )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                  text = stateBadgeInfo.first,
                  style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = stateBadgeInfo.second
                  ),
                  modifier = Modifier.testTag("conversation_state_badge")
                )
              }
            }
          }

          // 2b. Live Speech Transcription Preview (when recognizing)
          if (voiceSession == null && lastRecognizedText.isNotBlank()) {
            Spacer(modifier = Modifier.height(10.dp))
            Surface(
              shape = RoundedCornerShape(12.dp),
              color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
              modifier = Modifier.fillMaxWidth().testTag("live_transcription_preview")
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
              ) {
                Icon(
                  imageVector = Icons.Default.Mic,
                  contentDescription = null,
                  tint = VoxPrimary,
                  modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                  text = "\"$lastRecognizedText\"",
                  style = MaterialTheme.typography.bodyMedium.copy(
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurface
                  )
                )
              }
            }
          }

          // 2c. Voice Session Result, Multi-Turn Clarification & Confirmation Panel
          voiceSession?.let { session ->
            Spacer(modifier = Modifier.height(12.dp))
            Card(
              shape = RoundedCornerShape(16.dp),
              colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
              ),
              modifier = Modifier
                .fillMaxWidth()
                .testTag("voice_session_card")
            ) {
              Column(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
              ) {
                // Transcription Header
                if (session.transcription.isNotBlank()) {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Row(
                      verticalAlignment = Alignment.CenterVertically,
                      modifier = Modifier.weight(1f)
                    ) {
                      Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = null,
                        tint = VoxSecondary,
                        modifier = Modifier.size(16.dp)
                      )
                      Spacer(modifier = Modifier.width(6.dp))
                      Text(
                        text = "\"${session.transcription}\"",
                        style = MaterialTheme.typography.bodyMedium.copy(
                          fontWeight = FontWeight.Medium,
                          color = MaterialTheme.colorScheme.onSurface
                        )
                      )
                    }
                    IconButton(
                      onClick = { viewModel.cancelVoiceSession() },
                      modifier = Modifier.size(24.dp).testTag("close_voice_session_button")
                    ) {
                      Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Tutup Respon Suara",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                      )
                    }
                  }
                }

                // Assistant Response
                if (session.assistantResponse.isNotBlank()) {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                  ) {
                    Icon(
                      imageVector = Icons.Default.RecordVoiceOver,
                      contentDescription = null,
                      tint = if (session.isAiResponse) VoxSecondary else VoxPrimary,
                      modifier = Modifier.size(18.dp).padding(top = 2.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                      if (session.isAiResponse) {
                        Surface(
                          shape = RoundedCornerShape(6.dp),
                          color = VoxSecondary.copy(alpha = 0.12f),
                          modifier = Modifier.padding(bottom = 4.dp)
                        ) {
                          Text(
                            text = "✨ Gemini AI Fallback",
                            style = MaterialTheme.typography.labelSmall.copy(
                              fontWeight = FontWeight.Bold,
                              color = VoxSecondary,
                              fontSize = 10.sp
                            ),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                          )
                        }
                      }
                      Text(
                        text = session.assistantResponse,
                        style = MaterialTheme.typography.bodyMedium.copy(
                          color = MaterialTheme.colorScheme.onSurface,
                          fontWeight = FontWeight.Normal
                        ),
                        modifier = Modifier.testTag("assistant_response_text")
                      )
                    }
                  }
                }

                // Ambiguous Matching candidate options
                if (session.candidateOptions.isNotEmpty()) {
                  Spacer(modifier = Modifier.height(4.dp))
                  Text(
                    text = "Pilih salah satu item di bawah:",
                    style = MaterialTheme.typography.labelMedium.copy(
                      fontWeight = FontWeight.SemiBold,
                      color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                  )
                  Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                  ) {
                    session.candidateOptions.forEachIndexed { idx, optionText ->
                      OutlinedButton(
                        onClick = { viewModel.selectCandidateOption(idx) },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                          .fillMaxWidth()
                          .testTag("ambiguous_candidate_button_$idx")
                      ) {
                        Text(
                          text = optionText,
                          style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium)
                        )
                      }
                    }
                  }
                }

                // Confirmation Buttons (when pending action requires user agreement)
                session.pendingAction?.let {
                  Spacer(modifier = Modifier.height(2.dp))
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                  ) {
                    Button(
                      onClick = { viewModel.confirmPendingAction() },
                      colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                      shape = RoundedCornerShape(12.dp),
                      modifier = Modifier
                        .weight(1f)
                        .testTag("confirm_voice_action_button")
                    ) {
                      Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                      Spacer(modifier = Modifier.width(6.dp))
                      Text("Ya, Lanjutkan")
                    }
                    OutlinedButton(
                      onClick = { viewModel.cancelPendingAction() },
                      shape = RoundedCornerShape(12.dp),
                      modifier = Modifier
                        .weight(1f)
                        .testTag("cancel_voice_action_button")
                    ) {
                      Icon(imageVector = Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                      Spacer(modifier = Modifier.width(6.dp))
                      Text("Batal")
                    }
                  }
                }

                // Session cancellation button for active Multi-Turn conversation
                if (session.pendingAction == null && (
                      session.multiTurnContext != null ||
                      assistantState is VoiceAssistantState.WaitingForAnswer ||
                      assistantState is VoiceAssistantState.AwaitingConfirmation
                    )) {
                  Spacer(modifier = Modifier.height(2.dp))
                  OutlinedButton(
                    onClick = { viewModel.cancelVoiceSession() },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                      .fillMaxWidth()
                      .testTag("cancel_conversational_session_button")
                  ) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Hentikan Sesi Percakapan")
                  }
                }

                // Parser Debug Trace Card (Stage 7E.2)
                if (parserDebugEnabled && session.parserDebugTrace != null) {
                  Spacer(modifier = Modifier.height(4.dp))
                  com.example.ui.components.ParserDebugTraceCard(trace = session.parserDebugTrace)
                }
              }
            }
          }

          Spacer(modifier = Modifier.height(14.dp))

          // Quick prompt action cards (sized evenly to prevent text truncation)
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            QuickVoiceActionCard(
              label = "+ Pengingat",
              onClick = { showAddReminderDialog = true },
              modifier = Modifier
                .weight(1f)
                .testTag("quick_action_add_reminder")
            )
            QuickVoiceActionCard(
              label = "Agenda Hari Ini",
              onClick = { viewModel.processVoiceCommand("agenda hari ini") },
              modifier = Modifier
                .weight(1f)
                .testTag("quick_action_today_agenda")
            )
            QuickVoiceActionCard(
              label = "Pengingat Berikutnya",
              onClick = { viewModel.processVoiceCommand("pengingat berikutnya") },
              modifier = Modifier
                .weight(1f)
                .testTag("quick_action_next_reminder")
            )
          }
        }
      }
    }

    // 3. Stats Overview Row
    item {
      val completedCount = remember(allReminders) { allReminders.count { it.isCompleted } }
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        StatOverviewCard(
          title = "Pengingat Aktif",
          count = stats.remindersCount,
          icon = Icons.Default.Alarm,
          tint = VoxPrimary,
          onClick = { onNavigateTo(VoxDestination.REMINDERS) },
          modifier = Modifier.weight(1f)
        )
        StatOverviewCard(
          title = "Hari Ini",
          count = todayReminders.size,
          icon = Icons.Default.CheckCircle,
          tint = VoxSecondary,
          onClick = { onNavigateTo(VoxDestination.REMINDERS) },
          modifier = Modifier.weight(1f)
        )
        StatOverviewCard(
          title = "Selesai",
          count = completedCount,
          icon = Icons.Default.CheckCircle,
          tint = Color(0xFF10B981),
          onClick = { onNavigateTo(VoxDestination.REMINDERS) },
          modifier = Modifier.weight(1f)
        )
      }
    }

    // 4. Pengingat Hari Ini (Today's Reminders)
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "Pengingat Hari Ini",
          style = MaterialTheme.typography.titleLarge.copy(
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
          )
        )
        Text(
          text = "Lihat Semua",
          style = MaterialTheme.typography.labelLarge.copy(
            color = VoxPrimary,
            fontWeight = FontWeight.SemiBold
          ),
          modifier = Modifier
            .clickable { onNavigateTo(VoxDestination.REMINDERS) }
            .padding(4.dp)
        )
      }
    }

    if (todayReminders.isEmpty()) {
      item {
        EmptyCardPlaceholder(
          message = "Tidak ada jadwal pengingat untuk hari ini.",
          actionText = "+ Buat Pengingat",
          onAction = { showAddReminderDialog = true }
        )
      }
    } else {
      items(todayReminders, key = { "home_today_reminder_${it.id}" }) { reminder ->
        ReminderCard(
          reminder = reminder,
          onToggle = { viewModel.toggleReminder(reminder) },
          onDelete = { viewModel.deleteReminder(reminder.id) },
          onEdit = { editingReminder = reminder }
        )
      }
    }

    // 5. Daftar Pengingat Mendatang (Upcoming Reminders)
    item {
      Spacer(modifier = Modifier.height(6.dp))
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "Pengingat Mendatang",
          style = MaterialTheme.typography.titleLarge.copy(
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
          )
        )
        Text(
          text = "Lihat Semua",
          style = MaterialTheme.typography.labelLarge.copy(
            color = VoxPrimary,
            fontWeight = FontWeight.SemiBold
          ),
          modifier = Modifier
            .clickable { onNavigateTo(VoxDestination.REMINDERS) }
            .padding(4.dp)
        )
      }
    }

    val displayReminders = upcomingReminders.take(3)
    if (displayReminders.isEmpty()) {
      item {
        EmptyCardPlaceholder(
          message = "Tidak ada jadwal pengingat mendatang saat ini.",
          actionText = "+ Buat Pengingat",
          onAction = { showAddReminderDialog = true }
        )
      }
    } else {
      items(displayReminders, key = { "home_reminder_${it.id}" }) { reminder ->
        ReminderCard(
          reminder = reminder,
          onToggle = { viewModel.toggleReminder(reminder) },
          onDelete = { viewModel.deleteReminder(reminder.id) },
          onEdit = { editingReminder = reminder }
        )
      }
    }
  }

  if (showAddReminderDialog) {
    AddReminderDialog(
      onDismiss = { showAddReminderDialog = false },
      onConfirm = { title, desc, due, prio, cat, repeat ->
        viewModel.addReminder(title, desc, due, prio, cat, repeat)
        showAddReminderDialog = false
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

@Composable
private fun QuickVoiceActionCard(
  label: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  Surface(
    shape = RoundedCornerShape(12.dp),
    color = MaterialTheme.colorScheme.surfaceVariant,
    modifier = modifier
      .clip(RoundedCornerShape(12.dp))
      .clickable(onClick = onClick)
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .heightIn(min = 48.dp)
        .padding(horizontal = 6.dp, vertical = 6.dp),
      contentAlignment = Alignment.Center
    ) {
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall.copy(
          fontWeight = FontWeight.SemiBold,
          fontSize = 11.sp,
          lineHeight = 14.sp,
          color = MaterialTheme.colorScheme.onSurface
        ),
        textAlign = TextAlign.Center,
        softWrap = true,
        maxLines = 2
      )
    }
  }
}

@Composable
private fun StatOverviewCard(
  title: String,
  count: Int,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  tint: Color,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  Card(
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surface
    ),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    modifier = modifier.clickable(onClick = onClick)
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Box(
          modifier = Modifier
            .size(28.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(tint.copy(alpha = 0.15f)),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(16.dp)
          )
        }
        Text(
          text = count.toString(),
          style = MaterialTheme.typography.titleLarge.copy(
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
          )
        )
      }
      Spacer(modifier = Modifier.height(2.dp))
      Text(
        text = title,
        style = MaterialTheme.typography.labelSmall.copy(
          color = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        maxLines = 1
      )
    }
  }
}

@Composable
fun EmptyCardPlaceholder(
  message: String,
  actionText: String? = null,
  onAction: (() -> Unit)? = null,
  modifier: Modifier = Modifier
) {
  Card(
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    ),
    modifier = modifier.fillMaxWidth()
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(20.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center
    ) {
      Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium.copy(
          color = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center
      )
      if (actionText != null && onAction != null) {
        Spacer(modifier = Modifier.height(10.dp))
        Button(
          onClick = onAction,
          colors = ButtonDefaults.buttonColors(containerColor = VoxPrimary)
        ) {
          Text(actionText)
        }
      }
    }
  }
}
