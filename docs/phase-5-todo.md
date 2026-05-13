# Phase 5 TODO — Storage Engine Convergence (B-tree + Pager On-Disk)

> Planning document only. No implementation code in this phase doc.

## 1) Scope and goals

- [ ] Replace temporary VM catalog/row persistence with pager-backed on-disk page storage.
- [ ] Route table and index reads/writes through `sqlite2j-btree` and `sqlite2j-pager`.
- [ ] Keep SQL behavior compatible with existing Phase 4 semantics while changing storage internals.
- [ ] Preserve deterministic behavior across reopen/recovery.

## 2) Explicit constraints

- [ ] Maintain JDK 15 compatibility.
- [ ] Keep implementation minimal and production-driven (no speculative abstractions).
- [ ] Preserve deterministic errors/results for existing supported SQL.
- [ ] Do not broaden SQL surface in this phase unless required for storage convergence.

## 3) Data format convergence

### 3.1 Page/cell encoding
- [x] **Deterministic table leaf cell encoding (Phase 5 baseline)**
  - Cell layout (in order):
    1. `rowId` (`uint64`, big-endian)
    2. `columnCount` (`uint16`, big-endian)
    3. Repeated column payloads in schema order:
       - `typeTag` (`uint8`): `0x00=NULL`, `0x01=INT`, `0x02=TEXT`
       - `payloadLength` (`uint32`, big-endian)
       - `payloadBytes`:
         - `NULL`: length `0`, no payload bytes
         - `INT`: length `8`, signed 64-bit big-endian two's complement
         - `TEXT`: UTF-8 bytes (no BOM), exact byte length
  - No varint or compression in Phase 5 baseline.

- [x] **Deterministic single-column index cell encoding (Phase 5 baseline)**
  - Cell layout (in order):
    1. `indexKeyTypeTag` (`uint8`) with same tag mapping as table values
    2. `indexKeyLength` (`uint32`, big-endian)
    3. `indexKeyPayload` (encoded exactly like table value payload)
    4. `rowId` (`uint64`, big-endian) as table-row reference
  - Duplicate index keys are ordered by ascending `rowId`.

- [x] **Endianness/header/version markers**
  - Endianness is fixed to big-endian for all fixed-width numeric fields.
  - File header uses fixed magic + version:
    - Magic bytes: `S2JDB\0`
    - Format version: `0x0001` (uint16 big-endian)
  - Header fields are positional and deterministic; unknown major version => explicit format error.

### 3.2 File layout
- [x] **Minimal page-backed file header (Phase 5 baseline)**
  - File header bytes:
    1. Magic (`6` bytes): `S2JDB\0`
    2. Version (`2` bytes, BE): `0x0001`
    3. Page size (`2` bytes, BE): fixed `4096` in Phase 5
    4. Root table page number (`4` bytes, BE): default `1`
    5. Root index catalog page number (`4` bytes, BE): default `2`
    6. Page count (`4` bytes, BE)
    7. Reserved (`10` bytes, zeroed in Phase 5)

- [x] **Page numbering/allocation policy**
  - Page numbers are 1-based.
  - Page `1`: table root; page `2`: index catalog root.
  - New pages allocate monotonically (`maxPageNo + 1`), no free-list reuse in Phase 5 baseline.
  - Within-page cell ordering is deterministic:
    - Table pages: ascending `rowId`
    - Index pages: ascending `(key, rowId)` using current value comparison rules.

- [x] **Deterministic serialization rule**
  - Given identical logical schema/data and insert/update/delete history normalized by current planner behavior, serialized bytes must be identical.
  - All reserved bytes are zero-filled.
  - No timestamp/random data may be written into database pages.

### 3.3 Compatibility migration policy
- [x] **Migration behavior from `sqlite2j-vm-v1` text format**
  - If file starts with text header `sqlite2j-vm-v1`, open in **read-convert-write** migration mode:
    1. Parse legacy text format strictly.
    2. Materialize logical schema + rows + indexes in memory.
    3. Write full Phase 5 page-format file atomically via temp file + rename.
    4. Reopen using page format and continue normally.
  - Migration is single-shot; once converted, legacy header is no longer emitted.

- [x] **Deterministic explicit errors for unsupported/ambiguous states**
  - Invalid/unknown magic bytes => `STORAGE_FORMAT_UNSUPPORTED`.
  - Known magic with unsupported version => `STORAGE_FORMAT_VERSION_UNSUPPORTED`.
  - Legacy text parse ambiguity/corruption => `STORAGE_MIGRATION_FAILED`.
  - Partial/failed atomic rename => `STORAGE_MIGRATION_IOERR`.

## 4) Runtime wiring (VM -> B-tree -> Pager)

### 4.1 Read path
- [ ] **Step 1: Introduce storage adapters in VM database layer**
  - Add a table-storage adapter that wraps `sqlite2j-pager` + `sqlite2j-btree` scan APIs.
  - Add an index-storage adapter that wraps index key lookup APIs (single-column baseline).
  - Keep existing VM in-memory model available behind a feature flag for parity validation during migration.

