# Working together on Uno Launcher

How the work is organised now that more than one person may contribute. Short version: **one public repository, a protected `main`, small areas with a named owner, and the owner alone for anything that decides what the app is allowed to do or how it is released.**

## The repository

- There is one repository, `everettpenne/UnoLauncher`, and it is **public**. All source, all branches and all tags are visible to anyone. There is no private copy and no hidden unreleased work; plans and notes kept only on the owner's computer are not part of the project.
- **`main` is the line everything starts from.** It is protected: a change reaches it through a pull request, CI (`build`: build, unit tests, lint, and the public-source export check) must pass, and a code owner must approve. The owner can still push directly; that is a convenience for one person, not a path for anyone else.
- **Release tags (`v*`) can only be created by the owner.** Pushing one builds and signs a release with secrets held in the repository settings, so it is deliberately not something a contributor can do, from any branch.
- `.github/CODEOWNERS` says who is asked to review what. The owner is asked on everything; a contributor is added to the line for their own area, and only that area.

## How to contribute

1. **Fork the repository** (you do not need write access to it) and work on a branch in your fork. Fork pull requests cannot read the repository's secrets, which is the safe default.
2. **Open an issue first** for anything bigger than a small fix, and say which area it is in. For a widget, ask for an **id** (see [Writing an Uno widget](widget-development.md)).
3. **Keep a pull request to one area.** If it changes files outside your area, say why, or split it.
4. Say **what you ran** and attach a **screenshot** for anything visible, on sample data only.
5. The owner reviews. Small, focused pull requests are merged quickly; large ones that move files around are not, so ask first.

Set up the commit hook once per clone (`git config core.hooksPath scripts/git-hooks`) and run `./scripts/gradle.sh :app:assembleDebug :app:testDebugUnitTest :app:lintDebug` before you push. See [Contributing](../CONTRIBUTING.md).

## The areas

The code is grouped by feature, one folder (and Kotlin package) each, under `app/src/main/java/com/jake/duolauncher/`:

| Folder | What it is | Who can take it |
| --- | --- | --- |
| `widgets/` | Uno's own widgets and their registry | Any contributor. Start here. |
| `folders/` | Large folders, the folder panel and folder editing | Any contributor |
| `appearance/` | Wallpaper, glass, colour, type, corners, light and dark | Any contributor |
| `feed/` | The user-added RSS and Atom news feed | A trusted contributor (it uses the network); the feed redirect and host rules are a merge gate |
| `search/` | Search, answers, hand-off to other apps, optional contacts | A trusted contributor; contacts stay on the device |
| `panel/` | The control panel and its system controls | A trusted contributor; no new system hooks without the owner |
| `island/` | The dynamic island, its overlay, media, calls, Live Updates and permission ledger | The owner, for now (see below) |
| `keyboard/` | Uno Keyboard | The owner and the most trusted contributors only: it sees everything typed |
| *(top level)* | Home, state, paging, drag, Android widget hosting, Discover, backup, the updater, setup | The owner, permanently |

Moving between folders is an import change, not a design change: everything is `internal` to the one module, so a package is a place to find things and a boundary to review, not a hard wall. The map of what lives where is in the [code map](architecture.md).

### Why the island is the owner's for now

It draws over other apps and reads notifications, so a mistake there is a privacy mistake. It is also in the middle of a restructuring: its state and effects are being moved out of the screen code into one owner, and until that is done and covered by tests, two people editing it will collide. When that settles it can be handed on in the same way as the other areas, with the owner reviewing anything that touches permissions.

## Rules that apply everywhere

- **No new permission and no new network endpoint without the owner's decision.** Everything optional stays off until the person turns it on, with a plain explanation at the point of use. Privacy wording must say exactly what the code does, no more and no less.
- **Do not commit** signing keys, passwords, local paths, personal layouts, screenshots with real data, or copied code.
- **Saved data is a contract.** Widget ids, the saved layout format, backup files and preference keys must keep working for people who already have them. Never reuse an id or a key.
- **The build, release and supply-chain files are the owner's:** `.github/`, `scripts/`, the Gradle files, `PUBLIC-FILES`, the manifest and its XML, `PRIVACY.md`, the updater and layout backup. They are listed in `CODEOWNERS`.
- **Tests:** a pure rule gets a JVM test; behaviour that needs Android gets an emulator test. A few emulator tests are quarantined while they are rewritten; the reasons are written down, and nobody should add to that list quietly.

## Releases (the owner)

The owner bumps `versionCode` and `versionName` in `app/build.gradle.kts` and `version=` in `scripts/release-signed.sh`, writes `docs/releases/<version>.md` with honest caveats, adds it to `PUBLIC-FILES` and the `.gitignore` allowlist, runs the checks, tags `v<version>` and pushes the tag. CI signs and publishes. The signing key is held by the owner, outside the repository.

## Growing trust

A contributor starts with every pull request reviewed. After a few merged pull requests in an area, the owner may let them approve changes that touch only that area. The owner-only files above stay owner-only whatever the history. If the number of contributors grows past a handful, or a hard boundary is wanted between areas, the next step is a Gradle module per area; for now packages, `CODEOWNERS` and CI give most of that protection for a fraction of the work.

## Keeping this document true

If the structure changes (a folder moves, an area changes hands, the protections change), change this file in the same pull request.
