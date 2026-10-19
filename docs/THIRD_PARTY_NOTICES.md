# THIRD_PARTY_NOTICES.md — Phase 1 POC

This prototype uses third-party Android libraries. The items below document provenance and known review items, not a final legal opinion.

| Dependency | Version / source | Upstream license / notes | Phase 1 handling |
|---|---|---|---|
| Shizuku API | `dev.rikka.shizuku:api:13.1.5`; [official source](https://github.com/RikkaApps/Shizuku-API) | Upstream Shizuku-API repository uses MIT licensing. Verify the exact resolved artifact/license at build time. | Dependency only; no Shizuku source/assets copied. |
| Shizuku Provider | `dev.rikka.shizuku:provider:13.1.5`; [official source](https://github.com/RikkaApps/Shizuku-API) | Same upstream project; re-check resolved artifact metadata before release. | Required integration provider in Lab manifest. |
| libadb-android | `com.github.MuntashirAkon:libadb-android:3.1.1`; [upstream](https://github.com/MuntashirAkon/libadb-android) | Dual-licensed GPL-3.0-or-later OR Apache-2.0. The README states that it has not been security-audited and calls out an LGPL dependency. Transitive dependency licenses must be inventoried from the resolved Gradle graph. | Experimental direct pairing/connect POC only. No upstream code copied. No release until security/license review. |
| sun-security-android | `com.github.MuntashirAkon:sun-security-android:1.1`; documented by libadb README | Used for X.509 certificate construction in upstream example; verify repository license and transitive inventory before redistribution. | Imported API dependency only. |
| Conscrypt Android | `org.conscrypt:conscrypt-android:2.5.3`; [upstream](https://github.com/google/conscrypt) | Upstream project is Apache-2.0; check actual resolved artifact and notice requirements. | Included as a TLS-provider candidate to evaluate; the Lab does not explicitly register it in source. Confirm whether the library/default Android provider suffices or wire it only if evidence requires. |
| Compose BOM / Activity Compose / Material 3 | Catalog pins Compose BOM `2026.04.01`, Activity Compose `1.12.4`, Navigation Compose `2.9.8`, and Lifecycle `2.10.0`; [Compose BOM](https://developer.android.com/develop/ui/compose/bom), [AndroidX versions](https://developer.android.com/jetpack/androidx/versions) | AndroidX dependencies are consumed as Maven artifacts; include dependency license notices as required for any release. | Native UI dependencies only. |
| JUnit | `junit:junit:4.13.2` | JUnit license/notice should be checked as part of dependency reporting. | Test scope only. |

## Release gate

Before publishing or distributing a non-internal release:

1. Run Gradle dependency insight/report and collect **all** direct and transitive licenses with resolved versions.
2. Review the dual-license selection and LGPL obligations in the direct ADB route with an appropriate reviewer. “Apache-2.0 option” does not make dependencies automatically Apache-2.0.
3. Review pairing, TLS, key generation, Keystore wrapping, error/log handling and command allowlisting; upstream explicitly disclaims a security audit.
4. Add the required full license/notice texts for every resolved dependency and verify that any modifications or linking choices comply.
5. Decide whether Option B is acceptable for distribution; if not, exclude it from release builds or choose a verified replacement.

The current ZIP is a feasibility POC. It is not a public release and does not include signing keys or production credentials.


## Phase 3 upstream behavior reference

The notification UX was researched against RikkaApps/Shizuku `AdbPairingTutorialActivity.kt`, `AdbPairingService.kt`, and `StartWirelessAdbViewHolder.kt`. No Shizuku source code or artwork was copied into this project. Shizuku main-app code files are Apache-2.0; the separate Shizuku-API project is MIT. Links and the license distinction are recorded in `RESEARCH.md`. Existing `libadb-android` is still dual-licensed GPL-3.0-or-later OR Apache-2.0; a complete dependency/license and security review is required before public release.
