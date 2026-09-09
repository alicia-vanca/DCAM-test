# Confluence source inventory

Authenticated Confluence API snapshot refreshed on **2026-08-26** (Asia/Saigon) through `node download-confluence.js`. Credentials came from the gitignored local application properties and are not stored here. Earlier July sections below are retained as historical refresh notes.

Confluence remains the source of truth. This file records what was checked locally and how to interpret the local digest.

## August 26 full refresh

| Check | Result |
|---|---:|
| DVID space pages scanned | 172 |
| DCAM pages downloaded | 84 |
| Credential-bearing pages blocked | 0 |
| DCAM page paths changed from repository baseline | 51 |

The current original-page index is [`confluence-original/INDEX.md`](confluence-original/INDEX.md). Each downloaded page stores its Confluence page ID and current page version in its header. The downloader does not persist API update timestamps, so this inventory uses the current Confluence page versions and the refresh timestamp rather than inventing last-update times.

The six added current paths are the Factory Provisioning Portal/BFF API Contract, DCAM-23, DCAM-30, DCAM-6, the GMS-free Android Runtime ADR, and the Device API Credential/mTLS ADR. Page ID `49873154` was renamed from the former Web Portal/API contract title; the obsolete local filename was removed.

### Current implementation checkpoints

| Page | Page version | Current local summary |
|---|---:|---|
| [DCAM Project Home](https://ducviet.atlassian.net/wiki/spaces/DVID/pages/41648280) | 79 | [`README.md`](README.md), product/scope summaries |
| [DCAM Document Status Registry](https://ducviet.atlassian.net/wiki/spaces/DVID/pages/51085647) | 63 | [`06-refresh-2026-08-26.md`](confluence-summary/06-refresh-2026-08-26.md) |
| [DCAM Release & Build Applicability Matrix](https://ducviet.atlassian.net/wiki/spaces/DVID/pages/51020012) | 11 | MVP and feature summaries |
| [DCAM-BDMA Data Contract](https://ducviet.atlassian.net/wiki/spaces/DVID/pages/47743153) | 17 | `01-requirements/data-contract.md` |
| [10 - Android Device Operation Requirements](https://ducviet.atlassian.net/wiki/spaces/DVID/pages/48496661) | 13 | `01-requirements/android-device-operation.md` |
| [DCAM Architecture Home](https://ducviet.atlassian.net/wiki/spaces/DVID/pages/47185929) | 53 | `02-architecture/system-architecture.md` |
| [06 - Cloud Services, Update & Configuration Architecture](https://ducviet.atlassian.net/wiki/spaces/DVID/pages/47120459) | 33 | `02-architecture/cloud-quality-security.md` |
| [DCAM Android Development Standard](https://ducviet.atlassian.net/wiki/spaces/DVID/pages/47120580) | 16 | `03-development/android-standard.md` |
| [DCAM Security & Encryption Design](https://ducviet.atlassian.net/wiki/spaces/DVID/pages/48496720) | 22 | `05-technical-design/README.md` |
| [DCAM Logging & Diagnostics Design](https://ducviet.atlassian.net/wiki/spaces/DVID/pages/51019937) | 14 | `05-technical-design/README.md` |
| [DCAM Factory Provisioning Portal & BFF API Contract](https://ducviet.atlassian.net/wiki/spaces/DVID/pages/49873154) | 13 | `02-architecture/cloud-quality-security.md` |
| [ADR - DCAM GMS-free Android Runtime Baseline](https://ducviet.atlassian.net/wiki/spaces/DVID/pages/69730306) | 1 | `03-development/android-standard.md`, `05-technical-design/README.md` |
| [ADR – DCAM Device API Credential & mTLS Baseline](https://ducviet.atlassian.net/wiki/spaces/DVID/pages/70287362) | 1 | `06-refresh-2026-08-26.md` |
| [DCAM-23 Recording & Recovery Evidence](https://ducviet.atlassian.net/wiki/spaces/DVID/pages/63078416) | 1 | `01-requirements/mvp-baseline.md` |
| [DCAM-30 Camera/Import Evidence](https://ducviet.atlassian.net/wiki/spaces/DVID/pages/67436575) | 2 | `01-requirements/mvp-baseline.md` |
| [DCAM-6 Working Recording Slice Evidence](https://ducviet.atlassian.net/wiki/spaces/DVID/pages/66355221) | 5 | `01-requirements/mvp-baseline.md` |

The Registry page itself currently contains a top-level `81/81` approval-scope statement and a §4 `80/80` data-cutoff statement. This source-side discrepancy remains visible in the refresh digest and must not be used to infer release readiness.

## July 17 targeted refresh

Fetched and read:

- [DCAM Engineering Evidence & NAS Artifact SOP](https://ducviet.atlassian.net/wiki/spaces/DVID/pages/53608471), approved 1.0, page version 3, updated 2026-07-16 03:07 UTC.
- [DCAM Document Status Registry](https://ducviet.atlassian.net/wiki/spaces/DVID/pages/51085647), page version 19, updated 2026-07-17 00:57 UTC.
- [DCAM Project Home](https://ducviet.atlassian.net/wiki/spaces/DVID/pages/41648280), page version 68, updated 2026-07-17 00:57 UTC.

The engineering-evidence summary is in `docs/dcam-knowledge/confluence-summary/03-development/engineering-evidence.md`.

## July 9 targeted refresh

Fully fetched/read for this refresh:

- [DCAM Architecture Delivery Profile](https://ducviet.atlassian.net/wiki/spaces/DVID/pages/50626744), Approved 1.1, page version 4, updated 2026-07-09 08:42 UTC.

This page is the implementation guardrail for translating the larger target architecture into phase-scoped work. Its Phase 1 profile recommends no more than five Gradle modules (`:app`, `:core`, `:media`, `:storage`, and `:bdma-contract`), accepts a smaller initial `:app`/`:core` shape, requires package-first organization, and permits extraction only when an explicit split trigger exists.

## July 8 refresh scope

Fully fetched/read for this refresh:

- `4.2 - Technical Design` folder children, 15 pages total.
- `06 - Cloud Services, Update & Configuration Architecture`.
- `DCAM-BDMA Data Contract`.
- `10 - Android Device Operation Requirements`.
- `09 - System Settings Requirements`.
- `DCAM Architecture Home`.
- `DCAM Android Development Standard`.

Metadata-only CQL check also found many additional pages changed on **2026-07-08**. Not every older topic summary in this local digest has been fully rewritten from those changed pages; compare page versions before relying on any major product, schema, release, or architecture decision.

## Key pages refreshed

| Area | Page | Version | Last API update (UTC) |
|---|---|---:|---|
| Root | [DCAM](https://ducviet.atlassian.net/wiki/spaces/DVID/pages/36536321/DCAM) | 10 | 2026-07-08 04:16 |
| Root | [DCAM Project Home](https://ducviet.atlassian.net/wiki/spaces/DVID/pages/41648280/DCAM+Project+Home) | 44 | 2026-07-08 10:05 |
| Product | [DCAM Project Charter](https://ducviet.atlassian.net/wiki/spaces/DVID/pages/41156610) | 15 | 2026-07-08 01:25 |
| Product | [DCAM Product Vision](https://ducviet.atlassian.net/wiki/spaces/DVID/pages/41648238) | 5 | 2026-07-03 02:13 |
| Product | [DCAM Roadmap](https://ducviet.atlassian.net/wiki/spaces/DVID/pages/41615474) | 8 | 2026-07-03 01:28 |
| Product | [DCAM MVP Scope](https://ducviet.atlassian.net/wiki/spaces/DVID/pages/42532866) | 5 | 2026-07-03 02:13 |
| Sprint operations | [DCAM Phase 2 Documentation Plan](https://ducviet.atlassian.net/wiki/spaces/DVID/pages/41189378) | 8 | 2026-07-03 06:54 |
| Sprint operations | [DCAM 9-Month Development Plan](https://ducviet.atlassian.net/wiki/spaces/DVID/pages/46759955) | 2 | 2026-07-03 02:14 |
| Requirements | [DCAM-BDMA Data Contract](https://ducviet.atlassian.net/wiki/spaces/DVID/pages/47743153) | 7 | 2026-07-08 05:50 |
| Requirements | [04 - Device Configuration Requirements](https://ducviet.atlassian.net/wiki/spaces/DVID/pages/47710554) | 5 | 2026-07-08 05:49 |
| Requirements | [05 - User & Device Operation Requirements](https://ducviet.atlassian.net/wiki/spaces/DVID/pages/47710574) | 5 | 2026-07-08 01:34 |
| Requirements | [08 - Security & Encryption Requirements](https://ducviet.atlassian.net/wiki/spaces/DVID/pages/47710594) | 6 | 2026-07-08 01:41 |
| Requirements | [09 - System Settings Requirements](https://ducviet.atlassian.net/wiki/spaces/DVID/pages/47710614) | 15 | 2026-07-08 08:55 |
| Requirements | [10 - Android Device Operation Requirements](https://ducviet.atlassian.net/wiki/spaces/DVID/pages/48496661) | 9 | 2026-07-08 09:05 |
| Requirements | [DCAM Non-functional Requirements](https://ducviet.atlassian.net/wiki/spaces/DVID/pages/48595009) | 11 | 2026-07-08 07:42 |
| Architecture | [DCAM Architecture Home](https://ducviet.atlassian.net/wiki/spaces/DVID/pages/47185929) | 24 | 2026-07-08 09:37 |
| Architecture | [01 - Architecture Overview](https://ducviet.atlassian.net/wiki/spaces/DVID/pages/47120395) | 10 | 2026-07-08 02:44 |
| Architecture | [02 - Architecture Principles](https://ducviet.atlassian.net/wiki/spaces/DVID/pages/47120416) | 12 | 2026-07-08 02:44 |
| Architecture | [03 - Android Platform & Compatibility Strategy](https://ducviet.atlassian.net/wiki/spaces/DVID/pages/47120437) | 11 | 2026-07-08 09:13 |
| Architecture | [04 - Application & Module Architecture](https://ducviet.atlassian.net/wiki/spaces/DVID/pages/47218698) | 14 | 2026-07-08 09:14 |
| Architecture | [05 - Data, Storage & BDMA Architecture](https://ducviet.atlassian.net/wiki/spaces/DVID/pages/47185950) | 9 | 2026-07-08 02:45 |
| Architecture | [06 - Cloud Services, Update & Configuration Architecture](https://ducviet.atlassian.net/wiki/spaces/DVID/pages/47120459) | 21 | 2026-07-08 08:56 |
| Architecture | [07 - Logging, Diagnostics, Performance & Security](https://ducviet.atlassian.net/wiki/spaces/DVID/pages/47185971) | 9 | 2026-07-08 02:20 |
| Architecture | [08 - DCAM-BDMA Integration Boundary](https://ducviet.atlassian.net/wiki/spaces/DVID/pages/47153235) | 9 | 2026-07-08 02:27 |
| Architecture | [DCAM Architecture Delivery Profile](https://ducviet.atlassian.net/wiki/spaces/DVID/pages/50626744) | 4 | 2026-07-09 08:42 |
| Android development | [DCAM Android Training & Architecture Onboarding](https://ducviet.atlassian.net/wiki/spaces/DVID/pages/46825510) | 9 | 2026-07-08 07:46 |
| Android development | [DCAM Android Development Standard](https://ducviet.atlassian.net/wiki/spaces/DVID/pages/47120580) | 10 | 2026-07-08 07:39 |
| Governance | [DCAM Documentation Governance](https://ducviet.atlassian.net/wiki/spaces/DVID/pages/47120620) | 8 | 2026-07-08 09:57 |
| ADR | [ADR - DCAM Android Dedicated Device / Device Owner / Lock Task Decision](https://ducviet.atlassian.net/wiki/spaces/DVID/pages/49774787) | 1 | 2026-07-08 07:28 |
| QA | [DCAM QA Test Strategy & Test Matrix](https://ducviet.atlassian.net/wiki/spaces/DVID/pages/49545345) | 7 | 2026-07-08 10:13 |
| QA | [DCAM Device POC & Hardware Validation Report](https://ducviet.atlassian.net/wiki/spaces/DVID/pages/49545399) | 6 | 2026-07-08 09:39 |
| Factory | [DCAM Factory Provisioning & Device Production SOP](https://ducviet.atlassian.net/wiki/spaces/DVID/pages/49545629) | 2 | 2026-07-08 10:17 |

## Technical Design folder

The pages below live under [4.2 - Technical Design](https://ducviet.atlassian.net/wiki/spaces/DVID/folder/47120392). Per project guidance, treat them as draft/expected target behavior, not as a concrete description of the current repository. After implementation, these documents should be corrected/completed officially.

| Page | Version | Last API update (UTC) |
|---|---:|---|
| [DCAM Android Device Owner & Kiosk Policy Design](https://ducviet.atlassian.net/wiki/spaces/DVID/pages/49840280) | 3 | 2026-07-08 08:53 |
| [DCAM Android Operation Design](https://ducviet.atlassian.net/wiki/spaces/DVID/pages/48562239) | 14 | 2026-07-08 08:59 |
| [DCAM BDMA Integration Technical Design](https://ducviet.atlassian.net/wiki/spaces/DVID/pages/48595030) | 5 | 2026-07-08 05:54 |
| [DCAM Device Capability & Feature Eligibility Design](https://ducviet.atlassian.net/wiki/spaces/DVID/pages/48758788) | 5 | 2026-07-08 09:15 |
| [DCAM Device Provisioning Web Portal Design](https://ducviet.atlassian.net/wiki/spaces/DVID/pages/49315858) | 5 | 2026-07-08 09:58 |
| [DCAM In-App Operation, Device Settings & Media Console Design](https://ducviet.atlassian.net/wiki/spaces/DVID/pages/49840330) | 8 | 2026-07-08 08:47 |
| [DCAM Realtime AI Detection Design](https://ducviet.atlassian.net/wiki/spaces/DVID/pages/48595090) | 7 | 2026-07-08 02:57 |
| [DCAM Recording & Capture Design](https://ducviet.atlassian.net/wiki/spaces/DVID/pages/48529484) | 9 | 2026-07-08 02:54 |
| [DCAM Security & Encryption Design](https://ducviet.atlassian.net/wiki/spaces/DVID/pages/48496720) | 10 | 2026-07-08 08:57 |
| [DCAM Self Update Design](https://ducviet.atlassian.net/wiki/spaces/DVID/pages/48529439) | 8 | 2026-07-08 09:18 |
| [DCAM Sensor & Location Monitoring Design](https://ducviet.atlassian.net/wiki/spaces/DVID/pages/48496794) | 6 | 2026-07-08 02:57 |
| [DCAM SQLite Database Design](https://ducviet.atlassian.net/wiki/spaces/DVID/pages/48529463) | 11 | 2026-07-08 09:17 |
| [DCAM State Machine Design](https://ducviet.atlassian.net/wiki/spaces/DVID/pages/48496753) | 10 | 2026-07-08 09:16 |
| [DCAM Storage Design](https://ducviet.atlassian.net/wiki/spaces/DVID/pages/48496699) | 6 | 2026-07-08 02:55 |
| [DCAM Web Portal & Device API Contract](https://ducviet.atlassian.net/wiki/spaces/DVID/pages/49873154) | 1 | 2026-07-08 09:56 |

## Documentation structure update

- `03 - Requirements` now includes the approved Data Contract, functional requirements, System Settings Requirements, Android Device Operation Requirements, and updated Non-functional Requirements.
- `4.2 - Technical Design` now contains a broad draft design set covering runtime operation, kiosk/device owner, in-app console/settings/media, recording, storage, SQLite, BDMA, capability, provisioning/API, self-update, security, sensors/location, and AI.
- [4.4 - Architecture Decision Records](https://ducviet.atlassian.net/wiki/spaces/DVID/folder/41189399)
- [05 - Release Management](https://ducviet.atlassian.net/wiki/spaces/DVID/folder/36438024)
- [06 - Incident Log](https://ducviet.atlassian.net/wiki/spaces/DVID/folder/36372488)

## Refresh guidance

Before relying on this digest for a major product, schema, release, or architecture decision:

1. Compare page versions/update timestamps with this inventory.
2. Re-read any changed page.
3. Update the relevant topic file and this inventory.
4. Preserve explicit distinction between approved decisions, draft target design, TBDs, local implementation notes, and current code behavior.
