package com.jake.duolauncher

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jake.duolauncher.appearance.GlassButton
import com.jake.duolauncher.appearance.GlassDialog

/**
 * A short "what's new" note, shown once after the launcher is updated. A fresh install does not see it (the first-run sheet is the
 * welcome); someone who updates sees the current version's few lines once and can dismiss it. The words live in strings.xml and are
 * rewritten with each release (see the release steps in docs/working-together.md).
 */
internal object WhatsNew {
    private const val PREFS = "whats_new"
    private const val SEEN = "seen"

    enum class Decision { SHOW, MARK_SEEN, NOTHING }

    /** The part of a version a note is written for: "0.46" for "0.46.1-uno01". A patch release does not show the note again. */
    fun noteKey(version: String): String = version.substringBefore('-').split('.').take(2).joinToString(".")

    /** [seen] is the version whose note was last shown or skipped (null if never). A fresh install is marked seen without showing. */
    fun decide(seen: String?, current: String, freshInstall: Boolean): Decision = when {
        current.isBlank() || current == "unknown" -> Decision.NOTHING
        seen != null && noteKey(seen) == noteKey(current) -> Decision.NOTHING
        freshInstall -> Decision.MARK_SEEN
        else -> Decision.SHOW
    }

    fun seen(context: Context): String? = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(SEEN, null)
    fun markSeen(context: Context, version: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(SEEN, version).apply()
    }

    /** Decides at start-up; marks a fresh install as seen and returns true when the note should be shown. */
    fun shouldShowAtStart(context: Context, version: String, freshInstall: Boolean): Boolean =
        when (decide(seen(context), version, freshInstall)) {
            Decision.SHOW -> true
            Decision.MARK_SEEN -> { markSeen(context, version); false }
            Decision.NOTHING -> false
        }
}

@Composable
internal fun WhatsNewDialog(version: String, onDone: () -> Unit) {
    GlassDialog(onDismissRequest = onDone, title = stringResource(com.jake.duolauncher.R.string.whats_new_title, version),
        modifier = Modifier.testTag("whats-new"),
        actions = { GlassButton(stringResource(com.jake.duolauncher.R.string.whats_new_done), onDone, prominent = true, tag = "whats-new-done") }) {
        listOf(com.jake.duolauncher.R.string.whats_new_1, com.jake.duolauncher.R.string.whats_new_2,
            com.jake.duolauncher.R.string.whats_new_3, com.jake.duolauncher.R.string.whats_new_4).forEach { id ->
            Row {
                Text("•", Modifier.padding(end = 10.dp), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(id), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
