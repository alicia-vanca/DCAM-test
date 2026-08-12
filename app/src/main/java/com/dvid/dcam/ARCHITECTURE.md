# Kiến trúc Dcam

Tài liệu này dành cho thành viên mới, đặc biệt là người đã quen MVC.

Mục tiêu không phải tạo nhiều tầng. Mục tiêu là tránh để `Activity` vừa xử lý giao diện,
vừa quyết định nghiệp vụ, vừa gọi DB, file, camera hoặc phần cứng.

## 1. Chỉ cần nhớ ba khu

```text
1. app/ui
   Nhận thao tác, giữ trạng thái màn hình, vẽ giao diện

2. feature
   Quyết định ứng dụng phải làm gì

3. platform
   Làm việc thật với Android, DB, file, camera, phần cứng, mạng
```

Một đường gọi thường gặp:

```text
Màn hình / nút cứng → class *UseCase
*UseCase → domain (chỉ khi có quy tắc nghiệp vụ Java thuần)
*UseCase hoặc domain → port (chỉ khi cần hệ thống bên ngoài)
adapter trong platform triển khai port → DB / file / camera / phần cứng / mạng
```

`domain` không phải bước bắt buộc trong mọi đường gọi. Use case chỉ dùng `domain` khi có
quy tắc, phép tính hoặc model nghiệp vụ cần tách riêng.

Use case đã thuộc phần `application`. Không có thêm một “application layer” khác nằm sau
use case.

## 2. Năm quy tắc cần nhớ

1. UI handler, `ViewModel` hoặc lớp nhận input gọi class `*UseCase`.
2. `*UseCase` là `public final class`, không phải interface và không có cặp `*UseCaseImpl`.
3. Khi use case hoặc domain cần DB, file, Android SDK, camera, phần cứng hoặc mạng, nó gọi
   qua interface trong `application/port`.
4. Class biết Android hoặc nhà cung cấp cụ thể nằm trong `platform` và triển khai interface đó.
5. `AppComposition` tạo và nối các class. UI không tự tạo class `platform` và không import
   `platform`.

`MainActivity` chỉ quản lý vòng đời, gắn view và chuyển màn hình. Nó gọi các class thuộc `app` do
`AppComposition` cung cấp. Nó không tự tạo class `platform`, không quyết định nghiệp vụ và không
chứa chuỗi xử lý dài. `MainActivity` hiện còn một phần code cũ; khi thêm chức năng mới, đặt hành
động vào `ViewModel`, input router hoặc use case thay vì làm Activity phình thêm.

Nếu thay đổi chỉ liên quan cách hiển thị, dừng ở `app/ui`; không cần use case.
Nếu use case không cần hệ thống bên ngoài, không cần tạo port hay class trong `platform`.

Sơ đồ ở mục 1 là thứ tự gọi lúc chạy. Chiều import trong code khác:

```text
app/ui        → feature
platform      → interface trong feature/application/port
AppComposition → app/ui + feature + platform
```

`feature` không import ngược `app` hoặc `platform`.

## 3. Use case: class cho một việc người dùng cần

Ví dụ người dùng mở một thư mục media:

```java
public final class BrowseMediaUseCase {
    private final MediaRepository repository;

    public BrowseMediaUseCase(MediaRepository repository) {
        this.repository = repository;
    }

    public List<MediaEntry> execute(String relativePath) throws Exception {
        return repository.list(relativePath);
    }
}
```

UI gọi thẳng `BrowseMediaUseCase`. Không tạo thêm:

```text
interface BrowseMediaUseCase
BrowseMediaUseCaseImpl
```

Lý do: ứng dụng chỉ có một hành động “duyệt media”. Thêm interface ở phía UI không tạo
ranh giới mới, nhưng làm tăng số file và số quy tắc phải nhớ.

Khi test use case, dùng use case thật và truyền bản giả của `MediaRepository`.

## 4. Port và adapter: phần nghiệp vụ cần, platform thực hiện

`port` là interface do phần nghiệp vụ định nghĩa để yêu cầu một khả năng từ hệ thống bên ngoài.
Port không biết Android, DB, SDK hoặc nhà cung cấp cụ thể.

`adapter` là class trong `platform` triển khai port và trực tiếp dùng Android, DB, SDK hoặc nhà
cung cấp cụ thể.

```java
public interface MediaRepository {
    List<MediaEntry> list(String relativePath) throws Exception;
}
```

Class làm việc thật nằm trong `platform`:

```java
public final class LocalMediaRepository implements MediaRepository {
    // Đọc DcamStorage và trả về MediaEntry.
}
```

Tên thường dùng:

| Nhu cầu | Tên interface | Tên class cụ thể |
|---|---|---|
| Đọc/lưu một nhóm dữ liệu | `MediaRepository` | `LocalMediaRepository` |
| Lưu một giá trị hoặc trạng thái nhỏ | `FeatureGateStore` | `AndroidFeatureGateStore` |
| Gọi thiết bị hoặc SDK | `CameraGateway` | `SharedCameraGateway` triển khai `CameraGateway` |

Không bắt buộc dùng đúng hậu tố nếu tên khác rõ hơn. Điều bắt buộc là interface nói về nhu
cầu của nghiệp vụ; class cụ thể nói rõ cách làm hoặc nhà cung cấp.

