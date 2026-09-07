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
