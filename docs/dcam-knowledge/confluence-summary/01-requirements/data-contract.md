# Data contract

Source status: current Confluence page version 17 (approved contract 1.14), last updated 2026-08-25. This page is a local implementation-oriented digest; the [Confluence Data Contract](https://ducviet.atlassian.net/wiki/spaces/DVID/pages/47743153) remains authoritative.

## System boundary

Build 0.1 overlay: Decision Brief and Release & Build Applicability Matrix control active-build exceptions. MP4 MD5 is required before affected item becomes `BDMA_READY`; checksum failure preserves MP4 but blocks BDMA import for that item.

Build 0.1 also overrides generic target-state identity and storage behavior: use fixed technical operator `B01OPR` / `Build 0.1 Operator`, internal storage only, no external fallback, and no login UI. These exceptions do not change later build profiles.

## Build 0.1 filename-token contradiction

Current source requirements cannot all be satisfied for the reference device:

- `DEVICE_TOKEN` must be an exact snapshot of validated `serial_number`, use `[A-Z0-9]`, and be `6–10` characters.
- Truncation, underscore padding, aliasing, hashing, and invented replacement tokens are prohibited.
- Device POC observed `serial_number` `KF5OF2126040802193`: `17` characters, valid charset, invalid required length.
- Keeping the exact value violates length. Any shortening or replacement violates snapshot and no-replacement rules.
- `OPERATOR_TOKEN` `B01OPR` is valid at exactly `6` characters.

Treat filename identity qualification as blocked by a requirement contradiction until authoritative documents change. Device POC must confirm the physical `serial_number`; local code or documentation must not manufacture a compliant-looking substitute.
DCAM is the Android-side data producer. BDMA is the desktop-side consumer, importer, sync/write-back actor, and cleanup actor. Spring Boot BFF plus PostgreSQL `ddmp` owns cloud device identity, Factory Portal provisioning, remote-config metadata, and audit metadata; Firebase/BaaS is not this authority boundary.

BDMA discovers data over ADB and must scan both the external and internal DCAM media roots, external first. Physical Android paths remain device-specific and require validation on real BodyCamera hardware.

BDMA provisioning is not required for normal factory provisioning. The default business-provisioning path is the Web Provisioning Portal QR flow.

## Identity and compatibility

DCAM device identity is not based on serial number, owner name, or manufacture date.

| Field | Role | Storage |
|---|---|---|
| `dcam_cloud_device_id` | BFF/PostgreSQL `ddmp` primary cloud key | BFF/PostgreSQL `ddmp` and `dcam.db` |
| `platformDeviceId` | BFF↔DCAM contract name for the same logical cloud identity | Resolved by BFF; not a second root identity |
| `serial_number` | Hardware identity and primary recovery key | App-private storage, `dcam_config.cson`, `dcam.db` mirror, BFF/PostgreSQL `ddmp`, SD recovery cache |
| `android_id_hash` | Not used by the current contract | Must not be used as identity or recovery lookup |
| `owner_name` | Mutable/semi-static device information | `dcam_config.cson`, `dcam.db` mirror, BFF/PostgreSQL `ddmp` |
| `manufacture_date` | Semi-static device information, ISO `YYYY-MM-DD` | `dcam_config.cson`, `dcam.db` mirror, BFF/PostgreSQL `ddmp` |
| `serial_history` | Historical serial values | BFF/PostgreSQL `ddmp`; optional DB mirror |
| `app_installation_id` | App-install metadata only | Optional metadata; not a device identity key |

BDMA recognizes the app and contract through metadata such as `app_code`, `app_package_name`, app version, `dcam_data_contract_version`, `media_contract_version`, and `encoder_contract_version`. `bdma_decoder_profile_id` is not part of the contract; BDMA must use its built-in compatibility table for supported app/media/encoder contract versions.

## Logical storage layout

```text
Internal DCAM Storage Root
├── Media/{Video,Image,Audio,IMP}
├── Config/dcam_config.cson
├── Database/dcam.db
├── Logs/logs.txt
└── Temp

External DCAM Storage Root
├── Media/{Video,Image,Audio,IMP}
└── Temp
```

- `dcam_config.cson`, `dcam.db`, and `logs.txt` always live under the internal root.
- Media storage mode is Internal, External, or Auto. Auto prefers external storage and falls back to internal when external storage is missing, full, invalid, unavailable, or not writable.
- Folder names start with an uppercase letter. BDMA imports only from the four `Media` subfolders and ignores `Temp` unless a future contract defines recovery/import behavior.

## Media formats and naming

Supported source formats are `.mp4` video, `.jpg` image, and `.mp3`, `.aac`, or `.wav` audio.

```text
DCAM_<CameraID>_<UserID>_<YYYYMMDD>_<HHMMSS>[_IMP][_enc].<ext>
```

- `CameraID` is 6–10 characters; `UserID` is exactly 6 characters.
- Important media uses `_IMP` and is stored in `Media/IMP` regardless of media type.
- AES-256 encrypted media uses `_enc`. Important encrypted media must use suffix order `_IMP_enc`.
- Metadata is embedded in the media file when the format supports it. A separate per-media JSON file is not part of contract 1.14.
- Encryption algorithm detail, keys, rotation, and BDMA decryption remain for the Security & Encryption Design.

## MP4 checksum behavior

MD5 applies **only** to `.mp4` video, including important and encrypted variants. When enabled, DCAM writes a same-basename `.md5` file beside the video. It does not create MD5 sidecars for image or audio.

| Case | Import result | Source cleanup |
|---|---|---|
| MP4 + matching MD5, verification passes | Imported / Verified | Auto-delete allowed when cleanup is enabled |
| MP4 + MD5, verification fails | Failed / Checksum Mismatch | Do not delete |
| MP4 without MD5 | Imported / Unverified | Per-file user confirmation required before delete |
| Image/audio imported successfully | Imported | Auto-delete allowed when cleanup is enabled |
| Unreadable, unsupported, or unsupported encrypted media | Failed | Do not delete |

Deleting a source MP4 also permits deletion of its matching `.md5`. Cleanup must never delete config, database, logs, failed media, or `Temp` files.

## Config, database, and logs

| Artifact | Purpose | DCAM | BDMA |
|---|---|---|---|
| `Config/dcam_config.cson` | Device information only | Read/write | Read/write/update device information only through an approved contract path |
| `Database/dcam.db` | User/operator, settings, runtime, media/session, tracking, import/write-back, identity, provisioning, and config-cache data | Read/write/update | Write only approved tables/fields, subject to schema/version/locking safety |
| `Logs/logs.txt` | Operational and diagnostic logs | Read/write | Read-only; never modify, truncate, or delete |

`dcam_config.cson` may contain device display identity, device name, model, serial number, owner name, manufacture date, firmware/hardware version, and app/contract metadata if needed for compatibility display. It must not contain media metadata, user/history data, storage mode, feature flags, MD5/encryption settings, cleanup policy, import/sync state, logs, credentials, or operational settings. Those belong in `dcam.db`.

`dcam.db` covers device identity/provisioning, remote config cache, user/operator/auth/session/sync data, operational and applied settings, runtime state, media/session/finalization/recovery state, BDMA import/write-back state, tracking state, and optional diagnostic events. It does not make `android_id_hash`, `headwindDeviceRef`, or a local installation ID into a device identity.

BDMA must not alter source media bytes, embedded metadata, MD5 content, `Temp`, active operator-session runtime state, active media-session lifecycle fields, or the server-owned device identity primary mapping. It may delete source media only after a successful import under the cleanup matrix above.

## User/operator sync

DCAM and BDMA both maintain user/operator management data. Later profiles may require an authenticated operator session on DCAM; Build 0.1 uses the approved fixed technical operator exception and no login UI. Emergency recording may use the protected system identity `EMERGENCY_OVERRIDE_ADMIN` when no operator is logged in. User/auth sync records must be versioned/revisioned and must not interrupt active evidence capture.

## Versioning and remaining design work

- Contract version: 1.14.
- Media naming and media contract metadata must be versioned; breaking naming changes require a contract update and BDMA compatibility review.
- `dcam.db` must expose schema/version metadata for BDMA compatibility checks.
- App package/version and data/media/encoder contract metadata must be sufficient for BDMA app recognition.
- Remote config payload fields remain outside this contract and belong to System Settings / Cloud Architecture.
- Exact physical roots, SQLite table details, embedded metadata fields/encoding, encryption/key design, concurrency protocol for BDMA writes, duplicate/retry semantics, and detailed recovery behavior still need implementation, fixtures, and joint DCAM-BDMA tests.
