# DCAM Document Status Registry

**Page ID**: 51085647  
**Version**: 19  
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

Approved 1.15

Status

Approved

Approval Scope

Cross-document version/status/approval-scope summary; coverage 63/63 named retrievable pages và controlled hierarchy/metadata synchronization.

Owner

Hoàng Ngọc Quyền

Technical Reviewer

Tech Lead / Documentation Owner

Approver

Hoàng Ngọc Quyền

Parent Folder

02 - Sprint Operations

Target Audience

PM/BA, Tech Lead, Document Owners, QA, Reviewers, Approvers

Last Updated

2026-07-17

Related Jira

None

Related Documents

DCAM Documentation Governance, DCAM Project Home, DCAM Architecture Home, DCAM Requirements Home, DCAM Release & Build Applicability Matrix, DCAM Requirement–Design–Test Traceability Matrix, Decision Brief – DCAM MVP Internal Build 0.1 – Working Recording Slice

## 1. Purpose

Trang này là registry duy nhất để tổng hợp version, document status, approval scope và dependency condition của các tài liệu DCAM.

Document page metadata = trạng thái chính thức của chính tài liệu đó.
DCAM Document Status Registry = nơi tổng hợp duy nhất để review toàn bộ bộ tài liệu.
Navigation pages must not copy version/status tables.
Khi version hoặc status của một tài liệu thay đổi, document owner phải cập nhật metadata của trang và registry này trong cùng change set.

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

**Data cut-off:** 2026-07-17

**Current coverage:** `63/63` named, retrievable current Confluence pages có page metadata `Project` bắt đầu bằng `DCAM`.

**Hierarchy validation:** Live descendants có 73 entries gồm 10 folders và 63 named page entries. Không có untitled hoặc `Dangling` entry. Page ID `49774716` không còn xuất hiện; documented exclusion đã đóng.

**Scope authority:** [DCAM Documentation Governance](/wiki/spaces/DVID/pages/47120620/DCAM+Documentation+Governance) §5.3.1–§5.3.2. Registry chỉ tổng hợp page metadata, không nâng `Status` hoặc mở rộng `Approval Scope`.

Document

Version

Status

Approval Scope / Dependency

Owner

Last Reviewed

[DCAM Project Home](/wiki/spaces/DVID/pages/41648280/DCAM+Project+Home)

3.37

Approved

Project navigation, physical hierarchy và current-summary only; §4 synchronized to the 2026-07-17 live hierarchy (73 entries / 10 folders / 63 named pages); không sở hữu mutable version/status hoặc requirement/design baseline.

Hoàng Ngọc Quyền

2026-07-17

[DCAM Project Charter](/wiki/spaces/DVID/pages/41156610/DCAM+Project+Charter)

1.5

Approved

Project objectives, governance, roles và project-direction baseline; active build applicability thuộc DCAM Release & Build Applicability Matrix; không phải Production approval.

Hoàng Ngọc Quyền

2026-07-14

[DCAM Product Vision](/wiki/spaces/DVID/pages/41648238/DCAM+Product+Vision)

2.5

Approved

Long-term product direction và target outcomes; không phải build, release hoặc Production approval.

Hoàng Ngọc Quyền

2026-07-14

[DCAM Roadmap](/wiki/spaces/DVID/pages/41615474/DCAM+Roadmap)

1.6

Approved

Phase và milestone direction; active build applicability và release gate thuộc DCAM Release & Build Applicability Matrix.

Hoàng Ngọc Quyền

2026-07-14

[DCAM MVP Scope](/wiki/spaces/DVID/pages/42532866/DCAM+MVP+Scope)

2.7

Approved

DEC-01–DEC-07 và Working Recording Slice cho DCAM MVP Internal Build 0.1

Hoàng Ngọc Quyền

2026-07-13

[DCAM 9-Month Development Plan](/wiki/spaces/DVID/pages/46759955/DCAM+9-Month+Development+Plan)

1.6

Approved

