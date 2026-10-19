# TEST_PROTOCOL.md — Phase 1 Reproducible Test Plan

## Safety and setup

1. Use an Android 11+ emulator or physical test device you control. Prefer a physical device for Wireless debugging and input injection results. Test only the dedicated InputBridge Test Target or another app you are authorized to test.
2. Build/install **both** APKs: `InputBridge Lab` and `InputBridge Test Target`. Check that both install and launch independently.
3. Record device model, Android version/build number, API level, security patch, app version, date/time and whether the session uses Wi-Fi or mobile data. Do not paste ADB pairing codes, ADB private keys, or personal network details into a public report.
4. Open the target app once and verify it starts with a blank/waiting event log. The target does not fabricate incoming events.

## A. Baseline target events

1. In the target app, tap, drag and release on the capture canvas. Expect `TOUCH DOWN`, `TOUCH MOVE` where applicable, and `TOUCH UP` (or `CANCEL` if interrupted).
2. Attach a physical keyboard and press/release a normal key, then hold a key long enough to test repeat events. Check key code, scan code, device ID, source, repeat count and down/up state.
3. Attach a mouse. Move it across the canvas, click, and use vertical/horizontal scroll if supported. Check source/device, relative axes, button state and scroll axes; do not infer broken hardware from one missing axis.
4. Press `Capture pointer`, move mouse inside the app and then press `Release`. Record whether capture was acquired/released and whether movement is reported. Test touchpad separately from mouse where possible.
5. If supported, attach a controller and observe its actual button/axis events in the target app. This test target logs only the key/motion event surface it receives; it is not yet a complete controller remapper.

## B. Shizuku activation probe

1. Start Shizuku through its official documented path for the device. On a non-root device this may require restarting after reboot.
2. In Lab, tap `Check status`. Record the actual state; if Binder is unavailable, stop here and fix that before proceeding.
3. Tap `Request permission` and respond on the Shizuku authorization prompt. Deny once as a recovery test, then retry and grant.
4. Tap `Bind helper`. Wait for the bind callback. Record the reported process UID. Do not call UID 2000 root; do not claim injection from UID alone.
5. Tap `Open InputBridge Test Target` to verify it launches. Return to the Lab and run `Test one tap`, then `Test one swipe` in the Shizuku card. The shared helper issues a bounded numeric tap or 500 ms horizontal swipe after opening the target; inspect for actual `TOUCH DOWN/UP` and `MOVE` rows. A helper response alone is not a pass.
6. Inspect the target's event log. If a new real `TOUCH DOWN/UP` appears at the expected time, record it with a screenshot/log that contains no secrets. If nothing appears, the result is fail/unverified even if the shared helper returns a command response.
7. Tap `Read result` for the helper-side result. Record permission denial, binder death, helper failure or no target event independently.
8. Unbind or stop the service and confirm the socket helper is stopped. Stop/restart Shizuku or reboot only if safe for the test device, then confirm Lab reports the binder/helper as unavailable instead of stale success. Rebind and repeat.

## C. Direct Wireless debugging probe (Experimental)

1. Enable Developer options and Wireless debugging from Android Settings. Use the OS UI, and follow device-specific instructions; do not assume a single Settings intent opens the right screen on every OEM.
2. From the Wireless debugging screen, open **Pair device with pairing code**. Note the temporary pair port and the connection service's current port separately. Do not send the pairing code to anyone and do not place it in a report.
3. In Lab, enter device host/IP, the **pairing port**, and the six-digit code; press `Pair device`. The code field should clear immediately. Record only success/failure and relevant error category.
4. If pairing reports success, enter the current **connection port** separately and press `Connect + verify shell id`. Never reuse the pairing port unless Android's current UI separately states that is the current connect port.
5. Record shell result independently. `id` output is evidence of a shell session, not input injection.
6. Tap `Bootstrap helper + verify UID`. The Lab uses ADB only for the fixed APK-path lookup and detached `app_process` bootstrap. The shared helper then reports its actual UID over the authenticated loopback socket. If that fails, pairing/shell can still be working while helper launch is blocked.
7. Tap `Test one tap`, then `Test one swipe`. The Lab opens the target and asks the helper to issue a bounded numeric tap or 500 ms horizontal swipe over the loopback protocol after a short delay; the input commands are not sent over the ADB connection. Inspect the target app for real `TOUCH DOWN/UP` rows (and `MOVE` rows for swipe). Record pass only if the target receives the real events. This simple shell helper does not test simultaneous injected multi-touch.
8. Use `Stop helper` to close the helper's local server, then `Disconnect`. Reopen/forget Wireless debugging pairing through Android Settings if needed. Do not leave Wireless debugging enabled unnecessarily after the test.

## D. Reporting template

For each run, write:

```text
Test ID:
Date/time (local):
Device model / OEM:
Android version / build / API:
Lab APK version:
Target APK version:
Activation path: Shizuku / Direct Wireless debugging
Stages: binder / permission / helper / pairing / connection / shell / target event
Expected result:
Actual result:
Evidence of real target event (yes/no + event row type):
Latency (optional, measured method and repetitions):
Recovery / failure details:
Logs redacted (pairing code and keys removed): yes/no
```

The Lab reports helper round-trip time, which includes command execution and response but is not a true event-delivery latency measurement. Do not claim end-to-end latency from it or from a single casual tap. For latency comparisons, repeat at least 30 times under the same device conditions and separately report median and p95, failures/cancellations, and the exact measurement method. Do not begin a full mapping engine until at least one end-to-end path proves delivery.
