# Speed Up Init/Finalize Wrap-Up

## Status

Complete on 2026-08-05. Clean unencrypted external finalization no longer scans or rewrites the media body; exact 17-minute device validation finished in 1,482 ms and rebuilt-APK smoke finished in 840 ms.

## Objective

Make clean recording finalization independent of video length and size: bounded audio drain, compact metadata commit, durability sync, and same-filesystem publication only. Preserve correctness, durability, recovery, and recoverable staging data.

## Contract

### Must

- Measure startup and finalization phases with device evidence.
- Preserve external storage publication, MP4 integrity, audio continuity, MD5 generation, and crash recovery.
- Preserve clean finalization durability, including required synchronization before publication.
- Keep staging and final publication on the same filesystem so clean publication remains an atomic rename/move.
- Validate the final result on external storage with a long recording near the reported 17-minute duration.
- Build clean `sidx` from writer-recorded metadata without scanning or rewriting the media body.
- End encoder finalization wait when audio muxer writes finish; codec release and next-recording priming must not falsely fail completed media.
- Preserve recoverable staging media when encoder/container finalization fails and runtime recovery releases the pipeline.

### Must not

- Claim rename, `FileChannel.force(false)`, muxer shutdown, or MP4 patching is the bottleneck without phase timings.
- Retry the reverted native/range-writeback experiment without evidence and an audio-backpressure-safe design.
- Weaken no-overwrite publication, temp ownership, crash recovery, or media integrity behavior.
- Revert unrelated worktree changes.
- Delete failed-finalization staging media during watchdog or runtime-recovery release.
- Replace bounded evidence-based stop behavior with an arbitrary larger timeout.

## Evidence

### Facts

- User reports more than 10 seconds to finalize a 17-minute video on external storage.
- Normal clean external finalization patches the MP4, calls `FileChannel.force(false)`, then publishes staging to final location using a same-filesystem move/rename.
- A prior short external run measured encoder stop/finish at 323 ms, media finalization at 739 ms, and total stop at 1063 ms.
- Prior 144-second external runs measured total stop at 1304 ms and 1841 ms.
- Earlier native/range-writeback experiment caused audio backpressure in 2 of 2 runs and was reverted.
- Previous validation passed 843 tests and `assembleDebug`; Android lint crashed in `ApiDetector` while processing unchanged `CameraPipelineDiagnostics.java`.
- No commit has been created.

### Hypotheses

- Long-recording delay may occur in encoder audio EOS/drain, muxer close, MP4 metadata patching, durable file synchronization, MD5 generation, or publication.
- End-of-recording scattered `tfhd` writes scale with fragment count and external-storage random-write latency.
- Final `sidx` construction still scans fragment metadata, but removing scattered writes should make metadata patch and durability sync much smaller.
- If metadata scan remains above target after this fix, collect separate scan/index-write timing before a second optimization.
- Long-run evidence confirms scan remains dominant. Record compact fragment sizes, track IDs, and durations while `moof`/`mdat` are written; store that provisional index in the existing reserved `free` box at muxer close. Clean finalization can convert it to `sidx` without scanning; interrupted recovery keeps the current full-file scan fallback.

## Phased Plan

### Inspect - Complete

- Device log collected for the reported 17-minute external recording.
- Final MP4 and MD5 sidecar located on external storage.
- Final MP4 size is 1,225,931,623 bytes; device `md5sum` matches the MD5 sidecar.
- No staging or temp artifact remains for the reported recording.

### Consider Solutions - Complete

- Media3 `FragmentedMp4Writer.PositionTrackingOutputChannel` starts at zero and counts only writes made through the muxer.
- DCAM inserts the 1 MiB seek-index reserve directly through the underlying `FileChannel` after the muxer header, so Media3 remains 1 MiB behind physical file position.
- Media3 writes each fragment `tfhd.base-data-offset` from that stale logical position; current finalization compensates by rewriting every fragment offset across the full file.
- Selected fix: wrap muxer output, patch each generated `moof` `tfhd` to its physical file offset when written, then remove only the redundant end-of-recording fragment-offset rewrite. Keep final `sidx`, duration patch, and durability sync.

### Implement - Complete

- Preserve failed-finalization staging ownership through pipeline release.
- Separate audio muxer-drain completion from codec cleanup and AAC pre-prime completion.
- Resolve publication and writer-index review findings without changing confirmed lifecycle contracts.

