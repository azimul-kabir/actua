# App lock

Optional device-local lock (#1338, slices #1340 and #1341) that asks for a fingerprint, face, or the device PIN,
pattern or password before Actua shows any budget data. **Settings → Privacy → App lock.**

## Behavior

- Off by default. Turning it **on or off requires authenticating first**, so a borrowed unlocked phone
  can neither disable the lock nor enrol it on the owner's behalf. It can't be turned on when the
  device has no screen lock (the setting says to set one up first).
- Authenticators: a strong biometric or the device credential on Android 11+; a weak biometric or the
  device credential on Android 9–10, where the platform can't combine a strong biometric with the
  credential. Uses `androidx.biometric` only (no Play Services), so it works on devices without Google Play.
- **Relock timeout** (Immediately, 1, 5 or 15 minutes after leaving Actua; default Immediately). An
  unreadable stored value falls back to Immediately, never to "off".
- A **new process always starts locked**, so an app that was killed and restored asks again. A
  rotation or theme change does not relock; only leaving the app does.
- While locked, the app content is **not composed at all**: nothing is drawn, held in the view
  hierarchy or exposed to accessibility services behind the lock screen. Back on the lock screen
  closes the app.
- If the device loses its screen lock after the app lock was turned on, Actua **stays locked** and
  offers a shortcut to the security settings, rather than opening for whoever holds the phone.
- Background work (sync, scheduled posting, reminders, bank-sync and capture jobs) is not affected: it
  never displays budget data.

## Entry points

Every way into Actua that shows or changes budget data asks to unlock first and then continues to
where it was going. While locked, nothing of the destination is composed; the request waits and is
carried out after a successful unlock.

| Entry point | Behavior while locked |
| --- | --- |
| Launcher, app shortcuts, Quick Settings **Add transaction** tile | Open `MainActivity`: lock screen first, then the screen asked for. From the device lock screen the tile also asks for the device unlock before Actua opens. |
| Widget taps (balances, categories, accounts, schedules, reports, **Add** buttons) | Open `MainActivity` with the widget's action: lock screen first, then the same category, account, report or add-transaction screen. |
| Captured-transaction, schedule and card-reminder notification taps | Open `MainActivity`: lock screen first, then the review, schedule or account screen. |
| Share sheet (text, statement files) and "Open with" a statement file | Open `MainActivity`: lock screen first, then the import starts. The file or text is held, not read, until you unlock. |
| Widget setup screens (Favourite categories, Account balances, Single item, Upcoming schedules, Widget look) | Show the lock screen; the setup screen, which lists budget names, is not composed until you unlock. |
| Tasker / Locale **Queue transaction** edit screen | Shows the lock screen; the form (which reads account names) is not composed or loaded until you unlock. |
| Disconnect confirmation | Shows the lock screen before the confirmation. |
| Google Drive sign-in redirect | Shows nothing and forwards to `MainActivity`, which is locked. |

A test (`AppLockEntryPointsTest`) fails if an exported activity is added that is neither
`MainActivity` nor an `AppLockActivity`.

## Hide widget amounts

A widget draws outside Actua and cannot ask for a fingerprint, so while the lock is on it would
otherwise keep showing balances. **Settings → Privacy → App lock → Hide widget amounts** (off by
default, shown only while the lock is on) replaces every amount on every widget with `••••`,
leaves the figures out of the screen-reader description, and drops the savings rate. It is the same
treatment as the Hide balances setting, and it is applied alongside it. Account and category names,
schedule titles and chart shapes stay visible. Turning the option or the lock on or off redraws the
widgets right away. If you skip the option, widgets keep showing balances.

## Background capture keeps working

The notification listener, Tasker/MacroDroid broadcasts (`QUEUE_TRANSACTION`, Tasker fire) and the
capture notifications they post run without a screen, so the lock does not touch them: transactions
keep queueing while Actua is locked, nothing is dropped, and you review them after unlocking.
Capture notifications already hide amounts on a secure lock screen.

## What it does not cover yet

- Widget names and notification text are not hidden by the lock; only widget amounts can be (above).
- The recents screenshot is taken when the app goes to the background, **before** the lock engages.
  Turn on **Hide app contents in recents** (Settings → Privacy) to keep balances out of recents.
- The lock is a convenience barrier on an otherwise unlocked phone. It does not encrypt the budget
  database or change how credentials are stored in the Android Keystore.
