package com.nex.ai

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.hardware.camera2.CameraCharacteristics
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

    fun processVoiceCommand(context: Context, rawQuery: String): CommandResult {
        val cleanFullQuery = rawQuery.lowercase(Locale.ROOT)
            .replace(Regex("[^a-zA-Z0-9 ]"), "")
            .trim()

        // 1. WAKE-WORD / CALLING NEX ALONE
        val wakeKeywords = listOf("nex", "next", "necks", "nyx", "hey nex", "hello nex", "hi nex", "ok nex", "nex suno", "suno nex")
        if (wakeKeywords.any { cleanFullQuery == it }) {
            return CommandResult.Handled("Yes Sir, standing by. What are your orders?")
        }

        // Who are you / Identity
        if (cleanFullQuery.contains("who are you") || cleanFullQuery.contains("what is your name") || cleanFullQuery.contains("tum kaun ho")) {
            return CommandResult.Handled("Yes Sir, I am Nex, your personal artificial intelligence.")
        }

        // Strip "Nex" prefix from commands like "Nex torch on karo"
        var query = cleanFullQuery
        val callPrefixes = listOf("hey nex ", "hello nex ", "hi nex ", "ok nex ", "nex ", "next ")
        for (prefix in callPrefixes) {
            if (query.startsWith(prefix)) {
                query = query.removePrefix(prefix).trim()
                break
            }
        }

        // 2. FLASHLIGHT / TORCH (Bilingual: Hindi & English)
        if (query.contains("torch") || query.contains("flashlight") || query.contains("flash") || query.contains("light")) {
            val isOff = query.contains("off") || query.contains("band") || query.contains("bujhao") || query.contains("close") || query.contains("stop")
            val isOn = query.contains("on") || query.contains("chalu") || query.contains("jalao") || query.contains("start") || query.contains("open")
            if (isOff) {
                return CommandResult.Handled(toggleTorch(context, false))
            } else if (isOn) {
                return CommandResult.Handled(toggleTorch(context, true))
            }
        }

        // 3. INSTAGRAM REELS (Deep In-App Link)
        if (query.contains("reel") || query.contains("reels")) {
            val uri = Uri.parse("https://www.instagram.com/reels/")
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage("com.instagram.android")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
            }
            return try {
                context.startActivity(intent)
                CommandResult.Handled("Yes Sir, launching Instagram Reels right away.")
            } catch (e: Exception) {
                val fallbackIntent = Intent(Intent.ACTION_VIEW, uri).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                context.startActivity(fallbackIntent)
                CommandResult.Handled("Yes Sir, opening Instagram Reels.")
            }
        }

        // 4. INSTAGRAM DMS / MESSAGES
        if (query.contains("insta message") || query.contains("instagram dm") || query.contains("instagram message")) {
            val uri = Uri.parse("https://www.instagram.com/direct/inbox/")
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage("com.instagram.android")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            return try {
                context.startActivity(intent)
                CommandResult.Handled("Yes Sir, opening your Instagram messages.")
            } catch (e: Exception) {
                CommandResult.Handled("Yes Sir, unable to open Instagram inbox.")
            }
        }

        // 5. YOUTUBE SHORTS
        if (query.contains("short") || query.contains("shorts")) {
            val uri = Uri.parse("https://www.youtube.com/shorts")
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage("com.google.android.youtube")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            return try {
                context.startActivity(intent)
                CommandResult.Handled("Yes Sir, launching YouTube Shorts.")
            } catch (e: Exception) {
                CommandResult.Handled("Yes Sir, YouTube is not accessible.")
            }
        }

        // 6. PLAY MUSIC / SEARCH ON YOUTUBE
        if (query.contains("play ") || query.contains("on youtube") || query.contains("search youtube") || query.contains("gana bajao") || query.contains("gana chalao")) {
            val topic = query.replace("play", "")
                .replace("on youtube", "")
                .replace("search youtube", "")
                .replace("gana bajao", "")
                .replace("gana chalao", "")
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

        // 7. VOLUME CONTROLS (Bilingual)
        if (query.contains("volume") || query.contains("awaz") || query.contains("sound")) {
            if (query.contains("up") || query.contains("increase") || query.contains("badhao") || query.contains("tez") || query.contains("louder")) {
                return CommandResult.Handled(adjustVolume(context, AudioManager.ADJUST_RAISE))
            }
            if (query.contains("down") || query.contains("decrease") || query.contains("kam") || query.contains("ghatao") || query.contains("lower")) {
                return CommandResult.Handled(adjustVolume(context, AudioManager.ADJUST_LOWER))
            }
            if (query.contains("mute") || query.contains("silent") || query.contains("chup") || query.contains("shant")) {
                return CommandResult.Handled(setVolumeMute(context))
            }
            if (query.contains("max") || query.contains("full") || query.contains("pura") || query.contains("maximum")) {
                return CommandResult.Handled(setMaxVolume(context))
            }
        }

        // 8. BATTERY STATUS (Bilingual)
        if (query.contains("battery") || query.contains("charge") || query.contains("charging")) {
            val batteryLevel = getBatteryPercentage(context)
            return CommandResult.Handled("Yes Sir, current power level is at $batteryLevel percent.")
        }

        // 9. TIME CHECK (Bilingual)
        if (query.contains("time") || query.contains("kitne baje") || query.contains("samay") || query.contains("clock")) {
            val currentTime = SimpleDateFormat("hh:mm a", Locale.UK).format(Date())
            return CommandResult.Handled("Yes Sir, it is currently $currentTime.")
        }

        // 10. SYSTEM SETTINGS
        if (query.contains("open wifi") || query.contains("wifi settings") || query.contains("wifi kholo")) {
            launchSystemIntent(context, Settings.ACTION_WIFI_SETTINGS)
            return CommandResult.Handled("Yes Sir, opening Wi-Fi settings.")
        }
        if (query.contains("open bluetooth") || query.contains("bluetooth settings") || query.contains("bluetooth kholo")) {
            launchSystemIntent(context, Settings.ACTION_BLUETOOTH_SETTINGS)
            return CommandResult.Handled("Yes Sir, opening Bluetooth settings.")
        }
        if (query.contains("open settings") || query.contains("system settings") || query.contains("settings kholo")) {
            launchSystemIntent(context, Settings.ACTION_SETTINGS)
            return CommandResult.Handled("Yes Sir, opening master settings.")
        }
        if (query.contains("go home") || query.contains("home screen") || query.contains("bahar aao")) {
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
            }
            context.startActivity(homeIntent)
            return CommandResult.Handled("Yes Sir, navigating to home screen.")
        }

        // 11. CAMERA OPTICS
        if (query.contains("camera") || query.contains("photo") || query.contains("selfie") || query.contains("picture")) {
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

        // 12. PHONE DIALER
        if (query.contains("open dialer") || query.contains("phone kholo") || query.contains("call lagao") || query.contains("call")) {
            val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(dialIntent)
            return CommandResult.Handled("Yes Sir, phone dialer engaged.")
        }

        // 13. PRIVATE SENTINEL (WhatsApp / SMS)
        if (query.contains("message") || query.contains("who texted") || query.contains("kiska message") || query.contains("message padho")) {
            val brief = NotificationSentinelService.getLatestNotificationBrief()
            return CommandResult.Handled("Yes Sir, $brief")
        }

        // 14. UNIVERSAL APP LAUNCHER (Bilingual: "open...", "launch...", "...kholo", "...chalao")
        var targetApp: String? = null
        if (query.startsWith("open ") || query.startsWith("launch ")) {
            targetApp = query.removePrefix("open ").removePrefix("launch ").trim()
        } else if (query.endsWith(" kholo") || query.endsWith(" chalao")) {
            targetApp = query.removeSuffix(" kholo").removeSuffix(" chalao").trim()
        } else if (query.contains(" kholo") || query.contains(" open")) {
            targetApp = query.replace("kholo", "").replace("open", "").replace("app", "").trim()
        }

        if (!targetApp.isNullOrBlank()) {
            val launched = launchApplication(context, targetApp)
            if (launched) {
                return CommandResult.Handled("Yes Sir, opening $targetApp right away.")
            }
        }

        // Direct check for popular apps like "instagram", "whatsapp", "youtube", "spotify"
        for ((key, _) in POPULAR_APPS) {
            if (query == key || query.contains(key)) {
                val launched = launchApplication(context, key)
                if (launched) {
                    return CommandResult.Handled("Yes Sir, opening $key now.")
                }
            }
        }

        // If not a hardware action, pass to ChatGPT-class AI Engine
        return CommandResult.PassToAI(rawQuery)
    }

    private fun launchApplication(context: Context, appName: String): Boolean {
        val pm = context.packageManager
        val cleanName = appName.lowercase().trim()

        // 1. Direct Package Map Check
        val knownPackage = POPULAR_APPS[cleanName]
        if (knownPackage != null) {
            val intent = pm.getLaunchIntentForPackage(knownPackage)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                context.startActivity(intent)
                return true
            }
        }

        // 2. Scan installed applications
        try {
            val packages = pm.getInstalledApplications(0)
            for (app in packages) {
                val label = pm.getApplicationLabel(app).toString().lowercase(Locale.ROOT)
                if (label == cleanName || label.contains(cleanName) || cleanName.contains(label)) {
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
            var targetCameraId: String? = null

            // Detect back-facing camera with flash hardware
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
