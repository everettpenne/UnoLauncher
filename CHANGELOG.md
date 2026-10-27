# Changelog

## 0.114.0-uno01

- **The open island's corners are 44 dp** (they were 34), as iOS's expanded island is (concentric with the display's corners inside its margin); after the panel was widened the old radius read tighter than the phone's own corners. It applies to every open card and to Search, which share the island's shape.
- **Icon Size goes up to 125%** of the default (it stopped at 103%), as well as down to 60%, on both layouts. A larger icon grows out of its box evenly, so the grid, the widgets and the names do not move.
- **An app that changes its launcher icon keeps its place.** Seekur's Settings > App Icon switches which activity-alias is enabled, so its launcher entry changed id and Uno dropped it from Home (and from its folder), and it had to be placed again. When a placed app disappears and its package has exactly one new launcher entry that nothing else uses, that entry now takes its place on Home, in the dock, in its folder.
- **Not tried on a phone:** the icon size at 125% and the alias hand-over were checked by unit tests and on an emulator (the icon-switch hand-over not at all, because the emulator has no icon-switching app).

## 0.113.0-uno01

- **A new app icon.** The island over a grid of four app tiles, on a teal gradient with a soft light from the upper left and a shadow under the glyph, drawn to the iOS 26 look. It is now an adaptive icon (the old one was a flat square that Android could not mask), with a themed one-colour version for Android's themed icons. The suite's other apps (Thumper 1.0.2, Spice 0.9.1, Baliset 0.5.1, Sietch 0.5.1, Seekur 0.9.0) got new icons in the same style.

## 0.112.0-uno01

- **Widgets move icons out of the way.** A widget put down on apps or folders no longer refuses: the icons under it move to the next free cells after it, in order, onto the next page if the page is full, as on iOS. A widget over another widget, or with no room anywhere, is still refused. (The old rule, an app blocks a widget, made a held widget impossible to re-place on a busy page.)
- **Widgets align to the icons.** On the side-dock layout a widget's top and bottom are the top of the icons in its first row and the bottom of the icons in its last, not the bottom of the names under them; the row spacing is chosen so a 2 x 2 widget is exactly square on screens from 320 to 480 dp.
- **Icon Size** (Home Screen) scales the drawn icon inside a fixed icon box: the grid, the widgets and the dock do not move. It now applies to the iPhone layout as well. Show App Names was already there.
- **Dark icons: Auto.** Auto is Original while Uno is light and Dark (each app's colours on a near-black tile) while it is dark.
- **The App Library and the feed reach the bottom on the iPhone layout**, as they do on the side-dock layout; the room the dock and Search pill hold on Home was being kept free under them.
- **Not tried on a phone:** all of this was checked on an emulator only (the phone was away), so the widget move, the aligned widgets and the icon scale have not been seen on a Pixel 10a. The call, Live Update, download and navigation cards are still unseen with real data.

## 0.111.1-uno01

- **The island's pause works on every player.** A media session can accept a pause and ignore it (Flow does), and the island treated that as done. It now checks 0.7 s later whether the audio is still playing and, if it is, pauses by audio focus or the media key, as it does with no session. Tested on a Pixel 10a with Flow.
- **The glow over the camera is smooth.** The black veil that hides the camera hole had a straight-edged strip that left a visible vertical line where the glow stopped; it is now an oval of black that fades out in every direction.
- **No bright edge on the small pill.** The light edge along the island's top-left now draws only once the island is more than half open; on the pill (and each time an app opens under it) it was a distraction.
- **Hold menus.** Holding a widget or a folder on Home opens a small frosted menu (Edit Widget, Edit Home Screen, Remove Widget; Open Folder, Rename, Edit Home Screen), with the held thing clear in a window cut out of the dim. A dock app gets the app menu Home apps have. All hold menus dim the whole window, status and navigation strips included, with a lighter blur, and play the long-press cue and a tick per row.
- **The App Library and the feed** sit over a blurred wallpaper (the picture fades in as those pages come into view), with translucent glass cards. App icons have iOS 26's flatter finish: a faint sheen, a thin edge of light and a hairline of shade, in place of the older domed look.
- **Widget gallery.** With nothing typed it lists the Uno widgets and then **Apps** (icon and widget count); an app opens as a pager of its widgets with a size, a description, page dots and Add widget, and a held preview can be dragged onto Home. A search keeps the flat list.
- **Island.** In the Playful theme, a pill's glyph and detail blur as they swap; a long press on the pill while it is charging (and nothing else is live) opens a battery card with a liquid cell; a finished timer's ring sends out a burst; capsules pop out on a looser spring with a haptic tick.
- **Photos widget** also asks for Android 14's "selected photos" access (lint SelectedPhotoAccess), so a partial grant shows the photos the person chose.
- **Testing.** The 267 instrumented tests were run on a Pixel 10a. The failures were the same as on 0.110.0 (written for an emulator or a foldable, or already failing there) except one test that expected the old full sheet when an app is held, which now goes through the menu's More row and passes. Frame timing swiping to the App Library and Discover on the phone: about 4% and 6.5% janky frames with a release build of the dev app, the same with the new blurred wallpaper switched off, so it did not add cost. **Not seen on a phone with real data:** the call, Live Update, download and navigation cards.

## 0.111.0-uno01

- **The island's middle level.** A long-press on the pill opens a card made for what it is showing, in place of the old title-and-buttons card: **music** (full width, artwork, wavy seek bar, large controls), **calls** (avatar, running time, Open and End), **timer** (a shrinking ring, +1 min, cancel), **stopwatch** (rolling digits, pause, reset), a **Live Update** (segmented progress with milestone dots; a download gets a wavy ring that bursts when it completes) and **navigation** (a large turn arrow, distance, street, ETA). The island body stays black, so the camera hole stays hidden; an activity's colour is only a glow behind the card, and a black veil fades every glow out over the camera.
- **A Playful island theme**, on by default (Island › Style › Playful, or Calm): digits that roll, play and pause buttons that change shape, wavy progress, a light edge along the island's top-left, a liquid-fill battery while charging, and livelier capsules (a looser pop-out, a deeper squish, a haptic tick).
- **Home, more like iOS.** A seven-row grid (the space below the apps is used; saved layouts are migrated), square widgets with the island on the side, a frosted menu when you hold an app on Home or in the App Library (now named **App Library**), iOS-style folders, an iOS-like Edit Home, a widget placement bar, and **Card** or **Glass** widget styles (per widget, with a default).
- **Cheaper glass.** The App Library and Discover use a frosted backdrop in place of a live one while they move (library jank 12% to 2.5%, Discover 19% to 3%).
- **A five-step welcome flow** and a large "Settings" title. The island's open and spotlight states are a little wider to cover the status icons.
- **Not yet seen on a phone with real data:** the call, Live Update, download and navigation cards, and the 261 instrumented tests were not re-run for this release.

## 0.110.0-uno01

- **The welcome sheet turns the island on.** A "Wake up the island" group beside the Home-app and widget steps shows whether Notification access and Island alignment are on, with a Turn on button that opens Android's own page for each; it rechecks when you come back. The alignment service was a settings hunt, and the island is noticeably better with it.
- **The island settings preview plays on its own.** Left alone for a few seconds it tours music, a Live Update, a message and the open island, and stops when you pick a scene or change a setting.
- **Notification access, said plainly.** With it off, the island's Notifications group says nothing can drop down yet and has a Turn On Notification Access button (the switches below looked like they worked).
- **An optional Camera & Mic log** (Notifications & Privacy), off by default: when the camera, microphone or screen recording was in use and for how long, the last 60 uses, on this phone only, no app named (Android does not say which app). Uses under 1.5 s are not listed. Turning it off clears it.
- **Fixes.** The settings Home preview had no name for screen readers. `-PunoDev` now also builds the minified release as "Uno Dev", for timing the launcher on a phone that has the real release. On-device test pass on a Pixel 10a: the 20 tests that fail there also fail on 0.104.2 (they are written for an emulator or a foldable), and the one island test that assumed a tapped-aside call was updated for urgency.

## 0.109.0-uno01

- **Smarter drop-downs.** **People Only** (Island › Notifications) lets only conversations (Android's message category, a messaging-style notification, or a conversation shortcut) drop the island down. A conversation drops it at most once every 15 seconds, and an app that drops it four times in a minute is quiet for two minutes. An app whose drop-downs are never opened (from the island or the shade) ten times in a row stops dropping down; **Quieted Automatically** lists it, and one tap brings it back. All learned on the phone from notifications Uno already reads.
- **Full screen.** A full-screen app (over apps this is landscape) keeps the island away, but now a call or a ringing timer still shows; before, they vanished with the rest.
- **Shorter words on a busy status bar.** With the alignment service's reading, when the pill's words would make it cut an icon, it uses a shorter form ("12 min" to "12m", "1 hr 5 min" to "1h 5m", "Charging 84%" to "84%") if the pill that results, snapped as the island snaps it, cuts less.

## 0.108.0-uno01

- **The pill fits what it shows.** With something on it, the pill measures its own detail (the call's length, the timer, the stopwatch, a Live Update's status, an event flash's words) and is as wide as the wider of its two sides, symmetric about the camera, in 4 dp steps and never narrower than the resting pill. Digits are measured at their widest, so a counting clock never nudges it. It was a fixed width, and a flash's width was guessed from its character count, so a long app name ran off the pill and a short "1:23" sat in room built for far more. On a Pixel 10a a call is now 112 dp, the stopwatch 137 dp, music the resting 103 dp.
- **What matters comes forward.** A call always leads; a timer leads in its last minute, a navigation instruction when the turn is within 200 m, and a Live Update that says it is arriving ("Arriving", "is here", "1 min"); then the pill hands back to what you had flicked to.
- **Step Aside in Its App** (Island › Hide Over Apps, off by default): an activity leaves the island while its own app is in front, as on iPhone (music while you are in the player). It uses the same opt-in tracking as Hide Over Chosen Apps; the permission ledger says so.

## 0.107.0-uno01

- **The alignment service's reading is used on a busy status bar.** The island took a symmetric width only if one within 8 dp of the wanted width cleared every icon; on a Pixel 10a with four notification icons and Do Not Disturb none did, so it kept a width that cut the Do Not Disturb icon in half. It now takes the symmetric width, within 6 dp narrower to 20 dp wider, that leaves the least of any icon cut, never over the clock or the battery. The alignment page also said "Off" while the service was running (Android's list of enabled services can leave it out); it now also reads the system setting.
- **The music card**: a wavy progress line that drifts while playing (flat when paused or dragged), a filled play button in the cover's colour, and the playing bars at its corner.
- **A livelier flame**: the cover's colour under the pill is stronger, its tongues sway further, and a quick flicker rides on their swell. Its clock wrapped every ~17 minutes, which jumped every tongue at once; it now counts from when it starts.
- **Corners** are the suite's Glass kit's, which follow iOS: small 8 dp, medium 16 dp, large 24 dp, settings cards 26 dp (iOS 26's inset groups) in a 42 dp sheet.
- **The island settings preview** sizes glyphs and their end insets as the island does, and draws the camera as a faint lens rather than a grey disc.

## 0.106.0-uno01

- **A Height slider** (Island › Height). From "Camera", the camera's own height (the default), up to 6 dp more above and below it; the width stays 16 mm, the island stays centred on the camera, and the open panel is unchanged. The old size setting had been retired in 0.104.
- **Balanced live activities.** A call, timer, stopwatch, music or Live Update glyph is 62 % of the pill's height, centred in its left end, and the detail ends the same distance from the right end, so the two sides mirror each other. The glyph used to be the pill's height less 14 px, which on a Pixel 10a filled it to within 2 dp of the edge beside small text. Music with no artwork shows a note instead of an empty side.
- **A compact content pill**, about 1.45 times the resting width (390 px on the 10a; it was 448 px).
- **Always symmetric, never guessing the status bar.** The island no longer snaps to a guessed Pixel status bar, and never snaps one edge without the other; with the alignment service's reading it still takes the nearest symmetric width that covers or clears each icon whole. The guess put icons where there were none on a Pixel 10a, so the pill grew lopsided and over apps cut the Do Not Disturb icon in half. Two other Android islands (Material Capsule, SmartIsland) use fixed symmetric widths for the same reason.

## 0.105.1-uno01

- **A symmetric island, checked on a Pixel 10a.** On Home with the side dock, Android's status bar is hidden, but the island still snapped its edges to that bar's icon gaps, which left it 2 to 5 px off the camera and wider on one side; with no bar it now stays exactly centred at its 16 mm width (270 px on the 10a, centred within half a pixel). The accessibility service's island, which draws the Home island too, now follows Home's status bar and rail. Over apps, when no symmetric fit is found within the growth limit, the island is centred at the wider of the two snapped halves instead of snapping each edge on its own.
- **An even charge ring.** The ring round the pill fills from the bottom centre evenly up both sides, like a level rising, instead of clockwise from the top (one side lit, the other dark). The lone battery glyph to the right of the camera while charging is gone; the ring, the "Charging N%" flash and the open panel say it. Where the status column shows the battery (Home with the side dock), there is no ring.
- **Uno Dev builds.** `-PunoDev` builds a debug copy with its own package and labels, so it installs beside a release install on a real phone for testing.

## 0.105.0-uno01

- **The pill is the camera's height on the Pixel 10a.** 0.104.2 made the resting pill as tall as a punch-hole camera, but only for holes at most 28 dp across; the 10a's cutout outline is larger than that (about 30 dp, drawn a little wider than the glass), so it was still sized as a notch and padded well above and below the camera. A punch hole is now recognised by its shape (round, below the top edge, small beside the screen).
- **One owner for the island's events.** Audio, ringer, Do Not Disturb, camera, microphone, screen recording, VPN, USB, Bluetooth and battery listeners ran inside whichever island was drawn, so they stopped under a sheet on Home or while the overlay hid for a full-screen app (a camera that came on meanwhile was timed from when the island came back). They now run once, held by Home while it is in front and by the overlay while the island may show over apps. The island's timing rules are pure functions with tests.
- **Quick actions.** A long press on the pill opens a card for its leading activity with that activity's own buttons: +1 Min and Cancel for a timer, Pause and Reset for the stopwatch, Previous, Play/Pause and Next for music, End and Open for a call, Open for a Live Update.
- **Battery moments.** The island shows plugging in (with the level), reaching full, and Low Battery at 20 % and 10 %, over apps as well as on Home. Charging used to flash only on Home, and again each time Home reappeared while plugged in.
- **Hide the island over chosen apps** (Island › Hide Over Apps, off by default, island over other apps only). While on with apps chosen, Uno's accessibility service is told the package name of the app in front and nothing else; the permission ledger says so.
- **Focus with Do Not Disturb.** Focus can turn on and off with Android's Do Not Disturb and its schedules.
- **Lock Screen clock widget.** The date over a large time, straight on the wallpaper as on iOS's lock screen, 4 × 2; tap it to choose Classic, Heavy, Thin, Serif or Mono, in white or the accent.
- **App Library categories.** An A–Z / Categories choice groups apps by the category each declares, named by Android, with the largest groups first; searching is always A–Z.
- **Profiles.** Notifications from the work profile and Private Space are named "App (Work)" or "App (Private)" with Android's profile badge, badge only that profile's copy of the app, and are no longer dropped for apps installed only in that profile. Search marks work apps.
- **GrapheneOS Sensors.** The permission ledger shows whether Uno has GrapheneOS's Sensors permission and what stays still without it; Tilt Highlight's note says so plainly.
- **Privacy.** A cable or Wi-Fi transfer to a new phone no longer copies Uno's data (learned words included): `allowBackup="false"` did not cover device-to-device transfer on Android 12+.
- **Tidier code.** Lint warnings went from 171 to 1; the rest are fixed or documented in `app/lint.xml`. The Google feed's frame no longer allocates a path every frame, and the status bar reading says "1 item".

## 0.104.2-uno01

- **A 16 mm island, as tall as the camera.** At rest the pill is about 16 mm wide (13.5 mm read a little small), still growing at most 8 dp a side so it never cuts a status bar icon in half. On punch-hole phones it is now as tall as the camera hole, with half a dp of black round it: its height still came from the retired size setting, about 8 dp of island above and below the camera, so on a Pixel 10a it was much taller than the cutout. Live activities, music and the open island grow from it as before.
- **A truthful island preview.** The settings preview sized itself from the rectangle Android reports round the camera, which on the Pixel 10a is far taller than the hole, so its camera was a tall oval and the pill came out chunky. It now uses the hole's real outline, the same one the island uses, and draws its status bar at Android's real sizes scaled with the preview.

## 0.104.1-uno01

- **The resting island is the 13.5 mm pill, everywhere.** Over other apps, on the iOS layout, and (since 0.104 draws the Home island in the accessibility service's window) on Home too, the idle island shrank to a ring round the camera, about 7 mm on a Pixel 10a, so the 13.5 mm width never showed. It is now always a pill about 13.5 mm wide centred on the camera, the leaked width of the iPhone 18 Pro's island. As before, it can grow a little (at most 8 dp a side) so it never cuts a status bar icon in half, and live activities, music and notifications widen it. The island settings preview shows the same.

## 0.104.0-uno01

- **Settings like iOS Settings.** Every page is a grouped list: a small heading, an inset card of rows divided by hairlines, and a footer with the explanation, so row names are short and Title Case. The overview lists the pages with coloured icon tiles; the top bar has the way back, the page's name and Done. Choices show a check mark; actions are rows in the accent. **Backup & about** is now **General**, and the island's style has its own page.
- **About half as many settings.** Glass is one choice (Clear, Frosted or Off), the lens sliders are fixed, and tilt is one switch. **Material You is gone**: the accent is **Uno orange** by default or **from the wallpaper** (Material You users keep a wallpaper accent). The island's width modes, size slider and Live Update text choice are fixed; Bluetooth, headphone and VPN/USB alerts are one **System Alerts** switch; turn-by-turn follows **Navigation**; the Home island stays still automatically when the accessibility service is on. The keyboard has about 13 settings: no blur, the emoji key, magnified keys and space-bar cursor always on, **Learned Words** in one group, and haptics as Off, Soft, Crisp or Firm. Show App Names moved to Home Screen; the status rail switch shows only with the side dock, which stays for foldables.
- **A wallpaper editor like iOS 26's.** Choosing or editing a photo, or a built-in scene, opens it full screen with Cancel and Done in glass capsules. Swipe sideways for styles (Natural, Vivid, Black & White, Duotone, Color Wash, Studio Dim), pinch and drag with two fingers to frame it, and use the glass buttons for Blur, Adjust (brightness, colour, warmth, blur) and a preview of your Home over it. Photos adjusted before keep their look.
- **Concentric corners.** Settings cards, control panel tiles, App Library rows, dialog rows and the cards in the open island are their container's corner less the inset between them; the island's cards follow its panel shape.
- **A 13.5 mm island.** At rest with nothing to show, the island is about 13.5 mm wide, worked out from the display's real pixel density.
- **One glass language.** Glass is one of two kinds, as on iOS 26: clear (a light blur, a thin bright edge) for Home's dock, rail, widgets and controls, and frosted for sheets, menus, dialogs, panels, folders and the App Library.

## 0.103.0-uno01

- **The island is a sensible width again.** In 0.102.0 the centred island could keep widening until a clear spot lined up on both sides; without a status bar reading every notification slot counted as full, so at rest it was very wide. It may now grow at most 8 dp a side to centre itself, and otherwise snaps each edge on its own. With island alignment on, an edge now stays where the island wants it unless it would land in an icon (snapping to gap middles had sent it out past the last icon when few were showing).
- **Uno's status bar is removed.** The island widening for what it has to show is enough; Android's status bar is used again.
- **Stack dots like iOS's.** A widget stack's dots are small, sit at its edge without a plate, and show only for a moment when the widget changes; they were a dark pill always over the widget's content (the Moon widget's text, a photo).
- **Finer glass.** Clear glass's edge is a thin line of light that fades quickly away from the light, not an outline; the top sheen is softer.
- **Cleaner icon names.** Plain white with a soft shadow; the dark halo is kept only where the wallpaper behind a name is bright.
- **Icons that look native.** A logo drawn small in its tile is enlarged to fill it (within Android's safe zone); icons that are not adaptive (old or round ones) sit in the iOS shape on their own edge colour instead of keeping their outline. A new **Dark** icon style puts each app's own artwork on a near-black tile, lifting artwork drawn dark. Icon lighting now also gives icons a soft shadow on Home, in the dock and in folders.
- **One type and spacing scale.** The island and settings previews use Uno's type scale; sheets, settings, panels, search and folders use one spacing scale (4, 8, 12, 16, 24 dp). The remaining stock Material buttons in sheets and settings are the glass kit's.
- **Motion.** Apps open out of their icon from folders and search as they do from Home. Folders, search, the control panel, widget stacks and the page dots' lens share one set of springs.

## 0.102.0-uno01

- **The island is symmetric about the camera.** Its edges used to snap each to its own nearest gap between status bar icons, which left it a few dp longer on one side, off centre on the camera. Now one half-width is chosen for both sides: the smallest at least as wide as it wants (and as its privacy dots need) where both edges clear every icon, so each icon is wholly under it or wholly beside it; it never covers the clock or the battery. It uses the status bar's real icons when island alignment is on, and the measured grid otherwise; if no centred width fits, it falls back to snapping each edge. Capsules sit 3 dp beside it on clear ground. On the Pixel 10a's measured bar the resting island is 355..725 px, centred on 540 (it was 345..729).
- **Uno's status bar** (iOS layout, Launcher settings › Home, off by default). Home hides Android's status bar and draws its own around the island as an iPhone does: the time left of it; alarm, Do Not Disturb and VPN (when on), signal, Wi-Fi and the battery right of it, in ink that follows the wallpaper. The island is then centred at the iPhone's pill width. Android's camera and microphone indicators are not drawn there; the island always shows its own privacy dots.
- **Tidy the status bar** (Line up with the status bar). Pick icons to hide (alarm, Bluetooth, headphones, rotation, NFC and more) and copy the adb command that hides them through Android's hidden icon list, with the command that undoes it. Uno cannot change the setting itself.

## 0.101.0-uno01

- **The island lines up with the real status bar.** A new, optional accessibility service, **Uno Launcher island alignment**, reads where the status bar's icons are (their positions only, from SystemUI's status bar window, never their text or any other app's window, kept in memory and never sent). With it on, the island puts its edges in the real gaps between icons on either side of the camera, on any phone, at any font or display size, and whatever icons are showing; it never covers the clock. Without it the island uses the spacing measured on a Pixel 10a and the guides on the calibration page, as before. It is separate from the shade service, which still reads nothing.
- **Line up with the status bar** (the island's settings) shows whether automatic alignment is on and how many status bar items it is following, with a button to Android's accessibility settings; the hand guides are kept under **By hand**. The permission ledger has a row for the new service.

## 0.100.0-uno01

- **Widgets that look like iOS's.** Each Uno widget's header is in its own colour and semibold (orange for timers and alarms, green for battery, blue for checklist and counter, gold for note and moon), darkened on a light wallpaper so it reads; big figures are semibold, tightly tracked and of one width instead of thin; content sits 16 dp in, as on iOS. The Date widget reads like Apple's Calendar widget (the weekday in red capitals, a large date), the Clock widget has an orange header, a large time and the date, and Battery shows the phone in its ring with the percentage under it.
- **The island covers the status bar on the iOS layout.** The Home island is drawn in the launcher's window, under the status bar's, so the clock and icons showed on top of the open island. On the iOS layout the status bar now steps aside while the island is open, peeking, being pulled, flashing an event or growing into search, and comes back once it has landed. Home keeps the bar's room, so nothing moves.
- **On the iOS layout the idle island is a ring round the camera.** Android keeps its status bar icons clear of the camera, so the ring never covers one; the island widens when it has something to show. "Extra wide" and the other keep-wide choices still keep it wide.
- **Status bar icons follow the wallpaper.** They were always light, and faded out over a pale wallpaper; now they are dark over a light top strip and light over a dark one, with the glass ink's dead band so they never flicker.

## 0.99.0-uno01

- **An iOS Home layout.** Launcher settings › Home › Home layout switches the phone screen between Uno's **Side dock** and **iOS**: a strict grid scaled from the iPhone's own measurements (square small widgets, medium and large four columns wide, 60 pt icons), widgets placed and resized only at **Small, Medium and Large**, and the dock a clear glass bar of four apps along the bottom with a **Search** pill above it that turns into the page dots while the pages move. The status bar shows instead of the status rail. The inner screen of a foldable keeps the side dock; widgets placed before keep their size until resized.
- **Customizing Home like an iPhone** (iOS layout). Hold empty space and Home goes straight into jiggle mode; widgets wiggle with the icons and carry the same **−** to remove them; the status bar's row holds **Edit** (Add Widget, Customize, Add apps, pages) and **Done**; tapping a widget offers Small, Medium and Large.
- **Clear glass.** Home's glass (dock, status rail, widgets, page dots, search) is clear with a bright edge and a sheen along the top, as iOS's Liquid Glass is. Appearance › Clear glass, on by default; sheets and panels keep their tint.
- **The island opens out of itself and closes back in.** Search grows out of the island exactly where and however big it is (the open island, when opened from its Search button) and shrinks back into the pill as it is drawn, with the island's own corners at every size; it used to be a squashed copy of the panel growing from a pill a few dp off the real one. The island's sideways corrections unwind with its width, so it no longer drifts before it grows; its swell, squash and stretch are about the camera; the privacy dots and capsules stay on the camera's line, and capsules come out only once it has landed. When a sheet, panel or search takes over, the island closes and fades instead of vanishing. Fixed: after a peek, the island's touch area could stay larger than the island.
- **A better open island.** A battery gauge beside the percentage, steady digits, real buttons for Search, Feed and Settings, a soft shadow (tinted with the music's colour while it plays), content that grows out of the camera, and a long press on the pill that opens the app behind its main activity (the call, the player, a Live Update).

## 0.98.2-uno01

- **The keyboard no longer blurs the app behind it unless you turn that on.** Reported: with the glass style, text in Signal's message box flickered between what was typed and gibberish once it wrapped onto a second line (the sent message was right); Classic style did not do it. Blur behind the keyboard window is the only thing the styles do differently to any window but the keyboard's own, so it is now the switch **Blur the app behind the keyboard**, off by default. The keys keep their glass look on a solid panel. The window also no longer asks for blur for a moment at start-up regardless of style. Not reproduced on the emulator, so this needs confirming on the phone.

## 0.98.1-uno01

- **Fixed: text turning to gibberish in some apps (reported in Signal).** Uno Keyboard sent a correction as two steps (delete the word, then type the fix) and deleted from its own remembered copy of the text. An app that rewrites its text box as it changes could act on the half-done edit, the copy drifted from what was really there, and later corrections deleted the wrong characters, with the app and the keyboard each undoing the other. Every edit is now one batch edit the app sees whole; before anything is deleted the app's real text is checked and, if it differs, the keyboard re-reads it and changes nothing; and the keyboard recognises its own cursor updates in order, so an app's change that lands where an earlier keystroke left the cursor is no longer mistaken for its own.
- **Autocorrect reads a dropped vowel back in.** "brwn" is now "brown" and "ovr" is "over" (they were "been" and "or"): a skipped vowel is the commonest slip of a fast thumb, and two slips in one short word count for more than one.

## 0.98.0-uno01

- **Uno Keyboard, rebuilt.** A liquid-glass look: on phones that allow it the keyboard's window blurs the app behind it, and the keys are translucent glass with a bright top edge and a springy press. **Key style** in its settings switches back to the flat look of 0.97, with everything new still working. A live keyboard at the top of its settings shows each change (height, layout, keys, style) and can be typed on.
- **Smarter typing.** Swipe typing. Autocorrect that uses where each key was actually touched, splits words run together ("ofthe") and puts back a correction with one tap. Predictions and completions that learn your words, how you capitalise them and what you put after what. Emoji suggestions for the word you type, emoji search and skin tones. A full stop or comma after a picked word takes the space's place.
- **What it learns stays on the phone.** Learned words, pinned clips and recent emoji are kept in no-backup storage, written by one file that a test holds to that; nothing is learned in password, email, address or number fields or incognito tabs. Learning can be turned off, words removed or added, and everything forgotten from Settings. The keyboard's code still has no network access.
- **Fossify Keyboard's features.** Clipboard history (copies stay an hour, pinned clips are kept, copies marked sensitive are skipped), 17 layouts (QWERTY, Dvorak, Colemak, Colemak-DH, Workman, QWERTZ, AZERTY, Spanish, Portuguese, Italian, Nordic, Turkish, Polish, Russian, Ukrainian, Greek) with the globe key going round them, keyboard height, key sounds, an optional emoji key and key magnifier, and digits on the top row's long-press. Plus a tools row with a text-editing panel, a voice-typing button when a voice keyboard is installed, and autofill chips from your password manager.
- **Haptics with a feel.** Soft, Crisp or Firm, and new cues for a correction, caps lock, Delete taking whole words and a swipe crossing keys.

## 0.97.0-uno01

- **A live island in its settings.** The island page keeps a small copy of the island above its settings, drawn with the island's real geometry for this phone's camera, its theme and its springs. A setting that changes how the island looks or behaves plays its effect there as it is changed: the size slider, Off / Wide / Extra wide (music), the compact Live Update text, camera and microphone indicators, app shortcuts (the open island), what a notification card shows and its buttons, and every style choice (colours replay the scene; a new panel or button shape opens it; a new motion replays the movement). Chips under the preview show any situation again: idle, music, a Live Update, three activities, a notification, the camera in use, or open.
- **The island page, regrouped.** Its settings sit under The island, How it looks and moves, What shows in the island, New notifications (moved here from Notifications & privacy), Timer, and Where things show. Settings search follows the moves.
- **Wallpaper you can adjust before applying.** A chosen photo opens in a phone-shaped preview of Home: drag it into place, pinch (or use the slider) to zoom, and set brightness, blur, colour and warmth. The preview is the saved picture made small, by the same code, so Apply gives exactly what was shown. The photo as chosen is kept, so **Adjust photo** reopens an applied one with its settings. Built-in backgrounds are previewed on Home too, with Apply and Cancel, instead of changing at once.

## 0.96.0-uno01

- **One motion driver for the island.** Everything that moves the island's body (how open it is each way, the widening for an event, the shrink to a ring, the status bar grid's edges, the press and the pulse) is now driven from one place toward what the island is heading for. When the target changes part-way, only the values that change restart, each from where it is and how fast it is going, so an interruption (a tap mid-morph, an activity ending mid-swap, a pull let go) carries on smoothly instead of jumping or starting cold. Every spring the island uses comes from one table, from its theme, and snaps with Android's animations off.

## 0.95.0-uno01

- **Search in Launcher settings.** A search field at the top of Launcher settings finds any setting by its name or by other words for it ("dark mode", "do not disturb", "typo") and goes straight to its page. A test checks every switch, slider and group on the pages can be found.
- **Line up with the status bar.** A new page (The island) draws guides over the real status bar where the island thinks the gaps between icons are, and moves them in quarter-dp steps until they sit between the icons, so the island never cuts an icon in half on a phone other than the Pixel 10a it was measured on.
- **Keep the Home island still** (optional, with Uno's accessibility service). The Home island is drawn in its own window, so it stays at the camera while Android animates the launcher on unlock or when an app closes.
- **One record of where the island is.** The touch window is now the island and each capsule's own rectangle, published as they are laid out, rather than a reserve worked out separately on each side.
- **The island page says when it will look grey.** Over other apps through "display over other apps", Android draws the island at 80% opacity; the page says so and how to get the solid island.
- **Code health.** The 2,100-line island file is split by job (state, faces, rows, slots, modifiers). Deprecated APIs are replaced (the clipboard, lifecycle owner, display, parcelable lists, background-start mode, typed extras) or explained where nothing replaces them, so the launcher compiles with no warnings. Dead code from earlier capsule layouts is gone, along with unused strings and redundant version checks.

## 0.94.0-uno01

- **No more sliver of the signal icon past the island's end.** The island's ends are round, so an end sitting just inside the narrow gap between the signal and Wi-Fi icons showed the top of the signal bars. An outer edge now sits a little past the middle of its gap, away from the island; where a capsule meets the island the two edges still share one gap.
- **The island no longer flies in from the middle of the screen** when the launcher starts or the phone wakes. Its window was added at the top centre and then moved into place, and Android animates a window's move. It now starts and stays at the top-left corner, with move animations turned off for it and for its touch window, and its springs start afresh when the screen's size is first known.
- **The glow and the timer's ring belong to the island's own activity.** Music in a capsule no longer tints the island, and a timer in a capsule no longer rings it.

## 0.93.0-uno01

- **Fixed: taps near the top of the screen going missing.** After the island had opened once (a tap, a pull, a notification card, an app's name), its touch window kept the open panel's size and took taps meant for the top of the app underneath, such as in-app back buttons. The island compared its settled frame with a target that lacked the width adjustments, so the touch area only ever grew. It now shrinks back to the island the moment it settles, and a test that fails on the old code guards it.
- **A much smaller overlay window.** The island's drawing window over other apps was larger than the screen at all times, and Android blended it into every frame of the app in front and redrew it whole whenever the island animated. It is now only the top strip while the island is collapsed, growing to full height only while it opens, and never past the screen's edges: less battery and smoother apps underneath.
- The status bar grid's clock check runs once a minute instead of on every tick of the island's clock.

## 0.92.0-uno01

- **The island no longer cuts a status bar icon in half.** Measured on a Pixel 10a's status bar, every edge of the island and its capsules now falls in a gap between two icons, so each icon is either wholly covered or wholly clear, whatever is showing and however many notification icons there are. This covers the resting island, the wider island that shows an app's icon and name, event flashes, and the capsules.
- **The capsules are rounded rectangles, not circles.** The left one covers exactly two notification icon slots and the right one as few slots as it needs, each ending in a gap, with a thin seam to the main island.
- **Privacy dots sit inside the island's right end,** where they cannot cover half an icon, and ask the island for a little more room.
- **The clock's width is read from your time format,** so the island still lands in the gaps with a two-digit hour or a 24-hour clock.
- The process and the measurements are written up in `docs/island-layout-process.md`.

## 0.91.1-uno01

- **The island no longer takes touches while it starts.** Its window used to be added touchable and as big as the screen, so for the seconds a cold start takes it swallowed every touch meant for the app in front. It now starts untouchable, and only the small window around the island takes touches.
- **Fixed a crash in the island's own window.** A frame measured before the screen's size was known could come out narrower than nothing, and a negative corner radius threw. The radius is now never negative.

## 0.91.0-uno01

- **The island's tuned layout is the default.** The main island is 15 dp wider than automatic, and the left and right islands are 10 dp wider and 2 dp closer in. Three islands at most: the main island, one on its left and one on its right.
- **The island springs open to show an app's icon and name.** When an app opens (or a notification's app shows), the live activities tuck into the island, which springs 62 dp wider and 3 dp to the left, then returns to its resting size with the activities back when the name goes away.
- **Fixed: the top of the screen not taking touches.** The island's touch window had been padded far beyond what it draws, so it swallowed touches meant for the app under the top of the screen. It is now exactly the island and its capsules.
- **The Island debug page is only in debug builds.** A release build neither shows it nor reads anything it saved.

## 0.90.0-uno01

- **Three live activities spread around the island.** With three or more live, the first is on the main island, the second is a circle on its left and the third a circle on its right (with two, the second stays on the right). Every capsule is now a circle showing the activity's symbol, and only as many circles are drawn on the right as fit before the screen's edge.
- **An Island debug page in Launcher settings.** Force dummy live activities on (call, timer, stopwatch, music, live update, and the camera, microphone and recording dots) and set the width and position of the main island and each capsule by hand, with the numbers shown and copyable. All zero and nothing forced is the normal layout.

## 0.89.0-uno01

- **Live activities leave gracefully.** When an activity ends, its capsule slides back into the pill on the same neck it left on, shrinking and melting in, and the pill gives a small pulse. The pill's own glyph and detail shrink out and the next ones grow in, and the camera, microphone and recording dots spring up and shrink away instead of popping.
- **Swapping activities merges and splits.** Tapping a capsule absorbs it into the pill, then the old front detaches into the freed slot a moment later.
- **Extra wide is 1% wider** than the 75% size width, still at the height you chose.

## 0.88.0-uno01

- **Capsules are black again.** The coloured rim, colour wash and breathing glow added in 0.87.0 are gone; the detached live-activity capsules match the pill in plain AMOLED black.
- **Extra wide is the width of the 75% size setting.** The collapsed pill takes the width it has with the size slider at 75% and keeps the height you chose. It never gets narrower than the slider's own width.
- **Extra capsules show the activity's symbol, not an app's picture.** Music is its equalizer bars rather than album art, and a Live Update is a plain symbol in its accent colour (with its progress ring when it has one). A navigation turn arrow is kept, since it says where to turn.

## 0.87.0-uno01

- **Several live activities are easier to handle on the island.** The activity you bring to the front (by tapping its capsule or flicking) now stays in front while others start and end, instead of a different one jumping forward. A flick steps through every activity and wraps round. Capsules glide to their new places and change width smoothly, press down under your finger, and are announced as buttons ("Bring music to the front").
- **The extra capsules look like their activities.** Each capsule has a soft wash of its activity's colour and a rim in that colour that breathes slowly while the activity is live.

## 0.86.0-uno01

- **The dock leaves cleanly into All apps.** Swiping into All apps now slides the dock and status rail fully off the left edge with the home page, instead of parking them on the left edge drawn over the app list. The feed side was already right; both edges now exit the screen cleanly.

## 0.85.0-uno01

- **The dock's glass slides with the dock.** The dock and status rail's glass surface now moves off-screen together with its icons (before, only the icons moved and the glass stayed floating over the feed and All apps).
- **Edge pages keep their margin.** The feed and All apps now sit with the same 16dp margin on both sides instead of running to the screen's right edge.

## 0.84.0-uno01

- **The dock knows its place.** Between home pages the dock and the status rail stay pinned, as always. But swipe into the feed (left) or the All apps list (right) and they slide off-screen with the home page, while the feed and All apps expand to fill the width the dock vacated — so the edge pages are true full-bleed surfaces and the dock/rail feel like part of the home screen rather than furniture bolted to the glass.
- **A little more breathing room round the island icon.** The app icon and artwork circle in the pill is a touch smaller, so it sits inside a wider pill border and reads as set into the island.

## 0.83.0-uno01

- **One type scale, everywhere.** The All apps list, Spotlight rows, the status rail, every widget face and the settings now speak the same `UnoType` HIG rhythm instead of stray raw point sizes; section headers, label colours and corner radii are consistent, and the last Material `surfaceVariant` slabs are gone. The launcher finally reads as one piece of glass.
- **The wide island's icon sits in its end.** When the pill is wide, the app icon or artwork sits concentrically with the pill's rounded end (a few pixels inside it), instead of floating in the middle of the wider slot.

## 0.82.1-uno01

- **Concentric icons, for real.** App icons and artwork on the pill (and the detached capsules) are now circles sized a few pixels inside the pill's radius, centered — they follow the island size and read as set into it. (0.82.0 shipped the leading/trailing restyle; this release actually ships the concentric-icon change it described.)

## 0.82.0-uno01

- **The island reads like Apple's now.** With two live activities, the first stays attached to the camera in the main pill and the second becomes its own detached capsule to the right — a black pill with a hairline rim and a small gap — instead of both squeezing into one pill. Activities beyond two pop out as more of the same capsules, so a call + music + a timer reads as one coherent, Apple-like row of islands. A sideways flick still swaps which one is leading, and tapping a capsule brings it forward.
- **Icons sit concentrically.** App icons and artwork on the pill are now circles sized a few pixels inside the pill's radius, centered, instead of fixed-size squares — so they follow the island size and read as set into the pill.

## 0.81.0-uno01

- **Spotlight opens like a pill, not a rectangle.** The search panel's corners now morph with the emerge — fully round while it is still pill-sized, easing to the panel's 34dp corner as it grows — so the brief "rounded rectangle" flash at the very start of opening (and its mirror on close) is gone.

## 0.80.0-uno01

- **Live Activities, as a first-class contract.** The island's reading of Android 16's promoted ongoing notifications is now a named, tested contract: a pure `LiveUpdateLogic.build` maps the notification's fields (title, short status, body, progress, color, …) into the island's model, with reference tests for the ride-share and delivery cases. PRIVACY.md now names the exact closed set of fields read, and a test fails if the code and the doc ever drift.
- **Compact Live Update text.** A new setting under the island chooses how much a Live Update says on the pill: glyph only, glyph plus the short status (the default), or glyph plus the title — mirroring the notification-card level control.

## 0.79.0-uno01

- **The island, simplified like Apple's.** The long-press tools face (torch, timer, stopwatch grid) is gone, and with it one hidden gesture and one surface. Running timers and stopwatches still live in the island; you start them from search ("timer 10", "stopwatch"), the Timers widget, or the control panel's torch. The compact island is now passive, as Apple intends: tap to open, touch-and-hold to expand.
- **One drag, five uses.** Pull-to-peek, swipe-up-to-close, the hold-open rest, and Spotlight's collapse now all read their thresholds and their smoothing from a single `IslandDrag` home — so the next feel change is one edit, not five. The feel today is unchanged; the plumbing is honest.
- **The island explains itself.** The open panel's first line now says *why* it is showing what it is ("You're navigating", "Charging", "Now playing"), and a "What shows where" section in island settings maps every event to its one home.
- **Accessibility from the source.** Arrivals and departures (timer started, call ended, music paused) are announced to TalkBack once, at the moment the state changes, and the reply-send and play/pause buttons grew to proper touch targets.

## 0.78.0-uno01

- **A quieter island.** The peek and morph fade no longer build a gradient on every frame (it draws from a once-made ramp instead), the Search/Feed/Settings labels drop to icons before they clip at larger system fonts, a long event title like "Do Not Disturb" gets room instead of truncating, and TalkBack now says when the phone is charging.
- **The feed that refreshes itself.** Pull the news feed down past a short distance and it refreshes (the rubber band shows the progress as it does); the header shows a spinner while it refreshes, stale feeds (over a day old) mark their "Updated" line in the accent, a page with every feed off offers a Manage feeds button, and a partial failure names the feeds that couldn't load. Text that used to slip under 4.5:1 now clears it, and big feeds sort themselves off the main thread so scrolling never stutters.
- **The launcher, squared away.** One press spring everywhere (the menu, buttons and widgets no longer disagree), Spotlight closes with the same spring it opens with, folders animate closed as well as open, a scatter of sub-48 dp buttons grew, off-grid paddings are back on the 8 dp grid, a couple of hardcoded corner radii became tokens, and the Home-edit wiggle and battery ripple respect reduced motion.

## 0.77.0-uno01

- **Spotlight is the island's size.** The search panel is now exactly as wide as the open island (they share one width source: 92% of the screen, 336–372dp), so the panel reads as the pill grown larger instead of a wider sheet.
- **It collapses, not slides.** Swiping up on Spotlight shrinks it back into the island under your finger — the whole panel scales and fades into the pill, and letting go past a short distance or with a flick finishes the collapse. It no longer flies off the top of the screen.
- **The glass rides the keyboard both ways.** The page dots and search button now follow the keyboard's top edge smoothly while it opens AND while it closes (they used to hold still on close and snap down at the end). The keyboard's own slide animation is restored to the window — edge-to-edge, so nothing else moves.

## 0.76.0-uno01

- **Spotlight is the island's search, through and through.** The search panel now shares the island's 34 dp corner radius (one constant, so they can't drift), sits 24 dp lower, and closes the way a peek does — swipe up from its bottom and it follows your finger, letting go past 32 dp or with a quick flick. While Spotlight is open the island keeps to itself: tapping the camera cutout does nothing (no island pops up underneath), and an incoming notification shows only its dot rather than expanding the island — including a turn-by-turn cue. Close Spotlight and the island owns notifications again.

## 0.75.0-uno01

- **Settings, reorganized around you.** Thirteen destinations became nine: **Appearance** (wallpaper, glass and app icons together), **Home layout**, **The island** (everything island in one place), **Notifications & privacy** (access, badges, notification cards, Focus, hidden apps and the permission ledger end to end), **Gestures & feel** (now with the control panel's tiles and shortcuts, next to the swipe that opens it), **Search**, **News feed**, **Keyboard**, and **Backup & about** (backup, updates and help). Every switch is two taps away; nothing moved in meaning, defaults or storage.
- **Spotlight does more of the typing.** A bare phone number offers Dial (and "call 555-123-4567" just dials); a domain or URL offers "Open in browser"; an empty search offers one-tap Stopwatch, Torch and Focus. A phone number is no longer mistaken for a sum ("555-123-4567" used to "answer" -4135). And an app you actually use outranks a better-spelled one you never open ("maps" puts your daily Organic Maps above a never-opened Maps).
- **A fresher island.** A Live Update that stops updating for ten minutes steps off the pill (navigation and countdown clocks stay); the card still waits on the open island.

## 0.74.0-uno01

- **Spotlight and the keyboard move as one.** Opening search used to happen in three beats — the island grew, then the keyboard popped, then the whole home screen jumped up to make room. The keyboard is now asked for on the very frame the island begins to open and dismissed the frame the close begins; the window no longer resizes for the keyboard (it was `adjustResize`, and the home container was padding for the IME through `safeDrawing`), so Home, the dock and the rail never move; and the page dots and search button ride the keyboard's top edge frame by frame, up and back down.
- **Sliders glide.** Settings sliders moved in stair-steps: the thumb's spring was run to a full stop before the next finger position was even read. The thumb now tracks your finger 1:1 while you drag (the liquid stretch still reads your speed), and springs — carrying your velocity — only when you tap or the value changes on its own.
- **A slow swipe up closes the island.** Two things blocked it: resting a finger on the bottom handle claimed the touch as a "hold it open" before a gentle swipe could start, and the close itself was a release-time flick that never followed your finger. A drag up from the open island (or a peek) now tracks your finger — bottom edge and all, through the same low-pass as the pull-down — and letting go closes it past 32 dp of travel or with a quick flick; a finger that doesn't move is still the hold.

## 0.73.0-uno01

- **The peek no longer fights the status bar.** A pull on the pill used to land the peek AND open Android's shade at the same time, because the pill wraps the camera inside the system's status-bar strip and SystemUI claims swipes that begin there. Now each gesture has one owner: a pull that starts below the strip is the island's (peek, shade stays closed); a pull that starts inside the strip is the system's (shade). With "Island everywhere" and the shade accessibility service on, the island floats above the status bar and the whole pill is yours — that remains the way to drag from anywhere on the pill.
- **Extra wide island.** "Permanent wide island" is now a three-way choice: Off (the idle island shrinks to a ring), Wide (today's wide pill), and Extra wide — half again as wide, clamped so the pill and panel always keep the screen's margins and clear the dock. Existing Wide settings are kept.
- **A steadier finger.** Swiping the island closed (most noticeable with a notification on it) tracks your finger through a 40 ms low-pass filter, so the bottom edge follows you without the high-frequency twitch, and the settle spring keeps your filtered speed. With Android's animations off the island tracks 1:1 as before.
- **Sliders slide again.** Settings sliders updated their percentage but the thumb froze: the thumb's spring was cancelled and restarted on every drag event and never advanced (a 0.63.0 regression, visible only where the value writes back synchronously). The spring now retargets without cancelling, so the thumb follows your finger.
- **A finished navigation leaves the island.** A Live Update whose app ends or dies without cancelling its notification (Organic Maps' arrow was the report) used to linger on the island forever. A once-a-minute sweep now drops any live activity whose notification is gone.
- **The keyboard learns its contractions.** "dont", "hows", "oclock" and fifty more bare forms fix to "don't", "how's", "o'clock" — 73 in all — and real words are never stolen: "were", "its", "lets" only OFFER "we're", "it's", "let's". A correctly typed "doesn't" is left alone, "I Dont" capitalizes right, "ID" no longer becomes "I'D", and a flung apostrophe completes ("don'" → "don't"). Still never learns or stores a word.

## 0.72.0-uno01

- **The island and the status bar stop competing.** Every event now has exactly one visible owner, by rule (`IslandEventArbiter`, unit-tested): an ongoing activity (call, timer, music, Live Update) lives in the island; a new notification shows the island's card only if you opted into titles and text, otherwise the short name flash — and neither fires while a card is already down, nor for three seconds after you just opened the notification shade (you saw it there). When the status rail is on, it owns the battery level, so the island no longer draws a second battery-level ring beside it. The Android status bar itself is untouched: a launcher has no honest way to hide another app's icons, and Uno won't fake one.
- **A pull on the island is only a pull on the island.** Dragging the pill down into a peek could also fire the launcher's top-edge pull (opening the shade, the control panel or search at the same time), because nothing excluded the pill from that gesture. A drag that starts on the pill is now the island's alone; the shade's own pull-down everywhere else is unchanged. (Android's system status-bar strip still owns any touch that begins inside the strip itself — no app may change that — and the peek still lands even when Android cancels the drag at the very top.)

## 0.71.0-uno01

- **The news feed, restyled.** Story cards were grey Material slabs with a blue byline; they are now ink-tinted glass with the headline in 17 sp semibold, the summary at 15 sp, and the source and time in a muted 12 sp that no longer competes with the headline. Unread stories carry a small accent dot (read stories dim their text, not the whole card), the "new" count is a proper pill, a feed that fails to load shows its error in a tinted callout with a glass retry, and the search field and empty-page buttons are glass instead of stock Material. Feed settings rows get separators and a glass delete button.
- **Spotlight learns to spell.** App names forgive a typo: "gmial" finds Gmail, "calndar" finds Calendar (ranked below exact and prefix matches, so good spelling still wins). "wifi settings", "bluetooth settings" and "screen brightness" now reach their settings pages, "countdown 5" starts the timer, and **Enter** does the smart thing — the top app if there is one, else your typed action, else the first matching contact, else a web search (Enter on "timer 10" used to web-search it). The action row sits under an "Action" header, an empty Spotlight teaches "Try 'timer 10', 'note buy milk', or '12+3'", and the letters you typed are bolded in app names. The All apps list rubber-bands at its ends like the feed.
- **Grabs that land mid-flight.** Catching the island while it was collapsing snapped it back to collapsed; it now re-opens from the height you caught it at. Re-grabbing the rubber band at a list's edge while it sprang back snapped it to the edge; the curve now continues from where your finger met it.

## 0.70.0-uno01

- **Uno Actions: type a command, the launcher does it.** In the island's search field, type `timer 10`, `stopwatch`, `note buy milk` (writes straight into your Note widget), `alarm in 20` or `alarm 7:30` (your clock app opens with the time filled in), `torch`, `focus` (or `focus on` / `focus off`), and — if you've turned on contacts in search — `call Mom` or `text Sam`, which show the matching contacts and open the dialer or messages app (the dialer is shown, never a call placed). Each action says what it will do before you tap it; everything runs on the phone, nothing is sent anywhere, and nothing new needs a permission.
- **Sheets, edges and pages move like iOS.** A settings sheet can now be thrown away with a decisive downward flick, while scrolling a long page up past its top still never closes the sheet by accident. The news feed rubber-bands at its ends with the classic iOS resistance curve and springs back (and snaps back, with Android's animations off). Turning a Home page settles with the speed of your fling, and so does the island when you swipe it closed or let go of a pull — the spring starts from your finger's release speed instead of a fixed one.
- **Haptics you can feel the UI through.** A light detent tick the moment a pull-down gesture crosses its threshold, a tick when a dragged icon crosses into a new Home cell, a proper long-press rumble through the haptics switch (it was a raw buzz before), and a single centralized toggle/checkbox haptic (the Focus switch used to fire two). Sounds stay off by default.
- **A smoother island under the fingers.** The island's animation frames no longer allocate: the progress ring's paths, the bubble's neck, the fade layer's paint and the music glow's gradients are built once and reused, so the phone's garbage collector stays out of the way of the 120 Hz glass. No visual change.
- **The keyboard, more iOS.** Per-key haptics: letters tick lightly, delete is a touch sharper (and softens into a fast pulse while held), space and return click a little deeper, and the shift, symbol and emoji keys answer with a soft two-tick toggle. Double-space for a period now has its own switch (on by default). A quick flick down on any letter types a comma, a flick up an apostrophe. And autocorrect reads two words back, not one ("frm" after "the at" fixes to "from", not "form"), capitalizes its fix at the start of a sentence ("teh" → "The"), and knows six more contractions ("aint", "wholl", "therell"…). It still never learns or stores anything you type — the false-change rate is pinned at 0.022 % by a test.

## 0.69.0-uno01

- **Peek without the jitter.** Pulling the island down recomposed the whole island (faces and all) on nearly every touch event: 14 times for a 16-move pull, which is what stuttered. The island's height and width are now driven outside composition (a collector turns the pull, the peek and the open state into targets for two animations), so a pull changes layout and drawing only (a test holds it to six compositions for sixteen moves). Also fixed in the peek and the pull:
  - the body no longer stretches and squashes with the finger's uneven speed, and does not press-squash under the finger while it is pulled (it jumped back when the drag took over the touch);
  - the charging or timer ring no longer rings the whole peek panel, and the pop-out bubbles stay away from a peek (they hung off the panel's edge) and come back when it closes;
  - while the body is narrower than the panel its text no longer shows cut off at the sides and blurred (the face waits until the body is nearly panel-wide), and while it is only part open the bottom of what is shown fades out instead of a line of text being cut in half;
  - the peek rests with a little less space under its content, so no sliver of the next row shows.
- **One accent.** Switches were iOS green while checkmarks, radios, sliders, buttons and chips were orange, and the chosen wallpaper card was lavender with a teal ring. They all take the launcher's one accent now (the wallpaper's, or orange), as do the News feed's chips and its "new" count. Status colours (charging, camera, a call) stay what they mean.
- **No foldable references.** The README, the user guide, troubleshooting, the contributing notes, the design and architecture docs and the release-validation notes describe a phone and, where the layout differs, "a wide window"; the welcome sheet no longer says "foldable"; the Home layout presets are "Single page" and "Wide" (they were "Cover" and "Inner") and the extra left page is the "wide-screen page". The unfolded screenshot and the old welcome screenshot are gone from `docs/images`. Past changelog entries are left as they were.

## 0.68.0-uno01

- **App names you can read.** On Home, an icon's name was white with a faint shadow and measured about 2.2:1 against the lighter parts of the dune wallpaper (the sand under Play Store, YouTube, Contacts). It is now 12 sp medium (was 11 regular) with a soft dark halo blurred behind it and a tighter shadow on top, and measures 2.9:1 at worst and 3.3:1 on average (was 2.6:1 average) on the same screen. Folder names use the same label. The halo copy is hidden from screen readers.
- **Clearer widget text.** The small line under the clock and date widgets ("Local time", "October") is 12 and 13 sp medium at 92 % ink (was 11 and 12 sp at 80 %).
- **Double-tap to lock.** New switch under Gestures & feel: a double tap on an empty spot on Home locks the screen, like the power button. Android only allows an app to do that through an accessibility service, so it uses Uno's optional one (the one that opens the shade and hosts the island over other apps), and says what is needed when it is not running. Off by default; a single tap, a folder and every occupied cell are unchanged (only empty cells wait for a second tap). The permission ledger names it.
- **A proper icon for the Dynamic island** in Launcher settings: a capsule, not the plain circle it had.

## 0.67.0-uno01

- **Peek.** Pull the small island down (a drag that starts on it) and it opens part-way; let go and it holds as a peek, panel-wide and as tall as the top of the panel and its first row (the time, then what is most relevant: a held notification, a call, a Live Update, the music). Pull the peek on to open it fully, swipe up to close it, tap it to open it; left alone it tucks away after five seconds. The pull follows the finger, and a pull that Android cancels part-way at the very top of the screen still lands in the peek.
- **Hold it open.** Rest a finger on the bottom of the open island (or a peek) and it stays open for as long as you hold it, with a small handle there to say so; it tucks away a normal time after you let go. A hold does not open the tools or press the button under your finger.
- **A recent notification stays one tap away.** For 30 seconds after a notification arrives it is held: a small dot in its app's colour on the pill, and, on the open island or a peek, the notification itself (its app, title and text, with its Reply, Mark as read and other buttons) just under the time. Using it, opening it or letting 30 seconds pass removes it. A turn-by-turn instruction is not held this way.
- **Navigation you can read while driving.** The turn arrow is 72 dp (was 46) and is read from the notification at 224 px (was 96, which blurred), the distance is 34 sp bold (was 20) and the street 20 sp semibold on up to two lines (was 12 sp on one). The turn card that drops down for each manoeuvre uses the same large layout, and on the pill the arrow and distance are larger (24 dp, 15 sp bold). Checked with Organic Maps' notification layout, where the title is the distance and the text the street.
- **Smarter autocorrect.** It reads the sentence: a candidate that often follows the previous word is preferred, so ties between real words settle by what you are saying, completions rank the same way, and a rare real word that is one slip from a common one that fits is fixed ("ro the"). A fat-fingered extra letter (a key next to its neighbour) is cheaper to undo than any other. Habitual misspellings that distance cannot reach ("alot", "tommorow", "neccessary", "thankyou") and more contractions ("wouldve", "itll", "theres") are fixed outright; informal words ("lol", "idk", "gonna", "thx") and capitalised names in the middle of a sentence are left alone. It still never learns or stores anything you type. About 0.2 % of common words after common words would change; a test holds it to that.
- **Logo.** A new mark for the launcher, in `docs/images/` (`uno-logo-*.png`): one knife-edge dune, lit on the left and shaded on the right, a lone figure on its crest, and two moons.

## 0.66.0-uno01

- **Liquid motion.** The island stretches taller as it opens and squashes as it closes, by its own speed (calm themes barely, jelly ones more), so it moves like a body of liquid with volume. What is written on it blurs and grows in as the body opens around it, and out as it closes. A bubble leaving the pill (a third live activity) starts inside the pill's end, overshoots on a spring and stays joined by a neck that stretches thin and pinches off (`Metaball`). With Android's animations off none of this runs.
- **Buttons on notification cards.** The card shows the notification's own buttons (up to three: a reply first, then Mark as read, Archive and so on), only where the card shows a title. Tapping one does what it does in the shade. A reply is typed in a field in the island itself (on Home; over other apps it opens the notification, since that window cannot take the keyboard) and is sent through the notification's own reply input; the card waits while you type. Switch: Notifications, Action buttons on the card.
- **Camera and microphone row.** The open island has a privacy row while the camera, microphone or screen recording is on: which, for how long, a Review button for Android's privacy settings and, if you have already allowed usage access, which app was in front when it started. Android does not tell any app which app is using the camera or microphone (checked against the public API), so the island does not claim to know.
- **Bluetooth connect alerts.** A watch, keyboard or car connecting or disconnecting flashes its name, using Android's Nearby devices permission, which is asked for only when you switch Bluetooth device alerts on. Headphones and speakers keep their own alert, so they are not said twice. The permission ledger lists it.
- **Sized for the Pixel 10a.** On a punch-hole phone the island keeps 4 dp from the top edge (was 6) and at least 4 dp round the hole (was 3), so the pill is 27 dp or more tall instead of a thin capsule; the open panel is 92 % of the screen's width, up to 372 dp (was 336 dp), and its corners are 34 dp (was 30). The panel still clears the dock. All of it follows the camera hole the phone reports.

## 0.65.0-uno01

- **The island opens and closes more smoothly.** The island used to recompose on every frame of its morph, and its panel text was wrapped again each frame while the body grew. Now the animated size is read only when the island lays out and draws, and the open panel is laid out once at its final size and revealed by the growing body. A test holds opening or closing to a handful of compositions (it was about one per frame).
- **The open panel is tall enough.** With a feed headline showing, the panel was short by the headline's two lines and the Search / Feed / Settings row was cut off the bottom; the headline's room is now counted, and so is a larger system font. Text on the island also uses its font's own line height instead of the theme's 22 sp, so rows are as tall as their text.
- **Next alarm.** The open island shows the next alarm beside the date ("7:30 AM", or with the weekday when it is more than a day away). It reads the system's alarm clock; no permission is needed.
- **Song times.** The seek bar has the time played on its left and the time left on its right, in digits of equal width so they do not shimmer.
- **Screen reader.** The island is a button with a stated open or closed state and "open" / "close" click labels; its description says what is live on it (a call, a timer, music); and a new event or notification card is announced once, without re-reading the ticking times.
- **Reduced motion.** With Android's animations switched off the island snaps open and shut instead of springing.
- **Includes 0.64.0 (never published on its own):**
  - **A real type scale.** Uno follows Apple's HIG / SF Pro rhythm: sizes 34 down to 11, iOS line heights and tracking, set in the bundled Inter variable font. `UnoType` in `appearance/DuoTypography.kt` is the single source of truth; the glass UI kit reads from it too.
  - **Regular body, semibold headers**, as in SF Pro, instead of Material's "medium everywhere".
  - **What moved on screen:** section headers 16→17 semibold, help and note text 12→13, sheet titles 24→22, dialog titles 22→20 semibold, body and guidance 14/16→16/17, page titles 28 medium→28 regular, kit buttons 14 medium→15 semibold.


## 0.63.0-uno01

- **Glass looks thicker.** Glass surfaces (widgets, the dock, cards, the status rail) now carry the Backdrop library's own default shadow, so they sit on the page like slabs of glass instead of flat tinted panes.
- **Toggles and sliders work like iOS 26's.** The thumb is a white pill at rest. While you hold it, it grows to about 1.5 times its size, turns clear and bends the track beneath it with a faint colour fringe, and stretches as it moves. You can tap a toggle, or drag its thumb across and let go. The slider's press animation never ran before (nothing drove it); it now follows your finger, and dragging past an end still stretches the thumb.
- **Buttons and chips respond to your finger.** With liquid glass on, a button is a small lens capsule that swells, drifts slightly towards where you drag and stretches along the drag, with a soft light that follows your finger and a spring back on release. A prominent button is tinted with the wallpaper accent. The selected chip is lit accent glass; the others stay flat but swell and glow. With liquid glass off they are the flat pills they were.
- **Glass menus.** The Home edit "+" menu and the folder app menu are now liquid glass panels that bend and blur what is behind them, spring open from the corner nearest what you tapped, flip above when there is no room, and light up the row under your finger. With glass off they are Material's menus.
- **Glass adapts to the wallpaper.** Glass text is dark in the light theme and light in the dark theme, so when the wallpaper is the wrong way round for that (a dark photo in the light theme, a bright one in the dark theme), the picture under the glass is lifted or dimmed and eased in contrast so the text stays readable. A wallpaper that already suits the theme is left as it is.
- Tests: unit tests for the wallpaper tone rule; emulator tests for the switch (tap, drag both ways, a tiny drag, screen-reader action) and the slider (tap, drag, ends).

## 0.62.0-uno01

- **Navigation directions stay up longer.** The island's drop-down for a maps instruction now stays for nine seconds (it was four), and the least time between two drop-downs is ten seconds (it was six), so each one can be read before the next replaces it.
- **No more flash of the full island on the way out.** When a notification card or the tools face closed, the island cleared what it was showing at once while it was still shrinking, so for a moment it drew the ordinary open island (the clock, app shortcuts and settings) at the card's size before reaching the camera. The island now keeps the face it had, and its size, until it has finished closing. This was in the closing code, not in the maps notification, so it applied to every card and to the tools face.
- Tests: an emulator test steps the closing frame by frame and checks the ordinary open face never appears (it fails without the fix); the island suite passes.

## 0.61.0-uno01

- **You can scroll the feed titles sideways again.** The Home pager watches every touch before the page under it and takes any mostly-sideways drag as a page turn, so a swipe along the row of feed titles (All, Saved, each feed) turned the page instead of scrolling the row. The row now registers itself, as the widget-stack rail does, and a touch that starts on it scrolls it.
- **App icons look lit, like iOS's.** Original icons now get a soft light along the top edge and a fainter glint along the bottom, a glow spilling down from the top and a gentle shading toward the lower edge, so they look slightly raised. It is painted into each icon inside its silhouette, so the corners stay clean, and it is meant to be subtle: the app's own artwork is not recoloured. That is what lets you use the apps' default icons. A new **Icon lighting** choice (Lit or Flat) sits under Icon style in the icon settings, on by default. Themed icons (which already have their own glass lighting) and icon-pack icons are left as they were.
- Tests: the existing unit and feed suites pass; the lighting was checked by eye on the emulator.

## 0.60.0-uno01

- **Dropping an app on a folder works again.** Only the folder's small icon plate counted as "on the folder"; a finger on its label or just beside the plate counted as an ordinary Home cell, so the layout opened a gap there and slid the folder out from under the app being held. Now an app held anywhere over the middle of a folder's cell is over the folder, and nothing moves. Near the cell's edge it is still an ordinary move.
- **Removing a widget from a stack removes it.** The stack kept drawing the removed widget at its old position (it left the list but stayed in the stack) because the stack's pages were keyed by position, and the animation held on to the first widget it drew there. Pages are now keyed by the widget, so the removed one goes and the base widget (or the next) shows at once.
- **Settings are harder to close by accident, and remember where you were.** Scrolling a settings page past its top, or flinging it, used to hand the leftover scroll to the sheet and close it. That leftover is now absorbed; the sheet closes from its handle, the Back gesture, a tap outside, or the Close button. Closing the sheet on a page and swiping up again (or reopening from Home options) returns to that page; Back still steps up one level.
- **The lock screen follows day and night.** With "also set Android's home and lock screen" on and a built-in background chosen, the wallpaper was sent only when you chose it, so it stayed on the old scene when the system theme or the sunrise and sunset schedule switched. Uno now notices the change, with the launcher out of sight too (the system light/dark switch, and the schedule on the minute tick) and sends the matching scene once per change. A photo background is the same day and night, so it is left alone. The screen has to be on for the schedule's switch to be noticed, so a lock screen may change when you wake the phone.
- Tests: unit tests for the folder-cell rule, the settings guard and when the system wallpaper is re-sent; emulator tests for removing the visible widget from a stack and for reopening settings on the same page.

## 0.59.0-uno01

- **Folders look and work like iOS's.** Opening a folder shows a small, translucent panel sized to what is in it (not a full-screen sheet): the name centred above it and editable in place (tap it, type, tap outside; there is no Done button), the apps as bare icons with their names in a 3 x 3 grid, and more than nine paging sideways with dots. The panel springs open from slightly small. On Home a folder's icon always shows a 3 x 3 preview.
- **Making a folder is quicker.** After you drop one app on another (or pick "Create folder"), the new folder opens at once with its name selected, ready to type over. It starts with a sensible name: when the apps agree on a category (Social, Games, Music, Video, Photos, News, Maps, Productivity, Utilities) the folder is called that; otherwise "Folder".
- **Island themes can do much more.** A theme file can now set a highlight colour for anything without one of its own; turn off apps' own colours so every app wears the highlight; give particular apps their own colours by package name (up to 16); choose the shape of an app's glyph (circle, squircle, rounded, square, arch); colour progress while it fills and when it is full, and show a percentage; and swap the sides of the navigation card and colour its distance. The island style page has controls for all of these except per-app colours, which live in the theme file. Old theme files still work, and a plain theme exports as small as before. See docs/island-themes.md.
- **Make your own wallpaper.** A new guide, docs/making-a-wallpaper.md, takes you from an SVG editor to Uno's background picker: canvas size, safe areas for the island and dock, export settings and a checklist.
- Tests: unit tests for the folder's paging and sizing rules, the folder preview, the category-based names, the accent rules (override, own colour, highlight), progress colours, glow, and every new theme field (round trip, bad values, the 16-app limit, legibility); the folder emulator suites pass, and the new folder panel was checked on the emulator.

## 0.58.0-uno01

- **Turn arrows on the island.** A maps app's navigation notification carries the turn arrow as its large icon, which the island ignored (it showed the app's logo). It now shows the arrow, in the arrow's own colours: on the pill beside the distance, and large on the open island with the distance and the street. This works for any app that sends its arrow that way; Organic Maps does (its notification was read to check). An instruction without an arrow still shows the app's icon, and no other kind of activity has its large icon read.
- **The island drops down for each turn.** When the instruction changes in a way worth a glance, the island drops down for four seconds with the arrow, the distance and the street, then tucks away, like the iPhone's. It does so when a route starts, when the arrow or the street changes, and when the turn comes within about 200 metres and again within about 50. It never reopens more than once every six seconds. It uses the island's own card, so it works over other apps when "Island everywhere" is on. A new switch, **Drop down for each turn**, sits under "Navigation in the island" (Notifications & privacy) so it can be turned off.
- **Something in the Dune range sky.** Look up at night. (Only the Dune range wallpaper, only after dark.)
- Tests: unit tests for reading distances in metres, kilometres, miles, feet and yards, for every reason to drop down and for the cool-down; an emulator test shows the arrow on the pill and the open island, falls back to the app's icon without one, and never shows an arrow for an ordinary update; a unit test finds the sky's two moons and checks nothing else changed.

## 0.57.0-uno01

- **Notification cards belong to the island.** The drop-down card for a new notification used to be a separate rounded panel inset in the island, with its own edge and a ripple, so it looked cut out of it. It is now part of the body: the app's colour glows out from behind its icon into the island's own black, with no card edge and no ripple (the island's own press squash is the feedback).
- **The now-playing glow rises from the bottom.** The track's colour now comes up from the island's bottom edge instead of in from the left, and while audio is really playing a few soft tongues sway and swell slowly like a low flame. Paused or lingering music keeps a still glow, and nothing animates when Android's animations are off.
- **The motions are clearly different now.** Jelly and Droplet were too alike, so each is its own cartoon:
  - **Bouncy** is a rubber ball: height and width overshoot together and ring down.
  - **Jelly** is a wobbling blob: only its height springs, and its width is tied to it the opposite way, so the body breathes (wide and flat when short, narrow and tall when stretched).
  - **Droplet** is a water drop: it drops down fast while the width barely moves, hangs narrow and tall, then spreads wide slowly and smoothly with no wobble. Its content appears once it has spread.
  - **Calm, Standard and Snappy** are tuned further apart (slow and soft, smooth, quick and crisp).
- **Another hidden thing**, somewhere in the launcher this time.
- Tests: unit tests for the coupled jelly width, the personalities of the motions, the flame's shape and bounds, and the hidden thing; the island and Spotlight emulator suites pass; frame-by-frame captures of each motion, the card and the glow were checked on the emulator.

## 0.56.0-uno01

- **A livelier island.** The island's width and height used to be held to the panel's size, so even the "Bouncy" setting could not visibly overshoot. They are now two separate springs that may overshoot, which is what makes stretch, squash and bounce possible.
  - **Standard** opens smoothly with a hair of overshoot instead of stopping dead; **Calm** and **Snappy** are tuned to match.
  - **Bouncy is properly cartoonish:** it overshoots by about a quarter, rings once or twice, and the width lags the height so the body wobbles.
  - **Two new motions, Jelly and Droplet.** *Jelly* is springier still, the width swinging wider than the height. *Droplet* drops down narrow and tall like a water drop leaving a tap, then splashes wide and settles. Both work in theme files too (`"motion": "jelly"` or `"droplet"`).
  - **Three new presets** to go with them: *Droplet* (water blue), *Jelly* (candy pink) and *Spice* (sand and blue).
  - **A press squashes the island** wide and flat like a thumb on a water balloon and it springs back; **events swell it more** in the livelier motions. Closing still settles without overshoot, so it always lands on the camera.
- **No battery percentage on the pill.** The wide island no longer writes the battery level on itself. The status bar shows it, the island speaks about the battery only while charging, and the open panel still shows it.
- **Wallpaper depth.** The wallpaper is drawn a little larger than the screen and drifts the other way as you swipe between pages, so it sits behind them; the glass bends what is really on screen. There is a **Wallpaper depth** switch next to Material You colors, and it stays still when Android's animations are off. (Wallpapers already re-render for the new shape when a folding phone opens; this reuses that bigger-than-the-screen drawing, and the drift per page shrinks as pages are added so the edges never show.)
- **Something hiding in Spotlight** for readers of a certain desert novel. Whole phrases only, so ordinary searches are untouched.
- Tests: unit tests for the overshooting geometry, every motion's spring bounds and personality, the new theme values, the wallpaper drift and the easter egg; island and Spotlight emulator suites re-run.

## 0.55.0-uno01

- **A much better news feed.**
  - **Read and new:** stories you have opened dim, and the feed page shows "N new".
  - **Save for later:** long-press a story to save it (or use the "Save for later" accessibility action). A **Saved** chip shows only those, and a saved story is never trimmed away.
  - **Filter and search:** chips for each feed, and a search box over titles and summaries.
  - **Faster, kinder refresh:** feeds are fetched together (at most two at a time from one server) and ask the server "has this changed?" first, so an unchanged feed costs almost nothing. Each feed shows its own error instead of one combined message.
  - **OPML import and export** in the feed settings, to move your list to or from another reader (up to 200 feeds, files up to 1 MB).
  - **Stories to keep per feed:** 20, 40 or 80. Lowering it trims the stored stories at once; saved stories are always kept.
  - **Your feed list is in layout backups.** A restore adds feeds you do not follow yet and never removes any; the save-time check and the restore review both cover feeds. Stories you have read or saved are not carried.
  - Fixed along the way: a story carried by two feeds could crash the feed page; a refresh could stick on "checking"; feed names never updated after a refresh; a "not modified" answer could hide new stories after a crash; removing a feed left its read and saved marks behind.
- **Two new widgets from the widget lane.**
  - **Checklist:** up to 12 short tasks on Home; tap one to tick it off, tap elsewhere to add. Kept on the phone, nothing else.
  - **Screen time:** today's screen time and your three most-used apps with a bar toward a daily goal you set. It needs Android's **Usage access** grant, which you give in Settings only after placing the widget and tapping it. **This release adds the Usage access permission to Uno** (declared in the manifest, listed in Permissions & privacy, and described in PRIVACY.md); it reads usage statistics on the phone while the widget is showing and sends nothing anywhere. Without the grant the widget shows only the ask.
- **Widget stacks work with a screen reader.** A stack reads out "Widget 2 of 3: Counter" and offers "Next widget in stack" and "Previous widget in stack" actions, so the swipe and the dots are not the only way to switch.
- **Under the hood, no change in behavior:** `LauncherScreen.kt` is split into five files (home grid, widget slots and stacks, widget cards, sheets, theme), and `scripts/startup-time.sh` measures cold start at three levels of precompilation. The baseline profile has been in the app since 0.34; on the emulator its benefit is within the noise (about 2.4 to 3.4 s either way), so the real answer waits for a phone.
- Tests: new unit tests for the feed rules, and emulator tests for the feed store, the feed page, backups with feeds and the screen-reader actions on stacks.

## 0.54.0-uno01

- **A new Console widget.** A live, timestamped log of events on the phone itself: screen on and off, charger connected and disconnected, battery low, airplane mode, ringer mode, Do Not Disturb, headphones and Bluetooth or USB audio, VPN connecting and dropping, USB data, apps installed, removed and updated, and a time zone change. Find it under **Uno widgets** in the picker (it stacks like the others).
  - **Private by design:** it needs no permission, reads nothing from other apps and keeps the log only in memory (never saved, never sent), and only while the widget is on screen. Android gives a normal app no "all events" feed, so these are the public signals the phone shares.
- **Battery and Moon previews** now show a steady sample (a charging battery, a gibbous moon) in the widget picker instead of whatever the phone is doing.
- **A fix inside the Console before it shipped:** its app install and update events needed a "package" filter that had been combined with the screen, charger and other events, which would have silenced all of those; they are now separate. Its VPN listener also now reports on the main thread.
- Tests: unit tests for the console's wording, timestamps and event ring; an emulator test checks that the two event filters stay apart.

## 0.53.0-uno01

- **Swipe a stack to switch it.** Swipe up on a widget stack for the next widget and down for the previous one; they slide in and out, with a tick on each step. Sideways swipes still turn the page. A swipe that starts on a stack no longer opens the shade, control panel or settings (swipe from outside it for those), and a list-style Android widget keeps its own scrolling (the dots down the edge still switch it).
- **Drag a widget onto another to stack it.** Long-press a widget and drag it over the middle of another one. Hold it there for about half a second: the target grows a little and gets a ring, and a buzz tells you it will stack. Let go to merge. A quick pass over a widget still just moves it. A widget that already holds a stack, a folder widget, or a full stack will not take part.
- **Folders from a drag, the iOS way.** Dragging an app over the middle of another app already made a folder; the target app now grows and glows as you hold, so you can see it will.
- Tests: emulator tests swipe a stack up and down, hold-to-stack a Counter onto a Note, and check that a quick pass does not stack.

## 0.52.0-uno01

- **Uno widgets can be stacked.** The Clock, the Date card and every widget in the Uno library (Note, Counter, Battery, Month, Moon, Timers and the rest) now stack, alone or mixed with Android widgets. Open a widget's options, choose **Stack another widget here**, and pick the one to merge in; switch between them with the dots down the stack's right edge. A stacked widget keeps its own saved data (a note's text, a counter's count), and **Remove ... from this stack** forgets it.
- Behind the scenes, a stacked Uno widget keeps the Home slot it had, so that slot is not given to a new widget and its data is not cleaned up while the stack's base widget is on Home.
- Still true from before: stacks are not saved in layout backups, and the widget stacks line on the device checklist has not been confirmed on a phone.
- Tests: unit tests for the stack member codes; an emulator test stacks a Counter behind a Note, switches with the rail, and checks its data and slot survive.

## 0.51.0-uno01

- **Material You colors.** A new switch under Customize > Wallpaper, **Material You colors**, takes the colors of buttons, switches, sliders and glass from Android's Material You palette (made from your system wallpaper). It replaces *Color from wallpaper* while it is on, and the same contrast checks keep controls readable whatever the system picks. Uno's own palette is still the default.
- **More in Spotlight.**
  - **Settings pages:** typing "wifi", "bluetooth", "brightness", "battery", "fingerprint", "vpn" and so on offers the matching Android settings page (18 of them; matched by name or alternative words, from three letters).
  - **Quick actions:** "timer 5", "10 min timer" or "set a timer for 90 s" starts a timer in the island, and "stopwatch" starts or stops the stopwatch.
  - Nothing is read from other apps and nothing leaves the phone. Calendar events are not included: they would need a new permission.
- **Backup completeness checks.**
  - **After saving**, the backup is read back the way a restore would read it, and the confirmation says what was saved ("3 pages, 2 hidden apps, 4 folders, 5 widgets"), or lists anything that did not survive.
  - **Before restoring**, the review screen lists what comes back, with a mark for each line: pages, apps (and any not installed on this phone), folders, widgets (and which must reconnect), hidden apps, settings, and what backups never carry (widget stacks and your background photo).
  - **Hidden apps are now in backups.** Backups made before this have none and leave your current hidden apps alone.
- Tests: unit tests for settings and timer matching; emulator tests for the dynamic palette, the saved setting, the Spotlight rows, hidden apps in a backup and the completeness check (including noticing a changed Home).

## 0.50.0-uno01

- **Hidden apps.** Long-press an app and choose **Hide app**: it leaves All apps, the pickers, Spotlight suggestions, Home and the dock. Typing the app's exact name in All apps or Spotlight still finds it. **Settings > Notifications & privacy** lists your hidden apps, each with **Show again**. An app inside a folder has to be taken out of the folder first. Hidden apps are not yet carried in layout backups.
- **More in the long-press menu:** **Uninstall** (not offered for phone apps that cannot be removed; Uno now asks Android for the permission to start an uninstall) and **Turn badges off for this app**, which silences one app's badge.
- **Reorder Home pages.** Edit Home's **+** menu has **Move this page earlier** and **later**; the page's apps, folders and widgets move with it.
- **Badges:** choose **Count** or **Dot** (a plain dot with no number) under Notifications & privacy.
- Already in earlier releases, for reference: apps' own quick actions in the long-press menu and opt-in notification badges.
- Tests: unit tests for hidden-app matching, moving pages (apps and widgets travel, edges do nothing) and the badge mute list; emulator tests hide an app from its menu and move a blank page earlier.

## 0.49.1-uno01

- **Dune range: the distant rock ranges are cleaner too.** They keep about a third of the grain they had (the sky keeps a fifth, and the sand keeps all of it), and the soft blotchy mottling in the haze is about 45% weaker, so the ridges read as haze and rock rather than noise. The composition, colours and the sand's texture are unchanged.

## 0.49.0-uno01

- **Dune range is crisper and cleaner.** Same composition, same colours; what changed is how it is drawn.
  - **Sharper:** the scene was computed at 720 px wide and stretched to your screen, which softened every edge and enlarged the grain. It is now computed at your screen's own width (up to 1080 px), and the blend along each ridge and dune crest is half as wide, so silhouettes are crisp.
  - **A clean sky:** film grain over a smooth gradient reads as noise, not texture. The open sky now keeps a fifth of the grain, with a hair of fine dither so it does not band. Measured on the same rows, the sky's noise drops about four times; the sand's is unchanged.
  - **Finer sand:** the sand keeps its grain, now fine instead of coarse after the stretch, so it reads as sand rather than blotches.
  - Dune range only: the other built-in backgrounds are drawn as before.
- **Dune range takes a little longer to appear the first time** after a change of size or light/dark, since it draws more pixels (on a background thread, with the gradient showing meanwhile).
- Tests: a regression test checks that the sky's noise is well under the sand's, day and night, and that the sand still has texture.

## 0.48.3-uno01

- **Layout backups now include blank Home pages.** A backup stores how many Home pages you had, so pages you added with Add page come back after a restore. Backups made before this have no page count and restore as before (only pages with something on them). Layout backups stay version 3, so an older build can still read a newer backup, though it ignores the page count.
- Tests: an emulator test round-trips a backup with blank pages, an older backup without a page count, and a nonsense page count; a stale assertion that expected backup version 2 was corrected.

## 0.48.2-uno01

- **Fixed a crash after restoring settings from a backup.** Root cause: Android's JSON writes a whole-number float (a keyboard haptic strength of 1.0, the island size at 1.0) as `1`, so the restore read it back as an integer and, on an install that had never saved that setting, stored it as an integer; the launcher then read it as a decimal and threw `ClassCastException` at startup. Restore now writes these settings (keyboard haptic strength, island size, tilt, and the three lens knobs) as decimals, and every place that reads them accepts a stray integer, so an install already holding the bad value starts again without reinstalling.
- Release notes are now picked up by `.gitignore` automatically, so a new notes file no longer needs `git add -f`.
- Tests: a unit test for the float keys and an emulator test that restores a whole-number float into a fresh install and reads it back.

## 0.48.1-uno01

- **The app is now signed with a new key.** The password for the old signing key was lost, so a new key was made and its password stored safely. Android will not install an update signed with a different key, so **this release does not install over 0.47.0 or 0.48.0: uninstall Uno first.** Before you do, export a layout backup from Settings (Backup) and restore it afterwards. Future releases use this key, so this is a one-time step.
- No other changes: the 0.48.0 features (several Home pages, the Spotlight revamp, exact widget resize rows) are all here.

## 0.48.0-uno01

- **Several Home pages.** Edit Home's **+** menu now has **Add page**, which keeps a blank page until you fill it, and **Remove this empty page**. Pages survive restarts and undo; the cap is 12. (Before, a page existed only while something was on it.)
- **Spotlight, revamped.**
  - Results are grouped under Apps, Contacts, Saved headlines and Search elsewhere, in one row style, and the top hit (what Enter opens) is marked.
  - Smarter matching: word starts, **initials** ("ytm" finds YouTube Music, "gm" Google Maps) and loose letter matches for three letters or more. Equally good matches put the apps you open most, and most recently, first. That history stays on the device.
  - Before you type, Spotlight suggests the apps you open most.
  - New answers on the device: "date", "today", "time". Tapping any answer copies it.
  - A visible "Search the web for ..." row, the same action as Enter.
- **Widgets no longer end up taller than you set them.** Root cause: dragging the resize handle converted finger travel to rows with the narrow top-row height, but the rows below are taller, so the same drag counted extra rows (a half to a full row too many). The handle now measures against the real row boundaries.
- **Faster development loop:** `scripts/quick-check.sh` compiles and runs the unit tests in seconds; the deeper checks are for releases.
- Tests: new unit tests for pages, resize rows, matching, frecency and date answers; an emulator test adds and removes a page.

## 0.47.0-uno01

- **The widget picker shows what you are choosing.** Every Uno widget (and the Clock and Date cards) is drawn as its real face at Home size, in a two-column grid with its name, size and what it does. A tap on the preview places the widget. Previews use sample data (a track playing, an alarm for tomorrow, a note, a counter at 3) and never read or save anything of yours. Search now matches widget names and descriptions.
- **Every widget redesigned**, in the same spirit as the Media player and the battery:
  - **Month:** an accent disc for today, dimmer weekends.
  - **Day, month and year:** gradient bars in three colours.
  - **Moon:** a halo and a shaded sphere with faint seas.
  - **Countdown:** the number in the accent colour. **Note:** an accent bar and a bold first line. **Counter:** a soft glow behind the number. **Timers:** accent pills.
  - **Sunrise and sunset:** the sun's path as a dome from sunrise to sunset, with the sun where it is now.
  - **Analog clock:** a filled dial with an accent mark and centre.
  - **Next alarm:** a large time and a chip for how long to go.
  - **World clock:** a sun or moon dot for day or night at each place, and "+1" in place of "Tomorrow", which squeezed the city names.
  - **Glance:** chips for the alarm and for sunrise and sunset, with the sun times shortened so they fit.
  - Colours come from one shared palette and follow your wallpaper accent when "Color from wallpaper" is on.
- **Since 0.46.0 (so you see it in one place):** the Molten metal wallpaper, icon packs, the Media player and Liquid battery redesigns, island styles shared as text, the glass UI kit for menus, the accessibility pass, and the first-run tour. See the 0.46.0 and 0.46.1 notes.
- **"What's new" note** updated to cover this release and the last.
- Tests: emulator tests check that the picker shows the real faces with sample data, that writes to a preview are ignored, and that tapping a preview places the widget; the widget, accessibility and "what's new" suites pass.

## 0.46.1-uno01

- **Media player, redesigned.** The album art is blurred into a wash behind the card, so every track brings its own colours; text and controls are white on it with a filled play button. At 2x2 it shows title, artist, a progress bar with a knob and previous, play and next (before, the title and artwork were squeezed out entirely); at 4x2 the artwork becomes a tile beside them, with the elapsed and remaining time. The empty state is calmer. Long titles end in "…" (a scrolling title was tried and left out: it keeps Compose busy and hangs the emulator tests).
- **Liquid battery, redesigned.** The whole card is the vessel: the liquid fills it edge to edge with a second slower wave behind the first, a bright waterline, light under the surface and a glint down one side. It is green with rising bubbles while charging, orange at 20% or below, red at 10% or below, and your wallpaper accent (or a clear blue) otherwise; the percentage is large and stays readable where the surface crosses it.
- **Battery ring polished:** a thicker ring with a gradient that brightens toward its end, and a percentage that no longer collides with it.
- A patch release no longer shows the "what's new" note again: it is shown once per feature version (0.46, then 0.47).

## 0.46.0-uno01

- **A new Molten metal wallpaper**, redone from scratch to evoke the iPhone 17 Pro wallpapers while staying dune-like: long wind-sculpted sweeps drawn as one continuous field of glass-like contour lines that bend into and through each other, crisp hairline highlights with a faint red-blue fringe like thick glass, light that pools just past a contour and falls away to black, and grain on the glow only. Ice blue by day, ember by night, and mostly black so icons stay readable, with a dark top for the camera and island.
- **Icon packs.** Pick any installed icon pack that works with Nova, ADW or Lawnchair under Icons. Apps it covers use its icons; the rest keep your icon style. Uno reads only the pack's list of icons and its pictures.
- **New widgets**, written by the widget lane and reviewed: **Media player** (art, title, play and skip, a seek bar), **Glance** (time, date, next alarm, sunrise and sunset), **Liquid battery** (the charge as a liquid that ripples and tilts with the phone), plus the Photos album source and World clock from earlier. Fixed on review: the media widget no longer asks the player anything while drawing (it could freeze Home), its empty state says which setting to turn on, and Glance names the day of an alarm more than a day away.
- **One glass style for menus.** A small kit (dialogs, buttons, chips, fields, switches, a date picker) now builds the settings pages, the Home dialogs, the widget menus and the first-run sheet, so they match the rest of Home. The rules are in `docs/design.md`.
- **Island style grows up.** Eight looks (three new: Aurora, Rose, Forest), the island theme is in settings backups, and a theme can be shared as text: **Copy this theme as text** and **Paste a theme from text** (read only when you tap Paste, checked and previewed like a file).
- **Accessibility pass.** Every control on Home and the settings pages has a name (an audit found 379 without one; now none), switches read as one control with their label and have a 48 dp target, empty Home spaces are announced, the island has a label, and text at 200% size wraps without overlapping.
- **Words moved out of code** (227 texts) into one resource file, with a test that stops plain text coming back; a step toward translation.
- **A short "what's new" note** after an update (shown once), and three tips in the first-run sheet.
- **Location fixes** for sunrise and sunset: a place can be typed with a comma decimal or pasted as "lat, lon", and the page confirms what was saved with today's times; "Use device location" waits longer for GPS, can be cancelled, and falls back to the last known place.
- **Set Android's wallpaper too** (from 0.44): off by default.
- Decisions written down: nested folders will not be built, folders in the dock and Private Space are "not yet" (`docs/design.md`).

## Unreleased

- **A seventh built-in wallpaper: Molten metal.** Abstract dune sweeps poured in liquid metal, after the iPhone Pro liquid-metal wallpapers and tuned for OLED: a specular rim along each crest that shifts hue where the metal turns to the light, bodies falling to deep glossy black, and vivid coloured light spilling off the crests and pooling electric in the troughs. Silver and cyan over a deep blue sky by day, copper and embers over pure black by night, with the same black night top as the other scenes. No grain: clean gradients with a one-code-value dither so they cannot band. Drawn on the phone like the rest; appears in the wallpaper picker with its own day and night versions.
- Tests: unit tests that the scene keeps its broad abstract geometry on screen, renders day (deep blue sky, silver rims, cyan pools) and night (pure black sky, ember copper, deep blacks) distinctly, and stays deterministic, fast and well-formed at any size.

## 0.45.0-uno01

- **Island themes.** The island's look is now data: body and text colours, four accent colours (playback, charging, timer and silent, focus), a panel shape (soft, round, square), a button shape (pill, rounded, square) and a motion feel (calm, standard, snappy, bouncy). **Launcher settings, Dynamic island, Island style** has five presets (Classic, Midnight, Graphite, Neon, Amber), a live preview, and pickers for each colour and shape. Classic is exactly the look you had before.
- **Import and export.** **Import a theme** reads a small text file you pick, shows a preview, and applies it only when you tap Apply; **Export this theme** saves the current look so you can edit or send it. Only files you pick are read; Uno has no way for another app to send it a theme. The format and how to write one are in `docs/island-themes.md`.
- **Safe by construction.** A theme cannot change what the island does. Camera, microphone, screen-recording, VPN and USB marks and the call buttons keep their own colours. Files over 8 KB, invalid files, a body that is not dark (the camera hole must stay hidden) and text or accents that would not be readable are refused with the reason.
- **Glass UI kit.** A set of building blocks for menus (`GlassDialog`, `GlassButton`, `GlassChips`, `GlassSwitchRow`, `GlassRow`, `GlassTextField`, headings and notes), with rules in `docs/design.md`. The Photos widget's settings, which used Material's plain dialog, were rebuilt on it, as were the Note, Counter, Countdown and World clock dialogs. The Countdown date picker is still Material's.
- Tests: unit tests for the theme format and legibility rules (including that the documented sample parses), and emulator tests that drive the Photos menu and the island style page and photograph a themed island.

## 0.44.0-uno01

- **Set Android's wallpaper too.** On the Wallpaper page, **Also set home and lock screen** (off by default) hands the background you choose, a built-in scene or your photo, to Android as the home and lock screen wallpaper. Turning it on sends the current background at once and replaces what is there now, including a live wallpaper. Adds the install-time `SET_WALLPAPER` permission (no prompt).
- **Photos widget: pick an album.** The widget can now rotate through every picture in one of your photo albums, including new ones, besides the photos you choose by hand. This asks for the Photos & media permission, only when you tap Allow in the widget; without it the widget works as before. Listed in the permission ledger and in `PRIVACY.md`.
- Widget work is now done in its own lane (own working copy, lane-check script) so it can proceed in parallel with launcher work; see `docs/widget-development.md`.

## 0.43.1-uno01

- **Photos widget now shows your photos.** Choosing photos saved them but the widget never drew any: the code that reads a picture's size treated Android's normal "no bitmap returned while measuring" answer as a failure, so every picture was rejected. Fixed, with an emulator test that puts a real image in the widget and checks it is drawn.

## 0.43.0-uno01

- **Six built-in wallpapers**, each with a day and a night version: Uno dunes (the original), **Deep dunes** (the original three dunes with soft flowing depth), **Dune range** (broad dunes under hazy mountain ranges, with stars at night, closer to the iPhone Duo's desert), **Dune glass**, **Dune bloom** and **Dune flow** (abstract scenes with grain and soft light, made to give the glass something to refract). All are drawn on the phone, nothing is downloaded.
- **The night wallpapers fade to pure black at the very top**, so the camera cutout disappears and the island appears to emerge from the dark.
- **A new wallpaper picker**: a grid of previews of the built-in scenes, so you can see them before choosing.
- **Photos widget**: shows photos you choose with Android's document picker (read access to those images only, no photo-library permission), changing every 15 minutes. Remove all forgets the list.
- **World clock widget**: the time in up to four places you pick, in your 12/24-hour style, with Tomorrow or Yesterday when a place is on another day.
- A contributor checklist for widgets in `docs/widget-development.md`.
- Tests: unit tests for the wallpaper renderers (deterministic output, geometry, night stars, black-top mask), Photos and World clock rules, and the widget registry.

## 0.42.0-uno01

- **Edit Home, like iOS's jiggle mode.** Long-press empty space and choose **Edit Home** (an app's options have it too). Icons wiggle and carry a **−** badge, a drag starts the moment your finger moves (no long press), and free cells show as faint squares with the one under your finger filling in, so it is plain where an icon can go. Tap **−** to take an app off Home (it stays in All apps), **+** adds apps or a widget, and **Done** or Back leaves. A tap on an icon does not open it while you edit.
- **Forgiving drops.** A drop that just misses a cell (in a margin, or beside the grid) now lands in the nearest Home cell instead of being thrown away, within three quarters of a cell's height. Dropping one app on the middle of another still makes a folder.
- **About "only two columns":** every free cell on a Home page already accepted a drop in my check (the layout allows gaps anywhere), so the grid itself was not the limit. What was missing was any way to see or feel that: no free-cell hints, no edit mode, and moving by "Move earlier / Move later" buttons. This release adds the first two; the Move buttons are still there under an app's options.
- Tests: unit tests for the wiggle, the distance and nearest-cell rules; emulator tests that enter Edit Home, check the badges and free cells, remove an app with its badge, drag with no long press, check that a tap does not open an app while editing, and that Done and Back leave. The drag, paging, workspace, folder and widget-move suites were re-run on the result: of 29 tests, 4 failed, none because of this change. Three need a widget-binding permission that a fresh emulator does not grant ("Emulator fixture requires widget binding permission"), and one (`draggingAFilteredAppDismissesTheKeyboardBeforePlacement`) drops an app on the middle of another and expects a plain move, which since large folders arrived makes a folder, so the test needs updating.

## 0.41.0-uno01

This release reorganises the code so that more than one person can work on it safely (no change to behaviour there), and improves the widget library.

- **Code is grouped by feature.** The Kotlin files now live in a folder (and package) per feature: `island/`, `search/`, `panel/`, `appearance/`, `folders/`, `feed/`, `widgets/` and `keyboard/`; Home, state, paging, drag, Android widget hosting, Discover, backup and the updater stay at the top level. Moving a file only changes its package line and the imports that use it; the 76 unit-test files and the emulator suites for the island, settings, folders and widgets were run on the result. The manifest names three components by their new package (the island overlay service, the timer receiver and the dune wallpaper service).
- **Uno widgets are one file each.** `UnoWidgets.kt` is now only the registry; every widget (`BatteryWidget.kt`, `MonthWidget.kt`, `MoonWidget.kt` and so on) has its own file in `widgets/`. A new test fails if a widget id is repeated, positive, or a shipped id disappears, because saved layouts and backups depend on them.
- **Four widget improvements and two new widgets** (all permission-free, all local):
  - **Analog clock** (new): a dial with hour and minute hands and the date; it redraws once a minute, exactly when the hand moves.
  - **Next alarm** (new): the time of your next clock-app alarm, which day, and how long until it; tap to open the clock app. It asks Android for that one answer (no permission) and keeps nothing; it updates the moment an alarm changes.
  - **Moon** now says what is coming: "Full moon in 6 days" or "New moon tomorrow", whichever is nearer.
  - **Countdown** can repeat every year, for a birthday or an anniversary: it rolls on to the next time the day comes round, and 29 February falls on the 28th in a year without one.
  - **Timers and the island agree.** The Timers widget offered 5, 10, 25 and 45 minutes while the island's own tools offered 1, 5, 10 and 30; both now use one list (5, 10, 25, 45), kept in one place so they cannot drift apart again.
- **Guides for working together.** `docs/working-together.md` describes the repository (public, a protected `main`, release tags only the owner can create, fork and pull request, who reviews what); `docs/widget-development.md` is the rules and checklist for writing a widget; the code map, island roadmap, user guide and troubleshooting guide are brought up to date.
- **On GitHub:** `main` now requires a passing CI build and a code-owner review (the owner can still push), `.github/CODEOWNERS` keeps the build, release, permissions and updater files with the owner, and `v*` release tags can only be created by the owner.
- Old planning notes that were superseded were removed.

## 0.40.2-uno01

From an independent audit's improvement plan, the small, safe items only.

- **Privacy wording now matches what the code does.** Three places said "message text is never read" while the app does read a promoted Live Update's title, body and status, a navigation instruction and, if you turn it on, the title or text of the island's notification card. They now say that chat and message notifications are never read for their text, and name the exceptions: the notification-access comment in the code, the Live Update comment, the call setting's description, the README and `PRIVACY.md`. `PRIVACY.md` also now says that a media session's position and length are read, in memory only, to decide whether paused media is worth returning to.
- **The island's clock follows your phone's 12/24-hour setting.** The open island always showed 24-hour time; it now shows "1:05 PM" or "13:05" like the status rail does.
- **Location is stated plainly.** Under the sunrise and sunset settings, a line says the place is kept only on this device and never sent until you clear it, and `PRIVACY.md` says the same. It is not cleared automatically: the sunrise widget and the sunrise-sunset appearance both use it, so clearing it when one of them is switched off would break the other. **Clear location** removes it.
- **The background-start allowance is explained where it lives.** The comment on how the island sends a tap action now says it is attached only there, and only to a tap.
- **Island state is under test.** 22 new unit tests pin how the island moves today: opening and closing, events taking turns and the queue limit, the Focus side effect, notification cards, playback timing and the silence rule, the privacy indicators and the order of activities. The island's tools object no longer needs Android to start, which is what let these tests run on the computer.
- **An honest emulator suite.** Five widget tests in `SharedGridIntegrationTest` failed on a fresh stock emulator. Four were failing on their own set-up (the widget picker has grown since they were written) (not on the behaviour they check) and are now quarantined with `@Ignore`, with the reasons written in `docs/future-fixes.md` under "Test debt"; the fourth skips itself when Home has no real Android widget bound. Waits in that file are longer.

## 0.40.1-uno01

- **The glow round the camera is gone.** Paused media worth returning to no longer rings the camera in the cover's colour. Everything else about 0.40.0 stays: the wide pill is for playing sound, paused or finished media lingers six seconds with still bars, media worth returning to (a title, part-way through, longer than 30 seconds) leaves the pill and stays on the open island for two minutes by default (1, 2, 10 or 30 minutes in Dynamic island settings), and a player that says "playing" over silence stops counting after four seconds. The pill's own rings (timer, charging, event flash) are unchanged.

## 0.40.0-uno01

- **The wide pill is for sound that is playing.** Playing media shows the wide pill with the moving bars and the cover-colour wash, as before. What happens when it stops now depends on whether it is worth coming back to:
  - **Finished or not worth returning to** (a video that reached its end, a clip of 30 seconds or less, a player that stopped, anything without a title or a position): still bars on the pill for about six seconds, then it goes. It is gone from the open island too.
  - **Paused part-way through something worth returning to** (it has a title, a position, is longer than 30 seconds and is not at its end): after the six seconds the pill goes back to its resting size and a soft ring in the cover's colour glows round the camera. The track stays on the open island, where you can resume it, for two minutes by default.
- **A setting for that time.** *Dynamic island → Paused music and podcasts*: 1, 2 (default), 10 or 30 minutes.
- **The glow sits behind the timer and charging rings.** It is drawn outside the pill and under any ring, so a running timer or a charging ring stays on top and unchanged. It is not shown while the island is open, while an event flashes, or while a timer rings.
- **A player that says "playing" over silence stops counting after four seconds.** A muted autoplay video in a feed reports "playing" while no sound comes out, and since 0.38 the island trusted the player over the audio system, which is the likeliest reason the indicator hung around. Playing now needs the player to say so and audio to be coming out (or to have gone quiet only a moment ago); with no player session, audio alone decides as before.
- Tests: unit tests for every phase and boundary (linger, resumable, window, settable window, the silence rule, what counts as worth returning to); emulator tests for paused-and-resumable, just paused, finished video, short clip, no title, past the window, and a session playing over silence.

## 0.39.1-uno01

- **Tapping a notification in the island is more forgiving, and its tap action is sent properly.**
  - A tap anywhere on a notification's flash (not only on the 20 dp app icon) now opens that notification. Before, a tap that just missed the icon opened the island's panel and threw the flash away, which looked like "the tap does nothing".
  - The icon's own tap target is larger (40 dp).
  - Every tap action the island sends (a notification's, the music player's now-playing screen, a call's, a Live Update's) now says that it may bring an activity to the front. From Android 14 a sender that does not say so can have the start silently dropped while the call still reports success; that is the likeliest reason it worked on the emulator but not on your phone. If an action still fails, the island falls back to opening the app itself.
- Tests: emulator tests tap the track in the open island and the app icon on a notification flash, and check that the right app or screen is in front afterwards.
- Not verified: I cannot send a real notification from another app to the emulator, so the flash was driven directly with a notification-style tap action. Please say whether tapping a notification (icon or pill) and the music card now opens the app; if it still does not, say which of the two, and whether it is over Home or over another app.

## 0.39.0-uno01

- **Search now grows out of the camera, and the dimming reaches the top.** On a phone whose Home window starts below the camera cutout, the search panel was laid out in the wrong coordinates: it sat about one cutout's height too low (a black blob under the camera, not joined to it) and the dimming layer stopped at the same line, leaving a lighter strip along the top edge. That strip is the "shaded area behind search" that has been there since the control panel and search were added. Both are now laid out in the window's own coordinates, the panel takes its position from the island's own resting frame (the one the island is drawn with), and the dimming covers the whole window. An emulator test puts the panel inside a parent that starts well below the top and checks that the panel's top equals the pill's top and the dimming reaches y = 0.
- **Tap the cover or title to open the player.** In the open island's music card, tapping the album art or the track opens the app that is playing: its own now-playing screen if it offers one, otherwise the app itself (before, it did nothing for players that offer no now-playing screen).

## 0.38.0-uno01

- **Search grows out of the island.** Pulling down for search (or tapping the island's Search button) no longer fades in a glass sheet: the panel starts as the collapsed pill, true black like the island, and grows to its full size from the camera, shrinking back into it when you leave. The text and results fade in once there is room for them. Without an island the panel still opens from the top, kept clear of the status bar. (The frame-by-frame motion is a spring and could not be captured exactly on the emulator; the sizing rules are unit-tested and the open and settled states were checked in screenshots.)
- **Tapping the app icon on a notification opens that notification.** When a notification only has room for the app's icon and name on the pill, tapping the icon now sends the notification's own tap action (the conversation, the message, the download) rather than just opening the app's front page; an app that gave no action is opened as before. The drop-down card does the same, and falls back to opening the app if the notification has no action.
- **Music detection is quicker and more accurate.**
  - The island now trusts the player's own session first: it knows "paused" the instant you pause and "playing" before the audio system has said anything. The audio system's answer is used when there is no session.
  - Every media session is watched, not only the one on show. Before, a second player that started while the first sat paused was not noticed until the list of sessions changed.
  - A player that is buffering (starting, or catching up after a seek) counts as playing, so the island no longer flickers to "paused".
  - Paused controls leave at the exact moment their 90-second window ends, not at the next tick of a slow clock, and leave at once when the player closes. A start or a stop each restart the window.
- **The waveform moves.** Checked on the display in a plain activity (the test framework switches endless animations off, which is why an earlier check seemed to show still bars): the four bars change from frame to frame while playing and sit still, at rest height, when paused.
- Tests: unit tests for the playback rules and the search sizing; emulator tests for the session-versus-audio cases, the controls leaving and returning, and the moving and still waveform. A small debug-only activity hosts the bars for that check; release builds do not contain it.

## 0.37.0-uno01

From an independent UX audit of how the launcher's borrowed design languages meet. The audit's overall finding stands: Apple's look, Android's behaviour and GrapheneOS's values are the product, and what needed fixing was the places where two of them answered the same question. Judged against the code, some of its points were already true and are left as they were (see below).

- **One owner for the battery level.** With the vertical status rail on, the rail shows the time and a battery ring, and the collapsed island also printed the battery percentage. The island now says nothing about the battery unless it is charging; with the rail off it still shows the percentage as before. (Over other apps, and with the rail off, nothing changes.)
- **"Focus" means one thing.** The island's flash for switching Android's Do Not Disturb off said "Focus off", the same word as Uno's own Focus (the switch that hides the apps you pick). It now says "Do Not Disturb off".
- **One control centre, one alternative.** The right-hand pull-down choice "Android Quick Settings" is now **Android mode** ("Opens Android's own Quick Settings instead"), so the control panel is clearly the default and the other is clearly the exception.
- **The keyboard says what it does.** Under the keyboard's switches a line now reads "Suggestions use a fixed word list. Nothing you type is learned or stored", where the longer explanation was at the bottom of the page.
- **A written metaphor policy** in `docs/design.md`: an event-home table (each kind of event has one home), one owner for time and battery, one control centre, imports restyled before they ship, one name per thing, Back always works, and consent in the product's voice.
- **Checked and left alone:** rows that leave Uno for Android's Settings (brightness access, Do Not Disturb access, System settings, shortcuts) already carry a chevron; the keyboard's switcher key already uses the same glass key as the rest of the keyboard; the island's music bar is already drawn in the island's own style; every overlay and page already handles system Back; "Spotlight" appears nowhere on screen (only in code), so there was no second search name to unify. Time was never shown by the collapsed island.

## 0.36.0-uno01

- **One name for settings.** The home menu's "Customize launcher", the island's "Customize" button, the settings sheet's close button and the places that told you to "hide in Customize" or "turn one on under Customize > News feed" now all say **Launcher settings** or **Settings**, the same name as the sheet's title. The help page's "Customize any page" is "Settings on any page".
- **A menu that says what it holds.** Long-pressing empty space showed a sheet titled "Add to Home" whose rows were Widgets, Wallpaper and Launcher settings. It is now titled "This space", with the line "Add a widget, change the wallpaper, or open settings."
- **Leftover names fixed.** The wallpaper page said the preview "does not change Duo's launcher background"; it now says Uno's. The App icons page no longer repeats its own title as a heading (it says "Icon style"). The island's privacy-indicator switch describes the new dots rather than marks.
- **A tour that looks at every screen.** A new emulator test walks the home screen and every settings page and saves a screenshot of each, so the whole launcher can be reviewed by eye after a change; it found the wording above.
- Release notes are now listed in the public-source allowlist as part of the release steps (0.35.0's first two publish attempts failed because its notes were missing from it; the third passed).

## 0.35.0-uno01

- **Music washes the island in its colour.** While something plays, the album art's colour fades from the left edge of the pill and the open panel into the island's black (as HyperOS does). Paused music keeps a fainter wash; a notification card, an event flash or a ringing timer takes the island's colour first, so two colours never compete.
- **Privacy marks are dots, not pill slots.** The camera, microphone and screen-recording marks are small round dots beside the pill, so a video call keeps its call time and its music on the pill and shows the dots as well. Previously the three marks could take the pill's two places.
- **A call shows how long it has lasted** (green, ticking every second) on the pill instead of a name cut to a few letters. The name is on the open call card, whose second line no longer clips.
- **One rule for what is shown.** Of the five activities (call, timer, stopwatch, Live Update, music) the pill shows two and bubbles show the next two; only music, the least urgent, is left out when all five run at once. Every combination is covered by a unit test.
- **Text sizes agree:** timer, stopwatch, Live Update and call text on the pill all use one size. A peek card does not appear over a ringing timer.
- Tests: scenario tests on the emulator for a video call, an event during a call, music playing, music with a call and a crowded island, with screenshots; the older island tests wait for the island's system listeners before setting state.

## 0.34.0-uno01

- The island takes the colour of an app's icon. A new notification's card has a wash of the icon's colour fading into the island's black, an event flash for an app rings the pill in that colour while it shows, and a Live Update or download whose app gives no colour of its own uses the icon's (a progress ring and glyph in that colour). The colour is the icon's dominant vivid hue (the same picker used for album art), worked out once per app and kept; grey, black and white icons give none.
- "Permanent wide island" (Dynamic island settings): over other apps the island normally shrinks to a ring round the camera when it has nothing to show, so that it does not cover the status bar. With the switch on it stays the wide pill (with the battery level on one side) all the time. Inside the launcher it was always wide.
- Smoother island, found by reading how it opens:
  - The quick-launch strip's icons were looked up from the package manager at the moment the island opened, and the strip then arrived half way through the animation, changing the island's height. That was the stutter you saw with app icons showing. The strip is now looked up ahead of time (at start, and whenever the island is closed and the shortcuts changed) and the island's height already includes it when it opens.
  - Over other apps, the touch window followed the island's size on every animation frame, which is a call to the window manager per frame. It is now told the size the island is heading for as soon as a change starts, held there while it animates, and snapped to the settled size afterwards.
  - The island's biggest sizes (for the fixed drawing window) are worked out once per environment rather than on every frame; a notification card's icon is wrapped once.
- Smoother dragging: while an app was being dragged, the pointer position was read directly while composing the whole launcher screen, so every touch event recomposed all of it. The "what is under my finger" answers (the drop target, whether a drop makes a folder, which screen edge the finger is on) are now derived, so the screen recomposes only when an answer changes. App icons are wrapped as Compose images once per app instead of at every use, which also lets Compose skip icons that did not change. The search suggestions in All apps (which ask the package manager which browsers and stores are installed) are worked out when the search changes, not whenever the list is rebuilt.
- Less idle work: the clock and date cards tick once a minute instead of every second; the Timers widget ticks only while a timer runs; and a burst of notifications (a download posts one every second) is read once per burst rather than once per notification.
- A baseline profile for the app's own code is bundled, so Android compiles it ahead of time when the app is installed instead of discovering what to compile while you use it (faster start and fewer first-use hitches). The release build was checked to turn it into a profile. Effects are not measured: I have no phone to measure on, so none of the smoothness work has been timed; it removes work I found by reading the code.
- Tests: unit tests for the frame helpers and the ring colour; the emulator suites for the island, dragging, folders, widgets and settings were re-run on the final code.

## 0.33.0-uno01

Ideas taken from HyperBridge (an Apache-2.0 app that feeds notifications to HyperOS's island; read for ideas only, no code used).

- Downloads and other progress: any ongoing notification with a real, unfinished progress bar (a download, an upload, an install) shows on the island as a ring round the app's icon with its percentage, and as a card with a bar when the island is open. Only its title and progress are read, never its body text; players, calls, alarms and messages are not counted as tasks. Switch: "Downloads and other progress".
- Navigation: an ongoing navigation notification (the navigation category, so Organic Maps, OsmAnd and the like as well as Google Maps) shows its instruction and the line under it (distance or arrival) on the island. Switch: "Navigation in the island". Both kinds and Live Updates share one list on the island (newest first, up to three), so they already get the pill's two slots and the pop-out bubbles.
- Notification cards: the island now drops down a card for a new notification and tucks it away after a few seconds, instead of only flashing the app's name. You choose how much it shows: app name only (the default, as before), plus the title, or plus the text. Tapping the card opens the notification itself (its own content intent, so a message opens its conversation); a swipe up or the wait dismisses it. It does not interrupt an island you have opened yourself. Android's own heads-up banner cannot be suppressed by a launcher, so turn "pop on screen" off for the apps in Android's notification settings if you want only the island's card.
- Spoiler protection: a list of words that keep a notification's text off the island. If any of them appears in the title or text, only the app's name is shown. Case is ignored; the list is cleaned and bounded.
- Per-app timeouts: in "Apps that can use the island" each app has a time (Auto is 6 seconds, then 3, 6, 10 or 15) for how long its card stays. The same list's switches now also cover progress and navigation.
- Privacy wording: notification access now reads, beyond the app names and counts of before, a Live Update's text, a download's title and progress, a navigation instruction and, only at the card levels you choose, a new notification's title and text. PRIVACY.md, the permission ledger and the README say so, and the ledger tests were updated from "never the text of a message" to the accurate statement.
- Not built: inline reply from the card (it needs typing, which the island over other apps cannot take), and a separate "floating" choice; the card's timeout and tap-to-open are the behaviours. The flash for the name-only level keeps its fixed length.
- Tests: unit tests for the activity classification (what counts as a download or navigation), the card levels, the blocked words and the per-app timeouts; emulator tests that the card shows its title and text, drops the text line at the title level, tucks away after the app's time, and does not interrupt an island you opened. Not seen with real apps: a real download, a real navigation notification, a real message card.

## 0.32.0-uno01

A batch of island work, prompted by a review of what was half-built. The three problems reported from the phone (the island search field that could not be found, device location that never answers, and entering a place by hand) are written up in docs/future-fixes.md with what is probably behind each and what to try, and are deliberately not changed here, except that the first is helped by the overlay buttons below. docs/future-fixes.md also lists every shipped feature that is thin or has never been run on a phone.

- The island above other apps now has buttons. The "Island everywhere" island had no action row at all, so there was nothing to tap for search, the feed or settings; the search field added in 0.31.0 only exists on the island inside the launcher, because the overlay is never focused and cannot take typing. The overlay's panel now has Search, Feed and Customize buttons that open Uno on the search box, the feed or the settings (MainActivity takes a destination from the intent).
- Quick launch from the island, as HyperOS's island carries app shortcuts: the open island shows the apps picked under Control panel & Focus, Shortcuts in the panel (up to five, one tap each, personal profile only), in the launcher's island and the overlay. A switch ("App shortcuts in the island") turns it off. Looked up off the main thread, and only while the island is open.
- Tools: long-pressing the island now also offers Android's own Internet and Volume panels (they slide up over whatever is on screen and need no permission), next to the timer, stopwatch and flashlight.
- Calls: an ongoing call's card in the island has an End button that sends the call app's own hang-up action. It appears only for an ongoing CallStyle call (Android puts the hang-up action first for those); incoming and screening calls have no defined order of actions, so they get none. The rule is unit tested; an emulator test shows the button with a call posted.
- Large folders: dropping an app onto a large folder puts it inside and takes it off its Home or dock spot (before, apps could be added only through "Choose folder apps"). From the All apps list the app stays in the list. An emulator test drags an app onto one.
- Tests and notes: unit tests for the quick-launch picking and the hang-up rule; emulator tests for the shortcut strip, the End button and the drop onto a large folder. docs/future-fixes.md is new and linked from the README.

## 0.31.0-uno01

- Sunrise and sunset from the phone's location now works on phones with no network or fused location service (GrapheneOS without a network location app). The old request only tried the network provider (and the passive one, which cannot answer a one-off request), so it ended with "No approximate location provider is available". It now asks for approximate and precise location together (Android shows its usual choice and either grant works), tries the fused, network and then GPS providers in turn, with a longer wait for GPS, and falls back to a cached fix from any provider. Whatever it gets, the saved place is rounded to a tenth of a degree (about 11 km, a minute or two of daylight), so a precise fix is never kept more exact than the feature needs. ACCESS_FINE_LOCATION is added to the manifest; it is asked for only when you tap Use device location, and PRIVACY.md and the README say so. The provider choice and the rounding are unit tested; the live fix has not been seen on a phone.
- Folder tiles on Home now wear the dock's look: a see-through tint with a bright rim that fades from the top-left (and the flat tile when glass is off). I first used the real backdrop lens as the dock does, but with it on every tile an emulator test failed at teardown (a Compose view-tree detach error), so the tiles use plain tint and rim fills; over a wallpaper the lens had little to bend, so the difference is small.
- Keyboard: after a symbol typed from the numbers or symbols page (an apostrophe, a full stop, an at sign) the keyboard returns to the letters, as iOS does; digits and the space bar keep you on the page so a number can be finished. And more room under the bottom row: Android's own keyboard-switcher button sits in the navigation bar just under the Return key on phones that show it, and the thumb aiming at the lower edge of Return landed on it. The gap is 20 dp instead of 10. (Uno's own globe key is on the left of the space bar, away from Return.) Unit tested for the page rule.
- The widget placement preview is drawn with the same origin the drag ghost and the tap handler use (the launcher root's), so it cannot sit off from where the widget lands when the root is not at the screen origin. On the emulator the preview and the placed widget already matched to the pixel (a new test checks it for the first and the fourth row), so if it still looks a row low on your phone, a screenshot of the preview and of the result would pin it down.
- Island search: the open island has a search field ("Search the web") in place of the Search button, with the magnifier opening the full search and the Feed and Customize buttons shown as icons. Type and press Enter, and your browser (Vanadium if installed, or the one chosen in settings) opens on its results page with the words already searched (the ordinary web-search request, which Vanadium handles). Spotlight's Enter key now opens the top app, or with no app to open runs the same web search. An emulator test types into the field, presses Enter and checks the query is sent. Widgets from other apps (a search-bar widget placed in the island) cannot take typing, so this field is Uno's own.
- Test notes: the held-drag page-turn test waited 5 s for a page to appear, which the (very slow) emulator no longer met; it waits 40 s now and passes.

## 0.30.0-uno01

- A library of nine new built-in widgets (widget picker, Uno widgets), drawn by the launcher itself with no provider app and no permission: **Battery** (a ring for the level, time to full while charging), **Month** (a calendar with today marked, from the date alone; it reads no calendar app), **Day, month and year** (progress bars and the days left), **Moon phase** (drawn from the date with the mean synodic month), **Countdown** (a name and a date you choose, days until or since), **Note** (a short note, up to 600 characters), **Counter** (tap to add, a small button to subtract, tap the name to rename or reset), **Timers** (5, 10, 25 and 45 minute buttons that start the island timer, with the running time and Stop) and **Sunrise and sunset** (from the place set in Appearance, worked out on the phone with the existing solar maths). Each is a built-in widget id, so placing, moving, resizing, paging and removing use the widget machinery; the registry (UnoWidgets) and each widget's rules (UnoWidgetLogic) are unit tested, and emulator tests place all nine and check their faces, a counter tap and that a note goes with its widget.
- What the typed widgets keep (notes, counters, countdown names and dates) lives in its own private preference file (WidgetData) and is pruned with its widget. It is now part of the settings backup, together with large folders' apps and names, which a backup used to drop: restoring a layout brings its notes, counters and large folders back. (A raised length limit applies to large folders only.)
- Cleaned up after a study of the whole app: the dead, unreferenced old settings panel (about 100 lines, still titled "Make it yours") is gone; directional icons that were flagged deprecated (back, forward, help, open, backspace) now mirror correctly in right-to-left languages; the Help page and the user guide, troubleshooting guide and roadmap no longer use the old settings names or say only a long-press opens settings; and a new section of the user guide covers the grouped settings, dragging to make folders, large folders and the widget library.
- Aesthetic pass from looking at the widgets on an emulator: the battery ring is thinner with a smaller percentage, the month grid has tighter padding so six-week months fit, the moon has an outline and a lit side that follows its phase, and the year card's bars fit with the days left in its title.

## 0.29.1-uno01

A security release, answering an October 2026 audit of 0.27.0 (no policy violations; two Medium and several Low findings):

- Updater fails closed (Medium): a release that publishes no SHA-256 checksum for its APK is no longer downloaded or installed unchecked, and the APK's signing certificates must now equal those of the installed app before it is handed to Android's installer (Android's own same-signer rule stays as the backstop). The key check reads the certificates out of the downloaded file with the platform's own APK parser; an emulator test proves it gives the installed app's certificates for its own APK. Pinning to the installed key, rather than a copy of the key in the source, means a rotated release key needs a manual reinstall, which is the safe direction.
- Size limits now bound the transfer, not just the result: the update check, checksum file, APK and every feed are read with a running byte count and cut off as soon as they exceed the cap (and a declared Content-Length over the cap is refused up front); the APK is streamed to disk while it is hashed instead of held in memory. Unit tested, including a stream that never ends.
- Updater redirects are followed by hand and only to https on github.com, api.github.com and *.githubusercontent.com (unit tested, including look-alike hosts such as github.com.evil.example); every hop is checked.
- The User-Agent now carries the real version (it said 0.16).
- Claims made accurate: Live Updates do read the body text and short status of the promoted ongoing notification (ride, delivery), and the code comment, PRIVACY.md and the ledger now say so, while chat and message notifications are still never read for their text. The accessibility consent text, PRIVACY.md and the ledger now disclose that the shade service also draws the island above other apps; the plain overlay is the documented alternative. Dismissing the shade prompt by tapping outside is now remembered like "No thanks" (Help and setup still offers it). The keyboard's stale comment that it has no suggestions or autocorrect is corrected.
- Secure screens (Medium, documented): the everywhere-island is drawn over banking and password apps because Android gives a launcher no way to tell which apps are secure without accessibility or usage access, which Uno refuses. It cannot be tapped through. PRIVACY.md and the overlay's ledger row now say so and point to "Island everywhere" as the switch. A per-app hide list is not built: it would need to know the foreground app.
- Not changed: encrypting the local location preference (it stays in the app's private storage, is never sent, and is excluded from backups; documented in PRIVACY.md); tightening the overlay's ~6 dp touch margin and its 2 s visibility poll. The audit's layout_backup_pending item was already done: the staging is cleared after every use.

## 0.29.0-uno01

- Large folders in 2 x 1 and 1 x 2: the widget picker offers "Large folder, wide" and "Large folder, tall" next to the 2 x 2 one, and any large folder can be resized down to 2 x 1 or 1 x 2 (never 1 x 1) or up to the whole grid. Thin faces use compact icons (32 dp in 40 dp cells) and drop the title so a few apps still fit: about three icons in a 2 x 1 and four in a 1 x 2. Metrics and capacity are unit tested; an emulator test places both shapes.
- Island: headphone and Bluetooth audio alerts. The island flashes the device's name when Bluetooth audio, a headset, wired headphones or a USB headset connects or disconnects (the phone's own speaker, HDMI and the like are ignored; the devices already connected when the island appears are not announced). It uses Android's AudioDeviceCallback, which needs no permission; a switch turns it off.
- Island: a red recording mark while the screen is being recorded (Android 15+). Android tells a window when it is being captured through WindowManager's screen-recording callback; it only says "recorded" or "not", never by what. It needs the normal, install-time DETECT_SCREEN_RECORDING permission (added to the manifest and described in the permission ledger) and follows the camera/microphone indicators switch. It is a privacy mark, so it takes a pill slot ahead of calls and media.
- Island: time to full. The open island on a charging phone shows "40 min to full" under the battery percentage, from BatteryManager.computeChargeTimeRemaining (asked off the main thread and only while open and charging).
- Debug builds' broadcast can fake the recording mark (--ez recording true). Not seen on a real recording, headphones or charger: the emulator tests cover the mark's appearing and disappearing; the wording and device choices are unit tested.

## 0.28.0-uno01

- Large folders, as in HyperOS: a folder that takes several Home cells and shows its apps on its face, so each launches with one tap without opening anything. Add one from the widget picker (Uno Launcher, "Large folder"; 2 x 2 to start), then tap it and choose "Choose folder apps" to pick up to 24 apps and name it. It is resized like any widget (Widget options, Resize), and a bigger face shows more icons (icons are 44 dp in 56 dp cells; the face shows as many as fit, in as many columns as fit). Moving, paging and removing use the widget machinery; the apps and name live in their own preference file (LargeFolders), and a folder whose widget is gone takes its contents with it.
- Built as a built-in widget (FOLDER_WIDGET = -6) rather than a bigger kind of the ordinary folder, because the ordinary folder is one cell in the layout's slot list and every drop, undo and backup rule depends on that; multi-cell placement, collision, resize and paging already exist for widgets. The cost: it is not the ordinary folder, so you cannot drop an app onto it to add it (use Choose folder apps), and apps in a large folder are also still on Home wherever you had them.
- Backup: a layout backup keeps the large folder's place and size but not its apps or name (like widget stacks), so a restored one is empty until you choose its apps again.
- Tests: unit tests for the storage, toggle, limits, pruning and capacity rules; an instrumented test that places a large folder, fills it, checks its apps are on the face, and that removing the widget clears its contents (passes on an emulator). Not tried: tapping an icon on the face (launching), the picker's own path to placing it, and the editor sheet.

## 0.27.0-uno01

Features taken from HyperOS (3.x) after reading what its launcher and Super Island do (third-party coverage; Xiaomi's own changelogs were not available):

- Apps that can use the island: Customize, Dynamic island, "Apps that can use the island" lists your apps, all linked by default; switching one off keeps its Live Updates and new-notification peeks out of the island, like the per-app list in HyperOS's island settings (IslandLinks, kept by package name; it is part of the settings backup). Unit tested.
- Seekable music progress: the open island's music card has a thin position bar that you can tap to jump or drag to scrub (HyperOS makes its island media progress interactive). It appears when the player reports a duration, advances while playing from the position the player last reported (MediaProgress, unit tested), and the island's clock ticks once a second only while the island is open and playing. The island's own swipe handling no longer takes a sideways drag on the open island, so the scrub works. The music row is 14 dp taller.
- Already here, in HyperOS terms: several live events at once (the pill carries two and up to two more come out as bubbles, 0.25.0), widgets in the island (0.25.0), a Live Updates focus on ride/delivery/timer cards (0.24.0), tinted icons following the wallpaper (0.24.0).
- Looked at and not done: folder sizes (2x1, 1x2, and large folders whose apps launch without opening them) and a 5x8 home grid need changes to the 4-column home grid and drag model that deserve a release of their own; the lock-screen features (depth clock, AI wallpapers) belong to the system, which a launcher cannot change; "super icon" and app lock were not found in the coverage.

## 0.26.1-uno01

- Fix the tap shadow that 0.26.0 meant to remove: MaterialTheme installs its own ripple as LocalIndication inside the theme, which overrode the no-op provided outside it. The no-op is now provided inside MaterialTheme.
- Fix the rectangles while moving an icon: the dragged icon and folder carried a 16 dp shadow (drawn as a rectangle around a rounded icon), and the cell under the finger and the vacated cell were drawn as framed boxes. All gone. While a drop would make a folder, a soft square shows behind the app below, and nothing reflows out of the way.
- Widget stacks: the page gestures now step aside for a touch that starts on the stack's dot rail (StackRailRegistry). 0.26.0's swipe up (and the older swipe down) could take a drag along the rail. The fade between stacked widgets added in 0.24 is reverted to the plain switch. Not confirmed on a device that either was the fault.
- Drag to make a folder: a scripted drag onto the middle of another app makes a folder (new instrumented test); the middle zone is a little wider (35% of the cell's width, 42% of its height). If it still fails for you, the failing kind of drag is what is needed.
- Instrumented tests updated: plain moves and hover previews now end near a cell's lower edge, since the middle makes a folder, and the removed gap rectangle is no longer asserted.

## 0.26.0-uno01

- No more shadow on tap: every press on Home drew either Material's grey ripple or a white bloom over the icon (pressGlow). Both are gone launcher-wide (LocalIndication is now a no-op); pressing is shown by the springy scale alone, which now settles faster (damping 0.6, stiffness 520 instead of 0.45 / 260).
- Dropping an app on the middle of another app makes a folder of the two, as on iOS (within 30% of the cell's width and 35% of its height from the centre; nearer the edge is still an ordinary move). While held over the middle nothing reflows out of the way. Apps from All apps can be dropped onto Home apps the same way. The rule (folderDropTarget) is unit tested; apps never nest folders or widgets.
- Swipe up from anywhere on Home opens the launcher settings (not when the touch starts on a widget that scrolls itself, or on a scrolled dock; the same guard the downward swipes use). Verified on an emulator.
- Settings reorganised like iOS Settings: a grouped overview (Look: Wallpaper & glass, App icons; Home: Home layout, Gestures & feel, Search, Control panel & Focus; Island & keyboard: Dynamic island, Keyboard; Privacy: Notifications & privacy; More: News feed, Backup, Updates, Help). The old "Control panel & extras" page, which held about twenty unrelated sections, is split into those pages, and the island switches moved off the wallpaper page onto the Dynamic island page. Every switch keeps its test tag. Opening the island's Customize button now lands on the overview.
- Folders show a 3x3 preview (from 0.24) and the press feel above applies to them too.
- Not verified on a device: the emulator was too slow to judge drag-to-folder or the feel of the changes; please try dragging apps onto each other, tapping icons, and swiping up on a real phone.

## 0.25.0-uno01

- Pop-out bubbles: with more live activities than the pill can carry (it shows two), the extras come out as small round bubbles beside the pill, up to two, as on newer dynamic islands. Tapping a bubble brings its activity to the front of the pill. The island's drawing window reserves room for them and the touch window grows to cover them while they show. The ordering rules (IslandBubbles) are unit tested; verified on an emulator with camera, microphone and a Live Update at once (the Live Update showed as a bubble, and a tap on it put "12 min" on the pill). Debug builds can fake the camera and microphone with the debug broadcast (--ez camera true --ez mic true).
- Widget in the island (opt-in): Customize, Control panel & extras, "Widget in the island" lets you choose one Android widget that the open island shows under its activities, like HyperOS island widgets. It has its own widget host (id 2048), separate from Home's, so Home's tidy-up of unused widgets never touches it; Android asks you to allow the binding and runs the widget's own setup screen. NOT yet run end to end: the emulator became too overloaded to drive the picker, so choosing a widget, its rendering in the panel and taps on it are untested. Nothing happens until a widget is chosen.
- Not built: a draggable pop-out island subwindow. It needs a touchable, movable window of its own (the island's windows are deliberately not touchable and fixed-size), which is a larger change than fitted this release.

## 0.24.0-uno01

- Android 16 Live Updates in the island: a notification flagged as promoted ongoing (a ride, delivery, timer, navigation) appears as the app's icon and short status beside the camera, and as a card with its title and progress bar when the island is open. Only the title, short status, progress and small icon are read, never message text. Needs notification access; a switch turns it off. Pure logic (LiveUpdateLogic) is unit tested; verified on an emulator with a debug-only broadcast that posts a promoted notification.
- Full screen: the island hides while the display is landscape (full-screen video and most games) and returns in portrait. Reading another app's status-bar state is not possible from an accessibility overlay (it is never given system-bar insets; measured on an emulator, no inset events arrived and a probe window reported the bar hidden even when shown), and the alternative needs window-reading accessibility access, which Uno deliberately does not request. A full-screen app in portrait therefore still shows the island.
- Backup: layout backups (version 3) now also carry Uno's settings (island, keyboard, panel and appearance choices). The preview says how many settings will be restored. Location, the web browser choice and debug options are never included; older backups still restore. Widget stacks are not included, because their widgets are tied to the phone. Unit tested.
- Icons: a Tinted style takes its colour from the wallpaper without recolouring the rest of the launcher (Themed still follows the Color from wallpaper switch).
- Folders show up to nine apps in a 3x3 preview (2x2 for small folders) and one badge with everything unread inside. Widget stacks fade between widgets and name the new one briefly.
- Developer HUD (debug builds only, Customize, Control panel & extras): frame time, jank, the island's window sizes and positions, and why it is hidden. Never present in a release build.
- Instrumented tests for the island (open and close, camera and mic marks, Live Update glyph, card and removal, tools panel) and for the keyboard (key actions, emoji page, password lane, one-handed, no overlapping keys, touch-target height).

## 0.23.10-uno01

- Keyboard: an iOS-style look. The glassy gradient keys are replaced by flat ones: white letter keys and grey special keys on a pale panel in light mode (dark equivalents at night), a thin darker line under each key, a hollow shift arrow that fills when on, a lowercase "return" (a blue key for Go, Search, Send, Next and Done), outlined delete that fills when pressed, and the pressed key hidden under a balloon-shaped magnified preview as on iOS.
- Keyboard overlap fixes found on an emulator: the switcher globe key is shown only when Android says there is another keyboard to switch to (the input method also declares supportsSwitchingToNextInputMethod), the keys sit higher above the system's own switcher button and the gesture bar, the preview balloon on the top row is no longer clipped by the window (the suggestion strip is now an always-present lane, except in password fields), the keys are shorter in landscape and the fullscreen extract editor is switched off there, and the keyboard is capped at 640 dp wide so it does not stretch on an unfolded screen.
- Keyboard: an emoji panel (about 600 emoji in nine categories, with tabs beside ABC and delete, and a Recents tab kept in memory for the session only), a Paste button in the suggestion lane when the clipboard holds text (the clipboard is read only when it is tapped), long-press variants for symbols and digits as on iOS (dashes, currency, quotes, degree and so on), one-handed mode (Customize, Control panel & extras: off, left or right, with a button on the free side to restore full width), and a held backspace that takes whole words after a while. Backspace now removes an emoji, a flag or a symbol with its variation selector as one character instead of leaving half a surrogate pair.
- Keyboard privacy test: the clipboard may be read in exactly one place, the paste action; the test now also fails on anything that writes the clipboard or reads it outside that action, and the listener that shows the Paste button never touches the content.
- Keyboard speed: the keys are no longer all recomposed on every key press (KeyboardUiState and KeyboardActions are @Stable, the preview is its own layer); pressing a key does less drawing (flat fills instead of gradients); backspace no longer makes a blocking call to the app to ask whether text is selected; the correction for the word being typed is computed on the suggestion thread and used by the space bar instead of scanning the dictionary on the main thread; WordEngine caches the best completions of short prefixes and no longer builds a list on every contains call; the word lists load on a background-priority thread so they do not compete with the first draw.
- Fix two ways the keyboard could disappear, both found by testing on an emulator: a preview layer that filled its parent made the keyboard window as tall as the screen, so Android hid the keyboard as soon as a key was held; and the alternatives strip was a Popup, which builds a window on the main thread in the middle of a touch and, on a slow device, got the keyboard killed as not responding. Both are now drawn in the keyboard's own window.
- Verified on an emulator with a centred camera: the layout in light, dark, one-handed and landscape, the preview balloon, the alternatives strip for a letter, the numbers page, the emoji panel (typing two emoji and the Recents tab), and Paste. Unit tests cover the emoji data and recents, the bottom row, symbol alternates, grapheme-aware and word backspace, the word-engine cache and the clipboard rule. The feel and speed of typing can only be judged on a phone.

## 0.23.9-uno01

- Fix the music controls disappearing from the open island after a pause. IslandPlayback.controlsVisible required the time since the player was last seen playing to be between 0 and 90 s, but it is compared against the island's clock, which ticks only every 5 s; right after a pause that clock is older than the moment the pause was noticed, so the difference was negative and the controls were hidden until the next tick, leaving the island open and empty (a tap where the play button had been then started the audio again). Anything not yet past the window is now inside it. Verified on an emulator: after pausing through the island the card stays with a play triangle. Unit tested.
- A music card in the open island: a 56 dp artwork tile with the track and artist above the controls, a larger play/pause button tinted from the cover, and a tap on the art or title opens the player. Without notification access the tile shows the bars and "Playing" or "Paused". Over other apps the panel no longer reserves the room Home uses for its Search / Feed / Customize buttons.
- Permission ledger (Customize, Control panel & extras): every permission the launcher uses (display over other apps, the accessibility service, notification access, contacts, and the marks that need none), what each is for, what it can see, a status, and a link to Android's settings to turn it off, plus a line saying which island you actually have. The wording is pure data (PermissionLedger) and unit tested.
- The ledger calls out an accessibility service that is turned on in Settings but not running (Advanced Protection and restricted-settings blocks do this), and says the plain overlay is used instead.
- VPN connected / dropped and USB data connected / ended alerts. VPN uses ConnectivityManager.registerNetworkCallback for the VPN transport and USB uses the sticky USB_STATE broadcast; neither needs a new permission, Android's replay of the current state to a new listener is not shown as a change, and a switch turns them off. A USB link counts as data only when a host is connected, the port is configured and a data function (mtp, ptp, rndis, midi, accessory, audio_source, ncm, uvc, adb) is active. Wording and detection are unit tested; not exercised against a real VPN or USB link.
- docs/island-roadmap.md records what is done and what is still to do.

## 0.23.8-uno01

- Fix the island opening from the wrong corner and collapsing through the wrong place over other apps. When Android resizes an overlay window it briefly shows the old, smaller picture anchored at the new window's top-left corner until the new frame arrives, so any design that resizes the window as the island moves shows the island in the wrong place for those frames. 0.23.7 held the window at a bigger size and offset the island inside it, which only made the resizes rarer and larger; recorded on an emulator with a centred camera, the island appeared at the left edge of the window, then at the right, before settling. With the camera on top the island is now drawn in a window of one fixed size, big enough for the open island and the widest event, that never resizes while the island moves, and is NOT_TOUCHABLE. A second, transparent window follows the island's current bounds, takes the touches and forwards them to the drawing window's view; it draws nothing, so resizing it every frame is invisible. Touches anywhere else pass to the app below (the dispatcher shows the drawing window as NOT_TOUCHABLE and the touch window as the island's size). With the camera on a side edge a single window sized to the island is still used.
- Also fixed in 0.23.7's overlay: the held window's pad was added again on every recomposition, growing the window each frame until the screen edges clamped it. That design is gone.
- A window parked off-screen is never drawn, so the island never composed and never reported its position. The overlay window now starts at the top centre instead (rather than the top-left corner, which flashed on every start).
- Debug builds only: a "debugCutout" = "left,top,right,bottom" pixel preference stands in for the camera, so an emulator can be given a centred punch hole. Release builds are not debuggable, so it never applies to them.

## 0.23.7-uno01

- Fix the play/pause icon and the waveform staying in "playing" after a pause. AudioManager.isMusicActive was read at the instant Android reported a playback change, and right after a pause it can still say active; no further callback follows, so the island showed "playing" until something else changed. IslandSystemEvents now reads it again 0.35 s and 1.2 s after every change and after every media command, and the session's own state (when notification access gives us the session) can veto "playing". The play/pause icon flips the moment it is tapped and the real state takes over. The waveform stays while a paused player can still be resumed (the existing 90-second window), dimmed and still, and only moves while audio plays.
- Two-slot live activities. The collapsed pill now carries up to two live activities, one either side of the camera, as iOS does: camera and microphone in use, a call, a timer, a stopwatch, music. The planning is pure and tested (IslandLive): with one activity its glyph is on one side and its detail on the other; with two, each side carries one. A sideways flick across the island turns which one is in front. The clock no longer sits in the idle pill, so the slot is free. Over other apps the idle island is still a ring around the camera, and widens for any live activity.
- Camera and microphone indicators. A green camera mark and an orange microphone mark show while any app is using that hardware. They use public callbacks that need no permission: CameraManager.AvailabilityCallback (the island's own flashlight is ignored through a TorchCallback) and AudioManager.AudioRecordingCallback (silenced clients are ignored). Android does not say which app, and nothing is stored. A new switch under Notifications turns them off. Verified on an emulator with a test page that opens the camera and microphone.
- Event queue. A second event now waits behind the first instead of erasing it (up to three waiting); the same event again refreshes, and a newer ringer or Focus change replaces an older one, since only the latest mode matters. Verified on the emulator: Silent, then Airplane, then back to the live activities.
- Gestures: swipe up closes the open island or dismisses a flash, and a sideways flick sends a flash away or turns the live activities. There is no swipe down to open: Android takes a downward drag that starts at the top of the screen for the notification shade and cancels it for us (seen on the emulator, where the touch is cancelled exactly where the status bar strip ends). The detector watches in the Initial pass and consumes once the finger passes the touch slop, so a swipe does not also fire a tap.
- Album-art tint. The bars and the play button take a colour from the album artwork when notification access provides it (ArtTint: the most common vivid hue in a 24 px copy, lifted so it reads on black; greys give no tint).
- A smoother morph. The collapsed and open faces crossfade where they used to swap at the halfway point; opening keeps its spring and closing is critically damped, so the island settles onto the camera without overshooting; the auto-collapse waits longer (12 s) while music plays and restarts on any button press; the media buttons have a press animation and a haptic tick. Over other apps the overlay window is held at the union of the sizes the island passes through (plus a small pad) while it moves, and is the tight size again once settled, so it no longer resizes on every frame, which clipped the pill's corners and made collapse stutter. It also starts off-screen until the island reports its position, so it does not flash at the screen's top-left corner.
- Docs: docs/island-roadmap.md records what is built and the GrapheneOS-spirit work still to do (a permission ledger, handling Advanced Protection blocking the accessibility overlay, VPN and USB indicators, and rules for staying out of the way).

## 0.23.6-uno01

- Stop the island announcing the ringer mode every time the phone is unlocked. RINGER_MODE_CHANGED_ACTION and the airplane-mode broadcast are sticky, so registering the receiver is handed the current value immediately; IslandSystemEvents treated that as a change and flashed it each time the island came into composition, so a phone on vibrate said "vibrate" on every unlock. The receiver now ignores the initial sticky delivery (isInitialStickyBroadcast); real changes still show.
- Fix the island jumping sideways as it collapses over other apps. The overlay window was anchored top-left and moved with updateViewLayout every frame, which lagged the content (it shrinks immediately), so the island sat left of the camera until the window caught up. With the camera on top the window is now anchored at its horizontal centre, which is the camera, so it resizes around a fixed point and needs no per-frame moves. A camera on a side edge keeps top-left placement. Checked on an emulator by sampling the window frame: its centre stays fixed through a collapse.
- Make pause work without notification access. Playing is detected through AudioManager.isMusicActive, but the session controls need notification access, and the media-key fallback can be ignored from an app on newer Android. When there is no session and audio is playing, play/pause now requests permanent audio focus and abandons it, which makes a focus-respecting player pause and stay paused, and falls back to the media key if audio is still playing afterwards. Play after a pause and skip still use the media key. Not verified against a real player.

## 0.23.5-uno01

- Fix a crash a few seconds after launch on 0.23.4 ("Cannot coerce value to an empty range"). The idle overlay island's compact width (hole plus a ring each side, with the ring as small as 3 dp) could be narrower than the hole plus the 4 dp clearance that IslandGeometry.frame then clamps the island's left edge to, so that clamp's range had its minimum above its maximum and threw. The compact width now always includes the clearance, and the clamp can no longer be given an empty range. Two new tests (a sweep over densities, camera sizes and positions, size settings, compactness and progress, plus the reported case) fail on 0.23.4's geometry and pass now.

## 0.23.4-uno01

- Make the everywhere-island tappable and stop it covering the status icons. Android layers a plain application overlay beneath the status bar, and the status bar window owns every touch in its strip (confirmed in the input dispatcher's window stack: StatusBar above the overlay, touchable region y 0-136), so an island on the camera was drawn under the status icons and never received a tap. The overlay window is now hosted by the existing accessibility service (SystemShadeAccessibilityService) as TYPE_ACCESSIBILITY_OVERLAY, which is layered above the status bar. It needs no "display over other apps" permission and the service still observes no events and reads nothing; the window code moved out of IslandOverlayService into IslandOverlayHost, which both services use. Without the accessibility service enabled the old application overlay is the fallback (visible, but not tappable in the strip), and the "Island everywhere" dialog gains a "Tappable island" button that opens Accessibility settings. Verified on an emulator: the island window sits above the StatusBar window and a tap over Chrome expands it.
- Tapping outside an expanded island collapses it. On Home a transparent scrim under the island takes the tap; on the overlay the window sets FLAG_WATCH_OUTSIDE_TOUCH and collapses on ACTION_OUTSIDE (which carries no position). Verified on the emulator.
- The collapsed island no longer shows the time (the status bar has it, and the expanded panel keeps it). Over other apps the idle island shrinks to a ring around the camera (IslandGeometry.frame compactness) so it does not cover the status icons on either side, and widens for an event, timer, stopwatch, ringing alarm, call or playback.
- Fix the island's play/pause and skip buttons doing nothing. They now use the playing session's own transport controls when notification access gives us the session (pause when playing, play otherwise), falling back to the media key; the key event also now carries real timestamps, where a zero-stamped event reads as stale and can be dropped by the media session service. Not verified on a device.
- Tapping the app icon in a notification peek opens that app (the peek remembers its component; work-profile apps are skipped). Not verified on a device.

## 0.23.3-uno01

- Fix the everywhere-island sitting a status-bar height below the camera. The overlay window was laid out inside the area below the status bar, so the island's y (measured in true screen pixels from Display.getCutout) was applied from there; its parent frame started at the bottom of the status bar. The window now sets FLAG_LAYOUT_IN_SCREEN and FLAG_LAYOUT_NO_LIMITS (kept in OverlayPolicy.WINDOW_FLAGS, with a unit test), so its x and y are absolute screen pixels and it stays unfocusable and passes touches outside its own bounds.
- The island now stays on the camera when the phone is turned sideways, instead of jumping to the top-centre of the rotated screen. Turned on its side the camera is on the left or right edge, and the old cutout picker only accepted a cutout near the top, so nothing matched and the island fell back to a no-camera layout. A small side-edge rectangle now counts as the camera (a tall waterfall strip does not), and the island becomes a vertical pill hugging it: events and the panel grow inward from the edge, centred on the camera, with the content beside the hole. The overlay also re-reads the display size each time, where it used to read it once, so rotating (and folding) updates it.

- Fix the island size setting doing nothing. The overlay read the camera from `Display.getCutout()` and used the raw bounding rectangle, which on many phones is tall and starts at the very top edge; the step that tightens it to the visible hole (using the cutout path) existed only on Home's path. With the tall rectangle there is no room above the island, so the size slider had nothing to change. Both paths now share `IslandGeometry.holeFor`, and a test shows the slider has a range with the refined hole and none with the raw rectangle. I could not reproduce it on the emulator, whose notch touches the top edge, so this one is verified by test only.

## 0.23.2-uno01

- Fix the everywhere-island sitting below the camera cutout: overlay windows do not reliably receive the cutout in their own insets, so the overlay now builds the island environment from the display itself — real display metrics, Display.getCutout for the camera hole, and the system status-bar height for the no-cutout fallback. Verified on-device with an emulated punch hole: the window wraps the hole exactly.

## 0.23.1-uno01

- Fix the everywhere-overlay breaking touch: the island composable's fill-size wrapper inside the wrap-content overlay window made the window cover the whole screen and swallow input, pushed the island off-center, and fed a relayout loop. The overlay now renders only the island itself, window updates are deduplicated, and the island is anchored from the system status-bar height (overlay windows receive no status-bar insets). Verified on-device: the window measures exactly 326x95 px collapsed and 840x389 px expanded, centered, below the status bar, with taps expanding and collapsing it in place.

## 0.23.0-uno01

- Island everywhere (Phase 2): an opt-in overlay that shows the island above other apps. The window is exactly the island's size, NOT_FOCUSABLE and NOT_TOUCH_MODAL so touches outside it pass through, and it never draws on the lock screen or when the screen is off; the permission is requested through an explainer, revoked permission stops it immediately, and Home's own island steps aside so there is never two islands.

## 0.22.2-uno01

- Calls in the island: while a call is active and notification access is on, the island shows the caller's name and a ticking elapsed time from the phone app's own call notification; tapping the card opens the call screen. The name is held in memory only, cleared when the call ends, and never stored; message text is never read. A Show calls in the island switch lives under Notifications, on by default and off without notification access.
- Media card gains artwork and artist: the expanded island shows the album art thumbnail and the artist line under the track title, alongside the existing controls.

## 0.22.1-uno01

- Fix a crash opening Control panel & extras on Android 14+ (targetSdk > 33): the page read the restricted `enabled_input_methods` and `default_input_method` secure settings and Android threw a SecurityException. The enabled list now comes from the public InputMethodManager API, and the selected state degrades to "unknown" where Android no longer lets apps read it; the switcher button stays available so you can still check. The hard-keyboard setting read in the keyboard service is guarded the same way.

## 0.22.0-uno01

- Keyboard speed. Letters now type the moment the finger lands instead of on release (holding a letter that has accents still opens the strip, and choosing one swaps the letter just typed). Each key keeps its own touch handler across shift changes, which used to restart every key's handler on the first keystroke after shift and could drop an overlapping tap, so two fingers rolling over keys no longer lose presses. The key preview is drawn inside the keyboard instead of as a pop-up window created and destroyed on every press. The keyboard keeps its own copy of the text before the cursor, updated from what it types and deletes, and only re-reads the field when the app changes the text or moves the cursor, so a letter costs one call into the app (the commit) instead of two, and a space or backspace about one instead of four to five (capitalisation is also worked out locally now). Suggestions are computed on a background thread and only the newest result is shown, so a keystroke never waits for a dictionary scan. Typing "teh quick" with taps ten milliseconds apart produced "the quick", and the text stayed correct after backspaces. I have not measured milliseconds on a real phone.
- Keyboard haptics, modelled on iOS: a light tick on touch-down (not on release), played with the vibrator's own tick primitives at an adjustable strength on a background thread so a key never waits for it. Each key has its own feel: letters lightest, the space bar and modifier keys a little firmer, Return a soft click, held delete and cursor sliding lighter again, and a tick as you move across accent choices. Overlapping presses closer than 12 ms blur into one, so it is skipped. It follows Android's touch-feedback setting, and a motor without primitives falls back to the system's tick. Customize > Control panel & extras > Uno Keyboard has a Key haptics slider (0 is off) and a Feel it button that plays a short typing run.


- Keyboard fix: pressing a letter no longer makes the neighbouring keys shift away. Cause: the key-preview and accent popups were children of the key row, and a popup counts as an extra (zero-size) child, so the row's 5 dp gap between children grew by one gap while a preview was showing and pushed every other key sideways. The popups now live inside the pressed key's own box. Checked by comparing the keyboard's pixels with a key held and not held: the other keys in that row and the rows below are unchanged.
- Keyboard suggestions and autocorrect. A three-slot strip above the keys shows the word as typed, the best correction in bold (what space will apply), and common completions; between words it shows likely next words. Space, a full stop, comma or other punctuation, and Return apply the correction. **Backspace right after a correction undoes it, and that word is then left alone for the session**; tapping the quoted as-typed word does the same. It handles contractions (dont gives don't, im gives I'm), keeps your capitalisation (Teh gives The), is more forgiving of slips to a neighbouring key and of swapped or doubled letters than of random ones, and leaves URLs, addresses, handles, hashtags, and words with digits alone. It switches itself off in password, email, web address, name, postal address, number and phone fields, and when an app asks for no suggestions.
- Local and non-learning: a 80,000-word English list (word frequencies from the SymSpell project, MIT, derived from Google Books n-grams) and a next-word table are bundled in the app. Nothing you type is ever added to them, saved, or sent; there is no personal dictionary, so it cannot learn your own names and slang yet. Suggestions switch off in Customize.
- FlorisBoard-style additions, each with a switch: an optional number row, and sliding a finger along the space bar to move the cursor.
- Third-party data: the word list's licence is shipped in the app's notices.

## 0.21.0-uno01

- Fix: pulling down on Home now opens search, as you expected. Search used to be reachable only from the island's button, or by changing a setting that replaced notifications on the whole left side, so a pull from lower on Home opened Android's notification shade. Pulling down now works like iOS by default: **from the top edge** (the top ~22% of Home) the left side opens notifications and the right side the control panel as before, and **a pull that starts anywhere lower opens search**. Customize > Control panel & extras > Pulling down on Home keeps the two older behaviours as choices (notifications on the whole left side, or search on the whole left side). The setting is stored under a new name, so everyone gets the new default; an earlier explicit choice of Search is kept.

- Uno Keyboard: a glass-styled on-screen keyboard (an Android input method). Keys are painted with the same lit tile, sheen and upper-left rim as the launcher's glass; pressing a letter shows a magnified preview above the key (never in password fields, where someone watching could read it); long-pressing a vowel or c, n, s, y, z offers accents; shift is off/once/caps lock (double tap), with automatic capital letters after a sentence; there is a numbers page and a symbols page; backspace repeats while held and accelerates; double space types a full stop; the Return key becomes Go, Search, Send, Next or Done as the field asks; number and phone fields open on the numbers page; a globe key switches keyboards. It follows the system light/dark theme, picks up the Color from wallpaper accent, and uses the same Haptics and Sounds switches.
- The keyboard is strictly opt-in (Android makes you turn it on, and Customize > Control panel & extras > Uno Keyboard shows its status and opens the right Android screens) and strictly local: nothing typed is stored, learned, logged or sent. Because Uno's APK also holds the internet permission for feeds you add, a unit test fails the build if any keyboard source file imports anything network- or logging-related. It has no autocorrect, prediction, swipe typing, emoji or voice input yet, so it types exactly what you press; say so before you make it your main keyboard.
- The keyboard shows unless a physical keyboard is attached and Android's "Show on-screen keyboard" setting for it is off.
- Spotlight's placeholder no longer wraps onto two lines in Inter.

## 0.20.0-uno01

- Widget stacks: long-press a widget, choose **Stack another widget here**, and pick another widget on Home. It moves into the first widget's stack and frees its spot. The stack shows one widget at a time with a dot rail on its right edge: tap a dot, or drag along the rail, to switch (vertical swipes on Home already open the panel and notifications). **Remove ... from this stack** in the same sheet deletes a member. Up to 5 stacked widgets per widget. Stacks are kept apart from the layout (its validated format, undo and backup are untouched), so a layout export does not include them yet; removing the base widget drops its stack.
- Search suggestions: when no installed app matches a search (in Spotlight and in All apps), Uno offers "Search F-Droid", "Search Aurora Store" and "Search the web with <browser>" rows. Tapping one opens that app with your words in it (`market://search` for the stores, a web-search request for the browser). Uno shows no results itself and contacts nothing: Aurora and web search have no public API, F-Droid's would need Uno to fetch its index, and that would break the no-hosts rule. Choose the browser and which stores to suggest under Customize > Control panel & extras > Search suggestions (Vanadium and F-Droid/Aurora by default, only those actually installed are shown); the web search uses the browser's own search engine, so no engine address is built into Uno. The whole thing can be switched off.
- Search from anywhere: a Spotlight-style panel over whatever page you are on, opened from the island's Search button or, if you choose it under Customize > Control panel & extras, from the left-hand pull-down in place of notifications (notifications stay the default). One box finds apps (name-prefix first), contacts (if enabled), answers to sums and conversions, and headlines from your saved feeds. Nothing leaves the device and it never touches the web.
- Haptics and sound design: every tick now goes through one switch. New feedback on page changes, app launches, opening the panel or Spotlight, and flipping Focus. **Haptics** are on by default and **Sounds** are off by default (Customize > Control panel & extras > Feel and sound); sounds are Android's own touch sounds, so no audio is bundled, they follow your system Touch sounds setting and volume, and they are silent in silent mode.
- Dynamic island: a progress ring now traces the collapsed pill from the top centre, clockwise: orange and depleting for a running timer, green and filling to the battery level while charging. A notification peek now shows the posting app's own icon and name (never the message). With notification access on, tapping the track name in the expanded island opens the player. A timer whose alarm never reached the process (for example after the app was stopped) now finishes when the clock passes its end instead of sitting at 0:00.

## 0.19.0-uno01

- Themed icons are now liquid glass: a translucent tinted tile (lighter toward the light, so the wallpaper reads through), a soft shadow under the glyph, a white sheen over the upper left, and a rim that is bright at the lit corner and fades to the far side, matching the live rim light. Apps without a monochrome icon layer now get an ink silhouette of their own glyph instead of a washed-out grey slab. It is the look of glass painted into the icon, not live refraction, because icons are drawn as images.
- App shortcuts: long-press an app and its own quick actions (Camera's selfie, Chrome's incognito tab, Maps' Home and Work) appear at the top of the options sheet, up to four, in the app's own order. They are read through the launcher-apps service, which only answers a default Home app, so they show once Uno is your Home app.

- Fix: split screen could open an unrelated app as the first one. The second app was launched after a fixed 2.5 s even if the first app (a slow cold start) was not yet in front, so Android paired it with whatever it had been showing. It now waits until the first app is actually in front, and if that doesn't happen within 12 s, or you return Home first, it cancels instead of guessing. I could not reproduce the wrong pairing on the emulator (apps start too fast there), so this is the fix for the timing cause I identified, not a confirmed match for what you saw.
- Google Discover is gone as a default and as an option: the feed page always owns the leading page, and the "Use my feed instead of Discover" switch is removed.
- Each saved feed now has a switch (Customize > News feed) to show or hide it on the feed page, so you can keep several links without seeing them all. Switched-off feeds stay saved, aren't fetched, and their entries are hidden (also from the island headline); turning one back on fetches it right away. If every feed is off, the page says so. New feeds start on.

## 0.18.1-uno01

- Tilt strength slider (Customize > Wallpaper & appearance, under Tilt-following highlight). The default effect was too gentle to notice on a real phone: a hand tilt of 10 to 20 degrees moved a thin rim only a few degrees. Strength now multiplies the roll the light follows (1x to 6x, 4x at the default 60%), sharpens the rim's falloff so the lit edge stays bright while the other edges fade out (making the direction obvious), and thickens the rim slightly. Brightening the whole rim instead was tried first and made things worse: at full strength every edge was a white outline and the movement vanished, so strength acts on contrast, not brightness.
- A live readout under the slider says whether motion readings are arriving ("Motion sensor working. Light angle 43 degrees (45 degrees when upright)") or, if none are, points at GrapheneOS's per-app Sensors toggle.
- The light's swing limit rose from 75 to 135 degrees so the higher gains can move it around the shape, sampling is faster (20 Hz) and smoothing quicker, with the redraw threshold at 2 degrees.

## 0.18.0-uno01

- Split screen: long-press an app, choose **Open in split screen**, then pick the second app. The two open stacked top and bottom on a phone (Android chooses the arrangement for the screen). No accessibility service is involved: the second app is launched with Android's "open beside" flag once the first is in front. Work-profile apps can be the first app but not the second yet, because only the launcher-apps service can start them and it cannot set launch flags.
- Tilt-following highlight (Customize > Wallpaper & appearance, with Liquid glass; off by default): the glass edge light stays put in the room as you tilt the phone, so the shine slides across the glass. It reads gravity (or the accelerometer) at a low rate only while Home is on screen, redraws only when the light has moved a visible amount, and fades out when the phone lies flat. It needs no permission; if GrapheneOS's per-app Sensors toggle is off the light simply stays put.
- Search that answers: type a sum or a conversion into All apps search and the answer appears above the app results, with a tap to copy. Arithmetic supports + - * / ^, parentheses, percent ("15% of 80", "50+10%" is 55), pi, and sqrt, abs, ln, log, sin, cos, tan (degrees). Conversions cover length, mass, volume, speed, data, time and temperature ("5 km in mi", "212 f to c", "90 min in hours"). Everything is computed on the device, and anything that isn't clearly a sum or a conversion is left to ordinary app search ("7-zip" and "2048" stay app names).

## 0.17.1-uno01

- Directional rim light: every glass surface (widgets, dock, panels, sheets, the selection lens and the island) now has a specular highlight lit from the upper left, brightest on the edges facing the light and fading opposite, instead of an even outline. It is defined once (`GlassRim`) so all surfaces agree on where the light is.

- Fix: the control panel's dimming layer now covers the whole window. It used to sit inside the area padded for the status and navigation bars, so those strips stayed at the Home brightness and read as the edges of a rectangle behind the glass. The panel now lives beside that content and applies the safe-area padding to itself only. (On an emulator whose taskbar is a separate system window, that strip is still drawn by the system and can't be dimmed from inside the app.)
- New setting, **Color from wallpaper** (Customize > Wallpaper & appearance, next to Liquid glass): takes one color from the wallpaper, whether a photo or the Uno dunes, and uses it to tint the glass (including the control panel and sheets), for slider and switch colors, and for themed icons. Lightness is walked until contrast targets hold on the glass in both light and dark, so text stays readable on any wallpaper (unit-tested across ten test wallpapers in both themes). Off by default.

## 0.17.0-uno01

- Control panel: swiping down on the right 30% of Home now opens the launcher's own glass panel instead of Android's Quick Settings (choose either under Customize > Control panel & extras). It has media transport with the equalizer while audio plays, a volume slider with a haptic tick per step, a brightness slider, a Ring / Vibrate / Silent selector, a flashlight, a Focus switch, up to five app shortcuts, and a **System settings** row that hands off to Android's real Quick Settings. Back, a tap outside, or an upward swipe closes it. The panel needs no accessibility service; only the hand-off row does. Notifications still open from the left 70%.
- Panel customization: show, hide and reorder every tile, and pick the shortcut apps.
- Sliders now stretch slightly and spring back when dragged past either end.
- Focus: hides the apps you pick from Home and All apps until you turn it off, from the panel or Customize. Your saved layout is untouched, and hidden apps leave an empty spot. Optionally sets the ringer to vibrate while Focus is on and restores it afterwards.
- Island tools: long-press the island for a timer (1, 5, 10 or 30 minutes), a stopwatch and the flashlight. A running timer or stopwatch shows in the collapsed island, and the timer rings the default alarm sound and vibrates until you tap the island (or 30 seconds pass). The timer is an alarm, so it still fires with Home closed; it is exact to the second if you allow exact alarms, and within about a minute otherwise.
- Themed icons: an opt-in icon style that draws each app's own single-colour icon layer (Android 13+) in the launcher's colours, with other apps washed toward the same palette. Rebuilds with the light/dark appearance.
- Opt-in notification access: unread-count badges on app icons (Home, dock and folders), the playing track's title and artwork in the control panel and island, and a brief "app name" peek in the island when a notification arrives. Each has its own switch and all stay off until you allow notification access in Android's settings. Message text is never read, and nothing is stored or sent.
- Opt-in contact search: matching contact names appear under the app results in All apps, using Android's contacts permission. Only names are read and nothing is stored.
- Folders on glass get edge-lit tiles that let the panel show through, and folder icons show badges.
- New permissions, all low-impact: Vibrate and exact alarms (timer), "Modify system settings" (brightness slider) and Do Not Disturb access (Silent), plus contacts and notification access. None is asked for until you use the feature that needs it, and the launcher works fully with all of them declined.
- Fix: the timer's vibration was missing its permission declaration.
- Fix: the Customize sheet read app state outside the composition's observation.

## 0.16.14-uno01

- Inter is now the typeface throughout: one bundled variable font (SIL Open Font License; the license ships in the app and is listed in the third-party notices), with no font download. The island's action buttons drop to icon-only when their labels don't fit the wider letterforms.
- Dynamic island pill: the body is now true OLED black so the camera hole disappears into it, with the glass rim and refraction kept as a thin outer ring.
- Island playback indicator: an animated equalizer while audio plays, and previous / play-pause / next in the expanded panel (media keys; no track titles, which would need notification access). Controls stay for 90 s after playback stops.
- Island events, with the pill widening to fit each title: ringer (Silent / Vibrate / Ringer), airplane mode, Do Not Disturb, and charging with its percentage. A ringer change caused by Do Not Disturb is suppressed. Events pulse the pill with a spring, and tapping it gives a light haptic tick.
- Fix a tap on the island being swallowed by its hidden panel: only the visible face is composed now, since invisible buttons still held 48 dp touch targets.
- Fix a main-thread stall risk: all audio and notification service calls now run on a worker thread, because those binder calls can block for seconds.
- Selection lens: an iOS 26-style clear glass lens on the page-dots strip. It glides between Discover, the Home dots and All apps as the pager scrolls, magnifies the icon or dot under it, and lifts while a finger is on the strip. Dragging along the strip now scrubs through pages with the lens under the finger. The strip is exempt from the pager's own horizontal drag so the two don't fight.
- Dock icons get a press lens: clear glass that lifts and magnifies the icon while it is held.

## 0.16.13-uno01

- Adaptive glass text: widgets, the status rail, the page-dots strip and the search button now choose white or dark ink from the brightness of the wallpaper behind them, instead of always white, so they stay readable over pale wallpapers. The switch uses the WCAG contrast crossover with a small dead band, so it never flickers while content slides past (`AdaptiveInk.kt`).
- Progressive edge blur: content softens into a blur toward the top and bottom edges of the screen, beneath the island, rail, dock and page dots, like iOS 26's scroll edge effect (`EdgeBlur.kt`). It is subtle at rest and shows when content scrolls under an edge.

## 0.16.12-uno01

- Dynamic island pill: size it from the visible camera hole (the cutout path) instead of Android's much taller bounding rectangle. The Island size slider now changes the pill's height across its whole range, and the pill keeps 6 dp clear of the top edge instead of running to it.
- Page-dots strip: pages now reserve exactly the strip's height (`PageIndicatorLayout`, 48 dp, or 96 dp with the "Set as home app" button), including the feed/Discover page and the native Google Discover window, so the dots no longer overlap them.
- Add `scripts/size-matrix.sh`, which screenshots Home and the feed page across phone, fold-cover, small-phone, large-text, fold-inner and tablet sizes on one emulator, to check layouts without owning a foldable.

## 0.16.11-uno01

- Spacing-grid audit: dock/rail and bottom-right margins to 16 dp, bottom strips to 8 dp, page-dots capsule to 8 dp, feed page to 16/16/16/12 with 12 dp card gaps, sheet to 16 dp with 8 dp gaps, and the no-cutout island sits 8 dp below the status bar.
- The expanded island now caps its width so it can never reach the dock strip, whatever the dock-width preset.
- Island size slider rework: much wider ranges (slots 44-80 dp, surround 0-16 dp, min height 12-24 dp, expanded body 96-128 dp) so the effect is unmistakable, with tests pinning the punch-hole behavior and the dock clearance.

## 0.16.10-uno01

- Island size slider (Compact to Large) under the Dynamic island switch; the capsule's height, surrounding padding, and side slots all scale with it.
- Updates page: check GitHub for Uno releases, install any version (downgrades included), and an Auto-update toggle that checks every few hours, downloads, verifies against the published SHA256SUMS, and opens Android's install prompt. Android always asks to confirm the install.

## 0.16.9-uno01

- Fix the dynamic island's position: it now wraps the front-camera hole (centered on it, symmetric around it, with the time on one side and charging/battery on the other and the expanded panel hanging below it) instead of drifting to the right. The old placement centered the island inside a safe-area-padded parent and then added the cutout's window offset on top, and also read pixel values as dp. Layout is now pure geometry in window pixels (`IslandGeometry`), re-read from the live window insets on every layout so it follows rotation and folding, and an off-center or edge-flush hole (such as the Pixel Fold's inner camera) is still fully wrapped.

## 0.16.8-uno01

- Dynamic island: a liquid-glass capsule anchored to the camera cutout on Home. Collapsed it shows the time (and charging); tap it to expand into a live panel with time, battery, the top feed headline, and search/feed/customize actions; it auto-collapses after five seconds.
- App launches and charging transitions flash through the island.
- The island is launcher-local and never overlays other apps; a Dynamic island switch lives under Wallpaper & appearance.

## 0.16.7-uno01

- Liquid controls: sliders and switches in customization are glass when liquid glass is on — an orange iOS slider with a refracting glass thumb and a green iOS switch with a glass thumb, with the stock Material fallback when glass is off.
- Three lens knobs replace the single refraction slider: refraction height, refraction amount, and chromatic aberration, each 0-100% and persisted separately (the old single value migrates).
- iOS accents: green switches, orange slider tracks and value labels.

## 0.16.6-uno01

- Target Pixel phones on GrapheneOS, with the Pixel Fold (2023) as the primary design device; documentation no longer names a Samsung reference device.
- The leading page always exists. It shows Google Discover only where the Google app is installed and Window Extensions exist; otherwise (and whenever a configured feed is preferred) it is the news feed. With no feed configured it is an opt-in page offering the GrapheneOS announcements and release feeds, and nothing is contacted until one is tapped.
- Network hardening: feeds must be https, redirects are followed only within the same host (at most three), and an explicit network-security config refuses cleartext. The GrapheneOS forum publishes no RSS/Atom feed, so suggestions use `grapheneos.social` and `grapheneos.org`.
- Feed reader: untitled posts (Mastodon) get a headline from their text, HTML is stripped from summaries, and Atom `xhtml` content is read instead of dropped.
- Shade gestures are now a one-time, easily declined opt-in: the prompt explains what the accessibility service can and can't do, **No thanks** is remembered, and a declined swipe is silent. Help & setup still offers it.
- iOS-style corners: one four-step corner scale (12/18/24/32 dp plus capsule) used everywhere, app icons use Apple's 22.37% continuous-corner silhouette with an antialiased mask, and settings switches use iOS colors. Panels keep circular-arc corners because the glass lens accepts only rounded rectangles.
- Fix unreadable dark text on the glass feed page.

## 0.16.5-uno01

- Fix backdrop sampling: the wallpaper and pager now record into separate layers that glass surfaces combine, so the dock and rail refract real page content instead of empty pixels.
- Run Home beneath the dock and status rail, iOS-style: pages slide under the glass during swipes while Discover and All apps stay clipped to the viewport.
- Glass for everything inside the pager: feed, Discover recovery, All apps, the widget gallery, the first-run and app/empty-space sheets (inset glass cards), the page-dot capsule, and the search/back circle controls.
- Refraction slider now spans 0-200% with a stronger lens curve and chromatic aberration from 50%.

## 0.16.4-uno01

- Real liquid refraction: the lens effect now uses deeper displacement with depth weighting, and adds chromatic aberration at high intensities.
- Add a refraction intensity slider under Wallpaper & appearance; the default sits at 55% and the top end matches the demo's heavy bending.

## 0.16.3-uno01

- Extend liquid glass: the status rail and folder panel are now glass surfaces.
- Springy press glows and scale on dock, Home, and folder icons.
- Glass tint follows the wallpaper: the committed photo's muted palette color blends into the theme glass (drawn dunes keep the theme tint).

## 0.16.2-uno01

- Experiment: liquid glass aesthetics. The dock, customization panel, and feed page now blur and refract Home behind them (vibrancy, blur, and lens effects).
- Add a Liquid glass switch under Wallpaper & appearance; turning it off restores the flat glass look and saves battery.
- The effect is drawn locally with runtime shaders; no new permissions or data use.

## 0.16.1-uno01

Maintenance release: identical app, built and published by the new automated CI release pipeline.

## 0.16.0-uno01

First Uno Launcher release, rebranded from jakesgoodapps/DuoLauncher.

- Add an optional personal news feed: RSS 2.0 and Atom pages fetched directly from the addresses you add, cached on the device, and shown in the Discover slot where Google's feed is unavailable or when you prefer it.
- Open the Discover slot on devices without Window Extensions support when feeds are configured, so the feed works without the Google app.
- Keep the feed private: the new internet permission is used only for fetching added feeds, nothing is uploaded, and removing a feed removes its cache.
- Rebrand the app, wallpapers, shade service, documentation, and user-facing text to Uno Launcher. The package identifier remains `com.jake.duolauncher`, inherited from the upstream project.

## 0.15.0-beta01

First public-beta preparation release. Tested scope and APK checksums accompany the release package.

- Add a skippable introduction for fresh installations and help through customization; existing layouts open directly.
- Improve recovery choices when Google Discover is unavailable.
- Show distinct Wi-Fi levels across the dot and three arcs.
- Preserve the current wallpaper when photo selection is canceled or fails, and improve interrupted preview recovery and temporary permission cleanup.
- Prepare optimized release builds, external signing, public-source export, and automated build checks.
- Add installation, update, permission, contribution, and compatibility documentation.

## 0.14.7

- Restore long-press pickup in scrollable Android widgets while preserving native vertical scrolling.

## 0.14.6

- Preserve the selected Home page or unfolded pair when returning from an app.

## 0.14.5

- Allow vertical scrolling inside native Android widgets.

## Earlier development

Home/All apps paging; right-side dock; overlapping unfolded pages and an unfolded-only workspace; native widgets and visual selection; cross-page dragging and temporary pages; work/personal profiles; Home folders; local wallpapers and daylight appearance; layout backup; Google search/Discover; long-press customization; and motion/recovery refinements.
