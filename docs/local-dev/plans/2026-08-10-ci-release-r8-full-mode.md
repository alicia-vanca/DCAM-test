status: complete

# Objective
Điều chỉnh CI để mọi APK gửi bộ phận test là release, đã ký bằng cùng một key, bật minify/shrink và R8 full mode; giữ nguyên Advanced Settings, BuildConfig loop và media-encryption toggle.

# Contract
## Must
- CI build đúng release variant cho APK test.
- Release cấu hình `isDebuggable = false`, `isMinifyEnabled = true`, `isShrinkResources = true`.
- Bật R8 full mode theo cấu hình Gradle/R8 tương thích dự án.
- Giữ behavior hiện có của hidden devmode trong release APK và đổi header user-visible thành `Advanced Settings`.
- Không tự ý loại code hoặc setting khi chưa có evidence; hiện chưa xác nhận code dev nào cần xóa khỏi release.
- Giữ behavior production; existing GitHub Actions test/build success là validation đủ, không thêm artifact gate riêng.

## Must not
- Không đổi business behavior ngoài yêu cầu hardening build.
- Không xóa hoặc vô hiệu hóa Advanced Settings; không đổi behavior `MEDIA_ENCRYPTION` toggle.
- Không làm yếu keep rule hoặc test để che lỗi shrink/obfuscation.
- Không đưa debug-only endpoint/probe, credential công cụ nội bộ, hoặc log nhạy cảm vào release; hidden devmode là ngoại lệ được giữ.
- Không kết luận nguyên nhân trước khi đối chiếu Gradle, CI và artifact thực tế.

