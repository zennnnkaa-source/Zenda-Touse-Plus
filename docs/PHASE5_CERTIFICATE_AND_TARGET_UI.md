# Phase 5 — ADB Certificate Reliability and Target UI

## Wireless Debugging pairing

The previous identity generator created a self-signed X.509 certificate without the SubjectKeyIdentifier and PrivateKeyUsage extensions shown in the `libadb-android` integration example. This revision follows that documented certificate shape, uses explicit X.509 subject/issuer wrappers, checks the certificate validity, and verifies its self-signature before persisting it. The private-key bytes remain encrypted by an Android Keystore AES-GCM key.

The identity file is now versioned as `adb-identity-v2.bin`; the older `adb-identity-v1.bin` is not loaded by the new code, so an install upgrade creates a new local ADB identity instead of reusing the previous certificate. The old file is not proactively deleted, to avoid destructive migration behavior. Pairing codes are transient and are not added to the exception log message. The pairing service records the exception chain in Logcat under `ZendaPairing`, while the user-facing notification gives a more specific message for certificate failures.

The notification interaction continues to follow the public Shizuku pattern at a behavioral level: discover the current pairing service first, expose inline `RemoteInput` for the six-digit code, run the real ADB pairing protocol off the main thread, and show the actual result. The implementation is independent; no Shizuku source or artwork is copied. A manual in-app code field remains the fallback for notification restrictions.

## Test Target UI

The companion app has a refreshed dark, high-contrast layout with a branded header, concise instructions, Capture pointer / Release / Clear log controls, a dedicated touch surface, and a live event stream with an event count. The underlying event rows are still emitted only for actual input events delivered to the Activity/surface; the screen does not fabricate successful input delivery. Event history remains bounded.

## Branding

- Main launcher/app title: **Zenda Touse Plus**
- Companion target title: **Zenda Touse Plus Test Target**
- Both modules declare version name `0.5.0`. The existing package/application IDs are unchanged to preserve upgrades and Shizuku integration.

## Validation status

Static source and packaging checks are performed for this ZIP. The current environment does not have a configured Android SDK/Gradle execution path, so this document does not claim that Android Lint, Gradle unit tests, Release APK compilation, notification RemoteInput on the user's OEM, or successful real-device pairing has passed. After installing the new build, test using a fresh code from the currently open Wireless debugging pairing dialog. If the failure persists, capture the `ZendaPairing` Logcat exception chain with the six-digit code redacted.

## References

- [`libadb-android` README — certificate generation example](https://github.com/MuntashirAkon/libadb-android#configuring-adb)
- [Shizuku `AdbPairingService.kt`](https://github.com/RikkaApps/Shizuku/blob/master/manager/src/main/java/moe/shizuku/manager/adb/AdbPairingService.kt)
- [Shizuku wireless debugging setup guidance](https://shizuku.rikka.app/guide/setup/)
