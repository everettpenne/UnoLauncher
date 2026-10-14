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

    private val fid = "folder:" + java.util.UUID(0, 7)

    @Test fun anAppHeldOverAFolderCellIsHeldOverTheFolder() {
        val hit = folderCellAsFolder(region(fid), Offset(50f, 100f), source)
        assertEquals(DropTarget.Folder(fid), hit?.target)
    }

    @Test fun theEdgeOfAFolderCellIsStillAMove() {
        assertNull(folderCellAsFolder(region(fid), Offset(3f, 60f), source))
        assertNull(folderCellAsFolder(region(fid), Offset(50f, 119f), source))
    }

    @Test fun foldersWidgetsAndPlainCellsDoNotQualify() {
        assertNull(folderCellAsFolder(region("b"), Offset(50f, 60f), source))
        assertNull(folderCellAsFolder(region(fid), Offset(50f, 60f), DragRegion(DropTarget.Home(1), cell, fid, 0)))
        assertNull(folderCellAsFolder(region(fid), Offset(50f, 60f), DragRegion(DropTarget.Widget(2), cell, null, 0, widgetId = 5)))
        assertNull(folderCellAsFolder(region(fid, DropTarget.Dock(0)), Offset(50f, 60f), source))
        assertNull(folderCellAsFolder(region(fid), Offset(50f, 60f), DragRegion(DropTarget.Library("a"), cell, "a", 0, folderId = fid)))
    }
}
