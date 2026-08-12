# Remove dcim/config.cson from serial build

## Status
In progress — DCAM production live Segmented AES-GCM

## Objective
Loại bỏ CSON legacy khỏi serial và media-encryption config logic; password encryption chỉ lấy từ build; nghiên cứu cách mã hóa recording recovery-safe mà không cần đồng thời một bản plain và một bản encrypted toàn phần.

## Contract

### Must
- Xác định mọi chỗ logic build serial đọc, sao chép, sinh, hoặc phụ thuộc `dcim/config.cson`.
- Gỡ đúng phụ thuộc đó với thay đổi nhỏ nhất.
- Giữ kiểm tra lỗi cần thiết và hành vi build khác.
- Chạy kiểm tra tập trung sau chỉnh sửa.
- Gỡ mọi migration encryption từ `Config/dcam_config.cson` và public `DCIM/configs.cson`.
- Password encryption luôn là giá trị truyền từ `BuildConfig.BODYCAM_CRYPTO_PASSWORD`; không lưu hoặc đọc password từ preference/CSON.
- Giữ preference enable/disable hiện tại; user chỉ đổi nguồn password.
- Nghiên cứu giải pháp recording encryption có recovery rõ ràng và peak storage không x2 toàn bộ recording.

### Must not
- Không đổi semantics của build không phải serial.
- Không xóa `dcim/config.cson` nếu tệp còn phục vụ luồng khác.
- Không sửa test để che thay đổi hành vi ngoài yêu cầu.
- Không tự đổi container, định dạng artifact, hoặc decryption contract trong pha nghiên cứu.
- Không đề xuất `plain.tmp` và `_enc.tmp` cùng tồn tại ở kích thước toàn recording.
- Không đổi recorder lifecycle, MP4 finalization, hoặc capture recovery đã được user xác nhận safe.

## Evidence

### Facts
- Yêu cầu trực tiếp: loại bỏ `dcim/config.cson` khỏi logic build serial.
- Trước thay đổi, `FileDeviceSerialNumberStore.restoreIfAvailable()` ưu tiên DB, `Config/dcam_config.cson`, bản sao SD, rồi mới lấy `account.user_id` từ cấu hình legacy.
- Trước thay đổi, `AppComposition` truyền `storage.legacyAccountConfigFile()` vào serial store.
- Runtime path của cấu hình legacy hiện là public `DCIM/configs.cson`; test legacy dùng `DCIM/config.cson`.
- `legacyAccountConfigFile()` vẫn được dùng riêng bởi `AndroidMediaEncryptionPreferenceStoreImpl`, nên không được xóa khỏi storage.
- Trước thay đổi, `docs/local-dev/current-repo/current-state.md` mô tả fallback legacy này.
- `AndroidMediaEncryptionPreferenceStoreImpl` lưu trạng thái runtime trong SharedPreferences `dcam_media_encryption`, không dùng CSON làm runtime store.
- CSON chỉ được đọc bởi `migrateLegacyConfigs()` để nhập một lần `video.file.encryption` / `file.encryption` và `video.file.encrypt_password` / `file.encrypt_password`.
- `AppComposition` truyền `Config/dcam_config.cson` và public `DCIM/configs.cson` làm hai nguồn migration legacy.
- Test migration xác nhận marker `legacy_cson_migrated`, retry khi đọc lỗi, và idempotency.

### Hypotheses
- Đã bác bỏ: đây không phải build script; đây là fallback dựng serial khi khởi tạo identity.

## Phased Plan
- [x] Inspect — xác định fallback legacy, call site, test, và tài liệu liên quan.
- [x] Consider solutions — chọn gỡ toàn bộ dependency legacy khỏi serial store.
- [x] Implement — đã gỡ dependency legacy và cập nhật regression test.
- [x] Confirm — diff, encoding, compile, và test tập trung đều đạt.

## User Correction — 2026-08-10
- User hỏi: media encryption liên quan gì tới `config.cson`; kết luận “giữ `legacyAccountConfigFile()` cho media encryption” chưa được chứng minh trong lượt trước.
- Premise-derived product changes: không có; code encryption và storage path đã tồn tại trước nhiệm vụ.
- Premise-derived task state: Decisions, Completion Notes, và final summary đã coi dependency encryption là lý do giữ path; phải kiểm chứng và sửa các kết luận đó.
- Regression test DCIM hiện chỉ bảo vệ contract serial không đọc legacy config; chưa xác nhận policy encryption.
- Follow-up phases: [x] inspect encryption owner; [x] consider corrected scope; [x] remove migration; [x] validate; [x] research design; [x] conclude.

## Extended Request — 2026-08-10
- User yêu cầu gỡ luôn CSON migration khỏi encryption.
- Encryption password phải dùng password truyền lúc build.
- Cần nghiên cứu cách mã hóa khi quay, recovery-safe, không tốn x2 dung lượng do plain temp rồi encrypted temp.
- User correction: recorder hiện đã recovery-safe; không redesign recorder hoặc muxer. Vấn đề bắt đầu sau khi recording hoàn tất và encryption bật.
- `BodycamMediaCrypto.encryptFileInPlace()` hiện tạo `dcam-media-*.tmp`, transform toàn file, rồi replace staging file; peak scratch gần bằng file recording.
- `DcamStagedMediaRecovery` giữ staged `_enc` nếu `.enc-complete` chưa có; không tự resume transform dở dang.
- Current crypto là AES-256-CTR với zero IV, không có authenticated tag hoặc format version.
- Trước extended change, `AppComposition` truyền `mediaEncryptionPreferences::mediaEncryptionPassword` cho camera/audio; password có thể đến từ migrated CSON.
- Current slice: thay thế full-size encrypted temp bằng conversion có bounded scratch space và recovery state rõ.
- Extended phases: [x] inspect password/capture pipeline; [x] remove migration; [x] validate; [x] research design; [x] conclude.

