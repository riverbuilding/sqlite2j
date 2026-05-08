# Phase 3 — Transactions + Rollback Journal

> Planning document only. No implementation code in this phase doc.

## 1) Define scope and invariants first

### 1.1 Phase 3 scope (decided)

- [x] Support the following SQL transaction statements in Phase 3:
  - `BEGIN` (and `BEGIN TRANSACTION` synonym)
  - `COMMIT`
  - `ROLLBACK`
- [x] Transaction model for Phase 3:
  - Single-writer assumption
  - No nested transactions
  - No savepoints
  - Existing autocommit behavior remains the baseline outside explicit transactions
- [x] Out-of-scope for Phase 3 (explicit):
  - WAL mode
  - Multi-writer concurrency
  - Transaction isolation tuning/variants beyond current baseline

### 1.2 Correctness invariants (decided)

- [x] **Atomicity**
  - A transaction’s effects become fully visible only after successful `COMMIT`.
  - If `COMMIT` does not complete successfully, the database must be restorable to the exact pre-`BEGIN` state.

- [x] **Rollback correctness**
  - `ROLLBACK` restores all modified pages touched during the active transaction to their original preimage captured before first modification.
  - After successful rollback, no logical effects of that transaction remain.

- [x] **Crash recovery correctness**
  - On reopen, if an incomplete transaction is detected via rollback journal state, recovery restores the pre-transaction state.
  - Recovery must be idempotent (safe if interrupted and retried).

- [x] **Durability boundary (Phase 3 definition)**
  - A successful `COMMIT` is considered durable once commit finalization completes according to the journal lifecycle rules defined in this document.

- [x] **Conservative safety rule**
  - On ambiguous/corrupt journal state, prefer fail-safe behavior that protects database integrity over availability/performance.

### 1.3 SQL behavior constraints (decided for now)

- [x] `BEGIN` while already in a transaction returns an explicit transaction-state error.
- [x] `COMMIT` with no active transaction returns an explicit transaction-state error.
- [x] `ROLLBACK` with no active transaction returns an explicit transaction-state error.
- [x] `BEGIN` modifiers (e.g., `DEFERRED`, `IMMEDIATE`, `EXCLUSIVE`) are not introduced in Phase 3 unless already required by current parser behavior; otherwise return clear unsupported/invalid syntax behavior.

---

## 2) Journal file format and lifecycle design

- [ ] Specify rollback journal file naming/location strategy (e.g., sidecar file next to DB)
- [ ] Define minimal journal format for correctness-first implementation:
  - Header/magic + version
  - Transaction state marker(s)
  - Original page images (page number + bytes)
  - Optional checksum/length fields (if needed for corruption detection in this phase)
- [ ] Define journal lifecycle states:
  - Created on first write in transaction
  - Populated with original pages before any page overwrite
  - Synced before mutating DB pages (ordering guarantee)
  - Deleted/truncated/invalidated at successful commit
- [ ] Define behavior if journal exists at open/startup:
  - Recovery required vs stale/clean journal detection

## 3) Transaction state machine (engine-level behavior)

- [ ] Design internal transaction state transitions:
  - `IDLE -> IN_TXN -> COMMITTING -> IDLE`
  - `IN_TXN -> ROLLING_BACK -> IDLE`
- [ ] Define invalid command handling:
  - `COMMIT` with no active transaction
  - `ROLLBACK` with no active transaction
  - `BEGIN` while already in transaction
- [ ] Define what operations are legal in each state and expected errors

## 4) Write path ordering rules (correctness before optimization)

- [ ] Specify write-ahead ordering for rollback journal:
  1. Capture original page into journal (first time page touched in txn)
  2. Flush/sync journal to durable media
  3. Apply page change to DB
- [ ] Define commit ordering:
  - Ensure DB changes durable as required
  - Mark commit completion via journal finalization strategy
- [ ] Define rollback ordering:
  - Restore pages from journal in deterministic order
  - Flush restored state as needed
  - Remove/invalidate journal after successful rollback
- [ ] Explicitly avoid performance optimizations for now (batching, group commit, etc.)

## 5) Crash recovery on reopen

- [ ] Define startup recovery algorithm:
  - Detect hot/incomplete journal
  - Validate journal sanity (header/state/length)
  - Replay rollback (restore original pages)
  - Finalize cleanup and transition to clean startup
- [ ] Define behavior for partial/corrupt journal:
  - Conservative fallback that preserves integrity (fail-safe)
- [ ] Define idempotency requirements:
  - Re-running recovery after interruption should remain safe

## 6) Parser/planner/executor touchpoints (design tasks only)

- [ ] Map SQL `BEGIN`/`COMMIT`/`ROLLBACK` to execution actions
- [ ] Decide whether `BEGIN` variants (`DEFERRED/IMMEDIATE/EXCLUSIVE`) are:
  - Unsupported for now (with clear error), or
  - Accepted but treated uniformly in this phase
- [ ] Define user-visible error messages and codes for transaction misuse

## 7) Test plan (must pass on JDK 15 + `mvn test`)

- [ ] Unit tests: transaction state machine edge cases
- [ ] Integration tests:
  - `BEGIN` + writes + `COMMIT` persists changes
  - `BEGIN` + writes + `ROLLBACK` discards changes
  - Multiple page updates in single txn
  - Same page updated multiple times (journal only needs original preimage)
- [ ] Crash simulation tests (deterministic):
  - Crash after journal write but before DB write
  - Crash after some DB writes but before commit finalization
  - Reopen triggers recovery and restores consistent pre-txn state
- [ ] Reopen tests:
  - Clean reopen (no journal)
  - Hot journal recovery path
- [ ] Negative tests:
  - `COMMIT`/`ROLLBACK` outside txn
  - `BEGIN` inside active txn

## 8) Non-goals for Phase 3 (to prevent scope creep)

- [ ] No WAL mode
- [ ] No concurrency control beyond current baseline
- [ ] No savepoints/nested transactions
- [ ] No journal format optimization beyond correctness
- [ ] No speculative abstractions not used by production path

## 9) Definition of done for Phase 3

- [ ] `BEGIN`, `COMMIT`, `ROLLBACK` functionally correct
- [ ] Rollback journal guarantees atomic rollback semantics
- [ ] Crash recovery on reopen works for defined crash points
- [ ] Behavior documented (including limitations)
- [ ] Full test suite passes with:
  - [ ] `mvn test`
