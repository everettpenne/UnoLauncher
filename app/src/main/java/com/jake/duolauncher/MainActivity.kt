package com.jake.duolauncher

import android.app.role.RoleManager
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.LauncherApps
import android.net.Uri
import android.os.Bundle
import android.os.UserManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.viewModels
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.doOnPreDraw
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.activity.result.contract.ActivityResultContracts
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter

class MainActivity : ComponentActivity() {
    private val model: LauncherModel by viewModels()
    private lateinit var widgets: WidgetController
    internal lateinit var backups: BackupController
        private set
    internal lateinit var backgrounds: LauncherBackgroundController
        private set
    private val homeRequests = mutableIntStateOf(0)
    private val searchRequests = mutableIntStateOf(0)
    private val feedSetupRequests = mutableIntStateOf(0)
    private val defaultHome = mutableStateOf(false)
    private val showFirstRun = mutableStateOf(false)
    private lateinit var setupExperience: SetupExperience
    private lateinit var status: DeviceStatusMonitor
    private lateinit var appearance: AppearanceStore
    internal lateinit var feeds: FeedStore
        private set
    internal val island get() = IslandRuntime.state
    private val extrasStore by lazy { ExtrasStore(applicationContext) }
    private val notificationAccess = mutableStateOf(false)
    private val contactsPermission = activityResultRegistry.register("duo.extras.contacts", this,
        ActivityResultContracts.RequestPermission()) { granted -> if (!granted) extrasStore.setContactSearch(false) }
    private val extrasActions by lazy {
        ExtrasActions(extrasStore,
            requestContacts = { contactsPermission.launch(android.Manifest.permission.READ_CONTACTS) },
            hasContactsPermission = { checkSelfPermission(android.Manifest.permission.READ_CONTACTS) == android.content.pm.PackageManager.PERMISSION_GRANTED },
            openNotificationAccess = { startActivity(Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
            hasNotificationAccess = { notificationAccess.value },
            toggleFocus = ::toggleFocus, islandWidget = islandWidget)
    }
    // Registered when the activity is created: a result launcher cannot be registered once the activity has started.
    private val islandWidget = IslandWidgetController(this)
    private fun toggleFocus() {
        val next = !extrasStore.state.focusOn
        extrasStore.setFocusOn(next)
        FocusMode.applyRinger(this, next, extrasStore.state.focusVibrate)
        island.showEvent(IslandEvent(if (next) "Focus on" else "Focus off", IslandSymbol.FOCUS))
    }
    internal lateinit var updates: UpdateStore
        private set
    private var appearanceLocationGeneration = 0
    private var appearancePermissionGeneration = -1
    private var appearanceLocationCancellation: CancellationSignal? = null
    private var timeReceiverRegistered = false
    private val timeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { appearance.refresh(systemDark()) }
    }
    private val locationPermission = activityResultRegistry.register("duo.appearance.location", this,
        ActivityResultContracts.RequestMultiplePermissions(), permissionResult@{ grants ->
        if (appearancePermissionGeneration != appearanceLocationGeneration || isDestroyed) return@permissionResult
        appearancePermissionGeneration = -1
        // Either grant will do: approximate answers from the network or fused provider where there is one, precise lets the phone's
        // own GPS answer where there is not (Google-free systems), and the place kept is rounded in both cases.
        if (grants.values.any { it }) requestAppearanceLocation(keepPending = true)
        else finishAppearanceLocation("Location permission wasn’t granted. Using the system theme until you set a place.")
    })
    private var openingDiscover = false
    private var shadeSetupDialog: android.app.AlertDialog? = null
    private var returningFromShadeSettings = false
    private var shadeSetupOwnsExternalUi = false
    private var recreatingShadeSetup = false
    private var shadePromptDismissedThisRun = false
    private val shadePrefs by lazy { getSharedPreferences("shade", MODE_PRIVATE) }
    private fun shadePromptDeclined() = shadePromptDismissedThisRun ||
        runCatching { shadePrefs.getBoolean("declined", false) }.getOrDefault(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Before anything can prune widget ids: stacked widgets are not on the grid but must stay bound.
        UpdateSecurity.appVersion = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: "unknown"
        WidgetStacks.init(this)
        LargeFolders.init(this)
        WidgetData.init(this)
        super.onCreate(savedInstanceState)
        setupExperience = SetupExperience(this)
        showFirstRun.value = setupExperience.entryDecision(SetupExperience.hadLauncherState(this)) ==
            SetupEntryDecision.SHOW
        returningFromShadeSettings = savedInstanceState?.getBoolean(SHADE_SETTINGS_PENDING) == true
        val restoreShadeDialog = savedInstanceState?.getBoolean(SHADE_DIALOG_VISIBLE) == true
        appearance = AppearanceStore(this)
        feeds = FeedStore(this, lifecycleScope)
        updates = UpdateStore(this, lifecycleScope) { release -> promptInstall(release) }
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        widgets = WidgetController(this, model) { active ->
            LiveDiscover.setExternalResultPending(this, "main", "widget-setup", active)
        }.also { it.restore(savedInstanceState) }
        backups = BackupController(this, model, widgets, onExternalResultChanged = { active ->
            LiveDiscover.setExternalResultPending(this, "main", "layout-backup", active)
        }, onSettingsApplied = { extrasStore.reload(); appearance.reloadFromPreferences(); LargeFolders.reload(); WidgetData.reload() }).also { it.restore() }
        backgrounds = LauncherBackgroundController(this) { active ->
            LiveDiscover.setExternalResultPending(this, "main", "launcher-background", active)
        }
        status = DeviceStatusMonitor(this).also { lifecycle.addObserver(it) }
        IslandTools.load(this)
        NotificationFeed.onPeek = { pkg, label ->
            // The posting app's own icon when it is a launchable app here; otherwise a plain bell.
            val app = model.state.value.apps.firstOrNull { it.packageName == pkg }
            runOnUiThread { if (app != null) island.showPeek(app, label) else island.showEvent(IslandEvent(label, IslandSymbol.NOTIFICATION)) }
        }
        updateDefaultHome()
        if (savedInstanceState == null && intent.getStringExtra("duo_destination") == "search") searchRequests.intValue++
        intent.removeExtra("duo_destination")
        setContent {
            SideEffect { UnoFeedback.configure(this@MainActivity, extrasStore.state.haptics, extrasStore.state.sounds) }
            TiltHighlight(enabled = appearance.state.tiltHighlight && appearance.state.liquidGlass,
                strength = appearance.state.tiltStrength)
            val rawState = model.state.collectAsStateWithLifecycle().value
            val extrasState = extrasStore.state
            // Focus hides apps at the last moment, so the saved layout and the model never change.
            val state = remember(rawState, extrasState.focusOn, extrasState.focusHidden) {
                rawState.copy(apps = Focus.filter(rawState.apps, { it.id }, extrasState.focusOn, extrasState.focusHidden))
            }
            val deviceStatus = status.state.collectAsStateWithLifecycle().value
            DuoTheme(appearance.state.dark) {
                androidx.compose.runtime.CompositionLocalProvider(LocalFeedFollow provides feeds::addFeed,
                    LocalWallpaperLuma provides rememberWallpaperLuma(appearance.state.dark),
                    LocalWallpaperAccent provides rememberWallpaperAccent(appearance.state.wallpaperColor, appearance.state.dark)) {
                LauncherScreen(state, model, widgets, homeRequests.intValue,
                    onLaunch = { launchApp(it) }, onMakeDefault = ::makeDefault, onAppInfo = ::appInfo,
                    isDefaultHome = defaultHome.value, deviceStatus = deviceStatus, onStatusMode = ::setStatusMode, onWallpaperPreview = ::previewWallpaper,
                    onDiscover = ::openDiscover, searchRequests = searchRequests.intValue,
                    onLaunchFrom = ::launchApp, onGoogleSearch = ::openGoogleSearch,
                    appearance = appearance.state,
                    onAppearanceMode = { cancelAppearanceLocation(); appearance.setMode(it, systemDark()) },
                    onAppearanceManual = { place, lat, lon -> cancelAppearanceLocation(); appearance.setManual(place, lat, lon, systemDark()) },
                    onAppearanceDeviceLocation = ::useAppearanceLocation,
                    onAppearanceClear = { cancelAppearanceLocation(); appearance.clearLocation(systemDark()) },
                    showFirstRun = showFirstRun.value,
                    onFinishFirstRun = ::finishFirstRun,
                    onShadeSetup = ::showShadeSetup,
                    extras = extrasActions,
                    feed = feeds.state.collectAsStateWithLifecycle().value,
                    feedSetupRequests = feedSetupRequests.intValue,
                    onFeedRefresh = feeds::refresh,
                    onFeedOpenEntry = ::openFeedEntry,
                    onFeedVisible = { feeds.refreshIfStale() },
                    onAddFeed = feeds::addFeed,
                    onRemoveFeed = feeds::removeFeed,
                    onSourceEnabled = feeds::setSourceEnabled,
                    onLiquidGlass = appearance::setLiquidGlass,
                    onWallpaperColor = appearance::setWallpaperColor,
                    onTiltHighlight = appearance::setTiltHighlight,
                    onTiltStrength = appearance::setTiltStrength,
                    island = island,
                    onIsland = appearance::setIsland,
                    islandEverywhere = extrasStore.state.islandEverywhere,
                    onIslandEverywhere = ::setIslandEverywhere,
                    updates = updates.state.collectAsStateWithLifecycle().value,
                    onCheckUpdates = updates::checkNow,
                    onInstallRelease = updates::installRelease,
                    onAutoUpdate = updates::setAutoUpdate,
                    onIslandScale = appearance::setIslandScale,
                    onRefractionHeight = appearance::setRefractionHeight,
                    onRefractionAmount = appearance::setRefractionAmount,
                    onRefractionChroma = appearance::setRefractionChroma)
                }
            }
        }
        FoldRenderExperiment.attach(this)
        // Reassert the token after recreation (and after process restoration, where the
        // in-memory owner set is empty) before any external UI can uncover Discover.
        if (returningFromShadeSettings || restoreShadeDialog) ownShadeSetupExternally()
        if (restoreShadeDialog) window.decorView.post { if (!isFinishing && !isDestroyed) showShadeSetup() }
    }

    override fun onStart() {
        super.onStart(); widgets.host.startListening()
        if (!timeReceiverRegistered) {
            ContextCompat.registerReceiver(this, timeReceiver, IntentFilter().apply {
                addAction(Intent.ACTION_TIME_TICK); addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED); addAction(Intent.ACTION_DATE_CHANGED)
            }, ContextCompat.RECEIVER_NOT_EXPORTED)
            timeReceiverRegistered = true
        }
        appearance.refresh(systemDark())
    }
    override fun onStop() {
        // The first app of a split is now in front; give it a beat to settle, then open the second beside it.
        if (pendingSplitSecond != null) { splitLauncherStopped = true; splitHandler.removeCallbacks(openSplitSecond); splitHandler.postDelayed(openSplitSecond, SPLIT_SETTLE_MS) }
        if (timeReceiverRegistered) { unregisterReceiver(timeReceiver); timeReceiverRegistered = false }
        widgets.host.stopListening(); super.onStop()
    }
    override fun onDestroy() {
        recreatingShadeSetup = isChangingConfigurations
        shadeSetupDialog?.dismiss()
        if (!isChangingConfigurations) releaseShadeSetupOwnership()
        cancelAppearanceLocation()
        super.onDestroy()
    }
    override fun onResume() {
        super.onResume()
        // Back on Home before the second app opened: the user left the first app, so don't open anything now.
        if (splitLauncherStopped && pendingSplitSecond != null) {
            pendingSplitSecond = null; splitLauncherStopped = false
            splitHandler.removeCallbacks(openSplitSecond); splitHandler.removeCallbacks(abandonSplit)
        }
        if (returningFromShadeSettings) {
            returningFromShadeSettings = false
            releaseShadeSetupOwnership()
        }
        val discover = DiscoverSession.host.get()
        if (discover != null) window.decorView.doOnPreDraw {
            it.postOnAnimation { if (DiscoverSession.host.get() === discover) DiscoverSession.dismiss() }
        }
        notificationAccess.value = androidx.core.app.NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
        model.refresh(); appearance.refresh(systemDark()); updateDefaultHome(); feeds.refreshIfStale(); updates.checkIfStale(); syncIslandOverlay()
        window.decorView.post {
            if (!isFinishing && !isDestroyed && !LiveDiscover.viewport.isEmpty)
                LiveDiscover.prepare(this, LiveDiscover.viewport, LiveDiscover.pageWidth)
        }
    }

