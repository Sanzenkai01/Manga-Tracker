# Changelog

Project changes are recorded here. Entries describe repository changes, not planned or unverified app behavior.

## 2026-11-15 — Preenchendo metadados em mangás existentes

### Alterações

- Checagens manuais e automáticas agora aproveitam metadados disponíveis na página para preencher título, capa original e URL da série ausentes em registros existentes, incluindo mangás restaurados de backup.
- Os campos já preenchidos não são substituídos; a URL da série precisa pertencer ao domínio acompanhado. O preenchimento ocorre mesmo quando o parser identifica os metadados, mas não consegue validar a lista de capítulos.
- A atualização local do repositório é observável pela biblioteca e pelos detalhes; não exige cadastrar o mangá novamente.
- Mangás pausados continuam sem checagens automáticas; a atualização manual permanece disponível. A execução depende da próxima checagem e dos limites do WorkManager.

### Verificação

- Passou: `gradlew.bat --no-daemon :core:data:testDebugUnitTest :app:testDebugUnitTest :core:data:lintDebug :app:lintDebug :app:assembleDebug` — **BUILD SUCCESSFUL**; 92 testes passaram (39 core/data, 53 app), sem falhas, erros ou skips. Lint debug dos módulos `core:data` e `app`, e montagem do APK debug também passaram.
- O build emitiu o aviso conhecido de incompatibilidade de versão XML entre ferramentas do Android SDK. Não foi realizado teste em dispositivo nem refresh ao vivo.

## 2026-11-15 — Retentando notificações pendentes

### Alterações

- Capítulos encontrados em uma checagem bem-sucedida agora entram em uma fila persistida localmente antes da publicação. Se a permissão ou o canal impedir a notificação, uma próxima checagem bem-sucedida tenta publicá-la novamente.
- Capítulos já lidos saem da fila; capítulos de baseline do cadastro e de backup importado não geram avisos retroativos. A fila é removida junto com o capítulo.
- Room foi atualizado para o esquema v3 com migração não destrutiva v2→v3; a cadeia v1→v2→v3 preserva os registros existentes.
- As checagens automáticas continuam usando a mesma cadeia de fetchers do cadastro para HiveToons, Mairimashita Iruma-Kun, Império da Britannia e RoliaScan. Mangás previamente pausados continuam pausados até a pessoa reativá-los nos detalhes.
- Atualizado `PROJECT_STATUS.md` e `KNOWN_ISSUES.md` com recuperação de avisos pendentes e limitações de execução/permissão.

### Verificação

- Passou: `gradlew.bat --no-daemon :core:data:testDebugUnitTest :app:testDebugUnitTest` — **BUILD SUCCESSFUL**; 90 testes passaram (38 core/data, 52 app), sem falhas, erros ou skips.
- Passou: `gradlew.bat --no-daemon :parsers:site-adapters:test :core:data:lintDebug :app:lintDebug :app:assembleDebug` — **BUILD SUCCESSFUL**; 22 testes de adaptadores, lint dos módulos `core:data` e `app`, e montagem do APK debug concluídos.
- Não foi feito teste em emulador/dispositivo ou atualização ao vivo nos sites; WorkManager continua sujeito às restrições e adiamentos do Android.

## 2026-11-15 — Corrigindo a resposta da API do Império

### Alterações

- A inspeção do cliente público do site revelou o cabeçalho `X-NoEncryptionBritta: 1`, usado pelo próprio cliente para solicitar JSON em vez da resposta opaca retornada sem ele. O app envia esse cabeçalho apenas ao host de API permitido; não executa JavaScript nem tenta decodificar respostas.
- Adicionado teste de regressão para garantir que cabeçalhos adicionais sejam enviados apenas ao host correspondente, além de teste do parser para o link de capítulo 185, capa e contagem declarada inconsistente.
- Atualizado `Manga-Tracker-source.zip` com os fontes e a documentação atuais; os 174 arquivos foram conferidos sem diretórios de build, `.git` ou credenciais locais.
- Atualizados `PROJECT_STATUS.md`, `SUPPORTED_SITES.md` e `KNOWN_ISSUES.md` com o comportamento observado e a limitação de não haver validação dentro do app.

### Verificação

- Passou: `gradlew.bat --no-daemon :core:data:testDebugUnitTest :parsers:site-adapters:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` — **BUILD SUCCESSFUL**; 110 testes passaram (37 core:data, 22 site-adapters, 51 app), sem falhas, erros ou skips; lint debug e montagem do APK debug concluídos.
- Uma requisição pública no navegador ao endpoint do link 185, com o cabeçalho documentado e sem credenciais, retornou HTTP 200 JSON com capítulos 1–186, incluindo 185 e 186. A resposta declara `total_capitulos=148`, portanto não é marcada como arquivo completo. A imagem da capa carregou no navegador em 2667×4000.
- Não foi feito teste de adição, atualização automática ou renderização dentro do app/dispositivo.

## 2026-11-15 — Integrando a lista pública de capítulos da RoliaScan

### Alterações

