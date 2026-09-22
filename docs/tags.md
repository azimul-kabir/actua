# Tags in Actua

Actua uses Actual Budget's canonical tag metadata and keeps tag behavior compatible with Actual rather than maintaining a separate Android-only tag store.

## Notes and autocomplete

In a transaction note, type `#` to search managed tags. Suggestions update as the active hashtag token changes. Hidden tags are excluded from autocomplete. `##` is treated as a literal hash escape and is not a tag token. Recognized `#tag` tokens are colored in the notes field as you type, matching the tag's color once the transaction is saved.

Tag discovery is case-insensitive, while canonical rename follows Actual's case-sensitive rename semantics. A tag filter matches an exact hashtag token, so `#travel` does not match `#travel2026`.

## Manage Tags

Open **Manage → Tags** to create, edit, recolor, describe, hide/show, rename, or delete managed tags. Renaming a managed tag also rewrites matching transaction-note hashtags through Actua's CRDT transaction writer. Deleting tag metadata intentionally leaves historical hashtag text in notes as unmanaged text, matching Actual behavior.

Tap a managed tag, or use its actions menu and choose **View transactions**, to see transactions containing that exact tag. The active tag is shown as a removable filter chip. Parent and split notes are both considered.

## Sync and offline behavior

Tag metadata and transaction results are read from the active local Actual budget, so tag management and discovery remain available offline. Mutations use Actua's normal Actual-compatible mutation path and schedule synchronization. The managed-tag transaction view observes sync data generation and refreshes after new Actual data is applied.

Changes made by another Actual client become visible after the next successful sync. Existing transaction editors keep their current in-progress text until they are closed or saved; tag discovery reads the latest committed local budget state.

## Compatibility notes

Tag names cannot contain whitespace or `#`. Managed and unmanaged hashtags can coexist in notes. Filtering does not require a hashtag to have managed metadata, but Manage Tags exposes discovery from canonical managed tags. Long tag collections can be searched from Manage Tags before opening transaction discovery.
