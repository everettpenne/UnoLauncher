package com.jake.duolauncher

import com.jake.duolauncher.appearance.IconPacks
import android.content.ComponentName
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** The debug build carries a one-icon pack (res/xml/appfilter.xml in src/debug) that turns Chrome red. */
class IconPackInstrumentedTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules = org.junit.rules.RuleChain.outerRule(WithoutNativeFeed()).around(compose)
    private val chrome = ComponentName("com.android.chrome", "com.google.android.apps.chrome.Main")

    @Test fun aPackIsLoadedAndItsIconReplacesTheAppsIcon() {
        val context = compose.activity
        val pack = IconPacks.load(context, context.packageName)
        assertNotNull("the debug pack loads", pack)
        assertNotNull("it maps Chrome", pack!!.drawableFor(chrome))
        assertNull("it leaves other apps alone", pack.drawableFor(ComponentName("com.google.android.youtube", "x")))

        val model = ViewModelProvider(compose.activity)[LauncherModel::class.java]
        compose.waitUntil(90_000) { !model.state.value.loading }
        val prefs = context.getSharedPreferences("extras", 0)
        val before = prefs.getString("iconPack", "")
        try {
            prefs.edit().putString("iconPack", context.packageName).commit()
            compose.runOnIdle { model.refresh() }
            compose.waitUntil(30_000) {
                val app = model.state.value.apps.firstOrNull { it.component == chrome } ?: return@waitUntil false
                val px = app.image.toPixelMap(); val c = px[px.width / 2, px.height / 2]
                c.red > .9f && c.green < .1f && c.blue < .1f
            }
        } finally {
            prefs.edit().putString("iconPack", before).commit()
            compose.runOnIdle { model.refresh() }
        }
    }
}
