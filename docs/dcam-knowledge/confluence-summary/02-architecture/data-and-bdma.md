# Data, storage, and DCAM–BDMA boundary

Data Contract status: current Confluence page version 13, last registry review 2026-07-14. The [contract digest](../01-requirements/data-contract.md) is authoritative over earlier proposed directions in this architecture summary.

## Core boundary decision

Build 0.1 controlling profile: internal storage only, finalized media only for BDMA, and MP4 MD5 success before affected item becomes `BDMA_READY`.

**DCAM is a passive data producer. BDMA is an active data consumer and manager.**

- DCAM creates original media, metadata, local records/database, device/user context, states, and logs.
- DCAM keeps source data locally and exposes an agreed ADB-readable location or mechanism.
- BDMA detects the connected device and initiates discovery, reading, synchronization, import, validation, indexing, display, backup, export, and reporting.
- DCAM does not push/upload/sync source data to BDMA in the current scope.
- The current transfer/access boundary is ADB—not REST, WebSocket, FTP, MQTT, cloud upload, or a DCAM-to-BDMA socket.

## Ownership by stage

| Stage | Owner/source of truth |
|---|---|
| Creation and finalization on BodyCamera | DCAM |
| Exposed source files/records before import | DCAM |
| ADB connection, discovery, read, and sync | BDMA |
| Import staging, validation, and retry | BDMA |
| Managed desktop copy, index, display, backup/export | BDMA |

BDMA must treat source media bytes, embedded metadata, MD5 content, `Temp`, active runtime state, and logs as non-writable. Contract 1.6 explicitly permits BDMA to update device information in `dcam_config.cson` through approved paths, write only approved `dcam.db` tables/fields, and delete successfully imported media under the cleanup rules. These writes require schema, locking, corruption, and concurrent-access safeguards.

## Data categories

| Data | Current direction |
|---|---|
| Video | `.mp4` source media |
| Image | `.jpg` source media |
| Audio/PTT | Audio file if applicable; final format TBD |
| Metadata | Embedded in media when supported; standalone media JSON is not part of contract 1.6; exact fields/encoding remain TBD |
| App/contract metadata | App package/version plus data/media/encoder contract versions; no `bdma_decoder_profile_id` |
| Local DB | Internal `Database/dcam.db`; identity/provisioning, user/operator, settings, runtime, media/session, tracking, import/write-back, and config-cache data |
| Logs | Internal `Logs/logs.txt`; BDMA read-only |
| Device/User context | Created or captured by DCAM, then mapped by BDMA |
| Config | `dcam_config.cson` is device-information only; operational settings belong in `dcam.db` |

Media formats are `.mp4`, `.jpg`, and `.mp3/.aac/.wav`. Logical roots, folders, naming, access, and cleanup are final at the contract baseline; physical paths and detailed schemas are not.

## Import and integrity outcomes

- MP4 with matching MD5: import as Verified when it passes; reject as Checksum Mismatch when it fails.
- MP4 without MD5: import as Unverified; require per-file confirmation before source deletion.
- Image/audio: import without MD5 and do not warn about its absence.
- Unsupported/unreadable/unsupported-encrypted media and `Temp` are not deleted.

The DCAM-side persisted lifecycle (`recording`, pending/finalizing, completed, corrupt, recovered) remains a design gap and must not be collapsed into BDMA import state.

## Metadata direction

Contract 1.6 binds media association to deterministic naming and embedded metadata when supported. It does not define a standalone media JSON artifact. Exact embedded fields/types, GPS validity, source lifecycle, collision handling, and schema encoding still need Metadata/Database Design.

The filename baseline is `DCAM_<CameraID>_<UserID>_<YYYYMMDD>_<HHMMSS>[_IMP][_enc].<ext>`. Important media lives in `Media/IMP`; MD5 applies only to MP4 and uses the same base name in the same folder.

## Error ownership

| Scenario | Primary owner |
|---|---|
| Record/capture failure | DCAM |
| Missing or wrong source metadata | DCAM; BDMA reports validation error |
| Incomplete/corrupt source before import | DCAM classifies where detectable; BDMA must reject/report |
| BodyCamera storage full | DCAM |
| GPS unavailable | DCAM continues core work and marks unavailable |
| ADB detection/disconnect/read transport issue | BDMA |
| Desktop import/validation/index/display issue | BDMA |
| Data Contract/media naming/DB schema mismatch | Shared and versioned; BDMA must stop unsafe DB updates and report compatibility errors |

## Boundary for future features

- Remote management adds a command/config path but does not automatically transfer media ownership.
- Streaming adds a real-time channel while recorded-media ownership remains local/ADB-based.
- PTT adds real-time audio and possibly stored artifacts that need a contract.
- Cloud sync may add a new consumer and must explicitly revisit ownership.
- Any write/delete responsibility beyond the explicit device-info, database-field, and cleanup permissions in contract 1.6 requires a contract update and, when architecturally significant, an ADR.
