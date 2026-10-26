package com.jake.duolauncher

import org.junit.Assert.*
import org.junit.Test

/** Layouts saved on the six-row grid (pages of 24 cells) come to the seven-row grid (pages of 28) with every app and widget where it was. */
class SevenRowMigrationTest {
    @Test fun theGridIsSevenRowsOfFour() {
        assertEquals(7, GRID_ROWS); assertEquals(4, GRID_COLUMNS); assertEquals(28, HOME_CELLS)
        assertEquals(24, HOME_CELLS_BEFORE_SEVEN)
    }

    @Test fun eachPageKeepsItsFirstSixRowsAndGainsAnEmptySeventh() {
        // Page 0 has apps in its first and last old cells, page 1 in its first.
        val old = MutableList<String?>(25) { null }.apply { this[0] = "a"; this[23] = "z"; this[24] = "p1" }
        val migrated = restrideFromSixRows(old)
        assertEquals("a", migrated[0]); assertEquals("z", migrated[23])
        assertTrue((24 until 28).all { migrated[it] == null })
        assertEquals("p1", migrated[28])
        assertEquals(2, homePageCount(migrated.size))
    }

    @Test fun everyAppIsKeptOnceInTheSameRowAndColumn() {
        val old = List<String?>(72) { "app$it" }
        val migrated = restrideFromSixRows(old)
        old.forEachIndexed { index, id ->
            val page = index / 24; val local = index % 24
            assertEquals(id, migrated[page * HOME_CELLS + local])
            assertEquals(local / GRID_COLUMNS, (page * HOME_CELLS + local).let { homeCellLocal(it) } / GRID_COLUMNS)
        }
        assertEquals(old.size, migrated.filterNotNull().size)
    }

    @Test fun anEmptyOrShortListStaysAsSmallAsItWas() {
        assertTrue(restrideFromSixRows(emptyList()).isEmpty())
        assertEquals(listOf("only"), restrideFromSixRows(listOf("only")))
        assertEquals(listOf<String?>(null, "second"), restrideFromSixRows(listOf(null, "second")))
    }

    @Test fun theWidePageGainsFourEmptyCellsAtTheEnd() {
        val old = List<String?>(24) { if (it == 5) "x" else null }
        val migrated = restrideLeadingFromSixRows(old)
        assertEquals(HOME_CELLS, migrated.size); assertEquals("x", migrated[5])
        assertTrue((24 until 28).all { migrated[it] == null })
        assertThrows(IllegalArgumentException::class.java) { restrideLeadingFromSixRows(List(28) { null }) }
    }

    @Test fun aWidgetBelowTheOldGridMovesBelowTheNewOneAndOthersStayPut() {
        val below = WidgetPlacement(5, 101, 1, 0, 6, 4, 4)
        assertEquals(below.copy(row = GRID_ROWS), placementFromSixRows(below))
        val inside = WidgetPlacement(0, 101, 0, 0, 0, 2, 2)
        assertSame(inside, placementFromSixRows(inside))
        val bottomRow = WidgetPlacement(1, 101, 0, 0, 4, 4, 2)   // ends at the old bottom
        assertSame(bottomRow, placementFromSixRows(bottomRow))
        val leadingPanel = WidgetPlacement(2, INFO_WIDGET, -1, 0, 0, 4, 6)
        assertSame(leadingPanel, placementFromSixRows(leadingPanel))
    }

    @Test fun aMigratedBelowGridWidgetStillCountsAsTheBelowGridKind() {
        val moved = placementFromSixRows(WidgetPlacement(5, 101, 1, 0, 6, 4, 4))
        assertEquals(GRID_ROWS, moved.row)
        assertTrue(validBackupPlacement(moved.copy(slot = 5, page = 1)))
    }
}
