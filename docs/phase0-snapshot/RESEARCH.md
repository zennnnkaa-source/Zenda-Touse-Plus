# RESEARCH.md — InputBridge Phase 0

Research date: **2026-10-18**. Evidence levels: **[VERIFIED-DOC]** = read in a primary source (official docs / repo) during Phase 0; **[SEARCH]** = seen only in search-result snippets; **[ASSUMPTION]** = inferred, not checked; **[UNVERIFIED-DEVICE]** = needs a physical device.
Nothing in this document was built or run on a device. No build or test was executed in Phase 0.

---

## 1. Summary (สรุปสั้น)

| Question | Answer | Confidence |
|---|---|---|
| Can Option A (Shizuku) provide a privileged process for input injection? | Yes in principle: Shizuku API + UserService runs code as shell UID 2000 (or root). Actual injection of mouse-look/touch into *other* apps is not proven. | High (mechanism) / Low (end-to-end input) |
| Can Option B (own Wireless-debugging ADB client) work? | Protocol is documented in the AOSP/community ecosystem and libraries exist, but no library is audited and none was tested here. Treat as **Experimental / NOT VERIFIED**. | Low–Medium |
| Can mouse-look work inside our own app without privilege? | Yes: `View.requestPointerCapture()` (API 26) + `AXIS_RELATIVE_X/Y`. | High |
| Can mouse-look work *system-wide in third-party games* without privilege? | No public non-privileged API. Needs shell/Shizuku-level `INJECT_EVENTS` path. | Medium |
| Is AccessibilityService `dispatchGesture` enough? | Limited (20 strokes/gesture, cancellation, latency). Fallback only. | Medium–High |
| Distribution risk | Play policy (Accessibility, QUERY_ALL_PACKAGES) and Android Developer Verification (Thailand in first wave from 2026-09-30). | Medium–High |

---

## 2. Activation paths

### 2.1 Option A — Shizuku  *(Primary)*
- Shizuku exposes a Binder to apps; the privileged server runs as **ADB shell (UID 2000)** or **root (UID 0)**. [VERIFIED-DOC] https://github.com/RikkaApps/Shizuku
- Non-root mode: the server **dies on reboot** and must be restarted (wireless debugging on Android 11+, or `adb shell`). The app must detect "not running" and guide the user. [VERIFIED-DOC]
- API artifact: Shizuku-API 13.1.5 is the latest seen on Maven Central; its page showed an old date ("3 years ago"), so **re-check in Phase 1** before pinning. [VERIFIED-DOC, flagged] https://github.com/RikkaApps/Shizuku-API
- Integration pattern: permission request → bind `UserService` (our own code in a separate process with shell privileges) → AIDL → inject `InputEvent` from there. [VERIFIED-DOC]
- Open: whether framework `InputManager.injectInputEvent` from the UserService is permitted on every OEM/Android 11–17 build. [UNVERIFIED-DEVICE]

### 2.2 Option B — Direct Wireless Debugging (own ADB pairing)  *(Required by spec, Experimental)*
- Android 11+ Wireless debugging: **SPAKE2 pairing with a 6-digit code**, then connect over **TLS (STLS, TLS 1.3)**. Pairing and connect ports are **separate and randomly chosen**; discovery via mDNS `_adb-tls-pairing._tcp` and `_adb-tls-connect._tcp`. Keys are stored in `/data/misc/adb/adb_keys`. [VERIFIED-DOC] https://developer.android.com/tools/adb#wireless-android11-command-line
- Android 17 / adb 37 "ADB Wi-Fi 2.0" adds trusted-network auto-connect. Behavior on-device for third-party clients not tested. [SEARCH] / [UNVERIFIED-DEVICE]
- Practical flow the app must implement: user enables Developer options → Wireless debugging → "Pair device with pairing code" → app discovers pairing service via mDNS (or user types port) → pair → discover connect service → connect → run shell command that starts our helper process.
- Constraint: our app and the Wireless-debugging UI must both be visible (split-screen / notification inline reply is the known workaround). [ASSUMPTION — to verify in Phase 1]

### 2.3 On-device ADB client candidates

| Candidate | Finding | Decision |
|---|---|---|
| **libadb-android** (MuntashirAkon) https://github.com/MuntashirAkon/libadb-android | Supports pairing + TLS connect. Unaudited. LGPL dependency → affects distribution (dynamic linking/relinking obligations). | **Evaluate in Phase 1** behind an interface. |
| **LADB** (tytydraco) https://github.com/tytydraco/LADB | Restrictive license, no Play-friendly clause, and by design incompatible with running alongside Shizuku. | **Do not copy code.** Reference for UX only. |
| **xswl369** (repo name only) | Shared ADB key across installs, OCR-based pairing, 1 commit. | **Rejected** (security + maturity). |
| Own implementation of ADB protocol | High effort/risk (SPAKE2, TLS, key mgmt). | Only if libadb-android fails Phase 1 spike. |

Design rule: use ADB **only to bootstrap a helper process** (same helper binary/class used by Shizuku UserService), not as a long-lived input pipe.

---

## 3. Input injection backends

