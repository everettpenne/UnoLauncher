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

**Every island edge goes in a gap, 1.5 dp inside it** (`StatusBarGrid`, `StatusBarGridTest`). A unit test sweeps every wanted width, one- and
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

## Recorded values

Found with the debug page on 2026-10-19 and shipped as `IslandLayoutDefaults`. They are the width the island would *like*; the grid then grows
each edge outward to the nearest gap (shipped in 0.92.0):

| Island | Width | Across | Down |
|---|---|---|---|
| Main island, at rest (before snapping) | +15 | 0 | 0 |
| Main island, showing an app's icon and name (before snapping) | +62 | -3 | 0 |
| Left and right capsules | set by the grid | | |

Three islands at most. Still to check against the real status bar in each state: the app-name state, event flashes, and the capsules.
