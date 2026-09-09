# Build and Launch Process for DCAM Application with ADB and Gradle

**Page ID**: 54394925  
**Version**: 9  
**Type**: page  
**URL**: https://ducviet.atlassian.net/wiki/spaces/DVID/pages/54394925

---


# Build and Launch Process for DCAM Application with ADB and Gradle

Item

Information

Project

DCAM (Android BodyCamera Application)

Document Type

Working Instruction / Engineering Runbook

Version

0.1

Status

Draft

Approval Scope

Local build/install/launch instruction and expected-output template only; not approved product baseline, execution evidence, Device POC result or release approval.

Owner

[phiha (Unlicensed)](https://ducviet.atlassian.net/wiki/people/557058:9faf7c54-9287-4a39-a5cd-09337b4b73b9?ref=confluence) [Việt Anh](https://ducviet.atlassian.net/wiki/people/70121:291a3abc-1d00-4cc3-84e8-af1e4491aeb1?ref=confluence) 

Technical Reviewer

Android Lead

Approver

Hoàng Ngọc Quyền

Parent Page

04 Technical Documentation

Target Audience

Android Developers, QA, Technical Reviewers

Last Updated

21 Aug 2026

Related Jira

[DCAM-3](https://ducviet.atlassian.net/browse/DCAM-3) — Android Environment / Runnable APK; linkage does not itself prove execution evidence.

Related Documents

DCAM Android Development Standard, DCAM Device POC & Hardware Validation Report, DCAM Engineering Evidence & NAS Artifact SOP, DCAM Documentation Governance

Dependencies / Blockers

Exact `develop` commit, target device/ADB configuration, build/test/log artifact location, timestamp and Jira-linked evidence are required before any result can be treated as execution evidence.

## Mục đích

Tài liệu này là runbook dành cho lập trình viên để thực hiện một vòng kiểm tra cơ bản trên thiết bị Android thật hoặc emulator:

Kiểm tra môi trường và kết nối ADB.

Chạy unit test và build APK debug bằng Gradle.

Cài APK lên đúng thiết bị.

Khởi động DCAM bằng component tường minh.

Thu log và kiểm tra crash hoặc ANR trong khoảng quan sát.

Đây là **developer smoke test**, không thay thế kiểm thử chức năng, kiểm thử thiết bị, QA hoặc quy trình phát hành production.

‌

## Cấu hình hiện tại

Thành phần

Giá trị

Repository

`https://github.com/DucVietTech/dcam.git`

Branch / Exact Commit SHA

`develop` / `c4da859868f3147bbd59b35ef38c098fdba58a94`

Gradle module

`:app`

Application ID

`com.dvid.dcam`

Launcher component

`com.dvid.dcam/.app.MainActivity`

Gradle wrapper

`9.6.1`

Java source/target

`17`

Gradle Java toolchain

`21`

Minimum SDK

`26`

Target SDK

`36`

Compile SDK

`36.1`

Các giá trị trên phải được đối chiếu lại với `app/build.gradle`, `gradle/wrapper/gradle-wrapper.properties` và `app/src/main/AndroidManifest.xml` nếu cấu hình dự án thay đổi.

## Controlled Verification Record

Field

Value

Parent task

`DCAM-3`

Automation support

`DCAM-48`

Verified baseline

`develop @ c4da859868f3147bbd59b35ef38c098fdba58a94`

Evidence

`EV-DCAM-3-20260821-001`

Evidence path

`\\192.168.100.2\SERVER-SnT\2. DCAM\Artifacts\Build-0.1\Sprint-2\DCAM-3\20260821_001`

Result

`PASS`

Scope

Developer build/install/launch baseline

This record supplements the configuration above; it does not change the `Draft` status or establish camera qualification, complete Device POC, production approval or release readiness.

‌

## Chạy tự động bằng script và lưu evidence lên NAS

Runner nằm ngoài Git repository và được lưu cùng nhau tại:

\\192.168.100.2\SERVER-SnT\2. DCAM\Artifacts\Build-0.1\Sprint-2\DCAM-48\run-dcam-smoke-test.cmd
\\192.168.100.2\SERVER-SnT\2. DCAM\Artifacts\Build-0.1\Sprint-2\DCAM-48\run-dcam-smoke-test.ps1
Wrapper `.cmd` dùng `%~dp0`, vì vậy hai file phải nằm cùng thư mục. `DCAM-48` là thư mục runner và Jira folder của evidence; các run folder được tạo bên trong thư mục này.

### Lệnh khuyến nghị

Trước khi chạy, user phải mở PowerShell tại thư mục gốc của project DCAM, nơi có `gradlew.bat`. Script sử dụng thư mục hiện tại làm repository và mặc định publish evidence lên NAS:

```
& '\\192.168.100.2\SERVER-SnT\2. DCAM\Artifacts\Build-0.1\Sprint-2\DCAM-48\run-dcam-smoke-test.cmd'
```

Có thể dùng một dòng kiểm tra đường dẫn trước khi chạy:

```
$runner = '\\192.168.100.2\SERVER-SnT\2. DCAM\Artifacts\Build-0.1\Sprint-2\DCAM-48\run-dcam-smoke-test.cmd'; if (-not (Test-Path -LiteralPath $runner -PathType Leaf)) { throw "Runner not found: $runner" }; & $runner
```

Trong lúc chạy, script chỉ dùng một thư mục staging duy nhất dưới `%TEMP%` trên máy thực thi. Sau khi publish lên NAS và xác minh SHA-256 thành công, staging này được xóa; không có evidence nào được giữ lại trong repository hoặc trong `app\build`.

Đường dẫn evidence mặc định trong PS1 là:

```
\\192.168.100.2\SERVER-SnT\2. DCAM\Artifacts
```

Script tự tạo cấu trúc:

Artifacts\Build-0.1\Sprint-2\DCAM-48\YYYYMMDD_sequence\
  manifest.md
  manifest.json
  build\app-debug.apk
  build\source-readout.json
  adb\
  logs\
  media\recordings\
  media\images\
  screenshots\
  reports\
Nếu muốn truyền tường minh evidence root:

```
& '\\192.168.100.2\SERVER-SnT\2. DCAM\Artifacts\Build-0.1\Sprint-2\DCAM-48\run-dcam-smoke-test.cmd' -EvidenceRoot '\\192.168.100.2\SERVER-SnT\2. DCAM\Artifacts'
```

### Tham số PS1

Tham số

Mặc định

Tác dụng

`-Serial`

Tự chọn

Serial ADB; nếu bỏ qua phải có đúng một thiết bị ở trạng thái `device`.

`-ObservationSeconds`

`30`

Thời gian quan sát sau launch, trong khoảng `0–3600`.

`-CleanInstall`

Tắt

Gỡ DCAM và xóa dữ liệu trước khi cài APK.

`-SkipUnitTests`

Tắt

Bỏ `:app:testDebugUnitTest`, vẫn chạy `:app:assembleDebug`.

`-BuildOnly`

Tắt

Chỉ build và tạo evidence; không yêu cầu ADB. NAS vẫn được yêu cầu mặc định.

`-ShowAppLog`

Tắt

In app logcat theo PID lên terminal.

`-JiraKey`

`DCAM-48`

Jira key dùng cho Evidence ID và Jira folder.

`-EvidenceRoot`

UNC NAS DCAM

NAS là nơi lưu evidence chính thức và bắt buộc phải truy cập được.

`-BuildName`

`Build-0.1`

Tầng build trong cấu trúc NAS.

### Ví dụ

Chỉ build, test và publish evidence, không cài lên thiết bị:

```
& '\\192.168.100.2\SERVER-SnT\2. DCAM\Artifacts\Build-0.1\Sprint-2\DCAM-48\run-dcam-smoke-test.cmd' -BuildOnly
```

Chạy với thiết bị cụ thể và quan sát 60 giây:

```
& '\\192.168.100.2\SERVER-SnT\2. DCAM\Artifacts\Build-0.1\Sprint-2\DCAM-48\run-dcam-smoke-test.cmd' -Serial DEVICE_SERIAL -ObservationSeconds 60
```

Cài sạch và hiển thị app log:

```
& '\\192.168.100.2\SERVER-SnT\2. DCAM\Artifacts\Build-0.1\Sprint-2\DCAM-48\run-dcam-smoke-test.cmd' -CleanInstall -ShowAppLog
```

### Quy trình và điều kiện evidence

Script thực hiện theo thứ tự:

Xác nhận thư mục PowerShell hiện tại là repository DCAM có `gradlew.bat`, sau đó đọc commit, branch và trạng thái working tree.

Kiểm tra NAS evidence root và cấp sequence mới, không ghi đè run folder cũ.

Chạy unit test và assemble debug APK.

Ghi Gradle output, source readout và APK vào evidence set.

Nếu không phải `-BuildOnly`, kiểm tra ADB, model/firmware và serial.

Ghi ADB output, install result, launch result, PID và logcat.

Tạo `manifest.md` và `manifest.json` chứa Evidence ID, Jira key, operator, thời gian, device/model/firmware, commit, commands, Pass/Fail và checksum SHA-256.

Publish toàn bộ evidence set lên NAS và xác minh checksum sau khi copy.

Xóa thư mục staging tạm dưới `%TEMP%`; nếu publish NAS thất bại, run bị Blocked/Fail và staging chỉ được giữ ngoài project để chẩn đoán.

Evidence ID có dạng:

```
EV-DCAM-48-YYYYMMDD-sequence
```

Run folder có dạng:

```
YYYYMMDD_sequence
```

Kết quả process:

Exit code `0`: execution và NAS publish thành công.

Exit code `1`: execution hoặc artifact bị Fail.

Exit code `2`: môi trường bị Blocked, thường do repo/device/NAS chưa sẵn sàng.

Evidence chính thức luôn phải nằm trên NAS. Nếu NAS không truy cập được hoặc checksum sau khi copy không khớp, script không báo PASS và trả về trạng thái Blocked/Fail; không có chế độ lưu evidence vào project.

## 1. Điều kiện đầu vào

Bắt buộc mở PowerShell tại thư mục gốc của project DCAM, nơi có file `gradlew.bat`. Không chạy runner từ thư mục NAS hoặc thư mục bất kỳ khác.

Kiểm tra các công cụ:

adb version
java -version
.\gradlew.bat --version
.\gradlew.bat -q javaToolchains
Yêu cầu:

`adb` có trong `PATH`.

JVM dùng để khởi động Gradle đáp ứng yêu cầu của Android Gradle Plugin; môi trường hiện tại có thể dùng JVM 17.

Một JDK 21 khả dụng cho Java toolchain được cấu hình trong module `:app`.

Android SDK đã cài platform phù hợp với Compile SDK 36.1.

Thiết bị đã bật USB debugging và đã chấp nhận khóa RSA của máy tính.

Kiểm tra đang đứng đúng thư mục:

if (-not (Test-Path '.\gradlew.bat')) {
    throw 'Hãy mở PowerShell tại thư mục gốc của repository DCAM.'
}
## 2. Chọn chính xác thiết bị

Liệt kê thiết bị:

```
adb devices -l
```

Chỉ tiếp tục khi thiết bị cần dùng có trạng thái `device`.

Các trạng thái không hợp lệ:

`unauthorized`: mở khóa thiết bị và chấp nhận yêu cầu USB debugging.

`offline`: kết nối lại thiết bị hoặc khởi động lại ADB.

Không có thiết bị: kiểm tra cáp USB, driver, emulator và USB debugging.

Không dùng `adb get-serialno` khi có thể có nhiều thiết bị. Sao chép serial từ kết quả `adb devices -l` và gán tường minh:

$serial = 'SERIAL_FROM_ADB_DEVICES'

if ([string]::IsNullOrWhiteSpace($serial) -or $serial -eq 'SERIAL_FROM_ADB_DEVICES') {
    throw 'Chưa cấu hình serial của thiết bị.'
}

$deviceState = (adb -s $serial get-state 2>&1 | Out-String).Trim()
if ($LASTEXITCODE -ne 0 -or $deviceState -ne 'device') {
    throw "Thiết bị $serial chưa sẵn sàng. Trạng thái: $deviceState"
}
Tất cả lệnh ADB phía dưới phải sử dụng `-s $serial` để tránh thao tác nhầm thiết bị.

## 3. Khai báo biến dùng chung

$applicationId = 'com.dvid.dcam'
$activity = '.app.MainActivity'
$component = "$applicationId/$activity"
$apkPath = '.\app\build\outputs\apk\debug\app-debug.apk'
## 4. Chạy unit test và build APK debug

.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug --no-daemon --stacktrace

if ($LASTEXITCODE -ne 0) {
    throw 'Unit test hoặc quá trình build APK đã thất bại.'
}

if (-not (Test-Path $apkPath)) {
    throw "Build kết thúc nhưng không tìm thấy APK tại $apkPath"
}
Ý nghĩa:

`:app:testDebugUnitTest`: chạy unit test của biến thể debug.

`:app:assembleDebug`: tạo APK debug.

`--no-daemon`: không giữ Gradle daemon sau lệnh này.

`--stacktrace`: hiển thị stack trace khi xảy ra lỗi.

Tiêu chí PASS: Gradle trả về `BUILD SUCCESSFUL` và file `$apkPath` tồn tại.

## 5. Cài APK

$installOutput = adb -s $serial install -r -t $apkPath 2>&1
$installExitCode = $LASTEXITCODE
$installText = ($installOutput | Out-String).Trim()
$installText

if ($installExitCode -ne 0 -or $installText -notmatch '(?m)^Success\s*$') {
    throw "Không thể cài APK lên thiết bị $serial.`n$installText"
}
Ý nghĩa:

`-r`: cài đè ứng dụng hiện có và thông thường giữ lại dữ liệu ứng dụng.

`-t`: cho phép cài test/debug APK.

Tiêu chí PASS: ADB trả về `Success`.

Nếu cần kiểm thử với trạng thái cài đặt sạch, phải chủ động gỡ ứng dụng trước khi cài. Lưu ý thao tác này xóa dữ liệu ứng dụng:

```
adb -s $serial uninstall $applicationId
```

## 6. Khởi động ứng dụng

Dừng phiên bản đang chạy và xóa log cũ trước khi mở lại:

adb -s $serial shell am force-stop $applicationId
if ($LASTEXITCODE -ne 0) {
    throw "Không thể dừng DCAM trên thiết bị $serial."
}

adb -s $serial logcat -c
if ($LASTEXITCODE -ne 0) {
    throw "Không thể xóa logcat trên thiết bị $serial."
}

$launchOutput = adb -s $serial shell am start -W -n $component 2>&1
$launchExitCode = $LASTEXITCODE
$launchText = ($launchOutput | Out-String).Trim()
$launchText

if ($launchExitCode -ne 0 -or $launchText -notmatch 'Status:\s+ok') {
    throw "DCAM không khởi động thành công.`n$launchText"
}
Tiêu chí PASS:

Kết quả có `Status: ok`.

Activity được mở là `com.dvid.dcam/.app.MainActivity`.

Màn hình ứng dụng xuất hiện trên đúng thiết bị.

## 7. Khoảng quan sát

Sau khi ứng dụng mở, thao tác các chức năng cần smoke test trong ít nhất 30–60 giây. Ví dụ:

Xác nhận màn hình chính hiển thị.

Xác nhận ứng dụng không tự đóng.

Thực hiện thao tác đang cần kiểm tra cho thay đổi hiện tại.

Quan sát hộp thoại permission hoặc lỗi hiển thị trên thiết bị.

Việc không phát hiện crash ngay sau khi mở ứng dụng không chứng minh toàn bộ chức năng DCAM hoạt động đúng.

## 8. Thu log của process DCAM

Lấy PID sau khi ứng dụng đã khởi động:

$pidOutput = (adb -s $serial shell pidof $applicationId 2>&1 | Out-String).Trim()

if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($pidOutput)) {
    throw 'Không tìm thấy process DCAM. Ứng dụng có thể đã dừng hoặc crash.'
}

$appPid = ($pidOutput -split '\s+')[0]
adb -s $serial logcat -d --pid=$appPid -v threadtime

if ($LASTEXITCODE -ne 0) {
    throw "Không thể đọc logcat cho PID $appPid."
}
Nếu ứng dụng dùng nhiều process, lệnh trên chỉ hiển thị process đầu tiên. Khi điều tra lỗi sâu hơn, cần kiểm tra toàn bộ PID hoặc logcat hệ thống.

## 9. Kiểm tra crash và ANR

Vì logcat đã được xóa ngay trước khi launch, phần kiểm tra dưới đây áp dụng cho khoảng thời gian từ lúc khởi động đến lúc chạy lệnh:

$allLogs = adb -s $serial logcat -d -v threadtime 2>&1
$logcatExitCode = $LASTEXITCODE

if ($logcatExitCode -ne 0) {
    throw 'Không thể đọc logcat để kiểm tra crash hoặc ANR.'
}

$escapedApplicationId = [regex]::Escape($applicationId)
$crashPattern = "FATAL EXCEPTION|ANR in $escapedApplicationId|Process:\s*$escapedApplicationId"
$crashLines = @($allLogs | Select-String -Pattern $crashPattern)

if ($crashLines.Count -gt 0) {
    $crashLines
    throw 'FAIL: Phát hiện dấu hiệu crash hoặc ANR của DCAM.'
}

'PASS: Không phát hiện FATAL EXCEPTION hoặc ANR của DCAM trong khoảng quan sát.'
Nếu ứng dụng đã crash trước khi lấy PID, hãy ưu tiên kiểm tra phần logcat toàn hệ thống này thay vì chỉ dựa vào `logcat --pid`.

## Tiêu chí hoàn thành

Vòng smoke test chỉ được coi là PASS khi đồng thời thỏa mãn:

Thiết bị có trạng thái `device` và đúng serial đã chọn.

Unit test thành công.

Gradle build thành công và APK tồn tại.

ADB cài APK thành công.

`am start -W` trả về `Status: ok`.

Ứng dụng vẫn chạy trong khoảng quan sát.

Không phát hiện `FATAL EXCEPTION` hoặc ANR liên quan đến `com.dvid.dcam`.

Kết quả PASS này chỉ xác nhận baseline build/install/launch. Nó không xác nhận recording, camera, microphone, storage, kiosk/device-owner, BDMA, network hoặc toàn bộ yêu cầu sản phẩm.