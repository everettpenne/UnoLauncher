package com.jake.duolauncher

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FolderDropTest {
    private val cell = Rect(0f, 0f, 100f, 120f)
    private fun region(app: String?, target: DropTarget = DropTarget.Home(3)) = DragRegion(target, cell, app, 0)
    private val source = DragRegion(DropTarget.Home(1), Rect(200f, 0f, 300f, 120f), "a", 0)
    private fun isFolder(id: String) = id.startsWith("folder:")

    @Test fun middleOfAnotherAppMakesAFolder() =
        assertEquals("b", folderDropTarget(region("b"), Offset(50f, 60f), source, ::isFolder))

    @Test fun nearTheEdgeIsOnlyAMove() {
        assertNull(folderDropTarget(region("b"), Offset(5f, 60f), source, ::isFolder))
        assertNull(folderDropTarget(region("b"), Offset(50f, 115f), source, ::isFolder))
    }

    @Test fun emptyCellOrSameAppIsAMove() {
        assertNull(folderDropTarget(region(null), Offset(50f, 60f), source, ::isFolder))
        assertNull(folderDropTarget(region("a"), Offset(50f, 60f), source, ::isFolder))
    }

    @Test fun foldersAndWidgetsNeverNest() {
        assertNull(folderDropTarget(region("folder:x"), Offset(50f, 60f), source, ::isFolder))
        assertNull(folderDropTarget(region("b"), Offset(50f, 60f), source.copy(appId = "folder:y"), ::isFolder))
        assertNull(folderDropTarget(region("b"), Offset(50f, 60f), source.copy(target = DropTarget.Widget(2)), ::isFolder))
    }

    @Test fun dockAndFolderRegionsAreNotHomeCells() {
        assertNull(folderDropTarget(region("b", DropTarget.Dock(0)), Offset(50f, 60f), source, ::isFolder))
        assertNull(folderDropTarget(null, Offset(50f, 60f), source, ::isFolder))
    }
}
