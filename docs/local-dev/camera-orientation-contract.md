# Camera Orientation Implementation and Test Contract

This document defines mandatory implementation rules, test procedures, evidence requirements, and stop conditions for camera preview, captured images, recorded video, and onscreen UI.

It is an engineering contract for an AI coding agent.

The agent must not change orientation behavior based on assumptions, visual guesses, common device conventions, or hardcoded angles.

---

## 1. Primary Acceptance Requirement

For every tested camera and every tested physical device orientation:

* The physical `UP`, `DOWN`, `LEFT`, and `RIGHT` shown on the directional paper facing the camera must appear consistently in:

  * The live preview.
  * The captured JPEG.
  * The recorded video when played normally.
* UI text must remain upright in current screen coordinates.
* UI text must not rotate together with the camera image.
* Preview must not be stretched.
* Preview must not be cropped by the app.
* Preview must not overflow the safe display area.
* Preview must not render behind status bars, navigation bars, display cutouts, or other system insets.
* Final preview parity must match the JPEG reference; one evidence-backed app compensation transform is allowed when the platform preview stream is measured as mirrored.
* Preview framing and output framing must be compared manually.
* Physical device orientation must never be forced through code, API, ADB, shell commands, automation, or window-manager controls.

The directional paper placed facing the camera is the physical-direction reference.

The labels on the paper must clearly identify:

```text
UP
DOWN
LEFT
RIGHT
```

The preview and output file pass only when these directions match the real physical scene.

---

## 2. Normative Language

The following terms are mandatory:

* `MUST`: required.
* `MUST NOT`: prohibited.
* `SHOULD`: recommended unless evidence justifies otherwise.
* `MAY`: optional.

The agent must treat every `MUST` and `MUST NOT` statement as a blocking requirement.

---

## 3. Definitions

```text
S = camera sensor orientation in degrees
D = current display rotation in degrees
F = lens facing: back or front
N(x) = normalized right-angle rotation
O = logical target output rotation
Q_preview = rotation already present in the sampled preview
Q_video = rotation physically baked into encoded video pixels
A_preview = residual app preview rotation
V = residual video orientation metadata
```

Normalization:

```text
N(x) = ((x % 360) + 360) % 360
```

Valid normalized values:

```text
0
90
180
270
```

All rotation values in this document use one consistent convention:

```text
Clockwise rotation in unmirrored image coordinates
```

The agent must document any API whose direction convention differs and convert it before using the value.

---

## 4. Required Runtime Inputs

The agent MUST read or measure:

* Active camera ID.
* Active physical camera ID, when applicable.
* Lens facing.
* Sensor orientation `S`.
* Current display rotation `D`.
* Preview stream width and height.
* JPEG stream width and height.
* Video stream width and height.
* Current safe display bounds.
* System-bar insets.
* Display-cutout insets.
* `SurfaceTexture` transform matrix, when a `SurfaceTexture` is used.
* EGL or shader rotation.
* EGL or shader crop.
* View transform.
* Pipeline rotation.
* Pipeline crop.
* Preview mirror parity.
* Video pixel rotation already applied before encoding.
* Requested JPEG orientation.
* Requested video orientation metadata.

The agent MUST NOT infer these values from a previous device, previous camera, common Android behavior, or expected defaults.

---

## 5. Logical Output Rotation

Compute the target logical output rotation as:

```text
back camera:
O = N(S - D)

front camera:
O = N(S + D)
```

`O` is the target orientation for the normally displayed output.

The agent MUST NOT hardcode a general result such as:

```text
O = 90
O = 270
```

A fixed value is allowed only as a logged result for one measured combination of:

```text
camera ID
physical camera ID
S
D
F
```

---

## 6. JPEG Requirement

For Camera2 JPEG capture:

```text
JPEG_ORIENTATION = O
```

The platform may represent orientation through:

* Physically rotated JPEG pixels.
* EXIF orientation metadata.
* A combination of both.

Therefore, JPEG validation MUST use the image as normally decoded with orientation metadata respected.

The agent MUST NOT determine correctness only from raw JPEG width and height.

Required JPEG result:

* The directional paper appears physically upright.
* `UP` is physically up.
* `DOWN` is physically down.
* `LEFT` is physically left.
* `RIGHT` is physically right.
* Left/right parity matches the real scene.
* The JPEG is not unintentionally mirrored.
* The JPEG and preview show the same physical directions.

---

## 7. Preview Transform Requirement

The preview MUST NOT blindly apply `O` directly to the preview view.

Camera systems may already apply some combination of:

* Sensor rotation.
* Display rotation.
* Texture-coordinate transform.
* Crop.
* Scaling.
* EGL rotation.
* Shader rotation.
* Camera HAL transform.
* Framework transform.
* Mirror transform.

The agent MUST distinguish between:

```text
T_sample
```

The transform required to sample the source texture correctly.

```text
Q_preview
```

The rotation already visible in the sampled preview image.

```text
C_preview
```

The crop already represented by the preview source.