    internal fun openSystemShade(panel: ShadePanel) {
        when (SystemShadeAccessibilityService.open(this, panel)) {
            ShadeOpenResult.OPENED -> Unit
            // The service is optional and denial must stick: a declined or dismissed prompt is
            // never shown again by the gesture itself (Help & setup still offers it on request),
            // so a swipe on Home is simply a no-op until the user opts in.
            ShadeOpenResult.SERVICE_DISABLED -> if (!shadePromptDeclined()) showShadeSetup()
            ShadeOpenResult.SERVICE_STARTING -> Toast.makeText(this,
                "Shade gestures are starting. Swipe down again.", Toast.LENGTH_SHORT).show()
            ShadeOpenResult.ACTION_REJECTED -> Toast.makeText(this,
                "Android couldn’t open the system panel.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showShadeSetup() {
        if (shadeSetupDialog?.isShowing == true) return
        ownShadeSetupExternally()
        shadeSetupDialog = android.app.AlertDialog.Builder(this)
            .setTitle("Shade gestures (optional)")
            .setMessage("Swiping down on Home can open Notifications or Quick Settings. Android lets a launcher do that only through an accessibility service, so it stays off until you turn it on.\n\n" +
                "It can: open those two panels when you swipe, and draw the dynamic island above other apps and the status bar (an accessibility service is the only window Android lets sit there).\n" +
                "It can’t: read your screen, see other apps, or tap or type for you.\n\n" +
                "Without it, the island can still show above other apps through “Display over other apps”, under the status bar. Turn the service off any time in Settings → Accessibility. Uno Launcher works the same without it.")
            .setNegativeButton("No thanks") { _, _ ->
                runCatching { shadePrefs.edit().putBoolean("declined", true).apply() }
            }
            .setPositiveButton("Open settings") { _, _ ->
                try {
                    returningFromShadeSettings = true
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                } catch (_: android.content.ActivityNotFoundException) {
                    returningFromShadeSettings = false
                    releaseShadeSetupOwnership()
                    Toast.makeText(this, "Accessibility settings are unavailable.", Toast.LENGTH_LONG).show()
                }
            }
            // Tapping outside or going Back is also a "no": remembered, so the prompt does not return on every swipe. "Set up shade
            // gestures" in Help and setup still offers it.
            .also { dialog -> dialog.setOnCancelListener {
                shadePromptDismissedThisRun = true
                runCatching { shadePrefs.edit().putBoolean("declined", true).apply() }
            } }
            .also { dialog -> dialog.setOnDismissListener {
                shadeSetupDialog = null
                if (!returningFromShadeSettings && !recreatingShadeSetup) releaseShadeSetupOwnership()
            } }
            .show()
    }

    private fun finishFirstRun() {
        setupExperience.finish()
        showFirstRun.value = false
    }

    private fun ownShadeSetupExternally() {
        if (shadeSetupOwnsExternalUi) return
        shadeSetupOwnsExternalUi = true
        LiveDiscover.setExternalResultPending(this, "main", "shade-service-setup", true)
    }

    private fun releaseShadeSetupOwnership() {
        if (!shadeSetupOwnsExternalUi) return
        shadeSetupOwnsExternalUi = false
        LiveDiscover.setExternalResultPending(this, "main", "shade-service-setup", false)
    }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) setStatusMode(model.state.value.verticalStatus)
    }
    override fun onSaveInstanceState(outState: Bundle) {
        widgets.save(outState)
        outState.putBoolean(SHADE_DIALOG_VISIBLE, shadeSetupDialog?.isShowing == true && !returningFromShadeSettings)
        outState.putBoolean(SHADE_SETTINGS_PENDING, returningFromShadeSettings)
        super.onSaveInstanceState(outState)
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        FoldRenderExperiment.onNewIntent(this, intent)
        if (intent.getStringExtra("duo_destination") == "search") searchRequests.intValue++
        else if (intent.hasCategory(Intent.CATEGORY_HOME) || intent.getStringExtra("duo_destination") == "home") homeRequests.intValue++
        intent.removeExtra("duo_destination")
    }

