# Uno Launcher user guide

Uno Launcher is an experimental Android launcher designed around a foldable phone, a four-column Home grid, and a four-position dock on the right. The cover shows one Home page at a time. Unfolding adds an editable workspace on the left: the first view pairs that workspace with Home 1, followed by Home 1 + Home 2, Home 2 + Home 3, and so on.

## Start and switch launchers

On a fresh install, **Welcome to Uno Launcher** offers **Choose Home app**, **Add a widget**, **Explore Home**, and **Not now**. Choosing or skipping setup does not prevent later changes.

To make Uno Launcher the launcher, choose **Set as home app** in customization, or open **Help & setup** and choose **Set Uno Launcher as Home**. Android owns the final Home-app chooser. To switch away later, choose **Change home app**, or use Android **Settings → Apps → Default apps → Home app**. The exact Android path may vary by device.

## Move around Home

- Swipe horizontally across Home, the dock, or the right rail to move one page per gesture.
- Swipe right from Home 1 for **My feed** (or Google Discover where the Google app is installed and you haven't chosen the feed). On a fresh install the feed page is empty: it offers the GrapheneOS announcements and release feeds, and nothing is contacted until you tap one. Swipe left, press Back, or use its right-pointing arrow to return.
- Swipe past the last Home page for **All apps**. Its **Search apps** field always searches installed apps locally.
- The dock and its search control stay on the right. The page controls also open Discover or All apps.
- Pressing the system Home control from an app returns to the Home page or unfolded pair you last had visible. From All apps, search, or Discover it returns to the last Home view.

## Customize Home

Swipe up anywhere on Home to open **Launcher settings** (not when the swipe starts on a widget that scrolls itself). You can also long press an empty Home cell to open **Add to Home**, then choose **Widgets**, **Wallpaper**, or **Customize launcher**. If every cell is occupied, long press the slim wallpaper margin at the left edge of the grid. The settings are grouped like iOS Settings:

- **Look:** **Wallpaper & glass** (launcher photos, Android wallpaper, liquid glass, color from wallpaper, light and dark) and **App icons** (original, themed or tinted by your wallpaper).
- **Home:** **Home layout** (icon size, row spacing, dock geometry, Home apps, widgets on the visible page), **Gestures & feel** (app names, the upper-right status display, pull-down and right-hand swipes, haptics and sounds), **Search** (the Google button, suggestions, contacts) and **Control panel & Focus**.
- **Island & keyboard:** **Dynamic island** (size, everywhere mode, live activities, alerts, the island widget, which apps may use it) and **Keyboard**.
- **Privacy:** **Notifications & privacy** (notification access, badges, and what every permission can see).
- **More:** **News feed**, **Backup** (layout and your settings, including large folders and widget notes), **Updates** and **Help & setup**.

After a layout edit, **Undo last layout change** appears in customization. It covers the latest supported layout change, so use it before making another edit.

## Apps, folders, and the dock

Hold an app, then drag it to an empty cell, another page, or a vacant dock position. Neighboring Home icons move aside when possible. Pause at the left or right screen edge while holding to turn a page; dragging at the end can create another Home page.

Dragging between Home and the dock moves the shortcut instead of duplicating it. The dock holds four apps. When it is full, Uno Launcher shows **Dock full • Move an app out first** and rejects a new arrival; it never evicts an app automatically. Existing dock apps can still be reordered. Drag a Home or dock shortcut to **Remove** to remove the shortcut without uninstalling the app.

Long press and release an app for options such as **Move on Home**, **Create folder**, **App info**, or **Remove from Home**. **All apps** remains the complete installed-app catalog even when a shortcut is removed.

If Android exposes a managed profile, **All apps** shows **Personal** and **Work** filters. A paused profile shows **Work apps are paused** and **Turn on work apps**. Availability and cross-profile widget access remain controlled by the profile administrator.

## Widgets

Open **Widgets** from an empty-space menu, **Add widget to this page** in customization, or **Add a widget** during setup. Search the catalog, select **Personal** or **Work** when those choices exist, then tap a preview to place it or hold it to drag. Android may ask you to allow the binding, and some providers open their own setup screen.

Hold an existing widget to pick it up, then drag it across cells or pages. A small amount of held finger jitter is allowed. Move into the lower-right **Remove** target to delete it from Home. Long press and release without dragging to open **Widget options**, which can include **Widget settings**, **Resize on Home**, page moves, **Replace**, and **Remove**.

For **Resize on Home**, drag the resize handle and choose **Apply**, or choose **Cancel**. The alternate size controls end with **Apply size**. Uno rejects sizes or moves that overlap another item, exceed the four-column by six-row grid, or violate the provider's allowed sizes.

Scrollable Android widgets keep their native vertical scrolling when the touch begins on scrollable provider content. A horizontal swipe can still change Home pages. Hold still before moving when you intend to pick up the widget.

## Background and appearance

In **Wallpaper & glass**, **Choose a photo** creates a private preview. It does not replace the current launcher background until you choose **Apply**; **Cancel** keeps the committed background. If selection is interrupted, choose **Resume** or **Cancel**. Recovery has been checked for activity recreation and a completed private preview file, but an interruption during the earlier decode step may require selecting the photo again.

**Preview Android wallpaper** opens Android's separate wallpaper preview. It does not change Uno Launcher's **Launcher background**. **Reset to Uno dunes** removes the selected launcher background.

Appearance choices are **Light**, **Dark**, **Follow system**, and **Sunrise / sunset**. Sunrise/sunset accepts coordinates through **Use this place**, or requests approximate location only when you choose **Use device location**. If location is unavailable, Uno visibly falls back to the system theme. **Clear location** removes saved coordinates; Uno Launcher does not request location in the background.

## Optional shade gestures

On Home, swipe down from the left 70% to open Notifications or from the right 30% to open Quick Settings. This is off until you turn it on: Android only lets a launcher open those panels through an accessibility service. The first swipe explains exactly what the service can and can't do and offers **Open settings** or **No thanks**. **No thanks** is remembered, and the swipe then does nothing; pressing outside the prompt only hides it for now. You can enable the gestures later from **Help & setup**, and turn them off any time in Android Settings → Accessibility. The service only requests the system panel actions; it can't read your screen or see other apps.

## Layout backup

Open **Backup**, choose **Save**, and select a document destination. Choose **Restore** to select a backup, inspect **Review restored layout**, then choose **Restore** again. **Cancel** leaves Home unchanged.

Backups contain Home and dock positions, folders, widget descriptions and spaces, layout presets, labels, search behavior, and status settings. They include the unfolded-only workspace. They do not include the selected background photo or live Android widget bindings. After restore, provider widgets keep their saved space but require **Reconnect**; unavailable apps leave empty positions, and work-profile entries may need manual placement. Review a backup before sharing because it can expose app names, folder names, and profile metadata.


## Dragging, folders and the widget library

Drop an app on the middle of another app to make a folder of the two; drop nearer the edge to move it there. **Large folders** (widget picker, Uno widgets: 2 × 2, wide 2 × 1 and tall 1 × 2) show their apps on Home so each launches with one tap; tap one, then **Choose folder apps**.

The widget picker's **Uno widgets** are drawn by the launcher itself with no permissions: **Battery**, **Month**, **Day, month and year**, **Moon phase**, **Countdown**, **Note**, **Counter**, **Timers** (starts the island timer) and **Sunrise and sunset** (for the place you set in Appearance). Countdowns, notes and counters keep what you type in the app's private storage, and layout backups carry it.
