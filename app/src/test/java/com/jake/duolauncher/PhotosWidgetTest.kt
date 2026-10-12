package com.jake.duolauncher

import com.jake.duolauncher.widgets.*

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotosWidgetTest {
    private val a = "content://media/picker/a/1"
    private val b = "content://media/picker/a/2"
    private val c = "content://media/picker/a/3"

    @Test fun parseKeepsOnlyContentUrisWithoutRepeatsAndCapsTheList() {
        assertEquals(listOf(a, b), PhotoGallery.parse("$a\n\nnot-a-uri\n$b\n$a\n  "))
        assertEquals(emptyList<String>(), PhotoGallery.parse(null))
        assertEquals(emptyList<String>(), PhotoGallery.parse("file:///sdcard/photo.jpg"))
        val many = (1..40).map { "content://media/picker/a/$it" }
        assertEquals(PhotoGallery.MAX_PHOTOS, PhotoGallery.parse(many.joinToString("\n")).size)
    }

    @Test fun photosRoundTripThroughStorage() {
        val photos = listOf(a, b, c)
        assertEquals(photos, PhotoGallery.parse(PhotoGallery.serialize(photos)))
        assertEquals("", PhotoGallery.serialize(emptyList()))
    }

    @Test fun addAllAppendsOnlyNewContentUrisUpToTheCap() {
        assertEquals(listOf(a, b), PhotoGallery.addAll(listOf(a), listOf(b, a, "not-a-uri")))
        val full = (1..PhotoGallery.MAX_PHOTOS).map { "content://media/picker/a/$it" }
        assertEquals(full, PhotoGallery.addAll(full, listOf(c)))
    }

    @Test fun theShownPictureTurnsWithEachIntervalAndWraps() {
        val interval = PhotoGallery.INTERVAL_SECONDS
        assertEquals(0, PhotoGallery.indexFor(3, 0))
        assertEquals(1, PhotoGallery.indexFor(3, interval))
        assertEquals(2, PhotoGallery.indexFor(3, 2 * interval))
        assertEquals("wraps back to the first", 0, PhotoGallery.indexFor(3, 3 * interval))
        assertEquals("part-way through an interval keeps the same picture", 1, PhotoGallery.indexFor(3, interval + 17))
        assertEquals("an empty list is always safe", 0, PhotoGallery.indexFor(0, 5 * interval))
        assertEquals("a clock before the epoch still lands inside the list", 2, PhotoGallery.indexFor(3, -interval))
    }

    @Test fun theRotationPeriodIsTheAdvertisedOne() {
        assertEquals(15L * 60L, PhotoGallery.INTERVAL_SECONDS)
    }

    @Test fun clearingEmptiesTheList() {
        assertTrue(PhotoGallery.parse(PhotoGallery.serialize(emptyList())).isEmpty())
    }
}
