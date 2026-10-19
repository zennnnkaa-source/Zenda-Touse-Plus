# Zenda Touse Plus — Phase 1

**Product prototype:** InputBridge Activation & Input Feasibility Lab  
**Status:** source and CI scaffold prepared; Phase 1 is **not passed yet** because no Android build or real-device test has been executed in the authoring environment.

Phase 1 is a proof-of-concept, not the production mapper. It intentionally separates:

- **InputBridge Lab** (`dev.zenda.inputbridge.lab`) — Shizuku checks/helper probe and experimental direct Wireless debugging pairing.
- **InputBridge Test Target** (`dev.zenda.inputbridge.target`) — a separate app that records only key, touch, mouse and pointer-capture events actually delivered to it.

The permanent product name in this phase remains **InputBridge** (per the Phase 0 decision). The project root combines it with the umbrella project: **Zenda Touse Plus**.

## What this Phase 1 POC currently contains

- Runtime Shizuku Binder/permission/UID checks, Binder lifecycle callbacks, and an isolated `UserService` probe.
- Experimental direct ADB pairing using `libadb-android`; pairing and connection ports are entered separately.
- Direct ADB is used only to locate the Lab APK and bootstrap `AdbBootstrapHelperMain` with `app_process`; subsequent `UID` / bounded tap and horizontal-swipe probes go through a short-lived authenticated loopback socket to the shared `LocalHelperServer` runtime used by the Shizuku path.
- Fixed-shell-command allowlist: only `id`, the exact Lab `pm path` lookup, and the strict generated helper-bootstrap command are allowed. The app does not provide a raw shell or use ADB as its ongoing input pipe.
- Per-install ADB identity whose encoded private-key bytes are encrypted with an AES-GCM key backed by Android Keystore before app-private persistence. The local helper session uses an ephemeral 256-bit token held only in memory. These are POC designs, not audited security implementations.
- Separate test target with bounded event log, key down/up fields, pointer capture control, touch pointers, mouse motion axes, button and scroll state.
- Unit tests for endpoint, pairing-code and fixed-command validation.
- GitHub Actions workflow to set up a pinned Gradle distribution + Android SDK, run lint/unit tests, assemble both debug APKs and upload them as an artifact.

## Current validation snapshot

- Structural validator: **0 errors, 1 warning** (the missing Gradle Wrapper files; the workflow downloads pinned Gradle instead).
- 26 standalone Kotlin assertions for the pure input-policy validator (including bounded tap/swipe requests): **passed**. This is not an Android compile, Gradle test, or device test.
- Android build, lint, Gradle/JUnit tests, APK assembly, Shizuku, direct ADB and real event delivery: **not run / unverified**.

## Required validation — do not skip

1. Push this folder to a GitHub repository and open **Actions → Android CI**. A green CI run is the first build/test evidence; no green run is included with this ZIP.
2. Download both debug APKs from the workflow artifact and install **both** on a test Android 11+ device.
3. Follow [`docs/TEST_PROTOCOL.md`](docs/TEST_PROTOCOL.md). Run the Shizuku flow and direct Wireless debugging flow independently. See [`docs/CAPABILITY_MATRIX.md`](docs/CAPABILITY_MATRIX.md) for version-specific evidence labels.
4. Confirm the test target's event list shows the expected tap/key/mouse event. A pairing success, ADB shell `id`, or helper UID alone is **not** proof that input injection works.
5. Record device model, Android build, time, steps, exact result and a redacted log in [`docs/FEASIBILITY_RESULTS.md`](docs/FEASIBILITY_RESULTS.md). Never record pairing codes or private keys.

## Build through GitHub Actions

This starter deliberately does not contain a guessed `gradle-wrapper.jar`. The available environment did not provide an intact wrapper binary or Android SDK, so the CI workflow installs Gradle **9.6.0** explicitly instead of depending on an unverified wrapper. This avoids presenting a partial wrapper as valid.

The workflow installs JDK 17, Android platform `android-36` and build-tools `36.0.0`, then runs:

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
Zenda_Touse_Plus_Phase1/
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
