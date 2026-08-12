package com.dvid.dcam.platform.device.capability.catalog;

import com.dvid.dcam.feature.device.application.port.CameraCatalogSource.CameraFacts;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

final class RawCameraCatalogFactory {
    private static final Comparator<StreamSize> STREAM_SIZE_ORDER = Comparator
            .comparingInt((StreamSize value) -> value.resolution().width())
            .thenComparingInt(value -> value.resolution().height())
            .thenComparing(StreamSize::minimumFrameDurationNanos,
                    Comparator.nullsFirst(Long::compareTo));
    private static final Comparator<FrameRateRange> FRAME_RATE_ORDER = Comparator
            .comparing(FrameRateRange::lower)
            .thenComparing(FrameRateRange::upper);
    private static final Comparator<EncoderSizeCapabilities> ENCODER_SIZE_ORDER = Comparator
            .comparingInt((EncoderSizeCapabilities value) -> value.resolution().width())
            .thenComparingInt(value -> value.resolution().height());

    private RawCameraCatalogFactory() {}

    static RawCatalog create(
            DeviceIdentity deviceIdentity,
            List<CameraFacts> cameras,
            ConcurrentCameraFacts concurrency,
            List<EncoderFacts> h264Encoders) {
        Objects.requireNonNull(deviceIdentity, "deviceIdentity");
        ArrayList<CameraFacts> cameraValues = new ArrayList<>();
        for (CameraFacts camera : cameras) cameraValues.add(normalize(camera));
        cameraValues.sort(Comparator.comparing(CameraFacts::cameraId));
        List<CameraFacts> orderedCameras = List.copyOf(cameraValues);
        ConcurrentCameraFacts orderedConcurrency = normalize(concurrency);
        ArrayList<EncoderFacts> encoderValues = new ArrayList<>();
        for (EncoderFacts encoder : h264Encoders) encoderValues.add(normalize(encoder));
        encoderValues.sort(Comparator.comparing(EncoderFacts::name)
                .thenComparing(EncoderFacts::canonicalName));
        List<EncoderFacts> orderedEncoders = List.copyOf(encoderValues);
        String signatureInput = signatureInput(
                deviceIdentity, orderedCameras, orderedConcurrency, orderedEncoders);
        return new RawCatalog(deviceIdentity, orderedCameras, orderedConcurrency,
                orderedEncoders, signatureInput);
    }

    private static CameraFacts normalize(CameraFacts facts) {
        return new CameraFacts(
                facts.cameraId(),
                facts.hardwareLevel(),
                facts.lensFacing(),
                facts.sensorOrientationDegrees(),
                sorted(facts.availableCapabilities(), Integer::compareTo),
                sorted(facts.physicalCameraIds(), CameraId::compareTo),
                facts.streamConfigurationMapAvailable(),
                sorted(facts.privateOutputs(), STREAM_SIZE_ORDER),
                sorted(facts.mediaCodecOutputs(), STREAM_SIZE_ORDER),
                sorted(facts.mediaRecorderOutputs(), STREAM_SIZE_ORDER),
                sorted(facts.jpegOutputs(), STREAM_SIZE_ORDER),
                sorted(facts.aeTargetFpsRanges(), FRAME_RATE_ORDER));
    }

    private static ConcurrentCameraFacts normalize(ConcurrentCameraFacts facts) {
        Objects.requireNonNull(facts, "concurrency");
        ArrayList<ConcurrentCameraCombination> values = new ArrayList<>();
        for (ConcurrentCameraCombination combination : facts.combinations()) {
            values.add(new ConcurrentCameraCombination(
                    sorted(combination.cameraIds(), CameraId::compareTo)));
        }
        values.sort((left, right) -> compareCameraIds(
                left.cameraIds(), right.cameraIds()));
        List<ConcurrentCameraCombination> combinations = List.copyOf(values);
        return new ConcurrentCameraFacts(facts.queryAvailable(), combinations);
    }

