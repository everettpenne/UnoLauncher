package com.jake.duolauncher

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * Where Android's Settings sends someone who asks for Uno's settings (App info's settings link, which any app can offer with
 * APPLICATION_PREFERENCES). It shows nothing: it opens Uno's own settings sheet over Home and finishes. Uno's settings stay in the one place they
 * are, beside the Home they change, so a change is seen as it is made. It is exported, so nothing the caller sends is read: only the page to open.
 */
class SettingsLinkActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runCatching {
            startActivity(Intent(this, MainActivity::class.java)
                .putExtra("duo_destination", "customize")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        finish()
    }
}
