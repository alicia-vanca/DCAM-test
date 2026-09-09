# DCAM SQLite Database Design

**Page ID**: 48529463  
**Version**: 22  
**Type**: page  
**URL**: https://ducviet.atlassian.net/wiki/spaces/DVID/pages/48529463

---


# DCAM SQLite Database Design

Item

Information

Project

DCAM (Android BodyCamera Application)

Document Type

Technical Design

Version

Approved Provisional Baseline 2.0

Status

Approved Provisional Baseline

Approval Scope

Build 0.1 minimal data/state subset; exact schema/migration/transaction boundary Pending Technical Review

Owner

Hoàng Ngọc Quyền

Technical Reviewer

Tech Lead / DB Reviewer / BDMA Lead / Security Reviewer / Cloud Lead / Android Lead

Approver

Hoàng Ngọc Quyền

Parent Folder

4.2 - Technical Design

Target Audience

Tech Lead, Android Developers, BDMA Developers, QA, Support, Cloud/WebServer Team

Last Updated

2026-08-25

Related Jira

Not linked

Dependencies / Blockers

Technical Review cho physical schema, `schema_version`, recovery representation và transaction implementation; Device POC/ADB compatibility evidence.

Related Documents

DCAM Factory Provisioning & Device Production SOP, DCAM Factory Provisioning Portal & BFF API Contract, DCAM-BDMA Data Contract, 04 - Device Configuration Requirements, 05 - User & Device Operation Requirements, 09 - System Settings Requirements, 06 - Cloud Services, Update & Configuration Architecture, DCAM Android Device Owner & Kiosk Policy Design, DCAM In-App Operation, Device Settings & Media Console Design, DCAM Self Update Design, DCAM Device Capability & Feature Eligibility Design, DCAM Android Operation Design, DCAM Recording & Capture Design, DCAM Storage Design, DCAM State Machine Design, DCAM Security & Encryption Design, 07 - Logging & Diagnostics Requirements, ADR - DCAM GMS-free Android Runtime Baseline, Decision Brief – DCAM MVP Internal Build 0.1 – Working Recording Slice

## 1. Purpose

**DCAM SQLite Database Design** định nghĩa `dcam.db` schema direction và runtime database behavior cho settings, runtime state, media/session state, update state, capability/eligibility state, optional kiosk policy snapshot, in-app console state, maintenance audit/session state, monitoring/tracking state, offline user/operator management, authentication/session state, device identity/provisioning state, device information mirror, app/contract metadata, remote config cache và BDMA write-back compatibility.

Project-wide Device Identity, Device Owner/EMM và update baseline được reference từ:

DCAM Project Home / DCAM Architecture Home.

ADR - DCAM Device Identity Baseline: serial_number + dcam_cloud_device_id.

ADR - DCAM Android Dedicated Device / Device Owner / Lock Task Decision.

DCAM Factory Provisioning & Device Production SOP.

Local database impact:

Persist identity/provisioning fields theo semantics của Identity ADR và API Contract.

Persist policy, maintenance và update state dưới dạng non-sensitive runtime/audit state; DB không sở hữu policy decision.

Không lưu secret, maintenance credential hoặc raw Android system identifier.

Định nghĩa table ownership, schema versioning, migration, transaction, locking, external-write detection và recovery behavior.

## 2. Database Boundary

`dcam.db` là runtime state boundary giữa:

Android runtime
Android Device Owner / Kiosk Policy runtime snapshot when needed
In-app console settings and visibility state
Maintenance session/audit state
Self Update / APK update state
Prohibited update-source rejection audit state
BFF device identity and desired-state configuration
BDMA Desktop write-back / user sync
Recording / Storage / Recovery
User / Operator Authentication
Settings / Remote Config
Capability / Feature Eligibility
Diagnostics / Support
Important rule:

Do not store plaintext maintenance password.
Do not store Google account password/token.
Do not store raw Android system identifier.
Do not store android_id_hash as production identity/recovery lookup.
Do not store unsupported Managed Google Play policy state as current baseline runtime state.
## 3. Authoritative References

Topic

Authoritative Document

Local Usage

DB schema, ownership, transaction, lock, write-back and recovery behavior

DCAM SQLite Database Design

Tài liệu này là authoritative cho `dcam.db` runtime database behavior.

Factory provisioning, DSetup, serial injection and SD Identity File

DCAM Factory Provisioning & Device Production SOP

DSetup resolves serial from SD Identity File or barcode and injects serial into DCAM.

Factory Portal/BFF API and identity contract