# Evidence
## Facts
- Yêu cầu người dùng nêu rõ release APK cho bộ phận test, ba cờ release, R8 full mode, và loại code dev.
- Người dùng xác nhận lần hai: devmode phải giữ nguyên và tiếp tục nằm trong release APK; diễn giải trước đó chỉ giữ ở debug/internal là sai.
- User correction ngày 2026-08-10: giữ vòng lặp mọi property trong `app/build.gradle`; không áp dụng BuildConfig allowlist trong task này.
- `ATLASSIAN_TOKEN`, `ATLASSIAN_EMAIL`, `CONFLUENCE_BASE_URL` thuộc ignored local file và không được CI cung cấp; giữ nguyên cơ chế property hiện tại.
- “Dev code” trong yêu cầu sếp nghĩa là test/debug bypass như tắt mã hóa data, tắt ẩn thư mục hoặc hạ production safeguard; không đồng nghĩa hidden devmode hiện tại.
- Hidden devmode hiện tại phải đổi header thành `Advanced Settings`; behavior còn lại giữ nguyên.
- Premise-derived plan cần sửa: bỏ Slice BuildConfig allowlist, đổi devmode classification sang Advanced Settings và map release-only production safeguards. Không có product code nào đã sửa từ premise cũ.
- Final user decision ngày 2026-08-10: dùng một signing key cho cả `develop` và `release`.
- Final user decision: giữ secret trong `BuildConfig`; không triển khai Keystore/provisioning migration.
- Final user decision: chưa có evidence code dev cần loại; `MEDIA_ENCRYPTION` là setting có thể toggle và phải giữ behavior.
- Final user decision: không thêm artifact gate, device validation hoặc verifier riêng; GitHub Actions build flow là đủ.
- Final user correction ngày 2026-08-10: cloud không liên quan `application-local.properties`; workflow environment truyền cả `BODYCAM_CRYPTO_PASSWORD` và `LOGGLY_TOKEN`, còn `DEFAULT_STORAGE_MODE` không thuộc secret flow.
- Source fact: `application.properties` đã khai báo `LOGGLY_TOKEN`, `BODYCAM_CRYPTO_PASSWORD` và default `DEFAULT_STORAGE_MODE=APP_DATA`.
- Current source fact: workflow truyền `BODYCAM_CRYPTO_PASSWORD` qua environment nhưng line 44 ghi `LOGGLY_TOKEN` vào `application-local.properties`; user xác nhận line 44 là flow sai cần xóa.
- Corrected cloud flow: GitHub Secret → workflow environment → Gradle `propertyValue(key)` → generated `BuildConfig` field → app code; cloud build không đọc hoặc ghi `application-local.properties`.
- Invalidated premise: giữ line 44. Premise-derived text trong Decisions, Slice 3, Slice 5, validation và completion phải sửa.
- Ảnh chụp cho thấy APK hiện dễ dịch ngược, có dấu hiệu debug build, tên symbol còn rõ, `android:debuggable="true"`, `BuildConfig.DEBUG=true`, và logic nằm trong DEX.
- GitHub Actions build `assembleRelease` trên nhánh `release`, nhưng build `assembleDebug` trên nhánh `develop` rồi vẫn upload vào thư mục artifact `build/release`.
- `app/build.gradle` dùng Android Gradle Plugin 9.2.1 và release đang đặt `optimization.enable = false`; chưa khai báo `isDebuggable`, minify, shrink resources, hoặc ProGuard file.
- Source set hiện có `main` và `debug`, chưa có `release`; nhiều công cụ camera dev nằm trong `app/src/debug`, nhưng `DeveloperFeatureToggles` và `DeveloperFeatureToggle` nằm trong `app/src/main`.
- Hidden devmode production hiện mở bằng 7 lần chạm trong `MainActivity`; metadata/toggle nằm ở `app/src/main`, nên release đã chứa behavior này.
- `CameraDevModeActivity`, camera benchmark, latency initializer/probe và use case debug nằm ở `app/src/debug`; source-contract test đang kiểm tra các file benchmark ở debug source set.
- `removeDeviceOwner()` và `resetDatabase()` nằm trong `main` và thuộc luồng hidden devmode phải giữ; R8 chỉ được đổi tên/tối ưu, không xóa behavior.
- `:app:signingReport` xác nhận release có `Config: null`; APK release hiện chưa có release signing key.
- Build script đang biến mọi key trong `application.properties` và `application-local.properties` thành `BuildConfig`; local file còn chứa key công cụ Atlassian/Confluence không thuộc app.
- `app/build.gradle` khám phá key từ tracked `application.properties`; `System.getenv(key)` ưu tiên hơn file, nên cloud env có thể cấp cả `BODYCAM_CRYPTO_PASSWORD` và `LOGGLY_TOKEN` mà không cần local file.
- Chưa có `proguard-rules.pro` hoặc artifact-verification script.
- Với AGP 9.2.1, R8 full mode là mặc định khi optimization được bật; cấu hình `optimization.enable = false` mới là chặn trực tiếp.
- Local task map xác nhận `packageRelease`, `packageReleaseBundle` và `packageReleaseUniversalApk`; release signing validation có thể bind vào package tasks mà không chặn unit tests.
- Official AGP guidance cho 9.2 dùng legacy `minifyEnabled`, `shrinkResources` và `proguard-android-optimize.txt`; không thêm `android.enableR8.fullMode=true`.
- Working tree có thay đổi khác đang tồn tại; task này phải tránh ghi đè các file đó.
- `FeatureGate.MEDIA_ENCRYPTION` hiện có default `false` và được đăng ký thành toggle “Media encryption”; final user decision xác nhận đây là setting hợp lệ, không phải dev code cần remove.
- Header hiện dùng `R.string.developer_mode`; text là `Developer mode` và `Chế độ nhà phát triển`. Unlock notice dùng `developer_mode_unlocked`.
- Working-tree diff hiện tại chuyển `BODYCAM_CRYPTO_PASSWORD` thành required `BuildConfig` value; final plan giữ embedded secret và không ghi đè thay đổi này.
- Không có code dev cụ thể được xác nhận cần xóa; folder-hiding example không còn scope điều tra trong task này.

## Hypotheses
- Release signing config chưa có; cùng key cho hai branch cần stable keystore và protected CI secrets.
- Không có evidence hiện tại cho code dev cần remove; không mở thêm investigation trong task này.

