# Chế độ nhà phát triển

`app/devmode` chỉ chuẩn bị nhãn, nhóm và `SettingId` cho màn hình công tắc dành cho nhà phát triển.
Nó không quyết định tính năng có được chạy hay không.

## Mỗi package làm gì

- `core/featuregate`
  - `FeatureGate`: định nghĩa công tắc, giá trị mặc định, khóa lưu trữ và quan hệ gate cha/con.
  - `FeatureGates`: đọc, ghi và tính trạng thái bật thực tế.
  - `FeatureGateStore`: interface lưu trạng thái.
- `platform/featuregate`
  - `AndroidFeatureGateStore`: lưu trạng thái bằng `SharedPreferences`.
- `app/devmode`
  - Gắn một `FeatureGate` với nhãn, nhóm và `SettingId` để màn hình biết phải hiển thị gì.
  - Chuyển yêu cầu đọc/ghi sang `FeatureGates`; không tự tính lại quy tắc bật/tắt.
- `app/ui`
  - Vẽ công tắc và nhận thao tác người dùng.

```text
app/ui
    → DeveloperFeatureToggles (thông tin hiển thị, chuyển yêu cầu)
    → FeatureGates (quy tắc bật/tắt)
    → FeatureGateStore (port lưu trạng thái)
    → AndroidFeatureGateStore (adapter dùng SharedPreferences)
    → SharedPreferences
```

Sơ đồ này mô tả riêng màn hình công tắc; không thay thế đường gọi UI → `*UseCase` của tính năng
thông thường.
```

## Không đặt trong `app/devmode`

- Giá trị mặc định của gate.
- Quan hệ gate cha/con.
- Cách tính trạng thái bật thực tế.
- Code đọc `SharedPreferences`.
- Renderer, `Activity` hoặc code Android UI.

Nếu logic bật/tắt xuất hiện trong `app/devmode`, quy tắc đã bị lặp. Chuyển quy tắc đó về
`FeatureGates`.

## Thêm công tắc mới

1. Thêm `FeatureGate` nếu tính năng chưa có gate.
2. Khai báo gate cha/con ngay trong `FeatureGate` nếu thật sự cần.
3. Đăng ký gate trong `DeveloperFeatureToggles` với nhãn, nhóm và `SettingId`.
4. Thêm hoặc cập nhật mục hiển thị trong `app/ui`.
5. Test trạng thái bật thực tế ở `FeatureGates`; test ánh xạ nhãn/`SettingId` ở
   `DeveloperFeatureToggles`.

Không đăng ký mọi feature vào chế độ nhà phát triển. Chỉ thêm công tắc khi có yêu cầu tắt/mở
trong lúc phát triển hoặc kiểm thử.
