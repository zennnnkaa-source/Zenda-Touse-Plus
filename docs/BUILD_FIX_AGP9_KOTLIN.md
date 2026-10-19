# Build Fix — AGP 9 Built-in Kotlin and Compose SDK 37

## Diagnosis from the 2026-10-19 GitHub Actions log

The build stops while applying `org.jetbrains.kotlin.android` in `input-target/build.gradle.kts` line 1. The exception attempts to cast AGP's new `ApplicationExtensionImpl` to the legacy `BaseExtension`. The log also shows `android.builtInKotlin=false` is deprecated. This is an AGP 9 Kotlin-plugin / new-DSL incompatibility, not a keystore or Gradle-wrapper failure.

## Changes in this hotfix

- Removed `org.jetbrains.kotlin.android` from the root plugin declarations and both Android modules.
- Removed its version-catalog plugin alias.
- Enabled the AGP built-in Kotlin route by removing `android.builtInKotlin=false`.
- Migrated both modules from `android.kotlinOptions` to `kotlin.compilerOptions` with JVM 17.
- Set `compileSdk = 37` because Compose BOM `2026.08.00` includes Compose 1.12, which requires compile SDK 37.
- Updated the bundled `android-ci.yml` to install `platforms;android-37`; Build Tools remain `36.0.0`, the default specified for AGP 9.4.0.

## Important: update the active signed-release workflow too

The supplied CI log is from a signed-release workflow (`assembleRelease` and signing secrets), while the earlier project ZIP contains a debug-only `android-ci.yml`. The active release workflow was not present in the ZIP used to prepare this patch, so this archive deliberately does not overwrite that workflow.

In the release workflow that produced the attached log, change its SDK install line from:

```bash
sdkmanager "platforms;android-36" "build-tools;36.0.0"
```

to:

```bash
sdkmanager "platforms;android-37" "build-tools;36.0.0"
```

Keep the existing keystore/secrets/signing steps unchanged.

## Verification status

This is a targeted configuration fix derived from the attached log and official Android build documentation. It has **not** been run through a complete Android/Gradle build in this environment. The next GitHub Actions run must confirm compilation, lint, unit tests and both release APKs. Any subsequent compiler/dependency error should be treated as a new finding, not as evidence the original diagnosis was incorrect.

References:
- https://developer.android.com/build/migrate-to-built-in-kotlin
- https://developer.android.com/build/releases/agp-9-4-0-release-notes
- https://developer.android.com/blog/posts/what-s-new-in-the-jetpack-compose-august-26-release
