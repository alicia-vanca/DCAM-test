# Source structure and requirement evidence report

> **Historical snapshot.** This report preserves evidence from its audit date. Do not use its package names or architecture conventions for new code. Use [`ARCHITECTURE.md`](../../../app/src/main/java/com/dvid/dcam/ARCHITECTURE.md) and [`FEATURE_DEVELOPMENT_GUIDE.md`](../../../app/src/main/java/com/dvid/dcam/FEATURE_DEVELOPMENT_GUIDE.md) instead.

Audit date: **2026-07-09** (Asia/Saigon)
Repository baseline: working tree based on commit `dcd1082` (`Update document`) plus local feature, auth, data, and module-boundary changes
Documentation baseline: broadly refreshed on **2026-07-08** and selectively refreshed on **2026-07-09** with the Approved 1.1 Architecture Delivery Profile. This evidence report has not been fully re-audited against every draft design.

## Executive conclusion

The current project **substantially satisfies the documented architecture direction**, but it **only partially satisfies the MVP functional baseline** and does **not yet prove MVP acceptance or production readiness**.

The strongest evidence is a standardized feature-first Clean Architecture/Ports-and-Adapters structure with explicit `domain`, `application/usecase`, `application/port`, `presentation`, and outer `platform` responsibilities. Gradle now enforces the inward `:app` → `:core` dependency, while source-level architecture tests enforce canonical package paths, boundary-interface placement/naming, dependency direction, implementation `Impl` naming, and approved source ownership across both modules. Capture is local-first, physical and touch controls share the same use-case interfaces, configuration falls back to safe local defaults, and online log upload is asynchronous and optional.

The source implements the directly supported Data Contract media artifact-shape subset that remains in Data Contract 1.6, plus local project-control features: media folders, contract filenames, important-media mapping, AAC output, the active `logs.txt` filename, feature-gated menu/hardware-key behavior, local language preference, gated remote diagnostics upload, and local AES-256-CTR media transforms. It deliberately does not invent the still-undefined physical roots, config schema migration, MP4 sidecar content, DB operational schema, app/contract metadata, key-management policy, or BDMA decryption behavior. Product-critical gaps remain: Internal/External/Auto behavior, device-information-only CSON, MP4 MD5 generation, embedded metadata/database schema, persisted media lifecycle, low-storage policy, GPS capture, final encryption/key design, process-death recovery, BDMA import/cleanup/write-back proof, and a real BodyCamera compatibility matrix. Android Device Operation adds approved boot/launcher/kiosk/power/recovery expectations; existing permission/foreground behavior is present, while exact dedicated-screen/kiosk behavior remains open and Android system bars are intentionally visible for device indicators.

### Verdict at a glance

| Question | Finding |
|---|---|
| Is the code modular? | **Yes at package/layer level and partially at build level.** Gradle compiler-enforces `:app` → `:core`; architecture tests enforce the remaining app/feature/platform boundaries inside `:app`. |
| Is it expandable? | **Yes structurally.** Feature slices, use cases, explicit application boundaries, platform implementations, and composition give clear extension seams without speculative source scaffolds. |
| Is core logic tied to one hardware implementation? | **Mostly no, but portability is not yet proven.** Feature application/domain code is Android- and vendor-independent; `AppComposition` selects the current CameraX adapter. Real devices and vendor fallbacks remain untested. |
| Is core operation tied to online services? | **No for current capture/storage.** The capture flow contains no cloud dependency. Loggly upload is feature-gated, token-gated, network-constrained, queued, and asynchronous. Diagnostics are nevertheless still concretely Loggly-oriented. |
| Does the project satisfy the full requirement baseline? | **No.** Architecture alignment is strong; functional MVP coverage is partial; acceptance targets need missing implementation plus device/BDMA/QA evidence. |

## Scope and evidence rules

This audit compares the source to:

- [MVP requirement baseline](../../dcam-knowledge/confluence-summary/01-requirements/mvp-baseline.md)
- [System and application architecture](../../dcam-knowledge/confluence-summary/02-architecture/system-architecture.md)
- [Data, storage, and DCAM-BDMA boundary](../../dcam-knowledge/confluence-summary/02-architecture/data-and-bdma.md)
- [Cloud, quality, and security direction](../../dcam-knowledge/confluence-summary/02-architecture/cloud-quality-security.md)
- [Android development standard](../../dcam-knowledge/confluence-summary/03-development/android-standard.md)
- [Current repository state](../current-repo/current-state.md)

Confluence now has Approved Data Contract 1.6 plus a Requirements Home and functional-requirement pages, including Approved Android Device Operation Requirements 1.8 and System Settings Requirements 1.13; Non-functional Requirements and Technical Design draft pages were also updated on July 8. The IDs in this report are local audit labels, not Confluence requirement IDs.

