# Product brief and PM intent

Source refresh: **2026-07-15**. Build 0.1 scope is governed by Release & Build Applicability Matrix plus DEC-01-DEC-07 Decision Brief; long-term product direction does not make deferred features release blockers.

## One-sentence definition

DCAM is a dedicated, Java-first Android application for DVID BodyCamera devices that reliably records video and captures images at the source, creates structured local media and metadata, and exposes that data for BDMA Desktop to ingest and manage.

It is not intended to be a public Android camera app or a replacement for BDMA.

## The product idea

DCAM is the field-side data producer in the DVID BodyCamera ecosystem. BDMA is the downstream desktop consumer and manager.

```text
Field operator
    ↓
DCAM on BodyCamera
    creates media + metadata + device/user context + logs
    stores and exposes source data locally
    ↓ ADB, initiated by BDMA
BDMA Desktop
    imports + validates + indexes + displays + backs up + exports + reports
```

The long-term product is broader than a camera: capture, structured evidence data, streaming, communication, device management, user/operator context, security, and GPS. The first obligation, however, is trustworthy capture and a clean handoff to BDMA.

## What the PM wants

The documents consistently optimize for these outcomes:

- **Reliability first:** recording and capture must survive realistic BodyCamera constraints; minimizing lost or corrupt data matters more than UI polish or secondary features.
- **BDMA compatibility from day one:** deterministic files, metadata, status, and schema versioning should prevent BDMA from guessing or accumulating special cases.
- **Simple field operation:** common actions should require few steps and work on dedicated hardware controls where appropriate.
- **Recoverable and observable behavior:** interrupted, pending, completed, corrupt, or recovered data must be distinguishable; important flows need useful logs.
- **Hardware-aware operation:** the app must handle limited battery/storage, unstable GPS/network, vendor firmware differences, and missing capabilities without crashing.
- **A platform that can grow:** Device/User foundations arrive before streaming and PTT so later features do not force a major rewrite.
- **A customer pilot after nine months:** the goal is a pilot/production candidate with documentation and test evidence, not a claim that every advanced capability is fully production-hardened.

## Users and their needs

| User | Primary need |
|---|---|
| Field BodyCamera operator | Fast, low-friction capture without lost data |
| Operations/supervision | Clear device health and trustworthy recorded data |
| BDMA operator | Importable files and correct device/time/user/status mapping |
| Engineering/support | Logs, states, and diagnostics sufficient to explain failures |
| PM/customer pilot stakeholders | A stable end-to-end workflow and evidence of readiness |

## Product principles

- Offline-first core operation.
- Reliability before optional capability.
- BDMA-compatible by design.
- Simple field interaction.
- Recoverable data and explicit states.
- Observable behavior with local-first diagnostics.
- Versioned, extensible metadata.
- Security-aware handling of media, metadata, config, update, and transmission.
- Capability-based behavior across BodyCamera models.
- Provider-agnostic cloud integration.

## Success measures

The MVP documents set these concrete targets:

| Measure | Target |
|---|---:|
| Video recording success | ≥ 99% |
| Image capture success | ≥ 99% |
| BDMA import success | 100% |
| Required metadata completeness | 100% |
| Critical file corruption | 0 cases |
| Critical crashes in main record/capture flows | 0 |

Longer-term measures also include GPS availability, storage handling reliability, app crash rate, device/user mapping readiness, and pilot feedback.

## Product boundary

- DCAM owns Android-side capture, original media/metadata, local states, device context, and source logs.
- BDMA owns desktop connection, pull/sync, import, validation, indexing, long-term management, display, backup, export, and reports. Contract 1.6 also permits controlled device-info/database write-back and post-import source cleanup; it may not modify source media, embedded metadata, MD5 content, temp files, active runtime state, or logs.
- Cloud is not required for core operation.
- Customer Pilot / Production Candidate is narrower than full commercial production readiness.
- Remote device management and advanced user management in Phase 2 are foundations, not full fleet management or enterprise IAM.
- Streaming, PTT, full GPS route, and advanced encryption in Phase 3 are beta/basic capabilities.

## Governance signals

- Sponsor: DVID.
- Product Owner and Project Manager: Hoàng Ngọc Quyền.
- Delivery model: Agile Scrum, two-week sprints in the detailed plan.
- Team assumption: three developers, shared/phase QA, BDMA team participating in contract and integration work.
- Work tracking: Jira; documentation: Confluence; source control/code review: Git and pull requests.
- Major technical decisions should be recorded as ADRs.
