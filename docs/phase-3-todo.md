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

### 4.1 Fundamental ordering contract (decided)

- [x] Rollback-journal mode in Phase 3 follows strict **journal-before-database** ordering.
- [x] No database page overwrite is allowed until the corresponding rollback preimage is durably recorded.
- [x] Correctness takes priority over throughput/latency in all ordering decisions.

### 4.2 Per-page mutation sequence (decided)

- [x] For each page mutation within an explicit transaction:
  1. Check whether this page already has a journaled preimage in current transaction.
  2. If not journaled yet, append the original page preimage record to journal.
  3. Sync journal so newly required rollback bytes are durable.
  4. Apply in-memory and/or on-disk page mutation according to existing engine architecture.

- [x] If page preimage was already journaled in the same transaction:
  - Skip preimage append.
  - Continue with page mutation (subject to existing durability boundaries).

### 4.3 Multi-page statement behavior (decided)

- [x] Statements mutating multiple pages must obey the same per-page rule for each page touched.
- [x] Partial progress inside a statement is acceptable during execution as long as rollback journal guarantees full restoration capability.
- [x] On statement-level failure inside `IN_TXN`, transaction remains active unless failure is classified as unrecoverable for integrity.

### 4.4 Commit ordering (decided)

- [x] Commit path transitions engine to `COMMITTING` state before final durability actions.
- [x] **Known implementation deviation to fix in later phase**:
  - Current implementation uses a hybrid marker-based commit finalization (`COMMITTED` marker + sync + delete).
  - Target protocol should be unified to a strict SQLite-like rollback-journal commit lifecycle (delete/truncate model with durable cleanup semantics).
  - Do not treat the current hybrid style as final architecture.
- [x] Commit finalization order:
  1. Ensure all transaction DB changes required by the Phase 3 durability definition are flushed/synced.
  2. Update journal commit marker to `COMMITTED`.
  3. Sync journal metadata/content as needed so marker transition is durable.
  4. Remove journal file (preferred cleanup) or otherwise invalidate it unambiguously.
  5. Transition to `IDLE` only after the above steps reach a non-ambiguous outcome.

- [x] If commit path fails before non-ambiguous finalization:
  - Prefer rollback/recovery-safe handling over reporting success.
  - Never report commit success while requiring rollback for correctness.

### 4.5 Rollback ordering (decided)

- [x] Rollback path transitions engine to `ROLLING_BACK` state.
- [x] Rollback restore order:
  1. Read journal records and restore original page preimages.
  2. Flush/sync restored DB state as required for consistency.
  3. Remove/invalidate journal file.
  4. Transition back to `IDLE`.

- [x] Rollback must be safe to retry after interruption (idempotent recovery orientation).

### 4.6 Explicit non-optimizations for Phase 3 (decided)

- [x] No group commit or batched fsync optimization.
- [x] No deferred preimage capture.
- [x] No speculative coalescing of journal records beyond first-preimage rule.
- [x] No fast-path that weakens journal-before-overwrite correctness.

---

## 5) Crash recovery on reopen

### 5.1 Recovery trigger detection (decided)

- [x] On database open, check for existence of sidecar journal (`<db-file-path>-journal`).
- [x] If no journal exists, startup proceeds normally with no recovery phase.
- [x] If journal exists, startup enters recovery decision flow before accepting mutating operations.

### 5.2 Journal validation gates (decided)

- [x] Validate minimally required header fields before any replay:
  - Magic bytes
  - Format version
  - Page size compatibility with target database
  - Commit marker field presence/state

- [x] Validate page records conservatively:
  - Page number range sanity
  - Payload length correctness
  - No truncated record tails

- [x] If validation proves journal is stale-safe (`COMMITTED` + structurally sane), treat as cleanup candidate, not replay input.

### 5.3 Recovery decision matrix (decided)

- [x] `COMMITTED` journal:
  - Do not replay rollback.
  - Remove/ignore journal safely and continue startup.

- [x] `INCOMPLETE` journal with valid replayable records:
  - Run rollback replay before normal startup.

- [x] Corrupt/ambiguous journal:
  - Never infer success of interrupted commit.
  - Prefer integrity-preserving failure mode when correctness cannot be proven.

### 5.4 Rollback replay algorithm on reopen (decided)

- [x] Recovery replay steps:
  1. Open journal and parse validated records.
  2. Restore each recorded original page preimage to database.
  3. Flush/sync restored DB state as required by Phase 3 consistency contract.
  4. Remove/invalidate journal.
  5. Mark recovery complete and continue normal startup.

- [x] Replay order may be deterministic record order; correctness requirement is exact restoration of pre-transaction state.

### 5.5 Idempotency and interruption safety (decided)

- [x] Recovery process must be safe to run again after interruption (power loss/crash during recovery).
- [x] Repeated reopen attempts either:
  - Progress toward a clean `IDLE` startup state, or
  - Stop with explicit integrity-protecting failure.
