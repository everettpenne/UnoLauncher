package com.jake.duolauncher

import androidx.compose.ui.res.stringResource
import com.jake.duolauncher.island.*
import com.jake.duolauncher.appearance.*

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
internal fun LayoutRestorePreview(preview: LayoutImportPreview, onRestore: () -> Unit, onCancel: () -> Unit) {
    GlassAlertDialog(onDismissRequest = onCancel, modifier = Modifier.testTag("layout-restore-preview"),
        title = { Text(stringResource(com.jake.duolauncher.R.string.review_restored_layout)) }, text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("${preview.appCount} apps · ${preview.folderCount} folders · ${preview.widgetCount} widgets")
                if (preview.layout.leadingSlots.any { it != null } || preview.layout.widgetPlacements.any { it.page == -1 })
                    Text(stringResource(com.jake.duolauncher.R.string.includes_your_unfolded_only_page), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(com.jake.duolauncher.R.string.this_also_restores_icon_layout_labels))
                preview.settings?.let { Text("Also restores ${it.count} Uno settings (island, keyboard, appearance, and panel choices). Location and debug options are never included.") }
                Text(stringResource(com.jake.duolauncher.R.string.widget_stacks_are_not_included_their), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(com.jake.duolauncher.R.string.your_selected_launcher_background_photo_is),
                    style = MaterialTheme.typography.bodySmall)
                if (preview.missingApps.isNotEmpty()) {
                    Text("Unavailable apps (${preview.missingApps.size})", style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.error)
                    preview.missingApps.forEach { saved ->
                        val label = saved.substringAfterLast('(').removeSuffix(")").takeIf { it.isNotBlank() } ?: "Unavailable app"
                        Text("• $label — its saved position will stay empty")
                    }
                }
                if (preview.profileIssues.isNotEmpty()) {
                    Text(stringResource(com.jake.duolauncher.R.string.profile_attention), style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.error)
                    preview.profileIssues.forEach { Text("• $it") }
                }
                val reconnect = preview.layout.widgetPlacements.count { it.id == NEEDS_BINDING_WIDGET }
                if (reconnect > 0) Text("$reconnect widget${if (reconnect == 1) "" else "s"} will keep their saved space and ask to reconnect after restore.")
                Text(stringResource(com.jake.duolauncher.R.string.nothing_changes_until_you_choose_restore), style = MaterialTheme.typography.bodySmall)
            }
        }, confirmButton = { GlassButton(prominent = true, onClick = onRestore, modifier = Modifier.testTag("layout-restore-apply")) { Text(stringResource(com.jake.duolauncher.R.string.restore)) } },
        dismissButton = { GlassButton(onClick = onCancel, modifier = Modifier.testTag("layout-restore-cancel")) { Text(stringResource(com.jake.duolauncher.R.string.cancel)) } })
}
