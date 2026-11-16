---
applyTo: "**/*{Dao,Database,Entity,Migration*,Repository}.kt"
---

# Local database and data-layer instructions

- Use Room for persistent application data stored on the device. Keep core manga tracking available offline; do not require a remote database or cloud service.
- Keep entities focused on persistence, DAOs responsible for database queries, and repositories responsible for coordinating data sources and exposing app-level operations.
- Do not access Room directly from composables or place SQL/database logic in ViewModels.
- Use suspend functions and/or Flow for asynchronous database operations, following the project's established patterns. Keep blocking database and disk work off the main thread.
- Define and register explicit Room migrations when changing a persisted schema. Never use destructive migration behavior or discard user data as a shortcut unless the user explicitly authorizes it.
- Make query behavior clear and efficient. Use transactions when a logically atomic operation spans multiple database actions.
- Keep remote/network data, when explicitly requested, behind a separate data source and repository boundary. Network or HTML parsing failures must not erase or invalidate locally stored tracking data.
- Test important persistence behavior, including relevant queries and migrations. Inspect entities, DAOs, database setup, repositories, and existing tests before schema or data-flow changes.
- Preserve existing stored data and functionality. Explain any unavoidable data or schema impact before making it.