DCAM Factory Provisioning Portal & BFF API Contract

Định nghĩa `serial_lookup/{serial_number}`, `devices/{dcam_cloud_device_id}` và production record fields.

Device identity, device information and CSON scope

04 - Device Configuration Requirements

Tài liệu này lưu identity mirror, device information mirror và provisioning state.

BFF identity and Factory Portal provisioning architecture

06 - Cloud Services, Update & Configuration Architecture

Định nghĩa `dcam_cloud_device_id`, `serial_number`, Factory Portal/BFF provisioning and cloud metadata.

Android Device Owner / kiosk policy

DCAM Android Device Owner & Kiosk Policy Design

Tài liệu này chỉ persist optional policy state snapshot/audit pointers nếu cần; actual policy behavior không nằm ở DB design.

In-app console / maintenance UX

DCAM In-App Operation, Device Settings & Media Console Design

Tài liệu này lưu requested/applied console settings, non-sensitive maintenance session/audit state and approved target state if needed.

Self Update

DCAM Self Update Design

Tài liệu này lưu update state/history and validation result; artifact/install behavior belongs to Self Update Design.

Remote config apply policy

09 - System Settings Requirements

Tài liệu này lưu pending/applied config state, including requested kiosk policy/update settings.

User/operator requirements, login policy and emergency override requirement

05 - User & Device Operation Requirements

Tài liệu này persist user/operator/auth/session data.

BDMA-facing data/file/import/write-back/user sync contract

DCAM-BDMA Data Contract

Data Contract định nghĩa BDMA được read/write/sync những gì, bao gồm app/data/media/encoder contract compatibility.

Android runtime startup/login/recovery

DCAM Android Operation Design

Android Operation mở DB, resolve identity, verify policy state và quản lý session lifecycle.

Credential/auth/security/kiosk/update security

DCAM Security & Encryption Design

Credentials, identity values, maintenance values, Google account and policy-sensitive values phải tuân theo security rules.

## 4. Database Ownership Rule

Android owns database schema và runtime invariants.

BDMA và BFF chỉ có thể ảnh hưởng tới approved data thông qua approved sync/config flows. Android validate trước khi apply runtime state.

Core rules:

Android owns schema migration.
Android owns applied runtime state.
Android owns active operator_session runtime state.
Android owns active recording/media session lifecycle.
Android owns local provisioning/apply state.
Android owns optional local policy state snapshot if persisted.
Android owns in-app console setting/apply state.
Android owns maintenance session/audit state.
Android owns Self Update state/history.
KioskPolicyManager owns actual Android Device Owner / Lock Task / User Restrictions behavior.
BFF owns cloud device record and target config revision.
BFF may provide requested kiosk/update settings through desired-state/config contract.
BDMA may write approved user/operator/auth/setting/import fields only.
BDMA must not write bdma_decoder_profile_id because DCAM does not use dynamic decoder profile.
External writes must be detectable by Android.
## 5. Table Ownership Matrix

Table / Area

Android Write

BDMA Write

BFF Write to Device DB

Conflict Policy

`schema_version`

Yes

No

No

Android owns migration.

`device_identity`

Yes

No

Indirect via provision/fetch only

Android lưu resolved identity từ server.

`device_information` / device info fields

Yes

Approved fields only if contract allows

Indirect via provision/fetch only

Android validates and mirrors latest device information.

`device_identity_history`

Yes

No

Indirect via provision/fetch only

Append-only direction.

`provisioning_state`

Yes

No

Indirect via provision status fetch

Android runtime-owned.

`sd_identity_sync_state`

Yes

No

No

Android-owned local sync state for recovery cache.

`kiosk_policy_state`

Yes

No

Indirect requested policy only

Android-owned snapshot; actual policy belongs to Kiosk Policy Design.

`kiosk_policy_history`

Yes

No

No

Append-only safe audit/reference direction.

`console_setting_state`

Yes

No

Indirect requested settings only

Android validates and applies.

`maintenance_session_state`

Yes

No

No

Android-owned; no secret values.

`maintenance_audit_history`

Yes

No

No

Append-only safe audit.

`remote_config_cache`

Yes

No

Indirect via config fetch only

Android validate trước khi cache/apply.

`remote_config_apply_state`

Yes

No

No

Android owns applied state.

`user_profile`

Yes

Yes

No direct write

Two-way sync; conflict rule by Data Contract/User Design.

`user_auth_method`

Yes

Yes, approved fields only

No direct write

Auth refs phải versioned và security-validated.

`operator_session`

