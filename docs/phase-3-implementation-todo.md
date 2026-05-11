# Phase 3 — Implementation TODO (Transactions + Rollback Journal)

> This checklist translates `docs/phase-3-todo.md` design decisions into implementation steps.
> Do not treat this as feature-complete until Section 9 gates pass.

## 0) Execution rules (decided)

- [x] **JDK compatibility gate**
  - Every Phase 3 change must compile and run on **JDK 15**.
  - Any Java 16+ language or library feature is disallowed.

- [x] **Minimum necessary production scope**
  - Implement only behavior required for Phase 3 transaction correctness (`BEGIN`/`COMMIT`/`ROLLBACK` + rollback journal).
  - Do not add speculative abstractions, storage infrastructure, or "future" extension points unless immediately required by production behavior in the same step.

- [x] **Correctness-first over optimization**
  - Preserve journal-before-overwrite and recovery correctness even when this increases I/O or reduces throughput.
  - Defer performance tuning (batching/group-commit/fast paths) until after correctness gates are passing.

- [x] **Validation cadence**
  - Run `mvn test` at each merge-ready checkpoint and before finalizing any Phase 3 implementation PR.
  - A checkpoint is not complete if tests are not green.

---

## 1) Transaction SQL surface (`BEGIN` / `COMMIT` / `ROLLBACK`) (decided)

- [x] **Accepted statements in Phase 3**
  - `BEGIN`
  - `BEGIN TRANSACTION` (same behavior as `BEGIN`)
  - `COMMIT`
  - `ROLLBACK`

- [x] **Parser behavior contract**
  - Transaction control statements must be recognized deterministically and mapped to dedicated transaction-control statement types.
  - Parsing must preserve statement kind so executor dispatch can enforce state-machine rules reliably.

- [x] **`BEGIN` modifier behavior in Phase 3**
  - `BEGIN DEFERRED`, `BEGIN IMMEDIATE`, and `BEGIN EXCLUSIVE` are out of scope unless already required by current parser compatibility.
  - If not already supported, return clear unsupported/invalid behavior rather than silently aliasing to a different mode.

- [x] **Planner/executor touchpoint constraints**
  - Planner (or equivalent dispatch layer) should route `BEGIN`/`COMMIT`/`ROLLBACK` directly to transaction-control actions.
  - Do not introduce speculative storage abstractions at this step; only wire the minimum control-path behavior required for transaction commands.

**Checkpoint gate**
- [x] Transaction statements are recognized and routed deterministically.
- [x] Unsupported/invalid transaction forms produce deterministic errors.

---

## 2) Engine transaction state machine

- [ ] Introduce/verify explicit internal states:
  - [ ] `IDLE`
  - [ ] `IN_TXN`
  - [ ] `COMMITTING`
  - [ ] `ROLLING_BACK`
- [ ] Implement allowed transitions only:
  - [ ] `IDLE -> IN_TXN` (`BEGIN`)
  - [ ] `IN_TXN -> COMMITTING -> IDLE` (`COMMIT` success)
  - [ ] `IN_TXN -> ROLLING_BACK -> IDLE` (`ROLLBACK` success)
- [ ] Enforce invalid command behavior:
  - [ ] `BEGIN` in `IN_TXN` errors without mutating state.
  - [ ] `COMMIT`/`ROLLBACK` in `IDLE` error without mutating state.
  - [ ] Re-entry commands rejected during `COMMITTING`/`ROLLING_BACK`.
- [ ] Ensure transition failures end in integrity-preserving outcomes only.

**Checkpoint gate**
- [ ] State misuse errors are deterministic and non-mutating.
- [ ] No user-visible partial-success commit outcomes.

---

## 3) Rollback journal file primitives

- [ ] Add journal path derivation: `<db-file-path>-journal`.
- [ ] Implement minimal header read/write with fields from Phase 3 design:
  - [ ] magic
  - [ ] version
  - [ ] page size
  - [ ] reserved flags
  - [ ] commit marker (`INCOMPLETE`/`COMMITTED`)
- [ ] Implement page-record append/read primitives:
  - [ ] page number
  - [ ] payload length
  - [ ] preimage bytes
- [ ] Add minimal validation gates (reject bad magic/version/page-size/truncation/invalid page numbers).

**Checkpoint gate**
- [ ] Journal file can be created, parsed, and validated for well-formed/corrupt cases.