Status meanings:

| Status | Meaning |
|---|---|
| **Verified** | Direct source evidence and, where applicable, an automated check support the claim. |
| **Partial** | A meaningful implementation exists, but a required part or acceptance proof is missing. |
| **Scaffolded** | A boundary or placeholder exists without usable feature behavior. |
| **Gap** | No implementation evidence was found for the required behavior. |
| **External proof needed** | Source inspection cannot prove the claim; device, BDMA, performance, security, or QA evidence is required. |

## Actual source structure

The repository contains two Gradle modules, 145 production Java files, 21 local unit-test files, and 55 `@Test` methods at the updated working-tree baseline.

```text
:app
└── com.dvid.dcam
    ├── app/                          Entry point and composition; UI code lives under app/ui
    ├── feature/                      Domain/application vertical slices
    └── platform/                     Android/hardware/filesystem/provider implementations

:core
└── com.dvid.dcam.core/               Shared pure-Java config/feature/logging capabilities
```

| Boundary | Direct evidence | Assessment |
|---|---|---|
| Build/module | [`settings.gradle`](../../../settings.gradle) includes `:app` and `:core`; [`app/build.gradle`](../../../app/build.gradle) depends on the pure-Java library configured by [`core/build.gradle`](../../../core/build.gradle). | The approved smaller Phase 1 shape is implemented. Gradle compiler-enforces that core cannot depend outward on Android/app/feature/platform code. |
| App shell | [`MainActivity`](../../../app/src/main/java/com/dvid/dcam/app/MainActivity.java) renders ViewBinding screens; [`MainViewModel`](../../../app/src/main/java/com/dvid/dcam/app/ui/MainViewModel.java) exposes immutable cross-feature UI state. | Matches MVVM direction while keeping app lifecycle/navigation outside feature logic. |
| Domain/application | [`SerializedRecordingCoordinator`](../../../app/src/main/java/com/dvid/dcam/feature/capture/application/usecase/SerializedRecordingCoordinator.java), [`PhotoCaptureUseCase`](../../../app/src/main/java/com/dvid/dcam/feature/capture/application/usecase/PhotoCaptureUseCase.java), and [`AudioRecordingUseCase`](../../../app/src/main/java/com/dvid/dcam/feature/capture/application/usecase/AudioRecordingUseCase.java) depend on application boundaries such as [`CameraGateway`](../../../app/src/main/java/com/dvid/dcam/feature/capture/application/port/CameraGateway.java) and [`AudioRecorder`](../../../app/src/main/java/com/dvid/dcam/feature/capture/application/port/AudioRecorder.java); capture values remain under `domain`. | Use case, boundary, and domain ownership are explicit and platform-independent. Current use cases remain thin delegation rather than rich policy. |
| Platform implementations | [`CameraXCameraGatewayImpl`](../../../app/src/main/java/com/dvid/dcam/platform/camera/CameraXCameraGatewayImpl.java), [`AndroidAudioRecorderImpl`](../../../app/src/main/java/com/dvid/dcam/platform/audio/AndroidAudioRecorderImpl.java), [`AndroidDeviceRepositoryImpl`](../../../app/src/main/java/com/dvid/dcam/platform/device/AndroidDeviceRepositoryImpl.java), [`LocalMediaRepository`](../../../app/src/main/java/com/dvid/dcam/platform/storage/LocalMediaRepository.java), and [`AppLogger`](../../../app/src/main/java/com/dvid/dcam/platform/logging/app/AppLogger.java) implement application/core boundaries. | Android/provider code is visibly concentrated under `platform`. |
| Composition | [`AppComposition`](../../../app/src/main/java/com/dvid/dcam/app/AppComposition.java) selects adapters and repositories and creates the process-owned capture adapters and Activity-bound preview runtime. | Concrete selection is explicit and no longer mixed into Activity rendering. Alternate adapter selection and composition tests remain future work. |
| Boundary enforcement | [`LayerDependencyTest`](../../../app/src/test/java/com/dvid/dcam/architecture/LayerDependencyTest.java) checks dependency direction, canonical layer paths, public-port placement/naming, and approved top-level package families. | The intended architecture is executable rather than dependent on reviewer interpretation. |

One transitional leak is correctly confined to platform code: [`DcamMediaOutput`](../../../app/src/main/java/com/dvid/dcam/platform/storage/DcamMediaOutput.java) exposes CameraX output types to camera/storage adapters. Those types do not enter domain, application, feature adapters, or app-presentation contracts.

## Verified runtime flows

### Video, SOS, and image capture

