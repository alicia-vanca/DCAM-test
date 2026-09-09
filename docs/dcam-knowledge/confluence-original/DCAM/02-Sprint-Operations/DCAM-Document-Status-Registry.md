# DCAM Document Status Registry

**Page ID**: 51085647  
**Version**: 63  
**Type**: page  
**URL**: https://ducviet.atlassian.net/wiki/spaces/DVID/pages/51085647

---


# DCAM Document Status Registry

Item

Information

Project

DCAM (Android BodyCamera Application)

Document Type

Document Status / Version Registry

Version

Approved 1.44

Status

Approved

Approval Scope

Cross-document version/status/approval-scope summary; coverage 81/81 current pages. This controlled change aligns Factory Portal provisioning with Spring Boot BFF, Docker deployment, host-native PostgreSQL and the mandatory mTLS pending-enrollment/proof-of-possession boundary. It does not change Build 0.1 scope or grant production approval to QR/PKI/RBAC implementation details.

Owner

Hoàng Ngọc Quyền

Technical Reviewer

Tech Lead / Documentation Owner

Approver

Hoàng Ngọc Quyền

Parent Folder

02  Sprint Operations

Target Audience

PM/BA, Tech Lead, Document Owners, QA, Reviewers, Approvers

Last Updated

2026-08-26

Related Jira

None

Related Documents

DCAM Documentation Governance, DCAM Project Home, DCAM Architecture Home, DCAM Requirements Home, DCAM Release & Build Applicability Matrix, DCAM Requirement–Design–Test Traceability Matrix, Decision Brief – DCAM MVP Internal Build 0.1 – Working Recording Slice, [Decision Brief – DCAM Phase 2 Web Portal Scope Precedence](/wiki/spaces/DVID/pages/54296681/Decision+Brief+DCAM+Phase+2+Web+Portal+Scope+Precedence)

## 1. Purpose

Trang này là registry duy nhất để tổng hợp version, document status, approval scope và dependency condition của các tài liệu DCAM.

Document page metadata = trạng thái chính thức của chính tài liệu đó.
DCAM Document Status Registry = nơi tổng hợp duy nhất để review toàn bộ bộ tài liệu.
Navigation pages must not copy version/status tables.
Khi version hoặc status của một tài liệu thay đổi, document owner phải cập nhật metadata của trang và registry này trong cùng change set. Change set Hybrid ngày 2026-08-24 đã đồng bộ các DCAM ADR/design affected và navigation/knowledge pages. Change set ngày 2026-08-25 bổ sung approved ADR GMS-free Android Runtime Baseline và đồng bộ Architecture Home/Registry; change set sau đó chốt delivery Diagnostics offline-first: DCAM lưu cục bộ có giới hạn, BFF xác nhận theo `event_id`, còn provider vận hành là downstream thay thế được. DDMP giữ registry riêng theo governance của DDMP. Change set hiện tại chuyển Factory Portal sang Spring Boot BFF + Thymeleaf và PostgreSQL ddmp, đồng bộ Cloud/Identity/SOP/BFF contracts; Firebase không còn là provisioning or device-management authority.

## 2. Status Taxonomy

Status

Meaning

Allowed Use

Draft

Nội dung đang soạn hoặc chưa hoàn tất review bắt buộc.

Không dùng làm production baseline.

Approved Direction

Product/architecture direction đã được chấp thuận; implementation detail hoặc evidence chưa hoàn tất.

Dùng cho planning và downstream design có điều kiện.

Approved Provisional Baseline

Baseline có thể dùng cho implementation/QA hiện tại nhưng giá trị có thể được điều chỉnh bằng evidence.

Phải ghi rõ dependency và adjustment rule.

Approved Pending Device POC

Direction/baseline đã được duyệt nhưng production validity phụ thuộc target device/model/firmware evidence.

Không được diễn giải là hardware-certified.

Approved Pending Security Review

Behavior/boundary đã được duyệt nhưng cryptographic/policy values chưa được Security Review chốt.

Không được tuyên bố production-security approved.

Approved for Build `<profile>`

Tài liệu hoặc subset đã được duyệt làm release baseline cho build cụ thể.

Chỉ có hiệu lực cho build được nêu.

Production Approved

Nội dung, dependency evidence và approval gate cần thiết cho production đã hoàn tất.

Có thể dùng làm production/release baseline.

Superseded / Archived

Không còn là nguồn hiện hành.

Chỉ dùng cho audit/history.

`Approved` không kèm qualifier chỉ được dùng khi decision complete và không còn POC/Security/Deployment dependency làm thay đổi nghĩa của baseline.

## 3. Registry Rules

Rule ID

Rule

REG-001

Không copy version/status table vào DCAM Project Home, DCAM Architecture Home hoặc DCAM Requirements Home.

REG-002

Mỗi thay đổi status phải có version message nêu rõ approval scope hoặc dependency còn lại.

REG-003

`Approved` không hợp lệ nếu bảng Approval của cùng trang còn `Pending`.