# Phased plan
- inspect: complete — AGP 9.2.1/Gradle 9.6.1, release unsigned, package tasks và working-tree boundaries đã xác nhận.
- consider solutions: complete — workflow env cho cả hai secrets; tracked properties chỉ khai báo field names/default.
- implement: complete — release/CI/signing/header và review fixes đã triển khai.
- confirm: complete — current workspace including concurrent GCM files passes full tests, CI-like release build and artifact checks.

# Considered solutions
- Chỉ đổi CI sang `assembleRelease`: chưa đủ vì release đang tắt optimization và chưa ký.
- Thêm flavor/build type mới: không cần; chưa có code dev cần phân loại hoặc remove.
- Giữ vòng lặp BuildConfig và embedded secrets: chọn theo user; tracked `application.properties` khai báo field names, workflow env cung cấp cả hai secret values.
- Một `signingConfigs.release`/keystore cho cả `develop` và `release`: chọn để giữ update continuity.
- Không thêm artifact verifier, device smoke gate hoặc Keystore migration; ngoài scope đã chốt.

# Decisions
- Giữ unlock 7-tap, Advanced Settings behavior và `MEDIA_ENCRYPTION` toggle; chỉ đổi header copy.
- Không remove code dev vì chưa có evidence cụ thể; `src/debug` hiện tự loại khỏi release theo variant.
- Mọi artifact test từ `develop` và `release` dùng release build type, minify/shrink, non-debuggable và cùng một stable signing key.
- Giữ vòng lặp property-to-BuildConfig và embedded secrets; `application.properties` chỉ cung cấp key names/default không-secret, workflow env cấp `BODYCAM_CRYPTO_PASSWORD` và `LOGGLY_TOKEN`, Gradle tạo BuildConfig fields.
- Signing env names: `DCAM_RELEASE_KEYSTORE_FILE`, `DCAM_RELEASE_KEYSTORE_PASSWORD`, `DCAM_RELEASE_KEY_ALIAS`, `DCAM_RELEASE_KEY_PASSWORD`; GitHub stores keystore as `DCAM_RELEASE_KEYSTORE_BASE64`.
- R8 dùng `proguard-android-optimize.txt`; không có custom keep file. Chỉ thêm rule hẹp nếu release build/runtime đưa evidence.
- Validation chỉ dùng GitHub Actions build/test flow; không thêm artifact/device gate.
- Không có code product nào đã sửa trong task cập nhật plan.

# Implementation Plan
## Slice 1 — Hardening release variant
Expected files: `app/build.gradle`.

Actions:
1. Thay `optimization.enable = false` bằng release config rõ ràng: `debuggable false`, `minifyEnabled true`, `shrinkResources true`.
2. Dùng `getDefaultProguardFile('proguard-android-optimize.txt')`; không tạo custom rules file khi chưa có rule.
3. Không thêm `android.enableR8.fullMode=true`; AGP 9.2.1 đã dùng full mode mặc định. Chỉ bảo đảm không có `android.enableR8.fullMode=false` hoặc compatibility override.
4. Build release trước với rule tối thiểu; chỉ thêm keep rule hẹp khi warning hoặc release runtime chứng minh cần thiết. Không dùng broad `-keep class ** { *; }`.

Acceptance:
- Release Gradle model có `debuggable=false`, minify và shrink resource bật.
- `clean assembleRelease` chạy thành công với R8 optimization; lỗi shrink/keep rule được sửa theo evidence build.
- Không thêm mapping/resource report gate ngoài Gradle task success.

## Slice 2 — Advanced Settings, không remove dev code
Expected files: `app/src/main/res/values/strings.xml`, `app/src/main/res/values-vi/strings.xml`; không đổi feature-gate behavior.

Actions:
1. Đổi `developer_mode` thành `Advanced Settings` / `Cài đặt nâng cao`.
2. Đổi `developer_mode_unlocked` tương ứng.
3. Giữ internal names, 7-tap unlock, `DeveloperFeatureToggles`, `MEDIA_ENCRYPTION` toggle và advanced actions hiện có.
4. Không tạo source-set policy mới, không force encryption on, không xóa/hide setting khi chưa có code/test evidence.
5. `src/debug` classes tiếp tục được Android release variant loại tự động; không cần cleanup riêng.