Execution sequencing và delivery planning; active scope thuộc DCAM Release & Build Applicability Matrix, delivery status thuộc Jira.

Hoàng Ngọc Quyền

2026-07-14

[DCAM Documentation Governance](/wiki/spaces/DVID/pages/47120620/DCAM+Documentation+Governance)

1.18

Approved

Documentation ownership, status taxonomy, approval integrity, traceability, artifact evidence repository governance và Registry inclusion/exclusion/coverage rules.

Hoàng Ngọc Quyền

2026-07-16

[DCAM Engineering Evidence & NAS Artifact SOP](/wiki/spaces/DVID/pages/53608471/DCAM+Engineering+Evidence+NAS+Artifact+SOP)

0.2

Draft

Controlled procedure for raw technical evidence repository DCAM-EVID-NAS-01; không xác nhận NAS readiness hoặc Build 0.1 readiness khi UNC path, Access Group, Backup/Retention và access confirmation còn TBD.

TBD – PM/Tech Lead xác nhận

2026-07-16

[DCAM Release & Build Applicability Matrix](/wiki/spaces/DVID/pages/51020012/DCAM+Release+Build+Applicability+Matrix)

1.5

Approved

Build applicability cho DCAM MVP Internal Build 0.1, DEC-01–DEC-07, Important Media Conditional — Not Activated clarification và legacy MD5 exclusion guardrail cho later profiles.

Hoàng Ngọc Quyền

2026-07-14

[DCAM Document Status Registry](/wiki/spaces/DVID/pages/51085647/DCAM+Document+Status+Registry)

1.15

Approved

Cross-document version/status/approval-scope summary; coverage 63/63 named retrievable pages và controlled hierarchy/metadata synchronization.

Hoàng Ngọc Quyền

2026-07-17

[DCAM Requirements Home](/wiki/spaces/DVID/pages/47710513/DCAM+Requirements+Home)

1.4

Approved

Requirements navigation, physical structure và reading order only; requirement behavior thuộc từng Requirements page.

Hoàng Ngọc Quyền

2026-07-14

[DCAM Requirement–Design–Test Traceability Matrix](/wiki/spaces/DVID/pages/51085669/DCAM+Requirement+Design+Test+Traceability+Matrix)

1.6

Approved Provisional Baseline

DEC-01–DEC-07 Build 0.1 backlog-readiness traceability, gồm Important Media Conditional — Not Activated; chưa phải release acceptance khi Jira/evidence còn thiếu.

Hoàng Ngọc Quyền

2026-07-16

[01 - Recording & Capture Requirements](/wiki/spaces/DVID/pages/47743356/01+-+Recording+Capture+Requirements)

1.5

Approved

Recording requirements và Build 0.1 checksum/finalization profile

Hoàng Ngọc Quyền

2026-07-13

[02 - Media Storage Requirements](/wiki/spaces/DVID/pages/47808901/02+-+Media+Storage+Requirements)

1.4

Approved

Stable requirements và Build 0.1 Internal-only storage policy

Hoàng Ngọc Quyền

2026-07-16

[06 - BDMA Integration Requirements](/wiki/spaces/DVID/pages/47743376/06+-+BDMA+Integration+Requirements)

1.5

Approved

Stable requirements và Build 0.1 final-media/checksum import boundary

Hoàng Ngọc Quyền

2026-07-16

[07 - Logging & Diagnostics Requirements](/wiki/spaces/DVID/pages/47776094/07+-+Logging+Diagnostics+Requirements)

1.7

Approved

Stable Build 0.1 logging requirements cho storage, checksum, operator và Device Status

Hoàng Ngọc Quyền

2026-07-16

[DCAM Storage Design](/wiki/spaces/DVID/pages/48496699/DCAM+Storage+Design)

0.10

Approved Pending Device POC

Build 0.1 Internal-only mechanics; physical path/scoped storage/ADB evidence Pending Device POC

Hoàng Ngọc Quyền

2026-07-13

[DCAM SQLite Database Design](/wiki/spaces/DVID/pages/48529463/DCAM+SQLite+Database+Design)