- Adicionado um adaptador para a página de série e o endpoint público que a própria RoliaScan usa para carregar capítulos. A requisição é limitada a uma janela de até 500 itens por verificação, sem paginação adicional.
- A validação confirma IDs únicos, ordem decrescente, domínio, slug, número e ID nas URLs dos capítulos. A capa vem dos metadados da série; uma lista inválida ou indisponível não substitui capítulos locais.
- Adicionados fixtures e testes para a resposta recente, capa, capítulo mais novo, URL divergente, ordem inválida e falha do endpoint.
- A chamada inicial ao endpoint do Império, sem o cabeçalho de resposta plaintext usado pelo cliente público, retornava texto opaco. A correção e a verificação do cabeçalho estão registradas na entrada seguinte.
- Atualizados `PRODUCT_SPEC.md`, `SUPPORTED_SITES.md`, `PROJECT_STATUS.md` e `KNOWN_ISSUES.md` com evidências e limitações.

### Verificação

- Passou: `gradlew.bat --no-daemon :parsers:site-adapters:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` — **BUILD SUCCESSFUL**; 72 testes passaram (21 site-adapters, 51 app), sem falhas, erros ou skips; lint debug e montagem do APK debug concluídos.
- A lista pública consultada uma vez para a amostra da RoliaScan retornou 286 capítulos com `has_more=false`; a URL da capa respondeu `200 image/jpeg` (96.528 bytes). Nenhum teste de interação em dispositivo/emulador ou refresh dentro do app foi executado.

## 2026-11-15 — Importando backup local

### Alterações

- Adicionado o botão **Carregar backup** nas Configurações, usando o seletor de documentos do Android e sem permissão ampla de armazenamento.
- O arquivo JSON é validado antes da importação e mesclado transacionalmente; dados atuais são preservados em conflitos e reimportações não duplicam histórico nem tentativas.
- Atualizados o requisito funcional e o status do projeto para incluir restauração não destrutiva.

### Verificação

- Passou: `gradlew.bat --no-daemon :core:data:testDebugUnitTest :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` — **BUILD SUCCESSFUL**; 84 testes JVM (36 em core/data e 48 no app), sem falhas, erros ou skips; lint debug e montagem do APK debug concluídos.

## 2026-11-15 — Validando janelas recentes e organizando a interface

### Alterações

- Corrigida a validação do HiveToons: um conjunto recente ordenado não depende de contagem histórica declarada, e links que não são capítulos não invalidam a lista.
- Uma janela recente validada não mostra mais um aviso sobre arquivo histórico incompleto; o status de verificação agora diz que não foi possível validar capítulos recentes.
- Adicionado o botão localizado **Colar** ao campo de link e divisores entre grupos das Configurações.
- Registrada a limitação observada na chamada direta do endpoint do Império da Britannia sem o cabeçalho plaintext do cliente público: ela retornou conteúdo opaco `text/plain`, não o JSON esperado pelo adaptador. O app não executa scripts nem decodifica essa resposta.

### Verificação

- Passou: `gradlew.bat --no-daemon :parsers:site-adapters:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` — **BUILD SUCCESSFUL**.
- A suíte de parsers do site passou com 17 testes; os 44 testes unitários do app, lint debug e montagem do APK debug também passaram.
- A página do Império foi inspecionada em um navegador: havia 42 correspondências de links `/capitulo/` e a capa renderizada em 600x804. O endpoint direto retornou `text/plain` opaco; o CDN da capa respondeu `200 image/webp`, 59.346 bytes. Isso não é uma verificação dentro do app nem comprova monitoramento automático.

## 2026-11-15 — Exporting the local library backup

### Changed

- Added a Room transaction-backed snapshot and versioned JSON export for manga, chapters, read states/history, monitoring settings, check attempts, and saved site configurations.
- Added a Settings action that opens Android's document picker so the user chooses the destination and file name. No broad storage permission or external service is required.
- Added localized in-progress, success, and error states. Import/restore is not included.
- Added repository, JSON-shape, and Settings ViewModel tests.
- Added backup behavior to `PRODUCT_SPEC.md` and documented the current validation limitation in `PROJECT_STATUS.md`.

### Verification

- Passed: XML parsing and localized backup resource-name checks for English and pt-BR.
- Installed JDK 17.0.21 and Android SDK Platform 35/Build Tools 35.0.0 under the current user so the project could be built.
- Passed: `gradlew.bat --no-daemon :app:assembleDebug :core:data:testDebugUnitTest :app:testDebugUnitTest` — **BUILD SUCCESSFUL**; all 78 targeted tests passed (core/data 34, app 44), with zero failures, errors, or skips.
- Verified debug APK at `app/build/outputs/apk/debug/app-debug.apk` (20,495,215 bytes); `apksigner verify` passed, and package/version/compile SDK metadata were confirmed. No device installation or interaction test was performed.

## 2026-11-15 — Supporting Mairimashita Iruma-Kun and compact navigation

### Changed

- Added a Mairimashita Iruma-Kun parser for its observed public series page, including title-matched original cover discovery and an ordered partial chapter window.
- Chapter URLs for the recognized series are fetched from the series page before registration, so the latest visible chapter becomes the monitoring baseline instead of making already-published chapters appear new later.
- Corrected generic grouping and series URL derivation for WordPress chapter-post slugs; decimal identifiers such as `443-5` are compared as `443.5`.
- Replaced the library's top-bar settings/add labels and the three back labels with accessible Material icons.
- Added the Compose Material extended-icons dependency for the requested gear icon.
- Corrected the sample domain in `PRODUCT_SPEC.md` and documented evidence and limitations in `SUPPORTED_SITES.md`.

