# Phase Status — Zenda Touse Plus / InputBridge

**As of:** 2026-10-19

## Status summary

| Field | Status |
|---|---|
| Umbrella project | Zenda Touse Plus |
| Product prototype | InputBridge Activation & Input Feasibility Lab |
| Repository strategy | Continue in the original Phase 1 repository; no separate Phase 2 project |
| Phase 0 | **PASS (documents only)**; original documents preserved under `docs/phase0-snapshot/` |
| Phase 1 feasibility gate | **OPEN / NOT PASSED** — latest release log reached APK packaging but signing failed with `Password is not ASCII`; physical-device target event is still undocumented |
| In-place native foundation/UI work | **IMPLEMENTED IN SOURCE, NOT BUILD-VERIFIED** — AndroidX Navigation Compose, Material 3 app shell, DataStore theme setting, Room bounded probe history |
| Structural project audit | **PASS: 0 errors, 1 warning** — the remaining warning is missing Gradle Wrapper files |
| Latest UI/foundation Gradle build | **NOT RUN** in this authoring environment; no Android SDK or Gradle executable is installed locally |
| GitHub Actions CI for latest changes | **PENDING** — must run after copying/committing the listed file updates |
| Release signing | **BLOCKED** until the existing signing password/keystore setup is corrected; do not replace the signing key casually |
| Shizuku → target input | **UNVERIFIED-DEVICE** |
| Direct ADB pairing/connect/helper | **EXPERIMENTAL / PARTIALLY UNVERIFIED** |
| Input delivery to target | **UNVERIFIED**; no PASS is inferred from helper response or shell UID |

## What changed in-place

- Extracted the Compose interface out of `MainActivity.kt` into `ui/LabScreen.kt`.
- Added a consistent Material 3 light/dark theme with semantic colors.
- Added AndroidX Navigation Compose destinations: Home, Shizuku, Direct, Diagnostics, and Settings.
- Added DataStore-backed local dark-theme preference.
- Added Room-backed bounded event history. It stores only predefined provider/action/outcome values and timestamps; never pairing codes, key material, or raw command output.
- Kept Shizuku and Direct ADB controls in the existing `:lab` module. The separate `:input-target` application remains separate by design for input-delivery tests.
- Kept the original Phase 0 snapshot unchanged.

## Exact remaining gates

1. Run GitHub Actions on the newest source and resolve compile/lint/test errors. Structural inspection is not compilation evidence.
2. Fix Release signing with the existing keystore and matching ASCII-compatible passwords, without leaking any secret into logs.
3. Install both Debug APKs on an Android 11+ physical device.
4. Test Shizuku and Direct ADB independently.
5. Confirm the test target visibly records the expected event and document device model, Android build, date, steps, and a redacted result in `docs/FEASIBILITY_RESULTS.md`.

## Required reports

- `RESEARCH.md` — sources and technical unknowns.
- `DECISIONS.md` — architecture decisions and reversibility.
- `FEATURE_MATRIX.md` and `CAPABILITY_MATRIX.md` — evidence labels.
- `FEASIBILITY_RESULTS.md` — real test results only; unrun tests remain unverified.
- `TEST_PROTOCOL.md` — repeatable device test.
- `THIRD_PARTY_NOTICES.md` — dependency license/security review.

## Phase policy

The user explicitly requested that AndroidX foundation/UI work continue within the existing Phase 1 project. That in-place work does not convert the Phase 1 evidence gate to PASS. Do not label Input injection as verified until the target app visibly receives and logs the event on a physical device.
