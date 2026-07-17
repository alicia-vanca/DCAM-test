# System and application architecture

## Architecture intent

Architecture Home is current Confluence page version 39, last registry review 2026-07-14. It identifies current Data Contract as official storage/data/integration baseline. Active Build 0.1 profile is controlled by Release & Build Applicability Matrix, Architecture Delivery Profile, and DEC-01-DEC-07 Decision Brief.

The July 8 Technical Design pages add target-direction language for Android operation, kiosk policy, in-app console/settings, recording, storage, SQLite, BDMA integration, provisioning, update, security, sensors, and AI. Treat those pages as draft/expected design intent until implementation and review correct/complete them.

For Build 0.1, architecture must collapse to one recording-first vertical slice. Generic platform layers activate only when needed by capture, finalization, minimum contract outputs, local diagnostics, or BDMA import. Device-owner policy, cloud/provider adapters, advanced auth, update, AI, streaming, and PTT remain outside release-critical path.

## Current consolidated architecture decisions

- DCAM is Android-side evidence producer; BDMA is desktop-side active reader/importer/manager. Android never depends on BDMA being connected to record or finalize data.
- Source media, DB, CSON, and logs have explicit ownership. BDMA may read approved finalized artifacts and controlled write-back fields; it may not modify source media, temp files, checksum content, active runtime state, or local logs.
- Dependency direction points inward. UI/use cases depend on owned domain boundaries; Android, camera, filesystem, SQLite, Firebase, update, and vendor/provider details stay behind adapters.
- Phase 1 begins with `:app` and `:core`; package-first growth is preferred. Maximum recommended Phase 1 modules are `:app`, `:core`, `:media`, `:storage`, and `:bdma-contract`, added only after documented extraction triggers.
- One serialized recording authority owns camera state. Runtime services, hardware keys, UI, recovery, and remote commands cannot create parallel recording state machines.
- Local-first operation is mandatory. Cloud, provisioning, remote config, crash upload, and update providers are optional adapters and cannot block capture/data integrity.
- Feature activation combines setting, capability, permission, policy authority, safety guard, and temporary availability. Unsupported or degraded behavior is explicit, never silently assumed.
- Device identity separates `serial_number`, `dcam_cloud_device_id`, and recovery lookup `android_id_hash`; no raw Android identifier becomes business identity.
- Production dedicated-device direction uses local Device Owner/DPC plus Lock Task where supported; missing required authority produces controlled policy-required/degraded state.
- Security protects credentials, identity, config, update, and media boundaries. Exact encryption algorithms, keys, rotation, and BDMA decryption remain security-profile decisions, not invented defaults.

