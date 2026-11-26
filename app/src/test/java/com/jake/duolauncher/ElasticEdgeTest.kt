package com.jake.duolauncher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ElasticEdgeTest {
    private val delta = 0.001f

    @Test fun `at rest the resisted offset is zero`() {
        assertEquals(0f, ElasticEdgeMath.resist(0f, 100f), 0f)
    }

    @Test fun `a pull of one viewport resists to the iOS constant`() {
        // (1 - 1/(0.55 + 1)) * 100 ≈ 35.48: pulling a full viewport moves the edge only a third of it.
        assertEquals(35.484f, ElasticEdgeMath.resist(100f, 100f), delta)
    }

    @Test fun `resistance is diminishing and never exceeds the viewport`() {
        val first = ElasticEdgeMath.resist(100f, 100f)
        val second = ElasticEdgeMath.resist(200f, 100f)
        val far = ElasticEdgeMath.resist(10_000f, 100f)
        assertTrue("further pull resists more", second > first)
        assertTrue("a huge pull approaches but never passes the viewport", far < 100f)
        assertTrue(far > 90f)
    }

    @Test fun `sign follows the drag direction`() {
        assertEquals(-ElasticEdgeMath.resist(50f, 100f), ElasticEdgeMath.resist(-50f, 100f), delta)
    }

    @Test fun `a zero or negative dimension yields no offset`() {
        assertEquals(0f, ElasticEdgeMath.resist(50f, 0f), 0f)
        assertEquals(0f, ElasticEdgeMath.resist(50f, -10f), 0f)
    }

    @Test fun `unresist is the inverse of resist`() {
        val dim = 100f
        for (d in listOf(0f, 10f, 50f, 100f, 250f, 1000f, -30f, -500f)) {
            assertEquals(d, ElasticEdgeMath.unresist(ElasticEdgeMath.resist(d, dim), dim), 1.5f)
        }
        assertEquals(0f, ElasticEdgeMath.unresist(50f, 0f), 0f)
    }

    @Test fun `a re-grab during the spring-back continues from the visible offset`() {
        val dim = 100f
        // The offset part-way through the spring-back (a full-viewport pull, then released).
        val midOffset = ElasticEdgeMath.resist(100f, dim)
        // The finger lands again and pulls a further 10px the same way: the curve re-anchors on the visible
        // offset, so the edge keeps moving out instead of snapping back to the resisted value of a fresh 10px.
        val next = ElasticEdgeMath.resist(ElasticEdgeMath.unresist(midOffset, dim) + 10f, dim)
        assertTrue("continues outward: $next > $midOffset", next > midOffset)
        assertTrue("stays under the viewport", next < dim)
        // And a pull the other way recedes smoothly from the same place.
        val back = ElasticEdgeMath.resist(ElasticEdgeMath.unresist(midOffset, dim) - 10f, dim)
        assertTrue("recedes smoothly: $back < $midOffset", back < midOffset)
    }

    @Test fun `a drag back towards the content lets the band go slack before anything scrolls`() {
        val band = ElasticOverscrollState { false }
        // Pulled past the bottom: the band is stretched upwards (negative).
        band.applyDrag(-300f, 1000f)
        val stretched = band.offset
        assertTrue(stretched < 0f)
        // A small move back is all taken by the band; a pull further the same way is not its business here.
        assertEquals(10f, band.relax(10f), delta)
        assertEquals(stretched + 10f, band.offset, delta)
        assertEquals(0f, band.relax(-25f), 0f)
        // A big move back takes only what brings the band to rest, and leaves the rest to scroll the page.
        val left = -band.offset
        assertEquals(left, band.relax(500f), delta)
        assertEquals(0f, band.offset, 0f)
        assertEquals(0f, band.relax(40f), 0f)
    }

    @Test fun `a fling throws the band by a capped share of its speed`() {
        assertEquals(0f, ElasticEdgeMath.flingShare(0f), 0f)
        assertEquals(-180f, ElasticEdgeMath.flingShare(-1000f), delta)
        assertEquals(ElasticEdgeMath.FLING_MAX, ElasticEdgeMath.flingShare(50_000f), 0f)
        assertEquals(-ElasticEdgeMath.FLING_MAX, ElasticEdgeMath.flingShare(-50_000f), 0f)
        assertEquals(125f, ElasticEdgeMath.flingLimit(1000f), 0f)
        assertEquals(0f, ElasticEdgeMath.flingLimit(0f), 0f)
    }
}