## Considered Solutions
1. Chỉ bỏ nhánh fallback trong `restoreIfAvailable()`: diff nhỏ hơn nhưng để lại constructor, field, và dependency chết.
2. Bỏ dependency legacy khỏi serial nhưng giữ path cho encryption migration: đã áp dụng cho slice đầu, sau đó bị user correction thay thế.
3. Xóa luôn đường dẫn legacy khỏi storage: được chọn sau khi user yêu cầu gỡ migration encryption.
4. Direct camera-to-GCM streaming: không chọn vì recorder/muxer nằm ngoài phạm vi và MP4 writer hiện cần seekable file.
5. Simple in-place GCM overwrite không rollback chunk: không chọn vì torn write có thể phá cả plaintext lẫn ciphertext.
6. Bounded-scratch in-place conversion với one-chunk rollback: phù hợp storage/recovery; GCM v2 cần decoder contract mới.

## Decisions
- Chọn phương án 2: serial chỉ lấy từ DB, `Config/dcam_config.cson`, hoặc SD identity backup.
- Sửa kết luận lượt đầu: public DCIM config không cần cho engine mã hóa; nó chỉ còn là nguồn migration preference legacy.
- User đã yêu cầu gỡ migration encryption.
- Xóa `legacyConfigs`, password preference, migration parser, và `legacyAccountConfigFile()` call/method.
- Truyền `() -> BuildConfig.BODYCAM_CRYPTO_PASSWORD` trực tiếp cho video/audio encryption.
- Không đổi recorder hoặc crypto format trong pha này; streaming/chunked conversion là research/design slice riêng.
- Thay test migration legacy bằng test hồi quy xác nhận file DCIM không cấu hình serial.

## Changes Made
- Đã sửa lỗi định dạng ở đuôi nhật ký do biểu thức cập nhật PowerShell; không có file sản phẩm bị ảnh hưởng.
- Xóa key, field, constructor overload, và fallback `account.user_id` khỏi `FileDeviceSerialNumberStore`.
- `AppComposition` không còn truyền `legacyAccountConfigFile()` vào serial store.
- Test serial xác nhận cấu hình DCIM không phục hồi serial; test SD backup không còn dựng legacy input.
- Cập nhật current-state để bỏ mô tả migration fallback.
- Removed encryption CSON migration and password preference from `AndroidMediaEncryptionPreferenceStoreImpl`.
- `AppComposition` now validates and supplies `BuildConfig.BODYCAM_CRYPTO_PASSWORD` directly to video/audio.
- Removed unused `DcamStorage.legacyAccountConfigFile()` and updated source guards.

## Validation Results
- `git diff --check -- <touched paths>`: đạt, không có whitespace error.
- `.\gradlew.bat :app:testDebugUnitTest --tests "com.dvid.dcam.platform.config.FileDeviceSerialNumberStoreTest"`: đạt.
- JUnit XML: 16 tests, 0 failures, 0 errors, 0 skipped.
- Static dependency check: serial và encryption production code không còn `legacyAccountConfigFile`, CSON migration, hoặc password preference.
- Mojibake scan và numeric-entity scan trên file chạm: không có match.
- Final focused regression set: 57 tests, 0 failures, 0 errors.
- Compile debug main/test sources: đạt.
- Final `git diff --check`, UTF-8/BOM/line-ending/trailing-whitespace, mojibake, numeric-entity, và static dependency checks: đạt.
- Broad `MainActivityStartupFlowTest` run exposed unrelated failure `cameraSwitchStateUsesEventsInsteadOfClockPolling()` at line 77; changed method `compositionUsesDeviceIdentityAndBuildCryptoSources()` passed when isolated.

## Recording Encryption Research

### Verified Current Flow
- `DcamMediaOutputImpl.finalizeCleanVideo()` patches finalized MP4 metadata, then `BodycamMediaCrypto.encryptFileInPlace()` encrypts, writes `.enc-complete`, and publishes.
- `BodycamMediaCrypto.encryptFileInPlace()` currently creates a full-size transformed temp file and replaces staging afterward; it is not true in-place encryption.
- `DcamStagedMediaRecovery` preserves `_enc` staging without `.enc-complete` because bytes may be plaintext, ciphertext, or an interrupted transform.
- `DcamInterruptedMp4Finalizer.finalizeInterrupted()` currently creates a full-size repair copy before replacing the staged MP4; clean-stop `finalizeCleanTimed()` patches the file in place.

### Corrected Compatibility Boundary
- Legacy `_enc` remains raw whole-file AES-256-CTR with SHA-256(password) and fixed zero IV. Existing BDMA decoder remains read-only compatibility code.
- New recordings may use a new authenticated GCM envelope and a new decoder. Legacy CTR framing no longer constrains new files.
- New files keep the same `_enc` filename convention. Primary and mirror `DCAM-GCM-MEDIA\r\n` header magic identify the new envelope; no `_enc2` suffix or public v2 name is introduced.
- User correction invalidates the prior CTR-first decision. Premise-derived product changes: none; only research conclusions changed.

