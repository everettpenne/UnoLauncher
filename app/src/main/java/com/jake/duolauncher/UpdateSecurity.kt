package com.jake.duolauncher

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.URI
import java.security.MessageDigest

/** The rules the self-updater and the feed reader use to decide what to trust, pure so they are tested. */
internal object UpdateSecurity {
    /** The app's version as shown to servers in the User-Agent; set once at start. */
    @Volatile var appVersion: String = "unknown"

    fun userAgent(kind: String) = "UnoLauncher/$appVersion (Android) $kind"

    /** Where an update download may be sent: GitHub itself and its release-asset hosts, over https only. */
    fun redirectAllowed(uri: URI): Boolean {
        if (uri.scheme?.lowercase() != "https") return false
        val host = uri.host?.lowercase() ?: return false
        return host == "github.com" || host == "api.github.com" || host.endsWith(".githubusercontent.com")
    }

    enum class Checksum { OK, MISSING, MISMATCH }

    /** A release with no published checksum is refused, not installed unchecked. */
    fun checksum(expected: String?, actual: String): Checksum = when {
        expected.isNullOrBlank() -> Checksum.MISSING
        !expected.equals(actual, ignoreCase = true) -> Checksum.MISMATCH
        else -> Checksum.OK
    }

    /** The downloaded APK must be signed by exactly the certificates that signed the app that is already installed. */
    fun sameSigners(installed: Set<String>, candidate: Set<String>): Boolean =
        installed.isNotEmpty() && candidate.isNotEmpty() && installed == candidate

    fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }

    fun sha256Hex(bytes: ByteArray) = hex(MessageDigest.getInstance("SHA-256").digest(bytes))
}

/** Reads a stream with a running byte count, so a server that sends more than the cap is cut off instead of filling memory. */
internal object BoundedRead {
    /** The bytes, or null as soon as more than [max] arrive. */
    fun readCapped(input: InputStream, max: Int): ByteArray? {
        val out = java.io.ByteArrayOutputStream(minOf(max, 64 * 1024))
        val buffer = ByteArray(16 * 1024)
        var total = 0
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            total += n
            if (total > max) return null
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }

    /** Copies to [out] while hashing, refusing more than [max] bytes; returns the SHA-256 hex, or null when over the cap. */
    fun copyCapped(input: InputStream, out: OutputStream, max: Long): String? {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            total += n
            if (total > max) return null
            digest.update(buffer, 0, n); out.write(buffer, 0, n)
        }
        return UpdateSecurity.hex(digest.digest())
    }
}

internal class DownloadTooLarge : IOException("too large")
