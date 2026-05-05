# Phase 2 Backlog — Core SQL Behavior

Phase 2 should extend the Phase 1 vertical slice without restructuring parser, compiler, VM, pager, or B-tree ownership.

## Candidate Work Items
- Add expression parsing and evaluation for simple predicates.
- Add `WHERE` support for table scans.
- Add `ORDER BY` support with deterministic sort behavior.
- Add `UPDATE` and `DELETE` support.
- Expand value handling beyond the Phase 1 integer/text/null baseline.
- Replace temporary Phase 1 persistence format with storage-backed catalog/table integration.
- Add more negative tests for unsupported or malformed SQL.

## Non-Goals
- No indexes in Phase 2 unless needed to preserve API flow.
- No transaction or rollback journal behavior; that remains Phase 3 scope.
- No SQLite 3.x features outside compatibility tooling.
