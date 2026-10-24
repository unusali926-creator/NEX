package com.nex.ai

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.camera2.CameraManager
import android.os.BatteryManager
import android.provider.MediaStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed class CommandResult {
    data class Handled(val response: String) : CommandResult()
    data class PassToAI(val originalQuery: String) : CommandResult()
}

object HardwareManager {

    private var isTorchOn: Boolean = false

    fun processVoiceCommand(context: Context, rawQuery: String): CommandResult {
        val query = rawQuery.lowercase(Locale.ROOT).trim()

        // 1. Flashlight / Torch Controls
        if (query.contains("torch on") || query.contains("flashlight on") || query.contains("light chalu") || query.contains("torch jalao")) {
            val response = toggleTorch(context, true)
            return CommandResult.Handled(response)
        }
        if (query.contains("torch off") || query.contains("flashlight off") || query.contains("light band") || query.contains("torch band")) {
            val response = toggleTorch(context, false)
            return CommandResult.Handled(response)
        }

        // 2. Battery Percentage Check
        if (query.contains("battery") || query.contains("charge") || query.contains("charging")) {
            val batteryLevel = getBatteryPercentage(context)
            return CommandResult.Handled("Sir, battery abhi $batteryLevel percent par hai.")
        }

        // 3. Exact Time Check
        if (query.contains("time") || query.contains("kitne baje") || query.contains("samay")) {
            val currentTime = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
            return CommandResult.Handled("Sir, abhi theek $currentTime ho rahe hain.")
        }

        // 4. WhatsApp Launcher
        if (query.contains("whatsapp kholo") || query.contains("open whatsapp")) {
            val launched = launchApp(context, "com.whatsapp")
            return if (launched) {
                CommandResult.Handled("WhatsApp open kar diya hai, Sir.")
            } else {
                CommandResult.Handled("Sir, aapke phone me WhatsApp install nahi mila.")
            }
        }

        // 5. YouTube Launcher
        if (query.contains("youtube kholo") || query.contains("open youtube")) {
            val launched = launchApp(context, "com.google.android.youtube")
            return if (launched) {
                CommandResult.Handled("YouTube launch kar diya hai, Sir.")
            } else {
                CommandResult.Handled("Sir, YouTube application nahi mili.")
            }
        }

        // 6. Camera Launcher
        if (query.contains("camera kholo") || query.contains("open camera")) {
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            return try {
                context.startActivity(intent)
                CommandResult.Handled("Camera optics online hain, Sir.")
            } catch (e: Exception) {
                CommandResult.Handled("Sir, camera launch nahi ho paya.")
            }
        }

        // 7. Private WhatsApp/SMS Sentinel Reader
        if (query.contains("message") || query.contains("kaun message kiya") || query.contains("kiska message")) {
            val brief = NotificationSentinelService.getLatestNotificationBrief()
            return CommandResult.Handled(brief)
        }

        // Agar hardware command nahi hai, toh Groq LLaMA AI ko pass karein
        return CommandResult.PassToAI(rawQuery)
    }

    private fun toggleTorch(context: Context, turnOn: Boolean): String {
        return try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList[0]
            cameraManager.setTorchMode(cameraId, turnOn)
            isTorchOn = turnOn
            if (turnOn) "Flashlight trigger kar di hai, Sir." else "Flashlight off kar di gayi hai, Sir."
        } catch (e: Exception) {
            "Sir, flashlight hardware accessible nahi hai."
        }
    }

    private fun getBatteryPercentage(context: Context): Int {
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (level >= 0 && scale > 0) (level * 100) / scale else 0
    }

    private fun launchApp(context: Context, packageName: String): Boolean {
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                true
            } else false
        } catch (e: Exception) {
            false
        }
    }
}
