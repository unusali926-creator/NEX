package com.nex.ai

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class NotificationSentinelService : NotificationListenerService() {

    companion object {
        var lastSender: String? = null
        var lastMessage: String? = null
        var lastPackage: String? = null

        fun getLatestNotificationBrief(): String {
            return if (lastSender != null && lastMessage != null) {
                "Sir, $lastSender ka message aaya hai: $lastMessage"
            } else {
                "Sir, abhi koi naya message nahi hai."
            }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        sbn?.notification?.extras?.let { extras ->
            val packageName = sbn.packageName
            // Monitor WhatsApp and Default SMS apps
            if (packageName.contains("whatsapp") || packageName.contains("messaging") || packageName.contains("mms")) {
                val title = extras.getCharSequence("android.title")?.toString()
                val text = extras.getCharSequence("android.text")?.toString()

                if (!title.isNullOrBlank() && !text.isNullOrBlank()) {
                    lastSender = title
                    lastMessage = text
                    lastPackage = packageName
                }
            }
        }
    }
}
