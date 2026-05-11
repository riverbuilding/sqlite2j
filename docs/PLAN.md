# sqlite2j Project Plan

Below is a concrete plan you can use as a project blueprint.

## 1) Proposed Architecture

Use a layered architecture that mirrors classic SQLite 2.0 flow:

1. **Frontend**
   - SQL tokenizer
   - SQL parser (SQLite 2-like grammar)
   - AST / parse structures

2. **Compiler**
   - Semantic analysis / name resolution
   - Query/code generation to bytecode ops (VDBE-like instruction stream)

3. **Execution Engine**
   - Virtual machine (bytecode interpreter)
   - Runtime registers, cursors, temporary values
   - Operator implementations (scan, compare, sort, insert/update/delete, etc.)

4. **Storage Stack**
   - B-tree tables and index pages
   - Pager (page cache + disk I/O abstraction)
   - Journal manager (rollback journal)
   - File locking manager (SQLite 2-like lock semantics as close as Java allows)

5. **Public Surfaces**
   - Java API (C API-inspired)
   - CLI shell (`sqlite2j` executable)

6. **Compatibility/Test Harness**
   - Golden tests
   - Differential tests vs native SQLite 2.0 where feasible
   - Crash/journal recovery tests

## 2) Major Components / Services

### A. `sql` module
- **Tokenizer/Lexer**
- **Parser** (hand-written recursive descent or directly translated logic)
- **AST / parse context**
- **Error reporting** with line/column and SQLite-like messages where possible

### B. `compiler` module
- **Name resolver** (tables, columns, aliases)
- **Schema lookup**
- **Code generator** producing VDBE-like opcodes
- **Expression compiler** and predicate planner (simple, correctness-first)

### C. `vm` module (VDBE analog)
- **Opcode enum + instruction representation**
- **Execution context** (registers, stack values, cursors)
- **Cursor abstraction** into B-tree
- **Comparison/collation/null semantics**

### D. `storage-btree` module
- **Table B-tree**
- **Index B-tree**
- **Cell/page encoding/decoding**
- **Split/merge/rebalance**
- Deterministic serialization behavior

### E. `storage-pager` module
- Page cache
- Dirty page tracking
- Flush policy consistent with rollback model
- Page size + database header handling

### F. `journal` module
- Rollback journal file format/flow
- Begin/commit/rollback logic
- Recovery on startup/open
- Idempotent rollback logic

### G. `locking` module
- Lock state transitions (shared/reserved/exclusive equivalents)
- Java NIO file locks + compatibility shims
- Deterministic single-process model first, then multiprocess validation

### H. `catalog` module
- Schema tables (SQLite 2 equivalents)
- Table/index metadata
- DDL application (`CREATE TABLE`, `DROP TABLE`, index creation)

### I. `api` module
- C-like session API in Java style
- Connection, statement prepare/step/finalize model
- Result row/value accessors
- Transaction control methods

### J. `cli` module
- REPL shell
- `.help`, `.schema`, `.tables` (minimal)
- Batch SQL execution

### K. `compat-tests` module
- SQL behavior tests
- Differential oracle tests (if native SQLite 2.0 build is available)
- Recovery and crash simulation suite

## 4) API Design

Keep it small and explicit first:

### Core interfaces (example shape)
- `SQLite2J.open(path)`
- `Connection.prepare(sql) -> Statement`
- `Statement.step() -> StepResult` (ROW / DONE / ERROR)
- `Statement.columnCount()`
- `Statement.columnName(i)`
- `Statement.columnType(i)`
- `Statement.columnText(i)`, `columnInt(i)`, etc.
- `Statement.reset()`
- `Statement.close()`
- `Connection.exec(sql)` for convenience
- `Connection.begin()`, `commit()`, `rollback()`
- `Connection.close()`
- `SQLiteException` with error code + message

### Design principles
- Map close to SQLite mental model (`prepare/step/finalize`)
- Keep auto-commit semantics explicit
- Avoid JDBC complexity initially
- Optional future adapter for JDBC compatibility

## 5) Directory / Repo Structure

```text
sqlite2j/
  pom.xml
  README.md
  LICENSE
  docs/
    architecture.md
    sqlite2-compatibility-matrix.md
    file-format-notes.md
    transaction-model.md
  modules/
    sqlite2j-core/
      src/main/java/...
      src/test/java/...
    sqlite2j-sql/
      src/main/java/...
      src/test/java/...
    sqlite2j-compiler/
      src/main/java/...
      src/test/java/...
    sqlite2j-vm/
      src/main/java/...
      src/test/java/...
    sqlite2j-btree/
      src/main/java/...
      src/test/java/...
    sqlite2j-pager/
      src/main/java/...
      src/test/java/...
    sqlite2j-journal/
      src/main/java/...
      src/test/java/...
    sqlite2j-locking/
      src/main/java/...
      src/test/java/...
    sqlite2j-cli/
      src/main/java/...
      src/test/java/...
    sqlite2j-compat-tests/
      src/test/java/...
  scripts/
    run-compat-tests.sh
    crash-test-harness.sh
  testdata/
    sql/
    dbfiles/
    expected/
```