Yes

No

No

Android runtime-owned; read-only cho diagnostics.

`user_sync_state`

Yes

Yes

No direct write

Shared sync checkpoint/revision state.

`operational_setting`

Yes

Yes, allowed fields only

Indirect via remote config fetch

Chỉ là requested value; Android validate trước khi apply.

`applied_setting_state`

Yes

No

No

Android source of truth cho applied runtime value.

`feature_eligibility_state`

Yes

No

No

Android evaluate; server/BDMA read cho support.

`update_state`

Yes

No

Indirect update request/config only

Android-owned Self Update runtime state.

`update_history`

Yes

No

No

Append-only safe update audit/history.

`media_session`

Yes

Limited / normally No

No

Android source of truth cho recording/capture lifecycle và operator snapshot.

`media_import_state`

Yes

Yes

No

BDMA có thể update import status theo contract.

`external_change_log`

Yes

Yes

No direct write

Dùng để notify Android về BDMA write-back.

`diagnostic_event`

Yes

No

No

Android write; external read-only.

## 6. Core Table Groups

Group

Candidate Tables

Purpose

Ownership Direction

Schema / Migration

`schema_version`, `schema_migration_history`

DB version và migration status.

Android-owned.

Device Identity / Information / Provisioning

`device_identity`, `device_information`, `device_identity_history`, `provisioning_state`, `sd_identity_sync_state`

Cloud identity, serial mirror, owner name, manufacture date, app/contract metadata, provisioning status and SD recovery-cache sync status.

Android-owned local mirror.

Kiosk Policy Snapshot

`kiosk_policy_state`, `kiosk_policy_history`

Optional snapshot of requested/applied policy revision, lock task state, maintenance mode and last policy error.

Android-owned snapshot only; actual policy owned by Kiosk Policy Design.

In-app Console

`console_setting_state`, `console_module_visibility`, `console_navigation_state` if persisted

Requested/applied console settings, module visibility and support state if needed.

Android-owned; can be derived/runtime-only if simple.

Maintenance

`maintenance_session_state`, `maintenance_audit_history`, `maintenance_failed_attempt_state`

Non-sensitive maintenance session/audit/lockout/cooldown metadata.

Android-owned; no plaintext credential.

Remote Config

`remote_config_cache`, `remote_config_apply_state`, `remote_config_history`

Target/pending/applied config revision và apply result.

Android-owned cache/apply state.

User / Operator

`user_profile`, `user_auth_method`, `operator_session`, `user_sync_state`, `user_change_history`

Offline user management, login methods, operator session và BDMA/DCAM sync.

Shared profile/auth/sync; session Android-owned.

Settings

`operational_setting`, `applied_setting_state`, `setting_change_history`

Requested/applied settings và audit.

Android-owned; BDMA/remote config có thể request allowed values.

Capability / Eligibility

`device_capability_profile`, `device_performance_profile`, `feature_eligibility_state`, `feature_runtime_state`

Capability detection và runtime eligibility.

Android-owned.

Runtime State

`app_runtime_state`, `foreground_service_state`, `recovery_state`

Android runtime và recovery state.

Android-owned.

Media / Recording

`media_session`, `media_event_marker`, `media_finalization_state`

Recording/capture lifecycle, operator snapshot và finalization state.

Android-owned.

BDMA

`media_import_state`, `bdma_import_history`, `external_change_log`

BDMA import/write-back coordination.

Shared theo contract.

Monitoring / Tracking

`sensor_monitoring_state`, `device_tracking`, `tracking_sync_state`

Sensor/GPS/tracking runtime và future sync.

Android-owned; field-level sharing TBD.

Update

`update_state`, `update_history`, `update_package_validation_state`

Self Update state, validation result, install result and history.

Android-owned.

GMS-free Update-source Audit

`prohibited_update_source_event` optional

Rejected request/source event only; no Google account or token.

Android-owned when an attempted prohibited source must be audited.

Diagnostics

`diagnostic_event`, `runtime_error_event`

Support/QA diagnostics.

Android write, external read-only.

## 7. Device Identity, Information and Provisioning Tables

Approved identity rules:

dcam_cloud_device_id is the server/cloud primary key.
serial_number is Hardware Identity / primary recovery key.
serial_lookup/{serial_number} is the cloud create/restore lookup.
SD Identity File is recovery cache on external SD card, not Hardware Identity.
owner_name is mutable/semi-static device information.
manufacture_date is semi-static device information using YYYY-MM-DD.
Advertising ID must not be stored as identity key.
Raw ANDROID_ID must not be stored or logged.
android_id_hash must not be stored/used as production recovery lookup in the current baseline.
bdma_decoder_profile_id must not be stored because BDMA uses built-in compatibility logic based on app/data/media/encoder contract versions.
### 7.1 `device_identity`