### Corrected Recorder Constraint
- Keep current recorder lifecycle, `FragmentedMp4Muxer`, audio muxing, logical MP4 offsets, 1 MiB seek reservation, close-time index writes, duration patching, and interrupted-capture recovery semantics.
- Live GCM must sit below these algorithms as a transparent seekable logical file. Do not simplify the MP4 layout to make encryption append-only.
- Physical storage may be append-only even when logical MP4 writes seek backward. Random logical rewrites append a new encrypted generation instead of overwriting old ciphertext.

### Selected Live Architecture
Use a log-structured, seekable segmented-GCM media store:

```text
recorder / MP4 finalizer
        |
        | plaintext logical reads, writes, seek, truncate
        v
DcamLogicalMediaChannel
        |
        | fixed logical blocks + transactions
        v
physical _enc file: DCAM-GCM header + append-only encrypted records
```
- Recorder sees the same plaintext byte positions and behavior as today.
- Disk contains only GCM ciphertext records, tags, authenticated metadata, and at most bounded plaintext RAM buffers.
- No full plaintext media file and no second full encrypted file exist.

### Minimal Logical I/O Contract
`DcamFragmentedMp4Layout` currently needs only position get/set, write, open/close, and durability. `DcamInterruptedMp4Finalizer` needs seek, length, truncate, primitive reads/writes, and force.
- Introduce one small random-access media interface covering those exact operations.
- Plain implementation wraps `FileChannel`/`RandomAccessFile`.
- GCM implementation exposes a decrypted logical view backed by encrypted block records.
- Keep MP4 parsing, duration math, fragment trimming, audio data, and seek-index algorithms unchanged; replace only storage access.

### Same-Name Physical Format
New files keep `_enc` and begin with clear `DCAM-GCM` magic. Legacy raw CTR has no valid magic.

```text
[DCAM-GCM header]
  file ID, KDF salt, logical block size, authenticated header hash

[DATA record]
  transaction ID, logical block index, generation, plaintext length
  fresh random 96-bit nonce, ciphertext, 128-bit GCM tag

[COMMIT record]
  transaction ID, resulting logical length, committed DATA references/hash
  authentication tag

[FINAL manifest]
  logical length, latest block generations/offsets, completion state
  authentication tag and duplicate DCAM-GCM marker
```
- AAD binds header hash, file ID, transaction ID, block index, generation, logical offset, and plaintext length.
- Generate a fresh nonce for every physical DATA record. A rewrite always creates a new generation and nonce.
- Only DATA records referenced by a valid COMMIT become visible in the logical file.
- FINAL manifest accelerates BDMA reads and prevents silent truncation of completed media. Recovery can rebuild state by scanning valid committed records if FINAL is missing.

### Logical Block Behavior
- Sequential recorder writes fill fixed logical blocks and append encrypted DATA+COMMIT records.
- Seek-back writes decrypt the latest affected blocks, modify plaintext in RAM, then append new block generations in one transaction.
- Logical holes from the 1 MiB MP4 reservation remain authenticated implicit zero ranges until written; current MP4 offsets stay unchanged.
- Logical truncate appends a committed length update; physical tail records remain unreachable. No destructive disk truncate is required.
- Block size should be small enough to limit rewrite/loss, initially around 64–256 KiB, then fixed by device benchmark.

### Current Writer Mapping
- `SharedAvcEncoder` still sends the same buffers to `DcamFragmentedMp4Layout.OutputChannel`.
- `DcamFragmentedMp4Layout.reserveSeekIndex()` still advances the logical position and writes its marker.
- `writeRecordedSeekIndex()` still seeks into the reserved logical region. The GCM store commits updated block generations.
- Audio samples remain normal MP4 bytes inside the logical stream. Encryption does not split or reinterpret audio.
- File-size limiting must query projected physical bytes from the GCM store instead of raw `File.length()`.

### Current Finalizer Mapping
- Refactor `DcamInterruptedMp4Finalizer` I/O behind the minimal random-access interface; keep its box parsing, fragment detection, duration calculation, seek-index writing, and trim decisions.
- Clean finalization writes patched logical blocks in one transaction, then appends FINAL.
- Interrupted recovery writes all logical patches under one transaction. If any step fails or process crashes before COMMIT, prior committed logical state remains active.
- This transactional block log replaces the full-size repair copy for GCM files while preserving rollback behavior. Plain files may keep the current copy-based recovery.

### Live Crash Recovery
1. Detect `DCAM-GCM` header in the same `_enc` file.
2. Scan records; verify each complete GCM record. Apply sequential autocommit DATA immediately; ignore only the incomplete physical tail and transaction DATA lacking a valid COMMIT.
3. Rebuild latest committed logical block map and logical length.
4. Open decrypted logical random-access view and run current interrupted MP4 finalizer.
5. Commit duration/seek/truncate patches as one new transaction.
6. Append authenticated FINAL, force storage, write `.enc-complete`, then publish.
- Sequential DATA records do not wait for a global COMMIT. Recovery retains every complete authenticated record; loss remains limited to the MP4 fragment current recovery already considers incomplete. COMMIT activates non-tail rewrites, multi-record patches, and truncation atomically.
- Invalid committed interior records mean corruption/tampering; preserve and report. Do not skip them.

