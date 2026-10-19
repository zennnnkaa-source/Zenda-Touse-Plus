# Phase Status — Zenda Touse Plus / InputBridge

**As of:** 2026-10-19

| Field | Status |
|---|---|
| Umbrella project | Zenda Touse Plus |
| Product prototype | InputBridge Activation & Input Feasibility Lab |
| Current phase | **Phase 1 — IN PROGRESS / VALIDATION BLOCKED** |
| Phase 0 | **PASS (documents only)**; preserved under `docs/phase0-snapshot/` |
| Source files | Written for Lab + separate test target; Android sources are not compiled here |
| Isolated policy logic check | **PASS** — standalone Kotlin assertions for port/code/shell-command/helper-request allowlists; not a Gradle/JUnit test run |
| Structural audit | **PASS: 0 errors, 1 warning** — validator checks structure only; the warning is the missing Gradle Wrapper set |
| GitHub Actions build / lint / unit tests | **NOT RUN** — requires pushing project to a GitHub repository |
| APK artifacts | **NOT CREATED** in the authoring environment |
| Emulator/device tests | **NOT RUN** |
| Shizuku → target input | **UNVERIFIED-DEVICE** |
| Direct ADB pairing/connect/shell | **EXPERIMENTAL / NOT RUN** |
| Input delivery to target | **UNVERIFIED**; Phase 1 exit gate not met |
| Next permitted phase | **Remain in Phase 1** until exit criteria pass or blocker is documented |

## Phase 1 files and deliverables

- [RESEARCH.md](RESEARCH.md) — current source review and open technical questions.
- [DECISIONS.md](DECISIONS.md) — carried decisions plus Phase 1-specific choices.
- [FEATURE_MATRIX.md](FEATURE_MATRIX.md) — features mapped to honest evidence states.
- [CAPABILITY_MATRIX.md](CAPABILITY_MATRIX.md) — Android version × activation path × backend status.
- [RISK_REGISTER.md](RISK_REGISTER.md) — updated implementation/security/build risks.
- [FEASIBILITY_RESULTS.md](FEASIBILITY_RESULTS.md) — current evidence log and unrun test matrix.
- [TEST_PROTOCOL.md](TEST_PROTOCOL.md) — repeatable emulator/device test plan.
- [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) — dependency provenance/license/security review gates.
- `docs/phase0-snapshot/` contains original Phase 0 source documents unchanged.

## Exact phase gate

Phase 1 remains open until GitHub Actions succeeds and at least one activation path delivers a real input event to the separate test app on a physical Android 11+ device, with a redacted record in `FEASIBILITY_RESULTS.md`. Pairing, shell output, an overlay, or a successful bind is not enough.

The authoring environment had no Android SDK or working Gradle distribution/wrapper, so no local APK can be claimed. GitHub Actions was added as the supported build path; its first run is still required.
