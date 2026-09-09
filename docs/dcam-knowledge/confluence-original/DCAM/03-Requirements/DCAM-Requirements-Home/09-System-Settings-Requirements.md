# 09 - System Settings Requirements

**Page ID**: 47710614  
**Version**: 23  
**Type**: page  
**URL**: https://ducviet.atlassian.net/wiki/spaces/DVID/pages/47710614

---


# 09 - System Settings Requirements

Item

Information

Project

DCAM

Document Type

Functional Requirements

Version

Approved 1.20

Status

Approved

Approval Scope

System settings target requirements, including approved DDMP Hybrid authority boundary; build applicability thuộc Matrix, exact provider/payload/runtime implementation thuộc Design/Contract.

Owner

Hoàng Ngọc Quyền

Technical Reviewer

Tech Lead / Cloud Lead / Security Reviewer / Android Lead

Approver

Hoàng Ngọc Quyền

Parent Folder

03 Requirements / DCAM Requirements Home

Target Audience

PM/BA, Tech Lead, Android Developers, QA, Cloud/WebServer Team

Last Updated

2026-08-25

Related Jira

None

Related Documents

DCAM Release & Build Applicability Matrix, DCAM Factory Provisioning & Device Production SOP, ADR - DCAM Device Identity Baseline: serial_number + dcam_cloud_device_id, DCAM Web Portal & Device API Contract, 04 - Device Configuration Requirements, 10 - Android Device Operation Requirements, 06 - Cloud Services, Update & Configuration Architecture, DCAM Android Device Owner & Kiosk Policy Design, DCAM In-App Operation, Device Settings & Media Console Design, DCAM Android Operation Design, DCAM Recording & Capture Design, DCAM Storage Design, DCAM SQLite Database Design, DCAM Device Capability & Feature Eligibility Design, DCAM State Machine Design, DCAM Self Update Design, DCAM Security & Encryption Design

## 1. Purpose

Trang này định nghĩa requirement-level behavior cho system settings, app operation settings, in-app device/media console settings, remote config, config apply policy, kiosk requested-policy settings, AutoUpdate preconditions và runtime setting boundaries của DCAM.

Current update and identity baseline follows the current authoritative **DCAM Factory Provisioning & Device Production SOP**, **ADR - DCAM Device Identity Baseline: serial_number + dcam_cloud_device_id** and related API/technical designs. Tài liệu không hardcode version của dependent document trong requirement text.

Current DCAM device baseline has no external EMM.
Managed Google Play / Android Management API policy-driven update is not applicable.
Primary update path = DCAM Self Update / APK update.
Google Play Store/Managed Google Play/Google-account update is not a supported device setting or maintenance flow.
serial_number = Hardware Identity / primary recovery key.
dcam_cloud_device_id = Cloud Identity / primary cloud device id.
SD Identity File = recovery cache on external SD card, not Hardware Identity.
Do not use ANDROID_ID, android_id_hash or device_lookup/{android_id_hash} in the current production baseline.
Build applicability is controlled by **DCAM Release & Build Applicability Matrix**:

Build 0.1 requires only local MVP settings needed for recording/storage/BDMA.
Remote Config, full kiosk requested-policy settings and Self Update are deferred for Build 0.1.
They become applicable from Build 0.2 according to the Matrix and approved feature scope.
Trang này định nghĩa remote config identity/apply baseline, storage boundary, runtime guard policy và initial setting groups. Exact field-level remote config payload schema, field names, rollout details và một số value set cụ thể vẫn để TBD cho phase design/implementation tiếp theo.

Chi tiết Android Device Owner / Lock Task / User Restrictions apply behavior thuộc **DCAM Android Device Owner & Kiosk Policy Design**. Chi tiết in-app console UI/feature behavior thuộc **DCAM In-App Operation, Device Settings & Media Console Design**. Chi tiết artifact/download/install flow thuộc **DCAM Self Update Design**.

## 2. Authoritative References

Topic

Source of Truth

Local Usage

Build/phase applicability

DCAM Release & Build Applicability Matrix

Xác định setting group nào bắt buộc, conditional hoặc deferred cho từng build.

Factory provisioning and identity recovery

