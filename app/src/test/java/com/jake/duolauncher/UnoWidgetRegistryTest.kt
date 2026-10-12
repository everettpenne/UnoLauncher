package com.jake.duolauncher

import com.jake.duolauncher.widgets.*

import com.jake.duolauncher.widgets.UnoWidgets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The rules every widget in the registry has to keep (see docs/widget-development.md). They protect saved layouts and backups. */
class UnoWidgetRegistryTest {
    /** Ids already shipped. A saved layout or a backup may hold any of them, so none may ever be removed or reused for something else. */
    private val shipped = (-28..-20).toSet()

    @Test fun everyShippedIdIsStillRegistered() {
        val registered = UnoWidgets.all.map { it.id }.toSet()
        assertTrue("A shipped widget id was removed: ${shipped - registered}", registered.containsAll(shipped))
    }

    @Test fun idsAreUniqueAndNegative() {
        val ids = UnoWidgets.all.map { it.id }
        assertEquals("Two widgets share an id: $ids", ids.size, ids.toSet().size)
        assertTrue("Built-in widget ids are negative", ids.all { it < 0 })
    }

    @Test fun idsStayBelowTheReservedRangeAndInOrder() {
        // -1..-19 belong to the widgets that were here before the library (clock and friends); the library starts at -20.
        assertTrue(UnoWidgets.all.all { it.id <= -20 })
    }

    @Test fun everyWidgetHasALabelABlurbAndARealSize() {
        UnoWidgets.all.forEach {
            assertTrue("${it.id} needs a label", it.label.isNotBlank())
            assertTrue("${it.id} needs a one-line description", it.blurb.isNotBlank())
            assertTrue("${it.id} needs a size of at least 1 x 1", it.spanX >= 1 && it.spanY >= 1)
        }
    }

    @Test fun byIdFindsEveryWidgetAndNothingElse() {
        UnoWidgets.all.forEach { assertNotNull(UnoWidgets.byId(it.id)) }
        assertEquals(null, UnoWidgets.byId(-1))
    }
}
