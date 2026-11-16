# Manga Tracker

Manga Tracker is a native Android app project for keeping a personal manga list and reading progress organized. Its core purpose is local tracking: viewing and maintaining the user's collection must not require a server, Firebase, a paid API, or a cloud account.

> **Project state:** the app includes local library/details, explicit read state, manual refresh, WorkManager checks, chapter notifications, and a persisted system/light/dark theme. Theme and language follow the device by default; Portuguese (Brazil) and English resources are available, with English fallback. Device testing and full site compatibility remain pending. WorkManager may defer its hourly schedule.

## Configured technology

- **Kotlin 2.1.21**, **Gradle Kotlin DSL**, **AGP 8.10.1**, **Gradle 8.11.1**, and **JDK 17**.
- **Jetpack Compose** and **Material 3**, with Navigation Compose destinations for the library, site discovery, manga details, and settings.
- **MVVM** and the **Repository Pattern**, with a manually composed dependency container in the initial shell.
- **Room 2.7.2** for the local data layer (schema v2, explicit reading state/history, and a tested non-destructive v1-to-v2 migration) and **DataStore** for library layout and theme preferences.
- **OkHttp** fetches bounded public HTML, JSON from explicitly allowed APIs, and cover images with public-address and redirect checks; **Jsoup** extracts generic metadata and static markup; manual CSS/RE2J rules can be previewed, tested, and saved per host. JavaScript is not executed. WorkManager schedules checks approximately hourly when network and battery constraints permit.
- `minSdk` is **26** (Android 8.0); `compileSdk` and `targetSdk` are **35**.
- The app follows the **device language** by default, with Portuguese (Brazil) and English resources; English is the fallback for other locales. Displayed dates follow the system locale. Settings offer system, light, and dark themes; the selected mode is saved locally, with system theme as the default.

## Requirements

To develop and run the Android app:

1. Install a current stable version of **Android Studio**.
2. Use **JDK 17**, the configured version for this project.
3. Install **Android SDK Platform 35** and compatible build tools through Android Studio's SDK Manager.
4. Use the project's **Gradle Wrapper** (`gradlew.bat` on Windows; version 8.11.1).
5. Have internet access available for initial tool, SDK, and dependency downloads. This is a development setup requirement, not a requirement for the app's local tracking features.

The package ID `com.sanzenkai.mangatracker` is provisional until confirmed for distribution. No release signing configuration is included.

## Open in Android Studio

1. Clone the repository and open Android Studio.
2. Choose **Open** and select the repository root containing `settings.gradle.kts`.
3. Allow Android Studio to sync Gradle and install any SDK components it reports as missing.
4. Select the app run configuration and an Android 8.0/API 26-or-newer emulator or connected device.
5. To build the debug APK from Android Studio's Terminal or PowerShell at the repository root, run:

   ```powershell
   .\gradlew.bat :app:assembleDebug
   ```

   The APK is written to `app/build/outputs/apk/debug/app-debug.apk`. To run the available JVM tests, use:

   ```powershell
   .\gradlew.bat :core:data:testDebugUnitTest :core:parser-engine:test :parsers:site-adapters:test :app:testDebugUnitTest
   ```

The handoff bundle is `Manga-Tracker-source.zip` in the repository root. It contains the source, Gradle Wrapper, documentation, and Copilot instructions; generated build directories, `.git`, local SDK settings, and signing credentials are excluded. The current archive predates the latest metadata-backfill changes and should be refreshed before sharing a source handoff.

The “Add a manga” screen accepts a manga or chapter URL and includes a Paste action. For HiveToons, a validated newest-first window is enough to track forward: registration saves only the latest chapter as a baseline, and later checks save only chapters newer than that baseline; the full historical archive is not required. The Imperio da Britannia HTML page is JavaScript-rendered, but the site's public client exposes `X-NoEncryptionBritta: 1` to request JSON from its public API. The app now sends that header only to the allowlisted Imperio API host. The supplied chapter-185 API sample returned entries through chapter 186 and the cover loaded in a browser; its declared total conflicts with the list, so the app does not claim a complete archive. No in-app refresh has been performed. Other sites need a validated list/window or a tested manual configuration for automatic updates. Ambiguous or failed results do not overwrite saved chapters. Opening a chapter does not change reading state. New manga start with monitoring enabled when the parser can verify a usable current chapter; monitoring can be paused/resumed per manga. New chapters trigger notifications after a successful check; notification permission can be requested explicitly from Settings. The notification's open action does not mark a chapter read; its separate read action persists the read state. Theme and library preferences are stored locally; language follows the Android system setting.

## Project documentation

- [Project status](./PROJECT_STATUS.md) — completed preparation, pending development stages, and next tasks.
- [Product specification](./PRODUCT_SPEC.md) — functional requirements and acceptance invariants.
- [Changelog](./CHANGELOG.md) — recorded project changes.
- [Architecture](./ARCHITECTURE.md) — intended layers and module organization; clearly distinguished from implemented code.
- [Supported sites](./SUPPORTED_SITES.md) — site validation policy and verified compatibility results.
- [Known issues](./KNOWN_ISSUES.md) — current blockers and unverified limitations.
- [Copilot instructions](./.github/copilot-instructions.md) and [repository instructions](./AGENTS.md).
- [Manga Tracker Copilot agent](./.github/agents/manga-tracker.agent.md) and the [Agent Skills](./.github/skills/).

## Tests and build status

The broader module verification for system-language localization and the settings layout refinement ran on 2026-11-15:

```powershell
.\gradlew.bat --no-daemon --rerun-tasks :core:localization:test :core:data:testDebugUnitTest :core:parser-engine:test :parsers:site-adapters:test :app:testDebugUnitTest :app:lintDebug :core:data:lintDebug :app:assembleDebug
```

Result: **BUILD SUCCESSFUL**; all **62 JVM tests passed** (app 21, core data 26, parser engine 12, site adapters 2, localization 1), with zero failures, errors, or skips. App and core-data debug lint tasks passed.

After configuring the supported Imperio plaintext-response header and adding regression tests, the latest verification ran:

```powershell
.\gradlew.bat --no-daemon :core:data:testDebugUnitTest :parsers:site-adapters:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Result: **BUILD SUCCESSFUL**; all **110 JVM tests** passed (core data 37, site adapters 22, app 51), with zero failures, errors, or skips. App debug lint passed. The latest debug APK is 20,975,225 bytes at `app/build/outputs/apk/debug/app-debug.apk`. The refreshed source bundle `Manga-Tracker-source.zip` contains 174 entries and excludes build outputs and local credentials.

The parser fallback is covered by offline fixtures for static HTML only; arbitrary live-site compatibility has not been established. An earlier manual smoke test installed and launched the app on a Pixel 6 API 35 emulator; `MainActivity` remained in the foreground with no app crash recorded. No device was connected during the current build, so the latest UI changes were not manually exercised. The build reported that two native libraries could not be stripped and were packaged unstripped. Release signing is not configured; the debug APK is for development/testing, not a signed release. JavaScript-rendered pages remain limited as described in `SUPPORTED_SITES.md` and `KNOWN_ISSUES.md`.