DCAM Factory Provisioning & Device Production SOP

DSetup resolves serial from SD Identity File or barcode, injects serial, and defines factory execution boundary.

Device identity and serial/config file scope

04 - Device Configuration Requirements

`serial_number` là Hardware Identity/recovery key; `dcam_cloud_device_id` là Cloud Identity.

API/data contract

DCAM Web Portal & Device API Contract

Defines `serial_lookup/{serial_number}` and `devices/{dcam_cloud_device_id}`.

Cloud/WebServer device identity and provisioning flow

06 - Cloud Services, Update & Configuration Architecture

Định nghĩa identity architecture và provisioning architecture.

Kiosk policy

DCAM Android Device Owner & Kiosk Policy Design

Định nghĩa Device Owner/DPC policy, Lock Task Mode, User Restrictions, Home/Launcher policy, Maintenance Mode and no-external-EMM baseline.

In-app operation/device/media console

DCAM In-App Operation, Device Settings & Media Console Design

Định nghĩa App Operation Settings, Device/System Settings proxy, File/Storage Manager, Media Viewer, Login/User settings and Controlled Maintenance Mode; no Play Store fallback UX.

Recording setting application

DCAM Recording & Capture Design

Recording-affecting settings apply only before/safe recording window.

Storage/media setting application

DCAM Storage Design

Storage manager and media viewer must use safe storage/finalization state.

Settings persistence and remote config cache

DCAM SQLite Database Design

Lưu requested/applied/pending config state trong `dcam.db`.

Startup restore/provisioning/runtime policy verification

DCAM Android Operation Design

Thực hiện serial-based identity restore/provisioning-required flow và kiosk policy verification trong Android runtime.

Feature eligibility states

DCAM Device Capability & Feature Eligibility Design

Settings không được override capability limits; GMS-free compliance is a mandatory release gate, not an end-user capability.

Cross-runtime guards

DCAM State Machine Design

Chỉ apply settings/update khi runtime guards cho phép.

Self Update flow

DCAM Self Update Design

Primary current update path; owns APK artifact, validation and install flow.

## 3. System Settings Boundary

Area

Requirement

Status

Operational Settings Storage

Operational/runtime settings phải được lưu trong `dcam.db`, không lưu trong `dcam_config.cson`.

Approved

App Operation Settings Storage

Video resolution, FPS, quality, pre-record, post-record, audio, file split and recording/app behavior settings phải lưu trong `dcam.db`.

Approved Direction

Device/System Requested Settings Storage

USB/Wi-Fi/GPS/device/system requested settings phải lưu trong `dcam.db` hoặc policy manager state; không lưu trong CSON.

Approved Direction

Device Information Storage

Device information như `serial_number` có thể được lưu trong `dcam_config.cson` và mirror sang `dcam.db`.

Approved

Identity Storage

`dcam_cloud_device_id` và `serial_number` được lưu trong `dcam.db`; SD Identity File sync status có thể được lưu nếu cần.

Approved

Requested vs Applied Value

Remote/admin/local settings chỉ là requested values cho đến khi Android validate và apply thành công.

Approved

Pending Config Cache

Remote config đã fetch phải được cache dưới dạng pending/effective config trong `dcam.db`.

Approved Direction

Runtime Guard

Config apply phải bị blocked/deferred khi recording, emergency, finalization, recovery hoặc update guard báo unsafe.

Approved

Capability Guard

Config không được enable feature mà device capability/eligibility không support.

Approved

Kiosk Policy Guard

Kiosk policy config chỉ là requested policy; Android policy managers validate Device Owner/DPC authority, Lock Task allowlist, User Restrictions support và runtime guard trước khi apply.

Approved Direction

In-app Console Guard

In-app console settings must validate access level, capability, policy support and runtime guard before apply.

Approved Direction

Update Source Boundary

AutoUpdate must use DCAM Self Update / APK update as current baseline; Managed Google Play policy-driven update must not be assumed.

Approved Direction

Audit

Kết quả config fetch/apply/reject/rollback/policy defer/admin setting change/update must be logged/audited.

Approved Direction

## 4. Remote Config Identity Baseline

Remote config được resolve bằng stable server-side device identity.

