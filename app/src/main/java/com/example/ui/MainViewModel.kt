package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.SessionRepository
import com.example.data.UsageSession
import com.example.service.DetoxService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainViewModel(
    private val application: Application,
    private val repository: SessionRepository
) : AndroidViewModel(application) {

    // Collect service status reactively
    val isServiceRunning: StateFlow<Boolean> = DetoxService.isServiceRunning
    val currentCountdown: StateFlow<Int> = DetoxService.currentCountdown
    val selectedDelaySeconds: StateFlow<Int> = DetoxService.selectedDelaySeconds
    val activeSessionSeconds: StateFlow<Long> = DetoxService.activeSessionSeconds
    val isScreenInteractive: StateFlow<Boolean> = DetoxService.isScreenInteractive

    // Collect all database sessions
    val allSessions: StateFlow<List<UsageSession>> = repository.allSessions
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Calculate today's statistics
    val statsState: StateFlow<StatsData> = allSessions.map { sessions ->
        calculateStats(sessions)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = StatsData()
    )

    fun toggleService() {
        val context = application.applicationContext
        if (isServiceRunning.value) {
            // Stop service
            context.stopService(Intent(context, DetoxService::class.java))
        } else {
            // Start service
            val intent = Intent(context, DetoxService::class.java).apply {
                putExtra("delay_seconds", selectedDelaySeconds.value)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    fun setDelaySeconds(seconds: Int) {
        val context = application.applicationContext
        if (isServiceRunning.value) {
            // Update active service configuration
            val intent = Intent(context, DetoxService::class.java).apply {
                putExtra("delay_seconds", seconds)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } else {
            // Just update UI configuration
            DetoxService.selectedDelaySeconds.value = seconds
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.deleteAllSessions()
        }
    }

    private fun calculateStats(sessions: List<UsageSession>): StatsData {
        val now = Calendar.getInstance()
        val todayYear = now.get(Calendar.YEAR)
        val todayDay = now.get(Calendar.DAY_OF_YEAR)

        val todaySessions = sessions.filter {
            val cal = Calendar.getInstance().apply { timeInMillis = it.unlockTime }
            cal.get(Calendar.YEAR) == todayYear && cal.get(Calendar.DAY_OF_YEAR) == todayDay
        }

        val totalUnlocks = todaySessions.size
        val totalWarnings = todaySessions.count { it.warned }

        // Compute average session duration (excluding incomplete ones)
        val completedSessions = todaySessions.filter { it.lockTime > 0 }
        val avgDurationSeconds = if (completedSessions.isNotEmpty()) {
            completedSessions.map { it.durationSeconds }.average().toLong()
        } else {
            0L
        }

        return StatsData(
            totalUnlocks = totalUnlocks,
            totalWarnings = totalWarnings,
            avgDurationSeconds = avgDurationSeconds
        )
    }

    // Helper to format long duration
    fun formatDuration(seconds: Long): String {
        return when {
            seconds < 60 -> "${seconds}秒"
            else -> {
                val mins = seconds / 60
                val secs = seconds % 60
                if (secs > 0) "${mins}分${secs}秒" else "${mins}分钟"
            }
        }
    }

    // Helper to format timestamp
    fun formatTimestamp(timestamp: Long): String {
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    class Factory(
        private val application: Application,
        private val repository: SessionRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
                return MainViewModel(application, repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}

data class StatsData(
    val totalUnlocks: Int = 0,
    val totalWarnings: Int = 0,
    val avgDurationSeconds: Long = 0
)
