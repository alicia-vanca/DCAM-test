package com.dvid.dcam.app.ui.settings.camera;

import java.util.Objects;

/** Stable per-camera control identity used by generic settings callbacks. */
public record CameraSettingControlId(String cameraId, Kind kind) {
    public enum Kind {
        STATUS("status"),
        VIDEO_RESOLUTION("video-resolution"),
        VIDEO_FRAME_RATE("video-frame-rate"),
        IMAGE_RESOLUTION("image-resolution");

        private final String key;

        Kind(String key) { this.key = key; }
    }

    public CameraSettingControlId {
        Objects.requireNonNull(cameraId, "cameraId");
        if (cameraId.isBlank()) throw new IllegalArgumentException("cameraId is required");
        kind = Objects.requireNonNull(kind, "kind");
    }

    public String value() {
        return "camera:" + cameraId.length() + ":" + cameraId + ":" + kind.key;
    }

    public static CameraSettingControlId parse(String value) {
        Objects.requireNonNull(value, "value");
        if (!value.startsWith("camera:")) {
            throw new IllegalArgumentException("invalid camera control ID");
        }
        int lengthEnd = value.indexOf(':', 7);
        if (lengthEnd < 0) throw new IllegalArgumentException("invalid camera control ID");
        int cameraLength;
        try {
            cameraLength = Integer.parseInt(value.substring(7, lengthEnd));
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("invalid camera control ID", error);
        }
        int cameraStart = lengthEnd + 1;
        int kindStart = cameraStart + cameraLength + 1;
        if (cameraLength <= 0 || kindStart > value.length()
                || value.charAt(kindStart - 1) != ':') {
            throw new IllegalArgumentException("invalid camera control ID");
        }
        String cameraId = value.substring(cameraStart, kindStart - 1);
        String kindKey = value.substring(kindStart);
        for (Kind kind : Kind.values()) {
            if (kind.key.equals(kindKey)) return new CameraSettingControlId(cameraId, kind);
        }
        throw new IllegalArgumentException("invalid camera control kind");
    }
}