Cloud Identity / BFF Factory Portal boundary and DDMP `platformDeviceId` = dcam_cloud_device_id
Hardware Identity / recovery key = serial_number
Cloud recovery/create/restore lookup = serial_lookup/{serial_number}
Recovery cache = SD Identity File
Not used = ANDROID_ID, android_id_hash, device_lookup/{android_id_hash}
Rules:

Rule

Description

Status

SET-ID-001

Remote config phải được fetch bằng `dcam_cloud_device_id` sau khi identity đã được resolve.

Approved

SET-ID-002

Nếu local cloud identity bị mất nhưng `serial_number` còn, DCAM dùng `serial_lookup/{serial_number}` để recover `dcam_cloud_device_id`.

Approved

SET-ID-003

Nếu local serial mất sau factory reset, DSetup phải recover serial từ SD Identity File hoặc barcode scan và inject serial lại.

Approved

SET-ID-004

`serial_number` là Hardware Identity / primary recovery key, nhưng cloud primary key vẫn là `dcam_cloud_device_id`.

Approved

SET-ID-005

Advertising ID không được dùng làm primary key hoặc recovery key.

Approved

SET-ID-006

`ANDROID_ID`, `android_id_hash` và `device_lookup/{android_id_hash}` không dùng trong current production baseline.

Approved

SET-ID-007

Nếu không tìm thấy `serial_lookup/{serial_number}`, DCAM enters `PROVISIONING_REQUIRED` và follows approved QR-based Factory Worker Web Portal flow.

Approved

## 5. Remote Config Publish / Fetch / Apply Policy

Publish một revision không trực tiếp modify device files.

Server publishes config profile revision
    ↓
Server updates target revision
    ↓
Device learns about update by startup fetch, periodic fetch, optional push signal or manual check
    ↓
Device fetches effective config by dcam_cloud_device_id
    ↓
Device validates config
    ↓
Device stores pending_config in dcam.db
    ↓
Device applies only when runtime guard allows
    ↓
Device records applied_config_revision and reports result
Apply policy:

Case

Behavior

Status

Device online and safe

Fetch, validate và apply.

Approved Direction

Device offline

Giữ current applied config; retry later.

Approved

Device recording

Có thể fetch; apply phải deferred nếu config ảnh hưởng runtime.

Approved

Emergency active

Apply phải deferred.

Approved

Finalizing media

Apply phải deferred.

Approved

DB/storage recovery active

Apply phải deferred.

Approved

Policy recovery active

Apply policy-affecting config phải deferred cho đến khi policy state safe hoặc được xác định fail/degraded.

Approved Direction

Invalid config schema

Reject và giữ last valid applied config.

Approved

Unsupported capability

Reject/degrade affected feature theo Device Capability Design.

Approved

Unsupported kiosk policy

Reject/degrade affected policy theo Kiosk Policy Design; không silently clear restrictions.

Approved Direction

Unsupported in-app console control

Hide/disable/read-only affected control and log/support reason if needed.

Approved Direction

Managed Google Play policy config

Not applicable for current baseline; reject or ignore with safe reason.

Approved Direction

## 6. `dcam_config.cson` Update Policy

Hầu hết remote config values không được ghi vào `dcam_config.cson`.

Config Type

Local Storage

Status

`serial_number`

App-private storage + `dcam_config.cson` + `dcam.db` mirror + SD Identity File recovery cache.

Approved

`dcam_cloud_device_id`

`dcam.db`; optional local mirror if required by implementation.

Approved

SD Identity File sync state

`dcam.db` optional sync state; SD Identity File on external SD card.

Approved Direction

Device model / firmware info

`dcam_config.cson` + DB mirror nếu cần.

Approved Direction

Recording settings

`dcam.db`.

Approved

App operation settings

`dcam.db`.

Approved Direction

Storage mode / thresholds

`dcam.db`.

Approved

File/media viewer preferences

`dcam.db`.

Approved Direction

GPS/sensor/AI flags

`dcam.db`.

Approved

Auth/login settings

`dcam.db`.

Approved Direction

Device/system requested settings

`dcam.db` and/or policy manager state; not CSON.

Approved Direction

