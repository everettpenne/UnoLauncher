package com.jake.duolauncher

import org.junit.rules.ExternalResource

/** Marks this build's What's New note as seen, so it does not sit in front of the screens the tests drive. */
class WhatsNewSeen : ExternalResource() {
    override fun before() {
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val version = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: return
        context.getSharedPreferences("whats_new", android.content.Context.MODE_PRIVATE).edit().putString("seen", version).commit()
    }
}
