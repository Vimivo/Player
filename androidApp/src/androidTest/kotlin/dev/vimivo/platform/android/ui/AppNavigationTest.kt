package dev.vimivo.platform.android.ui

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import dev.vimivo.app.MainActivity
import org.junit.Rule
import org.junit.Test

class AppNavigationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    /** The tab bar's tabs are selectable and labelled by their text; their icons carry no description. */
    private fun tab(destination: String): SemanticsNodeInteraction =
        composeRule.onNode(isSelectable() and hasText(destination))

    /** Lists and rows here are lazy, so an item is only composed once scrolled near: try each scrollable. */
    private fun bringIntoView(text: String) {
        val scrollables = composeRule.onAllNodes(hasScrollToNodeAction())
        val count = scrollables.fetchSemanticsNodes().size
        for (index in 0 until count) {
            if (composeRule.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()) return
            runCatching { scrollables[index].performScrollToNode(hasText(text)) }
        }
        composeRule.onNodeWithText(text).assertExists("$text is not in any scrollable list")
    }

    @Test
    fun primaryDestinationsRemainReachable() {
        listOf("Music", "Videos", "Playlists", "Settings", "Home").forEach { destination ->
            tab(destination).performClick()
            tab(destination).assertIsSelected()
        }
    }

    @Test
    fun globalSearchCanBeOpenedAndClosed() {
        composeRule.onNodeWithContentDescription("More application actions").performClick()
        composeRule.onNodeWithText("Search library").performClick()
        composeRule.onNodeWithText("Search songs, albums, artists, videos").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Close search").performClick()
    }

    @Test
    fun musicLibraryExposesCoreCategories() {
        tab("Music").performClick()
        listOf("All songs", "Albums", "Artists", "Album artists", "Genres", "Folders", "Recently added", "Favorites")
            .forEach { bringIntoView(it) }
    }

    @Test
    fun settingsExposePersonalizationPrivacyAndBackupCategories() {
        tab("Settings").performClick()
        composeRule.onNodeWithText("Appearance").assertIsDisplayed()
        composeRule.onNodeWithText("Dynamic color").assertExists()
        listOf("Privacy", "Backup & restore", "About").forEach { bringIntoView(it) }
    }
}
