package com.jake.duolauncher

import org.junit.Assert.*
import org.junit.Test

class StackRulesTest {
    @Test fun storageRoundTripsAndToleratesJunk() {
        val stacks = mapOf(2 to listOf(11, 12), 7 to listOf(30))
        assertEquals(stacks, StackRules.parse(StackRules.serialize(stacks)))
        assertEquals(mapOf(2 to listOf(11)), StackRules.parse("2:11;bad;x:3;4:;5:-1,abc"))
        assertEquals(emptyMap<Int, List<Int>>(), StackRules.parse(null))
        assertEquals("", StackRules.serialize(mapOf(1 to emptyList())))
    }

    @Test fun aWidgetCanBeInOneStackOnceAndStacksAreCapped() {
        var stacks = emptyMap<Int, List<Int>>()
        stacks = StackRules.add(stacks, 1, 10)!!
        assertNull("same widget twice", StackRules.add(stacks, 1, 10))
        assertNull("same widget in another stack", StackRules.add(stacks, 2, 10))
        assertNull("built-in widgets (negative ids) cannot stack", StackRules.add(stacks, 1, -3))
        repeat(StackRules.MAX_MEMBERS - 1) { stacks = StackRules.add(stacks, 1, 20 + it)!! }
        assertNull("stack is full", StackRules.add(stacks, 1, 99))
    }

    @Test fun removingTheLastMemberEmptiesTheStack() {
        val stacks = mapOf(1 to listOf(10, 11))
        assertEquals(mapOf(1 to listOf(11)), StackRules.remove(stacks, 1, 10))
        assertEquals(emptyMap<Int, List<Int>>(), StackRules.remove(StackRules.remove(stacks, 1, 10), 1, 11))
    }

    @Test fun onlyStacksWhoseBaseIsStillPlacedKeepTheirWidgets() {
        val stacks = mapOf(1 to listOf(10, 11), 2 to listOf(20))
        assertEquals(setOf(10, 11), StackRules.retained(stacks, setOf(1, 5)))
        assertEquals(emptySet<Int>(), StackRules.retained(stacks, emptySet()))
    }

    @Test fun pageIndexStaysInsideTheStack() {
        assertEquals(0, StackRules.clampPage(-1, 3))
        assertEquals(3, StackRules.clampPage(9, 3))
        assertEquals(0, StackRules.clampPage(2, 0))
    }
}
