package com.jake.duolauncher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LargeFolderRulesTest {
    @Test fun roundTripsThroughStorage() {
        val folders = mapOf(3 to LargeFolder("Work", listOf("a", "b")), 7 to LargeFolder("Fun", emptyList()))
        assertEquals(folders, LargeFolderRules.parse(LargeFolderRules.serialize(folders)))
    }

    @Test fun damagedStorageIsEmptyNotFatal() {
        assertEquals(emptyMap<Int, LargeFolder>(), LargeFolderRules.parse(null))
        assertEquals(emptyMap<Int, LargeFolder>(), LargeFolderRules.parse("not json"))
        assertEquals(mapOf(2 to LargeFolder("Folder", listOf("x"))), LargeFolderRules.parse("""{"2":{"a":["x","x",""]},"zz":{},"4":5}"""))
    }

    @Test fun toggleAddsRemovesAndRefusesWhenFull() {
        val one = LargeFolderRules.toggle(LargeFolder(), "a")!!
        assertEquals(listOf("a"), one.appIds)
        assertEquals(emptyList<String>(), LargeFolderRules.toggle(one, "a")!!.appIds)
        val full = LargeFolder("F", List(LargeFolderRules.MAX_APPS) { "app$it" })
        assertNull(LargeFolderRules.toggle(full, "extra"))
        assertEquals(LargeFolderRules.MAX_APPS - 1, LargeFolderRules.toggle(full, "app3")!!.appIds.size)
    }

    @Test fun renameIsBounded() {
        assertEquals(LargeFolderRules.MAX_TITLE, LargeFolderRules.rename(LargeFolder(), "x".repeat(100)).title.length)
    }

    @Test fun pruneDropsFoldersWhoseWidgetIsGone() {
        val folders = mapOf(1 to LargeFolder("A"), 2 to LargeFolder("B"))
        assertEquals(setOf(2), LargeFolderRules.prune(folders, setOf(2, 9)).keys)
    }

    @Test fun biggerFacesHoldMoreIcons() {
        val small = LargeFolderRules.capacity(160f, 160f)
        val wide = LargeFolderRules.capacity(330f, 160f)
        val tall = LargeFolderRules.capacity(160f, 330f)
        assertTrue(wide.second > small.second)
        assertTrue(tall.second > small.second)
        assertTrue(small.first >= 1 && small.second >= 1)
        assertEquals(1 to 1, LargeFolderRules.capacity(10f, 10f))
    }

    @Test fun thinFacesGetCompactIconsAndNoTitle() {
        val wide = LargeFolderRules.metrics(165f, 85f)   // 2 x 1
        val tall = LargeFolderRules.metrics(80f, 190f)   // 1 x 2
        val normal = LargeFolderRules.metrics(165f, 190f) // 2 x 2
        assertTrue(!wide.titled && !tall.titled && normal.titled)
        assertTrue(wide.icon < normal.icon)
        assertTrue("2 x 1 holds at least 3 icons", LargeFolderRules.capacity(165f, 85f).second >= 3)
        assertTrue("1 x 2 holds at least 3 icons", LargeFolderRules.capacity(80f, 190f).second >= 3)
    }
}
