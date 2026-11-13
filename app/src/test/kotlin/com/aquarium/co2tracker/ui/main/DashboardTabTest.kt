package com.aquarium.co2tracker.ui.main

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardTabTest {

    @Test
    fun testDashboardTabValuesIncludeSettings() {
        val tabs = DashboardTab.entries
        assertEquals(3, tabs.size)
        assertTrue(tabs.contains(DashboardTab.DASHBOARD))
        assertTrue(tabs.contains(DashboardTab.HISTORY))
        assertTrue(tabs.contains(DashboardTab.SETTINGS))
    }
}
