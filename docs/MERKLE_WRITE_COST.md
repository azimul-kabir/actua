# Merkle write-cost investigation (#1600)

This is the investigation slice of [#1600](https://github.com/azimul-kabir/actua/issues/1600).
It adds tests and opt-in measurements; it does not change production writers, clock
persistence, wire encoding or recovery. The implementation and before/after acceptance
criteria remain open.

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
./gradlew :app:assembleDirectInstrumented :app:assembleDirectInstrumentedAndroidTest
./gradlew :app:connectedDirectInstrumentedAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.azimulkabir.actua.data.budget.MerkleWriteCostBenchmarkTest \
  -Pandroid.testInstrumentationRunnerArguments.merkleWriteBenchmark=true
```

The runner prints `MERKLE_WRITE_COST` lines containing device/API/ABI and every nanosecond
sample. Report the raw samples and median/p90, device, API, variant and source revision.
Use the same device and conditions for a future production before/after comparison.
Physical-device Macrobenchmark guidance remains in [PERFORMANCE_BASELINE.md](PERFORMANCE_BASELINE.md).

## Implementation decision and remaining work

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

Do not close #1600 for this investigation slice.