### Verification

- Passed: `gradlew.bat --no-daemon :core:parser-engine:test :parsers:site-adapters:test` — **BUILD SUCCESSFUL**.
- App unit tests, lint, and APK assembly were not run: Gradle could not configure Android tasks because no Android SDK is installed or configured (`ANDROID_HOME`/`sdk.dir` missing). The app-level fetcher test is present but remains unexecuted.
- The series HTML was inspected publicly; this was not a live in-app fetch or monitoring test. Full-archive completeness and broad site compatibility are not claimed.

## 2026-11-15 — Integrating the Imperio da Britannia chapters API

### Changed

- Added a bounded JSON fetcher that restricts requests to explicitly allowed hosts, enforces HTTPS/443 in production, validates and pins public DNS results, refuses redirects, validates JSON media types, and limits response size.
- Added a site-specific adapter that obtains the observed public chapter API response for recognized series/chapter URLs and passes it through the existing discovery and monitoring pipelines.
- Added API parsing that validates the returned manga slug and chapter records, sorts the available chapters newest-first, and marks the latest chapter as known without treating the API's observed zero total as a complete archive.
- Preserved HTML fallback for registering a chapter link if the API is unavailable; a series URL reports the API failure instead of returning an empty success.
- Added offline parser fixtures and tests for API parsing, safe JSON fetching, app-level routing, and explicit failure behavior. Updated site support, known issues, project status, and README to distinguish test coverage from live compatibility.

### Verification

- Passed: `gradlew.bat --no-daemon :core:localization:test :core:parser-engine:test :parsers:site-adapters:test :core:data:testDebugUnitTest :app:testDebugUnitTest :app:lintDebug :core:data:lintDebug :app:assembleDebug` — **BUILD SUCCESSFUL**.
- All 102 JVM tests passed (app 39, core/data 33, site adapters 14, parser engine 15, localization 1), with zero failures, errors, or skipped tests. App and core-data debug lint passed.
- Debug APK rebuilt at `app/build/outputs/apk/debug/app-debug.apk` (14,165,214 bytes). No live in-app refresh or device interaction test was performed.
- Refreshed and verified `Manga-Tracker-source.zip` with 159 source/documentation files, including the new API adapter, tests, and fixture; generated build outputs and `.git` data are excluded.

## 2026-11-15 — Compacting library cards and broadening static discovery

### Changed

- Simplified library cards to show the manga title, reading badge, and one compact chapter/unread summary; verification and monitoring details remain in manga details.
- Added a conservative generic fallback for static HTML chapter links. It groups same-host links by inferred series path, filters common navigation and related-content links, and accepts only a strictly ordered recent window. It does not claim that the list is complete or that arbitrary sites are supported.
- Added offline parser tests for route variations, unrelated links, newest-chapter selection, and rejection of an out-of-order window. Updated the manual-configuration ViewModel test to use a series URL where no chapter is directly identifiable.
- Updated site-support evidence, project status, and known limitations to distinguish synthetic fixture coverage from live-site compatibility.
- Refreshed `Manga-Tracker-source.zip` with the current source and documentation, excluding build outputs and local credentials.

### Verification

- Passed: `gradlew.bat --no-daemon :core:localization:test :core:parser-engine:test :parsers:site-adapters:test :core:data:testDebugUnitTest :app:testDebugUnitTest :app:lintDebug :core:data:lintDebug :app:assembleDebug` — **BUILD SUCCESSFUL**.
- Test result XML reports 92 JVM tests, with zero failures, errors, or skipped tests. Both debug lint tasks passed.
- Debug APK verified at `app/build/outputs/apk/debug/app-debug.apk` (14,165,214 bytes). No instrumented tests, live arbitrary-site checks, or device interactions were run.
- Source bundle verified at 154 entries; required code, project documentation, Gradle Wrapper, and Copilot instructions are included.

## 2026-11-15 — Tracking verified recent chapter windows

### Changed

- Fixed HiveToons validation so a correctly ordered, internally consistent recent chapter window is usable even when the page does not provide the full historical archive. The parser still rejects malformed records, invalid chapter order, duplicate identifiers, and mismatched links.
- Registration from a verified recent window saves only the latest chapter as the baseline. Later checks persist only entries newer than a matched saved chapter or comparable numeric baseline; ambiguous windows leave saved chapters unchanged.
- Added an Imperio da Britannia parser for the supplied series URL shape. It can identify series metadata but does not invent a current chapter or claim automatic monitoring for a JavaScript-rendered chapter list. A chapter URL can be saved with monitoring paused.
- Redesigned the add-manga form with a grouped card, larger URL field, clear preview, and concise site-specific guidance. Dynamic-series pages guide the user to provide a chapter link.
- Updated English and Brazilian Portuguese strings, tests, supported-site evidence, project status, known limitations, and the README.
- Refreshed the requested source bundle so it includes the current parsers, fixtures, and documentation while excluding generated build outputs.

### Verification

