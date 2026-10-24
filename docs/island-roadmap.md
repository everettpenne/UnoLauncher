# Island roadmap

What is built, and what is still to do. This is a working list, not a promise.

## Built

- **A leading activity on the pill and detached capsules beside it.** Calls, timers, stopwatches, Live Updates and music: the most important holds the pill (glyph one side, detail the other), the next is Apple's trailing capsule, and up to two more pop out as extra capsules. A sideways flick (or a tap on a capsule) turns which is in front. When all five run at once only music, the least urgent, is left out (every combination is unit tested).
- **Privacy dots.** Camera, microphone and screen recording show as small green, orange and red dots beside the pill, from public callbacks that need no permission.
- **Music that follows the player.** The player's own session decides playing and paused; a session that says "playing" over silence stops counting after a few seconds. Finished media lingers a few seconds; paused media worth returning to (a title, part-way through, over 30 seconds) leaves the pill and waits on the open island for a time you set. The cover colour washes the pill while it plays; the moving bars are checked on the real display.
- **Notification cards and flashes.** A new notification drops a card (name only, name and title, or name, title and text; blocked words; per-app timeouts and an allowlist). A flash with only the app's icon opens that notification on a tap anywhere on the pill.
- **Live Updates, navigation and downloads** as generic activities, each with its own switch.
- **Over other apps.** An overlay with a bounds-exact touch window, optional permanent wide pill, hiding in landscape, never on the lock screen; plain overlay or the optional accessibility-hosted island above the status bar.
- **Quick apps and a hosted widget** in the open panel; a search field and **search that grows out of the island**.
- **Event flashes** for ringer, airplane, Do Not Disturb, charging, VPN, USB data and audio devices, queued so two quick events are both seen.
- **Next alarm and song times** on the open panel, a screen reader that hears what is live (and each new event or card once), and a snap instead of a spring when Android's animations are off.
- **Buttons on notification cards** (a reply typed in the island on Home), a **camera and microphone row** with how long and, when usage access is already allowed, the app that was in front, **Bluetooth connect alerts**, **liquid motion** (stretch and squash, blur-in faces, a bubble that pulls away on a neck), and a size made for the Pixel 10a.
- **Pull to peek, hold open, and a recent notification held for 30 seconds** (see the changelog), and **navigation set large enough to read while driving**.
- **A permission ledger** (Launcher settings, Notifications & privacy): every permission, what it is for, what it can see, and a link to turn it off, and a plain line saying which island you actually have.
- **GrapheneOS's Sensors permission** in the ledger and under Tilt Highlight: whether Uno has it, and what stays still without it. Only the launcher's own grant is readable without privileges (a denied app gets still readings, not an error); the phone-wide "Sensors off" tile is not, so it is not shown.
- **One owner for the island's events.** The audio, ringer, camera, microphone, recording, VPN, USB, Bluetooth and battery listeners run once, held by Home while the launcher is in front and by the overlay while the island may show over apps (`IslandEventsOwner`), not inside whichever island is drawn; they keep running under a sheet or a full-screen app. The timing rules are pure and pinned (`IslandPolicy`).
- **Hide over chosen apps.** Off by default; while on and apps are chosen, the shade service is told only the package of the app in front, and the ledger says so.
- **Quick actions.** A long press on the pill opens a card for the leading activity with its own buttons (timer, stopwatch, music, call, Live Update).
- **Battery moments.** Plugging in, reaching full, and 20 % and 10 % on the way down, over apps as well as on Home.
- **Notifications know their profile.** A work-profile or Private Space notification is named "App (Work)" or "App (Private)" with Android's profile badge, badges only that profile's copy of the app, is rate-limited apart from the personal app, and works for apps installed only in that profile (their cards were dropped). Search marks work apps.

## To do

1. **Per-profile awareness, the rest.** Notifications are done (see Built). Left: Private Space apps are not listed at all (only managed profiles are), and a Live Update or call from another profile is not yet opened in that profile when its card has no tap action. Secondary users are separate users and never reach this launcher.
2. **Which app is using the camera or microphone.** Checked: Android's public API does not say (no package on the camera callback or on an audio recording), so the island shows how long, and the app that was in front when it started only as context.
3. **Be honest about what it is.** A drawn pill, not hardware; no hosts contacted; documenting exactly what each permission does.

## Ideas

- A fully fixed-size window with a proper touch region, if a clean way appears.
