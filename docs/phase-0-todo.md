# Phase 0 TODO — Foundations

## 0.1 Project Bootstrap & Governance
- [ ] Define Phase 0 acceptance criteria in `docs/phase-0-foundations.md`.
- [ ] Maintain compatibility policy and scope boundaries in `docs/sqlite2-compatibility-matrix.md`.
- [ ] Keep coding conventions documented for Java 15 and Maven multi-module development.
- [ ] Enforce determinism rules for all test categories.
- [ ] Keep the high-level project blueprint current in `docs/PLAN.md`.

## 0.2 Maven Multi-Module Setup (JDK 15)
- [ ] Create root aggregator Maven project (`packaging=pom`).
- [ ] Set Java toolchain/compile target to JDK 15.
- [ ] Add module skeletons:
  - [ ] `sqlite2j-core`
  - [ ] `sqlite2j-sql`
  - [ ] `sqlite2j-compiler`
  - [ ] `sqlite2j-vm`
  - [ ] `sqlite2j-btree`
  - [ ] `sqlite2j-pager`
  - [ ] `sqlite2j-journal`
  - [ ] `sqlite2j-locking`
  - [ ] `sqlite2j-cli`
  - [ ] `sqlite2j-compat-tests`
- [ ] Define dependency direction rules and prevent cyclic dependencies.
- [ ] Configure central plugin management (compiler/surefire/failsafe/jar).
- [ ] Add Maven profiles for `fast` and `compat` test flows.
- [ ] Add Maven Enforcer checks for JDK version and dependency sanity.
- [ ] Enable reproducible build settings where feasible.

## 0.3 Base Package Layout & Boundaries
- [ ] Use package naming convention `org.sqlite2j...`.
- [ ] Document module boundaries and allowed import directions.
- [ ] Define initial shared primitive ownership strategy.

## 0.4 Error Code System
- [ ] Document error model and stable code taxonomy in `docs/error-model.md`.
- [ ] Define canonical error object shape:
  - [ ] stable code
  - [ ] primary message
  - [ ] optional cause
  - [ ] optional diagnostic context
- [ ] Define cross-module error translation rules without losing root code.
- [ ] Define test assertion policy: assert error code first, message second.

## 0.5 Page/File Abstractions
- [ ] Document page/file contracts in `docs/storage-abstractions.md`.
- [ ] Define minimal interfaces for DB file, journal file, page cache entries, and pager hooks.
- [ ] Define serialization invariants (page numbering, endianness, header handling).
- [ ] Define failure model and IO error categorization.

## 0.6 Deterministic Test Framework Baseline
- [ ] Select baseline test framework and document standards.
- [ ] Add deterministic test utilities plan:
  - [ ] fixed temp directory handling
  - [ ] deterministic random seed policy
  - [ ] deterministic clock abstraction
  - [ ] binary snapshot helpers
- [ ] Normalize test runtime environment (UTC, `Locale.ROOT`, UTF-8).
- [ ] Define test layering conventions (unit/integration/compat).
- [ ] Define CI command matrix for deterministic runs.

## 0.7 Phase 0 Exit Gates
- [ ] Build is clean on JDK 15.
- [ ] Module graph has no cycles.
- [ ] Error model is documented and reviewed.
- [ ] Storage abstraction contracts are documented and reviewed.
- [ ] Deterministic testing policy is documented and ready for enforcement.
- [ ] Phase 1 can begin without structural rework.

## 0.8 Phase 0 Non-Goals
- [ ] No SQL execution implementation.
- [ ] No full parser implementation.
- [ ] No B-tree algorithm implementation.
- [ ] No rollback journal recovery implementation.
- [ ] No performance optimization work.

## 0.3 Completion Artifacts
- `docs/architecture/module-boundaries.md`
- `docs/architecture/package-layout.md`

## 0.4–0.6 Completion Artifacts
- `docs/error-model.md`
- `docs/storage-abstractions.md`
- `docs/testing/framework-baseline.md`
- `modules/sqlite2j-compat-tests/src/test/java/org/sqlite2j/compat/DeterminismBaselineTest.java`
