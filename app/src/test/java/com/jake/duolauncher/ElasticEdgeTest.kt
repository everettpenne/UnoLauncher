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
}