```text
touch control ─┐
               ├─> MainViewModel
physical key ──┘       -> capture use case
                       -> CameraGateway
                       -> CameraXCameraGatewayImpl
                       -> DcamMediaOutput -> local file or MediaStore
                       -> CaptureEvents
                       -> immutable MainUiState -> rendered UI
```

Evidence:

1. [`HardwareButtonRouter`](../../../app/src/main/java/com/dvid/dcam/app/ui/input/HardwareButtonRouter.java) maps BodyCamera keys to the same capture use cases used by touch UI; the router has focused unit tests.
2. [`MainViewModel`](../../../app/src/main/java/com/dvid/dcam/app/ui/MainViewModel.java) delegates photo/video/SOS/audio actions to feature use cases and maps domain events to UI state.
3. [`SerializedRecordingCoordinator`](../../../app/src/main/java/com/dvid/dcam/feature/capture/application/usecase/SerializedRecordingCoordinator.java) serializes video/SOS commands and capture event state.
4. [`CameraXCameraGatewayImpl`](../../../app/src/main/java/com/dvid/dcam/platform/camera/CameraXCameraGatewayImpl.java) maps CameraX start/finalize/photo/error callbacks to `CaptureEvents`, serializes the SOS handoff by finalizing the active recording first, and starts/stops [`RecordingForegroundService`](../../../app/src/main/java/com/dvid/dcam/platform/recording/RecordingForegroundService.java).
5. [`DcamMediaOutputImpl`](../../../app/src/main/java/com/dvid/dcam/platform/storage/DcamMediaOutputImpl.java) translates logical media descriptors into app files or Android MediaStore operations.

This proves an implemented application flow. It does not prove the documented 99% success target, interruption recovery, or operation on every target BodyCamera.

### Device identity and runtime preferences

```text
device identity store
    -> device serial
    -> media filename and AppLogger

application properties
    -> BuildConfig
    -> media encryption password

SharedPreferences + feature gate
    -> runtime media encryption setting
```

The runtime no longer loads the legacy aggregate configuration. Device serial, build-time crypto configuration, operator session, and runtime feature preferences have separate owners.

### Device status

```text
AndroidDeviceRepositoryImpl -> DeviceRepository
    -> RefreshDeviceStatusUseCase -> MainViewModel -> MainUiState
```

[`AndroidDeviceRepositoryImpl`](../../../app/src/main/java/com/dvid/dcam/platform/device/AndroidDeviceRepositoryImpl.java) currently reads battery percentage, available app-storage bytes, and GPS capability/enabled status. It handles missing services/runtime failures with explicit unknown/unavailable states. It does not acquire GPS coordinates or cover network, GMS, USB, firmware, camera, or microphone capability in the status model.

### Local-first and optional online diagnostics

```text
capture/audio implementation -> Logger -> AppLogger -> RoomLogWriter
                                      ├─> Logcat + internal Logs/logs.txt
                                      └─> Room outbox
                                            -> WorkManager (network required)
                                            -> Loggly only when feature gate and token allow it
```

[`AppLogger`](../../../app/src/main/java/com/dvid/dcam/platform/logging/app/AppLogger.java) writes Logcat and local file output before queuing remote diagnostics. [`RoomLogWriter`](../../../app/src/main/java/com/dvid/dcam/platform/logging/app/RoomLogWriter.java) persists events, and [`LogglyUploadScheduler`](../../../app/src/main/java/com/dvid/dcam/platform/logging/loggly/LogglyUploadScheduler.java) schedules upload with a connected-network constraint. Logging is always-on infrastructure rather than a Cloud settings feature; [`LogglyUploadJobService`](../../../app/src/main/java/com/dvid/dcam/platform/logging/loggly/LogglyUploadJobService.java) exits successfully when no token is configured. Therefore Internet/Loggly is not on the capture success path. However, the diagnostics implementation and naming are provider-specific, so general provider neutrality is incomplete.

### BDMA boundary

DCAM now produces contract-shaped media folders and names inside the existing prototype root selection. [`DcamStorage`](../../../app/src/main/java/com/dvid/dcam/platform/storage/DcamStorage.java) creates `Media/{Video,Image,Audio,IMP}`, [`DcamFileName`](../../../app/src/main/java/com/dvid/dcam/platform/storage/DcamFileName.java) creates `DCAM_...[_IMP][_enc]` names, [`BodycamMediaCrypto`](../../../app/src/main/java/com/dvid/dcam/platform/storage/BodycamMediaCrypto.java) applies local AES-256-CTR transforms when the gated preference is enabled, and [`LocalMediaRepository`](../../../app/src/main/java/com/dvid/dcam/platform/storage/LocalMediaRepository.java) restricts browsing to those four roots and blocks path traversal. Optional MP4 MD5 generation and retry are implemented behind the `VIDEO_MD5` gate.

