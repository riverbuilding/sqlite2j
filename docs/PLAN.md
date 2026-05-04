# sqlite2j Project Plan

## Goal
Translate SQLite 2.0 from C to Java with behavioral and functional parity as the primary objective.

## Guiding Principles
- Correctness and compatibility over performance.
- Deterministic storage and test behavior.
- Readable, maintainable Java with minimal dependencies.
- Modular architecture enabling isolated testing by layer.

## Phase Overview
1. **Phase 0 — Foundations**
   - Maven multi-module setup (JDK 15)
   - Error code system design
   - Page/file abstractions design
   - Deterministic test framework baseline
2. **Phase 1 — Minimal vertical slice**
   - `CREATE TABLE`, `INSERT`, `SELECT *`
   - End-to-end parser → compiler → VM → storage path
3. **Phase 2 — Core SQL behavior**
   - `WHERE`, `ORDER BY`, `UPDATE`, `DELETE`
4. **Phase 3 — Transactions and rollback journal**
   - `BEGIN`, `COMMIT`, `ROLLBACK`
   - Recovery behavior
5. **Phase 4 — Indexes and planning baseline**
6. **Phase 5 — Compatibility hardening**
7. **Phase 6 — Reliability and CLI polish**

## Phase 0 Detailed Plan (Current)
See `docs/phase-0-foundations.md` for acceptance criteria and required artifacts.
