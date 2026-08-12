# Current repository state

This page describes the refactored code visible on 2026-07-28. It records implementation reality against current Confluence. Build 0.1-specific rules come from the Decision Brief and Release & Build Applicability Matrix; this page is not a substitute for formal requirements.

## Build and platform

- Two Gradle modules: Android application `:app` (namespace/application ID `com.dvid.dcam`) and pure-Java library `:core`.
- `:app` depends on `:core`; the core module has no Android or outward app/feature/platform dependency.
- Java-only build; Java 17 source/target and Java 21 Gradle toolchain.
- Android Gradle Plugin 9.2.1; min SDK 26, target SDK 36, compile SDK 36.1.
- CameraX 1.6.1, Room 2.8.4, WorkManager 2.11.2.
- AndroidX ViewModel/LiveData 2.11.0 and ViewBinding are enabled.
- JUnit Jupiter 6.1.0 is used for local tests.
- No Retrofit, dependency-injection framework, or Kotlin dependency is present.

The SDK values remain implementation choices until device/profile review. Current Build 0.1 reference is NCC-036V / Android 12 / API 31 / firmware `877AOOAKN1_RK2_V009`, pending physical POC; exact SQLite table details and physical paths remain TBD.

## Refactored architecture

```text
touch UI / hardware keys
    ↓ concrete final use-case classes
app/ui and feature/*/application/usecase
    ├─→ feature/*/domain when pure business rules are needed
    └─→ feature/*/application/port interfaces for external capabilities
            ↓ concrete implementations
        platform/*: Android, shared Camera2/EGL runtime, retained CameraX adapter, MediaRecorder, storage, device, providers

core/*: shared pure-Java config, feature-gate, input, and logging contracts
```

| Package family | Responsibility |
|---|---|
| `app` | Android entry points and `AppComposition` wiring |
| `app.ui` | ViewModels, UI state, renderers, navigation, lifecycle handling, and input routing |
| `app.devmode` | Developer-screen labels/groups/setting IDs mapped to `core.featuregate` |
| `feature.*.domain` | Entities, immutable values, enums, and pure business rules only |
| `feature.*.application.usecase` | Concrete final classes representing application actions |
| `feature.*.application.port` | Java interfaces for DB, file, device, SDK, network, and provider boundaries |
| `feature.*.application.repository` | Pure-Java implementations only when a feature actually needs them |
| `platform.camera`, `platform.audio`, `platform.recording` | Shared Camera2/EGL runtime, retained CameraX adapter, MediaRecorder, and foreground-service implementations |
| `platform.storage`, `platform.database`, `platform.config` | Filesystem/media output, Room, and local CSON implementations |
| `platform.device`, `platform.input`, `platform.permission` | Android device APIs, raw key handling, and runtime permissions |
| `platform.logging.app` | Default app-process Logger implementation, local file writer, and Room outbox writer |
| `platform.logging.loggly` | `:loggly` services that read Room and perform all cloud HTTP delivery |
| `core.config`, `core.featuregate`, `core.input`, `core.logging` | Shared pure-Java capabilities compiled in the independent `:core` module |

Use cases are concrete classes; interfaces are reserved for real external, command, or event boundaries. The current source-level map and dependency rules live in `app/src/main/java/com/dvid/dcam/ARCHITECTURE.md`.

## Current user/application flow

