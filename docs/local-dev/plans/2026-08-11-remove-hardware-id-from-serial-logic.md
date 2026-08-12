# Remove Hardware ID from Serial Logic

Status: Complete

## Objective

Stop platform hardware ID from being used as `serialNumber` while preserving Room-backed serial persistence and separate DB hardware identity.

## Contract

### Must

- Keep Room → CSON → SD serial precedence, persistence, and rollback behavior.
- Keep `DeviceIdentityEntity.hardwareId` and existing DB hardware identity behavior unchanged.
- Remove platform serial from `DeviceInfo.serialNumber` and stop Room identity synchronization from copying it into `serialNumber`.
- Remove the detected-serial default UI path and `saveDefault` because they save platform hardware ID as serial.
- Preserve manual serial validation, restored-serial acceptance, and unrelated working-tree changes.
- Add or update focused regression coverage.

### Must not

- Remove Room from serial logic.
- Change DB schema, entity factories, hardware ID storage, or existing hardware ID values.
- Add `unknown` or another replacement hardware identifier.
- Reinterpret DB `hardwareId` storage as serial derivation.
- Weaken existing tests or touch unrelated hunks.

## Evidence

### Facts

- User correction: DB stores hardware ID separately; that is not the hardware-ID-to-serial bug and must remain untouched.
- User correction: `startup logger chỉ đọc config/SD, không Room` is invalid; startup must still read Room first.
- Original `FileDeviceSerialNumberStore` mirrors serial through Room, CSON, and SD and rolls all three back on failure.
- Original `FileDeviceSerialNumberStore` receives hardware ID only to populate the separate `DeviceIdentityEntity.hardwareId` field when creating the DB row.
- The actual hardware-to-serial paths are `DeviceInfo.serialNumber`, `AndroidDeviceRepositoryImpl` passing platform serial into it, `RoomDeviceIdentityRepositoryImpl` copying it into DB `serialNumber`, and detected-default/`saveDefault` UI.
- Current working tree correctly removes those hardware-to-serial paths but incorrectly removes Room from the serial store and startup logger.
- `DeviceIdentityEntity` is restored and unchanged.
- Runtime incident at 2026-08-11 15:48: device displayed the generic identity-restore failure dialog after corrected Room restoration.
- Previous ADB-absence note is invalidated: BodyCamera `adb-BODYCAMERA4HHITK-I4V7Ej._adb-tls-connect._tcp` and emulator `emulator-5554` attached successfully; physical verification completed on BodyCamera.
- Screenshot shows `CAM 133456`; `MainActivity.updateCameraIdentity()` renders that value only from `deviceSerialNumbers.load()`, proving CSON serial was already configured when the modal appeared.
- `ensureConfigFileAccess()` unconditionally converts every synchronization exception into `identityRestoreError`, so a mirror failure blocks startup even when usable identity already exists.
- The restore-failure action is labeled `Open settings` / `Mở cài đặt` but invokes retry, making the dialog additionally misleading.

- Validation evidence: manual save uses `[A-Z0-9]{6,10}`, while CSON `load()`, Room `databaseSerial()`, and SD backup parsing can accept values outside that contract.
- Existing test `acceptsRestoredSerialOutsideManualLengthLimit` explicitly preserves an invalid 11-character serial and is invalidated by the user correction.

- Backup precedence evidence: `DcamStorage.identityBackupFiles()` preserves `externalRoots` order, and `externalRoots` preserves Android `getExternalFilesDirs()` discovery order from index 1 onward.
- Conflict evidence: one production `IOException` branch and one regression test create the unsupported multi-backup conflict state.

### Hypotheses

- The prior low-level synchronization exception did not reproduce after installing the corrected APK with storage access granted; retain full exceptions in warning/error logs rather than guessing.

## User Corrections — 2026-08-11

- New correction: restore precedence is Room → CSON → SD; the first valid serial wins and synchronizes Room, CSON, and every SD backup. Multiple valid SD backups do not create a conflict state.
- New correction: manual entry and restore share the same 6-10 alphanumeric serial contract; backup parser `{1,64}` is invalid and must not bypass input validation.
- Invalid premise: detected serial and `saveDefault` could remain after hardware-derived serial removal.
- Invalid premise: removing hardware ID from serial allowed removing Room from serial logic.
- Invalid premise: DB hardware identity needed a new `unknown` factory or separate initializer.
- Corrected contract: preserve DB hardware identity and Room serial behavior exactly; remove only hardware-derived `serialNumber` inputs and UI/API paths.

## Phased Plan

