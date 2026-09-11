package com.jarvis.app

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetTapTest {
    @Test fun speechAlwaysWins() {
        assertEquals(TapAction.INTERRUPT, widgetTapAction(true, true))
        assertEquals(TapAction.INTERRUPT, widgetTapAction(true, false))
    }

    @Test fun otherwiseTogglesWake() {
        assertEquals(TapAction.WAKE_ON, widgetTapAction(false, false))
        assertEquals(TapAction.WAKE_OFF, widgetTapAction(false, true))
    }
}
