# Device checklist

Things that have only been run on an emulator, or with posted test data, and need a real phone. Do them on the phone you use every day, in the order that suits you; each says what to do, what should happen, and what to tell us if it does not. Put `OK`, `Not OK: <what you saw>` or `Skipped` next to each, and send the list back. Screenshots help; blank out anything private.

Build under test: the latest release (see Launcher settings, Updates).

## A. Things you reported earlier

- [ ] **Island search.** With Island everywhere on, open another app, tap the island. Tap Search. *Expect:* Uno opens its search with the keyboard up. Type a word no app matches and press Enter. *Expect:* your browser opens with that search.
- [ ] **Device location.** Launcher settings, Wallpaper & glass, Appearance, Sunrise / sunset, Use device location. Stand by a window or outdoors. *Expect:* "Waiting for device location (GPS)…" with a Cancel button, then within about 90 s "Saved: Device location (rounded), …" with today's sunrise and sunset. If it fails, note whether you allowed precise location and what the message said.
- [ ] **Place by hand.** Type a place name, then paste `40.7128, -74.0060` into the Latitude field and tap Use this place. *Expect:* the two fields fill in and a line appears: "Saved: … Sunrise … sunset … today".

## B. The island

- [ ] **Notification cards.** Launcher settings, Dynamic island: turn on "Show new notifications in the island" at each of its three levels. Have someone message you. *Expect:* the card shows the app name, then the title, then the text. A blocked word hides the text.
- [ ] **Downloads and navigation.** Start a download in a browser; start a route in Google Maps. *Expect:* progress or the next turn appears in the island.
- [ ] **Live Updates.** An app that posts a promoted notification (a ride, a delivery, a timer in some clock apps). *Expect:* it shows in the island.
- [ ] **Music card.** Play something in your usual player, expand the island, drag the seek bar, and pause. *Expect:* the position moves, play and pause work, the island glows in the cover's colour, and it goes away about two minutes after you stop.
- [ ] **Widget in the island.** Settings, Dynamic island, Widget in the island, pick one. *Expect:* it shows in the expanded island and a tap does what it does at home. (This has never been run end to end.)
- [ ] **Audio alerts and recording mark.** Plug in or connect headphones; start a screen recording. *Expect:* an alert for the headphones and a recording mark in the island.
- [ ] **Full-screen apps.** Open a full-screen video in landscape. *Expect:* the island hides. In portrait it will still show; that is a known limit.
- [ ] **Island themes.** Island style, pick Neon, then Reset. Export your theme, then Import it back with Android's file picker. *Expect:* the preview appears before it applies. Also try Neon while the island is over another app.

## C. Widgets and Home

- [ ] **Widget stacks.** Make a stack of two widgets (Android or Uno, or one of each), swipe between them, restart the launcher. *Expect:* the stack and its position survive. (You reported stacks not working; two fixes have not been confirmed.)
- [ ] **Photos widget.** Choose photos with the picker; add an album (allow Photos & media when asked). *Expect:* pictures show and change; the Settings menu is glass.
- [ ] **World clock and other glass menus.** Open the menus of the Note, Counter, Countdown and World clock widgets. *Expect:* they match the rest of Home and the keyboard does not cover the field.
- [ ] **Edit Home.** Long-press empty space, Edit Home. *Expect:* icons wiggle, drag starts at once, free cells show, Done leaves. Tell us where you still cannot place an icon.

## C2. Icon packs and screen reader

- [ ] **Icon pack.** Install an icon pack (any that says it works with Nova or Lawnchair), then Launcher settings, Icons, Icon pack, pick it. *Expect:* the apps it covers change icon; others keep theirs.
- [ ] **TalkBack.** Turn on TalkBack and swipe through Home and one settings page. *Expect:* every control is announced with a name; switches say their name and on/off together; free Home spaces say "Empty Home space".
- [ ] **Back gesture.** Swipe back from a settings page. *Expect:* the predictive-back peek, then the previous page.

## D. Wallpapers

- [ ] **Each built-in wallpaper, day and night.** Wallpaper & glass, pick each of the six. *Expect:* it appears within a couple of seconds; tell us if any takes long. At night the very top should be pure black around the camera, with the island emerging from it.
- [ ] **Also set home and lock screen.** Turn it on. *Expect:* your Android home and lock screens change to the same picture, cropped sensibly on the cover and inner screens of the Fold.

## E. Backup and updates

