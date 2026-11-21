# Merkle write-cost investigation (#1600)

Tracks [#1600](https://github.com/azimul-kabir/actua/issues/1600). The original investigation
and baseline below were merged in #1701. The implementation now opts production writers
into atomic row/log/clock persistence and incrementally maintains a connection-local,
log-derived Merkle baseline. Wire encoding, hashing and sync timestamp semantics are unchanged.

## History and upstream reference

- The writer's per-save rebuild and the sync client's log-derived tree were already present
  in Actua's initial source snapshot, [277bb3dc](https://github.com/azimul-kabir/actua/commit/277bb3dc94a3c6e02a95c3e24742e203c2586f0e).
  The inspected history does not record a benchmark or a separate rationale for doing the
  full scan after *every local write*.
- The [#659 parity audit](https://github.com/azimul-kabir/actua/issues/659), committed in
  [e9a38f47](https://github.com/azimul-kabir/actua/commit/e9a38f47eb4597f72083688162c734cd54cfee43),
  explicitly documents the log as authoritative and recovery when clock persistence is
  interrupted. See [SYNC_PARITY.md sections 4 and 7](SYNC_PARITY.md).
- Actual's [Merkle implementation at the fixture revision](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/crdt/src/crdt/merkle.ts)
  inserts timestamp hashes along base-3 minute paths and retains the last two children at
  each level. Its [sync write path](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/server/sync/index.ts)
  persists messages and clock/Merkle in one transaction. Full reconstruction is also a
  supported upstream [repair operation](https://github.com/actualbudget/actual/blob/59fe126f637d858c061e1eeedbef5436c8f2225a/packages/loot-core/src/server/sync/repair.ts).

At production revision `35e77d09`, there are twelve rebuild call sites (plus the
method declaration), distributed as follows:

| Caller | Rebuild sites | Role |
| --- | --- | --- |
| `ActualTransactionWriter` | 1 | Shared post-mutation clock save for transaction operations |
| `ActualEntityWriter` | 1 | Shared entity-message persistence |
| `ActualBudgetWriter` | 4 | Budget/template amounts, rollover, income hold and category transfers |
| `ActualScheduleWriter` | 2 | Next-date advancement and schedule write plans |
| `ActualTagWriter` | 1 | Tag-message persistence |
| `PayeeLocationWriter` | 1 | Payee-location message persistence |
| `ActualSyncClient` | 2 | Client initialization and the start of every sync pass |

## Equivalence evidence

`MerkleIncrementalConsistencyTest` compares **all retained nodes and hashes**, plus diff in
both directions, after each batch and a Merkle JSON encode/decode round trip. It covers:

- Real timestamp vectors already used by `SyncCoreFixtureTest`.
- Twelve reproducible random seeds, each with 320 messages, chronological/reverse/shuffled
  delivery and randomly sized batches. Inputs mix same-minute messages and minute buckets
  spread across decades.
- Late arrivals in previously dropped branches.
- The duplicate-insertion hazard: XORing a delivered timestamp twice is wrong. An updater
  must use only the rows actually inserted by `insertMessageRows`, not the delivery batch.

`MerkleMessageLogConsistencyTest` additionally compares a test-only incremental candidate
with the production SQLite `deriveMerkleFromMessageLog`, reopening the database between
batches and including duplicate delivery. Production sync fixtures are unchanged.

For Actua's current pruning rule, retained children are always the greatest two child keys
seen at a prefix. New insertions cannot make a discarded smaller key eligible again: the
two greater keys are still present. Every retained prefix keeps the XOR of all messages
under that prefix, including discarded descendants. This explains why updating retained
prefix hashes and pruning again can match rebuilding even for out-of-order input. This
reasoning assumes a correct initial tree and insertion of each unique log row exactly once.
It is not evidence that an arbitrary stored tree is trustworthy.

As already documented in the parity audit, upstream skips pruning a zero-hash node, while
Actua prunes every non-leaf. The shape may differ for XOR-cancelled subtrees. This PR does
not change that rule or claim universal byte-for-byte shape equality with upstream. The
existing upstream hash/diff/pruning fixtures must continue to pass before an implementation
is accepted; these additional property tests do not replace them.

## Repeatable measurement

`MerkleWriteCostBenchmarkTest` is opt-in, uses only synthetic temporary budgets, deletes them
on completion, and has no timing thresholds. It seeds 10,000, 100,000 and 500,000 historical
messages, one per second, all editing a synthetic transaction's notes. This isolates history
size from account/transaction-list size. It does not model all real budget distributions.

For each size it performs two warm-ups and seven measured calls to the real
`ActualTransactionWriter.updateTransaction` notes-save path, including message generation,
row/log persistence, full Merkle reconstruction, clock persistence and the write callback.
It separately measures full reconstruction and a test-only insert/prune operation starting
from a known-correct tree. That last measurement excludes SQLite, clock JSON persistence,
locking and baseline validation: **it is not an optimized transaction-save measurement**.
The log grows by nine messages during each size's save samples.

```sh
./gradlew :app:assemblePlaystoreInstrumented :app:assemblePlaystoreInstrumentedAndroidTest
./gradlew :app:connectedPlaystoreInstrumentedAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.azimulkabir.actua.data.budget.MerkleWriteCostBenchmarkTest \
  -Pandroid.testInstrumentationRunnerArguments.merkleWriteBenchmark=true
```

The runner prints `MERKLE_WRITE_COST` lines containing device/API/ABI and every nanosecond
sample. Report the raw samples and median/p90, device, API, variant and source revision.
Use the same device and conditions for a future production before/after comparison.
Physical-device Macrobenchmark guidance remains in [PERFORMANCE_BASELINE.md](PERFORMANCE_BASELINE.md).

## Exploratory emulator results

Measured 2026-11-20 (Asia/Dhaka), production source `35e77d09`, harness `7a6068ad`.
Device: Android SDK built for arm64, API 37, arm64-v8a; emulator 37.2.13 on an
Apple Silicon Mac with 8 GB RAM; `directInstrumented`, no physical device available.
App assembly was complete before timing; no physical-device Macrobenchmark was run.
These numbers are diagnostic emulator observations, **not a real-device baseline or an
end-to-end before/after optimization claim**. At that investigation stage, the production writer was unchanged.

Times below are **median / p90 in milliseconds**, using nearest-rank p90 (the maximum
of seven samples). Save and rebuild were timed in separate phases; differences between
those medians must not be interpreted as a precise breakdown of the save's remaining work.

| Seed messages | Current transaction save | Full tree rebuild only | Test-only insert/prune only |
| --- | --- | --- | --- |
| 10,000 | 44.708 / 45.308 | 30.298 / 31.643 | 0.193 / 0.205 |
| 100,000 | 330.407 / 365.396 | 322.246 / 340.377 | 0.506 / 1.381 |
| 500,000 | 1769.798 / 1799.981 | 1771.049 / 1812.414 | 1.188 / 2.066 |

The 50× history increase produced roughly a 40× save-time increase in this run. This
supports proceeding to an atomic incremental-update implementation slice, with the
recovery protections below. The isolated tree operation demonstrates a potential source
of savings but does not measure the final cost of any production replacement.

Raw samples (nanoseconds; seed, operation, seven samples):

```csv
10000,save_ns,45224625,45308333,44707750,45269083,37291625,34141916,35286292
10000,rebuild_ns,31165000,29422750,28638125,30054208,30297834,31643500,30500250
10000,incremental_tree_only_ns,193709,192459,205083,192792,192125,194709,191917
100000,save_ns,350991042,365396000,328853208,330407042,344911084,327252917,326251792
100000,rebuild_ns,320421834,332781834,319525375,322246041,340377250,320451916,332597958
100000,incremental_tree_only_ns,598709,506166,528125,483375,478500,479334,1380709
500000,save_ns,1799981335,1776925542,1762900876,1769798376,1761138292,1774595876,1765326126
500000,rebuild_ns,1761138376,1812414293,1771048834,1752187876,1758441751,1798510418,1789749001
500000,incremental_tree_only_ns,1184125,1268541,1188125,1187833,2066417,1142125,1176541
```

## Investigation validation (#1701)

- Committed wrapper: `:app:testDirectInstrumentedUnitTest --tests '*MerkleIncrementalConsistencyTest' --tests '*Sync*FixtureTest' --tests '*SyncUpstreamVectorTest'`:
  **31 tests passed** (four new consistency tests and the existing sync fixtures/vectors).
- `:app:assembleDirectInstrumented :app:assembleDirectInstrumentedAndroidTest`: **passed**.
- API 37 instrumentation: **20 existing sync-client/database tests passed**, covering
  recovery, offline writes, interrupted-response retries and fake-server/SQLite convergence.
  The opt-in measurement completed successfully at all three sizes.
- The initial test APK did not include the newly added SQLite consistency class. Rebuilt with
  `:app:assembleDirectInstrumentedAndroidTest`, reinstalled, and ran that class alone:
  **one test passed**. No test assertions were weakened to resolve the class-loading failure.
- `TwoClientTransactionParityTest` skipped itself as designed: its live Actual-server workflow
  was not configured. Fake-server/SQLite convergence above is not a live upstream round trip.
- `git diff --check`: passed. The pinned upstream repair-source link was checked through GitHub.
- Full-app lint, the complete JVM/device suite, API 28 and physical-device Macrobenchmark runs
  were not run for this test/documentation-only slice. Production behavior is unchanged.

## Investigation decision (before implementation)

The equivalence results support investigating incremental updates, but are not sufficient
for replacing `saveClock()` with `loadClock().merkle.inserting(...)`:

1. Current row/log writes and clock saves can commit separately. A crash, or a sync clock
   save interleaved with local edits, can leave a parseable tree that omits committed messages.
   Rebuilding repairs that today. A newer timestamp alone does not prove the tree is complete.
2. Local writers use several mutation entry points, including specialized transaction-row
   methods; changing only `applyLocalMessages` would miss some writes. Inventory all call sites.
3. A future updater must coordinate row mutations, accepted message timestamps and clock/tree
   persistence atomically, including multiple database connections and sync interleavings.
   An in-memory cache without invalidation across those paths is insufficient.
4. Keep explicit full reconstruction for existing/missing/malformed or suspect clock baselines,
   sync repair, imported/restored/redownloaded budgets and message replay until each path has
   its own validated invariant. Retain full-scan test comparisons as a correctness oracle.
5. Implement one shared finalization path only after those decisions, then repeat the real
   transaction-save measurements at all three sizes and run crash/reopen, duplicate-delivery,
   two-client convergence, retry/offline and API 28 SQLite tests. Before/after physical-device
   measurements are still required when hardware is available.

The investigation PR left #1600 open for the implementation and its validation.


## Production implementation

All six writers (transactions, entities, budgets, tags, schedules and payee locations) now
run their existing database mutation in `ActualBudgetDatabase.localWrite`. The shared
`insertMessageRows` path takes only successfully inserted timestamps and finalizes the
clock/tree inside the same SQLite transaction. Callbacks retain their existing timing
following the mutation. Payee locations keep their receive-side last-writer-wins selection.
Schedule JSON-path bookkeeping is enclosed in the same local-write transaction.

The baseline is scoped to one `ActualBudgetDatabase` instance, never initialized from
`messages_clock`. The first local write without a validated baseline rebuilds from the authoritative log,
already including that batch. Subsequent local writes insert accepted timestamps into that baseline and prune.
A duplicate ignored by SQLite is not XORed into the tree again. No persistent table or schema is added; a connection-local TEMP table marks its lifetime.

Cold opens, raw log inserts, incoming remote rows, and any rolled-back transaction invalidate
or lack the baseline and therefore force a full reconstruction on the next local write.
Opening an imported/restored/redownloaded file and migration replay also precede creation of
this instance-local baseline. A full rebuild remains an independent correctness oracle;
sync initialization and every sync pass continue using it.

Other connections are detected with SQLite's [PRAGMA data_version](https://www.sqlite.org/pragma.html#pragma_data_version).
SQLite specifies that its value changes for commits by other connections and is meaningful
only when compared on the same connection. Both the version check and tree update run in
an Android SQLite **write transaction**, which pins the primary connection and excludes
other writers. A changed version discards the cached baseline. An unrelated external commit
can therefore cause a conservative full rebuild, but cannot admit stale tree state.

Android can replace a pooled physical connection while the owning `SQLiteDatabase` object
remains alive. Its connection-local version counter can restart at the same number as the
old one. An empty `actua_local_merkle_connection` TEMP table marks the physical connection's
lifetime; if absent, the baseline is invalidated before checking the version. The TEMP schema
is never part of the persistent budget or an exported archive. The connection-replacement
regression fails against the earlier counter-only implementation and passes with this guard.
See [Android's connection pool](https://android.googlesource.com/platform/frameworks/base/+/android-9.0.0_r61/core/java/android/database/sqlite/SQLiteConnectionPool.java).

The transaction helper remembers a failed nested transaction even if the caller catches its
exception and performs more writes before the outer rollback. It discards the in-memory
tree when that outer transaction ends, or when ending a transaction throws. This prevents
rolled-back timestamps from being retained in the next successful write.

Successful sync saves its existing timestamp boundary together with a current log-derived
tree under the SQLite writer lock, reusing a validated baseline or rebuilding if invalid. Local edits committed since the response's snapshot are
therefore included in the stored tree without being marked acknowledged by that boundary.
Raw `applyLocalMessages`/`insertMessages`/`receiveMessages` APIs outside `localWrite` retain
their prior recovery semantics and invalidate the local baseline when they add rows.

A cold local write and the first after invalidation still pay the full
history cost. The steady-state benchmark's warm-up excludes that rebuild. This optimization
does not claim to remove cold-open/recovery work or accelerate the network sync protocol.


## Implementation comparison on the same emulator

Production baseline: retained pre-implementation APK with source `35e77d09` (production
code is identical to investigation merge `55d75ab2`), benchmark harness from #1701.
Optimized source: `720f893e`. Measured 2026-11-20 (Asia/Dhaka), on the same running API 37
arm64 emulator, same `directInstrumented` variant, same untouched benchmark, two warm-ups
and seven samples per size. Each instrumentation run starts its own app process. Assembly
finished before timing, and no builds or lint ran during either measurement.

**These are exploratory emulator results, not physical-device performance evidence.**
No frame timings/janky-frame comparison or physical Macrobenchmark was available.

| Seed messages | Before: median / p90 ms | After: median / p90 ms |
| --- | --- | --- |
| 10,000 | 43.309 / 44.237 | 0.657 / 0.731 |
| 100,000 | 316.370 / 319.735 | 2.926 / 3.129 |
| 500,000 | 1742.807 / 1775.864 | 5.563 / 9.041 |

At 500k messages, the measured steady-state median fell from about 1.74 seconds to 5.56 ms.
The benchmark excludes the cold rebuild through warm-up. The retained tree's shape and
clock JSON still grow with the spread of minute buckets; this is not a claim of constant
latency for every budget or after invalidation. Cross-connection writes, including clock-only
commits, conservatively invalidate the baseline and can make the next local save cold.

Raw transaction-save samples (nanoseconds):

```csv
before,10000,43479541,43308541,43919834,44237000,36182875,32112875,33090250
before,100000,316370000,317316292,318363876,319735459,309379750,315951083,312614625
before,500000,1754803376,1738220334,1761658292,1742806584,1731155501,1731050709,1775864168
after,10000,730625,667125,668417,647583,630375,657458,657167
after,100000,2008708,3128708,1944709,3000250,3018250,1939209,2926417
after,500000,7138458,5562708,5421375,5436916,6302875,9040542,5100333
```


## Implementation validation

Source `720f893e`, API 37 and API 28 arm64 emulators, no production budgets or credentials.

- `./gradlew :app:testDirectInstrumentedUnitTest :app:assembleDirectInstrumented :app:assembleDirectInstrumentedAndroidTest`:
  **1,871 JVM tests passed and both instrumented APKs assembled**. This includes the existing
  upstream hash/timestamp/wire/encryption vectors and seeded full-tree consistency tests.
- API 37 core instrumentation: **34 passed** (`LocalMerklePersistenceTest`,
  `ActualSyncClientTest`, `ActualBudgetDatabaseSyncTest`, `MerkleMessageLogConsistencyTest`).
- API 37 affected-writer/recovery instrumentation: **224 passed**, using the selection below.
- API 28: **all 258 core and affected-writer/recovery tests passed**, including the
  `data_version`, concurrent-handle, rollback, duplicate, and clock-failure checks.
- Both before/after benchmark runs completed and their clock/log consistency assertions passed.
- The connection-replacement regression failed against the counter-only APK and passed with
  the TEMP lifetime guard on both API 28 and 37. It also checks that the marker is absent
  from the persistent budget schema.
- The new sync-interleaving test initially expected the source message's node in the saved
  boundary. Corrected it to assert the sync client's own node, preserving the existing HLC
  semantics, then reran the complete core selection successfully.
- The live Actual two-client workflow was not configured. Existing fake-server reconciliation,
  overlapping/reverse-delivery tests and real concurrent SQLite-handle writes passed; these
  do not substitute for a live upstream integration run.
- No physical Android device was available for the retained AddEditTransactionBenchmark or
  NavigationBenchmark before/after journeys. Emulator timings above are diagnostic only.

For the core run, install the instrumented APKs, then use:

```sh
adb shell am instrument -w -r \
  -e class com.azimulkabir.actua.data.budget.LocalMerklePersistenceTest,com.azimulkabir.actua.data.sync.ActualSyncClientTest,com.azimulkabir.actua.data.budget.ActualBudgetDatabaseSyncTest,com.azimulkabir.actua.data.budget.MerkleMessageLogConsistencyTest \
  com.azimulkabir.actua.test.test/androidx.test.runner.AndroidJUnitRunner
```

The affected-writer selection adds these fully qualified classes (pass a comma-separated
list to the same runner's `-e class`; the API 28 run combined this with the core selection):

```text
com.azimulkabir.actua.data.ActuaRepositoryMultiAccountImportTest
com.azimulkabir.actua.data.ActuaRepositoryOneTimeScheduleTest
com.azimulkabir.actua.data.ActuaRepositoryScheduleWeekendPostTest
com.azimulkabir.actua.data.bank.BankSyncFailureTest
com.azimulkabir.actua.data.bank.BankSyncReconciliationTest
com.azimulkabir.actua.data.budget.ActualAccountCloseTest
com.azimulkabir.actua.data.budget.ActualBudgetBufferTest
com.azimulkabir.actua.data.budget.ActualBudgetReadModelTest
com.azimulkabir.actua.data.budget.ActualDataIntegrityRegressionTest
com.azimulkabir.actua.data.budget.ActualEntityWriterReorderTest
com.azimulkabir.actua.data.budget.ActualTagWriterTest
com.azimulkabir.actua.data.budget.BankLinkWriterTest
com.azimulkabir.actua.data.budget.BudgetDownloadServiceTest
com.azimulkabir.actua.data.budget.BudgetResetRecoveryTest
com.azimulkabir.actua.data.budget.CategoryDeleteTransferTest
com.azimulkabir.actua.data.budget.CategoryGroupDeleteTest
com.azimulkabir.actua.data.budget.CustomReportAndDashboardWriterTest
com.azimulkabir.actua.data.budget.DashboardRepairTest
com.azimulkabir.actua.data.budget.TransactionMergeWriterTest
com.azimulkabir.actua.data.budget.TransactionParityTest
com.azimulkabir.actua.data.budget.TransactionTransferLinkWriterTest
com.azimulkabir.actua.data.budget.TransferRemovalTest
com.azimulkabir.actua.data.location.PayeeLocationDatabaseTest
com.azimulkabir.actua.data.schedules.ScheduleWriteBuilderTest
com.azimulkabir.actua.data.sync.StandaloneSchedulePostingTest
com.azimulkabir.actua.data.sync.WriterClockHighWaterTest
```
