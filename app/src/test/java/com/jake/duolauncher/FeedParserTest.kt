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
        // The card shows the summary as plain text, so markup is stripped rather than displayed.
        assertEquals("Hello world", first.summary)
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

    @Test fun untitledMicroblogItemsGetAHeadlineFromTheirText() {
        val feed = parse("""
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0"><channel><title>GrapheneOS</title>
              <item>
                <guid isPermaLink="true">https://grapheneos.social/@GrapheneOS/1</guid>
                <link>https://grapheneos.social/@GrapheneOS/1</link>
                <pubDate>Mon, 05 Oct 2026 03:53:58 +0000</pubDate>
                <description>&lt;p&gt;GmsCompatConfig version 176 released:&lt;/p&gt;&lt;p&gt;&lt;a href="https://github.com/x/releases/tag/config-176"&gt;&lt;span class="invisible"&gt;https://&lt;/span&gt;&lt;span&gt;github.com/x&lt;/span&gt;&lt;/a&gt;&lt;/p&gt;&lt;p&gt;See the linked release notes &amp;amp; changelog.&lt;/p&gt;</description>
              </item>
              <item>
                <link>https://grapheneos.social/@GrapheneOS/2</link>
                <description>&lt;p&gt;Short note&lt;/p&gt;</description>
              </item>
            </channel></rss>
        """.trimIndent())
        assertEquals(2, feed.entries.size)
        assertEquals("GmsCompatConfig version 176 released", feed.entries[0].title)
        // The summary continues after the headline and drops the bare link.
        assertEquals("See the linked release notes & changelog.", feed.entries[0].summary)
        assertEquals("Short note", feed.entries[1].title)
        assertEquals("", feed.entries[1].summary)
    }

    @Test fun atomXhtmlContentIsReadAsText() {
        val feed = parse("""
            <?xml version="1.0" encoding="utf-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom"><title>GrapheneOS changelog</title>
              <entry><id>x#2026100200</id><link href="https://grapheneos.org/releases#2026100200"/>
                <title>2026100200</title><updated>2026-10-02T00:00:00Z</updated>
                <content type="xhtml"><div xmlns="http://www.w3.org/1999/xhtml"><p>Tags:</p>
                  <ul><li><a href="https://github.com/y">2026100200</a> (Pixel Fold)</li></ul>
                  <p>Changes since the 2026092500 release:</p><ul><li>kernel: fix</li><li>apps: update</li></ul>
                </div></content></entry>
            </feed>
        """.trimIndent())
        val entry = feed.entries.single()
        assertEquals("2026100200", entry.title)
        assertEquals("https://grapheneos.org/releases#2026100200", entry.link)
        assertTrue(entry.summary.contains("Changes since the 2026092500 release:"))
        assertTrue("List items must not run together: ${entry.summary}", entry.summary.contains("kernel: fix apps: update"))
        assertFalse(entry.summary.contains("<"))
    }

    @Test fun longUntitledPostsContinueWhereTheHeadlineStopped() {
        val post = "Google wants Android users to feel safe due to believing they have the latest privacy and security patches. Their announcement explains https://example.com/x the details."
        val (headline, rest) = FeedParser.splitHeadline(post)
        assertTrue(headline.endsWith("…"))
        assertTrue(headline.length <= 91)
        // Headline plus summary reproduces the post's words (minus the link), so nothing repeats.
        val rejoined = headline.removeSuffix("…") + " " + rest
        assertEquals(post.replace(" https://example.com/x", "").replace(Regex("\\s+"), " "), rejoined.replace(Regex("\\s+"), " ").trim())
    }

    @Test fun declaredTitlesAreKeptVerbatim() {
        val feed = parse("""<rss version="2.0"><channel><item><title>Tom &amp; Jerry &lt;live&gt;</title>
            <link>https://example.com/a</link></item></channel></rss>""")
        assertEquals("Tom & Jerry <live>", feed.entries.single().title)
    }

    @Test fun plainTextStripsMarkupAndDecodesEntities() {
        assertEquals("Fish & chips it’s", FeedParser.plainText("<p>Fish &amp; chips</p><p>it&#8217;s</p>"))
        assertEquals("a b", FeedParser.plainText("a<br/>b"))
        assertEquals("", FeedParser.plainText("   "))
        assertEquals("x", FeedParser.headlineFrom("x"))
        val long = "word ".repeat(40).trim()
        assertTrue(FeedParser.headlineFrom(long).length <= 91)
        assertTrue(FeedParser.headlineFrom(long).endsWith("…"))
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
