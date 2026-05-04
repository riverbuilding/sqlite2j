# sqlite2j

`sqlite2j` is a Java port of SQLite 2.0 focused on behavioral and functional parity, not a redesign of SQL database internals.

## Project Goals
- Port SQLite 2.0 core architecture from C to Java.
- Preserve SQLite 2.0 behavior, SQL semantics, transaction model, and storage expectations as closely as practical.
- Keep implementation pure Java (no JNI/native dependency in production code).

## Architecture Summary
sqlite2j follows a layered design:
1. SQL frontend (tokenizer + parser)
2. Compiler/code generator (VDBE-like bytecode)
3. Virtual machine interpreter
4. Storage stack (B-tree + pager + rollback journal + locking)
5. Public interfaces (Java API + CLI)
6. Compatibility/differential testing harness

See full details in `docs/PLAN.md`.

## Roadmap
The implementation roadmap is phased:
- **Phase 0:** Foundations (tooling, contracts, determinism)
- **Phase 1:** Minimal vertical slice (`CREATE TABLE`, `INSERT`, `SELECT *`)
- **Phase 2+:** Core SQL semantics, transactions, indexes, compatibility hardening

Detailed phase planning:
- `docs/phase-0-foundations.md`
- `docs/phase-1-todo.md`

## Standards and Governance
- Architecture boundaries: `docs/architecture/module-boundaries.md`
- Package layout: `docs/architecture/package-layout.md`
- Coding standards: `docs/coding-conventions.md`
- Compatibility tracking: `docs/sqlite2-compatibility-matrix.md`
- Deterministic test rules: `docs/testing-determinism.md`

## Status
Planning and documentation phase in progress. Implementation has not started.
