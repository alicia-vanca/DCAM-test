# CameraX interrupted-recording recovery evidence

- Review date: 2026-07-11
- Project CameraX version: `1.6.1`
- Local conclusion: implementation support is present; target BodyCamera proof is still required.

## Official-source finding

CameraX 1.6 migrated `VideoCapture`/`Recorder` to the AndroidX Media3 muxer. The official release
notes state that this fixes video corruption when recording is interrupted unexpectedly or the app
is terminated:

- [CameraX 1.6 release notes](https://developer.android.com/jetpack/androidx/releases/camera#1.6.0-beta02)
- [AndroidX change introducing the Media3 muxer](https://android.googlesource.com/platform/frameworks/support/+/b88067fc5509654f5958f5833e07c9a48e7e4f9a)
- [AndroidX interruption-resilience change](https://android.googlesource.com/platform/frameworks/support/+/82dff63c1656f1d18d471a0d4d3965e502c5d510)

The older platform `MediaMuxer` behavior is not an acceptable fallback: the Android MPEG-4 writer
constructs/finalizes important MP4 metadata during orderly stop. The Media3 migration is therefore
material to the Build 0.1 evidence-preservation requirement.

CameraX also documents that a successful `VideoRecordEvent.Finalize` makes output safe to access.
Some controlled errors, including file-size limit and source-inactive, can still produce usable
output; encoding, recorder, no-valid-data and unknown errors may produce malformed output and must
not be published without validation:

- [VideoRecordEvent.Finalize](https://developer.android.com/reference/androidx/camera/video/VideoRecordEvent.Finalize)

## Implemented local boundary

- Active visual capture writes only under `Temp`.
- Successful CameraX/image completion is copied to a hidden non-contract publication file, flushed
  with `FileDescriptor.sync()`, size-verified, and renamed into `Media/*`.
- A failure never overwrites final media and leaves staging in place.
- Once per process startup, contract-named `Temp` candidates are checked with Android decoders.
  Playable unencrypted candidates are finalized; invalid, encrypted, duplicate or ambiguous
  candidates are preserved.
- Per-file database rows are not part of the Build 0.1 readiness boundary. Presence under the
  approved `Media/*` folder is the BDMA-visible ready state.

## Required BodyCamera destructive test

Desktop unit tests cannot prove vendor filesystem, codec, muxer or sudden-power-loss behavior.
Run all cases on both qualified internal and removable roots:

1. Start a timestamp-visible video and record the wall-clock start time.
2. After at least 30 seconds, force-stop the package without pressing Stop:
   `adb shell am force-stop com.dvid.dcam`.
3. Before relaunch, pull the contract-named MP4 from `Temp` and verify it with BDMA and an independent
   decoder such as `ffprobe`/VLC.
4. Record recovered duration. Acceptance: playable audio/video and loss bounded to the agreed
   near-crash tolerance (proposed initial gate: no more than five seconds).
5. Relaunch DCAM. Verify startup recovery moves a playable unencrypted candidate to the matching
   `Media/*` folder and preserves an invalid candidate in `Temp`.
6. Repeat with process kill, device reboot/power interruption, storage-full limit, and removable
   storage removal. A reboot/power cut is stronger than force-stop and must be reported separately.
7. Repeat at least ten times per root; record recovered duration delta and any corrupt/unreadable
   result. Do not declare the requirement proven from one successful sample.

## Remaining limits

- CameraX's fix is library-level evidence, not proof for the selected BodyCamera firmware and
  filesystem.
- The exact amount of trailing video preserved is not guaranteed by the API documentation.
- Automatic recovery of `_enc` staging is intentionally disabled because the filename alone cannot
  prove whether an interrupted in-place transformation contains plaintext or ciphertext.
- Removable-media recovery must run again on a mount event; startup recovery can only inspect roots
  available at that time.

## BWC execution results — 2026-07-11

Device: BWC `KF5OF2126040802193`, removable volume `E3AE-18F6`, battery 100%.

Before the interruption trials, real-device launch/publication found two Java API compatibility
problems that JVM tests did not expose:

- `Stream.toList()` failed at startup on the device runtime. It was replaced with explicit list
  construction.
- `FileInputStream.transferTo()` failed on the publication worker. It was replaced with a bounded
  64 KiB manual copy loop followed by flush and `FileDescriptor.sync()`.

After each correction, `gradlew test assembleDebug` passed and the APK was reinstalled.

### Graceful baseline and recovery

- CameraX produced a 71,637,100-byte staged MP4 on removable storage.
- The unsupported-copy crash occurred after CameraX finalized but before filesystem publication.
- Staging and the hidden partial publication copy both remained present.
- After installing the compatible copy implementation, startup recovery removed the partial,
  Android `MediaMetadataRetriever` accepted the MP4, and DCAM published it to `Media/Video`.
- Recovery log: `recovered=1, preserved=0, duplicates=0`.

### Forced app termination

- Recording start filename/time: `..._20260711_091810.mp4` / 09:18:10.
- At approximately 30 seconds, `adb shell am force-stop com.dvid.dcam` terminated DCAM without a
  Stop/finalize request.
- Raw `Temp` file after process death: 75,080,663 bytes.
- MP4 `mvhd`: timescale 10,000, duration 301,037 units = **30.104 seconds**.
- The file was pulled before relaunch. On relaunch, Android media validation accepted it and startup
  recovery published it to removable `Media/Video`.
- Recovery log: `recovered=1, preserved=0, duplicates=0`.

Result: the forced-termination sample retained playable media through the interruption point with
no measurable trailing loss at the one-second observation precision.

### Device reboot during recording

- Recording start filename/time: `..._20260711_092052.mp4` / 09:20:52.
- `adb reboot` was issued at approximately 30 seconds without sending DCAM Stop.
- Raw removable `Temp` file after boot: 69,600,557 bytes.
- MP4 `mvhd`: timescale 10,000, duration 274,005 units = **27.400 seconds**.
- Observed trailing loss was approximately **2.6 seconds**, inside the proposed five-second gate.
- After reboot the BWC exposes the removable volume to Windows as USB mass storage and reports it
  as `shared`, so Android startup recovery cannot inspect that root until the volume is returned to
  Android. The artifact remains preserved in removable `Temp`; its host copy is under
  `build/device-test/reboot-30s.mp4`.

Result: the reboot sample contains a valid MP4 movie header and near-crash duration, but Android
decoder validation after a real remount and repeated/power-cut trials are still required before the
full item 9 gate can be called complete.
### Notification shade and power-button observations — 2026-07-17

- Audio recording survived opening the notification shade and pressing the power button; recording
  continued without restarting.
- Video recording did not survive either interruption:
  - Opening the notification shade stopped video recording, finalized and saved the current clip,
    then required a new recording from 0 seconds.
  - Pressing the power button also stopped video recording and saved the current clip; recording did
    not resume automatically.

Result: audio interruption recovery is observed, but video recording currently stops and starts a new
clip after notification-shade or power-button interruption. This does not prove uninterrupted video
continuity or automatic resume; keep video recovery as a failing or partial result until fixed and
retested.