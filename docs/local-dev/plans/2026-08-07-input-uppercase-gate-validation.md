# Input Uppercase Gate Validation

- Status: Complete
- Objective: Tự động chuyển ký tự nhập `a-z` thành `A-Z` và chỉ cho dữ liệu hợp lệ `A-Z0-9` đi qua cổng kiểm tra hiện có.

## Contract

### Must
- Chuẩn hóa chữ thường ASCII thành chữ hoa khi người dùng nhập.
- Chỉ chấp nhận ký tự thuộc `A-Z0-9` tại cổng validate.
- Giữ nguyên hành vi đã xác nhận ngoài phạm vi field liên quan.

### Must not
- Không suy đoán hoặc đổi nghiệp vụ khác.
- Không nới lỏng test hợp đồng hiện có.
- Không thêm dependency hoặc abstraction mới nếu API nền tảng đủ dùng.

## Evidence

### Facts
- Yêu cầu người dùng: "auto turn input a-z to A-Z, validate gate A-Z0-9".
- Field liên quan là serial thiết bị trong `app/src/main/java/com/dvid/dcam/app/MainActivity.java`.
- UI hiện chỉ có `InputFilter.LengthFilter(10)` và dùng `TextWatcher` để bật/tắt nút xác nhận.
- Cổng validate là `DeviceSerialNumberUseCase.isValid` trong `app/src/main/java/com/dvid/dcam/feature/device/application/usecase/DeviceSerialNumberUseCase.java`.
- Contract độ dài hiện tại là 6–10 ký tự; regex hiện chấp nhận cả chữ hoa và chữ thường ASCII.

### Hypotheses
- Có một field dạng mã/ID đang nhận input tự do và một validator hiện hữu cần siết về ASCII chữ hoa cùng chữ số.

## Phased Plan

- Inspect — Completed: xác định dialog serial thiết bị, filter UI, use case validate, và test liên quan.
- Consider solutions — Completed: chọn API Android `InputFilter.AllCaps` và regex gate chữ hoa ASCII.
- Implement — Completed: thêm native all-caps filter, siết regex, và thêm test hồi quy.
- Confirm — Completed: focused tests, test XML, diff, và UTF-8 checks đều pass.

## Considered Solutions

- Dùng `InputFilter.AllCaps` cùng `LengthFilter`: native, một dòng, giữ cursor/filter pipeline hiện có.
- Dùng `TextWatcher` tự viết uppercase: loại bỏ vì thêm re-entrancy và xử lý selection không cần thiết.
- Uppercase trong `save`: loại bỏ vì không thể hiện ngay trên UI và làm mờ ranh giới validate.

## Decisions

- Dùng API chuẩn/nền tảng; không thêm dependency.
- Giữ trim và độ dài 6–10 hiện có; chỉ siết alphabet từ `A-Za-z` thành `A-Z`.
- Thêm `InputFilter.AllCaps` trước `LengthFilter` để chữ thường nhập/paste được đổi trước khi kiểm tra độ dài.

## Changes Made

- Tạo task journal này.
- `MainActivity`: thêm `InputFilter.AllCaps` trước giới hạn 10 ký tự.
- `DeviceSerialNumberUseCase`: đổi gate thành `[A-Z0-9]{6,10}`.
- Test: kiểm UI dùng all-caps filter và chữ thường không qua validator.

## Validation Results

- `./gradlew.bat :app:testDebugUnitTest --tests com.dvid.dcam.architecture.MainActivityStartupFlowTest --tests com.dvid.dcam.platform.config.FileDeviceSerialNumberStoreTest --rerun-tasks` passed (exit code 0).
- Compiler chỉ báo note deprecated API đã có; không có test failure.
- Test XML: `MainActivityStartupFlowTest` 17 test và `FileDeviceSerialNumberStoreTest` 17 test; 0 failure, 0 error, 0 skipped.
- `git diff --check` pass cho toàn bộ path chạm tới.
- Strict UTF-8, BOM, mixed-EOL, trailing-space, mojibake, và numeric-entity checks pass.
- Check đầu tiên ép final newline đã dừng trên trạng thái không-newline có sẵn của `DeviceSerialNumberUseCase.java`; giữ nguyên định dạng file, không đổi ngoài phạm vi.

## Completion Notes

- Required work: None.
- Chữ thường nhập vào field serial được native filter đổi thành chữ hoa; validator chỉ nhận `A-Z0-9`, giữ độ dài 6–10 và trim hiện có.
- Không có rủi ro còn mở trong phạm vi task.
