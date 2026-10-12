# Transaction imports

Actua imports bank data through reviewable transaction candidates. Parsing never writes directly to
the budget. Only rows selected on the review screen are committed, together, through the normal
Actual-compatible CRDT transaction writer.

How bank sync and file import compare with Actual Budget, row by row, is tracked in
[BANK_SYNC_PARITY.md](BANK_SYNC_PARITY.md).

## CSV statements

The file picker accepts local CSV or plain-text files. Processing stays on the device. Actua detects
comma, semicolon, and tab delimiters and suggests roles for common headers. Before importing, users
can map each column to Date, Payee, Notes, Reference, Amount, Debit, Credit, or Ignore; choose a date
format; and indicate whether a single amount column lists expenses as positive numbers. Separate
debit and credit columns always retain their normal debit-negative, credit-positive meaning.

Mappings can be saved as named device-local profiles. A profile is loaded only when the user chooses
it and the statement has the same number of columns. Profiles do not contain statement rows or budget
data.

## Review, duplicates, and history

Malformed rows are excluded and reported. Candidate rows remain editable. A row whose normalized
date, amount, and payee repeat an earlier row in the same file starts unchecked and is labelled
"Repeated in this file"; a user may deliberately select it, because some banks legitimately repeat
equal purchases.

As in Actual, a row with the same amount as an existing transaction in the selected account dated
within seven days (a matching payee is preferred) is shown with that match, and importing updates
the existing transaction instead of adding a copy. Tick **Add as a new transaction** to import it
separately; a match that is reconciled is left unchanged. Imported payee names are title-cased, and
**Mark as cleared** (on by default) sets whether imported rows arrive cleared.

