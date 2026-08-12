# DCAM current repository notes

Use this folder for repository-local implementation status, codebase guides, and notes that describe what this checkout currently does.

These files are local development references. They are not Confluence source material and should be compared with the Confluence-derived baseline in `docs/dcam-knowledge` before making product or architecture decisions.

## Files

- [current-state.md](current-state.md) records what this checkout currently implements and where it differs from the target.
- [settings-grid-map.md](settings-grid-map.md) records the current feature-gated settings grid and local implementation map. The broader target direction now lives in the Confluence-derived [Technical Design digest](../../dcam-knowledge/confluence-summary/05-technical-design/README.md).
- `DCAM_Huong_dan_dev_MVP_codebase.pdf` is a local codebase guide/reference.

## Local implementation snapshot

Reviewed: **2026-07-21** (Asia/Saigon). Draft/demo settings and placeholder feature pages are intentionally excluded from completed status.

| Area | Code-backed status |
|---|---|
| Android app shell | Java-first Android app with ViewBinding shell, `AppComposition` wiring, capture use cases, CameraX preview/video/photo, MediaRecorder audio, active-recording foreground notification, and Device Owner/default-home kiosk lifecycle hooks. |
| Feature gates | Project-phase feature gates persist locally and drive menu visibility, disabled-screen fallback, hardware capture-key behavior, and the hidden Developer settings screen. Logging remains shared always-on infrastructure. |
| Local media | Media output uses contract-shaped folders for `Video`, `IMP`, `Image`, and `Audio` with DCAM filenames including optional `_IMP` and `_enc` markers. Optional MP4 MD5 sidecars use a durable retry queue. File browsing is read-only and rooted inside managed media folders. |
| Settings and preferences | Language and `INTERNAL`/`EXTERNAL`/`AUTO` storage selection persist locally. Media-encryption preference is gated by the Security/Encryption feature gate and is read by photo, video/SOS, and audio save flows. |
| Encryption | Local AES-256-CTR media transforms exist for saved media when encryption is enabled. Final key-management policy and BDMA compatibility evidence remain open. |
| Diagnostics | `AppLogger` and `RoomLogWriter` own Logcat, local files, Room enqueueing, and atomic crash spooling in the app process. The `:loggly` process owns Room/crash-spool upload, HTTP delivery, and foreground/job execution. Provider-neutral diagnostics design remains future work. |

Open items not claimed as done: production proof for Internal/External/Auto physical roots and managed-kiosk policy, device-information-only `Config/dcam_config.cson`, app/contract metadata, fully DB-backed settings, media metadata schema, persisted media lifecycle, BDMA-compatible MD5 fixture proof, BDMA E2E import proof, final security/key design, BDMA decryption validation, BodyCamera hardware validation, and later streaming/PTT/cloud/update/transfer/GPS/Device-User flows.
