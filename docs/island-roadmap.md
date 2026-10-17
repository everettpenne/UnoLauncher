# Island roadmap

What is built, and what is still to do. This is a working list, not a promise.

## Built

- **A pill with two slots and up to two bubbles.** Calls, timers, stopwatches, Live Updates and music sit either side of the camera, the next two pop out as round bubbles, and a sideways flick turns which is in front. When all five run at once only music, the least urgent, is left out (every combination is unit tested).
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

## To do

1. **One owner for the island's state and effects.** Home and the overlay share one `IslandState` and each composable still runs its own effects against it. Move the policy and effects into one controller, behind tests that pin today's behaviour (the first of these, `IslandStateTest`, exists).
2. **Hide over apps the person chooses.** An optional list, off by default, with its own consent. It needs the accessibility service to learn which app is in front, so it must not be on by default and its consent copy must say exactly what the service then receives.
3. **Per-profile awareness.** Work profile, Private Space and secondary users: say whose notification or app an event is, and keep profiles from mixing in search and peeks.
4. **Which app is using the camera or microphone.** Checked: Android's public API does not say (no package on the camera callback or on an audio recording), so the island shows how long, and the app that was in front when it started only as context.
5. **Sensors switched off.** Show GrapheneOS's Sensors permission state if it can be read without privileges.
6. **Be honest about what it is.** A drawn pill, not hardware; no hosts contacted; documenting exactly what each permission does.

## Ideas

- A fully fixed-size window with a proper touch region, if a clean way appears.