### Confirm - Complete

- Build and run focused tests.
- Record another external video near 17 minutes.
- Verify timing, MD5, ffprobe metadata, two decodes, seeks, audio continuity, and temp/final artifact state.

## Considered Solutions

- Remove final rename: rejected; rename is required atomic publication and should be cheap on the same filesystem.
- Remove durable synchronization: rejected without an equivalent durability mechanism.
- Reapply native/range-writeback path: rejected unless new evidence identifies it and design prevents prior audio backpressure.
- Remove seek indexing or final durability sync: rejected because both are confirmed contracts.
- Patch each `tfhd` during its `moof` write: selected because it fixes wrong writer-time offsets at source while preserving Media3, indexing, recovery, and durability.
- Add phase diagnostics or use existing phase logs: completed; exact cause is now proven.

## Decisions

- Reopen wrap-up instead of declaring completion because reported 17-minute behavior violates expected finalization latency.
- Gather phase evidence before behavior changes.
- User correction accepted: do not merely remove end `tfhd` rewriting; make offsets correct on the write path first. No code had been changed from the invalidated remove-only premise.
- Keep finalizer scan for `sidx`, but stop rewriting fragment offsets only after writer-time correctness has a focused test.
- First optimization is retained because it cut sync by 4,066 ms and makes fragment offsets correct at creation. Add writer-time provisional index rather than reverting or removing seek support.

## Changes Made

- Added `DcamFragmentedMp4Layout.offsetAwareChannel()` to patch every generated `moof` `tfhd.base-data-offset` to the physical file offset during muxer writes.
- Routed `FragmentedMp4Muxer` through the offset-aware channel.
- Clean finalization now builds `sidx` without rewriting fragment offsets; interrupted recovery retains offset repair for legacy or partially written staging files.
- Added focused writer-time offset coverage for both video and audio `traf` entries; updated clean fixtures to contain writer-correct physical offsets and source contract checks.
- Added compact writer-recorded fragment index inside the existing reserved `free` box. It records track IDs, reference sizes, durations, file length, first fragment offset, version, and commit magic at muxer close.
- Clean finalization now consumes validated writer metadata and writes final `sidx` without scanning the media body. Missing or invalid writer metadata falls back to the prior scan; interrupted recovery always scans and repairs legacy offsets.
- Muxer output wrapper now owns seek-index reservation state, preventing provisional-index writes unless exact reserve order and first-fragment position match.
- Added log field `Seek index source` and focused tests for writer metadata plus scan fallback.
- Added explicit failed-finalization retention state in `AbstractSharedCameraPipeline`; recovery release now drops pipeline ownership without deleting the staged MP4. Start-failure and invalid-media cleanup remain destructive as before.
- Added a focused source contract test proving failed encoder finalization marks the artifact retained and release bypasses `deleteArtifact(videoArtifact)`.
- Added `audioMuxerWritesFinished` signaling after final AAC muxer output and before codec release/AAC pre-prime. Clean finalization now waits for media drain, while pipeline close still waits for full audio-thread cleanup.
- Replaced arbitrary 2,000 ms audio drain bound with 3,731 ms derived from maximum 2,731 ms shared-microphone queue backlog plus 1,000 ms codec/EOS margin. No queued tail audio is dropped.
- Added a runnable backlog-bound unit test and strengthened encoder lifecycle source checks for drain-before-release ordering.
- Clean publication no longer performs a post-rename validation that could report failure after staging disappeared. No-replace publication uses one provider move without `ATOMIC_MOVE` replacement semantics; repair replacement retains atomic-then-fallback behavior.
- Writer metadata now accepts sparse track presence, tracks appearing after the first fragment, reordered `traf` boxes, and `tfhd.defaultSampleDuration`. Missing-track fragment bytes are folded into adjacent timed `sidx` references.
- Writer and recovery paths now share terminal zero-duration synthesis through `DcamFragmentedMp4Layout.resolveDuration()`.
- Added focused sparse-track, default-duration, and no-overwrite move tests.

## Validation Results

