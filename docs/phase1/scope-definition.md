# Phase 1 Scope Definition

## Scope Freeze (Mandatory)
Phase 1 is strictly limited to **single-table operations** for the minimal vertical slice.

### Included in Phase 1
- `CREATE TABLE`
- `INSERT`
- `SELECT *`

### Explicitly Excluded from Phase 1
- Indexes (`CREATE INDEX`, index scans)
- `WHERE` filtering
- `ORDER BY`
- `UPDATE`
- `DELETE`
- Transactions (`BEGIN`, `COMMIT`, `ROLLBACK`)

Any work outside the included list above must be deferred to later phases.

## Behavioral Constraints
- Operate on one user table at a time for Phase 1 acceptance.
- Selection shape is `SELECT * FROM <table>` only.
- Unsupported SQL must return clear, deterministic errors.

## Done Criteria
Phase 1 scope is considered complete only when all acceptance scripts in `testdata/sql/phase1/` pass against sqlite2j according to expected outcomes.
