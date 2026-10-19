# Zenda Touse Plus — InputBridge (continuing in the original Phase 1 project)

**Product prototype:** InputBridge Activation & Input Feasibility Lab  
**Status:** Continuing the user's requested Phase 2 Native Project Foundation & CI work in this same repository. The latest source revision still needs a fresh GitHub Actions run. The user reports the current app flows are usable; end-to-end input delivery remains explicitly unverified until the separate Test Target records an event.

Phase 1 is a proof-of-concept, not the production mapper. It intentionally separates:

- **InputBridge Lab** (`dev.zenda.inputbridge.lab`) — Shizuku checks/helper probe and experimental direct Wireless debugging pairing.
- **InputBridge Test Target** (`dev.zenda.inputbridge.target`) — a separate app that records only key, touch, mouse and pointer-capture events actually delivered to it.

The permanent product name in this phase remains **InputBridge** (per the Phase 0 decision). The project root combines it with the umbrella project: **Zenda Touse Plus**.

## What the existing project contains

- Runtime Shizuku Binder/permission/UID checks, Binder lifecycle callbacks, and an isolated `UserService` probe.
- Experimental direct ADB pairing using `libadb-android`; normal flow discovers the separate pairing and connection ports through Android NSD, with an expandable manual endpoint fallback for devices/networks that do not advertise mDNS.
- Direct ADB is used only to locate the Lab APK and bootstrap `AdbBootstrapHelperMain` with `app_process`; subsequent `UID` / bounded tap and horizontal-swipe probes go through a short-lived authenticated loopback socket to the shared `LocalHelperServer` runtime used by the Shizuku path.
- Fixed-shell-command allowlist: only `id`, the exact Lab `pm path` lookup, and the strict generated helper-bootstrap command are allowed. The app does not provide a raw shell or use ADB as its ongoing input pipe.
- Per-install ADB identity whose encoded private-key bytes are encrypted with an AES-GCM key backed by Android Keystore before app-private persistence. The local helper session uses an ephemeral 256-bit token held only in memory. These are POC designs, not audited security implementations.
- Separate test target with bounded event log, key down/up fields, pointer capture control, touch pointers, mouse motion axes, button and scroll state.
- Unit tests for endpoint, pairing-code and fixed-command validation.
- GitHub Actions workflow to set up a pinned Gradle distribution + Android SDK, run lint/unit tests, assemble both debug APKs and upload them as an artifact.
- Native AndroidX application shell: Jetpack Compose + Material 3, AndroidX Navigation Compose, lifecycle-aware state collection, a persistent light/dark theme preference using DataStore, and a Room-backed bounded history containing only fixed probe labels/outcomes.
- Bottom-tab selection is bound to the exact active Navigation Compose destination; the Direct activation path can be opened from the bottom tab or Home shortcut.
- Direct activation now separates UI states for waiting for Settings return, waiting for mDNS details, entering the code, pairing, connecting, and helper-ready. Successful pairing automatically attempts the current connection endpoint and helper bootstrap; this remains experimental until re-tested on device.

## Current validation snapshot

- Structural validator: **0 errors, 1 warning** (the missing Gradle Wrapper files; the workflow downloads pinned Gradle instead).
- 26 standalone Kotlin assertions for the pure input-policy validator (including bounded tap/swipe requests): **passed**. This is not an Android compile, Gradle test, or device test.
- Latest user-provided GitHub log reached Release APK packaging but failed while signing with `Password is not ASCII`; this is a signing configuration issue, not evidence of a clean Release build.
- Latest navigation/Direct flow edit: structural audit rerun with 0 errors and one missing-Gradle-Wrapper warning; a Gradle/Android compile has not been run for these newest edits.
- Shizuku, Direct ADB mDNS discovery, automatic connect/helper bootstrap and target event delivery: **not device-verified for this revision**.

## Required validation — do not skip

1. Apply this ZIP over the same GitHub repository and open **Actions → Phase 2 Native Foundation CI**. A green CI run is the first build/test evidence for this revision; no green run is included with this ZIP.
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
- This is still the original Phase 1 repository being advanced in place per the user's explicit Phase 2 request; no separate Phase 2 project is created. Phase 2 is not marked PASS until the current revision passes CI. The `UNVERIFIED` input status is intentional: shell UID/helper response is not proof of target event delivery.


## AndroidX foundation added in-place

- `ui/` owns the reusable Material 3 screens and theme.
- `data/preferences/UiPreferencesRepository.kt` persists only the user's theme preference with DataStore.
- `data/local/` contains Room-backed recent probe history. The database stores fixed provider/action/outcome enums and timestamps only; it never stores pairing codes, ADB keys, command output, or arbitrary user input.
- The navigation shell has Home, Shizuku, Direct, Diagnostics, and Settings destinations.

The foundation is intentionally layered into the existing `:lab` module. The `:input-target` module remains a separate app because separating the input receiver is necessary to test actual event delivery.