1.8

Approved Provisional Baseline

Build 0.1 minimal data/state subset; exact schema/migration/transaction boundary Pending Technical Review

Hoàng Ngọc Quyền

2026-07-16

[DCAM BDMA Integration Technical Design](/wiki/spaces/DVID/pages/48595030/DCAM+BDMA+Integration+Technical+Design)

0.9

Draft

Build 0.1 integration profile prepared; physical path/schema and Technical Review remain open

Hoàng Ngọc Quyền

2026-07-13

[DCAM Concurrency & Threading Model Design](/wiki/spaces/DVID/pages/50725012/DCAM+Concurrency+Threading+Model+Design)

0.5

Draft

Build 0.1 required subset; exact executor sizing/queue policy Pending Technical Review và Device POC

Hoàng Ngọc Quyền

2026-07-16

[DCAM-BDMA Data Contract](/wiki/spaces/DVID/pages/47743153/DCAM-BDMA+Data+Contract)

1.12

Approved

Global contract với authoritative media filename token mapping, Build 0.1 exchange profile và legacy MD5 applicability guardrail.

Hoàng Ngọc Quyền

2026-07-16

[DCAM Non-functional Requirements](/wiki/spaces/DVID/pages/48595009/DCAM+Non-functional+Requirements)

1.13

Approved

System quality direction; numeric performance budgets và hardware-dependent behavior thuộc Performance Budget và Device POC.

Hoàng Ngọc Quyền

2026-07-14

[DCAM Architecture Home](/wiki/spaces/DVID/pages/47185929/DCAM+Architecture+Home)

1.33

Approved

Architecture/Technical Design navigation, reading order và current-summary only; không sở hữu mutable version/status table.

Hoàng Ngọc Quyền

2026-07-14

[DCAM Architecture Delivery Profile](/wiki/spaces/DVID/pages/50626744/DCAM+Architecture+Delivery+Profile)

1.3

Approved for Build DCAM MVP Internal Build 0.1

Architecture guardrails cho Working Recording Slice; không phải Production/fleet approval

Hoàng Ngọc Quyền

2026-07-13

[DCAM Recording & Capture Design](/wiki/spaces/DVID/pages/48529484/DCAM+Recording+Capture+Design)

1.4

Approved Provisional Baseline

Build 0.1 runtime direction; Camera capability và exact Camera1/Camera2 Pending Device POC

Hoàng Ngọc Quyền

2026-07-16

[DCAM Security & Encryption Design](/wiki/spaces/DVID/pages/48496720/DCAM+Security+Encryption+Design)

1.6

Approved Pending Security Review

Authentication, authorization, credential-protection direction, maintenance-entry model and sensitive-data constraints are approved; exact cryptographic and policy values remain open.

Hoàng Ngọc Quyền

2026-07-14

[DCAM Performance Budget & Resource Constraints](/wiki/spaces/DVID/pages/50659486/DCAM+Performance+Budget+Resource+Constraints)

0.5

Approved Pending Device POC

Build 0.1 performance guardrails; numeric device evidence Pending Device POC

Hoàng Ngọc Quyền

2026-07-13

[DCAM QA Test Strategy & Test Matrix](/wiki/spaces/DVID/pages/49545345/DCAM+QA+Test+Strategy+Test+Matrix)

2.4

Approved

Build 0.1 test definitions và release gates; Important Media Conditional — Not Activated; execution evidence Pending Device POC/Jira linkage.

Hoàng Ngọc Quyền

2026-07-14

[DCAM Device POC & Hardware Validation Report](/wiki/spaces/DVID/pages/49545399/DCAM+Device+POC+Hardware+Validation+Report)

0.8

Approved Pending Device POC

Reference configuration và Build 0.1 test plan approved; qualification/result chưa pass

Hoàng Ngọc Quyền

2026-07-14