REG-004

Baseline phụ thuộc hardware phải dùng `Approved Pending Device POC` hoặc `Approved Provisional Baseline`.

REG-005

Baseline phụ thuộc algorithm/key/policy security phải dùng `Approved Pending Security Review`.

REG-006

Build release acceptance phải dùng `Approved for Build <profile>` hoặc tham chiếu Release & Build Applicability Matrix.

REG-007

Navigation pages chỉ link tới registry và authoritative pages; không pin mutable versions.

REG-008

Khi registry và page metadata khác nhau, page metadata là nguồn tức thời; registry phải được sửa trong cùng ngày và discrepancy phải được ghi nhận.

## 4. Current Document Register

**Data cut-off:** 2026-08-25

**Current coverage:** `80/80` named, retrievable current Confluence pages có page metadata `Project` bắt đầu bằng `DCAM`. `80/80` trang có governed metadata đủ điều kiện theo Registry;

**Hierarchy validation:** Live descendants có 80 entries gồm 80 current page nodes và 0 folder nodes. Mười section containers đã được metadata-managed như Documentation Section / Physical Hierarchy Index. Không có untitled hoặc `Dangling` entry; page ID `49774716` không xuất hiện.

**Working Instruction record:** [Build and Launch Process for DCAM Application with ADB and Gradle](/wiki/spaces/DVID/pages/54394925/Build+and+Launch+Process+for+DCAM+Application+with+ADB+and+Gradle) now has Draft Working Instruction / Engineering Runbook metadata and a Registry row. Its commands and expected outputs do not confirm execution evidence, Device POC result, build pass or release approval.

| [DCAM Documentation Governance](/wiki/spaces/DVID/pages/47120620/DCAM+Documentation+Governance) | 1.20 | Approved | Ownership guide now routes Factory Portal BFF/Thymeleaf implementation to its implementation design; governance rules unchanged. | Hoàng Ngọc Quyền | 2026-08-25 |

‌

### 4.1 Approved Implementation Repository Mapping

Item

Record

Repository Identity