- Passed: `gradlew.bat --no-daemon :core:localization:test :core:parser-engine:test :parsers:site-adapters:test :core:data:testDebugUnitTest :app:testDebugUnitTest :app:lintDebug :core:data:lintDebug :app:assembleDebug` — **BUILD SUCCESSFUL**.
- JVM result files report 89 tests, 0 failures, 0 errors, and 0 skipped.
- Both debug lint tasks passed. Debug APK verified at `app/build/outputs/apk/debug/app-debug.apk` (14,164,594 bytes).
- Source bundle verified: `Manga-Tracker-source.zip` (154 entries; required parser, fixture, and documentation files present; no build outputs).
- No instrumented tests or live in-app site refresh were run; site compatibility remains unverified end-to-end.

## 2026-11-15 — Fixing HiveToons and adding library multi-select

### Changed

- Fixed HiveToons chapter-list completeness by reading and validating the chapter records already embedded in the server-delivered HTML. The parser checks the declared total, record count, unique slugs, matching chapter numbers, and same-host URLs; it does not execute site JavaScript.
- Added fixture coverage for partial responses, malformed records, decimal chapter identifiers, and a 427-record payload. Live response inspection confirmed 427 unique serialized records for the declared Eleceed total; the app itself was not run against that live page.
- Added long-press selection in library list and grid layouts, batch mark-read/mark-unread actions, and a removal confirmation explaining that associated chapters and reading history will also be deleted.
- Made batch reading changes and manga removal transactional. Successful removal also cancels the selected manga's scheduled checks and active notifications.
- Updated the product specification, project status, site evidence, and known limitations.

### Verification

- Passed: `gradlew.bat --no-daemon :parsers:site-adapters:test :core:data:testDebugUnitTest :app:testDebugUnitTest :app:lintDebug :core:data:lintDebug :app:assembleDebug` — **BUILD SUCCESSFUL**.
- Test result totals: 69 JVM tests (33 app, 29 core/data, 7 site adapters), 0 failures, 0 errors, 0 skipped.
- Both lint tasks and debug assembly passed. App lint reported 19 non-blocking warnings: 18 dependency-version recommendations and one pre-existing pt-BR plural warning.
- Verified debug APK: `app/build/outputs/apk/debug/app-debug.apk` (14,161,086 bytes). No instrumented/device interaction test or live in-app refresh was performed.

## 2026-11-15 — Adding reading filters and manga status badges

### Changed

- Added library filters for all manga, manga with no unread chapters, and manga with unread chapters.
- Added a status badge to each manga in both list and grid views. It uses the established colors: blue for already read, green for a newly detected unread chapter, and red for other unread chapters.
- Reused the same status badge component on the chapter-details screen. The library summary now derives its new-unread count from the last successful check and saved reading state; no database schema migration was needed.
- Added localized empty states for filters with no matching manga.
- Added the filter and badge behavior to the product specification.

### Verification

- Passed: `gradlew.bat --no-daemon :core:data:testDebugUnitTest :app:testDebugUnitTest :app:lintDebug :core:data:lintDebug :app:assembleDebug` — **BUILD SUCCESSFUL**.
- Test result totals: 58 JVM tests (31 app, 27 core/data), 0 failures, 0 errors, 0 skipped. App and core-data debug lint passed.
- Verified debug APK: `app/build/outputs/apk/debug/app-debug.apk` (14,090,010 bytes).
- No device/emulator visual validation was performed; the existing source ZIP was not refreshed.

## 2026-11-15 — Correcting site checks and chapter presentation

### Changed

- Fixed automatic checks to use the saved same-host series page when no manual page configuration is active; previously the coordinator fetched the registered chapter URL.
- Added a HiveToons series-page parser and fixture-backed completeness check. The observed page declares 427 chapters but serves only 25 links, so it remains explicitly incomplete and cannot reconcile or remove saved chapters.
- Recorded incomplete chapter lists separately from general check failures and clarified the limitation in the library/details status.
- Improved chapter cards and added distinct blue “read”, green “new”, and red “unread” states; removed repeated manga title and technical series URL from the detail header.
- Updated site support, architecture, project status, and known-limitations documentation with the observed result.

### Verification

- Passed: `gradlew.bat --no-daemon :parsers:site-adapters:test :core:data:testDebugUnitTest :app:testDebugUnitTest :app:lintDebug :core:data:lintDebug :app:assembleDebug` — **BUILD SUCCESSFUL**; app and site-adapter tests passed, both lint tasks passed, and the debug APK assembled.
- Passed with forced execution: `gradlew.bat --no-daemon --rerun-tasks :core:data:testDebugUnitTest` — **BUILD SUCCESSFUL**.
- Test result totals: 57 JVM tests (26 app, 27 core/data, 4 site adapters), 0 failures, 0 errors, 0 skipped.
- Verified debug APK: `app/build/outputs/apk/debug/app-debug.apk` (14,087,695 bytes).
- No device/emulator visual validation was performed; the existing source ZIP was not refreshed for this change.

## 2026-11-15 — Simplifying the add screen

### Changed

- Removed the explanatory paragraph and parser details/URLs from the normal result; the screen now shows only the manga title and linked chapter.
- Replaced the long incomplete-list explanation with a short note and kept manual site setup collapsed until requested.
- Shortened the search/add actions and manual setup guidance in English and Brazilian Portuguese.

### Verification