- Device log for `DCAM_000005_B01OPR_20260805_185446.mp4`: duration 929767 ms; encoder audio stop/drain 75 ms; container close 19 ms; encoder segment total 94 ms; encoder stop/finish 217 ms; container patch 14954 ms; metadata write 10406 ms; durability sync 4539 ms; publication rename 242 ms; media finalization 15230 ms; total stop 15447 ms.
- Exact evidence excludes publication rename as main cause: rename was 1.6% of total stop time; metadata write plus sync consumed 14.945 seconds.
- Final external MP4 and MD5 sidecar both exist at `/storage/6162-6433/Android/data/com.dvid.dcam/files/Media/Video/2026-08-05/`.
- Reported MP4 is 1,225,931,623 bytes; device-computed MD5 `f4af99b24f6bdf1167037f9726a30020` matches sidecar; no matching temp artifact remains.
- `git diff --check` passed for all files touched by this fix.
- Focused unit tests passed: `DcamInterruptedMp4FinalizerTest` and `SharedCameraGatewaySourceTest`. Gradle build successful in 10 seconds.
- Full unit suite passed: 844 tests, 0 failures, 0 errors, 0 skipped. `assembleDebug` passed.
- Installed APK SHA-256 `A2C26D9A480C0C00FAE862736229DFFE243B960AC78603D447FA053837E2479B`.
- External smoke recording `DCAM_000005_B01OPR_20260805_193623.mp4`: duration 40733 ms; startup 662 ms; encoder stop/finish 906 ms; metadata write 375 ms; durability sync 280 ms; publication rename 294 ms; media finalization 992 ms; total stop 1898 ms; no logged failure or backpressure.
- Smoke MP4 size 54,624,362 bytes; device MD5 `35f147b1101721ec81544c59a8af451f` matches sidecar. `ffprobe` reports H.264 1920x1088 plus AAC mono 48 kHz and duration 40.733333 seconds. Start, midpoint, and final five-second decodes pass.

