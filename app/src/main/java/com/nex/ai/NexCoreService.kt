package com.nex.ai

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Locale

class NexCoreService : Service() {

    private lateinit var voiceManager: VoiceManager
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        const val CHANNEL_ID = "NEX_SENTINEL_CHANNEL"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "ACTION_START_NEX"
        const val ACTION_STOP = "ACTION_STOP_NEX"
        var isServiceRunning = false

        // Bridge to MainActivity HUD
        var onStateUpdateListener: ((String, String) -> Unit)? = null
        var onTextHeardListener: ((String) -> Unit)? = null

        var currentAssistantState = "STANDBY"
        var currentStatusDetail = "TAP POWER ON TO ACTIVATE"
        var lastHeardText = ""
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        // CPU Keep-Alive: Prevents phone from sleeping when screen is OFF
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "NexAI:CpuKeepAliveWakeLock").apply {
            setReferenceCounted(false)
            acquire(24 * 60 * 60 * 1000L) // 24-hour continuous shield
        }

        // Initialize 24/7 Master Voice Cadence inside Service
        voiceManager = VoiceManager(
            context = this,
            onSpeechRecognized = { userQuery ->
                lastHeardText = userQuery
                onTextHeardListener?.invoke(userQuery)
                wakeScreenBriefly() // Wakes screen up when you speak
                handleVoiceCommand(userQuery)
            },
            onStateUpdate = { state, detail ->
                currentAssistantState = state
                currentStatusDetail = detail
                updateNotification(detail)
                onStateUpdateListener?.invoke(state, detail)
            }
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                shutdownService()
                return START_NOT_STICKY
            }
            else -> {
                isServiceRunning = true
                val notification = buildNotification("N.E.X. Online & Listening (Off-Screen Ready)...")

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                        } else 0
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }

                voiceManager.startContinuousCadence()
            }
        }
        return START_STICKY
    }

    private fun handleVoiceCommand(rawQuery: String) {
        val query = rawQuery.lowercase(Locale.ROOT).trim()

        // Sleep command
        if (query.contains("sleep") || query.contains("standby") || query.contains("so jao")) {
            voiceManager.speak("Yes Sir, powering down systems into standby.")
            shutdownService()
            return
        }

        serviceScope.launch {
            when (val result = HardwareManager.processVoiceCommand(this@NexCoreService, rawQuery)) {
                is CommandResult.Handled -> {
                    voiceManager.speak(result.response)
                }
                is CommandResult.PassToAI -> {
                    currentAssistantState = "PROCESSING"
                    currentStatusDetail = "NEX THINKING..."
                    onStateUpdateListener?.invoke(currentAssistantState, currentStatusDetail)

                    val aiResponse = AiEngine.queryNex(result.originalQuery)
                    voiceManager.speak(aiResponse)
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun wakeScreenBriefly() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            val screenLock = powerManager.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "NexAI:ScreenWakeUp"
            )
            screenLock.acquire(4000L) // Lights up screen for 4 seconds
        } catch (_: Exception) {}
    }

    private fun buildNotification(statusText: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val sleepIntent = Intent(this, NexCoreService::class.java).apply {
            action = ACTION_STOP
        }
        val sleepPendingIntent = PendingIntent.getService(
            this, 1, sleepIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("N.E.X. / F.R.I.D.A.Y. (ACTIVE)")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setContentIntent(openAppPendingIntent)
            .addAction(android.R.drawable.ic_lock_power_off, "SLEEP", sleepPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(statusText: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, buildNotification(statusText))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Nex Core Sentinel",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background & Off-Screen Sentinel for Nex Assistant"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun shutdownService() {
        isServiceRunning = false
        currentAssistantState = "STANDBY"
        currentStatusDetail = "SYSTEM IN STANDBY"
        onStateUpdateListener?.invoke(currentAssistantState, currentStatusDetail)

        voiceManager.shutdown()
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        shutdownService()
        serviceJob.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
