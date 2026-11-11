package com.jake.duolauncher.search

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.UserManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.jake.duolauncher.AppEntry
import com.jake.duolauncher.DuoTheme
import com.jake.duolauncher.ExtrasStore
import com.jake.duolauncher.FocusMode
import com.jake.duolauncher.LauncherModels
import com.jake.duolauncher.appearance.DuoAppearanceRuntime
import com.jake.duolauncher.feed.FeedStore
import com.jake.duolauncher.search.context.ContextEnv
import com.jake.duolauncher.widgets.UnoWidgets

/**
 * Search, floating over whatever app is in front, for the system assistant gesture.
 *
 * Home's search lives in [MainActivity], which is the Home activity: starting it brings the home task to the front and covers the app the
 * person was using. So the assistant gets its own translucent activity in its own task, drawing the same [Spotlight] panel — the same
 * results, the same Ask Qwen — and it finishes when the panel closes, which puts the app back exactly as it was.
 *
 * It is not the launcher: nothing here is saved, and closing it leaves no trace in recents.
 */
class AssistActivity : ComponentActivity() {
    private val model by lazy { LauncherModels.of(application) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val extras = ExtrasStore(applicationContext)
        val feeds = FeedStore(this, lifecycleScope)
        // What was on the screen when the gesture fired, if the app in front handed it over.
        val screenText = intent.getStringExtra("uno_assist_text")
        setContent {
            val state = model.state.collectAsStateWithLifecycle().value
            val extrasState = extras.state
            val feedState = feeds.state.collectAsStateWithLifecycle().value
            val assistantOn = extrasState.localAssistant
            val assistant = remember(assistantOn) {
                if (assistantOn && LocalAssistant.isAvailable(this@AssistActivity)) LocalAssistant(this@AssistActivity) else null
            }
            // The phone's facts, read when a question is asked; the screen is the newest of them and is only ever a quote.
            val contextEnv = remember(assistant, screenText) {
                assistant?.let {
                    ContextEnv(applicationContext, { state }, { extrasState }, { null }, { feedState.shownEntries }, { screenText })
                }
            }
            DuoTheme(DuoAppearanceRuntime.dark) {
                Box(Modifier.fillMaxSize()) {
                    Spotlight(
                        glass = null,
                        apps = state.apps,
                        hiddenApps = state.hiddenApps,
                        feedEntries = feedState.shownEntries,
                        contactsEnabled = extrasState.contactSearch,
                        handoff = rememberHandoffResolver(extrasState),
                        onLaunch = { app -> openApp(app); finish() },
                        onOpenFeedLink = { link -> openLink(link); finish() },
                        onOpenContact = { contact -> ContactsSearch.open(this@AssistActivity, contact); finish() },
                        onDismiss = { finish() },
                        // Drawn open at once (there is no island to grow out of). The field is focused by a tap rather than at open:
                        // in a freshly created translucent window, focusing it programmatically makes the panel's scroll responder
                        // answer a bring-into-view before it is placed, which throws and kills the launcher.
                        visible = true,
                        autoFocus = false,
                        progress = { 1f },
                        noteSlot = state.widgetPlacements.firstOrNull { it.id == UnoWidgets.NOTE }?.slot,
                        focusOn = { extras.state.focusOn },
                        onToggleFocus = {
                            val next = !extras.state.focusOn
                            extras.setFocusOn(next)
                            FocusMode.applyRinger(this@AssistActivity, next, extras.state.focusVibrate)
                        },
                        assistant = assistant,
                        contextEnv = contextEnv,
                    )
                }
            }
        }
    }

    /** Opens an app from the results, in its own profile, then gets out of the way. */
    private fun openApp(app: AppEntry) {
        runCatching {
            val user = getSystemService(UserManager::class.java).getUserForSerialNumber(app.userSerial)
                ?: throw IllegalStateException("Profile is unavailable")
            getSystemService(android.content.pm.LauncherApps::class.java).startMainActivity(app.component, user, null, null)
        }.onFailure {
            Toast.makeText(this, "${app.label} is unavailable.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openLink(link: String) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}
