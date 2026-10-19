# Build fix — SDK 36 baseline

Date: 2026-10-19

The submitted GitHub Actions log failed while configuring `:input-target`, before compile or tests:

`Failed to apply plugin 'org.jetbrains.kotlin.android'.`
`Cannot add extension with name 'kotlin', as there is an extension already registered with that name.`

## Changes applied

- Removed the redundant `org.jetbrains.kotlin.android` plugin from `input-target`; AGP 9 supplies built-in Kotlin.
- Replaced the legacy `android.kotlinOptions` block with `kotlin.compilerOptions`.
- Set `lab` `compileSdk` to 36; `input-target` was already set to 36.
- Changed both CI workflows to install `platforms;android-36`.
- Downgraded Compose BOM to `2026.04.01`, Navigation Compose to `2.9.8`, and Lifecycle to `2.10.0` to keep dependencies aligned with the requested API 36 baseline. The Compose BOM `2026.08.00` / Compose 1.12 and Lifecycle 2.11 require API 37.
- Kept `targetSdk = 36`, Build Tools `36.0.0`, AGP `9.4.0`, Gradle `9.6.0`, and JDK 17.

## Validation status

This fix was statically inspected and the ZIP structure checked. No Gradle executable or Android SDK is available in the artifact-authoring environment, so **Android Build / Lint / Unit Tests are not claimed to pass**. GitHub Actions must provide the next real build result.

## Sources

- https://developer.android.com/build/migrate-to-built-in-kotlin
- https://developer.android.com/blog/posts/whats-new-in-the-jetpack-compose-april-26-release
- https://developer.android.com/blog/posts/what-s-new-in-the-jetpack-compose-august-26-release
- https://developer.android.com/jetpack/androidx/releases/lifecycle
- https://developer.android.com/jetpack/androidx/releases/navigation
