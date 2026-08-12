# Historical App POC Capability Assessment

> **Historical snapshot.** This report describes the repository on July 10, 2026. Use current source and `app/src/main/java/com/dvid/dcam/ARCHITECTURE.md` for present behavior.

**Date:** 2026-07-10  
**Purpose:** Can current DCAM code pass the Sprint 0 POC tests?

---

## Executive Summary

**Overall readiness:** ❌ **NOT READY** for most POC tests.

**Why:** Recording is Activity-owned, no recovery, no staging, FGS doesn't own camera.

**What will happen:** Tests will expose critical architecture gaps (which is the point of POC).

---

## Test-by-Test Assessment

### Test Group 1: Camera API Behavior

| Test | Current Code Capability | Expected Result | Can Test? |
|---|---|---|---|
| **1.1 Basic recording** | ✅ CameraX recording works | ✅ Pass | ✅ YES |
| **1.2 Screen off** | ⚠️ CameraX lifecycle-bound to Activity | ❓ Unknown - depends on Android/vendor | ✅ YES |
| **1.3 Activity recreate** | ❌ `activeRecording` field in gateway, lost on recreate | ❌ **FAIL** - recording stops or duplicate | ✅ YES |
| **1.4 Process kill** | ❌ No recovery, Activity-bound lifecycle | ❌ **FAIL** - recording lost, partial file | ✅ YES |
| **1.5 Multiple recordings** | ✅ Start/stop implemented | ✅ Should pass | ✅ YES |
| **1.6 Callback timing** | ✅ CameraX callbacks exist | ✅ Can observe | ✅ YES |

**Key code evidence:**

```java
// AppComposition.java line ~186
public CaptureRuntime createCaptureRuntime(ComponentActivity owner) {
    CameraXCameraGatewayImpl camera = new CameraXCameraGatewayImpl(
            owner, owner, config, mediaOutput, logger, captureEvents,
            mediaEncryptionSettings, operatorSession, cameraPreview);
    //     ^^^^^ LifecycleOwner = Activity
```

```java
// CameraXCameraGatewayImpl.java
private Recording activeRecording;  // ← Lost when Activity dies
```

**Conclusion:** Tests 1.3 and 1.4 will **fail**, proving we need FGS ownership (ADR-004).

---

### Test Group 2: Foreground Service Behavior

| Test | Current Code Capability | Expected Result | Can Test? |
|---|---|---|---|
| **2.1 FGS notification** | ✅ Notification works | ✅ Pass | ✅ YES |
| **2.2 Activity finish** | ❌ Camera bound to Activity lifecycle | ❌ **FAIL** - recording stops | ✅ YES |
| **2.3 Process kill** | ❌ `START_NOT_STICKY`, no restart | ❌ **FAIL** - service dies, no recovery | ✅ YES |
| **2.4 Service restart** | ❌ `START_NOT_STICKY` | ❌ Won't restart | ✅ YES |
| **2.5 Recording ownership** | ❌ Activity-owned | ❌ **FAIL** - lost on Activity death | ✅ YES |

**Key code evidence:**

```java
// RecordingForegroundService.java line ~72
@Override public int onStartCommand(Intent intent, int flags, int startId) {
    // ... show notification ...
    return START_NOT_STICKY;  // ← No restart after kill
}
```

Service comment line ~8:
```java
/**
 * Keeps an active recording visible to Android and reduces background process risk.
 * Camera ownership remains in the CameraX adapter until recovery design is approved.
 *                            ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
 */
```

**Conclusion:** All ownership tests will **fail**, proving we need to move recording to service (ADR-004).

---

### Test Group 3: Storage Roots and ADB Visibility

| Test | Current Code Capability | Expected Result | Can Test? |
|---|---|---|---|
| **3.1 Internal path discovery** | ✅ App writes to configured mode | ✅ Can find path | ✅ YES |
| **3.2 External path discovery** | ⚠️ External mode exists, untested | ❓ Unknown on real device | ✅ YES |
| **3.3.1 Move atomicity** | ❌ No Temp staging - writes directly to final path | ❌ **N/A** - no move operation | ⚠️ NO |
| **3.3.2 Copy+fsync** | ❌ No staging/finalization | ❌ **N/A** - no copy operation | ⚠️ NO |
| **3.3.3 Free space** | ✅ `DeviceRepository.readStatus()` reports storage | ✅ Can compare | ✅ YES |
| **3.3.4 Storage removed** | ⚠️ No error handling for storage removal | ❓ Unknown - likely crash | ✅ YES |

**Key code evidence:**

```java
// CameraXCameraGatewayImpl.java - recording start
DcamMediaFile output = mediaOutput.prepareVideoFile(mode, operatorId);
//                                  ^^^^^^^^^^^^^^^
// Goes straight to final Media/* path, not Temp
```

From `current-state.md`:
> Camera/audio currently target final media paths directly; no durable finalization state

**Conclusion:** Can test path discovery and free space. Can't test move/copy atomicity because it doesn't exist yet (Sprint 2 work).

---

### Test Group 4: Screen/Power Policy