    private static EncoderFacts normalize(EncoderFacts facts) {
        EncoderVideoCapabilities video = facts.videoCapabilities();
        if (video != null) {
            video = new EncoderVideoCapabilities(
                    video.widthAlignment(),
                    video.heightAlignment(),
                    video.supportedWidths(),
                    video.supportedHeights(),
                    video.bitrateRange(),
                    video.supportedFrameRates(),
                    normalizeEncoderSizes(video.cameraSizeCapabilities()));
        }
        return new EncoderFacts(
                facts.name(),
                facts.canonicalName(),
                facts.alias(),
                facts.hardwareAccelerated(),
                facts.softwareOnly(),
                facts.vendor(),
                facts.maxSupportedInstances(),
                sorted(facts.profileLevels(), Comparator
                        .comparingInt(CodecProfileLevel::profile)
                        .thenComparingInt(CodecProfileLevel::level)),
                sorted(facts.colorFormats(), Integer::compareTo),
                video);
    }

    private static List<EncoderSizeCapabilities> normalizeEncoderSizes(
            List<EncoderSizeCapabilities> values) {
        List<EncoderSizeCapabilities> result = new ArrayList<>();
        for (EncoderSizeCapabilities value : values) {
            result.add(new EncoderSizeCapabilities(value.resolution(),
                    sorted(value.supportedFrameRates(), Integer::compareTo)));
        }
        result.sort(ENCODER_SIZE_ORDER);
        return List.copyOf(result);
    }

    private static String signatureInput(
            DeviceIdentity deviceIdentity,
            List<CameraFacts> cameras,
            ConcurrentCameraFacts concurrency,
            List<EncoderFacts> encoders) {
        StringBuilder result = new StringBuilder();
        append(result, "format", "camera-catalog-signature-input-v1");
        appendDevice(result, deviceIdentity);
        append(result, "cameraCount", cameras.size());
        for (CameraFacts camera : cameras) appendCamera(result, camera);
        appendConcurrency(result, concurrency);
        append(result, "encoderCount", encoders.size());
        for (EncoderFacts encoder : encoders) appendEncoder(result, encoder);
        return result.toString();
    }

    private static void appendDevice(StringBuilder result, DeviceIdentity device) {
        append(result, "sdkInt", device.sdkInt());
        append(result, "manufacturer", device.manufacturer());
        append(result, "brand", device.brand());
        append(result, "device", device.device());
        append(result, "product", device.product());
        append(result, "model", device.model());
        append(result, "hardware", device.hardware());
        append(result, "board", device.board());
        append(result, "bootloader", device.bootloader());
        append(result, "buildId", device.buildId());
        append(result, "incremental", device.incremental());
        append(result, "securityPatch", device.securityPatch());
        append(result, "fingerprint", device.fingerprint());
    }

    private static void appendCamera(StringBuilder result, CameraFacts camera) {
        append(result, "cameraId", camera.cameraId().value());
        append(result, "hardwareLevel", camera.hardwareLevel());
        append(result, "lensFacing", camera.lensFacing());
        append(result, "sensorOrientationDegrees", camera.sensorOrientationDegrees());
        append(result, "availableCapabilityCount", camera.availableCapabilities().size());
        for (Integer capability : camera.availableCapabilities()) {
            append(result, "availableCapability", capability);
        }
        append(result, "physicalCameraIdCount", camera.physicalCameraIds().size());
        for (CameraId cameraId : camera.physicalCameraIds()) {
            append(result, "physicalCameraId", cameraId.value());
        }
        append(result, "streamConfigurationMapAvailable",
                camera.streamConfigurationMapAvailable());
        appendOutputs(result, "privateOutput", camera.privateOutputs());
        appendOutputs(result, "mediaCodecOutput", camera.mediaCodecOutputs());
        appendOutputs(result, "mediaRecorderOutput", camera.mediaRecorderOutputs());
        appendOutputs(result, "jpegOutput", camera.jpegOutputs());
        append(result, "aeTargetFpsRangeCount", camera.aeTargetFpsRanges().size());
        for (FrameRateRange range : camera.aeTargetFpsRanges()) {
            appendRange(result, "aeTargetFpsRange", range);
        }
    }

    private static void appendOutputs(
            StringBuilder result, String label, List<StreamSize> outputs) {
        append(result, label + "Count", outputs.size());
        for (StreamSize output : outputs) {
            append(result, label + "Width", output.resolution().width());
            append(result, label + "Height", output.resolution().height());
            append(result, label + "MinimumFrameDurationNanos",
                    output.minimumFrameDurationNanos());
        }
    }

