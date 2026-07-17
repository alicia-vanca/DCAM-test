# Default Settings and Delete Empty Date Folders

**Page ID**: 51052612  
**Version**: 3  
**Type**: page  
**URL**: https://ducviet.atlassian.net/wiki/spaces/DVID/pages/51052612

---


# Settings Enhancement: Default Settings and Delete Empty Date Folders

**Item**

**Information**

**Project**

BDMA Desktop

**Document Type**

Feature Overview / Technical Design

**Version**

Draft 1.0

**Status**

DRAFT

**Owner**

Duchm245

**Technical Reviewer**

Tech Lead

**Approver**

TBD

**Parent Folder**

BDMA Technical Documentation

**Target Audience**

PM/BA, Tech Lead, Developers, QA

**Last Updated**

10 Jul 2026

**Related Jira**

[BDMA-158](https://ducviet.atlassian.net/jira/software/projects/BDMA/boards/36?selectedIssue=BDMA-158)

**Related Documents**

 [https://ducviet.atlassian.net/wiki/spaces/DVID/pages/29032450/Module+Sync+Backup?xpis=eyJicmlkZ2UiOiJyb3ZvLWNoYXQubWluaS1tb2RhbCIsImlkIjoiMTc4MzczMTg3NDgxMyIsInNvdXJjZSI6ImNvbmZsdWVuY2UifQ%3D%3D](https://ducviet.atlassian.net/wiki/spaces/DVID/pages/29032450/Module+Sync+Backup?xpis=eyJicmlkZ2UiOiJyb3ZvLWNoYXQubWluaS1tb2RhbCIsImlkIjoiMTc4MzczMTg3NDgxMyIsInNvdXJjZSI6ImNvbmZsdWVuY2UifQ%3D%3D) [Device Connection and Disconnection Process](/wiki/spaces/DVID/pages/26443811/Device+Connection+and+Disconnection+Process)  [https://ducviet.atlassian.net/wiki/spaces/DVID/pages/27132122/UI+Design?xpis=eyJicmlkZ2UiOiJyb3ZvLWNoYXQubWluaS1tb2RhbCIsImlkIjoiMTc4MzczMTg3NDgxMyIsInNvdXJjZSI6ImNvbmZsdWVuY2UifQ%3D%3D](https://ducviet.atlassian.net/wiki/spaces/DVID/pages/27132122/UI+Design?xpis=eyJicmlkZ2UiOiJyb3ZvLWNoYXQubWluaS1tb2RhbCIsImlkIjoiMTc4MzczMTg3NDgxMyIsInNvdXJjZSI6ImNvbmZsdWVuY2UifQ%3D%3D)

## 1. Mục đích và phạm vi

Tài liệu này mô tả chi tiết tính năng **Cài đặt mặc định** (Default Settings) và tùy chọn **Xóa các thư mục ngày nếu trống** (Delete Empty Date Folders) trong màn hình Settings của BDMA Desktop. Admin/Dev/User có thể reset các setting thuộc phạm vi màn hình của mình; Admin/Dev có thêm quyền bật cleanup thư mục ngày trống trên thiết bị sau đồng bộ.

### 1.1 Trong phạm vi

Nút "Cài đặt mặc định" trên màn hình Admin Settings, Dev Settings và User Settings.

Reset settings theo scope: `ADMIN`, `DEV`, `USER`.

Parent/child setting: "Tự động xóa file sau đồng bộ" (parent) và "Xóa thư mục ngày nếu trống" (child).

Remote cleanup: xóa media, `.md5` sidecar, và thư mục ngày trên thiết bị ADB internal/external và mass-storage.

Snapshot setting vào `SyncContext` tại thời điểm device vào queue.

Confirmation dialog, async volume scan, và guard blocking khi sync/backup/restore active.

Normalize dữ liệu cũ: parent=false, child=true → child=false.

### 1.2 Ngoài phạm vi

Thay đổi cơ chế autodelete hiện tại hoặc luồng sync hiện có.

Đồng bộ setting giữa các thiết bị hoặc user.

Audit log đầy đủ cho mỗi lần reset.

Giới hạn số lượng setting có thể reset.

### 1.3 Giả định

Admin/Dev có quyền truy cập app-level config (`app_config`), User chỉ tác động `user_config`.

ADB connection khả dụng tại thời điểm cleanup.

Date folder format cố định `uuuu-MM-dd`.

Sync context snapshot được tạo tại thời điểm device vào queue; setting thay đổi sau đó không ảnh hưởng device đang chờ/xử lý.

Guest và pre-login chỉ có quyền truy cập Settings dạng popup (Language/Theme), không thấy nút "Cài đặt mặc định".

## 2. Tổng quan

Tính năng **Cài đặt mặc định** cho phép nhanh chóng khôi phục các cấu hình về trạng thái chuẩn theo phân quyền Admin/Dev/User, giảm thao tác thủ công. Tùy chọn **Xóa thư mục ngày nếu trống** giúp giải phóng dung lượng trên thiết bị body camera sau đồng bộ, tránh tích tụ folder rỗng và tối ưu bộ nhớ thiết bị lâu dài.

## 3. Thao tác người dùng

### 3.1 Default Settings

**Màn hình**

**Thao tác**

**Mô tả**

Admin Settings (`Role.ADMIN`)

Click "Cài đặt mặc định" → confirm

Reset app-level và per-user settings của user đang đăng nhập; Registry cleared; storage folders reset; UI reloaded.

Dev Settings (`Role.DEV`)

Click "Cài đặt mặc định" → confirm

Reset app-level settings (Language, Theme, Sync/Backup folder, Start with Windows, auto-delete, child); storage folders reset; Registry cleared; Export không bị ảnh hưởng.

User Settings (`Role.USER`)

Click "Cài đặt mặc định" → confirm

Reset per-user settings của user đang đăng nhập (Language, Theme, Export, Ask every time); app-level và Registry không thay đổi.

Admin/Dev/User

Click "Cài đặt mặc định" → cancel/close

Không ghi DB, không đổi Registry, không reload UI.

Guest/pre-login

Mở Settings

Chỉ thấy popup (Language/Theme/Update); không có nút "Cài đặt mặc định".

### 3.2 Parent/Child Setting

**Màn hình**

**Thao tác**

**Mô tả**

Admin/Dev Settings (`Role.ADMIN`/`Role.DEV`)

Bật "Tự động xóa sau đồng bộ" (parent)

Persist parent=true; child checkbox enabled.

Admin/Dev Settings

Tắt parent

Persist parent=false và child=false; child disabled.

Admin/Dev Settings

Bật "Xóa thư mục ngày nếu trống" (child, chỉ khi parent on)

Persist child=true; cleanup date folder chạy ở lần sync tiếp theo.

## 4. Acceptance Criteria

**ID**

**Scenario**

**Expected result**

**AC-01**

Admin click "Cài đặt mặc định" và confirm

All app-level và per-user settings reset về default; Registry cleared; storage folders reset; UI reloaded.

**AC-02**

Dev click "Cài đặt mặc định" và confirm

Dev-scoped settings reset (Language, Theme, Sync/Backup folder, Start with Windows, auto-delete, child); storage folders reset; Registry cleared; Export không bị ảnh hưởng.

**AC-03**

User click "Cài đặt mặc định" và confirm

Chỉ per-user settings (language, theme, export) reset; app-level và registry không thay đổi.

**AC-04**

Admin/Dev/User mở confirmation và cancel/close

Không ghi DB, không đổi Registry, không reload UI.

**AC-05**

Bật parent (auto-delete) trong Admin/Dev Settings

Child (delete empty folders) enabled và click được.

**AC-06**

Tắt parent

Child tự động disable; persist cả parent=false và child=false.

**AC-07**

Load dữ liệu cũ có parent=false, child=true

Controller normalize child về false qua service.

**AC-08**

Sync thành công, autoDelete=true, deleteEmptyDateFolders=false

Media và .md5 sidecar bị xóa trên device; date folder được giữ.

**AC-09**

Sync thành công, autoDelete=true, deleteEmptyDateFolders=true

Media, .md5 sidecar bị xóa; date folder bị xóa nếu trống.

**AC-10**

Admin/Dev reset trong khi sync/backup/restore đang chạy

Reset bị block; controller kiểm tra trước và sau confirmation.

**AC-11**

Guest hoặc pre-login truy cập Settings

Không thấy Default Settings button.

**AC-12**

Volume scan khi reset (chỉ ADMIN/DEV)

Scan chạy background Executor ngay khi mở confirmation; kết quả truyền vào `resetToDefaults(userId, scope, preloadedVolumes)`. USER reset không scan volume.

**AC-13**

Sidecar .md5 cleanup fail khi child bật

`rmdir` thất bại (trả false); không delete recursive.

**AC-14**

Date folder chứa hidden entry hoặc subfolder

`rmdir` thất bại; folder được giữ nguyên.

**AC-15**

Date folder không đúng format `uuuu-MM-dd`

Bỏ qua, không xóa.

**AC-16**

Mass-storage fallback

Cleanup media, `.md5` sidecar và date folder trên mass-storage qua drive letter tương tự ADB.

## 5. Luồng chính

### 5.1 Settings UI Flow (Default Settings)

Admin/Dev: AdminSettingsDialogController
User:      UserSettingsDialogController
        |
        v
AdminSettingsDialogService
        |
        +-- UserSettingService -> user_config
        +-- AppConfigService  -> app_config
        +-- FolderManagerService.resetDefaultStoragePaths(...)
        +-- Windows Registry HKCU Run entry

User click "Cài đặt mặc định" → controller hiển thị confirmation dialog.

Controller gọi `adminSettingsService.discoverStorageVolumes()` trong background Executor ngay khi mở confirmation.

Nếu user confirm, controller kiểm tra sync/backup/restore active (kiểm tra trước và sau confirmation; chỉ áp dụng cho ADMIN/DEV, USER không bị block).

Gọi `resetToDefaults(userId, scope, preloadedVolumes)` → xuống `FolderManagerService.resetDefaultStoragePaths(preloadedVolumes)`.

DB, Registry, folder resolver được cập nhật; UI reload theo scope.

Admin và Dev dùng chung logic từ `AdminSettingsDialogController`; Dev override `getResetScope()` trả `Role.DEV`. User có controller riêng, chỉ reset per-user settings.

### 5.2 Sync Cleanup Flow (Delete Empty Date Folders)

AdminLayoutController
        |
        | snapshots autoDelete + deleteEmptyDateFolders vào SyncContext
        v
DeviceSyncQueue
        |
        v
DataSyncWorker
        |
        +-- RemoteMediaCleanupService.deleteRemoteFile(...)
        |       +-- ADB: AdbClient.deleteRemoteFile(... rm -f ...)
        |       +-- Mass-storage: MassStorageService.deleteFile(...)
        |
        +-- RemoteMediaCleanupService.cleanupEmptyDateFolders(...)
                +-- ADB: AdbClient.deleteRemoteDirectoryIfEmpty(... rmdir ...)
                +-- Mass-storage: MassStorageService.deleteDirectoryIfEmpty(...)

`AdminLayoutController` snapshot `autoDelete` và `deleteEmptyDateFolders` vào `SyncContext` khi queue device.

`DataSyncWorker` nhận context và gọi `RemoteMediaCleanupService.deleteRemoteFile(...)` sau pull/verify hoặc khi xử lý file đã sync từ trước.

Nếu `deleteEmptyDateFolders=true`, service ghi nhận để cleanup date folder ở cuối sync pass (không rmdir trong bước xóa file).

Cuối sync pass: `cleanupEmptyDateFolders()` quét các date folder và thử `rmdir` nếu trống. Orphan `.md5` cleanup diễn ra trong `collectSyncFiles`, không nằm trong method này.

Setting được snapshot tại thời điểm device vào queue. Nếu user đổi setting trong lúc device đang chờ hoặc đang sync, context hiện tại vẫn dùng giá trị cũ; device queue sau mới nhận giá trị mới.

## 6. Thành phần chính

**Component**

**Responsibility**

`AdminSettingsDialogController`

Bind i18n, xử lý parent/child state, confirmation reset, prefetch storage volumes, reload UI sau reset.

`DevSettingsDialogController`

Kế thừa Admin controller; reset theo `Role.DEV`.

`UserSettingsDialogController`

Reset per-user settings (language, theme, export) theo `Role.USER`.

`AdminSettingsDialogService`

Đọc/ghi app-level settings, enforce `child=true => parent=true`, cung cấp `isResetBlocked(scope)` để controller chặn reset khi sync/backup/restore active, reset defaults theo scope.

`FolderManagerService`

Resolve và ghi lại default Sync/Backup folder qua `resetDefaultStoragePaths(preloadedVolumes)`.

`SyncContext`

DTO mang snapshot `autoDelete` và `deleteEmptyDateFolders` vào sync pipeline.

`AdminLayoutController`

Lấy parent/child setting và tạo `SyncContext` khi queue device.

`DataSyncWorker`

Gọi cleanup sau sync và cuối sync pass.

`DeviceSyncQueue`

Queue nhận `SyncContext` snapshot khi device vào hàng chờ; dedup device, cấp API `add`/`take`/`done`/`isActive`.

`RemoteMediaCleanupService`

Xóa media, `.md5` sidecar, validate date-folder, cleanup date folder sau sync (cả ADB và mass-storage).

`AdbClient`

Thao tác xóa file/folder và list directory qua ADB shell.

`MassStorageService`

Thao tác xóa file/folder trên mass-storage qua drive letter.

`PreLoginSettingsPopupController` + popup FXML

Settings popup cho Guest/pre-login (Language/Theme/Update, không có nút Default Settings).

`admin-settings-dialog.fxml`

FXML Admin Settings (có Default Settings button + child checkbox).

`dev-settings-dialog.fxml`

FXML Dev Settings (ẩn Export card).

`user-settings-dialog.fxml`

FXML User Settings (không có child cleanup).

## 7. Thay đổi Database

Không có migration hoặc bảng mới. Feature dùng schema key-value hiện có.

### 7.1 `app_config`

**Key**

**Default**

**Ghi chú**

`dataSync.isAutoDeleteAfterSync`

`false`

Parent setting (key có sẵn): tự động xóa file trên thiết bị sau sync.

`dataSync.isDeleteEmptyDateFolderAfterSync`

`false`

Child setting mới: chỉ có hiệu lực khi parent=true.

`dataSync.syncDir`

Resolver trong `FolderManagerService`

Reset ghi lại default Sync folder (default động theo volume).

`dataBackup.backupDir`

Resolver trong `FolderManagerService`

Reset ghi lại default Backup folder theo volume resolver (default động theo volume).

`appConfig.isStartWithWindows`

`false`

Reset tắt startup và xóa Registry entry best-effort.

### 7.2 `user_config`

**Key**

**Default**

**Scope**

`language`

`VI`

Admin, Dev, User

`theme`

`LIGHT`

Admin, Dev, User

`user.export.dir`

`%USERPROFILE%\Downloads`

Admin, User

`export.lastDir`

`%USERPROFILE%\Downloads`

Admin, User (lưu ý: key không có prefix `user.`)

`user.export.askEveryTime`

`false`

Admin, User

## 8. Configuration

### 8.1 Scope reset (Role-based)

Scope được xác định bằng `Role` enum (`ADMIN`, `DEV`, `USER`).

**Scope**

**Reset**

`ADMIN`

Language, Theme, Export folder, Ask every time export, Sync folder, Backup folder, Start with Windows, auto-delete parent, delete-empty-date-folder child.

`DEV`

Language, Theme, Sync folder, Backup folder, Start with Windows, auto-delete parent, delete-empty-date-folder child. Export setting đang ẩn nên không reset.

`USER`

Language, Theme, Export folder, Ask every time export — của user đang đăng nhập. Không chạm app-level config, storage folder hoặc Registry.

### 8.2 Parent/Child Setting

Ba trạng thái hợp lệ:

**Parent auto-delete**

**Child delete empty folders**

**Child enabled**

Off

Off

NO

On

Off

YES

On

On

YES

Rules:

Child chỉ click được khi parent bật.

Tắt parent sẽ persist parent=false và child=false.

Khi load dữ liệu cũ có parent=false nhưng child=true, controller normalize về false qua service.

Service `setAutoDeleteState(autoDelete, deleteEmptyDateFolders)` luôn enforce invariant `child=true => parent=true`.

## 9. Edge Cases và Error Handling

**Tình huống**

**Hành vi mong đợi**

**Trạng thái**

Reset trong lúc sync/backup/restore active

Bị block bởi service; controller kiểm tra trước và sau confirmation; USER không bị block.

DEFINED

Parent=false, child=true khi load dữ liệu cũ

Service normalize child về false.

DEFINED

Delete startup Registry entry thất bại

Non-fatal; vẫn persist config=false.

DEFINED

Sidecar `.md5` cleanup fail khi child bật

`rmdir` thất bại (trả false); không delete recursive — safe behavior.

DEFINED

Date folder chứa hidden entry hoặc subfolder

`rmdir` thất bại, folder được giữ nguyên.

DEFINED

Date folder không đúng format `uuuu-MM-dd` hoặc sai cấu trúc

Bỏ qua, không xóa. Storage root, media-type folder, nested folder sai, folder malformed đều được skip.

DEFINED

Mass-storage fallback

Cleanup media, `.md5` sidecar và date folder trên mass-storage qua drive letter tương tự ADB.

DEFINED

Volume scan khi reset (chỉ ADMIN/DEV)

Scan chạy background `Executor` ngay khi mở confirmation; kết quả preloadedVolumes truyền vào `resetToDefaults`. USER reset không scan volume.

DEFINED

Setting thay đổi khi device đang trong queue

Context snapshot giữ giá trị cũ; device queue sau nhận giá trị mới.

DEFINED

Batch input rỗng (reset không có setting nào)

Service vẫn ghi default cho mọi key trong scope; không phát event riêng.

DEFINED

Confirmation mở nhưng user đổi scope giữa chừng

Scope cố định theo controller; confirmation chỉ dùng cho scope hiện tại.

DEFINED

Device mất kết nối ADB giữa lúc cleanup

`AdbClient` trả lỗi; cleanup fail an toàn; không retry.

DEFINED

## 10. UX/UI Specification

Chi tiết behavior (thao tác, confirmation, blocking, parent/child state) xem §3, §4, và §9. Mục này chỉ mô tả các yếu tố UI thuần.

### 10.1 Default Settings

Nút "Cài đặt mặc định" nằm ở card footer của Admin Settings, Dev Settings và User Settings.

Guest/pre-login: popup chỉ có Language/Theme/Update (xem `prelogin-settings-popup.fxml`); không có nút Default Settings.

Confirmation dialog dùng `AlertHelper.createConfirmation` (tiêu đề + content i18n).

### 10.2 Delete Empty Date Folders

Child checkbox "Xóa thư mục ngày nếu trống" nằm dưới parent "Tự động xóa file sau đồng bộ".

Child indent một cấp so với parent, dùng CSS class `.settings-child-row`.

Child `disable`/`unchecked` khi parent off (xem §8.2 cho bảng trạng thái hợp lệ).

### 10.3 Remote Cleanup Detail

Sidecar `.md5` được tính bằng cách thay extension cuối: `video.mp4` → `video.md5`, `name_enc.mp4` → `name_enc.md5`. Không dùng `remotePath + ".md5"`.

Khi `deleteEmptyDateFolders=true`, service xóa thư mục ngày ở cuối sync pass qua `cleanupEmptyDateFolders()`; `rmdir` chỉ thành công nếu folder thật sự trống; folder còn file, hidden entry hoặc subfolder được giữ.

Mass-storage: cleanup file/folder qua drive letter (`MassStorageService`), tương tự ADB.

Validation date-folder: cấu trúc kỳ vọng `<storage-root>/<media-type>/<uuuu-MM-dd>/<file>`. Media type thuộc `FileType.ALL_VALUES`. Date parse strict theo `uuuu-MM-dd`.

## 11. Known limitations

**Giới hạn**

**Ảnh hưởng**

**Hướng xử lý**

`resetToDefaults` trả `boolean` và log lỗi, không có model kết quả chi tiết

Không phân biệt được "rollback-incomplete" vs "thất bại toàn bộ" ở UI.

Có thể nâng cấp sau bằng `SettingsResetResult` với trạng thái chi tiết.

Reset là best-effort, không có transaction nguyên tử chung

DB/Registry/folder resolver được ghi độc lập; nếu một bước thất bại, các bước khác vẫn đã ghi.

Chấp nhận rủi ro thấp; service trả `false` và log error để báo reset thất bại.

Lỗi xóa Registry entry được xem là non-fatal

Vẫn lưu `appConfig.isStartWithWindows=false` dù Registry entry còn (entry có thể đã không tồn tại).

`resetToDefaults` catch `IllegalStateException` từ `setStartWithWindows(false)`, log warning, vẫn trả `true`.

Date-folder format cố định `uuuu-MM-dd`

Nếu body camera model mới dùng format khác, có thể bỏ sót cleanup.

Chỉ mở rộng bằng allow-list strict format, không nới validation thành xóa folder tùy ý.

### 11.1 Verification notes (internal)

Item

Ghi chú

`cleanupEmptyDateFolders()` thử `rmdir` khi child bật kể cả khi sidecar cleanup fail

Nếu sidecar còn tồn tại, `rmdir` thất bại. Safe vì không dùng recursive delete. Hành vi hiện tại là đúng.

Admin và Dev dùng chung controller (`AdminSettingsDialogController`); FXML riêng (Dev ẩn Export card bằng `visible/managed=false`)

Dev override `getResetScope()`. Cần đảm bảo Export setting ẩn đúng trên Dev — kiểm tra bằng smoke test.

## 12. Tài liệu liên quan

[https://ducviet.atlassian.net/wiki/spaces/DVID/pages/29032450/Module+Sync+Backup?xpis=eyJicmlkZ2UiOiJyb3ZvLWNoYXQubWluaS1tb2RhbCIsImlkIjoiMTc4MzczMTg3NDgxMyIsInNvdXJjZSI6ImNvbmZsdWVuY2UifQ%3D%3D](https://ducviet.atlassian.net/wiki/spaces/DVID/pages/29032450/Module+Sync+Backup?xpis=eyJicmlkZ2UiOiJyb3ZvLWNoYXQubWluaS1tb2RhbCIsImlkIjoiMTc4MzczMTg3NDgxMyIsInNvdXJjZSI6ImNvbmZsdWVuY2UifQ%3D%3D)  — mô tả DataSyncWorker, SyncContext, DeviceSyncQueue — đây là pipeline mà "Delete Empty Date Folders" chạy qua.

[Device Connection and Disconnection Process](/wiki/spaces/DVID/pages/26443811/Device+Connection+and+Disconnection+Process)  — ADB connection layer dùng cho remote cleanup.

[https://ducviet.atlassian.net/wiki/spaces/DVID/pages/27132122/UI+Design?xpis=eyJicmlkZ2UiOiJyb3ZvLWNoYXQubWluaS1tb2RhbCIsImlkIjoiMTc4MzczMTg3NDgxMyIsInNvdXJjZSI6ImNvbmZsdWVuY2UifQ%3D%3D](https://ducviet.atlassian.net/wiki/spaces/DVID/pages/27132122/UI+Design?xpis=eyJicmlkZ2UiOiJyb3ZvLWNoYXQubWluaS1tb2RhbCIsImlkIjoiMTc4MzczMTg3NDgxMyIsInNvdXJjZSI6ImNvbmZsdWVuY2UifQ%3D%3D)  Kiến trúc CSS phân lớp — liên quan đến `.settings-child-row` và các FXML dialog.