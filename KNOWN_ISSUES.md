# Known issues and limitations

**Last updated:** 2026-11-15

The static audit, available JVM tests, lint, and forced full Gradle build completed after a corrective pass. Device-level behavior remains untested. The items below describe verified project limitations or unmeasured risks, not defects reproduced on a device.

## Current blockers

- **WorkManager timing is approximate:** the app requests an hourly periodic dispatch, but Android may defer it for Doze, network/battery constraints, and system scheduling. Exact execution every 60 minutes is not guaranteed; device-level timing has not been measured.
- **Existing metadata is filled only after a page parse:** a manual or enabled automatic check can fill blank title/cover/series fields for previously saved or restored manga when the parser returns those values. Existing nonblank values are intentionally preserved. Paused manga need a manual refresh or monitoring re-enabled; network/parser failures or fields absent from a page cannot be backfilled. This update has unit-test coverage but no device/live-site validation.
- **Notification device behavior is unverified:** channel, permission UI, image loading, grouping, and independent actions pass unit tests and debug assembly, but have not been manually tested on an emulator/device.
- **Limited runtime/device verification:** a manual smoke test installed and launched the debug APK on a Pixel 6 API 35 emulator and observed no crash. The updated settings layout, notification interactions, theme switching, and complete UI flows have not been manually exercised on a device.
- **Android Studio is not installed in this environment:** validation used the locally available JDK 17.0.21 and Android SDK Platform 35/Build Tools 35.0.0. Install Android Studio for the normal IDE workflow.
- **No instrumented database/UI tests:** no module contains `src/androidTest` files. Room schema v3, repository workflows, the v1-to-v3 migration chain, notifications, and monitoring scheduling logic have JVM/Robolectric coverage only; `adb devices` reported no connected device.
- **No end-to-end site integration has been certified:** HiveToons accepts a validated newest-first window and its fixtures cover partial windows and a 427-record archive, but the app has not been run through a live refresh. If a saved chapter is absent from the returned window and its number cannot be compared safely, the check leaves chapters unchanged. RoliaScan's public page, chapter endpoint, and cover were inspected and its parser/fetcher are unit-tested, but no on-device addition or automatic refresh was run. For Imperio, the app now uses the plaintext-response header exposed by the site's public client; the supplied chapter-185 API sample returned 186 rows and its cover loaded in a browser, but the in-app path has not been exercised. Other layouts and Mairimashita live in-app behavior remain unverified. See [SUPPORTED_SITES.md](./SUPPORTED_SITES.md).
- **Coordinator maps have no eviction policy:** `MangaCheckCoordinator` retains in-memory mutexes by manga ID and host throttling state by domain for the process lifetime. No memory profile demonstrates a user-visible problem, but growth with many distinct IDs/domains has not been measured.
- **Dependency update advisories remain:** app lint reports 18 non-blocking recommendations for newer Gradle, Android Gradle Plugin, and AndroidX dependency versions. Versions were not changed during the audit because upgrades require a separate compatibility review. The build also reports that two native libraries could not be stripped and packages them as-is.
- **Language coverage is limited:** the app includes Brazilian Portuguese and English resources; other system locales use English fallback. The new system-language selection has not yet been exercised on an Android device.

## Decisions still pending

- Final confirmation of the provisional `applicationId` before distribution and additional emulator/device validation, including theme switching and the Android API 26 baseline.
- Backup import accepts only the current versioned JSON format; migration from other backup versions is not implemented.
- Further permitted-site testing and lawful-access review. HiveToons, Mairimashita Iruma-Kun, RoliaScan, and Imperio da Britannia have parser/request tests but are not certified end-to-end; Imperio still needs an in-app refresh check.
- Any future JavaScript-rendered content strategy; the current OkHttp/Jsoup pipeline intentionally does not execute JavaScript or bypass access controls.
- If notification permission or the channel is disabled, Android prevents posting and the app does not bypass that choice. Newly detected chapters stay queued and are retried at the next successful manual or automatic check; granting permission alone does not immediately schedule a retry, and WorkManager may defer the next automatic check.
- Previously paused manga remain paused after updates to site adapters; re-enable monitoring in the manga details screen to resume automatic checks. The app does not infer that a saved paused state was caused by an older parser limitation.
- Device-level notification permission behavior, notification-channel settings, expandable image layout, and external URL launching have not been manually tested.
- Whether future monitoring reports should expose additional throttling/retry state.
- Release variant, signing ownership, and distribution process.
- Whether/when to bound or clean the check coordinator's in-memory per-manga/per-host state after profiling.

## Limitations of future site support

- Public markup may change without notice; a parser can stop extracting fields even when a site remains reachable. Fixtures are snapshots and do not prove that the live page still matches.
- Open Graph, JSON-LD, Schema.org, and WordPress metadata do not guarantee chapter-level data or a consistent site layout.
- CSS selectors and regex are source-specific and require validation against representative pages and fixtures.
- Even an `ItemList` whose declared count matches the valid unique entries does not prove that a site is not paginated; manual CSS configuration cannot infer whether a selector covers every page.
- Network access, battery restrictions, site policy, and user permissions can limit background updates. Core local tracking must remain usable without those integrations.
- WorkManager cannot guarantee that a periodic check runs exactly on the requested one-hour boundary; the app must present actual check results/times and handle deferral.
- Imperio da Britannia's chapter list is absent from the static HTML shell, so the app does not execute its scripts. The site's public client exposes `X-NoEncryptionBritta: 1` to request JSON from its public endpoint; `SafeJsonFetcher` now sends this header only to the allowlisted API host. The supplied sample returned 186 rows while `total_capitulos` declared 148, so the parser does not claim a complete archive. The supplied CDN cover loaded in the browser at 2667×4000; Android rendering and an in-app automatic refresh remain unverified.
- RoliaScan's public chapter endpoint currently returns a full list for the inspected series, but its own client notes that requests are rate-limited by IP. The app requests at most one 500-item window per check, respects the existing per-host check interval, and does not fetch additional pages. Other series, pagination changes, and device-level image rendering are unverified.
- Generic chapter-window discovery is a heuristic for static HTML: it needs at least three identifiable same-host chapter links with a common inferred series path and a strictly ordered number/date signal. It can miss different URL conventions or ambiguous ordering; passing a synthetic fixture does not establish support for any live site.
- Some pages may not include chapter data in server-delivered HTML. If a supported public endpoint or other permitted extraction cannot obtain that data, the app must report the limitation and retain the last successful result rather than infer that there are no new chapters.

Functional behavior and failure invariants are specified in [PRODUCT_SPEC.md](./PRODUCT_SPEC.md). These are implementation risks/requirements, not defects confirmed in a running app.

## Reporting policy

Update this file when an issue is reproduced or a limitation is confirmed, and include the affected scope and evidence. Do not label planned work as a fix, claim a runtime defect without a reproduction, or report a test/build result that was not actually obtained.
