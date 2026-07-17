# Engineering Evidence

Source: [DCAM Engineering Evidence & NAS Artifact SOP](https://ducviet.atlassian.net/wiki/spaces/DVID/pages/53608471), approved 1.0, Confluence page version 3, updated 2026-07-16.

## Purpose

- Store raw Build 0.1 technical evidence in the internal evidence repository.
- Cover APK/build packages, MP4/JPG media, logcat, ADB output, screenshots, reports, and run manifests.
- Keep raw artifacts out of Confluence, Jira, Slack, and cloud storage.

## Ownership

- NAS `DCAM-EVID-NAS-01` owns raw artifacts.
- Jira owns execution/evidence index: Jira key, Evidence ID, UNC path, status, and result.
- Confluence owns governance, SOP, metadata, conclusions, and evidence links.
- GitHub is not an authoritative evidence source until explicitly approved.

## Required layout

```text
\\<dvid-snt-server>\2. DCAM\Artifacts\
  _templates\
  Build-0.1\
    Sprint-2\
      DCAM-<Jira-key>\
        YYYYMMDD_<run-id>\
          manifest.md
          build\
          adb\
          logs\
          media\
            recordings\
            images\
          screenshots\
          reports\
  Archive\
```

Evidence ID format: `EV-DCAM-<Jira-key>-<YYYYMMDD>-<sequence>`.

## Manifest minimum

- Evidence ID and Jira key.
- Runner and execution time.
- Device, model, and firmware.
- Build or commit ID when available.
- Main ADB commands.
- `Pass` or `Fail` result.
- File list and checksums when applicable.
- Use `TBD` or `Not captured`; do not infer missing data.
- Never overwrite a reviewed run; create a new run ID for reruns.

## Current boundary

The SOP does not approve NAS readiness, Device POC pass, Build 0.1 readiness, or production release. Repository status becomes operational only after IT confirms stable UNC path, access group, backup/retention, and Tech Lead access.
