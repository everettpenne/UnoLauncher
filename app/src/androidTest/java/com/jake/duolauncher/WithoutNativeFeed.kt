package com.jake.duolauncher

import org.junit.rules.ExternalResource

/** Pager/layout tests use the real Discover page with the remote window transport disabled.
 * LiveDiscoverIntegrationTest separately exercises Google's real, independently focused window.
 */
class WithoutNativeFeed : ExternalResource() {
    override fun before() {
        LiveDiscover.attachNativeFeed = false
        // A "what's new" note after an update would sit in front of the screens the tests drive: mark this build's note as seen.
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val version = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: return
        context.getSharedPreferences("whats_new", android.content.Context.MODE_PRIVATE).edit().putString("seen", version).commit()
    }
    override fun after() { LiveDiscover.attachNativeFeed = true }
}
