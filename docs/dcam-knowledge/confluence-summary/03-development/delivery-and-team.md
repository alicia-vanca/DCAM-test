# Delivery model, team, and documentation workflow

Source refresh: **2026-08-26**. Documentation workflow now includes registry synchronization, Build 0.1 applicability, DEC-01-DEC-07 traceability, evidence links, GMS-free dependency/source checks, and BFF/mTLS/PKI qualification gates before any production claim.

## Delivery assumptions

| Item | Assumption |
|---|---|
| Duration | 9 months |
| Sprint | 2 weeks |
| Estimated sprints | 18 |
| Developers | 3 |
| QA | Shared or assigned by phase |
| Product Owner | Internal |
| PM | Hoàng Ngọc Quyền |
| Platform | Android BodyCamera |
| Desktop partner | BDMA Desktop |
| Language | Java-first |
| Final target | Customer Pilot / Production Candidate |

## Execution strategy

- Train the Java/Desktop team before feature delivery.
- Establish capture/storage/metadata/logging foundations first.
- Integrate BDMA early and version the contract.
- Build Device/User foundations before streaming and PTT.
- Deliver demonstrable builds at each phase.
- Reserve the final phase for regression, stability, performance, security, documentation, and pilot preparation.
- Use schedule buffer to absorb technical risk, never to silently add scope.

## Sprint focus

| Sprints | Focus |
|---|---|
| 1 | Training and environment |
| 2 | Camera/record/capture POC |
| 3–6 | MVP foundation |
| 7–10 | Data Contract, BDMA, Device/User, basic encryption |
| 11 | Phase 2 integration/stabilization |
| 12–14 | Streaming, PTT, GPS route, advanced encryption |
| 15–16 | Advanced-feature hardening/beta validation |
| 17 | Regression, performance, stability, security |
| 18 | Release candidate, pilot, and documentation |

## Role boundaries

- Product Owner prioritizes scope and acceptance direction.
- PM plans execution, tracks risk/progress, and coordinates release/documentation.
- Tech Lead owns architecture, technical decisions, review direction, and implementation quality.
- Android developers implement and test device-side behavior.
- BDMA team co-owns the Data Contract and desktop ingest/mapping/E2E validation.
- QA creates and executes integration, regression, stability, and release tests.

## Documentation by phase

| Phase | Expected artifacts |
|---|---|
| Training | Onboarding guide and camera prototype notes |
| Phase 1 | Functional requirements, MVP checklist, known issues, MVP release notes |
| Phase 2 | Data Contract, Device/User notes, integration report |
| Phase 3 | Streaming/PTT/GPS-route designs and advanced test checklist |
| Phase 4 | Release notes, installation guide, test report, known issues, pilot notes |

## Documentation maintenance rules

- One authoritative document per concern.
- Keep Product direction, Requirements, Architecture, and Technical Design distinct.
- Keep training guidance separate from the architecture baseline and implementation standard.
- Link important requirements/changes to Jira.
- Use explicit TBDs rather than assumptions.
- Create an ADR when a major decision becomes stable or violates an existing principle.
- Update Project Home and cross-links when pages move or are added.
- Update relevant documentation before release.
- Synchronize changed page metadata with Document Status Registry in same change set.
- Store raw Build 0.1 evidence in internal NAS `DCAM-EVID-NAS-01`; keep Confluence/Jira limited to SOP, metadata, conclusions, and evidence links.
- Keep working evidence summaries such as DCAM-23, DCAM-30, and DCAM-6 separate from the parent Device POC conclusion and release approval.
- Treat Spring Boot BFF/Thymeleaf, PostgreSQL `ddmp`, per-device mTLS, Keystore proof-of-possession, and GMS-free runtime details as controlled cross-document authorities; synchronize their page metadata through the Registry.
- Use Jira-linked Evidence IDs and immutable run folders; never overwrite reviewed evidence runs.
- Trace each Build 0.1 decision through requirement/design, Jira issue, implementation/PR, QA Test ID, and evidence link.
- Do not mark Pending Device POC rows Covered/Passed before identifiable physical-device evidence is attached.
- Do not treat Approved Provisional Baseline or PM-approved reference configuration as production approval.