### User Correction — Live Fragment Parity
- User confirmed plain fragmented MP4 and live encrypted output must retain the same completed fragments after crash.
- Invalidated premise: “only COMMIT records expose writes” would discard valid sequential media records and could make encryption recovery worse than plain recording.
- Corrected contract: complete authenticated sequential DATA is immediately visible. A fragment checkpoint marks the latest complete MP4 fragment for live readers but does not control DATA visibility.
- Flush a partial tail-block generation before each MP4 fragment CHECKPOINT. Later appends may replace that same block with its preserved prefix plus next-fragment bytes; the earlier generation protects the completed fragment until the replacement authenticates.
- COMMIT activates non-tail rewrites, multi-record random-access changes, and truncation; one-block non-tail patches also require COMMIT.

### BDMA Self-Detection And Decryption
```text
if file starts with valid DCAM-GCM header:
    verify FINAL and committed record map
    decrypt latest logical block generations in logical-offset order
    output exact finalized MP4 bytes
else:
    attempt legacy raw CTR decrypt and require strict media validation
```
- Same `_enc` filename. No `_enc2`. No user-visible v2 mode.
- Detected GCM with invalid authentication never falls back to CTR.
- Header corruption plus failed legacy media validation reports unknown/corrupt instead of emitting garbage.

### Storage Budget
- One ciphertext record per committed logical block plus small headers/tags.
- Rewritten metadata blocks add only their newer generations; old generations provide rollback.
- No two-slot copy for every block and no file-sized duplicate.
- Expected overhead is tags/record headers, final manifest, a few rewritten metadata blocks, and uncommitted crash tail.

### Rejected Approaches
- Do not remove current seek reservation, duration patch, seek-index generation, audio path, or MP4 recovery.
- Do not wrap a sequential whole-file GCM stream around the current seekable writer.
- Do not overwrite existing GCM ciphertext in place with the same nonce.
- Post-finalize segmented GCM remains a simpler fallback, but user selected live segmented GCM.

### Revised Decision
- Primary architecture: live segmented AES-256-GCM through a log-structured seekable logical media store.
- Preserve current recorder and finalizer behavior; abstract only their storage I/O.
- New GCM content self-identifies inside the same `_enc` file with `DCAM-GCM` header/final manifest.
- Legacy raw CTR remains read-only for old `_enc` files.
- No product crypto or recorder code changed during this research turn.

### Security Constraints
- GCM requires unique nonces and mandatory tag verification.
- New per-file key derives from build secret plus random salt using an approved KDF; do not reuse the legacy zero-IV stream.
- COMMIT and FINAL metadata must be authenticated and length/count bounded before allocations or seeks.
- Build secret entropy remains separate; a human-memorable value needs password hardening.

### Revised Research Validation
- Enumerated actual writer/finalizer random-access calls; required abstraction is bounded to existing operations.
- Preserved current MP4 algorithms and moved crash atomicity into authenticated block transactions.
- Rechecked same-name format dispatch, legacy CTR boundary, GCM nonce/tag requirements, storage overhead, and recovery phases; final validation is recorded below.
- Confirm phase completed through interoperability tests, full unit suites, diff checks, and UTF-8 validation; independent review remains active.

### Exact Binary Contract — 2026-08-10
- Canonical contract exists at `docs/contracts/dcam-segmented-gcm-media.md`; identical copy exists in BDMA.
- File header is 96 bytes with primary and mirror `DCAM-GCM-MEDIA\r\n` magic, CRC32, block/KDF parameters, random salt, and random file ID.
- Records are append-only `[96-byte header][16-byte GCM tag][ciphertext]`; AAD is exact file header plus exact record header.
- DATA tail records autocommit. COMMIT activates contiguous transactional DATA, including one-block non-tail rewrites. CHECKPOINT marks a complete MP4 fragment. FINAL is required for published files.
- Deterministic cross-repo fixture hashes: encrypted `9d53238c68e15fa1ba326edfbcfde43f14eec15febe6f04b7b112596c3d12883`; plaintext `ff6a7b63623fba846c3e2079c4e004b3c03dbbd382e67a4b1ce8a2f7181d7d5e`. The vector includes zero-DATA truncate from 4,300 to 4,200 bytes inside the final block.
- DCAM codec: `app/src/main/java/com/dvid/dcam/platform/storage/DcamSegmentedGcmFormat.java`.
- DCAM proof: `DcamSegmentedGcmFormatTest` regenerates the fixture, decrypts autocommit + transaction state, and rejects a modified tag.
- Validation: `gradlew.bat :app:testDebugUnitTest --tests "com.dvid.dcam.platform.storage.DcamSegmentedGcmFormatTest"` passed on August 10, 2026.

### BDMA Dispatch Audit — 2026-08-10
- Fact: sync pull and startup transition already delegate to `BodycamCryptoService`, but `ExternalMediaDecryptWorker` still derives the zero-IV CTR key and calls `AesCtrCryptoService` directly.
- Decision: make `BodycamCryptoService` the single content-dispatch owner for all three paths; preserve external decrypt progress and cancellation through an overload.
- Fact: BDMA commit `2037ccd` deliberately removed general tests from tracking. This task keeps that default but adds narrow `.gitignore` exceptions for the crypto contract test and fixtures, then enables `useJUnitPlatform()` so CI executes them.
- Validation: tracked `BodycamCryptoServiceTest` passes content detection, byte-exact shared fixture with in-block truncate, tag failure without CTR fallback, mirror-magic downgrade prevention, required FINAL, PBKDF2 cancellation, lifecycle route ownership, and legacy CTR.

