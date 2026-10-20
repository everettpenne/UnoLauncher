# Contributor code map

Uno Launcher is a Kotlin/Jetpack Compose Android Home application with one normal app module. It owns its Home content, dock, editing UI and widget hosts. Android owns the secure lock screen, recents, notification panels and system app transitions. Google owns the content and input inside its Discover feed.

## Where to start

All paths below are relative to `app/src/main/java/com/jake/duolauncher/`. Features live in a folder (and Kotlin package) each; everything is `internal` to the one module, so a package is a place to find things and a boundary to review. The top level holds the core: Home, state, paging, drag, Android widget hosting, Discover, backup and the updater. See [Working together](working-together.md) for who looks after what.

| Area | Where | Responsibility |
| --- | --- | --- |
| Activity and setup | `MainActivity.kt`, `SetupExperience.kt` | Android intents/results, Home selection, fresh-install setup and optional access |
| App catalog and saved layout | `LauncherModel.kt`, `LayoutModel.kt`, `ProfileSupport.kt` | Observable launcher state, profile-aware identities, package changes and persistence |
| Home and navigation | `LauncherScreen.kt`, `LauncherPager.kt`, `PageGestures.kt`, `WorkspacePageMotion.kt` | Page composition, shared gestures, unfolded pairs and page motion |
| Home pieces (split out of `LauncherScreen.kt`, behavior unchanged) | `HomeGrid.kt` (workspace, pages, grid, dock, tiles), `WidgetSlots.kt` (widget slot, stacks, stack swipe and rail, drag-to-stack rules), `WidgetCards.kt` (clock, date and glass card), `LauncherSheets.kt` (app picker, widget options, sliders), `DuoTheme.kt` (the theme) | `LauncherScreen` itself stays the one composable that owns Home's state and wires these together |
| Editing | `HomeEditing.kt`, `HomeDrag.kt` | Placement rules, drag previews, insertion and cancellation |
| Folders | `folders/` | Large folders, the folder panel, folder editing and previews |
| Android widgets | `WidgetController.kt`, `WidgetPicker.kt`, `WidgetSizing.kt`, `ZeroPaddingWidgetHost.kt`, `WidgetVerticalGestures.kt`, `WidgetStacks.kt` | Provider catalog, binding/configuration, geometry, stacks and native touch arbitration |
| Uno widgets | `widgets/` | The launcher's own widgets, one file each, and the registry (`UnoWidgets.kt`). See [Writing an Uno widget](widget-development.md) |
| Dynamic island | `island/` | The pill and its panel, activities (calls, timers, media, Live Updates), the overlay over other apps, notification cards, quick apps, the island's themes (`IslandTheme.kt`, see [Island themes](island-themes.md)), and the permission ledger. `DynamicIsland.kt` is the island itself; its state is `IslandState.kt`, its faces and rows `IslandFaces.kt` and `IslandRows.kt`, the live activity slots and capsule glyphs `IslandSlots.kt`, its body's modifiers `IslandModifiers.kt`, and where it may put its edges over the status bar `StatusBarGrid.kt` (with the person's correction in `StatusBarCalibration.kt`) |
| Search | `search/` | The search panel that grows from the island, answers, hand-off to other apps, All apps, optional contacts |
| Control panel | `panel/` | The pull-down panel and the system controls it drives |
| Appearance | `appearance/` | Photo and dune wallpapers, liquid glass, ink, corners, type, colour from wallpaper, scheduling, and the glass UI kit for menus (`GlassUi.kt`) |
| News feed | `feed/` | User-added RSS/Atom sources, direct bounded fetching, on-device cache, and the Discover-slot surface |
| Keyboard | `keyboard/` | Uno Keyboard (the input method) and its tests' privacy gate |
| Google features | `GoogleSearch.kt`, `DiscoverClient.kt`, `LiveDiscoverActivity.kt`, `DiscoverBounds.kt` | Search intents, feed protocol, persistent host and embedding compatibility |
| Settings and sheets | `CustomizationSheet.kt`, `LauncherActionSheet.kt`, `ExtrasSettings.kt`, `ExtrasStore.kt` | Long-press actions, settings pages and the saved settings |
| Status | `DeviceStatus.kt`, `StatusRail.kt` | Live status and its presentation |
| Backup and shade access | `LayoutBackup.kt`, `BackupController.kt`, `SettingsBackup.kt`, `SystemShadeController.kt`, `Updates.kt` | Portable layout and settings import/export, optional system-panel actions, the self-updater |

## Layout and identity

`LauncherModel` exposes a `StateFlow<LauncherState>`. App discovery happens off the main thread; installed-app identities include their Android profile. A temporarily unavailable package or paused profile must not silently erase its placements.

Each ordinary Home page has a four-column, six-row grid. Apps occupy cells; widgets occupy explicit rectangles with durable slot identities. The dock has four positions and rejects incoming apps when full. Moving a shortcut between Home and the dock moves that placement; All apps remains the installed-app catalog.

Wide-window navigation uses overlapping pairs: leading workspace + Home 1, Home 1 + Home 2, and so on. The leading workspace has separate `leadingSlots` and durable widget page `-1`; it disappears from the single-page view without deleting its contents. **Pager page `-1` separately means Discover.** Use the address helpers in `HomeEditing.kt` rather than treating negative cell indices as missing values.

Saved layouts use explicit JSON fields with migration backups. Layout export is a portable description, not a copy of Android's widget capabilities: an import must reconnect widgets, and cross-installation work profiles may require manual correction. Photo files are outside the layout backup.

## Native widgets and gestures

Android widgets render through native `AppWidgetHostView` children inside Compose. Binding and configuration are asynchronous: preserve the pending operation and real binding until it finishes or is explicitly canceled. Restoring a saved numeric widget ID cannot recreate a deleted system binding.

The shared ancestor recognizes horizontal one-page gestures across the page, dock and rail. Native vertical widget content must retain vertical input. A bound widget's scoped long-press recognizer tolerates consumed sub-slop jitter, but scrolling, cancellation, release or another pointer cancels pickup. Do not solve one path by consuming every event: that breaks the others.

Widget size publication uses measured content with `updateAppWidgetOptions`, posted/coalesced after layout. Framework padding behavior is relevant when changing this. Nearby pages remain composed to avoid expensive RemoteViews reinflation during a swipe.

## Discover ownership

The live path keeps a persistent Google window and live Home graphics layers. Healthy Discover backing remains transparent; a recovery surface appears while a status message is present, including a delayed connection or an error. Google reports feed progress but owns native feed gestures, so a timeout or progress reversal is not proof that a finger was released.

`DiscoverBounds.kt` contains an unsupported alignment-hint workaround scoped to audited Window Extensions versions 8–10. Other versions retain normal alignment. This avoids an additional vendor task-fragment transition in the tested configuration; it is not a public SystemUI animation API or a compatibility guarantee. Keep the version guard, host-start recovery and fallback path when changing embedding behavior.

The leading pager slot has exactly one owner at a time. When feeds are configured and the user prefers them (or Window Extensions are absent), the news feed owns the slot: `LauncherScreen` enables the leading page and calls `LiveDiscover.setFeedOwnsSlot(true)`, which keeps the Google window closed and ignores its progress callbacks. Flipping the preference off returns the slot to Google through the normal prepare path. Never drive both surfaces from the same page state.

## Persistence and recovery

Setup uses separate preferences and classifies existing installations before the model creates default state. Completion is stored before closing the welcome sheet, so upgrades do not show onboarding or replace layouts.

A selected photo is decoded into private staging before **Apply** replaces the committed background. URI permission belongs to that selection operation and is released when no longer needed. Failed or canceled selection preserves the existing background. Never explicitly recycle a bitmap already published to Compose; rendering may still reference it. Activity and ready-file recovery have tests; provider process death during early decode remains a separate validation gap.

## Testing changes

Pure placement, gesture-decision, sizing, profile, status and setup rules have JVM tests under `app/src/test/`. Native widget/provider behavior, real sheet Back handling, Android results and live Discover require emulator integration checks under `app/src/androidTest/`.

Start with a focused regression that reproduces the behavior, then run the relevant build/unit/lint checks described in [Contributing](../CONTRIBUTING.md). Integration fixtures need a disposable emulator with their expected apps/providers. Record real binding tuples before testing and preserve them during cleanup; preferences alone are not a complete widget backup. Cover/inner dimension overrides test layout behavior, not physical hinge timing or real-device performance.

Debug probes and instrumentation fixtures are separate source sets. The release build uses R8/resource shrinking and excludes debug activities. See [release instructions](public-release.md) for signing and the [tested beta scope](releases/0.15.0-beta01.md) before expanding compatibility claims.