Field

Meaning

`dcam_cloud_device_id`

Stable BFF primary cloud device id.

`serial_number`

Hardware Identity / primary recovery key.

`serial_source`

`DSETUP_INJECTED`, `SD_IDENTITY_FILE`, `BARCODE_SCAN`, `WEB_PORTAL`, `SERVER_RESTORE`, `LOCAL_CSON`, `MANUAL_APPROVED_FALLBACK`, etc.

`owner_name`

Latest owner/customer/agency display name.

`manufacture_date`

Device manufacture date in ISO format `YYYY-MM-DD`.

`device_info_source`

Source of owner/manufacture/device info values.

`app_installation_id`

Current app-install instance metadata; not a device identity key and not tied to any cloud SDK.

`device_model`

Model snapshot.

`firmware_version`

Firmware snapshot.

`app_package_name`

Android package name.

`app_version_name`

App version name snapshot.

`app_version_code`

App version code snapshot.

`dcam_data_contract_version`

Overall DCAM-BDMA data contract version.

`media_contract_version`

Media contract version for folder/naming/checksum/import/cleanup.

`encoder_contract_version`

Fixed DCAM encoder contract version.

`identity_state`

`LOCAL_ONLY`, `SERIAL_REQUIRED`, `RESOLVED`, `PROVISIONING_REQUIRED`, `ACTIVE`, `DISABLED`, `REVOKED`, `ERROR`.

`last_identity_lookup_at`

Last serial lookup time.

`last_identity_restore_at`

Last restore time.

`last_seen_report_at`

Last server heartbeat/report time.

### 7.2 `sd_identity_sync_state`

Field

Meaning

`sd_available`

Whether approved SD card/external storage is available.

`sd_identity_file_path_reference`

Approved relative path reference, e.g. `DCAM_FACTORY/device_identity.json`.

`sync_state`

`NOT_REQUIRED`, `SYNCED`, `MISSING_RECREATED`, `CARD_REPLACED_RECREATED`, `FAILED`, `CONFLICT_OVERWRITTEN`, `CONFLICT_REPORTED`.

`last_sync_at`

Last successful sync timestamp.

`last_error_code`

Safe reason code if sync failed.

Rules:

App-private serial_number is source of truth while DCAM is running.
SD Identity File must not override app-private serial_number in normal operation.
If SD Identity File is missing and app-private serial exists, DCAM recreates it.
If SD card is replaced and app-private serial exists, DCAM creates SD Identity File on the new card.
### 7.3 `device_information` Optional Split

For MVP, `owner_name` and `manufacture_date` may be stored directly in `device_identity` to keep schema simple.

If the device information set grows, it may be split into a dedicated table with `dcam_cloud_device_id`, `serial_number`, `owner_name`, `manufacture_date`, `device_model`, `firmware_version`, `updated_at` and `source`.

## 8. Kiosk Policy Snapshot Tables

Detailed Device Owner / Lock Task / User Restrictions behavior thuộc **DCAM Android Device Owner & Kiosk Policy Design**. DB chỉ lưu optional snapshot/audit fields nếu implementation cần recovery/support.

### 8.1 `kiosk_policy_state` Optional Table

Field

Meaning

`requested_policy_revision`

Requested kiosk policy revision from remote/admin config if applicable.

`applied_policy_revision`

Last policy revision successfully verified/applied locally.

`device_policy_state`

`DEVICE_POLICY_UNKNOWN`, `DEVICE_POLICY_REQUIRED`, `DEVICE_POLICY_APPLIED`, `POLICY_DEGRADED`, `POLICY_RECOVERY_REQUIRED`.

`lock_task_state`

`LOCK_TASK_READY`, `LOCK_TASK_ACTIVE`, `LOCK_TASK_FAILED`, `NOT_REQUIRED`, etc.

`user_restriction_profile`

Applied/reported restriction profile name/version.

`maintenance_mode_state`

`NONE`, `REQUESTED`, `ACTIVE`, `EXITING`, `FAILED`.

`last_policy_check_at`

Last policy verification timestamp.

`last_policy_error_code`

Safe policy reason code.

`last_policy_restore_at`

Last restore attempt timestamp after reboot/update/maintenance.

Rules:

