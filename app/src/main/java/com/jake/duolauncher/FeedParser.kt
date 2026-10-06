package com.jake.duolauncher

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/** A feed entry as parsed from an RSS or Atom document, before it is attached to a saved source. */
internal data class ParsedFeedEntry(
    val title: String,
    val link: String,
    val summary: String = "",
    val publishedAt: Long = 0L,
)

/** A parsed feed document: the feed's own title plus its entries. */
internal data class ParsedFeed(val title: String?, val entries: List<ParsedFeedEntry>)

internal class FeedParseException(message: String) : Exception(message)

/** Minimal, dependency-free RSS 2.0 and Atom parser.
 * Uses the framework XmlPullParser, so it works on every supported Android version
 * without extra native code or reflection. Parsing is bounded and never fetches.
 */
internal object FeedParser {
    const val MAX_ENTRIES = 50

    fun parse(input: InputStream): ParsedFeed {
        val parser = try {
            XmlPullParserFactory.newInstance().newPullParser()
        } catch (e: Exception) {
            throw FeedParseException("The feed reader is unavailable on this device.")
        }
        return try {
            parser.setInput(input, null)
            readDocument(parser)
        } catch (e: FeedParseException) {
            throw e
        } catch (e: Exception) {
            throw FeedParseException("This address didn't contain a readable feed.")
        } finally {
            runCatching { input.close() }
        }
    }

    private fun readDocument(parser: XmlPullParser): ParsedFeed {
        var feedTitle: String? = null
        val entries = mutableListOf<ParsedFeedEntry>()
        var inItem = false
        var title: String? = null
        var link: String? = null
        var summary = ""
        var dateRaw: String? = null
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    when (val name = localName(parser.name)) {
                        "channel" -> { /* RSS container; its title is the feed title */ }
                        "item", "entry" -> {
                            if (!inItem) {
                                inItem = true
                                title = null; link = null; summary = ""; dateRaw = null
                            }
                        }
                        "title" -> {
                            val text = elementText(parser)
                            if (inItem) { if (title == null) title = text }
                            else if (feedTitle == null) feedTitle = text
                        }
                        "link" -> {
                            if (inItem && link == null) {
                                val href = parser.getAttributeValue(null, "href")
                                link = if (!href.isNullOrBlank() && isWebLink(href)) href.trim()
                                else elementText(parser).trim().takeIf { isWebLink(it) }
                            }
                        }
                        "description", "summary", "content" -> {
                            if (inItem && summary.isBlank()) summary = elementText(parser)
                        }
                        "pubdate", "published", "updated", "date" -> {
                            if (inItem && dateRaw == null) dateRaw = elementText(parser)
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (localName(parser.name)) {
                        "item", "entry" -> {
                            if (inItem) {
                                inItem = false
                                val entryTitle = title?.trim()?.takeIf { it.isNotBlank() }
                                val entryLink = link?.trim()?.takeIf { isWebLink(it) }
                                if (entryTitle != null && entryLink != null) {
                                    entries += ParsedFeedEntry(entryTitle, entryLink,
                                        summary.trim().take(500), parseFeedDate(dateRaw))
                                }
                                if (entries.size >= MAX_ENTRIES) return ParsedFeed(feedTitle?.trim(), entries)
                            }
                        }
                    }
                }
            }
            event = parser.next()
        }
        return ParsedFeed(feedTitle?.trim(), entries)
    }

    /** Reads the complete text of the current element. Falls back to an empty string
     * when the element contains nested markup instead of plain text.
     */
    private fun elementText(parser: XmlPullParser): String = try {
        parser.nextText()
    } catch (e: Exception) {
        ""
    }

    private fun localName(name: String?): String = name?.substringAfterLast(':')?.lowercase(Locale.US) ?: ""

    internal fun isWebLink(link: String): Boolean =
        link.startsWith("http://", ignoreCase = true) || link.startsWith("https://", ignoreCase = true)

    internal fun parseFeedDate(raw: String?): Long {
        if (raw.isNullOrBlank()) return 0L
        val value = raw.trim()
        // RSS pubDate: "Sat, 1 Sep 2018 10:00:00 GMT" — pad a single-digit day for RFC 1123.
        val padded = value.replace(Regex(",\\s(\\d)\\s"), ", 0$1 ")
        parseZoned(padded, DateTimeFormatter.RFC_1123_DATE_TIME)?.let { return it }
        parseZoned(padded, DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US))?.let { return it }
        // Atom dates are ISO 8601 with an offset (including Z).
        runCatching { OffsetDateTime.parse(value, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant().toEpochMilli() }
            .getOrNull()?.let { return it }
        return 0L
    }

    private fun parseZoned(value: String, formatter: DateTimeFormatter): Long? = try {
        ZonedDateTime.parse(value, formatter).toInstant().toEpochMilli()
    } catch (e: DateTimeParseException) {
        null
    }
}