There is no BDMA-side code in this repository and no contract-aligned embedded metadata record for BDMA to validate. Media folders/names now match their contract rules and local encryption transforms exist, but storage-mode discovery, MP4 MD5, ADB access, BDMA decryption compatibility, import results, database/config write-back, cleanup, duplicate/retry behavior, and shared fixtures are not demonstrated. Consequently, end-to-end DCAM-BDMA compatibility remains unproven.

## Requirement traceability

### Architecture and quality attributes

| ID | Requirement/direction | Code and test evidence | Status | Finding |
|---|---|---|---|---|
| ARCH-01 | Java-first Android, XML/ViewBinding, MVVM, LiveData, Gradle | [`app/build.gradle`](../../../app/build.gradle), XML layouts under [`res/layout`](../../../app/src/main/res/layout), `MainViewModel`, `MainUiState` | **Verified** | Current stack matches the development standard. |
| ARCH-02 | UI/controller -> use case -> application boundary -> platform implementation | `MainActivity`, `HardwareButtonRouter`, `MainViewModel`, capture/media/device use cases, `CameraGateway`, `AudioRecorder`, repositories, and platform implementations; enforced by `LayerDependencyTest` | **Verified** | The main capture/device/media flows follow the target direction. |
| ARCH-03 | Core business logic independent of Android/vendor/provider APIs | No Android/AndroidX/Google imports in `core` or feature application/domain; architecture test checks this | **Verified** | Core and feature workflows/contracts can be tested without CameraX or a vendor SDK. |
| ARCH-04 | Modular and maintainable structure | Compiler-enforced `:app` → `:core` boundary plus vertical feature slices and architecture tests | **Partial** | Shared pure-Java ownership is compiler-isolated; app/feature/platform remain package/test-enforced and cross-feature presentation still sits in one app/ui ViewModel/Activity shell. |
| ARCH-05 | Hardware capability behind replaceable implementations | `CameraGateway`, `AudioRecorder`, and `DeviceRepository`; CameraX/MediaRecorder/Android implementations remain outside application/domain | **Partial** | Replacing implementations leaves application/domain code mostly unchanged. Runtime selection, vendor fallback, and device matrix are missing; manifest requires a camera. |
| ARCH-06 | Offline-first core; cloud must not block capture | No cloud call in use cases/repositories/camera output; local storage/logging; token-gated WorkManager upload | **Verified** for current capture path | Video, photo, audio, storage, config, and UI do not require an online service. Metadata/BDMA readiness is still absent, so the entire offline MVP is not complete. |
| ARCH-07 | Provider-neutral cloud/config/update integration | No provider-neutral cloud/config/update boundary is active; build properties, device identity, and runtime preferences have separate owners; the concrete Loggly worker is isolated in logging and gated at runtime | **Gap** | Cloud/update boundaries should be introduced only with approved behavior; diagnostics remain Loggly-specific even though upload enablement is now gated. |
| ARCH-08 | Configuration over hardcoding with safe defaults | Device identity store, build properties, feature-gate preferences, language preference, and media-encryption preference | **Partial** | Device identity and local preferences are implemented. Cloud provisioning, DB-backed operational settings, Internal/External/Auto behavior, Camera FHD quality, key mappings, and provider behavior still need designs. |
| ARCH-09 | Long/blocking work off UI; serialized hardware work | Single-thread media browser executor, Room, WorkManager; CameraX executor/callback contract | **Partial** | Some threading choices conform. No performance/ANR evidence, priority tests, or vendor `HandlerThread` implementation exists. |
| ARCH-10 | Testability through interfaces/fakes | Architecture, storage, browser, input, config, UI-state, DB-manifest, feature-gate/encryption, and log-worker unit tests | **Partial** | Boundary tests exist, but capture use cases/repository/camera error flows lack broad fake-based tests and instrumentation coverage is only a placeholder. |
| ARCH-11 | Versioned database evolution | Central Room manifest, exported schemas 1/2, explicit `V1_TO_V2`, DB manifest test | **Verified** for log DB | Database migration discipline exists. It does not provide the required media metadata schema or DCAM-BDMA schema version. |
| ARCH-12 | Security-aware local access and secret handling | Local properties are gitignored; operator credentials use bcrypt cost 10 with embedded per-hash salts rather than plaintext; media browser canonicalizes and restricts paths; FileProvider is non-exported; local AES-256-CTR media transform exists behind a preference | **Partial** | Useful controls exist. Unshipped PBKDF2 development credentials require data reset or reprovisioning; auth throttling/default-credential policy, target-device KDF measurement, final media-key management, BDMA decryption, update validation, permission audit, and security testing remain unresolved. |

### MVP behavior and acceptance

