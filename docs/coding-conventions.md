# Coding Conventions

## Language and Tooling
- Language: Java
- Target runtime/toolchain: JDK 15
- Build system: Maven

## General Standards
- Prefer clear, explicit code over clever optimization.
- Keep modules isolated with well-defined boundaries.
- Avoid unnecessary dependencies.
- Preserve behavior-oriented naming where mapping from SQLite 2.0 concepts is useful.

## Error Handling
- Use stable internal error codes for assertions and compatibility tests.
- Include actionable context in exception messages.
- Preserve root cause exceptions when crossing module boundaries.

## Testing Expectations
- Every new behavior must include deterministic tests.
- Unit tests should be colocated with module code.
- Integration and compatibility tests should be explicit and reproducible.
