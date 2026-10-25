package com.nex.ai

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
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

    private val POPULAR_APPS = mapOf(
        "instagram" to "com.instagram.android",
        "insta" to "com.instagram.android",
        "whatsapp" to "com.whatsapp",
        "youtube" to "com.google.android.youtube",
        "yt" to "com.google.android.youtube",
        "chrome" to "com.android.chrome",
        "browser" to "com.android.chrome",
        "spotify" to "com.spotify.music",
        "facebook" to "com.facebook.katana",
        "snapchat" to "com.snapchat.android",
        "telegram" to "org.telegram.messenger",
        "calculator" to "com.google.android.calculator"
    )

    // Bypasses Android 10 to Android 14 Background Activity Launch (BAL) restrictions
    fun launchIntentSafely(context: Context, intent: Intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // Android 14 (API 34)
                val options = ActivityOptions.makeBasic()
                // Explicitly allow starting activity from background service
                options.setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                val pi = PendingIntent.getActivity(
                    context,
                    (System.currentTimeMillis() % 10000).toInt(),
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    options.toBundle()
                )
                pi.send(context, 0, null, null, null, null, options.toBundle())
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val pi = PendingIntent.getActivity(
                    context,
                    (System.currentTimeMillis() % 10000).toInt(),
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                pi.send()
            } else {
                context.startActivity(intent)
            }
        } catch (_: Exception) {
            try {
                context.startActivity(intent)
            } catch (_: Exception) {}
        }
    }

    suspend fun processVoiceCommand(context: Context, rawQuery: String): CommandResult {
        val cleanFullQuery = rawQuery.lowercase(Locale.ROOT)
            .replace(Regex("[\\p{Punct}]"), " ")
            .trim()

        // 1. FAST PATH: Instant offline check for direct simple words
        val fastResponse = checkFastOfflineCommands(context, cleanFullQuery)
        if (fastResponse != null) {
            return CommandResult.Handled(fastResponse)
        }

        // 2. INTELLIGENT AI AGENT PATH: Lets 70B LLaMA understand ANY natural language, slang or intent!
        val aiRawResponse = AiEngine.queryNex(rawQuery)
        val cleanSpeech = parseAndExecuteAiAction(context, aiRawResponse)
        return CommandResult.Handled(cleanSpeech)
    }

    private fun checkFastOfflineCommands(context: Context, query: String): String? {
        // Calling Nex alone
        val wakeKeywords = listOf("nex", "next", "necks", "nyx", "hey nex", "hello nex", "hi nex", "ok nex", "nex suno", "नेक्स", "नेक्स्ट")
        if (wakeKeywords.any { query == it }) {
            return "Yes Sir, standing by. What are your orders?"
        }

        // Fast Torch
        if (query == "torch on" || query == "turn on torch" || query == "torch jalao" || query == "torch chalu karo") {
            return toggleTorch(context, true)
        }
        if (query == "torch off" || query == "turn off torch" || query == "torch band karo") {
            return toggleTorch(context, false)
        }

        // Fast Reels
        if (query == "show me reels" || query == "open reels" || query == "reels dikhao" || query == "reels") {
            launchReels(context)
            return "Yes Sir, launching Instagram Reels right away."
        }

        // Fast Volume
        if (query == "volume up" || query == "awaz badhao") {
            return adjustVolume(context, AudioManager.ADJUST_RAISE)
        }
        if (query == "volume down" || query == "awaz kam karo") {
            return adjustVolume(context, AudioManager.ADJUST_LOWER)
        }
        if (query == "mute" || query == "shant ho jao") {
            return setVolumeMute(context)
        }

        // Fast Battery
        if (query == "battery" || query == "battery kitni hai") {
            val level = getBatteryPercentage(context)
            return "Yes Sir, battery is at $level percent."
        }

        // Fast Time
        if (query == "time" || query == "time kya hua hai" || query == "kitne baje") {
            val time = SimpleDateFormat("hh:mm a", Locale.UK).format(Date())
            return "Yes Sir, it is currently $time."
        }

        return null
    }

    // Parses Action Tags generated by AI (e.g. [EXEC:OPEN:instagram], [EXEC:REELS], etc.)
    private fun parseAndExecuteAiAction(context: Context, aiResponse: String): String {
        var cleanText = aiResponse

        if (cleanText.contains("[EXEC:TORCH_ON]")) {
            toggleTorch(context, true)
            cleanText = cleanText.replace("[EXEC:TORCH_ON]", "").trim()
        }
        if (cleanText.contains("[EXEC:TORCH_OFF]")) {
            toggleTorch(context, false)
            cleanText = cleanText.replace("[EXEC:TORCH_OFF]", "").trim()
        }
        if (cleanText.contains("[EXEC:REELS]")) {
            launchReels(context)
            cleanText = cleanText.replace("[EXEC:REELS]", "").trim()
        }
        if (cleanText.contains("[EXEC:OPEN:")) {
            val appName = cleanText.substringAfter("[EXEC:OPEN:").substringBefore("]").trim()
            launchApplication(context, appName)
            cleanText = cleanText.replace(Regex("\\[EXEC:OPEN:.*?\\]"), "").trim()
        }
        if (cleanText.contains("[EXEC:PLAY:")) {
            val songName = cleanText.substringAfter("[EXEC:PLAY:").substringBefore("]").trim()
            playOnYouTube(context, songName)
            cleanText = cleanText.replace(Regex("\\[EXEC:PLAY:.*?\\]"), "").trim()
        }
        if (cleanText.contains("[EXEC:VOLUME_UP]")) {
            adjustVolume(context, AudioManager.ADJUST_RAISE)
            cleanText = cleanText.replace("[EXEC:VOLUME_UP]", "").trim()
        }
        if (cleanText.contains("[EXEC:VOLUME_DOWN]")) {
            adjustVolume(context, AudioManager.ADJUST_LOWER)
            cleanText = cleanText.replace("[EXEC:VOLUME_DOWN]", "").trim()
        }
        if (cleanText.contains("[EXEC:VOLUME_MUTE]")) {
            setVolumeMute(context)
            cleanText = cleanText.replace("[EXEC:VOLUME_MUTE]", "").trim()
        }
        if (cleanText.contains("[EXEC:VOLUME_MAX]")) {
            setMaxVolume(context)
            cleanText = cleanText.replace("[EXEC:VOLUME_MAX]", "").trim()
        }

        return cleanText
    }

    fun launchReels(context: Context) {
        val uri = Uri.parse("https://www.instagram.com/reels/")
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage("com.instagram.android")
        }
        launchIntentSafely(context, intent)
    }

    fun playOnYouTube(context: Context, query: String) {
        val uri = Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(query))
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage("com.google.android.youtube")
        }
        launchIntentSafely(context, intent)
    }

    fun launchApplication(context: Context, appName: String): Boolean {
        val pm = context.packageManager
        val cleanName = appName.lowercase().trim()

        val knownPackage = POPULAR_APPS[cleanName]
        if (knownPackage != null) {
            val intent = pm.getLaunchIntentForPackage(knownPackage)
            if (intent != null) {
                launchIntentSafely(context, intent)
                return true
            }
        }

        try {
            val packages = pm.getInstalledApplications(0)
            for (app in packages) {
                val label = pm.getApplicationLabel(app).toString().lowercase(Locale.ROOT)
                if (label == cleanName || label.contains(cleanName) || cleanName.contains(label)) {
                    val launchIntent = pm.getLaunchIntentForPackage(app.packageName)
                    if (launchIntent != null) {
                        launchIntentSafely(context, launchIntent)
                        return true
                    }
                }
            }
        } catch (_: Exception) {}

        return false
    }

    fun toggleTorch(context: Context, turnOn: Boolean): String {
        return try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            var targetCameraId: String? = null

            for (id in cameraManager.cameraIdList) {
                val characteristics = cameraManager.getCameraCharacteristics(id)
                val hasFlash = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                if (hasFlash && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    targetCameraId = id
                    break
                }
            }

            if (targetCameraId == null) {
                targetCameraId = cameraManager.cameraIdList.firstOrNull { id ->
                    cameraManager.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                }
            }

            if (targetCameraId != null) {
                cameraManager.setTorchMode(targetCameraId, turnOn)
                if (turnOn) "Yes Sir, flashlight initialized." else "Yes Sir, flashlight disengaged."
            } else {
                "Yes Sir, no camera flash hardware detected."
            }
        } catch (e: Exception) {
            "Yes Sir, camera flash module is unreachable."
        }
    }

    fun adjustVolume(context: Context, direction: Int): String {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
        return "Yes Sir, audio levels adjusted."
    }

    fun setVolumeMute(context: Context): String {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, AudioManager.FLAG_SHOW_UI)
        return "Yes Sir, audio output muted."
    }

    fun setMaxVolume(context: Context): String {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, max, AudioManager.FLAG_SHOW_UI)
        return "Yes Sir, audio output set to maximum capacity."
    }

    fun getBatteryPercentage(context: Context): Int {
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (level >= 0 && scale > 0) (level * 100) / scale else 0
    }
}
