package com.dvid.dcam.app.ui.settings.camera;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Derived presentation row for one stable camera ID. */
public record CameraSettingsRow(
        String cameraId,
        String label,
        List<CameraVideoOption> videoOptions,
        List<CameraResolutionOption> imageOptions,
        Optional<CameraSelection> committedSelection,
        Optional<CameraSelection> targetSelection,
        CameraSettingsStatus status,
        boolean controlsEnabled) {
    public CameraSettingsRow {
        cameraId = Objects.requireNonNull(cameraId, "cameraId");
        label = Objects.requireNonNull(label, "label");
        videoOptions = List.copyOf(Objects.requireNonNull(videoOptions, "videoOptions"));
        imageOptions = List.copyOf(Objects.requireNonNull(imageOptions, "imageOptions"));
        committedSelection = Objects.requireNonNull(committedSelection, "committedSelection");
        targetSelection = Objects.requireNonNull(targetSelection, "targetSelection");
        status = Objects.requireNonNull(status, "status");
    }

    public Optional<CameraSelection> displayedSelection() {
        return targetSelection.isPresent() ? targetSelection : committedSelection;
    }

    public List<Integer> frameRateOptions() {
        Optional<CameraSelection> selection = displayedSelection();
        if (selection.isEmpty()) return List.of();
        String selectedVideoId = selection.orElseThrow().videoResolutionId();
        for (CameraVideoOption option : videoOptions) {
            if (option.id().equals(selectedVideoId)) return option.frameRates();
        }
        return List.of();
    }
}