- Passed: `gradlew.bat --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` — **BUILD SUCCESSFUL**; all 22 app JVM tests passed with zero failures, errors, or skips; app debug lint passed.
- Verified debug APK: `app/build/outputs/apk/debug/app-debug.apk` (14,086,743 bytes).
- No connected device was available for visual validation.

## 2026-11-15 — Adding a manga from a chapter link

### Changed

- Fixed registration being blocked whenever a site did not expose a complete chapter list. If the title, series page, and chapter from the submitted link are identified, the app can now add the manga and seed only that known chapter.
- Kept incomplete results out of automatic refresh reconciliation, which still requires a complete, validated chapter list; existing saved chapters remain protected.
- Renamed the library action to “Adicionar um mangá” and simplified the add-screen heading, instructions, and incomplete-list guidance in English and Brazilian Portuguese.
- Updated the site-discovery and repository tests, and documented that adding a manga does not imply full automatic site support.

### Verification

- Passed: `gradlew.bat --no-daemon :core:data:testDebugUnitTest :app:testDebugUnitTest :app:lintDebug :core:data:lintDebug :app:assembleDebug` — **BUILD SUCCESSFUL**; 22 app and 27 core-data JVM tests passed with zero failures, errors, or skips; app and core-data debug lint passed.
- Verified debug APK: `app/build/outputs/apk/debug/app-debug.apk` (14,092,555 bytes).
- No connected device was available for visual/manual testing.

## 2026-11-15 — Simplifying interface text

### Changed

- Reworded automatic-search timing and status messages in English and Brazilian Portuguese to explain the feature without implementation terminology; clarified that Android may delay searches.
- Simplified notification and local-storage descriptions in Settings.
- Updated localization tests to check the user-facing wording and ensure the schedule description does not expose an implementation name.
- Refreshed `Manga-Tracker-source.zip` with the current source, documentation, and Copilot guidance.

### Verification

- Passed: `gradlew.bat --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` — **BUILD SUCCESSFUL**; all 21 app JVM tests passed with zero failures, errors, or skips; app debug lint passed.
- Verified debug APK: `app/build/outputs/apk/debug/app-debug.apk` (14,091,695 bytes).
- No Android device was connected for visual validation.

## 2026-11-15 — Settings layout refinement

### Changed

- Replaced the widely spaced library-layout radio actions with a Material 3 single-choice segmented control inside a grouped settings card.
- Grouped theme options into compact, accessible selectable rows with descriptive supporting text, and made the settings content vertically scrollable for shorter displays.
- Added localized English and Brazilian Portuguese descriptions for the layout and theme groups.
- Used the open-source Now in Android settings dialog as a reference for compact radio rows, grouped choices, and scrolling; used Material 3 segmented-button guidance for switching the library view.

### Verification

- Passed: `gradlew.bat --no-daemon --rerun-tasks :core:localization:test :core:data:testDebugUnitTest :core:parser-engine:test :parsers:site-adapters:test :app:testDebugUnitTest :app:lintDebug :core:data:lintDebug :app:assembleDebug` — **BUILD SUCCESSFUL**; 62 JVM tests passed without failures, errors, or skips; app and core-data debug lint passed.
- Verified updated debug APK: `app/build/outputs/apk/debug/app-debug.apk` (13,621,625 bytes).
- No Android device was connected for visual interaction testing.

## 2026-11-15 — Language follows the system

### Changed

- Changed the default app language from fixed Portuguese (Brazil) to the Android system language. Brazilian Portuguese resources are used for pt-BR; English is the fallback for other locales.
- Localized parser, manual-configuration, site-adapter, fetch, and monitoring feedback dynamically for English and Brazilian Portuguese. Displayed dates continue to follow the system locale.
- Updated the specification and project documentation to describe system-language behavior and language coverage.
- Added Robolectric checks for English/pt-BR resource selection and made parser configuration tests independent of the host test locale.

### Verification

- Passed: `gradlew.bat --no-daemon --rerun-tasks :core:localization:test :core:data:testDebugUnitTest :core:parser-engine:test :parsers:site-adapters:test :app:testDebugUnitTest :app:lintDebug :core:data:lintDebug :app:assembleDebug` — **BUILD SUCCESSFUL**; 62 JVM tests passed with zero failures, errors, or skips; app and core-data debug lint passed.
- Verified debug APK: `app/build/outputs/apk/debug/app-debug.apk` (13,620,065 bytes).
- No device was connected for manual locale/theme validation. The refreshed source archive is `Manga-Tracker-source.zip`.

## 2026-11-15 — Dark theme and Portuguese (Brazil)

### Added

- Added persisted `SYSTEM`, `LIGHT`, and `DARK` theme preferences, with system theme as the default and a Settings selector.
- Added tests for theme preference persistence, SettingsViewModel state updates, and dark/light theme resolution.

### Changed

- Localized the default interface, site discovery/parser/fetch feedback, and displayed date/time formats to Portuguese (Brazil).
- Updated the functional specification, architecture, README, project status, and known limitations to include theme and language behavior.
- Refreshed `Manga-Tracker-source.zip` with the current source, documentation, and Copilot guidance; build outputs, local SDK settings, and signing files remain excluded.

### Verification

