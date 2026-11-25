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
and dark themes. They carry no buttons or calls to action, since an image can't be tapped; the
README's real Google Play badge sits below them. `screenshots/light/` and `screenshots/dark/` hold framed 720 × 1280
screenshots of the same screens in each theme. All of them were captured from the
built-in demo budget (Manage → Connection & Data → Try demo budget), so they contain
no real financial data. The receipt screens use a synthetic receipt image. Recapture them when
the UI changes noticeably.

They were last recaptured on 25 Nov 2026 on a 1080 × 2400 display with Android's demo status
bar (9:41, full battery and Wi-Fi), then framed at 720 × 1280. `widgets.png` (a home-screen
widget showcase) and `supporter-extras.png` (a drawn infographic) were not part of that
refresh.

`actua-promo-poster.png` is the poster frame of the README and website promo video.

The Google Play listing uses eight of the light screenshots, copied to
`fastlane/metadata/android/en-US/images/phoneScreenshots/`, and `featureGraphic.png` there, a
1024 × 500 version of the light hero without its Google Play buttons (store graphics must not
carry calls to action). Run the **Google Play Listing Images** workflow after changing them.

# Promo images

`promo/` holds social and announcement images, each in a 16:9 and a square version at two sizes:
`supporter-extras-*` for the Supporter extras, and `release-2.8.0-*` for the 2.8.0 release. They are not used by the
app or the Play listing. The release images use the demo budget's icons and drawn illustrations, never real data.
