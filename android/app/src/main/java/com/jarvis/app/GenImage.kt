package com.jarvis.app

import org.json.JSONArray
import org.json.JSONObject

/** Image-first model order: dedicated image models, then the user's chat models. Pure, tested. */
fun genImageModels(userModels: List<String>): List<String> =
    (listOf("gemini-2.5-flash-image", "gemini-2.0-flash-preview-image-generation") + userModels).distinct()

private val GEN_NOUNS = "image|picture|photo|photograph|drawing|painting|logo|wallpaper|poster|artwork|art"
private val GEN_IMAGE_RX =
    Regex("""^(?:please\s+)?(?:generate|create|design|make)\s+(?:me\s+)?(?:an?\s+|some\s+)?($GEN_NOUNS)(?:\s+of)?\s+(.+)$""")
private val DRAW_RX =
    Regex("""^(?:please\s+)?(?:draw|paint|sketch)\s+(?:me\s+)?(?:an?\s+|some\s+)?(?:($GEN_NOUNS)\s+(?:of\s+)?)?(.+)$""")

/**
 * Extract "noun subject" from an image command, or null. Pure, tested.
 * generate/create/design/make require an explicit media noun (so "make a call"
 * and "create a reminder" never match); draw/paint/sketch default to image.
 */
fun genImagePromptOf(low: String): String? {
    GEN_IMAGE_RX.find(low.trim())?.let {
        val subject = it.groupValues[2].trim().trimEnd('?', '.', '!').trim()
        if (subject.isEmpty()) return null
        return it.groupValues[1] + " " + subject
    }
    DRAW_RX.find(low.trim())?.let {
        val subject = it.groupValues[2].trim().trimEnd('?', '.', '!').trim()
        if (subject.isEmpty()) return null
        val noun = it.groupValues[1].ifEmpty { "image" }
        return noun + " " + subject
    }
    return null
}

/** generateContent body requesting TEXT+IMAGE. Pure, tested. */
fun genImageRequestBody(prompt: String): String =
    JSONObject()
        .put(
            "contents",
            JSONArray().put(
                JSONObject().put("role", "user").put(
                    "parts",
                    JSONArray().put(JSONObject().put("text", "Generate a detailed, high-quality $prompt."))
                )
            )
        )
        .put(
            "generationConfig",
            JSONObject().put("responseModalities", JSONArray().put("TEXT").put("IMAGE"))
        )
        .toString()

/** (mime, bytes) of the first inline image part, or null. Pure, tested. */
fun parseGenImageData(json: String): Pair<String, ByteArray>? {
    try {
        val parts = JSONObject(json).optJSONArray("candidates")?.optJSONObject(0)
            ?.optJSONObject("content")?.optJSONArray("parts") ?: return null
        for (i in 0 until parts.length()) {
            val inline = parts.optJSONObject(i)?.optJSONObject("inlineData") ?: continue
            val data = inline.optString("data", "")
            if (data.isEmpty()) continue
            val bytes = try {
                java.util.Base64.getDecoder().decode(data)
            } catch (_: Exception) {
                continue
            }
            if (bytes.isEmpty()) continue
            return inline.optString("mimeType", "image/png") to bytes
        }
    } catch (_: Exception) {
    }
    return null
}
