# Polish checklist

The list we are working down until Uno feels finished. Tick an item only when it is done *and checked as far as it can be* (an emulator-only check is written as such). Items that need Everett's phone say so, and stay open until the phone result is in. Newest status is at the top of each item.

Legend: `[-]` deferred on purpose (reason given), `[x]` done, `[~]` done as far as the emulator allows, waiting on the phone, `[ ]` open.

## 1. Close the "never seen on a real phone" gap

The device checklist is in [device-checklist.md](device-checklist.md). Work that does not need the phone:

- [x] Write the device checklist (every feature that has only run on an emulator or with test data).
- [x] **Entering a place by hand** (reported "never works"): accepts comma decimals, the typographic minus, degree signs, N/S/E/W letters and a pasted "lat, lon" pair in either field; after saving, the page shows "Saved: place, 40.7° N, 74.0° W. Sunrise …, sunset … today" (`PlaceInput`, unit-tested, and an emulator test drives it). The emulator test also caught a real case: pasting a pair into one field failed when the other field already held a value, which is what editing a saved place looks like; a pasted pair now wins.
- [~] **"Use device location" never completes**: waits up to 90 s for GPS (was 45), says which provider it is waiting on, has a Cancel button, and falls back to the last place the phone knew, of any age, instead of failing. Emulator test passes with a mock GPS fix; a cold fix on the real phone is the open part.
- [~] **The island's search field cannot be found**: the island over other apps has had Search, Feed and Settings buttons since 0.32.0 (the typed field exists only in the launcher's own island, because the overlay cannot take typing). Open on the phone: does Search from the overlay reach the launcher's search, and does Enter there reach the browser with the query.
- [ ] Everett runs the device checklist on the phone and reports each result.
- [ ] Fix what the phone run finds (one line per fix here).

## 2. Make the visual language consistent

About 87 stock Material buttons, chips, fields and dialogs sat outside the glass kit. Status: the settings sheet and the dialogs are converted; the surfaces whose text colour adapts to the wallpaper are not, on purpose (see the last item).

- [x] Inventory every stock `AlertDialog`, `TextButton`, `OutlinedButton`, `FilterChip`, `OutlinedTextField`, `Checkbox`, `RadioButton` and `Switch` outside `GlassUi.kt`, by screen. (Left alone deliberately: `IconButton`s, the system `AlertDialog.Builder` dialogs in `MainActivity`, the island's own controls, the first-run sheet, Discover, Edit Home's bar.)
- [x] Settings pages (`CustomizationSheet`, extras, feed, appearance, updates, backup, large-folder editor, folder name) use the glass kit. Checked on the emulator by photographing every settings page; the tile and feed switches are now the liquid switch.
- [x] Home dialogs (create folder, backup and photo prompts, widget not added, finish setup, widget settings, restore preview) use `GlassAlertDialog`.
- [x] The kit has slot-style forms so a Material screen moves over by renaming; `docs/design.md` says how, and where the kit's ink does and does not apply. Chips, checkboxes and radios report their selected state (a regression I introduced and fixed: the emulator test caught it).
- [x] A glass date picker (`GlassDatePickerDialog`, month grid unit-tested in `MonthGridTest`) replaces Material's in the Countdown widget.
- [x] The Photos full-screen viewer's controls are `GlassIconButton`s (white ink on its black bar).
- [x] The kit reads `LocalKitInk`, so a surface with its own ink can use it; the All apps panel's chips, search field and work button now do. Still Material on purpose: the spotlight field (its own glass styling) and Home widget cards' buttons.
- [x] First-run sheet converted to the kit. The Edit Home bar stays Material's tonal buttons by design: it sits on the live wallpaper and needs an opaque container to stay readable over any picture.

### Widget review (2026-10-12): Media player, Glance, Liquid battery

