# Base Package Layout

## Standard
All source files should be under `org.sqlite2j`.

## Module to Package Mapping
- `modules/sqlite2j-core` -> `org.sqlite2j.core`
- `modules/sqlite2j-sql` -> `org.sqlite2j.sql`
- `modules/sqlite2j-compiler` -> `org.sqlite2j.compiler`
- `modules/sqlite2j-vm` -> `org.sqlite2j.vm`
- `modules/sqlite2j-btree` -> `org.sqlite2j.btree`
- `modules/sqlite2j-pager` -> `org.sqlite2j.pager`
- `modules/sqlite2j-journal` -> `org.sqlite2j.journal`
- `modules/sqlite2j-locking` -> `org.sqlite2j.locking`
- `modules/sqlite2j-cli` -> `org.sqlite2j.cli`
- `modules/sqlite2j-compat-tests` -> `org.sqlite2j.compat`

## Conventions
- Public APIs should live in `.api` or module-root package namespaces.
- Internal implementation details should use `.internal` subpackages.
- Test utilities should remain in test source sets unless intentionally public.
