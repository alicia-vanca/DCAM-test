# Open decisions, risks, and missing documents

## Current source position

The 2026-08-26 refresh shows that the core requirements, architecture, release applicability, governance, and ADR set now exists. Remaining work is evidence, implementation validation, and explicitly gated detail—not recreating the source documents. New approved directions include GMS-free Android runtime, BFF/PostgreSQL factory provisioning, and per-device mTLS with Keystore proof-of-possession.

## Highest-priority open work

| Priority | Document | Why it blocks or reduces rework |
|---|---|---|
| P1 | Jira/evidence traceability completion | Link implementation, QA Test IDs, Evidence IDs, and reviewed repository/build artifacts to the current matrix |
| P1 | Device POC and GMS-free qualification | Prove the target firmware, resolved release dependencies, manifest/source flows, and production runtime guard on hardware |
| P1 | mTLS/PKI Security Review | Validate Keystore support, enrollment, rotation, revocation, certificate profile, and BFF authorization evidence |
| P2 | Storage/DB implementation review | Close physical roots, schema, concurrency, recovery, retention, and BDMA write-boundary details against the current contract |
| P2 | Factory Portal/BFF implementation design | Close QR cryptography, replay/freshness, RBAC/session, PostgreSQL schema/migrations, deployment, and reconciliation details |
| P2 | Release and deployment runbooks | Define artifact authorization, R2/CDN delivery, rollback, environment, and production operational evidence |
| P3 | Streaming, PTT, GPS-route designs | Required before Phase 3 implementation |

The DCAM-BDMA Data Contract is current page version 17. The following are remaining design/requirement gaps around that baseline and the refreshed Technical Design set.

## Data and BDMA decisions

- Exact Android storage root exposed through ADB.
- Exact mapping from logical roots to physical paths on each supported BodyCamera/storage API.
- Complete embedded metadata fields, encoding, validation, and media-format support; standalone media JSON is excluded by contract 1.14.
- Exact `dcam.db` schema and schema-version negotiation.
- Stable local implementation of `dcam_cloud_device_id`, `serial_number`, provider-neutral installation metadata, app/contract metadata, and user/operator mapping; `android_id_hash` is explicitly not used.
- Source-state vocabulary; `recording`, `pending`, `completed`, `corrupted`, and `recovered` are only proposed directions.
- Schema evolution and backward-compatibility rules.
- MP4 MD5 performance evidence and exact sidecar/content implementation remain open; Build 0.1 applicability is decided: finalize first, checksum MP4, then expose affected item as `BDMA_READY`.
- Duplicate import, partial import, retry/resume, and interruption rules beyond the contracted result categories.
- Where import/sync state lives in `dcam.db` and how DCAM/BDMA coordinate concurrent access.
- Retention policy beyond the contracted post-success cleanup permissions.
- Import-error UX and ownership across DCAM and BDMA.

## Android and application decisions

- Final supported device matrix and real pilot hardware.
- Final `minSdk`, `targetSdk`, and `compileSdk` policy in documentation.
- Reference-device camera qualification evidence; Build 0.1 Decision Brief selects Android platform Camera API and excludes vendor SDK, without guessing unverified Camera1/Camera2 capability.
- Preview and background-recording requirements.
- Behavior when microphone is unavailable.
- Final package/module naming.
- MVVM confirmation at architecture/ADR level (the Development Standard already treats it as the project standard).
- Dependency injection approach; currently not mandatory.
- State and domain error model.
- Room versus direct SQLite/data-file strategy; documentation remains undecided.
- Gson versus Jackson.
- Service interface signatures and adapter selection per hardware model.
- Foreground Service lifecycle, process death, restart, and recording recovery.
- Device-policy mechanism for optional Home/Launcher role and boot startup.
- Managed-device provisioning plus Lock Task/Kiosk/exit-control policy.
- Screen on/off, dim/keep-awake, WakeLock, battery-optimization, and background-limit policy.
- Restart behavior after crash, service kill, reboot, abnormal shutdown, and device reconnect.

## Cloud, config, and update decisions

- Which later cloud capabilities are actually required and which provider(s) implement them behind the BFF boundary.
- Exact BFF desired-state, remote-config schema, rollout, and provider outage behavior.
- REST/API fallback and BDMA-desktop cloud responsibilities.
- Remote-config schema, keys, validation, and safe rollout behavior.
- Runtime override controls and config precedence. Device information may use internal `dcam_config.cson`; identity/provisioning mirror, remote-config cache, and operational settings belong in `dcam.db`.
- Self-update server/mechanism, signature validation, rollback, forced/silent update policy.
- Mandatory performance metrics and their local/cloud format.

## Security decisions

- AES-256 implementation details and exact media/metadata scope; `_enc` and `_IMP_enc` naming are already decided.
- Key creation, secure storage, distribution, rotation, recovery, and BDMA decryption.
- API/provider authentication and authorization, including per-device mTLS enrollment, proof-of-possession, rotation, revocation, and BFF mapping.
- Exact PKI algorithms, certificate validity/revocation mechanism, Keystore/StrongBox support, and production security evidence.
- Update signature verification.
- Sensitive metadata classification, log redaction, export, retention, and access control.
- Integrity behavior outside the contracted optional MP4 MD5 flow.

## Advanced-feature decisions

- Streaming protocol, service/provider, session lifecycle, timeout, reconnect, and weak-network behavior.
- PTT protocol/SDK, audio format, trigger/state model, and error recovery.
- GPS route sampling, accuracy, session model, storage, and downstream display.
- How future cloud sync or remote control modifies the currently local/ADB ownership boundary.

## Principal delivery risks

| Risk | Mitigation direction |
|---|---|
| Java/Desktop team's Android learning curve | Two-week onboarding, sample app, device exercises, review |
| Camera/firmware incompatibility | POC and sustained tests on every target BodyCamera |
| File corruption on crash/power loss | Temporary/final state, explicit recovery, integrity checks |
| Data Contract changes | Define and version it early with both DCAM and BDMA teams |
| GPS instability | Optional validity state and graceful fallback |
| Storage/battery constraints | Real-device profiling, thresholds, warnings, and bounded logging |
| Encryption degrading recording | Benchmark on target hardware before committing design |
| Streaming/PTT network quality | Weak-network, timeout, reconnect, and fallback tests |
| Scope creep | Jira/change control and PM review; do not spend buffer on new scope |

## Decision discipline

- Mark unknowns as TBD; do not let prototype choices silently become contracts.
- Validate hardware-sensitive choices with a POC on real BodyCamera devices.
- Record major decisions as ADRs.
- Update the Data Contract, both boundary/data architecture pages, BDMA docs, tests, and this digest together when ownership or schema changes.
