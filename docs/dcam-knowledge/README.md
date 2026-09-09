# DCAM knowledge base

This folder is a local, task-oriented digest of the DCAM Confluence space. It is intended to give a developer or AI assistant enough context to reason about product intent, scope, architecture, and delivery without rereading every source page.

Snapshot date: **2026-08-26** (Asia/Saigon). The authenticated refresh scanned 172 DVID pages and downloaded 84 DCAM pages into the original mirror; no pages were blocked by the credential-bearing-page filter. Current sources include the mandatory GMS-free Android runtime baseline, Spring Boot BFF/Thymeleaf Factory Portal with PostgreSQL `ddmp`, per-device mTLS/Android Keystore proof-of-possession direction, provider-independent diagnostics, and new Build 0.1 evidence summaries. Confluence remains source of truth.

Repository-local implementation notes, evidence reports, and plans live outside this mirror in [docs/local-dev](../local-dev/README.md).

Technical Design pages under 4.2 are draft/target-direction material unless otherwise finalized through implementation and review. Treat them as expected design intent, not as current repository behavior.

## Read this first

1. [Product brief](00-product/product-brief.md) — what DCAM is and what the PM is optimizing for.
2. [Scope and roadmap](00-product/scope-and-roadmap.md) — MVP, phases, milestones, and delivery boundary.
3. [DCAM-BDMA Data Contract](01-requirements/data-contract.md) — the approved storage, naming, checksum, access, import, and cleanup baseline.
4. [MVP requirement baseline](01-requirements/mvp-baseline.md) — the consolidated product baseline plus the newly created Requirements structure.
5. [Open decisions](01-requirements/open-decisions.md) — what is explicitly TBD and must not be invented.
6. [System architecture](02-architecture/system-architecture.md) — architectural principles, layers, modules, Phase 1 delivery profile, and platform strategy.
7. [DCAM–BDMA and data boundary](02-architecture/data-and-bdma.md) — ownership, ADB integration, metadata, and storage direction.
8. [Development standard](03-development/android-standard.md) — the implementation rules expected by the architecture documents.
9. [Android device operation](01-requirements/android-device-operation.md) — full-screen, boot, launcher/kiosk, lifecycle, service, permission, power, and recovery requirements.
10. [Technical design drafts](05-technical-design/README.md) — current target design for operation, kiosk, console/settings, recording, storage, DB, BDMA, cloud provisioning, update, security, sensors, and AI.
11. [2026-08-26 refresh digest](06-refresh-2026-08-26.md) — cross-cutting authority changes, evidence boundaries, and current page/version checkpoints.

## Topic tree

```text
dcam-knowledge/
├── 00-product/
│   ├── product-brief.md
│   └── scope-and-roadmap.md
├── 01-requirements/
│   ├── mvp-baseline.md
│   ├── data-contract.md
│   ├── android-device-operation.md
│   └── open-decisions.md
├── 02-architecture/
│   ├── system-architecture.md
│   ├── data-and-bdma.md
│   └── cloud-quality-security.md
├── 03-development/
│   ├── android-standard.md
│   └── delivery-and-team.md
├── 04-features/
│   └── feature-map.md
├── 05-technical-design/
│   └── README.md
├── 06-refresh-2026-08-26.md
└── sources.md
```

## Ground rules for future work

- Preserve the priority order: **reliable capture → correct local data → BDMA compatibility → advanced features**.
- Core capture must work offline. Cloud is optional and may not block recording, capture, metadata, storage, or local logging.
- Treat BodyCamera hardware, firmware, Android version, GMS, GPS, storage, and network as capabilities, not assumptions.
- Treat the GMS-free ADR as a mandatory production Android runtime guard: no GMS/Play Store/Google-account/FCM/Analytics/Play Integrity dependency or flow. Crashlytics, if retained, is optional bounded telemetry only.
- DCAM creates and exposes source data; BDMA initiates ADB reads and owns desktop import, indexing, management, display, backup, and export.
- Follow the approved Data Contract for logical roots, folders, media naming, MD5 scope, BDMA permissions, import results, and cleanup. Do not invent physical paths, embedded metadata fields, DB schema, key management, recovery, streaming, or PTT details that remain outside it.
- Distinguish the documented target from the current prototype. See [local current repository state](../local-dev/current-repo/current-state.md).
- Apply the approved Architecture Delivery Profile when turning target architecture into Jira or implementation work: prove the recording/storage/BDMA vertical slice before platform expansion, keep Phase 1 at no more than five Gradle modules, and split packages only when a documented trigger is present.
- Never copy local values into documentation, source control, or logs. Local build-property policy is documented in the gitignored `application-local.properties` file.

## Current documentation gap

The current Confluence set contains the requirements, architecture, technical designs, ADRs, release applicability, governance registry, and working Build 0.1 evidence summaries. The remaining risk is synchronization and qualification: implementation evidence, Device POC/security/deployment gates, exact PKI/schema values, and release approval must still be completed where the source pages mark them pending. Do not treat the new evidence summaries or approved directions as production approval.

See [sources.md](sources.md) for the complete page/version inventory.
