package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.ui.theme.VoxPrimary
import com.example.ui.theme.VoxPrimaryVariant
import com.example.ui.theme.VoxSecondary
import com.example.ui.theme.VoxTertiary
import com.example.voice.VoiceAssistantState

@Composable
fun MicOrbButton(
  assistantState: VoiceAssistantState,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  val infiniteTransition = rememberInfiniteTransition(label = "pulse")
  val isListening = assistantState is VoiceAssistantState.Listening

  val pulseScale by infiniteTransition.animateFloat(
    initialValue = 1f,
    targetValue = if (isListening) 1.25f else 1.08f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = if (isListening) 800 else 1800, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "orb_scale"
  )

  val pulseAlpha by infiniteTransition.animateFloat(
    initialValue = 0.4f,
    targetValue = 0.08f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = if (isListening) 800 else 1800, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "orb_alpha"
  )

  Column(
    modifier = modifier,
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center
  ) {
    Box(
      contentAlignment = Alignment.Center,
      modifier = Modifier
        .size(140.dp)
        .testTag("mic_control_center")
    ) {
      // Outer breathing aura ring
      Box(
        modifier = Modifier
          .size(136.dp)
          .scale(pulseScale)
          .clip(CircleShape)
          .background(
            Brush.radialGradient(
              colors = listOf(
                VoxSecondary.copy(alpha = pulseAlpha),
                VoxPrimary.copy(alpha = pulseAlpha / 2),
                Color.Transparent
              )
            )
          )
      )

      // Secondary ring
      Box(
        modifier = Modifier
          .size(108.dp)
          .clip(CircleShape)
          .background(
            Brush.sweepGradient(
              colors = listOf(
                VoxPrimaryVariant.copy(alpha = 0.35f),
                VoxSecondary.copy(alpha = 0.35f),
                VoxTertiary.copy(alpha = 0.35f),
                VoxPrimaryVariant.copy(alpha = 0.35f)
              )
            )
          )
      )

      // Main interactive button using Surface onClick with built-in M3 ripple
      Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Color.Transparent,
        modifier = Modifier
          .size(88.dp)
          .shadow(elevation = 12.dp, shape = CircleShape, ambientColor = VoxPrimary, spotColor = VoxSecondary)
          .clip(CircleShape)
      ) {
        Box(
          contentAlignment = Alignment.Center,
          modifier = Modifier
            .size(88.dp)
            .background(
              Brush.linearGradient(
                colors = when (assistantState) {
                  is VoiceAssistantState.Listening -> listOf(Color(0xFFEF4444), Color(0xFFF97316))
                  is VoiceAssistantState.Speaking -> listOf(VoxSecondary, VoxPrimary)
                  is VoiceAssistantState.Processing -> listOf(VoxTertiary, VoxPrimaryVariant)
                  else -> listOf(VoxPrimaryVariant, VoxPrimary)
                }
              )
            )
        ) {
          Icon(
            imageVector = when (assistantState) {
              is VoiceAssistantState.Speaking -> Icons.Default.VolumeUp
              is VoiceAssistantState.Error -> Icons.Default.MicOff
              else -> Icons.Default.Mic
            },
            contentDescription = "Pusat Kendali Mikrofon VoxTask",
            tint = Color.White,
            modifier = Modifier.size(38.dp)
          )
        }
      }
    }

    Spacer(modifier = Modifier.height(10.dp))

    // Dynamic Assistant Status Chip
    Surface(
      shape = RoundedCornerShape(20.dp),
      color = MaterialTheme.colorScheme.surfaceVariant,
      modifier = Modifier.padding(horizontal = 16.dp)
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
      ) {
        Box(
          modifier = Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(
              when (assistantState) {
                is VoiceAssistantState.Listening -> Color(0xFFEF4444)
                is VoiceAssistantState.Processing -> VoxSecondary
                is VoiceAssistantState.Speaking -> VoxTertiary
                else -> Color(0xFF10B981)
              }
            )
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = when (assistantState) {
            is VoiceAssistantState.Listening -> "Mendengarkan suara Anda..."
            is VoiceAssistantState.Processing -> "Memproses perintah suara..."
            is VoiceAssistantState.Speaking -> "VoxTask sedang berbicara..."
            is VoiceAssistantState.Error -> "Siap mendengarkan kembali"
            else -> "Ketuk mikrofon untuk memberi perintah"
          },
          style = MaterialTheme.typography.labelMedium.copy(
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium
          ),
          textAlign = TextAlign.Center
        )
      }
    }
  }
}
