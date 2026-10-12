# Island themes

An island theme changes how the dynamic island **looks and moves**. It cannot change what the island does, what it shows, or what it can see. A theme is one small text file (JSON, at most 8 KB), which you install from **Launcher settings, Dynamic island, Island style, Import a theme**. Uno only reads files you pick; no other app can send it one.

Anyone can write a theme, and you decide whether to trust it: install only files from people you trust. Uno checks every file (see "What is checked") and shows you a preview before anything changes. **Reset** returns to Classic at any time.

## The file

```json
{
  "format": "uno-island-theme",
  "version": 1,
  "name": "Sunset",
  "author": "Your name",
  "colors": {
    "fill": "#0B0805",
    "ink": "#FFF3DC",
    "playback": "#FF7A3D",
    "charging": "#FFD166",
    "timer": "#FFB000",
    "focus": "#E0B0FF"
  },
  "shape": { "panel": "round", "controls": "rounded" },
  "motion": "bouncy"
}
```

| Field | Values | What it changes |
| --- | --- | --- |
| `format`, `version` | `"uno-island-theme"`, `1` | Required. A newer version is refused with a message. |
| `name`, `author` | up to 40 plain characters; `name` is required | Shown in the preview and settings. |
| `colors.fill` | `#RRGGBB` | The island's body. Must be dark (relative luminance of at most 0.10), so the camera hole stays hidden. |
| `colors.ink` | `#RRGGBB` | Text and most icons. Must have a contrast ratio of at least 4.5 against `fill`. |
| `colors.playback` | `#RRGGBB` | The music glow, equaliser and progress. Contrast at least 3 against `fill`. |
| `colors.charging` | `#RRGGBB` | Charging bolt, percentage and its event. Contrast at least 3. |
| `colors.timer` | `#RRGGBB` | Timer, silent mode and airplane mode marks. Contrast at least 3. |
| `colors.focus` | `#RRGGBB` | The Focus / Do Not Disturb mark. Contrast at least 3. |
| `shape.panel` | `soft` (default), `round`, `square` | The island body's corner radius. |
| `shape.controls` | `pill` (default), `rounded`, `square` | Buttons and chips (circles become rounded squares). |
| `motion` | `calm`, `standard` (default), `snappy`, `bouncy` | Stiffness and damping of the island's springs. Closing always settles without overshoot. |

Every field is optional except `format`, `version` and `name`; a missing one keeps the Classic value. Colours are `#RRGGBB` only (no alpha, no names). Fields Uno does not know are ignored, so a theme written for a later version still opens where it can.

## What is never themable

- The camera, microphone, screen-recording, VPN and USB-data marks keep their fixed colours: they exist to be recognised at a glance.
- The incoming-call controls (answer green, end red).
- What the island shows, when, and what any tap does. A theme has no code, no scripts and no expressions.

Shapes are rounded rectangles only, because the glass lens cannot bend around other outlines (a Material "cookie" or "clover" outline would crash the lens at draw time). Images are not part of version 1; if they come, they will be a new `version` with their own size limits.

## What is checked

A file is refused, with the reason shown, if it is larger than 8 KB, is not JSON, has the wrong `format`, has a `version` other than 1, has a missing or oversize `name`, has a colour that is not `#RRGGBB`, an unknown shape or motion name, a body that is not dark, or text and accent colours that would not stand out from it. Nothing is applied until you tap **Apply** in the preview.

## Writing one

1. Copy the sample above into a file ending in `.unoisland.json`.
2. Change the values. The island style page in Uno shows what a built-in preset looks like; **Export this theme** saves the current look as a file you can edit.
3. Import it on your phone and look at the preview. If it is refused, the message says which field and why.

## For the project

The schema lives in `island/IslandTheme.kt` (`IslandThemeFile`) and is covered by `IslandThemeTest`, which also parses the sample in this document so it cannot drift. Adding a field means adding it to the data class, the parser, `toJson`, the island's drawing, this table and the test, and keeping old files valid. Never repurpose a field name.