- Passed: `gradlew.bat --no-daemon --rerun-tasks :core:data:testDebugUnitTest :core:parser-engine:test :parsers:site-adapters:test :app:testDebugUnitTest :app:lintDebug :core:data:lintDebug :app:assembleDebug` — **BUILD SUCCESSFUL**; all 59 JVM tests passed with no failures or errors; app/core-data debug lint passed.
- Verified debug APK: `app/build/outputs/apk/debug/app-debug.apk` (13,614,850 bytes).
- Theme switching was not manually exercised on a device; no device was connected during this validation.

## 2026-11-15 — Android emulator smoke test

### Changed

- Installed the Android Emulator and API 35 Google APIs system image, created the `MangaTracker_API35` Pixel 6 AVD, and launched the existing debug APK.
- Confirmed `MainActivity` remained in the foreground and the app process stayed active with no AndroidRuntime crash marker. No application source or icon configuration changes were needed.

### Limitations

- This was a manual launch smoke test, not an instrumented/UI test. Notification interactions and end-to-end workflows remain unverified on a device.
- Emulator and API 35 system image were installed in the temporary Android SDK used for this workspace.

## 2026-11-15 — Debug and source handoff

### Added

- Prepared a source ZIP containing the project source, Gradle Wrapper, project documentation, and GitHub Copilot instructions/skills; generated build output, `.git`, local SDK settings, and signing credentials are excluded.
- Generated and verified the debug APK at `app/build/outputs/apk/debug/app-debug.apk`.

### Changed

- Expanded the README with Android Studio/Gradle build and test commands, artifact locations, and the exact final verification results.
- Updated project status with the final test, lint, APK, and source-bundle outcomes.

### Verification

- Passed: `gradlew.bat --no-daemon --rerun-tasks :core:data:testDebugUnitTest :core:parser-engine:test :parsers:site-adapters:test :app:testDebugUnitTest :app:lintDebug :core:data:lintDebug :app:assembleDebug` — **BUILD SUCCESSFUL**; 55 JVM tests passed with zero failures, errors, or skips; app and core-data debug lint passed; debug APK confirmed at 13,596,166 bytes.
- No instrumented tests or connected Android device were available. Two native libraries were packaged unstripped. The APK is a debug build; release signing is not configured.

## 2026-11-15 — Project preparation and documentation

### Added

- Repository guidance for GitHub Copilot and a specialized Manga Tracker custom agent.
- Nine Agent Skills covering Android architecture, Kotlin/Compose, manga parsing, site discovery, Room, notifications, background monitoring, testing, and Android release preparation.
- Project status, proposed architecture, supported-site verification register, and known-issues documentation.

### Changed

- Expanded the README with the product goal, intended technology stack, development prerequisites, Android Studio opening instructions, and the current non-runnable project state.

### Not implemented or verified

- No Android application code or user-facing features were added.
- No Android tests or Gradle build were run; this repository does not yet contain an Android Gradle project.
- No external site has been tested or declared compatible.

## 2026-11-15 — Functional specification

### Added

- `PRODUCT_SPEC.md` with the complete 25-point functional specification, including local persistence, manual and periodic monitoring, explicit reading actions, parser error invariants, site-access boundaries, and Android 8.0/API 26 minimum.
- Three user-provided chapter links as initial site-analysis examples, explicitly marked as untested and not supported.

### Changed

- Updated the README and project status to reflect the now-specified Android minimum and functional scope while keeping implementation status pending.
- Updated the architecture, site-verification register, and known-issues context to preserve the specification without claiming implemented behavior or successful site tests.
- Updated repository, Copilot, and custom-agent guidance to use `PRODUCT_SPEC.md` as the functional acceptance-criteria reference.

### Not implemented or verified

- No application functionality, site parser, or Android build/test was implemented or run as part of recording the specification.

## 2026-11-15 — Technical architecture plan

### Added

- Detailed `ARCHITECTURE.md` planning package/module boundaries, Room entities and DAOs, repositories, ViewModels, Compose screens/navigation, site discovery, independent parsers, WorkManager, notifications, reading history, tests, and build strategy.
- Static-first JavaScript-content handling: document OkHttp/Jsoup limits, disallow bypassing site protections, and reserve any future WebView rendering for a reviewed, constrained foreground flow; dynamic-only sources remain an explicit error for automatic background checks.

### Changed

- Updated `PROJECT_STATUS.md` to mark the technical plan complete while leaving Android project scaffolding, implementation, tests, and builds pending.

### Not implemented or verified

- No application modules, Kotlin/Room/Compose code, parser, worker, notification, test, or build were created or executed as part of this planning task.

## 2026-11-15 — Initial Android project scaffold

### Added

- Multi-module Gradle Kotlin DSL setup with an Android app, domain/data modules, parser API/engine, and a site-adapter module.
- Initial Android app shell: application class, MainActivity, Material 3 theme, one Navigation Compose destination, an empty library screen, and a ViewModel.
- Room schema v1 entities and DAOs for the local library, chapters, read state/history, parser configuration, monitoring state, and check attempts.
- Initial Room-backed library repository, DataStore layout-preference repository, parser contracts, and confidence-ordered parser registry.
- A focused JVM test for parser-candidate ordering and no-match behavior.

### Changed

- Updated the README, architecture, and project status to distinguish implemented scaffolding from planned product functionality.
- Configured Kotlin 2.1.21, AGP 8.10.1, Gradle 8.11.1, JDK 17, `minSdk` 26, and SDK 35; the initial build with this combination succeeded.