    @Deprecated("Widget configuration uses the platform host request-code API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (!widgets.onActivityResult(requestCode, resultCode) && !islandWidget.onActivityResult(requestCode, resultCode)) super.onActivityResult(requestCode, resultCode, data)
    }

    private fun launchApp(app: AppEntry, bounds: android.graphics.Rect? = null) {
        UnoFeedback.play(Cue.LAUNCH, window.decorView)
        island.showLaunch(app)
        try {
            val user = getSystemService(UserManager::class.java).getUserForSerialNumber(app.userSerial)
                ?: throw IllegalStateException("Profile is unavailable")
            getSystemService(LauncherApps::class.java).startMainActivity(app.component, user, screenBounds(bounds), launchOptions(bounds))
        } catch (_: Exception) { Toast.makeText(this, "${app.label} is unavailable.", Toast.LENGTH_SHORT).show(); model.refresh() }
    }

    private var pendingSplitSecond: AppEntry? = null
    private val splitHandler by lazy { android.os.Handler(mainLooper) }
    private val openSplitSecond = Runnable { openPendingSplitSecond() }
    private var splitLauncherStopped = false
    private val abandonSplit = Runnable {
        val waiting = pendingSplitSecond
        pendingSplitSecond = null
        if (waiting != null) Toast.makeText(this, "The first app didn't open in time, so split screen was cancelled.", Toast.LENGTH_LONG).show()
    }

