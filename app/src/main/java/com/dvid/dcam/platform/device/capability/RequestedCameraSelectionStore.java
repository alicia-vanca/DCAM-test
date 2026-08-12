package com.dvid.dcam.platform.device.capability;

import android.content.Context;
import android.content.SharedPreferences;
import com.dvid.dcam.feature.device.domain.camera.CameraId;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public final class RequestedCameraSelectionStore {
    private static final String PREFERENCES = "dcam_camera_selection";
    private static final String RECORD_QUALITY = "record_quality";
    private static final String RECORD_FRAME_RATE = "record_frame_rate";
    private static final String IMAGE_QUALITY = "image_quality";
    private static final String CAPTURE_ATTEMPT = "capture_attempt";

    private final SharedPreferences preferences;
    private final LinkedHashSet<String> runtimeRejections = new LinkedHashSet<>();

    public RequestedCameraSelectionStore(Context context) {
        Context applicationContext = Objects.requireNonNull(context, "context").getApplicationContext();
        Context checkedContext = applicationContext == null ? context : applicationContext;
        preferences = checkedContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }

    String recordQuality(CameraId cameraId) {
        return preferences.getString(key(cameraId, RECORD_QUALITY), "");
    }

    int recordFrameRate(CameraId cameraId) {
        return preferences.getInt(key(cameraId, RECORD_FRAME_RATE), 0);
    }

    String imageQuality(CameraId cameraId) {
        return preferences.getString(key(cameraId, IMAGE_QUALITY), "");
    }

    void saveRecordQuality(CameraId cameraId, String qualityId) {
        if (recordQuality(cameraId).equals(qualityId)) return;
        preferences.edit().putString(key(cameraId, RECORD_QUALITY), qualityId).apply();
    }

    void saveRecordFrameRate(CameraId cameraId, int frameRate) {
        if (recordFrameRate(cameraId) == frameRate) return;
        preferences.edit().putInt(key(cameraId, RECORD_FRAME_RATE), frameRate).apply();
    }

    void saveImageQuality(CameraId cameraId, String qualityId) {
        if (imageQuality(cameraId).equals(qualityId)) return;
        preferences.edit().putString(key(cameraId, IMAGE_QUALITY), qualityId).apply();
    }

    void markCaptureAttempt(String value) {
        if (!preferences.edit().putString(CAPTURE_ATTEMPT, value).commit()) {
            throw new IllegalStateException("capture attempt persistence failed");
        }
    }

    void clearCaptureAttempt() {
        if (!preferences.edit().remove(CAPTURE_ATTEMPT).commit()) {
            throw new IllegalStateException("capture attempt clear failed");
        }
    }

    synchronized Set<String> runtimeRejections() {
        return Set.copyOf(runtimeRejections);
    }

    synchronized void addRuntimeRejection(String rejection) {
        runtimeRejections.add(Objects.requireNonNull(rejection, "rejection"));
    }
    private static String key(CameraId cameraId, String suffix) {
        return "camera." + Objects.requireNonNull(cameraId, "cameraId").value() + "." + suffix;
    }


}
