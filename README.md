# Uno Launcher

A native Android launcher built around a right-side dock and a home screen that makes room when you unfold your phone.

Uno Launcher is a personal fork of [jakesgoodapps/DuoLauncher](https://github.com/jakesgoodapps/DuoLauncher) (MIT). It is built for Google Pixel phones running [GrapheneOS](https://grapheneos.org), with an iOS-inspired look, and adds a private RSS/Atom news feed for the Discover slot that needs no Google account or app.

## Download

**[⬇ Download the latest APK](https://github.com/everettpenne/UnoLauncher/releases/latest)** — on the release page, expand **Assets** and tap the `.apk` file. Direct file: `https://github.com/everettpenne/UnoLauncher/releases/download/v0.16.1-uno01/UnoLauncher-0.16.1-uno01-release.apk`. Browse [all releases](https://github.com/everettpenne/UnoLauncher/releases) for checksums and notes.

**Experimental beta for Pixel phones on GrapheneOS.** The primary design target is the Pixel Fold (2023); other Pixels use the same single-screen layout as its cover display. Development and testing so far use emulators at the Pixel Fold's cover (1080 × 2092) and inner (2208 × 1840) sizes. It has not yet been verified on a physical GrapheneOS device, and emulator results do not establish compatibility with real hardware. The code builds for Android 12 or later, but only current GrapheneOS releases are the target. Google Discover is optional and needs the Google app plus device support for activity embedding; on GrapheneOS the news feed fills that page. Release notes: [0.16.0-uno01](docs/releases/0.16.0-uno01.md).

<p>
  <img src="docs/images/duo-launcher-cover-home.png" width="240" alt="Uno Home on a cover-sized emulator, with its right-side dock">
  <img src="docs/images/duo-launcher-inner-home.png" width="500" alt="Uno Home unfolded, with an extra workspace on the left">
</p>

Screenshots use sample data on an emulator sized to the reference Fold. [Fresh-install welcome](docs/images/duo-launcher-welcome.png).

**Start here:** [User guide](docs/user-guide.md) · [Troubleshooting](docs/troubleshooting.md) · [Release notes](docs/releases/0.16.0-uno01.md)

## Features

- A persistent right-side dock and vertical Home status indicators.
- Overlapping unfolded page pairs: an extra workspace beside Home 1, then Home 1 beside Home 2, and so on.
- Android widgets, visual widget selection, resizing, native scrolling, and drag-and-drop between pages.
- App dragging, pages created during an edge drag, Home folders, and separate personal/work catalogs where device policy permits.
- Alphabetical All apps, Google search with a local app-search fallback, and live Discover on compatible devices.
- A news feed that fills the Discover slot without Google. It starts empty and connects to nothing until you tap a suggested GrapheneOS feed (announcements or releases) or add your own https feed.
- iOS-inspired styling, set in the Inter typeface: liquid-glass dock, widgets, sheets and controls that blur and refract Home behind them (adjustable, with an off switch), continuous-corner app icons, and one consistent corner scale.
- Local photo wallpapers, light/dark/system or sunrise/sunset appearance, and layout export/import.

Android still controls the lock screen, notification panels, recents, and system app transitions.

## Install and try it

1. Download the signed APK from this repository's Releases section. Read its tested-device notes and known issues.
2. Open the APK, allow installation from that source if Android asks, and open **Uno Launcher**.
3. Try the layout before choosing **Set as home app**. Select Uno Launcher in Android's Home app settings when ready.
4. Swipe up anywhere on Home for **Launcher settings**, or long press an empty Home cell or the narrow wallpaper margin beside a full grid to add widgets or **Customize launcher**. Help is available from customization.

To switch back, open Android **Settings → Apps → Default apps → Home app** and select your previous launcher. Vendor labels may differ. Installing Uno Launcher does not automatically select it as Home.

Normal beta updates install over the existing beta with the same signing key. Uninstalling or clearing storage removes the saved layout and widget bindings. A differently signed developer/debug build cannot be updated directly by the public APK; see [release and update notes](docs/public-release.md).

## Everyday controls

| Action | Gesture or control |
| --- | --- |
| Change pages | Swipe horizontally across Home, the dock, or right rail; one page per gesture |
| All apps | Swipe past the last Home page or tap its page control |
| Discover / My feed | Swipe right from the first Home page or tap the compass; shows your news feed when Google's feed isn't available |
| Return from Discover | Swipe left, use the right-pointing arrow, or press Back |
| Rearrange apps/widgets | Hold, then drag; pause at the screen edge to change or create a page |
| Add to the dock | Drag into a vacancy; move an app out first when the dock is full |
| Scroll a widget | Swipe vertically inside its content; hold still to pick it up |
| Customize | Long press empty Home space or the wallpaper margin beside the grid |
| Notifications | Pull down from the top edge of Home, on the left (needs the optional shade gestures) |
| Search | Pull down from anywhere below the top edge of Home, or Island > Search: apps, contacts, answers and feeds in one box. No installed match? It suggests F-Droid, Aurora and a web search in the app you pick |
| Widget stacks | Long-press a widget > **Stack another widget here** |
| Split screen | Long-press an app, **Open in split screen**, then pick the second app |
| Control panel | Pull down from the top edge of Home, on the right 30%: media, volume, brightness, ringer, flashlight, and a hand-off to Android's Quick Settings. Needs no accessibility service |

The surrounding status ring shows battery, the inner arcs show Wi-Fi strength, and the lower dots show cellular strength. Unknown readings are not displayed as full signal. This rail applies to Home only.

## Optional access and privacy

No launcher account, server, advertising, analytics, or automatic crash-upload service is used. Layouts and selected backgrounds stay on the device unless explicitly exported or shared.

- **Widgets:** Android asks to allow binding; providers may have their own setup.
- **Network:** the launcher contacts no server of its own. It connects only to the https feed addresses you choose, and redirects never leave that host. Revoking GrapheneOS's Network permission for the app leaves everything except fetching new feed entries working.
- **Shade gestures:** off until you enable them. They need an accessibility service that can only open Notifications or Quick Settings; it cannot read window contents or inject gestures. One tap on **No thanks** stops the prompt for good, and the launcher works the same without it.
- **Sunrise/sunset:** manually enter coordinates, or tap to request approximate (coarse) location once. There is no background location request.
- **Photos:** the system picker grants access to chosen images, without whole-library access.
- **Google features:** the installed Google app's account, network, and privacy settings apply.

- **Keyboard:** Uno Keyboard is optional and local (no network code, nothing stored or learned); it has autocorrect, suggestions, an emoji panel, a Paste button, one-handed mode and an iOS-style look.
- **Optional grants, all off until you use the feature:** notification access (badges, track titles, island peek), contacts (search), "Modify system settings" (brightness slider), Do Not Disturb access (Silent), and exact alarms (to-the-second timer). Declining any of them only turns off the one feature.

Read [data and permissions](PRIVACY.md) before sharing backups or diagnostics.

## Known limits

- Panels and widgets use circular-arc corners and only app icons get Apple-style continuous corners: the glass library accepts rounded rectangles only.
- The GrapheneOS forum (Flarum) publishes no RSS or Atom feed, so the suggested feeds are the project's announcement account and release notes instead.
- Discover can differ across Google, Android, and vendor updates. Its smooth embedding transition includes a version-scoped compatibility workaround; it is not a portable SystemUI API. Recovery controls let you return Home when unavailable. The news feed works without the Google app.
- The news feed reads RSS 2.0 and Atom pages with plain summaries; it does not render web pages, media, or script, and needs a browser app to open stories.
- Work apps/widgets remain subject to administrator policy. Private Space is not supported.
- Icon packs are not implemented (themed icons use each app's own monochrome layer). Folders cannot nest or occupy dock slots.
- Imported Android widgets require binding again. Cross-installation work entries may require manual placement. Backups exclude photo backgrounds and system widget capabilities.
- Secure lock-screen replacement and hinge-driven cross-display animation are outside this beta.

## Build

Use JDK 17 or Android Studio's bundled JDK, Android SDK 36, and the included Gradle wrapper. Set `ANDROID_HOME` or a local `sdk.dir` in `local.properties`.

```sh
./scripts/gradle.sh :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

The debug APK is at `app/build/outputs/apk/debug/app-debug.apk`. Release builds use R8 and resource shrinking; private signing material stays outside the repository. Follow [release instructions](docs/public-release.md) for signing and public-source export.

The project uses Kotlin, Jetpack Compose, AndroidX Window, and native widget hosting. Instrumentation runs on disposable emulators. Some integration fixtures require Google, Clock, Chrome, and a configured emulator; they are not commands for your everyday phone.

The [contributor code map](docs/architecture.md) explains the main components, data ownership and gesture/widget constraints.

## Feedback and contributions

Use issue templates with version, phone model, Android version, folded/unfolded state, and reproduction steps. Review screenshots and logs for personal/work information. See [contributing](CONTRIBUTING.md) and [changes](CHANGELOG.md).

Source is under the [MIT license](LICENSE); dependency notices are in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). This independent project is unaffiliated with Apple, Google, Samsung, or GrapheneOS. The default wallpaper is drawn locally; app icons come from installed apps. Apple research media and Google application code are excluded from the public source and APK.