| Test | Current Code Capability | Expected Result | Can Test? |
|---|---|---|---|
| **4.1 Screen timeout** | ⚠️ No WakeLock, depends on CameraX default | ❓ Unknown | ✅ YES |
| **4.2 Power button** | ⚠️ Same as 4.1 | ❓ Unknown | ✅ YES |
| **4.3 Battery optimization** | ⚠️ No explicit exemption request | ❓ Unknown impact | ✅ YES |

**Key code:** No WakeLock or power policy code found.

**Conclusion:** Can observe behavior, document what happens, decide if WakeLock needed.

---

### Test Group 5: Device Owner / Lock Task

| Test | Current Code Capability | Expected Result | Can Test? |
|---|---|---|---|
| **5.1 Device Owner** | ⚠️ No `DeviceAdminReceiver` in manifest | ❌ Can't provision yet | ⚠️ BLOCKED |
| **5.2 Lock Task Mode** | ⚠️ No Lock Task API calls | ❌ Not implemented | ⚠️ BLOCKED |
| **5.3 User Restrictions** | ⚠️ No DPM policy code | ❌ Not implemented | ⚠️ BLOCKED |

**Key code:** Search reveals no `DevicePolicyManager` or `DeviceAdminReceiver` in current code.

**Conclusion:** Kiosk features not implemented. Can test if hardware supports provisioning, but app code doesn't use it yet.

---

## Summary Table: Can We Test?

| Test Group | Tests That Work | Tests That Will Fail | Tests Blocked |
|---|---|---|---|
| Camera | 1.1, 1.2, 1.5, 1.6 | **1.3, 1.4** | None |
| FGS | 2.1 | **2.2, 2.3, 2.4, 2.5** | None |
| Storage | 3.1, 3.2, 3.3.3, 3.3.4 | N/A | 3.3.1, 3.3.2 (no staging) |
| Power | All observable | Unknown results | None |
| Device Owner | N/A | N/A | All (not implemented) |

**Legend:**
- ✅ Works = Current code should pass
- ❌ Will Fail = Current code will expose gap (GOOD - this is the point!)
- ⚠️ Blocked = Feature not implemented, can't test yet

---

## What POC Will Prove

### Critical Failures (expected)
1. **Activity death kills recording** (Test 1.3, 2.2) → proves need for FGS ownership
2. **Process kill loses state** (Test 1.4, 2.3) → proves need for recovery + persisted state
3. **No restart after kill** (Test 2.4) → proves need for `START_STICKY` or recovery

### Hardware Evidence (unknown until tested)
1. **CameraX works on this device?** (Test 1.1)
2. **Screen-off recording?** (Test 1.2, 4.1, 4.2)
3. **Physical storage paths?** (Test 3.1, 3.2)
4. **Device Owner support?** (Test 5.1)

### Can't Test Yet (waiting for Sprint 2)
1. Staging/finalization (no Temp usage)
2. Recovery reconciliation (no recovery logic)
3. Lock Task/kiosk (no DPM code)

---

## Recommendations

### 1. Run POC Now (Even Though Some Will Fail)

**Why:** Failures prove the gaps are real. Evidence for ADRs.

**Focus on:**
- Test 1.1–1.6: Camera API behavior
- Test 2.1–2.5: FGS limitations
- Test 3.1–3.2: Storage paths
- Test 4.1–4.3: Power behavior

**Skip for now:**
- Test 3.3.1–3.3.2: Staging (wait for Sprint 2)
- Test 5: Device Owner (wait for kiosk implementation)

### 2. Expected POC Report

```markdown
## Camera (ADR-002)
- ✅ 1.1 Basic: PASS
- ❓ 1.2 Screen-off: [document what happens]
- ❌ 1.3 Activity recreate: FAIL - recording lost
- ❌ 1.4 Process kill: FAIL - partial file, no recovery
- ✅ 1.5 Multiple: PASS
- ✅ 1.6 Callbacks: Works

**Decision:** CameraX works for basic recording. 
CRITICAL: Must move ownership to FGS (ADR-004) before Sprint 2.

## FGS (ADR-004)
- ✅ 2.1 Notification: PASS
- ❌ 2.2–2.5: All FAIL - Activity-bound lifecycle

**Decision:** Current FGS is notification-only. 
REQUIRED: Service must own CameraX and persist state.
REQUIRED: Change to START_STICKY or implement recovery.
```

### 3. ADR Impact

| ADR | What POC Proves |
|---|---|
| ADR-002 (Camera) | CameraX works / doesn't work on device X |
| ADR-004 (FGS) | Current START_NOT_STICKY + Activity ownership = data loss |
| ADR-005 (Storage) | Physical paths for Internal/External modes |
| ADR-006 (Encryption) | (Not hardware-dependent, policy decision) |

---

## Bottom Line

**Can we run POC?** ✅ YES - 75% of tests are runnable.

**Will tests pass?** ❌ NO - 40% will fail (Activity/FGS tests).

**Is that OK?** ✅ YES - **failures prove the gaps are real**, give evidence for ADRs.

**What to do:** Run POC this week, document failures, write ADRs with evidence.

---

**POC is not about passing all tests. POC is about learning device behavior and proving architecture gaps with evidence.**
