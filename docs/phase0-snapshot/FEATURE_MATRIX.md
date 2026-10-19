# FEATURE_MATRIX.md — InputBridge Phase 0

Feasibility labels: **FEASIBLE** (public API, documented) · **LIKELY** (documented mechanism, end-to-end unproven) · **EXPERIMENTAL** (needs spike) · **UNKNOWN** (needs physical device) · **RISKY** (policy/legal).
"Phase" = earliest phase where the feature is planned (final phase numbers follow the master prompt; adjust there if they differ).
No feature below has been built or tested. All "feasible" statements refer to research only.

## 1. Systems

| System | Feature | Backend / API | Feasibility | Evidence | Needs physical device? | Planned phase |
|---|---|---|---|---|---|---|
| Game Library | List installed games/apps | `PackageManager` + `<queries>` launcher intent | FEASIBLE | Docs | No | 1–2 |
| Game Library | Launch game | `getLaunchIntentForPackage` | FEASIBLE | Docs | Minor | 2 |
| Game Library | Per-game profile link | Room | FEASIBLE | Docs | No | 2 |
| Control Studio | Visual layout editor (buttons, joystick, crosshair) | Compose Canvas | FEASIBLE | Docs | No | 3 |
| Control Studio | Overlay on top of game | `SYSTEM_ALERT_WINDOW`, `FLAG_NOT_TOUCHABLE`, alpha ≤ 0.8 | LIKELY | Docs | **Yes** (OEM overlay behaviour) | 3–4 |
| Control Studio | Key → tap at screen position | Helper injection (A/B) or a11y fallback | LIKELY | Mechanism only | **Yes** | 4 |
| Control Studio | Key → swipe / hold | Same | LIKELY | Mechanism only | **Yes** | 4 |
| Control Studio | Gamepad stick → virtual joystick | Helper injection | LIKELY | Mechanism only | **Yes** | 4–5 |
| Keyboard Lab | Show pressed keys / scancodes / test pad | `KeyEvent` in own window | FEASIBLE | Docs | Needs a keyboard | 2 |
| Keyboard Lab | Remap, combos, toggle/hold modes | App logic | FEASIBLE | — | No | 4 |
| Keyboard Lab | Capture keys while game is foreground | Helper-level input reader | EXPERIMENTAL | None | **Yes** | 4–5 |
| Mouse Lab | Mouse test pad, buttons, wheel | `MotionEvent`, own window | FEASIBLE | Docs | Needs a mouse | 2 |
| Mouse Lab | Relative motion (pointer capture) in own window | `requestPointerCapture`, `AXIS_RELATIVE_X/Y` | FEASIBLE | Docs (API 26) | Yes (touchpad quirks on Android U+) | 2 |
| Mouse Lab | **Mouse look in third-party game** (relative → touch drag) | Helper injection | EXPERIMENTAL / UNKNOWN | None | **Yes, per game** | 4–5 |
| Mouse Lab | Sensitivity / DPI / acceleration curve | App logic | FEASIBLE | — | No (feel needs device) | 4 |
| Device Lab | List input devices, capabilities, VID/PID | `InputDevice` | FEASIBLE | Docs | Needs devices | 2 |
| Device Lab | Gamepad button/axis tester | `KeyEvent`/`MotionEvent` | FEASIBLE | Docs | Needs gamepad | 2 |
| Device Lab | Latency probe | App timing | LIKELY | — | Yes | 6 |
| Profile Manager | Create/duplicate/delete profiles | Room | FEASIBLE | Docs | No | 2–3 |
| Profile Manager | Export/import JSON (SAF) | SAF, versioned schema | FEASIBLE | Docs | No | 3 |
| Profile Manager | Share/community import | SAF / file | FEASIBLE | — | No | 7 |
| Profile Manager | Auto-switch profile by foreground app | `UsageStats` or a11y or helper | EXPERIMENTAL | None | Yes | 5–6 |

## 2. Activation

| Feature | Backend | Feasibility | Needs physical device? | Phase |
|---|---|---|---|---|
| Option A: Shizuku detect / request permission / bind UserService | Shizuku API 13.1.5 | LIKELY | **Yes** | 1 |
| Option A: guided restart after reboot | App UX | FEASIBLE | Yes | 1 |
| Option B: mDNS discovery of pairing/connect services | `NsdManager` | LIKELY | **Yes** | 1 |
| Option B: SPAKE2 pairing with 6-digit code | libadb-android (candidate) | EXPERIMENTAL | **Yes** | 1 |
| Option B: TLS connect + launch helper | libadb-android | EXPERIMENTAL | **Yes** | 1 |
| Option B: Android 17 auto-reconnect | adb 37 behaviour | UNKNOWN | **Yes (Android 17 device)** | later |
| Status UI: Verified / Experimental / Not available | Runtime probes | FEASIBLE | Partly | 1 |

## 3. Cross-cutting

| Feature | Feasibility | Notes |
|---|---|---|
| Material 3 UI, dark mode, Thai + English strings | FEASIBLE | |
| Room + DataStore | FEASIBLE | Room 2.8.x |
| CI on GitHub Actions | FEASIBLE | Not run yet |
| Play Store release | RISKY | Accessibility declaration, overlay, policy on input-injection tools |
| Sideload release | RISKY | Android Developer Verification (Thailand enforced 2026-09-30) |
| Anti-cheat compatibility | UNKNOWN / RISKY | Per-game; no guarantees to be promised to users |

## 4. Out of scope (Phase 0 lock)
Root-only features, bypassing game anti-cheat, macro/auto-clicker farming features, cloud accounts, telemetry, copying Panda/Touch Pro UI or assets.
