# Kiến trúc source Android DCAM

DCAM dùng **feature-first Clean Architecture** kết hợp tư duy
**Ports and Adapters / Hexagonal Architecture** trong hai Gradle modules:
Android application `:app` và pure-Java library `:core`.

Điểm quan trọng: folder `application/port` vẫn giữ tên `port` vì đó là thuật
ngữ kiến trúc, nhưng **tên file/class không dùng hậu tố `Port`**. Tên class nên
nói rõ capability nghiệp vụ, ví dụ `CameraGateway`, `AudioRecorder`,
`LanguagePreferenceStore`.

## UI notification convention

Transient floating notices must use `FloatingNotice`; do not create `Toast` or another floating
notice style directly at call sites. Camera preview text is reserved for camera status and errors.

## 1. Layer chuẩn

```text
domain ← application ← presentation/app/platform
```

| Layer | Trách nhiệm | Không được làm |
|---|---|---|
| `domain` | Entity, value object, enum, rule thuần Java | Import Android, SDK, UI, platform |
| `application/usecase` | Workflow/application action | Biết Activity, CameraX, filesystem thật |
| `application/port` | Interface application cần để gọi repository/hardware/provider | Nhắc tên Android/vendor cụ thể |
| `presentation` / `app` | ViewModel, UI state, navigation, Activity shell | Chứa business workflow dài |
| `platform` | Android, CameraX, MediaRecorder, filesystem, Room, WorkManager | Định nghĩa policy nghiệp vụ |
| `core` | Capability dùng chung thật sự | Gom code vào để né dependency |

Runtime có thể đi từ UI ra platform rồi callback ngược về UI, nhưng dependency
compile-time vẫn phải hướng vào trong.

## 2. Cấu trúc package hiện tại

```text
com.dvid.dcam/
├── app/
│   ├── MainActivity
│   ├── AppComposition
│   ├── navigation/
│   └── presentation/
├── feature/
│   └── <feature>/
│       ├── domain/
│       ├── application/
│       │   ├── usecase/
│       │   ├── port/
│       │   └── repository/        chỉ khi repository implementation thuần app/core
│       └── presentation/          chỉ khi feature có presentation riêng
├── platform/
│   ├── camera/
│   ├── audio/
│   ├── storage/
│   ├── device/
│   ├── input/
│   ├── config/
│   ├── database/
│   ├── recording/
│   └── logging/
└── core/
    └── <capability>/
        ├── domain/
        └── application/
            ├── port/
            └── repository/
```

Không tạo package `interfaces`, `classes`, `implementations`, `services`,
`adapter/in`, `adapter/out`, `port/in`, hoặc `port/out` trong source hiện tại.
Nếu sau này cần tách thêm layer, phải có ADR hoặc yêu cầu review rõ ràng.

## Android app shell boundary

app/shell contains Android-specific View, Activity controller, and floating-notice classes.
app/presentation remains framework-free presentation logic and must not import android.* or platform.*.

## 3. Quy tắc interface

Có hai nhóm public interface trong `feature`/`core`.

### Use case interface

Nằm trong `application/usecase`. Đây là API mà UI, hardware router hoặc
composition gọi vào application.

```java
public interface VideoRecordingUseCase {
    void toggleVideo();
    void startVideo();
    void startSos();
    void stopRecording();
    void toggleSos();
}
```

Implementation đơn giản nằm cùng folder và kết thúc bằng `Impl`. Application service có trách
nhiệm orchestration riêng được đặt tên theo trách nhiệm đó:

```java
public final class SerializedRecordingCoordinator
        implements VideoRecordingUseCase, CaptureEventUseCase {
    // one serialized authority for recording commands and camera events
}
```

Một use case interface được phép gom nhiều operation cùng một nghiệp vụ nhỏ,
miễn là cùng một actor/lý do thay đổi. Ví dụ `VideoRecordingUseCase` gom
start/stop/toggle video và SOS.

### Capability boundary trong `application/port`

Đây là interface application cần để gọi ra repository, hardware, storage,
logging hoặc platform capability. Tên class **không dùng `Port`**.

Ví dụ hiện tại:

```text
CameraGateway
AudioRecorder
MediaRepository
MediaOpener
DeviceRepository
LanguagePreferenceStore
ConfigurationSource
ConfigurationRepository
LogSink
```

Concrete implementation phải kết thúc bằng `Impl` và nói rõ provider/strategy:

