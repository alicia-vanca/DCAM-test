package com.dvid.dcam.app.ui.settings.camera;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Per-camera capability and selection projection from one production source. */
public record CameraSettingsCamera(
        String cameraId,
        List<CameraVideoOption> videoOptions,
        List<CameraResolutionOption> imageOptions,
        Optional<CameraSelection> committedSelection,
        Optional<CameraSelection> targetSelection,
        CameraSettingsStatus status) {
    public CameraSettingsCamera {
        cameraId = required(cameraId, "cameraId");
        videoOptions = List.copyOf(Objects.requireNonNull(videoOptions, "videoOptions"));
        imageOptions = List.copyOf(Objects.requireNonNull(imageOptions, "imageOptions"));
        committedSelection = Objects.requireNonNull(committedSelection, "committedSelection");
        targetSelection = Objects.requireNonNull(targetSelection, "targetSelection");
        status = Objects.requireNonNull(status, "status");
        requireUniqueVideoIds(videoOptions);
        requireUniqueImageIds(imageOptions);
    }

    public CameraSettingsCamera(String cameraId, List<CameraVideoOption> videoOptions,
            List<CameraResolutionOption> imageOptions, CameraSelection committedSelection,
            CameraSelection targetSelection, CameraSettingsStatus status) {
        this(cameraId, videoOptions, imageOptions, Optional.ofNullable(committedSelection),
                Optional.ofNullable(targetSelection), status);
    }

    private static void requireUniqueVideoIds(List<CameraVideoOption> options) {
        Set<String> ids = new HashSet<>();
        for (CameraVideoOption option : options) {
            Objects.requireNonNull(option, "video option");
            if (!ids.add(option.id())) {
                throw new IllegalArgumentException("duplicate video option " + option.id());
            }
        }
    }

    private static void requireUniqueImageIds(List<CameraResolutionOption> options) {
        Set<String> ids = new HashSet<>();
        for (CameraResolutionOption option : options) {
            Objects.requireNonNull(option, "image option");
            if (!ids.add(option.id())) {
                throw new IllegalArgumentException("duplicate image option " + option.id());
            }
        }
    }

    private static String required(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value;
    }
}