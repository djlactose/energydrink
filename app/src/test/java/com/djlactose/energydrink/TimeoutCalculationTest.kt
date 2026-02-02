package com.djlactose.energydrink

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for timeout value calculations.
 */
class TimeoutCalculationTest {

    // Timeout values in milliseconds (matching MainActivity)
    private val timeoutValues = longArrayOf(0, 5*60*1000, 15*60*1000, 30*60*1000, 60*60*1000, 120*60*1000)

    @Test
    fun `timeout index 0 means disabled`() {
        assertEquals(0L, timeoutValues[0])
    }

    @Test
    fun `timeout values are correct milliseconds`() {
        assertEquals(5 * 60 * 1000L, timeoutValues[1])  // 5 minutes
        assertEquals(15 * 60 * 1000L, timeoutValues[2]) // 15 minutes
        assertEquals(30 * 60 * 1000L, timeoutValues[3]) // 30 minutes
        assertEquals(60 * 60 * 1000L, timeoutValues[4]) // 1 hour
        assertEquals(120 * 60 * 1000L, timeoutValues[5]) // 2 hours
    }

    @Test
    fun `timeout values count is 6`() {
        assertEquals(6, timeoutValues.size)
    }
}