| ID | MVP expectation | Current evidence | Status | Missing proof or behavior |
|---|---|---|---|---|
| MVP-01 | Start/stop video and expose recording state | CameraX start/stop/finalize flow; `CaptureState`; foreground-service notification | **Partial** | Real BodyCamera results, 99% target, background/interruption/process-death recovery, and per-file metadata/status. |
| MVP-02 | Capture and save image with state/diagnostics | CameraX `takePicture`, local output, saved/error events, log calls | **Partial** | Real-device success target and per-image metadata/status. |
| MVP-03 | Stable, documented, ADB-readable local structure | Contract media folders/names inside existing app-data/public-DCIM prototype roots | **Partial** | Internal/External/Auto behavior, physical-path/device validation, cross-root discovery, ADB access, retention, and BDMA E2E proof remain. |
| MVP-04 | Metadata with file/device/time/GPS/status/schema version | Device ID and time are available in separate code paths; no metadata model, use case, or port exists | **Gap** | No general per-media metadata record, association, status vocabulary, GPS, `schema_version`, or authoritative store. |
| MVP-05 | Battery, storage, and GPS availability | `AndroidDeviceRepositoryImpl.readStatus` -> repository -> use case -> UI state | **Verified** for these three status indicators | Broader capability catalog and historical/per-media status are future work. |
| MVP-06 | Missing optional capabilities degrade gracefully | GPS status returns unavailable/disabled/unknown; config falls back to defaults; no-token upload succeeds without network | **Partial** | Systematic camera/microphone/storage/network/GMS/USB capability policy and tests. |
| MVP-07 | Local main-flow diagnostics with rotation/retention and safe context | Logcat, internal `Logs/logs.txt`, Room outbox/retry/dead archive; capture start/result/error logs | **Partial** | Add ADB/BDMA read proof, formal retention/privacy policy, broader categories, secret-redaction tests, and full provider abstraction. |
| MVP-08 | Completed/pending/corrupt/recovered source states | CameraX events update transient `CaptureState` | **Gap** | No persisted media lifecycle or recovery/corruption classification. Log outbox states are unrelated to media lifecycle. |
| MVP-09 | Near-full storage is handled safely | Available bytes are reported | **Gap** | No threshold, preflight, warning/prevention/stop policy, or low-storage recording test. |
| MVP-10 | Valid GPS stored with media when available | GPS hardware/enabled state only | **Gap** | Location acquisition, validity policy, timestamping, and media metadata association. |
| MVP-11 | BDMA discovers/imports/validates/maps media and metadata | Contract-shaped local media folders/names; no MD5 or BDMA-side implementation | **Gap / external proof needed** | Define MD5 sidecar content, then provide embedded metadata/schema, fixtures, a BDMA importer, ADB E2E tests, cleanup/write-back, and duplicate/retry/schema-mismatch behavior. |
| MVP-12 | No critical corruption/crashes; record/capture >=99%; BDMA import 100% | Unit tests and successful local build are engineering signals | **External proof needed** | Long-duration real-device, power-loss, near-full storage, performance, crash/ANR, corruption, and BDMA QA results. |

## Current limitations

The architecture is a good foundation, but it should not be interpreted as a completed platform. The following limitations describe the present source, not only missing acceptance paperwork.

### Architecture and maintainability limitations

| Area | Current limitation | Why it matters | Classification |
|---|---|---|---|
| Composition root | `AppComposition` now selects storage, config, logging, device, media, camera, audio, and repository implementations; `MainActivity` retains lifecycle, rendering, and input routing. | Activity coupling is reduced, but adapter selection is still static and `AppComposition` lacks focused composition tests or capability-based selection. | **Improved; incremental follow-up** |
| Camera boundary | `CameraXCameraGatewayImpl` is both a `FrameLayout` visual component and the `CameraGateway` implementation. It owns CameraX binding, recording, capture callbacks, messages, and foreground-service signaling. | UI lifecycle, capture lifecycle, and device implementation cannot evolve independently; headless recovery and vendor SDK substitution become harder. | **Focused refactor before recovery/vendor work** |
| Recording ownership | `RecordingForegroundService` supplies notification/foreground visibility, but `CameraXCameraGatewayImpl`, which is Activity lifecycle-owned, owns the active CameraX `Recording`. | Foreground status alone does not preserve recording across Activity recreation or process death. | **Reliability design and implementation** |
| ViewModel construction | `MainViewModel` receives use cases through `MainViewModelFactory` and remains an app-level cross-feature ViewModel. | It works for one shell, but feature growth can produce a large app-level state object. Feature-specific screens should move state/ViewModel into their feature when lifecycle grows. | **Incremental refactor** |
| Use-case depth | Most capture use cases are still light workflow delegation. | There is currently no application-level location for storage preflight, lifecycle transition rules, metadata finalization, or retry policy. Thin use cases are acceptable now, but policy must not drift into the Activity or platform implementations. | **Future implementation discipline** |
| Build modularity | `:core` compiles as an independent pure-Java library consumed by `:app`. `LayerDependencyTest` scans both modules and checks remaining package/layer rules. | Core cannot acquire Android or outward source dependencies without a build failure; app/feature/platform isolation remains source/test-enforced. | **Approved smaller Phase 1 shape** |
| Future capability catalog | Cloud, remote config, update, metadata, integrity, security, storage-contract, streaming, and PTT remain roadmap items without source boundaries. | This avoids fake abstraction; each capability still needs an approved use case and domain result/error model before implementation. | **Deferred by design** |
| Threading model | Media browsing, Room, and upload work are offloaded, while CameraX uses its main-executor callback contract. No vendor command queue or measured priority policy exists. | A vendor SDK or long-running stateful operation may require serialization; callback execution and UI work need profiling on real hardware. | **Partial implementation** |