```text
A_preview
```

The residual rotation the app must apply.

Residual preview rotation:

```text
A_preview = Rcw(O) * inverse(Q_preview)
```

Conceptual final preview:

```text
finalPreview =
    L_contain
    * A_preview
    * Q_preview
    * C_preview
    * sensorBuffer
```

Therefore:

```text
finalPreview =
    L_contain
    * Rcw(O)
    * C_preview
    * sensorBuffer
```

For the validated native `SurfaceTexture` preview path, the measured residual view rotation is:

```text
previewViewRotation = N(-D)
```

This view rotation is independent of lens facing. `O` remains the JPEG/video target and MUST NOT be substituted for `previewViewRotation`.

The crop remains part of the source.

The app MUST NOT attempt to invert a crop or recover discarded pixels.

---

## 8. Coordinate-Space Requirement

The agent MUST NOT multiply unrelated matrices as though they use the same coordinate space.

In particular:

* A `SurfaceTexture` matrix operates in texture-sampling coordinates.
* A view matrix operates in view or screen coordinates.
* A shader matrix may operate in normalized device coordinates or custom image coordinates.
* A crop rectangle may operate in sensor, stream, texture, or view coordinates.

Before composing transforms, the agent MUST document:

* Source coordinate space.
* Destination coordinate space.
* Rotation direction.
* Matrix order.
* Whether row vectors or column vectors are used.
* Whether the origin is top-left, bottom-left, or center.
* Whether the Y axis increases upward or downward.
* Whether the transform includes mirror parity.
* Whether the transform includes translation.
* Whether the transform includes crop or scale.

Matrix equations in this contract assume:

```text
column vectors
rightmost transform applied first
```

---

## 9. SurfaceTexture Requirement

When `SurfaceTexture` is used:

* The agent MUST retrieve its transform matrix.
* The matrix MUST be applied while sampling the texture.
* The matrix MUST NOT be canceled merely to simplify view layout.
* The agent MUST account for the matrix changing after `updateTexImage()`.
* The agent MUST log the matrix used for the tested frame or frame sequence.
* The agent MUST not assume the matrix contains only rotation.
* The agent MUST inspect whether it contains crop, translation, axis inversion, or other normalization.

---

## 10. Front-Camera Mirror Requirement

Final preview parity MUST match the JPEG, which is the left/right parity reference.

The current supported BodyCamera platform has manual evidence for both camera IDs and all four physical orientations:

* Back-camera preview arrives unmirrored.
* Front-camera preview arrives mirrored while JPEG and video remain unmirrored.
* App rotation, view matrix, and texture scale do not introduce that platform mirror.

Required application behavior on this validated platform:

```text
previewSurface.setMirrored(frontFacing)
```

For the front camera, `true` means apply exactly one counter-mirror transform so final preview parity is unmirrored. It does not mean mirrored output is desired. Back cameras receive no mirror transform.

The app MUST NOT:

* Change rotation equations to repair mirror parity.
* Apply more than one compensation transform.
* Mirror JPEG or video to match a mirrored preview.
* Reuse this platform policy on different hardware without parity evidence.

If another supported platform or camera has different preview parity:

1. Record the observed mirror state and all transform diagnostics.
2. Record preview, JPEG, and video evidence.
3. Add an explicit per-camera platform capability.
4. Do not stack another transform or infer parity from lens facing alone.

Mirror parity MUST be validated separately from rotation in every physical orientation.

---

## 11. Video Requirement

Video orientation has two components:

```text
Q_video = rotation already physically applied to encoded pixels
V = orientation metadata added to the container
```

Compute residual video metadata as:

```text
V = N(O - Q_video)
```

Examples:

```text
Q_video = 0
V = O
```

The encoder receives unrotated source-oriented pixels, so metadata provides the required playback rotation.

```text
Q_video = O
V = 0
```

The encoder receives pixels already rotated to the target orientation, so additional metadata would cause double rotation.

The agent MUST NOT automatically set:

```text
video orientation hint = O
```

unless evidence proves:

```text
Q_video = 0
```

Required video result when played by a normal metadata-aware player:

* `UP` on the directional paper appears up.
* `DOWN` appears down.
* `LEFT` appears left.
* `RIGHT` appears right.
* Video parity matches the physical scene.
* Video and JPEG resolve to the same physical orientation.
* Video and preview resolve to the same physical orientation.

The agent MUST inspect both:

* Encoded pixel orientation.
* Container orientation metadata.

---

## 12. UI Text Requirement

Onscreen text MUST remain in screen coordinates.

```text
textTransform = Identity
```

UI text MUST NOT inherit:

* Camera sensor rotation.
* Preview rotation.
* Texture rotation.
* Video rotation.
* JPEG orientation.
* Preview mirror.
* Camera crop.

When the device is physically rotated and Android reports the new display state:

* UI text must become upright for the current screen orientation.
* The top of each character must point toward the current physical top of the display.
* Text must not appear sideways.
* Text must not appear upside down.
* Text must not be mirrored.