- [ ] **Step 2: Route full table scans through B-tree cursor**
  - Replace `rowsView(table)` read-source in scan paths with B-tree cursor iteration.
  - Decode B-tree leaf cells into `VmRow` values using deterministic type mapping (`NULL`/`INT`/`TEXT`).
  - Keep output ordering equivalent to existing deterministic scan behavior.

- [ ] **Step 3: Route eligible indexed predicates through index B-tree**
  - Reuse existing equality eligibility rule (`WHERE column = literal`) for first indexed read path.
  - Resolve candidate row identifiers from index B-tree and fetch rows via table B-tree.
  - Keep deterministic fallback to full scan for unsupported predicates or invalid index metadata.

- [ ] **Step 4: Parity validation**
  - Add dual-path assertions in tests (scan vs index path) to prove equal logical results.
  - Verify deterministic reopen behavior with both indexed and non-indexed reads.

### 4.2 Write path
- [ ] **Step 5: Route INSERT through B-tree table write**
  - Encode inserted row into table-leaf cell format and insert via table B-tree API.
  - For indexed columns, encode index key and insert index entry in index B-tree.
  - Keep deterministic key ordering for duplicate index keys (`key`, then row id).

- [ ] **Step 6: Route UPDATE through B-tree mutation flow**
  - Locate candidate rows via scan/index path, apply value mutation, and write updated row payload.
  - If indexed column changes, remove old index entry and insert new one.
  - If indexed column does not change, avoid index churn.

- [ ] **Step 7: Route DELETE through B-tree removal flow**
  - Locate matching rows and remove row cells from table B-tree.
  - Remove corresponding index entries for all affected indexes.
  - Validate multi-row delete behavior remains deterministic.

- [ ] **Step 8: Atomicity under current transaction model**
  - Execute table + index mutations inside existing transaction/journal boundaries.
  - Ensure journal-before-overwrite guarantees cover all touched pages.
  - Add tests that fail writes mid-sequence and confirm no persistent table/index divergence after recovery.

### 4.3 Metadata path
- [ ] **Step 9: Page-backed metadata catalog**
  - Move table/index schema metadata from VM text-like logical map to page-backed catalog pages.
  - Define deterministic metadata record layout (table name, column defs, index name, index column).
  - Keep metadata versioning tied to page-format header version.

- [ ] **Step 10: Deterministic open/load validation**
  - On open, validate header magic/version/page-size before catalog traversal.
  - Validate metadata record integrity (bounds, lengths, type tags, duplicate-name rules).
  - Return explicit deterministic errors for unsupported/corrupt metadata states.

- [ ] **Step 11: Migration and compatibility hardening**
  - Complete one-shot migration from `sqlite2j-vm-v1` legacy text format to page-backed catalog/table/index pages.
  - On ambiguous migration state, fail closed with explicit migration error code/category.
  - Add reopen tests proving migrated databases behave identically to fresh page-format databases.

## 5) Transaction/journal integration

- [ ] Preserve current transaction command semantics (`BEGIN`, `COMMIT`, `ROLLBACK`).
- [ ] Ensure journal-before-overwrite ordering still holds when pager pages are dirty.
- [ ] Ensure reopen recovery restores both table and index consistency.

## 6) Safety and fallback policy

- [ ] On on-disk format corruption/ambiguity, fail closed with deterministic integrity error.
- [ ] Do not silently fall back to incompatible persistence modes.
- [ ] Keep fallback behavior deterministic when safe downgrade paths are explicitly supported.

## 7) Test plan

### 7.1 Parser/planner invariants
- [ ] Existing parser/codegen contracts remain stable (no regressions).
- [ ] Planner eligibility behavior for indexed equality remains deterministic.

### 7.2 Storage integration tests
- [ ] CREATE/INSERT/SELECT round-trip via B-tree/pager path.
- [ ] CREATE INDEX over populated tables yields expected lookup results.
- [ ] DML after index creation keeps table/index lookup parity.

### 7.3 Reopen/recovery tests
- [ ] Reopen preserves table data and index lookup behavior.
- [ ] Recovery from interrupted writes preserves pre-commit consistency.
- [ ] Recovery is idempotent across repeated reopen attempts.

### 7.4 Determinism tests
- [ ] Repeated identical workloads produce byte-stable or behavior-stable outcomes (as specified).
- [ ] Error messages/codes for corrupt/unsupported file states are deterministic.

## 8) Exit criteria

- [ ] Production read/write paths use B-tree + pager rather than temporary VM text persistence.
- [ ] Supported SQL behavior remains semantically equivalent to Phase 4 baseline.
- [ ] Table/index consistency holds through DML and transaction boundaries.
- [ ] Reopen/recovery tests pass for page-backed storage path.
- [ ] Full suite passes with:
  - [ ] `mvn test`
