package com.jake.duolauncher

import com.jake.duolauncher.folders.*

import org.junit.Assert.assertEquals
import org.junit.Test

class FolderPreviewTest {
    @Test fun everyFolderPreviewIsThreeByThreeLikeIos() {
        (2..40).forEach { assertEquals(9, FolderPreview.capacity(it)); assertEquals(3, FolderPreview.columns(it.coerceAtMost(9))) }
    }

    @Test fun iconsAlwaysFitTheirRows() {
        assertEquals(true, 3 * FolderPreview.iconFraction(3) < 1f)
    }

    @Test fun theOpenFolderPagesNineAtATime() {
        assertEquals(1, FolderPages.pageCount(2)); assertEquals(1, FolderPages.pageCount(9))
        assertEquals(2, FolderPages.pageCount(10)); assertEquals(3, FolderPages.pageCount(19))
        val ids = (1..11).map { "a$it" }
        assertEquals(ids.take(9), FolderPages.page(ids, 0))
        assertEquals(listOf("a10", "a11"), FolderPages.page(ids, 1))
        assertEquals(emptyList<String>(), FolderPages.page(ids, 2))
        assertEquals(0, FolderPages.pageOf(8)); assertEquals(1, FolderPages.pageOf(9)); assertEquals(0, FolderPages.pageOf(-3))
    }

    @Test fun thePanelIsSizedForTheFullestPage() {
        assertEquals(1, FolderPages.rows(2)); assertEquals(1, FolderPages.rows(3))
        assertEquals(2, FolderPages.rows(4)); assertEquals(2, FolderPages.rows(6))
        assertEquals(3, FolderPages.rows(7)); assertEquals(3, FolderPages.rows(30))
        assertEquals(1, FolderPages.rows(0))
    }

    @Test fun aNewFolderIsNamedAfterWhatIsInIt() {
        assertEquals("Social", FolderNaming.suggest(listOf(4, 4)))
        assertEquals("Games", FolderNaming.suggest(listOf(0, 0, 0)))
        assertEquals("Folder", FolderNaming.suggest(listOf(4, 0)))
        assertEquals("Folder", FolderNaming.suggest(listOf(null, null)))
        assertEquals("Folder", FolderNaming.suggest(listOf(-1, -1)))
        assertEquals("Folder", FolderNaming.suggest(listOf(4, null)))
        assertEquals("Folder", FolderNaming.suggest(emptyList()))
        assertEquals("Folder", FolderNaming.suggest(listOf(99, 99)))
    }

    @Test fun folderBadgeSumsAndIgnoresNegatives() {
        assertEquals(0, FolderPreview.unread(emptyList()))
        assertEquals(7, FolderPreview.unread(listOf(3, 0, 4)))
        assertEquals(3, FolderPreview.unread(listOf(3, -2)))
    }
}
