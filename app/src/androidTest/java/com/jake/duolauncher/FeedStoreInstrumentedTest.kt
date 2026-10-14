package com.jake.duolauncher

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jake.duolauncher.feed.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The feed store's bookkeeping, on seeded saved data (no network): cleaning up after a removed feed, the keep setting, restoring feeds. */
@RunWith(AndroidJUnit4::class)
class FeedStoreInstrumentedTest {
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefs get() = context.getSharedPreferences("feed", Context.MODE_PRIVATE)
    private var saved: Map<String, *> = emptyMap<String, Any>()

    @Before fun keepWhatWasThere() { saved = prefs.all.toMap(); prefs.edit().clear().commit() }

    @After fun putItBack() {
        val edit = prefs.edit().clear()
        saved.forEach { (k, v) -> when (v) {
            is String -> edit.putString(k, v); is Boolean -> edit.putBoolean(k, v); is Int -> edit.putInt(k, v); is Long -> edit.putLong(k, v)
        } }
        edit.commit()
    }

    private fun seed(sources: List<Triple<String, String, Boolean>>, entries: List<List<Any>>, read: List<String>, savedLinks: List<String>, keep: Int? = null) {
        val edit = prefs.edit()
            .putString("sources", JSONArray().also { a -> sources.forEach { (id, url, on) ->
                a.put(JSONObject().put("id", id).put("url", url).put("label", id).put("enabled", on)) } }.toString())
            .putString("entries", JSONArray().also { a -> entries.forEach { (source, link, at) ->
                a.put(JSONObject().put("sourceId", source).put("sourceLabel", source).put("title", "t $link").put("link", link).put("publishedAt", at)) } }.toString())
            .putString("read_links", JSONArray(read).toString()).putString("saved_links", JSONArray(savedLinks).toString())
        keep?.let { edit.putInt("keep_per_feed", it) }
        edit.commit()
    }

    private fun store() = FeedStore(context, CoroutineScope(SupervisorJob() + Dispatchers.Main))

    @Test fun removingAFeedDropsItsEntriesAndTheMarksOnlyItCarried() {
        seed(listOf(Triple("a", "https://a.example/feed", false), Triple("b", "https://b.example/feed", false)),
            listOf(listOf("a", "l1", 1L), listOf("a", "l2", 2L), listOf("b", "l2", 3L), listOf("b", "l3", 4L)),
            read = listOf("l1", "l3"), savedLinks = listOf("l1", "l2", "l3"))
        val store = store()
        store.removeFeed("a")
        val state = store.state.value
        assertEquals(listOf("b"), state.sources.map { it.id })
        assertEquals(setOf("l2", "l3"), state.entries.map { it.link }.toSet())
        assertEquals("a saved story another feed still carries stays saved", listOf("l2", "l3"), state.savedLinks)
        assertEquals(listOf("l3"), state.readLinks)
    }

    @Test fun lowerKeepTrimsAtOnceButNeverASavedStory() {
        seed(listOf(Triple("a", "https://a.example/feed", false)), (1..60).map { listOf("a", "s$it", it.toLong()) },
            read = emptyList(), savedLinks = listOf("s1"), keep = 80)
        val store = store()
        assertEquals(60, store.state.value.entries.size)
        store.setKeepPerFeed(20)
        val after = store.state.value
        assertEquals(20, after.keepPerFeed)
        assertEquals(21, after.entries.size)
        assertTrue("the saved story survives", after.entries.any { it.link == "s1" })
        store.setKeepPerFeed(33) // not one of the choices: ignored
        assertEquals(20, store.state.value.keepPerFeed)
        assertEquals("kept across restarts", 20, store().state.value.keepPerFeed)
    }

    @Test fun restoringFeedsAddsOnlyNewOnesAndRespectsTheLimit() {
        seed(listOf(Triple("a", "https://a.example/feed", false)), emptyList(), emptyList(), emptyList())
        val store = store()
        val added = store.restoreFeeds(listOf(FeedSpec("https://a.example/feed", "A again", false), FeedSpec("https://new.example/rss", "New", false),
            FeedSpec("http://insecure.example/rss", "Plain http", false)))
        assertEquals(1, added)
        assertEquals(listOf("https://a.example/feed", "https://new.example/rss"), store.state.value.sources.map { it.url })
        val many = (1..MAX_FEED_SOURCES + 5).map { FeedSpec("https://many$it.example/rss", "m$it", false) }
        store.restoreFeeds(many)
        assertEquals(MAX_FEED_SOURCES, store.state.value.sources.size)
    }
}
