package com.jarvis.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonaTest {
    private fun info(name: String, lang: String = "en", country: String = "US", net: Boolean = false) =
        EngineVoiceInfo(name, lang, country, net)

    private fun gendered8() = listOf(
        info("Voice Male 1"), info("Voice Male 2"), info("Voice Male 3"), info("Voice Male 4"),
        info("Voice Female 1"), info("Voice Female 2"), info("Voice Female 3"), info("Voice Female 4")
    )

    @Test fun jarvisPrefersBritishMale() {
        val infos = listOf(
            info("en-US-SMTf00", "en", "US"),
            info("en-US-SMTm00", "en", "US"),
            info("en-GB-SMTm00", "en", "GB")
        )
        val map = resolvePersonaVoices(infos, "en", "US")
        assertEquals("en-GB-SMTm00", map["jarvis"]?.name)
    }

    @Test fun gendersAreRespected() {
        val map = resolvePersonaVoices(gendered8(), "en", "US")
        for (k in listOf("jarvis", "arjun", "kabir", "dev")) {
            val n = map[k]?.name.orEmpty()
            assertTrue("$k got $n", n.contains("Male") && !n.contains("Female"))
        }
        for (k in listOf("priya", "ananya", "meera")) {
            assertTrue("$k got ${map[k]?.name}", map[k]?.name.orEmpty().contains("Female"))
        }
    }

    @Test fun assignmentIsDistinct() {
        val map = resolvePersonaVoices(gendered8(), "en", "US")
        assertEquals(7, map.values.map { it?.name }.toSet().size)
    }

    @Test fun unknownGenderVoicesStillAssign() {
        val infos = List(8) { i -> info("en-us-x-tpc-local-$i", "en", "US") }
        val map = resolvePersonaVoices(infos, "en", "US")
        assertTrue(map.values.all { it != null })
        assertEquals(7, map.values.map { it?.name }.toSet().size)
    }

    @Test fun legacyKeysHealToJarvis() {
        assertEquals("jarvis", personaKeyOrDefault("en-us-x-tpc-local"))
        assertEquals("jarvis", personaKeyOrDefault(null))
        assertEquals("priya", personaKeyOrDefault("priya"))
    }

    @Test fun deviceCountryPreferredForOthers() {
        val kabir = personaForKey("kabir")
        val inVoice = info("en-in-x-tpc-1", "en", "IN")
        val usVoice = info("en-us-x-tpc-1", "en", "US")
        assertTrue(scoreVoice(inVoice, kabir, "en", "IN") > scoreVoice(usVoice, kabir, "en", "IN"))
        val map = resolvePersonaVoices(
            listOf(
                info("en-GB-SMTm00", "en", "GB"),
                info("en-in-x-tpc-1", "en", "IN"), info("en-in-x-tpc-2", "en", "IN"),
                info("en-us-x-tpc-1", "en", "US"), info("en-us-x-tpc-2", "en", "US"),
                info("en-us-x-tpc-3", "en", "US"), info("en-us-x-tpc-4", "en", "US"),
                info("en-us-x-tpc-5", "en", "US")
            ),
            "en", "IN"
        )
        assertEquals("en-GB-SMTm00", map["jarvis"]?.name)
        assertTrue("arjun got ${map["arjun"]?.name}", map["arjun"]?.name.orEmpty().startsWith("en-in"))
        assertTrue("kabir got ${map["kabir"]?.name}", map["kabir"]?.name.orEmpty().startsWith("en-in"))
    }
}
