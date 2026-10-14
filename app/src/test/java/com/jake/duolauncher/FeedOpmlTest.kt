package com.jake.duolauncher

import com.jake.duolauncher.feed.*

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedOpmlTest {
    @Test fun parseExtractsEveryOutlineWithAUrlIncludingNestedFolders() {
        val xml = """
            <?xml version="1.0"?>
            <opml version="2.0"><head><title>Feeds</title></head><body>
              <outline text="News" title="News folder">
                <outline type="rss" text="GrapheneOS releases" xmlUrl="https://grapheneos.org/releases.atom" />
              </outline>
              <outline type="rss" text="Announcements" xmlUrl="https://grapheneos.social/@GrapheneOS.rss" />
              <outline text="no url here" />
            </body></opml>
        """.trimIndent()
        assertEquals(listOf(
            OpmlFeed("https://grapheneos.org/releases.atom", "GrapheneOS releases"),
            OpmlFeed("https://grapheneos.social/@GrapheneOS.rss", "Announcements"),
        ), parseOpml(xml))
    }

    @Test fun parseSkipsRepeatsBlanksAndGarbage() {
        assertEquals("the first URL wins", listOf(OpmlFeed("https://a.example/feed", "https://a.example/feed")),
            parseOpml("<opml><body><outline xmlUrl=\"https://a.example/feed\"/>" +
                "<outline text=\"dup\" xmlUrl=\"https://a.example/feed\"/></body></opml>"))
        assertEquals(emptyList<OpmlFeed>(), parseOpml(""))
        assertEquals(emptyList<OpmlFeed>(), parseOpml("not xml at all"))
    }

    @Test fun serializeEscapesAndRoundTrips() {
        val sources = listOf(
            FeedSource("a", "https://a.example/feed?x=1&y=2", "A & B <News>", enabled = true),
            FeedSource("b", "https://b.example/feed", "Plain", enabled = false),
        )
        val xml = serializeOpml(sources)
        assertTrue("the ampersand and angle bracket are escaped", xml.contains("&amp;") && xml.contains("&lt;"))
        val parsed = parseOpml(xml)
        assertEquals(listOf("https://a.example/feed?x=1&y=2", "https://b.example/feed"), parsed.map { it.url })
        assertEquals("A & B <News>", parsed.first().label)
    }

    @Test fun xmlEscapeCoversAllFiveCharacters() {
        assertEquals("a&amp;b&lt;c&gt;d&quot;e&apos;f", xmlEscape("a&b<c>d\"e'f"))
    }
}
