package com.jake.duolauncher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.URI

class UpdateSecurityTest {
    @Test fun onlyGithubOverHttpsIsFollowed() {
        assertTrue(UpdateSecurity.redirectAllowed(URI("https://github.com/everettpenne/UnoLauncher/releases/download/v1/a.apk")))
        assertTrue(UpdateSecurity.redirectAllowed(URI("https://api.github.com/repos/x/y/releases")))
        assertTrue(UpdateSecurity.redirectAllowed(URI("https://objects.githubusercontent.com/abc")))
        assertTrue(UpdateSecurity.redirectAllowed(URI("https://release-assets.githubusercontent.com/abc")))
        assertFalse(UpdateSecurity.redirectAllowed(URI("http://github.com/x")))
        assertFalse(UpdateSecurity.redirectAllowed(URI("https://evil.example/x")))
        assertFalse(UpdateSecurity.redirectAllowed(URI("https://github.com.evil.example/x")))
        assertFalse(UpdateSecurity.redirectAllowed(URI("https://notgithubusercontent.com/x")))
        assertFalse(UpdateSecurity.redirectAllowed(URI("file:///etc/passwd")))
    }

    @Test fun aMissingChecksumFailsClosed() {
        assertEquals(UpdateSecurity.Checksum.MISSING, UpdateSecurity.checksum(null, "abc"))
        assertEquals(UpdateSecurity.Checksum.MISSING, UpdateSecurity.checksum("  ", "abc"))
        assertEquals(UpdateSecurity.Checksum.MISMATCH, UpdateSecurity.checksum("abd", "abc"))
        assertEquals(UpdateSecurity.Checksum.OK, UpdateSecurity.checksum("ABC", "abc"))
    }

    @Test fun signersMustMatchExactlyAndExist() {
        assertTrue(UpdateSecurity.sameSigners(setOf("a"), setOf("a")))
        assertFalse(UpdateSecurity.sameSigners(setOf("a"), setOf("b")))
        assertFalse(UpdateSecurity.sameSigners(setOf("a"), setOf("a", "b")))
        assertFalse(UpdateSecurity.sameSigners(emptySet(), emptySet()))
        assertFalse(UpdateSecurity.sameSigners(setOf("a"), emptySet()))
    }

    @Test fun readCappedStopsAtTheLimit() {
        assertEquals(10, BoundedRead.readCapped(ByteArrayInputStream(ByteArray(10)), 10)!!.size)
        assertNull(BoundedRead.readCapped(ByteArrayInputStream(ByteArray(11)), 10))
        // A stream that never ends is cut off at the cap instead of being read to exhaustion.
        var served = 0L
        val endless = object : java.io.InputStream() {
            override fun read(): Int { served++; return 1 }
            override fun read(b: ByteArray, off: Int, len: Int): Int { served += len; return len }
        }
        assertNull(BoundedRead.readCapped(endless, 100_000))
        assertTrue("read ${served} bytes", served < 200_000)
    }

    @Test fun copyCappedHashesAndRefusesOversize() {
        val data = "hello".toByteArray()
        val out = ByteArrayOutputStream()
        val sha = BoundedRead.copyCapped(ByteArrayInputStream(data), out, 100)
        assertNotNull(sha)
        assertEquals(UpdateSecurity.sha256Hex(data), sha)
        assertEquals(5, out.size())
        assertNull(BoundedRead.copyCapped(ByteArrayInputStream(data), ByteArrayOutputStream(), 4))
    }

    @Test fun userAgentCarriesTheRealVersion() {
        UpdateSecurity.appVersion = "9.9.9-test"
        assertEquals("UnoLauncher/9.9.9-test (Android) updater", UpdateSecurity.userAgent("updater"))
    }
}
