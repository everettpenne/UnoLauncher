package com.jake.duolauncher

import com.jake.duolauncher.widgets.*

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChecklistWidgetTest {
    private fun item(text: String, done: Boolean) = Checklist.Item(text, done)

    @Test fun parseReadsTheDoneFlagAndWords() {
        assertEquals(listOf(item("Buy milk", true), item("Call Sam", false)),
            Checklist.parse("1|Buy milk\n0|Call Sam"))
        assertEquals(emptyList<Checklist.Item>(), Checklist.parse(null))
        assertEquals("malformed lines are skipped", emptyList<Checklist.Item>(), Checklist.parse("Buy milk\nx|no flag"))
    }

    @Test fun parseCapsTheList() {
        val many = (1..20).joinToString("\n") { "0|Task $it" }
        assertEquals(Checklist.MAX_ITEMS, Checklist.parse(many).size)
    }

    @Test fun itemsRoundTripThroughStorage() {
        val items = listOf(item("Buy milk", true), item("Call Sam", false))
        assertEquals(items, Checklist.parse(Checklist.serialize(items)))
    }

    @Test fun toggleFlipsOnlyTheTappedTask() {
        val items = listOf(item("a", false), item("b", true))
        assertEquals(listOf(item("a", true), item("b", true)), Checklist.toggle(items, 0))
        assertEquals("out of range is a no-op", items, Checklist.toggle(items, 5))
    }

    @Test fun addRejectsBlankRepeatAndFull() {
        val items = listOf(item("Buy milk", false))
        assertEquals(items, Checklist.add(items, "   "))
        assertEquals("case-insensitive repeat", items, Checklist.add(items, "buy MILK"))
        assertEquals(listOf(item("Buy milk", false), item("Call Sam", false)), Checklist.add(items, "Call Sam"))
        val full = (1..Checklist.MAX_ITEMS).map { item("Task $it", false) }
        assertEquals(full, Checklist.add(full, "One more"))
    }

    @Test fun addTidiesNewlinesAndBars() {
        assertEquals(listOf(item("Buy milk and bread", false)), Checklist.add(emptyList(), "Buy milk\nand|bread"))
    }

    @Test fun clearDoneAndRemoveAtWork() {
        val items = listOf(item("a", true), item("b", false), item("c", true))
        assertEquals(listOf(item("b", false)), Checklist.clearDone(items))
        assertEquals(listOf(item("a", true), item("c", true)), Checklist.removeAt(items, 1))
    }

    @Test fun doneCountCounts() {
        assertEquals(1 to 3, Checklist.doneCount(listOf(item("a", true), item("b", false), item("c", false))))
        assertTrue(Checklist.doneCount(emptyList()) == (0 to 0))
    }
}
