---
applyTo: "**/*.{gradle,gradle.kts,xml}"
---

# Android project instructions

- Target a native Android app named **Manga Tracker**. Keep Android-specific configuration compatible with the project's selected Android Gradle Plugin, Kotlin, and SDK versions; inspect the version catalog and Gradle files before changing versions.
- Use Gradle Kotlin DSL for build configuration. Prefer the Gradle Wrapper and the repository's established dependency/version-catalog conventions.
- Use Jetpack Compose and Material 3 for application screens and components. Keep themes, colors, typography, and other design-system decisions in the existing Compose theme structure.
- Request only Android permissions required by an explicitly requested feature. Explain privacy-sensitive permissions and avoid broad or unnecessary access.
- The app is local-first and must not require a server, Firebase, paid API, or cloud service for core tracking.
- Use WorkManager only for deferrable, persistent background work that fits its constraints and lifecycle. Do not use it as a substitute for immediate UI work.
- Keep dependencies minimal, compatible, and aligned with existing project configuration. Do not add a dependency when platform or existing project functionality is sufficient.
- Preserve existing manifest behavior, resources, build variants, and configurations unless the task authorizes a change. Do not remove functionality without explaining why and obtaining the user's authorization.
- Before substantial Android configuration changes, inspect affected modules, manifests, resources, Gradle files, and related tests. Run pertinent tests and build checks after changes when the configured toolchain permits; report exact commands and results.
