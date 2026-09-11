package com.jarvis.app

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

data class NotifItem(val pkg: String, val title: String, val text: String, val time: Long)

/** Notification listener: keeps the latest user notifications in memory. */
class NotifReader : NotificationListenerService() {
    companion object {
        private const val MAX = 20
        private val items = mutableListOf<NotifItem>()

        @Synchronized
        fun push(n: NotifItem) {
            items.removeAll { it.pkg == n.pkg && it.title == n.title && it.text == n.text }
            items.add(0, n)
            while (items.size > MAX) items.removeAt(items.size - 1)
        }

        @Synchronized
        fun snapshot(): List<NotifItem> = items.toList()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        try {
            if (sbn.packageName == packageName) return
            if (sbn.isOngoing) return
            val ex = sbn.notification.extras
            val title = (ex.getCharSequence("android.title") ?: "").toString().trim()
            val text = (ex.getCharSequence("android.text") ?: "").toString().trim()
            if (title.isEmpty() && text.isEmpty()) return
            push(NotifItem(sbn.packageName, title.take(120), text.take(200), sbn.postTime))
        } catch (_: Exception) {
        }
    }
}

/** "2 notifications: Whatsapp — ...". Pure, tested. */
fun formatNotifs(list: List<NotifItem>): String {
    if (list.isEmpty()) return "No notifications right now. All quiet."
    val sb = StringBuilder()
    sb.append(if (list.size == 1) "1 notification: " else "${list.size} notifications: ")
    for ((i, n) in list.take(5).withIndex()) {
        if (i > 0) sb.append(" | ")
        val app = n.pkg.substringAfterLast(".").replaceFirstChar { it.uppercase() }
        val body = listOf(n.title, n.text).filter { it.isNotBlank() }.joinToString(" — ")
        sb.append("$app — $body")
    }
    return sb.toString().take(900)
}
