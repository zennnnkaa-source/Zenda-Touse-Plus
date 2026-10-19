# Phase 3 source audit — 2026-10-20

## Scope

Reviewed the notification-based Wireless debugging pairing path in the existing `Zenda_Touse_Plus_Phase1` repository: `AdbPairingNotificationService`, `AdbServiceDiscovery`, `PairingNotificationPolicy`, the direct-activation parts of `MainActivity`, ADB manager ownership, manifest permissions, notification actions, unit-test coverage and the Phase 3 workflow. This was a source review; it was not a Gradle build or physical-device test.

## Defects found and corrected

- **Search timeout was cancelled before it could fire.** `beginDiscovery()` queued the timeout and then called the stop helper that removed that same callback. Discovery shutdown now occurs before a fresh timeout is scheduled; timeout handling closes the discovery without cancelling itself.
- **Retries could stack delayed restarts.** A single reusable restart runnable is now replaced/cancelled before rescheduling, avoiding multiple session resets.
- **RemoteInput extras were trusted too broadly.** The handler now validates the session, six ASCII digits, endpoint validity, pairing service kind, and exact host/port match against the current discovered endpoint. Each session also uses a distinct PendingIntent data URI.
- **Lost or idle mDNS endpoints could leave a stale code-input action active.** Discovery loss is now surfaced to the pairing service and Activity. A lost endpoint clears the notification input action and restarts the search timeout; an endpoint that remains idle for 3 minutes expires automatically and triggers rediscovery. Discovery callbacks carry a generation so late results from a previous run cannot overwrite newer endpoints.
- **mDNS reception could be unreliable on Android 11–12 and older Android 13 extension levels.** Discovery now holds a Wi-Fi multicast lock only while needed and releases it when discovery stops or fails. Android 16 activation also offers the `NEARBY_WIFI_DEVICES` permission where the OS requires it for opt-in local-network restrictions; denial is reported as a possible discovery/connection limitation rather than falsely reporting pairing failure.
- **Internal status broadcasts were not explicitly protected on Android 11–12.** The Lab now declares a signature-level internal permission, sends the broadcasts with that permission, and registers the receiver with the same permission on API 30–32; API 33+ continues to use `RECEIVER_NOT_EXPORTED`.
- **Activity and pairing service could create independent ADB managers at the same time.** Both now use one process-level manager/identity instance, reducing risk of identity-file races and inconsistent certificates during pairing/connection. Disconnect detaches the shared singleton before closing it so the next activation can create a fresh manager.
- **Notification action controls were underspecified.** Actions now have icons, the finished success notification does not offer a meaningless Stop action, and user cancellation is not recorded as a protocol pairing failure.
- **Discovery timeout could pollute probe history.** Notification results now explicitly distinguish an attempted ADB pairing operation from discovery/notification failures, so timeouts are not recorded as failed ADB protocol attempts.
- **A rejected code could leave a stale RemoteInput available briefly.** After a real pairing attempt fails, the service clears the endpoint, advances the session id, closes the old discovery and removes the old code action before scheduling retry.
- **A background Activity could restart a second discovery while system Settings remained open.** Pairing result handling avoids restarting Activity discovery while the Settings screen is still foregrounded; the normal resume path reacquires discovery when the user returns.
- **Notification and in-app submission could race.** A process-level submission guard now prevents the in-app/manual pairing path and Disconnect from stopping or overlapping an active notification-driven ADB pairing worker.
- **Retry and endpoint/session policy coverage was too narrow.** Unit tests now cover malformed codes/endpoints, stale or mismatched request data, connection endpoints being rejected as pairing endpoints, service-loss recovery, and success-state guards.

## Current implementation contract

The notification only requests the code that the Android Wireless debugging UI is already showing. It does not inspect Android Settings UI, create or guess a code, invoke root, or use a remote server. A successful result means only that the ADB pairing protocol accepted the request. It does not prove that a later ADB connect, helper bootstrap or input event succeeded.

If notification permission is denied or an OEM does not expose RemoteInput, the existing in-app pairing field remains the fallback. Endpoint discovery uses the advertised pairing port; the separate connection port remains a separate later step.

## Validation evidence

- XML manifest parse: **passed**.
- YAML workflow syntax parse: **passed** (the workflow content was parsed with PyYAML; GitHub Actions execution remains unverified).
- Project structural validator: **0 errors, 1 warning**; the warning is that the repository does not include Gradle Wrapper scripts/JAR/properties. CI installs Gradle 9.6.0 explicitly.
- Pure pairing-policy/state smoke check: **passed 17 assertions** using `kotlinc`; this is not a JUnit/Gradle test result.
- Android discovery source compilation against temporary hand-written API stubs: **not counted as passed**; the local compiler process did not finish reliably after the latest changes. The Android SDK/Gradle compilation remains the authoritative pending check.
- Gradle lint, JUnit tasks, debug APK assembly: **not run here** because this environment has no installed Gradle executable or Android SDK.
- Physical device, Android OEM notification surface, permission denied/granted paths and Wireless debugging pairing protocol: **not verified**.

## Manual/device test checklist

1. Start Direct activation with notifications allowed. Confirm the persistent notification first says it is searching, then adds the inline code action only after the pairing service is discovered.
2. Enter a wrong/expired code. Confirm the app reports a real pairing failure and does not mark pairing as successful.
3. Enter the current six-digit code. Confirm the success notification appears only after the ADB library returns success; then separately check the current connection port, shell identity and target event log.
4. Close the Android pairing dialog after discovery. Confirm the stale input action is removed and the notification returns to a discovery/retry state.
5. Leave Wireless debugging off for three minutes. Confirm the search timeout and Retry action appear; separately leave a discovered endpoint idle for three minutes and confirm the endpoint expires and discovery restarts.
6. Deny notification permission or test an OEM that suppresses RemoteInput. Confirm the in-app fallback can still pair.
7. Tap Stop while searching. Confirm the service stops without showing a false pairing-protocol failure.
8. On Android 11–12 and Android 13 below T extension 7, verify mDNS discovery while the temporary multicast lock is active, then verify it is released after stopping discovery.
9. On Android 16, test both granting and denying Nearby devices permission with local-network restrictions enabled; verify the warning and recovery path.
10. Repeat after rotating/recreating the Activity and after the app process is reclaimed, observing how the pending notification behaves.

Do not mark Phase 3 fully verified until Phase 3 CI is green and the device checklist has been exercised on the target device.
