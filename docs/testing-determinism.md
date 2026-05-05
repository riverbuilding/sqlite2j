# Testing Determinism Rules

All tests in sqlite2j must execute deterministically.

## Required Baseline Rules
- Fix timezone to UTC during tests.
- Use `Locale.ROOT` in tests and parsing/formatting logic.
- Use UTF-8 explicitly for text encoding/decoding.
- Use fixed random seeds for any randomized test data.
- Avoid dependence on filesystem iteration order.
- Ensure temporary file paths are test-scoped and isolated.

## Assertions
- Prefer exact-value assertions over fuzzy checks.
- For binary/page behavior, use byte-for-byte expected output where practical.
- Assert stable error code + message pattern for failures.

## CI Expectations
- Tests must pass repeatedly without flakiness.
- Any non-deterministic test is treated as a defect and must be corrected before merge.
