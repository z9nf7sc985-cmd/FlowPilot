package com.flowpilot.automation.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class LogEntry(
    val timeMs: Long = System.currentTimeMillis(),
    val level: Level = Level.INFO,
    val automationId: String? = null,
    val blockId: String? = null,
    val message: String
) {
    enum class Level { DEBUG, INFO, WARN, ERROR }
    fun timeLabel(): String =
        SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(timeMs))
}

object RunLog {
    private const val MAX = 500
    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())
    val entries: StateFlow<List<LogEntry>> = _entries.asStateFlow()

    private val _currentBlockId = MutableStateFlow<String?>(null)
    val currentBlockId: StateFlow<String?> = _currentBlockId.asStateFlow()

    private val _runningAutomationId = MutableStateFlow<String?>(null)
    val runningAutomationId: StateFlow<String?> = _runningAutomationId.asStateFlow()

    @Volatile var globalKill: Boolean = false

    fun setRunning(id: String?) { _runningAutomationId.value = id }
    fun setCurrentBlock(id: String?) { _currentBlockId.value = id }

    fun clear() { _entries.value = emptyList() }

    fun log(message: String, level: LogEntry.Level = LogEntry.Level.INFO, automationId: String? = null, blockId: String? = null) {
        val e = LogEntry(level = level, automationId = automationId, blockId = blockId, message = message)
        val next = (_entries.value + e).takeLast(MAX)
        _entries.value = next
    }
}
