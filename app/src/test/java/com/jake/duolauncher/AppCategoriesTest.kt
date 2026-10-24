package com.jake.duolauncher

import com.jake.duolauncher.search.AppCategories
import org.junit.Assert.assertEquals
import org.junit.Test

class AppCategoriesTest {
    private val titles = mapOf(0 to "Games", 4 to "Social", 6 to "Maps & Navigation")
    private fun group(apps: List<Pair<String, Int>>) =
        AppCategories.group(apps, { it.second }, { titles[it] }, { it.first }, other = "Other").mapValues { (_, v) -> v.map { it.first } }

    @Test fun biggestCategoriesFirstAndUndeclaredAppsLast() {
        val g = group(listOf("Signal" to 4, "Chess" to 0, "Maps" to 6, "Notes" to AppCategories.UNDEFINED, "Go" to 0, "Molly" to 4, "Sudoku" to 0))
        assertEquals(listOf("Games", "Social", "Maps & Navigation", "Other"), g.keys.toList())
        assertEquals(listOf("Chess", "Go", "Sudoku"), g["Games"])
    }

    @Test fun aCategoryAndroidCannotNameGoesUnderOther() {
        assertEquals(mapOf("Other" to listOf("Odd")), group(listOf("Odd" to 42)))
    }
}
