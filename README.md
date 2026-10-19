# Zenda Touse Plus — InputBridge

**InputBridge** is an Android toolkit for exploring and validating privileged input workflows through Shizuku and Android Wireless Debugging. It combines a guided activation app, a small on-device helper, and a separate test target so that setup status can be distinguished from input events that are actually received.

The project is designed for phone-first development and repeatable GitHub Actions builds. It does not require a custom backend or root access for the documented Wireless Debugging route. ADB pairing, connection, helper startup, and delivered input are treated as separate checkpoints rather than being reported as one success state.

## Highlights

- **Guided activation:** A dedicated Direct flow guides the user through Android Developer Options and Wireless Debugging, then tracks discovery, code entry, pairing, connection, and helper readiness.
- **Shizuku integration:** Checks Shizuku availability and permission state, observes Binder lifecycle changes, and supports a separate helper-service probe.
- **Wireless ADB pairing:** Discovers Android's pairing service through network service discovery (mDNS/NSD), uses the active host and dynamic port, and submits the six-digit code displayed by Android through the real ADB pairing protocol.
- **Notification code entry:** When notifications are available and the device supports the interaction, the ongoing pairing notification exposes an inline code field after a pairing endpoint is discovered. An in-app code field remains available as a fallback for denied notification permission or OEM notification behavior.
- **Connection discovery:** Searches separately for the Wireless ADB connection endpoint. Pairing and connecting are different operations and are reported independently.
- **Restricted helper commands:** Direct ADB is used to locate the Lab APK and bootstrap the on-device helper. The helper path uses a small allowlist instead of exposing an arbitrary shell command interface.
- **Input verification target:** A separate app records key, touch, mouse, scroll, and pointer-capture events that actually reach it. This provides a concrete way to distinguish successful activation from successful input delivery.
- **Modern Android UI:** Jetpack Compose, Material 3, Navigation Compose, a persistent light/dark theme preference, and a bounded local history of predefined probe actions and outcomes.
- **GitHub Release delivery:** The `Android Release` workflow runs quality checks, builds signed Release APKs for both apps, verifies their signatures, generates SHA-256 checksums, and publishes the APKs to a GitHub Release.

## Applications

| App | Application ID | Purpose |
| --- | --- | --- |
| **InputBridge Lab** | `dev.zenda.inputbridge.lab` | Main UI, Shizuku status and helper probe, Wireless ADB pairing/connection, helper bootstrap, diagnostics, settings, and local probe history. |
| **InputBridge Test Target** | `dev.zenda.inputbridge.target` | Separate target application for displaying input events actually delivered to the app. |

Keeping the test target separate is intentional: seeing “paired,” receiving a shell response, or starting a helper is not proof that a target application received a key or touch event.

## How the Direct connection works

1. Open the Direct section and follow the prompt to enable Developer Options and Wireless Debugging.
2. Start pairing. InputBridge searches for the Android pairing endpoint instead of assuming a fixed port.
3. Enter the six digits currently displayed by Android Wireless Debugging. The app does not generate, guess, or retrieve this code.
4. InputBridge passes the code and discovered endpoint to its ADB pairing implementation and displays the actual result.
5. After pairing succeeds, the app separately looks for the ADB connection endpoint and attempts to connect.
6. The app locates its helper, starts it through the restricted bootstrap path, and reports helper readiness.
7. Use **InputBridge Test Target** to verify whether the expected key, touch, or pointer event arrived.

Pairing and connection rely on Android's Wireless Debugging implementation and local-network discovery. On some Wi-Fi networks, routers, or OEM builds, mDNS discovery or notification inline input can be restricted. In those cases, the in-app code field and available manual endpoint controls can be used where applicable. Android 16's local-network restrictions may also require the nearby-device permission path provided by the app.

## InputBridge Lab capabilities

### Shizuku

- Inspect whether Shizuku is available and whether permission is granted.
- React to Binder connect/disconnect events and refresh the displayed state.
- Bind a dedicated `UserService` for isolated helper probing.
- Report the helper UID and the outcome of bounded probe operations.

### Wireless Debugging and ADB

- Discover `_adb-tls-pairing._tcp` and `_adb-tls-connect._tcp` service endpoints dynamically.
- Use an ongoing notification with inline `RemoteInput` when supported, plus an in-app fallback.
- Bound pairing discovery and endpoint lifetime, expose retry/stop actions, reject stale sessions, and avoid duplicate code submissions.
- Keep pairing codes transient; do not store them in the database or log them as normal diagnostic data.
- Use ADB for setup and helper bootstrap, not as an unrestricted persistent input pipe.

### Helper and diagnostic UI

- Use a narrowly defined command allowlist for the documented helper bootstrap and basic identity/probe checks.
- Route helper communication through an authenticated loopback channel with a short-lived in-memory session token.
- Present Home, Shizuku, Direct, Diagnostics, and Settings destinations.
- Store a bounded history of predefined provider/action/outcome values and timestamps, not arbitrary command output or pairing codes.
- Persist the theme preference using DataStore and keep the recent-probe history in Room.

## InputBridge Test Target

The test target is intentionally a separate Android application. It provides a visible event log for input that reaches the app, including:

- Key down/up information.
- Touch and multi-pointer activity.
- Mouse movement and supported pointer axes.
- Mouse button and scroll state.
- Pointer-capture state.

The test target is a verification instrument, not proof by itself that every activation route can inject input into every Android app. A test passes only when the expected event is visible in the target's event list.

## Security and privacy principles

- **No custom backend:** pairing and helper communication stay on the device and its local network; there is no project-operated account service or analytics server.
- **No root requirement for the documented route:** Wireless Debugging/ADB or a separately installed and authorized Shizuku service is used instead of assuming root access.
- **No arbitrary shell UI:** privileged commands are constrained to known operations and a generated helper-bootstrap command.
- **Transient secrets:** pairing codes are not persisted. The local helper session token is held in memory for the active session.
- **Protected pairing result messages:** internal status broadcasts are package-scoped and registered with a not-exported flag and the app's signature-level permission.
- **Conservative history:** the Room history contains predefined probe metadata and timestamps, not pairing codes, private-key bytes, free-form commands, or captured user input from other apps.

These are design safeguards, not a claim of a completed third-party security audit. Review [`docs/THIRD_PARTY_NOTICES.md`](docs/THIRD_PARTY_NOTICES.md) before redistribution. The project does not promise compatibility with every OEM, third-party app, game, or anti-cheat system. Test only with your own device and apps you are authorized to test; do not attempt to bypass anti-cheat or platform security controls.

## Build and publish a GitHub Release

Release builds are published through one workflow: **`.github/workflows/android-ci.yml`** (`Android Release`). There is no separate debug-artifact workflow.

### 1. Configure signing secrets

In the GitHub repository, open **Settings → Secrets and variables → Actions** and create these repository secrets using the exact names shown below:

| Secret name | Value |
| --- | --- |
| `KEYSTORE_BASE64` | The complete JKS/keystore file encoded as one Base64 string, without wrapping or extra labels. |
| `KEY_ALIAS` | The alias of the signing key inside the keystore. |
| `STORE_PASSWORD` | The keystore password. |
| `KEY_PASSWORD` | The selected key's password. |

The workflow checks that all four secrets are present, validates the keystore and key, and avoids printing their values. Keep the signing passwords compatible with the configured Android/Java signing tools; this workflow explicitly requires ASCII characters for password input.

### 2. Run the release workflow

1. Open **GitHub → Actions → Android Release**.
2. Select **Run workflow** on the default branch.
3. The workflow calculates the next semantic version from existing Git tags, updates both app modules, validates the project, runs lint and unit tests, and builds both signed Release APKs.
4. It verifies each APK signature, writes `SHA256SUMS.txt`, and creates a GitHub Release with a version tag such as `v1.0.0`.
5. Open **Releases** to download the published files.

The release job fails rather than publishing if lint/tests, signing validation, APK collection, or signature verification fails. The repository's Gradle Wrapper is intentionally not relied upon by this workflow; it installs the pinned Gradle distribution directly and uses JDK 17 with Android SDK platform 36 and Build Tools 36.0.0.

## Repository layout

```text
.
├── .github/
│   ├── scripts/validate_android_project.py
│   └── workflows/android-ci.yml
├── lab/                  # InputBridge Lab app
├── input-target/         # Separate event-verification app
├── gradle/               # Version catalog and dependency versions
├── docs/                 # Architecture, research, test and license notes
└── README.md
```

Useful documentation:

- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) — module boundaries and helper architecture.
- [`docs/TEST_PROTOCOL.md`](docs/TEST_PROTOCOL.md) — repeatable device checks.
- [`docs/CAPABILITY_MATRIX.md`](docs/CAPABILITY_MATRIX.md) — capability evidence and platform constraints.
- [`docs/FEASIBILITY_RESULTS.md`](docs/FEASIBILITY_RESULTS.md) — evidence ledger for observed results.
- [`docs/RESEARCH.md`](docs/RESEARCH.md) — platform research and implementation decisions.
- [`docs/THIRD_PARTY_NOTICES.md`](docs/THIRD_PARTY_NOTICES.md) — dependency and licensing notes.
- [`docs/RISK_REGISTER.md`](docs/RISK_REGISTER.md) — known technical risks and mitigations.

## Planned enhancements

The current structure is intended to support future work without replacing the existing app or splitting the repository. Possible next additions include richer input-device mapping, editable control layouts, reusable profiles, profile import/export, improved device/OEM diagnostics, and more automated end-to-end verification. These are roadmap items; they should be added only with appropriate platform research, tests, and explicit evidence of what works.

## Verification expectations

Automated Release publication provides build, lint, unit-test, signing, and packaging checks for the exact commit it processes. Physical-device checks are still necessary for OEM-specific Wireless Debugging behavior and for proving event delivery: pairing success or helper readiness alone is not enough. Follow [`docs/TEST_PROTOCOL.md`](docs/TEST_PROTOCOL.md) and record device model, Android version/build, steps, and results in [`docs/FEASIBILITY_RESULTS.md`](docs/FEASIBILITY_RESULTS.md). Never add pairing codes, private keys, or signing secrets to logs or documentation.