    /** Opens [first], then [second] beside it, stacked top and bottom on a phone (Android picks the arrangement).
     * No accessibility service is involved: the second launch carries `FLAG_ACTIVITY_LAUNCH_ADJACENT`, which asks
     * the system to place it next to the app already in front. That only works once [first] is actually in
     * front, and launching both in one call does not (the first lands hidden behind), so the second waits
     * until this launcher has left the screen, with a timeout in case it never does.
     *
     * Work-profile apps cannot be the second app: only the launcher-apps service can start them, and it cannot
     * set launch flags. They can still be the first.
     */
    internal fun launchSplit(first: AppEntry, second: AppEntry) {
        if (second.userSerial != personalUserSerial()) {
            Toast.makeText(this, "${second.label} is a work app, which can't be the second app in split screen yet.",
                Toast.LENGTH_LONG).show()
            return
        }
        pendingSplitSecond = second
        splitLauncherStopped = false
        launchApp(first)
        // If the first app never comes to the front (a slow cold start, or it failed), give up instead of launching the
        // second one beside whatever Android was last showing, which is what pairs it with an unrelated app.
        splitHandler.removeCallbacks(openSplitSecond); splitHandler.removeCallbacks(abandonSplit)
        splitHandler.postDelayed(abandonSplit, SPLIT_GIVE_UP_MS)
    }

