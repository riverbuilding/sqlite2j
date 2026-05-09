# Phase 3 — Implementation TODO (Transactions + Rollback Journal)

> This checklist translates `docs/phase-3-todo.md` design decisions into implementation steps.
> Do not treat this as feature-complete until Section 9 gates pass.

## 0) Execution rules

- [ ] Keep implementation compatible with **JDK 15**.
- [ ] Implement only what is required for current production behavior in Phase 3.
- [ ] Prefer minimal correctness-first behavior over optimization.
- [ ] Run `mvn test` before each merge-ready checkpoint.

---

## 1) Transaction SQL surface (`BEGIN` / `COMMIT` / `ROLLBACK`)

- [ ] Confirm parser recognizes:
  - [ ] `BEGIN`
  - [ ] `BEGIN TRANSACTION`
  - [ ] `COMMIT`
  - [ ] `ROLLBACK`
- [ ] Ensure unsupported `BEGIN` modifiers (`DEFERRED`/`IMMEDIATE`/`EXCLUSIVE`) are handled per Phase 3 decision (clear unsupported/invalid behavior).
- [ ] Add/update planner nodes (or equivalent dispatch path) for transaction control statements only.
- [ ] Wire executor entry points for begin/commit/rollback actions with no storage changes yet in this step.

**Checkpoint gate**
- [ ] Transaction statements are recognized and routed deterministically.
- [ ] Invalid forms produce deterministic errors.

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
- [ ] State transition validity and invalid-command matrix.
- [ ] Journal header/record parsing and validation failures.
- [ ] First-preimage-only tracking logic.

### 8.2 Integration tests
- [ ] `BEGIN` + writes + `COMMIT` persists.
- [ ] `BEGIN` + writes + `ROLLBACK` discards.
- [ ] Multi-page mutations in one transaction.
- [ ] Repeated updates to same page journal only first preimage.

### 8.3 Crash/reopen tests
- [ ] Crash after journal preimage sync, before DB overwrite.
- [ ] Crash after some DB overwrites, before commit finalization.
- [ ] Reopen with `INCOMPLETE` journal triggers recovery.
- [ ] Reopen with `COMMITTED` journal performs stale cleanup.

### 8.4 Negative/error tests
- [ ] `COMMIT`/`ROLLBACK` with no active transaction.
- [ ] `BEGIN` while transaction active.
- [ ] Command rejection during transitional states.

**Checkpoint gate**
- [ ] `mvn test` passes with new coverage and no regression.

---

## 9) Definition of done (implementation)

- [ ] All Phase 3 transaction commands implemented with documented semantics.
- [ ] Journal-before-overwrite guarantee enforced.
- [ ] Commit/rollback ordering semantics implemented and tested.
- [ ] Crash recovery on reopen implemented and idempotent.
- [ ] Non-goals remain out of scope (no WAL/savepoints/concurrency expansion/optimizations that weaken correctness).
- [ ] Full test suite passes via:
  - [ ] `mvn test`
