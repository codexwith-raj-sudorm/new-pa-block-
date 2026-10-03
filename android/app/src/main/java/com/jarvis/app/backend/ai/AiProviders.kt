package com.jarvis.app.backend.ai

import java.io.IOException
import java.net.URI
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

// AI provider ids + OpenAI-compatible request helpers. JVM-pure (no Android imports)
// so unit tests can cover them. Covers OpenAI, Groq, xAI, DeepSeek, Ollama…

const val AI_GEMINI = "gemini"
const val AI_OPENAI = "openai"
const val OPENAI_DEFAULT_BASE = "https://api.openai.com/v1"
const val OPENAI_DEFAULT_MODEL = "gpt-4o-mini"

/** Normalizes a base URL into a /chat/completions endpoint. Pure. */
fun openAiEndpoint(base: String): String {
    val root = base.trim().trimEnd('/').ifEmpty { OPENAI_DEFAULT_BASE }
    return if (root.endsWith("/chat/completions")) root else "$root/chat/completions"
}

/** True for loopback, link-local, RFC1918, and obvious localhost names. Pure. */
fun isLocalOrPrivateHost(host: String): Boolean {
    val h = host.trim().trim('[', ']').lowercase()
    if (h.isBlank()) return true
    if (h == "localhost" || h.endsWith(".localhost") || h.endsWith(".local")) return true
    if (h == "::1" || h.startsWith("fe80:") || h.startsWith("fc") || h.startsWith("fd")) return true
    val parts = h.split('.')
    val nums = parts.map { it.toIntOrNull() }
    if (nums.size == 4 && nums.all { (it ?: -1) in 0..255 }) {
        val a = nums[0] ?: return false
        val b = nums[1] ?: return false
        return a == 0 || a == 10 || a == 127 ||
            (a == 169 && b == 254) ||
            (a == 172 && b in 16..31) ||
            (a == 192 && b == 168)
    }
    return false
}

/**
 * Validates an OpenAI-compatible base URL before it is persisted. Public builds
 * should pass allowLocal=false to block localhost/LAN SSRF-style endpoints.
 * Returns null when accepted, otherwise a user-facing error. Pure.
 */
fun openAiBaseValidationError(base: String, allowLocal: Boolean = false): String? {
    val raw = base.trim()
    if (raw.isBlank()) return null
    val uri = try { URI(raw) } catch (_: Exception) { return "Other-AI endpoint is not a valid URL." }
    val scheme = uri.scheme?.lowercase().orEmpty()
    val host = uri.host.orEmpty()
    if (host.isBlank()) return "Other-AI endpoint must include a host."
    if (scheme == "https") {
        // ok
    } else if (allowLocal && scheme == "http") {
        // Debug/private builds may point to local Ollama-compatible services.
    } else {
        return "Other-AI endpoint must use https://."
    }
    if (!allowLocal && isLocalOrPrivateHost(host)) {
        return "Local/private Other-AI endpoints are blocked in release builds."
    }
    return null
}

/**
 * Synchronous OkHttp execution that is tied to coroutine cancellation.
 * Cancelling the coroutine calls Call.cancel(), which severs the socket instead
 * of waiting for execute() to return naturally. The caller owns and must close
 * the returned [Response] (normally via use { ... }).
 */
suspend fun OkHttpClient.executeCancellable(
    request: Request,
    onCallCreated: ((Call) -> Unit)? = null
): Response = suspendCancellableCoroutine { continuation ->
    val call = newCall(request)
    onCallCreated?.invoke(call)
    continuation.invokeOnCancellation { call.cancel() }
    try {
        val response = call.execute()
        if (continuation.isActive) {
            continuation.resume(response)
        } else {
            response.close()
        }
    } catch (e: IOException) {
        if (continuation.isActive) {
            if (call.isCanceled()) {
                val canceled = CancellationException("HTTP call canceled")
                canceled.initCause(e)
                continuation.resumeWithException(canceled)
            } else continuation.resumeWithException(e)
        }
    }
}

/** Minimal JSON string escaper. Pure. */
fun jsonEscape(s: String): String {
    val out = StringBuilder(s.length + 8)
    for (c in s) {
        when (c) {
            '"' -> out.append("\\\"")
            '\\' -> out.append("\\\\")
            '\n' -> out.append("\\n")
            '\r' -> out.append("\\r")
            '\t' -> out.append("\\t")
            else -> if (c < ' ') out.append(String.format("\\u%04x", c.code)) else out.append(c)
        }
    }
    return out.toString()
}

/**
 * OpenAI-compatible chat body: system prompt + mapped history + user turn.
 * History roles are Gemini-style ("user"/"model") — "model" maps to "assistant".
 * Pure.
 */
fun openAiChatBody(
    model: String,
    system: String,
    history: List<Pair<String, String>>,
    user: String
): String {
    val sb = StringBuilder()
    sb.append("{\"model\":\"").append(jsonEscape(model)).append("\",\"messages\":[")
    sb.append("{\"role\":\"system\",\"content\":\"").append(jsonEscape(system)).append("\"}")
    for ((r, t) in history.takeLast(20)) {
        val role = if (r == "user") "user" else "assistant"
        sb.append(",{\"role\":\"").append(role).append("\",\"content\":\"")
            .append(jsonEscape(t)).append("\"}")
    }
    sb.append(",{\"role\":\"user\",\"content\":\"").append(jsonEscape(user)).append("\"}")
    sb.append("],\"max_tokens\":1024}")
    return sb.toString()
}
