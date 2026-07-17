# Feature map by product phase

Source refresh: **2026-07-15**. For Build 0.1, Release & Build Applicability Matrix overrides phase-level feature lists. Important Media is Conditional - Not Activated; only Working Recording Slice features can block acceptance.

## Phase 1 — MVP Foundation

### Capture

- Video start/stop and recording state.
- Image capture and save.
- Camera framework POC and target-device validation.
- Basic handling of permission and camera errors.

### Local data

- Local media storage.
- Deterministic structure and filenames once contracted.
- Metadata generation and validation.
- Temporary/final/source status direction.
- Local logging and basic diagnostics.

### Device awareness

- Battery, storage, and GPS availability.
- Capability checks and safe fallback.

### Deliverable

MVP Internal Build 0.1 plus demo, test checklist, and known issues.

## Phase 2 — Platform Foundation & BDMA Integration

### Data and desktop integration

- Implement the approved DCAM–BDMA Data Contract 1.6 baseline.
- ADB discovery/read/import E2E demo.
- Metadata mapping for media, device, time, GPS, and status.
- File recovery/pending/corrupt handling.
- GPS per media file.

### Device and user platform

- Device ID/model/firmware/app version/storage/battery/network context.
- Basic remote status/config read-write foundation.
- User profile, operator, role, and permission foundation.

### Security

- Basic media/metadata encryption scope after agreed design and device benchmark.

### Deliverable

Secure Platform MVP Build 0.2, E2E demo, Device/User demo, and integration report.

## Phase 3 — Advanced Communication

- Live Streaming beta/basic implementation.
- Push-to-Talk trigger, capture/transmission, state/error logging.
- Full GPS route v1 by session.
- Advanced encryption.
- Timeout, reconnect, weak-network, and interruption handling.

These are beta capabilities, not a promise of full production hardening.

## Phase 4 — Hardening and Customer Pilot

- Full regression of core flows.
- Long-duration BodyCamera stability tests.
- CPU, memory, battery, and storage profiling.
- Streaming/PTT weak-network tests.
- GPS-route availability/fallback tests.
- Security review and critical/high bug resolution.
- Release notes, installation guide, test report, known issues, and pilot notes.

## Future platform

- Cloud upload/sync.
- OTA and extended operations.
- Fleet management and advanced monitoring.
- Analytics and additional downstream integrations.

## Important prototype-vs-scope note

The current repository contains standalone AAC recording, SOS action/state, legacy CSON configuration, Loggly upload, a Room-backed log outbox, and contract-shaped media folders/naming. SOS remains an application action in current code, but Important Media creation/activation is conditional and not active for Build 0.1. Contract storage modes, device-information-only CSON migration, app/contract metadata, MP4 MD5 generation, `dcam.db` operational scope, and BDMA integration remain implementation gaps.

## Cross-feature platform capabilities

- **Runtime operation:** boot/process recovery, foreground visibility, command arbitration, safe/degraded mode, and state reconciliation.
- **Dedicated-device policy:** Device Owner/DPC, Lock Task, Home/Launcher role, user restrictions, controlled maintenance, and policy restore.
- **In-app console:** operation, device status, settings, media view, diagnostics, maintenance actions, and permission/policy-aware controls without external Settings/file manager dependency.
- **Capability eligibility:** hardware, permission, policy, safety, and performance gating with explicit enabled/degraded/unsupported states.
- **Provisioning:** QR lookup, serial/owner/manufacture-date validation, cloud device identity, audit, and conflict handling.
- **Self Update:** signed APK metadata, safe download/install guard, recording-aware scheduling, rollback/recovery direction, and no Managed Google Play dependency.
- **Diagnostics:** local logs, crash/stability evidence, correlation, metrics, recovery data, optional provider upload, and secret-safe redaction.
- **Factory/production:** identity validation, DSetup workflow, kiosk/update/storage/ADB checks, READY_TO_SHIP or QUARANTINED decision, and evidence retention.
- **Optional advanced modules:** sensor/location and realtime AI remain capability-gated producers of observations/events; streaming/PTT stay future design work.
