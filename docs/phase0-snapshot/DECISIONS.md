# DECISIONS.md — InputBridge Phase 0

Format: ID · Decision · Alternatives (conservative / improved / exploratory) · Rationale · Status · Revisit trigger.
Status values: **LOCKED** (change needs a new decision entry), **PROVISIONAL** (to be proven by a Phase 1 spike), **REJECTED**.

---

### D-001 Platform & language — LOCKED
Native Android, Kotlin, Jetpack Compose + Material 3, minSdk 30 (Android 11, required for Wireless debugging), single-module first, multi-module later only if build time demands it.
Alternatives: Flutter/React Native (rejected: Binder/UserService/input APIs are native-only).

### D-002 Activation architecture — LOCKED (shape) / PROVISIONAL (Option B)
One abstraction `PrivilegedBackend` with two bootstraps that start the **same helper process**:
- **A: Shizuku UserService** — primary, recommended default.
- **B: Direct Wireless Debugging** — ADB pairing/connect used *only to launch the helper*; marked **Experimental** in UI until a physical-device test passes.

| Option | Description | Verdict |
|---|---|---|
| Conservative | Shizuku only | Safe but violates the "two required paths" spec |
| **Improved (chosen)** | Shizuku primary + Option B behind the same interface, Option B flagged Experimental | Meets spec without false assurance |
| Exploratory | Own full ADB implementation, no third-party libs | High risk/effort; only if D-003 spike fails |

### D-003 ADB client library — PROVISIONAL
Evaluate **libadb-android** in a Phase 1 spike behind `AdbClient` interface (so it can be swapped). Accept the LGPL obligation only if the spike passes (pairing + connect + shell command on ≥ 1 real device). Never copy LADB code; reject xswl369.
Exit criterion: documented pass/fail on a physical device. If fail → Option B stays "Not available" and the spec gap is reported.

### D-004 Input injection strategy — PROVISIONAL
Priority order:
1. **Shell/Shizuku helper injecting `InputEvent`s** (system-wide, needed for third-party games).
2. **In-app pointer capture** for Mouse Lab/test pad (our own window; no privilege).
3. **AccessibilityService `dispatchGesture`** as an *optional* fallback (keyboard/gamepad → tap/swipe mapping), off by default because of Play policy and the 20-stroke limit.
Mouse look in games is **NOT VERIFIED** until a game test passes.

### D-005 Input capture — LOCKED
Capture keyboard/mouse/gamepad via the app's focused window (`dispatchKeyEvent`, `dispatchGenericMotionEvent`, `requestPointerCapture`) and `InputDevice` APIs for Device Lab. System-wide capture while a game is foreground is part of the helper design (Phase 1/3 spike); not assumed to work.

### D-006 Overlay — LOCKED
Use `SYSTEM_ALERT_WINDOW` overlay with `FLAG_NOT_TOUCHABLE` for HUD/crosshair, alpha ≤ 0.8 (Android 12 untrusted-touch rule). Interactive editing happens in our own full-screen activity, not on top of the game.

### D-007 Persistence — LOCKED
Room (library, profiles, bindings) + DataStore (settings). Export/import/backup via **SAF** to a versioned JSON (`schemaVersion`). No reliance on app-specific external storage as the only copy.

### D-008 Package visibility — LOCKED
Use `<queries>` + launcher intent for the Game Library. **Do not request `QUERY_ALL_PACKAGES`** unless a later phase proves it is required and policy-justifiable.

### D-009 Distribution — PROVISIONAL
Primary: GitHub Releases (signed APK from GitHub Actions) + F-Droid-style transparency. Google Play only after policy review (Accessibility, overlay, Shizuku-type behaviour). **Developer verification** with Google must be completed to avoid sideload blocking in Thailand (enforced since 2026-09-30).

### D-010 Toolchain — PROVISIONAL (versions pinned in Phase 1 only from live sources)
Target AGP 9.4.0 + Gradle ≥ 9.6.0 + JDK 17 + Build Tools 36.0.0, compile/target API per AGP max (37) after a real build; Compose stable 1.12.1 / Material3 1.4.0 (or newest BOM); Room 2.8.x (Room 3 migration deferred); Kotlin version resolved at Phase 1 from AGP compatibility table. Use a version catalog (`libs.versions.toml`). Versions are not locked until `./gradlew build` actually passes in CI.

### D-011 CI — LOCKED (design) / UNVERIFIED (run)
GitHub Actions: lint, unit tests, assembleDebug, upload artifact. No claim of green CI until a run exists.

### D-012 Branding / legal — LOCKED
Name **InputBridge**; no assets, names, strings or UI copied from Panda Mouse Pro, Touch Pro, LADB or others. Document third-party licenses in `THIRD_PARTY_NOTICES.md` from Phase 1.

### D-013 Safety & honesty rules — LOCKED
- UI labels: **Verified / Experimental / Not available**, driven by runtime checks, never hard-coded optimism.
- No telemetry in the first versions. No network permission unless a feature requires it (and then disclosed).
- Per-game compatibility is recorded as "tested on <device, Android version, date>", else "Unknown".

### D-014 Scope lock for Phase 0 — LOCKED
Phase 0 produced documents only. No Android project, Gradle files or app code were created.

---

## Rejected
| ID | Rejected | Reason |
|---|---|---|
| R-001 | LADB code reuse | Restrictive license; incompatible with Shizuku coexistence |
| R-002 | xswl369 | Shared ADB key across installs, OCR pairing, immature |
| R-003 | Accessibility-only architecture | 20-stroke limit, cancellation, Play policy |
| R-004 | Root-required design | Not in spec; narrows audience (root via Shizuku remains possible but not required) |
