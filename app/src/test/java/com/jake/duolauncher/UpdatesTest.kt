package com.jake.duolauncher

import org.junit.Assert.*
import org.junit.Test

class UpdatesTest {
    @Test fun versionNamesOrderNumericallyThenBySuffix() {
        assertTrue(compareVersionNames("0.16.10-uno01", "0.16.9-uno01") > 0)
        assertTrue(compareVersionNames("0.16.9-uno01", "0.16.9-uno01") == 0)
        assertTrue(compareVersionNames("0.16.9", "0.16.9-uno01") < 0)
        assertTrue(compareVersionNames("1.0.0", "0.16.9-uno01") > 0)
        assertTrue(compareVersionNames("0.17.0", "0.16.9-uno01") > 0)
        assertTrue(compareVersionNames("0.9.10", "0.9.9") > 0)
        assertTrue(compareVersionNames("garbage", "0.16.9-uno01") < 0)
    }

    @Test fun releasesJsonParsesApkAndChecksumUrls() {
        val json = """
            [
              {
                "tag_name": "v0.16.10-uno01",
                "name": "Uno Launcher 0.16.10-uno01",
                "published_at": "2026-10-20T01:02:03Z",
                "assets": [
                  {"name": "SHA256SUMS.txt", "browser_download_url": "https://example.com/sums"},
                  {"name": "UnoLauncher-0.16.10-uno01-release.apk", "browser_download_url": "https://example.com/apk"},
                  {"name": "UnoLauncher-0.16.10-uno01-source.tar.gz", "browser_download_url": "https://example.com/src"}
                ]
              },
              {
                "tag_name": "v0.16.9-uno01",
                "name": "Uno Launcher 0.16.9-uno01",
                "published_at": "2026-10-06T01:02:03Z",
                "assets": [{"name": "UnoLauncher-0.16.9-uno01-release.apk", "browser_download_url": "https://example.com/apk9"}]
              }
            ]
        """.trimIndent()
        val releases = parseReleases(json)
        assertEquals(2, releases.size)
        val newest = releases[0]
        assertEquals("0.16.10-uno01", newest.tag)
        assertEquals("https://example.com/apk", newest.apkUrl)
        assertEquals("https://example.com/sums", newest.sumsUrl)
        assertEquals("2026-10-20", newest.publishedAt)
        assertEquals("0.16.9-uno01", releases[1].tag)
        assertNull(releases[1].sumsUrl)
    }

    @Test fun checksumLinesResolveByApkName() {
        val sums = """
            662f5587d881b55445773ae74199b440a03b1019d908c05b8dd74faee68165be  UnoLauncher-0.16.1-uno01-release.apk
            c3cdbfda835e8a679ebf06b291d038b70c09a792318e800cdfe2a11752de56e2  UnoLauncher-0.16.1-uno01-source.tar.gz
        """.trimIndent()
        assertEquals("662f5587d881b55445773ae74199b440a03b1019d908c05b8dd74faee68165be",
            expectedShaFor(sums, "UnoLauncher-0.16.1-uno01-release.apk"))
        assertEquals("c3cdbfda835e8a679ebf06b291d038b70c09a792318e800cdfe2a11752de56e2",
            expectedShaFor(sums, "UnoLauncher-0.16.1-uno01-source.tar.gz"))
        assertNull(expectedShaFor(sums, "UnoLauncher-0.16.2-uno01-release.apk"))
    }
}
