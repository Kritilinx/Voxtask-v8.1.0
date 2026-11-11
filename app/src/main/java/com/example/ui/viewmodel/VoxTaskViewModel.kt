package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.entity.ReminderEntity
import com.example.data.local.entity.TaskEntity
import com.example.data.local.entity.VoiceNoteEntity
import com.example.data.model.Priority
import com.example.data.model.RepeatMode
import com.example.data.repository.VoxTaskRepository
import com.example.reminder.AlarmScheduler
import com.example.reminder.AlarmToneRuntime
import com.example.reminder.AlarmToneUiState
import com.example.ai.AiCommandIntent
import com.example.ai.AiResponseValidator
import com.example.ai.AiUsageStats
import com.example.ai.AiUsageTracker
import com.example.ai.GeminiConfig
import com.example.ai.GeminiFallbackService
import com.example.ai.NetworkHelper
import com.example.voice.parser.ParsedCommand
import android.util.Log
import com.example.voice.DisambiguationAction
import com.example.voice.MultiTurnContext
import com.example.voice.PendingVoiceAction
import com.example.voice.TtsVoiceUiState
import com.example.voice.VoiceAssistantManager
import com.example.voice.VoiceAssistantState
import com.example.voice.VoiceSession
import com.example.voice.parser.CommandIntent
import com.example.voice.parser.DateTimeVoiceParser
import com.example.voice.parser.LocalCommandParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class VoxStats(
  val tasksCount: Int = 0,
  val remindersCount: Int = 0,
  val voiceNotesCount: Int = 0
)

class VoxTaskViewModel(application: Application) : AndroidViewModel(application) {

  private val database = AppDatabase.getDatabase(application)
  private val repository = VoxTaskRepository(
    reminderDao = database.reminderDao(),
    taskDao = database.taskDao(),
    voiceNoteDao = database.voiceNoteDao()
  )

  val alarmScheduler = AlarmScheduler(application)
  private val alarmToneCoordinator = AlarmToneRuntime.getCoordinator(application)
  val alarmToneState: StateFlow<AlarmToneUiState> = alarmToneCoordinator.uiState
  val assistantManager = VoiceAssistantManager(application)
  val assistantState: StateFlow<VoiceAssistantState> = assistantManager.assistantState
  val lastRecognizedText: StateFlow<String> = assistantManager.lastRecognizedText
  val ttsVoiceState: StateFlow<TtsVoiceUiState> = assistantManager.ttsVoiceState

  // Gemini AI Fallback integration
  val aiUsageTracker = AiUsageTracker(application)
  val geminiFallbackService = GeminiFallbackService(application)
  private var currentAiJob: Job? = null
  private val _aiUsageStats = MutableStateFlow(aiUsageTracker.getStats())
  val aiUsageStats: StateFlow<AiUsageStats> = _aiUsageStats.asStateFlow()

  fun refreshAiStats() {
    _aiUsageStats.value = aiUsageTracker.getStats()
  }

  fun setAiEnabled(enabled: Boolean) {
    aiUsageTracker.setEnabled(enabled)
    refreshAiStats()
  }

  fun setAiDailyLimit(limit: Int) {
    aiUsageTracker.setDailyLimit(limit)
    refreshAiStats()
  }

  fun resetAiDailyCount() {
    aiUsageTracker.resetDailyCount()
    refreshAiStats()
  }

  // Parser Debug & Pre-Creation Validation (Stage 7E.2)
  private val sharedPrefs = application.getSharedPreferences("vox_parser_debug_prefs", Context.MODE_PRIVATE)
  private val _parserDebugEnabled = MutableStateFlow(sharedPrefs.getBoolean("parser_debug_enabled", false))
  val parserDebugEnabled: StateFlow<Boolean> = _parserDebugEnabled.asStateFlow()

  private val _parserDebugHistory = MutableStateFlow<List<com.example.voice.parser.ParserDebugTrace>>(emptyList())
  val parserDebugHistory: StateFlow<List<com.example.voice.parser.ParserDebugTrace>> = _parserDebugHistory.asStateFlow()

  fun setParserDebugEnabled(enabled: Boolean) {
    _parserDebugEnabled.value = enabled
    sharedPrefs.edit().putBoolean("parser_debug_enabled", enabled).apply()
  }

  fun clearParserDebugHistory() {
    _parserDebugHistory.value = emptyList()
  }

  private fun recordParserDebugTrace(trace: com.example.voice.parser.ParserDebugTrace?) {
    if (trace == null) return
    val current = _parserDebugHistory.value.toMutableList()
    current.add(0, trace)
    if (current.size > 50) {
      _parserDebugHistory.value = current.take(50)
    } else {
      _parserDebugHistory.value = current
    }
  }

  // Voice interaction session and confirmation state
  private val _voiceSession = MutableStateFlow<VoiceSession?>(null)
  val voiceSession: StateFlow<VoiceSession?> = _voiceSession.asStateFlow()

  // Search queries
  private val _voiceNotesQuery = MutableStateFlow("")
  val voiceNotesQuery: StateFlow<String> = _voiceNotesQuery.asStateFlow()

  // Reminders - reactive state streams
  val allReminders: StateFlow<List<ReminderEntity>> = repository.allReminders
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val upcomingReminders: StateFlow<List<ReminderEntity>> = allReminders
    .map { reminders ->
      reminders.filter { !it.isCompleted }
    }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val todayReminders: StateFlow<List<ReminderEntity>> = allReminders
    .map { reminders ->
      val (start, end) = getDayRangeTimestamps()
      reminders.filter { it.dueDateTime in start..end }
    }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  // Tasks & Voice Notes - deactivated
  val allTasks: StateFlow<List<TaskEntity>> = MutableStateFlow(emptyList())
  val pendingTasks: StateFlow<List<TaskEntity>> = MutableStateFlow(emptyList())
  val todayTasks: StateFlow<List<TaskEntity>> = MutableStateFlow(emptyList())
  val allVoiceNotes: StateFlow<List<VoiceNoteEntity>> = MutableStateFlow(emptyList())

