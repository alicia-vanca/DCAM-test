# DCAM Realtime AI Detection Design

**Page ID**: 48595090  
**Version**: 8  
**Type**: page  
**URL**: https://ducviet.atlassian.net/wiki/spaces/DVID/pages/48595090

---


# DCAM Realtime AI Detection Design

Item

Information

Project

DCAM

Document Type

Technical Design

Version

Draft 0.8

Status

Draft

Approval Scope

Draft realtime AI design only; không phải approved implementation, Build 0.1 hoặc Production baseline.

Owner

Hoàng Ngọc Quyền

Technical Reviewer

Tech Lead / Android Lead / QA Lead

Approver

Hoàng Ngọc Quyền

Parent Folder

4.2 - Technical Design

Last Updated

2026-07-14

Related Jira

None

Related Documents

DCAM Android Operation Design, DCAM Device Capability & Feature Eligibility Design, DCAM State Machine Design, DCAM Recording & Capture Design, 09 - System Settings Requirements, DCAM Security & Encryption Design

Target Audience

Tech Lead, Android Developers, AI/ML Engineer, QA, Security Reviewer

## 1. Purpose

Trang này định nghĩa realtime AI / realtime analytics-specific behavior cho DCAM.

Trang này không duplicate shared feature state matrix, Android startup flow, RecordingController authority rule hoặc sensitive logging list. Trang này reference authoritative runtime design documents và apply các rule đó vào realtime analytics runtime.

## 2. Authoritative References

Topic

Authoritative Document

Local Use

RuntimeModuleRegistry and module initialization

DCAM Android Operation Design

Realtime runtime chỉ được initialized thông qua Android runtime registry khi eligible.

Feature state names and runtime pruning

DCAM Device Capability & Feature Eligibility Design

Trang này consume official feature eligibility states.

Runtime transition guards

DCAM State Machine Design

Realtime runtime tuân theo global guard rules.

RecordingController authority

DCAM Recording & Capture Design

Realtime events không được gọi trực tiếp recording/storage/camera.

AI / realtime settings

09 - System Settings Requirements

Requested runtime settings lấy từ validated `dcam.db` values.

Security and sensitive diagnostic data

DCAM Security & Encryption Design

Không duplicate sensitive data rules; apply security design.

## 3. RuntimeModuleRegistry Alignment

Realtime analytics là optional runtime module. Module này phải được register thông qua `RuntimeModuleRegistry` được định nghĩa trong **DCAM Android Operation Design**.

Runtime Module

Initialization Rule

`RealtimeAnalyticsRuntime`

Initialize only khi realtime feature là `ENABLED` hoặc approved `DEGRADED`.

`SampleSelectionController`

Initialize only với eligible realtime runtime.

`CameraSampleAnalyzer`

Attach only khi realtime runtime eligible và camera pipeline cho phép.

`RealtimeModelRuntime`

Initialize only nếu model/runtime package available, valid và capability-compatible.

`AnalyticsEventRouter`

Register only nếu realtime runtime có thể emit events an toàn.

Nếu capability, permission, model availability, thermal/battery policy hoặc performance blocks feature, Android Operation phải prune runtime module.

## 4. Event-to-RecordingController Rule

Realtime analytics không được control recording trực tiếp.

Realtime Analytics Runtime
        ↓
Detection Result / Event Candidate
        ↓
Application Event Router or Emergency Event Manager
        ↓
RecordingController command if recording action is required
Rules:

Rule

Description

AI-RULE-001

Realtime analytics có thể emit detection results hoặc event candidates, không emit recording actions.

AI-RULE-002

Realtime analytics không được gọi trực tiếp CameraService, RecordingEngine, StorageService hoặc RecordingController trừ khi đi qua approved command/event flow.

AI-RULE-003

Emergency Event Manager có thể convert detection event thành emergency request.

AI-RULE-004

Chỉ RecordingController quyết định recording starts, marks important hoặc queues event.

AI-RULE-005

Realtime runtime failure không được crash hoặc block core recording.

## 5. Local Scope

Area

Direction

Sample Selection

Apply local policy cho sampling/rate/input size sau khi runtime eligibility được approved.

Runtime Package / Model

Validate package/model availability và compatibility trước runtime start.

Workload Control

Throttle, degrade hoặc prune realtime workload khi unsafe.

Event Creation

Chỉ tạo detection result/event candidate; không tạo media.

Privacy/Security

Không store/log raw frames, face data hoặc sensitive analytics data trừ khi approved.

## 6. Logging Direction

[ANALYTICS] Runtime eligibility consumed
[ANALYTICS] Runtime initialized
[ANALYTICS] Runtime pruned: <reason_code>
[ANALYTICS] Runtime degraded: <reason_code>
[ANALYTICS] Event candidate emitted
Detailed sensitive logging rules thuộc **07 - Logging & Diagnostics Requirements** và **DCAM Security & Encryption Design**.

## 7. Conclusion

Realtime analytics là optional runtime module.
Android Operation initializes it through RuntimeModuleRegistry.
Device Capability quyết định nó có thể run hay không.
Realtime analytics chỉ emit events.
RecordingController là component duy nhất được phép quyết định recording behavior.