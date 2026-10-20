# RESEARCH.md — InputBridge Phase 1

**Research checked:** 2026-10-20  
**Scope:** Activation & Input Feasibility Lab only.  
**Evidence labels:** `DOC-VERIFIED` = checked in a current primary source; `SOURCE-OBSERVED` = stated by upstream project source but unaudited; `STATIC-DESIGN` = implemented in source but not compiled; `UNVERIFIED-DEVICE` = requires installation on real Android hardware; `NOT-RUN` = no execution evidence.

Phase 0 documents are preserved under [`phase0-snapshot/`](phase0-snapshot/). Those docs reported that no code, build, or physical-device test had been executed. Phase 1 does not overwrite that history.

## 1. Source-verified findings

| Area | Current finding | Evidence / consequence |
|---|---|---|
| Shizuku identity | Shizuku can provide Java/JNI execution using the server identity (ADB shell or root). The app must inspect reported UID and permission instead of assuming root. | `DOC-VERIFIED`; official [Shizuku API developer guide](https://github.com/RikkaApps/Shizuku-API). The test Lab displays the helper's actual reported UID when the binding works. |
| Shizuku lifecycle | Non-root Shizuku needs manual restart after reboot; Android 11+ Wireless debugging can be used to start it on-device. Binder receive/death listeners are provided by the API. | `DOC-VERIFIED`; [Shizuku API guide](https://github.com/RikkaApps/Shizuku-API). Reboot recovery is documented but not device-tested in this phase. |
| Shizuku UserService | Official API exposes `api` / `provider` artifacts and a UserService/Binder integration pattern. | `DOC-VERIFIED`; source/guide reviewed at the official repo. Dependency is pinned provisionally to `13.1.5`; CI resolution still needs to confirm it. |
| Wireless debugging | Android 11+ offers pairing with a temporary six-digit code; pairing and subsequent connection use separate ports. Both ports can be ephemeral. | `DOC-VERIFIED`; [Android ADB / Wireless debugging documentation](https://developer.android.com/tools/adb#wireless-android11-command-line). The UI intentionally provides separate fields and does not retain the code in saved UI state. |
| Direct ADB library | Upstream `libadb-android` README documents `pair(host, port, pairingCode)`, `connect(host, port)` and shell services. The README explicitly warns the library has not received a security audit. | `SOURCE-OBSERVED`; [libadb-android upstream](https://github.com/MuntashirAkon/libadb-android). Version `3.1.1` is a candidate, not a passing integration. The library is dual-licensed GPL-3.0-or-later OR Apache-2.0, and the README notes an LGPL dependency. A legal/security review remains open. |
| ADB identity | Upstream examples need a key and certificate. The Lab POC generates an RSA identity and encrypts its PKCS#8 private-key bytes with an AES-GCM key protected by Android Keystore. | `STATIC-DESIGN`; crypto implementation and persistence have not been compiled, audited or tested. Do not treat this as a security audit or production credential store. |
| Input target | The separate native test app records KeyEvent, touch/pointer and generic mouse/scroll events delivered to its own Activity. Pointer capture remains a test of input delivered to that app, not a system-wide guarantee. | Design follows Android input APIs. Source exists, but compilation/runtime and physical mouse/controller behavior are `UNVERIFIED-DEVICE`. |
| Accessibility fallback | Accessibility gesture dispatch remains a possible later fallback only; it is not the primary Phase 1 test route and is not implemented as a claimed working mapper. | Carried from Phase 0 research; policy and gesture limitations need their own evidence/review before use. |
| AGP/Gradle/JDK | AGP `9.4.0` release notes (September 2026) specify Gradle `9.6.0`, JDK 17, Build Tools `36.0.0`, maximum API level 37, and recommend KGP `2.2.10`. | `DOC-VERIFIED`; [AGP 9.4.0 release notes](https://developer.android.com/build/releases/agp-9-4-0-release-notes). Build uses JDK 17 and Gradle 9.6.0 in CI, compile SDK 36. No local build was run. |
| Compose versions | The Gradle catalog pins Compose BOM `2026.04.01`, Activity Compose `1.12.4`, Navigation Compose `2.9.8`, and Lifecycle `2.10.0`. These versions are selected for the API 36 build baseline; Compose 1.12 / BOM `2026.08.00` and Lifecycle 2.11 require API 37, so they are deliberately not used in this compatibility track. | Checked against the official [Compose BOM page](https://developer.android.com/develop/ui/compose/bom) and [AndroidX release notes](https://developer.android.com/jetpack/androidx/versions). Dependency resolution still needs CI. |

## 2. Feasibility matrix after source review

| Path / target | Result in this Phase 1 package | What it proves today |
|---|---|---|
| Shizuku availability and UID probe | Source prepared; `NOT-RUN` | Nothing until the Lab builds, binds and reads a real Binder response. |
| Shizuku shared helper → loopback `TAP` → test target | POC code prepared; `UNVERIFIED-DEVICE` | Must be evidenced by a real touch row in the target app. A helper response alone is not a pass. |
| Direct ADB pairing | POC code prepared; `NOT-RUN` | Does not prove TLS connection or a usable shell. |
| Direct ADB `id` shell probe | POC code prepared; `NOT-RUN` | Does not prove input injection. |
| Direct ADB bootstrap → shared loopback helper → target | POC code prepared; `UNVERIFIED-DEVICE` | Must be evidenced by actual target event log on device. |
| Keyboard/mouse/gamepad events | Target app source prepared; `NOT-RUN` | Requires real devices or emulator input. |
| Third-party game compatibility | `UNKNOWN / OUT OF SCOPE FOR THIS SPIKE` | No game was tested. Do not bypass anti-cheat or infer compatibility. |

## 3. Deliberate technical boundaries

1. Shizuku and direct Wireless debugging are separate options. Direct ADB remains marked `Experimental`.
2. The direct ADB route is intended to bootstrap the shared `LocalHelperServer` runtime via an allow-listed `app_process` command, not to create an unrestricted terminal or use ADB as an ongoing input pipe. The Shizuku UserService starts the same server/runtime within its process. The post-bootstrap loopback protocol is limited to `UID`, `TAP x y`, and `STOP` commands.
3. Pair port, connection port, shell identity, helper bootstrap, helper UID and target event delivery are separate steps and separate evidence.
4. Pairing code is neither logged nor saved to preferences/files by our UI. It is cleared from the text field immediately after the user starts pairing. The ADB library receives the code in process memory for protocol use; full memory zeroization is not guaranteed by the JVM.
5. No root assumption, anti-cheat evasion, stealth behavior, telemetry or unnecessary location/camera/microphone permission is included.
6. The ADB client, certificate generation, Android Keystore wrapping and Shizuku code are not independently security-audited. Keep this build for controlled feasibility tests only.

## 4. Unresolved questions

- Does `libadb-android:3.1.1` resolve under this Gradle/JDK/AGP toolchain and successfully complete pairing and connect on the target OEM?
- Does the returned shell session support the fixed `pm path` lookup and detached `app_process` bootstrap reliably on the same Android 11+ device?
- Does the same `LocalHelperServer` runtime load from the installed base APK and accept authenticated loopback requests under shell UID?
- Does the helper's input command deliver a real `MotionEvent` to the separate test app on that device?
- Does Shizuku `UserService` report UID 2000 or UID 0, and is `/system/bin/input tap` accepted from that helper process on the specific device?
- Does the Android Keystore-wrapped ADB key survive app restarts and recover safely from a missing/invalidated wrapping key?
- Does CI resolve AGP, KGP, Compose BOM, Shizuku and JitPack dependencies without incompatibility?

All of these remain open until CI/device evidence is recorded in [`FEASIBILITY_RESULTS.md`](FEASIBILITY_RESULTS.md).

## 10. AndroidX Foundation re-check (2026-10-19)

This research update informs the UI/foundation work added in-place to the existing Phase 1 repository; it does not prove the newest source compiles.

| Component | Selected version | Evidence | Notes |
|---|---|---|---|
| AGP | 9.4.0 | [Official AGP 9.4.0 release notes](https://developer.android.google.cn/build/releases/agp-9-4-0-release-notes?hl=en) | Gradle 9.6.0, JDK 17 and Build Tools 36.0.0 are the official compatibility defaults; max API is 37. |
| Compose BOM | 2026.08.00 | [Official Compose August 2026 release](https://android-developers.googleblog.com/2026/08/jetpack-compose-august-2026-release.html) | Compose 1.12 stable; requires compileSdk 37 and AGP 9.1.1 or newer. |
| Navigation Compose | 2.10.1 | [AndroidX stable release list](https://developer.android.com/jetpack/androidx/versions/stable-channel?hl=en) | Stable release shown in September 2026. |
| Lifecycle | 2.11.0 | [Lifecycle release notes](https://developer.android.com/jetpack/androidx/releases/lifecycle?authuser=56) | Stable release shown June 17, 2026; used for `lifecycleScope` and lifecycle-bound work. |
| Room | 2.8.5 | [Room release guide](https://developer.android.com/jetpack/androidx/releases/room?hl=en) | KSP compiler configured; history stores enumerated probe labels only. |
| DataStore | 1.2.1 | [DataStore release notes](https://developer.android.com/jetpack/androidx/releases/datastore?authuser=90) | Stable release; used for the local light/dark preference. |
| KSP | 2.3.10 | [KSP requirements](https://kotlinlang.org/docs/ksp-overview.html) | Official Kotlin page lists support for Kotlin Gradle Plugin 2.2.10–2.3.x, AGP 8.12+, Gradle 8.13+ and JDK 17+. Actual build resolution remains pending. |

### Important distinction

The current app already used AndroidX Compose and Material 3 before this work. The change is an architecture/UI improvement (Navigation Compose, theme tokens, DataStore, Room), not a migration away from a non-AndroidX UI framework. A full Gradle build is still required to validate the combined AGP/Kotlin/KSP/Compose versions.


## 11. Build compatibility correction (2026-10-19)

The latest GitHub Actions log failed during Gradle configuration of `:input-target`, before compilation. The exact error was `Cannot add extension with name 'kotlin', as there is an extension already registered with that name.` The module applied `org.jetbrains.kotlin.android` in addition to AGP 9 built-in Kotlin. The fix removes the redundant Kotlin Android plugin and migrates `kotlinOptions` to the built-in `kotlin.compilerOptions` DSL.

The project and both workflows now use `compileSdk = 36` and install `platforms;android-36`, as requested. To maintain that SDK baseline, Compose BOM is pinned to `2026.04.01`, Navigation Compose to `2.9.8`, and Lifecycle to `2.10.0`; Compose 1.12 / BOM `2026.08.00` and Lifecycle 2.11.0 require API 37 per official release notes. This is a compatibility selection based on published requirements, not a successful Android build claim.

Sources:
- AGP built-in Kotlin migration: https://developer.android.com/build/migrate-to-built-in-kotlin
- Compose April 2026 release: https://developer.android.com/blog/posts/whats-new-in-the-jetpack-compose-april-26-release
- Compose August 2026 release: https://developer.android.com/blog/posts/what-s-new-in-the-jetpack-compose-august-26-release
- AndroidX Lifecycle release notes: https://developer.android.com/jetpack/androidx/releases/lifecycle
- AndroidX Navigation release notes: https://developer.android.com/jetpack/androidx/releases/navigation


## 10. Phase 2 Navigation and Direct setup research (checked 2026-10-19)

- Android Settings reference documents `Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS` as the public action for the Developer options screen. OEM Settings apps may vary, so a fallback to general Settings and clear manual directions remain necessary: https://developer.android.com/reference/android/provider/Settings#ACTION_APPLICATION_DEVELOPMENT_SETTINGS
- Android `NsdManager` supports DNS-SD service discovery and resolving service endpoints; discovery must be stopped when no longer needed and can fail depending on environment/network. The app watches the separate `_adb-tls-pairing._tcp.` and `_adb-tls-connect._tcp.` service types and does not hardcode ports: https://developer.android.com/reference/android/net/nsd/NsdManager
- Navigation Compose provides `currentBackStackEntryAsState()` as reactive state when the top destination changes. The tab bar now compares the active route directly rather than checking all ancestors: https://developer.android.com/reference/kotlin/androidx/navigation/compose/currentBackStackEntryAsState.composable
- None of these references proves mDNS discovery or direct ADB pairing works on a given OEM device. Automatic Direct flow remains `EXPERIMENTAL / UNVERIFIED-DEVICE` until device tests pass.


## 11. Phase 3 — Shizuku-style notification pairing (researched 2026-10-19)

### Upstream source observations

- Shizuku's main application, not Shizuku-API, owns the Wireless debugging pairing flow. The current upstream files inspected were [`AdbPairingTutorialActivity.kt`](https://github.com/RikkaApps/Shizuku/blob/master/manager/src/main/java/moe/shizuku/manager/adb/AdbPairingTutorialActivity.kt), [`AdbPairingService.kt`](https://github.com/RikkaApps/Shizuku/blob/master/manager/src/main/java/moe/shizuku/manager/adb/AdbPairingService.kt), and [`StartWirelessAdbViewHolder.kt`](https://github.com/RikkaApps/Shizuku/blob/master/manager/src/main/java/moe/shizuku/manager/home/StartWirelessAdbViewHolder.kt).
- `AdbPairingTutorialActivity` checks notification/channel availability and starts a foreground pairing service; its Developer Options button launches `Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS` and supplies the Wireless debugging preference key.
- `AdbPairingService` creates a dedicated notification channel, searches for the pairing service, updates the same notification when a pairing endpoint appears, and attaches `android.app.RemoteInput` to a notification action. The `PendingIntent` is mutable on Android 12+ so the system can attach the RemoteInput result. It reads the submitted value with `RemoteInput.getResultsFromIntent(intent)`, then invokes the actual `AdbPairingClient` with the discovered port and user-entered code. It reports protocol success/failure separately.
- `StartWirelessAdbViewHolder` selects the notification tutorial on ordinary displays and the in-app pairing dialog in a multi-display context. [Issue #2114](https://github.com/RikkaApps/Shizuku/issues/2114) documents an OEM/Meta Quest case where notification RemoteInput is suppressed and recommends a fallback in-app dialog. The issue's report is device-specific; it is not evidence that all OEMs suppress RemoteInput.
- The explicitly requested `AdbPairDialogFragment.kt` path returned 404 in the current upstream tree, so its implementation was not treated as verified from that guessed path. The current source and issue were used to confirm the fallback concept rather than copying a stale file path.
- Android's [ADB documentation](https://developer.android.com/tools/adb) describes `_adb-tls-pairing._tcp` / `_adb-tls-connect._tcp` service discovery and notes that mDNS discovery can fail on networks that do not support it. Pairing and connection endpoints are distinct and ports are dynamic.

### Phase 3 implementation in this repository

- Added `AdbPairingNotificationService`, started by the foreground user action, with an ongoing search notification and dynamic action availability only after a pairing endpoint is discovered. It submits the actual RemoteInput value through the existing `AdbConnectionManager.pairNow(host, port, code)` backed by `libadb-android`; it does not fake the code or substitute a TCP connect for pairing.
- Pairing notification uses a mutable `PendingIntent` on Android 12+ only for the RemoteInput action, immutable intents for ordinary actions, a dedicated channel, explicit stop/retry actions, a bounded 3-minute discovery timeout, stale-session rejection, and duplicate-submission protection. The code is not logged or persisted.
- `MainActivity` receives a package-scoped result broadcast and updates the Direct state. Pair success triggers discovery of the current connection endpoint; it does not imply shell or input delivery success. Existing in-app code entry remains available as a fallback when notifications are denied or the OEM does not expose inline RemoteInput.
- The service is declared as `connectedDevice` foreground work and declares the corresponding permission plus network-state permissions. Android/OEM background and notification restrictions can still prevent notification display or inline input; the in-app path is the supported fallback.
- `AdbServiceDiscovery` manages `WifiManager.MulticastLock` for Android 12 and earlier and Android 13 devices below T extension 7, then releases it with the discovery lifecycle. The platform reference says those versions need a multicast lock to receive mDNS packets. See [NsdManager API reference](https://developer.android.com/reference/android/net/nsd/NsdManager).
- Android 16 introduces an opt-in local-network restriction for compatibility testing. The current target SDK remains 36; activation checks/request `NEARBY_WIFI_DEVICES` only on API 36 when not already granted and warns if denied. This does not claim support for future target SDK 37's separate `ACCESS_LOCAL_NETWORK` requirement. See [Android local network permission guidance](https://developer.android.com/privacy-and-security/local-network-permission).
- Discovery run generations prevent late mDNS resolve callbacks from previous scans from overwriting a newer pairing/connection endpoint.

### License handling

Shizuku's main application source states Apache-2.0 for code files, while the separate Shizuku-API repository is MIT. This implementation follows the observed interaction pattern but does not copy Shizuku source code or assets. Existing `libadb-android` remains dual-licensed GPL-3.0-or-later OR Apache-2.0 and its full transitive license review remains a release blocker; see `THIRD_PARTY_NOTICES.md`.

### Validation status

Source-level implementation and pure policy/state tests were added. Static structure/YAML/XML checks can be run in this environment, but the Android Gradle build, RemoteInput behavior on an OEM notification shade, and a real successful pairing session require CI and a physical Android 11+ device. These are not marked as passed here.


## Phase 5 — Certificate interoperability and notification pairing (2026-10-20)

- Reviewed the upstream `libadb-android` certificate-generation example. It includes `SubjectKeyIdentifier`, `PrivateKeyUsage`, explicit `CertificateSubjectName`/`CertificateIssuerName` wrappers, and an RSA self-signed X.509 certificate. The previous project code omitted the two extensions and used direct `X500Name` values. The updated implementation follows the library's documented certificate shape and verifies certificate validity, self-signature, and private-key/public-key correspondence before persistence. Source: https://github.com/MuntashirAkon/libadb-android#configuring-adb
- Versioned the encrypted identity and Android Keystore wrapping alias so the new build does not reuse the older certificate. This intentionally means pairing may need to be repeated once after updating.
- Reviewed Shizuku's open-source `AdbPairingService.kt`: the service searches for the active Wireless debugging pairing port, offers notification `RemoteInput`, performs pairing on a background dispatcher, and surfaces the actual failure. The existing Zenda notification flow retains that interaction pattern with independent implementation and an in-app fallback. Source: https://github.com/RikkaApps/Shizuku/blob/master/manager/src/main/java/moe/shizuku/manager/adb/AdbPairingService.kt
- Runtime `CertificateException` remains unverified until a real Android device pairs successfully. If it persists, the new `ZendaPairing` Logcat entry captures the exception chain without intentionally logging the pairing code.
