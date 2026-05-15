# Phase 4 — Indexes

> Planning document only. No implementation code in this phase doc.

## 1) Scope and constraints

### 1.1 Phase 4 scope (target)

- [ ] Implement `CREATE INDEX` for supported table/column targets.
- [ ] Use indexes for eligible lookup paths in query execution.
- [ ] Keep index structures synchronized on DML (`INSERT`, `UPDATE`, `DELETE`).
- [ ] Add deterministic tests that verify index behavior and results.

### 1.2 Explicit non-goals (unless production behavior forces expansion)

- [ ] No cost-based optimizer.
- [ ] No advanced index families (e.g., partial, expression, covering, clustered).
- [ ] No speculative background index maintenance.
- [ ] No broad storage redesign beyond what index support requires.

### 1.3 Constraints

- [ ] Preserve JDK 15 compatibility.
- [ ] Use only minimal production code required for Phase 4 behavior.
- [ ] Keep behavior deterministic and testable with current harness.

---

## 2) `CREATE INDEX` support

### 2.1 SQL surface

- [ ] Parse and validate basic form:
  - `CREATE INDEX index_name ON table_name (column_name)`
- [ ] Define deterministic errors for:
  - duplicate index name
  - missing table
  - missing/unknown column
  - unsupported syntax variants

### 2.2 Metadata and persistence

- [ ] Persist index metadata needed for reopen/recovery of schema state.
- [ ] Ensure index discovery during database open is deterministic.
- [ ] Keep metadata changes minimal and aligned with existing schema model.

### 2.3 Build path

- [ ] On index creation, scan existing table rows and populate index entries.
- [ ] Validate key encoding and ordering rules for supported value types.
- [ ] Fail atomically with clear errors when index build cannot complete safely.

---

## 3) Index lookup usage

### 3.1 Planner eligibility rules (minimal)

- [ ] Use index lookup only when predicate shape is explicitly supported (start with equality).
- [ ] Fall back to table scan for unsupported predicates or ambiguous cases.
- [ ] Keep planner rule-based and deterministic (no cost model in this phase).

### 3.2 Execution behavior

- [ ] Route eligible `SELECT` lookups through index->row resolution path.
- [ ] Preserve row visibility and result correctness identical to scan path.
- [ ] Keep deterministic ordering expectations explicit in tests/documentation.

### 3.3 Safety and fallback

- [ ] If index metadata is invalid/corrupt, fail safely or fall back deterministically based on defined policy.
- [ ] Never return different logical results due to index-vs-scan path choice.

---

## 4) Index maintenance on DML

### 4.1 INSERT

- [ ] Add index entry for each affected index on inserted row.
- [ ] Ensure key encoding consistency with build path.

### 4.2 UPDATE

- [ ] If indexed column value changes, remove old entry and insert new entry.
- [ ] If non-indexed columns change, avoid unnecessary index churn.

### 4.3 DELETE

- [ ] Remove corresponding index entries for deleted rows.
- [ ] Keep behavior correct for multi-row deletes.

### 4.4 Transactional/correctness expectations

- [ ] Ensure index and table changes remain consistent under existing transaction semantics.
- [ ] Prevent partially-applied DML from leaving index/table divergence.

---

## 5) Deterministic index behavior tests

### 5.1 Parser/planner tests

- [ ] Parse valid and invalid `CREATE INDEX` statements with deterministic outcomes.
- [ ] Verify planner path selection for eligible vs ineligible predicates.

### 5.2 Integration tests

- [ ] `CREATE INDEX` over populated table yields expected lookup results.
- [ ] Indexed lookup returns same logical rows as table-scan baseline.
- [ ] DML after index creation preserves lookup correctness.

### 5.3 DML maintenance tests

- [ ] INSERT updates index visibility immediately.
- [ ] UPDATE correctly moves index keys when indexed value changes.
- [ ] DELETE removes index entries so lookups no longer return removed rows.

### 5.4 Determinism tests

- [ ] Repeated identical workloads produce identical results.
- [ ] Reopen scenarios preserve index metadata and lookup behavior.
- [ ] Edge-case tests (null/type boundaries supported by engine) remain deterministic.

---

## 6) Exit criteria

- [ ] `CREATE INDEX` works for scoped syntax with deterministic errors.
- [ ] Eligible lookups use index path without changing logical query results.
- [ ] DML keeps index/table state consistent.
- [ ] Deterministic tests for create/lookup/maintenance pass.
- [ ] Full suite passes with:
  - [ ] `mvn test`
