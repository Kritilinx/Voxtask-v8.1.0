package com.example.voice.parser

enum class ParserFinalDecision {
  READY_TO_CREATE,
  NEEDS_CLARIFICATION,
  REJECTED
}

enum class RelativeDateUnit {
  WEEK,
  MONTH,
  YEAR
}

/**
 * Diagnostic trace holding structured breakdown of natural language parsing
 * across each stage: STT -> normalization -> slot extraction -> validation -> final decision.
 */
data class ParserDebugTrace(
  val originalText: String,
  val normalizedText: String,
  val intent: CommandIntent,
  val activityTitle: String,
  val relativeDurationString: String = "None",
  val relativeDateString: String = "None",
  val weekdayString: String = "None",
  val clockTimeString: String = "Not provided",
  val meridiemString: String = "None",
  val targetDateFormatted: String = "None",
  val targetTimeFormatted: String = "None",
  val targetTimestamp: Long? = null,
  val missingFields: List<String> = emptyList(),
  val isPendingClarification: Boolean = false,
  val finalDecision: ParserFinalDecision = ParserFinalDecision.REJECTED,
  val rejectionReason: String? = null,
  val isMultiTurnContinuation: Boolean = false,
  val previousContextSummary: String? = null,
  val timestampMillis: Long = System.currentTimeMillis(),
  val relativeDateUnit: RelativeDateUnit? = null,
  val relativeDateAmount: Int? = null,
  val relativeDateExpression: String? = null
)

