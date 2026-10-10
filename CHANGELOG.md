# Changelog

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