### Live GCM Follow-up Phases
- [x] Define seekable append-log GCM storage.
- [x] Map current writer seek and finalizer writes.
- [x] Correct sequential DATA visibility and fragment-boundary recovery parity.
- [x] Specify exact binary contract and shared DCAM/BDMA vectors.
- [x] Implement and validate DCAM contract codec.
- [x] Implement BDMA content detection and decryption.
- [x] Run interoperability and corruption tests.
- [x] Complete independent cross-review and final diff checks.

### Independent Review — 2026-08-10
- High finding confirmed: BDMA required final block generation length to equal logical tail length, so zero-DATA COMMIT truncation inside a block failed. Fix: coverage accepts an authenticated generation at least as long as exposed prefix; output writes only logical prefix.
- Medium findings addressed: crypto contract tests/fixtures are tracked and run through normal Gradle; lifecycle route ownership is regression-tested; DCAM round-trips a fragmented AV MP4 with seek reserve byte-for-byte. Full recorder crash/device playback lifecycle remains outside this non-wired slice.
- Medium finding addressed: contract now states same-tail block generations preserve prior prefix across CHECKPOINT and supports in-block truncate clipping.
- Low findings accepted for this slice: nonce/pending collection limits, smooth GCM progress beyond start/completion, and crypto callback package ownership remain future hardening. PBKDF2 now checks cooperative cancellation every 1,024 iterations.
- Final reviewer verdict after zero-DATA re-expansion fix: no high or medium findings; narrowed guard/regression slice has no remaining low finding.

### Implementation and Validation Update — 2026-08-10
- Superseded on August 11, 2026: the temporary shared `BodycamCryptoService` dispatcher was reverted. Final design keeps sync/startup on legacy `BodycamCryptoService` and gives ExternalMediaDecrypt explicit `SegmentedAesGcmCryptoService`-then-`AesCtrCryptoService` dispatch.
- Final name: `SegmentedAesGcmCryptoService`; it supports cooperative cancellation during PBKDF2, record scanning, and logical reconstruction. External AES-CTR progress behavior remains unchanged; Segmented AES-GCM reports start and completion.
- Contract now defines DCAM-only interrupted staging salvage: truncate at first invalid/torn tail record, discard an uncommitted final transaction, preserve earlier autocommit DATA, then run the existing MP4 finalizer. BDMA remains strict and requires FINAL.
- Shared binary/plain fixtures are tracked in DCAM test resources, BDMA test resources, and durable BDMA contract vectors; both contract copies include byte counts, PBKDF2 oracle key, and SHA-256 hashes.
- Relevant failed edits: the first multi-file strict edit wrote `BodycamCryptoService` before a later target mismatch; a PowerShell alias then caused two partial cancellation call-site edits; fixture regeneration first used the wrong Gradle test working directory. Exact-state corrections followed, temporary generator code was removed, and validation passed.
- Validation passed: BDMA `gradlew.bat test shadowJar`; focused tracked `BodycamCryptoServiceTest`; focused DCAM `DcamSegmentedGcmFormatTest`; full DCAM `:app:testDebugUnitTest`; fragmented AV + seek-reserve round-trip; shared truncate vector hashes match across repositories.
- Decoder hardening rejects authenticated logical lengths larger than the physical encrypted log before coverage loops or output reconstruction.
- BDMA `gradlew.bat build` remains blocked by pre-existing Gradle output wiring: `bootDistZip`, `bootDistTar`, and `bootStartScripts` consume `shadowJar` output without task dependencies. `gradlew.bat test shadowJar` passes; this task changes `build.gradle` only to enable JUnit Platform.

### Final Review Follow-up — 2026-08-10
- Second-review medium finding confirmed: after a zero-DATA COMMIT truncates inside a retained authenticated tail block, another zero-DATA COMMIT could increase logical length and re-expose bytes without new DATA.
- Decision: zero-DATA COMMIT may keep or reduce logical length only. BDMA must reject authenticated re-expansion without CTR fallback or output.
- Changes: both contract copies now forbid zero-DATA logical-length increase; BDMA regression appends authenticated COMMIT sequence 7 and FINAL sequence 8 after the shared truncate fixture and requires strict rejection.
- Focused validation: `D:\Projects\bdma\gradlew.bat test --tests "com.app.common.modules.crypto.services.BodycamCryptoServiceTest"` passed, including authenticated zero-DATA re-expansion rejection.
- Full validation: `D:\Projects\bdma\gradlew.bat test shadowJar` passed.
- Full validation: `D:\Projects\dcam\gradlew.bat :app:testDebugUnitTest` passed.
- Integrity checks: both contract copies are byte-identical; all encrypted fixture copies hash to `9d53238c68e15fa1ba326edfbcfde43f14eec15febe6f04b7b112596c3d12883`; all plaintext copies hash to `ff6a7b63623fba846c3e2079c4e004b3c03dbbd382e67a4b1ce8a2f7181d7d5e`.
- Final local audit: `git diff --check` passed in both repositories; touched text has UTF-8 without BOM, CRLF preserved, no trailing whitespace, mojibake, replacement characters, or numeric entities.
- Superseded dispatch audit: final ExternalMediaDecrypt owns explicit algorithm dispatch; sync and startup retain the pre-existing legacy `BodycamCryptoService` path.
- Phase status: inspect complete; consider solutions complete; implement complete; confirm complete.

