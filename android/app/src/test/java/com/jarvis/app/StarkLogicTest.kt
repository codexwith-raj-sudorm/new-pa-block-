package com.jarvis.app

import com.jarvis.app.local.vaultContent
import com.jarvis.app.ui.starkSharedPreview
import com.jarvis.app.widget.starkWidgetLabel
import org.junit.Assert.*
import org.junit.Test

class StarkLogicTest {
    @Test fun widgetLabelActive() {
        assertEquals("JARVIS: ACTIVE", starkWidgetLabel(true))
    }

    @Test fun widgetLabelStandby() {
        assertEquals("STANDBY", starkWidgetLabel(false))
    }

    @Test fun sharePreviewText() {
        assertEquals("hi", starkSharedPreview("hi"))
    }

    @Test fun sharePreviewNull() {
        assertEquals("No text payload detected.", starkSharedPreview(null))
    }

    @Test fun sharePreviewBlank() {
        assertEquals("No text payload detected.", starkSharedPreview("   "))
    }

    @Test fun sharePreviewCaps() {
        assertEquals(4000, starkSharedPreview("x".repeat(5000)).length)
    }

    @Test fun vaultContentTrims() {
        assertEquals("a", vaultContent("  a  "))
    }

    @Test fun vaultContentCaps() {
        assertEquals(2000, vaultContent("y".repeat(3000)).length)
    }
}
