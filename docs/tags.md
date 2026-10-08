# Tags in Actua

Actua uses Actual Budget's canonical tag metadata and keeps tag behavior compatible with Actual rather than maintaining a separate Android-only tag store.

## Notes and autocomplete

In a transaction note, type `#` to search managed tags. Suggestions update as the active hashtag token changes. Hidden tags are excluded from autocomplete. A `#` that follows another `#` never starts a tag, so `##tag` and `###tag` are plain text. Recognized `#tag` tokens are shown with the same colored pill in the notes field as you type, matching the tag's chip once the transaction is saved.

Tag matching follows Actual: viewing a tag's transactions, rename and report filters are case-sensitive, while rules ignore case. A tag filter matches an exact hashtag token, so `#travel` does not match `#travel2026`.

## Manage Tags

Open **Manage → Tags** to create, edit, recolor, describe, hide/show, rename, or delete managed tags. Renaming a managed tag also rewrites matching transaction-note hashtags through Actua's CRDT transaction writer. Deleting tag metadata intentionally leaves historical hashtag text in notes as unmanaged text, matching Actual behavior.

Tap a managed tag, or use its actions menu and choose **View transactions**, to see transactions containing that exact tag. The active tag is shown as a removable filter chip. Parent and split notes are both considered.

## Sync and offline behavior

Tag metadata and transaction results are read from the active local Actual budget, so tag management and discovery remain available offline. Mutations use Actua's normal Actual-compatible mutation path and schedule synchronization. The managed-tag transaction view observes sync data generation and refreshes after new Actual data is applied.

Changes made by another Actual client become visible after the next successful sync. Existing transaction editors keep their current in-progress text until they are closed or saved; tag discovery reads the latest committed local budget state.

## Compatibility notes

Tag names cannot contain whitespace or `#`. Managed and unmanaged hashtags can coexist in notes. Filtering does not require a hashtag to have managed metadata, but Manage Tags exposes discovery from canonical managed tags. Long tag collections can be searched from Manage Tags before opening transaction discovery.

## Parity with Actual

