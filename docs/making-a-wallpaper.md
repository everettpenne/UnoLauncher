# Making your own wallpaper

You can draw a wallpaper in any vector editor (Inkscape, Figma, Affinity, Illustrator, Boxy SVG) and use it as Uno's launcher background. Uno does not read SVG files directly: the background picker takes a picture, so the last step in the editor is to **export a PNG** (or WebP). Everything else is ordinary drawing.

## 1. Set up the canvas

| Setting | Value | Why |
| --- | --- | --- |
| Size | **1440 × 3120 px** (or your phone's resolution, tall side vertical) | Uno shrinks any picture whose long edge is over **2048 px**, so more than that is wasted. 1080 × 2400 is plenty for most phones. |
| Shape | Portrait, the same ratio as your screen | The picture is scaled to fill the screen and cropped to fit; a different ratio loses its edges. |
| Colour | sRGB | What phone screens show. |

Draw the important parts in the **middle 85%** of the canvas. Two things eat the edges:

- Uno draws the wallpaper about **4.5% larger on each side** and slides it a little as you swipe between pages (the depth effect). Anything in the outer strip can slide off-screen.
- Different phones crop a little at the top and bottom.

Leave the **top ~12%** quiet (no detail or bright shapes). The status bar, the clock and the Dynamic Island sit there, and a busy sky makes them harder to read. The **bottom ~15%** holds the dock and the search button; keep it calm too.

## 2. Draw it

- Use **gradients and large shapes**. Icons and text sit on top, so fine detail underneath fights with them.
- Keep contrast **low to medium**. A wallpaper that goes from near-white to near-black in one picture makes some icon labels unreadable. Uno's glass panels and its "Color from wallpaper" tint are built from the picture, so a wallpaper with one clear dominant hue gives the best tint.
- Avoid pure white and pure black for large areas. Off-white (#F2EFE8) and deep colour (#0B1020) look better on an OLED and behave better with glass.
- Soft light helps the glass effect: a gradient glow behind where the widgets go makes the frosted panels read as glass.
- In Inkscape, **Filters ▸ Blurs** and in Figma a layer blur give dunes, haze and glow cheaply. They export fine.
- If you want it in both light and dark mode, make two pictures (see step 4).

## 3. Export

**Inkscape**: *File ▸ Export* (Shift+Ctrl+E), choose *Page*, set width 1440 and height 3120, format **PNG**, then *Export*. Turn *Antialiasing* on.

**Figma**: select the frame, add an export setting, choose **PNG** at 1x (frame already 1440 × 3120), *Export*.

**Command line** (any SVG):

```bash
inkscape wallpaper.svg --export-type=png --export-width=1440 --export-height=3120 -o wallpaper.png
# or with rsvg-convert
rsvg-convert -w 1440 -h 3120 wallpaper.svg -o wallpaper.png
```

PNG is lossless and best for gradients. If the file is large (over ~5 MB), export **WebP** at quality 90 instead; gradients in JPEG band visibly, so avoid JPEG.

## 4. Put it in Uno

1. Copy the PNG to your phone (USB, Files, or any share). Anything that puts it in the photo gallery works.
2. In Uno open **Settings ▸ Appearance ▸ Wallpaper & glass ▸ Launcher background ▸ Background photo** and choose your picture in the photo picker. Uno asks for no storage permission: the system picker hands over just that one image.
3. Look at Home. Swipe between pages: the wallpaper should drift slightly behind the icons.
4. If you also want the phone's own lock screen and home wallpaper to match, turn on the option that applies the same picture to Android's wallpaper (off by default).
5. Open **Color from wallpaper** and check the glass tint picked a colour you like. If it did not, adjust the dominant hue in the SVG and re-export.

Your background photo is **not** included in layout backups (it can be large and is yours alone), so keep the SVG and the PNG.

## 5. Checklist

- [ ] Portrait, 1440 × 3120 or similar, under 2048 px on the long edge
- [ ] Important shapes inside the middle 85%
- [ ] Quiet at the top (island and clock) and at the bottom (dock)
- [ ] Not pure white / pure black over large areas
- [ ] Looks fine with the island's glass panels over it and at the largest icon size
- [ ] Exported as PNG (or WebP), not SVG

## Going further: a built-in scene

Uno's own Dune wallpapers are not pictures but code (`app/src/main/java/com/jake/duolauncher/appearance/DuneScenes.kt`): they are drawn at the phone's exact resolution, move with the time of day and tilt with the phone. A wallpaper made that way needs a change to the app, not an image. If you want one in your own build, a vector shape from your SVG can be reused: Compose's `PathParser` turns the `d="..."` string of an SVG `<path>` into a `Path` you draw in a scene. That is a developer task; for a still wallpaper the PNG route above gives the same look with no code.
