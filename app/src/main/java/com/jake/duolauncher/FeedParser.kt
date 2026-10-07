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
                                val entryLink = link?.trim()?.takeIf { isWebLink(it) }
                                val body = plainText(summary)
                                val declared = title?.replace(Regex("\\s+"), " ")?.trim()?.takeIf { it.isNotBlank() }
                                // Microblog feeds (Mastodon) publish items with no title at all; the
                                // headline is then the start of the post, and the summary carries on
                                // from where the headline stopped, without bare links.
                                val derived = if (declared == null) splitHeadline(body) else null
                                val entryTitle = declared ?: derived!!.first
                                val entrySummary = if (derived == null) body else derived.second
                                if (entryTitle.isNotBlank() && entryLink != null) {
                                    entries += ParsedFeedEntry(entryTitle, entryLink,
                                        entrySummary.take(500), parseFeedDate(dateRaw))
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

    /** Reads all text inside the current element, including nested markup (Atom
     * `type="xhtml"` content nests real elements), and leaves the parser on the element's
     * end tag like [XmlPullParser.nextText]. Block boundaries become spaces so words from
     * adjacent paragraphs or list items don't run together. Returns an empty string if the
     * element can't be read.
     */
    private fun elementText(parser: XmlPullParser): String {
        val text = StringBuilder()
        var depth = 1
        try {
            while (depth > 0) {
                when (parser.next()) {
                    XmlPullParser.TEXT, XmlPullParser.CDSECT, XmlPullParser.ENTITY_REF -> text.append(parser.text)
                    XmlPullParser.START_TAG -> { depth++; text.append(' ') }
                    XmlPullParser.END_TAG -> { depth--; text.append(' ') }
                    XmlPullParser.END_DOCUMENT -> return text.toString()
                }
            }
        } catch (e: Exception) {
            return ""
        }
        return text.toString()
    }

    /** Plain text from a feed field that may carry HTML (escaped markup, as Mastodon and many
     * blogs publish): tags removed, block breaks kept as spaces, entities decoded, whitespace
     * collapsed.
     */
    internal fun plainText(raw: String): String {
        if (raw.isBlank()) return ""
        var text = raw
        if ('<' in text) {
            text = text.replace(Regex("<\\s*(br|/p|/div|/li|/h[1-6])\\b[^>]*>", RegexOption.IGNORE_CASE), " ")
                .replace(Regex("<[^>]*>"), "")
        }
        text = text.replace(Regex("&#x([0-9a-fA-F]+);")) { m ->
            m.groupValues[1].toIntOrNull(16)?.let { String(Character.toChars(it)) } ?: m.value
        }.replace(Regex("&#(\\d+);")) { m ->
            m.groupValues[1].toIntOrNull()?.let { String(Character.toChars(it)) } ?: m.value
        }.replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">")
            .replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&")
        return text.replace(Regex("\\s+"), " ").trim()
    }

    /** Splits an untitled post into a short headline (its opening words, cut before the first
     * link and at a word boundary) and the rest of the text, with bare links removed.
     */
    internal fun splitHeadline(plain: String, limit: Int = 90): Pair<String, String> {
        val linkAt = plain.indexOf("http")
        val beforeLink = (if (linkAt > 0) plain.substring(0, linkAt) else plain)
            .trim().trimEnd(':', '-', '–', '—').trim()
        val start = beforeLink.ifBlank { plain }
        val (headline, consumed) = if (start.length <= limit) start to start.length else {
            val cut = start.take(limit).substringBeforeLast(' ', start.take(limit))
            (cut.trimEnd(',', ';', ':', '.') + "…") to cut.length
        }
        val rest = plain.drop(consumed).replace(Regex("https?://\\S+"), " ")
            .replace(Regex("\\s+"), " ").trim().trimStart(':', ',', ';', '-', '–', '—', ' ')
        return headline to rest
    }

    internal fun headlineFrom(plain: String, limit: Int = 90): String = splitHeadline(plain, limit).first

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
