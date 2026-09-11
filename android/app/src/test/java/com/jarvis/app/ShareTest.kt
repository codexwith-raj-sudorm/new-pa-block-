package com.jarvis.app

import org.junit.Assert.*
import org.junit.Test

class ShareTest {
    @Test fun sumPrompt() {
        assertEquals("Summarize this in 3 short bullets:\nhello", sharePrompt("sum", "hello"))
    }

    @Test fun eli5Prompt() {
        assertTrue(sharePrompt("eli5", "x").startsWith("Explain this like I'm 5"))
    }

    @Test fun bugsPromptMentionsFence() {
        assertTrue(sharePrompt("bugs", "code").contains("fenced block"))
    }

    @Test fun unknownKindTranslates() {
        assertTrue(sharePrompt("tr", "hi").contains("Hindi"))
    }

    @Test fun longTextTrimmed() {
        val p = sharePrompt("sum", "a".repeat(5000))
        assertTrue(p.length < 4200)
    }
}
