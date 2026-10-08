# Data and permissions

Uno Launcher stores settings, Home layout, widget placement, and selected wallpaper locally. It has no account system, backend, advertising, analytics SDK, or automatic crash reporting.

## Data used on your device

- Installed app names, icons, launch activities, and eligible work-profile entries populate Home and All apps.
- Widget providers control their content, accounts, and network activity; Android hosts their widgets.
- Battery, Wi-Fi, cellular signal, and airplane-mode readings populate the Home status rail while visible. Signal display does not require location access.
- Selecting a photo creates a local preview. Apply commits it; cancel preserves the previous background. Android's picker grants access to chosen images only.
- Sunrise/sunset appearance stores coordinates you enter or explicitly request through approximate location. Times are calculated locally. There is no background location tracking, and Clear location removes the stored coordinates.

## Optional access

The shade-gesture accessibility service opens notifications or Quick Settings in response to your gesture. It is off until you enable it, and the launcher never enables it for you. It cannot retrieve window contents or perform gesture injection and unsubscribes from accessibility events when connected. The first swipe explains this and offers **No thanks**, which is remembered so you aren't asked again. You can disable the service any time in Android Accessibility settings and continue using the launcher.

Coarse location is requested only when you tap the button for approximate location in sunrise/sunset appearance settings, as a single request; the launcher stores the resulting coordinates (and **Clear location** removes them). You can instead type coordinates, or leave the system theme on, and never grant it.

### Features that ask Android for access

Each of these is off by default, asks only when you turn on the feature that needs it, and can be revoked in Android's settings at any time. The launcher works fully without any of them.

- **Notification access** (badges, track title and artwork, island peek). The launcher reads which apps have unread notifications and how many, the current media session's title, artist and artwork, and the name of the app that just posted. It never reads message text. Nothing is stored, logged or sent, and the listener is only bound after you enable it in Android's settings.
- **Contacts** (search in All apps). Only contact names are read, on demand while you type; nothing is cached or sent. Tapping a result opens it in the system Contacts app.
- **Modify system settings** (brightness slider in the control panel). Used only to set screen brightness when you move that slider; moving it turns automatic brightness off.
- **Do Not Disturb access** (Silent in the ringer selector). Android ties Silent to Do Not Disturb; used only when you tap Silent.
- **Exact alarms** (island timer). Lets the timer end to the second with Home closed. Without it the timer ends within about a minute.
- **Vibrate** (island timer buzz), and a timer alarm that rings the default alarm sound.
- **Motion sensor** (tilt-following highlight, off by default). Gravity or the accelerometer is read at a low rate, only while Home is on screen and only when you turn the setting on; readings are never stored or sent. GrapheneOS's per-app Sensors toggle applies.
- **Search suggestions** (on by default, can be switched off). When no installed app matches a search, Uno can open an app store or a browser with your search words. This is a normal app launch: Uno itself sends nothing anywhere, and the web search goes through the browser you choose and its search engine, under that app's own privacy settings. Uno can only see whether those apps are installed.
- **Flashlight, volume, ringer and media keys** use Android APIs that need no permission.

Android controls widget-binding approval and Home-app selection. Providers can require separate setup or permissions.

## News feed and network use

The launcher contacts no server of its own and ships no default connection. The news feed page starts empty and nothing is fetched until you tap a suggested feed or add one. The suggestions are the GrapheneOS project's announcement feed (`grapheneos.social`) and release notes (`grapheneos.org`); each button shows its host and contacts only that host.

- Feeds must be `https`; plain `http` is refused, and the app declares no cleartext traffic.
- Redirects are followed only within the same host, over https, and at most three times. A feed that moves to a different server is refused, not contacted.
- Requests carry no cookies, no account, and no identifiers beyond a generic user-agent; nothing is uploaded.
- The Android internet permission exists solely for this feature. Entries are cached on this device and open in your browser, which then contacts the story's own site under its own settings.
- With network access turned off (for example, GrapheneOS's per-app Network permission), cached entries remain readable and new fetches report an error. Removing a feed removes its cached entries.

The GrapheneOS forum (`discuss.grapheneos.org`) publishes no RSS or Atom feed, so Uno cannot follow it; the project's announcements link to forum threads.

## Google and other apps

Discover and Google search use the installed Google app. Apps, search results, articles, and widgets may use their providers' network services and accounts. Those apps' policies and settings apply; Uno Launcher does not proxy their traffic or collect their content.

## Export, reports, and removal

A layout export is created only when you choose Save in Backup and select a destination. It can reveal installed apps, folder names, profile metadata, and layout preferences. Photos are excluded. Review it before sharing.

There is no automatic diagnostic upload. Screenshots and logs you manually attach to issues may contain personal information, widget content, account names, or work data. Review them first.

Uninstalling or clearing storage removes Uno Launcher's local settings, photos, and widget bindings. Exported files remain where you saved them. Android and device vendors may provide their own diagnostics independently of Uno Launcher.
