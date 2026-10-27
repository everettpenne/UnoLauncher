package com.jake.duolauncher

import org.junit.Assert.*
import org.junit.Test

class LauncherAliasTest {
    private val seekur = "com.everettpenne.seekur"
    private fun entry(alias: String, serial: Long = 0) = Triple("$seekur/$seekur.$alias", seekur, serial)

    @Test fun anAppThatSwitchedItsIconKeepsItsPlace() {
        val old = "$seekur/$seekur.IconSandworm"
        val moves = launcherAliasSuccessors(listOf(old, "other/.Main"), setOf("other/.Main", "$seekur/$seekur.IconSeekur"), listOf(entry("IconSeekur")), 0)
        assertEquals(mapOf(old to "$seekur/$seekur.IconSeekur"), moves)
    }

    @Test fun anAppThatIsStillThereIsLeftAlone() {
        val id = "$seekur/$seekur.IconSandworm"
        assertTrue(launcherAliasSuccessors(listOf(id), setOf(id), listOf(entry("IconSeekur")), 0).isEmpty())
    }

    @Test fun twoPossibleSuccessorsAreNotGuessed() {
        val old = "$seekur/$seekur.IconSandworm"
        assertTrue(launcherAliasSuccessors(listOf(old), setOf(), listOf(entry("IconSeekur"), entry("IconPeekBlue")), 0).isEmpty())
    }

    @Test fun anEntryAlreadySavedElsewhereIsNotTakenAsASuccessor() {
        val old = "$seekur/$seekur.IconSandworm"; val other = "$seekur/$seekur.IconSeekur"
        assertTrue(launcherAliasSuccessors(listOf(old, other), setOf(other), listOf(entry("IconSeekur")), 0).isEmpty())
    }

    @Test fun anotherUsersAppIsNotASuccessor() {
        val old = "$seekur/$seekur.IconSandworm"
        assertTrue(launcherAliasSuccessors(listOf(old), setOf(), listOf(entry("IconSeekur", serial = 10)), 0).isEmpty())
        assertEquals(1, launcherAliasSuccessors(listOf("duo.profile.app:10:$old".let { old }), setOf(), listOf(entry("IconSeekur")), 0).size)
    }
}
