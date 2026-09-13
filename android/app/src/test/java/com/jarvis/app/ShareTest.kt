package com.jarvis.app

import org.junit.Assert.*
import org.junit.Test

class ShareTest {
    @Test fun wrapsWithLang() {
        assertEquals("```kotlin\nval x = 1\n```", codeShareText("kotlin", "val x = 1"))
    }

    @Test fun blankLangDefaults() {
        assertEquals("```code\nx\n```", codeShareText("  ", "x"))
    }
}