kiosk_policy_state is a snapshot, not the source of Android system policy truth.
Actual Device Owner / Lock Task / User Restrictions behavior belongs to KioskPolicyManager and Kiosk Policy Design.
Do not store maintenance credential value, enrollment secret or kiosk exit secret in this table.
## 9. In-app Console and Maintenance State

### 9.1 `console_setting_state`

Field

Meaning

`setting_key`

Console setting key, e.g. video resolution, pre-record, device/system proxy, update option.

`requested_value`

Requested value.

`applied_value`

Actual applied value if applied.

`apply_state`

`PENDING`, `APPLIED`, `DEFERRED`, `REJECTED`, `FAILED`.

`defer_reason`

Runtime guard reason.

`updated_by_user_id`

Actor if local setting change.

`updated_at`

Timestamp.

### 9.2 `maintenance_session_state`

Field

Meaning

`maintenance_session_id`

Maintenance session id.

`requested_by_user_id`

Admin/Maintenance actor.

`state`

`REQUESTED`, `AUTH_REQUIRED`, `AUTH_FAILED`, `ACTIVE`, `EXITING`, `EXPIRED`, `RESTORE_FAILED`, `COMPLETED`.

`started_at`

Session start timestamp.

`expires_at`

Timeout timestamp if configured.

`last_restore_attempt_at`

Last policy restore attempt.

`last_result_code`

Safe result/reason code.

### 9.3 `maintenance_failed_attempt_state`

Field

Meaning

`actor_user_id`

Actor attempting maintenance access.

`failed_count`

Non-sensitive failed attempt count.

`cooldown_until`

Cooldown/lockout time if policy applies.

`last_failed_at`

Last failed attempt timestamp.

Rules:

Maintenance password value must never be stored in dcam.db.
Maintenance password hash input must never be stored in logs/history.
Only non-sensitive lockout/cooldown/session/audit metadata may be stored.
Emergency override cannot satisfy Maintenance Password Gate.
## 10. Self Update and GMS-free Update-source State

### 10.1 `update_state`

Field

Meaning

`update_id`

Current/last update operation id.

`update_source`

`BFF_R2_SELF_UPDATE`, `LOCAL_FACTORY_APK`.

`current_version_code`

Current app version code snapshot.

`target_version_code`

Target version code if known.

`state`

`NONE`, `CHECKING`, `AVAILABLE`, `DEFERRED`, `DOWNLOADING`, `VALIDATING`, `INSTALLING`, `FAILED`, `INSTALLED`, `VERIFIED`.

`defer_reason`

Recording/emergency/finalizing/policy recovery/etc.

`validation_result`

Package validation result.

`install_result`

Install result if available.

`last_checked_at`

Last update check.

`last_attempt_at`

Last install attempt.

`last_success_at`

Last successful update.

### 10.2 `update_history`

Append-only update audit/history.

Field

Meaning

`update_event_id`

Unique event id.

`event_type`

`CHECK_STARTED`, `MANIFEST_LOADED`, `APK_DOWNLOADED`, `APK_VALIDATED`, `INSTALL_STARTED`, `INSTALL_FAILED`, `VERSION_VERIFIED`, `POLICY_RESTORED`, `PROHIBITED_UPDATE_SOURCE_REJECTED`.

`source`

`BFF_R2_SELF_UPDATE`, `LOCAL_FACTORY_APK`, `PROHIBITED_SOURCE`.

`target_package`

DCAM or approved app package.

`old_version_code`

Old version if known.

`new_version_code`

New version if known.

`result_code`

Safe result/reason code.

`created_at`

Timestamp.

Rules:

Current baseline uses BFF-authorized Self Update from immutable R2/CDN artifact as the primary remote update path, with an approved local/factory APK fallback only.
Google Play Store, Managed Google Play, Android Management API and Google-account update flows are prohibited and are not represented as active runtime update states.
If a prohibited source is requested or detected, DCAM rejects it and may append a safe `PROHIBITED_UPDATE_SOURCE_REJECTED` audit event without storing account/token data.
Do not store Google account password/token in dcam.db.
## 11. Remote Config Tables

Remote config rule:

Publishing a server revision does not directly modify local DB/CSON.
Android fetches, validates, caches and applies when runtime guard allows.
Operational settings, console settings, kiosk requested-policy settings and update settings are stored in dcam.db.
dcam_config.cson is updated only for device information fields such as serial_number, owner_name, manufacture_date, device model or firmware information.
Candidate tables:

Table

Purpose

`remote_config_cache`

Lưu fetched config payload/metadata trước runtime apply.