  // Stats
  val stats: StateFlow<VoxStats> = repository.remindersCount
    .map { reminders ->
      VoxStats(tasksCount = 0, remindersCount = reminders, voiceNotesCount = 0)
    }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), VoxStats())

  init {
    seedInitialDataIfEmpty()
    assistantManager.prepareTts()
    refreshAlarmToneStatus()
  }

  fun refreshAlarmToneStatus() {
    viewModelScope.launch(Dispatchers.IO) {
      alarmToneCoordinator.refreshAndEnsureChannel()
    }
  }

  fun selectAlarmTone(uri: Uri?) {
    if (uri == null) return
    viewModelScope.launch(Dispatchers.IO) {
      alarmToneCoordinator.selectCustomAudioUri(uri.toString())
    }
  }

  fun resetAlarmToneToDefault() {
    viewModelScope.launch(Dispatchers.IO) {
      alarmToneCoordinator.resetToDefaultTone()
    }
  }

  fun previewAlarmTone() {
    viewModelScope.launch(Dispatchers.IO) {
      alarmToneCoordinator.previewSelectedTone()
    }
  }

  fun stopAlarmTonePreview() {
    viewModelScope.launch(Dispatchers.IO) {
      alarmToneCoordinator.stopPreview()
    }
  }

  fun refreshTtsVoices() {
    assistantManager.refreshTtsVoices()
  }

  fun selectAiVoice(voiceId: String) {
    assistantManager.selectAiVoice(voiceId)
  }

  fun clearSelectedAiVoice() {
    assistantManager.clearSelectedAiVoice()
  }

  fun previewAiVoice() {
    assistantManager.previewAiVoice()
  }

  fun getGreeting(): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when (hour) {
      in 5..11 -> "Selamat Pagi"
      in 12..14 -> "Selamat Siang"
      in 15..18 -> "Selamat Sore"
      else -> "Selamat Malam"
    }
  }

  fun getTodayFormattedDate(): String {
    val formatter = SimpleDateFormat("EEEE, d MMMM yyyy", Locale("id", "ID"))
    return formatter.format(System.currentTimeMillis())
  }

  fun setVoiceNotesQuery(query: String) {
    _voiceNotesQuery.value = query
  }

  // --- Task & VoiceNote features deactivated ---
  fun addTask(
    title: String,
    description: String = "",
    priority: Priority = Priority.MEDIUM,
    category: String = "General",
    dueDate: Long? = null
  ) {}

  fun updateTask(task: TaskEntity) {}
  fun toggleTask(task: TaskEntity) {}
  fun deleteTask(id: Long) {}

  // --- CRUD: Smart Reminder ---
  fun addReminder(
    title: String,
    description: String = "",
    dueDateTime: Long,
    priority: Priority = Priority.MEDIUM,
    category: String = "General",
    repeatMode: RepeatMode = RepeatMode.ONCE
  ) {
    viewModelScope.launch {
      val entity = ReminderEntity(
        title = title.trim(),
        description = description.trim(),
        dueDateTime = dueDateTime,
        priority = priority,
        category = category.trim().ifBlank { "General" },
        repeatMode = repeatMode
      )
      val newId = repository.insertReminder(entity)
      val saved = entity.copy(id = newId)
      alarmScheduler.schedule(saved)
    }
  }

  fun updateReminder(reminder: ReminderEntity) {
    viewModelScope.launch {
      repository.updateReminder(reminder)
      alarmScheduler.cancel(reminder.id)
      if (!reminder.isCompleted && reminder.dueDateTime > System.currentTimeMillis()) {
        alarmScheduler.schedule(reminder)
      }
    }
  }

  fun toggleReminder(reminder: ReminderEntity) {
    viewModelScope.launch {
      val newCompletedState = !reminder.isCompleted
      repository.setReminderCompleted(reminder.id, newCompletedState)
      if (newCompletedState) {
        alarmScheduler.cancel(reminder.id)
      } else {
        if (reminder.dueDateTime > System.currentTimeMillis()) {
          alarmScheduler.schedule(reminder.copy(isCompleted = false))
        }
      }
    }
  }

  fun deleteReminder(id: Long) {
    viewModelScope.launch {
      alarmScheduler.cancel(id)
      repository.deleteReminderById(id)
    }
  }

  fun addVoiceNote(
    title: String,
    transcription: String = "",
    durationMs: Long = 0L,
    tags: String = ""
  ) {}

  fun updateVoiceNote(voiceNote: VoiceNoteEntity) {}
  fun deleteVoiceNote(id: Long) {}

  // --- Voice Assistant Integration ---
  // --- Voice Assistant: Conversational Multi-Turn Engine ---
  private var sessionTimeoutJob: Job? = null

  private fun startSessionTimeout() {
    sessionTimeoutJob?.cancel()
    sessionTimeoutJob = viewModelScope.launch {
      delay(30_000L) // 30-second conversational timeout
      val currentState = assistantState.value
      if (_voiceSession.value != null && (
            currentState is VoiceAssistantState.WaitingForAnswer ||
            currentState is VoiceAssistantState.AwaitingConfirmation ||
            currentState is VoiceAssistantState.Listening
          )) {
        assistantManager.cancelAll()
        _voiceSession.value = VoiceSession(
          transcription = _voiceSession.value?.transcription ?: "",
          assistantResponse = "Sesi percakapan berakhir karena tidak ada respons selama 30 detik."
        )
      }
    }
  }

  private fun cancelSessionTimeout() {
    sessionTimeoutJob?.cancel()
    sessionTimeoutJob = null
  }

  fun triggerMicInteraction() {
    when (assistantState.value) {
      is VoiceAssistantState.Listening -> {
        cancelSessionTimeout()
        assistantManager.stopListening()
      }
      is VoiceAssistantState.Speaking -> {
        cancelSessionTimeout()
        assistantManager.stopSpeaking()
      }
      is VoiceAssistantState.WaitingForAnswer, is VoiceAssistantState.AwaitingConfirmation -> {
        // User manually taps mic to speak immediately
        cancelSessionTimeout()
        assistantManager.startListening(
          onResult = { text ->
            if (text.isNotBlank()) {
              processVoiceCommand(text)
            }
          },
          onError = {
            // Handled in assistantState
          }
        )
      }
      else -> {
        cancelSessionTimeout()
        assistantManager.startListening(
          onResult = { text ->
            if (text.isNotBlank()) {
              processVoiceCommand(text)
            }
          },
          onError = {
            // Handled in assistantState
          }
        )
      }
    }
  }

  fun speakAndAwaitAnswer(
    prompt: String,
    targetState: VoiceAssistantState = VoiceAssistantState.WaitingForAnswer(prompt)
  ) {
    startSessionTimeout()
    assistantManager.setState(VoiceAssistantState.Speaking(prompt))
    assistantManager.speak(prompt) {
      // Auto-listening callback: automatically start STT after TTS finishes speaking
      assistantManager.setState(targetState)
      assistantManager.startListening(
        onResult = { text ->
          if (text.isNotBlank()) {
            processVoiceCommand(text)
          }
        },
        onError = {
          // If STT times out on silence, state remains waiting for user input
        }
      )
    }
  }

  fun cancelVoiceSession() {
    cancelSessionTimeout()
    currentAiJob?.cancel()
    currentAiJob = null
    assistantManager.cancelAll()
    _voiceSession.value = VoiceSession(
      transcription = _voiceSession.value?.transcription ?: "",
      assistantResponse = "Sesi telah dibatalkan."
    )
  }

  fun selectCandidateOption(index: Int) {
    processVoiceCommand("nomor ${index + 1}")
  }

  fun processVoiceCommand(input: String) {
    cancelSessionTimeout()
    val currentSession = _voiceSession.value

    // 1. Check Cancellation Command ("batal", "stop", "hentikan")
    if (LocalCommandParser.isCancellation(input)) {
      cancelVoiceSession()
      return
    }

    // 2. Check Active Multi-Turn Context (Clarification or Ambiguity resolution)
    val multiTurn = currentSession?.multiTurnContext
    if (multiTurn != null) {
      // Protection against pending state collision:
      // If the user speaks a fresh command that has its own intent or timing (e.g. "Satu menit lagi harus tidur"),
      // do not hijack it as an answer to the old context; reset and process freshly.
      val freshCheck = LocalCommandParser.parse(input)
      val isFreshFullCommand = freshCheck.intent == CommandIntent.CREATE_REMINDER &&
          freshCheck.newTitle.isNotBlank() &&
          (freshCheck.dueDateTime != null || freshCheck.hasExplicitDate)

      if (isFreshFullCommand) {
        // Abandon old multi-turn and process new command
        _voiceSession.value = currentSession.copy(multiTurnContext = null)
      } else {
        handleMultiTurnAnswer(input, multiTurn)
        return
      }
    }

    // 3. Check Pending Confirmation Action
    val pending = currentSession?.pendingAction
    if (pending != null) {
      if (LocalCommandParser.isYes(input)) {
        executePendingAction(pending)
      } else if (LocalCommandParser.isNo(input)) {
        cancelPendingAction()
      } else {
        val prompt = "Mohon jawab 'ya' untuk melanjutkan, atau 'batal' untuk membatalkan."
        _voiceSession.value = currentSession.copy(
          transcription = input,
          assistantResponse = prompt
        )
        speakAndAwaitAnswer(prompt, VoiceAssistantState.AwaitingConfirmation(prompt))
      }
      return
    }

    // 4. Initial Turn: Dispatch based on parsed command
    val parsed = LocalCommandParser.parse(input)
    when (parsed.intent) {
      CommandIntent.CONFIRM_YES -> {
        val resp = "Tidak ada tindakan yang menunggu konfirmasi."
        assistantManager.speak(resp)
        _voiceSession.value = VoiceSession(transcription = input, assistantResponse = resp)
      }

      CommandIntent.CONFIRM_NO -> {
        val resp = "Tidak ada tindakan aktif."
        assistantManager.speak(resp)
        _voiceSession.value = VoiceSession(transcription = input, assistantResponse = resp)
      }

      CommandIntent.CLARIFICATION -> {
        val q = parsed.clarificationQuestion ?: "Informasi belum lengkap. Silakan ulangi perintah Anda."
        _voiceSession.value = VoiceSession(transcription = input, assistantResponse = q, isClarification = true)
        speakAndAwaitAnswer(q)
      }

      CommandIntent.VIEW_TODAY_AGENDA -> {
        val tReminders = todayReminders.value
        val responseText = buildString {
          append("Agenda pengingat hari ini: ")
          if (tReminders.isEmpty()) {
            append("Belum ada jadwal pengingat untuk hari ini.")
          } else {
            append("${tReminders.size} pengingat")
            val sample = tReminders.take(3).joinToString { it.title }
            append(" ($sample).")
          }
        }
        assistantManager.speak(responseText)
        _voiceSession.value = VoiceSession(transcription = input, assistantResponse = responseText)
      }

      CommandIntent.VIEW_NEXT_REMINDER -> {
        val now = System.currentTimeMillis()
        val next = upcomingReminders.value
          .filter { it.dueDateTime > now }
          .minByOrNull { it.dueDateTime }
        val resp = if (next != null) {
          val dateStr = formatDateTimeId(next.dueDateTime)
          "Pengingat berikutnya adalah '${next.title}' pada $dateStr."
        } else {
          "Tidak ada jadwal pengingat mendatang saat ini."
        }
        assistantManager.speak(resp)
        _voiceSession.value = VoiceSession(transcription = input, assistantResponse = resp)
      }

      CommandIntent.CREATE_REMINDER -> {
        val title = parsed.newTitle.trim()
        val due = parsed.dueDateTime
        val trace = parsed.debugTrace
        recordParserDebugTrace(trace)

        if (title.isNotBlank() && due != null) {
          // Validation: READY_TO_CREATE
          addReminder(
            title = title,
            dueDateTime = due,
            priority = parsed.priority,
            category = parsed.category,
            repeatMode = parsed.repeatMode
          )
          val dateStr = formatDateTimeId(due)
          val repeatText = if (parsed.repeatMode != RepeatMode.ONCE) " (${parsed.repeatMode.displayName})" else ""
          val resp = "Pengingat '$title' berhasil dijadwalkan pada $dateStr$repeatText."
          assistantManager.speak(resp)
          _voiceSession.value = VoiceSession(
            transcription = input,
            assistantResponse = resp,
            parserDebugTrace = trace
          )
        } else if (title.isNotBlank() && due == null) {
          // Validation: NEEDS_CLARIFICATION (Missing time)
          val context = MultiTurnContext.CreatingReminderMissingTime(
            title = title,
            priority = parsed.priority,
            category = parsed.category,
            repeatMode = parsed.repeatMode,
            targetDateMillis = parsed.targetDateMillis
          )
          val question = if (parsed.hasExplicitDate) {
            val activityPrompt = title.replace(Regex("(?i)^ada\\s+"), "").lowercase()
            "Jam berapa Anda ingin diingatkan tentang $activityPrompt?"
          } else {
            "Kapan Anda ingin diingatkan untuk '$title'?"
          }
          _voiceSession.value = VoiceSession(
            transcription = input,
            assistantResponse = question,
            multiTurnContext = context,
            isClarification = true,
            parserDebugTrace = trace
          )
          speakAndAwaitAnswer(question, VoiceAssistantState.WaitingForAnswer(question))
        } else if (title.isBlank() && due != null) {
          // Validation: NEEDS_CLARIFICATION (Missing title)
          val context = MultiTurnContext.CreatingReminderMissingTitle(
            dueDateTime = due,
            priority = parsed.priority,
            category = parsed.category,
            repeatMode = parsed.repeatMode
          )
          val dateStr = formatDateTimeId(due)
          val question = "Apa yang ingin Anda ingat pada $dateStr?"
          _voiceSession.value = VoiceSession(
            transcription = input,
            assistantResponse = question,
            multiTurnContext = context,
            isClarification = true,
            parserDebugTrace = trace
          )
          speakAndAwaitAnswer(question, VoiceAssistantState.WaitingForAnswer(question))
        } else {
          // Validation: REJECTED
          val question = "Apa yang ingin diingatkan dan kapan waktunya?"
          _voiceSession.value = VoiceSession(
            transcription = input,
            assistantResponse = question,
            isClarification = true,
            parserDebugTrace = trace
          )
          speakAndAwaitAnswer(question)
        }
      }

      CommandIntent.DELETE_REMINDER -> {
        val query = parsed.targetQuery
        val reminderMatches = findMatchingReminders(query)

        if (reminderMatches.size > 1) {
          // Ambiguous reminder matches: ask user to pick candidate
          val candidateIds = reminderMatches.map { it.id }
          val candidateTitles = reminderMatches.map { it.title }
          val options = reminderMatches.mapIndexed { idx, r -> "${idx + 1}. ${r.title}" }
          val prompt = "Ditemukan ${reminderMatches.size} pengingat yang cocok: ${options.joinToString(", ")}. Nomor berapa yang ingin dihapus?"

          val context = MultiTurnContext.DisambiguatingReminder(
            candidateIds = candidateIds,
            candidateTitles = candidateTitles,
            actionType = DisambiguationAction.DELETE
          )
          _voiceSession.value = VoiceSession(
            transcription = input,
            assistantResponse = prompt,
            multiTurnContext = context,
            candidateOptions = options
          )
          speakAndAwaitAnswer(prompt, VoiceAssistantState.WaitingForAnswer(prompt))
        } else if (reminderMatches.size == 1) {
          val matched = reminderMatches.first()
          val prompt = "Hapus pengingat '${matched.title}'? Katakan 'ya' untuk konfirmasi atau 'batal'."
          val action = PendingVoiceAction.DeleteReminder(matched.id, matched.title)
          _voiceSession.value = VoiceSession(
            transcription = input,
            assistantResponse = prompt,
            pendingAction = action
          )
          speakAndAwaitAnswer(prompt, VoiceAssistantState.AwaitingConfirmation("Hapus pengingat ${matched.title}"))
        } else {
          val resp = "Tidak ditemukan pengingat dengan nama '$query'."
          assistantManager.speak(resp)
          _voiceSession.value = VoiceSession(transcription = input, assistantResponse = resp)
        }
      }

      CommandIntent.EDIT_REMINDER -> {
        val query = parsed.targetQuery
        val reminderMatches = findMatchingReminders(query)

        if (reminderMatches.size > 1) {
          val candidateIds = reminderMatches.map { it.id }
          val candidateTitles = reminderMatches.map { it.title }
          val options = reminderMatches.mapIndexed { idx, r -> "${idx + 1}. ${r.title}" }
          val prompt = "Ditemukan ${reminderMatches.size} pengingat: ${options.joinToString(", ")}. Nomor berapa yang ingin diubah?"

          val context = MultiTurnContext.DisambiguatingReminder(
            candidateIds = candidateIds,
            candidateTitles = candidateTitles,
            actionType = DisambiguationAction.EDIT
          )
          _voiceSession.value = VoiceSession(
            transcription = input,
            assistantResponse = prompt,
            multiTurnContext = context,
            candidateOptions = options
          )
          speakAndAwaitAnswer(prompt, VoiceAssistantState.WaitingForAnswer(prompt))
        } else if (reminderMatches.size == 1) {
          val matched = reminderMatches.first()
          val newTime = parsed.dueDateTime
          if (newTime != null) {
            val dateStr = formatDateTimeId(newTime)
            val prompt = "Ubah waktu pengingat '${matched.title}' menjadi $dateStr? Katakan 'ya' untuk konfirmasi atau 'batal'."
            val action = PendingVoiceAction.EditReminderTime(matched.id, matched.title, newTime, parsed.repeatMode)
            _voiceSession.value = VoiceSession(
              transcription = input,
              assistantResponse = prompt,
              pendingAction = action
            )
            speakAndAwaitAnswer(prompt, VoiceAssistantState.AwaitingConfirmation("Ubah pengingat ${matched.title}"))
          } else {
            // Missing new time
            val context = MultiTurnContext.EditingReminderMissingTime(matched.id, matched.title)
            val question = "Kapan waktu baru untuk pengingat '${matched.title}'?"
            _voiceSession.value = VoiceSession(
              transcription = input,
              assistantResponse = question,
              multiTurnContext = context,
              isClarification = true
            )
            speakAndAwaitAnswer(question, VoiceAssistantState.WaitingForAnswer(question))
          }
        } else {
          val resp = "Pengingat '$query' tidak ditemukan untuk diubah."
          assistantManager.speak(resp)
          _voiceSession.value = VoiceSession(transcription = input, assistantResponse = resp)
        }
      }

      CommandIntent.COMPLETE_REMINDER -> {
        val query = parsed.targetQuery
        val reminderMatches = findMatchingReminders(query)
        if (reminderMatches.size > 1) {
          val candidateIds = reminderMatches.map { it.id }
          val candidateTitles = reminderMatches.map { it.title }
          val options = reminderMatches.mapIndexed { idx, r -> "${idx + 1}. ${r.title}" }
          val prompt = "Ditemukan beberapa pengingat: ${options.joinToString(", ")}. Nomor berapa yang ingin diselesaikan?"
          val context = MultiTurnContext.DisambiguatingReminder(
            candidateIds = candidateIds,
            candidateTitles = candidateTitles,
            actionType = DisambiguationAction.COMPLETE
          )
          _voiceSession.value = VoiceSession(
            transcription = input,
            assistantResponse = prompt,
            multiTurnContext = context,
            candidateOptions = options
          )
          speakAndAwaitAnswer(prompt, VoiceAssistantState.WaitingForAnswer(prompt))
        } else if (reminderMatches.size == 1) {
          val matched = reminderMatches.first()
          if (!matched.isCompleted) {
            toggleReminder(matched)
          }
          val resp = "Pengingat '${matched.title}' telah ditandai selesai."
          assistantManager.speak(resp)
          _voiceSession.value = VoiceSession(transcription = input, assistantResponse = resp)
        } else {
          val resp = "Tidak ditemukan pengingat '$query' untuk diselesaikan."
          assistantManager.speak(resp)
          _voiceSession.value = VoiceSession(transcription = input, assistantResponse = resp)
        }
      }

      CommandIntent.UNKNOWN -> {
        handleUnknownCommandWithAiFallback(input, parsed)
      }
    }
  }

  private fun handleUnknownCommandWithAiFallback(input: String, parsed: ParsedCommand) {
    val context = getApplication<Application>()

    // 1. Check if AI Fallback is enabled in Settings
    if (!aiUsageTracker.isEnabled()) {
      val resp = parsed.clarificationQuestion ?: "Perintah tidak dikenali. Fitur AI sedang dinonaktifkan di Pengaturan."
      assistantManager.speak(resp)
      _voiceSession.value = VoiceSession(
        transcription = input,
        assistantResponse = resp,
        isClarification = true,
        isAiResponse = false
      )
      return
    }

    // 2. Check Network Connectivity
    if (!NetworkHelper.isNetworkAvailable(context)) {
      Log.w("VoxTaskViewModel", "Network connection not available for Gemini AI fallback")
      val resp = GeminiConfig.DEFAULT_FRIENDLY_FAILURE_MESSAGE
      assistantManager.speak(resp)
      _voiceSession.value = VoiceSession(
        transcription = input,
        assistantResponse = resp,
        isClarification = true,
        isAiResponse = true
      )
      return
    }

    // 3. Check Daily Quota Limit
    if (!aiUsageTracker.canMakeRequest()) {
      val stats = aiUsageTracker.getStats()
      val resp = "Batas permintaan AI harian (${stats.dailyCount}/${stats.dailyLimit}) telah tercapai. Anda dapat mengaturnya di Pengaturan."
      assistantManager.speak(resp)
      _voiceSession.value = VoiceSession(
        transcription = input,
        assistantResponse = resp,
        isClarification = true,
        isAiResponse = false
      )
      return
    }

    // 4. Update UI State to Processing
    val statusMsg = "Menganalisis dengan Gemini AI..."
    assistantManager.setState(VoiceAssistantState.Processing("Gemini AI menganalisis..."))
    _voiceSession.value = VoiceSession(
      transcription = input,
      assistantResponse = statusMsg,
      isAiResponse = true
    )

    currentAiJob?.cancel()
    currentAiJob = viewModelScope.launch {
      try {
        val aiResult = geminiFallbackService.parseWithGemini(input)
        // Record quota and tokens only if an actual network request was made and succeeded
        if (!aiResult.isFromCache && aiResult.isNetworkSuccess) {
          aiUsageTracker.recordRequest(actualTokens = aiResult.actualTokenCount)
          refreshAiStats()
        }

        when (aiResult.intent) {
          AiCommandIntent.CREATE_REMINDER -> {
            val due = aiResult.targetEpochMillis
            if (aiResult.title.isNotBlank() && due != null) {
              if (aiResult.requiresConfirmation) {
                val dateStr = formatDateTimeId(due)
                val prompt = "Jadwalkan pengingat '${aiResult.title}' pada $dateStr? Katakan 'ya' untuk konfirmasi atau 'batal'."
                val action = PendingVoiceAction.CreateAiReminder(
                  title = aiResult.title,
                  dueDateTime = due,
                  priority = aiResult.priority,
                  category = "AI",
                  repeatMode = aiResult.repeatMode
                )
                _voiceSession.value = VoiceSession(
                  transcription = input,
                  assistantResponse = prompt,
                  pendingAction = action,
                  isAiResponse = true
                )
                speakAndAwaitAnswer(prompt, VoiceAssistantState.AwaitingConfirmation("Jadwalkan ${aiResult.title}"))
              } else {
                addReminder(
                  title = aiResult.title,
                  dueDateTime = due,
                  priority = aiResult.priority,
                  category = "AI",
                  repeatMode = aiResult.repeatMode
                )
                val resp = aiResult.responseText
                assistantManager.speak(resp)
                _voiceSession.value = VoiceSession(
                  transcription = input,
                  assistantResponse = resp,
                  isAiResponse = true
                )
              }
            } else if (aiResult.title.isNotBlank()) {
              // Missing explicit time, enter local multi-turn clarification
              val askTime = "Kapan Anda ingin diingatkan untuk '${aiResult.title}'? Contoh: besok jam 9 pagi."
              val turnContext = MultiTurnContext.CreatingReminderMissingTime(
                title = aiResult.title,
                priority = aiResult.priority,
                category = "AI",
                repeatMode = aiResult.repeatMode
              )
              _voiceSession.value = VoiceSession(
                transcription = input,
                assistantResponse = askTime,
                multiTurnContext = turnContext,
                isClarification = true,
                isAiResponse = true
              )
              speakAndAwaitAnswer(askTime, VoiceAssistantState.WaitingForAnswer(askTime))
            } else {
              val resp = aiResult.responseText
              assistantManager.speak(resp)
              _voiceSession.value = VoiceSession(
                transcription = input,
                assistantResponse = resp,
                isClarification = true,
                isAiResponse = true
              )
            }
          }

          AiCommandIntent.CREATE_TASK -> {
            val resp = "Fitur tugas telah dihapus. Anda dapat membuat pengingat dengan menyebutkan jadwal atau waktu."
            assistantManager.speak(resp)
            _voiceSession.value = VoiceSession(
              transcription = input,
              assistantResponse = resp,
              isAiResponse = true
            )
          }

          AiCommandIntent.QUERY_AGENDA -> {
            val tReminders = todayReminders.value
            val responseText = buildString {
              append(aiResult.responseText)
              if (tReminders.isNotEmpty()) {
                append(" (Agenda aktif: ${tReminders.size} pengingat hari ini).")
              }
            }
            assistantManager.speak(responseText)
            _voiceSession.value = VoiceSession(
              transcription = input,
              assistantResponse = responseText,
              isAiResponse = true
            )
          }

          AiCommandIntent.SUGGEST_PRIORITY -> {
            val resp = aiResult.responseText
            assistantManager.speak(resp)
            _voiceSession.value = VoiceSession(
              transcription = input,
              assistantResponse = resp,
              isAiResponse = true
            )
          }

          AiCommandIntent.GENERAL_RESPONSE -> {
            val resp = aiResult.responseText
            assistantManager.speak(resp)
            _voiceSession.value = VoiceSession(
              transcription = input,
              assistantResponse = resp,
              isAiResponse = true
            )
          }

          AiCommandIntent.NEEDS_CLARIFICATION -> {
            val resp = aiResult.responseText
            _voiceSession.value = VoiceSession(
              transcription = input,
              assistantResponse = resp,
              isClarification = true,
              isAiResponse = true
            )
            speakAndAwaitAnswer(resp, VoiceAssistantState.WaitingForAnswer(resp))
          }

          AiCommandIntent.UNSUPPORTED -> {
            val resp = aiResult.responseText
            assistantManager.speak(resp)
            _voiceSession.value = VoiceSession(
              transcription = input,
              assistantResponse = resp,
              isAiResponse = true
            )
          }
        }
      } catch (e: kotlinx.coroutines.CancellationException) {
        Log.d("VoxTaskViewModel", "Gemini AI fallback job cancelled by user")
      } catch (e: Exception) {
        Log.e("VoxTaskViewModel", "Gemini AI fallback unhandled error: ${e.message}", e)
        val errorMsg = GeminiConfig.DEFAULT_FRIENDLY_FAILURE_MESSAGE
        assistantManager.speak(errorMsg)
        _voiceSession.value = VoiceSession(
          transcription = input,
          assistantResponse = errorMsg,
          isAiResponse = true
        )
      }
    }
  }

  private fun handleMultiTurnAnswer(input: String, context: MultiTurnContext) {
    when (context) {
      is MultiTurnContext.CreatingReminderMissingTime -> {
        val parsedTime = DateTimeVoiceParser.parse(input)
        if (parsedTime.hasExplicitTime || parsedTime.hasExplicitDateTime) {
          val finalTimestamp = if (context.targetDateMillis != null) {
            val dateCal = Calendar.getInstance().apply { timeInMillis = context.targetDateMillis }
            val timeCal = Calendar.getInstance().apply { timeInMillis = parsedTime.timestamp }
            dateCal.set(Calendar.HOUR_OF_DAY, timeCal.get(Calendar.HOUR_OF_DAY))
            dateCal.set(Calendar.MINUTE, timeCal.get(Calendar.MINUTE))
            dateCal.set(Calendar.SECOND, 0)
            dateCal.set(Calendar.MILLISECOND, 0)
            dateCal.timeInMillis
          } else {
            parsedTime.timestamp
          }

          val prevDateFormatted = if (context.targetDateMillis != null) {
            val sdf = java.text.SimpleDateFormat("d MMMM yyyy", java.util.Locale("id", "ID"))
            sdf.format(java.util.Date(context.targetDateMillis))
          } else "Previous context"

          val finalCal = Calendar.getInstance().apply { timeInMillis = finalTimestamp }
          val sdfDate = java.text.SimpleDateFormat("d MMMM yyyy", java.util.Locale("id", "ID"))
          val sdfTime = java.text.SimpleDateFormat("HH:mm", java.util.Locale("id", "ID"))

          val multiTurnTrace = com.example.voice.parser.ParserDebugTrace(
            originalText = input,
            normalizedText = DateTimeVoiceParser.normalizeTemporalWords(input.lowercase()),
            intent = CommandIntent.CREATE_REMINDER,
            activityTitle = context.title,
            clockTimeString = String.format(java.util.Locale.ROOT, "%02d:%02d", finalCal.get(Calendar.HOUR_OF_DAY), finalCal.get(Calendar.MINUTE)),
            meridiemString = parsedTime.extractedMeridiem ?: "None",
            targetDateFormatted = sdfDate.format(finalCal.time),
            targetTimeFormatted = "${sdfTime.format(finalCal.time)} WIB",
            targetTimestamp = finalTimestamp,
            missingFields = emptyList(),
            isPendingClarification = false,
            finalDecision = com.example.voice.parser.ParserFinalDecision.READY_TO_CREATE,
            isMultiTurnContinuation = true,
            previousContextSummary = "Activity: '${context.title}', Date: $prevDateFormatted"
          )
          recordParserDebugTrace(multiTurnTrace)

          addReminder(
            title = context.title,
            dueDateTime = finalTimestamp,
            priority = context.priority,
            category = context.category,
            repeatMode = if (parsedTime.repeatMode != RepeatMode.ONCE) parsedTime.repeatMode else context.repeatMode
          )
          val dateStr = formatDateTimeId(finalTimestamp)
          val repeatText = if (parsedTime.repeatMode != RepeatMode.ONCE) " (${parsedTime.repeatMode.displayName})" else ""
          val resp = "Pengingat '${context.title}' berhasil dijadwalkan pada $dateStr$repeatText."
          assistantManager.speak(resp)
          _voiceSession.value = VoiceSession(
            transcription = input,
            assistantResponse = resp,
            parserDebugTrace = multiTurnTrace
          )
        } else {
          val multiTurnTrace = com.example.voice.parser.ParserDebugTrace(
            originalText = input,
            normalizedText = DateTimeVoiceParser.normalizeTemporalWords(input.lowercase()),
            intent = CommandIntent.CREATE_REMINDER,
            activityTitle = context.title,
            missingFields = listOf("Clock Time"),
            isPendingClarification = true,
            finalDecision = com.example.voice.parser.ParserFinalDecision.NEEDS_CLARIFICATION,
            rejectionReason = "Jawaban belum memuat jam/waktu yang valid",
            isMultiTurnContinuation = true,
            previousContextSummary = "Activity: '${context.title}'"
          )
          recordParserDebugTrace(multiTurnTrace)

          val question = "Waktu belum jelas. Jam berapa Anda ingin diingatkan untuk '${context.title.lowercase()}'?"
          _voiceSession.value = VoiceSession(
            transcription = input,
            assistantResponse = question,
            multiTurnContext = context,
            isClarification = true,
            parserDebugTrace = multiTurnTrace
          )
          speakAndAwaitAnswer(question, VoiceAssistantState.WaitingForAnswer(question))
        }
      }

      is MultiTurnContext.CreatingReminderMissingTitle -> {
        val cleaned = DateTimeVoiceParser.cleanRawReminderTitle(input)
        val title = cleaned.ifBlank { input.trim() }
        addReminder(
          title = title,
          dueDateTime = context.dueDateTime,
          priority = context.priority,
          category = context.category,
          repeatMode = context.repeatMode
        )
        val dateStr = formatDateTimeId(context.dueDateTime)
        val resp = "Pengingat '$title' berhasil dijadwalkan untuk $dateStr."
        assistantManager.speak(resp)
        _voiceSession.value = VoiceSession(transcription = input, assistantResponse = resp)
      }


      is MultiTurnContext.EditingReminderMissingTime -> {
        val parsedTime = DateTimeVoiceParser.parse(input)
        if (parsedTime.hasExplicitDateTime) {
          val dateStr = formatDateTimeId(parsedTime.timestamp)
          val prompt = "Ubah waktu pengingat '${context.title}' menjadi $dateStr? Katakan 'ya' untuk konfirmasi atau 'batal'."
          val action = PendingVoiceAction.EditReminderTime(
            reminderId = context.reminderId,
            title = context.title,
            newTimestamp = parsedTime.timestamp,
            newRepeatMode = parsedTime.repeatMode
          )
          _voiceSession.value = VoiceSession(
            transcription = input,
            assistantResponse = prompt,
            pendingAction = action
          )
          speakAndAwaitAnswer(prompt, VoiceAssistantState.AwaitingConfirmation("Ubah pengingat ${context.title}"))
        } else {
          val question = "Waktu baru belum jelas. Silakan sebutkan waktu baru untuk '${context.title}'."
          _voiceSession.value = VoiceSession(
            transcription = input,
            assistantResponse = question,
            multiTurnContext = context,
            isClarification = true
          )
          speakAndAwaitAnswer(question, VoiceAssistantState.WaitingForAnswer(question))
        }
      }

      is MultiTurnContext.DisambiguatingReminder -> {
        val idx = LocalCommandParser.parseCandidateIndex(input, context.candidateIds.size)
          ?: context.candidateTitles.indexOfFirst { it.contains(input.trim(), ignoreCase = true) }.takeIf { it >= 0 }

        if (idx != null && idx in context.candidateIds.indices) {
          val chosenId = context.candidateIds[idx]
          val chosenTitle = context.candidateTitles[idx]

          when (context.actionType) {
            DisambiguationAction.DELETE -> {
              val prompt = "Hapus pengingat '$chosenTitle'? Katakan 'ya' untuk konfirmasi atau 'batal'."
              val action = PendingVoiceAction.DeleteReminder(chosenId, chosenTitle)
              _voiceSession.value = VoiceSession(
                transcription = input,
                assistantResponse = prompt,
                pendingAction = action
              )
              speakAndAwaitAnswer(prompt, VoiceAssistantState.AwaitingConfirmation("Hapus pengingat $chosenTitle"))
            }

            DisambiguationAction.COMPLETE -> {
              val rem = allReminders.value.find { it.id == chosenId }
              if (rem != null && !rem.isCompleted) {
                toggleReminder(rem)
              }
              val resp = "Pengingat '$chosenTitle' telah ditandai selesai."
              assistantManager.speak(resp)
              _voiceSession.value = VoiceSession(transcription = input, assistantResponse = resp)
            }

            DisambiguationAction.EDIT -> {
              val nextContext = MultiTurnContext.EditingReminderMissingTime(chosenId, chosenTitle)
              val question = "Kapan waktu baru untuk pengingat '$chosenTitle'?"
              _voiceSession.value = VoiceSession(
                transcription = input,
                assistantResponse = question,
                multiTurnContext = nextContext,
                isClarification = true
              )
              speakAndAwaitAnswer(question, VoiceAssistantState.WaitingForAnswer(question))
            }
          }
        } else {
          val prompt = "Pilihan tidak dikenali. Sebutkan nomor 1 sampai ${context.candidateIds.size}."
          val options = context.candidateTitles.mapIndexed { i, t -> "${i + 1}. $t" }
          _voiceSession.value = VoiceSession(
            transcription = input,
            assistantResponse = prompt,
            multiTurnContext = context,
            candidateOptions = options
          )
          speakAndAwaitAnswer(prompt, VoiceAssistantState.WaitingForAnswer(prompt))
        }
      }
    }
  }

  fun confirmPendingAction() {
    cancelSessionTimeout()
    val session = _voiceSession.value
    if (session?.pendingAction != null) {
      executePendingAction(session.pendingAction)
    }
  }

  fun cancelPendingAction() {
    cancelSessionTimeout()
    assistantManager.cancelAll()
    val resp = "Tindakan telah dibatalkan."
    assistantManager.speak(resp)
    _voiceSession.value = VoiceSession(
      transcription = _voiceSession.value?.transcription ?: "",
      assistantResponse = resp
    )
  }

  fun clearVoiceSession() {
    cancelSessionTimeout()
    assistantManager.cancelAll()
    _voiceSession.value = null
  }

  private fun executePendingAction(action: PendingVoiceAction) {
    cancelSessionTimeout()
    when (action) {
      is PendingVoiceAction.DeleteReminder -> {
        deleteReminder(action.reminderId)
        val resp = "Pengingat '${action.title}' telah dihapus."
        assistantManager.speak(resp)
        _voiceSession.value = VoiceSession(transcription = "", assistantResponse = resp)
      }
      is PendingVoiceAction.EditReminderTime -> {
        val existing = allReminders.value.find { it.id == action.reminderId }
        if (existing != null) {
          updateReminder(existing.copy(dueDateTime = action.newTimestamp, repeatMode = action.newRepeatMode))
          val dateStr = formatDateTimeId(action.newTimestamp)
          val resp = "Pengingat '${action.title}' berhasil diubah ke $dateStr."
          assistantManager.speak(resp)
          _voiceSession.value = VoiceSession(transcription = "", assistantResponse = resp)
        } else {
          val resp = "Pengingat tidak ditemukan."
          assistantManager.speak(resp)
          _voiceSession.value = VoiceSession(transcription = "", assistantResponse = resp)
        }
      }
      is PendingVoiceAction.CreateAiReminder -> {
        addReminder(
          title = action.title,
          dueDateTime = action.dueDateTime,
          priority = action.priority,
          category = action.category,
          repeatMode = action.repeatMode
        )
        val dateStr = formatDateTimeId(action.dueDateTime)
        val resp = "Pengingat '${action.title}' berhasil dijadwalkan pada $dateStr."
        assistantManager.speak(resp)
      }
    }
  }

  fun findMatchingReminders(query: String): List<ReminderEntity> {
    val list = allReminders.value
    if (list.isEmpty() || query.isBlank()) return emptyList()
    val q = query.lowercase().trim()

    val exact = list.filter { it.title.equals(q, ignoreCase = true) }
    if (exact.isNotEmpty()) return exact

    val direct = list.filter {
      it.title.contains(q, ignoreCase = true) || q.contains(it.title, ignoreCase = true)
    }
    if (direct.isNotEmpty()) return direct

    val queryWords = q.split("\\s+".toRegex()).filter { it.length > 2 }
    if (queryWords.isEmpty()) return emptyList()

    return list.filter { item ->
      val titleWords = item.title.lowercase().split("\\s+".toRegex())
      queryWords.any { qw -> titleWords.any { tw -> tw.contains(qw) || qw.contains(tw) } }
    }
  }

  fun findMatchingTasks(query: String): List<TaskEntity> {
    val list = allTasks.value
    if (list.isEmpty() || query.isBlank()) return emptyList()
    val q = query.lowercase().trim()

    val exact = list.filter { it.title.equals(q, ignoreCase = true) }
    if (exact.isNotEmpty()) return exact

    val direct = list.filter {
      it.title.contains(q, ignoreCase = true) || q.contains(it.title, ignoreCase = true)
    }
    if (direct.isNotEmpty()) return direct

    val queryWords = q.split("\\s+".toRegex()).filter { it.length > 2 }
    if (queryWords.isEmpty()) return emptyList()

    return list.filter { item ->
      val titleWords = item.title.lowercase().split("\\s+".toRegex())
      queryWords.any { qw -> titleWords.any { tw -> tw.contains(qw) || qw.contains(tw) } }
    }
  }

  private fun formatDateTimeId(millis: Long): String {
    val calendar = Calendar.getInstance().apply { timeInMillis = millis }
    val now = Calendar.getInstance()

    val isToday = calendar.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
        calendar.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)

    val timeFormat = SimpleDateFormat("HH:mm", Locale("id", "ID"))
    val timeStr = timeFormat.format(millis)

    return if (isToday) {
      "Hari ini pukul $timeStr"
    } else {
      val dateFormat = SimpleDateFormat("EEEE, d MMMM 'pukul' HH:mm", Locale("id", "ID"))
      dateFormat.format(millis)
    }
  }

  private fun seedInitialDataIfEmpty() {
    viewModelScope.launch {
      val prefs = getApplication<Application>().getSharedPreferences("voxtask_prefs", Context.MODE_PRIVATE)
      val hasSeeded = prefs.getBoolean("has_seeded_initial_data", false)
      if (hasSeeded) return@launch

      val calendar = Calendar.getInstance()
      calendar.add(Calendar.HOUR_OF_DAY, 2)
      val laterToday = calendar.timeInMillis

      calendar.add(Calendar.DAY_OF_YEAR, 1)
      val tomorrow = calendar.timeInMillis

      val r1 = ReminderEntity(
        title = "Cek agenda rapat harian",
        description = "Tinjau prioritas tugas dan catatan penting bersama tim",
        dueDateTime = laterToday,
        priority = Priority.HIGH,
        category = "Pekerjaan",
        repeatMode = RepeatMode.DAILY
      )
      val id1 = database.reminderDao().insert(r1)
      alarmScheduler.schedule(r1.copy(id = id1))

      val r2 = ReminderEntity(
        title = "Minum air & istirahat sejenak",
        description = "Target hidrasi harian minimal 2 liter",
        dueDateTime = tomorrow,
        priority = Priority.LOW,
        category = "Kesehatan",
        repeatMode = RepeatMode.DAILY
      )
      val id2 = database.reminderDao().insert(r2)
      alarmScheduler.schedule(r2.copy(id = id2))
      // Mark seeded so user deletions are never overwritten on app restart
      prefs.edit().putBoolean("has_seeded_initial_data", true).apply()
    }
  }

  private fun getDayRangeTimestamps(): Pair<Long, Long> {
    val calendar = Calendar.getInstance().apply {
      set(Calendar.HOUR_OF_DAY, 0)
      set(Calendar.MINUTE, 0)
      set(Calendar.SECOND, 0)
      set(Calendar.MILLISECOND, 0)
    }
    val start = calendar.timeInMillis
    calendar.apply {
      set(Calendar.HOUR_OF_DAY, 23)
      set(Calendar.MINUTE, 59)
      set(Calendar.SECOND, 59)
      set(Calendar.MILLISECOND, 999)
    }
    val end = calendar.timeInMillis
    return Pair(start, end)
  }

  override fun onCleared() {
    super.onCleared()
    cancelSessionTimeout()
    assistantManager.release()
    alarmToneCoordinator.release()
  }
}
