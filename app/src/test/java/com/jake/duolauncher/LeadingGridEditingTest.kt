package com.jake.duolauncher

import com.jake.duolauncher.folders.*

import org.junit.Assert.*
import org.junit.Test

class LeadingGridEditingTest {
    @Test fun `signed leading addresses round trip without changing normal pages`() {
        assertEquals(-1, homeCellPage(-HOME_CELLS)); assertEquals(0, homeCellLocal(-HOME_CELLS))
        assertEquals(-1, homeCellPage(-1)); assertEquals(HOME_CELLS - 1, homeCellLocal(-1))
        assertEquals(-HOME_CELLS, homeCellIndex(-1, 0)); assertEquals(-1, homeCellIndex(-1, HOME_CELLS - 1))
        assertEquals(0, homeCellIndex(0, 0)); assertEquals(HOME_CELLS, homeCellIndex(1, 0))
        val layout = HomeLayout(listOf("screen1"), emptyList(), leadingSlots = List(HOME_CELLS) { if (it == HOME_CELLS - 1) "leading" else null })
        assertEquals("leading", layout.slotAt(-1)); assertEquals(-1, layout.indexOfShortcut("leading"))
        assertEquals("screen1", layout.slotAt(0)); assertEquals(1, layout.pageCount)
    }

    @Test fun `leading moves into empty cells directly and occupied insertion stays bounded`() {
        val first = -HOME_CELLS
        val leading = MutableList<String?>(HOME_CELLS) { null }.apply { this[0] = "a"; this[1] = "b"; this[HOME_CELLS - 1] = "z" }
        val before = HomeLayout(listOf("home"), emptyList(), leadingSlots = leading)
        val direct = dropApp(before, "a", DropTarget.Home(first + 2))
        assertNull(direct.slotAt(first)); assertEquals("a", direct.slotAt(first + 2)); assertEquals("b", direct.slotAt(first + 1))
        val inserted = dropApp(before, "new", DropTarget.Home(first + 1))
        assertEquals(listOf("a", "new", "b"), listOf(first, first + 1, first + 2).map(inserted::slotAt))
        assertEquals(listOf("home"), inserted.slots)
        val full = HomeLayout(listOf("home"), emptyList(), leadingSlots = List(HOME_CELLS) { "l$it" })
        assertSame(full, dropApp(full, "new", DropTarget.Home(first)))
    }

    @Test fun `cross-surface moves are atomic and never duplicate shortcuts`() {
        val first = -HOME_CELLS
        val before = HomeLayout(listOf("home"), listOf(null, null, null, null), leadingSlots = List(HOME_CELLS) { null })
        val toLeading = dropApp(before, "home", DropTarget.Home(first))
        assertEquals("home", toLeading.slotAt(first)); assertNull(toLeading.slotAt(0))
        val back = dropApp(toLeading, "home", DropTarget.Home(4))
        assertNull(back.slotAt(first)); assertEquals("home", back.slotAt(4))
        assertEquals(1, (back.leadingSlots + back.slots + back.dock).count { it == "home" })
    }

    @Test fun `leading widgets collide with leading apps using signed cells`() {
        val widget = WidgetPlacement(4, 26, -1, 0, 0, 2, 2)
        val first = -HOME_CELLS
        val leading = List<String?>(HOME_CELLS) { if (it == 2) "app" else null }
        val layout = HomeLayout(emptyList(), emptyList(), listOf(widget), leadingSlots = leading)
        assertEquals(setOf(first, first + 1, first + 4, first + 5), widget.coveredIndices())
        assertNull(widgetCandidate(layout, 5, first, 2, 2))
        // An app under the candidate is moved aside rather than blocking it.
        assertNotNull(widgetCandidate(layout, 5, first + 2, 1, 1))
        assertEquals(WidgetPlacement(5, EMPTY_WIDGET, -1, 2, 1, 1, 1), widgetCandidate(layout, 5, first + 6, 1, 1))
        assertEquals(widget.copy(column = 2), moveWidget(layout.copy(leadingSlots = List(HOME_CELLS) { null }), 4, first + 2).placement(4))
    }

    @Test fun `folders create dissolve and transfer on leading surface`() {
        val folder = FolderEntry("folder:00000000-0000-0000-0000-000000000008", "Pair", emptyList())
        val first = -HOME_CELLS
        val before = HomeLayout(listOf("b"), emptyList(), leadingSlots = List(HOME_CELLS) { if (it == 0) "a" else null })
        val created = createFolder(before, folder, "a", "b", first)
        assertEquals(folder.id, created.slotAt(first)); assertNull(created.slotAt(0))
        val extracted = removeAppFromFolder(created, folder.id, "a", DropTarget.Home(3))
        assertEquals("b", extracted.slotAt(first)); assertEquals("a", extracted.slotAt(3))
        assertTrue(extracted.folders.isEmpty())
    }
}
