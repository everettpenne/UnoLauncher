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
