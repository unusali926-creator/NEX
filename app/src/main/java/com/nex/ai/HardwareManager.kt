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

    private val POPULAR_APPS = mapOf(
        "instagram" to "com.instagram.android",
        "whatsapp" to "com.whatsapp",
        "youtube" to "com.google.android.youtube",
        "chrome" to "com.android.chrome",
        "browser" to "com.android.chrome",
        "spotify" to "com.spotify.music",
        "facebook" to "com.facebook.katana",
        "snapchat" to "com.snapchat.android",
        "telegram" to "org.telegram.messenger",
        "calculator" to "com.google.android.calculator"
    )

    fun processVoiceCommand(context: Context, rawQuery: String): CommandResult {
        // Strip punctuation and normalize string (e.g. "nex." -> "nex")
        val cleanFullQuery = rawQuery.lowercase(Locale.ROOT)
            .replace(Regex("[^a-zA-Z0-9 ]"), "")
            .trim()

        // 1. WAKE-WORD ALONE CHECK ("Nex", "Next", "Hey Nex", "Nex suno")
        val wakeKeywords = listOf("nex", "next", "necks", "nyx", "hey nex", "hello nex", "hi nex", "ok nex", "nex suno", "suno nex")
        if (wakeKeywords.any { cleanFullQuery == it }) {
            return CommandResult.Handled("Yes Sir, standing by. What are your orders?")
        }

        // Who are you / Identity
        if (cleanFullQuery.contains("who are you") || cleanFullQuery.contains("what is your name") || cleanFullQuery.contains("tum kaun ho")) {
            return CommandResult.Handled("Yes Sir, I am Nex, your personal artificial intelligence.")
        }

        // Strip "Nex" prefix if user said "Nex, open Instagram" -> "open instagram"
        var query = cleanFullQuery
        val callPrefixes = listOf("hey nex ", "hello nex ", "hi nex ", "ok nex ", "nex ", "next ")
        for (prefix in callPrefixes) {
            if (query.startsWith(prefix)) {
                query = query.removePrefix(prefix).trim()
                break
            }
        }

        // 2. DEEP IN-APP CONTROL: INSTAGRAM REELS
        if (query.contains("reel") || query.contains("reels") || query.contains("show me reels") || query.contains("open reels")) {
            val uri = Uri.parse("https://www.instagram.com/reels/")
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage("com.instagram.android")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
            }
            return try {
                context.startActivity(intent)
                CommandResult.Handled("Yes Sir, launching Instagram Reels right away.")
            } catch (e: Exception) {
                // Browser fallback
                val fallbackIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(fallbackIntent)
                CommandResult.Handled("Yes Sir, opening Instagram Reels.")
            }
        }

        // 3. DEEP IN-APP CONTROL: INSTAGRAM DMS / MESSAGES
        if (query.contains("instagram dm") || query.contains("instagram message") || query.contains("insta message")) {
            val uri = Uri.parse("https://www.instagram.com/direct/inbox/")
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage("com.instagram.android")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            return try {
                context.startActivity(intent)
                CommandResult.Handled("Yes Sir, opening Instagram Direct Messages.")
            } catch (e: Exception) {
                CommandResult.Handled("Yes Sir, unable to open Instagram inbox.")
            }
        }

        // 4. DEEP IN-APP CONTROL: YOUTUBE SHORTS
        if (query.contains("short") || query.contains("shorts") || query.contains("youtube shorts")) {
            val uri = Uri.parse("https://www.youtube.com/shorts")
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage("com.google.android.youtube")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            return try {
                context.startActivity(intent)
                CommandResult.Handled("Yes Sir, launching YouTube Shorts.")
            } catch (e: Exception) {
                CommandResult.Handled("Yes Sir, YouTube application is not reachable.")
            }
        }

        // 5. DEEP IN-APP CONTROL: PLAY MUSIC / VIDEO ON YOUTUBE
        if (query.startsWith("play ") || query.contains("on youtube") || query.contains("search youtube")) {
            val topic = query.replace("play", "")
                .replace("on youtube", "")
                .replace("search youtube", "")
                .replace("song", "")
                .trim()
            val uri = Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(topic))
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage("com.google.android.youtube")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            return try {
                context.startActivity(intent)
                CommandResult.Handled("Yes Sir, playing $topic on YouTube.")
            } catch (e: Exception) {
                CommandResult.Handled("Yes Sir, opening YouTube.")
            }
        }

        // 6. GOOGLE WEB SEARCH
        if (query.startsWith("google ") || query.startsWith("search web ")) {
            val searchTerm = query.removePrefix("google ").removePrefix("search web ").trim()
            val uri = Uri.parse("https://www.google.com/search?q=" + Uri.encode(searchTerm))
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            return CommandResult.Handled("Yes Sir, searching Google for $searchTerm.")
        }

        // 7. FLASHLIGHT / TORCH
        if (query.contains("torch on") || query.contains("flashlight on") || query.contains("turn on torch") || query.contains("turn on light")) {
            return CommandResult.Handled(toggleTorch(context, true))
        }
        if (query.contains("torch off") || query.contains("flashlight off") || query.contains("turn off torch") || query.contains("turn off light")) {
            return CommandResult.Handled(toggleTorch(context, false))
        }

        // 8. VOLUME CONTROLS
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

        // 9. BATTERY STATUS
        if (query.contains("battery") || query.contains("charge level") || query.contains("power level")) {
            val batteryLevel = getBatteryPercentage(context)
            return CommandResult.Handled("Yes Sir, current power level is at $batteryLevel percent.")
        }

        // 10. EXACT TIME
        if (query.contains("time") || query.contains("what time is it") || query.contains("clock")) {
            val currentTime = SimpleDateFormat("hh:mm a", Locale.UK).format(Date())
            return CommandResult.Handled("Yes Sir, it is currently $currentTime.")
        }

        // 11. SYSTEM SETTINGS
        if (query.contains("open wifi") || query.contains("wifi settings")) {
            launchSystemIntent(context, Settings.ACTION_WIFI_SETTINGS)
            return CommandResult.Handled("Yes Sir, opening Wi-Fi settings.")
        }
        if (query.contains("open bluetooth") || query.contains("bluetooth settings")) {
            launchSystemIntent(context, Settings.ACTION_BLUETOOTH_SETTINGS)
            return CommandResult.Handled("Yes Sir, opening Bluetooth settings.")
        }
        if (query.contains("open settings") || query.contains("system settings")) {
            launchSystemIntent(context, Settings.ACTION_SETTINGS)
            return CommandResult.Handled("Yes Sir, opening master settings.")
        }
        if (query.contains("go home") || query.contains("home screen")) {
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
            }
            context.startActivity(homeIntent)
            return CommandResult.Handled("Yes Sir, navigating to home screen.")
        }

        // 12. CAMERA OPTICS
        if (query.contains("open camera") || query.contains("take photo")) {
            val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            return try {
                context.startActivity(cameraIntent)
                CommandResult.Handled("Yes Sir, camera optics online.")
            } catch (e: Exception) {
                CommandResult.Handled("Yes Sir, unable to initialize camera.")
            }
        }

        // 13. PHONE DIALER
        if (query.contains("open dialer") || query.contains("open phone") || query.contains("call")) {
            val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(dialIntent)
            return CommandResult.Handled("Yes Sir, phone dialer engaged.")
        }

        // 14. PRIVATE SENTINEL (WhatsApp/SMS)
        if (query.contains("message") || query.contains("who texted") || query.contains("read message")) {
            val brief = NotificationSentinelService.getLatestNotificationBrief()
            return CommandResult.Handled("Yes Sir, $brief")
        }

        // 15. UNIVERSAL APP LAUNCHER (Any installed app)
        if (query.startsWith("open ") || query.startsWith("launch ")) {
            val targetName = query.removePrefix("open ").removePrefix("launch ").trim()
            val launched = launchApplication(context, targetName)
            return if (launched) {
                CommandResult.Handled("Yes Sir, opening $targetName right away.")
            } else {
                CommandResult.Handled("Yes Sir, $targetName is not installed on this device.")
            }
        }

        // Pass to AI with conversation memory
        return CommandResult.PassToAI(rawQuery)
    }

    private fun launchApplication(context: Context, appName: String): Boolean {
        val pm = context.packageManager
        val knownPackage = POPULAR_APPS[appName]
        if (knownPackage != null) {
            val intent = pm.getLaunchIntentForPackage(knownPackage)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                context.startActivity(intent)
                return true
            }
        }

        try {
            val packages = pm.getInstalledApplications(0)
            for (app in packages) {
                val label = pm.getApplicationLabel(app).toString().lowercase(Locale.ROOT)
                if (label == appName || label.contains(appName)) {
                    val launchIntent = pm.getLaunchIntentForPackage(app.packageName)
                    if (launchIntent != null) {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                        context.startActivity(launchIntent)
                        return true
                    }
                }
            }
        } catch (_: Exception) {}

        return false
    }

    private fun toggleTorch(context: Context, turnOn: Boolean): String {
        return try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList[0]
            cameraManager.setTorchMode(cameraId, turnOn)
            if (turnOn) "Yes Sir, flashlight initialized." else "Yes Sir, flashlight disengaged."
        } catch (e: Exception) {
            "Yes Sir, flashlight module is unreachable."
        }
    }

    private fun adjustVolume(context: Context, direction: Int): String {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
        return "Yes Sir, audio levels adjusted."
    }

    private fun setVolumeMute(context: Context): String {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, AudioManager.FLAG_SHOW_UI)
        return "Yes Sir, audio output muted."
    }

    private fun setMaxVolume(context: Context): String {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, max, AudioManager.FLAG_SHOW_UI)
        return "Yes Sir, audio output set to maximum capacity."
    }

    private fun getBatteryPercentage(context: Context): Int {
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (level >= 0 && scale > 0) (level * 100) / scale else 0
    }

    private fun launchSystemIntent(context: Context, action: String) {
        val intent = Intent(action).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
        }
        context.startActivity(intent)
    }
}
