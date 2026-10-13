package com.jake.duolauncher

/** One line of the "what comes back" list: [status] decides the mark shown beside it. */
internal data class BackupLine(val label: String, val detail: String, val status: Status) {
    enum class Status { OK, ATTENTION, NOTE }
}

/**
 * Completeness checks for layout backups. Before a restore, [restoreSummary] says what the file will bring back and what it
 * cannot (an app that is not installed, a widget that must be reconnected, what a backup never carries). After a save,
 * [verifyExport] reads the file back the way a restore would and reports anything that did not survive the round trip, so a
 * backup that would silently lose something is noticed when it is made, not when it is needed.
 */
internal object BackupCheck {
    private fun plural(count: Int, one: String, many: String = "${one}s") = "$count ${if (count == 1) one else many}"

    fun restoreSummary(preview: LayoutImportPreview): List<BackupLine> {
        val lines = mutableListOf<BackupLine>()
        lines += BackupLine("Home pages", plural(preview.layout.pageCount, "page"), BackupLine.Status.OK)
        lines += if (preview.missingApps.isEmpty()) BackupLine("Apps", plural(preview.appCount, "app") + " placed", BackupLine.Status.OK)
        else BackupLine("Apps", "${plural(preview.appCount, "app")} placed; ${preview.missingApps.size} not installed on this phone and left empty", BackupLine.Status.ATTENTION)
        lines += BackupLine("Folders", plural(preview.folderCount, "folder"), BackupLine.Status.OK)
        val reconnect = preview.layout.widgetPlacements.count { it.id == NEEDS_BINDING_WIDGET }
        lines += if (reconnect == 0) BackupLine("Widgets", plural(preview.widgetCount, "widget"), BackupLine.Status.OK)
        else BackupLine("Widgets", "${plural(preview.widgetCount, "widget")}; $reconnect will ask to reconnect", BackupLine.Status.ATTENTION)
        lines += when {
            preview.hiddenApps == null -> BackupLine("Hidden apps", "Not in this backup; your current hidden apps stay as they are", BackupLine.Status.NOTE)
            preview.hiddenUnavailable > 0 -> BackupLine("Hidden apps", "${preview.hiddenApps.size} restored; ${preview.hiddenUnavailable} not installed here", BackupLine.Status.ATTENTION)
            else -> BackupLine("Hidden apps", plural(preview.hiddenApps.size, "app"), BackupLine.Status.OK)
        }
        lines += preview.settings?.let { BackupLine("Settings", plural(it.count, "Uno setting"), BackupLine.Status.OK) }
            ?: BackupLine("Settings", "Not in this backup; your current settings stay as they are", BackupLine.Status.NOTE)
        lines += BackupLine("Not saved by backups", "Widget stacks and your background photo", BackupLine.Status.NOTE)
        return lines
    }

    /** What did not survive writing [raw] and reading it back, as plain sentences; empty means the backup is complete. */
    fun verifyExport(state: LauncherState, raw: String, scope: String): List<String> {
        val read = runCatching { decodeLayoutBackup(raw, state.apps, state.profiles, scope) }
            .getOrElse { return listOf("the file could not be read back (${it.message})") }
        val before = state.layout; val after = read.layout
        val problems = mutableListOf<String>()
        if (read.missingApps.isNotEmpty()) problems += "${plural(read.missingApps.size, "app")} could not be matched when read back"
        if (normalizeHomeSlots(before.slots) != normalizeHomeSlots(after.slots)) problems += "the Home apps differ"
        if (before.slotsForPage(-1) != after.slotsForPage(-1)) problems += "the unfolded-only page differs"
        if (before.dock != after.dock) problems += "the dock differs"
        if (before.folders != after.folders) problems += "the folders differ"
        fun shape(p: WidgetPlacement) = listOf(p.slot, p.page, p.column, p.row, p.spanX, p.spanY)
        if (before.widgetPlacements.map(::shape).sortedBy { it[0] } != after.widgetPlacements.map(::shape).sortedBy { it[0] }) problems += "the widgets differ"
        if (before.pageCount != after.pageCount) problems += "the number of Home pages differs (${before.pageCount} saved, ${after.pageCount} read back)"
        val hidden = state.hiddenApps.filter { id -> state.apps.any { it.id == id } }.toSet()
        if (read.hiddenApps != hidden) problems += "the hidden apps differ"
        if (read.settings == null) problems += "the settings were not saved"
        return problems
    }

    fun savedSummary(state: LauncherState): String {
        val layout = state.layout
        return "${plural(layout.pageCount, "page")}, ${plural(state.hiddenApps.size, "hidden app")}, ${plural(layout.folders.size, "folder")}, ${plural(layout.widgetPlacements.size, "widget")}"
    }
}
