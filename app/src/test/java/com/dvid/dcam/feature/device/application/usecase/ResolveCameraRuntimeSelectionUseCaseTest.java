package com.dvid.dcam.feature.device.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.CameraSnapshot;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.CodecSnapshot;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.CodecState;
import com.dvid.dcam.feature.device.application.port.CameraCapabilityStore.Snapshot;
import com.dvid.dcam.feature.device.domain.camera.CameraId;
import com.dvid.dcam.feature.device.domain.camera.CameraResolution;
import com.dvid.dcam.feature.device.domain.camera.CandidateKey;
import com.dvid.dcam.feature.device.domain.camera.CaptureModeTuple;
import com.dvid.dcam.feature.device.domain.camera.ImageMode;
import com.dvid.dcam.feature.device.domain.camera.PipelineAvailability;
import com.dvid.dcam.feature.device.domain.camera.PipelineEvidence;
import com.dvid.dcam.feature.device.domain.camera.StandardResolution;
import com.dvid.dcam.feature.device.domain.camera.StandardResolutionLabel;
import com.dvid.dcam.feature.device.domain.camera.VerificationPipelineId;
import com.dvid.dcam.feature.device.domain.camera.VideoCodec;
import com.dvid.dcam.feature.device.domain.camera.VideoMode;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class ResolveCameraRuntimeSelectionUseCaseTest {
    private static final CameraId MAIN = new CameraId("main");
    private static final CameraId AUXILIARY = new CameraId("auxiliary");
    private static final VerificationPipelineId PIPELINE =
            new VerificationPipelineId("a-camera2-native-surface-sharing-v1");

    @Test void requestedTupleAndCameraOrderRemainStableForStartup() {
        ResolveCameraRuntimeSelectionUseCase resolver =
                new ResolveCameraRuntimeSelectionUseCase();
        Snapshot snapshot = Snapshot.current("hardware", List.of(MAIN, AUXILIARY),
                List.of(camera(MAIN), camera(AUXILIARY)));

        CandidateKey requested = resolver.requestedCandidate(
                snapshot, MAIN, "HD", 24, "FHD").orElseThrow();

        assertEquals(List.of(MAIN, AUXILIARY), resolver.cameraOrder(snapshot));
        assertEquals(new CaptureModeTuple(video(StandardResolutionLabel.HD, 1280, 720, 24),
                image(StandardResolutionLabel.FHD, 1920, 1080)),
                requested.tuple().orElseThrow());
        assertEquals(PIPELINE, requested.verificationPipelineId());
    }

    @Test void pipelineSwitchKeepsCurrentProfileAsFallbackCeiling() {
        ResolveCameraRuntimeSelectionUseCase resolver =
                new ResolveCameraRuntimeSelectionUseCase();
        CandidateKey hd = candidate(MAIN, StandardResolutionLabel.HD, 1280, 720, 24,
                StandardResolutionLabel.HD, 1280, 720);
        PipelineEvidence pipeline = new PipelineEvidence(MAIN, VideoCodec.H264, PIPELINE,
                PipelineAvailability.AVAILABLE, List.of(hd), List.of());
        CodecSnapshot codec = new CodecSnapshot(VideoCodec.H264, CodecState.ACTIVE,
                Optional.empty(), List.of(pipeline), Optional.empty());
        Snapshot snapshot = Snapshot.current("hardware", List.of(MAIN), List.of(
                new CameraSnapshot(MAIN, "main-hardware", List.of(codec),
                        Optional.empty(), Optional.empty())));
        CaptureModeTuple currentProfile = new CaptureModeTuple(
                video(StandardResolutionLabel.FHD, 1920, 1080, 30),
                image(StandardResolutionLabel.FHD, 1920, 1080));

        CandidateKey requested = resolver.requestedProfileCandidate(
                snapshot, MAIN, currentProfile).orElseThrow();

        assertEquals(currentProfile, requested.tuple().orElseThrow());
        assertEquals(List.of(hd),
                com.dvid.dcam.feature.device.domain.camera.RequestedCeilingFallback
                        .orderedCandidates(requested, pipeline.effectiveCandidates()));
    }
    @Test void requestedImageUsesStandaloneInventoryWithoutRecordingTuple() {
        ResolveCameraRuntimeSelectionUseCase resolver =
                new ResolveCameraRuntimeSelectionUseCase();
        VideoMode record = video(StandardResolutionLabel.HD, 1280, 720, 24);
        ImageMode tupleImage = image(StandardResolutionLabel.FHD, 1920, 1088);
        ImageMode alignedStandalone = image(StandardResolutionLabel.FHD, 1920, 1080);
        ImageMode standaloneOnly = image(StandardResolutionLabel.UHD, 3840, 2160);
        List<CandidateKey> candidates = List.of(
                CandidateKey.forVideo(MAIN, VideoCodec.H264, PIPELINE, record),
                CandidateKey.forImage(MAIN, VideoCodec.H264, PIPELINE, tupleImage),
                CandidateKey.forImage(MAIN, VideoCodec.H264, PIPELINE, alignedStandalone),
                CandidateKey.forImage(MAIN, VideoCodec.H264, PIPELINE, standaloneOnly),
                CandidateKey.forTuple(MAIN, VideoCodec.H264, PIPELINE,
                        new CaptureModeTuple(record, tupleImage)));
        PipelineEvidence pipeline = new PipelineEvidence(MAIN, VideoCodec.H264, PIPELINE,
                PipelineAvailability.AVAILABLE, candidates, List.of());
        CodecSnapshot codec = new CodecSnapshot(VideoCodec.H264, CodecState.ACTIVE,
                Optional.empty(), List.of(pipeline), Optional.empty());
        Snapshot snapshot = Snapshot.current("hardware", List.of(MAIN), List.of(
                new CameraSnapshot(MAIN, "main-hardware", List.of(codec),
                        Optional.empty(), Optional.empty())));

        CandidateKey aligned = resolver.requestedCandidate(
                snapshot, MAIN, "HD", 24, "FHD").orElseThrow();
        CandidateKey standalone = resolver.requestedCandidate(
                snapshot, MAIN, "HD", 24, "UHD").orElseThrow();

        assertEquals(alignedStandalone.resolution(),
                aligned.imageMode().orElseThrow().resolution());
        assertEquals(standaloneOnly.resolution(),
                standalone.imageMode().orElseThrow().resolution());
    }

    private static CameraSnapshot camera(CameraId cameraId) {
        VideoMode fhd = video(StandardResolutionLabel.FHD, 1920, 1080, 30);
        VideoMode hd = video(StandardResolutionLabel.HD, 1280, 720, 24);
        ImageMode fhdImage = image(StandardResolutionLabel.FHD, 1920, 1080);
        List<CandidateKey> candidates = List.of(
                CandidateKey.forVideo(cameraId, VideoCodec.H264, PIPELINE, fhd),
                CandidateKey.forVideo(cameraId, VideoCodec.H264, PIPELINE, hd),
                CandidateKey.forImage(cameraId, VideoCodec.H264, PIPELINE, fhdImage),
                CandidateKey.forTuple(cameraId, VideoCodec.H264, PIPELINE,
                        new CaptureModeTuple(fhd, fhdImage)),
                CandidateKey.forTuple(cameraId, VideoCodec.H264, PIPELINE,
                        new CaptureModeTuple(hd, fhdImage)));
        PipelineEvidence pipeline = new PipelineEvidence(cameraId, VideoCodec.H264, PIPELINE,
                PipelineAvailability.AVAILABLE, candidates, List.of());
        CodecSnapshot codec = new CodecSnapshot(VideoCodec.H264, CodecState.ACTIVE,
                Optional.empty(), List.of(pipeline), Optional.empty());
        return new CameraSnapshot(cameraId, cameraId.value() + "-hardware", List.of(codec),
                Optional.empty(), Optional.empty());
    }

    private static CandidateKey candidate(CameraId cameraId,
            StandardResolutionLabel videoLabel, int videoWidth, int videoHeight, int frameRate,
            StandardResolutionLabel imageLabel, int imageWidth, int imageHeight) {
        return CandidateKey.forTuple(cameraId, VideoCodec.H264, PIPELINE,
                new CaptureModeTuple(video(videoLabel, videoWidth, videoHeight, frameRate),
                        image(imageLabel, imageWidth, imageHeight)));
    }

    private static VideoMode video(StandardResolutionLabel label,
            int width, int height, int frameRate) {
        return new VideoMode(new StandardResolution(label,
                new CameraResolution(width, height)), frameRate);
    }

    private static ImageMode image(StandardResolutionLabel label, int width, int height) {
        return new ImageMode(new StandardResolution(label,
                new CameraResolution(width, height)));
    }
}