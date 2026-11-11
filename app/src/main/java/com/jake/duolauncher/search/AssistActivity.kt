package com.jake.duolauncher.search

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.jake.duolauncher.MainActivity

/**
 * The assistant gesture's way in, and nothing more.
 *
 * Android starts an activity, so this is the activity it starts. With Uno's accessibility service on (or permission to draw over other
 * apps) it puts search over whatever app is in front ([AssistOverlay]) and finishes at once, so dismissing search leaves that app as it was. Without the permission it opens Home's
 * search instead, as the gesture always did, with the screen's text carried along either way.
 */
class AssistActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val screenText = intent.getStringExtra("uno_assist_text")
        if (AssistOverlay.canShow(this)) AssistOverlay.show(applicationContext, screenText)
        else runCatching {
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra("duo_destination", "spotlight").apply { if (!screenText.isNullOrBlank()) putExtra("uno_assist_text", screenText) })
        }
        finish()
        overridePendingTransition(0, 0)
    }
}
