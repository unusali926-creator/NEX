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

    fun launchIntentSafely(context: Context, intent: Intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // Android 14
                val options = ActivityOptions.makeBasic()
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

        // 1. FAST PATH: Check Offline Accessibility & Hardware Commands
        val fastResponse = checkFastOfflineCommands(context, cleanFullQuery)
        if (fastResponse != null) {
            return CommandResult.Handled(fastResponse)
        }

        // 2. AI AGENT PATH: Lets 70B LLaMA parse complex natural language & tags
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

        // 1. SCREENSHOT
        if (query.contains("screenshot") || query.contains("screen shot") || query.contains("स्क्रीनशॉट")) {
            return if (NexAccessibilityService.captureScreenshot()) {
                "Yes Sir, screenshot captured."
            } else {
                openAccessibilitySettings(context)
                "Sir, please enable Nex in Accessibility settings to allow screenshots."
            }
        }

        // 2. LOCK PHONE
        if (query.contains("lock phone") || query.contains("lock screen") || query.contains("phone lock") || query.contains("स्क्रीन लॉक") || query.contains("फोन लॉक")) {
            return if (NexAccessibilityService.lockPhone()) {
                "Yes Sir, locking phone."
            } else {
                openAccessibilitySettings(context)
                "Sir, please enable Nex in Accessibility settings to lock device."
            }
        }

        // 3. AUTO-SCROLL DOWN (Next Reel / Short)
        if (query.contains("next reel") || query.contains("agli reel") || query.contains("scroll down") || query.contains("neeche scroll") || query.contains("अगली रील") || query == "scroll") {
            NexAccessibilityService.scrollDown()
            return "Yes Sir, scrolling to next reel."
        }

        // 4. AUTO-SCROLL UP (Previous Reel)
        if (query.contains("previous reel") || query.contains("pichli reel") || query.contains("scroll up") || query.contains("upar scroll") || query.contains("पिछली रील")) {
            NexAccessibilityService.scrollUp()
            return "Yes Sir, scrolling up."
        }

        // 5. SYSTEM NAVIGATION (Back, Home, Recents, Notifications)
        if (query == "back" || query.contains("go back") || query.contains("peeche jao") || query.contains("पीछे जाओ")) {
            NexAccessibilityService.goBack()
            return "Yes Sir, going back."
        }
        if (query == "home" || query.contains("go home") || query.contains("home screen") || query.contains("होम")) {
            NexAccessibilityService.goHome()
            return "Yes Sir, navigating to home screen."
        }
        if (query.contains("recent apps") || query.contains("recents") || query.contains("रिसेंट")) {
            NexAccessibilityService.openRecents()
            return "Yes Sir, opening recent applications."
        }
        if (query.contains("notification") || query.contains("नोटिफिकेशन")) {
            NexAccessibilityService.openNotifications()
            return "Yes Sir, opening notification shade."
        }
        if (query.contains("control center") || query.contains("quick settings") || query.contains("कंट्रोल सेंटर")) {
            NexAccessibilityService.openQuickSettings()
            return "Yes Sir, opening quick settings."
        }

        // 6. TORCH
        if (query == "torch on" || query == "torch jalao" || query == "torch chalu karo" || query.contains("torch on")) {
            return toggleTorch(context, true)
        }
        if (query == "torch off" || query == "torch band karo" || query.contains("torch off")) {
            return toggleTorch(context, false)
        }

        // 7. INSTAGRAM REELS
        if (query == "show me reels" || query == "open reels" || query == "reels dikhao" || query == "reels") {
            launchReels(context)
            return "Yes Sir, launching Instagram Reels right away."
        }

        // 8. VOLUME
        if (query.contains("volume up") || query.contains("awaz badhao")) {
            return adjustVolume(context, AudioManager.ADJUST_RAISE)
        }
        if (query.contains("volume down") || query.contains("awaz kam karo")) {
            return adjustVolume(context, AudioManager.ADJUST_LOWER)
        }
        if (query == "mute" || query.contains("shant ho jao")) {
            return setVolumeMute(context)
        }

        // 9. BATTERY
        if (query == "battery" || query.contains("battery kitni hai")) {
            val level = getBatteryPercentage(context)
            return "Yes Sir, battery is at $level percent."
        }

        // 10. TIME
        if (query == "time" || query.contains("time kya hua hai") || query.contains("kitne baje")) {
            val time = SimpleDateFormat("hh:mm a", Locale.UK).format(Date())
            return "Yes Sir, it is currently $time."
        }

        return null
    }

    private fun parseAndExecuteAiAction(context: Context, aiResponse: String): String {
        var cleanText = aiResponse

        if (cleanText.contains("[EXEC:SCREENSHOT]")) {
            NexAccessibilityService.captureScreenshot()
            cleanText = cleanText.replace("[EXEC:SCREENSHOT]", "").trim()
        }
        if (cleanText.contains("[EXEC:LOCK_SCREEN]")) {
            NexAccessibilityService.lockPhone()
            cleanText = cleanText.replace("[EXEC:LOCK_SCREEN]", "").trim()
        }
        if (cleanText.contains("[EXEC:SCROLL_DOWN]")) {
            NexAccessibilityService.scrollDown()
            cleanText = cleanText.replace("[EXEC:SCROLL_DOWN]", "").trim()
        }
        if (cleanText.contains("[EXEC:SCROLL_UP]")) {
            NexAccessibilityService.scrollUp()
            cleanText = cleanText.replace("[EXEC:SCROLL_UP]", "").trim()
        }
        if (cleanText.contains("[EXEC:BACK]")) {
            NexAccessibilityService.goBack()
            cleanText = cleanText.replace("[EXEC:BACK]", "").trim()
        }
        if (cleanText.contains("[EXEC:HOME]")) {
            NexAccessibilityService.goHome()
            cleanText = cleanText.replace("[EXEC:HOME]", "").trim()
        }
        if (cleanText.contains("[EXEC:NOTIFICATIONS]")) {
            NexAccessibilityService.openNotifications()
            cleanText = cleanText.replace("[EXEC:NOTIFICATIONS]", "").trim()
        }
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

        return cleanText
    }

    private fun openAccessibilitySettings(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    fun launchReels(context: Context) {
        val uri = Uri.parse("https://www.instagram.com/reels/")
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage("com.instagram.android")
        }
        launchIntentSafely(context, intent)
    }

    fun playOnYouTube(context: Context, query: String) {
        val uri = Uri.parse("https://www
