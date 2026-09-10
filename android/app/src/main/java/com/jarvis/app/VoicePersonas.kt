package com.jarvis.app

/**
 * Curated voice cast: 7 named personalities instead of the engine's raw list
 * (where most entries sound identical).
 *
 * Jarvis Ringwal + 3 male + 3 female. Each persona = a display name, a personality
 * (speech rate + pitch), and matching rules that resolve it to a DISTINCT installed
 * engine voice. "Jarvis Ringwal" resolves to a British-English male voice with a calm,
 * low delivery — the closest a phone TTS gets to the MCU butler.
 *
 * This file is 100% JVM-pure (no android imports) so the matching logic is
 * unit-tested (see PersonaTest). The ViewModel and WakeService each hold a tiny
 * adapter converting android.speech.tts.Voice <-> EngineVoiceInfo.
 */
enum class PersonaGender { MALE, FEMALE }

data class VoicePersona(
    val key: String,
    val name: String,
    val tagline: String,
    val gender: PersonaGender,
    /** Preferred voice region; null = device region. Jarvis Ringwal wants GB. */
    val preferCountry: String?,
    val rate: Float,
    val pitch: Float
)

val VOICE_PERSONAS = listOf(
    VoicePersona("jarvis", "Jarvis Ringwal", "British butler — calm & loyal", PersonaGender.MALE, "GB", 0.95f, 0.88f),
    VoicePersona("arjun", "Arjun", "Deep & commanding", PersonaGender.MALE, null, 0.92f, 0.82f),
    VoicePersona("kabir", "Kabir", "Warm & friendly", PersonaGender.MALE, null, 1.0f, 1.0f),
    VoicePersona("dev", "Dev", "Young & sharp", PersonaGender.MALE, null, 1.12f, 1.08f),
    VoicePersona("priya", "Priya", "Warm & calm", PersonaGender.FEMALE, null, 0.94f, 1.0f),
    VoicePersona("ananya", "Ananya", "Elegant & clear", PersonaGender.FEMALE, null, 1.0f, 1.05f),
    VoicePersona("meera", "Meera", "Bright & cheerful", PersonaGender.FEMALE, null, 1.1f, 1.15f)
)

/** Heal legacy engine-voice names (or garbage) to a valid persona key. */
fun personaKeyOrDefault(key: String?): String =
    if (key != null && VOICE_PERSONAS.any { it.key == key }) key else "jarvis"

fun personaForKey(key: String?): VoicePersona =
    VOICE_PERSONAS.firstOrNull { it.key == key } ?: VOICE_PERSONAS[0]

/** Platform-free snapshot of an engine voice for matching. */
data class EngineVoiceInfo(
    val name: String,
    val language: String,
    val country: String,
    val networkRequired: Boolean
)

private val FEMALE_HINTS = listOf(
    "female", "woman", "feminine", "girl", "samantha", "siri", "zira",
    "priya", "neha", "aria", "jenny", "emma", "olivia", "sophia",
    "smtf", "+f", "_female", "-female"
)
private val MALE_HINTS = listOf(
    "male", "man", "masculine", "boy", "daniel", "david", "alex", "fred",
    "george", "rahul", "arjun", "matthew", "michael", "william", "james",
    "robert", "amit", "vijay", "kumar", "smtm", "+m", "_male", "-male"
)

/** Gender guess from the engine voice name. Female checked first ("woman" contains "man"). */
fun genderOfVoice(name: String): PersonaGender? {
    val n = name.lowercase()
    if (FEMALE_HINTS.any { n.contains(it) }) return PersonaGender.FEMALE
    if (MALE_HINTS.any { n.contains(it) }) return PersonaGender.MALE
    return null
}

fun scoreVoice(info: EngineVoiceInfo, persona: VoicePersona, deviceLang: String, deviceCountry: String): Int {
    var s = 0
    if (info.language.equals(deviceLang, ignoreCase = true)) s += 4
    val wantCountry = persona.preferCountry ?: deviceCountry
    if (wantCountry.isNotEmpty() && info.country.equals(wantCountry, ignoreCase = true)) s += 4
    else if (wantCountry.isNotEmpty() && info.country.isNotEmpty()) s -= 1
    if (!info.networkRequired) s += 3
    when (genderOfVoice(info.name)) {
        persona.gender -> s += 10
        null -> { }
        else -> s -= 50
    }
    return s
}

/**
 * Assign each persona a DISTINCT engine voice (personas in list order, Jarvis Ringwal first).
 * Never crosses genders while any same/unknown-gender voice remains; falls back to reuse
 * (still rate/pitch-differentiated) when the engine has fewer voices than personas.
 */
fun resolvePersonaVoices(
    infos: List<EngineVoiceInfo>,
    deviceLang: String,
    deviceCountry: String
): Map<String, EngineVoiceInfo?> {
    fun ranked(pool: List<EngineVoiceInfo>, persona: VoicePersona, strict: Boolean) =
        pool.map { it to scoreVoice(it, persona, deviceLang, deviceCountry) }
            .filter { !strict || it.second > -40 }
            .sortedWith(compareByDescending<Pair<EngineVoiceInfo, Int>> { it.second }.thenBy { it.first.name })
    val remaining = infos.toMutableList()
    val out = mutableMapOf<String, EngineVoiceInfo?>()
    for (persona in VOICE_PERSONAS) {
        val pick = ranked(remaining, persona, strict = true).firstOrNull()?.first
            ?: ranked(infos, persona, strict = false).firstOrNull()?.first
        out[persona.key] = pick
        remaining.remove(pick)
    }
    return out
}
