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
}