- `MainActivity` is the Android entry point and ViewBinding presentation shell.
- The Activity shell hides the Android status bar and keeps navigation-bar insets. Provisioned Device Owner installs additionally apply managed Home, lock-task, status-bar, uninstall, and permission policy; non-owner development installs remain usable without those privileged controls.
- `AppComposition` selects concrete adapters/repositories, loads configuration, and creates the lifecycle-bound capture runtime before attaching it to `MainViewModel`.
- Startup applies Device Owner policy on a dedicated executor before requesting missing core runtime permissions. The location prompt waits for the camera screen, then serial/config access follows; runtime permissions use Activity Result APIs rather than `DevicePolicyManager` grant calls.
- Boot/package-replacement handling reapplies kiosk policy outside the receiver main thread and launches DCAM when it is Device Owner or default Home. Activity resume only checks whether lock task may be entered.
- Physical keys route through `HardwareButtonRouter` to the same capture use cases used by touch UI.
- Static global `Runnable` action registries were removed.
- Camera preview UI is separated from the shared capture gateway: `SharedCameraPreviewView` owns the visible surface/status text, while `SharedCameraGateway` delegates binding and recording commands to one process-scoped shared runtime owner.
- Shared runtime events are mapped to domain capture events before they update UI state; the retained CameraX adapter is disconnected from production composition.
- SOS handoff serializes stop/finalize/start rather than overlapping shared-runtime recordings.
- Active video/SOS recording starts an Android foreground service and persistent notification.
- Opt-in developer feature gates are persisted in Android SharedPreferences. Ordinary new features work normally without registration; only surfaces deliberately made developer-disableable receive a gate. On a fresh install, `FeatureGate` defaults enable image capture, video recording, audio recording, the read-only Files browser, GPS, and the operational Storage and Device settings surfaces. Media encryption, authentication, security, cloud, transfer, and streaming gates default off.
- The menu is driven by `MainMenuModel`, keeps the declared order, hides disabled feature tiles, and falls back to Menu when a disabled screen is requested. About remains visible.
- Physical hardware capture keys now consult the same feature gates before invoking photo, video/SOS, or audio commands.
- A hidden Developer settings screen can toggle the surfaces explicitly made developer-disableable; it is reached from About through the local developer unlock gesture.
- Password-first login, local/developer user provisioning, logout, and boot-scoped operator-session restoration are implemented. New and updated credentials use bcrypt with cost 10 and bcrypt-embedded salts; normal screen navigation and hardware capture starts require an active session.
- The settings home presents a three-column launcher with 12 categories when the corresponding gates are enabled. Storage-mode selection is operational; remaining draft/demo controls are not counted as completed operational settings.
- Files is a read-only explorer limited to contract media roots `Video`, `IMP`, `Image`, and `Audio`. It supports folder navigation and opens media through a temporary FileProvider grant.
- Battery percentage, available bytes on the resolved capture root, and GPS capability/enabled state flow through `DeviceRepository`, a refresh use case, and ViewModel state.
- Language selection is implemented through a settings use case and Android SharedPreferences; changing language recreates the Activity with the localized context.
- Storage selection is persisted through a settings use case. Changing it recreates the Activity so the next media session resolves one stable root before capture.

## Current storage behavior

The operator media policy supports `INTERNAL`, `EXTERNAL`, and `AUTO`; `AUTO` is the fresh-install
default. All roots are Android app-specific directories—no public `DCIM` root is used:

- Logical Internal maps to the primary app-specific external-files directory and falls back to the
  private files directory if Android does not provide it.
- Logical External considers secondary/removable app-specific external-files directories.
- External and Auto prefer the first mounted, writable external candidate with enough free space
  and refresh removable-volume discovery when storage becomes available after startup.
- A system `MEDIA_MOUNTED` receiver reruns staged-media recovery immediately after Android regains
  removable-storage access; application startup remains the fallback recovery trigger.
- An explicit Internal selection never selects External.
- Root selection never changes during an active file.

The selected mode is persisted locally and mirrored to operational settings as requested/resolved
values. Exact production-device physical-root mapping, BDMA/ADB visibility, removal behavior, and
sustained write speed still require the planned BodyCamera POC.

All media starts use the resolved root and one capacity policy. Build 0.1 requires free space for an
estimated 30-minute 10 Mbps recording plus a 500 MiB finalization reserve. Starts are rejected when
the root is unavailable, unwritable, or below that threshold. CameraX video receives a file-size
limit that preserves the reserve; storage exhaustion is reported as a controlled failure and the
staged artifact is preserved for later finalization/recovery.

Visual-media completion now uses a filesystem readiness boundary. A completed staged artifact is
copied to a hidden non-contract publication file in the final directory, flushed, size-verified and
renamed into the approved `Media/*` folder without overwriting an existing file. Publication failure
preserves staging. Once per process startup, playable unencrypted contract-named artifacts left in
`Temp` are finalized conservatively; invalid, encrypted, duplicate or ambiguous artifacts remain in
`Temp`. No per-media database row gates Build 0.1 BDMA discovery.

