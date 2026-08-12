# 05 - User & Device Operation Requirements

**Page ID**: 47710574  
**Version**: 8  
**Type**: page  
**URL**: https://ducviet.atlassian.net/wiki/spaces/DVID/pages/47710574

---


# 05 - User & Device Operation Requirements

Item

Information

Project

DCAM (Android BodyCamera Application)

Document Type

Functional Requirements

Version

Approved 1.5

Status

Approved

Approval Scope

Target-state operator requirements với approved Build 0.1 exception

Owner

Hoàng Ngọc Quyền

Technical Reviewer

Tech Lead / BDMA Lead / Security Reviewer

Approver

Hoàng Ngọc Quyền

Parent Folder

03 Requirements / DCAM Requirements Home

Target Audience

PM/BA, Tech Lead, Android Developers, BDMA Developers, QA, Support, Security Reviewer

Last Updated

2026-07-21

Related Jira

None

Related Documents

DCAM Requirements Home, 04 - Device Configuration Requirements, 09 - System Settings Requirements, DCAM-BDMA Data Contract, DCAM SQLite Database Design, DCAM Android Operation Design, DCAM Recording & Capture Design, DCAM Security & Encryption Design, DCAM State Machine Design, Decision Brief – DCAM MVP Internal Build 0.1 – Working Recording Slice

## 1. Purpose

Tài liệu này định nghĩa các **Functional Requirements** liên quan đến **User / Operator Management**, **Operator Authentication**, **Login Session**, **Emergency Override**, **User Sync** và **Media Attribution** của DCAM.

Tài liệu này chốt requirement-level behavior. Các chi tiết technical design, DB schema, security handling và runtime orchestration được định nghĩa trong các tài liệu source of truth tương ứng.

## 2. Core Decisions

Decision Area

Decision

Status

Offline-first user management

DCAM phải có khả năng xác thực và vận hành user/operator local sau khi đã có local user data hợp lệ.

Approved

User data ownership

DCAM lưu user/operator data trong `dcam.db`; BDMA lưu user/operator data trong BDMA database.

Approved

User management on DCAM

DCAM có thể quản lý user/operator theo phạm vi được phép bởi product policy.

Approved

User management on BDMA

BDMA có thể quản lý user/operator và sync với DCAM qua ADB.

Approved

User sync direction

User/operator data sync two-way giữa DCAM và BDMA qua ADB.

Approved

Startup login

Sau khi device active/provisioned, DCAM phải hiển thị login screen nếu không có valid active operator session.

Approved

Normal recording gate

Normal recording và capture evidence yêu cầu active authenticated operator session.

Approved

Emergency override

Emergency recording được phép chạy without login nếu không có active operator session.

Approved

Emergency identity

Emergency override dùng system operator `EMERGENCY_OVERRIDE_ADMIN`, không dùng real Admin user.

Approved

Session timeout

Operator session không có time-based timeout.

Approved

Background behavior

App background/foreground hoặc screen off/on không tự logout operator.

Approved

Reboot behavior

Device reboot invalidates previous active session và yêu cầu login lại.

Approved

System modules before login

Logging, recovery, capability detection, sensor/GPS tracking và identity/provisioning checks có thể chạy trước login nếu safe.

Approved

Login methods

DCAM hỗ trợ Password, Pattern, Face Authentication, QR Code và NFC Tag theo device capability/security policy.

Approved Direction

## 3. User / Operator Management Scope

DCAM và BDMA cùng tham gia quản lý user/operator, nhưng runtime responsibility khác nhau.

Area

DCAM Requirement

BDMA Requirement

Notes

User profile

DCAM lưu user/operator profile local trong `dcam.db`.

BDMA lưu user/operator profile trong BDMA database.

Hai phía sync theo Data Contract.

User create/edit/delete/disable

DCAM có thể thực hiện theo policy được cấu hình.

BDMA có thể thực hiện theo quyền quản trị.

Conflict rule thuộc Data Contract / SQLite Design.

Auth method management

DCAM lưu auth method reference/protected representation để login offline.

BDMA có thể sync auth method data theo allowed fields.

Security Design định nghĩa constraint.

Operator session

DCAM là runtime owner của active operator session.

BDMA không được modify active `operator_session`.

BDMA chỉ read diagnostics nếu contract cho phép.

User sync

DCAM apply user sync sau validation và runtime guard.

BDMA là sync initiator qua ADB khi kết nối thiết bị.

Active recording phải được bảo vệ.

## 4. Operator Authentication Requirement

Normal recording và evidence capture phải được gắn với một authenticated operator.

Requirement ID

Requirement