    private fun personalUserSerial() = getSystemService(UserManager::class.java).getSerialNumberForUser(android.os.Process.myUserHandle())

    private fun openPendingSplitSecond() {
        val second = pendingSplitSecond ?: return
        pendingSplitSecond = null
        splitHandler.removeCallbacks(openSplitSecond); splitHandler.removeCallbacks(abandonSplit)
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(second.component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT)
        runCatching { startActivity(intent) }.onFailure {
            Toast.makeText(this, "${second.label} couldn't open beside it.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun screenBounds(bounds: android.graphics.Rect?): android.graphics.Rect? = bounds?.takeUnless { it.isEmpty }?.let {
        val location = IntArray(2); window.decorView.getLocationOnScreen(location)
        android.graphics.Rect(it).apply { offset(location[0], location[1]) }
    }
    private fun launchOptions(bounds: android.graphics.Rect?): Bundle? = bounds?.takeUnless { it.isEmpty }?.let {
        android.app.ActivityOptions.makeScaleUpAnimation(window.decorView, it.left, it.top, it.width(), it.height()).toBundle()
    }
    private fun openGoogleSearch(bounds: android.graphics.Rect?): Boolean = try {
        startActivity(googleSearchIntent().apply { sourceBounds = screenBounds(bounds) }, launchOptions(bounds))
        true
    } catch (_: android.content.ActivityNotFoundException) { false }
      catch (_: SecurityException) { false }

    private fun openDiscover() {
        if (DiscoverEmbedding.supported(this)) {
            if (openingDiscover) return
            openingDiscover = true
            // A very quick reopen can arrive before the previous return's deferred cleanup.
            // Finish that session before taking a new image, so it cannot invalidate this copy.
            DiscoverSession.dismiss()
            DiscoverMotion.capture(this) {
                openingDiscover = false
                if (!lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) return@capture
                DiscoverSession.apps = model.state.value.apps
                startActivity(Intent(this, DiscoverActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or Intent.FLAG_ACTIVITY_NO_ANIMATION))
            }
        }
        else showDiscoverFallback()
    }

    private fun showDiscoverFallback() {
        val google = packageManager.getLaunchIntentForPackage(DiscoverClient.GOOGLE_PACKAGE)
        android.app.AlertDialog.Builder(this)
            .setTitle("Discover isn’t available here")
            .setMessage("Uno Launcher can’t place the Discover feed beside Home on this device. You can open the Google app, add your own news feeds for this slot, or stay on Home.")
            .setNegativeButton("Stay on Home", null)
            .setNeutralButton("Add a feed") { _, _ -> feedSetupRequests.intValue++ }
            .apply {
                if (google != null) setPositiveButton("Open Google") { _, _ ->
                    runCatching { startActivity(google) }
                }
            }
            .show()
    }

    private var overlaySettingsReturned = false

    /** Opt-in overlay: never starts without the permission, and explains itself first. */
    private fun setIslandEverywhere(value: Boolean) {
        if (!value) {
            extrasStore.setIslandEverywhere(false)
            stopService(Intent(this, IslandOverlayService::class.java))
            IslandRuntime.updateOverlay(false)
            return
        }
        if (SystemShadeAccessibilityService.hostsIsland()) {
            // The accessibility overlay draws the island above the status bar and needs no overlay permission.
            extrasStore.setIslandEverywhere(true)
            return
        }
        if (Settings.canDrawOverlays(this)) {
            extrasStore.setIslandEverywhere(true)
            startService(Intent(this, IslandOverlayService::class.java))
            return
        }
        extrasStore.setIslandEverywhere(true)
        android.app.AlertDialog.Builder(this)
            .setTitle("Island everywhere")
            .setMessage("This shows the island above other apps using Android's \"display over other apps\" permission. The island window is exactly the island's size, never draws on the lock screen or when the screen is off, and touches outside it pass through to the app beneath. Android draws this kind of window beneath the status bar, so over other apps the island can be seen but not tapped. For a tappable island, choose \"Tappable island\" and turn on Uno Launcher shade gestures in Accessibility; it draws only the island and reads nothing from other apps. Nothing is read, stored, or sent that the island doesn't already show on Home.")
            .setNeutralButton("Tappable island") { _, _ ->
                // The accessibility overlay sits above the status bar, so the island can be tapped there.
                runCatching { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            }
            .setNegativeButton("Not now") { _, _ -> extrasStore.setIslandEverywhere(false) }
            .setPositiveButton("Open settings") { _, _ ->
                overlaySettingsReturned = true
                runCatching {
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")))
                }
            }
            .show()
    }

    /** After the overlay settings trip: start the overlay if granted, or explain restricted settings. */
    private fun syncIslandOverlay() {
        val wanted = extrasStore.state.islandEverywhere
        if (!wanted) return
        if (SystemShadeAccessibilityService.hostsIsland()) return
        if (Settings.canDrawOverlays(this)) {
            startService(Intent(this, IslandOverlayService::class.java))
            return
        }
        if (overlaySettingsReturned) {
            overlaySettingsReturned = false
            Toast.makeText(this,
                "If the \"display over other apps\" switch didn't appear, open Uno Launcher's app info and allow restricted settings, then try again.",
                Toast.LENGTH_LONG).show()
        }
    }

    private fun promptInstall(release: UnoRelease) {
        val file = updates.readyApk(release.tag) ?: return
        val uri = androidx.core.content.FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        }.onFailure {
            Toast.makeText(this, "Android couldn't open the installer.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openFeedEntry(link: String) {
        if (!FeedParser.isWebLink(link)) return
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)))
        } catch (_: android.content.ActivityNotFoundException) {
            Toast.makeText(this, "No browser app is available to open this story.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun makeDefault() {
        // Samsung may immediately cancel a role request; its Home settings is reliable.
        try { startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) }
        catch (_: android.content.ActivityNotFoundException) {
            val role = getSystemService(RoleManager::class.java)
            if (role.isRoleAvailable(RoleManager.ROLE_HOME)) startActivity(role.createRequestRoleIntent(RoleManager.ROLE_HOME))
            else startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
        }
    }

    private fun updateDefaultHome() {
        defaultHome.value = getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_HOME)
    }