Kiosk requested-policy settings

`dcam.db` pending/applied config state; actual Android policy state owned by Kiosk Policy Design.

Approved Direction

Maintenance settings

`dcam.db` non-sensitive requested/applied state and audit metadata; no plaintext maintenance password.

Approved Direction

Update settings

`dcam.db` requested/applied state; Self Update metadata/audit; no secret values.

Approved Direction

Emergency settings

`dcam.db`; exact schema TBD.

TBD

Server/Live/PTT/AI future settings

`dcam.db`; exact schema TBD.

Future / TBD

Remote config cache

`dcam.db`.

Approved

Applied config revision

`dcam.db`.

Approved

CSON write rule:

dcam_config.cson is updated only for device information fields such as serial_number.
Operational settings, app operation settings, console settings, kiosk policy settings and update settings are stored in dcam.db.
SD Identity File is a recovery cache for serial_number, not a remote config file.
## 7. In-app Console Settings Boundary

Kiosk mode means DCAM must expose controlled in-app settings and status surfaces.

Setting Group

Examples

Apply Owner

Storage

Status

App Operation Settings

Video resolution, FPS, bitrate/quality, audio, pre-record time, post-record time, file split duration, overlays.

In-App Console + Recording Design

`dcam.db`

Approved Direction

Device/System Settings Proxy

USB mode, Wi-Fi setup/status, GPS/location, brightness, volume, device info, network status.

In-App Console + Kiosk Policy / Android Operation

`dcam.db` / policy manager state

Approved Direction / Values TBD

File/Storage Manager Settings

Storage target, thresholds, storage dashboard preferences, BDMA readiness view.

In-App Console + Storage Design

`dcam.db`

Approved Direction

Media Viewer Settings

Visibility/filter/playback preferences if needed.

In-App Console + Storage/Security

`dcam.db`

TBD

Login Settings

Password/login method/face auth settings.

In-App Console + Security + SQLite

`dcam.db` + protected credential storage

Approved Direction

User Settings

Admin-only user add/edit/delete-disable/role/login method eligibility.

In-App Console + Security + SQLite

`dcam.db`

Approved Direction

Maintenance Settings

Maintenance enablement, approved target list, timeout, failed attempt policy.

In-App Console + Kiosk Policy + Security

`dcam.db`

Approved Direction / Values TBD

Emergency Settings

Emergency trigger, pre/post time, marker/defaults.

Future Emergency Design + Recording/Security

`dcam.db`

TBD

Server Connection

Server profile, connection health, retry mode.

Future Server Design

`dcam.db`

Future / TBD

Live Stream / PTT

Stream/PTT quality, channel, server target.

Future Live/PTT Design

`dcam.db`

Future / TBD

AI Mode

AI profile/model, enabled modes, confidence thresholds.

Realtime AI/Future AI Mode Design

`dcam.db`

Future / TBD

Rules:

Rule

Description

Status

SET-CONSOLE-001

In-app console settings are requested settings until validated and applied.

Approved

SET-CONSOLE-002

Recording-affecting settings must be deferred/rejected during recording, emergency, finalization or recording recovery.

Approved

SET-CONSOLE-003

Device/system settings requiring policy authority must route through Kiosk Policy Design and Android Operation policy managers.

Approved

SET-CONSOLE-004

File/media viewer must not expose in-progress/temp files as final media.

Approved

SET-CONSOLE-005

File Manager and Media Viewer are read-only; delete/edit/mark/export/share actions are not available.

Approved Direction

SET-CONSOLE-006

Emergency Settings remain TBD and must not silently change emergency behavior before approval.

TBD

SET-CONSOLE-007

Future Server/Live/PTT/AI controls must be hidden/disabled until their requirements/design are approved.

Approved Direction

## 8. Kiosk Requested-policy Settings

Kiosk settings are requested policy values. They do not directly guarantee Android policy apply success.

Setting

Meaning

Apply Owner

Status

`kiosk.enabled`

Request production kiosk policy behavior.

Kiosk Policy Design

Approved Direction

`kiosk.lock_task_enabled`

Request Lock Task Mode for normal field operation.

Kiosk Policy Design

Approved Direction

`kiosk.allowed_packages`