[Decision Brief – DCAM MVP Internal Build 0.1 – Working Recording Slice](/wiki/spaces/DVID/pages/51642452/Decision+Brief+DCAM+MVP+Internal+Build+0.1+Working+Recording+Slice)

1.1

Approved for Build DCAM MVP Internal Build 0.1

DEC-01–DEC-07 cho Working Recording Slice; không phải Production approval hoặc Device POC pass

Hoàng Ngọc Quyền

2026-07-13

[04 - Device Configuration Requirements](/wiki/spaces/DVID/pages/47710554/04+-+Device+Configuration+Requirements)

1.7

Approved

Device configuration requirements với narrow Build 0.1 operator exception

Hoàng Ngọc Quyền

2026-07-13

[05 - User & Device Operation Requirements](/wiki/spaces/DVID/pages/47710574/05+-+User+Device+Operation+Requirements)

1.5

Approved

Target-state operator requirements với approved Build 0.1 exception

Hoàng Ngọc Quyền

2026-07-13

[DCAM Logging & Diagnostics Design](/wiki/spaces/DVID/pages/51019937/DCAM+Logging+Diagnostics+Design)

0.6

Approved

Build 0.1 operational logging cho approved decisions; không thay Security Profile

Hoàng Ngọc Quyền

2026-07-16

[DCAM State Machine Design](/wiki/spaces/DVID/pages/48496753/DCAM+State+Machine+Design)

2.1

Approved Provisional Baseline

Target-state model với approved Build 0.1 no-login/checksum overlay

Hoàng Ngọc Quyền

2026-07-13

[DCAM Android Operation Design](/wiki/spaces/DVID/pages/48562239/DCAM+Android+Operation+Design)

2.1

Approved Provisional Baseline

Build 0.1 operation overlay; reference-device behavior Pending Device POC

Hoàng Ngọc Quyền

2026-07-14

[03 - Android Platform & Compatibility Strategy](/wiki/spaces/DVID/pages/47120437/03+-+Android+Platform+Compatibility+Strategy)

1.6

Approved

Global platform strategy với Build 0.1 reference profile Pending Device POC

Hoàng Ngọc Quyền

2026-07-13

[04 - Application & Module Architecture](/wiki/spaces/DVID/pages/47218698/04+-+Application+Module+Architecture)

1.11

Approved

Module boundaries với approved Build 0.1 reference/camera/checksum constraints

Hoàng Ngọc Quyền

2026-07-13

[08 - DCAM-BDMA Integration Boundary](/wiki/spaces/DVID/pages/47153235/08+-+DCAM-BDMA+Integration+Boundary)

1.11

Approved

DCAM–BDMA boundary với approved Build 0.1 import eligibility overlay và legacy MD5 no-profile-mapping guardrail; cryptographic algorithm/key/decryption policy thuộc approved Security Profile.

Hoàng Ngọc Quyền

2026-07-16

[ADR - DCAM Device Identity Baseline: serial_number + dcam_cloud_device_id](/wiki/spaces/DVID/pages/50692110/ADR+-+DCAM+Device+Identity+Baseline+serial_number+dcam_cloud_device_id)

1.1

Approved Direction

Device Identity direction: serial_number là Hardware Identity / primary recovery key và dcam_cloud_device_id là Cloud Identity; implementation, migration, Security/Factory/QA evidence chưa được Production-approved.

Hoàng Ngọc Quyền

2026-07-13

[DCAM Android Device Owner & Kiosk Policy Design](/wiki/spaces/DVID/pages/49840280/DCAM+Android+Device+Owner+Kiosk+Policy+Design)

0.9

Approved Pending Device POC

Device Owner/Kiosk implementation direction và runtime guards; exact OEM/firmware feasibility, Device Owner component, restrictions/allowlist và install behavior Pending Device POC.

Hoàng Ngọc Quyền

2026-07-13

[05 - Data, Storage & BDMA Architecture](/wiki/spaces/DVID/pages/47185950/05+-+Data+Storage+BDMA+Architecture)

1.9

Approved

