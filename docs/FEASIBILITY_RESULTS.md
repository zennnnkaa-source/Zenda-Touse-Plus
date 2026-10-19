# FEASIBILITY_RESULTS.md — Phase 1 Test Ledger

**Last updated:** 2026-10-19  
**Overall result:** `PHASE 2 FOUNDATION IN PROGRESS — INPUT DELIVERY STILL UNVERIFIED`.

This file is an evidence log, not a prediction. An item can change to `PASS` only after observing the stated signal. Phase 0 explicitly contained no code/build/device tests; those original statements remain in `phase0-snapshot/`.

## 1. Authoring environment checks

| Check | Result | Notes |
|---|---|---|
| Project source and manifests written | `SOURCE PRESENT` | See root README and module source trees. |
| Structural Android project audit | `PASS — 0 errors, 1 warning` | Validator ran against the final source tree. The only warning is that the Gradle Wrapper set is absent; this is a structural heuristic, not a compile result. |
| Local JDK | `JDK 21 FOUND` (environment inspection) | The configured GitHub workflow provisions JDK 17. A local toolchain match could not be made. |
| Gradle executable / valid wrapper | `NOT AVAILABLE` | No Gradle executable or intact `gradle-wrapper.jar` was available to this authoring environment. No fake wrapper was added; CI explicitly installs Gradle 9.6.0. |
| Android SDK / `android.jar` | `NOT AVAILABLE` | No `$ANDROID_HOME`/`$ANDROID_SDK_ROOT` with platform files was found at authoring time. |
| Network dependency resolution | `NOT RUN` | The local environment could not be relied upon to download Android/Gradle dependencies; actual resolution is delegated to CI. |
| Android emulator / physical device | `NOT AVAILABLE` to this session | No device install, pairing, Shizuku bind, shell command or event delivery was executed. |
| Release APKs | `NOT CREATED IN AUTHORING ENVIRONMENT` | The Android Release workflow uploads signed APKs for both modules only after lint, unit tests, signing checks and signature verification pass. |

## 2. Static/source result

| Probe | Code status | Execution evidence | Result |
|---|---|---|---|
| Shizuku Binder availability + UID | Implemented in Lab source | None | `NOT RUN` |
| Shizuku permission request/listener | Implemented in Lab source | None | `NOT RUN` |
| Shizuku UserService helper binding | POC implementation added | None | `NOT RUN` |
| Shizuku shared helper + loopback tap/swipe | POC implementation added with coordinate/duration bounds and a shared helper runtime | None | `UNVERIFIED-DEVICE` |
| Direct ADB pairing | POC wrapper plus NSD discovery for `_adb-tls-pairing._tcp.`; normal UI takes only the six-digit code | No physical-device evidence | `EXPERIMENTAL / DEVICE UNVERIFIED` |
| Direct ADB connect + `id` | NSD discovery for `_adb-tls-connect._tcp.` then automatic connect and `id`; manual host/port fallback retained | No physical-device evidence | `EXPERIMENTAL / DEVICE UNVERIFIED` |
| Direct ADB helper bootstrap + loopback tap/swipe | POC bootstrap and shared `LocalHelperServer` added; no direct ADB input pipe | None | `UNVERIFIED-DEVICE` |
| Test target real event capture | Native Activity source logs actual touch down/move/up, multi-pointer touch, KeyEvent, generic and captured pointer motion | None | `NOT RUN` |
| Simultaneous injected gestures | Not implemented by the bounded shell-command helper; test target can log physical multi-touch only | None | `NOT IMPLEMENTED / OPEN EXPERIMENT` |
| Timing | UI reports helper request round-trip time, not end-to-end input delivery latency | No repeated measurements yet | `SOURCE ONLY / NOT MEASURED` |
| Port/code/command/helper-request test source | JUnit tests written; separate standalone Kotlin assertions also executed | 26 standalone policy assertions passed; Gradle/JUnit test task not run | `PARTIAL — policy assertions PASS; project test task NOT RUN` |
| Kotlin source syntax pass | No parser/syntax diagnostics after source review | Host Kotlin 1.9 compiler cannot resolve Android and third-party APIs without Android SDK/dependencies; this is not a compile | `PARTIAL — syntax scan only` |
| Lint + signed Release APK assembly | `Android Release` workflow targets SDK 36, runs lint and unit tests, builds both signed Release APKs, and verifies their signatures | Latest audited revision has not run in Actions yet | `NOT RUN` |

## 3. Required result matrix (fill after running `docs/TEST_PROTOCOL.md`)

| ID | Device / Android build | Path | Step | Observed signal | Result | Evidence/log (redacted) |
|---|---|---|---|---|---|---|
| CI-01 | GitHub Actions runner | Build / lint / tests | Run `Android CI` workflow | Run URL + summaries | `NOT RUN` | Add URL after execution |
| SH-01 | Not recorded | Shizuku | Binder + permission + helper UID | Binder response and UID | `NOT RUN` | Add redacted result |
| SH-02 | Not recorded | Shizuku | Test one tap | Target app shows real `TOUCH DOWN/UP` rows | `NOT RUN` | Add redacted result; no pairing codes |
| ADB-01 | Not recorded | Direct ADB | Pairing | Library returns success | `NOT RUN` | Do not include temporary code |
| ADB-02 | Not recorded | Direct ADB | Connect + shell `id` | Nonempty shell output | `NOT RUN` | Redact host info if sharing publicly |
| ADB-03 | Not recorded | Direct ADB | Test one tap | Target app shows real `TOUCH DOWN/UP` rows | `NOT RUN` | Add redacted result |
| HW-01 | Not recorded | USB/Bluetooth input | Keyboard/mouse/controller | Target logs events and correct sources | `NOT RUN` | Record hardware type/model only if useful |

## 4. What counts as a PASS

- **Build PASS:** GitHub Actions ran dependency resolution, all configured lint/unit-test tasks and both signed Release APK assembly tasks successfully on the pinned toolchain, followed by APK signature verification. Include the workflow URL.
- **Shizuku PASS for bind:** Binder is live, Shizuku permission has been granted, UserService bind callback succeeds, and the helper reports the UID via `Os.getuid()`. This is a helper-access PASS only, not injection PASS.
- **Direct ADB PASS for pairing:** library pairing succeeds using the actual pair port and current temporary code. This is not connection PASS.
- **Direct ADB PASS for shell:** connection using the separate connection port succeeds and fixed `id` command returns output. This is not input PASS.
- **Input-delivery PASS:** the separate InputBridge Test Target displays the actual corresponding touch/key/mouse event after the probe. A shell exit code, successful Binder, tap/swipe helper response or empty command output cannot substitute for this observation.
- **Game compatibility:** not part of this phase. Do not claim it from a test-target pass.

## 5. Current blocker and phase decision

Input-delivery verification remains open, and continued development is intentionally kept in the same repository. The latest source adds route-state-driven tab selection, guided Developer Options launch, a waiting notification when notification permission is granted, NSD pairing/connection discovery, and an automatic Pair → Connect → helper path. These are source changes, not proof that mDNS resolves on the user’s OEM or that input is delivered. The Android Release workflow has not run against this latest revision; no Gradle/Android build proof or target event log is available in this authoring environment. If a path fails on the first device, record OS/build/OEM and a redacted log. Keep injection `UNVERIFIED` until the Test Target visibly reports the event.
