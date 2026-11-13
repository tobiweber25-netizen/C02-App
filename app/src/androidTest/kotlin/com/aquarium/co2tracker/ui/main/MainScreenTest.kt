package com.aquarium.co2tracker.ui.main

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.aquarium.co2tracker.MainActivity
import org.junit.Rule
import org.junit.Test

/** UI tests for [com.aquarium.co2tracker.ui.main.MainScreen]. */
class MainScreenTest {

  @get:Rule val composeTestRule = createAndroidComposeRule<MainActivity>()

  @Test
  fun appHeader_exists() {
    composeTestRule.onNodeWithText("AquaCO2").assertIsDisplayed()
    composeTestRule.onNodeWithText("Pulse").assertIsDisplayed()
  }
}