High-level data/storage/BDMA architecture; MD5 applicability theo Matrix/Data Contract; cryptographic algorithm, key management và decryption policy thuộc DCAM Security & Encryption Design và approved Security Profile.

Hoàng Ngọc Quyền

2026-07-13

[01 - Architecture Overview](/wiki/spaces/DVID/pages/47120395/01+-+Architecture+Overview)

1.6

Approved

High-level system context, component boundary và architecture overview; detailed decisions thuộc authoritative Architecture/ADR/Contract pages.

Hoàng Ngọc Quyền

2026-07-14

[02 - Architecture Principles](/wiki/spaces/DVID/pages/47120416/02+-+Architecture+Principles)

1.8

Approved

Project architecture principles và cross-cutting constraints; build applicability thuộc DCAM Release & Build Applicability Matrix.

Hoàng Ngọc Quyền

2026-07-14

[03 - Media Management Requirements](/wiki/spaces/DVID/pages/47710534/03+-+Media+Management+Requirements)

1.2

Approved

Media management requirement behavior; build applicability thuộc Matrix, filename/data exchange rules thuộc DCAM-BDMA Data Contract.

Hoàng Ngọc Quyền

2026-07-14

[06 - Cloud Services, Update & Configuration Architecture](/wiki/spaces/DVID/pages/47120459/06+-+Cloud+Services+Update+Configuration+Architecture)

3.3

Approved

Cloud, update và configuration architecture boundaries; exact API/schema thuộc Contract, security values thuộc Security Design.

Hoàng Ngọc Quyền

2026-07-14

[07 - Logging, Diagnostics, Performance & Security](/wiki/spaces/DVID/pages/47185971/07+-+Logging+Diagnostics+Performance+Security)

1.8

Approved

Operational quality architecture boundaries cho logging, diagnostics, performance và security; detailed rules thuộc Requirements/Design/QA sources.

Hoàng Ngọc Quyền

2026-07-14

[08 - Security & Encryption Requirements](/wiki/spaces/DVID/pages/47710594/08+-+Security+Encryption+Requirements)

1.5

Approved

Security requirement direction; exact algorithm, key management và policy values thuộc DCAM Security & Encryption Design và Security Review.

Hoàng Ngọc Quyền

2026-07-14

[09 - System Settings Requirements](/wiki/spaces/DVID/pages/47710614/09+-+System+Settings+Requirements)

1.17

Approved

System settings target requirements; build applicability thuộc Matrix, exact provider/payload/runtime implementation thuộc Design/Contract.

Hoàng Ngọc Quyền

2026-07-14

[10 - Android Device Operation Requirements](/wiki/spaces/DVID/pages/48496661/10+-+Android+Device+Operation+Requirements)

1.9

Approved

Android device-operation target requirements; active subset thuộc Matrix, hardware behavior phụ thuộc Device POC khi được nêu.

Hoàng Ngọc Quyền

2026-07-14

[ADR - DCAM Android Dedicated Device / Device Owner / Lock Task Decision](/wiki/spaces/DVID/pages/49774787/ADR+-+DCAM+Android+Dedicated+Device+Device+Owner+Lock+Task+Decision)

1.0

Approved Direction

Dedicated-device, local Device Owner và Lock Task architecture direction; exact DPC component, OEM/firmware feasibility và policy evidence còn phụ thuộc Device POC/Security Review.

Hoàng Ngọc Quyền

2026-07-14

[DCAM Android Development Standard](/wiki/spaces/DVID/pages/47120580/DCAM+Android+Development+Standard)

1.11

Approved

Project-specific Android engineering standard; không thay đổi product scope, requirement hoặc approved architecture.

Hoàng Ngọc Quyền

2026-07-14

[DCAM Android Training & Architecture Onboarding](/wiki/spaces/DVID/pages/46825510/DCAM+Android+Training+Architecture+Onboarding)

1.6

Approved

Developer onboarding và reading guidance; không thay thế Requirements, Architecture, Technical Design hoặc Build Applicability baseline.

Hoàng Ngọc Quyền

2026-07-14