Camera image transforms and UI transforms MUST remain separate.

If text is later burned into captured media, that is a separate rendering pipeline. Burned-in text must be transformed into final output coordinates only after output orientation is resolved.

---

## 13. Safe-Area and Contain Geometry

The preview MUST use contain behavior.

The app MUST NOT use a center-crop behavior when validating this contract.

Let the visible preview source after producer crop be:

```text
Wc × Hc
```

Determine the effective dimensions after output rotation:

```text
if O is 0 or 180:
    effectiveWidth = Wc
    effectiveHeight = Hc

if O is 90 or 270:
    effectiveWidth = Hc
    effectiveHeight = Wc
```

Let the safe area be:

```text
safeLeft
safeTop
availableWidth
availableHeight
```

Compute:

```text
scale = min(
    availableWidth / effectiveWidth,
    availableHeight / effectiveHeight
)

renderedWidth = effectiveWidth * scale
renderedHeight = effectiveHeight * scale

left =
    safeLeft
    + (availableWidth - renderedWidth) / 2

top =
    safeTop
    + (availableHeight - renderedHeight) / 2
```

Required behavior:

* Use one uniform scale.
* Preserve aspect ratio.
* Center inside the safe area.
* Do not stretch.
* Do not crop.
* Do not overflow.
* Do not render behind system bars.
* Do not render behind display cutouts.
* Clip only for negligible numerical rounding.
* Letterbox or pillarbox space is acceptable and required when aspect ratios differ.
* Preview may touch all four safe-area edges only when the aspect ratios match.

The agent MUST NOT declare unused space a bug when contain geometry mathematically requires it.

---

## 14. Dynamic Recalculation Requirements

The agent MUST recalculate relevant values when any of the following changes:

* Display rotation.
* Window dimensions.
* Safe-area insets.
* Display cutout.
* Camera ID.
* Active physical camera ID.
* Lens facing.
* Sensor orientation.
* Preview resolution.
* Preview crop.
* JPEG resolution.
* Video resolution.
* Preview source transform.
* EGL transform.
* Shader transform.
* View transform.
* Preview mirror parity.
* Video pipeline rotation.
* Device posture on foldable devices.

A 180-degree physical rotation MUST be handled even if Android does not recreate the activity or emit the same callbacks as a 90-degree rotation.

The agent MUST observe the actual display rotation instead of relying only on configuration-change callbacks.

---

## 15. Absolute Prohibition on Programmatic Device Rotation

The agent MUST NOT rotate or force the physical device orientation by using:

* Android orientation APIs.
* Requested activity orientation.
* Sensor-orientation overrides.
* Window-manager commands.
* ADB rotation commands.
* Emulator rotation commands.
* Shell settings.
* UI automation that rotates the device.
* Camera APIs intended to simulate another orientation.
* Any command that changes the reported display rotation without the user physically rotating the device.

Examples of prohibited actions include, but are not limited to:

```text
setRequestedOrientation(...)
adb shell settings put system accelerometer_rotation ...
adb shell settings put system user_rotation ...
wm set-user-rotation ...
content insert/update commands that force rotation
emulator rotate commands
```

The agent may read the current device orientation.

The agent may not change it.

---

## 16. Mandatory Manual-Rotation Stop Protocol

When testing requires a different physical device orientation, the agent MUST stop processing the task.

The agent MUST NOT continue automatically.

The agent MUST NOT force rotation.

The agent MUST NOT assume the user has rotated the device.

The agent must send a clear request containing:

* The exact required physical orientation.
* A statement that no API, ADB command, or automated rotation will be used.
* A request for the user to rotate the device manually.
* A request for the user to confirm before the task continues.

Required stop message format:

```text
Manual device rotation required.

Please physically rotate the device to: <required orientation>.

Keep the UP/DOWN/LEFT/RIGHT directional paper facing the camera and keep the scene unchanged.

I will not use an API, ADB, shell command, emulator control, or window-manager command to rotate the device.

Tell me to continue after the device is physically in the requested orientation.
```

After sending this message, the agent MUST:

* Stop issuing test commands.
* Stop capturing screenshots.
* Stop capturing JPEGs.
* Stop starting recordings.
* Stop changing code.
* Stop changing orientation values.
* Stop interpreting evidence for the requested orientation.

The agent may continue only after the user explicitly confirms that the device has been manually rotated and instructs the agent to continue.

Acceptable user confirmation examples:

```text
Continue.
Rotated, continue.
The device is now landscape-left. Continue.
Done, continue testing.
```

The agent MUST log the user confirmation in the test evidence.

---

## 17. Physical Orientation Naming

Before requesting manual rotation, the agent MUST describe the required orientation unambiguously.

Preferred descriptions:

```text
Portrait:
The top of the device is physically upward.

Upside-down portrait:
The bottom of the device is physically upward.

Landscape-left:
The device is horizontal and its left side is physically upward.

Landscape-right:
The device is horizontal and its right side is physically upward.
```

