# Island roadmap

What is built, and what is still to do. This is a working list, not a promise.

## Built

- Collapsed pill with two slots: the live activities (privacy indicators, call, timer, stopwatch, music) sit either side of the camera, and a sideways flick turns which one is in front.
- Camera and microphone indicators, permission-free (public camera-availability and audio-recording callbacks).
- Event queue: a second event waits behind the first instead of erasing it.
- Gestures: swipe up to close or dismiss, flick sideways to turn the activities (no swipe down: Android cancels a downward drag from the top for the notification shade).
- Smoother morph: the faces crossfade, closing is critically damped, and the overlay window is held at the open size while the island moves.
- Album-art tint on the bars and play button.

## To do: bringing the island in line with the spirit of GrapheneOS

The island should be a privacy and system-status instrument first and an iOS imitation second. Ideas, roughly in order:

1. **Permission ledger.** One screen listing every permission the island and launcher use (overlay, accessibility service, notification access, contacts), why each exists, what it can read, and a one-tap way to revoke it. Explain the tiers plainly: nothing; overlay only (seen, but not tappable over the camera); accessibility overlay (tappable).
2. **Advanced Protection and the accessibility overlay.** Android 16+ can block accessibility services that are not declared accessibility tools, and GrapheneOS users are the likeliest to turn that on. Detect when the accessibility service is blocked or disabled, say so, and fall back cleanly to the plain overlay with an honest explanation.
3. **More privacy and system indicators that need no special permission.**
   - VPN connected / dropped (a network callback for the VPN transport).
   - USB state: data connected versus charging only.
   - Which indicator is lit, with its app name, where Android allows it without privileged permissions.
4. **GrapheneOS-specific surfaces.** Per-profile awareness (work profile, Private Space, secondary users), and a note when sensors are switched off with GrapheneOS's Sensors permission, if it can be read without privileges.
5. **Stay out of the way.** Hide automatically during full-screen video and games; never over the lock screen; keep notification peeks to the app name only, off by default, with a per-app allowlist.
6. **Be honest about what it is.** The island is a drawn pill, not hardware. Keep the "connects to no hosts" stance, keep the release notes' hashes and signing fingerprint, and keep documenting exactly what each permission does.

## To do: other ideas

- Android 16+ Live Updates (promoted ongoing notifications) as generic live activities, with notification access on.
- Bluetooth device connect events (opt-in; needs the Bluetooth permission).
- A fully fixed-size window with a proper touch region, if a clean way appears.