Acceptance:
- Header mới hiển thị đúng hai locale.
- Advanced Settings và media-encryption toggle giữ behavior hiện tại.
- Không có production behavior change ngoài copy header.
## Slice 3 — Giữ BuildConfig loop và embedded secrets
Expected files: `.github/workflows/build.yml`, `application.properties`; `app/build.gradle` giữ nguyên.

Actions:
1. Không đổi vòng lặp `appProperties.stringPropertyNames().each`.
2. Giữ tracked key declarations `BODYCAM_CRYPTO_PASSWORD` và `LOGGLY_TOKEN` không có secret values; `DEFAULT_STORAGE_MODE` không thuộc secret flow.
3. Thêm `LOGGLY_TOKEN: ${{ secrets.LOGGLY_TOKEN }}` cạnh existing `BODYCAM_CRYPTO_PASSWORD` trong workflow `env`.
4. Xóa line 44 `printf ... > application-local.properties`; cloud workflow không tạo, đọc hoặc ghi `application-local.properties`.
5. Sửa comment trong `application.properties` để `application-local.properties` chỉ là local developer override, không liên quan GitHub Actions.
6. Không đưa `ATLASSIAN_TOKEN`, `ATLASSIAN_EMAIL`, `CONFLUENCE_BASE_URL` vào CI workspace.
7. Giữ secret trong BuildConfig theo quyết định user; không thêm Keystore/provisioning migration.
8. Không thêm artifact secret scan hoặc secret architecture task.

Acceptance:
- Clean CI runner nhận cả hai GitHub Secrets qua environment và generate đủ required BuildConfig fields.
- `./gradlew test` và release build không fail do thiếu `BODYCAM_CRYPTO_PASSWORD` hoặc `LOGGLY_TOKEN` field.
- Cloud workflow không tạo `application-local.properties`; local developer override behavior vẫn giữ.
## Slice 4 — Một release signing key cho hai branch
Expected files: `app/build.gradle`, `.github/workflows/build.yml`; GitHub Secrets ngoài repository.

Actions:
1. Dùng một long-lived keystore cho cả `develop` và `release` để giữ update continuity.
2. Lưu keystore/password/alias trong protected GitHub Secrets; backup key ngoài repository.
3. Decode keystore vào `$RUNNER_TEMP`, cấu hình `signingConfigs.release` từ environment.
4. Không fallback Android Debug key; thiếu key phải làm release build fail.
5. Giữ certificate/key lineage ổn định qua mọi test artifact và GitHub Release.
6. Không thêm QA-vs-production key split, `apksigner` verifier hoặc signing artifact gate.

Acceptance:
- `assembleRelease` sinh APK release đã ký bằng cùng key trên cả hai branch.
- APK candidate mới update được APK candidate trước cùng package/data.
- Keystore/password không commit hoặc upload.
## Slice 5 — CI chỉ phát release APK
Expected file: `.github/workflows/build.yml`.

Actions:
1. Cả `develop` và `release` đều chạy `clean assembleRelease`; bỏ `assembleDebug` và debug output path.
2. Workflow `env` truyền cả `BODYCAM_CRYPTO_PASSWORD` và `LOGGLY_TOKEN`; xóa local-file write.
3. Cùng release signing config/key được dùng cho hai branch.
4. `develop` upload test artifact; `release` upload artifact và tạo GitHub Release asset.
5. Giữ existing `./gradlew test`; không thêm verifier/mapping/device steps.

Acceptance:
- Workflow không còn `assembleDebug` hoặc copy từ `outputs/apk/debug`.
- Cả hai branch upload APK từ `outputs/apk/release`.
- Build/signing failure tự chặn upload theo GitHub Actions step dependency.
## Slice 6 — Không thêm artifact gate

Actions:
1. Không tạo `tools/verify-release-apk.sh`.
2. Không thêm `apkanalyzer`, certificate pin check, DEX class scan, mapping scan hoặc private mapping artifact.
3. GitHub Actions task success và release output path là validation đủ cho scope này.
4. Workflow fail tự nhiên nếu Gradle signing/config/build lỗi.
## Slice 7 — CI validation tối thiểu