### Product and data limitations

| Area | Current limitation | Consequence |
|---|---|---|
| Requirement authority | Data Contract 1.6 is approved and the functional-requirements / technical-design structure has expanded. | The approved contract now overrides conflicting prototype behavior, while draft technical-design details still should not be mistaken for current implementation. |
| Storage contract | Media folders, filename, AAC, active log filename, and local `_enc` transforms follow parts of the current contract media-artifact direction; config, storage modes, physical roots, app/contract metadata, and MP4 sidecars do not. | Several contract areas still need approved design and implementation before BDMA can rely on them. |
| Metadata | No authoritative per-media metadata entity/file contains file ID, device ID, capture time, location, source state, and schema version. | BDMA cannot validate or map evidence without guessing, so the core product handoff is incomplete. |
| Media lifecycle | UI state is transient; completed, pending, corrupt, interrupted, and recovered media states are not persisted. | Restart and recovery logic cannot reliably determine whether a file is ready for import. |
| File integrity | Contract 1.6 defines MP4 MD5 scope/naming, but sidecar content and generation are not implemented; no lifecycle persistence or corruption classification exists. | Contracted verified/unverified import behavior cannot yet operate. |
| Storage safety | Available bytes are displayed, but recording has no documented threshold, preflight decision, reserve, or full-storage transition. | Near-full storage can still cause capture/finalization failure despite status visibility. |
| GPS | The code detects whether GPS exists and is enabled but does not request, validate, timestamp, or associate a location with media. | The MVP GPS metadata requirement is not implemented. |
| Device capability | CameraX is the only camera implementation; physical key codes and FHD selection are fixed prototype behavior; broader camera, microphone, network, GMS, USB, and firmware capabilities are not modeled. | Source abstraction reduces coupling, but compatibility across BodyCamera models is not demonstrated. |
| Configuration | Built-in defaults and local CSON exist; runtime override and remote precedence are absent, and several behavior choices remain hardcoded. | Customer/device variation still requires code changes in some areas. |
| Diagnostics provider | Capture depends on `Logger`, and remote upload is feature-gated, but the outbox, worker, endpoint construction, and class names are Loggly-specific. | Core capture stays offline-capable, but replacing the online diagnostics provider is not yet a pure configuration change. |
| Database scope | Room currently persists the log outbox only. | Versioned database mechanics exist, but media, metadata, lifecycle, device/user mapping, and recovery records do not. |
| Feature UI | Feature-gated navigation, Files, language selection, and media-encryption preference are code-backed; `DemoSettingsState` controls and most menu feature pages remain placeholders. | Navigation and a few local preferences demonstrate structure, not completed feature behavior or operational settings persistence. |
| Security | Local AES-256-CTR media transforms exist, but key ownership/rotation, media protection, update verification, sensitive metadata rules, and BDMA decryption compatibility are unresolved. | Security readiness cannot be inferred from package isolation, secret hygiene, or the local transform alone. |

### Verification limitations

- The 34 local unit tests cover useful boundaries, including architecture rules, storage naming/path rules, legacy config loading, feature gates, local media crypto, and media-browser guards, but not the complete capture repository/use-case flow, CameraX behavior, lifecycle recovery, low storage, GPS, permissions, database migration execution, BDMA decryption, or BDMA integration.
- There is no instrumentation or automated real-device UI/camera suite.
- A successful debug build and unit-test run prove build health, not recording reliability, corruption resistance, battery behavior, performance, or security.
- No audited evidence demonstrates the `>=99%` recording/image target, `100%` BDMA import target, zero critical corruption, or zero critical main-flow crashes.
- No checked-in device/firmware/Android matrix proves operation across the intended BodyCamera range.

## Future improvement directions

