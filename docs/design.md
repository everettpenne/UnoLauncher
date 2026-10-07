# Uno design system

Uno Launcher is a deliberate hybrid: **Android behavior, Apple look.** This file is the
single source of truth for the numbers. When a new surface or control is added, it should
pull from these scales instead of inventing its own values — ad-hoc numbers are how
overlaps happen.

- **Behavior follows Android:** window insets, display cutouts, foldable posture, 48 dp
  touch targets, edge-to-edge. These are not style choices.
- **Look follows Apple's HIG:** continuous corners, the dynamic island, iOS control
  colors, liquid glass. Deviations from Material are intentional and listed here.

## Spacing scale

Commit to multiples of 4 dp; prefer the 8 dp grid.

| Step | Use |
| --- | --- |
| 4 dp | inside capsules, hairline breathing room |
| 8 dp | gaps between siblings, small paddings |
| 12 dp | list/pill paddings |
| 16 dp | screen margins, panel padding |
| 24 dp | section spacing |
| 32 dp | panel separation |

Rules: **16 dp** from screen edges, **≥ 8 dp** between siblings, **48 dp** minimum
touch targets. `WindowInsets.safeDrawing` wraps every full-screen layout.

## Corner system (`Corners.kt`)

One family, iOS-style nesting: an inner shape's radius is its container's radius minus
the inset between them.

