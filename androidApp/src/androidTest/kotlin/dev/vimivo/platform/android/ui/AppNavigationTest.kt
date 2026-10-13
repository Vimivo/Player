package dev.vimivo.platform.android.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.vimivo.app.MainActivity
import org.junit.Rule
import org.junit.Test

class AppNavigationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun primaryDestinationsRemainReachable() {
        listOf("Music", "Videos", "Network", "Settings", "Home").forEach { destination ->
            composeRule.onNodeWithContentDescription(destination).performClick()
            composeRule.onNodeWithText(destination, useUnmergedTree = true).assertIsDisplayed()
        }
    }

    @Test
    fun globalSearchCanBeOpenedAndClosed() {
        composeRule.onNodeWithContentDescription("Global search").performClick()
        composeRule.onNodeWithText("Search songs, albums, artists, videos").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Close search").performClick()
    }
}
