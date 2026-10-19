# RISK_REGISTER.md — Phase 1 Update

Likelihood / Impact: Low · Medium · High. `Open` means not yet resolved. Phase 0 risk IDs remain tracked; full Phase 0 copy is in [`phase0-snapshot/RISK_REGISTER.md`](phase0-snapshot/RISK_REGISTER.md).

| ID | Risk | Likelihood | Impact | Mitigation / evidence expected | Status |
|---|---|---|---|---|---|
| R-01 | Shell-level injection may not deliver mouse-look/touch to target app or game | Medium | High | Use separate test target; only mark pass on observed events; do not extrapolate to games. | OPEN / UNVERIFIED-DEVICE |
| R-02 | Direct Wireless debugging library may fail pairing/connect on OEM/OS combination | High | High | Keep Option B experimental; separate port fields; physical-device test. | OPEN / NOT RUN |
| R-03 | `libadb-android` has not been security audited and mentions an LGPL dependency | Medium | High | Keep behind wrapper, no arbitrary shell UI, inspect dependency tree/licenses; no release before review. | OPEN |
| R-04 | Shizuku server/helper may die after reboot or binder loss | High | Medium | Binder listeners; explicit not-running state; manual rebind/restart guidance. | OPEN / NOT RUN |
| R-05 | Shizuku API artifact version may be stale for newest OS | Medium | Medium | Dependency resolution plus real OS check; pinning is provisional. | OPEN |
| R-06 | ADB private identity leaks or is persisted insecurely | Low | High | Generate per-install identity; AES-GCM at rest with Android Keystore; never log key material; audit crypto/storage before release. | OPEN / NOT AUDITED |
| R-07 | Pairing code leaks via logs, analytics, files or UI restoration | Low | High | Never write code to log/file/preferences; clear the text field immediately; audit crash/ADB library logs. | PARTIAL MITIGATION / AUDIT OPEN |
| R-08 | Wrong host/port used because pairing and connect ports are confused | High | Medium | Separate fields and validation; user-facing errors identify the correct port. | MITIGATED IN UI DESIGN / DEVICE TEST OPEN |
| R-09 | Broad command execution can be abused or misused | Medium | High | No arbitrary command field; allow only `id`, exact Lab `pm path`, and a strict generated app_process bootstrap; helper protocol allows `UID`, numeric `TAP`, and `STOP`. | MITIGATED IN WRAPPER / SECURITY REVIEW OPEN |
| R-10 | Build fails because toolchain/dependency coordinates do not resolve together | Medium | Medium | Pinned version catalog, static validator, GitHub Actions; never claim green before run. | OPEN / CI NOT RUN |
| R-11 | No local Android SDK / Gradle wrapper available in authoring environment | High | Medium | CI explicitly installs Gradle 9.6.0 and SDK platform/build-tools; document wrapper omission. | KNOWN ENVIRONMENT BLOCKER |
| R-12 | Target receives no real event despite command success | Medium | High | Test app's real event log is the acceptance signal. A shell exit or helper UID is not enough. | OPEN / UNVERIFIED |
| R-13 | Android Developer Verification or store policy affects distribution | Medium–High | High | Not a release build; verify current program, publisher identity and store policy before distribution. | OPEN / RELEASE ONLY |
| R-14 | Game anti-cheat detects injected inputs or violates a game's rules | Medium | High | No stealth/evasion; test only a dedicated target/authorized app; avoid compatibility promises. | OPEN / OUT OF SCOPE |
| R-15 | UI/status wording falsely signals Activation success | Low | High | Separate binder, permission, pair, connect, shell and delivered-event states. | MITIGATED IN DESIGN / REVIEW OPEN |
| R-16 | Device key wrapping fails after Android Keystore key loss/invalidation | Medium | Medium | Current loader discards only the damaged app identity on read/decrypt failure and regenerates; test restart/key-loss paths. | OPEN / NOT RUN |

## Top blockers for Phase 1 closure

1. CI has not yet produced build/test evidence.
2. No physical Android 11+ device test has been executed from this environment.
3. Neither Shizuku nor direct ADB bootstrap/helper has proven that the separate test target received an event.
4. License/security review for the direct ADB dependency is not complete.

| R-17 | Wireless-debugging mDNS endpoints are not discoverable on some OEMs/networks | High | High | Keep retry and manual endpoint fallback; physical-device test both pairing and connection service discovery separately. | OPEN / DEVICE TEST NEEDED |
| R-18 | Notification permission denied prevents the system waiting notification | Medium | Medium | Continue showing the in-app waiting state and expose a clear Settings return path; never claim a notification was shown when Android denied permission. | MITIGATED IN UI / DEVICE TEST NEEDED |