```text
CameraXCameraGatewayImpl
AndroidAudioRecorderImpl
LocalMediaRepositoryImpl
AndroidMediaOpenerImpl
AndroidLanguagePreferenceStoreImpl
CsonConfigurationSourceImpl
DcamLogSinkImpl
```

Rule dễ nhớ:

> Folder nói kiến trúc. Class name nói capability. Concrete class nói provider.

## 4. Use case khác port như thế nào?

```text
UI / hardware key ──> VideoRecordingUseCase ─┐
                                             ├─> SerializedRecordingCoordinator
Camera callback ─────> CaptureEventUseCase ──┘              ↓
                                                        CameraGateway
                                                             ↑
                                                CameraXCameraGatewayImpl
```

- `UseCase` là cổng đi vào application.
- Interface trong `application/port` là cổng đi ra khỏi application.
- `platform/*Impl` là lớp ngoài cùng dùng Android/framework thật.

Vì team chọn convention chặt, use case luôn có interface và implementation
`Impl`. Capability boundary cũng là interface, nhưng tên không cần chữ `Port`.

## 5. Luồng capture hiện tại

```text
MainActivity / HardwareButtonRouter
    ↓
PhotoCaptureUseCase / VideoRecordingUseCase / AudioRecordingUseCase
    ↓
CameraGateway / AudioRecorder
    ↑
CameraXCameraGatewayImpl / AndroidAudioRecorderImpl
    ↓
CameraX / MediaRecorder / DcamMediaOutputImpl
    ↓ callback
CaptureEventUseCase
    ↓
MainViewModel -> MainUiState -> UI
```

`HardwareButtonRouter` và touch UI đi vào cùng use case, nên không có business
flow riêng cho nút cứng.

## 6. Composition root

`AppComposition` là nơi duy nhất chọn concrete implementation:

```text
CameraGateway        -> CameraXCameraGatewayImpl
Camera preview UI    -> CameraXPreviewView
AudioRecorder        -> AndroidAudioRecorderImpl
MediaRepository      -> LocalMediaRepositoryImpl
LanguagePreferenceStore -> AndroidLanguagePreferenceStoreImpl
ConfigurationSource  -> CsonConfigurationSourceImpl
LogSink              -> DcamLogSinkImpl
```

Use case không tự `new` platform implementation. Activity/ViewModel không gọi
CameraX, MediaRecorder, filesystem hoặc Room trực tiếp.

## 7. MVVM, MVP, Clean Architecture

- **MVVM**: cách tổ chức presentation: View/Activity → ViewModel → observable
  state.
- **Clean Architecture**: cách tổ chức dependency/layer toàn app.
- **Ports and Adapters**: cách đặt boundary để application không biết framework.
- **MVP** trong tài liệu sản phẩm nghĩa là Minimum Viable Product, không phải
  Model-View-Presenter.

Các khái niệm này không xung đột.

## 8. Quy tắc được test enforce

`LayerDependencyTest` kiểm tra:

- chỉ dùng bốn top-level package `app`, `core`, `feature`, `platform`;
- feature/core Java file phải nằm trong path layer chuẩn;
- domain/application không import Android, Google SDK, app hoặc platform;
- public interface trong feature/core chỉ nằm ở `application/usecase` hoặc
  `application/port`;
- public interface phải có callable behavior hiện tại, không tạo marker/placeholder rỗng;
- implementation của interface project phải kết thúc bằng `Impl`;
- không dùng package `adapter` trong feature/core source hiện tại.

## 9. Boundary chuyển tiếp cần nhớ

- `MainViewModel` vẫn là app-level ViewModel phối hợp nhiều feature. Khi một
  feature có màn hình/lifecycle riêng, tạo presentation riêng trong feature đó.
- `CameraXPreviewView` giữ preview UI/CameraX surface; `CameraXCameraGatewayImpl`
  giữ recording/capture ownership phía platform. Nếu chuyển sang vendor SDK hoặc
  recovery/process-death owner, giữ UI preview tách khỏi recording owner.
- `DcamMediaOutput` là SPI nội bộ platform, không truyền vào feature/domain.
- Gradle compiler enforce chiều phụ thuộc `:app` → `:core`; `:core` không thể
  import Android, app, feature hoặc platform code.
- Boundary bên trong `:app` giữa app/feature/platform tiếp tục được enforce bằng
  architecture tests. Chỉ tách module tiếp khi có trigger đã được duyệt.
