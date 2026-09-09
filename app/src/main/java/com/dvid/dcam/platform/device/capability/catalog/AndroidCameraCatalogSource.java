package com.dvid.dcam.platform.device.capability.catalog;

import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.os.Build;
import android.util.Range;
import android.util.Size;
import com.dvid.dcam.core.logging.domain.LogCategory;
import com.dvid.dcam.core.logging.application.port.Logger;
import com.dvid.dcam.feature.device.application.port.CameraCatalogSource;
import com.dvid.dcam.feature.device.application.port.CameraCatalogSource.CameraFacts;
import com.dvid.dcam.feature.device.application.port.CameraCatalogSource.CatalogException;
import com.dvid.dcam.feature.device.application.port.CameraCatalogSource.CodecProfileLevel;
import com.dvid.dcam.feature.device.application.port.CameraCatalogSource.ConcurrentCameraCombination;
import com.dvid.dcam.feature.device.application.port.CameraCatalogSource.ConcurrentCameraFacts;
import com.dvid.dcam.feature.device.application.port.CameraCatalogSource.DeviceIdentity;
import com.dvid.dcam.feature.device.application.port.CameraCatalogSource.EncoderFacts;
import com.dvid.dcam.feature.device.application.port.CameraCatalogSource.EncoderSizeCapabilities;
import com.dvid.dcam.feature.device.application.port.CameraCatalogSource.EncoderVideoCapabilities;
import com.dvid.dcam.feature.device.application.port.CameraCatalogSource.FrameRateRange;
import com.dvid.dcam.feature.device.application.port.CameraCatalogSource.IntegerRange;
import com.dvid.dcam.feature.device.application.port.CameraCatalogSource.RawCatalog;
import com.dvid.dcam.feature.device.application.port.CameraCatalogSource.StreamSize;
import com.dvid.dcam.feature.device.domain.camera.CameraId;
import com.dvid.dcam.feature.device.domain.camera.CameraResolution;
import com.dvid.dcam.feature.device.domain.camera.FrameRatePolicy;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public final class AndroidCameraCatalogSource implements CameraCatalogSource {
    private static final Comparator<CameraResolution> RESOLUTION_ORDER = Comparator
            .comparingInt(CameraResolution::width)
            .thenComparingInt(CameraResolution::height);

    private final Context context;
    private final Logger logger;

    public AndroidCameraCatalogSource(Context context, Logger logger) {
        this.context = Objects.requireNonNull(context, "context").getApplicationContext();
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override public RawCatalog load() throws CatalogException {
        long totalStartNanos = System.nanoTime();
        String stage = "cameras";
        try {
            CameraManager cameraManager = context.getSystemService(CameraManager.class);
            if (cameraManager == null) {
                throw new IllegalStateException("CameraManager unavailable");
            }

            long stageStartNanos = System.nanoTime();
            List<CameraFacts> cameras = readCameras(cameraManager);
            logStage(stage, stageStartNanos,
                    "cameraCount=" + cameras.size());

            stage = "concurrency";
            stageStartNanos = System.nanoTime();
            ConcurrentCameraFacts concurrency = readConcurrency(cameraManager);
            logStage(stage, stageStartNanos,
                    "queryAvailable=" + concurrency.queryAvailable()
                            + " combinationCount=" + concurrency.combinations().size());

            stage = "encoders";
            stageStartNanos = System.nanoTime();
            Map<CameraResolution, List<Integer>> videoFrameRates =
                    videoFrameRateCandidates(cameras);
            List<EncoderFacts> encoders = readH264Encoders(videoFrameRates);
            logStage(stage, stageStartNanos,
                    "h264EncoderCount=" + encoders.size()
                            + " sampledVideoSizeCount=" + videoFrameRates.size());

            stage = "signature";
            stageStartNanos = System.nanoTime();
            RawCatalog catalog = RawCameraCatalogFactory.create(
                    readDeviceIdentity(), cameras, concurrency, encoders);
            logStage(stage, stageStartNanos,
                    "inputLength=" + catalog.hardwareSignatureInput().length());
            logger.info(LogCategory.CAPABILITY, "unspecified", "camera_catalog stage=complete outcome=success"
                    + " cameraCount=" + catalog.cameras().size()
                    + " h264EncoderCount=" + catalog.h264Encoders().size()
                    + " elapsedMs=" + elapsedMillis(totalStartNanos));
            return catalog;
        } catch (CameraAccessException | RuntimeException error) {
            logger.warn(LogCategory.CAPABILITY, "unspecified", null, "camera_catalog stage=" + stage + " outcome=failure"
                    + " elapsedMs=" + elapsedMillis(totalStartNanos), error);
            throw new CatalogException("Camera catalog failed at stage " + stage, error);
        }
    }

    private List<CameraFacts> readCameras(CameraManager cameraManager)
            throws CameraAccessException {
        String[] cameraIds = cameraManager.getCameraIdList();
        Arrays.sort(cameraIds);
        List<CameraFacts> result = new ArrayList<>(cameraIds.length);
        for (String rawCameraId : cameraIds) {
            long startNanos = System.nanoTime();
            CameraCharacteristics characteristics = cameraManager
                    .getCameraCharacteristics(rawCameraId);
            StreamConfigurationMap map = characteristics.get(
                    CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            result.add(new CameraFacts(
                    new CameraId(rawCameraId),
                    characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL),
                    characteristics.get(CameraCharacteristics.LENS_FACING),
                    characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION),
                    integerList(characteristics.get(
                            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)),
                    physicalCameraIds(characteristics),
                    map != null,
                    map == null ? List.of() : formatOutputs(map, ImageFormat.PRIVATE),
                    map == null ? List.of() : classOutputs(map, MediaCodec.class),
                    map == null ? List.of() : classOutputs(map, MediaRecorder.class),
                    map == null ? List.of() : jpegOutputs(map),
                    frameRateRanges(characteristics.get(
                            CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES))));
            logger.info(LogCategory.CAPABILITY, "unspecified", "camera_catalog stage=camera_characteristics outcome=success"
                    + " cameraId=" + rawCameraId
                    + " streamMapAvailable=" + (map != null)
                    + " elapsedMs=" + elapsedMillis(startNanos));
        }
        return List.copyOf(result);
    }

    private static ConcurrentCameraFacts readConcurrency(CameraManager cameraManager)
            throws CameraAccessException {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return new ConcurrentCameraFacts(false, List.of());
        }
        List<ConcurrentCameraCombination> result = new ArrayList<>();
        for (Set<String> combination : cameraManager.getConcurrentCameraIds()) {
            if (combination.isEmpty()) continue;
            List<CameraId> cameraIds = new ArrayList<>(combination.size());
            for (String cameraId : combination) cameraIds.add(new CameraId(cameraId));
            cameraIds.sort(CameraId::compareTo);
            result.add(new ConcurrentCameraCombination(cameraIds));
        }
        return new ConcurrentCameraFacts(true, result);
    }

    private static List<EncoderFacts> readH264Encoders(
            Map<CameraResolution, List<Integer>> cameraFrameRates) {
        List<EncoderFacts> result = new ArrayList<>();
        for (MediaCodecInfo codec : new MediaCodecList(
                MediaCodecList.ALL_CODECS).getCodecInfos()) {
            if (!codec.isEncoder()) continue;
            String avcType = avcType(codec.getSupportedTypes());
            if (avcType == null) continue;
            MediaCodecInfo.CodecCapabilities capabilities = codec
                    .getCapabilitiesForType(avcType);
            result.add(new EncoderFacts(
                    codec.getName(),
                    canonicalName(codec),
                    codecFlag(codec, CodecFlag.ALIAS),
                    codecFlag(codec, CodecFlag.HARDWARE_ACCELERATED),
                    codecFlag(codec, CodecFlag.SOFTWARE_ONLY),
                    codecFlag(codec, CodecFlag.VENDOR),
                    capabilities.getMaxSupportedInstances(),
                    profileLevels(capabilities.profileLevels),
                    integerList(capabilities.colorFormats),
                    videoCapabilities(capabilities.getVideoCapabilities(), cameraFrameRates)));
        }
        result.sort(Comparator.comparing(EncoderFacts::name)
                .thenComparing(EncoderFacts::canonicalName));
        return List.copyOf(result);
    }

    private static EncoderVideoCapabilities videoCapabilities(
            MediaCodecInfo.VideoCapabilities capabilities,
            Map<CameraResolution, List<Integer>> cameraFrameRates) {
        if (capabilities == null) return null;
        List<EncoderSizeCapabilities> sizeCapabilities = new ArrayList<>();
        for (Map.Entry<CameraResolution, List<Integer>> entry : cameraFrameRates.entrySet()) {
            sizeCapabilities.add(readEncoderSizeCapabilities(
                    entry.getKey(), entry.getValue(), capabilities::areSizeAndRateSupported));
        }
        return new EncoderVideoCapabilities(
                capabilities.getWidthAlignment(),
                capabilities.getHeightAlignment(),
                integerRange(capabilities.getSupportedWidths()),
                integerRange(capabilities.getSupportedHeights()),
                integerRange(capabilities.getBitrateRange()),
                integerFrameRateRange(capabilities.getSupportedFrameRates()),
                sizeCapabilities);
    }

    static EncoderSizeCapabilities readEncoderSizeCapabilities(
            CameraResolution resolution, List<Integer> candidateFrameRates,
            SizeRateSupport support) {
        TreeSet<Integer> supported = new TreeSet<>();
        for (Integer frameRate : candidateFrameRates) {
            try {
                if (support.test(resolution.width(), resolution.height(), frameRate)) {
                    supported.add(frameRate);
                }
            } catch (IllegalArgumentException ignored) { }
        }
        return new EncoderSizeCapabilities(resolution, List.copyOf(supported));
    }

    private static Map<CameraResolution, List<Integer>> videoFrameRateCandidates(
            List<CameraFacts> cameras) {
        TreeMap<CameraResolution, TreeSet<Integer>> candidates =
                new TreeMap<>(RESOLUTION_ORDER);
        for (CameraFacts camera : cameras) {
            TreeSet<Integer> cameraRates = new TreeSet<>();
            for (FrameRateRange range : camera.aeTargetFpsRanges()) {
                cameraRates.add(FrameRatePolicy.normalize(range.lower().doubleValue()));
                cameraRates.add(FrameRatePolicy.normalize(range.upper().doubleValue()));
            }
            for (StreamSize output : camera.privateOutputs()) {
                TreeSet<Integer> resolutionRates = candidates.computeIfAbsent(
                        output.resolution(), ignored -> new TreeSet<>());
                resolutionRates.addAll(cameraRates);
                if (output.minimumFrameDurationNanos() != null
                        && output.minimumFrameDurationNanos() > 0) {
                    resolutionRates.add(FrameRatePolicy.normalize(
                            1_000_000_000d / output.minimumFrameDurationNanos()));
                }
            }
        }
        TreeMap<CameraResolution, List<Integer>> result =
                new TreeMap<>(RESOLUTION_ORDER);
        candidates.forEach((resolution, frameRates) ->
                result.put(resolution, List.copyOf(frameRates)));
        return Map.copyOf(result);
    }

    interface SizeRateSupport {
        boolean test(int width, int height, double framesPerSecond);
    }

    private static List<CameraResolution> videoSizes(List<CameraFacts> cameras) {
        TreeSet<CameraResolution> result = new TreeSet<>(RESOLUTION_ORDER);
        for (CameraFacts camera : cameras) {
            addResolutions(result, camera.privateOutputs());
            addResolutions(result, camera.mediaCodecOutputs());
            addResolutions(result, camera.mediaRecorderOutputs());
        }
        return List.copyOf(result);
    }
    private static void addResolutions(
            Set<CameraResolution> result, List<StreamSize> outputs) {
        for (StreamSize output : outputs) result.add(output.resolution());
    }

    private static List<StreamSize> jpegOutputs(StreamConfigurationMap map) {
        return mergeJpegOutputs(formatOutputs(map, ImageFormat.JPEG),
                highResolutionJpegOutputs(map));
    }

    private static List<StreamSize> highResolutionJpegOutputs(
            StreamConfigurationMap map) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return List.of();
        Size[] sizes;
        try {
            sizes = map.getHighResolutionOutputSizes(ImageFormat.JPEG);
        } catch (IllegalArgumentException ignored) {
            return List.of();
        }
        return formatOutputs(map, ImageFormat.JPEG, sizes);
    }

    static List<StreamSize> mergeJpegOutputs(
            List<StreamSize> regular, List<StreamSize> highResolution) {
        TreeMap<CameraResolution, StreamSize> merged =
                new TreeMap<>(RESOLUTION_ORDER);
        for (StreamSize output : regular) mergeOutput(merged, output);
        for (StreamSize output : highResolution) mergeOutput(merged, output);
        return List.copyOf(merged.values());
    }

    private static void mergeOutput(
            Map<CameraResolution, StreamSize> merged, StreamSize output) {
        merged.merge(output.resolution(), output, (left, right) -> new StreamSize(
                left.resolution(), minimumDuration(
                        left.minimumFrameDurationNanos(),
                        right.minimumFrameDurationNanos())));
    }

    private static Long minimumDuration(Long left, Long right) {
        if (left == null) return right;
        if (right == null) return left;
        return Math.min(left, right);
    }
    private static List<StreamSize> classOutputs(
            StreamConfigurationMap map, Class<?> outputClass) {
        Size[] sizes;
        try {
            sizes = map.getOutputSizes(outputClass);
        } catch (IllegalArgumentException ignored) {
            return List.of();
        }
        if (sizes == null) return List.of();
        List<StreamSize> result = new ArrayList<>(sizes.length);
        for (Size size : sizes) {
            Long duration;
            try {
                duration = map.getOutputMinFrameDuration(outputClass, size);
            } catch (IllegalArgumentException ignored) {
                duration = null;
            }
            result.add(new StreamSize(
                    new CameraResolution(size.getWidth(), size.getHeight()), duration));
        }
        return List.copyOf(result);
    }

    private static List<StreamSize> formatOutputs(
            StreamConfigurationMap map, int format) {
        Size[] sizes;
        try {
            sizes = map.getOutputSizes(format);
        } catch (IllegalArgumentException ignored) {
            return List.of();
        }
        return formatOutputs(map, format, sizes);
    }

    private static List<StreamSize> formatOutputs(
            StreamConfigurationMap map, int format, Size[] sizes) {
        if (sizes == null) return List.of();
        List<StreamSize> result = new ArrayList<>(sizes.length);
        for (Size size : sizes) {
            Long duration;
            try {
                duration = map.getOutputMinFrameDuration(format, size);
            } catch (IllegalArgumentException ignored) {
                duration = null;
            }
            result.add(new StreamSize(
                    new CameraResolution(size.getWidth(), size.getHeight()), duration));
        }
        return List.copyOf(result);
    }
    private static List<FrameRateRange> frameRateRanges(Range<Integer>[] ranges) {
        if (ranges == null) return List.of();
        List<FrameRateRange> result = new ArrayList<>(ranges.length);
        for (Range<Integer> range : ranges) {
            result.add(new FrameRateRange(
                    BigDecimal.valueOf(range.getLower()),
                    BigDecimal.valueOf(range.getUpper())));
        }
        return List.copyOf(result);
    }

    private static FrameRateRange frameRateRange(Range<Double> range) {
        return new FrameRateRange(
                BigDecimal.valueOf(range.getLower()),
                BigDecimal.valueOf(range.getUpper()));
    }

    private static FrameRateRange integerFrameRateRange(Range<Integer> range) {
        return new FrameRateRange(
                BigDecimal.valueOf(range.getLower()),
                BigDecimal.valueOf(range.getUpper()));
    }

    private static IntegerRange integerRange(Range<Integer> range) {
        return new IntegerRange(range.getLower(), range.getUpper());
    }

    private static List<CodecProfileLevel> profileLevels(
            MediaCodecInfo.CodecProfileLevel[] values) {
        if (values == null) return List.of();
        List<CodecProfileLevel> result = new ArrayList<>(values.length);
        for (MediaCodecInfo.CodecProfileLevel value : values) {
            result.add(new CodecProfileLevel(value.profile, value.level));
        }
        return List.copyOf(result);
    }

    private static List<Integer> integerList(int[] values) {
        if (values == null) return List.of();
        List<Integer> result = new ArrayList<>(values.length);
        for (int value : values) result.add(value);
        return List.copyOf(result);
    }

    private static List<CameraId> physicalCameraIds(
            CameraCharacteristics characteristics) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return List.of();
        List<CameraId> result = new ArrayList<>();
        for (String cameraId : characteristics.getPhysicalCameraIds()) {
            result.add(new CameraId(cameraId));
        }
        result.sort(CameraId::compareTo);
        return List.copyOf(result);
    }

    private static String avcType(String[] supportedTypes) {
        for (String supportedType : supportedTypes) {
            if (MediaFormat.MIMETYPE_VIDEO_AVC.equalsIgnoreCase(supportedType)) {
                return supportedType;
            }
        }
        return null;
    }

    private static String canonicalName(MediaCodecInfo codec) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                ? codec.getCanonicalName() : codec.getName();
    }

    private static Boolean codecFlag(MediaCodecInfo codec, CodecFlag flag) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null;
        return switch (flag) {
            case ALIAS -> codec.isAlias();
            case HARDWARE_ACCELERATED -> codec.isHardwareAccelerated();
            case SOFTWARE_ONLY -> codec.isSoftwareOnly();
            case VENDOR -> codec.isVendor();
        };
    }

    private static DeviceIdentity readDeviceIdentity() {
        return new DeviceIdentity(
                Build.VERSION.SDK_INT,
                safe(Build.MANUFACTURER),
                safe(Build.BRAND),
                safe(Build.DEVICE),
                safe(Build.PRODUCT),
                safe(Build.MODEL),
                safe(Build.HARDWARE),
                safe(Build.BOARD),
                safe(Build.BOOTLOADER),
                safe(Build.ID),
                safe(Build.VERSION.INCREMENTAL),
                safe(Build.VERSION.SECURITY_PATCH),
                safe(Build.FINGERPRINT));
    }

    private static String safe(String value) { return value == null ? "" : value; }

    private void logStage(String stage, long startNanos, String fields) {
        logger.info(LogCategory.CAPABILITY, "unspecified", "camera_catalog stage=" + stage + " outcome=success " + fields
                + " elapsedMs=" + elapsedMillis(startNanos));
    }

    private static long elapsedMillis(long startNanos) {
        return Math.max(0L, (System.nanoTime() - startNanos) / 1_000_000L);
    }

    private enum CodecFlag {
        ALIAS,
        HARDWARE_ACCELERATED,
        SOFTWARE_ONLY,
        VENDOR
    }
}