# Backdrop (Uno's fork)

Kyant's [AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass) `backdrop` library, Apache 2.0, copied at commit
`eada9a8600142c1bd6a5c5fc143d06f599cdfaef` ("Bump to 1.0.0"), which replaced the `io.github.kyant0:backdrop:1.0.0` dependency.

Every file Uno changed says so at its top. The changes, all for speed:

- **`renderScale`** on `drawBackdrop`: the backdrop is recorded and filtered at that fraction of full size and drawn back up. Blur and lens read
  `BackdropEffectScope.renderScale`, so a surface looks the same at any scale. Meant for heavily blurred surfaces, where the detail is gone anyway.
- **`direct`** on `drawBackdrop`: the surface paints what is behind it itself, in `onDrawBehind`. No backdrop layer, render effect or offscreen pass.
- **`ImageLens`**: the library's refraction shader, drawn straight from one shared image with a plain paint. One compiled program serves every surface.

Also: `recordLayer` takes its node as a parameter, since Uno's Kotlin (2.1) has no context parameters; `lensCornerRadii` and `colorControlsColorFilter`
are public, for `ImageLens`'s callers.
