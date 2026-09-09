package com.dvid.dcam.platform.device.capability.fast;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dvid.dcam.core.logging.domain.LogCategory;
import com.dvid.dcam.core.logging.application.port.Logger;
import com.dvid.dcam.feature.device.application.port.FastCameraCapabilityProbe.Completion;
import com.dvid.dcam.feature.device.application.port.FastCameraCapabilityProbe.Request;
import com.dvid.dcam.feature.device.domain.camera.CameraId;
import com.dvid.dcam.feature.device.domain.camera.CameraResolution;
import com.dvid.dcam.feature.device.domain.camera.ImageMode;
import com.dvid.dcam.feature.device.domain.camera.PipelineAvailability;
import com.dvid.dcam.feature.device.domain.camera.PipelineEvidence;
import com.dvid.dcam.feature.device.domain.camera.StandardResolution;
import com.dvid.dcam.feature.device.domain.camera.StandardResolutionLabel;
import com.dvid.dcam.feature.device.domain.camera.VerificationPipelineId;
import com.dvid.dcam.feature.device.domain.camera.VideoCodec;
import com.dvid.dcam.feature.device.domain.camera.VideoMode;
import com.dvid.dcam.platform.device.capability.probe.nativesharing.NativeSurfaceSharingFastProbe;
import java.util.List;
import java.util.OptionalInt;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class AndroidFastCameraCapabilityProbeTest {
    private static final VerificationPipelineId PIPELINE =
            new VerificationPipelineId("test-shared-private-v1");
    private static final VideoMode VIDEO = new VideoMode(
            new StandardResolution(StandardResolutionLabel.HD,
                    new CameraResolution(1280, 720)), 30);
    private static final ImageMode IMAGE = new ImageMode(
            new StandardResolution(StandardResolutionLabel.FHD,
                    new CameraResolution(1920, 1080)));

    @Test void multiCameraBatchRunsOperationsConcurrently() {
        CountDownLatch bothStarted = new CountDownLatch(2);
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximumActive = new AtomicInteger();
        AndroidFastCameraCapabilityProbe probe = new AndroidFastCameraCapabilityProbe(
                PIPELINE, request -> {
                    int activeNow = active.incrementAndGet();
                    maximumActive.accumulateAndGet(activeNow, Math::max);
                    bothStarted.countDown();
                    try {
                        if (!bothStarted.await(2, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("batch did not overlap");
                        }
                        return available(request.cameraId());
                    } catch (InterruptedException error) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("interrupted", error);
                    } finally {
                        active.decrementAndGet();
                    }
                }, new TestLogger());

        var result = probe.probeBatch(
                List.of(request("0"), request("1")), () -> false);

        assertTrue(result.complete());
        assertEquals(2, result.cameraResults().size());
        assertEquals(2, maximumActive.get());
    }

    @Test void cancellationBeforeBatchDoesNotStartCameraOperation() {
        AtomicInteger calls = new AtomicInteger();
        AndroidFastCameraCapabilityProbe probe = new AndroidFastCameraCapabilityProbe(
                PIPELINE, request -> {
                    calls.incrementAndGet();
                    return available(request.cameraId());
                }, new TestLogger());

        var result = probe.probeBatch(List.of(request("0")), () -> true);

        assertEquals(Completion.CANCELLED, result.completion());
        assertEquals(0, calls.get());
        assertTrue(result.cameraResults().isEmpty());
    }

    @Test void runningSingletonCanBeCancelled() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch neverReleased = new CountDownLatch(1);
        AtomicBoolean cancelled = new AtomicBoolean();
        AndroidFastCameraCapabilityProbe probe = new AndroidFastCameraCapabilityProbe(
                PIPELINE, request -> {
                    started.countDown();
                    try {
                        neverReleased.await();
                        return available(request.cameraId());
                    } catch (InterruptedException error) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("interrupted", error);
                    }
                }, new TestLogger());
        Thread canceller = new Thread(() -> {
            try {
                if (started.await(2, TimeUnit.SECONDS)) cancelled.set(true);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
            }
        });
        canceller.start();

        var result = probe.probeBatch(List.of(request("0")), cancelled::get);
        canceller.join(2_000);

        assertEquals(Completion.CANCELLED, result.completion());
        assertFalse(result.cleanupComplete());
    }

    @Test void nativeReleaseFailuresPropagateIncompleteCleanup() {
        assertFalse(AndroidFastCameraCapabilityProbe.nativeCleanupComplete(
                nativeResult("release:camera_close_timeout")));
        assertFalse(AndroidFastCameraCapabilityProbe.nativeCleanupComplete(
                nativeResult("release_thread:join_timeout")));
        assertFalse(AndroidFastCameraCapabilityProbe.nativeCleanupComplete(
                nativeResult("session_release:timeout")));
        assertFalse(AndroidFastCameraCapabilityProbe.nativeCleanupComplete(
                nativeResult("tuple_release:IllegalStateException")));
        assertTrue(AndroidFastCameraCapabilityProbe.nativeCleanupComplete(
                nativeResult("open:camera_access_4")));
    }

    private static NativeSurfaceSharingFastProbe.Result nativeResult(String detail) {
        PipelineEvidence evidence = new PipelineEvidence(
                new CameraId("0"), VideoCodec.H264, PIPELINE,
                PipelineAvailability.UNKNOWN, List.of(), List.of());
        return new NativeSurfaceSharingFastProbe.Result(
                evidence,
                NativeSurfaceSharingFastProbe.Completion.INCOMPLETE_TRANSIENT,
                36,
                OptionalInt.of(2),
                List.of(),
                1,
                detail);
    }

    private static AndroidFastCameraCapabilityProbe.ProbeOutcome available(
            CameraId cameraId) {
        PipelineEvidence evidence = new PipelineEvidence(
                cameraId, VideoCodec.H264, PIPELINE,
                PipelineAvailability.AVAILABLE, List.of(), List.of());
        return new AndroidFastCameraCapabilityProbe.ProbeOutcome(
                evidence, Completion.COMPLETE, true, 1, "complete");
    }

    private static Request request(String cameraId) {
        return new Request(new CameraId(cameraId), List.of(VIDEO), List.of(IMAGE));
    }

    private static final class TestLogger implements Logger {
        @Override public void debug(LogCategory category, String eventName, String message) {}
        @Override public void info(LogCategory category, String eventName, String message) {}
        @Override public void info(LogCategory category, String eventName, String reasonCode, String message, Throwable error) {}
        @Override public void warn(LogCategory category, String eventName, String reasonCode, String message, Throwable error) {}
        @Override public void error(LogCategory category, String eventName, String reasonCode, String message, Throwable error) {}
    }
}