    private fun systemDark() = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
        android.content.res.Configuration.UI_MODE_NIGHT_YES

    private fun hasCoarseLocation() = ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    private fun hasFineLocation() = ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun useAppearanceLocation() {
        cancelAppearanceLocation()
        appearance.locationStatus("Waiting for device location…")
        LiveDiscover.setExternalResultPending(this, "main", "appearance-location", true)
        if (hasCoarseLocation() || hasFineLocation())
            requestAppearanceLocation(keepPending = true)
        else {
            appearancePermissionGeneration = appearanceLocationGeneration
            runCatching { locationPermission.launch(arrayOf(android.Manifest.permission.ACCESS_COARSE_LOCATION, android.Manifest.permission.ACCESS_FINE_LOCATION)) }
                .onFailure { finishAppearanceLocation("Location permission couldn’t be requested. Using the system theme.") }
        }
    }

    // Permission is checked at the top and every call is inside runCatching, which absorbs a revoked grant.
    @android.annotation.SuppressLint("MissingPermission")
    private fun requestAppearanceLocation(keepPending: Boolean = false) {
        if (!keepPending) LiveDiscover.setExternalResultPending(this, "main", "appearance-location", true)
        val generation = ++appearanceLocationGeneration
        val manager = getSystemService(LocationManager::class.java)
        if (!hasCoarseLocation() && !hasFineLocation()) {
            finishAppearanceLocation("Location permission isn’t available. Using the system theme."); return
        }
        val precise = hasFineLocation()
        fun keep(latitude: Double, longitude: Double) {
            // Rounded to a tenth of a degree before it is kept: sunrise and sunset need no more, and nothing more exact is stored.
            appearance.setDeviceLocation(LocationChoice.coarsen(latitude), LocationChoice.coarsen(longitude), systemDark())
        }
        val enabled = runCatching { manager.getProviders(true).toSet() }.getOrDefault(emptySet())
        // A recent fix from any provider that is on, passive included, answers at once.
        val cached = runCatching { enabled.mapNotNull { manager.getLastKnownLocation(it) }
            .maxByOrNull { it.time }?.takeIf { System.currentTimeMillis() - it.time <= LocationChoice.CACHE_MAX_AGE_MS } }.getOrNull()
        if (cached != null) {
            if (generation == appearanceLocationGeneration) keep(cached.latitude, cached.longitude)
            finishAppearanceLocation(null); return
        }
        val providers = LocationChoice.providers(enabled, precise)
        if (providers.isEmpty()) {
            finishAppearanceLocation(if (!precise) "This phone has no approximate location source. Allow precise location, or enter a place by hand."
                else "Location is switched off. Turn it on in Android’s settings, or enter a place by hand.")
            return
        }
        tryAppearanceProvider(manager, providers, 0, generation, ::keep)
    }

