package com.dvid.dcam.app.ui.settings.camera;

import com.dvid.dcam.app.ui.settings.SettingId;
import com.dvid.dcam.app.ui.settings.SettingItem;
import com.dvid.dcam.app.ui.settings.SettingsScreenModel;
import com.dvid.dcam.app.ui.settings.SettingsSection;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Generic N-camera settings and transition presentation. */
public final class CameraSettingsPresentationState {
    private final List<CameraSettingsRow> cameras;
    private final boolean recording;
    private final boolean lazyVerification;
    private final Optional<String> activeCameraId;

    private CameraSettingsPresentationState(List<CameraSettingsRow> cameras,
            boolean recording, boolean lazyVerification, Optional<String> activeCameraId) {
        this.cameras = List.copyOf(cameras);
        this.recording = recording;
        this.lazyVerification = lazyVerification;
        this.activeCameraId = Objects.requireNonNull(activeCameraId, "activeCameraId");
    }

    public static CameraSettingsPresentationState from(CameraSettingsSource source,
            boolean recording, boolean lazyVerification) {
        return from(source, recording, lazyVerification, true);
    }

    public static CameraSettingsPresentationState from(CameraSettingsSource source,
            boolean recording, boolean lazyVerification, boolean optionsReady) {
        return from(source, recording, lazyVerification, optionsReady, Optional.empty());
    }

