# InputBridge Architecture — Existing Phase 1 Repository

The repository is intentionally continued in place. There is no separate Phase 2 project and no new production input-mapping engine has been declared complete.

```mermaid
flowchart TD
    UI[Compose Material 3 UI] --> NAV[AndroidX Navigation Compose]
    UI --> SETTINGS[UiPreferencesRepository]
    SETTINGS --> DS[(DataStore: theme only)]
    UI --> ACT[MainActivity: lifecycle coordinator]
    ACT --> SHI[Shizuku UserService probe]
    ACT --> ADB[Direct ADB pairing/connect bootstrap]
    SHI --> HELPER[Shared bounded helper protocol]
    ADB --> HELPER
    HELPER --> TARGET[Separate InputBridge Test Target]
    ACT --> HISTORY[ProbeHistoryRepository]
    HISTORY --> ROOM[(Room: fixed event enums + timestamp)]
    TARGET --> EVIDENCE[Manual target event verification]
```

## Boundaries

- `:lab` contains the activation-feasibility UI, Shizuku/ADB probes, preferences, and history.
- `:input-target` remains a separate Android application so it can independently record events actually delivered to its Activity.
- UI status text must not convert a successful pairing, shell identity, helper UID, or helper response into an input-delivery PASS.
- The Room table stores `provider`, `action`, `outcome`, and `occurredAtMillis` only. Retention is bounded to the newest 100 rows.
- DataStore stores only the user's dark/light theme choice. The temporary pairing code uses transient Compose state and is cleared when Pair is pressed.
- The current `MainActivity` still owns platform integration and action coordination. A later, separately reviewed change can extract provider lifecycle into testable state holders after the latest source has a green CI baseline.

## Build/runtime boundary

The source was structurally inspected in this environment, but no Android SDK or Gradle executable is installed. GitHub Actions must run lint, unit tests and both debug assemblies before this architecture update can be considered build-verified.
