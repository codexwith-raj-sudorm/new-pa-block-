package com.jarvis.app

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/** True when the user enabled Jarvis in system Accessibility settings. */
fun isAccessEnabled(ctx: Context): Boolean {
    return try {
        val flat = Settings.Secure.getString(
            ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()
        flat.contains(ctx.packageName + "/.JarvisAccess") ||
            flat.contains(ctx.packageName + "/com.jarvis.app.JarvisAccess")
    } catch (_: Exception) {
        false
    }
}

/** Pick a tap target from visible texts. Pure, tested. */
data class TapPick(val index: Int?, val options: List<String>)

fun tapPick(texts: List<String>, query: String): TapPick {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return TapPick(null, emptyList())
    texts.forEachIndexed { i, t ->
        if (t.lowercase() == q) return TapPick(i, emptyList())
    }
    val hits = texts.mapIndexedNotNull { i, t -> if (q in t.lowercase()) i else null }.distinct()
    if (hits.size == 1) return TapPick(hits[0], emptyList())
    return TapPick(null, hits.take(3).map { texts[it] })
}

/** System-bound service: exposes the screen to [AccessBridge]. */
class JarvisAccessService : AccessibilityService() {
    override fun onServiceConnected() {
        AccessBridge.bound = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        AccessBridge.bound = null
        return super.onUnbind(intent)
    }
}

/** Screen read + control. Every call returns null when the service isn't bound. */
object AccessBridge {
    var bound: JarvisAccessService? = null

    fun read(): String? {
        val root = try {
            bound?.rootInActiveWindow
        } catch (_: Exception) {
            null
        } ?: return null
        val out = mutableListOf<String>()
        fun walk(n: AccessibilityNodeInfo) {
            val t = n.text?.toString()?.trim().orEmpty()
            if (t.length > 1 && out.size < 60 && t !in out) out.add(t)
            for (i in 0 until n.childCount) {
                try {
                    n.getChild(i)?.let { walk(it) }
                } catch (_: Exception) {
                }
            }
        }
        try {
            walk(root)
        } catch (_: Exception) {
        }
        val s = out.joinToString("\n").take(1500)
        return s.ifBlank { "(blank screen — nothing readable)" }
    }

    fun tap(query: String): String? {
        val svc = bound ?: return null
        val root = try {
            svc.rootInActiveWindow
        } catch (_: Exception) {
            null
        } ?: return "I can't see the screen right now."
        val texts = mutableListOf<String>()
        val nodes = mutableListOf<AccessibilityNodeInfo>()
        fun walk(n: AccessibilityNodeInfo) {
            val t = n.text?.toString()?.trim().orEmpty()
            if (t.length > 1 && texts.size < 200) {
                texts.add(t)
                nodes.add(n)
            }
            for (i in 0 until n.childCount) {
                try {
                    n.getChild(i)?.let { walk(it) }
                } catch (_: Exception) {
                }
            }
        }
        try {
            walk(root)
        } catch (_: Exception) {
        }
        val pick = tapPick(texts, query)
        if (pick.index == null) {
            return if (pick.options.isEmpty()) "I don't see \"$query\" on screen."
            else "I see matches: " + pick.options.joinToString(" / ") + " — be more specific."
        }
        var n: AccessibilityNodeInfo? = nodes[pick.index]
        var guard = 0
        while (n != null && !n.isClickable && guard++ < 8) n = n.parent
        return if (n?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) "Tapped \"$query\"."
        else "Found \"$query\" but couldn't tap it."
    }

    fun scroll(down: Boolean): String? {
        val svc = bound ?: return null
        val root = try {
            svc.rootInActiveWindow
        } catch (_: Exception) {
            null
        } ?: return "I can't see the screen right now."
        val action = if (down) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        if (root.performAction(action)) return if (down) "Scrolled down." else "Scrolled up."
        var done = false
        fun walk(n: AccessibilityNodeInfo) {
            if (done) return
            if (n.isScrollable && n.performAction(action)) {
                done = true
                return
            }
            for (i in 0 until n.childCount) {
                try {
                    n.getChild(i)?.let { walk(it) }
                } catch (_: Exception) {
                }
            }
        }
        try {
            walk(root)
        } catch (_: Exception) {
        }
        return if (done) (if (down) "Scrolled down." else "Scrolled up.")
        else "Nothing scrollable here."
    }

    fun back(): String? {
        val svc = bound ?: return null
        return if (svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)) "Went back."
        else "Couldn't go back."
    }

    /** Open the task switcher (recent apps). False when unavailable. */
    fun recents(): Boolean {
        val svc = bound ?: return false
        return try {
            svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
        } catch (_: Exception) {
            false
        }
    }

    /** Tap the first node whose content-description matches (used to hit WhatsApp's Send). */
    fun tapDesc(desc: String): Boolean {
        val svc = bound ?: return false
        val root = try {
            svc.rootInActiveWindow
        } catch (_: Exception) {
            null
        } ?: return false
        var target: AccessibilityNodeInfo? = null
        fun walk(n: AccessibilityNodeInfo) {
            if (target != null) return
            if (desc.equals(n.contentDescription?.toString(), ignoreCase = true)) {
                target = n
                return
            }
            for (i in 0 until n.childCount) {
                try {
                    n.getChild(i)?.let { walk(it) }
                } catch (_: Exception) {
                }
            }
        }
        try {
            walk(root)
        } catch (_: Exception) {
        }
        var n = target
        var guard = 0
        while (n != null && !n.isClickable && guard++ < 8) n = n.parent
        return n?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
    }
}
