# Manga Tracker — project instructions

## Project and scope

- This repository is for **Manga Tracker**, a native Android application.
- Kotlin is the primary language. Use Gradle Kotlin DSL (`.gradle.kts`) for build configuration.
- Build the UI with Jetpack Compose and Material 3.
- Use Room for the local database, WorkManager for background work, OkHttp for HTTP requests, and Jsoup for HTML parsing when those capabilities are needed.
- Follow MVVM and the Repository Pattern. Keep the application modular, organized, testable, and appropriately simple for its current scope.
- The app must remain useful locally and must not depend on a server, Firebase, a paid API, or a cloud service. Network access is only appropriate for explicitly requested features; do not make core tracking depend on it.
- Treat [`PRODUCT_SPEC.md`](./PRODUCT_SPEC.md) as the functional source of truth. Before implementing or changing a specified feature, read its relevant requirements and preserve their acceptance invariants; distinguish specified behavior from implemented behavior.
- Do not remove or materially change existing functionality without explaining why and obtaining the user's authorization.
- Do not begin unrelated features or broaden a requested change without a clear need.

## Working in this repository

- Before a substantial change, inspect the related source, tests, and build configuration. Understand existing behavior and follow established project conventions.
- Make focused changes and preserve existing behavior unless the request explicitly authorizes a behavior change.
- Keep dependencies and Android permissions to the minimum needed. Explain additions that affect the build, privacy, or app behavior.
- Do not invent test results or claim that code compiled unless the relevant command was actually run. Report commands and outcomes accurately, including failures or unavailable tooling.
- Run the relevant tests after code changes. Run a build or other validation when appropriate and available; if it cannot be run, state why.
- As application development advances, keep `PROJECT_STATUS.md` and `CHANGELOG.md` accurate and consistent with completed work. Do not claim planned or unverified work as complete.

## Architecture and quality

- Keep UI, presentation state, domain decisions, and data access appropriately separated.
- Compose UI should render observable state and send user actions to presentation logic; avoid embedding database, networking, or parsing work in composables.
- Repositories should provide a clear boundary for data sources. Keep Room access behind repositories and isolate network and HTML parsing code from UI code.
- Use lifecycle-aware state collection, structured coroutines, and explicit error/loading/empty states where applicable.
- Prefer small, cohesive modules and types. Avoid unnecessary abstractions and keep public APIs narrow.
- Add or update focused tests for behavior introduced or changed. Preserve relevant existing tests.

## Change reporting

- Summarize the files and behavior changed.
- State the exact checks that were run and their results. Clearly distinguish tests, builds, static checks, and manual inspection.
- Call out any validation that could not be performed and why.
