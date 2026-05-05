# Deterministic Test Framework Baseline (Phase 0.6)

## Baseline Stack
- Unit/integration framework: JUnit 5 (Jupiter)
- Build execution: Maven Surefire/Failsafe
- Test source layout: module-local `src/test/java`

## Determinism Runtime Controls
Configured globally for tests:
- timezone fixed to UTC
- language/region fixed to `en-US`
- UTF-8 source encoding

## Test Utility Conventions
Planned shared utilities:
- deterministic random seed helper
- temp-directory helper with predictable naming
- binary snapshot/assert helpers for page and journal bytes
- fixture loader for SQL scripts and golden files

## Test Layering
- **Unit tests:** parser/compiler/vm/pager/btree internals
- **Integration tests:** cross-module execution paths
- **Compatibility tests:** differential checks vs SQLite 2.0 oracle (optional profile)

## CI Command Matrix
- Fast checks: `mvn -Pfast test`
- Full local checks: `mvn test`
- Compatibility profile: `mvn -Pcompat test`
