# Phase 1 Exit Criteria

Phase 1 is ready to close when the minimal vertical slice is stable and deterministic.

## Criteria
- **API flow:** A user can create a table, insert rows, and select all rows through the Java API.
- **CLI flow:** The same create/insert/select flow works through the command-line shell.
- **Persistence smoke:** Closing and reopening the same database path preserves `SELECT *` results.
- **Deterministic tests:** Phase 1 tests pass with the Maven test configuration.
- **Phase 2 readiness:** The Phase 2 backlog is documented without requiring Phase 1 architectural rework.

## Evidence
- API flow is covered by `Sqlite2jConnectionTest`.
- CLI output is snapshotted by `Sqlite2jShellTest` and `testdata/expected/phase1/cli_smoke.out`.
- Canonical SQL scripts and persistence/negative behavior are covered by `Phase1EndToEndTest`.
- Phase 2 backlog is tracked in `docs/phase2/backlog.md`.
