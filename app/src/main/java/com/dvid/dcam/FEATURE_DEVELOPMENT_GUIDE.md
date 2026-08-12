# Hướng dẫn phát triển tính năng

Tài liệu này trả lời hai câu hỏi: nên đặt mã nguồn ở đâu, và khi nào cần interface.
Đọc `ARCHITECTURE.md` nếu chưa rõ ba khu `app/ui`, `feature`, `platform`.

- Use case điều phối một hành động của ứng dụng.
- `domain` chứa model, phép tính hoặc quy tắc nghiệp vụ Java thuần.
- `port` mô tả khả năng mà phần nghiệp vụ cần từ bên ngoài.
- `adapter` trong `platform` triển khai port và gọi Android, DB, SDK hoặc phần cứng.

## 1. Bắt đầu bằng một câu về người dùng

Viết yêu cầu thành một hành động cụ thể, ví dụ:

- “Người dùng mở một thư mục media.”
- “Người dùng lưu số sê-ri thiết bị.”
- “Người dùng bấm nút cứng để dừng ghi video.”

Đừng bắt đầu bằng “tạo controller”, “tạo service” hoặc “tạo repository”. Những tên đó chỉ
xuất hiện sau khi đã biết việc cần làm.

## 2. Chọn nơi đặt code

| Câu hỏi | Làm gì |
|---|---|
| Chỉ đổi giao diện, trạng thái màn hình, chuyển màn hình (navigation) hoặc vòng đời Android? | Sửa `app/ui`, rồi dừng. |
| Có hành động như kiểm tra dữ liệu, bắt đầu/dừng ghi hoặc lưu cài đặt? | Tạo hoặc sửa class `*UseCase` trong `feature/<name>/application/usecase`. |
| Có quy tắc hoặc phép tính Java thuần? | Đặt trong `feature/<name>/domain` và gọi từ use case. |
| Cần DB, file, Android SDK, camera, phần cứng hoặc mạng? | Tạo interface trong `application/port` và class cụ thể trong `platform`. |
| Logic Java thuần được nhiều feature dùng thật? | Cân nhắc `core`; không dùng `core` làm chỗ tạm. |

Không phải tính năng nào cũng cần đủ mọi phần. Thay đổi chỉ ở giao diện không cần use case.
Use case không truy cập hệ thống bên ngoài thì không cần port hoặc adapter. Chỉ tạo `domain` khi
có quy tắc nghiệp vụ Java thuần cần tách riêng.

## 3. Quy trình ngắn

1. Phân loại thay đổi: UI, hành động của ứng dụng, hay gọi hệ thống bên ngoài.
2. Nếu có hành động của ứng dụng, tạo class `*UseCase` có dạng `public final class`.
3. Nếu cần DB, file, camera, phần cứng hoặc mạng, thêm interface trong `application/port`
   và class cụ thể trong `platform`.
4. Nối các class trong `AppComposition`, truyền use case vào nơi gọi, rồi viết test.

Không tạo thư mục hoặc file trống để “đủ kiến trúc”. Dừng ngay khi đã đủ cho yêu cầu hiện tại.

## 4. Ví dụ: duyệt media

Yêu cầu: người dùng mở màn hình Files và xem nội dung một thư mục.

### Bước 1: class use case

File: `feature/media/application/usecase/BrowseMediaUseCase.java`

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

Không tạo `BrowseMediaUseCase` interface và `BrowseMediaUseCaseImpl`. UI gọi class trên trực
tiếp.

### Bước 2: interface cho việc đọc dữ liệu

File: `feature/media/application/port/MediaRepository.java`

```java
public interface MediaRepository {
    List<MediaEntry> list(String relativePath) throws Exception;
}
```

`MediaRepository` là port mô tả khả năng đọc danh sách media mà use case cần. Nó không chứa `Context`, `File`, `Cursor` hoặc class Android.

### Bước 3: class đọc hệ thống tệp

File: `platform/storage/LocalMediaRepository.java`

```java
public final class LocalMediaRepository implements MediaRepository {
    // Đọc DcamStorage và trả về MediaEntry.
}
```

