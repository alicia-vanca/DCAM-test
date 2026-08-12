# Device identity persistence real-device evidence — 2026-07-21

## Result
PASS for current scoped real-device identity persistence flow. Current APK passed clean uninstall/reinstall with removable SD volume `6162-6433`: default app-op opened All files access settings, and after grant the SD identity restored into Room and mandatory CSON without showing the serial input dialog. Fresh no-backup startup, manual save, and cancel-without-grant access gating also passed. Historical pre-fix failure remains documented in E4.

Do not treat Gradle tests or historical manually granted runs alone as proof. E8 and E9 below are current physical-device evidence. Test scope excludes changes to manual-input and select-default UI behavior. Manual test identity was `726726`. This report does not record any detected/default identity value.
## Device and builds
- Device model: `BWC`
- Android: `12` / API `31`
- Removable volume used by the identity test: `E3AE-18F6`
- Package: `com.dvid.dcam`
- Earlier APK SHA-256: `D7F43552A868AA2870D73A4DDA9A64369C7D7188499B15CAFEC6B008D129B419`
- Earlier startup-permission APK SHA-256: `23E4AF7F371BA2142C2A690C7ED24A4178D26C179726BF5A54592B0DCF52EB53` (before external-flow ordering fix)
- Current APK SHA-256: `2DD1879B70E9F460128125E23F52BACD6D2855CC6C241AA3A41301D984378E0E`
- Test date: `2026-07-21` (Asia/Saigon)
- Current post-fix device: `BodyCamera`, Android `12` / API `31`, removable volume `6162-6433`.

## Storage contract
- Room table: `device_identity`, singleton row `id=1`.
- Mandatory BDMA CSON: `/storage/emulated/0/Android/data/com.dvid.dcam/files/Config/dcam_config.cson`.
- Removable backup: `/storage/E3AE-18F6/DCAM_FACTORY/device_identity.json`.
- Save writes DB, CSON, and removable backup as one synchronized operation with rollback on failure.
- `provisioningState=PROVISIONING_REQUIRED` is expected for local identity and does not block BDMA CSON reading.

## Confirmed evidence
### E1 — Fresh install without backup
At first launch:
- `device_identity` had no row.
- CSON existed with `serial_number=""`.
- Removable `device_identity.json` was absent.
- Identity dialog was visible and save button was disabled until valid manual input.
- No identity was silently persisted before user action.

### E2 — Manual input save
After entering and saving `726726`:
- Dialog dismissed only after save completed.
- Log markers: `SERIAL_TRACE save-request` at `17:28:09.044`; `SERIAL_TRACE save-success` at `17:28:09.327`.
- DB row contained `id=1`, `serialNumber=726726`, `provisioningState=PROVISIONING_REQUIRED`.
- Mandatory CSON contained `serial_number="726726"`.
- SD JSON contained `schema_version=1`, `identity_type=BODYCAMERA_SD_FACTORY_IDENTITY`, and `serial_number=726726`.
- No `.tmp` file remained in CSON or backup directories.

### E3 — Restore with manually granted app-op
Earlier uninstall/reinstall test used this explicit step before launch:

```powershell
adb shell appops set --uid com.dvid.dcam MANAGE_EXTERNAL_STORAGE allow
```

Observed after that manual grant:
- SD JSON remained after uninstall.
- `SERIAL_TRACE precheck-complete restored=true` at `17:41:04.343`.
- `SERIAL_TRACE restore-success` at `17:41:04.743`.
- DB, mandatory CSON, and SD JSON contained `726726`.
- Identity dialog was absent.
- Screenshot: [manually granted reinstall result](assets/device-identity-2026-07-21/identity-reinstall-backup.png).

This proves restore logic after access is available. It does not prove plain uninstall/reinstall because uninstall resets special access.

### E4 — Pre-fix plain uninstall/reinstall failure
Precondition after uninstall/reinstall:
- SD JSON still existed and contained `726726`.
- DB identity row was absent.
- Mandatory CSON was blank.
- `MANAGE_EXTERNAL_STORAGE: default`.

Observed log:

```text
SERIAL_TRACE restore-failed
java.lang.IllegalStateException: DCAM SD identity backup requires all-files access
```

Stack reached:

```text
DcamStorage.identityBackupFiles(DcamStorage.java:310)
FileDeviceSerialNumberStore.loadBackupSerial(FileDeviceSerialNumberStore.java:190)
FileDeviceSerialNumberStore.restoreIfAvailable(FileDeviceSerialNumberStore.java:76)
MainActivity.java:379
```

Result: identity was not restored and serial input was requested. This is the failure the startup permission change addresses.

### E5 — New startup permission gate before grant
Installed startup-permission APK with app-op still `default`.

Observed:
- Android opened `com.android.settings/.Settings$AppManageExternalStorageActivity` for DCAM.
- All files access switch was off.
- App log contained `STORAGE_TRACE all-files-access-required`.
- No `SERIAL_TRACE restore-failed` occurred before grant.
- DB remained empty and mandatory CSON remained blank.
- Serial input dialog did not appear before storage access was granted.
- Screenshot: [startup access requirement](assets/device-identity-2026-07-21/all-files-access-startup-required.png).

This proves startup now gates identity I/O on All files access. It does not yet prove post-grant restore.

### E6 — External-flow ordering regression
A clean BWC run with app-op `MANAGE_EXTERNAL_STORAGE: default` produced concrete competing-activity evidence:

```text
START ... com.android.permissioncontroller/.role.ui.RequestRoleActivity
STORAGE_TRACE all-files-access-required
START ... com.android.settings/.Settings$AppManageExternalStorageActivity
```

