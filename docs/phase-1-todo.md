# Phase 1 TODO — Minimal End-to-End Vertical Slice

## Objective
Deliver the smallest runnable sqlite2j slice that executes:
- `CREATE TABLE`
- `INSERT`
- `SELECT *`

through a complete path: parser -> code generator -> VM -> B-tree -> pager.

## 1. Scope Definition
- [ ] Freeze Phase 1 scope to single-table operations only.
- [ ] Exclude indexes, WHERE filtering, ORDER BY, UPDATE, DELETE, and transactions from Phase 1 implementation.
- [ ] Define explicit acceptance SQL scripts for done criteria.

## 2. Minimal SQL Frontend
- [ ] Implement tokenization for Phase 1 grammar subset.
- [ ] Implement parser support for:
  - [ ] `CREATE TABLE <name>(<col defs>)`
  - [ ] `INSERT INTO <name> VALUES (...)`
  - [ ] `SELECT * FROM <name>`
- [ ] Produce stable parse trees/statement objects with deterministic error reporting.
- [ ] Add parse-only tests for valid and invalid statements.

## 3. Minimal Catalog/Schema Handling
- [ ] Create in-memory schema registry abstraction for tables.
- [ ] Wire `CREATE TABLE` to schema registration and persistent catalog path placeholder.
- [ ] Validate duplicate table creation behavior.
- [ ] Add tests for table creation metadata and duplicate errors.

## 4. Code Generation (Phase 1 subset)
- [ ] Define initial opcode set for table create/insert/full-scan select.
- [ ] Add statement-to-bytecode lowering for Phase 1 SQL statements.
- [ ] Define deterministic opcode output format for tests.
- [ ] Add golden tests for codegen output.

## 5. VM Execution Pipeline
- [ ] Implement instruction dispatcher loop for Phase 1 opcodes.
- [ ] Implement runtime registers/value container for int/text/null baseline types.
- [ ] Implement table cursor open/scan primitives needed by `SELECT *`.
- [ ] Implement row emission API for result iteration.
- [ ] Add VM tests for instruction semantics and end-to-end query stepping.

## 6. B-tree + Pager Minimal Path
- [ ] Define minimal page and cell format required for table row storage.
- [ ] Implement table insert into B-tree leaf path (minimal split strategy acceptable if bounded/documented).
- [ ] Implement full table scan cursor traversal.
- [ ] Implement pager read/write page lifecycle for single-file DB.
- [ ] Add deterministic binary fixture tests for page write/read behavior.

## 7. API + CLI Thin Slice
- [ ] Expose minimal API flow:
  - [ ] `open`
  - [ ] `prepare`
  - [ ] `step`
  - [ ] `close`
- [ ] Add command-line shell execution for one-shot SQL and simple interactive mode.
- [ ] Ensure `SELECT *` results are printed consistently for tests.

## 8. Deterministic Testing Baseline for Phase 1
- [ ] Add end-to-end integration tests for canonical SQL scripts.
- [ ] Add deterministic output snapshots for CLI smoke tests.
- [ ] Add persistence smoke test: close/reopen DB and verify `SELECT *` result stability.
- [ ] Add negative tests for unsupported statements returning clear errors.

## 9. Phase 1 Exit Criteria
- [ ] A user can create a table, insert rows, and select all rows through API.
- [ ] Same flow works through CLI.
- [ ] Database state persists across process restart.
- [ ] All Phase 1 tests pass deterministically in CI.
- [ ] Phase 2 backlog is prepared without Phase 1 architectural rework.

## 10. Non-Goals for Phase 1
- [ ] No index creation or index scans.
- [ ] No WHERE evaluation.
- [ ] No ORDER BY sorting.
- [ ] No UPDATE/DELETE.
- [ ] No rollback journal recovery logic (planned Phase 3).
