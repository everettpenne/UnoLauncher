package com.jake.duolauncher

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jake.duolauncher.appearance.*
import com.jake.duolauncher.feed.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The feed page itself: a story two feeds both carry, the Saved view, and a filter chip for a feed that is gone. */
@RunWith(AndroidJUnit4::class)
class FeedPageInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private val a = FeedSource("a", "https://a.example/feed", "Feed A")
    private val b = FeedSource("b", "https://b.example/feed", "Feed B")
    private fun entry(source: FeedSource, link: String, at: Long) = FeedEntry(source.id, source.label, "Story $link", link, "", at)

    @Test fun aStoryCarriedByTwoFeedsShowsTwiceWithoutCrashingAndSavedFiltersIt() {
        val holder = androidx.compose.runtime.mutableStateOf(FeedState(sources = listOf(a, b),
            entries = listOf(entry(a, "https://x.example/shared", 3), entry(b, "https://x.example/shared", 2), entry(b, "https://x.example/only-b", 1)),
            savedLinks = listOf("https://x.example/only-b")))
        compose.setContent { DuoTheme(false) { FeedPage(holder.value, {}, {}, {}, {}) } }
        compose.waitForIdle()
        compose.onNodeWithTag("feed-entry-0").assertExists()
        compose.onNodeWithTag("feed-entry-1").assertExists()
        compose.onNodeWithTag("feed-entry-2").assertExists()
        compose.onNodeWithTag("feed-filter-saved").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("feed-entry-0").assertExists()
        compose.onAllNodesWithTag("feed-entry-1").assertCountEquals(0)
        // The feed behind the chosen chip is removed: the page falls back to showing everything, not an empty list.
        compose.onNodeWithTag("feed-filter-saved").performClick()
        compose.onNodeWithTag("feed-filter-b").performClick()
        holder.value = holder.value.copy(sources = listOf(a), entries = holder.value.entries.filter { it.sourceId == "a" })
        compose.waitForIdle()
        compose.onNodeWithTag("feed-entry-0").assertExists()
    }
}