Xử lý path, file, MIME type và API Android nằm ở đây.

### Bước 4: nối các class

Trong `AppComposition`:

```java
MediaRepository mediaRepository = new LocalMediaRepository(storage);
browseMedia = new BrowseMediaUseCase(mediaRepository);
```

`MainViewModel` nhận `browseMedia`, gọi nó, rồi cập nhật `MediaBrowserState`.
`MediaBrowserRenderer` chỉ đọc trạng thái và vẽ.

## 5. Chọn class hay interface

Dùng class cụ thể theo mặc định.

Tạo interface khi có một trong các nhu cầu cụ thể sau:

- Phần nghiệp vụ cần DB, file, Android SDK, camera, phần cứng hoặc mạng.
- Nhiều nguồn input cùng gọi một nhóm lệnh, như `RecordingCommands`.

- Một phần gửi sự kiện về phần khác, như `CaptureEvents`.

Không tạo interface chỉ vì:

- muốn fake chính use case trong test;
- hiện có một class nhưng đoán sau này sẽ có class thứ hai;
- muốn mọi tên đều có cặp interface/`Impl`.

Tên gợi ý:

| Vai trò | Ví dụ |
|---|---|
| Hành động của ứng dụng, luôn là class | `BrowseMediaUseCase`, `DeviceSerialNumberUseCase` |
| Đọc/lưu dữ liệu | `MediaRepository` |
| Lưu giá trị hoặc trạng thái nhỏ | `FeatureGateStore`, `DeviceSerialNumberStore` |
| Gọi thiết bị hoặc SDK | `CameraGateway` |
| Nhóm lệnh | `RecordingCommands` |
| Sự kiện gửi ngược lại | `CaptureEvents` |
| Class cụ thể | `LocalMediaRepository`, `AndroidFeatureGateStore` |

Không thêm tiền tố `I`. Không thêm hậu tố `Impl` cho use case.

### Khi app khởi động sau process restart

App không tự quay lại. Nếu lần chạy trước còn file trong `Temp`, startup chỉ đánh dấu capture cũ là đang hoàn tất rồi gọi cơ chế finalize file. Không tạo đoạn video hoặc audio mới.

Switch vật lý vẫn có thể bắt đầu quay, nhưng chỉ sau khi Android gửi event switch đang bật. Không tự thêm API đọc trạng thái switch khi chưa có bằng chứng từ thiết bị.

## 6. Công tắc nhà phát triển

Chỉ thêm công tắc khi sản phẩm cần tắt/mở tính năng trong màn hình nhà phát triển.

1. Thêm hoặc cập nhật `FeatureGate` trong `core/featuregate`.
2. Trong `FeatureGate`, khai báo giá trị mặc định. Chỉ khai báo quan hệ gate cha/con khi một công tắc phụ thuộc công tắc khác. `FeatureGates` tính trạng thái bật cuối cùng.
3. Đăng ký nhãn, nhóm và `SettingId` trong `app/devmode/DeveloperFeatureToggles`.
4. Để `app/ui` hiển thị công tắc.
5. Không đọc `SharedPreferences` hoặc tính lại gate cha trong `app/devmode`.

Chi tiết nằm trong `app/devmode/README.md`.

## 7. Trước khi gửi duyệt

- [ ] UI/input gọi class `*UseCase`; không gọi thẳng `platform`.
- [ ] `*UseCase` là class cụ thể; không có interface cùng tên hoặc `*UseCaseImpl`.
- [ ] Việc gọi DB, file, SDK, thiết bị hoặc mạng đi qua interface trong `application/port`.
- [ ] Class cụ thể nằm trong `platform`; `AppComposition` là nơi nối các class.
- [ ] Test hành vi và `LayerDependencyTest` chạy được.

Nếu vẫn chưa biết đặt file, hỏi đúng một câu:

```text
Phần này chỉ hiển thị UI, điều phối hành động trong use case, áp dụng quy tắc nghiệp vụ Java
thuần trong domain, hay truy cập hệ thống bên ngoài qua port/adapter?
```
