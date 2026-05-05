# SQLite 2 Compatibility Matrix

## Purpose
Track compatibility targets against SQLite 2.0 behavior and explicitly separate in-scope vs out-of-scope features.

## In Scope (Project Baseline)
- Core SQL operations: `CREATE TABLE`, `DROP TABLE`, `INSERT`, `UPDATE`, `DELETE`, `SELECT`.
- `WHERE`, `ORDER BY`, and index-backed behavior.
- Transaction commands: `BEGIN`, `COMMIT`, `ROLLBACK`.
- Rollback journal recovery behavior.
- File locking model approximating SQLite 2.0 semantics in pure Java.

## Out of Scope for Phase 0
- Modern SQLite 3.x features not required for compatibility tooling.
- Performance optimizations beyond correctness and determinism.
- JDBC adapter layer.

## Notes
This matrix will be expanded with per-feature compatibility status as implementation progresses.