- [x] Media player: no player queries while drawing (`NowPlaying` now carries the update time and speed), an empty state that says which of notification access or "Track title and artwork in media" to turn on, 48 dp buttons and seek bar, the seek bar readable and settable by a screen reader (tap-to-seek only: a drag would fight the Home pager). Emulator test with a real media session.
- [x] Glance: an alarm more than a day away now names its day ("07:30 · Mon", then the date past six days). It read as just "07:30" before.
- [x] Liquid battery: reviewed (compiles, 7 tests pass); emulator test draws it. Still to check on the phone: that the always-on ripple does not cost noticeable battery (see "Startup and memory check").
- [x] "Molten metal" wallpaper reviewed and redone (below).

**Molten metal, redone (2026-10-12).** The first version was rolling dunes with a rim light: nothing like the iPhone 17 Pro wallpapers it was meant to evoke. The new one keeps the dune character (long, wind-sculpted sweeps) but draws them as one continuous field of glass-like contour lines that bend into and through each other (a warped height field seen through contour lines), crisp hairline highlights with a slight red-blue fringe like thick glass, light that pools just past a contour and falls away to black (opening into broad pools in a few places), grain on the glow only, and a quiet dark top for the island and clock. Day is ice blue, night is ember; both are mostly black so icons keep their contrast. Prototyped in numpy against the references, then ported. Tests: the field is smooth and rises through about eight contours in every column, contour spacing varies, pools are broad in few places, both palettes are mostly black with bright hairlines and the right hue, and the edges carry a fringe.

## 3. Reliability debt

What the emulator says depends on its state, so `scripts/emulator-tests.sh` now sets one: Uno as the emulator's Home app (so "go Home" stays in Uno), app data cleared, and the old Home app restored afterwards (the restore did not work when the Home app had already been changed by hand; fixed by noting it here: run `adb shell cmd package set-home-activity com.google.android.apps.nexuslauncher/.NexusLauncherActivity` if the emulator is left on Uno).

Two runs on 2026-10-12 disagreed, which is itself the finding:

| Test | Stock Home app, leftover data | Uno as Home, cleared data |
| --- | --- | --- |
| `CustomizationNavigationIntegrationTest.appMoveSubpageBackDoesNotChangePlacement` | passes | **fails** |
| `LargeFolderIntegrationTest.anAppDroppedOnALargeFolderGoesIntoIt` | passes | **fails** |
| `LargeFolderIntegrationTest.aLargeFolderShowsItsAppsOnHomeAndCleansUpWithItsWidget` | fails (leftover data) | passes |
| `NativeAppearanceLocationIntegrationTest.explicitDeviceLocationDenialReturnsToUsableHomeWithoutChangingState` | passes | **fails** |
| `AppearanceBackgroundIntegrationTest.realPhotoPickerSelectsTheExactOwnedMediaItem` | flips between runs | fails |

- [x] **The Back and large-folder failures were the test setup, not the product.** With Uno as the emulator's Home app and the app data cleared, the first-run sheet sat over the screens the tests drive, and that is what made Back from a sub-page (`appMoveSubpageBackDoesNotChangePlacement`, `helpIsReachable...`, `systemBackReturns...`) and `anAppDroppedOnALargeFolderGoesIntoIt` fail. `scripts/emulator-tests.sh` now marks first-run finished after clearing (as a returning user would have), and all of `CustomizationNavigationIntegrationTest`, `LargeFolderIntegrationTest` and `GlassMenusInstrumentedTest` pass with Uno as Home (2026-10-12). Still worth a look on the phone (device checklist C), but there is no known bug here.
- [x] My new emulator tests used to leave Home emptied (unpinned apps, removed widgets) and so broke later tests such as `appMoveSubpageBack...`; they now restore the layout.
- [-] `NativeAppearanceLocationIntegrationTest.explicitDeviceLocationDenialReturnsToUsableHomeWithoutChangingState` swaps the phone's Home *role holder* itself, so it conflicts with the script's Home-app switch. It passed with the stock Home app earlier today. Run it on its own, with the stock Home app: `adb shell am instrument -w -e class com.jake.duolauncher.NativeAppearanceLocationIntegrationTest com.jake.duolauncher.test/androidx.test.runner.AndroidJUnitRunner`.
- [ ] `WidgetPickerE2ETest`: five tests fail on the released 0.45.0 code as well, in every state tried. They cannot find an "Add widget" node to tap. Find out whether the test or the widget picker changed.
- [ ] Make tests independent of leftover data (the large-folder face test assumes none), or have the script clear data between classes.
- [ ] The widget-binding test fixture grants (or skips) the bind permission on a fresh emulator.
- [ ] `draggingAFilteredAppDismissesTheKeyboardBeforePlacement` drops near an edge, not onto an occupied cell's centre.
- [ ] The four quarantined widget tests rewritten or deleted with a reason.
- [ ] The real-photo-picker test is robust or skips when the emulator's picker does not expose the item.
- [x] `emptySpaceWallpaperOpensPhotoControlsWithoutChangingLayoutOrBindings` was stale since the wallpaper picker arrived in 0.43 (the photo button is now below the fold); the test scrolls to it.
- [ ] A full emulator run that is green (or whose every failure is explained in `docs/future-fixes.md`).

