package com.jarvis.app

// AI provider ids + OpenAI-compatible request helpers. JVM-pure (no imports)
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