Improvements should preserve the existing dependency direction and proceed from contract and reliability work toward optional features. A wholesale rewrite or immediate framework migration is not indicated.

### Direction 1: establish contracts before expanding implementation

1. Turn the implemented Data Contract 1.6 media artifact subset into shared DCAM-BDMA fixtures and integration tasks.
2. Write the physical storage, embedded metadata, SQLite, recording lifecycle/recovery, GPS validity, and security designs for details not fixed by the contract.
3. Record decisions that materially constrain adapters or data using ADRs, especially camera API/fallback, metadata encoding, DB concurrency, encryption, and process-recovery ownership.
4. Expand the initial functional pages into traceable requirement IDs and acceptance tests shared with BDMA and QA.

This prevents the prototype folder layout, speculative source contracts, or current provider choices from becoming accidental contracts.

### Direction 2: build the reliability and data foundation

1. Introduce an authoritative per-media domain record with identity, type, device/operator context, timestamps, optional valid GPS, lifecycle state, schema version, and integrity information defined by the contract.
2. Persist lifecycle transitions before and after fragile file operations so restart recovery can distinguish recording, pending-finalization, completed, corrupt, and recovered artifacts.
3. Add storage preflight and reserve policy before capture; define safe behavior when capacity changes during recording.
4. Finalize media and metadata as one recoverable workflow, even if the underlying filesystem cannot provide a single atomic transaction.
5. Define a location boundary from the approved metadata use case, with explicit unavailable, stale, invalid, and valid outcomes; GPS failure must not abort capture.
6. Add integrity verification and recovery scanning before marking data import-ready.

The exit condition is not merely “files are created”; it is “each artifact has a durable, explainable, BDMA-consumable state after normal completion or interruption.”

### Direction 3: make runtime composition and hardware substitution clearer

1. **Completed structurally:** `AppComposition` now owns concrete dependency creation while `MainActivity` retains Android lifecycle and rendering. Add fake/capability-driven composition tests before claiming runtime substitutability.
2. Separate the preview widget from the camera/capture driver so recording ownership is not inherently tied to a View.
3. Decide whether the foreground service or another lifecycle owner should own long-running recording and recovery, then implement that design explicitly.
4. Provide adapter selection by capability/configuration for CameraX, vendor SDK, and unsupported/fake implementations.
5. Move device-specific key mapping and camera profiles behind validated device capability/configuration adapters.
6. Add contract tests that every camera/device adapter must pass.

This is targeted refactoring around composition and lifecycle—not a rewrite of domain, repository, or use-case layers.

### Direction 4: complete provider and configuration independence

1. Define a provider-neutral asynchronous diagnostics upload boundary below `Logger`.
2. Keep local-only/no-op behavior as a first-class implementation, then place Loggly behind a provider adapter.
3. Implement the documented configuration precedence only after ownership and validation rules are approved: runtime -> remote -> local -> defaults.
4. Reject unsafe remote values locally and preserve capture when remote config, cloud, GMS, or Internet is unavailable.
5. Add update/cloud implementations only behind approved capability contracts; do not route them through capture workflows.

### Direction 5: grow features without weakening boundaries

- Give each approved settings/feature area its own ViewBinding screen, feature state/ViewModel, use cases, and domain contracts where justified. Keep draft/demo controls out of completed-status evidence.
- Keep physical keys and touch actions converging on application commands rather than separate hardware-only business flows.
- Add Device/User foundations before streaming and PTT so identity and authorization do not get embedded in provider SDK callbacks.
- Do not add speculative boundaries; define them from an approved use case and expected domain result/error model.
- Keep the current `:app`/`:core` Gradle shape until another approved trigger—independent lifecycle, dependency isolation, parallel ownership, repeated independent testing, cycle prevention, or a stable implemented interface—justifies extraction. Existing feature-first packages remain candidate boundaries without forcing premature modules.

### Direction 6: turn quality targets into repeatable evidence

| Test/evidence layer | Needed improvement |
|---|---|
| Unit | Fake-boundary tests for use cases/repositories, media state transitions, storage thresholds, GPS validity, config precedence, integrity, and error mapping. |
| Database | Room migration tests from every supported version plus crash/restart consistency tests for media lifecycle data. |
| Adapter/instrumentation | CameraX/vendor contract tests, permissions, Activity recreation, foreground/background behavior, process kill, and FileProvider/ADB exposure. |
| Integration | Shared DCAM-BDMA fixtures for valid, duplicate, partial, corrupt, unknown-schema, interrupted, and repeated-import cases. |
| Hardware | A maintained device/firmware/Android capability matrix and repeated capture, long recording, low-battery, low-storage, GPS, offline, weak-network, reboot, and thermal tests. |
| Quality/security | Startup/capture/storage latency, CPU/memory/battery/ANR measurement, secret/redaction checks, update validation, media protection, and threat review. |

