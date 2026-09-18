# Review guide

Read `AGENTS.md` at the repo root first and apply its conventions.

Focus review comments on:

- Correctness bugs, data loss, and security issues (sync, CRDT, encryption, migrations).
- Divergence from `AGENTS.md` conventions (repository map, state/event patterns, Actuali/Actual parity references).
- Missing test coverage for behavior changes.
- Significant design problems or likely-broken edge cases.

Skip style nits and low-value suggestions covered by lint/detekt.
