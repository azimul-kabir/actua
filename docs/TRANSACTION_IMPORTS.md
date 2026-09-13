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

Opt-in notification/share ingestion remains the final slice of issue #65.