    private static void appendConcurrency(
            StringBuilder result, ConcurrentCameraFacts concurrency) {
        append(result, "concurrentQueryAvailable", concurrency.queryAvailable());
        append(result, "concurrentCombinationCount", concurrency.combinations().size());
        for (ConcurrentCameraCombination combination : concurrency.combinations()) {
            append(result, "concurrentCameraCount", combination.cameraIds().size());
            for (CameraId cameraId : combination.cameraIds()) {
                append(result, "concurrentCameraId", cameraId.value());
            }
        }
    }

    private static void appendEncoder(StringBuilder result, EncoderFacts encoder) {
        append(result, "encoderName", encoder.name());
        append(result, "encoderCanonicalName", encoder.canonicalName());
        append(result, "encoderAlias", encoder.alias());
        append(result, "encoderHardwareAccelerated", encoder.hardwareAccelerated());
        append(result, "encoderSoftwareOnly", encoder.softwareOnly());
        append(result, "encoderVendor", encoder.vendor());
        append(result, "encoderMaxSupportedInstances", encoder.maxSupportedInstances());
        append(result, "encoderProfileLevelCount", encoder.profileLevels().size());
        for (CodecProfileLevel profileLevel : encoder.profileLevels()) {
            append(result, "encoderProfile", profileLevel.profile());
            append(result, "encoderLevel", profileLevel.level());
        }
        append(result, "encoderColorFormatCount", encoder.colorFormats().size());
        for (Integer colorFormat : encoder.colorFormats()) {
            append(result, "encoderColorFormat", colorFormat);
        }
        EncoderVideoCapabilities video = encoder.videoCapabilities();
        append(result, "encoderVideoCapabilitiesAvailable", video != null);
        if (video == null) return;
        append(result, "encoderWidthAlignment", video.widthAlignment());
        append(result, "encoderHeightAlignment", video.heightAlignment());
        appendRange(result, "encoderSupportedWidths", video.supportedWidths());
        appendRange(result, "encoderSupportedHeights", video.supportedHeights());
        appendRange(result, "encoderBitrateRange", video.bitrateRange());
        appendRange(result, "encoderSupportedFrameRates", video.supportedFrameRates());

        append(result, "encoderCameraSizeCapabilityCount",
                video.cameraSizeCapabilities().size());
        for (EncoderSizeCapabilities size : video.cameraSizeCapabilities()) {
            append(result, "encoderCameraSizeWidth", size.resolution().width());
            append(result, "encoderCameraSizeHeight", size.resolution().height());
            append(result, "encoderCameraSizeFrameRateCount",
                    size.supportedFrameRates().size());
            for (Integer frameRate : size.supportedFrameRates()) {
                append(result, "encoderCameraSizeFrameRate", frameRate);
            }
        }
    }

    private static void appendRange(
            StringBuilder result, String label, IntegerRange range) {
        append(result, label + "Lower", range.lower());
        append(result, label + "Upper", range.upper());
    }

    private static void appendRange(
            StringBuilder result, String label, FrameRateRange range) {
        append(result, label + "Lower", range.lower().toPlainString());
        append(result, label + "Upper", range.upper().toPlainString());
    }

    private static void append(StringBuilder result, String key, Object value) {
        String text = value == null ? null : String.valueOf(value);
        result.append(key.length()).append(':').append(key).append('=');
        if (text == null) result.append('-');
        else result.append(text.length()).append(':').append(text);
        result.append(';');
    }

    private static int compareCameraIds(List<CameraId> left, List<CameraId> right) {
        int limit = Math.min(left.size(), right.size());
        for (int index = 0; index < limit; index++) {
            int order = left.get(index).compareTo(right.get(index));
            if (order != 0) return order;
        }
        return Integer.compare(left.size(), right.size());
    }

    private static <T> List<T> sorted(List<T> values, Comparator<? super T> comparator) {
        ArrayList<T> result = new ArrayList<>(Objects.requireNonNull(values, "values"));
        result.sort(comparator);
        return List.copyOf(result);
    }
}