On the BWC `KF5OF2126040802193`, a forced app termination after approximately 30 seconds left a
75,080,663-byte playable staged MP4 with a 30.104-second movie duration; startup recovery validated
and published it. A reboot at approximately 30 seconds preserved a 69,600,557-byte staged MP4 with a
27.400-second movie duration. The rebooted BWC USB-shares removable storage, so recovery must be
re-run when Android receives the volume again. Repeated trials and a true power-cut test remain open.

| Type | Folder | Extension |
|---|---|---|
| Video | `Media/Video/yyyy-MM-dd` | `.mp4` |
| SOS/important | `Media/IMP/yyyy-MM-dd` | `.mp4` |
| Image | `Media/Image/yyyy-MM-dd` | `.jpg` |
| Audio | `Media/Audio/yyyy-MM-dd` | `.aac` using AAC/ADTS |

Current filename shape:

```text
DCAM_<CameraID>_<UserID>_<yyyyMMdd>_<HHmmss>[_IMP][_enc].<ext>
```

The listed media layout, naming, and AAC output align with the corresponding Data Contract 1.6 media artifact rules. SOS remains an application action/state name, but its persisted artifact is contract-important media in `IMP` with `_IMP`. Device identity uses the approved device-information-only `Config/dcam_config.cson` store with DB/SD mirrors; operator identity comes from the active session, crypto password comes from build properties, and runtime encryption comes from the feature-gated preference store. App/contract metadata and DB-backed operational settings are not implemented.

AES-256-CTR media encryption now exists in `BodycamMediaCrypto` and is applied to saved photo, video/SOS, and audio artifacts when the saved media-encryption preference is enabled. Hiding the Security settings surface does not disable the shared crypto/storage capability. Encrypted files use the existing `_enc` filename marker. Final key management, BDMA decryption compatibility evidence, and operational policy remain open and must not be treated as fully approved security design.

Optional MP4 MD5 generation is implemented behind the `VIDEO_MD5` developer gate. Publication calculates the digest while copying, writes the `.md5` sidecar before accepting the final media, and queues retry work for sidecars that cannot be completed immediately. BDMA fixture/representation compatibility and production policy still need proof. There is also no contract-aligned embedded media metadata implementation, media/database schema version, persisted file lifecycle, cross-root BDMA scanner/importer, or verified cleanup/write-back flow.

## Logging and database

- Local Logcat plus `Logs/logs.txt` under the existing app-storage root, with dated `logs-YYYY-MM-DD.txt` rotation and 14-day retention. The filename now matches the contract, but final internal-root mapping, ADB exposure, and BDMA read-only enforcement remain unverified.
- Context includes version, thread, source, hardware ID, model, and camera/account ID.
- Room-backed `dcam.db` currently contains pending logs, device identity, remote config, operational settings, user profiles, hashed authentication methods, and operator sessions. Per-media/session/finalization rows are intentionally deferred for Build 0.1 because their exact schema and retention policy remain unresolved; the broader runtime, recovery, import and BDMA-safe write-back schema also remains future work.
- Application and feature code depend on `core.logging.application.port.Logger`. `AppLogger` and `RoomLogWriter` run in the default app process and own Logcat, local files, Room enqueueing, rotation, retention, and crash fallback initiation; they do not perform HTTP.
- `LogglyUploadService`, `LogglyUploadJobService`, `LogglyUploadScheduler`, `RoomLogUploader`, `LogglyCrashSpool`, and `LogglyHttpClient` run under `platform.logging.loggly` in `:loggly`. Normal upload reads Room; crash fallback reads atomic app-private spool files and does not read Room. Logging is always-on infrastructure and is not controlled by the Cloud settings surface; actual remote delivery still requires provider configuration. A full provider-neutral diagnostics design is future work.

## Future capabilities

