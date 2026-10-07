# Transaction imports

Actua imports bank data through reviewable transaction candidates. Parsing never writes directly to
the budget. Only rows selected on the review screen are committed, together, through the normal
Actual-compatible CRDT transaction writer.

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

Malformed rows are excluded and reported. Candidate rows remain editable and unchecked by default
when the normalized date, amount, and payee match either an existing transaction in the selected
account or an earlier row in the same file. The review screen explains which case was detected. A
user may deliberately select a flagged row, because some banks legitimately repeat equal purchases.

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

Send a broadcast (Tasker: *Send Intent*, Target *Broadcast Receiver*) with:

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
| `account` | no | Account hint, such as the last card digits |
| `source` | no | Label shown during review; defaults to `Tasker` |
| `text` | no | Raw alert text to run through Actua's own message parser instead of `amount` |

Example Tasker extras: `token:%ACTUA_TOKEN`, `amount:-%amount`, `payee:%merchant`,
`source:Tasker: GPay`.

Accepted broadcasts join the same bounded review queue as captured notifications and are reviewed
and imported the same way. Nothing is written to the budget until the user imports it. Broadcasts
are ignored when the integration is off, the token is missing or wrong, or the fields are invalid.
Turning the integration off, replacing the token, or deleting notification data stops old senders.