- Backup precedence correction inspect: Completed. Supplier order is preserved; only one conflict branch and one conflict test require change.
- Backup precedence correction consider: Completed. First valid SD backup in supplied order wins; subsequent backups are mirrors, not competing sources.
- Backup precedence correction implement: Completed. Removed conflict exception; first valid SD backup in supplied order wins and is persisted to every mirror.
- Backup precedence correction confirm: Completed. Focused test, source scan, diff/encoding check, and DB hash verification passed.
- Validation correction inspect: Completed. Found bypasses in CSON load, Room restore, SD backup parser, `isConfigured`, and early Room logger loading.
- Validation correction consider: Completed. Selected one store-local normalizer plus existing use-case validation; no DB schema/entity/DAO changes.
- Validation correction implement: Completed. CSON, Room restore, SD backup, configured-state checks, and early Room logger loading now enforce `[A-Z0-9]{6,10}`.
- Validation correction confirm: Completed. Focused tests, full diff check, source audit, encoding scan, and DB hash verification passed.
- Inspect: Completed. Compared original/current store, startup, UI, domain mapping, Room mapping, tests, and documentation.
- Consider solutions: Completed. Selected restoration of original DB/Room sections while retaining only valid hardware-to-serial deletions.
- Implement: Completed. Restored Room store/startup/tests/docs; DB entity and hardware identity behavior remain unchanged.
- Confirm: Completed for JVM/build checks. Focused tests, full unit suite, debug assembly, source scans, diff checks, and encoding checks passed.
- Runtime reproduce: Completed with original screenshot/source evidence. After ADB became available, the prior nested exception did not reproduce with storage access granted.
- Runtime diagnose: Completed for user-visible failure. Configured CSON serial is incorrectly treated as missing identity after a mirror synchronization exception.
- Runtime implement: Completed. Configured CSON serial now keeps startup non-blocking while the synchronization exception remains logged; blocking dialog remains for missing serial only. Retry copy now matches retry behavior.
- Device confirm: Completed. Installed debug APK with `adb install -r`; MainActivity resumed, displayed configured `CAM 000005`, and showed no identity-restore dialog. Restored `MANAGE_EXTERNAL_STORAGE` app-op to its original `default` state after testing.

## Considered Solutions

- Config/SD-only serial flow: rejected because Room must remain.
- New entity factory or `unknown` placeholder: rejected because DB hardware identity is unrelated and must remain unchanged.
- Separate DB identity initializer/callback: rejected after user correction; unnecessary DB scope change.
- Restore original DB serial wiring and keep only hardware-to-serial deletions: selected.
- Suppress all restore exceptions: rejected because missing-identity failures must remain blocking and diagnosable.
- Continue when CSON serial is already configured, log synchronization failure, and block only with no serial: selected from screenshot/source evidence.

## Decisions

- `FileDeviceSerialNumberStore` keeps `CloudStateDao`, `DeviceIdentityEntity`, DB precedence, DB write, DB rollback, and existing hardware ID constructor behavior.
- `saveDefault` remains removed.
- `AppComposition` restores Room-backed store construction and Room-first startup logger loading, but `defaultDeviceSerial` remains removed.
- `DeviceInfo`, `AndroidDeviceRepositoryImpl`, and `RoomDeviceIdentityRepositoryImpl` retain current changes that remove platform serial mapping.
- `DeviceIdentityEntity` remains untouched.
- Expected correction files: `FileDeviceSerialNumberStore`, `AppComposition`, focused tests, `current-state.md`, and this journal.

- Restored serials must match the exact manual contract `[A-Z0-9]{6,10}` before use or mirroring; invalid Room/CSON/SD values are ignored so normal fallback order can continue.
- `DeviceIdentityEntity` and `CloudStateDao` remain untouched; validation belongs to serial loading, not DB storage.

## Changes Made