Audit of tags against Actual Budget, tracked in
[#672](https://github.com/azimul-kabir/actua/issues/672) (part of
[#658](https://github.com/azimul-kabir/actua/issues/658)).

- **Upstream reference:** `actualbudget/actual` at
  [`59fe126f`](https://github.com/actualbudget/actual/tree/59fe126f637d858c061e1eeedbef5436c8f2225a)
  (v26.9.0). `LC/` = `packages/loot-core/src/`, `DC/` = `packages/desktop-client/src/`.
- **Actua paths** are relative to `app/src/main/java/com/azimulkabir/actua/`.
- **Method:** the server handlers ([`LC/server/tags/app.ts`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/server/tags/app.ts)),
  [`LC/shared/tags.ts`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/shared/tags.ts),
  the `hasTags`/`hasAnyTag` rule and filter code, and `DC/notes/linkParser.ts` were compared with
  Actua's source. For tag parsing, Actual's own functions were run on an edge-case corpus, and the
  results are asserted in `app/src/test/java/com/azimulkabir/actua/data/budget/TagParityCorpusTest.kt`.
- **Status:** **Match**; **Intentional** = an Android-only difference that doesn't affect other
  clients; **Divergence** = filed as an issue; **N/A** = not offered in Actua.

### Data model and writes

| Item | Actual | Actua | Status |
| --- | --- | --- | --- |
| `tags(id, tag UNIQUE, color, description, tombstone, hidden)` | migrations `1749799110000`, `1749799110001`, `1780327681000` | same migrations and blank-budget schema; reads tolerate older budgets missing `hidden` or `tombstone` (`data/budget/TagMetadataStore.kt:50`) | Match |
| Writes go through CRDT messages | `db.insertTag`/`updateTag`/`delete_` | `ActualTagWriter` → `applyLocalMessages` | Match |
| Name: trimmed, no whitespace or `#`, not empty | `renameTag` check; `createTag` trims | `validateTagName` / `ActualTagWriter.validTag` | Match |
| Create a name that exists | updates the existing row, deleted or live, and clears `tombstone` | returns a live match; a deleted match is brought back on its own row (`ActualTagWriter.create`, `ActualBudgetDatabase.fetchTagRowByName`) | Match ([#983](https://github.com/azimul-kabir/actua/issues/983)) |
| Color on create | trimmed, `null` when empty; tags created from a note get `null` | trimmed, `null` when empty; tags created from a note or Label get `#690CB0` | **Divergence** ([#986](https://github.com/azimul-kabir/actua/issues/986)) |
| Description | stored as typed | blank stored as `null` | Intentional (other clients show an empty description either way) |
| Edit | writes the fields passed | writes name (if changed), color, description and hidden | Match |
| Hide / show | `hidden` 1/0; hidden tags left out of autocomplete | same; hidden tags left out of autocomplete (`ui/transactions/TagAutocomplete.kt`) | Match |
| Delete | tombstones the row; notes keep their `#tag` text | same | Match |
| Bulk delete, hide and show (`tags-delete-all`, `tags-hide-all`, `tags-unhide-all`) | Manage Tags selection | one tag at a time | **N/A** |
| Order in Manage Tags | `Intl.Collator`, numeric, case-insensitive | `ORDER BY tag COLLATE NOCASE` (`tag10` sorts before `tag2`) | Intentional (display only) |

### Rename

| Item | Actual | Actua | Status |
| --- | --- | --- | --- |
| Name taken | rejected when any other row has it, deleted ones included | same, checked before any note is rewritten | Match ([#983](https://github.com/azimul-kabir/actua/issues/983)) |
| Notes rewritten | `renameTagInNotes`, case-sensitive, `(?<!#)#old(?=[\s#]\|$)`, on every live `transactions` row | same token rule (`renameTagInNotes`) on every live `transactions` row with a `#`, split lines included (`ActualTagWriter.rename`) | Match ([#984](https://github.com/azimul-kabir/actua/issues/984)) |
| One batch | tag and notes in one `batchMessages` | tag and notes in one `applyLocalMessages` | Match ([#984](https://github.com/azimul-kabir/actua/issues/984)) |

### Tags in notes

Actual reads a tag as `#` followed by everything up to whitespace or the next `#`. A `#` that
follows another `#` never starts a tag. All of the following come from the corpus test.

| Note | Actual | Actua | Status |
| --- | --- | --- | --- |
| `lunch #food`, `a#food`, `#food#fun`, `#food#` | `food` (and `fun`) | same | Match |
| `##food` | no tag (escaped) | same | Match |
| `###food` | no tag | same | Match ([#985](https://github.com/azimul-kabir/actua/issues/985)) |
| `#food.`, `#food,y`, `#a+b`, `#café` | the whole word is the tag (`food.`, `food,y`, …) | same | Match |
| `#`, `# food` | no tag | same | Match |
| Tag ended by a tab, a no-break space or another JS `\s` character | ends the tag | same everywhere (`data/budget/TagSyntax.kt`) | Match ([#985](https://github.com/azimul-kabir/actua/issues/985)) |
| Rendering | every `#tag` is a chip, with a theme default color when the tag has none | a chip only for tags with a stored color | **Divergence** ([#986](https://github.com/azimul-kabir/actua/issues/986)) |
| Shown text for `##food` | `#food` (one `#` dropped) | `##food` as typed | Intentional (display only; stored notes are identical) |

### Filters, rules and discovery

| Item | Actual | Actua | Status |
| --- | --- | --- | --- |
| Filter value → tags | `extractTagsForFilter`: words split on whitespace or `#`, deduplicated | `TagFilter.extract` (`data/rules/RulesEngine.kt:430`) | Match |
| Rule runs: `hasTags` (all), `hasAnyTag` (any) | case-insensitive (`Condition.eval` lowercases both) | case-insensitive (`TagFilter.contains`) | Match |
| Report and dashboard filters with `hasTags`/`hasAnyTag` | AQL `REGEXP`, case-sensitive | case-sensitive when not a rule run | Match ([#985](https://github.com/azimul-kabir/actua/issues/985)) |
| A tag's transactions | Manage Tags opens the register filtered by `notes hasTags #tag` (case-sensitive) | **View transactions**: exact token, parent and split notes, case-sensitive (`ui/transactions/TransactionTagFilter.kt`) | Match ([#985](https://github.com/azimul-kabir/actua/issues/985)) |
| Find tags in notes (`tags-discover`) | creates a tag, with no color, for each unmanaged `#tag` in notes | not offered; unmanaged hashtags still match filters | **N/A** |
| `show-hidden-tags` synced preference | Manage Tags shows hidden tags | not read; Manage Tags always lists hidden tags | Intentional |

### Divergences

| Issue | Severity | Summary |
| --- | --- | --- |
| [#983](https://github.com/azimul-kabir/actua/issues/983) | P1 | Creating or renaming to a deleted tag's name adds a second row with that name and breaks sync for Actual clients (fixed) |
| [#984](https://github.com/azimul-kabir/actua/issues/984) | P2 | Rename skips split-line notes and isn't one atomic write (fixed) |
| [#985](https://github.com/azimul-kabir/actua/issues/985) | Lower | `###tag`, case in views and report filters, and Unicode spaces in rules (fixed) |
| [#986](https://github.com/azimul-kabir/actua/issues/986) | Lower | Tags with no color aren't shown as chips; tags created from notes get a default color |

**Limitations:** source comparison plus a corpus test of the parsing functions; no budget was opened
in both clients.
