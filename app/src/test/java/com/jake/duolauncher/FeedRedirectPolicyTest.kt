package com.jake.duolauncher

import java.net.URI
import org.junit.Assert.*
import org.junit.Test

class FeedRedirectPolicyTest {
    private val origin = URI("https://grapheneos.org/releases.atom")

    @Test fun sameHostRedirectsAreFollowed() {
        assertEquals(URI("https://grapheneos.org/new.atom"), FeedRedirectPolicy.target(origin, "/new.atom"))
        assertEquals(URI("https://grapheneos.org/a/b.atom"), FeedRedirectPolicy.target(origin, "https://grapheneos.org/a/b.atom"))
        assertNotNull(FeedRedirectPolicy.target(origin, "https://GrapheneOS.org/x"))
    }

    @Test fun otherHostsAreNeverContacted() {
        assertNull(FeedRedirectPolicy.target(origin, "https://example.com/feed"))
        assertNull(FeedRedirectPolicy.target(origin, "https://www.grapheneos.org/releases.atom"))
        assertNull(FeedRedirectPolicy.target(origin, "https://grapheneos.org.evil.example/x"))
        assertNull(FeedRedirectPolicy.target(origin, "//evil.example/x"))
    }

    @Test fun downgradesAndCredentialsAreRefused() {
        assertNull(FeedRedirectPolicy.target(origin, "http://grapheneos.org/releases.atom"))
        assertNull(FeedRedirectPolicy.target(origin, "https://user:pw@grapheneos.org/x"))
    }

    @Test fun missingOrMalformedLocationIsRefused() {
        assertNull(FeedRedirectPolicy.target(origin, null))
        assertNull(FeedRedirectPolicy.target(origin, "  "))
        assertNull(FeedRedirectPolicy.target(origin, "ht!tp://bad host"))
    }
}
