# Changelog

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