Requested allowlist packages.

Kiosk Policy Design

Approved Direction / Values TBD

`kiosk.lock_task_features`

Requested Lock Task feature profile.

Kiosk Policy Design

TBD

`kiosk.home_app_enabled`

Request DCAM preferred Home/Launcher policy.

Kiosk Policy Design

Approved Direction

`kiosk.user_restriction_profile`

Requested restriction profile name/version.

Kiosk Policy Design

Approved Direction / Values TBD

`maintenance.enabled`

Whether authorized Maintenance Mode is available.

Kiosk Policy Design + Security Design

Approved Direction

`maintenance.exit_method`

Maintenance entry/exit method. Maintenance Password Gate is required.

Kiosk Policy Design + Security Design

Approved Direction

`maintenance.approved_target_packages`

Approved apps that may open during Controlled Mode.

In-App Console + Kiosk Policy

TBD

`maintenance.approved_settings_targets`

Approved Android settings panels/screens.

In-App Console + Kiosk Policy

TBD

`maintenance.session_timeout`

Maintenance session timeout.

In-App Console + Security

TBD

`maintenance.failed_attempt_policy`

Failed password attempt handling.

Security Design

TBD

Rules:

Rule

Description

Status

SET-KIOSK-001

Remote/admin kiosk settings are requested policy, not direct Android system state.

Approved

SET-KIOSK-002

Android must validate Device Owner/DPC authority before applying policy-affecting settings.

Approved Direction

SET-KIOSK-003

Kiosk policy changes must be deferred during recording, emergency, finalization, DB/storage recovery, unsafe update/install or policy recovery.

Approved Direction

SET-KIOSK-004

Unsupported restriction/package/feature must be rejected or degraded with safe reason code.

Approved Direction

SET-KIOSK-005

Maintenance Mode settings must pass Security Review before production use.

Approved Direction

SET-KIOSK-006

External EMM / Android Management API policy settings are not part of current baseline.

Approved Direction

## 9. Update Settings and AutoUpdate Preconditions

Current baseline:

Primary update path in the Hybrid profile = BFF-authorized DCAM Self Update from immutable Cloudflare R2/CDN artifact.
No Google Play Store/Managed Google Play/Google-account fallback exists; remote update uses BFF/R2 and approved local/factory package is separate support fallback.
Managed Google Play / Android Management API policy-driven update = not applicable; Headwind Client does not provide Android policy authority.
Update setting examples:

Setting

Meaning

Apply Owner

Status

`update.self_update_enabled`

Enable DCAM Self Update flow.

Self Update Design

Approved Direction

`update.artifact_provider`

Approved artifact provider such as WebServer/R2/local factory source.

Self Update + Cloud Architecture

TBD

`update.min_version_code`

Minimum allowed/applicable DCAM app version from DCAM update manifest, not Android Management API.

Self Update Design

TBD

`device.gms_free_compliance`

Dependency/manifest/source gate and target-device evidence status.

ADR + Release Matrix

Required for production profile claim

`update.managed_google_play_enabled`

Not applicable for current baseline; should remain false/not supported.

Self Update Design

Not Applicable

AutoUpdate only runs when all approved preconditions pass.

#

Precondition

1

AutoUpdate Enabled

2

Not Emergency Active

3

Not Recording

4

No Active Capture Session

5

Not Post-record / Finalizing

6

Monitoring / AI Safe

7

Capability Evaluation Complete

8

Charging

9

Internet Available

10

Valid Update Package

11

Device Policy State Safe for Update

12

Lock Task / Maintenance Window Safe if update path requires temporary policy transition

13

In-app console/admin setting apply not in unsafe transition

14

Self Update artifact provider reachable if Self Update path is used

15

Managed Google Play policy-driven update not requested on current no-EMM baseline

AutoUpdate must not bypass runtime guards defined in Android Operation, State Machine, Recording, Self Update, Kiosk Policy and In-App Console designs.

## DDMP Hybrid Management Settings Boundary

Hybrid management is an optional Phase 2+/POC-gated profile. This page defines requirement-level constraints only; payload/schema and endpoint details belong to DDMP 03/06.

Rule

Requirement

Status