- [x] Recovery must not create a state that appears committed if commit was not durably finalized.

### 5.6 Startup availability vs integrity policy (decided)

- [x] Integrity dominates availability for ambiguous states.
- [x] If neither safe replay nor safe staleness classification is possible, startup must fail closed with an explicit recovery/integrity error.
- [x] No best-effort "continue anyway" mode in Phase 3.

---

## 6) Parser/planner/executor touchpoints (design tasks only)

### 6.1 Parser touchpoints (decided)

- [x] Parser must recognize:
  - `BEGIN`
  - `BEGIN TRANSACTION`
  - `COMMIT`
  - `ROLLBACK`

- [x] `BEGIN` modifier handling in Phase 3:
  - `DEFERRED`, `IMMEDIATE`, `EXCLUSIVE` are not functionally differentiated in Phase 3.
  - If currently accepted by parser grammar, normalize to base `BEGIN` semantic behavior.
  - If not currently accepted, return explicit unsupported/invalid syntax behavior.

- [x] Parser must emit deterministic statement kinds for transaction commands (no heuristic inference downstream).

### 6.2 Planner touchpoints (decided)

- [x] Planner maps transaction statement kinds to dedicated transaction plan nodes/actions, not generic data-mutation nodes.
- [x] Transaction plan actions are side-effectful control operations with no row-result payload requirement.
- [x] Planner must preserve statement order exactly as submitted; no reordering across transaction control boundaries.

### 6.3 Executor touchpoints (decided)

- [x] Executor dispatch rules:
  - `BEGIN` -> call transaction manager begin path (`IDLE -> IN_TXN`)
  - `COMMIT` -> call commit path (`IN_TXN -> COMMITTING -> IDLE`)
  - `ROLLBACK` -> call rollback path (`IN_TXN -> ROLLING_BACK -> IDLE`)

- [x] Executor must enforce state-validity checks before invoking transition work and return deterministic transaction-state errors on invalid commands.
- [x] Executor must not bypass journaling/state-machine contracts for transaction commands.

### 6.4 Error surface and messaging contract (decided)

- [x] Define stable user-visible error categories for Phase 3:
  - No active transaction
  - Transaction already active
  - Transaction busy/illegal transition state
  - Recovery/integrity-required failure

- [x] Error message text can be refined during implementation, but category-to-condition mapping must remain deterministic.
- [x] Invalid transaction-control statements must not partially mutate transaction state.

### 6.5 Observability and diagnostics (decided, minimal)

- [x] Add minimal internal diagnostics hooks/log points at transaction boundaries (`BEGIN`, `COMMIT`, `ROLLBACK`, recovery-trigger).
- [x] No extensive telemetry framework added in Phase 3; keep instrumentation minimal and local to current production behavior.

### 6.6 Compatibility constraints (decided)

- [x] Maintain JDK 15 compatibility in parser/planner/executor changes.
- [x] Do not introduce speculative abstractions beyond what is required to execute Phase 3 behavior.

---

## 7) Test plan (must pass on JDK 15 + `mvn test`)

### 7.1 Test execution constraints (decided)

- [x] All Phase 3 implementation validation must run on JDK 15.
- [x] Baseline validation command is repository-root `mvn test`.
- [x] New tests should be deterministic and avoid timing-sensitive flakiness.

### 7.2 Unit tests — transaction state machine (planned)

- [ ] State transition coverage:
  - `IDLE -> IN_TXN` via `BEGIN`
  - `IN_TXN -> COMMITTING -> IDLE` via `COMMIT`
  - `IN_TXN -> ROLLING_BACK -> IDLE` via `ROLLBACK`

- [ ] Invalid command coverage:
  - `COMMIT` in `IDLE` returns "no active transaction" category
  - `ROLLBACK` in `IDLE` returns "no active transaction" category
  - `BEGIN` in `IN_TXN` returns "transaction already active" category

- [ ] Transition-state guard coverage:
  - Reject re-entrant transaction commands during `COMMITTING`/`ROLLING_BACK`
  - Ensure invalid command attempts do not mutate state

### 7.3 Integration tests — transaction semantics (planned)

- [ ] `BEGIN` + writes + `COMMIT` persists changes after reopen.
- [ ] `BEGIN` + writes + `ROLLBACK` discards changes.
- [ ] Multiple page updates within one transaction commit atomically.
- [ ] Same page updated multiple times journals only first preimage while preserving final logical result.
- [ ] Mixed read/write behavior within `IN_TXN` reflects transaction-local current view.

### 7.4 Crash simulation tests (planned)

- [ ] Crash after journal preimage append but before DB page overwrite.
- [ ] Crash after some DB overwrites but before commit marker finalization.
- [ ] Crash after commit marker set but before journal cleanup.
- [ ] Reopen after each simulated crash executes expected recovery path and restores consistent state.

### 7.5 Reopen/recovery tests (planned)

