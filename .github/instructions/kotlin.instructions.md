---
applyTo: "**/*.kt"
---

# Kotlin instructions

- Write idiomatic, type-safe Kotlin. Follow the repository's formatting, package structure, naming, and visibility conventions.
- Keep classes and functions cohesive and small. Prefer clear types and explicit domain models over loosely typed data or unnecessary abstractions.
- Follow MVVM and the Repository Pattern: composables render state and emit actions; ViewModels expose UI state and coordinate presentation-level work; repositories own data-source coordination.
- Use coroutines with structured concurrency and appropriate dispatchers for blocking or I/O work. Do not block the main thread, launch unmanaged scopes, or perform database, network, or HTML parsing work inside composables.
- Expose observable state using the project's established Flow/StateFlow conventions, and collect it in a lifecycle-aware way from Compose.
- Model loading, empty, success, and error conditions explicitly when relevant. Avoid swallowed exceptions, misleading fallback success, and non-null assertions that hide invalid state.
- Keep network access in a data layer using OkHttp where requested; isolate Jsoup parsing from UI and presentation code. Do not make core tracking dependent on network availability.
- Add or update focused tests for changed behavior. Inspect related code and tests before substantial changes, preserve existing behavior, and do not remove functionality without the user's authorization.
