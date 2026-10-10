# Uno Launcher

A private, iOS-inspired Android launcher for Google Pixel phones running [GrapheneOS](https://grapheneos.org): a glass home screen with a right-side dock, a dynamic island at the camera, a local keyboard, a library of built-in widgets, and no accounts, servers or tracking of its own.

Uno Launcher is a personal fork of [jakesgoodapps/DuoLauncher](https://github.com/jakesgoodapps/DuoLauncher) (MIT). It started as a foldable-first launcher (a home screen that makes room when you unfold your phone) and has grown a dynamic island, widgets, folders, a keyboard and a private news feed. The primary design target is still the Pixel Fold (2023); other Pixels use the same single-screen layout as its cover display.

## Contents

- [Download and install](#download-and-install)
- [How to use it](#how-to-use-it)
- [Features](#features)
- [Privacy and permissions](#privacy-and-permissions)
- [Known limits](#known-limits)
- [Build](#build)
- [Documentation, feedback and licence](#documentation-feedback-and-licence)

<p>
  <img src="docs/images/duo-launcher-cover-home.png" width="240" alt="Uno Home on a cover-sized emulator, with its right-side dock">
  <img src="docs/images/duo-launcher-inner-home.png" width="500" alt="Uno Home unfolded, with an extra workspace on the left">
</p>

Screenshots show sample data on an emulator sized to the reference Fold, and some predate the newest features. [Fresh-install welcome](docs/images/duo-launcher-welcome.png).

## Download and install

**[Download the latest APK](https://github.com/everettpenne/UnoLauncher/releases/latest)**: on the release page expand **Assets** and tap the `.apk`. Every release also carries `SHA256SUMS.txt` and its release notes. Browse [all releases](https://github.com/everettpenne/UnoLauncher/releases).

**Status: experimental, for Pixel phones on GrapheneOS.** It builds for Android 12 or later (minSdk 31, targetSdk 36), but only current GrapheneOS releases are the target. Development and testing use emulators (the Fold's cover display, 1080 × 2092, and inner display, 2208 × 1840, plus a phone-sized one); **it has not been verified on a physical GrapheneOS device**, and emulator results do not establish compatibility with real hardware. Each release's notes list what was and was not tried.

1. Open the downloaded APK, allow installation from that source if Android asks, and open **Uno Launcher**.
2. Look around before choosing **Set as home app**; when ready, select Uno Launcher in Android's Home app settings. Installing it does not select it automatically.
3. To switch back: Android **Settings → Apps → Default apps → Home app**, then pick your previous launcher.

**Updating.** Releases are signed with one key, so a new release installs over the old one. Uno can check GitHub for you (**Launcher settings → Updates**) and hands the download to Android's installer, which always asks you to confirm. The updater only talks to GitHub, refuses a release without a published checksum, checks the checksum, and checks that the APK carries the same signing key as the app you already have. A differently signed developer or debug build cannot be updated by a public APK; see [release and update notes](docs/public-release.md). Uninstalling or clearing storage removes the saved layout and widget bindings, so make a backup first if you care.

## How to use it

### The basics

| To do this | Do this |
| --- | --- |
| Change Home pages | Swipe sideways across Home, the dock or the right rail (one page per swipe) |
| See all apps | Swipe past the last Home page, or tap the page control. Search is built in |
| Open launcher settings | **Swipe up anywhere on Home** (not from a scrolling widget), or long-press empty space and tap **Customize launcher** |
| Search everything | Pull down on Home away from the top edge, or tap Search on the island. Apps, contacts, answers and feeds in one box; with no match it offers F-Droid, Aurora or a web search in the app you pick |
| See notifications | Pull down from the top-left of Home (needs the optional shade service, see below) |
| Open the control panel | Pull down from the top-right of Home: media, volume, brightness, ringer, flashlight, Focus, shortcuts and a hand-off to Android's Quick Settings |
| Move an app or widget | Long-press, then drag. Hold at a screen edge to change or create a page |
| Make a folder | Drop an app onto the **middle** of another app. Drop nearer the edge to just move it there |
| Add to the dock | Drag an app into a free dock slot (move one out first if the dock is full) |
| Open Discover / your news feed | Swipe right from the first Home page, or tap the compass. It shows Google's feed when available, otherwise your own feed |
| Use split screen | Long-press an app, **Open in split screen**, then pick the second app |

The ring around the clock shows battery, the arcs inside it show Wi-Fi strength, and the dots show cellular strength.

### Set it up

Open **Launcher settings** (swipe up on Home). It is grouped like iOS Settings:

- **Look:** *Wallpaper & glass* (a private launcher photo, the Android wallpaper, liquid glass and its refraction, colour from your wallpaper, light, dark or sunrise-to-sunset) and *App icons* (original, themed in the launcher's colours, or tinted by your wallpaper).
- **Home:** *Home layout* (icon size, row spacing, dock position, which apps sit on Home, widgets per page, separate cover and inner layouts), *Gestures & feel* (app names, the status display, what the pull-downs do, haptics and sounds), *Search*, and *Control panel & Focus* (tile order, panel shortcuts, apps Focus hides).
- **Island & keyboard:** *Dynamic island* and *Keyboard*.
- **Privacy:** *Notifications & privacy* (notification access, badges, and a ledger of every permission, what it is for and what it can see).
- **More:** *News feed*, *Backup*, *Updates*, *Help & setup*.

### Widgets, large folders and the widget library

Long-press empty space, choose **Widgets**, and pick one.

- **Android widgets** from your installed apps: Android asks you to allow the binding, and the provider may have its own setup. Resize them from their options; scroll them by swiping inside; hold still to pick one up.
- **Widget stacks:** open a widget's options and choose **Stack another widget here**; use the dots down its right edge to flip between the stacked widgets.
- **Large folders** (2 × 2, wide 2 × 1 or tall 1 × 2): a folder that shows its apps on Home so each opens with one tap. Tap it, then **Choose folder apps** to pick up to 24 apps and name it. Resize it like any widget.
- **Uno widgets**, drawn by the launcher itself with no permissions: **Battery** (ring, time to full), **Month** (calendar with today marked), **Day, month and year** (progress and days left), **Moon phase**, **Countdown** (tap to set a name and date), **Note** (tap to write), **Counter** (tap to add, small button to subtract, tap the name to rename or reset), **Timers** (5, 10, 25 and 45 minutes, running in the island) and **Sunrise and sunset** (needs a place set under Wallpaper & glass, Appearance). Countdowns, notes and counters are stored in the app's private storage and are part of layout backups.

### The dynamic island

The pill around your camera shows what is happening, and opens into a small panel.

- **Tap** it to open the panel (time, battery with time-to-full while charging, music, a call, a Live Update, your widget, and shortcuts to search, the feed and settings). **Swipe up** or tap outside to close it. **Long-press** it for tools: a flashlight, a timer and a stopwatch.
- **Several things at once:** the pill carries two; a third and fourth pop out as round **bubbles** beside it. Tap a bubble to bring it to the front, or flick across the pill to turn the order.
- **What it shows:** music (title, artwork, controls and a seek bar you can tap or drag), calls (caller and time), timers and the stopwatch, **Android 16 Live Updates** (a ride, a delivery, a timer), camera and microphone marks, a red mark while the screen is recorded, VPN and USB-data alerts, headphone and Bluetooth audio connecting, ringer, airplane and Do Not Disturb changes, charging, and optionally a peek when a notification arrives.
- **Over other apps:** turn on **Island everywhere** and grant "Display over other apps". If you also enable the optional shade service, the island is drawn above the status bar so it can be tapped. It steps aside in landscape (video and games).
- **A widget in the island:** choose one Android widget in *Dynamic island* settings for the open panel to show.
- **Control:** every switch is in *Dynamic island* settings, including **Apps that can use the island** (switch off any app's Live Updates and peeks).

### Uno Keyboard (optional)

In *Keyboard* settings, enable it in Android's keyboard settings and select it when typing. It is flat and iOS-like, with key previews, long-press accents and symbol variants, suggestions and autocorrect from a bundled word list (English; they never learn from what you type and switch off in password fields), an emoji panel, a Paste button (the clipboard is read only when you tap it), space-bar cursor sliding, one-handed mode and a number row.

### Backup and Focus

- **Backup** (*Launcher settings → Backup*): **Save** writes a JSON file to a place you choose; **Restore** shows a review before changing anything. It carries your Home layout, folders, widget placements, large folders and widget notes, and your Uno settings (not your location, browser choice, photo background, or widget stacks).
- **Focus** hides the apps you pick from Home and All apps until it is turned off (from the control panel); nothing is uninstalled.

### Shade gestures (optional)

Pulling down for Android's own notification and Quick Settings panels needs an accessibility service. It is off until you turn it on, **cannot read the screen or tap or type for you**, and is only used to open those two panels and to draw the island above the status bar. **No thanks** (or dismissing the prompt) stops the question for good, and Uno works the same without it.

## Features

- A persistent right-side dock, vertical status indicators, and overlapping page pairs when unfolded.
- Android widgets with visual picking, resizing and native scrolling; widget stacks; large folders; and a library of nine built-in widgets.
- App dragging, drop-to-make-folder, edge-drag page creation, Home folders, and separate personal and work catalogs where device policy permits.
- A dynamic island with Live Updates, music, calls, timers, privacy marks, system alerts, bubbles, a hosted widget and per-app control.
- A control panel, Focus, Spotlight-style search with app hand-off, badges, and contact search (all optional).
- An iOS-inspired look in the Inter typeface: liquid-glass dock, widgets, sheets and controls that blur and refract what is behind them (adjustable, with an off switch), continuous-corner icons, a spring-scale press with no ripple.
- A private RSS/Atom news feed that fills the Discover slot without a Google account or app. It starts empty and contacts nothing until you add a feed.
- Uno Keyboard, a local on-screen keyboard.
- Photo wallpapers, light, dark and sunrise-to-sunset appearance, layout and settings backup, and a self-updater.

Android still controls the lock screen, recents and system app transitions.

## Privacy and permissions

No launcher account, server, advertising, analytics or automatic crash upload is used. Layouts, notes and backgrounds stay on the device unless you export or share them. Read [PRIVACY.md](PRIVACY.md) for the full list.

- **Network:** the launcher contacts only the https feed addresses you choose (redirects never leave that host) and GitHub for updates and the release page. Revoking GrapheneOS's Network permission leaves everything except those two working.
- **Everything optional is off until you use it,** and declining any one only turns off that feature: notification access (badges, track titles, Live Updates, island peek; message text is never read, only a promoted Live Update's own title and status), contacts (search), "Modify system settings" (the brightness slider), Do Not Disturb access (Silent), exact alarms (to-the-second timers), approximate location once (sunrise, sunset), "Display over other apps" (the island above other apps) and the accessibility service above.
- **The island is drawn over banking and password apps** when Island everywhere is on, because Android gives a launcher no way to tell which apps are secure. It cannot be tapped through; turn Island everywhere off if you do not want it there.
- **Photos:** the system picker grants access to chosen images only. **Google features:** the installed Google app's own settings apply.

## Known limits

- Panels and widgets use circular-arc corners and only app icons get continuous corners: the glass library accepts rounded rectangles only.
- Discover can differ across Google, Android and vendor updates; recovery controls always return you Home. The news feed works without the Google app, reads RSS 2.0 and Atom with plain summaries, and needs a browser to open stories.
- Work apps and widgets remain subject to administrator policy. Private Space is not supported. Icon packs are not implemented. Folders cannot nest or sit in the dock.
- Imported Android widgets must be bound again; backups exclude widget stacks, photo backgrounds and system widget capabilities. A large folder or widget restored on another phone keeps its apps only where the same apps exist.
- The full-screen rule is landscape only (portrait full-screen apps cannot be detected without reading other apps' windows, which Uno will not do). Moon phase is the mean cycle and can be hours off.
- Secure lock-screen replacement and hinge-driven cross-display animation are outside scope.

## Build

Use JDK 17 or Android Studio's bundled JDK, Android SDK 36 and the included Gradle wrapper. Set `ANDROID_HOME` or a local `sdk.dir` in `local.properties`.

```sh
./scripts/gradle.sh :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

The debug APK is at `app/build/outputs/apk/debug/app-debug.apk`. Release builds use R8 and resource shrinking; private signing material stays outside the repository, and a tag starting with `v` builds and publishes a release through GitHub Actions. Follow the [release instructions](docs/public-release.md).

The project is Kotlin and Jetpack Compose with native widget hosting. Unit tests cover the pure rules; instrumented tests run on disposable emulators (some fixtures need Google, Clock and Chrome and are not for your everyday phone). The [code map](docs/architecture.md) explains the main components and the gesture and widget constraints; [design notes](docs/design.md) cover the details.

## Documentation, feedback and licence

[User guide](docs/user-guide.md) · [Troubleshooting](docs/troubleshooting.md) · [Island roadmap](docs/island-roadmap.md) · [Changelog](CHANGELOG.md) · [Contributing](CONTRIBUTING.md) · [Data and permissions](PRIVACY.md)

Use the issue templates with version, phone model, Android version, folded or unfolded state and reproduction steps, and review screenshots and logs for personal and work information first.

Source is under the [MIT licence](LICENSE); dependency notices are in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). This independent project is unaffiliated with Apple, Google, Samsung or GrapheneOS. The default wallpaper is drawn locally and app icons come from installed apps. Apple research media and Google application code are excluded from the public source and APK.
