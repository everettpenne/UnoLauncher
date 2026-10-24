# Setting the island's layout so it does not clip the status bar

A process for choosing the width and position of the main island and its capsules, for each state the island can be in, so no status-bar
element is left half-covered. Written down on 2026-10-19 so it can be picked up again. The values found so far are in
[Recorded values](#recorded-values).

## 1. The rule

- **Clipped** means a status-bar element (an icon, a number, a piece of text) is partly hidden by the island. Half a battery, or a "3G" cut
  through, is the failure to avoid.
- **Fine** means every element is either wholly under an island or wholly clear of it. Covering elements is acceptable.
- Status-bar elements come in steps (about 45 to 60 px per icon, more for text). The best place for an island's edge is the gap between two
  icons, so the work is finding those gaps for each layout.

## 2. Reference conditions

The system status bar changes with what is running, so layouts are tuned against a few frozen loads. SystemUI demo mode freezes them on the
emulator (`adb shell settings put global sysui_demo_allowed 1`, then `am broadcast -a com.android.systemui.demo -e command ...`).

| Load | What is in the status bar |
|---|---|
| Light | clock, 2 notification icons, Wi-Fi, signal, battery |
| Typical | clock, 4 notification icons, Bluetooth, Wi-Fi, signal, battery |
| Heavy | clock, a full row of notification icons, alarm, VPN key, Bluetooth, Wi-Fi, signal, battery |

Also fix the device (Pixel 10a), the display and font size, the island size slider and the wide mode.

## 3. Layout classes

Group the island's states into a short list of classes, not every combination. The inputs:

- **How many live activities:** 1, 2, 3 or more (three islands at most: the main island, one on its left, one on its right).
- **Privacy dots:** none, one or two, three.
- **What the main island shows:** a call or timer (text on the pill), music (bars), a live update.
- **An app's icon and name showing** (an app launch, or a notification's app): the live activities tuck into the island and it springs wider.
- **An event flash** (VPN, ringer, Focus and so on) widening the pill.
- **Wide mode** (Off, Wide, Extra wide) and the size slider.

Open and peeked states do not need these values.

## 4. Tuning one class

For each class and each reference load:

