# Actua attribution notice

## Actuali for iOS

Actua began as an independent Android reimplementation based on
[Matt Farrell's Actuali for iOS](https://github.com/MattFaz/actuali), which
continues to serve as an important behavioral and implementation reference.

The original project provided the behavioral reference for the budget, account, transaction, synchronization, backup, rules, schedules, reports, settings, and credit-card workflows implemented here. Its source is licensed under the MIT License, copyright 2025–2026 Matt Farrell.

Actua is maintained independently. It is not an official release of, or
endorsed or supported by, the original Actuali iOS project.

## Actual Budget

Actua is a native Android client for [Actual Budget](https://actualbudget.org/)
and includes a Kotlin implementation of behavior derived from [Actual Budget's
open-source repository](https://github.com/actualbudget/actual), particularly
its CRDT synchronization and loot-core packages.

Those portions are MIT-licensed and retain the copyright notice for James Long. Actual Budget is a separate project and does not endorse or support this app.

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

## Trademarks and support

Project names and logos remain the property of their respective owners. Issues
specific to Actua should be filed in the [Actua repository](https://github.com/azimul-kabir/actua),
not in the upstream iOS or Actual Budget issue trackers.
