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

## Contributor checklist

Work through this in order. Each box is something a reviewer will look for, so ticking it yourself first makes the review short.

### 1. Before you start

- [ ] There is an **issue** for the widget that says what it shows and how it behaves, and it names the **id** you were given. Do not start without one: two people picking the next free number is how two widgets end up with one.
- [ ] The widget needs **no permission, no network, and no new kind of stored data.** If it does (Bluetooth, contacts, location, photos from the library, anything that asks Android for access), **stop and ask the owner first.** That is a decision about what the app is allowed to do, not a widget change. The manifest, the permission ledger in the settings, `PRIVACY.md` and the consent wording all change with it, and those belong to the owner.
- [ ] You have your **own working copy**: a fork, or a `git worktree` on a branch, never someone else's checkout. Branch from the latest `main`.

### 2. While you write it

- [ ] One new file, `widgets/<Name>Widget.kt`, plus tests. Nothing outside `widgets/` except the registry line and your tests.
- [ ] **Write the registry line last.** Until it is there nothing can place your widget, so a half-finished file cannot affect anyone and the project keeps compiling.
- [ ] Every non-trivial rule is a plain function with a JVM test, in `app/src/test/java/com/jake/duolauncher/`. Faces only draw.
- [ ] Colours come from `LocalGlassInk`, corners from `Corner`, the title row from `CardTitle`. It reads on light and dark glass.
- [ ] It redraws as rarely as it can (`rememberNow` with the slowest period that looks right).
- [ ] Typed data goes through `WidgetData`, never your own preferences file.
- [ ] You have not touched `AndroidManifest.xml`, the Gradle files, `.github/`, `scripts/`, `PRIVACY.md`, the updater, backup, or the island.
- [ ] **Keep it compiling.** Never push code that does not build to a branch others pull from. If you need to share unfinished work, open a **draft** pull request.

### 3. Before you open the pull request

- [ ] `./scripts/gradle.sh :app:assembleDebug :app:testDebugUnitTest :app:lintDebug` passes, with no new warnings.
- [ ] On an emulator or a spare phone: it places from **Widgets → Uno widgets**, resizes, survives **rotating the phone and restarting the app**, and **removing it and undoing** brings it back.
- [ ] If it keeps typed data: make a backup (Launcher settings → Backup) and restore it; the data comes back.
- [ ] Screenshots on **sample data only**, light and dark, in the pull request.
- [ ] You have **rebased on the latest `main`**, and `UnoWidgetRegistryTest` still passes (it fails if an id is repeated, positive, or a shipped one is missing).

### 4. In the pull request

Say what it shows, which id it uses, what you ran, and what you did not test. The owner will answer within a few days; small, single-widget pull requests are merged first. Do not bundle other changes in.

### 5. After it is merged

- [ ] Delete your branch. The widget ships with the next release; the owner writes the release notes and lists it in the README.
- [ ] If you find a bug in it later, open an issue with the version and a screenshot, and do not reuse or renumber the id.

### Keeping the whole project buildable while several people write widgets

- One widget per branch, one branch per person; a branch that does not compile stays on its author's fork.
- The registry (`widgets/UnoWidgets.kt`) is the only file several widgets touch, and each adds one line, so conflicts are a trivial merge: keep both lines.
- If you want to try someone else's unfinished widget, check out their branch in a separate worktree; do not copy their files into yours.
- If two people need the same helper, put it in `widgets/WidgetHelpers.kt` in its own small pull request first.