## 5. Interface lệnh và interface sự kiện

Không phải interface nào cũng là port tới DB hoặc phần cứng. Dự án còn hai loại:

- `RecordingCommands`: nhiều nguồn input như màn hình và nút cứng cùng gọi một nhóm lệnh.

- `CaptureEvents`: camera gửi sự kiện hoàn tất hoặc lỗi ngược về phần ứng dụng.

Chỉ tạo các interface kiểu này khi có nhiều bên thật sự cần giao tiếp. Không đổi tên chúng
thành `*UseCase`.

Mặc định vẫn dùng class cụ thể cho `ViewModel`, renderer, model, use case và helper. Không
tạo interface chỉ để test hoặc vì “sau này có thể cần”.

### Process restart khi đang quay

Nếu process chết giữa lúc ghi hình hoặc ghi âm:

- App không tự bắt đầu đoạn ghi mới.
- Startup đổi capture còn dang dở sang trạng thái `finalizing`.
- `AppComposition` chỉ chạy recovery cho file trong `Temp`.
- Khi đúng file đã được đưa vào thư mục cuối, app xóa marker finalizing.
- Nếu storage chưa sẵn sàng, marker giữ nguyên để lần sau thử lại.

Switch vật lý là ngoại lệ do input thật: khi platform báo switch đang bật, `HardwareButtonRouter` mới gửi lệnh bắt đầu quay. App không tự đoán vị trí switch nếu platform không cung cấp trạng thái đó.

## 6. Mỗi khu chứa gì

```text
app/
├── AppComposition.java       tạo và nối các class
├── MainActivity.java         điểm vào màn hình Android
├── devmode/                  thông tin hiển thị cho công tắc nhà phát triển
└── ui/                       ViewModel, trạng thái, renderer, navigation, input

feature/<name>/
├── domain/                   model và quy tắc Java thuần, khi thật sự cần
└── application/
    ├── usecase/              class cho hành động của ứng dụng
    └── port/                 interface cho việc ở bên ngoài

platform/
└── <name>/                   class dùng Android, Room, file, camera, phần cứng, mạng

core/
└── ...                       Java thuần được từ hai phần trở lên dùng chung
```

`feature` và `core` không import Android, `app` hoặc `platform`. `platform` không import
`app`.

`Activity`, `Service`, `Receiver` và `Application` là class cụ thể vì Android tạo chúng.
Chúng chỉ làm phần Android bắt buộc rồi gọi code đã được nối từ `AppComposition`.

## 7. Ví dụ đầy đủ: duyệt media

```text
Chiều yêu cầu:
MainViewModel → BrowseMediaUseCase → MediaRepository (port)
→ LocalMediaRepository (adapter) → DcamStorage

Chiều kết quả:
DcamStorage → LocalMediaRepository → BrowseMediaUseCase
→ MainViewModel → MediaBrowserState → MediaBrowserRenderer
```

1. UI nhận thao tác mở thư mục.
2. `MainViewModel` gọi `BrowseMediaUseCase`.
3. Use case gọi `MediaRepository` vì đọc file là việc bên ngoài phần nghiệp vụ.
4. `LocalMediaRepository` dùng `DcamStorage` để đọc hệ thống tệp.
5. `MainViewModel` cập nhật `MediaBrowserState`.
6. `MediaBrowserRenderer` đọc trạng thái và vẽ danh sách.
7. `AppComposition` nối các class:

```java
MediaRepository mediaRepository = new LocalMediaRepository(storage);
browseMedia = new BrowseMediaUseCase(mediaRepository);
```

Nếu sau này dữ liệu chuyển sang DB, `BrowseMediaUseCase` có thể giữ nguyên. Chỉ class triển
khai `MediaRepository` và cách nối trong `AppComposition` thay đổi.

## 8. `core/featuregate` và `app/devmode`

Hai package này không làm cùng một việc:

- `FeatureGate` trong `core/featuregate` giữ tên, giá trị mặc định, key lưu trữ và quan hệ
  gate cha/con.
- `FeatureGates` đọc/ghi trạng thái và tính feature có được chạy thật hay không.
- `platform/featuregate` lưu trạng thái bằng Android `SharedPreferences`.
- `app/devmode` chỉ gắn một gate với nhãn, nhóm và `SettingId` để màn hình nhà phát triển
  hiển thị. Nó chuyển yêu cầu đọc/ghi sang `FeatureGates`, không tự tính lại quy tắc bật/tắt.
- `app/ui` vẽ công tắc và nhận thao tác.

Vì vậy metadata chỉ nằm trong `FeatureGate`; cách tính trạng thái bật thực tế chỉ nằm trong
`FeatureGates`.

## 9. Kiểm tra

- Test use case bằng use case thật và bản giả của port.
- Test class `platform` khi nó xử lý path, dữ liệu, lưu file hoặc chuyển đổi từ SDK.
- Chạy test ranh giới package:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.dvid.dcam.architecture.LayerDependencyTest
```

Hướng dẫn thêm feature mới nằm trong `FEATURE_DEVELOPMENT_GUIDE.md`.
