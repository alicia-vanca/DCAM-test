package com.dvid.dcam.platform.device;

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.app.ActivityManager;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Build;
import android.os.StatFs;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.CameraInfo;
import androidx.camera.camera2.interop.Camera2CameraInfo;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.video.Quality;
import androidx.camera.video.QualitySelector;
import androidx.core.content.ContextCompat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Cached device capability snapshot. Camera qualities refresh asynchronously. */
public final class AndroidDeviceCapabilities {
    private static final String PREFS = "dcam_capabilities";
    private static final String QUALITY = "record_quality";
    private final Context context;
    private final SharedPreferences preferences;

    public AndroidDeviceCapabilities(Context context) {
        this.context = context.getApplicationContext();
        preferences = this.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public void evaluate() {
        ActivityManager.MemoryInfo memory = new ActivityManager.MemoryInfo();
        ActivityManager activityManager = context.getSystemService(ActivityManager.class);
        if (activityManager != null) activityManager.getMemoryInfo(memory);
        StatFs storage = new StatFs(context.getFilesDir().getAbsolutePath());
        preferences.edit()
                .putBoolean("camera", context.getPackageManager().hasSystemFeature("android.hardware.camera"))
                .putBoolean("microphone", context.getPackageManager().hasSystemFeature("android.hardware.microphone"))
                .putBoolean("gps", context.getPackageManager().hasSystemFeature("android.hardware.location.gps"))
                .putBoolean("internet", detectInternet())
                .putInt("sensor_count", sensorCount())
                .putInt("cpu_count", Runtime.getRuntime().availableProcessors())
                .putLong("memory_bytes", memory.totalMem)
                .putLong("storage_bytes", storage.getTotalBytes())
                .putLong("storage_available_bytes", storage.getAvailableBytes())
                .putLong("evaluated_at", System.currentTimeMillis())
                .apply();
        ProcessCameraProvider.getInstance(context).addListener(this::evaluateCamera,
                ContextCompat.getMainExecutor(context));
    }

    public List<String> supportedRecordQualities() {
        String value = preferences.getString("qualities", "SD,HD,FHD");
        List<String> result = new ArrayList<>();
        for (String item : value.split(",")) if (!item.isBlank()) result.add(item);
        return result.isEmpty() ? List.of("SD") : result;
    }

    public String selectedRecordQuality() {
        String selected = preferences.getString(QUALITY, "FHD");
        return supportedRecordQualities().contains(selected) ? selected : highestQuality();
    }

    public void selectRecordQuality(String quality) {
        if (quality != null && supportedRecordQualities().contains(quality))
            preferences.edit().putString(QUALITY, quality).apply();
    }

    public boolean hasCamera() { return preferences.getBoolean("camera", false); }
    public boolean hasRearCamera() { return preferences.getBoolean("rear_camera", false); }
    public boolean hasFrontCamera() { return preferences.getBoolean("front_camera", false); }
    public boolean hasFlash() { return preferences.getBoolean("flash", false); }
    public boolean hasMonochromeCamera() { return preferences.getBoolean("monochrome_camera", false); }
    public boolean hasLowLightBoost() { return preferences.getBoolean("low_light_boost", false); }
    public boolean hasGps() { return preferences.getBoolean("gps", false); }
    public boolean hasInternet() { return preferences.getBoolean("internet", false); }
    public int sensorCount() {
        SensorManager manager = context.getSystemService(SensorManager.class);
        return manager == null ? 0 : manager.getSensorList(Sensor.TYPE_ALL).size();
    }

    private void evaluateCamera() {
        try {
            ProcessCameraProvider provider = ProcessCameraProvider.getInstance(context).get();
            boolean rearCamera = provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA);
            boolean frontCamera = provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA);
            CameraSelector selector = rearCamera
                    ? CameraSelector.DEFAULT_BACK_CAMERA : CameraSelector.DEFAULT_FRONT_CAMERA;
            List<CameraInfo> selectedCameras = selector.filter(provider.getAvailableCameraInfos());
            CameraInfo cameraInfo = selectedCameras.get(0);
            List<Quality> qualities = QualitySelector.getSupportedQualities(
                    cameraInfo);
            List<String> names = new ArrayList<>();
            for (Quality quality : Arrays.asList(Quality.SD, Quality.HD, Quality.FHD, Quality.UHD))
                if (qualities.contains(quality)) names.add(label(quality));
            Camera2CameraInfo camera2 = Camera2CameraInfo.from(cameraInfo);
            int[] capabilities = camera2.getCameraCharacteristic(
                    CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
            int[] aeModes = camera2.getCameraCharacteristic(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES);
            boolean infrared = detectInfraredCamera();
            preferences.edit().putBoolean("camera", rearCamera || frontCamera)
                    .putBoolean("rear_camera", rearCamera)
                    .putBoolean("front_camera", frontCamera)
                    .putBoolean("flash", cameraInfo.hasFlashUnit())
                    .putBoolean("monochrome_camera", contains(capabilities,
                            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MONOCHROME))
                    .putBoolean("low_light_boost", Build.VERSION.SDK_INT >= 35 && contains(aeModes, 6))
                    .putBoolean("ir_night_vision", infrared)
                    .putString("qualities", String.join(",", names)).apply();
        } catch (Exception ignored) {
            preferences.edit().putBoolean("rear_camera", false)
                    .putBoolean("front_camera", false)
                    .putBoolean("flash", false)
                    .putBoolean("monochrome_camera", false)
                    .putBoolean("low_light_boost", false)
                    .putBoolean("ir_night_vision", false)
                    .putString("qualities", "")
                    .remove(QUALITY).apply();
        }
    }

    public boolean hasIrNightVision() { return preferences.getBoolean("ir_night_vision", false); }

    private boolean detectInfraredCamera() {
        CameraManager manager = context.getSystemService(CameraManager.class);
        if (manager == null) return false;
        try {
            for (String cameraId : manager.getCameraIdList()) {
                CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing == null || facing != CameraCharacteristics.LENS_FACING_BACK) continue;
                for (CameraCharacteristics.Key<?> key : characteristics.getKeys()) {
                    String name = key.getName().toLowerCase(java.util.Locale.US);
                    if (!name.contains("ir") && !name.contains("infrared") && !name.contains("night")) continue;
                    Object value = characteristics.get(key);
                    if (value instanceof Boolean && (Boolean) value) return true;
                    if (value instanceof Number && ((Number) value).intValue() != 0) return true;
                }
            }
        } catch (Exception ignored) {
            // Vendor camera metadata is optional.
        }
        return false;
    }

    private String highestQuality() {
        List<String> qualities = supportedRecordQualities();
        return qualities.get(qualities.size() - 1);
    }

    private boolean detectInternet() {
        ConnectivityManager manager = context.getSystemService(ConnectivityManager.class);
        NetworkCapabilities capabilities = manager == null ? null : manager.getNetworkCapabilities(manager.getActiveNetwork());
        return capabilities != null && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    private static String label(Quality quality) {
        if (quality == Quality.UHD) return "4K";
        if (quality == Quality.FHD) return "FHD";
        return quality == Quality.HD ? "HD" : "SD";
    }

    private static boolean contains(int[] values, int target) {
        if (values == null) return false;
        for (int value : values) if (value == target) return true;
        return false;
    }
}
