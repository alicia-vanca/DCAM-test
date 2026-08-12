# DCAM Sensor & Location Monitoring Design

**Page ID**: 48496794  
**Version**: 7  
**Type**: page  
**URL**: https://ducviet.atlassian.net/wiki/spaces/DVID/pages/48496794

---


# DCAM Sensor & Location Monitoring Design

Item

Information

Project

DCAM

Document Type

Technical Design

Version

Draft 0.7

Status

Draft

Approval Scope

Draft sensor/location design only; không phải approved implementation, Build 0.1 hoặc Production baseline.

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

DCAM Android Operation Design, DCAM Device Capability & Feature Eligibility Design, DCAM State Machine Design, DCAM Recording & Capture Design, 09 - System Settings Requirements

Target Audience

Tech Lead, Android Developers, QA, Support

## 1. Purpose

Trang này định nghĩa monitoring-specific behavior cho Sensor và Location runtime.

Trang này không duplicate shared feature state matrix, Android startup flow, RecordingController authority rule hoặc DB schema. Trang này reference authoritative runtime design documents và apply các rule đó vào sensor/location monitoring.

## 2. Authoritative References

Topic

Authoritative Document

Local Use

RuntimeModuleRegistry and module initialization

DCAM Android Operation Design

Sensor/location modules chỉ được initialized thông qua Android runtime registry khi eligible.

Feature state names and runtime pruning

DCAM Device Capability & Feature Eligibility Design

Trang này consume official feature eligibility states.

Runtime transition guards

DCAM State Machine Design

Monitoring/tracking tuân theo global guard rules.

RecordingController authority

DCAM Recording & Capture Design

Sensor events không được gọi trực tiếp recording/storage/camera.

Monitoring/location settings

09 - System Settings Requirements

Requested settings lấy từ validated `dcam.db` values.

## 3. RuntimeModuleRegistry Alignment

Sensor và location runtime modules là optional modules. Các module này phải được register thông qua `RuntimeModuleRegistry` được định nghĩa trong **DCAM Android Operation Design**.

Runtime Module

Initialization Rule

`SensorMonitoringRuntime`

Initialize only khi motion/fall monitoring feature là `ENABLED` hoặc approved `DEGRADED`.

`LocationTrackingRuntime`

Initialize only khi location tracking feature là `ENABLED` hoặc approved `DEGRADED`.

`TrackingBufferRuntime`

Initialize only nếu tracking enabled và DB/storage conditions safe.

`MonitoringPolicyProvider`

Read validated settings từ `dcam.db`; không override capability/eligibility.

Nếu capability, permission hoặc policy blocks feature, Android Operation phải prune module và design này chỉ định nghĩa local consequence.

## 4. Event-to-RecordingController Rule

Sensor và location runtime không được control recording trực tiếp.

Sensor / Location Runtime
        ↓
Motion / Location Event Candidate
        ↓
Emergency Event Manager or Application Event Router
        ↓
RecordingController command if recording action is required
Rules:

Rule

Description

MON-RULE-001

Sensor runtime có thể emit event candidates, không emit recording actions.

MON-RULE-002

Location runtime có thể provide metadata/tracking data, không control recording.

MON-RULE-003

Emergency Event Manager có thể convert sensor event thành emergency request.

MON-RULE-004

Chỉ RecordingController quyết định recording starts, marks important hoặc queues event.

MON-RULE-005

Sensor/location failure không được crash hoặc block core recording trừ khi policy explicitly requires.

## 5. Local Scope

Area

Direction

Motion Monitoring

Apply eligibility result vào motion sensor listener registration.

Fall / Impact Detection

Chỉ tạo event candidates khi monitoring algorithm và capability allow.

Location Tracking

Apply eligibility result vào GPS/location listener và tracking buffer.

Degraded Operation

Chạy lower-rate/partial monitoring chỉ khi degraded mode được approved.

Runtime Failure

Disable/degrade/prune affected module và log reason; không control recording trực tiếp.

## 6. Logging Direction

[MONITORING] Runtime eligibility consumed
[MONITORING] Sensor runtime initialized
[MONITORING] Sensor runtime pruned: <reason_code>
[LOCATION] Location runtime initialized
[LOCATION] Location runtime pruned: <reason_code>
[MONITORING] Event candidate emitted
Detailed sensitive logging rules thuộc **07 - Logging & Diagnostics Requirements**.

## 7. Conclusion

Sensor/location modules là optional runtime modules.
Android Operation initializes them through RuntimeModuleRegistry.
Device Capability quyết định chúng có thể run hay không.
Sensor/location events đi qua Emergency/Event routing.
RecordingController là component duy nhất được phép quyết định recording behavior.