## User Correction — 2026-08-11
- User requests no further computer shutdown action.
- `zero-DATA COMMIT` and primary/mirror magic terminology was not explained clearly enough.
- User challenges replacing the direct `AesCtrCryptoService` dependency with `BodycamCryptoService`; audit must distinguish required format dispatch from unnecessary naming churn.
- Clarified input contract: ExternalMediaDecrypt selects _enc; one selected file may be legacy whole-file CTR or segmented DCAM GCM, so suffix cannot choose decoder.
- User correctly identifies proof gap: production recorder does not emit segmented GCM, so contract vectors cannot prove real record-import encryption, playback, audio, or seek behavior end to end.
- Previous completion claim remains valid only for contract, codec proof, and BDMA decoder slice; it must not imply production live encryption works.
- Premise-derived product changes to review: `BodycamCryptoService` dispatcher ownership and external decrypt dependency naming. Do not change recorder behavior from assumptions.
- Evidence: `BodycamCryptoService` existed before this change as the legacy CTR wrapper; no class rename occurred. ExternalMediaDecrypt changed dependency type because one `_enc.mp4` input may require CTR or segmented GCM.
- Evidence: production `DcamMediaOutputImpl` still calls `BodycamMediaCrypto.encryptFileInPlace()`, and `DcamSegmentedGcmFormat` has no production reference.
- Evidence: shared interoperability plaintext is patterned bytes, not MP4. Separate DCAM AV/seek test proves codec byte preservation but does not pass through production recorder or BDMA ExternalMediaDecrypt.
- Decision: keep one content-dispatch owner; do not push format detection into `AesCtrCryptoService` or duplicate it in ExternalMediaDecryptWorker. Correct misleading documentation only.
- Decision: explicitly state that production segmented record-import remains unproven until recorder wiring and end-to-end validation exist.
- Changes: contract now defines zero-DATA COMMIT in plain language, defines primary/mirror magic as duplicate format signatures, states the both-magic-loss detection limit, and narrows no-fallback wording to files already classified as segmented GCM.
- Changes: `BodycamCryptoService` documentation now describes both supported encrypted bodycam formats; class and dependency ownership remain unchanged.
- Validation: both contract copies remain byte-identical; `git diff --check` passes in DCAM and BDMA; touched UTF-8 text has no BOM, mojibake, numeric entities, or trailing whitespace.
- Validation decision: crypto tests were not rerun because behavior and binary format did not change; edits are contract clarification and Javadoc only.
- Reopened phases: inspect complete; consider solutions complete; implement complete; confirm complete.