Status

UDO-AUTH-001

DCAM phải yêu cầu operator login trước khi cho phép normal recording.

Approved

UDO-AUTH-002

DCAM phải yêu cầu operator login trước khi cho phép normal capture evidence.

Approved

UDO-AUTH-003

DCAM phải resolve active operator session trước khi RecordingController tiếp tục precheck.

Approved

UDO-AUTH-004

Nếu không có active operator session, normal recording phải bị reject với reason `OPERATOR_AUTH_REQUIRED`.

Approved

UDO-AUTH-005

UI phải hướng user/operator về login screen khi normal operation cần authentication.

Approved

UDO-AUTH-006

System modules không yêu cầu login nếu module đó không tạo evidence action thay mặt operator.

Approved

Normal recording gate:

StartRecordingRequested
    ↓
Check active operator session
    ↓
If active operator exists:
    continue Recording Precheck
    ↓
If no active operator:
    reject OPERATOR_AUTH_REQUIRED
    show Login Screen
## 5. Emergency Override Requirement

Emergency recording phải có khả năng hoạt động khi chưa có operator logged in, nhưng vẫn phải có attribution/audit rõ ràng.

Requirement ID

Requirement

Status

UDO-EO-001

Emergency recording được phép start without active operator session.

Approved

UDO-EO-002

Emergency recording without login phải dùng system operator `EMERGENCY_OVERRIDE_ADMIN`.

Approved

UDO-EO-003

Emergency override không được map vào real Admin user.

Approved

UDO-EO-004

Emergency override không cấp full interactive Admin UI access.

Approved

UDO-EO-005

Media/session created by emergency override phải lưu operator attribution snapshot.

Approved

UDO-EO-006

Emergency override usage phải được audit/log theo Security Design.

Approved

Required emergency system identity:

user_id = SYSTEM_EMERGENCY_OVERRIDE
operator_code = EMERGENCY_OVERRIDE_ADMIN
display_name = Emergency Override Admin
user_type = SYSTEM
role = SYSTEM_ADMIN
status = ACTIVE
Emergency recording gate:

EmergencyRecordingRequested
    ↓
Check active operator session
    ↓
If active operator exists:
    use active operator attribution
    ↓
If no active operator exists:
    use EMERGENCY_OVERRIDE_ADMIN attribution
    ↓
Continue emergency recording precheck
## 6. Login Session Requirements

Operator session policy của DCAM được thiết kế cho dedicated BodyCamera device.

Requirement ID

Requirement

Status

UDO-SESSION-001

Operator session không có time-based timeout.

Approved

UDO-SESSION-002

App chuyển background không làm logout operator.

Approved

UDO-SESSION-003

App quay lại foreground phải dùng lại active session nếu session vẫn valid.

Approved

UDO-SESSION-004

Screen off/on không làm logout operator.

Approved

UDO-SESSION-005

Process kill/recreate có thể restore session nếu cùng `device_boot_id` và DB/auth data valid.

Approved

UDO-SESSION-006

Device reboot phải invalidate previous active session.

Approved

UDO-SESSION-007

Manual logout hoặc admin revoke có thể kết thúc session theo policy.

Approved

UDO-SESSION-008

User disabled/auth method revoked từ sync không được interrupt active recording; effect áp dụng tại safe window.

Approved

Session behavior summary:

App background → keep session
App foreground → reuse valid session
Screen off/on → keep session
Process recreate without reboot → restore if same boot and valid
Device reboot → require login again
Manual logout/admin revoke → end session according to policy
## 7. Login Method Requirements

DCAM hỗ trợ nhiều login methods theo device capability và security policy.

Login Method

Requirement

Status

Password

Hỗ trợ offline verification bằng protected representation theo Security Design.

Approved Direction

Pattern

Hỗ trợ offline verification; không lưu raw pattern path/sequence.

Approved Direction

Face Authentication

Chỉ enable nếu device capability và security policy cho phép.

Approved Direction

QR Code

Hỗ trợ login bằng credential id/token mapping có thể validate offline.

Approved Direction

NFC Tag

Hỗ trợ login bằng NFC credential mapping có thể validate offline.

Approved Direction

Emergency Override

Chỉ dùng cho emergency recording khi no active operator session.

Approved

Rules:

Login method availability depends on Device Capability and Security Design.
Credential/auth sensitive data must follow Security & Encryption Design.
Unsupported login methods must be hidden, disabled or marked unavailable.
## 8. User / Operator Data Sync Requirements

User/operator data phải sync được giữa DCAM và BDMA, nhưng Android runtime vẫn là owner của active runtime state.

