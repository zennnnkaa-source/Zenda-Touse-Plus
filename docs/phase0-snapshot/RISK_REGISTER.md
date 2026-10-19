# RISK_REGISTER.md — InputBridge Phase 0

Scale: Likelihood (L) / Impact (I) = Low · Med · High. Status: Open unless noted. All risks are assessed from research; none was observed on a device.

| ID | Risk | L | I | Mitigation | Verify by | Owner phase |
|---|---|---|---|---|---|---|
| R-01 | Shell-level injection does not deliver mouse-look as touch drag in target games (games ignore injected events, wrong source/pointer handling) | Med | **High** | Spike with 2–3 popular games; keep in-app test pad as honest baseline; label per-game compatibility | Physical device + game | 1 / 4 |
| R-02 | Option B (own Wireless-debugging pairing) fails on some OEMs/Android versions (mDNS blocked, port changes, pairing UI needs split screen) | High | High | Swappable `AdbClient`; mark Experimental; Shizuku remains primary | Physical devices, Android 11–17 | 1 |
| R-03 | libadb-android is unaudited and LGPL — security and licensing exposure | Med | Med | Code review in spike, pin version, isolate behind interface, comply with LGPL (relinkable), consider own implementation if rejected | Code review + legal read | 1 |
| R-04 | Shizuku server dies on reboot (non-root) → app silently stops working | High | Med | Detect state on launch, clear UX to restart, never show "connected" without a live binder ping | Device test | 1 |
| R-05 | Shizuku-API artifact appears stale (13.1.5, "3 years ago") — compatibility with Android 16/17 unknown | Med | Med | Re-check Maven/GitHub at Phase 1; test on newest OS available | Build + device | 1 |
| R-06 | AccessibilityService fallback: 20-stroke cap, gesture cancellation, latency; Play policy declaration may be rejected | High | Med | Fallback only, off by default, prominent disclosure | Device + Play pre-check | 4 |
| R-07 | Overlay blocked/hidden by Android 12+ untrusted-touch rule (alpha > 0.8) or OEM restrictions | Med | Med | Alpha ≤ 0.8, `FLAG_NOT_TOUCHABLE`, editor in own activity | Device | 3 |
| R-08 | Android Developer Verification blocks sideloading of unverified APKs (Thailand from 2026-09-30, global 2027) | **High** | **High** | Register/verify developer identity early; document ADB-install and advanced-flow steps | Google program docs + test install on certified device | Release |
| R-09 | Google Play rejection (Accessibility, QUERY_ALL_PACKAGES, "cheat tool" perception) | Med | High | `<queries>` instead of QUERY_ALL_PACKAGES; honest description; pre-launch policy review | Play Console pre-review | Release |
| R-10 | Games / anti-cheat detect injected input and ban accounts | Med | High | In-app warning, no stealth/spoofing features, no compatibility promises | Per-game testing; user warning text | All |
| R-11 | Mouse pointer-capture quirks (captured touchpad behavior on Android U+, OEM differences) | Med | Med | Test with mouse + touchpad, fall back to non-captured mode | Device | 2 |
| R-12 | Competitor feature parity claims unsupported (Panda pages not readable) | Med | Low | Describe by feature category only; no copying | — | 0 |
| R-13 | Toolchain drift: AGP 9.4/Gradle 9.6/Kotlin/Compose versions incompatible when assembled | Med | Med | Pin from live sources in Phase 1, keep `libs.versions.toml`, run CI before claiming green | CI run | 1 |
| R-14 | Room 3.0 migration pressure | Low | Low | Stay on 2.8.x; schedule migration separately | — | Later |
| R-15 | User data loss on uninstall if profiles only in app-specific storage | Med | Med | SAF export/backup prompt, versioned schema | Test | 3 |
| R-16 | Pairing code / ADB keys leak or are shared (cf. xswl369 shared key) | Low | **High** | Per-install generated key in Android Keystore/private storage, never log codes | Security review | 1 |
| R-17 | Foreground-service / battery limits kill helper or capture process | Med | Med | Foreground service with notification; document OEM battery settings | Device | 1 / 6 |
| R-18 | Wi-Fi-dependent Option B unusable without network / on captive or client-isolated Wi-Fi | Med | Med | Document; Android 17 trusted-network feature may help (unverified) | Device | 1 |
| R-19 | Misleading UI ("connected" shown without proof) | Low | High | D-013 honesty rule; status derived from live probes only | Review | All |

## Top risks to resolve first (Phase 1 spikes)
R-01, R-02, R-04, R-08.
