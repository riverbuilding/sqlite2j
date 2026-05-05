# Phase 2 TODO — Core DML/Query Features

This document is a planning-only TODO list for Phase 2.
No implementation is included here.

## 1. Scope and Acceptance Criteria (Todo 1 — In Progress)

### Scope
- Extend the existing Phase 1 vertical slice only.
- Keep ownership boundaries intact across parser, compiler, VM, pager, and B-tree.
- Focus on:
  - `WHERE`
  - `ORDER BY`
  - `UPDATE`
  - `DELETE`
  - expression evaluation and comparisons
  - basic planner heuristic: table scan first

### Non-goals
- No indexes.
- No joins.
- No aggregation.
- No transaction or rollback journal behavior.
- No broad storage redesign unless directly required by production behavior.

### Constraints
- Maintain JDK 15 compatibility.
- Avoid Java 16+ language or library features.
- Prefer minimal, directly-used production behavior over anticipatory abstractions.

### Acceptance criteria (Phase 2)
- `SELECT * FROM table WHERE column OP literal` works for supported operators.
- `SELECT * FROM table ORDER BY column [ASC|DESC]` works deterministically.
- `WHERE` + `ORDER BY` execute in deterministic order (filter then sort).
- `UPDATE table SET column = literal [WHERE ...]` mutates only matching rows.
- `DELETE FROM table [WHERE ...]` removes only matching rows.
- Unsupported SQL fails with deterministic errors.
- No index or transaction behavior is introduced.

---

## 2. Parser and AST Work

### 2.1 Expressions
- [ ] Add minimal expression AST.
- [ ] Start with only required forms:
  - column reference
  - literal value
  - binary comparison
- [ ] Support operators:
  - `=`
  - `!=` or `<>`
  - `<`
  - `<=`
  - `>`
  - `>=`
- [ ] Decide whether to include conjunction support in Phase 2 (`AND` optional, `OR` deferred unless required).
- [ ] Reuse existing integer/text literal handling first.
- [ ] Define explicit null comparison behavior.
- [ ] Emit clear parser errors for unsupported expressions.

Suggested AST candidates:
- [ ] `Expression`
- [ ] `ColumnExpression`
- [ ] `LiteralExpression`
- [ ] `ComparisonExpression`
- [ ] `ComparisonOperator`

### 2.2 `SELECT * FROM ... WHERE ...`
- [ ] Extend select statement AST with optional `WHERE` expression and optional `ORDER BY`.
- [ ] Parse:
  - `SELECT * FROM table WHERE column = literal`
  - comparisons using supported operators.
- [ ] Keep projection limited to `*` unless new requirements appear.

### 2.3 `ORDER BY`
- [ ] Add AST for order clause:
  - column name
  - direction (default ascending)
- [ ] Parse:
  - `ORDER BY column`
  - `ORDER BY column ASC`
  - `ORDER BY column DESC`
- [ ] Start with single ordering column only.
- [ ] Define deterministic tie behavior (preserve scan/insertion order).
- [ ] Define ordering behavior for integer/text/null.

### 2.4 `UPDATE`
- [ ] Add `UpdateStatement` AST:
  - table name
  - assignment list
  - optional `WHERE`
- [ ] Parse:
  - `UPDATE table SET column = literal`
  - `UPDATE table SET column = literal WHERE ...`
- [ ] Start with literal-only assignment values.
- [ ] Support one assignment first; add multiple if required.

### 2.5 `DELETE`
- [ ] Add `DeleteStatement` AST:
  - table name
  - optional `WHERE`
- [ ] Parse:
  - `DELETE FROM table`
  - `DELETE FROM table WHERE ...`
- [ ] Reject unsupported delete forms with explicit errors.

## 3. Tokenizer Work
- [ ] Add keywords/tokens:
  - `WHERE`, `ORDER`, `BY`, `ASC`, `DESC`, `UPDATE`, `DELETE`, `SET`
  - comparison operators
- [ ] Add tokenizer tests for new keywords/operators and mixed keyword casing.

## 4. Compiler / Codegen Work

### 4.1 General compiler shape
- [ ] Evolve Phase 1 codegen minimally for Phase 2 statements.
- [ ] Lower filtered selects, ordered selects, updates, and deletes.
- [ ] Keep table-scan-first execution model.

### 4.2 Expression lowering
- [ ] Choose simple expression representation in instructions.
- [ ] Validate/resolve referenced columns against schema.
- [ ] Add compiler tests for expected opcode sequence.

### 4.3 Opcode additions
- [ ] Add only opcodes required by Phase 2 execution model.
- [ ] Avoid index/join/cost-model opcodes.

## 5. VM Execution Work

### 5.1 Table scan first
- [ ] Maintain full-scan-first strategy for reads and mutations.
- [ ] Apply optional filter during scan.
- [ ] Apply optional in-memory sort after filtering.

### 5.2 Expression evaluation
- [ ] Evaluate expressions using current row + schema.
- [ ] Implement integer/text comparison behavior.
- [ ] Define and test null behavior.

### 5.3 `WHERE`
- [ ] Skip non-matching rows.
- [ ] Return deterministic errors for unknown columns/type mismatches.

### 5.4 `ORDER BY`
- [ ] Materialize matching rows and sort deterministically.
- [ ] Validate order column existence.

### 5.5 `UPDATE`
- [ ] Scan table and update matching rows only.
- [ ] Preserve non-target columns.
- [ ] Add filtered/unfiltered update tests.

### 5.6 `DELETE`
- [ ] Scan table and remove matching rows only.
- [ ] Avoid cursor mutation hazards.
- [ ] Add filtered/unfiltered delete tests.

## 6. Planner Heuristics
- [ ] Keep planner heuristic intentionally simple:
  - scan table
  - filter rows
  - sort rows
  - emit or mutate rows
- [ ] No index planning.
- [ ] No cost model.

## 7. Test Plan

### 7.1 Parser tests
- [ ] Add valid and invalid SQL coverage for WHERE/ORDER BY/UPDATE/DELETE.

### 7.2 Compiler tests
- [ ] Assert opcode order and table targeting.

### 7.3 VM tests
- [ ] Add behavior tests for filter/sort/update/delete including edge cases.

### 7.4 End-to-end tests
- [ ] Add focused end-to-end SQL behavior tests.

## 8. Milestone Order
1. Expression foundation.
2. `WHERE`.
3. `ORDER BY`.
4. `DELETE`.
5. `UPDATE`.
6. Cleanup + exit criteria.

## 9. Exit Criteria
- [ ] WHERE works for supported comparison operators.
- [ ] ORDER BY works deterministically for supported value types.
- [ ] UPDATE and DELETE mutate only matching rows.
- [ ] Unsupported SQL fails deterministically.
- [ ] No non-goal features added.
- [ ] `mvn test` passes before completion of implementation work.
