# GitHub Copilot instructions for Manga Tracker

This repository contains **Manga Tracker**, a local-first native Android application. Read and follow the repository's [`AGENTS.md`](../AGENTS.md) and any applicable files under `.github/instructions/` before making changes.

For product behavior, consult [`PRODUCT_SPEC.md`](../PRODUCT_SPEC.md) before implementing a feature. Treat it as the acceptance-criteria source of truth; its requirements are not evidence that a feature is already implemented.

## Project conventions

- Use Kotlin as the primary language and Gradle Kotlin DSL for Gradle build files.
- Use Jetpack Compose with Material 3 for the UI.
- Use MVVM with the Repository Pattern. Keep modules and layers cohesive, testable, and no more complex than needed.
- Use Room for local persistence, WorkManager for scheduled or deferrable background work, OkHttp for HTTP, and Jsoup for HTML parsing when a requested feature needs them.
- Keep core tracking data and workflows local. Do not introduce a required server, Firebase, paid API, or cloud dependency. Network-backed functionality must be explicitly requested and must not make offline use fail.

## Before and during changes

- Inspect the relevant implementation, tests, and build files before substantial changes. Identify existing behavior and reuse established project patterns.
- Preserve existing functionality. Do not remove it without explaining the reason and receiving the user's authorization.
- Keep changes focused. Do not implement application features when the task only concerns project setup, documentation, or instructions.
- Minimize dependencies, permissions, and unrelated edits; explain necessary additions.
- Keep `PROJECT_STATUS.md` and `CHANGELOG.md` accurate as development progresses. Record completed work, not assumptions or unverified plans.

## Verification and reporting

- Run pertinent tests after code changes and appropriate build or static checks when available.
- Never invent test outcomes or say the app compiled unless a compilation command was actually executed successfully.
- Report the exact checks run, their outcomes, and any checks that could not be run.
- Do not silently treat failures or missing tooling as successful validation.
