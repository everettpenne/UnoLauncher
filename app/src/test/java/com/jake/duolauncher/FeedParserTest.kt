package com.jake.duolauncher

import org.junit.Assert.*
import org.junit.Test

class FeedParserTest {
    private fun parse(xml: String): ParsedFeed = FeedParser.parse(xml.byteInputStream(Charsets.UTF_8))

    @Test fun rss2EntriesAreParsedWithEntitiesCdataAndDates() {
        val feed = parse("""
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0"><channel>
                <title>Example News</title>
                <link>https://example.com</link>
                <item>
                    <title>First &amp; second</title>
                    <link>https://example.com/1</link>
                    <description><![CDATA[<b>Hello</b> world]]></description>
                    <pubDate>Sat, 01 Sep 2018 10:00:00 GMT</pubDate>
                </item>
                <item>
                    <title>Second story</title>
                    <link>https://example.com/2</link>
                    <description>Plain text</description>
                    <pubDate>Mon, 3 Sep 2018 08:30:00 +0200</pubDate>
                </item>
                <item><title>Missing link</title><description>nope</description></item>
                <item><title>Bad link</title><link>ftp://example.com/nope</link></item>
            </channel></rss>
        """.trimIndent())
        assertEquals("Example News", feed.title)
        assertEquals(2, feed.entries.size)
        val first = feed.entries[0]
        assertEquals("First & second", first.title)
        assertEquals("https://example.com/1", first.link)
        assertEquals("<b>Hello</b> world", first.summary)
        assertEquals(1535796000000L, first.publishedAt)
        val second = feed.entries[1]
        assertEquals("Second story", second.title)
        assertEquals(1535956200000L, second.publishedAt)
    }

    @Test fun atomEntriesPreferAlternateLinksAndIsoDates() {
        val feed = parse("""
            <?xml version="1.0" encoding="utf-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom">
                <title>Atom Feed</title>
                <entry>
                    <title>Atom story</title>
                    <link rel="alternate" href="https://example.com/a1"/>
                    <link rel="self" href="https://example.com/self/a1"/>
                    <summary type="html">Summary &amp; more</summary>
                    <updated>2018-09-02T12:00:00Z</updated>
                </entry>
                <entry>
                    <title>Relative link</title>
                    <link href="/relative/a2"/>
                    <updated>2018-09-02T13:00:00Z</updated>
                </entry>
                <entry>
                    <title>Undated</title>
                    <link href="https://example.com/a3"/>
                </entry>
            </feed>
        """.trimIndent())
        assertEquals("Atom Feed", feed.title)
        assertEquals(2, feed.entries.size)
        val first = feed.entries[0]
        assertEquals("Atom story", first.title)
        assertEquals("https://example.com/a1", first.link)
        assertEquals("Summary & more", first.summary)
        assertEquals(1535889600000L, first.publishedAt)
        assertEquals("Undated", feed.entries[1].title)
        assertEquals(0L, feed.entries[1].publishedAt)
    }

    @Test fun truncatedXmlNeverProducesEntriesOrCrashes() {
        // Pull-parser EOF behavior differs between the platform parser and the test
        // provider: either outcome is safe as long as no entries appear.
        val outcome = runCatching { parse("<rss><channel><item><title>broken</title>") }
        if (outcome.isSuccess) assertTrue(outcome.getOrThrow().entries.isEmpty())
        else assertTrue(outcome.exceptionOrNull() is FeedParseException)
    }

    @Test fun emptyFeedHasNoEntries() {
        val feed = parse("<rss version=\"2.0\"><channel><title>Sparse</title></channel></rss>")
        assertEquals("Sparse", feed.title)
        assertTrue(feed.entries.isEmpty())
    }

    @Test fun datesParseInCommonFormats() {
        assertEquals(1535796000000L, FeedParser.parseFeedDate("Sat, 01 Sep 2018 10:00:00 GMT"))
        assertEquals(1535796000000L, FeedParser.parseFeedDate("Sat, 1 Sep 2018 10:00:00 GMT"))
        assertEquals(1535956200000L, FeedParser.parseFeedDate("Mon, 3 Sep 2018 08:30:00 +0200"))
        assertEquals(1535889600000L, FeedParser.parseFeedDate("2018-09-02T12:00:00Z"))
        assertEquals(0L, FeedParser.parseFeedDate("not a date"))
        assertEquals(0L, FeedParser.parseFeedDate(null))
    }
}
