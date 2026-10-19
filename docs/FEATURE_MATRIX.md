# FEATURE_MATRIX.md — Phase 1 Evidence Status

**As of:** 2026-10-19. `SOURCE ONLY` means files exist but have not been compiled. Nothing in this table is reported as device-verified.

| Area | Feature in this package | Status | Evidence needed to upgrade status |
|---|---|---|---|
| Shizuku | Binder check / binder lifecycle listeners | SOURCE ONLY | Build + device test across service stop/restart. |
| Shizuku | Request Shizuku permission and report actual result | SOURCE ONLY | Device permission grant and deny tests. |
| Shizuku | Bind helper; report process UID | SOURCE ONLY | Successful bind callback and helper UID recorded on device. |
| Shizuku | Start shared helper and issue bounded `input tap` / `input swipe` probes through authenticated loopback IPC | SOURCE ONLY / UNVERIFIED-DEVICE | Build + device execution, helper UID record, and target event log confirms delivered touch; record Android build/OEM. |
| Direct ADB | Pairing with current host + pairing port + six-digit code | EXPERIMENTAL / SOURCE ONLY | CI dependency resolve + physical-device pair pass/fail, with code redacted. |
| Direct ADB | Connect using separate current connect port | EXPERIMENTAL / SOURCE ONLY | Device TLS ADB connect succeeds after pairing. |
| Direct ADB | Shell identity (`id`) | EXPERIMENTAL / SOURCE ONLY | Real shell output captured after connection. |
| Direct ADB | Bootstrap shared helper + one-tap request | EXPERIMENTAL / UNVERIFIED-DEVICE | Helper UID and target Activity records actual touch; bootstrap response alone is insufficient. |
| Test target | Key down/up metadata | SOURCE ONLY | Physical keyboard test on device. |
| Test target | Touch/multi-touch events | SOURCE ONLY | Screen touch and multi-finger behavior tested on device. |
| Test target | Mouse buttons, relative axes and scroll | SOURCE ONLY | USB/Bluetooth mouse test; identify event source and OS build. |
| Test target | Pointer capture acquire/release | SOURCE ONLY | Physical mouse/touchpad test across tested Android versions. |
| Automated logic | Port/code/command validation unit tests | SOURCE ONLY | GitHub Actions test task passes. |
| CI | lint + unit tests + assemble two debug APKs | WORKFLOW CREATED / NOT RUN | Successful GitHub Actions run; record run URL and commit. |
| Control Studio / game profile mapping | Not in Phase 1 scope | NOT STARTED | Planned for later phases only after input injection gate passes. |
| Third-party game support / anti-cheat compatibility | Not tested and not promised | UNKNOWN / RISKY | Requires authorized per-game testing and must not include anti-cheat bypass. |

## Phase 1 acceptance criteria

- [ ] Gradle dependency resolution and lint pass in GitHub Actions.
- [ ] Unit tests pass in GitHub Actions.
- [ ] Both debug APKs assemble and are downloadable as an Actions artifact.
- [ ] Shizuku state and UID are verified on at least one physical device.
- [ ] Direct ADB pairing/connect/shell has explicit pass/fail evidence on at least one physical device, or Option B is marked unavailable on that test setup.
- [ ] At least one activation path sends a test input that appears in the separate target's real event log.
- [ ] `docs/FEASIBILITY_RESULTS.md` records device model, build/version, steps, result, and redacted logs.
- [ ] No secrets or pairing codes appear in report/logs.

**Current gate:** not passed; the above device/build checks are still not run.
