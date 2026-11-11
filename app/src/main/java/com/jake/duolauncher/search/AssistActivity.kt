package com.jake.duolauncher.search

import android.os.Bundle
import androidx.activity.ComponentActivity

/**
 * The assistant gesture's way in, and nothing more.
 *
 * Android starts an activity, so this is the activity it starts: it hands the screen's text to [AssistOverlay], puts search on top of
 * whatever app is in front, and finishes at once. The window that remains belongs to the application, not to this activity, so there is no
 * second window for the keyboard or the status bar to fight with.
 */
class AssistActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AssistOverlay.show(applicationContext, intent.getStringExtra("uno_assist_text"))
        finish()
        overridePendingTransition(0, 0)
    }
}
