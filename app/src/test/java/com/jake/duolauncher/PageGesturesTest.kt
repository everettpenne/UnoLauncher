package com.jake.duolauncher

import org.junit.Assert.assertEquals
import org.junit.Test

class PageGesturesTest {
    private val count = 5
    private val threshold = 250f

    @Test fun `a slow small drag settles back on its anchor`() {
        assertEquals(2, releasePage(2f, 2, count, 0f, threshold))
        assertEquals(2, releasePage(2.05f, 2, count, 0f, threshold))
    }

    @Test fun `a slow large drag moves one page`() {
        assertEquals(3, releasePage(2.4f, 2, count, 0f, threshold))
        assertEquals(1, releasePage(1.6f, 2, count, 0f, threshold))
    }

    @Test fun `a fast small flick turns the page`() {
        // A tiny 0.05 drag with a strong leftward fling commits the adjacent page.
        assertEquals(3, releasePage(2.05f, 2, count, -300f, threshold))
        // A strong rightward fling from a tiny drag goes the other way.
        assertEquals(1, releasePage(1.95f, 2, count, 300f, threshold))
    }

    @Test fun `release is bounded to one adjacent page`() {
        // At the first page, a leftward fling can only reach page 1.
        assertEquals(1, releasePage(0f, 0, count, -400f, threshold))
        // At the last page, a rightward fling turns back to the previous page (there is no page beyond it).
        assertEquals(count - 2, releasePage((count - 1).toFloat(), count - 1, count, 400f, threshold))
    }

    @Test fun `boundedPagePosition clamps to the anchor's neighbours`() {
        assertEquals(2f, boundedPagePosition(2f, 1, 3), 0f)
        assertEquals(0f, boundedPagePosition(-1f, 0, 3), 0f)
        assertEquals(2f, boundedPagePosition(9f, 1, 3), 0f)
        assertEquals(4f, boundedPagePosition(5f, 4, 5), 0f)
    }
}