Requirement ID

Requirement

Status

UDO-SYNC-001

DCAM stores user/operator data in `dcam.db`.

Approved

UDO-SYNC-002

BDMA stores user/operator data in BDMA database.

Approved

UDO-SYNC-003

BDMA syncs user/operator data with DCAM through ADB.

Approved

UDO-SYNC-004

Sync must be versioned and auditable.

Approved

UDO-SYNC-005

Android must validate user/auth sync data before applying.

Approved

UDO-SYNC-006

Invalid sync data must be rejected and last valid state preserved.

Approved

UDO-SYNC-007

Active operator session must not be modified directly by BDMA.

Approved

UDO-SYNC-008

User disabled while recording must not interrupt active recording; new recording is blocked after safe window.

Approved

UDO-SYNC-009

Conflict resolution follows Data Contract / SQLite Database Design.

Approved Direction

Sync direction:

BDMA user/operator database
    ↕ ADB sync
DCAM dcam.db user/operator tables
    ↓
Android validation + runtime guard
    ↓
Applied user/auth/session availability
## 9. Media Attribution Requirements

Mọi normal recording/capture evidence cần có operator attribution rõ ràng.

Requirement ID

Requirement

Status

UDO-ATTR-001

Normal recording phải lưu operator snapshot từ active operator session.

Approved

UDO-ATTR-002

Normal capture evidence phải lưu operator snapshot từ active operator session.

Approved

UDO-ATTR-003

Emergency recording without login phải lưu emergency override snapshot.

Approved

UDO-ATTR-004

Operator snapshot phải giữ nguyên kể cả khi user profile thay đổi sau đó.

Approved

UDO-ATTR-005

BDMA phải phân biệt real operator attribution và emergency override attribution.

Approved

Required attribution fields:

operator_user_id
operator_code_snapshot
operator_name_snapshot
operator_badge_snapshot
operator_session_id
operator_auth_method
operator_resolution_state
Operator resolution states:

State

Meaning

`RESOLVED`

Active authenticated operator session đã được resolve thành công.

`EMERGENCY_OVERRIDE`

Không có active operator session và emergency recording dùng `EMERGENCY_OVERRIDE_ADMIN`.

`OPERATOR_AUTH_REQUIRED`

Normal recording/capture bị reject vì thiếu active operator session.

`INVALID`

Operator/session data không hợp lệ hoặc không thể dùng.

## 10. Source of Truth

Topic

Source of Truth

Requirement-level user/operator behavior

05 - User & Device Operation Requirements

User/operator sync boundary

DCAM-BDMA Data Contract

User/auth/session DB schema

DCAM SQLite Database Design

Startup login/session lifecycle runtime

DCAM Android Operation Design

Recording operator gate and attribution

DCAM Recording & Capture Design

Credential/auth security constraints

DCAM Security & Encryption Design

Cross-runtime guard and state coordination

DCAM State Machine Design

## 11. Practical Conclusion

DCAM uses offline-first user/operator management.
Normal recording and evidence capture require active authenticated operator session.
Emergency recording can run without login using EMERGENCY_OVERRIDE_ADMIN.
Operator session has no time-based timeout.
Background/foreground and screen off/on do not logout operator.
Device reboot requires login again.
DCAM and BDMA both manage user/operator data.
BDMA syncs user/operator data with DCAM through ADB.
Active operator_session runtime state is Android-owned.
Media sessions must store operator attribution snapshot.
Emergency override must be auditable and distinguishable from real operator attribution.
## 12. Approved Build 0.1 Operator Exception

Đối với Build 0.1, section này override login/session/authentication requirements mâu thuẫn ở target-state sections phía trên. Các target-state requirements vẫn giữ hiệu lực cho build sau.

Requirement ID

Build 0.1 Requirement

Status

UDO-B01-001

Không có login UI; normal recording/capture không yêu cầu authenticated operator session.

Approved for Build DCAM MVP Internal Build 0.1

UDO-B01-002

operator_id luôn là B01OPR và operator_name luôn là Build 0.1 Operator.

Approved for Build DCAM MVP Internal Build 0.1

UDO-B01-003

Hai giá trị không thể sửa từ UI hoặc runtime configuration và phải được snapshot nhất quán tại nơi SQLite/CSON/log schema yêu cầu.

Approved for Build DCAM MVP Internal Build 0.1

UDO-B01-004

Placeholder chỉ phục vụ technical traceability; không phải authenticated identity, credential, role hoặc authorization claim.

Approved for Build DCAM MVP Internal Build 0.1

Authentication, authorization, roles và operator management là Out of Scope cho Build 0.1.