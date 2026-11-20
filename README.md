# Actua

**A native Android client for [Actual Budget](https://actualbudget.org/), built with Kotlin and Jetpack Compose.**

<p align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="artwork/actua-hero-dark.png">
    <img src="artwork/actua-hero.png" alt="Actua, a native Android client for Actual Budget: phones showing the budget plan view, accounts and spending reports" width="900">
  </picture>
</p>

<p align="center">
  <a href="https://play.google.com/store/apps/details?id=com.azimulkabir.actua"><img src="https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png" alt="Get it on Google Play" height="80"></a>
</p>

<p align="center">
  <a href="https://github.com/azimul-kabir/actua/releases/latest"><img src="https://img.shields.io/badge/Download-APK-6750A4?style=for-the-badge&logo=github&logoColor=white" alt="Download APK"></a>
  <a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/azimul-kabir/actua"><img src="https://img.shields.io/badge/Get_it_on-Obtainium-6750A4?style=for-the-badge&logo=android&logoColor=white" alt="Get it on Obtainium"></a>
  <a href="https://actua.azimulkabir.com"><img src="https://img.shields.io/badge/Website-actua.azimulkabir.com-4B18D1?style=for-the-badge&logo=googlechrome&logoColor=white" alt="Website"></a>
  <a href="https://discord.gg/FhedEyP97f"><img src="https://img.shields.io/badge/Discord-Join-5865F2?style=for-the-badge&logo=discord&logoColor=white" alt="Join Discord"></a>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Android-9%2B-3DDC84?logo=android&logoColor=white" alt="Android 9+">
  <img src="https://img.shields.io/badge/Kotlin-Jetpack_Compose-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin and Jetpack Compose">
  <img src="https://img.shields.io/badge/License-GPL_v3-48506A" alt="GPLv3 license">
  <a href="https://www.azimulkabir.com"><img src="https://img.shields.io/badge/Developer-azimulkabir.com-48506A" alt="Developer website"></a>
</p>

## About

Actua brings Actual Budget to Android with a native Material You interface.

It connects directly to your self-hosted Actual Budget server, keeps a local copy of your budget for offline use, and synchronizes changes with Actual. You can also use Actua completely offline with standalone on-device budgets—no server required.

Actua is an independent community project and is not affiliated with or endorsed by Actual Budget.

## Features

- Native Material You interface in English, Brazilian Portuguese and Spanish
- Connect to your Actual server (password or OpenID) with offline budgets and encrypted sync, or use standalone on-device budgets with no server
- Envelope and tracking budgets with targets, `#template` automations, one-tap cover for overspent categories and Actual's budget actions
- Transactions with splits, transfers, merging and reconciliation, plus rules and automatic categorization as in Actual
- Receipt scanning on your phone with Google ML Kit, in every build
- Bank sync through your Actual server, and imports from CSV, OFX, QFX, QIF, CAMT.053, XLSX, PDF, SMS and notifications, including a Tasker plugin
- Scheduled transactions, a Bills calendar with reminders, credit cards, and loans with a payoff planner
- Dashboards and custom reports with a report builder and tap-through charts
- A customizable Home, tab bar and favorites, with home-screen widgets, launcher shortcuts and a Quick Settings tile
- App lock, hiding Actua from recents, and encrypted backups to your device, Google Drive or WebDAV
- Optional cosmetic Supporter extras (app icons, accent colors, Catppuccin themes, widget styles); budgeting always stays free
- A built-in demo budget and a privacy-safe Diagnostics report

See the [full feature list](docs/FEATURES.md), and [BACKEND_PARITY.md](BACKEND_PARITY.md) for detailed compatibility and implementation status. Actua is reviewed against Actual Budget 26.11, as of 2.4.0 ([details](docs/ACTUAL_26_11_COMPATIBILITY.md)).

## Watch Actua

<p align="center">
  <a href="artwork/actua-promo.mp4">
    <img src="artwork/actua-promo-poster.png" alt="Play the 30-second Actua video: budget plan and table views, accounts and loans, the payoff planner, credit cards, reports and widgets" width="640">
  </a>
</p>

A 30-second tour of the budget, accounts and loans, credit cards, reports and widgets, on a demo budget. Select the picture to play it.

## Screenshots

<table>
  <tr>
    <td align="center" width="25%">
      <a href="artwork/screenshots/light/budget.png">
        <picture>
          <source media="(prefers-color-scheme: dark)" srcset="artwork/screenshots/dark/budget.png">
          <img src="artwork/screenshots/light/budget.png" width="200" alt="Actua budget plan view with Ready to Budget, category targets and progress">
        </picture>
      </a>
    </td>
    <td align="center" width="25%">
      <a href="artwork/screenshots/light/budget-table.png">
        <picture>
          <source media="(prefers-color-scheme: dark)" srcset="artwork/screenshots/dark/budget-table.png">
          <img src="artwork/screenshots/light/budget-table.png" width="200" alt="Actua budget table view with budgeted, spent and balance columns">
        </picture>
      </a>
    </td>
    <td align="center" width="25%">
      <a href="artwork/screenshots/light/home.png">
        <picture>
          <source media="(prefers-color-scheme: dark)" srcset="artwork/screenshots/dark/home.png">
          <img src="artwork/screenshots/light/home.png" width="200" alt="Actua home dashboard with favorites and upcoming bills">
        </picture>
      </a>
    </td>
    <td align="center" width="25%">
      <a href="artwork/screenshots/light/accounts.png">
        <picture>
          <source media="(prefers-color-scheme: dark)" srcset="artwork/screenshots/dark/accounts.png">
          <img src="artwork/screenshots/light/accounts.png" width="200" alt="Actua accounts overview with favorites and group totals">
        </picture>
      </a>
    </td>
  </tr>
  <tr>
    <td align="center" width="25%">
      <a href="artwork/screenshots/light/transactions.png">
        <picture>
          <source media="(prefers-color-scheme: dark)" srcset="artwork/screenshots/dark/transactions.png">
          <img src="artwork/screenshots/light/transactions.png" width="200" alt="Actua transaction list with splits, cleared and reconciled states">
        </picture>
      </a>
    </td>
    <td align="center" width="25%">
      <a href="artwork/screenshots/light/add-transaction.png">
        <picture>
          <source media="(prefers-color-scheme: dark)" srcset="artwork/screenshots/dark/add-transaction.png">
          <img src="artwork/screenshots/light/add-transaction.png" width="200" alt="Actua add transaction screen">
        </picture>
      </a>
    </td>
    <td align="center" width="25%">
      <a href="artwork/screenshots/light/reports.png">
        <picture>
          <source media="(prefers-color-scheme: dark)" srcset="artwork/screenshots/dark/reports.png">
          <img src="artwork/screenshots/light/reports.png" width="200" alt="Actua reports dashboard with a spending-by-category donut chart">
        </picture>
      </a>
    </td>
    <td align="center" width="25%">
      <a href="artwork/screenshots/light/net-worth.png">
        <picture>
          <source media="(prefers-color-scheme: dark)" srcset="artwork/screenshots/dark/net-worth.png">
          <img src="artwork/screenshots/light/net-worth.png" width="200" alt="Actua net worth and cash flow reports">
        </picture>
      </a>
    </td>
  </tr>
  <tr>
    <td align="center" width="25%">
      <a href="artwork/screenshots/light/credit-card.png">
        <picture>
          <source media="(prefers-color-scheme: dark)" srcset="artwork/screenshots/dark/credit-card.png">
          <img src="artwork/screenshots/light/credit-card.png" width="200" alt="Actua credit card with billing cycle and statement balance">
        </picture>
      </a>
    </td>
    <td align="center" width="25%">
      <a href="artwork/screenshots/light/bills-calendar.png">
        <picture>
          <source media="(prefers-color-scheme: dark)" srcset="artwork/screenshots/dark/bills-calendar.png">
          <img src="artwork/screenshots/light/bills-calendar.png" width="200" alt="Actua bills calendar with upcoming scheduled transactions">
        </picture>
      </a>
    </td>
    <td align="center" width="25%">
      <a href="artwork/screenshots/light/rules.png">
        <picture>
          <source media="(prefers-color-scheme: dark)" srcset="artwork/screenshots/dark/rules.png">
          <img src="artwork/screenshots/light/rules.png" width="200" alt="Actua rules for automatic categorization">
        </picture>
      </a>
    </td>
    <td align="center" width="25%">
      <a href="artwork/screenshots/light/widgets.png">
        <picture>
          <source media="(prefers-color-scheme: dark)" srcset="artwork/screenshots/dark/widgets.png">
          <img src="artwork/screenshots/light/widgets.png" width="200" alt="Actua home-screen widgets: budget snapshot, favourite categories, account balances, upcoming schedules, spending charts and quick actions">
        </picture>
      </a>
    </td>
  </tr>
</table>

Screenshots use a demo budget and follow your GitHub light or dark theme.

### Supporter extras

Optional and cosmetic, unlockable in the Google Play version for now (the GitHub APKs show them locked until website purchases open). Under Settings → Supporter extras, everyone can preview every app icon, accent color, Catppuccin theme and widget style, with the locked ones marked "Supporter". Budgeting always stays free, and Violet and Material You accents are free for everyone.

<table>
  <tr>
    <td align="center" width="25%">
      <a href="artwork/screenshots/light/accent-colors.png">
        <picture>
          <source media="(prefers-color-scheme: dark)" srcset="artwork/screenshots/dark/accent-colors.png">
          <img src="artwork/screenshots/light/accent-colors.png" width="200" alt="Actua accent color picker with Violet, Teal, Amber, Rose, Forest, Ocean and Mono">
        </picture>
      </a>
    </td>
    <td align="center" width="25%">
      <a href="artwork/screenshots/light/catppuccin.png">
        <picture>
          <source media="(prefers-color-scheme: dark)" srcset="artwork/screenshots/dark/catppuccin.png">
          <img src="artwork/screenshots/light/catppuccin.png" width="200" alt="Actua Catppuccin theme picker with the Latte, Frappé, Macchiato and Mocha flavors and 14 accents">
        </picture>
      </a>
    </td>
    <td align="center" width="25%">
      <a href="artwork/screenshots/light/app-icon.png">
        <picture>
          <source media="(prefers-color-scheme: dark)" srcset="artwork/screenshots/dark/app-icon.png">
          <img src="artwork/screenshots/light/app-icon.png" width="200" alt="Actua app icon picker with Default, Dark, Mono and Light">
        </picture>
      </a>
    </td>
    <td align="center" width="25%">
      <a href="artwork/screenshots/light/widget-styles.png">
        <picture>
          <source media="(prefers-color-scheme: dark)" srcset="artwork/screenshots/dark/widget-styles.png">
          <img src="artwork/screenshots/light/widget-styles.png" width="200" alt="Actua widget style picker with Default, Translucent, Solid dark, Solid light and Accent-tinted">
        </picture>
      </a>
    </td>
  </tr>
</table>

## Try Actua

Actua requires **Android 9 or newer**.

**Recommended: [Get Actua on Google Play](https://play.google.com/store/apps/details?id=com.azimulkabir.actua).** You get automatic updates, receipt scanning and optional Support Actua purchases, which unlock cosmetic Supporter extras (budgeting always stays free).

Or install from [GitHub Releases](https://github.com/azimul-kabir/actua/releases):

- `Actua-v<version>.apk`: the Direct Full build, with receipt scanning. Website purchases are not open yet.
- `Actua-v<version>-full.apk`: the Google Play build, for devices without the Play Store.
- [Obtainium](https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/azimul-kabir/actua): update notifications and in-place upgrades. Use its APK filter (for example `-full`) to pick one APK.

The two GitHub APKs update each other. The Google Play version is signed with a different key, so neither can be installed over the other. To switch between Play and GitHub, back up your budget (an archive, WebDAV or Google Drive), uninstall, install the other build and restore; supporter status bought on Google Play stays with the Play version. See [docs/DIRECT_FULL.md](docs/DIRECT_FULL.md).

Don't have an Actual server handy? Open **Manage → Connection & Data → Try demo budget** to explore Actua using a completely local sample budget.

> [!CAUTION]
> Actua can write changes to your synchronized Actual budget. Back up important budgets before connecting a new client.

## Credits

Actua was originally based on and informed by [Actuali](https://github.com/MattFaz/actuali), the open-source iOS client by Matt Farrell.

The project aims for compatibility with [Actual Budget](https://actualbudget.org/) while providing an independently implemented native Android experience.

## Community

Found a bug or have an idea? [Open an issue](https://github.com/azimul-kabir/actua/issues).

Want to discuss Actua, test beta builds, or help with development? [Join the Discord server](https://discord.gg/FhedEyP97f).

## Privacy and terms

See the [Privacy Policy](PRIVACY.md) and the [Terms of Service](TERMS.md), which also cover the optional Support Actua purchases in the Google Play version. Both are published on the [website](https://actua.azimulkabir.com/).

## License

Actua is free software licensed under the [GNU General Public License v3.0](LICENSE).