## 4. Gaps people notice in a launcher

- [x] Strings out of code: 227 `Text("…")` literals moved into `strings.xml` (and their wording kept exactly), and `UiStringsTest` fails the build if a plain-words `Text("…")` comes back. Still in code: about 30 texts built from values ("Move to page ${n}") and content descriptions; new ones should use format strings.
- [x] Accessibility pass, emulator: `AccessibilityAuditInstrumentedTest` walks Home and every settings page. It found 379 unlabelled controls and 132 touch targets under 48 dp; now 0 and 1 (a chip half-scrolled off the edge). Fixes: free Home cells say "Empty Home space" with a "Home options" long-press action and occupied cells are hidden from screen readers; the island has a label; every switch row reads as one control with a 48 dp target; checkbox and radio rows are single toggles. At 200% text everything wraps without overlap; wallpaper names now wrap to two lines. Not done: a real TalkBack walk-through (focus order, announcements) on the phone.
- [x] Predictive back reviewed: target SDK 36 (on by default), every in-app Back goes through Compose `BackHandler` or the activity dispatcher, nothing overrides the old `onBackPressed`. The system animation itself is for the phone (device checklist).
- [-] Large-screen and Fold-unfolded layouts: skipped for now (no hardware to check on).
- [x] Icon packs: any pack in the common `appfilter.xml` format (Nova, ADW, Lawnchair and others), chosen under Icons. Covered apps get the pack's icon (work badge kept), others keep the icon style. Parser unit-tested (`AppFilterTest`); an emulator test loads a one-icon debug pack and sees Chrome's icon replaced. Not yet: masking uncovered apps with the pack's icon back, and a real pack on the phone.
- [x] Private Space, nested folders and folders in the dock: decided and written down in `docs/design.md` (Non-goals for now): nested folders will not be built (iOS parity); folders in the dock and Private Space are "not yet", with the reason and what would change it. The README limits line points there.

## 5. Finishing touches

- [x] First-run sheet has three "worth knowing" rows: Edit Home behind a long-press, tapping the island, and where the style settings are.
- [x] README screenshots: Molten metal night and day, the wallpaper picker, Edit Home, Island style (emulator, sample data). The user guide already describes each feature in words.
- [x] A "what's new" note after an update (`WhatsNew`): shown once per version, never on a fresh install, rewritten in `strings.xml` with each release. Unit-tested rules and an emulator test (appears once, then not again).
- [x] Three more built-in island styles (Aurora, Rose, Forest: eight in all) and sharing as text: **Copy this theme as text** and **Paste a theme from text** (the clipboard is read only when you tap Paste; the pasted theme goes through the same checks and preview as a file). An emulator test round-trips a theme through the clipboard.
- [~] Startup and memory check: emulator numbers only. Cold start 2.2 to 2.8 s (`am start -W`, software-rendered emulator); about 250 MB total PSS after settling with the default wallpaper; the Molten metal scene renders in under half a second at 1080 x 2340 on a laptop. The phone numbers are in the device checklist (section F).

## Rules for working down the list

- One item at a time; ship a release when a group of items is done, with honest release notes.
- When an item is finished, tick it here in the same change, and say what was and was not checked.
- Anything that needs the phone goes into `device-checklist.md` with steps and the result that is expected.
