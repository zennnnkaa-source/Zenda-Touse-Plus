# FEATURE_MATRIX.md — Feature Evidence Status

**As of:** 2026-10-20. `SOURCE ONLY` means files exist but have not been compiled. Nothing in this table is reported as device-verified.

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
| CI / Release | lint + unit tests + two signed Release APKs + signature verification + GitHub Release upload | WORKFLOW UPDATED / NOT RUN ON THIS REVISION | Successful Android Release workflow; record run URL, tag and commit. |
| Control Studio / game profile mapping | Not in Phase 1 scope | NOT STARTED | Planned for later phases only after input injection gate passes. |
| Third-party game support / anti-cheat compatibility | Not tested and not promised | UNKNOWN / RISKY | Requires authorized per-game testing and must not include anti-cheat bypass. |

## Phase 1 acceptance criteria

- [ ] Gradle dependency resolution and lint pass in GitHub Actions.
- [ ] Unit tests pass in GitHub Actions.
- [ ] Both signed Release APKs assemble, their signatures verify, and the files are published to GitHub Releases.
- [ ] Shizuku state and UID are verified on at least one physical device.
- [ ] Direct ADB pairing/connect/shell has explicit pass/fail evidence on at least one physical device, or Option B is marked unavailable on that test setup.
- [ ] At least one activation path sends a test input that appears in the separate target's real event log.
- [ ] `docs/FEASIBILITY_RESULTS.md` records device model, build/version, steps, result, and redacted logs.
- [ ] No secrets or pairing codes appear in report/logs.

**Current gate:** not passed; the above device/build checks are still not run.


## Phase 2 in-place UI updates (2026-10-19)

| Feature | Source implementation | Status | Verification needed |
|---|---|---|---|
| Bottom-tab selected indicator follows active page | `currentBackStackEntryAsState()` and exact route policy | SOURCE IMPLEMENTED; standalone route policy compiles | GitHub CI + device navigation regression |
| Direct activation launches Developer options | `Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS` with generic Settings fallback | SOURCE IMPLEMENTED | Physical device/OEM check |
| Pairing and connection port auto-discovery | `NsdManager` for separate `_adb-tls-pairing._tcp.` and `_adb-tls-connect._tcp.` services | EXPERIMENTAL / DEVICE UNVERIFIED | Physical device, Wireless debugging dialog active |
| Waiting notification | Notification permission + notification channel; in-app state retained if denied | SOURCE IMPLEMENTED | Android 13+ permission grant/deny test |
| Six-digit code entry then automatic Pair → Connect → helper | Source path implemented; manual endpoint remains a fallback | EXPERIMENTAL / DEVICE UNVERIFIED | Device protocol and helper test, followed by target event observation |


## Device discovery & diagnostics core (2026-10-20)

| Area | Feature in this package | Status | Evidence needed to upgrade status |
|---|---|---|---|
| Device registry | Public `InputDevice` enumeration with source flags, vendor/product metadata, virtual flag, and motion ranges | SOURCE IMPLEMENTED / BUILD NOT RUN | Android Release CI + attach/detach tests on a physical device |
| Device lifecycle | `InputManager.InputDeviceListener` refreshes add/change/remove events while the Activity is started | SOURCE IMPLEMENTED | Physical attach/detach and Activity stop/start checks |
| Key diagnostics | Real Activity-delivered key down/repeat/up events and per-device pressed-key state | SOURCE IMPLEMENTED / UNIT TESTS ADDED | CI tests + hardware keyboard/controller run |
| Motion diagnostics | Real Activity-delivered motion action/source/button state and bounded axis samples | SOURCE IMPLEMENTED / DEVICE UNVERIFIED | CI + mouse/controller/touchpad run with event-source evidence |
| Event history | Bounded 200-entry in-memory log; UI renders at most 30 latest rows | SOURCE IMPLEMENTED / UNIT TESTS ADDED | CI and stress test on device |
| Pairing-code privacy | Key recording paused while the six-digit pairing-code field has focus | SOURCE IMPLEMENTED | UI focus regression on Android device |
| Axis utilities | Centered/unipolar normalization, dead zone, inversion, sensitivity and pair transform | SOURCE IMPLEMENTED / POLICY SMOKE PASSED | JUnit task in Android Release CI |
| System-wide input capture | Events while another app is foreground | NOT PROVIDED BY THIS DIAGNOSTICS CORE | Separate privileged backend and authorized target verification |
