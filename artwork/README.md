# Actua icon

`actua-icon.svg` is the canonical Actua Fold A artwork.
`actua-icon.png` is its high-resolution raster preview and is used by the repository README.
`actua-play-store.png` is its 512 × 512 Play Store export.

The Android adaptive icon is split into background, foreground and monochrome
drawables under `app/src/main/res`. The foreground and monochrome layers use
native Android vector paths derived directly from the canonical SVG, while the
background remains solid violet. Material You recolors the matching monochrome
geometry on Android 13 and later. Keep the foreground inside the adaptive-icon
safe zone.

# Hero and screenshots

`actua-hero.png` and `actua-hero-dark.png` are the README hero for GitHub's light
and dark themes. `screenshots/light/` and `screenshots/dark/` hold framed 720 × 1280
screenshots of the same screens in each theme. All of them were captured from the
built-in demo budget (Manage → Connection & Data → Try demo budget), so they contain
no real financial data. Recapture them when the UI changes noticeably.
