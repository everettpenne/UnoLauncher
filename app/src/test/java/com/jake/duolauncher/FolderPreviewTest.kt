package com.jake.duolauncher

import org.junit.Assert.assertEquals
import org.junit.Test

class FolderPreviewTest {
    @Test fun smallFoldersUseTwoByTwo() {
        (2..4).forEach { assertEquals(4, FolderPreview.capacity(it)) }
        assertEquals(2, FolderPreview.columns(4))
        assertEquals(2, FolderPreview.columns(2))
    }

    @Test fun largerFoldersUseThreeByThreeCappedAtNine() {
        assertEquals(9, FolderPreview.capacity(5))
        assertEquals(9, FolderPreview.capacity(40))
        assertEquals(3, FolderPreview.columns(5))
        assertEquals(3, FolderPreview.columns(9))
    }

    @Test fun iconsAlwaysFitTheirRows() {
        assertEquals(true, 2 * FolderPreview.iconFraction(2) < 1f)
        assertEquals(true, 3 * FolderPreview.iconFraction(3) < 1f)
    }

    @Test fun folderBadgeSumsAndIgnoresNegatives() {
        assertEquals(0, FolderPreview.unread(emptyList()))
        assertEquals(7, FolderPreview.unread(listOf(3, 0, 4)))
        assertEquals(3, FolderPreview.unread(listOf(3, -2)))
    }
}