## 6) Implementation Phases

### Phase 0 — Foundations
- Maven multi-module setup (JDK 15)
- Error code system
- Page/file abstractions
- Deterministic test framework baseline

### Phase 1 — Minimal end-to-end vertical slice
- `CREATE TABLE`, `INSERT`, `SELECT *` (single table, no indexes)
- Parser -> codegen -> VM -> B-tree -> pager
- Basic CLI

### Phase 2 — Core DML/Query features
- `WHERE`, `ORDER BY`, `UPDATE`, `DELETE`
- Expression evaluation and comparisons
- Basic planner heuristics (table scan first)

### Phase 3 — Transactions + rollback journal
- `BEGIN`, `COMMIT`, `ROLLBACK`
- Crash recovery on reopen
- Journaling correctness before optimization

### Phase 4 — Indexes
- `CREATE INDEX`, index lookup usage
- Update index maintenance on DML
- Deterministic index behavior tests

### Phase 5 — Storage engine convergence (B-tree + pager on-disk)
- Replace temporary VM catalog/row persistence format with pager-backed on-disk pages
- Route table and index reads/writes through `sqlite2j-btree` + `sqlite2j-pager`
- Persist table/index data in deterministic page/cell encoding
- Keep transaction/journal semantics compatible with the new page-backed path
- Add reopen/recovery tests that assert behavior parity vs current VM-layer persistence

### Phase 6 — Compatibility push
- SQL semantics alignment
- Error message/code alignment
- File format compatibility experiments
- Differential tests against SQLite 2.0

### Phase 7 — Hardening + CLI polish
- Recovery torture tests
- Locking contention tests
- Shell usability and docs

## 7) Testing Strategy

### A. Unit tests by layer
- Lexer/parser grammar tests
- Codegen opcode snapshots
- VM opcode semantics tests
- B-tree invariants and split/merge tests
- Pager dirty/flush behavior tests
- Journal replay correctness tests

### B. Integration tests
- End-to-end SQL tests per feature
- Transaction lifecycle tests
- Cross-module deterministic behavior tests

### C. Compatibility/differential tests
- Run same SQL scripts on:
  1) sqlite2j
  2) native SQLite 2.0 (if buildable in CI/dev)
- Compare:
  - result sets
  - schema effects
  - errors

### D. Crash/recovery tests (critical)
- Inject crash points:
  - before journal sync
  - after journal sync
  - during page flush
  - before commit marker
- Reopen DB and validate invariants/data

### E. File-format tests
- Golden DB files with known page layout
- Byte-level checks where deterministic
- Forward/backward compatibility checks (if feasible)

### F. Property-style tests (optional, high value)
- Random operation sequences under transaction boundaries
- Model-based validation for small datasets

## 9) Major Risks / Tradeoffs

1. **SQLite 2.0 exact semantics are old and under-documented**
   - Mitigation: differential oracle tests + archival source study.

2. **File locking parity in Java across OSes**
   - Mitigation: define compatibility profile; validate on Linux/macOS/Windows; document deviations.

3. **Crash safety correctness is subtle**
   - Mitigation: early journal tests + systematic fault injection.

4. **Parser/codegen fidelity**
   - Mitigation: start with translated behavior patterns; test SQL edge cases heavily.

5. **Scope creep toward SQLite 3.x features**
   - Mitigation: strict compatibility matrix and explicit “not in scope” list.

6. **Performance temptation**
   - Tradeoff decision: prioritize correctness and deterministic behavior first.

## 10) MVP Scope vs Future Scope

### MVP (must-have)
- Core SQL:
  - `CREATE TABLE`, `DROP TABLE`, `INSERT`, `UPDATE`, `DELETE`, `SELECT`, `WHERE`, `ORDER BY`
- Single DB file operation
- B-tree tables + indexes
- Pager + rollback journal
- `BEGIN/COMMIT/ROLLBACK`
- Crash recovery for tested failure points
- Java API with prepare/step/finalize style
- Basic CLI
- Foundational compatibility tests

### Post-MVP (future)
- Expanded SQL edge-case parity
- Improved lock contention behavior
- Better planner/index selection heuristics
- More complete shell tooling
- Optional JDBC bridge
- Broader file compatibility verification and tooling
