# Error Code System (Phase 0.4)

## Goals
- Provide stable, testable error codes across modules.
- Preserve root cause context while translating errors between layers.
- Keep behavior compatible in spirit with SQLite-style result/error handling.

## Canonical Error Structure
Every surfaced sqlite2j error should include:
1. `code` (stable machine-readable code)
2. `message` (human-readable deterministic text)
3. `cause` (optional underlying exception)
4. `context` (optional key/value diagnostics, e.g. page number, SQL offset)

## Error Code Categories
Proposed top-level categories:
- `SQLITE2J_OK`
- `SQLITE2J_ERROR` (generic/internal)
- `SQLITE2J_MISUSE`
- `SQLITE2J_NOMEM` (reserved)
- `SQLITE2J_IOERR`
- `SQLITE2J_CORRUPT`
- `SQLITE2J_BUSY`
- `SQLITE2J_LOCKED`
- `SQLITE2J_READONLY`
- `SQLITE2J_INTERRUPT` (reserved)
- `SQLITE2J_CONSTRAINT`
- `SQLITE2J_SCHEMA`
- `SQLITE2J_TOOBIG` (reserved)
- `SQLITE2J_NOTFOUND`

Detailed subcodes are added per module as implementation proceeds.

## Ownership by Layer
- `sqlite2j-sql`: lexical/syntax parse failures.
- `sqlite2j-compiler`: semantic resolution failures.
- `sqlite2j-vm`: execution/runtime type/operation failures.
- `sqlite2j-pager`/`btree`/`journal`/`locking`: IO, corruption, busy/lock failures.
- `sqlite2j-core`: API boundary translation and normalization.

## Translation Rules
1. Never discard original error code when wrapping.
2. Preserve cause chain for diagnostics.
3. Add context keys rather than rewriting root message meaning.
4. At API boundary, expose stable top-level code + deterministic message.

## Testing Policy
- Assert `code` first.
- Assert message pattern second.
- Assert contextual fields where relevant.
- Avoid brittle full-string comparisons unless intentionally fixed.
