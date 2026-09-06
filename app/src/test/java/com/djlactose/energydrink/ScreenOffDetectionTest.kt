package com.djlactose.energydrink

import android.view.Display
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the display-state check used to shut the widget down
 * when the user turns the screen off.
 */
class ScreenOffDetectionTest {

    @Test
    fun `display off counts as screen off`() {
        assertTrue(FloatingWidgetService.isScreenOff(Display.STATE_OFF))
    }

    @Test
    fun `doze counts as screen off for always-on displays`() {
        assertTrue(FloatingWidgetService.isScreenOff(Display.STATE_DOZE))
        assertTrue(FloatingWidgetService.isScreenOff(Display.STATE_DOZE_SUSPEND))
    }

    @Test
    fun `display on does not count as screen off`() {
        assertFalse(FloatingWidgetService.isScreenOff(Display.STATE_ON))
    }

    @Test
    fun `unknown state does not count as screen off`() {
        assertFalse(FloatingWidgetService.isScreenOff(Display.STATE_UNKNOWN))
    }
}
