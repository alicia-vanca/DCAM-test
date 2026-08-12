package com.dvid.dcam.platform.device.capability.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import com.dvid.dcam.feature.device.domain.camera.CameraResolution;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

final class RawCameraCatalogFactoryTest {
    @Test void missingMapAndPresentEmptySizesRemainDistinctRawFacts() {
        CameraFacts missingMap = camera("0", false, List.of(), List.of());
        CameraFacts emptyMap = camera("1", true, List.of(), List.of());

        RawCatalog catalog = RawCameraCatalogFactory.create(
                device(), List.of(emptyMap, missingMap), unavailableConcurrency(), List.of());

        assertEquals(List.of(new CameraId("0"), new CameraId("1")),
                catalog.cameras().stream().map(CameraFacts::cameraId).toList());
        assertFalse(catalog.cameras().get(0).streamConfigurationMapAvailable());
        assertTrue(catalog.cameras().get(1).streamConfigurationMapAvailable());
        assertTrue(catalog.cameras().get(0).privateOutputs().isEmpty());
        assertTrue(catalog.cameras().get(1).privateOutputs().isEmpty());
        assertNotEquals(
                RawCameraCatalogFactory.create(device(), List.of(missingMap),
                        unavailableConcurrency(), List.of()).hardwareSignatureInput(),
                RawCameraCatalogFactory.create(device(), List.of(emptyMap),
                        unavailableConcurrency(), List.of()).hardwareSignatureInput());
    }

    @Test void overlappingAeRangesRemainSeparateAndExactEncoderRatesRemainRaw() {
        CameraFacts camera = camera("0", true, List.of(), List.of(
                fps("24", "60"), fps("15", "30")));
        EncoderFacts encoder = encoder(List.of(new EncoderSizeCapabilities(
                new CameraResolution(1920, 1080), List.of(30, 60))));

        RawCatalog catalog = RawCameraCatalogFactory.create(
                device(), List.of(camera), unavailableConcurrency(), List.of(encoder));

        assertEquals(List.of(fps("15", "30"), fps("24", "60")),
                catalog.cameras().get(0).aeTargetFpsRanges());
        assertEquals(List.of(30, 60), catalog.h264Encoders().get(0).videoCapabilities()
                .cameraSizeCapabilities().get(0).supportedFrameRates());
    }

    @Test void encoderAlignmentAndExactRateFactsRemainExact() {
        EncoderFacts encoder = encoder(List.of(
                new EncoderSizeCapabilities(
                        new CameraResolution(1920, 1088), List.of(60, 30)),
                new EncoderSizeCapabilities(
                        new CameraResolution(1921, 1080), List.of()),
                new EncoderSizeCapabilities(
                        new CameraResolution(1919, 1080), List.of(24))));

        RawCatalog catalog = RawCameraCatalogFactory.create(
                device(), List.of(), unavailableConcurrency(), List.of(encoder));
        EncoderVideoCapabilities video = catalog.h264Encoders().get(0).videoCapabilities();

        assertEquals(16, video.widthAlignment());
        assertEquals(2, video.heightAlignment());
        assertEquals(new CameraResolution(1919, 1080),
                video.cameraSizeCapabilities().get(0).resolution());
        assertEquals(List.of(24), video.cameraSizeCapabilities().get(0).supportedFrameRates());
        assertEquals(List.of(30, 60),
                video.cameraSizeCapabilities().get(1).supportedFrameRates());
        assertEquals(List.of(), video.cameraSizeCapabilities().get(2).supportedFrameRates());
    }

    @Test void multipleCameraAndEncoderInputOrdersProduceSameCatalogAndSignature() {
        CameraFacts camera0 = camera("0", true, List.of(
                stream(1920, 1080, 16_666_666L), stream(1280, 720, 33_333_333L)),
                List.of(fps("15", "30"), fps("30", "60")));
        CameraFacts camera1 = camera("1", true, List.of(
                stream(640, 480, 33_333_333L)), List.of(fps("15", "30")));
        ConcurrentCameraFacts concurrencyA = new ConcurrentCameraFacts(true, List.of(
                new ConcurrentCameraCombination(List.of(new CameraId("1"), new CameraId("0")))));
        ConcurrentCameraFacts concurrencyB = new ConcurrentCameraFacts(true, List.of(
                new ConcurrentCameraCombination(List.of(new CameraId("0"), new CameraId("1")))));
        EncoderFacts encoder = encoder(List.of(
                new EncoderSizeCapabilities(
                        new CameraResolution(1920, 1080), List.of(30, 60)),
                new EncoderSizeCapabilities(
                        new CameraResolution(1280, 720), List.of(30, 60, 120))));

        RawCatalog first = RawCameraCatalogFactory.create(
                device(), List.of(camera1, camera0), concurrencyA, List.of(encoder));
        RawCatalog second = RawCameraCatalogFactory.create(
                device(), List.of(camera0, camera1), concurrencyB, List.of(encoder));

        assertEquals(first, second);
        assertEquals(first.hardwareSignatureInput(), second.hardwareSignatureInput());
    }

    private static CameraFacts camera(
            String cameraId,
            boolean mapAvailable,
            List<StreamSize> privateOutputs,
            List<FrameRateRange> aeRanges) {
        return new CameraFacts(
                new CameraId(cameraId),
                1,
                0,
                90,
                List.of(3, 0),
                List.of(),
                mapAvailable,
                privateOutputs,
                List.of(),
                List.of(),
                List.of(),
                aeRanges);
    }

    private static EncoderFacts encoder(List<EncoderSizeCapabilities> sizeCapabilities) {
        return new EncoderFacts(
                "codec.avc",
                "codec.avc",
                false,
                true,
                false,
                true,
                4,
                List.of(new CodecProfileLevel(8, 4096), new CodecProfileLevel(1, 512)),
                List.of(21, 19),
                new EncoderVideoCapabilities(
                        16,
                        2,
                        new IntegerRange(64, 4096),
                        new IntegerRange(64, 2160),
                        new IntegerRange(1, 100_000_000),
                        fps("1", "120"),
                        sizeCapabilities));
    }

    private static StreamSize stream(int width, int height, long durationNanos) {
        return new StreamSize(new CameraResolution(width, height), durationNanos);
    }

    private static FrameRateRange fps(String lower, String upper) {
        return new FrameRateRange(new BigDecimal(lower), new BigDecimal(upper));
    }

    private static ConcurrentCameraFacts unavailableConcurrency() {
        return new ConcurrentCameraFacts(false, List.of());
    }

    private static DeviceIdentity device() {
        return new DeviceIdentity(
                36,
                "manufacturer",
                "brand",
                "device",
                "product",
                "model",
                "hardware",
                "board",
                "bootloader",
                "build-id",
                "incremental",
                "2026-07-01",
                "fingerprint");
    }
}