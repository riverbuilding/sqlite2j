This project must compile and run on **JDK 15**.

Do not use Java 16+ language/library features, including but not limited to:
- records
- pattern matching for `instanceof`
- sealed classes/interfaces
- `Stream.toList()`
- newer APIs unavailable in Java 15

Build this codebase with discipline: introduce code only when it is necessary for current production behavior.

In particular:
- Do not add infrastructure, packages, abstractions, or helpers "in advance" of real usage.
- Avoid adding storage-layer code until production code actually needs it (not just tests).
- Avoid adding large sets of golden test cases unless they are required by current behavior and provide clear value.
- Prefer the smallest clear implementation that satisfies current requirements.

Before finishing any code task, run:

```bash
mvn test
```
