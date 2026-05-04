# Module Boundaries and Import Rules

## Purpose
Define package layout and allowed dependency directions for sqlite2j modules.

## Base Package Convention
All Java code must use the base package namespace:
- `org.sqlite2j.*`

Recommended subpackages:
- `org.sqlite2j.api`
- `org.sqlite2j.sql`
- `org.sqlite2j.compiler`
- `org.sqlite2j.vm`
- `org.sqlite2j.btree`
- `org.sqlite2j.pager`
- `org.sqlite2j.journal`
- `org.sqlite2j.locking`
- `org.sqlite2j.cli`
- `org.sqlite2j.compat`

## Dependency Direction (High-Level)
Allowed direction is from top-level consumers downward into lower layers.

- `sqlite2j-cli` -> `sqlite2j-core`, `sqlite2j-api` (future)
- `sqlite2j-core` -> `sqlite2j-sql`, `sqlite2j-compiler`, `sqlite2j-vm`
- `sqlite2j-vm` -> `sqlite2j-btree`
- `sqlite2j-btree` -> `sqlite2j-pager`
- `sqlite2j-pager` -> `sqlite2j-journal`, `sqlite2j-locking`
- `sqlite2j-compat-tests` may depend on all modules for test orchestration

## Rules
1. No cyclic dependencies between Maven modules.
2. Parser/compiler layers must not depend on pager/journal/locking directly.
3. Storage layers must not depend on SQL/parser/compiler layers.
4. CLI must not access storage internals directly; only public API/core surfaces.
5. Cross-layer communication must use explicit interfaces, not concrete internals.

## Shared Primitive Ownership
To avoid dependency cycles, shared primitives are owned as follows:
- SQL tokens/AST nodes: `sqlite2j-sql`
- Opcode/instruction definitions: `sqlite2j-vm`
- Page/cell identifiers and pager contracts: `sqlite2j-pager`
- Error code enum and core exception type: `sqlite2j-core` (to be revisited in 0.4)

If shared primitives grow significantly, create a dedicated module in a future phase.
