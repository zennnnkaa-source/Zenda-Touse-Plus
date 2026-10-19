# UI/UX Handoff — Zenda Touse Plus / InputBridge

This UI work remains in the existing Zenda Touse Plus repository. The application is developed in place; it is not maintained as a separate prototype project.

## Current UI foundation

- Jetpack Compose + Material 3 theme with light/dark color schemes.
- Navigation Compose destinations: Home, Shizuku, Direct, Diagnostics, Settings.
- DataStore-backed local theme preference.
- Room-backed bounded probe history (latest 100 rows); it stores predefined provider/action/outcome values and timestamps only.
- Pairing code is held as transient UI state and is not persisted by `rememberSaveable`.
- Shizuku and Direct ADB remain separately labeled; input delivery must not be called verified solely because pairing/helper startup succeeds.

## Validation status

The source package and Gradle configuration were structurally inspected. The current authoring environment does not have an Android SDK or Gradle executable, so an Android build has not been run here. The Android Release GitHub Actions workflow must pass lint, unit tests, signed Release assembly, and signature verification before the current source is considered build-verified. A physical-device test must still demonstrate that the target app actually receives and records input.

## Repository workflows

- `android-ci.yml`: the single release workflow. Manual dispatch calculates the next version, validates `KEYSTORE_BASE64`, `KEY_ALIAS`, `STORE_PASSWORD`, and `KEY_PASSWORD`, runs lint and unit tests, builds and verifies both signed Release APKs, and publishes them to GitHub Releases. It uses JDK 17, Gradle 9.6.0, Android SDK 36, and Build Tools 36.0.0.
