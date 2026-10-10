package com.jake.duolauncher

import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URI
import java.net.UnknownHostException

/** Redirect rule for feed fetches: Uno connects only to the host the user added. A redirect
 * is followed only when it stays on that exact host over https; anything else (another
 * server, a downgrade to http, embedded credentials) is refused, never contacted.
 */
internal object FeedRedirectPolicy {
    const val MAX_REDIRECTS = 3

    fun target(current: URI, location: String?): URI? {
        if (location.isNullOrBlank()) return null
        val next = runCatching { current.resolve(location.trim()) }.getOrNull() ?: return null
        if (next.scheme?.lowercase() != "https" || next.rawUserInfo != null) return null
        val host = next.host ?: return null
        return next.takeIf { host.equals(current.host, ignoreCase = true) }
    }
}

internal sealed interface FeedFetchResult {
    data class Success(val feed: ParsedFeed) : FeedFetchResult
    data class Failure(val message: String) : FeedFetchResult
}

/** Direct, bounded fetch of one user-added feed over https. No cookies, no extra requests,
 * no uploads, and no host other than the one the user added.
 * On GrapheneOS and other hardened systems the per-app network toggle surfaces as a
 * SecurityException, which is reported as an actionable message instead of a crash.
 */
internal object FeedFetcher {
    const val MAX_BYTES = 2 * 1024 * 1024
    private const val TIMEOUT_MS = 10_000
    private val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)

    fun fetch(urlString: String): FeedFetchResult {
        try {
            var uri = URI(urlString)
            if (uri.scheme?.lowercase() != "https") return FeedFetchResult.Failure("Only https feeds are supported.")
            var redirects = 0
            var connection: HttpURLConnection
            while (true) {
                connection = uri.toURL().openConnection() as HttpURLConnection
                connection.connectTimeout = TIMEOUT_MS
                connection.readTimeout = TIMEOUT_MS
                // Followed by hand so every hop is checked against FeedRedirectPolicy.
                connection.instanceFollowRedirects = false
                connection.setRequestProperty("User-Agent", UpdateSecurity.userAgent("personal-feed"))
                connection.setRequestProperty("Accept",
                    "application/rss+xml, application/atom+xml, application/xml, text/xml, */*")
                val code = connection.responseCode
                if (code !in REDIRECT_CODES) break
                val target = FeedRedirectPolicy.target(uri, connection.getHeaderField("Location"))
                connection.disconnect()
                if (target == null) return FeedFetchResult.Failure(
                    "The feed moved to a different server. Add its new address instead.")
                if (++redirects > FeedRedirectPolicy.MAX_REDIRECTS) return FeedFetchResult.Failure(
                    "The feed redirected too many times.")
                uri = target
            }
            val code = connection.responseCode
            if (code !in 200..299) return FeedFetchResult.Failure("The server answered HTTP $code.")
            if (connection.contentLengthLong > MAX_BYTES) return FeedFetchResult.Failure("The feed is too large to read.")
            // Counted as it arrives, so a server that sends more than the cap is cut off rather than held in memory.
            val bytes = connection.inputStream.use { BoundedRead.readCapped(it, MAX_BYTES) }
                ?: return FeedFetchResult.Failure("The feed is too large to read.")
            val text = decodeBody(bytes, connection.contentType)
            val feed = FeedParser.parse(text.byteInputStream(Charsets.UTF_8))
            return if (feed.entries.isEmpty()) FeedFetchResult.Failure("This feed has no readable entries.")
            else FeedFetchResult.Success(feed)
        } catch (e: SecurityException) {
            return FeedFetchResult.Failure("Network access is turned off for Uno Launcher.")
        } catch (e: UnknownHostException) {
            return FeedFetchResult.Failure("The server couldn't be reached.")
        } catch (e: SocketTimeoutException) {
            return FeedFetchResult.Failure("The server took too long to answer.")
        } catch (e: FeedParseException) {
            return FeedFetchResult.Failure(e.message ?: "The feed couldn't be read.")
        } catch (e: IOException) {
            return FeedFetchResult.Failure("The connection failed.")
        } catch (e: IllegalArgumentException) {
            return FeedFetchResult.Failure("That isn't a web address.")
        }
    }

    /** Prefers the declared Content-Type charset, then the XML declaration, then UTF-8. */
    private fun decodeBody(bytes: ByteArray, contentType: String?): String {
        val charset = contentType?.let { type ->
            Regex("charset=([^;\\s]+)", RegexOption.IGNORE_CASE).find(type)?.groupValues?.get(1)
        }?.trim('\'', '"') ?: xmlDeclaredCharset(bytes) ?: "UTF-8"
        return runCatching { bytes.toString(charset(charset)) }.getOrElse { bytes.toString(Charsets.UTF_8) }
    }

    private fun xmlDeclaredCharset(bytes: ByteArray): String? {
        val head = bytes.copyOfRange(0, minOf(bytes.size, 512)).toString(Charsets.ISO_8859_1)
        val match = Regex("encoding\\s*=\\s*[\"']([^\"']+)", RegexOption.IGNORE_CASE).find(head) ?: return null
        return match.groupValues[1]
    }
}