### Verification

- Passed `gradlew.bat :core:parser-engine:test :app:testDebugUnitTest :app:assembleDebug --stacktrace`; a forced test rerun also passed. The two parser registry tests and one ViewModel error-state test had no failures or errors.
- Generated `app/build/outputs/apk/debug/app-debug.apk`. It was not installed or exercised on a device/emulator; no site has been tested or declared compatible.

### Still pending

- Manga registration, site adapters, parsing/discovery workflows, monitoring workers, notifications, and end-to-end reading-history actions.

## 2026-11-15 — Room persistence and reading-state workflows

### Added

- Room schema v2 with manga, chapter, reading-state, reading-history, site-configuration, monitoring-settings, and check-attempt persistence.
- Non-destructive v1-to-v2 migration that renames the existing configuration and monitoring tables and adds a chapter ordering index.
- Repository workflows for idempotent chapter reconciliation, unread counts, site configuration, per-manga monitoring, and check attempts.
- Explicit read/unread transitions that record history in the same transaction. New chapters begin unread; passive chapter observation and metadata refresh do not change existing read state.
- A validated snapshot boundary that rejects incomplete parser results and prevents an empty valid refresh from deleting stored chapters.
- Seven Room/repository tests covering the read-state invariants, persistence, relations, incomplete parsing, and v1-to-v2 data preservation.

### Verification

- Passed: `gradlew.bat --rerun-tasks :core:data:testDebugUnitTest :app:testDebugUnitTest :core:parser-engine:test :app:assembleDebug --stacktrace --no-daemon` (**BUILD SUCCESSFUL**).
- All 10 JVM tests passed: 7 data/repository, 1 app ViewModel, and 2 parser-registry tests.
- Room schema v2 was exported and the debug APK was assembled. No emulator/device or live-site test was performed.

### Still pending

- Connect the repository workflows to complete user-facing manga registration, library/detail, monitoring, and notification flows.

## 2026-11-15 — Site discovery and static parsing foundation

### Added

- Parser document/preview models, confidence-ordered discovery, generic HTML/Open Graph/JSON-LD extraction, Schema.org `ItemList` validation, WordPress hints, and dynamic-shell detection.
- A HiveToons Article adapter based only on the metadata observed in the supplied Eleceed sample; sanitized fixture tests confirm its partial result.
- Manual per-host CSS/RE2/J configuration, validation, preview/test, JSON persistence, and a Compose discovery screen reachable from the library.
- OkHttp HTML fetching with public-address validation, pinned DNS results, strict redirect rules, bounded response size, timeouts, and explicit error classifications.
- Sanitized fixtures and offline tests for HiveToons and Imperio da Britannia, fetcher behavior, and configuration persistence gating.

### Changed

- Added the parser API as a direct app dependency because UI/ViewModel code consumes its public result types.
- Corrected invalid regex handling to report configuration errors rather than throwing during validation.
- Updated project status, architecture, README, known limitations, and site evidence. Neither sample is declared fully compatible; the Mairimashita sample remains untested after DNS resolution failed.

### Verification

- Passed: `gradlew.bat --rerun-tasks :core:parser-engine:test :core:data:testDebugUnitTest :parsers:site-adapters:test :app:testDebugUnitTest :app:assembleDebug --no-daemon`.
- 26 JVM tests passed across parser engine, data/network, site adapters, and app ViewModels; debug APK assembly succeeded.
- No emulator/device run or live end-to-end app/site integration test was performed.

## 2026-11-15 — Initial Manga Tracker UI workflows

### Added

- Responsive library list/grid presentation with a persisted DataStore layout preference, asynchronous Coil cover loading, unread/latest-chapter summaries, verification time, and monitoring state.
- Manga registration from a chapter URL, gated on a successful parse with an identified title/series/current chapter and a complete chapter list; registration seeds the local Room repository and detects an already tracked series.
- Manga details and chapter list, with independent open and explicit mark-read/mark-unread actions, unread count, monitoring preference, and a manual refresh action.
- Settings screen for the saved library layout and site discovery/configuration entry point.
- Chapter-label mapping for numbered, decimal, special, and extra chapters; added registration, incomplete-refresh, and mapper tests.

### Changed

- Manual refresh now persists only a complete successful parser snapshot and records failed/incomplete checks without interpreting them as an empty list.
- Updated README, project status, architecture, and known issues to distinguish the implemented UI from pending background scheduling and notifications.

### Verification

- Passed: `gradlew.bat --rerun-tasks :core:parser-engine:test :core:data:testDebugUnitTest :parsers:site-adapters:test :app:testDebugUnitTest :app:assembleDebug --no-daemon` (**BUILD SUCCESSFUL**).
- 30 JVM tests passed: 10 parser engine, 13 data/network, 2 site adapters, and 5 app ViewModels. Debug APK assembled successfully; no emulator/device run was performed.
- Validation used temporary JDK 17, Android SDK Platform 35, and Build Tools 35.0.0.

### Pending at the end of the UI milestone

- WorkManager scheduling/cancellation, periodic checks, and notification delivery/actions remained for the next implementation milestone.
- No site is declared fully compatible; static parsing limits and fixture evidence remain documented in `SUPPORTED_SITES.md`.