Validation:
1. Existing `./gradlew test` pass.
2. Both branches run `clean assembleRelease`.
3. Release branch tạo GitHub Release asset; develop chỉ upload test artifact.
4. Không thêm device smoke, offline test, JADX comparison, crash retrace hoặc rollback drill.
5. Nếu release build fail do R8/keep rule, xử lý lỗi build theo evidence; không quay về debug APK.
# Explicit Non-Goals
- Không remove Advanced Settings hoặc `MEDIA_ENCRYPTION` toggle.
- Không tự nhận diện/xóa “dev code” khi chưa có evidence cụ thể.
- Không đổi BuildConfig property loop.
- Không migrate secret sang Android Keystore/provisioning.
- Không thêm artifact verifier, device validation, reverse-engineering comparison, packer, DEX encryption hoặc native secret hiding.
- Không đổi crypto algorithm/format trong task CI.

# Prerequisites / Decisions Needed Before Implementation
- Approved single release keystore, passwords, alias và backup owner.
- GitHub Secrets được cấp cho trusted `develop` và `release` workflows.
- Header copy `Advanced Settings` / `Cài đặt nâng cao` được duyệt.
# Post-review correction
- User questioned `app/proguard-rules.pro`: R8 is shrinker/compiler, while `proguardFiles` names its accepted rules format; the project-specific file contained no rules.
- Decision: keep only `proguard-android-optimize.txt` and remove empty `app/proguard-rules.pro`; no custom keep rules without release evidence.
- Plan change: remove custom-file creation from Slice 1 and revalidate release configuration.
- Key provisioning decision: use a new DCAM Android JKS, not BDMA Windows Authenticode PFX; no secret values recorded here.
# Changes made
- Triển khai cloud env-only secret flow, release hardening, signing, Advanced Settings copy và regression test fixes.
- Implementation batch 1: sửa `app/build.gradle`, `.github/workflows/build.yml`, `application.properties`, hai locale strings; không giữ custom R8 rules file.
- Release config bật debuggable false, minify, shrink resources, optimized default rules; package tasks depend `validateReleaseBuildInputs`.
- Workflow thêm LOGGLY env, decode keystore tạm, build release trên cả hai branch, bỏ cloud `application-local.properties`.

# Validation results
- Final `./gradlew test` pass on current workspace: 913 tests, 0 failures, 0 errors; configuration cache reused.
- Focused migration/newline regression tests pass.
- `validateReleaseBuildInputs` pass matrix: missing cloud BODYCAM fails, missing signing vars fails, missing keystore file fails, valid inputs pass.
- Clean CI-like `:app:assembleRelease` pass with `GITHUB_ACTIONS=true`; R8 minify, resource shrink, lint vital, signing và package tasks executed.
- Post-review `:app:assembleRelease --no-daemon` pass after removing empty custom rules file; `minifyReleaseWithR8`, resource optimization, lint vital, signing and packaging executed.
- Output có đúng một signed release APK; `apksigner verify` pass v2 với một signer dùng local validation key.
- APK manifest có 0 `debuggable` attributes; release BuildConfig có `DEBUG=false`; debug-source class/component scan có 0 match.
- R8 outputs non-empty: `mapping.txt`, `usage.txt`, `resources.txt`, `seeds.txt`, `configuration.txt`.
- Workflow YAML parse pass; 5 run blocks pass `bash -n`; workflow không còn `assembleDebug`, debug output path hoặc cloud `application-local.properties`.
- Target diff/whitespace, strict UTF-8, BOM, mojibake, numeric-entity và secret-leak checks pass.
- HEAD đổi từ `546fb66` sang `8271daa` trong task; final tests/build đã rerun trên `8271daa`.
- Independent review high findings về CI test gate và blank BODYCAM đã fix; re-review báo 0 unresolved high/medium findings.
- `:app:lintRelease` vẫn crash trong AGP lint `ApiDetector` trên unchanged `CameraPipelineDiagnostics.java`; `lintVitalRelease` pass trong release build, không disable detector để che lỗi.

