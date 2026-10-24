package com.jake.duolauncher

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jake.duolauncher.appearance.*

/** Rules for apps the user hid. Pure, so they are unit-tested. */
internal object HiddenApps {
    /** Hidden apps stay out of every list; typing one's exact name is the way to find it again. */
    fun <T> shown(apps: List<T>, hidden: Set<String>, query: String, id: (T) -> String, label: (T) -> String): List<T> {
        if (hidden.isEmpty()) return apps
        val exact = query.trim()
        return apps.filter { id(it) !in hidden || (exact.isNotEmpty() && label(it).equals(exact, ignoreCase = true)) }
    }
}

/** Settings list of hidden apps, each with a button to show it again. */
@Composable
internal fun HiddenAppsSetting(hidden: List<AppEntry>, onUnhide: (AppEntry) -> Unit) {
    Text(stringResource(com.jake.duolauncher.R.string.hidden_apps), style = MaterialTheme.typography.titleMedium)
    Text(stringResource(com.jake.duolauncher.R.string.hidden_apps_note), style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (hidden.isEmpty()) {
        Text(stringResource(com.jake.duolauncher.R.string.hidden_apps_none), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("hidden-apps-none"))
    }
    hidden.forEach { app ->
        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("hidden-app-${app.id}"), verticalAlignment = Alignment.CenterVertically) {
            Image(app.image, null, Modifier.size(40.dp).clip(Corner.icon))
            Text(app.label, Modifier.weight(1f).padding(start = 12.dp), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            GlassButton(onClick = { onUnhide(app) }) { Text(stringResource(com.jake.duolauncher.R.string.show_again)) }
        }
    }
}
