# ADR - DCAM GMS-free Android Runtime Baseline

**Page ID**: 69730306  
**Version**: 1  
**Type**: page  
**URL**: https://ducviet.atlassian.net/wiki/spaces/DVID/pages/69730306

---


# ADR - DCAM GMS-free Android Runtime Baseline

Item

Information

Project

DCAM (Android BodyCamera Application)

Document Type

Architecture Decision Record

Version

Approved Direction 1.0

Status

Approved Direction

Approval Scope

Mandatory production Android runtime baseline: no Google Play services / GMS or Google Play Store dependency. Defines allowed Firebase Crashlytics boundary, prohibited Android dependencies, verification gates and document impact; does not replace the approved DCAM DPC, DDMP BFF or Cloudflare R2 authority boundaries.

Decision Date

2026-08-25

Last Updated

2026-08-25

Related Jira

None

Owner

Hoàng Ngọc Quyền

Technical Reviewer

Tech Lead / Android Lead / Security Reviewer / QA Lead / Cloud Lead

Approver

Hoàng Ngọc Quyền

Parent Folder

4.4 - Architecture Decision Records (ADR)

Target Audience

PM/BA, Tech Lead, Android Developers, QA, Security Reviewer, Factory, Support, Cloud/WebServer Team

Related Documents

DCAM Project Home, DCAM Architecture Home, 02 - Architecture Principles, 03 - Android Platform & Compatibility Strategy, 04 - Application & Module Architecture, 06 - Cloud Services, Update & Configuration Architecture, ADR - DCAM Android Dedicated Device / Device Owner / Lock Task Decision, DCAM Self Update Design, DCAM Android Device Owner & Kiosk Policy Design, DCAM In-App Operation, Device Settings & Media Console Design, DCAM Security & Encryption Design, DCAM Logging & Diagnostics Design, DCAM QA Test Strategy & Test Matrix, DCAM Factory Provisioning & Device Production SOP, DCAM Release & Build Applicability Matrix, DDMP 01 Hybrid System Architecture, DDMP 06 Device Integration Contracts

## 1. Context

DCAM chạy trên BodyCamera dedicated-device. Approved Hybrid architecture xác định:

DCAM = sole Android Device Owner / DPC and privileged policy executor
Headwind Client = ordinary Application-mode app only
BFF = API boundary, desired-state, authorization and audit boundary
Headwind Community = limited fleet control-plane only
Cloudflare R2/CDN = immutable APK / manifest artifact plane
Tài liệu hiện hữu đã có hướng core operation hoạt động trên thiết bị non-GMS. Tuy nhiên, một số tài liệu vẫn mô tả Google Play Store như optional maintenance/update fallback, cùng capability, test và Google-account process tương ứng. Điều này tạo hai baseline không tương thích:

```
GMS optional  ≠  GMS-free required
```

Production BodyCamera phải không phụ thuộc Google Play services, Play Store hay Google account để recording, evidence handling, kiosk policy, provisioning, diagnostics, management sync hoặc DCAM APK update hoạt động.

## 2. Decision

### 2.1 Mandatory production Android baseline

DCAM production Android runtime = GMS-free required

No Google Play services / GMS runtime dependency.
No Google Play Store / Managed Google Play distribution or maintenance flow.
No Google account is required, used or stored on a production device.
“GMS-free” trong ADR này là phạm vi **Android device runtime**. Nó không tự động có nghĩa “không dùng bất kỳ dịch vụ Google/Firebase nào ở web/server”.

### 2.2 Approved authority and delivery path

Policy authority:        DCAM DPC only
Management/config API:   BFF
APK release authority:   BFF
APK artifact delivery:   immutable Cloudflare R2/CDN
APK validation/install:  DCAM DPC under safe runtime and kiosk guard
Fleet supplementary:     Headwind Client / Community, Application mode only
DCAM APK update có hai source class hợp lệ:

Priority

Source

Rule

1

BFF-authorized immutable R2/CDN manifest and APK

Production primary path. DCAM validates authorization, package identity, signature, checksum, version and compatibility before install.

2

Approved local/factory APK package

Support/factory fallback only when explicitly approved, validated and auditable.

Not applicable

Google Play Store, Managed Google Play, Android Management API policy-driven update

Không được dùng trong production baseline này.

### 2.3 Allowed Firebase boundary

Firebase là product family khác với Google Play services. Chỉ Firebase Android SDK không yêu cầu Google Play services mới có thể được xem xét, sau dependency review.

Component

Decision

Boundary

Firebase Crashlytics

Allowed, optional telemetry provider

Fatal crash, ANR và approved unexpected non-fatal only. Không thay thế Operational Logging; outage/unavailable không ảnh hưởng core operation.

Firebase Remote Config Android SDK

Not part of approved DCAM production baseline

BFF desired-state/config + local validated defaults là authority; không tạo second config control-plane ở device.

Firebase Hosting/Auth/Functions/Firestore for Factory Web Portal

Out of Android-runtime scope

Có thể tiếp tục theo cloud/web baseline hiện hành; không được hiểu là GMS dependency của DCAM device runtime.

Other Firebase Android SDK

Not approved unless ADR này và dependency gate được cập nhật

Không tự thêm vì “Firebase” không đồng nghĩa automatically GMS-free.

### 2.4 Prohibited Android dependencies and flows

DCAM production Android build, manifest, runtime flow và factory/maintenance process **must not** contain or require:

com.google.android.gms:* dependencies
Google Play services availability checks as a product capability
Google Play Store / com.android.vending launch, browse or update flow
Managed Google Play
Android Management API as a device policy/update channel
Firebase Cloud Messaging (FCM)
Firebase Analytics
Firebase App Check Play Integrity provider
Google Play Integrity API
Google Sign-In
Google Maps Android SDK
Firebase Phone Number Verification
Google account sign-in, storage, maintenance or factory procedure
A dependency may not be introduced transitively. The implementation must inspect the resolved release runtime dependency graph, not only direct Gradle declarations.