| Backend | Privilege | Mouse look | Multi-touch | Notes | Evidence |
|---|---|---|---|---|---|
| In-app pointer capture | none | **Yes, in our own window only** | n/a | `View.requestPointerCapture()` API 26, `SOURCE_MOUSE_RELATIVE`, `AXIS_RELATIVE_X/Y`; captured-touchpad behavior changed on Android U+ | [VERIFIED-DOC] |
| AccessibilityService `dispatchGesture` | user-enabled a11y | Poor (discrete strokes) | Up to **20 strokes** per gesture; `willContinue`/`continueStroke` (API 26); new gesture may cancel the old one | Needs Play a11y declaration; latency not measured | [VERIFIED-DOC] for 20 strokes; max duration and cancel-on-new-gesture **[SEARCH only]** |
| Shizuku + framework injection (UserService) | shell UID | Potentially full | Potentially full | Best candidate; end-to-end unproven | [UNVERIFIED-DEVICE] |
| Direct ADB helper (Option B) | shell UID | Same as above | Same as above | Same helper, different bootstrap | [UNVERIFIED-DEVICE] |
| Public APIs only | none | No for other apps | No | Cannot control other apps | [VERIFIED-DOC] |

Key technical fact: only the privileged paths can deliver **continuous relative motion mapped to a touch drag** in a third-party game. Whether games accept synthesized `MotionEvent`s with `SOURCE_TOUCHSCREEN` from shell injection must be tested per game. [UNVERIFIED-DEVICE]

References: https://developer.android.com/reference/android/view/View#requestPointerCapture() · https://developer.android.com/reference/android/accessibilityservice/GestureDescription

---

## 4. Overlay & game-screen constraints
- Overlay needs `SYSTEM_ALERT_WINDOW`. Use `FLAG_NOT_TOUCHABLE` for non-interactive crosshair/hints so touches pass through. [VERIFIED-DOC]
- Android 12+ **untrusted touch** rule: overlays with opacity above **0.8** block pass-through touches from being delivered to the app below. Keep overlay alpha ≤ 0.8 (or use our own window only for the editor). [VERIFIED-DOC]
- Anti-cheat / FLAG_SECURE / game-specific detection of injected input: **not researched**; [ASSUMPTION] some games will reject injected input. Must be tested per game.

---

## 5. Storage, backup, profiles
- Room for library/profiles/bindings; DataStore for settings. (Room 2.8.5 stable; Room 3.0 migration docs exist — stay on 2.8.x for Phase 1, plan the migration separately.) [VERIFIED-DOC]
- App-specific external storage is **deleted on uninstall** → not suitable as the only copy of user profiles. Use **Storage Access Framework** (`ACTION_CREATE_DOCUMENT`/`OPEN_DOCUMENT`) for export/import/backup. [VERIFIED-DOC]
- Profile export format: versioned JSON with `schemaVersion`, per-game entries keyed by package name. [DECISION — see DECISIONS.md]

---

## 6. Distribution & policy

### 6.1 Google Play
- **Accessibility API**: requires Play Console declaration, in-app prominent disclosure, and the tool must be core to the app's functionality. Using AccessibilityService for gaming input is a policy risk. https://support.google.com/googleplay/android-developer/answer/10964491 [VERIFIED-DOC/SEARCH]
- **QUERY_ALL_PACKAGES**: restricted; a Game Library needs installed-app lists. Prefer `<queries>` with a `MAIN/LAUNCHER` intent filter, which usually avoids the permission. https://support.google.com/googleplay/android-developer/answer/10158779 [VERIFIED-DOC]
- Apps that help bypass game rules / cheating could attract policy action — [ASSUMPTION]; keep marketing and docs focused on accessibility/controller mapping.

### 6.2 Android Developer Verification
- Google's developer-verification program: enforcement starts **2026-09-30 in Brazil, Indonesia, Singapore and Thailand**, global in 2027; includes an "advanced sideloading" flow; **installing via ADB remains allowed**. https://developer.android.com/developer-verification [VERIFIED-DOC/SEARCH]
- Impact for InputBridge: since the spec targets Thai users, **an unverified APK may be blocked or friction-heavy to sideload** on certified devices. Developer must register/verify before public release. Date is already past (2026-10-18), so this applies now.

---

## 7. Toolchain (checked 2026-10-18)

| Component | Version seen | Source | Note |
|---|---|---|---|
| Android Gradle Plugin | **9.4.0** | https://developer.android.com/build/releases/gradle-plugin | Requires Gradle ≥ 9.6.0, JDK 17, Build Tools 36.0.0, max API 37 |
| Compose (stable) | **1.12.1** | https://developer.android.com/jetpack/androidx/releases/compose | |
| Material 3 | **1.4.0** | same | |
| Compose BOM | 2026.08.00 seen | https://developer.android.com/develop/ui/compose/bom | Re-check the newest BOM in Phase 1 |
| Room | **2.8.5** stable | https://developer.android.com/jetpack/androidx/releases/room | Room 3.0 docs exist |
| Shizuku-API | 13.1.5 | Maven Central | Old date, re-check |
| Kotlin | not re-verified in Phase 0 | — | **Do not pin from memory**; resolve in Phase 1 against AGP 9.4 compatibility |

Versions above are **observations**, not a build proof. No Gradle build was run.

---

## 8. Competitor reference (Panda Mouse Pro / Touch Pro)
- Public store/marketing pages could not be fetched; **their activation UX and feature set remain UNVERIFIED**. InputBridge must not copy their branding, assets, names, or text; feature parity is described only by category in FEATURE_MATRIX.md.

---

## 9. Items that could not be verified
1. Max `dispatchGesture` duration and "new gesture cancels current gesture" behaviour — [SEARCH only].
2. Panda Mouse Pro activation flow — UNVERIFIED.
3. Shizuku-API artifact freshness (13.1.5, "3 years ago").
4. Android 17 ADB Wi-Fi 2.0 behaviour for third-party ADB clients.
5. Whether shell-level injection works with relative-mouse → touch drag in target games (all).
6. libadb-android pairing success rate across OEMs.
7. Kotlin version compatible with AGP 9.4.0 (not checked).