Metadata, a fully durable media lifecycle, contract configuration/database migration, Update, Streaming, and PTT remain roadmap/documentation capabilities only. GPS tracking has Android location adapters, runtime permission retry handling, settings integration, and current-location requests, but capture metadata integration remains incomplete. Optional MP4 MD5 sidecars use a durable retry queue plus WorkManager startup, immediate, and periodic recovery. Operator authentication/session and initial cloud/remote-config boundaries have code, but neither constitutes complete runtime/media delivery. Contract media folder/naming rules and local AES-256-CTR media transforms have concrete platform implementation; broader capabilities enter source only when approved behavior defines their domain values and application boundaries.

## Local property behavior

The Gradle build exports keys loaded from `application.properties` and gitignored `application-local.properties` into `BuildConfig`.

Private setup instructions for local-property handling belong in `application-local.properties`, not public build files.

## Remaining gaps

- Each approved menu feature still needs its own XML/ViewBinding screen and feature ViewModel/use cases beyond the feature-gated launcher/shell.
- `DemoSettingsState` still contains local UI scaffolding for draft controls; storage-mode selection is
  now backed by its own settings use case and persistence adapter.
- Shared Camera2/EGL runtime is selected by app composition and owned by one process-scoped runtime owner; the retained CameraX adapter is compiled but disconnected. The foreground service persists active-capture protection and triggers recovery after process recreation. True kill/reboot/power-cut proof remains open.
- Shared runtime serializes camera ownership and recovery through its process owner; the retained CameraX path still uses its lifecycle/main-executor contract.
- GPS tracking and current-location requests are implemented; embedding coordinates into media metadata and full lifecycle/device validation remain open.
- Device status currently covers battery, available selected-root storage, and GPS capability/enabled state; richer network/firmware/USB status remains future work.
- No persistent per-media status/recovery database state machine; foreground capture recovery state is persisted separately.
- Authentication has no failed-attempt throttling/lockout policy yet, and bcrypt cost-10 latency still requires measurement on target hardware. No PBKDF2 compatibility verifier is retained; development installs containing plaintext or PBKDF2 credentials must clear app data, reinstall, or reprovision users.
- Data Contract media folders/naming, important-media mapping, AAC output, active log filename, local AES-256-CTR transforms, logical storage-mode resolution, capacity safety, and optional MP4 MD5 sidecars are implemented locally. Production physical-root proof, device-information-only CSON, app/contract metadata, fully DB-owned settings, embedded metadata, final key handling, BDMA permissions, import results, cleanup, and E2E proof remain open.
- Android Device Operation: runtime permission sequencing, hidden status bar, Home intent, boot/package-replacement launch hook, Device Owner policy application, and lock-task entry are implemented. Physical provisioning/exit proof, exact dedicated-screen behavior, navigation/power policy, durable capture-service ownership, and crash/reboot recovery remain pending Technical Design/device validation.
- No streaming, PTT, or update implementation. Cloud/remote config remains an early no-op/local-state boundary, while Device/User auth is an incomplete MVP foundation rather than a finished feature.
- Real BodyCamera POC and hardware matrix validation remain mandatory.

## Verification

Engineering evidence follows the approved [DCAM Engineering Evidence & NAS Artifact SOP](https://ducviet.atlassian.net/wiki/spaces/DVID/pages/53608471): raw APK, media, logs, ADB output, screenshots, and reports belong in the internal NAS evidence store; Jira records Evidence ID and result; Confluence stores governance, metadata, conclusions, and links. The SOP does not itself prove NAS readiness, Device POC pass, or Build 0.1 readiness.

After refactoring:

- `:app:testDebugUnitTest`: passed on 2026-07-28 with 471 tests, 0 failures, 0 errors, and 0 skipped, including architecture boundaries, startup/kiosk lifecycle, shared camera ownership, recovery, storage limits, logging process ownership, location, authentication, storage recovery, device-serial persistence, and MD5 retry regressions.
- `:app:assembleDebug` and `:app:assembleDebugAndroidTest`: passed on 2026-07-28 with `:app` consuming the compiled `:core` JAR.
- `lintDebug`: passed on 2026-07-20.
- Generated `BuildConfig`: intentionally contains all application and local-property fields.