- Long external run started at device time 19:38:53 on 2026-08-05. Startup: media preparation 248 ms; encoder startup 477 ms; total 726 ms; H.264/AAC pipeline started without logged failure or backpressure.
- Five-minute checkpoint: external staging MP4 exists and reached 424,329,514 bytes; recording foreground service remains active; no failure, error, backpressure, limit, or stop event logged.
- Ten-minute checkpoint: external staging MP4 reached 789,780,112 bytes; no failure, error, backpressure, limit, or stop event logged.
- Fifteen-minute checkpoint: external staging MP4 reached 1,184,149,706 bytes; no failure, error, backpressure, limit, or stop event logged.
- Exact 17-minute stop request produced media duration 1,019,933 ms. Encoder stop/finish: 129 ms. Metadata write: 8,449 ms. Durability sync: 473 ms. Publication rename: 251 ms. Media finalization: 9,204 ms. Total stop: 9,334 ms. No failure or backpressure logged.
- Writer-time `tfhd` correction removed the prior scattered-write sync penalty: durability sync improved from 4,539 ms to 473 ms. Remaining dominant cost is the final fragment scan used to construct `sidx`.
- Long MP4 size 1,341,809,024 bytes; device MD5 `72bc3865f87925acf98e53765349f491` matches sidecar. `ffprobe` reports H.264 1920x1088 plus AAC mono 48 kHz and duration 1,019.933333 seconds. Start, midpoint, and final five-second decodes pass; no temp artifact remains.
- After provisional-index implementation: `git diff --check` passes; focused writer-index and source contract tests pass; full suite passes 846 tests with 0 failures, 0 errors, 0 skipped; `assembleDebug` passes. Stale direct-reserve path and encoding-corruption scans found no production match.
- Installed APK SHA-256 `1B09C630B9083737CE992EA4824F3F1E659F5A50F4CCC1F9C216113C3B4CD518`.
- 60-second external run `DCAM_000005_B01OPR_20260805_201158.mp4`: startup 766 ms; encoder stop/finish 364 ms; metadata write 80 ms; durability sync 1,369 ms; seek index source `writer metadata`; publication rename 352 ms; media finalization 1,852 ms; total stop 2,217 ms; no failure or backpressure.
- Writer-index smoke MP4 size 79,877,607 bytes; device MD5 `0de58c01fadc6340b40725cd0365342c` matches sidecar. `ffprobe` reports H.264 1920x1088 plus AAC mono 48 kHz and duration 59.966667 seconds. Start, midpoint, and final five-second decodes pass.
- Focused post-timeout-fix tests passed: `SharedMicrophoneCaptureTest` and `SharedCameraGatewaySourceTest`; Gradle build successful.
- First sparse-index compile check failed with two unreported `IOException` calls in `recordedSegmentIndexes`; no runtime test ran. Fix: declare existing helper as throwing `IOException`, already supported by its caller.
- Focused storage/audio tests passed after fix: `DcamInterruptedMp4FinalizerTest`, `DcamMediaPublisherTest`, `SharedMicrophoneCaptureTest`, and `SharedCameraGatewaySourceTest`.
- Invalid or incompatible writer metadata now fails clean finalization boundedly and preserves staging; only interrupted recovery scans media for legacy or incomplete recordings.
- Clean-finalization failure logs and runtime capture messages now inspect staging existence; post-publication failures no longer claim the file remains in Temp.
- Expanded focused suite passed: storage finalizer/publisher/output, runtime backend/source contracts, and microphone backlog tests.
- Full `:app:testDebugUnitTest :app:assembleDebug` passed. Test XML totals: 856 tests, 0 failures, 0 errors, 0 skipped.
- Debug APK SHA-256: `0C04874B7258E3D26925755DDF67D07F82776955BEF8E704E582C7A6FEE2D942`; size 7,125,590 bytes.
- Device validation target connected: `BodyCamera`, removable storage `/storage/6162-6433` mounted with 14 GB free. Starting current-APK smoke before exact 17-minute run.
- Current-APK 24.533-second external smoke `DCAM_000005_B01OPR_20260805_210929.mp4` passed: encoder stop/finish 130 ms; audio drain 30 ms; metadata write 41 ms; durability sync 880 ms; writer metadata used; rename 231 ms; media finalization 1,193 ms; total stop 1,323 ms; Temp empty.
- Exact host stop request at 1,020,018 ms produced media duration 1,019,800 ms for `DCAM_000005_B01OPR_20260805_211045.mp4`. Audio drain 122 ms; container close 41 ms; encoder stop/finish 291 ms; metadata write 443 ms; durability sync 450 ms; writer metadata used; rename 265 ms; media finalization 1,190 ms; total stop 1,482 ms. Temp empty.
- Long MP4 size 1,341,686,870 bytes. Device MD5 `0373a45e4c4ff88531468a0ba8a23602` matches sidecar.
- Independent review confirmed normal performance evidence and identified failure-path gaps: clean finalization still scanned on missing writer metadata; clean inspection mismatch deleted staging; AAC runtime exceptions after stop could be suppressed; post-rename notification failure could be reported as finalization failure. Reopened implementation only for these paths; normal writer-metadata path remains unchanged.
- Final long external run started at device time 20:14:19 on 2026-08-05. Startup: media preparation 109 ms; encoder startup 400 ms; total 510 ms; no failure or backpressure.
- Final long five-minute checkpoint: external staging MP4 reached 395,401,256 bytes; no failure, error, backpressure, limit, or stop event logged.
- Final long ten-minute checkpoint: external staging MP4 reached 818,681,835 bytes; no failure, error, backpressure, limit, or stop event logged.
- Final long fifteen-minute checkpoint: external staging MP4 reached 1,184,118,474 bytes; no failure, error, backpressure, limit, or stop event logged.
- Final exact 17-minute stop failed before container close: audio stop/drain timed out at 2,002 ms; container close 0 ms; runtime outcome `recovery_required` with `audio_encoder_stop_timeout`. Staging input was retained and publication was not attempted. Cause not yet proven.
- After watchdog recovery, exhaustive searches of external Temp, external final media, internal app files, and internal media found no `DCAM_000005_B01OPR_20260805_201419` artifact. The 17-minute staging path disappeared. Treat as confirmed recovery data-loss behavior until exact delete path is identified.
- Independent review found no high-severity issue. Medium findings to resolve before completion: publication no-overwrite race/fallback, post-rename recovery messaging/state, sparse A/V fragment handling, duplicated writer/recovery duration policy, and incomplete final long validation.
- Confirmed staging deletion owner: `SharedAvcEncoder.finish()` retains `segmentFile` after audio timeout; `SharedCameraRuntimeBackend.executeStopRecording()` calls `failRecording()`, which only releases the media reservation; watchdog release reaches `AbstractSharedCameraPipeline.releaseResources()`, which unconditionally deletes `videoArtifact`.
- Audio-loop source separates media completion from thread completion incorrectly: `prepareAndRunAudio()` marks `audioCaptureStopped` and clears `audioThread` after all audio muxer writes, then performs codec release and next-recording AAC pre-prime before thread exit; `stopAudioCapture()` waits for thread exit and can report `audio_encoder_stop_timeout` after media writes already completed.
- Shared microphone queue can retain at most 64 × 4,096 bytes, about 2.73 seconds of mono 48 kHz PCM. This backlog is bounded and independent of recording length, but current diagnostics do not identify whether failed run was still draining queued PCM, releasing codec, or priming next codec.