`remote_config_apply_state`

Lưu pending/applied config state.

`remote_config_history`

Append-only fetch/apply/reject/defer history.

## 12. User / Operator Tables

User/operator tables vẫn được định nghĩa theo User Requirements và Security Design:

Table

Purpose

`user_profile`

Offline user/operator profile data.

`user_auth_method`

Approved credential/auth method references.

`operator_session`

Current và historical operator sessions.

`user_sync_state`

BDMA/DCAM user sync checkpoint và revision.

`user_change_history`

User/auth change audit history.

Required system operator:

user_id = SYSTEM_EMERGENCY_OVERRIDE
operator_code = EMERGENCY_OVERRIDE_ADMIN
display_name = Emergency Override Admin
user_type = SYSTEM
role = SYSTEM_ADMIN
status = ACTIVE
Session rule:

Session timeout is disabled.
Background/foreground does not end session.
Device reboot invalidates previous active session and requires login again.
Operator login does not override missing required production kiosk policy.
## 13. Runtime State, Settings and Feature Eligibility

`feature_eligibility_state.eligibility_state` phải dùng official state set từ **DCAM Device Capability & Feature Eligibility Design**.

Allowed values:

ENABLED
DEGRADED
DISABLED_BY_POLICY
DISABLED_BY_PERMISSION
UNSUPPORTED_HARDWARE
UNSUPPORTED_PERFORMANCE
TEMPORARILY_UNAVAILABLE
PRUNED
ERROR
`SUPPORTED` không được dùng làm persisted `feature_eligibility_state` value.

## 14. Media Session Schema Direction

`media_session` lưu recording/capture session state. Recording business lifecycle được định nghĩa bởi **DCAM Recording & Capture Design**.

Required operator attribution fields include:

operator_user_id
operator_code_snapshot
operator_name_snapshot
operator_badge_snapshot
operator_session_id
operator_auth_method
operator_resolution_state
Important distinction:

media_session.state describes Android recording/capture lifecycle.
media_import_state describes BDMA import lifecycle.
BDMA_READY means final media is safe for BDMA scan/import; it does not mean BDMA has imported the file.
## 15. External Write Detection and Apply Policy

BDMA write-back, remote config changes, requested kiosk policy changes, console settings and update settings phải detectable và được apply an toàn.

Change types include:

DEVICE_IDENTITY_RESTORED
SERIAL_NUMBER_UPDATE
OWNER_NAME_UPDATE
MANUFACTURE_DATE_UPDATE
CONTRACT_METADATA_UPDATE
SD_IDENTITY_FILE_SYNCED
SD_IDENTITY_FILE_SYNC_FAILED
REMOTE_CONFIG_FETCHED
REMOTE_CONFIG_APPLY_REQUESTED
REMOTE_CONFIG_APPLIED
KIOSK_POLICY_REQUESTED
KIOSK_POLICY_APPLIED
KIOSK_POLICY_DEFERRED
CONSOLE_SETTING_REQUESTED
CONSOLE_SETTING_APPLIED
MAINTENANCE_SESSION_STARTED
MAINTENANCE_POLICY_RESTORED
SELF_UPDATE_REQUESTED
SELF_UPDATE_DEFERRED
SELF_UPDATE_VERIFIED
PROHIBITED_UPDATE_SOURCE_REJECTED
USER_PROFILE_UPDATE
USER_AUTH_METHOD_UPDATE
USER_SYNC_CHECKPOINT
SETTING_UPDATE
IMPORT_STATE_UPDATE
DEVICE_TRACKING_UPDATE
Apply examples:

Change

During Recording Behavior

Rule

Remote config affecting runtime

Defer tới safe window.

Preserve evidence/runtime stability.

Kiosk policy affecting Lock Task/User Restrictions

Defer during recording/finalizing/emergency/recovery; apply through KioskPolicyManager only.

Preserve evidence and avoid unsafe policy transition.

Console setting affecting recording

Defer/reject during active recording/finalization/emergency.

In-App Console + Recording guard.

Self Update requested

Defer during recording/emergency/finalization/recovery/policy unsafe state.

Self Update guard.

Disable active user

Không interrupt current recording; block new recording sau safe window.

Preserve evidence.

Change storage/encryption/recording settings

Defer tới safe window.

Tránh corrupt session.

## 16. DB Recovery Behavior

Scenario

Expected Behavior

DB missing after reinstall/delete but app-private serial exists

Recreate DB and attempt server identity restore by `serial_lookup/{serial_number}`.

CSON missing after reinstall/delete

