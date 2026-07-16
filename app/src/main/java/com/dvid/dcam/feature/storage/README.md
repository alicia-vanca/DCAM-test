# Storage

Status: implemented.

`feature.storage` owns Android-free storage policy and application boundaries:

- Publication policy: `StorageMode` (`APP_DATA`, `PUBLIC_DCIM`).
- Physical partition preference: `MediaPartitionLocation` (`INTERNAL`, `EXTERNAL`, `AUTO`).
- Capacity checks, active-volume status, warning state, and recovery result.
- Application ports for preferences, capacity snapshots, active runtime policy, and active volume.
- `StorageSettingsUseCase` keeps persisted preference and live capture policy synchronized.

Android filesystem, MediaStore, SharedPreferences, mount handling, and storage resolution adapters live under `platform/storage` and `platform/config`. Concrete wiring lives in `AppComposition`.

Rules:

- Keep storage selection policy single-source through `DcamStorageRootResolver`.
- Never infer publication mode from paths; use `StorageMode`.
- Never use enum ordinals as UI or persistence IDs.
- Changing partition preference must update the active runtime policy before the next capture.