# 2026-08-11 Signing Key Provisioning
- User requested creation of a new DCAM Android release signing key.
- Must: one long-lived Android key for both `develop` and `release`; store private material outside repository; verify Gradle signing compatibility.
- Must not: reuse BDMA Windows Authenticode PFX; overwrite an existing signing key; record passwords or private material in repository, logs, or journal.
- inspect: complete — JDK 21/keytool available; no release keystore found in repository or designated secure paths; `gh` unavailable.
- consider solutions: superseded — initial 10,000-day validity was accepted before the user clarified the app was unreleased and requested effectively permanent validity.
- implement: superseded — initial 2053 key was generated and validated, then replaced and securely removed after the user correction.
- confirm: superseded — initial key validation passed, but its 2053 validity no longer represents final signing material.
- Verification note: first Base64 round-trip check used unavailable `SHA256.HashData` on current Windows PowerShell/.NET; only public certificate fingerprint metadata was appended before failure, with no key/password change. Re-run uses `SHA256.Create().ComputeHash`.

# 2026-08-11 Signing Key Validity Correction
- User correction: DCAM has not been released; replacing the newly generated signing key does not break release update continuity.
- Clarification: APK Signature Scheme v2 is the Android package-signature format, not application release/version numbering. With `minSdk 26`, v2 signing is expected.
- Must: replace the 2053 certificate before first release with an effectively permanent certificate whose finite X.509 `NotAfter` is near the representable maximum.
- Must not: overwrite canonical signing files until staged key generation, certificate inspection, clean release build, and APK signer comparison pass.
- inspect: complete — current key is unused for release and therefore replaceable.
- consider solutions: complete — literal infinite X.509 validity is impossible; target `9999-12-30` as practical maximum.
- implement: complete — staged year-9999 key generated, validated, atomically promoted to canonical paths, and unused 2053 key/credentials removed after canonical verification.
- confirm: complete — canonical certificate is valid from 2026-08-11 through 9999-12-30; clean release build passes; APK has one v2 signer matching certificate SHA-256 `A9F093F2505199E6B151DA272AA93B0C38C2A94B3C10D4AACC58104CCA3FB93D`; Base64 round-trip and restricted ACL pass; old/staged files are absent.

# Completion Notes
- Final canonical key: `C:\Users\ADMIN\.dcam\signing\dcam-release.jks`, alias `dcam-release`, RSA-4096/SHA256withRSA, X.509 `NotAfter` 9999-12-30, keystore SHA-256 `80145223820D9A62868342A632FDD3262516342DC1599F006EC85D2288451DCA`.
- Key provisioning completion: credentials handoff is `C:\Users\ADMIN\.dcam\signing\dcam-release-github-secrets.txt`; `gh` is unavailable, so GitHub secret upload remains an operational handoff, not repository work. Back up keystore and password separately before deleting the plaintext handoff file.
- Final repository leak scan after canonical replacement: 0 generated signing-secret matches, 0 keystore/PFX files in repository, and signing directory contains only canonical keystore plus credentials handoff.
- Required work: None.
- Empty `app/proguard-rules.pro` removed; release keeps optimized default R8 rules only.
- GitHub cần cùng release key cho cả hai branch qua `DCAM_RELEASE_KEYSTORE_BASE64`, `DCAM_RELEASE_KEYSTORE_PASSWORD`, `DCAM_RELEASE_KEY_ALIAS`, `DCAM_RELEASE_KEY_PASSWORD`.
- App build secrets: `BODYCAM_CRYPTO_PASSWORD` bắt buộc trên cloud; `LOGGLY_TOKEN` optional và blank chỉ tắt remote logging.
- Initial pipeline validation used Android debug keystore; final provisioning validation used the generated canonical release key; workflow never falls back to debug signing.
- Existing unrelated `docs/local-dev/current-repo/current-state.md` và `docs/local-dev/plans/2026-08-10-remove-dcim-config-from-serial-build.md` changes giữ nguyên.
- External GCM source/test/resources/contracts remained outside task scope; final 913-test run and clean release build include them and pass.