Restore serial/device information từ app-private storage/server if identity lookup succeeds.

SD Identity File missing while app-private serial exists

Recreate SD Identity File from app-private serial.

SD card replaced while app-private serial exists

Create SD Identity File on new SD card.

SD Identity File conflicts with app-private serial

App-private serial wins; overwrite SD file or raise warning according to policy.

Factory reset clears app-private data

DSetup must recover serial from SD Identity File or barcode scan and inject serial again.

Serial lookup not found

Enter `PROVISIONING_REQUIRED` or approved factory/admin provisioning flow.

Server unavailable but local identity exists

Continue với last valid local identity/config cache.

Server unavailable and no local identity

Stay trong provisioning/identity recovery state cho đến khi có network hoặc admin action.

Policy snapshot missing

Re-check actual Device Owner/Lock Task/User Restrictions state; recreate snapshot if needed.

Policy snapshot conflicts with Android policy state

Actual Android policy state wins; update snapshot and log safe reason code.

Maintenance session state incomplete after crash/reboot

Attempt policy restore; mark session expired/restore result with safe reason.

Update state incomplete after crash/reboot

Verify current app version and policy state; mark update verified/failed/recovery required.

DB corrupted

Preserve corrupted DB, create recovery DB nếu allowed, enter safe recovery behavior.

Active operator session exists after reboot

Mark `EXPIRED_BY_REBOOT`; yêu cầu login lại.

User table missing/corrupted

Block normal recording, cho phép system modules và emergency override nếu policy cho phép.

Emergency override system operator missing

Recreate từ default/migration nếu safe; nếu không thì log fatal auth config issue.

Media file exists but DB record missing

Reconcile bằng cách scan final folders và insert recovered `media_session` nếu có thể.

Invalid remote config data

Reject và giữ last valid applied config.

Recovery rule:

When database state and file state disagree, preserve source evidence first.
When cloud identity is missing, use serial_number and serial_lookup/{serial_number}.
When app-private serial is missing after factory reset, DSetup must recover serial from SD Identity File or barcode scan.
When policy snapshot is missing or stale, verify actual Android policy state before normal operation.
When maintenance/update state is uncertain, restore kiosk policy before field operation.
When auth/user state is uncertain, block normal recording but allow system modules and emergency override policy if configured.
## 17. Security / Backup / Logging

Area

Direction

Raw ANDROID_ID

Không được store/log raw.

`android_id_hash`

Không dùng/store như production recovery lookup trong current baseline.

Advertising ID

Không được dùng làm identity key.

`serial_number`

Approved Hardware Identity / recovery key; logs must follow safe production logging policy.

SD Identity File

Recovery cache only; do not treat as Hardware Identity.

Credential storage

Password, pattern, QR, NFC credentials chỉ được store dưới dạng hash/reference.

Maintenance credential

Không được store plaintext trong DB; only approved protected representation outside generic runtime tables.

Google account password/token

Không được store/log trong DB.

Update package secrets

Không store private signing key/secret.

DB backup before write-back

Recommended trước BDMA write-back hoặc remote config migration.

Audit

Identity restore, provisioning, SD Identity File sync, config apply, kiosk policy events, maintenance, update and user/auth changes nên được recorded.

Evidence safety

DB recovery không được delete source media/evidence khi chưa có approved rule.

Required example logs use safe identifiers/reason codes only:

[IDENTITY] Device identity restored by serial_number
[IDENTITY] SD Identity File sync result: <reason_code>
[CONFIG] Remote config deferred: <reason_code>
[POLICY] Policy snapshot updated
[MAINTENANCE] Maintenance session started
[MAINTENANCE] Policy restore result: <reason_code>
[UPDATE] Self update deferred: <reason_code>
[UPDATE] Self update verified
[UPDATE] Prohibited GMS/Play update source rejected
[DB] External write detected: <change_type>
[AUTH] Session expired by reboot
## 18. Build 0.1 Minimal Subset

Subset này là semantic implementation boundary cho Working Recording Slice. Không tự chọn physical schema khi Technical Review chưa hoàn tất.

Requirement ID

Minimal Semantic Requirement

Build 0.1

Closure / Guardrail

DB-SCHEMA-001

`dcam.db` phải expose `schema_version` để Android và BDMA kiểm tra compatibility.

Required

Exact type/format/table placement giữ `TBD` đến Technical Review.

DB-MEDIA-001

DB phải giữ đủ media/session identity và file association cho recording/image artifact của slice.

Required

Exact table/column/relationship giữ `TBD`.