    public static CameraSettingsPresentationState from(CameraSettingsSource source,
            boolean recording, boolean lazyVerification, boolean optionsReady,
            Optional<String> activeCameraId) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(activeCameraId, "activeCameraId");
        List<CameraSettingsCamera> supplied = List.copyOf(
                Objects.requireNonNull(source.cameras(), "source cameras"));
        List<CameraSettingsRow> rows = new ArrayList<>();
        Set<String> cameraIds = new HashSet<>();
        for (int index = 0; index < supplied.size(); index++) {
            CameraSettingsCamera camera = Objects.requireNonNull(supplied.get(index), "camera");
            if (!cameraIds.add(camera.cameraId())) {
                throw new IllegalArgumentException("duplicate cameraId " + camera.cameraId());
            }
            CameraSettingsStatus status = effectiveStatus(
                    camera.status(), lazyVerification);
            boolean controlsEnabled = !lazyVerification
                    && (status == CameraSettingsStatus.READY
                    || camera.targetSelection().isPresent());
            rows.add(new CameraSettingsRow(camera.cameraId(), roleLabel(index, supplied.size()),
                    optionsReady ? camera.videoOptions() : List.of(),
                    optionsReady ? camera.imageOptions() : List.of(),
                    camera.committedSelection(), camera.targetSelection(), status, controlsEnabled));
        }
        return new CameraSettingsPresentationState(
                rows, recording, lazyVerification, activeCameraId);
    }

    public List<CameraSettingsRow> cameras() { return cameras; }
    public boolean recording() { return recording; }
    public boolean lazyVerification() { return lazyVerification; }

    public SettingsScreenModel recordingScreen(String videoResolutionLabel,
            String frameRateLabel) {
        return screen(videoResolutionLabel, frameRateLabel, null,
                null, null, true, false, false);
    }

    public SettingsScreenModel cameraScreen(String imageResolutionLabel,
            String photoQualityNotice, String statusLabel) {
        return screen(null, null, imageResolutionLabel,
                photoQualityNotice, statusLabel, false, true, false);
    }

    SettingsScreenModel screen(String videoResolutionLabel, String frameRateLabel,
            String imageResolutionLabel, String photoQualityNotice, String statusLabel) {
        return screen(videoResolutionLabel, frameRateLabel, imageResolutionLabel,
                photoQualityNotice, statusLabel, true, true, true);
    }

    SettingsScreenModel screen() {
        return screen("Video resolution", "Frame rate", "Image resolution",
                "Photo quality may vary while recording.", "Status");
    }

    private SettingsScreenModel screen(String videoResolutionLabel, String frameRateLabel,
            String imageResolutionLabel, String photoQualityNotice, String statusLabel,
            boolean includeVideo, boolean includeImage, boolean includeStatus) {
        List<SettingsSection> sections = new ArrayList<>();
        for (int index = 0; index < cameras.size(); index++) {
            CameraSettingsRow camera = cameras.get(index);
            List<SettingItem> items = new ArrayList<>();
            if (includeStatus) {
                items.add(SettingItem.text(new CameraSettingControlId(camera.cameraId(),
                        CameraSettingControlId.Kind.STATUS).value(), statusLabel,
                        camera.status().label()));
            }
            if (includeVideo) {
                items.add(resolutionSetting(camera, SettingId.CAMERA_VIDEO_RESOLUTION,
                        CameraSettingControlId.Kind.VIDEO_RESOLUTION, videoResolutionLabel,
                        videoLabels(camera.videoOptions()), selectedVideoIndex(camera)));
                items.add(frameRateSetting(camera, frameRateLabel));
            }
            if (includeImage) {
                SettingItem image = resolutionSetting(camera, SettingId.CAMERA_IMAGE_RESOLUTION,
                        CameraSettingControlId.Kind.IMAGE_RESOLUTION, imageResolutionLabel,
                        imageLabels(camera.imageOptions()), selectedImageIndex(camera));
                if (photoQualityNotice != null && !photoQualityNotice.isBlank()) {
                    image = image.withDescription(photoQualityNotice);
                }
                items.add(image);
            }
            sections.add(new SettingsSection(camera.label(), items));
        }
        if (sections.isEmpty()) {
            sections.add(new SettingsSection("Camera", List.of()));
        }
        return new SettingsScreenModel(sections);
    }

    private static SettingItem resolutionSetting(CameraSettingsRow camera, SettingId id,
            CameraSettingControlId.Kind kind, String label, List<String> options,
            int selectedIndex) {
        String stableId = new CameraSettingControlId(camera.cameraId(), kind).value();
        if (options.isEmpty()) {
            return SettingItem.text(label, camera.status().label()).withEnabled(false);
        }
        return SettingItem.choice(id, stableId, label, options, selectedIndex)
                .withEnabled(camera.controlsEnabled());
    }

    private static SettingItem frameRateSetting(CameraSettingsRow camera, String label) {
        List<Integer> frameRates = camera.frameRateOptions();
        String stableId = new CameraSettingControlId(camera.cameraId(),
                CameraSettingControlId.Kind.VIDEO_FRAME_RATE).value();
        if (frameRates.isEmpty()) {
            return SettingItem.text(label, camera.status().label()).withEnabled(false);
        }
        List<String> options = new ArrayList<>();
        for (Integer frameRate : frameRates) options.add(frameRate + " FPS");
        int selected = camera.displayedSelection()
                .map(CameraSelection::frameRate)
                .map(frameRates::indexOf)
                .orElse(-1);
        return SettingItem.choice(SettingId.CAMERA_VIDEO_FRAME_RATE, stableId, label,
                options, selected).withEnabled(camera.controlsEnabled());
    }

    private static int selectedVideoIndex(CameraSettingsRow camera) {
        Optional<CameraSelection> selection = camera.displayedSelection();
        if (selection.isEmpty()) return -1;
        String selectedId = selection.orElseThrow().videoResolutionId();
        for (int index = 0; index < camera.videoOptions().size(); index++) {
            if (camera.videoOptions().get(index).id().equals(selectedId)) return index;
        }
        return -1;
    }

    private static int selectedImageIndex(CameraSettingsRow camera) {
        Optional<CameraSelection> selection = camera.displayedSelection();
        if (selection.isEmpty()) return -1;
        String selectedId = selection.orElseThrow().imageResolutionId();
        for (int index = 0; index < camera.imageOptions().size(); index++) {
            if (camera.imageOptions().get(index).id().equals(selectedId)) return index;
        }
        return -1;
    }

    private static List<String> videoLabels(List<CameraVideoOption> options) {
        List<String> labels = new ArrayList<>();
        for (CameraVideoOption option : options) labels.add(option.label());
        return List.copyOf(labels);
    }

    private static List<String> imageLabels(List<CameraResolutionOption> options) {
        List<String> labels = new ArrayList<>();
        for (CameraResolutionOption option : options) labels.add(option.label());
        return List.copyOf(labels);
    }

    private static CameraSettingsStatus effectiveStatus(CameraSettingsStatus status,
            boolean lazyVerification) {
        if (status != CameraSettingsStatus.READY) return status;
        if (lazyVerification) return CameraSettingsStatus.VERIFYING;
        return status;
    }

    private static String roleLabel(int index, int cameraCount) {
        if (index == 0) return "Camera chính";
        if (cameraCount == 2) return "Camera phụ";
        return "Camera phụ " + index;
    }
}