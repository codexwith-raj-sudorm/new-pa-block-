package com.jarvis.app

/**
 * Baked master identity: the one true master, recognized on every device
 * without typing details. Entering [BAKED_MASTER_KEY] in Settings installs
 * master mode with this identity on any install.
 *
 * NOTE: this ships inside the APK/source — anyone holding it can read the
 * key. It is an identity switch, not a security boundary. Keep the APK
 * private, like the built-in Gemini key.
 */
const val BAKED_MASTER_KEY = "JARVIS-RAJ-MASTER-77"
const val BAKED_MASTER_NAME = "Raj Thakur"
const val BAKED_MASTER_ABOUT = "Raj Thakur from West Bengal, India is the creator " +
    "of Jarvis and its one true Master. He is building Jarvis as his dream " +
    "personal AI assistant."

/** Personalized master welcome greeting (pure, tested). */
fun masterGreet(name: String): String =
    "Welcome back, Master " + firstName(name) + ". I am Jarvis, ready to serve."

/** First word of a full name ("Raj Thakur" -> "Raj"). Pure, tested. */
fun firstName(full: String): String =
    full.trim().split(Regex("\\s+")).firstOrNull().orEmpty()

/** "Good morning, Master Raj." Pure, tested. */
fun wakeGreet(hour: Int, fullName: String): String {
    val g = when (hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        else -> "Good evening"
    }
    val first = firstName(fullName)
    return if (first.isEmpty()) "$g, Master." else "$g, Master $first."
}

/** Day-part bucket for once-per-part wake greetings. Pure, tested. */
fun wakeBucket(hour: Int): String = when (hour) {
    in 5..11 -> "morning"
    in 12..16 -> "afternoon"
    else -> "evening"
}

/** "2026-09-12-morning" stamp: greeting plays once per stamp. Pure, tested. */
fun wakeGreetStamp(date: String, bucket: String): String = "$date-$bucket"
