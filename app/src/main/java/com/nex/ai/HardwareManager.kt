package com.nex.ai

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.provider.MediaStore
import android.provider.Settings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed class CommandResult {
    data class Handled(val response: String) : CommandResult()
    data class PassToAI(val originalQuery: String) : CommandResult()
}

object HardwareManager {

    fun processVoiceCommand(context: Context, rawQuery: String): CommandResult {
        val query = rawQuery.lowercase(Locale.ROOT).trim()

        // 1. Flashlight / Torch Controls
        if (query.contains("torch on") || query.contains("flashlight on") || query.contains("turn on torch") || query.contains("turn on light")) {
            return CommandResult.Handled(toggleTorch(context, true))
        }
        if (query.contains("torch off") || query.contains("flashlight off") || query.contains("turn off torch") || query.contains("turn off light")) {
            return CommandResult.Handled(toggleTorch(context, false))
        }

        // 2. Volume Controls (Full Audio Access)
        if (query.contains("volume up") || query.contains("increase volume") || query.contains("louder")) {
            return CommandResult.Handled(adjustVolume(context, AudioManager.ADJUST_RAISE))
        }
        if (query.contains("volume down") || query.contains("decrease volume") || query.contains("lower volume")) {
            return CommandResult.Handled(adjustVolume(context, AudioManager.ADJUST_LOWER))
        }
        if (query.contains("mute") || query.contains("silence")) {
            return CommandResult.Handled(setVolumeMute(context))
        }
        if (query.contains("max volume") || query.contains("maximum volume")) {
            return CommandResult.Handled(setMaxVolume(context))
        }

        // 3. Battery Percentage Check
        if (query.contains("battery") || query.contains("charge level") || query.contains("power level")) {
            val batteryLevel = getBatteryPercentage(context)
            return CommandResult.Handled("Sir, current power level is at $batteryLevel percent.")
        }

        // 4. Exact Time Check
        if (query.contains("time") || query.contains("what time is it") || query.contains("clock")) {
            val currentTime = SimpleDateFormat("hh:mm a", Locale.UK).format(Date())
            return CommandResult.Handled("It is currently $currentTime, Sir.")
        }

        // 5. System Quick Settings / Navigation
        if (query.contains("open wifi") || query.contains("wifi settings")) {
            launchSystemIntent(context, Settings.ACTION_WIFI_SETTINGS)
            return CommandResult.Handled("Opening Wi-Fi configuration, Sir.")
        }
        if (query.contains("open bluetooth") || query.contains("bluetooth settings")) {
            launchSystemIntent(context, Settings.ACTION_BLUETOOTH_SETTINGS)
            return CommandResult.Handled("Accessing Bluetooth protocols, Sir.")
        }
        if (query.contains("open settings") || query.contains("system settings")) {
            launchSystemIntent(context, Settings.ACTION_SETTINGS)
            return CommandResult.Handled("Launching system settings, Sir.")
        }
        if (query.contains("go home") || query.contains("home screen")) {
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(homeIntent)
            return CommandResult.Handled("Navigating to home screen, Sir.")
        }

        // 6. Dialer / Phone Call
        if (query.contains("open dialer") || query.contains("open phone") || query.contains("call")) {
            val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(dialIntent)
            return CommandResult.Handled("Phone dialer engaged, Sir.")
        }

        // 7. Camera Optics
        if (query.contains("open camera") || query.contains("take photo")) {
            val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            return try {
                context.startActivity(cameraIntent)
                CommandResult.Handled("Camera optics online, Sir.")
            } catch (e: Exception) {
                CommandResult.Handled("Sir, unable to initialize camera optics.")
            }
        }

        // 8. Private WhatsApp/SMS Sentinel
        if (query.contains("message") || query.contains("who texted") || query.contains("read message")) {
            val brief = NotificationSentinelService.getLatestNotificationBrief()
            return CommandResult.Handled(brief)
        }

        // 9. UNIVERSAL APP LAUNCHER (Opens ANY app installed on device: Instagram, Spotify, Chrome, Calculator, etc.)
        if (query.startsWith("open ") || query.startsWith("launch ")) {
            val targetAppName = query.removePrefix("open ").removePrefix("launch ").trim()
            val launched = launchAppByName(context, targetAppName)
            if (launched) {
                return CommandResult.Handled("Opening $targetAppName now, Sir.")
            }
        }

        // If not a hardware command, route query to Groq LLaMA
        return CommandResult.PassToAI(rawQuery)
    }

    private fun toggleTorch(context: Context, turnOn: Boolean): String {
        return try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList[0]
            cameraManager.setTorchMode(cameraId, turnOn)
            if (turnOn) "Flashlight initialized, Sir." else "Flashlight disengaged, Sir."
        } catch (e: Exception) {
            "Sir, flashlight module is unreachable."
        }
    }

    private fun adjustVolume(context: Context, direction: Int): String {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
        return "Audio levels adjusted, Sir."
    }

    private fun setVolumeMute(context: Context): String {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, AudioManager.FLAG_SHOW_UI)
        return "Audio output muted, Sir."
    }

    private fun setMaxVolume(context: Context): String {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, max, AudioManager.FLAG_SHOW_UI)
        return "Audio output set to maximum capacity, Sir."
    }

    private fun getBatteryPercentage(context: Context): Int {
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (level >= 0 && scale > 0) (level * 100) / scale else 0
    }

    private fun launchSystemIntent(context: Context, action: String) {
        val intent = Intent(action).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    private fun launchAppByName(context: Context, appName: String): Boolean {
        val pm = context.packageManager
        val packages = pm.getInstalledApplications(0)

        for (app in packages) {
            if ((app.flags and ApplicationInfo.FLAG_SYSTEM) != 0 && pm.getLaunchIntentForPackage(app.packageName) == null) {
                continue
            }
            val label = pm.getApplicationLabel(app).toString().lowercase(Locale.ROOT)
            if (label == appName || label.contains(appName)) {
                val launchIntent = pm.getLaunchIntentForPackage(app.packageName)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                    return true
                }
            }
        }
        return false
    }
}
