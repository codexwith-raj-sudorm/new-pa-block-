package com.jarvis.app.ui.components

// JVM-pure Cyber Mode strings (no imports) so unit tests can cover them.

/** Shell prompt for Cyber Mode, e.g. raj@jarvis:~$ — pure. */
fun cyberPrompt(user: String): String {
    val u = user.trim().ifEmpty { "master" }
    return "$u@jarvis:~\$"
}

/** Login banner shown when Cyber Mode is on and the chat is empty. Pure. */
fun cyberBootLines(version: String = "v5.9"): List<String> = listOf(
    "JARVIS SECURE SHELL -- $version",
    "Encrypted channel established. All systems nominal.",
    "Type below to issue a command."
)
