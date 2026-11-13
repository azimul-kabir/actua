# Actua attribution notice

## Actuali for iOS

Actua began as an independent Android reimplementation based on
[Matt Farrell's Actuali for iOS](https://github.com/MattFaz/actuali), which
continues to serve as an important behavioral and implementation reference.

The original project provided the behavioral reference for the budget, account, transaction, synchronization, backup, rules, schedules, reports, settings, and credit-card workflows implemented here. Its source is licensed under the MIT License, copyright 2025–2026 Matt Farrell.

Actua itself is licensed under the [GNU General Public License v3.0](LICENSE).
The MIT-licensed upstream code, migrations and artwork credited in this notice
keep their original copyright notices.

Actua is maintained independently. It is not an official release of, or
endorsed or supported by, the original Actuali iOS project.

## Actual Budget

Actua is a native Android client for [Actual Budget](https://actualbudget.org/)
and includes a Kotlin implementation of behavior derived from [Actual Budget's
open-source repository](https://github.com/actualbudget/actual), particularly
its CRDT synchronization and loot-core packages.

Those portions are MIT-licensed and retain the copyright notice for James Long. Actual Budget is a separate project and does not endorse or support this app.

`app/src/main/resources/actual-migrations/` contains verbatim copies of Actual Budget's SQL
migrations from `packages/loot-core/migrations`, used to upgrade budgets last saved by an older
Actual. They are covered by the same MIT license and copyright notice.

## Catppuccin

The Catppuccin themes under Settings → Supporter extras are based on the
[Catppuccin](https://github.com/catppuccin/catppuccin) palette. Actua is not an
official Catppuccin port and is not endorsed by Catppuccin. The color values come
from the official palette source, [catppuccin/palette](https://github.com/catppuccin/palette)
(`palette.json`, version 1.8.0, commit `07d02aa110ef9eb7e7427afca5c73ba9cf7f8ebd`),
and are copied unchanged into `ui/theme/CatppuccinPalette.kt`.

Where a pair of colors needs more contrast, Actua changes the text color or the tone of
the container, never the named Catppuccin color shown as the accent:

- Text on the accent colors uses Catppuccin's Base or Crust where that reads at WCAG
  AA (4.5:1), and black or white where it does not. That affects 12 of Latte's 14
  accents (all but Mauve and Red).
- The lightest card surface in some flavors is eased towards a neighboring Surface or
  Crust color so that Text stays readable on it, and containers tinted with an accent
  are eased towards Base for the same reason.
- With the Red or Maroon accent, the error color takes a different lightness from the
  accent so that errors do not look like ordinary controls.

Catppuccin is licensed under the MIT License:

> MIT License
>
> Copyright (c) 2021 Catppuccin
>
> Permission is hereby granted, free of charge, to any person obtaining a copy
> of this software and associated documentation files (the "Software"), to deal
> in the Software without restriction, including without limitation the rights
> to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
> copies of the Software, and to permit persons to whom the Software is
> furnished to do so, subject to the following conditions:
>
> The above copyright notice and this permission notice shall be included in all
> copies or substantial portions of the Software.
>
> THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
> IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
> FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
> AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
> LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
> OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
> SOFTWARE.

## Artwork

The original Actuali app icon was designed by
[u/bdownz](https://www.reddit.com/user/bdownz/). Actua's ribbon-and-envelope
icon is a newly rendered treatment closely based on that original design.
The upstream artwork and design contribution remain credited under the
Actuali project's MIT license.

## Design references

Parts of Actua's mobile interaction design were inspired by publicly visible
patterns in [YNAB](https://www.ynab.com/), including aspects of transaction
entry, amount controls, and compact budgeting presentation. These interfaces
were independently designed and implemented in Kotlin and Jetpack Compose for
Actua's Material You interface and Actual Budget data model.

Actua does not include YNAB source code, artwork, logos, or other assets. YNAB
is a separate product and trademark of its respective owner and does not
endorse, sponsor, or support Actua.

## PDFBox Android

Local text extraction from PDF statements uses
[PdfBox-Android](https://github.com/TomRoush/PdfBox-Android), an Android port of Apache PDFBox.
PdfBox-Android and Apache PDFBox are licensed under the Apache License 2.0.

## OkHttp

WebDAV backups use [OkHttp](https://square.github.io/okhttp/), which is licensed under the Apache
License 2.0 and is not modified. It is needed because Android's built-in `HttpURLConnection` rejects the
WebDAV methods PROPFIND, MKCOL and MOVE.

## Google ML Kit

The Google Play version of Actua uses [Google ML Kit](https://developers.google.com/ml-kit) text recognition
(bundled Latin, Chinese, Japanese, Korean and Devanagari models, on-device) to read receipt photos. It is a proprietary dependency and is included in the
`playstore` flavor only; the `fdroid` flavor, which GitHub Releases and F-Droid ship, does not contain it.

## Trademarks and support

Project names and logos remain the property of their respective owners. Issues
specific to Actua should be filed in the [Actua repository](https://github.com/azimul-kabir/actua),
not in the upstream iOS or Actual Budget issue trackers.
