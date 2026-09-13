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
the same digits. Category assignment remains uncategorized rather than being guessed from merchant
text; users can apply Actual rules or categorize the reviewed transaction after import.
