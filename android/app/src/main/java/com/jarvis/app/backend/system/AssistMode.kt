package com.jarvis.app.backend.system

import android.content.Context
import android.content.Intent
import android.provider.Settings

/** Assist entry the system launches on the hold-gesture when Jarvis is the default assistant. */
const val ASSIST_ACTIVITY = "com.jarvis.app.frontend.screens.AssistActivity"

/**
 * Package of the flattened default-assistant component ("pkg/cls").
 * Pure, tested.
 */
fun assistantPkgOf(flat: String?): String? {
    val f = flat?.trim().orEmpty()
    if (f.isEmpty()) return null
    val core = f.substringAfterLast("{", f).substringBefore("}", f)
    return core.substringBefore("/").trim().ifBlank { null }
}

/** True when Jarvis is the device's default assistant app. */
fun isJarvisDefaultAssistant(ctx: Context): Boolean = try {
    assistantPkgOf(Settings.Secure.getString(ctx.contentResolver, "assistant")) == ctx.packageName
} catch (_: Exception) {
    false
}

/** Current default-assistant package, if any. */
fun defaultAssistantPkg(ctx: Context): String? = try {
    assistantPkgOf(Settings.Secure.getString(ctx.contentResolver, "assistant"))
} catch (_: Exception) {
    null
}

/** System screen where the user picks the default assistant app. */
fun defaultAssistantSettingsIntent(): Intent =
    Intent(Settings.ACTION_VOICE_INPUT_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

/** Settle delay so our own window is gone before a screen capture (no selfies). Pure, tested. */
fun assistCaptureSettleMs(): Long = 800L