[DucVietTech/dcam](https://github.com/DucVietTech/dcam)

Default Branch

`main`

Approval

PM Approved

Scope

Authoritative DCAM implementation repository identity/linkage.

Evidence Boundary

Mapping không xác nhận commit, PR, CI, build, test, Device POC hoặc release acceptance. Jira và Traceability Matrix phải ghi execution evidence riêng.

Authoritative Documentation Record

[Decision Brief – DCAM MVP Internal Build 0.1 – Working Recording Slice](/wiki/spaces/DVID/pages/51642452/Decision+Brief+DCAM+MVP+Internal+Build+0.1+Working+Recording+Slice) §4.1; [DCAM Requirement–Design–Test Traceability Matrix](/wiki/spaces/DVID/pages/51085669/DCAM+Requirement+Design+Test+Traceability+Matrix).

Document

Version

Status

Approval Scope / Dependency

Owner

Last Reviewed

[DCAM Project Home](/wiki/spaces/DVID/pages/41648280/DCAM+Project+Home)

3.44

Approved

Project summary/hierarchy now names BFF + Thymeleaf Factory Portal + PostgreSQL ddmp and remains navigation-only.

Hoàng Ngọc Quyền

2026-08-25

[DCAM Project Charter](/wiki/spaces/DVID/pages/41156610/DCAM+Project+Charter)

1.6

Approved

Project objectives, governance, roles và project-direction baseline; Phase 2 Web Portal scope is limited by DEC-P2-WEB-01; active build applicability thuộc DCAM Release & Build Applicability Matrix; không phải Production approval.

Hoàng Ngọc Quyền

2026-07-21

[DCAM Product Vision](/wiki/spaces/DVID/pages/41648238/DCAM+Product+Vision)

2.5

Approved

Long-term product direction và target outcomes; không phải build, release hoặc Production approval.

Hoàng Ngọc Quyền

2026-07-21

[DCAM Roadmap](/wiki/spaces/DVID/pages/41615474/DCAM+Roadmap)

1.7

Approved

Phase và milestone direction; DEC-P2-WEB-01 defines the narrow Phase 2 Web Portal provisioning scope; active build applicability và release gate thuộc DCAM Release & Build Applicability Matrix.

Hoàng Ngọc Quyền

2026-07-21

[DCAM MVP Scope](/wiki/spaces/DVID/pages/42532866/DCAM+MVP+Scope)

2.7

Approved

DEC-01–DEC-07 và Working Recording Slice cho DCAM MVP Internal Build 0.1

Hoàng Ngọc Quyền

2026-07-21

[DCAM 9-Month Development Plan](/wiki/spaces/DVID/pages/46759955/DCAM+9-Month+Development+Plan)

1.6

Approved

Execution sequencing và delivery planning; active scope thuộc DCAM Release & Build Applicability Matrix, delivery status thuộc Jira.

Hoàng Ngọc Quyền

2026-07-21

[DCAM Documentation Governance](/wiki/spaces/DVID/pages/47120620/DCAM+Documentation+Governance)

1.19

Approved

Documentation ownership, status taxonomy, approval integrity, traceability and GMS-free authoritative-baseline/reference rule.

Hoàng Ngọc Quyền

2026-08-25

[DCAM Engineering Evidence & NAS Artifact SOP](/wiki/spaces/DVID/pages/53608471/DCAM+Engineering+Evidence+NAS+Artifact+SOP)

1.1

Approved

Quy trình kiểm soát raw technical evidence cho DCAM Build 0.1; không xác nhận NAS readiness, Device POC pass hoặc Build 0.1 readiness.

Hoàng Ngọc Quyền

2026-07-21

[DCAM Release & Build Applicability Matrix](/wiki/spaces/DVID/pages/51020012/DCAM+Release+Build+Applicability+Matrix)

1.8

Approved

Build applicability unchanged; Phase 2 provisioning blockers now use BFF/PostgreSQL security/deployment terminology.

Hoàng Ngọc Quyền

2026-08-25

[DCAM Document Status Registry](/wiki/spaces/DVID/pages/51085647/DCAM+Document+Status+Registry)

Approved 1.44

Approved

Cross-document status summary including controlled Factory Portal BFF/mTLS pending-enrollment synchronization; does not approve Build 0.1 or production QR/PKI/RBAC details.

Hoàng Ngọc Quyền

2026-08-26

[DCAM Requirements Home](/wiki/spaces/DVID/pages/47710513/DCAM+Requirements+Home)

1.5

Approved

Requirements navigation, physical structure và reading order only; requirement behavior thuộc từng Requirements page.

Hoàng Ngọc Quyền

2026-07-21

[DCAM Requirement–Design–Test Traceability Matrix](/wiki/spaces/DVID/pages/51085669/DCAM+Requirement+Design+Test+Traceability+Matrix)

2.1

Approved Provisional Baseline

DEC-01–DEC-07 Build 0.1 traceability plus DEC-P2-WEB-01 Phase 2 planning boundary; no Jira/execution evidence or release acceptance uplift.

Hoàng Ngọc Quyền

2026-08-21

[01 - Recording & Capture Requirements](/wiki/spaces/DVID/pages/47743356/01+-+Recording+Capture+Requirements)

1.5

Approved

Recording requirements và Build 0.1 checksum/finalization profile

Hoàng Ngọc Quyền

2026-07-21

[02 - Media Storage Requirements](/wiki/spaces/DVID/pages/47808901/02+-+Media+Storage+Requirements)

1.4

Approved

Stable requirements và Build 0.1 Internal-only storage policy

Hoàng Ngọc Quyền

2026-07-21

[06 - BDMA Integration Requirements](/wiki/spaces/DVID/pages/47743376/06+-+BDMA+Integration+Requirements)

1.6

Approved

Stable requirements và Build 0.1 final-media/checksum import boundary

Hoàng Ngọc Quyền

2026-07-21

[07 - Logging & Diagnostics Requirements](/wiki/spaces/DVID/pages/47776094/07+-+Logging+Diagnostics+Requirements)

1.9

Approved

GMS-free/provider-independent diagnostics require bounded `DiagnosticsOutbox`, BFF acknowledgement/idempotency and optional Crashlytics only.

Hoàng Ngọc Quyền

2026-08-25

[DCAM Storage Design](/wiki/spaces/DVID/pages/48496699/DCAM+Storage+Design)

0.10

Approved Pending Device POC

Build 0.1 Internal-only mechanics; physical path/scoped storage/ADB evidence Pending Device POC

Hoàng Ngọc Quyền

2026-07-13

[DCAM SQLite Database Design](/wiki/spaces/DVID/pages/48529463/DCAM+SQLite+Database+Design)

2.0

Approved Provisional Baseline

Cloud identity/config ownership terminology is BFF + PostgreSQL ddmp; app_installation_id is provider-neutral metadata, not a device identity key.

Hoàng Ngọc Quyền

2026-08-25

[DCAM BDMA Integration Technical Design](/wiki/spaces/DVID/pages/48595030/DCAM+BDMA+Integration+Technical+Design)

0.10

Draft

Build 0.1 integration profile prepared; physical path/schema and Technical Review remain open; repository identity recorded without status uplift.

Hoàng Ngọc Quyền

2026-07-18

[DCAM Concurrency & Threading Model Design](/wiki/spaces/DVID/pages/50725012/DCAM+Concurrency+Threading+Model+Design)

0.5

Draft

Build 0.1 required subset; exact executor sizing/queue policy Pending Technical Review và Device POC

Hoàng Ngọc Quyền

2026-07-16

[DCAM-BDMA Data Contract](/wiki/spaces/DVID/pages/47743153/DCAM-BDMA+Data+Contract)

1.14

Approved

Cloud identity/provisioning terminology uses BFF/PostgreSQL ddmp; app_installation_id is provider-neutral metadata, not a device identity key.

Hoàng Ngọc Quyền

2026-08-25

[DCAM Non-functional Requirements](/wiki/spaces/DVID/pages/48595009/DCAM+Non-functional+Requirements)

1.14

Approved

System quality includes mandatory GMS-free runtime guard; numeric budgets remain POC-dependent.

Hoàng Ngọc Quyền

2026-08-25

[DCAM Architecture Home](/wiki/spaces/DVID/pages/47185929/DCAM+Architecture+Home)

1.42

Approved

Navigation/reading order adds the Device API Credential & mTLS ADR while remaining non-authoritative for mutable baselines.

Hoàng Ngọc Quyền

2026-08-26

[DCAM Architecture Delivery Profile](/wiki/spaces/DVID/pages/50626744/DCAM+Architecture+Delivery+Profile)

1.5

Approved for Build DCAM MVP Internal Build 0.1

Architecture guardrails for Working Recording Slice; GMS-free dependency/source guard applies to every profile; not Production/fleet approval.

Hoàng Ngọc Quyền

2026-08-25

[DCAM Recording & Capture Design](/wiki/spaces/DVID/pages/48529484/DCAM+Recording+Capture+Design)

1.5

Approved Provisional Baseline

Build 0.1 runtime direction; Camera capability, Camera1/Camera2 decision, physical storage/path behavior and Technical Review/Device POC evidence pending.

Hoàng Ngọc Quyền

2026-07-20

[DCAM Security & Encryption Design](/wiki/spaces/DVID/pages/48496720/DCAM+Security+Encryption+Design)

1.11

Approved Pending Security Review

Adds Keystore-backed per-device mTLS direction and prohibits credential/identity conflation; PKI values and evidence remain under Security Review.

Hoàng Ngọc Quyền

2026-08-26

[DCAM Performance Budget & Resource Constraints](/wiki/spaces/DVID/pages/50659486/DCAM+Performance+Budget+Resource+Constraints)

0.8

Approved Pending Device POC

Performance guardrails add GMS-free telemetry isolation; numeric/device evidence remains Pending Device POC.

Hoàng Ngọc Quyền

2026-08-25

[DCAM QA Test Strategy & Test Matrix](/wiki/spaces/DVID/pages/49545345/DCAM+QA+Test+Strategy+Test+Matrix)

2.7

Approved

Build/release test definitions including mandatory GMS-free dependency/source and provider-independent diagnostics checks for production profile; no execution evidence uplift.

Hoàng Ngọc Quyền

2026-08-25

[DCAM Device POC & Hardware Validation Report](/wiki/spaces/DVID/pages/49545399/DCAM+Device+POC+Hardware+Validation+Report)

1.1

Approved Pending Device POC

Reference configuration/test plan plus GMS-free dependency/manifest/source and target-firmware POC matrix; physical evidence and conclusion remain pending.

Hoàng Ngọc Quyền

2026-08-25

[Decision Brief – DCAM MVP Internal Build 0.1 – Working Recording Slice](/wiki/spaces/DVID/pages/51642452/Decision+Brief+DCAM+MVP+Internal+Build+0.1+Working+Recording+Slice)

1.2

Approved for Build DCAM MVP Internal Build 0.1

DEC-01–DEC-07 cho Working Recording Slice; không phải Production approval hoặc Device POC pass

Hoàng Ngọc Quyền

2026-07-21

[Decision Brief – DCAM Phase 2 Web Portal Scope Precedence](/wiki/spaces/DVID/pages/54296681/Decision+Brief+DCAM+Phase+2+Web+Portal+Scope+Precedence)

1.1

Approved

Phase 2 scope is unchanged; implementation/deployment blockers now name BFF identity/session/RBAC, PostgreSQL and QR policy rather than a legacy provider.

Hoàng Ngọc Quyền

2026-08-25

[04 - Device Configuration Requirements](/wiki/spaces/DVID/pages/47710554/04+-+Device+Configuration+Requirements)

1.8

Approved

Device identity/config requirements now use the BFF/PostgreSQL ddmp provider boundary; scope remains unchanged.

Hoàng Ngọc Quyền

2026-08-25

[05 - User & Device Operation Requirements](/wiki/spaces/DVID/pages/47710574/05+-+User+Device+Operation+Requirements)

1.5

Approved

Target-state operator requirements với approved Build 0.1 exception

Hoàng Ngọc Quyền

2026-07-21

[DCAM Logging & Diagnostics Design](/wiki/spaces/DVID/pages/51019937/DCAM+Logging+Diagnostics+Design)

1.0

Approved

Clarifies DCAM local-first diagnostics versus BFF monitoring: expected device/security events are audit/metrics, unexpected BFF failures use backend observability; no direct Android Sentry/OTel path.

Hoàng Ngọc Quyền

2026-08-26

[DCAM State Machine Design](/wiki/spaces/DVID/pages/48496753/DCAM+State+Machine+Design)

2.4

Approved Provisional Baseline

Adds credential lifecycle overlay: enrollment, active, rotation, revocation and re-enrolment without disrupting local recording/evidence.

Hoàng Ngọc Quyền

2026-08-26

[DCAM Android Operation Design](/wiki/spaces/DVID/pages/48562239/DCAM+Android+Operation+Design)

2.6

Approved Provisional Baseline

Defines operational credential states and safe offline/recovery behaviour; exact PKI implementation remains POC-gated.

Hoàng Ngọc Quyền

2026-08-26

[03 - Android Platform & Compatibility Strategy](/wiki/spaces/DVID/pages/47120437/03+-+Android+Platform+Compatibility+Strategy)

1.10

Approved

GMS-free compatibility baseline uses provider-neutral remote-configuration wording.

Hoàng Ngọc Quyền

2026-08-25

[04 - Application & Module Architecture](/wiki/spaces/DVID/pages/47218698/04+-+Application+Module+Architecture)

1.15

Approved

Adds logical DeviceCredentialManager, enrollment coordinator and mTLS Device API client boundaries.

Hoàng Ngọc Quyền

2026-08-26

[08 - DCAM-BDMA Integration Boundary](/wiki/spaces/DVID/pages/47153235/08+-+DCAM-BDMA+Integration+Boundary)

1.12

Approved

DCAM–BDMA boundary with approved Build 0.1 import eligibility overlay; repository identity recorded without changing Device POC/Security gates.

Hoàng Ngọc Quyền

2026-07-18

[ADR - DCAM Device Identity Baseline: serial_number + dcam_cloud_device_id](/wiki/spaces/DVID/pages/50692110/ADR+-+DCAM+Device+Identity+Baseline+serial_number+dcam_cloud_device_id)

1.9

Approved

Links the one-cloud-identity baseline to the separate mTLS credential ADR; identity fields remain non-credentials.

Hoàng Ngọc Quyền

2026-08-26

[ADR – DCAM Device API Credential & mTLS Baseline](/wiki/spaces/DVID/pages/70287362/ADR+DCAM+Device+API+Credential+mTLS+Baseline)

Approved Direction 1.0

Approved Direction

Authoritative per-device Device API credential baseline: Android Keystore key, mTLS client certificate, controlled enrollment/rotation/revocation and separate human/device scopes; exact PKI values/evidence remain gated.

Hoàng Ngọc Quyền

2026-08-26

[ADR - DCAM GMS-free Android Runtime Baseline](/wiki/spaces/DVID/pages/69730306/ADR+-+DCAM+GMS-free+Android+Runtime+Baseline)

Approved Direction 1.0

Approved Direction

Mandatory production Android runtime baseline: no Google Play services/GMS, Google Play Store, Google account, FCM, Analytics or Play Integrity dependency; DCAM DPC, BFF and R2 authority boundaries remain unchanged. Firebase Crashlytics is optional and does not replace local-first diagnostics.

Hoàng Ngọc Quyền

2026-08-25

[DCAM Android Device Owner & Kiosk Policy Design](/wiki/spaces/DVID/pages/49840280/DCAM+Android+Device+Owner+Kiosk+Policy+Design)

1.1

Approved Pending Device POC

Device Owner/Kiosk direction includes GMS-free maintenance/allowlist boundary; exact OEM/firmware/DPC/allowlist/Headwind package evidence remains Pending Device POC.

Hoàng Ngọc Quyền

2026-08-25

[05 - Data, Storage & BDMA Architecture](/wiki/spaces/DVID/pages/47185950/05+-+Data+Storage+BDMA+Architecture)

1.9

Approved

High-level data/storage/BDMA architecture; MD5 applicability theo Matrix/Data Contract; cryptographic algorithm, key management và decryption policy thuộc DCAM Security & Encryption Design và approved Security Profile.

Hoàng Ngọc Quyền

2026-07-13

[01 - Architecture Overview](/wiki/spaces/DVID/pages/47120395/01+-+Architecture+Overview)

2.0

Approved

High-level system context now names Spring Boot BFF + Thymeleaf Factory Portal and PostgreSQL ddmp; Hybrid authority and mandatory GMS-free Android runtime remain unchanged.

Hoàng Ngọc Quyền

2026-08-25

[02 - Architecture Principles](/wiki/spaces/DVID/pages/47120416/02+-+Architecture+Principles)

1.12

Approved

Cross-cutting Factory Portal provider baseline is Spring Boot BFF + Thymeleaf + PostgreSQL ddmp.

Hoàng Ngọc Quyền

2026-08-25

[03 - Media Management Requirements](/wiki/spaces/DVID/pages/47710534/03+-+Media+Management+Requirements)

1.2

Approved

Media management requirement behavior; build applicability thuộc Matrix, filename/data exchange rules thuộc DCAM-BDMA Data Contract.

Hoàng Ngọc Quyền

2026-07-21

[06 - Cloud Services, Update & Configuration Architecture](/wiki/spaces/DVID/pages/47120459/06+-+Cloud+Services+Update+Configuration+Architecture)

3.7

Approved

Adds BFF-controlled Device PKI and per-device mTLS as Device API trust boundary; exact PKI/network values remain gated.

Hoàng Ngọc Quyền

2026-08-26

[07 - Logging, Diagnostics, Performance & Security](/wiki/spaces/DVID/pages/47185971/07+-+Logging+Diagnostics+Performance+Security)

1.9

Approved

Quality architecture includes mandatory GMS-free observability boundary; detailed rules remain in Requirements/Design/QA sources.

Hoàng Ngọc Quyền

2026-08-25

[08 - Security & Encryption Requirements](/wiki/spaces/DVID/pages/47710594/08+-+Security+Encryption+Requirements)

1.5

Approved

Security requirement direction; exact algorithm, key management và policy values thuộc DCAM Security & Encryption Design và Security Review.

Hoàng Ngọc Quyền

2026-07-21

[09 - System Settings Requirements](/wiki/spaces/DVID/pages/47710614/09+-+System+Settings+Requirements)

1.20

Approved

System settings identity boundary uses BFF Factory Portal and PostgreSQL ddmp; feature scope remains unchanged.

Hoàng Ngọc Quyền

2026-08-25

[10 - Android Device Operation Requirements](/wiki/spaces/DVID/pages/48496661/10+-+Android+Device+Operation+Requirements)

1.11

Approved

Android operation requirements replace manual Play Store fallback with mandatory GMS-free runtime/update-source rejection guard.

Hoàng Ngọc Quyền

2026-08-25

[ADR - DCAM Android Dedicated Device / Device Owner / Lock Task Decision](/wiki/spaces/DVID/pages/49774787/ADR+-+DCAM+Android+Dedicated+Device+Device+Owner+Lock+Task+Decision)

1.1

Approved Direction

Dedicated-device, DCAM-only Device Owner/DPC và Lock Task architecture direction, bao gồm approved DDMP hybrid boundary; exact DPC component, OEM/firmware coexistence feasibility và policy evidence còn phụ thuộc Device POC/Security Review.

Hoàng Ngọc Quyền

2026-08-24

[DCAM Android Development Standard](/wiki/spaces/DVID/pages/47120580/DCAM+Android+Development+Standard)

1.13

Approved

Engineering standard aligns Factory Portal/device identity boundary to BFF while retaining the GMS-free dependency/manifest/source CI guard.

Hoàng Ngọc Quyền

2026-08-25

[DCAM Android Training & Architecture Onboarding](/wiki/spaces/DVID/pages/46825510/DCAM+Android+Training+Architecture+Onboarding)

1.7

Approved

Developer onboarding includes mandatory GMS-free dependency/update-source awareness; does not replace Requirements, Architecture, Technical Design or Build Applicability baseline.

Hoàng Ngọc Quyền

2026-08-25

[DCAM Device Capability & Feature Eligibility Design](/wiki/spaces/DVID/pages/48758788/DCAM+Device+Capability+Feature+Eligibility+Design)

0.10

Draft

Draft capability/eligibility design replaces Play Store capability with GMS-free compliance evidence; evaluator/persistence/POC evidence remain Draft.

Hoàng Ngọc Quyền

2026-08-25

[DCAM Device Provisioning Web Portal App Design](/wiki/spaces/DVID/pages/50692194/DCAM+Device+Provisioning+Web+Portal+App+Design)

1.6

Approved Direction

Factory Portal UI is BFF-hosted Thymeleaf in ddmp-bff Docker; QR pairing is submitted as payload/scan reference and BFF creates pending enrollment only; browser has no direct PostgreSQL/certificate access.

Hoàng Ngọc Quyền

2026-08-26

[DCAM Device Provisioning Web Portal Design](/wiki/spaces/DVID/pages/49315858/DCAM+Device+Provisioning+Web+Portal+Design)

1.5

Approved Direction

Factory provisioning BFF flow creates/restores identity and short-lived pending enrollment from validated QR pairing; DCAM later proves Keystore key possession before certificate issue.

Hoàng Ngọc Quyền

2026-08-26

[DCAM Device Provisioning Web Portal Implementation Design](/wiki/spaces/DVID/pages/51019802/DCAM+Device+Provisioning+Web+Portal+Implementation+Design)

1.5

Approved Direction

Spring MVC/Thymeleaf BFF module now maps pending enrollment, QR pairing validation and Device PKI proof continuation; ddmp is host-native Server B and implementation/security evidence remains pending.

Hoàng Ngọc Quyền

2026-08-26

[DCAM DSetup Factory Tool Design](/wiki/spaces/DVID/pages/50626624/DCAM+DSetup+Factory+Tool+Design)

1.3

Approved

Approved DSetup factory-tool behavior; không phải Device POC pass, device qualification hoặc Production shipment approval.

Hoàng Ngọc Quyền

2026-07-14

[DCAM Factory Provisioning & Device Production SOP](/wiki/spaces/DVID/pages/49545629/DCAM+Factory+Provisioning+Device+Production+SOP)

1.4

Approved

Factory procedure aligns provisioning connectivity and cloud identity terminology to BFF/PostgreSQL ddmp; optional Crashlytics telemetry rule remains unchanged.

Hoàng Ngọc Quyền

2026-08-25

[DCAM In-App Operation, Device Settings & Media Console Design](/wiki/spaces/DVID/pages/49840330/DCAM+In-App+Operation+Device+Settings+Media+Console+Design)

1.4

Draft

Draft console explicitly prohibits Play Store/Managed Google Play/Android Management API/Google-account update targets; only BFF-authorized R2/CDN or approved local/factory recovery is permitted.

Hoàng Ngọc Quyền

2026-08-25

[DCAM Realtime AI Detection Design](/wiki/spaces/DVID/pages/48595090/DCAM+Realtime+AI+Detection+Design)

0.8

Draft

Draft realtime AI design only; không phải approved implementation, Build 0.1 hoặc Production baseline.

Hoàng Ngọc Quyền

2026-07-14

[DCAM Self Update Design](/wiki/spaces/DVID/pages/48529439/DCAM+Self+Update+Design)

1.3

Draft

Draft self-update design removes Play Store/account fallback; BFF/R2 primary and controlled local/factory package fallback only. Exact schema/install/rollout evidence remain Draft.

Hoàng Ngọc Quyền

2026-08-25

[DCAM Sensor & Location Monitoring Design](/wiki/spaces/DVID/pages/48496794/DCAM+Sensor+Location+Monitoring+Design)

0.7

Draft

Draft sensor/location design only; không phải approved implementation, Build 0.1 hoặc Production baseline.

Hoàng Ngọc Quyền

2026-07-14

[DCAM Factory Provisioning Portal & BFF API Contract](/wiki/spaces/DVID/pages/49873154/DCAM+Factory+Provisioning+Portal+BFF+API+Contract)

1.1

Approved Direction

Administrative Factory route accepts QR payload/scan reference, creates pending enrollment and separates later Device API proof/certificate issue; BFF/ddmp remains sole data authority.

Hoàng Ngọc Quyền

2026-08-26

### 4.2 Current Hierarchy Index and Working Instruction Records

Document

Version

Status

Approval Scope / Dependency

Owner

Last Reviewed

[01  Product Management](/wiki/spaces/DVID/pages/55148561/01+Product+Management)

1.0

Approved

Physical documentation hierarchy and navigation only; does not own product baseline, mutable Version/Status, delivery status or release conclusion.

Hoàng Ngọc Quyền

2026-07-21

[02  Sprint Operations](/wiki/spaces/DVID/pages/55312385/02+Sprint+Operations)

1.0

Approved

Physical documentation hierarchy and navigation only; does not own product baseline, mutable Version/Status, delivery status or release conclusion.

Hoàng Ngọc Quyền

2026-07-21

[03 Requirements](/wiki/spaces/DVID/pages/55312417/03+Requirements)

1.0

Approved

Physical documentation hierarchy and navigation only; does not own product baseline, mutable Version/Status, delivery status or release conclusion.

Hoàng Ngọc Quyền

2026-07-21

[04 Technical Documentation](/wiki/spaces/DVID/pages/55312401/04+Technical+Documentation)

1.0

Approved

Physical documentation hierarchy and navigation only; does not own product baseline, mutable Version/Status, delivery status or release conclusion.

Hoàng Ngọc Quyền

2026-07-21

[4.1 - Software Architecture](/wiki/spaces/DVID/pages/55115808/4.1+-+Software+Architecture)

1.0

Approved

Physical documentation hierarchy and navigation only; does not own product baseline, mutable Version/Status, delivery status or release conclusion.

Hoàng Ngọc Quyền

2026-07-21

[4.2 - Technical Design](/wiki/spaces/DVID/pages/55181373/4.2+-+Technical+Design)

1.0

Approved

Physical documentation hierarchy and navigation only; does not own product baseline, mutable Version/Status, delivery status or release conclusion.

Hoàng Ngọc Quyền

2026-07-21

[4.3 - Android Development](/wiki/spaces/DVID/pages/55246889/4.3+-+Android+Development)

1.0

Approved

Physical documentation hierarchy and navigation only; does not own product baseline, mutable Version/Status, delivery status or release conclusion.

Hoàng Ngọc Quyền

2026-07-21

[DCAM-2 — Device POC Results & Evidence Summary](/wiki/spaces/DVID/pages/57344040/DCAM-2+Device+POC+Results+Evidence+Summary)

1.5

Draft

Working execution/evidence summary under DCAM Device POC & Hardware Validation Report; its recorded outcomes do not change authoritative POC conclusion, Traceability, Jira workflow or release readiness.

duchm & Việt Anh

2026-08-21

[DCAM-6: Working Recording Slice Integration & Evidence Summary (local working record; Confluence Page ID/URL pending)](/wiki/spaces/DVID/pages/66355221/DCAM-6+Working+Recording+Slice+Integration+Evidence+Summary)

0.1

Draft

Build 0.1 WRS evidence summary: `Pass with recorded blocked cases` (`EV-DCAM-38-20260815-001`; DCAM-40: 20 Pass, 3 Blocked, 0 Fail). Review/Jira/QA handoff pending.

Việt Anh

2026-08-21

[DCAM-9: Image Capture POC Results & Evidence Summary](/wiki/spaces/DVID/pages/59473994/DCAM-9+Image+Capture+POC+Results+Evidence+Summary)

0.1

Draft

Working evidence summary under DCAM Device POC & Hardware Validation Report. Các trường Version/Target Audience/Approver/Parent Page/Related Jira/Dependencies-Blockers đã được bổ sung; không có Status/evidence/POC/release uplift.

phiha

2026-08-21

[DCAM-8: BDMA Sample Import & Evidence Summary](/wiki/spaces/DVID/pages/60030996/DCAM-8+BDMA+Sample+Import+Evidence+Summary)

0.1

Draft

Working evidence summary under DCAM Device POC & Hardware Validation Report. Các trường Version/Target Audience/Approval Scope/Approver/Parent Page/Related Jira/Dependencies-Blockers đã được bổ sung; không có Status/evidence/POC/release uplift.

phiha

2026-08-21

[4.4 - Architecture Decision Records (ADR)](/wiki/spaces/DVID/pages/55246907/4.4+-+Architecture+Decision+Records+ADR)

1.0

Approved

Physical documentation hierarchy and navigation only; does not own product baseline, mutable Version/Status, delivery status or release conclusion.

Hoàng Ngọc Quyền

2026-07-21

[05 Release Management](/wiki/spaces/DVID/pages/55345169/05+Release+Management)

1.0

Approved

Physical documentation hierarchy and navigation only; does not own product baseline, mutable Version/Status, delivery status or release conclusion.

Hoàng Ngọc Quyền

2026-07-21

[06 Incident Log](/wiki/spaces/DVID/pages/55345153/06+Incident+Log)

1.0

Approved

Physical documentation hierarchy and navigation only; does not own product baseline, mutable Version/Status, delivery status or release conclusion.

Hoàng Ngọc Quyền

2026-07-21

[Build and Launch Process for DCAM Application with ADB and Gradle](/wiki/spaces/DVID/pages/54394925/Build+and+Launch+Process+for+DCAM+Application+with+ADB+and+Gradle)

0.1

Draft

Local build/install/launch instruction and expected-output template only; not approved product baseline, execution evidence, Device POC result or release approval.

phiha

2026-07-29

## 5. Change Workflow

Update authoritative document metadata
    ↓
Complete required review/approval
    ↓
Update this registry
    ↓
Validate dependent navigation links
    ↓
Update Traceability Matrix when requirement/design/test mapping changes
Project Home, Architecture Home và Requirements Home không cần cập nhật chỉ vì version của một trang thay đổi, trừ khi navigation, ownership, status summary hoặc reading order thực sự thay đổi.

## 6. Migration Note

Qualified statuses are applied immediately to documents with explicit open Device POC or Security Review dependencies when those pages are revised.

Other pages retain their current page metadata until their next material review. Their approval scope in this registry must be read together with the Applicability Matrix and authoritative dependency documents.

## 7. Practical Conclusion

One document owns its own metadata.
One registry summarizes the document set.
Navigation pages do not copy mutable version/status values.
Qualified approval statuses prevent Draft, POC-dependent and Security-dependent content from being mistaken for production approval.
## 8. Build 0.1 Registry Guardrail

NCC-036V qualification giữ Approved Pending Device POC đến khi có test evidence.

Approved for Build DCAM MVP Internal Build 0.1 không phải Production Approved.

Draft Technical Designs không tự được nâng status bởi PM decision.

[DucVietTech/dcam](https://github.com/DucVietTech/dcam) là PM-approved repository identity; nó không tự là PR/build/test evidence hoặc release evidence.

Navigation pages không copy mutable version/status hoặc Build 0.1 decision tables.

## 9. Controlled synchronization — Factory Portal BFF and device enrollment

Factory Portal is an administrative Spring MVC/Thymeleaf module inside the ddmp-bff Docker service on Server A. It validates non-secret DCAM QR pairing data and creates/restores cloud identity plus short-lived PENDING_ENROLLMENT in host-native PostgreSQL ddmp on Server B.

The Portal/Factory Worker never receives a device private key or Device API credential. DCAM later uses the separate Device API to prove Android Keystore key possession against BFF challenge before BFF Device PKI issues/binds a client certificate. IdP integration is a BFF security dependency; Firebase/BaaS/direct browser-data authority is not part of the provisioning line.

This synchronization is Phase 2 / Build 0.2 minimum and does not change DCAM Build 0.1 applicability.