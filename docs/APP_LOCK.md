# App lock

Optional device-local lock (#1338, slice #1340) that asks for a fingerprint, face, or the device PIN,
pattern or password before Actua shows any budget data. **Settings → Privacy → App lock.**

## Behavior

- Off by default. Turning it **on or off requires authenticating first**, so a borrowed unlocked phone
  can neither disable the lock nor enrol it on the owner's behalf. It can't be turned on when the
  device has no screen lock (the setting says to set one up first).
- Authenticators: a strong biometric or the device credential on Android 11+; a weak biometric or the
  device credential on Android 9–10, where the platform can't combine a strong biometric with the
  credential. Uses `androidx.biometric` only (no Play Services), so it works in the F-Droid build.
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

## What it does not cover yet

- Only `MainActivity` is gated. Other exported entry points (widget configuration, Tasker edit, the
  disconnect confirmation) and widget contents are covered by
  [#1341](https://github.com/azimul-kabir/actua/issues/1341).
- The recents screenshot is taken when the app goes to the background, **before** the lock engages.
  Turn on **Hide app contents in recents** (Settings → Privacy) to keep balances out of recents.
- The lock is a convenience barrier on an otherwise unlocked phone. It does not encrypt the budget
  database or change how credentials are stored in the Android Keystore.