Because landscape terminology varies between platforms, the agent SHOULD also describe which physical device edge must point upward.

The agent MUST NOT request only:

```text
Rotate to 90 degrees.
Rotate to landscape.
```

unless the physical edge direction is also stated.

---

## 18. Directional Paper Test Setup

The directional paper is the ground-truth scene marker.

The paper MUST:

* Face the active camera.
* Remain visible in preview.
* Remain visible in JPEG.
* Remain visible in recorded video.
* Clearly show `UP`, `DOWN`, `LEFT`, and `RIGHT`.
* Have an asymmetric layout sufficient to detect mirror parity.
* Remain in the same position during each preview/JPEG comparison.
* Remain in the same position during the relevant video recording.

Recommended marker layout:

```text
              UP

LEFT     asymmetric mark     RIGHT

             DOWN
```

The asymmetric mark may be:

* A letter that is not horizontally symmetric.
* A numbered arrow.
* A mark placed only on one side.
* A handwritten word.
* A colored or shaped marker with a clearly documented side.

A simple cross without asymmetric content is insufficient for mirror testing.

---

## 19. Required Test Sequence Per Camera

The following sequence MUST be executed independently for every camera ID being validated.

### Step 1: Record Build Identity

Record:

* APK file hash.
* Version name.
* Version code.
* Git commit.
* Build variant.
* Package name.
* Device model.
* Android version.
* Camera ID.
* Active physical camera ID, when applicable.

Do not continue if the APK or approved version values changed unexpectedly.

### Step 2: Confirm Physical Test Setup

Confirm:

* The user placed the directional paper facing the camera.
* All four direction labels are visible.
* An asymmetric mirror marker is visible.
* The physical device orientation is confirmed by the user.
* No API or command forced rotation.

### Step 3: Read Orientation Inputs

Record:

```text
S
D
F
O
```

Also record:

```text
O = N(S - D)
```

or:

```text
O = N(S + D)
```

depending on lens facing.

### Step 4: Record Preview Pipeline

Record:

* Preview source dimensions.
* Source crop.
* `SurfaceTexture` matrix.
* EGL transform.
* Shader transform.
* Pipeline rotation.
* View transform.
* Contain-layout transform.
* Preview mirror parity.
* Computed `Q_preview`.
* Computed `A_preview`.

### Step 5: Inspect Live Preview

Manually verify:

* Paper `UP` points physically upward.
* Paper `DOWN` points physically downward.
* Paper `LEFT` appears physically left.
* Paper `RIGHT` appears physically right.
* Preview is not mirrored.
* Preview is not stretched.
* Preview is not cropped by app layout.
* Preview stays within safe bounds.
* UI text remains upright.
* UI text is not mirrored.
* UI text does not rotate with the preview image.

### Step 6: Capture Corresponding JPEG

While the paper and camera remain unchanged:

* Capture the JPEG immediately before or after the preview screenshot.
* Record the requested JPEG orientation.
* Record resulting JPEG metadata.
* Do not move the device.
* Do not move the paper.
* Do not change camera settings.

### Step 7: Manually Compare Preview and JPEG

Compare:

* Physical `UP`.
* Physical `DOWN`.
* Physical `LEFT`.
* Physical `RIGHT`.
* Mirror parity.
* Visible framing.
* Aspect ratio.
* Crop.
* Relative position of the asymmetric marker.

No automated image comparison is allowed under this contract.

### Step 8: Record Video

Record:

* Requested video resolution.
* Requested frame rate.
* Encoder input orientation.
* `Q_video`.
* Requested orientation metadata `V`.
* Actual container orientation metadata.

Play the video normally and verify the four physical directions.

### Step 9: Record Result

Result must be one of:

```text
PASS
FAIL
BLOCKED — insufficient evidence
BLOCKED — manual rotation required
BLOCKED — platform mirror mismatch
```

The agent MUST NOT use `PASS` when required diagnostics are missing.

---

## 20. Required Orientation Coverage

Unless the approved test plan narrows the scope, validate each camera in:

* Portrait.
* Landscape with the left device edge physically upward.
* Upside-down portrait.
* Landscape with the right device edge physically upward.

Each orientation change requires the mandatory manual-rotation stop protocol.

Example sequence:

```text
1. Test portrait.
2. Stop.
3. Ask user to rotate manually to landscape-left.
4. Wait for explicit confirmation.
5. Continue landscape-left test.
6. Stop.
7. Ask user to rotate manually to upside-down portrait.
8. Wait for explicit confirmation.
9. Continue upside-down portrait test.
10. Repeat for landscape-right.
```

---

## 21. Evidence Requirements

For every camera and physical device orientation, collect:

1. APK hash.
2. Version name.
3. Version code.
4. Git commit.
5. Device model.
6. Android version.
7. User-confirmed physical orientation.
8. Confirmation that no API, ADB, shell, emulator, or window-manager rotation was used.
9. Camera ID.
10. Active physical camera ID, when applicable.
11. Sensor orientation `S`.
12. Display rotation `D`.
13. Lens facing `F`.
14. Computed output rotation `O`.
15. Requested JPEG orientation.
16. JPEG orientation metadata.
17. Preview stream dimensions.
18. JPEG dimensions.
19. Video dimensions.
20. Preview crop.
21. `SurfaceTexture` matrix.
22. EGL or shader transform.
23. View transform.
24. Preview rotation already applied, `Q_preview`.
25. Residual preview rotation, `A_preview`.
26. Preview mirror parity.
27. Video pixel rotation, `Q_video`.
28. Video orientation metadata, `V`.
29. Safe-area bounds.
30. Preview rendered bounds.
31. Preview screenshot.
32. Corresponding JPEG.
33. Short video sample.
34. Manual preview/JPEG comparison.
35. Manual preview/video comparison.
36. UI text orientation result.
37. Final pass, fail, or blocked status.

---

## 22. Logger Requirements

Application logs MUST include a structured record similar to:

```text
OrientationState {
    apkHash,
    versionName,
    versionCode,
    gitCommit,
    deviceModel,
    androidVersion,

    cameraId,
    physicalCameraId,
    lensFacing,

    sensorOrientationDegrees,
    displayRotationDegrees,
    computedOutputRotationDegrees,

    previewWidth,
    previewHeight,
    previewCrop,

    jpegWidth,
    jpegHeight,
    jpegRequestedOrientation,
    jpegResultMetadata,

    videoWidth,
    videoHeight,
    videoPixelRotation,
    videoOrientationMetadata,

    surfaceTextureMatrix,
    eglTransform,
    shaderTransform,
    viewTransform,

    previewSourceRotation,
    previewResidualRotation,
    previewMirrorParity,

    safeAreaBounds,
    renderedPreviewBounds,

    physicalOrientationConfirmedByUser,
    forcedRotationUsed = false
}
```

Logs MUST be produced before changing orientation code.

---

## 23. Prohibited Shortcuts

The agent MUST NOT use any of the following without direct evidence:

```text
previewRotation = O
previewRotation = O - S
previewRotation = S - D
front camera always needs mirror
front camera always needs 270 degrees
back camera always needs 90 degrees
video orientation hint always equals O
JPEG dimensions prove JPEG orientation
TextureView already handles everything
SurfaceView already handles everything
CameraX already handles everything
Camera2 never rotates preview
the emulator behaves like the physical device
```

The following shortcut:

```text
previewRotation = O - S
```

is permitted only when evidence proves that the upstream preview pipeline already consumed exactly `R(S)` and no other unaccounted orientation exists.

Mirror parity must still be validated separately.

---

## 24. Change-Control Gate

The agent MUST NOT modify orientation behavior until all relevant inputs are collected.

Before making a code change, the agent must state:

```text
Observed failure:
Expected result:
Actual result:
Camera ID:
Physical camera ID:
S:
D:
F:
O:
Q_preview:
A_preview:
Preview mirror parity:
Q_video:
Video metadata:
Suspected failing pipeline stage:
Evidence supporting the suspicion:
```

A code change is prohibited when the cause is only:

* A guess.
* A common Android convention.
* A single screenshot without matching JPEG.
* A JPEG without preview evidence.
* A preview without transform diagnostics.
* A test performed after forced device rotation.
* A comparison where the paper or device moved.
* A mirror assumption based only on lens facing.

When the cause is unclear:

1. Add focused logger diagnostics.
2. Rebuild without changing orientation behavior.
3. Repeat the same physical test.
4. Compare new evidence.
5. Change code only when evidence identifies the responsible stage.

---

## 25. Failure Classification

### Rotation Failure

Examples:

* `UP` appears left, right, or down.
* Preview and JPEG differ by 90, 180, or 270 degrees.
* Video playback differs from JPEG orientation.

Required action:

* Inspect `S`, `D`, `O`, `Q_preview`, `A_preview`, `Q_video`, and video metadata.
* Do not change mirror behavior.

### Mirror Failure

Examples:

* `LEFT` and `RIGHT` are reversed.
* The asymmetric marker appears on the opposite side.
* Preview differs in parity from JPEG.

Required action:

* Inspect mirror parity separately.
* Do not change rotation equations.

### Geometry Failure

Examples:

* Circles appear oval.
* Preview is stretched.
* Scene edges are missing due to app layout.
* Preview overlaps system bars.
* Preview overflows safe bounds.

Required action:

* Inspect source crop, aspect ratio, contain scale, safe-area calculation, and rendered bounds.
* Do not change orientation equations.

### UI Failure

Examples:

* Text rotates with preview.
* Text is sideways.
* Text is upside down.
* Text is mirrored.
* Text overlaps system bars.

Required action:

* Separate UI layout from the camera transform.
* Do not compensate by changing camera output rotation.

### Evidence Failure

Examples:

* Missing logs.
* Missing matching JPEG.
* Missing directional paper.
* Device orientation not confirmed.
* Rotation was forced programmatically.
* Paper moved between preview and JPEG.

