package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.VoxPrimary
import com.example.voice.parser.ParserDebugTrace
import com.example.voice.parser.ParserFinalDecision

@Composable
fun ParserDebugTraceCard(
  trace: ParserDebugTrace,
  modifier: Modifier = Modifier
) {
  val (statusColor, statusIcon, statusLabel) = when (trace.finalDecision) {
    ParserFinalDecision.READY_TO_CREATE -> Triple(Color(0xFF10B981), Icons.Default.CheckCircle, "READY_TO_CREATE")
    ParserFinalDecision.NEEDS_CLARIFICATION -> Triple(Color(0xFFF59E0B), Icons.Default.HelpOutline, "NEEDS_CLARIFICATION")
    ParserFinalDecision.REJECTED -> Triple(Color(0xFFEF4444), Icons.Default.ErrorOutline, "REJECTED")
  }

  Card(
    shape = RoundedCornerShape(14.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    ),
    border = androidx.compose.foundation.BorderStroke(1.dp, statusColor.copy(alpha = 0.5f)),
    modifier = modifier
      .fillMaxWidth()
      .testTag("parser_debug_trace_card")
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      // Header
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          Icon(
            imageVector = Icons.Default.BugReport,
            contentDescription = "Parser Debug",
            tint = VoxPrimary,
            modifier = Modifier.size(16.dp)
          )
          Text(
            text = if (trace.isMultiTurnContinuation) "Parser Debug (Turn 2+)" else "Parser Debug Trace",
            style = MaterialTheme.typography.labelMedium.copy(
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onSurface
            )
          )
        }

        Surface(
          shape = RoundedCornerShape(8.dp),
          color = statusColor.copy(alpha = 0.15f)
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            Icon(
              imageVector = statusIcon,
              contentDescription = null,
              tint = statusColor,
              modifier = Modifier.size(12.dp)
            )
            Text(
              text = statusLabel,
              style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                color = statusColor,
                fontSize = 11.sp
              )
            )
          }
        }
      }

      // Previous multi-turn context notice if applicable
      if (trace.previousContextSummary != null) {
        Surface(
          shape = RoundedCornerShape(6.dp),
          color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
          modifier = Modifier.fillMaxWidth()
        ) {
          Text(
            text = "Konteks Sebelumnya: ${trace.previousContextSummary}",
            style = MaterialTheme.typography.labelSmall.copy(
              color = VoxPrimary,
              fontWeight = FontWeight.Medium
            ),
            modifier = Modifier.padding(6.dp)
          )
        }
      }

      // Trace detail rows
      Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        TraceRow(label = "Original Text", value = trace.originalText)
        TraceRow(label = "Normalized Text", value = trace.normalizedText)
        TraceRow(label = "Intent", value = trace.intent.name)
        TraceRow(label = "Activity", value = trace.activityTitle.ifBlank { "Not extracted" })
        if (trace.relativeDurationString != "None") {
          TraceRow(label = "Relative Duration", value = trace.relativeDurationString)
        }
        if (trace.relativeDateUnit != null) {
          TraceRow(label = "Relative Date Unit", value = trace.relativeDateUnit.name)
        }
        if (trace.relativeDateAmount != null) {
          TraceRow(label = "Relative Date Amount", value = trace.relativeDateAmount.toString())
        }
        if (!trace.relativeDateExpression.isNullOrBlank()) {
          TraceRow(label = "Relative Date Expression", value = trace.relativeDateExpression)
        }
        if (trace.relativeDateString != "None") {
          TraceRow(label = "Relative Date", value = trace.relativeDateString)
        }
        if (trace.weekdayString != "None") {
          TraceRow(label = "Weekday", value = trace.weekdayString)
        }
        TraceRow(label = "Clock Time", value = trace.clockTimeString)
        if (trace.meridiemString != "None") {
          TraceRow(label = "Meridiem", value = trace.meridiemString)
        }
        TraceRow(label = "Target Date", value = trace.targetDateFormatted)
        TraceRow(label = "Target Time", value = trace.targetTimeFormatted)
        val missingStr = if (trace.missingFields.isEmpty()) "None" else trace.missingFields.joinToString(", ")
        TraceRow(label = "Missing Fields", value = missingStr, highlightValue = trace.missingFields.isNotEmpty())
        TraceRow(label = "Pending Clarification", value = trace.isPendingClarification.toString().replaceFirstChar { it.uppercase() })
        TraceRow(label = "Final Decision", value = trace.finalDecision.name, highlightValue = trace.finalDecision != ParserFinalDecision.READY_TO_CREATE)

        if (!trace.rejectionReason.isNullOrBlank()) {
          TraceRow(label = "Reason", value = trace.rejectionReason, highlightValue = true)
        }
      }
    }
  }
}

@Composable
private fun TraceRow(
  label: String,
  value: String,
  highlightValue: Boolean = false
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.Top
  ) {
    Text(
      text = "• $label:",
      style = MaterialTheme.typography.bodySmall.copy(
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 11.5.sp
      ),
      modifier = Modifier.weight(0.42f)
    )
    Text(
      text = value,
      style = MaterialTheme.typography.bodySmall.copy(
        fontWeight = if (highlightValue) FontWeight.Bold else FontWeight.SemiBold,
        color = if (highlightValue) Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurface,
        fontSize = 11.5.sp,
        fontFamily = FontFamily.Monospace
      ),
      modifier = Modifier.weight(0.58f)
    )
  }
}