- Post-review focused validation reached test compilation and failed only because `DcamInterruptedMp4FinalizerTest` uses `IOException` without importing `java.io.IOException`. No tests executed in that command; record failure before one-line import fix.

- Added missing `java.io.IOException` import to `DcamInterruptedMp4FinalizerTest`; production behavior unchanged.

- Confirm phase resumed: run focused finalizer, publisher, output, camera lifecycle, runtime backend, and microphone regression tests after review hardening.

- Post-review focused suite passed: `DcamInterruptedMp4FinalizerTest`, `DcamMediaPublisherTest`, `DcamMediaOutputImplTest`, `SharedCameraGatewaySourceTest`, `SharedCameraRuntimeBackendTest`, and `SharedMicrophoneCaptureTest`. Next confirm phase: full unit suite plus debug APK assembly.

- Post-review full validation passed: 857 tests, 0 failures, 0 errors, 0 skipped; `:app:assembleDebug` passed. Rebuilt APK is 7,139,089 bytes with SHA-256 `8859E39692E465FB27BF270857924FC976BBBB1513683DA5EC1FE0CC14880876`.
- Confirm phase next validates pulled exact 17-minute MP4 checksum, streams, duration, and start/mid/end decode.

- Pulled exact 17-minute MP4 validation passed: 1,341,686,870 bytes; local MD5 `0373a45e4c4ff88531468a0ba8a23602` matches device and sidecar; H.264 1920x1088 duration 1,019.800 s; AAC mono 48 kHz duration 1,019.797333 s; five-second A/V decodes passed at 0 s, 507 s, and 1,014.5 s.
- Confirm phase next installs rebuilt post-review APK and runs short external-storage smoke; normal fast path code is unchanged, so another 17-minute run is not required unless smoke exposes a regression.

- Rebuilt APK installed in place and launched with existing operator data. Short external smoke recorded `DCAM_000005_B01OPR_20260805_214318.mp4`; host observed stop-to-publication in 902 ms. Final file is 76,307,032 bytes; Temp is empty; recording UI returned idle; device MD5 `f862baeb1d084ec8f1709436c718c456` matches sidecar.

- Rebuilt-APK smoke exact logs: duration 57,167 ms; audio stop/drain 47 ms; container close 31 ms; encoder stop/finish 179 ms; metadata write 74 ms; durability sync 352 ms; writer metadata used; publication rename 178 ms; media finalization 660 ms; total stop 840 ms. No finalization failure or backpressure appears in filtered logs.
- Final smoke check pulls the 76 MB MP4 and validates streams plus A/V decode locally before source-diff checks.

- Pulled rebuilt-APK smoke MP4 matches device MD5. `ffprobe` reports H.264 1920x1088 duration 57.166667 s and AAC mono 48 kHz duration 57.152 s; five-second A/V decodes passed at 0 s and 52 s.
- Review phase: inspect exact task diff, run path-scoped `git diff --check`, and scan touched files for encoding corruption before completion.




- Independent review findings resolved for reported clean path: missing writer metadata fails without scanning; clean inspection mismatch retains staging; AAC drain exceptions fail finalization; post-rename notification failure remains success; no-overwrite publication requests `ATOMIC_MOVE`.
- Final hygiene passed: path-scoped `git diff --check`, untracked trailing-whitespace scan, UTF-8 corruption scan, and numeric-entity scan.


## Completion Notes

Required work: None

All validation passed: full unit suite, debug APK, long media verification, rebuilt device smoke, and final hygiene checks.