Required action:

```text
Result = BLOCKED — insufficient evidence
```

Do not change code.

---

## 26. Agent Execution Checklist

### Before Testing

* [ ] Confirm exact APK hash.
* [ ] Confirm version name and version code.
* [ ] Confirm Git commit.
* [ ] Confirm device model and Android version.
* [ ] Confirm active camera ID.
* [ ] Confirm active physical camera ID when applicable.
* [ ] Confirm the directional paper faces the camera.
* [ ] Confirm all four direction labels are visible.
* [ ] Confirm an asymmetric mirror marker is visible.
* [ ] Confirm physical device orientation with the user.
* [ ] Confirm no programmatic rotation was used.

### Orientation Inputs

* [ ] Read `S`.
* [ ] Read `D`.
* [ ] Read `F`.
* [ ] Compute `O`.
* [ ] Log the complete equation.
* [ ] Verify all values normalize to `0`, `90`, `180`, or `270`.

### Preview

* [ ] Record preview dimensions.
* [ ] Record preview crop.
* [ ] Record `SurfaceTexture` matrix.
* [ ] Record EGL transform.
* [ ] Record shader transform.
* [ ] Record pipeline transform.
* [ ] Record view transform.
* [ ] Determine `Q_preview`.
* [ ] Compute `A_preview`.
* [ ] Record mirror parity separately.
* [ ] Confirm mirror compensation matches measured platform parity and final preview parity matches JPEG.
* [ ] Confirm contain geometry.
* [ ] Confirm preview stays in the safe area.
* [ ] Confirm no stretch.
* [ ] Confirm no app crop.
* [ ] Confirm physical directions using the paper.

### UI

* [ ] Confirm UI text is upright.
* [ ] Confirm UI text uses screen coordinates.
* [ ] Confirm UI text does not rotate with preview.
* [ ] Confirm UI text is not mirrored.
* [ ] Confirm UI respects system insets.

### JPEG

* [ ] Record requested JPEG orientation.
* [ ] Capture JPEG without moving device or paper.
* [ ] Read orientation metadata.
* [ ] Decode with metadata respected.
* [ ] Confirm physical directions.
* [ ] Confirm mirror parity.
* [ ] Compare framing manually with preview.

### Video

* [ ] Record encoder input orientation.
* [ ] Determine `Q_video`.
* [ ] Compute `V = N(O - Q_video)`.
* [ ] Record container orientation metadata.
* [ ] Play video normally.
* [ ] Confirm physical directions.
* [ ] Confirm mirror parity.
* [ ] Compare video with preview and JPEG.

### Before Changing Orientation

* [ ] Identify the failing stage.
* [ ] Confirm sufficient diagnostics exist.
* [ ] Rule out mirror failure before changing rotation.
* [ ] Rule out geometry failure before changing rotation.
* [ ] Rule out UI failure before changing camera transform.
* [ ] Document expected and actual results.
* [ ] Do not change code from assumptions.

### Before Testing Another Physical Orientation

* [ ] Stop the task.
* [ ] Do not use API, ADB, shell, emulator, or window-manager rotation.
* [ ] Tell the user the exact required physical orientation.
* [ ] Ask the user to rotate the device manually.
* [ ] Ask the user to keep the directional paper facing the camera.
* [ ] Ask the user to keep the scene unchanged.
* [ ] Wait for explicit user confirmation.
* [ ] Continue only after the user says to continue.

---

## 27. Final Acceptance Checklist

A camera/orientation combination passes only when all boxes are checked:

* [ ] Preview `UP` matches physical up.
* [ ] Preview `DOWN` matches physical down.
* [ ] Preview `LEFT` matches physical left.
* [ ] Preview `RIGHT` matches physical right.
* [ ] JPEG `UP` matches physical up.
* [ ] JPEG `DOWN` matches physical down.
* [ ] JPEG `LEFT` matches physical left.
* [ ] JPEG `RIGHT` matches physical right.
* [ ] Video `UP` matches physical up.
* [ ] Video `DOWN` matches physical down.
* [ ] Video `LEFT` matches physical left.
* [ ] Video `RIGHT` matches physical right.
* [ ] Preview and JPEG have the same rotation.
* [ ] Preview and JPEG have the same mirror parity.
* [ ] Preview and video have the same physical orientation.
* [ ] UI text is upright.
* [ ] UI text is not mirrored.
* [ ] UI text does not inherit the camera transform.
* [ ] Preview is not stretched.
* [ ] Preview is not cropped by app layout.
* [ ] Preview remains inside the safe area.
* [ ] All required logs and evidence exist.
* [ ] No physical orientation was forced programmatically.
* [ ] Every orientation change was performed manually by the user.
* [ ] Every manual rotation was explicitly confirmed before testing continued.

If any required item is unchecked, the result is not `PASS`.

---

## 28. Required Agent Behavior Summary

The agent must follow this order:

