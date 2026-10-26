package com.jake.duolauncher

import android.graphics.Bitmap
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** A layout backup made before the seven-row grid (version 3: pages of 24 cells) imports with every app and widget where it was. */
class SevenRowBackupInstrumentedTest {
    private val a = "com.example.a/com.example.a.Main"
    private val b = "com.example.b/com.example.b.Main"
    private val c = "com.example.c/com.example.c.Main"
    private val personal = AppProfile(0, "Personal", true, false, false, true, true)
    private fun app(id: String) = AppEntry(id, id.substringBefore('/'), Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888))

    private fun versionThreeBackup(version: Int, cells: Int, widgetRow: Int): String {
        val slots = JSONArray().apply { repeat(cells * 2 + 1) { put(JSONObject.NULL) } }
        slots.put(0, a); slots.put(cells - 1, b); slots.put(cells, c)
        return JSONObject().put("version", version).put("sourceScope", "scope")
            .put("apps", JSONArray().apply { listOf(a, b, c).forEach { id -> put(JSONObject().put("id", id).put("label", id).put("component", id)
                .put("userSerial", 0).put("profileLabel", "Personal").put("work", false)) } })
            .put("homeSlots", slots).put("leadingSlots", JSONArray().apply { repeat(cells) { put(JSONObject.NULL) } })
            .put("folders", JSONArray()).put("dock", JSONArray().apply { repeat(4) { put(JSONObject.NULL) } })
            .put("widgets", JSONArray().put(JSONObject().put("slot", 5).put("builtinId", DATE_WIDGET).put("page", 1).put("column", 0)
                .put("row", widgetRow).put("spanX", 4).put("spanY", 4)))
            .put("compact", preset()).put("expanded", preset())
            .put("labels", true).put("googleSearch", true).put("verticalStatus", true).toString()
    }

    private fun preset() = JSONObject().put("iconSize", 66.0).put("rowGap", 8.0).put("dockWidth", 68.0).put("dockPosition", .56).put("dockAlignToGrid", true)

    @Test fun aSixRowBackupKeepsEveryAppInItsCell() {
        val preview = decodeLayoutBackup(versionThreeBackup(3, 24, widgetRow = 6), listOf(app(a), app(b), app(c)), listOf(personal), "scope")
        val layout = preview.layout
        assertEquals(a, layout.slotAt(0))
        assertEquals("the last cell of page 0", b, layout.slotAt(HOME_CELLS_BEFORE_SEVEN - 1))
        assertEquals("the first cell of page 1 is now at the start of the 28-cell page", c, layout.slotAt(homeCellIndex(1, 0)))
        assertNull("nothing leaks into the new seventh row", layout.slotAt(HOME_CELLS_BEFORE_SEVEN))
        assertEquals(3, preview.appCount)
    }

    @Test fun aSixRowBackupsBelowGridWidgetMovesBelowTheNewGrid() {
        val preview = decodeLayoutBackup(versionThreeBackup(3, 24, widgetRow = 6), listOf(app(a), app(b), app(c)), listOf(personal), "scope")
        assertEquals(GRID_ROWS, preview.layout.widgetPlacements.single().row)
    }

    @Test fun aSevenRowBackupIsTakenAsItIs() {
        val preview = decodeLayoutBackup(versionThreeBackup(4, HOME_CELLS, widgetRow = GRID_ROWS), listOf(app(a), app(b), app(c)), listOf(personal), "scope")
        val layout = preview.layout
        assertEquals(a, layout.slotAt(0)); assertEquals(b, layout.slotAt(HOME_CELLS - 1)); assertEquals(c, layout.slotAt(HOME_CELLS))
        assertEquals(GRID_ROWS, layout.widgetPlacements.single().row)
    }

    @Test fun aSixRowBackupWithTheWrongWidePageSizeIsRefused() {
        assertThrows(IllegalArgumentException::class.java) {
            decodeLayoutBackup(versionThreeBackup(3, HOME_CELLS, widgetRow = 6), listOf(app(a), app(b), app(c)), listOf(personal), "scope")
        }
    }
}