[DCAM Device Capability & Feature Eligibility Design](/wiki/spaces/DVID/pages/48758788/DCAM+Device+Capability+Feature+Eligibility+Design)

0.7

Draft

Draft capability/eligibility design only; exact device support phụ thuộc approved Build Profile và Device POC evidence.

Hoàng Ngọc Quyền

2026-07-14

[DCAM Device Provisioning Web Portal App Design](/wiki/spaces/DVID/pages/50692194/DCAM+Device+Provisioning+Web+Portal+App+Design)

1.3

Approved

Approved Web Portal application flow/UI baseline; implementation/deployment details thuộc Implementation Design và approved Security/API contracts.

Hoàng Ngọc Quyền

2026-07-14

[DCAM Device Provisioning Web Portal Design](/wiki/spaces/DVID/pages/49315858/DCAM+Device+Provisioning+Web+Portal+Design)

1.2

Approved

Approved provisioning business-flow baseline; UI behavior thuộc App Design, implementation thuộc Implementation Design, API/schema thuộc API Contract.

Hoàng Ngọc Quyền

2026-07-14

[DCAM Device Provisioning Web Portal Implementation Design](/wiki/spaces/DVID/pages/51019802/DCAM+Device+Provisioning+Web+Portal+Implementation+Design)

1.2

Approved

Approved Web Portal implementation direction trong phạm vi page; production deployment và security evidence vẫn phải theo approved gates.

Hoàng Ngọc Quyền

2026-07-14

[DCAM DSetup Factory Tool Design](/wiki/spaces/DVID/pages/50626624/DCAM+DSetup+Factory+Tool+Design)

1.3

Approved

Approved DSetup factory-tool behavior; không phải Device POC pass, device qualification hoặc Production shipment approval.

Hoàng Ngọc Quyền

2026-07-14

[DCAM Factory Provisioning & Device Production SOP](/wiki/spaces/DVID/pages/49545629/DCAM+Factory+Provisioning+Device+Production+SOP)

1.3

Approved

Factory provisioning procedure và acceptance gates; device qualification/shipment acceptance chỉ hợp lệ khi required evidence và gates hoàn tất.

Hoàng Ngọc Quyền

2026-07-14

[DCAM In-App Operation, Device Settings & Media Console Design](/wiki/spaces/DVID/pages/49840330/DCAM+In-App+Operation+Device+Settings+Media+Console+Design)

1.2

Draft

Draft in-app console/device-settings/media-console design only; không phải approved implementation hoặc release baseline.

Hoàng Ngọc Quyền

2026-07-14

[DCAM Realtime AI Detection Design](/wiki/spaces/DVID/pages/48595090/DCAM+Realtime+AI+Detection+Design)

0.8

Draft

Draft realtime AI design only; không phải approved implementation, Build 0.1 hoặc Production baseline.

Hoàng Ngọc Quyền

2026-07-14

[DCAM Self Update Design](/wiki/spaces/DVID/pages/48529439/DCAM+Self+Update+Design)

1.1

Draft

Draft self-update design only; không phải approved implementation, release hoặc Production baseline.

Hoàng Ngọc Quyền

2026-07-14

[DCAM Sensor & Location Monitoring Design](/wiki/spaces/DVID/pages/48496794/DCAM+Sensor+Location+Monitoring+Design)

0.7

Draft

Draft sensor/location design only; không phải approved implementation, Build 0.1 hoặc Production baseline.

Hoàng Ngọc Quyền

2026-07-14

[DCAM Web Portal & Device API Contract](/wiki/spaces/DVID/pages/49873154/DCAM+Web+Portal+Device+API+Contract)

0.8

Approved

Approved web/device API and data-contract boundary; deployment và security enforcement phải theo approved Implementation/Security sources.

Hoàng Ngọc Quyền

2026-07-14

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

GitHub repository mapping chưa được phê duyệt và không được ghi như authoritative evidence source.

Navigation pages không copy mutable version/status hoặc Build 0.1 decision tables.