| Token | Value | Use |
| --- | --- | --- |
| `Corner.small` | 12 dp | rows, highlights, small tiles |
| `Corner.medium` | 18 dp | cards and items inside a panel |
| `Corner.large` | 24 dp | widgets, folders, drop targets |
| `Corner.xlarge` | 32 dp | dock, status rail, sheets, panels, island expanded |
| `Corner.pill` | 50% | capsules and circles |
| `Corner.icon` | squircle, 22.37% of side | app-icon silhouette (Apple's continuous-corner ratio) |

Container surfaces keep circular-arc corners: the glass lens accepts only
`RoundedCornerShape` and throws on continuous-curve outlines at draw time.

## Color

| Role | Light | Dark |
| --- | --- | --- |
| Ink (on-surface text) | `0xFF243A46` | `0xFFEAF3F6` |
| Glass | `0xFFE8EFF2` | `0xFF263A43` |
| Dune gradient top | `0xFF41687E` | `0xFF132832` |
| Dune gradient bottom | `0xFFD8CEB6` | `0xFF463F35` |
| Theme primary | `0xFF30596D` | `0xFF9BC5D7` |

iOS accents: green `IosGreen 0xFF34C759` (switches, charging), dark green
`IosGreenDark 0xFF30D158`, orange `IosOrange 0xFFFF9500` (sliders, highlights),
grey `IosGrey 0xFF787880`. Green = affirmative state; orange = adjustable value.

## Typography

Material defaults (`MaterialTheme.typography`) everywhere; the status rail sets its own
sizes: time up to 18 sp, detail up to 11 sp, both respecting the system font scale.
All body text keeps ≥ 4.5:1 contrast against its glass tint.

## Touch and interaction

- Minimum target 48 dp: buttons use `heightIn(min = 48.dp)`, circle controls
  `size(...).coerceAtLeast(48.dp)`.
- Press feedback: spring (damping 0.45, stiffness 260), scale to 0.92, white bloom
  `alpha 0.18` (`PressGlow.kt`). Drag gestures must never fight press feedback.
- Island springs: damping 0.5, stiffness 300; auto-collapse after 5 s.

## Home geometry

- Grid: 4 columns × 6 rows; dock: 4 slots.
- Presets (Customize launcher → Home layout, per cover/inner):
  icon size 40–68 dp, row gap 0–28 dp, dock width 56–84 dp, dock height on screen
  25–75% (or aligned to rows).
- Pager inset math: `pagerWidth = maxWidth - dockWidth - 28 dp`; the pager runs under
  the dock and rail so pages slide beneath the glass.

## Glass rules (`LiquidGlass.kt`)

All surfaces share one lens (`liquidGlass`), tuned by `GlassSettings`
(height, amount, chromatic; defaults 0.55 / 0.55 / 0, each persisted 0–1):

- Refraction height 8–44 dp (capped at 72 px), amount 16–132 dp, depth always on,
  chromatic aberration engages above 5%.
- Tint follows the wallpaper: the photo's muted palette blended 50% into the theme
  glass; the drawn dunes keep the theme glass.

Per-surface tint and blur (alpha over the theme/derived tint):

| Surface | Tint alpha | Blur |
| --- | --- | --- |
| Dock, status rail | 0.12 | 0.75 dp |
| Circle controls, page dots | 0.12–0.14 | 0.75–1 dp |
| Island | 0.34 | 2 dp |
| Feed page | 0.55–0.82 | 2 dp |
| Discover recovery | 0.45 | 3 dp |
| All apps panel | 0.40 | 6 dp |
| Folder panel | 0.90 | 4 dp |
| Glass sheet | surface @ 0.58 | 8 dp |
| Widget gallery | 0.62 | 12 dp |

## Island geometry (`IslandGeometry.kt`)

Window-pixel pure layout, re-read from live insets on every layout (rotation/fold safe).
Cutout = the camera hole nearest the top-center.

| Constant | Value |
| --- | --- |
| Expanded width | 336 dp |
| Expanded body below hole | 112 dp |
| Edge margin | 8 dp |
| No-cutout capsule | 108–132 × 30–38 dp |
| Collapsed, by the Island size slider | slots 50–66 dp each side, min half-height 15–21 dp, hole surround 2–10 dp |

The collapsed face keeps the time and battery on opposite sides of the hole; the
expanded panel hangs below it. **Known collision:** on cover-width displays the 336 dp
expanded island overlaps the status rail (see the spacing audit below).

## Motion

- Springs for press (above) and island expand; `animateFloatAsState` for values.
- Sheets: Material `ModalBottomSheet`; when glass is on, `GlassModalSheet` renders the
  sheet as an inset glass card and exports its backdrop so controls inside refract the
  sheet itself (`LocalPageGlass`).

## Deliberate Material deviations

1. Squircle app icons (22.37%) instead of Material's circular arcs.
2. iOS green/orange control colors instead of the Material color roles.
3. Liquid-glass surfaces (refraction) instead of tonal elevation.
4. The dynamic island instead of Material's status indicators.

## Spacing audit (known off-grid values)

Audited against: 16 dp edge margins, ≥ 8 dp sibling gaps, 8 dp grid.

| Location | Current | Verdict |
| --- | --- | --- |
| Dock & rail right margin | `padding(end = 12.dp)` | off-grid; use 16 dp |
| Bottom strips bottom margin | `bottom = 6.dp` | off-grid; use 8 dp |
| Bottom-right controls right margin | `padding(end = 12.dp)` | off-grid; use 16 dp |
| Page-dots capsule | `padding(horizontal = 6.dp)` | off-grid; use 8 dp |
| Feed page | `22 / 18 / 14 / 10 dp` | mixed; use 16 / 16 / 16 / 12 |
| Feed entry spacing | `spacedBy(10.dp)` | off-grid; use 8 or 12 dp |
| Sheet body | `horizontal 20 dp, spacedBy(10.dp)` | off-grid; use 16 / 8 dp |
| No-cutout island top | `statusBarHeight + 4.dp` | below the 8 dp rule; use 8 dp |
| Island expanded width | 336 dp fixed | overlaps the rail on cover widths (see below) |

**Top-strip collision:** on a 411 dp cover display the expanded island spans
≈ 37–374 dp while the rail starts at ≈ 315 dp — a ≈ 30–58 dp overlap (depending on dock width). Fix when next
editing `IslandGeometry`: cap the expanded width at
`screenWidth − 2 × (dockWidth + 16 dp)`, or fade the rail while the island is expanded.
