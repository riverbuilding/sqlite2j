# Page/File Abstractions (Phase 0.5)

## Objectives
- Define deterministic contracts for database and journal file access.
- Separate logical page behavior from low-level IO details.
- Make pager/btree/journal independently testable.

## Core Concepts

### Page
A logical page unit includes:
- `pageNo` (explicitly documented numbering scheme)
- immutable `pageSize`
- mutable byte content buffer
- dirty flag
- optional generation/version for cache coherence

### Database File Interface
Responsibilities:
- open/close lifecycle
- random-access read/write by file offset
- size query and resize
- sync/fsync contract hooks
- lock integration hooks

### Journal File Interface
Responsibilities:
- open/create lifecycle
- append record operations
- sync/truncate/delete behavior
- replay iteration for recovery

### Pager Contract
Responsibilities:
- map page number -> page buffer
- cache and dirty tracking
- transaction boundary hooks (`begin`, `commit`, `rollback`)
- coordinate with journal before durable writes

## Deterministic Invariants
- Page byte serialization is deterministic for equal inputs.
- Endianness is fixed and documented.
- File header encoding is deterministic and versioned.
- Page numbering policy is consistent and tested.

## Failure Model
- IO failures map to stable `IOERR`-class errors.
- Short reads/writes are explicit error states.
- Recovery operations are idempotent where possible.

## Initial Interface Ownership
- Pager and page primitives: `sqlite2j-pager`
- Journal contracts: `sqlite2j-journal`
- Locking contracts: `sqlite2j-locking`
