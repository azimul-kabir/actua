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

XLSX/PDF statements and opt-in notification/share ingestion are tracked as the next two slices of
issue #65.