1. Open Launcher settings, Island debug (debug builds only). Force the dummy activities for the class.
2. Set width, across (main island) or gap (a capsule) and down for each island until nothing is clipped.
3. Copy the numbers (the page's "Copy numbers" line) and take a screenshot.
4. Choose one set that works for as many loads as possible. Decide the tie-break in advance: which load matters most, and which side of the
   status bar is allowed to be covered.

The numbers are in dp, added to the island's shipped layout (see `IslandLayoutDefaults`). Width is added to the automatic width and the island
grows equally both ways. Across moves the main island sideways. For a capsule, gap is added to its distance from the main island, outward.
Down moves it lower.

## 5. Measuring clipping

To judge by number and not by eye (not built yet):

1. Render the status bar with and without the island over it. The difference is the island's footprint.
2. Split the status bar into its separate elements.
3. An element is clipped if the footprint overlaps part of it, and fine if it overlaps none or all of it.
4. Report a clip count and the clipped elements for each candidate. Sweep width and position per class and load, and review only the
   candidates that score zero.

## 6. Storing the defaults

- A table keyed by layout class, holding width, across or gap, and down for the main island and each capsule.
- Lookup falls back from the most specific class to a general one, so a missing class never gives a bad layout.
- Hand adjustments from the debug page are added on top, so tuning can continue.
- A test checks every class against the reference loads and fails if one regresses.

Today the defaults are one fixed set plus one for "showing an app's icon and name" (`IslandLayoutDefaults`); the table is the next step.

## 7. Decisions to settle

- Which status-bar elements may be covered, and which may not (the clock, the notification icons, the battery).
- Which reference load is the priority when layouts conflict.
- Whether the second activity stays on the left (over the clock and notifications) or sometimes goes right.
- Final numbers are checked on the real phone, since the emulator's status bar is not a Pixel's.

## 8. A lesson about touch

Everything the island draws beside the pill is inside its **touch window**, a small window that takes every touch inside it. It must be
exactly as large as what is drawn, plus a small pad. In the first debug build it was padded by a "slack" amount on every side, which swallowed
touches meant for the top of the app underneath. `touchRect()` now builds it from the island's real frame and each capsule's real extent, and
`IslandDebugTest` pins that it never grows beyond them. Any new layout code must keep to this.

A second way it went wrong (fixed in 0.93.0): the touch window follows the frame the island *reports*, and the report only shrinks once the
live frame matches the frame the island is heading for. When the island's width was adjusted (by hand, then by the grid), the live frame no
longer matched the unadjusted target, so the report only ever grew: after the island had opened once (a tap, a pull, a notification card) the
touch window kept the open panel's size and took taps meant for the top of the app underneath, intermittently, depending on whether the island
had opened since it last started. The target now gets the same adjustments as the live frame, and
`IslandScenarioInstrumentedTest.theTouchFrameShrinksBackWhenTheIslandCloses` fails on the old code. Any adjustment applied to the live frame
must also be applied to `headingAdjusted`.

## 9. The status bar grid (the layout that ships)

Screenshots of the Pixel 10a's status bar, with the island off and on, give the positions of every element (2.625 density, 1080 px wide):

| Element | x range (px) |
|---|---|
| Clock, one-digit hour | 93-160 (a two-digit hour or any 24-hour time is about 19 px wider, and moves everything after it right) |
| Notification icons 1 to 4 | 188-218, 248-279, 305-336, 360-396 (a pitch of about 57 px) |
| Do not disturb (and other extra icons, packed leftward) | 736-767 |
| Signal | 788-842 |
| Wi-Fi | 858-903 |
| Battery | 919-988 |

The notification icons run from the clock to the camera hole, and the right-hand icons are packed against the screen's right edge. So the
gaps between icons are at fixed places: on the left a fixed distance from the clock and then every 21.85 dp, on the right a fixed distance
from the right edge. Android lays the notification icons out only around the camera hole, and the island is far wider than the hole, so icons
run under it. Covering an icon wholly is fine; covering half is the failure. The rule that follows:

**Every island edge goes in a gap** (`StatusBarGrid`, `StatusBarGridTest`). Where a capsule meets the main island, the two edges share one
gap, 1.5 dp either side of its middle. An outer edge (nothing beyond it) sits 1 dp *past* the gap's middle, away from the island: the island's
ends are round and fall back about 1.6 dp at an icon's top and bottom, and in the narrow signal-to-Wi-Fi gap an edge 1.5 dp inside the middle
showed the top of the signal icon above the island's end (seen on the phone in 0.93.0, fixed in 0.94.0). A unit test sweeps every wanted width, one- and
two-digit hours, capsules on either side and zero to three privacy dots, and fails if any edge lands inside an icon.

How each state follows from it:

- **Main island at rest.** The width the island would like (+15 dp over automatic) grows outward to the nearest gap on each side: about 348 px on
  the left (between notification icons 3 and 4) and 726 px on the right (before the do-not-disturb slot). Icons 4 and later, and any extra
  right-hand icons, are then wholly under it; icons 1 to 3 are wholly clear.
- **Showing an app's icon and name (+62 dp wider, 3 dp left) and event flashes.** The same snap, so the island is a few gaps wider. It stops at
  the clock on the left and the battery on the right.
- **A two-digit hour.** The left gaps move right by a digit (7.2 dp), read from the clock's format.
- **Left capsule (the second of three activities).** Covers exactly two icon slots, ending in the gap the island starts at, with a 3 dp seam
  between them, so it is a stadium about 40 dp wide, not a circle.
- **Right capsule.** Starts in the gap the island ends at and covers as few slots as make it wide enough (the do-not-disturb and signal slots).
- **Privacy dots** sit inside the island's right end, not outside it where they would cover half an icon. They ask the island for more room:
  one or two dots take it to the next gap on the right, three to the one after.
- **Over other apps, idle:** the island is a ring round the camera, and the grid is not used.
- **No top camera** (phone turned sideways, or a camera elsewhere): the grid is not used and the islands sit flush.

The numbers come from one phone and one status bar. If the system's spacing changes, update the constants in `StatusBarGrid` (they are in dp)
and the test's icon positions.

## 10. One footprint, a calibration page, and a still Home island (0.95.0)

- **The touch window comes from what is drawn.** The island publishes each capsule's rectangle as it lays them out (`IslandRuntime.capsuleRectsPx`),
  and the touch window is the island's frame and those rectangles, each with a small pad (`touchArea`). There is no longer a separate reserve
  worked out for each side, which is how the touch area and the drawing drifted apart before.
- **Line up with the status bar** (The island, in Launcher settings) draws pink guides over the real status bar where the grid thinks the gaps
  are, and moves them by the left side, the notification icons' spacing and the right side, in quarter-dp steps. The island uses the result
  (`StatusBarCalibration`, its own preferences file), so the grid can be matched to a phone other than the Pixel 10a it was measured on.
- **The Home island no longer moves with the launcher's window.** When Uno's accessibility service is on, its overlay draws the Home island too
  (only while Home is in front, if the island is not set to show everywhere), so unlocking or closing an app no longer carries the island
  with the launcher's window. Without the service, the launcher still draws it itself.
- **The grey island.** Over other apps through "display over other apps" (not the accessibility service), Android draws the island at 80%
  opacity so taps can pass through it; the island page now says so and how to get the solid island.

## 11. Reading the real status bar (0.101.0)

The grid above is one phone's status bar, measured by hand. When the person turns on **Uno Launcher island alignment** (a second accessibility
service, separate from the shade service and off unless they turn it on), the island uses the status bar's real icons instead
(`island/StatusBarReader.kt`):

