package com.jake.duolauncher

import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URI
import java.net.UnknownHostException

internal sealed interface FeedFetchResult {
    data class Success(val feed: ParsedFeed) : FeedFetchResult
    data class Failure(val message: String) : FeedFetchResult
}

/** Direct, bounded fetch of one user-added feed. No cookies, no extra requests, no uploads.
 * On GrapheneOS and other hardened systems the per-app network toggle surfaces as a
 * SecurityException, which is reported as an actionable message instead of a crash.
 */
internal object FeedFetcher {
    const val MAX_BYTES = 2 * 1024 * 1024
    private const val USER_AGENT = "UnoLauncher/0.16 (Android) personal-feed"
    private const val TIMEOUT_MS = 10_000

    fun fetch(urlString: String): FeedFetchResult {
        try {
            val connection = URI(urlString).toURL().openConnection() as HttpURLConnection
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.setRequestProperty("Accept",
                "application/rss+xml, application/atom+xml, application/xml, text/xml, */*")
            val code = connection.responseCode
            if (code !in 200..299) return FeedFetchResult.Failure("The server answered HTTP $code.")
            val bytes = connection.inputStream.use(InputStream::readBytes)
            if (bytes.size > MAX_BYTES) return FeedFetchResult.Failure("The feed is too large to read.")
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
