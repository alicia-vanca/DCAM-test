# Confluence refresh gap report - 2026-08-26

**Source refresh:** `node download-confluence.js` scanned 172 Confluence space pages, downloaded 84 DCAM pages, and blocked 0 credential-bearing pages on 2026-08-26. Confluence remains source of truth; this file records repository-local implications only.

## Current authoritative baseline

- Build 0.1 remains the Working Recording Slice: one reference-device profile, internal storage, fixed `B01OPR` operator, MP4 MD5 before `BDMA_READY`, and no automatic scope uplift from later-phase design.
- The new GMS-free Android Runtime ADR makes production Android independent of Google Play services, Play Store, Google accounts, FCM, Analytics, and Play Integrity. Firebase Crashlytics is optional bounded telemetry only.
- Factory provisioning/device management now uses Spring Boot BFF + Thymeleaf Factory Portal with BFF-only PostgreSQL `ddmp`. The BFF owns authorization, create/restore, idempotency, and audit; browser clients do not access the database directly.
- Device API authentication is per-device mTLS with an Android Keystore-held private key and proof-of-possession enrollment. `serial_number` is the primary recovery key; `dcam_cloud_device_id` / `platformDeviceId` is the logical cloud identity; `android_id_hash` is not used as identity or recovery.
- DCAM-23, DCAM-30, and DCAM-6 are working evidence summaries. Their observed passes do not close Device POC, Security Review, deployment, or production/release approval gates.

## Local documentation corrections

| Stale local direction | Current source direction |
|---|---|
| GMS and Play Store are optional capability/update paths | Production Android is GMS-free; prohibited GMS/Play Store/Google-account flows must be rejected. |
| Firebase/WebServer owns provisioning and cloud identity | Spring Boot BFF + PostgreSQL `ddmp` owns Factory Portal provisioning and cloud identity. |
| `android_id_hash` is the recovery lookup key | `serial_number` and approved SD recovery data participate in recovery; `android_id_hash` is not used. |
| Web Portal/API contract has the former title/path | Page ID `49873154` is now the Factory Provisioning Portal & BFF API Contract; the stale local filename was removed. |
| Device identity can imply device authentication | Device identity and mTLS credential are separate; enrollment proves Keystore key possession. |

## Local implementation implications

The refreshed authority conflicts with existing implementation areas that were not changed in this documentation-only task:

- `app/build.gradle` currently declares `com.google.android.gms:play-services-location`.
- Location capability/provider classes currently import Google Play Services location APIs.
- Local database/domain names still contain `firebaseInstallationId`.

These are implementation migration items, not documentation evidence of a completed GMS-free build. They require a separate scoped code change, replacement capability design, tests, and release dependency/manifest/source verification.

## Qualification gates still open

- Reconcile the Registry page's top-level `81/81` approval-scope statement with its §4 `80/80` data-cutoff statement.
- Complete physical-device GMS-free dependency/manifest/source evidence for the target firmware.
- Complete mTLS/PKI Security Review, Keystore/StrongBox POC, certificate lifecycle, and BFF authorization evidence.
- Complete physical storage/DB/BDMA qualification and traceability links to Jira, PR/build, QA Test IDs, and NAS Evidence IDs.

Use the 2026-08-26 Confluence digest for current source meaning, and keep this report separate from approved requirements and the repository's current-state record.