## 3. Consequences

Area

Required consequence

Dedicated device

DCAM remains the only Device Owner/DPC. GMS absence must not weaken Lock Task, user restrictions, auto-start/recovery or controlled maintenance behavior.

Kiosk / Maintenance

No Play Store target, allowlist exception or Google account workflow. Maintenance Mode may expose only explicitly approved system/support surfaces.

Update

BFF-authorized R2/CDN Self Update becomes the only remote production APK path. Update failure must preserve kiosk/DPC state.

Configuration

BFF desired-state and validated local defaults are the device configuration baseline.

Diagnostics

Local-first logs, bounded queue and BDMA diagnostics remain mandatory. Loggly is operational logging; Crashlytics is optional crash/stability telemetry.

Offline operation

Recording, evidence integrity, local diagnostics, DPC/kiosk recovery and safe update deferral remain functional without GMS, provider or cloud reachability.

DDMP

Headwind Client must not be used to reintroduce Play Store, FCM, GMS policy or privileged update authority.

## 4. Verification and Release Gates

A production release cannot claim this baseline unless all gates below pass:

Gate

Required evidence

Dependency gate

Resolved `releaseRuntimeClasspath` contains no `com.google.android.gms:*`, FCM, Analytics, Play Integrity/App Check Play Integrity, Google Sign-In, Maps SDK or other prohibited component.

Manifest/source gate

No Play Store intent/package flow, Google-account maintenance flow, FCM registration/token flow or GMS availability dependency.

Device POC gate

Provision, normal field operation, recording, evidence finalization, reboot/process recovery, DPC/Lock Task and BFF sync work on target firmware without GMS/Play Store.

Update gate

R2/CDN update validates artifact and preserves DPC/Lock Task/User Restrictions after success, failure and reboot.

Observability gate

Local diagnostics and BDMA artifact work without GMS, Crashlytics, Loggly or network; Crashlytics evidence is validated only when the optional provider is enabled.

Factory/SOP gate

No production device provisioning, maintenance or recovery step requires a Google account or Play Store.

DDMP coexistence gate

Headwind Client install/update/reboot cannot disturb DCAM DPC, HOME, Lock Task, update authority or recovery.

The Release & Build Applicability Matrix owns active build applicability. QA and Device POC own test evidence; this ADR owns the architectural decision only.

## 5. Required Documentation Alignment

Priority

Documents

Required alignment

P0

Project Home; Architecture Home; 02 Principles; 03 Platform; 04 Modules; 06 Cloud; Release & Build Applicability Matrix

Publish/reference this mandatory GMS-free baseline; remove wording that makes GMS/Play Store an optional supported production profile.

P1

Self Update; Kiosk; In-App Operation Console; Android Operation; State Machine; Capability; System Settings; Android Device Operation Requirements

Remove Play Store fallback, GMS/Play capability, Google-account flow, related events/reason codes and feature flags.

P1

Factory SOP; QA Matrix; Device POC Report

Replace Play Store cases with explicit no-GMS acceptance tests and R2/local-factory recovery evidence.

P2

Security; Logging; Logging Requirements; NFR; Performance; Android Development Standard

Apply prohibited dependency list, Crashlytics boundary and mandatory local-first/no-GMS diagnostics rule.

DDMP contract check

DDMP 01 and 06

Reconfirm that Headwind Application mode does not introduce a GMS, FCM or privileged update dependency.

Until the P0/P1 documents are aligned, this ADR is the prevailing authority where wording conflicts.

## 6. Accepted Risks and Mitigations

Risk

Mitigation

Some OEM firmware or bundled app assumes GMS.

Device POC validates target firmware before production profile approval; unsupported firmware is not accepted silently.

Crashlytics telemetry unavailable or degraded.

Local-first diagnostics, Loggly queue and BDMA artifact remain independent fallback; core operation never depends on Crashlytics.

R2/BFF update path is unavailable.

Defer safely, retain current trusted APK and use separately approved local/factory package process; never switch to Play Store.

A future library adds a transitive GMS dependency.

Enforce resolved dependency gate in CI/release review.

Firebase Android SDK ambiguity causes accidental scope expansion.

Every new Firebase Android SDK requires dependency classification and explicit architecture/security review.

Factory/support tries to use a personal account as a workaround.

SOP and maintenance UX prohibit Google accounts and Play Store; audit and QA enforce the rule.

## 7. Decision Summary

DCAM production Android runtime is GMS-free by requirement, not merely compatible with non-GMS devices.

DCAM must not depend on Google Play services, Google Play Store, Managed Google Play,
Android Management API, FCM, Analytics, Play Integrity, Google Sign-In or Google-account maintenance.

DCAM remains the sole Device Owner/DPC.
BFF remains the API/release authority.
Cloudflare R2/CDN remains the immutable APK artifact plane.
Headwind remains Application mode and a limited supplementary control-plane.

Firebase Crashlytics may remain as optional crash/stability telemetry because it does not require GMS;
it never replaces local diagnostics or operational logging.
## 8. External Technical Reference

Firebase’s current Android dependency matrix and integration guidance are the external reference for whether a specific Firebase SDK requires Google Play services. Dependency status can change with library versions; the CI/release gate remains mandatory.

[https://firebase.google.com/docs/android/android-play-services](https://firebase.google.com/docs/android/android-play-services)

[https://firebase.google.com/docs/android/learn-more](https://firebase.google.com/docs/android/learn-more)