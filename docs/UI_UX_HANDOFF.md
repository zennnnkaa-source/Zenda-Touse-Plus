# UI/UX Handoff — Zenda Touse Plus / InputBridge

This UI update remains in the existing Phase 1 repository. It does not create a separate Phase 2 project.

## Current UI foundation

- Jetpack Compose + Material 3 theme with light/dark color schemes.
- Navigation Compose destinations: Home, Shizuku, Direct, Diagnostics, Settings.
- DataStore-backed local theme preference.
- Room-backed bounded probe history (latest 100 rows); it stores predefined provider/action/outcome values and timestamps only.
- Pairing code is held as transient UI state and is not persisted by `rememberSaveable`.
- Shizuku and Direct ADB remain separately labeled; input delivery must not be called verified solely because pairing/helper startup succeeds.

## Validation status

The source package and Gradle configuration were structurally inspected. The current authoring environment does not have an Android SDK or Gradle executable, so an Android build has not been run here. GitHub Actions must pass lint, unit tests, and debug assembly before this UI is considered build-verified. A physical-device test must still demonstrate that the target app actually receives and records input.

## Repository workflows

- `android-ci.yml`: release-only workflow, preserving existing signing secret names and publishing a tagged release on manual dispatch.
- `phase1-build-test.yml`: unsigned debug CI for push, pull request, or manual dispatch. It installs API 37 and Build Tools 36.0.0 to match AGP 9.4 / Compose.
