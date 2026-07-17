# Android development standard

Source status: current Confluence page version 14, last registry review 2026-07-14.

Delivery overlay: [DCAM Architecture Delivery Profile](https://ducviet.atlassian.net/wiki/spaces/DVID/pages/50626744) is current page version 6, reviewed 2026-07-14. It determines which target-architecture rules are mandatory in Phase 1 and which activate only when corresponding later-phase feature enters scope.

## Target stack from the documentation

| Concern | Standard/direction |
|---|---|
| Language | Java-first |
| UI | XML layouts + ViewBinding |
| Architecture | Feature-first Clean Architecture + Ports and Adapters; MVVM at the UI boundary |
| UI state | LiveData |
| Ordinary background work | ExecutorService |
| Stateful hardware/vendor SDK calls | HandlerThread or SDK-required serialized thread |
| REST/API | Retrofit if required |
| JSON | Gson or Jackson; undecided |
| Database | SQLite direction; Room vs direct strategy still needs contract/design confirmation |
| Dependency injection | Not mandatory; introduce only when complexity justifies it |
| Build | Gradle |

## Core dependency rule

```text
Activity/Fragment/controller
    -> application use case
    -> application boundary (for example Repository, CameraGateway)
    <- platform/application implementation
    -> Android/vendor/cloud framework
```

Dependency direction always points inward. UI, ViewModel, and use-case code must not call camera, hardware, storage, Retrofit, Firebase, or other provider SDKs directly. Application code depends only on domain values and boundaries it owns. Platform implementations translate external behavior into those boundaries.

For the MVP paths currently in scope, the delivery profile makes the recording/capture controller, camera adapter, storage boundary, database/repository boundary, BDMA Data Contract, safe logging, and explicit disabling of unsupported optional features the P0 rules. Feature-specific managers and coordinators become mandatory when their feature enters implementation scope; they are not prerequisites for the first working recording slice.

`Interface Adapters` is a Clean Architecture layer name; it does not mean a folder for Java `interface` declarations. Public feature/core interfaces live only in `application/usecase` or `application/port`, and must represent a real use-case or capability boundary. The package may be named `port`, but class/file names do not use the `Port` suffix. Use capability names such as `CameraGateway`, `AudioRecorder`, `MediaOpener`, `LanguagePreferenceStore`, `ConfigurationSource`, and `LogSink`; keep `Repository` for repository contracts. Concrete implementations must end with `Impl`.

Canonical feature package shape:

```text
feature/<feature>/
    domain/
    application/
        usecase/
        port/
        repository/
    presentation/
```

## Threading rules

| Work | Expected mechanism |
|---|---|
| Camera open/close/start/stop | Dedicated HandlerThread or SDK-required executor |
| Stateful BodyCamera/vendor command | HandlerThread unless vendor requires another thread |
| PTT/streaming SDK command | Serialized SDK-required thread |
| File/metadata/checksum work | ExecutorService |
| Database operation | Database/Room executor or ExecutorService |
| Retrofit request | Retrofit-managed execution |
| UI update | Main thread via ViewModel/LiveData |

Do not put fragile hardware commands into a general thread pool. Do not expose raw vendor callbacks or status objects to UI.

## State and error rules

- ViewModel exposes clear, UI-oriented states such as idle, preparing, recording, stopping, completed, and error.
- Map raw SDK exceptions/status codes at adapter boundaries into application/domain results.
- Use stable error categories: permission, unsupported capability, SDK, storage, metadata, network/cloud, and unknown/diagnostic.
- Do not throw raw vendor exceptions through multiple layers.
- Important failures carry enough safe context for diagnostics.

## Storage, metadata, and cloud rules

- UI/ViewModel never knows physical folder paths or writes business files directly.
- Use cases depend on domain models, not filesystem details.
- Temporary/final data must be distinguishable.
- The approved Data Contract governs logical roots, folders, media naming, MD5 scope, BDMA permissions, import results, and cleanup. The future Storage/Database/Security designs must fill its explicitly open implementation details.
- Cloud calls go through provider-neutral output ports defined from approved use cases.
- Core recording/capture/storage/metadata and BDMA readiness remain fully local-capable.

## Pull-request checklist

- No direct SDK access from UI, ViewModel, or use case.
- Application logic depends on owned boundaries; vendor/provider implementation remains in platform/application implementation classes.
- Stateful hardware access is serialized.
- Long work does not block UI.
- UI state is observable and domain-oriented.
- SDK errors are mapped.
- Critical success/error paths are logged without sensitive data.
- Cloud is abstracted and optional.
- Storage/metadata details do not leak upward.
- Tests use fakes at application boundaries where practical.
- Jira issue is linked when applicable.

## Build 0.1 implementation evidence

- Implement against NCC-036V / Android 12/API 31 reference profile without claiming untested device compatibility.
- Keep camera integration behind Android platform boundary; do not add vendor SDK because Decision Brief marks it not applicable for Build 0.1.
- Use internal storage only for active profile. Preserve staging/final artifacts on failure; never expose partial media to BDMA.
- Persist fixed `B01OPR` / `Build 0.1 Operator` consistently where required; do not build login/auth framework for this build.
- Finalize MP4, compute MD5 off main thread, persist checksum state, then publish `BDMA_READY`. Missing/mismatch digest blocks import for affected item.
- Record battery, internal free space, and GPS availability state. Coordinates/routes and continuous tracking are outside acceptance.
- Attach Jira, PR/build, QA Test ID, logs, generated artifacts, and identifiable physical-device evidence. Unit tests alone do not close Pending Device POC.

## Training context

The team is expected to come from Java/JavaFX/Desktop and BDMA knowledge. The two-week onboarding covers Android Studio/Gradle/ADB, Java-on-Android mindset, Activity/Service/Foreground Service lifecycle, permissions, CameraX/Camera2, storage, GPS, network basics, Logcat/debugging, and deployment to real BodyCamera hardware.

Training exit means each developer can build/install/debug the app, run capture and recording samples, handle runtime permissions, perform basic storage I/O, understand lifecycle/background constraints, and work through the Git/Jira workflow.

## Architecture evolution: current decisions and future triggers

Keep the current two-module, single-composition design while it remains easy to scan. Do not add abstractions only to match a future architecture diagram.

Current ownership rules:

- `MainActivity` owns the Activity-scoped capture runtime and hardware command routing.
- A retained `MainViewModel` may retain UI state, but it must rebind to the current Activity's capture event source and must not retain an old Activity-scoped camera/audio runtime.
- `RecordingForegroundService` currently provides foreground visibility only. It does not own or recover recording.

Future work must be triggered by a concrete requirement:

| Trigger | Then consider |
|---|---|
| Recording must survive Activity loss or configuration recreation | Move recording ownership to a durable runtime/foreground service and define recovery behavior |
| Streaming, recording, and preview need the camera concurrently | Separate camera runtime ownership from preview attachment |
| A second vendor/model needs different hardware behavior | Add a capability-specific platform implementation selected in composition |
| A feature gains independent loading, error, navigation, or background state | Give that feature its own presentation state/ViewModel |
| `AppComposition` becomes difficult to scan or owns multiple independent runtime graphs | Delegate construction to feature-level composition helpers |
| Configuration develops several cohesive groups | Introduce typed configuration sections for those real groups |
| A package gains independent lifecycle ownership, isolated platform/vendor/cloud dependencies, parallel-team ownership, repeated independent testing needs, dependency-cycle risk, or a stable interface with a real implementation | Evaluate extraction within the approved Phase 1 limit of five Gradle modules |

Avoid a generic event bus, generic device adapter, empty vendor packages, placeholder ViewModels, or a custom scope framework without those triggers.

The approved Phase 1 Gradle recommendation is `:app`, `:core`, `:media`, `:storage`, and `:bdma-contract`, while a smaller initial `:app`/`:core` structure is accepted. The repository now uses that smaller two-module structure. Keep remaining capabilities package-first and do not split another module solely because a future design page names it.
