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
        assertNull("a bare built-in id cannot stack: it must carry its slot (unoCode)", StackRules.add(stacks, 1, -3))
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

    @Test fun unoMembersCarryTheirKindAndTheSlotThatKeepsTheirData() {
        for (slot in listOf(0, 7, 1234, 9_999)) for (id in listOf(CLOCK_WIDGET, DATE_WIDGET, -20, -36, -60)) {
            val code = StackRules.unoCode(id, slot)
            assertTrue(StackRules.isUno(code))
            assertEquals(id, StackRules.unoWidgetId(code))
            assertEquals(slot, StackRules.unoSlot(code))
        }
        assertFalse(StackRules.isUno(14))
        assertFalse(StackRules.isUno(-5))
        assertTrue(StackRules.isMember(0) && StackRules.isMember(StackRules.unoCode(-20, 3)))
        assertFalse(StackRules.isMember(-1))
    }

    @Test fun unoMembersStackStoreAndAreUniqueLikeAndroidOnes() {
        val note = StackRules.unoCode(-30, 4)
        val clock = StackRules.unoCode(CLOCK_WIDGET, 2)
        var stacks = StackRules.add(emptyMap(), 1, 10)!!
        stacks = StackRules.add(stacks, 1, note)!!
        stacks = StackRules.add(stacks, 1, clock)!!
        assertEquals(listOf(10, note, clock), stacks[1])
        assertNull("the same Uno widget twice", StackRules.add(stacks, 1, note))
        assertNull("in another stack", StackRules.add(stacks, 9, note))
        assertEquals(stacks, StackRules.parse(StackRules.serialize(stacks)))
        assertEquals(listOf(10, clock), StackRules.remove(stacks, 1, note)[1])
    }

    @Test fun stackedUnoWidgetsKeepTheirSlotsOnlyWhileTheStackIsPlaced() {
        val stacks = mapOf(1 to listOf(10, StackRules.unoCode(-30, 4), StackRules.unoCode(-31, 6)), 2 to listOf(StackRules.unoCode(-32, 8)))
        assertEquals(setOf(4, 6, 8), StackRules.memberSlots(stacks, setOf(1, 2, 3)))
        assertEquals(setOf(8), StackRules.memberSlots(stacks, setOf(2)))
        assertEquals(emptySet<Int>(), StackRules.memberSlots(stacks, setOf(3)))
        // Android ids still retain; Uno codes never collide with them.
        assertEquals(setOf(10), StackRules.retained(stacks, setOf(1)).filter { it >= 0 }.toSet())
    }
}