DB-FINAL-001

DB phải phân biệt in-progress/finalizing/finalized/ready/recovery state đủ để không expose partial media.

Required

Exact enum/state representation giữ `TBD`.

DB-BDMA-001

`BDMA_READY` chỉ biểu thị final media an toàn cho scan/import; không biểu thị đã import.

Required

Full BDMA write-back không thuộc Build 0.1 scope.

DB-REC-001

DB/file mismatch, missing/corrupt DB và interrupted finalization phải đi vào controlled recovery và preserve evidence.

Required

Exact recovery state/table giữ `TBD`.

DB-OWN-001

Android owns schema/migration/runtime transaction; DB work chạy qua `DbExecutor`; file I/O/checksum/camera wait nằm ngoài DB transaction.

Required

BDMA write chỉ khi contract/schema cho phép; không mở rộng ownership trong changeset này.

### 18.1 Technical Review Items

Item

Current Decision

Guardrail / Closure Gate

Room hoặc raw SQLite

`TBD`

Có thể defer cho backlog; phải chốt trước implementation merge và QA-DB-002 execution.

Exact `schema_version` representation

`TBD`

Chốt trước schema migration/compatibility implementation.

Physical media/session/finalization tables and columns

`TBD`

Chốt bằng Technical Review; không suy ra từ semantic IDs.

Recovery representation

`TBD`

Phải preserve evidence và support QA-DB-003 trước Working Recording Slice exit.

Transaction retry algorithm

`TBD`

Không vượt `PERF-ANR-007`; chốt trước DB implementation merge.

## 19. Open Questions / TBD

Item

Status

Final SQLite implementation approach: Room vs raw SQLite wrapper

TBD

Final journal mode: WAL vs rollback journal on target BodyCamera

TBD / POC

Exact `schema_version` format

TBD

Whether optional snapshot tables are physical tables or runtime-only

TBD / Implementation

Exact SD Identity File sync table/schema need

TBD / Android + QA

Remote config payload fields

Initial setting groups and apply/cache baseline defined in System Settings; exact field-level payload schema TBD

Config profile schema

TBD / Remote Config implementation

Kiosk requested-policy payload fields

Initial requested-policy groups defined in System Settings/Kiosk Policy Design; exact field-level schema TBD

Provisioning API exact contract

API contract baseline defined in DCAM Factory Provisioning Portal & BFF API Contract; exact implementation/auth details TBD

Provisioning QR signature/expiration format

TBD

Owner name validation length/charset

TBD

Manufacture date source and correction policy

TBD

External change detection polling/observer mechanism

TBD

Transaction retry policy

TBD

Exact BDMA user sync allowed fields

TBD in Data Contract

User conflict resolution UI on BDMA

TBD

Face authentication implementation model

TBD by device capability/security review

QR/NFC credential formats

TBD

DB encryption requirement

TBD

`media_session` exact schema

TBD

Self Update physical table schema details

TBD / Implementation

Maintenance failed-attempt lockout physical schema details

TBD / Security + Implementation

Migration test matrix with BDMA/WebServer versions

TBD

## 20. Practical Conclusion

`dcam.db` là runtime database boundary, không phải nơi định nghĩa lại project baseline.

Android runtime sở hữu schema, invariant, migration và transaction boundary.

Identity/provisioning field tuân theo Identity ADR và API Contract; trang này chỉ định nghĩa cách persist/recover local.

Kiosk, maintenance và update field chỉ là non-sensitive state/audit snapshot; behavior thuộc domain design tương ứng.

BDMA chỉ được write approved field/table; mọi external change phải version-checked và detectable.

Khi DB/auth/identity/policy/update state không chắc chắn, implementation phải fail safe, preserve evidence và chuyển sang recovery state phù hợp.

## 21. Build 0.1 Minimal SQLite Subset

Logical State / Data

Build 0.1 Requirement

schema_version

Required

Media / Session

Minimal recording/capture session và finalized artifact state

Checksum

Pending, success/ready và failed representation

BDMA Readiness

Không BDMA_READY trước valid MD5 đối với MP4

Recovery

State đủ để reconcile MP4, MD5 và interrupted finalization sau restart

Operator Snapshot

operator_id = B01OPR; operator_name = Build 0.1 Operator

Ownership

Android/DCAM sở hữu runtime transaction; BDMA tuân theo Data Contract boundary

Exact table, column, Room/raw SQLite choice, migration, transaction grouping và enum spelling chưa được Decision Brief phê duyệt; giữ Pending Technical Review.