```text
Measure
Log
Compute
Test
Compare manually
Identify the responsible stage
Change only the responsible stage
Retest
```

The agent must never follow this order:

```text
Guess
Hardcode an angle
Force device rotation
Apply an unmeasured or duplicate mirror
Capture incomplete evidence
Declare success
```

When a new physical orientation is required:

```text
STOP
ASK USER TO ROTATE MANUALLY
WAIT FOR USER CONFIRMATION
CONTINUE
```

The directional paper is the physical truth reference.

For every device orientation, the live preview, normally displayed output file, and onscreen UI must agree with the real physical `UP`, `DOWN`, `LEFT`, and `RIGHT`.
---

## 29. Validation Record - 2026-08-01

### Scope and Result

* Live preview regression result: `PASS`.
* User manually confirmed camera 0 and camera 1 in all four physical device orientations.
* Preview direction, mirror parity, contain geometry, safe-area placement, and UI orientation passed.
* JPEG and video were not re-run during this final preview-layout regression. This record does not replace their separate evidence.

### Build Identity

```text
APK: app/build/outputs/apk/debug/app-debug.apk
versionName: 1.0
versionCode: 1
SHA-256: 1A6601C2DAE4F77F723F126CB46B2B42C0A5B6543878378D421716C02EC659E7
installed: 2026-08-01 19:36:23 Asia/Saigon
```

### Device and Camera Inputs

```text
device: BodyCamera
Android: 12
API: 31
build fingerprint: Android/full_k69v1_64_k419/k69v1_64_k419:12/SP1A.210812.016/mp1V14271:user/release-keys
physical camera IDs: none exposed; each tested camera is a standalone logical camera
```

| Camera | Facing | `S` | Selected pipeline | Video/preview stream |
|---|---|---:|---|---|
| `0` | back | `0` | `a-camera2-native-surface-sharing-v1` | `1920x1088@30` |
| `1` | front | `270` | `b-camera2-egl-fanout-v1` | `1280x720@30` |

### Rotation Matrix

| `D` | Camera 0 `O = N(S - D)` | Camera 1 `O = N(S + D)` | Manual preview result |
|---:|---:|---:|---|
| `0` | `0` | `270` | `PASS` |
| `90` | `270` | `0` | `PASS` |
| `180` | `180` | `90` | `PASS` |
| `270` | `90` | `180` | `PASS` |

For every row, the user confirmed the physical `UP`, `DOWN`, `LEFT`, and `RIGHT` paper matched the preview for both cameras. Front-camera compensation produced final parity matching the paper; UI text remained upright and unmirrored.

### Failure and Responsible Stage

Observed camera 1 after a physical `D=270` to `D=0` transition:

```text
final preview container: 480x746
stale TextureView: 250x444
```

`250x444` was the layout computed with the new orientation against the old `782x444` landscape container. Camera A and B produced the same stale geometry, while direction and parity remained correct. Evidence isolated the failure to shared preview resize timing, not camera orientation math or either transport pipeline.

`SharedCameraPreviewView.onSizeChanged()` changed child layout parameters during the parent layout traversal. The corrected update is posted until after that traversal, then reads final `getWidth()` and `getHeight()`.

Post-fix runtime measurements:

```text
camera 1, D=0, pipeline A:
container=480x746, TextureView=420x746

camera 1, D=270, pipeline A and pipeline B:
container=782x444, raw TextureView=440x782, visual viewport=782x440
```

### Evidence

```text
build/camera-orientation-evidence/dcam-front-a-d0-transition.png
build/camera-orientation-evidence/dcam-front-a-d0-layout-fix.png
build/camera-orientation-evidence/dcam-front-a-d270-paper.png
build/camera-orientation-evidence/dcam-front-a-d270-restart-switch.png
build/camera-orientation-evidence/dcam-front-b-d270-layout-fix-final.png
build/camera-orientation-evidence/dcam-orientation-final-pass.png
```

Screenshots were reviewed manually. No automated image processing or comparison was used. Every physical orientation change was performed manually by the user after an explicit stop-and-confirm step. No API, ADB, shell, emulator, or window-manager command rotated the device.

### Validation

```text
:app:testDebugUnitTest --tests com.dvid.dcam.platform.camera.shared.SharedCameraGatewaySourceTest --tests com.dvid.dcam.platform.camera.shared.PreviewSizeCalculatorTest
BUILD SUCCESSFUL

:app:assembleDebug
BUILD SUCCESSFUL
```
## 30. Video Encoder Transform Validation - 2026-08-01

### Scope and Result

* Camera 1 pipeline B video encoder transform result: `PASS` in all four physical device orientations.
* Camera 0 pipeline A video regression result: `PASS` at `D=0`.
* Preview and JPEG behavior were not changed by this fix.
* Encoded video pixels remain source-oriented: `Q_video=0`.
* Container metadata remains the residual transform: `V=N(O-Q_video)=O`.

### Build Identity

