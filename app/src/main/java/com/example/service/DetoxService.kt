package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.KeyguardManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.os.Vibrator
import android.os.VibratorManager
import android.os.VibrationEffect
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.AppDatabase
import com.example.data.SessionRepository
import com.example.data.UsageSession
import com.example.ui.WarningActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class DetoxService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    private var timerJob: Job? = null

    private lateinit var repository: SessionRepository
    private var currentSessionId: Long = 0L
    private var currentSessionGeneration = 0L
    private val pendingSessionEndTimes = mutableMapOf<Long, Long>()
    private var screenReceiver: BroadcastReceiver? = null
    private var sessionStartedAtElapsedRealtime = 0L
    private var warningScheduleAnchorElapsedRealtime = 0L
    private var nextWarningAtElapsedRealtime = 0L

    companion object {
        private const val TAG = "DetoxService"
        private const val NOTIFICATION_CHANNEL_ID = "detox_service_channel"
        private const val WARNING_CHANNEL_ID = "detox_warning_channel"
        private const val NOTIFICATION_ID = 8888
        private const val WARNING_NOTIFICATION_ID = 9999

        // Live status flows for real-time UI synchronization
        val isServiceRunning = MutableStateFlow(false)
        val currentCountdown = MutableStateFlow(60) // in seconds
        val selectedDelaySeconds = MutableStateFlow(60) // customizable (default 60s)
        val activeSessionSeconds = MutableStateFlow(0L) // active session duration
        val isScreenInteractive = MutableStateFlow(true)
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate service")
        val database = AppDatabase.getDatabase(this)
        repository = SessionRepository(database.usageSessionDao())

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification(getString(R.string.service_running_desc, selectedDelaySeconds.value)))
        isServiceRunning.value = true

        registerScreenBroadcastReceiver()

        // If service is started when screen is already on, begin session immediately
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        val isScreenOnNow = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            pm.isInteractive
        } else {
            @Suppress("DEPRECATION")
            pm.isScreenOn
        }
        
        isScreenInteractive.value = isScreenOnNow
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (isScreenOnNow && !keyguardManager.isKeyguardLocked) {
            startTrackingSession()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand service")
        // Handle changes in configured delay
        intent?.let {
            val newDelay = it.getIntExtra("delay_seconds", -1)
            if (newDelay > 0 && newDelay != selectedDelaySeconds.value) {
                selectedDelaySeconds.value = newDelay
                // If counting down, reset countdown to new delay
                if (timerJob?.isActive == true) {
                    currentCountdown.value = newDelay
                    val now = SystemClock.elapsedRealtime()
                    warningScheduleAnchorElapsedRealtime = now
                    nextWarningAtElapsedRealtime = now + newDelay * 1_000L
                }
                updateForegroundNotification(getString(R.string.service_running_desc, newDelay))
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.d(TAG, "onDestroy service")
        isServiceRunning.value = false
        unregisterScreenBroadcastReceiver()
        stopTrackingSession(saveSession = true)
        serviceJob.cancel()
        super.onDestroy()
    }

    private fun registerScreenBroadcastReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        screenReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_OFF -> {
                        Log.d(TAG, "Screen off received")
                        isScreenInteractive.value = false
                        stopTrackingSession(saveSession = true)
                    }
                    Intent.ACTION_SCREEN_ON -> {
                        Log.d(TAG, "Screen on received")
                        isScreenInteractive.value = true
                        // Devices without a secure lock screen may not send
                        // ACTION_USER_PRESENT. Start only when the keyguard is
                        // already gone; otherwise wait for the unlock broadcast.
                        val keyguardManager =
                            getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
                        if (!keyguardManager.isKeyguardLocked) {
                            startTrackingSession()
                        }
                    }
                    Intent.ACTION_USER_PRESENT -> {
                        Log.d(TAG, "User present (unlocked) received")
                        isScreenInteractive.value = true
                        // Secure unlock completed, restart countdown to be precise
                        startTrackingSession()
                    }
                }
            }
        }
        registerReceiver(screenReceiver, filter)
    }

    private fun unregisterScreenBroadcastReceiver() {
        screenReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (e: Exception) {
                Log.e(TAG, "Error unregistering receiver", e)
            }
        }
        screenReceiver = null
    }

    private fun startTrackingSession() {
        // SCREEN_ON and USER_PRESENT can both arrive for one unlock. Do not
        // restart the timer or insert a second database session.
        if (timerJob?.isActive == true) {
            return
        }

        activeSessionSeconds.value = 0L

        val unlockTime = System.currentTimeMillis()
        val sessionGeneration = ++currentSessionGeneration
        val startedAtElapsedRealtime = SystemClock.elapsedRealtime()
        sessionStartedAtElapsedRealtime = startedAtElapsedRealtime
        warningScheduleAnchorElapsedRealtime = startedAtElapsedRealtime
        nextWarningAtElapsedRealtime =
            startedAtElapsedRealtime + selectedDelaySeconds.value * 1_000L
        currentCountdown.value = selectedDelaySeconds.value

        // Record session start to database
        serviceScope.launch {
            try {
                val session = UsageSession(
                    unlockTime = unlockTime,
                    lockTime = 0,
                    durationSeconds = 0,
                    warned = false
                )
                val insertedSessionId = repository.insertSession(session)
                if (
                    currentSessionGeneration == sessionGeneration &&
                    timerJob?.isActive == true
                ) {
                    currentSessionId = insertedSessionId
                    Log.d(TAG, "Inserted session: $currentSessionId")
                } else {
                    // The screen was locked before Room returned. Close this
                    // late insert instead of leaving an orphan active session.
                    val endTime = pendingSessionEndTimes.remove(sessionGeneration)
                        ?: System.currentTimeMillis()
                    repository.updateSession(
                        session.copy(
                            id = insertedSessionId.toInt(),
                            lockTime = endTime,
                            durationSeconds =
                                ((endTime - unlockTime).coerceAtLeast(0L)) / 1_000L
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to insert session", e)
            }
        }

        // Start countdown timer and active duration tracker
        timerJob = serviceScope.launch {
            while (true) {
                val now = SystemClock.elapsedRealtime()
                activeSessionSeconds.value =
                    ((now - sessionStartedAtElapsedRealtime).coerceAtLeast(0L)) / 1_000L

                if (now >= nextWarningAtElapsedRealtime) {
                    triggerWarning()

                    // Stay aligned to real elapsed time from the unlock/configuration
                    // anchor. If Android delayed this coroutine, skip missed slots
                    // instead of drifting or showing several alerts at once.
                    val intervalMillis = selectedDelaySeconds.value * 1_000L
                    val elapsedFromAnchor =
                        (now - warningScheduleAnchorElapsedRealtime).coerceAtLeast(0L)
                    val nextInterval = elapsedFromAnchor / intervalMillis + 1L
                    nextWarningAtElapsedRealtime =
                        warningScheduleAnchorElapsedRealtime + nextInterval * intervalMillis
                }

                val remainingMillis =
                    (nextWarningAtElapsedRealtime - now).coerceAtLeast(0L)
                currentCountdown.value =
                    ((remainingMillis + 999L) / 1_000L).toInt()
                delay(remainingMillis.coerceIn(1L, 1_000L))
            }
        }
    }

    private fun stopTrackingSession(saveSession: Boolean) {
        timerJob?.cancel()
        timerJob = null

        if (saveSession && currentSessionId == 0L && sessionStartedAtElapsedRealtime > 0L) {
            pendingSessionEndTimes[currentSessionGeneration] = System.currentTimeMillis()
        }

        if (saveSession && currentSessionId != 0L) {
            val sessionId = currentSessionId
            val activeSeconds = if (sessionStartedAtElapsedRealtime > 0L) {
                (SystemClock.elapsedRealtime() - sessionStartedAtElapsedRealtime)
                    .coerceAtLeast(0L) / 1_000L
            } else {
                activeSessionSeconds.value
            }
            serviceScope.launch(Dispatchers.IO) {
                try {
                    val session = repository.getSessionById(sessionId.toInt())
                    if (session != null) {
                        val lockTime = System.currentTimeMillis()
                        val duration = if (activeSeconds > 0) {
                            activeSeconds
                        } else {
                            (lockTime - session.unlockTime) / 1_000L
                        }
                        val updated = session.copy(
                            lockTime = lockTime,
                            durationSeconds = duration
                        )
                        repository.updateSession(updated)
                        Log.d(TAG, "Updated session $sessionId with duration $duration seconds")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to update session closure", e)
                }
            }
        }
        currentSessionId = 0L
        sessionStartedAtElapsedRealtime = 0L
        warningScheduleAnchorElapsedRealtime = 0L
        nextWarningAtElapsedRealtime = 0L
    }

    private fun triggerWarning() {
        Log.d(TAG, "Detox Warning Triggered!")
        
        // 1. Update database record to warned = true
        if (currentSessionId != 0L) {
            val sessionId = currentSessionId
            serviceScope.launch(Dispatchers.IO) {
                try {
                    val session = repository.getSessionById(sessionId.toInt())
                    if (session != null) {
                        repository.updateSession(session.copy(warned = true))
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to update warning status in DB", e)
                }
            }
        }

        // 2. Play warning vibration
        triggerVibration()

        // 3. Launch Warning Activity!
        // We do this if overlay permission is granted or directly as fullScreenIntent.
        val warningIntent = Intent(this, WarningActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }

        // Always post the high-priority full-screen notification. It is the reliable
        // fallback when Android or an OEM blocks background activity launches.
        showHighPriorityWarningNotification(warningIntent)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
            // Can draw over other apps -> launch activity directly!
            Log.d(TAG, "Has overlay permission, launching WarningActivity directly")
            try {
                startActivity(warningIntent)
            } catch (e: Exception) {
                Log.e(TAG, "Direct warning activity launch failed", e)
            }
        } else {
            Log.d(TAG, "No overlay permission; using fullscreen notification warning")
        }
    }

    private fun triggerVibration() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                val vibrator = vibratorManager.defaultVibrator
                val effect = VibrationEffect.createWaveform(longArrayOf(0, 500, 200, 500, 200, 800), -1)
                vibrator.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (vibrator.hasVibrator()) {
                    val effect = VibrationEffect.createWaveform(longArrayOf(0, 500, 200, 500, 200, 800), -1)
                    vibrator.vibrate(effect)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Vibration failed", e)
        }
    }

    private fun showHighPriorityWarningNotification(intent: Intent) {
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(this, 1001, intent, pendingIntentFlags)

        val warningNotification = NotificationCompat.Builder(this, WARNING_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // fallback
            .setContentTitle(getString(R.string.warning_title))
            .setContentText(getString(R.string.warning_desc))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setDefaults(Notification.DEFAULT_ALL)
            .setAutoCancel(true)
            .setFullScreenIntent(pendingIntent, true)
            .setTimeoutAfter(30_000)
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(WARNING_NOTIFICATION_ID, warningNotification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "又玩手机守护服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "保持后台监测手机解锁状态，一分钟后提示放下手机。"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)

            val warningChannel = NotificationChannel(
                WARNING_CHANNEL_ID,
                "强制提醒",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "到达设定使用时长时显示全屏提醒。"
                enableVibration(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            manager.createNotificationChannel(warningChannel)
        }
    }

    private fun buildNotification(contentText: String): Notification {
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE
        } else {
            0
        }
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, pendingIntentFlags)

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("又玩手机・已开启防沉迷")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // fallback
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateForegroundNotification(contentText: String) {
        val notification = buildNotification(contentText)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}
