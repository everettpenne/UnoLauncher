package com.jake.duolauncher

import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** The updater's key check relies on reading the signing certificates out of an APK file on disk; this proves the platform gives
 * them for the installed app's own APK, and that they equal the installed package's certificates. */
@RunWith(AndroidJUnit4::class)
class UpdateSignerInstrumentedTest {
    @Test fun anApkFilesSignersMatchTheInstalledPackageItCameFrom() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val pm = context.packageManager
        val installed = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        val archive = pm.getPackageArchiveInfo(installed.applicationInfo!!.sourceDir, PackageManager.GET_SIGNING_CERTIFICATES)
        fun hashes(info: android.content.pm.PackageInfo?) =
            info?.signingInfo?.apkContentsSigners?.mapTo(mutableSetOf()) { UpdateSecurity.sha256Hex(it.toByteArray()) }.orEmpty()
        assertFalse("installed signers should exist", hashes(installed).isEmpty())
        assertTrue("the APK file's signers must equal the installed ones", UpdateSecurity.sameSigners(hashes(installed), hashes(archive)))
    }
}