- Replaced the conflict regression with a precedence regression covering invalid-first, first-valid, later-different-valid, and synchronization of Room/CSON/all SD copies.
- Removed the unsupported SD-backup conflict policy: first valid backup in supplied order becomes the selected serial and later persistence synchronizes every backup.
- Added one store-local serial normalizer and applied it to CSON load, Room restore, and SD backup parsing.
- Changed `isConfigured` to require the same valid 6-10 uppercase alphanumeric format as manual save.
- Changed early Room logger loading to validate the Room serial before assigning it to `AppLogger`.
- Replaced the obsolete 11-character acceptance test and added Room/CSON/SD restore regressions for invalid lengths.
- Created this task journal.
- Removed detected-default UI, `saveDefault`, `DeviceInfo.serialNumber`, and hardware-to-serial Room identity copying.
- Incorrectly removed Room from serial store and startup loading under an invalid premise.
- Restored `DeviceIdentityEntity` after rejecting unrelated factory/copy changes.
- Restored original Room-backed store construction, DB-first restore/write/rollback, and Room-first startup logger loading.
- Restored DB regression tests and DB/SD mirror documentation while keeping `saveDefault` removed.
- Preserved original DB hardware ID field, constructor input, row creation, and existing-row behavior; no initializer/callback code was added.
- Changed `MainActivity` restore-error handling so configured CSON serial survives Room/CSON/SD mirror synchronization failure without a blocking modal; full exception remains logged as a warning.
- Corrected restore-failure message and changed the mismatched `Open settings` / `Mở cài đặt` action to `Retry` / `Thử lại`.
- Added a focused source regression test for the configured-serial non-blocking branch and retry labels.
- First focused rerun failed before tests because the local PowerShell edit helper duplicated MainActivityStartupFlowTest.java; production Java compilation passed. Repair is limited to removing the duplicate second copy.
- After duplicate repair, production and test compilation passed; one existing source test failed because it still searched the removed `identityRestoreError = error;` assignment. Update it to assert the configured-serial check and ternary error assignment.
- Updated the stale assertion; focused MainActivityStartupFlowTest rerun passed with --rerun-tasks.
- Full post-runtime-fix .\gradlew.bat test :app:assembleDebug --rerun-tasks passed: BUILD SUCCESSFUL; 47 tasks executed.
- Installed `app/build/outputs/apk/debug/app-debug.apk` on BodyCamera with `adb install -r`; no application data or Room database was cleared.

## Validation Results

- Post-precedence correction `./gradlew.bat :app:testDebugUnitTest --tests "com.dvid.dcam.platform.config.FileDeviceSerialNumberStoreTest" --rerun-tasks` passed: `BUILD SUCCESSFUL`; 29 tasks executed.
- Regression verified invalid first backup is skipped, first valid backup wins, a later different valid backup is overwritten, and Room/CSON/all SD copies converge on the selected serial.
- Source scan found no SD-backup conflict exception or conflict test; full validation paths still require `[A-Z0-9]{6,10}`.
- Earlier focused and full test passes apply only to invalid config/SD-only behavior and are not completion evidence.
- Latest combined validation compiled `:app:assembleDebug` but unit-test compilation failed at stale `databaseSerial` usage in `MainActivityStartupFlowTest`.
- Independent review confirmed production compile and `git diff --check` pass for the invalid intermediate state; those results do not validate corrected Room behavior.
- Corrected static checks pass: required Room paths present; forbidden detected/default/hardware-to-serial paths absent; path-scoped `git diff --check`, mojibake scan, and numeric-entity scan pass.
- Focused `:app:testDebugUnitTest` passed for `FileDeviceSerialNumberStoreTest`, `MainActivityStartupFlowTest`, and `DeviceSerialNumberUseCaseTest` with `--rerun-tasks`.
- Full `.\gradlew.bat test :app:assembleDebug --rerun-tasks` passed: `BUILD SUCCESSFUL`; 47 tasks executed.
- Final full `git diff --check` passed; `DeviceIdentityEntity` and `CloudStateDao` have no diff.
- Final production scan found no detected-default, `saveDefault`, `DeviceInfo.serialNumber`, or platform-hardware-to-serial mapping; required Room paths remain.
- Final focused rerun passed for `MainActivityStartupFlowTest`, `FileDeviceSerialNumberStoreTest`, and `DeviceSerialNumberUseCaseTest`: `BUILD SUCCESSFUL`.
- Physical UI check passed with storage access granted: MainActivity active, `CAM 000005` visible, no identity-restore modal, and `SERIAL_TRACE dialog-check ... savedPresent=true`.
- `DeviceIdentityEntity.java` and `CloudStateDao.java` working-tree hashes exactly match `HEAD`.
- Post-correction focused command `./gradlew.bat :app:testDebugUnitTest --tests "com.dvid.dcam.platform.config.FileDeviceSerialNumberStoreTest" --tests "com.dvid.dcam.feature.device.application.usecase.DeviceSerialNumberUseCaseTest" --tests "com.dvid.dcam.architecture.MainActivityStartupFlowTest" --rerun-tasks` passed: `BUILD SUCCESSFUL`; 29 tasks executed.
- Post-correction source audit found no `{1,64}`, `[A-Za-z0-9]`, obsolete long-restore comment, or 11-character acceptance test in active serial validation paths.
- CSON load, Room restore, SD backup parsing, configured-state checks, and early Room logger loading now use the confirmed `[A-Z0-9]{6,10}` contract.
- Final full `git diff --check` and UTF-8/mojibake scans passed after the validation correction.

## Completion Notes

- Required work: None.
- Restore contract is Room → CSON → SD; first valid value wins and synchronizes Room, CSON, and every SD backup.
- If no valid source exists, restore returns no serial and existing UI requests manual 6-10 character input; saving then synchronizes all mirrors.
- No backup conflict state remains. DB entity, DAO, hardware ID storage, schema, and unrelated working-tree changes remain untouched.