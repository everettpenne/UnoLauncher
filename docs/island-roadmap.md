# Island roadmap

What is built, and what is still to do. This is a working list, not a promise.

## Built

- Collapsed pill with two slots: the live activities (privacy indicators, call, timer, stopwatch, music) sit either side of the camera, and a sideways flick turns which one is in front.
- Camera and microphone indicators, permission-free (public camera-availability and audio-recording callbacks).
- Event queue: a second event waits behind the first instead of erasing it.
- Gestures: swipe up to close or dismiss, flick sideways to turn the activities (no swipe down: Android cancels a downward drag from the top for the notification shade).
- Smoother morph: the faces crossfade, closing is critically damped, and the overlay window is held at the open size while the island moves.
- Album-art tint on the bars and play button.
- A music card in the open island: artwork and track above the controls, with the controls staying visible after a pause.
- A permission ledger (Customize, Control panel & extras): every permission the launcher uses, what it is for, what it can see, and a link to turn it off, plus a plain line saying which island you actually have (Home only, above other apps but not tappable, or tappable).
- A blocked-service check: the ledger says so when the accessibility service is turned on in Settings but Android is not running it (Advanced Protection or a restricted-settings block), and the plain overlay is used instead.
- VPN connected / dropped and USB data connected / ended alerts, from public callbacks that need no extra permission, with a switch to turn them off.

## To do: bringing the island in line with the spirit of GrapheneOS

The island should be a privacy and system-status instrument first and an iOS imitation second. Done so far is listed above; still to do:

1. **Stay out of the way.** Hide automatically during full-screen video and games. The island cannot see other apps' windows, and the system bar insets an overlay window receives are not reliable enough to depend on, so this needs a trustworthy full-screen signal first.
2. **Peek allowlist.** Notification peeks are app name only and off by default; add a per-app allowlist so only the apps you pick can peek.
3. **Per-profile awareness.** Work profile, Private Space and secondary users: say whose notification or app an event is, and keep profiles from mixing in search and peeks.
4. **Which app is using the camera or microphone.** Only where Android allows it without privileged permissions; today the marks say that something is in use, not what.
5. **Sensors switched off.** Show GrapheneOS's Sensors permission state if it can be read without privileges.
6. **Be honest about what it is.** The island is a drawn pill, not hardware. Keep the "connects to no hosts" stance, keep the release notes' hashes and signing fingerprint, and keep documenting exactly what each permission does (the ledger is where that now lives).

## To do: other ideas

- Android 16+ Live Updates (promoted ongoing notifications) as generic live activities, with notification access on.
- Bluetooth device connect events (opt-in; needs the Bluetooth permission).
- A fully fixed-size window with a proper touch region, if a clean way appears.