    /** Asks one provider and, if it gives no answer in time, the next. */
    @android.annotation.SuppressLint("MissingPermission")
    private fun tryAppearanceProvider(manager: LocationManager, providers: List<String>, index: Int, generation: Int, keep: (Double, Double) -> Unit) {
        if (index >= providers.size) { if (generation == appearanceLocationGeneration) finishAppearanceLocation("No location answer arrived. Try outdoors, or enter a place by hand."); return }
        val provider = providers[index]
        val cancellation = CancellationSignal()
        appearanceLocationCancellation = cancellation
        window.decorView.postDelayed({
            if (generation == appearanceLocationGeneration && appearanceLocationCancellation === cancellation) {
                cancellation.cancel(); tryAppearanceProvider(manager, providers, index + 1, generation, keep)
            }
        }, LocationChoice.timeoutMs(provider))
        runCatching { manager.getCurrentLocation(provider, cancellation, ContextCompat.getMainExecutor(this)) { location ->
            if (generation != appearanceLocationGeneration || isDestroyed || appearanceLocationCancellation !== cancellation) return@getCurrentLocation
            if (location != null) { keep(location.latitude, location.longitude); finishAppearanceLocation(null) }
            else tryAppearanceProvider(manager, providers, index + 1, generation, keep)
        } }.onFailure { if (generation == appearanceLocationGeneration) tryAppearanceProvider(manager, providers, index + 1, generation, keep) }
    }