- [ ] Clean reopen with no journal performs no recovery.
- [ ] Reopen with `INCOMPLETE` replayable journal restores pre-transaction state.
- [ ] Reopen with `COMMITTED` journal treats file as stale cleanup candidate.
- [ ] Reopen with malformed/ambiguous journal fails closed with integrity-preserving error.
- [ ] Recovery idempotency: repeated reopen attempts after interrupted recovery remain safe.

### 7.6 Negative and error-contract tests (planned)

- [ ] Validate deterministic mapping from misuse to error categories.
- [ ] Validate no partial state mutation on invalid transaction-control commands.
- [ ] Validate commit is never reported successful when rollback/recovery is still required for correctness.

### 7.7 Minimal implementation-phase acceptance gates (decided)

- [x] Phase 3 is not considered complete until:
  - Unit, integration, crash-simulation, reopen, and negative tests are implemented for committed behavior.
  - `mvn test` passes on JDK 15.
  - No regression in existing phase compatibility tests.

---

## 8) Non-goals for Phase 3 (to prevent scope creep)

### 8.1 Concurrency and locking non-goals (decided)

- [x] No multi-writer transaction support.
- [x] No advanced lock-mode negotiation beyond current baseline behavior.
- [x] No deadlock detection subsystem work in Phase 3.

### 8.2 Transaction feature non-goals (decided)

- [x] No nested transactions.
- [x] No savepoints.
- [x] No partial rollback-to-savepoint semantics.
- [x] No distributed or cross-database transaction coordination.

### 8.3 Storage/journaling non-goals (decided)

- [x] No WAL mode implementation in Phase 3.
- [x] No journal compression/encryption features.
- [x] No journal format optimization beyond correctness-first minimum.
- [x] No speculative checksum scheme unless required to close a concrete correctness gap.

### 8.4 Performance non-goals (decided)

- [x] No group commit.
- [x] No batched fsync/write-combining optimization passes.
- [x] No opportunistic deferred journaling.
- [x] No performance tuning that weakens journal-before-overwrite guarantees.

### 8.5 SQL surface non-goals (decided)

- [x] No expanded transaction-control syntax beyond currently scoped `BEGIN`/`COMMIT`/`ROLLBACK` behavior.
- [x] No semantic differentiation of `BEGIN DEFERRED/IMMEDIATE/EXCLUSIVE` in Phase 3.
- [x] No compatibility emulation for unsupported advanced transaction SQL features.

### 8.6 Architecture/process non-goals (decided)

- [x] No introduction of broad new abstractions/frameworks without direct Phase 3 production necessity.
- [x] No large golden-test corpus expansion beyond tests required for Phase 3 correctness behavior.
- [x] No unrelated refactors bundled into Phase 3 transaction PRs.

---

## 9) Definition of done for Phase 3

### 9.1 Functional completion criteria (planned)

- [ ] `BEGIN`, `COMMIT`, and `ROLLBACK` are implemented and wired through parser/planner/executor paths.
- [ ] Explicit transaction state transitions conform to defined state machine contracts.
- [ ] Invalid transaction command usage returns deterministic, state-derived errors.

### 9.2 Journaling correctness criteria (planned)

- [ ] Rollback journal capture uses first-preimage-only behavior per page per transaction.
- [ ] Journal-before-database overwrite ordering is enforced for all transaction writes.
- [ ] Commit finalization follows required ordering and cannot report success ambiguously.
- [ ] Rollback finalization restores pre-transaction state and cleans journal artifacts.

### 9.3 Crash recovery completion criteria (planned)

- [ ] Reopen logic correctly distinguishes `COMMITTED`, `INCOMPLETE`, and invalid journal states.
- [ ] Recovery replay restores exact pre-transaction state for incomplete transactions.
- [ ] Recovery behavior is idempotent across interrupted reopen/recovery attempts.
- [ ] Ambiguous/corrupt journal conditions fail closed with integrity-preserving behavior.

### 9.4 Test completion criteria (planned)

- [ ] Unit tests cover state transitions and invalid command matrix.
- [ ] Integration tests cover commit persistence and rollback discard semantics.
- [ ] Crash-simulation tests cover defined crash windows and expected reopen outcomes.
- [ ] Reopen/recovery tests cover clean, incomplete, committed-stale, and malformed journal scenarios.
- [ ] Negative/error-contract tests verify deterministic mapping and no partial state mutation.

### 9.5 Build and compatibility gates (required)

- [x] Implementation must remain JDK 15 compatible.
- [ ] Full repository test suite passes with:
  - [ ] `mvn test`
- [ ] Existing pre-Phase-3 compatibility tests remain green (no regressions).

### 9.6 Scope and quality gates (required)

- [ ] No Phase 3 non-goals are silently implemented.
- [ ] No speculative abstractions are introduced without direct production need.
- [ ] User-visible behavior limits and known constraints are documented.

### 9.7 Final acceptance rule (required)

- [ ] Phase 3 is complete only when all sections above are satisfied together; partial completion of implementation without recovery/test gates does not qualify as done.
