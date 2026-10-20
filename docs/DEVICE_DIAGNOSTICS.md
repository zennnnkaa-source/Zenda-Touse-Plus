# Device Discovery & Diagnostics Core

## Purpose

The diagnostics core provides the data layer used by InputBridge's future Keyboard, Mouse, and Controller Labs. It uses public Android APIs and distinguishes device enumeration from observed input delivery.

## Device registry

- `InputManager` attach/change/detach callbacks refresh the current registry.
- Device descriptors include Android's device ID, display name, descriptor, source bitmask, vendor/product IDs, virtual-device flag, keyboard type, and motion-range metadata.
- Each motion range includes its axis/source identifiers and min/max/flat/fuzz/resolution values as exposed by Android.
- Device IDs are runtime identifiers, not durable user identities. Do not use them as permanent profile keys.

## Event capture scope

The Lab Activity records only events that Android dispatches to its own window:

- `KeyEvent`: down, repeat, up, key-code label, repeat count, meta state, device ID, and source.
- `MotionEvent`: action, source class, pointer count, button-state bitmask, and values for a bounded set of axes exposed by the device (axis values are sampled from pointer index 0 when the event has multiple pointers).
- The observer wraps the Activity window's public `Window.Callback` interface and delegates every callback to the original Activity callback; it does not override AndroidX `ComponentActivity`'s restricted dispatch methods. The controller does not read events from other apps, intercept system-wide input, or fabricate test events. An empty event log means no matching event has reached this Activity during the current monitoring window; it does not prove the device is unsupported.

## Bounded state and lifecycle

- The recent event log retains at most 200 entries. Older entries are discarded as new events arrive and a dropped-event counter is incremented.
- The UI displays at most the latest 30 entries to keep rendering bounded.
- Pressed-key state is keyed by `(deviceId, keyCode)` so the same key code from two devices is not conflated.
- `ACTION_UP` releases a key; device removal releases all keys belonging to that device; Activity stop clears any remaining held state to avoid stale keys after interruption.
- The registry listener is registered while the Activity is started and unregistered when it stops.
- Event summaries contain key-code names rather than typed character text. Key-event recording is paused while the six-digit pairing-code field is focused; entering the code therefore does not add digit key codes to the diagnostic log. The core does not persist raw input events or transmit telemetry.

## Axis transforms

`DiagnosticsPolicy` provides pure, unit-testable functions:

- centered range normalization to `[-1, 1]`, including asymmetric device ranges;
- unipolar range normalization to `[0, 1]` for controls such as triggers;
- dead-zone handling, inversion, sensitivity clamping, and optional X/Y swap.

These are mathematical helpers only. They do not change Android device calibration globally and are not yet connected to a mapping profile.

## Known limits

- Events are only observed when delivered to the Lab Activity; this is not system-wide input capture.
- Device enumeration can include built-in and virtual devices, and does not guarantee an external peripheral is physically connected.
- OEM firmware, HID reports, focus, Android dispatch behavior, and device source flags can affect which events appear.
- A dedicated interactive Keyboard/Mouse/Controller Lab, event export, and target-app input mapping belong to later work.
- Unit tests were added, but Android Gradle execution and physical-device verification remain pending until the `Android Release` workflow runs and a device test is recorded.
