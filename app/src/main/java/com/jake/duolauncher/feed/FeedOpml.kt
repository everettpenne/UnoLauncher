package com.jake.duolauncher.feed

import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory

/** A feed address and its name, as an OPML outline carries them. */
internal data class OpmlFeed(val url: String, val label: String)

/** The outcome of an import: how many feeds were added, and how many were skipped (invalid or already present). */
data class FeedOpmlResult(val added: Int, val skipped: Int)

/** The largest OPML file that will be read: far more than the feed limit needs, far less than could hurt. */
internal const val MAX_OPML_BYTES = 1024 * 1024

/** How many feeds an OPML import will take, so a hostile file can't fill the list. */
internal const val MAX_IMPORT_FEEDS = 200

/** Escapes the five XML characters so a label or URL is safe inside an attribute. */
internal fun xmlEscape(text: String): String = text
    .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    .replace("\"", "&quot;").replace("'", "&apos;")

/** The user's feeds as an OPML 2.0 document, for moving the list to another reader. */
internal fun serializeOpml(sources: List<FeedSource>): String = buildString {
    append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
    append("<opml version=\"2.0\">\n")
    append("  <head><title>Uno Launcher feeds</title></head>\n")
    append("  <body>\n")
    sources.forEach { source ->
        append("    <outline text=\"").append(xmlEscape(source.label))
            .append("\" type=\"rss\" xmlUrl=\"").append(xmlEscape(source.url)).append("\" />\n")
    }
    append("  </body>\n</opml>\n")
}

/** Every outline with an xmlUrl in the file, in document order, first URL wins, at most [MAX_IMPORT_FEEDS].
 *  Folders are handled naturally: outlines are found wherever they sit. */
internal fun parseOpml(xml: String): List<OpmlFeed> {
    if (xml.isBlank()) return emptyList()
    val document = runCatching { opmlBuilder().parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8))) }.getOrNull()
        ?: return emptyList()
    val outlines = document.getElementsByTagName("outline")
    val seen = mutableSetOf<String>()
    return buildList {
        for (index in 0 until outlines.length) {
            val element = outlines.item(index) as? org.w3c.dom.Element ?: continue
            val url = element.getAttribute("xmlUrl").trim()
            if (url.isEmpty() || !seen.add(url)) continue
            val label = (element.getAttribute("text").ifBlank { element.getAttribute("title") }).trim()
            add(OpmlFeed(url, label.ifBlank { url }))
        }
    }.take(MAX_IMPORT_FEEDS)
}

/** A document builder that refuses DOCTYPE and external entities, so a hostile OPML can't read local files. */
private fun opmlBuilder(): DocumentBuilder {
    val factory = DocumentBuilderFactory.newInstance()
    runCatching { factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
    runCatching { factory.setFeature("http://xml.org/sax/features/external-general-entities", false) }
    runCatching { factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
    runCatching { factory.setXIncludeAware(false) }
    runCatching { factory.setExpandEntityReferences(false) }
    return factory.newDocumentBuilder()
}
