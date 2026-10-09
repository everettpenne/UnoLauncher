# Uno design system

Uno Launcher is a deliberate hybrid: **Android behavior, Apple look.** This file is the
single source of truth for the numbers. When a new surface or control is added, it should
pull from these scales instead of inventing its own values — ad-hoc numbers are how
overlaps happen.

- **Behavior follows Android:** window insets, display cutouts, foldable posture, 48 dp
  touch targets, edge-to-edge. These are not style choices.
- **Look follows Apple's HIG:** continuous corners, the dynamic island, iOS control
  colors, liquid glass. Deviations from Material are intentional and listed here.

## Spacing scale

Commit to multiples of 4 dp; prefer the 8 dp grid.

| Step | Use |
| --- | --- |
| 4 dp | inside capsules, hairline breathing room |
| 8 dp | gaps between siblings, small paddings |
| 12 dp | list/pill paddings |
| 16 dp | screen margins, panel padding |
| 24 dp | section spacing |
| 32 dp | panel separation |

Rules: **16 dp** from screen edges, **≥ 8 dp** between siblings, **48 dp** minimum
touch targets. `WindowInsets.safeDrawing` wraps every full-screen layout.

## Corner system (`Corners.kt`)

One family, iOS-style nesting: an inner shape's radius is its container's radius minus
the inset between them.

