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

### 2.1 Journal filename and placement (decided)

- [x] Journal path is a sidecar next to the main database file.
- [x] Base naming rule: `<db-file-path>-journal`.
- [x] No alternate temp directory behavior in Phase 3.
- [x] Journal is treated as owned by exactly one database file path.

### 2.2 Minimal rollback journal format (decided)

- [x] File layout is append-only during an active transaction and uses fixed-size header + repeated page records.

- [x] **Header fields** (written at journal creation):
  - Magic bytes (constant identifier for rollback journal)
  - Format version (Phase 3 fixed integer)
  - Database page size (bytes)
  - Reserved header flags (set to 0 in Phase 3)

- [x] **Transaction status marker**:
  - Header includes a commit marker field with two states:
    - `INCOMPLETE` (default after creation)
    - `COMMITTED` (set only at commit finalization)

- [x] **Page record format**:
  - Page number (`int`/fixed-width numeric field)
  - Page payload length (must equal database page size in Phase 3)
  - Raw page bytes (original preimage)

- [x] **Duplication rule**:
  - Only the first preimage of a page in a transaction is journaled.
  - Rewrites to the same page during the same transaction do not append duplicate preimages.

- [x] **Integrity checks (minimal but required)**:
  - Reject journal if magic/version/page-size fields are invalid.
  - Reject records with invalid page number or truncated payload.
  - No per-record checksum in Phase 3 unless implementation reveals a concrete correctness gap.

### 2.3 Journal lifecycle and state transitions (decided)

- [x] **Creation**:
  - Journal file is created lazily on first page mutation inside an explicit transaction.
  - Header is written immediately with `INCOMPLETE` status.

- [x] **Population**:
  - Before mutating any DB page for the first time in a transaction, write that page's original preimage to the journal.

- [x] **Durability ordering**:
  - Journal content needed for rollback is synced before corresponding DB page overwrite.

- [x] **Commit finalization**:
  - After DB-side commit durability steps, set journal status to `COMMITTED`.
  - Then remove the journal file (preferred) as the final cleanup step.
  - If deletion fails, startup logic must still treat a `COMMITTED` journal as non-recovery input.

- [x] **Rollback finalization**:
  - Restore pages from journal preimages.
  - Sync restored DB state as required for consistency.
  - Remove journal file after successful rollback completion.

### 2.4 Journal presence at startup/reopen (decided)

- [x] If no journal exists, continue normal startup.

- [x] If journal exists:
  - Parse and validate header/records conservatively.
  - If status is `INCOMPLETE` and structure is valid enough to replay, perform rollback recovery.
  - If status is `COMMITTED`, treat as stale and remove/ignore safely.

- [x] Corrupt/ambiguous journal behavior:
  - Never treat corruption as successful commit.
  - Prefer integrity-preserving failure mode if safe recovery cannot be proven.

---

## 3) Transaction state machine (engine-level behavior)

### 3.1 Internal states (decided)

- [x] `IDLE`
  - No explicit transaction is active.
  - Statements execute under existing autocommit baseline behavior.

- [x] `IN_TXN`
  - Explicit transaction is active after successful `BEGIN`.
  - Page mutations are tracked against rollback journal rules.

- [x] `COMMITTING`
  - Transitional internal state entered only during commit finalization.
  - No new SQL statement may begin while this transition is in progress.

- [x] `ROLLING_BACK`
  - Transitional internal state entered only during rollback replay/finalization.
  - No new SQL statement may begin while this transition is in progress.

### 3.2 State transitions (decided)

- [x] Primary transitions:
  - `IDLE -> IN_TXN` on successful `BEGIN`
  - `IN_TXN -> COMMITTING -> IDLE` on successful `COMMIT`
  - `IN_TXN -> ROLLING_BACK -> IDLE` on successful `ROLLBACK`

- [x] Failure-path transitions:
  - `IN_TXN -> ROLLING_BACK -> IDLE` if commit encounters a correctness-threatening failure before finalization can be completed.
  - `COMMITTING -> IDLE` only after commit finalization outcome is unambiguous.
  - `ROLLING_BACK -> IDLE` only after rollback restore/finalization completes or an integrity-preserving hard failure is raised.

### 3.3 Command validity matrix (decided)

- [x] In `IDLE`:
  - `BEGIN`: valid, enters `IN_TXN`.
  - `COMMIT`: invalid, return explicit "no active transaction" error.
  - `ROLLBACK`: invalid, return explicit "no active transaction" error.

- [x] In `IN_TXN`:
  - `BEGIN`: invalid, return explicit "transaction already active" error.
  - `COMMIT`: valid, enters `COMMITTING`.
  - `ROLLBACK`: valid, enters `ROLLING_BACK`.

- [x] In `COMMITTING` or `ROLLING_BACK`:
  - `BEGIN`, `COMMIT`, and `ROLLBACK` are all invalid for user-level re-entry.
  - Engine surfaces a busy/illegal-state style transaction error (exact wording to be fixed during implementation).

### 3.4 Operation legality by state (decided)

- [x] Reads in `IN_TXN` use in-memory/current transaction view consistent with existing engine architecture.
- [x] Writes in `IN_TXN` are allowed and must follow journal-before-overwrite ordering.
- [x] No writes are accepted once state enters `COMMITTING` or `ROLLING_BACK` except internal recovery/finalization work.
- [x] Any unrecoverable I/O/state inconsistency during transition states must prefer integrity-preserving failure over continuing normal execution.

### 3.5 Error contract principles (decided)

- [x] Transaction-state misuse errors are deterministic and state-derived (not best-effort).
- [x] Errors for invalid transaction commands do not mutate state.
- [x] On transition failure, externally visible state after error must be either:
  - Clean `IDLE` with no partial transaction effects, or
  - Explicit failure requiring reopen/recovery; never silent partial success.

---

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