The activity stack contained both `RequestRoleActivity` and `AppManageExternalStorageActivity` above `MainActivity`. This proved default-home flow could launch before the required storage gate and stack external results. Source fix moves `requestDefaultHomeIfNeeded()` after `ensureAllFilesAccess()` in current APK. Device went offline before current APK rerun, so this source fix lacks post-fix physical confirmation.

### E7 — BDMA-style ADB read after granted restore
ADB pulled the mandatory CSON path after the earlier granted restore:

```text
adb pull /storage/emulated/0/Android/data/com.dvid.dcam/files/Config/dcam_config.cson
```

- Pulled size: `56` bytes.
- Device SHA-256: `821D35CDAB02AF1C6BE648BF4566DAF123268D7E1C695B65A986D3B01971F588`.
- Pulled SHA-256: `821D35CDAB02AF1C6BE648BF4566DAF123268D7E1C695B65A986D3B01971F588`.
- Pulled content:

```cson
[device]
device.name="BodyCamera"
serial_number="726726"
```

This proves the required Android path is readable through ADB after restore. It is transport-level evidence, not a joint BDMA binary import test.

### E8 — Current APK clean uninstall/reinstall with physical SD restore
Test device: `BodyCamera`, Android `12` / API `31`, removable volume `6162-6433`, selected through ADB `transport_id=1`.

Preconditions and actions:
1. Saved identity `726726` through current APK. SD backup remained at `/storage/6162-6433/DCAM_FACTORY/device_identity.json`.
2. Uninstalled `com.dvid.dcam`; backup JSON remained on removable SD.
3. Installed current APK SHA-256 `2DD1879B70E9F460128125E23F52BACD6D2855CC6C241AA3A41301D984378E0E` with `MANAGE_EXTERNAL_STORAGE: default`.
4. Confirmed runtime permissions, observed startup Settings screen, enabled All files access, and returned to DCAM.

Observed:
- Before grant: `STORAGE_TRACE all-files-access-required`; focus was `com.android.settings/.Settings$AppManageExternalStorageActivity`; no `RequestRoleActivity` appeared.
- After grant: `MANAGE_EXTERNAL_STORAGE: allow`.
- Restore logs: `SERIAL_TRACE precheck-complete restored=true` at `19:26:44.689`; `SERIAL_TRACE restore-success` at `19:26:44.704`.
- Room query result: `1|726726|PROVISIONING_REQUIRED` from `device_identity`.
- Mandatory CSON read through ADB:

```text
[device]
device.name="BodyCamera"
serial_number="726726"
```

- CSON path: `/storage/emulated/0/Android/data/com.dvid.dcam/files/Config/dcam_config.cson`; size `56`; SHA-256 `821D35CDAB02AF1C6BE648BF4566DAF123268D7E1C695B65A986D3B01971F588`.
- SD JSON path: `/storage/6162-6433/DCAM_FACTORY/device_identity.json`; size `220`; SHA-256 `2890D6637AF8844AD355F8DBD6317B76ADE0B19EDDE387E9A17D64DF580E0F88`.
- SD JSON contained `schema_version=1`, `identity_type=BODYCAMERA_SD_FACTORY_IDENTITY`, and `serial_number=726726`.
- No `.tmp` file remained in either identity directory.
- MainActivity resumed after restore; no post-restore `dialog-shown` marker occurred.

### E9 — Return from All files settings without grant
With identity already restored, set `MANAGE_EXTERNAL_STORAGE` to `default`, started DCAM, and pressed Back from the app-specific All files access screen.

Observed:
- DCAM resumed directly; no competing role screen appeared.
- Noncancelable dialog title: `Cần quyền truy cập bộ nhớ`.
- Dialog message: `DCAM cần quyền Truy cập tất cả tệp để đọc và khôi phục bản sao nhận diện thiết bị từ SD.`
- Only action: `MỞ CÀI ĐẶT`.
- Serial identity dialog did not appear.
- Test cleanup restored `MANAGE_EXTERNAL_STORAGE: allow` and stopped DCAM.
## Pending physical checks
Completed on current APK with physical removable SD:
1. Default app-op opened All files access settings before identity I/O.
2. No competing role screen appeared in current startup flow.
3. Returning without grant showed the noncancelable storage-access dialog and kept serial input hidden.
4. Granting All files access restored the SD identity into Room and mandatory CSON without user input.
5. DB, CSON, and SD JSON matched `726726`; no temporary files remained.
## Source and regression proof
- `app/src/main/java/com/dvid/dcam/app/MainActivity.java`: startup access gate, settings result handling, deferred identity UI, async identity I/O.
- `app/src/main/java/com/dvid/dcam/platform/permission/DcamPermissions.java`: API-aware special/runtime storage access check.
- `app/src/main/AndroidManifest.xml`: legacy storage compatibility declaration for API 26–29.
- `app/src/main/java/com/dvid/dcam/platform/config/FileDeviceSerialNumberStore.java`: synchronized DB/CSON/SD persistence and rollback.
- `app/src/test/java/com/dvid/dcam/architecture/MainActivityStartupFlowTest.java`: startup ordering and access-gate source regression checks.
- `app/src/main/java/com/dvid/dcam/platform/database/entities/DeviceIdentityEntity.java`: singleton identity schema.

## Validation status
Completed:

```powershell
.\gradlew.bat --no-daemon --max-workers=1 :app:testDebugUnitTest
.\gradlew.bat --no-daemon --max-workers=1 :app:assembleDebug
```

Both commands passed for current APK SHA-256 `2DD1879B70E9F460128125E23F52BACD6D2855CC6C241AA3A41301D984378E0E`. Physical-device E8 and E9 passed on `BodyCamera` / Android `12` / API `31` with removable volume `6162-6433`.

## Limits
- Actual BDMA binary import remains outside this test scope.
- Cloud provisioning was not exercised; `PROVISIONING_REQUIRED` remains expected local state.