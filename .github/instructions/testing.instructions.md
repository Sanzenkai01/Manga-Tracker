---
applyTo: "**/*{Test,Tests}.kt"
---

# Testing instructions

- Add or update focused tests for behavior introduced or changed, using the repository's existing test frameworks and conventions.
- Use local unit tests for deterministic domain, ViewModel, repository, and parsing logic where practical. Use Android instrumentation tests when behavior depends on Android runtime, Compose UI, Room integration, or platform components.
- Prefer isolated tests with controlled dependencies and deterministic inputs. Do not make routine tests depend on external networks, paid APIs, Firebase, or cloud services.
- For Room changes, cover relevant reads, writes, and migrations. For background work, test scheduling and worker behavior using the project's configured test support. For Compose changes, test user-visible behavior and state where appropriate.
- Run the pertinent tests after code changes. Use the Gradle Wrapper and existing task names when available; inspect project configuration rather than assuming module or task names.
- Report the exact command and outcome. Never invent passing results or claim compilation without executing a successful compile/build command.
- If tests cannot run because the project, wrapper, SDK, dependencies, or tooling are unavailable, state that explicitly and do not represent the change as verified.
- Do not delete, weaken, skip, or rewrite an existing test merely to make a change pass without a clear reason and the user's authorization.