## 2026-11-15 — WorkManager monitoring implementation

### Added

- A unique one-hour periodic WorkManager dispatch that queues unique checks for enabled manga, using connected-network and battery-not-low constraints.
- Per-manga pause/resume integration, automatic check enqueueing after registration, retryable transient failure handling, exponential backoff, and diagnostic worker logs.
- A shared check coordinator for manual and background refresh, with per-manga serialization, a two-second in-process same-host request interval, persisted check outcomes, and complete-snapshot-only reconciliation.
- Tests for coordinator failure safety, retryable network classification, idempotent chapter discovery, pause during a check, and WorkManager unique scheduling/constraints/cancellation.

### Changed

- Newly registered manga now start with monitoring enabled so automatic checking begins without a separate setup action.
- UI and project documentation now state that Android may defer WorkManager runs and that one hour is not an exact execution guarantee.

### Verification

- Passed: `gradlew.bat --rerun-tasks :core:data:testDebugUnitTest :app:testDebugUnitTest :core:parser-engine:test :parsers:site-adapters:test :app:assembleDebug --no-daemon` (**BUILD SUCCESSFUL**).
- **36 JVM tests passed** across parser engine (10), data/network and Room (15), site adapters (2), and app (9), with zero failures, errors, or skipped tests. The debug APK assembled successfully; no emulator/device run was performed.

### Still pending

- Notification channel and chapter notification actions.
- Device/emulator validation, Compose/instrumented tests, and full site compatibility.

## 2026-11-15 — Chapter notifications

### Added

- Stable Android notification channel and a user-initiated Settings flow to request Android 13+ `POST_NOTIFICATIONS` permission and open the system's app-notification settings.
- Grouped per-chapter notifications with original cover art in an expandable BigPictureStyle when safely downloaded, and text-only fallback when the image is absent or unavailable.
- Separate immutable PendingIntents to open the validated chapter URL and to mark the chapter read through a non-exported receiver and the existing Room read/history transaction.
- Idempotent persistence of the time a notification was successfully posted. Notification delivery is connected to both manual and WorkManager-discovered chapters.
- Bounded public-host image fetching with pinned DNS, same-host redirects, image content-type checks, and response size limits.
- Tests for notification actions/PendingIntent payloads and flags, Android 8 notification channels, Android 13 permission denial, grouping, successful delivery wiring, and persistent read/notification state.
- Enabled Android resources in app unit tests for Robolectric notification channel/string checks and kept the WorkManager test on a neutral Application.

### Verification

- Passed: `gradlew.bat --rerun-tasks :core:data:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug --no-daemon` — 19 `:core:data` tests and 15 `:app` tests passed with zero failures, errors, or skipped tests; the debug APK assembled successfully.
- Notification permission prompts, grouping, and chapter opening have not been exercised on an emulator or device.

### Still pending

- Device/emulator validation of permission prompts, notification rendering/grouping, and external chapter opening.
- Compose/instrumented tests and full site compatibility.

## 2026-11-15 — Comprehensive audit and corrective pass

### Changed

- Preserved already-known chapter publication dates when a refresh omits the date, and added a numeric tie-breaker for chapters sharing timestamps.
- Bounded JSON-LD script/aggregate size, nesting depth, and structure count; only treat an `ItemList` as complete when its declared count matches the valid unique entries.
- Corrected public IPv4/IPv6 address filtering and added bounded image HTTP handling. The app-wide Coil loader now uses the public-host-validated image client; tests cover disallowed hosts/ports, redirects, and response size.
- Invalidated/cancelled stale site-analysis and manual-test jobs after URL/configuration changes, and added a deterministic ViewModel race test.
- Exposed the last monitoring outcome in library and detail screens. Added the application launcher icon from the existing project artwork, pluralized notification grouping text, and removed impossible API guards under `minSdk 26`.
- Handled notification-summary `SecurityException` explicitly, fixing the permission-related lint failure found in the initial audit build.
- Replaced a deprecated Bundle accessor in the notification test.

### Verification

- Passed: `gradlew.bat --no-daemon --rerun-tasks build` (**BUILD SUCCESSFUL**, 230 actionable tasks), including debug/release build tasks, unit tests, and lint.
- Passed JVM tests: `:app` 16 tests per debug/release variant; `:core:data` 25 tests per debug/release variant; `:core:parser-engine` 12; `:parsers:site-adapters` 2. Zero failures/errors/skips (55 unique test cases; 96 executions including both app/data variants).
- After the final test-only Bundle accessor change, `:app:testDebugUnitTest` and `:app:testReleaseUnitTest` were rerun successfully (16 tests each).
- Lint has no remaining app findings other than 18 non-blocking Gradle/AGP/dependency-version recommendations; `:core:data` reports no lint issues. Two native libraries could not be stripped and were packaged unstripped.
- Debug APK and unsigned release APK were generated. No instrumented test source set or connected Android device was available; no APK/device or live-site test was performed.

### Remaining limitations

- WorkManager timing remains approximate; no site is fully compatible; JavaScript-rendered and potentially paginated chapter lists remain unverified.
- Coordinator mutex/throttle maps have no eviction policy; memory/battery impact has not been profiled.
- Notification behavior, API 26 runtime behavior, and UI interaction still need emulator/device validation. Release signing/distribution remains unconfigured.