---

## 4) Write-path integration (journal-before-overwrite)

- [ ] On first page mutation in explicit transaction, lazily create journal and write `INCOMPLETE` header.
- [ ] Track whether page preimage already journaled within current transaction.
- [ ] For first touch of each page:
  1. [ ] capture original preimage
  2. [ ] append to journal
  3. [ ] sync journal rollback bytes
  4. [ ] allow DB page overwrite
- [ ] For subsequent touches in same transaction, skip duplicate preimage append.
- [ ] Keep statement failure behavior aligned with current engine semantics unless integrity requires escalation.

**Checkpoint gate**
- [ ] No DB overwrite occurs before durable rollback bytes exist for first page touch.

---

## 5) Commit path

- [ ] Transition `IN_TXN -> COMMITTING` at commit start.
- [ ] Implement commit finalization order:
  1. [ ] flush/sync DB changes required by Phase 3 durability boundary
  2. [ ] set journal marker to `COMMITTED`
  3. [ ] sync marker durability
  4. [ ] remove journal file (or unambiguously invalidate)
  5. [ ] transition to `IDLE`
- [ ] Ensure commit is never reported successful if outcome is ambiguous.
- [ ] Route pre-finalization failures into rollback/recovery-safe behavior.

**Checkpoint gate**
- [ ] Successful commit leaves durable DB changes and no hot journal.

---

## 6) Rollback path

- [ ] Transition `IN_TXN -> ROLLING_BACK` at rollback start.
- [ ] Restore pages from journal preimages.
- [ ] Flush/sync restored state as required for consistency.
- [ ] Remove/invalidate journal.
- [ ] Transition to `IDLE`.
- [ ] Ensure rollback path is safe to retry after interruption.

**Checkpoint gate**
- [ ] Post-rollback database matches pre-`BEGIN` state for touched pages.

---

## 7) Startup crash recovery on reopen

- [ ] On open, detect presence of `<db-file-path>-journal` before mutating operations.
- [ ] If no journal: normal startup.
- [ ] If journal exists:
  - [ ] validate structure conservatively
  - [ ] if marker `INCOMPLETE` and replayable: perform rollback recovery
  - [ ] if marker `COMMITTED`: treat as stale, cleanup/ignore safely
- [ ] For ambiguous/corrupt journal states, follow integrity-preserving fail-closed behavior.
- [ ] Ensure recovery is idempotent if interrupted and retried.

**Checkpoint gate**
- [ ] Reopen after simulated crash restores pre-transaction state for incomplete transactions.

---

## 8) Tests to implement (JDK 15 + `mvn test`)

### 8.1 Unit tests
- [x] State transition validity and invalid-command matrix.
- [x] Journal header/record parsing and validation failures.
- [x] First-preimage-only tracking logic.

### 8.2 Integration tests
- [x] `BEGIN` + writes + `COMMIT` persists.
- [x] `BEGIN` + writes + `ROLLBACK` discards.
- [ ] Multi-page mutations in one transaction.
- [x] Repeated updates to same page journal only first preimage.

### 8.3 Crash/reopen tests
- [x] Crash after journal preimage sync, before DB overwrite.
- [x] Crash after some DB overwrites, before commit finalization.
- [x] Reopen with `INCOMPLETE` journal triggers recovery.
- [x] Reopen with `COMMITTED` journal performs stale cleanup.

### 8.4 Negative/error tests
- [x] `COMMIT`/`ROLLBACK` with no active transaction.
- [x] `BEGIN` while transaction active.
- [x] Command rejection during transitional states.

**Checkpoint gate**
- [x] `mvn test` passes with new coverage and no regression.

---

## 9) Definition of done (implementation)

- [x] Resolve known commit-protocol deviation:
  - [x] Replace hybrid marker/delete commit finalization with one consistent protocol.
  - [x] Prefer SQLite-like rollback-journal commit ordering and durable journal cleanup semantics.
- [x] All Phase 3 transaction commands implemented with documented semantics.
- [x] Journal-before-overwrite guarantee enforced.
- [x] Commit/rollback ordering semantics implemented and tested.
- [x] Crash recovery on reopen implemented and idempotent.
- [x] Non-goals remain out of scope (no WAL/savepoints/concurrency expansion/optimizations that weaken correctness).
- [x] Full test suite passes via:
  - [x] `mvn test`