```text
APK: app/build/outputs/apk/debug/app-debug.apk
versionName: 1.0
versionCode: 1
SHA-256: 68FDB7ABABAE96ACCE5367E5095969DE64ECA13D1D34EC76957C9BA2D6BD57AA
size: 7191431 bytes
built: 2026-08-01 19:59:53 Asia/Saigon
installed update: 2026-08-01 20:02:31 Asia/Saigon
installed APK SHA-256: 68FDB7ABABAE96ACCE5367E5095969DE64ECA13D1D34EC76957C9BA2D6BD57AA
```

### Failure Evidence and Responsible Stage

The failing camera 1 pipeline B sample was:

```text
DCAM_000005_B01OPR_20260801_194907.mp4
video pixels: 1280x720
SAR: 1:1
video duration: 1.970622 s
container duration: 3.114667 s
ffprobe rotation: +90
Android target O: 270
```

Manual review of the raw frame showed that the full preview `SurfaceTexture` transform had already baked preview rotation and mirror into the landscape encoder canvas. The portrait scene was stretched into `1280x720`; normal playback then applied valid orientation metadata again. This isolated the failure to the pipeline B EGL encoder input transform, not `O`, container metadata, preview, or JPEG.

Pipeline B now keeps the measured `SurfaceTexture` matrix for preview and derives a crop-only encoder matrix. Preview-only rotation and mirror are not baked into encoded pixels. Camera ID, lens facing, sensor orientation, and display rotation remain runtime inputs; no camera-specific angle or mirror value is hardcoded.

### Runtime Video Matrix

| Physical top edge | Camera | Pipeline | `D` | `S` | `O` | MP4 | ffprobe rotation | Manual result |
|---|---:|---|---:|---:|---:|---|---:|---|
| Device top | `1` front | B | `0` | `270` | `270` | `DCAM_000005_B01OPR_20260801_200358.mp4` | `+90` | `PASS` |
| Device left | `1` front | B | `270` | `270` | `180` | `DCAM_000005_B01OPR_20260801_200634.mp4` | `-180` | `PASS` |
| Device bottom | `1` front | B | `180` | `270` | `90` | `DCAM_000005_B01OPR_20260801_200830.mp4` | `-90` | `PASS` |
| Device right | `1` front | B | `90` | `270` | `0` | `DCAM_000005_B01OPR_20260801_200955.mp4` | none | `PASS` |
| Device top | `0` back | A | `0` | `0` | `0` | `DCAM_000005_B01OPR_20260801_201314.mp4` | none | `PASS` |

For 90-degree metadata, ffprobe reports the opposite sign from the Android orientation-hint convention. Each normal frame resolved to physical `UP`, `DOWN`, `LEFT`, and `RIGHT`; no tested video was stretched or mirrored. Camera 1 parity matched the unmirrored JPEG/video contract. Camera 0 pipeline A remained unchanged and passed regression.

### Evidence

```text
build/camera-orientation-evidence/DCAM_000005_B01OPR_20260801_194907.mp4
build/camera-orientation-evidence/DCAM_000005_B01OPR_20260801_194907-raw.png
build/camera-orientation-evidence/DCAM_000005_B01OPR_20260801_194907-normal.png
build/camera-orientation-evidence/DCAM_000005_B01OPR_20260801_200358.mp4
build/camera-orientation-evidence/DCAM_000005_B01OPR_20260801_200358-raw.png
build/camera-orientation-evidence/DCAM_000005_B01OPR_20260801_200358-normal.png
build/camera-orientation-evidence/DCAM_000005_B01OPR_20260801_200634.mp4
build/camera-orientation-evidence/DCAM_000005_B01OPR_20260801_200634-raw.png
build/camera-orientation-evidence/DCAM_000005_B01OPR_20260801_200634-normal.png
build/camera-orientation-evidence/DCAM_000005_B01OPR_20260801_200830.mp4
build/camera-orientation-evidence/DCAM_000005_B01OPR_20260801_200830-raw.png
build/camera-orientation-evidence/DCAM_000005_B01OPR_20260801_200830-normal.png
build/camera-orientation-evidence/DCAM_000005_B01OPR_20260801_200955.mp4
build/camera-orientation-evidence/DCAM_000005_B01OPR_20260801_200955-raw.png
build/camera-orientation-evidence/DCAM_000005_B01OPR_20260801_200955-normal.png
build/camera-orientation-evidence/DCAM_000005_B01OPR_20260801_201314.mp4
build/camera-orientation-evidence/DCAM_000005_B01OPR_20260801_201314-raw.png
build/camera-orientation-evidence/DCAM_000005_B01OPR_20260801_201314-normal.png
```

Frames were extracted from MP4 files and reviewed manually. No automated image processing or comparison was used. Every physical orientation change was performed manually by the user after an explicit stop-and-confirm step. No API, ADB, shell, emulator, or window-manager command rotated the device.

### Validation

```text
:app:testDebugUnitTest
BUILD SUCCESSFUL

:app:assembleDebug
BUILD SUCCESSFUL
```