HSET-001

DCAM must use BFF as the device-facing management boundary. It must not call Headwind REST API/database directly.

Approved Direction

HSET-002

Headwind Client configuration/status must not directly apply Android Device Owner, kiosk, Lock Task, restriction or package-install policy. DCAM remains the sole privileged executor.

Approved Direction

HSET-003

Desired-state/config from BFF is requested input. DCAM validates identity, schema, version and runtime safety before persist/apply; unsafe or duplicate input is ACKed/deferred/rejected with reason.

Approved Direction

HSET-004

`dcam_cloud_device_id` is the DDMP `platformDeviceId`; `serial_number` remains hardware recovery key. A Headwind reference is mapping-only and not an authentication credential.

Approved Direction

HSET-005

Build 0.1 must not require DDMP, Headwind, BFF or R2 to start or record.

Approved

Authoritative DDMP sources: [DDMP 00](/wiki/spaces/DVID/pages/68845572/00+DDMP+Architecture+Overview+Reading+Guide), [DDMP 03 BFF](/wiki/spaces/DVID/pages/68812826/03+Management+API+BFF+Architecture+Baseline), [DDMP 06 Device Integration Contracts](/wiki/spaces/DVID/pages/68812848/06+Device+Integration+Contracts), [DDMP 05 APK Release & Cloudflare R2](/wiki/spaces/DVID/pages/68780056/05+APK+Release+Cloudflare+R2).

## 10. Performance Class, Policy Class and Threshold Status

Item

Status

`device.cpu_performance_class`

Approved Direction / Values TBD

`device.storage_write_class`

Approved Direction / Values TBD

`device.policy_authority_state`

Approved Direction / Values TBD

`device.lock_task_state`

Approved Direction / Values TBD

`device.user_restriction_profile_state`

Approved Direction / Values TBD

`device.gms_available`

Not Applicable in production configuration; DCAM must not depend on this capability.

`device.play_store_available`

Approved Direction / Values TBD

`console.system_setting_control_capability`

Approved Direction / Values TBD

`console.media_viewer_capability`

Approved Direction / Values TBD

`update.self_update_capability`

Approved Direction / Values TBD

`update.play_store_fallback_capability`

Optional / Values TBD

Exact polling interval for remote config

TBD

Exact rollout percentage algorithm

TBD

Exact remote config payload fields

Initial setting groups defined; exact field-level payload schema TBD

Exact in-app console payload fields

Initial setting groups defined; exact field-level payload schema TBD

Exact kiosk policy payload fields

Initial requested-policy groups defined; exact field-level payload schema TBD

Exact rollback UI

TBD

## 11. Practical Conclusion

Remote config identity/apply baseline and initial setting groups have been defined; exact field-level payload schema remains TBD.
Identity and apply architecture are aligned with the current authoritative Factory SOP and identity/API documents.
Document references do not pin a mutable dependent document version.
Build applicability is owned by DCAM Release & Build Applicability Matrix.
dcam_cloud_device_id identifies the device in BFF/PostgreSQL ddmp.
serial_number is Hardware Identity / primary recovery key.
serial_lookup/{serial_number} is used to create/restore dcam_cloud_device_id.
SD Identity File is recovery cache on external SD card, not Hardware Identity.
Do not use ANDROID_ID, android_id_hash or device_lookup/{android_id_hash} in the current production baseline.
Remote config is cached/applied through dcam.db.
dcam_config.cson is updated only for device information fields.
Operational settings, app operation settings, in-app console settings, kiosk policy requested settings and update settings are stored in dcam.db.
Kiosk policy settings are requested policy only.
Actual Device Owner / Lock Task / User Restrictions apply behavior belongs to DCAM Android Device Owner & Kiosk Policy Design.
In-app operation/device/media console behavior belongs to DCAM In-App Operation, Device Settings & Media Console Design.
Current DCAM device baseline has no external EMM / Android Management API / Managed Google Play policy-driven update.
Primary update path is DCAM Self Update / APK update.
No Google Play Store/Managed Google Play/Google-account update path is supported; prohibited source requests are rejected.
AutoUpdate requires device policy state, console/admin transition state and Self Update artifact state to be safe before install.