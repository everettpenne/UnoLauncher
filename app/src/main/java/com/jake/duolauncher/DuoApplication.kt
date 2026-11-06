package com.jake.duolauncher

import android.app.Application

class DuoApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        com.jake.duolauncher.appearance.SystemWallpaperSync.start(this)
    }

    /** The system's light/dark switch: keep Android's lock screen wallpaper in step with the launcher (see SystemWallpaperSync). */
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        com.jake.duolauncher.appearance.SystemWallpaperSync.check(this,
            newConfig.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES)
    }
}