- [ ] **Backup and restore.** Backup, export; change a few settings and the island theme; restore. *Expect:* your layout, settings, widget data and island theme come back.
- [ ] **Updater.** When a newer release is out, Launcher settings, Updates. *Expect:* it finds the release, downloads it, checks it, and offers to install. (The full path has never been run.)

## F. Look, feel and speed

- [ ] **Molten metal wallpaper.** Pick it in Wallpaper & glass, in light and in dark. *Expect:* flowing contour lines over black, ice blue by day and ember by night, with crisp hairlines and a faint colour fringe; icons and their labels stay readable everywhere; the top is dark around the camera. Tell us if any region behind icons is too busy or too bright.
- [ ] **What's new.** After updating from the previous version. *Expect:* a short note appears once; Got it closes it and it does not return.
- [ ] **First-run tour.** (Needs a fresh install or cleared data.) *Expect:* the welcome sheet has three extra tips.
- [ ] **Island style sharing.** Copy this theme as text, paste it into a message to yourself, copy that, then Paste a theme from text. *Expect:* a preview appears and nothing changes until Apply.
- [ ] **Speed and battery.** Cold-start Uno (swipe it away, press Home) and judge the delay; leave the Liquid battery widget on Home with tilt-following off and on for an hour and compare battery use in Android's battery page.
- [ ] **Media player and battery widgets.** Add both to Home and play something. *Expect:* the player takes its colours from the album art and fits its card at 2x2 and 4x2 (title, artist, progress, controls; elapsed and remaining time when wide); the Liquid battery fills its card, turns green while charging with bubbles, orange when low and red when nearly empty. Tell us if the titles are cut off too soon, or if anything is hard to read over your wallpaper.
- [ ] **Widget picker and the polished widgets.** Long-press empty space, Widgets. *Expect:* each Uno widget is shown as a preview of itself (not a text card); tapping one places it. Look at each on Home in light and dark: nothing clipped, text readable over your wallpaper, the World clock shows all its city names.
- [ ] 0.48: Edit Home > + > Add page shows a blank page, Remove this empty page removes it, pages survive a restart; Spotlight suggests your most opened apps, "ytm"-style initials work, answers copy on tap; drag a widget's resize handle on rows 2-4 and confirm it lands on the row you stop at.
- [ ] 0.50: long-press an app > Hide app removes it from All apps and Spotlight, exact name still finds it, Settings > Notifications & privacy > Show again restores it; Uninstall on a user app opens Android's confirmation; with badges on and notification access granted, Turn badges off for this app silences it and Dot style shows a dot; Edit Home > + > Move this page earlier/later.
- [ ] 0.51: Customize > Wallpaper > Material You colors recolors switches, sliders and glass from the system palette in light and dark (and replaces Color from wallpaper); Spotlight "wifi" opens Wi-Fi settings and "timer 5" starts a timer in the island; saving a backup shows a "saved and checked" summary, and the restore review lists pages, apps, widgets, hidden apps and settings.
- [ ] Stacks by gesture: swipe up/down on a stack switches its widget (a list widget keeps its own scrolling; the dots still switch it); hold one widget over the middle of another for half a second (it grows and gets a ring) and let go to stack it; dragging an app onto an app makes a folder, the target app grows and glows first.
- [ ] 0.54 Console widget (Uno widgets > Console): connect and unplug the charger, toggle airplane mode and the ringer, plug in headphones or a Bluetooth device, start a VPN, and install or update an app; each should add a line within a second. Check the light look as well as dark.
- [ ] Startup: with a release build installed on the phone, run `scripts/startup-time.sh` (set `ADB`). It compares cold start with no precompiled code, with the baseline profile, and fully compiled. On the emulator the three were within noise of each other (about 2.4 to 3.4 s, with random 10 to 17 s outliers), so this is the first useful measurement of whether the profile helps.
- [ ] 0.55 News feed: open a story (it dims and "N new" drops), long-press to save and use the Saved chip, filter by feed, import then export an OPML file, change "Stories to keep per feed". Screen time widget: place it, tap it, grant Usage access in Settings, and check it shows today and your top apps (and that Permissions & privacy lists Usage access). Checklist widget: add, tick and clear tasks.
- [ ] 0.56 Island: Customize > Island style > Motion, try Bouncy, Jelly and Droplet (and the Droplet, Jelly and Spice presets) and open, close and tap the island; tell me what feels too much or too little. The wide island should show no battery percentage. Wallpaper depth: swipe between Home pages and watch the wallpaper drift (and turn it off with the switch). Spotlight: try the spice phrase from the novel.
