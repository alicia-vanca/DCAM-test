package com.dvid.dcam.feature.device.application.port;

import com.dvid.dcam.feature.device.domain.camera.CameraId;
import com.dvid.dcam.feature.device.domain.camera.CameraResolution;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

public interface CameraCatalogSource {
    RawCatalog load() throws CatalogException;

    final class CatalogException extends Exception {
        public CatalogException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    record RawCatalog(
            DeviceIdentity deviceIdentity,
            List<CameraFacts> cameras,
            ConcurrentCameraFacts concurrency,
            List<EncoderFacts> h264Encoders,
            String hardwareSignatureInput) {
        public RawCatalog {
            deviceIdentity = Objects.requireNonNull(deviceIdentity, "deviceIdentity");
            cameras = immutable(cameras, "cameras");
            concurrency = Objects.requireNonNull(concurrency, "concurrency");
            h264Encoders = immutable(h264Encoders, "h264Encoders");
            hardwareSignatureInput = required(hardwareSignatureInput, "hardwareSignatureInput");
        }
    }

    record DeviceIdentity(
            int sdkInt,
            String manufacturer,
            String brand,
            String device,
            String product,
            String model,
            String hardware,
            String board,
            String bootloader,
            String buildId,
            String incremental,
            String securityPatch,
            String fingerprint) {
        public DeviceIdentity {
            if (sdkInt <= 0) throw new IllegalArgumentException("sdkInt must be positive");
            manufacturer = nonNull(manufacturer, "manufacturer");
            brand = nonNull(brand, "brand");
            device = nonNull(device, "device");
            product = nonNull(product, "product");
            model = nonNull(model, "model");
            hardware = nonNull(hardware, "hardware");
            board = nonNull(board, "board");
            bootloader = nonNull(bootloader, "bootloader");
            buildId = nonNull(buildId, "buildId");
            incremental = nonNull(incremental, "incremental");
            securityPatch = nonNull(securityPatch, "securityPatch");
            fingerprint = nonNull(fingerprint, "fingerprint");
        }
    }

    record CameraFacts(
            CameraId cameraId,
            Integer hardwareLevel,
            Integer lensFacing,
            Integer sensorOrientationDegrees,
            List<Integer> availableCapabilities,
            List<CameraId> physicalCameraIds,
            boolean streamConfigurationMapAvailable,
            List<StreamSize> privateOutputs,
            List<StreamSize> mediaCodecOutputs,
            List<StreamSize> mediaRecorderOutputs,
            List<StreamSize> jpegOutputs,
            List<FrameRateRange> aeTargetFpsRanges) {
        public CameraFacts {
            cameraId = Objects.requireNonNull(cameraId, "cameraId");
            availableCapabilities = immutable(availableCapabilities, "availableCapabilities");
            physicalCameraIds = immutable(physicalCameraIds, "physicalCameraIds");
            privateOutputs = immutable(privateOutputs, "privateOutputs");
            mediaCodecOutputs = immutable(mediaCodecOutputs, "mediaCodecOutputs");
            mediaRecorderOutputs = immutable(mediaRecorderOutputs, "mediaRecorderOutputs");
            jpegOutputs = immutable(jpegOutputs, "jpegOutputs");
            aeTargetFpsRanges = immutable(aeTargetFpsRanges, "aeTargetFpsRanges");
        }
    }

    record StreamSize(CameraResolution resolution, Long minimumFrameDurationNanos) {
        public StreamSize {
            resolution = Objects.requireNonNull(resolution, "resolution");
            if (minimumFrameDurationNanos != null && minimumFrameDurationNanos < 0) {
                throw new IllegalArgumentException("minimumFrameDurationNanos must not be negative");
            }
        }
    }

    record FrameRateRange(BigDecimal lower, BigDecimal upper) {
        public FrameRateRange {
            lower = Objects.requireNonNull(lower, "lower");
            upper = Objects.requireNonNull(upper, "upper");
            if (lower.signum() < 0 || lower.compareTo(upper) > 0) {
                throw new IllegalArgumentException("invalid frame-rate range");
            }
        }
    }

    record ConcurrentCameraFacts(
            boolean queryAvailable, List<ConcurrentCameraCombination> combinations) {
        public ConcurrentCameraFacts {
            combinations = immutable(combinations, "combinations");
        }
    }

    record ConcurrentCameraCombination(List<CameraId> cameraIds) {
        public ConcurrentCameraCombination {
            cameraIds = immutable(cameraIds, "cameraIds");
            if (cameraIds.isEmpty()) {
                throw new IllegalArgumentException("concurrent camera combination is empty");
            }
        }
    }

    record EncoderFacts(
            String name,
            String canonicalName,
            Boolean alias,
            Boolean hardwareAccelerated,
            Boolean softwareOnly,
            Boolean vendor,
            int maxSupportedInstances,
            List<CodecProfileLevel> profileLevels,
            List<Integer> colorFormats,
            EncoderVideoCapabilities videoCapabilities) {
        public EncoderFacts {
            name = required(name, "name");
            canonicalName = required(canonicalName, "canonicalName");
            if (maxSupportedInstances <= 0) {
                throw new IllegalArgumentException("maxSupportedInstances must be positive");
            }
            profileLevels = immutable(profileLevels, "profileLevels");
            colorFormats = immutable(colorFormats, "colorFormats");
        }
    }

    record CodecProfileLevel(int profile, int level) {
        public CodecProfileLevel {
            if (profile < 0 || level < 0) {
                throw new IllegalArgumentException("profile and level must not be negative");
            }
        }
    }

    record EncoderVideoCapabilities(
            int widthAlignment,
            int heightAlignment,
            IntegerRange supportedWidths,
            IntegerRange supportedHeights,
            IntegerRange bitrateRange,
            FrameRateRange supportedFrameRates,
            List<EncoderSizeCapabilities> cameraSizeCapabilities) {
        public EncoderVideoCapabilities {
            if (widthAlignment <= 0 || heightAlignment <= 0) {
                throw new IllegalArgumentException("encoder alignment must be positive");
            }
            supportedWidths = Objects.requireNonNull(supportedWidths, "supportedWidths");
            supportedHeights = Objects.requireNonNull(supportedHeights, "supportedHeights");
            bitrateRange = Objects.requireNonNull(bitrateRange, "bitrateRange");
            supportedFrameRates = Objects.requireNonNull(
                    supportedFrameRates, "supportedFrameRates");

            cameraSizeCapabilities = immutable(
                    cameraSizeCapabilities, "cameraSizeCapabilities");
        }
    }

    record EncoderSizeCapabilities(
            CameraResolution resolution,
            List<Integer> supportedFrameRates) {
        public EncoderSizeCapabilities {
            resolution = Objects.requireNonNull(resolution, "resolution");
            supportedFrameRates = immutable(supportedFrameRates, "supportedFrameRates");
            for (Integer frameRate : supportedFrameRates) {
                if (frameRate == null || frameRate <= 0) {
                    throw new IllegalArgumentException("frame rate must be positive");
                }
            }
        }
    }

    record IntegerRange(int lower, int upper) {
        public IntegerRange {
            if (lower < 0 || lower > upper) {
                throw new IllegalArgumentException("invalid integer range");
            }
        }
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }

    private static String nonNull(String value, String name) {
        return Objects.requireNonNull(value, name);
    }

    private static <T> List<T> immutable(List<T> values, String name) {
        return List.copyOf(Objects.requireNonNull(values, name));
    }
}