# DCAM-30 — Build 0.1 Camera Recording, Image Capture and BDMA Sample Import

**Page ID**: 67436575  
**Version**: 2  
**Type**: page  
**URL**: https://ducviet.atlassian.net/wiki/spaces/DVID/pages/67436575

---


Item

Information

Project

DCAM

Document type

Engineering Evidence Summary (Build 0.1)

Status

**Draft**

Document version

1.0

Approval scope

Observed DCAM-30 Build 0.1 evidence only; does not change requirements, source code, test code, Device POC approval, or release readiness.

Owner

DCAM Engineering / QA

Technical reviewer

Tech Lead / QA

Approver

PM

Related Jira

[DCAM-30](https://ducviet.atlassian.net/browse/DCAM-30);

Related documents

[DCAM Requirement–Design–Test Traceability Matrix](/wiki/spaces/DVID/pages/51085669/DCAM+Requirement+Design+Test+Traceability+Matrix); [DCAM Engineering Evidence & NAS Artifact SOP](/wiki/spaces/DVID/pages/53608471/DCAM+Engineering+Evidence+NAS+Artifact+SOP)

Last updated

2026-08-20

Evidence cutoff

2026-08-20 11:23:39 +07:00

Artifact root

`Z:\2. DCAM\Artifacts\Build-0.1\Sprint-3\DCAM-30\20260819_001\`

## 1. Purpose and Use

This page is a private working execution summary for DCAM-30. It records observed Build 0.1 evidence, results, limitations, and artifact locations. The parent page remains authoritative for Device POC conclusion, traceability, Jira workflow, and release readiness.

## 2. Run Summary

Field

Value

Evidence ID

`EV-DCAM-30-20260819-001`

Run root

`Build-0.1\Sprint-3\DCAM-30\20260819_001`

Execution window

`2026-08-19T14:31:50.6205565+07:00 to 2026-08-19T15:11:38.8391837+07:00`

Device

Android BWC (k69v1_64_k419) / KF5OF2126040802193

Android/API

Android 12 / API 31

Configured video

`FHD / h264 / 1920x1088@30`

Build branch

`DCAM-5-Storage-Temp-/-Finalization`

Build commit

`23808bb0819b20dfee19580422021b48767ca8fb`

Procedure result

**Pass**

Overall result

**Pass for DCAM-30 evidence scope**

## 3. Evidence Result Matrix

All rows reflect the current DCAM-30 engineering report. Four concise columns keep Confluence rendering readable.

Test ID

Executed scope

Result

Evidence / key result

`QA-REC-001`

Normal recording

**Pass**

MP4 30.304 s; start 493 ms; finalize 598 ms; MD5 pass.

`QA-STO-001`

Temp to final artifact boundary

**Pass**

Staged Temp MP4 cleared after final MP4/MD5 publication, including recovery.

`QA-STO-002`

Final Media MP4 + matching MD5 BDMA_READY

**Pass**

File-only readiness pair in Media/Video; MD5 matches; no DB state/update.

`QA-IMG-001`

Final image capture

**Pass**

Final JPG captured under Media/Image; no JPG MD5 sidecar.

`QA-IMG-002`

Image folder / JPG MD5 subcondition

**Pass**

JPG is in Media/Image and no JPG MD5 sidecar exists.

`QA-IMG-003`

Image capture during active video

**Pass**

Accepted command to final JPG: 475 ms <= 1500 ms; video stayed active.

`QA-IMG-004`

Permission revoked and Camera unavailable

**Pass**

Permission recovery restored camera readiness; native system screenshot excluded. AppOp recovery notice observed; DCAM stayed alive; preview restored.

`QA-PERF-003`

Low-space automatic stop finalization

**Pass**

Critical stop finalized in 404 ms <= 5 s; final Media MP4/MD5 pair exists.

`QA-PERF-007`

Recording low-space precheck

**Pass**

Start rejected below 500 MiB; no final or Temp video artifact.

`QA-PERF-008`

Low-space active recording handling

**Pass**

Recording stopped; final MP4/MD5 published; scenario Temp empty.

`QA-BDMA-001`

Finalized MP4 MD5 and image/MP4 import

**Pass**

Three JPG and three MP4 syncs observed; MP4 hashes match source and destination.

`QA-BDMA-002`

Missing MP4 MD5 guard

**Pass**

Import denied; source remained on device.

`QA-BDMA-003`

MP4 MD5 mismatch handling

**Pass**

Mismatch rejected; retry ceiling and Temp cleanup observed.

`QA-BDMA-004`

Final image without MD5

**Pass**

Finalized image follows observed image contract.

`QA-BDMA-005`

Temp scan exclusion and cleanup

**Pass**

Media inside Temp folder was ignored during sync.

`QA-BDMA-006`

Disconnect during media pull

**Pass**

Fourth pull failed after retries; all four source files preserved; failed transfer not counted as sync.

`QA-BDMA-007`

Missing generated MD5 blocks BDMA_READY/import

**Pass**

Observable no-MD5 guard passes.

## 4. Key Evidence Observations

**Recording:** normal MP4 duration 30.304 s; recovery MP4 duration 13.176 s; both decode fully and match MD5 sidecars.

**Image during recording:** accepted PHOTO_CAPTURE to final JPG publication measured 475 ms against the 1500 ms limit; video remained active.

**Critical low-space:** automatic stop finalized the recording in 404 ms below the 500 MiB floor; final Media MP4 plus matching MD5 sidecar defines readiness evidence.

**BDMA sync:** three JPGs and three MP4s synced successfully in completed runs; MP4 MD5 values matched source and destination.

**MD5 and Temp handling:** missing-sidecar and mismatch guards behaved as expected; source Temp candidate was ignored; transfer Temp files were cleaned.

**Disconnect recovery:** the failed pull did not remove source media; all four candidate source files remained after reconnect, and the failed transfer is not counted as successful sync.

**BDMA_READY:** final image or MP4 plus matching MD5 sidecar under Media; no persisted state, timestamp, DB row, or DB update is required.

## 5. Evidence Package

Artifact

Path

Engineering report

`Z:\2. DCAM\Artifacts\Build-0.1\Sprint-3\DCAM-30\20260819_001\reports\DCAM-30-engineering-report.md`

Manifest

`Z:\2. DCAM\Artifacts\Build-0.1\Sprint-3\DCAM-30\20260819_001\manifest.md`

Checksum inventory

`Z:\2. DCAM\Artifacts\Build-0.1\Sprint-3\DCAM-30\20260819_001\artifact-checksums.md`

BDMA / MD5 validation

`Z:\2. DCAM\Artifacts\Build-0.1\Sprint-3\DCAM-30\20260819_001\adb\bdma-sync-md5-validation.txt`

QA-BDMA-006 app log

`Z:\2. DCAM\Artifacts\Build-0.1\Sprint-3\DCAM-30\20260819_001\logs\qa-bdma-006-disconnect-app.log`

QA-BDMA-006 source preservation

`Z:\2. DCAM\Artifacts\Build-0.1\Sprint-3\DCAM-30\20260819_001\adb\qa-bdma-006-source-preservation.txt`

QA-PERF-003 validation

`Z:\2. DCAM\Artifacts\Build-0.1\Sprint-3\DCAM-30\20260819_001\adb\qa-perf-003-low-space-finalization-validation.txt`

Media validation

`Z:\2. DCAM\Artifacts\Build-0.1\Sprint-3\DCAM-30\20260819_001\logs\media-validation.txt`

Media

`Z:\2. DCAM\Artifacts\Build-0.1\Sprint-3\DCAM-30\20260819_001\media`

Screenshots

`Z:\2. DCAM\Artifacts\Build-0.1\Sprint-3\DCAM-30\20260819_001\screenshots`

Build APK and metadata

`Z:\2. DCAM\Artifacts\Build-0.1\Sprint-3\DCAM-30\20260819_001\build`