    private fun cancelAppearanceLocation() {
        appearanceLocationGeneration++
        appearancePermissionGeneration = -1
        appearanceLocationCancellation?.cancel(); appearanceLocationCancellation = null
        if (::appearance.isInitialized) appearance.locationStatus(null)
        LiveDiscover.setExternalResultPending(this, "main", "appearance-location", false)
    }

    private fun finishAppearanceLocation(message: String?) {
        appearanceLocationGeneration++
        appearancePermissionGeneration = -1
        appearanceLocationCancellation = null
        appearance.locationStatus(message)
        LiveDiscover.setExternalResultPending(this, "main", "appearance-location", false)
    }

    private fun setStatusMode(vertical: Boolean) {
        LiveDiscover.host.get()?.statusMode(vertical)
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (vertical) controller.hide(WindowInsetsCompat.Type.statusBars()) else controller.show(WindowInsetsCompat.Type.statusBars())
    }

    private fun previewWallpaper() {
        try {
            startActivity(Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
                .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, ComponentName(this, DuneWallpaperService::class.java)))
        } catch (_: android.content.ActivityNotFoundException) {
            Toast.makeText(this, "The system wallpaper preview is unavailable.", Toast.LENGTH_LONG).show()
        }
    }

    private fun appInfo(app: AppEntry) {
        try {
            val user = getSystemService(UserManager::class.java).getUserForSerialNumber(app.userSerial)
                ?: throw IllegalStateException("Profile is unavailable")
            getSystemService(LauncherApps::class.java).startAppDetailsActivity(app.component, user, null, null)
        } catch (_: Exception) {
            Toast.makeText(this, "${app.label} is unavailable.", Toast.LENGTH_SHORT).show()
            model.refresh()
        }
    }

    private companion object {
        const val SHADE_DIALOG_VISIBLE = "duo.shade.dialog_visible"
        const val SHADE_SETTINGS_PENDING = "duo.shade.settings_pending"
        const val SPLIT_GIVE_UP_MS = 12_000L
        const val SPLIT_SETTLE_MS = 700L
    }
}
