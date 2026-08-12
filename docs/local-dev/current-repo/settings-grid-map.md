# Settings grid design map

This is a repository-local implementation map, not a Confluence mirror. The broader expected Setting hub, in-app console, role/capability/runtime guards, Maintenance Password Gate, and media-console direction are summarized in [Technical design draft digest](../../dcam-knowledge/confluence-summary/05-technical-design/README.md).

Status: feature-gated 12-category navigation with a read-only Files explorer, local language persistence, operational `INTERNAL`/`EXTERNAL`/`AUTO` storage selection, and a gated media-encryption preference. Draft/demo setting controls remain scaffolding and are not counted as completed operational settings.

The settings home behaves like an app launcher: a three-column grid containing only an icon and category label, without recording/device status or a page heading. Feature gates decide which tiles are visible and send disabled destinations back to Menu. Opening a category shows a scrollable title and controls/list content; system Back returns to the grid. Endpoint values, usernames, passwords, passcodes, and secret keys are deliberately absent.

## Code-backed pieces

- Files browses only `Video`, `IMP`, `Image`, and `Audio` inside DCAM-managed media storage and opens files through a temporary Android viewer grant.
- Language selection is persisted locally and recreates the Activity with the localized context.
- Storage selection persists `INTERNAL`, `EXTERNAL`, or `AUTO` and recreates the Activity so the next media session resolves one stable app-specific root. Production physical-root mapping still requires device proof.
- Security/Encryption has a media-encryption preference. Hiding its settings surface does not disable the shared crypto/storage capability. When the preference is enabled, photo, video/SOS, and audio save flows apply local AES-256-CTR transforms and use the `_enc` filename marker.
- Developer settings expose opt-in local feature gates. These gates are a project-control surface, not an approved operational settings feature and not a registry requirement for ordinary new features.
- Other setting controls backed by `DemoSettingsState` are local UI scaffolding only.

| Grid item | Detail-list contents | OEM/CSON source areas for later implementation |
|---|---|---|
| Files | Browse `Video`, `IMP`, `Image`, and `Audio`; open media in an Android viewer | Contract `Media` folders only |
| Recording | Recording, segment length, pre-record, delay, boot/loop and battery warning | OEM `recording`; CSON `[video] file.*` |
| Cameras | Rear/front preview and photo sizes, rotation, IR and flashlight | OEM rear/front camera; CSON `[camera]` |
| Video streams | Default stream plus main/sub codec, resolution, FPS and quality | OEM main/sub stream; CSON `[video] main.*`, `sub.*` |
| Audio | File format, recording sound, encoding and volume | OEM preferred audio format/disable sound; CSON `[audio]`, `[video] file.audio_record_format` |
| Storage | Target, low-space warning, recycle and post-upload deletion | OEM recording storage/loop; CSON `[video] file.save_storage`, `file.recycle`, `file.low_capacity_*` |
| GPS | Enablement, GPS/AGPS mode and update/upload frequency | OEM `gps`; CSON `[location]` |
| Device | Device/operator ID, display controls, fall detection, lights and language | OEM identity/display/hardware/localization; CSON `[device]`, `[common]` |
| Security | Media-encryption preference is code-backed; menu/USB protection and transmission encryption remain later work | OEM security/transmission security; CSON `[common] enter_menu.*`, `usb_storage.*`, `transport.*` |
| Network | Backend address/port and APN connection fields | OEM APN/device identity; CSON `[backend]`, `[apn]` |
| Transfer | RTSP viewing and FTP upload settings | OEM RTSP/FTP; CSON `[stream]`, `[ftp]` |
| About | Device/app version, firmware/OTA, capabilities, diagnostics | CSON `[device]`, `[backend]`, capability detection and local diagnostics |

## Later implementation rules

- Treat CSON options and OEM-exposed capabilities as device-dependent; do not assume every model supports every field.
- Load current values through application-owned configuration ports, not directly in Activity or View code.
- Mask sensitive fields and require deliberate reveal/edit flows.
- Validate values against the device-provided option lists before writing.
- Keep recording/capture available if optional configuration cannot be read.
- Log important setting changes without logging passwords, passcodes, tokens, or secret material.
- Data Contract 1.6 is approved: settings must honor Internal/External/Auto media selection, MP4-only MD5, encryption suffix rules, app/contract metadata, and cleanup rules. Store operational settings in `dcam.db`, not device-information-only `dcam_config.cson`; detailed UI/schema design remains pending.