## Prior Completion Notes
- Required work: None.
- Contract, DCAM codec/proof, BDMA content detection/decryption, legacy CTR compatibility, shared vectors, and strict zero-DATA truncate semantics are complete and validated.
- Production recorder integration and DCAM live-viewer integration were intentionally outside this slice; current production recording encryption remains legacy until a separate implementation task wires the seekable live-GCM store.
- BDMA full `build` remains affected by pre-existing Gradle distro-task wiring; required `test shadowJar` validation passes.
## Completion Notes — 2026-08-11
- Required work: None for this terminology, naming, and proof audit.
- Superseded by the ExternalMediaDecrypt correction below: `BodycamCryptoService` no longer dispatches Segmented AES-GCM.
- Production segmented-GCM record-import is not proven. Current DCAM production encryption still uses `BodycamMediaCrypto.encryptFileInPlace()` CTR, while segmented format code remains test-only.
- Any future claim that live segmented recording/import works requires production recorder wiring plus end-to-end ExternalMediaDecrypt playback, audio, seek, and interruption validation.
## User Correction — 2026-08-11 ExternalMediaDecrypt Dispatch
- Exact input contract: ExternalMediaDecrypt receives an `_enc` media file that may be legacy AES-CTR or Segmented AES-GCM.
- ExternalMediaDecrypt must inspect Segmented AES-GCM magic first. If absent, it must continue through the existing AES-CTR implementation and behavior.
- Algorithm owners and identifiers must state algorithms explicitly: `AesCtr` and `SegmentedAesGcm`. `Bodycam` and `DCAM` do not distinguish algorithms because both formats run on bodycam devices.
- Invalidated decision: extending `BodycamCryptoService` into the shared GCM/CTR content dispatcher and routing ExternalMediaDecrypt through it.
- Premise-derived changes to revert or replace: `BodycamCryptoService` GCM dependency/dispatch/progress overload, ExternalMediaDecrypt service/worker `BodycamCryptoService` dependency, lifecycle route guard expecting all paths through that service, and `DcamSegmentedGcmDecryptor` naming.
- Preserve: existing `BodycamCryptoService` legacy sync/startup AES-CTR behavior; existing `AesCtrCryptoService`; segmented binary contract, strict no-fallback after magic detection, vectors, and decoder validation.
- Evidence: pre-change `BodycamCryptoService` is the legacy media-level AES-CTR wrapper used by sync/startup. ExternalMediaDecrypt alone called `AesCtrCryptoService` directly.
- Selected solution: restore `BodycamCryptoService` exactly to legacy CTR behavior; rename new format owner to `SegmentedAesGcmCryptoService`; inject `AesCtrCryptoService` plus `SegmentedAesGcmCryptoService` into ExternalMediaDecrypt; check magic first, otherwise execute the original CTR helper unchanged.
- Expected BDMA files: `BodycamCryptoService.java`, `SegmentedAesGcmCryptoService.java`, ExternalMediaDecrypt service/worker, two focused tests, `.gitignore`, and contract copy.
- Changes applied: `BodycamCryptoService` restored to HEAD legacy CTR behavior; new class renamed `SegmentedAesGcmCryptoService`; ExternalMediaDecrypt service/worker now inject both algorithm-specific services and branch on segmented magic before the original CTR helper.
- Regression files split into `SegmentedAesGcmCryptoServiceTest` and `ExternalMediaDecryptWorkerCryptoDispatchTest`; old `BodycamCryptoServiceTest` removed.
- Focused validation passed on August 11, 2026: `gradlew.bat test --tests "com.app.common.modules.crypto.services.SegmentedAesGcmCryptoServiceTest" --tests "com.app.common.modules.externalmediadecrypt.workers.ExternalMediaDecryptWorkerCryptoDispatchTest"`.
- Regression strengthened: ExternalMediaDecrypt worker now decrypts the real shared Segmented AES-GCM fixture, rejects a detected tampered fixture without touching AES-CTR, and decrypts a real legacy AES-CTR fixture when magic is absent.
- Focused validation rerun passed after strengthening worker tests.
- Full validation passed on August 11, 2026: `gradlew.bat test shadowJar`.
- Final behavior review: magic match invokes only `SegmentedAesGcmCryptoService`; detected Segmented AES-GCM failure never invokes AES-CTR; absent magic executes the original AES-CTR derivation/decrypt/clear path.
- Final architecture review: `BodycamCryptoService` has no working-tree diff and remains legacy-only; format detection has one production owner in ExternalMediaDecrypt; no stale `DcamSegmentedGcmDecryptor`, `isSegmentedGcm`, or `BodycamCryptoServiceTest` references remain.
- Final integrity review: BDMA and DCAM `git diff --check` pass; contract/vector copies are identical; touched text is UTF-8 without BOM, mojibake, numeric entities, or trailing whitespace.
- Remaining low-risk debt: Segmented AES-GCM progress reports start/completion only. Recorder integration remains intentionally outside this BDMA slice.
- Required work: None for the BDMA ExternalMediaDecrypt dispatch slice.
- Phases: inspect complete; consider solutions complete; implement complete; confirm complete.

## DCAM Production Live Segmented AES-GCM — 2026-08-11

### Status
Complete — DCAM implementation and scoped validation passed; repository-wide rerun has two unrelated architecture failures.

### Objective
Wire production DCAM video and standalone AAC recording to Segmented AES-256-GCM without a full-size plaintext temp, while preserving existing fragmented-MP4 recovery, audio, playback, duration, and seek behavior after decryption.

### Contract

#### Must
- Encrypt live through a seekable logical plaintext view backed by append-only authenticated GCM records.
- Keep `_enc`; BDMA detects primary or mirror magic and otherwise uses legacy AES-CTR.
- Use `BuildConfig.BODYCAM_CRYPTO_PASSWORD` as the recording key input.
- Preserve completed MP4 fragments across crash; only the currently incomplete fragment may be discarded.
- Preserve MP4 seek reservation, writer index, duration patch, interrupted finalizer, video audio track, and standalone AAC framing.
- Publish only after durable authenticated FINAL, or after the same live output handle successfully writes FINAL.
- Avoid a plaintext file-sized temp and avoid a second ciphertext-sized publication copy.

#### Must not
- Do not create `_enc2` or rename existing legacy crypto owners.
- Do not replace Media3 muxer, fragment rules, audio timing, seek/index semantics, or legacy AES-CTR compatibility.
- Do not fall back to AES-CTR after Segmented AES-GCM content detection.
- Do not change unrelated UI, rotation, release-note, or CI behavior.

### Evidence

#### Facts
- BDMA ExternalMediaDecrypt dispatch and shared fixtures passed `test shadowJar` before this DCAM slice.
- Camera video now receives `DcamRecordingOutput` from media lifecycle; standalone AAC uses the same output abstraction.
- Segmented video uses 64 KiB logical blocks; standalone AAC uses 4 KiB blocks to bound checkpoint rewrite overhead.
- Fragment checkpoint occurs only after all declared `mdat` bytes are written.
- Seek-back index and duration patches execute as authenticated logical transactions.
- Interrupted GCM recovery opens the logical media view, finalizes MP4 or trims incomplete ADTS, validates playback, writes FINAL, then same-filesystem moves the ciphertext.
- GCM recovery MD5 work runs after publication through the existing retry owner; MD5 failure no longer misreports a moved file as preserved in Temp.
- Failed standalone AAC paths release active staging reservations, allowing same-process recovery.

#### Hypotheses
- No active implementation hypothesis remains.
- Physical bodycam performance and playback remain device-validation evidence, not assumed facts from JVM tests.

