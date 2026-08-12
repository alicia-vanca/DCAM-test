# DCAM MVP Readiness Report

> **Historical snapshot.** This report preserves evidence from its audit date. Do not use its package names or architecture conventions for new code. Use [`ARCHITECTURE.md`](../../app/src/main/java/com/dvid/dcam/ARCHITECTURE.md) and [`FEATURE_DEVELOPMENT_GUIDE.md`](../../app/src/main/java/com/dvid/dcam/FEATURE_DEVELOPMENT_GUIDE.md) instead.

**Date:** 2026-07-10  
**Status:** Historical implementation snapshot
**Source:** Full repo and document review

## Project Overview

**DCAM** is a dedicated Android BodyCamera app by DVID. Producer-side in a two-part ecosystem:
- **DCAM** (this repo): captures video/images, stores media + metadata, exposes via ADB
- **BDMA Desktop**: ingests, validates, indexes, displays, manages downstream

**MVP goal:** End-to-end recording → finalization → BDMA-readiness on real hardware.

## Architecture Snapshot

| Concern | Current |
|---|---|
| Modules | `:app` (Android) + `:core` (pure Java) — approved small shape |
| Language | Java 17, Java 21 toolchain |
| UI | XML + ViewBinding + LiveData |
| Camera | CameraX 1.6.1 (prototype; no real-device POC yet) |
| DB | Room 2.8.4, 6 entity tables (auth/session/config/device/logs/runtime settings) |
| Recording | CameraX gateway → `VideoRecordingUseCaseImpl` — Activity-owned, not service-owned |
| Auth | Password-first login, bcrypt cost 10, boot-scoped sessions, Room-based |
| Storage | Media/* folders, Temp exists but unused; files write directly to final paths |
| Logging | Local logcat + `Logs/logs.txt` with rotation, Loggly outbox |
| Encryption | AES-256-CTR exists in code; MVP must force OFF via ADR-006 |
| Tests | 55 JVM tests passing; `assembleDebug` passes |

## Document Map

```
docs/
├── dcam-knowledge/          ← Confluence digest (source of truth)
│   ├── 00-product/          product-brief, scope-and-roadmap
│   ├── 01-requirements/     mvp-baseline, data-contract, android-device-operation, open-decisions
│   ├── 02-architecture/     system-architecture, data-and-bdma, cloud-quality-security
│   ├── 03-development/      android-standard, delivery-and-team
│   ├── 04-features/         feature-map
│   ├── 05-technical-design/ draft design pages (expectation, not current code)
│   ├── sources.md           Confluence page inventory with versions
│   └── README.md            reading order
├── local-dev/
│   ├── current-repo/        current-state, settings-grid-map, Huong-dan-dev PDF
│   ├── evidence/            source-structure-requirements-evidence
│   └── plans/
│       ├── mvp-delivery-plan.md   ← primary delivery playbook
│       ├── database-ddl-v1-design.md
│       └── database-ddl-v1.sql
```

## Key Documents Summary

### Product (docs/dcam-knowledge/00-product/)

| Document | Key content |
|---|---|
| **product-brief.md** | One-sentence definition: dedicated Java Android app for BodyCamera that records/captures, creates structured local media+metadata, exposes to BDMA. Success: ≥99% record/capture, 100% BDMA import, 0 corruption, 0 critical crashes. |
| **scope-and-roadmap.md** | 9-month plan: Phase 1 (MVP) months 1-3, Phase 2 (BDMA) months 4-5, Phase 3 (advanced) months 6-7, Phase 4 (hardening) months 8-9. M2 MVP 0.1 target end week 12. |

### Requirements (docs/dcam-knowledge/01-requirements/)

| Document | Key content |
|---|---|
| **mvp-baseline.md** | Recording + image capture + local storage + metadata + device status + logging + BDMA integration. Non-functional: offline-first, ≥99% reliable, no UI thread blocking, capability-based across hardware variants. 8 acceptance criteria. |
| **data-contract.md** | Approved 1.6. Logical roots: `Media/{Video,Image,Audio,IMP}`, `Config/dcam_config.cson`, `Database/dcam.db`, `Logs/logs.txt`, `Temp`. Filename: `DCAM_<CameraID>_<UserID>_<yyyyMMdd>_<HHmmss>[_IMP][_enc].<ext>`. MP4-only optional MD5. No standalone media JSON. |
| **android-device-operation.md** | Approved 1.8. Dedicated screen, Device Owner/DPC support, Lock Task, kiosk, operator session required for normal capture, emergency override via `EMERGENCY_OVERRIDE_ADMIN` identity, foreground operation, recovery after crash/reboot/corruption. |
| **open-decisions.md** | ~40+ documented TBDs: physical storage roots, CameraX vs Camera2 POC pending, FGS ownership, Room vs raw SQLite, RIR avoidance, encryption/key management, streaming protocols, DB concurrency protocol, embedded metadata fields encoding. |

### Architecture (docs/dcam-knowledge/02-architecture/)

| Document | Key content |
|---|---|
| **system-architecture.md** | Feature-first Clean Architecture + Ports & Adapters. 4 standard layers: Domain → Application → Interface Adapter → Frameworks. MVVM at UI boundary. Phase 1: max 5 Gradle modules (accepts current `:app`/`:core`). |

### Development Standard (docs/dcam-knowledge/03-development/)

| Document | Key content |
|---|---|
| **android-standard.md** | Java-first, XML+ViewBinding, Clean Architecture, LiveData, EventHandler for serialized hardware, Repository boundaries, no direct SDK calls from UI/usecase. PR checklist enforces dependency direction. |

### MVP Delivery Plan (docs/local-dev/plans/)

| Document | Key content |
|---|---|
| **mvp-delivery-plan.md** | Comprehensive: capability matrix, current-state assessment, 3 hazards, 4-sprint sequence, product surface (visible/hidden), target boundaries, DDL requirements, recording state machine, recording finalization order, failure test matrix (17 scenarios), 8 ADR register, test strategy, Definition of Done, 13 explicit non-goals. |

## Current Implementation State

### What Works (implemented & tested)
- 2-module Gradle shape (`:app` → `:core`)
- Feature-first packages with Clean Architecture layering
- CameraX photo/video capture prototype (unverified on target hardware)
- Password-first login with bcrypt cost 10; bcrypt salts are embedded in stored hashes
- Boot-scoped operator session + session invalidation on reboot
- Room v1 with 6 entity tables: UserProfile, UserAuthMethod, OperatorSession, DeviceIdentity, OperationalSetting, PendingLog, RemoteConfig
- Local logging with rotation + Loggly outbox
- Battery/storage/GPS status reporting
- Read-only Files browser for Media/* folders
- Language switching
- Physical hardware key routing
- Developer feature gates + menu hiding
- 55 JVM tests passing

### What's In Progress
- Login/auth: missing failed-attempt throttle/lockout
- Operator session: navigation/hardware guards exist, but recording authority doesn't enforce session check

### What's Missing (blocking MVP)
- **Sprint 0 gates:** Target-device Camera/FGS/storage POC, ADR approvals, encryption policy (force OFF), no-op cloud init removal from startup
- **Recording authority:** No single application coordinator; CameraX gateway has mutable active recording
- **Staging/finalization:** Camera writes directly to final `Media/*` paths; no `Temp`, no `BDMA_READY`
- **Service ownership:** `RecordingForegroundService` is `START_NOT_STICKY`, doesn't own CameraX/finalization
- **DDL v1 runtime tables:** Missing media_session, media_finalization_state, app_runtime_state, foreground_service_state tables
- **Recovery:** No startup recovery use case; no DB/filesystem reconciliation
- **BDMA E2E:** No proof of ADB-readable contract, no MD5 workflow, no joint import fixture
- **Encryption policy:** AES-256-CTR exists but must be OFF by ADR-006 until approved
- **Cloud init:** `AppComposition` still creates no-op remote config during startup even for MVP

## 8 ADR Decisions Required

| ADR | Must settle by | Status |
|---|---|---|
| ADR-001 | Java/XML/MVVM/package architecture confirmation | Sprint 0 — **OPEN** |
| ADR-002 | CameraX vs Camera2 vs vendor SDK | Before Sprint 2 — **OPEN** |
| ADR-003 | Room vs raw SQLite, BDMA concurrency | Before DDL v1 ships — **OPEN** |
| ADR-004 | FGS ownership, restart, target-SDK behavior | Before Sprint 2 — **OPEN** |
| ADR-005 | Physical storage roots, finalization mechanics | Before Sprint 2 — **OPEN** |
| ADR-006 | MVP encryption ON/OFF (default OFF) | Sprint 0 — **OPEN** |
| ADR-007 | Password-first auth, emergency policy | Before Sprint 1 acceptance — **OPEN** |
| ADR-008 | BDMA write-back tables/fields, locking, cleanup | Before Sprint 3 — **OPEN** |

## Sprint Plan (from mvp-delivery-plan.md)

| Sprint | Goal | Key Deliverables |
|---|---|---|
| Sprint 0 | Scope truth, POC, decisions | ADRs, Camera POC, encryption OFF policy, cloud cleanup, failure matrix |
| Sprint 1 | Authenticated command authority | Runtime startup ordering, session enforcement, DDL v1 entities, recording authority with prechecks |
| Sprint 2 | Service-owned recording + finalization | FGS hosting, Temp/staging, finalization workflow, `BDMA_READY` publication, storage thresholds |
| Sprint 3 | Recovery, emergency, BDMA E2E | Startup recovery, DB/filesystem reconciliation, emergency identity, ADB contract proof, import tracking |
| Sprint 4+ | Hardening, release candidate | Real device failure matrix, long-duration tests, profiling, version freeze, docs |

## Critical Risks

1. **No real BodyCamera POC yet** — CameraX/gateway/storage behavior untested on target hardware
2. **Encryption enabled in code** — must force OFF by ADR-006 for MVP
3. **Cloud init runs on startup** — violates "no hidden behavior for disabled features"
4. **SOS/emergency state as app action** — no protected identity or policy implemented
5. **Audio recording exists but not in MVP** — must be hidden without breaking shared infra
6. **Demo settings resemble completed features** — do not persist or apply approved operational settings

## Recommendation

**Sprint 0 must close before Sprint 1 implementation continues.** The ADRs, device POC, encryption policy, and startup cleanup are preconditions for the remaining work to be grounded in real hardware evidence rather than assumptions.
