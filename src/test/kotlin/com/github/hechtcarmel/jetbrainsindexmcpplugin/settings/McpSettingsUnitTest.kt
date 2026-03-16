package com.github.hechtcarmel.jetbrainsindexmcpplugin.settings

import junit.framework.TestCase

class McpSettingsUnitTest : TestCase() {

    // State default values tests

    fun testStateDefaultValues() {
        val state = McpSettings.State()

        assertEquals("Default maxHistorySize should be 100", 100, state.maxHistorySize)
    }

    // State mutability tests

    fun testStateMaxHistorySizeMutable() {
        val state = McpSettings.State()
        state.maxHistorySize = 200

        assertEquals(200, state.maxHistorySize)
    }

    fun testStateCustomConstructor() {
        val state = McpSettings.State(
            maxHistorySize = 500
        )

        assertEquals(500, state.maxHistorySize)
    }

    // State copy tests

    fun testStateCopy() {
        val original = McpSettings.State(maxHistorySize = 50)
        val copy = original.copy(maxHistorySize = 150)

        assertEquals(50, original.maxHistorySize)
        assertEquals(150, copy.maxHistorySize)
    }

    // State equals and hashCode tests

    fun testStateEquals() {
        val state1 = McpSettings.State()
        val state2 = McpSettings.State()

        assertEquals(state1, state2)
    }

    fun testStateNotEqualsWhenDifferent() {
        val state1 = McpSettings.State(maxHistorySize = 100)
        val state2 = McpSettings.State(maxHistorySize = 200)

        assertFalse(state1 == state2)
    }

    fun testStateHashCode() {
        val state1 = McpSettings.State()
        val state2 = McpSettings.State()

        assertEquals(state1.hashCode(), state2.hashCode())
    }

    // McpSettings instance tests

    fun testMcpSettingsInitialization() {
        val settings = McpSettings()

        // Should have default state
        assertNotNull(settings.state)
        assertEquals(100, settings.maxHistorySize)
    }

    fun testMcpSettingsPropertyDelegation() {
        val settings = McpSettings()

        settings.maxHistorySize = 250

        assertEquals(250, settings.maxHistorySize)
    }

    fun testMcpSettingsLoadState() {
        val settings = McpSettings()
        val newState = McpSettings.State(
            maxHistorySize = 75
        )

        settings.loadState(newState)

        assertEquals(75, settings.maxHistorySize)
    }

    fun testMcpSettingsGetStateReturnsCurrentState() {
        val settings = McpSettings()
        settings.maxHistorySize = 300

        val state = settings.state

        assertEquals(300, state.maxHistorySize)
    }

    // Edge case tests

    fun testMaxHistorySizeZero() {
        val state = McpSettings.State(maxHistorySize = 0)
        assertEquals(0, state.maxHistorySize)
    }

    fun testMaxHistorySizeNegative() {
        val state = McpSettings.State(maxHistorySize = -1)
        assertEquals(-1, state.maxHistorySize)
    }
}