## Refactor timing guidance

| Timing | Recommended action |
|---|---|
| **Now / before major feature work** | Define approved data/lifecycle contracts; add fake-based workflow and composition tests; keep new policy out of UI and adapters. (`AppComposition` extraction is complete.) |
| **Before vendor camera or recovery work** | Separate preview UI from capture ownership and decide the long-running recording lifecycle owner. |
| **Before adding another diagnostics provider** | Introduce a neutral uploader/local-only boundary around Loggly-specific code. |
| **Now that media folder/name and local encryption shape are aligned** | Define remaining MD5/config/storage/key-management details, then create BDMA fixtures/E2E tests and implement metadata, database settings, permissions, decryption validation, import results, cleanup, and recovery. |
| **Later, if an approved trigger appears** | Extract `:media`, `:storage`, or `:bdma-contract`, or introduce a DI framework. None is required merely to match the target diagram. |
| **Do not do yet** | Rewrite working layers, invent ports for unapproved future capabilities, or modularize every package without an ownership/build/enforcement reason. |

## Priority gaps and recommended evidence

| Priority | Required next outcome | Evidence that closes the gap |
|---|---|---|
| P0 | Complete Data Contract 1.6 integration beyond local media artifact/encryption shape | Shared fixtures, ADB discovery, app/contract metadata, embedded media association, schema/lifecycle rules, BDMA decryption validation, cleanup/write-back, duplicate/retry behavior, and joint DCAM-BDMA tests. |
| P0 | Persist media lifecycle and recover interrupted recordings | State-machine tests plus app-kill, reboot/power-loss, corrupt/partial-file, and recovery results on BodyCamera. |
| P0 | Implement storage safety | Configured threshold/policy, preflight checks, safe finalize/stop behavior, and near-full/exhausted-device tests. |
| P0 | Validate target hardware | Device/firmware/Android capability matrix with record, photo, audio, key, storage, lifecycle, and permission results. |
| P1 | Implement valid GPS-to-metadata flow | Fake-location unit tests and unavailable/stale/valid real-device scenarios. |
| P1 | Make adapter selection capability-driven | `AppComposition` exists; add CameraX/vendor/unsupported selections plus adapter contract tests. |
| P1 | Generalize diagnostics provider boundary | Provider-neutral upload contract, local-only implementation, Loggly adapter, and offline/failure tests beyond the current runtime gate. |
| P1 | Expand automated coverage | Fake-port tests for use cases/repositories, CameraX instrumentation, Room migration tests, and UI/permission/lifecycle tests. |
| P2 | Consider additional Gradle modules | ADR/change record tied to an approved split trigger; preserve the established `:app` → `:core` direction and avoid cycles. |

## Verification record

Static verification performed during this audit:

- Confirmed the `:app` and `:core` Gradle project graph and enumerated 145 production Java files, 21 local unit-test files, and 55 test methods.
- Confirmed `core` and feature application/domain do not import Android, AndroidX, Google/provider APIs, `app`, or `platform`; confirmed platform does not import the app shell.
- Reviewed composition, capture, storage, config, feature-gate, media-encryption, device-status, logging/outbox, Room migration, physical-key, and media-browser paths.
- Recorded the evidence against the working tree based on commit `dcd1082`; the current contract-alignment and feature-gate/encryption changes are intentionally local until reviewed.

Build verification command:

```powershell
.\gradlew.bat test assembleDebug
```

Result: **PASS** on 2026-07-09 for the current two-module working tree; `:core` compiled and packaged independently, app unit tests passed, and the debug APK assembled. Automated unit/build success is necessary engineering evidence, but it does not substitute for BodyCamera, BDMA, performance, reliability, security, or QA acceptance evidence.

Android lint verification:

```powershell
.\gradlew.bat lintDebug
```

Result: **last verified PASS** on 2026-07-06 with no lint errors. Lint reported 23 non-blocking warnings (primarily compound-drawable and hardcoded-text suggestions). The final 2026-07-07 re-run was blocked when the sandboxed Gradle wrapper attempted a network download, so the current hard validation is unit tests plus debug assembly.

## Final assessment

The project is on a sound architectural path: dependency direction is visible, tested, and practical; Android/provider behavior is mostly kept at the edges; the core capture path is local-first; and extension points exist for additional hardware and services.

The precise statement supported by evidence is:

> **The current source structure satisfies the main modularity, dependency-isolation, extensibility, and offline-first architecture expectations. The implementation does not yet satisfy the complete DCAM MVP requirements or acceptance criteria because its metadata, lifecycle/integrity, storage-safety, GPS, BDMA, recovery, hardware-validation, and quality evidence are incomplete.**