The [Architecture Delivery Profile](https://ducviet.atlassian.net/wiki/spaces/DVID/pages/50626744) is **Approved 1.1** (page version 4, updated 2026-07-09). It is the guardrail for converting that larger target architecture into current implementation work: Phase 1 stays deliberately small and proves a runnable recording/storage/BDMA slice before platform expansion.

DCAM is an offline-first, modular, hardware-aware Android application. Core business logic should remain independent of Android APIs, BodyCamera vendor SDKs, camera SDKs, and cloud providers.

The current high-level context is:

```text
Hardware / Android platform
    Camera, microphone, GPS, storage, battery, network, USB
        -> through platform adapters
DCAM application
    UI/controller -> use case -> application boundary <- platform implementation
        ->
Local media / metadata / DB / logs
        -> ADB read initiated by desktop
BDMA Desktop

Optional cloud/config/diagnostics providers sit behind application-owned output ports and may be absent.
```

## Mandatory architectural principles

- **Offline first:** recording, capture, local storage, metadata, logging, basic device status, and BDMA-readiness do not require Internet or cloud.
- **Reliability first:** data preservation and explicit failure state take priority over secondary work or visual polish.
- **Capability based:** detect what the specific BodyCamera can do; do not assume all models, Android versions, firmware, GMS, sensors, or storage behave alike.
- **Platform abstraction:** business logic must not call Android/vendor/cloud SDKs directly.
- **Cloud-provider abstraction:** Firebase may be an implementation but is not the architecture.
- **BDMA-compatible by design:** source data is deterministic, versioned, and contract-driven.
- **Configuration over hardcoding:** device/customer/environment-sensitive behavior comes from safe config layers.
- **Observability:** important flows expose meaningful state and diagnostics.
- **Backward compatibility:** metadata and Data Contract evolution should be versioned.
- **Security awareness:** least permission, secret hygiene, controlled logs, protected update/config/data.

## Standard layers and dependency direction

| Layer | Responsibility | Forbidden dependency/behavior |
|---|---|---|
| Domain | Entities, immutable values, enums, and pure business rules | Android, UI, database, filesystem, vendor, or provider APIs |
| Application | Use cases plus application-owned capability/repository boundaries | Android/framework APIs and concrete implementations |
| Interface Adapter | Translate UI/storage/provider input/output and implement application boundaries | Core product policy and direct dependency from application/domain |
| Frameworks & Drivers | Android, CameraX, MediaRecorder, Room, filesystem, network, vendor SDK | Product policy that belongs in domain/application |

The source uses feature-first Clean Architecture with Ports and Adapters. MVVM is the presentation pattern at the UI boundary; it does not replace or conflict with the four dependency layers. A Java `interface` is placed by ownership: use-case interfaces live in `application/usecase`; repository/hardware/provider boundaries live in `application/port`; concrete implementations live in `platform` or an application repository package. The folder keeps the architecture term `port`, but class/file names use capability names such as `CameraGateway`, not a `Port` suffix.

## Current source organization

| Package family | Responsibility |
|---|---|
| `app` | Android entry point, navigation, cross-feature presentation, and composition root |
| `feature/<name>/domain` | Feature-owned entities and pure rules |
| `feature/<name>/application` | Use cases and application-owned capability/repository boundaries |
| `feature/<name>/presentation` | Feature-owned UI state/ViewModel when a feature needs its own presentation |
| `platform` | Android, hardware, storage, database, and provider adapters |
| `core` | Deliberately shared capabilities using the same domain/application vocabulary |

The repository currently uses the approved smaller two-module start: Android application `:app` plus pure-Java library `:core`. Gradle enforces `:app` → `:core`; source-level architecture tests continue to enforce the app/feature/platform boundaries inside `:app`.

## Phase 1 Gradle and delivery profile

The approved profile limits Phase 1 to **at most five Gradle modules**. Its recommended shape is:

```text
:app
:core
:media
:storage
:bdma-contract
```

A smaller initial `:app`/`:core` shape is explicitly accepted. Organize recording, capture, storage, database, BDMA, identity, logging, and device ownership as packages first; the package examples in Confluence are logical guidance and do not by themselves require renaming the repository's existing `com.dvid.dcam` namespace.

Extract a package into its own Gradle module only when at least one approved trigger exists: independent lifecycle ownership, platform/vendor/cloud dependency isolation, parallel-team ownership, a repeatable independent testing boundary, dependency-cycle prevention, or a stable interface backed by a real implementation. A future capability appearing in a design document is not sufficient.

Before adding another architecture layer or module, demonstrate the working slice: build and run the APK on the selected BodyCamera, record at least 30 seconds, capture an image, finalize media, emit the minimal DB/config/log outputs, and let BDMA detect/import the sample. An exception is allowed only when the addition directly blocks recording, storage, BDMA ingest, device POC, or release safety.

## Boundary and capability rule

Current approved boundaries include use cases such as `PhotoCaptureUseCase`, `VideoRecordingUseCase`, `AudioRecordingUseCase`, `CaptureEventUseCase`, `BrowseMediaUseCase`, `OpenMediaUseCase`, and capability/repository boundaries such as `CameraGateway`, `AudioRecorder`, `DeviceRepository`, `MediaRepository`, `MediaOpener`, `ConfigurationSource`, `ConfigurationRepository`, `LanguagePreferenceStore`, and `LogSink`. Names describe the application capability, never the current library or vendor. Concrete implementations end with `Impl`, for example `CameraXCameraGatewayImpl` and `DcamLogSinkImpl`.

Do not reserve future architecture with empty `*Service` interfaces. Location, metadata, integrity, cloud, update, streaming, PTT, and other future capabilities enter source only after an approved use case defines domain inputs/results/errors and demonstrates the need for a replaceable boundary. Do not add a generic event bus, domain-event publisher, or cross-feature application-event dispatcher until a concrete metadata/media lifecycle/cloud workflow needs decoupled side effects. Each BodyCamera or provider-specific implementation belongs behind a platform implementation. A vendor SDK change should primarily change that implementation, not UI, ViewModel, use cases, or domain.

## Threading model

- Stateful, fragile, call-order-sensitive hardware/vendor SDK operations should use a dedicated `HandlerThread` or the SDK-required serialized thread.
- File I/O, parsing, checksums, ordinary database work, and background helpers use `ExecutorService` or a library-owned executor.
- Retrofit/network uses its managed execution unless a specific helper needs another executor.
- UI state returns to the main thread through ViewModel/LiveData.
- Logging, cloud, metrics, and background work may not degrade recording.

## Android compatibility strategy

- Documentation direction: Android 7.0+ if device/SDK permits; final SDK policy and device matrix TBD.
- Camera direction: CameraX first, Camera2/vendor fallback after real-device POC.
- Centralize permission handling for camera, microphone, location, storage/media, network, notifications, and version-specific behavior.
- Support GMS and non-GMS devices.
- Treat GMS as a capability relevant to some SDKs, not a requirement for all cloud access.
- REST/HTTPS and BDMA/backend-side cloud work can operate without Android GMS.
- Gracefully degrade when GPS, network, cloud, or other optional capability is unavailable.

## Architecture decision status

Decided direction: Java-first, offline-first, capability-based operation, feature-first Clean Architecture, application-owned ports/platform adapters, serialized hardware access where needed, cloud abstraction, BDMA compatibility, ADB boundary, DCAM-producer/BDMA-consumer ownership, and a phase-scoped delivery profile capped at five Gradle modules in Phase 1.

Decided by Data Contract 1.6: logical storage layout, Internal/External/Auto selection, media formats and naming, `_IMP`/`_enc` suffixes, MP4-only MD5 behavior, app/data/media/encoder contract metadata, no dynamic `bdma_decoder_profile_id`, device-information-only `dcam_config.cson`, broader `dcam.db` ownership, BDMA import/write-back boundaries, and post-import cleanup.

Still pending: physical device paths, exact embedded metadata and SQLite table details, DB concurrency/write protocol, final camera API, which approved Gradle extractions are actually justified and when, source lifecycle/error model, duplicate/retry recovery behavior, Device Owner/DPC POC, maintenance credential policy, streaming/PTT protocols, encryption/key management/decryption detail, and detailed update mechanism.
