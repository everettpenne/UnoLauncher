package com.jake.duolauncher.search

import android.app.KeyguardManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.jake.duolauncher.MainActivity

/**
 * The assistant gesture's way in, and nothing more.
 *
 * Android starts an activity, so this is the activity it starts. With Uno's accessibility service on (or permission to draw over other
 * apps) it puts search over whatever app is in front ([AssistOverlay]) and finishes at once, so dismissing search leaves that app as it was.
 * Without either it opens Home's search instead, as the gesture always did, with the screen's text carried along either way.
 *
 * Never over the lock screen: search shows apps and contacts, and Ask Qwen can read the calendar and notifications, so with the phone
 * locked the gesture first asks Android to unlock (PIN, pattern or fingerprint) and opens search only once it is unlocked. Cancelled, nothing opens.
 */
class AssistActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val screenText = intent.getStringExtra("uno_assist_text")
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (keyguard?.isKeyguardLocked == true) {
            // Shown over the lock screen only to ask for the unlock; this activity draws nothing itself.
            setShowWhenLocked(true)
            keyguard.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() { open(screenText); done() }
                override fun onDismissCancelled() = done()
                override fun onDismissError() = done()
            })
            return
        }
        open(screenText)
        done()
    }

    private fun open(screenText: String?) {
        if (AssistOverlay.canShow(this)) AssistOverlay.show(applicationContext, screenText)
        else runCatching {
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra("duo_destination", "spotlight").apply { if (!screenText.isNullOrBlank()) putExtra("uno_assist_text", screenText) })
        }
    }

    private fun done() {
        finish()
        overridePendingTransition(0, 0)
    }
}
