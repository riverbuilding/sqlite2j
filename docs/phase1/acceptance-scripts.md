# Phase 1 Acceptance SQL Scripts

The following scripts define explicit done criteria inputs for Phase 1.

## Script Set
1. `testdata/sql/phase1/01_create_insert_select.sql`
   - Validates core happy path for `CREATE TABLE`, `INSERT`, `SELECT *`.
2. `testdata/sql/phase1/02_single_table_smoke.sql`
   - Validates a second table scenario under same limited SQL set.
3. `testdata/sql/phase1/03_unsupported_features.sql`
   - Validates deterministic rejection of out-of-scope features.

## Expected Outcome Rules
- Scripts 01 and 02 should execute successfully and return inserted rows.
- Script 03 should return deterministic unsupported/invalid-operation errors for excluded statements.
- Error assertions should be based on stable error codes (message patterns second).