- **What it reads.** The service is told only of SystemUI's window changes (`packageNames="com.android.systemui"`). A quarter of a second after
  a burst of them, it finds SystemUI's status bar window (type `TYPE_SYSTEM`, at the top, about the bar's height) and walks its tree, keeping
  the bounds of each visible leaf. It reads no text or descriptions and never looks at another app's window. Only the latest set is kept, in
  memory; nothing is stored or sent. The permission ledger lists it.
- **From nodes to icons** (`StatusBarScan.icons`): only small leaves within the bar's height count. Parts that overlap are one icon. Parts
  that only touch are two, because each view's bounds hold its own padding (on the emulator the clock's and the first notification icon's touch).
- **From icons to gaps** (`StatusBarScan.gaps`): left of the camera, the middle of each gap after the first icon (the clock, which the island
  never covers) and room past the last; right of it, room before the first, the middles, and room past the last. These replace the grid's
  gaps on that side in `StatusBarGrid.layout`; a side with no icons keeps the grid.
- **When it is used.** Only while the service runs and the reading was made at the current screen width. An empty read (the bar hidden, as in
  the iOS layout's jiggle mode or with the status rail) keeps the last good one.
- One gotcha found on the emulator: SystemUI's Compose host reports itself not visible while the icons inside it are, so every node's
  children are walked and only leaves are checked for visibility.

## 12. Symmetric about the camera, Uno's status bar, and tidying (0.102.0)

- **Symmetric.** `StatusBarGrid.symmetric` picks one half-width for both sides: the smallest at least as wide as wanted (and as the privacy dots
  need) where *both* edges clear every icon by `EDGE_CLEAR_DP`, never over the clock or the battery. Candidates are the wanted width and each
  width at which an edge just clears an icon. Icons are the status bar as read (`StatusBarLive`) or, without a reading, the grid's slots
  (`gridIcons`, conservative). Capsules sit `SEAM_DP` beside the island and grow outward to the first clear point. If no width fits, the old
  edge-by-edge snapping is used. On the Pixel 10a's measured bar the resting island comes out 355..725 px (centred on 540) where it was 345..729.
- **Uno's status bar** (iOS layout, Launcher settings › Home): Android's bar is hidden on Home and `UnoStatusBar` draws the time centred left of
  the island and alarm, Do Not Disturb, VPN, signal, Wi-Fi and battery centred right of it. The island then has nothing to avoid and is centred
  (`symmetric` with no icons) at the iPhone's pill width. Android's camera and microphone indicators are not drawn; the island's own dots are.
- **Tidy the status bar** (on Line up with the status bar): builds `adb shell settings put secure icon_blacklist ...` from chosen slots, with the
  command that undoes it. Uno cannot write the setting itself.

## Recorded values

Found with the debug page on 2026-10-19 and shipped as `IslandLayoutDefaults`. They are the width the island would *like*; the grid then grows
each edge outward to the nearest gap (shipped in 0.92.0):

| Island | Width | Across | Down |
|---|---|---|---|
| Main island, at rest (before snapping) | +15 | 0 | 0 |
| Main island, showing an app's icon and name (before snapping) | +62 | -3 | 0 |
| Left and right capsules | set by the grid | | |

Three islands at most. Still to check against the real status bar in each state: the app-name state, event flashes, and the capsules.
