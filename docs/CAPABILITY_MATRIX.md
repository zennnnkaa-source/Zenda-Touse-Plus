# CAPABILITY_MATRIX.md — Android × Activation × Backend

**As of:** 2026-10-19. This is a planning/evidence matrix, not a compatibility claim. Runtime cells remain `NOT TESTED` because this authoring environment cannot exercise the updated source on a physical device. The user reports the existing app flow is usable, but the revised mDNS auto-discovery and target event delivery still need a fresh device check.

Legend: `DOC` = mechanism documented by public API/protocol; `POC SOURCE` = code exists but is not compiled; `NOT TESTED` = no runtime evidence; `EXPERIMENTAL` = result must not be shown as available to end users.

## Platform baseline

| Android version | API level | Shizuku Binder / UID | Direct Wireless debugging pairing + TLS connect | `input tap` delivered to target | Pointer capture inside target | Overall device evidence |
|---|---:|---|---|---|---|---|
| Android 11 | 30 | DOC + POC SOURCE / NOT TESTED | DOC: Wireless debugging introduced before/on this version; POC SOURCE / NOT TESTED | NOT TESTED | Public API baseline, NOT TESTED | None |
| Android 12 / 12L | 31 / 32 | POC SOURCE / NOT TESTED | DOC + POC SOURCE / NOT TESTED | NOT TESTED | Public API baseline, NOT TESTED | None |
| Android 13 | 33 | POC SOURCE / NOT TESTED | DOC + POC SOURCE / NOT TESTED | NOT TESTED | Public API baseline, NOT TESTED | None |
| Android 14 | 34 | POC SOURCE / NOT TESTED | DOC + POC SOURCE / NOT TESTED | NOT TESTED | Public API baseline, NOT TESTED | None |
| Android 15 | 35 | POC SOURCE / NOT TESTED | DOC + POC SOURCE / NOT TESTED | NOT TESTED | Public API baseline, NOT TESTED | None |
| Android 16 | 36 | POC SOURCE / NOT TESTED | DOC + POC SOURCE / NOT TESTED | NOT TESTED | Public API baseline, NOT TESTED | None |
| Android 17 | 37 | API compatibility NOT VERIFIED on device | Wireless debugging behavior of the current OS and any ADB Wi‑Fi 2.0 behavior must be validated; NOT TESTED | NOT TESTED | POC target compiled against SDK 36 candidate; NOT TESTED on 37 | None |

The project uses `minSdk = 30` and `compileSdk = 36`. The build configuration may need `compileSdk` raised to 37 if APIs specific to Android 17 are later needed; Phase 1 code does not intentionally depend on Android 17-only APIs. A supported compile target is not the same as verified OEM/device compatibility.

## Activation / backend matrix

| Activation route | Backend or stage | What can be checked | Current status | Required proof |
|---|---|---|---|---|
| A — Shizuku | Binder alive/dead | Live binder and lifecycle callback | POC SOURCE / NOT TESTED | Physical device, including server restart |
| A — Shizuku | Shizuku permission | Actual permission result | POC SOURCE / NOT TESTED | Grant and deny on device |
| A — Shizuku | UserService identity | `Os.getuid()` inside helper process | POC SOURCE / NOT TESTED | Callback + reported UID from remote helper |
| A — Shizuku | Shared helper runtime | UserService starts shared `LocalHelperServer`; app sends loopback `TAP x y` | EXPERIMENTAL / NOT TESTED | `TOUCH DOWN` and `TOUCH UP` observed in target app |
| B — Direct ADB | SPAKE2 pairing | NSD discovery of the current pairing endpoint + six-digit code; manual fallback | POC SOURCE / DEVICE UNVERIFIED | Library reports pair success on a physical device after NSD resolution |
| B — Direct ADB | TLS ADB connect | NSD discovery of the separate current connection endpoint; manual fallback | POC SOURCE / DEVICE UNVERIFIED | Connection result and subsequent shell access on a physical device |
| B — Direct ADB | Shell identity | Fixed `id` command | POC SOURCE / NOT TESTED | Non-empty output after a connected session |
| B — Direct ADB | Shared helper runtime | Bootstrap `LocalHelperServer` via app_process; localhost token-authenticated `TAP x y` | EXPERIMENTAL / NOT TESTED | Helper UID response and actual target event, not bootstrap-command completion |
| No privilege | Test target | Own-window KeyEvent/MotionEvent and pointer capture | POC SOURCE / NOT TESTED | Emulator/device with actual keyboard/mouse/controller |
| No privilege | Other apps / games | System-wide capture or injection | Not provided by this public-API target path | Requires separate privilege/backend and per-target verification |
| Accessibility fallback | `dispatchGesture` | Not implemented in this Phase 1 Lab | Not available in this package | Separate feasibility/security/policy review before adding |

## How to update the matrix

For every tested device, duplicate or extend the row rather than changing all OEMs to `PASS`. Record Android release/build, OEM/model, path, helper UID, pairing/connect ports' **types only** (never code), exact target event, failure/recovery and timestamp in `FEASIBILITY_RESULTS.md`. If a feature has not been tried on a device/version, keep it `NOT TESTED`.