| Token | Value | Use |
| --- | --- | --- |
| `Corner.small` | 12 dp | rows, highlights, small tiles |
| `Corner.medium` | 18 dp | cards and items inside a panel |
| `Corner.large` | 24 dp | widgets, folders, drop targets |
| `Corner.xlarge` | 32 dp | dock, status rail, sheets, panels, island expanded |
| `Corner.pill` | 50% | capsules and circles |
| `Corner.icon` | squircle, 22.37% of side | app-icon silhouette (Apple's continuous-corner ratio) |

Container surfaces keep circular-arc corners: the glass lens accepts only
`RoundedCornerShape` and throws on continuous-curve outlines at draw time.

## Color

| Role | Light | Dark |
| --- | --- | --- |
| Ink (on-surface text) | `0xFF243A46` | `0xFFEAF3F6` |
| Glass | `0xFFE8EFF2` | `0xFF263A43` |
| Dune gradient top | `0xFF41687E` | `0xFF132832` |
| Dune gradient bottom | `0xFFD8CEB6` | `0xFF463F35` |
| Theme primary | `0xFF30596D` | `0xFF9BC5D7` |

iOS accents: green `IosGreen 0xFF34C759` (switches, charging), dark green
`IosGreenDark 0xFF30D158`, orange `IosOrange 0xFFFF9500` (sliders, highlights),
grey `IosGrey 0xFF787880`. Green = affirmative state; orange = adjustable value.

## Typography

**Inter** (SIL OFL 1.1, bundled as one variable font, `res/font/inter_variable.ttf`) is the
typeface everywhere, so the launcher reads the same on every Pixel and needs no font download.
`DuoTypography` is Material's type scale with Inter as its family (`DuoTypography.kt`); weights
300-700 come from the variable weight axis. Sizes stay Material's; the status rail sets its own:
time up to 18 sp, detail up to 11 sp, both respecting the system font scale. Inter runs wider than
the system font, so tight spots (the island's action buttons) drop to icon-only when a label
doesn't fit. All body text keeps ≥ 4.5:1 contrast against its glass tint.

## Touch and interaction

- Minimum target 48 dp: buttons use `heightIn(min = 48.dp)`, circle controls
  `size(...).coerceAtLeast(48.dp)`.
- Press feedback: spring (damping 0.45, stiffness 260), scale to 0.92, white bloom
  `alpha 0.18` (`PressGlow.kt`). Drag gestures must never fight press feedback.
- Island springs: damping 0.5, stiffness 300; auto-collapse after 5 s.

## Bottom strip (`PageIndicatorLayout.kt`)

The page dots (and, until Uno is the Home app, the "Set as home app" button above them) occupy
a fixed strip. Content pages reserve exactly that space instead of guessing: 8 dp margin +
32 dp capsule + 8 dp gap = **48 dp**, or **96 dp** with the setup button. Home pages, All apps,
the feed page and the native Google Discover window all use `PageIndicatorLayout.reserveDp`.

## Home geometry

- Grid: 4 columns × 6 rows; dock: 4 slots.
- Presets (Customize launcher → Home layout, per cover/inner):
  icon size 40–68 dp, row gap 0–28 dp, dock width 56–84 dp, dock height on screen
  25–75% (or aligned to rows).
- Pager inset math: `pagerWidth = maxWidth - dockWidth - 28 dp`; the pager runs under
  the dock and rail so pages slide beneath the glass.

## Glass rules (`LiquidGlass.kt`)

All surfaces share one lens (`liquidGlass`), tuned by `GlassSettings`
(height, amount, chromatic; defaults 0.55 / 0.55 / 0, each persisted 0–1):

- Refraction height 8–44 dp (capped at 72 px), amount 16–132 dp, depth always on,
  chromatic aberration engages above 5%.
- Tint follows the wallpaper: the photo's muted palette blended 50% into the theme
  glass; the drawn dunes keep the theme glass.

Per-surface tint and blur (alpha over the theme/derived tint):

| Surface | Tint alpha | Blur |
| --- | --- | --- |
| Dock, status rail | 0.12 | 0.75 dp |
| Circle controls, page dots | 0.12–0.14 | 0.75–1 dp |
| Island | 0.34 | 2 dp |
| Feed page | 0.55–0.82 | 2 dp |
| Discover recovery | 0.45 | 3 dp |
| All apps panel | 0.40 | 6 dp |
| Folder panel | 0.90 | 4 dp |
| Glass sheet | surface @ 0.58 | 8 dp |
| Widget gallery | 0.62 | 12 dp |

## Island geometry (`IslandGeometry.kt`)

Window-pixel pure layout, re-read from live insets on every layout (rotation/fold safe).
Cutout = the camera hole nearest the top-center, **tightened to the visible hole** using
`DisplayCutout.cutoutPath`. Android reports a bounding *rectangle* that is often much taller
than the hole and starts at y = 0; sizing from it made the pill run to the top of the screen
and left the Island size slider with nothing to change. Collapsed height is the hole plus a
ring that the slider sweeps from 3 dp up to the most that fits while keeping 6 dp clear above
the island (`TOP_MARGIN_DP`), so the slider always has an effect and the pill never touches
the top edge.

| Constant | Value |
| --- | --- |
| Expanded width | 336 dp |
| Expanded body below hole | 112 dp |
| Edge margin | 8 dp |
| No-cutout capsule | 108–132 × 30–38 dp |
| Collapsed, by the Island size slider | slots 44–80 dp each side, min half-height 12–24 dp, hole surround 0–16 dp; expanded body 96–128 dp |

The collapsed face keeps the time and battery on opposite sides of the hole; the
expanded panel hangs below it. The expanded width is capped at
`screenWidth − 2 × (dockWidth + 16 dp)` so it can never reach the dock strip.

## Adaptive ink (`AdaptiveInk.kt`)

Glass content picks white or dark ink from what is behind it, as iOS does. The wallpaper is
rendered once at 16 x 36 and its luminance kept as a grid; each glass surface measures where it
sits and blends the wallpaper brightness with its own tint. Dark ink is used above the WCAG
crossover where white and `GlassInk.Dark` have equal contrast (about 0.21), with a 0.04 dead band
and a 220 ms fade so a surface never flickers while pages slide past. Applied to the built-in
widgets, the status rail, the page-dots strip and the circle controls.

## Selection lens (`GlassLens.kt`, `PageStrip.kt`)

iOS 26's selection lens: a nearly clear glass capsule that refracts at its rim and magnifies
(1.18x) whatever is under it. On the page-dots strip it rides the pager's fractional position, so
it glides between Discover, the Home dots and All apps as pages scroll; pressing and dragging
along the strip scrubs pages with the lens under the finger and lifts it (scale up to 1.14x).
The strip's items are recorded into their own layer backdrop and combined with the Home
backdrop, so the lens magnifies the icons and not just the wallpaper. Item geometry and the
position-to-centre mapping live in `PageStripLayout`, which is unit-tested.

The strip must be exempt from the pager's own drag handler (`onePageGestures(ignorePress = ...)`),
which listens in the Initial pass and would consume the drag first. The scrub itself listens in the
Initial pass too and consumes only after horizontal touch slop, so a plain tap still reaches its
button. The lens shape must be a rounded rectangle: the glass lens throws on squircle outlines.
Dock icons carry a press-only variant (alpha and lift follow the press spring).

## Scroll edge blur (`EdgeBlur.kt`)

Three stacked blur bands (2, 6 and 14 dp over 100%, 70% and 40% of the strip) fade out away from
the top and bottom screen edges. The top strip is the system inset plus 24 dp; the bottom strip is
the page-dots reserve. They sit below the island, rail, dock and dots and never take touches.

## Island activities (`IslandActivities.kt`)

The pill body is **true black** (`#000000`, an OLED pixel that is off) so the camera hole disappears
into it; only a 2.5 dp outer ring stays see-through, which keeps the lens refraction and rim highlight.
Collapsed, the trailing slot shows, in priority order: an event, the playback equalizer, charging, the
battery. Events flash for 2.6 s and widen the pill to fit their title: ringer (Silent / Vibrate /
Ringer), airplane mode, Do Not Disturb (Focus), charging with its percentage, and app launches. A
ringer change right after a Focus change is dropped (DND moves the ringer too). Each event gives the
pill a small spring pulse. Playback is detected with `AudioManager` (no permission): an animated
four-bar equalizer, and, in the expanded panel, previous / play-pause / next sent as media keys, kept
for 90 s after audio stops so a paused track can be resumed. Titles and artwork would need notification
access and are deliberately not used.

All audio and notification service calls run on a private worker thread: they are binder round trips
that can stall for seconds when those services are busy, and on the main thread that froze the UI.
Only the visible face (collapsed or expanded) is composed: an invisible panel still owns its buttons'
touch targets, which Compose pads to 48 dp, and they caught taps meant for the pill.

## Motion

- Springs for press (above) and island expand; `animateFloatAsState` for values.
- Sheets: Material `ModalBottomSheet`; when glass is on, `GlassModalSheet` renders the
  sheet as an inset glass card and exports its backdrop so controls inside refract the
  sheet itself (`LocalPageGlass`).

## Deliberate Material deviations

1. Squircle app icons (22.37%) instead of Material's circular arcs.
2. iOS green/orange control colors instead of the Material color roles.
3. Liquid-glass surfaces (refraction) instead of tonal elevation.
4. The dynamic island instead of Material's status indicators.

## Spacing audit (known off-grid values)

Audited against: 16 dp edge margins, ≥ 8 dp sibling gaps, 8 dp grid.

| Location | Current | Verdict |
| --- | --- | --- |
| Dock & rail right margin | `padding(end = 12.dp)` | off-grid; use 16 dp |
| Bottom strips bottom margin | `bottom = 8.dp` | fixed (`PageIndicatorLayout`) |
| Bottom-right controls right margin | `padding(end = 12.dp)` | off-grid; use 16 dp |
| Page-dots capsule | `padding(horizontal = 6.dp)` | off-grid; use 8 dp |
| Feed page | `22 / 18 / 14 / 10 dp` | mixed; use 16 / 16 / 16 / 12 |
| Feed entry spacing | `spacedBy(10.dp)` | off-grid; use 8 or 12 dp |
| Sheet body | `horizontal 20 dp, spacedBy(10.dp)` | off-grid; use 16 / 8 dp |
| No-cutout island top | `statusBarHeight + 4.dp` | below the 8 dp rule; use 8 dp |
| Island expanded width | 336 dp fixed | overlaps the rail on cover widths (see below) |

**Top-strip collision:** on a 411 dp cover display the expanded island spans
≈ 37–374 dp while the rail starts at ≈ 315 dp — a ≈ 30–58 dp overlap (depending on dock width). Fix when next
editing `IslandGeometry`: cap the expanded width at
`screenWidth − 2 × (dockWidth + 16 dp)`, or fade the rail while the island is expanded.

## Control panel

The right 30% of a downward swipe on Home opens a glass panel (`ControlPanel.kt`) rather than Android's Quick Settings; Notifications still use the system shade from the left 70%. Tiles: media transport (media keys, no notification access, so no titles), volume (music stream), brightness (system setting), a Ring / Vibrate / Silent selector, and a flashlight. A **System settings** row hands off to the real Quick Settings through the optional accessibility service, so everything the panel doesn't cover is one tap away.

Principles:

- No new always-on grant. Brightness needs "Modify system settings" and Silent needs Do Not Disturb access; each is requested only when the user taps the control, and the panel shows an "Allow ..." row instead of a dead slider.
- Nothing runs while the panel is closed. `SystemControls` registers its receiver, torch, playback and brightness listeners when the panel enters composition and removes them when it leaves.
- Every binder call (audio, camera, settings, notification policy) runs on one worker thread; slider drags are coalesced to the newest value, and the thumb moves optimistically while the write follows.
- The rules (volume and brightness mapping, when Silent needs access, which camera has the torch) live in `ControlLogic.kt` and are unit-tested.

## Extras

Everything below is opt-in or user-arranged, kept in one preference file (`extras`, `ExtrasStore.kt`) with its rules in `ExtrasModel.kt` so they are unit-tested.

- **Panel tiles** (`PanelLayout`): a saved order and a hidden set. An old saved order keeps its order and gains any tile a newer build added; with everything hidden, System settings returns so Quick Settings stays reachable. Ringer and Flashlight share a row when adjacent.
- **Focus** (`Focus.filter`): applied in `MainActivity` to the app list the UI sees, not to the model, so the saved layout and pins never change and hidden apps return untouched. The optional ringer change only touches the ringer when it was ringing and only restores it if it is still on vibrate.
- **Island tools** (`IslandTools.kt`): the timer is an alarm-clock alarm when exact alarms are allowed and an inexact Doze-friendly alarm otherwise, plus an in-process trigger; whichever arrives first rings. State persists so a timer that ended while the process was gone is shown as done. The island's torch worker exists only while the tools face is open.
- **Themed icons** (`drawThemed`): the app's monochrome layer in the palette's ink on its glass colour; apps without one are greyscaled and pulled toward the glass colour. Icons are baked into bitmaps, so a style or light/dark change rebuilds the cache.
- **Notification access** (`NotificationFeed.kt`): a `NotificationListenerService` that is only bound after the user enables it. All system calls run on a private worker thread. It publishes badge counts (ongoing notifications and group summaries are not counted), the current media session, and a throttled app-name peek. Apps without a launcher icon are invisible to the launcher under Android's package visibility, so they never peek.
- **Contact search** (`ContactsSearch.kt`): names only, on demand, word-prefix matching (`ContactMatch`), at most five results.

## Color from wallpaper

`WallpaperAccent.kt`: the wallpaper is rendered small exactly as Home draws it (photo or dunes) and Palette picks its vibrant swatch. `AccentMath.derive` then produces three colors of one hue (accent, glass tint, ink), walking lightness until the accent has 3.2:1 contrast and the ink 7.5:1 on the glass, because equal HSL lightness is wildly different in perceived brightness across hues (yellow is the failure case a fixed lightness hits). It is provided as `LocalWallpaperAccent`; glass tint, sliders, switches and themed icons read it, and the icon cache key includes it so icons rebuild when the wallpaper or theme changes.

## Rim light

All glass surfaces take their edge highlight from `GlassRim.Light` (`Highlight.Default` from the glass library): a directional specular rim, brightest where the edge faces an upper-left light and fading opposite, rather than the uniform `Highlight.Plain` outline we used before. Changing the light (angle, intensity, falloff) is a one-line edit there. A tilt-driven angle is possible later because the style takes the angle as a parameter.

## Search that answers

`SearchSmarts.kt` is pure: a small recursive-descent parser (precedence, right-associative ^, unary signs, postfix percent where "50+10%" means ten percent of 50) and a unit table with a base unit per kind, so any pair of units of one kind converts through it; temperature goes through kelvin because it needs offsets, not ratios. A result is only offered when the query contains a real operator, function or percent (so a bare number or an app name never turns into an "answer"), and division by zero, negative square roots and unbalanced parentheses give nothing.

## Split screen

Android gives a launcher exactly one supported route to split screen: launch the second app with `FLAG_ACTIVITY_LAUNCH_ADJACENT` while the first is in front. Two findings shaped `MainActivity.launchSplit`: starting both apps in a single `startActivities` call leaves the first hidden behind (its task is created but not made visible), so the first app is launched on its own; and the second must wait until it is actually in front, so it is opened from `onStop` (this launcher leaving the screen) after a short settle, with a 2.5 s timeout if that never happens. No accessibility global action is used, so the feature adds no grant. The arrangement (stacked on a phone, side by side on a wide inner screen) is Android's choice and cannot be forced.

## Tilt-following highlight

`GlassRim.angle` is snapshot state read inside the glass modifiers' highlight lambda, so a change re-runs only their drawing, never composition. `TiltHighlight` feeds it from gravity: the rim rotates against the phone's roll so the light stays fixed in the room, clamped to 75 degrees either way and faded out when the phone is flat (no meaningful roll). Samples are smoothed and only applied when the light has moved 2 degrees, because every glass surface redraws on a change.

The strength setting (`TiltMath.gainFor`, 1x to 6x) multiplies the roll, and `GlassRim.boost` (0..1) shapes the rim: a sharper falloff (`1 + 8b`), a little more intensity and a thicker line. The first version simply made the rim brighter and thicker, and at full strength every edge became a uniform white outline, which hides the direction of the light; contrast between the lit and unlit edges is what makes movement readable, so strength raises falloff, not overall brightness.

## Glass icons and app shortcuts

`drawThemed` paints a themed icon in layers: a translucent gradient tile, the glyph (the monochrome layer, or the foreground's silhouette in ink for apps without one) over a blurred offset copy as a shadow, a radial white sheen, and a diagonal-gradient rim stroke. Icons are bitmaps, so this is a painted look, not backdrop refraction; the colors come from the palette or `WallpaperAccent`, so the icon cache key already rebuilds them with the theme.

`AppShortcuts` reads manifest and dynamic shortcuts for the icon's activity through `LauncherApps.getShortcuts` (IO dispatcher; the sheet is usable at once and the entries fill in) and starts one with `startShortcut` on its own thread. `hasShortcutHostPermission` is false unless Uno is the default Home app, in which case the list is simply empty.

## Widget stacks

`WidgetStacks`/`StackRules` keep, per base widget slot, a list of extra provider widget ids in their own preferences file. Merging a widget removes its grid placement but registers its id first, and `LauncherModel.retainedWidgetIds` includes members of stacks whose base is still placed, so the existing id pruning never unbinds them; a stack whose base disappears is no longer retained and its members are pruned with it. `WidgetSlot` renders the base or the selected member. The switcher is a dot rail rather than a vertical swipe because Home's downward swipe is already the shade/panel/search gesture. The layout model, its schema, undo and backup are deliberately untouched.

## Spotlight

`Spotlight.kt` is an overlay at the same level as the control panel, so it covers the whole window. `SpotlightRank` orders app matches (name prefix, then a word prefix, then contains, alphabetical) and feed matches (every word must appear), and it reuses `SearchSmarts` and `ContactsSearch`. The left pull-down is a setting (`LeftSwipe`) so notifications remain the default.

## Feedback

`UnoFeedback` maps a `Cue` (tick, page, open, toggle, launch, confirm) to a haptic and an optional system sound effect. The switches live in one place; the sound path plays `AudioManager.playSoundEffect` on its own thread, which follows the system touch-sounds setting.

## Island ring

`IslandRingLogic.choose` picks the ring (timer over charging; none while an event flashes, while expanded, or while a timer rings). The outline is built by hand from the top centre because a library round-rect starts elsewhere, and the arc is a `PathMeasure` segment starting at 0.

## Search suggestions (hand-off)

Showing results from F-Droid, Aurora or the web inside Uno would mean Uno contacting those servers (F-Droid by scraping search.f-droid.org or downloading its whole index; Aurora has no public API since it fronts Google Play; a web search needs an engine), which conflicts with the launcher's rule that it contacts only feed addresses the user adds. So `SearchHandoff.kt` hands the search to the user's own apps: `HandoffLogic` decides when to offer (nothing else matched or answered; stores need 3+ characters), which browser (the user's pick, else Vanadium, else any) and which installed stores, and builds the intents (`market://search?q=...`, and `ACTION_WEB_SEARCH` carrying only the words so the browser's own engine decides). `HandoffResolver.open` resolves the intent first and says so if nothing can handle it, falling back to any web-search handler for browsers. Stores always show their well-known name because some devices label the Play Store package oddly. Package visibility comes from `<queries>` in the manifest.

## Uno Keyboard

`keyboard/KeyboardModel.kt` holds every typing rule with no Android classes (pages and rows, shift off/once/locked, caps from the editor's cursor-caps mode, double-space full stop, Return kind from the IME action, password and number-field detection, accent sets) and is unit-tested. `UnoKeyboardService` is an `InputMethodService` that hosts Compose: an input method has no lifecycle owner, so the service itself implements `LifecycleOwner`, `SavedStateRegistryOwner` and `ViewModelStoreOwner` and attaches them to the window's decor view and the `ComposeView`. `KeyboardUi.kt` paints the keys flat, close to iOS (the keyboard lives in its own window and cannot sample what is behind it): white letter keys and grey special keys on a pale panel in light mode, the dark equivalents at night, a thin darker line under each key, an iOS-style hollow shift arrow, a lowercase "return" and a blue action key. The keys are 46 dp tall in portrait and 36 dp in landscape, and the keyboard is capped at 640 dp wide so an unfolded screen does not stretch it. The magnified key (a balloon rising out of the key, with the key hidden under it) and the alternatives strip for a held key are drawn in layers inside the keyboard's own window, not as popups, and a long press tracks the pointer across the strip. The strip of suggestions above the keys is a lane that is always present (except in password fields, which never preview keys), so the balloon over the top row always has room and is never clipped. `onEvaluateInputViewShown` is overridden to honour Android's "show on-screen keyboard with a physical keyboard" setting: the stock rule hides the keyboard whenever a hardware keyboard is configured, as the emulator reports.

Because an input method sees everything and the APK holds the internet permission, `KeyboardPrivacyTest` scans every file in the package and fails on network, logging and file-writing APIs, and on anything that writes the clipboard. The keyboard may read the clipboard in exactly one place, the paste action, which runs on a tap: a listener learns only that the clipboard changed and what type it is (never its content) so the Paste button can appear, and a test pins that.

Not built yet, deliberately: a dictionary and autocorrect, next-word prediction, swipe typing, emoji, voice input, one-handed and floating layouts, and languages other than English (US).

## Pull-down routing

`PullDownRouting.route` (pure, unit-tested) decides where a downward pull on Home goes from the column it starts in (left 70% or right 30%, as before) and how far down the gesture area it starts (0 at the top). The gesture handler reports that fraction through the new `onDownwardSwipeAt` callback (the older `onDownwardSwipe` remains for the instrumented tests). The default, `SMART`, sends a pull that starts in the top 22% to notifications or the control panel by column, and anything lower to search; `NOTIFICATIONS` and `SEARCH` keep the old whole-left-side behaviours. The preference key is new (`pullDown`) so earlier saved values cannot pin people to the old behaviour; `migrate` keeps an earlier explicit choice of Search.

## Keyboard suggestions and autocorrect

`WordEngine` (pure Kotlin) holds word scores and a next-word table loaded from `assets/keyboard` on a background thread when the service starts (no suggestions until then). `correction` scans only words that start with the typed word's first or second letter and within two letters of its length, scoring `commonness/10 - 4*editCost`; `editCost` is a Damerau-Levenshtein variant where a neighbouring-key substitution costs 0.6, any other 1.3, a transposition 0.8, and a missing letter that doubles an adjacent one 0.5 (so "occured" and "begining" fix cheaply), with an early exit once the row minimum passes the limit. The allowed cost grows with word length so short words are corrected only for one slip. `Suggest.parse` extracts the word and the previous word from the text before the cursor and decides whether the token is plain prose; `autocorrect` additionally skips mixed-case names and words the person has undone. The service applies a correction on space, closing punctuation and Return, remembers it in `lastFix`, and a backspace straight after restores the typed word and adds it to a per-session ignore set. Input types gate everything (`KeyboardModel.allowsSuggestions/allowsAutocorrect`). The dictionary is built from the SymSpell English list (words: lower-case letters only, log-scaled scores) and its bigram list (the five most common followers of 7,489 words); regenerate with the script described in `assets/keyboard/README.txt`.

## Keyboard speed and haptics

Per-keystroke cost was dominated by calls into the app. `TextMirror` keeps the last 80 characters before the cursor, updated by the keyboard's own commits and deletes; `ExpectedCursor` remembers the cursor positions those edits produce so the selection updates the app sends back are recognised as echoes and ignored, and only an unexpected cursor (a tap, a paste, the app rewriting text) triggers a re-read. Deleting more than the copy holds, a selection, Return, and cursor sliding all invalidate it and fall back to reading the field. Capitalisation is `KeyboardModel.capsFromText` over the mirror, reproducing Android's sentence/word/character rules. Suggestions run on a single background thread with a sequence number so stale results are dropped.

Touch handling: a letter commits on `awaitFirstDown`. Its handler is keyed on `Unit` and reads the current key through `rememberUpdatedState`, because the label changes with shift and keying on it restarted every letter's handler mid-press. The preview and the alternatives strip are overlays in the keyboard's own window (a `Popup` per press creates a window each time, which blocked the main thread long enough on a slow device for Android to declare the keyboard not responding). They are sized with `matchParentSize`, never `fillMaxSize`: the keyboard window wraps its content, and a child that fills a wrapping window makes it as tall as the screen, which Android reads as a keyboard covering everything and hides it as soon as a key is held. `KeyboardUiState` and `KeyboardActions` are marked `@Stable` and the preview lives in its own composable, so pressing a key redraws that key and the preview and not the whole keyboard.

Backspace is grapheme-aware (an emoji, a flag or a symbol with its variation selector goes as one) and, held for a while, takes whole words. It no longer asks the app whether text is selected (a blocking call on every repeat): the selection state comes from the selection updates. A correction is worked out on the suggestion thread with the suggestions and kept for the space bar, so the dictionary is no longer scanned on the main thread when a word is finished; `WordEngine` caches the best completions of short prefixes and no longer builds a list on every `contains`. The word lists load on a background-priority thread so they do not compete with the keyboard's first draw.

## Keyboard features

The emoji panel (`EmojiData`, parsed from `assets/keyboard/emoji.txt`) is a scrolling grid with category tabs beside the ABC and delete keys; recents are kept in memory for the session only. The switcher key is shown only when Android says there is another keyboard to switch to, and the input method declares `supportsSwitchingToNextInputMethod`. A Paste button appears in the suggestion lane when the clipboard holds text. Long-pressing a symbol or digit opens its variants (dashes, currency, quotes, degree, and so on) as well as a letter's accents. One-handed mode (Customize, Control panel & extras) pushes the keys to one side at 80% width and leaves a button on the free side to restore full width. In landscape the fullscreen extract editor is switched off so the app stays visible above the keyboard.

`KeyHaptics` fires `VibrationEffect.Composition` primitives (LOW_TICK, TICK, CLICK) from `HapticProfile`, with a predefined tick on motors without them, on a `HandlerThread`, using `VibrationAttributes.USAGE_TOUCH` on API 33+ and checking `HAPTIC_FEEDBACK_ENABLED` otherwise. Strength 0.6 reproduces the base profile and scales linearly; 12 ms rate limit.
