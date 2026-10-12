# Writing an Uno widget

Uno's own widgets (Battery, Month, Moon phase and the rest) are drawn by the launcher itself. This is everything you need to add one without touching anything else. For how the whole project is organised, see [Working together](working-together.md).

## What a widget is

- **A file** in `app/src/main/java/com/jake/duolauncher/widgets/`, named `<Name>Widget.kt`, in the package `com.jake.duolauncher.widgets`. It holds the widget's face (a `@Composable` taking `slot: Int` and `onOptions: () -> Unit`) and any helpers only it uses.
- **One line** in the registry, `widgets/UnoWidgets.kt`: an id constant and an `UnoWidgetDef(...)` entry. The widget picker, Home, layout backup and restore all read that list, so nothing else needs to change.
- **Logic in plain functions** (calendar maths, moon phase, progress, formatting) that a JVM unit test can call. Put it in your widget file, or in `widgets/UnoWidgetLogic.kt` if another widget could use it. The face then only draws.

Copy the smallest existing widget (`NoteWidget.kt` or `ProgressWidget.kt`) and change it.

## The rules

1. **No permissions, no network.** A widget draws from the date, the battery level, what the person types into it and, for the Next alarm widget, the one answer Android gives about the next alarm (no permission needed), and nothing leaves the phone. That is a promise the project makes to its users; a widget that needs more is a different kind of thing and needs the owner's decision first.
2. **Take the id you were given, and never reuse one.** Ids are negative integers; the library starts at -20 and is at -28 today. Saved layouts and backups store the id, so an id that is removed or reused would show the wrong widget (or none) on someone's Home screen. **Ask for an id in the issue before you start**: two people picking the next free number at the same time is how two widgets end up with one. `UnoWidgetRegistryTest` fails if an id is repeated, positive, or a shipped one disappears.
3. **Keep what the person types in `WidgetData`** (`WidgetData.get/set(slot, field, value)`). It is saved, pruned when the widget leaves Home, and included in settings backups automatically. Do not open your own preferences file.
4. **Use the shared look.** Colours come from `LocalGlassInk.current` (so a widget reads on light and dark glass), corners from `Corner`, and the title row from `CardTitle`. Do not hard-code colours that ignore the theme.
5. **No ticking faster than you need.** Use `rememberNow(periodMs)` and pick the slowest period that still looks right (a minute for a clock, an hour for a date). A widget that wakes the phone every second is a bug.
6. **Do not depend on another widget's file.** Shared helpers live in `WidgetHelpers.kt` and `UnoWidgetLogic.kt`.
7. **Keep it small.** A widget that needs hundreds of lines is two ideas.

## What to hand in

A pull request that contains:

- the widget file, the registry line, and your tests (a JVM test for every non-trivial rule, in `app/src/test/java/com/jake/duolauncher/`, in the style of `UnoWidgetLogicTest.kt`);
- a **screenshot** of the widget on Home, on sample data, in light and dark if you can;
- a line saying what you ran. The minimum is:

```sh
./scripts/gradle.sh :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

CI runs the same thing and must be green before the owner reviews.

## What not to touch

Anything outside `widgets/`, the registry line and your tests. In particular: `AndroidManifest.xml`, the Gradle files, `.github/`, `scripts/`, `PRIVACY.md`, the updater and backup code. A change there is not a widget change; open an issue and talk to the owner first. These files are owned in `.github/CODEOWNERS`, so the owner is asked to review them automatically.

## Checking it by hand

Build and install the debug app on an emulator or a spare phone, long-press empty space on Home, choose **Widgets**, pick yours from **Uno widgets**, and check: it places, it resizes, it survives rotating the phone and restarting the app, it looks right on light and dark glass, and removing it and undoing brings it back. For a widget with typed data, also make a backup (Launcher settings, Backup) and restore it.