### Phased Plan
- [x] Inspect — mapped recorder, muxer, finalizer, recovery, publication, password, audio, and capacity ownership.
- [x] Consider solutions — selected one seekable recording handle over plain or append-log GCM storage.
- [x] Implement — connected storage core, camera, AAC, finalization, recovery, and publication.
- [x] Confirm — focused compile/tests and diff/UTF-8 checks passed; repository-wide rerun isolated to an unrelated MainActivity rotation import.
- [x] Review — initial findings were fixed; local behavior and architecture passes found no unresolved high or medium issue. Follow-up reviewer timed out and was shut down.

### Considered Solutions
- Rejected post-finalize in-place GCM: crash can destroy the only recoverable media and ciphertext expansion prevents safe same-length overwrite.
- Rejected forward-only GCM wrapper: current MP4 writer seeks backward and truncates.
- Rejected full plaintext/ciphertext duplicate: violates storage contract.
- Selected append-only Segmented AES-GCM logical block generations with autocommit tail DATA, transactional rewrites/truncate, CHECKPOINT, and FINAL.

### Decisions
- `DcamRecordingOutput` owns plain or Segmented AES-GCM random-access output.
- Complete sequential tail DATA is immediately visible after tag validation; CHECKPOINT protects completed fragment boundaries.
- COMMIT activates random-access rewrites and truncate atomically.
- Live video finalization trusts the same open output handle only after `finish()` succeeds; detached publication uses strict `openPublished()` validation.
- Recovery publication uses clean move; optional video MD5 runs after move through retry callback.
- Legacy `BodycamMediaCrypto` remains for old AES-CTR media/photos only.

### Changes Made
- Added `DcamRandomAccessMedia`, `DcamRecordingOutput`, `DcamSegmentedGcmFormat`, and `SegmentedAesGcmMediaStore`.
- Routed camera and standalone AAC writes through `DcamRecordingOutput`.
- Adapted MP4 layout, interrupted finalizer, validator, recovery, and size limiter to logical media I/O.
- Added GCM-aware recovery, FINAL publication checks, autocommit prefix verification, audio reservation release, and post-move MD5 retry handling.
- Kept BDMA-recognized format, `_enc` filename, legacy AES-CTR fallback boundary, audio/video bytes, and seek/index behavior unchanged.

### Regression Proof
- Fragment checkpoint waits for complete split `mdat` and keeps output open for logical finalization.
- Segmented MP4 clean finalization rolls back all logical patches on failure.
- Unfinalized Segmented AES-GCM cannot publish through detached publication.
- Authenticated autocommit cannot rewrite committed tail prefix; recovery retains prior bytes.
- MD5 failure after clean move still reports recovered/resolved media.
- Runtime passes prepared `DcamRecordingOutput` ownership to camera pipeline.
- Failed standalone AAC paths release active staging reservation.
- Encrypted AAC/video round-trip byte-for-byte; recovered video keeps audio, duration, and seek metadata.

### Validation Results
- `gradlew.bat :app:compileDebugJavaWithJavac :app:compileDebugUnitTestJavaWithJavac` — BUILD SUCCESSFUL.
- Focused storage, recovery, media-output, camera-runtime, and source-contract tests — BUILD SUCCESSFUL.
- `gradlew.bat :app:testDebugUnitTest` — BUILD SUCCESSFUL.
- Prior `gradlew.bat test` run — BUILD SUCCESSFUL.
- 2026-08-11 current `gradlew.bat test` rerun — FAILED only in `LayerDependencyTest.onlyCompositionAndFrameworkEntriesImportPlatformFromApp` and `LayerDependencyTest.uiAndMainActivityDoNotImportPlatform`; both report the unrelated working-tree import `MainActivity -> platform.camera.shared.CameraOrientation`, outside this encryption task.
- Current focused compile plus nine DCAM storage, camera, capacity, and startup test classes — BUILD SUCCESSFUL.
- Path-scoped `git diff --check` — clean.
- Strict UTF-8, trailing-whitespace, mojibake, and numeric-entity scan — clean.

### Independent Review
- Initial review found one high issue: failed AAC reservation ownership.
- Initial review found medium issues: post-move MD5 failure reporting, detached GCM publication without strict FINAL, and missing autocommit prefix verification.
- All findings received focused fixes and regression tests; prior full suites passed before unrelated rotation edits.
- Requested follow-up reviewer did not return before shutdown; two local passes inspected lifecycle/security and architecture/testability, with no unresolved high or medium issue.

### Completion Notes
- Required work: None for DCAM repository implementation.
- Repository-wide rerun remains red only for the unrelated `MainActivity -> platform.camera.shared.CameraOrientation` dependency; no outside-scope fix applied.
- Device evidence gap: no physical bodycam capture, crash/reboot, DCAM viewer, or BDMA desktop playback run was available in CLI. Release sign-off still needs encrypted video with audio, force-stop/reboot recovery, BDMA import, and play/seek/audio verification.
- DCAM Media Viewer direct Segmented AES-GCM decryption was not wired in this slice; contract covers live recorder/recovery and BDMA import/decryption.
- Remaining evidence gap: no physical bodycam capture, crash/reboot, DCAM viewer, or BDMA desktop playback run was available in CLI. Device acceptance should record encrypted video with audio, force-stop/reboot during capture, recover, import in BDMA, and verify play/seek/audio before release sign-off.
