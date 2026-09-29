# Sync protocol parity: HLC, CRDT, protobuf, Merkle and encryption

Feature-by-feature audit of the Actual Budget sync behavior Actua depends on, tracked in
[#659](https://github.com/azimul-kabir/actua/issues/659) (part of
[#658](https://github.com/azimul-kabir/actua/issues/658)). Runtime/scheduling behavior is in
[SYNC_BEHAVIOR.md](SYNC_BEHAVIOR.md); the schema/protocol version audit is in
[ACTUAL_26_9_COMPATIBILITY.md](ACTUAL_26_9_COMPATIBILITY.md).

- **Upstream reference:** `actualbudget/actual` at
  [`59fe126f`](https://github.com/actualbudget/actual/tree/59fe126f637d858c061e1eeedbef5436c8f2225a).
  Links below use the prefix `U/` =
  `https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/`.
- **Actua paths** are relative to `app/src/main/java/com/azimulkabir/actua/`. Tests: `src/test` =
  `app/src/test/java/com/azimulkabir/actua/`, `src/androidTest` =
  `app/src/androidTest/java/com/azimulkabir/actua/`.
- **Status:** **Match** = same observable/wire behavior; **Intentional** = deliberate Android
  divergence that doesn't change what the server or other clients see; **Divergence** = filed as an
  issue; **N/A** = upstream feature Actua doesn't use.

## 1. HLC timestamps

| Behavior | Actual | Actua | Status | Test evidence |
| --- | --- | --- | --- | --- |
| 46-char string `ISO-CCCC-NNNNNNNNNNNNNNNN`: uppercase hex counter padded to 4, node left-padded/truncated to 16 | [`U/packages/crdt/src/crdt/timestamp.ts#L109-L115`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/crdt/src/crdt/timestamp.ts#L109-L115) | `data/sync/HlcTimestamp.kt:17-21` | Match | `SyncCoreFixtureTest.timestampParsesFormatsAndOrdersLikeUpstream` |
| Ordering is string comparison (`valueOf() = toString()`) | `timestamp.ts#L105-L107` | `HlcTimestamp.kt:23` | Match | same |
| Parse: millis ≥ 0, counter ≤ `0xFFFF`, node ≤ 16 chars | `timestamp.ts#L166-L189` | `HlcTimestamp.kt:31-51` | Match for canonical input. Actua's regex also requires a non-empty node without `-` and rejects year ≥ 10000, which Actual's `Date.parse` also rejects. The server only relays client-generated canonical strings, so the stricter parse is unobservable. | `SyncCoreFixtureTest.timestampRejectsInvalidUpstreamVectors` (all upstream invalid vectors) |
| `zero`, `max`, `since(iso)` | `timestamp.ts#L158-L160`, `#L298-L302` | `HlcTimestamp.kt:35-36,53` | Match | `SyncCoreFixtureTest` |
| `send()`: `max(old, now)`, counter + 1 on stutter/regression, else 0 | `timestamp.ts#L195-L230` | `data/sync/HybridLogicalClock.kt:37-55` | Match | **New:** `SyncUpstreamVectorTest.sendIsMonotonicWithMonotonicStutteringAndRegressingClocks` (upstream vectors) |
| `recv()`: `max(old, now, msg)` with Actual's four-way counter rule | `timestamp.ts#L235-L292` | `HybridLogicalClock.kt:57-71` | Match | **New:** `SyncUpstreamVectorTest.receiveIsMonotonicWith*` (all upstream recv vectors) |
| 5-minute drift limit on send and recv; counter overflow at `0xFFFF` | `timestamp.ts#L83-L89`, `#L213-L219`, `#L251-L253`, `#L276-L281` | `HybridLogicalClock.kt:60,73-76` | Match | **New:** `SyncUpstreamVectorTest.sendFailsWithCounterOverflowAndClockDrift`, `receiveFailsWithClockDrift`; `SyncCoreFixtureTest.clockPreservesMonotonicityAndDetectsOverflow` |
| Node id: last 16 hex chars of a dash-less UUIDv4 | `timestamp.ts#L79-L81` | `HybridLogicalClock.kt:80` | Match (format) | – |
| One global clock per budget, persisted in `messages_clock` with a stable node and advanced by every received message | `timestamp.ts#L34-L77`, [`U/packages/loot-core/src/server/db/index.ts#L99-L116`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/server/db/index.ts#L99-L116), `sync/index.ts#L447-L460` | Each writer (`data/budget/ActualTransactionWriter.kt:20-24`, `ActualEntityWriter.kt:20-22`, `ActualBudgetWriter.kt:14-16`, `data/schedules/ActualScheduleWriter.kt:14-15`, `ActualTagWriter.kt:23-25`, `data/location/PayeeLocationWriter.kt:17-19`) and `data/sync/ActualSyncClient.kt:40-42` has its own clock with a random node. Writer clocks read the message-log high-water mark (`ActualBudgetDatabase.messageLogHighWater`) before every `send()` (`HybridLogicalClock.kt:37-44`), so messages the sync client received after a writer was built still advance it | **Intentional** ([#691](https://github.com/azimul-kabir/actua/issues/691)). Every local timestamp is newer than everything already in `messages_crdt` when it is generated, which is the property Actual's shared clock provides; a received message always passes Actual's drift check first, so the advanced send drifts no further than upstream would. A random node per instance is harmless to the server. | `SyncCoreFixtureTest.sendAdvancesPastMessageLogWrittenAfterConstruction`; `WriterClockHighWaterTest` (instrumented, one case per writer) |

## 2. CRDT values and message application

| Behavior | Actual | Actua | Status | Test evidence |
| --- | --- | --- | --- | --- |
| Serialize `null → 0:`, number → `N:<n>`, string → `S:<s>` | [`U/packages/loot-core/src/server/sync/index.ts#L157-L167`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/server/sync/index.ts#L157-L167) | `data/sync/CrdtMessage.kt:18-25` | Match. Booleans become `N:0/1`, matching Actual's integer booleans. Unknown types serialize as `0:`, while upstream throws; writers only pass supported types. | `SyncEncoderFixtureTest.crdtValuesMatchActualEncoding` |
| Deserialize: `N:` via `parseFloat`, unknown prefix throws | `sync/index.ts#L169-L182` | `CrdtMessage.kt:27-38` (Long, else Double, else `Null`) | Match for valid values. Integer cents stay `Long`, so no float round trip. Malformed values become `NULL` instead of aborting the batch. **Intentional:** only a hostile or buggy peer can produce them. | `SyncEncoderFixtureTest.crdtValuesMatchActualEncoding` |
| Per-cell last-writer-wins: skip a message when the log has the same timestamp; mark it "old" (log it, don't apply it) when a newer one exists | `sync/index.ts#L197-L223` | `data/budget/ActualBudgetDatabase.kt:1395-1399` (`receiveMessages`), `:1441-1448` (`hasSameOrNewerCellMessage`), `:1450-1469` (`INSERT OR IGNORE`) | Match | `ActualBudgetDatabaseSyncTest.incomingMessagesAreOrderedDeduplicatedAndApplied`, `twoClientsConvergeWithOverlapAndReverseDelivery` |
| Apply in timestamp order inside one DB transaction; UPDATE if the row exists, else INSERT | `sync/index.ts#L273-L385` | `ActualBudgetDatabase.kt:1471-1500` (`applyMessageRows`, `upsertValue`) inside `transaction {}` | Match | same |
| Unknown dataset or column: throw `invalid-schema` and abort the whole batch | `sync/index.ts#L80-L108` | Message is logged in `messages_crdt` but not applied (`ActualBudgetDatabase.kt:1471-1477`); `replayStoredMessages` (`:1746`) materializes it when a known migration later adds the column | **Intentional.** It keeps the Merkle hash consistent and avoids executing hostile identifiers. The user gets no "update required" prompt for tables or columns outside Actua's migration list. | `ActualBudgetDatabaseSyncTest.unknownAndHostileIdentifiersAreLoggedButNeverExecuted` |
| `prefs` dataset goes to metadata prefs; `preferences/budgetType` side effect | `sync/index.ts#L83-L85`, `#L347-L349`, `#L367-L370` | `prefs` has no table, so it's logged only; `preferences` rows are applied like any table | **Intentional/partial.** Synced metadata prefs (e.g. the budget name) aren't mirrored into Actua's local budget list. | – |
| Local mutations are applied and logged atomically, with no LWW check (a fresh `send()` is always newest) | `sync/index.ts#L488-L532` | `ActualBudgetDatabase.kt:1385-1389` (`applyLocalMessages`) | Match, provided the timestamp is newest. See the #691 row above. | See [LOCAL_FIRST_SYNC_AUDIT.md](LOCAL_FIRST_SYNC_AUDIT.md) |
| Undo log independent of the message log | `sync/index.ts#L314` (`undo.appendMessages`) | Actua has no multi-step undo; the message log is never rewritten | N/A | – |

## 3. Protobuf wire format

| Message / field | Actual (`sync.proto`) | Actua | Status | Test evidence |
| --- | --- | --- | --- | --- |
| `EncryptedData{iv=1, authTag=2, data=3}` | [`U/packages/crdt/src/proto/sync.proto#L4-L8`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/crdt/src/proto/sync.proto#L4-L8) | `data/sync/SyncProtocol.kt:23-45` | Match | `SyncEncryptionFixtureTest.encryptedSyncMessageRoundTripsThroughProtobufEnvelope` |
| `Message{dataset=1,row=2,column=3,value=4}` | `sync.proto#L10-L15` | `SyncProtocol.kt:47-71` | Match | `SyncEncoderFixtureTest.innerMessageMatchesUpstreamBytes` |
| `MessageEnvelope{timestamp=1,isEncrypted=2,content=3}` | `sync.proto#L17-L21` | `SyncProtocol.kt:73-94` | Match | `SyncEncoderFixtureTest.syncRequestMatchesUpstreamBytes` |
| `SyncRequest{messages=1,fileId=2,groupId=3,(4 reserved),keyId=5,since=6}`; `keyId` is empty when unencrypted | `sync.proto#L23-L30`, [`U/packages/loot-core/src/server/sync/encoder.ts#L32-L93`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/server/sync/encoder.ts#L32-L93) | `SyncProtocol.kt:96-129`, `data/sync/SyncEncoder.kt:16-32` | Match | `SyncEncoderFixtureTest.syncRequestMatchesUpstreamBytes` |
| `SyncResponse{messages=1, merkle=2 (JSON)}` | `sync.proto#L32-L35`, `encoder.ts#L95-L140` | `SyncProtocol.kt:131-149`, `SyncEncoder.kt:34-52` | Match. Actua also rejects a blank Merkle and unparsable envelope timestamps (`InvalidMerkle`/`InvalidTimestamp`), where upstream would fail later. | `SyncEncoderFixtureTest.plaintextResponseRoundTrips`, `encryptedEnvelopeRequiresCipher` |
| Proto3 defaults omitted, unknown fields skipped, bounded varints | protobuf-es | `data/sync/ProtoWire.kt` | Match | `SyncEncoderFixtureTest` byte fixtures |

## 4. Merkle trie

| Behavior | Actual | Actua | Status | Test evidence |
| --- | --- | --- | --- | --- |
| Key = base-3 minutes since epoch; XOR of `murmurhash.v3(timestamp)` along the path; signed 32-bit hashes | [`U/packages/crdt/src/crdt/merkle.ts#L48-L68`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/crdt/src/crdt/merkle.ts#L48-L68) | `data/sync/MerkleTree.kt:9-10,37-49,59`, `data/sync/MurmurHash3.kt` | Match | `SyncCoreFixtureTest.murmurHashMatchesUpstreamVectors`, `merkleInsertAndDiffMatchUpstream`; **new** `SyncUpstreamVectorTest.merkleInsertMatchesUpstreamSnapshotHashes` |
| `diff`: descend the first differing key in sorted order, stop at a missing key, pad to 16 digits, ×60 000 | `merkle.ts#L36-L46`, `#L78-L139` | `MerkleTree.kt:12-33,61-62` | Match | **New:** `SyncUpstreamVectorTest.merkleDiffReturnsUpstreamTimeDifference`, `merkleDiffWithEmptyTrieReturnsEpoch`, `merkleDiffOfDifferentlyShapedAndPrunedTriesMatchesUpstream` |
| `prune(n = 2)` keeps the last two children per level and preserves hashes | `merkle.ts#L141-L164` | `MerkleTree.kt:35,51-57` | Match. Upstream skips pruning a node whose hash is 0; Actua skips only leaves. That only matters for an XOR-cancelled non-empty subtree, which is compared by hash either way. | **New:** `SyncUpstreamVectorTest.merklePruningKeepsUpstreamHashes` |
| Bulk build from the message log | `merkle.ts#L70-L76`, [`U/packages/loot-core/src/server/sync/repair.ts`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/server/sync/repair.ts) | `MerkleTree.building`, `ActualBudgetDatabase.kt:1402-1413` | Match. Actua rebuilds from the log on every sync pass, the equivalent of upstream `rebuildMerkleHash`. | `SyncCoreFixtureTest.bulkMerkleBuildMatchesIncrementalInsertion` |
| Server Merkle JSON parse | `JSON.parse` | `data/sync/MerkleJson.kt` | Match for the server's `{"0"/"1"/"2", "hash"}` shape. Actua rejects any other key (`InvalidMerkle`); upstream would ignore it. **Intentional** strictness. | `ActualSyncClientTest` (via fake server) |

## 5. End-to-end encryption

| Behavior | Actual | Actua | Status | Test evidence |
| --- | --- | --- | --- | --- |
| Key derivation: PBKDF2-SHA512, 10 000 iterations, 256-bit, salt = UTF-8 bytes of the server salt string | [`U/packages/loot-core/src/server/encryption/encryption-internals.ts#L63-L94`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/server/encryption/encryption-internals.ts#L63-L94) | `data/sync/SyncEncryption.kt:24-31` | Match | `SyncEncryptionFixtureTest.derivedKeyMatchesActualFixture` |
| AES-256-GCM, 12-byte random IV, 16-byte tag split off the ciphertext | `encryption-internals.ts#L17-L61` | `SyncEncryption.kt:33-65` | Match | `SyncEncryptionFixtureTest.decryptsActualKeyTestFixture`, `encryptedSyncMessageRoundTripsThroughProtobufEnvelope` |
| Key test: decrypt the server `test` JSON (`value`, `meta.iv`, `meta.authTag`); missing test → `old-key-style`; failure → `decrypt-failure` | [`U/packages/loot-core/src/server/encryption/app.ts#L91-L118`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/server/encryption/app.ts#L91-L118) | `data/sync/EncryptionKeyManager.kt:20-40` (`UnsupportedLegacyKey`, `InvalidPassword`, `MalformedTestMessage`) | Match | `src/androidTest/.../data/sync/EncryptionKeyManagerTest.validatesActualFixtureAndRejectsWrongPassword` |
| Key storage | `asyncStorage` `encrypt-keys` | `data/security/BudgetEncryptionKeyStore.kt` (derived key wrapped by an Android Keystore AES-GCM key) | **Intentional** (stronger at rest) | `EncryptionKeyManagerTest.derivedKeyIsWrappedByAndroidKeystore` |
| `keyId` sent on every request; missing key → `encrypt-failure`/`decrypt-failure` (`isMissingKey`) | `encoder.ts#L38-L66`, `#L107-L123` | `data/sync/ActualSyncWorker.kt:70-79` refuses to sync without a loaded key whose id matches the budget's `encryptKeyId` (`EncryptionKeyUnavailable`); an encrypted envelope without a cipher → `EncryptionRequired` | Match for local detection. The error surface is covered by [#692](https://github.com/azimul-kabir/actua/issues/692). | `SyncEncoderFixtureTest.encryptedEnvelopeRequiresCipher` |

## 6. Full sync loop

| Behavior | Actual | Actua | Status | Test evidence |
| --- | --- | --- | --- | --- |
| Send `messages_crdt WHERE timestamp > since`; since = explicit diff time, else `lastSyncedTimestamp`, else now − 5 min | `sync/index.ts#L534-L540`, `#L695-L704` | `ActualSyncClient.kt:58-78`, `ActualBudgetDatabase.kt:1344-1368` | **Intentional.** The fallback is the downloaded snapshot's log high-water mark, not "5 minutes ago", so pending edits made before the first sync are never skipped. | `ActualSyncClientTest.freshDownloadUsesSnapshotHighWaterMarkAndDoesNotRepushHistory`, `localWriteAfterRecoveryBeforeFirstSyncIsSent`, `validClockBehindLogPreservesUnsentWrites` |
| Receive → `Timestamp.recv` each message → apply → Merkle diff → recurse from the diff time | `sync/index.ts#L735-L830` | `ActualSyncClient.kt:79-91` | Match | `ActualSyncClientTest.clientsExchangeMessagesAndConverge`, `missingClockAfterCommittedWriteRecoversThroughMerkleDifference`, `retryAfterResponseInterruptionResendsAcceptedLocalWriteAndConverges` |
| Repeated-sync limit: `out-of-sync` after 10 identical diff times or 100 passes; counter resets when the local clock moved | `sync/index.ts#L754-L830` | `ActualSyncClient.kt:59,100` (10 passes total) | **Divergence** ([#693](https://github.com/azimul-kabir/actua/issues/693)): a large catch-up can fail sooner than Actual would | `ActualSyncClientTest.permanentlyDishonestMerkleIsBounded` |
| On success, persist `lastSyncedTimestamp` = current clock | `sync/index.ts#L831-L841` | `ActualSyncClient.kt:93-94` (saved in `messages_clock`) | Match | `ActualSyncClientTest.*ClockRecoversFromMessageLog` |
| Abort if the group id changed during the request (local sync reset) | `sync/index.ts#L729-L733` | Not applicable: a reset/re-download replaces the budget file and database outside a running sync | N/A | – |
| Sync is serialized (`once`, `sequential`) | `sync/index.ts#L261`, `#L597` | `ActualSyncClient.sync` and `ActualSyncRunner.run` are `@Synchronized`; WorkManager unique work | Match | `SyncSchedulingPolicyTest` |

## 7. `messages_crdt` / `messages_clock` persistence

| Behavior | Actual | Actua | Status | Test evidence |
| --- | --- | --- | --- | --- |
| `messages_crdt(timestamp, dataset, row, column, value)`, value stored serialized | `sync/index.ts#L357-L365` | `ActualBudgetDatabase.kt:1450-1469` | Match | `ActualBudgetDatabaseSyncTest` |
| `messages_clock` row `id = 1`, JSON `{timestamp, merkle}`, written in the same transaction as the applied messages | `sync/index.ts#L373-L384`; `timestamp.ts#L49-L77` | `ActualBudgetDatabase.kt:1416-1439` (same JSON shape); written after each writer operation and after a successful sync, outside the message transaction | **Intentional.** On load, Actua treats the log as the source of truth: an invalid, epoch, or behind-the-log clock is recovered from `MAX(timestamp)` and the Merkle tree is re-derived from the log. | `ActualSyncClientTest.blankLegacyClockRecoversFromMessageLog`, `epochClockRecoversFromMessageLog`, `nonzeroLegacy1970ClockRecoversFromMessageLog`, `malformedClockRecoversFromMessageLog`, `invalidClockWithEmptyLogRequestsFullHistory` |

## 8. Server and sync error handling

`/sync` rejections come from
[`U/packages/sync-server/src/app-sync.ts#L138-L190`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/sync-server/src/app-sync.ts#L138-L190)
and
[`U/packages/sync-server/src/app-sync/validation.js#L7-L47`](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/sync-server/src/app-sync/validation.js#L7-L47).
Actual's client maps them in `sync/index.ts#L597-L661` and `packages/desktop-client/src/sync-events.ts`.
Actua's current mapping goes through `data/network/ActualServerClient.kt:283-291,521-526`, with
retries at `ActualSyncWorker.kt:167-168`: non-200 → `ActualServerException.Http`, 401/403 →
`Unauthorized`, any exception → WorkManager retry (5 attempts, exponential backoff from 10 s), then
failure. The latest message appears in **Manage → Connection & Data**. With a fallback server
configured, every primary failure is retried once against the fallback URL.

| Reason (source) | Actual behavior | Actua behavior today | Class | Target ([#692](https://github.com/azimul-kabir/actua/issues/692)) |
| --- | --- | --- | --- | --- |
| `network-failure` (fetch threw) | "network" notice; retries on next sync | IOException → fallback URL, then WorkManager retry | Retry | Match |
| `internal-error` (500) | Generic error | `Http(500)` → retry | Retry | Match |
| `unauthorized` (401) / `token-expired` (401 + `token-not-found`) | Read-only mode + auth notice / sign out | `Unauthorized` → retried 5× → surfaced | Surface | Stop retrying; prompt to sign in again |
| `file-access-not-allowed` (403, text) | Generic error | Treated as `Unauthorized` | Surface | Distinct "no access to this file" message |
| `file-not-found` (400) | "Not a cloud file" → Register (upload) | `Http(400)` → retry | Surface | No retry; explain; offer re-link via Actual |
| `since-required` (422) | – (client always sends `since`) | `Http(422)` | N/A | Actua always sends `since` |
| `file-old-version` (400) | "Reset sync" prompt | `Http(400)` → retry | Surface | No retry; direct the user to reset in Actual |
| `file-needs-upload` (400) | "Upload" (reset sync) | `Http(400)` → retry | Surface | No retry; direct the user to Actual |
| `file-key-mismatch` (400) | "Reset key" prompt | `Http(400)` → retry | Surface | No retry; direct the user to Actual |
| `file-has-reset` / `file-has-new-key` (400) | "Sync has been reset" → revert (re-download) or upload | `Http(400)` → retry | Recover (user-confirmed) | No retry; offer re-download after backing up local unsynced edits |
| `decrypt-failure` / `encrypt-failure` | "Missing encryption key" → create/re-enter key | Missing key: `EncryptionKeyUnavailable` (no retry, surfaced). Wrong key: `SyncEncryptionException` → retry | Surface | No retry; prompt for the encryption password |
| `clock-drift` (`Timestamp.ClockDriftError` on recv) | "Time sync issue" notice | `HlcException.ClockDrift` → retry | Surface | No retry until the device time changes; explain |
| `out-of-sync` (loop limit) | "Out of sync" → Repair / Reset sync | `ActualSyncException.OutOfSync` → retry | Surface | Keep the retry (the next run rebuilds the Merkle tree from the log); see #693 |
| `invalid-schema` / `apply-failure` | "Update required" / apply-failure notice | Not raised (see section 2, unknown dataset/column) | Intentional | – |

## Fixture coverage

Existing fixtures (`SyncCoreFixtureTest`, `SyncEncoderFixtureTest`, `SyncEncryptionFixtureTest`) cover
MurmurHash, timestamp parse/format, wire bytes and key derivation. This audit adds
`src/test/.../data/sync/SyncUpstreamVectorTest.kt`, which ports the remaining upstream `crdt` vectors
verbatim from `timestamp.test.ts` (all `send`/`recv` cases, overflow, drift) and `merkle.test.ts`
(snapshot hashes, diff time, empty-trie diff, pruning, differently shaped/pruned diffs). Where upstream
overrides `timestamp.hash()`, the test builds the same tries with `MerkleTree.building` and the same
per-minute hashes. No fixture was regenerated.

Still missing, and covered by the linked issues' acceptance criteria:

- Server error reason classification and retry decisions (#692)
- Loop-limit progress and reset behavior (#693)

## Divergences filed

- [#691](https://github.com/azimul-kabir/actua/issues/691): writer clocks weren't advanced by received messages (LWW divergence under clock skew); fixed
- [#692](https://github.com/azimul-kabir/actua/issues/692): server/sync error reasons retried as generic failures
- [#693](https://github.com/azimul-kabir/actua/issues/693): full-sync retry limit stricter than Actual
