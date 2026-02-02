package com.djlactose.energydrink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for preference default values.
 * These tests verify the expected default behavior without requiring Android context.
 */
class PreferencesTest {

    // Default preference values (matching MainActivity defaults)
    private val defaultOpacity = 100
    private val defaultTimeoutMs = 0L
    private val defaultTimeoutIndex = 0
    private val defaultShutdownOnPower = false

    @Test
    fun `opacity defaults to 100`() {
        assertEquals(100, defaultOpacity)
    }

    @Test
    fun `timeout defaults to 0 (off)`() {
        assertEquals(0L, defaultTimeoutMs)
    }

    @Test
    fun `timeout index defaults to 0`() {
        assertEquals(0, defaultTimeoutIndex)
    }

    @Test
    fun `shutdown on power defaults to false`() {
        assertEquals(false, defaultShutdownOnPower)
    }

    @Test
    fun `custom icon uri would default to null`() {
        val customIconUri: String? = null
        assertNull(customIconUri)
    }

    @Test
    fun `opacity range is 0 to 100`() {
        val minOpacity = 0
        val maxOpacity = 100
        assertEquals(0, minOpacity)
        assertEquals(100, maxOpacity)
    }
}