Captured notifications and automation broadcasts can belong to different accounts, so each queued
row has its own **Account**, pre-filled when its account hint names one (an exact account name, or
digits that appear in exactly one account's name). Every ticked row that has an account is imported
together in one batch, each matched against its own account; ticked rows without an account stay
queued until one is chosen. Statement files and pasted text import into the single **Import into**
account.

After approval, Actua stores a bounded device-local history of the latest 20 imports: source file
name, format, target account name, imported/skipped counts, and time. Raw rows, messages, balances,
and statement files are not retained. History can be cleared from the import screen.

## XLSX and PDF statements

Actua reads the first XLSX worksheet directly from the local OOXML archive, including shared and
inline strings, sparse columns, and Excel serial dates. It locates a likely header row within the
first 25 rows so common statement preambles do not need to be removed manually.

Text-based PDFs are extracted locally and accepted only when a recognizable multi-column table is
present. Scanned/image-only PDFs and layouts whose text cannot be separated reliably are rejected
with a CSV/XLSX recommendation. Actua does not use OCR or guess transaction boundaries. Files are
limited to 25 MB, and expanded XLSX worksheet parts are bounded to reduce malformed-archive risk.

## SMS, email, and app notifications

SMS or email transaction alerts can be pasted into the import screen or shared to Actua through
Android's text share sheet. Actua requests neither SMS access nor mailbox access. The parser requires
explicit debit/credit wording and a labeled or currency-prefixed amount, then extracts the date,
payee, reference, and account/card suffix when available. Every result remains review-only.

Users may optionally enable Android notification-listener access for future transaction alerts.
This is a system-level sensitive permission and is never enabled silently. Actua ignores messages
from apps the user has not explicitly selected, ignores messages that do not match the financial
parser, and stores at most 100 normalized candidates. An empty allowed-app list captures nothing. Raw notification
text is not retained. Captured data, parser settings, and capture state can be deleted together.

Debit and credit keyword sets are configurable for different bank wording. Confidence is high when
both a payee and reference are found, medium when a payee is found, and low when the source must be
used as the payee. An unambiguous card/account suffix can select an Actual account whose name contains
the same digits. Category assignment is not guessed from merchant text; Actual rules run on every
imported row, as they do in Actual's import, and can categorize or rename it.

## Tasker and other automation apps

Automation apps such as Tasker, MacroDroid, or Automate can do their own notification parsing and
hand Actua the result. This helps with apps that post alerts for many cards in changing formats,
such as Google Pay. The integration is off by default. Turning on **Accept transactions from Tasker**
on the import screen creates a random per-install token, which can be copied or replaced there.

### Tasker plugin (recommended)

In a Tasker task, add *Plugin → Actua → Queue transaction*. The form has a field for every value
below, and each field accepts Tasker variables such as `%amount` or `-%amount`. The token is filled
in for you, and the form offers to turn the integration on if it is off. **Account** has a list of
the open accounts whose names select them on review; typing a name, digits or a variable still works. Currency symbols are
removed from the amount, and a variable that is still unset when the action runs (a literal
`%payee`) counts as empty. Tasker waits for the result: if Actua ignores the transaction, the action
fails and `%errmsg` holds the same reason the import screen shows. Replacing the token in Actua stops
saved actions until each is opened and saved again.

### Broadcast intent

Other automation apps, or Tasker without the plugin, send a broadcast (Tasker: *Send Intent*,
Target *Broadcast Receiver*) with:

- Action: `com.azimulkabir.actua.action.QUEUE_TRANSACTION`
- Package: `com.azimulkabir.actua`
- Extras (Tasker's `key:value` form):

| Extra | Required | Meaning |
|---|---|---|
| `token` | yes | The token shown on the import screen |
| `amount` | yes, unless `text` is sent | Decimal amount with at most two decimals, e.g. `-12.34`. Negative is an outflow. Thousands commas are ignored. |
| `type` | no | `debit` or `credit`; overrides the sign of `amount` |
| `payee` | no | Defaults to the source label |
| `date` | no | `YYYY-MM-DD`; defaults to today |
| `notes` | no | Transaction notes |
| `reference` | no | Bank reference or transaction ID |
| `account` | no | Account to select on review: an exact account name (case-insensitive), or digits such as the last card digits that appear in exactly one account's name |
| `source` | no | Label shown during review; defaults to `Tasker` |
| `text` | no | Raw alert text to run through Actua's own message parser instead of `amount` |

Example Tasker extras: `token:%ACTUA_TOKEN`, `amount:-%amount`, `payee:%merchant`,
`source:Tasker: GPay`.

### Step by step with Tasker's Send Intent

1. In Actua, open **Import transactions**, turn on **Accept transactions from Tasker**, and tap
   **Token** to copy it. In Tasker, store it in a global variable such as `%ACTUA_TOKEN`.
2. Create a profile that fires on the bank app's notification (*Event → UI → Notification*, or an
   AutoNotification intercept) and set variables for the amount and payee from its title and text.
   Remove currency symbols from the amount: `$25.00` is rejected, `1,234.56` is accepted.
3. Add the task action *System → Send Intent* and fill in:

   | Field | Value |
   |---|---|
   | Action | `com.azimulkabir.actua.action.QUEUE_TRANSACTION` |
   | Cat | None |
   | Mime Type, Data | empty |
   | Extra | `token:%ACTUA_TOKEN` |
   | Extra | `amount:-%amount` |
   | Extra | `payee:%payee` |
   | Package | `com.azimulkabir.actua` |
   | Class | empty |
   | Target | **Broadcast Receiver** |

4. Run the task, then open the import screen again and tap **Review captured**.

Each Extra must start with its key and a colon. `%amount` on its own sends no `amount` extra, and a
broadcast without `token:` is ignored. When a broadcast is ignored, the import screen shows
**Last broadcast ignored** with the reason, such as a missing or wrong token or an invalid amount.
The reason and time are kept, never the values sent; it clears after the next accepted broadcast,
a new token, turning the integration off, or deleting notification data.

- **Credit cards:** `amount:-%amount` turns a charge shown as `25.00` into an outflow. Use
  `amount:%amount` where the notification already has the right sign, or send `type:debit`.
- **Two accounts in one app:** create one profile per notification title, each with its own Send
  Intent, and send `account:` with the exact Actual account name, e.g. `account:Amex Gold`. Both
  accounts' rows can then be imported together.
- **Fewer Extra fields:** if your Tasker version shows only three Extra fields, use them for
  `token`, `amount` and `payee`, or send the broadcast from a *Java Code* action instead.

### Sending every extra with Tasker's Java Code action

Tasker's *Code → Java Code* action can send all extras in one step, without the Extra field limit.
Set local variables such as `%amount`, `%payee` or `%account` earlier in the task; unset or empty
ones are skipped. The token is read from the global `%ACTUA_TOKEN`.

```java
import android.content.Intent;

intent = new Intent("com.azimulkabir.actua.action.QUEUE_TRANSACTION");
intent.setPackage("com.azimulkabir.actua");
intent.putExtra("token", tasker.getVariable("ACTUA_TOKEN"));

/* Tasker variable names without the % prefix. */
String[] keys = { "amount", "type", "payee", "date", "notes", "reference", "account", "source", "text" };
for (int i = 0; i < keys.length; i++) {
    String value = tasker.getVariable(keys[i]);
    /* Keep digits, minus, comma and point, so "$ -12.34" is sent as "-12.34". */
    if (value != null && keys[i].equals("amount")) value = value.replaceAll("[^0-9.,-]", "");
    if (value != null && value.length() > 0) intent.putExtra(keys[i], value);
}
context.sendBroadcast(intent);
```

The cleanup keeps commas as thousands separators, so amounts must use a point for decimals.

Accepted broadcasts join the same bounded review queue as captured notifications and are reviewed
and imported the same way. Nothing is written to the budget until the user imports it. Broadcasts
are ignored when the integration is off, the token is missing or wrong, or the fields are invalid,
and the import screen names the reason.
Turning the integration off, replacing the token, or deleting notification data stops old senders.
