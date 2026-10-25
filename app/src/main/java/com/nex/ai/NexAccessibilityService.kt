package com.nex.ai

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.view.accessibility.AccessibilityEvent

class NexAccessibilityService : AccessibilityService() {

    companion object {
        var instance: NexAccessibilityService? = null
        val isServiceConnected: Boolean
            get() = instance != null

        // 1. System Navigation
        fun goBack(): Boolean = instance?.performGlobalAction(GLOBAL_ACTION_BACK) ?: false
        fun goHome(): Boolean = instance?.performGlobalAction(GLOBAL_ACTION_HOME) ?: false
        fun openRecents(): Boolean = instance?.performGlobalAction(GLOBAL_ACTION_RECENTS) ?: false
        fun openNotifications(): Boolean = instance?.performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS) ?: false
        fun openQuickSettings(): Boolean = instance?.performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS) ?: false

        // 2. Lock Screen
        fun lockPhone(): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                instance?.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN) ?: false
            } else false
        }

        // 3. Take Screenshot
        fun captureScreenshot(): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                instance?.performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT) ?: false
            } else false
        }

        // 4. Auto-Scroll Down (Next Reel / Next Short)
        fun scrollDown(): Boolean {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && instance != null) {
                val displayMetrics = instance!!.resources.displayMetrics
                val width = displayMetrics.widthPixels
                val height = displayMetrics.heightPixels

                val path = Path().apply {
                    moveTo(width / 2f, height * 0.75f)
                    lineTo(width / 2f, height * 0.22f)
                }
                val gesture = GestureDescription.Builder()
                    .addStroke(GestureDescription.StrokeDescription(path, 0, 250))
                    .build()
                return instance!!.dispatchGesture(gesture, null, null)
            }
            return false
        }

        // 5. Auto-Scroll Up (Previous Reel)
        fun scrollUp(): Boolean {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && instance != null) {
                val displayMetrics = instance!!.resources.displayMetrics
                val width = displayMetrics.widthPixels
                val height = displayMetrics.heightPixels

                val path = Path().apply {
                    moveTo(width / 2f, height * 0.25f)
                    lineTo(width / 2f, height * 0.75f)
                }
                val gesture = GestureDescription.Builder()
                    .addStroke(GestureDescription.StrokeDescription(path, 0, 250))
                    .build()
                return instance!!.dispatchGesture(gesture, null, null)
            }
            return false
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }
}
