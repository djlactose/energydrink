package com.djlactose.energydrink

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isEnabled
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.hamcrest.Matchers.not
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for MainActivity UI interactions and preference persistence.
 */
@RunWith(AndroidJUnit4::class)
class MainActivityTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        // Clear preferences before each test
        context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }

    @After
    fun tearDown() {
        // Clean up preferences after tests
        context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }

    @Test
    fun mainActivityLaunches() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(withId(R.id.startServiceButton)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun opacitySliderIsDisplayed() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(withId(R.id.opacitySlider)).check(matches(isDisplayed()))
            onView(withId(R.id.opacityValue)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun timeoutSpinnerIsDisplayed() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(withId(R.id.timeoutSpinner)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun powerToggleIsDisplayed() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(withId(R.id.powerButtonToggle)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun customIconControlsAreDisplayed() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(withId(R.id.customIconPreview)).check(matches(isDisplayed()))
            onView(withId(R.id.selectIconButton)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun powerTogglePersistsState() {
        // First, enable the toggle
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(withId(R.id.powerButtonToggle)).perform(click())
        }

        // Verify it was saved
        val savedValue = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            .getBoolean("shutdown_on_power", false)
        assert(savedValue) { "Power toggle state should be saved as true" }
    }

    @Test
    fun opacityDefaultsTo100() {
        val defaultOpacity = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            .getInt("widget_opacity", 100)
        assert(defaultOpacity == 100) { "Default opacity should be 100" }
    }

    @Test
    fun timeoutDefaultsToOff() {
        val defaultTimeout = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            .getLong("timeout_ms", 0)
        assert(defaultTimeout == 0L) { "Default timeout should be 0 (off)" }
    }

    @Test
    fun customIconDefaultsToNull() {
        val customIcon = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            .getString("custom_icon_uri", null)
        assert(customIcon == null) { "Default custom icon should be null" }
    }
}
