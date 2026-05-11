# Phase 5 TODO — Storage Engine Convergence (B-tree + Pager On-Disk)

> Planning document only. No implementation code in this phase doc.

## 1) Scope and goals

- [ ] Replace temporary VM catalog/row persistence with pager-backed on-disk page storage.
- [ ] Route table and index reads/writes through `sqlite2j-btree` and `sqlite2j-pager`.
- [ ] Keep SQL behavior compatible with existing Phase 4 semantics while changing storage internals.
- [ ] Preserve deterministic behavior across reopen/recovery.

## 2) Explicit constraints

- [ ] Maintain JDK 15 compatibility.
- [ ] Keep implementation minimal and production-driven (no speculative abstractions).
- [ ] Preserve deterministic errors/results for existing supported SQL.
- [ ] Do not broaden SQL surface in this phase unless required for storage convergence.

## 3) Data format convergence

### 3.1 Page/cell encoding
- [ ] Define deterministic table leaf cell encoding for current value types (`INT`, `TEXT`, `NULL`).
- [ ] Define deterministic index key/value encoding for single-column indexes.
- [ ] Define and lock endianness and header/version markers.

### 3.2 File layout
- [ ] Define minimal database file header for page-backed persistence.
- [ ] Define page numbering/allocation policy.
- [ ] Ensure deterministic serialization for identical logical state.

### 3.3 Compatibility migration policy
- [ ] Decide migration behavior from current `sqlite2j-vm-v1` text format to page format.
- [ ] Return deterministic, explicit error for unsupported/ambiguous migration states.

## 4) Runtime wiring (VM -> B-tree -> Pager)

### 4.1 Read path
- [ ] Use B-tree cursor read path for table scans.
- [ ] Use index B-tree lookup path for eligible indexed predicates.
- [ ] Preserve result equivalence with prior VM-layer behavior.

### 4.2 Write path
- [ ] Route INSERT table/index mutations through B-tree operations.
- [ ] Route UPDATE/DELETE through B-tree row mutation/removal flow.
- [ ] Keep index maintenance and table mutation atomic under existing transaction model.

### 4.3 Metadata path
- [ ] Persist schema/index metadata in page-backed catalog structures.
- [ ] Ensure metadata open/load is deterministic and validates format/header/version.

## 5) Transaction/journal integration

- [ ] Preserve current transaction command semantics (`BEGIN`, `COMMIT`, `ROLLBACK`).
- [ ] Ensure journal-before-overwrite ordering still holds when pager pages are dirty.
- [ ] Ensure reopen recovery restores both table and index consistency.

## 6) Safety and fallback policy

- [ ] On on-disk format corruption/ambiguity, fail closed with deterministic integrity error.
- [ ] Do not silently fall back to incompatible persistence modes.
- [ ] Keep fallback behavior deterministic when safe downgrade paths are explicitly supported.

## 7) Test plan

### 7.1 Parser/planner invariants
- [ ] Existing parser/codegen contracts remain stable (no regressions).
- [ ] Planner eligibility behavior for indexed equality remains deterministic.

### 7.2 Storage integration tests
- [ ] CREATE/INSERT/SELECT round-trip via B-tree/pager path.
- [ ] CREATE INDEX over populated tables yields expected lookup results.
- [ ] DML after index creation keeps table/index lookup parity.

### 7.3 Reopen/recovery tests
- [ ] Reopen preserves table data and index lookup behavior.
- [ ] Recovery from interrupted writes preserves pre-commit consistency.
- [ ] Recovery is idempotent across repeated reopen attempts.

### 7.4 Determinism tests
- [ ] Repeated identical workloads produce byte-stable or behavior-stable outcomes (as specified).
- [ ] Error messages/codes for corrupt/unsupported file states are deterministic.

## 8) Exit criteria

- [ ] Production read/write paths use B-tree + pager rather than temporary VM text persistence.
- [ ] Supported SQL behavior remains semantically equivalent to Phase 4 baseline.
- [ ] Table/index consistency holds through DML and transaction boundaries.
- [ ] Reopen/recovery tests pass for page-backed storage path.
- [ ] Full suite passes with:
  - [ ] `mvn test`
