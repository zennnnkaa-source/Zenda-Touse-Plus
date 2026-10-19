# Zenda Touse Plus — InputBridge (continuing in the original Phase 1 project)

**Product prototype:** InputBridge Activation & Input Feasibility Lab  
**Status:** Phase 1 feasibility work remains open pending release-signing correction and real-device evidence. The AndroidX native-foundation and UI improvements are being added **in this same repository**, not as a separate Phase 2 project.

Phase 1 is a proof-of-concept, not the production mapper. It intentionally separates:

- **InputBridge Lab** (`dev.zenda.inputbridge.lab`) — Shizuku checks/helper probe and experimental direct Wireless debugging pairing.
- **InputBridge Test Target** (`dev.zenda.inputbridge.target`) — a separate app that records only key, touch, mouse and pointer-capture events actually delivered to it.

The permanent product name in this phase remains **InputBridge** (per the Phase 0 decision). The project root combines it with the umbrella project: **Zenda Touse Plus**.

## What the existing project contains

- Runtime Shizuku Binder/permission/UID checks, Binder lifecycle callbacks, and an isolated `UserService` probe.
- Experimental direct ADB pairing using `libadb-android`; pairing and connection ports are entered separately.
- Direct ADB is used only to locate the Lab APK and bootstrap `AdbBootstrapHelperMain` with `app_process`; subsequent `UID` / bounded tap and horizontal-swipe probes go through a short-lived authenticated loopback socket to the shared `LocalHelperServer` runtime used by the Shizuku path.
- Fixed-shell-command allowlist: only `id`, the exact Lab `pm path` lookup, and the strict generated helper-bootstrap command are allowed. The app does not provide a raw shell or use ADB as its ongoing input pipe.
- Per-install ADB identity whose encoded private-key bytes are encrypted with an AES-GCM key backed by Android Keystore before app-private persistence. The local helper session uses an ephemeral 256-bit token held only in memory. These are POC designs, not audited security implementations.
- Separate test target with bounded event log, key down/up fields, pointer capture control, touch pointers, mouse motion axes, button and scroll state.
- Unit tests for endpoint, pairing-code and fixed-command validation.
- GitHub Actions workflow to set up a pinned Gradle distribution + Android SDK, run lint/unit tests, assemble both debug APKs and upload them as an artifact.
- Native AndroidX application shell: Jetpack Compose + Material 3, AndroidX Navigation Compose, lifecycle-aware state collection, a persistent light/dark theme preference using DataStore, and a Room-backed bounded history containing only fixed probe labels/outcomes.

## Current validation snapshot

- Structural validator: **0 errors, 1 warning** (the missing Gradle Wrapper files; the workflow downloads pinned Gradle instead).
- 26 standalone Kotlin assertions for the pure input-policy validator (including bounded tap/swipe requests): **passed**. This is not an Android compile, Gradle test, or device test.
- Latest user-provided GitHub log reached Release APK packaging but failed while signing with `Password is not ASCII`; this is a signing configuration issue, not evidence of a clean Release build.
- New AndroidX UI/foundation edits: structural audit **PASS**; a Gradle compile has not been run for these latest edits.
- Shizuku, direct ADB and real event delivery: **unverified on physical device**.

## Required validation — do not skip

1. Push this folder to a GitHub repository and open **Actions → Android CI**. A green CI run is the first build/test evidence; no green run is included with this ZIP.
2. Download both debug APKs from the workflow artifact and install **both** on a test Android 11+ device.
3. Follow [`docs/TEST_PROTOCOL.md`](docs/TEST_PROTOCOL.md). Run the Shizuku flow and direct Wireless debugging flow independently. See [`docs/CAPABILITY_MATRIX.md`](docs/CAPABILITY_MATRIX.md) for version-specific evidence labels.
4. Confirm the test target's event list shows the expected tap/key/mouse event. A pairing success, ADB shell `id`, or helper UID alone is **not** proof that input injection works.
5. Record device model, Android build, time, steps, exact result and a redacted log in [`docs/FEASIBILITY_RESULTS.md`](docs/FEASIBILITY_RESULTS.md). Never record pairing codes or private keys.

## Build through GitHub Actions

This starter deliberately does not contain a guessed `gradle-wrapper.jar`. The available environment did not provide an intact wrapper binary or Android SDK, so the CI workflow installs Gradle **9.6.0** explicitly instead of depending on an unverified wrapper. This avoids presenting a partial wrapper as valid.

The CI workflow installs JDK 17, Android platform `android-36` and build-tools `36.0.0`, then runs:

```bash
gradle :lab:lint :input-target:lint \
  :lab:testDebugUnitTest :input-target:testDebugUnitTest \
  :lab:assembleDebug :input-target:assembleDebug
```

Outputs expected from a successful CI run:

- `lab/build/outputs/apk/debug/lab-debug.apk`
- `input-target/build/outputs/apk/debug/input-target-debug.apk`

These paths are expected destinations only; no APKs were produced in the environment used to author this ZIP.

## Folder map

```text
Zenda_Touse_Plus_Phase1/ (same repository as Phase 1)
├── .github/workflows/android-ci.yml
├── gradle/libs.versions.toml
├── lab/                  # Shizuku + direct ADB POC
├── input-target/         # real-input event capture target
├── docs/                 # current Phase 1 planning and evidence
└── docs/phase0-snapshot/ # original Phase 0 inputs preserved unchanged
```

## Important restrictions

- Option B is **Experimental** until pairing → connect → shell → target event delivery is demonstrated on a physical device.
- No game/anti-cheat compatibility is promised; only test with the dedicated target app or another target you are authorized to test. Do not attempt to bypass anti-cheat.
- No telemetry, analytics, or pairing-code logging is included intentionally.
- Do not publish or redistribute a production/release APK until the third-party dependency license and security review in `docs/THIRD_PARTY_NOTICES.md` is complete.
- **Do not move to Phase 2 automatically.** Phase 1 exit criteria remain blocked until a real input path reaches the test target and results are recorded.


## AndroidX foundation added in-place

- `ui/` owns the reusable Material 3 screens and theme.
- `data/preferences/UiPreferencesRepository.kt` persists only the user's theme preference with DataStore.
- `data/local/` contains Room-backed recent probe history. The database stores fixed provider/action/outcome enums and timestamps only; it never stores pairing codes, ADB keys, command output, or arbitrary user input.
- The navigation shell has Home, Shizuku, Direct, Diagnostics, and Settings destinations.

The foundation is intentionally layered into the existing `:lab` module. The `:input-target` module remains a separate app because separating the input receiver is necessary to test actual event delivery.
