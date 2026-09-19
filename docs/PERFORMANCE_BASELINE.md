# Performance instrumentation, baselines, and regression protection (#321, #331)

Part of the [performance & UI smoothness initiative](https://github.com/azimul-kabir/actua/issues/316).
This slice adds repeatable Macrobenchmark coverage for the app's core journeys so later
optimization work (recomposition, list performance, mutation architecture, invalidation, etc.)
can be measured against a documented baseline instead of "feel".

## What's here

A new `:macrobenchmark` Gradle module (`com.android.test`, alongside the existing
`:baselineprofile` module from PR #319) with `MacrobenchmarkRule`-based tests under
`macrobenchmark/src/main/java/com/azimulkabir/actua/macrobenchmark/`:

| Class | Journeys |
| --- | --- |
| `StartupBenchmark` | Cold / warm / hot startup (`StartupTimingMetric`) |
| `BudgetScreenBenchmark` | Scroll, category-group expand/collapse, month switching, category-group reorder, opening Category Details |
| `AccountsScreenBenchmark` | Scroll, section expand/collapse, opening an account and scrolling its transactions |
| `TransactionsScreenBenchmark` | Opening All Transactions and scrolling, search, status-filter chips |
| `AddEditTransactionBenchmark` | Opening/closing Add Transaction, on-screen calculator keypad entry |
| `NavigationBenchmark` | Bottom-navigation tab switching, back navigation from a detail screen |

All journeys except startup use `FrameTimingMetric` (janky/slow-frame counts and frame-duration
percentiles) and run against realistic, populated data: `DemoBudgetJourneys.seedDemoBudget()`
drives the app's own "Try demo budget" / "Reset demo budget" flow (`DemoBudgetSeeder`) once per
test class, outside any measured block, so every benchmark exercises the same populated budget
(4 accounts, 4 category groups, ~10 categories, targets, rules, schedules) instead of an empty
database.

`MainActivity`/`AppNavigation` now also call `Activity.reportFullyDrawn()` once the Budget tab
has real data to show (see the `reportedFullyDrawn` `LaunchedEffect` in `AppNavigation.kt`), so
`StartupTimingMetric` reports both time-to-initial-display and time-to-full-display instead of
only the former.

## Why a separate module, and why "benchmarkRelease"

`:baselineprofile` only *generates* the baseline profile (`BaselineProfileRule`, runs against
`:app`'s `nonMinifiedRelease` variant). Actual measurement needs `MacrobenchmarkRule` against
release-shaped code — R8, no debuggable — or the numbers mostly reflect debug-build overhead
(no minification, JIT warm-up) rather than what real users experience. The
`androidx.baselineprofile` plugin applied to `:app` already creates a `benchmarkRelease` build
variant automatically for exactly this purpose (release-equivalent, debug-signed, profileable);
`:macrobenchmark`'s own `benchmark` build type targets it via `matchingFallbacks`.

## Running locally

Requires a connected device or emulator, and — because `benchmarkRelease` inherits `:app`'s
`release` signing config — either the real release-signing secrets
(`ACTUA_KEYSTORE_PATH`/`ACTUA_KEYSTORE_PASSWORD`/`ACTUA_KEY_ALIAS`/`ACTUA_KEY_PASSWORD`, as used
in CI/release builds) or, for local iteration, the debug keystore:

```bash
export ACTUA_KEYSTORE_PATH="$HOME/.android/debug.keystore"
export ACTUA_KEYSTORE_PASSWORD=android
export ACTUA_KEY_ALIAS=androiddebugkey
export ACTUA_KEY_PASSWORD=android

./gradlew :macrobenchmark:connectedBenchmarkAndroidTest
```

On an emulator, Macrobenchmark refuses to run by default (unreliable performance counters,
unlocked screen, etc.); suppress those specific checks for local/emulator validation only —
**do not use this for numbers you intend to keep**, only to confirm the tests still execute:

```bash
./gradlew :macrobenchmark:connectedBenchmarkAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.suppressErrors=EMULATOR,DEBUGGABLE,UNLOCKED,LOW-BATTERY,ACTIVITY-MISSING,NOT-PROFILEABLE
```

Run a single class/method with `-Pandroid.testInstrumentationRunnerArguments.class=<FQCN>[#method]`.

Results (including per-iteration frame-timing/startup JSON) land under
`macrobenchmark/build/outputs/connected_android_test_additional_output/benchmark/` and are also
summarized in Android Studio's "App Insights" / a `BenchmarkResult` json in that same directory.

## Baseline measurements

**Not yet captured.** Frame-timing and startup numbers are only meaningful on real hardware —
emulator perf counters are unreliable and explicitly suppressed above just to prove the tests
run — so this section stays empty until someone runs
`./gradlew :macrobenchmark:connectedBenchmarkAndroidTest` against a real Pixel 8 (and ideally a
representative lower-end device, per #330) with production release signing.

When that run happens, record here per journey: median/p90 frame duration, janky-frame count and
percentage, and (for `StartupBenchmark`) time-to-initial-display and time-to-full-display across
the 5 iterations each test performs, plus the device model, Android version and app version/commit
tested. Re-run and update this table after any change called out in #316's sub-issues so
regressions or improvements are visible against a fixed point, per the "performance changes can
be compared against the baseline" acceptance criterion on #321.

## Regression-protection workflow

Macrobenchmarks are retained as representative, repeatable journeys; they are not a shared-runner
performance gate. Frame timings on GitHub-hosted emulators are too variable to make a reliable
pass/fail threshold, and running the full suite in every PR would add cost without producing
actionable signal. Existing CI continues to provide the deterministic protection: it builds,
unit-tests, and lints Android-impacting changes.

For a PR that changes rendering, Compose state, navigation, input responsiveness, database work
on an interactive path, or benchmark code itself:

1. State the affected journey(s) in the PR description and run the matching Macrobenchmark class
   before and after the change on the same physical device, Android version, app variant, and
   device condition. Prefer a charged device with no foreground workload, and allow it to cool
   between runs when necessary.
2. Keep the raw `BenchmarkResult` JSON files with the PR's local review material or attach a
   small redacted summary to the PR. Do not commit device-specific raw results to the repository
   unless they establish or intentionally replace the documented baseline.
3. Record the comparison using this compact template, including an explanation for a meaningful
   regression or for why device measurement was not possible:

   | Journey | Device / Android | Commit | Median / p90 | Janky frames | Notes |
   | --- | --- | --- | --- | --- | --- |
   | `Class#method` | model, API | before / after | before → after | before → after | thermal state, data shape, interpretation |

4. Treat the numbers as investigation evidence, not a target to game. Re-run a surprising result,
   check the relevant trace/profile, and preserve functional, accessibility, offline, and Actual
   sync correctness before accepting a trade-off.

Reviewers should request this comparison when the change is performance-sensitive. Documentation-
only changes and ordinary localized behavior changes do not need a Macrobenchmark run. If timing
data is unavailable, record that limitation in the PR rather than substituting emulator numbers
or adding a hard CI threshold.

## Known limitations / follow-ups

- `TransactionsScreenBenchmark#toggleClearedFilter` matches "Cleared"/"Uncleared" by visible text;
  if a future screen change puts another "Cleared"/"Uncleared" label on the same screen (e.g. a
  balance breakdown), the selector may need to be narrowed (e.g. to a clickable-chip selector).
- `AddEditTransactionBenchmark#keypadEntry` drives `CalculatorAmountSheet`'s on-screen digit pad,
  not the IME — the amount field is `readOnly` by design and delegates entry to that sheet.
- Category-group reorder is exercised via the "Move Essentials group up/down" buttons rather than
  a simulated drag gesture (flakier and harder to make deterministic under UiAutomator); this
  still exercises the same move-and-persist code path (`CategoryDragReorder`/
  `CategoryReorderPlanner`) that PR #250's drag reorder implementation uses.
